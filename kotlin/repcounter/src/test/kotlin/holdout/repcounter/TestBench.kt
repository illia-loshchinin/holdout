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
         * What should count: `honest`, and wherever knees are not judged — the
         * Daily 25 and an Easy Holdout ([Stakes.judgesKnees]) — a push-up from the
         * knees, counted by design: benched as `knees-daily` (the name predates
         * Easy; the baseline keeps it). `unsure` is left out.
         */
        const val KNEES_DAILY = "knees-daily"

        /** How a [label] is benched in a sitting at [stakes]: a knee rep counts where knees are not judged. */
        fun kind(stakes: Stakes, label: String): String = if (label == "knees" && !stakes.judgesKnees) KNEES_DAILY else label

        /**
         * A rep today's counter counts where no label lies: nobody has said what it
         * was, so it is held as wrong until someone labels it ([benchSitting]).
         */
        const val UNLABELLED_COUNT = "unlabelled-count"
        val SHOULD_COUNT = setOf("honest", KNEES_DAILY)

        /** `none`: no rep on the video at all — the app judged a moment nobody made a push-up in. */
        val CHEATS = listOf("half", "knees", "floor", "cobra", "sag", "other", "none")

        /**
         * An honest movement the counter cannot see — the legs outside its
         * picture, which is narrower than the video on some builds. It should not
         * count, and should be refused with [RepHint.LEGS_IN_VIEW].
         */
        const val UNSEEN = "unseen"

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
    /** The trace time the labelled rep ended: the labels file's `t`, which keys it in the ratchet's baseline. */
    val labelT: Long = 0,
) {
    val counted: Boolean get() = verdict == "counted"
    val right: Boolean get() = counted == (label in RepLabels.SHOULD_COUNT)
}

/**
 * Today's counter as the app hands it over at "go": built for this sitting and
 * primed on its countdown. Null where today's app builds none — no counting, or
 * a view other than 45°.
 */
fun Trace.Recording.primedCounter(): RepCounter? {
    if (countingView != CameraView.OBLIQUE) return null
    return RepCounter(view = CameraView.OBLIQUE, stakes = stakes).also { counter ->
        for (frame in countdownFrames) counter.prime(frame.world, frame.image)
    }
}

/**
 * The placement ratio of the plank held through the countdown, as today's
 * counter calibrates on it ([RepCounter.calibratedPlacementRatio]); null when
 * it would not calibrate.
 */
fun Trace.Recording.heldPlacementRatio(): Float? = primedCounter()?.calibratedPlacementRatio

/**
 * What would stop today's countdown on this recording, or null when it runs to
 * "go": frame by frame, the pose checks of [Preflight] passing at 45° and the plank
 * staying one the counter can read ([ReadyGate.plankBlocker]), held to the
 * [CountdownGuard]'s grace, and nothing failing on the last frame — as
 * SittingViewModel judges it. Light and background motion are not in the trace;
 * they passed on the phone, and pass here (review, PR #14).
 */
fun Trace.Recording.countdownStopsBecause(): String? {
    val preflight = Preflight(stakes = stakes)
    val gate = ReadyGate(stakes = stakes)
    val guard = CountdownGuard()
    var failing: String? = null
    for (frame in countdownFrames) {
        val report = preflight.evaluate(PreflightInput(brightness = 0.5f, backgroundMotion = 0f, world = frame.world, image = frame.image.frame, imagePose = frame.image))
        gate.onFrame(frame.world, frame.timestampMillis, pillsGreen = true, view = CameraView.OBLIQUE, image = frame.image)
        val why = when {
            !report.canStart -> report.fixes.firstOrNull() ?: "a check failed"
            report.view != CameraView.OBLIQUE -> "the phone read ${report.view?.name?.lowercase() ?: "nowhere"}, not 45°"
            else -> gate.plankBlocker?.fix
        }
        if (why != null && failing == null) failing = why
        if (why == null) failing = null
        if (guard.shouldAbort(frame.timestampMillis, holds = why == null)) return "today's countdown stops: $why"
    }
    return failing?.takeIf { guard.isFailing }?.let { "today's countdown stops at go: $it" }
}

/**
 * Why today's app would not start this sitting, or null when it would. Since 8 Oct
 * no sitting is counted but at 45°, and none starts counting without a plank held
 * through the countdown to read the placement from — on Hard, a plank on the toes
 * too (SittingViewModel.startCounting). Since 2 Oct a Hard one does not start from
 * in front of about 35° — the placement ratio at or over
 * [RepRules.maxHipRisePlacementRatio]; an Easy one, or the Daily 25, from in front
 * of about 40° ([Stakes.EASY_MAX_PLACEMENT_RATIO]). Its reps cannot happen on the
 * phone any more, so they are neither scored nor held by the ratchet: from in
 * front the body line reads 127–143° on straight bodies (29 Sep), head-on knees and
 * cobras read as honest reps, and no rule should be bent to fit a sitting the app
 * refuses (review, PR #14). [countdown] false leaves out [countdownStopsBecause].
 */
