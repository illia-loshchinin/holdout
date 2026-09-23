//! `initialize` (contracts/program-instructions §1, research R14). Task T027.
//!
//! Until `Config` exists there is no stored key to authenticate a signer
//! against, so the signer is checked against the program's own `ProgramData`:
//! only the current upgrade authority may initialize. Without that, anyone
//! watching the deploy could initialize first and keep `admin`, `operator`,
//! `sponsor` and the fee destination — and the program ID with them.

use anchor_lang::prelude::*;
use anchor_lang::system_program::{self, Transfer};
use anchor_spl::associated_token::AssociatedToken;
use anchor_spl::token::{Mint, Token, TokenAccount};

use crate::errors::HoldoutError;
use crate::program::Holdout;
use crate::state::config::{
    ChipSet, Config, Faucet, CONFIG_SEED, FAUCET_VAULT_SEED, MINT_AUTHORITY_SEED, SKR_DECIMALS,
};

#[derive(Accounts)]
// Bound under other names: the `treasury_wallet` field would shadow the argument.
#[instruction(operator_arg: Pubkey, sponsor_arg: Pubkey, treasury_arg: Pubkey)]
pub struct Initialize<'info> {
    /// Anchor deserializes every non-`init` account before it checks any
    /// constraint, and checks constraints in field order — so this one runs
    /// before `config`, the mint, the fee account or the vault is created.
    /// `None` (an immutable program) matches no signer at all.
    #[account(
        mut,
        constraint = program_data.upgrade_authority_address == Some(admin.key())
            @ HoldoutError::NotUpgradeAuthority,
    )]
    pub admin: Signer<'info>,

    #[account(
        init,
        payer = admin,
        space = 8 + Config::INIT_SPACE,
        seeds = [CONFIG_SEED],
        bump,
    )]
    pub config: Box<Account<'info, Config>>,

    /// This program, so its `ProgramData` address can be read rather than
    /// trusted: any other program's `ProgramData`, where a stranger might well
    /// be the authority, fails here.
    #[account(
        constraint = program.programdata_address()? == Some(program_data.key())
            @ HoldoutError::AccountMismatch,
    )]
    pub program: Program<'info, Holdout>,

    pub program_data: Account<'info, ProgramData>,

    /// The mock SKR mint, a fresh keypair. Only the faucet can mint (FR-042).
    #[account(
        init,
        payer = admin,
        mint::decimals = SKR_DECIMALS,
        mint::authority = mint_authority,
        mint::token_program = token_program,
    )]
    pub skr_mint: Box<Account<'info, Mint>>,

    /// CHECK: a PDA that owns no data; it only signs `mint_to` for the faucet.
    #[account(seeds = [MINT_AUTHORITY_SEED], bump)]
    pub mint_authority: UncheckedAccount<'info>,

    /// CHECK: any wallet may be the treasury; it only owns the fee account.
    #[account(address = treasury_arg @ HoldoutError::AccountMismatch)]
    pub treasury_wallet: UncheckedAccount<'info>,

    /// The treasury's own SKR account, so fees reach the treasury (R14).
    #[account(
        init,
        payer = admin,
        associated_token::mint = skr_mint,
        associated_token::authority = treasury_wallet,
        associated_token::token_program = token_program,
    )]
    pub fee_destination: Box<Account<'info, TokenAccount>>,

    /// A system-owned PDA holding lamports only. The Operator fills it with an
    /// ordinary transfer; the program spends it by signing with its seeds.
    #[account(mut, seeds = [FAUCET_VAULT_SEED], bump)]
    pub faucet_vault: SystemAccount<'info>,

    pub system_program: Program<'info, System>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
}

pub fn handle_initialize(
    ctx: Context<Initialize>,
    operator: Pubkey,
    sponsor: Pubkey,
    _treasury_wallet: Pubkey,
    faucet_sol_top_up: u64,
    faucet_daily_lamport_cap: u64,
    faucet_sponsor_float: u64,
) -> Result<()> {
    // The vault must exist at rent exemption before the first claim can spend
    // from it. Anyone may already have sent lamports to the address, so only
    // the shortfall is paid.
    let rent_exempt = Rent::get()?.minimum_balance(0);
    let shortfall = rent_exempt.saturating_sub(ctx.accounts.faucet_vault.lamports());
    if shortfall > 0 {
        system_program::transfer(
            CpiContext::new(
                ctx.accounts.system_program.key(),
                Transfer {
                    from: ctx.accounts.admin.to_account_info(),
                    to: ctx.accounts.faucet_vault.to_account_info(),
                },
            ),
            shortfall,
        )?;
    }

    let config = &mut ctx.accounts.config;
    config.admin = ctx.accounts.admin.key();
    config.operator = operator;
    config.sponsor = sponsor;
    config.skr_mint = ctx.accounts.skr_mint.key();
    config.fee_destination = ctx.accounts.fee_destination.key();
    // Both gates start off on devnet (FR-003, research R17).
    config.ladder_gate_enabled = false;
    config.genesis_gate_enabled = false;
    // No chip set yet, and no cohort may open before one exists (FR-055b).
    config.chip_set = ChipSet::default();
    config.faucet = Faucet {
        sol_top_up: faucet_sol_top_up,
        sponsor_float: faucet_sponsor_float,
        daily_lamport_cap: faucet_daily_lamport_cap,
        lamport_day: 0,
        lamports_today: 0,
    };
    config.bump = ctx.bumps.config;
    Ok(())
}
