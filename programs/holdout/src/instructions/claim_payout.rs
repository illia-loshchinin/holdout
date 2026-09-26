//! `claim_payout` (contracts/program-instructions §3). Task T080.
//!
//! A deliberate member action, claimable indefinitely (FR-034, FR-035). No
//! automation pushes money at anyone, and nothing expires: a finisher who comes
//! back in a month is paid the same figure as one who claimed the minute the
//! cohort settled, because the figure was written once at finalisation and is
//! only read here.
//!
//! `stake + share_per_tier[tier]`, both out of the cohort's own vault.

use anchor_lang::prelude::*;
use anchor_spl::token::{self, Token, TokenAccount, Transfer};

use crate::{
    errors::HoldoutError,
    events::PayoutClaimed,
    state::{
        cohort::{Cohort, COHORT_SEED, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        membership::{Membership, MembershipState, MEMBERSHIP_SEED},
    },
};

/// The large accounts are boxed for the SBF stack.
#[derive(Accounts)]
pub struct ClaimPayout<'info> {
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

    /// The seeds carry the member's own key, so a signer can only ever claim
    /// their own payout.
    #[account(
        mut,
        seeds = [MEMBERSHIP_SEED, cohort.key().as_ref(), member.key().as_ref()],
        bump = membership.bump,
    )]
    pub membership: Box<Account<'info, Membership>>,

    #[account(
        mut,
        associated_token::mint = config.skr_mint,
        associated_token::authority = member,
    )]
    pub member_token: Box<Account<'info, TokenAccount>>,

    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler(ctx: Context<ClaimPayout>) -> Result<()> {
    // Before finalisation there is no share to read, so there is nothing to pay
    // — not a smaller payout, but no claim at all.
    require!(ctx.accounts.cohort.settled, HoldoutError::NotClaimable);
    require!(
        ctx.accounts.membership.state != MembershipState::Claimed,
        HoldoutError::AlreadyClaimed
    );
    require!(
        ctx.accounts.membership.state == MembershipState::Finished,
        HoldoutError::NotClaimable
    );

    let stake = ctx.accounts.membership.stake;
    let tier = ctx.accounts.membership.tier as usize;
    let share = ctx.accounts.cohort.share_per_tier[tier];
    let amount = stake
        .checked_add(share)
        .ok_or(error!(HoldoutError::AccountMismatch))?;

    let cohort_key = ctx.accounts.cohort.key();
    let league_id = ctx.accounts.cohort.league_id;
    let index_bytes = ctx.accounts.cohort.index.to_le_bytes();
    let bump = [ctx.accounts.cohort.bump];
    let seeds: &[&[u8]] = &[COHORT_SEED, &[league_id], &index_bytes, &bump];

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
        amount,
    )?;

    let cohort = &mut ctx.accounts.cohort;
    cohort.held_stake = cohort
        .held_stake
        .checked_sub(stake)
        .ok_or(error!(HoldoutError::AccountMismatch))?;
    cohort.pool = cohort
        .pool
        .checked_sub(share)
        .ok_or(error!(HoldoutError::AccountMismatch))?;

    ctx.accounts.membership.state = MembershipState::Claimed;

    emit!(PayoutClaimed {
        cohort: cohort_key,
        wallet: ctx.accounts.member.key(),
        amount,
    });

    Ok(())
}
