/**
 * The harness proving itself. Task T016.
 *
 * Nothing here tests the program's rules — no instruction exists yet (T027 on).
 * It tests that the tool scenarios will stand on is sound: the real binary loads
 * and runs, chain time moves when told to, `ProgramData` says who the upgrade
 * authority is, an injected state reads back exactly, and a snapshot of injected
 * state flows through the whole chain — encode, SVM, `getProgramAccounts`, decode —
 * into the invariant checker.
 */

import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { generateKeyPairSigner, type Address } from '@solana/kit';
import { beforeAll, describe, expect, it } from 'vitest';

import { assertInvariants, InvariantError } from '../invariants/checker.js';
import { ANCHOR_ERRORS, ERROR_CODES } from './errors.js';
import {
  DAY,
  DEFAULT_CHIPS,
  DEFAULT_PROGRAM_PATH,
  FIRST_START,
  HOUR,
  SKR,
  WEEK,
  createHarness,
  expectProgramError,
  type Harness,
} from './harness.js';
import { PROGRAM_ID, cohortPda, leaguePda, profilePda, programDataPda, vaultPda } from './pdas.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const IDL_PATH = resolve(HERE, '../../target/idl/holdout.json');

let h: Harness;
beforeAll(async () => {
  h = await createHarness();
});

describe('the program under test', () => {
  it('is the real built binary: an unknown instruction reaches Anchor and comes back as 101', async () => {
    const payer = await h.newWallet();
    // No instruction exists yet, so any 8-byte discriminator is unknown. Reaching
    // Anchor's fallback proves the ELF was loaded through the upgradeable loader
    // and executed, rather than the call failing before the program ran.
    const result = await h.send([{ programAddress: PROGRAM_ID, accounts: [], data: new Uint8Array(8) }], payer);
    expect(result.ok).toBe(false);
    expect(result.errorCode).toBe(ANCHOR_ERRORS.InstructionFallbackNotFound);
    expect(result.logs.some((l) => l.includes(PROGRAM_ID))).toBe(true);
  });

  it('reports a failure that is not a program error as no error code', async () => {
    const broke = await generateKeyPairSigner(); // never funded
    const result = await h.send([{ programAddress: PROGRAM_ID, accounts: [], data: new Uint8Array(8) }], broke);
    expect(result.ok).toBe(false);
    expect(result.errorCode).toBeNull();
  });

  it('names program errors, and refuses to pass a mismatch', async () => {
    const payer = await h.newWallet();
    const result = await h.send([{ programAddress: PROGRAM_ID, accounts: [], data: new Uint8Array(8) }], payer);
    expect(() => expectProgramError(result, 'PreviousCohortUnsettled')).toThrow(/expected PreviousCohortUnsettled \(6045\) but got 101/);
  });
});

describe('chain time', () => {
  it('starts a week before the first judged cohort', async () => {
    const fresh = await createHarness();
    expect(fresh.now()).toBe(FIRST_START - WEEK);
  });

  it('moves to an instant and by a distance, in seconds', () => {
    h.setTime(FIRST_START);
    expect(h.now()).toBe(FIRST_START);
    h.advance(DAY - 1);
    expect(h.now()).toBe(FIRST_START + DAY - 1);
    h.advance(1);
    expect(h.now()).toBe(FIRST_START + DAY);
  });

  it('can move backwards, which a scenario needs to replay two orders', () => {
    h.setTime(FIRST_START + 10);
    h.setTime(FIRST_START);
    expect(h.now()).toBe(FIRST_START);
  });

  it('keeps slots in step with a large jump', () => {
    const before = h.svm.getClock().slot;
    h.advance(WEEK);
    expect(h.svm.getClock().slot).toBeGreaterThan(before);
  });
});

