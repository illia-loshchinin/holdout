package holdout.app.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import holdout.app.ui.Notice
import holdout.app.ui.PrimaryButton
import holdout.app.ui.SecondaryButton
import holdout.app.ui.theme.HoldoutColors

/** The colour of a [DayState] title. */
internal fun DayTone.color(): Color = when (this) {
    DayTone.Plain -> HoldoutColors.TextPrimary
    DayTone.Accent -> HoldoutColors.Accent
    DayTone.Success -> HoldoutColors.Success
    DayTone.Warning -> HoldoutColors.Warning
}

/**
 * The day's button, as Today and Standing's day strip both draw it: Start,
 * Keep going or Submit day N run [onStart] or [onSubmit] with the day's index; a
 * day short of SOL says so, with its way out; a send under way is the busy button.
 */
@Composable
internal fun DayActions(day: DayState, onStart: () -> Unit, onSubmit: (dayIndex: Int) -> Unit, onGetTokens: () -> Unit) {
    if (day.phase == DayPhase.ShortOfSol) Notice(DayState.NO_FEE, warning = true)
    val button = day.button ?: return
    PrimaryButton(
        button.label,
        {
            if (button.action == DayAction.Start) {
                onStart()
            } else if (button.action == DayAction.Submit) {
                onSubmit(day.dayIndex)
            }
        },
        enabled = button.enabled,
        busy = button.busy,
    )
    if (day.phase == DayPhase.ShortOfSol) SecondaryButton("Get test tokens", onGetTokens)
}
