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
  lastFaucetDay: -1,
  ...over,
});

/**
 * The instants an outcome records on a profile: a cohort's end for a clear, the
 * deadline missed for a forfeit — never the time a transaction landed (FR-060b),
 * which is exactly what lets INV-9 derive the figure instead of trusting it.
 */
const CLEARED_A = FIRST_START + 7 * DAY;
const FORFEITED_B = FIRST_START + 8 * DAY - 1;

/** The slot a live place in cohort A puts on its member's profile. */
const inCohortA = { runningCohort: 'cohort-A', runningEnd: FIRST_START + 7 * DAY } as const;

const snapshot = (over: Partial<Snapshot> = {}): Snapshot => ({
  now: FIRST_START,
  cohorts: [cohort()],
  memberships: [membership()],
  // INV-4 reads the slots against the memberships in both directions, so the
  // default profile holds the place the default membership took. A test about
  // the slots passes its own.
  profiles: [profile(inCohortA)],
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
          profiles: [
            profile({
              ...inCohortA,
              pendingCohort: 'cohort-B',
              pendingStart: next.start,
              pendingEnd: next.start + 7 * DAY,
            }),
          ],
        }),
      ),
    ).not.toThrow();
  });

  it('catches a join that took a place without filling the slot', () => {
    // What a broken `join_cohort` leaves behind: the membership is live, the
    // profile still reads as free, and the very next join takes a second
    // obligation. Nothing else in the snapshot is wrong.
    expectViolation('INV-4', () =>
      assertInvariants(snapshot({ profiles: [profile()] })),
    );
  });

  it('catches a slot left naming a cohort the member is no longer in', () => {
    // The mirror image: the place is gone but the slot outlived it, so the
    // member is refused their next join for an obligation that does not exist.
    expectViolation('INV-4', () =>
      assertInvariants(
        snapshot({
          memberships: [membership({ state: 'Forfeited' })],
          profiles: [profile({ ...inCohortA, leagues: { 0: progress({ forfeited: 1 }) } })],
        }),
      ),
    );
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
  const cleared = [
    profile({
      leagues: {
        0: progress({ cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: CLEARED_A }),
      },
    }),
  ];

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

  it('catches a held stake that moved by something other than the claim', () => {
    // The pool falls by exactly the share, so half the claim is beyond reproach;
    // the held stake falls by 40 where the member's 100 was due back, and the
    // vault is lowered to agree with the cohort, so INV-1 holds at both ends.
    const prev = snapshot({ now: at, cohorts: [settled()], memberships: [finished], profiles: cleared });
    const next = snapshot({
      now: at,
      cohorts: [settled({ heldStake: 60n, pool: 0n, vaultAmount: 60n })],
      memberships: [{ ...finished, state: 'Claimed' }],
      profiles: cleared,
    });
    expectViolation('INV-6', () => assertInvariants(next, prev));
  });

});

describe('INV-2 · the terms of a membership are written once', () => {
  it('catches a stake rewritten under a live membership', () => {
    // The stake is what custody holds, what a forfeit hands the pool and what a
    // claim returns, so every figure the other invariants balance is derived
    // from it. Lowered here with the held stake and the vault lowered to agree,
    // the snapshot is internally consistent at both ends and the member is 60
    // out of pocket. Once the cohort settles the same rewrite also shows up in
    // INV-5's tally; while it is still running, this is the only rule watching.
    const live = cohort({ memberCount: 1, heldStake: 100n, vaultAmount: 100n });
    const prev = snapshot({ cohorts: [live], memberships: [membership()] });
    const next = snapshot({
      cohorts: [cohort({ memberCount: 1, heldStake: 40n, vaultAmount: 40n })],
      memberships: [membership({ stake: 40n })],
    });
    expectViolation('INV-2', () => assertInvariants(next, prev));
  });

  it('catches a membership moved to another wallet', () => {
    const prev = snapshot();
    expectViolation('INV-2', () =>
      assertInvariants(
        snapshot({
          memberships: [membership({ wallet: 'wallet-2' })],
          profiles: [profile({ wallet: 'wallet-2', ...inCohortA })],
        }),
        prev,
      ),
    );
  });
});

