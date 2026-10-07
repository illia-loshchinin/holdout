//! `settle_finalize` (contracts/program-instructions §2). Task T079.
//!
//! One call, once, and the figures it writes are the same for every member
//! (FR-031, INV-5, INV-6). The shares are **per tier**, not per member: a
//! cohort has exactly four chip amounts (FR-055), so four numbers describe
//! every finisher and finalisation costs the same whether the cohort holds ten
//! members or a thousand (research R10).
//!
//! ## The league reserve, in cohort order (FR-037c–e, H35)
//!
//! With finishers, in this order: the cohort draws its **boost** —
//! `min(20% of its own pool, the boost reserve)` — and the shares are computed
//! on the boosted pool; it draws `min(comeback owed, the comeback reserve)` for
//! its finishers' **comeback refunds**; then it **credits** the reserve shares
//! of its own forfeits to the league. A cohort draws before it credits, so its
//! own forfeits fund later cohorts, never itself.
//!
//! The reserve moves only here, and both `settle_count` and this instruction
//! refuse cohort *i* until cohort *i − 1* of the league is settled — this one
//! too, because a cohort nobody joined is never counted — so finalization runs
//! in cohort order: what a cohort draws depends only on earlier cohorts of its league,
//! never on when the crank recorded anyone's forfeit (INV-10, constitution II).
//!
//! ## Nobody finished
//!
//! Its whole pool goes out — a third to the developer, the rest to operations —
//! and nothing carries over into another cohort (FR-037, H34, H35). Its reserve
//! shares are still credited to the league.

use anchor_lang::prelude::*;
use anchor_spl::token::{Token, TokenAccount};

