/**
 * The checker's own tests. Part of T017: a checker that silently passes is worse
 * than none, so every invariant is shown failing on the state that breaks it and
 * passing on the state that does not.
 *
 * These run without the program: the snapshots are built by hand, which is also
 * how the interleaving INV-9 exists for can be written before `settle_count`
 * exists to produce it (T077a covers the same case against the real program).
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

import {
  assertInvariants,
  boostFor,
  comebackOwedFor,
  comebackRefund,
  comebackRounds,
  forfeitSplit,
  assertSameProgress,
  assertUnchanged,
  deriveStreak,
  InvariantError,
  maxDaysCompleted,
  type CohortSnapshot,
  type LeagueProgressSnapshot,
  type LeagueSnapshot,
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
  comebackDue: 0n,
  forfeitsRecorded: 0,
  counted: 0,
  finisherCount: 0,
  finishersPerTier: [0, 0, 0, 0],
  totalFinisherStake: 0n,
  settled: false,
  sharePerTier: [0n, 0n, 0n, 0n],
  roundingRemainder: 0n,
  noFinisherBoost: 0n,
  boostReceived: 0n,
  comebackOwed: 0n,
  comebackFunded: 0n,
  comebackClaimed: 0n,
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
  comebackEntitled: 0n,
  ...over,
});

const progress = (over: Partial<LeagueProgressSnapshot> = {}): LeagueProgressSnapshot => ({
  cleared: 0,
  forfeited: 0,
  currentStreak: 0,
  longestStreak: 0,
  lastChanged: 0,
  comebackStake: 0n,
  comebackLeague: 0,
  comebackRound: 0,
  comebackLastRound: 0,
  ...over,
});

const profile = (over: Partial<ProfileSnapshot> = {}): ProfileSnapshot => ({
  wallet: 'wallet-1',
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
  leagues: [],
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

const WEEK = 7 * DAY;

/**
 * A league's reserve account on the real weekly schedule, holding exactly its
 * two reserves unless told otherwise. `nextIndex` 2: cohorts 0 and 1 exist.
 */
const leagueOf = (over: Partial<LeagueSnapshot> = {}): LeagueSnapshot => {
  const l = {
    address: 'league-0',
    leagueId: 0,
    boostReserve: 0n,
    comebackReserve: 0n,
    firstStart: FIRST_START,
    cadence: WEEK,
    joinLock: 3_600,
    dayLength: DAY,
    nextIndex: 2,
    ...over,
  };
  return { reserveVaultAmount: l.boostReserve + l.comebackReserve, ...l };
};

