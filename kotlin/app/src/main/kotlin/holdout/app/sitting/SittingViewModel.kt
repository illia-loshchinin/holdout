package holdout.app.sitting

import androidx.lifecycle.ViewModel
import holdout.app.storage.Store
import holdout.app.streak.StreakStore
import holdout.app.vision.VisionAnalyzer
import holdout.repcounter.CameraView
import holdout.repcounter.Coach
import holdout.repcounter.Guidance
import holdout.repcounter.ImagePose
import holdout.repcounter.Lens
import holdout.repcounter.Placement
import holdout.repcounter.PoseFrame
import holdout.repcounter.PositionGuide
import holdout.repcounter.Preflight
import holdout.repcounter.PreflightInput
import holdout.repcounter.PreflightReport
import holdout.repcounter.Readout
import holdout.repcounter.ReadyGate
import holdout.repcounter.RepCounter
import holdout.repcounter.RepEvent
import holdout.repcounter.Stakes
import holdout.repcounter.Tilt
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
    /**
     * The free Daily 25 takes any placement; a staked sitting is side-on or
     * angled, never head-on, with full push-ups from the toes ([Stakes]). It decides what the
     * camera check accepts, what the guide asks for and what the counter reads.
     */
    private val stakes: Stakes = Stakes.DAILY,
) : ViewModel() {

    enum class Step { PREFLIGHT, COUNTDOWN, COUNTING, COMPLETE }

    /**
     * Something the member should hear. The screen is on the floor and they are
     * looking at it from above, or not at all.
     */
    sealed interface Cue {
        data class CountdownTick(val remaining: Int) : Cue
        data object Counting : Cue

        /** One verified rep that did not also bank a block — the block has its own chime. */
        data object Rep : Cue

        /** The coach's next correction, spoken — the member is too far away to read. */
        data class Say(val words: String) : Cue
        data class BlockBanked(val banked: Int) : Cue
        data object DayComplete : Cue
    }

    data class UiState(
        val step: Step = Step.PREFLIGHT,
        val preflight: PreflightReport? = null,
        /** How far through the ready-position hold, 0..1. */
        val readyProgress: Float = 0f,
        /**
         * Where to lie, and the one thing to fix next. Null before the first
         * frame. The overlay drawn from this comes off the moment the member is
         * aligned — it is scaffolding, not furniture.
         */
        val guidance: Guidance? = null,
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
        /** What this sitting is held to: decides the set-up card, and which placements it shows. */
        val stakes: Stakes = Stakes.DAILY,
        /** What the counter last read, for the debug readout. */
        val readout: Readout? = null,
        /**
         * The set-up guide is open. It opens when the sitting does — while the
         * member is still holding the phone — unless they asked not to see it.
         */
        val showSetup: Boolean = false,
        /** The one short correction to show big while getting into position. */
        val coach: Coach? = null,
    ) {
        val doneToday: Int get() = banked + looseReps
        val remaining: Int get() = (target - doneToday).coerceAtLeast(0)
    }

    private val preflight = Preflight(stakes = stakes)
    private val readyGate = ReadyGate()
    private val positionGuide = PositionGuide()

    /** Updated from the gravity sensor; drives where the floor is drawn. */
    @Volatile
    private var tilt: Tilt = Tilt(0f)

    /** Read from the camera characteristics once bound; a sane guess until then. */
    @Volatile
    private var lens: Lens = Lens.DEFAULT

    fun onTilt(tilt: Tilt) {
        this.tilt = tilt
    }

    fun onLens(lens: Lens) {
        this.lens = lens
    }
    private val banking = BankingController(store, wallet, scope, dayIndex, target)
    private var repCounter = RepCounter()

    /**
     * The view the ready position was held in. Counting measures in it for the
     * whole sitting — head-on reads the elbow from the picture, side-on from
     * the body (`ViewReading`) — so it is fixed when the countdown starts
     * rather than re-judged on every frame of a set.
     */
    private var countingView: CameraView = CameraView.SIDE

    private val _uiState = MutableStateFlow(
        UiState(
            banked = banking.banked,
            target = target,
            stakes = stakes,
            showSetup = !store.isSetupGuideHidden(wallet, stakes.name),
        ),
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
                val guidance = positionGuide.guide(
                    pose = observation.image,
                    view = report.view,
                    floor = Placement.floorView(tilt, lens),
                    allowedViews = stakes.allowedViews,
                )
                // Nobody can be in position while reading the guide, and a
                // countdown must never start behind it.
                if (_uiState.value.showSetup) readyGate.reset()
                val ready = !_uiState.value.showSetup &&
                    readyGate.onFrame(
                        frame = observation.world,
                        nowMillis = observation.world.timestampMillis,
                        pillsGreen = report.canStart,
                        view = report.view,
                        image = observation.imagePose(),
                    )
                val coach = Coach.next(report, guidance)
                _uiState.update {
                    it.withFrame(observation).copy(
                        preflight = report,
                        readyProgress = readyGate.progress,
                        guidance = guidance,
                        coach = coach,
                    )
                }
                speak(coach, observation.world.timestampMillis)
                if (ready) {
                    countingView = report.view ?: CameraView.SIDE
                    startCountdown()
                }
            }

            // The overlay stays live through the countdown, so the member can
            // still see they are in frame while they hold position.
            Step.COUNTDOWN -> _uiState.update { it.withFrame(observation) }

            Step.COUNTING -> {
                val event = repCounter.onFrame(observation.world, observation.imagePose())
                _uiState.update { it.withFrame(observation).copy(readout = repCounter.readout) }
                when (event) {
                    is RepEvent.Counted -> onVerifiedRep()
                    is RepEvent.Discarded -> _uiState.update { it.copy(hint = event.hint?.message) }
                    null -> Unit
                }
            }

            else -> Unit
        }
    }

    private fun VisionAnalyzer.Observation.imagePose() = ImagePose(image, frameWidth, frameHeight)

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
        // The guide is dropped here and never comes back. A silhouette
        // reappearing over somebody mid-set is a distraction they cannot act on
        // without abandoning the set.
        _uiState.update { it.copy(step = Step.COUNTDOWN, readyProgress = 1f, guidance = null) }
    }

    /** One second of the countdown, driven by the screen's timer. */
    fun onCountdownTick(remaining: Int) {
        _cues.tryEmit(Cue.CountdownTick(remaining))
    }

    /** The 3-second countdown finished. A fresh counter: this sitting starts at zero. */
    fun startCounting() {
        repCounter = RepCounter(view = countingView, stakes = stakes)
        _uiState.update { it.copy(step = Step.COUNTING, hint = null, looseReps = 0) }
        _cues.tryEmit(Cue.Counting)
    }

    private fun onVerifiedRep() {
        when (val outcome = banking.onVerifiedRep()) {
            is BankingController.Outcome.Counted -> {
                _uiState.update { it.copy(looseReps = banking.loose, hint = null, justBanked = null) }
                _cues.tryEmit(Cue.Rep)
            }

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
                // Only the real Daily 25 extends the streak — never a test run.
                if (scope == Store.CHALLENGE) streaks.recordCompletedDay(wallet, dayIndex)
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

    private var coachSince: Pair<Coach, Long>? = null
    private var spoken: Pair<Coach, Long>? = null

    /**
     * Say the correction once it has held for a moment — so a flicker between
     * two words is not read out — and again every few seconds while it is still
     * the thing to fix. "Hold" is said once: repeating it would talk over the
     * countdown it leads into.
     */
    private fun speak(coach: Coach, nowMillis: Long) {
        if (_uiState.value.showSetup) return
        val since = coachSince?.takeIf { it.first == coach }?.second ?: nowMillis.also { coachSince = coach to it }
        if (nowMillis - since < COACH_SETTLE_MILLIS) return
        val last = spoken
        val due = when {
            last == null || last.first != coach -> true
            coach == Coach.HOLD -> false
            else -> nowMillis - last.second >= COACH_REPEAT_MILLIS
        }
        if (!due) return
        spoken = coach to nowMillis
        _cues.tryEmit(Cue.Say(coach.words))
    }

    private companion object {
        const val COACH_SETTLE_MILLIS = 700L
        const val COACH_REPEAT_MILLIS = 5_000L
    }

    /** The guide was closed. [dontShowAgain] is remembered for this wallet and these stakes. */
    fun closeSetup(dontShowAgain: Boolean) {
        if (dontShowAgain) store.hideSetupGuide(wallet, stakes.name)
        _uiState.update { it.copy(showSetup = false) }
    }

    /** "How to set up" — reopens the guide, whatever was chosen before. */
    fun openSetup() {
        _uiState.update { it.copy(showSetup = true) }
    }

    fun onError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun dismissJustBanked() {
        _uiState.update { it.copy(justBanked = null) }
    }
}
