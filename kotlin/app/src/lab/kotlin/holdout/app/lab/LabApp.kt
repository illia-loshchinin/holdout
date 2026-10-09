package holdout.app.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import holdout.app.sitting.LabSittingTools
import holdout.app.sitting.SittingScreen
import holdout.app.sitting.SittingViewModel
import holdout.app.sitting.TraceLabelDialog
import holdout.app.sitting.TraceNotSavedDialog
import holdout.app.sitting.TraceRecorder
import holdout.app.storage.MmkvStore
import holdout.app.storage.Store
import holdout.app.streak.StreakStore
import holdout.app.ui.Body
import holdout.app.ui.HoldoutCard
import holdout.app.ui.RecordVideoSwitch
import holdout.app.ui.SecondaryButton
import holdout.app.ui.SectionLabel
import holdout.app.ui.TestSwitch
import holdout.app.ui.theme.AmountStyle
import holdout.app.ui.theme.HoldoutColors
import holdout.app.ui.theme.LabelStyle
import holdout.app.ui.theme.Spacing
import java.io.File

/**
 * Holdout Lab (spec 004): record and label push-up sets to tune rep counting.
 *
 * ```
 *  Holdout Lab                                   headlineMedium
 *  Record and label sets …                       Body
 *  COUNT AS                                      SectionLabel
 *  [ Daily 25 ]        45° · knees allowed       SecondaryButton + LabelStyle
 *  [ Easy Holdout ]    …
 *  [ Hard Holdout ]    …
 *  TEST · record video / clean screen            TestSwitch
 *  TARGET   − 25 +                               AmountStyle
 *  12 sets on this phone · pull them over USB    LabelStyle
 * ```
 *
 * No wallet and no network (FR-002): Lab builds no wallet, RPC or chain client,
 * and its manifest drops the INTERNET permission. A set is the member app's own
 * sitting — same set-up guide, checks and counting — plus a trace, an optional
 * video and the label question at the end.
 */
@Composable
fun LabApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val store: Store = remember { MmkvStore.create(context) }
    val streaks = remember { StreakStore(store) }
    val settings = remember { LabSettings(MmkvLabPrefs()) }
    val traces = remember { File(context.filesDir, "traces") }
    var set by remember { mutableStateOf<LabSet?>(null) }

    val current = set
    if (current == null) {
        LabChooser(settings, traces, onStart = { mode -> set = LabSet(mode, System.currentTimeMillis(), settings.target) }, modifier = modifier)
        return
    }

    val vm = remember(current) {
        SittingViewModel(
            store = store,
            streaks = streaks,
            wallet = LAB_WALLET,
            scope = labScope(current.mode, current.startMillis),
            dayIndex = Store.UNKNOWN_DAY,
            target = current.target,
            chainSecondsNow = { null },
            stakes = current.mode.stakes,
            rollsOver = false,
            recorder = TraceRecorder(traces, current.mode.stakes),
        )
    }
    DisposableEffect(vm) { onDispose { vm.release() } }
    val back = { set = null }
    SittingScreen(
        viewModel = vm,
        onFinished = back,
        onCancelled = { back() },
        modifier = modifier,
        finishLabel = "Back to Lab",
        tools = LabSittingTools(
            readouts = !settings.cleanScreen,
            cleanScreen = settings.cleanScreen,
            recordVideo = settings.recordsVideo,
            labelPrompt = { counted, onSave, onSkip -> TraceLabelDialog(counted, onSave, onSkip) },
            notSavedNotice = { onOk -> TraceNotSavedDialog(onOk) },
        ),
    )
}

private data class LabSet(val mode: LabMode, val startMillis: Long, val target: Int)

/** Lab sets are no one's: they bank under this name, never a wallet's. */
private const val LAB_WALLET = "lab"

@Composable
private fun LabChooser(settings: LabSettings, traces: File, onStart: (LabMode) -> Unit, modifier: Modifier = Modifier) {
    var recordsVideo by remember { mutableStateOf(settings.recordsVideo) }
    var cleanScreen by remember { mutableStateOf(settings.cleanScreen) }
    var target by remember { mutableIntStateOf(settings.target) }
    val onPhone = remember { traces.listFiles { f -> f.name.endsWith(".trace.csv") }?.size ?: 0 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HoldoutColors.Canvas)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screenGutter),
        verticalArrangement = Arrangement.spacedBy(Spacing.betweenItems),
    ) {
        Text("Holdout Lab", style = MaterialTheme.typography.headlineMedium, color = HoldoutColors.TextPrimary)
        Body("Record and label sets to tune rep counting. No wallet and no network: nothing here is staked or sent.")

        SectionLabel("Count as")
        LabMode.entries.forEach { mode ->
            HoldoutCard {
                SecondaryButton(mode.label, onClick = { onStart(mode) }, modifier = Modifier.fillMaxWidth())
                Text(mode.detail, style = LabelStyle, color = HoldoutColors.TextTertiary)
            }
        }

        SectionLabel("Set")
        RecordVideoSwitch(recordsVideo) {
            recordsVideo = it
            settings.recordsVideo = it
        }
        TestSwitch(
            checked = cleanScreen,
            onChange = {
                cleanScreen = it
                settings.cleanScreen = it
            },
            on = "TEST · clean screen: no dots or text, for training recordings",
            off = "TEST · clean screen off",
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.tight)) {
            Text("TARGET", style = LabelStyle, color = HoldoutColors.TextTertiary)
            TextButton(
                onClick = {
                    settings.target = target - 1
                    target = settings.target
                },
                modifier = Modifier.sizeIn(minWidth = Spacing.touchTarget, minHeight = Spacing.touchTarget).semantics { contentDescription = "Fewer reps" },
            ) { Text("−", style = AmountStyle, color = HoldoutColors.Accent) }
            Text("$target", style = AmountStyle, color = HoldoutColors.TextPrimary)
            TextButton(
                onClick = {
                    settings.target = target + 1
                    target = settings.target
                },
                modifier = Modifier.sizeIn(minWidth = Spacing.touchTarget, minHeight = Spacing.touchTarget).semantics { contentDescription = "More reps" },
            ) { Text("+", style = AmountStyle, color = HoldoutColors.Accent) }
        }

        Text(
            "$onPhone set${if (onPhone == 1) "" else "s"} on this phone · pull them over USB with scripts/pull-traces.sh",
            style = LabelStyle,
            color = HoldoutColors.TextTertiary,
        )
    }
}