fun Trace.Recording.notStartedBecause(countdown: Boolean = true): String? {
    val counter = primedCounter() ?: return when (countingView) {
        null -> "never started counting"
        CameraView.FRONT -> "head-on, where no sitting is counted since 8 Oct"
        else -> "side-on, where no sitting is counted since 8 Oct"
    }
    if (countdown) countdownStopsBecause()?.let { return it }
    if (!counter.placementCalibrated) return "no plank held through the countdown to read the placement from, which today's app does not start"
    if (stakes == Stakes.STAKED && !counter.plankCalibrated) return "no plank on the toes held through the countdown, which a Hard sitting needs to start"
    val frontLine = if (stakes == Stakes.STAKED) RepRules().maxHipRisePlacementRatio else Stakes.EASY_MAX_PLACEMENT_RATIO
    val ratio = counter.calibratedPlacementRatio ?: return null
    return if (ratio >= frontLine) "$stakes from in front of its band (held ratio %.2f), which today's app does not start".format(ratio) else null
}

/** Whether today's app would start this sitting ([notStartedBecause]). */
fun Trace.Recording.startsToday(): Boolean = notStartedBecause() == null

/**
 * Whether the counter read this sitting in today's picture: 4:3, as the video
 * is. Up to 29 Sep the app analysed a 9:16 crop (720×1280) of a 4:3 video, so
 * the knees and feet could be in the video and outside what the counter saw —
 * reps refused "legs in the picture" that a reviewer saw whole. Since 1 Oct the
 * two are the same picture (owner, 4 Oct: those sittings are not scored).
 */
fun Trace.Recording.inTodaysPicture(): Boolean = frames.firstOrNull()?.image?.let { it.width * 4 == it.height * 3 } != false

/** Why this sitting is left out of the bench score and the ratchet ([countdown] false: kept in the ratchet), or null when it is scored. */
fun Trace.Recording.notScoredBecause(countdown: Boolean = true): String? = when {
    !inTodaysPicture() -> frames.first().image.let { "read in a ${it.width}×${it.height} crop, not today's 4:3 picture" }
    else -> notStartedBecause(countdown)
}

/**
 * Replays [recording] through today's counter and pairs each judged rep with
 * the label of the rep that ended on (or within [toleranceMillis] of) the same
 * frame. A label still unpaired takes the one judged rep that ends inside its
 * own stretch — after the rep recorded before it, up to its end — when there is
 * exactly one: the same movement, ended a little sooner or later by a counter
 * whose top has moved (on 1 Oct a rep held at the top before locking out ended
 * a second earlier than when recorded). A stretch the counter now cuts into
 * several reps pairs with none. A label still unpaired is returned in
 * [BenchSitting.missing]: the counter cuts that stretch of the sitting
 * differently. A rep counted where no label lies is returned in
 * [BenchSitting.unlabelledCounts]: a count nobody has vouched for, which a
 * change to the counter must not add unseen (review, PR #14).
 */
fun benchSitting(name: String, recording: Trace.Recording, labels: RepLabels, toleranceMillis: Long = 400): BenchSitting {
    val view = recording.countingView ?: return BenchSitting(emptyList(), labels.reps, emptyList())
    val counter = RepCounter(view = view, stakes = recording.stakes)
    for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
    val counting = recording.countingFrames
    val t0 = counting.firstOrNull()?.world?.timestampMillis ?: return BenchSitting(emptyList(), labels.reps, emptyList())
    val events = counting.mapNotNull { frame ->
        counter.onFrame(frame.world, frame.image)?.let { frame.world.timestampMillis to it }
    }
    val placement = listOfNotNull(recording.placement, recording.phoneSide).joinToString(" ").ifEmpty { "?" }
    val pairs = mutableMapOf<Long, RepLabels.Rep>()
    for ((t, _) in events) {
        val label = labels.reps.filter { it !in pairs.values && abs(it.endMillis - t) <= toleranceMillis }.minByOrNull { abs(it.endMillis - t) } ?: continue
        pairs[t] = label
    }
    val recorded = counting.filter { it.event.isNotBlank() }.map { it.timestampMillis }
    for (label in labels.reps.filter { it !in pairs.values }) {
        val from = recorded.lastOrNull { it < label.endMillis - toleranceMillis } ?: t0
        events.map { it.first }
            .filter { t -> t !in pairs && t > from && t <= label.endMillis + toleranceMillis }
            // One that lands on another recorded rep is that rep's.
            .filter { t -> recorded.none { it != label.endMillis && abs(it - t) <= toleranceMillis } }
            .singleOrNull()?.let { pairs[it] = label }
    }
    val judged = events.mapNotNull { (t, event) ->
        val label = pairs[t] ?: return@mapNotNull null
        if (label.label == "unsure") return@mapNotNull null
        val verdict = when (event) {
            is RepEvent.Counted -> "counted"
            is RepEvent.Discarded -> event.hint?.name ?: "refused"
        }
        // The Daily 25 counts push-ups from the knees: there a knees label should count.
        val kind = RepLabels.kind(recording.stakes, label.label)
        BenchRep(name, labels.person, placement, (t - t0) / 1000f, kind, label.note, verdict, label.by, label.flagged, label.claudeNote, label.hint, label.endMillis)
    }
    val unlabelledCounts = events.filter { (t, event) -> event is RepEvent.Counted && t !in pairs }.map { it.first }
    val unlabelledRefusals = events.filter { (t, event) -> event is RepEvent.Discarded && t !in pairs }.map { it.first }
    return BenchSitting(judged, labels.reps.filter { it !in pairs.values && it.label != "unsure" }, unlabelledCounts, unlabelledRefusals)
}