describe('INV-1 · custody', () => {
  it('passes when the vault holds the stakes and the pool', () => {
    expect(() =>
      assertInvariants(snapshot({ cohorts: [cohort({ heldStake: 400n, pool: 680n, vaultAmount: 1_080n })] })),
    ).not.toThrow();
  });

  it('fails when a single unit is missing', () => {
    expectViolation('INV-1', () =>
      assertInvariants(snapshot({ cohorts: [cohort({ heldStake: 400n, pool: 680n, vaultAmount: 1_079n })] })),
    );
  });

  /** Pledges whose forfeit is not counted yet, and refunds funded but not yet claimed. */
  const holding = { heldStake: 400n, pool: 680n, comebackDue: 5n, comebackFunded: 7n, comebackClaimed: 3n };

  it('counts the pledges not yet counted and the unclaimed refunds as the vault\'s too (H35)', () => {
    expect(() => assertInvariants(snapshot({ cohorts: [cohort({ ...holding, vaultAmount: 1_089n })] }))).not.toThrow();
    expectViolation('INV-1', () =>
      assertInvariants(snapshot({ cohorts: [cohort({ ...holding, vaultAmount: 1_088n })] })),
    );
  });

  it('fails when more refunds were claimed than the reserve funded', () => {
    expectViolation('INV-1', () =>
      assertInvariants(
        snapshot({ cohorts: [cohort({ ...holding, comebackClaimed: 8n, vaultAmount: 1_084n })] }),
      ),
    );
  });

  it('holds a league\'s reserve vault to its boost reserve plus its comeback reserve', () => {
    const l = leagueOf({ boostReserve: 30n, comebackReserve: 15n });
    expect(() => assertInvariants(snapshot({ leagues: [l] }))).not.toThrow();
    expectViolation('INV-1', () => assertInvariants(snapshot({ leagues: [{ ...l, reserveVaultAmount: 44n }] })));
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

  it('still wants the slot for a cohort that is locked and has not started', () => {
    expectViolation('INV-4', () =>
      assertInvariants(snapshot({ now: FIRST_START - 60, profiles: [profile()] })),
    );
  });

  it('lets an ended, uncounted cohort drop out of the slots', () => {
    // Cleared cohort A, took cohort B's lobby place, and B started before A was
    // counted. Normalisation promoted B over A in `running`, and here B's
    // forfeit has been recorded, which released `running` too. A's membership
    // is still `Joined` waiting for its count, but A has ended, so nothing can
    // overlap it.
    const a = cohort({ address: 'cohort-A' });
    const b = cohort({
      address: 'cohort-B',
      index: 1,
      start: FIRST_START + 7 * DAY,
      joinCutoff: FIRST_START + 7 * DAY - 3_600,
      heldStake: 0n,
      pool: 100n,
      forfeitsRecorded: 1,
    });
    const at = b.start + DAY;
    const afterForfeit = snapshot({
      now: at,
      cohorts: [a, b],
      memberships: [
        membership({ address: 'm-1', cohort: 'cohort-A', daysCompleted: 7 }),
        membership({ address: 'm-2', cohort: 'cohort-B', state: 'Forfeited' }),
      ],
      profiles: [profile({ leagues: { 0: progress({ forfeited: 1, lastChanged: b.start + DAY - 1 }) } })],
    });
    expect(() => assertInvariants(afterForfeit)).not.toThrow();

    // The same lag with B still live: `running` names B, not A.
    const whileB = snapshot({
      now: at - 60,
      cohorts: [a, { ...b, heldStake: 100n, pool: 0n, forfeitsRecorded: 0, vaultAmount: 100n }],
      memberships: [
        membership({ address: 'm-1', cohort: 'cohort-A', daysCompleted: 7 }),
        membership({ address: 'm-2', cohort: 'cohort-B', daysCompleted: 1 }),
      ],
      profiles: [profile({ runningCohort: 'cohort-B', runningEnd: b.start + 7 * DAY })],
    });
    expect(() => assertInvariants(whileB)).not.toThrow();
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

  it('sends a no-finisher pool whole to the league boost reserve, touching neither fee account', () => {
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
    const prev = snapshot({
      now: at,
      cohorts: [empty],
      leagues: [leagueOf({ boostReserve: 20n, comebackReserve: 6n })],
      memberships: [],
      profiles: [],
      operationsDestination: 40n,
      developerDestination: 40n,
    });
    const settledEmpty = { ...empty, settled: true, pool: 0n, vaultAmount: 0n, noFinisherBoost: 81n };
    const after = (over: Partial<Snapshot> = {}) =>
      snapshot({
        now: at,
        cohorts: [settledEmpty],
        leagues: [leagueOf({ boostReserve: 101n, comebackReserve: 6n })],
        memberships: [],
        profiles: [],
        developerDestination: 40n,
        operationsDestination: 40n,
        ...over,
      });
    expect(() => assertInvariants(after(), prev)).not.toThrow();

    // Split between the developer and operations, as the rule before 2026-10-12 did.
    expectViolation('INV-2', () =>
      assertInvariants(after({ developerDestination: 80n, operationsDestination: 41n }), prev),
    );
    // The pool left the cohort but never reached the league's books.
    expectViolation('INV-10', () => assertInvariants(after({ leagues: [leagueOf({ boostReserve: 20n, comebackReserve: 6n })] }), prev));
    // It reached the books, but as a pledge: the comeback reserve is not the boost reserve.
    expectViolation('INV-10', () =>
      assertInvariants(after({ leagues: [leagueOf({ boostReserve: 20n, comebackReserve: 87n })] }), prev),
    );
    // The figure the cohort records has to be the pool it had.
    expectViolation('INV-2', () =>
      assertInvariants(after({ cohorts: [{ ...settledEmpty, noFinisherBoost: 80n }] }), prev),
    );
  });
});

describe('INV-2 · the boost is min(10 % of the own pool, the boost reserve), drawn before the shares (FR-037d)', () => {
  const at = FIRST_START + 8 * DAY;
  const finisher = membership({ state: 'Finished', daysCompleted: 7, counted: true });
  const cleared = [
    profile({
      leagues: {
        0: progress({ cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: CLEARED_A }),
      },
    }),
  ];

  /** One counted finisher staking 100; a forfeit has left 70 in the pool and its pledge in the league. */
  const counted = (over: Partial<CohortSnapshot> = {}): CohortSnapshot =>
    cohort({
      memberCount: 1,
      counted: 1,
      finisherCount: 1,
      finishersPerTier: [0, 0, 0, 1],
      totalFinisherStake: 100n,
      heldStake: 100n,
      pool: 70n,
      vaultAmount: 170n,
      ...over,
    });

  /** Finalized with `boost` drawn: the finisher's share is the whole boosted pool. */
  const finalized = (boost: bigint) =>
    counted({
      settled: true,
      boostReceived: boost,
      pool: 70n + boost,
      sharePerTier: [0n, 0n, 0n, 70n + boost],
      vaultAmount: 170n + boost,
    });

  /** Reserves before, the boost a finalize claims, and where the books say it came from. */
  const step = (
    reserve: { boost: bigint; comeback: bigint },
    boost: bigint,
    after: { boost: bigint; comeback: bigint } = { boost: reserve.boost - boost, comeback: reserve.comeback },
  ) => {
    const prev = snapshot({
      now: at,
      cohorts: [counted()],
      leagues: [leagueOf({ boostReserve: reserve.boost, comebackReserve: reserve.comeback })],
      memberships: [finisher],
      profiles: cleared,
    });
    const next = snapshot({
      now: at,
      cohorts: [finalized(boost)],
      leagues: [leagueOf({ boostReserve: after.boost, comebackReserve: after.comeback })],
      memberships: [finisher],
      profiles: cleared,
    });
    return () => assertInvariants(next, prev);
  };

  it('accepts 10 % of the own pool when the boost reserve holds more', () => {
    expect(step({ boost: 50n, comeback: 20n }, 7n)).not.toThrow();
  });

  it('accepts the whole boost reserve when it holds less than 10 %', () => {
    expect(step({ boost: 5n, comeback: 0n }, 5n)).not.toThrow();
  });

  it('catches a boost above 10 % of the own pool', () => {
    expectViolation('INV-2', step({ boost: 50n, comeback: 20n }, 14n));
  });

  it('catches a boost that drew on the pledges when the boost reserve ran dry', () => {
    // 5 in the boost reserve, 20 pledged: the boost is 5. A boost of 7 that took
    // its last 2 from the comeback reserve has spent a member's pledge, and the
    // books still add up — the two reserves share one vault.
    expectViolation('INV-2', step({ boost: 5n, comeback: 20n }, 7n, { boost: 0n, comeback: 18n }));
  });

  it('catches a boost taken from the comeback reserve although the boost reserve could pay it', () => {
    expectViolation('INV-10', step({ boost: 50n, comeback: 20n }, 7n, { boost: 50n, comeback: 13n }));
  });

  it('catches a boost paid to a cohort with no forfeits, which keeps "exactly your stake back"', () => {
    const noForfeits = counted({ pool: 0n, vaultAmount: 100n });
    const prev = snapshot({
      now: at,
      cohorts: [noForfeits],
      leagues: [leagueOf({ boostReserve: 50n })],
      memberships: [finisher],
      profiles: cleared,
    });
    const next = snapshot({
      now: at,
      cohorts: [
        counted({
          settled: true,
          pool: 10n,
          boostReceived: 10n,
          sharePerTier: [0n, 0n, 0n, 10n],
          vaultAmount: 110n,
        }),
      ],
      leagues: [leagueOf({ boostReserve: 40n })],
      memberships: [finisher],
      profiles: cleared,
    });
    expectViolation('INV-2', () => assertInvariants(next, prev));
  });
});

describe('INV-10 · a league reserve moves only through counts, releases and finalizations', () => {
  const at = FIRST_START + 3 * DAY;

  it('catches a reserve topped up with nothing to move it', () => {
    const prev = snapshot({ now: at, leagues: [leagueOf()] });
    expectViolation('INV-10', () =>
      assertInvariants(snapshot({ now: at, leagues: [leagueOf({ boostReserve: 10n })] }), prev),
    );
  });

  it("catches a reserve that moved while another league's cohort finalized", () => {
    // League 1's cohort nobody finished sends its 15 to league 1's boost reserve;
    // league 0's reserve has nothing to do with it.
    const other = cohort({
      address: 'cohort-X',
      leagueId: 1,
      memberCount: 0,
      heldStake: 0n,
      pool: 15n,
      vaultAmount: 15n,
    });
    const prev = snapshot({
      now: FIRST_START + 8 * DAY,
      cohorts: [other],
      leagues: [leagueOf({ boostReserve: 20n }), leagueOf({ address: 'league-1', leagueId: 1 })],
      memberships: [],
      profiles: [],
    });
    const done = { ...other, settled: true, pool: 0n, vaultAmount: 0n, noFinisherBoost: 15n };
    const next = (league0: LeagueSnapshot) =>
      snapshot({
        now: FIRST_START + 8 * DAY,
        cohorts: [done],
        leagues: [league0, leagueOf({ address: 'league-1', leagueId: 1, boostReserve: 15n })],
        memberships: [],
        profiles: [],
      });
    expect(() => assertInvariants(next(leagueOf({ boostReserve: 20n })), prev)).not.toThrow();
    expectViolation('INV-10', () => assertInvariants(next(leagueOf({ boostReserve: 30n })), prev));
  });

  it('catches a league that appears already holding a reserve', () => {
    expectViolation('INV-10', () =>
      assertInvariants(snapshot({ leagues: [leagueOf({ comebackReserve: 5n })] }), snapshot()),
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

describe('INV-2 · a forfeit splits four ways (FR-027, H35)', () => {
  const at = FIRST_START + 3 * DAY;

  /** One member of tier 3, stake 100, about to be forfeited: 70 pool, 10 pledge, 10 developer, 10 operations. */
  const live = cohort({ memberCount: 1, heldStake: 100n, pool: 0n, vaultAmount: 100n });
  const joined = membership();
  const forfeited = { ...joined, state: 'Forfeited' as const };
  const after = cohort({ memberCount: 1, heldStake: 0n, pool: 70n, comebackDue: 10n, vaultAmount: 80n });
  const slots = [profile(inCohortA)];
  /** The missed deadline of day 0, which is the outcome instant a record writes. */
  const missed = FIRST_START + DAY - 1;
  const released = [
    profile({ leagues: { 0: progress({ forfeited: 1, lastChanged: missed }) } }),
  ];
  const prev = snapshot({
    now: at,
    cohorts: [live],
    leagues: [leagueOf()],
    memberships: [joined],
    profiles: slots,
    operationsDestination: 500n,
    developerDestination: 200n,
  });
  const next = (over: Partial<Snapshot> = {}) =>
    snapshot({
      now: at,
      cohorts: [after],
      leagues: [leagueOf()],
      memberships: [forfeited],
      profiles: released,
      operationsDestination: 510n,
      developerDestination: 210n,
      ...over,
    });

  it('accepts a forfeit whose parts each land where they belong', () => {
    expect(() => assertInvariants(next(), prev)).not.toThrow();
  });

  it('catches an operations part that left the vault for somewhere else', () => {
    // Every figure the cohort keeps is right: the stake left held, 70 of it
    // reached the pool, 10 is pledged, and the vault fell by 20. The 10 simply
    // never arrived, and only reading the destination shows it.
    expectViolation('INV-2', () => assertInvariants(next({ operationsDestination: 500n }), prev));
  });

  it("catches the developer's part paid to operations", () => {
    expectViolation('INV-2', () =>
      assertInvariants(next({ operationsDestination: 520n, developerDestination: 200n }), prev),
    );
  });

  it('catches a pledge paid out at once instead of held until its forfeit is counted', () => {
    expectViolation('INV-10', () =>
      assertInvariants(
        next({
          cohorts: [{ ...after, comebackDue: 0n, vaultAmount: 70n }],
          operationsDestination: 520n,
        }),
        prev,
      ),
    );
  });

  it('catches a pledge credited to the league when the forfeit is recorded', () => {
    // The books balance on both sides — the league's reserve vault holds what its
    // reserve says — but a pledge booked before the count would be open before
    // the member's comeback is (FR-037c).
    expectViolation('INV-10', () =>
      assertInvariants(
        next({
          cohorts: [{ ...after, comebackDue: 0n, vaultAmount: 70n }],
          leagues: [leagueOf({ comebackReserve: 10n })],
        }),
        prev,
      ),
    );
  });
});

/**
 * The comeback ledger (FR-037e). A member forfeits cohort A on its first day
 * (the deadline of day 0 is missed), so the round that makes it good is cohort
 * B, the next to start. The ledger moves the pledge of 10 — a tenth of the
 * stake of 100 — from A's vault into the league's reserve vault when the forfeit
 * is counted, and then:
 *
 *  - finishing B makes `min(10, new stake)` owed and releases the rest of the
 *    pledge to the boost reserve;
 *  - finishing any later cohort, or being forfeited again, or `release_comeback`
 *    once B has settled, releases the whole pledge to the boost reserve.
 */
describe('INV-10 · the comeback ledger (FR-037e)', () => {
  const missed = FIRST_START + DAY - 1;
  const cohortAt = (index: number, over: Partial<CohortSnapshot> = {}): CohortSnapshot =>
    cohort({
      address: `cohort-${'ABCD'[index]}`,
      index,
      start: FIRST_START + index * WEEK,
      joinCutoff: FIRST_START + index * WEEK - 3_600,
      ...over,
    });
  const at = FIRST_START + 3 * WEEK;
  const forfeitProgress = (over: Partial<LeagueProgressSnapshot> = {}) =>
    progress({ forfeited: 1, lastChanged: missed, ...over });
  /** What a counted forfeit writes: stake 100, league 0, the round after the miss. */
  const open = { comebackStake: 100n, comebackLeague: 0, comebackRound: 1, comebackLastRound: 1 };
  const lost = (counted: boolean) =>
    membership({ address: 'm-A', cohort: 'cohort-A', state: 'Forfeited', counted });
  /** Cohort A with its one forfeit recorded, counted or not. */
  const forfeitedA = (countedYet: boolean) =>
    cohortAt(0, {
      memberCount: 1,
      heldStake: 0n,
      pool: 70n,
      comebackDue: countedYet ? 0n : 10n,
      counted: countedYet ? 1 : 0,
      vaultAmount: countedYet ? 70n : 80n,
    });
  /** Cohort A, settled with nobody finished: its pool went to the boost reserve. */
  const settledA = () =>
    cohortAt(0, { memberCount: 1, counted: 1, heldStake: 0n, pool: 0n, settled: true, noFinisherBoost: 70n, vaultAmount: 0n });
  const profileOf = (over: Partial<LeagueProgressSnapshot> = {}, more: Partial<ProfileSnapshot> = {}) =>
    profile({ leagues: { 0: forfeitProgress(over) }, ...more });

  describe('counting a forfeit', () => {
    const prev = snapshot({
      now: at,
      cohorts: [forfeitedA(false)],
      leagues: [leagueOf()],
      memberships: [lost(false)],
      profiles: [profileOf()],
    });
    const next = (over: Partial<Snapshot> = {}) =>
      snapshot({
        now: at,
        cohorts: [forfeitedA(true)],
        leagues: [leagueOf({ comebackReserve: 10n })],
        memberships: [lost(true)],
        profiles: [profileOf(open)],
        ...over,
      });

    it('moves the pledge into the comeback reserve as it opens the comeback', () => {
      expect(() => assertInvariants(next(), prev)).not.toThrow();
    });

    it('catches a pledge booked to the boost reserve, where a boost could spend it', () => {
      expectViolation('INV-10', () => assertInvariants(next({ leagues: [leagueOf({ boostReserve: 10n })] }), prev));
    });

    it('catches a pledge that left the cohort and was booked nowhere', () => {
      expectViolation('INV-10', () => assertInvariants(next({ leagues: [leagueOf()] }), prev));
    });

    it('catches a comeback opened for the wrong rounds', () => {
      // A miss on day 0 is made good by the next round only; the round after it is too late.
      expectViolation('INV-9', () =>
        assertInvariants(next({ profiles: [profileOf({ ...open, comebackRound: 2, comebackLastRound: 2 })] }), prev),
      );
    });

    it('catches a count that left the comeback unopened while the pledge was booked', () => {
      expectViolation('INV-9', () => assertInvariants(next({ profiles: [profileOf()] }), prev));
    });

    it('opens a comeback with two rounds after a miss on the last day', () => {
      // The deadline of the last day is the second before cohort B starts, after
      // B's joining closed: either B or the round after it makes the pledge good.
      const lastDay = FIRST_START + 7 * DAY - 1;
      const lastDayProgress = { forfeited: 1, lastChanged: lastDay };
      const a = (countedYet: boolean) => ({ ...forfeitedA(countedYet), counted: countedYet ? 1 : 0 });
      const member = (countedYet: boolean) => ({ ...lost(countedYet), daysCompleted: 6 });
      expect(comebackRounds(leagueOf(), lastDay)).toEqual([1, 2]);
      const before = snapshot({
        now: at,
        cohorts: [a(false)],
        leagues: [leagueOf()],
        memberships: [member(false)],
        profiles: [profile({ leagues: { 0: progress(lastDayProgress) } })],
      });
      const written = snapshot({
        now: at,
        cohorts: [a(true)],
        leagues: [leagueOf({ comebackReserve: 10n })],
        memberships: [member(true)],
        profiles: [profile({ leagues: { 0: progress({ ...lastDayProgress, ...open, comebackRound: 1, comebackLastRound: 2 }) } })],
      });
      expect(() => assertInvariants(written, before)).not.toThrow();
      const narrow = snapshot({
        ...written,
        profiles: [profile({ leagues: { 0: progress({ ...lastDayProgress, ...open }) } })],
      });
      expectViolation('INV-9', () => assertInvariants(narrow, before));
    });
  });

  describe('counting a finisher of the round', () => {
    /** B: one finisher staking `stake`, counted or not yet. */
    const roundB = (countedYet: boolean, stake = 100n, owed = 0n, over: Partial<CohortSnapshot> = {}) =>
      cohortAt(1, {
        memberCount: 1,
        counted: countedYet ? 1 : 0,
        finisherCount: countedYet ? 1 : 0,
        finishersPerTier: countedYet ? [0, 0, 0, 1] : [0, 0, 0, 0],
        totalFinisherStake: countedYet ? stake : 0n,
        heldStake: stake,
        comebackOwed: owed,
        vaultAmount: stake,
        ...over,
      });
    const finish = (countedYet: boolean, over: Partial<MembershipSnapshot> = {}) =>
      membership({
        address: 'm-B',
        cohort: 'cohort-B',
        state: countedYet ? 'Finished' : 'Joined',
        daysCompleted: 7,
        counted: countedYet,
        ...over,
      });
    const cleared = (over: Partial<LeagueProgressSnapshot> = {}) =>
      forfeitProgress({ cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: FIRST_START + 2 * WEEK, ...over });

    const prev = snapshot({
      now: at,
      cohorts: [settledA(), roundB(false)],
      leagues: [leagueOf({ comebackReserve: 10n })],
      memberships: [lost(true), finish(false)],
      profiles: [profileOf(open)],
    });
    const next = (over: Partial<Snapshot> = {}) =>
      snapshot({
        now: at,
        cohorts: [settledA(), roundB(true, 100n, 10n)],
        leagues: [leagueOf({ comebackReserve: 10n })],
        memberships: [lost(true), finish(true, { comebackEntitled: 10n })],
        profiles: [profile({ leagues: { 0: cleared() } })],
        ...over,
      });

    it('makes the pledge owed and keeps it in the comeback reserve until finalization draws it', () => {
      expect(() => assertInvariants(next(), prev)).not.toThrow();
    });

    it('catches a pledge released to the boost reserve although the round was finished', () => {
      expectViolation('INV-10', () =>
        assertInvariants(next({ leagues: [leagueOf({ boostReserve: 10n })] }), prev),
      );
    });

    it('catches a finisher counted without the entitlement it earned', () => {
      expectViolation('INV-2', () =>
        assertInvariants(
          next({
            cohorts: [settledA(), roundB(true)],
            memberships: [lost(true), finish(true)],
          }),
          prev,
        ),
      );
    });

    it('catches an entitlement above the pledge', () => {
      expectViolation('INV-2', () =>
        assertInvariants(
          next({
            cohorts: [settledA(), roundB(true, 100n, 20n)],
            leagues: [leagueOf({ comebackReserve: 20n })],
            memberships: [lost(true), finish(true, { comebackEntitled: 20n })],
          }),
          prev,
        ),
      );
    });

    it('releases the part of the pledge above a smaller new stake to the boost reserve', () => {
      const small = (countedYet: boolean) => finish(countedYet, { stake: 4n });
      const before = snapshot({
        now: at,
        cohorts: [settledA(), roundB(false, 4n)],
        leagues: [leagueOf({ comebackReserve: 10n })],
        memberships: [lost(true), small(false)],
        profiles: [profileOf(open)],
      });
      const after = (reserve: { boost: bigint; comeback: bigint }) =>
        snapshot({
          now: at,
          cohorts: [settledA(), roundB(true, 4n, 4n)],
          leagues: [leagueOf({ boostReserve: reserve.boost, comebackReserve: reserve.comeback })],
          memberships: [lost(true), { ...small(true), comebackEntitled: 4n }],
          profiles: [profile({ leagues: { 0: cleared() } })],
        });
      expect(() => assertInvariants(after({ boost: 6n, comeback: 4n }), before)).not.toThrow();
      expectViolation('INV-10', () => assertInvariants(after({ boost: 0n, comeback: 10n }), before));
    });

    it('releases the whole pledge when a later round is the one finished', () => {
      const later = (countedYet: boolean) =>
        finish(countedYet, { address: 'm-C', cohort: 'cohort-C' });
      const roundC = (countedYet: boolean, over: Partial<CohortSnapshot> = {}) =>
        roundB(countedYet, 100n, 0n, { address: 'cohort-C', index: 2, start: FIRST_START + 2 * WEEK, joinCutoff: FIRST_START + 2 * WEEK - 3_600, ...over });
      const settledB = cohortAt(1, { settled: true, heldStake: 0n, pool: 0n, vaultAmount: 0n });
      const before = snapshot({
        now: at,
        cohorts: [settledA(), settledB, roundC(false)],
        leagues: [leagueOf({ comebackReserve: 10n, nextIndex: 3 })],
        memberships: [lost(true), later(false)],
        profiles: [profileOf(open)],
      });
      const after = (reserve: { boost: bigint; comeback: bigint }) =>
        snapshot({
          now: at,
          cohorts: [settledA(), settledB, roundC(true)],
          leagues: [leagueOf({ boostReserve: reserve.boost, comebackReserve: reserve.comeback, nextIndex: 3 })],
          memberships: [lost(true), later(true)],
          profiles: [profile({ leagues: { 0: cleared({ lastChanged: FIRST_START + 3 * WEEK }) } })],
        });
      expect(() => assertInvariants(after({ boost: 10n, comeback: 0n }), before)).not.toThrow();
      expectViolation('INV-10', () => assertInvariants(after({ boost: 0n, comeback: 10n }), before));
    });
  });

  describe('finalizing the round', () => {
    const owedB = cohortAt(1, {
      memberCount: 1,
      counted: 1,
      finisherCount: 1,
      finishersPerTier: [0, 0, 0, 1],
      totalFinisherStake: 100n,
      heldStake: 100n,
      comebackOwed: 10n,
      vaultAmount: 100n,
    });
    const won = membership({
      address: 'm-B',
      cohort: 'cohort-B',
      state: 'Finished',
      daysCompleted: 7,
      counted: true,
      comebackEntitled: 10n,
    });
    const cleared = [
      profile({
        leagues: {
          0: forfeitProgress({ cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: FIRST_START + 2 * WEEK }),
        },
      }),
    ];
    const prev = snapshot({
      now: at,
      cohorts: [settledA(), owedB],
      leagues: [leagueOf({ boostReserve: 3n, comebackReserve: 10n })],
      memberships: [lost(true), won],
      profiles: cleared,
    });
    const next = (funded: bigint, reserve: { boost: bigint; comeback: bigint }) =>
      snapshot({
        now: at,
        cohorts: [
          settledA(),
          {
            ...owedB,
            settled: true,
            comebackFunded: funded,
            vaultAmount: 100n + funded,
            sharePerTier: [0n, 0n, 0n, 0n],
          },
        ],
        leagues: [leagueOf({ boostReserve: reserve.boost, comebackReserve: reserve.comeback })],
        memberships: [lost(true), won],
        profiles: cleared,
      });

    it('draws what is owed from the comeback reserve, in full', () => {
      expect(() => assertInvariants(next(10n, { boost: 3n, comeback: 0n }), prev)).not.toThrow();
    });

    it('catches a refund funded short although the reserve held it', () => {
      expectViolation('INV-2', () => assertInvariants(next(6n, { boost: 3n, comeback: 4n }), prev));
    });

    it('catches a refund drawn from the boost reserve', () => {
      expectViolation('INV-10', () => assertInvariants(next(10n, { boost: 0n, comeback: 3n }), prev));
    });
  });

  describe('release_comeback', () => {
    const settledB = cohortAt(1, { settled: true, heldStake: 0n, pool: 0n, vaultAmount: 0n });
    const unsettledB = cohortAt(1, { heldStake: 0n, pool: 0n, vaultAmount: 0n });
    const with_ = (b: CohortSnapshot, record: Partial<LeagueProgressSnapshot>, reserve: { boost: bigint; comeback: bigint }) =>
      snapshot({
        now: at,
        cohorts: [settledA(), b],
        leagues: [leagueOf({ boostReserve: reserve.boost, comebackReserve: reserve.comeback })],
        memberships: [lost(true)],
        profiles: [profileOf(record)],
      });

    it('moves the pledge of a comeback whose round is settled to the boost reserve', () => {
      const prev = with_(settledB, open, { boost: 4n, comeback: 10n });
      const next = with_(settledB, {}, { boost: 14n, comeback: 0n });
      expect(() => assertInvariants(next, prev)).not.toThrow();
    });

    it('catches a release that left the pledge in the comeback reserve', () => {
      const prev = with_(settledB, open, { boost: 4n, comeback: 10n });
      expectViolation('INV-10', () => assertInvariants(with_(settledB, {}, { boost: 4n, comeback: 10n }), prev));
    });

    it('catches a release while the round can still be finished', () => {
      const prev = with_(unsettledB, open, { boost: 4n, comeback: 10n });
      expectViolation('INV-9', () => assertInvariants(with_(unsettledB, {}, { boost: 14n, comeback: 0n }), prev));
    });

    it('allows a release before an unopened round has started its first day, and not before', () => {
      // The league has opened only cohort A; the round is B, whose first day ends a week and a day on.
      const a = settledA();
      const world = (nowAt: number, record: Partial<LeagueProgressSnapshot>, reserve: { boost: bigint; comeback: bigint }) =>
        snapshot({
          now: nowAt,
          cohorts: [a],
          leagues: [leagueOf({ boostReserve: reserve.boost, comebackReserve: reserve.comeback, nextIndex: 1 })],
          memberships: [lost(true)],
          profiles: [profileOf(record)],
        });
      const firstDayEnd = FIRST_START + WEEK + DAY - 1;
      const prev = (nowAt: number) => world(nowAt, open, { boost: 0n, comeback: 10n });
      expect(() => assertInvariants(world(firstDayEnd + 1, {}, { boost: 10n, comeback: 0n }), prev(firstDayEnd + 1))).not.toThrow();
      expectViolation('INV-9', () =>
        assertInvariants(world(firstDayEnd, {}, { boost: 10n, comeback: 0n }), prev(firstDayEnd)),
      );
    });

    it('waits for the opened first round of a two-round comeback even when the last round never opens', () => {
      // A last-day miss: rounds 1 and 2. The league has opened cohorts 0 and 1 only, and cohort 2's
      // first day is long over — but cohort 1 is not settled, so a finish of it may still be uncounted.
      const two = { ...open, comebackLastRound: 2 };
      const late = cohortAt(2).start + DAY;
      const world = (b: CohortSnapshot, record: Partial<LeagueProgressSnapshot>, reserve: { boost: bigint; comeback: bigint }) =>
        snapshot({
          now: late,
          cohorts: [settledA(), b],
          leagues: [leagueOf({ boostReserve: reserve.boost, comebackReserve: reserve.comeback, nextIndex: 2 })],
          memberships: [lost(true)],
          profiles: [profile({ leagues: { 0: forfeitProgress(record) } })],
        });
      expectViolation('INV-9', () => assertInvariants(world(unsettledB, {}, { boost: 14n, comeback: 0n })));
      const prev = world(settledB, two, { boost: 4n, comeback: 10n });
      expect(() => assertInvariants(world(settledB, {}, { boost: 14n, comeback: 0n }), prev)).not.toThrow();
    });

    it('catches a record wiped while its round can still make it good, even with no step to compare', () => {
      expectViolation('INV-9', () => assertInvariants(with_(unsettledB, {}, { boost: 4n, comeback: 0n })));
      expect(() => assertInvariants(with_(settledB, {}, { boost: 14n, comeback: 0n }))).not.toThrow();
    });
  });

  describe('a boost never touches a pledge', () => {
    const world = (reserve: bigint) =>
      snapshot({
        now: at,
        cohorts: [settledA(), cohortAt(1, { heldStake: 0n, vaultAmount: 0n })],
        leagues: [leagueOf({ comebackReserve: reserve })],
        memberships: [lost(true)],
        profiles: [profileOf(open)],
      });

    it('holds the comeback reserve to at least the open pledges', () => {
      expect(() => assertInvariants(world(10n))).not.toThrow();
      expect(() => assertInvariants(world(40n))).not.toThrow();
      expectViolation('INV-10', () => assertInvariants(world(9n)));
    });

    /** The member of the comeback world, finished and counted: 10 owed, `funded` of it drawn. */
    const owing = (reserve: bigint, funded: bigint) => {
      const w = comebackWorld({ entitled: 10n, funded });
      return snapshot({
        now: FIRST_START + 30 * DAY,
        cohorts: [w.a, w.b],
        leagues: [leagueOf({ comebackReserve: reserve, nextIndex: 3 })],
        memberships: [w.lost, w.won],
        profiles: w.profiles,
      });
    };

    it('adds the refunds owed and not yet drawn', () => {
      expect(() => assertInvariants(owing(10n, 0n))).not.toThrow();
      expectViolation('INV-10', () => assertInvariants(owing(9n, 0n)));
    });

    it('stops counting a refund once it is drawn', () => {
      expect(() => assertInvariants(owing(0n, 10n))).not.toThrow();
      expect(() => assertInvariants(owing(4n, 6n))).not.toThrow();
      expectViolation('INV-10', () => assertInvariants(owing(3n, 6n)));
    });
  });
});

/**
 * A member who forfeited cohort A (day 0 missed, counted) and finished a later
 * cohort B of the same league, owed 10 % of the forfeited 100 if B is the
 * round after the miss (FR-037e). B is settled and its refund funded in full.
 */
const comebackWorld = (
  over: { bIndex?: number; bLeague?: number; entitled?: bigint; funded?: bigint; lostStake?: bigint } = {},
) => {
  const bIndex = over.bIndex ?? 1;
  const bLeague = over.bLeague ?? 0;
  const entitled = over.entitled ?? 10n;
  const funded = over.funded ?? entitled;
  const bStart = FIRST_START + bIndex * WEEK;
  const a = cohort({
    address: 'cohort-A',
    settled: true,
    memberCount: 1,
    counted: 1,
    heldStake: 0n,
    vaultAmount: 0n,
    noFinisherBoost: 70n,
  });
  const b = cohort({
    address: 'cohort-B',
    leagueId: bLeague,
    index: bLeague === 0 ? bIndex : 0,
    start: bStart,
    joinCutoff: bStart - 3_600,
    settled: true,
    memberCount: 1,
    counted: 1,
    finisherCount: 1,
    finishersPerTier: [0, 0, 0, 1],
    totalFinisherStake: 100n,
    sharePerTier: [0n, 0n, 0n, 80n],
    heldStake: 100n,
    pool: 80n,
    comebackOwed: entitled,
    comebackFunded: funded,
    vaultAmount: 180n + funded,
  });
  const lost = membership({ address: 'm-A', cohort: 'cohort-A', state: 'Forfeited', counted: true, stake: over.lostStake ?? 100n });
  const won = membership({
    address: 'm-B',
    cohort: 'cohort-B',
    state: 'Finished',
    daysCompleted: 7,
    counted: true,
    comebackEntitled: entitled,
  });
  const missed = FIRST_START + DAY - 1;
  const forfeitOnly = { forfeited: 1, lastChanged: missed };
  const clear = { cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: bStart + 7 * DAY };
  const leagues: Record<number, LeagueProgressSnapshot> =
    bLeague === 0
      ? { 0: progress({ ...forfeitOnly, ...clear }) }
      : // Another league's finish leaves league 0's pledge open: 10 of the 100 stake.
        {
          0: progress({ ...forfeitOnly, comebackStake: 100n, comebackLeague: 0, comebackRound: 1, comebackLastRound: 1 }),
          1: progress(clear),
        };
  return { a, b, lost, won, profiles: [profile({ leagues })] };
};

describe('INV-2 · a comeback is earned in its round, in the same league (FR-037e)', () => {
  const at = FIRST_START + 30 * DAY;
  const check = (w: ReturnType<typeof comebackWorld>, reserve = 0n) => () =>
    assertInvariants(
      snapshot({
        now: at,
        cohorts: [w.a, w.b],
        leagues: [leagueOf({ comebackReserve: reserve, nextIndex: 3 }), leagueOf({ address: 'league-1', leagueId: 1 })],
        memberships: [w.lost, w.won],
        profiles: w.profiles,
      }),
    );

  it('accepts 10 % of the forfeited stake for finishing the round after the miss', () => {
    expect(check(comebackWorld())).not.toThrow();
  });

  it('catches an entitlement for a round after the comeback\'s last', () => {
    // The miss on day 0 of cohort 0 is made good by cohort 1; finishing cohort 2 lets it lapse.
    expectViolation('INV-2', check(comebackWorld({ bIndex: 2 })));
    expect(check(comebackWorld({ bIndex: 2, entitled: 0n }))).not.toThrow();
  });

  it('catches an entitlement earned in another league', () => {
    // League 1's finish leaves league 0's pledge open (and covered), and earns nothing.
    expectViolation('INV-2', check(comebackWorld({ bLeague: 1 }), 10n));
    expect(check(comebackWorld({ bLeague: 1, entitled: 0n }), 10n)).not.toThrow();
  });

  it('catches a profile that kept a comeback record its finish used up', () => {
    const w = comebackWorld();
    const kept = profile({
      leagues: {
        0: { ...w.profiles[0]!.leagues[0]!, comebackStake: 100n, comebackLeague: 0, comebackRound: 1, comebackLastRound: 1 },
      },
    });
    expectViolation('INV-9', check({ ...w, profiles: [kept] }, 10n));
  });

  it('catches a comeback no longer open that nothing explains', () => {
    // League 1's finish above: league 0's record must still be open, since its
    // round (cohort 1) is neither settled nor past its first day.
    const w = comebackWorld({ bLeague: 1, entitled: 0n });
    const wiped = profile({ leagues: { 0: progress({ forfeited: 1, lastChanged: FIRST_START + DAY - 1 }), 1: w.profiles[0]!.leagues[1]! } });
    expectViolation('INV-9', check({ ...w, profiles: [wiped] }));
  });
});

describe('INV-2 · a claim pays the comeback refund', () => {
  const at = FIRST_START + 30 * DAY;
  // Owed 10, funded 10: the refund is the whole of it.
  const w = comebackWorld();
  const leagues = [leagueOf({ nextIndex: 3 })];
  const prev = snapshot({
    now: at,
    cohorts: [w.a, w.b],
    leagues,
    memberships: [w.lost, w.won],
    profiles: w.profiles,
    walletBalances: { 'wallet-1': 0n },
  });
  const claimed = (paid: bigint, over: Partial<CohortSnapshot> = {}) =>
    snapshot({
      now: at,
      cohorts: [w.a, { ...w.b, heldStake: 0n, pool: 0n, comebackClaimed: 10n, vaultAmount: 0n, ...over }],
      leagues,
      memberships: [w.lost, { ...w.won, state: 'Claimed' }],
      profiles: w.profiles,
      walletBalances: { 'wallet-1': paid },
    });

  it('accepts stake + share + the refund', () => {
    expect(() => assertInvariants(claimed(190n), prev)).not.toThrow();
  });

  it('catches a claim that left out the refund', () => {
    expectViolation('INV-2', () => assertInvariants(claimed(180n), prev));
  });

  it('catches a refund the cohort did not book as claimed', () => {
    expectViolation('INV-6', () => assertInvariants(claimed(190n, { comebackClaimed: 0n, vaultAmount: 10n }), prev));
  });

  it('scales the refund to what was funded when the reserve could not cover it', () => {
    // The program funds every refund in full, but the arithmetic is still pro rata.
    const short = comebackWorld({ lostStake: 200n, entitled: 20n, funded: 10n });
    const before = snapshot({
      now: at,
      cohorts: [short.a, short.b],
      leagues: [leagueOf({ nextIndex: 3, comebackReserve: 10n })],
      memberships: [short.lost, short.won],
      profiles: short.profiles,
      walletBalances: { 'wallet-1': 0n },
    });
    const after = (paid: bigint) =>
      snapshot({
        now: at,
        cohorts: [short.a, { ...short.b, heldStake: 0n, pool: 0n, comebackClaimed: 10n, vaultAmount: 0n }],
        leagues: [leagueOf({ nextIndex: 3, comebackReserve: 10n })],
        memberships: [short.lost, { ...short.won, state: 'Claimed' }],
        profiles: short.profiles,
        walletBalances: { 'wallet-1': paid },
      });
    expect(() => assertInvariants(after(190n), before)).not.toThrow();
    expectViolation('INV-2', () => assertInvariants(after(200n), before));
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
          // The pledge of the counted forfeit, a tenth of its stake, is in the league.
          leagues: [leagueOf({ comebackReserve: 10n, nextIndex: 2 })],
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
                  // The counted forfeit opens a comeback for cohort 2, the next
                  // to start after the missed deadline (FR-037e).
                  comebackStake: 100n,
                  comebackLeague: 0,
                  comebackRound: 2,
                  comebackLastRound: 2,
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

describe('the checker\'s money rules replay the shared vectors (fixtures/settlement-vectors.json)', () => {
  // The Rust maths replays the same file, so the checker and the program cannot
  // drift apart on a rounding rule without one suite or the other failing.
  type Big = number | string;
  const text = readFileSync(resolve(dirname(fileURLToPath(import.meta.url)), '../../fixtures/settlement-vectors.json'), 'utf8');
  // u64 maxima do not survive a double: quote every long integer before parsing.
  const vectors = JSON.parse(text.replace(/:\s*(\d{16,})/g, ': "$1"')).reserve_vectors as {
    forfeit_split: Record<'stake' | 'pool' | 'comeback' | 'developer' | 'operations', Big>[];
    boost: Record<'own_pool' | 'reserve' | 'boost', Big>[];
    comeback_owed: Record<'forfeited_stake' | 'new_stake' | 'owed', Big>[];
    comeback_refund: Record<'entitled' | 'funded' | 'owed' | 'refund', Big>[];
    comeback_round: Record<'first_start' | 'cadence' | 'join_lock' | 'missed_at' | 'first' | 'last', number>[];
  };
  const n = (v: Big) => BigInt(v);

  it('forfeit split', () => {
    for (const v of vectors.forfeit_split) {
      expect(forfeitSplit(n(v.stake)), `split of ${v.stake}`).toEqual({
        pool: n(v.pool), comeback: n(v.comeback), developer: n(v.developer), operations: n(v.operations),
      });
    }
  });

  it('boost, comeback owed and comeback refund', () => {
    for (const v of vectors.boost) expect(boostFor(n(v.own_pool), n(v.reserve))).toBe(n(v.boost));
    for (const v of vectors.comeback_owed) expect(comebackOwedFor(n(v.forfeited_stake), n(v.new_stake))).toBe(n(v.owed));
    for (const v of vectors.comeback_refund) expect(comebackRefund(n(v.entitled), n(v.funded), n(v.owed))).toBe(n(v.refund));
  });

  it('the rounds that make a comeback good', () => {
    expect(vectors.comeback_round.length).toBeGreaterThan(5);
    for (const v of vectors.comeback_round) {
      expect(
        comebackRounds({ firstStart: v.first_start, cadence: v.cadence, joinLock: v.join_lock }, v.missed_at),
        JSON.stringify(v),
      ).toEqual([v.first, v.last]);
    }
  });
});
