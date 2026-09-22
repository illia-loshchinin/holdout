package holdout.app.sitting

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.app.vision.VisionAnalyzer
import holdout.repcounter.PoseIndex
import java.util.concurrent.Executors

/**
 * The sitting, screen 8 of PRD §10.1: start card, four pre-flight pills, palm
 * liveness, a 3-second countdown, counting with the skeleton overlay and the
 * bank ring, then the day complete.
 *
 * The camera runs only while this screen is on top, and every frame it produces
 * is analysed in process and dropped (FR-022).
 */
@Composable
fun SittingScreen(
    viewModel: SittingViewModel,
    onFinished: () -> Unit,
    onCancelled: (discarded: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCamera = granted }

    KeepScreenOn()

    // Every transition after Start is a sound: the member is on the floor with
    // the screen out of sight and their hands on the ground.
    val cues = remember { SittingCues(context) }
    DisposableEffect(cues) { onDispose { cues.close() } }
    LaunchedEffect(viewModel) { viewModel.cues.collect { cues.play(it) } }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas),
    ) {
        if (state.step != SittingViewModel.Step.COMPLETE) {
            if (hasCamera) {
                CameraFeed(viewModel)
                SkeletonOverlay(viewModel, mirrored = CAMERA_IS_FRONT_FACING)
            } else {
                LaunchedEffect(Unit) { requestCamera.launch(Manifest.permission.CAMERA) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.screenGutter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems, Alignment.CenterVertically),
        ) {
            when (state.step) {
                SittingViewModel.Step.PREFLIGHT -> PreflightPills(state, hasCamera)
                SittingViewModel.Step.COUNTDOWN -> Countdown(
                    onTick = viewModel::onCountdownTick,
                    onDone = viewModel::startCounting,
                )
                SittingViewModel.Step.COUNTING -> CountingOverlay(state)
                SittingViewModel.Step.COMPLETE -> DayComplete(state, onFinished)
            }

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Warning)
            }
        }

        if (state.step != SittingViewModel.Step.COMPLETE) {
            TextButton(
                onClick = { onCancelled(viewModel.endSitting()) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Spacing.tight),
            ) {
                Text("Stop", color = HoldoutColors.TextSecondary)
            }
        }
    }
}

/**
 * Holds the screen on for as long as this screen is composed.
 *
 * A sitting is minutes of not touching the phone, so the display times out and
 * locks in the middle of a set — the camera goes with it, and the reps since
 * the last banked block are gone. `FLAG_KEEP_SCREEN_ON` needs no permission and
 * is released the moment the sitting ends, so it cannot leak into the rest of
 * the app or flatten the battery after the member walks away. A `WakeLock`
 * would do the same job, and would need `WAKE_LOCK` and careful release.
 */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun PreflightPills(state: SittingViewModel.UiState, hasCamera: Boolean) {
    if (!hasCamera) {
        Text(
            "Holdout counts your reps on this phone, so it needs the camera. " +
                "No video ever leaves the device.",
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        return
    }

    val report = state.preflight
    if (report == null) {
        Text("Looking…", style = LabelStyle, color = HoldoutColors.TextTertiary)
        return
    }

    report.statuses.forEach { status ->
        Text(
            text = if (status.passing) status.check.label else "${status.check.label} — ${status.fix}",
            style = LabelStyle,
            color = if (status.passing) HoldoutColors.Success else HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
    }

    // No button. By the time these are green the member is face-down on the
    // floor, and asking them to tap is what "I'm ready" got wrong. Getting into
    // the top of a push-up and holding it is the start signal (ReadyGate).
    if (report.canStart) {
        Text(
            "Get into position and hold",
            style = MaterialTheme.typography.titleMedium,
            color = HoldoutColors.TextPrimary,
            textAlign = TextAlign.Center,
        )
        CircularProgressIndicator(
            progress = { state.readyProgress },
            modifier = Modifier.size(96.dp),
            color = HoldoutColors.Accent,
            trackColor = HoldoutColors.Surface3,
        )
    }
}

@Composable
private fun Countdown(onTick: (Int) -> Unit, onDone: () -> Unit) {
    var remaining by remember { mutableStateOf(3) }
    LaunchedEffect(Unit) {
        while (remaining > 0) {
            onTick(remaining)
            kotlinx.coroutines.delay(1_000)
            remaining -= 1
        }
        onDone()
    }
    Text("$remaining", style = CounterStyle, color = HoldoutColors.Accent)
}

@Composable
private fun CountingOverlay(state: SittingViewModel.UiState) {
    Text("${state.doneToday}", style = CounterStyle, color = HoldoutColors.TextPrimary)
    Text(
        "${state.banked} banked · ${state.remaining} to go",
        style = AmountStyle,
        color = HoldoutColors.TextSecondary,
    )
    state.hint?.let {
        Text(it, style = MaterialTheme.typography.titleMedium, color = HoldoutColors.Accent)
    }
}

@Composable
private fun DayComplete(state: SittingViewModel.UiState, onFinished: () -> Unit) {
    Text("${state.target}", style = CounterStyle, color = HoldoutColors.Success)
    Text("done", style = LabelStyle, color = HoldoutColors.TextSecondary)
    PrimaryButton("Back", onFinished)
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(Spacing.primaryButtonHeight),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.buttonColors(
            containerColor = HoldoutColors.TextPrimary,
            contentColor = HoldoutColors.OnLight,
        ),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * The skeleton, drawn from the **image** landmarks — the only ones that know
 * where the body is in the picture. The rep rule reads the world landmarks
 * instead, which describe the body and not its projection.
 *
 * Two things have to match the preview exactly, or the bones float beside the
 * body and the member trusts nothing the screen says:
 *
 * - **Mirroring.** `PreviewView` mirrors the front camera, because a selfie
 *   view that does not is disorienting. `ImageAnalysis` frames are *not*
 *   mirrored, so the landmarks arrive in un-mirrored coordinates and have to be
 *   flipped back to sit on the picture.
 * - **Letterboxing.** The preview is `FIT_CENTER`, so the whole analysed frame
 *   is on screen with bars on two sides rather than cropped. The overlay
 *   applies the same fit, which is what [SittingViewModel.UiState.frameWidth]
 *   is carried for.
 *
 * None of this touches the count. Mirroring swaps which arm the model calls
 * left, and the rule averages both sides and measures angles, which a
 * reflection does not change.
 */
@Composable
private fun SkeletonOverlay(viewModel: SittingViewModel, mirrored: Boolean) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val skeleton = state.skeleton ?: return
    if (state.frameWidth <= 0 || state.frameHeight <= 0) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // The same fit PreviewView applies, so the two agree.
                val scale = minOf(
                    size.width / state.frameWidth,
                    size.height / state.frameHeight,
                )
                val drawnWidth = state.frameWidth * scale
                val drawnHeight = state.frameHeight * scale
                val offsetX = (size.width - drawnWidth) / 2f
                val offsetY = (size.height - drawnHeight) / 2f

                fun project(landmark: holdout.repcounter.Landmark) = androidx.compose.ui.geometry.Offset(
                    x = offsetX + (if (mirrored) 1f - landmark.x else landmark.x) * drawnWidth,
                    y = offsetY + landmark.y * drawnHeight,
                )

                val bones = listOf(
                    PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_ELBOW,
                    PoseIndex.LEFT_ELBOW to PoseIndex.LEFT_WRIST,
                    PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_ELBOW,
                    PoseIndex.RIGHT_ELBOW to PoseIndex.RIGHT_WRIST,
                    PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_HIP,
                    PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_HIP,
                    PoseIndex.LEFT_HIP to PoseIndex.LEFT_ANKLE,
                    PoseIndex.RIGHT_HIP to PoseIndex.RIGHT_ANKLE,
                    PoseIndex.LEFT_SHOULDER to PoseIndex.RIGHT_SHOULDER,
                    PoseIndex.LEFT_HIP to PoseIndex.RIGHT_HIP,
                )
                bones.forEach { (from, to) ->
                    val a = skeleton[from] ?: return@forEach
                    val b = skeleton[to] ?: return@forEach
                    if (a.presence < 0.5f || b.presence < 0.5f) return@forEach
                    drawLine(
                        color = HoldoutColors.Accent,
                        start = project(a),
                        end = project(b),
                        strokeWidth = 4f,
                    )
                }
            },
    )
}

