# Data Model: Check the Chain Again

Nothing is stored on the phone or on chain. One in-memory value, held in `HoldoutViewModel.UiState`.

## ChainCheck

| Field | Type | Meaning |
|---|---|---|
| `checking` | Boolean | A check's read is out. |
| `stillUnreachable` | Boolean | The last check ended and the chain did not answer. |

### Transitions

| From | Event | To | Read started |
|---|---|---|---|
| any, chain answering (`offline == false`) | `request` | unchanged | no |
| `checking` | `request` | `checking` | no (joins) |
| idle or `stillUnreachable`, `offline == true` | `request` | `checking`, `stillUnreachable` kept | yes |
| `checking` | `finished(answered = false)` | `stillUnreachable` | — |
| `checking` | `finished(answered = true)` | idle | — |
| any | `answered()` (any successful read) | idle | — |
| any | the app leaves the front (`pause()`) | idle; a running check's job is cancelled | — |
| any | wallet changes (`watch()` of another wallet) | idle (`UiState` reset); the old wallet's check job is cancelled | — |

### Rules

- A check exists only for the wallet being watched; a wallet change resets `UiState`, and with it the check.
- `stillUnreachable` is shown only while `offline` is true, and not while `checking`: the state keeps it through the next check (so a check that fails again does not change it), but the line is hidden while that check is out (contracts/check-again-ui.md).
- While `checking`, the button shows its busy state and the pull its indicator.
- A check ends with `finished(answered = !offline)`, read after its own read. If its answer was set aside because a newer read is still out (the newest answer wins), it can end as `stillUnreachable`. The newer read's answer then clears it through `answered()`. This is brief and corrects itself.
- A check always ends. A read that throws (a side read such as chain time or the roster, or the snapshot) is a read that did not answer: it is caught and `finished` still runs, straight after it. A cancellation raised inside a read while the check's own job is still live (an engine cancelling its call) is treated the same way: a read that did not answer, so the check ends through `finished`. Only a cancellation of the check's own job skips `finished`: `pause()` (FR-008) and a wallet change (`watch()` of another wallet) cancel it and clear the state themselves, so an old check cannot finish into a later state (A → B → A). It never stays `checking`.
- `pause()` also drops `stillUnreachable`: after the app goes to the background and comes back, the line reads "can't be reached right now" again until the next check.
