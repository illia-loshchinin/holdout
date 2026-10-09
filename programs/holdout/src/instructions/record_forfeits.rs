//! `record_forfeits` (contracts/program-instructions §2). Task T069.
//!
//! Permissionless, paged and repeatable. It does not *decide* anything: a
//! member whose deadline passed with the day incomplete is already out by
//! `Membership::in_breach`, which every reader computes from chain time alone
//! (constitution II, AC-2). This only materialises that — splits the stake
//! 70 / 5 / 5 / 10 / 10 (FR-027, H35): the pool part funds the cohort's pool, the
//! developer and operations parts leave at once, and the two reserve parts wait
//! in the vault until the cohort finalizes (FR-037c) — and records the outcome
//! on the profile.
//!
//! **Repeatable by construction** (AC-5, INV-7, research R9). A pair that is
//! not in breach, or already forfeited, is *skipped* rather than refused, so the
//! crank may re-send a page, overlap pages, or order them however it likes. The
//! only refusal is a malformed page: a profile that is not the membership's, or
//! a membership from another cohort, fails the whole page with `AccountMismatch`
//! (6060) rather than being quietly skipped — a mismatch is a caller bug, and
//! skipping it would let a page look done when it had done nothing.
//!
//! **The league streak is not touched here.** A mid-cohort forfeit can be
//! recorded before an earlier cohort of the same league has been counted, so a
//! streak written now would depend on the order automation happened to run in.
//! `settle_count` writes it, in cohort order (FR-061a, INV-9). What *is* written
//! now is `forfeited`, an order-independent counter (FR-060a), and
//! `last_changed` — the **missed deadline**, never `now`, so neither the page
//! order nor how late the crank runs can reorder two members tied on a
//! leaderboard (FR-060b).

use anchor_lang::prelude::*;
use anchor_spl::token::{Token, TokenAccount};

use crate::{
    errors::HoldoutError,
    events::ForfeitRecorded,
    guards,
    state::{
        cohort::{Cohort, COHORT_SEED, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        membership::{Membership, MembershipState},
        profile::{Profile, PROFILE_SEED},
    },
    vault::transfer_signed,
};

/// Pairs one page may carry (research R9). Ten `(Membership, Profile)` pairs
/// plus the fixed accounts fit a transaction with room for the fee CPI.
pub const MAX_PAIRS: usize = 10;

/// `Cohort` is boxed: it does not fit in `try_accounts`' own SBF stack frame
/// beside the rest.
#[derive(Accounts)]
pub struct RecordForfeits<'info> {
    /// Anyone. Pays the fee, and the result does not depend on who they are.
    #[account(mut)]
    pub caller: Signer<'info>,

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

    #[account(
        mut,
        constraint = fee_destination.key() == config.fee_destination @ HoldoutError::AccountMismatch,
    )]
    pub fee_destination: Box<Account<'info, TokenAccount>>,

    #[account(
        mut,
        constraint = developer_destination.key() == config.developer_destination @ HoldoutError::AccountMismatch,
    )]
    pub developer_destination: Box<Account<'info, TokenAccount>>,

    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler<'info>(ctx: Context<'info, RecordForfeits<'info>>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let pairs = ctx.remaining_accounts;

    // An odd page would leave a membership without the profile its outcome is
    // recorded on, which is a caller bug rather than something to guess at.
    require!(pairs.len().is_multiple_of(2), HoldoutError::AccountMismatch);
    require!(pairs.len() / 2 <= MAX_PAIRS, HoldoutError::AccountMismatch);

    let cohort_key = ctx.accounts.cohort.key();
    let mut to_developer: u64 = 0;
    let mut to_operations: u64 = 0;

    for pair in pairs.chunks(2) {
        let mut membership: Account<'info, Membership> = Account::try_from(&pair[0])?;
        let mut profile: Account<'info, Profile> = Account::try_from(&pair[1])?;

        // Both halves checked before anything is written: a page that is wrong
        // anywhere writes nothing at all.
        require_keys_eq!(membership.cohort, cohort_key, HoldoutError::AccountMismatch);
        let expected_profile = Pubkey::create_program_address(
            &[PROFILE_SEED, membership.wallet.as_ref(), &[profile.bump]],
            &crate::ID,
        )
        .map_err(|_| error!(HoldoutError::AccountMismatch))?;
        require_keys_eq!(
            profile.key(),
            expected_profile,
            HoldoutError::AccountMismatch
        );
        require_keys_eq!(
            profile.wallet,
            membership.wallet,
            HoldoutError::AccountMismatch
        );

        // Not in breach, or already dealt with: skipped, not refused.
        if !membership.in_breach(&ctx.accounts.cohort, now) {
            continue;
        }

        let cohort = &mut ctx.accounts.cohort;
        let split = cohort.take_forfeit(membership.stake)?;
        to_developer = to_developer
            .checked_add(split.developer)
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        to_operations = to_operations
            .checked_add(split.operations)
            .ok_or(error!(HoldoutError::AccountMismatch))?;

        // The deadline they missed, not the moment automation noticed it.
        let outcome_at = guards::forfeit_instant(&membership, cohort);
        let league_id = cohort.league_id;
        let progress = profile.progress_mut(league_id);
        progress.forfeited = progress.forfeited.saturating_add(1);
        progress.touch(outcome_at);
        // Normalisation first (data-model §1.5): a member who never sat a
        // single day still has this cohort in `pending`, and `release_running`
        // cannot reach it there. Without this, forfeiting without ever sitting
        // would leave the lobby place on their profile for good and every later
        // join would answer `LobbyPlaceTaken` — a forfeit ending the member's
        // place in the league, which is exactly what FR-002 forbids.
        profile.normalise_slots(now);
        profile.release_running(&cohort_key);

        membership.state = MembershipState::Forfeited;

        emit!(ForfeitRecorded {
            cohort: cohort_key,
            wallet: membership.wallet,
            days_completed: membership.days_completed,
            fee: split.fee(),
        });

        membership.exit(&crate::ID)?;
        profile.exit(&crate::ID)?;
    }

    // One transfer per destination for the page rather than one per pair: the
    // end state is identical and the CPI budget is not spent ten times over.
    let league_id = ctx.accounts.cohort.league_id;
    let index = ctx.accounts.cohort.index.to_le_bytes();
    let bump = [ctx.accounts.cohort.bump];
    let seeds: &[&[u8]] = &[COHORT_SEED, &[league_id], &index, &bump];
    let token_program = ctx.accounts.token_program.to_account_info();
    transfer_signed(
        &token_program,
        ctx.accounts.vault.to_account_info(),
        ctx.accounts.developer_destination.to_account_info(),
        ctx.accounts.cohort.to_account_info(),
        seeds,
        to_developer,
    )?;
    transfer_signed(
        &token_program,
        ctx.accounts.vault.to_account_info(),
        ctx.accounts.fee_destination.to_account_info(),
        ctx.accounts.cohort.to_account_info(),
        seeds,
        to_operations,
    )?;

    Ok(())
}
