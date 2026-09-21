//! `claim_faucet` (contracts/program-instructions §3). Task T038.
//!
//! The instruction that makes a **zero-SOL wallet** possible. A brand-new
//! wallet cannot pay the fee for any transaction, including its own faucet
//! claim, and a program address cannot pay fees either (research R13). So:
//!
//!  - the **recipient does not sign** — it is named in the arguments and
//!    carried as a plain writable account, which is what keeps partial signing
//!    through MWA out of the first-run flow entirely;
//!  - the bundled **sponsor** pays, and the vault reimburses its fee and any
//!    rent it paid in the same instruction, so its balance stays flat;
//!  - a wallet that already holds SOL may pay for itself instead.
//!
//! ## Why the cap counts lamports, not claims
//!
//! A recipient already holding `sol_top_up` needs no top-up — so under a claim
//! counter the rent reimbursed for its brand-new `Profile` and token account
//! left the vault uncapped, and the same recycled 0.02 SOL could buy account
//! creation for fresh wallet after fresh wallet. **Every lamport that leaves
//! the vault** is charged against `daily_lamport_cap` instead, which bounds one
//! day's abuse however it is spent. A claim that moves no lamports at all —
//! the recipient pays its own fee and already holds the top-up, as every
//! scripted wallet does (R12) — spends none of the budget.
//!
//! When the budget or the vault is exhausted the whole instruction fails with
//! its own error. There is no partial payout.

use anchor_lang::{prelude::*, system_program};
use anchor_spl::{
    associated_token::AssociatedToken,
    token::{self, Mint, MintTo, Token, TokenAccount},
};

use crate::{
    errors::HoldoutError,
    events::FaucetClaimed,
    math::schedule,
    state::{
        config::{Config, CONFIG_SEED, FAUCET_VAULT_SEED, MINT_AUTHORITY_SEED},
        profile::{Profile, NEVER_CLAIMED, PROFILE_SEED},
    },
};

/// The large accounts are boxed: `Config` and `Profile` do not fit in
/// `try_accounts`' own SBF stack frame beside the mint and two token accounts.
#[derive(Accounts)]
pub struct ClaimFaucet<'info> {
    /// `Config.sponsor` or the recipient itself — nobody else (6002).
    #[account(mut)]
    pub payer: Signer<'info>,

    #[account(mut, seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Box<Account<'info, Config>>,

    /// CHECK: the wallet being funded. It signs nothing, which is the whole
    /// point — a wallet with no SOL could not sign for itself anyway. The
    /// handler checks it against the `recipient` argument; the constraint
    /// cannot, because the account of that name shadows the argument here.
    #[account(mut)]
    pub recipient: UncheckedAccount<'info>,

    #[account(
        init_if_needed,
        payer = payer,
        space = 8 + Profile::INIT_SPACE,
        seeds = [PROFILE_SEED, recipient.key().as_ref()],
        bump,
    )]
    pub profile: Box<Account<'info, Profile>>,

    #[account(mut, address = config.skr_mint @ HoldoutError::AccountMismatch)]
    pub skr_mint: Box<Account<'info, Mint>>,

    /// CHECK: seeds-derived signer for `mint_to`; holds no data.
    #[account(seeds = [MINT_AUTHORITY_SEED], bump)]
    pub mint_authority: UncheckedAccount<'info>,

    #[account(
        init_if_needed,
        payer = payer,
        associated_token::mint = skr_mint,
        associated_token::authority = recipient,
    )]
    pub recipient_token: Box<Account<'info, TokenAccount>>,

    /// CHECK: the program's lamport store. System-owned, so lamports leave it
    /// through a System transfer this program signs for with its seeds.
    #[account(mut, seeds = [FAUCET_VAULT_SEED], bump)]
    pub faucet_vault: UncheckedAccount<'info>,

    /// CHECK: `Config.sponsor`, reimbursed when it is the payer. Never signs.
    #[account(mut, address = config.sponsor @ HoldoutError::AccountMismatch)]
    pub sponsor: UncheckedAccount<'info>,

    pub system_program: Program<'info, System>,
    pub token_program: Program<'info, Token>,
    pub associated_token_program: Program<'info, AssociatedToken>,
}

