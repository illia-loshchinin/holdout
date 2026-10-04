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
 * change has put right without asking. Only sittings with a video: a fixture
 * folder is left untouched.
 */
fun main(args: Array<String>) {
    val dir = File(args.firstOrNull() ?: "../phone_traces")
    val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    var written = 0
    dir.walkTopDown()
        .filter { it.isFile && it.name.endsWith(".trace.csv") && File(it.path.removeSuffix(".trace.csv") + ".mp4").exists() }
        .forEach { trace ->
            val recording = runCatching { Trace.parse(trace.readLines()) }.getOrNull() ?: return@forEach
            val view = recording.countingView ?: return@forEach
            val counter = RepCounter(view = view, stakes = recording.stakes)
            for (frame in recording.countdownFrames) counter.prime(frame.world, frame.image)
            val lines = recording.countingFrames.mapNotNull { frame ->
                when (val event = counter.onFrame(frame.world, frame.image)) {
                    is RepEvent.Counted -> "${frame.world.timestampMillis},counted"
                    is RepEvent.Discarded -> "${frame.world.timestampMillis},${event.hint?.name ?: "refused"}"
                    null -> null
                }
            }
            File(trace.path.removeSuffix(".trace.csv") + ".today.csv")
                .writeText((listOf("# holdout-today-verdicts v1", "# replayed=$stamp", "t,verdict") + lines).joinToString("\n", postfix = "\n"))
            written++
        }
    println("todayVerdicts: $written sittings replayed under ${dir.absolutePath}")
}
