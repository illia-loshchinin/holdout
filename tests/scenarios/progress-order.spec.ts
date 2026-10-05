/**
 * The interleaving that made streaks order-dependent. Task T077a.
 *
 * The bug this file exists to prevent: a member clears cohort A, takes their
 * place in cohort B, misses B's first day — and B's forfeit is **recorded
 * before A is counted**. If a forfeit wrote the streak, their history would
 * read "forfeit, then clear" and the streak would come out at 1; counted the
 * other way round it would come out at 0. The same member, the same cohorts,
 * two answers, decided by the crank's schedule.
 *
 * So `record_forfeits` writes `forfeited` — an order-independent counter — and
 * leaves the streak alone, and `settle_count` writes the streak in **cohort
 * order**, refusing cohort *i* until the league's cohort *i − 1* is settled
 * (FR-061a, INV-9). This file runs the interleaving both ways and asserts the
 * two profiles are identical.
 *
 * Then the leg FR-002 promises: a forfeit ends a *cohort*, never the member's
 * place in the league. They join again, clear cohort C, and the streak reads 1.
 */

import { generateKeyPairSigner, type Address, type KeyPairSigner } from '@solana/kit';
import { describe, expect, it } from 'vitest';

import {
  DEFAULT_CHIPS,
  FIRST_START,
  SKR,
  createHarness,
  expectProgramError,
  type Harness,
} from '../helpers/harness.js';
import {
  forfeitPairs,
  joinCohortIx,
  recordForfeitsIx,
  settleCountIx,
  settleFinalizeIx,
  submitDayIx,
} from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda, vaultPda } from '../helpers/pdas.js';

/** The real 7-day league: 24-hour days, weekly cadence, an hour's join lock. */
const LEAGUE = 0;
const OTHER_LEAGUE = 1;
const DAY = 86_400;
const HOUR = 3_600;
const WEEK = 7 * DAY;
const DURATION = 7;
/** Cohort `i` of the league. B starts exactly as A ends, so places never overlap. */
const startOf = (index: number) => FIRST_START + index * WEEK;
const endOf = (index: number) => startOf(index) + DURATION * DAY;

/** Everything one run of the interleaving needs, built fresh. */
interface World {
  h: Harness;
  mint: Address;
  feeDestination: Address;
  crank: KeyPairSigner;
  member: KeyPairSigner;
}

const world = async (): Promise<World> => {
  const h = await createHarness({ startTime: FIRST_START - DAY });
  const mint = (await generateKeyPairSigner()).address;
  h.injectMint(mint, null, 10_000_000n * SKR);

  const treasury = (await generateKeyPairSigner()).address;
  const feeDestination = await associatedTokenPda(treasury, mint);
  h.injectTokenAccount(feeDestination, mint, treasury, 0n);
  await h.injectConfig({ skrMint: mint, feeDestination });

  const league = { durationDays: DURATION, dayLength: BigInt(DAY), cadence: BigInt(WEEK), joinLock: BigInt(HOUR) };
  await h.injectLeague(LEAGUE, { ...league, firstStart: BigInt(FIRST_START), nextIndex: 3 });
  await h.injectLeague(OTHER_LEAGUE, { ...league, firstStart: BigInt(FIRST_START), nextIndex: 1 });
  for (const index of [0, 1, 2]) {
    await h.injectCohort(LEAGUE, index, {
      start: BigInt(startOf(index)),
      joinCutoff: BigInt(startOf(index) - HOUR),
      durationDays: DURATION,
      dayLength: BigInt(DAY),
    });
  }

  const member = await h.newWallet();
  h.injectTokenAccount(await associatedTokenPda(member.address, mint), mint, member.address, 10n * DEFAULT_CHIPS[3]);
  return { h, mint, feeDestination, crank: await h.newWallet(), member };
};

