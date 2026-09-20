/**
 * The sponsor's float, and the drain nothing can prevent. Task T037a.
 *
 * The fee-sponsor key **ships inside the app** (research R13, README known
 * weaknesses). So anyone can extract it, and anyone can empty its float with a
 * plain System Program transfer — no faucet guard can stop that, because the
 * transfer never touches our program. What matters is what an empty sponsor
 * costs: an empty sponsor cannot pay a claim fee, which would end zero-SOL
 * onboarding rather than merely costing a day's allowance.
 *
 * Hence `top_up_sponsor`: **permissionless, and the sponsor does not sign**.
 * Any wallet with a little devnet SOL can refill the float from the vault, the
 * crank runs it on every pass, and the refill is charged to the *same* daily
 * lamport budget as a claim — so a griefer must spend the whole day's
 * allowance to stop onboarding for the rest of that day (FR-042b).
 *
 * The float is deliberately small: it only has to cover the fees between two
 * crank passes, so there is little to steal.
 */

import {
  generateKeyPairSigner,
  getAddressDecoder,
  getU64Encoder,
  AccountRole,
  type Address,
  type Instruction,
  type KeyPairSigner,
} from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  DEFAULT_CHIPS,
  FIRST_START,
  createHarness,
  expectProgramError,
  oneEvent,
  type Harness,
} from '../helpers/harness.js';
import { claimFaucetIx, topUpSponsorIx } from '../helpers/instructions.js';
import { SYSTEM_PROGRAM, associatedTokenPda, faucetVaultPda, mintAuthorityPda } from '../helpers/pdas.js';

const SOL = 1_000_000_000n;
const SOL_TOP_UP = 20_000_000n;
const SPONSOR_FLOAT = 50_000_000n;
const DAILY_CAP = 720_000_000n;
const todayAt = (now: number) => BigInt(Math.floor((now - 18_000) / 86_400));

/** `SponsorToppedUp { lamports }`. */
const decodeToppedUp = (bytes: Uint8Array) =>
  new DataView(bytes.buffer, bytes.byteOffset).getBigUint64(0, true);

/**
 * A plain System Program transfer, which is all it takes to empty the float.
 * Nothing about it goes through our program, which is exactly the point.
 */
const systemTransfer = (from: Address, to: Address, lamports: bigint): Instruction => {
  const data = new Uint8Array(12);
  new DataView(data.buffer).setUint32(0, 2, true); // System instruction 2 = Transfer
  data.set(new Uint8Array(getU64Encoder().encode(lamports)), 4);
  return {
    programAddress: SYSTEM_PROGRAM,
    accounts: [
      { address: from, role: AccountRole.WRITABLE_SIGNER },
      { address: to, role: AccountRole.WRITABLE },
    ],
    data,
  };
};

