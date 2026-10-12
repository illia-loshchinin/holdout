---

description: "Task list for 004 Holdout Lab and the member app"
---

# Tasks: Holdout Lab and the Member App

**Input**: Design documents from `specs/004-lab-and-member-apps/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/variants-and-seams.md](contracts/variants-and-seams.md), [quickstart.md](quickstart.md)

**Tests**: Included. Constitution VII and research R10 call for them. Seam tests come before the seams change.

**Organization**: By user story: US1 Lab (P1), US2 member has no recorder (P1), US3 nothing for judges (P1), US4 one counting source (P2).

**One correction to the plan**: `scripts/pull-traces.sh` uses `adb run-as`, which works only on a debuggable app. So testers get **`labDebug`**, not `labRelease` as data-model §1 says. Tasks below use `labDebug`; `/speckit-analyze` should carry the fix into data-model §1.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on an unfinished task)
- **[Story]**: US1–US4
- Paths from the repository root; Gradle commands from `kotlin/`

---

## Phase 1: Setup — the flavors

- [X] T001 In `kotlin/app/build.gradle.kts`:
  - add `flavorDimensions += "app"` and the flavors `member` (no suffix) and `lab` (`applicationIdSuffix = ".lab"`), as contracts §1 shows;
  - change `debugImplementation(libs.androidx.camera.video)` to `"labImplementation"(libs.androidx.camera.video)`.

  Leave `CheckReleaseHasNoRecorder` for T015. Then run `./gradlew :app:tasks --all | grep -i assemble` and confirm the four variants `assembleMemberDebug`, `assembleMemberRelease`, `assembleLabDebug` and `assembleLabRelease`.
- [X] T002 Rename every Gradle variant reference that breaks with flavors:
  - `.github/workflows/ci.yml` lines 274 and 279: `:app:testDebugUnitTest` → `:app:testMemberDebugUnitTest :app:testLabDebugUnitTest`, and `:app:assembleDebug` → `:app:assembleMemberDebug :app:assembleLabDebug`;
  - `docs/holdout_setup_runbook.md` line 219: `:app:installDebug` → `:app:installMemberDebug`, adding a line for `:app:installLabDebug`.

  `specs/001` and `specs/002` quickstarts are updated in T030.

**Checkpoint**: `./gradlew :app:assembleMemberDebug :app:assembleLabDebug` builds two APKs (behaviour still identical, both still contain everything).

---

## Phase 2: Foundational — the seams (blocks every story)

### Tests first

- [X] T003 [P] In `kotlin/app/src/test/kotlin/holdout/app/sitting/SittingRecorderSeamTest.kt`, write a test for `SittingViewModel`:
  - built with a fake `SittingRecorder`, it calls `record` for analysed frames, `label(...)` when `saveLabel` is called, and `close()` once on end;
  - built with `recorder = null`, it writes no file anywhere under a temp dir, and `canLabelTrace` is false (data-model §3 invariant).

  It must fail until T005–T006.

### Seams

- [X] T004 Create `kotlin/app/src/main/kotlin/holdout/app/sitting/SittingRecorder.kt`, the interface in contracts §3. `record` takes exactly the arguments `TraceRecorder.record` takes today.
- [X] T005 In `kotlin/app/src/main/kotlin/holdout/app/sitting/SittingViewModel.kt`:
  - replace the `traceDir: File?` parameter and `TraceRecorder(it, stakes)` (line 97) with `recorder: SittingRecorder? = null`;
  - keep every call site (`record`, `label`, `close`, `file?.name` → `fileName`) behaving as today;
  - rewrite the KDoc that says "Debug builds" to say "Holdout Lab".

  T003 must now pass.
- [X] T006 Make `kotlin/app/src/main/kotlin/holdout/app/sitting/TraceRecorder.kt` implement `SittingRecorder`, with no behaviour change. Keep it in `src/main` for now; T009 moves it.
- [X] T007 Create `kotlin/app/src/main/kotlin/holdout/app/sitting/LabSittingTools.kt` (contracts §3). In `SittingScreen.kt`, replace the `cleanScreen` and `recordVideo` parameters and the `BuildConfig.DEBUG` checks at lines 214, 271 and 492 with `tools: LabSittingTools? = null`:
  - label prompt only when `tools != null && viewModel.canLabelTrace`;
  - readouts only when `tools?.readouts == true`;
  - clean screen from `tools?.cleanScreen`;
  - video only when `tools != null` and the factory returns one.
- [X] T008 Create `AppRoot(sender: ActivityResultSender, modifier: Modifier)` in `kotlin/app/src/member/kotlin/holdout/app/AppRoot.kt` (calls `HoldoutApp`). Make `kotlin/app/src/main/kotlin/holdout/app/MainActivity.kt` call `AppRoot(...)` instead of `HoldoutApp(...)`.

**Checkpoint**: `./gradlew :app:testMemberDebugUnitTest :app:assembleMemberDebug` passes. `HoldoutApp` still passes the old values through (T016 removes them).

---

## Phase 3: User Story 1 — A tester records and labels a set in Holdout Lab (P1) 🎯 MVP

**Goal**: Holdout Lab installs beside Holdout. It chooses a mode, counts with no wallet or chain, saves a trace and optional video, and asks for a label (FR-001–FR-009).

**Independent Test**: quickstart §4, rows 1–6.

- [X] T009 [US1] Move the Lab-only files into `kotlin/app/src/lab/kotlin/holdout/app/` with `git mv`, keeping the package names (contracts §2):
  - `sitting/TraceRecorder.kt`;
  - `sitting/TraceLabelDialog.kt`;
  - `ui/TestSwitch.kt`;
  - `src/debug/kotlin/holdout/app/sitting/SittingVideoFactory.kt` → `src/lab/kotlin/holdout/app/sitting/SittingVideoFactory.kt`;
  - `src/release/kotlin/holdout/app/sitting/SittingVideoFactory.kt` → `src/member/kotlin/holdout/app/sitting/SittingVideoFactory.kt`.

  Also move `kotlin/app/src/test/kotlin/holdout/app/TraceRecorderTest.kt` to `kotlin/app/src/testLab/kotlin/holdout/app/TraceRecorderTest.kt`.
- [X] T010 [P] [US1] In `kotlin/app/src/testLab/kotlin/holdout/app/lab/LabModesTest.kt`, write tests (they must fail before T011):
  - Daily 25 → `Stakes.DAILY`, Easy Holdout → `Stakes.EASY`, Hard Holdout → `Stakes.STAKED`;
  - `labScope(mode, startMillis)` is `"lab:<MODE>:<startMillis>"`, and two sets started 1 ms apart get different scopes;
  - settings default on a fresh store to `recordsVideo = false`, `cleanScreen = false`, `target = 25`, with `target` clamped to 1–100 (data-model §2).
- [X] T011 [US1] Create `kotlin/app/src/lab/kotlin/holdout/app/lab/LabApp.kt` (contracts §5, research R3), following `.claude/skills/holdout-ui` (theme tokens, `PrimaryButton`/`SecondaryButton`, no literal colours):
  - **Choose screen:** title "Holdout Lab", three mode rows, `RecordVideoSwitch` and a clean-screen `TestSwitch`, a target stepper, and "N sets on this phone" counted from `files/traces/*.csv`.
  - **Set:** on tap, build a `SittingViewModel` with Lab's `Store` and `StreakStore`, `wallet = "lab"`, `scope = labScope(...)`, `target`, `chainSecondsNow = { null }`, `rollsOver = false`, `stakes = mode`, `recorder = TraceRecorder(File(filesDir, "traces"), mode)`. Show `SittingScreen` with `tools = LabSittingTools(readouts = !cleanScreen, cleanScreen, labelPrompt = { vm, done -> TraceLabelDialog(...) })`.
  - **Back:** return to Choose after the label.
  - **No network:** it never builds a wallet, `Rpc` or `ChainRepository`.

  T010 must now pass.
- [X] T012 [US1] Create `kotlin/app/src/lab/kotlin/holdout/app/AppRoot.kt`, which calls `LabApp(modifier)` and ignores `sender`.
- [X] T013 [P] [US1] Add Lab resources in `kotlin/app/src/lab/res/` (research R8):
  - `values/strings.xml` with `app_name` = "Holdout Lab";
  - an adaptive launcher icon (`mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml`, `drawable/ic_launcher_foreground.xml`): the design book's 3×3 status crowd (`docs/design/assets/app-icon/README.md`, the larger Holdout in the middle) drawn in `#80ECFF` on a `#000000` background, inside the 66 dp safe zone. *Superseded 2026-10-10 by T033: Holdout's icon v2 with the H in warning coral.*
- [X] T014 [US1] In `scripts/pull-traces.sh`:
  - add `--package <id>`, defaulting to `holdout.app.lab`;
  - replace every `run-as holdout.app` (lines 27, 37, 41, 42, 45) with `run-as "$package"`;
  - update its usage text to say Lab sets come from `holdout.app.lab` (the `labDebug` build), and that `--package holdout.app` drains traces left by pre-split debug builds (research R7).

**Checkpoint**: quickstart §4 works on a phone with `labDebug`.

---

## Phase 4: User Story 2 — A member's app never records them (P1)

**Goal**: No member build contains a recorder, trace saving, label form, tester switch or readout, and a build check fails one that does (FR-010).

**Independent Test**: quickstart §2 (the check both ways) and §5 row 5.

- [X] T015 [US2] In `kotlin/app/build.gradle.kts`, replace `CheckReleaseHasNoRecorder` (lines 230–257) with `CheckMemberHasNoLabCode` per contracts §4:
  - scan `src/main`, `src/member`, `src/debug`, `src/release`, `src/memberDebug` and `src/memberRelease`;
  - fail on `androidx.camera.video`, `TraceRecorder`, `TraceLabelDialog`, `TestSwitch` or `LabApp`, with the exact message from contracts §4;
  - wire it before every task matching `assembleMember*` or `bundleMember*`;
  - update the comment to cite constitution X v2.1.0.
- [X] T016 [US2] In `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, remove every tester feature:
  - `recordVideo` and `onRecordVideoChange` (lines 248–252);
  - `traceDir = if (BuildConfig.DEBUG) …` (lines 374 and 413): pass no recorder;
  - `stakedTest`, `cleanScreen` and `onResetToday` (lines 698–706), and the state behind them;
  - `SittingScreen`'s `cleanScreen` and `recordVideo` arguments: no `tools` passed.
- [X] T017 [US2] In `kotlin/app/src/main/kotlin/holdout/app/home/HomeScreen.kt`, remove the parameters `stakedTest`, `onStakedTestChange`, `cleanScreen`, `onCleanScreenChange`, `recordVideo`, `onRecordVideoChange` and `onResetToday`, and the TEST rows they draw (from line 83). Remove the previews that set them.
- [X] T018 [US2] Run `./gradlew :app:checkMemberHasNoLabCode :app:assembleMemberDebug :app:assembleMemberRelease`; all must pass. Then run quickstart §2's negative proof and revert it.

**Checkpoint**: a member sitting shows no label dialog, readouts or "rec ·" line (quickstart §5 row 5).

---

## Phase 5: User Story 3 — Nothing for judges only (P1)

**Goal**: No walkthrough, judge link or judge text. A plain "About Holdout" keeps the tour, Disconnect and the devnet notice. Weaknesses are in the README only (FR-012–FR-015).

**Independent Test**: quickstart §5 rows 1–4.

- [X] T019 [P] [US3] In `kotlin/app/src/main/kotlin/holdout/app/wallet/ConnectScreen.kt`:
  - remove the `onWalkthrough` parameter and the `TextButton` at lines 113–114 ("For judges: watch a full cycle" / "Watch a full cycle (3 min)");
  - rewrite the KDoc at lines 42–46: before a wallet, only connecting and no-wallet help;
  - update both previews.
- [X] T020 [US3] In `kotlin/app/src/main/kotlin/holdout/app/about/AboutScreen.kt`:
  - delete `WalkthroughScreen`;
  - in `AboutScreen`, remove the `onWalkthrough` parameter and its button, the known-weaknesses section and the scripted-wallets list;
  - keep the title "About Holdout", the devnet sentence, "The three-card tour again" and "Disconnect wallet" with its network note;
  - add one `Body` line on scripted members: "Some members in these Holdouts are scripted: real wallets under the same rules, labelled SCRIPTED wherever they appear."
  - rewrite the KDoc (lines 30–31) without judges.
- [X] T021 [US3] In `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`:
  - remove `Route.Walkthrough`, its branch (lines 575–576) and the `beforeWallet` walkthrough branch (lines 264 and 298), plus the `WalkthroughScreen` import;
  - stop passing `onWalkthrough` to `ConnectScreen` and `AboutScreen`;
  - rename the link at line 743 from "About · for judges" to "About Holdout";
  - rewrite the KDoc at line 111 ("recorded walkthrough plays before it").
- [X] T022 [P] [US3] In `kotlin/app/src/main/kotlin/holdout/app/home/CohortHome.kt` line 176, rename "About · wallet" to "About Holdout". In `kotlin/app/src/main/kotlin/holdout/app/tour/TourScreen.kt` line 30, rewrite the comment without the walkthrough or judges.
- [X] T023 [US3] Move `kotlin/app/src/main/assets/weaknesses.json` to `docs/weaknesses.json` with `git mv`, and update both its `$comment` and `scripts/render-weaknesses.mjs` to the new path. Run `node scripts/render-weaknesses.mjs`; `README.md` must not change. Then remove the now-unused weaknesses loading from `AboutScreen.kt` and anything else that read the asset (grep `weaknesses.json` in `kotlin/`).
- [X] T024 [US3] Grep `kotlin/app/src/main` and `kotlin/app/src/member` for `judge`, `Judge`, `walkthrough` and `Walkthrough` (case-insensitive); none may remain in member-visible strings or names. Comments may name the demo video only. Run `bash scripts/check-wording.sh`; it must pass.

**Checkpoint**: quickstart §5 rows 1–4 on a member release build.

---

## Phase 6: User Story 4 — A counting fix reaches both apps (P2)

**Goal**: One counting source, with the same results in both apps (FR-011, SC-003).

**Independent Test**: `:repcounter:test` passes, and both variants compile counting from `src/main` and `:repcounter` only.

- [X] T025 [US4] Check that no file under `kotlin/app/src/lab` or `kotlin/app/src/member` defines or overrides anything in `holdout.app.vision`, `holdout.app.sitting` (other than the seam implementations named in contracts §2) or `holdout.repcounter`. Add that rule as a sentence to the KDoc of `SittingRecorder.kt`.
- [X] T026 [US4] Run `./gradlew :repcounter:test :app:testMemberDebugUnitTest :app:testLabDebugUnitTest`. All pass, and `RecordedTracesTest` replays every committed labelled set (SC-003).

---

## Phase 7: Polish and cross-cutting

- [X] T027 Run quickstart §2 and §3 fully: tests, `ktlintCheck`, both APKs built, both installed side by side.
- [ ] T028 Run `python scripts/design-book.py check --report-only`; it must show no new raw colours from `LabApp.kt`.
- [ ] T029 On a tester phone, before installing the new member build, run `bash scripts/pull-traces.sh --package holdout.app` (quickstart §1). **Needs the product owner's phone.**
- [X] T030 Update the docs that name variants or the old flows:
  - `specs/001-holdout-hackathon/quickstart.md` and `specs/002-rep-placement-rules/quickstart.md` (`assembleDebug` → `assembleMemberDebug` / `assembleLabDebug`, `testDebugUnitTest` → `testMemberDebugUnitTest`), through `/speckit-plan` on each, since they are spec-kit files;
  - `README.md`: Lab is not part of the clone-and-run steps, but the member build command changes;
  - `docs/holdout_screen_map.md`: drop A2 Walkthrough and X1 "About · for judges", and add "About Holdout".
- [ ] T031 Bring 001's plan, tasks and `contracts/android-app.md` in line with the amended 001 spec (no walkthrough, no About for judges, Media3 not needed; T090 and T106–T108), through `/speckit-converge` on 001. Do the same for 003's plan, tasks (T016–T017) and quickstart (row 7), through `/speckit-analyze` then `/speckit-tasks` on 003.
- [ ] T032 Run quickstart §4 and §5 on a Seeker and record the results in the PR. **Needs the product owner's phone.**
- [X] T033 [P] *(Added and done 2026-10-10, product-owner decisions; branch `feat/decisions-2026-10-10`; revised after the PR #36 review, 478cd44.)* Holdout Lab's launcher icon is Holdout's icon v2 with the H of seven in `warning` (`#FF623A`), with no geometry copied: `kotlin/app/src/main/res/drawable/ic_launcher_foreground.xml` and `avd_holdout_splash.xml` take the H's colour from `ic_launcher_h` (`src/main/res/values/ic_launcher_mark.xml`), which `kotlin/app/src/lab/res/values/ic_launcher_mark.xml` overrides, so Lab's splash plays the coral H too; Lab's own themed-icon layer `kotlin/app/src/lab/res/drawable/ic_launcher_monochrome.xml` is the whole crowd with the H, apart from Holdout's H alone (FR-001, research R8). The three rows on Choose say that the Daily 25 and the 7-day count the same way and the 21-day more strictly (contracts §5): `45° · knees allowed · counted as the 7-day`, `7-day · 45° · knees allowed`, `21-day · 45° · from the toes · the stricter count` in `LabMode.detail`, `kotlin/app/src/lab/kotlin/holdout/app/lab/LabSettings.kt`. In the same change the member app's stake sheet and forfeit screen read the fee sentence as reworded by the product owner (001 clarification 2026-10-10, FR-041): "Miss one and you lose your stake. 80 % of it stays with members: 70 % to the prize pool and 10 % to the boost and comeback reserves of Easy · 7-day Holdouts. 20 % goes to the project, and so does the prize pool if nobody finishes." (`Format.lostStakeSplit` in `ui/Components.kt`, tested in `HoldoutPiecesTest.kt`; 001 FR-027 unchanged). Checked: `ktlintCheck`, `:app:testMemberDebugUnitTest` (146), `:app:testLabDebugUnitTest` (155), `:app:assembleMemberDebug`, `:app:assembleLabDebug`, `check-wording.sh`.

---

## Phase 8: Walk round 5 — names, Holdouts Now/Past, set-up guide, pre-flight, tour

*(Added 2026-10-12, by `/speckit-tasks` after the clarification session "2026-10-12" of spec.md (the TEST COHORT label of FR-013 is the Hackathon Holdout's DEVNET COHORT badge, and the short test lobby's Holdouts are named Hackathon Holdouts; FR-013, FR-014) and its planning edits (contracts/variants-and-seams.md, quickstart rows 2 and 4). Numbered from T034, after this feature's own T033: the T106 to T108 that appear in T031 are `specs/001-holdout-hackathon`'s. The rest of the same walk — the Holdouts tab as Now and Past, the set-up guide, the pre-flight page and the tour — is specified in 001 (Phase 34) and 002 (Phase 10), not here. Product-owner decision, the name chosen by his delegate; no screen other than the label changes.)*

**Goal:** the member app's disclosure label on the short test lobby's Holdouts is the quiet neutral "DEVNET COHORT · {day length} DAYS" pill, and members read "Hackathon Holdout".
**Independent test:** quickstart rows 2 and 4 (the label where it applies; a Seeker wallet offered the Hackathon Holdout like any other).

- [X] T034 [US3] **The DEVNET COHORT badge is a neutral pill** (`kotlin/app/src/main/kotlin/holdout/app/ui/Components.kt` `TestCohortBadge`: surface-3 fill, text-secondary text, not the warning tone; FR-013). Done 2026-10-12: `7827ea1` (delegate).
- [X] T035 [US3] **The short test lobby's Holdouts are "Hackathon Holdout" in the member app** (`kotlin/app/src/main/kotlin/holdout/app/` — the stake sheet, lobby, Today, Holdouts, forfeit, leaderboard and reminder strings and the tests that read them; FR-014). Done 2026-10-12: `f4d77af` (the name, across 31 files), `efde239` (a reminder says the name once) (delegate).
- [X] T036 [P] [docs] [US3] **Spec, contract and quickstart wording** (`specs/004-lab-and-member-apps/spec.md` Session 2026-10-12 and FR-013, FR-014 and the names note; `contracts/variants-and-seams.md`; `quickstart.md` rows 2 and 4), through `/speckit-clarify` and `/speckit-plan`. Done 2026-10-12: `cfb8b61` and `439b894`.
- [X] T037 [US3] **The short test lobby's Holdouts are "Hackathon · {n}-day" in the member app** (`kotlin/app/src/main/kotlin/holdout/app/` — the same strings as 001 T339: the stake sheet, lobby, Today, Holdouts, forfeit, leaderboard and reminder strings and the tests that read them; FR-014; 001 FR-044a). Supersedes T035's "Hackathon Holdout"; the DEVNET COHORT badge stays. The spec, contract and quickstart wording is done through `/speckit-clarify` and `/speckit-plan` (this session). Done 2026-10-12: `fbea71c` (with 001 T339).

**Dependencies:** T034 and T035 are built (001 T294 tracks the same commits); T036 reads the code. **Open:** `kotlin/app/src/main/kotlin/holdout/app/ui/StatusChip.kt` still says in a comment that `warning` is the tone for DEVNET COHORT.

---

## Dependencies & Execution Order

- **Setup:** T001 → T002.
- **Foundational:** T003 (test) → T004 → T005 → T006, and T007 and T008 after T004. All need T001.
- **US1:** needs Phase 2. T009 → T010 (test) → T011 → T012. T013 and T014 can run any time after T001.
- **US2:** needs T009 (files moved) and Phase 2. T015 → T016 → T017 → T018.
- **US3:** needs Phase 2 only. T019, T020 and T022 are in different files; T021 after T019 and T020; T023; T024 last.
- **US4:** after US1–US3.
- **Polish:** after all stories. T029 must happen **before** any tester phone gets the new member build.

## Parallel Opportunities

- **Tests:** T003 and T010.
- **Lab extras:** T013 (resources) and T014 (script) alongside T011.
- **Judge removal:** T019 and T022 alongside T020.
- **Stories:** US2 (T015–T018) and US3 (T019–T024) once T009 is done.

```text
A: T003 → T004 → T005 → T006 → T009 → T010 → T011 → T012
B: T007 → T008 → T015 → T016 → T017 → T018
C: T013, T014, T019, T020, T022 → T021 → T023 → T024
```

## Implementation Strategy

**MVP:** Phases 1–3 (T001–T014). Holdout Lab records, labels and pulls sets, while the member app still behaves as today. That alone unblocks counting work.

**Then:** US2 and US3 make the member app what you submit: no recorder, nothing for judges. US4 confirms one counting source. Polish carries the docs.

**Deadline:** T001–T028 are about half a day. Do it before the screens freeze on Sunday at 18:00, so the video is filmed on the final member app. T029 and T032 need your phone.
