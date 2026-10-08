package holdout.app.sitting

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import holdout.app.BuildConfig
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import holdout.app.vision.PhoneTilt
import holdout.app.vision.VisionAnalyzer
import holdout.repcounter.Angles
import holdout.repcounter.Coach
import holdout.repcounter.ImagePose
import holdout.repcounter.Lens
import holdout.repcounter.PoseIndex
import holdout.repcounter.Side
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.atan

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
    /**
     * Debug builds: nothing drawn over the camera, for screen recordings that
     * train the knee classifier — dots and text on the legs would be learnt
     * instead of the legs. Sounds still play.
     */
    cleanScreen: Boolean = false,
    /** The finished screen's button: "Back" for the Daily 25, the submit for a Holdout day. */
    finishLabel: String = "Back",
    /** Debug builds: record this sitting's camera as video (constitution X, v1.5.0). */
    recordVideo: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    // Whether the member has already answered the system prompt this sitting.
    var askedCamera by remember { mutableStateOf(false) }
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasCamera = granted
        askedCamera = true
    }
    // Left the foreground: the camera stops, and counting does not resume where it was.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { viewModel.onLeftForeground() }
    // Back from the system settings with the camera allowed: pick it up.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Denied once: ask again. Once Android stops showing the prompt ("don't
     * ask again", or a second denial), only the app's settings page can grant
     * it, so that is where the button goes.
     */
    fun allowCamera() {
        val activity = context.findActivity()
        val promptStillShown = !askedCamera ||
            (activity != null && androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA))
        if (promptStillShown) {
            requestCamera.launch(Manifest.permission.CAMERA)
        } else {
            context.startActivity(
                android.content.Intent(
                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.fromParts("package", context.packageName, null),
                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    KeepScreenOn()

    /** Debug builds: where to go once the member has labelled the trace. */
    var labelThen by remember { mutableStateOf<(() -> Unit)?>(null) }

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
                CameraFeed(viewModel, recordVideo)
                if (!cleanScreen) {
                    PositionOverlay(viewModel, mirrored = CAMERA_IS_FRONT_FACING)
                    SkeletonOverlay(viewModel, mirrored = CAMERA_IS_FRONT_FACING)
                }
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
                SittingViewModel.Step.PREFLIGHT -> {
                    PreflightPills(state, hasCamera, onAllowCamera = ::allowCamera, cameraAsked = askedCamera)
                    if (state.guidance?.aligned != true) {
                        TextButton(onClick = viewModel::openSetup) {
                            Text("How to set up", color = HoldoutColors.Accent)
                        }
                    }
                }
                SittingViewModel.Step.COUNTDOWN -> Countdown(
                    onTick = viewModel::onCountdownTick,
                    onDone = viewModel::startCounting,
                )
                SittingViewModel.Step.COUNTING -> if (cleanScreen) {
                    // Clean screen: the count up in the corner, out of the way of the body.
                    Unit
                } else {
                    CountingOverlay(state)
                }
                SittingViewModel.Step.COMPLETE -> DayComplete(state, finishLabel) {
                    if (BuildConfig.DEBUG && viewModel.canLabelTrace) labelThen = onFinished else onFinished()
                }
            }

            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.Warning)
            }
        }

        // A Holdout day's deadline stays in view the whole sitting: the home
        // screen's countdown and last-minutes warning are gone while the camera is up.
        if (state.step != SittingViewModel.Step.COMPLETE && !cleanScreen) {
            DeadlineBanner(viewModel, Modifier.align(Alignment.TopCenter).padding(top = Spacing.screenGutter))
        }

        // Read while the phone is still in hand: it opens with the sitting, and
        // the countdown cannot start behind it.
        if (state.showSetup && state.step == SittingViewModel.Step.PREFLIGHT) {
            SetupDialog(
                stakes = state.stakes,
                detected = state.preflight?.view,
                onDone = viewModel::closeSetup,
            )
        }

        if (cleanScreen && state.step == SittingViewModel.Step.COUNTING) {
            Text(
                "${state.doneToday}",
                style = LabelStyle,
                color = HoldoutColors.TextSecondary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.betweenItems),
            )
        }

        // Debug builds: log what was really done before leaving, so the
        // trace this sitting wrote arrives already labelled.
        labelThen?.let { leave ->
            TraceLabelDialog(
                counted = state.repsCounted,
                onSave = { done, note, placement, phoneSide ->
                    viewModel.labelTrace(done, note, placement, phoneSide)
                    labelThen = null
                    leave()
                },
                onSkip = {
                    labelThen = null
                    leave()
                },
            )
        }

        if (state.step != SittingViewModel.Step.COMPLETE) {
            TextButton(
                onClick = {
                    val discarded = viewModel.endSitting()
                    if (BuildConfig.DEBUG && viewModel.canLabelTrace) {
                        labelThen = { onCancelled(discarded) }
                    } else {
                        onCancelled(discarded)
                    }
                },
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

/**
 * The positioning helper: what to fix next, then the four checks as chips.
 *
 * The outline itself is drawn by [PositionOverlay] over the camera; this is the
 * text half. Once the member is aligned it all goes, leaving the skeleton.
 */
@Composable
private fun PreflightPills(
    state: SittingViewModel.UiState,
    hasCamera: Boolean,
    onAllowCamera: () -> Unit = {},
    cameraAsked: Boolean = false,
) {
    if (!hasCamera) {
        Text(
            "Holdout counts your reps on this phone, so it needs the camera. " +
                "No video ever leaves the device.",
            style = MaterialTheme.typography.bodyLarge,
            color = HoldoutColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        // Denied, by accident or not: the sitting must not be a dead end.
        if (cameraAsked) {
            PrimaryButton("Allow camera", onAllowCamera)
        }
        return
    }

    val report = state.preflight
    if (report == null) {
        Text("Looking…", style = LabelStyle, color = HoldoutColors.TextTertiary)
        return
    }

    val guidance = state.guidance
    val aligned = guidance?.aligned == true

    // Fade rather than cut, so it reads as finished instead of broken.
    val helperAlpha by animateFloatAsState(
        targetValue = if (aligned) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "helper",
    )

    // The one correction, big enough to read from the floor — and spoken.
    state.coach?.let { coach ->
        Text(
            text = coach.words,
            style = CoachStyle,
            color = if (coach == Coach.HOLD) HoldoutColors.Success else HoldoutColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(HoldoutColors.Scrim, MaterialTheme.shapes.large)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
    // Every pill is green and the hold is not filling: the reason, in a sentence.
    state.holdFix?.let { fix ->
        Text(
            text = fix,
            style = LabelStyle,
            color = HoldoutColors.Warning,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(HoldoutColors.Scrim, MaterialTheme.shapes.medium)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }

    Column(
        modifier = Modifier.alpha(helperAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        // FR-019's four checks, still run and still gating — just no longer the
        // main event. Each red chip still states its own fix.
        report.statuses.forEach { status ->
            Text(
                text = if (status.passing) {
                    status.check.label + (state.preflight.view?.let { " · ${it.name.lowercase()}" } ?: "")
                } else {
                    status.fix ?: status.check.label
                },
                style = LabelStyle,
                color = when {
                    status.passing -> HoldoutColors.Success
                    status.check.advisory -> HoldoutColors.Warning
                    else -> HoldoutColors.TextSecondary
                },
                textAlign = TextAlign.Center,
            )
        }
    }

    if (aligned) {
        CircularProgressIndicator(
            progress = { state.readyProgress },
            modifier = Modifier.size(96.dp),
            color = HoldoutColors.Accent,
            trackColor = HoldoutColors.Surface3,
        )
    }
}

/** The coach's words: read from two metres away, so as big as a line allows. */
private val CoachStyle = TextStyle(fontSize = 54.sp, lineHeight = 58.sp, fontWeight = FontWeight.Bold)

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

/**
 * The count, with a reward on every rep: the number jumps, and a "+1" rises off
 * it and fades. A member mid-set glances at the screen for a fraction of a
 * second; this is what makes that glance land.
 */
@Composable
private fun CountingOverlay(state: SittingViewModel.UiState) {
    val done = state.doneToday
    var previous by remember { mutableStateOf(done) }
    val pulse = remember { Animatable(1f) }
    val rise = remember { Animatable(1f) }
    LaunchedEffect(done) {
        if (done > previous) {
            launch {
                pulse.snapTo(1.35f)
                pulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            launch {
                rise.snapTo(0f)
                rise.animateTo(1f, tween(durationMillis = 750, easing = FastOutSlowInEasing))
            }
        }
        previous = done
    }

    Box(contentAlignment = Alignment.Center) {
        Text(
            "$done",
            style = CounterStyle,
            color = HoldoutColors.TextPrimary,
            modifier = Modifier.scale(pulse.value),
        )
        if (rise.value < 1f) {
            Text(
                "+1",
                style = MaterialTheme.typography.headlineLarge,
                color = HoldoutColors.Success,
                modifier = Modifier.graphicsLayer {
                    translationY = -rise.value * 90.dp.toPx() - 40.dp.toPx()
                    translationX = 70.dp.toPx()
                    alpha = 1f - rise.value
                    val grow = 0.8f + 0.5f * rise.value
                    scaleX = grow
                    scaleY = grow
                },
            )
        }
    }
    Text(
        "${state.banked} banked · ${state.remaining} to go",
        style = AmountStyle,
        color = HoldoutColors.TextSecondary,
    )
    // Twice the title size: read from the floor, at arm's length.
    state.hint?.let {
        val title = MaterialTheme.typography.titleMedium
        Text(
            it,
            style = title.copy(fontSize = title.fontSize * 2, lineHeight = title.lineHeight * 2),
            color = HoldoutColors.Accent,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }

    // Test builds only: what the counter is reading, to tune it on a real body.
    if (BuildConfig.DEBUG) {
        state.traceName?.let {
            Text("rec · $it", style = LabelStyle, color = HoldoutColors.TextTertiary)
        }
        // Pose analysis must hold ≥ 15 FPS (research R4) — watched most while video records.
        Text(
            "analysis ${state.analysisFps?.let { "%.0f".format(it) } ?: "—"} fps" +
                when (state.recordingVideo) {
                    true -> " · video on"
                    false -> " · video could not start"
                    null -> ""
                },
            style = LabelStyle,
            color = if ((state.analysisFps ?: 99f) < 15f) HoldoutColors.Warning else HoldoutColors.TextTertiary,
        )
        state.readout?.let { r ->
            Text(
                "${r.view.name.lowercase()} · ${state.stakes.name.lowercase()} · elbow ${r.elbow.toInt()}° · " +
                    "line ${r.posture.toInt()}° · knee ${r.knee?.toInt()?.let { "$it°" } ?: "—"}",
                style = LabelStyle,
                color = HoldoutColors.TextTertiary,
            )
        }
    }
}

/** Time to a Holdout day's deadline, in the warning colour for its last minutes (FR-047); nothing for the Daily 25. */
@Composable
private fun DeadlineBanner(viewModel: SittingViewModel, modifier: Modifier) {
    var left by remember { mutableStateOf(viewModel.deadlineSecondsLeft()) }
    LaunchedEffect(viewModel) {
        while (true) {
            left = viewModel.deadlineSecondsLeft()
            kotlinx.coroutines.delay(1_000)
        }
    }
    val seconds = left ?: return
    Text(
        "${holdout.app.ui.Format.countdown(seconds)} to today's deadline",
        style = MaterialTheme.typography.titleMedium,
        color = if (holdout.core.Derive.inWarningWindow(seconds)) HoldoutColors.Warning else HoldoutColors.TextSecondary,
        modifier = modifier,
    )
}

@Composable
private fun DayComplete(state: SittingViewModel.UiState, label: String, onFinished: () -> Unit) {
    Text("${state.target}", style = CounterStyle, color = HoldoutColors.Success)
    Text("done", style = LabelStyle, color = HoldoutColors.TextSecondary)
    PrimaryButton(label, onFinished)
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
 * The floor line and the outline to lie into.
 *
 * Drawn under the skeleton, in the same letterboxed, mirrored frame the
 * skeleton uses, so the outline and the body sit in one coordinate system.
 *
 * **It fades out the moment the member is aligned** and is gone for good once
 * the countdown starts — scaffolding for getting into position, and pure noise
 * once they are there, both for the member holding a plank and for the camera
 * filming the demo.
 */
@Composable
private fun PositionOverlay(viewModel: SittingViewModel, mirrored: Boolean) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val guidance = state.guidance ?: return
    if (state.frameWidth <= 0 || state.frameHeight <= 0) return

    val alpha by animateFloatAsState(
        targetValue = if (guidance.aligned) 0f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "overlay",
    )
    if (alpha <= 0.01f) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha)
            .drawBehind {
                val fit = letterbox(size.width, size.height, state.frameWidth, state.frameHeight)

                // Where the floor starts. Everything below it is somewhere the
                // member could lie.
                guidance.horizonY?.let { horizon ->
                    val y = fit.offsetY + horizon * fit.height
                    drawLine(
                        color = HoldoutColors.Accent.copy(alpha = 0.35f),
                        start = Offset(fit.offsetX, y),
                        end = Offset(fit.offsetX + fit.width, y),
                        strokeWidth = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)),
                    )
                }

                val target = guidance.target
                val left = if (mirrored) 1f - target.right else target.left
                drawRoundRect(
                    color = HoldoutColors.Accent,
                    topLeft = Offset(
                        fit.offsetX + left * fit.width,
                        fit.offsetY + target.top * fit.height,
                    ),
                    size = Size(
                        2f * target.halfWidth * fit.width,
                        2f * target.halfHeight * fit.height,
                    ),
                    cornerRadius = CornerRadius(80f, 80f),
                    style = Stroke(
                        width = 6f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(28f, 20f)),
                    ),
                )

                // What goes in the box: a body at the top of a push-up, shaped
                // for the placement, so the outline reads as "lie like this".
                val box = Rect(
                    left = fit.offsetX + left * fit.width,
                    top = fit.offsetY + target.top * fit.height,
                    right = fit.offsetX + (left + 2f * target.halfWidth) * fit.width,
                    bottom = fit.offsetY + target.bottom * fit.height,
                )
                drawPlankFigure(box.deflate(box.minDimension * 0.08f), HoldoutColors.Accent.copy(alpha = 0.7f))
            },
    )
}

/**
 * The lens's vertical field of view, which is what turns the phone's tilt into
 * a horizon line.
 *
 * `SENSOR_INFO_PHYSICAL_SIZE` and the focal length give it exactly. **Frames
 * are rotated to the display**, so for a 90°/270° rotation the sensor's width
 * is what ends up vertical on screen — read the axis that matches the rotation
 * the analyser applied, or the horizon lands in the wrong place on exactly the
 * orientation people actually use.
 *
 * Falls back to [Lens.DEFAULT] when the characteristics are unreadable: a
 * horizon a few degrees out still puts the outline in the right half of the
 * screen, which is all the guide needs.
 */
@Suppress("TooGenericExceptionCaught")
private fun verticalFovOf(cameraInfo: androidx.camera.core.CameraInfo): Lens = runCatching {
    val camera2 = Camera2CameraInfo.from(cameraInfo)
    val size = camera2.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
    val focalLengths = camera2.getCameraCharacteristic(
        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
    )
    val focal = focalLengths?.firstOrNull() ?: return@runCatching Lens.DEFAULT
    if (size == null || focal <= 0f) return@runCatching Lens.DEFAULT

    val rotation = cameraInfo.sensorRotationDegrees
    val verticalExtent = if (rotation == 90 || rotation == 270) size.width else size.height
    val fov = 2.0 * atan((verticalExtent / (2.0 * focal)))
    Lens(Math.toDegrees(fov).toFloat())
}.getOrElse { Lens.DEFAULT }

/** Where a frame of [frameWidth] x [frameHeight] lands inside the view, FIT_CENTER. */
private data class Letterbox(
    val offsetX: Float,
    val offsetY: Float,
    val width: Float,
    val height: Float,
)

private fun letterbox(viewWidth: Float, viewHeight: Float, frameWidth: Int, frameHeight: Int): Letterbox {
    val scale = minOf(viewWidth / frameWidth, viewHeight / frameHeight)
    val width = frameWidth * scale
    val height = frameHeight * scale
    return Letterbox((viewWidth - width) / 2f, (viewHeight - height) / 2f, width, height)
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

                // Every joint the rule reads, as a point, joined as the body is:
                // arms, torso, and the legs through the knee — the knee is what
                // the staked check reads, so it has to be visible, not skipped.
                //
                // The side nearer the camera is drawn bright and the far side
                // dim: at 45° the counter measures only the near side, and the
                // far limbs are the model's guess from behind the body.
                val near = Angles.nearSide(ImagePose(skeleton, state.frameWidth, state.frameHeight))
                fun sideOf(index: Int): Side? = when (index) {
                    PoseIndex.LEFT_SHOULDER, PoseIndex.LEFT_ELBOW, PoseIndex.LEFT_WRIST,
                    PoseIndex.LEFT_HIP, PoseIndex.LEFT_KNEE, PoseIndex.LEFT_ANKLE,
                    -> Side.LEFT
                    PoseIndex.RIGHT_SHOULDER, PoseIndex.RIGHT_ELBOW, PoseIndex.RIGHT_WRIST,
                    PoseIndex.RIGHT_HIP, PoseIndex.RIGHT_KNEE, PoseIndex.RIGHT_ANKLE,
                    -> Side.RIGHT
                    else -> null
                }
                fun colourOf(vararg indices: Int): Color {
                    val sides = indices.toList().mapNotNull(::sideOf).toSet()
                    val far = near != null && sides.size == 1 && sides.single() != near
                    return if (far) HoldoutColors.Accent.copy(alpha = 0.35f) else HoldoutColors.Accent
                }

                val bones = listOf(
                    PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_ELBOW,
                    PoseIndex.LEFT_ELBOW to PoseIndex.LEFT_WRIST,
                    PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_ELBOW,
                    PoseIndex.RIGHT_ELBOW to PoseIndex.RIGHT_WRIST,
                    PoseIndex.LEFT_SHOULDER to PoseIndex.LEFT_HIP,
                    PoseIndex.RIGHT_SHOULDER to PoseIndex.RIGHT_HIP,
                    PoseIndex.LEFT_HIP to PoseIndex.LEFT_KNEE,
                    PoseIndex.LEFT_KNEE to PoseIndex.LEFT_ANKLE,
                    PoseIndex.RIGHT_HIP to PoseIndex.RIGHT_KNEE,
                    PoseIndex.RIGHT_KNEE to PoseIndex.RIGHT_ANKLE,
                    PoseIndex.LEFT_SHOULDER to PoseIndex.RIGHT_SHOULDER,
                    PoseIndex.LEFT_HIP to PoseIndex.RIGHT_HIP,
                )
                bones.forEach { (from, to) ->
                    val a = skeleton[from] ?: return@forEach
                    val b = skeleton[to] ?: return@forEach
                    if (a.presence < 0.5f || b.presence < 0.5f) return@forEach
                    drawLine(
                        color = colourOf(from, to),
                        start = project(a),
                        end = project(b),
                        strokeWidth = 4f,
                    )
                }

                val joints = listOf(
                    PoseIndex.LEFT_SHOULDER, PoseIndex.RIGHT_SHOULDER,
                    PoseIndex.LEFT_ELBOW, PoseIndex.RIGHT_ELBOW,
                    PoseIndex.LEFT_WRIST, PoseIndex.RIGHT_WRIST,
                    PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP,
                    PoseIndex.LEFT_KNEE, PoseIndex.RIGHT_KNEE,
                    PoseIndex.LEFT_ANKLE, PoseIndex.RIGHT_ANKLE,
                )
                // Hips and knees a little larger: they are what the body-line,
                // cobra and knees-off-the-floor checks read.
                val keyJoints = setOf(PoseIndex.LEFT_HIP, PoseIndex.RIGHT_HIP, PoseIndex.LEFT_KNEE, PoseIndex.RIGHT_KNEE)

                // The head: a larger dot, joined to the middle of the shoulders.
                val nose = skeleton[PoseIndex.NOSE]
                val ls = skeleton[PoseIndex.LEFT_SHOULDER]
                val rs = skeleton[PoseIndex.RIGHT_SHOULDER]
                if (nose != null && nose.presence >= 0.5f) {
                    if (ls != null && rs != null && ls.presence >= 0.5f && rs.presence >= 0.5f) {
                        val neck = holdout.repcounter.Landmark((ls.x + rs.x) / 2f, (ls.y + rs.y) / 2f, 0f, 1f)
                        drawLine(color = HoldoutColors.Accent, start = project(neck), end = project(nose), strokeWidth = 4f)
                    }
                    drawCircle(color = HoldoutColors.Accent, radius = 14f, center = project(nose))
                }
                joints.forEach { index ->
                    val joint = skeleton[index] ?: return@forEach
                    if (joint.presence < 0.5f) return@forEach
                    drawCircle(
                        color = colourOf(index),
                        radius = if (index in keyJoints) 11f else 8f,
                        center = project(joint),
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
 * 4:3 — the sensor's own shape — on the preview, the analysis **and** the
 * debug recording, bound as one group sharing one crop ([sittingViewPort]).
 *
 * Until 29 Sep preview and analysis were 16:9, a crop of the sensor about 12 %
 * narrower each side, while the recorder took the full 4:3: the video showed a
 * whole body while the counter had already lost the head or the knees at its
 * edge ("Move back" on a set the video shows in frame). Now all three see the
 * same, and the counter gets the widest picture the camera has. The cost: on
 * the Seeker's tall screen `FIT_CENTER` leaves the preview shorter. The
 * placement thresholds were measured in 9:16 and read x through
 * `holdout.repcounter.Aspect`, so they still mean what they did.
 */
private val SITTING_RESOLUTION = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .build()

/**
 * The analysis frame: 4:3 at 960×720 (720×960 upright). Left to its default
 * CameraX gives analysis 640×480, which put a member's arm at about 107 pixels
 * where the 16:9 frame had 270 — the first 4:3 build did exactly that (1 Oct).
 * This is the same width the counter had before; more would cost analysis
 * frames, which must stay at 15 a second or better (research R4).
 */
private val ANALYSIS_RESOLUTION = ResolutionSelector.Builder()
    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
    .setResolutionStrategy(
        androidx.camera.core.resolutionselector.ResolutionStrategy(
            android.util.Size(960, 720),
            androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
        ),
    )
    .build()

/** One crop for every use case: portrait 3:4, the whole sensor. The app is portrait-locked. */
private val sittingViewPort = androidx.camera.core.ViewPort
    .Builder(android.util.Rational(3, 4), android.view.Surface.ROTATION_0)
    .setScaleType(androidx.camera.core.ViewPort.FIT)
    .build()

private fun sittingGroup(vararg useCases: androidx.camera.core.UseCase) = androidx.camera.core.UseCaseGroup.Builder()
    .setViewPort(sittingViewPort)
    .apply { useCases.forEach { addUseCase(it) } }
    .build()

/**
 * CameraX preview plus the analyser, bound to this screen's lifecycle — and in
 * a debug build with [recordVideo] on, the camera saved as video beside the
 * sitting's trace (constitution X, v1.5.0).
 */
@Composable
private fun CameraFeed(viewModel: SittingViewModel, recordVideo: Boolean) {
    val context = LocalContext.current
    val video = remember(recordVideo) {
        if (recordVideo) sittingVideo(context, java.io.File(context.filesDir, "traces"), viewModel.uiState.value.stakes.name.lowercase()) else null
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    // Gravity, so the app knows where the floor is before the model has seen
    // anybody — which is when a phone lying on its back needs saying.
    val phoneTilt = remember { PhoneTilt(context) }
    DisposableEffect(phoneTilt) {
        phoneTilt.start()
        onDispose { phoneTilt.stop() }
    }
    LaunchedEffect(phoneTilt) { phoneTilt.tilt.collect { viewModel.onTilt(it) } }

    val analyzer = remember {
        VisionAnalyzer(
            context = context,
            onPose = { observation -> viewModel.onPoseFrame(observation, observation.backgroundMotion) },
            onError = viewModel::onError,
        )
    }

    // The camera is bound to the Activity's lifecycle, which outlives this
    // screen. So leaving it has to unbind the camera explicitly — otherwise
    // frames keep arriving for a sitting that has ended — and only then close
    // the model, on the analysis thread, after any frame still in flight.
    val bound = remember { BoundCamera() }
    DisposableEffect(Unit) {
        onDispose {
            // Named after the trace, so the video and its landmarks pair up.
            video?.stop(viewModel.uiState.value.traceName)
            bound.release()
            executor.execute { analyzer.close() }
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
                    .setResolutionSelector(ANALYSIS_RESOLUTION)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }

                runCatching {
                    provider.unbindAll()
                    // With video when asked, and the camera can carry all three; a
                    // sitting never fails for want of a recording.
                    val withVideo = video?.let { v ->
                        runCatching { provider.bindToLifecycle(lifecycleOwner, SITTING_CAMERA, sittingGroup(preview, analysis, v.useCase)) }
                            .onFailure { provider.unbindAll() }
                            .getOrNull()
                    }
                    val camera = withVideo ?: provider.bindToLifecycle(lifecycleOwner, SITTING_CAMERA, sittingGroup(preview, analysis))
                    if (withVideo != null) {
                        bound.hold(provider, preview, analysis, video.useCase)
                        video.start()
                        viewModel.onRecordingVideo(true)
                    } else {
                        bound.hold(provider, preview, analysis)
                        if (video != null) viewModel.onRecordingVideo(false)
                    }
                    viewModel.onLens(verticalFovOf(camera.cameraInfo))
                }.onFailure { viewModel.onError("The camera could not start: ${it.message}") }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}

/**
 * What this screen bound to the camera, so leaving the screen can unbind it.
 * Binding happens asynchronously, so the screen may be left before it does; a
 * binding that lands after that is undone on arrival.
 */
private class BoundCamera {
    private var provider: ProcessCameraProvider? = null
    private var useCases: Array<UseCase> = emptyArray()
    private var released = false

    fun hold(provider: ProcessCameraProvider, vararg useCases: UseCase) {
        if (released) {
            (useCases.firstOrNull { it is ImageAnalysis } as? ImageAnalysis)?.clearAnalyzer()
            provider.unbind(*useCases)
            return
        }
        this.provider = provider
        this.useCases = arrayOf(*useCases)
    }

    fun release() {
        released = true
        useCases.filterIsInstance<ImageAnalysis>().forEach { it.clearAnalyzer() }
        provider?.unbind(*useCases)
        provider = null
        useCases = emptyArray()
    }
}

/** The activity behind a Compose context, which may be wrapped (a theme, a locale). */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
