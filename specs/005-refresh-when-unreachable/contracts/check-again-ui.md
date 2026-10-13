# UI contract: Check again

Network name `N` = `Deployment.networkName` ("Devnet" on the devnet build).

## Where it shows (only while the last chain read failed)

| Screen | Line already there / added | Check again button | Pull |
|---|---|---|---|
| Home, running Holdout, day waiting | *"Done — waiting for network"* + its body (existing) | yes, under the body | yes |
| Home, running Holdout, day not done | *"N can't be reached right now. You can still do today's reps…"* notice (existing) | yes, under the notice | yes |
| Home, no Holdout shown (no read yet) | *"N unreachable — the Daily 25 still works"* (existing) | yes, under the label | yes |
| Home, no Holdout running, an earlier read shown | *"N can't be reached right now."* (added, above the Holdout cards) | yes | yes |
| Home, running Holdout, today done | *"N can't be reached right now."* (added, above *"Today is done and on chain."*) | yes | yes |
| A Holdout's details — also the Standing tab's root (`shell/StandingHost.kt`) | *"N can't be reached right now."* (added, under the title) | yes | yes |
| Forfeit (`shell/HoldoutsHost.kt`) | *"N can't be reached right now."* (added) | yes | yes |
| Holdouts tab root, My Holdouts (`shell/HoldoutsHost.kt`) *(as built, PR #62 review)* | *"N can't be reached right now."* (above the lists) | yes | no |
| Standing tab, empty state | — | none | no |

The home rows above are Today (`shell/TodayHost.kt`). The empty state of Standing has no chain figure of its own to refresh, so it carries neither the line nor the button.

What a check reads on the screens that have a roster (the home of a running Holdout, a Holdout's details, the forfeit screen): the snapshot, chain time and that Holdout's roster at once, each read the check's own: a snapshot read already out is not joined, and the roster read is forced even when one of that roster is already out, since that one may be hanging (spec Session 2026-10-13). The 30-second pass joins the check's snapshot read rather than starting another (001 FR-071). A roster read started elsewhere is not cancelled by leaving the front; it is bounded by 60 s.

While the chain can't be reached, the pull is also a *Check again* accessibility action on the screen, so TalkBack and switch users can run the check without the gesture (on the screens that have the pull).

While the chain answers: none of the above. A pull shows no indicator and starts no read, and there is no accessibility action.

## States of the control

| State | Button | Line under it |
|---|---|---|
| idle | *Check again* (secondary button) | — |
| checking | *Checking N…*, busy, not tappable | — |
| still unreachable | *Check again* | *"N still can't be reached."*, warning colour, centred. On the screens with an added line, that line itself turns into *"N still can't be reached."* and nothing is added under the button, so the screen does not move. |
| chain answered | gone | gone; the screen shows what it shows online |

## Never

- The words "offline", "internet", "error" or "connection error" in any of these strings.
- A submit or any write by the check itself. The only wallet that may open after a check is 001 FR-024b's automatic send for a waiting day, the same one that opens when the chain answers by itself.
