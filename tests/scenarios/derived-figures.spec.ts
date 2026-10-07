/**
 * The figures the screen shows, and the moment they move. Task T072.
 *
 * FR-038a and AC-8: a member's device computes the survivor counter, the
 * projected pool and its own projected share **from chain accounts and chain
 * time alone**. So one second after a deadline every device already excludes
 * the members who missed it — no automation has run, nothing on chain has
 * changed, and the number on the screen has already moved (constitution II).
 *
 * The property this file proves is the other half of that: **recording those
 * forfeits later changes no displayed figure**. If it did, the counter would
 * jump when the crank happened to run, and the number that sells the product
 * would be a number nobody could trust.
 *
 * At 1,000 members, because a rounding error that hides at three members is
 * what a thousand finds.
 */

import { generateKeyPairSigner, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeAll, describe, expect, it } from 'vitest';

import { inBreach, type CohortSnapshot, type MembershipSnapshot, type Snapshot } from '../invariants/checker.js';
import { DEFAULT_CHIPS, FIRST_START, SKR, createHarness, type Harness } from '../helpers/harness.js';
import { forfeitPairs, joinCohortIx, recordForfeitsIx, submitDayIx, type FeeDestinations } from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda } from '../helpers/pdas.js';

const LEAGUE = 100;
const INDEX = 0;
const DAY_LENGTH = 60;
const DURATION = 7;
const START = FIRST_START;
const MEMBERS = 1_000;
/** One page of forfeits (research R9). */
const PAGE = 10;

const feeOf = (stake: bigint) => (stake * 30n) / 100n;

// ------------------------------------------------------------ the derived figures
// One definition each, from data-model §5, over a decoded snapshot — the same
// inputs `kotlin/core`'s `Derive.kt` will have and nothing else.

/** The checker's snapshot uses plain strings for addresses; kit's are branded. */
type Key = string;

const membersOf = (s: Snapshot, cohort: Key) => s.memberships.filter((m) => m.cohort === cohort);

const isStanding = (m: MembershipSnapshot, c: CohortSnapshot, now: number): boolean =>
  m.state === 'Finished' || m.state === 'Claimed' || (m.state === 'Joined' && !inBreach(m, c, now));

/** `count(standing) / member_count_at_start` (FR-038). */
const survivors = (s: Snapshot, c: CohortSnapshot, now: number): number =>
  membersOf(s, c.address).filter((m) => isStanding(m, c, now)).length;

/** `pool + Σ over breached-but-unrecorded members of (stake − fee)`. */
const projectedPool = (s: Snapshot, c: CohortSnapshot, now: number): bigint =>
  membersOf(s, c.address)
    .filter((m) => m.state === 'Joined' && inBreach(m, c, now))
    .reduce((sum, m) => sum + (m.stake - feeOf(m.stake)), c.pool);

/** `floor(projected_pool × m.stake ÷ Σ stake of standing members)`. */
const projectedShare = (s: Snapshot, c: CohortSnapshot, now: number, stake: bigint): bigint => {
  const standingStake = membersOf(s, c.address)
    .filter((m) => isStanding(m, c, now))
    .reduce((sum, m) => sum + m.stake, 0n);
  if (standingStake === 0n) return 0n;
  return (projectedPool(s, c, now) * stake) / standingStake;
};

/** Standing first, then the rest by days lasted, descending (FR-062). */
const roster = (s: Snapshot, c: CohortSnapshot, now: number): Key[] =>
  [...membersOf(s, c.address)]
    .sort((a, b) => {
      const standing = Number(isStanding(b, c, now)) - Number(isStanding(a, c, now));
      if (standing !== 0) return standing;
      const lasted = b.daysCompleted - a.daysCompleted;
      return lasted !== 0 ? lasted : a.wallet.localeCompare(b.wallet);
    })
    .map((m) => m.wallet);

/** Every figure a cohort screen shows, in one object, for comparison. */
const figuresOf = (s: Snapshot, c: CohortSnapshot, now: number, forWallets: readonly Key[]) => ({
  survivors: survivors(s, c, now),
  projectedPool: projectedPool(s, c, now),
  shares: forWallets.map((w) => {
    const m = membersOf(s, c.address).find((x) => x.wallet === w);
    return m ? projectedShare(s, c, now, m.stake) : 0n;
  }),
  roster: roster(s, c, now),
});

