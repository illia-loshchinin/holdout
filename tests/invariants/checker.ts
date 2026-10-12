/**
 * The invariant checker — INV-1…INV-10 of spec §8.4. Task T017, amended for H35
 * and again on 2026-10-12 (the guaranteed comeback).
 *
 * Pure functions over a decoded snapshot: no LiteSVM, no RPC, no Kit. Scenario
 * tests call `assertInvariants` after every step and pass the previous snapshot
 * so the checks that are about a *change* can run too; the randomised suite
 * (T076) calls the same function a few thousand times.
 *
 * Amounts are bigint base units. Times are numbers — chain seconds fit a double
 * with room to spare, and arithmetic on them stays readable.
 *
 * ## The money since 2026-10-12
 *
 * A forfeited stake splits 70 / 10 / 10 / 10 (FR-027): the pool part and the
 * member's comeback pledge (`comeback_due`) stay in the cohort's vault, the
 * developer and operations parts leave at once. The pledge moves into the
 * league's reserve vault when the forfeit is *counted* and is booked to the
 * league's comeback reserve in the same instruction that opens the member's
 * comeback (FR-037c, FR-037e), so an open comeback is always funded. A pledge
 * reaches the boost reserve only by being released: replaced by a newer counted
 * forfeit, left unclaimed by a finish outside the comeback's rounds or above the
 * new stake, or released by `release_comeback`. A boost draws on the boost
 * reserve alone (FR-037d), and a cohort nobody finished sends its whole pool
 * there (FR-037). Nothing carries over between cohorts.
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
  /** The comeback pledges of this cohort's recorded forfeits, held in its vault until each is counted. */
  comebackDue: bigint;
  forfeitsRecorded: number;
  counted: number;
  finisherCount: number;
  finishersPerTier: [number, number, number, number];
  totalFinisherStake: bigint;
  settled: boolean;
  sharePerTier: [bigint, bigint, bigint, bigint];
  roundingRemainder: bigint;
  /** A cohort nobody finished: its whole pool, sent to the league's boost reserve. */
  noFinisherBoost: bigint;
  /** What finalization drew from the league's boost reserve into the pool. */
  boostReceived: bigint;
  /** Σ comeback entitlements of its counted finishers. */
  comebackOwed: bigint;
  /** What finalization drew from the comeback reserve: all of `comebackOwed`, which the reserve always holds. */
  comebackFunded: bigint;
  /** Refunds paid out with claims so far. */
  comebackClaimed: bigint;
  /** SKR the cohort's vault token account actually holds. */
  vaultAmount: bigint;
}

export interface LeagueSnapshot {
  address: Address;
  leagueId: number;
  boostReserve: bigint;
  comebackReserve: bigint;
  /** The league's schedule: a comeback's rounds and their release are read from it. */
  firstStart: number;
  cadence: number;
  joinLock: number;
  dayLength: number;
  /** The index the league opens next: cohorts below it exist. */
  nextIndex: number;
  /** SKR the league's reserve vault token account actually holds. */
  reserveVaultAmount: bigint;
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
  /** The comeback refund this finisher is owed, before any pro-rata scaling. */
  comebackEntitled: bigint;
}

export interface LeagueProgressSnapshot {
  cleared: number;
  forfeited: number;
  currentStreak: number;
  longestStreak: number;
  lastChanged: number;
  /** The latest counted forfeit's stake while its comeback is open; 0 once made good or released. */
  comebackStake: bigint;
  comebackLeague: number;
  /** The first and the last round (cohort indices of `comebackLeague`) that make it good. */
  comebackRound: number;
  comebackLastRound: number;
}

export interface ProfileSnapshot {
  wallet: Address;
  /** Keyed by league id, as the program's three slots decode. */
  leagues: Record<number, LeagueProgressSnapshot>;
  runningCohort: Address | null;
  runningEnd: number;
  pendingCohort: Address | null;
  pendingStart: number;
  /** The lobby cohort's own end, carried so the promotion can hand it over. */
  pendingEnd: number;
  /** Global day index of the last faucet claim; -1 = never (R13). */
  lastFaucetDay: number;
}

