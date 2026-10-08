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
 * as finished without a replay (review, PR #14). A sitting today's counter
 * does not count at all — head-on, or never counting — gets a replay with no
 * verdicts, never an old file left from a counter that did (review, PR #14).
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
            val view = recording.countingView
            // Head-on sittings are not counted any more (since 8 Oct), and one that never
            // started counting has nothing to judge: no verdict at all, said in the file.
            val lines = if (view == null || view == CameraView.FRONT) {
                emptyList()
            } else {
                val counter = RepCounter(view = view, stakes = recording.stakes)
                for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
                recording.countingFrames.mapNotNull { frame ->
                    when (val event = counter.onFrame(frame.world, frame.image)) {
                        is RepEvent.Counted -> "${frame.world.timestampMillis},counted"
                        is RepEvent.Discarded -> "${frame.world.timestampMillis},${event.hint?.name ?: "refused"}"
                        null -> null
                    }
                }
            }
            val skipped = when (view) {
                null -> listOf("# not counted: the sitting never started counting")
                CameraView.FRONT -> listOf("# not counted: head-on, where no sitting is counted since 8 Oct")
                else -> emptyList()
            }
            File(trace.path.removeSuffix(".trace.csv") + ".today.csv")
                .writeText((listOf("# holdout-today-verdicts v1", "# replayed=$stamp") + skipped + "t,verdict" + lines).joinToString("\n", postfix = "\n"))
            written++
        }
    println("todayVerdicts: $written sittings replayed under ${dir.absolutePath}")
}
