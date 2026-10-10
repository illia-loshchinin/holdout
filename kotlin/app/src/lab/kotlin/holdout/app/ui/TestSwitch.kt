package holdout.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing

/** A Holdout Lab switch, labelled for what it does when on and when off. Never in the member app (spec 004). */
@Composable
fun TestSwitch(checked: Boolean, onChange: (Boolean) -> Unit, on: String, off: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = HoldoutColors.Accent))
        Text(if (checked) on else off, style = LabelStyle, color = if (checked) HoldoutColors.Accent else HoldoutColors.TextTertiary)
    }
}

/** Holdout Lab: the switch that records sets as video (constitution X v2.1.0). */
@Composable
fun RecordVideoSwitch(checked: Boolean, onChange: (Boolean) -> Unit) = TestSwitch(
    checked,
    onChange,
    on = "TEST · recording sittings as video — only people who agreed in writing to be filmed",
    off = "TEST · video recording off — only people who agreed in writing may be filmed",
)
