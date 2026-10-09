package holdout.app.sitting

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle

/**
 * Holdout Lab only: what the counter is reading, under the count, to tune it on
 * a real body. Lives in `src/lab` so no member build contains it (spec 004
 * FR-010, contracts §3); the sitting draws it through [LabSittingTools.readouts].
 */
@Composable
fun LabReadouts(state: SittingViewModel.UiState) {
    if (state.traceFailed) {
        Text("rec · not saved", style = LabelStyle, color = HoldoutColors.Warning)
    } else {
        state.traceName?.let {
            Text("rec · $it", style = LabelStyle, color = HoldoutColors.TextTertiary)
        }
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
