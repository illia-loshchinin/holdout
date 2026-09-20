//! `settle_finalize` (contracts/program-instructions §2). Task T079.
//!
//! One call, once, and the figures it writes are the same for every member
//! (FR-031, INV-5, INV-6). The shares are **per tier**, not per member: a
//! cohort has exactly four chip amounts (FR-055), so four numbers describe
//! every finisher and finalisation costs the same whether the cohort holds ten
//! members or a thousand (research R10).
//!
//! ## The carry-over destination is not the caller's to choose
//!
//! When nobody finished, half the pool goes to the treasury and the rest must
//! reach a cohort members can still join (FR-037, FR-037a). The destination is
//! fixed by chain state — the league's cohort `next_index − 1` — and the
//! accounts for it are **required**, not optional:
//!
//!  - withheld → `CarryOverTargetRequired` (6046), so no caller can strand the
//!    money by omission;
//!  - not that cohort → `AccountMismatch` (6060), so none can pick a different
//!    one;
//!  - that cohort is this one, or has already locked → `CarryOverTargetNotOpen`
//!    (6047).
//!
//! The last refusal is always answerable: `open_cohort` is permissionless and
//! due the moment the previous cohort started, so the crank opens the league's
//! next lobby and settles in the same pass. It therefore costs one automation
//! pass rather than parking money the caller's timing chose to strand — which
//! is why **Phase 1 never writes `carry_over_held`** (FR-030a).

use anchor_lang::prelude::*;
use anchor_spl::token::{self, Token, TokenAccount, Transfer};

