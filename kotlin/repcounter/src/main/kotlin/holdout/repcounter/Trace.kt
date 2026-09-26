package holdout.repcounter

/**
 * A recorded sitting: every frame's landmarks, and what the counter made of
 * them — the raw material for tuning recognition against real bodies.
 *
 * A debug build writes one per sitting on the phone; a developer pulls it with
 * `scripts/pull-traces.sh` and commits the useful ones under
 * `fixtures/rep-traces/`, where [RecordedTracesTest] replays them and checks
 * the count against what a person counted. **Landmarks only — never a pixel of
 * the picture** (constitution X): a trace is thirty-odd numbers per frame.
 *
 * ```
 * # holdout-trace v2
 * # stakes=STAKED width=720 height=1280 expectedReps=?
 * # landmarks=11,12,13,14,15,16,23,24,25,26,27,28
 * t,step,view,<world x y z p per landmark>,<image x y p per landmark>,elbow,posture,knee,event
 * ```
 *
 * `step` is the sitting's step when the frame arrived; `view` is what the
 * pre-flight check saw, or during counting the view the sitting is locked to;
 * `event` is `counted`, `discarded:<hint>`, or empty.
 */
object Trace {
    const val MAGIC = "# holdout-trace v2"

    /** The joints the rule reads, in file order. */
    val LANDMARKS = intArrayOf(
        PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER,
        PoseIndex.LEFT_ELBOW, PoseIndex.RIGHT_ELBOW,
        PoseIndex.LEFT_WRIST, PoseIndex.RIGHT_WRIST,
        PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP,
        PoseIndex.LEFT_KNEE, PoseIndex.RIGHT_KNEE,
        PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE,
        // Recorded since 2026-09-25; older traces simply do not list it.
        PoseIndex.NOSE,
    )

    fun header(stakes: Stakes, width: Int, height: Int): String = buildString {
        appendLine(MAGIC)
        appendLine("# stakes=${stakes.name} width=$width height=$height expectedReps=?")
        appendLine("# landmarks=${LANDMARKS.joinToString(",")}")
        append("t,step,view")
        for (i in LANDMARKS) append(",w${i}x,w${i}y,w${i}z,w${i}p")
        for (i in LANDMARKS) append(",i${i}x,i${i}y,i${i}p")
        appendLine(",elbow,posture,knee,event")
    }

    fun row(
        timestampMillis: Long,
        step: String,
        view: CameraView?,
        world: PoseFrame,
        image: PoseFrame,
        readout: Readout?,
        event: RepEvent?,
    ): String = buildString {
        append(timestampMillis).append(',').append(step).append(',').append(view?.name ?: "")
        for (i in LANDMARKS) {
            val p = world[i]
            append(',').append(p?.x.fmt()).append(',').append(p?.y.fmt()).append(',').append(p?.z.fmt()).append(',').append(p?.presence.fmt(3))
        }
        for (i in LANDMARKS) {
            val p = image[i]
            append(',').append(p?.x.fmt()).append(',').append(p?.y.fmt()).append(',').append(p?.presence.fmt(3))
        }
        append(',').append(readout?.elbow.fmt(1))
        append(',').append(readout?.posture.fmt(1))
        append(',').append(readout?.knee.fmt(1))
        append(',').append(
            when (event) {
                is RepEvent.Counted -> "counted"
                is RepEvent.Discarded -> "discarded:${event.hint?.name ?: "none"}"
                null -> ""
            },
        )
        appendLine()
    }

    /** What a person says they did in a sitting, by movement — the ground truth a trace is judged against. */
    val MOVEMENTS = listOf("honest", "half", "knees", "cobra", "other")

