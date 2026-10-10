//! `join_cohort` (contracts/program-instructions §3). Task T056.
//!
//! A member picks a **tier**, never an amount. The stake is
//! `cohort.chips[tier]` — the four amounts this cohort copied when it opened —
//! so no later re-price can move it (FR-055, FR-058, constitution V) and there
//! is no path by which a stake that is not one of the cohort's four is ever
//! held (AC-13).
//!
//! The member pays for everything they create — their `Membership`, and their
//! `Profile` if this is their first — and the stake moves from their own SKR
//! account into the cohort's vault, whose authority is the cohort PDA. No key
//! outside the program can move it from there (constitution I).
//!
//! The lobby place it records is what keeps two daily obligations from ever
//! overlapping (FR-006a, FR-006b, INV-4). The member's running cohort must be
//! supplied whenever `profile.running` is set, because the overlap is judged on
//! that account's own end and never on the profile's cached copy — see
//! `guards::require_slot_free`.

use anchor_lang::prelude::*;
use anchor_spl::token::{self, Mint, Token, TokenAccount, Transfer};

use crate::{
    errors::HoldoutError,
    events::MemberJoined,
    guards,
    state::{
        cohort::{Cohort, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        membership::{Membership, MembershipState, MEMBERSHIP_SEED},
        profile::{PendingSlot, Profile, NEVER_CLAIMED, PROFILE_SEED},
    },
};

/// The large accounts are boxed. `Cohort` and `Profile` are big enough that
/// deserializing them into `try_accounts`' own frame blows the SBF 4 KiB stack
/// limit — the build says so, and the runtime answers with an access violation
/// rather than an error code. `Box` moves them to the heap.
#[derive(Accounts)]
pub struct JoinCohort<'info> {
    #[account(mut)]
    pub member: Signer<'info>,

    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,

    /// Verified by ownership and discriminator, not by seeds: only `open_cohort`
    /// ever creates one of these, and the vault and membership below are derived
    /// from **this** account's address, so a different cohort in the slot is
    /// simply a join into that cohort.
    #[account(mut)]
    pub cohort: Box<Account<'info, Cohort>>,

    #[account(
        mut,
        seeds = [VAULT_SEED, cohort.key().as_ref()],
        bump = cohort.vault_bump,
        constraint = vault.mint == config.skr_mint @ HoldoutError::AccountMismatch,
    )]
    pub vault: Box<Account<'info, TokenAccount>>,

    #[account(
        init,
        payer = member,
        space = 8 + Membership::INIT_SPACE,
        seeds = [MEMBERSHIP_SEED, cohort.key().as_ref(), member.key().as_ref()],
        bump,
    )]
    pub membership: Box<Account<'info, Membership>>,

    /// Created on a member's very first join, and reused ever after.
    #[account(
        init_if_needed,
        payer = member,
        space = 8 + Profile::INIT_SPACE,
        seeds = [PROFILE_SEED, member.key().as_ref()],
        bump,
    )]
    pub profile: Box<Account<'info, Profile>>,

    #[account(
        mut,
        associated_token::mint = skr_mint,
        associated_token::authority = member,
    )]
    pub member_token: Box<Account<'info, TokenAccount>>,

    /// The cohort in `profile.running`, required whenever that slot is set.
    /// Anchor's "none" is the program's own ID in the slot.
    pub running_cohort: Option<Box<Account<'info, Cohort>>>,

    #[account(constraint = skr_mint.key() == config.skr_mint @ HoldoutError::AccountMismatch)]
    pub skr_mint: Box<Account<'info, Mint>>,

    pub system_program: Program<'info, System>,
    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler(ctx: Context<JoinCohort>, tier: u8) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;

    guards::require_open(&ctx.accounts.cohort, now)?;
    guards::require_tier(tier)?;

    let profile = &mut ctx.accounts.profile;
    // A profile created by this very call is all zeroes, including a
    // `last_faucet_day` of 0, which would read as "claimed on day 0".
    if profile.wallet == Pubkey::default() {
        profile.wallet = ctx.accounts.member.key();
        profile.last_faucet_day = NEVER_CLAIMED;
        profile.bump = ctx.bumps.profile;
    }

    // A lobby place whose cohort has started is a running obligation now.
    profile.normalise_slots(now);

    let cohort_key = ctx.accounts.cohort.key();
    let running_key = ctx.accounts.running_cohort.as_ref().map(|a| a.key());
    let running: Option<(&Pubkey, &Cohort)> = match (&running_key, &ctx.accounts.running_cohort) {
        (Some(key), Some(account)) => Some((key, account.as_ref())),
        _ => None,
    };
    guards::require_slot_free(profile, &ctx.accounts.cohort, running, now)?;

    // The cached end, refreshed from the account the guard just checked. The
    // place carries the end across the promotion now, so for a profile written
    // since that landed this writes back what is already there; for one
    // promoted before it, this is the write that clears the stale 0.
    if let Some((_, account)) = running {
        profile.running.end = account.end();
    }

    // Both leagues are open to every member (FR-003); the genesis gate ships
    // off in this build (research R17).
    require!(
        guards::genesis_gate_ok(ctx.accounts.config.genesis_gate_enabled, false),
        HoldoutError::GenesisGateClosed
    );

    let stake = ctx.accounts.cohort.chips[tier as usize];

    // Member → vault. The member signs, so nothing here can move anyone else's SKR.
    token::transfer(
        CpiContext::new(
            ctx.accounts.token_program.key(),
            Transfer {
                from: ctx.accounts.member_token.to_account_info(),
                to: ctx.accounts.vault.to_account_info(),
                authority: ctx.accounts.member.to_account_info(),
            },
        ),
        stake,
    )?;

    let membership = &mut ctx.accounts.membership;
    membership.cohort = cohort_key;
    membership.wallet = ctx.accounts.member.key();
    membership.tier = tier;
    membership.stake = stake;
    membership.joined_at = now;
    membership.days_completed = 0;
    membership.state = MembershipState::Joined;
    membership.counted = false;
    membership.bump = ctx.bumps.membership;

    let cohort = &mut ctx.accounts.cohort;
    cohort.held_stake = cohort
        .held_stake
        .checked_add(stake)
        .ok_or(error!(HoldoutError::AccountMismatch))?;
    cohort.member_count += 1;

    // The end travels with the place. Normalisation promotes it without the
    // cohort account in hand (data-model §1.5), so a zero here would reach the
    // running slot as "no obligation" (FR-006b, INV-4).
    profile.pending = PendingSlot {
        cohort: cohort_key,
        start: cohort.start,
        end: cohort.end(),
    };

    emit!(MemberJoined {
        cohort: cohort_key,
        wallet: membership.wallet,
        tier,
        stake,
    });

    Ok(())
}
