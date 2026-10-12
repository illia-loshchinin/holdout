/**
 * Joining the comeback before the forfeit is recorded. ES-17, FR-037g, research R22.
 *
 * A member who just missed a deadline taps Join on the comeback offer before
 * automation has recorded the forfeit. The forfeit is *derived* (INV-8), but an
 * unrecorded one still holds the profile's running slot, and `join_cohort` refuses
 * an overlap with a running cohort whose end is in the future (6026). So the app
 * sends ONE transaction of two instructions:
 *
 *   [ record_forfeits(caller = the member, their own (membership, profile) pair),
 *     join_cohort(the next cohort, running cohort NOT passed) ]
 *
 * `record_forfeits` is permissionless and idempotent; it frees the slot, and with
 * no running cohort passed `join_cohort` does not write the old cohort's end back
 * into the cleared slot. The program is unchanged: this file proves the app's
 * recipe against the real binary, step by step under the invariant checker.
 */

import {
  addSignersToTransactionMessage,
  appendTransactionMessageInstructions,
  createTransactionMessage,
  getTransactionEncoder,
  pipe,
  setTransactionMessageFeePayerSigner,
  setTransactionMessageLifetimeUsingBlockhash,
  signTransactionMessageWithSigners,
  generateKeyPairSigner,
  type Address,
  type Instruction,
  type KeyPairSigner,
} from '@solana/kit';
import { describe, expect, it } from 'vitest';

import {
  DEFAULT_CHIPS,
  FIRST_START,
  SKR,
  createHarness,
  expectProgramError,
  type Harness,
  type SendResult,
} from '../helpers/harness.js';
import {
  claimPayoutIx,
  comebackAccounts,
  forfeitPairs,
  joinCohortIx,
  recordForfeitsIx,
  releaseComebackIx,
  settleCountIx,
  settleFinalizeIx,
  submitDayIx,
  type FeeDestinations,
} from '../helpers/instructions.js';
import { PROGRAM_ID, associatedTokenPda, cohortPda } from '../helpers/pdas.js';
import { assertInvariants, forfeitSplit, type Snapshot } from '../invariants/checker.js';

const NO_KEY = '11111111111111111111111111111111' as Address;
const DAY_LENGTH = 60;
const DURATION = 7;
const START0 = FIRST_START;
const END0 = START0 + DURATION * DAY_LENGTH;
/** The next cohort starts while cohort 0 is still running: the round after the miss (FR-037e). */
const START1 = START0 + 3 * DAY_LENGTH;
const END1 = START1 + DURATION * DAY_LENGTH;
const JOIN_LOCK = 10;
const MISSED = START0 + DAY_LENGTH - 1; // the deadline of day 0 of cohort 0
const OLD_TIER = 3; // 100 SKR
const OLD_STAKE = DEFAULT_CHIPS[OLD_TIER];
/** The legacy transaction size limit. */
const MAX_TX_BYTES = 1232;

interface World {
  h: Harness;
  mint: Address;
  fees: FeeDestinations;
  crank: KeyPairSigner;
  member: KeyPairSigner;
  league: number;
  cohort0: Address;
  cohort1: Address;
  /** Send, insist it lands, check every invariant against the step before. */
  act(instructions: readonly Instruction[], signer: KeyPairSigner): Promise<SendResult>;
}

/** Cohort 0 (running, member joined it, day 0 missed) and cohort 1 (open), forfeit NOT recorded. */
const world = async (): Promise<World> => {
  const h = await createHarness({ startTime: START0 - DAY_LENGTH });
  const mint = (await generateKeyPairSigner()).address;
  h.injectMint(mint, null, 1_000_000_000n * SKR);
  const fees = await h.injectFeeDestinations(mint);
  await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });

  const league = h.freshTestLeague().leagueId;
  await h.injectLeague(league, {
    durationDays: DURATION,
    dayLength: BigInt(DAY_LENGTH),
    firstStart: BigInt(START0),
    cadence: BigInt(3 * DAY_LENGTH),
    joinLock: BigInt(JOIN_LOCK),
    nextIndex: 2,
  });
  const cohortAt = (index: number, start: number) =>
    h.injectCohort(league, index, {
      start: BigInt(start),
      joinCutoff: BigInt(start - JOIN_LOCK),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
    });
  const cohort0 = await cohortAt(0, START0);
  const cohort1 = await cohortAt(1, START1);

  const member = await h.newWallet();
  h.injectTokenAccount(await associatedTokenPda(member.address, mint), mint, member.address, 10n * OLD_STAKE);

  let previous: Snapshot | undefined;
  const w: World = {
    h,
    mint,
    fees,
    crank: await h.newWallet(),
    member,
    league,
    cohort0,
    cohort1,
    act: async (instructions, signer) => {
      previous ??= await h.snapshot();
      const result = await h.sendOrThrow(instructions, signer);
      const current = await h.snapshot();
      assertInvariants(current, previous);
      previous = current;
      return result;
    },
  };

  // Joined cohort 0 while it was open (the lobby place), then its day-0 deadline passes unmet.
  await w.act([await joinCohortIx(member.address, mint, league, 0, OLD_TIER)], member);
  h.setTime(MISSED + 1);
  return w;
};

