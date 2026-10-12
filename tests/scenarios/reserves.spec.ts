/**
 * League reserves, the two fee destinations, and Easy and Hard (H35).
 *
 * What this file holds the contract to:
 *
 *  - **A forfeit splits four ways** (FR-027): 70 % to the pool, 10 % held in the
 *    cohort's vault as the member's comeback pledge (`comeback_due`), 10 % to the
 *    developer destination and 10 % — plus whatever the floors leave — to operations.
 *  - **The pledge is booked when the forfeit is counted, in cohort order**
 *    (FR-037c, INV-10): `settle_count` moves it into the league's reserve vault and
 *    onto `comeback_reserve` as it opens the member's comeback, so an open comeback
 *    is always funded and what a cohort draws can never depend on when the crank
 *    recorded somebody else's forfeit.
 *  - **The prize boost** (FR-037d): `min(10 % of the cohort's own pool, the
 *    boost reserve)`, drawn before the shares from the boost reserve alone — never
 *    a pledge — and nothing for a cohort with no forfeits, so "everyone finishes,
 *    exactly your stake back" still holds. The boost reserve is what nobody
 *    claimed: a cohort nobody finished (FR-037) and a pledge released.
 *  - **The comeback refund** (FR-037e): forfeit, then finish the league's first
 *    cohort to start after the missed deadline (or the one after it, for a miss on
 *    the last day), and the claim pays 10 % of the forfeited stake on top. A
 *    pledge nobody makes good goes to the boost reserve: replaced by a newer
 *    counted forfeit, a finish after the round, or `release_comeback`.
 *  - **Form** (constitution V): `create_league` takes 0 or 1 and nothing else,
 *    `open_cohort` copies it, and `create_league` also creates the reserve vault.
 *
 * Where a scenario runs through the program end to end, every transaction is
 * followed by the invariant checker against the snapshot before it, so INV-1,
 * INV-2 and INV-10 are asserted on the real program's every step, not only on the
 * hand-built states of `checker.spec.ts`.
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { generateKeyPairSigner, getAddressDecoder, type Address, type Instruction, type KeyPairSigner } from '@solana/kit';
import { describe, expect, it } from 'vitest';

import { LeagueConfig } from '../helpers/accounts.js';
import {
  DAY,
  DEFAULT_CHIPS,
  FIRST_START,
  HOUR,
  SKR,
  WEEK,
  cohortCustody,
  createHarness,
  expectProgramError,
  oneEvent,
  type Harness,
  type LeagueOverrides,
  type SendResult,
} from '../helpers/harness.js';
import {
  claimPayoutIx,
  createLeagueIx,
  forfeitPairs,
  joinCohortIx,
  openCohortIx,
  recordForfeitsIx,
  releaseComebackIx,
  settleCountIx,
  settleFinalizeIx,
  submitDayIx,
  type CreateLeagueArgs,
  type FeeDestinations,
} from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda, leaguePda, mintAuthorityPda, reserveBump, reservePda, vaultPda } from '../helpers/pdas.js';
import { assertInvariants, forfeitSplit, type Snapshot } from '../invariants/checker.js';

const DAY_LENGTH = 60;
const DURATION = 7;
const START = FIRST_START;
const JOIN_LOCK = 10;
/** Cohort `i` starts `i × CADENCE` after the first: a gap as long as the cohort itself. */
const CADENCE = 2 * DURATION * DAY_LENGTH;
const startOf = (index: number) => START + index * CADENCE;
const endOf = (index: number) => startOf(index) + DURATION * DAY_LENGTH;
/** The last second of day `k` of cohort `index`. */
const deadlineOf = (index: number, k: number) => startOf(index) + (k + 1) * DAY_LENGTH - 1;

const HERE = dirname(fileURLToPath(import.meta.url));

/** The pure-math golden vectors the Rust maths replays too (fixtures/settlement-vectors.json). */
const vectorText = readFileSync(resolve(HERE, '../../fixtures/settlement-vectors.json'), 'utf8');
const splitVectors = (
  JSON.parse(vectorText.replace(/:\s*(\d{16,})/g, ': "$1"')) as {
    reserve_vectors: { forfeit_split: Record<'stake' | 'pool' | 'comeback' | 'developer' | 'operations', number | string>[] };
  }
).reserve_vectors.forfeit_split;
const vectorFor = (stake: bigint) => {
  const v = splitVectors.find((x) => BigInt(x.stake) === stake);
  if (!v) throw new Error(`no forfeit_split vector for ${stake}`);
  return {
    pool: BigInt(v.pool),
    comeback: BigInt(v.comeback),
    developer: BigInt(v.developer),
    operations: BigInt(v.operations),
  };
};

/** `CohortSettled { cohort, finishers, total_finisher_stake, share_per_tier, rounding_remainder, boost, comeback_funded, no_finisher_boost }`. */
const decodeSettled = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return {
    boost: view.getBigUint64(84, true),
    comebackFunded: view.getBigUint64(92, true),
    noFinisherBoost: view.getBigUint64(100, true),
  };
};

/** `PayoutClaimed { cohort, wallet, amount, comeback }`. */
const decodeClaimed = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return {
    wallet: getAddressDecoder().decode(bytes.slice(32, 64)),
    amount: view.getBigUint64(64, true),
    comeback: view.getBigUint64(72, true),
  };
};

// ---------------------------------------------------------------------- world

interface World {
  h: Harness;
  mint: Address;
  fees: FeeDestinations;
  crank: KeyPairSigner;
  league: number;
  /** Send, insist it lands, and check every invariant against the step before. */
  act(instructions: readonly Instruction[], signer: KeyPairSigner): Promise<SendResult>;
}

