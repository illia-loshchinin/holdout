/**
 * The randomised invariant suite, and spike S3. Task T076.
 *
 * Three runs, each asking a different question.
 *
 * 1. **A seeded random walk** fires plausible actions at a cohort — joins,
 *    leaves, sittings, missed days, forfeit pages repeated and shuffled,
 *    settlement pages the same, finalisation and claims — and asserts
 *    INV-1…INV-9 **after every single step**. Refusals are expected and
 *    ignored: a fuzz run's job is to find a state where an invariant breaks,
 *    not to keep every call legal. The seed is fixed, so a failure is
 *    reproducible.
 *
 * 2. **A 1,000-member cohort** through the whole loop, which is spike S3
 *    (research S3): does the paged design hold at the size the plan assumes,
 *    within one transaction's account limit and compute budget? Invariants are
 *    checked at every **stage** boundary rather than every step here, because
 *    a snapshot is O(members) and doing it per step would be O(members²) —
 *    the walk above is what covers per-step.
 *
 * 3. **One sequence replayed in two processing orders** — every forfeit
 *    recorded before the earlier cohorts settle, then the reverse — with every
 *    profile required to end identical (INV-9, FR-061a).
 */

import { generateKeyPairSigner, type Address, type KeyPairSigner } from '@solana/kit';
import { describe, expect, it } from 'vitest';

import { assertInvariants, assertSameProgress, type Snapshot } from './checker.js';
import { DEFAULT_CHIPS, FIRST_START, SKR, createHarness, type Harness } from '../helpers/harness.js';
import {
  claimPayoutIx,
  forfeitPairs,
  joinCohortIx,
  leaveCohortIx,
  recordForfeitsIx,
  settleCountIx,
  settleFinalizeIx,
  submitDayIx,
  type FeeDestinations,
} from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda } from '../helpers/pdas.js';

const DAY_LENGTH = 60;
const DURATION = 5;
const START = FIRST_START;
const END = START + DURATION * DAY_LENGTH;
/** One page (research R9). */
const PAGE = 10;

/** mulberry32 — a fixed seed, so a failure names a sequence you can re-run. */
const rng = (seed: number) => () => {
  seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};

interface Fixture {
  h: Harness;
  mint: Address;
  fees: FeeDestinations;
  crank: KeyPairSigner;
  cohort: Address;
  league: number;
}

/** A fresh test league with one cohort in its lobby. */
const setUp = async (
  options: { duration?: number; dayLength?: number; boostReserve?: bigint; comebackReserve?: bigint } = {},
): Promise<Fixture> => {
  const duration = options.duration ?? DURATION;
  const dayLength = options.dayLength ?? DAY_LENGTH;
  const h = await createHarness({ startTime: START - dayLength });
  const mint = (await generateKeyPairSigner()).address;
  h.injectMint(mint, null, 10_000_000_000n * SKR);

  const fees = await h.injectFeeDestinations(mint);
  await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });

  const league = h.freshTestLeague().leagueId;
  await h.injectLeague(league, {
    durationDays: duration,
    dayLength: BigInt(dayLength),
    firstStart: BigInt(START),
    nextIndex: 1,
    boostReserve: options.boostReserve ?? 0n,
    comebackReserve: options.comebackReserve ?? 0n,
  });
  const cohort = await h.injectCohort(league, 0, {
    start: BigInt(START),
    joinCutoff: BigInt(START - 10),
    durationDays: duration,
    dayLength: BigInt(dayLength),
  });
  return { h, mint, fees, crank: await h.newWallet(), cohort, league };
};

/** A funded wallet that can join and sit. */
const wallet = async (f: Fixture): Promise<KeyPairSigner> => {
  const who = await generateKeyPairSigner();
  await f.h.fund(who.address, 60_000_000n);
  f.h.injectTokenAccount(await associatedTokenPda(who.address, f.mint), f.mint, who.address, 4n * DEFAULT_CHIPS[3]!);
  return who;
};

const shuffled = <T>(items: readonly T[], next: () => number): T[] => {
  const out = [...items];
  for (let i = out.length - 1; i > 0; i -= 1) {
    const j = Math.floor(next() * (i + 1));
    [out[i], out[j]] = [out[j]!, out[i]!];
  }
  return out;
};

