/**
 * The invariant checker — INV-1…INV-9 of spec §8.4. Task T017.
 *
 * Pure functions over a decoded snapshot: no LiteSVM, no RPC, no Kit. Scenario
 * tests call `assertInvariants` after every step and pass the previous snapshot
 * so the checks that are about a *change* can run too; the randomised suite
 * (T076) calls the same function a few thousand times.
 *
 * Amounts are bigint base units. Times are numbers — chain seconds fit a double
 * with room to spare, and arithmetic on them stays readable.
 */

export type Address = string;

export type MembershipState = 'Joined' | 'Forfeited' | 'Finished' | 'Claimed';

export interface CohortSnapshot {
  address: Address;
  leagueId: number;
  index: number;
  start: number;
  joinCutoff: number;
  durationDays: number;
  dayLength: number;
  chips: [bigint, bigint, bigint, bigint];
  memberCount: number;
  heldStake: bigint;
  pool: bigint;
  carryOverReceived: bigint;
  forfeitsRecorded: number;
  counted: number;
  finisherCount: number;
  finishersPerTier: [number, number, number, number];
  totalFinisherStake: bigint;
  settled: boolean;
  sharePerTier: [bigint, bigint, bigint, bigint];
  roundingRemainder: bigint;
  carryOverHeld: bigint;
  /** SKR the cohort's vault token account actually holds. */
  vaultAmount: bigint;
}

export interface MembershipSnapshot {
  address: Address;
  cohort: Address;
  wallet: Address;
  tier: number;
  stake: bigint;
  daysCompleted: number;
  state: MembershipState;
  counted: boolean;
}

export interface LeagueProgressSnapshot {
  cleared: number;
  forfeited: number;
  currentStreak: number;
  longestStreak: number;
  lastChanged: number;
}

export interface ProfileSnapshot {
  wallet: Address;
  qualified: boolean;
  /** Keyed by league id, as the program's three slots decode. */
  leagues: Record<number, LeagueProgressSnapshot>;
  runningCohort: Address | null;
  runningEnd: number;
  pendingCohort: Address | null;
  pendingStart: number;
}

export interface Snapshot {
  now: number;
  cohorts: CohortSnapshot[];
  memberships: MembershipSnapshot[];
  profiles: ProfileSnapshot[];
  /** SKR held by the fee destination, for the delta checks. */
  feeDestination?: bigint;
}

export class InvariantError extends Error {
  /** Which invariant broke, e.g. `INV-9`. */
  readonly invariant: string;

  // A plain field rather than a constructor parameter property: this file has to
  // load under a strip-only transpiler (`node --experimental-strip-types`) as
  // well as under the bundler vitest uses.
  constructor(invariant: string, detail: string) {
    super(`${invariant}: ${detail}`);
    this.name = 'InvariantError';
    this.invariant = invariant;
  }
}

const fail = (invariant: string, detail: string): never => {
  throw new InvariantError(invariant, detail);
};

// ---------------------------------------------------------------- derivations

/** `end` of a cohort: the instant after its final deadline. */
export const cohortEnd = (c: CohortSnapshot): number => c.start + c.durationDays * c.dayLength;

/** The last second of day `k` (data-model §1.3). */
export const deadlineOf = (c: CohortSnapshot, dayIndex: number): number =>
  c.start + (dayIndex + 1) * c.dayLength - 1;

/** Whole cohort days elapsed, clamped — the program's `schedule::days_elapsed`. */
export const daysElapsed = (c: CohortSnapshot, now: number): number =>
  now < c.start ? 0 : Math.min(Math.floor((now - c.start) / c.dayLength), c.durationDays);

/** The INV-3 ceiling: the day in progress may itself be completed. */
export const maxDaysCompleted = (c: CohortSnapshot, now: number): number =>
  now < c.start ? 0 : Math.min(daysElapsed(c, now) + 1, c.durationDays);

