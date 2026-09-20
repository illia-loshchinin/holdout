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
  noFinisherFee: 0n,
  carryOverSent: 0n,
  carryOverTarget: null,
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
  pendingEnd: 0,
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

  it('catches the stale submission a step advanced its clock into', () => {
    // The shape a scenario produces when it snapshots, advances the clock past
    // day 0's deadline, and the program then accepts day 0 anyway. Read at the
    // previous instant this looks lawful, and INV-3's ceiling has risen with
    // the clock, so nothing else is watching.
    const c = cohort();
    const prev = snapshot({ now: c.start, memberships: [membership({ daysCompleted: 0 })] });
    const next = snapshot({
      now: c.start + DAY + 10,
      memberships: [membership({ daysCompleted: 1 })],
    });
    expectViolation('INV-8', () => assertInvariants(next, prev));
  });
});

describe('INV-6 · a settled cohort only moves through claims', () => {
  /** A settled cohort: one finisher of tier 3, stake 100, owed a share of 80. */
  const settled = (over: Partial<CohortSnapshot> = {}): CohortSnapshot =>
    cohort({
      settled: true,
      counted: 1,
      finisherCount: 1,
      finishersPerTier: [0, 0, 0, 1],
      totalFinisherStake: 100n,
      sharePerTier: [0n, 0n, 0n, 80n],
      heldStake: 100n,
      pool: 80n,
      vaultAmount: 180n,
      ...over,
    });

  const at = FIRST_START + 8 * DAY;
  const finished = membership({ state: 'Finished', daysCompleted: 7, counted: true });
  /** What INV-9 derives from that one counted finish — the streak is not the point here. */
  const cleared = [profile({ leagues: { 0: progress({ cleared: 1, currentStreak: 1, longestStreak: 1 }) } })];

  it('accepts a claim that takes exactly the stake and the share', () => {
    const prev = snapshot({ now: at, cohorts: [settled()], memberships: [finished], profiles: cleared });
    const next = snapshot({
      now: at,
      cohorts: [settled({ heldStake: 0n, pool: 0n, vaultAmount: 0n })],
      memberships: [{ ...finished, state: 'Claimed' }],
      profiles: cleared,
    });
    expect(() => assertInvariants(next, prev)).not.toThrow();
  });

  it('catches balances walked down with the vault and no claim to show for it', () => {
    // The books balance — INV-1 holds at both ends — and every membership is
    // still `Finished`. Only the missing claim gives it away.
    const prev = snapshot({ now: at, cohorts: [settled()], memberships: [finished], profiles: cleared });
    const next = snapshot({
      now: at,
      cohorts: [settled({ pool: 30n, vaultAmount: 130n })],
      memberships: [finished],
      profiles: cleared,
    });
    expectViolation('INV-6', () => assertInvariants(next, prev));
  });

  it('catches a held stake that fell further than the claim returns', () => {
    const prev = snapshot({ now: at, cohorts: [settled()], memberships: [finished], profiles: cleared });
    const next = snapshot({
      now: at,
      cohorts: [settled({ heldStake: 0n, pool: 0n, vaultAmount: 0n, memberCount: 1 })],
      memberships: [{ ...finished, stake: 40n, state: 'Claimed' }],
      profiles: cleared,
    });
    expectViolation('INV-6', () => assertInvariants(next, prev));
  });
});

