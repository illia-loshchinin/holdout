//! `create_league` (contracts/program-instructions §1, data-model §1.2).
//! Task T028.
//!
//! A real league is held to the devnet timing — a 24-hour day, one cohort a
//! week, joining closing an hour before a Monday 05:00 UTC start — so nothing
//! but a test build can produce a cohort with a shorter day. Test leagues
//! (`league_id >= 100`) exist only in builds carrying `test-leagues`, and even
//! there the feature widens the test leagues alone (research R11, H30).

use anchor_lang::prelude::*;

use crate::errors::HoldoutError;
use crate::math::schedule::BOUNDARY_OFFSET;
use crate::state::config::{Config, CONFIG_SEED};
use crate::state::league::{
    LeagueConfig, DEVNET_CADENCE, DEVNET_DAY_LENGTH, DEVNET_JOIN_LOCK, EXERCISE_PUSHUP, LEAGUE_SEED,
};

/// Monday 5 January 1970, 05:00:00 UTC — the first Monday start after the
/// epoch, which fell on a Thursday. Every valid `first_start` is this plus a
/// whole number of weeks.
const EPOCH_MONDAY_START: i64 = 4 * DEVNET_DAY_LENGTH + BOUNDARY_OFFSET;

/// Whether this build accepts test leagues. The judged program never does.
const TEST_LEAGUES: bool = cfg!(feature = "test-leagues");

#[derive(Accounts)]
#[instruction(league_id: u8)]
pub struct CreateLeague<'info> {
    #[account(mut)]
    pub admin: Signer<'info>,

    // Checked before `league` is created, since constraints run in field order.
    #[account(seeds = [CONFIG_SEED], bump = config.bump, has_one = admin @ HoldoutError::NotAdmin)]
    pub config: Box<Account<'info, Config>>,

    #[account(
        init,
        payer = admin,
        space = 8 + LeagueConfig::INIT_SPACE,
        seeds = [LEAGUE_SEED, &[league_id]],
        bump,
    )]
    pub league: Box<Account<'info, LeagueConfig>>,

    pub system_program: Program<'info, System>,
}

/// What a league may be, apart from who asks. Pure, so both builds' rules are
/// unit-tested in one run; the handler passes the build's own `TEST_LEAGUES`.
#[allow(clippy::too_many_arguments)]
pub fn check_league(
    league_id: u8,
    duration_days: u8,
    exercise: u8,
    first_start: i64,
    day_length: i64,
    cadence: i64,
    join_lock: i64,
    test_leagues: bool,
) -> Result<()> {
    // The reserved exercises are layout for later, not behaviour now (H30).
    require!(
        exercise == EXERCISE_PUSHUP,
        HoldoutError::ExerciseNotSupported
    );

    if LeagueConfig::is_test_league(league_id) {
        // Any timing, but only in a test build — and never one the schedule
        // maths would divide by zero or run backwards on.
        require!(test_leagues, HoldoutError::InvalidLeagueTiming);
        require!(
            duration_days > 0 && day_length > 0 && cadence > 0 && join_lock >= 0,
            HoldoutError::InvalidLeagueTiming
        );
        return Ok(());
    }

    require!(
        day_length == DEVNET_DAY_LENGTH
            && cadence == DEVNET_CADENCE
            && join_lock == DEVNET_JOIN_LOCK
            && (first_start - EPOCH_MONDAY_START).rem_euclid(DEVNET_CADENCE) == 0,
        HoldoutError::InvalidLeagueTiming
    );
    Ok(())
}

