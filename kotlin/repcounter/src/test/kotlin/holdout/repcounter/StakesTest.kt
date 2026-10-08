package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where the phone may go: head-on or at 45° for the free Daily 25, at 45° only
 * when money is on it, and square side-on for neither.
 *
 * Head-on the legs point into the lens and a push-up from the knees is
 * invisible — fine for a free habit, not when one member's knee push-up takes
 * another member's stake. At 45° the picture shows the knee, so it is judged.
 */
class StakesTest {

    private fun input(shoulderSpan: Float) = PreflightInput(
        brightness = 0.5f,
        backgroundMotion = 0f,
        world = Traces.frame(0, elbowDegrees = 175f, bodyLineDegrees = 180f),
        image = Traces.imageFrame(shoulderSpan = shoulderSpan),
    )

    private val sideOn = input(shoulderSpan = 0.05f)
    private val atFortyFive = input(shoulderSpan = 0.3f)
    private val headOn = input(shoulderSpan = 0.7f)

    @Test
    fun `a staked sitting starts at 45 degrees`() {
        val report = Preflight(stakes = Stakes.STAKED).evaluate(atFortyFive)
        assertEquals(CameraView.OBLIQUE, report.view)
        assertTrue(report.canStart)
    }

    @Test
    fun `a staked sitting will not start head-on or square side-on, and says turn to 45`() {
        for (frame in listOf(headOn, sideOn)) {
            val report = Preflight(stakes = Stakes.STAKED).evaluate(frame)
            assertFalse(report.canStart, "${report.view} must not start a staked sitting")
            assertEquals(listOf(Stakes.STAKED.cameraFix), report.fixes)
        }
    }

    @Test
    fun `the Daily 25 starts at 45 degrees only, as an Easy Holdout does`() {
        // 8 Oct (002 FR-001): head-on is dropped — the Daily 25 is where a Holdout's set-up is practised.
        assertTrue(Preflight(stakes = Stakes.DAILY).evaluate(atFortyFive).canStart)
        for (frame in listOf(headOn, sideOn)) {
            val report = Preflight(stakes = Stakes.DAILY).evaluate(frame)
            assertFalse(report.canStart, "${report.view} must not start the Daily 25")
            assertEquals(listOf(Stakes.DAILY.cameraFix), report.fixes)
        }
        assertEquals(Stakes.EASY.allowedViews, Stakes.DAILY.allowedViews)
    }

    @Test
    fun `no counter is measured head-on, whatever the stakes`() {
        // The second lock on the door the camera check closes (002 FR-006, retired 8 Oct).
        for (stakes in Stakes.entries) {
            assertFailsWith<IllegalArgumentException> { RepCounter(view = CameraView.FRONT, stakes = stakes) }
        }
        assertNull(ViewReading.of(CameraView.FRONT, Traces.frame(0, 175f, 180f), Traces.angledImage(0, 175f)))
    }

    @Test
    fun `the guide asks for the stakes' placement before anything else, and never calls the wrong one aligned`() {
        val guide = PositionGuide()
        val floor = FloorView.Horizon(0.35f)
        val allowed = Stakes.STAKED.allowedViews
        val target = guide.guide(null, CameraView.OBLIQUE, floor, allowedViews = allowed).target
        val landmarks = MutableList(PoseIndex.COUNT) { Landmark(target.centreX, target.centreY, 0f, 1f) }
        landmarks[0] = Landmark(target.left, target.top, 0f, 1f)
        landmarks[1] = Landmark(target.right, target.bottom, 0f, 1f)
        val body = PoseFrame(0, landmarks)

        val headOnGuide = guide.guide(body, CameraView.FRONT, floor, allowedViews = allowed)
        assertEquals(PositionHint.PHONE_AT_45, headOnGuide.hint)
        assertFalse(headOnGuide.aligned)

        val angledGuide = guide.guide(body, CameraView.OBLIQUE, floor, allowedViews = allowed)
        assertNull(angledGuide.hint)
        assertTrue(angledGuide.aligned)

        val dailySideOn = guide.guide(body, CameraView.SIDE, floor, allowedViews = Stakes.DAILY.allowedViews)
        assertEquals(PositionHint.PHONE_AT_45, dailySideOn.hint)
    }

