---

description: "Task list for 002 rep placement and form rules — as built, with what remains"
---

# Tasks: Rep Placement and Form Rules

**Input**: Design documents from `specs/002-rep-placement-rules/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Required — constitution VII, and FR-023 makes labelled phone traces the test suite. Every rule below landed with a synthetic test or a labelled trace that failed first.

**As built**: Tasks marked `[X]` were implemented and verified on a Seeker between 23 and 26 September 2026 (commits `105faa0` … `439a826`). Open tasks are what remains. Tasks owned by `specs/001-holdout-hackathon` are referenced, not duplicated.

**Refreshed 2026-10-08** for spec Session 2026-10-08: the Daily 25 is counted at 45° only, in the Easy band and with Easy's checks; head-on is retired (FR-006); a rep on the back or standing is refused (FR-008a, SC-002c). The head-on tasks are kept with a **Superseded** or **Dropped** note rather than deleted, so their history stays; the work of 7–8 October is T040–T045 (commits `19f773a`, `cdc4c1a`, `8e8b346`, `63f7060`, `78b54e5`), and what it leaves open is T046–T048.

**Refreshed 2026-10-12** for spec Session 2026-10-12 (FR-016's tick kept however the guide closes; FR-018/FR-020a's tip sheet, with the 30 seconds counting only time when the pre-flight page is visible): the tip sheet is built as T051 (owned by `specs/001-holdout-hackathon` T253, commit `569c627`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependency on an open task)
- **[Story]**: US1 Daily 25 at 45° · US2 staked fairness · US3 setting up · US4 per-rep feedback

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

- [X] T007 Image-plane angles scaled to pixels, near side, legs-above-hands, shoulder-over-hand, shoulder height, head in frame, in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Angles.kt` — *the head-on helpers (shoulder height, both-arms elbow, torso elevation) removed 2026-10-08 in T042*
- [X] T008 `ViewReading.HeadOn` / `ViewReading.Angled` from the picture, world landmarks only as fallback, in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/ViewReading.kt` — *`HeadOn` removed 2026-10-08 in T042: a head-on frame is no reading*
- [X] T009 Classify placement by shoulder span ÷ torso length — side ≤ 0.5, head-on ≥ 1.55 — in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Placement.kt` and `Preflight.kt` — *since then side ≤ 0.25 (T034c); the front line per stakes is 1.1 for Hard and 0.90 for Easy and the Daily 25 (T041)*
- [X] T010 `Stakes` with accepted views (Daily: FRONT, OBLIQUE; staked: OBLIQUE), `cameraFix`, `STAKED_RULE` in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Stakes.kt` — **Superseded 2026-10-08 (T041)**: every stakes accepts OBLIQUE only, one camera fix
- [X] T011 Per-rep medians and push-phase window (from the bottom while elbow ≥ 100°); rep clock starts below 140°, in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/RepCounter.kt`

**Checkpoint**: the counter can be built for a stake and a locked view.

---

## Phase 3: US1 — Daily 25 counts at 45°, the placement a Holdout is counted at (P1) 🎯

**Independent test** *(revised 2026-10-08)*: `./gradlew :repcounter:test` — `StakesTest` "the Daily 25 starts at 45 degrees only…", "no counter is measured head-on…" and "the Daily 25 counts nothing done on the back or standing up…"; `PreflightTest` "the Daily 25 starts at 45 degrees only…"; `RecordedTracesTest` and `RepRatchetTest` over `fixtures/rep-traces/`. The 7 Oct Daily 25 sittings replay 22/22 and 7/7 with no crunch or stand-up counted. The `front-*` traces are evidence, not tests.

