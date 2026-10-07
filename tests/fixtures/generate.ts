/**
 * The golden fixtures — the cross-language contract. Task T029.
 *
 * `fixtures/` is where the Rust program, the TypeScript suite and the Kotlin
 * client all agree. Kotlin has no Anchor and no Codama: `kotlin/core` writes
 * its codecs, PDA helpers, instruction builders and derived figures **by hand**
 * (research R2 deviation), so something has to catch the moment one of them
 * drifts. These files are that something, and they are generated from the same
 * definitions the contract tests use rather than typed out.
 *
 * | File | Consumed by |
 * |---|---|
 * | `accounts.json` | Kotlin `Accounts.kt` decoders — bytes in, fields out |
 * | `pdas.json` | Kotlin `Pdas.kt` — seed inputs to addresses |
 * | `instructions.json` | Kotlin `Instructions.kt` — args to data bytes and account order |
 * | `derived-figures.json` | Kotlin `Derive.kt` — accounts plus chain time to screen figures |
 * | `settlement-vectors.json` | written by hand from PRD §19; the Rust maths replays it |
 *
 * It runs as part of the suite, so CI's `git diff --exit-code -- fixtures/`
 * fails the build the moment a layout, a seed, an instruction's account order
 * or a derived figure changes without the fixtures being regenerated and
 * reviewed — in whichever of the three languages moved.
 *
 * Every figure here is computed from the same helpers the scenarios use. This
 * file writes files; it asserts only that what it wrote can be read back.
 */

import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { address, type Address } from '@solana/kit';
import { describe, expect, it } from 'vitest';

import {
  Cohort,
  Config,
  LeagueConfig,
  Membership,
  Profile,
  toHex,
  type CohortAccount,
  type ConfigAccount,
  type LeagueConfigAccount,
  type MembershipAccount,
  type ProfileAccount,
} from '../helpers/accounts.js';
import { DEFAULT_CHIPS, FIRST_START, SKR } from '../helpers/harness.js';
import {
  claimFaucetIx,
  claimPayoutIx,
  createLeagueIx,
  forfeitPairs,
  initializeIx,
  joinCohortIx,
  leaveCohortIx,
  openCohortIx,
  publishChipSetIx,
  recordForfeitsIx,
  settleCountIx,
  settleFinalizeIx,
  setFlagsIx,
  submitDayIx,
  topUpSponsorIx,
} from '../helpers/instructions.js';
import {
  cohortBump,
  cohortPda,
  configBump,
  configPda,
  faucetVaultPda,
  leagueBump,
  leaguePda,
  membershipBump,
  membershipPda,
  mintAuthorityPda,
  profileBump,
  profilePda,
  vaultBump,
  vaultPda,
} from '../helpers/pdas.js';
import {
  cohortEnd,
  deadlineOf,
  inBreach,
  maxDaysCompleted,
  statusOf,
  type CohortSnapshot,
  type MembershipSnapshot,
} from '../invariants/checker.js';

const OUT = resolve(dirname(fileURLToPath(import.meta.url)), '../../fixtures');
const DAY = 86_400;
const HOUR = 3_600;
const WEEK = 7 * DAY;

/** Stand-in keys, fixed so the fixtures are stable across runs. */
const key = (byte: number): Address =>
  address(
    // A 32-byte value written as base58 through the codec is awkward to fix by
    // hand, so these are taken from PDAs and known addresses instead.
    [
      '11111111111111111111111111111111',
      'TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA',
      'ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL',
      'Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4',
      'BPFLoaderUpgradeab1e11111111111111111111111',
      'SysvarRent111111111111111111111111111111111',
      'SysvarC1ock11111111111111111111111111111111',
    ][byte % 7]!,
  );

const ADMIN = key(3);
const OPERATOR = key(1);
const SPONSOR = key(2);
const MINT = key(4);
const FEE_DESTINATION = key(5);
const MEMBER = key(6);

const write = (name: string, value: unknown): string => {
  mkdirSync(OUT, { recursive: true });
  const text = `${JSON.stringify(value, (_k, v) => (typeof v === 'bigint' ? v.toString() : v), 2)}\n`;
  writeFileSync(resolve(OUT, name), text);
  return text;
};

