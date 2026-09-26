# Setup runbook — what only a human can do

Everything in `tasks.md` Phase 1 that a machine cannot finish on its own: a
domain, devnet SOL, real phones, real people. Each step says what to run, what
"done" looks like, and where the answer gets written down.

**Read the order as real.** Steps 1–4 block nothing but cost nothing; step 7
blocks the rep counter entirely (constitution, Development Workflow:
implementation does not begin before the spikes pass or their fallbacks are
chosen). Step 8 blocks the footage, which blocks the submission. Steps 5 and 6
are **done** — kept below as the record of what was run.

Dates below are the plan's: freeze **28 September**, submission **9 October
08:59 CEST**.

---

## 0. Where things already are

| | |
|---|---|
| Done in the repository | The Anchor workspace, the account and maths layer, the LiteSVM harness, the contract suites, CI, the Gradle project with all four modules, ktlint, the S1 JVM spike, and **the Daily 25 end to end** — pre-flight, palm liveness, the rep counter, banking and the streak |
| Done so far | Steps 5 and 6 — `scrcpy` and spike S1, both on 22 September |
| Still to do | Steps 1–4, 7 and 8 |
| Blocked until then | Nothing is stopped. The rep counter is **built against research R4's trigger values**, and step 7 is what replaces them with measured ones — see the warning in step 7 |
| Not blocked at all | `initialize`, `create_league`, `open_cohort`, join/leave, settlement — the program work carries on regardless (T027 onward) |

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

## 2. Program keypair — 2 minutes, before anything touches devnet

The deployed address has to stay `Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4`.
Anchor silently generates a *different* key when the file is missing, deploys to
the new address, and then the README, the app and `declare_id!` all name a
program that is not there.

```bash
mkdir -p target/deploy && cp ~/.config/solana/holdout-program-keypair.json target/deploy/holdout-keypair.json && anchor keys list
```

**Done when** it prints `holdout: Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4`.

If that file does not exist yet, this is the moment to find out: a key generated
after the 28 September freeze strands the judged cohort. Back it up off this
machine today.

---

## 3. Operational keys and devnet SOL (T010) — 20 minutes, then it drips

```bash
bash scripts/devnet-keys.sh
```

Creates `admin`, `operator`, `sponsor` and `treasury` under
`~/.config/solana/holdout/`, never overwriting one that exists, and prints the
four public keys. `admin` must also be the program's upgrade authority —
`initialize` checks that against the ProgramData account and refuses anyone else
(R14). So deploy with `admin`, or hand it the authority before `crank init`.

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

The funding schedule it measures you against:

| By | What | How much |
|---|---|---|
| now | deploy rent, on `admin` | measured at the rehearsal deploy |
| 22 Sep | scripted wallets, from `operator` | ~1.12 SOL, 0.02 each |
| launch | sponsor float | 0.05 SOL |
| 8 Oct | faucet vault | **25 SOL** — from 9 October it must last to 11 November with no top-up (FR-042a) |

Devnet airdrops are rate-limited and often fail, which is the whole reason the
faucet exists. Start collecting now, a bit at a time, from more than one source
(`solana airdrop 2`, the web faucet). 25 SOL is not something to begin on 7
October.

---

## 4. Domain and Digital Asset Links (T009) — 30 minutes plus DNS

The app already claims the identity `https://holdout.app` (`MainViewModel`).
Until that domain serves a Digital Asset Links file naming this package and its
signing certificate, wallets show the member an unverified raw package name
instead of "Holdout".

1. Register `holdout.app`, or pick a subdomain of something you already own.
   **If it is not `holdout.app`, change `identityUri` in
   `kotlin/app/src/main/kotlin/holdout/app/MainViewModel.kt` to match** — the
   URI and the file have to be on the same host or the check fails.
2. Generate the file:

   ```bash
   bash scripts/assetlinks.sh
   ```

   That prints the debug build's entry. Add the release fingerprint to the same
   array once the release keystore exists:

   ```bash
   bash scripts/assetlinks.sh --keystore ~/holdout-release.jks --alias holdout
   ```

3. Serve it at `https://<domain>/.well-known/assetlinks.json` — HTTPS, no
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

**Answer: Seed Vault's approval screen records.** It is not `FLAG_SECURE`, so no
moment in the film needs the second camera and every take can be a single
capture. Written into `docs/holdout_demo_video.md` §4, where every filming day
reads it.

The second phone's wallet has not been checked and may still block; that is one
line of the first tester install (step 8).

---

## 6. Spike S1 — wallet signing on real hardware (T012a) — passed 22 September

**T038, T040 and T061 are unblocked.** No fallback was needed: the template's
libraries work as researched and nothing moves to sol4k. The full result, and
the one finding that constrains the code, is in `research.md` under
"S1 · passed 22 September 2026".

