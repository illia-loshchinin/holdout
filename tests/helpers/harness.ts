/**
 * The LiteSVM harness: clock control, wallets, sending, arbitrary state, and a
 * snapshot the invariant checker can read. Task T016 (research R2).
 *
 * ## Two ways to reach a state
 *
 * 1. **Through the program** — `send()` an instruction, exactly as a client would.
 *    This is what a scenario asserts *behaviour* with.
 * 2. **By injection** — `injectCohort()` and friends write an account straight
 *    into the SVM, bypassing every instruction. This is for *setup*: a settled
 *    predecessor, a 1,000-member cohort (T076) that would take a thousand joins,
 *    a state that is reachable but slow. LiteSVM's `setAccount` says as much:
 *    it can also build states the program could never reach, so a scenario that
 *    injects is responsible for injecting something the program could produce.
 *    The injectors default to states that satisfy INV-1 (the vault holds what the
 *    cohort says it does) so a test starts consistent unless it says otherwise.
 *
 * ## Settling anything (FR-061a)
 *
 * `settle_count` refuses cohort *i* until cohort *i − 1* of that league is
 * settled (error 6045). A scenario that settles takes one of two shapes, and the
 * builders offer both directly:
 *
 *  - `freshTestLeague()` — a league of its own, so its cohort is index 0 with no
 *    predecessor; the default, and the one that never meets 6045 by accident;
 *  - `injectCohort({ league, index: 1 })` after `injectCohort({ league, index: 0,
 *    settled: true })` — for a scenario that is *about* ordering.
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { LiteSVM } from 'litesvm';
import {
  addSignersToTransactionMessage,
  appendTransactionMessageInstructions,
  createTransactionMessage,
  generateKeyPairSigner,
  getAddressEncoder,
  lamports,
  pipe,
  setTransactionMessageFeePayerSigner,
  setTransactionMessageLifetimeUsingBlockhash,
  signTransactionMessageWithSigners,
  type Address,
  type Instruction,
  type KeyPairSigner,
  type TransactionSigner,
} from '@solana/kit';

import {
  Cohort,
  Config,
  LeagueConfig,
  Membership,
  Profile,
  type CohortAccount,
  type ConfigAccount,
  type LeagueConfigAccount,
  type LeagueProgress,
  type MembershipAccount,
  type ProfileAccount,
} from './accounts.js';
import { errorCodeFromLogs, ERROR_CODES, type ErrorName } from './errors.js';
import {
  PROGRAM_ID,
  TOKEN_PROGRAM,
  UPGRADEABLE_LOADER,
  cohortPda,
  associatedTokenPda,
  configPda,
  leaguePda,
  membershipPda,
  profilePda,
  programDataPda,
  vaultPda,
} from './pdas.js';
import type {
  CohortSnapshot,
  LeagueProgressSnapshot,
  MembershipSnapshot,
  ProfileSnapshot,
  Snapshot,
} from '../invariants/checker.js';

// ------------------------------------------------------------------ constants

export const HOUR = 3_600;
export const DAY = 86_400;
export const WEEK = 7 * DAY;

/** Monday 28 September 2026, 05:00:00 UTC — the first judged cohort start. */
export const FIRST_START = 1_790_571_600;

/**
 * Base units in one whole SKR. **Assumed 6**: the mock mint's decimals are not
 * fixed anywhere in the spec, and `initialize` (T027) must decide. Injected mints
 * use this; nothing in a settlement figure depends on it.
 */
export const SKR = 1_000_000n;

/** The four chip tiers as an injected cohort copies them: $10/$25/$50/$100 in SKR. */
export const DEFAULT_CHIPS: [bigint, bigint, bigint, bigint] = [10n * SKR, 25n * SKR, 50n * SKR, 100n * SKR];

const HERE = dirname(fileURLToPath(import.meta.url));
export const DEFAULT_PROGRAM_PATH = resolve(HERE, '../../target/deploy/holdout.so');

const NO_KEY = '11111111111111111111111111111111' as Address;

// --------------------------------------------------------------------- results

export interface SendResult {
  readonly ok: boolean;
  readonly logs: string[];
  /** The custom program error number, or `null` if it succeeded or failed otherwise. */
  readonly errorCode: number | null;
  readonly computeUnits: bigint;
}

