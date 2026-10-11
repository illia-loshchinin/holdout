# Research: Check the Chain Again

## R1 — What "the chain can't be reached" is

- **Decision**: the check is offered exactly while `HoldoutViewModel.UiState.offline` is true — the last snapshot read (`ChainRepository.load`) returned nothing.
- **Rationale**: it is the state every existing "can't be reached" line already keys on (`CohortHome`, the no-Holdout home's *"Devnet unreachable — the Daily 25 still works"*, `sittingNote`), so the button appears and goes with the words it sits under. A dropped `adb reverse` or a dead RPC both set it; the phone's own network state is not consulted (spec edge case: never "You're offline").
- **Alternatives**: `ConnectivityManager` state — rejected, it is the phone's network, not the chain, and missed the owner's case entirely.

## R2 — What a check reads

- **Decision**: the snapshot (`refreshNow()` in the view model), chain time (the body of the app's chain-time loop, factored into one suspend function so the check and the loop save the reading the same way), and the roster of the Holdout on screen (`loadRoster`). Started together. The check ends when the snapshot and chain time have answered. The roster load is fire-and-forget and is not awaited, since only the snapshot decides `offline`. A side read that throws (chain time, the roster) is caught inside its own child and cannot cancel the check. A snapshot read that throws is caught too, and `finished(answered = !offline)` runs straight after the `try`/`catch`, so `checking` always clears (the stand-in review, 5959873). A cancellation of the check's own job skips `finished`: the job is kept in the view model, and `pause()` cancels it and resets `check` to idle, so nothing it started goes on in the background (FR-008; the Codex review of PR #57, PR #61); `watch()` of another wallet cancels it too, so it cannot finish into a later state of the first wallet. A cancellation raised inside a read while the job is still live is a read that did not answer, and the check ends through `finished` (the stand-in review of PR #61). Chain time is saved the same way for the loop and the check, and is applied newest-wins (R3). The roster read is fire-and-forget but a child of the check's job (`loadRoster(cohort, scope)` is given the check's scope), so leaving the front cancels it. A scheduled `loadRoster` (the 30-second pass, a screen opening) is outside a check and unchanged. A new check also cancels the previous check's job first, so nothing it left running outlives it.
- **Rationale**: these are the reads the screens are drawn from; the snapshot alone decides `offline`. Chain time matters when a start had no reading (the screens can't show a countdown without it).
- **Alternatives**: `clockViewModel.retry.networkBack()` only — rejected, it ends the wait only when there is no reading yet; with one, chain time would wait up to 10 minutes.

## R3 — Joining, and clearing

- **Decision**: a plain-Kotlin gate `ChainCheck` (data-model) with `request(offline)` → whether to start a read, `finished(answered)` and `answered()` for any successful read. A request while one is under way, or while the chain answers, starts nothing.
- **Rationale**: one rule, unit-testable on the JVM like `AutoSubmit`; the view model's overlapping-read guard (`newestApplied`) keeps the newest snapshot, so a check racing the 30-second read needs nothing more there. Chain time has a guard of its own: `MainViewModel.refreshChainTime` applies an answer only if its read started after the newest read whose answer was already applied (newest wins): an earlier read that lands first is applied and then replaced by the later one, and one that lands last is set aside, so `ChainClock` cannot be rewound (PR #61). Comparing slots inside `ChainClock.observe` was rejected: slots start over after a local validator is reset, and a lower slot would then be refused for good.
- **Alternatives**: a `Job` check in the view model alone, rejected because it hides the rule from tests. The view model's own wiring is tested too, through the `ChainReads` seam (T019, `HoldoutViewModelCheckTest`).

## R4 — The pull gesture

- **Decision**: Material 3's `Modifier.pullToRefresh(isRefreshing, state, enabled, onRefresh)` with `PullToRefreshDefaults.Indicator`, wrapped once in `ui/Components.kt` as `ChainCheckBox`, `enabled = offline`. The indicator uses the design tokens (`Surface2`, `Accent`).
- **Rationale**: the modifier has an `enabled` flag, which the owner's answer needs (no pull while the chain answers); the screens already scroll with `verticalScroll`, which nests under it.
- **Alternatives**: `PullToRefreshBox` — no `enabled` in this BOM's signature; a hand-made drag detector — more code, worse feel.

## R5 — A check sends nothing of its own

- **Decision**: the check does not call `submitDay` or touch `AutoSubmit`. When its read answers, `refreshNow()` applies it as any read, and `SendWaitingDay` / `CohortHome` decide what the waiting day shows, as today.
- **Rationale**: FR-004; 001 FR-024b is unchanged. `AutoSubmit.step` resets its gate while `offline`, so the first answer after an unreachable spell — by a check or by the schedule — makes the Phase 2 automatic send (T098) due: the wallet's approval opens by itself, and *"Done — tap to submit"* follows a decline. The owner chose to keep that (spec Clarifications, 2026-10-11).

## R6 — Copy

- **Decision**: *"Check again"* (button), *"Checking Devnet…"* (while under way), *"Devnet still can't be reached."* (after a failed check), *"Devnet can't be reached right now."* (details and forfeit). Network named from `Deployment.networkName`.
- **Rationale**: owner's answers (spec Clarifications 2026-10-11); design book voice: short, says what happened and what to do; never "offline".
