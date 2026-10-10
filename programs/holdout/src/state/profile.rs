//! `Profile` (data-model §1.5). Task T024.
//!
//! One per wallet: the permanent unlock, per-league progress, and the two
//! obligation slots that keep two daily obligations from ever overlapping
//! (INV-4). Nothing here is written by a client — only by the instructions.

use anchor_lang::prelude::*;

/// PDA seed prefix, followed by the wallet.
pub const PROFILE_SEED: &[u8] = b"profile";

/// Progress slots: the 7-day league, the 21-day league, and one shared by every
/// test league. The test slot exists in **both** builds on purpose — the layout
/// must be identical whether or not `test-leagues` is compiled in, or the
/// rehearsal and judged deployments would disagree about this account
/// (constitution V, research R11).
pub const LEAGUE_SLOTS: usize = 3;
/// The slot every `league_id >= 100` shares.
pub const TEST_LEAGUE_SLOT: usize = 2;

/// `last_faucet_day` while a wallet has never claimed.
pub const NEVER_CLAIMED: i64 = -1;

/// One league's record. `cleared` and `forfeited` are plain counters, so the
/// order automation runs in cannot change them; the two streak figures are
/// written only by `settle_count`, in cohort order (FR-061a, INV-9).
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, Default, InitSpace)]
pub struct LeagueProgress {
    pub cleared: u16,
    pub forfeited: u16,
    pub current_streak: u16,
    pub longest_streak: u16,
    /// The instant of the member's most recent outcome — a cohort's `end` for a
    /// clear, the missed deadline for a forfeit — and never the time the
    /// transaction landed, so no caller's timing can reorder two members tied on
    /// a leaderboard (FR-060b). Written through [`LeagueProgress::touch`].
    pub last_changed: i64,
    /// The stake of the latest **counted** forfeit in this league, while it can
    /// still earn a comeback; 0 once used (FR-037e). Written only by
    /// `settle_count`, so in cohort order like the streak (INV-9).
    pub comeback_stake: u64,
    /// That forfeit's missed deadline.
    pub comeback_from: i64,
    /// The last instant a cohort may start and still be its comeback.
    pub comeback_until: i64,
}

impl LeagueProgress {
    /// Record the instant of an outcome. Monotonic: an older outcome processed
    /// late cannot move the figure backwards, which is what keeps it independent
    /// of page order (FR-060b, INV-9).
    pub fn touch(&mut self, outcome_at: i64) {
        if outcome_at > self.last_changed {
            self.last_changed = outcome_at;
        }
    }

    /// A cleared cohort: one more clear, and the streak grows.
    pub fn record_clear(&mut self, outcome_at: i64) {
        self.cleared = self.cleared.saturating_add(1);
        self.current_streak = self.current_streak.saturating_add(1);
        self.longest_streak = self.longest_streak.max(self.current_streak);
        self.touch(outcome_at);
    }

    /// A forfeited cohort, counted at settlement: the streak breaks. The
    /// `forfeited` counter rose earlier, when the forfeit was recorded.
    pub fn record_forfeit_counted(&mut self, outcome_at: i64) {
        self.current_streak = 0;
        self.touch(outcome_at);
    }

    /// A forfeit counted at settlement may be made good by finishing a later
    /// cohort of the league that starts after `missed_at` and by `until`
    /// (FR-037e). Only the latest counted forfeit is kept: counting runs in
    /// cohort order, so "latest" never depends on the crank (INV-9).
    pub fn open_comeback(&mut self, forfeited_stake: u64, missed_at: i64, until: i64) {
        self.comeback_stake = forfeited_stake;
        self.comeback_from = missed_at;
        self.comeback_until = until;
    }

    /// The forfeited stake a finisher of a cohort starting at `cohort_start`
    /// makes good, if that cohort is the comeback — and the record is used up.
    pub fn take_comeback(&mut self, cohort_start: i64) -> Option<u64> {
        let open = self.comeback_stake > 0
            && cohort_start > self.comeback_from
            && cohort_start <= self.comeback_until;
        if !open {
            return None;
        }
        let stake = self.comeback_stake;
        self.comeback_stake = 0;
        Some(stake)
    }

