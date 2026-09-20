//! Holdout — push-up commitment cohorts on Solana devnet.
//!
//! The contract is the only authority on funds (constitution I): no off-chain
//! key can move a stake, a pool or a carry-over. Breach is derived from chain
//! time (II), never declared by automation.
//!
//! Members read "the Daily 25" and "a Holdout"; this code stores a `Cohort` in
//! a league (spec §1a).

use anchor_lang::prelude::*;

// The matching keypair is NOT in the repository (`.gitignore` excludes every
// keypair). It lives at `~/.config/solana/holdout-program-keypair.json` and must
// be copied to `target/deploy/holdout-keypair.json` before `anchor deploy`, or
// Anchor generates a different key and the deployed address stops matching this
// one (quickstart §6).
declare_id!("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4");

pub mod errors;
pub mod events;
pub mod guards;
pub mod instructions;
pub mod math;
pub mod state;

use instructions::*;

#[program]
pub mod holdout {
    // The rest land here as their tasks complete (tasks.md T037–T080).
    // Nothing is implemented before its failing test exists (constitution VII).
    use super::*;

    /// Once, by the program's current upgrade authority (research R14).
    #[allow(clippy::too_many_arguments)]
    pub fn initialize(
        ctx: Context<Initialize>,
        operator: Pubkey,
        sponsor: Pubkey,
        treasury_wallet: Pubkey,
        faucet_sol_top_up: u64,
        faucet_daily_lamport_cap: u64,
        faucet_sponsor_float: u64,
    ) -> Result<()> {
        instructions::initialize::handler(
            ctx,
            operator,
            sponsor,
            treasury_wallet,
            faucet_sol_top_up,
            faucet_daily_lamport_cap,
            faucet_sponsor_float,
        )
    }

    /// Admin only. A real league keeps the devnet timing; a test league needs a
    /// `test-leagues` build (H30, research R11).
    #[allow(clippy::too_many_arguments)]
    pub fn create_league(
        ctx: Context<CreateLeague>,
        league_id: u8,
        duration_days: u8,
        reps_per_day: u16,
        exercise: u8,
        first_start: i64,
        day_length: i64,
        cadence: i64,
        join_lock: i64,
    ) -> Result<()> {
        instructions::create_league::handler(
            ctx,
            league_id,
            duration_days,
            reps_per_day,
            exercise,
            first_start,
            day_length,
            cadence,
            join_lock,
        )
    }

    /// Operator only. Four SKR amounts, never a price (constitution III).
    pub fn publish_chip_set(ctx: Context<PublishChipSet>, amounts: [u64; 4]) -> Result<()> {
        instructions::publish_chip_set::handler(ctx, amounts)
    }

    /// Permissionless: the caller pays the rent and nothing else (FR-030a).
    pub fn open_cohort(ctx: Context<OpenCohort>, league_id: u8, index: u32) -> Result<()> {
        instructions::open_cohort::handler(ctx, league_id, index)
    }

    /// A member picks a tier, never an amount (AC-13, FR-055).
    pub fn join_cohort(ctx: Context<JoinCohort>, tier: u8) -> Result<()> {
        instructions::join_cohort::handler(ctx, tier)
    }

    /// Unconditional and lossless while joining is open (FR-009).
    pub fn leave_cohort(ctx: Context<LeaveCohort>) -> Result<()> {
        instructions::leave_cohort::handler(ctx)
    }

    /// One completed day; the proof is emitted, never stored (research R16).
    pub fn submit_day(ctx: Context<SubmitDay>, day_index: u8, proof_hash: [u8; 32]) -> Result<()> {
        instructions::submit_day::handler(ctx, day_index, proof_hash)
    }

    /// Permissionless, paged and repeatable (AC-5, INV-7, research R9).
    pub fn record_forfeits<'info>(ctx: Context<'info, RecordForfeits<'info>>) -> Result<()> {
        instructions::record_forfeits::handler(ctx)
    }

    /// Admin only. The two gates this build ships off (FR-003, research R17).
    pub fn set_flags(
        ctx: Context<SetFlags>,
        ladder_gate_enabled: bool,
        genesis_gate_enabled: bool,
    ) -> Result<()> {
        instructions::set_flags::handler(ctx, ladder_gate_enabled, genesis_gate_enabled)
    }
}
