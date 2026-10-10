# Research — 001-holdout-hackathon

**Phase 0 output of `/speckit-plan`.** Each entry: **Decision** · **Rationale** · **Alternatives considered**. Items marked *confirm at spike* are decided here but must be validated by the risk spikes in `plan.md` before implementation depends on them; the fallback is named so a failed spike changes a line, not the plan.

Resolves decision-record open items P3, P4, P5, P6, P8, P10, P11 (plan-level parts). Revised 2026-09-17 for the official CLOCK IN dates and deliverables (spec clarifications of that date): R12, R13, R14, R18.

**Stack in one line:** Rust/Anchor program on devnet · **native Android app in Kotlin** (Jetpack Compose, Solana Mobile Kotlin SDKs, MediaPipe Tasks) · Kotlin/JVM crank sharing the app's core module · TypeScript + LiteSVM for program integration tests.

---

## R1 · On-chain program framework

- **Decision:** Rust program built with **Anchor 1.1.x (1.1.2)**, one program named `holdout`, deployed to **devnet**. Toolchain pinned to the Solana Foundation's tested combination: **Solana CLI 3.1.10**, Rust **≥ 1.89**, platform tools v1.52+, `solana-*` crates on `^3`, all `anchor-*` crates on the exact same version, `Cargo.lock` committed. Token CPIs go through Anchor's **token interface**, so the program works with either the classic SPL Token program or Token Extensions. The toolchain runs in **WSL2 (Ubuntu 24.04)** on the Windows development machine — Anchor 1.x binaries need GLIBC ≥ 2.39 and have no native Windows build.
- **Rationale:** Anchor 1.1.x (June 2026) is the Solana Foundation's recommended line for new projects (solana-dev skill, compatibility matrix). One program keeps account relationships, PDAs and invariants in one place for a 13-day build. SKR is an SPL asset; the mock SKR mint is a classic SPL mint, and the token interface keeps the door open for Token Extensions (the Genesis Token uses them).
- **Alternatives considered:** native Rust without Anchor (more boilerplate, slower to a correct IDL); several programs as in the archived PRD (cross-program invariants for no benefit); Pinocchio (less tooling, no time to absorb).

## R2 · Contract testing and clock control

- **Decision:** three layers, written **before** the logic they test (constitution VII):
  1. **Pure settlement math** in a dependency-free Rust module (`programs/holdout/src/math/`), unit-tested against every vector in PRD §19 (V1–V22) plus property tests for the arithmetic invariants.
  2. **Program integration tests** in **TypeScript on LiteSVM** (`litesvm` npm 1.3.x) through **`@solana/kit` with `@solana/kit-plugin-litesvm`**, using a **Codama-generated Kit client** rendered from the Anchor IDL. Tests set the Clock sysvar to jump to join cutoffs, deadlines and settlement. The randomised INV-1…INV-8 suite (acceptance criterion 7), including a 1,000-member cohort, runs here. A small **Surfpool** smoke suite (`anchor test`'s default runner in Anchor 1.x) exercises the real RPC path with `surfnet_timeTravel` before each devnet deploy. All of this runs in WSL2 — LiteSVM and Surfpool ship native binaries for Linux and macOS only.
  3. **Shared golden fixtures** (`fixtures/*.json`): account byte layouts, PDA derivations, instruction encodings and derived figures (breach, survivor counter, projected share) produced by the TypeScript tests and **replayed by the Kotlin `core` module's unit tests**, so the Kotlin client can never drift from the program.
- **Rationale:** LiteSVM runs in-process with clock control, and every rule here is time-driven; Anchor's TypeScript tooling is the most mature place to test the program itself. Golden fixtures turn "two languages" into one source of truth.
- **Alternatives considered:** `solana-test-validator` (slow, no clock control); `@solana/web3.js` v1 with the old Anchor TypeScript client (the Solana Foundation now recommends Kit; web3.js v1 is legacy); Rust-only integration tests (fine, but the generated Kit client makes fixture generation trivial); writing program tests in Kotlin (no LiteSVM bindings).
- **Deviation from the solana-dev skill, stated:** the skill says never to hand-write Borsh layouts for a program you own. Codama has no Kotlin renderer, so the Kotlin codecs in `core` are hand-written — which is exactly why they must replay the golden fixtures generated from the Codama TypeScript client and the real program (layer 3).

## R3 · Mobile app stack

- **Decision:** a **native Android app in Kotlin**, UI in **Jetpack Compose**, generated with the **Solana Mobile CLI** (`npx solana-mobile@latest create`, template **"Kotlin Compose Minimal"**) — the same template as the local reference project `fitbet`. Its tested versions become the baseline:

  | Component | Version from the template |
  |---|---|
  | Gradle / Android Gradle Plugin | 9.7.1 / 9.3.2 (built-in Kotlin support — no separate `kotlin-android` plugin in app modules) |
  | Kotlin / Compose compiler plugin | 2.4.10 |
  | Compose BOM | 2026.08.00 (Material 3, lifecycle 2.11, activity-compose 1.13) |
  | `com.solanamobile:mobile-wallet-adapter-clientlib-ktx` | 2.2.0 |
  | `com.solanamobile:web3-solana` | 0.3.1 (public keys, transactions) |
  | `io.github.funkatronics:multimult` | 0.2.6 (Base58) |
  | `com.solanamobile:rpc-core` | **added by us** — not in the template's app module; version pinned at scaffold (the template's stray root entry names 0.2.7) |
  | compileSdk / targetSdk / minSdk | 37 / 37 / **26** |
  | Bytecode / Gradle daemon JVM | Java 17 / JDK 21 |

  Wallet flow follows the template's pattern: a `MobileWalletAdapter` with a `ConnectionIdentity` (app name, icon, identity URI) and `blockchain = Solana.Devnet`; an `ActivityResultSender` created in the activity; `connect`, `transact { signAndSendTransactions(...) }` and `disconnect`, each returning `TransactionResult.Success`, **`NoWalletFound`** (drives the no-wallet screen, ES-0) or `Failure`. The identity URI must be a domain we control with a Digital Asset Links file so wallets can verify the app. Anchor instruction data is encoded following Solana Mobile's "Working with Anchor programs in Kotlin" guide. *Confirm at spike S1.*