/** The two instructions of the app's recipe. */
const comebackJoin = async (w: World, tier: number): Promise<Instruction[]> => [
  await recordForfeitsIx(w.member.address, w.fees, w.league, 0, await forfeitPairs(w.cohort0, [w.member.address])),
  await joinCohortIx(w.member.address, w.mint, w.league, 1, tier),
];

/** The serialized size of the recipe as a legacy transaction, one signature (the member pays and signs). */
const legacySize = async (w: World, instructions: readonly Instruction[]): Promise<number> => {
  const message = pipe(
    createTransactionMessage({ version: 'legacy' }),
    (m) => setTransactionMessageFeePayerSigner(w.member, m),
    (m) =>
      setTransactionMessageLifetimeUsingBlockhash({ blockhash: w.h.svm.latestBlockhash(), lastValidBlockHeight: 0n }, m),
    (m) => appendTransactionMessageInstructions(instructions, m),
    (m) => addSignersToTransactionMessage([w.member], m),
  );
  return getTransactionEncoder().encode(await signTransactionMessageWithSigners(message)).length;
};

describe('the comeback join before the forfeit is recorded (ES-17, FR-037g, R22)', () => {
  it('records the forfeit and joins the next cohort in one transaction', async () => {
    const w = await world();
    const { h, member } = w;

    // Before: the forfeit is derived but not recorded; the running slot is held.
    const oldBefore = await h.readMembership(w.cohort0, member.address);
    expect(oldBefore.state).toBe('Joined');
    const profileBefore = await h.readProfile(member.address);
    // The lobby place is promoted lazily, inside the next instruction that touches the profile.
    expect(profileBefore.pending.cohort, "the unrecorded forfeit still holds cohort 0's place").toBe(w.cohort0);
    expect(profileBefore.pending.end).toBe(BigInt(END0));
    const developerBefore = h.tokenBalance(w.fees.developer);
    const operationsBefore = h.tokenBalance(w.fees.operations);
    const memberSkrBefore = h.tokenBalance(await associatedTokenPda(member.address, w.mint));

    const newTier = 1; // a smaller chip than the lost one: the app lets the member choose
    const instructions = await comebackJoin(w, newTier);

    // (5) the legacy transaction fits.
    const size = await legacySize(w, instructions);
    console.log(`comeback join (record_forfeits + join_cohort), legacy transaction: ${size} bytes (limit ${MAX_TX_BYTES})`);
    expect(size).toBeLessThanOrEqual(MAX_TX_BYTES);

    // (1) the transaction succeeds, (7) INV-1 and the rest hold after it (act asserts them).
    const result = await w.act(instructions, member);
    expect(result.ok).toBe(true);

    // (2) the old membership is Forfeited, the split applied as for any record_forfeits.
    const oldAfter = await h.readMembership(w.cohort0, member.address);
    expect(oldAfter.state).toBe('Forfeited');
    const split = forfeitSplit(OLD_STAKE);
    const cohort0 = await h.readCohort(w.league, 0);
    expect(cohort0.forfeitsRecorded).toBe(1);
    expect(cohort0.pool).toBe(split.pool);
    expect(cohort0.comebackDue).toBe(split.comeback);
    expect(h.tokenBalance(w.fees.developer) - developerBefore).toBe(split.developer);
    expect(h.tokenBalance(w.fees.operations) - operationsBefore).toBe(split.operations);
    expect([split.pool, split.comeback, split.developer, split.operations]).toEqual([
      70n * SKR,
      10n * SKR,
      10n * SKR,
      10n * SKR,
    ]);

    // (3) the new membership exists with the chosen tier and stake.
    const newStake = DEFAULT_CHIPS[newTier]!;
    const fresh = await h.readMembership(w.cohort1, member.address);
    expect(fresh.state).toBe('Joined');
    expect(fresh.tier).toBe(newTier);
    expect(fresh.stake).toBe(newStake);
    const cohort1 = await h.readCohort(w.league, 1);
    expect(cohort1.memberCount).toBe(1);
    expect(cohort1.heldStake).toBe(newStake);
    expect(h.tokenBalance(await associatedTokenPda(member.address, w.mint))).toBe(memberSkrBefore - newStake);

    // (4) the running slot is cleared and holds nothing of the old cohort; the lobby place is the new cohort.
    const profile = await h.readProfile(member.address);
    expect(profile.running.cohort).toBe(NO_KEY);
    expect(profile.running.end, 'the old end is not written into the cleared slot').toBe(0n);
    expect(profile.pending.cohort).toBe(w.cohort1);
    expect(profile.pending.start).toBe(BigInt(START1));
    expect(profile.pending.end).toBe(BigInt(END1));
  });

  it('is refused as a bare join_cohort with the running cohort passed (the negative control)', async () => {
    const w = await world();
    const { h, member } = w;
    const snapshotBefore = await h.snapshot();

    const result = await h.send([await joinCohortIx(member.address, w.mint, w.league, 1, 1, w.cohort0)], member);
    expect(result.ok).toBe(false);
    expectProgramError(result, 'ObligationsOverlap'); // 6026
    expect(result.errorCode).toBe(6026);

    // Nothing moved: the forfeit is still unrecorded and no place was taken.
    expect((await h.readMembership(w.cohort0, member.address)).state).toBe('Joined');
    expect((await h.readProfile(member.address)).pending.cohort).toBe(w.cohort0);
    const cohort1 = await h.readCohort(w.league, 1);
    expect(cohort1.memberCount).toBe(0);
    assertInvariants(await h.snapshot(), snapshotBefore);
  });

  it('is also refused without the running cohort, since a set running slot needs it (AccountMismatch)', async () => {
    // The reason the recipe must record first rather than just omit the account.
    const w = await world();
    const result = await w.h.send([await joinCohortIx(w.member.address, w.mint, w.league, 1, 1)], w.member);
    expect(result.ok).toBe(false);
    expect(result.errorCode).not.toBeNull();
    expect(result.errorCode).not.toBe(6026);
  });

  it('then owes the member min(10 % of the lost stake, the new stake) on finishing the round after the miss, and pays it', async () => {
    const w = await world();
    const { h, member } = w;
    const newTier = 0; // 10 SKR: as small as a chip gets, and exactly the 10 pledged
    await w.act(await comebackJoin(w, newTier), member);

    // Finish all seven days of cohort 1; once cohort 0 has ended, count and finalize it (FR-061a).
    let settledZero = false;
    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(START1 + day * DAY_LENGTH);
      if (!settledZero && h.now() >= END0) {
        h.setTime(END0);
        await w.act(
          [await settleCountIx(w.crank.address, w.fees, w.league, 0, await forfeitPairs(w.cohort0, [member.address]))],
          w.crank,
        );
        await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 0)], w.crank);
        settledZero = true;
        h.setTime(START1 + day * DAY_LENGTH);
      }
      await w.act([await submitDayIx(member.address, w.league, 1, day, new Uint8Array(32).fill(day + 1))], member);
    }
    expect(settledZero, 'cohort 0 was counted while cohort 1 ran').toBe(true);

    // The forfeit is counted: its pledge is in the league's comeback reserve and
    // the profile names cohort 1, the first to start after the missed deadline (R22).
    const progress = (await h.readProfile(member.address)).leagues[2]!;
    expect(progress.comebackStake).toBe(OLD_STAKE);
    expect([progress.comebackLeague, progress.comebackRound, progress.comebackLastRound]).toEqual([w.league, 1, 1]);
    expect(START1).toBeGreaterThan(MISSED);
    const league = await h.readLeague(w.league);
    expect(league.comebackReserve).toBe(10n * SKR);
    // Nobody finished cohort 0, so its pool went to the boost reserve whole.
    expect(league.boostReserve).toBe(70n * SKR);

    h.setTime(END1);
    await w.act(
      [
        await settleCountIx(
          w.crank.address,
          w.fees,
          w.league,
          1,
          await forfeitPairs(w.cohort1, [member.address]),
          w.cohort0,
        ),
      ],
      w.crank,
    );
    const membership = await h.readMembership(w.cohort1, member.address);
    expect(membership.comebackEntitled).toBe(10n * SKR); // min(10 % of 100 = 10, 10)
    expect((await h.readProfile(member.address)).leagues[2]!.comebackStake, 'used up').toBe(0n);
    expect((await h.readCohort(w.league, 1)).comebackOwed).toBe(10n * SKR);

    // Finalization draws it in full from the comeback reserve, and the claim pays it with the stake.
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 1)], w.crank);
    expect((await h.readCohort(w.league, 1)).comebackFunded).toBe(10n * SKR);
    const walletBefore = h.tokenBalance(await associatedTokenPda(member.address, w.mint));
    await w.act([await claimPayoutIx(member.address, w.mint, w.league, 1)], member);
    expect(h.tokenBalance(await associatedTokenPda(member.address, w.mint)) - walletBefore).toBe(
      DEFAULT_CHIPS[newTier]! + 10n * SKR,
    );
  });
});

