package holdout.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import holdout.repcounter.BackgroundMotion
import holdout.repcounter.BodyBox
import holdout.repcounter.Landmark
import holdout.repcounter.PoseFrame
import holdout.repcounter.PoseIndex
import com.google.mediapipe.tasks.components.containers.Landmark as MpLandmark

/**
 * The camera end of rep verification (T046–T050, research R4, R5).
 *
 * **Every frame is processed in this process and discarded.** Nothing is
 * written to disk, nothing is uploaded, and there is no code path here that
 * could — which is what acceptance criterion 10 checks by putting the phone
 * behind a proxy and watching for anything but RPC traffic (FR-022,
 * constitution X).
 *
 * **One model.** The hand landmarker went with the palm liveness check: the
 * ready position is the top of a push-up now, which the pose model already
 * sees (`ReadyGate`). That takes about 8 MB off the APK and removes the
 * mid-sitting model switch that came with it.
 */
class VisionAnalyzer(
    context: Context,
    private val onPose: (Observation) -> Unit,
    private val onError: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    /**
     * One analysed frame.
     *
     * [frameWidth] and [frameHeight] are the analysed image's size **after
     * rotation**, and they matter: the skeleton overlay has to letterbox the
     * landmarks the same way the preview letterboxes the picture, or the bones
     * sit beside the body instead of on it.
     */
    data class Observation(
        val world: PoseFrame,
        val image: PoseFrame,
        val brightness: Float,
        /** Share of the background that moved lately, for the "Background still" check ([BackgroundMotion]). */
        val backgroundMotion: Float,
        val frameWidth: Int,
        val frameHeight: Int,
    )

    private val poseLandmarker: PoseLandmarker? = runCatching {
        PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(POSE_MODEL)
                        // GPU first: research R4's ≥ 15 FPS target on a Seeker
                        // assumes it. The CPU delegate is S2's documented
                        // fallback, chosen against measurements rather than here.
                        .setDelegate(Delegate.GPU)
                        .build(),
                )
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setResultListener { result, input -> handlePose(result, input.width, input.height) }
                .setErrorListener { error -> onError(error.message ?: "pose model error") }
                .build(),
        )
    }.getOrElse {
        onError("The pose model could not start: ${it.message}")
        null
    }

    /** The frame's own timestamp, so the counter's cycle timing is the camera's, not the UI's. */
    private var lastTimestampMillis = 0L

    /**
     * Guards the landmarker against [close]. The camera delivers frames on its
     * own thread and can have one in flight when the screen goes away; a frame
     * handed to a closed landmarker throws, and on the analysis thread that
     * took the whole app down the moment the member pressed Stop.
     */
    private val lock = Any()

    @Volatile
    private var closed = false

    override fun analyze(imageProxy: ImageProxy) {
        if (closed) {
            imageProxy.close()
            return
        }
        // The capture clock, not the wall clock: it only moves forward, so a
        // network-time correction mid-sitting cannot squeeze reps into
        // "too fast". Read before the frame is closed.
        val capturedMillis = imageProxy.imageInfo.timestamp / 1_000_000
        val bitmap = try {
            imageProxy.toBitmap().rotated(imageProxy.imageInfo.rotationDegrees)
        } catch (e: IllegalStateException) {
            onError("The camera frame could not be read: ${e.message}")
            null
        } finally {
            imageProxy.close()
        }
        bitmap ?: return

        // MediaPipe's live-stream mode rejects a timestamp that does not
        // advance, and two frames can share a millisecond.
        val timestamp = maxOf(capturedMillis, lastTimestampMillis + 1)
        lastTimestampMillis = timestamp

        val grid = bitmap.lumaGrid()
        lastBrightness = grid.mean()
        lastMotion = motion.update(grid.values, grid.cols, grid.rows, lastBody)
        synchronized(lock) {
            if (closed) return
            // A model that failed mid-sitting costs frames, never the app.
            runCatching { poseLandmarker?.detectAsync(BitmapImageBuilder(bitmap).build(), timestamp) }
                .onFailure { onError("The pose model stopped: ${it.message}") }
        }
    }

    @Volatile
    private var lastBrightness: Float = 0.5f

    private val motion = BackgroundMotion()

    @Volatile
    private var lastMotion: Float = 0f

    /** Where the member was in the last result: their own movement is not background. */
    @Volatile
    private var lastBody: BodyBox? = null

    private fun handlePose(result: PoseLandmarkerResult, width: Int, height: Int) {
        val image = result.landmarks().firstOrNull()
        val world = result.worldLandmarks().firstOrNull()
        if (image == null || world == null) {
            lastBody = null
            onPose(Observation(EMPTY, EMPTY, lastBrightness, lastMotion, width, height))
            return
        }
        val timestamp = result.timestampMs()
        val imageFrame = PoseFrame(timestamp, image.map { Landmark(it.x(), it.y(), it.z(), presenceOf(it)) })
        lastBody = BodyBox.of(imageFrame)
        onPose(
            Observation(
                world = PoseFrame(timestamp, world.map { Landmark(it.x(), it.y(), it.z(), presenceOf(it)) }),
                image = imageFrame,
                brightness = lastBrightness,
                backgroundMotion = lastMotion,
                frameWidth = width,
                frameHeight = height,
            ),
        )
    }

    /** Safe from any thread, and more than once; frames arriving afterwards are dropped. */
    fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            poseLandmarker?.close()
        }
    }

    companion object {
        /**
         * R4's decision: FULL on the GPU delegate, with LITE only "if spike S2
         * misses 15 FPS on a Seeker". LITE was shipped first by mistake — and
         * better landmarks matter most in the depth direction, which is
         * precisely what a phone placed in front of the member depends on.
         */
        const val POSE_MODEL = "pose_landmarker_full.task"

        private val EMPTY = PoseFrame(0, List(PoseIndex.COUNT) { Landmark(0f, 0f, 0f, 0f) })
    }
}

/**
 * MediaPipe carries presence as an `Optional<Float>` that some model builds
 * leave empty, falling back to visibility, which may also be empty.
 *
 * An absent score is read as fully present. The model returned the landmark;
 * dropping every frame because it declined to score its own confidence would
 * stop the counter working at all, and the pre-flight "whole body in frame"
 * check already refuses to start a sitting it cannot see.
 *
 * These are free functions rather than extensions on purpose — an extension
 * named `presence()` would be shadowed by the member of the same name and
 * silently never called.
 */
private fun presenceOf(landmark: NormalizedLandmark): Float = landmark.presence().orElseGet { landmark.visibility().orElse(1f) }

private fun presenceOf(landmark: MpLandmark): Float = landmark.presence().orElseGet { landmark.visibility().orElse(1f) }

private fun Bitmap.rotated(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

/** Luma on a coarse grid, row-major: the lighting check's mean and the motion check's cells. */
private class LumaGrid(val values: FloatArray, val cols: Int, val rows: Int) {
    fun mean(): Float = if (values.isEmpty()) 0.5f else values.average().toFloat()
}

/**
 * Luma sampled every [step] pixels — the pre-flight checks need numbers, not a
 * photometric measurement, and reading every pixel of a 640×480 frame thirty
 * times a second would cost more than the pose model does.
 */
private fun Bitmap.lumaGrid(step: Int = 16): LumaGrid {
    val cols = (width + step - 1) / step
    val rows = (height + step - 1) / step
    val values = FloatArray(cols * rows)
    for (row in 0 until rows) {
        for (col in 0 until cols) {
            val pixel = getPixel(col * step, row * step)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            values[row * cols + col] = ((0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0).toFloat()
        }
    }
    return LumaGrid(values, cols, rows)
}
