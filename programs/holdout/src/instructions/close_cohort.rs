//! `close_cohort` (contracts/program-instructions §3). Task T379.
//!
//! Permissionless, like the rest of settlement (FR-030a): the crank calls it.
//! A paid-out round's SKR vault and then the cohort itself are closed, and
//! both rents go to `Config.operator`, the crank's payer that opened them
//! (FR-035b). The caller chooses nothing.
//!
//! Only once the round may close (FR-035c) and every membership of it has
//! been closed (`member_count == 0`, `close_membership`). The books are at
//! zero by then, so whatever the vault still holds reached it from outside
//! them — a stray transfer — and goes to the operations destination first:
//! nobody can keep a round open by sending it dust.

use anchor_lang::prelude::*;
use anchor_spl::token::{self, CloseAccount, Token, TokenAccount};

use crate::{
    errors::HoldoutError,
    events::CohortClosed,
    state::{
        cohort::{Cohort, CLOSE_GRACE, COHORT_SEED, TEST_CLOSE_GRACE, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        league::{LeagueConfig, LEAGUE_SEED},
    },
    vault::transfer_signed,
};

/// The large accounts are boxed for the SBF stack.
#[derive(Accounts)]
pub struct CloseCohort<'info> {
    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,

    #[account(
        seeds = [LEAGUE_SEED, &[cohort.league_id]],
        bump = league.bump,
    )]
    pub league: Box<Account<'info, LeagueConfig>>,

    #[account(mut, close = operator)]
    pub cohort: Box<Account<'info, Cohort>>,

    /// The league's cohort `index + 1`: settled, so nothing of the league's
    /// settlement needs this one again (FR-035c).
    #[account(
        constraint = next_cohort.league_id == cohort.league_id
            && next_cohort.index == cohort.index + 1 @ HoldoutError::AccountMismatch,
    )]
    pub next_cohort: Box<Account<'info, Cohort>>,

    #[account(
        mut,
        seeds = [VAULT_SEED, cohort.key().as_ref()],
        bump = cohort.vault_bump,
        constraint = vault.mint == config.skr_mint @ HoldoutError::AccountMismatch,
    )]
    pub vault: Box<Account<'info, TokenAccount>>,

    /// Operations: SKR that reached the vault from outside its books.
    #[account(
        mut,
        constraint = fee_destination.key() == config.fee_destination @ HoldoutError::AccountMismatch,
    )]
    pub fee_destination: Box<Account<'info, TokenAccount>>,

    /// Receives both rents.
    /// CHECK: only lamports are credited; the address is `Config.operator`.
    #[account(mut, address = config.operator @ HoldoutError::AccountMismatch)]
    pub operator: UncheckedAccount<'info>,

    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler(ctx: Context<CloseCohort>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let cohort = &ctx.accounts.cohort;
    let grace = if LeagueConfig::is_test_league(cohort.league_id) {
        TEST_CLOSE_GRACE
    } else {
        CLOSE_GRACE
    };
    require!(
        cohort.member_count == 0
            && cohort.closable(
                ctx.accounts.league.cadence,
                grace,
                ctx.accounts.next_cohort.settled,
                now
            ),
        HoldoutError::RoundNotClosable
    );

    let cohort_key = cohort.key();
    let league_id = cohort.league_id;
    let index = cohort.index;
    let index_bytes = index.to_le_bytes();
    let bump = [cohort.bump];
    let seeds: &[&[u8]] = &[COHORT_SEED, &[league_id], &index_bytes, &bump];

    let swept = ctx.accounts.vault.amount;
    transfer_signed(
        &ctx.accounts.token_program.to_account_info(),
        ctx.accounts.vault.to_account_info(),
        ctx.accounts.fee_destination.to_account_info(),
        ctx.accounts.cohort.to_account_info(),
        seeds,
        swept,
    )?;
    token::close_account(CpiContext::new_with_signer(
        ctx.accounts.token_program.key(),
        CloseAccount {
            account: ctx.accounts.vault.to_account_info(),
            destination: ctx.accounts.operator.to_account_info(),
            authority: ctx.accounts.cohort.to_account_info(),
        },
        &[seeds],
    ))?;

    emit!(CohortClosed {
        cohort: cohort_key,
        league: league_id,
        index,
        swept,
    });
    Ok(())
}
