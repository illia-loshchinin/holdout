/**
 * The on-chain figures a leaderboard reads. Task T096 (amended 2026-10-06, H36).
 *
 * The ranking itself — survival rate, the minimum, the tie-breaks, "Updating"
 * while a league is mid-count — lives in Kotlin core (T112). This file checks
 * only what that ranking is fed: each member's `Profile.leagues[slot]` record
 * (AC-14, FR-060a, FR-060b, FR-061, FR-061a).
 *
 *  - **`cleared` and `forfeited` are counters.** A clear lands when the cohort
 *    is counted; a forfeit lands the moment it is recorded — by
 *    `record_forfeits` while the cohort runs, or by `settle_count` for a breach
 *    nobody recorded.
 *  - **The streaks are written only by `settle_count`, in cohort order.** Up by
 *    one for a finisher with `longest = max(longest, current)`, back to zero for
 *    a counted forfeit with `longest` kept. Counting cohort *i* is refused until
 *    cohort *i − 1* of the league has settled (`PreviousCohortUnsettled`).
 *  - **`last_changed` is the outcome's own instant** — the cohort's `end` for a
 *    clear, the missed `deadline_of(days_completed)` for a forfeit — taken as a
 *    `max`, and never the time the transaction landed. Every case here settles
 *    late on purpose so a landing-time write would show.
 *  - **Pages are order-free.** Any order, overlap or repetition gives the same
 *    figures to the unit.
 *  - **Mid-count is visible on the cohort.** While some memberships are counted
 *    and others not, the counted members' figures have moved and the rest have
 *    not; `counted > 0 && !settled` is what the app keys off to hold the board.
 *  - **Every test league writes the one shared slot** (`Profile::slot_of`):
 *    slot 2 for any `league_id >= 100`, leaving the judged slots 0 and 1 alone.
 *
 * Test leagues are injected rather than created, so the default build serves:
 * `test-leagues` gates only `create_league`'s timing check, not the slot.
 *
 * Every transaction that should land goes through `w.act`, which also runs the
 * invariant checker against the step before — INV-9's own derivation of the
 * streak and `last_changed` included.
 */

import { generateKeyPairSigner, type Address, type Instruction, type KeyPairSigner } from '@solana/kit';
import { describe, expect, it } from 'vitest';

import type { LeagueProgress } from '../helpers/accounts.js';
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
import { assertInvariants, assertSameProgress, assertUnchanged, type Snapshot } from '../invariants/checker.js';

const DAY_LENGTH = 60;
const DURATION = 7;
const START = FIRST_START;
const JOIN_LOCK = 10;
/** Cohort `i` starts `i × CADENCE` after the first: a gap as long as the cohort itself. */
const CADENCE = 2 * DURATION * DAY_LENGTH;
const startOf = (index: number) => START + index * CADENCE;
/** `start + duration_days × day_length` — the instant a clear records. */
const endOf = (index: number) => startOf(index) + DURATION * DAY_LENGTH;
/** `deadline_of(k)`: the last second of day `k` (data-model §1.3) — the instant a forfeit records. */
const deadlineOf = (index: number, k: number) => startOf(index) + (k + 1) * DAY_LENGTH - 1;
/** Far enough past any end that a landing-time write could not be mistaken for an outcome instant. */
const LATE = 10 * CADENCE;

/** `Profile::slot_of`: 0 and 1 are the judged leagues; every `league_id >= 100` shares 2. */
const SLOT_7D = 0;
const SLOT_21D = 1;
const TEST_SLOT = 2;

const ZERO: LeagueProgress = {
  cleared: 0,
  forfeited: 0,
  currentStreak: 0,
  longestStreak: 0,
  lastChanged: 0n,
  comebackStake: 0n,
  comebackFrom: 0n,
  comebackUntil: 0n,
};

/** The figures a leaderboard reads from one slot. */
const board = (p: LeagueProgress) => ({
  cleared: p.cleared,
  forfeited: p.forfeited,
  currentStreak: p.currentStreak,
  longestStreak: p.longestStreak,
  lastChanged: p.lastChanged,
});

// ---------------------------------------------------------------------- world

interface World {
  h: Harness;
  mint: Address;
  fees: FeeDestinations;
  crank: KeyPairSigner;
  league: number;
  /** Send, insist it lands, and check every invariant against the step before. */
  act(instructions: readonly Instruction[], signer: KeyPairSigner): Promise<SendResult>;
}