#[allow(clippy::too_many_arguments)]
pub fn handle_create_league(
    ctx: Context<CreateLeague>,
    league_id: u8,
    duration_days: u8,
    reps_per_day: u16,
    exercise: u8,
    first_start: i64,
    day_length: i64,
    cadence: i64,
    join_lock: i64,
) -> Result<()> {
    check_league(
        league_id,
        duration_days,
        exercise,
        first_start,
        day_length,
        cadence,
        join_lock,
        TEST_LEAGUES,
    )?;

    let league = &mut ctx.accounts.league;
    league.league_id = league_id;
    league.duration_days = duration_days;
    league.reps_per_day = reps_per_day;
    league.exercise = exercise;
    league.day_length = day_length;
    league.first_start = first_start;
    league.cadence = cadence;
    league.join_lock = join_lock;
    league.next_index = 0;
    league.bump = ctx.bumps.league;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::state::league::{LEAGUE_21D, LEAGUE_7D, TEST_LEAGUE_MIN};

    /// Monday 28 September 2026, 05:00:00 UTC — the first judged cohort start.
    const FIRST_START: i64 = 1_790_571_600;

    fn real(league_id: u8, first_start: i64, test_leagues: bool) -> Result<()> {
        check_league(
            league_id,
            7,
            EXERCISE_PUSHUP,
            first_start,
            DEVNET_DAY_LENGTH,
            DEVNET_CADENCE,
            DEVNET_JOIN_LOCK,
            test_leagues,
        )
    }

    fn compressed(league_id: u8, exercise: u8, test_leagues: bool) -> Result<()> {
        check_league(
            league_id,
            3,
            exercise,
            FIRST_START,
            60,
            120,
            10,
            test_leagues,
        )
    }

    fn is(result: Result<()>, expected: HoldoutError) -> bool {
        result == Err(expected.into())
    }

    #[test]
    fn the_epoch_anchor_is_a_monday_at_five() {
        assert_eq!(
            (FIRST_START - EPOCH_MONDAY_START).rem_euclid(DEVNET_CADENCE),
            0,
            "28 September 2026 05:00 UTC is a Monday start"
        );
    }

    #[test]
    fn a_real_league_starts_on_a_monday_at_five() {
        for build in [false, true] {
            assert!(real(LEAGUE_7D, FIRST_START, build).is_ok());
            assert!(real(LEAGUE_21D, FIRST_START + 2 * DEVNET_CADENCE, build).is_ok());
            // Before the epoch too: rem_euclid, not `%`, keeps the sign honest.
            assert!(real(LEAGUE_7D, EPOCH_MONDAY_START - DEVNET_CADENCE, build).is_ok());

            for off in [
                1,
                -1,
                DEVNET_JOIN_LOCK,
                -DEVNET_JOIN_LOCK,
                DEVNET_DAY_LENGTH,
            ] {
                assert!(
                    is(
                        real(LEAGUE_7D, FIRST_START + off, build),
                        HoldoutError::InvalidLeagueTiming
                    ),
                    "{off} seconds off Monday 05:00 is refused"
                );
            }
        }
    }

    #[test]
    fn a_real_league_keeps_the_devnet_timing_in_either_build() {
        for build in [false, true] {
            assert!(is(
                compressed(LEAGUE_7D, EXERCISE_PUSHUP, build),
                HoldoutError::InvalidLeagueTiming
            ));
        }
    }

    #[test]
    fn a_test_league_needs_a_test_build() {
        assert!(is(
            compressed(TEST_LEAGUE_MIN, EXERCISE_PUSHUP, false),
            HoldoutError::InvalidLeagueTiming
        ));
        assert!(is(
            real(TEST_LEAGUE_MIN, FIRST_START, false),
            HoldoutError::InvalidLeagueTiming
        ));
        assert!(compressed(TEST_LEAGUE_MIN, EXERCISE_PUSHUP, true).is_ok());
        assert!(compressed(u8::MAX, EXERCISE_PUSHUP, true).is_ok());
    }

    #[test]
    fn a_test_league_still_needs_a_timing_the_maths_can_run() {
        for (duration, day, cadence, lock) in [
            (0, 60, 120, 10),
            (3, 0, 120, 10),
            (3, 60, 0, 10),
            (3, 60, 120, -1),
        ] {
            assert!(is(
                check_league(
                    TEST_LEAGUE_MIN,
                    duration,
                    EXERCISE_PUSHUP,
                    FIRST_START,
                    day,
                    cadence,
                    lock,
                    true
                ),
                HoldoutError::InvalidLeagueTiming
            ));
        }
    }

    #[test]
    fn only_push_ups_in_this_build() {
        for exercise in [1, 2, 3, u8::MAX] {
            for build in [false, true] {
                let real_league = check_league(
                    LEAGUE_7D,
                    7,
                    exercise,
                    FIRST_START,
                    DEVNET_DAY_LENGTH,
                    DEVNET_CADENCE,
                    DEVNET_JOIN_LOCK,
                    build,
                );
                assert!(is(real_league, HoldoutError::ExerciseNotSupported));
            }
            assert!(is(
                compressed(TEST_LEAGUE_MIN, exercise, true),
                HoldoutError::ExerciseNotSupported
            ));
        }
    }
}
