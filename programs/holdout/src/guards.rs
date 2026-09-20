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

/// Whether this member may take a place in `cohort`. Two rules, both about
/// obligations never overlapping (FR-006a, FR-006b, INV-4): at most one lobby
/// place, and a new cohort may not start before the running one's final
/// deadline.
///
/// `running` is the profile's running cohort — its address and its account —
/// and the caller must supply it whenever `profile.running` is set, else
/// `AccountMismatch`. **The end comes from that account, never from
/// `profile.running.end`**: slot normalisation moves a lobby place into the
/// running slot knowing only the cohort's address and start (data-model §1.5),
/// so it cannot fill the end, and a guard that trusted the cached `0` would
/// skip the overlap check for exactly the window between a cohort starting and
/// its member's first completed day. Reading the cohort makes the rule depend
/// on chain state alone (constitution II).
pub fn require_slot_free(
    profile: &Profile,
    cohort: &Cohort,
    running: Option<(&Pubkey, &Cohort)>,
    now: i64,
) -> Result<()> {
    require!(profile.pending.is_none(), HoldoutError::LobbyPlaceTaken);
    if profile.running.is_none() {
        return Ok(());
    }

    let (running_key, running_cohort) = running.ok_or(error!(HoldoutError::AccountMismatch))?;
    require_keys_eq!(
        *running_key,
        profile.running.cohort,
        HoldoutError::AccountMismatch
    );

    // A running cohort whose final deadline has passed owes the member nothing,
    // even before settlement has released the slot.
    if running_cohort.end() > now {
        require!(
            cohort.start >= running_cohort.end(),
            HoldoutError::ObligationsOverlap
        );
    }
    Ok(())
}

/// The three things `submit_day` needs beyond the cohort being `Running`: the
/// day is the member's next one, it is today, and today's deadline has not
/// passed (FR-013, FR-017, FR-025, INV-8 — errors 6031, 6032, 6033).
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
    // The two mismatches are different answers, as the contract's table says:
    // a day behind the member's next one is one they already completed (6033),
    // while a day ahead of it — or one that is not today — is the wrong day
    // (6031). A member in breach meets the second: their next day is an
    // earlier one, and that day is no longer today.
    require!(
        day_index >= membership.days_completed,
        HoldoutError::DayAlreadyCompleted
    );
    require!(
        day_index == membership.days_completed,
        HoldoutError::WrongDay
    );
    require!(day_index == cohort.day_index(now), HoldoutError::WrongDay);
    require!(
        now <= cohort.deadline_of(day_index),
        HoldoutError::DeadlinePassed
    );
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

    /// The number a client would match on, out of an `anchor_lang::Error`.
    fn anchor_error_code(err: &Error) -> u32 {
        match err {
            Error::AnchorError(inner) => inner.error_code_number,
            Error::ProgramError(inner) => match inner.program_error {
                anchor_lang::solana_program::program_error::ProgramError::Custom(code) => code,
                ref other => panic!("not a custom error: {other:?}"),
            },
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

    /// The contract's table gives the two mismatches different codes, and
    /// clients match on the numbers (contracts §5).
    #[test]
    fn a_day_behind_is_already_completed_and_a_day_ahead_is_the_wrong_day() {
        let c = cohort(FIRST_START, 7);

        // Completed day 0, still day 0: asking for day 0 again.
        let err = require_submittable(&membership(1), &c, 0, FIRST_START).unwrap_err();
        assert_eq!(anchor_error_code(&err), 6033, "DayAlreadyCompleted");

        // Next day is 0, asking for day 1 — skipping ahead.
        let err = require_submittable(&membership(0), &c, 1, FIRST_START).unwrap_err();
        assert_eq!(anchor_error_code(&err), 6031, "WrongDay");

        // In breach: day 0 was missed, and it is day 2 now.
        let now = FIRST_START + 2 * DEVNET_DAY_LENGTH;
        let err = require_submittable(&membership(0), &c, 0, now).unwrap_err();
        assert_eq!(
            anchor_error_code(&err),
            6031,
            "WrongDay, not a completed day"
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
        let running = cohort(FIRST_START, 7);
        let running_key = Pubkey::new_unique();
        let running_end = running.end();
        let mut p = profile();
        p.running = RunningSlot {
            cohort: running_key,
            end: running_end,
        };
        let supplied = Some((&running_key, &running));

        let touching = cohort(running_end, 7);
        assert!(
            require_slot_free(&p, &touching, supplied, FIRST_START).is_ok(),
            "starting exactly as the running one ends is allowed (FR-006b)"
        );

        let overlapping = cohort(running_end - 1, 7);
        assert!(require_slot_free(&p, &overlapping, supplied, FIRST_START).is_err());

        p.pending = PendingSlot {
            cohort: Pubkey::new_unique(),
            start: running_end,
        };
        assert!(
            require_slot_free(&p, &touching, supplied, FIRST_START).is_err(),
            "one lobby place at a time (FR-006a)"
        );
    }

    #[test]
    fn a_normalised_running_slot_still_blocks_an_overlap() {
        // The regression this guard's shape exists for. A member joined cohort A
        // and it has now started, so slot normalisation moved their lobby place
        // into the running slot — knowing only A's address and start, so
        // `running.end` is left at 0. A guard reading that cached zero would let
        // them take a second place in a cohort starting inside A's run, which is
        // INV-4 gone for the whole window before their first completed day.
        let a = cohort(FIRST_START, 7);
        let a_key = Pubkey::new_unique();
        let mut p = profile();
        p.pending = PendingSlot {
            cohort: a_key,
            start: a.start,
        };

        let now = a.start;
        p.normalise_slots(now);
        assert!(
            p.pending.is_none(),
            "the lobby place became the running one"
        );
        assert_eq!(p.running.cohort, a_key);
        assert_eq!(p.running.end, 0, "normalisation cannot know A's end");

        let b = cohort(FIRST_START + DEVNET_DAY_LENGTH, 7);
        assert!(
            require_slot_free(&p, &b, Some((&a_key, &a)), now).is_err(),
            "B starts before A ends, so the place must be refused"
        );

        // The rule is the cohort's own end, so a cohort starting at A's end is fine.
        let after = cohort(a.end(), 7);
        assert!(require_slot_free(&p, &after, Some((&a_key, &a)), now).is_ok());
    }

    #[test]
    fn a_running_slot_without_its_cohort_is_refused() {
        let a = cohort(FIRST_START, 7);
        let a_key = Pubkey::new_unique();
        let mut p = profile();
        p.running = RunningSlot {
            cohort: a_key,
            end: 0,
        };
        let b = cohort(FIRST_START + DEVNET_DAY_LENGTH, 7);

        assert!(
            require_slot_free(&p, &b, None, a.start).is_err(),
            "the caller cannot omit the account and have the rule skipped"
        );

        let stranger = Pubkey::new_unique();
        assert!(
            require_slot_free(&p, &b, Some((&stranger, &a)), a.start).is_err(),
            "nor substitute another cohort for it"
        );
    }

    #[test]
    fn a_finished_run_frees_the_slot_even_before_settlement() {
        let a = cohort(FIRST_START, 7);
        let a_key = Pubkey::new_unique();
        let mut p = profile();
        p.running = RunningSlot {
            cohort: a_key,
            end: 0,
        };

        // A's last deadline has passed; settlement has not released the slot yet.
        let now = a.end();
        let b = cohort(a.end(), 7);
        assert!(require_slot_free(&p, &b, Some((&a_key, &a)), now).is_ok());
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
