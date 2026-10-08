package holdout.repcounter

import java.io.File

/**
 * How today's counter does on one labelled rep, worst first: [WRONG] — a cheat
 * counted, or a rep that should count refused; [WORDS] — refused as it should
 * be, but not in the words the label calls for; [RIGHT].
 */
enum class RepState { WRONG, WORDS, RIGHT }

/** The words a refusal of each label should use, unless the reviewer chose others ("Should say"). `other` takes any. */
val LABEL_WORDS = mapOf(
    "half" to RepHint.GO_LOWER.name,
    "knees" to RepHint.UP_ON_YOUR_TOES.name,
    "floor" to RepHint.HIPS_OFF_FLOOR.name,
    "cobra" to RepHint.KEEP_BODY_STRAIGHT.name,
    "sag" to RepHint.KEEP_BODY_STRAIGHT.name,
    RepLabels.UNSEEN to RepHint.LEGS_IN_VIEW.name,
)

fun BenchRep.state(): RepState = when {
    !right -> RepState.WRONG
    counted -> RepState.RIGHT
    else -> {
        val want = wantedHint.ifEmpty { LABEL_WORDS[label].orEmpty() }
        if (want.isEmpty() || verdict == want) RepState.RIGHT else RepState.WORDS
    }
}

/** One labelled rep in the baseline: the fixture, the trace time it ended, its label, and how the counter did. */
data class RatchetRep(val fixture: String, val t: Long, val label: String, val state: RepState, val verdict: String) {
    val key: Pair<String, Long> get() = fixture to t
}

/**
 * The regression ratchet over every labelled fixture (`fixtures/rep-traces/`):
 * `baseline.csv` holds how the counter did on each labelled rep, and
 * `RepRatchetTest` fails when any rep does worse — or better, until the
 * improvement is written into the baseline (`./gradlew
 * :repcounter:updateBaseline`), so a rep put right stays right. A change can
 * no longer trade one person's honest reps for another's cheats.
 */
object Ratchet {
    const val BASELINE = "baseline.csv"

    /**
     * Today's counter on every labelled rep under [dir], a label no judged rep
     * lands near included (verdict [NOT_JUDGED]). A sitting today's app would
     * not start, or that it read in another picture than today's, is left out
     * ([notScoredBecause]).
     */
    fun today(dir: File): List<RatchetRep> {
        val reps = mutableListOf<RatchetRep>()
        for (trace in TraceFiles.under(dir)) {
            val labelsFile = TraceFiles.labels(trace).takeIf { it.exists() } ?: continue
            val name = TraceFiles.name(trace)
            val recording = Trace.parse(TraceFiles.lines(trace))
            if (recording.notScoredBecause() != null) continue
            val (judged, missing, unlabelledCounts) = benchSitting(name, recording, RepLabels.parse(labelsFile.readLines()))
            judged.forEach { reps += RatchetRep(name, it.labelT, it.label, it.state(), it.verdict) }
            // A label no judged rep lands near is a rep the counter does not see: it counts
            // nothing — wrong for a rep that should count, right for a cheat.
            for (m in missing) {
                val kind = RepLabels.kind(recording.stakes, m.label)
                val state = if (kind in RepLabels.SHOULD_COUNT) RepState.WRONG else RepState.RIGHT
                reps += RatchetRep(name, m.endMillis, kind, state, NOT_JUDGED)
            }
            // A rep counted where nobody labelled one: held as wrong, so a change that
            // starts counting between labelled reps shows up as new until it is labelled.
            unlabelledCounts.forEach { reps += RatchetRep(name, it, RepLabels.UNLABELLED_COUNT, RepState.WRONG, "counted") }
        }
        return reps.sortedWith(compareBy({ it.fixture }, { it.t }))
    }

    /** The verdict of a labelled rep today's counter does not judge as a rep at all. */
    const val NOT_JUDGED = "not-judged"

    fun read(file: File): List<RatchetRep> = if (!file.exists()) {
        emptyList()
    } else {
        file.readLines().filter { it.firstOrNull()?.isLetterOrDigit() == true && !it.startsWith("fixture,") }.map { line ->
            val (fixture, t, label, state, verdict) = line.split(",")
            RatchetRep(fixture, t.toLong(), label, RepState.valueOf(state.uppercase()), verdict)
        }
    }

    fun write(file: File, reps: List<RatchetRep>) {
        val header = listOf(
            "# holdout-rep-baseline v1 — how the counter does on every labelled rep under fixtures/rep-traces/",
            "# right: the right outcome and words; words: refused, in other words than the label calls for; wrong: counted a cheat, or did not count a rep that should (verdict not-judged: no rep seen there)",
            "# Written by ./gradlew :repcounter:updateBaseline. RepRatchetTest fails when a rep does worse than here — or better, until this is updated.",
            "fixture,t,label,state,verdict",
        )
        file.writeText((header + reps.map { "${it.fixture},${it.t},${it.label},${it.state.name.lowercase()},${it.verdict}" }).joinToString("\n", postfix = "\n"))
    }
}

/** `./gradlew :repcounter:updateBaseline` — writes today's state of every labelled fixture rep to the baseline. */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "../fixtures/rep-traces")
    val reps = Ratchet.today(dir)
    val file = File(dir, Ratchet.BASELINE)
    val before = Ratchet.read(file).associateBy { it.key }
    Ratchet.write(file, reps)
    val better = reps.count { r -> before[r.key]?.let { r.state > it.state } == true }
    val worse = reps.count { r -> before[r.key]?.let { r.state < it.state } == true }
    val added = reps.count { it.key !in before }
    println("baseline: ${reps.size} labelled reps (${reps.count { it.state == RepState.RIGHT }} right, ${reps.count { it.state == RepState.WORDS }} words, ${reps.count { it.state == RepState.WRONG }} wrong); $better better, $worse worse, $added new since the last baseline")
}
