package holdout.app.home

import holdout.app.chain.Holdout
import holdout.app.nav.NavEvent
import holdout.app.nav.Sitting
import holdout.app.ui.Format
import holdout.core.CohortSnapshot
import holdout.core.Derive
import holdout.core.LeagueParams
import holdout.core.Stage
import holdout.repcounter.Stakes

/** Where the member's day stands: what is left to do, a finished day not yet on the chain, or on the chain. */
enum class DayPhase {
    /** Reps still to do (or being done): Start, Keep going. */
    ToDo,

    /** The target is done on this phone and not on the chain: waiting for the network, sending, or a tap to submit. */
    Unsent,

    /** Unsent, and the wallet has no SOL for the fee: the way out is the faucet. */
    ShortOfSol,

    /** The day is on the chain. */
    OnChain,
}

/** The colour a day's title takes, resolved by the screen from theme colours. */
enum class DayTone { Plain, Accent, Success, Warning }

/** What the day button does; [None] is the one that only says the wallet is out. */
enum class DayAction { Start, Submit, None }

/** The day's one button: its words and what a tap does. */
data class DayButton(val label: String, val action: DayAction, val enabled: Boolean = true, val busy: Boolean = false)

/**
 * A running Holdout's day for the member standing in it — one model for Today
 * (T2) and Standing's day strip, so the two never disagree about the button,
 * the deadline or whether the day is done (FR-024b, FR-038, FR-047, FR-069a/b).
 * Pure: the screens only draw it.
 */