describe('INV-5 · the tally is derived, not taken on trust', () => {
  const at = FIRST_START + 8 * DAY;
  const finisher = membership({ state: 'Finished', daysCompleted: 7, counted: true });
  const cleared = [
    profile({
      leagues: {
        0: progress({ cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: CLEARED_A }),
      },
    }),
  ];

  /** One counted finisher: tier 3, stake 100. */
  const tallied = (over: Partial<CohortSnapshot> = {}): CohortSnapshot =>
    cohort({
      memberCount: 1,
      counted: 1,
      finisherCount: 1,
      finishersPerTier: [0, 0, 0, 1],
      totalFinisherStake: 100n,
      ...over,
    });

  it('accepts a tally that matches the counted memberships', () => {
    expect(() =>
      assertInvariants(
        snapshot({ now: at, cohorts: [tallied()], memberships: [finisher], profiles: cleared }),
      ),
    ).not.toThrow();
  });

  it('catches an overstated denominator', () => {
    // The shares it divides out are smaller and the remainder larger to match,
    // so finalization stays internally consistent and the difference goes to the
    // fee destination. The member who claims at their real tier finds it short.
    expectViolation('INV-5', () =>
      assertInvariants(
        snapshot({
          now: at,
          cohorts: [tallied({ totalFinisherStake: 200n })],
          memberships: [finisher],
          profiles: cleared,
        }),
      ),
    );
  });

  it('catches a finisher counted into the wrong tier', () => {
    expectViolation('INV-5', () =>
      assertInvariants(
        snapshot({
          now: at,
          cohorts: [tallied({ finishersPerTier: [1, 0, 0, 0] })],
          memberships: [finisher],
          profiles: cleared,
        }),
      ),
    );
  });

  it('catches a finisher count that no membership backs', () => {
    expectViolation('INV-5', () =>
      assertInvariants(
        snapshot({
          now: at,
          cohorts: [tallied({ finisherCount: 2 })],
          memberships: [finisher],
          profiles: cleared,
        }),
      ),
    );
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
  const cleared = [
    profile({
      leagues: {
        0: progress({ cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: CLEARED_A }),
      },
    }),
  ];

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
      // Open at `at` — a carry-over may only enter a cohort members can join.
      start: FIRST_START + 14 * DAY,
      joinCutoff: FIRST_START + 14 * DAY - 3_600,
      memberCount: 0,
      heldStake: 0n,
      vaultAmount: 0n,
    });
    const empty = counted({
      memberCount: 0,
      counted: 0,
      finisherCount: 0,
      finishersPerTier: [0, 0, 0, 0],
      totalFinisherStake: 0n,
      heldStake: 0n,
      pool: 81n,
      vaultAmount: 81n,
    });
    const prev = snapshot({ now: at, cohorts: [empty, lobby], memberships: [], profiles: [] });
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
      assertInvariants(
        snapshot({ now: at, cohorts: [settledEmpty, fed], memberships: [], profiles: [] }),
        prev,
      ),
    ).not.toThrow();

    // The same split, one unit short of the pool it started with.
    expectViolation('INV-2', () =>
      assertInvariants(
        snapshot({
          now: at,
          cohorts: [{ ...settledEmpty, noFinisherFee: 39n }, fed],
          memberships: [],
          profiles: [],
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
    // Still Open at `at`: FR-037a puts the carry where members can join it, so
    // a lobby one cadence further out is the one a settlement may feed.
    start: FIRST_START + 14 * DAY,
    joinCutoff: FIRST_START + 14 * DAY - 3_600,
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
    const prev = snapshot({ now: at, cohorts: [lobby], memberships: [], profiles: [] });
    expectViolation('INV-2', () =>
      assertInvariants(
        snapshot({
          now: at,
          cohorts: [{ ...lobby, pool: 41n, carryOverReceived: 41n, vaultAmount: 41n }],
          memberships: [],
          profiles: [],
        }),
        prev,
      ),
    );
  });

  it('catches a carry-over sent to a cohort nobody can join any more', () => {
    // Same league, no finishers at the source, the amounts on both sides agree
    // — and the money lands in a cohort that has already started, where no
    // member can ever stake against it (FR-037a).
    const started = { ...lobby, start: FIRST_START + 7 * DAY, joinCutoff: FIRST_START + 7 * DAY - 3_600 };
    const source = { ...stranger, leagueId: 0 };
    const prev = snapshot({ now: at, cohorts: [started, source], memberships: [], profiles: [] });
    expectViolation('INV-2', () =>
      assertInvariants(
        snapshot({
          now: at,
          cohorts: [
            { ...started, pool: 41n, carryOverReceived: 41n, vaultAmount: 41n },
            {
              ...source,
              settled: true,
              pool: 0n,
              vaultAmount: 0n,
              noFinisherFee: 40n,
              carryOverSent: 41n,
              carryOverTarget: 'cohort-B',
            },
          ],
          memberships: [],
          profiles: [],
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
    const prev = snapshot({ now: at, cohorts: [lobby, stranger], memberships: [], profiles: [] });
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
          profiles: [],
        }),
        prev,
      ),
    );
  });
});

describe('INV-5 · the denominator is written once, and not before its time', () => {
  const at = FIRST_START + 8 * DAY;

  it('catches a finalize that ran while members were still uncounted', () => {
    // The allocation it wrote balances on its own — an empty pool always does —
    // so nothing about the money gives it away. The two members never counted
    // are simply stranded in a cohort that can no longer be settled.
    const half = cohort({ memberCount: 2, counted: 0, heldStake: 0n, pool: 0n, vaultAmount: 0n });
    const prev = snapshot({ now: at, cohorts: [half], memberships: [], profiles: [] });
    expectViolation('INV-5', () =>
      assertInvariants(
        snapshot({ now: at, cohorts: [{ ...half, settled: true }], memberships: [], profiles: [] }),
        prev,
      ),
    );
  });

  it('accepts the same finalize once the count is complete', () => {
    const done = cohort({ memberCount: 2, counted: 2, heldStake: 0n, pool: 0n, vaultAmount: 0n });
    const prev = snapshot({ now: at, cohorts: [done], memberships: [], profiles: [] });
    expect(() =>
      assertInvariants(
        snapshot({ now: at, cohorts: [{ ...done, settled: true }], memberships: [], profiles: [] }),
        prev,
      ),
    ).not.toThrow();
  });
});

describe('INV-2 · the fee reaches the fee destination', () => {
  const at = FIRST_START + 3 * DAY;

  /** One member of tier 3, stake 100, about to be forfeited: fee 20, pool 80. */
  const live = cohort({ memberCount: 1, heldStake: 100n, pool: 0n, vaultAmount: 100n });
  const joined = membership();
  const forfeited = { ...joined, state: 'Forfeited' as const };
  const after = cohort({ memberCount: 1, heldStake: 0n, pool: 80n, vaultAmount: 80n });
  const slots = [profile(inCohortA)];
  /** The missed deadline of day 0, which is the outcome instant a record writes. */
  const missed = FIRST_START + DAY - 1;
  const released = [
    profile({ leagues: { 0: progress({ forfeited: 1, lastChanged: missed }) } }),
  ];

  it('accepts a forfeit whose fee lands in the treasury', () => {
    const prev = snapshot({ now: at, cohorts: [live], memberships: [joined], profiles: slots, feeDestination: 500n });
    const next = snapshot({ now: at, cohorts: [after], memberships: [forfeited], profiles: released, feeDestination: 520n });
    expect(() => assertInvariants(next, prev)).not.toThrow();
  });

  it('catches a fee that left the vault for somewhere else', () => {
    // Every figure the cohort keeps is right: the stake left held, 80 of it
    // reached the pool, and the vault fell by the whole 100. The 20 simply
    // never arrived, and only reading the destination shows it.
    const prev = snapshot({ now: at, cohorts: [live], memberships: [joined], profiles: slots, feeDestination: 500n });
    const next = snapshot({ now: at, cohorts: [after], memberships: [forfeited], profiles: released, feeDestination: 500n });
    expectViolation('INV-2', () => assertInvariants(next, prev));
  });
});

describe('INV-6 · a claim needs a settled cohort', () => {
  const at = FIRST_START + 8 * DAY;
  const finisher = membership({ state: 'Finished', daysCompleted: 7, counted: true });
  const cleared = [
    profile({
      leagues: {
        0: progress({ cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: CLEARED_A }),
      },
    }),
  ];

  /** Counted, not yet finalized: the shares are still unwritten. */
  const counting = (over: Partial<CohortSnapshot> = {}): CohortSnapshot =>
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

  it('catches a claim paid in the window before finalization', () => {
    // `settle_count` has run, so the membership already reads `Finished`; the
    // shares have not been written, so the claim takes the stake and nothing
    // else. Every figure agrees with itself — the pool is untouched, custody
    // falls by exactly what left — and the member is paid out of a cohort whose
    // denominator is not fixed yet.
    const prev = snapshot({ now: at, cohorts: [counting()], memberships: [finisher], profiles: cleared });
    const next = snapshot({
      now: at,
      cohorts: [counting({ heldStake: 0n, vaultAmount: 81n })],
      memberships: [{ ...finisher, state: 'Claimed' }],
      profiles: cleared,
    });
    expectViolation('INV-6', () => assertInvariants(next, prev));
  });
});

describe('INV-2 · a claim has to reach the member', () => {
  const at = FIRST_START + 8 * DAY;
  const finisher = membership({ state: 'Finished', daysCompleted: 7, counted: true });
  const cleared = [
    profile({
      leagues: {
        0: progress({ cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: CLEARED_A }),
      },
    }),
  ];

  /** Settled, one finisher of tier 3: stake 100 back, share 80 on top. */
  const settled = (over: Partial<CohortSnapshot> = {}): CohortSnapshot =>
    cohort({
      settled: true,
      memberCount: 1,
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

  const before = (balance: bigint): Snapshot =>
    snapshot({
      now: at,
      cohorts: [settled()],
      memberships: [finisher],
      profiles: cleared,
      walletBalances: { 'wallet-1': balance },
      faucetMint: 100n,
    });

  const after = (balance: bigint): Snapshot =>
    snapshot({
      now: at,
      cohorts: [settled({ heldStake: 0n, pool: 0n, vaultAmount: 0n })],
      memberships: [{ ...finisher, state: 'Claimed' }],
      profiles: cleared,
      walletBalances: { 'wallet-1': balance },
      faucetMint: 100n,
    });

  it('accepts a claim the member actually received', () => {
    expect(() => assertInvariants(after(180n), before(0n))).not.toThrow();
  });

  it('catches a payout that went somewhere else', () => {
    // The cohort's own books are immaculate: the vault fell by 180, the held
    // stake and the pool with it, the membership reads `Claimed`. Only the
    // wallet that was owed the money says otherwise.
    expectViolation('INV-2', () => assertInvariants(after(0n), before(0n)));
  });

  it('counts a join against the same wallet, so a step can do both', () => {
    // A member who claims one cohort and joins the next in the same step is
    // paid 180 and stakes 100, so the balance moves by 80. Nothing here should
    // need the two to be in separate steps.
    const lobby = cohort({
      address: 'cohort-B',
      index: 1,
      start: FIRST_START + 14 * DAY,
      joinCutoff: FIRST_START + 14 * DAY - 3_600,
      memberCount: 1,
      heldStake: 100n,
      vaultAmount: 100n,
    });
    const joined = membership({ address: 'm-B', cohort: 'cohort-B' });
    const prev = before(0n);
    const next = snapshot({
      now: at,
      cohorts: [settled({ heldStake: 0n, pool: 0n, vaultAmount: 0n }), lobby],
      memberships: [{ ...finisher, state: 'Claimed' }, joined],
      profiles: [
        profile({
          ...cleared[0]!,
          pendingCohort: 'cohort-B',
          pendingStart: lobby.start,
          pendingEnd: lobby.start + 7 * DAY,
        }),
      ],
      walletBalances: { 'wallet-1': 80n },
      faucetMint: 100n,
    });
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
      // The member below is a counted finisher, and INV-5 derives these three
      // from the memberships rather than taking the cohort's word for them.
      finisherCount: 1,
      finishersPerTier: [0, 0, 0, 1],
      totalFinisherStake: 100n,
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
    const derived = { cleared: 1, currentStreak: 0, longestStreak: 1, lastChanged: FORFEITED_B };
    expect(deriveStreak(memberships, cohorts, 'wallet-1', 0)).toEqual(derived);

    // Reversing the arrays is exactly what a different page order produces.
    expect(deriveStreak([...memberships].reverse(), cohorts, 'wallet-1', 0)).toEqual(derived);
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
              leagues: {
                0: progress({
                  cleared: 1,
                  forfeited: 1,
                  currentStreak: 1,
                  longestStreak: 1,
                  lastChanged: FORFEITED_B,
                }),
              },
            }),
          ],
        }),
      ),
    );
  });

  it('catches a tie-break instant taken from the crank instead of the outcome', () => {
    // Every counter is right; only the instant is the moment the settlement
    // transaction happened to land. Two members level on everything else would
    // be ordered by whichever one the crank reached first (FR-060b).
    const { a, b, memberships } = twoCohorts('Forfeited', true);
    expectViolation('INV-9', () =>
      assertInvariants(
        snapshot({
          now: FIRST_START + 8 * DAY,
          cohorts: [a, b],
          memberships,
          profiles: [
            profile({
              leagues: {
                0: progress({
                  cleared: 1,
                  forfeited: 1,
                  currentStreak: 0,
                  longestStreak: 1,
                  lastChanged: FORFEITED_B + 4_000,
                }),
              },
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
              leagues: {
                0: progress({
                  cleared: 1,
                  forfeited: 1,
                  currentStreak: 0,
                  longestStreak: 1,
                  lastChanged: FORFEITED_B,
                }),
              },
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
              leagues: {
                0: progress({
                  cleared: 1,
                  forfeited: 1,
                  currentStreak: 1,
                  longestStreak: 1,
                  // The forfeit has resolved cohort B (FR-060a), so it is the
                  // member's most recent outcome and the tie-break instant is
                  // its missed deadline. Only the streak waits for the count
                  // (FR-061a), which is why it is still 1.
                  lastChanged: FORFEITED_B,
                }),
              },
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