- **Corrected 2026-09-18, after actually generating it.** The stray root `dependencies { }` and `android { }` blocks (MWA 2.0.3, web3-solana 0.2.5, rpc-core 0.2.7) are in the local **`fitbet` reference project**, not in what `npx solana-mobile@latest create` produces. A fresh scaffold is clean. What it really needs:
  - **Names come from the directory**: `kotlin` became `rootProject.name = "kotlin"` and namespace `com.kotlin`. Both renamed to **`holdout.app`**, matching the package paths in tasks.md.
  - **Identity URI** is `https://yourdapp.com`; now `https://holdout.app`, the domain still to be registered with its Digital Asset Links file (T009).
  - **`rpc-core` is absent**; added at **0.2.8**, with `rpc-solana` and `rpc-ktordriver` beside it.
  - **The Kotlin JVM plugin must be declared `apply false` in the root build file**, or `:core`, `:repcounter` and `:crank` fail with "the plugin is already on the classpath with an unknown version".
  - Every pinned version resolved as listed above, and `:app:assembleDebug` produces a working APK.
- **Emulator for the daily loop (2026-09-18):** AVD `holdout37` — Pixel 7, API 37, Google Play x86_64 image. The app installs, launches and returns `NoWalletFound` with no wallet present (ES-0). The Seeker remains the device of record for Seed Vault signing and the ≥ 15 FPS pose target (spikes S1, S2).
- **Rationale:** the camera pipeline is the product's magic, and native Kotlin gives MediaPipe and CameraX first-party, GPU-accelerated support with no bridge (R4). Starting from the CLI template gives a known-good, current Gradle/AGP/Kotlin/Compose/MWA combination instead of assembling one by hand in a 13-day build. One language for app, device logic and crank (R12).
- **Alternatives considered:** React Native + Expo (faster UI iteration, but pose detection through community Vision Camera plugins is the riskiest spike, and a TypeScript/Kotlin bridge sits on the hot path); Flutter (weak Solana Mobile support); sol4k as the Solana client (capable, but Solana Mobile's own libraries match MWA's types directly).

## R4 · On-device pose estimation and rep counting

- **Decision:** **MediaPipe Tasks Vision — Pose Landmarker** (BlazePose GHUM, 33 landmarks, `LIVE_STREAM` mode, **FULL** variant on the **GPU delegate** with automatic CPU fallback, LITE if spike S2 misses 15 FPS on a Seeker) fed by **CameraX** `ImageAnalysis` (newest frame only, ~640×480). The rep rules, the four pre-flight checks and the palm check live in a **pure Kotlin/JVM module** (`kotlin/repcounter`) that consumes landmark frames and has no Android dependency.
- **How the angles are actually measured** — the part that decides whether the PRD's rule is enforceable:
  - **World landmarks, not image coordinates.** The task emits 3D world landmarks in metres relative to the hips, so elbow flexion and the shoulder–hip–ankle line stop depending on where the phone is lying. Image coordinates drive the overlay only.
  - **Smoothing before thresholding.** A one-euro filter on the landmarks feeding each angle. Published validation puts BlazePose's joint-angle error at roughly **2° mean absolute, RMSE ≈ 2.6°**, so raw per-frame angles cannot be compared against a hard edge.
  - **Hysteresis wider than that error.** The PRD's rule stays *top > 160°, bottom < 90°, body line ±10°, cycle 0.8–6 s* (FR-021), but the state machine triggers at **≥ 165° for the top and ≤ 85° for the bottom**, so a 2–3° jitter band cannot flip a state or double-count a rep. A rep is counted by the state machine completing top → bottom → top, never by a single frame.
  - **Visibility gating.** Frames where elbow, shoulder, hip or ankle fall below the landmark presence threshold are dropped rather than scored; a run of dropped frames ends the attempt with the "can't see you" hint instead of guessing.
  - **Framing is classified, not required (corrected 2026-09-23).** This bullet previously read "a front-on phone cannot see elbow flexion at all", which contradicts the world-landmark bullet three lines above it and is wrong. The landmarks are 3D, so the angles are **geometrically recoverable from any view**. What changes with placement is *precision*: a single camera estimates depth far worse than position within the image, and a push-up seen head-on puts nearly all of its movement along the depth axis. Side-on keeps that movement in the image plane, which is the strongest signal; head-on leans on the weakest. Both are accepted — see the placement decision below.
  - **Every number here is provisional until spike S2 measures it** against the 10 honest and 3 cheat videos; S2's deliverable is `fixtures/rep-traces/s2-angles.json`, the measured per-frame angle table the final thresholds are set from, committed with the traces so the tuning is evidence rather than recollection.
- **Rationale:** MediaPipe's Android task library with CameraX is Google's reference path, and the Hand Landmarker for palm liveness (R5) shares the same runtime and frame stream. Keeping the rules in a JVM module makes them unit-testable against recorded traces — including deliberate cheats — without a phone. No part of the pipeline has a network path, so frames never leave the device (constitution X).
- **Alternatives considered:** ML Kit Pose Detection (fewer landmarks, no world coordinates); MoveNet Thunder via TFLite (17 keypoints, and the body-line rule needs hips and ankles; validation puts its upper-limb error under 10° RMSE, worse than BlazePose for this measurement); server inference (forbidden by constitution X).
- **Sources:** MediaPipe Pose Landmarker task documentation; BlazePose joint-angle validation (MAE ≈ 2.11°, RMSE ≈ 2.63°); MoveNet upper-limb range-of-motion validity study.

- **Which placement (decided 2026-09-23, after the first sitting):** **both.** People put the phone on the floor in front of them, and the pre-flight check that demanded a side-on view blocked the commonest placement, which is a check that stops the app working. `Preflight.classifyView` now names the view — `SIDE`, `OBLIQUE` or `FRONT` — from shoulder span against torso length in the image, a ratio that is independent of how the phone is rotated. The check fails only when it cannot tell what it is looking at.
  - **One rule set, for now.** `RepRules.forView` exists so spike S2 can give the front view its own triggers, and returns the same rules for every view today. Inventing front-view numbers before the traces exist is the tuning-without-evidence this research keeps refusing.
  - **What must never happen:** widening a front-view trigger past the rule to make head-on counting work. Triggers are *stricter* than the rule by construction so jitter cannot count a rep; a looser one counts reps FR-021 forbids, which changes what a rep is. `RepRules`'s `require` refuses it and `RepRulesViewTest` checks every view.
  - **Expected cost:** sag detection is weaker head-on, because the shoulder–hip–ankle line points away from the lens. Named in the README rather than engineered around.