export type CohortStatus = 'Open' | 'Locked' | 'Running' | 'Settling' | 'Settled';

export const statusOf = (c: CohortSnapshot, now: number): CohortStatus => {
  if (c.settled) return 'Settled';
  if (now < c.joinCutoff) return 'Open';
  if (now < c.start) return 'Locked';
  if (now < cohortEnd(c)) return 'Running';
  return 'Settling';
};

export const inBreach = (m: MembershipSnapshot, c: CohortSnapshot, now: number): boolean =>
  m.state === 'Joined' &&
  now >= c.start &&
  m.daysCompleted < c.durationDays &&
  now > deadlineOf(c, m.daysCompleted);

/**
 * The streak figures a profile must hold, derived rather than trusted (INV-9).
 *
 * Only *counted* memberships have had a streak written, and FR-061a makes
 * counting run in cohort order, so the counted set is always a prefix of the
 * member's history in that league. Ordering by cohort start and counting the
 * finishes since the last forfeit therefore reproduces what the program wrote,
 * whatever order the pages and the cohorts were processed in.
 */
export const deriveStreak = (
  memberships: MembershipSnapshot[],
  cohorts: Map<Address, CohortSnapshot>,
  wallet: Address,
  leagueId: number,
): { cleared: number; currentStreak: number; longestStreak: number } => {
  const history = memberships
    .filter((m) => m.wallet === wallet && m.counted)
    .map((m) => ({ m, c: cohorts.get(m.cohort) }))
    .filter((x): x is { m: MembershipSnapshot; c: CohortSnapshot } => x.c !== undefined)
    .filter((x) => x.c.leagueId === leagueId)
    .sort((a, b) => a.c.start - b.c.start || a.c.index - b.c.index);

  let cleared = 0;
  let currentStreak = 0;
  let longestStreak = 0;
  for (const { m } of history) {
    if (m.state === 'Finished' || m.state === 'Claimed') {
      cleared += 1;
      currentStreak += 1;
      longestStreak = Math.max(longestStreak, currentStreak);
    } else if (m.state === 'Forfeited') {
      currentStreak = 0;
    }
  }
  return { cleared, currentStreak, longestStreak };
};

// ---------------------------------------------------------------- the checks

const byAddress = <T extends { address: Address }>(xs: T[]): Map<Address, T> =>
  new Map(xs.map((x) => [x.address, x]));

/** INV-1 · held stakes + pool + any held carry-over == what the vault holds. */
const checkCustody = (s: Snapshot): void => {
  for (const c of s.cohorts) {
    const expected = c.heldStake + c.pool + c.carryOverHeld;
    if (c.vaultAmount !== expected) {
      fail(
        'INV-1',
        `cohort ${c.address}: vault ${c.vaultAmount} != held ${c.heldStake} + pool ${c.pool} + held carry-over ${c.carryOverHeld}`,
      );
    }
  }
};

/** INV-3 · completed days never exceed the ceiling, counting the day in progress. */
const checkDays = (s: Snapshot, cohorts: Map<Address, CohortSnapshot>): void => {
  for (const m of s.memberships) {
    const c = cohorts.get(m.cohort);
    if (!c) continue;
    const ceiling = maxDaysCompleted(c, s.now);
    if (m.daysCompleted > ceiling) {
      fail('INV-3', `${m.wallet} in ${c.address}: ${m.daysCompleted} completed, ceiling ${ceiling}`);
    }
  }
};

