/**
 * Instruction builders written from the contract rather than generated from the
 * program: `initialize`, `create_league`, `publish_chip_set` and `open_cohort`
 * (contracts/program-instructions §1–§2, task T017b), then `join_cohort` and
 * `leave_cohort` (§3, tasks T054 and T054a).
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
 * is therefore a decision, made here and followed by T027, T028, T055, T056, T057
 * and T058:
 *
 *  - Signers first, then the state the instruction reads or writes, then programs.
 *  - The SKR mint is a fresh keypair that signs `initialize` (§6 lists no mint seed).
 *  - `fee_destination` is the treasury wallet's **associated** token account —
 *    the **operations** destination since H35 — and `developer_destination` is
 *    the developer wallet's associated token account. Every instruction that
 *    moves a fee takes both, operations first ({@link FeeDestinations}).
 *  - A league's reserve vault is the token-account PDA `["reserve", league_id]`
 *    whose authority is the league PDA; `create_league` creates it and
 *    `settle_finalize` draws from and credits it (H35).
 *  - The faucet vault is a system-owned PDA holding lamports; `initialize` funds it
 *    to rent exemption and the Operator tops it up by an ordinary transfer.
 *  - `open_cohort` takes the previous cohort as an optional account: the program's
 *    own ID in that slot means "none" (Anchor's convention), which index 0 sends.
 *  - A member's SKR is held in their **associated** token account, the one
 *    `claim_faucet` creates, so `join_cohort` and `leave_cohort` address that.
 *  - `join_cohort` takes the member's running cohort by the same optional-account
 *    convention, and the program **requires** it whenever `profile.running` is
 *    set. Slot normalisation moves a lobby place into the running slot knowing
 *    only the cohort's address and start (data-model §1.5), so `running.end` is
 *    left at 0 and a guard trusting it would skip the overlap rule; the account
 *    is what the rule is checked against instead.
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
  membershipPda,
  mintAuthorityPda,
  profilePda,
  programDataPda,
  reservePda,
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

/**
 * The two token accounts the fee is split between (FR-027, FR-037f, H35):
 * `Config.fee_destination` — operations — and `Config.developer_destination`.
 */
export interface FeeDestinations {
  operations: Address;
  developer: Address;
}

export interface InitializeArgs {
  operator: Address;
  sponsor: Address;
  /** Owns the operations destination (`fee_destination`). */
  treasuryWallet: Address;
  /** Owns the developer destination (H35). */
  developerWallet: Address;
  faucetSolTopUp: bigint | number;
  faucetDailyLamportCap: bigint | number;
  faucetSponsorFloat: bigint | number;
}

/**
 * `initialize(operator, sponsor, treasury_wallet, developer_wallet,
 * faucet_sol_top_up, faucet_daily_lamport_cap, faucet_sponsor_float)`. The `admin` signs and pays;
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
    ro(args.developerWallet),
    rw(await associatedTokenPda(args.developerWallet, skrMint)),
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
    key(args.developerWallet),
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
  /** 0 = from the toes (Hard), 1 = knees allowed (Easy); anything else is `InvalidForm` (H35). */
  form: number;
  firstStart: bigint | number;
  dayLength: bigint | number;
  cadence: bigint | number;
  joinLock: bigint | number;
}

/**
 * `create_league(league_id, duration_days, reps_per_day, exercise, form,
 * first_start, day_length, cadence, join_lock)`. `skrMint` must be
 * `config.skr_mint`: the league's reserve vault is created here, a token
 * account of that mint whose authority is the league PDA (H35).
 */
export const createLeagueIx = async (
  admin: Address,
  skrMint: Address,
  args: CreateLeagueArgs,
): Promise<Instruction> => ({
  programAddress: PROGRAM_ID,
  accounts: [
    rwSigner(admin),
    ro(await configPda()),
    rw(await leaguePda(args.leagueId)),
    ro(skrMint),
    rw(await reservePda(args.leagueId)),
    ro(SYSTEM_PROGRAM),
    ro(TOKEN_PROGRAM),
  ],
  data: concat(
    instructionDiscriminator('create_league'),
    u8(args.leagueId),
    u8(args.durationDays),
    u16(args.repsPerDay),
    u8(args.exercise),
    u8(args.form),
    i64(args.firstStart),
    i64(args.dayLength),
    i64(args.cadence),
    i64(args.joinLock),
  ),
});

