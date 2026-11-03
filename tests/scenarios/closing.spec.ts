/**
 * Closing a paid-out round. Task T381.
 *
 * What this file holds the contract to (FR-035b, FR-035c, INV-6):
 *
 *  - **Rent goes back to whoever paid it.** `close_membership` returns a
 *    membership's rent to its own wallet; `close_cohort` returns the vault's and
 *    the cohort's to `Config.operator`. Both are permissionless and the caller
 *    chooses nothing.
 *  - **Only once nothing is owed.** The cohort is settled and its books are at
 *    zero — a finisher who never claims keeps the round open for good, so claims
 *    still never expire (FR-035) — no comeback join can still use it, its grace
 *    (30 days, 1 day in a test league) has passed, and the league's next cohort
 *    is settled. Before then each refuses with `RoundNotClosable` and changes
 *    nothing.
 *  - **Memberships first.** `close_cohort` wants every membership closed, so each
 *    `MembershipClosed` carries the round's whole summary (FR-035d).
 *  - **Dust cannot hold a round open.** SKR sent to the vault from outside its
 *    books goes to operations before the vault closes.
 *  - **`release_comeback` still works** once its round is closed: it takes any
 *    later settled cohort of the league (FR-037h).
 */

import { generateKeyPairSigner, getAddressDecoder, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  DAY,
  DEFAULT_CHIPS,
  FIRST_START,
  SKR,
  createHarness,
  expectProgramError,
  oneEvent,
  type Harness,
} from '../helpers/harness.js';
import { closeCohortIx, closeMembershipIx, releaseComebackIx, type FeeDestinations } from '../helpers/instructions.js';
import { cohortPda, membershipPda, profilePda, vaultPda } from '../helpers/pdas.js';

const DAY_LENGTH = 60;
const DURATION = 7;
const START = FIRST_START;
const CADENCE = 2 * DURATION * DAY_LENGTH;
const startOf = (index: number) => START + index * CADENCE;
const endOf = (index: number) => startOf(index) + DURATION * DAY_LENGTH;
/** The first instant cohort 0 of a test league may close: its end plus a day. */
const CLOSABLE = endOf(0) + DAY;

const STAKE = DEFAULT_CHIPS[2]!;
const SHARE = 7n * SKR;

/**
 * `MembershipClosed { cohort, wallet, league, index, start, end, tier, stake,
 * days_completed, finished, paid, comeback }`.
 */
const decodeMembershipClosed = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return {
    cohort: getAddressDecoder().decode(bytes.slice(0, 32)),
    wallet: getAddressDecoder().decode(bytes.slice(32, 64)),
    league: view.getUint8(64),
    index: view.getUint32(65, true),
    start: view.getBigInt64(69, true),
    end: view.getBigInt64(77, true),
    tier: view.getUint8(85),
    stake: view.getBigUint64(86, true),
    daysCompleted: view.getUint8(94),
    finished: view.getUint8(95) === 1,
    paid: view.getBigUint64(96, true),
    comeback: view.getBigUint64(104, true),
  };
};

/** `CohortClosed { cohort, league, index, swept }`. */
const decodeCohortClosed = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return {
    cohort: getAddressDecoder().decode(bytes.slice(0, 32)),
    league: view.getUint8(32),
    index: view.getUint32(33, true),
    swept: view.getBigUint64(37, true),
  };
};

