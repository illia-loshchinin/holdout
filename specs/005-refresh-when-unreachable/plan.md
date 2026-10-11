# Implementation Plan: Check the Chain Again

**Branch**: `005-refresh-when-unreachable` | **Date**: 2026-10-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/005-refresh-when-unreachable/spec.md`

## Summary

While the app is in its *"the chain can't be reached"* state (`HoldoutViewModel.UiState.offline`, the last snapshot read failed), a member can ask it to read the chain now: a **Check again** button on every "can't be reached" line, and a pull-down on the home, a Holdout's details and the forfeit screen. A check is the read the app already makes every 30 seconds — the snapshot (`ChainRepository.load`), chain time, and the roster of the Holdout shown — started at once. A small plain-Kotlin gate, `ChainCheck`, holds whether a check is under way and whether the last one found the chain still silent; the view model joins a second request to the first, and a successful read, by a check or by the schedule, clears it. A check sends nothing of its own: once it finds the chain answering, 001 FR-024b's automatic send opens the wallet's approval for a waiting day exactly as when the chain comes back by itself, and the tap follows a decline (spec Clarifications, 2026-10-11).

## Technical Context

**Language/Version**: Kotlin 2.x, Android (minSdk 26), Jetpack Compose (BOM 2026.08.00, Material 3)

**Primary Dependencies**: existing only — `androidx.compose.material3` (its `pullToRefresh` modifier and `PullToRefreshDefaults.Indicator`), `kotlinx.coroutines`, the app's `Rpc`/`ChainRepository`

**Storage**: none; a check is not kept on the phone

**Testing**: JUnit 4 + `kotlin.test` unit tests in `kotlin/app/src/test` (the gate and the copy), as `AutoSubmitTest` and `OfflineNoteTest`

**Target Platform**: Android, Solana Seeker; the member app (`member` flavour) and Holdout Lab share the code but Lab shows no chain screens

**Project Type**: mobile app

**Performance Goals**: the checking state shows within 0.5 s of the gesture (SC-002); the answer is one RPC round trip

**Constraints**: reads only while in front (FR-008); no send of its own (FR-004); copy per the design book's voice and 001 FR-024b's wording rule

**Scale/Scope**: one gate class, one view-model entry point, one shared UI component, five screens touched (`CohortHome`, the no-Holdout home's `below`, `CohortDetailScreen`, `ForfeitScreen`, and the home state wiring in `HoldoutApp`)

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
├── holdout/
│   ├── ChainCheck.kt            # new: the gate (idle / checking / still unreachable)
│   └── HoldoutViewModel.kt      # checkNow(); UiState.check; a successful read clears it
├── ui/
│   └── Components.kt            # new: CheckAgain(checking, stillUnreachable, onCheck); ChainUnreachable(...) — the line + CheckAgain for details and forfeit; ChainCheckBox (pull, enabled only while unreachable)
├── home/
│   └── CohortHome.kt            # checking/stillUnreachable/onCheck params; waiting day and pre-sitting notice carry CheckAgain; a done day gets ChainUnreachable; pull
├── cohort/
│   ├── CohortDetailScreen.kt    # offline/checking/stillUnreachable/onCheck params; ChainUnreachable + pull
│   └── ForfeitScreen.kt         # the same
└── HoldoutApp.kt                # readChainTime() shared with the chain-time loop; checkChain(roster) wires every screen; no-Holdout home: CheckAgain under its label, ChainUnreachable over an earlier read

kotlin/app/src/test/kotlin/holdout/app/
├── ChainCheckTest.kt            # new
└── CheckAgainCopyTest.kt        # new: the lines name the network, no banned words
```

**Structure Decision**: the existing single Android app module; the gate is plain Kotlin beside `AutoSubmit` so it is tested on the JVM, and the UI goes through the `holdout-ui` skill's components and tokens.

## Complexity Tracking

None.