| Leg | |
|---|---|
| Seeker + Seed Vault: connect, sign, send | **Passed** — signature `3PN4nN1L…fJwzzL`, slot 502,517,648, shot log S-01 |
| `web3-solana` + `rpc-core` on a plain JVM | **Passed**, and kept as a test |
| `scrcpy` on the approval screen | **Passed** — step 5 |
| Plain Android phone + its own MWA wallet | Not run. Answered by the first tester install (step 8) |
| `NoWalletFound` on a phone with no wallet | Not run. It is T041's acceptance criterion and is checked there |

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

The app's temporary screen keeps its **Send devnet transaction** button until
T039–T040 replace it, so the path can be re-run on any phone in a minute:

```bash
cd kotlin && ./gradlew :app:installDebug
```

Two things are still worth a glance when a tester phone is in hand: that the
approval screen names **"Holdout"** rather than a raw package once step 4's
Digital Asset Links file is live, and that a phone with no wallet returns
`NoWalletFound`.

---

## 7. Spike S2 — rep counting on real video (T012b) — half a day

**The rep counter is already built and tested** (T046–T049), against synthetic
traces and the trigger values research R4 proposed: 165° at the top, 85° at the
bottom. Every test asserts the *rule* — top above 160°, bottom below 90°, body
line within ±10°, cycle 0.8–6 s — and never those triggers, so this spike can
move them without rewriting a single test.

**What is not yet known is whether those numbers are right for a real body in
front of a real model.** Synthetic traces prove the state machine does what the
rule says. Only video proves the rule survives a real person, real light and the
model's own ~2° error. Until this spike runs, the counter is plausible, not
verified — and it is the product's magic, so it cannot be left at plausible.

What to record, on a Seeker, **on the front camera** — that is what a sitting
uses (research R4), and its resolution and field of view are lower than the back
camera's, so a recording made with the back one would not measure the app:

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

- **≥ 15 FPS** pose inference on the Seeker's front camera. Below that, fall
  back in this order: lite model, lower analysis resolution, CPU delegate.
- **The whole body still in frame** at the distance a propped phone gives. Two
  things narrow it: the front camera, and the 16:9 aspect the preview and the
  analysis are both pinned to, which is usually a vertical crop of the sensor.
  If the ankles drop out, the pre-flight check says so rather than miscounting —
  but it means the member cannot start, so this is a real pass/fail for the
  spike. 4:3 is the fallback, at the cost of a smaller preview.
- **The start position is recognised**, in both placements. Holding the top of a
  push-up is what begins a sitting now (`ReadyGate`, FR-020); if the model reads
  a plank as anything other than elbows extended and body straight, nothing
  starts at all.
- **The positioning overlay puts the outline somewhere sensible.** The floor
  line comes from the phone's tilt and its lens, so check it against where the
  floor actually is in the preview — a horizon in the wrong half of the screen
  means the field of view was read off the wrong sensor axis.
- **The frame rate on the FULL model.** R4's bar is ≥ 15 FPS on a Seeker. FULL
  is now shipped as R4 always specified; LITE is the documented fallback and is
  one constant in `VisionAnalyzer`.
- Every honest video counted within **1 rep** of 25, and all three cheats
  rejected — under the rule, before any tuning.
- The palm detected.
- **The measured per-frame elbow and body-line angles committed as
  `fixtures/rep-traces/s2-angles.json`.** The schema is in
  `fixtures/rep-traces/README.md`. This is the spike's real output: the
  `RepRules` defaults in `kotlin/repcounter/.../RepCounter.kt` are replaced from
  these numbers, and the traces are replayed alongside the synthetic ones.
  Relaxing a threshold is allowed — but only against recorded evidence, which is
  that file.

The quickest way to gather it: install the debug APK, run a sitting, and record
what the counter does against what you actually did. The counting screen already
shows the running total and the hints, so a miscount is visible as it happens.

---

## 8. Beta testers (T011) — this week

Five to seven real people with Android phones, recruited **by 21 September** on
the filming calendar, which has already passed. This is the step most worth
doing today.

- Open the group chat.
- Send the one-page onboarding note. It is already written, in
  `docs/holdout_demo_video.md` §9 — do not rewrite it.
- Collect filming consent in writing, in the chat, from anyone who might appear
  on camera. Keep the messages.
- Book the rehearsal run, **24–26 September**: a test league of seven 10-minute
  days at 5 reps, about 70 minutes per tester, on the rehearsal deployment. Its
  footage is never used.

Real members are the point. Scripted wallets top a cohort up to its floor and no
further, so every tester recruited makes the crowd the judges see more real.

---

## Where each answer belongs

| Step | Written down in |
|---|---|
| 3 | `kotlin/core/src/main/resources/scripted-wallets.json`, committed |
| 4 | the domain's `.well-known/`, and `MainViewModel` if the host changed |
| 5 | `docs/holdout_demo_video.md` §4 |
| 6 | `docs/holdout_demo_video.md` §5, with signatures |
| 7 | `fixtures/rep-traces/s2-angles.json`, committed |
| 8 | the group chat, and the consent messages kept |

Tick the matching task in `specs/001-holdout-hackathon/tasks.md` as each one
lands. A spike is not closed by passing — it is closed by its result being
written where the next task reads it.