/** A test league (or a real id, compressed) with empty reserves unless told otherwise. */
const world = async (over: LeagueOverrides & { leagueId?: number } = {}): Promise<World> => {
  const h = await createHarness({ startTime: START - DAY_LENGTH });
  const mint = (await generateKeyPairSigner()).address;
  h.injectMint(mint, null, 1_000_000_000n * SKR);
  const fees = await h.injectFeeDestinations(mint);
  await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });

  const { leagueId, ...league } = over;
  const id = leagueId ?? h.freshTestLeague().leagueId;
  await h.injectLeague(id, {
    durationDays: DURATION,
    dayLength: BigInt(DAY_LENGTH),
    firstStart: BigInt(START),
    cadence: BigInt(CADENCE),
    joinLock: BigInt(JOIN_LOCK),
    nextIndex: 3,
    ...league,
  });

  let previous: Snapshot | undefined;
  const w: World = {
    h,
    mint,
    fees,
    crank: await h.newWallet(),
    league: id,
    act: async (instructions, signer) => {
      previous ??= await h.snapshot();
      const result = await h.sendOrThrow(instructions, signer);
      const current = await h.snapshot();
      assertInvariants(current, previous);
      previous = current;
      return result;
    },
  };
  return w;
};

/** Cohort `index` of the world's league, on the compressed cadence unless `start` says otherwise. */
const cohortAt = async (w: World, index: number, start = startOf(index), leagueId = w.league) =>
  w.h.injectCohort(leagueId, index, {
    start: BigInt(start),
    joinCutoff: BigInt(start - JOIN_LOCK),
    durationDays: DURATION,
    dayLength: BigInt(DAY_LENGTH),
  });

/** A wallet holding SKR enough for several top-tier stakes, joined to nothing yet. */
const wallet = async (w: World, skr = 10n * DEFAULT_CHIPS[3]): Promise<KeyPairSigner> => {
  const who = await w.h.newWallet();
  w.h.injectTokenAccount(await associatedTokenPda(who.address, w.mint), w.mint, who.address, skr);
  return who;
};

const join = async (w: World, who: KeyPairSigner, index: number, tier: number, leagueId = w.league) =>
  w.act([await joinCohortIx(who.address, w.mint, leagueId, index, tier)], who);

/** Every day of cohort `index` (starting at `start`) completed by `who`. */
const clearAllDays = async (w: World, who: KeyPairSigner, index: number, start = startOf(index), leagueId = w.league) => {
  for (let day = 0; day < DURATION; day += 1) {
    w.h.setTime(start + day * DAY_LENGTH);
    await w.act([await submitDayIx(who.address, leagueId, index, day, new Uint8Array(32).fill(day))], who);
  }
};

const record = async (w: World, index: number, who: readonly KeyPairSigner[], leagueId = w.league) =>
  w.act(
    [await recordForfeitsIx(w.crank.address, w.fees, leagueId, index, await forfeitPairs(await cohortPda(leagueId, index), who.map((m) => m.address)))],
    w.crank,
  );

/** Count every listed member, then finalize: the whole of settlement for one cohort. */
const settle = async (w: World, index: number, who: readonly KeyPairSigner[], leagueId = w.league) => {
  const cohort = await cohortPda(leagueId, index);
  await w.act(
    [
      await settleCountIx(
        w.crank.address,
        w.fees,
        leagueId,
        index,
        await forfeitPairs(cohort, who.map((m) => m.address)),
        index > 0 ? await cohortPda(leagueId, index - 1) : undefined,
      ),
    ],
    w.crank,
  );
  await w.act([await settleFinalizeIx(w.crank.address, w.fees, leagueId, index)], w.crank);
};

const balanceOf = async (w: World, who: KeyPairSigner) =>
  w.h.tokenBalance(await associatedTokenPda(who.address, w.mint));

const reservesOf = async (w: World, leagueId = w.league) => {
  const l = await w.h.readLeague(leagueId);
  return { boost: l.boostReserve, comeback: l.comebackReserve, vault: w.h.tokenBalance(await reservePda(leagueId)) };
};

// ================================================================== the split

describe('when a forfeit is recorded (FR-027, H35)', () => {
  it('lands 70 % in the pool, 10 % as the member\'s pledge, 10 % with the developer and the rest with operations', async () => {
    const w = await world();
    // Stakes the golden vectors pin, chosen so every floor bites somewhere.
    const chips: [bigint, bigint, bigint, bigint] = [9n, 19n, 1_250n, 25_000_000n];
    await w.h.injectCohort(w.league, 0, {
      start: BigInt(START),
      joinCutoff: BigInt(START - JOIN_LOCK),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      chips,
    });
    const members: KeyPairSigner[] = [];
    for (const tier of [0, 1, 2, 3]) {
      const who = await wallet(w);
      await join(w, who, 0, tier);
      members.push(who);
    }

    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, members);

    const parts = chips.map(vectorFor);
    // The checker's own arithmetic agrees with the shared vectors.
    chips.forEach((stake, i) => expect(forfeitSplit(stake)).toEqual(parts[i]));
    const sum = (f: (p: (typeof parts)[number]) => bigint) => parts.reduce((s, p) => s + f(p), 0n);

    const cohort = await w.h.readCohort(w.league, 0);
    expect(cohort.pool).toBe(sum((p) => p.pool));
    expect(cohort.comebackDue).toBe(sum((p) => p.comeback));
    expect(w.h.tokenBalance(w.fees.developer)).toBe(sum((p) => p.developer));
    expect(w.h.tokenBalance(w.fees.operations)).toBe(sum((p) => p.operations));
    // Every unit of every stake is somewhere: in the vault, or with one of the two.
    const total = chips.reduce((s, c) => s + c, 0n);
    expect(w.h.tokenBalance(await vaultPda(await cohortPda(w.league, 0)))).toBe(cohortCustody(cohort));
    expect(cohortCustody(cohort) + sum((p) => p.developer + p.operations)).toBe(total);
    // …and none of it has reached the league yet: the pledge waits for the count (FR-037c).
    expect(await reservesOf(w)).toEqual({ boost: 0n, comeback: 0n, vault: 0n });
  });
});

// ============================================================ booked at the count