export class ProgramCallFailed extends Error {
  constructor(readonly result: SendResult) {
    super(`transaction failed${result.errorCode !== null ? ` with ${result.errorCode}` : ''}\n${result.logs.join('\n')}`);
    this.name = 'ProgramCallFailed';
  }
}

/** Assert a transaction failed with a named program error, printing the logs if not. */
export const expectProgramError = (result: SendResult, name: ErrorName): void => {
  const want = ERROR_CODES[name];
  if (result.ok) throw new Error(`expected ${name} (${want}) but the transaction succeeded`);
  if (result.errorCode !== want) {
    throw new Error(`expected ${name} (${want}) but got ${result.errorCode ?? 'a non-program error'}\n${result.logs.join('\n')}`);
  }
};

// ------------------------------------------------------------------- injectors

export type CohortOverrides = Partial<CohortAccount> & {
  /** SKR the vault holds. Defaults to `heldStake + pool + carryOverHeld` (INV-1). */
  vaultAmount?: bigint;
};

export interface LeagueRef {
  readonly leagueId: number;
}

/** Everything an injected league needs; timing defaults to the real devnet league. */
export type LeagueOverrides = Partial<LeagueConfigAccount>;

const tokenAccountBytes = (mint: Address, owner: Address, amount: bigint): Uint8Array => {
  // SPL Token account, 165 bytes: mint, owner, amount, delegate (None), state
  // (Initialized = 1), is_native (None), delegated_amount, close_authority (None).
  const out = new Uint8Array(165);
  const view = new DataView(out.buffer);
  const enc = getAddressEncoder();
  out.set(enc.encode(mint), 0);
  out.set(enc.encode(owner), 32);
  view.setBigUint64(64, amount, true);
  out[108] = 1;
  return out;
};

const mintBytes = (authority: Address | null, supply: bigint, decimals: number): Uint8Array => {
  // SPL Mint, 82 bytes: mint_authority COption, supply, decimals, is_initialized,
  // freeze_authority COption.
  const out = new Uint8Array(82);
  const view = new DataView(out.buffer);
  if (authority) {
    view.setUint32(0, 1, true);
    out.set(getAddressEncoder().encode(authority), 4);
  }
  view.setBigUint64(36, supply, true);
  out[44] = decimals;
  out[45] = 1;
  return out;
};

// ---------------------------------------------------------------------- harness

export interface HarnessOptions {
  /** A built program; defaults to `target/deploy/holdout.so` (run `anchor build`). */
  programPath?: string;
  /** Start the chain clock here; defaults to a week before the first judged cohort. */
  startTime?: number;
}

export class Harness {
  readonly svm: LiteSVM;
  /** Signs `initialize`, and is what `ProgramData` records as the upgrade authority. */
  readonly upgradeAuthority: KeyPairSigner;

  private nextTestLeague = 100;

  private constructor(svm: LiteSVM, upgradeAuthority: KeyPairSigner) {
    this.svm = svm;
    this.upgradeAuthority = upgradeAuthority;
  }

  static async create(options: HarnessOptions = {}): Promise<Harness> {
    const path = options.programPath ?? DEFAULT_PROGRAM_PATH;
    if (!existsSync(path)) {
      throw new Error(`no program binary at ${path} — run \`anchor build\` first`);
    }

    const svm = new LiteSVM();
    // LiteSVM deploys through the upgradeable loader, creating the program and its
    // ProgramData account — but with no upgrade authority (an immutable program).
    svm.addProgram(PROGRAM_ID, new Uint8Array(readFileSync(path)));

    const harness = new Harness(svm, await generateKeyPairSigner());
    await harness.setUpgradeAuthority(harness.upgradeAuthority.address);
    harness.setTime(options.startTime ?? FIRST_START - WEEK);
    await harness.fund(harness.upgradeAuthority.address, 100n * 10n ** 9n);
    return harness;
  }

  // ---------------------------------------------------------------------- clock

  /** The chain's unix time in seconds — the only clock any rule reads (constitution VI). */
  now(): number {
    return Number(this.svm.getClock().unixTimestamp);
  }

