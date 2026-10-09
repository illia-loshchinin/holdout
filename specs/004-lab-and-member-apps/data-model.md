# Data Model: Holdout Lab and the Member App

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md)

Nothing here is on chain. §1 describes builds, §2 Lab's on-phone data, §3 the seams.

## 1. Build variants

| Variant | Flavor | Build type | `applicationId` | Name | Tester features | Distribution |
|---|---|---|---|---|---|---|
| `memberDebug` | member | debug | `holdout.app` | Holdout | none (FR-010) | Team development only |
| `memberRelease` | member | release | `holdout.app` | Holdout | none | Members; **the submitted APK** |
| `labDebug` | lab | debug | `holdout.app.lab` | Holdout Lab | all | Team phones over USB |
| `labRelease` | lab | release | `holdout.app.lab` | Holdout Lab | all | Consenting testers, direct install (FR-009) |

**Rules**:
- A `member*` variant compiles only `src/main`, `src/member`, its build-type folder (`src/debug` or `src/release`) and its variant folder.
- A `lab*` variant compiles `src/main`, `src/lab` and the same.
- `checkMemberHasNoLabCode` (contracts §4) runs before every `member*` assemble or bundle.

## 2. Lab set (Lab app storage only)

One per set recorded in Holdout Lab.

| Field | Where | Rule |
|---|---|---|
| Mode | Trace header (`# stakes=`) | `DAILY`, `EASY` or `STAKED`, from the chooser (Daily 25, Easy Holdout, Hard Holdout) |
| Trace | `files/traces/<name>.csv` | Format of 002 data-model, unchanged: landmarks and decisions per frame, never a picture (FR-004) |
| Label | Written into the trace when given | As 002 FR-025: movements counted plus an optional note. Skipping leaves the trace unlabelled |
| Video | `files/traces/<name>.mp4` and `<name>.video.json` | Only when the video switch is on. No audio (FR-005, 002 FR-026) |
| Banking scope | Lab MMKV | `lab:<mode>:<setStartMillis>`: new per set, so a set never inherits reps (R3) |

**Lab settings** (Lab MMKV):
- `recordsVideo`: boolean, default **false** on a fresh install;
- `cleanScreen`: boolean, default false;
- `target`: integer 1–100, default 25.

**Lifecycle**: chosen → setting up → counting → ended (by Stop or by reaching the target) → labelled or skipped → pulled (`pull-traces.sh` copies, checks the size, then deletes from the phone).

## 3. The seams (in `src/main`)

| Seam | Members | Lab implementation | Member implementation |
|---|---|---|---|
| `SittingRecorder` | `record(...)` per analysed frame; `label(expectedReps, done, note, placement, phoneSide)`; `close()`; `val fileName: String?` | `TraceRecorder` | none (`null` passed) |
| `SittingVideoFactory` | `create(context, traceDir): SittingVideo?` (existing) | CameraX `VideoCapture` recorder | returns `null` |
| `LabSittingTools` | `@Composable labelPrompt(onDone)`, `readouts: Boolean`, `cleanScreen: Boolean` | label dialog; readouts on; clean screen from settings | not passed (`null`): no prompt, no readouts, overlay drawn |

**Invariant**: with `recorder = null` and `tools = null`, `SittingViewModel` and `SittingScreen` behave exactly as a member sitting does today (FR-016). A unit test checks that nothing is written.
