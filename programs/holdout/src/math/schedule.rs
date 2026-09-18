//! Cohort schedule arithmetic — pure, and the only place a deadline is defined.
//!
//! One clock (constitution VI): every instant below comes from chain time and
//! the cohort's own parameters. No timezone, offset or locale exists anywhere.
//! Tests were written before the logic (constitution VII); they fail until T019.

/// The 05:00 UTC boundary, as seconds past midnight (OD-1).
pub const BOUNDARY_OFFSET: i64 = 18_000;

/// A cohort day in seconds on devnet. Test leagues override it (scope doc D-A).
pub const DAY_LENGTH: i64 = 86_400;

/// Joining and leaving close one hour before the start (FR-008).
pub const JOIN_LOCK: i64 = 3_600;

/// Last instant at which day `k` (zero-based) can still be completed:
/// `start + (k + 1) * day_length - 1` (data-model §1.3).
pub fn deadline_of(start: i64, day_length: i64, day_index: u8) -> i64 {
    let _ = (start, day_length, day_index);
    todo!("T019")
}

/// The global day index used by the streak and the faucet:
/// `floor((now - 18,000) / 86,400)` — it turns over at 05:00 UTC (OD-6).
pub fn global_day_index(now: i64) -> i64 {
    let _ = now;
    todo!("T019")
}

/// A cohort's join cutoff: `start - join_lock` (FR-008).
pub fn join_cutoff(start: i64, join_lock: i64) -> i64 {
    let _ = (start, join_lock);
    todo!("T019")
}

/// Start of cohort `index` in a league: `first_start + index * cadence` (R11).
pub fn cohort_start(first_start: i64, cadence: i64, index: u32) -> i64 {
    let _ = (first_start, cadence, index);
    todo!("T019")
}

/// How many days of a cohort have been reached at `now`, capped at its
/// duration. A member's `days_completed` may never exceed this (INV-3).
pub fn days_reached(start: i64, day_length: i64, duration_days: u8, now: i64) -> u8 {
    let _ = (start, day_length, duration_days, now);
    todo!("T019")
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Monday 28 September 2026, 05:00:00 UTC — the first judged cohort start.
    const FIRST_START: i64 = 1_790_571_600;

    #[test]
    fn deadlines_land_one_second_before_the_next_boundary() {
        assert_eq!(deadline_of(FIRST_START, DAY_LENGTH, 0), FIRST_START + DAY_LENGTH - 1);
        assert_eq!(deadline_of(FIRST_START, DAY_LENGTH, 6), FIRST_START + 7 * DAY_LENGTH - 1);
        // A 7-day cohort's final deadline is one second before the next Monday.
        assert_eq!(
            deadline_of(FIRST_START, DAY_LENGTH, 6) + 1,
            cohort_start(FIRST_START, 7 * DAY_LENGTH, 1)
        );
    }

    #[test]
    fn a_test_league_keeps_the_same_shape_with_a_shorter_day() {
        assert_eq!(deadline_of(1_000, 600, 0), 1_599);
        assert_eq!(deadline_of(1_000, 600, 6), 5_199);
    }

    #[test]
    fn the_global_day_turns_over_at_five_utc() {
        let day = global_day_index(FIRST_START);
        assert_eq!(global_day_index(FIRST_START + DAY_LENGTH - 1), day, "same day one second before");
        assert_eq!(global_day_index(FIRST_START + DAY_LENGTH), day + 1, "next day at the boundary");
        assert_eq!(global_day_index(FIRST_START - 1), day - 1, "previous day one second before the start");
    }

    #[test]
    fn joining_closes_an_hour_before_the_start() {
        assert_eq!(join_cutoff(FIRST_START, JOIN_LOCK), FIRST_START - 3_600);
    }

    #[test]
    fn cohorts_start_weekly() {
        let cadence = 7 * DAY_LENGTH;
        assert_eq!(cohort_start(FIRST_START, cadence, 0), FIRST_START);
        assert_eq!(cohort_start(FIRST_START, cadence, 1), FIRST_START + cadence);
        assert_eq!(cohort_start(FIRST_START, cadence, 6), FIRST_START + 6 * cadence);
    }

    #[test]
    fn days_reached_is_capped_at_the_duration() {
        assert_eq!(days_reached(FIRST_START, DAY_LENGTH, 7, FIRST_START), 0);
        assert_eq!(days_reached(FIRST_START, DAY_LENGTH, 7, FIRST_START + DAY_LENGTH), 1);
        assert_eq!(days_reached(FIRST_START, DAY_LENGTH, 7, FIRST_START + 99 * DAY_LENGTH), 7);
    }
}
