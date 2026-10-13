# Implementation Plan: Holdout hackathon build

**Branch**: `001-holdout-hackathon` | **Date**: 2026-09-15, revised 2026-09-17 for the CLOCK IN rules | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-holdout-hackathon/spec.md`

## Summary

A fitness commitment app on Solana devnet: **the Daily 25** — a free daily push-up set with a cosmetic streak — under staked **Holdouts** of 7 or 21 days, 25 reps a day (members' names; the contract stores them as cohorts in two leagues, spec §1a) where missed days forfeit stakes into a per-cohort prize pool that finishers claim. Everything that decides money lives in one **Rust/Anchor program**; every deadline is derived from chain time; automation is permissionless and idempotent. Members use a **native Android app in Kotlin** (Jetpack Compose, Mobile Wallet Adapter, MediaPipe Tasks on-device pose detection) that banks verified reps in blocks of 10 on the device and submits a completed day on-chain. A **Kotlin/JVM crank** shares the app's core module to open cohorts, record forfeits, settle, drive scripted members and publish chip sets. The contract is tested first in **TypeScript on LiteSVM** with clock control; golden fixtures keep the Kotlin client byte-compatible.

Work follows the spec's phasing (spec §3a): **Phase 1** is the full loop and must work end to end before any **Phase 2** item (league leaderboards, weekly chip re-pricing, carry-over delivery, automatic offline submission) starts. There is no feature freeze (removed 2026-10-06, constitution v2.0.0).

## Technical Context

**Language/Version**: Rust ≥ 1.89 with Anchor 1.1.2 and Solana CLI 3.1.10 for the program; Kotlin 2.4.10 (JDK 21 daemon, Java 17 bytecode) for the Android app, core library and crank; TypeScript on Node 22 LTS for program integration tests only.

**Primary Dependencies**:
- **Program:** Anchor 1.1.2 with the token interface, `solana-*` crates on `^3` (research R1).
- **Android app** — generated from the Solana Mobile CLI's **Kotlin Compose Minimal** template (R3):
  - **Build:** AGP 9.3.2 with Gradle 9.7.1; Compose BOM 2026.08.00.
  - **Solana:** `mobile-wallet-adapter-clientlib-ktx` 2.2.0, `web3-solana` 0.3.1, `multimult` 0.2.6, plus `rpc-core` (version pinned at scaffold).
  - **Camera and device:** MediaPipe Tasks Vision (Pose Landmarker), CameraX, MMKV for Android (R4–R6). *(Hand Landmarker dropped 2026-09-22 with the palm check, T047, R5.)* *(Media3 ExoPlayer dropped 2026-10-09 with the in-app walkthrough, R18.)*
- **Tests:** `@solana/kit` with `@solana/kit-plugin-litesvm` (LiteSVM 1.3.x) and a Codama-generated Kit client; a Surfpool smoke suite before deploys (R2).
- **Crank:** `kotlin/core` (`web3-solana`, `rpc-core`) plus a CLI argument parser (R12).

**Storage**:
- **On-chain:** program accounts — `Config`, `LeagueConfig`, `Cohort` and its vault, `Membership`, `Profile`, the faucet vault (data-model §1).
- **Device:** MMKV key-value records for banked reps, pending submissions, streak and cached chain time (data-model §4). No server database.

**Testing**:
- **Program:** `cargo test` for the pure settlement math (the vectors V1–V22 and the reserve vectors in `fixtures/settlement-vectors.json`, which govern, first drawn from the archived PRD §19, plus property tests); Kit + LiteSVM integration tests for every scenario, including the randomised INV-1…INV-9 suite at 1,000 members; a Surfpool smoke run (`anchor test`) with time travel before each devnet deploy.
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
- **Reminders:** on-device only — `AlarmManager` on chain-derived elapsed-realtime instants, no server or push service; `POST_NOTIFICATIONS` asked once at the first join (FR-068, R21). A member who finished gets one more, the **finished notice**, 15 minutes after the end — no network, so it says the claim is *probably* ready (FR-068b, 2026-10-11) — and the settling screens count down to that usual 15 minutes (`Derive.settleUsuallyIn`, FR-035a), which follows the crank's 10-minute cadence (R12).
- **Deadlines:** submission (APK, GitHub repository, demo video of about three minutes, pitch deck) before 13 October 2026, 01:59 CEST (FR-066; moved from 9 October, 08:59 CEST, constitution v2.0.1). Judging runs 13 October – 9 November.
- **Live period:** no manual intervention from submission through the winners' announcement on 11 November 2026 (FR-042a, FR-043).
- **Upgrades:** the contract stays upgradeable for defect fixes only (FR-007b, R14).

**Scale/Scope**: two leagues; weekly cohorts of up to 1,000 members each (up to three 21-day cohorts running at once); real members first, topped up by a fixed pool of 56 scripted wallets to a floor of 20 members per 7-day cohort and, on devnet since 2026-10-13, 4 per Hackathon round once a real member has joined it and none per 21-day cohort (R12, FR-043); about 14 app screens with variants, the first-run tour included (PRD §10.1, FR-067); judging 10 October – 9 November 2026.

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
| **XI. Weaknesses documented** | Everything faked, stubbed, flagged or trusting is named in the README | PASS — **action required** | Two trust gaps the README must name, with their fixes: the faucet's fee **sponsor key ships in the APK** (R13), and the **program upgrade authority** is kept (R14). A third since 2026-10-10 (constitution v2.3.0, R17): on devnet the **Seeker-exclusive gate is the app's**, not the contract's, until the flag is turned on at mainnet. The demo video and pitch deck must not hide them (follow-ups below) |
| **XII. The build demonstrates itself** | Live populated cohort any day of the live period; free challenge immediately; scripting disclosed in app, README, demo video and deck | PASS | Permissionless weekly opening; crank every 10 min; real members first, with scripted wallets filling each cohort only to its floor — Spark 20, a Hackathon round 4 once someone real joins, Habit none on devnet (R12, 2026-10-13); faucet funded to 11 November (R13); the full cycle shown in the demo video, sent as a link, and runnable by anyone with a Seeker on the short test lobby (R18, FR-044, FR-044a; amended 2026-10-09). Since v2.3.0 a wallet without a Seeker Genesis Token still sees every live Holdout thin and does the Daily 25 at once; only joining is refused, with the reason and the Seeker store link (FR-003c, R17) |
| **XIII. Honest language** | "Prize"; no money on leaderboards; fiat approximate; test network stated | PASS | Copy per PRD §13; roster and boards carry no amounts (contracts/android-app §6) |
| **Hackathon constraints** | Devnet only; signed APK, no store listing; named flags; submission deliverables by 13 October 01:59 CEST; upgrade authority for defect fixes only; out-of-scope list; leaderboards on-chain only | PASS | Flags in `Config`; `genesis_gate_enabled` now has its check written behind it (R17, v2.3.0), off on devnet, where staking is kept Seeker-exclusive by the app; Phase 2 built after Phase 1, no date gate; test leagues confined to `test-leagues` builds (LiteSVM and the local validator, per the `create_league` row of `contracts/program-instructions.md` and quickstart §5a; the devnet program, FR-044a) and never in a mainnet build; GitHub repository judges can clone, pushed from the first commit; deliverables and final APK in the delivery sequence; upgrade rule in R14 |
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
    │                               # claim_faucet,
    │                               # top_up_sponsor, join_cohort, leave_cohort,
    │                               # submit_day, claim_payout
    ├── math/                       # settlement.rs, schedule.rs — pure, no Anchor types
    ├── genesis.rs                  # SEEKER_GENESIS_GROUP, Seeker Genesis Token check over account bytes (R17)
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
│                                   # Derive.kt, RPC reads, scripted-wallets.json,
│                                   # SeekerGenesisReader.kt — mainnet Seeker Genesis Token read (R17)
├── repcounter/                     # JVM: pre-flight checks, ready hold, push-up state machine
├── app/                            # Android: Compose UI, CameraX + MediaPipe, MWA, MMKV
│   └── src/main/kotlin/…/
│       ├── wallet/  faucet/  home/  lobby/  sitting/
│       ├── cohort/  roster/  claim/  streak/
│       ├── stake/                    # SeekerGate.kt, SeekerGateSheet.kt, StakeSheet.kt — the Seeker gate before the stake confirm (FR-003b)
│       ├── nav/                      # NavState + pure reducer: tabs, back stacks, sheets, signing flow (FR-069, FR-070; research R23)
│       ├── holdouts/  standing/  me/ # the Holdouts (H0), Standing and Me (W1) tab roots (FR-069b…FR-069d)
│       ├── signing/                  # Waiting for your wallet, confirming checklist, results, You're in (FR-070…FR-070b)
│       ├── tour/                     # first-run cards (five, each fits a Seeker screen), FR-067
│       ├── about/                    # About Holdout: test network, tour again, disconnect (spec 004)
│       ├── ui/theme/                 # Color.kt, Type.kt, Shape.kt, Theme.kt — the design book's tokens (research R20)
│       └── leaderboard/            # [Phase 2]
└── crank/                          # JVM CLI: run, scripted, chips, init, status

.github/workflows/crank.yml         # fallback schedule, disabled by default
fly.toml                            # crank scheduled machine
```