// ============================================================ a miss on the last day
//
// The deadline of the last day is the second before the next cohort starts, and
// the next cohort's joining closed ten seconds earlier. A member who misses it
// has no open cohort to come back to — so the round that makes the comeback good
// is the one that has just started, and joining it stays possible until its first
// day's deadline, with the forfeited membership and its cohort as proof
// (FR-037e, `guards::require_joinable`). The round after it is the alternative.

/** Cohorts follow each other back to back: cohort `i + 1` starts the second cohort `i` ends. */
const CADENCE = DURATION * DAY_LENGTH;
const startOf = (index: number) => START0 + index * CADENCE;
const endOf = (index: number) => startOf(index) + DURATION * DAY_LENGTH;
/** The last second of day `k` of cohort `index`. */
const deadlineOf = (index: number, k: number) => startOf(index) + (k + 1) * DAY_LENGTH - 1;

interface LastDayWorld extends Omit<World, 'cohort0' | 'cohort1'> {
  cohorts: Address[];
}

/**
 * A member who joined cohort 0, sat six of its seven days and missed the last.
 * The clock stands on the first second of cohort 1, which started the second
 * their last deadline passed; cohort 2 is still open. Nothing is recorded.
 */
const lastDayWorld = async (nextIndex = 3): Promise<LastDayWorld> => {
  const h = await createHarness({ startTime: START0 - DAY_LENGTH });
  const mint = (await generateKeyPairSigner()).address;
  h.injectMint(mint, null, 1_000_000_000n * SKR);
  const fees = await h.injectFeeDestinations(mint);
  await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });

  const league = h.freshTestLeague().leagueId;
  await h.injectLeague(league, {
    durationDays: DURATION,
    dayLength: BigInt(DAY_LENGTH),
    firstStart: BigInt(START0),
    cadence: BigInt(CADENCE),
    joinLock: BigInt(JOIN_LOCK),
    nextIndex,
  });
  const cohorts: Address[] = [];
  for (const i of [0, 1, 2]) {
    cohorts.push(
      await h.injectCohort(league, i, {
        start: BigInt(startOf(i)),
        joinCutoff: BigInt(startOf(i) - JOIN_LOCK),
        durationDays: DURATION,
        dayLength: BigInt(DAY_LENGTH),
      }),
    );
  }

  const member = await h.newWallet();
  h.injectTokenAccount(await associatedTokenPda(member.address, mint), mint, member.address, 10n * OLD_STAKE);

  let previous: Snapshot | undefined;
  const w: LastDayWorld = {
    h,
    mint,
    fees,
    crank: await h.newWallet(),
    member,
    league,
    cohorts,
    act: async (instructions, signer) => {
      previous ??= await h.snapshot();
      const result = await h.sendOrThrow(instructions, signer);
      const current = await h.snapshot();
      assertInvariants(current, previous);
      previous = current;
      return result;
    },
  };

  await w.act([await joinCohortIx(member.address, mint, league, 0, OLD_TIER)], member);
  for (let day = 0; day < DURATION - 1; day += 1) {
    h.setTime(startOf(0) + day * DAY_LENGTH);
    await w.act([await submitDayIx(member.address, league, 0, day, new Uint8Array(32).fill(day))], member);
  }
  h.setTime(endOf(0));
  return w;
};