describe('INV-2 · finalization lands on an exact pool', () => {
  const counted = (over: Partial<CohortSnapshot> = {}): CohortSnapshot =>
    cohort({
      memberCount: 1,
      counted: 1,
      finisherCount: 1,
      finishersPerTier: [0, 0, 0, 1],
      totalFinisherStake: 100n,
      heldStake: 100n,
      pool: 81n,
      vaultAmount: 181n,
      ...over,
    });

  const at = FIRST_START + 8 * DAY;
  const finisher = membership({ state: 'Finished', daysCompleted: 7, counted: true });
  const cleared = [profile({ leagues: { 0: progress({ cleared: 1, currentStreak: 1, longestStreak: 1 }) } })];

  it('accepts the shares it owes plus the remainder it gave up', () => {
    const prev = snapshot({ now: at, cohorts: [counted()], memberships: [finisher], profiles: cleared });
    const next = snapshot({
      now: at,
      cohorts: [
        counted({ settled: true, sharePerTier: [0n, 0n, 0n, 80n], roundingRemainder: 1n, pool: 80n, vaultAmount: 180n }),
      ],
      memberships: [finisher],
      profiles: cleared,
    });
    expect(() => assertInvariants(next, prev)).not.toThrow();
  });

  it('catches a finalize that drained the claimable shares', () => {
    // The pool falls, the vault falls with it, and the shares the cohort wrote
    // still promise 80 per finisher. Only the first claim would find out.
    const prev = snapshot({ now: at, cohorts: [counted()], memberships: [finisher], profiles: cleared });
    const next = snapshot({
      now: at,
      cohorts: [
        counted({ settled: true, sharePerTier: [0n, 0n, 0n, 80n], roundingRemainder: 1n, pool: 0n, vaultAmount: 100n }),
      ],
      memberships: [finisher],
      profiles: cleared,
    });
    expectViolation('INV-2', () => assertInvariants(next, prev));
  });

  it('requires a no-finisher cohort to empty its pool into the fee and the carry', () => {
    const lobby = cohort({
      address: 'cohort-B',
      index: 1,
      start: FIRST_START + 7 * DAY,
      joinCutoff: FIRST_START + 7 * DAY - 3_600,
      memberCount: 0,
      heldStake: 0n,
      vaultAmount: 0n,
    });
    const empty = counted({
      finisherCount: 0,
      finishersPerTier: [0, 0, 0, 0],
      totalFinisherStake: 0n,
      heldStake: 0n,
      pool: 81n,
      vaultAmount: 81n,
    });
    const prev = snapshot({ now: at, cohorts: [empty, lobby], memberships: [] });
    const settledEmpty = {
      ...empty,
      settled: true,
      pool: 0n,
      vaultAmount: 0n,
      noFinisherFee: 40n,
      carryOverSent: 41n,
      carryOverTarget: 'cohort-B',
    };
    const fed = { ...lobby, pool: 41n, carryOverReceived: 41n, vaultAmount: 41n };
    expect(() =>
      assertInvariants(snapshot({ now: at, cohorts: [settledEmpty, fed], memberships: [] }), prev),
    ).not.toThrow();

    // The same split, one unit short of the pool it started with.
    expectViolation('INV-2', () =>
      assertInvariants(
        snapshot({
          now: at,
          cohorts: [{ ...settledEmpty, noFinisherFee: 39n }, fed],
          memberships: [],
        }),
        prev,
      ),
    );
  });
});

describe('INV-2 · a carry-over has to have a sender', () => {
  const at = FIRST_START + 8 * DAY;

  const lobby = cohort({
    address: 'cohort-B',
    index: 1,
    start: FIRST_START + 7 * DAY,
    joinCutoff: FIRST_START + 7 * DAY - 3_600,
    memberCount: 0,
    heldStake: 0n,
    vaultAmount: 0n,
  });

  /** A cohort of *another* league, nobody finished, ready to finalize. */
  const stranger = cohort({
    address: 'cohort-C',
    leagueId: 1,
    index: 4,
    memberCount: 0,
    heldStake: 0n,
    pool: 81n,
    vaultAmount: 81n,
  });

  it('catches a pool fed by a counter nobody sent anything into', () => {
    const prev = snapshot({ now: at, cohorts: [lobby], memberships: [] });
    expectViolation('INV-2', () =>
      assertInvariants(
        snapshot({
          now: at,
          cohorts: [{ ...lobby, pool: 41n, carryOverReceived: 41n, vaultAmount: 41n }],
          memberships: [],
        }),
        prev,
      ),
    );
  });

  it('catches a carry-over that crossed from another league', () => {
    // Every figure on both sides adds up on its own: the source splits its pool
    // into a fee and a carry and empties it, the target's `carry_over_received`
    // covers its new pool, and INV-1 holds at both ends. Only putting the two
    // sides next to each other shows the carry left league 1 and landed in
    // league 0, which FR-037a does not allow.
    const prev = snapshot({ now: at, cohorts: [lobby, stranger], memberships: [] });
    expectViolation('INV-2', () =>
      assertInvariants(
        snapshot({
          now: at,
          cohorts: [
            { ...lobby, pool: 41n, carryOverReceived: 41n, vaultAmount: 41n },
            {
              ...stranger,
              settled: true,
              pool: 0n,
              vaultAmount: 0n,
              noFinisherFee: 40n,
              carryOverSent: 41n,
              carryOverTarget: 'cohort-B',
            },
          ],
          memberships: [],
        }),
        prev,
      ),
    );
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
