package holdout.repcounter

import java.io.File
import kotlin.math.abs

/**
 * `./gradlew :repcounter:replayTraces [-Ptraces=<dir>]` — calibration from
 * labelled phone traces (spec 002 T034c). Every trace in the folder (default
 * `phone_traces/`, what `scripts/pull-traces.sh` pulls) is replayed through
 * the real counter and the real placement check, and reported:
 *
 * - **per trace**: where the phone stood (as labelled), what the placement
 *   check made of the counting frames, the placement ratio, and the count
 *   against the person's;
 * - **per labelled placement**: the ratio's 5th, 50th and 95th percentile,
 *   which is what the check's `sideRatio` and `frontRatio` are set from, and
 *   how many traces counted within one rep of the person.
 *
 * Nothing here decides anything: it prints what the thresholds should be
 * judged against. Traces without a `placement` label are listed but left out
 * of the per-placement table.
 */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "../phone_traces")
    val files = dir.listFiles { f -> f.name.endsWith(".trace.csv") }?.sortedBy { it.name }.orEmpty()
    require(files.isNotEmpty()) { "no .trace.csv files in ${dir.absolutePath}" }

    val detail = args.getOrNull(1) == "detail"
    val cycles = mutableMapOf<String, List<String>>()

    data class Row(val name: String, val placement: String?, val side: String?, val stakes: Stakes, val ratios: List<Float>, val views: Map<CameraView?, Int>, val counted: Int, val expected: Int?, val hints: Map<String, Int>)

    val rows = files.mapNotNull { file ->
        val recording = runCatching { Trace.parse(file.readLines()) }.getOrNull() ?: return@mapNotNull null
        val counting = recording.countingFrames
        // A placement neither stakes accepts (square side-on) never starts
        // counting: its set-up frames are all there is to measure it by.
        val measured = counting.ifEmpty { recording.frames.filter { it.step == "PREFLIGHT" } }.ifEmpty { return@mapNotNull null }
        val preflight = Preflight(stakes = recording.stakes)
        val xScale = measured.first().image.let { Aspect.xScale(it.width, it.height) }
        val ratios = measured.mapNotNull { preflight.placementRatio(it.image.frame, xScale) }
        val views = measured.groupingBy { preflight.classifyView(it.image.frame, xScale) }.eachCount()

        val hints = mutableMapOf<String, Int>()
        var total = 0
        val view = recording.countingView
        val counter = view?.let { runCatching { RepCounter(view = it, stakes = recording.stakes) }.getOrNull() }
        if (counter != null) {
            for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
            val lines = mutableListOf<String>()
            val t0 = counting.first().world.timestampMillis
            fun f(v: Float?) = v?.let { "%.2f".format(it) } ?: "-"
            for (frame in counting) {
                val event = counter.onFrame(frame.world, frame.image) ?: continue
                (event as? RepEvent.Discarded)?.let { hints.merge(it.hint?.name ?: "none", 1, Int::plus) }
                val c = counter.lastCycle ?: continue
                val what = if (event is RepEvent.Counted) "counted" else (event as RepEvent.Discarded).hint?.name ?: "none"
                lines += "| %.1f | %s | %.0f | %.0f | %s | %s | %s | %.0f%% | %s | %s | %d |".format(
                    (frame.world.timestampMillis - t0) / 1000f, what, c.top, c.minElbow, f(c.shoulderOverHand), f(c.pushKnee), f(c.pushLegsAboveHands),
                    c.legsUnseenShare * 100, f(c.posture), f(c.pushPosture), c.cycleMillis,
                )
            }
            cycles[file.name] = lines
            total = counter.total
        } else {
            hints["never counted"] = 1
        }
        Row(file.name.removeSuffix(".trace.csv"), recording.placement, recording.phoneSide, recording.stakes, ratios, views, total, recording.expectedReps, hints)
    }

    fun pct(values: List<Float>, p: Double): String = values.sorted().let { s -> if (s.isEmpty()) "-" else "%.2f".format(s[((s.size - 1) * p).toInt()]) }
    fun share(views: Map<CameraView?, Int>): String {
        val total = views.values.sum().coerceAtLeast(1)
        return listOf(CameraView.FRONT, CameraView.OBLIQUE, CameraView.SIDE, null)
            .joinToString(" ") { v -> "${v?.name?.take(4)?.lowercase() ?: "none"} ${100 * (views[v] ?: 0) / total}%" }
    }

    println("## Traces\n")
    println("| trace | placement | stakes | ratio p5 / p50 / p95 | placement check on counting frames | counted / person | refused |")
    println("|---|---|---|---|---|---|---|")
    for (r in rows) {
        val where = listOfNotNull(r.placement?.let { if (it.all(Char::isDigit)) "$it°" else it }, r.side).joinToString(" ").ifEmpty { "?" }
        val accuracy = r.expected?.let { "${r.counted} / $it" } ?: "${r.counted} / ?"
        println("| ${r.name} | $where | ${r.stakes.name.lowercase()} | ${pct(r.ratios, 0.05)} / ${pct(r.ratios, 0.5)} / ${pct(r.ratios, 0.95)} | ${share(r.views)} | $accuracy | ${r.hints.entries.joinToString(" ") { "${it.key}×${it.value}" }} |")
    }

    if (detail) {
        for ((name, lines) in cycles) {
            println("\n### $name\n")
            println("| s | verdict | top | min elbow | shoulder over hand | push knee | push legs | knee unseen | body | push body | ms |")
            println("|---|---|---|---|---|---|---|---|---|---|---|")
            lines.forEach(::println)
        }
    }

    val labelled = rows.filter { it.placement != null }
    println("\n## By labelled placement\n")
    if (labelled.isEmpty()) {
        println("No trace carries a placement label yet: label sittings in the debug build's \"What did you just do?\" form.")
        return
    }
    println("| placement | traces | ratio p5 / p50 / p95 (all counting frames) | placement check | within 1 rep of the person |")
    println("|---|---|---|---|---|")
    for ((placement, group) in labelled.groupBy { it.placement!! }.toSortedMap(compareBy { Trace.PLACEMENTS.indexOf(it) })) {
        val ratios = group.flatMap { it.ratios }
        val views = group.flatMap { it.views.entries }.groupBy({ it.key }, { it.value }).mapValues { it.value.sum() }
        val judged = group.filter { it.expected != null }
        val within = judged.count { abs(it.counted - it.expected!!) <= 1 }
        println("| $placement | ${group.size} | ${pct(ratios, 0.05)} / ${pct(ratios, 0.5)} / ${pct(ratios, 0.95)} | ${share(views)} | $within of ${judged.size} |")
    }
    println("\nIn force: side ≤ 0.25 < 45° < 1.55 ≤ head-on (Preflight's sideRatio and frontRatio, in 9:16 units).")
}