**Structure Decision**: one repository, three build systems with one job each. **Cargo** builds the program (WSL2). **npm** exists only to test the program on LiteSVM, generate the Codama client and generate the fixtures (WSL2). **Gradle** — seeded from the Solana Mobile CLI template — builds everything that runs in production off-chain: the Android app and the crank, sharing `kotlin/core` so there is exactly one Kotlin client implementation, pinned to the program by committed fixtures.

## Delivery sequence

*(2026-10-09: the organisers moved the submission to 13 October 01:59 CEST and judging to 13 October – 9 November (constitution v2.0.1); the in-app walkthrough was dropped for a demo video sent as a link, and recording moved to Holdout Lab (spec 004, v2.1.0). The rows below are kept as the history they record.)*

**Re-planned 17 September 2026** against the official CLOCK IN dates: submission due 9 October 08:59 CEST (06:59 UTC), judging 10 October – 9 November, winners announced 11 November. The spikes had not started by 17 September, so they now run alongside the tests-first step instead of before it, and the judged launch moves from 20–21 to 22–23 September. The hard gates are fixed by cohort #3: its lobby must be live and the app's join flow working before its join cutoff (**28 September 04:00 UTC**), because the walkthrough and demo video can only come from it (R18). Each step ends with its tests green.

| Window | Step | Done when |
|---|---|---|
| **17–18 Sep** | **Setup and tests first:**<br>• WSL2 toolchain (R19).<br>• Anchor workspace and Kotlin template scaffolded, with its defects fixed (R3).<br>• GitHub repository created and pushed from the first commit, since judges read the commit history (FR-066).<br>• `fixtures/settlement-vectors.json`, the Rust math tests, and the LiteSVM harness with clock control and invariant checker, all failing.<br>• **Spike S1** (MWA signing on a Seeker and a plain phone, including which wallets sign devnet transactions) and **spike S2** (rep counting on real video) start. | Failing tests committed and pushed; S1 answered or its fallback chosen |
| **18–21 Sep** | **Program, Phase 1:**<br>• Schedule and settlement math; accounts; all Phase 1 instructions; fixture generation.<br>• Kotlin `core` replaying the fixtures.<br>• **Spike S3** (full cycle at 1,000 members on LiteSVM). S2 continues on device. | Contract-side acceptance scenarios and the 1,000-member invariant suite pass; S2 and S3 passed or fallbacks chosen |
| **18–20 Sep** | **Design, alongside the program:** prototype the four hero screens — home in a cohort, the sitting while counting, stake confirm, claim — from the design tokens (today the design book in `docs/design/`, research R20; the archived PRD's §11 they began from is no longer a source); critique and accessibility pass; then **freeze the tokens** and write the Compose theme (`kotlin/app/.../ui/theme/`: colours, type, shapes, no dynamic colour). App icon, wordmark and splash drafted | The prototype reads as a Seeker-native app beside the device's own screens; tokens frozen before the app slice starts |
| **21–22 Sep** | *(Superseded 2026-09-26: the devnet program itself carries `test-leagues` and hosts the one disclosed short test cohort, FR-044a; the judged leagues start 5 Oct, and the freeze moves with them. Superseded again 2026-10-06: no freeze, and a test lobby kept running for judges.)* **Rehearsal deployment:** a separate program ID with a test league; Surfpool smoke suite | A compressed cycle runs on devnet; deploy rent measured for the SOL budget |
| **22–23 Sep** | **Devnet launch of the judged program:**<br>• Deploy, keeping the upgrade authority (R14).<br>• `crank init` with leagues starting **28 Sep 05:00 UTC**; `crank chips publish`.<br>• Faucet vault part-funded (topped up to 25 SOL by 8 Oct, R13).<br>• Cohort #3 opened so its lobby is live; crank scheduled on Fly.io.<br>• The 56 scripted wallets funded, filling cohort #3 to half its floor (R12). | `crank status` shows cohort #3 `Open` with scripted members joining; a second `crank run` changes nothing |
| **by 21 Sep** | **Recruit 5–7 beta testers** (Android phone, devnet-capable wallet per spike S1); group chat opened; consent asked from anyone who may appear in the footage (`docs/holdout_demo_video.md`) | Testers confirmed for the test cohort on the devnet program and cohort #3 |
| **21–26 Sep** | **App, Phase 1 vertical slice** (in parallel from 21 Sep): wallet-first connect → faucet → ~~ladder~~ *(removed: the screen 2026-10-09, the gate 2026-10-10, H37)* → stake → lobby (lock) → sitting with pre-flight, palm *(dropped 2026-09-22 for the held start position, T047)*, counting, banking, offline warning → submit / tap-to-submit → home counters and roster → forfeit → claim; streak; walkthrough placeholder, reachable from the connect screen | Device scenarios 15–18 pass on a Seeker and a plain Android phone; a full compressed cycle passes on the devnet program (the test lobby, FR-044a) |
| **24–26 Sep** | **Beta test on the devnet program's test league:** a test league of seven 10-minute days at 5 reps (H32), so a tester runs faucet → join → sittings → forfeit → settlement → claim in about 70 minutes. *(Superseded 2026-10-06: the test lobby runs three 10-minute days, a new cohort every 30 minutes, for judges through the live period — FR-044a; the separate rehearsal deployment was superseded on 2026-09-26, so the rows above that name one are history, not a second program.)* Bugs fixed the same day. Nothing filmed here is ever used (H14) | Every tester completes a full cycle; their findings are logged and triaged |
| **26–27 Sep** | **Phase 2, only if every Phase 1 check is green:** automatic offline submission → carry-over delivery → league leaderboards → weekly chip tooling (in that order — smallest first). With the compressed calendar, expect most to be cut | Each item either lands with its acceptance scenario, or is cut and logged in the decision record |
| **27 Sep → 28 Sep 04:00 UTC** | **Release candidate APK.** Testers install it and join cohort #3; the finisher (Seeker) and forfeiter (second phone) wallets join on camera, and the onboarding shots — app icon, connect, faucet, ~~ladder~~ *(removed, H37)*, stake — are filmed with a brand-new wallet (R18) | Every tester's membership plus both filming wallets on-chain before the join cutoff; locked hour filmed |
| **28 Sep** | *(Superseded: the freeze moved to 5 Oct, then was removed 2026-10-06.)* Cohort #3 starts at 05:00 UTC | Release candidate tagged |
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
- ~~**Scope doc D-F and D-G:** note the Kotlin crank and MMKV for Android.~~ *(Moot 2026-10-10: the scope document is retired; the crank is research R12 and MMKV research R6.)*
- **Devnet SOL, gathered on this schedule:**
  - Judged program deploy by 22–23 Sep, the delivery table's launch row (rent computed from the built binary with `solana rent <bytes>` beforehand; the separate rehearsal deployment was superseded on 2026-09-26, so there is no second program to close).
  - About 1.12 SOL for the 56 scripted wallets by 22 Sep (R12).
  - 25 SOL in the faucet vault by 8 Oct (R13).
- **Before spike S1:** set up WSL2 Ubuntu 24.04 with Rust, Solana CLI 3.1.10, Anchor 1.1.2 and Node 22; register a domain (or subdomain) for the MWA identity URI and host its Digital Asset Links file (R3).
- **Agent tooling:** install the solana-dev skill in this repository and add the Solana MCP server (R19).

## Amendment 2026-10-11 — the comeback offer (FR-037g, open item L13)

*(Superseded in part 2026-10-12 by the amendment "The guaranteed comeback" below: the window of two rounds and 14 days is the next round — or, after a last-day miss, a late round or the one after it —, the amount is exact, and the program does change. The derivation idea and the join path (ES-17) stand.)*

**Summary.** After a forfeit the app offers the comeback of FR-037e at once, worked out on the phone from the member's forfeited membership, instead of waiting for the league progress record that the program writes only at settlement (research R22). **The program, its accounts, its instructions and the IDL are unchanged**, so H24 is untouched and the change needs no deploy; it ships in the app before the submission. Branch `feat/comeback-offer`, base `001-holdout-hackathon`.

**Technical context, delta.** Kotlin only: `kotlin/core` (pure derivation, unit-tested on the JVM) and `kotlin/app` (Compose). No Rust, no TypeScript, no fixture regeneration. The join transaction gains one instruction already in `core` (`Instructions.recordForfeits`), sent ahead of `join_cohort` when the member's own forfeit is not yet recorded (ES-17).

**Constitution check.** I: funds still move only by program rule; the app reads, it decides nothing — settlement still decides the refund. II: the offer is derived from the missed deadline and chain time, the same `deadline_of(days_completed)` the program records; the record the app sends is the permissionless one any caller may send. VI: chain time only. VII: the derivation is tested in `core` against the program's own window rule (`LeagueProgress::take_comeback`); tasks T178–T187 and T197–T205 say which tests were seen red first and which were written alongside the code (VII binds the program's instructions and vectors, which this change does not touch). IX: every new state has a screen — offer open, place held inside the window, place held outside it, the next round not opened yet, no round left (ES-18). XIII: every amount is "up to", the reserve is named by level and length, never "league" (FR-039, FR-041). **Gate: PASS.**