- **Positioning overlay (added 2026-09-23):** accepting both placements exposed the thing the four text checks never solved — the member has no idea where to put their body. The app now finds the floor from **gravity and the lens**, with no body required: `Placement.floorView` projects the horizon from the phone's pitch and its vertical field of view, so a phone lying on its back can be told it is looking at the ceiling *before* anyone gets down on the floor. A dashed outline is drawn on the floor, shaped for the placement — long and flat for a side view, foreshortened head-on — with one correction at a time. It fades out the moment the member is aligned and does not return once the countdown starts: scaffolding, not furniture.
- **Model: FULL, as decided here.** LITE was shipped by mistake on 2026-09-22 and corrected on 2026-09-23. Better landmarks matter most in the depth direction, which is exactly what the newly accepted front placement depends on. LITE remains the documented fallback if S2 measures under 15 FPS on a Seeker.
- **Which camera (decided 2026-09-22, on the device):** the **front** camera. A
  sitting is self-recorded — the phone is propped at chest height beside the
  member — so they have to see the preview to get their whole body in frame, the
  angle right and a palm where the model can read it. With the back camera they
  aim a lens they cannot see through, which is the exact framing problem the
  pre-flight pills exist to catch; being told to fix something you cannot see is
  worse than no check. Two consequences: the preview is mirrored by
  `PreviewView` while `ImageAnalysis` frames are not, so the skeleton overlay
  mirrors the landmarks back (the count is unaffected — a reflection does not
  change an angle, and the rule averages both sides); and **spike S2 now
  measures the front camera**, whose resolution and field of view are lower. If
  it cannot hold ≥ 15 FPS, or loses the ankles at arm's length, that is a
  finding for the spike and its documented fallbacks, not a reason to point the
  camera away from the member.

## R5 · Palm liveness

- **Decision:** MediaPipe Tasks **Hand Landmarker** on the same CameraX stream: an open palm (five fingertips extended, palm facing the camera) held **3 continuous seconds**; **30-second timeout** back to pre-flight (FR-020). The hand model runs only during the liveness step and is closed before counting, so pose inference has the GPU to itself. *Confirm at spike S2.*
- **Rationale:** same runtime, same frames; hand landmarks make "open palm facing the camera" a geometric rule in `repcounter` rather than a trained classifier.
- **Alternatives considered:** pose wrist/hand landmarks only (too coarse to tell an open palm from a fist).

## R6 · Banked progress and pending submissions on the device

- **Decision:** **MMKV for Android** key-value storage. Keys are scoped by **wallet + cohort (or `challenge`) + day index**, so another wallet's or another day's progress is never read and expires by construction at the boundary. A block is **written synchronously before** the UI shows it as banked. A completed cohort day not yet landed is stored as a **pending submission** `{cohort, dayIndex, proofHash, completedAt}` and shown as "Done — tap to submit" (Phase 1). The daily streak is stored the same way, per wallet.
- **Rationale:** MMKV writes are synchronous and survive process death — the "write before showing banked" guarantee (FR-023) — with no server (FR-024a).
- **Alternatives considered:** Jetpack DataStore (asynchronous writes — a crash between count and write could show an unsaved block); Room (more setup than a handful of keys needs).
- **Resolves:** P10.

## R7 · Time on the device

- **Decision:** every deadline, breach, lock and countdown is computed from **chain time** — the `unix_timestamp` in the Clock sysvar — read on app start, on resume, and after each confirmed transaction, then advanced with **`SystemClock.elapsedRealtime()`** (monotonic) between reads. The device wall clock is never used. Formulas live once in `kotlin/core` (`Derive.kt`), shared by app and crank and pinned by golden fixtures (R2).
- **Rationale:** FR-017 and constitution II make chain time the only judge; a monotonic timer can't be moved by changing the phone's clock, and periodic re-sync bounds drift to seconds.
- **Alternatives considered:** device clock (manipulable); a time server (a server, and still not chain time).

## R8 · Reading chain state at 1,000 members

- **Decision:** the app reads program accounts directly from a **devnet RPC endpoint** that supports `getProgramAccounts` filters (a hosted devnet RPC provider, with public devnet as fallback): memberships of one cohort via a `memcmp` filter on the cohort key and a `dataSlice` covering only the fields the roster and counters need; decoded in `core`, cached in memory, refreshed on resume and every 30 seconds while the cohort screen is visible. **No Holdout server or indexer.** Phase 2 league leaderboards read `Profile` accounts the same way.
- **Rationale:** a 1,000-member cohort is ~100 KB of membership data — one call. The RPC node is chain infrastructure, not a Holdout service that decides or ranks anything (FR-060, FR-065).
- **Alternatives considered:** a Holdout indexer (a server that could show unsupported figures); one websocket subscription per membership (1,000 subscriptions).
- **Resolves:** P8.

## R9 · Paging automation for 1,000-member cohorts

- **Decision:** `record_forfeits`, `settle_count` and `open_cohort` are **permissionless**. Paged instructions take **(membership, profile) pairs in remaining accounts, up to 10 pairs per transaction** (tuned at spike S3 against the transaction size limit). Each membership carries its own processed flag, so pages can run **in any order and any number of times**. Settlement is split into `settle_count` (paged, order-independent; forfeits any still-unrecorded breach in the page first) and `settle_finalize` (one transaction, only once every member is counted).
- **Rationale:** per-membership flags make idempotency and out-of-order paging structural rather than a cursor that must be advanced correctly (constitution VIII, acceptance criterion 7). A 1,000-member cohort needs ~100 short transactions per step — minutes for the crank, and anyone can run them.
- **Alternatives considered:** a stored cursor (a crashed caller must resume exactly; two callers race); address lookup tables for bigger pages (unnecessary at this scale; revisit if S3 shows limits bite).

## R10 · Exact settlement in constant time

- **Decision:** because every stake in a cohort equals one of that cohort's **four** locked chip amounts (FR-055), `settle_count` accumulates **finisher counts per tier** alongside `total_finisher_stake` (`T`). `settle_finalize` computes, in constant time:
  - `share_t = floor(pool × chip_t ÷ T)` for each tier `t`
  - `remainder = pool − Σ_t count_t × share_t`, sent to the fee destination (FR-032a)
  - with `T = 0`: `developer = floor(pool ÷ 2)` to the developer destination and `pool − developer` to the operations destination; nothing carries over (FR-037, revised 2026-10-05 and 2026-10-09)
  A claim pays `stake + share_tier`, read from the finalized cohort.
- **Rationale:** the exact remainder normally needs every finisher's share; tier counters make it O(1) and give every member of a tier a byte-identical payout (acceptance criterion 3) with no iteration at claim.
- **Alternatives considered:** storing each finisher's payout during counting (the pool isn't final until every forfeit is recorded); leaving the remainder in the pool (rejected by H11).

## R11 · Opening cohorts on schedule

