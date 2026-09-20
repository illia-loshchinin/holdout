/**
 * Settlement and the claim. Task T077.
 *
 * The promise this file holds the contract to: **every member settles on the
 * same figures at the same moment** (FR-031). The shares are per-tier, not
 * per-member, so a cohort finalises in constant time whatever its size
 * (research R10) — two finishers on equal stakes therefore receive
 * byte-identical payouts by construction, not by luck.
 *
 * `settle_count` is paged, repeatable and order-free, exactly as
 * `record_forfeits` is. `settle_finalize` runs **once** (INV-5, INV-6), and
 * what it writes is written once.
 *
 * The part worth the most care is the **carry-over destination**. A no-finisher
 * cohort must be handed the league's cohort `next_index − 1` and its vault, and
 * that cohort must be `Open`. Withholding them is `CarryOverTargetRequired`
 * (6046); a league with no open lobby is `CarryOverTargetNotOpen` (6047), and
 * the refusal costs one automation pass rather than stranding money the
 * caller's timing chose to strand. So the destination is fixed by chain state,
 * and running "open the lobby, then finalize" in either order lands the
 * carry-over in the same cohort with the same figure (FR-030a, FR-037a).
 *
 * Each cohort here is index 0 of **its own** test league unless the case is
 * about ordering, so `PreviousCohortUnsettled` (6045) never fires by accident
 * (T016).
 */

import { generateKeyPairSigner, getAddressDecoder, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import { assertUnchanged } from '../invariants/checker.js';
import {
  DEFAULT_CHIPS,
  FIRST_START,
  SKR,
  createHarness,
  eventPayloads,
  expectProgramError,
  oneEvent,
  type Harness,
} from '../helpers/harness.js';
import {
  carryOverTarget,
  claimPayoutIx,
  forfeitPairs,
  joinCohortIx,
  openCohortIx,
  publishChipSetIx,
  settleCountIx,
  settleFinalizeIx,
  submitDayIx,
} from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda, vaultPda } from '../helpers/pdas.js';

const DAY_LENGTH = 60;
const DURATION = 7;
const START = FIRST_START;
const END = START + DURATION * DAY_LENGTH;
const CADENCE = 2 * DURATION * DAY_LENGTH;
const JOIN_LOCK = 10;

const feeOf = (stake: bigint) => (stake * 20n) / 100n;
const shareFor = (pool: bigint, chip: bigint, total: bigint) => (total === 0n ? 0n : (pool * chip) / total);

/** `CohortSettled { cohort, finishers, total_finisher_stake, share_per_tier, rounding_remainder }`. */
const decodeSettled = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return {
    cohort: getAddressDecoder().decode(bytes.slice(0, 32)),
    finishers: view.getUint32(32, true),
    totalFinisherStake: view.getBigUint64(36, true),
    sharePerTier: [0, 1, 2, 3].map((t) => view.getBigUint64(44 + t * 8, true)),
    roundingRemainder: view.getBigUint64(76, true),
  };
};

/** `CarryOverSent { from, to, amount, fee }`. */
const decodeCarryOverSent = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return {
    from: getAddressDecoder().decode(bytes.slice(0, 32)),
    to: getAddressDecoder().decode(bytes.slice(32, 64)),
    amount: view.getBigUint64(64, true),
    fee: view.getBigUint64(72, true),
  };
};

