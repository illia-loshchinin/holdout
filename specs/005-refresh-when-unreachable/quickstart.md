# Quickstart: Check the Chain Again

## Unit tests (Windows)

```bash
cd kotlin && ./gradlew :app:testMemberDebugUnitTest --tests "holdout.app.ChainCheckTest" --tests "holdout.app.CheckAgainCopyTest"
```

Expected: all pass — the gate's transitions (data-model.md) and the copy rules (contracts/check-again-ui.md).

## On a phone against a local validator (the owner's case)

Prerequisites: the member debug APK installed, a local validator in WSL, `adb reverse tcp:8899 tcp:8899`, a wallet in a running test Holdout.

If something keeps `adb reverse tcp:8899` alive, such as another session's keep-alive loop, cut a link of your own instead:

```bash
adb reverse tcp:8898 tcp:8899
```

```bash
cd kotlin && ./gradlew :app:installMemberDebug -Pholdout.rpcUrl=http://127.0.0.1:8898 -Pholdout.sponsorKeypair=//wsl.localhost/Ubuntu/home/<you>/.config/solana/holdout/sponsor.json
```

Then use `adb reverse --remove tcp:8898` to cut the link and `adb reverse tcp:8898 tcp:8899` to restore it. Reinstall the 8899 build afterwards. A refused local port answers at once, so *"Checking…"* only flashes. The network is named *"The local validator"* on such a build.

1. Finish today's reps. Remove the reverse: `adb reverse --remove tcp:8899`. Within 30 s the home reads *"Done — waiting for network"* with **Check again**.
2. Tap **Check again**: *"Checking Devnet…"*, then *"Devnet still can't be reached."* Pull down: the indicator shows, same result.
3. Restore the reverse. Tap **Check again**: within 5 s the waiting line goes and the wallet's approval for the day opens by itself (001 FR-024b's automatic send), once. Decline it: the home reads *"Done — tap to submit"*. Nothing is submitted without the member's approval.
4. With the chain answering, pull down on the home: no indicator.
5. Open a Holdout's details and, in a forfeited Holdout, the forfeit screen, with the reverse removed: each shows *"Devnet can't be reached right now."* with **Check again** and the pull.
6. With the reverse removed, look at the home with no Holdout running after an earlier read (above its Holdout cards), and at the home of a running Holdout whose day is done. Each shows *"Devnet can't be reached right now."* with **Check again**. A failed check turns that line into *"Devnet still can't be reached."* in place.
