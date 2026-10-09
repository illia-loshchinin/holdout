# Implementation Plan: Holdout Lab and the Member App

**Branch**: `004-lab-and-member-apps` | **Date**: 2026-10-09 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/004-lab-and-member-apps/spec.md`

## Summary

One Gradle module, two product flavors.

- **`member`** (`holdout.app`) is the app members use and judges install.
- **`lab`** (`holdout.app.lab`, "Holdout Lab") is the tester app.

Shared code (camera, vision, `repcounter`, the sitting screen, banking, theme) stays in `src/main`, so both apps count from one source. Lab-only code moves into `src/lab`, so member code can't reference it: the trace recorder, the video recorder, the label dialog, the test switches and the readouts. Three small interfaces in `src/main` (`SittingRecorder`, `SittingVideoFactory`, `LabSittingTools`) are the only points of contact.

**Holdout Lab** opens on a mode chooser (Daily 25, Easy, Hard), then runs the shared sitting with no wallet and no chain. Each set writes a trace, optionally a video, and asks for a label.

**The member app:**
- loses every `BuildConfig.DEBUG` tester feature and the judge-only parts: the walkthrough, the "For judges" link, and the About screen's weaknesses and scripted-wallet lists;
- keeps a plain "About Holdout" for the tour, Disconnect and the devnet notice;
- gets a renamed build check, `checkMemberHasNoLabCode`, which fails any member build that contains lab code;
- loses the weaknesses source from its assets, which moves to `docs/` for the README.

## Technical Context

**Language/Version**: Kotlin 2.4.10, AGP 9.3.2, Gradle 9.7.1 (001 plan).

**Primary Dependencies**: Unchanged. The only move is that `androidx.camera.video` goes from `debugImplementation` to `labImplementation`. No dependency is added.

**Storage**: Per app, by `applicationId`: each app has its own MMKV and `files/traces`. Lab writes traces (002 data-model) and optional videos. The member app writes no traces.

**Testing**:
- JUnit 4 app tests (`src/test`, plus `src/testLab` for Lab-only code);
- `:repcounter` `RecordedTracesTest` for SC-003;
- the Gradle build check, run both ways;
- a manual phone check (quickstart).

**Target Platform**: Android, minSdk 26. Seeker first, then any Android phone with a camera for Lab.

**Project Type**: The existing mobile app. Its one module gains a flavor dimension.

**Performance Goals**: Unchanged. Pose analysis holds ≥ 15 FPS with video on in Lab (002 FR-026). Lab reaches counting in under 60 s from launch (SC-001).

**Constraints**:
- Constitution X v2.1.0: no recorder in any member build, enforced by a build check.
- The member APK submitted on 13 Oct is `memberRelease`.
- No contract, crank or `:core`/`:repcounter` change.
- Member behaviour changes only where FR-010 and FR-012 say (FR-016).

**Scale/Scope**:
- **Moved:** about 6 files (`TraceRecorder`, `TraceLabelDialog`, `TestSwitch`, the two `SittingVideoFactory`, `weaknesses.json`).
- **New:** about 4 files (`LabApp`, `AppRoot` × 2, the seam interfaces).
- **Edited:** about 8 files (`HoldoutApp`, `HomeScreen`, `CohortHome`, `ConnectScreen`, `AboutScreen`, `SittingScreen`, `SittingViewModel`, `TourScreen`).
- **Plus:** `build.gradle.kts`, lab resources and two scripts.

No `NEEDS CLARIFICATION` items remain; see [research.md](research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate | Pre-research | Post-design evidence |
|---|---|---|---|
| **I–V** (funds, forfeiture, no oracle, SKR, cohort parameters) | Contract-side rules | PASS | No contract, crank or chain-logic change. Lab never touches a wallet or chain (FR-002) |
| **VI. One clock** | Chain time for deadlines | PASS | Member app unchanged. Lab has no deadlines: `chainSecondsNow = { null }` (R3) |
| **VII. Tests precede logic** | Tests first | PASS | Tasks put the seam tests (R10) and the build-check proof before the moves |
| **VIII. Idempotent automation** | — | PASS | No automation touched |
| **IX. Every state has a screen** | Every state visible | PASS | No member state is removed. The walkthrough was not a state; About remains for Disconnect and the tour (R5). Lab's states: chooser, set, label |
| **X. Camera frames never leave the device** (v2.1.0) | Recorder only in Holdout Lab; member builds can't contain it; build check | PASS | Lab-only code sits in `src/lab`, so member variants can't compile a reference (R2). `checkMemberHasNoLabCode` runs before every member assemble and bundle (R4). Video stays on-device, pulled by `pull-traces.sh` and deleted (R7) |
| **XI. Weaknesses documented** | Named in the README | PASS | Weaknesses render to the README from `docs/weaknesses.json` (R6) |
| **XII. The build demonstrates itself** | Live crowd; scripted members disclosed and labelled | PASS | First-run disclosure and SCRIPTED labels unchanged. The test lobby stays, open to anyone (FR-014) |
| **XIII. Honest language** | — | PASS | Copy unchanged except removed judge text and "About Holdout" |
| **Hackathon constraints** | Devnet only; signed APK; test lobby labelled and open to anyone | PASS | `memberRelease` is the signed APK. Lab is never distributed (FR-009) |
| **Development workflow** | Spec changes through skills | PASS | Constitution v2.1.0 and 001/002/003 already amended through their skills. 001's plan, tasks and `contracts/android-app.md` follow in Polish |

**Gate result: PASS.** No violations.

## Project Structure

### Documentation (this feature)

```text
specs/004-lab-and-member-apps/
├── spec.md
├── plan.md              # This file
├── research.md          # R1–R10
├── data-model.md        # Variants, Lab set, the seams
├── quickstart.md        # Build both apps, prove the check, phone walk
├── contracts/
│   └── variants-and-seams.md   # Variant matrix, source-set ownership, seams, build check, Lab screens
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks
```

### Source Code

```text
kotlin/app/
├── build.gradle.kts                 # flavorDimensions("app"); member/lab; labImplementation(camera-video);
│                                    # CheckMemberHasNoLabCode before every assembleMember*/bundleMember*
└── src/
    ├── main/kotlin/holdout/app/
    │   ├── MainActivity.kt          # calls AppRoot(sender, modifier)
    │   ├── HoldoutApp.kt            # − DEBUG test switches, − Route.Walkthrough, − beforeWallet walkthrough
    │   ├── about/AboutScreen.kt     # "About Holdout": − walkthrough, − weaknesses, − scripted list
    │   ├── home/HomeScreen.kt       # − stakedTest/cleanScreen/recordVideo/resetToday
    │   ├── home/CohortHome.kt       # "About · wallet" → "About Holdout"
    │   ├── wallet/ConnectScreen.kt  # − onWalkthrough link
    │   ├── tour/TourScreen.kt       # comment only
    │   └── sitting/
    │       ├── SittingRecorder.kt   # NEW seam (interface)
    │       ├── LabSittingTools.kt   # NEW seam (label prompt slot, readouts, clean screen)
    │       ├── SittingVideo.kt      # seam, unchanged
    │       ├── SittingScreen.kt     # − BuildConfig.DEBUG; takes LabSittingTools? = null
    │       └── SittingViewModel.kt  # takes recorder: SittingRecorder? instead of traceDir
    ├── member/
    │   ├── kotlin/holdout/app/AppRoot.kt                       # → HoldoutApp
    │   └── kotlin/holdout/app/sitting/SittingVideoFactory.kt   # from src/release: returns null
    ├── lab/
    │   ├── kotlin/holdout/app/AppRoot.kt                       # → LabApp
    │   ├── kotlin/holdout/app/lab/LabApp.kt                    # NEW: mode chooser, switches, set, label
    │   ├── kotlin/holdout/app/sitting/TraceRecorder.kt         # from src/main
    │   ├── kotlin/holdout/app/sitting/TraceLabelDialog.kt      # from src/main
    │   ├── kotlin/holdout/app/sitting/SittingVideoFactory.kt   # from src/debug
    │   ├── kotlin/holdout/app/ui/TestSwitch.kt                 # from src/main
    │   └── res/                                                # app_name "Holdout Lab", lab icon (R8)
    ├── debug/       # keeps only AndroidManifest.xml + network_security_config (R9)
    ├── release/     # emptied (its stub moved to src/member)
    ├── test/        # seam tests (R10)
    └── testLab/     # Lab mode mapping, per-set scope

docs/weaknesses.json                 # moved from app/src/main/assets (R6)
scripts/render-weaknesses.mjs        # new source path
scripts/pull-traces.sh               # default package holdout.app.lab; --package option (R7)
```

**Structure Decision**: Flavors in the existing `:app` module (research R1). It is the smallest change that makes the member app structurally free of Lab code while keeping one counting source. A separate `:lab` module remains a later option.

## Complexity Tracking

No violations to justify.