const leagueTiming = {
  durationDays: DURATION,
  dayLength: BigInt(DAY_LENGTH),
  firstStart: BigInt(START),
  cadence: BigInt(CADENCE),
  joinLock: BigInt(JOIN_LOCK),
  nextIndex: 4,
};

/** A fresh harness with one test league (id 100) on compressed timing and empty reserves. */
const world = async (): Promise<World> => {
  const h = await createHarness({ startTime: START - DAY_LENGTH });
  const mint = (await generateKeyPairSigner()).address;
  h.injectMint(mint, null, 1_000_000_000n * SKR);
  const fees = await h.injectFeeDestinations(mint);
  await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });

  const league = h.freshTestLeague().leagueId;
  await h.injectLeague(league, leagueTiming);

  let previous: Snapshot | undefined;
  return {
    h,
    mint,
    fees,
    crank: await h.newWallet(),
    league,
    act: async (instructions, signer) => {
      previous ??= await h.snapshot();
      const result = await h.sendOrThrow(instructions, signer);
      const current = await h.snapshot();
      assertInvariants(current, previous);
      previous = current;
      return result;
    },
  };
};

/** Cohort `index` of a league, in its lobby, starting at `start`. */
const cohortAt = async (w: World, index: number, leagueId = w.league, start = startOf(index)) =>
  w.h.injectCohort(leagueId, index, {
    start: BigInt(start),
    joinCutoff: BigInt(start - JOIN_LOCK),
    durationDays: DURATION,
    dayLength: BigInt(DAY_LENGTH),
  });

/**
 * A wallet holding SKR enough for several top-tier stakes. Pass `signer` to fund
 * the same key in another harness, so two runs can be compared wallet by wallet.
 */
const wallet = async (w: World, signer?: KeyPairSigner): Promise<KeyPairSigner> => {
  const who = signer ?? (await generateKeyPairSigner());
  await w.h.fund(who.address, 10n * 10n ** 9n);
  w.h.injectTokenAccount(await associatedTokenPda(who.address, w.mint), w.mint, who.address, 10n * DEFAULT_CHIPS[3]);
  return who;
};

const join = async (
  w: World,
  who: KeyPairSigner,
  index: number,
  tier = 1,
  leagueId = w.league,
  runningCohort?: Address,
) => w.act([await joinCohortIx(who.address, w.mint, leagueId, index, tier, runningCohort)], who);

/**
 * Sit cohort `index` day by day: each member completes their first `days[i]`
 * days and then stops. The clock is left at the start of the last day.
 */
const sit = async (
  w: World,
  index: number,
  plan: readonly (readonly [KeyPairSigner, number])[],
  leagueId = w.league,
  start = startOf(index),
) => {
  for (let day = 0; day < DURATION; day += 1) {
    w.h.setTime(start + day * DAY_LENGTH);
    for (const [who, days] of plan) {
      if (day < days) {
        await w.act([await submitDayIx(who.address, leagueId, index, day, new Uint8Array(32).fill(day))], who);
      }
    }
  }
};

const record = async (w: World, index: number, who: readonly KeyPairSigner[], leagueId = w.league) =>
  w.act(
    [
      await recordForfeitsIx(
        w.crank.address,
        w.fees,
        leagueId,
        index,
        await forfeitPairs(await cohortPda(leagueId, index), who.map((m) => m.address)),
      ),
    ],
    w.crank,
  );

const countIx = async (w: World, index: number, who: readonly KeyPairSigner[], leagueId = w.league) =>
  settleCountIx(
    w.crank.address,
    w.fees,
    leagueId,
    index,
    await forfeitPairs(await cohortPda(leagueId, index), who.map((m) => m.address)),
    index > 0 ? await cohortPda(leagueId, index - 1) : undefined,
  );

/** One `settle_count` page, which must land. */
const count = async (w: World, index: number, who: readonly KeyPairSigner[], leagueId = w.league) =>
  w.act([await countIx(w, index, who, leagueId)], w.crank);

const finalize = async (w: World, index: number, leagueId = w.league) =>
  w.act([await settleFinalizeIx(w.crank.address, w.fees, leagueId, index)], w.crank);

