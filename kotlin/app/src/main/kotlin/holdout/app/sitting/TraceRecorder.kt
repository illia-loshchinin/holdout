package holdout.app.sitting

import holdout.repcounter.CameraView
import holdout.repcounter.PoseFrame
import holdout.repcounter.Readout
import holdout.repcounter.RepEvent
import holdout.repcounter.Stakes
import holdout.repcounter.Trace
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Writes one sitting's landmarks and the counter's decisions to a trace file
 * (`holdout.repcounter.Trace`), **in debug builds only**, into the app's
 * private storage. Nothing here leaves the phone on its own: a developer pulls
 * the file with `scripts/pull-traces.sh` over USB.
 *
 * No pixels are written — landmarks and numbers only (constitution X). The
 * file name's time is the phone's wall clock; it names a file and decides
 * nothing.
 */
class TraceRecorder(private val dir: File, private val stakes: Stakes) {

    // Three threads touch one file: frames arrive on MediaPipe's result
    // thread, Stop and leaving close it on the main thread, and labelling
    // rewrites it on its own. Every method holds this lock, so a row is never
    // half-written when the file is closed or relabelled.
    private val lock = Any()

    private var writer: BufferedWriter? = null
    private var failed = false
    private var closed = false
    private var rowsSinceFlush = 0

    /**
     * The file being written, once the first frame has opened it. Set under
     * [lock] on the pose-result thread, read on the main thread without it.
     */
    @Volatile
    var file: File? = null
        private set

    fun record(
        timestampMillis: Long,
        step: String,
        view: CameraView?,
        world: PoseFrame,
        image: PoseFrame,
        width: Int,
        height: Int,
        readout: Readout? = null,
        event: RepEvent? = null,
    ) {
        synchronized(lock) {
            // After close, never again: a camera frame still in flight when the
            // member presses Stop would otherwise open a fresh, near-empty file.
            if (failed || closed) return
            write(timestampMillis, step, view, world, image, width, height, readout, event)
        }
    }

    private fun write(
        timestampMillis: Long,
        step: String,
        view: CameraView?,
        world: PoseFrame,
        image: PoseFrame,
        width: Int,
        height: Int,
        readout: Readout?,
        event: RepEvent?,
    ) {
        runCatching {
            val out = writer ?: open(width, height)
            out.write(Trace.row(timestampMillis, step, view, world, image, readout, event))
            // Roughly once a second, and on every rep decision: a sitting that
            // ends with the app swiped away or killed still leaves its trace.
            rowsSinceFlush += 1
            if (rowsSinceFlush >= FLUSH_EVERY_ROWS || event != null) {
                out.flush()
                rowsSinceFlush = 0
            }
        }.onFailure {
            // A trace is a debugging aid; failing to write one must never
            // disturb the sitting it is recording.
            failed = true
            closeLocked()
        }
    }

    private fun open(width: Int, height: Int): BufferedWriter {
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        // Never an existing name: two sittings started in the same second
        // would otherwise truncate the first one's trace.
        val base = "$stamp-${stakes.name.lowercase()}"
        val target = generateSequence(0) { it + 1 }
            .map { n -> File(dir, if (n == 0) "$base.trace.csv" else "$base-$n.trace.csv") }
            .first { !it.exists() }
        file = target
        return target.bufferedWriter().also {
            it.write(Trace.header(stakes, width, height))
            // The header straight to storage: a sitting that ends before the
            // first flush still leaves a readable file, not an empty one.
            it.flush()
            writer = it
        }
    }

    /**
     * Writes a person's account of the sitting into the closed trace's header
     * (`Trace.label`). Called off the main thread; a failure leaves the trace
     * as it was, unlabelled.
     */
    fun label(expectedReps: Int, done: Map<String, Int>, note: String) {
        synchronized(lock) {
            val target = file ?: return
            closeLocked()
            runCatching {
                val labelled = Trace.label(target.readLines(), expectedReps, done, note)
                target.writeText(labelled.joinToString("\n", postfix = "\n"))
            }
        }
    }

    fun close() {
        synchronized(lock) { closeLocked() }
    }

    private fun closeLocked() {
        closed = true
        runCatching { writer?.close() }
        writer = null
    }

    private companion object {
        /** About a second of frames at the analyser's rate. */
        const val FLUSH_EVERY_ROWS = 30
    }
}
