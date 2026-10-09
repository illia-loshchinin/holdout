# Research: Holdout Lab and the Member App

**Feature**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Date**: 2026-10-09

Each decision rests on how the code is today, read at `004-lab-and-member-apps` (base `c442c23` plus documents). Paths are under `kotlin/app/` unless they start at the repository root.

---

## R1 · Two apps from one codebase: product flavors, not a second module

**Decision**: Add one flavor dimension, `app`, to the existing `:app` module, with two flavors:

| Flavor | `applicationId` | Launcher name | Ships to |
|---|---|---|---|
| `member` | `holdout.app` (unchanged) | Holdout | Members; the submitted APK is `memberRelease` |
| `lab` | `holdout.app.lab` (`applicationIdSuffix = ".lab"`) | Holdout Lab | Team and consenting testers, by direct install |

Build types stay `debug` and `release`, which gives four variants: `memberDebug`, `memberRelease`, `labDebug` and `labRelease`. Shared code stays in `src/main`. Lab-only code goes in `src/lab`, and the member's stand-ins in `src/member`.

**Rationale**:
- **One counting source.** It is the same module and the same `src/main`, so `:repcounter`, the vision analyser and the sitting screen are compiled once per variant from one revision. Neither app can carry a different version of the rules (FR-011), and no copying or versioning is needed.
- **A seam that already exists.** Today the recorder is split as `src/debug/…/SittingVideoFactory.kt` (real) against `src/release/…/SittingVideoFactory.kt` (null). The split becomes `src/lab` against `src/member`.
- **Side by side.** A different `applicationId` gives separate installs and separate private storage (MMKV, `files/traces`), so the two apps share nothing (SC-005).
- **Smallest change** before the 13 October deadline: no Gradle module moves, and no change for `:core`, `:repcounter` or `:crank`.

**Alternatives considered**:
- **A separate `:lab` application module** depending on a new `:sitting` library extracted from `:app`. It's cleaner long-term, but it means moving camera, vision, sitting, banking and theme code into a library, about a day of work at risk on the deadline. It stays possible later: `src/lab` can become `:lab` once the dataset and classifier work (002 T036–T039) grows it.
- **Keep `BuildConfig.DEBUG` gating.** This is the current state. It fails FR-010, which bans tester features from the member app's developer builds too.
- **A runtime "tester mode" switch** in one APK: the member APK would still contain the recorder. That breaks constitution X v2.1.0.

## R2 · The seams between shared code and Lab-only code

**Decision**: Shared code depends on three small interfaces in `src/main`. Each flavor supplies its own implementations; `member` supplies none, or null.

| Seam (in `src/main`) | Used by | `lab` provides | `member` provides |
|---|---|---|---|
| `SittingRecorder`: `record(frame…)`, `close()`, `label(…)`, `fileName` | `SittingViewModel` (today it builds `TraceRecorder` itself, line 97) | `TraceRecorder` (moved from `src/main` to `src/lab`) | nothing; the view model gets `null` |
| `SittingVideoFactory` (exists): `create(context, traceDir): SittingVideo?` | `SittingScreen` | the CameraX recorder (moved from `src/debug` to `src/lab`) | returns `null` (moved from `src/release` to `src/member`) |
| `LabSittingTools`: a slot for the label prompt after a set, the readouts overlay and clean screen | `SittingScreen` | `TraceLabelDialog` (moved to `src/lab`) and the readouts | absent; `SittingScreen`'s defaults draw nothing extra |

Every `BuildConfig.DEBUG` check that gates a tester feature is removed:
- `HoldoutApp.kt` lines 249–252, 374, 413 and 698–706;
- `SittingScreen.kt` lines 214, 271 and 492.

The member app's Home loses the four tester switches (`HomeScreen.kt`: `stakedTest`, `cleanScreen`, `recordVideo`, `onResetToday`). `TestSwitch.kt` moves to `src/lab`.

**Rationale**: Code in `src/lab` cannot be referenced from `src/main` or `src/member`, because the member variants would not compile. So FR-010 holds structurally, before any check runs.

**Alternatives considered**: injecting lambdas everywhere. It means more call-site churn, and the interfaces are easier to review.

## R3 · What Holdout Lab is on screen

**Decision**: `src/lab` has its own entry composable, `LabApp`. `MainActivity` (shared) calls a per-flavor `AppRoot(sender, modifier)`: `member` calls `HoldoutApp`, `lab` calls `LabApp`, which ignores `sender` and never builds a wallet or a chain client. The flow:

1. **Choose a mode**: three rows, Daily 25 (`Stakes.DAILY`), Easy Holdout (`Stakes.EASY`), Hard Holdout (`Stakes.STAKED`). Below them sit the switches:
   - *Record video* (off by default, remembered, with the consent wording of 002 FR-026);
   - *Clean screen*;
   - the target reps (default 25).
2. **The set**: the shared `SittingScreen`, with `SittingViewModel` built as follows:
   - `store`: Lab's own store (MMKV in the Lab app's sandbox);
   - `wallet = "lab"`;
   - `scope = "lab:<mode>:<setStartMillis>"`: a fresh scope per set, so banking never blocks a new set ("start a fresh set at any time", FR-007);
   - `chainSecondsNow = { null }` and `rollsOver = false`: no chain, as an offline Daily 25 already runs;
   - `traceDir = files/traces` and the `SittingRecorder` from R2;
   - the set-up guide and placement checks for that mode, unchanged.
3. **At the end**: the label prompt (002 FR-025), then back to step 1.

**Rationale**: Every piece already exists and runs without chain time, because the Daily 25 works offline. Lab adds a chooser screen, about 150 lines.

**Alternatives considered**: reusing `HoldoutApp` with the wallet step skipped. It would drag chain, faucet and stake code into Lab and keep the two flows tangled.