/** Count everyone in one page, then finalize. */
const settle = async (w: World, index: number, who: readonly KeyPairSigner[], leagueId = w.league) => {
  await count(w, index, who, leagueId);
  await finalize(w, index, leagueId);
};

const progressOf = async (w: World, who: KeyPairSigner, slot = TEST_SLOT) =>
  (await w.h.readProfile(who.address)).leagues[slot]!;

// ================================================================ a clear

describe('given a member who clears a cohort', () => {
  it('when it settles long after its end, then cleared, current and longest each rise by one and last_changed is the cohort end, never the landing time', async () => {
    const w = await world();
    await cohortAt(w, 0);
    const member = await wallet(w);
    await join(w, member, 0);
    await sit(w, 0, [[member, DURATION]]);
    expect(await progressOf(w, member), 'nothing lands before the count').toEqual(ZERO);

    w.h.setTime(endOf(0) + LATE);
    await settle(w, 0, [member]);

    const progress = await progressOf(w, member);
    expect(board(progress)).toEqual({
      cleared: 1,
      forfeited: 0,
      currentStreak: 1,
      longestStreak: 1,
      lastChanged: BigInt(endOf(0)),
    });
    expect(progress.lastChanged).toBe(BigInt(startOf(0) + DURATION * DAY_LENGTH));
    expect(progress.lastChanged, 'not the time the count landed').not.toBe(BigInt(w.h.now()));
  });

  it('when two finishers are counted in separate pages at different times, then both carry the same last_changed — the cohort end (FR-031, FR-060b)', async () => {
    const w = await world();
    await cohortAt(w, 0);
    const early = await wallet(w);
    const late = await wallet(w);
    await join(w, early, 0, 0);
    await join(w, late, 0, 3);
    await sit(w, 0, [[early, DURATION], [late, DURATION]]);

    w.h.setTime(endOf(0) + 1);
    await count(w, 0, [early]);
    w.h.setTime(endOf(0) + LATE);
    await count(w, 0, [late]);
    await finalize(w, 0);

    const a = await progressOf(w, early);
    const b = await progressOf(w, late);
    expect(a.lastChanged).toBe(BigInt(endOf(0)));
    expect(board(b)).toEqual(board(a));
  });

  it('when they clear the next cohort of the league too, then the streak grows to two and longest follows it, and last_changed moves to the later end', async () => {
    const w = await world();
    await cohortAt(w, 0);
    await cohortAt(w, 1);
    const member = await wallet(w);

    await join(w, member, 0);
    await sit(w, 0, [[member, DURATION]]);
    w.h.setTime(endOf(0));
    await settle(w, 0, [member]);

    await join(w, member, 1);
    await sit(w, 1, [[member, DURATION]]);
    w.h.setTime(endOf(1) + LATE);
    await settle(w, 1, [member]);

    expect(board(await progressOf(w, member))).toEqual({
      cleared: 2,
      forfeited: 0,
      currentStreak: 2,
      longestStreak: 2,
      lastChanged: BigInt(endOf(1)),
    });
  });

  it('when a forfeit has broken an earlier streak of two, then a new clear raises current to one and leaves longest at two (longest = max(longest, current))', async () => {
    const w = await world();
    for (const index of [0, 1, 2, 3]) await cohortAt(w, index);
    const member = await wallet(w);

    // Clear, clear.
    for (const index of [0, 1]) {
      await join(w, member, index);
      await sit(w, index, [[member, DURATION]]);
      w.h.setTime(endOf(index));
      await settle(w, index, [member]);
    }
    // Forfeit, after two days.
    await join(w, member, 2);
    await sit(w, 2, [[member, 2]]);
    w.h.setTime(endOf(2));
    await settle(w, 2, [member]);
    expect(board(await progressOf(w, member))).toEqual({
      cleared: 2,
      forfeited: 1,
      currentStreak: 0,
      longestStreak: 2,
      lastChanged: BigInt(deadlineOf(2, 2)),
    });

    // Clear again.
    await join(w, member, 3);
    await sit(w, 3, [[member, DURATION]]);
    w.h.setTime(endOf(3) + LATE);
    await settle(w, 3, [member]);
    expect(board(await progressOf(w, member))).toEqual({
      cleared: 3,
      forfeited: 1,
      currentStreak: 1,
      longestStreak: 2,
      lastChanged: BigInt(endOf(3)),
    });
  });
});