    /**
     * [lines] with a person's account written into the header: the expected
     * count, what was done by movement, and a note. Written on the phone right
     * after the sitting, so a pulled trace is already a test.
     *
     * The note is kept to one header line and cannot forge a header fact:
     * `=` becomes `:`.
     */
    fun label(lines: List<String>, expectedReps: Int, done: Map<String, Int>, note: String): List<String> {
        require(lines.firstOrNull() == MAGIC) { "not a holdout trace v2" }
        val out = lines.toMutableList()
        out[1] = out[1].replace(Regex("""expectedReps=\S+"""), "expectedReps=$expectedReps")
        val account = MOVEMENTS.joinToString(",") { "$it:${done[it] ?: 0}" }
        val extra = mutableListOf("# done=$account")
        val clean = note.replace(Regex("""\s+"""), " ").replace('=', ':').trim()
        if (clean.isNotEmpty()) extra += "# note=$clean"
        out.addAll(2, extra)
        return out
    }

    private fun Float?.fmt(decimals: Int = 4): String = this?.let { "%.${decimals}f".format(java.util.Locale.ROOT, it) } ?: ""

    /** One frame read back from a trace. */
    data class Frame(
        val timestampMillis: Long,
        val step: String,
        val view: CameraView?,
        val world: PoseFrame,
        val image: ImagePose,
        val event: String,
    )

    /** A whole trace read back: its header facts and its frames. */
    data class Recording(
        val stakes: Stakes,
        /** What a person counted watching the set, or null when nobody has yet. */
        val expectedReps: Int?,
        val frames: List<Frame>,
    ) {
        /** The view counting was locked to — the first counting frame's. */
        val countingView: CameraView? get() = frames.firstOrNull { it.step == "COUNTING" }?.view

        val countingFrames: List<Frame> get() = frames.filter { it.step == "COUNTING" }

        /**
         * The countdown that led into counting — the last unbroken run of
         * `COUNTDOWN` frames before the first `COUNTING` one. The app's counter
         * follows it without judging (`RepCounter.prime`), so a replay must too.
         */
        val countdownFrames: List<Frame> get() {
            val first = frames.indexOfFirst { it.step == "COUNTING" }
            if (first <= 0) return emptyList()
            var start = first
            while (start > 0 && frames[start - 1].step == "COUNTDOWN") start--
            return frames.subList(start, first)
        }
    }

    fun parse(lines: List<String>): Recording {
        require(lines.firstOrNull() == MAGIC) { "not a holdout trace v2" }
        val facts = lines.takeWhile { it.startsWith("#") }
            .flatMap { it.removePrefix("#").trim().split(' ') }
            .mapNotNull { it.split('=', limit = 2).takeIf { kv -> kv.size == 2 }?.let { kv -> kv[0] to kv[1] } }
            .toMap()
        val width = facts.getValue("width").toInt()
        val height = facts.getValue("height").toInt()
        val indices = facts.getValue("landmarks").split(',').map { it.toInt() }
        val body = lines.dropWhile { it.startsWith("#") }.drop(1).filter { it.isNotBlank() }

        val frames = body.map { line ->
            val v = line.split(',')
            fun f(i: Int) = v[i].toFloatOrNull() ?: 0f
            val world = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }
            val image = MutableList(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) }
            indices.forEachIndexed { k, index ->
                val w = 3 + k * 4
                world[index] = Landmark(f(w), f(w + 1), f(w + 2), f(w + 3))
                val im = 3 + indices.size * 4 + k * 3
                image[index] = Landmark(f(im), f(im + 1), 0f, f(im + 2))
            }
            val t = v[0].toLong()
            Frame(
                timestampMillis = t,
                step = v[1],
                view = v[2].takeIf { it.isNotEmpty() }?.let(CameraView::valueOf),
                world = PoseFrame(t, world),
                image = ImagePose(PoseFrame(t, image), width, height),
                event = v.last(),
            )
        }
        return Recording(
            stakes = Stakes.valueOf(facts.getValue("stakes")),
            expectedReps = facts["expectedReps"]?.toIntOrNull(),
            frames = frames,
        )
    }
}