    @Test
    fun `a staked counter cannot be built for a view other than 45 degrees`() {
        assertFailsWith<IllegalArgumentException> { RepCounter(view = CameraView.FRONT, stakes = Stakes.STAKED) }
        assertFailsWith<IllegalArgumentException> { RepCounter(view = CameraView.SIDE, stakes = Stakes.STAKED) }
        RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.STAKED)
    }

    /** A set at 45°, read from the picture as on a phone, with the knee held at [kneeDegrees]. */
    private fun angledSet(kneeDegrees: Float, count: Int = 10): Pair<Int, List<RepHint?>> {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.STAKED)
        val hints = mutableListOf<RepHint?>()
        for (frame in Traces.set(count = count)) {
            val image = Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees)
            (counter.onFrame(frame, image) as? RepEvent.Discarded)?.let { hints += it.hint }
        }
        return counter.total to hints
    }

    @Test
    fun `at 45 degrees, full push-ups from the toes count`() {
        val (count, hints) = angledSet(kneeDegrees = 178f)
        assertEquals(10, count)
        assertTrue(hints.isEmpty(), "expected no hints, got $hints")
    }

    @Test
    fun `at 45 degrees, push-ups from the knees count nothing, and say why`() {
        val (count, hints) = angledSet(kneeDegrees = 70f)
        assertEquals(0, count)
        assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.UP_ON_YOUR_TOES }, "got $hints")
    }

    @Test
    fun `a leg bent a little in the air is fine - only knees on the floor are refused`() {
        // The rule is knees off the floor, not legs dead straight.
        assertEquals(10, angledSet(kneeDegrees = 150f).first)
        assertEquals(0, angledSet(kneeDegrees = 70f).first)
    }

    @Test
    fun `at 45 degrees the elbow is read from the picture, not the 3D estimate`() {
        // The world elbow says a full push-up; the picture says the arms barely
        // bent. On a phone the picture decides, so nothing counts.
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.STAKED)
        for (frame in Traces.set(count = 5)) {
            val shallow = 175f - (175f - Angles.elbow(frame)!!) * 0.3f
            counter.onFrame(frame, Traces.angledImage(frame.timestampMillis, shallow))
        }
        assertEquals(0, counter.total)
    }

    @Test
    fun `the near side is read, not the hidden far one`() {
        // The near leg straight and long in the picture; the far leg, which the
        // model guesses, short and folded. Only the near one may decide.
        val near = Traces.angledImage(0, elbowDegrees = 175f, kneeDegrees = 178f)
        val landmarks = near.frame.landmarks.toMutableList()
        val hip = landmarks[PoseIndex.LEFT_HIP]
        landmarks[PoseIndex.RIGHT_HIP] = hip
        landmarks[PoseIndex.RIGHT_KNEE] = Landmark(hip.x + 0.03f, hip.y + 0.02f, 0f, 1f)
        landmarks[PoseIndex.RIGHT_ANKLE] = Landmark(hip.x, hip.y + 0.03f, 0f, 1f)
        val picture = ImagePose(PoseFrame(0, landmarks), near.width, near.height)
        assertEquals(Side.LEFT, Angles.nearSide(picture))
        assertTrue(Angles.kneeInImage(picture)!! > 170f)
    }

    @Test
    fun `the Daily 25 counts push-ups from the knees - staked sittings do not`() {
        val daily = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
        for (frame in Traces.set(count = 10)) {
            daily.onFrame(frame, Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees = 70f))
        }
        assertEquals(10, daily.total, "the Daily 25 is a habit for building strength; knees are allowed")
        assertEquals(0, angledSet(kneeDegrees = 70f).first, "money is only staked on full push-ups")
    }

    /** [image] with [joint] moved [pixels] down the picture (up when negative). The drawn arm is 170 px long. */
    private fun moved(image: ImagePose, joint: Int, pixels: Float): ImagePose {
        val landmarks = image.frame.landmarks.toMutableList()
        landmarks[joint] = landmarks[joint].copy(y = landmarks[joint].y + pixels / image.height)
        return image.copy(frame = image.frame.copy(landmarks = landmarks))
    }

    private fun dailySet(picture: (ImagePose) -> ImagePose): Pair<Int, List<RepHint?>> {
        val daily = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
        val hints = mutableListOf<RepHint?>()
        for (frame in Traces.set(count = 10)) {
            val image = picture(Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!))
            (daily.onFrame(frame, image) as? RepEvent.Discarded)?.let { hints += it.hint }
        }
        return daily.total to hints
    }

    @Test
    fun `the Daily 25 counts nothing done on the back or standing up - in silence`() {
        // 7 Oct: crunches with the knees up, and a kettlebell held standing, counted
        // among push-ups — the arms bent and straightened, the body line read straight.
        assertEquals(10, dailySet { it }.first, "the same arms, face down: ten push-ups")
        val onTheBack = dailySet {
            val (hip, knee) = it.frame.landmarks.let { l -> l[PoseIndex.LEFT_HIP] to l[PoseIndex.LEFT_KNEE] }
            moved(it, PoseIndex.LEFT_KNEE, (hip.y - knee.y) * it.height - 100f)
        }
        assertEquals(0, onTheBack.first, "the knee above the hip: lying on the back")
        assertTrue(onTheBack.second.all { it == null }, "nothing to coach: ${onTheBack.second}")
        val standing = dailySet { moved(it, PoseIndex.LEFT_ANKLE, 300f) }
        assertEquals(0, standing.first, "the ankle a whole leg below the hand: standing")
        assertTrue(standing.second.all { it == null }, "nothing to coach: ${standing.second}")
    }

    @Test
    fun `the Daily 25 may start from the knees`() {
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.DAILY)
        val top = { t: Long -> Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f) }
        val knees = { t: Long -> Traces.angledImage(t, elbowDegrees = 175f, kneeDegrees = 70f) }
        gate.onFrame(top(0), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(0))
        assertTrue(gate.onFrame(top(2_000), 2_000, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(2_000)))
    }

    @Test
    fun `the staked ready position is not held from the knees`() {
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.STAKED)
        val top = { t: Long -> Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f) }
        val knees = { t: Long -> Traces.angledImage(t, elbowDegrees = 175f, kneeDegrees = 70f) }
        gate.onFrame(top(0), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(0))
        assertFalse(gate.onFrame(top(2_000), 2_000, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(2_000)))
    }

    /** [image] with the near leg read below the hand — a knee and ankle under a hand that is on the floor. */
    private fun legsBelowHand(image: ImagePose): ImagePose {
        val landmarks = image.frame.landmarks.toMutableList()
        val wrist = landmarks[PoseIndex.LEFT_WRIST]
        for (i in listOf(PoseIndex.LEFT_KNEE, PoseIndex.LEFT_ANKLE)) landmarks[i] = landmarks[i].copy(y = wrist.y + 0.3f)
        return image.copy(frame = image.frame.copy(landmarks = landmarks))
    }

    @Test
    fun `legs read below the hands are a misread, not knees down - straight legs still count`() {
        // On the Seeker, 27 Sep: honest reps with the knee at 174-178° were refused
        // "Knees off the floor" because the legs read 2.6-6.8 arm-lengths *below* the
        // hands — which are on the floor. Impossible, so it is unreadable, and the
        // knee angle, which saw straight legs, decides.
        val image = legsBelowHand(Traces.angledImage(1_000, elbowDegrees = 170f, kneeDegrees = 178f))
        assertEquals(null, Angles.legsAboveHandsInImage(image, Side.LEFT))
        assertTrue(kneesOffFloor(knee = 176f, legsAboveHands = null, rules = RepRules()))
        // Knees really down still read above zero (0.32-0.39) and are still refused.
        assertEquals(0, angledSet(kneeDegrees = 70f).first)
    }

    @Test
    fun `a leg a little bent on the toes passes the bent-knee line when it came down near straight, a knee push-up never does`() {
        val rules = RepRules()
        // 3 Oct, a full rep on the toes late in a set: push knee 130°, legs 0.47, down at 135°.
        assertTrue(kneesOffFloor(knee = 130f, legsAboveHands = 0.47f, rules = rules, descentKnee = 135f))
        // The one knee push-up recorded over 120° on the push was lowered onto the knee at 104°.
        assertFalse(kneesOffFloor(knee = 126f, legsAboveHands = 0.41f, rules = rules, descentKnee = 104f))
        // Lowered on the toes, pushed from the knees with the feet raised (90–112° on the push).
        assertFalse(kneesOffFloor(knee = 110f, legsAboveHands = 0.44f, rules = rules, descentKnee = 150f))
        // The hold has no way down to read: the push line alone.
        assertFalse(kneesOffFloor(knee = 130f, legsAboveHands = 0.47f, rules = rules))
    }

    /** [image] with the near knee gone from the picture, as when MediaPipe loses it. */
    private fun withoutKnee(image: ImagePose): ImagePose {
        val landmarks = image.frame.landmarks.toMutableList()
        landmarks[PoseIndex.LEFT_KNEE] = landmarks[PoseIndex.LEFT_KNEE].copy(presence = 0f)
        return image.copy(frame = image.frame.copy(landmarks = landmarks))
    }

    @Test
    fun `a staked push with no knee in the picture is not credited - the knees were never seen`() {
        for (stakes in Stakes.entries) {
            val counter = RepCounter(view = CameraView.OBLIQUE, stakes = stakes)
            val hints = mutableListOf<RepHint?>()
            for (frame in Traces.set(count = 10)) {
                // From the knees, shins down, the knee never seen.
                val image = withoutKnee(Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees = SHINS_DOWN))
                (counter.onFrame(frame, image) as? RepEvent.Discarded)?.let { hints += it.hint }
            }
            // Every sitting since 8 Oct: the Daily 25 keeps the legs in view as Easy does (002 FR-007).
            assertEquals(0, counter.total, "$stakes")
            assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.LEGS_IN_VIEW }, "$stakes: got $hints")
        }
    }

    @Test
    fun `a leg hidden through the push back up is not credited, even though those frames cannot be read at all`() {
        // Knee and ankle gone: no body line, so the whole frame is unreadable and dropped.
        fun withoutLeg(image: ImagePose): ImagePose {
            val landmarks = image.frame.landmarks.toMutableList()
            for (i in listOf(PoseIndex.LEFT_KNEE, PoseIndex.LEFT_ANKLE)) landmarks[i] = landmarks[i].copy(presence = 0f)
            return image.copy(frame = image.frame.copy(landmarks = landmarks))
        }
        for (stakes in Stakes.entries) {
            val counter = RepCounter(view = CameraView.OBLIQUE, stakes = stakes)
            val hints = mutableListOf<RepHint?>()
            var previous = 180f
            for (frame in Traces.set(count = 10)) {
                val elbow = Angles.elbow(frame)!!
                // Visible down to the bottom, hidden on the way up, back in view at the top.
                val rising = elbow > previous && elbow in 80f..165f
                previous = elbow
                val image = Traces.angledImage(frame.timestampMillis, elbow)
                (counter.onFrame(frame, if (rising) withoutLeg(image) else image) as? RepEvent.Discarded)?.let { hints += it.hint }
            }
            // Every sitting since 8 Oct: the Daily 25 keeps the legs in view as Easy does (002 FR-007).
            assertEquals(0, counter.total, "$stakes")
            assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.LEGS_IN_VIEW }, "$stakes: got $hints")
        }
    }

    @Test
    fun `a knee hidden on the way down is not credited, though the push shows it`() {
        for (stakes in Stakes.entries) {
            val counter = RepCounter(view = CameraView.OBLIQUE, stakes = stakes)
            val hints = mutableListOf<RepHint?>()
            var previous = 180f
            for (frame in Traces.set(count = 10)) {
                val elbow = Angles.elbow(frame)!!
                // Hidden while lowering — onto the knees, for all the camera knows — and back for the push.
                val lowering = elbow < previous && elbow in 80f..165f
                previous = elbow
                val image = Traces.angledImage(frame.timestampMillis, elbow)
                (counter.onFrame(frame, if (lowering) withoutKnee(image) else image) as? RepEvent.Discarded)?.let { hints += it.hint }
            }
            // Every sitting since 8 Oct: the Daily 25 keeps the legs in view as Easy does (002 FR-007).
            assertEquals(0, counter.total, "$stakes")
            assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.LEGS_IN_VIEW }, "$stakes: got $hints")
        }
    }

    @Test
    fun `the plank is read whatever the arms do - a Hard countdown stops on knees down once the arms bend`() {
        // The gate's own blocker names the arms first; a member who starts down on "1" and
        // drops the knees would otherwise show only ARMS, and the countdown run on (PR #14 review).
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.STAKED)
        fun at(elbow: Float, knee: Float): Pair<HoldBlocker?, HoldBlocker?> {
            gate.onFrame(Traces.frame(0, elbowDegrees = elbow, bodyLineDegrees = 180f), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = Traces.angledImage(0, elbow, kneeDegrees = knee))
            return gate.blocker to gate.plankBlocker
        }
        assertEquals(HoldBlocker.ARMS to HoldBlocker.KNEES, at(elbow = 120f, knee = 70f))
        assertEquals(HoldBlocker.ARMS to null, at(elbow = 120f, knee = 178f), "going down on the toes: the plank holds")
        assertEquals(null to null, at(elbow = 175f, knee = 178f))
    }

    @Test
    fun `with the pills green the ready hold says what the position lacks`() {
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.STAKED)
        fun blockerAt(elbow: Float, knee: Float = 178f, bodyLine: Float = 180f, image: (ImagePose) -> ImagePose = { it }): HoldBlocker? {
            gate.onFrame(Traces.frame(0, elbowDegrees = elbow, bodyLineDegrees = bodyLine), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = image(Traces.angledImage(0, elbow, kneeDegrees = knee)))
            return gate.blocker
        }
        assertEquals(null, blockerAt(elbow = 175f), "the top of a push-up, on the toes: nothing is missing")
        assertEquals(HoldBlocker.ARMS, blockerAt(elbow = 120f))
        assertEquals(HoldBlocker.KNEES, blockerAt(elbow = 175f, knee = 70f))
        // The knee put on the ankle, as the model does in loose trousers: not a knee at all.
        val collapsed = { image: ImagePose ->
            val l = image.frame.landmarks.toMutableList()
            l[PoseIndex.LEFT_KNEE] = l[PoseIndex.LEFT_ANKLE]
            image.copy(frame = image.frame.copy(landmarks = l))
        }
        assertEquals(HoldBlocker.KNEES_UNREADABLE, blockerAt(elbow = 175f, image = collapsed))
        // Not a word about the position until the pills are green.
        gate.onFrame(Traces.frame(0, 120f, 180f), 0, pillsGreen = false, view = CameraView.OBLIQUE, image = Traces.angledImage(0, 120f))
        assertEquals(null, gate.blocker)
    }

    @Test
    fun `the staked ready position needs the knees in the picture`() {
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.STAKED)
        val top = { t: Long -> Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f) }
        val unseen = { t: Long -> withoutKnee(Traces.angledImage(t, elbowDegrees = 175f)) }
        gate.onFrame(top(0), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = unseen(0))
        assertFalse(gate.onFrame(top(2_000), 2_000, pillsGreen = true, view = CameraView.OBLIQUE, image = unseen(2_000)))
    }

    /**
     * [image] with the near arm lost and the far arm guessed straight — what
     * MediaPipe does for a moment when the near arm is hidden.
     */
    private fun nearArmLost(image: ImagePose): ImagePose {
        val l = image.frame.landmarks.toMutableList()
        val shoulder = l[PoseIndex.LEFT_SHOULDER]
        listOf(PoseIndex.LEFT_ELBOW, PoseIndex.LEFT_WRIST).forEach { l[it] = l[it].copy(presence = 0f) }
        l[PoseIndex.RIGHT_SHOULDER] = shoulder.copy(presence = 1f)
        l[PoseIndex.RIGHT_ELBOW] = shoulder.copy(y = shoulder.y + 0.3f, presence = 1f)
        l[PoseIndex.RIGHT_WRIST] = shoulder.copy(y = shoulder.y + 0.6f, presence = 1f)
        // The far leg guessed where the near one is, so the far side reads longer.
        listOf(PoseIndex.LEFT_HIP to PoseIndex.RIGHT_HIP, PoseIndex.LEFT_KNEE to PoseIndex.RIGHT_KNEE, PoseIndex.LEFT_ANKLE to PoseIndex.RIGHT_ANKLE)
            .forEach { (near, far) -> l[far] = l[near] }
        return image.copy(frame = image.frame.copy(landmarks = l))
    }

    @Test
    fun `a frame where the near arm is lost is skipped, not read from the far side`() {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
        val verdicts = mutableListOf<RepEvent>()
        Traces.set(count = 10).forEachIndexed { i, frame ->
            val image = Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!)
            counter.onFrame(frame, if (i > 10 && i % 4 == 0) nearArmLost(image) else image)?.let { verdicts += it }
        }
        // Read from the far side, its arm guessed straight mid-descent would cut reps short
        // ("Go lower"). Skipped, every rep reaches the bottom and is judged. A skipped frame is
        // also one the legs were not seen in, and on these coarse synthetic reps — three frames
        // to a push — that is over the quarter every sitting allows since 8 Oct (002 FR-007).
        assertEquals(10, verdicts.size)
        assertTrue(verdicts.none { it is RepEvent.Discarded && it.hint == RepHint.GO_LOWER }, "got $verdicts")
    }

    /** Five reps topping out at [repTop], after a countdown held at [held] (or none). */
    private fun countAt45(held: Float?, repTop: Float): Int {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
        var start = 0L
        if (held != null) {
            Traces.settleAtTop(0, millis = 1_000, elbow = held).forEach { counter.prime(it, Traces.angledImage(it.timestampMillis, held)) }
            start = 1_100
        }
        Traces.set(count = 5, top = repTop).forEach { frame ->
            val at = frame.copy(timestampMillis = frame.timestampMillis + start)
            counter.onFrame(at, Traces.angledImage(at.timestampMillis, Angles.elbow(frame)!!))
        }
        return counter.total
    }

    @Test
    fun `at 45 degrees the top of a rep is the member's own held arm, never under 148 nor over the rule`() {
        // From 45–50° on the floor a locked-out arm reads 150–158°: held at 158, reps back to 154 count.
        assertEquals(5, countAt45(held = 158f, repTop = 154f))
        // From a more frontal spot the same arm reads 175°: there the rule's own 160° stands, and 154° is not a lockout.
        assertEquals(0, countAt45(held = 175f, repTop = 154f))
        assertEquals(5, countAt45(held = 175f, repTop = 170f))
        // The floor: an arm held bent does not buy a lower line than 148°.
        assertEquals(0, countAt45(held = 150f, repTop = 144f))
        // No countdown to read (never so on the phone): the rule's own top.
        assertEquals(0, countAt45(held = null, repTop = 154f))
    }

    @Test
    fun `a descent started during the countdown still counts - the counter follows the countdown`() {
        val frames = Traces.set(count = 1)
        val go = frames.size * 2 / 5 // "go" lands part-way down
        fun run(primed: Boolean): Int {
            val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
            frames.forEachIndexed { i, frame ->
                val image = Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!)
                when {
                    i < go && primed -> counter.prime(frame, image)
                    i < go -> Unit
                    else -> counter.onFrame(frame, image)
                }
            }
            return counter.total
        }
        assertEquals(0, run(primed = false), "a counter that starts mid-descent loses the rep")
        assertEquals(1, run(primed = true))
    }

    @Test
    fun `a whole rep during the countdown is not counted before go`() {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.DAILY)
        val frames = Traces.set(count = 2)
        val firstRepDone = Traces.set(count = 1).size // the countdown ends as the first rep does
        frames.forEachIndexed { i, frame ->
            val image = Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!)
            if (i < firstRepDone) assertEquals(null, counter.prime(frame, image)) else counter.onFrame(frame, image)
        }
        assertEquals(1, counter.total)
    }

    /** What a staked sitting calibrated on, after a countdown held with the knee at [countdownKnee] and one rep. */
    private fun calibratedAfterCountdown(countdownKnee: Float): CycleReadout? {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.STAKED)
        Traces.settleAtTop(0, millis = 1_000, elbow = 170f).forEach { counter.prime(it, Traces.angledImage(it.timestampMillis, 170f, kneeDegrees = countdownKnee)) }
        Traces.set(count = 1, top = 170f).forEach { frame ->
            val at = frame.copy(timestampMillis = frame.timestampMillis + 1_100)
            counter.onFrame(at, Traces.angledImage(at.timestampMillis, Angles.elbow(frame)!!))
        }
        return counter.lastCycle
    }

    @Test
    fun `given a staked countdown held on the knees, when counting starts, then it has set no plank to measure reps against`() {
        // On the toes the countdown is this person's plank.
        val toes = calibratedAfterCountdown(countdownKnee = 178f)
        assertTrue(toes?.heldLegsAboveHands != null, "on the toes")
        // Kneeling through it must not set a kneeling reference that knee reps then match (review on #14).
        val kneeling = calibratedAfterCountdown(countdownKnee = 80f)
        assertNull(kneeling?.heldLegsAboveHands)
        assertNull(kneeling?.heldPlacementRatio)
    }

    @Test
    fun `given an Easy Holdout, when push-ups come from the knees at 45 degrees, then they count where Hard refuses them`() {
        fun count(stakes: Stakes): Int {
            val counter = RepCounter(view = CameraView.OBLIQUE, stakes = stakes)
            for (frame in Traces.set(count = 10)) {
                counter.onFrame(frame, Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees = 70f))
            }
            return counter.total
        }
        assertEquals(10, count(Stakes.EASY), "Easy counts push-ups from the knees (H35)")
        assertEquals(0, count(Stakes.STAKED), "Hard does not")
    }

    /** Ten reps at 45° under [stakes], the knee at [kneeDegrees] with the lower leg raised, shoulder–hip–knee at [hipDegrees]. */
    private fun kneesSet(stakes: Stakes, kneeDegrees: Float, hipDegrees: Float = 180f): Pair<Int, List<RepHint?>> {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = stakes)
        val hints = mutableListOf<RepHint?>()
        for (frame in Traces.set(count = 10)) {
            val image = Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees, hipDegrees = hipDegrees)
            (counter.onFrame(frame, image) as? RepEvent.Discarded)?.let { hints += it.hint }
        }
        return counter.total to hints
    }

    @Test
    fun `a knee push-up with the lower legs raised is straight from shoulder to knee, and bent from shoulder to ankle`() {
        val image = Traces.angledImage(0, elbowDegrees = 175f, kneeDegrees = 60f)
        assertTrue(Angles.bodyLineToKneeInImage(image, Side.LEFT)!! > 178f)
        assertTrue(Angles.bodyLineInImage(image, Side.LEFT)!! < RepRules().angledBodyLineFloorDegrees)
    }

    @Test
    fun `the world reading takes the knee line too where knees count, and the ankle line alone where they do not`() {
        // Shoulder–hip–ankle bent to 120°, the knees straight on along the trunk.
        val bent = Traces.frame(0, elbowDegrees = 175f, bodyLineDegrees = 120f)
        val landmarks = bent.landmarks.toMutableList()
        for (knee in listOf(PoseIndex.LEFT_KNEE, PoseIndex.RIGHT_KNEE)) landmarks[knee] = Landmark(0f, -2f, 0f, 1f)
        val frame = bent.copy(landmarks = landmarks)
        assertEquals(180f, ViewReading.of(CameraView.SIDE, frame, null, bodyLineToKnee = true)!!.posture, 0.5f)
        assertEquals(120f, ViewReading.of(CameraView.SIDE, frame, null)!!.posture, 0.5f)
    }

    @Test
    fun `given an Easy Holdout, when push-ups come from the knees with the lower legs raised, then they count - the body line runs to the knee`() {
        // Codex P1 on PR #18: read shoulder–hip–ankle, raised or crossed lower legs
        // pulled every rep under the 140° floor and an Easy member following the
        // rule on the join screen was refused "Keep your body straight" (FR-021a).
        val (count, hints) = kneesSet(Stakes.EASY, kneeDegrees = 60f)
        assertEquals(10, count)
        assertTrue(hints.isEmpty(), "expected no hints, got $hints")
    }

    @Test
    fun `given a Hard Holdout, when the same push-ups come from the knees with the lower legs raised, then they are refused - knees down`() {
        val (count, hints) = kneesSet(Stakes.STAKED, kneeDegrees = 60f)
        assertEquals(0, count)
        assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.UP_ON_YOUR_TOES }, "got $hints")
    }

    @Test
    fun `given an Easy Holdout, when the hips sag between shoulder and knee, then the rep is refused - keep your body straight`() {
        for (knee in listOf(60f, 178f)) {
            val (count, hints) = kneesSet(Stakes.EASY, kneeDegrees = knee, hipDegrees = 125f)
            assertEquals(0, count, "knee $knee")
            assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.KEEP_BODY_STRAIGHT }, "knee $knee: got $hints")
        }
    }

    @Test
    fun `given a Daily 25 at 45 degrees, when push-ups come from the knees with the lower legs raised, then they count`() {
        val (count, hints) = kneesSet(Stakes.DAILY, kneeDegrees = 60f)
        assertEquals(10, count)
        assertTrue(hints.isEmpty(), "expected no hints, got $hints")
    }

    @Test
    fun `given an Easy Holdout, when the member holds the start from the knees with the lower legs raised, then the hold is accepted`() {
        for ((stakes, opens) in listOf(Stakes.EASY to true, Stakes.DAILY to true, Stakes.STAKED to false)) {
            val gate = ReadyGate(holdMillis = 1_000, stakes = stakes)
            val top = { t: Long -> Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f) }
            val knees = { t: Long -> Traces.angledImage(t, elbowDegrees = 175f, kneeDegrees = 60f) }
            gate.onFrame(top(0), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(0))
            assertEquals(opens, gate.onFrame(top(2_000), 2_000, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(2_000)), "$stakes")
        }
    }

    @Test
    fun `given an Easy Holdout, when the knees leave the picture, then the rep is not credited - the legs stay in view`() {
        val counter = RepCounter(view = CameraView.OBLIQUE, stakes = Stakes.EASY)
        val hints = mutableListOf<RepHint?>()
        for (frame in Traces.set(count = 10)) {
            val image = withoutKnee(Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees = 70f))
            (counter.onFrame(frame, image) as? RepEvent.Discarded)?.let { hints += it.hint }
        }
        assertEquals(0, counter.total)
        assertTrue(hints.isNotEmpty() && hints.all { it == RepHint.LEGS_IN_VIEW }, "got $hints")
    }

    @Test
    fun `given an Easy Holdout, when the phone stands more frontal than about 40 degrees, then the sitting does not start`() {
        // A placement ratio of 1.0 — the median of every 30° recording — passes
        // Hard's camera check and is refused for Easy, where it let cobras count.
        val frontish = input(shoulderSpan = 0.4f)
        assertTrue(Preflight(stakes = Stakes.STAKED).evaluate(frontish).canStart)
        val easy = Preflight(stakes = Stakes.EASY).evaluate(frontish)
        assertFalse(easy.canStart)
        assertEquals(CameraView.FRONT, easy.view)
        // 0.75, inside the 40–60° recordings' range, starts.
        assertTrue(Preflight(stakes = Stakes.EASY).evaluate(input(shoulderSpan = 0.3f)).canStart)
    }

    @Test
    fun `given an Easy Holdout, when the member holds the start from the knees, then the hold is accepted`() {
        val gate = ReadyGate(holdMillis = 1_000, stakes = Stakes.EASY)
        val top = { t: Long -> Traces.frame(t, elbowDegrees = 175f, bodyLineDegrees = 180f) }
        val knees = { t: Long -> Traces.angledImage(t, elbowDegrees = 175f, kneeDegrees = 70f) }
        gate.onFrame(top(0), 0, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(0))
        assertTrue(gate.onFrame(top(2_000), 2_000, pillsGreen = true, view = CameraView.OBLIQUE, image = knees(2_000)))
    }

    /** The stances of every rep a Daily 25 counter at [view] counts, the knee at [kneeDegrees]. */
    private fun stances(view: CameraView, kneeDegrees: Float = 178f, hideKnee: Boolean = false): List<RepStance?> {
        val counter = RepCounter(view = view, stakes = Stakes.DAILY)
        val out = mutableListOf<RepStance?>()
        for (frame in Traces.set(count = 5)) {
            val image = if (view == CameraView.OBLIQUE) {
                Traces.angledImage(frame.timestampMillis, Angles.elbow(frame)!!, kneeDegrees = kneeDegrees).let { if (hideKnee) withoutKnee(it) else it }
            } else {
                null
            }
            (counter.onFrame(frame, image) as? RepEvent.Counted)?.let { out += it.stance }
        }
        return out
    }

    @Test
    fun `given a Daily 25 at 45 degrees, when reps are counted, then each says whether Hard would have counted it`() {
        assertEquals(List(5) { RepStance.TOES }, stances(CameraView.OBLIQUE))
        assertEquals(List(5) { RepStance.KNEES }, stances(CameraView.OBLIQUE, kneeDegrees = 70f), "counted all the same - the Daily 25 allows knees")
        // A knee never seen is not credited in the Daily 25 either since 8 Oct (002 FR-007).
        assertEquals(emptyList(), stances(CameraView.OBLIQUE, kneeDegrees = SHINS_DOWN, hideKnee = true))
    }

    @Test
    fun `given a Daily 25 head-on, when reps are counted, then no stance is claimed - the legs point into the lens`() {
        val counted = stances(CameraView.SIDE)
        assertTrue(counted.isNotEmpty())
        assertTrue(counted.all { it == null })
    }

    @Test
    fun `a cohort's form picks its stakes, and an unknown form picks none`() {
        assertEquals(Stakes.STAKED, Stakes.ofForm(0))
        assertEquals(Stakes.EASY, Stakes.ofForm(1))
        assertNull(Stakes.ofForm(2))
        assertTrue(Stakes.EASY.staked && !Stakes.EASY.judgesKnees)
        assertTrue(Stakes.STAKED.staked && Stakes.STAKED.judgesKnees)
        assertFalse(Stakes.DAILY.staked || Stakes.DAILY.judgesKnees)
    }

    @Test
    fun `the rule a member reads before staking names the placement and the knees`() {
        val rule = Stakes.STAKED_RULE.lowercase()
        assertTrue("45" in rule && "not in front" in rule, "the placement")
        assertTrue("toes" in rule && "knees" in rule, "the knees")
        assertTrue("resting on the floor" in rule, "the floor: a rep resting on it does not count")
    }

    private companion object {
        /** A push-up from the knees with the shins near the floor: shoulder–hip–ankle reads 160°, over the floor. */
        const val SHINS_DOWN = 140f
    }
}