// ------------------------------------------------------------------- accounts

const config: ConfigAccount = {
  admin: ADMIN,
  operator: OPERATOR,
  sponsor: SPONSOR,
  skrMint: MINT,
  feeDestination: FEE_DESTINATION,
  ladderGateEnabled: false,
  genesisGateEnabled: false,
  chipSet: { amounts: DEFAULT_CHIPS, version: 1, publishedAt: BigInt(FIRST_START - WEEK), maxTopTier: DEFAULT_CHIPS[3] },
  faucet: {
    solTopUp: 20_000_000n,
    sponsorFloat: 50_000_000n,
    dailyLamportCap: 720_000_000n,
    lamportDay: 20_700n,
    lamportsToday: 41_234_567n,
  },
  bump: 255,
};

const league: LeagueConfigAccount = {
  leagueId: 0,
  durationDays: 7,
  repsPerDay: 25,
  exercise: 0,
  dayLength: BigInt(DAY),
  firstStart: BigInt(FIRST_START),
  cadence: BigInt(WEEK),
  joinLock: BigInt(HOUR),
  nextIndex: 3,
  bump: 254,
};

/** A cohort mid-run: members standing, a pool from forfeits already recorded. */
const running: CohortAccount = {
  leagueId: 0,
  index: 2,
  start: BigInt(FIRST_START),
  joinCutoff: BigInt(FIRST_START - HOUR),
  durationDays: 7,
  repsPerDay: 25,
  dayLength: BigInt(DAY),
  exercise: 0,
  chips: DEFAULT_CHIPS,
  memberCount: 37,
  heldStake: 900n * SKR,
  pool: 176n * SKR,
  carryOverReceived: 0n,
  forfeitsRecorded: 5,
  counted: 0,
  finisherCount: 0,
  finishersPerTier: [0, 0, 0, 0],
  totalFinisherStake: 0n,
  settled: false,
  sharePerTier: [0n, 0n, 0n, 0n],
  roundingRemainder: 0n,
  noFinisherFee: 0n,
  carryOverSent: 0n,
  carryOverHeld: 0n,
  carryOverTarget: key(0),
  vaultBump: 253,
  bump: 252,
};

/** The same cohort settled, with its four shares and a remainder written. */
const settled: CohortAccount = {
  ...running,
  memberCount: 37,
  heldStake: 620n * SKR,
  pool: 175n * SKR,
  counted: 37,
  finisherCount: 20,
  finishersPerTier: [6, 5, 5, 4],
  totalFinisherStake: 620n * SKR,
  settled: true,
  sharePerTier: [2n * SKR, 7n * SKR, 14n * SKR, 28n * SKR],
  roundingRemainder: 1n * SKR,
  carryOverTarget: key(0),
};

/** A no-finisher cohort: its whole pool went to the fee destination (FR-037). */
const noFinishers: CohortAccount = {
  ...running,
  index: 1,
  memberCount: 3,
  heldStake: 0n,
  pool: 0n,
  counted: 3,
  finisherCount: 0,
  finishersPerTier: [0, 0, 0, 0],
  totalFinisherStake: 0n,
  settled: true,
  noFinisherFee: 120n * SKR,
  carryOverSent: 0n,
  carryOverHeld: 0n,
  carryOverTarget: key(0),
};

const membership = (over: Partial<MembershipAccount> = {}): MembershipAccount => ({
  cohort: key(3),
  wallet: MEMBER,
  tier: 3,
  stake: DEFAULT_CHIPS[3],
  joinedAt: BigInt(FIRST_START - DAY),
  daysCompleted: 4,
  state: 'Joined',
  counted: false,
  bump: 251,
  ...over,
});

const zeroProgress = { cleared: 0, forfeited: 0, currentStreak: 0, longestStreak: 0, lastChanged: 0n };

