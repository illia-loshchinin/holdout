//! `initialize` (contracts/program-instructions §1). Task T027.
//!
//! One call, once, by the program's **current upgrade authority** — not by the
//! deployer, and not by whoever gets there first. Before `Config` exists there
//! is nothing else to authenticate a signer against, so a stranger who reached
//! a freshly deployed program first would keep `admin` and point `operator`,
//! `sponsor` and the fee destination at their own addresses, leaving no way to
//! reclaim the program ID (research R14). The `ProgramData` account the loader
//! owns is the only thing on chain that says who that authority is, so it is
//! read here and the answer is stored as `Config.admin`.
//!
//! What the call creates: `Config`, the mock SKR mint under the program's own
//! `["mint_authority"]` PDA, the treasury's SKR token account — the fee
//! destination — and the faucet's lamport vault. Both gate flags start `false`
//! (FR-003, research R17) and the chip set starts empty, which is what
//! `ChipSetNotPublished` (6012) protects until the Operator publishes one.

use anchor_lang::prelude::*;
use anchor_spl::{
    associated_token::AssociatedToken,
    token::{Mint, Token, TokenAccount},
};

use crate::{
    errors::HoldoutError,
    state::config::{ChipSet, Config, Faucet, CONFIG_SEED, FAUCET_VAULT_SEED, MINT_AUTHORITY_SEED},
};

/// Decimals of the mock SKR mint.
///
/// The spec never fixes this and devnet SKR is ours to mint, so T027 decides it:
/// **6**, the same as USDC and as SPL's own default, which keeps a $10 chip a
/// round `10_000_000` base units and every settlement vector in
/// `fixtures/settlement-vectors.json` an exact integer. The TypeScript harness
/// (`tests/helpers/harness.ts`, `SKR`) and the Kotlin client both assume it;
/// changing it here means changing them in the same commit.
pub const SKR_DECIMALS: u8 = 6;

#[derive(Accounts)]
pub struct Initialize<'info> {
    /// The program's current upgrade authority. Pays for everything below and
    /// becomes `Config.admin`.
    #[account(mut)]
    pub admin: Signer<'info>,

    #[account(
        init,
        payer = admin,
        space = 8 + Config::INIT_SPACE,
        seeds = [CONFIG_SEED],
        bump,
    )]
    pub config: Account<'info, Config>,

    /// This program. `programdata_address()` is `None` unless the upgradeable
    /// loader owns it, so this constraint also rules out a program deployed
    /// under a loader that has no authority to check.
    #[account(
        constraint = program.programdata_address()? == Some(program_data.key())
            @ HoldoutError::NotUpgradeAuthority,
    )]
    pub program: Program<'info, crate::program::Holdout>,

    /// The loader's record of who may upgrade this program. `None` means the
    /// program is immutable and nobody holds the authority, which refuses
    /// everyone — including the deployer (research R14).
    #[account(
        constraint = program_data.upgrade_authority_address == Some(admin.key())
            @ HoldoutError::NotUpgradeAuthority,
    )]
    pub program_data: Account<'info, ProgramData>,

    /// The mock SKR mint: a fresh keypair, since §6 gives it no seed. Its
    /// authority is the program's PDA, so only `claim_faucet` ever mints.
    #[account(
        init,
        payer = admin,
        mint::decimals = SKR_DECIMALS,
        mint::authority = mint_authority,
    )]
    pub skr_mint: Account<'info, Mint>,

    /// CHECK: seeds-derived signer for `mint_to`; it holds no data and is never read.
    #[account(seeds = [MINT_AUTHORITY_SEED], bump)]
    pub mint_authority: UncheckedAccount<'info>,

    /// CHECK: the treasury's (operations) wallet. It owns the fee destination and signs nothing.
    pub treasury_wallet: UncheckedAccount<'info>,

    /// The operations destination: the operations part of every forfeit, two
    /// thirds of a no-finisher pool and rounding remainders land here, in the
    /// treasury's own associated token account — the program never holds them.
    #[account(
        init,
        payer = admin,
        associated_token::mint = skr_mint,
        associated_token::authority = treasury_wallet,
    )]
    pub fee_destination: Account<'info, TokenAccount>,

    /// CHECK: the developer's wallet. It owns the developer destination — the
    /// developer part of every forfeit (H35), named in the README (constitution
    /// XI) — and signs nothing.
    pub developer_wallet: UncheckedAccount<'info>,

    #[account(
        init,
        payer = admin,
        associated_token::mint = skr_mint,
        associated_token::authority = developer_wallet,
    )]
    pub developer_destination: Account<'info, TokenAccount>,

    /// CHECK: a system-owned PDA holding nothing but lamports. Created empty and
    /// rent-exempt; the Operator tops it up with an ordinary transfer, and only
    /// the faucet instructions move lamports out of it (research R13).
    #[account(
        init,
        payer = admin,
        space = 0,
        owner = System::id(),
        seeds = [FAUCET_VAULT_SEED],
        bump,
    )]
    pub faucet_vault: UncheckedAccount<'info>,

    pub system_program: Program<'info, System>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
}

#[allow(clippy::too_many_arguments)]
pub(crate) fn handler(
    ctx: Context<Initialize>,
    operator: Pubkey,
    sponsor: Pubkey,
    treasury_wallet: Pubkey,
    developer_wallet: Pubkey,
    faucet_sol_top_up: u64,
    faucet_daily_lamport_cap: u64,
    faucet_sponsor_float: u64,
) -> Result<()> {
    // The wallet named in the arguments is the one whose token account was just
    // created; a mismatch would point the fee destination at a stranger.
    require_keys_eq!(
        treasury_wallet,
        ctx.accounts.treasury_wallet.key(),
        HoldoutError::AccountMismatch
    );
    require_keys_eq!(
        developer_wallet,
        ctx.accounts.developer_wallet.key(),
        HoldoutError::AccountMismatch
    );

    let config = &mut ctx.accounts.config;
    config.admin = ctx.accounts.admin.key();
    config.operator = operator;
    config.sponsor = sponsor;
    config.skr_mint = ctx.accounts.skr_mint.key();
    config.fee_destination = ctx.accounts.fee_destination.key();
    config.developer_destination = ctx.accounts.developer_destination.key();
    // Both gates are off in this build, and the README says why (FR-003, R17).
    config.ladder_gate_enabled = false;
    config.genesis_gate_enabled = false;
    // No chip set yet: `open_cohort` refuses until the Operator publishes one,
    // so no cohort can ever copy four zeroed amounts (FR-055b).
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