/** INV-4 · one running membership, one lobby place, and no overlap. */
const checkSlots = (s: Snapshot, cohorts: Map<Address, CohortSnapshot>): void => {
  const running = new Map<Address, CohortSnapshot[]>();
  const pending = new Map<Address, CohortSnapshot[]>();
  for (const m of s.memberships) {
    const c = cohorts.get(m.cohort);
    if (!c || m.state !== 'Joined') continue;
    const status = statusOf(c, s.now);
    const bucket = status === 'Running' ? running : status === 'Open' || status === 'Locked' ? pending : null;
    if (!bucket) continue;
    bucket.set(m.wallet, [...(bucket.get(m.wallet) ?? []), c]);
  }
  for (const [wallet, cs] of running) {
    if (cs.length > 1) {
      fail('INV-4', `${wallet} is in ${cs.length} running cohorts: ${cs.map((c) => c.address).join(', ')}`);
    }
  }
  for (const [wallet, cs] of pending) {
    if (cs.length > 1) {
      fail('INV-4', `${wallet} holds ${cs.length} lobby places`);
    }
    const run = running.get(wallet)?.[0];
    if (run && cs[0] && cs[0].start < cohortEnd(run)) {
      fail('INV-4', `${wallet}: lobby cohort starts ${cs[0].start}, running one ends ${cohortEnd(run)}`);
    }
  }
};

/** INV-9 · the streak follows the member's own cohorts, not the crank's order. */
const checkStreaks = (s: Snapshot, cohorts: Map<Address, CohortSnapshot>): void => {
  for (const p of s.profiles) {
    for (const [idText, progress] of Object.entries(p.leagues)) {
      const leagueId = Number(idText);
      const derived = deriveStreak(s.memberships, cohorts, p.wallet, leagueId);
      if (progress.currentStreak !== derived.currentStreak) {
        fail(
          'INV-9',
          `${p.wallet} league ${leagueId}: current streak ${progress.currentStreak}, derived ${derived.currentStreak}`,
        );
      }
      if (progress.longestStreak !== derived.longestStreak) {
        fail(
          'INV-9',
          `${p.wallet} league ${leagueId}: longest streak ${progress.longestStreak}, derived ${derived.longestStreak}`,
        );
      }
      if (progress.cleared !== derived.cleared) {
        fail(
          'INV-9',
          `${p.wallet} league ${leagueId}: cleared ${progress.cleared}, derived ${derived.cleared}`,
        );
      }
      const forfeited = s.memberships.filter((m) => {
        const c = cohorts.get(m.cohort);
        return m.wallet === p.wallet && c?.leagueId === leagueId && m.state === 'Forfeited';
      }).length;
      if (progress.forfeited !== forfeited) {
        fail(
          'INV-9',
          `${p.wallet} league ${leagueId}: forfeited ${progress.forfeited}, on-chain memberships ${forfeited}`,
        );
      }
    }
  }
};

