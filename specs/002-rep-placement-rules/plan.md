# Implementation Plan: Rep Placement and Form Rules

**Branch**: `002-rep-placement-rules` | **Date**: 2026-09-26 (as built); refreshed 2026-10-08 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/002-rep-placement-rules/spec.md`

**Note**: This plan is written after the fact. The recognition work was built and tuned against the phone between 23 and 26 September, then kept being tuned against labelled recordings into October; the plan records what exists, why, and what is left, so that `specs/001-holdout-hackathon` can build on it. **Refreshed 2026-10-08** for spec Session 2026-10-08: every sitting is counted at 45°; the Daily 25 is held to the Easy set-up and checks; head-on is retired (FR-006); a rep on the back or standing is refused (FR-008a).

## Summary

Make the push-up counter work from where a Holdout is counted — the phone on the floor ahead of the shoulder, turned to about 45° — and strict enough to be fair. The counter reads the **picture** (MediaPipe image landmarks) rather than the 3D world estimate, measured on the side of the body nearer the lens. Each rep is judged on **medians over the rep** and over its **push phase** (from the bottom until the elbow opens past 100°), and on the **way down** for the knee. **One counter, three stakes, checks only ever switched off, never retuned**: a Hard Holdout (form 0, 21-day) judges everything, knees off the floor included; an Easy Holdout (form 1, 7-day; H35) everything but knees off the floor, from no more frontal than about 40°; the free Daily 25 is an Easy Holdout with nothing staked (since 2026-10-08), so it rehearses the staked set-up every day. In every sitting a rep must be face down. Head-on, which could not see knees, a sag, a cobra or hips on the floor, is no longer read at all.

The evidence is the phone's own: debug builds record a landmark trace per sitting — and, for consenting people, the video beside it — the reviewer labels each rep from the video, and two harnesses hold the counter to those labels: a **test bench** that scores every labelled rep against a gate, and a **ratchet** that fails when any labelled rep does worse than its baseline.

## Technical Context

**Language/Version**: Kotlin 2.4.10, Java 17 bytecode (as `specs/001-holdout-hackathon/plan.md`). Node 22 for the labelling tool.

**Primary Dependencies**: MediaPipe Tasks Vision Pose Landmarker (full model, GPU, live stream), CameraX (preview, analysis and — debug only — `VideoCapture` in one 3:4 `UseCaseGroup`), Jetpack Compose, MMKV, Android `TextToSpeech` and `ToneGenerator`. No new dependency.

**Storage**: MMKV for the set-up guide preference (`setup_hidden:<wallet>:<stakes>`). Debug builds only: trace files and videos in the app's private `files/traces/`, pulled over USB into git-ignored `phone_traces/`; labels files beside them; promoted sittings committed to `fixtures/rep-traces/` as trace (gzip) and labels, never video.

**Testing**: JUnit 5 in `kotlin/repcounter` — synthetic geometry tests per rule; `RecordedTracesTest` replaying every committed trace against its person-counted `expectedReps` (±1); `RepRatchetTest` holding every reviewer-confirmed labelled rep to `fixtures/rep-traces/baseline.csv` (278 rows on 2026-10-08: 258 labelled reps and 20 counts awaiting labels). `./gradlew :repcounter:testBench` scores the labelled reps by label, person and sitting against the gate (honest ≥ 98 % counted, each cheat ≤ 2 %); `:repcounter:todayVerdicts` writes today's verdict beside every sitting with video for the labelling page. 145 tests in `repcounter` on 2026-10-08.

**Target Platform**: Android 8.0+; tuned on a Solana Seeker.

**Project Type**: Mobile app (the `repcounter` JVM module plus the `app` module's sitting screens) and a local labelling tool (`scripts/label-reps.mjs` + `.html`).

**Performance Goals**: Pose inference ≥ 15 FPS (unchanged from 001; 22–25 FPS measured with debug video on); a counted rep's tone within 300 ms of the top.

**Constraints**: Camera frames never leave the device (constitution X) — traces hold landmark numbers only, and debug video stays on the phone until pulled over USB, of people who agreed in writing. No microphone permission. Every threshold is stated with the measurements behind it (constitution XI).

**Scale/Scope**: One exercise (push-ups), three stakes, one accepted placement (45°, with a band per stakes). 74 committed traces; 27 labelled sittings from at least three people; the 8 head-on traces (`front-*`) kept as evidence only.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | How |
|---|---|---|
| V. Parameters live on the cohort | Pass | The cohort's form picks the stakes (`Stakes.ofForm`: 0 Hard, 1 Easy); reps per day and exercise come from the cohort (001 T065). |
| VII. Tests precede logic | Pass | Every threshold change landed with a synthetic test or a labelled phone trace that failed first; since 4 Oct the ratchet makes a regression on any confirmed labelled rep fail the build. |
| IX. Every state has a screen | Pass | Refused placement (with which way to turn), knees down, legs out of the picture, cobra, too shallow and an unreadable knee each have a shown and spoken hint. A rep on the back or standing is refused in silence on purpose: there is nothing to coach. |
| X. Camera frames never leave the device | Pass | Traces are landmark numbers; debug video is the v1.5.0 exception — debug builds only, no audio, on the device until pulled over USB, consenting people only, and `checkReleaseHasNoRecorder` fails a release build that contains the recorder. |
| XI. Weaknesses documented | Pass | README table rendered from `kotlin/app/src/main/assets/weaknesses.json`: one-person tuning, the coarse 45° body line, cobras and hips resting on the floor not always caught, knee push-ups counting in the Daily 25 and Easy by design. The head-on rows left with the placement. |
| XIII. Honest language | Pass | The Hard and Easy rule sentences (`Stakes.STAKED_RULE`, `EASY_RULE`) are plain and shown before staking; the tour states one placement for every sitting. |

Post-design re-check (2026-10-08): no change. Dropping head-on removes a placement whose limits had to be excused; it adds no new exception.

## Project Structure

### Documentation (this feature)

```text
specs/002-rep-placement-rules/
├── plan.md              # This file
├── research.md          # Decisions and the measurements behind them (D1–D12)
├── data-model.md        # Stakes, placement, readings, rules, traces, labels, baseline
├── quickstart.md        # How to check it on a phone, in tests and on the bench
├── contracts/
│   ├── trace-format.md  # The trace file, v2
│   └── words.md         # Everything the member is shown or told
└── tasks.md             # /speckit-tasks
```

### Source Code (repository root)

```text
kotlin/repcounter/src/main/kotlin/holdout/repcounter/
├── Pose.kt           # Landmark, PoseFrame, ImagePose, PoseIndex
├── Angles.kt         # image-plane angles, near side, legs/hip/shoulder over the hand, knee over the hip, ankle under the hand, knee readable, edges, head in frame
├── ViewReading.kt    # Angled reading (none head-on); kneesOffFloor, postureHeld
├── Placement.kt      # CameraView classification (span / torso ratio)
├── Stakes.kt         # DAILY / STAKED / EASY: one placement, judgesKnees, rule sentences, ofForm
├── Preflight.kt      # camera checks: light, whole body (head, near knee and foot clear of the edges), placement band per stakes, which way to turn
├── PositionGuide.kt  # where to move
├── Coach.kt          # one ≤3-word correction at a time
├── ReadyGate.kt      # ready position held; what it still lacks (HoldBlocker)
├── CountdownGuard.kt # the countdown keeps checking what started it
├── RepCounter.kt     # RepRules, per-rep verdicts, hints, the held plank, the toes stance
└── Trace.kt          # trace v2: header, row, parse, label

