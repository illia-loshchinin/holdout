---

description: "Task list for 002 rep placement and form rules — as built, with what remains"
---

# Tasks: Rep Placement and Form Rules

**Input**: Design documents from `specs/002-rep-placement-rules/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Required — constitution VII, and FR-023 makes labelled phone traces the test suite. Every rule below landed with a synthetic test or a labelled trace that failed first.

**As built**: Tasks marked `[X]` were implemented and verified on a Seeker between 23 and 26 September 2026 (commits `105faa0` … `439a826`). Open tasks are what remains. Tasks owned by `specs/001-holdout-hackathon` are referenced, not duplicated.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an open task)
- **[Story]**: US1 Daily 25 placements · US2 staked fairness · US3 setting up · US4 per-rep feedback

---

## Phase 1: Setup — tuning evidence (FR-023, FR-025)

**Purpose**: every later threshold is set from the phone's own recordings.

- [X] T001 Define trace format v2 — header facts, world and image landmarks incl. `NOSE`, readout and event per row, `parse`, `label` — in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Trace.kt` ([contracts/trace-format.md](contracts/trace-format.md))
- [X] T002 Record one trace per sitting in debug builds, header flushed at once, rows every 30 frames or on an event, closed on Stop / completion / leaving and never reopened, in `kotlin/app/src/main/kotlin/holdout/app/sitting/TraceRecorder.kt`
- [X] T003 [P] Pull traces over USB with `scripts/pull-traces.sh` into git-ignored `phone_traces/`
- [X] T004 Replay every labelled trace in `fixtures/rep-traces/` within ±1 rep in `kotlin/repcounter/src/test/kotlin/holdout/repcounter/RecordedTracesTest.kt`
- [X] T005 [P] Ask "What did you just do?" after Stop and after the day completes — full, half, knees, cobra, other, note — and write it into the trace (`expectedReps` = full, plus knees in the Daily 25) in `kotlin/app/src/main/kotlin/holdout/app/sitting/TraceLabelDialog.kt` and `SittingViewModel.labelTrace`
- [X] T006 [P] Debug switches on home — staked rules without a stake (scope `test-staked`), clean screen, reset today — in `kotlin/app/src/main/kotlin/holdout/app/home/HomeScreen.kt`, `HoldoutApp.kt`, `storage/Store.kt` (`clearDay`)

---

## Phase 2: Foundational — reading the picture (research D1–D4)

- [X] T007 Image-plane angles scaled to pixels, near side, legs-above-hands, shoulder-over-hand, shoulder height, head in frame, in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Angles.kt`
- [X] T008 `ViewReading.HeadOn` / `ViewReading.Angled` from the picture, world landmarks only as fallback, in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/ViewReading.kt`
- [X] T009 Classify placement by shoulder span ÷ torso length — side ≤ 0.5, head-on ≥ 1.55 — in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Placement.kt` and `Preflight.kt`
- [X] T010 `Stakes` with accepted views (Daily: FRONT, OBLIQUE; staked: OBLIQUE), `cameraFix`, `STAKED_RULE` in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Stakes.kt`
- [X] T011 Per-rep medians and push-phase window (from the bottom while elbow ≥ 100°); rep clock starts below 140°, in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/RepCounter.kt`

**Checkpoint**: the counter can be built for a stake and a locked view.

---

## Phase 3: US1 — Daily 25 counts from where people put the phone (P1) 🎯

**Independent test**: `./gradlew :repcounter:test` — `front-honest-*`, `angled-honest-*` count within one; `front-half-*` count 0; `front-knees-01` counts 5 (Daily).

- [X] T012 [US1] Head-on: elbow from the picture, torso down (elevation ≤ 55°), depth by shoulders reaching ≤ 0.25 of top height (FR-006) in `RepCounter.kt`
- [X] T013 [US1] 45°: top at 160°, depth by shoulder over hand ≤ 0.32, body-line rep median ≥ 140° (FR-008) in `RepCounter.kt`
- [X] T014 [US1] Refuse side-on with "Put the phone in front of you, or at 45°" (FR-003, FR-004) in `Preflight.kt`
- [X] T015 [US1] Knee push-ups count in the Daily 25 in both placements (FR-007) — `judgeKnees = stakes == STAKED` in `RepCounter.kt`, fixture `front-knees-01`
- [X] T016 [P] [US1] Cobra refused in both stakes by push-phase body line ≥ 145° (FR-008, SC-002b), fixtures `angled-knees-cobra-01`, `angled-right-cobra-01`
- [ ] T017 [US1] Record a head-on cobra set (5 reps, Daily 25), label it, commit as `fixtures/rep-traces/front-cobra-01.trace.csv`; if any rep counts, add a head-on cobra rule in `RepCounter.kt` with the failing fixture first
- [ ] T018 [US1] State in the tour's counting card that the Daily 25 accepts the phone in front or at 45° and welcomes knee push-ups (FR-013) — owned by `specs/001-holdout-hackathon` T042, `kotlin/app/src/main/kotlin/holdout/app/tour/TourScreen.kt`

---

## Phase 4: US2 — money is only staked on push-ups the camera can hold everyone to (P1)

**Independent test**: staked fixtures `angled-right-knees-feetdown-01`, `angled-right-knees-feetup-01`, `angled-half-01` count 0; `angled-*-honest-*` count 10.

- [X] T019 [US2] Staked accepts 45° only; a staked `RepCounter` cannot be built for another view; camera check says "Turn the phone to 45°" (FR-002, FR-004) in `Stakes.kt`, `Preflight.kt`, `RepCounter.kt`
- [X] T020 [US2] Knees down on the push: legs-above-hands < 0.41 or knee median < 90° → "Knees off the floor" (FR-010) in `RepCounter.kt` and `ViewReading.kneesOffFloor`
- [X] T021 [US2] Ready position held from the toes in staked sittings; the hint on every attempt (FR-011) in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/ReadyGate.kt`
- [X] T022 [US2] Staked rule sentence shown in the set-up guide in `kotlin/app/src/main/kotlin/holdout/app/sitting/PlacementGuide.kt`
- [ ] T023 [US2] Show `Stakes.STAKED_RULE` verbatim on the stake sheet before staking (FR-012, FR-014, SC-006) — owned by `specs/001-holdout-hackathon` T059, `kotlin/app/src/main/kotlin/holdout/app/stake/StakeSheet.kt`
- [ ] T024 [US2] Build staked sittings from the cohort (exercise, reps) with `Stakes.STAKED` — owned by `specs/001-holdout-hackathon` T065, `kotlin/app/src/main/kotlin/holdout/app/sitting/CohortDayController.kt`

