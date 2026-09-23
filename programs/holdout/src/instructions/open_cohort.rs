//! `open_cohort(league_id, index)` (contracts/program-instructions §2).
//! Task T055.
//!
//! Permissionless: the caller only pays the rent, and chain state alone decides
//! whether the cohort is due (FR-030a). A cohort copies every league parameter
//! and the current chip set at creation and never reads them again, so a later
//! publish or league change cannot move its rules (constitution V, FR-055).

use anchor_lang::prelude::*;
use anchor_spl::token::{Mint, Token, TokenAccount};

use crate::errors::HoldoutError;
use crate::events::CohortOpened;
use crate::math::schedule;
use crate::state::cohort::{Cohort, COHORT_SEED, VAULT_SEED};
use crate::state::config::{Config, CONFIG_SEED};
use crate::state::league::{LeagueConfig, LEAGUE_SEED};

#[derive(Accounts)]
#[instruction(league_id: u8, index: u32)]
pub struct OpenCohort<'info> {
    #[account(mut)]
    pub payer: Signer<'info>,

    // Without a published set the cohort would copy four zeroed amounts and
    // keep them for life (FR-055b). Anchor checks these after creating
    // `cohort` and `vault`, and a refusal rolls both back. One consequence: an
    // index already opened fails in the System Program ("already in use")
    // before the index check can answer 6020 — the cohort exists either way.
    #[account(
        seeds = [CONFIG_SEED],
        bump = config.bump,
        constraint = config.chip_set.is_published() @ HoldoutError::ChipSetNotPublished,
    )]
    pub config: Box<Account<'info, Config>>,

    #[account(
        mut,
        seeds = [LEAGUE_SEED, &[league_id]],
        bump = league.bump,
        constraint = index == league.next_index @ HoldoutError::CohortNotDue,
    )]
    pub league: Box<Account<'info, LeagueConfig>>,

    #[account(
        init,
        payer = payer,
        space = 8 + Cohort::INIT_SPACE,
        seeds = [COHORT_SEED, &[league_id], &index.to_le_bytes()],
        bump,
    )]
    pub cohort: Box<Account<'info, Cohort>>,

    /// The cohort's own SKR account; the cohort PDA is its authority, so only
    /// this program's instructions can move what it holds (constitution I).
    #[account(
        init,
        payer = payer,
        seeds = [VAULT_SEED, cohort.key().as_ref()],
        bump,
        token::mint = skr_mint,
        token::authority = cohort,
        token::token_program = token_program,
    )]
    pub vault: Box<Account<'info, TokenAccount>>,

    /// Cohort `index − 1` of this league; absent (the program's ID in its slot)
    /// for index 0.
    pub previous_cohort: Option<Box<Account<'info, Cohort>>>,

    #[account(address = config.skr_mint @ HoldoutError::AccountMismatch)]
    pub skr_mint: Box<Account<'info, Mint>>,

    pub system_program: Program<'info, System>,
    pub token_program: Program<'info, Token>,
}

/// Whether cohort `index` of `league_id` may open now, given the previous
/// cohort's `(league_id, index, start)` when one was passed. Every cohort
/// account lives at the PDA its league and index derive, so matching those two
/// fields identifies the previous cohort exactly.
pub fn check_previous(
    league_id: u8,
    index: u32,
    previous: Option<(u8, u32, i64)>,
    now: i64,
) -> Result<()> {
    match (index, previous) {
        (0, None) => Ok(()),
        (0, Some(_)) | (_, None) => err!(HoldoutError::AccountMismatch),
        (i, Some((prev_league, prev_index, prev_start))) => {
            require!(
                prev_league == league_id && prev_index == i - 1,
                HoldoutError::AccountMismatch
            );
            // One lobby at a time: the next cohort opens once this one starts
            // (R11, FR-007a).
            require!(now >= prev_start, HoldoutError::CohortNotDue);
            Ok(())
        }
    }
}

pub fn handle_open_cohort(ctx: Context<OpenCohort>, league_id: u8, index: u32) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    check_previous(
        league_id,
        index,
        ctx.accounts
            .previous_cohort
            .as_ref()
            .map(|p| (p.league_id, p.index, p.start)),
        now,
    )?;

    let league = &mut ctx.accounts.league;
    let chips = ctx.accounts.config.chip_set.amounts;
    let start = schedule::cohort_start(league.first_start, league.cadence, index);

    let cohort = &mut ctx.accounts.cohort;
    cohort.set_inner(Cohort {
        league_id,
        index,
        start,
        join_cutoff: schedule::join_cutoff(start, league.join_lock),
        duration_days: league.duration_days,
        reps_per_day: league.reps_per_day,
        day_length: league.day_length,
        exercise: league.exercise,
        chips,
        member_count: 0,
        held_stake: 0,
        pool: 0,
        carry_over_received: 0,
        forfeits_recorded: 0,
        counted: 0,
        finisher_count: 0,
        finishers_per_tier: [0; 4],
        total_finisher_stake: 0,
        settled: false,
        share_per_tier: [0; 4],
        rounding_remainder: 0,
        no_finisher_fee: 0,
        carry_over_sent: 0,
        carry_over_held: 0,
        carry_over_target: Pubkey::default(),
        vault_bump: ctx.bumps.vault,
        bump: ctx.bumps.cohort,
    });

    league.next_index = index.checked_add(1).ok_or(HoldoutError::CohortNotDue)?;

    emit!(CohortOpened {
        league: league_id,
        index,
        start,
        chips,
    });
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    const START: i64 = 1_790_571_600;

    fn is(result: Result<()>, expected: HoldoutError) -> bool {
        result == Err(expected.into())
    }

    #[test]
    fn the_first_cohort_needs_no_previous_one() {
        assert!(check_previous(0, 0, None, 0).is_ok());
        assert!(is(
            check_previous(0, 0, Some((0, 0, START)), START),
            HoldoutError::AccountMismatch
        ));
    }

    #[test]
    fn a_later_cohort_waits_for_the_previous_one_to_start() {
        assert!(is(
            check_previous(0, 1, Some((0, 0, START)), START - 1),
            HoldoutError::CohortNotDue
        ));
        assert!(check_previous(0, 1, Some((0, 0, START)), START).is_ok());
        assert!(check_previous(0, 1, Some((0, 0, START)), START + 1).is_ok());
    }

    #[test]
    fn the_previous_cohort_must_be_this_leagues_last_one() {
        assert!(is(
            check_previous(0, 1, None, START),
            HoldoutError::AccountMismatch
        ));
        assert!(is(
            check_previous(0, 2, Some((0, 0, START)), START),
            HoldoutError::AccountMismatch
        ));
        assert!(is(
            check_previous(0, 1, Some((1, 0, START)), START),
            HoldoutError::AccountMismatch
        ));
    }
}