describe('ProgramData and the upgrade authority (research R14)', () => {
  it('sits at the PDA the loader derives, which is pinned here', async () => {
    expect(await programDataPda()).toBe('J7HAVBVtxDEGrr6kcAfuit4WvNvEq6zwZJYiBr1DnNSG');
  });

  it('records the harness authority, and leaves the ELF where it was', async () => {
    const at = await programDataPda();
    const account = h.svm.getAccount(at);
    if (!account.exists) throw new Error('missing');
    expect(account.data[12]).toBe(1);
    expect(Buffer.from(account.data.slice(13, 45)).equals(Buffer.from(await addressBytes(h.upgradeAuthority.address)))).toBe(true);

    const elf = readFileSync(DEFAULT_PROGRAM_PATH);
    expect(account.data.length).toBe(45 + elf.length);
    expect(Buffer.from(account.data.slice(45)).equals(elf)).toBe(true);
  });

  it('can be handed to someone else, or revoked', async () => {
    const stranger = await generateKeyPairSigner();
    await h.setUpgradeAuthority(stranger.address);
    const at = await programDataPda();
    const moved = h.svm.getAccount(at);
    if (!moved.exists) throw new Error('missing');
    expect(Buffer.from(moved.data.slice(13, 45)).equals(Buffer.from(await addressBytes(stranger.address)))).toBe(true);

    await h.setUpgradeAuthority(null);
    const revoked = h.svm.getAccount(at);
    if (!revoked.exists) throw new Error('missing');
    expect(revoked.data[12]).toBe(0);
    expect(revoked.data.slice(13, 45).every((b) => b === 0)).toBe(true);

    await h.setUpgradeAuthority(h.upgradeAuthority.address);
  });

  it('still runs the program after the authority changed', async () => {
    const payer = await h.newWallet();
    const result = await h.send([{ programAddress: PROGRAM_ID, accounts: [], data: new Uint8Array(8) }], payer);
    expect(result.errorCode).toBe(ANCHOR_ERRORS.InstructionFallbackNotFound);
  });
});

describe('injected state reads back exactly', () => {
  it('a cohort, with a vault that holds what the cohort says it does', async () => {
    const league = h.freshTestLeague();
    const at = await h.injectCohort(league, 0, { heldStake: 400n, pool: 680n, memberCount: 5 });
    expect(at).toBe(await cohortPda(league.leagueId, 0));

    const back = await h.readCohort(league.leagueId, 0);
    expect(back.heldStake).toBe(400n);
    expect(back.pool).toBe(680n);
    expect(back.chips).toEqual(DEFAULT_CHIPS);
    expect(back.start).toBe(BigInt(FIRST_START));
    expect(back.joinCutoff).toBe(BigInt(FIRST_START - HOUR));

    expect(h.tokenBalance(await vaultPda(at))).toBe(1_080n);
  });

  it('a later cohort starts a week on, on the real cadence', async () => {
    const league = h.freshTestLeague();
    await h.injectCohort(league, 0);
    await h.injectCohort(league, 1);
    const second = await h.readCohort(league.leagueId, 1);
    expect(second.start).toBe(BigInt(FIRST_START + WEEK));
  });

  it('a membership and a profile', async () => {
    const league = h.freshTestLeague();
    const cohort = await h.injectCohort(league, 0);
    const wallet = (await generateKeyPairSigner()).address;

    await h.injectMembership(cohort, wallet, { daysCompleted: 4, state: 'Forfeited', tier: 1, stake: 25n * SKR });
    const m = await h.readMembership(cohort, wallet);
    expect(m).toMatchObject({ daysCompleted: 4, state: 'Forfeited', tier: 1, stake: 25n * SKR, wallet, cohort });

    await h.injectProfile(wallet, { qualified: true, lastFaucetDay: 9n });
    const p = await h.readProfile(wallet);
    expect(p.qualified).toBe(true);
    expect(p.lastFaucetDay).toBe(9n);
    expect(await profilePda(wallet)).toBeDefined();
  });

  it('a league, each test league getting an id of its own', async () => {
    const a = h.freshTestLeague();
    const b = h.freshTestLeague();
    expect(a.leagueId).toBeGreaterThanOrEqual(100);
    expect(b.leagueId).toBe(a.leagueId + 1);
    await h.injectLeague(a, { durationDays: 21 });
    expect(h.svm.getAccount(await leaguePda(a.leagueId)).exists).toBe(true);
  });
});

