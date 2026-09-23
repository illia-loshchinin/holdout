/**
 * Instruction builders for the setup instructions — `initialize`, `create_league`,
 * `set_flags`, `publish_chip_set` and `open_cohort` (contracts/program-instructions
 * §1–§2). Task T017b; `set_flags` added with T028.
 *
 * ## Why these are written by hand
 *
 * The Codama client (`tests/clients/holdout/`) is generated from the program's own
 * IDL, and the IDL has no instructions until T027. More to the point, a client
 * generated from the program can only agree with the program; these builders are
 * written from the contract, so a scenario using them fails when the program drifts
 * from what `program-instructions.md` says. Argument order is the contract's own.
 *
 * ## What the contract leaves to the implementation
 *
 * The contract names the accounts each instruction touches but not their order, and
 * it does not say how the mint or the fee destination are addressed. The order below
 * is therefore a decision, made here and followed by T027, T028, T055 and T058:
 *
 *  - Signers first, then the state the instruction reads or writes, then programs.
 *  - The SKR mint is a fresh keypair that signs `initialize` (§6 lists no mint seed).
 *  - `fee_destination` is the treasury wallet's **associated** token account.
 *  - The faucet vault is a system-owned PDA holding lamports; `initialize` funds it
 *    to rent exemption and the Operator tops it up by an ordinary transfer.
 *  - `open_cohort` takes the previous cohort as an optional account: the program's
 *    own ID in that slot means "none" (Anchor's convention), which index 0 sends.
 */

import { createHash } from 'node:crypto';

import {
  AccountRole,
  getAddressEncoder,
  getI64Encoder,
  getU16Encoder,
  getU32Encoder,
  getU64Encoder,
  getU8Encoder,
  type Address,
  type Instruction,
} from '@solana/kit';

import {
  ASSOCIATED_TOKEN_PROGRAM,
  PROGRAM_ID,
  SYSTEM_PROGRAM,
  TOKEN_PROGRAM,
  associatedTokenPda,
  cohortPda,
  configPda,
  faucetVaultPda,
  leaguePda,
  mintAuthorityPda,
  programDataPda,
  vaultPda,
} from './pdas.js';

/** Anchor's default: the first 8 bytes of `sha256("global:<snake_case_name>")`. */
export const instructionDiscriminator = (name: string): Uint8Array =>
  new Uint8Array(createHash('sha256').update(`global:${name}`).digest().subarray(0, 8));

const u8 = (n: number) => new Uint8Array(getU8Encoder().encode(n));
const u16 = (n: number) => new Uint8Array(getU16Encoder().encode(n));
const u32 = (n: number) => new Uint8Array(getU32Encoder().encode(n));
const u64 = (n: bigint | number) => new Uint8Array(getU64Encoder().encode(BigInt(n)));
const i64 = (n: bigint | number) => new Uint8Array(getI64Encoder().encode(BigInt(n)));
const key = (a: Address) => new Uint8Array(getAddressEncoder().encode(a));

const concat = (...parts: Uint8Array[]): Uint8Array => {
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const part of parts) {
    out.set(part, at);
    at += part.length;
  }
  return out;
};

const ro = (address: Address) => ({ address, role: AccountRole.READONLY });
const rw = (address: Address) => ({ address, role: AccountRole.WRITABLE });
const roSigner = (address: Address) => ({ address, role: AccountRole.READONLY_SIGNER });
const rwSigner = (address: Address) => ({ address, role: AccountRole.WRITABLE_SIGNER });

// ------------------------------------------------------------------ initialize

export interface InitializeArgs {
  operator: Address;
  sponsor: Address;
  treasuryWallet: Address;
  faucetSolTopUp: bigint | number;
  faucetDailyLamportCap: bigint | number;
  faucetSponsorFloat: bigint | number;
}

/**
 * `initialize(operator, sponsor, treasury_wallet, faucet_sol_top_up,
 * faucet_daily_lamport_cap, faucet_sponsor_float)`. The `admin` signs and pays;
 * `skrMint` is a new keypair that signs too, so both must reach `send()`.
 */
