//! The checks every instruction shares: breach, status, the obligation slots and
//! the two gates (data-model §1.4, §1.5, §2). Task T025.
//!
//! Pure functions over the state structs — no account contexts, no clock reads,
//! no CPI. The instructions pass `now` in from the Clock sysvar, which keeps
//! every rule here unit-testable and keeps one definition of each rule for the
//! program, the fixtures and the Kotlin client to agree on (constitution II, VI).

use anchor_lang::prelude::*;

use crate::errors::HoldoutError;
use crate::state::cohort::{Cohort, CohortStatus};
use crate::state::membership::{Membership, MembershipState, MAX_TIER};
use crate::state::profile::Profile;

/// Joining is only open before the cutoff (FR-008, error 6021).
pub fn require_open(cohort: &Cohort, now: i64) -> Result<()> {
    require!(
        cohort.status(now) == CohortStatus::Open,
        HoldoutError::JoiningClosed
    );
    Ok(())
}

/// Completed days are only accepted while the cohort is running (error 6030).
pub fn require_running(cohort: &Cohort, now: i64) -> Result<()> {
    require!(
        cohort.status(now) == CohortStatus::Running,
        HoldoutError::CohortNotRunning
    );
    Ok(())
}

/// Settlement steps only run once the final deadline has passed and not after
/// the cohort is settled (errors 6040, 6041).
pub fn require_settling(cohort: &Cohort, now: i64) -> Result<()> {
    require!(!cohort.settled, HoldoutError::AlreadySettled);
    require!(
        cohort.status(now) == CohortStatus::Settling,
        HoldoutError::NotSettling
    );
    Ok(())
}

/// A tier is 0..=3 (FR-054, error 6022).
pub fn require_tier(tier: u8) -> Result<()> {
    require!(tier <= MAX_TIER, HoldoutError::InvalidTier);
    Ok(())
}

/// The ladder gate: a 21-day join needs a cleared 7-day cohort, but only while
/// the flag is on. The unlock is recorded either way (FR-003, FR-003a).
pub fn ladder_gate_ok(enabled: bool, league_id: u8, qualified: bool) -> bool {
    !enabled || league_id == crate::state::league::LEAGUE_7D || qualified
}

/// The genesis gate (research R17). `holds_genesis` is established by the
/// instruction from the accounts it was passed; with the flag off, nothing is
/// checked and no account is needed.
pub fn genesis_gate_ok(enabled: bool, holds_genesis: bool) -> bool {
    !enabled || holds_genesis
}

/// Whether this member may take a place in `cohort`, given the profile after
/// slot normalisation. Two rules, both about obligations never overlapping
/// (FR-006a, FR-006b, INV-4): at most one lobby place, and a new cohort may not
/// start before the running one's final deadline.
pub fn require_slot_free(profile: &Profile, cohort: &Cohort, now: i64) -> Result<()> {
    require!(profile.pending.is_none(), HoldoutError::LobbyPlaceTaken);
    if !profile.running.is_none() && profile.running.end > now {
        require!(
            cohort.start >= profile.running.end,
            HoldoutError::ObligationsOverlap
        );
    }
    Ok(())
}

/// The three things `submit_day` needs beyond the cohort being `Running`: the
/// day is the member's next one, it is today, and today's deadline has not
/// passed (FR-013, FR-017, FR-025, INV-8 — errors 6031, 6032, 6033).
///
/// The order is what tells the three apart, because a client picks its recovery
/// path from the code. A day below `days_completed` is a repeat whatever the
/// clock says (6033); one above it was never the member's next day, so it is
/// wrong rather than late (6031); and only once the index is the right one does
/// the deadline speak — before the calendar does, since a missed day always
/// falls on a day that has since turned over, and `WrongDay` would otherwise
/// swallow every 6032 a member could ever see.
pub fn require_submittable(
    membership: &Membership,
    cohort: &Cohort,
    day_index: u8,
    now: i64,
) -> Result<()> {
    require!(
        membership.state == MembershipState::Joined,
        HoldoutError::CohortNotRunning
    );
    require!(
        day_index >= membership.days_completed,
        HoldoutError::DayAlreadyCompleted
    );
    require!(
        day_index == membership.days_completed,
        HoldoutError::WrongDay
    );
    require!(
        now <= cohort.deadline_of(day_index),
        HoldoutError::DeadlinePassed
    );
    require!(day_index == cohort.day_index(now), HoldoutError::WrongDay);
    Ok(())
}

