package holdout.repcounter

import java.io.File
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * What a person saw each rep of a sitting was, from its video: the trace time
 * the rep ended (the row its verdict was written on), a label and a note.
 * Written by `scripts/label-reps.mjs` beside the trace as `<sitting>.labels.csv`.
 */
data class RepLabels(val meta: Map<String, String>, val reps: List<Rep>) {
    data class Rep(val endMillis: Long, val label: String, val note: String)

    val person: String get() = meta["person"]?.takeIf { it.isNotBlank() } ?: "unnamed"

    companion object {
        /** Only `honest` should count; `unsure` is left out. */
        val SHOULD_COUNT = setOf("honest")
        val CHEATS = listOf("half", "knees", "floor", "cobra", "sag", "other")

        fun parse(lines: List<String>): RepLabels {
            val meta = lines.mapNotNull { Regex("""^#\s*(\w+)=(.*)$""").find(it)?.destructured?.let { (k, v) -> k to v.trim() } }.toMap()
            val reps = lines.filter { it.firstOrNull()?.isDigit() == true }.map { line ->
                val parts = line.split(",", limit = 3)
                Rep(parts[0].trim().toLong(), parts[1].trim(), parts.getOrElse(2) { "" }.trim())
            }
            return RepLabels(meta, reps)
        }
    }
}

/** One labelled rep as the counter judges it today. */
data class BenchRep(val sitting: String, val person: String, val placement: String, val secondsIn: Float, val label: String, val note: String, val verdict: String) {
    val counted: Boolean get() = verdict == "counted"
    val right: Boolean get() = counted == (label in RepLabels.SHOULD_COUNT)
}

/**
 * Replays [recording] through today's counter and pairs each judged rep with
 * the label of the rep that ended on (or within [toleranceMillis] of) the same
 * frame. A label no judged rep lands near is returned in the second list:
 * the counter now cuts that stretch of the sitting into reps differently.
 */
fun benchSitting(name: String, recording: Trace.Recording, labels: RepLabels, toleranceMillis: Long = 400): Pair<List<BenchRep>, List<RepLabels.Rep>> {
    val view = recording.countingView ?: return emptyList<BenchRep>() to labels.reps
    val counter = RepCounter(view = view, stakes = recording.stakes)
    for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
    val counting = recording.countingFrames
    val t0 = counting.firstOrNull()?.world?.timestampMillis ?: return emptyList<BenchRep>() to labels.reps
    val events = counting.mapNotNull { frame ->
        counter.onFrame(frame.world, frame.image)?.let { frame.world.timestampMillis to it }
    }
    val placement = listOfNotNull(recording.placement, recording.phoneSide).joinToString(" ").ifEmpty { "?" }
    val used = mutableSetOf<RepLabels.Rep>()
    val judged = events.mapNotNull { (t, event) ->
        val label = labels.reps.filter { it !in used && abs(it.endMillis - t) <= toleranceMillis }.minByOrNull { abs(it.endMillis - t) } ?: return@mapNotNull null
        used += label
        if (label.label == "unsure") return@mapNotNull null
        val verdict = when (event) {
            is RepEvent.Counted -> "counted"
            is RepEvent.Discarded -> event.hint?.name ?: "refused"
        }
        BenchRep(name, labels.person, placement, (t - t0) / 1000f, label.label, label.note, verdict)
    }
    return judged to labels.reps.filter { it !in used && it.label != "unsure" }
}