**Where it lands.**

| Piece | Files |
|---|---|
| The offer, derived | `kotlin/core/src/main/kotlin/holdout/core/Comeback.kt` (new): the latest forfeit per progress slot (recorded `Forfeited`, or in breach and not yet recorded — `Derive.inBreach`), its missed deadline `Derive.deadlineOf(c, daysCompleted)`, the window `(missed, missed + 14 × dayLength]`, the rounds — every `firstStart + k × cadence` of the slot's scheduling league (the forfeit's own league, test leagues included — a retired test league still opens the rounds its comebacks need, FR-044a; amended 2026-10-12 with `e4fd9c9` and `f1777d0`: while the shared test slot records an open pledge, `Comeback.offers` takes the latest forfeit of the pledged league (`comebackLeague`) from the member's profile, never a newer forfeit in another test league, and gives no offer when that forfeit is not in sight) inside the window, each with its cohort if opened and its status — the member's place in one, and the amount `Settlement.comebackOwed(lost, chip)`; used up when a counted finish of a later cohort in the window exists. Tests in `kotlin/core/src/test/kotlin/holdout/core/ComebackTest.kt` |
| Joining right after the loss | `Journey.joinBlocker` ignores a running cohort the member is in breach of; `HoldoutViewModel.join` sends `[recordForfeits(own pair), joinCohort(running = null)]` when that forfeit is unrecorded — `null` because the record has already cleared the running slot, and passing the old cohort would write its end back into a cleared slot. Called from `Route.Stake` and `Route.Forfeit` in `HoldoutApp.kt`, with `runningInBreach` required; cases in `JourneyTest.kt` |
| Forfeit screen (H6) | `kotlin/app/.../cohort/ForfeitScreen.kt`: the offer card, the Join into the stake sheet on the lost chip, the second-round line, the place-held and leave-first variants; wiring in `HoldoutApp.kt` (`Route.Forfeit`) |
| Stake sheet (H2) | `kotlin/app/.../stake/StakeSheet.kt`: an initial tier, and the "Finish it and N SKR of the stake you lost comes back." line for the chip chosen (the exact amount, H39 — never "up to"), only when the Holdout is a comeback round. *(2026-10-12, `435720d`)* The sheet fits one Seeker screen (400 × 829 dp, at most 92 %) with nothing to scroll in every state — one-line title, no "Your stake" row, the level's rule as a plain line under the fee sentence, the one way on (*Get test tokens*, *See your lobby*) in the stake button's place, and in the last 10 minutes a warning-coloured countdown with the warning "A transaction signed now may land too late." — guarded by `stake/StakeSheetFitTest` (eight states) |
| Today (T1) | `kotlin/app/.../home/DailyHoldoutLine.kt`: the nudge from the derived offer (before joining: start and time left to join; after: finish it), replacing the read of `progress.comeback_*`; `DailyHoldoutLineTest.kt` |
| Copy | `ui/Components.kt` `Format` helpers; the design book's rules through the `holdout-ui` skill |
| After the PR #55 review *(2026-10-11)* | `Comeback.offerFrom`: `upcoming` excludes a round inside its own join lock; for the test slot the rounds first came from the newest test league only (FR-044a; superseded 2026-10-12: they come from the forfeit's own league, a retired test league opening the rounds its comebacks need, and the offer follows the slot's pledge); `Comeback.forScreenOf` keeps the offer to the latest forfeit's screen (`Route.Forfeit`); Today's order is a round to join now, then a place held, then a round not opened yet (`DailyHoldoutLine.line`, computed once with its comeback flag); the join decision as pure `Journey.joinPlan` / `Journey.joinInstructions`, used by `HoldoutViewModel.join` and `ChainView.joinPlan`; one slot mapping, `ProfileSnapshot.slotOf` |
| After the device run *(2026-10-11)* | Today's comeback line before the Daily 25 (`home/HomeScreen.kt` `holdoutLineIsComeback`, `DailyHoldoutLine.comebackLine`); "opens for joining shortly" (`ui/Components.kt` `Format.opensForJoining`); the offer's `upcoming` round (`Comeback.Offer.upcoming`); the payout breakdown after the claim (`core` `Stage.Claimed` carries stake, share and comeback; `claim/ClaimScreen.kt` `PayoutBreakdown`, used by `cohort/CohortDetailScreen.kt`); the record-and-join transaction on LiteSVM, `tests/scenarios/comeback-join.spec.ts` (ES-17) |
| Docs (not spec-kit) | `docs/holdout_decisions.md` (L13 resolved, dated log row), `docs/weaknesses.json` → README table (the L13 row removed or reworded), `docs/holdout_screen_map.md` (H6 card, UC-9, UC-10, §8 item 13) |

