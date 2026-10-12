//! `release_comeback` (contracts/program-instructions §2). FR-037e.
//!
//! Permissionless, like the rest of settlement: a comeback pledge whose round
//! can no longer be finished leaves the league's comeback reserve and joins its
//! boost reserve, for later cohorts' winners. Both live in the one reserve
//! vault, so no token moves; only the two figures do.
//!
//! Counting already releases a pledge whenever its member is counted again in
//! the league. This is for the member who never comes back. A pledge is
//! released only once its **last** round cannot make it good any more (the
//! earlier one, if there are two, is settled before the last can be):
//!
//!  - **the round was opened** (`league.next_index > round`): it must be passed
//!    and settled. Had the member finished it, counting would have made the
//!    pledge owed and closed the comeback; had they forfeited it, counting
//!    would have replaced the comeback with a new one for a later round.
//!  - **the round was never opened**: its first day, by the league's schedule,
//!    has passed — later than its joining closes, and later than any comeback
//!    member may join it — so if it opens now nobody can ever be in it.
//!
//! What a later boost draws may therefore depend on when this ran; a pledge
//! never does (INV-10).

use anchor_lang::prelude::*;

use crate::{
    errors::HoldoutError,
    events::ComebackReleased,
    instructions::settle_count::release_pledge,
    math::schedule,
    state::{
        cohort::Cohort,
        league::{LeagueConfig, LEAGUE_SEED},
        profile::{Profile, PROFILE_SEED},
    },
};

#[derive(Accounts)]
pub struct ReleaseComeback<'info> {
    /// Anyone. The caller's identity never changes the outcome (FR-030a).
    pub caller: Signer<'info>,

    #[account(
        mut,
        seeds = [LEAGUE_SEED, &[league.league_id]],
        bump = league.bump,
    )]
    pub league: Box<Account<'info, LeagueConfig>>,

    #[account(
        mut,
        seeds = [PROFILE_SEED, profile.wallet.as_ref()],
        bump = profile.bump,
    )]
    pub profile: Box<Account<'info, Profile>>,

    /// The comeback's last round — cohort `comeback_last_round` of this
    /// league — required once it has been opened.
    pub round: Option<Box<Account<'info, Cohort>>>,
}

pub(crate) fn handler(ctx: Context<ReleaseComeback>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let league_id = ctx.accounts.league.league_id;
    let progress = ctx.accounts.profile.progress(league_id);
    let pledge = progress
        .open_pledge_in(league_id)
        .ok_or(error!(HoldoutError::NoComebackToRelease))?;
    let round = progress.comeback_last_round;

    let league = &ctx.accounts.league;
    if league.next_index > round {
        let cohort = ctx
            .accounts
            .round
            .as_ref()
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        require!(
            cohort.league_id == league_id && cohort.index == round,
            HoldoutError::AccountMismatch
        );
        require!(cohort.settled, HoldoutError::ComebackStillOpen);
    } else {
        let start = schedule::cohort_start(league.first_start, league.cadence, round);
        let first_deadline = schedule::deadline_of(start, league.day_length, 0);
        require!(now > first_deadline, HoldoutError::ComebackStillOpen);
    }

    release_pledge(&mut ctx.accounts.league, pledge)?;
    let profile = &mut ctx.accounts.profile;
    profile.progress_mut(league_id).clear_comeback();

    emit!(ComebackReleased {
        wallet: profile.wallet,
        league: league_id,
        round,
        amount: pledge,
    });

    Ok(())
}