/// The outcome instant a forfeit records on the profile: the deadline the member
/// missed, not the moment automation noticed (FR-060b).
pub fn forfeit_instant(membership: &Membership, cohort: &Cohort) -> i64 {
    cohort.deadline_of(membership.days_completed)
}

/// The outcome instant a clear records: the cohort's end, identical for everyone
/// who cleared it (FR-031, FR-060b).
pub fn clear_instant(cohort: &Cohort) -> i64 {
    cohort.end()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::league::{DEVNET_DAY_LENGTH, DEVNET_JOIN_LOCK, LEAGUE_21D, LEAGUE_7D};
    use crate::state::profile::{PendingSlot, RunningSlot};

    const FIRST_START: i64 = 1_790_571_600;

    fn cohort(start: i64, duration_days: u8) -> Cohort {
        Cohort {
            league_id: 0,
            index: 0,
            start,
            join_cutoff: start - DEVNET_JOIN_LOCK,
            duration_days,
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
        }
    }

    fn membership(days_completed: u8) -> Membership {
        Membership {
            cohort: Pubkey::default(),
            wallet: Pubkey::default(),
            tier: 3,
            stake: 100,
            joined_at: 0,
            days_completed,
            state: MembershipState::Joined,
            counted: false,
            bump: 0,
        }
    }

    fn profile() -> Profile {
        Profile {
            wallet: Pubkey::default(),
            qualified: false,
            leagues: Default::default(),
            running: RunningSlot::default(),
            pending: PendingSlot::default(),
            last_faucet_day: -1,
            bump: 0,
        }
    }

    /// The code a rejection carries — the contract a client matches on, so the
    /// tests below pin it rather than settling for `is_err`.
    fn code_of(result: Result<()>) -> u32 {
        match result.expect_err("expected a rejection") {
            Error::AnchorError(e) => e.error_code_number,
            other => panic!("expected an AnchorError, got {other:?}"),
        }
    }

    #[test]
    fn a_day_is_submittable_only_on_its_own_day_and_before_its_deadline() {
        let c = cohort(FIRST_START, 7);
        let m = membership(0);
        assert!(
            require_submittable(&m, &c, 0, FIRST_START).is_ok(),
            "the first second of day 0"
        );
        assert!(
            require_submittable(&m, &c, 0, c.deadline_of(0)).is_ok(),
            "and its last"
        );
        assert!(
            require_submittable(&m, &c, 0, c.deadline_of(0) + 1).is_err(),
            "in breach, and no automation had to run for that (INV-8)"
        );
        assert!(
            require_submittable(&m, &c, 1, FIRST_START).is_err(),
            "not tomorrow's day"
        );
        assert!(
            require_submittable(&membership(1), &c, 1, FIRST_START).is_err(),
            "day 1 is not today yet"
        );
    }

    #[test]
    fn each_rejected_submission_names_its_own_code() {
        let c = cohort(FIRST_START, 7);

        // A missed day: still the member's next one, but its deadline has gone.
        // The clock has moved on to day 1, which must not turn this into 6031 —
        // day lengths are positive, so every missed day looks exactly like this.
        assert_eq!(
            code_of(require_submittable(
                &membership(0),
                &c,
                0,
                c.deadline_of(0) + 1
            )),
            u32::from(HoldoutError::DeadlinePassed),
            "6032 is what a member who missed day 0 must be told"
        );

        // A repeat: the day is below `days_completed`, whatever the clock says.
        assert_eq!(
            code_of(require_submittable(
                &membership(2),
                &c,
                1,
                FIRST_START + 2 * c.day_length
            )),
            u32::from(HoldoutError::DayAlreadyCompleted),
            "6033 is a second submit, and only that (FR-025)"
        );

        // A day that was never the member's next one — malformed or stale, not
        // a repeat: nothing has completed day 3.
        assert_eq!(
            code_of(require_submittable(&membership(0), &c, 3, FIRST_START)),
            u32::from(HoldoutError::WrongDay),
            "6031 for a future index, never 6033"
        );

        // The right index, in time for its deadline, but not today: the member
        // is somehow ahead of the cohort.
        assert_eq!(
            code_of(require_submittable(&membership(3), &c, 3, FIRST_START)),
            u32::from(HoldoutError::WrongDay),
            "6031 for the calendar day"
        );
    }

    #[test]
    fn the_ladder_gate_only_bites_when_it_is_on() {
        assert!(
            ladder_gate_ok(false, LEAGUE_21D, false),
            "devnet: both leagues open (FR-003)"
        );
        assert!(!ladder_gate_ok(true, LEAGUE_21D, false));
        assert!(ladder_gate_ok(true, LEAGUE_21D, true));
        assert!(
            ladder_gate_ok(true, LEAGUE_7D, false),
            "the 7-day league never closes (FR-002)"
        );
    }

    #[test]
    fn two_obligations_can_never_overlap() {
        let running_end = FIRST_START + 7 * DEVNET_DAY_LENGTH;
        let mut p = profile();
        p.running = RunningSlot {
            cohort: Pubkey::new_unique(),
            end: running_end,
        };

        let touching = cohort(running_end, 7);
        assert!(
            require_slot_free(&p, &touching, FIRST_START).is_ok(),
            "starting exactly as the running one ends is allowed (FR-006b)"
        );

        let overlapping = cohort(running_end - 1, 7);
        assert!(require_slot_free(&p, &overlapping, FIRST_START).is_err());

        p.pending = PendingSlot {
            cohort: Pubkey::new_unique(),
            start: running_end,
            end: running_end + 7 * DEVNET_DAY_LENGTH,
        };
        assert!(
            require_slot_free(&p, &touching, FIRST_START).is_err(),
            "one lobby place at a time (FR-006a)"
        );
    }

    #[test]
    fn a_promoted_lobby_place_still_blocks_an_overlapping_join() {
        let started = cohort(FIRST_START, 7);
        let mut p = profile();
        p.pending = PendingSlot {
            cohort: Pubkey::new_unique(),
            start: started.start,
            end: started.end(),
        };

        // The place is taken while the cohort is still in the lobby, and the
        // member comes back on its first day — the promotion has to hand the
        // running slot a real end, not a zero, or nothing below refuses.
        let now = started.start;
        p.normalise_slots(now);
        assert!(p.pending.is_none(), "promoted");

        let overlapping = cohort(started.end() - 1, 7);
        assert!(
            require_slot_free(&p, &overlapping, now).is_err(),
            "a cohort starting inside the promoted obligation (FR-006b, INV-4)"
        );

        let after = cohort(started.end(), 7);
        assert!(
            require_slot_free(&p, &after, now).is_ok(),
            "one starting as it ends is fine"
        );
    }

    #[test]
    fn status_guards_agree_with_the_clock() {
        let c = cohort(FIRST_START, 7);
        assert!(require_open(&c, c.join_cutoff - 1).is_ok());
        assert!(require_open(&c, c.join_cutoff).is_err(), "ES-12");
        assert!(require_running(&c, FIRST_START).is_ok());
        assert!(require_running(&c, c.end()).is_err());
        assert!(require_settling(&c, c.end()).is_ok());
        assert!(require_settling(&c, c.end() - 1).is_err());

        let mut settled = cohort(FIRST_START, 7);
        settled.settled = true;
        assert!(require_settling(&settled, settled.end()).is_err(), "INV-6");
    }

    #[test]
    fn outcome_instants_come_from_the_cohort_not_the_clock() {
        let c = cohort(FIRST_START, 7);
        assert_eq!(clear_instant(&c), c.end());
        assert_eq!(forfeit_instant(&membership(2), &c), c.deadline_of(2));
    }

    #[test]
    fn tiers_stop_at_three() {
        assert!(require_tier(0).is_ok());
        assert!(require_tier(3).is_ok());
        assert!(require_tier(4).is_err());
    }
}