- [X] T012 [US1] Head-on: elbow from the picture, torso down (elevation ≤ 55°), depth by shoulders reaching ≤ 0.25 of top height (FR-006) in `RepCounter.kt` — **Superseded 2026-10-08**: FR-006 retired; the head-on rules removed in T042
- [X] T013 [US1] 45°: top at 160°, depth by shoulder over hand ≤ 0.32, body-line rep median ≥ 140° (FR-008) in `RepCounter.kt`
- [X] T014 [US1] Refuse side-on with "Put the phone in front of you, or at 45°" (FR-003, FR-004) in `Preflight.kt` — **Superseded 2026-10-08 (T041)**: every sitting says "Turn the phone to 45°", with which way to turn at the band's edges
- [X] T015 [US1] Knee push-ups count in the Daily 25 in both placements (FR-007) — `judgeKnees = stakes == STAKED` in `RepCounter.kt`, fixture `front-knees-01` — *at 45° only since 2026-10-08, and in Easy too (`Stakes.judgesKnees`)*
- [X] T016 [P] [US1] Cobra refused in both stakes by push-phase body line ≥ 145° (FR-008, SC-002b), fixtures `angled-knees-cobra-01`, `angled-right-cobra-01`
- [X] T017 [US1] Record a head-on cobra set (5 reps, Daily 25), label it, commit as `fixtures/rep-traces/front-cobra-01.trace.csv`; if any rep counts, add a head-on cobra rule in `RepCounter.kt` with the failing fixture first — **Dropped 2026-10-08, not done**: no sitting is counted head-on (FR-006 retired)
- [X] T018 [US1] State in the tour's counting card that the Daily 25 accepts the phone in front or at 45° and welcomes knee push-ups (FR-013) — owned by `specs/001-holdout-hackathon` T042, `kotlin/app/src/main/kotlin/holdout/app/tour/TourScreen.kt` **Done 2026-09-26 with 001's T042.** *Wording revised 2026-10-08 in T041: the phone at 45°, as in a Holdout.*
- [X] T040 [US1] *(Added 2026-10-08, FR-008a, SC-002c; commit `19f773a`.)* Refuse, in silence, a rep done on the back or standing: `Angles.kneeAboveHipInImage` and `Angles.ankleBelowHandInImage` in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Angles.kt`; `kneeAboveHip` / `ankleBelowHand` on `ViewReading.Angled` in `ViewReading.kt`; `RepRules.maxKneeAboveHip` 0.3 and `maxAnkleBelowHand` 0.6, with `RepCounter.notFaceDown` judged first *(since `6894c3c`, 8 Oct; at first after every other body check)*, in `RepCounter.kt`; test "the Daily 25 counts nothing done on the back or standing up - in silence" in `kotlin/repcounter/src/test/kotlin/holdout/repcounter/StakesTest.kt`. Measured: every labelled push-up knee −0.59..0.02, ankle ≤ 0.04; crunches knee 0.51–0.81; standing ankle 1.87–1.94. The 7 Oct Daily 25 sittings go 25 → 22 and 25 → 7, as on video
- [X] T041 [US1] *(Added 2026-10-08, FR-001, FR-004, FR-007; commit `cdc4c1a`.)* Hold the Daily 25 to the Easy set-up at 45°: `Stakes.allowedViews` OBLIQUE and one `cameraFix` for every stakes in `Stakes.kt`; the Daily 25 takes Easy's front line (0.90), the which-way-to-turn fixes and the near knee and foot clear of the edges to start, in `Preflight.kt`; an unreadable knee holds the Daily 25 in `ReadyGate.kt`; legs watched in every sitting at 45° (`watchLegs`) in `RepCounter.kt`; the guide, tour and view-model copy in `kotlin/app/src/main/kotlin/holdout/app/sitting/PlacementGuide.kt`, `tour/TourScreen.kt`, `sitting/SittingViewModel.kt`; the head-on and cobra rows in `kotlin/app/src/main/assets/weaknesses.json` and the README table rendered from it. Tests moved with the rule in `PreflightTest.kt` and `StakesTest.kt`
- [X] T042 [US1] *(Added 2026-10-08, FR-006 retired; commit `8e8b346`.)* Remove the head-on reading: `ViewReading.HeadOn`, the torso and head-on depth rules and their `RepRules`, `Angles.torsoElevation`, `shoulderHeightInImage` and the both-arms elbow; `RepCounter` cannot be built head-on; the guide's "In front" card and the head-on plank figure in `PlacementGuide.kt` and `SittingScreen.kt`; `FrontViewTest.kt` and the head-on frame builders in `Traces.kt` deleted. The `front-*` fixtures stay as evidence: `Trace.Recording.startsToday` in `TestBench.kt` leaves out a sitting today's app would not start (head-on, or in front of its stakes' band); baseline 285 → 260 labelled reps (the 25 of `front-honest-05` and `front-honest-knees-04`), 0 better, 0 worse

---

## Phase 4: US2 — money is only staked on push-ups the camera can hold everyone to (P1)

**Independent test**: staked fixtures `angled-right-knees-feetdown-01`, `angled-right-knees-feetup-01`, `angled-half-01` count 0; `angled-*-honest-*` count 10.

- [X] T019 [US2] Staked accepts 45° only; a staked `RepCounter` cannot be built for another view; camera check says "Turn the phone to 45°" (FR-002, FR-004) in `Stakes.kt`, `Preflight.kt`, `RepCounter.kt`
- [X] T020 [US2] Knees down on the push: legs-above-hands < 0.41 or knee median < 90° → "Knees off the floor" (FR-010) in `RepCounter.kt` and `ViewReading.kneesOffFloor`
- [X] T021 [US2] Ready position held from the toes in staked sittings; the hint on every attempt (FR-011) in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/ReadyGate.kt`
- [X] T022 [US2] Staked rule sentence shown in the set-up guide in `kotlin/app/src/main/kotlin/holdout/app/sitting/PlacementGuide.kt`
- [X] T023 [US2] Show `Stakes.STAKED_RULE` verbatim on the stake sheet before staking (FR-012, FR-014, SC-006) — owned by `specs/001-holdout-hackathon` T059, `kotlin/app/src/main/kotlin/holdout/app/stake/StakeSheet.kt` **Done 2026-09-26 with 001's T059.**
- [X] T024 [US2] Build staked sittings from the cohort (exercise, reps) with `Stakes.STAKED` — owned by `specs/001-holdout-hackathon` T065, `kotlin/app/src/main/kotlin/holdout/app/sitting/CohortDayController.kt` **Done 2026-09-26 with 001's T065.**
- [X] T043 [US2] *(Added 2026-10-08, FR-010; PR #14 review; commit `63f7060`.)* A Hard countdown stops on `HoldBlocker.KNEES_UNREADABLE` and `BODY` as on `KNEES` (`PLANK_BLOCKERS`), and counting does not start without a plank read through the countdown (`RepCounter.plankCalibrated`), in `kotlin/app/src/main/kotlin/holdout/app/sitting/SittingViewModel.kt` and `RepCounter.kt` — else the legs-against-own-plank check passes every rep. All 31 Hard sittings on the 4:3 picture calibrate one

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

- [X] T030 [P] Known weaknesses for rep counting in `README.md` (one-person tuning, head-on knees, ±10° body line at 45°, occasional cobra) — *the head-on row became "knee push-ups count in the Daily 25 and Easy, by design" on 2026-10-08 (T041)*
- [X] T031 [P] Fixtures table with expected counts in `fixtures/rep-traces/README.md`
- [ ] T032 [P] Measure SC-003 from committed traces: share of top-of-rep frames classified `OBLIQUE` in every angled fixture, as a test in `kotlin/repcounter/src/test/kotlin/holdout/repcounter/PlacementTest.kt` (≥ 99%)
- [ ] T033 Collect labelled traces from at least three more people (beta testers, 001 T095) — one honest, one knee, one cobra set each at 45°, staked switch on — and commit them to `fixtures/rep-traces/`; re-tune thresholds only with a failing fixture first
- [ ] T034 Keep the known-weaknesses rows for rep counting in sync with T033's results: edit them in `docs/weaknesses.json` and re-render the README table with `node scripts/render-weaknesses.mjs`, never `README.md` by hand (`--check` fails on it) — *amended 2026-10-10: was `README.md`; the table is rendered from `docs/weaknesses.json` since spec 004*
- [X] T034a *(Added 2026-09-29, FR-010, FR-024 revised.)* Close the knees-out-of-camera loophole and move the head to entry only: `Angles.kneeClearOfEdge`; `RepRules.legEdgeMargin` (13 %) and `maxLegsUnseenShare` (25 %) in `RepCounter`, refusing with `LEGS_IN_VIEW` ("Keep your knees in the picture", said at once); the whole-body pre-flight check requires the head clearly in frame and, staked, the near knee clear of the edges; the counting-time head warning removed. Calibrated by replaying all 22 staked phone traces: the knees-out set goes 5 → 0, every honest set unchanged; the set is committed as `fixtures/rep-traces/angled-knees-out-of-frame-01.trace.csv` with its own test
- [X] T034b *(Added 2026-09-29, product owner.)* One picture for all: preview, analysis and the debug recorder bound as one `UseCaseGroup` with a 3:4 `ViewPort` at the sensor's full width (`SittingScreen`, `SittingVideoFactory`), instead of 16:9 analysis beside a 4:3 recording — the video had shown a whole body while the counter lost the head at its edge. The placement ratio and the guide outline, measured in 9:16, read x through `Aspect` (exact: same body, same placement); edge checks use the real, wider frame. Test sittings at 45° and head-on on the Seeker still to confirm the placement pill and the analysis FPS
- [ ] T034c *(Added 2026-09-29.)* Calibrate the placement check on the 4:3 picture and widen the staked band to 45–55°: the label form records the phone's angle and side (`# placement=… phoneSide=…`), `./gradlew :repcounter:replayTraces` reports the placement ratio and count accuracy per labelled angle, and the recording protocol is in `fixtures/rep-traces/README.md`. The knee guidance is built — the whole-body check tells a hidden knee ("Turn slightly left/right", away from the phone) from one at the edge ("Further"). **First two sets in (1 Oct, 45° and 50°, phone on the right):** both counted 0 of 10 under the 160° top — fixed by setting the top at 45° per sitting from the arm held through the countdown (FR-008 revised), both now 10 of 10 with the knee and cobra reps refused, committed as `angled45-right-mixed-04` and `angled50-right-mixed-05`; `sideRatio` lowered 0.5 → 0.25 provisionally (45° read side-on in 12 % of frames, 50° in 35 %); analysis pinned to 960×720 and the recording raised to HD after the 4:3 switch had dropped analysis to 640×480. **Confirmed live the same day** on the new build: a third set at 50° counted 10 of 10 as it was done and refused all 15 cheats (`angled50-right-mixed-06`); 23 FPS while counting at 720×960 with HD video; no side-on frames at `sideRatio` 0.25. **Settled 2 Oct from five more sittings (phone on the right): 55°, 40°, 60°, an unmeasured spot near 40° and a 10-second side-on hold.** `sideRatio` stays 0.25: 40°, 55° and 60° read side-on in 0 %, 0 % and 2 % of counting frames, and the side-on hold read side-on in every frame and never started (`side-right-hold-01`). `frontRatio` stays 1.55: 40° reached 1.02 at the 95th percentile. 55° counted 11 of 11 and 40° 5 of 5 live, every knee, cobra and half rep refused (`angled55-right-mixed-07`, `angled40-right-mixed-08`). One fix: the 45° depth check refused 3 honest reps of 12 from the unmeasured spot (shoulder 0.33–0.34 over the hand) — a rep in the 0.32–0.36 band now counts when the elbow closed to 66° or less (the set was 12 reps, confirmed on video; `angled-right-honest-10`). And one loophole closed: at 60° a rep let down from the knees and pushed up on the toes counted — the knee angle is now judged on the descent too (`angled60-right-knees-down-up-09`). `replayTraces -Pdetail` prints every cycle with what it was judged on. **Third batch, 2 Oct (45° left, head-on Daily 25, two sets near 30°):** head-on depth line 0.25 → 0.285 (a Daily 25 set of 15 had counted 8, now 14); a knee under 135° now needs the legs at 0.48 (five knee reps with the feet raised had counted near 30°); fixtures `angled45-left-mixed-11`, `front-honest-knees-04`, `angled30-right-honest-12`, `angled30-right-knees-feetup-13`. **Fourth batch, 2 Oct (45° left again, head-on, 30° knees, and a second person in loose trousers):** the bent-knee rule refused all ten knee reps live; head-on counted 9 of 10. Three things fixed: (1) a rep pushed up from lying on the floor, and a second person's near-straight cobras, counted — the hips must now rise with the shoulders half-way up the push (`minHipRise` 0.90, FR-008); (2) her plank never started the hold and nothing said why — a knee the model puts on the ankle is treated as unseen, and with the pills green the coach now says what the position lacks (FR-018a); (3) two honest reps on the left were refused with the knee well inside the picture — it was the foot at the edge, which the set-up now asks to be 6 % inside. Fixtures `angled45-left-floor-start-15`, `angled45-right-loose-trousers-cobra-16`, `angled45-right-loose-trousers-hold-17`, `front-honest-05`, `angled30-right-knees-14`. **And the band closed in front** (product owner, 2 Oct): a staked sitting no longer starts from in front of about 35° (placement ratio 1.1, the line the hip-rise check is judged under), so no accepted placement is left where the floor-start cheat counts; the camera pill and the coach say which way to turn. The staked fixtures recorded before that at 1.1 or more (`angled-honest-01/02`, `angled-mixed-02/03`, `angled-knees-cobra-01`, `angled-left-honest-03`, `angled-right-honest-01`, the `angled-right-knees-*` and `-cobra-01` sets, `angled30-right-honest-12`) stay as tests of the counter; the app would not start them today. **Still open: the phone on the left reads far nearer side-on than on the right at the same floor mark (held ratio 0.44 against 0.76, 18 % of counting frames under `sideRatio`, knee near the edge) — one set only; re-record with the phone a little further back before deciding anything**

- [X] T044 [P] *(Added 2026-10-08, FR-023; PR #14 review; commit `63f7060`.)* Bench and ratchet: a knee rep is benched as should-count wherever knees are not judged — the Daily 25 and Easy (`RepLabels.kind`); a rep counted where no label lies is reported by the bench, fails its gate and is held by the ratchet as `unlabelled-count` until labelled, in `kotlin/repcounter/src/test/kotlin/holdout/repcounter/TestBench.kt`, `Ratchet.kt`, `RepRatchetTest.kt`; `fixtures/rep-traces/baseline.csv` now 280 rows (260 labelled + 20 unlabelled counts)
- [X] T045 [P] *(Added 2026-10-08, FR-023; PR #14 review; commits `63f7060`, `78b54e5`.)* Labelling page and server: reps only today's replay counts are listed as "not recorded" (one replay count per recorded rep, as the bench pairs them); a replay that judged nothing is kept apart from one not run; a refresh that returns after another sitting opened is dropped; `--promote` and `--prune` take a finished sitting only when a reviewer confirmed every label, and prune only when the promoted labels are the working ones; deleting videos needs the page's header and origin — in `scripts/label-reps.html` and `scripts/label-reps.mjs`
- [ ] T046 Label the 26 reps the labelling page lists as "not recorded" (`node scripts/label-reps.mjs`, the queue's "not recorded: only today counts it"). Among them are the honest sets of ten in `20261001-173613-staked` and `20261001-173958-staked`; then `node scripts/label-reps.mjs --promote` refreshes `fixtures/rep-traces/angled45-right-mixed-04.labels.csv` and `angled50-right-mixed-05.labels.csv`, and `./gradlew :repcounter:updateBaseline` turns their 20 `unlabelled-count` rows in `fixtures/rep-traces/baseline.csv` into labelled reps
- [ ] T047 [P] Confirm, as the reviewer, the labels Claude set in `20261002-153437-staked` (promoted as `fixtures/rep-traces/angled45-left-floor-start-15.labels.csv`, two `by=claude` rows, both `unseen`), then `--promote` and `updateBaseline`
- [ ] T048 [P] Record a Daily 25 set at the front edge of the Easy band (about 40°, phone on either side) — 10 honest, 5 from the knees, 5 cobras — label it from video and commit it to `fixtures/rep-traces/`, to confirm the band holds for the Daily 25 now that it starts no further in front (FR-001, SC-002b)

---

## Phase 8: Deferred — "knee on the floor" image classifier (research D9)

**Starts only when T033 has traces from several people.** Until then the landmark rules (T020) stand.

- [X] T035 Amend the constitution (principle X) for members sharing their own clean-screen recordings as training data — consent, where frames go, deletion — via `/speckit-constitution`, `.specify/memory/constitution.md` **Done 2026-09-27 as constitution v1.5.0, scoped narrower than planned**: debug builds may record a sitting's camera — the team and consenting testers only, on the device until pulled over USB, then deleted. Members of the public sharing their own recordings stays a later decision.
- [X] T035a *(Added 2026-09-27, FR-026.)* Record a sitting's camera as video in debug builds — `SittingVideo` in `kotlin/app/src/main/kotlin/holdout/app/sitting/`, CameraX `VideoCapture` in `kotlin/app/src/debug/`, a null stub in `kotlin/app/src/release/`; the "record video" test switch on both homes; the file named after the trace with a `.video.json` of its start on the boot clock; the analysis FPS in the debug readout; `scripts/pull-traces.sh` pulls videos and deletes them from the phone once copied; `checkReleaseHasNoRecorder` wired into assembleRelease. **Built. Measured on the Seeker 2026-09-29: 22.0–24.6 FPS while counting with video on, across 7 sittings — above the 15 FPS bar; videos start 0.2–0.6 s before the first trace frame and match the trace's length within 0.5 s.**
- [ ] T036 Specify the classifier as its own feature via `/speckit-specify` — input crops, labels, where it runs (staked, push phase only), fallback to landmark rules, accuracy bar
- [ ] T037 Build the dataset tool: leg crops around near knee and ankle from clean-screen recordings, labelled per frame from the trace's movement label, in `scripts/classifier/make_dataset.py` — **map the coordinates for recordings before 30 Sep:** their trace landmarks are normalised to the 16:9 analysis frame while the video recorded the sensor's 4:3 width, so a trace x of 0..1 lands at about 0.125..0.875 of the video's width. From then on preview, analysis and video share one 4:3 crop (T034b) and line up as recorded
- [ ] T038 Train and export a small on-device image classifier (`.tflite`) in `scripts/classifier/train.py`; report precision and recall per person held out
- [ ] T039 Run the classifier on the push phase of staked sittings in `kotlin/app/src/main/kotlin/holdout/app/vision/KneeClassifier.kt`, feeding a knee verdict into `RepCounter`; frames stay on the device

---

## Phase 9: Convergence

- [X] T049 Give the near knee its own blocking pre-flight pill, Knees visible, between Whole body in frame and Camera view in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Preflight.kt` (`PreflightCheck.KNEES_VISIBLE`, `Preflight.kneesVisible()`), moving the near-knee checks and their three fixes out of Whole body in frame, red without a picture pose, and have `Coach.next` rank the knee turn fixes below a failing Whole body in frame pill per FR-024a (missing)
- [X] T050 Make the set-up guide in `kotlin/app/src/main/kotlin/holdout/app/sitting/PlacementGuide.kt` say "Two things decide whether a rep counts: your whole body in the picture, head to feet, and your knees clearly visible to the phone." under its title on every page, with the phone page's "From there it sees all of you, and the knee nearer to it." and the body page opening on the whole-body and knees lines, per FR-015 as amended (missing) *(Superseded 2026-10-12 by T052: the guide has no opening lines now.)*
- [X] T051 [US3] **Pre-flight tip sheet** (FR-020a, US-3 scenario 5; `contracts/words.md` "Tip sheet"; quickstart 001 24e) — owned and tracked by `specs/001-holdout-hackathon` T253, built in commit `569c627`: the trigger counter in `kotlin/app/src/main/kotlin/holdout/app/sitting/PreflightTipsOffer.kt` (30 seconds in total short of all green, counting only time when the pre-flight page is visible and not time under the set-up guide, though a flip during it counts; or the third green-to-red turn; whichever is first; kept until the sitting ends), the "Getting the checks green" sheet in `sitting/PreflightTipsSheet.kt`, wired in `sitting/PreflightScreen.kt`, `sitting/SittingViewModel.kt` and `sitting/SittingScreen.kt`. Tests: `PreflightTipsOfferTest` (incl. "time spent reading the set-up guide does not count"), `PreflightTipsSheetTest`, `PreflightTipsWiringTest` in `kotlin/app/src/test/kotlin/holdout/app/sitting/`

---

## Phase 10: Walk round 5 — names, Holdouts Now/Past, set-up guide, pre-flight, tour

*(Added 2026-10-12, by `/speckit-tasks` after the clarification session "2026-10-12" of spec.md (four answers from the design canvas and the Seeker walk: the set-up guide's three tabs and Start; the Rules tab and its cues card; the pre-flight page's "Place your phone" and bare outline, with no unfinished-reps pill; the tour's top-view panel; FR-013, FR-014, FR-015, FR-015a, FR-018, FR-019, FR-020b) and its planning edits (contracts/words.md, quickstart rows Guide, Place and Tour). The decisions are the product owner's. Numbered from T052. The names and the Holdouts tab of the same walk belong to spec 001 (its Phase 34). No counting logic changes; the sentences of FR-014 stay on the stake sheet and the pre-flight page.)*

**Goal:** the set-up guide is three tabs (Positioning, Rules, Start) with no intro paragraph, each fact said once, and its Rules tab teaches what the sitting says; the pre-flight page says "Place your phone" once, shows a bare dashed outline only once the phone stands and no time-left pill; card 3 of the tour draws the placement from above.
**Independent test:** quickstart rows Guide, Place and Tour.

- [X] T052 [P] [US3] **The set-up guide is "1. Positioning · 2. Rules · 3. Start", with no intro paragraph** (`kotlin/app/src/main/kotlin/holdout/app/sitting/PlacementGuide.kt` `SetupGuide`, `PositioningPage`, `SittingCopy.kt`; FR-015, FR-016). The two opening lines ("Two things decide whether a rep counts…", "From there it sees all of you…") are removed; Skip/Back and Next/Got it and "Don't show this again" stay. Tests first (`kotlin/app/src/test/kotlin/holdout/app/sitting/SetupGuideTest.kt`). Done 2026-10-12: `5cb8244` (delegate).
- [X] T053 [P] [US3] **The Rules tab lists the sitting's own rule lines and a "What you'll hear and see" card** (`kotlin/app/src/main/kotlin/holdout/app/sitting/PlacementGuide.kt` `RulesPage`, `sittingCues`, `CuesCard`; FR-015a, FR-019). The cue words are `RepHint`'s; "Knees off the floor" only where `Stakes.judgesKnees`. Tests first (`SetupGuideTest`). Done 2026-10-12: `0cf3dd4` (delegate).
- [X] T054 [P] [US3] **The Start tab says banking in one line** (`kotlin/app/src/main/kotlin/holdout/app/sitting/PlacementGuide.kt` `StartPage`; FR-015). Done 2026-10-12: `554eee0` (a "Banked in tens" card), replaced by `64222c0` ("Every 10 push-ups are saved on your phone.", the owner finding the card too much text) (delegate).
- [X] T055 [P] [US3] **Pre-flight says "Place your phone" once, with where it goes** (`kotlin/repcounter/src/main/kotlin/holdout/repcounter/Coach.kt` `AIM_AT_FLOOR`, `kotlin/app/src/main/kotlin/holdout/app/sitting/SittingCopy.kt` `PLACE_PHONE`, `PreflightScreen.kt`, `SittingViewModel.kt` — spoken once like "Hold"; FR-018, FR-019; contracts/words.md). Tests first (`PreflightScreenTest`). Done 2026-10-12: `c3a09db` (delegate).
- [X] T056 [P] [US3] **The camera outline is hidden until the phone stands and is the dashed box alone** (`kotlin/app/src/main/kotlin/holdout/app/sitting/SittingScreen.kt` `PositionOverlay`, `PlacementGuide.kt`; coach words with a soft shadow; FR-020b). Done 2026-10-12: `c3a09db` (hidden until the phone stands, shadow), `31778a5` (no push-up figure) (delegate).
- [X] T057 [P] [US3] **No "Under 10 minutes left…" pill on the pre-flight or counting page; the time line alone turns warning** (`kotlin/app/src/main/kotlin/holdout/app/sitting/SittingChrome.kt`, `CountingScreen.kt`; FR-020b, 001 FR-047). Tracked in 001 T298. Done 2026-10-12: `701e8bb` (delegate).
- [ ] T058 [P] [US1] **Tour card 3 draws the placement from above** — the set-up guide's animated "At 45°" top view as a panel of about 120 × 74 dp, left of the rep count, labelled "~1.5m ahead of your shoulder", the drawing shared with `PlacementGuide.kt` (`kotlin/app/src/main/kotlin/holdout/app/tour/TourScreen.kt` `TourCounting`; FR-013, 001 FR-067; D-R4-15, D-R4-20, D-R4-22). Test first (`kotlin/app/src/test/kotlin/holdout/app/tour/TourScreenTest.kt`). Tracked in 001 T299. Open: being built when this phase was written; the privacy line on card 3 is done (`35e4c6e`, 001 T297).
- [X] T059 [P] [docs] [US3] **Spec, words, quickstart and plan for walk round 5** (`specs/002-rep-placement-rules/spec.md` Session 2026-10-12 and FR-013, FR-014, FR-015, FR-015a, FR-018, FR-019, FR-020b; `contracts/words.md` "The set-up guide", "Pre-flight, first correction" and "The tour's counting card"; `quickstart.md` rows Guide, Place and Tour; `plan.md` structure line), through `/speckit-clarify` and `/speckit-plan`. Done 2026-10-12: `522781b` and `3682d50`.
- [X] T060 [P] [docs] [US3] **Screen map and compose map** (`docs/holdout_screen_map.md` — S2's tabs, S3's coach, outline and time line, version +0.1; `.claude/skills/holdout-ui/references/compose-map.md`). Not spec-kit artifacts. Tracked in 001 T301; done 2026-10-12, in the commit that follows this phase's tasks commit.

**Dependencies:** T052 before T053 and T054 (same file); T055 before T056 (same screen); T058 after T052 (the top-view drawing is shared); T059 and T060 read the code. **Open:** the device walk of quickstart rows Guide, Place and Tour on a Seeker (001 T270).

---

## Dependencies

- Phase 1 → Phase 2 → US1, US2 (both P1, independent of each other) → US3 → US4.
- T018, T023, T024 wait on `specs/001-holdout-hackathon` T042, T059, T065.
- T041 builds on T040; T042 on T041 (the head-on reading is removed only once no sitting starts head-on).
- T046 and T047 wait on the reviewer; T046 depends on T045 (the page lists the reps to label).
- T028 and T033 wait on 001 T095 (beta testers).
- T034 waits on T033 (it writes T033's results into the weaknesses rows).
- Phase 8 waits on T033 and T035.
- Phase 9 (T049–T051) is built; it does not wait on Phase 7's open T046–T048 (labelling and a recording) or on Phase 8, and is independent of both; T050 follows T049 (the guide's knees line names the pill).
- Phase 10 (T052–T060) is built but for T058 (the tour's top view); it waits on nothing in Phases 7 and 8.

## Parallel execution examples

- T032, T033 and T048 are independent recording/measurement jobs; T046 and T047 are independent labelling jobs.
- In Phase 8, T037 and the T039 app scaffold can proceed side by side once T036 fixes the crop format.

## Implementation strategy

The MVP — US1 and US2 on the counter — is built and on the phone. Since 2026-10-08 the Daily 25 rehearses the Easy set-up at 45° and the head-on path is gone (T040–T042). What remains: label what only today's counter sees and confirm Claude's labels so the ratchet holds real ground truth (T046, T047), check the Daily 25 at the front edge of its band (T048), then widen the evidence beyond one person (T033) before any classifier work (Phase 8).
