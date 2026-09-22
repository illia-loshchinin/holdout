//! `submit_day` (contracts/program-instructions §3). Task T064.
//!
//! One completed day, claimed by the member themselves. No money moves and no
//! caller but the member can move this number, so the only thing at stake is
//! the member's own obligation.
//!
//! **The proof is emitted and not stored** (research R16, FR-025). A 32-byte
//! hash per member per day would be 21 of them in a 21-day cohort — rent for
//! something no rule ever reads again — so `DayCompleted` carries it into the
//! log and the account keeps only the count. That the proof is client-signed at
//! all is a documented weakness, not a hidden one (constitution XI, scope doc
//! D-E): the real fix is a server-side attestation signer.
//!
//! Which day may be claimed is decided by chain time alone (constitution II):
//! the member's next day, and today, and today's deadline not yet passed. A
//! member in breach therefore cannot catch up, and no automation had to run to
//! make that true (INV-8).

use anchor_lang::prelude::*;

use crate::{
    errors::HoldoutError,
    events::DayCompleted,
    guards,
    state::{
        cohort::Cohort,
        membership::{Membership, MEMBERSHIP_SEED},
        profile::{Profile, PROFILE_SEED},
    },
};

/// The large accounts are boxed: `Cohort` and `Profile` do not fit in
/// `try_accounts`' own SBF stack frame beside everything else.
#[derive(Accounts)]
pub struct SubmitDay<'info> {
    /// Pays the fee, and nothing else leaves their wallet.
    pub member: Signer<'info>,

    pub cohort: Box<Account<'info, Cohort>>,

    /// The seeds carry the member's own key, so a signer can only ever move
    /// their own count.
    #[account(
        mut,
        seeds = [MEMBERSHIP_SEED, cohort.key().as_ref(), member.key().as_ref()],
        bump = membership.bump,
    )]
    pub membership: Box<Account<'info, Membership>>,

    #[account(
        mut,
        seeds = [PROFILE_SEED, member.key().as_ref()],
        bump = profile.bump,
    )]
    pub profile: Box<Account<'info, Profile>>,
}

pub(crate) fn handler(ctx: Context<SubmitDay>, day_index: u8, proof_hash: [u8; 32]) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;

    guards::require_running(&ctx.accounts.cohort, now)?;
    guards::require_submittable(
        &ctx.accounts.membership,
        &ctx.accounts.cohort,
        day_index,
        now,
    )?;

    let membership = &mut ctx.accounts.membership;
    membership.days_completed = membership
        .days_completed
        .checked_add(1)
        .ok_or(error!(HoldoutError::WrongDay))?;

    // The cohort has started, so a lobby place in it is a running obligation.
    ctx.accounts.profile.normalise_slots(now);
    if ctx.accounts.profile.running.cohort == ctx.accounts.cohort.key() {
        // The end normalisation could not know, from the account in hand.
        ctx.accounts.profile.running.end = ctx.accounts.cohort.end();
    }

    emit!(DayCompleted {
        cohort: ctx.accounts.cohort.key(),
        wallet: ctx.accounts.member.key(),
        day_index,
        proof_hash,
    });

    Ok(())
}