const join = async (w: World, index: number, tier = 1, runningCohort?: Address) =>
  w.h.sendOrThrow(
    [await joinCohortIx(w.member.address, w.mint, LEAGUE, index, tier, runningCohort)],
    w.member,
  );

const clearEveryDay = async (w: World, index: number, who?: KeyPairSigner) => {
  const signer = who ?? w.member;
  for (let day = 0; day < DURATION; day += 1) {
    w.h.setTime(startOf(index) + day * DAY);
    await w.h.sendOrThrow(
      [await submitDayIx(signer.address, LEAGUE, index, day, new Uint8Array(32).fill(day))],
      signer,
    );
  }
};

/** Another wallet, funded and joined into cohort `index`. */
const coMember = async (w: World, index: number, tier = 0): Promise<KeyPairSigner> => {
  const who = await w.h.newWallet();
  w.h.injectTokenAccount(
    await associatedTokenPda(who.address, w.mint),
    w.mint,
    who.address,
    10n * DEFAULT_CHIPS[3]!,
  );
  await w.h.sendOrThrow([await joinCohortIx(who.address, w.mint, LEAGUE, index, tier)], who);
  return who;
};

const settle = async (w: World, index: number, alsoCount: readonly Address[] = []) => {
  const cohort = await cohortPda(LEAGUE, index);
  const previous = index > 0 ? await cohortPda(LEAGUE, index - 1) : undefined;
  await w.h.sendOrThrow(
    [
      await settleCountIx(
        w.crank.address,
        w.feeDestination,
        LEAGUE,
        index,
        await forfeitPairs(cohort, [w.member.address, ...alsoCount]),
        previous,
      ),
    ],
    w.crank,
  );
  await w.h.sendOrThrow(
    [await settleFinalizeIx(w.crank.address, w.feeDestination, LEAGUE, index)],
    w.crank,
  );
};

const recordForfeit = async (w: World, index: number) => {
  const cohort = await cohortPda(LEAGUE, index);
  await w.h.sendOrThrow(
    [
      await recordForfeitsIx(
        w.crank.address,
        w.feeDestination,
        LEAGUE,
        index,
        await forfeitPairs(cohort, [w.member.address]),
      ),
    ],
    w.crank,
  );
};

/** The member's progress in the 7-day league. */
const progressOf = async (w: World) => (await w.h.readProfile(w.member.address)).leagues[LEAGUE]!;

