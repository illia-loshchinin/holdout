//! `settle_count` (contracts/program-instructions §2). Tasks T078 and T084.
//!
//! Counting is where a cohort's outcome becomes each member's history. It is
//! paged and repeatable like `record_forfeits`, and it is the **only** step
//! that writes a league streak.
//!
//! Why the streak waits until here (FR-061a, INV-9): a mid-cohort forfeit can
//! be recorded before an earlier cohort of the same league has been counted, so
//! a streak written at forfeit time would depend on the order automation
//! happened to run in. Counting instead runs in cohort order — the guard below
//! refuses cohort *i* until cohort *i − 1* of the league is settled — so every
//! profile's streak follows the member's own cohorts, whatever the crank did.
//!
//! Every pair is validated **before anything is mutated**. Without that, a
//! caller could count a foreign membership, mark it `counted`, and drive this
//! cohort's `counted` to `member_count` without its own members ever being
//! counted (`AccountMismatch`, 6060).

use anchor_lang::prelude::*;
use anchor_spl::token::{Token, TokenAccount};

use crate::{
    errors::HoldoutError,
    events::{ForfeitRecorded, MemberCounted},
    guards,
    math::settlement,
    state::{
        cohort::{Cohort, COHORT_SEED, VAULT_SEED},
        config::{Config, CONFIG_SEED},
        league::{LeagueConfig, LEAGUE_7D, LEAGUE_SEED},
        membership::{Membership, MembershipState},
        profile::{Profile, PROFILE_SEED},
    },
    vault::transfer_signed,
};

pub use super::record_forfeits::MAX_PAIRS;

/// The large accounts are boxed: they do not fit in `try_accounts`' own SBF
/// stack frame beside the rest.
#[derive(Accounts)]
pub struct SettleCount<'info> {
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

    #[account(
        mut,
        constraint = developer_destination.key() == config.developer_destination @ HoldoutError::AccountMismatch,
    )]
    pub developer_destination: Box<Account<'info, TokenAccount>>,

    /// The league's cohort `index - 1`, required for every index but 0.
    pub previous_cohort: Option<Box<Account<'info, Cohort>>>,

    pub token_program: Program<'info, Token>,
}

