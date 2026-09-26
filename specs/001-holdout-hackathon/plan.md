# Implementation Plan: Holdout hackathon build

**Branch**: `001-holdout-hackathon` | **Date**: 2026-09-15, revised 2026-09-17 for the CLOCK IN rules | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-holdout-hackathon/spec.md`

## Summary

A fitness commitment app on Solana devnet: **the Daily 25** — a free daily push-up set with a cosmetic streak — under staked **Holdouts** of 7 or 21 days, 25 reps a day (members' names; the contract stores them as cohorts in two leagues, spec §1a) where missed days forfeit stakes into a per-cohort prize pool that finishers claim. Everything that decides money lives in one **Rust/Anchor program**; every deadline is derived from chain time; automation is permissionless and idempotent. Members use a **native Android app in Kotlin** (Jetpack Compose, Mobile Wallet Adapter, MediaPipe Tasks on-device pose detection) that banks verified reps in blocks of 10 on the device and submits a completed day on-chain. A **Kotlin/JVM crank** shares the app's core module to open cohorts, record forfeits, settle, drive scripted members and publish chip sets. The contract is tested first in **TypeScript on LiteSVM** with clock control; golden fixtures keep the Kotlin client byte-compatible.

Work follows the spec's phasing (spec §3a): **Phase 1** is the full loop and must work end to end before any **Phase 2** item (league leaderboards, weekly chip re-pricing, carry-over delivery, automatic offline submission) starts, and nothing lands after the 28 September freeze.

## Technical Context

**Language/Version**: Rust ≥ 1.89 with Anchor 1.1.2 and Solana CLI 3.1.10 for the program; Kotlin 2.4.10 (JDK 21 daemon, Java 17 bytecode) for the Android app, core library and crank; TypeScript on Node 22 LTS for program integration tests only.

**Primary Dependencies**:
- **Program:** Anchor 1.1.2 with the token interface, `solana-*` crates on `^3` (research R1).
- **Android app** — generated from the Solana Mobile CLI's **Kotlin Compose Minimal** template (R3):
  - **Build:** AGP 9.3.2 with Gradle 9.7.1; Compose BOM 2026.08.00.
  - **Solana:** `mobile-wallet-adapter-clientlib-ktx` 2.2.0, `web3-solana` 0.3.1, `multimult` 0.2.6, plus `rpc-core` (version pinned at scaffold).
  - **Camera and device:** MediaPipe Tasks Vision (Pose Landmarker, Hand Landmarker), CameraX, MMKV for Android, Media3 ExoPlayer (R4–R6, R18).
- **Tests:** `@solana/kit` with `@solana/kit-plugin-litesvm` (LiteSVM 1.3.x) and a Codama-generated Kit client; a Surfpool smoke suite before deploys (R2).
- **Crank:** `kotlin/core` (`web3-solana`, `rpc-core`) plus a CLI argument parser (R12).

**Storage**:
- **On-chain:** program accounts — `Config`, `LeagueConfig`, `Cohort` and its vault, `Membership`, `Profile`, the faucet vault (data-model §1).
- **Device:** MMKV key-value records for banked reps, pending submissions, streak and cached chain time (data-model §4). No server database.

**Testing**:
- **Program:** `cargo test` for the pure settlement math (PRD §19 vectors V1–V22 plus property tests); Kit + LiteSVM integration tests for every scenario, including the randomised INV-1…INV-9 suite at 1,000 members; a Surfpool smoke run (`anchor test`) with time travel before each devnet deploy.
- **Kotlin:** JUnit 5 for `core` (golden fixture replay) and `repcounter` (recorded landmark traces, including cheats); Compose UI tests for key screens.
- **Manual device checks:** quickstart §8.

**Target Platform**: Solana devnet; Android 8.0+ (minSdk 26, target/compile SDK 37, matching the template), primary device Solana Seeker with Seed Vault Wallet; the crank on a JVM (Fly.io scheduled machine).

**Development environment**: Windows 11 with **WSL2 (Ubuntu 24.04)** for everything Solana — Rust, Anchor, Solana CLI, LiteSVM and Surfpool have no native Windows binaries (GLIBC ≥ 2.39 required). Android Studio on Windows for the app. Agent-run Anchor and Surfpool commands use `NO_DNA=1`.

**Project Type**: Mobile app with an on-chain program and a CLI automation tool (no web backend).

**Performance Goals**:
- **Pose inference:** ≥ 15 FPS on a Seeker, with a counted rep appearing within 300 ms of returning to the top.
- **Transactions:** a submitted completed day confirms on devnet within 10 s (p95); cold start to the connect or home screen < 3 s.
- **Cohort data:** a 1,000-member roster loads in < 5 s.
- **Automation:** the crank records every forfeit of a 1,000-member cohort within 15 minutes after a boundary.

**Constraints**:
- **Camera privacy:** frames never leave the device, and there is no microphone permission.
- **Server-free:** no Holdout server decides deadlines, outcomes, rankings or faucet payouts.
- **Price-free contract:** no instruction reads a price, and the contract stores no fiat figure.
- **Chain time:** every deadline is judged by chain time, never the device clock.
- **Offline:** sittings and banking work without a connection.
- **Deadlines:** feature freeze 28 September 2026; submission (APK, GitHub repository, demo video of about three minutes, pitch deck) before 9 October 2026 08:59 CEST (FR-066).
- **Live period:** no manual intervention from submission through the winners' announcement on 11 November 2026 (FR-042a, FR-043).
- **Upgrades:** the contract stays upgradeable for defect fixes only (FR-007b, R14).

**Scale/Scope**: two leagues; weekly cohorts of up to 1,000 members each (up to three 21-day cohorts running at once); real members first, topped up by a fixed pool of 56 scripted wallets to a floor of 20 members per 7-day cohort and 12 per 21-day cohort (R12); about 14 app screens with variants, the first-run tour included (PRD §10.1, FR-067); judging 10 October – 9 November 2026.

No `NEEDS CLARIFICATION` items remain — all are resolved in [research.md](research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate | Pre-research | Post-design evidence |
|---|---|---|---|
| **I. Contract is the only authority on funds** | No off-chain key can move a stake, pool or carry-over | PASS | No admin/operator/sponsor withdrawal exists; funds move only in rule-driven instructions (contracts/program-instructions §1–3); the faucet vault holds only devnet gas SOL and minted test SKR, never stakes. The program upgrade authority is the constitution's named hackathon exception — defect fixes only, never a fund movement or a rule change for an existing cohort (R14) |
| **II. Forfeiture is derived** | Breach = `now > deadline_of(days_completed)` from chain state; automation only materialises it | PASS | `in_breach` guard in `submit_day` and `record_forfeits`; displays derive the same formula (data-model §1.3, §5; R7) |
| **III. No oracle in contract logic** | No price read; no fiat input to any on-chain rule | PASS | The rate exists only in `crank chips publish` and the README; `publish_chip_set` takes SKR amounts only (R15) |
| **IV. SKR only** | Stakes, pool, payouts and fees are SKR | PASS | Every token movement uses the SKR mint; devnet SOL appears only as transaction fees and rent (R13) |
| **V. Parameters live on the cohort** | Start, cutoff, duration, reps, day length, boundary and chips fixed at creation | PASS | Copied into `Cohort` by `open_cohort` from `LeagueConfig`/`Config`; never read from constants (data-model §1.3) |
| **VI. One clock** | Single 05:00 UTC boundary from chain time; countdown, never dates | PASS | Cohort start aligned to Monday 05:00 UTC; `deadline_of` lands on the boundary; streak and faucet day use `floor((now − 18,000) ÷ 86,400)` |
| **VII. Tests precede logic** (non-negotiable) | Vectors and invariants exist as failing tests before instructions | PASS | Delivery sequence step 1 writes `fixtures/settlement-vectors.json`, the Rust math tests and the invariant harness before any instruction body |
| **VIII. Idempotent, chunkable automation** | Safe twice, safe paged, two callers harmless | PASS | Per-membership state and `counted` flags; pages in any order; permissionless (R9, FR-030a) |
| **IX. Every state has a screen** | Each state reachable in UI or provably unreachable | PASS | State → screen table in contracts/android-app §5, first-run tour (FR-067) and the About/judges route included |
| **X. Camera frames never leave the device** | No upload, persistence or server inference | PASS | Network egress limited to RPC and the local wallet (contracts/android-app §2); no `RECORD_AUDIO`; AC-10 traffic check |
| **XI. Weaknesses documented** | Everything faked, stubbed, flagged or trusting is named in the README | PASS — **action required** | Two trust gaps the README must name, with their fixes: the faucet's fee **sponsor key ships in the APK** (R13), and the **program upgrade authority** is kept (R14). The demo video and pitch deck must not hide them (follow-ups below) |
| **XII. The build demonstrates itself** | Live populated cohort any day of the live period; free challenge immediately; scripting disclosed in app, README, demo video and deck | PASS | Permissionless weekly opening; crank every 10 min; real members first, with scripted wallets filling each cohort only to its floor (R12); faucet funded to 11 November (R13); walkthrough from cohort #3 in the APK, shared with the demo video and playable before a wallet is connected (R18, FR-001a) |
| **XIII. Honest language** | "Prize"; no money on leaderboards; fiat approximate; test network stated | PASS | Copy per PRD §13; roster and boards carry no amounts (contracts/android-app §6) |
| **Hackathon constraints** | Devnet only; signed APK, no store listing; named flags; feature freeze; submission deliverables by 9 October; upgrade authority for defect fixes only; out-of-scope list; leaderboards on-chain only | PASS | Flags in `Config`; Phase 2 cut at the freeze; test leagues confined to `test-leagues` builds and a separate rehearsal deployment; GitHub repository judges can clone, pushed from the first commit; deliverables and final APK in the delivery sequence; upgrade rule in R14 |
| **Development workflow** | Stack in the plan, not the spec; risk spikes before implementation | PASS | Spec unchanged; spikes S1–S3 are the first delivery step |

**Gate result: PASS.** No violations; nothing needs complexity justification.

## Project Structure

### Documentation (this feature)

```text
specs/001-holdout-hackathon/
├── spec.md
├── plan.md                         # This file
├── research.md                     # Phase 0: decisions R1–R19, spikes S1–S3
├── data-model.md                   # Phase 1: accounts, validation, invariants, device records
├── quickstart.md                   # Phase 1: build, run and acceptance validation
├── contracts/
│   ├── program-instructions.md     # On-chain interface, events, errors, PDAs, fixtures
│   ├── crank-cli.md                # Automation / scripted members / chips CLI
│   └── android-app.md              # Permissions, egress, wallet use, state → screen
└── tasks.md                        # Created by /speckit-tasks
```

### Source Code (repository root)

```text
Anchor.toml                         # anchor 1.1.2; [test] runner = surfpool (smoke only)
Cargo.toml / Cargo.lock             # workspace; lock committed (compatibility matrix)
.agents/skills/solana-dev/          # solana-dev skill (npx skills add …), linked into .claude/skills
skills-lock.json