    /// Cohorts played — cleared plus forfeited (FR-060a).
    pub fn played(&self) -> u16 {
        self.cleared.saturating_add(self.forfeited)
    }
}

/// A membership in a cohort that has started. Default = none.
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, Default, InitSpace)]
pub struct RunningSlot {
    pub cohort: Pubkey,
    pub end: i64,
}

/// A lobby place in a cohort that has not started. Default = none (FR-006a).
///
/// `end` is the cohort's own end, copied in as the place is taken. It is dead
/// weight until the place is promoted, and then it is the whole point: the
/// running slot's `end` is what stops a second obligation overlapping this one
/// (FR-006b, INV-4), and the cohort account is not passed to every instruction
/// that normalises the slots, so the end has to travel with the place.
#[derive(AnchorSerialize, AnchorDeserialize, Clone, Copy, Debug, Default, InitSpace)]
pub struct PendingSlot {
    pub cohort: Pubkey,
    pub start: i64,
    pub end: i64,
}

impl RunningSlot {
    pub fn is_none(&self) -> bool {
        self.cohort == Pubkey::default()
    }

    pub fn clear(&mut self) {
        *self = Self::default();
    }
}

impl PendingSlot {
    pub fn is_none(&self) -> bool {
        self.cohort == Pubkey::default()
    }

    pub fn clear(&mut self) {
        *self = Self::default();
    }
}

#[account]
#[derive(InitSpace)]
pub struct Profile {
    pub wallet: Pubkey,
    pub leagues: [LeagueProgress; LEAGUE_SLOTS],
    pub running: RunningSlot,
    pub pending: PendingSlot,
    /// Global day index of the last faucet claim; `-1` = never (R13).
    pub last_faucet_day: i64,
    pub bump: u8,
}

impl Profile {
    /// Which progress slot a league writes to. Real leagues have their own; every
    /// test league shares the third, so test runs never touch judged progress.
    pub fn slot_of(league_id: u8) -> usize {
        if (league_id as usize) < TEST_LEAGUE_SLOT {
            league_id as usize
        } else {
            TEST_LEAGUE_SLOT
        }
    }

    pub fn progress(&self, league_id: u8) -> &LeagueProgress {
        &self.leagues[Self::slot_of(league_id)]
    }

    pub fn progress_mut(&mut self, league_id: u8) -> &mut LeagueProgress {
        &mut self.leagues[Self::slot_of(league_id)]
    }

    /// Slot normalisation: a lobby place whose cohort has started becomes the
    /// running one. Run at the start of every member instruction and wherever a
    /// forfeit or a count touches the profile (data-model §1.5).
    pub fn normalise_slots(&mut self, now: i64) {
        if !self.pending.is_none() && self.pending.start <= now {
            self.running = RunningSlot {
                cohort: self.pending.cohort,
                end: self.pending.end,
            };
            self.pending.clear();
        }
    }

