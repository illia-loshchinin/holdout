/**
 * Program-derived addresses (contracts/program-instructions §6). Task T016.
 *
 * One definition of every seed, mirroring the constants in
 * `programs/holdout/src/state/*.rs`. The Kotlin core has its own copy pinned to
 * `fixtures/pdas.json` (T029), so a seed that drifts fails a build somewhere.
 */

import { address, getAddressEncoder, getProgramDerivedAddress, getU32Encoder, getU8Encoder, type Address } from '@solana/kit';

/** The judged program's ID — `declare_id!` and `Anchor.toml` agree on it. */
export const PROGRAM_ID: Address = address('Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4');

/** The BPF upgradeable loader, which owns the program and its `ProgramData`. */
export const UPGRADEABLE_LOADER: Address = address('BPFLoaderUpgradeab1e11111111111111111111111');

export const TOKEN_PROGRAM: Address = address('TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA');
export const SYSTEM_PROGRAM: Address = address('11111111111111111111111111111111');
export const ASSOCIATED_TOKEN_PROGRAM: Address = address('ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL');

const utf8 = (s: string): Uint8Array => new TextEncoder().encode(s);
const addr = (a: Address): Uint8Array => new Uint8Array(getAddressEncoder().encode(a));
const u8 = (n: number): Uint8Array => new Uint8Array(getU8Encoder().encode(n));
/** Little-endian, as Anchor and the contract's §6 table say. */
const u32le = (n: number): Uint8Array => new Uint8Array(getU32Encoder().encode(n));

const derive = async (programAddress: Address, seeds: Uint8Array[]): Promise<Address> =>
  (await getProgramDerivedAddress({ programAddress, seeds }))[0];

/** The canonical bump the program stores and re-checks with `bump = account.bump`. */
const deriveBump = async (programAddress: Address, seeds: Uint8Array[]): Promise<number> =>
  (await getProgramDerivedAddress({ programAddress, seeds }))[1];

const configSeeds = () => [utf8('config')];
const leagueSeeds = (leagueId: number) => [utf8('league'), u8(leagueId)];
const cohortSeeds = (leagueId: number, index: number) => [utf8('cohort'), u8(leagueId), u32le(index)];
const vaultSeeds = (cohort: Address) => [utf8('vault'), addr(cohort)];
const membershipSeeds = (cohort: Address, wallet: Address) => [utf8('member'), addr(cohort), addr(wallet)];
const profileSeeds = (wallet: Address) => [utf8('profile'), addr(wallet)];

export const configPda = (): Promise<Address> => derive(PROGRAM_ID, configSeeds());

export const leaguePda = (leagueId: number): Promise<Address> => derive(PROGRAM_ID, leagueSeeds(leagueId));

export const cohortPda = (leagueId: number, index: number): Promise<Address> =>
  derive(PROGRAM_ID, cohortSeeds(leagueId, index));

export const vaultPda = (cohort: Address): Promise<Address> => derive(PROGRAM_ID, vaultSeeds(cohort));

export const membershipPda = (cohort: Address, wallet: Address): Promise<Address> =>
  derive(PROGRAM_ID, membershipSeeds(cohort, wallet));

export const profilePda = (wallet: Address): Promise<Address> => derive(PROGRAM_ID, profileSeeds(wallet));

/**
 * Bumps for the accounts the harness injects. The program re-derives each address
 * from its stored bump, so an injected account must carry the real one or every
 * instruction that reads it fails its seeds check.
 */
export const bumps = {
  config: (): Promise<number> => deriveBump(PROGRAM_ID, configSeeds()),
  league: (leagueId: number): Promise<number> => deriveBump(PROGRAM_ID, leagueSeeds(leagueId)),
  cohort: (leagueId: number, index: number): Promise<number> => deriveBump(PROGRAM_ID, cohortSeeds(leagueId, index)),
  vault: (cohort: Address): Promise<number> => deriveBump(PROGRAM_ID, vaultSeeds(cohort)),
  membership: (cohort: Address, wallet: Address): Promise<number> =>
    deriveBump(PROGRAM_ID, membershipSeeds(cohort, wallet)),
  profile: (wallet: Address): Promise<number> => deriveBump(PROGRAM_ID, profileSeeds(wallet)),
};

export const faucetVaultPda = (): Promise<Address> => derive(PROGRAM_ID, [utf8('faucet_vault')]);

export const mintAuthorityPda = (): Promise<Address> => derive(PROGRAM_ID, [utf8('mint_authority')]);

/** A wallet's associated token account for a mint — where the treasury's fees land. */
export const associatedTokenPda = (owner: Address, mint: Address): Promise<Address> =>
  derive(ASSOCIATED_TOKEN_PROGRAM, [addr(owner), addr(TOKEN_PROGRAM), addr(mint)]);

/** The loader's `ProgramData` account, whose upgrade authority `initialize` checks. */
export const programDataPda = (): Promise<Address> => derive(UPGRADEABLE_LOADER, [addr(PROGRAM_ID)]);