data class DayState(
    val phase: DayPhase,
    /** The Holdout's day index, 0-based: what a submit sends. */
    val dayIndex: Int,
    /** `dayIndex + 1`, the number a member reads. */
    val day: Int,
    /** Push-ups the day asks for. */
    val target: Int,
    /** Reps banked on this phone toward it. */
    val banked: Int,
    val title: String,
    val tone: DayTone,
    val text: String?,
    /** The deadline, in seconds — only while the day is not on the chain; null once it is, so no countdown is drawn. */
    val timeLeft: Long?,
    /** Inside the last 10 minutes of the day, with reps still to do (FR-047). */
    val warning: Boolean,
    val button: DayButton?,
    /** The last chain read failed. */
    val offline: Boolean,
    /** The Holdout's final day. */
    val lastDay: Boolean,
    /** The hero's big number (FR-069a): what the day asks, what is left of it, or the target once it is done. */
    val number: String,
    /** The word under [number]: "push-ups", "to go", "still needed", "done", "on chain". */
    val unit: String,
    val numberTone: DayTone,
    /** The hero's one sub line, in two lines at most; its words carry the state, so the colour is never alone. */
    val sub: String,
    val subTone: DayTone = DayTone.Plain,
    /** Once the day is on chain, the tonal pill that stands where the button was: when the next day opens, or that the end settles. */
    val pill: String? = null,
) {
    /** Reps still owed today. */
    val owed: Int get() = (target - banked).coerceAtLeast(0)

    companion object {
        const val BLOCK = 10

        /** After the last day is on the chain: the next day never opens, the end settles (FR-035a). */
        const val LAST_DAY = "That was the last day. It settles soon after the end, then your claim is ready."

        /** The sub line before the first rep: a Holdout day is also today's Daily 25 (FR-050). */
        const val COUNTS_AS_DAILY = "Counts as your Daily 25 too."

        const val NO_FEE = "Not enough devnet SOL for the fee. Test tokens include some."

        fun of(
            c: CohortSnapshot,
            stage: Stage.Active,
            now: Long,
            banked: Int,
            pendingDay: Int?,
            autoSendTried: Boolean,
            offline: Boolean,
            busy: Boolean,
            canPayFees: Boolean,
            /** A send of this day went out and was not confirmed: it may land yet, so Submit stays shut until a chain read settles it (FR-070a). */
            mayStillLand: Boolean = false,
        ): DayState {
            val reps = c.repsPerDay
            val day = stage.dayIndex + 1
            val timeLeft = Derive.timeLeftToday(c, now)
            val lastDay = day >= c.durationDays
            val waiting = pendingDay != null && pendingDay == stage.dayIndex
            val base = DayState(
                phase = DayPhase.ToDo,
                dayIndex = stage.dayIndex,
                day = day,
                target = reps,
                banked = banked,
                title = "",
                tone = DayTone.Plain,
                text = null,
                timeLeft = timeLeft,
                warning = false,
                button = null,
                offline = offline,
                lastDay = lastDay,
                number = reps.toString(),
                unit = "push-ups",
                numberTone = DayTone.Plain,
                sub = COUNTS_AS_DAILY,
            )
            return when {
                stage.todayDone -> base.copy(
                    phase = DayPhase.OnChain,
                    title = "Today is done and on chain.",
                    tone = DayTone.Success,
                    // The red countdown goes with the deadline; the next day's opening replaces it, and the last day has none.
                    text = if (lastDay) LAST_DAY else timeLeft?.let { "Day ${day + 1} opens in ${Format.countdown(it)}." },
                    timeLeft = null,
                    unit = "on chain",
                    numberTone = DayTone.Success,
                    sub = if (lastDay) LAST_DAY else "Streak +1. It counted as your Daily 25.",
                    pill = if (lastDay) "Settling after the end" else timeLeft?.let { "Day ${day + 1} opens in ${Format.countdown(it)}." },
                )
                waiting && mayStillLand -> base.copy(
                    phase = DayPhase.Unsent,
                    title = "Done — sent, not confirmed yet",
                    tone = DayTone.Accent,
                    text = "Day $day may still land. Sending opens again once devnet answers.",
                    button = DayButton("Submit day $day", DayAction.Submit, enabled = false),
                    unit = "done",
                    numberTone = DayTone.Success,
                    sub = "Day $day may still land. Sending opens again once devnet answers.",
                )
                waiting && !offline && !canPayFees -> base.copy(
                    phase = DayPhase.ShortOfSol,
                    title = "Today's $reps are done",
                    tone = DayTone.Accent,
                    text = "Not on chain yet.",
                    button = DayButton("Submit day $day", DayAction.Submit, enabled = false),
                    unit = "done",
                    numberTone = DayTone.Success,
                    sub = "Not enough devnet SOL to put day $day on chain.",
                    subTone = DayTone.Warning,
                )
                waiting -> {
                    val tap = !offline && !busy && autoSendTried
                    base.copy(
                        phase = DayPhase.Unsent,
                        title = when {
                            offline -> "Done — waiting for network"
                            tap -> "Done — tap to submit"
                            else -> "Done — sending to the chain"
                        },
                        tone = DayTone.Success,
                        text = if (offline) {
                            // Not "by itself": nothing is sent while the app is closed (FR-024b, 2026-10-10).
                            "Your $reps are saved on this phone. It only counts once it reaches the chain before the deadline: it's sent when Holdout is open and back online, and you approve it in your wallet."
                        } else {
                            "Your $reps are counted on this phone. It only counts once it reaches the chain before the deadline" + if (tap) "." else ": approve it in your wallet."
                        },
                        button = when {
                            tap -> DayButton("Submit day $day", DayAction.Submit)
                            !offline -> DayButton(Format.WAITING_FOR_WALLET, DayAction.None, busy = true)
                            else -> null
                        },
                        unit = "done",
                        numberTone = DayTone.Success,
                        sub = when {
                            offline -> "Saved on this phone. It sends when devnet answers."
                            tap -> "Counted on this phone. It counts once it is on chain."
                            else -> "Approve it in your wallet to put day $day on chain."
                        },
                    )
                }
                else -> {
                    val warning = Derive.inWarningWindow(timeLeft)
                    val owed = (reps - banked).coerceAtLeast(0)
                    base.copy(
                        title = when {
                            warning -> "$owed push-ups still needed"
                            banked > 0 -> "$banked of $reps banked"
                            else -> "$reps push-ups"
                        },
                        tone = if (warning) DayTone.Warning else DayTone.Plain,
                        text = when {
                            warning -> "Under 10 minutes left. Unfinished reps won't count."
                            banked > 0 -> "$owed to go"
                            else -> "all at once or $BLOCK at a time"
                        },
                        warning = warning,
                        button = DayButton(if (banked > 0) "Keep going" else "Start", DayAction.Start),
                        number = (if (banked > 0 || warning) owed else reps).toString(),
                        unit = when {
                            warning -> "still needed"
                            banked > 0 -> "to go"
                            else -> "push-ups"
                        },
                        numberTone = if (warning) DayTone.Warning else DayTone.Plain,
                        sub = when {
                            warning -> "Under 10 minutes left. Unfinished reps won't count."
                            banked > 0 -> "$banked banked for good"
                            else -> COUNTS_AS_DAILY
                        },
                        subTone = if (warning) DayTone.Warning else DayTone.Plain,
                    )
                }
            }
        }

        /**
         * The sitting a Start tap opens, from Today or Standing alike: this Holdout's day. An exercise or a form this
         * build does not know is refused, never guessed (FR-014, H30, H35).
         */
        fun start(h: Holdout, dayIndex: Int): NavEvent.StartSitting? = if (h.cohort.exercise == LeagueParams.EXERCISE_PUSHUP && Stakes.ofForm(h.cohort.form) != null) {
            NavEvent.StartSitting(Sitting.HoldoutDay(h.address.base58(), dayIndex))
        } else {
            null
        }
    }
}
