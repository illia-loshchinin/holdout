package holdout.app.sitting

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.UseCase
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Holdout Lab only: the camera stream of a set, as video (constitution X v2.1.0). */
fun sittingVideo(context: Context, dir: File, stakes: String): SittingVideo? = CameraXSittingVideo(context.applicationContext, dir, stakes)

/**
 * CameraX `VideoCapture` bound beside the preview and the analysis: the raw
 * camera, with nothing drawn over it — the clean footage the knee classifier
 * needs. HD — 960×720, the analysis frame's own size (raised from SD on 1 Oct:
 * 480×640 left too little of the legs to label) — and no more, so the encoder
 * does not take frames from pose analysis, which must hold ≥ 15 FPS (research
 * R4; Lab's readout shows it while recording). Falls back to SD.
 */
private class CameraXSittingVideo(private val context: Context, private val dir: File, private val stakes: String) : SittingVideo {
    private val recorder =
        Recorder
            .Builder()
            .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
            // The analysis frame's shape, so video and trace show the same picture
            // (with the shared crop the sitting binds all three under).
            .setAspectRatio(androidx.camera.core.AspectRatio.RATIO_4_3)
            .build()
    private val capture = VideoCapture.withOutput(recorder)
    private var recording: Recording? = null
    private var file: File? = null
    private var startedElapsedNanos = 0L

    @Volatile
    private var finalName: String? = null

    override val useCase: UseCase get() = capture

    // No audio is ever enabled: withAudioEnabled() is not called, and the app
    // holds no microphone permission (FR-022a).
    @SuppressLint("MissingPermission")
    override fun start() {
        if (recording != null) return
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
        val out = File(dir, "$stamp-$stakes.recording.mp4").also { file = it }
        recording =
            capture.output
                .prepareRecording(context, FileOutputOptions.Builder(out).build())
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> startedElapsedNanos = SystemClock.elapsedRealtimeNanos()
                        is VideoRecordEvent.Finalize -> finish(out, event)
                        else -> Unit
                    }
                }
    }

    override fun stop(traceName: String?) {
        finalName = traceName?.removeSuffix(".trace.csv")
        recording?.stop()
        recording = null
    }

    /** Renames the finished file after the trace and writes when it started. */
    private fun finish(out: File, event: VideoRecordEvent.Finalize) {
        if (event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE) {
            Log.w(TAG, "recording ended with error ${event.error}", event.cause)
        }
        if (!out.exists() || out.length() == 0L) return
        val base = finalName ?: out.name.removeSuffix(".recording.mp4")
        val video = File(dir, "$base.mp4").let { if (it.exists()) File(dir, "$base-${System.currentTimeMillis()}.mp4") else it }
        if (!out.renameTo(video)) return
        runCatching {
            File(dir, video.name.removeSuffix(".mp4") + ".video.json").writeText(
                """{"video":"${video.name}","trace":${finalName?.let { "\"$it.trace.csv\"" } ?: "null"},""" +
                    """"startedElapsedRealtimeNanos":$startedElapsedNanos,"durationNanos":${event.recordingStats.recordedDurationNanos},""" +
                    """"clock":"trace timestamps are the camera's sensor time; on this phone that is the boot clock, as startedElapsedRealtimeNanos is"}""",
            )
        }
    }

    companion object {
        private const val TAG = "SittingVideo"
    }
}
