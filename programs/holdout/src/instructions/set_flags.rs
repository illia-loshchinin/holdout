//! `set_flags` (contracts/program-instructions §1). Task T028.
//!
//! The two gates this build ships off, and the README says why: the ladder gate
//! because nobody should have to spend a week before reaching the 21-day league
//! (FR-003), the genesis gate because testers have no Seeker (research R17).
//! Both are still computed and recorded, so flipping a flag is the only
//! difference between this build and an enforced ladder (AC-1) — which is what
//! makes this instruction worth having rather than a redeploy.
//!
//! It moves no money. Nothing here, and nothing the admin can call, touches a
//! held stake, a pool or a carry-over (constitution I).

use anchor_lang::prelude::*;

use crate::{
    errors::HoldoutError,
    events::FlagsSet,
    state::config::{Config, CONFIG_SEED},
};

#[derive(Accounts)]
pub struct SetFlags<'info> {
    #[account(constraint = admin.key() == config.admin @ HoldoutError::NotAdmin)]
    pub admin: Signer<'info>,

    #[account(mut, seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Config>,
}

pub(crate) fn handler(
    ctx: Context<SetFlags>,
    ladder_gate_enabled: bool,
    genesis_gate_enabled: bool,
) -> Result<()> {
    let config = &mut ctx.accounts.config;
    config.ladder_gate_enabled = ladder_gate_enabled;
    config.genesis_gate_enabled = genesis_gate_enabled;

    emit!(FlagsSet {
        ladder_gate_enabled,
        genesis_gate_enabled,
    });

    Ok(())
}
