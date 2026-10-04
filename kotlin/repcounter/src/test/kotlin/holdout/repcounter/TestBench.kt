package holdout.repcounter

import java.io.File
import kotlin.math.abs
import kotlin.system.exitProcess

/**
 * What a person saw each rep of a sitting was, from its video. Written by
 * `scripts/label-reps.mjs` beside the trace as `<sitting>.labels.csv`:
 * `# key=value` lines, then `t,label,by,flag,hint,note,claude` — the trace
 * time the rep ended (the row its verdict was written on), the label, who set
 * it (`reviewer` or `claude`), `check` when the reviewer asks Claude to look at
 * it (`answered` once Claude has replied), the [RepHint] the reviewer would
 * want it refused with, the reviewer's
 * note and Claude's. A v1 file (`t,label,note`) reads as the reviewer's.
 */
data class RepLabels(val meta: Map<String, String>, val reps: List<Rep>) {
    data class Rep(
        val endMillis: Long,
        val label: String,
        val note: String,
        val by: String = "reviewer",
        val flagged: Boolean = false,
        val claudeNote: String = "",
        /** The [RepHint] name the reviewer wants this rep refused with; empty for no preference. */
        val hint: String = "",
    )

    val person: String get() = meta["person"]?.takeIf { it.isNotBlank() } ?: "unnamed"

    /** The reviewer ticked the sitting as done. */
    val reviewed: Boolean get() = !meta["reviewed"].isNullOrBlank()

    companion object {
        /**
         * What should count: `honest`, and in the Daily 25 a push-up from the knees,
         * which it counts by design — benched as `knees-daily`. `unsure` is left out.
         */
        const val KNEES_DAILY = "knees-daily"
        val SHOULD_COUNT = setOf("honest", KNEES_DAILY)
        val CHEATS = listOf("half", "knees", "floor", "cobra", "sag", "other")

        fun parse(lines: List<String>): RepLabels {
            val meta = lines.mapNotNull { Regex("""^#\s*(\w+)=(.*)$""").find(it)?.destructured?.let { (k, v) -> k to v.trim() } }.toMap()
            val columns = lines.firstOrNull { it.startsWith("t,") }?.let(::csvFields)
            val reps = lines.filter { it.firstOrNull()?.isDigit() == true }.map { line ->
                if (columns == null || columns.size == 3) {
                    val parts = line.split(",", limit = 3)
                    Rep(parts[0].trim().toLong(), parts[1].trim(), parts.getOrElse(2) { "" }.trim())
                } else {
                    val fields = csvFields(line)
                    fun get(name: String) = columns.indexOf(name).takeIf { it >= 0 }?.let { fields.getOrNull(it) }.orEmpty().trim()
                    Rep(get("t").toLong(), get("label"), get("note"), get("by").ifEmpty { "reviewer" }, get("flag") == "check", get("claude"), get("hint"))
                }
            }
            return RepLabels(meta, reps)
        }

        /** One CSV line: a quoted field may hold commas and doubled quotes. */
        fun csvFields(line: String): List<String> {
            val out = mutableListOf<String>()
            val cur = StringBuilder()
            var quoted = false
            var i = 0
            while (i < line.length) {
                val c = line[i]
                when {
                    quoted && c == '"' && line.getOrNull(i + 1) == '"' -> {
                        cur.append('"')
                        i++
                    }
                    quoted && c == '"' -> quoted = false
                    quoted -> cur.append(c)
                    c == '"' -> quoted = true
                    c == ',' -> {
                        out += cur.toString()
                        cur.clear()
                    }
                    else -> cur.append(c)
                }
                i++
            }
            out += cur.toString()
            return out
        }
    }
}

