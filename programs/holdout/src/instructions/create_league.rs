//! `create_league` (contracts/program-instructions §1). Task T028.
//!
//! A league is the schedule and the obligation; a cohort is one run of it. The
//! admin creates the two judged leagues once, and every cohort copies these
//! parameters at creation so a running cohort's rules can never move under its
//! members (constitution V).
//!
//! Two guards, in this order:
//!
//!  1. `exercise == 0`. `1` (negatives) and `2` (decline) are reserved layout
//!     for later exercises, not behaviour in this build, and are rejected here
//!     rather than stored and ignored (H30, `ExerciseNotSupported` 6053). The
//!     check runs first, so a `test-leagues` build refuses a reserved exercise
//!     on a test league too.
//!  2. Timing. A real league (`league_id < 100`) keeps the devnet timing
//!     exactly: a 24-hour day, a weekly cadence, an hour's join lock and a
//!     Monday 05:00 UTC first start. A test league (`league_id >= 100`) takes
//!     any timing, and **only in builds carrying the `test-leagues` feature** —
//!     LiteSVM and the separate rehearsal deployment, never the judged program
//!     (research R11, scope doc D-A). Without the feature the id alone is
//!     refused, whatever timing came with it.

use anchor_lang::prelude::*;

use crate::{
    errors::HoldoutError,
    state::{
        config::{Config, CONFIG_SEED},
        league::{
            is_monday_0500_utc, LeagueConfig, DEVNET_CADENCE, DEVNET_DAY_LENGTH, DEVNET_JOIN_LOCK,
            EXERCISE_PUSHUP, LEAGUE_21D, LEAGUE_7D, LEAGUE_SEED,
        },
    },
};

#[derive(Accounts)]
#[instruction(league_id: u8)]
pub struct CreateLeague<'info> {
    #[account(mut, constraint = admin.key() == config.admin @ HoldoutError::NotAdmin)]
    pub admin: Signer<'info>,

    #[account(seeds = [CONFIG_SEED], bump = config.bump)]
    pub config: Account<'info, Config>,

    #[account(
        init,
        payer = admin,
        space = 8 + LeagueConfig::INIT_SPACE,
        seeds = [LEAGUE_SEED, &[league_id]],
        bump,
    )]
    pub league: Account<'info, LeagueConfig>,

    pub system_program: Program<'info, System>,
}

/// Whether this build may create a league with this id at all, and with this
/// timing. The judged program is compiled without `test-leagues`, so the whole
/// test-league branch is absent from the binary the judges run — the feature is
/// what widens it, not a flag anyone can flip on chain (research R11).
fn timing_ok(
    league_id: u8,
    duration_days: u8,
    first_start: i64,
    day_length: i64,
    cadence: i64,
    join_lock: i64,
) -> bool {
    if LeagueConfig::is_test_league(league_id) {
        // A test league's whole point is a compressed day, so its clock is
        // free — but never a zero duration, for the reason below.
        return cfg!(feature = "test-leagues") && duration_days > 0;
    }
    // The duration belongs to the timing: it is what fixes the league's `end`,
    // and `open_cohort` copies it into every cohort permanently, so a wrong
    // figure here can never be corrected. The two judged leagues are 7 and 21
    // days and nothing else (data-model §1.2).
    //
    // Zero is the dangerous one. `settle_count` calls a member finished when
    // `days_completed == duration_days`, so in a zero-day league every member
    // who never sat a single day is a finisher: an immediate clear, a payout,
    // and the 21-day unlock, without a push-up. Every other id is refused
    // outright — ids 2 to 99 are not test leagues, yet `Profile::slot_of`
    // sends them to the test progress slot, so a "real" league there would
    // write its judged progress where test runs live.
    let expected = match league_id {
        LEAGUE_7D => 7,
        LEAGUE_21D => 21,
        _ => return false,
    };
    duration_days == expected
        && day_length == DEVNET_DAY_LENGTH
        && cadence == DEVNET_CADENCE
        && join_lock == DEVNET_JOIN_LOCK
        && is_monday_0500_utc(first_start)
}

#[allow(clippy::too_many_arguments)]
pub(crate) fn handler(
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
    require!(
        exercise == EXERCISE_PUSHUP,
        HoldoutError::ExerciseNotSupported
    );
    require!(
        timing_ok(
            league_id,
            duration_days,
            first_start,
            day_length,
            cadence,
            join_lock
        ),
        HoldoutError::InvalidLeagueTiming
    );

    let league = &mut ctx.accounts.league;
    league.league_id = league_id;
    league.duration_days = duration_days;
    league.reps_per_day = reps_per_day;
    league.exercise = exercise;
    league.day_length = day_length;
    league.first_start = first_start;
    league.cadence = cadence;
    league.join_lock = join_lock;
    // No cohort yet; `open_cohort` is permissionless and must use this index.
    league.next_index = 0;
    league.bump = ctx.bumps.league;

    Ok(())
}