// ============================================================== a forfeit

describe('given a member on a streak of one who forfeits the next cohort', () => {
  /** Cohort 0 cleared and settled; cohort 1 joined by the member and by someone who will finish it. */
  const onAStreak = async () => {
    const w = await world();
    await cohortAt(w, 0);
    await cohortAt(w, 1);
    const member = await wallet(w);
    // Someone who finishes cohort 1, so it finalizes on a finisher.
    const finisher = await wallet(w);

    await join(w, member, 0);
    await sit(w, 0, [[member, DURATION]]);
    w.h.setTime(endOf(0));
    await settle(w, 0, [member]);

    await join(w, member, 1, 2);
    await join(w, finisher, 1, 0);
    expect(board(await progressOf(w, member))).toEqual({
      cleared: 1,
      forfeited: 0,
      currentStreak: 1,
      longestStreak: 1,
      lastChanged: BigInt(endOf(0)),
    });
    return { w, member, finisher };
  };

  it('when record_forfeits runs while the cohort is Running, then forfeited rises at once, the streak waits for the count, and last_changed is the missed deadline', async () => {
    const { w, member, finisher } = await onAStreak();
    const days = 2;

    // Day 2 is missed; the crank notices well after the deadline, still mid-cohort.
    for (let day = 0; day < days + 4; day += 1) {
      w.h.setTime(startOf(1) + day * DAY_LENGTH);
      if (day < days) await w.act([await submitDayIx(member.address, w.league, 1, day, new Uint8Array(32))], member);
      await w.act([await submitDayIx(finisher.address, w.league, 1, day, new Uint8Array(32))], finisher);
    }
    expect(w.h.now()).toBeLessThan(endOf(1));
    expect(w.h.now()).toBeGreaterThan(deadlineOf(1, days) + DAY_LENGTH);
    await record(w, 1, [member]);

    expect(board(await progressOf(w, member)), 'forfeited now; the streak not yet').toEqual({
      cleared: 1,
      forfeited: 1,
      currentStreak: 1,
      longestStreak: 1,
      lastChanged: BigInt(deadlineOf(1, days)),
    });
    expect((await progressOf(w, member)).lastChanged).not.toBe(BigInt(w.h.now()));

    // The finisher completes the rest; the cohort settles late.
    for (let day = days + 4; day < DURATION; day += 1) {
      w.h.setTime(startOf(1) + day * DAY_LENGTH);
      await w.act([await submitDayIx(finisher.address, w.league, 1, day, new Uint8Array(32))], finisher);
    }
    w.h.setTime(endOf(1) + LATE);
    await settle(w, 1, [member, finisher]);

    expect(board(await progressOf(w, member))).toEqual({
      cleared: 1,
      forfeited: 1,
      currentStreak: 0,
      longestStreak: 1,
      lastChanged: BigInt(deadlineOf(1, days)),
    });
    expect((await progressOf(w, member)).lastChanged).not.toBe(BigInt(w.h.now()));
  });

  it('when nobody records the breach and settle_count forfeits it, then forfeited rises once, current goes to zero, longest is kept, and last_changed is the missed deadline', async () => {
    const { w, member, finisher } = await onAStreak();
    const days = 4;
    await sit(w, 1, [[member, days], [finisher, DURATION]]);
    expect(board(await progressOf(w, member)), 'a breach nobody recorded writes nothing').toEqual({
      cleared: 1,
      forfeited: 0,
      currentStreak: 1,
      longestStreak: 1,
      lastChanged: BigInt(endOf(0)),
    });

    w.h.setTime(endOf(1) + LATE);
    await count(w, 1, [member]);
    expect((await w.h.readMembership(await cohortPda(w.league, 1), member.address)).state).toBe('Forfeited');
    // A repeated page forfeits nothing twice.
    await count(w, 1, [member, finisher]);
    await count(w, 1, [member, finisher]);
    await finalize(w, 1);

    const progress = await progressOf(w, member);
    expect(board(progress)).toEqual({
      cleared: 1,
      forfeited: 1,
      currentStreak: 0,
      longestStreak: 1,
      lastChanged: BigInt(deadlineOf(1, days)),
    });
    expect(progress.lastChanged).toBe(BigInt(startOf(1) + (days + 1) * DAY_LENGTH - 1));
    expect(progress.lastChanged).not.toBe(BigInt(w.h.now()));
  });
});

