//! `set_flags` (contracts/program-instructions §1). Task T028.
//!
//! The two gates are off on devnet (FR-003, research R17); turning one on later
//! is this instruction, never a migration. Neither flag touches money.

use anchor_lang::prelude::*;

use crate::errors::HoldoutError;
use crate::events::FlagsSet;
use crate::state::config::{Config, CONFIG_SEED};

#[derive(Accounts)]
pub struct SetFlags<'info> {
    pub admin: Signer<'info>,

    #[account(
        mut,
        seeds = [CONFIG_SEED],
        bump = config.bump,
        has_one = admin @ HoldoutError::NotAdmin,
    )]
    pub config: Box<Account<'info, Config>>,
}

pub fn handle_set_flags(
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