/** The late join of cohort `index`, with the proof of the miss in cohort 0. */
const lateJoin = async (w: LastDayWorld, who: KeyPairSigner, tier: number, index = 1): Promise<Instruction> =>
  joinCohortIx(
    who.address,
    w.mint,
    w.league,
    index,
    tier,
    w.cohorts[0],
    undefined,
    await comebackAccounts(who.address, w.league, 0),
  );

describe('a miss on the last day: the round that just started stays joinable (FR-037e)', () => {
  it('lets the member join it late, takes the running slot at once, and pays the comeback on finishing it', async () => {
    const w = await lastDayWorld();
    const { h, member } = w;
    expect(h.now(), 'cohort 1 started the second the last deadline passed').toBe(deadlineOf(0, DURATION - 1) + 1);
    expect(h.now()).toBeGreaterThan(startOf(1) - JOIN_LOCK);

    // Closed to everyone else…
    const bystander = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(bystander.address, w.mint), w.mint, bystander.address, OLD_STAKE);
    expectProgramError(await h.send([await joinCohortIx(bystander.address, w.mint, w.league, 1, 1)], bystander), 'JoiningClosed');
    // …and to the member without the proof.
    expectProgramError(
      await h.send([await joinCohortIx(member.address, w.mint, w.league, 1, 1, w.cohorts[0])], member),
      'JoiningClosed',
    );

    const newTier = 1; // 25 SKR
    const result = await w.act([await lateJoin(w, member, newTier)], member);
    expect(result.ok).toBe(true);
    const fresh = await h.readMembership(w.cohorts[1]!, member.address);
    expect([fresh.state, fresh.tier, fresh.stake]).toEqual(['Joined', newTier, DEFAULT_CHIPS[newTier]]);
    // The cohort is running, so the place is the member's obligation now, not a lobby place.
    const profile = await h.readProfile(member.address);
    expect(profile.running.cohort).toBe(w.cohorts[1]);
    expect(profile.running.end).toBe(BigInt(endOf(1)));
    expect(profile.pending.cohort).toBe(NO_KEY);

    // Cohort 0 is over: count the forfeit, which opens the comeback on rounds 1 and 2.
    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 0, await forfeitPairs(w.cohorts[0]!, [member.address]))],
      w.crank,
    );
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 0)], w.crank);
    const progress = (await h.readProfile(member.address)).leagues[2]!;
    expect([progress.comebackStake, progress.comebackLeague, progress.comebackRound, progress.comebackLastRound]).toEqual([
      OLD_STAKE,
      w.league,
      1,
      2,
    ]);
    const league = await h.readLeague(w.league);
    expect([league.boostReserve, league.comebackReserve]).toEqual([70n * SKR, 10n * SKR]);

    // The member sits the round — day 0 is the very day they joined on.
    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(startOf(1) + day * DAY_LENGTH);
      await w.act([await submitDayIx(member.address, w.league, 1, day, new Uint8Array(32).fill(day + 1))], member);
    }
    h.setTime(endOf(1));
    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 1, await forfeitPairs(w.cohorts[1]!, [member.address]), w.cohorts[0])],
      w.crank,
    );
    expect((await h.readMembership(w.cohorts[1]!, member.address)).comebackEntitled).toBe(10n * SKR);
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 1)], w.crank);
    expect((await h.readCohort(w.league, 1)).comebackFunded).toBe(10n * SKR);

    const before = h.tokenBalance(await associatedTokenPda(member.address, w.mint));
    await w.act([await claimPayoutIx(member.address, w.mint, w.league, 1)], member);
    expect(h.tokenBalance(await associatedTokenPda(member.address, w.mint)) - before).toBe(DEFAULT_CHIPS[newTier]! + 10n * SKR);
  });

  it('keeps it open until the second its first day ends, and not a second longer (6021)', async () => {
    const at = async (now: number) => {
      const w = await lastDayWorld();
      w.h.setTime(now);
      return { w, result: await w.h.send([await lateJoin(w, w.member, 1)], w.member) };
    };

    const last = await at(deadlineOf(1, 0));
    expect(last.result.ok, last.result.logs.join('\n')).toBe(true);

    const late = await at(deadlineOf(1, 0) + 1);
    expectProgramError(late.result, 'JoiningClosed');
    expect((await late.w.h.readCohort(late.w.league, 1)).memberCount).toBe(0);
  });

  it('refuses the proof to anyone it is not about, and for any cohort but the round (6021)', async () => {
    const w = await lastDayWorld();
    const { h, member } = w;
    const stranger = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(stranger.address, w.mint), w.mint, stranger.address, 10n * OLD_STAKE);

    // A stranger holding the member's proof is still a stranger.
    const borrowed = await comebackAccounts(member.address, w.league, 0);
    expectProgramError(
      await h.send([await joinCohortIx(stranger.address, w.mint, w.league, 1, 1, undefined, undefined, borrowed)], stranger),
      'JoiningClosed',
    );
    // A bystander with no proof at all.
    expectProgramError(await h.send([await joinCohortIx(stranger.address, w.mint, w.league, 1, 1)], stranger), 'JoiningClosed');
    // The membership without its cohort is no proof either.
    const proof = borrowed;
    const half = await joinCohortIx(member.address, w.mint, w.league, 1, 1, w.cohorts[0], undefined, {
      membership: proof.membership,
      cohort: PROGRAM_ID,
    });
    expect((await h.send([half], member)).ok).toBe(false);

    // The round is the first cohort to start after the miss; cohort 2, once its joining has closed, is not.
    h.setTime(startOf(2) - JOIN_LOCK);
    expectProgramError(await h.send([await lateJoin(w, member, 1, 2)], member), 'JoiningClosed');
    // …though it is open to the member like anyone else until then.
    h.setTime(startOf(2) - JOIN_LOCK - 1);
    const open = await h.send([await joinCohortIx(member.address, w.mint, w.league, 2, 1, w.cohorts[0])], member);
    expect(open.ok, open.logs.join('\n')).toBe(true);
    expect((await h.readCohort(w.league, 1)).memberCount).toBe(0);
  });

  it('is no way back for a member who finished their cohort: a membership still standing is no proof', async () => {
    const w = await lastDayWorld();
    const { h } = w;
    // A second wallet that sat all seven days of cohort 0.
    const finisher = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(finisher.address, w.mint), w.mint, finisher.address, 10n * OLD_STAKE);
    h.setTime(startOf(0) - 20);
    await h.sendOrThrow([await joinCohortIx(finisher.address, w.mint, w.league, 0, 1)], finisher);
    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(startOf(0) + day * DAY_LENGTH);
      await h.sendOrThrow([await submitDayIx(finisher.address, w.league, 0, day, new Uint8Array(32))], finisher);
    }
    h.setTime(endOf(0));
    expectProgramError(await h.send([await lateJoin(w, finisher, 1)], finisher), 'JoiningClosed');
  });

  it('is made good just as well by finishing the round after it — the other of the two', async () => {
    const w = await lastDayWorld();
    const { h, member } = w;

    // Not joining cohort 1 at all: record the miss (this frees the slot), and take cohort 2 while it is open.
    await w.act(
      [await recordForfeitsIx(member.address, w.fees, w.league, 0, await forfeitPairs(w.cohorts[0]!, [member.address]))],
      member,
    );
    const newTier = 2; // 50 SKR
    await w.act([await joinCohortIx(member.address, w.mint, w.league, 2, newTier)], member);

    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 0, await forfeitPairs(w.cohorts[0]!, [member.address]))],
      w.crank,
    );
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 0)], w.crank);
    h.setTime(endOf(1));
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 1)], w.crank);
    expect(
      (await h.readProfile(member.address)).leagues[2]!,
      'still open: cohort 2 is the second of its two rounds',
    ).toMatchObject({ comebackStake: OLD_STAKE, comebackRound: 1, comebackLastRound: 2 });

    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(startOf(2) + day * DAY_LENGTH);
      await w.act([await submitDayIx(member.address, w.league, 2, day, new Uint8Array(32).fill(day))], member);
    }
    h.setTime(endOf(2));
    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 2, await forfeitPairs(w.cohorts[2]!, [member.address]), w.cohorts[1])],
      w.crank,
    );
    expect((await h.readMembership(w.cohorts[2]!, member.address)).comebackEntitled).toBe(10n * SKR);
    expect((await h.readProfile(member.address)).leagues[2]!.comebackStake, 'used up').toBe(0n);
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 2)], w.crank);

    const before = h.tokenBalance(await associatedTokenPda(member.address, w.mint));
    await w.act([await claimPayoutIx(member.address, w.mint, w.league, 2)], member);
    expect(h.tokenBalance(await associatedTokenPda(member.address, w.mint)) - before).toBe(DEFAULT_CHIPS[newTier]! + 10n * SKR);
  });
});