// ------------------------------------------------------------------- set_flags

const bool = (v: boolean) => new Uint8Array([v ? 1 : 0]);

/**
 * `set_flags(genesis_gate_enabled)` — admin only. The genesis gate ships off in
 * this build, and flipping it is the *only* difference between it and an
 * enforced gate, which is why it is an instruction rather than a redeploy.
 * There is no ladder gate (FR-003).
 */
export const setFlagsIx = async (admin: Address, genesisGateEnabled: boolean): Promise<Instruction> => ({
  programAddress: PROGRAM_ID,
  accounts: [roSigner(admin), rw(await configPda())],
  data: concat(instructionDiscriminator('set_flags'), bool(genesisGateEnabled)),
});

// ------------------------------------------------------------ publish_chip_set

/** A deadline no scenario's clock reaches, for publishes that are not about the deadline. */
export const NO_DEADLINE = 2n ** 63n - 1n;

/**
 * `publish_chip_set(amounts: [u64; 4], valid_until: i64)` — the operator signs,
 * and no price is passed (constitution III); the set is refused at or after
 * `validUntil` (6014, FR-055a, FR-056).
 */
export const publishChipSetIx = async (
  operator: Address,
  amounts: readonly [bigint | number, bigint | number, bigint | number, bigint | number],
  validUntil: bigint | number = NO_DEADLINE,
): Promise<Instruction> => ({
  programAddress: PROGRAM_ID,
  accounts: [roSigner(operator), rw(await configPda())],
  data: concat(instructionDiscriminator('publish_chip_set'), ...amounts.map(u64), i64(validUntil)),
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

// ----------------------------------------------------------------- join_cohort

/**
 * `join_cohort(tier)`. The member signs, pays the rent for their `Membership`
 * (and their `Profile` if this is their first), and the stake moves from their
 * associated SKR account into the cohort's vault.
 *
 * `runningCohort` is the cohort in the member's `profile.running` slot, and the
 * program requires it whenever that slot is set: slot normalisation cannot fill
 * `running.end`, so the overlap rule is checked against that cohort's own
 * account rather than a cached figure. Omitted — the program's own ID in the
 * slot, Anchor's convention for "none" — for a member with no running cohort.
 */
export const joinCohortIx = async (
  member: Address,
  skrMint: Address,
  leagueId: number,
  index: number,
  tier: number,
  runningCohort?: Address,
): Promise<Instruction> => {
  const cohort = await cohortPda(leagueId, index);
  return {
    programAddress: PROGRAM_ID,
    accounts: [
      rwSigner(member),
      ro(await configPda()),
      rw(cohort),
      rw(await vaultPda(cohort)),
      rw(await membershipPda(cohort, member)),
      rw(await profilePda(member)),
      rw(await associatedTokenPda(member, skrMint)),
      ro(runningCohort ?? PROGRAM_ID),
      ro(skrMint),
      ro(SYSTEM_PROGRAM),
      ro(TOKEN_PROGRAM),
    ],
    data: concat(instructionDiscriminator('join_cohort'), u8(tier)),
  };
};

// ---------------------------------------------------------------- leave_cohort

/**
 * `leave_cohort()`. A full refund with no deduction, the `Membership` closed and
 * its rent returned, and the lobby place cleared (FR-009). Open status only.
 */
export const leaveCohortIx = async (
  member: Address,
  skrMint: Address,
  leagueId: number,
  index: number,
): Promise<Instruction> => {
  const cohort = await cohortPda(leagueId, index);
  return {
    programAddress: PROGRAM_ID,
    accounts: [
      rwSigner(member),
      ro(await configPda()),
      rw(cohort),
      rw(await vaultPda(cohort)),
      rw(await membershipPda(cohort, member)),
      rw(await profilePda(member)),
      rw(await associatedTokenPda(member, skrMint)),
      ro(TOKEN_PROGRAM),
    ],
    data: instructionDiscriminator('leave_cohort'),
  };
};

// ------------------------------------------------------------------ submit_day

/**
 * `submit_day(day_index, proof_hash)`. The member signs and pays the fee; no
 * money moves. The proof is **emitted and not stored** (research R16, FR-025),
 * so nothing here writes 32 bytes per member per day into an account.
 */
export const submitDayIx = async (
  member: Address,
  leagueId: number,
  index: number,
  dayIndex: number,
  proofHash: Uint8Array,
): Promise<Instruction> => {
  if (proofHash.length !== 32) throw new Error(`a proof hash is 32 bytes, got ${proofHash.length}`);
  const cohort = await cohortPda(leagueId, index);
  return {
    programAddress: PROGRAM_ID,
    accounts: [
      rwSigner(member),
      ro(cohort),
      rw(await membershipPda(cohort, member)),
      rw(await profilePda(member)),
    ],
    data: concat(instructionDiscriminator('submit_day'), u8(dayIndex), proofHash),
  };
};

// ------------------------------------------------------------- record_forfeits

/** A page pair: the membership and the profile of its wallet. */
export interface ForfeitPair {
  membership: Address;
  profile: Address;
}

/** The pairs for a set of wallets in one cohort, in the order given. */
export const forfeitPairs = async (
  cohort: Address,
  wallets: readonly Address[],
): Promise<ForfeitPair[]> =>
  Promise.all(
    wallets.map(async (wallet) => ({
      membership: await membershipPda(cohort, wallet),
      profile: await profilePda(wallet),
    })),
  );

/**
 * `record_forfeits()`, permissionless: the caller pays the fee and the outcome
 * does not depend on who they are (FR-030a). The pairs ride in the remaining
 * accounts, up to ten of them, and pairs not in breach or already forfeited are
 * skipped rather than refused — so a page may be repeated and re-ordered freely
 * (research R9, INV-7).
 *
 * Each breached stake splits 70/5/5/10/10 (FR-027, H35): the pool part and the
 * two reserve parts stay in the vault — the reserve parts as `boost_due` /
 * `comeback_due` until the cohort finalizes — while the developer and
 * operations parts leave at once, one batched transfer each per page.
 */
export const recordForfeitsIx = async (
  caller: Address,
  fees: FeeDestinations,
  leagueId: number,
  index: number,
  pairs: readonly ForfeitPair[],
): Promise<Instruction> => {
  const cohort = await cohortPda(leagueId, index);
  return {
    programAddress: PROGRAM_ID,
    accounts: [
      rwSigner(caller),
      ro(await configPda()),
      rw(cohort),
      rw(await vaultPda(cohort)),
      rw(fees.operations),
      rw(fees.developer),
      ro(TOKEN_PROGRAM),
      ...pairs.flatMap((pair) => [rw(pair.membership), rw(pair.profile)]),
    ],
    data: instructionDiscriminator('record_forfeits'),
  };
};

// ---------------------------------------------------------------- settle_count

/**
 * `settle_count()`, permissionless and paged like `record_forfeits`. The
 * league's cohort `index - 1` is required for every index but 0: counting runs
 * in cohort order so every profile's streak does too (FR-061a) — and every
 * profile's comeback record, which a counted forfeit opens and a counted finish
 * of a later cohort in its window uses up (FR-037e).
 */
export const settleCountIx = async (
  caller: Address,
  fees: FeeDestinations,
  leagueId: number,
  index: number,
  pairs: readonly ForfeitPair[],
  previousCohort?: Address,
): Promise<Instruction> => {
  const cohort = await cohortPda(leagueId, index);
  return {
    programAddress: PROGRAM_ID,
    accounts: [
      rwSigner(caller),
      ro(await configPda()),
      ro(await leaguePda(leagueId)),
      rw(cohort),
      rw(await vaultPda(cohort)),
      rw(fees.operations),
      rw(fees.developer),
      ro(previousCohort ?? PROGRAM_ID),
      ro(TOKEN_PROGRAM),
      ...pairs.flatMap((pair) => [rw(pair.membership), rw(pair.profile)]),
    ],
    data: instructionDiscriminator('settle_count'),
  };
};

// ------------------------------------------------------------- settle_finalize

/**
 * `settle_finalize()`. With finishers it draws the boost from the league's
 * reserve, writes the shares on the boosted pool and funds the comeback
 * refunds; with none it splits the whole pool half to the developer and the
 * rest to operations (FR-037). Either way it then credits the cohort's reserve
 * shares to its league (FR-037c). Like `settle_count` it needs the league's
 * cohort `index - 1`, settled — an empty cohort is never counted, so this is
 * its only ordering check (FR-061a, INV-10).
 */
export const settleFinalizeIx = async (
  caller: Address,
  fees: FeeDestinations,
  leagueId: number,
  index: number,
  /** The league's cohort `index - 1`: derived when omitted, withheld with `null`. */
  previousCohort?: Address | null,
): Promise<Instruction> => {
  const cohort = await cohortPda(leagueId, index);
  const previous = previousCohort === undefined ? (index > 0 ? await cohortPda(leagueId, index - 1) : null) : previousCohort;
  return {
    programAddress: PROGRAM_ID,
    accounts: [
      rwSigner(caller),
      ro(await configPda()),
      rw(await leaguePda(leagueId)),
      rw(cohort),
      rw(await vaultPda(cohort)),
      rw(fees.operations),
      rw(fees.developer),
      rw(await reservePda(leagueId)),
      ro(previous ?? PROGRAM_ID),
      ro(TOKEN_PROGRAM),
    ],
    data: instructionDiscriminator('settle_finalize'),
  };
};

// ---------------------------------------------------------------- claim_payout

/**
 * `claim_payout()`: `stake + share_per_tier[tier]`, read from the finalised
 * cohort, plus any comeback refund — `comeback_entitled`, scaled by what the
 * reserve funded (FR-037e).
 */
export const claimPayoutIx = async (
  member: Address,
  skrMint: Address,
  leagueId: number,
  index: number,
): Promise<Instruction> => {
  const cohort = await cohortPda(leagueId, index);
  return {
    programAddress: PROGRAM_ID,
    accounts: [
      rwSigner(member),
      ro(await configPda()),
      rw(cohort),
      rw(await vaultPda(cohort)),
      rw(await membershipPda(cohort, member)),
      rw(await associatedTokenPda(member, skrMint)),
      ro(TOKEN_PROGRAM),
    ],
    data: instructionDiscriminator('claim_payout'),
  };
};

// ---------------------------------------------------------------- claim_faucet

/**
 * `claim_faucet(recipient)`. **The recipient does not sign** (research R13):
 * a brand-new wallet holds no SOL and cannot pay for its own first
 * transaction, so the bundled sponsor pays and is reimbursed from the vault in
 * the same instruction. A wallet that already holds SOL may pay for itself
 * instead, and `payer` is then the recipient.
 */
export const claimFaucetIx = async (
  payer: Address,
  recipient: Address,
  skrMint: Address,
  sponsor: Address,
): Promise<Instruction> => ({
  programAddress: PROGRAM_ID,
  accounts: [
    rwSigner(payer),
    rw(await configPda()),
    rw(recipient),
    rw(await profilePda(recipient)),
    rw(skrMint),
    ro(await mintAuthorityPda()),
    rw(await associatedTokenPda(recipient, skrMint)),
    rw(await faucetVaultPda()),
    rw(sponsor),
    ro(SYSTEM_PROGRAM),
    ro(TOKEN_PROGRAM),
    ro(ASSOCIATED_TOKEN_PROGRAM),
  ],
  data: concat(instructionDiscriminator('claim_faucet'), key(recipient)),
});

// -------------------------------------------------------------- top_up_sponsor

/**
 * `top_up_sponsor()`. Permissionless and **the sponsor does not sign**: the key
 * ships in the app, so anyone can empty its float and anyone must be able to
 * refill it, or a drain would end zero-SOL onboarding rather than costing a
 * day's allowance (FR-042b, research R13).
 */
export const topUpSponsorIx = async (caller: Address, sponsor: Address): Promise<Instruction> => ({
  programAddress: PROGRAM_ID,
  accounts: [
    rwSigner(caller),
    rw(await configPda()),
    rw(await faucetVaultPda()),
    rw(sponsor),
    ro(SYSTEM_PROGRAM),
  ],
  data: instructionDiscriminator('top_up_sponsor'),
});