const profile = (over: Partial<ProfileAccount> = {}): ProfileAccount => ({
  wallet: MEMBER,
  qualified: true,
  leagues: [
    { cleared: 3, forfeited: 1, currentStreak: 2, longestStreak: 3, lastChanged: BigInt(FIRST_START + 7 * DAY) },
    { cleared: 1, forfeited: 0, currentStreak: 1, longestStreak: 1, lastChanged: BigInt(FIRST_START + 21 * DAY) },
    { ...zeroProgress },
  ],
  running: { cohort: key(3), end: BigInt(FIRST_START + 7 * DAY) },
  pending: { cohort: key(0), start: 0n, end: 0n },
  lastFaucetDay: 20_700n,
  bump: 250,
  ...over,
});

/** Bytes plus the fields they decode to, for every account at several states. */
const accountFixtures = () => {
  const cases: { name: string; state: string; bytes: string; fields: unknown }[] = [];
  const add = <T>(
    name: string,
    state: string,
    codec: { encode: (v: T) => Uint8Array; decode: (b: Uint8Array) => T },
    value: T,
  ) => {
    const bytes = codec.encode(value);
    // The fixture is only worth committing if it round-trips.
    expect(codec.encode(codec.decode(bytes)), `${name}/${state} round-trips`).toEqual(bytes);
    cases.push({ name, state, bytes: toHex(bytes), fields: value });
  };

  add('Config', 'initialised', Config, config);
  add('Config', 'gates-on', Config, { ...config, ladderGateEnabled: true, genesisGateEnabled: true });
  add('LeagueConfig', 'seven-day', LeagueConfig, league);
  add('LeagueConfig', 'twenty-one-day', LeagueConfig, { ...league, leagueId: 1, durationDays: 21 });
  add('Cohort', 'running', Cohort, running);
  add('Cohort', 'settled', Cohort, settled);
  add('Cohort', 'no-finisher', Cohort, noFinishers);
  add('Membership', 'joined', Membership, membership());
  add('Membership', 'forfeited', Membership, membership({ state: 'Forfeited', daysCompleted: 2, counted: true }));
  add('Membership', 'finished', Membership, membership({ state: 'Finished', daysCompleted: 7, counted: true }));
  add('Membership', 'claimed', Membership, membership({ state: 'Claimed', daysCompleted: 7, counted: true }));
  add('Profile', 'qualified-with-history', Profile, profile());
  add('Profile', 'fresh', Profile, profile({
    qualified: false,
    leagues: [{ ...zeroProgress }, { ...zeroProgress }, { ...zeroProgress }],
    running: { cohort: key(0), end: 0n },
    pending: { cohort: key(0), start: 0n, end: 0n },
    lastFaucetDay: -1n,
  }));
  return cases;
};

// ----------------------------------------------------------------------- PDAs

const pdaFixtures = async () => {
  const cohort = await cohortPda(0, 2);
  return [
    { name: 'config', seeds: ['config'], address: await configPda(), bump: await configBump() },
    { name: 'league', seeds: ['league', 0], address: await leaguePda(0), bump: await leagueBump(0) },
    { name: 'league', seeds: ['league', 1], address: await leaguePda(1), bump: await leagueBump(1) },
    { name: 'league', seeds: ['league', 100], address: await leaguePda(100), bump: await leagueBump(100) },
    { name: 'cohort', seeds: ['cohort', 0, 0], address: await cohortPda(0, 0), bump: await cohortBump(0, 0) },
    { name: 'cohort', seeds: ['cohort', 0, 2], address: cohort, bump: await cohortBump(0, 2) },
    { name: 'cohort', seeds: ['cohort', 1, 511], address: await cohortPda(1, 511), bump: await cohortBump(1, 511) },
    { name: 'vault', seeds: ['vault', cohort], address: await vaultPda(cohort), bump: await vaultBump(cohort) },
    {
      name: 'member',
      seeds: ['member', cohort, MEMBER],
      address: await membershipPda(cohort, MEMBER),
      bump: await membershipBump(cohort, MEMBER),
    },
    { name: 'profile', seeds: ['profile', MEMBER], address: await profilePda(MEMBER), bump: await profileBump(MEMBER) },
    { name: 'faucet_vault', seeds: ['faucet_vault'], address: await faucetVaultPda(), bump: null },
    { name: 'mint_authority', seeds: ['mint_authority'], address: await mintAuthorityPda(), bump: null },
  ];
};

