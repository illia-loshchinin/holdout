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

### Rules

- A check exists only for the wallet being watched; a wallet change resets `UiState`, and with it the check.
- `stillUnreachable` is shown only while `offline` is true, and not while `checking`: the state keeps it through the next check (so a check that fails again does not change it), but the line is hidden while that check is out (contracts/check-again-ui.md).
- While `checking`, the button shows its busy state and the pull its indicator.
