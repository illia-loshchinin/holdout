//! `set_flags` (contracts/program-instructions §1). Task T028.
//!
//! The genesis gate ships off in this build because testers have no Seeker
//! (research R17), and the README says so. The check is still written, so
//! flipping the flag is the only difference between this build and an enforced
//! gate — which is what makes this instruction worth having rather than a
//! redeploy. There is no ladder gate: both leagues are open to every member
//! (FR-003, 2026-10-10).
//!
//! It moves no money. Nothing here, and nothing the admin can call, touches a
//! held stake or a pool (constitution I).

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

pub(crate) fn handler(ctx: Context<SetFlags>, genesis_gate_enabled: bool) -> Result<()> {
    let config = &mut ctx.accounts.config;
    config.genesis_gate_enabled = genesis_gate_enabled;

    emit!(FlagsSet {
        genesis_gate_enabled
    });

    Ok(())
}