/** INV-2, INV-5, INV-6, INV-8 — each about a change, so each needs both snapshots. */
const checkDeltas = (prev: Snapshot, next: Snapshot): void => {
  const before = byAddress(prev.cohorts);
  const prevMemberships = byAddress(prev.memberships);
  const prevCohorts = new Map(prev.cohorts.map((c) => [c.address, c]));

  for (const c of next.cohorts) {
    const b = before.get(c.address);
    if (!b) continue;

    // INV-5 · the denominator and the shares are written exactly once.
    if (b.settled) {
      if (!c.settled) fail('INV-5', `cohort ${c.address} un-settled itself`);
      if (b.totalFinisherStake !== c.totalFinisherStake) {
        fail('INV-5', `cohort ${c.address}: denominator changed after settling`);
      }
      if (b.sharePerTier.some((share, i) => share !== c.sharePerTier[i])) {
        fail('INV-5', `cohort ${c.address}: shares changed after settling`);
      }
      if (b.roundingRemainder !== c.roundingRemainder) {
        fail('INV-5', `cohort ${c.address}: remainder changed after settling`);
      }

      // INV-6 · after settling, only a claim (or the Phase 2 delivery) moves
      // anything: the pool falls by shares paid and held stake by the stake.
      if (c.pool > b.pool) fail('INV-6', `cohort ${c.address}: pool grew after settling`);
      if (c.heldStake > b.heldStake) {
        fail('INV-6', `cohort ${c.address}: held stake grew after settling`);
      }
      if (c.memberCount !== b.memberCount) {
        fail('INV-6', `cohort ${c.address}: member count changed after settling`);
      }
    }

    // INV-2 · only recorded forfeits, carry-over arrival and claims touch a pool.
    const forfeitsAdded = next.memberships.filter((m) => {
      const was = prevMemberships.get(m.address);
      return m.cohort === c.address && m.state === 'Forfeited' && was?.state === 'Joined';
    });
    const fromForfeits = forfeitsAdded.reduce((sum, m) => {
      const fee = (m.stake * 20n) / 100n;
      return sum + (m.stake - fee);
    }, 0n);
    const carryArrived = c.carryOverReceived - b.carryOverReceived;
    const claimsPaid = next.memberships
      .filter((m) => {
        const was = prevMemberships.get(m.address);
        return m.cohort === c.address && m.state === 'Claimed' && was?.state === 'Finished';
      })
      .reduce((sum, m) => sum + (c.sharePerTier[m.tier] ?? 0n), 0n);

    const available = b.pool + fromForfeits + carryArrived;
    if (c.settled && !b.settled) {
      // The finalize step is the one place a pool falls without a claim: the
      // rounding remainder leaves, and on the no-finisher path so does the whole
      // pool. It may only ever fall.
      if (c.pool > available) {
        fail('INV-2', `cohort ${c.address}: pool grew through finalize, ${available} → ${c.pool}`);
      }
    } else if (c.pool !== available - claimsPaid) {
      fail(
        'INV-2',
        `cohort ${c.address}: pool ${b.pool} → ${c.pool}, but forfeits +${fromForfeits}, carry-over +${carryArrived}, claims -${claimsPaid}`,
      );
    }
  }

  // INV-8 · a member already in breach cannot complete another day.
  for (const m of next.memberships) {
    const was = prevMemberships.get(m.address);
    const c = prevCohorts.get(m.cohort);
    if (!was || !c) continue;
    if (m.daysCompleted > was.daysCompleted && inBreach(was, c, prev.now)) {
      fail(
        'INV-8',
        `${m.wallet} in ${m.cohort} submitted while in breach at ${prev.now} (${was.daysCompleted} → ${m.daysCompleted})`,
      );
    }
  }
};

/**
 * Assert every invariant that a single snapshot can show, plus the ones about a
 * change when `previous` is supplied. Throws `InvariantError` naming the
 * invariant and the accounts involved.
 */
export const assertInvariants = (current: Snapshot, previous?: Snapshot): void => {
  const cohorts = new Map(current.cohorts.map((c) => [c.address, c]));
  checkCustody(current);
  checkDays(current, cohorts);
  checkSlots(current, cohorts);
  checkStreaks(current, cohorts);
  if (previous) checkDeltas(previous, current);
};

/**
 * INV-7 · automation is safe to repeat. A caller runs a step twice and passes
 * the snapshots either side of the second run; nothing may have moved.
 */
export const assertUnchanged = (before: Snapshot, after: Snapshot): void => {
  const strip = (s: Snapshot) => JSON.stringify({ ...s, now: 0 }, (_k, v) => (typeof v === 'bigint' ? v.toString() : v));
  if (strip(before) !== strip(after)) {
    fail('INV-7', 'running the step again changed state');
  }
};

/**
 * INV-9's other half, used by T076: the same work in a different processing
 * order must leave identical profiles.
 */
export const assertSameProgress = (a: Snapshot, b: Snapshot): void => {
  const key = (s: Snapshot) =>
    JSON.stringify(
      [...s.profiles]
        .sort((x, y) => x.wallet.localeCompare(y.wallet))
        .map((p) => [p.wallet, p.qualified, p.leagues]),
    );
  if (key(a) !== key(b)) fail('INV-9', 'two processing orders produced different progress');
};