// ======================================================= page order freedom

describe('given a cohort of eight settled in pages (INV-7, INV-9)', () => {
  /** Index into the eight members: finishers 0–3, forfeiters 4–7. */
  const DAYS_SAT = [DURATION, DURATION, DURATION, DURATION, 3, 0, 5, 1] as const;
  const TIERS = [0, 1, 3, 2, 3, 0, 1, 2] as const;
  /** Recorded by `record_forfeits` while cohort 1 runs; the other two are left to the count. */
  const RECORDED = [4, 7];

  /**
   * Cohort 0: all eight clear it, so every streak starts at one. Cohort 1: four
   * finish, four forfeit. Cohort 1 is then counted in `pages`, finalized, and
   * the snapshot returned.
   */
  const run = async (signers: readonly KeyPairSigner[], pages: readonly (readonly number[])[]) => {
    const w = await world();
    await cohortAt(w, 0);
    await cohortAt(w, 1);
    const members = await Promise.all(signers.map((s) => wallet(w, s)));

    for (const who of members) await join(w, who, 0, 1);
    await sit(w, 0, members.map((who) => [who, DURATION] as const));
    w.h.setTime(endOf(0));
    await settle(w, 0, members);

    for (const [i, who] of members.entries()) await join(w, who, 1, TIERS[i]);
    await sit(w, 1, members.map((who, i) => [who, DAYS_SAT[i]!] as const));
    expect(w.h.now(), 'still Running when the forfeits are recorded').toBeLessThan(endOf(1));
    await record(w, 1, RECORDED.map((i) => members[i]!));

    w.h.setTime(endOf(1) + LATE);
    for (const page of pages) await count(w, 1, page.map((i) => members[i]!));
    await finalize(w, 1);

    const profiles = await Promise.all(members.map(async (who) => (await w.h.readProfile(who.address)).leagues));
    return { snapshot: await w.h.snapshot(), profiles };
  };

  it('when the pages run reversed, overlapping or repeated, then every member ends on the figures the natural order gives', async () => {
    const signers = await Promise.all(DAYS_SAT.map(() => generateKeyPairSigner()));

    const natural = await run(signers, [[0, 1, 2], [3, 4, 5], [6, 7]]);
    const reversed = await run(signers, [[7, 6, 5], [4, 3, 2], [1, 0]]);
    const shuffled = await run(signers, [
      [5, 2, 7],
      [5, 2, 7],
      [0, 6],
      [3, 1, 4, 0],
      [6, 7, 1, 2, 3, 4, 5, 0],
    ]);

    // What the natural order gives, member by member.
    for (const [i, days] of DAYS_SAT.entries()) {
      const slot = natural.profiles[i]![TEST_SLOT];
      if (days === DURATION) {
        expect(board(slot), `finisher ${i}`).toEqual({
          cleared: 2,
          forfeited: 0,
          currentStreak: 2,
          longestStreak: 2,
          lastChanged: BigInt(endOf(1)),
        });
      } else {
        expect(board(slot), `forfeiter ${i}`).toEqual({
          cleared: 1,
          forfeited: 1,
          currentStreak: 0,
          longestStreak: 1,
          lastChanged: BigInt(deadlineOf(1, days)),
        });
      }
    }

    // Identical, every slot of every member — not merely close.
    expect(reversed.profiles).toEqual(natural.profiles);
    expect(shuffled.profiles).toEqual(natural.profiles);
    assertSameProgress(natural.snapshot, reversed.snapshot);
    assertSameProgress(natural.snapshot, shuffled.snapshot);
  });
});

// ================================================== not yet over: no figures

