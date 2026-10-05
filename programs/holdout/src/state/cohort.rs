//! `Cohort` (data-model §1.3). Task T022.
//!
//! One run of a league: its parameters are fixed at creation (constitution V),
//! its status is derived from chain time rather than stored (constitution II),
//! and its settlement figures are written exactly once (INV-5).

use anchor_lang::prelude::*;

use crate::{
    errors::HoldoutError,
    math::{schedule, settlement},
};

/// PDA seed prefix, followed by `league_id` and `index` (u32 little-endian).
pub const COHORT_SEED: &[u8] = b"cohort";
/// PDA seed prefix of a cohort's SKR vault, followed by the cohort address.
pub const VAULT_SEED: &[u8] = b"vault";

/// Where a cohort is in its life, derived from chain time and `settled` alone.
/// Not stored: every device and every instruction computes it the same way, so a
/// missed automation run can delay money but never a transition (constitution II).
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, PartialEq, Eq)]
pub enum CohortStatus {
    /// Joining and leaving are open.
    Open,
    /// Joining has closed, the roster is final, the obligation has not begun.
    Locked,
    /// Members owe a day each day.
    Running,
    /// The final deadline has passed and settlement has not finished.
    Settling,
    /// `settle_finalize` has run. Only claims can still change anything (INV-6).
    Settled,
}

#[account]
#[derive(InitSpace)]
pub struct Cohort {
    // ---- fixed at creation (FR-007) --------------------------------------
    pub league_id: u8,
    pub index: u32,
    pub start: i64,
    pub join_cutoff: i64,
    pub duration_days: u8,
    pub reps_per_day: u16,
    pub day_length: i64,
    pub exercise: u8,
    /// How reps are judged, copied from the league (H35): see [`crate::state::league::FORM_TOES`].
    pub form: u8,
    /// The four chip amounts, copied from `Config.chip_set` and never changed —
    /// a later publish cannot move this cohort's stakes (FR-055).
    pub chips: [u64; 4],

    // ---- running totals ---------------------------------------------------
    pub member_count: u32,
    /// Members' stakes, held separately from the pool at every instant (INV-1).
    pub held_stake: u64,
    /// Only forfeited stakes ever enter this, and a boost drawn from the
    /// league's reserve of earlier forfeits (INV-2).
    pub pool: u64,
    /// The boost-reserve share of this cohort's forfeits, held in its vault
    /// until it finalizes and moves to the league (FR-037c).
    pub boost_due: u64,
    /// The comeback-reserve share, held likewise (FR-037c).
    pub comeback_due: u64,
    pub forfeits_recorded: u32,
    /// Memberships `settle_count` has processed; settlement finalizes when this
    /// reaches `member_count` (INV-5).
    pub counted: u32,
    pub finisher_count: u32,
    pub finishers_per_tier: [u32; 4],
    /// The payout denominator, written as finishers are counted (FR-033).
    pub total_finisher_stake: u64,

    // ---- written once by settle_finalize (INV-5, INV-6) -------------------
    pub settled: bool,
    pub share_per_tier: [u64; 4],
    pub rounding_remainder: u64,
    /// A cohort nobody finished: its whole pool, sent to the developer and
    /// operations destinations (FR-037).
    pub no_finisher_fee: u64,
    /// The boost this cohort drew from its league's reserve (FR-037d).
    pub boost_received: u64,
    /// Comeback refunds owed to its finishers, summed as they are counted (FR-037e).
    pub comeback_owed: u64,
    /// What finalization drew from the comeback reserve: `min(owed, reserve)`.
    pub comeback_funded: u64,
    /// Refunds paid out with claims so far.
    pub comeback_claimed: u64,

    pub vault_bump: u8,
    pub bump: u8,
}

impl Cohort {
    /// The instant after the last day's deadline: `start + duration * day_length`.
    pub fn end(&self) -> i64 {
        self.start + i64::from(self.duration_days) * self.day_length
    }

    /// The last second of day `k` (data-model §1.3).
    pub fn deadline_of(&self, day_index: u8) -> i64 {
        schedule::deadline_of(self.start, self.day_length, day_index)
    }

    /// The current zero-based day while `Running`, clamped outside it.
    pub fn day_index(&self, now: i64) -> u8 {
        schedule::days_elapsed(self.start, self.day_length, self.duration_days, now)
    }

