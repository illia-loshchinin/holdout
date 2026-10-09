# Quickstart: Holdout Lab and the Member App

**Feature**: [spec.md](spec.md) · **Contract**: [contracts/variants-and-seams.md](contracts/variants-and-seams.md)

Proves the split works. Toolchain and signing are as in `specs/001-holdout-hackathon/quickstart.md`. Gradle commands run from `kotlin/`.

## 1. Before updating testers' phones

Drain the traces and videos the old debug app recorded. They live in `holdout.app`, which won't write any more after this feature:

```bash
bash scripts/pull-traces.sh --package holdout.app
```

**Expect**: every file is copied, size-checked and deleted from the phone, as today.

## 2. Tests and the build check

```bash
./gradlew :app:testMemberDebugUnitTest :app:testLabDebugUnitTest :repcounter:test
```

```bash
./gradlew :app:checkMemberHasNoLabCode :app:ktlintCheck
```

**Expect**: all pass. `:repcounter:test` replays every committed labelled set through the one shared set of counting rules (SC-003).

**Prove the check fails**: add a line `import androidx.camera.video.Recorder` to any file under `app/src/main/kotlin`, then run:

```bash
./gradlew :app:assembleMemberDebug
```

**Expect**: the build fails with the contracts §4 message (SC-002). Remove the line again.

## 3. Build both apps and install them side by side

```bash
./gradlew :app:assembleMemberRelease :app:assembleLabDebug
```

The release build is unsigned until the submission signing key is set up (001 quickstart). For this check, install `memberDebug`, which has the same screens and no tester code:

```bash
adb install -r app/build/outputs/apk/member/debug/app-member-debug.apk
```

```bash
adb install -r app/build/outputs/apk/lab/debug/app-lab-debug.apk
```

**Expect**: two icons, "Holdout" and "Holdout Lab", told apart at a glance (FR-001). Testers always get `labDebug`; `labRelease` is never installed on a tester's phone, because its sets could not be pulled (data-model §1).

## 4. Holdout Lab on a phone (about 10 minutes)

| # | Do | Expect | Covers |
|---|---|---|---|
| 1 | With no wallet app installed (or ignoring it), open Holdout Lab | The mode chooser within seconds; nothing asks for a wallet or network. Under 60 s from launch to counting | US1-1, FR-002, SC-001 |
| 2 | Choose Easy Holdout, set up, do 5 push-ups, Stop | The same set-up guide and checks as an Easy sitting in Holdout; readouts on screen; then the label dialog | US1-2, US1-3 |
| 3 | Turn on Record video, do a Daily 25 set of 5, label it | A video and its `.video.json` beside the trace | US1-4, FR-005 |
| 4 | Do a Hard Holdout set with Clean screen on | Nothing drawn over the camera | FR-007 |
| 5 | Pull: run the default pull, with no `--package`, so it pulls from `holdout.app.lab` | All sets pulled, size-checked, deleted from the phone | FR-008, US1-5 |
| 6 | Open them in the labelling page (`node scripts/label-reps.mjs`) | They open like any earlier set | SC-006 |

```bash
bash scripts/pull-traces.sh
```

## 5. The member app (`memberDebug`, or `memberRelease` once signed), about 10 minutes

| # | Do | Expect | Covers |
|---|---|---|---|
| 1 | Fresh install; on the connect screen | Connect wallet, plus no-wallet help when no wallet app; no video, no judge link | US3-1, FR-012 |
| 2 | Connect; walk every screen | No text addressed to judges; "Test network, test token." and the scripted-member note in the tour; SCRIPTED and TEST COHORT labels where they apply | US3-2, US3-3, FR-013, SC-004 |
| 3 | Open About Holdout | Devnet notice, the tour again, Disconnect wallet; no weaknesses list, no walkthrough | FR-012, FR-015 |
| 4 | Find a short test Holdout on Today | Offered like any Holdout, labelled with its day length, no mention of judges | US3-4, FR-014 |
| 5 | Do a Daily 25 sitting and Stop | No label dialog, no readouts, no "rec ·" line | US2-2, FR-010 |
| 6 | Do 10 Lab sets, then reopen Holdout | Banked reps, streak and settings unchanged | SC-005 |

## 6. README

```bash
node scripts/render-weaknesses.mjs
```

**Expect**: the README's weaknesses table is unchanged apart from rows added on purpose. The script reads `docs/weaknesses.json` (FR-015).