export interface Snapshot {
  now: number;
  cohorts: CohortSnapshot[];
  /** Every league account, with what its reserve vault holds. */
  leagues: LeagueSnapshot[];
  memberships: MembershipSnapshot[];
  profiles: ProfileSnapshot[];
  /** SKR held by the operations destination (`Config.fee_destination`), for the delta checks. */
  operationsDestination?: bigint;
  /** SKR held by the developer destination (H35), for the delta checks. */
  developerDestination?: bigint;
  /**
   * SKR each member wallet holds, by wallet. Every transfer has two sides, and
   * this is the one outside the program: without it a `claim_payout` that moved
   * the right amount to the wrong token account is indistinguishable from one
   * that paid the member. Absent when no config names a mint.
   */
  walletBalances?: Record<Address, bigint>;
  /** What one faucet claim mints — `chip_set.max_top_tier` (FR-042a). */
  faucetMint?: bigint;
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

// ------------------------------------------------------------ the money rules
// The same arithmetic as `programs/holdout/src/math/settlement.rs`, written
// again from the spec so the two can disagree; `checker.spec.ts` replays
// `fixtures/settlement-vectors.json` through these.

/** The fee on one forfeited stake: 30 %, rounded down (FR-027). */
export const feeOf = (stake: bigint): bigint => (stake * 30n) / 100n;

export interface ForfeitSplit {
  pool: bigint;
  comeback: bigint;
  developer: bigint;
  operations: bigint;
}

/** The member's comeback pledge on a forfeited stake: 10 %, rounded down (FR-037e). */
export const comebackPledge = (stake: bigint): bigint => (stake * 10n) / 100n;

/** Where one forfeited stake goes: 70 / 10 / 10 / 10, operations taking the rounding (FR-027, H35; re-split 2026-10-12). */
export const forfeitSplit = (stake: bigint): ForfeitSplit => {
  const fee = feeOf(stake);
  const comeback = comebackPledge(stake);
  const developer = (stake * 10n) / 100n;
  return { pool: stake - fee, comeback, developer, operations: fee - comeback - developer };
};

const min = (a: bigint, b: bigint): bigint => (a < b ? a : b);

/** `min(10 % of the cohort's own pool, the league's boost reserve)` (FR-037d). */
export const boostFor = (ownPool: bigint, boostReserve: bigint): bigint => min((ownPool * 10n) / 100n, boostReserve);

/** `min(the pledge of the forfeited stake, the stake finished on)` (FR-037e). */
export const comebackOwedFor = (forfeitedStake: bigint, newStake: bigint): bigint =>
  min(comebackPledge(forfeitedStake), newStake);

/** One finisher's refund, scaled pro rata when the reserve funded less than was owed (FR-037e). */
export const comebackRefund = (entitled: bigint, funded: bigint, owed: bigint): bigint =>
  owed === 0n ? 0n : (entitled * funded) / owed;

/** `first_start + index * cadence` (research R11). */
export const cohortStartOf = (l: Pick<LeagueSnapshot, 'firstStart' | 'cadence'>, index: number): number =>
  l.firstStart + index * l.cadence;

/** The smallest `k` with `first_start + k * cadence > missed_at`, clamped at 0 (`schedule::comeback_round`). */
export const comebackRound = (l: Pick<LeagueSnapshot, 'firstStart' | 'cadence'>, missedAt: number): number =>
  Math.max(0, Math.min(Math.floor((missedAt - l.firstStart) / l.cadence) + 1, 2 ** 32 - 1));

/**
 * The rounds that make a comeback good (`schedule::comeback_rounds`): the first
 * cohort of the league to start after the missed deadline, and — when that cohort
 * had already closed for joining the second after the miss — the one after it.
 */
export const comebackRounds = (
  l: Pick<LeagueSnapshot, 'firstStart' | 'cadence' | 'joinLock'>,
  missedAt: number,
): [first: number, last: number] => {
  const first = comebackRound(l, missedAt);
  const cutoff = cohortStartOf(l, first) - l.joinLock;
  return missedAt + 1 < cutoff ? [first, first] : [first, first + 1];
};

/** A profile's comeback record for one league, as the three slots hold it. */
export interface ComebackRecord {
  stake: bigint;
  league: number;
  round: number;
  lastRound: number;
}

export const NO_COMEBACK: ComebackRecord = { stake: 0n, league: 0, round: 0, lastRound: 0 };

export const comebackOf = (p: LeagueProgressSnapshot): ComebackRecord => ({
  stake: p.comebackStake,
  league: p.comebackLeague,
  round: p.comebackRound,
  lastRound: p.comebackLastRound,
});

const sameRecord = (a: ComebackRecord, b: ComebackRecord): boolean =>
  a.stake === b.stake && a.league === b.league && a.round === b.round && a.lastRound === b.lastRound;

const isOpen = (r: ComebackRecord): boolean => r.stake > 0n;

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
): { cleared: number; currentStreak: number; longestStreak: number; lastChanged: number } => {
  // Counted memberships, plus forfeited ones that automation has recorded but
  // not yet counted: `forfeited` and `last_changed` move the moment a forfeit
  // is recorded (FR-060a, data-model §1.5), while the streak figures are
  // written only by `settle_count`. Filtering the history on `counted` alone
  // would derive a `last_changed` of 0 for a member whose forfeit is recorded
  // and whose cohort has not been counted yet — a window every paged crank
  // run passes through.
  const history = memberships
    .filter((m) => m.wallet === wallet && (m.counted || m.state === 'Forfeited'))
    .map((m) => ({ m, c: cohorts.get(m.cohort) }))
    .filter((x): x is { m: MembershipSnapshot; c: CohortSnapshot } => x.c !== undefined)
    .filter((x) => x.c.leagueId === leagueId)
    .sort((a, b) => a.c.start - b.c.start || a.c.index - b.c.index);

  let cleared = 0;
  let currentStreak = 0;
  let longestStreak = 0;
  // The instant of the most recent outcome, and never the time a transaction
  // landed (FR-060b). `LeagueProgress::touch` takes the maximum, so the figure
  // is order-independent — which is the whole point, and is also what makes it
  // derivable here: the largest outcome instant in the counted history.
  let lastChanged = 0;
  for (const { m, c } of history) {
    if (m.state === 'Finished' || m.state === 'Claimed') {
      cleared += 1;
      currentStreak += 1;
      longestStreak = Math.max(longestStreak, currentStreak);
      lastChanged = Math.max(lastChanged, cohortEnd(c));
    } else if (m.state === 'Forfeited') {
      // The outcome instant lands when the forfeit is recorded; the streak
      // only breaks once the cohort counts it, because `settle_count` is the
      // one place that writes streaks (INV-9).
      if (m.counted) currentStreak = 0;
      lastChanged = Math.max(lastChanged, deadlineOf(c, m.daysCompleted));
    }
  }
  return { cleared, currentStreak, longestStreak, lastChanged };
};

/**
 * Whether a comeback's pledge can be released at `now` by `release_comeback`:
 * the latest of its rounds the league has opened — cohort
 * `min(last, next_index − 1)` — is settled (and with it every earlier one), and a
 * round not opened yet has had its first day pass (FR-037e). Once true it stays
 * true — cohorts only settle and the clock only runs forward — so a single
 * snapshot can tell a profile that was lawfully released from one whose record
 * was wiped.
 */
export const releasable = (
  league: LeagueSnapshot,
  cohorts: Iterable<CohortSnapshot>,
  record: ComebackRecord,
  now: number,
): boolean => {
  const last = record.lastRound;
  if (league.nextIndex > record.round) {
    const latest = Math.min(last, league.nextIndex - 1);
    let settled = false;
    for (const c of cohorts) if (c.leagueId === league.leagueId && c.index === latest) settled = c.settled;
    if (!settled) return false;
  }
  if (league.nextIndex <= last) return now > cohortStartOf(league, last) + league.dayLength - 1;
  return true;
};

/**
 * The comeback record a profile must hold for one league, and what each counted
 * finisher is entitled to, derived rather than trusted (FR-037e).
 *
 * Written only by `settle_count`, in cohort order like the streak, so the same
 * ordering argument applies: walk the member's counted cohorts of the league by
 * start. A counted forfeit replaces the record with its stake and the rounds
 * that make it good (`comebackRounds`); a counted finish of one of those rounds
 * is owed `min(10 % of the forfeited stake, its own stake)` and closes the
 * record; a counted finish of a later cohort lets it lapse. `release_comeback`
 * leaves no trace in the memberships, so a record cleared by it is recognised in
 * `checkStreaks` by `releasable` rather than here.
 *
 * `known` is false when the league's schedule is not in the snapshot.
 */
export const deriveComeback = (
  memberships: MembershipSnapshot[],
  cohorts: Map<Address, CohortSnapshot>,
  leagues: Map<number, LeagueSnapshot>,
  wallet: Address,
  leagueId: number,
): { known: boolean; record: ComebackRecord; entitled: Map<Address, bigint> } => {
  const league = leagues.get(leagueId);
  const entitled = new Map<Address, bigint>();
  if (!league) return { known: false, record: NO_COMEBACK, entitled };
  const history = memberships
    .filter((m) => m.wallet === wallet && m.counted)
    .map((m) => ({ m, c: cohorts.get(m.cohort) }))
    .filter((x): x is { m: MembershipSnapshot; c: CohortSnapshot } => x.c !== undefined)
    .filter((x) => x.c.leagueId === leagueId)
    .sort((a, b) => a.c.start - b.c.start || a.c.index - b.c.index);

  let record = NO_COMEBACK;
  for (const { m, c } of history) {
    if (m.state === 'Forfeited') {
      const [round, lastRound] = comebackRounds(league, deadlineOf(c, m.daysCompleted));
      record = { stake: m.stake, league: leagueId, round, lastRound };
    } else if (m.state === 'Finished' || m.state === 'Claimed') {
      if (!isOpen(record)) continue;
      if (c.index >= record.round && c.index <= record.lastRound) {
        entitled.set(m.address, comebackOwedFor(record.stake, m.stake));
        record = NO_COMEBACK;
      } else if (c.index > record.lastRound) {
        record = NO_COMEBACK;
      }
    }
  }
  return { known: true, record, entitled };
};