describe('close_membership and close_cohort (FR-035b, FR-035c)', () => {
  let h: Harness;
  let mint: Address;
  let fees: FeeDestinations;
  let operator: KeyPairSigner;
  let crank: KeyPairSigner;
  let league: number;

  beforeEach(async () => {
    h = await createHarness({ startTime: START - DAY_LENGTH });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 100_000_000n * SKR);
    fees = await h.injectFeeDestinations(mint);
    operator = await h.newWallet();
    await h.injectConfig({
      skrMint: mint,
      feeDestination: fees.operations,
      developerDestination: fees.developer,
      operator: operator.address,
    });
    crank = await h.newWallet();
    league = h.freshTestLeague().leagueId;
    await h.injectLeague(league, {
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      firstStart: BigInt(START),
      cadence: BigInt(CADENCE),
      joinLock: 0n,
      nextIndex: 2,
    });
  });

  /** Cohort `index`, settled and paid out unless told otherwise. */
  const settledCohort = (index: number, over: Parameters<Harness['injectCohort']>[2] = {}) =>
    h.injectCohort(league, index, {
      start: BigInt(startOf(index)),
      joinCutoff: BigInt(startOf(index)),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      settled: true,
      ...over,
    });

  /** A round with one claimed finisher and one counted forfeit, and a settled next cohort. */
  const paidOutRound = async () => {
    const cohort = await settledCohort(0, {
      memberCount: 2,
      counted: 2,
      finisherCount: 1,
      sharePerTier: [0n, 0n, SHARE, 0n],
    });
    await settledCohort(1);
    const finisher = (await generateKeyPairSigner()).address;
    const forfeiter = (await generateKeyPairSigner()).address;
    await h.injectMembership(cohort, finisher, {
      tier: 2,
      stake: STAKE,
      daysCompleted: DURATION,
      state: 'Claimed',
      counted: true,
    });
    await h.injectMembership(cohort, forfeiter, {
      tier: 2,
      stake: STAKE,
      daysCompleted: 3,
      state: 'Forfeited',
      counted: true,
    });
    return { cohort, finisher, forfeiter };
  };

  const closeMember = async (wallet: Address, index = 0) =>
    h.send([await closeMembershipIx(league, index, wallet)], crank);
  const closeRound = async (index = 0) => h.send([await closeCohortIx(fees, operator.address, league, index)], crank);

  it('returns each membership’s rent to its member and the summary in MembershipClosed', async () => {
    const { cohort, finisher, forfeiter } = await paidOutRound();
    h.setTime(CLOSABLE);

    const at = await membershipPda(cohort, finisher);
    const rent = h.balance(at);
    expect(rent).toBeGreaterThan(0n);

    const result = await closeMember(finisher);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect(h.balance(at)).toBe(0n);
    expect(h.balance(finisher)).toBe(rent);
    expect(decodeMembershipClosed(oneEvent(result, 'MembershipClosed'))).toEqual({
      cohort,
      wallet: finisher,
      league,
      index: 0,
      start: BigInt(startOf(0)),
      end: BigInt(endOf(0)),
      tier: 2,
      stake: STAKE,
      daysCompleted: DURATION,
      finished: true,
      paid: STAKE + SHARE,
      comeback: 0n,
    });
    expect((await h.readCohort(league, 0)).memberCount).toBe(1);

    const lost = await closeMember(forfeiter);
    expect(lost.ok, lost.logs.join('\n')).toBe(true);
    const summary = decodeMembershipClosed(oneEvent(lost, 'MembershipClosed'));
    expect(summary.finished).toBe(false);
    expect(summary.paid).toBe(0n);
    expect(summary.daysCompleted).toBe(3);
    expect((await h.readCohort(league, 0)).memberCount).toBe(0);
  });

  it('closes the vault and the cohort once every membership is closed, both rents to the operator', async () => {
    const { cohort, finisher, forfeiter } = await paidOutRound();
    h.setTime(CLOSABLE);

    expectProgramError(await closeRound(), 'RoundNotClosable');

    await h.sendOrThrow([await closeMembershipIx(league, 0, finisher)], crank);
    await h.sendOrThrow([await closeMembershipIx(league, 0, forfeiter)], crank);

    const vault = await vaultPda(cohort);
    const rents = h.balance(cohort) + h.balance(vault);
    const before = h.balance(operator.address);
    const result = await closeRound();
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect(h.balance(cohort)).toBe(0n);
    expect(h.balance(vault)).toBe(0n);
    expect(h.balance(operator.address) - before).toBe(rents);
    expect(decodeCohortClosed(oneEvent(result, 'CohortClosed'))).toEqual({ cohort, league, index: 0, swept: 0n });
  });

  it('sweeps SKR sent to the vault from outside its books to operations', async () => {
    const cohort = await settledCohort(0, { vaultAmount: 5n });
    await settledCohort(1);
    h.setTime(CLOSABLE);

    const result = await closeRound();
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect(h.tokenBalance(fees.operations)).toBe(5n);
    expect(decodeCohortClosed(oneEvent(result, 'CohortClosed')).swept).toBe(5n);
    expect(h.balance(cohort)).toBe(0n);
  });

  it('refuses before the grace has passed, and a judged league waits 30 days', async () => {
    const { finisher } = await paidOutRound();
    h.setTime(CLOSABLE - 1);
    expectProgramError(await closeMember(finisher), 'RoundNotClosable');

    const judged = 0;
    await h.injectLeague(judged, { nextIndex: 2 });
    const start = BigInt(FIRST_START);
    const real = await h.injectCohort(judged, 0, { start, settled: true });
    await h.injectCohort(judged, 1, { settled: true });
    const end = Number(start) + 7 * DAY;
    const close = async () => h.send([await closeCohortIx(fees, operator.address, judged, 0)], crank);

    h.setTime(end + 29 * DAY);
    expectProgramError(await close(), 'RoundNotClosable');
    h.setTime(end + 30 * DAY);
    const result = await close();
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect(h.balance(real)).toBe(0n);
  });

  it('keeps a round open while a finisher has not claimed — claims never expire (FR-035)', async () => {
    const cohort = await settledCohort(0, {
      memberCount: 1,
      counted: 1,
      finisherCount: 1,
      heldStake: STAKE,
      pool: SHARE,
      sharePerTier: [0n, 0n, SHARE, 0n],
    });
    await settledCohort(1);
    const finisher = (await generateKeyPairSigner()).address;
    await h.injectMembership(cohort, finisher, { tier: 2, stake: STAKE, daysCompleted: DURATION, state: 'Finished', counted: true });

    h.setTime(CLOSABLE + 365 * DAY);
    expectProgramError(await closeMember(finisher), 'RoundNotClosable');
    expectProgramError(await closeRound(), 'RoundNotClosable');
    expect(h.balance(await membershipPda(cohort, finisher))).toBeGreaterThan(0n);
  });

  it('waits out the comeback window: end + 2 × cadence + day_length, to the second (FR-035c)', async () => {
    // A cadence of two days puts the window past the 1-day test grace, so the window alone decides.
    const cadence = 2 * DAY;
    await h.injectLeague(league, {
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      firstStart: BigInt(START),
      cadence: BigInt(cadence),
      joinLock: 0n,
      nextIndex: 2,
    });
    await settledCohort(0);
    await h.injectCohort(league, 1, { start: BigInt(START + cadence), durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), settled: true });
    const window = endOf(0) + 2 * cadence + DAY_LENGTH;

    h.setTime(window);
    expectProgramError(await closeRound(), 'RoundNotClosable');
    h.setTime(window + 1);
    const result = await closeRound();
    expect(result.ok, result.logs.join('\n')).toBe(true);
  });

  it('refuses a membership not counted, or neither claimed nor forfeited', async () => {
    const cohort = await settledCohort(0, { memberCount: 2, counted: 2 });
    await settledCohort(1);
    const uncounted = (await generateKeyPairSigner()).address;
    const joined = (await generateKeyPairSigner()).address;
    await h.injectMembership(cohort, uncounted, { state: 'Forfeited', counted: false });
    await h.injectMembership(cohort, joined, { state: 'Joined', counted: true });
    h.setTime(CLOSABLE);
    expectProgramError(await closeMember(uncounted), 'RoundNotClosable');
    expectProgramError(await closeMember(joined), 'RoundNotClosable');
    expectProgramError(await closeRound(), 'RoundNotClosable');
  });

  it('reports a comeback refund inside what the claim paid', async () => {
    const refund = 5n * SKR;
    const cohort = await settledCohort(0, {
      memberCount: 1,
      counted: 1,
      finisherCount: 1,
      sharePerTier: [0n, 0n, SHARE, 0n],
      comebackOwed: refund,
      comebackFunded: refund,
      comebackClaimed: refund,
    });
    await settledCohort(1);
    const finisher = (await generateKeyPairSigner()).address;
    await h.injectMembership(cohort, finisher, { tier: 2, stake: STAKE, daysCompleted: DURATION, state: 'Claimed', counted: true, comebackEntitled: refund });
    h.setTime(CLOSABLE);
    const result = await closeMember(finisher);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    const summary = decodeMembershipClosed(oneEvent(result, 'MembershipClosed'));
    expect(summary.comeback).toBe(refund);
    expect(summary.paid).toBe(STAKE + SHARE + refund);
  });

  it('refuses while the round is unsettled or the next cohort is not settled', async () => {
    await settledCohort(0, { settled: false });
    await settledCohort(1);
    h.setTime(CLOSABLE);
    expectProgramError(await closeRound(), 'RoundNotClosable');

    await settledCohort(0);
    await settledCohort(1, { settled: false });
    expectProgramError(await closeRound(), 'RoundNotClosable');
  });

  it('refuses a cohort passed as the next one that is not index + 1', async () => {
    await settledCohort(0);
    await settledCohort(1);
    await settledCohort(2);
    h.setTime(CLOSABLE);
    const result = await h.send([await closeCohortIx(fees, operator.address, league, 0, 2)], crank);
    expectProgramError(result, 'AccountMismatch');
  });
});

