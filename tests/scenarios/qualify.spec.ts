/**
 * Qualifying for the 21-day league. Task T083.
 *
 * The acceptance criterion this file exists for is AC-1: **the flag is the only
 * difference between this build and an enforced ladder**. So the unlock is
 * recorded on every 7-day clear whether or not the gate is on (FR-003a,
 * FR-004), and flipping `ladder_gate_enabled` on refuses an unqualified 21-day
 * join. Nothing else changes — which is what makes the known weakness in the
 * README ("both leagues open from first launch") a flag rather than a rewrite.
 *
 * `qualified` is also **never revoked**: a later forfeit ends a streak, not the
 * unlock.
 *
 * The two cohorts it needs are consecutive in the real 7-day league and are
 * settled in that order, so `PreviousCohortUnsettled` never fires by accident
 * (T016, FR-061a).
 */

import { generateKeyPairSigner, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  DEFAULT_CHIPS,
  FIRST_START,
  SKR,
  createHarness,
  expectProgramError,
  type Harness,
} from '../helpers/harness.js';
import {
  forfeitPairs,
  joinCohortIx,
  setFlagsIx,
  settleCountIx,
  settleFinalizeIx,
  submitDayIx,
  type FeeDestinations,
} from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda, profilePda } from '../helpers/pdas.js';

const LEAGUE_7D = 0;
const LEAGUE_21D = 1;
const DAY = 86_400;
const HOUR = 3_600;
const WEEK = 7 * DAY;
const DURATION = 7;
const startOf = (index: number) => FIRST_START + index * WEEK;
const endOf = (index: number) => startOf(index) + DURATION * DAY;
const NO_KEY = '11111111111111111111111111111111' as Address;