    /// Clear the running slot when it is this cohort — done as a forfeit is
    /// recorded or a finisher counted, so the member is free to join again.
    pub fn release_running(&mut self, cohort: &Pubkey) {
        if self.running.cohort == *cohort {
            self.running.clear();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn profile() -> Profile {
        Profile {
            wallet: Pubkey::default(),
            leagues: [LeagueProgress::default(); LEAGUE_SLOTS],
            running: RunningSlot::default(),
            pending: PendingSlot::default(),
            last_faucet_day: NEVER_CLAIMED,
            bump: 0,
        }
    }

    #[test]
    fn a_streak_is_consecutive_clears_and_a_forfeit_ends_it() {
        let mut p = LeagueProgress::default();
        p.record_clear(100);
        p.record_clear(200);
        assert_eq!((p.cleared, p.current_streak, p.longest_streak), (2, 2, 2));
        p.forfeited += 1;
        p.record_forfeit_counted(300);
        assert_eq!(
            (p.current_streak, p.longest_streak),
            (0, 2),
            "the longest never decreases"
        );
        p.record_clear(400);
        assert_eq!((p.current_streak, p.longest_streak, p.played()), (1, 2, 4));
    }

    #[test]
    fn a_counted_forfeit_opens_a_comeback_that_a_later_cohort_in_its_window_uses_once() {
        let mut p = LeagueProgress::default();
        // Missed at 1_000, window of 14 days of 100 s: cohorts starting by 2_400.
        p.open_comeback(250, 1_000, 2_400);
        assert_eq!(
            p.take_comeback(1_000),
            None,
            "a cohort that started before the breach is not a comeback"
        );
        assert_eq!(
            p.take_comeback(2_401),
            None,
            "nor one that started after the window"
        );
        assert_eq!(
            p.take_comeback(2_400),
            Some(250),
            "the last second of the window still counts"
        );
        assert_eq!(p.take_comeback(2_400), None, "used up");
    }

    #[test]
    fn only_the_latest_counted_forfeit_can_earn_a_comeback() {
        let mut p = LeagueProgress::default();
        p.open_comeback(250, 1_000, 2_400);
        p.open_comeback(500, 3_000, 4_400);
        assert_eq!(
            p.take_comeback(2_000),
            None,
            "the earlier forfeit's window was replaced"
        );
        assert_eq!(p.take_comeback(3_500), Some(500));
    }

    #[test]
    fn the_outcome_instant_never_moves_backwards() {
        let mut p = LeagueProgress::default();
        p.record_clear(500);
        assert_eq!(p.last_changed, 500);
        // An earlier cohort settled late: its instant must not overwrite the
        // later one, or the leaderboard tie-break would depend on crank order.
        p.record_forfeit_counted(200);
        assert_eq!(p.last_changed, 500);
    }

    #[test]
    fn test_leagues_share_the_third_slot_and_leave_judged_progress_alone() {
        assert_eq!(Profile::slot_of(0), 0);
        assert_eq!(Profile::slot_of(1), 1);
        assert_eq!(Profile::slot_of(100), TEST_LEAGUE_SLOT);
        assert_eq!(Profile::slot_of(255), TEST_LEAGUE_SLOT);

        let mut p = profile();
        p.progress_mut(100).record_clear(1);
        assert_eq!(
            p.progress(0).cleared,
            0,
            "a test cohort never touches league 0"
        );
        assert_eq!(p.progress(100).cleared, 1);
    }

    #[test]
    fn a_forfeit_frees_the_place_of_a_member_who_never_sat_a_day() {
        // The member staked and never showed up, so nothing ever normalised
        // their slots: the cohort is still in `pending`, never in `running`.
        // `release_running` alone cannot reach it there, and a place left on the
        // profile would answer `LobbyPlaceTaken` for every later join — a
        // forfeit ending the member's place in the league (FR-002).
        let cohort = Pubkey::new_unique();
        let start = 1_790_571_600;
        let mut p = profile();
        p.pending = PendingSlot {
            cohort,
            start,
            end: start + 7 * 86_400,
        };

        let now = start + 86_400;
        p.release_running(&cohort);
        assert_eq!(p.pending.cohort, cohort, "release alone leaves it stranded");

        p.normalise_slots(now);
        p.release_running(&cohort);
        assert!(p.pending.is_none(), "the place is free again");
        assert!(p.running.is_none());
    }

    #[test]
    fn a_lobby_place_becomes_the_running_one_at_its_start() {
        let mut p = profile();
        let cohort = Pubkey::new_unique();
        p.pending = PendingSlot {
            cohort,
            start: 1_000,
            end: 8_000,
        };

        p.normalise_slots(999);
        assert!(!p.pending.is_none(), "not yet");

        p.normalise_slots(1_000);
        assert!(p.pending.is_none());
        assert_eq!(p.running.cohort, cohort);
        assert_eq!(
            p.running.end, 8_000,
            "the obligation the promotion carries over — a zero here would let the \
             next join start inside this cohort (FR-006b, INV-4)"
        );

        p.release_running(&Pubkey::new_unique());
        assert_eq!(
            p.running.cohort, cohort,
            "another cohort's outcome leaves it alone"
        );
        p.release_running(&cohort);
        assert!(p.running.is_none());
    }
}
