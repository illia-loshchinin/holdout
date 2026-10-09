# Contract: Variants, Seams and the Build Check

**Feature**: [../spec.md](../spec.md) · **Data model**: [../data-model.md](../data-model.md)

## 1. Gradle (in `kotlin/app/build.gradle.kts`)

```kotlin
android {
    flavorDimensions += "app"
    productFlavors {
        create("member") { dimension = "app" }                       // holdout.app
        create("lab") { dimension = "app"; applicationIdSuffix = ".lab" }  // holdout.app.lab
    }
}
dependencies { "labImplementation"(libs.androidx.camera.video) }   // was debugImplementation
```

Commands from `kotlin/`:

| Command | Builds |
|---|---|
| `./gradlew :app:assembleMemberRelease` | The submitted APK |
| `./gradlew :app:assembleMemberDebug` | Member app for development |
| `./gradlew :app:assembleLabDebug` | Holdout Lab, the build testers install (debuggable, so `pull-traces.sh` can read it) |
| `./gradlew :app:assembleLabRelease` | Holdout Lab release: built and checked, not distributed |
| `./gradlew :app:checkMemberHasNoLabCode` | The check alone (§4) |

Every existing command that named a variant changes: `assembleDebug` → `assembleMemberDebug`, `assembleRelease` → `assembleMemberRelease`, `testDebugUnitTest` → `testMemberDebugUnitTest`. That covers the README, quickstarts and CI workflows (tasks list each file).

## 2. Source-set ownership

| File | Before | After |
|---|---|---|
| `sitting/TraceRecorder.kt` | `src/main` | `src/lab` (implements `SittingRecorder`) |
| `sitting/TraceLabelDialog.kt` | `src/main` | `src/lab` |
| `ui/TestSwitch.kt` (`TestSwitch`, `RecordVideoSwitch`) | `src/main` | `src/lab` |
| `sitting/SittingVideoFactory.kt` (recorder) | `src/debug` | `src/lab` |
| `sitting/SittingVideoFactory.kt` (null) | `src/release` | `src/member` |
| `AppRoot.kt` | — | `src/member` (→ `HoldoutApp`) and `src/lab` (→ `LabApp`) |
| `lab/LabApp.kt` | — | `src/lab` |
| `sitting/SittingRecorder.kt`, `sitting/LabSittingTools.kt` | — | `src/main` (interfaces only) |
| `assets/weaknesses.json` | `src/main` | `docs/weaknesses.json` (not in any APK) |
| `src/debug/AndroidManifest.xml`, `res/xml/network_security_config.xml` | `src/debug` | unchanged (research R9) |

## 3. Seam signatures (in `src/main`)

```kotlin
interface SittingRecorder {
    val fileName: String?
    val failed: Boolean   // true once the trace could not be written: the set is not saved
    fun record(/* the arguments TraceRecorder.record takes today */)
    // true once the label is in the file; written to <name>.tmp and moved over the trace atomically,
    // so a failure (storage full) leaves the unlabelled trace whole
    fun label(expectedReps: Int, done: Map<String, Int>, note: String, placement: String? = null, phoneSide: String? = null): Boolean
    fun close()
}

class LabSittingTools(
    val readouts: (@Composable (state: SittingViewModel.UiState) -> Unit)?,   // LabReadouts, or null under clean screen
    val cleanScreen: Boolean,
    val recordVideo: Boolean,
    val labelPrompt: @Composable (counted: Int, onSave: (done: Map<String, Int>, note: String, placement: String?, phoneSide: String?) -> Unit, onSkip: () -> Unit) -> Unit,
    val notSavedNotice: @Composable (onOk: () -> Unit) -> Unit,
    val onSetProblem: (what: String) -> Unit,   // a label or video lost after the set screen has gone
)

fun sittingVideo(context: Context, dir: File, stakes: String, onFailed: (what: String) -> Unit): SittingVideo?   // member: null
```

- When a Lab set ends and `recorder.failed` is true, `SittingScreen` shows `notSavedNotice` instead of `labelPrompt`, and `LabReadouts` shows "rec · not saved" (spec edge case: the phone runs out of space during a Lab set). A set whose trace failed is never labelled.
- **Losses after the set** (same edge case): a label that could not be written (`label` returns false) and a video that did not finalize whole (a CameraX `Finalize` error other than the camera closing, an empty file, or a failed rename) are reported through `onSetProblem`. The video finalizes only after the set screen has gone, so Lab shows these on **Choose** as a dismissible "not saved" card naming what was lost. The trace itself stays on the phone either way.

- `SittingViewModel(…, recorder: SittingRecorder? = null, …)` replaces `traceDir: File?`.
- `SittingScreen(…, tools: LabSittingTools? = null, …)` replaces `cleanScreen` and `recordVideo` and every `BuildConfig.DEBUG` check in it.
- `SittingScreen` calls `tools?.readouts?.invoke(state)` under the count and holds no readout text of its own: the readout UI (`LabReadouts`, in `src/lab`) is never compiled into a member build (FR-010).

## 4. Build check

`CheckMemberHasNoLabCode` (renamed from `CheckReleaseHasNoRecorder`):

| | |
|---|---|
| **Scans** | `src/main`, `src/member`, `src/debug`, `src/release`, `src/memberDebug`, `src/memberRelease` (`kotlin` and `java` folders) |
| **Fails on** | any file containing `androidx.camera.video`, `TraceRecorder`, `TraceLabelDialog`, `TestSwitch`, `LabApp`, `LabReadouts`, or the literal `"rec · ` |
| **Runs before** | every task named `assembleMember*` or `bundleMember*` |
| **Message** | `Member-app code contains Holdout Lab code: <files>. Only src/lab may record, label, show readouts or show tester switches (constitution X v2.1.0, spec 004 FR-010).` |

## 5. Holdout Lab screens

| Screen | Shows | Actions |
|---|---|---|
| **Choose** | Title "Holdout Lab"; three rows: *Daily 25*, *Easy Holdout · knees allowed · 40–60°*, *Hard Holdout · from the toes · 45°*; the switches *Record video* (consent wording from `RecordVideoSwitch`), *Clean screen*; *Target* (default 25); the count of traces on the phone; a "not saved" card for each label or video the last sets lost, until dismissed | Tap a row: start a set |
| **Set** | The shared sitting: set-up guide, placement checks, counting; readouts on (unless clean screen) | Stop |
| **Label** | The existing label dialog (002 FR-025) | Save, or skip; then back to Choose |

No wallet, network, faucet, Holdout, tour or About appears in Lab.

## 6. Member app: removed and kept (FR-012–FR-015)

| Removed | Kept |
|---|---|
| `WalkthroughScreen`, `Route.Walkthrough`, `beforeWallet` walkthrough | Connect screen, no-wallet help |
| Connect screen's "For judges: watch a full cycle" or "Watch a full cycle (3 min)" link | "Test network, test token." everywhere it is today |
| About's "Watch a full cycle", known weaknesses, scripted-wallet list | **About Holdout**: devnet notice, the tour again, Disconnect wallet with its network note, one sentence on scripted members |
| Home's four TEST switches; sitting readouts and "rec ·" line; label prompt | First-run scripted disclosure, SCRIPTED labels, TEST COHORT labels, the short test lobby |
| Links named "About · for judges" and "About · wallet" | One link named **About Holdout** in both places |
