/**
 * Missing a day. Task T068.
 *
 * Two things this file is really about.
 *
 * **Breach is derived, not declared** (constitution II, AC-2). The moment a
 * day's deadline passes with the day incomplete the member is out, and every
 * reader computes that from chain state alone. `record_forfeits` only
 * *materialises* it — moves the fee, funds the pool — and until it runs the
 * member is already out by every rule that matters (INV-8).
 *
 * **Automation is safe to repeat** (AC-5, INV-7). The same page twice, the
 * pages in any order, a page mixing breached and standing members: the end
 * state is identical, because a pair that is not in breach or already forfeited
 * is skipped rather than refused (research R9).
 *
 * The fee is exactly `floor(stake × 20 ÷ 100)` and the rest is pool (FR-027,
 * FR-028). The league streak is **not** touched here: only `settle_count`
 * writes it, in cohort order, because a mid-cohort forfeit can be recorded
 * before an earlier cohort of the same league has been counted (FR-061a).
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
  type Harness,
} from '../helpers/harness.js';
import { forfeitPairs, joinCohortIx, recordForfeitsIx, submitDayIx } from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda, profilePda, vaultPda } from '../helpers/pdas.js';

const LEAGUE = 100;
const INDEX = 0;
const DAY_LENGTH = 60;
const DURATION = 7;
const START = FIRST_START;
const deadlineOf = (k: number) => START + (k + 1) * DAY_LENGTH - 1;
const NO_KEY = '11111111111111111111111111111111' as Address;

/** The fee on one forfeited stake: 20 %, rounded down (FR-027). */
const feeOf = (stake: bigint) => (stake * 20n) / 100n;

/** `ForfeitRecorded { cohort, wallet, days_completed, fee }`. */
const decodeForfeit = (bytes: Uint8Array) => ({
  cohort: getAddressDecoder().decode(bytes.slice(0, 32)),
  wallet: getAddressDecoder().decode(bytes.slice(32, 64)),
  daysCompleted: bytes[64],
  fee: new DataView(bytes.buffer, bytes.byteOffset + 65, 8).getBigUint64(0, true),
});