pub(crate) fn handler<'info>(ctx: Context<'info, SettleCount<'info>>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    guards::require_settling(&ctx.accounts.cohort, now)?;

    // Cohort order, so every streak this page writes follows the member's own
    // cohorts rather than the crank's schedule (FR-061a).
    let index = ctx.accounts.cohort.index;
    if index > 0 {
        let previous = ctx
            .accounts
            .previous_cohort
            .as_ref()
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        require!(
            previous.league_id == ctx.accounts.cohort.league_id && previous.index == index - 1,
            HoldoutError::AccountMismatch
        );
        require!(previous.settled, HoldoutError::PreviousCohortUnsettled);
    }

    let pairs = ctx.remaining_accounts;
    require!(pairs.len().is_multiple_of(2), HoldoutError::AccountMismatch);
    require!(pairs.len() / 2 <= MAX_PAIRS, HoldoutError::AccountMismatch);

    let cohort_key = ctx.accounts.cohort.key();
    let duration_days = ctx.accounts.cohort.duration_days;
    let league_id = ctx.accounts.cohort.league_id;
    let mut to_developer: u64 = 0;
    let mut to_operations: u64 = 0;

    // Validate the whole page first: a page wrong anywhere writes nothing.
    let mut decoded: Vec<(Account<'info, Membership>, Account<'info, Profile>)> =
        Vec::with_capacity(pairs.len() / 2);
    for pair in pairs.chunks(2) {
        let membership: Account<'info, Membership> = Account::try_from(&pair[0])?;
        let profile: Account<'info, Profile> = Account::try_from(&pair[1])?;

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

        // No membership twice in one page. Decoding the page up front gives
        // every entry its own copy of the account, so two entries naming the
        // same membership would both read `counted == false` — the `counted`
        // guard below reads that copy, not the account, and cannot see the
        // write the earlier pass made. The second pass would then count the
        // member again: `cohort.counted`, `finisher_count` and
        // `total_finisher_stake` all rise twice. A caller who duplicates one
        // member and omits another still reaches `counted == member_count`,
        // finalises on distorted shares, and leaves the omitted member's stake
        // locked in a cohort that can never be counted again (INV-1, INV-2).
        //
        // `record_forfeits` cannot be reached this way: it decodes inside its
        // loop, so a second pass re-reads `Forfeited` and `in_breach` is false.
        // Anchor's own duplicate-mutable-account check does not help here,
        // because it covers the `Accounts` struct and not `remaining_accounts`.
        //
        // At most `MAX_PAIRS` entries, so the scan is trivial.
        require!(
            decoded
                .iter()
                .all(|(seen, _)| seen.key() != membership.key()),
            HoldoutError::AccountMismatch
        );

        decoded.push((membership, profile));
    }

    for (membership, profile) in decoded.iter_mut() {
        if membership.counted {
            continue;
        }

        // A breach nobody recorded is recorded here, on exactly the terms
        // `record_forfeits` uses — the split, the pool, the missed deadline.
        if membership.in_breach(&ctx.accounts.cohort, now) {
            let cohort = &mut ctx.accounts.cohort;
            let split = cohort.take_forfeit(membership.stake)?;
            to_developer = to_developer
                .checked_add(split.developer)
                .ok_or(error!(HoldoutError::AccountMismatch))?;
            to_operations = to_operations
                .checked_add(split.operations)
                .ok_or(error!(HoldoutError::AccountMismatch))?;

            let outcome_at = guards::forfeit_instant(membership, cohort);
            let progress = profile.progress_mut(league_id);
            progress.forfeited = progress.forfeited.saturating_add(1);
            progress.touch(outcome_at);

            membership.state = MembershipState::Forfeited;

            emit!(ForfeitRecorded {
                cohort: cohort_key,
                wallet: membership.wallet,
                days_completed: membership.days_completed,
                fee: split.fee(),
            });
        }

        let finished = membership.state == MembershipState::Joined
            && membership.days_completed == duration_days;

        if finished {
            let stake = membership.stake;
            let tier = membership.tier as usize;
            let cohort = &mut ctx.accounts.cohort;
            cohort.finisher_count += 1;
            cohort.finishers_per_tier[tier] += 1;
            cohort.total_finisher_stake = cohort
                .total_finisher_stake
                .checked_add(stake)
                .ok_or(error!(HoldoutError::AccountMismatch))?;

            membership.state = MembershipState::Finished;

            // The cohort's `end`, identical for everyone who cleared it, so no
            // two finishers can be ordered apart by when they were counted.
            let outcome_at = guards::clear_instant(cohort);
            let cohort_start = cohort.start;
            let progress = profile.progress_mut(league_id);

            // A comeback (FR-037e): this cohort started inside the window a
            // counted forfeit of the same league opened. Counting runs in cohort
            // order, so which forfeit that is never depends on the crank.
            if let Some(forfeited_stake) = progress.take_comeback(cohort_start) {
                let entitled = settlement::comeback_owed(forfeited_stake, stake);
                membership.comeback_entitled = entitled;
                let cohort = &mut ctx.accounts.cohort;
                cohort.comeback_owed = cohort
                    .comeback_owed
                    .checked_add(entitled)
                    .ok_or(error!(HoldoutError::AccountMismatch))?;
            }

            let progress = profile.progress_mut(league_id);
            progress.record_clear(outcome_at);

            // The 21-day unlock: recorded on every 7-day clear, whether or not
            // the gate is on, and never cleared (T084, FR-003a, FR-004).
            if league_id == LEAGUE_7D {
                profile.qualified = true;
            }
        } else {
            // Counted in a forfeited state, whenever that forfeit was recorded.
            let outcome_at = guards::forfeit_instant(membership, &ctx.accounts.cohort);
            let window = settlement::COMEBACK_WINDOW_DAYS * ctx.accounts.cohort.day_length;
            let progress = profile.progress_mut(league_id);
            progress.record_forfeit_counted(outcome_at);
            // The latest counted forfeit of this league may still be made good
            // by finishing a later cohort that starts within the window (FR-037e).
            // A member who left is never counted, so only a real forfeit opens one.
            if membership.state == MembershipState::Forfeited {
                progress.open_comeback(
                    membership.stake,
                    outcome_at,
                    outcome_at.saturating_add(window),
                );
            }
        }

        // Either way the obligation is over, so the member is free to stake
        // again — which means clearing the place wherever it sits. A member who
        // never sat a day still has this cohort in `pending`, and
        // `release_running` cannot reach it there (data-model §1.5).
        profile.normalise_slots(now);
        profile.release_running(&cohort_key);

        membership.counted = true;
        ctx.accounts.cohort.counted += 1;

        emit!(MemberCounted {
            cohort: cohort_key,
            wallet: membership.wallet,
            finished,
        });

        membership.exit(&crate::ID)?;
        profile.exit(&crate::ID)?;
    }

    // One transfer per destination for the page, as `record_forfeits` does.
    let index_bytes = ctx.accounts.cohort.index.to_le_bytes();
    let bump = [ctx.accounts.cohort.bump];
    let seeds: &[&[u8]] = &[COHORT_SEED, &[league_id], &index_bytes, &bump];
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