describe('top_up_sponsor (contracts §2, FR-042b)', () => {
  let h: Harness;
  let mint: Address;
  let sponsor: KeyPairSigner;
  let vault: Address;
  let anybody: KeyPairSigner;

  beforeEach(async () => {
    h = await createHarness({ startTime: FIRST_START - 86_400 });
    mint = (await generateKeyPairSigner()).address;
    h.injectMint(mint, await mintAuthorityPda(), 0n, 6);

    sponsor = await generateKeyPairSigner();
    await h.fund(sponsor.address, SPONSOR_FLOAT);

    await h.injectConfig({ skrMint: mint, sponsor: sponsor.address });
    vault = await faucetVaultPda();
    await h.fund(vault, 25n * SOL);

    // Someone unrelated with a little devnet SOL — a community member, or the crank.
    anybody = await h.newWallet();
  });

  /** Empty the float the way anyone holding the extracted key can. */
  const drainTheFloat = async () => {
    const thief = await h.newWallet();
    const balance = h.balance(sponsor.address);
    // Leave the sponsor enough to pay this transfer's own fee and nothing more.
    await h.sendOrThrow([systemTransfer(sponsor.address, thief.address, balance - 10_000n)], sponsor);
  };

  const topUp = async (who: KeyPairSigner = anybody) =>
    h.send([await topUpSponsorIx(who.address, sponsor.address)], who);

  it('refuses while the sponsor still holds half its float (6054)', async () => {
    expect(h.balance(sponsor.address)).toBe(SPONSOR_FLOAT);
    expectProgramError(await topUp(), 'SponsorFloatSufficient');

    // Exactly half is still sufficient: the refill waits until it is half gone.
    const thief = await h.newWallet();
    await h.sendOrThrow(
      [systemTransfer(sponsor.address, thief.address, SPONSOR_FLOAT / 2n - 5_000n)],
      sponsor,
    );
    expect(h.balance(sponsor.address)).toBe(SPONSOR_FLOAT / 2n);
    expectProgramError(await topUp(), 'SponsorFloatSufficient');

    expect((await h.readConfig()).faucet.lamportsToday, 'a refusal costs the day nothing').toBe(0n);
  });

  it('restores the float from the vault when sent by an unrelated wallet, with no sponsor signature', async () => {
    await drainTheFloat();
    const drained = h.balance(sponsor.address);
    expect(drained).toBeLessThan(SPONSOR_FLOAT / 2n);

    const vaultBefore = h.balance(vault);
    const result = await topUp();
    expect(result.ok, result.logs.join('\n')).toBe(true);

    // Back to the float exactly, from the vault, charged to the day's budget.
    expect(h.balance(sponsor.address)).toBe(SPONSOR_FLOAT);
    const moved = SPONSOR_FLOAT - drained;
    expect(h.balance(vault)).toBe(vaultBefore - moved);
    expect((await h.readConfig()).faucet.lamportsToday).toBe(moved);
    expect(decodeToppedUp(oneEvent(result, 'SponsorToppedUp'))).toBe(moved);

    // The sponsor never signed: the instruction lists it as a writable account only.
    const ix = await topUpSponsorIx(anybody.address, sponsor.address);
    const asAccount = ix.accounts?.find((a) => a.address === sponsor.address);
    expect(String(asAccount?.role)).not.toContain('SIGNER');
  });

  it('a drain pauses zero-SOL onboarding for exactly one pass (FR-042b)', async () => {
    // Before: a wallet with nothing can be funded.
    const first = await generateKeyPairSigner();
    await h.sendOrThrow([await claimFaucetIx(sponsor.address, first.address, mint, sponsor.address)], sponsor);
    expect(h.balance(first.address)).toBe(SOL_TOP_UP);

    await drainTheFloat();

    // While drained, the sponsor cannot pay a claim fee at all — the failure is
    // the runtime's, not a program error, because there is nothing to pay with.
    const second = await generateKeyPairSigner();
    const blocked = await h.send(
      [await claimFaucetIx(sponsor.address, second.address, mint, sponsor.address)],
      sponsor,
    );
    expect(blocked.ok).toBe(false);
    expect(h.balance(second.address)).toBe(0n);

    // One permissionless refill later — what the crank does every pass — and
    // onboarding works again, with no Operator involved.
    await h.sendOrThrow([await topUpSponsorIx(anybody.address, sponsor.address)], anybody);
    const result = await h.send(
      [await claimFaucetIx(sponsor.address, second.address, mint, sponsor.address)],
      sponsor,
    );
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect(h.balance(second.address)).toBe(SOL_TOP_UP);
    expect(h.tokenBalance(await associatedTokenPda(second.address, mint))).toBe(DEFAULT_CHIPS[3]);
  });

  it('is charged to the same daily budget as a claim, and refused once it is spent', async () => {
    await drainTheFloat();
    await h.injectConfig({
      skrMint: mint,
      sponsor: sponsor.address,
      faucet: {
        solTopUp: SOL_TOP_UP,
        sponsorFloat: SPONSOR_FLOAT,
        dailyLamportCap: DAILY_CAP,
        lamportDay: todayAt(h.now()),
        lamportsToday: DAILY_CAP - 1_000n,
      },
    });

    const vaultBefore = h.balance(vault);
    const drained = h.balance(sponsor.address);
    expectProgramError(await topUp(), 'FaucetCapReached');
    expect(h.balance(vault), 'nothing moved').toBe(vaultBefore);
    expect(h.balance(sponsor.address)).toBe(drained);

    // A new day, a new budget — and no separate refill counter to disagree with it.
    const today = todayAt(h.now());
    h.setTime(Number((today + 1n) * 86_400n + 18_000n));
    const result = await topUp();
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect(h.balance(sponsor.address)).toBe(SPONSOR_FLOAT);
  });

  it('refuses when the vault cannot cover the refill (6052)', async () => {
    await drainTheFloat();
    // A vault with only its rent exemption left.
    const poor = await createHarness({ startTime: FIRST_START - 86_400 });
    const skr = (await generateKeyPairSigner()).address;
    poor.injectMint(skr, await mintAuthorityPda(), 0n, 6);
    const empty = await generateKeyPairSigner();
    await poor.injectConfig({ skrMint: skr, sponsor: empty.address });
    await poor.fund(await faucetVaultPda(), 1_000_000n);
    const runner = await poor.newWallet();

    expectProgramError(
      await poor.send([await topUpSponsorIx(runner.address, empty.address)], runner),
      'FaucetEmpty',
    );
    expect(poor.balance(empty.address)).toBe(0n);
  });

  it('refuses an address that is not the configured sponsor', async () => {
    await drainTheFloat();
    const impostor = await generateKeyPairSigner();
    expectProgramError(
      await h.send([await topUpSponsorIx(anybody.address, impostor.address)], anybody),
      'AccountMismatch',
    );
    expect(h.balance(impostor.address)).toBe(0n);
    expect(getAddressDecoder).toBeDefined();
  });

  it('is idempotent enough to run every pass: the second is simply refused', async () => {
    await drainTheFloat();
    await h.sendOrThrow([await topUpSponsorIx(anybody.address, sponsor.address)], anybody);
    const spentAfterFirst = (await h.readConfig()).faucet.lamportsToday;

    // The crank runs it again ten minutes later; the float is full, so nothing happens.
    h.advance(600);
    expectProgramError(await topUp(), 'SponsorFloatSufficient');
    expect((await h.readConfig()).faucet.lamportsToday).toBe(spentAfterFirst);
  });
});
