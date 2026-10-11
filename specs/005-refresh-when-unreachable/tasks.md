---

description: "Task list for 005 Check the Chain Again"

---

# Tasks: Check the Chain Again

**Input**: Design documents from `specs/005-refresh-when-unreachable/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/check-again-ui.md, quickstart.md

**Tests**: included — constitution VII (tests precede logic); unit tests on the JVM in `kotlin/app/src/test`.

**UI**: every screen task goes through the `holdout-ui` skill (tokens, components, copy rules).

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup

None: no new dependency (Material 3's `pullToRefresh` is in the existing BOM, research R4).

## Phase 2: Foundational (blocks both stories)

- [ ] T001 [P] Write failing `ChainCheckTest` in `kotlin/app/src/test/kotlin/holdout/app/ChainCheckTest.kt` covering every row of data-model.md's transition table: `request` while the chain answers starts nothing; `request` while `checking` joins (no read); `request` from idle or `stillUnreachable` with `offline == true` → `checking`, read started; `finished(answered = false)` → `stillUnreachable`; `finished(answered = true)` → idle; `answered()` from any state → idle
- [ ] T002 [P] Write failing `CheckAgainCopyTest` in `kotlin/app/src/test/kotlin/holdout/app/CheckAgainCopyTest.kt`: the strings of contracts/check-again-ui.md built for a given network name — *"Check again"*, *"Checking N…"*, *"N still can't be reached."*, *"N can't be reached right now."* — name the network and contain none of "offline", "internet", "error" (SC-004, FR-007)
- [ ] T003 Implement `ChainCheck` (fields `checking`, `stillUnreachable`; `request(offline): Pair<ChainCheck, Boolean>`-style step, `finished(answered)`, `answered()`) and a `CheckAgainCopy` object holding the T002 strings in `kotlin/app/src/main/kotlin/holdout/app/holdout/ChainCheck.kt`; T001 and T002 pass
- [ ] T004 Add `check: ChainCheck` to `HoldoutViewModel.UiState` and `checkNow(alsoRead: suspend () -> Unit)` in `kotlin/app/src/main/kotlin/holdout/app/holdout/HoldoutViewModel.kt`: gated by `ChainCheck.request(offline)`; starts `refreshNow()` and `alsoRead()` together; ends with `finished(answered = !offline)`; any successful `refreshNow()` applies `answered()`; reads only while `refresher != null` (in front, FR-008); never calls `submitDay` or touches `autoSubmit` (FR-004)
- [ ] T005 In `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, factor the chain-time loop body into one suspend function (read `refreshChainTime(clock)` and save the reading) used by the loop and by checks, and one `onCheck(roster: SolanaPublicKey?)` that calls `holdouts.checkNow { readChainTime(); roster?.let { holdouts.loadRoster(it) } }` (research R2)
- [ ] T006 Add to `kotlin/app/src/main/kotlin/holdout/app/ui/Components.kt`, via the `holdout-ui` skill: `CheckAgain(check, onCheck)` — a secondary button *Check again* / busy *Checking N…*, and *"N still can't be reached."* under it when `stillUnreachable`; `ChainCheckBox(offline, checking, onCheck, content)` — `Modifier.pullToRefresh(enabled = offline)` with `PullToRefreshDefaults.Indicator` in design tokens

**Checkpoint**: the gate and the copy are tested; the view model can check; the components exist.

## Phase 3: User Story 1 — Check again from the waiting day (P1) 🎯 MVP

**Goal**: on the home with a day waiting and the chain unreachable, the member checks now and sees the result.

**Independent Test**: quickstart steps 1–3.

- [ ] T007 [US1] In `kotlin/app/src/main/kotlin/holdout/app/home/CohortHome.kt`, add `check: ChainCheck` and `onCheck` parameters; under the *"Done — waiting for network"* body and under the *"N can't be reached right now…"* notice, show `CheckAgain` while `offline`; wrap the screen's scrolling column in `ChainCheckBox(offline, …)`
- [ ] T008 [US1] Wire `CohortHome` in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt` with `check = chain.check` and `onCheck = { onCheck(h.address) }`

**Checkpoint**: US1 works alone: quickstart 1–3 on a phone.

## Phase 4: User Story 2 — Check again from any screen that shows the chain (P2)

**Goal**: the no-Holdout home, a Holdout's details and the forfeit screen offer the same check while the chain can't be reached.

**Independent Test**: quickstart steps 4–5.

- [ ] T009 [US2] In `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, under *"N unreachable — the Daily 25 still works"* on the no-Holdout home, show `CheckAgain` (`onCheck(null)`), and wrap that home in `ChainCheckBox`
- [ ] T010 [P] [US2] In `kotlin/app/src/main/kotlin/holdout/app/cohort/CohortDetailScreen.kt`, add `offline`, `check`, `onCheck`; while `offline` show *"N can't be reached right now."* under the title with `CheckAgain`, and wrap in `ChainCheckBox`
- [ ] T011 [P] [US2] Same in `kotlin/app/src/main/kotlin/holdout/app/cohort/ForfeitScreen.kt`
- [ ] T012 [US2] Wire T010 and T011 in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt` with `chain.offline`, `chain.check`, `onCheck(h.address)`

## Phase 5: Polish

- [ ] T013 Run the `holdout-ui` visual check on the five states of contracts/check-again-ui.md (screenshots on a phone or emulator)
- [ ] T014 Run `bash scripts/ci-local.sh --only kotlin` (ktlint, unit tests, both debug APKs) and fix what it finds
- [ ] T015 Walk quickstart.md on a phone against a local validator

## Dependencies

- T001, T002 → T003 → T004 → T005; T006 after T003 (copy) — all before Phase 3.
- US1 (T007–T008) and US2 (T009–T012) are independent of each other after Phase 2.
- T013–T015 last.

## Parallel examples

- T001 ∥ T002 (two test files).
- T010 ∥ T011 (two screens), then T012.

## Implementation Strategy

MVP = Phase 2 + US1: the owner's case (the waiting day after `adb reverse` dropped). US2 then extends the same component to the other screens. One PR into `001-holdout-hackathon`.