describe('given a league whose cohorts settle in order (FR-037c, INV-10)', () => {
  it('leaves the league untouched through recording, books the pledge when the forfeit is counted, and moves nothing more at finalization', async () => {
    const w = await world();
    await cohortAt(w, 0);
    const finisher = await wallet(w);
    const quitter = await wallet(w);
    await join(w, finisher, 0, 0);
    await join(w, quitter, 0, 3);

    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [quitter]);
    expect(await reservesOf(w)).toEqual({ boost: 0n, comeback: 0n, vault: 0n });
    expect((await w.h.readCohort(w.league, 0)).comebackDue, 'the pledge waits in the cohort\'s vault').toBe(10n * SKR);

    await clearAllDays(w, finisher, 0);
    w.h.setTime(endOf(0));
    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 0, await forfeitPairs(await cohortPda(w.league, 0), [finisher.address, quitter.address]))],
      w.crank,
    );
    // The count moves the pledge, whole, into the comeback reserve — and nothing into the boost reserve.
    expect(await reservesOf(w)).toEqual({ boost: 0n, comeback: 10n * SKR, vault: 10n * SKR });
    expect((await w.h.readCohort(w.league, 0)).comebackDue).toBe(0n);

    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 0)], w.crank);
    expect(await reservesOf(w), 'finalization adds nothing').toEqual({ boost: 0n, comeback: 10n * SKR, vault: 10n * SKR });
    const settled = await w.h.readCohort(w.league, 0);
    expect(settled.comebackDue).toBe(0n);
    expect(settled.boostReceived, 'an empty reserve boosts nothing').toBe(0n);
  });

  it('does not let a forfeit recorded in a running later cohort change an earlier cohort\'s boost, whichever runs first', async () => {
    /**
     * Cohort 0: one finisher, one quitter (pool 70, so 10 % is 7), against a
     * boost reserve of 5. Cohort 1 is running by the time cohort 0 settles, and
     * one of its members has missed day 0. Nothing about cohort 1 reaches the
     * league until its forfeit is counted, so the crank recording it first
     * cannot lift cohort 0's boost from 5.
     */
    const run = async (recordFirst: boolean) => {
      const w = await world({ boostReserve: 5n * SKR });
      await cohortAt(w, 0);
      await cohortAt(w, 1);
      const finisher = await wallet(w);
      const quitter = await wallet(w);
      const later = await wallet(w);
      await join(w, finisher, 0, 1);
      await join(w, quitter, 0, 3);
      await join(w, later, 1, 3);
      await clearAllDays(w, finisher, 0);

      // Cohort 1 is running, and `later` missed its day 0.
      w.h.setTime(deadlineOf(1, 0) + 1);
      if (recordFirst) {
        await record(w, 1, [later]);
        expect(await reservesOf(w), 'recording leaves the league alone').toEqual({
          boost: 5n * SKR,
          comeback: 0n,
          vault: 5n * SKR,
        });
      }
      await settle(w, 0, [finisher, quitter]);
      if (!recordFirst) await record(w, 1, [later]);

      const zero = await w.h.readCohort(w.league, 0);
      const one = await w.h.readCohort(w.league, 1);
      return {
        boost: zero.boostReceived,
        shares: zero.sharePerTier,
        reserves: await reservesOf(w),
        laterDue: one.comebackDue,
      };
    };

    const recordedFirst = await run(true);
    const settledFirst = await run(false);
    expect(recordedFirst.boost).toBe(5n * SKR);
    expect(recordedFirst).toEqual(settledFirst);
    // Cohort 0 drew the 5 and booked its quitter's pledge; cohort 1's pledge waits for its count.
    expect(recordedFirst.reserves).toEqual({ boost: 0n, comeback: 10n * SKR, vault: 10n * SKR });
    expect(recordedFirst.laterDue).toBe(10n * SKR);
  });
});

// ================================================================== the boost