describe('given a cohort that is Locked or Running', () => {
  it('when members keep up, finish every day, or miss one, then nobody\'s cleared or streak moves, and only a recorded forfeit raises forfeited', async () => {
    const w = await world();
    await cohortAt(w, 0);
    const keeper = await wallet(w);
    const quitter = await wallet(w);
    await join(w, keeper, 0);
    await join(w, quitter, 0);

    // Locked: joining has closed and nothing has started.
    w.h.setTime(startOf(0) - JOIN_LOCK / 2);
    for (const who of [keeper, quitter]) expect(await progressOf(w, who), 'Locked').toEqual(ZERO);

    // Running: the keeper completes day 0 and 1, the quitter only day 0.
    w.h.setTime(startOf(0));
    await w.act([await submitDayIx(keeper.address, w.league, 0, 0, new Uint8Array(32))], keeper);
    await w.act([await submitDayIx(quitter.address, w.league, 0, 0, new Uint8Array(32))], quitter);
    w.h.setTime(startOf(0) + DAY_LENGTH);
    await w.act([await submitDayIx(keeper.address, w.league, 0, 1, new Uint8Array(32))], keeper);

    // Day 1 has passed for the quitter. Out by derivation, but nothing written.
    w.h.setTime(deadlineOf(0, 1) + 1);
    for (const who of [keeper, quitter]) expect(await progressOf(w, who), 'breach not yet recorded').toEqual(ZERO);
    // A running cohort cannot be counted at all.
    expectProgramError(await w.h.send([await countIx(w, 0, [keeper, quitter])], w.crank), 'NotSettling');

    // The forfeit recorded mid-cohort: forfeited and its instant, nothing else.
    await record(w, 0, [keeper, quitter]);
    expect(await progressOf(w, quitter)).toEqual({ ...ZERO, forfeited: 1, lastChanged: BigInt(deadlineOf(0, 1)) });
    expect(await progressOf(w, keeper), 'the keeper is skipped').toEqual(ZERO);

    // The keeper finishes every day while the cohort is still Running.
    for (let day = 2; day < DURATION; day += 1) {
      w.h.setTime(startOf(0) + day * DAY_LENGTH);
      await w.act([await submitDayIx(keeper.address, w.league, 0, day, new Uint8Array(32))], keeper);
    }
    expect(w.h.now()).toBeLessThan(endOf(0));
    expect((await w.h.readMembership(await cohortPda(w.league, 0), keeper.address)).daysCompleted).toBe(DURATION);
    expect(await progressOf(w, keeper), 'all seven days done, but not counted').toEqual(ZERO);
    expect(await progressOf(w, quitter)).toEqual({ ...ZERO, forfeited: 1, lastChanged: BigInt(deadlineOf(0, 1)) });

    // No other slot saw any of it.
    for (const who of [keeper, quitter]) {
      const p = await w.h.readProfile(who.address);
      expect([p.leagues[SLOT_7D], p.leagues[SLOT_21D]]).toEqual([ZERO, ZERO]);
    }
  });
});

// ================================================================ mid-count

