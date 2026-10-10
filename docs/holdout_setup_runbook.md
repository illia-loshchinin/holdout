# Setup runbook — what only a human can do

The parts of running Holdout that a machine cannot finish on its own: a domain,
devnet SOL, keys that must never be committed, real phones, real people. Each
step says what to run, what "done" looks like, and where the answer gets
written down. `specs/001-holdout-hackathon/quickstart.md` covers everything
that *is* automatic: building, testing and deploying.

Step numbers are stable because code and tests cite them: the sponsor key
(step 3, `kotlin/app/build.gradle.kts`), `scrcpy` (step 5, the `holdout-ui`
skill), the S1 regression test (step 6, `S1JvmSpikeTest`) and the rep-counter
calibration (step 7, `fixtures/rep-traces/README.md`).

---

## 0. Where things stand

| | |
|---|---|
| Done | Toolchain and keypair checks (1, 2); operational keys and scripted wallets (3); `scrcpy` and spike S1 on 22 September (5, 6) |
| Still by hand | Devnet SOL for the faucet vault (3); the domain and its Digital Asset Links file (4); the release keystore (4); rep-counter calibration on real video (7); testers (8) |
| Not blocked | Everything in the program and the app builds and tests without any of this; the rep counter runs on research R4's trigger values until step 7 replaces them with measured ones |

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

**Done when** both pass. They pass in the local CI gate
(`scripts/ci-local.sh`), so a failure here is the local toolchain, not the code.

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

Devnet airdrops are rate-limited and often fail, which is the whole reason the
faucet exists. Collect a bit at a time, from more than one source
(`solana airdrop 2`, the web faucet), well before the deploy.

---

## 4. Domain, Digital Asset Links and the release keystore (T009, T115)

The app claims the identity `https://holdout.app` (`MainViewModel`). Until that
domain serves a Digital Asset Links file naming this package and its signing
certificate, wallets show the member an unverified raw package name instead of
"Holdout".

1. Register `holdout.app`, or pick a subdomain of something you already own.
   **If it is not `holdout.app`, change `identityUri` in
   `kotlin/app/src/main/kotlin/holdout/app/MainViewModel.kt` to match** — the
   URI and the file have to be on the same host or the check fails.
2. Create the release keystore, outside the repository, and back it up with the
   program keypair. The build reads it from Gradle properties in
   `%USERPROFILE%\.gradle\gradle.properties`: `holdout.keystore`,
   `holdout.keystorePassword`, `holdout.keyAlias`, `holdout.keyPassword`
   (T115). A lost keystore means a new package identity for every member.
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

## 7. Rep-counter calibration on real video (T012b) — half a day

**The rep counter is built and tested** (T046–T049), against synthetic traces
and the trigger values research R4 proposed: 165° at the top, 85° at the
bottom. Every test asserts the *rule* — top above 160°, bottom below 90°, body
line within ±10°, cycle 0.8–6 s — and never those triggers, so this step can
move them without rewriting a single test.

**What is not yet known is whether those numbers are right for a real body in
front of a real model.** Synthetic traces prove the state machine does what the
rule says. Only video proves the rule survives a real person, real light and the
model's own ~2° error. Until this runs, the counter is plausible, not verified,
and it is the product's magic, so it cannot be left at plausible.

Record in **Holdout Lab**, on a Seeker, **on the front camera** — that is what
a sitting uses (research R4), and its resolution and field of view are lower
than the back camera's, so a recording made with the back one would not measure
the app. Lab writes the trace and the label; nothing leaves the phone except
what the tester exports (constitution X, spec 004).

**The set doubles: every video in both placements.** The app accepts the phone
in front of the member and beside them, so both have to be measured. A threshold
set from side-on traces alone is a threshold for half the members.

| Videos, in **each** placement | |
|---|---|
| 10 honest | 25 real push-ups each. Different people and rooms if you can — one person in one room is one set of angles, not a sample |
| 3 cheats | half reps; sagging hips; too fast |

The **sagging-hips** cheat is the one to watch head-on. The body line points away
from the lens there, which is the weakest thing a single camera measures, and
these traces are what decides whether the front view needs its own trigger set
or whether staked cohorts should require the side view.

What has to come out of it:

- **≥ 15 FPS** pose inference on the Seeker's front camera with the FULL model.
  Below that, fall back in this order: LITE (one constant in `VisionAnalyzer`),
  lower analysis resolution, CPU delegate.
- **The whole body still in frame** at the distance a propped phone gives. If
  the ankles drop out, the pre-flight check says so rather than miscounting,
  but the member cannot start, so this is a real pass/fail. 4:3 is the fallback,
  at the cost of a smaller preview.
- **The start position is recognised**, in both placements. Holding the top of a
  push-up is what begins a sitting (`ReadyGate`, FR-020); if the model reads a
  plank as anything other than elbows extended and body straight, nothing starts.
- **The positioning overlay puts the outline somewhere sensible.** The floor
  line comes from the phone's tilt and its lens, so check it against where the
  floor actually is in the preview.
- Every honest video counted within **1 rep** of 25, and all three cheats
  rejected, under the rule, before any tuning.
- The palm detected.
- **The measured per-frame elbow and body-line angles committed as
  `fixtures/rep-traces/s2-angles.json`.** The schema is in
  `fixtures/rep-traces/README.md`. This is the step's real output: the
  `RepRules` defaults in `kotlin/repcounter/.../RepCounter.kt` are replaced from
  these numbers, and the traces are replayed alongside the synthetic ones.
  Relaxing a threshold is allowed, but only against recorded evidence, which is
  that file.

---

## 8. Testers (T011)

Real people with Android phones, for the test lobby and for Lab sets.

- Open the group chat and send the one-page onboarding note
  (`docs/holdout_demo_video.md` §9): install, a devnet wallet, the faucet, what
  each day asks.
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
| 4 | the domain's `.well-known/`, the keystore backup, and `MainViewModel` if the host changed |
| 6 | `research.md`, "S1 · passed 22 September 2026" |
| 7 | `fixtures/rep-traces/s2-angles.json`, committed |
| 8 | the group chat, and the consent messages kept |

Tick the matching task in `specs/001-holdout-hackathon/tasks.md` as each one
lands. A step is not closed by passing; it is closed by its result being
written where the next task reads it.
