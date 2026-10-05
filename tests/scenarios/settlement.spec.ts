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
 * A cohort nobody finished sends its whole pool out — a third to the developer
 * destination, the rest to operations — and nothing carries over (FR-037, H34,
 * H35), so settling it needs no other cohort and no caller can change where the
 * money goes (FR-030a). Either way a finalizing cohort credits its reserve dues
 * to its league; boosts and comebacks are reserves.spec.ts's subject, and every
 * league here starts with empty reserves, so no boost changes a figure below.
 *
 * Each cohort here is index 0 of **its own** test league unless the case is
 * about ordering, so `PreviousCohortUnsettled` (6045) never fires by accident
 * (T016).
 */

import { generateKeyPairSigner, getAddressDecoder, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import { assertUnchanged, feeOf, forfeitSplit, noFinisherSplit } from '../invariants/checker.js';
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
  claimPayoutIx,
  forfeitPairs,
  joinCohortIx,
  settleCountIx,
  settleFinalizeIx,
  submitDayIx,
  type FeeDestinations,
} from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda, reservePda, vaultPda } from '../helpers/pdas.js';

const DAY_LENGTH = 60;
const DURATION = 7;
const START = FIRST_START;
const END = START + DURATION * DAY_LENGTH;
const CADENCE = 2 * DURATION * DAY_LENGTH;
const JOIN_LOCK = 10;

const shareFor = (pool: bigint, chip: bigint, total: bigint) => (total === 0n ? 0n : (pool * chip) / total);

/**
 * `CohortSettled { cohort, finishers, total_finisher_stake, share_per_tier,
 * rounding_remainder, boost, comeback_funded }`.
 */
const decodeSettled = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return {
    cohort: getAddressDecoder().decode(bytes.slice(0, 32)),
    finishers: view.getUint32(32, true),
    totalFinisherStake: view.getBigUint64(36, true),
    sharePerTier: [0, 1, 2, 3].map((t) => view.getBigUint64(44 + t * 8, true)),
    roundingRemainder: view.getBigUint64(76, true),
    boost: view.getBigUint64(84, true),
    comebackFunded: view.getBigUint64(92, true),
  };
};