kotlin/repcounter/src/test/kotlin/holdout/repcounter/
├── RecordedTracesTest.kt   # replays fixtures/rep-traces within ±1, and named sittings rep by rep
├── RepRatchetTest.kt       # no confirmed labelled rep does worse than baseline.csv
├── TestBench.kt            # benchSitting: labels ↔ today's reps; the gate; Trace.Recording.startsToday
├── Ratchet.kt              # today's state of every labelled rep; updateBaseline
├── TodayVerdicts.kt        # today's verdict beside every sitting with video
├── ReplayReport.kt         # placement ratios and every cycle's readout (replayTraces)
└── … synthetic rule tests (Stakes, Preflight, ReadyGate, Coach, …)

kotlin/app/src/main/kotlin/holdout/app/
├── sitting/SittingViewModel.kt  # steps, countdown guard (Hard: a plank to calibrate on), coaching, trace and video, labelling
├── sitting/SittingScreen.kt     # camera, skeleton, +1, clean screen
├── sitting/PlacementGuide.kt    # SetupDialog: Phone / Body / Start, the 45° band per stakes
├── sitting/SittingCues.kt       # rep tone, banked chime, speech
├── sitting/TraceRecorder.kt     # writes the trace, debug only
├── sitting/TraceLabelDialog.kt  # "What did you just do?"
├── streak/ToesMeter.kt          # how many of the Daily 25's reps Hard would have counted
└── assets/weaknesses.json       # the known weaknesses, rendered into README.md

scripts/
├── pull-traces.sh      # adb pull of traces and videos; videos deleted from the phone once copied
├── label-reps.mjs      # local server: queue, labels, --flags, --reply, --promote, --prune
└── label-reps.html     # the labelling page: each rep as a loop, today's verdict beside the recorded one

fixtures/rep-traces/    # committed traces (+ labels), baseline.csv, README
```

**Structure Decision**: The rule stays in the plain-JVM `repcounter` module so every decision is testable from recorded numbers without a phone (001 research R12); the app module only adapts MediaPipe, draws, speaks and records; the labelling tool is plain Node with no dependencies, run on the developer's machine against git-ignored recordings.

## Complexity Tracking

None. The Daily 25 dropping head-on removed a measurement path (`ViewReading.HeadOn`, its depth and torso rules, `FrontViewTest`) rather than adding one.
