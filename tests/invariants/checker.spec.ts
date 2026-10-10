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
  forfeitSplit,
  noFinisherSplit,
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
  boostDue: 0n,
  comebackDue: 0n,
  forfeitsRecorded: 0,
  counted: 0,
  finisherCount: 0,
  finishersPerTier: [0, 0, 0, 0],
  totalFinisherStake: 0n,
  settled: false,
  sharePerTier: [0n, 0n, 0n, 0n],
  roundingRemainder: 0n,
  noFinisherFee: 0n,
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
  comebackFrom: 0,
  comebackUntil: 0,
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

/** A league's reserve account, holding exactly its two reserves unless told otherwise. */
const leagueOf = (over: Partial<LeagueSnapshot> = {}): LeagueSnapshot => {
  const l = { address: 'league-0', leagueId: 0, boostReserve: 0n, comebackReserve: 0n, ...over };
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

  /** Reserve shares not yet credited, and refunds funded but not yet claimed. */
  const holding = { heldStake: 400n, pool: 680n, boostDue: 10n, comebackDue: 5n, comebackFunded: 7n, comebackClaimed: 3n };

  it('counts the reserve dues and the unclaimed refunds as the vault\'s too (H35)', () => {
    expect(() => assertInvariants(snapshot({ cohorts: [cohort({ ...holding, vaultAmount: 1_099n })] }))).not.toThrow();
    expectViolation('INV-1', () =>
      assertInvariants(snapshot({ cohorts: [cohort({ ...holding, vaultAmount: 1_098n })] })),
    );
  });

  it('fails when more refunds were claimed than the reserve funded', () => {
    expectViolation('INV-1', () =>
      assertInvariants(
        snapshot({ cohorts: [cohort({ ...holding, comebackClaimed: 8n, vaultAmount: 1_094n })] }),
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

  it('splits a no-finisher pool half to the developer, the rest to operations, and credits its dues', () => {
    const empty = counted({
      memberCount: 0,
      counted: 0,
      finisherCount: 0,
      finishersPerTier: [0, 0, 0, 0],
      totalFinisherStake: 0n,
      heldStake: 0n,
      pool: 81n,
      boostDue: 5n,
      comebackDue: 5n,
      vaultAmount: 91n,
    });
    const prev = snapshot({
      now: at,
      cohorts: [empty],
      leagues: [leagueOf()],
      memberships: [],
      profiles: [],
      operationsDestination: 0n,
      developerDestination: 0n,
    });
    const settledEmpty = { ...empty, settled: true, pool: 0n, boostDue: 0n, comebackDue: 0n, vaultAmount: 0n, noFinisherFee: 81n };
    const after = (over: Partial<Snapshot> = {}) =>
      snapshot({
        now: at,
        cohorts: [settledEmpty],
        leagues: [leagueOf({ boostReserve: 5n, comebackReserve: 5n })],
        memberships: [],
        profiles: [],
        developerDestination: 40n,
        operationsDestination: 41n,
        ...over,
      });
    expect(() => assertInvariants(after(), prev)).not.toThrow();

    // Split a third / two thirds, as the rule before 2026-10-09 would: both sides of the account add up.
    expectViolation('INV-2', () =>
      assertInvariants(after({ developerDestination: 27n, operationsDestination: 54n }), prev),
    );
    // The dues left the cohort but never reached the league's books.
    expectViolation('INV-10', () => assertInvariants(after({ leagues: [leagueOf()] }), prev));
    // The fee the cohort records has to be the pool it had.
    expectViolation('INV-2', () =>
      assertInvariants(after({ cohorts: [{ ...settledEmpty, noFinisherFee: 80n }] }), prev),
    );
  });
});

describe('INV-2 · the boost is min(20 % of the own pool, the reserve), drawn before the shares (FR-037d)', () => {
  const at = FIRST_START + 8 * DAY;
  const finisher = membership({ state: 'Finished', daysCompleted: 7, counted: true });
  const cleared = [
    profile({
      leagues: {
        0: progress({ cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: CLEARED_A }),
      },
    }),
  ];

  /** One counted finisher staking 100; a forfeit has left 70 in the pool and 10 + 5 due. */
  const counted = (over: Partial<CohortSnapshot> = {}): CohortSnapshot =>
    cohort({
      memberCount: 1,
      counted: 1,
      finisherCount: 1,
      finishersPerTier: [0, 0, 0, 1],
      totalFinisherStake: 100n,
      heldStake: 100n,
      pool: 70n,
      boostDue: 10n,
      comebackDue: 5n,
      vaultAmount: 185n,
      ...over,
    });

  /** Finalized with `boost` drawn: the finisher's share is the whole boosted pool. */
  const finalized = (boost: bigint, reserve: { boost: bigint; comeback: bigint }) => ({
    cohort: counted({
      settled: true,
      boostReceived: boost,
      pool: 70n + boost,
      sharePerTier: [0n, 0n, 0n, 70n + boost],
      boostDue: 0n,
      comebackDue: 0n,
      vaultAmount: 170n + boost,
    }),
    league: leagueOf({ boostReserve: reserve.boost - boost + 10n, comebackReserve: reserve.comeback + 5n }),
  });

  const step = (reserve: { boost: bigint; comeback: bigint }, boost: bigint) => {
    const prev = snapshot({
      now: at,
      cohorts: [counted()],
      leagues: [leagueOf({ boostReserve: reserve.boost, comebackReserve: reserve.comeback })],
      memberships: [finisher],
      profiles: cleared,
    });
    const f = finalized(boost, reserve);
    const next = snapshot({ now: at, cohorts: [f.cohort], leagues: [f.league], memberships: [finisher], profiles: cleared });
    return () => assertInvariants(next, prev);
  };

  it('accepts 20 % of the own pool when the reserve holds more', () => {
    expect(step({ boost: 50n, comeback: 20n }, 14n)).not.toThrow();
  });

  it('accepts the whole reserve when it holds less than 20 %', () => {
    expect(step({ boost: 5n, comeback: 0n }, 5n)).not.toThrow();
  });

  it('catches a boost above 20 % of the own pool', () => {
    expectViolation('INV-2', step({ boost: 50n, comeback: 20n }, 20n));
  });

  it("catches a boost that drew on the cohort's own dues, credited too early", () => {
    // With 5 in reserve the boost is 5. Crediting the cohort's own 10 first
    // would make it min(14, 15) = 14 — the order FR-037d rules out.
    expectViolation('INV-2', step({ boost: 5n, comeback: 0n }, 14n));
  });

  it('catches a boost paid to a cohort with no forfeits, which keeps "exactly your stake back"', () => {
    const noForfeits = counted({ pool: 0n, boostDue: 0n, comebackDue: 0n, vaultAmount: 100n });
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
          boostDue: 0n,
          comebackDue: 0n,
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

describe('INV-10 · a league reserve moves only when one of its cohorts finalizes', () => {
  const at = FIRST_START + 3 * DAY;

  it('catches a reserve topped up with no cohort finalizing', () => {
    const prev = snapshot({ now: at, leagues: [leagueOf()] });
    expectViolation('INV-10', () =>
      assertInvariants(snapshot({ now: at, leagues: [leagueOf({ boostReserve: 10n })] }), prev),
    );
  });

  it("catches a reserve that moved while another league's cohort finalized", () => {
    const other = cohort({
      address: 'cohort-X',
      leagueId: 1,
      memberCount: 0,
      heldStake: 0n,
      pool: 0n,
      boostDue: 10n,
      comebackDue: 5n,
      vaultAmount: 15n,
    });
    const prev = snapshot({
      now: FIRST_START + 8 * DAY,
      cohorts: [other],
      leagues: [leagueOf({ boostReserve: 20n }), leagueOf({ address: 'league-1', leagueId: 1 })],
      memberships: [],
      profiles: [],
    });
    const done = { ...other, settled: true, boostDue: 0n, comebackDue: 0n, vaultAmount: 0n };
    const next = (league0: LeagueSnapshot) =>
      snapshot({
        now: FIRST_START + 8 * DAY,
        cohorts: [done],
        leagues: [league0, leagueOf({ address: 'league-1', leagueId: 1, boostReserve: 10n, comebackReserve: 5n })],
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

describe('INV-2 · a forfeit splits five ways (FR-027, H35)', () => {
  const at = FIRST_START + 3 * DAY;

  /** One member of tier 3, stake 100, about to be forfeited: 70 pool, 5 + 5 due, 10 developer, 10 operations. */
  const live = cohort({ memberCount: 1, heldStake: 100n, pool: 0n, vaultAmount: 100n });
  const joined = membership();
  const forfeited = { ...joined, state: 'Forfeited' as const };
  const after = cohort({ memberCount: 1, heldStake: 0n, pool: 70n, boostDue: 5n, comebackDue: 5n, vaultAmount: 80n });
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
    // reached the pool, 10 is due, and the vault fell by 20. The 10 simply never
    // arrived, and only reading the destination shows it.
    expectViolation('INV-2', () => assertInvariants(next({ operationsDestination: 500n }), prev));
  });

  it("catches the developer's part paid to operations", () => {
    expectViolation('INV-2', () =>
      assertInvariants(next({ operationsDestination: 520n, developerDestination: 200n }), prev),
    );
  });

  it('catches a reserve share paid out at once instead of held as due', () => {
    expectViolation('INV-10', () =>
      assertInvariants(
        next({
          cohorts: [{ ...after, boostDue: 0n, vaultAmount: 75n }],
          operationsDestination: 515n,
        }),
        prev,
      ),
    );
  });

  it('catches a reserve share credited to the league when the forfeit is recorded', () => {
    // The books balance on both sides — the league's reserve vault holds what its
    // reserve says — but a reserve credited now would let the crank's timing set
    // an earlier cohort's boost (FR-037c).
    expectViolation('INV-10', () =>
      assertInvariants(
        next({
          cohorts: [{ ...after, boostDue: 0n, comebackDue: 0n, vaultAmount: 70n }],
          leagues: [leagueOf({ boostReserve: 10n, comebackReserve: 5n })],
        }),
        prev,
      ),
    );
  });
});

/**
 * A member who forfeited cohort A (day 0 missed, counted) and finished cohort B
 * — by default of the same league a week later: inside the 14-day window, so
 * owed 20 % of the forfeited 100 (FR-037e). B is settled; the reserve funded
 * half of what it owed.
 */
const comebackWorld = (over: { bStart?: number; bLeague?: number; entitled?: bigint } = {}) => {
  const missed = FIRST_START + DAY - 1;
  const bStart = over.bStart ?? FIRST_START + 7 * DAY;
  const bLeague = over.bLeague ?? 0;
  const entitled = over.entitled ?? 20n;
  const a = cohort({
    address: 'cohort-A',
    settled: true,
    memberCount: 1,
    counted: 1,
    heldStake: 0n,
    vaultAmount: 0n,
  });
  const b = cohort({
    address: 'cohort-B',
    leagueId: bLeague,
    index: bLeague === 0 ? 1 : 0,
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
    comebackFunded: entitled / 2n,
    vaultAmount: 180n + entitled / 2n,
  });
  const lost = membership({ address: 'm-A', cohort: 'cohort-A', state: 'Forfeited', counted: true });
  const won = membership({
    address: 'm-B',
    cohort: 'cohort-B',
    state: 'Finished',
    daysCompleted: 7,
    counted: true,
    comebackEntitled: entitled,
  });
  const window = { comebackFrom: missed, comebackUntil: missed + 14 * DAY };
  const inWindow = bLeague === 0 && bStart > missed && bStart <= window.comebackUntil;
  const forfeitOnly = { forfeited: 1, lastChanged: missed, ...window, comebackStake: inWindow ? 0n : 100n };
  const clear = { cleared: 1, currentStreak: 1, longestStreak: 1, lastChanged: bStart + 7 * DAY };
  const leagues: Record<number, LeagueProgressSnapshot> =
    bLeague === 0
      ? { 0: progress({ ...forfeitOnly, ...clear }) }
      : { 0: progress(forfeitOnly), 1: progress(clear) };
  return { a, b, lost, won, profiles: [profile({ leagues })] };
};

describe('INV-2 · a comeback is earned in the window, in the same league (FR-037e)', () => {
  const at = FIRST_START + 30 * DAY;
  const check = (w: ReturnType<typeof comebackWorld>) => () =>
    assertInvariants(snapshot({ now: at, cohorts: [w.a, w.b], memberships: [w.lost, w.won], profiles: w.profiles }));

  it('accepts 20 % of the forfeited stake for finishing a later cohort a week on', () => {
    expect(check(comebackWorld())).not.toThrow();
  });

  it('accepts a cohort starting on the last second of the window', () => {
    expect(check(comebackWorld({ bStart: FIRST_START + DAY - 1 + 14 * DAY }))).not.toThrow();
  });

  it('catches an entitlement for a cohort starting one second after the window', () => {
    expectViolation('INV-2', check(comebackWorld({ bStart: FIRST_START + DAY + 14 * DAY })));
  });

  it('catches an entitlement earned in another league', () => {
    expectViolation('INV-2', check(comebackWorld({ bLeague: 1 })));
  });

  it('catches a profile that kept a comeback record its finish used up', () => {
    const w = comebackWorld();
    const kept = profile({ leagues: { 0: { ...w.profiles[0]!.leagues[0]!, comebackStake: 100n } } });
    expectViolation('INV-9', check({ ...w, profiles: [kept] }));
  });
});

describe('INV-2 · a claim pays the comeback refund, scaled to what was funded', () => {
  const at = FIRST_START + 30 * DAY;
  // Owed 20, funded 10: the reserve was short, so the refund is 10.
  const w = comebackWorld();
  const prev = snapshot({
    now: at,
    cohorts: [w.a, w.b],
    memberships: [w.lost, w.won],
    profiles: w.profiles,
    walletBalances: { 'wallet-1': 0n },
  });
  const claimed = (paid: bigint, over: Partial<CohortSnapshot> = {}) =>
    snapshot({
      now: at,
      cohorts: [w.a, { ...w.b, heldStake: 0n, pool: 0n, comebackClaimed: 10n, vaultAmount: 0n, ...over }],
      memberships: [w.lost, { ...w.won, state: 'Claimed' }],
      profiles: w.profiles,
      walletBalances: { 'wallet-1': paid },
    });

  it('accepts stake + share + the pro-rata refund', () => {
    expect(() => assertInvariants(claimed(190n), prev)).not.toThrow();
  });

  it('catches the full entitlement paid out of a short reserve', () => {
    expectViolation('INV-2', () => assertInvariants(claimed(200n), prev));
  });

  it('catches a refund the cohort did not book as claimed', () => {
    expectViolation('INV-6', () => assertInvariants(claimed(190n, { comebackClaimed: 0n, vaultAmount: 10n }), prev));
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
                  // The counted forfeit opens a comeback for 14 days (FR-037e).
                  comebackStake: 100n,
                  comebackFrom: FORFEITED_B,
                  comebackUntil: FORFEITED_B + 14 * DAY,
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
    forfeit_split: Record<'stake' | 'pool' | 'boost' | 'comeback' | 'developer' | 'operations', Big>[];
    boost: Record<'own_pool' | 'reserve' | 'boost', Big>[];
    comeback_owed: Record<'forfeited_stake' | 'new_stake' | 'owed', Big>[];
    comeback_refund: Record<'entitled' | 'funded' | 'owed' | 'refund', Big>[];
    no_finisher_split: Record<'pool' | 'developer' | 'operations', Big>[];
  };
  const n = (v: Big) => BigInt(v);

  it('forfeit split', () => {
    for (const v of vectors.forfeit_split) {
      expect(forfeitSplit(n(v.stake)), `split of ${v.stake}`).toEqual({
        pool: n(v.pool), boost: n(v.boost), comeback: n(v.comeback), developer: n(v.developer), operations: n(v.operations),
      });
    }
  });

  it('boost, comeback owed, comeback refund and the no-finisher split', () => {
    for (const v of vectors.boost) expect(boostFor(n(v.own_pool), n(v.reserve))).toBe(n(v.boost));
    for (const v of vectors.comeback_owed) expect(comebackOwedFor(n(v.forfeited_stake), n(v.new_stake))).toBe(n(v.owed));
    for (const v of vectors.comeback_refund) expect(comebackRefund(n(v.entitled), n(v.funded), n(v.owed))).toBe(n(v.refund));
    for (const v of vectors.no_finisher_split) {
      expect(noFinisherSplit(n(v.pool))).toEqual({ developer: n(v.developer), operations: n(v.operations) });
    }
  });
});
