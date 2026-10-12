//! `release_comeback` (contracts/program-instructions §2). FR-037e.
//!
//! Permissionless, like the rest of settlement: a comeback pledge whose round
//! can no longer be finished leaves the league's comeback reserve and joins its
//! boost reserve, for later cohorts' winners. Both live in the one reserve
//! vault, so no token moves; only the two figures do.
//!
//! Counting already releases a pledge whenever its member is counted again in
//! the league. This is for the member who never comes back. A pledge is
//! released only once **none** of its rounds can make it good any more:
//!
//!  - **every round that was opened is settled.** The latest opened one —
//!    cohort `min(last, next_index − 1)` — must be passed and settled; cohorts
//!    settle in order, so the earlier rounds are settled too. Had the member
//!    finished one, counting would have made the pledge owed and closed the
//!    comeback; had they forfeited one, counting would have replaced the
//!    comeback with a new one for a later round.
//!  - **a round never opened can no longer take anyone**: the first day of the
//!    last round, by the league's schedule, has passed — later than its
//!    joining closes, and later than any comeback member may join it — so if
//!    it opens now nobody can ever be in it.
//!
//! Both hold when only some of the rounds were opened. Checking the time alone
//! would let a pledge go while its member's finish of an earlier round still
//! waits to be counted.
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

    /// The comeback's latest opened round — cohort
    /// `min(comeback_last_round, league.next_index − 1)` of this league —
    /// required once its first round has been opened.
    pub round: Option<Box<Account<'info, Cohort>>>,
}

pub(crate) fn handler(ctx: Context<ReleaseComeback>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let league_id = ctx.accounts.league.league_id;
    let progress = ctx.accounts.profile.progress(league_id);
    let pledge = progress
        .open_pledge_in(league_id)
        .ok_or(error!(HoldoutError::NoComebackToRelease))?;
    let first = progress.comeback_round;
    let round = progress.comeback_last_round;

    let league = &ctx.accounts.league;
    if league.next_index > first {
        // The latest of its rounds the league has opened, settled — and with
        // it every earlier one, since cohorts settle in order.
        let latest = round.min(league.next_index - 1);
        let cohort = ctx
            .accounts
            .round
            .as_ref()
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        require!(
            cohort.league_id == league_id && cohort.index == latest,
            HoldoutError::AccountMismatch
        );
        require!(cohort.settled, HoldoutError::ComebackStillOpen);
    }
    if league.next_index <= round {
        // A round not opened yet: nobody can be in it once its first day has passed.
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
