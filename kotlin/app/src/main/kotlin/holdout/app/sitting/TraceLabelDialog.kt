package holdout.app.sitting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.repcounter.Trace

/**
 * Debug builds: what the member actually did in the sitting that just ended,
 * written into its trace so the pulled file is already a test
 * (`holdout.repcounter.Trace.label`). The person's count is the only ground
 * truth there is; the app's own count is shown for comparison, never assumed.
 */
@Composable
fun TraceLabelDialog(
    counted: Int,
    onSave: (done: Map<String, Int>, note: String) -> Unit,
    onSkip: () -> Unit,
) {
    val done = remember { mutableStateMapOf<String, Int>() }
    var note by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onSkip, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.screenGutter)
                .background(HoldoutColors.Surface1, RoundedCornerShape(24.dp))
                .padding(Spacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            Text("What did you just do?", style = MaterialTheme.typography.titleLarge, color = HoldoutColors.TextPrimary)
            Text(
                "The app counted $counted. Log what you really did — it goes into this set's trace.",
                style = MaterialTheme.typography.bodyMedium,
                color = HoldoutColors.TextSecondary,
                textAlign = TextAlign.Center,
            )

            Trace.MOVEMENTS.forEach { movement ->
                Counter(
                    label = LABELS.getValue(movement),
                    value = done[movement] ?: 0,
                    onChange = { done[movement] = it.coerceAtLeast(0) },
                )
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional) — e.g. phone on a book") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) { Text("Skip", color = HoldoutColors.TextSecondary) }
                Button(
                    onClick = { onSave(done.toMap(), note) },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HoldoutColors.TextPrimary,
                        contentColor = HoldoutColors.OnLight,
                    ),
                ) { Text("Save", style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
private fun Counter(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(value - 1) }) { Text("−", style = MaterialTheme.typography.titleLarge, color = HoldoutColors.Accent) }
            Text(
                "$value",
                style = CounterStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                color = HoldoutColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )
            TextButton(onClick = { onChange(value + 1) }) { Text("+", style = MaterialTheme.typography.titleLarge, color = HoldoutColors.Accent) }
        }
    }
}

private val LABELS = mapOf(
    "honest" to "Full push-ups",
    "half" to "Half push-ups",
    "knees" to "From the knees",
    "cobra" to "Cobra",
    "other" to "Other movement",
)
