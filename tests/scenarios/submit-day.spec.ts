/**
 * A day's reps inside a Holdout. Task T063.
 *
 * One completed day per member per day, and the day a submission may claim is
 * decided by chain time alone: it must be the member's next day *and* today,
 * and today's deadline must not have passed (FR-013, FR-017, FR-025, INV-8).
 * `days_completed` therefore never exceeds `max_days_completed(now)` — which is
 * **one** in the cohort's first second, because a day-0 submission there is
 * valid (INV-3, data-model §1.3).
 *
 * The proof is emitted and not stored (research R16), so the assertions read it
 * out of the `DayCompleted` event rather than an account.
 *
 * A compressed test league runs a whole cycle in minutes, so these scenarios
 * travel days rather than waiting for them (scope doc D-A).
 */

import { generateKeyPairSigner, getAddressDecoder, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  DEFAULT_CHIPS,
  FIRST_START,
  SKR,
  createHarness,
  expectProgramError,
  oneEvent,
  type Harness,
} from '../helpers/harness.js';
import { joinCohortIx, submitDayIx } from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda } from '../helpers/pdas.js';

/** A test league whose day is a minute, so seven of them pass in seven minutes. */
const DAY_LENGTH = 60;
const DURATION = 7;
const START = FIRST_START;
/** The last second of day `k`. */
const deadlineOf = (k: number) => START + (k + 1) * DAY_LENGTH - 1;
const END = START + DURATION * DAY_LENGTH;

const proof = (fill: number): Uint8Array => new Uint8Array(32).fill(fill);

/** `DayCompleted { cohort, wallet, day_index, proof_hash }`. */
const decodeDayCompleted = (bytes: Uint8Array) => ({
  cohort: getAddressDecoder().decode(bytes.slice(0, 32)),
  wallet: getAddressDecoder().decode(bytes.slice(32, 64)),
  dayIndex: bytes[64],
  proofHash: bytes.slice(65, 97),
});

