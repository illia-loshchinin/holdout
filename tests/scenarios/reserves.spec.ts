/**
 * League reserves, the two fee destinations, and Easy and Hard (H35).
 *
 * What this file holds the contract to:
 *
 *  - **A forfeit splits five ways** (FR-027): 70 % to the pool, 10 % and 5 % held
 *    in the cohort's vault as `boost_due` / `comeback_due`, 5 % to the developer
 *    destination and 10 % — plus whatever the floors leave — to operations.
 *  - **Reserves are credited only at finalization, in cohort order** (FR-037c,
 *    INV-10): what a cohort draws can never depend on when the crank recorded
 *    somebody else's forfeit.
 *  - **The prize boost** (FR-037d): `min(20 % of the cohort's own pool, the
 *    boost reserve)`, drawn before the shares — and nothing for a cohort with no
 *    forfeits, so "everyone finishes, exactly your stake back" still holds.
 *  - **The comeback refund** (FR-037e): forfeit, then finish a later cohort of the
 *    same league that starts within 14 days of the missed deadline, and the claim
 *    pays 10 % of the forfeited stake on top — scaled down pro rata when the
 *    reserve is short.
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
/** 14 of the forfeited cohort's days (FR-037e). */
const WINDOW = 14 * DAY_LENGTH;

const HERE = dirname(fileURLToPath(import.meta.url));

/** The pure-math golden vectors the Rust maths replays too (fixtures/settlement-vectors.json). */
const vectorText = readFileSync(resolve(HERE, '../../fixtures/settlement-vectors.json'), 'utf8');
const splitVectors = (
  JSON.parse(vectorText.replace(/:\s*(\d{16,})/g, ': "$1"')) as {
    reserve_vectors: { forfeit_split: Record<'stake' | 'pool' | 'boost' | 'comeback' | 'developer' | 'operations', number | string>[] };
  }
).reserve_vectors.forfeit_split;
const vectorFor = (stake: bigint) => {
  const v = splitVectors.find((x) => BigInt(x.stake) === stake);
  if (!v) throw new Error(`no forfeit_split vector for ${stake}`);
  return {
    pool: BigInt(v.pool),
    boost: BigInt(v.boost),
    comeback: BigInt(v.comeback),
    developer: BigInt(v.developer),
    operations: BigInt(v.operations),
  };
};

/** `CohortSettled { cohort, finishers, total_finisher_stake, share_per_tier, rounding_remainder, boost, comeback_funded }`. */
const decodeSettled = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return { boost: view.getBigUint64(84, true), comebackFunded: view.getBigUint64(92, true) };
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
  it('lands 70 % in the pool, 10 % and 5 % as the cohort\'s dues, 5 % with the developer and the rest with operations', async () => {
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
    expect(cohort.boostDue).toBe(sum((p) => p.boost));
    expect(cohort.comebackDue).toBe(sum((p) => p.comeback));
    expect(w.h.tokenBalance(w.fees.developer)).toBe(sum((p) => p.developer));
    expect(w.h.tokenBalance(w.fees.operations)).toBe(sum((p) => p.operations));
    // Every unit of every stake is somewhere: in the vault, or with one of the two.
    const total = chips.reduce((s, c) => s + c, 0n);
    expect(w.h.tokenBalance(await vaultPda(await cohortPda(w.league, 0)))).toBe(cohortCustody(cohort));
    expect(cohortCustody(cohort) + sum((p) => p.developer + p.operations)).toBe(total);
    // …and none of it has reached the league yet (FR-037c).
    expect(await reservesOf(w)).toEqual({ boost: 0n, comeback: 0n, vault: 0n });
  });
});

// ============================================================ credited at finalize

