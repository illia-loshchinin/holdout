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

None: no new dependency (Material 3's `pullToRefresh` is in the existing BOM, research R4). T019 later adds one test-only dependency, `kotlinx-coroutines-test` 1.9.0.

## Phase 2: Foundational (blocks both stories)

- [X] T001 [P] Write failing `ChainCheckTest` in `kotlin/app/src/test/kotlin/holdout/app/ChainCheckTest.kt` covering every row of data-model.md's transition table: `request` while the chain answers starts nothing; `request` while `checking` joins (no read); `request` from idle or `stillUnreachable` with `offline == true` → `checking`, read started; `finished(answered = false)` → `stillUnreachable`; `finished(answered = true)` → idle; `answered()` from any state → idle
- [X] T002 [P] Write failing `CheckAgainCopyTest` in `kotlin/app/src/test/kotlin/holdout/app/CheckAgainCopyTest.kt`: the strings of contracts/check-again-ui.md built for a given network name — *"Check again"*, *"Checking N…"*, *"N still can't be reached."*, *"N can't be reached right now."* — name the network and contain none of "offline", "internet", "error" (SC-004, FR-007)
- [X] T003 Implement `ChainCheck` (fields `checking`, `stillUnreachable`; `request(offline): Pair<ChainCheck, Boolean>`-style step, `finished(answered)`, `answered()`) and a `CheckAgainCopy` object holding the T002 strings in `kotlin/app/src/main/kotlin/holdout/app/holdout/ChainCheck.kt`; T001 and T002 pass
- [X] T004 Add `check: ChainCheck` to `HoldoutViewModel.UiState` and `checkNow(alsoRead: suspend () -> Unit)` in `kotlin/app/src/main/kotlin/holdout/app/holdout/HoldoutViewModel.kt`: gated by `ChainCheck.request(offline)`; starts `refreshNow()` and `alsoRead()` together; ends with `finished(answered = !offline)`; any successful `refreshNow()` applies `answered()`; reads only while `refresher != null` (in front, FR-008); never calls `submitDay` or touches `autoSubmit` (FR-004). A side read that throws is caught in its own child, and `finished` runs in a `finally` (T018)
- [X] T005 In `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, factor the chain-time loop body into one suspend function (read `refreshChainTime(clock)` and save the reading) used by the loop and by checks, and one `checkChain(roster: SolanaPublicKey?)` that calls `holdouts.checkNow { roster?.let(holdouts::loadRoster); readChainTime() }`; the roster load is fire-and-forget and the check does not wait for it (research R2)
- [X] T006 Add to `kotlin/app/src/main/kotlin/holdout/app/ui/Components.kt`, via the `holdout-ui` skill: `CheckAgain(checking, stillUnreachable, onCheck)` — a secondary button *Check again* / busy *Checking N…*, and *"N still can't be reached."* under it when `stillUnreachable` and not `checking`; `ChainUnreachable(checking, stillUnreachable, onCheck)` — the line *"N can't be reached right now."* plus `CheckAgain`, for screens that say nothing else about the chain; `ChainCheckBox(offline, checking, onCheck, content)` — `Modifier.pullToRefresh(enabled = offline)` with `PullToRefreshDefaults.Indicator` in design tokens, plus a *Check again* accessibility custom action while `offline` (T018)

**Checkpoint**: the gate and the copy are tested; the view model can check; the components exist.

## Phase 3: User Story 1 — Check again from the waiting day (P1) 🎯 MVP

**Goal**: on the home with a day waiting and the chain unreachable, the member checks now and sees the result.

**Independent Test**: quickstart steps 1–3.

- [X] T007 [US1] In `kotlin/app/src/main/kotlin/holdout/app/home/CohortHome.kt`, add `checking: Boolean`, `stillUnreachable: Boolean` and `onCheck` parameters; under the *"Done — waiting for network"* body and under the *"N can't be reached right now…"* notice, show `CheckAgain` while `offline`; wrap the screen's scrolling column in `ChainCheckBox(offline, …)`
- [X] T008 [US1] Wire `CohortHome` in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt` with `checking = chain.check.checking`, `stillUnreachable = chain.check.stillUnreachable` and `onCheck = { checkChain(h.address) }`

**Checkpoint**: US1 works alone: quickstart 1–3 on a phone.

## Phase 4: User Story 2 — Check again from any screen that shows the chain (P2)

**Goal**: the no-Holdout home, a Holdout's details and the forfeit screen offer the same check while the chain can't be reached.

**Independent Test**: quickstart steps 4–6.

- [X] T009 [US2] In `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, under *"N unreachable — the Daily 25 still works"* on the no-Holdout home, show `CheckAgain` (`checkChain(null)`), and wrap that home in `ChainCheckBox`
- [X] T010 [P] [US2] In `kotlin/app/src/main/kotlin/holdout/app/cohort/CohortDetailScreen.kt`, add `offline`, `checking`, `stillUnreachable`, `onCheck` parameters; while `offline` show `ChainUnreachable(checking, stillUnreachable, onCheck)` under the title, and wrap in `ChainCheckBox`
- [X] T011 [P] [US2] Same in `kotlin/app/src/main/kotlin/holdout/app/cohort/ForfeitScreen.kt`
- [X] T012 [US2] Wire T010 and T011 in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt` with `chain.offline`, `chain.check.checking`, `chain.check.stillUnreachable`, `checkChain(target.address)`
- [X] T017 [US2] Fixes from the 2026-10-11 phone walk (commits b66f751, 0274804):
  - `ChainUnreachable` on the no-Holdout home over an earlier read, in `kotlin/app/src/main/kotlin/holdout/app/HoldoutApp.kt`, and on the `todayDone` branch in `kotlin/app/src/main/kotlin/holdout/app/home/CohortHome.kt`.
  - `ChainUnreachable` keeps one steady line that turns into *"N still can't be reached."*; `CheckAgain`'s still line is centred and in the warning colour, in `kotlin/app/src/main/kotlin/holdout/app/ui/Components.kt`.
  - `CheckAgainCopy.checking` lowercases a leading "The" mid-sentence, in `kotlin/app/src/main/kotlin/holdout/app/holdout/ChainCheck.kt`, tested in `CheckAgainCopyTest`.
- [X] T018 [US2] Fixes from the stand-in review (commit 5959873):
  - In `kotlin/app/src/main/kotlin/holdout/app/holdout/HoldoutViewModel.kt`, `checkNow` catches a throwing side read in its own child and ends the check in a `finally`, so "Checking…" can't stick.
  - In `kotlin/app/src/main/kotlin/holdout/app/ui/Components.kt`, `ChainCheckBox` carries a *Check again* accessibility custom action while the chain can't be reached.

## Phase 5: Polish

- [X] T013 Run the `holdout-ui` visual check on the five states of contracts/check-again-ui.md (screenshots on a phone or emulator) — *2026-10-11, Seeker against the localnet: the home over an earlier read, details, forfeit, the still line, the pull indicator while unreachable and none while answering. The waiting-day state was not on screen: no running Holdout on that ledger. The walk's fixes are in b66f751 and 0274804.*
- [X] T014 Run `bash scripts/ci-local.sh --only kotlin` (ktlint, unit tests, both debug APKs) and fix what it finds
- [X] T019 [US1] A JVM test seam for `HoldoutViewModel.checkNow`, from the stand-in review of PR #57:
  - a `ChainReads` interface (`load`, `roster`, `leaderboard`) in `kotlin/app/src/main/kotlin/holdout/app/chain/ChainRepository.kt`, implemented by `ChainRepository` and taken by `HoldoutViewModel`;
  - `WalletRepository` builds its `MobileWalletAdapter` lazily, so the view model can be built on the JVM;
  - `kotlinx-coroutines-test` as a test dependency;
  - `HoldoutViewModelCheckTest` in `kotlin/app/src/test/kotlin/holdout/app/`, covering:
    - no read while not in front (FR-008);
    - a second ask joining the first;
    - the wallet-switch guard;
    - a check's stale read set aside;
    - a scheduled read mid-check clearing it;
    - a throwing side read still ending the check, and still letting the chain's answer count;
    - the check waiting for its side read;
    - the view model recording no send of its own and leaving the waiting day on the phone (part of SC-003). The UI's automatic send (`SendWaitingDay`) is not driven by these tests.
  - *Done in 119c5f6, 730cf3f and c87b0f2: 10 tests pass. Each of these breaks makes them fail: removing the in-front guard, the side-read catch or the wallet guard, or recording a send in `checkNow`.*
- [ ] T015 Walk quickstart.md (steps 1–7) on a phone against a local validator — *2026-10-11: steps 2, 4 and 5 walked. Steps 1 and 3 need a running Holdout with today's reps done, which that ledger lacked (no `localnet-crank.sh --loop` running), so this stays open. Step 7 (the app sent to the background mid-check: no "Checking…" left) is unwalked; it covers T020's pause behaviour.*
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

## Phase 6: Convergence

- [X] T020 [US1] Fixes from the Codex review of PR #57 (PR #61, commits 8c26e91, 33e9001, ba6d9c7 and the roster-failure fix after them) per FR-008 and FR-003 (partial). Supersedes the wording of T004, T018 and T019 where it differs:
  - In `kotlin/app/src/main/kotlin/holdout/app/MainViewModel.kt`, `refreshChainTime(clock, read)` applies an answer only if its read started after the newest read whose answer was already applied (newest wins): an earlier read that lands first is applied and then replaced by the later one, one that lands last is set aside, so `ChainClock` cannot be rewound (research R3; a slot comparison in `ChainClock.observe` was rejected, since slots restart after a local validator reset).
  - In `kotlin/app/src/main/kotlin/holdout/app/holdout/HoldoutViewModel.kt`, `checkNow` keeps its job in `checking`; `pause()` cancels it and resets `check` to `ChainCheck()`, so no read goes on and no "Checking…" stays once the app leaves the front. `finished` no longer runs in a `finally` (T004, T018): it runs straight after a `try`/`catch`, so a throwing read still ends the check, and a cancellation is rethrown and skips it, so a cancelled check never ends as "still can't be reached".
  - Also from the stand-in review of PR #61: `watch()` of another wallet cancels the old wallet's check job, so an A → B → A switch cannot let the old check finish into A's state; and a cancellation raised inside a read while the check's own job is live ends the check through `finished` (`ensureActive()` after catching it) instead of leaving "Checking…" on.
  - A roster read that throws ends only itself (`loadRoster` catches a non-cancellation exception and keeps the last roster), so as a child of the check's job it cannot cancel the check.
  - A check's roster read is a child of the check's job: `loadRoster(cohort, scope = viewModelScope)` in `kotlin/app/src/main/kotlin/holdout/app/holdout/HoldoutViewModel.kt` takes a scope, `checkNow(alsoRead: suspend (CoroutineScope) -> Unit)` hands the check's scope to `alsoRead`, and `HoldoutApp.checkChain` passes it on. It is still not awaited (research R2), but `pause()` cancels it (FR-008); a new `checkNow` cancels the previous check's job first.
  - *Note (2026-10-12, PR #62 review, commit `b0d146a`):* `loadRoster` now allows one read per cohort in flight, so a check starts a roster read of its own, as a child of the check, only when none of that cohort is out; a read already out, started by the app-wide Standing loop, a screen's loop or an earlier ask, serves the check and is not a child of it, so `pause()` does not cancel it. It is bounded instead: a roster read is given up after 60 s (`ROSTER_READ_MILLIS`), and a read set aside with the check that owned it lets the next ask read again. Tests: `HoldoutViewModelRosterTest` (`given a cohort's roster read still out, when it is asked for again, then there is still one chain read`; `given a read that was set aside with the check that owned it, when it is asked for again, then it reads again`; `given a read that never answers, when a minute has passed, then it is given up and the next ask reads`). Spec FR-003 and research R2 say so. *Superseded:* the check's roster read is forced (`force = true`, commit `b2f62ea`, 12 October evening), starting its own even when one is out, and the check's snapshot read is its own, not joined — only the 30-second pass joins a read out (commit `a995bcf`, 001 FR-071 / T377, PR #71); spec FR-003 and Session 2026-10-13 say so.
  - Tests: new `kotlin/app/src/test/kotlin/holdout/app/MainViewModelTest.kt` (5 tests: the earlier read answering last, the earlier read answering first and then replaced, a later read that fails, a lone answer, a failed read) and, in `HoldoutViewModelCheckTest`, a pause-during-check case, a throwing read, a read cancelled inside while the check is live, the A → B → A switch, a check's roster read still out when the app leaves the front (not stored), the check ending without waiting for the roster, and a roster read that throws (the check still ends) — 17 tests there now, not the 10 of T019. Passing with `ChainCheckTest`.