use crate::{
    errors::HoldoutError,
    events::{CarryOverSent, CohortSettled},
    guards,
    math::settlement,
    state::{
        cohort::{Cohort, CohortStatus, COHORT_SEED, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        league::{LeagueConfig, LEAGUE_SEED},
    },
};

/// The large accounts are boxed: two `Cohort`s and a `Config` do not fit in
/// `try_accounts`' own SBF stack frame.
#[derive(Accounts)]
pub struct SettleFinalize<'info> {
    /// Anyone. The caller's identity never changes the outcome (FR-030a).
    #[account(mut)]
    pub caller: Signer<'info>,

    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,

    #[account(
        seeds = [LEAGUE_SEED, &[league.league_id]],
        bump = league.bump,
    )]
    pub league: Box<Account<'info, LeagueConfig>>,

    #[account(
        mut,
        constraint = cohort.league_id == league.league_id @ HoldoutError::AccountMismatch,
    )]
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

    /// The league's cohort `next_index - 1`. Required when nobody finished.
    #[account(mut)]
    pub target_cohort: Option<Box<Account<'info, Cohort>>>,

    /// That cohort's vault.
    #[account(mut)]
    pub target_vault: Option<Box<Account<'info, TokenAccount>>>,

    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler<'info>(ctx: Context<'info, SettleFinalize<'info>>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    guards::require_settling(&ctx.accounts.cohort, now)?;
    require!(
        ctx.accounts.cohort.counted == ctx.accounts.cohort.member_count,
        HoldoutError::CountIncomplete
    );

    let cohort_key = ctx.accounts.cohort.key();
    let league_id = ctx.accounts.cohort.league_id;
    let index_bytes = ctx.accounts.cohort.index.to_le_bytes();
    let bump = [ctx.accounts.cohort.bump];
    let signer: &[&[u8]] = &[COHORT_SEED, &[league_id], &index_bytes, &bump];

    let pool = ctx.accounts.cohort.pool;
    let total_finisher_stake = ctx.accounts.cohort.total_finisher_stake;

    if total_finisher_stake > 0 {
        let chips = ctx.accounts.cohort.chips;
        let mut shares = [0u64; 4];
        for tier in 0..4 {
            shares[tier] = settlement::share_for_tier(pool, chips[tier], total_finisher_stake);
        }
        let remainder =
            settlement::rounding_remainder(pool, ctx.accounts.cohort.finishers_per_tier, shares);

        if remainder > 0 {
            transfer_from_vault(
                &ctx,
                signer,
                ctx.accounts.fee_destination.to_account_info(),
                remainder,
            )?;
        }

        let cohort = &mut ctx.accounts.cohort;
        cohort.share_per_tier = shares;
        cohort.rounding_remainder = remainder;
        cohort.pool = pool
            .checked_sub(remainder)
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        cohort.settled = true;

        emit!(CohortSettled {
            cohort: cohort_key,
            finishers: cohort.finisher_count,
            total_finisher_stake,
            share_per_tier: shares,
            rounding_remainder: remainder,
        });

        return Ok(());
    }

    // Nobody finished. The destination is whatever chain state says it is.
    let target_index = ctx
        .accounts
        .league
        .next_index
        .checked_sub(1)
        .ok_or(error!(HoldoutError::CarryOverTargetNotOpen))?;

    // Answered before the accounts are even asked for. When the league's latest
    // cohort is *this* one there is no later cohort to carry into, and no
    // account the caller could pass would change that — the same account in two
    // mutable slots is not even representable. So the caller gets the reason
    // (6047: open the league's next cohort, then settle) rather than a bare
    // "target required" they could not satisfy (FR-037a).
    require!(
        target_index != ctx.accounts.cohort.index,
        HoldoutError::CarryOverTargetNotOpen
    );

    let (target_cohort, target_vault) = match (
        ctx.accounts.target_cohort.as_ref(),
        ctx.accounts.target_vault.as_ref(),
    ) {
        (Some(cohort), Some(vault)) => (cohort, vault),
        // Omission must not decide anything (FR-030a).
        _ => return Err(error!(HoldoutError::CarryOverTargetRequired)),
    };

    let expected_target = Pubkey::create_program_address(
        &[
            COHORT_SEED,
            &[league_id],
            &target_index.to_le_bytes(),
            &[target_cohort.bump],
        ],
        &crate::ID,
    )
    .map_err(|_| error!(HoldoutError::AccountMismatch))?;
    require_keys_eq!(
        target_cohort.key(),
        expected_target,
        HoldoutError::AccountMismatch
    );
    let expected_vault = Pubkey::create_program_address(
        &[
            VAULT_SEED,
            target_cohort.key().as_ref(),
            &[target_cohort.vault_bump],
        ],
        &crate::ID,
    )
    .map_err(|_| error!(HoldoutError::AccountMismatch))?;
    require_keys_eq!(
        target_vault.key(),
        expected_vault,
        HoldoutError::AccountMismatch
    );

    // The destination has to be one members can still join.
    require!(
        target_cohort.status(now) == CohortStatus::Open,
        HoldoutError::CarryOverTargetNotOpen
    );

    let (fee_half, carry) = settlement::no_finisher_split(pool);
    let target_key = target_cohort.key();
    let target_vault_info = target_vault.to_account_info();

    if fee_half > 0 {
        transfer_from_vault(
            &ctx,
            signer,
            ctx.accounts.fee_destination.to_account_info(),
            fee_half,
        )?;
    }
    if carry > 0 {
        transfer_from_vault(&ctx, signer, target_vault_info, carry)?;
    }

    let target = ctx
        .accounts
        .target_cohort
        .as_mut()
        .ok_or(error!(HoldoutError::CarryOverTargetRequired))?;
    target.pool = target
        .pool
        .checked_add(carry)
        .ok_or(error!(HoldoutError::AccountMismatch))?;
    target.carry_over_received = target
        .carry_over_received
        .checked_add(carry)
        .ok_or(error!(HoldoutError::AccountMismatch))?;

    let cohort = &mut ctx.accounts.cohort;
    cohort.no_finisher_fee = fee_half;
    cohort.carry_over_sent = carry;
    // Stays zero: Phase 1 refuses rather than parking money (FR-037a).
    cohort.carry_over_held = 0;
    cohort.carry_over_target = target_key;
    cohort.pool = 0;
    cohort.settled = true;

    emit!(CohortSettled {
        cohort: cohort_key,
        finishers: 0,
        total_finisher_stake: 0,
        share_per_tier: [0; 4],
        rounding_remainder: 0,
    });
    emit!(CarryOverSent {
        from: cohort_key,
        to: target_key,
        amount: carry,
        fee: fee_half,
    });

    Ok(())
}

/// Move SKR out of this cohort's vault, signed by the cohort PDA that holds it.
fn transfer_from_vault<'info>(
    ctx: &Context<'info, SettleFinalize<'info>>,
    signer: &[&[u8]],
    to: AccountInfo<'info>,
    amount: u64,
) -> Result<()> {
    token::transfer(
        CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            Transfer {
                from: ctx.accounts.vault.to_account_info(),
                to,
                authority: ctx.accounts.cohort.to_account_info(),
            },
            &[signer],
        ),
        amount,
    )
}
