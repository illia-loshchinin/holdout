package holdout.core

import com.solana.publickey.SolanaPublicKey

/**
 * Where a member stands in one cohort, derived from chain state and chain time
 * alone — the state column of contracts/android-app §5, so every screen the app
 * shows for a Holdout is chosen here and nowhere else (constitution IX).
 *
 * Like every figure in [Derive], a breach is read from the deadline, not from
 * whether the crank has recorded it yet (FR-038a): one second after a missed
 * deadline the member is [Forfeited], recorded or not.
 */
sealed interface Stage {
    /** Not in this cohort: the ladder offers it while it is `Open`. */
    data object NotJoined : Stage

    /** Joined and joining still open: the lobby, with leave (US-2). */
    data object Lobby : Stage

    /** Joined, joining closed, not started: the locked lobby, no actions (ES-12a). */
    data object LobbyLocked : Stage

    /**
     * Running and standing. [dayIndex] is today's cohort day; [todayDone] is
     * whether it is already on chain.
     */
    data class Active(val dayIndex: Int, val todayDone: Boolean) : Stage

    /** Missed a deadline — recorded by the crank or not yet (US-4). */
    data class Forfeited(val daysLasted: Int, val recorded: Boolean) : Stage

    /** Finished every day; settlement has not finalised yet (contracts §5). */
    data object Settling : Stage

    /**
     * Finished and settled: [amount] = stake + the tier's share + any comeback
     * refund is claimable, forever (FR-035, FR-037e). [comeback] is `0` for a
     * finisher who was owed none.
     */
    data class ReadyToClaim(val amount: ULong, val stake: ULong, val share: ULong, val comeback: ULong = 0uL) : Stage

    data class Claimed(val amount: ULong) : Stage
}

object Journey {
    /** The stage of [member] — null when the wallet holds no membership — in [c] at [now]. */
    fun stage(c: CohortSnapshot, member: MembershipSnapshot?, now: Long): Stage {
        if (member == null) return Stage.NotJoined
        val status = Derive.status(c, now)
        val payout = payoutOf(c, member)
        return when (member.state) {
            MembershipSnapshot.State.Claimed -> Stage.Claimed(payout)
            MembershipSnapshot.State.Forfeited -> Stage.Forfeited(member.daysCompleted, recorded = true)
            MembershipSnapshot.State.Finished ->
                if (c.settled) {
                    val comeback = Derive.comebackRefund(member, c) ?: 0uL
                    Stage.ReadyToClaim(payout, member.stake, payout - member.stake - comeback, comeback)
                } else {
                    Stage.Settling
                }
            MembershipSnapshot.State.Joined ->
                when (status) {
                    CohortStatus.Open -> Stage.Lobby
                    CohortStatus.Locked -> Stage.LobbyLocked
                    CohortStatus.Running ->
                        if (Derive.inBreach(member, c, now)) {
                            Stage.Forfeited(member.daysCompleted, recorded = false)
                        } else {
                            val today = Derive.dayIndex(c, now)
                            Stage.Active(today, todayDone = member.daysCompleted > today)
                        }
                    // Counting has not reached this member yet: the days on chain decide.
                    CohortStatus.Settling, CohortStatus.Settled ->
                        if (member.daysCompleted >= c.durationDays) Stage.Settling else Stage.Forfeited(member.daysCompleted, recorded = false)
                }
        }
    }

    /**
     * `stake + share_per_tier[tier] + comeback_refund` — what `claim_payout`
     * pays (FR-034, FR-037e), meaningful once the cohort is settled.
     */
    fun payoutOf(c: CohortSnapshot, member: MembershipSnapshot): ULong = member.stake + (c.sharePerTier.getOrNull(member.tier) ?: 0uL) + (Derive.comebackRefund(member, c) ?: 0uL)

    /**
     * The cohort a member's next join must name as its running one: the
     * profile's running slot as the program would normalise it (see
     * `Instructions.joinCohort`).
     */
    fun runningCohortFor(profile: ProfileSnapshot?, now: Long): SolanaPublicKey? = profile?.normalised(now)?.running?.cohort

    /**
     * Whether a wallet may take a place in [c] at [now], as far as the app can
     * tell before asking the program: joining open, no lobby place held, and
     * no running cohort ending after [c] starts (FR-006a, FR-006b). The
     * program re-checks all of it.
     */
    fun joinBlocker(c: CohortSnapshot, profile: ProfileSnapshot?, runningCohortEnd: Long?, now: Long): JoinBlocker? {
        if (Derive.status(c, now) != CohortStatus.Open) return JoinBlocker.JoiningClosed
        val p = profile?.normalised(now) ?: return null
        if (p.pending != null) return JoinBlocker.LobbyPlaceTaken
        if (runningCohortEnd != null && runningCohortEnd > now && c.start < runningCohortEnd) return JoinBlocker.Overlaps
        return null
    }

    enum class JoinBlocker { JoiningClosed, LobbyPlaceTaken, Overlaps }
}
