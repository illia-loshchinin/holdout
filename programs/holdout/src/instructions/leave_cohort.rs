//! `leave_cohort` (contracts/program-instructions §3). Task T057.
//!
//! Unconditional and lossless while joining is open: every unit of the stake
//! back, no fee and no deduction of any kind (FR-009). Nothing has been at risk
//! yet — the obligation has not begun — so there is nothing to keep.
//!
//! `Withdrawn` is not a state: the `Membership` is **closed** and its rent goes
//! back to the member (data-model §1.4), which is also why a second leave has
//! no account to act on. The lobby place is cleared, so the member is free to
//! stake elsewhere immediately.
//!
//! From the cutoff onwards this refuses with `JoiningClosed` (6021) and the
//! roster the cohort runs with is final — decided by chain time, with no
//! automation involved (ES-12a, constitution II).

use anchor_lang::prelude::*;
use anchor_spl::token::{self, Token, TokenAccount, Transfer};

use crate::{
    errors::HoldoutError,
    events::MemberLeft,
    guards,
    state::{
        cohort::{Cohort, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        membership::{Membership, MembershipState, MEMBERSHIP_SEED},
        profile::{Profile, PROFILE_SEED},
    },
};

/// The large accounts are boxed. `Cohort` and `Profile` are big enough that
/// deserializing them into `try_accounts`' own frame blows the SBF 4 KiB stack
/// limit — the build says so, and the runtime answers with an access violation
/// rather than an error code. `Box` moves them to the heap.
#[derive(Accounts)]
pub struct LeaveCohort<'info> {
    /// Receives the refund and the membership's rent.
    #[account(mut)]
    pub member: Signer<'info>,

    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,

    #[account(mut)]
    pub cohort: Box<Account<'info, Cohort>>,

    #[account(
        mut,
        seeds = [VAULT_SEED, cohort.key().as_ref()],
        bump = cohort.vault_bump,
        constraint = vault.mint == config.skr_mint @ HoldoutError::AccountMismatch,
    )]
    pub vault: Box<Account<'info, TokenAccount>>,

    /// Closed by this instruction. The seeds carry the member's own key, so a
    /// signer can only ever reach their own membership.
    #[account(
        mut,
        close = member,
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

    #[account(
        mut,
        associated_token::mint = config.skr_mint,
        associated_token::authority = member,
    )]
    pub member_token: Box<Account<'info, TokenAccount>>,

    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler(ctx: Context<LeaveCohort>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;

    guards::require_open(&ctx.accounts.cohort, now)?;
    require!(
        ctx.accounts.membership.state == MembershipState::Joined,
        HoldoutError::NotClaimable
    );

    let stake = ctx.accounts.membership.stake;
    let cohort_key = ctx.accounts.cohort.key();
    let wallet = ctx.accounts.member.key();

    // Vault → member, signed by the cohort PDA that holds it. The amount is the
    // membership's own recorded stake, so no rounding and no fee can creep in.
    let league_id = ctx.accounts.cohort.league_id;
    let index = ctx.accounts.cohort.index.to_le_bytes();
    let bump = [ctx.accounts.cohort.bump];
    let seeds: &[&[u8]] = &[
        crate::state::cohort::COHORT_SEED,
        &[league_id],
        &index,
        &bump,
    ];
    token::transfer(
        CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            Transfer {
                from: ctx.accounts.vault.to_account_info(),
                to: ctx.accounts.member_token.to_account_info(),
                authority: ctx.accounts.cohort.to_account_info(),
            },
            &[seeds],
        ),
        stake,
    )?;

    let cohort = &mut ctx.accounts.cohort;
    cohort.held_stake = cohort
        .held_stake
        .checked_sub(stake)
        .ok_or(error!(HoldoutError::AccountMismatch))?;
    cohort.member_count = cohort
        .member_count
        .checked_sub(1)
        .ok_or(error!(HoldoutError::AccountMismatch))?;

    let profile = &mut ctx.accounts.profile;
    profile.normalise_slots(now);
    if profile.pending.cohort == cohort_key {
        profile.pending.clear();
    }

    emit!(MemberLeft {
        cohort: cohort_key,
        wallet,
        stake,
    });

    Ok(())
}