**Sequence.** `core` derivation and tests → join path (ES-17) → Forfeit screen and stake sheet → Today → docs → local validator run of quickstart scenario 23 → PR. Built and verified on 11 October (device run, LiteSVM, CI (local), stand-in review); L13 is resolved and out of the weaknesses table.

**Risks.** The offer must agree with the program's rule to the second (`>` the missed deadline, `≤` the window's end): `ComebackTest` replays the program's boundary cases. Two instructions in one transaction must fit the legacy size limit; measured in the local run.

## Amendment 2026-10-11 (evening) — the hero screens (FR-069…FR-070c, FR-041, FR-053, FR-067)

**Summary.** The submitted build is navigated by a bottom bar of four tabs, Today · Standing · Holdouts · Me, replacing hub-and-spoke from one home screen; wallet signing gets a Holdout-owned flow with named results; the tour has four cards (five since 2026-10-12, amendment below); members read Spark · 7-day and Habit · 21-day; the fee sentence, the boost line and the dollar rule are shorter. Kotlin app only: no Rust, TypeScript, program, IDL or fixture change, and counting logic (`kotlin/repcounter`) is untouched.

**Technical context, delta.** Navigation becomes a pure reducer over a `NavState` value (JVM-testable); the signing flow a state machine with a pure result mapping; Compose screen and shell tests run on the JVM under Robolectric 4.17 pinned to SDK 36 (research R23; `compileSdk`/`targetSdk` stay 37). Two new MMKV keys (`looked:`, `sitting:`; data-model §4); since 2026-10-12 `seen:`, the day this phone first saw the wallet; and since 2026-10-13 `sittingday:`, the day a `sitting:` marker banks into, and `unconfirmed:`, the sends that may still land (Session 2026-10-13). Member-facing level names, rule sentences and the fee and boost copy are app-side only — `LeagueParams.formName` in `kotlin/core`, which the crank uses, is not renamed.