// --------------------------------------------------------------- instructions

const instructionFixtures = async () => {
  const cohort = await cohortPda(0, 2);
  const pairs = await forfeitPairs(cohort, [MEMBER]);
  const built: { name: string; args: unknown; data: string; accounts: { address: string; role: string }[] }[] = [];

  const add = async (name: string, args: unknown, ix: Awaited<ReturnType<typeof initializeIx>>) => {
    built.push({
      name,
      args,
      data: toHex(new Uint8Array(ix.data ?? new Uint8Array())),
      accounts: (ix.accounts ?? []).map((a) => ({ address: String(a.address), role: String(a.role) })),
    });
  };

  await add(
    'initialize',
    {
      operator: OPERATOR,
      sponsor: SPONSOR,
      treasuryWallet: FEE_DESTINATION,
      faucetSolTopUp: '20000000',
      faucetDailyLamportCap: '720000000',
      faucetSponsorFloat: '50000000',
    },
    await initializeIx(ADMIN, MINT, {
      operator: OPERATOR,
      sponsor: SPONSOR,
      treasuryWallet: FEE_DESTINATION,
      faucetSolTopUp: 20_000_000n,
      faucetDailyLamportCap: 720_000_000n,
      faucetSponsorFloat: 50_000_000n,
    }),
  );
  await add(
    'create_league',
    { leagueId: 0, durationDays: 7, repsPerDay: 25, exercise: 0, firstStart: FIRST_START, dayLength: DAY, cadence: WEEK, joinLock: HOUR },
    await createLeagueIx(ADMIN, {
      leagueId: 0,
      durationDays: 7,
      repsPerDay: 25,
      exercise: 0,
      firstStart: FIRST_START,
      dayLength: DAY,
      cadence: WEEK,
      joinLock: HOUR,
    }),
  );
  await add(
    'set_flags',
    { ladderGateEnabled: true, genesisGateEnabled: false },
    await setFlagsIx(ADMIN, true, false),
  );
  await add('publish_chip_set', { amounts: DEFAULT_CHIPS }, await publishChipSetIx(OPERATOR, DEFAULT_CHIPS));
  await add('open_cohort', { leagueId: 0, index: 0 }, await openCohortIx(ADMIN, MINT, 0, 0));
  await add(
    'open_cohort',
    { leagueId: 0, index: 2, previousCohort: await cohortPda(0, 1) },
    await openCohortIx(ADMIN, MINT, 0, 2, await cohortPda(0, 1)),
  );
  await add('join_cohort', { tier: 3 }, await joinCohortIx(MEMBER, MINT, 0, 2, 3));
  await add(
    'join_cohort',
    { tier: 0, runningCohort: await cohortPda(0, 1) },
    await joinCohortIx(MEMBER, MINT, 0, 2, 0, await cohortPda(0, 1)),
  );
  await add('leave_cohort', {}, await leaveCohortIx(MEMBER, MINT, 0, 2));
  await add(
    'submit_day',
    { dayIndex: 4, proofHash: '2a'.repeat(32) },
    await submitDayIx(MEMBER, 0, 2, 4, new Uint8Array(32).fill(0x2a)),
  );
  await add('record_forfeits', { pairs: 1 }, await recordForfeitsIx(ADMIN, FEE_DESTINATION, 0, 2, pairs));
  await add(
    'settle_count',
    { pairs: 1, previousCohort: await cohortPda(0, 1) },
    await settleCountIx(ADMIN, FEE_DESTINATION, 0, 2, pairs, await cohortPda(0, 1)),
  );
  await add('settle_finalize', {}, await settleFinalizeIx(ADMIN, FEE_DESTINATION, 0, 2));
  await add('claim_payout', {}, await claimPayoutIx(MEMBER, MINT, 0, 2));
  await add('claim_faucet', { recipient: MEMBER }, await claimFaucetIx(SPONSOR, MEMBER, MINT, SPONSOR));
  await add('top_up_sponsor', {}, await topUpSponsorIx(ADMIN, SPONSOR));
  return built;
};

// ------------------------------------------------------------ derived figures