describe('given a cohort with finishers at finalization (FR-037d)', () => {
  it.each([
    ['10 % of its own pool when the reserve holds more', 1_000n * SKR, 7n * SKR],
    ['the whole reserve when it holds less than 10 %', 3n * SKR, 3n * SKR],
  ])('boosts the pool by %s, before the shares are computed', async (_name, reserve, boost) => {
    const w = await world({ boostReserve: reserve });
    await cohortAt(w, 0);
    const finisher = await wallet(w);
    const quitter = await wallet(w);
    await join(w, finisher, 0, 1);
    await join(w, quitter, 0, 3);
    await clearAllDays(w, finisher, 0);

    w.h.setTime(endOf(0));
    const cohort = await cohortPda(w.league, 0);
    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 0, await forfeitPairs(cohort, [finisher.address, quitter.address]))],
      w.crank,
    );
    const result = await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 0)], w.crank);

    const settled = await w.h.readCohort(w.league, 0);
    expect(settled.boostReceived).toBe(boost);
    expect(decodeSettled(oneEvent(result, 'CohortSettled')).boost).toBe(boost);
    // The only finisher takes the whole boosted pool: 70 of the quitter's 100, plus the boost.
    expect(settled.sharePerTier[1]).toBe(70n * SKR + boost);
    expect(settled.roundingRemainder).toBe(0n);
    // The boost came out of the boost reserve; the quitter's pledge was booked at the count.
    expect(await reservesOf(w)).toEqual({
      boost: reserve - boost,
      comeback: 10n * SKR,
      vault: reserve - boost + 10n * SKR,
    });

    const before = await balanceOf(w, finisher);
    await w.act([await claimPayoutIx(finisher.address, w.mint, w.league, 0)], finisher);
    expect((await balanceOf(w, finisher)) - before).toBe(DEFAULT_CHIPS[1] + 70n * SKR + boost);
  });

  it('gives a cohort nobody forfeited no boost at all: every finisher gets exactly their stake back (FR-041)', async () => {
    const w = await world({ boostReserve: 50n * SKR, comebackReserve: 20n * SKR });
    await cohortAt(w, 0);
    const a = await wallet(w);
    const b = await wallet(w);
    await join(w, a, 0, 0);
    await join(w, b, 0, 3);
    for (let day = 0; day < DURATION; day += 1) {
      w.h.setTime(startOf(0) + day * DAY_LENGTH);
      for (const who of [a, b]) {
        await w.act([await submitDayIx(who.address, w.league, 0, day, new Uint8Array(32))], who);
      }
    }

    w.h.setTime(endOf(0));
    await settle(w, 0, [a, b]);
    const settled = await w.h.readCohort(w.league, 0);
    expect(settled.boostReceived).toBe(0n);
    expect(settled.sharePerTier).toEqual([0n, 0n, 0n, 0n]);
    expect(await reservesOf(w), 'the reserve is untouched').toEqual({ boost: 50n * SKR, comeback: 20n * SKR, vault: 70n * SKR });

    for (const [who, stake] of [[a, DEFAULT_CHIPS[0]], [b, DEFAULT_CHIPS[3]]] as const) {
      const before = await balanceOf(w, who);
      await w.act([await claimPayoutIx(who.address, w.mint, w.league, 0)], who);
      expect((await balanceOf(w, who)) - before).toBe(stake);
    }
  });

  it('sends the pool of a cohort nobody finished to the boost reserve, and a later cohort\'s boost draws on it (FR-037)', async () => {
    const w = await world();
    await cohortAt(w, 0);
    await cohortAt(w, 1);
    const quitters = [await wallet(w), await wallet(w)];
    const finisher = await wallet(w);
    const later = await wallet(w);
    for (const who of quitters) await join(w, who, 0, 3);
    await join(w, finisher, 1, 1);
    await join(w, later, 1, 3);

    // Cohort 0: both quit on day 0 and nobody finishes. Its 140 SKR pool is not split
    // between the fee accounts: every one of them goes to the boost reserve.
    w.h.setTime(deadlineOf(0, 0) + 1);
    const feesBefore = [w.h.tokenBalance(w.fees.operations), w.h.tokenBalance(w.fees.developer)];
    await record(w, 0, quitters);
    const feesAfterRecord = [w.h.tokenBalance(w.fees.operations), w.h.tokenBalance(w.fees.developer)];
    w.h.setTime(endOf(0));
    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 0, await forfeitPairs(await cohortPda(w.league, 0), quitters.map((q) => q.address)))],
      w.crank,
    );
    const empty = await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 0)], w.crank);
    const zero = await w.h.readCohort(w.league, 0);
    expect(zero.noFinisherBoost).toBe(140n * SKR);
    expect(zero.pool).toBe(0n);
    expect(decodeSettled(oneEvent(empty, 'CohortSettled')).noFinisherBoost).toBe(140n * SKR);
    expect(
      [w.h.tokenBalance(w.fees.operations), w.h.tokenBalance(w.fees.developer)],
      'finalizing a cohort nobody finished pays neither fee account',
    ).toEqual(feesAfterRecord);
    expect(feesAfterRecord[1]! - feesBefore[1]!).toBe(20n * SKR);
    expect(await reservesOf(w)).toEqual({ boost: 140n * SKR, comeback: 20n * SKR, vault: 160n * SKR });
    expect(w.h.tokenBalance(await vaultPda(await cohortPda(w.league, 0)))).toBe(0n);

    // Cohort 1: one finisher, one quitter. Its own pool is 70, so it draws min(7, 140) = 7.
    await clearAllDays(w, finisher, 1);
    w.h.setTime(endOf(1));
    await settle(w, 1, [finisher, later]);
    const one = await w.h.readCohort(w.league, 1);
    expect(one.boostReceived).toBe(7n * SKR);
    expect(one.sharePerTier[1]).toBe(77n * SKR);
    expect(await reservesOf(w)).toEqual({ boost: 133n * SKR, comeback: 30n * SKR, vault: 163n * SKR });
  });
});

// =============================================================== the comeback

