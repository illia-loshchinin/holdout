/**
 * The checker's own tests. Part of T017: a checker that silently passes is worse
 * than none, so every invariant is shown failing on the state that breaks it and
 * passing on the state that does not.
 *
 * These run without the program: the snapshots are built by hand, which is also
 * how the interleaving INV-9 exists for can be written before `settle_count`
 * exists to produce it (T077a covers the same case against the real program).
 */

import { describe, expect, it } from 'vitest';

import {
  assertInvariants,
  assertSameProgress,
  assertUnchanged,
  deriveStreak,
  InvariantError,
  maxDaysCompleted,
  type CohortSnapshot,
  type LeagueProgressSnapshot,
  type MembershipSnapshot,
  type MembershipState,
  type ProfileSnapshot,
  type Snapshot,
} from './checker.js';

/** Monday 28 September 2026, 05:00:00 UTC. */
const FIRST_START = 1_790_571_600;
const DAY = 86_400;

const cohort = (over: Partial<CohortSnapshot> = {}): CohortSnapshot => ({
  address: 'cohort-A',
  leagueId: 0,
  index: 0,
  start: FIRST_START,
  joinCutoff: FIRST_START - 3_600,
  durationDays: 7,
  dayLength: DAY,
  chips: [10n, 25n, 50n, 100n],
  memberCount: 1,
  heldStake: 100n,
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
  carryOverHeld: 0n,
  vaultAmount: 100n,
  ...over,
});

const membership = (over: Partial<MembershipSnapshot> = {}): MembershipSnapshot => ({
  address: 'membership-1',
  cohort: 'cohort-A',
  wallet: 'wallet-1',
  tier: 3,
  stake: 100n,
  daysCompleted: 0,
  state: 'Joined',
  counted: false,
  ...over,
});

const progress = (over: Partial<LeagueProgressSnapshot> = {}): LeagueProgressSnapshot => ({
  cleared: 0,
  forfeited: 0,
  currentStreak: 0,
  longestStreak: 0,
  lastChanged: 0,
  ...over,
});

const profile = (over: Partial<ProfileSnapshot> = {}): ProfileSnapshot => ({
  wallet: 'wallet-1',
  qualified: false,
  leagues: { 0: progress() },
  runningCohort: null,
  runningEnd: 0,
  pendingCohort: null,
  pendingStart: 0,
  ...over,
});

const snapshot = (over: Partial<Snapshot> = {}): Snapshot => ({
  now: FIRST_START,
  cohorts: [cohort()],
  memberships: [membership()],
  profiles: [profile()],
  ...over,
});

const expectViolation = (invariant: string, fn: () => void): void => {
  try {
    fn();
  } catch (e) {
    expect(e).toBeInstanceOf(InvariantError);
    expect((e as InvariantError).invariant).toBe(invariant);
    return;
  }
  throw new Error(`expected ${invariant} to fail`);
};

describe('INV-1 · custody', () => {
  it('passes when the vault holds the stakes, the pool and any held carry-over', () => {
    expect(() =>
      assertInvariants(snapshot({ cohorts: [cohort({ heldStake: 400n, pool: 680n, vaultAmount: 1_080n })] })),
    ).not.toThrow();
  });

  it('fails when a single unit is missing', () => {
    expectViolation('INV-1', () =>
      assertInvariants(snapshot({ cohorts: [cohort({ heldStake: 400n, pool: 680n, vaultAmount: 1_079n })] })),
    );
  });
});

describe('INV-3 · completed days', () => {
  it('lets the first day be completed on the first day', () => {
    const c = cohort();
    expect(maxDaysCompleted(c, c.start)).toBe(1);
    expect(() =>
      assertInvariants(snapshot({ now: c.start, memberships: [membership({ daysCompleted: 1 })] })),
    ).not.toThrow();
  });

  it('refuses two completed days on the first day', () => {
    expectViolation('INV-3', () =>
      assertInvariants(snapshot({ now: FIRST_START, memberships: [membership({ daysCompleted: 2 })] })),
    );
  });

  it('never allows more than the duration', () => {
    expectViolation('INV-3', () =>
      assertInvariants(
        snapshot({ now: FIRST_START + 99 * DAY, memberships: [membership({ daysCompleted: 8 })] }),
      ),
    );
  });
});

describe('INV-4 · one obligation at a time', () => {
  it('fails on two running memberships for one wallet', () => {
    const a = cohort({ address: 'cohort-A' });
    const b = cohort({ address: 'cohort-B', index: 1 });
    expectViolation('INV-4', () =>
      assertInvariants(
        snapshot({
          cohorts: [a, b],
          memberships: [
            membership({ address: 'm-1', cohort: 'cohort-A' }),
            membership({ address: 'm-2', cohort: 'cohort-B' }),
          ],
        }),
      ),
    );
  });

  it('allows a lobby place in a cohort that starts as the running one ends', () => {
    const running = cohort({ address: 'cohort-A' });
    const next = cohort({
      address: 'cohort-B',
      index: 1,
      start: FIRST_START + 7 * DAY,
      joinCutoff: FIRST_START + 7 * DAY - 3_600,
    });
    expect(() =>
      assertInvariants(
        snapshot({
          now: FIRST_START + DAY,
          cohorts: [running, next],
          memberships: [
            membership({ address: 'm-1', cohort: 'cohort-A' }),
            membership({ address: 'm-2', cohort: 'cohort-B' }),
          ],
        }),
      ),
    ).not.toThrow();
  });
});

