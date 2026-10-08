package holdout.repcounter

import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * `./gradlew :repcounter:todayVerdicts [-Ptraces=<dir>]` — every sitting under
 * the folder (default `phone_traces/`) that has its video, replayed through
 * today's counter, and the verdict on each rep written beside the trace as
 * `<sitting>.today.csv`: the trace time the rep ended and the verdict
 * (`counted`, or the [RepHint] it was refused with, or `refused`).
 *
 * The labelling page (`scripts/label-reps.mjs`) shows it next to the verdict
 * the app gave when the sitting was recorded, so a reviewer sees which reps a
 * change has put right without asking. Sittings with a video, or with labels
 * (a video pruned after promoting): the labelling tool will not take a sitting
 * as finished without a replay (review, PR #14). A sitting today's app would
 * not record as it was — the bench's [notScoredBecause]: an old picture, not at
 * 45°, no plank held through the countdown, in front of its band — gets a replay
 * with no verdicts and the reason, never verdicts for reps that can no longer
 * happen, nor an old file left from a counter that judged them (review, PR #14);
 * the labelling tool never promotes it or prunes its video. One only today's
 * countdown would stop is replayed and says so: the ratchet holds its labels.
 */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "../phone_traces")
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    var written = 0
    dir.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".trace.csv") }
        .filter { trace -> listOf(".mp4", ".labels.csv").any { File(trace.path.removeSuffix(".trace.csv") + it).exists() } }
        .forEach { trace ->
            val recording = runCatching { Trace.parse(trace.readLines()) }.getOrNull() ?: return@forEach
            // The same sittings the bench scores and the ratchet holds: no verdict at all
            // for one today's app would not record, and why, said in the file.
            // One whose countdown today's app would stop is still replayed: the ratchet holds
            // its labels (owner, 8 Oct), so the reps the recording missed must be labelled too.
            val notScored = recording.notScoredBecause(countdown = false)
            val stops = recording.countdownStopsBecause().takeIf { notScored == null }
            val counter = recording.primedCounter()
            val lines = if (notScored != null || counter == null) {
                emptyList()
            } else {
                recording.countingFrames.mapNotNull { frame ->
                    when (val event = counter.onFrame(frame.world, frame.image)) {
                        is RepEvent.Counted -> "${frame.world.timestampMillis},counted"
                        is RepEvent.Discarded -> "${frame.world.timestampMillis},${event.hint?.name ?: "refused"}"
                        null -> null
                    }
                }
            }
            val skipped = listOfNotNull(
                notScored?.let { "# not counted: $it" },
                stops?.let { "# not scored on the bench, held by the ratchet: $it" },
            )
            File(trace.path.removeSuffix(".trace.csv") + ".today.csv")
                .writeText((listOf("# holdout-today-verdicts v1", "# replayed=$stamp") + skipped + "t,verdict" + lines).joinToString("\n", postfix = "\n"))
            written++
        }
    println("todayVerdicts: $written sittings replayed under ${dir.absolutePath}")
}
