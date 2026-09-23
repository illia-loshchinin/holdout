//! `publish_chip_set` (contracts/program-instructions §1). Task T058.
//!
//! The Operator converts the USD tiers to SKR off-chain and publishes four
//! amounts. No price, rate or fiat figure is passed or stored (constitution
//! III). A publish never changes a cohort that already copied a set (FR-055),
//! and `max_top_tier` only ever rises, so the faucet always mints enough for
//! the top chip of every cohort that exists (FR-042a, FR-055b).

use anchor_lang::prelude::*;

use crate::errors::HoldoutError;
use crate::events::ChipSetPublished;
use crate::state::config::{ChipSet, Config, CONFIG_SEED};

#[derive(Accounts)]
pub struct PublishChipSet<'info> {
    pub operator: Signer<'info>,

    #[account(
        mut,
        seeds = [CONFIG_SEED],
        bump = config.bump,
        has_one = operator @ HoldoutError::NotOperator,
    )]
    pub config: Box<Account<'info, Config>>,
}

/// `0 < a0 < a1 < a2 < a3` (error 6011).
pub fn is_valid_chip_set(amounts: &[u64; 4]) -> bool {
    amounts[0] > 0 && amounts.windows(2).all(|pair| pair[0] < pair[1])
}

/// The set that replaces `current`: new amounts, the next version, and a
/// high-water mark that never falls.
pub fn next_chip_set(current: &ChipSet, amounts: [u64; 4], now: i64) -> Result<ChipSet> {
    require!(is_valid_chip_set(&amounts), HoldoutError::InvalidChipSet);
    Ok(ChipSet {
        amounts,
        version: current
            .version
            .checked_add(1)
            .ok_or(HoldoutError::InvalidChipSet)?,
        published_at: now,
        max_top_tier: current.max_top_tier.max(amounts[3]),
    })
}

pub fn handle_publish_chip_set(ctx: Context<PublishChipSet>, amounts: [u64; 4]) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let config = &mut ctx.accounts.config;
    config.chip_set = next_chip_set(&config.chip_set, amounts, now)?;
    emit!(ChipSetPublished {
        version: config.chip_set.version,
        amounts,
    });
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    const SKR: u64 = 1_000_000;

    #[test]
    fn a_chip_set_is_four_strictly_increasing_positive_amounts() {
        assert!(is_valid_chip_set(&[
            10 * SKR,
            25 * SKR,
            50 * SKR,
            100 * SKR
        ]));
        assert!(is_valid_chip_set(&[1, 2, 3, 4]));
        assert!(!is_valid_chip_set(&[0, 25, 50, 100]), "a zero chip");
        assert!(!is_valid_chip_set(&[10, 10, 50, 100]), "equal neighbours");
        assert!(!is_valid_chip_set(&[10, 50, 25, 100]), "a decreasing pair");
        assert!(!is_valid_chip_set(&[10, 25, 50, 50]), "equal at the top");
    }

    #[test]
    fn the_high_water_mark_never_falls() {
        let first = next_chip_set(&ChipSet::default(), [10, 25, 50, 100], 7).unwrap();
        assert_eq!(first.version, 1);
        assert_eq!(first.published_at, 7);
        assert_eq!(first.max_top_tier, 100);

        let cheaper = next_chip_set(&first, [5, 10, 20, 40], 8).unwrap();
        assert_eq!(cheaper.version, 2);
        assert_eq!(cheaper.amounts, [5, 10, 20, 40]);
        assert_eq!(
            cheaper.max_top_tier, 100,
            "still funds the older cohort's top chip"
        );

        let dearer = next_chip_set(&cheaper, [20, 50, 100, 200], 9).unwrap();
        assert_eq!(dearer.max_top_tier, 200);
    }

    #[test]
    fn an_invalid_set_publishes_nothing() {
        let current = next_chip_set(&ChipSet::default(), [10, 25, 50, 100], 7).unwrap();
        assert_eq!(
            next_chip_set(&current, [10, 10, 50, 100], 8).unwrap_err(),
            HoldoutError::InvalidChipSet.into()
        );
    }
}
