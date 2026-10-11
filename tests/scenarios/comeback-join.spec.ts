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
  forfeitPairs,
  joinCohortIx,
  recordForfeitsIx,
  settleCountIx,
  settleFinalizeIx,
  submitDayIx,
  type FeeDestinations,
} from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda } from '../helpers/pdas.js';
import { assertInvariants, forfeitSplit, type Snapshot } from '../invariants/checker.js';

const NO_KEY = '11111111111111111111111111111111' as Address;
const DAY_LENGTH = 60;
const DURATION = 7;
const START0 = FIRST_START;
const END0 = START0 + DURATION * DAY_LENGTH;
/** The next cohort starts while cohort 0 is still running, inside the 14-day window. */
const START1 = START0 + 3 * DAY_LENGTH;
const END1 = START1 + DURATION * DAY_LENGTH;
const JOIN_LOCK = 10;
const MISSED = START0 + DAY_LENGTH - 1; // the deadline of day 0 of cohort 0
const WINDOW = 14 * DAY_LENGTH;
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
    expect(cohort0.boostDue).toBe(split.boost);
    expect(cohort0.comebackDue).toBe(split.comeback);
    expect(h.tokenBalance(w.fees.developer) - developerBefore).toBe(split.developer);
    expect(h.tokenBalance(w.fees.operations) - operationsBefore).toBe(split.operations);
    expect([split.pool, split.boost, split.comeback, split.developer, split.operations]).toEqual([
      70n * SKR,
      5n * SKR,
      5n * SKR,
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

  it('then entitles the member to min(20 % of the lost stake, the new stake) on finishing inside the window', async () => {
    const w = await world();
    const { h, member } = w;
    const newTier = 0; // 10 SKR: smaller than 20 % of 100, so the cap on the new stake binds
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

    // The forfeit is counted and its window recorded on the profile, exactly as R22 says.
    const progress = (await h.readProfile(member.address)).leagues[2]!;
    expect(progress.comebackStake).toBe(OLD_STAKE);
    expect(progress.comebackFrom).toBe(BigInt(MISSED));
    expect(progress.comebackUntil).toBe(BigInt(MISSED + WINDOW));
    expect(START1).toBeGreaterThan(MISSED);
    expect(START1).toBeLessThanOrEqual(MISSED + WINDOW);

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
    expect(membership.comebackEntitled).toBe(10n * SKR); // min(20 % of 100 = 20, 10)
    expect((await h.readProfile(member.address)).leagues[2]!.comebackStake, 'used up').toBe(0n);
    expect((await h.readCohort(w.league, 1)).comebackOwed).toBe(10n * SKR);
  });
});