programs/holdout/
├── Cargo.toml                      # anchor-lang/anchor-spl 1.1.2, solana-* ^3; features: test-leagues
└── src/
    ├── lib.rs
    ├── state/                      # config.rs, league.rs, cohort.rs, membership.rs, profile.rs
    ├── instructions/               # initialize, create_league, set_flags, publish_chip_set,
    │                               # open_cohort, record_forfeits, settle_count, settle_finalize,
    │                               # deliver_carry_over [Phase 2], claim_faucet,
    │                               # top_up_sponsor, join_cohort, leave_cohort,
    │                               # submit_day, claim_payout
    ├── math/                       # settlement.rs, schedule.rs — pure, no Anchor types
    ├── guards.rs                   # in_breach, status, slot normalisation, gates
    ├── events.rs
    └── errors.rs

idl/holdout.json                    # Anchor IDL from the build

tests/                              # TypeScript (runs in WSL2): Kit + LiteSVM
├── package.json                    # @solana/kit, @solana/kit-plugin-litesvm, litesvm, codama, vitest
├── clients/holdout/                # Codama-generated Kit client (committed)
├── helpers/                        # clock control, cohort builders, invariant checker
├── smoke/                          # Surfpool + time travel, run before devnet deploys
├── scenarios/                      # one file per acceptance criterion (contract side)
├── invariants/                     # randomised INV-1…INV-9, 1,000-member cohort
└── fixtures/generate.ts            # writes ../fixtures/*.json

