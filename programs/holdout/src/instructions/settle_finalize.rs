//! `settle_finalize` (contracts/program-instructions §2). Task T079.
//!
//! One call, once, and the figures it writes are the same for every member
//! (FR-031, INV-5, INV-6). The shares are **per tier**, not per member: a
//! cohort has exactly four chip amounts (FR-055), so four numbers describe
//! every finisher and finalisation costs the same whether the cohort holds ten
//! members or a thousand (research R10).
//!
//! ## Nobody finished: the whole pool goes to the fee destination
//!
//! A cohort nobody finished sends its whole pool to the fee destination, and
//! nothing carries over into another cohort (FR-037, decided 2026-10-05). So
//! settling it needs no other cohort, its outcome depends on nothing but its
//! own state, and no caller's timing or omission can change it (FR-030a).

use anchor_lang::prelude::*;
use anchor_spl::token::{self, Token, TokenAccount, Transfer};

use crate::{
    errors::HoldoutError,
    events::CohortSettled,
    guards,
    math::settlement,
    state::{
        cohort::{Cohort, COHORT_SEED, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        league::{LeagueConfig, LEAGUE_SEED},
    },
};

/// The large accounts are boxed: two `Cohort`s and a `Config` do not fit in
/// `try_accounts`' own SBF stack frame.
#[derive(Accounts)]
pub struct SettleFinalize<'info> {
    /// Anyone. The caller's identity never changes the outcome (FR-030a).
    #[account(mut)]
    pub caller: Signer<'info>,

    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,

    #[account(
        seeds = [LEAGUE_SEED, &[league.league_id]],
        bump = league.bump,
    )]
    pub league: Box<Account<'info, LeagueConfig>>,

    #[account(
        mut,
        constraint = cohort.league_id == league.league_id @ HoldoutError::AccountMismatch,
    )]
    pub cohort: Box<Account<'info, Cohort>>,

    #[account(
        mut,
        seeds = [VAULT_SEED, cohort.key().as_ref()],
        bump = cohort.vault_bump,
        constraint = vault.mint == config.skr_mint @ HoldoutError::AccountMismatch,
    )]
    pub vault: Box<Account<'info, TokenAccount>>,

    #[account(
        mut,
        constraint = fee_destination.key() == config.fee_destination @ HoldoutError::AccountMismatch,
    )]
    pub fee_destination: Box<Account<'info, TokenAccount>>,

    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler<'info>(ctx: Context<'info, SettleFinalize<'info>>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    guards::require_settling(&ctx.accounts.cohort, now)?;
    require!(
        ctx.accounts.cohort.counted == ctx.accounts.cohort.member_count,
        HoldoutError::CountIncomplete
    );

    let cohort_key = ctx.accounts.cohort.key();
    let league_id = ctx.accounts.cohort.league_id;
    let index_bytes = ctx.accounts.cohort.index.to_le_bytes();
    let bump = [ctx.accounts.cohort.bump];
    let signer: &[&[u8]] = &[COHORT_SEED, &[league_id], &index_bytes, &bump];

    let pool = ctx.accounts.cohort.pool;
    let total_finisher_stake = ctx.accounts.cohort.total_finisher_stake;

    if total_finisher_stake > 0 {
        let chips = ctx.accounts.cohort.chips;
        let mut shares = [0u64; 4];
        for tier in 0..4 {
            shares[tier] = settlement::share_for_tier(pool, chips[tier], total_finisher_stake);
        }
        let remainder =
            settlement::rounding_remainder(pool, ctx.accounts.cohort.finishers_per_tier, shares);

        if remainder > 0 {
            transfer_from_vault(
                &ctx,
                signer,
                ctx.accounts.fee_destination.to_account_info(),
                remainder,
            )?;
        }

        let cohort = &mut ctx.accounts.cohort;
        cohort.share_per_tier = shares;
        cohort.rounding_remainder = remainder;
        cohort.pool = pool
            .checked_sub(remainder)
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        cohort.settled = true;

        emit!(CohortSettled {
            cohort: cohort_key,
            finishers: cohort.finisher_count,
            total_finisher_stake,
            share_per_tier: shares,
            rounding_remainder: remainder,
        });

        return Ok(());
    }

    // Nobody finished: the whole pool goes to the fee destination (FR-037).
    let fee = settlement::no_finisher_fee(pool);
    if fee > 0 {
        transfer_from_vault(
            &ctx,
            signer,
            ctx.accounts.fee_destination.to_account_info(),
            fee,
        )?;
    }

    let cohort = &mut ctx.accounts.cohort;
    cohort.no_finisher_fee = fee;
    cohort.pool = 0;
    cohort.settled = true;

    emit!(CohortSettled {
        cohort: cohort_key,
        finishers: 0,
        total_finisher_stake: 0,
        share_per_tier: [0; 4],
        rounding_remainder: 0,
    });

    Ok(())
}

/// Move SKR out of this cohort's vault, signed by the cohort PDA that holds it.
fn transfer_from_vault<'info>(
    ctx: &Context<'info, SettleFinalize<'info>>,
    signer: &[&[u8]],
    to: AccountInfo<'info>,
    amount: u64,
) -> Result<()> {
    token::transfer(
        CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            Transfer {
                from: ctx.accounts.vault.to_account_info(),
                to,
                authority: ctx.accounts.cohort.to_account_info(),
            },
            &[signer],
        ),
        amount,
    )
}
