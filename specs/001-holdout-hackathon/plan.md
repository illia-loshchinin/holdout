# Implementation Plan: Holdout hackathon build

**Branch**: `001-holdout-hackathon` | **Date**: 2026-09-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-holdout-hackathon/spec.md`

## Summary

A fitness commitment app on Solana devnet: a free daily push-up challenge with a cosmetic streak, under two staked leagues (7-day and 21-day, 25 reps a day) where missed days forfeit stakes into a per-cohort prize pool that finishers claim. Everything that decides money lives in one **Rust/Anchor program**; every deadline is derived from chain time; automation is permissionless and idempotent. Members use a **native Android app in Kotlin** (Jetpack Compose, Mobile Wallet Adapter, MediaPipe Tasks on-device pose detection) that banks verified reps in blocks of 10 on the device and submits a completed day on-chain. A **Kotlin/JVM crank** shares the app's core module to open cohorts, record forfeits, settle, drive scripted members and publish chip sets. The contract is tested first in **TypeScript on LiteSVM** with clock control; golden fixtures keep the Kotlin client byte-compatible.

Work follows the spec's phasing (spec §3a): **Phase 1** is the full loop and must work end to end before any **Phase 2** item (league leaderboards, weekly chip re-pricing, carry-over delivery, automatic offline submission) starts, and nothing lands after the 28 September freeze.

## Technical Context

**Language/Version**: Rust (toolchain required by Anchor 1.0.x) for the program; Kotlin 2.x on JDK 17 for the Android app, core library and crank; TypeScript on Node 22 LTS for program integration tests only.

**Primary Dependencies**:
- **Program:** Anchor 1.0.x with the token interface (research R1).
- **Android app:** Jetpack Compose, starting from Solana Mobile's Kotlin Compose scaffold; `mobile-wallet-adapter-clientlib-ktx` 2.0.x; Solana Mobile `web3-core` and `rpc-core`; kborsh; MediaPipe Tasks Vision (Pose Landmarker, Hand Landmarker); CameraX; MMKV for Android; Media3 ExoPlayer (R3–R6, R18).
- **Tests:** LiteSVM with the Anchor TypeScript client (R2).
- **Crank:** `kotlin/core` plus a CLI argument parser (R12).

**Storage**:
- **On-chain:** program accounts — `Config`, `LeagueConfig`, `Cohort` and its vault, `Membership`, `Profile`, the faucet vault (data-model §1).
- **Device:** MMKV key-value records for banked reps, pending submissions, streak and cached chain time (data-model §4). No server database.

**Testing**:
- **Program:** `cargo test` for the pure settlement math (PRD §19 vectors V1–V22 plus property tests); TypeScript + LiteSVM integration tests for every scenario, including the randomised INV-1…INV-8 suite at 1,000 members.
- **Kotlin:** JUnit 5 for `core` (golden fixture replay) and `repcounter` (recorded landmark traces, including cheats); Compose UI tests for key screens.
- **Manual device checks:** quickstart §8.

**Target Platform**: Solana devnet; Android 10+ (API 29), primary device Solana Seeker with Seed Vault Wallet; the crank on a JVM (Fly.io scheduled machine).

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
- **Deadline:** feature freeze 28 September 2026.

**Scale/Scope**: two leagues; weekly cohorts of up to 1,000 members each (up to three 21-day cohorts running at once); ~140 scripted wallets per week; about 13 app screens with variants (PRD §10.1); one month of judging.

No `NEEDS CLARIFICATION` items remain — all are resolved in [research.md](research.md).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

| Principle | Gate | Pre-research | Post-design evidence |
|---|---|---|---|
| **I. Contract is the only authority on funds** | No off-chain key can move a stake, pool or carry-over | PASS | No admin/operator/sponsor withdrawal exists; funds move only in rule-driven instructions (contracts/program-instructions §1–3); the faucet vault holds only devnet gas SOL and minted test SKR, never stakes |
| **II. Forfeiture is derived** | Breach = `now > deadline_of(days_completed)` from chain state; automation only materialises it | PASS | `in_breach` guard in `submit_day` and `record_forfeits`; displays derive the same formula (data-model §1.3, §5; R7) |
| **III. No oracle in contract logic** | No price read; no fiat input to any on-chain rule | PASS | The rate exists only in `crank chips publish` and the README; `publish_chip_set` takes SKR amounts only (R15) |
| **IV. SKR only** | Stakes, pool, payouts and fees are SKR | PASS | Every token movement uses the SKR mint; devnet SOL appears only as transaction fees and rent (R13) |
| **V. Parameters live on the cohort** | Start, cutoff, duration, reps, day length, boundary and chips fixed at creation | PASS | Copied into `Cohort` by `open_cohort` from `LeagueConfig`/`Config`; never read from constants (data-model §1.3) |
| **VI. One clock** | Single 05:00 UTC boundary from chain time; countdown, never dates | PASS | Cohort start aligned to Monday 05:00 UTC; `deadline_of` lands on the boundary; streak and faucet day use `floor((now − 18,000) ÷ 86,400)` |
| **VII. Tests precede logic** (non-negotiable) | Vectors and invariants exist as failing tests before instructions | PASS | Delivery sequence step 1 writes `fixtures/settlement-vectors.json`, the Rust math tests and the invariant harness before any instruction body |
| **VIII. Idempotent, chunkable automation** | Safe twice, safe paged, two callers harmless | PASS | Per-membership state and `counted` flags; pages in any order; permissionless (R9, FR-030a) |
| **IX. Every state has a screen** | Each state reachable in UI or provably unreachable | PASS | State → screen table in contracts/android-app §5 |
| **X. Camera frames never leave the device** | No upload, persistence or server inference | PASS | Network egress limited to RPC and the local wallet (contracts/android-app §2); no `RECORD_AUDIO`; AC-10 traffic check |
| **XI. Weaknesses documented** | Everything faked, stubbed, flagged or trusting is named in the README | PASS — **action required** | New trust gap from research: the faucet's fee **sponsor key ships in the APK** (R13). The README must name it with its on-chain limits (follow-up below) |
| **XII. The build demonstrates itself** | Live populated cohort any day; free challenge immediately | PASS | Permissionless weekly opening; crank every 10 min; scripted wallets as real members; faucet; walkthrough in the APK (R11–R13, R18) |
| **XIII. Honest language** | "Prize"; no money on leaderboards; fiat approximate; test network stated | PASS | Copy per PRD §13; roster and boards carry no amounts (contracts/android-app §6) |
| **Hackathon constraints** | Devnet only; named flags; feature freeze; out-of-scope list; leaderboards on-chain only | PASS | Flags in `Config`; Phase 2 cut at the freeze; test leagues confined to `test-leagues` builds and a separate rehearsal deployment |
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
programs/holdout/
├── Cargo.toml                      # features: test-leagues
└── src/
    ├── lib.rs
    ├── state/                      # config.rs, league.rs, cohort.rs, membership.rs, profile.rs
    ├── instructions/               # initialize, create_league, set_flags, publish_chip_set,
    │                               # open_cohort, record_forfeits, settle_count, settle_finalize,
    │                               # deliver_carry_over [Phase 2], claim_faucet, join_cohort,
    │                               # leave_cohort, submit_day, claim_payout
    ├── math/                       # settlement.rs, schedule.rs — pure, no Anchor types
    ├── guards.rs                   # in_breach, status, slot normalisation, gates
    ├── events.rs
    └── errors.rs

tests/                              # TypeScript, LiteSVM
├── package.json
├── helpers/                        # clock control, cohort builders, invariant checker
├── scenarios/                      # one file per acceptance criterion (contract side)
├── invariants/                     # randomised INV-1…INV-8, 1,000-member cohort
└── fixtures/generate.ts            # writes ../fixtures/*.json

fixtures/                           # committed golden JSON (contracts/program-instructions §7)

kotlin/                             # Gradle root (Kotlin DSL, version catalog)
├── core/                           # JVM: account codecs, PDAs, instruction builders,
│                                   # Derive.kt, RPC reads, scripted-wallets.json
├── repcounter/                     # JVM: pre-flight checks, palm rule, push-up state machine
├── app/                            # Android: Compose UI, CameraX + MediaPipe, MWA, MMKV
│   └── src/main/kotlin/…/
│       ├── wallet/  faucet/  home/  ladder/  lobby/  sitting/
│       ├── cohort/  roster/  claim/  streak/  walkthrough/
│       └── leaderboard/            # [Phase 2]
└── crank/                          # JVM CLI: run, scripted, chips, init, status

.github/workflows/crank.yml         # fallback schedule, disabled by default
fly.toml                            # crank scheduled machine
```