---

## Phase 5: US3 — setting up without reading small print from the floor (P2)

**Independent test**: quickstart §2 rows US3 and FR-024.

- [X] T025 [US3] Set-up guide dialog, tabs Phone / Body / Start, plan diagrams of the accepted placements only, "Don't show this again" per wallet and stakes, reopenable (FR-015–FR-017) in `kotlin/app/src/main/kotlin/holdout/app/sitting/PlacementGuide.kt` and `Store.isSetupGuideHidden`
- [X] T026 [US3] One ≤3-word correction at a time, in the FR-018 order, shown large, spoken after 0.7 s, repeated every 5 s, "Hold" once (FR-018, FR-019) in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Coach.kt` and `SittingViewModel`
- [X] T027 [US3] Head required by the camera check; "Head out of view · move back" shown and "Move back" spoken while counting (FR-024) in `Preflight.kt`, `Angles.headInFrame`, `SittingViewModel.watchHead`, `SittingScreen.kt`
- [ ] T028 [US3] Measure SC-005: five people who have never used the app place the phone from the set-up guide alone; record first-try successes in `specs/002-rep-placement-rules/checklists/sc-measurements.md` (needs 001 T095 beta testers)

---

## Phase 6: US4 — knowing a rep counted without looking (P3)

- [X] T029 [US4] 70 ms tone per counted rep, banked chime on every tenth, rising "+1" and count pulse (FR-021) in `kotlin/app/src/main/kotlin/holdout/app/sitting/SittingCues.kt` and `SittingScreen.CountingOverlay`

---

## Phase 7: Polish and cross-cutting

- [X] T030 [P] Known weaknesses for rep counting in `README.md` (one-person tuning, head-on knees, ±10° body line at 45°, occasional cobra)
- [X] T031 [P] Fixtures table with expected counts in `fixtures/rep-traces/README.md`
- [ ] T032 [P] Measure SC-003 from committed traces: share of top-of-rep frames classified `OBLIQUE` in every angled fixture, as a test in `kotlin/repcounter/src/test/kotlin/holdout/repcounter/PlacementTest.kt` (≥ 99%)
- [ ] T033 Collect labelled traces from at least three more people (beta testers, 001 T095) — one honest, one knee, one cobra set each at 45°, staked switch on — and commit them to `fixtures/rep-traces/`; re-tune thresholds only with a failing fixture first
- [ ] T034 [P] Keep the README weaknesses row for rep counting in sync with T033's results, `README.md`

---

## Phase 8: Deferred — "knee on the floor" image classifier (research D9)

**Starts only when T033 has traces from several people.** Until then the landmark rules (T020) stand.

- [ ] T035 Amend the constitution (principle X) for members sharing their own clean-screen recordings as training data — consent, where frames go, deletion — via `/speckit-constitution`, `.specify/memory/constitution.md`
- [ ] T036 Specify the classifier as its own feature via `/speckit-specify` — input crops, labels, where it runs (staked, push phase only), fallback to landmark rules, accuracy bar
- [ ] T037 Build the dataset tool: leg crops around near knee and ankle from clean-screen recordings, labelled per frame from the trace's movement label, in `scripts/classifier/make_dataset.py`
- [ ] T038 Train and export a small on-device image classifier (`.tflite`) in `scripts/classifier/train.py`; report precision and recall per person held out
- [ ] T039 Run the classifier on the push phase of staked sittings in `kotlin/app/src/main/kotlin/holdout/app/vision/KneeClassifier.kt`, feeding a knee verdict into `RepCounter`; frames stay on the device

---

## Dependencies

- Phase 1 → Phase 2 → US1, US2 (both P1, independent of each other) → US3 → US4.
- T018, T023, T024 wait on `specs/001-holdout-hackathon` T042, T059, T065.
- T028 and T033 wait on 001 T095 (beta testers).
- Phase 8 waits on T033 and T035.

## Parallel execution examples

- T017, T032 and T033 are independent recording/measurement jobs.
- In Phase 8, T037 and the T039 app scaffold can proceed side by side once T036 fixes the crop format.

## Implementation strategy

The MVP — US1 and US2 on the counter — is built and on the phone. What remains is wiring the staked rule into the 001 staking flow (T023, T024, T018), then widening the evidence beyond one person (T033) before any classifier work (Phase 8).
