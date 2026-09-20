/**
 * The lobby, and the cutoff that closes it. Task T054a.
 *
 * Every rule here is decided by **chain time alone** (constitution II): no
 * automation has to run for joining to close, for the roster to be final, or
 * for a cohort to be locked. A join and a leave one second before the cutoff
 * both land; one second after it, both are refused, and the roster that stands
 * at that instant is the one the cohort runs with (ES-12, ES-12a, ES-13).
 *
 * It also covers the rule that keeps two daily obligations from ever
 * overlapping (FR-006b, INV-4), including the case the guard's shape exists
 * for: a member whose lobby place was moved into the running slot by slot
 * normalisation, which cannot fill `running.end`, so the rule is checked
 * against the running cohort's own account instead.
 */

import { generateKeyPairSigner, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  DAY,
  DEFAULT_CHIPS,
  FIRST_START,
  HOUR,
  SKR,
  WEEK,
  createHarness,
  expectProgramError,
  type Harness,
} from '../helpers/harness.js';
import { joinCohortIx, leaveCohortIx } from '../helpers/instructions.js';
import { associatedTokenPda, membershipPda, vaultPda } from '../helpers/pdas.js';

const LEAGUE = 0;
const INDEX = 0;
const NO_KEY = '11111111111111111111111111111111' as Address;
/** The cutoff: an hour before the start (FR-008). */
const CUTOFF = FIRST_START - HOUR;

describe('the lobby closes on chain time alone (ES-12, ES-13)', () => {
  let h: Harness;
  let mint: Address;
  let member: KeyPairSigner;
  let cohort: Address;

  beforeEach(async () => {
    h = await createHarness({ startTime: FIRST_START - DAY });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 1_000_000n * SKR);
    await h.injectConfig({ skrMint: mint });
    await h.injectLeague(LEAGUE, { nextIndex: 1 });
    cohort = await h.injectCohort(LEAGUE, INDEX);

    member = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(member.address, mint), mint, member.address, DEFAULT_CHIPS[3]);
  });

  const join = async (who: KeyPairSigner = member, tier = 0) =>
    h.send([await joinCohortIx(who.address, mint, LEAGUE, INDEX, tier)], who);
  const leave = async (who: KeyPairSigner = member) =>
    h.send([await leaveCohortIx(who.address, mint, LEAGUE, INDEX)], who);

  it('takes a join one second before the cutoff', async () => {
    h.setTime(CUTOFF - 1);
    const result = await join();
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect((await h.readMembership(cohort, member.address)).joinedAt).toBe(BigInt(CUTOFF - 1));
  });

  it('refuses a join at the cutoff itself, and every second after', async () => {
    h.setTime(CUTOFF);
    expectProgramError(await join(), 'JoiningClosed');

    h.setTime(CUTOFF + 1);
    expectProgramError(await join(), 'JoiningClosed');

    h.setTime(FIRST_START);
    expectProgramError(await join(), 'JoiningClosed');

    expect(h.svm.getAccount(await membershipPda(cohort, member.address)).exists).toBe(false);
    expect(h.tokenBalance(await vaultPda(cohort))).toBe(0n);
  });

  it('takes a leave one second before the cutoff, with the whole stake back', async () => {
    h.setTime(FIRST_START - DAY);
    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, INDEX, 2)], member);

    h.setTime(CUTOFF - 1);
    const result = await leave();
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect(h.tokenBalance(await associatedTokenPda(member.address, mint))).toBe(DEFAULT_CHIPS[3]);
    expect(h.tokenBalance(await vaultPda(cohort))).toBe(0n);
  });

  it('refuses a leave from the cutoff onwards: the roster is final (ES-12a)', async () => {
    h.setTime(FIRST_START - DAY);
    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, INDEX, 2)], member);

    h.setTime(CUTOFF);
    expectProgramError(await leave(), 'JoiningClosed');

    h.setTime(FIRST_START - 1);
    expectProgramError(await leave(), 'JoiningClosed');

    // The stake is committed: still held, still the member's, and no way out.
    expect(h.tokenBalance(await vaultPda(cohort))).toBe(DEFAULT_CHIPS[2]);
    expect((await h.readMembership(cohort, member.address)).state).toBe('Joined');
    expect((await h.readCohort(LEAGUE, INDEX)).memberCount).toBe(1);
  });

  it('holds the locked roster from the cutoff to the start, with no actions at all', async () => {
    h.setTime(FIRST_START - DAY);
    const joined: KeyPairSigner[] = [];
    for (const tier of [0, 1, 2]) {
      const m = await h.newWallet();
      h.injectTokenAccount(await associatedTokenPda(m.address, mint), mint, m.address, DEFAULT_CHIPS[3]);
      await h.sendOrThrow([await joinCohortIx(m.address, mint, LEAGUE, INDEX, tier)], m);
      joined.push(m);
    }

    const locked = await h.readCohort(LEAGUE, INDEX);
    const expected = DEFAULT_CHIPS[0] + DEFAULT_CHIPS[1] + DEFAULT_CHIPS[2];
    expect(locked.memberCount).toBe(3);
    expect(locked.heldStake).toBe(expected);

    // Every instant of the locked hour refuses both actions and changes nothing.
    for (const at of [CUTOFF, CUTOFF + 1, FIRST_START - 1]) {
      h.setTime(at);
      expectProgramError(await join(), 'JoiningClosed');
      expectProgramError(await leave(joined[0]!), 'JoiningClosed');
    }

    const after = await h.readCohort(LEAGUE, INDEX);
    expect(after.memberCount).toBe(3);
    expect(after.heldStake).toBe(expected);
    expect(h.tokenBalance(await vaultPda(cohort))).toBe(expected);
  });
});