describe('given a member who forfeited and came back (FR-037e)', () => {
  it('pays stake, share and the 10 % pledge to one who finishes the next round — though a boost had drained the boost reserve in between', async () => {
    // The boost reserve holds 5. Cohort 0's own pool is 280, so its boost would be 28
    // if it could: it takes the 5 and leaves the boost reserve empty. The pledges
    // were never the boost's to spend, so the comeback is still funded in full.
    const w = await world({ boostReserve: 5n * SKR });
    await cohortAt(w, 0);
    await cohortAt(w, 1);
    const comeback = await wallet(w);
    const quitters = [await wallet(w), await wallet(w), await wallet(w)];
    const steady = await wallet(w);
    const lateQuitter = await wallet(w);
    for (const who of [comeback, ...quitters]) await join(w, who, 0, 3);
    await join(w, steady, 0, 0);
    await join(w, lateQuitter, 1, 3);

    // Cohort 0: four tier-3 forfeits on day 0 — four pledges of 10 SKR.
    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [comeback, ...quitters]);
    // Free again, the member stakes on the next cohort straight away.
    await join(w, comeback, 1, 3);
    await clearAllDays(w, steady, 0);

    w.h.setTime(endOf(0));
    await settle(w, 0, [comeback, ...quitters, steady]);
    const zero = await w.h.readCohort(w.league, 0);
    expect(zero.boostReceived, 'min(10 % of 280, the 5 in the boost reserve)').toBe(5n * SKR);
    const progress = (await w.h.readProfile(comeback.address)).leagues[2]!;
    expect(progress.comebackStake).toBe(DEFAULT_CHIPS[3]);
    // Cohort 1 is the first to start after the missed deadline, and joining it was still open.
    expect(startOf(1)).toBeGreaterThan(deadlineOf(0, 0));
    expect([progress.comebackLeague, progress.comebackRound, progress.comebackLastRound]).toEqual([w.league, 1, 1]);
    expect(await reservesOf(w), 'the boost reserve is empty; all four pledges are intact').toEqual({
      boost: 0n,
      comeback: 40n * SKR,
      vault: 40n * SKR,
    });

    await clearAllDays(w, comeback, 1);
    w.h.setTime(endOf(1));
    await settle(w, 1, [comeback, lateQuitter]);

    const one = await w.h.readCohort(w.league, 1);
    const membership = await w.h.readMembership(await cohortPda(w.league, 1), comeback.address);
    expect(membership.comebackEntitled, '10 % of the 100 SKR forfeited').toBe(10n * SKR);
    expect(one.comebackOwed).toBe(10n * SKR);
    expect(one.comebackFunded, 'drawn in full').toBe(10n * SKR);
    expect(one.boostReceived, 'the boost reserve is empty').toBe(0n);
    expect((await w.h.readProfile(comeback.address)).leagues[2]!.comebackStake, 'used up').toBe(0n);
    // The late quitter's own pledge was booked at the count; the refund came out of the first four.
    expect(await reservesOf(w)).toEqual({ boost: 0n, comeback: 40n * SKR, vault: 40n * SKR });

    const before = await balanceOf(w, comeback);
    const claim = await w.act([await claimPayoutIx(comeback.address, w.mint, w.league, 1)], comeback);
    const paid = (await balanceOf(w, comeback)) - before;
    // Stake 100, the pool of cohort 1's one quitter, 70, and the refund, 10.
    expect(paid).toBe(DEFAULT_CHIPS[3] + 70n * SKR + 10n * SKR);
    expect(decodeClaimed(oneEvent(claim, 'PayoutClaimed'))).toEqual({
      wallet: comeback.address,
      amount: paid,
      comeback: 10n * SKR,
    });
    const after = await w.h.readCohort(w.league, 1);
    expect(after.comebackClaimed).toBe(10n * SKR);
    expect(w.h.tokenBalance(await vaultPda(await cohortPda(w.league, 1))), 'nothing left behind').toBe(0n);
  });

  it.each([
    ['finishes the round after the miss', 1, 10n * SKR],
    ['finishes only a cohort after the round', 2, 0n],
  ])('a member who %s: owed %s', async (_name, finished, owed) => {
    const w = await world();
    await cohortAt(w, 0);
    await cohortAt(w, 1);
    await cohortAt(w, 2);
    const member = await wallet(w);
    await join(w, member, 0, 3);

    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [member]);
    w.h.setTime(endOf(0));
    await settle(w, 0, [member]);
    // Nobody finished cohort 0: its 70 SKR pool is the boost reserve, the pledge is the comeback's.
    expect(await reservesOf(w)).toEqual({ boost: 70n * SKR, comeback: 10n * SKR, vault: 80n * SKR });

    if (finished === 2) {
      // The member sits the round out: cohort 1 settles empty.
      w.h.setTime(endOf(1));
      await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 1)], w.crank);
    }
    await join(w, member, finished, 3);
    await clearAllDays(w, member, finished);
    w.h.setTime(endOf(finished));
    await settle(w, finished, [member]);

    const cohort = await cohortPda(w.league, finished);
    expect((await w.h.readMembership(cohort, member.address)).comebackEntitled).toBe(owed);
    expect((await w.h.readCohort(w.league, finished)).comebackOwed).toBe(owed);
    expect((await w.h.readProfile(member.address)).leagues[2]!.comebackStake, 'closed either way').toBe(0n);
    // Made good: the pledge stays, owed, and finalization drew it. Let lapse: it joined the boost reserve.
    expect(await reservesOf(w)).toEqual(
      owed > 0n
        ? { boost: 70n * SKR, comeback: 0n, vault: 70n * SKR }
        : { boost: 80n * SKR, comeback: 0n, vault: 80n * SKR },
    );
    const before = await balanceOf(w, member);
    await w.act([await claimPayoutIx(member.address, w.mint, w.league, finished)], member);
    expect((await balanceOf(w, member)) - before).toBe(DEFAULT_CHIPS[3] + owed);
  });

  it('releases the old pledge and opens a new one when the member forfeits the comeback round too', async () => {
    const w = await world();
    await cohortAt(w, 0);
    await cohortAt(w, 1);
    const member = await wallet(w);
    await join(w, member, 0, 3);

    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [member]);
    await join(w, member, 1, 3);
    w.h.setTime(endOf(0));
    await settle(w, 0, [member]);
    expect((await w.h.readProfile(member.address)).leagues[2]!).toMatchObject({
      comebackStake: DEFAULT_CHIPS[3],
      comebackRound: 1,
      comebackLastRound: 1,
    });
    expect(await reservesOf(w)).toEqual({ boost: 70n * SKR, comeback: 10n * SKR, vault: 80n * SKR });

    // The round itself is forfeited on its first day.
    w.h.setTime(deadlineOf(1, 0) + 1);
    await record(w, 1, [member]);
    w.h.setTime(endOf(1));
    await settle(w, 1, [member]);

    // Cohort 1's forfeit replaced the comeback: the old 10 went to the boost reserve with
    // cohort 1's own 70 (nobody finished it), and a new 10 is pledged for the round after.
    const progress = (await w.h.readProfile(member.address)).leagues[2]!;
    expect(progress.comebackStake).toBe(DEFAULT_CHIPS[3]);
    const nextRound = Math.floor((deadlineOf(1, 0) - START) / CADENCE) + 1;
    expect(nextRound).toBe(2);
    expect([progress.comebackLeague, progress.comebackRound, progress.comebackLastRound]).toEqual([w.league, 2, 2]);
    expect(await reservesOf(w)).toEqual({ boost: 150n * SKR, comeback: 10n * SKR, vault: 160n * SKR });
    expect((await w.h.readProfile(member.address)).leagues[2]!.forfeited).toBe(2);
  });

  it('owes nothing for finishing a cohort of another league, however full that league\'s reserve', async () => {
    // Real league ids, so each league has a profile slot of its own (every test
    // league shares slot 2, which would blur exactly this distinction).
    const w = await world({ leagueId: 0 });
    await w.h.injectLeague(1, {
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      firstStart: BigInt(START),
      cadence: BigInt(CADENCE),
      joinLock: BigInt(JOIN_LOCK),
      nextIndex: 1,
      comebackReserve: 50n * SKR,
    });
    await cohortAt(w, 0, startOf(0), 0);
    await cohortAt(w, 0, startOf(1), 1);
    const member = await wallet(w);
    await join(w, member, 0, 3, 0);

    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [member], 0);
    await join(w, member, 0, 3, 1);
    w.h.setTime(endOf(0));
    await settle(w, 0, [member], 0);
    const open = (await w.h.readProfile(member.address)).leagues[0]!;
    expect(open.comebackStake).toBe(DEFAULT_CHIPS[3]);
    expect([open.comebackLeague, open.comebackRound, open.comebackLastRound]).toEqual([0, 1, 1]);

    await clearAllDays(w, member, 0, startOf(1), 1);
    w.h.setTime(endOf(1));
    await settle(w, 0, [member], 1);

    const other = await cohortPda(1, 0);
    expect((await w.h.readMembership(other, member.address)).comebackEntitled).toBe(0n);
    expect((await w.h.readCohort(1, 0)).comebackFunded).toBe(0n);
    expect((await reservesOf(w, 1)).comeback, 'league 1\'s reserve is untouched').toBe(50n * SKR);
    expect((await w.h.readProfile(member.address)).leagues[0]!.comebackStake, 'still open in league 0').toBe(DEFAULT_CHIPS[3]);
    expect((await reservesOf(w, 0)).comeback, 'and still pledged there').toBe(10n * SKR);

    const before = await balanceOf(w, member);
    await w.act([await claimPayoutIx(member.address, w.mint, 1, 0)], member);
    expect((await balanceOf(w, member)) - before, 'exactly the stake').toBe(DEFAULT_CHIPS[3]);
  });

  it('scales every refund down pro rata if the comeback reserve ever falls short, flooring each', async () => {
    // Not a state the program reaches — every pledge is booked when its forfeit is
    // counted — but the claim keeps the formula as defence: two comebacks owed 10
    // and 5 SKR against a reserve of 5 each get a third of their entitlement,
    // floored, and one base unit stays in the vault. The comeback records are
    // injected rather than played out, so this case checks figures, not invariants.
    const w = await world({ comebackReserve: 5n * SKR });
    await cohortAt(w, 0);
    const big = await wallet(w);
    const small = await wallet(w);
    const open = (stake: bigint) => ({
      cleared: 0, forfeited: 1, currentStreak: 0, longestStreak: 0, lastChanged: BigInt(START - 100),
      comebackStake: stake, comebackLeague: w.league, comebackRound: 0, comebackLastRound: 0,
    });
    const none = { ...open(0n), forfeited: 0, lastChanged: 0n, comebackLeague: 0 };
    await w.h.injectProfile(big.address, { leagues: [none, none, open(DEFAULT_CHIPS[3])] });
    await w.h.injectProfile(small.address, { leagues: [none, none, open(DEFAULT_CHIPS[2])] });

    for (const who of [big, small]) {
      await w.h.sendOrThrow([await joinCohortIx(who.address, w.mint, w.league, 0, 3)], who);
    }
    for (let day = 0; day < DURATION; day += 1) {
      w.h.setTime(startOf(0) + day * DAY_LENGTH);
      for (const who of [big, small]) {
        await w.h.sendOrThrow([await submitDayIx(who.address, w.league, 0, day, new Uint8Array(32))], who);
      }
    }
    w.h.setTime(endOf(0));
    const cohort = await cohortPda(w.league, 0);
    await w.h.sendOrThrow(
      [await settleCountIx(w.crank.address, w.fees, w.league, 0, await forfeitPairs(cohort, [big.address, small.address]))],
      w.crank,
    );
    await w.h.sendOrThrow([await settleFinalizeIx(w.crank.address, w.fees, w.league, 0)], w.crank);

    const settled = await w.h.readCohort(w.league, 0);
    expect(settled.comebackOwed).toBe(15n * SKR);
    expect(settled.comebackFunded, 'all the reserve had').toBe(5n * SKR);
    expect((await reservesOf(w)).comeback).toBe(0n);

    const refunds: bigint[] = [];
    for (const who of [big, small]) {
      const before = await balanceOf(w, who);
      const claim = await w.h.send([await claimPayoutIx(who.address, w.mint, w.league, 0)], who);
      expect(claim.ok, claim.logs.join('\n')).toBe(true);
      const refund = decodeClaimed(oneEvent(claim, 'PayoutClaimed')).comeback;
      expect((await balanceOf(w, who)) - before).toBe(DEFAULT_CHIPS[3] + refund);
      refunds.push(refund);
    }
    // floor(10 × 5 / 15) and floor(5 × 5 / 15), in SKR base units.
    expect(refunds).toEqual([3_333_333n, 1_666_666n]);
    const after = await w.h.readCohort(w.league, 0);
    expect(after.comebackClaimed).toBe(4_999_999n);
    expect(w.h.tokenBalance(await vaultPda(cohort)), 'one unit, short of the finishers to share it').toBe(1n);
    expect(cohortCustody(after)).toBe(1n);
  });

  it('sends a pledge straight to the boost reserve when a test league\'s shared slot already holds another league\'s', async () => {
    // Every test league shares profile slot 2, so one wallet holds one test-league
    // comeback at a time. A second league's forfeit opens none: its pledge is free
    // for that league's boosts, which only that league could release (FR-037e).
    const w = await world();
    const other = w.h.freshTestLeague().leagueId;
    await w.h.injectLeague(other, {
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
      firstStart: BigInt(START),
      cadence: BigInt(CADENCE),
      joinLock: BigInt(JOIN_LOCK),
      nextIndex: 1,
    });
    await cohortAt(w, 0);
    await cohortAt(w, 0, startOf(0) + 2 * DAY_LENGTH, other);
    const member = await wallet(w);
    await join(w, member, 0, 3);

    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [member]);
    // Free again: the second league's cohort is still open.
    await join(w, member, 0, 3, other);
    w.h.setTime(endOf(0));
    await settle(w, 0, [member]);
    const slot = (await w.h.readProfile(member.address)).leagues[2]!;
    expect([slot.comebackLeague, slot.comebackStake]).toEqual([w.league, DEFAULT_CHIPS[3]]);

    w.h.setTime(startOf(0) + 2 * DAY_LENGTH + DURATION * DAY_LENGTH);
    await settle(w, 0, [member], other);

    const after = (await w.h.readProfile(member.address)).leagues[2]!;
    expect([after.comebackLeague, after.comebackStake], 'the first league keeps the slot').toEqual([w.league, DEFAULT_CHIPS[3]]);
    expect(await reservesOf(w, other), 'the other league: its pool and its pledge, both for boosts').toEqual({
      boost: 80n * SKR,
      comeback: 0n,
      vault: 80n * SKR,
    });
    expect(await reservesOf(w)).toEqual({ boost: 70n * SKR, comeback: 10n * SKR, vault: 80n * SKR });
  });
});

