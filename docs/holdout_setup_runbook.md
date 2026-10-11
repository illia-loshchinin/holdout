# Setup runbook — what only a human can do

The parts of running Holdout that a machine cannot finish on its own: a domain,
devnet SOL, keys that must never be committed, real phones, real people. Each
step says what to run, what "done" looks like, and where the answer gets
written down. `specs/001-holdout-hackathon/quickstart.md` covers everything
that *is* automatic: building, testing and deploying.

Step numbers are stable because code and tests cite them: the sponsor key
(step 3, `kotlin/app/build.gradle.kts`), `scrcpy` (step 5, the `holdout-ui`
skill), the S1 regression test (step 6, `S1JvmSpikeTest`) and the rep-counter
recordings (step 7, `fixtures/rep-traces/README.md`).

---

## 0. Where things stand

| | |
|---|---|
| Done | Toolchain and keypair checks (1, 2); operational keys and scripted wallets (3); `scrcpy` and spike S1 on 22 September (5, 6); spike S2, done differently through spec 002: the counter is held by sittings recorded on a Seeker (7, T012b) |
| Still by hand | Devnet SOL for the faucet vault (3); the domain and its Digital Asset Links file (4); the release keystore (4); more labelled sets for the rep counter, from more people (7, spec 002 T033, T046–T048); testers (8) |
| Not blocked | Everything in the program and the app builds and tests without any of this; the rep counter is held by the fixtures already committed, and step 7 adds evidence to them |

---

## 1. Toolchain check (WSL2) — 10 minutes

Everything Solana runs inside WSL2 Ubuntu 24.04; Android Studio and Gradle run
on Windows. Prefix Anchor and Surfpool with `NO_DNA=1`.

```bash
rustc --version && solana --version && anchor --version && node --version && surfpool --version
```

**Done when** they read ≥ 1.89, 3.1.10, 1.1.2, 22.x and a Surfpool version
installed from `curl -sL https://run.surfpool.run/ | bash` — never
`cargo install surfpool` (research R1, R19).

```bash
NO_DNA=1 anchor build && cargo test -p holdout --lib --features test-leagues
```

**Done when** both pass. They pass in CI, so a failure here is the local
toolchain, not the code.

---

## 2. Program keypair — 2 minutes, before anything touches a network

The deployed address has to stay `Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4`.
Anchor silently generates a *different* key when the file is missing, deploys to
the new address, and then the README, the app and `declare_id!` all name a
program that is not there.

```bash
mkdir -p target/deploy && cp ~/.config/solana/holdout-program-keypair.json target/deploy/holdout-keypair.json && anchor keys list
```

**Done when** it prints `holdout: Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4`.

The keypair lives in `~/.config/solana/` inside WSL and nowhere in the
repository. Back it up off this machine; a lost program key means a new program
address and a new deployment for every member.

---

## 3. Operational keys and devnet SOL (T010)

```bash
bash scripts/devnet-keys.sh
```

Creates `admin`, `operator`, `sponsor` and `treasury` under
`~/.config/solana/holdout/`, never overwriting one that exists, and prints the
four public keys. `admin` must also be the program's upgrade authority —
`initialize` checks that against the ProgramData account and refuses anyone else
(R14). So deploy with `admin`, or hand it the authority before `crank init`.

