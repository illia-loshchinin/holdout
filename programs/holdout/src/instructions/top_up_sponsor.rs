//! `top_up_sponsor` (contracts/program-instructions §2). Task T038a.
//!
//! The fee-sponsor key ships inside the app (research R13, README known
//! weaknesses), so anyone can extract it and empty its float with a plain
//! System Program transfer — no guard here can prevent that, because such a
//! transfer never touches this program. What this instruction decides is what
//! an empty sponsor *costs*.
//!
//! **Permissionless, and the sponsor does not sign.** Any wallet with a little
//! devnet SOL can refill the float from the vault, and the crank does it on
//! every ten-minute pass. A drain therefore pauses zero-SOL onboarding for at
//! most one pass, needs no Operator during the live period (FR-042a forbids
//! manual intervention), and costs the vault no more than the day's budget
//! (FR-042b).
//!
//! The refill draws on the **same** daily lamport budget as a claim. There is
//! deliberately no separate per-day refill counter: the lamport budget already
//! bounds the day, and a second counter would only bound it twice while adding
//! state and another way to be wrong. So a griefer has to spend the whole day's
//! allowance to stop onboarding for the rest of that day.
//!
//! Both refusals — `SponsorFloatSufficient` above half the float, and
//! `FaucetCapReached` once the day is spent — are ordinary outcomes rather than
//! incidents: the crank reports them, exits 0 and tries again next pass.

use anchor_lang::{prelude::*, system_program};

use crate::{
    errors::HoldoutError,
    events::SponsorToppedUp,
    math::schedule,
    state::config::{Config, CONFIG_SEED, FAUCET_VAULT_SEED},
};

#[derive(Accounts)]
pub struct TopUpSponsor<'info> {
    /// Anyone. Pays the fee and gets nothing for it.
    #[account(mut)]
    pub caller: Signer<'info>,

    #[account(mut, seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,

    /// CHECK: the program's lamport store, system-owned.
    #[account(mut, seeds = [FAUCET_VAULT_SEED], bump)]
    pub faucet_vault: UncheckedAccount<'info>,

    /// CHECK: `Config.sponsor`. The lamport recipient, and it never signs.
    #[account(mut, address = config.sponsor @ HoldoutError::AccountMismatch)]
    pub sponsor: UncheckedAccount<'info>,

    pub system_program: Program<'info, System>,
}

pub(crate) fn handler(ctx: Context<TopUpSponsor>) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let today = schedule::global_day_index(now);

    // Above half the float there is nothing to do: the float only has to cover
    // the fees between two crank passes.
    let refill = ctx
        .accounts
        .config
        .faucet
        .sponsor_refill(ctx.accounts.sponsor.lamports())
        .ok_or(error!(HoldoutError::SponsorFloatSufficient))?;

    // Charged before anything moves, and against the same budget as a claim.
    require!(
        ctx.accounts.config.faucet.charge(refill, today),
        HoldoutError::FaucetCapReached
    );

    let rent_floor = Rent::get()?.minimum_balance(0);
    let spendable = ctx
        .accounts
        .faucet_vault
        .lamports()
        .saturating_sub(rent_floor);
    require!(refill <= spendable, HoldoutError::FaucetEmpty);

    let vault_bump = [ctx.bumps.faucet_vault];
    let vault_seeds: &[&[u8]] = &[FAUCET_VAULT_SEED, &vault_bump];
    system_program::transfer(
        CpiContext::new_with_signer(
            ctx.accounts.system_program.key(),
            system_program::Transfer {
                from: ctx.accounts.faucet_vault.to_account_info(),
                to: ctx.accounts.sponsor.to_account_info(),
            },
            &[vault_seeds],
        ),
        refill,
    )?;

    emit!(SponsorToppedUp { lamports: refill });

    Ok(())
}