describe('given a league whose cohorts settle in order (FR-037c, INV-10)', () => {
  it('leaves the league untouched through recording and counting, and credits exactly the dues at finalization', async () => {
    const w = await world();
    await cohortAt(w, 0);
    const finisher = await wallet(w);
    const quitter = await wallet(w);
    await join(w, finisher, 0, 0);
    await join(w, quitter, 0, 3);

    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [quitter]);
    expect(await reservesOf(w)).toEqual({ boost: 0n, comeback: 0n, vault: 0n });

    await clearAllDays(w, finisher, 0);
    w.h.setTime(endOf(0));
    await w.act(
      [await settleCountIx(w.crank.address, w.fees, w.league, 0, await forfeitPairs(await cohortPda(w.league, 0), [finisher.address, quitter.address]))],
      w.crank,
    );
    expect(await reservesOf(w), 'counting moves nothing into the reserve either').toEqual({ boost: 0n, comeback: 0n, vault: 0n });

    await w.act([await settleFinalizeIx(w.crank.address, w.fees, w.league, 0)], w.crank);
    expect(await reservesOf(w)).toEqual({ boost: 10n * SKR, comeback: 5n * SKR, vault: 15n * SKR });
    const settled = await w.h.readCohort(w.league, 0);
    expect([settled.boostDue, settled.comebackDue]).toEqual([0n, 0n]);
    expect(settled.boostReceived, 'an empty reserve boosts nothing').toBe(0n);
  });

  it('does not let a forfeit recorded in a running later cohort change an earlier cohort\'s boost, whichever runs first', async () => {
    /**
     * Cohort 0: one finisher, one quitter (pool 70, so 20 % is 14), against a
     * boost reserve of 5. Cohort 1 is running by the time cohort 0 settles, and
     * one of its members has missed day 0. Crediting cohort 1's 10 SKR boost
     * share when it is recorded would let the crank lift cohort 0's boost from 5
     * to 14 just by recording first.
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
        laterDues: [one.boostDue, one.comebackDue],
      };
    };

    const recordedFirst = await run(true);
    const settledFirst = await run(false);
    expect(recordedFirst.boost).toBe(5n * SKR);
    expect(recordedFirst).toEqual(settledFirst);
    // Cohort 0 drew 5 and credited its own 10 + 5; cohort 1's shares wait for it.
    expect(recordedFirst.reserves).toEqual({ boost: 10n * SKR, comeback: 5n * SKR, vault: 15n * SKR });
    expect(recordedFirst.laterDues).toEqual([10n * SKR, 5n * SKR]);
  });
});

// ================================================================== the boost

describe('given a cohort with finishers at finalization (FR-037d)', () => {
  it.each([
    ['20 % of its own pool when the reserve holds more', 1_000n * SKR, 14n * SKR],
    ['the whole reserve when it holds less than 20 %', 3n * SKR, 3n * SKR],
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
    // Drawn first, then this cohort's own 10 + 5 credited.
    expect(await reservesOf(w)).toEqual({
      boost: reserve - boost + 10n * SKR,
      comeback: 5n * SKR,
      vault: reserve - boost + 15n * SKR,
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
});

// =============================================================== the comeback

describe('given a member who forfeited and came back (FR-037e)', () => {
  it('pays stake, share and 10 % of the forfeited stake to one who finishes the next cohort of the league within 14 days', async () => {
    const w = await world();
    await cohortAt(w, 0);
    await cohortAt(w, 1);
    const comeback = await wallet(w);
    const quitters = [await wallet(w), await wallet(w)];
    const steady = await wallet(w);
    const lateQuitter = await wallet(w);
    for (const who of [comeback, ...quitters]) await join(w, who, 0, 3);
    await join(w, steady, 0, 0);
    await join(w, lateQuitter, 1, 3);

    // Cohort 0: three tier-3 forfeits on day 0 — 30 SKR of boost and 15 of comeback due.
    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [comeback, ...quitters]);
    // Free again, the member stakes on the next cohort straight away.
    await join(w, comeback, 1, 3);
    await clearAllDays(w, steady, 0);

    w.h.setTime(endOf(0));
    await settle(w, 0, [comeback, ...quitters, steady]);
    const progress = (await w.h.readProfile(comeback.address)).leagues[2]!;
    expect(progress.comebackStake).toBe(DEFAULT_CHIPS[3]);
    expect(progress.comebackFrom).toBe(BigInt(deadlineOf(0, 0)));
    expect(progress.comebackUntil).toBe(BigInt(deadlineOf(0, 0) + WINDOW));
    expect(await reservesOf(w)).toEqual({ boost: 30n * SKR, comeback: 15n * SKR, vault: 45n * SKR });

    // Cohort 1 starts inside the window: 840 s after a deadline the window keeps open for 840.
    expect(startOf(1)).toBeGreaterThan(deadlineOf(0, 0));
    expect(startOf(1)).toBeLessThanOrEqual(deadlineOf(0, 0) + WINDOW);
    await clearAllDays(w, comeback, 1);
    w.h.setTime(endOf(1));
    await settle(w, 1, [comeback, lateQuitter]);

    const one = await w.h.readCohort(w.league, 1);
    const membership = await w.h.readMembership(await cohortPda(w.league, 1), comeback.address);
    expect(membership.comebackEntitled, '10 % of the 100 SKR forfeited').toBe(10n * SKR);
    expect(one.comebackOwed).toBe(10n * SKR);
    expect(one.comebackFunded, 'the reserve held 15').toBe(10n * SKR);
    expect(one.boostReceived, 'min(20 % of 70, 30)').toBe(14n * SKR);
    expect((await w.h.readProfile(comeback.address)).leagues[2]!.comebackStake, 'used up').toBe(0n);
    expect(await reservesOf(w)).toEqual({
      boost: 30n * SKR - 14n * SKR + 10n * SKR,
      comeback: 15n * SKR - 10n * SKR + 5n * SKR,
      vault: 36n * SKR,
    });

    const before = await balanceOf(w, comeback);
    const claim = await w.act([await claimPayoutIx(comeback.address, w.mint, w.league, 1)], comeback);
    const paid = (await balanceOf(w, comeback)) - before;
    // Stake 100, the whole boosted pool 70 + 14, and the refund 10.
    expect(paid).toBe(DEFAULT_CHIPS[3] + 84n * SKR + 10n * SKR);
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
    ['counts a cohort starting on the last second of the window', 0, 10n * SKR],
    ['owes nothing for one starting a second after it', 1, 0n],
  ])('%s', async (_name, late, owed) => {
    const w = await world();
    await cohortAt(w, 0);
    const nextStart = deadlineOf(0, 0) + WINDOW + late;
    await cohortAt(w, 1, nextStart);
    const member = await wallet(w);
    await join(w, member, 0, 3);

    w.h.setTime(deadlineOf(0, 0) + 1);
    await record(w, 0, [member]);
    await join(w, member, 1, 3);
    w.h.setTime(endOf(0));
    await settle(w, 0, [member]);

    await clearAllDays(w, member, 1, nextStart);
    w.h.setTime(nextStart + DURATION * DAY_LENGTH);
    await settle(w, 1, [member]);

    const cohort = await cohortPda(w.league, 1);
    expect((await w.h.readMembership(cohort, member.address)).comebackEntitled).toBe(owed);
    expect((await w.h.readCohort(w.league, 1)).comebackOwed).toBe(owed);
    expect((await w.h.readProfile(member.address)).leagues[2]!.comebackStake).toBe(owed > 0n ? 0n : DEFAULT_CHIPS[3]);
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
    expect((await w.h.readProfile(member.address)).leagues[0]!.comebackStake).toBe(DEFAULT_CHIPS[3]);

    await clearAllDays(w, member, 0, startOf(1), 1);
    w.h.setTime(endOf(1));
    await settle(w, 0, [member], 1);

    const other = await cohortPda(1, 0);
    expect((await w.h.readMembership(other, member.address)).comebackEntitled).toBe(0n);
    expect((await w.h.readCohort(1, 0)).comebackFunded).toBe(0n);
    expect((await reservesOf(w, 1)).comeback, 'league 1\'s reserve is untouched').toBe(50n * SKR);
    expect((await w.h.readProfile(member.address)).leagues[0]!.comebackStake, 'still open in league 0').toBe(DEFAULT_CHIPS[3]);

    const before = await balanceOf(w, member);
    await w.act([await claimPayoutIx(member.address, w.mint, 1, 0)], member);
    expect((await balanceOf(w, member)) - before, 'exactly the stake').toBe(DEFAULT_CHIPS[3]);
  });

  it('scales every refund down pro rata when the comeback reserve is short, flooring each', async () => {
    // Two comebacks owed 10 and 5 SKR against a reserve of 5: each gets a third
    // of its entitlement, floored, and one base unit stays in the vault (H35's
    // known limit). The forfeits are injected as counted comeback records rather
    // than played out, so this case checks figures, not invariants.
    const w = await world({ comebackReserve: 5n * SKR });
    await cohortAt(w, 0);
    const big = await wallet(w);
    const small = await wallet(w);
    const open = (stake: bigint) => ({
      cleared: 0, forfeited: 1, currentStreak: 0, longestStreak: 0, lastChanged: BigInt(START - 100),
      comebackStake: stake, comebackFrom: BigInt(START - 100), comebackUntil: BigInt(START + 10_000),
    });
    const none = { ...open(0n), forfeited: 0, lastChanged: 0n, comebackFrom: 0n, comebackUntil: 0n };
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