describe('settle_count, settle_finalize and claim_payout (contracts §2, §3)', () => {
  let h: Harness;
  let mint: Address;
  let fees: FeeDestinations;
  let crank: KeyPairSigner;
  let operator: KeyPairSigner;
  let league: number;

  beforeEach(async () => {
    h = await createHarness({ startTime: START - DAY_LENGTH });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 100_000_000n * SKR);

    fees = await h.injectFeeDestinations(mint);

    operator = await h.newWallet();
    await h.injectConfig({
      skrMint: mint,
      feeDestination: fees.operations,
      developerDestination: fees.developer,
      operator: operator.address,
    });
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
          fees,
          league,
          index,
          await forfeitPairs(await cohortPda(league, index), wallets),
          previous,
        ),
      ],
      crank,
    );

  const finalize = async (index: number) =>
    h.send([await settleFinalizeIx(crank.address, fees, league, index)], crank);

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
        await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [
          a.address,
          b.address,
          quitter.address,
        ])),
      ],
      crank,
    );
    await h.sendOrThrow([await settleFinalizeIx(crank.address, fees, league, 0)], crank);

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

  it('sends the rounding remainder to operations, credits the dues to the league, and the claims add up to the unit (AC-3)', async () => {
    const cohort = await cohortAt(0);
    // Three finishers on mixed tiers, so the pool rarely divides evenly.
    const finishers = [await member(0, 0), await member(0, 1), await member(0, 3)];
    const quitters = [await member(0, 2), await member(0, 3)];
    for (const who of finishers) await clearAllDays(who, 0);

    const forfeitedStakes = [DEFAULT_CHIPS[2]!, DEFAULT_CHIPS[3]!];
    const parts = forfeitedStakes.map(forfeitSplit);
    const toOperations = parts.reduce((sum, p) => sum + p.operations, 0n);
    const toDeveloper = parts.reduce((sum, p) => sum + p.developer, 0n);
    const boostDue = parts.reduce((sum, p) => sum + p.boost, 0n);
    const comebackDue = parts.reduce((sum, p) => sum + p.comeback, 0n);
    const poolFromForfeits = forfeitedStakes.reduce((sum, s) => sum + (s - feeOf(s)), 0n);

    h.setTime(END);
    await h.sendOrThrow(
      [
        await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [
          ...finishers.map((m) => m.address),
          ...quitters.map((m) => m.address),
        ])),
      ],
      crank,
    );

    const counted = await h.readCohort(league, 0);
    expect(counted.pool).toBe(poolFromForfeits);
    expect([counted.boostDue, counted.comebackDue]).toEqual([boostDue, comebackDue]);
    expect(h.tokenBalance(fees.operations)).toBe(toOperations);
    expect(h.tokenBalance(fees.developer)).toBe(toDeveloper);

    const result = await h.sendOrThrow([await settleFinalizeIx(crank.address, fees, league, 0)], crank);
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
    expect(h.tokenBalance(fees.operations)).toBe(toOperations + remainder);
    expect(h.tokenBalance(fees.developer), 'the remainder is operations\' alone').toBe(toDeveloper);
    expect(settled.pool).toBe(poolFromForfeits - remainder);
    // An empty reserve boosts nothing; the dues move to the league (FR-037c).
    expect(settled.boostReceived).toBe(0n);
    expect([settled.boostDue, settled.comebackDue]).toEqual([0n, 0n]);
    const reserves = await h.readLeague(league);
    expect([reserves.boostReserve, reserves.comebackReserve]).toEqual([boostDue, comebackDue]);
    expect(h.tokenBalance(await reservePda(league))).toBe(boostDue + comebackDue);

    const event = decodeSettled(oneEvent(result, 'CohortSettled'));
    expect(event.finishers).toBe(3);
    expect(event.roundingRemainder).toBe(remainder);
    expect(event.boost).toBe(0n);
    expect(event.comebackFunded).toBe(0n);

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
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [wallets[3]!, wallets[1]!]))],
      crank,
    );
    await h.sendOrThrow(
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [wallets[1]!, wallets[0]!]))],
      crank,
    );
    await h.sendOrThrow(
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, wallets))],
      crank,
    );

    const counted = await h.readCohort(league, 0);
    expect(counted.counted).toBe(4);
    expect(counted.finisherCount).toBe(2);

    const before = await h.snapshot();
    await h.sendOrThrow(
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, wallets))],
      crank,
    );
    assertUnchanged(before, await h.snapshot());
  });

  it('refuses a page naming the same membership twice (INV-1, INV-2)', async () => {
    // The page is decoded up front, so each entry carries its own copy of the
    // account. Two entries for one membership would both read
    // `counted == false`, and the second pass would count the member a second
    // time: `counted`, `finisherCount` and `totalFinisherStake` all double.
    //
    // That is not merely untidy accounting. `settle_finalize` needs
    // `counted == member_count`, so a caller who duplicates one member and
    // omits another reaches that total with a member never counted, finalises
    // on shares computed from an inflated finisher stake, and strands the
    // omitted member's stake in a settled cohort that can never be counted
    // again. Anchor's duplicate-mutable-account check does not reach it —
    // that covers the `Accounts` struct, not `remaining_accounts`.
    const cohort = await cohortAt(0);
    const finisher = await member(0, 0);
    const other = await member(0, 1);
    await clearAllDays(finisher, 0);

    h.setTime(END);
    const before = await h.snapshot();

    // The finisher twice, `other` left out — the shape that would forge a
    // complete count.
    expectProgramError(
      await h.send(
        [
          await settleCountIx(
            crank.address,
            fees,
            league,
            0,
            await forfeitPairs(cohort, [finisher.address, finisher.address]),
          ),
        ],
        crank,
      ),
      'AccountMismatch',
    );

    // Refused whole: a page wrong anywhere writes nothing.
    assertUnchanged(before, await h.snapshot());

    // And the honest page still works, with each member counted exactly once.
    await h.sendOrThrow(
      [
        await settleCountIx(
          crank.address,
          fees,
          league,
          0,
          await forfeitPairs(cohort, [finisher.address, other.address]),
        ),
      ],
      crank,
    );
    const counted = await h.readCohort(league, 0);
    expect(counted.counted, 'both members, once each').toBe(2);
    expect(counted.finisherCount, 'one finisher, counted once').toBe(1);
    expect(counted.totalFinisherStake).toBe(
      (await h.readMembership(cohort, finisher.address)).stake,
    );
  });

  it('refuses to finalize before every member is counted (INV-5)', async () => {
    const cohort = await cohortAt(0);
    const a = await member(0, 1);
    const b = await member(0, 1);
    await clearAllDays(a, 0);
    await clearAllDays(b, 0);

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [a.address]))],
      crank,
    );
    expectProgramError(await finalize(0), 'CountIncomplete');
    expect((await h.readCohort(league, 0)).settled).toBe(false);

    await h.sendOrThrow(
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [b.address]))],
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
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [a.address]))],
      crank,
    );
    await h.sendOrThrow([await settleFinalizeIx(crank.address, fees, league, 0)], crank);

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
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [winner.address, quitter.address]))],
      crank,
    );
    await h.sendOrThrow([await settleFinalizeIx(crank.address, fees, league, 0)], crank);

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
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [winner.address]))],
      crank,
    );
    // Counted but not finalised: there is no share to read yet.
    expectProgramError(await h.send([await claimPayoutIx(winner.address, mint, league, 0)], winner), 'NotClaimable');
    expect(cohort).toBe(await cohortPda(league, 0));
  });

  // --------------------------------------------------- nobody finished at all

  it('splits a no-finisher pool a third to the developer and the rest to operations, and nothing carries over (FR-037)', async () => {
    const cohort = await cohortAt(0);
    const quitter = await member(0, 3);
    // A later lobby is open: it must receive nothing.
    const next = await cohortAt(1);
    await h.injectLeague(league, {
      durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
      cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK), nextIndex: 2,
    });

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [quitter.address]))],
      crank,
    );
    const counted = await h.readCohort(league, 0);
    const pool = counted.pool;
    expect(counted.finisherCount).toBe(0);
    expect(pool).toBe(DEFAULT_CHIPS[3]! - feeOf(DEFAULT_CHIPS[3]!));

    const operationsBefore = h.tokenBalance(fees.operations);
    const developerBefore = h.tokenBalance(fees.developer);
    const nextBefore = await h.readCohort(league, 1);
    const result = await h.sendOrThrow([await settleFinalizeIx(crank.address, fees, league, 0)], crank);

    const settled = await h.readCohort(league, 0);
    expect(settled.settled).toBe(true);
    expect(settled.noFinisherFee).toBe(pool);
    expect(settled.pool).toBe(0n);
    // 70 SKR: 23.333333 to the developer (floored), 46.666667 to operations.
    const out = noFinisherSplit(pool);
    expect(out.developer).toBe(pool / 3n);
    expect(h.tokenBalance(fees.developer)).toBe(developerBefore + out.developer);
    expect(h.tokenBalance(fees.operations)).toBe(operationsBefore + out.operations);
    // Its reserve shares still reach the league (H35), and the vault ends empty.
    const split = forfeitSplit(DEFAULT_CHIPS[3]!);
    const reserves = await h.readLeague(league);
    expect([reserves.boostReserve, reserves.comebackReserve]).toEqual([split.boost, split.comeback]);
    expect([settled.boostDue, settled.comebackDue, settled.boostReceived]).toEqual([0n, 0n, 0n]);
    expect(h.tokenBalance(await vaultPda(cohort))).toBe(0n);

    expect(await h.readCohort(league, 1)).toEqual(nextBefore);
    expect(h.tokenBalance(await vaultPda(next))).toBe(nextBefore.heldStake + nextBefore.pool);

    const event = decodeSettled(oneEvent(result, 'CohortSettled'));
    expect(event.finishers).toBe(0);
    expect(event.boost).toBe(0n);
  });

  it('settles a no-finisher cohort with no other cohort in its league (FR-037, FR-030a)', async () => {
    // The league's latest cohort is this one: there is nothing to open first.
    const cohort = await cohortAt(0);
    const quitter = await member(0, 1);

    h.setTime(END);
    await h.sendOrThrow(
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(cohort, [quitter.address]))],
      crank,
    );
    const pool = (await h.readCohort(league, 0)).pool;

    const result = await finalize(0);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect((await h.readCohort(league, 0)).noFinisherFee).toBe(pool);
    expect(h.tokenBalance(await vaultPda(cohort))).toBe(0n);
  });

  it('gives the same no-finisher settlement whoever calls it (FR-030a)', async () => {
    /** Settle cohort 0 of a fresh league with nobody finishing, called by a new wallet. */
    const run = async () => {
      const harness = await createHarness({ startTime: START - DAY_LENGTH });
      const skr = (await generateKeyPairSigner()).address;
      harness.injectMint(skr, null, 100_000_000n * SKR);
      const fees = await harness.injectFeeDestinations(skr);
      await harness.injectConfig({ skrMint: skr, feeDestination: fees.operations, developerDestination: fees.developer });
      const runner = await harness.newWallet();
      const id = harness.freshTestLeague().leagueId;
      await harness.injectLeague(id, {
        durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), firstStart: BigInt(START),
        cadence: BigInt(CADENCE), joinLock: BigInt(JOIN_LOCK), nextIndex: 1,
      });
      const zero = await harness.injectCohort(id, 0, {
        start: BigInt(START),
        joinCutoff: BigInt(START - JOIN_LOCK),
        durationDays: DURATION,
        dayLength: BigInt(DAY_LENGTH),
      });
      const who = await harness.newWallet();
      harness.injectTokenAccount(await associatedTokenPda(who.address, skr), skr, who.address, DEFAULT_CHIPS[3]);
      await harness.sendOrThrow([await joinCohortIx(who.address, skr, id, 0, 2)], who);

      harness.setTime(END);
      await harness.sendOrThrow(
        [await settleCountIx(runner.address, fees, id, 0, await forfeitPairs(zero, [who.address]))],
        runner,
      );
      await harness.sendOrThrow([await settleFinalizeIx(runner.address, fees, id, 0)], runner);
      const settled = await harness.readCohort(id, 0);
      return {
        fee: settled.noFinisherFee,
        pool: settled.pool,
        toOperations: harness.tokenBalance(fees.operations),
        toDeveloper: harness.tokenBalance(fees.developer),
        reserves: await harness.readLeague(id).then((l) => [l.boostReserve, l.comebackReserve]),
      };
    };

    expect(await run()).toEqual(await run());
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
    // The foreign pair, by its own cohort's PDA: it exists and decodes, which is
    // exactly what makes it dangerous. Counting it would drive this cohort's
    // `counted` towards its `member_count` without its own members ever being
    // counted.
    expectProgramError(
      await h.send(
        [
          await settleCountIx(
            crank.address,
            fees,
            league,
            0,
            await forfeitPairs(foreign, [stranger.address]),
          ),
        ],
        crank,
      ),
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
      [await settleCountIx(crank.address, fees, league, 0, await forfeitPairs(zero, [first.address]))],
      crank,
    );
    await h.sendOrThrow([await settleFinalizeIx(crank.address, fees, league, 0)], crank);

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