// ====================================================== release_comeback

/** `ComebackReleased { wallet, league, round, amount }`. */
const decodeReleased = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return {
    wallet: getAddressDecoder().decode(bytes.slice(0, 32)),
    league: bytes[32],
    round: view.getUint32(33, true),
    amount: view.getBigUint64(37, true),
  };
};

describe('given a member who never comes back (FR-037e, release_comeback)', () => {
  const release = async (w: World, caller: KeyPairSigner, who: KeyPairSigner, round?: number) =>
    w.h.send([await releaseComebackIx(caller.address, w.league, who.address, round)], caller);

  /** The member joins cohort 0 and misses its first day; the forfeit is recorded, not yet counted. */
  const forfeitedAndSettled = async (w: World) => {
    await cohortAt(w, 0);
    const member = await wallet(w);
    await join(w, member, 0, 3);
    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [member]);
    return member;
  };

  it('moves the pledge to the boost reserve once the round has settled, for anyone who asks', async () => {
    const w = await world();
    const member = await forfeitedAndSettled(w);
    await cohortAt(w, 1);
    const stranger = await w.h.newWallet();

    // Nothing to release until the forfeit is counted: the pledge is still in cohort 0's vault.
    expectProgramError(await release(w, stranger, member, 1), 'NoComebackToRelease');

    w.h.setTime(endOf(0));
    await settle(w, 0, [member]);
    expect(await reservesOf(w)).toEqual({ boost: 70n * SKR, comeback: 10n * SKR, vault: 80n * SKR });

    // The round is cohort 1, opened and not yet settled: it can still be finished.
    expectProgramError(await release(w, stranger, member, 1), 'ComebackStillOpen');
    // The league has opened the round, so it has to be named — and be the right one.
    expectProgramError(await release(w, stranger, member), 'AccountMismatch');
    await cohortAt(w, 2);
    expectProgramError(await release(w, stranger, member, 2), 'AccountMismatch');
    expect(await reservesOf(w), 'the refusals moved nothing').toEqual({ boost: 70n * SKR, comeback: 10n * SKR, vault: 80n * SKR });

    // The round settles with nobody in it.
    w.h.setTime(endOf(1));
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 1)], w.crank);
    const before = await w.h.snapshot();
    const result = await release(w, stranger, member, 1);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    assertInvariants(await w.h.snapshot(), before);

    expect(decodeReleased(oneEvent(result, 'ComebackReleased'))).toEqual({
      wallet: member.address,
      league: w.league,
      round: 1,
      amount: 10n * SKR,
    });
    expect(await reservesOf(w)).toEqual({ boost: 80n * SKR, comeback: 0n, vault: 80n * SKR });
    const progress = (await w.h.readProfile(member.address)).leagues[2]!;
    expect([progress.comebackStake, progress.comebackLeague, progress.comebackRound, progress.comebackLastRound]).toEqual([0n, 0, 0, 0]);
    // Done once: the member has nothing left to release.
    expectProgramError(await release(w, stranger, member, 1), 'NoComebackToRelease');
  });

  it('lets a later cohort\'s boost spend the released pledge — and the member earns nothing for finishing it', async () => {
    const w = await world();
    const member = await forfeitedAndSettled(w);
    await cohortAt(w, 1);
    await cohortAt(w, 2);
    const finisher = await wallet(w);
    const quitter = await wallet(w);
    await join(w, finisher, 2, 0);
    await join(w, quitter, 2, 3);
    w.h.setTime(endOf(0));
    await settle(w, 0, [member]);
    w.h.setTime(endOf(1));
    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 1)], w.crank);
    await w.act([await releaseComebackIx(w.crank.address, w.league, member.address, 1)], w.crank);

    await clearAllDays(w, finisher, 2);
    w.h.setTime(endOf(2));
    await settle(w, 2, [finisher, quitter]);
    // Cohort 2's own pool is 70: a boost of 7, out of the 80 the nobody-finished pool and the release made.
    expect((await w.h.readCohort(w.league, 2)).boostReceived).toBe(7n * SKR);
    expect(await reservesOf(w)).toEqual({ boost: 73n * SKR, comeback: 10n * SKR, vault: 83n * SKR });
  });

  it('refuses a release for a round that has not opened until its first day is over, and allows it a second after', async () => {
    // The league has opened only cohort 0; the round is cohort 1, a week on.
    const w = await world({ nextIndex: 1 });
    const member = await forfeitedAndSettled(w);
    w.h.setTime(endOf(0));
    await settle(w, 0, [member]);
    const stranger = await w.h.newWallet();

    w.h.setTime(deadlineOf(1, 0));
    expectProgramError(await release(w, stranger, member), 'ComebackStillOpen');
    w.h.setTime(deadlineOf(1, 0) + 1);
    const before = await w.h.snapshot();
    const result = await release(w, stranger, member);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    assertInvariants(await w.h.snapshot(), before);
    expect(await reservesOf(w)).toEqual({ boost: 80n * SKR, comeback: 0n, vault: 80n * SKR });
  });

  it('refuses a profile with no open pledge in the league (6046)', async () => {
    const w = await world();
    await cohortAt(w, 0);
    const member = await wallet(w);
    await join(w, member, 0, 3);
    expectProgramError(await release(w, member, member), 'NoComebackToRelease');
  });
});

