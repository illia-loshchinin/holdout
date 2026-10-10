/**
 * Staking into a Holdout and getting out again. Task T054.
 *
 * What the cohort charges is the cohort's own copied chip amount, not whatever
 * the Operator has published since (FR-055) — and a member never names an
 * amount at all, only a tier, so there is no path by which a stake that is not
 * one of the cohort's four can ever be held (AC-13).
 *
 * A leave is unconditional and lossless while joining is open: the whole stake
 * back, the `Membership` closed and its rent returned, the lobby place cleared
 * (FR-009). The cutoff itself is `lobby.spec.ts`'s subject.
 *
 * Setup that is not the subject of a test is injected, so a defect in
 * `open_cohort` cannot hide one in `join_cohort`.
 */

import { generateKeyPairSigner, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  DEFAULT_CHIPS,
  FIRST_START,
  HOUR,
  SKR,
  WEEK,
  cohortCustody,
  createHarness,
  expectProgramError,
  type Harness,
} from '../helpers/harness.js';
import { joinCohortIx, leaveCohortIx, publishChipSetIx } from '../helpers/instructions.js';
import { associatedTokenPda, cohortPda, membershipPda, vaultPda } from '../helpers/pdas.js';

const LEAGUE = 0;
const INDEX = 0;
/** Comfortably inside the lobby: a day before the cutoff. */
const IN_THE_LOBBY = FIRST_START - 24 * HOUR;