describe('the randomised invariant suite (T076, spike S3)', () => {
  it('holds INV-1…INV-10 after every step of a seeded random walk', async () => {
    // A league with something in reserve, so the finalize the walk ends on draws
    // a boost (FR-037d) — capped at 10 % of whatever pool the walk produced, or
    // at this odd figure, whichever is smaller — and credits its dues (FR-037c).
    const f = await setUp({ boostReserve: 7n * SKR + 3n, comebackReserve: 2n * SKR });
    const next = rng(20260920);
    const members: KeyPairSigner[] = [];
    for (let i = 0; i < 24; i += 1) members.push(await wallet(f));

    let previous: Snapshot = await f.h.snapshot();
    let steps = 0;
    /** One action, then the invariants. A refusal is a legal outcome. */
    const step = async (send: () => Promise<unknown>) => {
      await send();
      const current = await f.h.snapshot();
      assertInvariants(current, previous);
      previous = current;
      steps += 1;
    };

    // ---- the lobby: joins, and some of them changing their minds -----------
    const joined = new Set<Address>();
    for (const who of members) {
      const tier = Math.floor(next() * 4);
      await step(async () => {
        const result = await f.h.send([await joinCohortIx(who.address, f.mint, f.league, 0, tier)], who);
        if (result.ok) joined.add(who.address);
      });
      if (next() < 0.25) {
        await step(async () => {
          const result = await f.h.send([await leaveCohortIx(who.address, f.mint, f.league, 0)], who);
          if (result.ok) joined.delete(who.address);
        });
      }
    }
    expect(joined.size).toBeGreaterThan(8);

    // Someone has to clear it, so the finalize below takes the finisher path;
    // the no-finisher split is settlement.spec.ts's subject.
    const stayer = members.find((m) => joined.has(m.address))!;

    // ---- the run: some sit, some miss -------------------------------------
    const daysDone = new Map<Address, number>();
    for (let day = 0; day < DURATION; day += 1) {
      f.h.setTime(START + day * DAY_LENGTH + Math.floor(next() * (DAY_LENGTH - 1)));
      for (const who of shuffled(members, next)) {
        if (!joined.has(who.address)) continue;
        const sits = who.address === stayer.address || next() < 0.7;
        if (!sits) continue;
        const mine = daysDone.get(who.address) ?? 0;
        await step(async () => {
          const result = await f.h.send(
            [await submitDayIx(who.address, f.league, 0, mine, new Uint8Array(32).fill(day))],
            who,
          );
          if (result.ok) daysDone.set(who.address, mine + 1);
        });
      }

      // Mid-run forfeit pages, shuffled and sometimes repeated.
      if (next() < 0.5) {
        const page = shuffled([...joined], next).slice(0, PAGE);
        const repeats = next() < 0.4 ? 2 : 1;
        for (let r = 0; r < repeats; r += 1) {
          await step(async () =>
            f.h.send(
              [
                await recordForfeitsIx(
                  f.crank.address,
                  f.fees,
                  f.league,
                  0,
                  await forfeitPairs(f.cohort, page),
                ),
              ],
              f.crank,
            ),
          );
        }
      }
    }

    // ---- settling: pages out of order and repeated ------------------------
    f.h.setTime(END + 1);
    const everyone = [...joined];
    for (let round = 0; round < 3; round += 1) {
      for (let at = 0; at < everyone.length; at += PAGE) {
        const page = shuffled(everyone, next).slice(0, PAGE);
        await step(async () =>
          f.h.send(
            [
              await settleCountIx(
                f.crank.address,
                f.fees,
                f.league,
                0,
                await forfeitPairs(f.cohort, page),
              ),
            ],
            f.crank,
          ),
        );
      }
    }
    // A final sweep, so `counted` certainly reaches `member_count`.
    for (let at = 0; at < everyone.length; at += PAGE) {
      const page = everyone.slice(at, at + PAGE);
      await step(async () =>
        f.h.send(
          [
            await settleCountIx(
              f.crank.address,
              f.fees,
              f.league,
              0,
              await forfeitPairs(f.cohort, page),
            ),
          ],
          f.crank,
        ),
      );
    }

    const counted = await f.h.readCohort(f.league, 0);
    expect(counted.counted).toBe(counted.memberCount);

    // ---- finalise, twice, then claims in a random order -------------------
    await step(async () =>
      f.h.send([await settleFinalizeIx(f.crank.address, f.fees, f.league, 0)], f.crank),
    );
    await step(async () =>
      f.h.send([await settleFinalizeIx(f.crank.address, f.fees, f.league, 0)], f.crank),
    );
    expect((await f.h.readCohort(f.league, 0)).settled).toBe(true);

    for (const who of shuffled(members, next)) {
      const repeats = next() < 0.3 ? 2 : 1;
      for (let r = 0; r < repeats; r += 1) {
        await step(async () =>
          f.h.send([await claimPayoutIx(who.address, f.mint, f.league, 0)], who),
        );
      }
    }

    expect(steps, 'the walk actually walked').toBeGreaterThan(100);
    // Every finisher was paid, so nothing of theirs is left in the vault.
    const settled = await f.h.readCohort(f.league, 0);
    expect(settled.settled).toBe(true);
    expect(f.h.tokenBalance(await associatedTokenPda(stayer.address, f.mint))).toBeGreaterThan(
      3n * DEFAULT_CHIPS[3]!,
    );
  });

  it('runs a 1,000-member cohort through the whole loop (spike S3, AC-7)', async () => {
    const f = await setUp({ duration: 3 });
    const size = 1_000;
    const end = START + 3 * DAY_LENGTH;

    const members: KeyPairSigner[] = [];
    for (let i = 0; i < size; i += 1) members.push(await wallet(f));

    let previous: Snapshot = await f.h.snapshot();
    const stage = async (label: string) => {
      const current = await f.h.snapshot();
      try {
        assertInvariants(current, previous);
      } catch (error) {
        throw new Error(`invariant broke after ${label}: ${(error as Error).message}`);
      }
      previous = current;
    };

    for (const [at, who] of members.entries()) {
      await f.h.sendOrThrow([await joinCohortIx(who.address, f.mint, f.league, 0, at % 4)], who);
    }
    await stage('1,000 joins');
    expect((await f.h.readCohort(f.league, 0)).memberCount).toBe(size);

    // Three in four sit every day; the rest drop out along the way.
    const standing = members.filter((_m, at) => at % 4 !== 3);
    for (let day = 0; day < 3; day += 1) {
      f.h.setTime(START + day * DAY_LENGTH);
      for (const who of standing) {
        await f.h.sendOrThrow(
          [await submitDayIx(who.address, f.league, 0, day, new Uint8Array(32).fill(day))],
          who,
        );
      }
      await stage(`day ${day}`);
    }

    // Paged forfeits for the quarter that stopped, then paged counting.
    f.h.setTime(end);
    const quitters = members.filter((_m, at) => at % 4 === 3).map((m) => m.address);
    for (let at = 0; at < quitters.length; at += PAGE) {
      await f.h.sendOrThrow(
        [
          await recordForfeitsIx(
            f.crank.address,
            f.fees,
            f.league,
            0,
            await forfeitPairs(f.cohort, quitters.slice(at, at + PAGE)),
          ),
        ],
        f.crank,
      );
    }
    await stage('paged forfeits');

    const everyone = members.map((m) => m.address);
    for (let at = 0; at < everyone.length; at += PAGE) {
      await f.h.sendOrThrow(
        [
          await settleCountIx(
            f.crank.address,
            f.fees,
            f.league,
            0,
            await forfeitPairs(f.cohort, everyone.slice(at, at + PAGE)),
          ),
        ],
        f.crank,
      );
    }
    await stage('paged counting');

    const counted = await f.h.readCohort(f.league, 0);
    expect(counted.counted).toBe(size);
    expect(counted.finisherCount).toBe(standing.length);

    // Finalisation is constant-time: four shares describe a thousand members.
    await f.h.sendOrThrow(
      [await settleFinalizeIx(f.crank.address, f.fees, f.league, 0)],
      f.crank,
    );
    await stage('finalise');

    for (const who of standing) {
      await f.h.sendOrThrow([await claimPayoutIx(who.address, f.mint, f.league, 0)], who);
    }
    await stage('every claim');

    // To the unit: the vault is empty and the books agree (AC-3).
    const done = await f.h.readCohort(f.league, 0);
    expect(done.heldStake).toBe(0n);
    expect(done.pool).toBe(0n);
    expect(f.h.tokenBalance(await cohortVault(f))).toBe(0n);
  });

  it('ends with identical profiles whichever order the cohorts are processed in (INV-9, FR-061a)', async () => {
    /**
     * The same four wallets in both runs: `assertSameProgress` keys on the
     * wallet, so replaying the sequence with fresh keypairs would compare two
     * different sets of people and never match. Generated once here, funded
     * separately inside each run.
     */
    const cast = [
      await generateKeyPairSigner(),
      await generateKeyPairSigner(),
      await generateKeyPairSigner(),
      await generateKeyPairSigner(),
    ];

    /**
     * Two cohorts of one league, each with a member who clears and a member who
     * quits. `forfeitsFirst` decides whether every forfeit is recorded before
     * any cohort is settled, or the other way round.
     */
    const run = async (forfeitsFirst: boolean): Promise<Snapshot> => {
      const f = await setUp({ duration: 3 });
      const cadence = 6 * DAY_LENGTH;
      await f.h.injectLeague(f.league, {
        durationDays: 3,
        dayLength: BigInt(DAY_LENGTH),
        firstStart: BigInt(START),
        cadence: BigInt(cadence),
        joinLock: 10n,
        nextIndex: 2,
      });
      const second = await f.h.injectCohort(f.league, 1, {
        start: BigInt(START + cadence),
        joinCutoff: BigInt(START + cadence - 10),
        durationDays: 3,
        dayLength: BigInt(DAY_LENGTH),
      });
      const cohorts = [f.cohort, second];
      const startsAt = [START, START + cadence];
      const endsAt = [START + 3 * DAY_LENGTH, START + cadence + 3 * DAY_LENGTH];

      // Two members in each cohort: one clears, one quits — the same four
      // people in both runs.
      const people: KeyPairSigner[][] = [];
      for (const index of [0, 1]) {
        f.h.setTime(startsAt[index]! - 20);
        const pair = [cast[index * 2]!, cast[index * 2 + 1]!];
        for (const who of pair) {
          await f.h.fund(who.address, 60_000_000n);
          f.h.injectTokenAccount(
            await associatedTokenPda(who.address, f.mint),
            f.mint,
            who.address,
            4n * DEFAULT_CHIPS[3]!,
          );
          await f.h.sendOrThrow([await joinCohortIx(who.address, f.mint, f.league, index, 1)], who);
        }
        for (let day = 0; day < 3; day += 1) {
          f.h.setTime(startsAt[index]! + day * DAY_LENGTH);
          await f.h.sendOrThrow(
            [await submitDayIx(pair[0]!.address, f.league, index, day, new Uint8Array(32))],
            pair[0]!,
          );
        }
        people.push(pair);
      }

      const recordFor = async (index: number) =>
        f.h.sendOrThrow(
          [
            await recordForfeitsIx(
              f.crank.address,
              f.fees,
              f.league,
              index,
              await forfeitPairs(cohorts[index]!, [people[index]![1]!.address]),
            ),
          ],
          f.crank,
        );

      const settleOne = async (index: number) => {
        await f.h.sendOrThrow(
          [
            await settleCountIx(
              f.crank.address,
              f.fees,
              f.league,
              index,
              await forfeitPairs(cohorts[index]!, people[index]!.map((p) => p.address)),
              index > 0 ? cohorts[index - 1] : undefined,
            ),
          ],
          f.crank,
        );
        await f.h.sendOrThrow(
          [await settleFinalizeIx(f.crank.address, f.fees, f.league, index)],
          f.crank,
        );
      };

      f.h.setTime(endsAt[1]!);
      if (forfeitsFirst) {
        await recordFor(0);
        await recordFor(1);
        await settleOne(0);
        await settleOne(1);
      } else {
        await settleOne(0);
        await recordFor(1);
        await settleOne(1);
      }

      return f.h.snapshot();
    };

    const forfeitsFirst = await run(true);
    const settleFirst = await run(false);

    // Wallets differ between runs, so compare the progress figures themselves.
    assertSameProgress(forfeitsFirst, settleFirst);
  });
});

/** The current cohort's vault. */
const cohortVault = async (f: Fixture): Promise<Address> => {
  const { vaultPda } = await import('../helpers/pdas.js');
  return vaultPda(await cohortPda(f.league, 0));
};
