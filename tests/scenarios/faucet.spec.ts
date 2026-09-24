/**
 * The faucet, and the zero-SOL wallet it exists for. Task T037.
 *
 * A brand-new wallet holds **no SOL and cannot pay for any transaction**,
 * including its own faucet claim, and a program address cannot pay fees either
 * (research R13). So `claim_faucet` takes no member signature: the bundled
 * sponsor pays, and the vault reimburses its fee and any rent it paid in the
 * same instruction, keeping the sponsor's balance flat.
 *
 * The cap is a **lamport budget, not a claim counter**, and that distinction is
 * the point of this file. A recipient already holding `sol_top_up` needs no
 * top-up — so under a claim counter, the rent reimbursed for its brand-new
 * `Profile` and token account would leave the vault uncapped, and the same
 * recycled 0.02 SOL could buy account creation for fresh wallet after fresh
 * wallet. Every lamport that leaves the vault is charged instead.
 *
 * And a claim that moves **no** lamports at all — the recipient pays its own
 * fee and already holds the top-up, as every scripted wallet does (R12) —
 * spends none of the budget and is not capped.
 */

import { generateKeyPairSigner, getAddressDecoder, type Address, type KeyPairSigner } from '@solana/kit';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  DEFAULT_CHIPS,
  FIRST_START,
  SKR,
  createHarness,
  expectProgramError,
  oneEvent,
  type Harness,
} from '../helpers/harness.js';
import { claimFaucetIx } from '../helpers/instructions.js';
import { associatedTokenPda, faucetVaultPda, mintAuthorityPda, profilePda } from '../helpers/pdas.js';

const SOL = 1_000_000_000n;
const SOL_TOP_UP = 20_000_000n;
const SPONSOR_FLOAT = 50_000_000n;
const DAILY_CAP = 720_000_000n;
/** Days turn over at 05:00 UTC (OD-6). */
const todayAt = (now: number) => BigInt(Math.floor((now - 18_000) / 86_400));

/** `FaucetClaimed { recipient, skr, lamports }`. */
const decodeClaimed = (bytes: Uint8Array) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset);
  return {
    recipient: getAddressDecoder().decode(bytes.slice(0, 32)),
    skr: view.getBigUint64(32, true),
    lamports: view.getBigUint64(40, true),
  };
};

