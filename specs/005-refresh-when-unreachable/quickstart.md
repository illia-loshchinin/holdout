# Quickstart: Check the Chain Again

## Unit tests (Windows)

```bash
cd kotlin && ./gradlew :app:testMemberDebugUnitTest --tests "holdout.app.ChainCheckTest" --tests "holdout.app.CheckAgainCopyTest"
```

Expected: all pass — the gate's transitions (data-model.md) and the copy rules (contracts/check-again-ui.md).

## On a phone against a local validator (the owner's case)

Prerequisites: the member debug APK installed, a local validator in WSL, `adb reverse tcp:8899 tcp:8899`, a wallet in a running test Holdout.

1. Finish today's reps. Remove the reverse: `adb reverse --remove tcp:8899`. Within 30 s the home reads *"Done — waiting for network"* with **Check again**.
2. Tap **Check again**: *"Checking Devnet…"*, then *"Devnet still can't be reached."* Pull down: the indicator shows, same result.
3. Restore the reverse. Tap **Check again**: within 5 s the waiting line gives way to *"Done — tap to submit"* (or the wallet opening, if FR-024b's Phase 2 send is due). Nothing is submitted without the member's approval.
4. With the chain answering, pull down on the home: no indicator.
5. Open a Holdout's details and, in a forfeited Holdout, the forfeit screen, with the reverse removed: each shows *"Devnet can't be reached right now."* with **Check again** and the pull.