describe('the figures move at the deadline, not when the crank runs (FR-038a, AC-8)', () => {
  let h: Harness;
  let cohort: Address;
  let mint: Address;
  let fees: FeeDestinations;
  let crank: KeyPairSigner;
  let wallets: Address[];
  /** Everyone who completed day 0 — the ones still standing after it. */
  let sitters: Address[];
  let atDeadline: ReturnType<typeof figuresOf>;
  let sampled: Address[];
  const signers = new Map<Address, KeyPairSigner>();

  beforeAll(async () => {
    h = await createHarness({ startTime: START - DAY_LENGTH });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 1_000_000_000n * SKR);

    fees = await h.injectFeeDestinations(mint);
    await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });
    await h.injectLeague(LEAGUE, {
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      firstStart: BigInt(START),
      nextIndex: 1,
    });
    cohort = await h.injectCohort(LEAGUE, INDEX, {
      start: BigInt(START),
      joinCutoff: BigInt(START - 10),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
    });
    crank = await h.newWallet();

    // A thousand members, spread across all four tiers.
    wallets = [];
    for (let i = 0; i < MEMBERS; i += 1) {
      const who = await generateKeyPairSigner();
      await h.fund(who.address, 40_000_000n);
      h.injectTokenAccount(await associatedTokenPda(who.address, mint), mint, who.address, DEFAULT_CHIPS[3]);
      await h.sendOrThrow([await joinCohortIx(who.address, mint, LEAGUE, INDEX, i % 4)], who);
      wallets.push(who.address);
      // The signer is needed again for the sitting, so keep it.
      signers.set(who.address, who);
    }

    // Day 0: three in five sit, the rest are about to miss it.
    h.setTime(START);
    sitters = [];
    for (const [at, wallet] of wallets.entries()) {
      if (at % 5 < 3) {
        await h.sendOrThrow(
          [await submitDayIx(wallet, LEAGUE, INDEX, 0, new Uint8Array(32))],
          signers.get(wallet)!,
        );
        sitters.push(wallet);
      }
    }

    // One second past day 0's deadline: every device already knows.
    h.setTime(START + DAY_LENGTH);
    sampled = [wallets[0]!, wallets[1]!, wallets[2]!, wallets[3]!, sitters[0]!, sitters[1]!];
    const snapshot = await h.snapshot();
    const c = snapshot.cohorts.find((x) => x.address === cohort)!;
    atDeadline = figuresOf(snapshot, c, h.now(), sampled);
  });

  it('already excludes everyone who missed the deadline, with nothing recorded yet', async () => {
    const onChain = await h.readCohort(LEAGUE, INDEX);
    expect(onChain.forfeitsRecorded, 'no automation has run').toBe(0);
    expect(onChain.pool, 'and no money has moved').toBe(0n);

    // The counter is the sitters, and the projected pool is what the missers
    // are about to forfeit — both read off chain state alone.
    expect(atDeadline.survivors).toBe(sitters.length);
    expect(atDeadline.survivors).toBeLessThan(MEMBERS);
    const missers = wallets.length - sitters.length;
    expect(missers).toBeGreaterThan(0);
    expect(atDeadline.projectedPool).toBeGreaterThan(0n);
  });

  it('shows the same figures after every forfeit is recorded (AC-8)', async () => {
    // The crank works through the whole cohort, ten at a time.
    const missers = wallets.filter((w) => !sitters.includes(w));
    for (let at = 0; at < missers.length; at += PAGE) {
      const page = missers.slice(at, at + PAGE);
      await h.sendOrThrow(
        [
          await recordForfeitsIx(
            crank.address,
            fees,
            LEAGUE,
            INDEX,
            await forfeitPairs(cohort, page),
          ),
        ],
        crank,
      );
    }

    const onChain = await h.readCohort(LEAGUE, INDEX);
    expect(onChain.forfeitsRecorded).toBe(missers.length);
    expect(onChain.pool).toBeGreaterThan(0n);

    const snapshot = await h.snapshot();
    const c = snapshot.cohorts.find((x) => x.address === cohort)!;
    const afterRecording = figuresOf(snapshot, c, h.now(), sampled);

    // The whole point: recording moved money and changed no displayed figure.
    expect(afterRecording.survivors).toBe(atDeadline.survivors);
    expect(afterRecording.projectedPool).toBe(atDeadline.projectedPool);
    expect(afterRecording.shares).toEqual(atDeadline.shares);
    expect(afterRecording.roster).toEqual(atDeadline.roster);
  });

  it('has the stored pool equal to what was projected before anything ran', async () => {
    // The projection was not merely stable, it was right: the pool the contract
    // now holds is the figure the screen showed a moment after the deadline.
    const onChain = await h.readCohort(LEAGUE, INDEX);
    expect(onChain.pool).toBe(atDeadline.projectedPool);
    expect(cohort).toBe(await cohortPda(LEAGUE, INDEX));
  });
});