describe('record_forfeits (contracts §2)', () => {
  let h: Harness;
  let mint: Address;
  let cohort: Address;
  let vault: Address;
  let feeDestination: Address;
  let crank: KeyPairSigner;

  beforeEach(async () => {
    h = await createHarness({ startTime: START - DAY_LENGTH });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 10_000_000n * SKR);

    const treasury = (await generateKeyPairSigner()).address;
    feeDestination = await associatedTokenPda(treasury, mint);
    h.injectTokenAccount(feeDestination, mint, treasury, 0n);

    await h.injectConfig({ skrMint: mint, feeDestination });
    await h.injectLeague(LEAGUE, { durationDays: DURATION, dayLength: BigInt(DAY_LENGTH), nextIndex: 1 });
    cohort = await h.injectCohort(LEAGUE, INDEX, {
      start: BigInt(START),
      joinCutoff: BigInt(START - 10),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
    });
    vault = await vaultPda(cohort);
    crank = await h.newWallet();
  });

  /** A member joined at `tier`, through the program. */
  const joinedMember = async (tier: number): Promise<KeyPairSigner> => {
    const member = await h.newWallet();
    h.injectTokenAccount(await associatedTokenPda(member.address, mint), mint, member.address, DEFAULT_CHIPS[3]);
    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, INDEX, tier)], member);
    return member;
  };

  const record = async (wallets: readonly Address[], who: KeyPairSigner = crank) =>
    h.send(
      [await recordForfeitsIx(who.address, feeDestination, LEAGUE, INDEX, await forfeitPairs(cohort, wallets))],
      who,
    );

  // ------------------------------------------------- breach before automation

  it('has the member out the second the deadline passes, before anything runs (AC-2, INV-8)', async () => {
    const member = await joinedMember(3);

    h.setTime(deadlineOf(0) + 1);
    // Nothing has run: the account still says Joined, and the money has not moved…
    expect((await h.readMembership(cohort, member.address)).state).toBe('Joined');
    expect((await h.readCohort(LEAGUE, INDEX)).pool).toBe(0n);
    // …but the member cannot act, which is the part that decides the outcome.
    const late = await h.send([await submitDayIx(member.address, LEAGUE, INDEX, 0, new Uint8Array(32))], member);
    expect(late.ok).toBe(false);

    // Recording it only materialises what was already true.
    await h.sendOrThrow(
      [
        await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, await forfeitPairs(cohort, [
          member.address,
        ])),
      ],
      crank,
    );
    expect((await h.readMembership(cohort, member.address)).state).toBe('Forfeited');
  });

  // -------------------------------------------------------------- the numbers

  it('takes exactly 20 % as fee and pools the rest (FR-027, FR-028)', async () => {
    const member = await joinedMember(3);
    const stake = DEFAULT_CHIPS[3]!;

    h.setTime(deadlineOf(0) + 1);
    const result = await record([member.address]);
    expect(result.ok, result.logs.join('\n')).toBe(true);

    const fee = feeOf(stake);
    const after = await h.readCohort(LEAGUE, INDEX);
    expect(h.tokenBalance(feeDestination)).toBe(fee);
    expect(after.pool).toBe(stake - fee);
    expect(after.heldStake).toBe(0n);
    expect(after.forfeitsRecorded).toBe(1);
    // The vault still holds what the cohort says it holds (INV-1).
    expect(h.tokenBalance(vault)).toBe(after.heldStake + after.pool + after.carryOverHeld);
  });

  it.each([
    ['tier 0', 0],
    ['tier 1', 1],
    ['tier 2', 2],
    ['tier 3', 3],
  ])('rounds the fee down at %s', async (_name, tier) => {
    const member = await joinedMember(tier);
    const stake = DEFAULT_CHIPS[tier]!;

    h.setTime(deadlineOf(0) + 1);
    await h.sendOrThrow(
      [
        await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, await forfeitPairs(cohort, [
          member.address,
        ])),
      ],
      crank,
    );

    expect(h.tokenBalance(feeDestination)).toBe(feeOf(stake));
    expect((await h.readCohort(LEAGUE, INDEX)).pool).toBe(stake - feeOf(stake));
  });

  it('records the missed deadline as the outcome instant, never `now` (FR-060b)', async () => {
    const member = await joinedMember(1);

    // Day 0 completed, day 1 missed — the instant is day 1's deadline.
    h.setTime(START);
    await h.sendOrThrow([await submitDayIx(member.address, LEAGUE, INDEX, 0, new Uint8Array(32))], member);

    // The crank runs three days late; the figure must not move with it.
    h.setTime(START + 4 * DAY_LENGTH);
    const result = await record([member.address]);
    expect(result.ok, result.logs.join('\n')).toBe(true);

    const profile = await h.readProfile(member.address);
    expect(profile.leagues[2]!.lastChanged).toBe(BigInt(deadlineOf(1)));
    expect(profile.leagues[2]!.lastChanged).not.toBe(BigInt(h.now()));

    const event = decodeForfeit(eventPayloads(result, 'ForfeitRecorded')[0]!);
    expect(event.wallet).toBe(member.address);
    expect(event.daysCompleted).toBe(1);
    expect(event.fee).toBe(feeOf(DEFAULT_CHIPS[1]!));
  });

  it('raises `forfeited` and frees the running slot, and leaves the streak alone (FR-061a)', async () => {
    const member = await joinedMember(2);
    h.setTime(START);
    await h.sendOrThrow([await submitDayIx(member.address, LEAGUE, INDEX, 0, new Uint8Array(32))], member);
    expect((await h.readProfile(member.address)).running.cohort).toBe(cohort);

    h.setTime(deadlineOf(1) + 1);
    await h.sendOrThrow(
      [
        await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, await forfeitPairs(cohort, [
          member.address,
        ])),
      ],
      crank,
    );

    const progress = (await h.readProfile(member.address)).leagues[2]!;
    expect(progress.forfeited, 'an order-independent counter, raised now (FR-060a)').toBe(1);
    expect(progress.cleared).toBe(0);
    expect(progress.currentStreak, 'untouched — only settle_count writes it').toBe(0);
    expect(progress.longestStreak).toBe(0);
    // Free to stake again: the obligation is over.
    expect((await h.readProfile(member.address)).running.cohort).toBe(NO_KEY);
  });

  // ------------------------------------------------- repeatable, in any order

  it('is idempotent: the same page twice changes nothing the second time (AC-5, INV-7)', async () => {
    const members = [await joinedMember(0), await joinedMember(3)];
    const wallets = members.map((m) => m.address);

    h.setTime(deadlineOf(0) + 1);
    await h.sendOrThrow(
      [await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, await forfeitPairs(cohort, wallets))],
      crank,
    );

    const before = await h.snapshot();
    const again = await record(wallets);
    expect(again.ok, again.logs.join('\n')).toBe(true);
    assertUnchanged(before, await h.snapshot());
  });

  it('gives the same end state whatever order the pages come in', async () => {
    const members = [await joinedMember(0), await joinedMember(1), await joinedMember(2), await joinedMember(3)];
    const wallets = members.map((m) => m.address);
    h.setTime(deadlineOf(0) + 1);

    // Reversed, then overlapping, then all of them: every pair lands once.
    await h.sendOrThrow(
      [
        await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, await forfeitPairs(cohort, [
          wallets[3]!,
          wallets[1]!,
        ])),
      ],
      crank,
    );
    await h.sendOrThrow(
      [
        await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, await forfeitPairs(cohort, [
          wallets[1]!,
          wallets[0]!,
          wallets[3]!,
        ])),
      ],
      crank,
    );
    await h.sendOrThrow(
      [await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, await forfeitPairs(cohort, wallets))],
      crank,
    );

    const total = wallets.reduce((sum, _w, tier) => sum + DEFAULT_CHIPS[tier]!, 0n);
    const fees = wallets.reduce((sum, _w, tier) => sum + feeOf(DEFAULT_CHIPS[tier]!), 0n);
    const after = await h.readCohort(LEAGUE, INDEX);
    expect(after.forfeitsRecorded).toBe(4);
    expect(h.tokenBalance(feeDestination)).toBe(fees);
    expect(after.pool).toBe(total - fees);
    expect(after.heldStake).toBe(0n);
    for (const wallet of wallets) {
      expect((await h.readMembership(cohort, wallet)).state).toBe('Forfeited');
    }
  });

  it('skips a member who is still standing, and takes the one beside them', async () => {
    const standing = await joinedMember(0);
    const breached = await joinedMember(3);

    // `standing` completed day 0; `breached` did not.
    h.setTime(START);
    await h.sendOrThrow([await submitDayIx(standing.address, LEAGUE, INDEX, 0, new Uint8Array(32))], standing);

    h.setTime(deadlineOf(0) + 1);
    const result = await record([standing.address, breached.address]);
    expect(result.ok, result.logs.join('\n')).toBe(true);

    expect((await h.readMembership(cohort, standing.address)).state).toBe('Joined');
    expect((await h.readMembership(cohort, breached.address)).state).toBe('Forfeited');

    const after = await h.readCohort(LEAGUE, INDEX);
    expect(after.forfeitsRecorded).toBe(1);
    expect(after.heldStake, 'the standing member\'s stake is still held').toBe(DEFAULT_CHIPS[0]!);
    expect(h.tokenBalance(feeDestination)).toBe(feeOf(DEFAULT_CHIPS[3]!));
  });

  it('records nothing at all before the cohort starts', async () => {
    const member = await joinedMember(3);
    h.setTime(START - 1);

    const before = await h.snapshot();
    const result = await record([member.address]);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    assertUnchanged(before, await h.snapshot());
    expect((await h.readMembership(cohort, member.address)).state).toBe('Joined');
  });

  it('takes an empty page without complaint', async () => {
    const result = await record([]);
    expect(result.ok, result.logs.join('\n')).toBe(true);
  });

  // --------------------------------------------------------- pairs must match

  it('refuses a whole page carrying a profile that is not the membership\'s', async () => {
    const member = await joinedMember(3);
    const stranger = await h.newWallet();
    h.setTime(deadlineOf(0) + 1);

    const pairs = await forfeitPairs(cohort, [member.address]);
    const forged = [{ membership: pairs[0]!.membership, profile: await profilePda(stranger.address) }];
    await h.injectProfile(stranger.address);

    expectProgramError(
      await h.send(
        [await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, forged)],
        crank,
      ),
      'AccountMismatch',
    );
    expect((await h.readMembership(cohort, member.address)).state).toBe('Joined');
    expect(h.tokenBalance(feeDestination)).toBe(0n);
  });

  it('refuses a whole page carrying a membership from another cohort', async () => {
    const member = await joinedMember(3);
    const other = await h.injectCohort(LEAGUE, 1, {
      start: BigInt(START),
      durationDays: DURATION,
      dayLength: BigInt(DAY_LENGTH),
    });
    await h.injectMembership(other, member.address, { tier: 3, stake: DEFAULT_CHIPS[3]! });

    h.setTime(deadlineOf(0) + 1);
    const pairs = await forfeitPairs(other, [member.address]);
    expectProgramError(
      await h.send([await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, pairs)], crank),
      'AccountMismatch',
    );
    expect((await h.readCohort(LEAGUE, INDEX)).forfeitsRecorded).toBe(0);
    expect(cohort).toBe(await cohortPda(LEAGUE, INDEX));
  });

  it('refuses an odd page: every membership needs its profile', async () => {
    const member = await joinedMember(3);
    h.setTime(deadlineOf(0) + 1);

    const pairs = await forfeitPairs(cohort, [member.address]);
    const odd = await recordForfeitsIx(crank.address, feeDestination, LEAGUE, INDEX, pairs);
    // Drop the profile, leaving the membership unpaired.
    const truncated = { ...odd, accounts: (odd.accounts ?? []).slice(0, -1) };
    expect((await h.send([truncated], crank)).ok).toBe(false);
    expect((await h.readMembership(cohort, member.address)).state).toBe('Joined');
  });

  it('is permissionless: any caller gets the same result (FR-030a)', async () => {
    const member = await joinedMember(2);
    h.setTime(deadlineOf(0) + 1);

    const stranger = await h.newWallet();
    const result = await record([member.address], stranger);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect((await h.readMembership(cohort, member.address)).state).toBe('Forfeited');
    expect(h.tokenBalance(feeDestination)).toBe(feeOf(DEFAULT_CHIPS[2]!));
  });

  it('takes a full page of ten', async () => {
    const members: KeyPairSigner[] = [];
    for (let i = 0; i < 10; i += 1) members.push(await joinedMember(i % 4));

    h.setTime(deadlineOf(0) + 1);
    const result = await record(members.map((m) => m.address));
    expect(result.ok, result.logs.join('\n')).toBe(true);

    const after = await h.readCohort(LEAGUE, INDEX);
    expect(after.forfeitsRecorded).toBe(10);
    expect(after.heldStake).toBe(0n);
    expect(h.tokenBalance(vault)).toBe(after.pool);
  });

  it('refuses more than ten pairs in one page (research R9)', async () => {
    const members: KeyPairSigner[] = [];
    for (let i = 0; i < 11; i += 1) members.push(await joinedMember(i % 4));

    h.setTime(deadlineOf(0) + 1);
    const result = await record(members.map((m) => m.address));
    expect(result.ok).toBe(false);
    expect((await h.readCohort(LEAGUE, INDEX)).forfeitsRecorded).toBe(0);
  });
});
