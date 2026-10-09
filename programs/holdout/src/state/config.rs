//! `Config` singleton (data-model §1.1). Task T020.
//!
//! One account holds every privileged key, the mock SKR mint, the chip set and
//! the faucet's books. Nothing here decides who won or lost: the keys may only
//! run the instructions listed beside them in `contracts/program-instructions.md`
//! §1, and none of those moves a stake or a pool (constitution I).

use anchor_lang::prelude::*;

/// PDA seed for the singleton.
pub const CONFIG_SEED: &[u8] = b"config";
/// PDA seed for the mint authority that signs faucet `mint_to`.
pub const MINT_AUTHORITY_SEED: &[u8] = b"mint_authority";
/// PDA seed for the program-owned lamport store behind the faucet.
pub const FAUCET_VAULT_SEED: &[u8] = b"faucet_vault";

/// Lamports a claim tops a recipient up to: 0.02 SOL, enough for membership and
/// profile rent plus every fee of a 21-day cycle (research R13).
pub const DEFAULT_SOL_TOP_UP: u64 = 20_000_000;
/// Lamports `top_up_sponsor` restores the bundled fee payer to: 0.05 SOL. Small
/// on purpose — anyone can extract the key from the app and empty it (FR-042b).
pub const DEFAULT_SPONSOR_FLOAT: u64 = 50_000_000;
/// Every lamport leaving the faucet vault in one day is charged against this:
/// 0.72 SOL, which is 34 days inside a vault funded to 25 SOL (research R13).
pub const DEFAULT_DAILY_LAMPORT_CAP: u64 = 720_000_000;

/// The four stake chips, and what the faucet mints.
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, Default, InitSpace)]
pub struct ChipSet {
    /// $10 / $25 / $50 / $100 in SKR base units, strictly increasing (FR-054).
    pub amounts: [u64; 4],
    /// `0` until the first publish. No cohort may open before that (FR-055b).
    pub version: u32,
    /// Chain time of the publish that produced `amounts`.
    pub published_at: i64,
    /// High-water mark of every `amounts[3]` ever published, never decreasing.
    /// The faucet mints this rather than today's top tier, so one claim funds
    /// the top chip of **any** open cohort even after a re-price lowered the
    /// tiers under a cohort that had already copied them (FR-042a, research R15).
    pub max_top_tier: u64,
}

impl ChipSet {
    /// Whether a chip set has been published at all.
    pub fn is_published(&self) -> bool {
        self.version > 0
    }

    /// The SKR amount of a tier, or `None` when the tier is outside 0..=3.
    pub fn amount(&self, tier: u8) -> Option<u64> {
        self.amounts.get(tier as usize).copied()
    }
}

/// The faucet's limits and today's spend (research R13).
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, Default, InitSpace)]
pub struct Faucet {
    /// Lamports a claim tops the recipient up to.
    pub sol_top_up: u64,
    /// Lamports `top_up_sponsor` restores the sponsor to.
    pub sponsor_float: u64,
    /// The daily budget over every lamport that leaves the vault.
    pub daily_lamport_cap: u64,
    /// Global day index `lamports_today` belongs to (05:00 UTC boundary, OD-6).
    pub lamport_day: i64,
    /// Lamports already out of the vault today.
    pub lamports_today: u64,
}

impl Faucet {
    /// Lamports still available today, rolling the counter over on a new day.
    /// A caller that has not yet rolled it over sees the full budget, which is
    /// what a day boundary crossing mid-transaction should give them.
    pub fn remaining_today(&self, today: i64) -> u64 {
        if self.lamport_day == today {
            self.daily_lamport_cap.saturating_sub(self.lamports_today)
        } else {
            self.daily_lamport_cap
        }
    }

    /// Charge `lamports` against today's budget. `false` when it would exceed
    /// the cap and nothing is written — the caller turns that into
    /// `FaucetCapReached` (6051), never a partial payout.
    pub fn charge(&mut self, lamports: u64, today: i64) -> bool {
        if lamports > self.remaining_today(today) {
            return false;
        }
        if self.lamport_day != today {
            self.lamport_day = today;
            self.lamports_today = 0;
        }
        self.lamports_today = self.lamports_today.saturating_add(lamports);
        true
    }