  /** Move chain time to an absolute instant. Slots advance with it so slot-based code sees time pass. */
  setTime(unix: number): void {
    const clock = this.svm.getClock();
    const delta = BigInt(unix) - clock.unixTimestamp;
    clock.unixTimestamp = BigInt(unix);
    // A slot is ~400 ms; keep slots roughly in step so nothing sees time stand still.
    if (delta > 0n) clock.slot += (delta * 5n) / 2n;
    this.svm.setClock(clock);
    this.svm.expireBlockhash();
  }

  advance(seconds: number): void {
    this.setTime(this.now() + seconds);
  }

  // ------------------------------------------------------------------- wallets

  async newWallet(sol = 10n): Promise<KeyPairSigner> {
    const wallet = await generateKeyPairSigner();
    await this.fund(wallet.address, sol * 10n ** 9n);
    return wallet;
  }

  async fund(target: Address, lamportAmount: bigint): Promise<void> {
    const res = this.svm.airdrop(target, lamports(lamportAmount));
    if (res && 'err' in res && typeof res.err === 'function') {
      throw new Error(`airdrop failed: ${String(res)}`);
    }
  }

  balance(target: Address): bigint {
    return this.svm.getBalance(target) ?? 0n;
  }

  // -------------------------------------------------------------- ProgramData

  /**
   * Point the program's `ProgramData` at a different upgrade authority, or at
   * none. `initialize` accepts only the current authority (research R14), so a
   * scenario proves both directions: the authority succeeds, a stranger gets
   * `NotUpgradeAuthority`.
   */
  async setUpgradeAuthority(authority: Address | null): Promise<void> {
    const address = await programDataPda();
    const account = this.svm.getAccount(address);
    if (!account.exists) throw new Error('ProgramData is missing — the program was not loaded');

    // UpgradeableLoaderState::ProgramData { slot: u64, upgrade_authority: Option<Pubkey> }
    // behind a u32 variant tag (3). The Option always reserves its 32 key bytes, so the
    // ELF stays at offset 45 whether the authority is set or not.
    const data = new Uint8Array(account.data);
    if (data[0] !== 3) throw new Error(`ProgramData has variant tag ${data[0]}, expected 3`);
    if (authority) {
      data[12] = 1;
      data.set(getAddressEncoder().encode(authority), 13);
    } else {
      data[12] = 0;
      data.fill(0, 13, 45);
    }
    this.svm.setAccount({ ...account, data });
  }

  // -------------------------------------------------------------------- sending

  /** Send one transaction. Never throws on a program error: the result says what happened. */
  async send(
    instructions: readonly Instruction[],
    payer: TransactionSigner,
    extraSigners: readonly TransactionSigner[] = [],
  ): Promise<SendResult> {
    const message = pipe(
      createTransactionMessage({ version: 0 }),
      (m) => setTransactionMessageFeePayerSigner(payer, m),
      (m) =>
        setTransactionMessageLifetimeUsingBlockhash(
          { blockhash: this.svm.latestBlockhash(), lastValidBlockHeight: 0n },
          m,
        ),
      (m) => appendTransactionMessageInstructions(instructions, m),
      (m) => addSignersToTransactionMessage([payer, ...extraSigners], m),
    );
    const transaction = await signTransactionMessageWithSigners(message);
    const raw = this.svm.sendTransaction(transaction);
    // An identical transaction in the same blockhash is a duplicate to LiteSVM.
    this.svm.expireBlockhash();

    if ('err' in raw && typeof raw.err === 'function') {
      const logs = raw.meta().logs();
      return { ok: false, logs, errorCode: errorCodeFromLogs(logs), computeUnits: raw.meta().computeUnitsConsumed() };
    }
    const meta = raw as unknown as { logs(): string[]; computeUnitsConsumed(): bigint };
    return { ok: true, logs: meta.logs(), errorCode: null, computeUnits: meta.computeUnitsConsumed() };
  }

  async sendOrThrow(
    instructions: readonly Instruction[],
    payer: TransactionSigner,
    extraSigners: readonly TransactionSigner[] = [],
  ): Promise<SendResult> {
    const result = await this.send(instructions, payer, extraSigners);
    if (!result.ok) throw new ProgramCallFailed(result);
    return result;
  }

  // ------------------------------------------------------------ state injection