describe('release_comeback once its round is closed (FR-037h)', () => {
  it('takes a later settled cohort of the league in place of the round', async () => {
    const h = await createHarness({ startTime: START - DAY_LENGTH });
    const mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 100_000_000n * SKR);
    const fees = await h.injectFeeDestinations(mint);
    await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });
    const league = h.freshTestLeague().leagueId;
    const pledge = STAKE / 10n;
    await h.injectLeague(league, {
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      firstStart: BigInt(START),
      cadence: BigInt(CADENCE),
      joinLock: 0n,
      nextIndex: 3,
      comebackReserve: pledge,
    });
    // Round 0 is closed: no account. Cohort 2 is settled.
    await h.injectCohort(league, 2, {
      start: BigInt(startOf(2)),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      settled: true,
    });
    const wallet = (await generateKeyPairSigner()).address;
    const zero = {
      cleared: 0,
      forfeited: 0,
      currentStreak: 0,
      longestStreak: 0,
      lastChanged: 0n,
      comebackStake: 0n,
      comebackLeague: 0,
      comebackRound: 0,
      comebackLastRound: 0,
    };
    await h.injectProfile(wallet, {
      leagues: [{ ...zero }, { ...zero }, { ...zero, forfeited: 1, comebackStake: STAKE, comebackLeague: league }],
    });
    expect(h.balance(await cohortPda(league, 0))).toBe(0n);

    const caller = await h.newWallet();
    const result = await h.send([await releaseComebackIx(caller.address, league, wallet, 2)], caller);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    const after = await h.readLeague(league);
    expect(after.comebackReserve).toBe(0n);
    expect(after.boostReserve).toBe(pledge);
    expect((await h.readProfile(wallet)).leagues[2]!.comebackStake).toBe(0n);
    expect(h.balance(await profilePda(wallet))).toBeGreaterThan(0n);
  });
});