export const initializeIx = async (
  admin: Address,
  skrMint: Address,
  args: InitializeArgs,
): Promise<Instruction> => ({
  programAddress: PROGRAM_ID,
  accounts: [
    rwSigner(admin),
    rw(await configPda()),
    ro(PROGRAM_ID),
    ro(await programDataPda()),
    rwSigner(skrMint),
    ro(await mintAuthorityPda()),
    ro(args.treasuryWallet),
    rw(await associatedTokenPda(args.treasuryWallet, skrMint)),
    rw(await faucetVaultPda()),
    ro(SYSTEM_PROGRAM),
    ro(TOKEN_PROGRAM),
    ro(ASSOCIATED_TOKEN_PROGRAM),
  ],
  data: concat(
    instructionDiscriminator('initialize'),
    key(args.operator),
    key(args.sponsor),
    key(args.treasuryWallet),
    u64(args.faucetSolTopUp),
    u64(args.faucetDailyLamportCap),
    u64(args.faucetSponsorFloat),
  ),
});

// ---------------------------------------------------------------- create_league

export interface CreateLeagueArgs {
  leagueId: number;
  durationDays: number;
  repsPerDay: number;
  exercise: number;
  firstStart: bigint | number;
  dayLength: bigint | number;
  cadence: bigint | number;
  joinLock: bigint | number;
}

/** `create_league(league_id, duration_days, reps_per_day, exercise, first_start, day_length, cadence, join_lock)`. */
export const createLeagueIx = async (admin: Address, args: CreateLeagueArgs): Promise<Instruction> => ({
  programAddress: PROGRAM_ID,
  accounts: [
    rwSigner(admin),
    ro(await configPda()),
    rw(await leaguePda(args.leagueId)),
    ro(SYSTEM_PROGRAM),
  ],
  data: concat(
    instructionDiscriminator('create_league'),
    u8(args.leagueId),
    u8(args.durationDays),
    u16(args.repsPerDay),
    u8(args.exercise),
    i64(args.firstStart),
    i64(args.dayLength),
    i64(args.cadence),
    i64(args.joinLock),
  ),
});

// ------------------------------------------------------------------ set_flags

/** `set_flags(ladder_gate_enabled, genesis_gate_enabled)` — the admin signs; nothing is paid. */
export const setFlagsIx = async (
  admin: Address,
  ladderGateEnabled: boolean,
  genesisGateEnabled: boolean,
): Promise<Instruction> => ({
  programAddress: PROGRAM_ID,
  accounts: [roSigner(admin), rw(await configPda())],
  data: concat(instructionDiscriminator('set_flags'), u8(ladderGateEnabled ? 1 : 0), u8(genesisGateEnabled ? 1 : 0)),
});

// ------------------------------------------------------------ publish_chip_set

/** `publish_chip_set(amounts: [u64; 4])` — the operator signs, and no price is passed (constitution III). */
export const publishChipSetIx = async (
  operator: Address,
  amounts: readonly [bigint | number, bigint | number, bigint | number, bigint | number],
): Promise<Instruction> => ({
  programAddress: PROGRAM_ID,
  accounts: [roSigner(operator), rw(await configPda())],
  data: concat(instructionDiscriminator('publish_chip_set'), ...amounts.map(u64)),
});

// ----------------------------------------------------------------- open_cohort

/**
 * `open_cohort(league_id, index)`, permissionless: `payer` only pays the rent.
 * `previousCohort` is required for `index > 0` and omitted for index 0.
 */
export const openCohortIx = async (
  payer: Address,
  skrMint: Address,
  leagueId: number,
  index: number,
  previousCohort?: Address,
): Promise<Instruction> => {
  const cohort = await cohortPda(leagueId, index);
  return {
    programAddress: PROGRAM_ID,
    accounts: [
      rwSigner(payer),
      ro(await configPda()),
      rw(await leaguePda(leagueId)),
      rw(cohort),
      rw(await vaultPda(cohort)),
      ro(previousCohort ?? PROGRAM_ID),
      ro(skrMint),
      ro(SYSTEM_PROGRAM),
      ro(TOKEN_PROGRAM),
    ],
    data: concat(instructionDiscriminator('open_cohort'), u8(leagueId), u32(index)),
  };
};