use crate::{
    errors::HoldoutError,
    events::CohortSettled,
    guards,
    math::settlement,
    state::{
        cohort::{Cohort, COHORT_SEED, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        league::{LeagueConfig, LEAGUE_SEED, RESERVE_SEED},
    },
    vault::transfer_signed,
};

/// The large accounts are boxed: a `Cohort` and a `Config` do not fit in
/// `try_accounts`' own SBF stack frame beside the rest.
#[derive(Accounts)]
pub struct SettleFinalize<'info> {
    /// Anyone. The caller's identity never changes the outcome (FR-030a).
    #[account(mut)]
    pub caller: Signer<'info>,

    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,

    /// Writable: its reserves move here, and only here (INV-10).
    #[account(
        mut,
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

    /// Operations.
    #[account(
        mut,
        constraint = fee_destination.key() == config.fee_destination @ HoldoutError::AccountMismatch,
    )]
    pub fee_destination: Box<Account<'info, TokenAccount>>,

    #[account(
        mut,
        constraint = developer_destination.key() == config.developer_destination @ HoldoutError::AccountMismatch,
    )]
    pub developer_destination: Box<Account<'info, TokenAccount>>,

    #[account(
        mut,
        seeds = [RESERVE_SEED, &[league.league_id]],
        bump = league.reserve_vault_bump,
        constraint = reserve_vault.mint == config.skr_mint @ HoldoutError::AccountMismatch,
    )]
    pub reserve_vault: Box<Account<'info, TokenAccount>>,

    /// The league's cohort `index - 1`, required for every index but 0. A
    /// cohort nobody joined is never counted, so this is its only ordering
    /// check: without it an empty cohort could settle ahead of an older one
    /// and let the next cohort count past it (FR-061a, INV-10).
    pub previous_cohort: Option<Box<Account<'info, Cohort>>>,

    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler<'info>(ctx: Context<'info, SettleFinalize<'info>>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    guards::require_settling(&ctx.accounts.cohort, now)?;
    require!(
        ctx.accounts.cohort.counted == ctx.accounts.cohort.member_count,
        HoldoutError::CountIncomplete
    );
    let index = ctx.accounts.cohort.index;
    if index > 0 {
        let previous = ctx
            .accounts
            .previous_cohort
            .as_ref()
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        require!(
            previous.league_id == ctx.accounts.cohort.league_id && previous.index == index - 1,
            HoldoutError::AccountMismatch
        );
        require!(previous.settled, HoldoutError::PreviousCohortUnsettled);
    }

    let cohort_key = ctx.accounts.cohort.key();
    let league_id = ctx.accounts.cohort.league_id;
    let index_bytes = ctx.accounts.cohort.index.to_le_bytes();
    let cohort_bump = [ctx.accounts.cohort.bump];
    let cohort_seeds: &[&[u8]] = &[COHORT_SEED, &[league_id], &index_bytes, &cohort_bump];
    let league_bump = [ctx.accounts.league.bump];
    let league_seeds: &[&[u8]] = &[LEAGUE_SEED, &[league_id], &league_bump];

    let token_program = ctx.accounts.token_program.to_account_info();
    let vault = ctx.accounts.vault.to_account_info();
    let reserve_vault = ctx.accounts.reserve_vault.to_account_info();
    let cohort_info = ctx.accounts.cohort.to_account_info();
    let league_info = ctx.accounts.league.to_account_info();

    let total_finisher_stake = ctx.accounts.cohort.total_finisher_stake;
    let mut boost = 0u64;
    let mut comeback_funded = 0u64;
    let mut shares = [0u64; 4];
    let mut remainder = 0u64;

    if total_finisher_stake > 0 {
        // (1) The boost, drawn before the shares so they are computed on it.
        boost = settlement::boost_for(ctx.accounts.cohort.pool, ctx.accounts.league.boost_reserve);
        transfer_signed(
            &token_program,
            reserve_vault.clone(),
            vault.clone(),
            league_info.clone(),
            league_seeds,
            boost,
        )?;
        ctx.accounts.league.boost_reserve -= boost;
        let pool = ctx
            .accounts
            .cohort
            .pool
            .checked_add(boost)
            .ok_or(error!(HoldoutError::AccountMismatch))?;

        // (2) Per-tier shares on the boosted pool; the floors' remainder to operations.
        let chips = ctx.accounts.cohort.chips;
        for tier in 0..4 {
            shares[tier] = settlement::share_for_tier(pool, chips[tier], total_finisher_stake);
        }
        remainder =
            settlement::rounding_remainder(pool, ctx.accounts.cohort.finishers_per_tier, shares);
        transfer_signed(
            &token_program,
            vault.clone(),
            ctx.accounts.fee_destination.to_account_info(),
            cohort_info.clone(),
            cohort_seeds,
            remainder,
        )?;

        // (3) The comeback refunds this cohort's finishers are owed, as far as
        // the reserve reaches; each is paid, scaled, with its claim (FR-037e).
        comeback_funded = ctx
            .accounts
            .cohort
            .comeback_owed
            .min(ctx.accounts.league.comeback_reserve);
        transfer_signed(
            &token_program,
            reserve_vault.clone(),
            vault.clone(),
            league_info.clone(),
            league_seeds,
            comeback_funded,
        )?;
        ctx.accounts.league.comeback_reserve -= comeback_funded;

        let cohort = &mut ctx.accounts.cohort;
        cohort.boost_received = boost;
        cohort.share_per_tier = shares;
        cohort.rounding_remainder = remainder;
        cohort.comeback_funded = comeback_funded;
        cohort.pool = pool
            .checked_sub(remainder)
            .ok_or(error!(HoldoutError::AccountMismatch))?;
    } else {
        // Nobody finished: the whole pool goes out and nothing carries over.
        let pool = ctx.accounts.cohort.pool;
        let (developer, operations) = settlement::no_finisher_split(pool);
        transfer_signed(
            &token_program,
            vault.clone(),
            ctx.accounts.developer_destination.to_account_info(),
            cohort_info.clone(),
            cohort_seeds,
            developer,
        )?;
        transfer_signed(
            &token_program,
            vault.clone(),
            ctx.accounts.fee_destination.to_account_info(),
            cohort_info.clone(),
            cohort_seeds,
            operations,
        )?;
        let cohort = &mut ctx.accounts.cohort;
        cohort.no_finisher_fee = pool;
        cohort.pool = 0;
    }

    // (4) This cohort's own reserve shares go to its league — after it drew,
    // so they fund later cohorts only (FR-037c).
    let boost_due = ctx.accounts.cohort.boost_due;
    let comeback_due = ctx.accounts.cohort.comeback_due;
    let credit = boost_due
        .checked_add(comeback_due)
        .ok_or(error!(HoldoutError::AccountMismatch))?;
    transfer_signed(
        &token_program,
        vault,
        reserve_vault,
        cohort_info,
        cohort_seeds,
        credit,
    )?;
    let league = &mut ctx.accounts.league;
    league.boost_reserve = league
        .boost_reserve
        .checked_add(boost_due)
        .ok_or(error!(HoldoutError::AccountMismatch))?;
    league.comeback_reserve = league
        .comeback_reserve
        .checked_add(comeback_due)
        .ok_or(error!(HoldoutError::AccountMismatch))?;

    let cohort = &mut ctx.accounts.cohort;
    cohort.boost_due = 0;
    cohort.comeback_due = 0;
    cohort.settled = true;

    emit!(CohortSettled {
        cohort: cohort_key,
        finishers: cohort.finisher_count,
        total_finisher_stake,
        share_per_tier: shares,
        rounding_remainder: remainder,
        boost,
        comeback_funded,
    });

    Ok(())
}
