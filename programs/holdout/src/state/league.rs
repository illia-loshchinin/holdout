//! `LeagueConfig` (data-model §1.2). Task T021.
//!
//! A league is the schedule and the obligation; a cohort is one run of it. Every
//! parameter a cohort will copy lives here, and a cohort copies them at creation
//! so a running cohort's rules can never move under its members (constitution V).

use anchor_lang::prelude::*;

/// PDA seed prefix, followed by `league_id`.
pub const LEAGUE_SEED: &[u8] = b"league";

/// The 7-day league: the entry point, and it never closes (FR-002).
pub const LEAGUE_7D: u8 = 0;
/// The 21-day league.
pub const LEAGUE_21D: u8 = 1;
/// From here up are test leagues: any timing, admin only, and only in builds
/// carrying the `test-leagues` feature (research R11, scope doc D-A).
pub const TEST_LEAGUE_MIN: u8 = 100;

/// Push-ups. `1` (negatives) and `2` (decline) are reserved layout for later
/// exercises and rejected by `create_league` in this build (H30, error 6053).
pub const EXERCISE_PUSHUP: u8 = 0;

/// Devnet day: 24 hours, boundary at 05:00 UTC (constitution VI).
pub const DEVNET_DAY_LENGTH: i64 = 86_400;
/// One cohort a week, per league.
pub const DEVNET_CADENCE: i64 = 604_800;
/// Joining and leaving close an hour before the start (FR-008).
pub const DEVNET_JOIN_LOCK: i64 = 3_600;
/// 25 reps a day, in both leagues.
pub const DEVNET_REPS_PER_DAY: u16 = 25;

/// Offset of Monday 05:00:00 UTC inside a week, in seconds.
///
/// Unix day 0 (1 Jan 1970) was a Thursday, so Monday is day 4 of the epoch
/// week, and the day itself turns over at 05:00 UTC (`BOUNDARY_OFFSET`,
/// constitution VI). Every real league's `first_start` lands exactly here.
pub const MONDAY_0500_UTC: i64 = 4 * DEVNET_DAY_LENGTH + crate::math::schedule::BOUNDARY_OFFSET;

/// Whether an instant is a Monday at 05:00:00 UTC — the only start a real
/// league may have (data-model §1.2). `rem_euclid` so instants before the
/// epoch answer the same way a positive one would.
pub fn is_monday_0500_utc(at: i64) -> bool {
    at.rem_euclid(DEVNET_CADENCE) == MONDAY_0500_UTC
}

#[account]
#[derive(InitSpace)]
pub struct LeagueConfig {
    /// `0` = 7-day, `1` = 21-day, `>= 100` = test league.
    pub league_id: u8,
    /// 7 or 21 on devnet.
    pub duration_days: u8,
    /// 25.
    pub reps_per_day: u16,
    /// `0` = push-ups; the reserved values never reach a cohort in this build.
    pub exercise: u8,
    /// Seconds in one cohort day.
    pub day_length: i64,
    /// A Monday 05:00:00 UTC. Cohort `i` starts `first_start + i * cadence`.
    pub first_start: i64,
    pub cadence: i64,
    pub join_lock: i64,
    /// The index the next `open_cohort` must use (R11, FR-007a).
    pub next_index: u32,
    pub bump: u8,
}

impl LeagueConfig {
    /// Whether this id belongs to a test league, which only exists in builds
    /// carrying the `test-leagues` feature.
    pub fn is_test_league(league_id: u8) -> bool {
        league_id >= TEST_LEAGUE_MIN
    }

    /// Whether this league runs the devnet timing every real cohort must use.
    /// `create_league` enforces it for every non-test league, so nothing but a
    /// test build can produce a cohort with a shorter day (error 6010).
    pub fn has_devnet_timing(&self) -> bool {
        self.day_length == DEVNET_DAY_LENGTH
            && self.cadence == DEVNET_CADENCE
            && self.join_lock == DEVNET_JOIN_LOCK
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn monday_at_five_is_the_only_start_a_real_league_may_have() {
        // 28 Sep 2026, 05:00:00 UTC — the first judged cohort, and what
        // `tests/helpers/harness.ts` calls FIRST_START.
        let first_start = 1_790_571_600;
        assert!(is_monday_0500_utc(first_start));
        assert!(is_monday_0500_utc(first_start + 2 * DEVNET_CADENCE));
        assert!(is_monday_0500_utc(first_start - 100 * DEVNET_CADENCE));

        assert!(!is_monday_0500_utc(first_start + 1), "a second late");
        assert!(!is_monday_0500_utc(first_start - 1), "a second early");
        assert!(!is_monday_0500_utc(first_start - 3_600), "Monday 04:00 UTC");
        assert!(
            !is_monday_0500_utc(first_start + DEVNET_DAY_LENGTH),
            "Tuesday"
        );
    }

    #[test]
    fn test_leagues_start_at_one_hundred() {
        assert!(!LeagueConfig::is_test_league(LEAGUE_7D));
        assert!(!LeagueConfig::is_test_league(LEAGUE_21D));
        assert!(!LeagueConfig::is_test_league(99));
        assert!(LeagueConfig::is_test_league(TEST_LEAGUE_MIN));
    }
}