// ===================================== the golden vectors, through the program

describe('given the golden comeback_round vectors (fixtures/settlement-vectors.json)', () => {
  const vectors = (
    JSON.parse(vectorText) as {
      reserve_vectors: {
        comeback_round: { note: string; first_start: number; cadence: number; join_lock: number; missed_at: number; first: number; last: number }[];
      };
    }
  ).reserve_vectors.comeback_round;

  it('has them', () => {
    expect(vectors.length).toBeGreaterThanOrEqual(9);
  });

  it.each(vectors.filter((v) => v.missed_at >= v.first_start).map((v) => [v.note, v] as const))(
    'opens the same rounds when settle_count counts the forfeit: %s',
    async (_note, v) => {
      // Rebuild the miss from the vector: a one-day cohort of the league, whose day 0
      // ends the second at `missed_at`, counted by the real program.
      const h = await createHarness({ startTime: v.missed_at - 5 });
      const mint = (await generateKeyPairSigner()).address;
      h.injectMint(mint, null, 1_000_000_000n * SKR);
      const fees = await h.injectFeeDestinations(mint);
      await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });
      const league = h.freshTestLeague().leagueId;
      const index = Math.floor((v.missed_at - v.first_start) / v.cadence);
      const start = v.first_start + index * v.cadence;
      const dayLength = v.missed_at + 1 - start;
      await h.injectLeague(league, {
        durationDays: 1,
        dayLength: BigInt(dayLength),
        firstStart: BigInt(v.first_start),
        cadence: BigInt(v.cadence),
        joinLock: BigInt(v.join_lock),
        nextIndex: index + 1,
      });
      if (index > 0) {
        await h.injectCohort(league, index - 1, {
          settled: true,
          start: BigInt(v.first_start + (index - 1) * v.cadence),
          joinCutoff: BigInt(v.first_start + (index - 1) * v.cadence - v.join_lock),
          durationDays: 1,
          dayLength: BigInt(v.cadence),
        });
      }
      const cohort = await h.injectCohort(league, index, {
        start: BigInt(start),
        joinCutoff: BigInt(start - v.join_lock),
        durationDays: 1,
        dayLength: BigInt(dayLength),
        memberCount: 1,
        heldStake: DEFAULT_CHIPS[3],
      });
      const member = await h.newWallet();
      await h.injectMembership(cohort, member.address, { daysCompleted: 0 });
      await h.injectProfile(member.address);
      const crank = await h.newWallet();

      h.setTime(v.missed_at + 1);
      await h.sendOrThrow(
        [
          await settleCountIx(
            crank.address,
            fees,
            league,
            index,
            await forfeitPairs(cohort, [member.address]),
            index > 0 ? await cohortPda(league, index - 1) : undefined,
          ),
        ],
        crank,
      );
      const progress = (await h.readProfile(member.address)).leagues[2]!;
      expect([progress.comebackRound, progress.comebackLastRound]).toEqual([v.first, v.last]);
      expect([progress.comebackLeague, progress.comebackStake]).toEqual([league, DEFAULT_CHIPS[3]]);
    },
  );
});

