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
  /** The fee half of a no-finisher split — the other half is the carry-over. */
  noFinisherFee: bigint;
  /** The send side of a carry-over: how much left, and for where. */
  carryOverSent: bigint;
  carryOverTarget: Address | null;
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
  /** The lobby cohort's own end, carried so the promotion can hand it over. */
  pendingEnd: number;
  /** Global day index of the last faucet claim; -1 = never (R13). */
  lastFaucetDay: number;
}

export interface Snapshot {
  now: number;
  cohorts: CohortSnapshot[];
  memberships: MembershipSnapshot[];
  profiles: ProfileSnapshot[];
  /** SKR held by the fee destination, for the delta checks. */
  feeDestination?: bigint;
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
  const slotsOf = new Map(s.profiles.map((p) => [p.wallet, p]));
  for (const m of s.memberships) {
    const c = cohorts.get(m.cohort);
    if (!c || m.state !== 'Joined' || statusOf(c, s.now) === 'Settled') continue;
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
 * `settle_count` writes `finisher_count`, `finishers_per_tier` and
 * `total_finisher_stake` page by page, and `settle_finalize` divides by the
 * last of them. Taking those three at face value leaves the allocation
 * self-consistent no matter what they say: an overstated denominator produces
 * smaller shares and a larger remainder that still add back to the pool, so the
 * money checks pass while the difference has been handed to the fee
 * destination — and the first member to claim at their real tier finds the
 * cohort short. Derived here instead, from the memberships that carry
 * `counted`, so the figures are checked at every stage of the count rather than
 * only at the end.
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
    }
  }
};

/** One carry-over that moved in a step: how much, and out of which cohort. */
interface CarryMove {
  from: CohortSnapshot;
  amount: bigint;
}

/**
 * Every carry-over that moved in this step, keyed by the cohort it arrived at.
 *
 * A target's own `carry_over_received` is the cohort's word for where its new
 * pool came from, and taking that word is how a settlement that drains an
 * unrelated cohort — even one in another league — slips past INV-1 and INV-2
 * with the bookkeeping field incremented to cover it. So the send side is
 * reconstructed here and the two are made to agree. A no-finisher finalize
 * raises the source's `carry_over_sent`; the Phase 2 delivery instead empties
 * its `carry_over_held`. Both name the destination in `carry_over_target`,
 * written by the same instruction, so `next` is where that is read.
 */
const carryMovesByTarget = (
  next: Snapshot,
  before: Map<Address, CohortSnapshot>,
): Map<Address, CarryMove[]> => {
  const moves = new Map<Address, CarryMove[]>();
  for (const from of next.cohorts) {
    const b = before.get(from.address);
    if (!b) continue;
    const sent = from.carryOverSent - b.carryOverSent;
    if (sent < 0n) {
      fail('INV-2', `cohort ${from.address}: carry-over sent fell by ${-sent}`);
    }
    const released = b.carryOverHeld - from.carryOverHeld;
    const amount = sent + (released > 0n ? released : 0n);
    if (amount === 0n) continue;
    const target =
      from.carryOverTarget ??
      fail('INV-2', `cohort ${from.address} moved ${amount} of carry-over naming no target`);
    moves.set(target, [...(moves.get(target) ?? []), { from, amount }]);
  }
  return moves;
};

