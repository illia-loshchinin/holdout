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
| `./gradlew :app:assembleLabDebug` / `assembleLabRelease` | Holdout Lab |
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
    fun record(/* the arguments TraceRecorder.record takes today */)
    fun label(expectedReps: Int, done: Map<String, Int>, note: String, placement: String? = null, phoneSide: String? = null)
    fun close()
}

class LabSittingTools(
    val readouts: Boolean,
    val cleanScreen: Boolean,
    val labelPrompt: @Composable (viewModel: SittingViewModel, onDone: () -> Unit) -> Unit,
)
```

- `SittingViewModel(…, recorder: SittingRecorder? = null, …)` replaces `traceDir: File?`.
- `SittingScreen(…, tools: LabSittingTools? = null, …)` replaces `cleanScreen` and `recordVideo` and every `BuildConfig.DEBUG` check in it.

## 4. Build check

`CheckMemberHasNoLabCode` (renamed from `CheckReleaseHasNoRecorder`):

| | |
|---|---|
| **Scans** | `src/main`, `src/member`, `src/debug`, `src/release`, `src/memberDebug`, `src/memberRelease` (`kotlin` and `java` folders) |
| **Fails on** | any file containing `androidx.camera.video`, `TraceRecorder`, `TraceLabelDialog`, `TestSwitch` or `LabApp` |
| **Runs before** | every task named `assembleMember*` or `bundleMember*` |
| **Message** | `Member-app code contains Holdout Lab code: <files>. Only src/lab may record, label or show tester switches (constitution X v2.1.0, spec 004 FR-010).` |

## 5. Holdout Lab screens

| Screen | Shows | Actions |
|---|---|---|
| **Choose** | Title "Holdout Lab"; three rows: *Daily 25*, *Easy Holdout · knees allowed · 40–60°*, *Hard Holdout · from the toes · 45°*; the switches *Record video* (consent wording from `RecordVideoSwitch`), *Clean screen*; *Target* (default 25); the count of traces on the phone | Tap a row: start a set |
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