const feeOf = (stake: bigint) => (stake * 30n) / 100n;

/** The cohort and roster the derived figures are computed over. */
const derivedFixtures = () => {
  const cohort: CohortSnapshot = {
    address: String(key(3)),
    leagueId: 0,
    index: 2,
    start: FIRST_START,
    joinCutoff: FIRST_START - HOUR,
    durationDays: 7,
    dayLength: DAY,
    chips: DEFAULT_CHIPS,
    memberCount: 5,
    heldStake: 185n * SKR,
    pool: 20n * SKR,
    carryOverReceived: 0n,
    forfeitsRecorded: 0,
    counted: 0,
    finisherCount: 0,
    finishersPerTier: [0, 0, 0, 0],
    totalFinisherStake: 0n,
    settled: false,
    sharePerTier: [0n, 0n, 0n, 0n],
    roundingRemainder: 0n,
    noFinisherFee: 0n,
    carryOverSent: 0n,
    carryOverTarget: null,
    carryOverHeld: 0n,
    vaultAmount: 205n * SKR,
  };

  const roster: MembershipSnapshot[] = [
    { address: 'm0', cohort: cohort.address, wallet: 'w0', tier: 3, stake: DEFAULT_CHIPS[3]!, daysCompleted: 3, state: 'Joined', counted: false },
    { address: 'm1', cohort: cohort.address, wallet: 'w1', tier: 2, stake: DEFAULT_CHIPS[2]!, daysCompleted: 3, state: 'Joined', counted: false },
    { address: 'm2', cohort: cohort.address, wallet: 'w2', tier: 1, stake: DEFAULT_CHIPS[1]!, daysCompleted: 2, state: 'Joined', counted: false },
    { address: 'm3', cohort: cohort.address, wallet: 'w3', tier: 0, stake: DEFAULT_CHIPS[0]!, daysCompleted: 1, state: 'Joined', counted: false },
    { address: 'm4', cohort: cohort.address, wallet: 'w4', tier: 3, stake: DEFAULT_CHIPS[3]!, daysCompleted: 0, state: 'Forfeited', counted: false },
  ];

  /** Every instant worth a fixture: inside a day, at a deadline, and past it. */
  const instants = [
    { label: 'open', now: FIRST_START - 2 * HOUR },
    { label: 'locked', now: FIRST_START - HOUR },
    { label: 'day-3-mid', now: FIRST_START + 3 * DAY + 600 },
    { label: 'day-3-warning-window', now: FIRST_START + 4 * DAY - 300 },
    { label: 'day-3-deadline', now: FIRST_START + 4 * DAY - 1 },
    { label: 'day-3-deadline-passed', now: FIRST_START + 4 * DAY },
    { label: 'settling', now: cohortEnd(cohort) },
  ];

  const standing = (m: MembershipSnapshot, now: number) =>
    m.state === 'Finished' || m.state === 'Claimed' || (m.state === 'Joined' && !inBreach(m, cohort, now));

  return {
    cohort,
    roster: roster.map((m) => ({ wallet: m.wallet, tier: m.tier, stake: m.stake, daysCompleted: m.daysCompleted, state: m.state })),
    cases: instants.map(({ label, now }) => {
      const survivors = roster.filter((m) => standing(m, now));
      const projectedPool = roster
        .filter((m) => m.state === 'Joined' && inBreach(m, cohort, now))
        .reduce((sum, m) => sum + (m.stake - feeOf(m.stake)), cohort.pool);
      const standingStake = survivors.reduce((sum, m) => sum + m.stake, 0n);
      const dayIndex = Math.min(Math.floor(Math.max(0, now - cohort.start) / cohort.dayLength), cohort.durationDays - 1);
      const timeLeftToday = deadlineOf(cohort, dayIndex) - now;
      return {
        label,
        now,
        status: statusOf(cohort, now),
        dayIndex: now < cohort.start ? null : dayIndex,
        maxDaysCompleted: maxDaysCompleted(cohort, now),
        survivorCount: survivors.length,
        memberCountAtStart: cohort.memberCount,
        projectedPool,
        projectedSharePerWallet: roster.map((m) => ({
          wallet: m.wallet,
          share: standingStake === 0n ? 0n : (projectedPool * m.stake) / standingStake,
        })),
        rosterOrder: [...roster]
          .sort((a, b) => {
            const byStanding = Number(standing(b, now)) - Number(standing(a, now));
            if (byStanding !== 0) return byStanding;
            const byDays = b.daysCompleted - a.daysCompleted;
            return byDays !== 0 ? byDays : a.wallet.localeCompare(b.wallet);
          })
          .map((m) => m.wallet),
        timeLeftToday: now < cohort.start ? null : timeLeftToday,
        inWarningWindow: now < cohort.start ? false : timeLeftToday <= 600 && timeLeftToday >= 0,
        timeUntilJoiningCloses: cohort.joinCutoff - now,
        atStake: cohort.heldStake,
        carryOverReceived: cohort.carryOverReceived,
      };
    }),
  };
};