/**
 * One sitting on the bench: its judged labelled reps, the labels no judged rep
 * landed near, and reps judged where no label lies (trace times) — counted, or
 * refused. A refusal adds no count, so it fails nothing; but it may be an honest
 * rep, and only a label can say (review, PR #14).
 */
data class BenchSitting(
    val judged: List<BenchRep>,
    val missing: List<RepLabels.Rep>,
    val unlabelledCounts: List<Long>,
    val unlabelledRefusals: List<Long> = emptyList(),
)

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
    val traces = TraceFiles.under(dir)
    require(traces.isNotEmpty()) { "no .trace.csv(.gz) files under ${dir.absolutePath}" }

    val reps = mutableListOf<BenchRep>()
    val lost = mutableListOf<String>()
    val strays = mutableListOf<String>()
    val refusedStrays = mutableListOf<String>()
    val unlabelled = mutableListOf<String>()
    val notStarted = mutableListOf<String>()
    for (trace in traces) {
        val name = File(TraceFiles.base(trace)).relativeTo(dir).path.replace(File.separatorChar, '/')
        val labelsFile = TraceFiles.labels(trace)
        if (!labelsFile.exists()) {
            unlabelled += name
            continue
        }
        val recording = Trace.parse(TraceFiles.lines(trace))
        recording.notScoredBecause()?.let {
            notStarted += "$name: $it"
            continue
        }
        val labels = RepLabels.parse(labelsFile.readLines())
        val (judged, missing, unlabelledCounts, unlabelledRefusals) = benchSitting(name, recording, labels)
        reps += judged
        // A labelled rep today's counter does not judge at all counts nothing: scored
        // as not judged — a miss for one that should count, right for a cheat — as the
        // ratchet scores it, so the gate cannot pass on reps the counter stopped seeing.
        val t0 = recording.countingFrames.firstOrNull()?.timestampMillis ?: 0L
        val placement = listOfNotNull(recording.placement, recording.phoneSide).joinToString(" ").ifEmpty { "?" }
        for (m in missing) {
            val kind = RepLabels.kind(recording.stakes, m.label)
            reps += BenchRep(name, labels.person, placement, (m.endMillis - t0) / 1000f, kind, m.note, Ratchet.NOT_JUDGED, m.by, m.flagged, m.claudeNote, m.hint, m.endMillis)
            lost += "$name: a ${m.label} rep ending at t=${m.endMillis} has no judged rep near it today"
        }
        val t0Counted = recording.countingFrames.firstOrNull()?.timestampMillis ?: 0L
        unlabelledCounts.forEach { strays += "$name: counted at %.1f s into counting (t=$it), where no rep is labelled".format((it - t0Counted) / 1000f) }
        unlabelledRefusals.forEach { refusedStrays += "$name: refused at %.1f s into counting (t=$it), where no rep is labelled".format((it - t0Counted) / 1000f) }
    }

    fun pct(n: Int, of: Int) = if (of == 0) "-" else "%.0f %%".format(100.0 * n / of)
    val failures = mutableListOf<String>()

    println("## Gate — honest (and Daily 25 knees) counted ≥ 98 %, each cheat counted ≤ 2 %\n")
    println("| label | reps | counted | refused | rate | gate |")
    println("|---|---|---|---|---|---|")
    fun shown(label: String) = if (label == RepLabels.KNEES_DAILY) "knees (Daily 25 / Easy, counts)" else label
    for (label in RepLabels.SHOULD_COUNT.toList() + RepLabels.CHEATS + RepLabels.UNSEEN) {
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
    // A count nobody vouched for fails the gate until it is labelled: the count it adds is real.
    if (strays.isNotEmpty()) {
        failures += "counted where no rep is labelled"
        println("\n## Counted where no rep is labelled (${strays.size}) — label them\n\n" + strays.joinToString("\n") { "- $it" })
    }
    // A refusal there fails nothing, but may be an honest rep nobody has seen.
    if (refusedStrays.isNotEmpty()) {
        println("\n## Refused where no rep is labelled (${refusedStrays.size}) — label them if they were push-ups\n\n" + refusedStrays.joinToString("\n") { "- $it" })
    }
    if (notStarted.isNotEmpty()) {
        println("\n## Not scored: sittings today's app would not record as they were (${notStarted.size})\n\n" + notStarted.joinToString("\n") { "- $it" })
    }
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