describe('claim_faucet (contracts §3, research R13)', () => {
  let h: Harness;
  let mint: Address;
  let sponsor: KeyPairSigner;
  let vault: Address;

  const vaultBalance = () => h.balance(vault);

  beforeEach(async () => {
    h = await createHarness({ startTime: FIRST_START - 86_400 });
    mint = (await generateKeyPairSigner()).address;
    // The program's own PDA is the mint authority, so the faucet never runs out
    // of SKR and nothing else can mint any.
    h.injectMint(mint, await mintAuthorityPda(), 0n, 6);

    sponsor = await generateKeyPairSigner();
    await h.fund(sponsor.address, SPONSOR_FLOAT);

    await h.injectConfig({ skrMint: mint, sponsor: sponsor.address });
    vault = await faucetVaultPda();
    await h.fund(vault, 25n * SOL);
  });

  /** A wallet with no SOL at all — the case the faucet exists for. */
  const zeroSolWallet = async (): Promise<KeyPairSigner> => generateKeyPairSigner();

  const claim = async (recipient: Address, payer: KeyPairSigner = sponsor) =>
    h.send([await claimFaucetIx(payer.address, recipient, mint, sponsor.address)], payer);

  const skrOf = async (wallet: Address) => h.tokenBalance(await associatedTokenPda(wallet, mint));

  // ------------------------------------------------------- the zero-SOL wallet

  it('funds a wallet holding nothing, paid for by the sponsor (FR-042a)', async () => {
    const newcomer = await zeroSolWallet();
    expect(h.balance(newcomer.address)).toBe(0n);

    const sponsorBefore = h.balance(sponsor.address);
    const vaultBefore = vaultBalance();

    const result = await claim(newcomer.address);
    expect(result.ok, result.logs.join('\n')).toBe(true);

    // SOL to spend, and SKR to stake with.
    expect(h.balance(newcomer.address)).toBe(SOL_TOP_UP);
    expect(await skrOf(newcomer.address)).toBe(DEFAULT_CHIPS[3]);

    // The sponsor ends exactly where it started — not merely no poorer. Its
    // fee and the rent it paid for the new accounts come back out of the
    // vault, and nothing more: reimbursing the fee twice would leave it above
    // its float, and `toBeGreaterThanOrEqual` is what let that through once
    // already.
    expect(h.balance(sponsor.address), 'the sponsor is reimbursed exactly once')
      .toBe(sponsorBefore);

    // And every lamport that left the vault is charged to the day.
    const spent = vaultBefore - vaultBalance();
    expect(spent).toBeGreaterThan(SOL_TOP_UP);
    const config = await h.readConfig();
    expect(config.faucet.lamportsToday).toBe(spent);
    expect(config.faucet.lamportDay).toBe(todayAt(h.now()));

    const profile = await h.readProfile(newcomer.address);
    expect(profile.wallet).toBe(newcomer.address);
    expect(profile.lastFaucetDay).toBe(todayAt(h.now()));

    const event = decodeClaimed(oneEvent(result, 'FaucetClaimed'));
    expect(event.recipient).toBe(newcomer.address);
    expect(event.skr).toBe(DEFAULT_CHIPS[3]);
    expect(event.lamports).toBe(spent);
  });

  it('mints the highest top tier ever published, not today\'s (FR-042a, R15)', async () => {
    // A re-price lowered the tiers after an older, dearer cohort copied them.
    const cheaper: [bigint, bigint, bigint, bigint] = [1n * SKR, 2n * SKR, 3n * SKR, 4n * SKR];
    await h.injectConfig({
      skrMint: mint,
      sponsor: sponsor.address,
      chipSet: { amounts: cheaper, version: 2, publishedAt: 0n, maxTopTier: DEFAULT_CHIPS[3] },
    });

    const newcomer = await zeroSolWallet();
    await h.sendOrThrow([await claimFaucetIx(sponsor.address, newcomer.address, mint, sponsor.address)], sponsor);
    expect(await skrOf(newcomer.address), 'enough for any open cohort\'s top chip').toBe(DEFAULT_CHIPS[3]);
  });

  it('takes one claim per recipient per day, and another after the boundary (ES-0a)', async () => {
    const newcomer = await zeroSolWallet();
    await h.sendOrThrow([await claimFaucetIx(sponsor.address, newcomer.address, mint, sponsor.address)], sponsor);

    expectProgramError(await claim(newcomer.address), 'FaucetAlreadyClaimedToday');
    // Later the same day is still the same day.
    h.advance(3_600);
    expectProgramError(await claim(newcomer.address), 'FaucetAlreadyClaimedToday');
    expect(await skrOf(newcomer.address), 'and nothing was minted twice').toBe(DEFAULT_CHIPS[3]);

    // 05:00 UTC turns the day over.
    const today = todayAt(h.now());
    h.setTime(Number((today + 1n) * 86_400n + 18_000n));
    expect(todayAt(h.now())).toBe(today + 1n);
    const again = await claim(newcomer.address);
    expect(again.ok, again.logs.join('\n')).toBe(true);
    expect(await skrOf(newcomer.address)).toBe(2n * DEFAULT_CHIPS[3]!);
  });

  it('sends no top-up to a wallet that already holds it, but still charges the rent (research R13)', async () => {
    // The hole a claim *counter* would leave: no top-up is due, yet the vault
    // still pays for a brand-new Profile and token account.
    const funded = await generateKeyPairSigner();
    await h.fund(funded.address, SOL_TOP_UP);
    expect(h.svm.getAccount(await profilePda(funded.address)).exists).toBe(false);

    const vaultBefore = vaultBalance();
    await h.sendOrThrow([await claimFaucetIx(sponsor.address, funded.address, mint, sponsor.address)], sponsor);

    // No top-up: the wallet is no richer in SOL than the accounts it now owns.
    expect(h.balance(funded.address)).toBe(SOL_TOP_UP);
    const spent = vaultBefore - vaultBalance();
    expect(spent, 'the rent and fee still left the vault').toBeGreaterThan(0n);
    expect(spent).toBeLessThan(SOL_TOP_UP);
    expect((await h.readConfig()).faucet.lamportsToday).toBe(spent);
  });

  it('charges nothing at all when the recipient pays for itself and needs no top-up (R12)', async () => {
    // A scripted wallet: its own SOL, its own accounts already made.
    const scripted = await generateKeyPairSigner();
    await h.fund(scripted.address, 1n * SOL);
    await h.injectProfile(scripted.address);
    h.injectTokenAccount(await associatedTokenPda(scripted.address, mint), mint, scripted.address, 0n);

    const vaultBefore = vaultBalance();
    const result = await h.sendOrThrow(
      [await claimFaucetIx(scripted.address, scripted.address, mint, sponsor.address)],
      scripted,
    );

    expect(vaultBalance(), 'not one lamport out of the vault').toBe(vaultBefore);
    expect((await h.readConfig()).faucet.lamportsToday, 'so nothing is charged to the day').toBe(0n);
    expect(await skrOf(scripted.address), 'and the SKR is still minted').toBe(DEFAULT_CHIPS[3]);
    expect(decodeClaimed(oneEvent(result, 'FaucetClaimed')).lamports).toBe(0n);
  });

  it('refuses before the first chip set is published, sparing the day\'s claim', async () => {
    // Between `initialize` and the first `publish_chip_set`, `max_top_tier` is
    // zero. A claim would mint no SKR and still stamp `last_faucet_day`,
    // spending the recipient's one claim of the day on nothing and leaving
    // them unable to make a real one until the next 05:00 UTC boundary.
    await h.injectConfig({
      skrMint: mint,
      sponsor: sponsor.address,
      chipSet: { amounts: [0n, 0n, 0n, 0n], version: 0, publishedAt: 0n, maxTopTier: 0n },
    });

    const newcomer = await zeroSolWallet();
    const vaultBefore = vaultBalance();
    expectProgramError(await claim(newcomer.address), 'ChipSetNotPublished');

    // Nothing written, nothing spent, and the claim still available.
    expect(h.svm.getAccount(await profilePda(newcomer.address)).exists).toBe(false);
    expect(vaultBalance()).toBe(vaultBefore);
    expect((await h.readConfig()).faucet.lamportsToday).toBe(0n);
  });

  // ------------------------------------------------------------- the day's cap

  it('refuses once the day\'s lamport budget is spent, and nothing is half-done (ES-0b)', async () => {
    // Put the day's counter just under the cap.
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

    const newcomer = await zeroSolWallet();
    const vaultBefore = vaultBalance();
    expectProgramError(await claim(newcomer.address), 'FaucetCapReached');

    expect(vaultBalance(), 'no partial payout').toBe(vaultBefore);
    expect(h.balance(newcomer.address)).toBe(0n);
    expect(h.svm.getAccount(await profilePda(newcomer.address)).exists).toBe(false);
  });

  it('starts a fresh budget on the next day', async () => {
    await h.injectConfig({
      skrMint: mint,
      sponsor: sponsor.address,
      faucet: {
        solTopUp: SOL_TOP_UP,
        sponsorFloat: SPONSOR_FLOAT,
        dailyLamportCap: DAILY_CAP,
        lamportDay: todayAt(h.now()),
        lamportsToday: DAILY_CAP,
      },
    });
    const newcomer = await zeroSolWallet();
    expectProgramError(await claim(newcomer.address), 'FaucetCapReached');

    const today = todayAt(h.now());
    h.setTime(Number((today + 1n) * 86_400n + 18_000n));
    const result = await claim(newcomer.address);
    expect(result.ok, result.logs.join('\n')).toBe(true);

    const config = await h.readConfig();
    expect(config.faucet.lamportDay).toBe(today + 1n);
    expect(config.faucet.lamportsToday).toBeLessThan(DAILY_CAP);
    expect(config.faucet.lamportsToday).toBeGreaterThan(0n);
  });

  it('refuses when the vault cannot cover what the claim would move (6052)', async () => {
    // A vault holding its rent exemption and little else.
    const drained = await createHarness({ startTime: FIRST_START - 86_400 });
    const skr = (await generateKeyPairSigner()).address;
    drained.injectMint(skr, await mintAuthorityPda(), 0n, 6);
    const payer = await generateKeyPairSigner();
    await drained.fund(payer.address, SPONSOR_FLOAT);
    await drained.injectConfig({ skrMint: skr, sponsor: payer.address });
    await drained.fund(await faucetVaultPda(), 2_000_000n);

    const newcomer = await generateKeyPairSigner();
    const before = drained.balance(await faucetVaultPda());
    expectProgramError(
      await drained.send([await claimFaucetIx(payer.address, newcomer.address, skr, payer.address)], payer),
      'FaucetEmpty',
    );
    expect(drained.balance(await faucetVaultPda())).toBe(before);
    expect(drained.balance(newcomer.address)).toBe(0n);
  });

  // ------------------------------------------------------------- who may pay

  it('accepts the recipient as its own payer', async () => {
    const self = await generateKeyPairSigner();
    await h.fund(self.address, 1n * SOL);

    const result = await h.send([await claimFaucetIx(self.address, self.address, mint, sponsor.address)], self);
    expect(result.ok, result.logs.join('\n')).toBe(true);
    expect(await skrOf(self.address)).toBe(DEFAULT_CHIPS[3]);
    // Topped up to the floor, not beyond: it already had more.
    expect(h.balance(self.address)).toBeGreaterThan(SOL_TOP_UP);
  });

  it('refuses a payer who is neither the sponsor nor the recipient (6002)', async () => {
    const stranger = await h.newWallet();
    const newcomer = await zeroSolWallet();
    expectProgramError(await claim(newcomer.address, stranger), 'NotSponsorOrRecipient');
    expect(h.balance(newcomer.address)).toBe(0n);
  });

  it('needs no signature from the recipient at all (research R13)', async () => {
    const newcomer = await zeroSolWallet();
    const ix = await claimFaucetIx(sponsor.address, newcomer.address, mint, sponsor.address);
    // The recipient appears as a writable account, never as a signer — which is
    // what lets a wallet with zero SOL be funded without partial signing.
    const asAccount = ix.accounts?.find((a) => a.address === newcomer.address);
    expect(asAccount).toBeDefined();
    expect(String(asAccount?.role)).not.toContain('SIGNER');
    expect((await h.send([ix], sponsor)).ok).toBe(true);
  });

  it('refuses a recipient argument that is not the recipient account', async () => {
    const newcomer = await zeroSolWallet();
    const other = await zeroSolWallet();
    const ix = await claimFaucetIx(sponsor.address, newcomer.address, mint, sponsor.address);
    // Same accounts, different name in the argument.
    const forged = {
      ...ix,
      data: (await claimFaucetIx(sponsor.address, other.address, mint, sponsor.address)).data,
    };
    expect((await h.send([forged], sponsor)).ok).toBe(false);
    expect(h.balance(newcomer.address)).toBe(0n);
  });
});