fixtures/                           # committed golden JSON (contracts/program-instructions §7)

scripts/                            # repo checks run in CI
└── check-no-oracle.sh              # fails if any instruction names a price or oracle (constitution III)

rustfmt.toml                        # Rust formatting

kotlin/                             # Gradle root generated by `npx solana-mobile@latest create`
│                                   # (Kotlin Compose Minimal); stray root dependencies removed
├── settings.gradle.kts             # include(":app", ":core", ":repcounter", ":crank")
├── gradle/libs.versions.toml       # the only place versions live
├── core/                           # JVM: account codecs, PDAs, instruction builders,
│                                   # Derive.kt, RPC reads, scripted-wallets.json
├── repcounter/                     # JVM: pre-flight checks, palm rule, push-up state machine
├── app/                            # Android: Compose UI, CameraX + MediaPipe, MWA, MMKV
│   └── src/main/kotlin/…/
│       ├── wallet/  faucet/  home/  ladder/  lobby/  sitting/
│       ├── cohort/  roster/  claim/  streak/
│       ├── tour/                     # first-run cards, FR-067
│       ├── about/                    # judges: walkthrough player, scripted list, weaknesses
│       ├── ui/theme/                 # Color.kt, Type.kt, Shape.kt, Theme.kt — PRD §11 tokens
│       └── leaderboard/            # [Phase 2]
└── crank/                          # JVM CLI: run, scripted, chips, init, status