describe('a snapshot flows from the SVM into the invariant checker', () => {
  it('passes for a consistent cohort and names INV-1 when the vault is one unit short', async () => {
    const fresh = await createHarness();
    const league = fresh.freshTestLeague();
    await fresh.injectCohort(league, 0, { heldStake: 400n, pool: 680n });
    const ok = await snap(fresh);
    expect(() => assertInvariants(ok)).not.toThrow();

    const broken = await createHarness();
    const l2 = broken.freshTestLeague();
    await broken.injectCohort(l2, 0, { heldStake: 400n, pool: 680n, vaultAmount: 1_079n });
    try {
      assertInvariants(await snap(broken));
      throw new Error('should have failed');
    } catch (e) {
      expect(e).toBeInstanceOf(InvariantError);
      expect((e as InvariantError).invariant).toBe('INV-1');
    }
  });

  it('carries INV-9 across two cohorts of one test league, through the shared profile slot', async () => {
    const fresh = await createHarness();
    const league = fresh.freshTestLeague();
    // A cleared, then a forfeit one week later: the streak must read 0, longest 1.
    const a = await fresh.injectCohort(league, 0, { settled: true, counted: 1, memberCount: 1 });
    const b = await fresh.injectCohort(league, 1, { memberCount: 1, pool: 80n });
    const wallet = (await generateKeyPairSigner()).address;
    await fresh.injectMembership(a, wallet, { state: 'Claimed', daysCompleted: 7, counted: true });
    await fresh.injectMembership(b, wallet, { state: 'Forfeited', daysCompleted: 0, counted: true });

    const progress = (over: object) => ({ cleared: 0, forfeited: 0, currentStreak: 0, longestStreak: 0, lastChanged: 0n, ...over });
    const leagues = [progress({}), progress({}), progress({ cleared: 1, forfeited: 1, currentStreak: 0, longestStreak: 1 })] as never;
    await fresh.injectProfile(wallet, { leagues });
    fresh.setTime(FIRST_START + 3 * WEEK);
    const ok = await snap(fresh);
    expect(() => assertInvariants(ok)).not.toThrow();

    // What the old, order-dependent design would have left behind.
    const wrong = [progress({}), progress({}), progress({ cleared: 1, forfeited: 1, currentStreak: 1, longestStreak: 1 })] as never;
    await fresh.injectProfile(wallet, { leagues: wrong });
    try {
      assertInvariants(await snap(fresh));
      throw new Error('should have failed');
    } catch (e) {
      expect((e as InvariantError).invariant).toBe('INV-9');
    }
  });

  it('sees every account by its discriminator, with nothing registered', async () => {
    const fresh = await createHarness();
    const league = fresh.freshTestLeague();
    const cohort = await fresh.injectCohort(league, 0, { memberCount: 2 });
    const w1 = (await generateKeyPairSigner()).address;
    const w2 = (await generateKeyPairSigner()).address;
    await fresh.injectMembership(cohort, w1);
    await fresh.injectMembership(cohort, w2);
    await fresh.injectProfile(w1);
    const s = await fresh.snapshot();
    expect(s.cohorts).toHaveLength(1);
    expect(s.memberships).toHaveLength(2);
    expect(s.profiles).toHaveLength(1);
  });
});

describe('the error table', () => {
  const idlPresent = existsSync(IDL_PATH);

  it.runIf(idlPresent)('matches the built IDL, code for code', () => {
    const idl = JSON.parse(readFileSync(IDL_PATH, 'utf8')) as { errors: { code: number; name: string }[] };
    const built = new Map(idl.errors.map((e) => [e.name, e.code]));
    for (const [name, code] of Object.entries(ERROR_CODES)) {
      expect(built.get(name), `${name} in the IDL`).toBe(code);
    }
    expect(built.size, 'the IDL has no error this table lacks').toBe(Object.keys(ERROR_CODES).length);
  });

  it.skipIf(idlPresent)('needs `anchor build` to compare with the IDL', () => {
    // Reported as skipped, on purpose: a missing IDL is a build step not run, not a pass.
  });
});

// -------------------------------------------------------------------- helpers

const snap = (harness: Harness) => harness.snapshot();

async function addressBytes(a: Address): Promise<Uint8Array> {
  const { getAddressEncoder } = await import('@solana/kit');
  return new Uint8Array(getAddressEncoder().encode(a));
}
