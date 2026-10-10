/**
 * Both leagues are open to every member, always. Task T138.
 *
 * The 7-day and 21-day leagues are two separate products for different people,
 * not rungs of a ladder (FR-003, decided 2026-10-10). So a wallet with no 7-day
 * history joins a 21-day cohort, and nothing a member does in one league —
 * clearing it or forfeiting it — changes whether the other takes them (AC-1).
 * There is no gate to flip: `set_flags` carries the genesis gate alone.
 *
 * The two 7-day cohorts used here are consecutive in the real 7-day league and
 * are settled in that order, so `PreviousCohortUnsettled` never fires by
 * accident (T016, FR-061a).
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
import { associatedTokenPda, cohortPda } from '../helpers/pdas.js';

const LEAGUE_7D = 0;
const LEAGUE_21D = 1;
const DAY = 86_400;
const HOUR = 3_600;
const WEEK = 7 * DAY;
const DURATION = 7;
const startOf = (index: number) => FIRST_START + index * WEEK;
const endOf = (index: number) => startOf(index) + DURATION * DAY;

describe('both leagues open to every member (AC-1, FR-002, FR-003)', () => {
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
      await h.injectCohort(LEAGUE_21D, index, {
        start: BigInt(startOf(index)),
        joinCutoff: BigInt(startOf(index) - HOUR),
        durationDays: 21,
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

  /** Another wallet that joins 7-day cohort `index` and clears it, so the cohort has a finisher. */
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
    await h.sendOrThrow([await settleFinalizeIx(crank.address, fees, LEAGUE_7D, index)], crank);
  };

  it('lets a wallet with no 7-day history join a 21-day cohort (AC-1)', async () => {
    const result = await h.send([await joinCohortIx(member.address, mint, LEAGUE_21D, 0, 1)], member);
    expect(result.ok, result.logs.join('\n')).toBe(true);

    const profile = await h.readProfile(member.address);
    expect(profile.pending.cohort).toBe(await cohortPda(LEAGUE_21D, 0));
    expect(profile.leagues[LEAGUE_7D]!.cleared + profile.leagues[LEAGUE_7D]!.forfeited, 'no 7-day history at all').toBe(0);
    expect((await h.readCohort(LEAGUE_21D, 0)).memberCount).toBe(1);
  });

  it('takes a member into the 21-day league after a 7-day clear exactly as before it', async () => {
    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE_7D, 0, 1)], member);
    await clearSevenDays(0, member);
    h.setTime(endOf(0));
    await settle7d(0, [member.address]);
    expect((await h.readProfile(member.address)).leagues[LEAGUE_7D]!.cleared).toBe(1);

    h.setTime(startOf(1) - 2 * HOUR);
    const joined = await h.send(
      [await joinCohortIx(member.address, mint, LEAGUE_21D, 1, 0, await cohortPda(LEAGUE_7D, 0))],
      member,
    );
    expect(joined.ok, joined.logs.join('\n')).toBe(true);
  });

  it('keeps both leagues open after a 7-day forfeit, the 7-day one included (FR-002)', async () => {
    const quitter = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(quitter.address, mint), mint, quitter.address, DEFAULT_CHIPS[3]);
    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE_7D, 0, 1)], member);
    await h.sendOrThrow([await joinCohortIx(quitter.address, mint, LEAGUE_7D, 0, 0)], quitter);
    const finisher = await finisherIn(0);
    h.setTime(endOf(0));
    await settle7d(0, [member.address, quitter.address, finisher.address]);
    expect((await h.readProfile(member.address)).leagues[LEAGUE_7D]!.forfeited).toBe(1);
    expect((await h.readProfile(quitter.address)).leagues[LEAGUE_7D]!.forfeited).toBe(1);

    // Either league takes a forfeiter back: the 7-day league never closes, and
    // the 21-day league never asked for anything.
    h.setTime(startOf(1) - 2 * HOUR);
    const again = await h.send([await joinCohortIx(member.address, mint, LEAGUE_7D, 1, 0)], member);
    expect(again.ok, again.logs.join('\n')).toBe(true);
    const across = await h.send([await joinCohortIx(quitter.address, mint, LEAGUE_21D, 1, 0)], quitter);
    expect(across.ok, across.logs.join('\n')).toBe(true);
  });

  it('sets the genesis gate through set_flags, admin only — there is no other flag', async () => {
    const admin = h.upgradeAuthority;
    expect((await h.readConfig()).genesisGateEnabled).toBe(false);

    const stranger = await h.newWallet();
    expectProgramError(await h.send([await setFlagsIx(stranger.address, true)], stranger), 'NotAdmin');
    expect((await h.readConfig()).genesisGateEnabled, 'a stranger changes nothing').toBe(false);

    await h.sendOrThrow([await setFlagsIx(admin.address, true)], admin);
    expect((await h.readConfig()).genesisGateEnabled).toBe(true);

    await h.sendOrThrow([await setFlagsIx(admin.address, false)], admin);
    expect((await h.readConfig()).genesisGateEnabled).toBe(false);
  });
});