  private put(address: Address, data: Uint8Array, owner: Address = PROGRAM_ID): void {
    this.svm.setAccount({
      address,
      data,
      executable: false,
      lamports: lamports(this.svm.minimumBalanceForRentExemption(BigInt(data.length))),
      programAddress: owner,
      space: BigInt(data.length),
    });
  }

  /** An SPL mint owned by the token program. Returns its address. */
  injectMint(mint: Address, authority: Address | null, supply = 0n, decimals = 6): Address {
    this.put(mint, mintBytes(authority, supply, decimals), TOKEN_PROGRAM);
    return mint;
  }

  /** An SPL token account holding `amount`. */
  injectTokenAccount(at: Address, mint: Address, owner: Address, amount: bigint): Address {
    this.put(at, tokenAccountBytes(mint, owner, amount), TOKEN_PROGRAM);
    return at;
  }

  async injectConfig(over: Partial<ConfigAccount> = {}): Promise<Address> {
    const at = await configPda();
    const config: ConfigAccount = {
      admin: this.upgradeAuthority.address,
      operator: NO_KEY,
      sponsor: NO_KEY,
      skrMint: NO_KEY,
      feeDestination: NO_KEY,
      ladderGateEnabled: false,
      genesisGateEnabled: false,
      chipSet: { amounts: DEFAULT_CHIPS, version: 1, publishedAt: BigInt(FIRST_START - WEEK), maxTopTier: DEFAULT_CHIPS[3] },
      faucet: {
        solTopUp: 20_000_000n,
        sponsorFloat: 50_000_000n,
        dailyLamportCap: 720_000_000n,
        lamportDay: 0n,
        lamportsToday: 0n,
      },
      bump: 255,
      ...over,
    };
    this.put(at, Config.encode(config));
    return at;
  }

  /**
   * A league of its own, id ≥ 100, so a cohort built in it is index 0 with no
   * predecessor and `settle_count` can never answer 6045 by accident. Each call
   * returns a new league.
   */
  freshTestLeague(): LeagueRef {
    if (this.nextTestLeague > 255) throw new Error('out of test league ids (100–255)');
    return { leagueId: this.nextTestLeague++ };
  }

  async injectLeague(league: LeagueRef | number, over: LeagueOverrides = {}): Promise<Address> {
    const leagueId = typeof league === 'number' ? league : league.leagueId;
    const at = await leaguePda(leagueId);
    const account: LeagueConfigAccount = {
      leagueId,
      durationDays: 7,
      repsPerDay: 25,
      exercise: 0,
      dayLength: BigInt(DAY),
      firstStart: BigInt(FIRST_START),
      cadence: BigInt(WEEK),
      joinLock: BigInt(HOUR),
      nextIndex: 0,
      bump: 255,
      ...over,
    };
    this.put(at, LeagueConfig.encode(account));
    return at;
  }

  /**
   * A cohort, its vault and (if the league account does not exist) nothing else.
   * `start` defaults from `index` on the real weekly cadence; any field may be
   * overridden, including the league's timing for a compressed test league.
   */
  async injectCohort(
    league: LeagueRef | number,
    index: number,
    over: CohortOverrides = {},
  ): Promise<Address> {
    const leagueId = typeof league === 'number' ? league : league.leagueId;
    const { vaultAmount, ...fields } = over;
    const dayLength = fields.dayLength ?? BigInt(DAY);
    const start = fields.start ?? BigInt(FIRST_START) + BigInt(index) * BigInt(WEEK);
    const cohort: CohortAccount = {
      leagueId,
      index,
      start,
      joinCutoff: start - BigInt(HOUR),
      durationDays: 7,
      repsPerDay: 25,
      dayLength,
      exercise: 0,
      chips: DEFAULT_CHIPS,
      memberCount: 0,
      heldStake: 0n,
      pool: 0n,
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
      carryOverHeld: 0n,
      carryOverTarget: NO_KEY,
      vaultBump: 255,
      bump: 255,
      ...fields,
    };

    const at = await cohortPda(leagueId, index);
    this.put(at, Cohort.encode(cohort));

    // The vault is a token account owned by the token program, authority = the cohort.
    // It needs a mint; use the configured one if `initialize` ran or injectConfig did,
    // otherwise the system-program placeholder (fine for a checker that reads amounts).
    const configAddr = await configPda();
    const configAccount = this.svm.getAccount(configAddr);
    const mint = configAccount.exists ? Config.decode(new Uint8Array(configAccount.data)).skrMint : NO_KEY;
    const vault = await vaultPda(at);
    this.injectTokenAccount(
      vault,
      mint,
      at,
      vaultAmount ?? cohort.heldStake + cohort.pool + cohort.carryOverHeld,
    );
    return at;
  }