describe('settle_count, settle_finalize and claim_payout (contracts §2, §3)', () => {
  let h: Harness;
  let mint: Address;
  let feeDestination: Address;
  let crank: KeyPairSigner;
  let operator: KeyPairSigner;
  let league: number;

  beforeEach(async () => {
    h = await createHarness({ startTime: START - DAY_LENGTH });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 100_000_000n * SKR);

    const treasury = (await generateKeyPairSigner()).address;
    feeDestination = await associatedTokenPda(treasury, mint);
    h.injectTokenAccount(feeDestination, mint, treasury, 0n);

    operator = await h.newWallet();
    await h.injectConfig({ skrMint: mint, feeDestination, operator: operator.address });
    crank = await h.newWallet();
    league = h.freshTestLeague().leagueId;
    await h.injectLeague(league, {
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      firstStart: BigInt(START),
      cadence: BigInt(CADENCE),
      joinLock: BigInt(JOIN_LOCK),
      nextIndex: 1,
    });
  });

  /** Cohort `index` of the current test league, injected in its lobby. */
  const cohortAt = async (index: number) =>
    h.injectCohort(league, index, {
      start: BigInt(START + index * CADENCE),
      joinCutoff: BigInt(START + index * CADENCE - JOIN_LOCK),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
    });

  /** A wallet funded with SKR and joined into cohort `index` at `tier`. */
  const member = async (index: number, tier: number): Promise<KeyPairSigner> => {
    const who = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(who.address, mint), mint, who.address, DEFAULT_CHIPS[3]);
    await h.sendOrThrow([await joinCohortIx(who.address, mint, league, index, tier)], who);
    return who;
  };

  /** Complete every day of cohort `index` for `who`. */
  const clearAllDays = async (who: KeyPairSigner, index: number) => {
    const start = START + index * CADENCE;
    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(start + day * DAY_LENGTH);
      await h.sendOrThrow([await submitDayIx(who.address, league, index, day, new Uint8Array(32).fill(day))], who);
    }
  };

  const count = async (index: number, wallets: readonly Address[], previous?: Address) =>
    h.send(
      [
        await settleCountIx(
          crank.address,
          feeDestination,
          league,
          index,
          await forfeitPairs(await cohortPda(league, index), wallets),
          previous,
        ),
      ],
      crank,
    );

  const finalize = async (index: number, target?: { cohort: Address; vault: Address }) =>
    h.send([await settleFinalizeIx(crank.address, feeDestination, league, index, target)], crank);

  // ------------------------------------------------------ counting and paying

  it('pays two finishers on equal stakes byte-identical amounts (FR-031)', async () => {
    const cohort = await cohortAt(0);
    const a = await member(0, 2);
    const b = await member(0, 2);
    const quitter = await member(0, 3);

    await clearAllDays(a, 0);
    await clearAllDays(b, 0);

    h.setTime(END);
    await h.sendOrThrow(
      [
        await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [
          a.address,
          b.address,
          quitter.address,
        ])),
      ],
      crank,
    );
    await h.sendOrThrow([await settleFinalizeIx(crank.address, feeDestination, league, 0)], crank);

    const settled = await h.readCohort(league, 0);
    const stake = DEFAULT_CHIPS[2]!;
    expect(settled.finisherCount).toBe(2);
    expect(settled.totalFinisherStake).toBe(2n * stake);
    expect(settled.settled).toBe(true);
    // Identical tier, identical share — the same number, not two close ones.
    expect(settled.sharePerTier[2]).toBe(shareFor(settled.pool, stake, 2n * stake));

    const before = [h.tokenBalance(await associatedTokenPda(a.address, mint)), h.tokenBalance(await associatedTokenPda(b.address, mint))];
    await h.sendOrThrow([await claimPayoutIx(a.address, mint, league, 0)], a);
    await h.sendOrThrow([await claimPayoutIx(b.address, mint, league, 0)], b);

    const paidA = h.tokenBalance(await associatedTokenPda(a.address, mint)) - before[0]!;
    const paidB = h.tokenBalance(await associatedTokenPda(b.address, mint)) - before[1]!;
    expect(paidA).toBe(paidB);
    expect(paidA).toBe(stake + settled.sharePerTier[2]!);
  });

  it('sends the rounding remainder to the fee destination, and the claims add up to the unit (AC-3)', async () => {
    const cohort = await cohortAt(0);
    // Three finishers on mixed tiers, so the pool rarely divides evenly.
    const finishers = [await member(0, 0), await member(0, 1), await member(0, 3)];
    const quitters = [await member(0, 2), await member(0, 3)];
    for (const who of finishers) await clearAllDays(who, 0);

    const forfeitedStakes = [DEFAULT_CHIPS[2]!, DEFAULT_CHIPS[3]!];
    const feesFromForfeits = forfeitedStakes.reduce((sum, s) => sum + feeOf(s), 0n);
    const poolFromForfeits = forfeitedStakes.reduce((sum, s) => sum + (s - feeOf(s)), 0n);

    h.setTime(END);
    await h.sendOrThrow(
      [
        await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [
          ...finishers.map((m) => m.address),
          ...quitters.map((m) => m.address),
        ])),
      ],
      crank,
    );

    const counted = await h.readCohort(league, 0);
    expect(counted.pool).toBe(poolFromForfeits);
    expect(h.tokenBalance(feeDestination)).toBe(feesFromForfeits);

    const result = await h.sendOrThrow([await settleFinalizeIx(crank.address, feeDestination, league, 0)], crank);
    const settled = await h.readCohort(league, 0);

    const expectedShares = [0, 1, 2, 3].map((t) =>
      shareFor(poolFromForfeits, DEFAULT_CHIPS[t]!, settled.totalFinisherStake),
    );
    const paidOut = settled.finishersPerTier.reduce(
      (sum, n, t) => sum + BigInt(n) * expectedShares[t]!,
      0n,
    );
    const remainder = poolFromForfeits - paidOut;

    expect(settled.roundingRemainder).toBe(remainder);
    expect(h.tokenBalance(feeDestination)).toBe(feesFromForfeits + remainder);
    expect(settled.pool).toBe(poolFromForfeits - remainder);

    const event = decodeSettled(oneEvent(result, 'CohortSettled'));
    expect(event.finishers).toBe(3);
    expect(event.roundingRemainder).toBe(remainder);

    // Every finisher claims; the vault ends empty to the unit (AC-3).
    for (const who of finishers) {
      await h.sendOrThrow([await claimPayoutIx(who.address, mint, league, 0)], who);
    }
    const after = await h.readCohort(league, 0);
    expect(after.heldStake).toBe(0n);
    expect(after.pool).toBe(0n);
    expect(h.tokenBalance(await vaultPda(cohort))).toBe(0n);
  });

  it('counts in pages, in any order, and repeating a page changes nothing (INV-7)', async () => {
    const cohort = await cohortAt(0);
    const members = [await member(0, 0), await member(0, 1), await member(0, 2), await member(0, 3)];
    await clearAllDays(members[0]!, 0);
    await clearAllDays(members[2]!, 0);
    const wallets = members.map((m) => m.address);

    h.setTime(END);
    // Reversed, then overlapping, then everyone.
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [wallets[3]!, wallets[1]!]))],
      crank,
    );
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [wallets[1]!, wallets[0]!]))],
      crank,
    );
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, wallets))],
      crank,
    );

    const counted = await h.readCohort(league, 0);
    expect(counted.counted).toBe(4);
    expect(counted.finisherCount).toBe(2);

    const before = await h.snapshot();
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, wallets))],
      crank,
    );
    assertUnchanged(before, await h.snapshot());
  });

  it('refuses to finalize before every member is counted (INV-5)', async () => {
    const cohort = await cohortAt(0);
    const a = await member(0, 1);
    const b = await member(0, 1);
    await clearAllDays(a, 0);
    await clearAllDays(b, 0);

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [a.address]))],
      crank,
    );
    expectProgramError(await finalize(0), 'CountIncomplete');
    expect((await h.readCohort(league, 0)).settled).toBe(false);

    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [b.address]))],
      crank,
    );
    expect((await finalize(0)).ok).toBe(true);
  });

  it('finalizes exactly once (INV-5, INV-6)', async () => {
    const cohort = await cohortAt(0);
    const a = await member(0, 1);
    await clearAllDays(a, 0);

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [a.address]))],
      crank,
    );
    await h.sendOrThrow([await settleFinalizeIx(crank.address, feeDestination, league, 0)], crank);

    const before = await h.snapshot();
    expectProgramError(await finalize(0), 'AlreadySettled');
    assertUnchanged(before, await h.snapshot());
  });

  it('refuses settlement before the final deadline', async () => {
    const cohort = await cohortAt(0);
    const a = await member(0, 1);
    await clearAllDays(a, 0);

    h.setTime(END - 1);
    expectProgramError(await count(0, [a.address]), 'NotSettling');
    expectProgramError(await finalize(0), 'NotSettling');
  });

  // ---------------------------------------------------------------- the claim

  it('pays a finisher stake plus share, once, and indefinitely (FR-034, FR-035)', async () => {
    const cohort = await cohortAt(0);
    const winner = await member(0, 1);
    const quitter = await member(0, 3);
    await clearAllDays(winner, 0);

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [winner.address, quitter.address]))],
      crank,
    );
    await h.sendOrThrow([await settleFinalizeIx(crank.address, feeDestination, league, 0)], crank);

    const settled = await h.readCohort(league, 0);
    const expected = DEFAULT_CHIPS[1]! + settled.sharePerTier[1]!;

    // Claimable long after the fact: a month later is still fine.
    h.setTime(END + 30 * 86_400);
    const token = await associatedTokenPda(winner.address, mint);
    const before = h.tokenBalance(token);
    const result = await h.sendOrThrow([await claimPayoutIx(winner.address, mint, league, 0)], winner);
    expect(h.tokenBalance(token) - before).toBe(expected);
    expect((await h.readMembership(cohort, winner.address)).state).toBe('Claimed');
    expect(eventPayloads(result, 'PayoutClaimed')).toHaveLength(1);

    // Twice is refused, and the quitter cannot claim at all.
    expectProgramError(await h.send([await claimPayoutIx(winner.address, mint, league, 0)], winner), 'AlreadyClaimed');
    expectProgramError(await h.send([await claimPayoutIx(quitter.address, mint, league, 0)], quitter), 'NotClaimable');
  });

  it('refuses a claim before the cohort is settled', async () => {
    const cohort = await cohortAt(0);
    const winner = await member(0, 1);
    await clearAllDays(winner, 0);

    h.setTime(END);
    expectProgramError(await h.send([await claimPayoutIx(winner.address, mint, league, 0)], winner), 'NotClaimable');
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [winner.address]))],
      crank,
    );
    // Counted but not finalised: there is no share to read yet.
    expectProgramError(await h.send([await claimPayoutIx(winner.address, mint, league, 0)], winner), 'NotClaimable');
    expect(cohort).toBe(await cohortPda(league, 0));
  });

  // --------------------------------------------------- nobody finished at all

  it('splits a no-finisher pool 50/50 with the odd unit to the carry-over (FR-037)', async () => {
    const cohort = await cohortAt(0);
    // One stake, so the pool is odd if the stake minus its fee is.
    const quitter = await member(0, 0);

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [quitter.address]))],
      crank,
    );

    const counted = await h.readCohort(league, 0);
    const pool = counted.pool;
    expect(counted.finisherCount).toBe(0);
    expect(counted.totalFinisherStake).toBe(0n);

    // The league's next cohort is the destination, and it must be open.
    const next = await cohortAt(1);
    await h.injectLeague(league, {
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      firstStart: BigInt(START),
      cadence: BigInt(CADENCE),
      joinLock: BigInt(JOIN_LOCK),
      nextIndex: 2,
    });

    const feesBefore = h.tokenBalance(feeDestination);
    const result = await h.sendOrThrow(
      [await settleFinalizeIx(crank.address, feeDestination, league, 0, await carryOverTarget(league, 1))],
      crank,
    );

    const half = pool / 2n;
    const carry = pool - half;
    expect(carry, 'the odd unit goes to the members\' side').toBeGreaterThanOrEqual(half);

    const settled = await h.readCohort(league, 0);
    expect(settled.noFinisherFee).toBe(half);
    expect(settled.carryOverSent).toBe(carry);
    expect(settled.carryOverHeld, 'Phase 1 refuses rather than holding (FR-037a)').toBe(0n);
    expect(settled.carryOverTarget).toBe(next);
    expect(settled.pool).toBe(0n);
    expect(h.tokenBalance(feeDestination)).toBe(feesBefore + half);

    const target = await h.readCohort(league, 1);
    expect(target.pool).toBe(carry);
    expect(target.carryOverReceived).toBe(carry);
    expect(h.tokenBalance(await vaultPda(next))).toBe(target.heldStake + target.pool);

    const event = decodeCarryOverSent(oneEvent(result, 'CarryOverSent'));
    expect(event.from).toBe(cohort);
    expect(event.to).toBe(next);
    expect(event.amount).toBe(carry);
    expect(event.fee).toBe(half);
  });

  it('carries over into a cohort that itself has no finishers, and on again (ES-7a)', async () => {
    // Cohort 0 carries into cohort 1; cohort 1 also has nobody finish, so its
    // pool — its own forfeits *plus* what it received — carries into cohort 2.
    const first = await cohortAt(0);
    const quitter0 = await member(0, 0);
    await cohortAt(1);
    await h.injectLeague(league, {
      durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
      cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK), nextIndex: 2,
    });

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(first, [quitter0.address]))],
      crank,
    );
    await h.sendOrThrow(
      [await settleFinalizeIx(crank.address, feeDestination, league, 0, await carryOverTarget(league, 1))],
      crank,
    );
    const received = (await h.readCohort(league, 1)).carryOverReceived;
    expect(received).toBeGreaterThan(0n);

    // Someone joins cohort 1's lobby and quits.
    h.setTime(START + CADENCE - 2 * JOIN_LOCK);
    const quitter1 = await member(1, 3);
    const second = await cohortPda(league, 1);
    await cohortAt(2);
    await h.injectLeague(league, {
      durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
      cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK), nextIndex: 3,
    });

    h.setTime(START + CADENCE + DURATION * DAY_LENGTH);
    await h.sendOrThrow(
      [
        await settleCountIx(crank.address, feeDestination, league, 1, await forfeitPairs(second, [quitter1.address]), first),
      ],
      crank,
    );
    const poolOfSecond = (await h.readCohort(league, 1)).pool;
    // Its own forfeit plus the carry it received.
    expect(poolOfSecond).toBe(received + (DEFAULT_CHIPS[3]! - feeOf(DEFAULT_CHIPS[3]!)));

    await h.sendOrThrow(
      [await settleFinalizeIx(crank.address, feeDestination, league, 1, await carryOverTarget(league, 2))],
      crank,
    );
    const third = await h.readCohort(league, 2);
    expect(third.carryOverReceived).toBe(poolOfSecond - poolOfSecond / 2n);
  });

  // ------------------------------------- the destination cannot be chosen

  it('refuses a no-finisher finalize with the target withheld (6046), writing nothing', async () => {
    const cohort = await cohortAt(0);
    const quitter = await member(0, 1);
    await cohortAt(1);
    await h.injectLeague(league, {
      durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
      cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK), nextIndex: 2,
    });

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [quitter.address]))],
      crank,
    );

    const before = await h.snapshot();
    expectProgramError(await finalize(0), 'CarryOverTargetRequired');
    assertUnchanged(before, await h.snapshot());
  });

  it('refuses a target that is not the league\'s latest cohort', async () => {
    const cohort = await cohortAt(0);
    const quitter = await member(0, 1);
    await cohortAt(1);
    await cohortAt(2);
    await h.injectLeague(league, {
      durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
      cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK), nextIndex: 3,
    });

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [quitter.address]))],
      crank,
    );

    // Cohort 1 is open too, but the destination is fixed at `next_index - 1`.
    const before = await h.snapshot();
    expectProgramError(await finalize(0, await carryOverTarget(league, 1)), 'AccountMismatch');
    assertUnchanged(before, await h.snapshot());
  });

  it('refuses when the league has no cohort open for joining (6047), and takes it once one is', async () => {
    const cohort = await cohortAt(0);
    const quitter = await member(0, 1);

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(cohort, [quitter.address]))],
      crank,
    );

    // The league's latest cohort is this one, which is not a lobby at all.
    const before = await h.snapshot();
    expectProgramError(await finalize(0, await carryOverTarget(league, 0)), 'CarryOverTargetNotOpen');
    assertUnchanged(before, await h.snapshot());

    // Opening the next one is permissionless and always due, so the refusal
    // costs one pass (FR-037a).
    await h.sendOrThrow([await publishChipSetIx(operator.address, DEFAULT_CHIPS)], operator);
    await h.sendOrThrow([await openCohortIx(crank.address, mint, league, 1, cohort)], crank);
    const next = await cohortPda(league, 1);

    const result = await finalize(0, await carryOverTarget(league, 1));
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect((await h.readCohort(league, 0)).carryOverTarget).toBe(next);
  });

  it('lands the carry-over in the same place whichever order the pass runs in (FR-030a)', async () => {
    /** Settle cohort 0 of a fresh league, either finalizing first or opening first. */
    const run = async (finalizeFirst: boolean) => {
      const harness = await createHarness({ startTime: START - DAY_LENGTH });
      const skr = (await generateKeyPairSigner()).address;
      harness.injectMint(skr, null, 100_000_000n * SKR);
      const treasury = (await generateKeyPairSigner()).address;
      const fees = await associatedTokenPda(treasury, skr);
      harness.injectTokenAccount(fees, skr, treasury, 0n);
      const op = await harness.newWallet();
      await harness.injectConfig({ skrMint: skr, feeDestination: fees, operator: op.address });
      const runner = await harness.newWallet();
      const id = harness.freshTestLeague().leagueId;
      const timing = {
        durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
        cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK),
      };
      await harness.injectLeague(id, { ...timing, nextIndex: 1 });
      const zero = await harness.injectCohort(id, 0, {
        start: BigInt(START),
        joinCutoff: BigInt(START - JOIN_LOCK),
        durationDays: DURATION,
        dayLength: BigInt(DAY_LENGTH),
      });

      const who = await harness.newWallet();
      harness.injectTokenAccount(await associatedTokenPda(who.address, skr), skr, who.address, DEFAULT_CHIPS[3]);
      await harness.sendOrThrow([await joinCohortIx(who.address, skr, id, 0, 1)], who);

      harness.setTime(END);
      await harness.sendOrThrow(
        [await settleCountIx(runner.address, fees, id, 0, await forfeitPairs(zero, [who.address]))],
        runner,
      );
      await harness.sendOrThrow([await publishChipSetIx(op.address, DEFAULT_CHIPS)], op);

      if (finalizeFirst) {
        // The refusal, then the lobby, then the same call again.
        expectProgramError(
          await harness.send([await settleFinalizeIx(runner.address, fees, id, 0, await carryOverTarget(id, 0))], runner),
          'CarryOverTargetNotOpen',
        );
      }
      await harness.sendOrThrow([await openCohortIx(runner.address, skr, id, 1, zero)], runner);
      await harness.sendOrThrow(
        [await settleFinalizeIx(runner.address, fees, id, 0, await carryOverTarget(id, 1))],
        runner,
      );

      const source = await harness.readCohort(id, 0);
      const target = await harness.readCohort(id, 1);
      return {
        target: target.index,
        received: target.carryOverReceived,
        sent: source.carryOverSent,
        fee: source.noFinisherFee,
      };
    };

    const refusedFirst = await run(true);
    const openedFirst = await run(false);
    expect(refusedFirst).toEqual(openedFirst);
  });

  // --------------------------------------------------- pairs must still match

  it('fails a page carrying a pair from another cohort, leaving both cohorts alone (6060)', async () => {
    const cohort = await cohortAt(0);
    const mine = await member(0, 1);

    // A second league entirely, with its own cohort and member.
    const otherLeague = h.freshTestLeague().leagueId;
    await h.injectLeague(otherLeague, {
      durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
      cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK), nextIndex: 1,
    });
    const foreign = await h.injectCohort(otherLeague, 0, {
      start: BigInt(START),
      joinCutoff: BigInt(START - JOIN_LOCK),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
    });
    const stranger = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(stranger.address, mint), mint, stranger.address, DEFAULT_CHIPS[3]);
    await h.sendOrThrow([await joinCohortIx(stranger.address, mint, otherLeague, 0, 2)], stranger);

    h.setTime(END);
    const before = await h.snapshot();
    // The foreign pair would drive this cohort's `counted` towards its
    // `member_count` without its own members ever being counted.
    expectProgramError(
      await count(0, [stranger.address]),
      'AccountMismatch',
    );
    assertUnchanged(before, await h.snapshot());

    expect((await h.readCohort(league, 0)).counted).toBe(0);
    expect((await h.readCohort(otherLeague, 0)).counted).toBe(0);
    expect((await h.readMembership(foreign, stranger.address)).counted).toBe(false);
    expect((await h.readMembership(cohort, mine.address)).counted).toBe(false);
  });

  // ------------------------------------------------------------ cohort order

  it('refuses to count cohort 1 until cohort 0 of the league has settled (FR-061a)', async () => {
    const zero = await cohortAt(0);
    const one = await cohortAt(1);
    await h.injectLeague(league, {
      durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
      cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK), nextIndex: 2,
    });

    const first = await member(0, 1);
    await clearAllDays(first, 0);
    h.setTime(START + CADENCE - 2 * JOIN_LOCK);
    const second = await member(1, 1);

    h.setTime(START + CADENCE + DURATION * DAY_LENGTH);
    expectProgramError(await count(1, [second.address], zero), 'PreviousCohortUnsettled');

    // Settle cohort 0, and cohort 1 opens up.
    await h.sendOrThrow(
      [await settleCountIx(crank.address, feeDestination, league, 0, await forfeitPairs(zero, [first.address]))],
      crank,
    );
    await h.sendOrThrow([await settleFinalizeIx(crank.address, feeDestination, league, 0)], crank);

    const result = await count(1, [second.address], zero);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect((await h.readCohort(league, 1)).counted).toBe(1);
    expect(one).toBe(await cohortPda(league, 1));
  });

  it('refuses to count cohort 1 with the predecessor withheld or substituted', async () => {
    const zero = await cohortAt(0);
    await cohortAt(1);
    await h.injectLeague(league, {
      durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
      cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK), nextIndex: 2,
    });
    h.setTime(START + CADENCE - 2 * JOIN_LOCK);
    const second = await member(1, 1);

    h.setTime(START + CADENCE + DURATION * DAY_LENGTH);
    expectProgramError(await count(1, [second.address]), 'AccountMismatch');
    // A settled cohort of *another* league is not this league's predecessor.
    const otherLeague = h.freshTestLeague().leagueId;
    await h.injectLeague(otherLeague, { nextIndex: 1 });
    const decoy = await h.injectCohort(otherLeague, 0, { start: BigInt(START), settled: true });
    expectProgramError(await count(1, [second.address], decoy), 'AccountMismatch');
    expect(zero).toBe(await cohortPda(league, 0));
  });
});