// ---------------------------------------------------------------------- write

describe('the golden fixtures (contracts §7, T029)', () => {
  it('writes accounts.json, and every account round-trips', () => {
    const cases = accountFixtures();
    expect(cases.length).toBeGreaterThan(10);
    write('accounts.json', {
      $comment:
        'Serialized bytes and the fields they decode to, for every account type at representative states. Generated by tests/fixtures/generate.ts — do not edit by hand. Consumed by kotlin/core Accounts.kt (T030, T031).',
      accounts: cases,
    });
  });

  it('writes pdas.json', async () => {
    const pdas = await pdaFixtures();
    write('pdas.json', {
      $comment:
        'Seed inputs to addresses, with the canonical bump where the program stores one. Generated by tests/fixtures/generate.ts — do not edit by hand. Consumed by kotlin/core Pdas.kt. String seeds are UTF-8; a number seed is one byte, except a cohort index which is u32 little-endian (contracts §6).',
      programId: 'Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4',
      pdas,
    });
    expect(pdas.every((p) => p.address.length > 0)).toBe(true);
  });

  it('writes instructions.json with every instruction the program has', async () => {
    const built = await instructionFixtures();
    write('instructions.json', {
      $comment:
        'Arguments to instruction data bytes and account order, for every instruction. Generated by tests/fixtures/generate.ts — do not edit by hand. Consumed by kotlin/core Instructions.kt. The discriminator is the first 8 bytes of each `data`; roles are @solana/kit AccountRole values.',
      instructions: built,
    });
    // Every instruction in the program has a fixture, so a new one cannot land
    // without the Kotlin side being given something to build against.
    const names = new Set(built.map((b) => b.name));
    for (const required of [
      'initialize',
      'create_league',
      'set_flags',
      'publish_chip_set',
      'open_cohort',
      'join_cohort',
      'leave_cohort',
      'submit_day',
      'record_forfeits',
      'settle_count',
      'settle_finalize',
      'claim_payout',
      'claim_faucet',
      'top_up_sponsor',
    ]) {
      expect(names, `${required} has a fixture`).toContain(required);
    }
  });

  it('writes derived-figures.json', () => {
    const derived = derivedFixtures();
    write('derived-figures.json', {
      $comment:
        'A cohort, its roster and a set of chain instants, with every figure a screen shows at each (data-model §5). Generated by tests/fixtures/generate.ts — do not edit by hand. Consumed by kotlin/core Derive.kt (T032).',
      ...derived,
    });

    // The figures move at the deadline, which is the property the fixtures are
    // for: at day 3's deadline everyone who sat is standing; one second later
    // the two who fell behind are not, with nothing on chain having changed.
    const atDeadline = derived.cases.find((c) => c.label === 'day-3-deadline')!;
    const justAfter = derived.cases.find((c) => c.label === 'day-3-deadline-passed')!;
    expect(atDeadline.survivorCount).toBeGreaterThan(justAfter.survivorCount);
    expect(justAfter.projectedPool).toBeGreaterThan(atDeadline.projectedPool);
    expect(derived.cases.find((c) => c.label === 'day-3-warning-window')!.inWarningWindow).toBe(true);
    expect(atDeadline.timeLeftToday).toBe(0);
  });
});
