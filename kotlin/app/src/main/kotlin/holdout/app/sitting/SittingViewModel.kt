package holdout.app.sitting

import androidx.lifecycle.ViewModel
import holdout.app.storage.Store
import holdout.app.streak.StreakStore
import holdout.app.vision.VisionAnalyzer
import holdout.repcounter.PoseFrame
import holdout.repcounter.Preflight
import holdout.repcounter.PreflightInput
import holdout.repcounter.PreflightReport
import holdout.repcounter.ReadyGate
import holdout.repcounter.RepCounter
import holdout.repcounter.RepEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One sitting, start to finish (T050, PRD §8, §10.1 screen 8).
 *
 * ```
 * pre-flight (four pills) + held at the top of a push-up
 *            → "three, two, one, go" → counting (bank every 10) → day complete
 * ```
 *
 * **Nothing is tapped after Start.** The member is face-down on the floor by
 * the time the pills go green, so the flow reads the position instead of asking
 * for a gesture, and speaks every transition, because the screen is out of
 * sight (`ReadyGate`, `SittingCues`).
 *
 * A sitting is one continuous run of checks and counting, and **every sitting
 * is verified from scratch** — the pills run again on each return (FR-019).
 * The palm liveness check that used to sit here is gone, and what that costs
 * is named in `ReadyGate` and in the README.
 *
 * The pure rules live in `:repcounter` and are tested without a phone. This
 * class is the wiring: which model the camera should be feeding, what the
 * screen shows, and when a rep becomes a banked block.
 */
class SittingViewModel(
    private val store: Store,
    private val streaks: StreakStore,
    private val wallet: String,
    private val scope: String,
    private val dayIndex: Long,
    private val target: Int,
    /** Chain time now, for the day's countdown. Null while the chain is unread. */
    private val chainSecondsNow: () -> Long?,
) : ViewModel() {

    enum class Step { PREFLIGHT, COUNTDOWN, COUNTING, COMPLETE }

    /**
     * Something the member should hear. The screen is on the floor and they are
     * looking at it from above, or not at all.
     */
    sealed interface Cue {
        data class CountdownTick(val remaining: Int) : Cue
        data object Counting : Cue
        data class BlockBanked(val banked: Int) : Cue
        data object DayComplete : Cue
    }

    data class UiState(
        val step: Step = Step.PREFLIGHT,
        val preflight: PreflightReport? = null,
        /** How far through the ready-position hold, 0..1. */
        val readyProgress: Float = 0f,
        val countdownFrom: Int = 3,
        val banked: Int = 0,
        val looseReps: Int = 0,
        val target: Int = 25,
        val hint: String? = null,
        val justBanked: Int? = null,
        val skeleton: PoseFrame? = null,
        /** The analysed frame's aspect, so the overlay can letterbox as the preview does. */
        val frameWidth: Int = 0,
        val frameHeight: Int = 0,
        val error: String? = null,
    ) {
        val doneToday: Int get() = banked + looseReps
        val remaining: Int get() = (target - doneToday).coerceAtLeast(0)
    }

    private val preflight = Preflight()
    private val readyGate = ReadyGate()
    private val banking = BankingController(store, wallet, scope, dayIndex, target)
    private var repCounter = RepCounter()

    private val _uiState = MutableStateFlow(
        UiState(banked = banking.banked, target = target),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // Buffered, because cues are emitted from the camera's analysis thread and
    // that thread must never block waiting for the UI to collect one.
    private val _cues = MutableSharedFlow<Cue>(extraBufferCapacity = 8)
    val cues: SharedFlow<Cue> = _cues.asSharedFlow()

    /** Seconds left in today, for the header. Null while the chain is unread. */
    fun secondsLeftToday(): Long? = chainSecondsNow()?.let { now ->
        holdout.core.HoldoutDay.secondsLeftIn(now)
    }

    /** Every pose frame while the pills are up, and while counting. */
    fun onPoseFrame(observation: VisionAnalyzer.Observation, backgroundMotion: Float) {
        when (_uiState.value.step) {
            Step.PREFLIGHT -> {
                val report = preflight.evaluate(
                    PreflightInput(
                        observation.brightness,
                        backgroundMotion,
                        observation.world,
                        observation.image,
                    ),
                )
                val ready = readyGate.onFrame(
                    frame = observation.world,
                    nowMillis = observation.world.timestampMillis,
                    pillsGreen = report.canStart,
                )
                _uiState.update {
                    it.withFrame(observation)
                        .copy(preflight = report, readyProgress = readyGate.progress)
                }
                if (ready) startCountdown()
            }

            // The overlay stays live through the countdown, so the member can
            // still see they are in frame while they hold position.
            Step.COUNTDOWN -> _uiState.update { it.withFrame(observation) }

            Step.COUNTING -> {
                val event = repCounter.onFrame(observation.world)
                _uiState.update { it.withFrame(observation) }
                when (event) {
                    is RepEvent.Counted -> onVerifiedRep()
                    is RepEvent.Discarded -> _uiState.update { it.copy(hint = event.hint?.message) }
                    null -> Unit
                }
            }

            else -> Unit
        }
    }

    private fun UiState.withFrame(observation: VisionAnalyzer.Observation) = copy(
        skeleton = observation.image,
        frameWidth = observation.frameWidth,
        frameHeight = observation.frameHeight,
    )

    /**
     * The pills are green and the position has held. Nothing was tapped.
     */
    private fun startCountdown() {
        readyGate.reset()
        _uiState.update { it.copy(step = Step.COUNTDOWN, readyProgress = 1f) }
    }

    /** One second of the countdown, driven by the screen's timer. */
    fun onCountdownTick(remaining: Int) {
        _cues.tryEmit(Cue.CountdownTick(remaining))
    }

    /** The 3-second countdown finished. A fresh counter: this sitting starts at zero. */
    fun startCounting() {
        repCounter = RepCounter()
        _uiState.update { it.copy(step = Step.COUNTING, hint = null, looseReps = 0) }
        _cues.tryEmit(Cue.Counting)
    }

    private fun onVerifiedRep() {
        when (val outcome = banking.onVerifiedRep()) {
            is BankingController.Outcome.Counted ->
                _uiState.update { it.copy(looseReps = banking.loose, hint = null, justBanked = null) }

            is BankingController.Outcome.Banked -> {
                _uiState.update {
                    it.copy(
                        banked = outcome.banked,
                        looseReps = banking.loose,
                        justBanked = outcome.banked,
                        hint = null,
                    )
                }
                // Ten reps are saved and cannot be lost now. That is worth a
                // sound of its own: it is the only progress signal a member
                // with their head down gets.
                _cues.tryEmit(Cue.BlockBanked(outcome.banked))
            }

            is BankingController.Outcome.DayComplete -> {
                // A Daily 25 is recorded on the device and extends the streak;
                // nothing goes on-chain and no connection is needed (FR-051).
                // A cohort day also submits — T061, not this task.
                streaks.recordCompletedDay(wallet, dayIndex)
                _cues.tryEmit(Cue.DayComplete)
                _uiState.update {
                    it.copy(
                        step = Step.COMPLETE,
                        banked = outcome.total,
                        looseReps = 0,
                        hint = null,
                    )
                }
            }
        }
    }

    /** The member stopped, or the screen went away. Loose reps go; blocks stay. */
    fun endSitting(): Int = banking.endSitting()

    fun onError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun dismissJustBanked() {
        _uiState.update { it.copy(justBanked = null) }
    }
}