describe('a streak follows the member\'s cohorts, not the crank\'s order (T077a)', () => {
  /**
   * Cohort A cleared, cohort B forfeited. `forfeitFirst` decides whether B's
   * forfeit is *recorded* before A is counted — the interleaving that used to
   * decide the answer.
   */
  const runInterleaving = async (forfeitFirst: boolean) => {
    const w = await world();

    // A: joined in the lobby and cleared outright.
    await join(w, 0);
    await clearEveryDay(w, 0);

    // B: its lobby opens while A is still running, and B starts exactly as A
    // ends, so the place is allowed (FR-006b). Someone else joins and clears B,
    // so B finalises on a finisher rather than needing a carry-over target.
    w.h.setTime(startOf(1) - 2 * HOUR);
    await join(w, 1, 2, await cohortPda(LEAGUE, 0));
    const other = await coMember(w, 1);

    // B's first day is missed by our member; the other one keeps going.
    await clearEveryDay(w, 1, other);

    if (forfeitFirst) {
      // The crank notices B's breach before it has settled A at all.
      w.h.setTime(startOf(1) + DAY + 1);
      await recordForfeit(w, 1);
    }

    // The crank is late and finds both cohorts settling. Counting B is refused
    // until A is settled, whatever order it would have liked to work in.
    w.h.setTime(endOf(1));
    expectProgramError(
      await w.h.send(
        [
          await settleCountIx(
            w.crank.address,
            w.feeDestination,
            LEAGUE,
            1,
            await forfeitPairs(await cohortPda(LEAGUE, 1), [w.member.address]),
            await cohortPda(LEAGUE, 0),
          ),
        ],
        w.crank,
      ),
      'PreviousCohortUnsettled',
    );

    // A settles.
    await settle(w, 0);
    expect((await progressOf(w)).cleared).toBe(1);
    expect((await progressOf(w)).currentStreak).toBe(1);

    if (!forfeitFirst) {
      await recordForfeit(w, 1);
    }

    // A cohort of the *other* league settles in between, and changes nothing.
    const streakBefore = (await progressOf(w)).currentStreak;
    await w.h.injectCohort(OTHER_LEAGUE, 0, {
      start: BigInt(startOf(0)),
      durationDays: DURATION,
      dayLength: BigInt(DAY),
      settled: true,
    });
    expect((await progressOf(w)).currentStreak).toBe(streakBefore);

    // B settles.
    await settle(w, 1, [other.address]);
    return w;
  };

  it('ends the streak at zero whichever order the two cohorts are processed in (INV-9)', async () => {
    const forfeitFirst = await progressOf(await runInterleaving(true));
    const countFirst = await progressOf(await runInterleaving(false));

    expect(forfeitFirst.currentStreak).toBe(0);
    expect(forfeitFirst.longestStreak).toBe(1);
    expect(forfeitFirst.cleared).toBe(1);
    expect(forfeitFirst.forfeited).toBe(1);

    // The whole point: identical, not merely close.
    expect(countFirst).toEqual(forfeitFirst);
  });

  it('lets the member join again after forfeiting without ever sitting a day (FR-002)', async () => {
    const w = await world();

    // A cleared.
    await join(w, 0);
    await clearEveryDay(w, 0);

    // B joined and then **never sat a single day** — the case that strands a
    // lobby place, because nothing ever normalises the member's slots and
    // `release_running` cannot reach a place that is still `pending`.
    w.h.setTime(startOf(1) - 2 * HOUR);
    await join(w, 1, 2, await cohortPda(LEAGUE, 0));
    const other = await coMember(w, 1);

    w.h.setTime(endOf(0));
    await settle(w, 0);

    w.h.setTime(startOf(1) + DAY + 1);
    await recordForfeit(w, 1);

    // The place must be free again, wherever it was sitting.
    const afterForfeit = await w.h.readProfile(w.member.address);
    expect(afterForfeit.pending.cohort).toBe('11111111111111111111111111111111');
    expect(afterForfeit.running.cohort).toBe('11111111111111111111111111111111');

    // So C's lobby takes them.
    await clearEveryDay(w, 1, other);
    w.h.setTime(startOf(2) - 2 * HOUR);
    await join(w, 2, 0);
    await clearEveryDay(w, 2);

    w.h.setTime(endOf(2));
    await settle(w, 1, [other.address]);
    await settle(w, 2);

    const progress = await progressOf(w);
    expect(progress.currentStreak, 'one clear since the forfeit').toBe(1);
    expect(progress.longestStreak).toBe(1);
    expect(progress.cleared).toBe(2);
    expect(progress.forfeited).toBe(1);

    // And the payout is there to claim, which is what makes coming back worth it.
    expect((await w.h.readMembership(await cohortPda(LEAGUE, 2), w.member.address)).state).toBe('Finished');
    expect(w.h.tokenBalance(await vaultPda(await cohortPda(LEAGUE, 2)))).toBeGreaterThan(0n);
  });

  it('records the clear\'s instant as the cohort\'s end, identical for everyone who cleared it (FR-031)', async () => {
    const w = await world();
    await join(w, 0);
    await clearEveryDay(w, 0);

    // The crank settles five days late; the figure must not move with it.
    w.h.setTime(endOf(0) + 5 * DAY);
    await settle(w, 0);

    const progress = await progressOf(w);
    expect(progress.lastChanged).toBe(BigInt(endOf(0)));
    expect(progress.lastChanged).not.toBe(BigInt(w.h.now()));
  });
});