  async injectMembership(
    cohort: Address,
    wallet: Address,
    over: Partial<MembershipAccount> = {},
  ): Promise<Address> {
    const at = await membershipPda(cohort, wallet);
    const membership: MembershipAccount = {
      cohort,
      wallet,
      tier: 3,
      stake: DEFAULT_CHIPS[3],
      joinedAt: BigInt(FIRST_START - DAY),
      daysCompleted: 0,
      state: 'Joined',
      counted: false,
      bump: 255,
      ...over,
    };
    this.put(at, Membership.encode(membership));
    return at;
  }

  async injectProfile(wallet: Address, over: Partial<ProfileAccount> = {}): Promise<Address> {
    const at = await profilePda(wallet);
    const zero: LeagueProgress = { cleared: 0, forfeited: 0, currentStreak: 0, longestStreak: 0, lastChanged: 0n };
    const profile: ProfileAccount = {
      wallet,
      qualified: false,
      leagues: [{ ...zero }, { ...zero }, { ...zero }],
      running: { cohort: NO_KEY, end: 0n },
      pending: { cohort: NO_KEY, start: 0n, end: 0n },
      lastFaucetDay: -1n,
      bump: 255,
      ...over,
    };
    this.put(at, Profile.encode(profile));
    return at;
  }

  // ------------------------------------------------------------------- reading

  async readCohort(leagueId: number, index: number): Promise<CohortAccount> {
    return this.read(await cohortPda(leagueId, index), Cohort.decode, 'Cohort');
  }

  async readMembership(cohort: Address, wallet: Address): Promise<MembershipAccount> {
    return this.read(await membershipPda(cohort, wallet), Membership.decode, 'Membership');
  }

  async readProfile(wallet: Address): Promise<ProfileAccount> {
    return this.read(await profilePda(wallet), Profile.decode, 'Profile');
  }

  async readConfig(): Promise<ConfigAccount> {
    return this.read(await configPda(), Config.decode, 'Config');
  }

  /** SKR a token account holds — the amount field at offset 64. */
  tokenBalance(tokenAccount: Address): bigint {
    const account = this.svm.getAccount(tokenAccount);
    if (!account.exists) return 0n;
    return new DataView(account.data.buffer, account.data.byteOffset).getBigUint64(64, true);
  }

  private read<T>(at: Address, decode: (b: Uint8Array) => T, what: string): T {
    const account = this.svm.getAccount(at);
    if (!account.exists) throw new Error(`${what} ${at} does not exist`);
    return decode(new Uint8Array(account.data));
  }

  // ------------------------------------------------------------------ snapshot