- **Decision:** each league has a `LeagueConfig` holding `first_start` (a Monday 05:00 UTC), `cadence` (7 days), `duration_days`, `reps_per_day`, `day_length` and `next_index`. Cohort `i` starts at `first_start + i × cadence`, join cutoff `start − 3,600 s`. `open_cohort` is permissionless and succeeds only for `i == next_index` and, for `i > 0`, only once cohort `i − 1` has started; it copies the current chip set. **Test leagues** with non-default `day_length` are created only by the admin, only in builds with the `test-leagues` feature, and used on LiteSVM and — *amended 2026-09-26, constitution v1.4.0* — on the devnet program itself, which is built with `test-leagues` so that **one disclosed short test cohort** can run the whole cycle before the deadline and be recorded for the walkthrough and demo video (FR-044, FR-044a). It is labelled everywhere in the app and keeps its progress in the profile's test slot, apart from judged progress. Mainnet builds never carry the feature. The judged leagues start **Monday 5 October 05:00 UTC** (moved from 28 September).
- **Rationale:** the schedule is pure arithmetic on stored parameters, so no caller can pick a start time or chip set (FR-030a), and a late opening still yields the scheduled start (ES-15).
- **Alternatives considered:** the Operator passing start times (a privileged, error-prone input); cron-only opening (a dead crank would stop the league).

## R12 · Scheduler, scripted members and Operator tooling

- **Decision:** one **Kotlin/JVM command-line tool** (`kotlin/crank`) that depends on the same `kotlin/core` module as the app:
  - `crank run` — opens due cohorts, records forfeits for breached members, settles finished cohorts. Idempotent; `--dry-run` prints what it would do (scope doc D-F).
  - `crank scripted` — drives scripted wallets: faucet claim, join, submit completed days early in each day, and stop submitting on each wallet's scheduled forfeit day.
  - `crank chips publish` — Operator-only chip set publication (Phase 1: once at launch).
  Packaged as a single runnable JAR, run every **10 minutes** as a **Fly.io scheduled machine** (scope doc D-F); a GitHub Actions scheduled workflow is the documented fallback. Anyone with the repo can run `crank run` by hand (FR-030a).
- **Scripted members fill to a floor (P5; revised 2026-09-17 — real members first):** a cohort is filled by real members first, and scripted wallets join only until it holds its league's minimum — **20 members in a 7-day cohort, 12 in a 21-day one**, counting real and scripted alike (FR-043). With 5–7 beta testers expected in cohort #3, roughly a third of it is real, and every later real joiner reduces the scripted share.
  - **Deficit:** `deficit = floor − current member count`, read from the cohort account before each pass. Once it reaches zero no further scripted wallet joins, and a scripted member never leaves to make room (FR-043a).
  - **Two stages, so a lobby is never dead and real members still have room:** fill to **half the floor when the lobby opens**, then to the **full floor 24 hours before the join cutoff**.
  - **Tiers and forfeits:** wallets spread across the four tiers, with forfeit days from a fixed seeded schedule removing **4–8 % per day** in the first half and **1–3 % per day** after, and about **40 %** finishing — the same shape as before, at the smaller size.
  - Their SKR stakes come from their own faucet claims under the same once-a-day rule as everyone (FR-043a).
- **Which cohorts, and for how long (revised 2026-09-17):** scripted wallets join every cohort starting from **28 September through 9 November 2026** — seven per league — so a populated cohort is running at every moment of the live period, from submission to the winners' announcement on 11 November (FR-043). Cohorts starting after 9 November get no scripted members. The crank's schedule keeps running until the last of those cohorts (the 21-day cohort starting 9 November) settles on **30 November**, so no scripted wallet forfeits merely because automation stopped; then the schedule is switched off, and anyone can still crank the leagues (FR-030a).
- **Wallet pool:** a fixed set of **56 wallets** — enough to fill every cohort to its floor with none left over:
  - **20 for the 7-day league.** Each holds a lobby place in the next cohort while in the current one (FR-006a), so the same 20 play every week.
  - **36 for the 21-day league, in three groups of 12** that take turns every three weeks. A 21-day wallet can't hold a place in a cohort that starts before its own ends (FR-006b), and up to three 21-day cohorts run at once.
  - No wallet plays both leagues at once (FR-006).
- **SOL:** the Operator tops each scripted wallet up **once to 0.02 SOL** before its first join (≈ **1.12 SOL** in total). Scripted wallets pay their own fees and rent, so they never touch the faucet's SOL cap (R13). The heaviest user is a 7-day wallet in all seven cohorts: profile and SKR token account rent ≈ 0.004 SOL, plus ≈ 0.0017 SOL per membership in rent and fees, ≈ 0.016 SOL in total — inside the top-up.
- **Floors live in the crank's configuration** (`--floor-7d`, `--floor-21d`), never on-chain: the contract must not know which wallets are scripted, and nothing about a cohort depends on it (constitution I, FR-043a).
- **Scripted address list:** generated once, committed as `kotlin/core/src/main/resources/scripted-wallets.json` (public keys only), bundled into the app and copied into the README. Keypairs live only in the crank's secret store.
- **Rationale:** the crank, scripted members and app share one Kotlin implementation of codecs, PDAs, instruction builders and derived figures — the production code paths never diverge. Filling only to a floor keeps the crowd as real as recruitment allows, which is what the rubric's stickiness quarter measures, while still guaranteeing that nobody installing the app finds an empty cohort (constitution XII).
- **Alternatives considered:**
  - **A TypeScript crank reusing the test client.** Two production client implementations to keep in sync.
  - **Separate services per job.**
  - **Fixed rosters of 80 and 60 regardless of real members** (the plan until 2026-09-17). Bigger cohorts, but with 5–7 testers the crowd would have been ~93 % scripted, which is the opposite of what judges score.
  - **No scripted members at all.** Saves about a day and a half and makes every figure real, but a thin recruiting week leaves a judge looking at a two-person cohort, breaking constitution XII.
- **Resolves:** P5.

## R13 · Faucet design, including a zero-SOL wallet