The app bundles `sponsor`'s secret so a wallet with no SOL can claim the
faucet (research R13, a README known weakness). The build reads it from a
Gradle property and never from the repository — put this in
`%USERPROFILE%\.gradle\gradle.properties` on Windows (not the project's file):

```properties
holdout.sponsorKeypair=\\\\wsl$\\Ubuntu\\home\\<you>\\.config\\solana\\holdout\\sponsor.json
```

Without it the app still builds: a wallet that holds SOL pays for its own
claim, and one with none is told the faucet cannot pay out (ES-0b).

```bash
bash scripts/gen-scripted-wallets.sh
```

Generates the 56 scripted wallets once — 20 for the 7-day league, 36 in three
groups of 12 for the 21-day — and writes their **public keys** to
`kotlin/core/src/main/resources/scripted-wallets.json`. Commit that file: it is
what labels scripted members in the app and the README, which constitution XII
requires. Run it **once**; regenerating it strands whatever you have funded.

```bash
bash scripts/devnet-status.sh
```

What has to be funded, and from which key:

| What | From | How much |
|---|---|---|
| Deploy rent | `admin` | measured at the first deploy |
| Scripted wallets | `operator` | ~1.12 SOL, 0.02 each |
| Sponsor float | `sponsor` | 0.05 SOL |
| Faucet vault | `admin`, at `crank init` | **25 SOL** — it must last the whole judging period, to 11 November, with no top-up (FR-042a) |
| Cohort rent | the crank payer, which is the `operator` key (decided 2026-10-11, L14; its risk is a README weakness) | the test lobby opens a cohort every 30 minutes, 0.0033 SOL each, about **0.16 SOL a day**: about 5 SOL to 11 November, about 8 SOL to 30 November. Nothing returns it on devnet (L14, H24) |

Devnet airdrops are rate-limited and often fail, which is the whole reason the
faucet exists. Collect a bit at a time, from more than one source
(`solana airdrop 2`, the web faucet), well before the deploy.

---

## 4. Domain, Digital Asset Links and the release keystore (T009, T115)

The app claims the identity `https://holdout.app` (`WalletRepository`). Until
that domain serves a Digital Asset Links file naming this package and its
signing certificate, wallets show the member an unverified raw package name
instead of "Holdout".

1. Register `holdout.app`, or pick a subdomain of something you already own.
   **If it is not `holdout.app`, change `identityUri` in
   `kotlin/app/src/main/kotlin/holdout/app/wallet/WalletRepository.kt` to
   match** — the URI and the file have to be on the same host or the check
   fails.
2. Create the release keystore, **outside the repository**, and back it up with
   the program keypair. A lost keystore means a new package identity for every
   member; a leaked one lets anyone ship an update as Holdout. The JDK's
   `keytool` makes it (`*.jks` and `*.keystore` are git-ignored as a backstop,
   but keep it out of the clone anyway):

   ```bash
   keytool -genkeypair -v -storetype PKCS12 -keystore ~/holdout-release.jks -alias holdout \
     -keyalg RSA -keysize 4096 -validity 10000 \
     -dname "CN=Holdout, O=Holdout, C=DE"
   ```

   A PKCS12 keystore has **one** password: `keytool` asks for it once, and the
   key's password is that same value (current JDKs make PKCS12 whatever the
   file is called, and ignore a separate `-keypass`). A backup copy of the file
   is not enough without it, so store it with the backup. Then tell Gradle
   where the keystore is — in `%USERPROFILE%\.gradle\gradle.properties`
   (Windows) or `~/.gradle/gradle.properties`, never in the repository's
   `kotlin/gradle.properties` (the build refuses a signing property there):

   ```properties
   holdout.keystore=C:/Users/<you>/holdout-release.jks
   holdout.keystorePassword=<the keystore password>
   holdout.keyAlias=holdout
   holdout.keyPassword=<the same password>
   ```

   `holdout.keystore` has to be an absolute path with forward slashes — a
   leading `~` is not expanded, backslashes are eaten by a `.properties` file,
   and a path inside the clone is refused. Prefer that file over `-P` flags:
   `-Pholdout.keystorePassword=...` leaves the password in the shell history and
   in the process list while the build runs. (`ORG_GRADLE_PROJECT_holdout.keystore`
   is not offered: neither bash nor PowerShell can set an environment variable
   with a dot in its name the usual way. A CI job passes `-P` flags from its
   secrets.)

   With all four set, `cd kotlin && ./gradlew :app:assembleMemberRelease`
   writes a signed `kotlin/app/build/outputs/apk/member/release/app-member-release.apk`
   — the APK that is submitted. Without them the release still builds, so CI
   and other machines need no keystore, but the APK is
   `app-member-release-unsigned.apk` and Gradle prints a `WARNING:
   assembleMemberRelease is UNSIGNED` line (`bundleMemberRelease` does the
   same). Some but not all four, or a path that is not an existing file outside
   the clone, fails **only** the release assemble, bundle and package tasks
   (`checkReleaseSigning`); debug builds, tests and `ktlintCheck` never look at
   the keystore. The build's own checks print neither the path nor a password,
   but Android Gradle Plugin errors at signing time (a wrong password or alias)
   do print the keystore path.

   Check the signature before submitting, from the repository root. The Android
   SDK is `sdk.dir` in `kotlin/local.properties`; on Windows its build-tools
   hold `apksigner.bat`, not an extensionless `apksigner`, and `ANDROID_HOME`
   is often unset:

   ```bash
   SDK=$(sed -n 's/^sdk.dir=//p' kotlin/local.properties)
   "$SDK/build-tools/36.0.0/apksigner.bat" verify --print-certs \
     kotlin/app/build/outputs/apk/member/release/app-member-release.apk
   ```

   ```powershell
   $sdk = (Select-String -Path kotlin\local.properties -Pattern '^sdk.dir=').Line -replace '^sdk.dir=', ''
   & "$sdk\build-tools\36.0.0\apksigner.bat" verify --print-certs `
     kotlin\app\build\outputs\apk\member\release\app-member-release.apk
   ```

   (If Studio wrote `sdk.dir` with escaped backslashes, `C\:\\Users\\...`, type
   the SDK path by hand.) A signed APK prints the signer's DN and the
   certificate's SHA-256 — the release fingerprint step 3 adds to
   `assetlinks.json` — and exits 0 (add `--verbose` for the `Verifies` line and
   the scheme used, v2). The unsigned APK fails with `DOES NOT VERIFY`.
3. Generate the file:

   ```bash
   bash scripts/assetlinks.sh
   ```

   That prints the debug build's entry. Add the release fingerprint to the same
   array:

   ```bash
   bash scripts/assetlinks.sh --keystore ~/holdout-release.jks --alias holdout
   ```

4. Serve it at `https://<domain>/.well-known/assetlinks.json` — HTTPS, no
   redirect, `Content-Type: application/json`.

