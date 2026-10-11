# UI contract: Check again

Network name `N` = `Deployment.networkName` ("Devnet" on the devnet build).

## Where it shows (only while the last chain read failed)

| Screen | Line already there / added | Check again button | Pull |
|---|---|---|---|
| Home, running Holdout, day waiting | *"Done — waiting for network"* + its body (existing) | yes, under the body | yes |
| Home, running Holdout, day not done | *"N can't be reached right now. You can still do today's reps…"* notice (existing) | yes, under the notice | yes |
| Home, no Holdout shown | *"N unreachable — the Daily 25 still works"* (existing) | yes, under the label | yes |
| A Holdout's details | *"N can't be reached right now."* (added, under the title) | yes | yes |
| Forfeit | *"N can't be reached right now."* (added) | yes | yes |

While the chain answers: none of the above; a pull shows no indicator and starts no read.

## States of the control

| State | Button | Line under it |
|---|---|---|
| idle | *Check again* (secondary button) | — |
| checking | *Checking N…*, busy, not tappable | — |
| still unreachable | *Check again* | *"N still can't be reached."* |
| chain answered | gone | gone; the screen shows what it shows online |

## Never

- The words "offline", "internet", "error" or "connection error" in any of these strings.
- A submit, a wallet opening, or any write from a check.