    /// The most days any member of this cohort may have completed (INV-3).
    pub fn max_days_completed(&self, now: i64) -> u8 {
        schedule::max_days_completed(self.start, self.day_length, self.duration_days, now)
    }

    /// Status from chain time and `settled` alone (data-model §1.3).
    pub fn status(&self, now: i64) -> CohortStatus {
        if self.settled {
            return CohortStatus::Settled;
        }
        if now < self.join_cutoff {
            CohortStatus::Open
        } else if now < self.start {
            CohortStatus::Locked
        } else if now < self.end() {
            CohortStatus::Running
        } else {
            CohortStatus::Settling
        }
    }

    /// Materialise one forfeited stake on this cohort's books (FR-027, H35): it
    /// leaves `held_stake`, its pool part joins the pool, its reserve parts wait
    /// in the vault for finalization (FR-037c). The developer and operations
    /// parts are the caller's to transfer out; the split says how much.
    pub fn take_forfeit(&mut self, stake: u64) -> Result<settlement::ForfeitSplit> {
        let split = settlement::forfeit_split(stake);
        self.held_stake = self
            .held_stake
            .checked_sub(stake)
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        self.pool = self
            .pool
            .checked_add(split.pool)
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        self.boost_due = self
            .boost_due
            .checked_add(split.boost)
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        self.comeback_due = self
            .comeback_due
            .checked_add(split.comeback)
            .ok_or(error!(HoldoutError::AccountMismatch))?;
        self.forfeits_recorded += 1;
        Ok(split)
    }

    /// What the vault must hold after every instruction touching this cohort
    /// (INV-1). Asserted by the test suite rather than by the program, which
    /// moves the three parts individually.
    pub fn custody(&self) -> u64 {
        self.held_stake
            + self.pool
            + self.boost_due
            + self.comeback_due
            + (self.comeback_funded - self.comeback_claimed)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::league::{DEVNET_DAY_LENGTH, DEVNET_JOIN_LOCK};

    /// Monday 28 September 2026, 05:00:00 UTC — the first judged cohort start.
    const FIRST_START: i64 = 1_790_571_600;

    fn cohort() -> Cohort {
        Cohort {
            league_id: 0,
            index: 0,
            start: FIRST_START,
            join_cutoff: FIRST_START - DEVNET_JOIN_LOCK,
            duration_days: 7,
            reps_per_day: 25,
            day_length: DEVNET_DAY_LENGTH,
            exercise: 0,
            form: 0,
            chips: [10, 25, 50, 100],
            member_count: 0,
            held_stake: 0,
            pool: 0,
            boost_due: 0,
            comeback_due: 0,
            forfeits_recorded: 0,
            counted: 0,
            finisher_count: 0,
            finishers_per_tier: [0; 4],
            total_finisher_stake: 0,
            settled: false,
            share_per_tier: [0; 4],
            rounding_remainder: 0,
            no_finisher_fee: 0,
            boost_received: 0,
            comeback_owed: 0,
            comeback_funded: 0,
            comeback_claimed: 0,
            vault_bump: 0,
            bump: 0,
        }
    }

    #[test]
    fn every_status_boundary_is_one_second_wide() {
        let c = cohort();
        assert_eq!(c.status(c.join_cutoff - 1), CohortStatus::Open);
        assert_eq!(
            c.status(c.join_cutoff),
            CohortStatus::Locked,
            "the cutoff itself closes it"
        );
        assert_eq!(c.status(c.start - 1), CohortStatus::Locked);
        assert_eq!(c.status(c.start), CohortStatus::Running);
        assert_eq!(
            c.status(c.end() - 1),
            CohortStatus::Running,
            "the last second still counts"
        );
        assert_eq!(c.status(c.end()), CohortStatus::Settling);
    }

    #[test]
    fn settled_outranks_the_clock() {
        let mut c = cohort();
        c.settled = true;
        assert_eq!(c.status(c.start - 1), CohortStatus::Settled);
        assert_eq!(c.status(c.end() + 99), CohortStatus::Settled);
    }

    #[test]
    fn the_end_is_the_instant_after_the_final_deadline() {
        let c = cohort();
        assert_eq!(c.deadline_of(6) + 1, c.end());
        assert_eq!(c.end(), FIRST_START + 7 * DEVNET_DAY_LENGTH);
    }

    #[test]
    fn custody_is_the_three_parts_the_vault_holds() {
        let mut c = cohort();
        c.held_stake = 400;
        c.pool = 680;
        assert_eq!(c.custody(), 1_080);
    }
}
