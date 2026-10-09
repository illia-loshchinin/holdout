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
    val files = dir.listFiles { f -> TraceFiles.isTrace(f) }?.sortedBy { it.name }.orEmpty()
    require(files.isNotEmpty()) { "no .trace.csv files in ${dir.absolutePath}" }

    val detail = args.getOrNull(1) == "detail"
    val cycles = mutableMapOf<String, List<String>>()

    fun pct0(values: List<Float>, p: Double): String = values.sorted().let { s -> if (s.isEmpty()) "-" else "%.2f".format(s[((s.size - 1) * p).toInt()]) }

    data class Row(val name: String, val placement: String?, val side: String?, val stakes: Stakes, val ratios: List<Float>, val views: Map<CameraView?, Int>, val counted: Int, val expected: Int?, val hints: Map<String, Int>)

    val rows = files.mapNotNull { file ->
        val recording = runCatching { Trace.parse(TraceFiles.lines(file)) }.getOrNull() ?: return@mapNotNull null
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
                lines += "| %.1f | %s | %.0f | %.0f | %s | %s | %s | %s | %s | %.0f%% | %s | %s | %d | %s |".format(
                    (frame.world.timestampMillis - t0) / 1000f, what, c.top, c.minElbow, f(c.shoulderOverHand), f(c.pushKnee), f(c.pushLegsAboveHands), f(c.descentKnee), f(c.descentLegsAboveHands),
                    c.legsUnseenShare * 100, f(c.posture), f(c.pushPosture), c.cycleMillis, f(c.hipRise),
                )
            }
            val held = recording.countdownFrames.mapNotNull { preflight.placementRatio(it.image.frame, xScale) }
            cycles[file.name] = listOf("held ratio p5/p50/p95: ${pct0(held, 0.05)} / ${pct0(held, 0.5)} / ${pct0(held, 0.95)} placement=${recording.placement} ${recording.phoneSide}") + lines
            total = counter.total
        } else {
            hints["never counted"] = 1
        }
        if (detail && recording.stakes == Stakes.STAKED) {
            // The ready hold, as the gate judged it on set-up frames the placement check passed.
            val rules = RepRules()
            val setUp = recording.frames.filter { it.step == "PREFLIGHT" && it.view == CameraView.OBLIQUE }
                .mapNotNull { ViewReading.of(CameraView.OBLIQUE, it.world, it.image) as? ViewReading.Angled }
            if (setUp.isNotEmpty()) {
                fun share(n: Int) = "${100 * n / setUp.size}%"
                val arms = setUp.count { it.elbow < rules.obliqueTopDegrees }
                val body = setUp.count { !postureHeld(CameraView.OBLIQUE, it, it.posture, rules) }
                val unseen = setUp.count { it.knee == null && it.legsAboveHands == null }
                val kneeLow = setUp.count { k -> k.knee?.let { it < rules.kneesDownDegrees } == true }
                val legsLow = setUp.count { k -> k.legsAboveHands?.let { it < rules.minLegsAboveHands } == true }
                val bent = setUp.count { !kneesOffFloor(it.knee, it.legsAboveHands, rules) } - setUp.count { k -> k.knee?.let { it < rules.kneesDownDegrees } == true || k.legsAboveHands?.let { it < rules.minLegsAboveHands } == true }
                val straightArms = setUp.filter { it.elbow >= rules.obliqueTopDegrees }
                cycles[file.name + " (hold)"] = listOf(
                    "set-up frames at 45°: ${setUp.size}; failing — arms ${share(arms)}, body ${share(body)}, knee unseen ${share(unseen)}, knee angle ${share(kneeLow)}, legs low ${share(legsLow)}, bent knee with legs under the higher line ${share(bent)}",
                    "with the arms straight (${straightArms.size}): body p50 ${pct0(straightArms.map { it.posture }, 0.5)}, knee p50 ${pct0(straightArms.mapNotNull { it.knee }, 0.5)}, legs p10/p50/p90 ${pct0(straightArms.mapNotNull { it.legsAboveHands }, 0.1)} / ${pct0(straightArms.mapNotNull { it.legsAboveHands }, 0.5)} / ${pct0(straightArms.mapNotNull { it.legsAboveHands }, 0.9)}",
                )
            }
        }
        Row(TraceFiles.name(file), recording.placement, recording.phoneSide, recording.stakes, ratios, views, total, recording.expectedReps, hints)
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
            println("| s | verdict | top | min elbow | shoulder over hand (head-on: share of top height) | push knee | push legs | descent knee | descent legs | knee unseen | body | push body | ms | hip rise |")
            println("|---|---|---|---|---|---|---|---|---|---|---|---|---|---|")
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