    /// Lamports `top_up_sponsor` would move, given the sponsor's balance. `None`
    /// while the sponsor still holds half its float, which is the refusal
    /// `SponsorFloatSufficient` (6054) reports (FR-042b).
    pub fn sponsor_refill(&self, sponsor_lamports: u64) -> Option<u64> {
        if sponsor_lamports >= self.sponsor_float / 2 {
            return None;
        }
        Some(self.sponsor_float - sponsor_lamports)
    }
}

#[account]
#[derive(InitSpace)]
pub struct Config {
    /// Signer of `initialize`, accepted only as the program's upgrade authority
    /// (research R14). May only run `initialize`, `create_league`, `set_flags`.
    pub admin: Pubkey,
    /// May only run `publish_chip_set`.
    pub operator: Pubkey,
    /// Fee payer `claim_faucet` accepts beside the recipient itself. Holds
    /// nothing but its float, which anyone may refill (FR-042b).
    pub sponsor: Pubkey,
    /// Mock SKR mint; its authority is the `["mint_authority"]` PDA.
    pub skr_mint: Pubkey,
    /// The **operations** destination: the treasury's SKR token account. The
    /// operations part of every forfeit, half of a no-finisher pool and
    /// rounding remainders land here (research R14, H35).
    pub fee_destination: Pubkey,
    /// The **developer** destination: the developer's SKR token account. The
    /// developer part of every forfeit and half of a no-finisher pool (H35).
    pub developer_destination: Pubkey,
    /// `false` on devnet: both leagues are open from launch (FR-003).
    pub ladder_gate_enabled: bool,
    /// `false` on devnet: testers have no Seeker (research R17).
    pub genesis_gate_enabled: bool,
    pub chip_set: ChipSet,
    pub faucet: Faucet,
    pub bump: u8,
}

#[cfg(test)]
mod tests {
    use super::*;

    fn faucet() -> Faucet {
        Faucet {
            sol_top_up: DEFAULT_SOL_TOP_UP,
            sponsor_float: DEFAULT_SPONSOR_FLOAT,
            daily_lamport_cap: DEFAULT_DAILY_LAMPORT_CAP,
            lamport_day: 7,
            lamports_today: 0,
        }
    }

    #[test]
    fn the_budget_covers_every_lamport_and_refuses_the_one_too_many() {
        let mut f = faucet();
        assert!(f.charge(DEFAULT_DAILY_LAMPORT_CAP - 1, 7));
        assert!(!f.charge(2, 7), "a charge past the cap is refused whole");
        assert_eq!(
            f.lamports_today,
            DEFAULT_DAILY_LAMPORT_CAP - 1,
            "and writes nothing"
        );
        assert!(f.charge(1, 7), "the last lamport of the day still fits");
    }

    #[test]
    fn the_budget_rolls_over_with_the_day() {
        let mut f = faucet();
        assert!(f.charge(DEFAULT_DAILY_LAMPORT_CAP, 7));
        assert_eq!(f.remaining_today(7), 0);
        assert_eq!(
            f.remaining_today(8),
            DEFAULT_DAILY_LAMPORT_CAP,
            "a new day, a new budget"
        );
        assert!(f.charge(1, 8));
        assert_eq!(f.lamports_today, 1, "yesterday's spend does not carry");
        assert_eq!(f.lamport_day, 8);
    }

    #[test]
    fn a_refill_waits_until_the_float_is_half_gone() {
        let f = faucet();
        let half = DEFAULT_SPONSOR_FLOAT / 2;
        assert_eq!(f.sponsor_refill(DEFAULT_SPONSOR_FLOAT), None);
        assert_eq!(f.sponsor_refill(half), None, "still half full");
        assert_eq!(
            f.sponsor_refill(half - 1),
            Some(DEFAULT_SPONSOR_FLOAT - (half - 1))
        );
        assert_eq!(
            f.sponsor_refill(0),
            Some(DEFAULT_SPONSOR_FLOAT),
            "drained by whoever has the key"
        );
    }

    #[test]
    fn a_chip_set_is_unpublished_until_it_has_a_version() {
        let mut c = ChipSet::default();
        assert!(!c.is_published(), "no cohort may open here (FR-055b)");
        c.version = 1;
        c.amounts = [10, 25, 50, 100];
        assert!(c.is_published());
        assert_eq!(c.amount(3), Some(100));
        assert_eq!(c.amount(4), None, "tier 4 does not exist (6022)");
    }
}
