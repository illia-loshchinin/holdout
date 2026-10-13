# Implementation Plan: Check the Chain Again

**Branch**: `005-refresh-when-unreachable` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-refresh-when-unreachable/spec.md`

## Summary

While the app is in its *"the chain can't be reached"* state (`HoldoutViewModel.UiState.offline`: two snapshot reads in a row failed, or the first with no view yet — 001 FR-071, since 2026-10-13; before, the last one), a member can ask it to read the chain now: a **Check again** button on every "can't be reached" line, and a pull-down on Today (the home), a Holdout's details (also Standing's root) and the forfeit screen; the Holdouts tab's root carries the line and the button, and Standing's empty state neither. A check is the read the app already makes every 30 seconds — the snapshot (`ChainRepository.load`), chain time, and the roster of the Holdout shown — started at once, or served by that roster's read already out (one read per Holdout at a time, research R2). A small plain-Kotlin gate, `ChainCheck`, holds whether a check is under way and whether the last one found the chain still silent; the view model joins a second request to the first, and a successful read, by a check or by the schedule, clears it. A check sends nothing of its own: once it finds the chain answering, 001 FR-024b's automatic send opens the wallet's approval for a waiting day exactly as when the chain comes back by itself, and the tap follows a decline (spec Clarifications, 2026-10-11).

## Technical Context

**Language/Version**: Kotlin 2.x, Android (minSdk 26), Jetpack Compose (BOM 2026.08.00, Material 3)

**Primary Dependencies**: existing only — `androidx.compose.material3` (its `pullToRefresh` modifier and `PullToRefreshDefaults.Indicator`), `kotlinx.coroutines`, the app's `Rpc`/`ChainRepository`. Since T019, a test-only `kotlinx-coroutines-test` at 1.9.0, the coroutines version the app resolves.

**Storage**: none; a check is not kept on the phone

**Testing**: JUnit 4 + `kotlin.test` unit tests in `kotlin/app/src/test`, as `AutoSubmitTest` and `OfflineNoteTest`. They cover the gate, the copy, and the view model's wiring of a check (T019), through a fake `ChainReads` and `kotlinx-coroutines-test`.

**Target Platform**: Android, Solana Seeker; the member app (`member` flavour) and Holdout Lab share the code but Lab shows no chain screens

**Project Type**: mobile app

**Performance Goals**: the checking state shows within 0.5 s of the gesture (SC-002); the answer is one RPC round trip

**Constraints**: reads only while in front (FR-008); no send of its own (FR-004); copy per the design book's voice and 001 FR-024b's wording rule

**Scale/Scope**: one gate class, one view-model entry point, one shared UI component, five screens touched (`CohortHome`, the no-Holdout home's `below`, `CohortDetailScreen`, `ForfeitScreen`, and — since the four tabs — the Holdouts tab's root), wired through `shell/TodayHost.kt`, `StandingHost.kt`, `HoldoutsHost.kt` and `Shared.checkChain`, which `HoldoutApp` builds

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Check | Result |
|---|---|---|
| I, II, III, IV, V, VIII | No program, fund or automation change | Pass (not touched) |
| VI One Clock | The check reads chain time through the same `refreshChainTime(clock)`; no device clock enters | Pass |
| VII Tests Precede Logic | `ChainCheckTest` and the copy tests are written failing before `ChainCheck` and the strings | Pass (tasks order it) |
| IX Every State Has a Screen | The new states — checking, still unreachable — each have their line; details and forfeit gain the unreachable line they lacked | Pass |
| X Camera frames | Not touched | Pass |
| XI Weaknesses documented | No new weakness; the README's weaknesses table is not touched | Pass |
| XII The build demonstrates itself | Not touched | Pass |
| XIII Honest Language | No prize, stake or payout wording is added. The "never offline" rule comes from the design book and 001 FR-024b, not XIII: copy names the network (`Deployment.networkName`) and never says "offline", "internet" or "error" (FR-007, SC-004, tested) | Pass |

Post-design re-check: unchanged, all pass. No complexity to justify.

## Project Structure

### Documentation (this feature)

```text
specs/005-refresh-when-unreachable/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── check-again-ui.md
└── tasks.md            # /speckit-tasks
```

### Source Code (repository root)

```text
kotlin/app/src/main/kotlin/holdout/app/
├── MainViewModel.kt             # changed: refreshChainTime applies an answer only if its read started after the newest one already applied (overlapping reads cannot rewind ChainClock)
├── chain/
│   └── ChainRepository.kt       # ChainReads interface, implemented by ChainRepository (T019)
├── wallet/
│   └── WalletRepository.kt      # the wallet adapter is made on first use, so the view model builds on the JVM (T019)
├── holdout/
│   ├── ChainCheck.kt            # new: the gate (idle / checking / still unreachable)
│   └── HoldoutViewModel.kt      # checkNow() keeps its job, pause() cancels it and clears the check; loadRoster(cohort, scope) lets a check own its roster read; UiState.check; a successful read clears it; takes ChainReads (T019)
├── ui/
│   └── Components.kt            # new: CheckAgain(checking, stillUnreachable, onCheck); ChainUnreachable(...) — the line + CheckAgain for details and forfeit; ChainCheckBox (pull, enabled only while unreachable)
├── home/
│   └── CohortHome.kt            # checking/stillUnreachable/onCheck params; waiting day and pre-sitting notice carry CheckAgain; a done day gets ChainUnreachable; pull
├── cohort/
│   ├── CohortDetailScreen.kt    # offline/checking/stillUnreachable/onCheck params; ChainUnreachable + pull
│   └── ForfeitScreen.kt         # the same
├── HoldoutApp.kt                # readChainTime() shared with the chain-time loop; checkChain(roster) is built here and handed to the hosts through Shared; the app-wide Standing roster loop (30 s running, 10 min finished)
└── shell/                       # since the four-tab navigation (001 FR-069) the wiring lives in the hosts, not HoldoutApp.kt alone
    ├── Shared.kt                # carries checkChain; ROSTER_REFRESH_MILLIS (30 s) and CHAIN_REFRESH_MILLIS (10 min)
    ├── TodayHost.kt             # Today: the running Holdout's home (CohortHome) and the no-Holdout home (CheckAgain under its label, ChainUnreachable over an earlier read), each with the pull
    ├── StandingHost.kt          # DetailHost (a Holdout's details, also Standing's root): ChainUnreachable + pull; RosterLoop / rosterReadByApp; the empty state has no check
    └── HoldoutsHost.kt          # HoldoutsRoot: ChainUnreachable (line + button, no pull); ForfeitHost: ChainUnreachable + pull

kotlin/app/src/test/kotlin/holdout/app/
├── ChainCheckTest.kt            # new
├── CheckAgainCopyTest.kt        # new: the lines name the network, no banned words
├── MainViewModelTest.kt         # new: chain-time reads overlap, the newest answer stands
└── HoldoutViewModelCheckTest.kt # new (T019): checkNow's wiring, with a fake chain
```

**Structure Decision**: the existing single Android app module; the gate is plain Kotlin beside `AutoSubmit` so it is tested on the JVM, and the UI goes through the `holdout-ui` skill's components and tokens.

## Complexity Tracking

None.