describe('INV-8 · breach blocks submission', () => {
  it('catches a day completed after the deadline passed', () => {
    const c = cohort();
    const prev = snapshot({ now: c.start + DAY, memberships: [membership({ daysCompleted: 0 })] });
    const next = snapshot({ now: c.start + DAY + 10, memberships: [membership({ daysCompleted: 1 })] });
    expectViolation('INV-8', () => assertInvariants(next, prev));
  });

  it('allows the same day completed before its deadline', () => {
    const c = cohort();
    const prev = snapshot({ now: c.start, memberships: [membership({ daysCompleted: 0 })] });
    const next = snapshot({ now: c.start + 60, memberships: [membership({ daysCompleted: 1 })] });
    expect(() => assertInvariants(next, prev)).not.toThrow();
  });
});

describe('INV-9 · streaks follow cohort order, not crank order', () => {
  /** A cleared cohort A, then a forfeited cohort B one week later. */
  const twoCohorts = (bState: MembershipState, bCounted: boolean) => {
    const a = cohort({
      address: 'cohort-A',
      index: 0,
      settled: true,
      heldStake: 0n,
      vaultAmount: 0n,
      memberCount: 1,
      counted: 1,
    });
    const b = cohort({
      address: 'cohort-B',
      index: 1,
      start: FIRST_START + 7 * DAY,
      joinCutoff: FIRST_START + 7 * DAY - 3_600,
      heldStake: 0n,
      pool: 80n,
      vaultAmount: 80n,
      memberCount: 1,
    });
    const memberships = [
      membership({ address: 'm-A', cohort: 'cohort-A', state: 'Claimed', daysCompleted: 7, counted: true }),
      membership({ address: 'm-B', cohort: 'cohort-B', state: bState, daysCompleted: 0, counted: bCounted }),
    ];
    return { a, b, memberships };
  };

  it('derives a broken streak once both cohorts are counted, whatever the order was', () => {
    const { a, b, memberships } = twoCohorts('Forfeited', true);
    const cohorts = new Map([a, b].map((c) => [c.address, c]));
    expect(deriveStreak(memberships, cohorts, 'wallet-1', 0)).toEqual({
      cleared: 1,
      currentStreak: 0,
      longestStreak: 1,
    });

    // Reversing the arrays is exactly what a different page order produces.
    expect(deriveStreak([...memberships].reverse(), cohorts, 'wallet-1', 0)).toEqual({
      cleared: 1,
      currentStreak: 0,
      longestStreak: 1,
    });
  });

  it('catches the profile that recorded the late clear over the earlier forfeit', () => {
    const { a, b, memberships } = twoCohorts('Forfeited', true);
    // What the old design produced: B's forfeit recorded first, then A's clear
    // counted, leaving a streak of 1 where chronology says 0.
    expectViolation('INV-9', () =>
      assertInvariants(
        snapshot({
          now: FIRST_START + 8 * DAY,
          cohorts: [a, b],
          memberships,
          profiles: [
            profile({
              leagues: { 0: progress({ cleared: 1, forfeited: 1, currentStreak: 1, longestStreak: 1 }) },
            }),
          ],
        }),
      ),
    );
  });

  it('accepts the figures the ordered rule writes', () => {
    const { a, b, memberships } = twoCohorts('Forfeited', true);
    expect(() =>
      assertInvariants(
        snapshot({
          now: FIRST_START + 8 * DAY,
          cohorts: [a, b],
          memberships,
          profiles: [
            profile({
              leagues: { 0: progress({ cleared: 1, forfeited: 1, currentStreak: 0, longestStreak: 1 }) },
            }),
          ],
        }),
      ),
    ).not.toThrow();
  });

  it('counts a forfeit the moment it is recorded but the streak only when counted', () => {
    // Mid-cohort: B's forfeit is recorded, nothing is counted yet. The forfeited
    // counter has moved; the streak has not (FR-061a).
    const { a, b, memberships } = twoCohorts('Forfeited', false);
    expect(() =>
      assertInvariants(
        snapshot({
          now: FIRST_START + 8 * DAY,
          cohorts: [a, b],
          memberships,
          profiles: [
            profile({
              leagues: { 0: progress({ cleared: 1, forfeited: 1, currentStreak: 1, longestStreak: 1 }) },
            }),
          ],
        }),
      ),
    ).not.toThrow();
  });
});

describe('INV-7 · repeating a step changes nothing', () => {
  it('passes on identical snapshots and fails when anything moved', () => {
    const before = snapshot();
    expect(() => assertUnchanged(before, snapshot())).not.toThrow();
    expectViolation('INV-7', () =>
      assertUnchanged(before, snapshot({ cohorts: [cohort({ forfeitsRecorded: 1 })] })),
    );
  });
});

describe('two processing orders', () => {
  it('must leave identical progress', () => {
    const a = snapshot();
    expect(() => assertSameProgress(a, snapshot())).not.toThrow();
    expectViolation('INV-9', () =>
      assertSameProgress(
        a,
        snapshot({ profiles: [profile({ leagues: { 0: progress({ currentStreak: 1 }) } })] }),
      ),
    );
  });
});