describe('given a cohort mid-count', () => {
  it('when some memberships are counted and others not, then the cohort reads counted > 0 and not settled, only the counted members\' figures have moved, and after the rest and settle_finalize everyone is consistent', async () => {
    const w = await world();
    await cohortAt(w, 0);
    await cohortAt(w, 1);
    const [a, b, c, d, e] = await Promise.all([0, 1, 2, 3, 4].map(() => wallet(w)));
    const members = [a!, b!, c!, d!, e!];

    // Cohort 0: all five clear it, so every streak is one.
    for (const who of members) await join(w, who, 0);
    await sit(w, 0, members.map((who) => [who, DURATION] as const));
    w.h.setTime(endOf(0));
    await settle(w, 0, members);

    // Cohort 1: a and b finish; c and e forfeit and are recorded mid-cohort; d's
    // breach is left for the count.
    for (const who of members) await join(w, who, 1);
    await sit(w, 1, [[a!, DURATION], [b!, DURATION], [c!, 1], [d!, 3], [e!, 2]]);
    await record(w, 1, [c!, e!]);

    w.h.setTime(endOf(1) + LATE);
    const before = new Map(await Promise.all(members.map(async (who) => [who.address, await progressOf(w, who)] as const)));

    // First page: a finisher and a recorded forfeiter.
    await count(w, 1, [a!, c!]);

    const cohort = await w.h.readCohort(w.league, 1);
    expect(cohort.counted).toBe(2);
    expect(cohort.memberCount).toBe(5);
    expect(cohort.settled).toBe(false);
    // The signal the app holds the board on: Settling, with some members counted.
    expect(w.h.now()).toBeGreaterThanOrEqual(endOf(1));
    expect(cohort.counted > 0 && !cohort.settled).toBe(true);

    // Counted: moved.
    expect(board(await progressOf(w, a!))).toEqual({
      cleared: 2,
      forfeited: 0,
      currentStreak: 2,
      longestStreak: 2,
      lastChanged: BigInt(endOf(1)),
    });
    expect(board(await progressOf(w, c!))).toEqual({
      cleared: 1,
      forfeited: 1,
      currentStreak: 0,
      longestStreak: 1,
      lastChanged: BigInt(deadlineOf(1, 1)),
    });
    // Not counted: exactly as before the page. `e`'s forfeit is already on its
    // record but its streak still reads one — why the board must wait.
    for (const who of [b!, d!, e!]) expect(await progressOf(w, who)).toEqual(before.get(who.address));
    expect(board(await progressOf(w, b!)).currentStreak).toBe(1);
    expect(board(await progressOf(w, d!)).forfeited).toBe(0);
    expect(board(await progressOf(w, e!))).toMatchObject({ forfeited: 1, currentStreak: 1 });

    // Cannot finalize mid-count.
    expectProgramError(
      await w.h.send([await settleFinalizeIx(w.crank.address, w.fees, w.league, 1)], w.crank),
      'CountIncomplete',
    );

    // The rest, then finalize.
    await count(w, 1, [b!, d!, e!]);
    await finalize(w, 1);
    const settled = await w.h.readCohort(w.league, 1);
    expect([settled.counted, settled.settled]).toEqual([5, true]);

    for (const who of [a!, b!]) {
      expect(board(await progressOf(w, who))).toEqual({
        cleared: 2,
        forfeited: 0,
        currentStreak: 2,
        longestStreak: 2,
        lastChanged: BigInt(endOf(1)),
      });
    }
    for (const [who, days] of [[c!, 1], [d!, 3], [e!, 2]] as const) {
      expect(board(await progressOf(w, who))).toEqual({
        cleared: 1,
        forfeited: 1,
        currentStreak: 0,
        longestStreak: 1,
        lastChanged: BigInt(deadlineOf(1, days)),
      });
    }
  });
});

// ======================================================== cohorts in order

describe('given two cohorts of one league in sequence (FR-061a)', () => {
  /**
   * Cohort 0 cleared, cohort 1 forfeited on day 0 — joined while cohort 0 was
   * still running. `recordEarly`: the crank records cohort 1's forfeit before
   * cohort 0 is counted, then tries to count cohort 1 first. Otherwise it counts
   * cohort 0 but has not finalized it when it tries cohort 1, and the breach is
   * left for the count.
   */
  const run = async (recordEarly: boolean) => {
    const w = await world();
    const zero = await cohortAt(w, 0);
    await cohortAt(w, 1);
    const member = await wallet(w);

    await join(w, member, 0);
    await sit(w, 0, [[member, DURATION]]);
    // Into cohort 1's lobby while cohort 0 still runs: cohort 0 is passed as the running one.
    await join(w, member, 1, 2, w.league, zero);

    if (recordEarly) {
      w.h.setTime(deadlineOf(1, 0) + 1);
      // Recording normalises the member's slots — cohort 1's lobby place
      // replaces cohort 0 in `running` — then releases cohort 1, so the profile
      // names neither slot while cohort 0's membership is still `Joined`
      // awaiting its count. INV-4 allows that: cohort 0 has ended, so nothing
      // can overlap it.
      await w.act(
        [
          await recordForfeitsIx(
            w.crank.address,
            w.fees,
            w.league,
            1,
            await forfeitPairs(await cohortPda(w.league, 1), [member.address]),
          ),
        ],
        w.crank,
      );
      expect(board(await progressOf(w, member)), 'recorded before cohort 0 is counted').toEqual({
        ...board(ZERO),
        forfeited: 1,
        lastChanged: BigInt(deadlineOf(1, 0)),
      });
    }

    w.h.setTime(endOf(1) + LATE);
    if (!recordEarly) await count(w, 0, [member]);

    // Cohort 1 first is refused, and writes nothing.
    const before = await w.h.snapshot();
    expectProgramError(await w.h.send([await countIx(w, 1, [member])], w.crank), 'PreviousCohortUnsettled');
    assertUnchanged(before, await w.h.snapshot());

    if (recordEarly) await count(w, 0, [member]);
    await finalize(w, 0);
    await settle(w, 1, [member]);
    return w.h.readProfile(member.address);
  };

  it('when the first is cleared and the second forfeited, then current_streak is 0 and longest 1 whichever order the crank tries', async () => {
    const recordedEarly = await run(true);
    const countedFirst = await run(false);

    expect(board(recordedEarly.leagues[TEST_SLOT]!)).toEqual({
      cleared: 1,
      forfeited: 1,
      currentStreak: 0,
      longestStreak: 1,
      lastChanged: BigInt(deadlineOf(1, 0)),
    });
    expect(countedFirst.leagues).toEqual(recordedEarly.leagues);
  });
});