- **Problem found in planning:** a brand-new wallet with **0 SOL cannot pay the fee** for any transaction, including its own faucet claim, and a program-derived address cannot pay transaction fees.
- **Decision:**
  - **Test SKR** is minted per claim by the program: the mock SKR mint's authority is a program address (`["mint_authority"]`), so the faucet never runs out of SKR. Amount: the current chip set's **$100-tier** amount.
  - **Devnet SOL** comes from a program-owned **faucet vault** (`["faucet_vault"]`), funded to last from submission through 11 November 2026. A claim **tops the recipient up to 0.02 SOL** — enough for membership and profile rent plus every fee of a 21-day cycle — and sends nothing if they already hold that much.
  - **Fee bootstrap:** the app bundles a **devnet sponsor key** whose only use is paying the fee for `claim_faucet`. The instruction needs **no member signature** — the recipient is the connected wallet's address — so no partial signing through MWA is involved, and it reimburses the sponsor's fee and rent from the vault in the same instruction, so the sponsor's balance stays flat. A wallet that already holds SOL may pay for its own claim instead; `claim_faucet` accepts either payer.
  - **The sponsor's float is small and refillable by anyone (added 2026-09-19):** the bundled key holds a float of `Config.faucet.sponsor_float` = **0.05 SOL** and nothing else. Because anyone can extract the key, anyone can also empty that float with a plain System Program transfer — no faucet guard can prevent it, and an empty sponsor cannot pay a claim fee, which would end zero-SOL onboarding rather than merely costing a day's allowance. So a **permissionless `top_up_sponsor`** refills the float from the faucet vault whenever the sponsor holds less than half of it, charged against the same daily budget as a claim. The crank runs it on every 10-minute pass, and any wallet with a little devnet SOL can run it too. A drain therefore pauses zero-SOL onboarding for at most one crank pass, needs no Operator, and costs the vault no more than the day's budget (FR-042b).
  - **What a refused refill means:** `top_up_sponsor` fails while the sponsor still holds half its float (`SponsorFloatSufficient`) and once the day's budget is spent (`FaucetCapReached`). Both are ordinary outcomes, not incidents: the crank reports them, exits `0` and tries again on the next pass. **No separate per-day refill counter is stored** — refills draw on the same lamport budget as claims, which is what bounds the day, and a second counter would only bound it twice while adding state and another way to be wrong.
  - **Limits enforced on-chain (revised 2026-09-19 — a lamport budget, not a claim counter):** one claim per recipient per day (`Profile.last_faucet_day`), and a **daily budget of 0.72 SOL on every lamport that leaves the faucet vault** — recipient top-ups, sponsor fee and rent reimbursements, and `top_up_sponsor` refills alike. Counting claims was not enough: a recipient already holding 0.02 SOL needs no top-up, so under a claim counter the rent and fee reimbursed for its brand-new `Profile` and token account left the vault uncapped, and the same recycled 0.02 SOL could pay the account-creation costs of fresh wallet after fresh wallet. Budgeting lamports closes that and keeps one day's abuse at ≈ 0.72 SOL however it is spent. A claim that moves no lamports at all — the recipient pays its own fee and already holds the top-up, as every scripted wallet does (R12) — consumes none of the budget and is not capped. When the budget or the vault is exhausted, `claim_faucet` fails with a specific error and the app shows ES-0b.
  - **Scripted wallets** are pre-funded with SOL once by the Operator and pay their own fees, so their claims mint SKR only (R12).
  - **Sizing (revised 2026-09-17 for the live period through 11 November; the stored values are normative in data-model §1.1):**
    - **Worst case per SOL-paying claim ≈ 0.024 SOL:** the 0.02 top-up, plus the profile and SKR token account rent reimbursed to the sponsor for a brand-new wallet.
    - **Funding deadline:** the vault may be topped up by hand until submission. From 13 October (moved from 9 October, 2026-10-09) it must last to 11 November with no top-up (FR-042a).
    - **Amount:** 34 days × 0.72 SOL ≈ **24.5 SOL**, so the vault is funded to **25 SOL by 12 October** (was 8 October; the live period is now 30 days, so 25 SOL leaves more margin).
    - **Why 0.72 SOL a day is enough:** at the worst case it is 30 brand-new wallets a day, well above expected judge and community traffic, and one claim funds a wallet for a whole 21-day cycle. Refilling a drained sponsor draws on the same budget, so a griefer must spend the whole day's allowance to stop onboarding for the rest of that day.
- **Rationale:** meets FR-042a and FR-042b — no server, no public faucet, no manual top-up — within Solana's fee rules. A sponsor key in the APK is a devnet-only trust gap: anyone can extract it, but it holds only a 0.05 SOL float, cannot touch a stake, a pool or a carry-over, cannot be locked out of use because anyone may refill it, and the daily lamport budget bounds what it can cost in a day.
- **Must be documented** in the README's known weaknesses (constitution XI): "the faucet's fee sponsor key ships in the app; a daily lamport budget caps abuse at one day's faucet allowance, and anyone can refill the sponsor if it is drained."
- **Alternatives considered:**
  - **The public devnet `requestAirdrop` for the first SOL.** Rate-limited and often failing — the reason H22 exists.
  - **A relayer server that co-signs.** A server, rejected by H22.
  - **Requiring members to bring devnet SOL.** Breaks FR-042a.
  - **Keeping the cap at 60 a day.** About 49 SOL for the longer live period.
  - **A claim counter as the only cap** (the plan until 2026-09-19). Left every reimbursement that needed no top-up outside the cap.
  - **Leaving a drained sponsor to the Operator.** Manual intervention during the live period, which FR-042a forbids.
  - **Making the recipient always pay its own claim fee.** That is exactly the zero-SOL case the faucet exists for.
  - **A larger sponsor float, refilled by hand.** More to steal and nothing gained: the float only has to cover the fees between two crank passes.
  - **A smaller 0.01 SOL top-up to stretch the vault.** Wallets may add priority fees to member transactions, and 21 days of them could exceed it.
- **Resolves:** P11.

## R14 · Fee destination and privileged keys

- **Decision:**
  - `Config.fee_destination` is the **operations** destination: an SKR token account owned by the treasury wallet, set at initialisation. `Config.developer_destination` is the **developer** destination, set at the same time and named in the README with what each is for (FR-037f). The 30 % fee on a forfeited stake is split by program rule — 5 % to the league's boost reserve, 5 % to its comeback reserve, 10 % to the developer destination, 10 % to operations (FR-027, H35; re-split 2026-10-09) — and operations also receives the rest of a no-finisher pool after the developer's floored half (FR-037) and the rounding remainders (FR-032a).
  - `Config.operator` may only `publish_chip_set`.
  - `Config.admin` may only `initialize`, `create_league` and `set_flags` (the genesis gate). `create_league` creates the two real leagues at launch, and a `league_id ≥ 100` test league only in builds carrying the `test-leagues` feature — one instruction, not two.
  - **Who may `initialize` (added 2026-09-19):** the program's **upgrade authority**, verified inside the instruction against the program's `ProgramData` account, and it becomes `Config.admin`. Before `Config` exists there is no stored key to check a signer against, so a deployment left uninitialised for even a moment could be claimed by a stranger who would then hold `admin`, `operator`, `sponsor` and the fee destination — and the program ID, which the app and the README both name, could not be reclaimed. Binding to the upgrade authority needs no constant in the source, works the same on the rehearsal and judged deployments, and anyone can verify it from chain state. **Ordering consequence:** `initialize` must run before the authority is revoked (FR-007b, README weakness); revoking first would leave the program permanently uninitialisable. Rejected: a hardcoded admin pubkey (a second constant to keep in step with two deployments, and it leaks a key into the source); trusting the deploy-then-init window to be short (it is a race the attacker picks the timing of).
  - **No instruction** lets any key move a held stake, a pool or a carry-over except the rule-driven instructions in `contracts/program-instructions.md`.
  - **Program upgrade authority** (added 2026-09-17, FR-007b) is held by the admin key and kept through the live period, used only to deploy defect fixes.
    - An upgrade never changes an existing account layout, a rule an existing cohort runs under, or any fund movement; a fix that would need any of those is not deployed.
    - The README names the authority as a known weakness, with its fix: revoke it once the contract is final.
    - Rejected: revoking it at the freeze. A defect found after 28 September would be unfixable while cohort #3 carries the walkthrough.