describe('two daily obligations never overlap (FR-006b, INV-4)', () => {
  let h: Harness;
  let mint: Address;
  let member: KeyPairSigner;
  /** The 7-day cohort the member is already running. */
  let running: Address;

  const JOIN_AT = FIRST_START + 2 * DAY;
  /** The running cohort's final deadline has passed by here. */
  const RUNNING_END = FIRST_START + 7 * DAY;

  beforeEach(async () => {
    h = await createHarness({ startTime: JOIN_AT });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 1_000_000n * SKR);
    await h.injectConfig({ skrMint: mint });
    await h.injectLeague(LEAGUE, { nextIndex: 4 });

    running = await h.injectCohort(LEAGUE, 0, { start: BigInt(FIRST_START), durationDays: 7 });
    member = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(member.address, mint), mint, member.address, DEFAULT_CHIPS[3]);
  });

  /** A cohort of the 21-day league, starting at `start`, open for joining. */
  const lobbyAt = async (index: number, start: number) =>
    h.injectCohort(LEAGUE, index, { start: BigInt(start), durationDays: 7 });

  const joinInto = async (index: number, runningCohort?: Address) =>
    h.send([await joinCohortIx(member.address, mint, LEAGUE, index, 0, runningCohort)], member);

  it('refuses a lobby place in a cohort starting before the running one ends', async () => {
    await lobbyAt(1, RUNNING_END - DAY);
    await h.injectProfile(member.address, {
      running: { cohort: running, end: BigInt(RUNNING_END) },
    });

    expectProgramError(await joinInto(1, running), 'ObligationsOverlap');
    expect(h.svm.getAccount(await membershipPda(await lobbyAt(1, RUNNING_END - DAY), member.address)).exists).toBe(
      false,
    );
  });

  it('takes a lobby place in a cohort starting exactly as the running one ends', async () => {
    const next = await lobbyAt(1, RUNNING_END);
    await h.injectProfile(member.address, {
      running: { cohort: running, end: BigInt(RUNNING_END) },
    });

    const result = await joinInto(1, running);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect((await h.readProfile(member.address)).pending.cohort).toBe(next);
  });

  /**
   * The regression the guard's shape exists for. Slot normalisation moves a
   * lobby place into the running slot knowing only the cohort's address and its
   * start, so `running.end` is left at 0 — and a rule that trusted that cached
   * zero would skip the overlap check for every member between their cohort
   * starting and their first completed day.
   */
  it('still refuses the overlap when the running slot came from normalisation (end = 0)', async () => {
    await lobbyAt(1, RUNNING_END - DAY);
    await h.injectProfile(member.address, {
      // Exactly what `normalise_slots` leaves behind.
      running: { cohort: running, end: 0n },
    });

    expectProgramError(await joinInto(1, running), 'ObligationsOverlap');
    expect((await h.readProfile(member.address)).pending.cohort).toBe(NO_KEY);
    expect((await h.readCohort(LEAGUE, 1)).memberCount).toBe(0);
  });

  it('repairs the cached end from the account it was given', async () => {
    const next = await lobbyAt(1, RUNNING_END);
    await h.injectProfile(member.address, {
      running: { cohort: running, end: 0n },
    });

    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, 1, 0, running)], member);

    const profile = await h.readProfile(member.address);
    expect(profile.running.end, 'no longer a permanent zero').toBe(BigInt(RUNNING_END));
    expect(profile.pending.cohort).toBe(next);
  });

  it('will not let the running cohort be left out, or swapped for another', async () => {
    await lobbyAt(1, RUNNING_END - DAY);
    await h.injectProfile(member.address, {
      running: { cohort: running, end: 0n },
    });

    // Omitting it would be the cheapest way to have the rule skipped.
    expectProgramError(await joinInto(1), 'AccountMismatch');

    // So would supplying a different cohort that happens to have ended.
    const decoy = await h.injectCohort(LEAGUE, 2, { start: BigInt(FIRST_START) - BigInt(WEEK), durationDays: 7 });
    expectProgramError(await joinInto(1, decoy), 'AccountMismatch');

    expect((await h.readCohort(LEAGUE, 1)).memberCount).toBe(0);
    expect((await h.readProfile(member.address)).pending.cohort).toBe(NO_KEY);
  });

  it('frees the slot once the running cohort\'s final deadline has passed', async () => {
    const next = await lobbyAt(1, RUNNING_END + HOUR);
    await h.injectProfile(member.address, {
      running: { cohort: running, end: 0n },
    });

    // The run is over; settlement has not released the slot yet.
    h.setTime(RUNNING_END);
    const result = await joinInto(1, running);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect((await h.readProfile(member.address)).pending.cohort).toBe(next);
  });

  it('needs no running cohort when the member has none', async () => {
    const next = await lobbyAt(1, RUNNING_END);
    // No profile at all: the first join creates it.
    const result = await joinInto(1);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect((await h.readProfile(member.address)).pending.cohort).toBe(next);
  });
});
