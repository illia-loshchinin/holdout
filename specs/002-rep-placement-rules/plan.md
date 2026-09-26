# Implementation Plan: Rep Placement and Form Rules

**Branch**: `002-rep-placement-rules` | **Date**: 2026-09-26 (as built) | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-rep-placement-rules/spec.md`

**Note**: This plan is written after the fact. The recognition work was built and tuned against the phone between 23 and 26 September; the plan records what exists, why, and what is left, so that `specs/001-holdout-hackathon` can build on it.

## Summary

Make the push-up counter work from where people actually put the phone — on the floor, in front of them or turned to about 45° — and make staked sittings strict enough to be fair. The counter reads the **picture** (MediaPipe image landmarks) rather than the 3D world estimate, because depth along the line of sight is what the model gets worst. At 45° it measures the side of the body nearer the lens. Each rep is judged on **medians over the rep** and over its **push phase** (from the bottom until the elbow opens past 100°), which is where knees on the floor and cobra arches show. Stakes decide which placements are accepted (Daily 25: head-on or 45°; staked: 45° only) and whether knees are judged (staked only). Debug builds record a landmark trace per sitting, ask what was really done when the sitting ends, and those traces replay as JUnit tests that must count within one rep.

## Technical Context

**Language/Version**: Kotlin 2.4.10, Java 17 bytecode (as `specs/001-holdout-hackathon/plan.md`).

**Primary Dependencies**: MediaPipe Tasks Vision Pose Landmarker (full model, GPU, live stream), CameraX, Jetpack Compose, MMKV, Android `TextToSpeech` and `ToneGenerator`. No new dependency.

**Storage**: MMKV for the set-up guide preference (`setup_hidden:<wallet>:<stakes>`). Debug builds only: trace files in the app's private `files/traces/`.

**Testing**: JUnit 5 in `kotlin/repcounter` — synthetic geometry tests per rule, plus `RecordedTracesTest` replaying every committed trace in `fixtures/rep-traces/` against its person-counted `expectedReps` (±1). 87 tests at 2026-09-26.

**Target Platform**: Android 8.0+; tuned on a Solana Seeker.

**Project Type**: Mobile app (the `repcounter` JVM module plus the `app` module's sitting screens).

**Performance Goals**: Pose inference ≥ 15 FPS (unchanged from 001); a counted rep's tone within 300 ms of the top.

**Constraints**: Camera frames never leave the device (constitution X) — traces hold landmark numbers only. No microphone permission. Every threshold is stated with the measurements behind it (constitution XI).

**Scale/Scope**: One exercise (push-ups), two stakes, two accepted placements. Thresholds rest on one person's seventeen labelled traces.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| V. Parameters live on the cohort | Pass | Stakes select the placement and knee rule; reps per day and exercise still come from the cohort (001 T065). |
| VII. Tests precede logic | Pass | Every threshold change landed with a synthetic test or a new labelled phone trace that failed first. |
| IX. Every state has a screen | Pass | Refused placement, knees down, cobra, too shallow and head out of view each have a shown and spoken hint. |
| X. Camera frames never leave the device | Pass | Traces are landmark numbers in private storage, debug builds only, pulled by the developer over USB. The deferred classifier would need training images: blocked until a constitution note says how a member's own recordings may be shared (research D9). |
| XI. Weaknesses documented | Pass | Known weaknesses listed in the spec's assumptions and README: one-person tuning, head-on knees, occasional cobra, the ±10° body-line rule at 45°. |
| XIII. Honest language | Pass | The staked rule sentence is plain, and shown before staking (FR-012; the stake sheet itself is 001 T059). |

Post-design re-check: no change.

## Project Structure

### Documentation (this feature)

```text
specs/002-rep-placement-rules/
├── plan.md              # This file
├── research.md          # Decisions and the measurements behind them
├── data-model.md        # Stakes, placement, readings, rules, traces
├── quickstart.md        # How to check it on a phone and in tests
├── contracts/
│   ├── trace-format.md  # The trace file, v2
│   └── words.md         # Everything the member is shown or told
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
kotlin/repcounter/src/main/kotlin/holdout/repcounter/
├── Pose.kt           # Landmark, PoseFrame, ImagePose, PoseIndex (NOSE added)
├── Angles.kt         # image-plane angles, near side, legs-above-hands, shoulder-over-hand, headInFrame
├── ViewReading.kt    # HeadOn / Angled readings; kneesOffFloor, postureHeld
├── Placement.kt      # CameraView classification (span / torso ratio)
├── Stakes.kt         # DAILY / STAKED: allowed views, camera fix, STAKED_RULE
├── Preflight.kt      # camera checks: light, tilt, whole body incl. head, placement
├── PositionGuide.kt  # where to move, per allowed views
├── Coach.kt          # one ≤3-word correction at a time
├── ReadyGate.kt      # ready position held (knees judged when staked)
├── RepCounter.kt     # RepRules, per-rep verdicts, hints
└── Trace.kt          # trace v2: header, row, parse, label

kotlin/repcounter/src/test/kotlin/holdout/repcounter/
├── RecordedTracesTest.kt   # replays fixtures/rep-traces within ±1
└── … synthetic rule tests (FrontView, Stakes, Coach, Preflight, ReadyGate, …)

kotlin/app/src/main/kotlin/holdout/app/
├── sitting/SittingViewModel.kt  # steps, coaching, head watch, trace recording and labelling
├── sitting/SittingScreen.kt     # camera, skeleton, +1, head warning, clean screen
├── sitting/PlacementGuide.kt    # SetupDialog: Phone / Body / Start
├── sitting/SittingCues.kt       # rep tone, banked chime, speech
├── sitting/TraceRecorder.kt     # writes the trace, debug only
├── sitting/TraceLabelDialog.kt  # "What did you just do?"
├── home/HomeScreen.kt           # debug switches: staked rules, clean screen, reset today
└── storage/Store.kt             # set-up guide preference, clearDay

fixtures/rep-traces/             # 17 labelled phone traces + README
scripts/pull-traces.sh           # adb pull of the debug build's traces
```

**Structure Decision**: The rule stays in the plain-JVM `repcounter` module so every decision is testable from recorded numbers without a phone (001 research R12); the app module only adapts MediaPipe, draws, speaks and records.

## Complexity Tracking

None.