/**
 * **The front camera**, not the back one.
 *
 * A sitting is self-recorded: the member props the phone at chest height beside
 * them and has to see the preview to get their whole body in frame, the angle
 * right and the palm where the model can read it. With the back camera they are
 * aiming a lens they cannot see through, which is exactly the framing problem
 * the pre-flight pills exist to catch — and being told to fix something you
 * cannot see is worse than no check at all.
 *
 * The cost is the front camera's lower resolution and field of view. Both are
 * S2's to measure: if the pose model cannot hold ≥ 15 FPS or loses the ankles at
 * arm's length, that is a finding for the spike, not a reason to point the
 * camera away from the member.
 */
private val SITTING_CAMERA = CameraSelector.DEFAULT_FRONT_CAMERA

/** Kept beside the selector so the two can never disagree. */
private const val CAMERA_IS_FRONT_FACING = true

/**
 * 16:9, on **both** the preview and the analysis.
 *
 * Two problems, one setting. Left to their defaults both use cases pick 4:3,
 * which `FIT_CENTER` letterboxes into a square in the middle of the Seeker's
 * tall screen — too little picture to judge how far away to stand. And left to
 * their defaults they can resolve to *different* ratios, while the skeleton
 * overlay letterboxes using the **analysis** frame size against a preview
 * showing the **preview** stream; pinning both closes a misalignment that has
 * simply not been noticed yet.
 *
 * 16:9 is usually a vertical crop of the sensor, so there is slightly less room
 * to step back than 4:3 gave. Whether the whole body still fits at a propped
 * phone's distance is **spike S2's** to measure, on this camera.
 */
private val SITTING_RESOLUTION = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
    .build()

/** CameraX preview plus the analyser, bound to this screen's lifecycle. */
@Composable
private fun CameraFeed(viewModel: SittingViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    val analyzer = remember {
        VisionAnalyzer(
            context = context,
            onPose = { observation ->
                // Background motion needs a frame-to-frame diff the analyser
                // does not keep yet; until it does, the pill reports still,
                // which is the permissive answer and never blocks a sitting.
                viewModel.onPoseFrame(observation, backgroundMotion = 0f)
            },
            onError = viewModel::onError,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            analyzer.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                // Show the whole analysed frame rather than cropping it: what
                // the member sees is then exactly what the model is scoring,
                // which is the only way the framing pill can be trusted.
                scaleType = PreviewView.ScaleType.FIT_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder()
                    .setResolutionSelector(SITTING_RESOLUTION)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(SITTING_RESOLUTION)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }

                runCatching {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        SITTING_CAMERA,
                        preview,
                        analysis,
                    )
                }.onFailure { viewModel.onError("The camera could not start: ${it.message}") }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
