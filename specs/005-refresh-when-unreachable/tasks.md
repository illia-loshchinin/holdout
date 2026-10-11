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

- [X] T001 [P] Write failing `ChainCheckTest` in `kotlin/app/src/test/kotlin/holdout/app/ChainCheckTest.kt` covering every row of data-model.md's transition table: `request` while the chain answers starts nothing; `request` while `checking` joins (no read); `request` from idle or `stillUnreachable` with `offline == true` → `checking`, read started; `finished(answered = false)` → `stillUnreachable`; `finished(answered = true)` → idle; `answered()` from any state → idle
- [X] T002 [P] Write failing `CheckAgainCopyTest` in `kotlin/app/src/test/kotlin/holdout/app/CheckAgainCopyTest.kt`: the strings of contracts/check-again-ui.md built for a given network name — *"Check again"*, *"Checking N…"*, *"N still can't be reached."*, *"N can't be reached right now."* — name the network and contain none of "offline", "internet", "error" (SC-004, FR-007)
- [X] T003 Implement `ChainCheck` (fields `checking`, `stillUnreachable`; `request(offline): Pair<ChainCheck, Boolean>`-style step, `finished(answered)`, `answered()`) and a `CheckAgainCopy` object holding the T002 strings in `kotlin/app/src/main/kotlin/holdout/app/holdout/ChainCheck.kt`; T001 and T002 pass
- [X] T004 Add `check: ChainCheck` to `HoldoutViewModel.UiState` and `checkNow(alsoRead: suspend () -> Unit)` in `kotlin/app/src/main/kotlin/holdout/app/holdout/HoldoutViewModel.kt`: gated by `ChainCheck.request(offline)`; starts `refreshNow()` and `alsoRead()` together; ends with `finished(answered = !offline)`; any successful `refreshNow()` applies `answered()`; reads only while `refresher != null` (in front, FR-008); never calls `submitDay` or touches `autoSubmit` (FR-004)
- [X] T005 In `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, factor the chain-time loop body into one suspend function (read `refreshChainTime(clock)` and save the reading) used by the loop and by checks, and one `checkChain(roster: SolanaPublicKey?)` that calls `holdouts.checkNow { roster?.let(holdouts::loadRoster); readChainTime() }`; the roster load is fire-and-forget and the check does not wait for it (research R2)
- [X] T006 Add to `kotlin/app/src/main/kotlin/holdout/app/ui/Components.kt`, via the `holdout-ui` skill: `CheckAgain(checking, stillUnreachable, onCheck)` — a secondary button *Check again* / busy *Checking N…*, and *"N still can't be reached."* under it when `stillUnreachable` and not `checking`; `ChainUnreachable(checking, stillUnreachable, onCheck)` — the line *"N can't be reached right now."* plus `CheckAgain`, for screens that say nothing else about the chain; `ChainCheckBox(offline, checking, onCheck, content)` — `Modifier.pullToRefresh(enabled = offline)` with `PullToRefreshDefaults.Indicator` in design tokens

**Checkpoint**: the gate and the copy are tested; the view model can check; the components exist.

## Phase 3: User Story 1 — Check again from the waiting day (P1) 🎯 MVP

**Goal**: on the home with a day waiting and the chain unreachable, the member checks now and sees the result.

**Independent Test**: quickstart steps 1–3.

- [X] T007 [US1] In `kotlin/app/src/main/kotlin/holdout/app/home/CohortHome.kt`, add `checking: Boolean`, `stillUnreachable: Boolean` and `onCheck` parameters; under the *"Done — waiting for network"* body and under the *"N can't be reached right now…"* notice, show `CheckAgain` while `offline`; wrap the screen's scrolling column in `ChainCheckBox(offline, …)`
- [X] T008 [US1] Wire `CohortHome` in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt` with `checking = chain.check.checking`, `stillUnreachable = chain.check.stillUnreachable` and `onCheck = { checkChain(h.address) }`