describe('join_cohort and leave_cohort (contracts §3)', () => {
  let h: Harness;
  let mint: Address;
  let member: KeyPairSigner;
  let cohort: Address;
  let vault: Address;
  let memberToken: Address;

  /** SKR the member holds. */
  const held = () => h.tokenBalance(memberToken);
  /** SKR the cohort's vault holds. */
  const vaulted = () => h.tokenBalance(vault);

  beforeEach(async () => {
    h = await createHarness({ startTime: IN_THE_LOBBY });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, null, 1_000_000n * SKR);
    await h.injectConfig({ skrMint: mint, operator: (await h.newWallet()).address });
    await h.injectLeague(LEAGUE, { nextIndex: 1 });
    cohort = await h.injectCohort(LEAGUE, INDEX);
    vault = await vaultPda(cohort);

    member = await h.newWallet();
    memberToken = await associatedTokenPda(member.address, mint);
    // What a faucet claim leaves behind: the top chip of the top tier ever published.
    h.injectTokenAccount(memberToken, mint, member.address, DEFAULT_CHIPS[3]);
  });

  const join = async (tier: number, runningCohort?: Address, who: KeyPairSigner = member) =>
    h.send([await joinCohortIx(who.address, mint, LEAGUE, INDEX, tier, runningCohort)], who);

  const leave = async (who: KeyPairSigner = member) =>
    h.send([await leaveCohortIx(who.address, mint, LEAGUE, INDEX)], who);

  // ------------------------------------------------------------------ joining

  describe('a member picks a tier, never an amount', () => {
    it.each([
      ['tier 0', 0],
      ['tier 1', 1],
      ['tier 2', 2],
      ['tier 3', 3],
    ])('stakes the cohort\'s own chip for %s', async (_name, tier) => {
      const before = held();
      const result = await join(tier);
      expect(result.ok, result.logs.join('\n')).toBe(true);

      const stake = DEFAULT_CHIPS[tier]!;
      const membership = await h.readMembership(cohort, member.address);
      expect(membership.tier).toBe(tier);
      expect(membership.stake).toBe(stake);
      expect(membership.daysCompleted).toBe(0);
      expect(membership.state).toBe('Joined');
      expect(membership.counted).toBe(false);
      expect(membership.joinedAt).toBe(BigInt(h.now()));

      // The stake left the member and is held by the cohort, not the program.
      expect(held()).toBe(before - stake);
      expect(vaulted()).toBe(stake);

      const after = await h.readCohort(LEAGUE, INDEX);
      expect(after.memberCount).toBe(1);
      expect(after.heldStake).toBe(stake);
      expect(after.pool, 'a stake is never pool money until it is forfeited (INV-2)').toBe(0n);

      // The lobby place, which is what keeps two obligations from overlapping.
      const profile = await h.readProfile(member.address);
      expect(profile.pending.cohort).toBe(cohort);
      expect(profile.pending.start).toBe(after.start);
      expect(profile.running.cohort).toBe('11111111111111111111111111111111');
    });

    it('creates the Profile on a first join, and reuses it on the next', async () => {
      await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, INDEX, 0)], member);
      const first = await h.readProfile(member.address);
      expect(first.wallet).toBe(member.address);
      expect(first.lastFaucetDay).toBe(-1n);
      for (const league of first.leagues) {
        expect(league.cleared).toBe(0);
        expect(league.forfeited).toBe(0);
      }
    });

    it('refuses a tier above the four the cohort has', async () => {
      expectProgramError(await join(4), 'InvalidTier');
      expectProgramError(await join(255), 'InvalidTier');

      expect(h.svm.getAccount(await membershipPda(cohort, member.address)).exists).toBe(false);
      expect(vaulted()).toBe(0n);
      expect((await h.readCohort(LEAGUE, INDEX)).memberCount).toBe(0);
    });

    it('charges the cohort\'s copied amounts, not a chip set published since (FR-055)', async () => {
      const operator = await h.newWallet();
      await h.injectConfig({ skrMint: mint, operator: operator.address });

      // The Operator re-prices everything downwards after this cohort opened.
      const cheaper: [bigint, bigint, bigint, bigint] = [1n * SKR, 2n * SKR, 3n * SKR, 4n * SKR];
      await h.sendOrThrow([await publishChipSetIx(operator.address, cheaper)], operator);

      await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, INDEX, 3)], member);

      const membership = await h.readMembership(cohort, member.address);
      expect(membership.stake, 'the cohort\'s own chip, for its whole life').toBe(DEFAULT_CHIPS[3]);
      expect(vaulted()).toBe(DEFAULT_CHIPS[3]);
    });

    it('refuses a member who cannot cover the chip, and holds nothing', async () => {
      h.injectTokenAccount(memberToken, mint, member.address, DEFAULT_CHIPS[3] - 1n);

      const result = await join(3);
      expect(result.ok).toBe(false);
      expect(vaulted()).toBe(0n);
      expect(held(), 'and takes nothing on the way out').toBe(DEFAULT_CHIPS[3] - 1n);
      expect((await h.readCohort(LEAGUE, INDEX)).heldStake).toBe(0n);
    });

    it('cannot join the same cohort twice', async () => {
      await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, INDEX, 0)], member);
      const second = await join(1);
      expect(second.ok).toBe(false);

      const membership = await h.readMembership(cohort, member.address);
      expect(membership.tier, 'the first join stands').toBe(0);
      expect(vaulted()).toBe(DEFAULT_CHIPS[0]);
      expect((await h.readCohort(LEAGUE, INDEX)).memberCount).toBe(1);
    });

    it('refuses a second lobby place (FR-006a)', async () => {
      // A place in another cohort of the same league, still in its lobby.
      const otherStart = BigInt(FIRST_START) + BigInt(WEEK);
      const other = await h.injectCohort(LEAGUE, 1, { start: otherStart });
      await h.injectProfile(member.address, {
        // The place carries the cohort's own end, as a join writes it.
        pending: { cohort: other, start: otherStart, end: otherStart + BigInt(WEEK) },
      });

      expectProgramError(await join(0), 'LobbyPlaceTaken');
      expect(h.svm.getAccount(await membershipPda(cohort, member.address)).exists).toBe(false);
      expect(vaulted()).toBe(0n);
    });
  });

  // ------------------------------------------------------------------ leaving

  describe('leaving while joining is open', () => {
    beforeEach(async () => {
      await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, INDEX, 2)], member);
    });

    it('refunds the whole stake with no deduction (FR-009)', async () => {
      const stake = DEFAULT_CHIPS[2];
      expect(vaulted()).toBe(stake);

      const result = await leave();
      expect(result.ok, result.logs.join('\n')).toBe(true);

      expect(held(), 'every unit back').toBe(DEFAULT_CHIPS[3]);
      expect(vaulted()).toBe(0n);

      const after = await h.readCohort(LEAGUE, INDEX);
      expect(after.memberCount).toBe(0);
      expect(after.heldStake).toBe(0n);
      expect(after.pool, 'a leave never funds the pool').toBe(0n);
    });

    it('closes the Membership and returns its rent', async () => {
      const at = await membershipPda(cohort, member.address);
      const rent = h.svm.getAccount(at).exists ? (h.svm.getAccount(at) as { lamports: bigint }).lamports : 0n;
      expect(rent).toBeGreaterThan(0n);

      const lamportsBefore = h.balance(member.address);
      await h.sendOrThrow([await leaveCohortIx(member.address, mint, LEAGUE, INDEX)], member);

      expect(h.svm.getAccount(at).exists, 'Withdrawn is a closed account (data-model §1.4)').toBe(false);
      expect(h.balance(member.address)).toBeGreaterThan(lamportsBefore);
    });

    it('clears the lobby place, so the member can join elsewhere', async () => {
      expect((await h.readProfile(member.address)).pending.cohort).toBe(cohort);
      await h.sendOrThrow([await leaveCohortIx(member.address, mint, LEAGUE, INDEX)], member);
      expect((await h.readProfile(member.address)).pending.cohort).toBe(
        '11111111111111111111111111111111',
      );
    });

    it('can be re-joined after leaving, at a different tier', async () => {
      await h.sendOrThrow([await leaveCohortIx(member.address, mint, LEAGUE, INDEX)], member);
      await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, INDEX, 0)], member);

      expect((await h.readMembership(cohort, member.address)).stake).toBe(DEFAULT_CHIPS[0]);
      expect(vaulted()).toBe(DEFAULT_CHIPS[0]);
      expect((await h.readCohort(LEAGUE, INDEX)).memberCount).toBe(1);
    });

    it('cannot be left twice', async () => {
      await h.sendOrThrow([await leaveCohortIx(member.address, mint, LEAGUE, INDEX)], member);
      const second = await leave();
      expect(second.ok).toBe(false);
      expect(vaulted()).toBe(0n);
    });

    it('is for the member themselves: a stranger cannot cash out their place', async () => {
      const stranger = await h.newWallet();
      const result = await h.send([await leaveCohortIx(stranger.address, mint, LEAGUE, INDEX)], stranger);
      expect(result.ok).toBe(false);
      expect(vaulted(), 'the stake stays held').toBe(DEFAULT_CHIPS[2]);
    });
  });

  // ------------------------------------------------- the money never goes astray

  it('holds exactly the stakes of its members, and nothing else (INV-1)', async () => {
    const members: KeyPairSigner[] = [];
    let expected = 0n;
    for (const tier of [0, 1, 2, 3]) {
      const m = await h.newWallet();
      h.injectTokenAccount(await associatedTokenPda(m.address, mint), mint, m.address, DEFAULT_CHIPS[3]);
      await h.sendOrThrow([await joinCohortIx(m.address, mint, LEAGUE, INDEX, tier)], m);
      members.push(m);
      expected += DEFAULT_CHIPS[tier]!;
    }

    let after = await h.readCohort(LEAGUE, INDEX);
    expect(after.memberCount).toBe(4);
    expect(after.heldStake).toBe(expected);
    expect(vaulted()).toBe(cohortCustody(after));

    // One of them leaves; the books still balance.
    await h.sendOrThrow([await leaveCohortIx(members[1]!.address, mint, LEAGUE, INDEX)], members[1]!);
    after = await h.readCohort(LEAGUE, INDEX);
    expect(after.memberCount).toBe(3);
    expect(after.heldStake).toBe(expected - DEFAULT_CHIPS[1]);
    expect(vaulted()).toBe(cohortCustody(after));
  });

  it('is the cohort it was asked for, not another of the same league', async () => {
    const other = await h.injectCohort(LEAGUE, 1, { start: BigInt(FIRST_START) + BigInt(WEEK) });
    expect(other).not.toBe(await cohortPda(LEAGUE, INDEX));

    await h.sendOrThrow([await joinCohortIx(member.address, mint, LEAGUE, INDEX, 0)], member);
    expect((await h.readCohort(LEAGUE, 1)).memberCount, 'the sibling is untouched').toBe(0);
    expect(h.tokenBalance(await vaultPda(other))).toBe(0n);
  });
});