## R4 · The build check (FR-010, constitution X v2.1.0)

**Decision**:
- Rename `CheckReleaseHasNoRecorder` (`app/build.gradle.kts` lines 239–257) to **`CheckMemberHasNoLabCode`**. It scans the sources the member variants compile: `src/main`, `src/member`, `src/debug`, `src/release`, `src/memberDebug` and `src/memberRelease`, in `kotlin` and `java`.
- It fails on any of: `androidx.camera.video`, `TraceRecorder`, `TraceLabelDialog`, `TestSwitch`, `LabApp`.
- It runs before every `assembleMember*` and `bundleMember*` task, debug and release alike.
- `debugImplementation(libs.androidx.camera.video)` becomes `labImplementation(...)`.

**Rationale**: R2 already makes such references fail to compile. The scan is the constitution's required build check and a backstop for the one library the member app can still see transitively (`camera-video`, through `camera-view`).

## R5 · Judge-only parts, and what stays as a plain About

**Decision**:
- **Delete:**
  - `WalkthroughScreen` and `Route.Walkthrough`;
  - the `beforeWallet` walkthrough branch in `HoldoutApp.kt` (lines 264 and 298);
  - `ConnectScreen`'s `onWalkthrough` parameter and its "For judges: watch a full cycle" or "Watch a full cycle (3 min)" link (lines 58 and 113–114);
  - the About screen's "Watch a full cycle" button, its known-weaknesses list and its scripted-wallet list.
- **Keep, renamed:** `AboutScreen` becomes a plain **About Holdout** for every member. It keeps:
  - the devnet notice;
  - the tour again;
  - Disconnect wallet, with its network note;
  - one sentence on scripted members.

  Both links to it ("About · for judges" in `HoldoutApp.kt` line 743, "About · wallet" in `CohortHome.kt` line 176) read **"About Holdout"**.
- **Comments** that describe the walkthrough or judges (`TourScreen.kt` line 30, `ConnectScreen.kt` lines 42–46) are rewritten.

**Rationale**:
- The About screen today is also the only place a member inside a Holdout can disconnect or replay the tour. Deleting it would strand those actions.
- FR-012 removes only what is addressed to judges.
- The scripted disclosure that constitution XII requires is on first run and on every scripted row (001 FR-064), so the 56-wallet list isn't needed in the app. It stays published in the repository.

**Alternatives considered**: moving Disconnect to Home now. That's part of the later navigation redesign (the Me tab), not this feature.

## R6 · Where the known weaknesses live

**Decision**:
- Move `app/src/main/assets/weaknesses.json` to **`docs/weaknesses.json`**.
- Point `scripts/render-weaknesses.mjs` at the new path.
- The app no longer reads it (FR-015). The README table is unchanged.

**Rationale**: It is a README source now, not an app asset, and keeping it in `assets` would ship an unused file in the APK. Spec 003's weaknesses entry (contracts §4) is added at the new path.

## R7 · Pulling Lab sets

**Decision**:
- `scripts/pull-traces.sh` reads from **`holdout.app.lab`** by default (`run-as holdout.app.lab`, same `files/traces` path and file names).
- A `--package holdout.app` option drains traces and videos left in the member app by debug builds made before the split. It is run once per tester phone before the member app is updated.
- `scripts/label-reps.mjs` reads the pulled files, not the phone, so it needs no change.

**Rationale**: FR-008 asks that the sets and the tools stay as they are, with only the location changing.

## R8 · Lab's name and icon

**Decision**:
- **Name:** `src/lab/res/values/strings.xml` sets `app_name` to **"Holdout Lab"**.
- **Icon:** `src/lab/res/` holds an adaptive icon built from the design book's **status-bar glyph**: the 3×3 crowd with the larger Holdout in the middle, drawn in `accent` (`#80ECFF`) on `canvas` black. A phone screen can tell it from Holdout's 5×5 white-and-green crowd at a glance (FR-001).

**Rationale**: It stays on black and uses no second green dot, so the brand rules for Holdout's own icon are untouched. Lab is never shown to members, so it needs no design-system entry. The design book's rule "never recolour the members" governs Holdout's own icon.

## R9 · What the member app's developer build may still do

**Decision**: Member developer builds keep the two conveniences that add no screen and no tester feature:
- plain HTTP to a local validator (`src/debug/AndroidManifest.xml`, `network_security_config.xml`);
- sign-only sending against a non-devnet RPC (`WalletRepository.kt` line 152).

They gain nothing else.

**Rationale**: FR-010 lists the banned features: recorder, traces, video, labelling, tester switches and readouts. These two are developer plumbing for 001 quickstart §5a and change nothing a member sees.

## R10 · Testing

**Decision**:
- **JVM unit tests**, in `src/test` and `src/testLab`:
  - the Lab mode mapping, Daily 25 / Easy / Hard to `Stakes.DAILY` / `EASY` / `STAKED`;
  - Lab's per-set scope is unique for each new set;
  - `SittingViewModel` with a fake `SittingRecorder` records, labels and closes, as today's trace behaviour;
  - `SittingViewModel` with `null` writes nothing.
- **The build check, proven both ways:**
  - `./gradlew :app:checkMemberHasNoLabCode` passes on the tree;
  - a scratch file importing `androidx.camera.video` in `src/main` makes `assembleMemberDebug` fail (quickstart). It is reverted afterwards.
- **SC-003:** `repcounter`'s `RecordedTracesTest` already replays every committed labelled set through the counting rules both apps share. One source means one result, so SC-003 is met by that suite passing. No second replay is needed.
- **On a phone:**
  - both apps installed side by side;
  - a Lab set in each mode, pulled with `pull-traces.sh` and opened in the labelling page;
  - a walk through the member release build for judge text (quickstart).
