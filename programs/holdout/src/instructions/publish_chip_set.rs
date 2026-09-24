//! `publish_chip_set` (contracts/program-instructions §1). Task T058.
//!
//! The Operator prices the four chips in SKR off chain and publishes the
//! amounts. **No price, no rate and no fiat figure reaches the program**
//! (constitution III, AC-9): the only argument is four SKR amounts, and the
//! only rule about them is that they are strictly increasing and positive.
//!
//! Two properties the rest of the build leans on:
//!
//!  - A publish **never touches a cohort**. A cohort copies the amounts when it
//!    opens and keeps them for its whole life, so a re-price cannot move a
//!    member's stake under them (FR-055, constitution V).
//!  - `max_top_tier` is a high-water mark and never decreases. It is what the
//!    faucet mints, so one claim always covers the top chip of **any** open
//!    cohort — including an older one that copied a more expensive set before a
//!    re-price lowered the tiers (FR-042a, FR-055b, research R15).

use anchor_lang::prelude::*;

use crate::{
    errors::HoldoutError,
    events::ChipSetPublished,
    state::config::{Config, CONFIG_SEED},
};

#[derive(Accounts)]
pub struct PublishChipSet<'info> {
    #[account(constraint = operator.key() == config.operator @ HoldoutError::NotOperator)]
    pub operator: Signer<'info>,

    #[account(mut, seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Config>,
}

/// `0 < a0 < a1 < a2 < a3`. Strict, so no two tiers can ever be the same chip.
fn strictly_increasing(amounts: &[u64; 4]) -> bool {
    amounts[0] > 0 && amounts.windows(2).all(|pair| pair[0] < pair[1])
}

pub(crate) fn handler(ctx: Context<PublishChipSet>, amounts: [u64; 4]) -> Result<()> {
    require!(strictly_increasing(&amounts), HoldoutError::InvalidChipSet);

    let now = Clock::get()?.unix_timestamp;
    let config = &mut ctx.accounts.config;

    config.chip_set.amounts = amounts;
    config.chip_set.version += 1;
    config.chip_set.published_at = now;
    config.chip_set.max_top_tier = config.chip_set.max_top_tier.max(amounts[3]);

    emit!(ChipSetPublished {
        version: config.chip_set.version,
        amounts,
    });

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_set_is_four_strictly_increasing_amounts_above_zero() {
        assert!(strictly_increasing(&[10, 25, 50, 100]));

        assert!(!strictly_increasing(&[0, 25, 50, 100]), "a free chip");
        assert!(!strictly_increasing(&[10, 10, 50, 100]), "two equal tiers");
        assert!(
            !strictly_increasing(&[10, 50, 25, 100]),
            "a decreasing pair"
        );
        assert!(!strictly_increasing(&[0, 0, 0, 0]), "the unpublished set");
    }
}