/** One labelled rep as the counter judges it today. */
data class BenchRep(
    val sitting: String,
    val person: String,
    val placement: String,
    val secondsIn: Float,
    val label: String,
    val note: String,
    val verdict: String,
    val by: String = "reviewer",
    val flagged: Boolean = false,
    val claudeNote: String = "",
    val wantedHint: String = "",
) {
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
        // The Daily 25 counts push-ups from the knees: there a knees label should count.
        val kind = if (recording.stakes == Stakes.DAILY && label.label == "knees") RepLabels.KNEES_DAILY else label.label
        BenchRep(name, labels.person, placement, (t - t0) / 1000f, kind, label.note, verdict, label.by, label.flagged, label.claudeNote, label.hint)
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

    println("## Gate — honest (and Daily 25 knees) counted ≥ 98 %, each cheat counted ≤ 2 %\n")
    println("| label | reps | counted | refused | rate | gate |")
    println("|---|---|---|---|---|---|")
    fun shown(label: String) = if (label == RepLabels.KNEES_DAILY) "knees (Daily 25, counts)" else label
    for (label in RepLabels.SHOULD_COUNT.toList() + RepLabels.CHEATS) {
        val group = reps.filter { it.label == label }
        if (group.isEmpty()) continue
        val counted = group.count { it.counted }
        val (rate, ok) = if (label in RepLabels.SHOULD_COUNT) {
            pct(counted, group.size) + " counted" to (counted * 100 >= group.size * 98)
        } else {
            pct(counted, group.size) + " counted" to (counted * 100 <= group.size * 2)
        }
        if (!ok) failures += shown(label)
        println("| ${shown(label)} | ${group.size} | $counted | ${group.size - counted} | $rate | ${if (ok) "pass" else "**FAIL**"} |")
    }

    println("\n## By person\n")
    println("| person | sittings | should count: counted | cheats counted |")
    println("|---|---|---|---|")
    for ((person, group) in reps.groupBy { it.person }.toSortedMap()) {
        val honest = group.filter { it.label in RepLabels.SHOULD_COUNT }
        val cheats = group.filter { it.label !in RepLabels.SHOULD_COUNT }
        println("| $person | ${group.map { it.sitting }.distinct().size} | ${honest.count { it.counted }} / ${honest.size} | ${cheats.count { it.counted }} / ${cheats.size} |")
    }

    println("\n## By sitting\n")
    println("| sitting | person | placement | should count: counted | cheats counted |")
    println("|---|---|---|---|---|")
    for ((sitting, group) in reps.groupBy { it.sitting }) {
        val honest = group.filter { it.label in RepLabels.SHOULD_COUNT }
        val cheats = group.filter { it.label !in RepLabels.SHOULD_COUNT }
        val byKind = cheats.filter { it.counted }.groupingBy { it.label }.eachCount().entries.joinToString(" ") { "${it.key}×${it.value}" }
        println("| $sitting | ${group.first().person} | ${group.first().placement} | ${honest.count { it.counted }} / ${honest.size} | ${cheats.count { it.counted }} / ${cheats.size}${if (byKind.isNotEmpty()) " ($byKind)" else ""} |")
    }

    val misses = reps.filter { !it.right }
    println("\n## Misses (${misses.size})\n")
    if (misses.isNotEmpty()) {
        println("| sitting | s into counting | label (by) | the counter | reviewer's note | Claude's note |")
        println("|---|---|---|---|---|---|")
        misses.forEach { println("| ${it.sitting} | %.1f | ${it.label} (${it.by}) | ${it.verdict} | ${it.note} | ${it.claudeNote} |".format(it.secondsIn)) }
    }

    // What the reviewer ticked "Review & fix": only these are taken up.
    fun say(name: String) = RepHint.entries.firstOrNull { it.name == name }?.message ?: name
    val flagged = reps.filter { it.flagged }
    println("\n## Ticked \"Review & fix\" by the reviewer (${flagged.size})\n")
    if (flagged.isNotEmpty()) {
        println("| sitting | s into counting | label | the counter | should say | reviewer's note | Claude's note |")
        println("|---|---|---|---|---|---|---|")
        flagged.forEach {
            println("| ${it.sitting} | %.1f | ${it.label} | ${say(it.verdict)} | ${it.wantedHint.takeIf(String::isNotEmpty)?.let(::say).orEmpty()} | ${it.note} | ${it.claudeNote} |".format(it.secondsIn))
        }
    }
    val unconfirmed = reps.count { it.by == "claude" }
    if (unconfirmed > 0) println("\n$unconfirmed labels set by Claude are not yet confirmed by a reviewer.")
    if (lost.isNotEmpty()) println("\n## Labels with no judged rep near them\n\n" + lost.joinToString("\n") { "- $it" })
    // Only what can be labelled — a sitting with its video — and each recording once:
    // pull-traces.sh copies every trace still on the phone into each new folder.
    val labelled = reps.map { it.sitting.substringAfterLast('/') }.toSet()
    val withVideo = unlabelled
        .filter { File(dir, "$it.mp4").exists() }
        .distinctBy { it.substringAfterLast('/') }
        .filter { it.substringAfterLast('/') !in labelled }
    if (withVideo.isNotEmpty()) println("\n## With video, not labelled yet (${withVideo.size})\n\n" + withVideo.joinToString(", "))
    if (unlabelled.size > withVideo.size) println("\n${unlabelled.size - withVideo.size} more traces have no labels, and no video beside them to label from (or are copies).")

    println("\n${reps.size} labelled reps in ${reps.map { it.sitting }.distinct().size} sittings, ${reps.map { it.person }.distinct().size} people. Gate: ${if (failures.isEmpty()) "pass" else "FAIL (${failures.joinToString()})"}")
    if (gate && failures.isNotEmpty()) exitProcess(1)
}