```bash
bash scripts/assetlinks.sh --verify --domain holdout.app
```

**Done when** that prints the published file and finds `holdout.app` in it.

Both fingerprints are public information: a fingerprint is a hash of a
certificate, not a key.

---

## 5. scrcpy (T012) — done, 22 September

```bash
scrcpy --record=take.mp4 --no-audio
```

**Answer: Seed Vault's approval screen records.** It is not `FLAG_SECURE`, so a
wallet approval can be captured like any other screen. The `holdout-ui` skill
uses `scrcpy` to look at the live phone while checking a screen.

---

## 6. Spike S1 — wallet signing on real hardware (T012a) — passed 22 September

No fallback was needed: the template's libraries work as researched and nothing
moved to sol4k. The full result is in `research.md` under "S1 · passed 22
September 2026".

| Leg | |
|---|---|
| Seeker + Seed Vault: connect, sign, send | **Passed** — signature `3PN4nN1L…fJwzzL`, slot 502,517,648 |
| `web3-solana` + `rpc-core` on a plain JVM | **Passed**, and kept as a test |
| Plain Android phone + its own MWA wallet | Not run on the spike; covered by tester installs (step 8) |
| `NoWalletFound` on a phone with no wallet | T041's acceptance criterion, checked there |

**Carry this into every instruction built from here:** Seed Vault returned the
transaction with **two `ComputeBudget` instructions it added itself**. What the
wallet sends is not byte-identical to the message the app built, so nothing may
assume it is — a completed-day proof (R16) has to be over payload the app
controls, never over the serialised transaction.

The suite below stays green and is the regression test for all of it:

```bash
cd kotlin && ./gradlew :core:test
```

`S1JvmSpikeTest` proves `web3-solana` and `rpc-core` build transactions, derive
program addresses and reach devnet from a plain JVM with no Android anywhere —
which is what lets the crank and the app share one client instead of two that
drift (R12). The devnet round trip is opt-in:

```bash
cd kotlin && HOLDOUT_DEVNET_SPIKE=1 ./gradlew :core:test --rerun-tasks
```