**Structure Decision**: one repository, three build systems with one job each. **Cargo** builds the program. **npm** exists only to test the program on LiteSVM and generate the fixtures. **Gradle** builds everything that runs in production off-chain — the Android app and the crank — sharing `kotlin/core` so there is exactly one client implementation, pinned to the program by committed fixtures.

## Delivery sequence

Sized against the scope doc's calendar (§0) and the spec's phasing. Each step ends with its tests green.

| Window | Step | Done when |
|---|---|---|
| **15–17 Sep** | **Spikes S1–S3** (research) in parallel with **tests first**: `fixtures/settlement-vectors.json`, Rust math tests, LiteSVM harness with clock control and invariant checker — all failing | Spikes pass or their fallbacks are chosen; failing tests committed |
| **17–20 Sep** | **Program, Phase 1:** schedule and settlement math; accounts; all Phase 1 instructions; fixture generation; Kotlin `core` replaying fixtures | Contract-side acceptance scenarios and the 1,000-member invariant suite pass |
| **20–21 Sep** | **Devnet launch of the judged program:** deploy; `crank init` with leagues starting **28 Sep 05:00 UTC**; `crank chips publish`; faucet vault funded; cohort index 0 (cohort #3) opened so its lobby is live; crank scheduled on Fly.io; rehearsal deployment with a test league | `crank status` shows cohort #3 `Open`; a second `crank run` changes nothing |
| **21–26 Sep** | **App, Phase 1 vertical slice:** wallet-first connect → faucet → ladder → stake → lobby (lock) → sitting with pre-flight, palm, counting, banking, offline warning → submit / tap-to-submit → home counters and roster → forfeit → claim; streak; walkthrough placeholder. **Scripted members** begin joining cohort #3 | Device scenarios 15–18 pass on a Seeker and a plain Android phone; a full compressed cycle passes on the rehearsal deployment |
| **26–27 Sep** | **Phase 2, only if every Phase 1 check is green:** automatic offline submission → carry-over delivery → league leaderboards → weekly chip tooling (in that order — smallest first) | Each item either lands with its acceptance scenario, or is cut and logged in the decision record |
| **28 Sep** | **Feature freeze.** Cohort #3 starts | Release APK tagged |
| **28 Sep – 4 Oct** | Bug fixes only; daily footage | — |
| **5–8 Oct** | Cohort #3 settles 5 Oct 05:00 UTC → record the walkthrough with a real claim → ship it inside the APK; README weaknesses final; video and deck | Scenario 4 recorded; final APK submitted before 9 Oct 08:59 CEST |

## Follow-ups outside this plan

- **README (constitution XI):** add the faucet's bundled sponsor key and its on-chain caps to the known weaknesses (R13).
- **Decision record:** mark P3, P4, P5, P8, P10 and P11 resolved by research R15, R14, R12, R8, R6 and R13; record the switch to a native Kotlin app.
- **Scope doc D-F and D-G:** note the Kotlin crank and MMKV for Android.
- **Before 20 Sep:** gather ~40 devnet SOL for the faucet vault (R13).

## Complexity Tracking

No constitution violations to justify.