// =========================== release_comeback never takes a pledge a count would make owed

describe('release_comeback waits for every round the league has opened (FR-037e)', () => {
  const release = async (w: { h: Harness; league: number }, caller: KeyPairSigner, wallet: Address, round?: number) =>
    w.h.send([await releaseComebackIx(caller.address, w.league, wallet, round)], caller);

  it('refuses while the member\'s finish of the first round waits to be counted, though the last round never opened', async () => {
    // A last-day miss: rounds 1 and 2. The league has opened cohorts 0 and 1 only.
    const w = await lastDayWorld(2);
    const { h, member } = w;
    await w.act([await lateJoin(w, member, 1)], member);
    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 0, await forfeitPairs(w.cohorts[0]!, [member.address]))],
      w.crank,
    );
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 0)], w.crank);
    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(startOf(1) + day * DAY_LENGTH);
      await w.act([await submitDayIx(member.address, w.league, 1, day, new Uint8Array(32).fill(day + 1))], member);
    }
    // Cohort 1 is over and uncounted; cohort 2 never opened, and its first day has passed.
    h.setTime(deadlineOf(2, 0) + 1);
    const stranger = await h.newWallet();
    const pledged = await h.readLeague(w.league);
    expect([pledged.boostReserve, pledged.comebackReserve]).toEqual([70n * SKR, 10n * SKR]);

    // The round the member finished is open until it is settled, however the time reads.
    expectProgramError(await release(w, stranger, member.address, 1), 'ComebackStillOpen');
    // The wrong cohort — the unopened last round — or none at all.
    expectProgramError(await release(w, stranger, member.address, 2), 'AccountMismatch');
    expectProgramError(await release(w, stranger, member.address), 'AccountMismatch');
    expectProgramError(await release(w, stranger, member.address, 0), 'AccountMismatch');
    const after = await h.readLeague(w.league);
    expect([after.boostReserve, after.comebackReserve], 'the refusals moved nothing').toEqual([70n * SKR, 10n * SKR]);

    // The count makes the refund owed, and the comeback is closed: nothing left to release.
    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 1, await forfeitPairs(w.cohorts[1]!, [member.address]), w.cohorts[0])],
      w.crank,
    );
    expect((await h.readMembership(w.cohorts[1]!, member.address)).comebackEntitled).toBe(10n * SKR);
    expectProgramError(await release(w, stranger, member.address, 1), 'NoComebackToRelease');
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 1)], w.crank);
    expectProgramError(await release(w, stranger, member.address, 1), 'NoComebackToRelease');
    expect((await h.readCohort(w.league, 1)).comebackFunded).toBe(10n * SKR);

    const before = h.tokenBalance(await associatedTokenPda(member.address, w.mint));
    await w.act([await claimPayoutIx(member.address, w.mint, w.league, 1)], member);
    expect(h.tokenBalance(await associatedTokenPda(member.address, w.mint)) - before).toBe(DEFAULT_CHIPS[1]! + 10n * SKR);
  });

  it('does the same in a 21-day league, where the first round is still running when the last one\'s first day passes', async () => {
    const LONG = 21;
    const cadence = 7 * DAY_LENGTH;
    const start = (i: number) => START0 + i * cadence;
    const end = (i: number) => start(i) + LONG * DAY_LENGTH;
    const h = await createHarness({ startTime: START0 - DAY_LENGTH });
    const mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 1_000_000_000n * SKR);
    const fees = await h.injectFeeDestinations(mint);
    await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });
    const league = h.freshTestLeague().leagueId;
    // Cohort 0 ends the second cohort 3 starts. Cohorts 0–3 are open; 4 never is.
    await h.injectLeague(league, {
      durationDays: LONG,
      dayLength: BigInt(DAY_LENGTH),
      firstStart: BigInt(START0),
      cadence: BigInt(cadence),
      joinLock: BigInt(JOIN_LOCK),
      nextIndex: 4,
    });
    const cohorts: Address[] = [];
    // Cohort 4's account exists (as an injected stand-in) so passing it is refused by the program, not the runtime.
    for (const i of [0, 1, 2, 3, 4]) {
      cohorts.push(
        await h.injectCohort(league, i, {
          start: BigInt(start(i)),
          joinCutoff: BigInt(start(i) - JOIN_LOCK),
          durationDays: LONG,
          dayLength: BigInt(DAY_LENGTH),
        }),
      );
    }
    const member = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(member.address, mint), mint, member.address, 10n * OLD_STAKE);
    const crank = await h.newWallet();
    let previous: Snapshot | undefined;
    const act = async (instructions: readonly Instruction[], signer: KeyPairSigner) => {
      previous ??= await h.snapshot();
      const result = await h.sendOrThrow(instructions, signer);
      const current = await h.snapshot();
      assertInvariants(current, previous);
      previous = current;
      return result;
    };

    await act([await joinCohortIx(member.address, mint, league, 0, OLD_TIER)], member);
    for (let day = 0; day < LONG - 1; day += 1) {
      h.setTime(start(0) + day * DAY_LENGTH);
      await act([await submitDayIx(member.address, league, 0, day, new Uint8Array(32).fill(day))], member);
    }
    // The last day is missed: the deadline is the second before cohort 3 starts.
    h.setTime(end(0));
    expect(end(0)).toBe(start(3));
    const proof = await comebackAccounts(member.address, league, 0);
    await act([await joinCohortIx(member.address, mint, league, 3, 1, cohorts[0], undefined, proof)], member);
    await act([await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohorts[0]!, [member.address]))], crank);
    await act([await settleFinalizeIx(crank.address, fees, league, 0)], crank);
    expect((await h.readProfile(member.address)).leagues[2]!).toMatchObject({ comebackRound: 3, comebackLastRound: 4 });

    // Cohorts 1 and 2 had nobody. The member sits cohort 3 through its 21 days; on the way,
    // cohort 4's first day passes (it never opens) while cohort 3 is still running.
    const stranger = await h.newWallet();
    const attempt = async (round?: number) =>
      h.send([await releaseComebackIx(stranger.address, league, member.address, round)], stranger);
    let tried = false;
    for (let day = 0; day < LONG; day += 1) {
      h.setTime(start(3) + day * DAY_LENGTH);
      if (!tried && h.now() > deadlineOf4(start(4))) {
        tried = true;
        expectProgramError(await attempt(3), 'ComebackStillOpen');
        expectProgramError(await attempt(4), 'AccountMismatch');
        expectProgramError(await attempt(), 'AccountMismatch');
        const reserves = await h.readLeague(league);
        expect([reserves.boostReserve, reserves.comebackReserve]).toEqual([70n * SKR, 10n * SKR]);
      }
      if (h.now() >= end(1) && (await h.readCohort(league, 1)).settled === false) {
        await act([await settleFinalizeIx(crank.address, fees, league, 1)], crank);
      }
      if (h.now() >= end(2) && (await h.readCohort(league, 2)).settled === false) {
        await act([await settleFinalizeIx(crank.address, fees, league, 2)], crank);
      }
      await act([await submitDayIx(member.address, league, 3, day, new Uint8Array(32).fill(day))], member);
    }
    expect(tried).toBe(true);

    h.setTime(end(3));
    await act(
      [await settleCountIx(crank.address, fees, league, 3, await forfeitPairs(cohorts[3]!, [member.address]), cohorts[2])],
      crank,
    );
    expect((await h.readMembership(cohorts[3]!, member.address)).comebackEntitled).toBe(10n * SKR);
    expectProgramError(await attempt(3), 'NoComebackToRelease');
  });
});

/** The last second of the first day of a cohort starting at `start`. */
const deadlineOf4 = (start: number) => start + DAY_LENGTH - 1;