The two apps install side by side:

```bash
cd kotlin && ./gradlew :app:installMemberDebug
```

```bash
cd kotlin && ./gradlew :app:installLabDebug
```

Holdout Lab is the tester app that records and labels sets (spec 004); members
never see it.

---

## 7. Rep counting on real video (T012b, spec 002) — an hour per set

**Spike S2 is closed, done differently** (T012b, 23–26 September, with
`specs/002-rep-placement-rules`). Its planned output, `s2-angles.json` from ten
honest and three cheat videos in two placements, was replaced by sittings that
Holdout Lab recorded on a Seeker and a person labelled rep by rep from the
video. They are committed under `fixtures/rep-traces/`; `RepRatchetTest` holds
every labelled rep and `RecordedTracesTest` every counted, unlabelled trace.
Pose inference runs at 22–25 FPS on the FULL model (spec 002 plan), the
counter reads a 4:3 picture (spec 002 T034b), and the palm check went with
palm liveness (T047).

What is still by hand is more evidence, from more people:

| Task | What to record or label |
|---|---|
| spec 002 T033 | Three more people: one honest, one from the knees and one cobra set each, staked switch on |
| spec 002 T048 | A Daily 25 set at the front edge of the band, about 40°: 10 honest, 5 from the knees, 5 cobras |
| spec 002 T046, T047 | The reps the labelling page lists as not recorded, and the labels Claude set that a reviewer has not confirmed |

**Every sitting is at about 45°, the Daily 25 included** (spec 002 FR-001,
FR-002, amended 7 October): the phone propped to one side of the member, on the
front camera a sitting uses. Head-on and square side-on sets are refused by
today's app, so they are evidence at most, never a test. Anyone filmed agrees
in writing first (step 8).

The loop runs on this machine; the label format and the rules are in
`fixtures/rep-traces/README.md`:

```bash
bash scripts/pull-traces.sh
```

Copies Holdout Lab's traces and videos over USB into `phone_traces/`
(git-ignored) and deletes each one from the phone once its copy is complete.

```bash
node scripts/label-reps.mjs
```

Serves the labelling page at `http://localhost:8766`: label each rep from its
video until the sitting is finished. `node scripts/label-reps.mjs --promote`
then copies finished sittings into `fixtures/rep-traces/`, traces and labels
only, never a video.

```bash
cd kotlin && ./gradlew :repcounter:testBench -Pgate
```

**Done when** the bench gate holds (honest reps counted 98 % or more, each
cheat 2 % or less) and `:repcounter:test` passes. A threshold moves only
against a failing fixture; when a change moves the ratchet,
`./gradlew :repcounter:updateBaseline` rewrites `fixtures/rep-traces/baseline.csv`,
committed with that change.

---

## 8. Testers (T011)

Real people with Android phones, for the test lobby and for Lab sets.

- Open the group chat and send the one-page onboarding note
  (`docs/holdout_demo_video.md` §9): install, a devnet wallet, the faucet, the
  phone at 45°, the test lobby and what each day asks.
- Anyone recorded in Lab, or who may appear on camera, consents in writing, in
  the chat. Keep the messages (constitution X).
- The test lobby (FR-044a) is the fastest full cycle: three 10-minute days, so a
  tester can join, sit, forfeit or finish, and claim inside an hour.

Real members are the point. Scripted wallets top a cohort up to its floor and no
further, so every real tester makes the crowd more real.

---

## Where each answer belongs

| Step | Written down in |
|---|---|
| 3 | `kotlin/core/src/main/resources/scripted-wallets.json`, committed |
| 4 | the domain's `.well-known/`, the keystore backup, and `WalletRepository` if the host changed |
| 6 | `research.md`, "S1 · passed 22 September 2026" |
| 7 | `fixtures/rep-traces/`: traces, labels and `baseline.csv`, committed |
| 8 | the group chat, and the consent messages kept |

Tick the matching task in `specs/001-holdout-hackathon/tasks.md` as each one
lands. A step is not closed by passing; it is closed by its result being
written where the next task reads it.
