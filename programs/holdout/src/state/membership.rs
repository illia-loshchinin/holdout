//! `Membership` (data-model §1.4). Task T023.
//!
//! One member's place in one cohort. Breach is derived from chain time, never
//! stored and never declared: automation only materialises it (constitution II).

use anchor_lang::prelude::*;

use super::cohort::Cohort;

/// PDA seed prefix, followed by the cohort address and the wallet.
pub const MEMBERSHIP_SEED: &[u8] = b"member";

/// The highest tier index a chip set has (FR-054, error 6022).
pub const MAX_TIER: u8 = 3;

/// `Withdrawn` is not here: leaving the lobby closes the account and returns its
/// rent. `Pending` and `Active` are derived from the cohort's status while the
/// state is `Joined` (spec §8.2).
#[derive(
    AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, Default, PartialEq, Eq, InitSpace,
)]
pub enum MembershipState {
    #[default]
    Joined,
    Forfeited,
    Finished,
    Claimed,
}

#[account]
#[derive(InitSpace)]
pub struct Membership {
    pub cohort: Pubkey,
    pub wallet: Pubkey,
    /// 0..=3.
    pub tier: u8,
    /// `cohort.chips[tier]` at join, never changed afterwards (FR-058).
    pub stake: u64,
    pub joined_at: i64,
    /// +1 per accepted `submit_day`; never above `cohort.max_days_completed(now)`
    /// (INV-3).
    pub days_completed: u8,
    pub state: MembershipState,
    /// Set once by `settle_count`, which makes paging order-independent (R9).
    pub counted: bool,
    pub bump: u8,
}

impl Membership {
    /// In breach the instant the current day's deadline passes with the day
    /// incomplete — computable from chain state alone, whether or not any
    /// automation has run (constitution II, data-model §1.4).
    pub fn in_breach(&self, cohort: &Cohort, now: i64) -> bool {
        self.state == MembershipState::Joined
            && now >= cohort.start
            && self.days_completed < cohort.duration_days
            && now > cohort.deadline_of(self.days_completed)
    }

    /// Still standing: finished, or joined and not in breach. This is what the
    /// survivor counter counts, at the instant of the deadline rather than at
    /// the instant a forfeit is recorded (FR-038a).
    pub fn standing(&self, cohort: &Cohort, now: i64) -> bool {
        match self.state {
            MembershipState::Finished | MembershipState::Claimed => true,
            MembershipState::Joined => !self.in_breach(cohort, now),
            MembershipState::Forfeited => false,
        }
    }

    /// Days lasted, which orders forfeited members on the roster (FR-062).
    pub fn days_lasted(&self) -> u8 {
        self.days_completed
    }

    /// Whether this membership has cleared its cohort — every day completed.
    pub fn has_cleared(&self, cohort: &Cohort) -> bool {
        self.days_completed == cohort.duration_days
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::league::DEVNET_DAY_LENGTH;

    const FIRST_START: i64 = 1_790_571_600;

    fn cohort() -> Cohort {
        let mut c = Cohort {
            league_id: 0,
            index: 0,
            start: FIRST_START,
            join_cutoff: FIRST_START - 3_600,
            duration_days: 7,
            reps_per_day: 25,
            day_length: DEVNET_DAY_LENGTH,
            exercise: 0,
            chips: [10, 25, 50, 100],
            member_count: 0,
            held_stake: 0,
            pool: 0,
            carry_over_received: 0,
            forfeits_recorded: 0,
            counted: 0,
            finisher_count: 0,
            finishers_per_tier: [0; 4],
            total_finisher_stake: 0,
            settled: false,
            share_per_tier: [0; 4],
            rounding_remainder: 0,
            no_finisher_fee: 0,
            carry_over_sent: 0,
            carry_over_held: 0,
            carry_over_target: Pubkey::default(),
            vault_bump: 0,
            bump: 0,
        };
        c.member_count = 1;
        c
    }

    fn membership(days_completed: u8, state: MembershipState) -> Membership {
        Membership {
            cohort: Pubkey::default(),
            wallet: Pubkey::default(),
            tier: 3,
            stake: 100,
            joined_at: FIRST_START - 7_200,
            days_completed,
            state,
            counted: false,
            bump: 0,
        }
    }

    #[test]
    fn breach_begins_one_second_after_the_deadline() {
        let c = cohort();
        let m = membership(0, MembershipState::Joined);
        let deadline = c.deadline_of(0);
        assert!(
            !m.in_breach(&c, deadline),
            "the deadline second is still theirs"
        );
        assert!(m.in_breach(&c, deadline + 1), "and the next one is not");
    }

    #[test]
    fn a_lobby_membership_cannot_be_in_breach() {
        let c = cohort();
        let m = membership(0, MembershipState::Joined);
        assert!(
            !m.in_breach(&c, c.start - 1),
            "nothing is owed before the start (old I2)"
        );
    }

    #[test]
    fn a_member_who_completed_every_day_is_never_in_breach() {
        let c = cohort();
        let m = membership(7, MembershipState::Joined);
        assert!(!m.in_breach(&c, c.end() + 99));
        assert!(m.has_cleared(&c));
    }

    #[test]
    fn standing_treats_a_breach_as_out_before_any_forfeit_is_recorded() {
        let c = cohort();
        let breached = membership(0, MembershipState::Joined);
        let at = c.deadline_of(0) + 1;
        assert!(
            !breached.standing(&c, at),
            "the counter drops at the deadline (FR-038a)"
        );

        let finished = membership(7, MembershipState::Finished);
        assert!(finished.standing(&c, at));
        let claimed = membership(7, MembershipState::Claimed);
        assert!(claimed.standing(&c, at));
        let forfeited = membership(2, MembershipState::Forfeited);
        assert!(!forfeited.standing(&c, at));
        assert_eq!(
            forfeited.days_lasted(),
            2,
            "roster order for the forfeited (FR-062)"
        );
    }
}