**Checkpoint**: US1 works alone: quickstart 1–3 on a phone.

## Phase 4: User Story 2 — Check again from any screen that shows the chain (P2)

**Goal**: the no-Holdout home, a Holdout's details and the forfeit screen offer the same check while the chain can't be reached.

**Independent Test**: quickstart steps 4–5.

- [X] T009 [US2] In `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, under *"N unreachable — the Daily 25 still works"* on the no-Holdout home, show `CheckAgain` (`checkChain(null)`), and wrap that home in `ChainCheckBox`
- [X] T010 [P] [US2] In `kotlin/app/src/main/kotlin/holdout/app/cohort/CohortDetailScreen.kt`, add `offline`, `checking`, `stillUnreachable`, `onCheck` parameters; while `offline` show `ChainUnreachable(checking, stillUnreachable, onCheck)` under the title, and wrap in `ChainCheckBox`
- [X] T011 [P] [US2] Same in `kotlin/app/src/main/kotlin/holdout/app/cohort/ForfeitScreen.kt`
- [X] T012 [US2] Wire T010 and T011 in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt` with `chain.offline`, `chain.check.checking`, `chain.check.stillUnreachable`, `checkChain(target.address)`
- [X] T017 [US2] Fixes from the 2026-10-11 phone walk (commits b66f751, 0274804):
  - `ChainUnreachable` on the no-Holdout home over an earlier read, in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, and on the `todayDone` branch in `kotlin/app/src/main/kotlin/holdout/app/home/CohortHome.kt`.
  - `ChainUnreachable` keeps one steady line that turns into *"N still can't be reached."*; `CheckAgain`'s still line is centred and in the warning colour, in `kotlin/app/src/main/kotlin/holdout/app/ui/Components.kt`.
  - `CheckAgainCopy.checking` lowercases a leading "The" mid-sentence, in `kotlin/app/src/main/kotlin/holdout/app/holdout/ChainCheck.kt`, tested in `CheckAgainCopyTest`.

## Phase 5: Polish

- [X] T013 Run the `holdout-ui` visual check on the five states of contracts/check-again-ui.md (screenshots on a phone or emulator) — *2026-10-11, Seeker against the localnet: the home over an earlier read, details, forfeit, the still line, the pull indicator while unreachable and none while answering. The waiting-day state was not on screen: no running Holdout on that ledger. The walk's fixes are in b66f751 and 0274804.*
- [X] T014 Run `bash scripts/ci-local.sh --only kotlin` (ktlint, unit tests, both debug APKs) and fix what it finds
- [ ] T015 Walk quickstart.md (steps 1–6) on a phone against a local validator — *2026-10-11: steps 2, 4 and 5 walked. Steps 1 and 3 need a running Holdout with today's reps done, which that ledger lacked (no `localnet-crank.sh --loop` running), so this stays open.*
- [ ] T016 Check SC-003: review that `HoldoutViewModel.checkNow` and `checkChain` in `kotlin/app/src/main/kotlin/holdout/app/` call no `submitDay` and do not touch `AutoSubmit`, and in T015's step 3 see the wallet's approval open by itself once, then *"Done — tap to submit"* after a decline (001 FR-024b) — *review half done 2026-10-11: `checkNow` and `checkChain` call no `submitDay` and do not touch `AutoSubmit`. The walk half waits on T015 step 3.*

## Dependencies

- T001, T002 → T003 → T004 → T005; T006 after T003 (copy) — all before Phase 3.
- US1 (T007–T008) and US2 (T009–T012) are independent of each other after Phase 2.
- T013–T016 last; T016's walk part runs with T015.

## Parallel examples

- T001 ∥ T002 (two test files).
- T010 ∥ T011 (two screens), then T012.

## Implementation Strategy

MVP = Phase 2 + US1: the owner's case (the waiting day after `adb reverse` dropped). US2 then extends the same component to the other screens. One PR into `001-holdout-hackathon`.