- **Rationale:** constitution I — the contract is the only authority on funds; the only discretionary powers are the chip set and the documented flags.
- **Alternatives considered:** fees to a program-owned treasury (nobody can spend them; nothing gained); an admin rescue withdrawal (violates constitution I).
- **Resolves:** P4.

## R15 · Chip set tooling (Phase 1 and 2)

- **Decision:**
  - **Phase 1:** `crank chips publish --rate <USD per SKR>` converts $10/$25/$50/$100 to SKR base units, rounded **down to the nearest 10 whole SKR** (minimum 10 SKR), and sends `publish_chip_set` once at launch. The rate is read by hand from a public SKR market price on launch day and recorded in the README — **never on-chain**, so the contract holds no fiat figure at all.
  - **Phase 2** *(decided 2026-10-06)*: `crank chips publish --auto`, a **standalone Operator command** run **every hour** by the Operator's OS scheduler, in any time zone (amended 2026-10-07) — never inside `crank run`, so the Operator key is loaded only for that run. The rate comes from the **Jupiter Price API only** (keyless, by the mainnet SKR mint); with no rate it exits without publishing (FR-057). It **re-prices every week** from that rate — no drift threshold, and a new version even when the rounded amounts are unchanged (the threshold, measured on the $100 tier, was dropped the same day). It publishes **at most once per publish window** *(amended 2026-10-10)* — the 4 hours before each Monday 05:00 UTC boundary, by chain time: a run outside it, or a `Config.chip_published_at` already inside it, means nothing to do. The first hourly run inside the window publishes; a run that fails or finds no rate is retried by the next one inside it. The cohorts opening at the boundary copy that set, so they lock a rate at most 4 hours old; publishing on the first run *after* 05:00, as first built, reached only the following week's cohorts. No send starts in the window's last 90 seconds — about a blockhash's life, and the send is watched for 60 s — and the chain's time is read again just before sending, since the rate fetch can take 30 s: a set landing after 05:00 would likewise reach only the next week's cohorts *(added 2026-10-10, PR #30 review)*. A blockhash lasts about 150 blocks, not a fixed time, so the cutoff alone could not promise that; `publish_chip_set` therefore carries a deadline, `valid_until`, and refuses a set at or after it (`ChipSetExpired`, 6014): the weekly re-price signs the window's close, a hand publish chain time + 10 minutes unless `--valid-until` says otherwise. A time, never a price (constitution III). Hourly, not once on Monday, because `CRON_TZ` is not portable (Debian cron ignores it) and a local-time Monday run can land before 05:00 UTC or shift with daylight saving. Two overlapping runs could both publish before either lands; with one Operator key the worst case is a near-identical duplicate set that week, so the race is accepted rather than closed with a program change.
- **Faucet interaction (added 2026-09-19):** a re-price may lower the tiers while a cohort that copied the older, larger set is still open. `Config.chip_set.max_top_tier` is therefore a high-water mark — `max` of every `amounts[3]` ever published — and the faucet mints that rather than today's top tier. Since no cohort can be created before a chip set exists (FR-055b) and the mark never decreases, it is at least the top tier of every cohort that exists, so FR-042a holds whatever order publishing and opening happen in. Minting more than today's tier costs nothing: the program mints mock SKR per claim.
- **Rationale:** readable amounts; the contract never sees a price (constitution III); Phase 2 is pure tooling on an instruction Phase 1 already has.
- **Alternatives considered:** an on-chain oracle (forbidden by constitution III).
- **Resolves:** P3.

## R16 · Completed-day proof

- **Decision:** `submit_day` is signed by the member's wallet through MWA and carries a 32-byte `proof_hash` — SHA-256 of the day's verification summary (day index, rep count, block times, sitting count, model version). The hash is emitted in the `DayCompleted` event, not stored in an account.
- **Rationale:** matches scope doc D-E (client-signed, documented trust gap) at zero storage cost; a future attestation signer can check traces without a migration.
- **Alternatives considered:** an attestation account per day (rent for 21 days × 1,000 members, for a hash nothing verifies on devnet).

## R17 · Genesis Token gate (flag off)

- **Decision:** the gate check exists in `join_cohort` behind `genesis_gate_enabled` (false on devnet): when enabled it requires a Token Extensions account owned by the member, holding one token whose mint is a member of the Seeker Genesis Token group. The verification helper is unit-tested with a fake group; it is never exercised on devnet.
- **Rationale:** constitution Hackathon Constraints — flags disable checks, never delete the logic.
- **Alternatives considered:** omitting the check (violates the flag rule).

## R18 · Recorded walkthrough — superseded 2026-10-09

**Superseded (spec 004, constitution v2.1.0):** the app carries no walkthrough and no Media3. The submission is judged as the app's final state, so the full cycle is shown in the **demo video, sent as a link with the submission**, recorded from the real app on a cohort of the short test lobby (FR-044, FR-044a). The 9:16 in-app export and the 40 MB budget are dropped. What follows is kept as the record of the earlier decision.


- **Decision (revised 2026-09-17):** a screen recording of the real app on the judged devnet program, taken **only from cohort #3**, the first judged 7-day cohort (FR-044). The same footage serves both the in-app walkthrough and the submission's demo video (FR-066).
  - **Two wallets on two devices:** a **finisher** on the Seeker and a **forfeiter** on the second phone. Both join cohort #3 through the release-candidate app before its join cutoff, **28 September 04:00 UTC**.
  - **Shots:**
    1. Join and lobby, by 27 September.
    2. The locked hour.
    3. The finisher's daily sittings, 28 September – 4 October.
    4. The forfeiter missing a day, then its forfeit screen.
    5. The survivor counter falling as scripted members forfeit.
    6. Settlement at 5 October 05:00 UTC.
    7. The finisher's real claim, with the SKR balance change.
  - **The cut runs the whole flow in order** (FR-044): launching the app, connecting a wallet, the faucet, home, staking, the lobby, a sitting, a completed day, the crowd thinning, a forfeit, settlement and the claim. Storyboard, screen checklist, shot log and beta-test plan: `docs/holdout_demo_video.md`.
  - **Editing, 5–7 October — two exports:**
    - **16:9 at 1080p** for the submission, about three minutes.
    - **9:16 at 720×1280, ~1.5 Mbps (≈ 34 MB)** for inside the app, shipped in the final APK as an MP4 played with **Media3 ExoPlayer**, within the 40 MB budget.
  - **Where it plays:** from the connect screen without a wallet, and from the app once connected (FR-001a, contracts/android-app §5). The player needs no network.
    - Scripted members, the test network and the named weaknesses stay disclosed in both (constitution XI, XII).
  - **Until the recording exists,** the screen says "Walkthrough recording in progress" — never a mock-up.
  - **Adding the recording is filming, not a feature change.** The final APK is the release candidate plus the video and bug fixes.
  - **Fallback:** a contract defect that blocks settlement or the claim is fixed by upgrade (FR-007b, R14). If the claim still can't be filmed by 8 October, the submission ships with "recording in progress" in the app, and the demo video stops at the last real step and says so. No shortened-day footage is used.
- **Rationale:**
  - Works offline for a judge, with no video host, and without a wallet — the case where a judge has no devnet-capable wallet is exactly when the recording matters.
  - Cohort #3 is the only judged cohort that settles before the 9 October deadline.
  - Judges score completion from the demo video, so every Phase 1 step it shows must be the real one.
- **Alternatives considered:**
  - **Streaming from a video host.** An external dependency.
  - **An animated mock.** Forbidden by FR-044.
  - **Cohort #4.** It settles 12 October, after submission.
  - **A labelled shortened-day rehearsal cohort.** Rejected in the 2026-09-17 clarification.
- **Resolves:** P6.

## R19 · Repository layout and tooling

- **Decision:** one repository with three build systems, each doing one job, on two environments:
  - **Cargo workspace** — `programs/holdout` (Rust, Anchor 1.1.2). Built in **WSL2**.
  - **npm** — `tests/` (Kit + LiteSVM integration tests, Codama client generation, fixture generation), Node 22 LTS (≥ 20.18). Run in **WSL2**.
  - **Gradle** — `kotlin/`, created from the Solana Mobile CLI template (R3) and extended with `core` (JVM library), `repcounter` (JVM library) and `crank` (JVM application) next to the template's `app`. Versions only in `gradle/libs.versions.toml`. Built on **Windows** with Android Studio or in WSL2.
  - **Tooling:** Kotlin 2.4.10, JDK 21 daemon / Java 17 bytecode, JUnit + Kotest assertions for JVM modules, Compose UI tests for key screens; ktlint + detekt; `cargo fmt` + Clippy.
  - **Agent tooling:** the **solana-dev skill** installed in this repository (`npx skills add solana-foundation/solana-dev-skill`, as in the reference project) and the **Solana MCP server** (`https://mcp.solana.com/mcp`) for current docs. Anchor and Surfpool commands run by agents are prefixed with `NO_DNA=1` for non-interactive output.
- **Rationale:** smallest setup that lets the app and crank share one Kotlin client while the program is tested with the Solana Foundation's recommended tooling; WSL2 is required because the Solana toolchain has no native Windows binaries.
- **Alternatives considered:** Kotlin Multiplatform (no second target needs it); a TypeScript crank (see R12).

---

## R20 · First-run tour and the design tokens in Compose

- **Decision:** the tour (FR-067) is **three Compose pages in a `HorizontalPager`** inside `kotlin/app/.../tour/`, shown after the first successful connect and re-openable from About Holdout. A `tour:{wallet}` MMKV record marks it seen (data-model §4). It carries **no video dependency at the freeze**: the short explainer (R18) drops into card 2 when it exists, and the cards stand alone until then.
- **Theme:** PRD §11 tokens are transcribed once into `ui/theme/` — `Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt` — as a single dark `ColorScheme` with **dynamic colour disabled**. System Roboto for UI; **JetBrains Mono bundled in `res/font/`** for amounts and countdowns. No other font ships.
- **Rationale:** the tour is the only onboarding a member gets (H29; there is no in-app walkthrough since 2026-10-09), and it must be finished before the app slice ends on 26 September — a pager over three static pages is hours of work, not days. Keeping the tokens in one file makes the design freeze on 20 September enforceable.
- **Alternatives considered:** a video-only explainer (nothing to ship at the freeze, since cohort #3 is not filmed until October); an interactive coach-mark overlay on the real screens (more code, and it fights the camera screens); Material You dynamic colour (recolours the brand on every device).

## R21 · On-device reminders (added 2026-09-27)

- **Decision:** reminders (FR-068) are **local notifications scheduled by the app with `AlarmManager`**, no server:
  - **Clock:** alarms are set on `ELAPSED_REALTIME_WAKEUP`, converted from the cohort's chain-time instants through the same `ChainClock` reading the screens use (R7) — so the device's wall clock never decides when a reminder fires (FR-017, constitution VI).
  - **Precision:** `setWindow` with a bounded window — a third of the lead, at least the 10 minutes Android allows without the exact-alarm permission — so a reminder always arrives before its deadline: 15 to 5 minutes before a one-hour day's, 3 to 2 hours before a 24-hour day's. A 10-minute test day (H32, 2026-09-29) is reminded as it opens, and the reminder may come at any point of the day. *(Changed 2026-09-27: `setAndAllowWhileIdle` let the Seeker hold a reminder about 57 minutes, past a one-hour deadline.) Doze holds a `setWindow` alarm until its next maintenance window, so each reminder is **also** set with `setAndAllowWhileIdle`, which Doze lets through, later; whichever fires first posts, the other stays quiet, and a reminder whose deadline has already passed posts nothing. An idle phone may therefore still get a reminder late, or not before the deadline. No `SCHEDULE_EXACT_ALARM`: that is a special permission the member would have to grant in settings, for a reminder that is a courtesy (FR-068a).
  - **What is scheduled:** the start of each Holdout the member holds a place in, and for every day not yet on chain, `deadline − min(3 h, max(day length ÷ 4, 10 min), day length − 1 s)` — for a 10-minute day, as it opens; its first day is left to the start reminder. The schedule is **recomputed from chain state on every refresh** (R8) and on app start: joins, leaves, completed days and forfeits simply change what the next computation produces — there is no separate schedule to keep in sync. Past instants are skipped. *(Amended 2026-10-10, FR-068.)* A day of 12 hours or more also gets a **Midday** reminder at `deadline − day length ÷ 2` — 17:00 UTC for a 24-hour day, an evening in Europe and a working day in the Americas — posted on the sounding channel, and its **Deadline** reminder is then posted on a second, **silent** channel (`IMPORTANCE_LOW`: no sound, no vibration, shown in the shade), because 3 hours before 05:00 UTC is the middle of the night in Europe. Shorter days keep their one sounding reminder. Chain time only; no timezone or locale (constitution II).
  - **At fire time:** the receiver reads the phone's own record (MMKV, data-model §4). A day completed or waiting to submit is still reminded, to be **submitted** — it counts for nothing until it reaches the chain, and a failed submit is exactly when a reminder helps. A day that reached the chain was already cancelled by the app's next sync. *(Changed 2026-09-27: it used to stay silent for a day done on the phone.)*
  - **Permission:** `POST_NOTIFICATIONS` (runtime on Android 13+), asked once — at the first join, or the first time the app finds the member already in a Holdout (someone who joined before reminders existed) — with the reason on the stake sheet; declining is remembered and never asked again unprompted.
  - **Reboots:** `ELAPSED_REALTIME` restarts with the phone and a chain reading is valid for one boot only (R7), so alarms are not restored by a boot receiver; they come back the next time the app reads the chain (FR-068a). No `RECEIVE_BOOT_COMPLETED`.
- **Rationale:** it is the whole of what a member needs — "you're on, and today isn't done" — with nothing new trusted, stored or sent: constitution X holds (no data leaves the device), the build stays server-free, and chain time stays the only clock.
- **Alternatives considered:**
  - **Firebase Cloud Messaging or any push service:** a server that knows each member's cohort and deadline, plus Google Play services — against the server-free build, for the same messages.
  - **WorkManager periodic work:** a 15-minute minimum period and no timing guarantee; fine for a daily nudge, too loose before a one-hour test day's deadline.
  - **Exact alarms:** a settings-screen permission for a convenience; rejected (above).
- **Weakness, named in the README:** reminders can arrive a few minutes late, not at all after a reboot until the app is opened, and never if notifications are declined — the deadline is the chain's (constitution XI).

## Spikes that confirm this research

| Spike | Confirms | Pass bar | If it fails |
|---|---|---|---|
| **S1 · Wallet on real hardware** | R3, R12, R13 | The Kotlin Compose Minimal template (fixed root build file) builds; on a Seeker it connects Seed Vault Wallet via MWA and lands a devnet transaction with `signAndSendTransactions`; a sponsor-paid transaction built with `web3-solana` + `rpc-core` lands too; `NoWalletFound` is returned on a phone with no wallet; `web3-solana` and `rpc-core` also run in a plain JVM module (for the crank); **`scrcpy --record` captures the app, and whether the wallet's approval screen records or comes out black (`FLAG_SECURE`)**, which decides whether those moments need the second camera (R18) | Try another MWA wallet on a plain Android phone; if a library blocks, or doesn't run on the plain JVM, use sol4k for transaction building in that module; film blocked screens with the second camera |
| **S2 · Rep counting on real video** | R4, R5 | ≥ 15 FPS pose inference on a Seeker; correct count on 10 real push-up videos (≤ 1 miscounted rep in 25) and rejection of 3 cheat videos (half reps, sagging hips, too fast); open palm detected in normal indoor light | Lite model; lower analysis resolution; CPU delegate; relax thresholds with recorded evidence |
| **S3 · Full cycle on a manipulated clock** | R1, R2, R9, R10 | In WSL2 with Anchor 1.1.2 / Solana CLI 3.1.10, on LiteSVM: open → join (incl. lobby place) → lock → daily submits → forfeits in out-of-order repeated pages → count → finalize with remainder → claim, for a 1,000-member cohort; vectors V1–V22 pass; Kotlin `core` replays the fixtures | Reduce page size; add address lookup tables |

### S1 · passed 22 September 2026

R3, R12 and R13 are confirmed on the device of record. No fallback was needed:
the template's libraries work as researched, and nothing moves to sol4k.

| Leg | |
|---|---|
| Seeker, Seed Vault Wallet | **Passed.** Connect, `signMessagesDetached`, and `signAndSendTransactions` landing a devnet memo — signature `3PN4nN1L…fJwzzL`, slot 502,517,648, 22 Sep 16:46 UTC, signer `CkJn1a53c1ZXe5XLZ38ZgsPa1WX28PUiMWHqtJayjfWE`, no error. Shot log S-01 |
| `web3-solana` + `rpc-core` on a plain JVM | **Passed**, and kept as a test: `kotlin/core/.../S1JvmSpikeTest.kt` builds a transaction, derives the `mint_authority` and `faucet_vault` addresses and reads a devnet blockhash with no Android on the classpath. The crank and the app can share one client (R12) |
| `scrcpy --record` | **Passed, and better than assumed.** The wallet's approval screen records — it is not `FLAG_SECURE`. The second camera is not needed for those seconds (R18, `docs/holdout_demo_video.md` §4) |
| A plain Android phone with its own MWA wallet | **Not run.** Compatibility for beta testers rather than a gate on the API; it is answered by the first tester install (T011) |
| `NoWalletFound` on a phone with no wallet | **Not run.** It is the acceptance criterion of T041, the no-wallet screen, and is checked there |

**Found on the way, and it constrains the code:** Seed Vault returned a
transaction carrying **two `ComputeBudget` instructions the wallet added
itself**. What `signAndSendTransactions` sends is therefore *not* byte-identical
to the message the app built. Nothing may assume it is — in particular a
completed-day proof (R16) must be over payload the app controls, never over the
serialised transaction, and any future check of "did the wallet send what we
asked" has to compare instructions, not bytes.

## Sources checked for this research (September 2026)

- [LiteSVM — Anchor docs](https://www.anchor-lang.com/docs/testing/litesvm) · [Time travel testing with LiteSVM](https://rareskills.io/post/litesvm)
- [Solana Mobile — Kotlin quickstart](https://docs.solanamobile.com/android-native/quickstart) · [Using Mobile Wallet Adapter (Kotlin)](https://docs.solanamobile.com/android-native/using_mobile_wallet_adapter) · [Working with Anchor programs in Kotlin](https://docs.solanamobile.com/android-native/using-anchor-programs) · [solana-kotlin-compose-scaffold](https://github.com/solana-mobile/solana-kotlin-compose-scaffold) · [mobile-wallet-adapter-clientlib-ktx](https://javadoc.io/doc/com.solanamobile/mobile-wallet-adapter-clientlib-ktx/latest/index.html)
- [MediaPipe Pose Landmarker for Android](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android)
- [SKR token launch (SPL asset)](https://thedefiant.io/news/tokens/solana-seeker-launches-skr-token-airdrop)
- Local reference project `D:\dev\Hackaton Solana Mobile\fitbet` — Solana Mobile CLI "Kotlin Compose Minimal" template (build files, version catalog, MWA view model) and the bundled solana-dev skill v2.4.0 (compatibility matrix, testing strategy, IDL codegen)
- [Solana Mobile templates](https://github.com/solana-mobile/templates) · [create-solana-dapp](https://github.com/solana-foundation/create-solana-dapp) · [Solana Mobile CLI — create a project](https://docs.solanamobile.com/get-started/react-native/create-solana-mobile-app)