.github/workflows/crank.yml         # fallback schedule, disabled by default
fly.toml                            # crank scheduled machine
```

**Structure Decision**: one repository, three build systems with one job each. **Cargo** builds the program (WSL2). **npm** exists only to test the program on LiteSVM, generate the Codama client and generate the fixtures (WSL2). **Gradle** — seeded from the Solana Mobile CLI template — builds everything that runs in production off-chain: the Android app and the crank, sharing `kotlin/core` so there is exactly one Kotlin client implementation, pinned to the program by committed fixtures.

## Delivery sequence

**Re-planned 17 September 2026** against the official CLOCK IN dates: submission due 9 October 08:59 CEST (06:59 UTC), judging 10 October – 9 November, winners announced 11 November. The spikes had not started by 17 September, so they now run alongside the tests-first step instead of before it, and the judged launch moves from 20–21 to 22–23 September. The hard gates are fixed by cohort #3: its lobby must be live and the app's join flow working before its join cutoff (**28 September 04:00 UTC**), because the walkthrough and demo video can only come from it (R18). Each step ends with its tests green.

| Window | Step | Done when |
|---|---|---|
| **17–18 Sep** | **Setup and tests first:**<br>• WSL2 toolchain (R19).<br>• Anchor workspace and Kotlin template scaffolded, with its defects fixed (R3).<br>• GitHub repository created and pushed from the first commit, since judges read the commit history (FR-066).<br>• `fixtures/settlement-vectors.json`, the Rust math tests, and the LiteSVM harness with clock control and invariant checker, all failing.<br>• **Spike S1** (MWA signing on a Seeker and a plain phone, including which wallets sign devnet transactions) and **spike S2** (rep counting on real video) start. | Failing tests committed and pushed; S1 answered or its fallback chosen |
| **18–21 Sep** | **Program, Phase 1:**<br>• Schedule and settlement math; accounts; all Phase 1 instructions; fixture generation.<br>• Kotlin `core` replaying the fixtures.<br>• **Spike S3** (full cycle at 1,000 members on LiteSVM). S2 continues on device. | Contract-side acceptance scenarios and the 1,000-member invariant suite pass; S2 and S3 passed or fallbacks chosen |
| **18–20 Sep** | **Design, alongside the program:** prototype the four hero screens — home in a cohort, the sitting while counting, stake confirm, claim — from the PRD §11 tokens; critique and accessibility pass; then **freeze the tokens** and write the Compose theme (`kotlin/app/.../ui/theme/`: colours, type, shapes, no dynamic colour). App icon, wordmark and splash drafted | The prototype reads as a Seeker-native app beside the device's own screens; tokens frozen before the app slice starts |
| **21–22 Sep** | *(Superseded 2026-09-26: the devnet program itself carries `test-leagues` and hosts the one disclosed short test cohort, FR-044a; the judged leagues start 5 Oct, and the freeze moves with them.)* **Rehearsal deployment:** a separate program ID with a test league; Surfpool smoke suite | A compressed cycle runs on devnet; deploy rent measured for the SOL budget |
| **22–23 Sep** | **Devnet launch of the judged program:**<br>• Deploy, keeping the upgrade authority (R14).<br>• `crank init` with leagues starting **28 Sep 05:00 UTC**; `crank chips publish`.<br>• Faucet vault part-funded (topped up to 25 SOL by 8 Oct, R13).<br>• Cohort #3 opened so its lobby is live; crank scheduled on Fly.io.<br>• The 56 scripted wallets funded, filling cohort #3 to half its floor (R12). | `crank status` shows cohort #3 `Open` with scripted members joining; a second `crank run` changes nothing |
| **by 21 Sep** | **Recruit 5–7 beta testers** (Android phone, devnet-capable wallet per spike S1); group chat opened; consent asked from anyone who may appear in the footage (`docs/holdout_demo_video.md`) | Testers confirmed for both the rehearsal run and cohort #3 |
| **21–26 Sep** | **App, Phase 1 vertical slice** (in parallel from 21 Sep): wallet-first connect → faucet → ladder → stake → lobby (lock) → sitting with pre-flight, palm, counting, banking, offline warning → submit / tap-to-submit → home counters and roster → forfeit → claim; streak; walkthrough placeholder, reachable from the connect screen | Device scenarios 15–18 pass on a Seeker and a plain Android phone; a full compressed cycle passes on the rehearsal deployment |
| **24–26 Sep** | **Beta test on the rehearsal deployment:** a test league of seven 10-minute days at 5 reps, so a tester runs faucet → join → sittings → forfeit → settlement → claim in about 70 minutes. Bugs fixed the same day. Nothing filmed here is ever used (H14) | Every tester completes a full cycle; their findings are logged and triaged |
| **26–27 Sep** | **Phase 2, only if every Phase 1 check is green:** automatic offline submission → carry-over delivery → league leaderboards → weekly chip tooling (in that order — smallest first). With the compressed calendar, expect most to be cut | Each item either lands with its acceptance scenario, or is cut and logged in the decision record |
| **27 Sep → 28 Sep 04:00 UTC** | **Release candidate APK.** Testers install it and join cohort #3; the finisher (Seeker) and forfeiter (second phone) wallets join on camera, and the onboarding shots — app icon, connect, faucet, ladder, stake — are filmed with a brand-new wallet (R18) | Every tester's membership plus both filming wallets on-chain before the join cutoff; locked hour filmed |
| **28 Sep** | **Feature freeze.** Cohort #3 starts at 05:00 UTC | Release candidate tagged |
| **28 Sep – 4 Oct** | **Bug fixes only:**<br>• Contract defects fixed by upgrade (FR-007b).<br>• Daily sitting footage; the home screen captured at about 05:05 UTC each day for the time-lapse; the forfeiter misses a day and its forfeit screen is filmed (shot list in `docs/holdout_demo_video.md`).<br>• Tester feedback collected after day 1 and at the end.<br>• README: clone-and-run steps checked on a clean WSL2 + Android Studio machine; known weaknesses final, including the sponsor key and upgrade authority.<br>• Pitch deck drafted. | A clean clone builds the app and the program from the README alone |
| **5 Oct** | Cohort #3 settles at 05:00 UTC → the crank settles it → the finisher claims on camera | Scenario 4 recorded |
| **5–7 Oct** | **Deliverables:**<br>• Demo video of about three minutes and the in-app walkthrough cut from the same footage.<br>• Pitch deck final; disclosure of scripted members, the test network and the weaknesses checked in both.<br>• Faucet vault funded to 25 SOL. | The video shows join, sitting, forfeit, settlement and claim, all from cohort #3 |
| **7–8 Oct** | **Final APK:** the release candidate plus the walkthrough video and bug fixes; install check on a Seeker and a plain phone; **submit APK, repository, video and deck on 8 Oct**, leaving a day of margin | Acceptance criterion 19; submitted before 9 Oct 08:59 CEST |
| **9 Oct – 11 Nov** | **Live period:** no manual intervention (FR-043); monitoring only; contract defect fixes by upgrade, recorded in the README. App fixes are not assumed to reach judges | A populated cohort is running at every moment; the faucet vault is not exhausted |
| **12–30 Nov** | The crank keeps settling the cohorts scripted wallets joined; the last settles 30 Nov 05:00 UTC; then the Fly.io schedule is switched off (R12) | Every scripted membership resolved |

Post-hackathon, outside this plan: a winning app must be published on the Solana dApp Store within 30 calendar days of the announcement (10 or 11 December 2026, depending on which announcement date the rules mean).

## Follow-ups outside this plan

- **README (constitution XI):**
  - Add two known weaknesses, each with its fix: the faucet's bundled sponsor key, with the daily lamport budget and the permissionless refill that bound it (R13, FR-042b), and the retained program upgrade authority (R14).
  - Add clone-and-run build steps for someone who has never seen the project (FR-066).
- **GitHub:** create the repository and push the existing history now; judges score technical depth from the commit history (FR-066).
- **Beta testers (by 21 Sep):** recruit 5–7, open a group chat, write the one-page onboarding note (install, devnet wallet, faucet, what each day asks) and collect written consent from anyone who may appear in the footage — `docs/holdout_demo_video.md`.
- **Filming kit (before spike S1):** `scrcpy` installed and checked against the wallet's approval screen, a tripod for the second phone, and the shot log started.
- **Scope doc D-F and D-G:** note the Kotlin crank and MMKV for Android.
- **Devnet SOL, gathered on this schedule:**
  - Rehearsal and judged program deploys by 21–22 Sep (rent measured at the rehearsal deploy; the rehearsal program can be closed afterwards to reclaim it).
  - About 1.12 SOL for the 56 scripted wallets by 22 Sep (R12).
  - 25 SOL in the faucet vault by 8 Oct (R13).
- **Before spike S1:** set up WSL2 Ubuntu 24.04 with Rust, Solana CLI 3.1.10, Anchor 1.1.2 and Node 22; register a domain (or subdomain) for the MWA identity URI and host its Digital Asset Links file (R3).
- **Agent tooling:** install the solana-dev skill in this repository and add the Solana MCP server (R19).

## Complexity Tracking

No constitution violations to justify.