  /**
   * Everything the program owns, decoded, in the shape `assertInvariants` reads.
   * `getProgramAccounts` finds each account by its discriminator, so nothing has to
   * be registered as it is created — through an instruction or by injection.
   */
  async snapshot(): Promise<Snapshot> {
    const cohorts: CohortSnapshot[] = [];
    const memberships: MembershipSnapshot[] = [];
    const rawProfiles: { address: Address; account: ProfileAccount }[] = [];

    const same = (a: Uint8Array, b: Uint8Array) => a.length >= 8 && b.every((x, i) => a[i] === x);

    for (const acct of this.svm.getProgramAccounts(PROGRAM_ID)) {
      const data = new Uint8Array(acct.data);
      if (same(data, Cohort.discriminator)) {
        const c = Cohort.decode(data);
        const vault = await vaultPda(acct.address);
        cohorts.push({
          address: acct.address,
          leagueId: c.leagueId,
          index: c.index,
          start: Number(c.start),
          joinCutoff: Number(c.joinCutoff),
          durationDays: c.durationDays,
          dayLength: Number(c.dayLength),
          chips: c.chips,
          memberCount: c.memberCount,
          heldStake: c.heldStake,
          pool: c.pool,
          carryOverReceived: c.carryOverReceived,
          forfeitsRecorded: c.forfeitsRecorded,
          counted: c.counted,
          finisherCount: c.finisherCount,
          finishersPerTier: c.finishersPerTier,
          totalFinisherStake: c.totalFinisherStake,
          settled: c.settled,
          sharePerTier: c.sharePerTier,
          roundingRemainder: c.roundingRemainder,
          noFinisherFee: c.noFinisherFee,
          carryOverSent: c.carryOverSent,
          carryOverTarget: c.carryOverTarget === NO_KEY ? null : c.carryOverTarget,
          carryOverHeld: c.carryOverHeld,
          vaultAmount: this.tokenBalance(vault),
        });
      } else if (same(data, Membership.discriminator)) {
        const m = Membership.decode(data);
        memberships.push({
          address: acct.address,
          cohort: m.cohort,
          wallet: m.wallet,
          tier: m.tier,
          stake: m.stake,
          daysCompleted: m.daysCompleted,
          state: m.state,
          counted: m.counted,
        });
      } else if (same(data, Profile.discriminator)) {
        rawProfiles.push({ address: acct.address, account: Profile.decode(data) });
      }
    }

    const leagueOf = new Map(cohorts.map((c) => [c.address, c.leagueId]));
    const profiles: ProfileSnapshot[] = rawProfiles.map(({ account: p }) => {
      const asSnapshot = (x: LeagueProgress): LeagueProgressSnapshot => ({
        cleared: x.cleared,
        forfeited: x.forfeited,
        currentStreak: x.currentStreak,
        longestStreak: x.longestStreak,
        lastChanged: Number(x.lastChanged),
      });
      const leagues: Record<number, LeagueProgressSnapshot> = {
        0: asSnapshot(p.leagues[0]),
        1: asSnapshot(p.leagues[1]),
      };
      // Every test league shares slot 2 (`Profile::slot_of`), so it can be checked
      // against one league's history only. With exactly one test league involved the
      // derivation is exact; with several it is undefined, so the slot is left out.
      const testLeagues = new Set(
        memberships
          .filter((m) => m.wallet === p.wallet)
          .map((m) => leagueOf.get(m.cohort))
          .filter((id): id is number => id !== undefined && id >= 100),
      );
      if (testLeagues.size === 1) leagues[[...testLeagues][0]!] = asSnapshot(p.leagues[2]);

      return {
        wallet: p.wallet,
        qualified: p.qualified,
        leagues,
        runningCohort: p.running.cohort === NO_KEY ? null : p.running.cohort,
        runningEnd: Number(p.running.end),
        pendingCohort: p.pending.cohort === NO_KEY ? null : p.pending.cohort,
        pendingStart: Number(p.pending.start),
        pendingEnd: Number(p.pending.end),
        lastFaucetDay: Number(p.lastFaucetDay),
      };
    });

    // None of these are program-owned, so `getProgramAccounts` reaches none of
    // them — and they are the far side of every transfer the program makes.
    // Without them a fee or a payout that went to the wrong token account is
    // invisible: custody still balances and the pool still adds up. `undefined`
    // means "nothing configured yet", which is not the same as a balance of nil.
    const account = this.svm.getAccount(await configPda());
    const config = account.exists ? Config.decode(new Uint8Array(account.data)) : undefined;
    const balances: Record<string, bigint> = {};
    if (config && config.skrMint !== NO_KEY) {
      for (const p of profiles) {
        const ata = await associatedTokenPda(p.wallet as Address, config.skrMint);
        balances[p.wallet] = this.tokenBalance(ata);
      }
    }

    return {
      now: this.now(),
      cohorts,
      memberships,
      profiles,
      feeDestination:
        config && config.feeDestination !== NO_KEY
          ? this.tokenBalance(config.feeDestination)
          : undefined,
      walletBalances: config ? balances : undefined,
      faucetMint: config ? config.chipSet.maxTopTier : undefined,
    };
  }
}

export const createHarness = (options?: HarnessOptions): Promise<Harness> => Harness.create(options);

export { UPGRADEABLE_LOADER };
