/**
 * League setup: who may `initialize`, what a league may be, and the rule that no
 * cohort opens before a chip set exists. Task T017b — written before any of these
 * instructions exists, so it is red until they do (constitution VII).
 *
 * What turns green, and when:
 *
 *  - `initialize` .................. T027
 *  - `create_league` ............... T028
 *  - `publish_chip_set`, `open_cohort` ... T058 and T055 (US6). The chip-set gate
 *    cannot be shown without both, so this last block stays red until they land.
 *
 * Instructions come from `helpers/instructions.ts`, which is written from the
 * contract rather than generated from the program (see its header). Setup that is
 * not the subject of a test is injected, so a defect in `initialize` cannot hide a
 * defect in `create_league`.
 *
 * The test-league cases need a second binary, built with the `test-leagues` feature
 * (`npm --prefix tests run build:test-leagues`). Without it those cases skip, and
 * the suite says so; the default binary must refuse a test league on its own.
 */

import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { generateKeyPairSigner, getAddressDecoder, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import { LeagueConfig } from '../helpers/accounts.js';
import {
  DAY,
  DEFAULT_CHIPS,
  FIRST_START,
  HOUR,
  SKR,
  WEEK,
  createHarness,
  expectProgramError,
  type Harness,
} from '../helpers/harness.js';
import {
  createLeagueIx,
  initializeIx,
  openCohortIx,
  publishChipSetIx,
  type CreateLeagueArgs,
  type InitializeArgs,
} from '../helpers/instructions.js';
import {
  SYSTEM_PROGRAM,
  TOKEN_PROGRAM,
  associatedTokenPda,
  cohortPda,
  configPda,
  faucetVaultPda,
  leaguePda,
  mintAuthorityPda,
  reserveBump,
  reservePda,
  vaultPda,
} from '../helpers/pdas.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const TEST_LEAGUES_PROGRAM = resolve(HERE, '../../target/test-leagues/holdout.so');

// -------------------------------------------------------------------- helpers

const account = (h: Harness, at: Address) => {
  const found = h.svm.getAccount(at);
  return found.exists ? found : null;
};
const exists = (h: Harness, at: Address): boolean => account(h, at) !== null;

/** An SPL mint: mint authority as a `COption`, supply, decimals. */
const readMint = (h: Harness, at: Address) => {
  const found = account(h, at);
  if (!found) return null;
  const data = new Uint8Array(found.data);
  const view = new DataView(data.buffer, data.byteOffset);
  return {
    owner: found.programAddress,
    authority: view.getUint32(0, true) === 1 ? getAddressDecoder().decode(data.slice(4, 36)) : null,
    supply: view.getBigUint64(36, true),
    decimals: data[44],
  };
};

/** An SPL token account: mint, owner, amount. */
const readTokenAccount = (h: Harness, at: Address) => {
  const found = account(h, at);
  if (!found) return null;
  const data = new Uint8Array(found.data);
  const view = new DataView(data.buffer, data.byteOffset);
  return {
    owner: found.programAddress,
    mint: getAddressDecoder().decode(data.slice(0, 32)),
    authority: getAddressDecoder().decode(data.slice(32, 64)),
    amount: view.getBigUint64(64, true),
  };
};

const addressOf = async (): Promise<Address> => (await generateKeyPairSigner()).address;

// ----------------------------------------------------------------- initialize

describe('initialize (research R14)', () => {
  let h: Harness;
  let args: InitializeArgs;
  let mint: KeyPairSigner;

  beforeEach(async () => {
    h = await createHarness();
    mint = await generateKeyPairSigner();
    args = {
      operator: await addressOf(),
      sponsor: await addressOf(),
      treasuryWallet: await addressOf(),
      developerWallet: await addressOf(),
      faucetSolTopUp: 20_000_000n,
      faucetDailyLamportCap: 720_000_000n,
      faucetSponsorFloat: 50_000_000n,
    };
  });

  it('accepts the upgrade authority, who becomes admin, and creates everything the program needs', async () => {
    const admin = h.upgradeAuthority;
    await h.sendOrThrow([await initializeIx(admin.address, mint.address, args)], admin, [mint]);

    const config = await h.readConfig();
    expect(config.admin).toBe(admin.address);
    expect(config.operator).toBe(args.operator);
    expect(config.sponsor).toBe(args.sponsor);
    expect(config.skrMint).toBe(mint.address);
    expect(config.feeDestination).toBe(await associatedTokenPda(args.treasuryWallet, mint.address));
    expect(config.developerDestination).toBe(await associatedTokenPda(args.developerWallet, mint.address));
    expect(config.ladderGateEnabled).toBe(false);
    expect(config.genesisGateEnabled).toBe(false);
    // No chip set yet, which no cohort may precede (FR-055b).
    expect(config.chipSet.amounts).toEqual([0n, 0n, 0n, 0n]);
    expect(config.chipSet.version).toBe(0);
    expect(config.chipSet.maxTopTier).toBe(0n);
    expect(config.faucet.solTopUp).toBe(args.faucetSolTopUp);
    expect(config.faucet.dailyLamportCap).toBe(args.faucetDailyLamportCap);
    expect(config.faucet.sponsorFloat).toBe(args.faucetSponsorFloat);
    expect(config.faucet.lamportsToday).toBe(0n);

    // The mock SKR mint: its authority is the program's PDA, so only the faucet mints.
    const skr = readMint(h, mint.address);
    expect(skr?.owner).toBe(TOKEN_PROGRAM);
    expect(skr?.authority).toBe(await mintAuthorityPda());
    expect(skr?.supply).toBe(0n);
    // The harness's `SKR` unit assumes 6 decimals. T027 decides the real figure; if it
    // is not 6, change `SKR` in helpers/harness.ts together with this line.
    expect(skr?.decimals).toBe(6);

    // The operations destination is the treasury's own token account, so fees reach the treasury.
    const fees = readTokenAccount(h, config.feeDestination);
    expect(fees?.owner).toBe(TOKEN_PROGRAM);
    expect(fees?.mint).toBe(mint.address);
    expect(fees?.authority).toBe(args.treasuryWallet);
    expect(fees?.amount).toBe(0n);

    // The developer destination likewise belongs to the developer's wallet (FR-037f).
    const developer = readTokenAccount(h, config.developerDestination);
    expect(developer?.owner).toBe(TOKEN_PROGRAM);
    expect(developer?.mint).toBe(mint.address);
    expect(developer?.authority).toBe(args.developerWallet);
    expect(developer?.amount).toBe(0n);

    // The faucet vault holds lamports at a system-owned PDA, funded to rent exemption.
    const vault = account(h, await faucetVaultPda());
    expect(vault?.programAddress).toBe(SYSTEM_PROGRAM);
    expect(vault?.lamports ?? 0n).toBeGreaterThanOrEqual(h.svm.minimumBalanceForRentExemption(0n));
  });

  it('refuses a stranger with NotUpgradeAuthority, and creates nothing', async () => {
    const stranger = await h.newWallet();
    const result = await h.send([await initializeIx(stranger.address, mint.address, args)], stranger, [mint]);
    expectProgramError(result, 'NotUpgradeAuthority');

    expect(exists(h, await configPda())).toBe(false);
    expect(exists(h, mint.address)).toBe(false);
    expect(exists(h, await associatedTokenPda(args.treasuryWallet, mint.address))).toBe(false);
    expect(exists(h, await associatedTokenPda(args.developerWallet, mint.address))).toBe(false);
    expect(exists(h, await faucetVaultPda())).toBe(false);
  });

  it('refuses even the deployer once the authority has moved on — the current authority decides, not the original', async () => {
    const newAuthority = await h.newWallet();
    await h.setUpgradeAuthority(newAuthority.address);

    const deployer = h.upgradeAuthority;
    const refused = await h.send([await initializeIx(deployer.address, mint.address, args)], deployer, [mint]);
    expectProgramError(refused, 'NotUpgradeAuthority');
    expect(exists(h, await configPda())).toBe(false);

    await h.sendOrThrow([await initializeIx(newAuthority.address, mint.address, args)], newAuthority, [mint]);
    expect((await h.readConfig()).admin).toBe(newAuthority.address);
  });

  it('refuses everyone when the program is immutable, since nobody holds the authority', async () => {
    await h.setUpgradeAuthority(null);
    const deployer = h.upgradeAuthority;
    const result = await h.send([await initializeIx(deployer.address, mint.address, args)], deployer, [mint]);
    expectProgramError(result, 'NotUpgradeAuthority');
    expect(exists(h, await configPda())).toBe(false);
  });

  it('cannot run twice: a second initialize fails and leaves Config as it was', async () => {
    const admin = h.upgradeAuthority;
    await h.sendOrThrow([await initializeIx(admin.address, mint.address, args)], admin, [mint]);

    const otherMint = await generateKeyPairSigner();
    const hijack: InitializeArgs = { ...args, operator: await addressOf(), sponsor: await addressOf() };
    const second = await h.send([await initializeIx(admin.address, otherMint.address, hijack)], admin, [otherMint]);
    expect(second.ok).toBe(false);

    const config = await h.readConfig();
    expect(config.operator).toBe(args.operator);
    expect(config.sponsor).toBe(args.sponsor);
    expect(config.skrMint).toBe(mint.address);
    expect(exists(h, otherMint.address)).toBe(false);
  });
});

// ---------------------------------------------------------------- create_league

const REAL_LEAGUE: CreateLeagueArgs = {
  leagueId: 0,
  durationDays: 7,
  repsPerDay: 25,
  exercise: 0,
  // The 7-day league is the Easy one: knees allowed (H35).
  form: 1,
  firstStart: FIRST_START,
  dayLength: DAY,
  cadence: WEEK,
  joinLock: HOUR,
};

/** A test league's timing: a minute-long day, so a whole cycle runs in minutes (scope doc D-A). */
const COMPRESSED = { dayLength: 60, cadence: 120, joinLock: 10 };

describe('create_league (data-model §1.2, H30, H35)', () => {
  let h: Harness;
  let admin: KeyPairSigner;
  let mint: Address;

  beforeEach(async () => {
    h = await createHarness();
    admin = h.upgradeAuthority;
    // The league's reserve vault is a token account of the configured mint.
    mint = await addressOf();
    h.injectMint(mint, await mintAuthorityPda());
    await h.injectConfig({ skrMint: mint }); // admin = the harness's upgrade authority
  });

  const create = async (over: Partial<CreateLeagueArgs> = {}, signer: KeyPairSigner = admin) =>
    h.send([await createLeagueIx(signer.address, mint, { ...REAL_LEAGUE, ...over })], signer);

  const readLeague = async (leagueId: number) => {
    const found = account(h, await leaguePda(leagueId));
    return found ? LeagueConfig.decode(new Uint8Array(found.data)) : null;
  };

  it('creates the 7-day league with the devnet timing, and stores what it was given', async () => {
    const result = await create();
    expect(result.ok, result.logs.join('\n')).toBe(true);

    expect(await readLeague(0)).toMatchObject({
      leagueId: 0,
      durationDays: 7,
      repsPerDay: 25,
      exercise: 0,
      firstStart: BigInt(FIRST_START),
      dayLength: BigInt(DAY),
      cadence: BigInt(WEEK),
      joinLock: BigInt(HOUR),
      nextIndex: 0,
      form: 1,
      boostReserve: 0n,
      comebackReserve: 0n,
      reserveVaultBump: await reserveBump(0),
    });
  });

  describe('form (H35)', () => {
    it.each([
      ['0, from the toes (Hard)', 1, 21, 0],
      ['1, knees allowed (Easy)', 0, 7, 1],
    ])('stores %s', async (_name, leagueId, durationDays, form) => {
      await h.sendOrThrow([await createLeagueIx(admin.address, mint, { ...REAL_LEAGUE, leagueId, durationDays, form })], admin);
      expect((await readLeague(leagueId))?.form).toBe(form);
    });
    // Any other form is refused with InvalidForm: reserves.spec.ts.
  });

  it('refuses a mint that is not the configured one, and creates nothing', async () => {
    const other = await addressOf();
    h.injectMint(other, await mintAuthorityPda());
    const result = await h.send([await createLeagueIx(admin.address, other, REAL_LEAGUE)], admin);
    expect(result.ok).toBe(false);
    expect(await readLeague(0)).toBeNull();
    expect(exists(h, await reservePda(0))).toBe(false);
  });

  it('creates the 21-day league beside it', async () => {
    await h.sendOrThrow([await createLeagueIx(admin.address, mint, { ...REAL_LEAGUE, leagueId: 1, durationDays: 21, form: 0 })], admin);
    expect(await readLeague(1)).toMatchObject({ leagueId: 1, durationDays: 21, nextIndex: 0 });
  });

  it('is for the admin only', async () => {
    const stranger = await h.newWallet();
    expectProgramError(await create({}, stranger), 'NotAdmin');
    expect(await readLeague(0)).toBeNull();
  });

  describe('exercise', () => {
    it('accepts 0, which is push-ups', async () => {
      await h.sendOrThrow([await createLeagueIx(admin.address, mint, { ...REAL_LEAGUE, exercise: 0 })], admin);
      expect((await readLeague(0))?.exercise).toBe(0);
    });

    it.each([
      ['1, negative push-ups', 1],
      ['2, decline push-ups', 2],
      ['any other value', 255],
    ])('rejects %s with ExerciseNotSupported, and creates nothing', async (_name, exercise) => {
      expectProgramError(await create({ exercise }), 'ExerciseNotSupported');
      expect(await readLeague(0)).toBeNull();
    });
  });

  describe('a real league keeps the devnet timing', () => {
    it.each([
      ['a day that is not 24 hours', { dayLength: 60 }],
      ['a cadence that is not weekly', { cadence: WEEK / 2 }],
      ['a join lock that is not one hour', { joinLock: HOUR / 2 }],
      ['a first start on a Tuesday', { firstStart: FIRST_START + DAY }],
      ['a first start on Monday at 04:00 UTC', { firstStart: FIRST_START - HOUR }],
      ['a first start one second past Monday 05:00 UTC', { firstStart: FIRST_START + 1 }],
      ['a first start one second before Monday 05:00 UTC', { firstStart: FIRST_START - 1 }],
    ])('rejects %s with InvalidLeagueTiming', async (_name, over) => {
      expectProgramError(await create(over), 'InvalidLeagueTiming');
      expect(await readLeague(0)).toBeNull();
    });

    it('accepts any Monday at 05:00 UTC as the first start', async () => {
      await h.sendOrThrow([await createLeagueIx(admin.address, mint, { ...REAL_LEAGUE, firstStart: FIRST_START + 2 * WEEK })], admin);
      expect((await readLeague(0))?.firstStart).toBe(BigInt(FIRST_START + 2 * WEEK));
    });
  });

  describe("a real league's duration is fixed by which league it is", () => {
    // `open_cohort` copies `duration_days` into every cohort permanently, so a
    // wrong figure here can never be corrected. Zero is the dangerous one:
    // `settle_count` calls a member finished when
    // `days_completed == duration_days`, so in a zero-day league everyone who
    // never sat a single day is a finisher — an immediate clear, a payout and
    // the 21-day unlock, with no push-ups at all.
    it.each([
      ['a zero-day league 0', { durationDays: 0 }],
      ['a one-day league 0', { durationDays: 1 }],
      ['a 21-day league 0', { durationDays: 21 }],
    ])('rejects %s with InvalidLeagueTiming', async (_name, over) => {
      expectProgramError(await create(over), 'InvalidLeagueTiming');
      expect(await readLeague(0)).toBeNull();
    });

    it.each([
      ['a zero-rep league', { repsPerDay: 0 }],
      ['a league of one rep', { repsPerDay: 1 }],
      ['a league of more than the Daily 25', { repsPerDay: 50 }],
    ])('rejects %s with InvalidLeagueTiming', async (_name, over) => {
      // Copied into every cohort as permanently as the duration. A zero-rep
      // league would be a daily obligation with nothing to do: a submission
      // that means nothing and a forfeit nobody could have avoided.
      expectProgramError(await create(over), 'InvalidLeagueTiming');
      expect(await readLeague(0)).toBeNull();
    });

    it('rejects a 7-day league 1, which is the 21-day league', async () => {
      expectProgramError(await create({ leagueId: 1, durationDays: 7, form: 0 }), 'InvalidLeagueTiming');
      expect(await readLeague(1)).toBeNull();
    });

    it('rejects an id no league is defined for', async () => {
      // Not a test league — those start at 100 — and not one of the two judged
      // leagues either. `Profile::slot_of` would send its progress to the test
      // slot, so a "real" league here would write judged progress where test
      // runs live (data-model §1.2).
      expectProgramError(await create({ leagueId: 2 }), 'InvalidLeagueTiming');
      expect(await readLeague(2)).toBeNull();
    });
  });

  describe('a test league (id 100 and up) needs a test-leagues build', () => {
    it('is refused by the judged build, whatever its timing', async () => {
      // Compressed timing, the case a test league exists for…
      expectProgramError(await create({ leagueId: 100, ...COMPRESSED }), 'InvalidLeagueTiming');
      // …and ordinary devnet timing: the id alone is what the judged build refuses.
      expectProgramError(await create({ leagueId: 100 }), 'InvalidLeagueTiming');
      expect(await readLeague(100)).toBeNull();
    });
  });
});

describe.skipIf(!existsSync(TEST_LEAGUES_PROGRAM))('create_league in a test-leagues build', () => {
  let h: Harness;
  let admin: KeyPairSigner;
  let mint: Address;

  beforeEach(async () => {
    h = await createHarness({ programPath: TEST_LEAGUES_PROGRAM });
    admin = h.upgradeAuthority;
    mint = await addressOf();
    h.injectMint(mint, await mintAuthorityPda());
    await h.injectConfig({ skrMint: mint });
  });

  it('accepts a test league with any timing', async () => {
    const args = { ...REAL_LEAGUE, leagueId: 100, durationDays: 3, ...COMPRESSED, firstStart: h.now() + 60 };
    await h.sendOrThrow([await createLeagueIx(admin.address, mint, args)], admin);
    const found = h.svm.getAccount(await leaguePda(100));
    expect(found.exists).toBe(true);
    if (found.exists) {
      expect(LeagueConfig.decode(new Uint8Array(found.data))).toMatchObject({ leagueId: 100, durationDays: 3, dayLength: 60n });
    }
  });

  it('still holds a real league to the devnet timing — the feature widens test leagues only', async () => {
    const result = await h.send(
      [await createLeagueIx(admin.address, mint, { ...REAL_LEAGUE, leagueId: 0, ...COMPRESSED })],
      admin,
    );
    expectProgramError(result, 'InvalidLeagueTiming');
    expect(h.svm.getAccount(await leaguePda(0)).exists).toBe(false);
  });

  it('still rejects a reserved exercise', async () => {
    const result = await h.send(
      [await createLeagueIx(admin.address, mint, { ...REAL_LEAGUE, leagueId: 100, exercise: 1, ...COMPRESSED })],
      admin,
    );
    expectProgramError(result, 'ExerciseNotSupported');
  });
});

// --------------------------------------------- publish_chip_set and open_cohort

describe('a cohort cannot open before a chip set is published (FR-055b)', () => {
  let h: Harness;
  let operator: KeyPairSigner;
  let crank: KeyPairSigner;
  let mint: Address;

  beforeEach(async () => {
    h = await createHarness();
    operator = await h.newWallet();
    crank = await h.newWallet();
    mint = await addressOf();
    h.injectMint(mint, await mintAuthorityPda());
    // What `initialize` leaves behind: a Config with no chip set, and the real league.
    await h.injectConfig({
      operator: operator.address,
      skrMint: mint,
      chipSet: { amounts: [0n, 0n, 0n, 0n], version: 0, publishedAt: 0n, maxTopTier: 0n },
    });
    await h.injectLeague(0);
  });

  const publish = async (amounts: readonly [bigint, bigint, bigint, bigint], signer: KeyPairSigner = operator) =>
    h.send([await publishChipSetIx(signer.address, amounts)], signer);

  it('fails open_cohort with ChipSetNotPublished, and succeeds with the same accounts once a set exists', async () => {
    const open = await openCohortIx(crank.address, mint, 0, 0);

    // Without this refusal the cohort would copy four zeroed amounts and keep them for
    // its whole life, accepting zero-stake joins that no later publish could correct.
    expectProgramError(await h.send([open], crank), 'ChipSetNotPublished');
    expect(exists(h, await cohortPda(0, 0))).toBe(false);
    expect(LeagueConfig.decode(new Uint8Array((account(h, await leaguePda(0)))!.data)).nextIndex).toBe(0);

    await h.sendOrThrow([await publishChipSetIx(operator.address, DEFAULT_CHIPS)], operator);

    // The very same instruction — no account changed — now lands.
    await h.sendOrThrow([open], crank);

    const cohort = await h.readCohort(0, 0);
    expect(cohort.chips).toEqual(DEFAULT_CHIPS);
    expect(cohort.start).toBe(BigInt(FIRST_START));
    expect(cohort.joinCutoff).toBe(BigInt(FIRST_START - HOUR));
    expect(cohort.durationDays).toBe(7);
    expect(cohort.repsPerDay).toBe(25);
    expect(cohort.dayLength).toBe(BigInt(DAY));
    expect(cohort.exercise).toBe(0);
    expect(cohort.form, 'copied from the league (constitution V, H35)').toBe(0);
    expect(cohort.memberCount).toBe(0);
    expect(cohort.pool).toBe(0n);

    const league = LeagueConfig.decode(new Uint8Array(account(h, await leaguePda(0))!.data));
    expect(league.nextIndex).toBe(1);

    // The vault is a token account of the cohort's own, empty until someone joins.
    const vault = readTokenAccount(h, await vaultPda(await cohortPda(0, 0)));
    expect(vault?.owner).toBe(TOKEN_PROGRAM);
    expect(vault?.mint).toBe(mint);
    expect(vault?.authority).toBe(await cohortPda(0, 0));
    expect(vault?.amount).toBe(0n);
  });

  it('records the published set: version, amounts, when, and the highest top tier ever seen', async () => {
    await h.sendOrThrow([await publishChipSetIx(operator.address, DEFAULT_CHIPS)], operator);

    const { chipSet } = await h.readConfig();
    expect(chipSet.amounts).toEqual(DEFAULT_CHIPS);
    expect(chipSet.version).toBe(1);
    expect(chipSet.publishedAt).toBe(BigInt(h.now()));
    expect(chipSet.maxTopTier).toBe(100n * SKR);
  });

  it('never lowers max_top_tier, and never changes a cohort that already copied a set (FR-055, FR-055b)', async () => {
    await h.sendOrThrow([await publishChipSetIx(operator.address, DEFAULT_CHIPS)], operator);
    await h.sendOrThrow([await openCohortIx(crank.address, mint, 0, 0)], crank);

    const cheaper: [bigint, bigint, bigint, bigint] = [5n * SKR, 10n * SKR, 20n * SKR, 40n * SKR];
    await h.sendOrThrow([await publishChipSetIx(operator.address, cheaper)], operator);

    const { chipSet } = await h.readConfig();
    expect(chipSet.version).toBe(2);
    expect(chipSet.amounts).toEqual(cheaper);
    // The faucet mints this, so a wallet can still stake the top chip of the older cohort.
    expect(chipSet.maxTopTier).toBe(100n * SKR);
    expect((await h.readCohort(0, 0)).chips).toEqual(DEFAULT_CHIPS);
  });

  it('is for the operator only', async () => {
    const stranger = await h.newWallet();
    expectProgramError(await publish(DEFAULT_CHIPS, stranger), 'NotOperator');
    expect((await h.readConfig()).chipSet.version).toBe(0);
  });

  it.each([
    ['a zero first amount', [0n, 25n * SKR, 50n * SKR, 100n * SKR]],
    ['two equal neighbours', [10n * SKR, 10n * SKR, 50n * SKR, 100n * SKR]],
    ['a decreasing pair', [10n * SKR, 50n * SKR, 25n * SKR, 100n * SKR]],
  ] as const)('rejects %s with InvalidChipSet, and publishes nothing', async (_name, amounts) => {
    expectProgramError(await publish([...amounts]), 'InvalidChipSet');
    const { chipSet } = await h.readConfig();
    expect(chipSet.version).toBe(0);
    expect(chipSet.amounts).toEqual([0n, 0n, 0n, 0n]);
  });
});
