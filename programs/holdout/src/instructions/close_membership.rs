//! `close_membership` (contracts/program-instructions §3). Task T379.
//!
//! Permissionless, like the rest of settlement (FR-030a): the crank calls it.
//! A membership whose round is paid out is closed and its rent goes back to the
//! member who paid it (FR-035b). The caller chooses nothing: the recipient is
//! the membership's own wallet.
//!
//! Only once its round may close (FR-035c) — nothing left on the cohort's
//! books, so every finisher has already claimed and claims still never expire
//! (FR-035) — and only a membership that needs nothing more: counted, and
//! `Claimed` or `Forfeited`. It takes one off the cohort's `member_count`,
//! which `close_cohort` wants at zero, so every membership closes while its
//! cohort is still there to fill `MembershipClosed` — the summary the app
//! rebuilds a closed round's history from (FR-035d).

use anchor_lang::prelude::*;

use crate::{
    errors::HoldoutError,
    events::MembershipClosed,
    math::settlement,
    state::{
        cohort::{Cohort, CLOSE_GRACE, TEST_CLOSE_GRACE},
        league::{LeagueConfig, LEAGUE_SEED},
        membership::{Membership, MembershipState},
    },
};

#[derive(Accounts)]
pub struct CloseMembership<'info> {
    #[account(
        seeds = [LEAGUE_SEED, &[cohort.league_id]],
        bump = league.bump,
    )]
    pub league: Box<Account<'info, LeagueConfig>>,

    #[account(mut)]
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
        close = wallet,
        constraint = membership.cohort == cohort.key() @ HoldoutError::AccountMismatch,
    )]
    pub membership: Box<Account<'info, Membership>>,

    /// Receives the membership's rent: always its own wallet.
    /// CHECK: only lamports are credited; the address is the membership's own.
    #[account(mut, address = membership.wallet @ HoldoutError::AccountMismatch)]
    pub wallet: UncheckedAccount<'info>,
}

pub(crate) fn handler(ctx: Context<CloseMembership>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let cohort = &ctx.accounts.cohort;
    let grace = if LeagueConfig::is_test_league(cohort.league_id) {
        TEST_CLOSE_GRACE
    } else {
        CLOSE_GRACE
    };
    require!(
        cohort.closable(
            ctx.accounts.league.cadence,
            grace,
            ctx.accounts.next_cohort.settled,
            now
        ),
        HoldoutError::RoundNotClosable
    );

    let membership = &ctx.accounts.membership;
    let finished = match membership.state {
        MembershipState::Claimed => true,
        MembershipState::Forfeited => false,
        _ => return Err(HoldoutError::RoundNotClosable.into()),
    };
    require!(membership.counted, HoldoutError::RoundNotClosable);

    let (paid, comeback) = if finished {
        let comeback = settlement::comeback_refund(
            membership.comeback_entitled,
            cohort.comeback_funded,
            cohort.comeback_owed,
        );
        let paid = membership
            .stake
            .checked_add(cohort.share_per_tier[membership.tier as usize])
            .and_then(|a| a.checked_add(comeback))
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        (paid, comeback)
    } else {
        (0, 0)
    };

    emit!(MembershipClosed {
        cohort: cohort.key(),
        wallet: membership.wallet,
        league: cohort.league_id,
        index: cohort.index,
        start: cohort.start,
        end: cohort.end(),
        tier: membership.tier,
        stake: membership.stake,
        days_completed: membership.days_completed,
        finished,
        paid,
        comeback,
    });

    let cohort = &mut ctx.accounts.cohort;
    cohort.member_count = cohort
        .member_count
        .checked_sub(1)
        .ok_or(error!(HoldoutError::AccountMismatch))?;
    Ok(())
}