// ---------------------------------------------------------------- the checks

const byAddress = <T extends { address: Address }>(xs: T[]): Map<Address, T> =>
  new Map(xs.map((x) => [x.address, x]));

/** What INV-1 says a cohort's vault holds. */
export const custodyOf = (c: CohortSnapshot): bigint =>
  c.heldStake + c.pool + c.comebackDue + (c.comebackFunded - c.comebackClaimed);

/**
 * INV-1 · a cohort's vault holds its held stakes, its pool, the comeback pledges
 * of its forfeits not yet counted and the comeback refunds funded and not yet
 * claimed; a league's reserve vault holds its boost reserve plus its comeback
 * reserve.
 */
const checkCustody = (s: Snapshot): void => {
  for (const c of s.cohorts) {
    if (c.comebackClaimed > c.comebackFunded) {
      fail('INV-1', `cohort ${c.address}: claimed ${c.comebackClaimed} of comeback refunds, only ${c.comebackFunded} funded`);
    }
    const expected = custodyOf(c);
    if (c.vaultAmount !== expected) {
      fail(
        'INV-1',
        `cohort ${c.address}: vault ${c.vaultAmount} != held ${c.heldStake} + pool ${c.pool} + comeback due ${c.comebackDue} + unclaimed refunds ${c.comebackFunded - c.comebackClaimed}`,
      );
    }
  }
  for (const l of s.leagues) {
    if (l.reserveVaultAmount !== l.boostReserve + l.comebackReserve) {
      fail(
        'INV-1',
        `league ${l.leagueId}: reserve vault ${l.reserveVaultAmount} != boost reserve ${l.boostReserve} + comeback reserve ${l.comebackReserve}`,
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

  // The profile's own slots are what `join_cohort` reads, so they have to say
  // the same thing the cohorts do. The end matters most: it is the only figure
  // the overlap guard has, a lobby place is promoted without the cohort account
  // in hand, and a promotion that dropped it would leave a zero here — which
  // reads as "no obligation" and opens the overlap this invariant forbids.
  for (const p of s.profiles) {
    const run = p.runningCohort === null ? undefined : cohorts.get(p.runningCohort);
    if (run && p.runningEnd !== cohortEnd(run)) {
      fail(
        'INV-4',
        `${p.wallet}: running slot ends ${p.runningEnd}, cohort ${run.address} ends ${cohortEnd(run)}`,
      );
    }
    const lobby = p.pendingCohort === null ? undefined : cohorts.get(p.pendingCohort);
    if (lobby && (p.pendingStart !== lobby.start || p.pendingEnd !== cohortEnd(lobby))) {
      fail(
        'INV-4',
        `${p.wallet}: lobby slot ${p.pendingStart}–${p.pendingEnd}, cohort ${lobby.address} runs ${lobby.start}–${cohortEnd(lobby)}`,
      );
    }
  }

  // Both directions between the profile's slots and the memberships that back
  // them. Checking only the slots a profile happens to name leaves the gap that
  // matters: a `join_cohort` that wrote a `Joined` membership and left the slot
  // empty passes here, and the *next* join then sees a free slot and takes a
  // second obligation — which only a scenario that tries that second join would
  // ever surface.
  //
  // Only a cohort that can still overlap something needs a slot: one in its
  // lobby or running. Once a cohort has ended (Settling) its membership stays
  // `Joined` until it is counted, but the slot may already be gone, and that is
  // lawful. Normalisation promotes the next lobby place over it, and recording
  // that next cohort's forfeit then releases `running` (data-model §1.5). An
  // ended cohort has no deadline left to overlap, so dropping it from the slot
  // loses nothing.
  const slotsOf = new Map(s.profiles.map((p) => [p.wallet, p]));
  for (const m of s.memberships) {
    const c = cohorts.get(m.cohort);
    if (!c || m.state !== 'Joined') continue;
    const status = statusOf(c, s.now);
    if (status === 'Settling' || status === 'Settled') continue;
    const p = slotsOf.get(m.wallet);
    if (!p) {
      fail('INV-4', `${m.wallet} is joined to ${c.address} with no profile to hold the slot`);
      continue;
    }
    // Either slot will do. Normalisation runs inside the member's own
    // instructions, so a lobby place whose cohort has started stays in
    // `pending` until the member sends the next one — that lag is lawful, and
    // the overlap guard covers it either way (data-model §1.5).
    if (p.runningCohort !== c.address && p.pendingCohort !== c.address) {
      fail(
        'INV-4',
        `${m.wallet} is joined to ${c.address}, but its profile holds running ${p.runningCohort ?? 'none'} / lobby ${p.pendingCohort ?? 'none'}`,
      );
    }
  }
  for (const p of s.profiles) {
    for (const [slot, named] of [
      ['running', p.runningCohort],
      ['lobby', p.pendingCohort],
    ] as const) {
      if (named === null || !cohorts.has(named)) continue;
      const held = s.memberships.some(
        (m) => m.wallet === p.wallet && m.cohort === named && m.state === 'Joined',
      );
      // A slot outlives nothing: `leave_cohort` clears the lobby place, and
      // recording a forfeit or counting a finisher releases the running one in
      // the same instruction that moves the membership off `Joined`. A slot
      // still naming a cohort the member has left refuses the member's next
      // join for an obligation that no longer exists.
      if (!held) {
        fail('INV-4', `${p.wallet}: ${slot} slot names ${named}, with no joined membership in it`);
      }
    }
  }
};

/**
 * INV-5 · the denominator, and the tally it is built from, are the counted
 * memberships and nothing else.
 *
 * `settle_count` writes `finisher_count`, `finishers_per_tier`,
 * `total_finisher_stake` and `comeback_owed` page by page, and `settle_finalize`
 * divides by the denominator and funds the comeback total. Taking those figures
 * at face value leaves the allocation self-consistent no matter what they say:
 * an overstated denominator produces smaller shares and a larger remainder that
 * still add back to the pool, so the money checks pass while the difference has
 * been handed to the operations destination — and the first member to claim at
 * their real tier finds the cohort short. Derived here instead, from the
 * memberships that carry `counted`, so the figures are checked at every stage of
 * the count rather than only at the end.
 */
const checkTallies = (s: Snapshot): void => {
  for (const c of s.cohorts) {
    const finishers = s.memberships.filter(
      (m) => m.cohort === c.address && m.counted && (m.state === 'Finished' || m.state === 'Claimed'),
    );
    if (c.finisherCount !== finishers.length) {
      fail(
        'INV-5',
        `cohort ${c.address}: ${c.finisherCount} finishers recorded, ${finishers.length} counted memberships finished`,
      );
    }
    const perTier = [0, 0, 0, 0];
    let stake = 0n;
    for (const m of finishers) {
      if (m.tier < 0 || m.tier > 3) fail('INV-5', `${m.wallet} in ${c.address}: tier ${m.tier}`);
      perTier[m.tier] = (perTier[m.tier] ?? 0) + 1;
      stake += m.stake;
    }
    if (c.finishersPerTier.some((n, t) => n !== perTier[t])) {
      fail(
        'INV-5',
        `cohort ${c.address}: tiers recorded [${c.finishersPerTier}], counted memberships give [${perTier}]`,
      );
    }
    if (c.totalFinisherStake !== stake) {
      fail(
        'INV-5',
        `cohort ${c.address}: denominator ${c.totalFinisherStake}, counted finishers staked ${stake}`,
      );
    }
    const owed = s.memberships
      .filter((m) => m.cohort === c.address)
      .reduce((sum, m) => sum + m.comebackEntitled, 0n);
    if (c.comebackOwed !== owed) {
      fail('INV-5', `cohort ${c.address}: comeback owed ${c.comebackOwed}, its memberships are entitled to ${owed}`);
    }
  }
};

/**
 * INV-9 · the streak follows the member's own cohorts, not the crank's order —
 * and so does the comeback record, which `settle_count` writes beside it.
 */
const checkStreaks = (s: Snapshot, cohorts: Map<Address, CohortSnapshot>): void => {
  const leagues = new Map(s.leagues.map((l) => [l.leagueId, l]));
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
      // The leaderboard tie-break. Written from the cohort's own end or the
      // deadline the member missed, never from the clock the crank ran on — so
      // an old outcome processed late cannot reorder two members who are level
      // on everything else (FR-060b). Nothing else in the snapshot would show
      // the difference: the counters are identical either way.
      if (progress.lastChanged !== derived.lastChanged) {
        fail(
          'INV-9',
          `${p.wallet} league ${leagueId}: last changed ${progress.lastChanged}, outcomes give ${derived.lastChanged}`,
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
      const comeback = deriveComeback(s.memberships, cohorts, leagues, p.wallet, leagueId);
      if (comeback.known) {
        const held = comebackOf(progress);
        // A pledge `release_comeback` let go leaves no membership behind, so a
        // cleared record is lawful exactly when the derivation still has it open
        // and its last round can no longer make it good.
        const released =
          isOpen(comeback.record) &&
          sameRecord(held, NO_COMEBACK) &&
          releasable(leagues.get(leagueId)!, cohorts.values(), comeback.record, s.now);
        if (!sameRecord(held, comeback.record) && !released) {
          fail(
            'INV-9',
            `${p.wallet} league ${leagueId}: comeback ${JSON.stringify(held, bigintJson)}, counted outcomes give ${JSON.stringify(comeback.record, bigintJson)}`,
          );
        }
      }
    }
  }
};

/**
 * INV-2 · a comeback entitlement is earned, not written: every membership's
 * `comeback_entitled` is what the member's counted history in that league
 * derives (FR-037e), and zero for anyone who has not finished a comeback cohort.
 * Checked per wallet and league from the memberships alone, so it holds for a
 * test league whose profile slot is shared and left out of the snapshot.
 */
const checkEntitlements = (s: Snapshot, cohorts: Map<Address, CohortSnapshot>): void => {
  const leagues = new Map(s.leagues.map((l) => [l.leagueId, l]));
  const ambiguous = walletsInSeveralTestLeagues(s, cohorts);
  const seen = new Set<string>();
  for (const m of s.memberships) {
    const c = cohorts.get(m.cohort);
    if (!c) continue;
    // Every test league shares one pledge slot, so which of several leagues a
    // pledge ended up in depends on the crank's order: nothing to derive.
    if (ambiguous.has(m.wallet)) continue;
    const key = `${m.wallet}/${c.leagueId}`;
    if (seen.has(key)) continue;
    seen.add(key);
    const derived = deriveComeback(s.memberships, cohorts, leagues, m.wallet, c.leagueId);
    if (!derived.known) continue;
    const { entitled } = derived;
    for (const x of s.memberships) {
      if (x.wallet !== m.wallet || cohorts.get(x.cohort)?.leagueId !== c.leagueId) continue;
      const want = entitled.get(x.address) ?? 0n;
      if (x.comebackEntitled !== want) {
        fail(
          'INV-2',
          `${x.wallet} in ${x.cohort}: comeback entitled ${x.comebackEntitled}, its counted history in league ${c.leagueId} gives ${want}`,
        );
      }
    }
  }
};

/** JSON.stringify's replacer for a record holding bigints. */
const bigintJson = (_k: string, v: unknown): unknown => (typeof v === 'bigint' ? v.toString() : v);

/** Wallets with memberships in more than one test league (id ≥ 100). */
const walletsInSeveralTestLeagues = (s: Snapshot, cohorts: Map<Address, CohortSnapshot>): Set<Address> => {
  const leaguesOf = new Map<Address, Set<number>>();
  for (const m of s.memberships) {
    const id = cohorts.get(m.cohort)?.leagueId;
    if (id === undefined || id < 100) continue;
    leaguesOf.set(m.wallet, (leaguesOf.get(m.wallet) ?? new Set()).add(id));
  }
  return new Set([...leaguesOf].filter(([, ids]) => ids.size > 1).map(([wallet]) => wallet));
};

/**
 * INV-1 and INV-10 for the leagues · a boost never touches a pledge.
 *
 * A league's comeback reserve has to cover every open pledge on a profile and
 * every refund owed and not yet drawn by a cohort. The pledges are what a member
 * was promised when their forfeit was counted; a boost draws on the boost
 * reserve alone, so however the two reserves are drawn, this floor holds.
 * It is a floor and not an equality because a scenario may inject more reserve
 * than the pledges explain.
 */
const checkPledges = (s: Snapshot): void => {
  for (const l of s.leagues) {
    let pledged = 0n;
    for (const p of s.profiles) {
      for (const progress of Object.values(p.leagues)) {
        if (progress.comebackStake > 0n && progress.comebackLeague === l.leagueId) {
          pledged += comebackPledge(progress.comebackStake);
        }
      }
    }
    let owedNotDrawn = 0n;
    for (const c of s.cohorts) {
      if (c.leagueId === l.leagueId && c.comebackOwed > c.comebackFunded) owedNotDrawn += c.comebackOwed - c.comebackFunded;
    }
    if (l.comebackReserve < pledged + owedNotDrawn) {
      fail(
        'INV-10',
        `league ${l.leagueId}: comeback reserve ${l.comebackReserve} is short of the ${pledged} pledged to open comebacks plus the ${owedNotDrawn} owed and not drawn`,
      );
    }
  }
};

/**
 * INV-10 · what moves a league's two reserves, and nothing else does.
 *
 * Four things move them, each by an amount fixed by the memberships and the
 * cohort alone:
 *
 *  - `settle_count` of a forfeit takes its pledge out of the cohort's vault
 *    into the reserve vault, booked to the comeback reserve as it opens the
 *    member's comeback; the member's older pledge in the league, if any, is
 *    released to the boost reserve;
 *  - `settle_count` of a finish inside the comeback's rounds makes
 *    `min(pledge, stake)` owed (it stays in the comeback reserve until drawn)
 *    and releases the rest, and a finish after the rounds releases the lot;
 *  - `release_comeback` moves a pledge whose last round is over to the boost
 *    reserve, and is recognised by a profile's open record vanishing with no
 *    count to explain it;
 *  - `settle_finalize` draws the boost (from the boost reserve, at most 10 % of
 *    the cohort's own pool) and the refunds owed (from the comeback reserve), or,
 *    for a cohort nobody finished, sends its pool to the boost reserve.
 *
 * The sum of the two reserves is checked whatever the profiles look like. The
 * split between them is replayed per wallet from the previous snapshot's
 * records, and is skipped (never loosened) for a league where a wallet's record
 * is not in the snapshot — the test leagues' shared slot, left out when a wallet
 * sits in several of them.
 */
const checkReserves = (
  prev: Snapshot,
  next: Snapshot,
  prevLeagues: Map<number, LeagueSnapshot>,
  newlyCounted: MembershipSnapshot[],
  pledgesCounted: Map<Address, bigint>,
  finalizedIn: Map<number, Finalized[]>,
): void => {
  const nextCohorts = new Map(next.cohorts.map((c) => [c.address, c]));
  for (const l of next.leagues) {
    const lb = prevLeagues.get(l.leagueId);
    if (!lb) {
      if (l.boostReserve !== 0n || l.comebackReserve !== 0n) {
        fail('INV-10', `league ${l.leagueId} appeared holding ${l.boostReserve} boost and ${l.comebackReserve} comeback reserve`);
      }
      continue;
    }
    const cohortsOfLeague = next.cohorts.filter((c) => c.leagueId === l.leagueId).sort((a, b) => a.index - b.index);
    const finalized = (finalizedIn.get(l.leagueId) ?? []).sort((a, b) => a.cohort.index - b.cohort.index);
    const counted = newlyCounted.filter((m) => nextCohorts.get(m.cohort)?.leagueId === l.leagueId);

    // The sum: every unit that crossed the reserve vault this step.
    let sum = lb.boostReserve + lb.comebackReserve;
    for (const c of cohortsOfLeague) sum += pledgesCounted.get(c.address) ?? 0n;
    for (const f of finalized) {
      sum +=
        f.cohort.totalFinisherStake > 0n
          ? -(f.cohort.boostReceived + f.cohort.comebackFunded)
          : f.cohort.noFinisherBoost;
    }
    if (l.boostReserve + l.comebackReserve !== sum) {
      fail(
        'INV-10',
        `league ${l.leagueId}: reserves ${lb.boostReserve}/${lb.comebackReserve} → ${l.boostReserve}/${l.comebackReserve}, but the pledges counted, the pools sent and the draws of this step make a total of ${sum}`,
      );
    }

    // The split, replayed from the profiles' records.
    let exact = true;
    let cr = lb.comebackReserve;
    let br = lb.boostReserve;
    const records = new Map<Address, ComebackRecord>();
    const prevRecord = (wallet: Address): ComebackRecord | undefined => {
      const held = records.get(wallet);
      if (held) return held;
      const progress = prev.profiles.find((p) => p.wallet === wallet)?.leagues[l.leagueId];
      return progress ? comebackOf(progress) : undefined;
    };

    // (1) counting, cohort by cohort
    for (const c of cohortsOfLeague) {
      for (const m of counted.filter((x) => x.cohort === c.address).sort((a, b) => a.wallet.localeCompare(b.wallet))) {
        const record = prevRecord(m.wallet);
        if (!record) {
          exact = false;
          continue;
        }
        const pledge = comebackPledge(m.stake);
        let entitled = 0n;
        if (m.state === 'Forfeited') {
          if (isOpen(record) && record.league !== l.leagueId) {
            // A test league's shared slot holds another league's pledge: this one is not opened.
            br += pledge;
          } else {
            if (isOpen(record)) {
              const old = comebackPledge(record.stake);
              cr -= old;
              br += old;
            }
            const [round, lastRound] = comebackRounds(lb, deadlineOf(c, m.daysCompleted));
            records.set(m.wallet, { stake: m.stake, league: l.leagueId, round, lastRound });
            cr += pledge;
          }
        } else if (isOpen(record) && record.league === l.leagueId) {
          const held = comebackPledge(record.stake);
          if (c.index >= record.round && c.index <= record.lastRound) {
            entitled = comebackOwedFor(record.stake, m.stake);
            cr -= held - entitled;
            br += held - entitled;
            records.set(m.wallet, NO_COMEBACK);
          } else if (c.index > record.lastRound) {
            cr -= held;
            br += held;
            records.set(m.wallet, NO_COMEBACK);
          }
        }
        if (m.state !== 'Forfeited' && m.comebackEntitled !== entitled) {
          fail(
            'INV-2',
            `${m.wallet} in ${m.cohort}: counted with a comeback entitlement of ${m.comebackEntitled}, its comeback gives ${entitled}`,
          );
        }
      }
    }

    // (2) releases: an open record gone with no count to close it
    for (const p of next.profiles) {
      const progress = p.leagues[l.leagueId];
      if (!progress) continue;
      const held = prevRecord(p.wallet) ?? (prev.profiles.some((q) => q.wallet === p.wallet) ? undefined : NO_COMEBACK);
      if (!held) {
        exact = false;
        continue;
      }
      const now = comebackOf(progress);
      if (sameRecord(now, held)) continue;
      if (isOpen(held) && sameRecord(now, NO_COMEBACK)) {
        if (!releasable(l, next.cohorts, held, next.now)) {
          fail(
            'INV-10',
            `${p.wallet} league ${l.leagueId}: comeback of ${held.stake} (rounds ${held.round}–${held.lastRound}) released while its last round can still make it good`,
          );
        }
        const pledge = comebackPledge(held.stake);
        cr -= pledge;
        br += pledge;
        records.set(p.wallet, NO_COMEBACK);
      } else {
        fail(
          'INV-9',
          `${p.wallet} league ${l.leagueId}: comeback ${JSON.stringify(held, bigintJson)} → ${JSON.stringify(now, bigintJson)}, which neither a count nor a release writes`,
        );
      }
    }

    // (3) finalization, cohort by cohort: the boost from the boost reserve, the
    // refunds from the comeback reserve, or the whole pool into the boost reserve.
    for (const { cohort: c, ownPool } of finalized) {
      if (c.totalFinisherStake > 0n) {
        if (exact) {
          const boost = boostFor(ownPool, br);
          if (c.boostReceived !== boost) {
            fail(
              'INV-2',
              `cohort ${c.address}: boost ${c.boostReceived}, but min(10% of ${ownPool}, boost reserve ${br}) is ${boost}`,
            );
          }
          const funded = c.comebackOwed < cr ? c.comebackOwed : cr;
          if (c.comebackFunded !== funded) {
            fail(
              'INV-2',
              `cohort ${c.address}: comeback funded ${c.comebackFunded}, but the ${c.comebackOwed} owed and the comeback reserve of ${cr} give ${funded}`,
            );
          }
        }
        br -= c.boostReceived;
        cr -= c.comebackFunded;
      } else {
        br += c.noFinisherBoost;
      }
    }

    if (exact) {
      if (l.boostReserve !== br || l.comebackReserve !== cr) {
        fail(
          'INV-10',
          `league ${l.leagueId}: reserves ${lb.boostReserve}/${lb.comebackReserve} → ${l.boostReserve}/${l.comebackReserve}, the counts, releases and finalizations of this step give ${br}/${cr}`,
        );
      }
    } else if (counted.length === 0 && finalized.length === 0) {
      if (l.boostReserve !== lb.boostReserve || l.comebackReserve !== lb.comebackReserve) {
        fail(
          'INV-10',
          `league ${l.leagueId}: reserves ${lb.boostReserve}/${lb.comebackReserve} → ${l.boostReserve}/${l.comebackReserve} with no count or finalization to move them`,
        );
      }
    }
  }
};

/** A cohort that finalized in the step being checked, with the pool it raised on its own. */
interface Finalized {
  cohort: CohortSnapshot;
  ownPool: bigint;
}

/** INV-2, INV-5, INV-6, INV-8, INV-10 — each about a change, so each needs both snapshots. */
const checkDeltas = (prev: Snapshot, next: Snapshot): void => {
  const before = byAddress(prev.cohorts);
  const prevMemberships = byAddress(prev.memberships);
  const prevCohorts = new Map(prev.cohorts.map((c) => [c.address, c]));
  const nextCohorts = new Map(next.cohorts.map((c) => [c.address, c]));
  const prevLeagues = new Map(prev.leagues.map((l) => [l.leagueId, l]));
  const finalizedIn = new Map<number, Finalized[]>();
  let operationsOwed = 0n;
  let developerOwed = 0n;

  // A membership's terms are written once, by `join_cohort`, and every figure
  // INV-1 and INV-2 balance is derived from them: the stake is what custody
  // holds and what a forfeit hands the pool, the tier is what picks the share.
  // So they are checked before they are used, and used from the *previous*
  // snapshot below — a `claim_payout` that underpaid its member could
  // otherwise rewrite the two numbers it is judged by and come out exact.
  for (const m of next.memberships) {
    const was = prevMemberships.get(m.address);
    if (!was) continue;
    if (m.cohort !== was.cohort || m.wallet !== was.wallet) {
      fail(
        'INV-2',
        `membership ${m.address} changed hands: ${was.wallet} in ${was.cohort} → ${m.wallet} in ${m.cohort}`,
      );
    }
    if (m.tier !== was.tier || m.stake !== was.stake) {
      fail(
        'INV-2',
        `membership ${m.address}: tier ${was.tier} staking ${was.stake} → tier ${m.tier} staking ${m.stake}`,
      );
    }
    // The entitlement is written by the count, once; the claim reads it, and a
    // claim that could rewrite it would set its own refund.
    if (m.comebackEntitled !== was.comebackEntitled && (was.counted || !m.counted)) {
      fail(
        'INV-2',
        `membership ${m.address}: comeback entitlement ${was.comebackEntitled} → ${m.comebackEntitled} outside its count`,
      );
    }
  }

  // The memberships `settle_count` counted this step, and what each forfeit's
  // pledge took out of its cohort's vault on the way to the league's reserve.
  const newlyCounted = next.memberships.filter((m) => {
    const was = prevMemberships.get(m.address);
    return m.counted && was !== undefined && !was.counted;
  });
  const pledgesCounted = new Map<Address, bigint>();
  for (const m of newlyCounted) {
    if (m.state !== 'Forfeited') continue;
    pledgesCounted.set(m.cohort, (pledgesCounted.get(m.cohort) ?? 0n) + comebackPledge(m.stake));
  }

  for (const c of next.cohorts) {
    const b = before.get(c.address);
    if (!b) continue;

    // The forfeits this step recorded, split as FR-027 splits them.
    const forfeitsAdded = next.memberships.flatMap((m) => {
      const was = prevMemberships.get(m.address);
      return m.cohort === c.address && m.state === 'Forfeited' && was?.state === 'Joined' ? [was] : [];
    });
    const split = forfeitsAdded.reduce(
      (sum, was) => {
        const part = forfeitSplit(was.stake);
        return {
          pool: sum.pool + part.pool,
          comeback: sum.comeback + part.comeback,
          developer: sum.developer + part.developer,
          operations: sum.operations + part.operations,
        };
      },
      { pool: 0n, comeback: 0n, developer: 0n, operations: 0n },
    );
    operationsOwed += split.operations;
    developerOwed += split.developer;

    // The claims this step settled — the only thing that may move a settled
    // cohort's balances, and the yardstick both INV-6 and INV-2 measure against.
    const claims = next.memberships.flatMap((m) => {
      const was = prevMemberships.get(m.address);
      return m.cohort === c.address && m.state === 'Claimed' && was?.state === 'Finished' ? [was] : [];
    });
    // INV-6 · `claim_payout` answers only for a settled cohort. Between
    // `settle_count` and `settle_finalize` a membership is already `Finished`
    // and the shares are not written yet, so a claim in that window pays out of
    // a pool that is still being assembled — and every figure below would
    // accept it, because it only ever asks whether the deltas agree with each
    // other. The cohort has to have been settled before the step, not during
    // it: finalization and a claim in one snapshot would also break the exact
    // post-finalize pool this checker pins.
    if (claims.length > 0 && !b.settled) {
      fail(
        'INV-6',
        `cohort ${c.address}: ${claims.length} claim(s) paid while it was ${statusOf(b, prev.now)}, not settled`,
      );
    }
    const claimsPaid = claims.reduce((sum, was) => sum + (c.sharePerTier[was.tier] ?? 0n), 0n);
    const stakesReturned = claims.reduce((sum, was) => sum + was.stake, 0n);
    const refundsPaid = claims.reduce(
      (sum, was) => sum + comebackRefund(was.comebackEntitled, c.comebackFunded, c.comebackOwed),
      0n,
    );

    if (b.settled) {
      // INV-5 · the denominator and the shares are written exactly once.
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

      // INV-6 · after settling, a claim is the only thing that moves anything,
      // and it moves every side by a known amount: the member's stake out of
      // held, their share out of the pool, their refund out of what was funded.
      // Pinning the deltas rather than only refusing increases is the point — a
      // balance walked *down*, with the vault lowered to match, satisfies INV-1
      // and leaves no membership to account for where the units went.
      if (b.pool - c.pool !== claimsPaid) {
        fail(
          'INV-6',
          `cohort ${c.address}: pool ${b.pool} → ${c.pool} after settling, but this step's claims pay ${claimsPaid}`,
        );
      }
      if (b.heldStake - c.heldStake !== stakesReturned) {
        fail(
          'INV-6',
          `cohort ${c.address}: held stake ${b.heldStake} → ${c.heldStake} after settling, but this step's claims return ${stakesReturned}`,
        );
      }
      if (c.comebackClaimed - b.comebackClaimed !== refundsPaid) {
        fail(
          'INV-6',
          `cohort ${c.address}: comeback claimed ${b.comebackClaimed} → ${c.comebackClaimed}, but this step's claims refund ${refundsPaid}`,
        );
      }
      if (c.memberCount !== b.memberCount) {
        fail('INV-6', `cohort ${c.address}: member count changed after settling`);
      }
      if (
        c.comebackDue !== b.comebackDue ||
        c.boostReceived !== b.boostReceived ||
        c.comebackOwed !== b.comebackOwed ||
        c.comebackFunded !== b.comebackFunded
      ) {
        fail('INV-6', `cohort ${c.address}: its reserve figures changed after settling`);
      }
      if (c.noFinisherBoost !== b.noFinisherBoost) {
        fail('INV-6', `cohort ${c.address}: the no-finisher boost changed after settling`);
      }
      if (
        c.finisherCount !== b.finisherCount ||
        c.finishersPerTier.some((n, i) => n !== b.finishersPerTier[i])
      ) {
        fail('INV-6', `cohort ${c.address}: the finisher tally changed after settling`);
      }
      if (c.counted !== b.counted || c.forfeitsRecorded !== b.forfeitsRecorded) {
        fail('INV-6', `cohort ${c.address}: the count changed after settling`);
      }
      continue;
    }

    // The pool this cohort raised on its own: what it had, plus this step's
    // forfeits' pool parts (INV-2). Its pledges still in the vault likewise: the
    // forfeits' 10 % arrives with the record, and leaves with the count.
    const ownPool = b.pool + split.pool;
    const comebackDue = b.comebackDue + split.comeback - (pledgesCounted.get(c.address) ?? 0n);

    if (!c.settled) {
      // INV-2 · only recorded forfeits' pool parts raise a pool before it settles.
      if (c.pool !== ownPool) {
        fail(
          'INV-2',
          `cohort ${c.address}: pool ${b.pool} → ${c.pool}, but this step's forfeits pool +${split.pool}`,
        );
      }
      // INV-10 · the pledges wait in the vault until their forfeit is counted: a
      // due that rises by less than its forfeits' share has been sent somewhere
      // before the count, and one that falls by more than the counted forfeits'
      // pledges has paid a pledge to somebody other than the league's reserve.
      if (c.comebackDue !== comebackDue) {
        fail(
          'INV-10',
          `cohort ${c.address}: comeback due ${b.comebackDue} → ${c.comebackDue}, but this step's forfeits pledge +${split.comeback} and its counted forfeits move -${pledgesCounted.get(c.address) ?? 0n} to the league`,
        );
      }
      if (
        c.boostReceived !== b.boostReceived ||
        c.comebackFunded !== b.comebackFunded ||
        c.comebackClaimed !== b.comebackClaimed ||
        c.noFinisherBoost !== b.noFinisherBoost
      ) {
        fail('INV-2', `cohort ${c.address}: settlement figures moved before it settled`);
      }
      continue;
    }

    // ---- finalized in this step --------------------------------------------
    // INV-5 · the denominator is written once, and only once it is complete.
    // A finalize that ran early strands every uncounted member in a settled
    // cohort for good, and the allocation it wrote can still balance on its
    // own — an empty pool does, and so do shares for the subset counted so
    // far — so the money checks below would pass it through.
    if (b.counted !== b.memberCount) {
      fail('INV-5', `cohort ${c.address}: settled with ${b.counted} of ${b.memberCount} members counted`);
    }
    if (c.totalFinisherStake > 0n) {
      // FR-037d · no boost beyond 10 % of the cohort's own pool; a cohort with
      // no forfeits has no pool and gets none. The exact figure — also bounded
      // by the boost reserve at that instant — is judged with the league below.
      const cap = (ownPool * 10n) / 100n;
      if (c.boostReceived > cap) {
        fail('INV-2', `cohort ${c.address}: boost ${c.boostReceived} exceeds 10% of its own pool ${ownPool}`);
      }
      // Finalization is the one place a pool moves without a claim, and it
      // moves to a figure the cohort has just written down for itself: the
      // shares it owes its finishers. "It only fell" is not enough — a finalize
      // that drained the claimable shares and lowered the vault with them would
      // satisfy INV-1, leave every membership `Finished`, and be discovered only
      // when the first claim failed.
      const available = ownPool + c.boostReceived;
      const owed = c.finishersPerTier.reduce(
        (sum, count, tier) => sum + BigInt(count) * (c.sharePerTier[tier] ?? 0n),
        0n,
      );
      if (c.pool !== owed) {
        fail('INV-2', `cohort ${c.address}: finalized to a pool of ${c.pool}, but its own shares owe ${owed}`);
      }
      if (available !== c.pool + c.roundingRemainder) {
        fail(
          'INV-2',
          `cohort ${c.address}: finalized ${ownPool} + boost ${c.boostReceived} into ${c.pool} owed + ${c.roundingRemainder} remainder`,
        );
      }
      // FR-037e · the refunds are drawn in full: every one owed was pledged.
      if (c.comebackFunded > c.comebackOwed) {
        fail('INV-2', `cohort ${c.address}: comeback funded ${c.comebackFunded}, only ${c.comebackOwed} owed`);
      }
      operationsOwed += c.roundingRemainder;
    } else {
      // FR-037 · nobody finished: the whole pool goes to the league's boost
      // reserve. The developer and operations took their parts with each forfeit.
      if (c.pool !== 0n) {
        fail('INV-2', `cohort ${c.address}: nobody finished, yet ${c.pool} stayed in the pool`);
      }
      if (c.noFinisherBoost !== ownPool) {
        fail('INV-2', `cohort ${c.address}: ${ownPool} to send to the boost reserve, but the no-finisher boost records ${c.noFinisherBoost}`);
      }
      if (c.boostReceived !== 0n || c.comebackFunded !== 0n) {
        fail('INV-2', `cohort ${c.address}: nobody finished, yet it drew ${c.boostReceived} boost and ${c.comebackFunded} comeback`);
      }
    }
    if (c.comebackClaimed !== b.comebackClaimed) {
      fail('INV-6', `cohort ${c.address}: comeback refunds paid while it finalized`);
    }
    // FR-037c · every pledge has left the vault: all its forfeits are counted.
    if (c.comebackDue !== 0n) {
      fail('INV-10', `cohort ${c.address}: finalized with ${c.comebackDue} of comeback pledges still in its vault`);
    }
    if (c.comebackDue !== comebackDue) {
      fail(
        'INV-10',
        `cohort ${c.address}: comeback due ${b.comebackDue} → ${c.comebackDue}, but this step's forfeits pledge +${split.comeback} and its counted forfeits move -${pledgesCounted.get(c.address) ?? 0n} to the league`,
      );
    }
    finalizedIn.set(c.leagueId, [...(finalizedIn.get(c.leagueId) ?? []), { cohort: c, ownPool }]);
  }

  checkReserves(prev, next, prevLeagues, newlyCounted, pledgesCounted, finalizedIn);

  // INV-2 · the fee is the part of a stake that reaches neither the pool nor a
  // pledge, and it lands in two accounts: the developer's 10 % and the
  // operations 10 % plus rounding, both at once when the forfeit is recorded, and
  // the finalization's rounding remainder to operations. Until those accounts
  // are read the snapshot cannot tell a fee that reached its destination from
  // one that reached somebody else's token account: the vault falls by the same
  // amount either way, so custody and the pool both still balance. Each side is
  // skipped when either snapshot has no such destination configured.
  if (prev.operationsDestination !== undefined && next.operationsDestination !== undefined) {
    const paid = next.operationsDestination - prev.operationsDestination;
    if (paid !== operationsOwed) {
      fail('INV-2', `the operations destination took ${paid}, but this step's forfeits and settlements owe it ${operationsOwed}`);
    }
  }
  if (prev.developerDestination !== undefined && next.developerDestination !== undefined) {
    const paid = next.developerDestination - prev.developerDestination;
    if (paid !== developerOwed) {
      fail('INV-2', `the developer destination took ${paid}, but this step's forfeits and settlements owe it ${developerOwed}`);
    }
  }

  // INV-2 · the member's own side of every transfer.
  //
  // Everything above reconciles the program's accounts against each other, and
  // a `claim_payout` that sent the exact amount to somebody else's token
  // account satisfies all of it: the vault falls, the pool and the held stake
  // fall with it, the membership reads `Claimed`. The only thing that tells the
  // two apart is the wallet that was supposed to be paid. Every flow that moves
  // SKR between a member and the program is accounted for here, so a step that
  // joins, leaves, claims and takes a faucet claim at once still adds up.
  if (prev.walletBalances && next.walletBalances) {
    const nextMemberships = byAddress(next.memberships);
    const owed = new Map<Address, bigint>();
    const owe = (wallet: Address, amount: bigint) =>
      owed.set(wallet, (owed.get(wallet) ?? 0n) + amount);

    for (const m of next.memberships) {
      const was = prevMemberships.get(m.address);
      const c = nextCohorts.get(m.cohort);
      // A membership that was not there before is a join: the stake leaves.
      if (!was) {
        owe(m.wallet, -m.stake);
        continue;
      }
      if (m.state === 'Claimed' && was.state === 'Finished') {
        const refund = c ? comebackRefund(was.comebackEntitled, c.comebackFunded, c.comebackOwed) : 0n;
        owe(m.wallet, was.stake + (c?.sharePerTier[was.tier] ?? 0n) + refund);
      }
    }
    // A membership that has gone is a `leave_cohort`, the one path that closes
    // the account, and it refunds the stake in full (FR-009).
    for (const was of prev.memberships) {
      if (!nextMemberships.has(was.address)) owe(was.wallet, was.stake);
    }
    // A faucet claim mints the largest top tier ever published, and says so by
    // moving the day it stamps on the profile (FR-042a, R13).
    if (next.faucetMint !== undefined) {
      const claimedBefore = new Map(prev.profiles.map((p) => [p.wallet, p.lastFaucetDay]));
      for (const p of next.profiles) {
        const before = claimedBefore.get(p.wallet);
        if (before !== undefined && p.lastFaucetDay > before) owe(p.wallet, next.faucetMint);
      }
    }

    for (const [wallet, expected] of owed) {
      // A wallet enters `walletBalances` when it first has a `Profile`, so on a
      // member's very first join it goes from untracked to tracked in one step
      // and its prior balance is *unknown*, not nil — the same distinction the
      // snapshot draws for a mint that is not configured yet. Reading the
      // absence as a zero would report every SKR the member already held as an
      // unexplained gain, which is the normal case: R13 has them claim the
      // faucet before they can stake at all. Nothing is checkable for that one
      // step, so it is skipped by name rather than papered over with a zero;
      // every later step for the same wallet is checked in full, and that is
      // where a payout or a fee sent to the wrong token account would show.
      const before = prev.walletBalances[wallet];
      if (before === undefined) continue;

      const paid = (next.walletBalances[wallet] ?? 0n) - before;
      if (paid !== expected) {
        fail(
          'INV-2',
          `${wallet}'s balance moved by ${paid}, but this step's joins, leaves, claims and faucet come to ${expected}`,
        );
      }
    }
  }

  // INV-8 · a member already in breach cannot complete another day.
  //
  // The breach is read at the *post-step* clock against the membership as it
  // was. Reading it at `prev.now` is what a broken program would want: a step
  // that advances the clock past a deadline and only then submits leaves
  // `prev.now` inside the valid window, so the stale submission passes here —
  // and INV-3 passes too, because the day the clock moved into has raised its
  // own ceiling. The cost of the stricter reading is a rule for scenarios: take
  // the snapshot straight after the transaction, never after advancing the
  // clock again, or a lawful submission is reported late.
  for (const m of next.memberships) {
    const was = prevMemberships.get(m.address);
    const c = prevCohorts.get(m.cohort);
    if (!was || !c) continue;
    if (m.daysCompleted > was.daysCompleted && inBreach(was, c, next.now)) {
      fail(
        'INV-8',
        `${m.wallet} in ${m.cohort} submitted while in breach at ${next.now} (${was.daysCompleted} → ${m.daysCompleted}, day ${was.daysCompleted} was due by ${deadlineOf(c, was.daysCompleted)})`,
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
  checkPledges(current);
  checkDays(current, cohorts);
  checkSlots(current, cohorts);
  checkTallies(current);
  checkStreaks(current, cohorts);
  checkEntitlements(current, cohorts);
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
 * order must leave identical profiles — streaks and comeback records alike.
 */
export const assertSameProgress = (a: Snapshot, b: Snapshot): void => {
  const key = (s: Snapshot) =>
    JSON.stringify(
      [...s.profiles]
        .sort((x, y) => x.wallet.localeCompare(y.wallet))
        .map((p) => [p.wallet, p.leagues]),
      (_k, v) => (typeof v === 'bigint' ? v.toString() : v),
    );
  if (key(a) !== key(b)) fail('INV-9', 'two processing orders produced different progress');
};