describe('the 21-day unlock (AC-1, FR-003a, FR-004)', () => {
  let h: Harness;
  let mint: Address;
  let fees: FeeDestinations;
  let crank: KeyPairSigner;
  let member: KeyPairSigner;

  const timing = { durationDays: DURATION, dayLength: BigInt(DAY), cadence: BigInt(WEEK), joinLock: BigInt(HOUR) };

  beforeEach(async () => {
    h = await createHarness({ startTime: FIRST_START - DAY });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 10_000_000n * SKR);

    fees = await h.injectFeeDestinations(mint);
    await h.injectConfig({ skrMint: mint, feeDestination: fees.operations, developerDestination: fees.developer });

    await h.injectLeague(LEAGUE_7D, { ...timing, firstStart: BigInt(FIRST_START), nextIndex: 2 });
    await h.injectLeague(LEAGUE_21D, { ...timing, durationDays: 21, firstStart: BigInt(FIRST_START), nextIndex: 2 });
    for (const index of [0, 1]) {
      await h.injectCohort(LEAGUE_7D, index, {
        start: BigInt(startOf(index)),
        joinCutoff: BigInt(startOf(index) - HOUR),
        durationDays: DURATION,
        dayLength: BigInt(DAY),
      });
    }

    crank = await h.newWallet();
    member = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(member.address, mint), mint, member.address, 10n * DEFAULT_CHIPS[3]);
  });

  const clearSevenDays = async (index: number, who: KeyPairSigner) => {
    for (let day = 0; day < DURATION; day += 1) {
      h.setTime(startOf(index) + day * DAY);
      await h.sendOrThrow(
        [await submitDayIx(who.address, LEAGUE_7D, index, day, new Uint8Array(32).fill(day))],
        who,
      );
    }
  };

  /**
   * Another wallet that joins cohort `index` and clears it, so the cohort has a
   * finisher: these cases are about one member's profile, not about how a
   * no-finisher pool settles.
   */
  const finisherIn = async (index: number): Promise<KeyPairSigner> => {
    const who = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(who.address, mint), mint, who.address, DEFAULT_CHIPS[3]);
    await h.sendOrThrow([await joinCohortIx(who.address, mint, LEAGUE_7D, index, 0)], who);
    await clearSevenDays(index, who);
    return who;
  };

  const settle7d = async (index: number, wallets: readonly Address[]) => {
    const cohort = await cohortPda(LEAGUE_7D, index);
    await h.sendOrThrow(
      [
        await settleCountIx(
          crank.address,
          fees,
          LEAGUE_7D,
          index,
          await forfeitPairs(cohort, wallets),
          index > 0 ? await cohortPda(LEAGUE_7D, index - 1) : undefined,
        ),
      ],
      crank,
    );
    await h.sendOrThrow(
      [await settleFinalizeIx(crank.address, fees, LEAGUE_7D, index)],
      crank,
    );
  };

  it('records the unlock on a 7-day clear, with the gate off (FR-003a)', async () => {
    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE_7D, 0, 1)], member);
    expect((await h.readProfile(member.address)).qualified).toBe(false);

    await clearSevenDays(0, member);
    h.setTime(endOf(0));
    await settle7d(0, [member.address]);

    const profile = await h.readProfile(member.address);
    expect(profile.qualified, 'computed and recorded even though nothing enforces it').toBe(true);
    expect(profile.leagues[LEAGUE_7D]!.cleared).toBe(1);
  });

  it('does not record it for a member who forfeited the 7-day cohort', async () => {
    const quitter = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(quitter.address, mint), mint, quitter.address, DEFAULT_CHIPS[3]);
    await h.sendOrThrow([await joinCohortIx(quitter.address, mint, LEAGUE_7D, 0, 1)], quitter);
    const finisher = await finisherIn(0);

    h.setTime(endOf(0));
    await settle7d(0, [quitter.address, finisher.address]);

    expect((await h.readProfile(quitter.address)).qualified).toBe(false);
    expect((await h.readProfile(finisher.address)).qualified, 'the one who cleared it did').toBe(true);
  });

  it('never revokes it: a later forfeit ends a streak, not the unlock (FR-004)', async () => {
    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE_7D, 0, 1)], member);
    await clearSevenDays(0, member);
    h.setTime(endOf(0));
    await settle7d(0, [member.address]);
    expect((await h.readProfile(member.address)).qualified).toBe(true);

    // The next 7-day cohort: joined, and abandoned.
    h.setTime(startOf(1) - 2 * HOUR);
    await h.sendOrThrow(
      [await joinCohortIx(member.address, mint, LEAGUE_7D, 1, 0, await cohortPda(LEAGUE_7D, 0))],
      member,
    );
    const finisher = await finisherIn(1);
    h.setTime(endOf(1));
    await settle7d(1, [member.address, finisher.address]);

    const profile = await h.readProfile(member.address);
    expect(profile.qualified, 'the unlock is permanent').toBe(true);
    expect(profile.leagues[LEAGUE_7D]!.currentStreak, 'the streak is not').toBe(0);
    expect(profile.leagues[LEAGUE_7D]!.forfeited).toBe(1);
  });

  it('opens both leagues while the gate is off — this build\'s known weakness (FR-003)', async () => {
    // A wallet that has cleared nothing at all walks into the 21-day league.
    await h.injectCohort(LEAGUE_21D, 0, {
      start: BigInt(startOf(0)),
      joinCutoff: BigInt(startOf(0) - HOUR),
      durationDays: 21,
      dayLength: BigInt(DAY),
    });
    expect(h.svm.getAccount(await cohortPda(LEAGUE_21D, 0)).exists).toBe(true);

    const result = await h.send([await joinCohortIx(member.address, mint, LEAGUE_21D, 0, 1)], member);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect((await h.readProfile(member.address)).qualified).toBe(false);
    expect((await h.readProfile(member.address)).pending.cohort).toBe(await cohortPda(LEAGUE_21D, 0));
  });

  it('flips the gates through set_flags, admin only', async () => {
    const admin = h.upgradeAuthority;
    expect((await h.readConfig()).ladderGateEnabled).toBe(false);

    const stranger = await h.newWallet();
    expectProgramError(
      await h.send([await setFlagsIx(stranger.address, true, true)], stranger),
      'NotAdmin',
    );
    expect((await h.readConfig()).ladderGateEnabled, 'a stranger changes nothing').toBe(false);

    await h.sendOrThrow([await setFlagsIx(admin.address, true, false)], admin);
    let config = await h.readConfig();
    expect(config.ladderGateEnabled).toBe(true);
    expect(config.genesisGateEnabled).toBe(false);

    // And back off again, which is what makes it a flag and not a migration.
    await h.sendOrThrow([await setFlagsIx(admin.address, false, false)], admin);
    config = await h.readConfig();
    expect(config.ladderGateEnabled).toBe(false);
    expect(config.genesisGateEnabled).toBe(false);
  });

  it('refuses an unqualified 21-day join with the gate on — the flag is the only difference (AC-1)', async () => {
    // Flipped through the real instruction: AC-1 is a claim about this flag, so
    // nothing here injects it.
    await h.sendOrThrow([await setFlagsIx(h.upgradeAuthority.address, true, false)], h.upgradeAuthority);
    await h.injectCohort(LEAGUE_21D, 0, {
      start: BigInt(startOf(0)),
      joinCutoff: BigInt(startOf(0) - HOUR),
      durationDays: 21,
      dayLength: BigInt(DAY),
    });

    expectProgramError(
      await h.send([await joinCohortIx(member.address, mint, LEAGUE_21D, 0, 1)], member),
      'LadderGateClosed',
    );
    // The refusal rolls the whole transaction back, so not even the Profile the
    // join would have created is left behind.
    expect(h.svm.getAccount(await profilePda(member.address)).exists).toBe(false);
    expect((await h.readCohort(LEAGUE_21D, 0)).memberCount).toBe(0);
    expect(NO_KEY).toBeTruthy();
  });

  it('lets a qualified wallet through the gate, and never closes the 7-day league (FR-002)', async () => {
    // Clear a 7-day cohort with the gate off, exactly as this build ships…
    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE_7D, 0, 1)], member);
    await clearSevenDays(0, member);
    h.setTime(endOf(0));
    await settle7d(0, [member.address]);
    expect((await h.readProfile(member.address)).qualified).toBe(true);

    // …then flip the flag on and prove the same wallet is admitted.
    await h.sendOrThrow([await setFlagsIx(h.upgradeAuthority.address, true, false)], h.upgradeAuthority);
    await h.injectCohort(LEAGUE_21D, 1, {
      start: BigInt(startOf(1)),
      joinCutoff: BigInt(startOf(1) - HOUR),
      durationDays: 21,
      dayLength: BigInt(DAY),
    });
    h.setTime(startOf(1) - 2 * HOUR);
    const admitted = await h.send(
      [await joinCohortIx(member.address, mint, LEAGUE_21D, 1, 0, await cohortPda(LEAGUE_7D, 0))],
      member,
    );
    expect(admitted.ok, admitted.logs.join('\n')).toBe(true);

    // And the 7-day league takes anyone, gate or no gate: it never closes.
    const newcomer = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(newcomer.address, mint), mint, newcomer.address, DEFAULT_CHIPS[3]);
    const entry = await h.send([await joinCohortIx(newcomer.address, mint, LEAGUE_7D, 1, 0)], newcomer);
    expect(entry.ok, entry.logs.join('\n')).toBe(true);
    expect((await h.readProfile(newcomer.address)).qualified).toBe(false);
  });

  it('does not qualify anyone for clearing a test league', async () => {
    const league = h.freshTestLeague().leagueId;
    await h.injectLeague(league, { durationDays: 2, dayLength: 60n, nextIndex: 1 });
    const cohort = await h.injectCohort(league, 0, {
      start: BigInt(FIRST_START),
      joinCutoff: BigInt(FIRST_START - 10),
      durationDays: 2,
      dayLength: 60n,
    });

    await h.sendOrThrow([await joinCohortIx(member.address, mint, league, 0, 0)], member);
    for (const day of [0, 1]) {
      h.setTime(FIRST_START + day * 60);
      await h.sendOrThrow(
        [await submitDayIx(member.address, league, 0, day, new Uint8Array(32))],
        member,
      );
    }
    h.setTime(FIRST_START + 2 * 60);
    await h.sendOrThrow(
      [
        await settleCountIx(
          crank.address,
          fees,
          league,
          0,
          await forfeitPairs(cohort, [member.address]),
        ),
      ],
      crank,
    );

    expect((await h.readProfile(member.address)).qualified, 'only league 0 unlocks it').toBe(false);
    expect((await h.readProfile(member.address)).leagues[2]!.cleared).toBe(1);
  });
});