**Constitution check.** I, II, III, IV, V, VI (no calendar date or clock time anywhere in the new screens: level · length plus countdowns), VII (reducer and mapping tests precede their logic), X (nothing new leaves the device) — unchanged. IX: every state has a screen — contracts/android-app.md §5 and §5a list the new ones (Standing empty state, My Holdouts, Me, signing results, *You're in*). XII: the Standing empty state keeps the live "Running now" list and the Daily 25 stays open to every wallet. XIII: copy rules enforced by a string test (quickstart 24c). Constitution v2.3.1 renames the levels members read; no gate changes.

**Where it lands.**

| Piece | Files / artifacts |
|---|---|
| Shell and tabs | `kotlin/app/.../nav/` (reducer `Nav.kt`, `Badges.kt`, `NavSaved.kt`), `shell/AppShell.kt` (stateless) with a host per tab and flow in `shell/` (`TodayHost`, `HoldoutsHost`, `StandingHost`, `MeHost`, `SittingHost`, `TxFlowHost`, `Shared`, `TabRoots`), `HoldoutApp.kt` (state, loops, router); the design system's BottomNav component |
| Tab roots | `holdouts/` (H0), `standing/` (the empty state; the detail H4 is `cohort/CohortDetailScreen.kt` hosted by `shell/StandingHost.kt`), `me/` (W1), Today = the existing `home/` T1 / T2 |
| Signing | `tx/` (`SigningScreens.kt`: the flow screens and *You're in*) and `wallet/Signing.kt` (the pure outcome → result mapping); `wallet/WalletRepository.kt` supplies the outcomes; stake confirm and faucet become sheets over their opener |
| Copy and levels | `ui/Components.kt` `Format` level and fee helpers, `Stakes` rule sentences per level (replacing `Stakes.STAKED_RULE` and the string replace in `TourScreen`), tour card 4 |
| Storage | `storage/Store.kt`: `looked:`, `sitting:`, `sittingday:`, `unconfirmed:` *(2026-10-13)* |
| Tests | reducer transitions, wallet-outcome mapping, Robolectric shell and per-screen tests, a copy-rule string test, an end-to-end journey test (quickstart 24a–24c) |
| Docs (not spec-kit) | `docs/holdout_screen_map.md` (tabs, J9, W1, H0), `docs/design/` compose map |

**Risks.** Robolectric cannot show the wallet's own sheet: the signing flow is tested up to the outcome, and the real approval stays a device check (quickstart 24b). A tab's back stack must survive the process dying behind the app (the reducer state is saved as plain strings, `nav/NavSaved.kt`; sheets, signing, sitting and the tour are not).

### Integration pass (clarification 2026-10-11, evening; FR-053a, FR-069, FR-069e, FR-024b, FR-001b, ES-1)

**Summary.** The sitting, the reminder and the saved navigation are wired into the shell by five small, pure pieces, each unit-tested on the JVM; no program, counting or fixture change and, at the time, no new storage key (`sitting:` of the amendment above is now read at Today). The amendment of 2026-10-13 below adds two: `sittingday:`, the day a sitting banks into, and `unconfirmed:`, the sends that may still land (data-model §4).

**Amendment 2026-10-13 (Session 2026-10-13; T349, PR #67).** `HoldoutViewModel.UiState` gains two in-memory records, never stored: `pending`, a `PendingTx` per action, Holdout and (for a submit) day for a send that may still land, which keeps that action shut until a chain read shows it landed or a read starts `PendingTx.LANDS_WITHIN_MILLIS` (120 s) after it; and `unseen`, the `UnseenTx` outcome of a signature the member left while it confirmed, drawn by `HoldoutApp` as a `NoticeCard` through `AppShell`'s `notice` slot until `dismissUnseen`. The shell's `knownNotSeeker` reaches Today, Holdouts and the forfeit screen (`canJoin`), which drop Join, and the comeback card and the claim upsell. `sittingday:` and the `seen:` pending value are data-model §4's. After the PR's Codex review: `pending` is also kept on the phone as `unconfirmed:{wallet}` and restored when the wallet is watched; its `sentAt` is taken, and the record kept, at the Confirming step (any outcome but may-still-land settles it); a submit that may still land is `DayState.mayStillLand`, drawn by Today and Standing's strip alike, and Stake and Claim are disabled through `canStake` and `canClaim`; a submit's card is matched by its day; a completion for a wallet since disconnected changes nothing; `unseen` is a list, at most three cards (`UnseenTx.MAX_CARDS`), each dismissed by itself; `knownNotSeeker` is `rememberSaveable`; `SittingMarker.start` writes `sittingday:` before `sitting:`; and the network's name comes from `Deployment.isDevnet` (PR #69, merged into this branch): *Devnet* for the public endpoint or a hosted one whose host names the cluster.

| Piece | Files / artifacts |
|---|---|
| The offer after a Daily 25 (FR-053a) | `shell/SittingOffers.kt` — pure `SittingOffers.of(view, now, seekerKnown, knownNotSeeker)`: nothing while a Holdout is running or in a lobby, nothing for a wallet known not to hold a Seeker, a comeback (`TodayModel.comeback`) before the soonest-closing open Holdout (`OfferCardState`); `shell/SittingHost.kt` passes it to the day-complete page |
| Too late to send → forfeit (FR-024b) | `shell/SittingWiring.kt` — `tooLateToSend` (chain time known and the day's deadline passed) and `holdoutDayEndEvents` (`SittingEnded`, then `Open(Forfeit)` only for a finished, too-late day) |
| Crash page at Today (ES-1) | `shell/SittingWiring.kt` `interruptedSittings` reading `InterruptedSitting.consume` (`sitting/SittingMarker.kt`) for the running Holdout's today, then the Daily 25's; `shell/TodayHost.kt` shows `StoppedScreen` once per marker |
| Reminder and restore (FR-069, FR-069e) | `AppOpen.kt` (the launcher or a reminder, also a new intent for a running activity → `NavEvent.AppOpened`: Today selected, its stack and any sheet dropped, a sitting or signature untouched; `AppOpen.take(intent, restored)` marks an intent once taken (`EXTRA_TAKEN`) so a rotation does not trigger it again; a reminder → Today on `onNewIntent` and on `onCreate` (also when it re-creates a killed app); an app restored by the launcher or from Recents keeps its screens); `nav/NavSaved.kt` (`encode`/`decode` of tab and pushed screens, version `v1`, unreadable → Today) held with `rememberSaveable` in `HoldoutApp.kt` |
| Wrong-network sentence (FR-001b) | `wallet/WalletRepository.WRONG_NETWORK` (with the Me path); `wallet/ConnectScreen.kt` maps it to the same sentence without the parenthesis |

**Constitution check.** Unchanged: VII (each piece is pure and has a test), VI (no date or clock time), X (nothing new leaves the device), XIII (copy). **Risks.** The crash page is checked on the next Today, not on the sitting's screen: a member killed mid-sitting who never returns to Today sees it later, as the marker stays until read.

## Amendment 2026-10-12 — the guaranteed comeback, a late join after a last-day miss, and a boost fed by what nobody claims (FR-008, FR-010, FR-027, FR-037, FR-037c–h, INV-1, INV-10)

**Summary.** A forfeited stake splits 70 / 10 / 10 / 10 (prize pool / the member's comeback pledge / developer / operations). The pledge is booked to the member's comeback when the forfeit is counted and is valid for the comeback rounds `first`…`last` of the same league: usually the single next round; after a miss on a round's last day, a choice of joining the round that starts the second after the miss **late** (until its first day's deadline) or the one after it. Made good it is paid in full; otherwise it goes to the league's boost reserve, through `settle_count` or the new permissionless `release_comeback` (which works on the last round). A cohort nobody finished sends its whole pool to the boost reserve. The boost keeps its size and draws only from `boost_reserve`. Research R24; contracts/program-instructions.md §2–§5; data-model §1.2–§1.5, §2, §3, §5; quickstart 23b. Branch: from `001-holdout-hackathon`'s current head (the hero-screens branch).

**Technical context, delta.** Rust (`programs/holdout`: `LeagueProgress`, `Cohort`, `record_forfeits`, `settle_count`, `settle_finalize`, `join_cohort` (the late join), `claim_payout`, new `release_comeback`, errors 6046–6047, events), the IDL and the Codama clients, `fixtures/` (regenerated from the real program), TypeScript/LiteSVM scenarios and the invariant suite, `kotlin/core` (`AccountLayout`/`Accounts`, `Instructions`, `Comeback`, `Derive`, `Settlement`), `kotlin/crank` (calls `release_comeback`), and `kotlin/app` copy and the late-join Join. The ledger layout changes: **a local ledger reset is required**; nothing is on devnet yet, so no migration exists and the devnet deploy — the owner's call — carries the new layout from its first day.

**Constitution check.** I: the pledge is the member's by program rule, held in the league's program-owned reserve vault; no key chooses where it goes, and a boost cannot touch it; the late join moves only the member's own stake into the vault, as any join does. II: the rounds, the amount, every release and the late join's eligibility are derived from the missed deadline, the schedule and chain state; the crank only materialises. III: no price read. V: the round indexes use the league's `first_start`, `cadence` and `join_lock`, which are fields, not constants. VII: the vectors (split, `first`/`last`, refund and excess, release, late-join window, no-finisher pool) and the INV-1/INV-10 invariants are written as failing tests before the instruction changes (tasks, phase 35). VIII: `release_comeback` is permissionless and a repeat moves nothing. IX: the new states — a pledge open, a round scheduled but not opened, a round only this member may still join, the last round closed without the member — have screens (contracts/android-app.md §5). XIII: the copy says "set aside for your comeback", "boosts", "tops up"; no "earn", "league" or "cohort". XI: a dead crank leaves pledges parked and boosts smaller, never a member's money lost; named in the README only if it changes what the weaknesses table says. FR-010's "roster is final" gains one named exception (the late join); the spec says so. **Gate: PASS.**

**Where it lands.**

| Piece | Files / artifacts |
|---|---|
| Program | `programs/holdout/src/state/profile.rs` (`LeagueProgress`: `comeback_stake`, `comeback_league`, `comeback_round`, `comeback_last_round`; `open_comeback`, `take_comeback` and a release helper), `state/cohort.rs` (`boost_due` removed, `no_finisher_boost`, `comeback_due` = uncounted pledges), `state/league.rs`, `state/membership.rs` (`comeback_entitled`), `math/settlement.rs` (the 70/10/10/10 split, `comeback_first_round`, `comeback_last_round`, refund and excess), `math/schedule.rs` (the comeback rounds), `guards.rs` (`require_joinable`, `is_comeback_round`: the late join), `instructions/record_forfeits.rs`, `settle_count.rs` (`league` mut, `reserve_vault` mut, the pledge booking, replace/make good/release), `settle_finalize.rs` (no `developer_destination`, the boost from `boost_reserve` only, the owed refund drawn in full, a no-finisher pool to the boost reserve), `join_cohort.rs` (optional `comeback_membership`, `comeback_cohort`; the late join), `claim_payout.rs` (refund in full), new `release_comeback.rs`, `errors.rs` (6046, 6047 in the settlement block), `events.rs`, `state/layout_tests.rs` |
| Program tests | Rust unit tests for the split, the rounds (boundary seconds, mid-round and last-day misses, the late-join window), the refund and the excess; `tests/` scenarios and the invariant suite (INV-1 with `comeback_due` and the funded-and-unclaimed term, the new INV-10) |
| Fixtures, IDL, clients | `fixtures/settlement-vectors.json`, `accounts.json`, `instructions.json`, `derived-figures.json`; `idl/holdout.json`; the Codama clients |
| Kotlin core | `AccountLayout`/`Accounts` (the new offsets), `Instructions` (`settleCount`/`settleFinalize` accounts, `joinCohort` with the comeback accounts, `releaseComeback`), `Comeback` (`first`/`last`, the late round, `comebackOwed = min(floor(lost × 10 ÷ 100), chip)`), `Derive` (`member_count_at_start` with late joins), `Settlement` and their tests |
| Crank | `kotlin/crank`: `release_comeback` last in each pass for every profile with an open pledge whose last round settled or whose first day's deadline passed unopened (contracts/crank-cli.md); `status` prints open pledges |
| App | forfeit screen (the approved comeback copy: headline "Win back {amount}", the mid-round and the last-day variants, the button "Join today's {level · length}"), Today's comeback line, stake sheet (the fee sentence and the per-chip line), claim and detail breakdown, H0's Out row "Win back {amount}", tour card 3 "Where a lost stake goes" (the tour has five cards), and the compact Daily 25 card on Today |
| Docs (not spec-kit) | `docs/holdout_decisions.md` (a dated note), `docs/holdout_screen_map.md`, `docs/weaknesses.json` → README table if a row changes, README's description of the split |

**Sequence.** Tests first (vectors and invariants red), then the program, then the IDL/clients and fixtures, then `kotlin/core` and the crank, then the app, then the ledger reset and a local-validator run of quickstart 23b and 23, then a phone install. The devnet deploy is the owner's call and is not a task to run.

**Risks.** `settle_count` gains `reserve_vault` and `league` (mut): a page of 10 pairs must still fit the legacy transaction size (measure; reduce the page or use a lookup table only if it does not) and the compute budget with one extra token transfer per page (the page's pledges move in one transfer). The record-and-late-join transaction (`record_forfeits` + `join_cohort` with two more accounts) must still fit 1232 bytes (ES-17's was 657). `release_comeback` by a stranger must be harmless: the guard reads only chain state, and a repeat finds no open pledge; and it must not release a pledge whose late round is still joinable, nor one whose finish of an opened round is still to be counted (every opened round settled first — `fe75dfd`). A test build's shared progress slot must not let league B's forfeit disturb league A's pledge (quickstart 23b h). The offer on the phone and the program must agree on `first`/`last` and the late window to the second (`>` `missed_deadline`, `≥` `missed_deadline + 1` against the join cutoff, `≤` the first day's deadline): `ComebackTest` replays the program's boundary cases from the fixture. A late joiner raises `member_count` after the start: the survivor counter's denominator (`member_count_at_start`) can rise until the first day's deadline. The fee sentence changed wording (FR-041); string tests over the copy must be updated in the same change.

## Complexity Tracking

No constitution violations to justify.
