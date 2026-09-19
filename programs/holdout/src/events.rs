//! Every event the program emits (contracts/program-instructions §4). Task T026.
//!
//! These are the only stream the app, the crank and the leaderboards read
//! besides the accounts themselves. Each one carries what a reader needs to
//! reproduce the change from chain state alone — no server fills any gap.

use anchor_lang::prelude::*;

#[event]
pub struct FlagsSet {
    pub ladder_gate_enabled: bool,
    pub genesis_gate_enabled: bool,
}

#[event]
pub struct ChipSetPublished {
    pub version: u32,
    pub amounts: [u64; 4],
}

#[event]
pub struct FaucetClaimed {
    pub recipient: Pubkey,
    /// SKR minted: the high-water top tier, so it funds any open cohort (FR-042a).
    pub skr: u64,
    /// Lamports that left the vault — top-up plus any sponsor reimbursement.
    pub lamports: u64,
}

#[event]
pub struct SponsorToppedUp {
    pub lamports: u64,
}

#[event]
pub struct CohortOpened {
    pub league: u8,
    pub index: u32,
    pub start: i64,
    pub chips: [u64; 4],
}

#[event]
pub struct MemberJoined {
    pub cohort: Pubkey,
    pub wallet: Pubkey,
    pub tier: u8,
    pub stake: u64,
}

#[event]
pub struct MemberLeft {
    pub cohort: Pubkey,
    pub wallet: Pubkey,
    pub stake: u64,
}

#[event]
pub struct DayCompleted {
    pub cohort: Pubkey,
    pub wallet: Pubkey,
    pub day_index: u8,
    /// Client-signed proof of the sitting — a documented trust gap until the
    /// attestation signer exists (constitution XI, scope doc D-E).
    pub proof_hash: [u8; 32],
}

#[event]
pub struct ForfeitRecorded {
    pub cohort: Pubkey,
    pub wallet: Pubkey,
    pub days_completed: u8,
    pub fee: u64,
}

#[event]
pub struct MemberCounted {
    pub cohort: Pubkey,
    pub wallet: Pubkey,
    pub finished: bool,
}

#[event]
pub struct CohortSettled {
    pub cohort: Pubkey,
    pub finishers: u32,
    pub total_finisher_stake: u64,
    pub share_per_tier: [u64; 4],
    pub rounding_remainder: u64,
}

#[event]
pub struct CarryOverSent {
    pub from: Pubkey,
    pub to: Pubkey,
    pub amount: u64,
    pub fee: u64,
}

/// Phase 1 never emits this: a no-finisher cohort is refused rather than settled
/// while its carry-over has nowhere to go (FR-037a). It stays for the league in
/// which nobody ever opens another cohort.
#[event]
pub struct CarryOverHeld {
    pub cohort: Pubkey,
    pub amount: u64,
    pub fee: u64,
}

/// **[Phase 2]** — the single delivery of an amount a stopped league held.
#[event]
pub struct CarryOverDelivered {
    pub from: Pubkey,
    pub to: Pubkey,
    pub amount: u64,
}

#[event]
pub struct PayoutClaimed {
    pub cohort: Pubkey,
    pub wallet: Pubkey,
    pub amount: u64,
}
