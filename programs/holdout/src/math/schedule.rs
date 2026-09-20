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
    start + (i64::from(day_index) + 1) * day_length - 1
}

/// The global day index used by the streak and the faucet:
/// `floor((now - 18,000) / 86,400)` — it turns over at 05:00 UTC (OD-6).
pub fn global_day_index(now: i64) -> i64 {
    // div_euclid, not `/`: integer division truncates toward zero, which would
    // put the instants just before the epoch boundary in the wrong day.
    (now - BOUNDARY_OFFSET).div_euclid(DAY_LENGTH)
}

/// A cohort's join cutoff: `start - join_lock` (FR-008).
pub fn join_cutoff(start: i64, join_lock: i64) -> i64 {
    start - join_lock
}

/// Start of cohort `index` in a league: `first_start + index * cadence` (R11).
pub fn cohort_start(first_start: i64, cadence: i64, index: u32) -> i64 {
    first_start + i64::from(index) * cadence
}

/// How many whole cohort days have **elapsed** at `now`, capped at the
/// cohort's duration: `floor((now − start) ÷ day_length)`, and `0` before the
/// start. While the cohort is `Running` this is its current zero-based day
/// index — the `day_index(now)` that `submit_day` matches against
/// (data-model §1.3).
///
/// Elapsed days are not a completion ceiling: at the cohort's first second
/// nothing has elapsed, yet a member who submits day 0 there legitimately
/// holds `days_completed == 1`. The INV-3 ceiling is [`max_days_completed`].
pub fn days_elapsed(start: i64, day_length: i64, duration_days: u8, now: i64) -> u8 {
    if now < start {
        return 0;
    }
    let elapsed = (now - start) / day_length;
    elapsed.min(i64::from(duration_days)) as u8
}

/// The most completed days a member of this cohort may hold at `now` — the
/// INV-3 ceiling: `0` before the start, otherwise
/// `min(days_elapsed + 1, duration_days)`, because the day in progress can
/// itself be completed at any second of it.
pub fn max_days_completed(start: i64, day_length: i64, duration_days: u8, now: i64) -> u8 {
    if now < start {
        return 0;
    }
    // The day in progress may itself be completed, hence the + 1 (INV-3).
    (days_elapsed(start, day_length, duration_days, now) + 1).min(duration_days)
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Monday 28 September 2026, 05:00:00 UTC — the first judged cohort start.
    const FIRST_START: i64 = 1_790_571_600;

    #[test]
    fn deadlines_land_one_second_before_the_next_boundary() {
        assert_eq!(
            deadline_of(FIRST_START, DAY_LENGTH, 0),
            FIRST_START + DAY_LENGTH - 1
        );
        assert_eq!(
            deadline_of(FIRST_START, DAY_LENGTH, 6),
            FIRST_START + 7 * DAY_LENGTH - 1
        );
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
        assert_eq!(
            global_day_index(FIRST_START + DAY_LENGTH - 1),
            day,
            "same day one second before"
        );
        assert_eq!(
            global_day_index(FIRST_START + DAY_LENGTH),
            day + 1,
            "next day at the boundary"
        );
        assert_eq!(
            global_day_index(FIRST_START - 1),
            day - 1,
            "previous day one second before the start"
        );
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
        assert_eq!(
            cohort_start(FIRST_START, cadence, 6),
            FIRST_START + 6 * cadence
        );
    }

    #[test]
    fn elapsed_days_are_the_current_day_index_and_stop_at_the_duration() {
        assert_eq!(
            days_elapsed(FIRST_START, DAY_LENGTH, 7, FIRST_START),
            0,
            "day 0 at the start"
        );
        assert_eq!(
            days_elapsed(FIRST_START, DAY_LENGTH, 7, FIRST_START + DAY_LENGTH - 1),
            0
        );
        assert_eq!(
            days_elapsed(FIRST_START, DAY_LENGTH, 7, FIRST_START + DAY_LENGTH),
            1
        );
        assert_eq!(
            days_elapsed(FIRST_START, DAY_LENGTH, 7, FIRST_START + 99 * DAY_LENGTH),
            7
        );
        assert_eq!(
            days_elapsed(FIRST_START, DAY_LENGTH, 7, FIRST_START - 1),
            0,
            "before the start"
        );
    }

    #[test]
    fn the_first_day_can_be_completed_on_the_first_day() {
        // The ceiling INV-3 asserts, not elapsed time: a submission at the very
        // first second of day 0 is valid and leaves `days_completed == 1`.
        assert_eq!(
            max_days_completed(FIRST_START, DAY_LENGTH, 7, FIRST_START),
            1
        );
        assert_eq!(
            max_days_completed(FIRST_START, DAY_LENGTH, 7, FIRST_START + DAY_LENGTH - 1),
            1
        );
        assert_eq!(
            max_days_completed(FIRST_START, DAY_LENGTH, 7, FIRST_START + DAY_LENGTH),
            2
        );
    }

    #[test]
    fn completed_days_are_capped_at_the_duration() {
        let end = FIRST_START + 7 * DAY_LENGTH;
        assert_eq!(
            max_days_completed(FIRST_START, DAY_LENGTH, 7, end - 1),
            7,
            "the final day"
        );
        assert_eq!(
            max_days_completed(FIRST_START, DAY_LENGTH, 7, end),
            7,
            "once it has ended"
        );
        assert_eq!(
            max_days_completed(FIRST_START, DAY_LENGTH, 7, FIRST_START + 99 * DAY_LENGTH),
            7
        );
        assert_eq!(
            max_days_completed(FIRST_START, DAY_LENGTH, 7, FIRST_START - 1),
            0,
            "nothing can be completed before the cohort starts"
        );
    }
}
