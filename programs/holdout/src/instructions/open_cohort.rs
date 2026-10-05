//! `open_cohort` (contracts/program-instructions §2). Task T055.
//!
//! Permissionless: anyone may pay the rent and open a league's next cohort, and
//! the caller's identity changes nothing about the result (FR-030a). That is
//! what makes the settlement refusal in `settle_finalize` always answerable —
//! a league with no lobby open is one permissionless call away from having one.
//!
//! Three guards, and each one closes a hole that cannot be corrected later:
//!
//!  - **A chip set must exist.** A cohort opened before the first
//!    `publish_chip_set` would copy four zeroed amounts and keep them for its
//!    whole life, accepting zero-stake joins no later publish could fix
//!    (`ChipSetNotPublished`, 6012; FR-007, FR-055b).
//!  - **`index == next_index`.** Cohorts are a dense sequence per league, which
//!    is what lets `settle_count` insist on cohort order (FR-061a).
//!  - **The previous cohort has started.** One lobby per league at a time, so a
//!    member's obligations cannot overlap by construction.
//!
//! Everything the cohort will ever need is copied here and never written again
//! (constitution V): the league's schedule and reps, and today's chip amounts.

use anchor_lang::prelude::*;
use anchor_spl::token::{Mint, Token, TokenAccount};

use crate::{
    errors::HoldoutError,
    events::CohortOpened,
    math::schedule,
    state::{
        cohort::{Cohort, COHORT_SEED, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        league::{LeagueConfig, LEAGUE_SEED},
    },
};

#[derive(Accounts)]
#[instruction(league_id: u8, index: u32)]
pub struct OpenCohort<'info> {
    /// Anyone. Pays the cohort's and the vault's rent, and gets nothing for it.
    #[account(mut)]
    pub payer: Signer<'info>,

    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Config>,

    #[account(
        mut,
        seeds = [LEAGUE_SEED, &[league_id]],
        bump = league.bump,
    )]
    pub league: Account<'info, LeagueConfig>,

    #[account(
        init,
        payer = payer,
        space = 8 + Cohort::INIT_SPACE,
        seeds = [COHORT_SEED, &[league_id], &index.to_le_bytes()],
        bump,
    )]
    pub cohort: Account<'info, Cohort>,

    /// The cohort's own SKR vault: stakes and the pool, held
    /// under the cohort's authority so no key outside the program can move them
    /// (constitution I).
    #[account(
        init,
        payer = payer,
        seeds = [VAULT_SEED, cohort.key().as_ref()],
        bump,
        token::mint = skr_mint,
        token::authority = cohort,
    )]
    pub vault: Account<'info, TokenAccount>,

    /// The league's cohort `index - 1`, required for every index but 0. Anchor's
    /// convention for "none" is the program's own ID in the slot, which is what
    /// index 0 sends.
    pub previous_cohort: Option<Account<'info, Cohort>>,

    #[account(constraint = skr_mint.key() == config.skr_mint @ HoldoutError::AccountMismatch)]
    pub skr_mint: Account<'info, Mint>,

    pub system_program: Program<'info, System>,
    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler(ctx: Context<OpenCohort>, league_id: u8, index: u32) -> Result<()> {
    let config = &ctx.accounts.config;
    // Before the first publish there are no amounts to copy, only zeroes (FR-055b).
    require!(
        config.chip_set.is_published(),
        HoldoutError::ChipSetNotPublished
    );

    let league = &mut ctx.accounts.league;
    require!(index == league.next_index, HoldoutError::CohortNotDue);

    let now = Clock::get()?.unix_timestamp;
    match (index, &ctx.accounts.previous_cohort) {
        // The league's first cohort has no predecessor to wait for.
        (0, _) => {}
        (_, Some(previous)) => {
            require!(
                previous.league_id == league_id && previous.index == index - 1,
                HoldoutError::AccountMismatch
            );
            // One lobby per league at a time: the next cohort opens only once
            // the current one has started and its roster is closed.
            require!(now >= previous.start, HoldoutError::CohortNotDue);
        }
        // Index > 0 with nothing in the slot: there is no way to check the rule.
        (_, None) => return Err(HoldoutError::AccountMismatch.into()),
    }

    let start = schedule::cohort_start(league.first_start, league.cadence, index);

    let cohort = &mut ctx.accounts.cohort;
    cohort.league_id = league_id;
    cohort.index = index;
    cohort.start = start;
    cohort.join_cutoff = schedule::join_cutoff(start, league.join_lock);
    cohort.duration_days = league.duration_days;
    cohort.reps_per_day = league.reps_per_day;
    cohort.day_length = league.day_length;
    cohort.exercise = league.exercise;
    // Today's amounts, kept for this cohort's whole life (FR-007, FR-055).
    cohort.chips = config.chip_set.amounts;
    cohort.vault_bump = ctx.bumps.vault;
    cohort.bump = ctx.bumps.cohort;

    league.next_index += 1;

    emit!(CohortOpened {
        league: league_id,
        index,
        start,
        chips: cohort.chips,
    });

    Ok(())
}