// ============================================================= test leagues

describe('given test leagues (league_id >= 100)', () => {
  it('when a member clears a cohort of league 100 and one of league 101, then slot 2 reads cleared 2 and the judged slots 0 and 1 are untouched', async () => {
    const w = await world();
    expect(w.league).toBe(100);
    const other = w.h.freshTestLeague().leagueId;
    expect(other).toBe(101);
    await w.h.injectLeague(other, leagueTiming);

    // League 100's cohort 0 starts first; league 101's cohort 0 one cadence later.
    await cohortAt(w, 0, w.league, startOf(0));
    await cohortAt(w, 0, other, startOf(1));
    const member = await wallet(w);

    await join(w, member, 0, 1, w.league);
    await sit(w, 0, [[member, DURATION]], w.league, startOf(0));
    w.h.setTime(endOf(0));
    await settle(w, 0, [member], w.league);
    const afterFirst = await w.h.readProfile(member.address);
    expect(board(afterFirst.leagues[TEST_SLOT]!)).toMatchObject({ cleared: 1, lastChanged: BigInt(endOf(0)) });

    await join(w, member, 0, 1, other);
    await sit(w, 0, [[member, DURATION]], other, startOf(1));
    w.h.setTime(endOf(1) + LATE);
    await settle(w, 0, [member], other);

    const profile = await w.h.readProfile(member.address);
    // Both clears in the one shared slot; it is one record, so its streak runs across both.
    expect(board(profile.leagues[TEST_SLOT]!)).toEqual({
      cleared: 2,
      forfeited: 0,
      currentStreak: 2,
      longestStreak: 2,
      lastChanged: BigInt(endOf(1)),
    });
    expect(profile.leagues[SLOT_7D]).toEqual(ZERO);
    expect(profile.leagues[SLOT_21D]).toEqual(ZERO);
    expect(profile.qualified, 'only a 7-day clear unlocks the 21-day league').toBe(false);
  });

  it('when the member also has 7-day league progress, then test-league outcomes leave that slot exactly as it was', async () => {
    const w = await world();
    // The real 7-day league id, on compressed timing.
    await w.h.injectLeague(0, leagueTiming);
    await cohortAt(w, 0, 0, startOf(0));
    await cohortAt(w, 0, w.league, startOf(1));
    const member = await wallet(w);

    await join(w, member, 0, 1, 0);
    await sit(w, 0, [[member, DURATION]], 0, startOf(0));
    w.h.setTime(endOf(0));
    await settle(w, 0, [member], 0);
    const judged = await w.h.readProfile(member.address);
    expect(board(judged.leagues[SLOT_7D]!)).toEqual({
      cleared: 1,
      forfeited: 0,
      currentStreak: 1,
      longestStreak: 1,
      lastChanged: BigInt(endOf(0)),
    });
    expect(judged.leagues[TEST_SLOT]).toEqual(ZERO);

    // A test-league forfeit after one day.
    await join(w, member, 0, 3, w.league);
    await sit(w, 0, [[member, 1]], w.league, startOf(1));
    w.h.setTime(endOf(1) + LATE);
    await settle(w, 0, [member], w.league);

    const profile = await w.h.readProfile(member.address);
    expect(profile.leagues[SLOT_7D]).toEqual(judged.leagues[SLOT_7D]);
    expect(profile.leagues[SLOT_21D]).toEqual(ZERO);
    expect(board(profile.leagues[TEST_SLOT]!)).toEqual({
      cleared: 0,
      forfeited: 1,
      currentStreak: 0,
      longestStreak: 0,
      lastChanged: BigInt(deadlineOf(1, 1)),
    });
  });
});
