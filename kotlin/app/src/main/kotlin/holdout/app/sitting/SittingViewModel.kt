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
    dayIndex: Long,
    private val target: Int,
    /** Chain time now, for the day's countdown. Null while the chain is unread. */
    private val chainSecondsNow: () -> Long?,
    /**
     * Every sitting is at 45° ([Stakes]); the Daily 25 and Easy count push-ups
     * from the knees, Hard only from the toes. It decides what the
     * camera check accepts, what the guide asks for and what the counter reads.
     */
    private val stakes: Stakes = Stakes.DAILY,
    /**
     * Which day a chain instant falls on, in this sitting's own days: the
     * global 05:00 UTC day for the Daily 25, the cohort's day for a Holdout.
     * A sitting that runs across a boundary banks the rest into the new day —
     * measured in the wrong days, a cohort sitting once filed its reps under
     * global day 20,723 instead of cohort day 0, and the day never submitted.
     */
    private val dayOf: (Long) -> Long = holdout.core.HoldoutDay::indexOf,
    /**
     * Whether reps keep counting into the next day when the sitting runs across
     * a boundary. The Daily 25 does; a Holdout day does not — a day not finished
     * by its deadline is already a breach, so a set done after it could never
     * be submitted, and the sitting ends there instead.
     */
    private val rollsOver: Boolean = true,
    /**
     * A Holdout day's time left at a chain instant — shown through the whole
     * sitting, since the deadline is what it is held to. Null for the Daily 25,
     * whose day has no stake on it.
     */
    private val timeLeftAt: ((Long) -> Long?)? = null,
    /**
     * Debug builds: where to write this sitting's trace (`TraceRecorder`).
     * Null in release builds, where nothing is recorded.
     */
    traceDir: java.io.File? = null,
) : ViewModel() {

    private val trace = traceDir?.let { TraceRecorder(it, stakes) }

    enum class Step {
        PREFLIGHT,
        COUNTDOWN,
        COUNTING,
        COMPLETE,
        ;

        /** Counting down or counting: leaving the foreground now ends the set — its loose reps go, the checks start over ([onLeftForeground]). */
        val inSet: Boolean get() = this == COUNTDOWN || this == COUNTING
    }

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
        /** With the pills green and the hold not filling: the sentence that says why (`HoldBlocker.fix`). */
        val holdFix: String? = null,
        /** Debug builds: the trace file this sitting is being recorded to. */
        val traceName: String? = null,
        /** Debug builds: pose frames analysed per second, over the last few seconds. */
        val analysisFps: Float? = null,
        /** Debug builds: true while the camera records video, false if asked for and it could not start, null if not asked. */
        val recordingVideo: Boolean? = null,
        /** Reps the counter credited in this sitting — shown next to the member's own count. */
        val repsCounted: Int = 0,
    ) {
        val doneToday: Int get() = banked + looseReps
        val remaining: Int get() = (target - doneToday).coerceAtLeast(0)
    }

    private val preflight = Preflight(stakes = stakes)
    private val readyGate = ReadyGate(stakes = stakes)
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

    /** The Daily 25's toes meter (H35) rides on the banking: read at 45°, never in a Holdout. */
    private val banking = BankingController(
        store,
        wallet,
        scope,
        dayIndex,
        target,
        toes = holdout.app.streak.ToesMeter(store, target).takeIf { stakes == Stakes.DAILY && scope == Store.CHALLENGE },
    )
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

    /** Seconds to this Holdout day's deadline; null for the Daily 25, or while the chain is unread. */
    fun deadlineSecondsLeft(): Long? = timeLeftAt?.let { at -> chainSecondsNow()?.let(at) }

    /** Seconds left in today, for the header. Null while the chain is unread. */
    fun secondsLeftToday(): Long? = chainSecondsNow()?.let { now ->
        holdout.core.HoldoutDay.secondsLeftIn(now)
    }

    /**
     * A sitting running across 05:00 UTC banks the rest into the new day
     * ([BankingController.rollOverTo]). Not a sitting that started before
     * chain time was known — `OfflineDays` dates that one — and not once the
     * day is complete: the finished screen stays yesterday's.
     */
    private fun rollOverAtBoundary() {
        if (banking.dayIndex == Store.UNKNOWN_DAY || _uiState.value.step == Step.COMPLETE) return
        val today = chainSecondsNow()?.let(dayOf) ?: return
        if (!rollsOver) {
            if (today != banking.dayIndex) deadlinePassed()
            return
        }
        if (banking.rollOverTo(today)) {
            _uiState.update { it.copy(banked = banking.banked, looseReps = banking.loose) }
        }
    }

    /** A Holdout day's deadline passed mid-sitting: nothing more is counted, and the member is told why, aloud. */
    private fun deadlinePassed() {
        if (stopped) return
        stopped = true
        val message = "The deadline for day ${banking.dayIndex + 1} has passed. Reps from here would not count."
        _uiState.update { it.copy(error = message, hint = null) }
        _cues.tryEmit(Cue.Say(message))
    }

    /** Every pose frame while the pills are up, and while counting. */
    fun onPoseFrame(observation: VisionAnalyzer.Observation, backgroundMotion: Float) {
        measureFps(observation.world.timestampMillis)
        // After Stop nothing is counted or banked, even while the screen is
        // still up for the member to log what they did.
        if (stopped) return
        rollOverAtBoundary()
        // A Holdout day that just ended stops the sitting: not even this frame counts.
        if (stopped) return
        when (_uiState.value.step) {
            Step.PREFLIGHT -> {
                val report = preflight.evaluate(
                    PreflightInput(
                        observation.brightness,
                        backgroundMotion,
                        observation.world,
                        observation.image,
                        observation.imagePose(),
                    ),
                )
                val guidance = positionGuide.guide(
                    pose = observation.image,
                    view = report.view,
                    floor = Placement.floorView(tilt, lens),
                    allowedViews = stakes.allowedViews,
                    xScale = holdout.repcounter.Aspect.xScale(observation.frameWidth, observation.frameHeight),
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
                // What the position still lacks, once nothing about the phone or the
                // picture does — and only then, so it never talks over a pill's own fix.
                val blocker = readyGate.blocker.takeIf { !_uiState.value.showSetup }
                val coach = Coach.next(report, guidance, blocker)
                _uiState.update {
                    it.withFrame(observation).copy(
                        preflight = report,
                        readyProgress = readyGate.progress,
                        guidance = guidance,
                        coach = coach,
                        holdFix = blocker?.fix?.takeIf { coach != Coach.HOLD && report.canStart },
                    )
                }
                speak(coach, observation.world.timestampMillis)
                record(observation, report.view)
                if (ready) {
                    countingView = report.view ?: CameraView.SIDE
                    startCountdown()
                }
            }

            // The overlay stays live through the countdown, so the member can
            // still see they are in frame while they hold position. The checks
            // that started it keep running: a phone moved, or a placement
            // these stakes do not accept, sends the sitting back to them.
            Step.COUNTDOWN -> {
                val report = preflight.evaluate(
                    PreflightInput(observation.brightness, backgroundMotion, observation.world, observation.image, observation.imagePose()),
                )
                _uiState.update { it.withFrame(observation).copy(preflight = report) }
                record(observation, countingView)
                // Hard: the counter calibrates on this plank, so the plank must stay one
                // it can read — knees coming down, a knee it cannot place, or the body
                // out of line stop the countdown as a moved phone does (review, PR #14).
                // Arms bending is not checked — people start down on "1" — so the plank is read
                // whatever the arms do: the gate's own blocker names the arms first and would
                // hide the rest once they bend. On every Hard countdown recorded the plank
                // failed for at most 400 ms in a row, under the guard's 500 ms grace.
                val kneesDown = stakes == Stakes.STAKED &&
                    readyGate.run {
                        onFrame(observation.world, observation.world.timestampMillis, pillsGreen = true, view = countingView, image = observation.imagePose())
                        plankBlocker != null
                    }
                val holds = report.canStart && report.view == countingView && !kneesDown
                synchronized(countdownLock) {
                    // The timer may have ended the countdown on the main
                    // thread while this frame was being read.
                    if (_uiState.value.step != Step.COUNTDOWN) return
                    // The counter follows the countdown without judging it, so a
                    // descent begun on "1" is already tracked at "go".
                    repCounter.prime(observation.world, observation.imagePose())
                    if (countdownGuard.shouldAbort(observation.world.timestampMillis, holds)) backToChecks()
                }
            }

            Step.COUNTING -> {
                val event = repCounter.onFrame(observation.world, observation.imagePose())
                _uiState.update { it.withFrame(observation).copy(readout = repCounter.readout) }
                record(observation, countingView, repCounter.readout, event)
                when (event) {
                    is RepEvent.Counted -> {
                        _uiState.update { it.copy(repsCounted = repCounter.total) }
                        onVerifiedRep(event.stance)
                    }
                    is RepEvent.Discarded -> {
                        _uiState.update { it.copy(hint = event.hint?.message) }
                        // Face-down, the member cannot read the screen: every refused
                        // rep's reason is said out loud, at once (owner, 2026-10-04 —
                        // it was the second time in a row). A new one cuts the last off.
                        event.hint?.let { _cues.tryEmit(Cue.Say(it.message)) }
                    }
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

    private val countdownGuard = holdout.repcounter.CountdownGuard()

    /**
     * The countdown ends on two threads: the screen's timer (main) and the
     * checks, read from pose results on MediaPipe's thread. Every step change
     * out of the countdown, and the counter it hands over, happens under this
     * lock, so the two can never both win.
     */
    private val countdownLock = Any()

    /** The countdown's checks stopped passing: back to the pills, as if it had never started. */
    private fun backToChecks() {
        countdownGuard.reset()
        readyGate.reset()
        _uiState.update { it.copy(step = Step.PREFLIGHT, readyProgress = 0f) }
    }

    /** The pills are green and the position has held. Nothing was tapped. */
    private fun startCountdown() {
        repCounter = RepCounter(view = countingView, stakes = stakes)
        countdownGuard.reset()
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

    /**
     * The 3-second countdown finished. The counter was built when it started
     * and has followed it, so a descent begun on "1" counts.
     */
    fun startCounting() {
        synchronized(countdownLock) {
            // The screen's timer ends the countdown on its own clock; the checks
            // decide whether counting may begin. An abort that already happened,
            // or a check failing at this very moment, wins over the timer.
            if (_uiState.value.step != Step.COUNTDOWN) return
            // Hard, and no plank read through the countdown: the legs on each push could
            // not be judged against this person's own, and every rep would pass that check.
            if (countdownGuard.isFailing || (stakes == Stakes.STAKED && !repCounter.plankCalibrated)) {
                backToChecks()
                return
            }
            _uiState.update { it.copy(step = Step.COUNTING, hint = null, looseReps = 0) }
        }
        _cues.tryEmit(Cue.Counting)
    }

    private fun onVerifiedRep(stance: holdout.repcounter.RepStance?) {
        when (val outcome = banking.onVerifiedRep(stance)) {
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
                // A day done before chain time was known has no date yet;
                // OfflineDays records the streak once it has one.
                if (scope == Store.CHALLENGE && banking.dayIndex != Store.UNKNOWN_DAY) streaks.recordCompletedDay(wallet, banking.dayIndex)
                trace?.close()
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

    @Volatile
    private var stopped = false

    /**
     * The app left the foreground mid-sitting: the camera stopped watching, so
     * what follows is a new sitting. Loose reps go, banked blocks stay, and the
     * checks and the ready hold are passed again before anything counts — nine
     * loose reps before a break must not be finished by one rep after it.
     */
    fun onLeftForeground() {
        synchronized(countdownLock) {
            if (stopped || !_uiState.value.step.inSet) return
            banking.endSitting()
            backToChecks()
            _uiState.update { it.copy(looseReps = 0, hint = null) }
        }
    }

    /** The member stopped, or the screen went away. Loose reps go; blocks stay. */
    fun endSitting(): Int {
        stopped = true
        trace?.close()
        return banking.endSitting()
    }

    /** Debug builds: whether this sitting wrote a trace that can be labelled. */
    val canLabelTrace: Boolean get() = trace?.file != null

    /**
     * Debug builds: what the member says they did, written into the trace. The
     * expected count is what the rule should credit: full push-ups, and in the
     * Daily 25 push-ups from the knees as well (FR-021a).
     */
    fun labelTrace(done: Map<String, Int>, note: String, placement: String? = null, phoneSide: String? = null) {
        val recorder = trace ?: return
        val expected = (done["honest"] ?: 0) + if (stakes == Stakes.DAILY) done["knees"] ?: 0 else 0
        Thread { recorder.label(expected, done, note, placement, phoneSide) }.start()
    }

    override fun onCleared() {
        trace?.close()
    }

    /**
     * The screen went away, however it was left — Stop, finishing, the back
     * gesture, the app going to the background for good. Closes the trace so
     * nothing buffered is lost. This is not constructed through a
     * `ViewModelProvider`, so [onCleared] alone would never run.
     */
    fun release() {
        trace?.close()
    }

    private fun record(
        observation: VisionAnalyzer.Observation,
        view: CameraView?,
        readout: holdout.repcounter.Readout? = null,
        event: RepEvent? = null,
    ) {
        trace?.record(
            timestampMillis = observation.world.timestampMillis,
            step = _uiState.value.step.name,
            view = view,
            world = observation.world,
            image = observation.image,
            width = observation.frameWidth,
            height = observation.frameHeight,
            readout = readout,
            event = event,
        )
        if (_uiState.value.traceName == null) {
            trace?.file?.name?.let { name -> _uiState.update { it.copy(traceName = name) } }
        }
    }

    private val frameTimes = ArrayDeque<Long>()

    /** Frames per second over the last three seconds of camera time. */
    private fun measureFps(timestampMillis: Long) {
        frameTimes.addLast(timestampMillis)
        while (frameTimes.size > 1 && timestampMillis - frameTimes.first() > 3_000) frameTimes.removeFirst()
        if (frameTimes.size < 2) return
        val span = (frameTimes.last() - frameTimes.first()).coerceAtLeast(1)
        val fps = (frameTimes.size - 1) * 1_000f / span
        // Once a second of camera time: a frame count would stall once the window fills.
        if (timestampMillis - fpsShownAt < 1_000) return
        fpsShownAt = timestampMillis
        _uiState.update { it.copy(analysisFps = fps) }
    }

    private var fpsShownAt = Long.MIN_VALUE / 2

    /** Whether the camera is recording this sitting as video (debug builds). */
    fun onRecordingVideo(on: Boolean) = _uiState.update { it.copy(recordingVideo = on) }

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