// ====================================================== form and the reserve vault

const REAL_LEAGUE: CreateLeagueArgs = {
  leagueId: 0,
  durationDays: 7,
  repsPerDay: 25,
  exercise: 0,
  form: 1,
  firstStart: FIRST_START,
  dayLength: DAY,
  cadence: WEEK,
  joinLock: HOUR,
};

describe('when the admin creates a league (H35, constitution V)', () => {
  const setUp = async () => {
    const h = await createHarness();
    const mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, await mintAuthorityPda());
    // The default chip set is published, so a cohort can open straight away.
    await h.injectConfig({ skrMint: mint });
    return { h, mint, admin: h.upgradeAuthority };
  };

  const tokenAccount = (h: Harness, at: Address) => {
    const found = h.svm.getAccount(at);
    if (!found.exists) return null;
    const data = new Uint8Array(found.data);
    return {
      owner: found.programAddress,
      mint: getAddressDecoder().decode(data.slice(0, 32)),
      authority: getAddressDecoder().decode(data.slice(32, 64)),
      amount: new DataView(data.buffer, data.byteOffset).getBigUint64(64, true),
    };
  };

  it('creates its reserve vault: an empty token account of the configured mint, held by the league PDA, with empty reserves', async () => {
    const { h, mint, admin } = await setUp();
    await h.sendOrThrow([await createLeagueIx(admin.address, mint, REAL_LEAGUE)], admin);

    const vault = tokenAccount(h, await reservePda(0));
    expect(vault).toMatchObject({ mint, authority: await leaguePda(0), amount: 0n });
    const league = await h.readLeague(0);
    expect(league).toMatchObject({ form: 1, boostReserve: 0n, comebackReserve: 0n });
    expect(league.reserveVaultBump).toBe(await reserveBump(0));
  });

  it('rejects form 2 with InvalidForm (6013), creating neither the league nor its vault', async () => {
    const { h, mint, admin } = await setUp();
    expectProgramError(await h.send([await createLeagueIx(admin.address, mint, { ...REAL_LEAGUE, form: 2 })], admin), 'InvalidForm');
    expect(h.svm.getAccount(await leaguePda(0)).exists).toBe(false);
    expect(h.svm.getAccount(await reservePda(0)).exists).toBe(false);
  });

  it.each([
    ['the 7-day league as Hard', 0, 7, 0],
    ['the 21-day league as Easy', 1, 21, 1],
  ])('rejects %s with InvalidForm: the judged leagues\' forms are fixed (H35)', async (_name, leagueId, durationDays, form) => {
    const { h, mint, admin } = await setUp();
    expectProgramError(
      await h.send([await createLeagueIx(admin.address, mint, { ...REAL_LEAGUE, leagueId, durationDays, form })], admin),
      'InvalidForm',
    );
    expect(h.svm.getAccount(await leaguePda(leagueId)).exists).toBe(false);
  });

  it.each([
    ['knees allowed (Easy) into the 7-day league\'s cohort', 0, 7, 1],
    ['from the toes (Hard) into the 21-day league\'s cohort', 1, 21, 0],
  ])('has open_cohort copy the form, %s', async (_name, leagueId, durationDays, form) => {
    const { h, mint, admin } = await setUp();
    await h.sendOrThrow([await createLeagueIx(admin.address, mint, { ...REAL_LEAGUE, leagueId, durationDays, form })], admin);
    const crank = await h.newWallet();
    await h.sendOrThrow([await openCohortIx(crank.address, mint, leagueId, 0)], crank);
    expect((await h.readCohort(leagueId, 0)).form).toBe(form);
    const league = h.svm.getAccount(await leaguePda(leagueId));
    expect(league.exists && LeagueConfig.decode(new Uint8Array(league.data)).form).toBe(form);
  });
});
