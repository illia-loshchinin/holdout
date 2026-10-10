package holdout.app.sitting

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import holdout.app.ui.theme.CounterStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.Spacing
import holdout.repcounter.Trace

/**
 * Holdout Lab: what the tester actually did in the set that just ended,
 * written into its trace so the pulled file is already a test
 * (`holdout.repcounter.Trace.label`). The person's count is the only ground
 * truth there is; the app's own count is shown for comparison, never assumed.
 */
@Composable
fun TraceLabelDialog(
    counted: Int,
    onSave: (done: Map<String, Int>, note: String, placement: String?, phoneSide: String?) -> Unit,
    onSkip: () -> Unit,
) {
    val done = remember { mutableStateMapOf<String, Int>() }
    var note by remember { mutableStateOf("") }
    // Kept from the last sitting: a calibration session records many sets from one spot.
    var placement by remember { mutableStateOf(lastPlacement) }
    var phoneSide by remember { mutableStateOf(lastPhoneSide) }

    Dialog(onDismissRequest = onSkip, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.screenGutter)
                .background(HoldoutColors.Surface1, RoundedCornerShape(24.dp))
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
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

            Text("Where was the phone?", style = MaterialTheme.typography.bodyLarge, color = HoldoutColors.TextPrimary)
            Choices(Trace.PLACEMENTS, placement, { if (it == "front" || it == "side") it else "$it°" }) { placement = it }
            Choices(Trace.SIDES, phoneSide, { "on my $it" }) { phoneSide = it }

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
                    onClick = {
                        lastPlacement = placement
                        lastPhoneSide = phoneSide
                        onSave(done.toMap(), note, placement, phoneSide)
                    },
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
            TextButton(onClick = { onChange(value - 1) }, modifier = Modifier.semantics { contentDescription = "Fewer ${label.lowercase()}" }) {
                Text("−", style = MaterialTheme.typography.titleLarge, color = HoldoutColors.Accent)
            }
            Text(
                "$value",
                style = CounterStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                color = HoldoutColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(40.dp),
            )
            TextButton(onClick = { onChange(value + 1) }, modifier = Modifier.semantics { contentDescription = "More ${label.lowercase()}" }) {
                Text("+", style = MaterialTheme.typography.titleLarge, color = HoldoutColors.Accent)
            }
        }
    }
}

/** One choice of several, tapped again to clear. Wraps onto as many rows as it needs. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Choices(options: List<String>, selected: String?, label: (String) -> String, onChange: (String?) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.tight),
    ) {
        options.forEach { option ->
            androidx.compose.material3.FilterChip(
                selected = option == selected,
                onClick = { onChange(if (option == selected) null else option) },
                label = { Text(label(option)) },
            )
        }
    }
}

/**
 * Holdout Lab: the set that just ended was not saved — its trace could not be
 * written (storage full, a write refused) or no frame ever reached it (the
 * camera never started, or Stop came first). Shown instead of
 * [TraceLabelDialog], so nobody labels a set the team will never receive.
 */
@Composable
fun TraceNotSavedDialog(onOk: () -> Unit) {
    Dialog(onDismissRequest = onOk, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.screenGutter)
                .background(HoldoutColors.Surface1, RoundedCornerShape(24.dp))
                .padding(Spacing.cardPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.tight),
        ) {
            Text("This set was not saved", style = MaterialTheme.typography.titleLarge, color = HoldoutColors.Warning)
            Text(
                "No whole trace of it is on the phone — none was written, or it was cut short — so it will not reach the team. Check that the camera starts and the phone has space, then record it again.",
                style = MaterialTheme.typography.bodyMedium,
                color = HoldoutColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = onOk,
                shape = MaterialTheme.shapes.extraLarge,
                colors = ButtonDefaults.buttonColors(
                    containerColor = HoldoutColors.TextPrimary,
                    contentColor = HoldoutColors.OnLight,
                ),
            ) { Text("OK", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

/** The last placement saved, for the next sitting's dialog — this process only. */
private var lastPlacement: String? = null
private var lastPhoneSide: String? = null

private val LABELS = mapOf(
    "honest" to "Full push-ups",
    "half" to "Half push-ups",
    "knees" to "From the knees",
    "cobra" to "Cobra",
    "other" to "Other movement",
)