pub(crate) fn handler(ctx: Context<ClaimFaucet>, recipient: Pubkey) -> Result<()> {
    let now = Clock::get()?.unix_timestamp;
    let today = schedule::global_day_index(now);

    // The argument names the wallet being funded, and the account has to be it:
    // the PDA seeds and the token account derive from the account, so a
    // mismatch would fund one wallet while recording the claim against another.
    require_keys_eq!(
        recipient,
        ctx.accounts.recipient.key(),
        HoldoutError::AccountMismatch
    );

    let payer_key = ctx.accounts.payer.key();
    require!(
        payer_key == ctx.accounts.sponsor.key() || payer_key == recipient,
        HoldoutError::NotSponsorOrRecipient
    );

    // A profile created by this very call is all zeroes, and a zeroed
    // `last_faucet_day` would read as "claimed on day 0".
    let profile = &mut ctx.accounts.profile;
    if profile.wallet == Pubkey::default() {
        profile.wallet = recipient;
        profile.last_faucet_day = NEVER_CLAIMED;
        profile.bump = ctx.bumps.profile;
    }
    require!(
        profile.last_faucet_day < today,
        HoldoutError::FaucetAlreadyClaimedToday
    );

    // Before the first `publish_chip_set`, `max_top_tier` is zero: the claim
    // would mint no SKR and still stamp `last_faucet_day`, spending the
    // recipient's one claim of the day on nothing and leaving them unable to
    // make a real one until the next 05:00 UTC boundary. `open_cohort` already
    // refuses in this window for the same reason (FR-055b), and refused before
    // anything is written, as here.
    require!(
        ctx.accounts.config.chip_set.is_published(),
        HoldoutError::ChipSetNotPublished
    );

    // ---- work out every lamport that would leave the vault -----------------

    let top_up = ctx
        .accounts
        .config
        .faucet
        .sol_top_up
        .saturating_sub(ctx.accounts.recipient.lamports());

    // Everything the sponsor is out of pocket for, in a single measurement:
    // the gap between the float it is configured to hold and what it holds
    // now. Both halves are already inside that gap — the transaction fee was
    // deducted before this instruction began, and the rent for whichever of
    // `Profile` and the token account Anchor has just created was paid during
    // it — so adding a fee constant on top would reimburse the fee twice,
    // push the sponsor above its float and spend the day's budget faster than
    // the claim actually cost (R13).
    //
    // Measuring rather than assuming also means a priority fee the caller
    // attached is reimbursed, where a fixed constant would not have been. The
    // gap cannot exceed `sponsor_float`, so one claim can move at most that
    // much, and the daily lamport budget bounds the rest — the same bound R13
    // already accepts for a drained sponsor.
    let sponsor_is_payer = payer_key == ctx.accounts.sponsor.key();
    let reimbursement = if sponsor_is_payer {
        ctx.accounts
            .config
            .faucet
            .sponsor_float
            .saturating_sub(ctx.accounts.sponsor.lamports())
    } else {
        0
    };

    let leaving_vault = top_up
        .checked_add(reimbursement)
        .ok_or(error!(HoldoutError::FaucetCapReached))?;

    // ---- can the day, and the vault, afford it? ----------------------------

    // Charged before anything moves, so a refusal leaves no partial payout.
    require!(
        ctx.accounts.config.faucet.charge(leaving_vault, today),
        HoldoutError::FaucetCapReached
    );

    let rent_floor = Rent::get()?.minimum_balance(0);
    let spendable = ctx
        .accounts
        .faucet_vault
        .lamports()
        .saturating_sub(rent_floor);
    require!(leaving_vault <= spendable, HoldoutError::FaucetEmpty);

    // ---- move it -----------------------------------------------------------

    let vault_bump = [ctx.bumps.faucet_vault];
    let vault_seeds: &[&[u8]] = &[FAUCET_VAULT_SEED, &vault_bump];

    if top_up > 0 {
        system_program::transfer(
            CpiContext::new_with_signer(
                ctx.accounts.system_program.key(),
                system_program::Transfer {
                    from: ctx.accounts.faucet_vault.to_account_info(),
                    to: ctx.accounts.recipient.to_account_info(),
                },
                &[vault_seeds],
            ),
            top_up,
        )?;
    }
    if reimbursement > 0 {
        system_program::transfer(
            CpiContext::new_with_signer(
                ctx.accounts.system_program.key(),
                system_program::Transfer {
                    from: ctx.accounts.faucet_vault.to_account_info(),
                    to: ctx.accounts.sponsor.to_account_info(),
                },
                &[vault_seeds],
            ),
            reimbursement,
        )?;
    }

    // The highest top tier ever published, not today's: a re-price may have
    // lowered the tiers while a cohort that copied the dearer set is still
    // open, and one claim must fund the top chip of any of them (FR-042a, R15).
    let skr = ctx.accounts.config.chip_set.max_top_tier;
    let authority_bump = [ctx.bumps.mint_authority];
    let authority_seeds: &[&[u8]] = &[MINT_AUTHORITY_SEED, &authority_bump];
    token::mint_to(
        CpiContext::new_with_signer(
            ctx.accounts.token_program.key(),
            MintTo {
                mint: ctx.accounts.skr_mint.to_account_info(),
                to: ctx.accounts.recipient_token.to_account_info(),
                authority: ctx.accounts.mint_authority.to_account_info(),
            },
            &[authority_seeds],
        ),
        skr,
    )?;

    ctx.accounts.profile.last_faucet_day = today;

    emit!(FaucetClaimed {
        recipient,
        skr,
        lamports: leaving_vault,
    });

    Ok(())
}