describe('submit_day (contracts §3)', () => {
  let h: Harness;
  let mint: Address;
  let member: KeyPairSigner;
  let cohort: Address;
  const league = 100;

  beforeEach(async () => {
    // Joined during the lobby, so the membership is the program's own work.
    h = await createHarness({ startTime: START - DAY_LENGTH });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 1_000_000n * SKR);
    await h.injectConfig({ skrMint: mint });
    await h.injectLeague(league, { durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), nextIndex: 1 });
    cohort = await h.injectCohort(league, 0, {
      start: BigInt(START),
      joinCutoff: BigInt(START - 10),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
    });

    member = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(member.address, mint), mint, member.address, DEFAULT_CHIPS[3]);
    await h.sendOrThrow([await joinCohortIx(member.address, mint, league, 0, 1)], member);
  });

  const submit = async (dayIndex: number, fill = 7, who: KeyPairSigner = member) =>
    h.send([await submitDayIx(who.address, league, 0, dayIndex, proof(fill))], who);

  const daysCompleted = async () => (await h.readMembership(cohort, member.address)).daysCompleted;

  it('accepts day 0 in the cohort\'s very first second, leaving one day completed (INV-3)', async () => {
    h.setTime(START);
    const result = await submit(0);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect(await daysCompleted()).toBe(1);
  });

  it('emits the proof and stores it nowhere (research R16, FR-025)', async () => {
    h.setTime(START);
    const result = await h.sendOrThrow([await submitDayIx(member.address, league, 0, 0, proof(0xab))], member);

    const event = decodeDayCompleted(oneEvent(result, 'DayCompleted'));
    expect(event.cohort).toBe(cohort);
    expect(event.wallet).toBe(member.address);
    expect(event.dayIndex).toBe(0);
    expect([...event.proofHash]).toEqual([...proof(0xab)]);

    // The membership grew by a day and by nothing else: no 32 bytes per day.
    const membership = await h.readMembership(cohort, member.address);
    expect(membership.daysCompleted).toBe(1);
    expect(membership.state).toBe('Joined');
  });

  it('moves the lobby place into the running slot (data-model §1.5)', async () => {
    h.setTime(START);
    expect((await h.readProfile(member.address)).pending.cohort).toBe(cohort);

    await h.sendOrThrow([await submitDayIx(member.address, league, 0, 0, proof(1))], member);

    const profile = await h.readProfile(member.address);
    expect(profile.pending.cohort).toBe('11111111111111111111111111111111');
    expect(profile.running.cohort).toBe(cohort);
  });

  it('takes one day per day, all the way through the cohort', async () => {
    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(START + day * DAY_LENGTH);
      const result = await submit(day, day);
      expect(result.ok, `day ${day}: ${result.logs.join('\n')}`).toBe(true);
      expect(await daysCompleted()).toBe(day + 1);
    }
    expect(await daysCompleted()).toBe(DURATION);
  });

  it('refuses a second submission for the same day (FR-025)', async () => {
    h.setTime(START);
    await h.sendOrThrow([await submitDayIx(member.address, league, 0, 0, proof(1))], member);

    expectProgramError(await submit(0, 2), 'DayAlreadyCompleted');
    expect(await daysCompleted(), 'the day counts once').toBe(1);
  });

  it('refuses tomorrow\'s day today, and a day already behind', async () => {
    h.setTime(START);
    expectProgramError(await submit(1), 'WrongDay');

    h.setTime(START + DAY_LENGTH);
    // Day 0 was never completed, so the member is in breach and day 1 is not
    // theirs to claim: their next day is 0, which is no longer today.
    expectProgramError(await submit(1), 'WrongDay');
    expect(await daysCompleted()).toBe(0);
  });

  it('never lets days_completed pass max_days_completed(now) (INV-3)', async () => {
    // Every day of the cohort, one submission at a time, and the ceiling holds.
    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(START + day * DAY_LENGTH);
      await h.sendOrThrow([await submitDayIx(member.address, league, 0, day, proof(day))], member);

      const maxAllowed = Math.min(day + 1, DURATION);
      expect(await daysCompleted()).toBe(maxAllowed);
      // And a second one the same day cannot push past it.
      expect((await submit(day + 1, day)).ok).toBe(false);
      expect(await daysCompleted()).toBe(maxAllowed);
    }
  });

  it('refuses a submission once the member is in breach (INV-8)', async () => {
    // Day 0's deadline passes with nothing submitted.
    h.setTime(deadlineOf(0) + 1);
    expectProgramError(await submit(0), 'WrongDay');
    expect(await daysCompleted(), 'no automation had to run for that').toBe(0);

    // And the member cannot catch up later either.
    h.setTime(START + 3 * DAY_LENGTH);
    expectProgramError(await submit(0), 'WrongDay');
    expect(await daysCompleted()).toBe(0);
  });

  it('accepts a submission in the last second of its day, and refuses the next', async () => {
    h.setTime(deadlineOf(0));
    await h.sendOrThrow([await submitDayIx(member.address, league, 0, 0, proof(1))], member);
    expect(await daysCompleted()).toBe(1);

    // One second later it is day 1, and day 1 is submittable — but day 0 is gone.
    h.setTime(deadlineOf(0) + 1);
    expectProgramError(await submit(0, 2), 'DayAlreadyCompleted');
    expect((await submit(1, 3)).ok).toBe(true);
    expect(await daysCompleted()).toBe(2);
  });

  it('refuses everything before the cohort starts', async () => {
    h.setTime(START - 1);
    expectProgramError(await submit(0), 'CohortNotRunning');
    expect(await daysCompleted()).toBe(0);
  });

  it('refuses everything after the cohort\'s final deadline', async () => {
    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(START + day * DAY_LENGTH);
      await h.sendOrThrow([await submitDayIx(member.address, league, 0, day, proof(day))], member);
    }

    h.setTime(END);
    expectProgramError(await submit(DURATION), 'CohortNotRunning');
    h.setTime(END + 10 * DAY_LENGTH);
    expectProgramError(await submit(DURATION), 'CohortNotRunning');
    expect(await daysCompleted(), 'a full clear, and not one day more').toBe(DURATION);
  });

  it('is the member\'s own day: a stranger cannot complete it for them', async () => {
    h.setTime(START);
    const stranger = await h.newWallet();
    const result = await h.send([await submitDayIx(stranger.address, league, 0, 0, proof(1))], stranger);
    expect(result.ok).toBe(false);
    expect(await daysCompleted()).toBe(0);
  });

  it('touches no money at all', async () => {
    const before = await h.readCohort(league, 0);
    h.setTime(START);
    await h.sendOrThrow([await submitDayIx(member.address, league, 0, 0, proof(1))], member);

    const after = await h.readCohort(league, 0);
    expect(after.heldStake).toBe(before.heldStake);
    expect(after.pool).toBe(before.pool);
    expect(after.memberCount).toBe(before.memberCount);
    expect(cohort).toBe(await cohortPda(league, 0));
  });
});