/**
 * `./gradlew :repcounter:testBench [-Ptraces=<dir>] [-Pgate]` — every labelled
 * sitting under the folder (default `fixtures/rep-traces/`), replayed through
 * today's counter and scored against what a person saw on the video:
 *
 * - **the gate**: honest reps counted, 98 % or more; each kind of cheat
 *   counted, 2 % or less. With `-Pgate` the task fails when it is not met;
 * - **by person** and **by sitting**, so a rule tuned on one body shows up as
 *   a red row for the next;
 * - **every miss**, with where it is in the sitting and the label's note.
 *
 * Sittings without a labels file are listed, not scored: label them with
 * `node scripts/label-reps.mjs`.
 */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "../fixtures/rep-traces")
    val gate = args.getOrNull(1) == "gate"
    val traces = dir.walkTopDown().filter { it.isFile && it.name.endsWith(".trace.csv") }.sortedBy { it.path }.toList()
    require(traces.isNotEmpty()) { "no .trace.csv files under ${dir.absolutePath}" }

    val reps = mutableListOf<BenchRep>()
    val lost = mutableListOf<String>()
    val unlabelled = mutableListOf<String>()
    for (trace in traces) {
        val name = trace.relativeTo(dir).path.removeSuffix(".trace.csv").replace(File.separatorChar, '/')
        val labelsFile = File(trace.path.removeSuffix(".trace.csv") + ".labels.csv")
        if (!labelsFile.exists()) {
            unlabelled += name
            continue
        }
        val (judged, missing) = benchSitting(name, Trace.parse(trace.readLines()), RepLabels.parse(labelsFile.readLines()))
        reps += judged
        missing.forEach { lost += "$name: a ${it.label} rep ending at t=${it.endMillis} has no judged rep near it today" }
    }

    fun pct(n: Int, of: Int) = if (of == 0) "-" else "%.0f %%".format(100.0 * n / of)
    val failures = mutableListOf<String>()

    println("## Gate — honest counted ≥ 98 %, each cheat counted ≤ 2 %\n")
    println("| label | reps | counted | refused | rate | gate |")
    println("|---|---|---|---|---|---|")
    for (label in listOf("honest") + RepLabels.CHEATS) {
        val group = reps.filter { it.label == label }
        if (group.isEmpty()) continue
        val counted = group.count { it.counted }
        val (rate, ok) = if (label == "honest") {
            pct(counted, group.size) + " counted" to (counted * 100 >= group.size * 98)
        } else {
            pct(counted, group.size) + " counted" to (counted * 100 <= group.size * 2)
        }
        if (!ok) failures += label
        println("| $label | ${group.size} | $counted | ${group.size - counted} | $rate | ${if (ok) "pass" else "**FAIL**"} |")
    }

    println("\n## By person\n")
    println("| person | sittings | honest counted | cheats counted |")
    println("|---|---|---|---|")
    for ((person, group) in reps.groupBy { it.person }.toSortedMap()) {
        val honest = group.filter { it.label == "honest" }
        val cheats = group.filter { it.label != "honest" }
        println("| $person | ${group.map { it.sitting }.distinct().size} | ${honest.count { it.counted }} / ${honest.size} | ${cheats.count { it.counted }} / ${cheats.size} |")
    }

    println("\n## By sitting\n")
    println("| sitting | person | placement | honest counted | cheats counted |")
    println("|---|---|---|---|---|")
    for ((sitting, group) in reps.groupBy { it.sitting }) {
        val honest = group.filter { it.label == "honest" }
        val cheats = group.filter { it.label != "honest" }
        val byKind = cheats.filter { it.counted }.groupingBy { it.label }.eachCount().entries.joinToString(" ") { "${it.key}×${it.value}" }
        println("| $sitting | ${group.first().person} | ${group.first().placement} | ${honest.count { it.counted }} / ${honest.size} | ${cheats.count { it.counted }} / ${cheats.size}${if (byKind.isNotEmpty()) " ($byKind)" else ""} |")
    }

    val misses = reps.filter { !it.right }
    println("\n## Misses (${misses.size})\n")
    if (misses.isNotEmpty()) {
        println("| sitting | s into counting | label | the counter | note |")
        println("|---|---|---|---|---|")
        misses.forEach { println("| ${it.sitting} | %.1f | ${it.label} | ${it.verdict} | ${it.note} |".format(it.secondsIn)) }
    }
    if (lost.isNotEmpty()) println("\n## Labels with no judged rep near them\n\n" + lost.joinToString("\n") { "- $it" })
    if (unlabelled.isNotEmpty()) println("\n## Not labelled yet (${unlabelled.size})\n\n" + unlabelled.joinToString(", "))

    println("\n${reps.size} labelled reps in ${reps.map { it.sitting }.distinct().size} sittings, ${reps.map { it.person }.distinct().size} people. Gate: ${if (failures.isEmpty()) "pass" else "FAIL (${failures.joinToString()})"}")
    if (gate && failures.isNotEmpty()) exitProcess(1)
}
