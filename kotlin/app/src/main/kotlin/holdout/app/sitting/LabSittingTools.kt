package holdout.app.sitting

import androidx.compose.runtime.Composable

/**
 * What Holdout Lab adds to a sitting (spec 004, contracts §3). The member app
 * passes none to [SittingScreen], which then shows a member's sitting and
 * nothing else: no readouts, no label question, no video.
 *
 * Only plain values and a composable slot cross this seam, so the label form
 * and the video recorder stay in `src/lab`, where member code cannot reach them.
 */
class LabSittingTools(
    /** What the counter is reading, on screen during the set. */
    val readouts: Boolean,
    /** Nothing drawn over the camera, for recordings that train the knee classifier. */
    val cleanScreen: Boolean,
    /** Save the camera as video beside the trace (constitution X v2.1.0). */
    val recordVideo: Boolean,
    /** Asks what was really done once a set that wrote a trace ends (002 FR-025). */
    val labelPrompt: @Composable (
        counted: Int,
        onSave: (done: Map<String, Int>, note: String, placement: String?, phoneSide: String?) -> Unit,
        onSkip: () -> Unit,
    ) -> Unit,
    /** Tells the tester a set that ended was not saved, because its trace could not be written. */
    val notSavedNotice: @Composable (onOk: () -> Unit) -> Unit,
)