/** INV-2, INV-5, INV-6, INV-8 — each about a change, so each needs both snapshots. */
const checkDeltas = (prev: Snapshot, next: Snapshot): void => {
  const before = byAddress(prev.cohorts);
  const prevMemberships = byAddress(prev.memberships);
  const prevCohorts = new Map(prev.cohorts.map((c) => [c.address, c]));
  const nextCohorts = new Map(next.cohorts.map((c) => [c.address, c]));
  const moves = carryMovesByTarget(next, before);
  const inSnapshot = new Set(next.cohorts.map((c) => c.address));

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
  }

  for (const [target, sent] of moves) {
    if (!inSnapshot.has(target)) {
      const total = sent.reduce((sum, m) => sum + m.amount, 0n);
      fail('INV-2', `${total} of carry-over went to ${target}, which is not in the snapshot`);
    }
  }

  for (const c of next.cohorts) {
    // Both sides of every carry-over, correlated. A cohort opened in this same
    // step has no `before`, so its received figure is measured from zero.
    const b = before.get(c.address);
    const carryArrived = c.carryOverReceived - (b?.carryOverReceived ?? 0n);
    if (carryArrived < 0n) {
      fail('INV-2', `cohort ${c.address}: carry-over received fell by ${-carryArrived}`);
    }
    const arrivals = moves.get(c.address) ?? [];
    const carrySent = arrivals.reduce((sum, m) => sum + m.amount, 0n);
    if (carryArrived !== carrySent) {
      fail(
        'INV-2',
        `cohort ${c.address}: carry-over received +${carryArrived}, but ${carrySent} was sent to it`,
      );
    }
    for (const move of arrivals) {
      if (move.from.address === c.address) {
        fail('INV-2', `cohort ${c.address} carried ${move.amount} over into itself`);
      }
      if (move.from.leagueId !== c.leagueId) {
        fail(
          'INV-2',
          `cohort ${c.address} (league ${c.leagueId}) took ${move.amount} from ${move.from.address} (league ${move.from.leagueId})`,
        );
      }
      if (move.from.totalFinisherStake !== 0n) {
        fail(
          'INV-2',
          `cohort ${move.from.address} carried ${move.amount} over although its finishers are owed it`,
        );
      }
      // FR-037a puts the carry where members can still reach it. A locked or
      // running cohort takes the money and nobody can join to win it back, and
      // the amount and the league both look right, so nothing else objects.
      // Read at the post-step clock, on the same footing as INV-8 below.
      const status = statusOf(c, next.now);
      if (status !== 'Open') {
        fail(
          'INV-2',
          `cohort ${c.address} took ${move.amount} of carry-over while ${status}, not Open (FR-037a)`,
        );
      }
    }

    if (!b) continue;

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

      // INV-6 · after settling, a claim is the only thing that moves anything,
      // and it moves both sides by a known amount: the member's stake out of
      // held, their share out of the pool. Pinning the deltas rather than only
      // refusing increases is the point — a balance walked *down*, with the
      // vault lowered to match, satisfies INV-1 and leaves no membership to
      // account for where the units went.
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
      if (c.memberCount !== b.memberCount) {
        fail('INV-6', `cohort ${c.address}: member count changed after settling`);
      }
      if (c.carryOverReceived !== b.carryOverReceived) {
        fail('INV-6', `cohort ${c.address}: took carry-over after settling`);
      }
      if (c.carryOverHeld > b.carryOverHeld) {
        fail('INV-6', `cohort ${c.address}: held carry-over grew after settling`);
      }
      if (c.noFinisherFee !== b.noFinisherFee) {
        fail('INV-6', `cohort ${c.address}: the no-finisher fee changed after settling`);
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
    }

    // INV-2 · only recorded forfeits, carry-over arrival and claims touch a pool.
    const forfeitsAdded = next.memberships.flatMap((m) => {
      const was = prevMemberships.get(m.address);
      return m.cohort === c.address && m.state === 'Forfeited' && was?.state === 'Joined' ? [was] : [];
    });
    const fromForfeits = forfeitsAdded.reduce((sum, was) => {
      const fee = (was.stake * 20n) / 100n;
      return sum + (was.stake - fee);
    }, 0n);

    const available = b.pool + fromForfeits + carryArrived;
    if (c.settled && !b.settled) {
      // INV-5 · the denominator is written once, and only once it is complete.
      // A finalize that ran early strands every uncounted member in a settled
      // cohort for good, and the allocation it wrote can still balance on its
      // own — an empty pool does, and so do shares for the subset counted so
      // far — so the money checks below would pass it through.
      if (b.counted !== b.memberCount) {
        fail(
          'INV-5',
          `cohort ${c.address}: settled with ${b.counted} of ${b.memberCount} members counted`,
        );
      }
      // Finalization is the one place a pool moves without a claim, and it moves
      // to a figure the cohort has just written down for itself: the shares it
      // owes its finishers, or nothing at all when nobody finished. "It only
      // fell" is not enough — a finalize that drained the claimable shares and
      // lowered the vault with them would satisfy INV-1, leave every membership
      // `Finished`, and be discovered only when the first claim failed.
      if (c.totalFinisherStake > 0n) {
        const owed = c.finishersPerTier.reduce(
          (sum, count, tier) => sum + BigInt(count) * (c.sharePerTier[tier] ?? 0n),
          0n,
        );
        if (c.pool !== owed) {
          fail(
            'INV-2',
            `cohort ${c.address}: finalized to a pool of ${c.pool}, but its own shares owe ${owed}`,
          );
        }
        if (available !== c.pool + c.roundingRemainder) {
          fail(
            'INV-2',
            `cohort ${c.address}: finalized ${available} into ${c.pool} owed + ${c.roundingRemainder} remainder`,
          );
        }
      } else {
        if (c.pool !== 0n) {
          fail('INV-2', `cohort ${c.address}: nobody finished, yet ${c.pool} stayed in the pool`);
        }
        const split =
          c.noFinisherFee +
          (c.carryOverSent - b.carryOverSent) +
          (c.carryOverHeld - b.carryOverHeld);
        if (split !== available) {
          fail(
            'INV-2',
            `cohort ${c.address}: ${available} to split, but fee ${c.noFinisherFee} and the carry-over account for ${split}`,
          );
        }
      }
    } else if (c.pool !== available - claimsPaid) {
      fail(
        'INV-2',
        `cohort ${c.address}: pool ${b.pool} → ${c.pool}, but forfeits +${fromForfeits}, carry-over +${carryArrived}, claims -${claimsPaid}`,
      );
    }
  }

  // INV-2 · the fee is the part of a stake that does *not* reach the pool, and
  // the remainder is the part that leaves it at finalization. Both land in one
  // account, and until that account is read the snapshot cannot tell a fee that
  // reached the treasury from one that reached somebody else's token account:
  // the vault falls by the same amount either way, so custody and the pool both
  // still balance. Skipped when either snapshot has no destination configured.
  if (prev.feeDestination !== undefined && next.feeDestination !== undefined) {
    let owed = 0n;
    for (const c of next.cohorts) {
      const b = before.get(c.address);
      if (!b) continue;
      for (const m of next.memberships) {
        const was = prevMemberships.get(m.address);
        if (m.cohort !== c.address || m.state !== 'Forfeited' || was?.state !== 'Joined') continue;
        owed += (was.stake * 20n) / 100n;
      }
      if (c.settled && !b.settled) {
        owed += c.totalFinisherStake > 0n ? c.roundingRemainder : c.noFinisherFee;
      }
    }
    const paid = next.feeDestination - prev.feeDestination;
    if (paid !== owed) {
      fail(
        'INV-2',
        `the fee destination took ${paid}, but this step's forfeits and settlements owe it ${owed}`,
      );
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
        owe(m.wallet, was.stake + (c?.sharePerTier[was.tier] ?? 0n));
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
  checkDays(current, cohorts);
  checkSlots(current, cohorts);
  checkTallies(current);
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
