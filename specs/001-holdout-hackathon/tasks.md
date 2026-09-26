# Tasks: Holdout — hackathon build

**Input**: Design documents from `specs/001-holdout-hackathon/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Included, and not optional here — constitution VII makes settlement vectors and invariants failing tests **before** the logic they describe.

**Organization**: grouped by user story, in the order the money loop has to come alive. Dates in the checkpoints follow the plan's delivery sequence; the freeze is 28 September and the submission 9 October 08:59 CEST (FR-066).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel — different files, no dependency on unfinished work
- **[Story]**: the user story the task serves (US1, US2, US3, US4, US5, US5A, US6, US7, US8, US9, US10)
- **IDs are stable.** A task added after a review takes a letter suffix in its place (T012a, T017a, T054a, T103a) rather than renumbering everything after it, so an ID in a commit message always means the same task. Suffixed tasks run in their printed position.
- Member-facing names: **the Daily 25** and **a Holdout**; the code keeps `Cohort` and `LeagueConfig` (spec §1a)

## Path conventions

- **Program (Rust/Anchor, WSL2)**: `programs/holdout/src/`
- **Contract tests (TypeScript, Kit + LiteSVM)**: `tests/`
- **Golden fixtures**: `fixtures/`
- **Kotlin**: `kotlin/core/`, `kotlin/repcounter/`, `kotlin/app/`, `kotlin/crank/`

---

## Phase 1: Setup (shared infrastructure)

**Purpose**: a repository three build systems can work in, and a toolchain that runs Anchor at all.

- [X] T001 Install the WSL2 Ubuntu 24.04 toolchain — Rust ≥ 1.89, Solana CLI 3.1.10, Anchor 1.1.2, Node 22, Surfpool via `curl -sL https://run.surfpool.run/ | bash` (research R1, R19; quickstart §1)
- [X] T002 Create the GitHub repository and push the existing history; judges score technical depth from commits (FR-066)
- [X] T003 Scaffold the Anchor workspace: `Anchor.toml` (real program id `Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4`, its keypair kept outside the repository at `~/.config/solana/holdout-program-keypair.json` and copied to `target/deploy/` before any deploy — quickstart §6; `[test]` runner surfpool), root `Cargo.toml`, `programs/holdout/Cargo.toml` with `anchor-lang`/`anchor-spl` 1.1.2, `solana-*` `^3`, feature `test-leagues`
- [X] T004 [P] Generate the Android app with `npx solana-mobile@latest create` (Kotlin Compose Minimal) into `kotlin/`, then fix the template: delete the stray root `dependencies {}` and `android {}` blocks in `kotlin/build.gradle.kts`, replace the placeholder namespace and identity, add `rpc-core` to `kotlin/gradle/libs.versions.toml` (research R3)
- [X] T005 [P] Add the Gradle modules `:core`, `:repcounter`, `:crank` to `kotlin/settings.gradle.kts` with JVM-only source sets (research R12)
- [X] T006 [P] Initialise the TypeScript test package in `tests/package.json` — `@solana/kit`, `@solana/kit-plugin-litesvm`, `litesvm`, `codama`, `vitest` (research R2)
- [X] T007 [P] Install the agent tooling: `npx skills add solana-foundation/solana-dev-skill` and `claude mcp add --transport http solana-mcp-server https://mcp.solana.com/mcp` (research R19)
- [X] T008 [P] Configure formatting and linting: `rustfmt.toml`, `clippy` in CI, `.editorconfig`, ktlint for the Kotlin modules
- [ ] T009 [P] Register the domain (or subdomain) for the MWA identity URI and publish its Digital Asset Links file (research R3)
- [ ] T010 [P] Start gathering devnet SOL on the plan's schedule: deploy rent, ~1.12 SOL for scripted wallets by 22 Sep, 25 SOL in the faucet vault by 8 Oct (research R12, R13)
- [ ] T011 [P] Recruit 5–7 beta testers, open the group chat, send the one-page onboarding note and collect filming consent (`docs/holdout_demo_video.md` §9)
- [X] T012 [P] Install `scrcpy` and check whether the wallet's approval screen records or comes out black (`FLAG_SECURE`), which decides whether those shots need the second camera; record the answer in `docs/holdout_demo_video.md` §4 (research R18)
- [X] T012a [P] **Spike S1 — wallet signing on real hardware**: the fixed template builds; a Seeker connects Seed Vault Wallet over MWA and lands a devnet transaction with `signAndSendTransactions`; a plain Android phone does the same with its own MWA wallet; a phone with no wallet returns `NoWalletFound`; a sponsor-paid transaction built with `web3-solana` and sent with `rpc-core` lands; both libraries run in a plain JVM module. **Fallback**: another MWA wallet, or sol4k for transaction building in that module (research S1). **Blocks T038, T040, T061.**
- [X] T012b [P] **Spike S2 — rep counting on real video**: ≥ 15 FPS pose inference on a Seeker; 10 honest push-up videos counted within 1 rep of 25; 3 cheat videos — half reps, sagging hips, too fast — rejected; an open palm detected in normal indoor light. **Fallback**: lite model, lower analysis resolution, CPU delegate, or thresholds relaxed against the recorded evidence (research S2). **Its output is committed** as `fixtures/rep-traces/s2-angles.json` — the measured per-frame elbow and body-line angles the hysteresis triggers in T048 are set from. **Blocks T046–T050.** **Done differently, 2026-09-23…26 — see `specs/002-rep-placement-rules`**: the evidence is seventeen labelled traces the app recorded on a Seeker (`fixtures/rep-traces/*.trace.csv`, replayed by `RecordedTracesTest` within ±1), not `s2-angles.json`; the cheats covered are half reps, knee push-ups and cobra; sagging hips are judged at 45° only, by a fold below 140°; the open palm was dropped with T047.

**Checkpoint (17–18 Sep)**: `NO_DNA=1 anchor build` succeeds in WSL2, the Gradle project builds, the repository is public, and **spikes S1 and S2 have passed or their fallbacks are chosen — implementation does not begin before that** (constitution, Development Workflow).

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: the maths, the accounts and the shared Kotlin client every story below stands on.

**⚠️ CRITICAL**: no user story work starts until this phase is green.

### Tests first (constitution VII)

- [X] T013 Write `fixtures/settlement-vectors.json` from PRD §19 V1–V22 — equal stakes, mixed tiers, rounding remainders, no-finisher 50/50, carry-over cases
- [X] T014 Write the failing Rust math tests in `programs/holdout/src/math/settlement.rs` (`#[cfg(test)]`) replaying `fixtures/settlement-vectors.json`
- [X] T015 [P] Write the failing schedule tests in `programs/holdout/src/math/schedule.rs` — `deadline_of(k) = start + (k+1) × day_length − 1`, global day index `floor((now − 18,000) ÷ 86,400)`, join cutoff `start − 3,600`, elapsed cohort days, and the INV-3 ceiling `max_days_completed` — which is **one** on the cohort's first day, because a day-0 submission in its first second is valid (data-model §1.3, §5)
- [X] T016 [P] Build the LiteSVM harness with clock control in `tests/helpers/harness.ts` — chain-time travel, cohort builders, account decoding, and the **`ProgramData` upgrade authority patched in place** so `initialize`'s check can pass (and fail) in tests: LiteSVM's `addProgram` does create the account, but with no authority, so the harness sets it (research R2, R14). Also `pdas.ts`, `accounts.ts` (hand-written codecs pinned to Rust by `layout-vectors.json`, which `cargo test` and `accounts.spec.ts` both read) and `errors.ts`; cohorts, memberships and profiles can be **injected** as setup or reached through instructions. A scenario that settles anything takes one of two shapes the builder offers directly: a **fresh test league**, so its cohort is index 0 with no predecessor, or a league whose earlier cohorts it settles first — `settle_count` refuses cohort *i* until cohort *i − 1* of that league is settled (FR-061a, error 6045), and no scenario should meet 6045 by accident
- [X] T017 [P] Write the invariant checker in `tests/invariants/checker.ts` asserting INV-1…INV-9 after every step (spec §8.4). **INV-9 needs a derivation, not a snapshot comparison:** for every profile the step touched, take that wallet's memberships whose cohort is `settled`, order them by cohort `start`, count the finishes since the last forfeit, and compare that with the stored `current_streak` — with `longest_streak` the largest value that derivation reaches across the history. T076 covers the rest of INV-9 by replaying one sequence in two processing orders (FR-061a)
- [X] T017a [P] Write the failing no-oracle check — `tests/scenarios/no-oracle.spec.ts` asserting that no instruction takes a price, a fiat figure or an oracle account among its accounts or arguments, plus `scripts/check-no-oracle.sh` grepping `programs/holdout/src/` for `price`, `oracle`, `pyth`, `switchboard` and `usd` identifiers and failing CI on a hit (the script exits 1 and `tests/scenarios/no-oracle.spec.ts` runs it; wiring it into a workflow waits for the CI file in T008); the only rate use in the repository stays `crank chips publish` (AC-9, constitution III)
- [X] T017b [P] Write the failing league-setup scenario in `tests/scenarios/league-setup.spec.ts` — **`initialize` signed by a stranger fails with `NotUpgradeAuthority` (6003) and creates nothing, while the upgrade authority succeeds and becomes `admin`** (R14; the harness fabricates the `ProgramData` account LiteSVM does not create, T016); **`open_cohort` before any `publish_chip_set` fails with `ChipSetNotPublished` (6012), and succeeds with the same accounts once a set is published** (FR-055b); `create_league` accepts `exercise == 0` and rejects `1` and `2` with `ExerciseNotSupported` (6053); a `league_id ≥ 100` test league is refused unless the build carries the `test-leagues` feature; `day_length`, `cadence`, `join_lock` and the Monday 05:00 UTC start are enforced for real leagues (data-model §2, contracts §1, H30)

### Program foundations

- [X] T018 Implement the settlement maths in `programs/holdout/src/math/settlement.rs` until T014 passes — per-tier `share_t = floor(pool × chip_t ÷ T)`, remainder to the fee destination, `T = 0` → `fee_half = floor(pool ÷ 2)` with the odd unit to carry-over (research R10, FR-032a, FR-037)
- [X] T019 Implement the schedule maths in `programs/holdout/src/math/schedule.rs` until T015 passes
- [X] T020 [P] Define `Config` in `programs/holdout/src/state/config.rs` — admin, operator, sponsor, fee destination, SKR mint, `chip_set.amounts: [u64; 4]`, `chip_set.max_top_tier` (high-water mark of every published `amounts[3]`, what the faucet mints), `faucet.sol_top_up = 20,000,000` lamports, `faucet.sponsor_float = 50,000,000`, `faucet.daily_lamport_cap = 720,000,000` with `lamport_day`/`lamports_today`, `ladder_gate_enabled = false`, `genesis_gate_enabled = false` (data-model §1.1, research R13)
- [X] T021 [P] Define `LeagueConfig` in `programs/holdout/src/state/league.rs` — `first_start` (Monday 05:00 UTC), `cadence = 7 days`, `duration_days`, `reps_per_day = 25`, `day_length`, `exercise: u8` (**`0` = push-ups; `1` negative and `2` decline are reserved and rejected by `create_league` with `ExerciseNotSupported`**), `next_index` (data-model §1.2, H30)
- [X] T022 [P] Define `Cohort` in `programs/holdout/src/state/cohort.rs` — start, join cutoff, duration, reps, day length, `exercise`, the four locked chip amounts, status, `member_count`, `held_stake`, `pool`, `carry_over_received` — the name the account, the instructions and the Kotlin client all use; `carry_over_in` is a fixture input key only — and the write-once settlement fields (data-model §1.3, FR-007)
- [X] T023 [P] Define `Membership` in `programs/holdout/src/state/membership.rs` — `tier: u8` (0–3), `stake: u64` equal to `cohort.chips[tier]` at join and never changed, `days_completed: u8` never above `max_days_completed(now)`, `state`, `counted: bool` set once, `bump` (data-model §1.4)
- [X] T024 [P] Define `Profile` in `programs/holdout/src/state/profile.rs` — `qualified` set on any 7-day clear and never cleared, per-league `cleared`/`forfeited`/`current_streak`/`longest_streak`/`last_changed`, `running`, `pending`, `last_faucet_day` (−1 = never). Record on the two streak fields that only `settle_count` writes them, in cohort order (FR-061a), and on `last_changed` that it holds the **outcome's** instant — a cohort's `end` or the missed deadline — written as a `max`, never `now` (data-model §1.5, FR-060b)
- [X] T025 Implement `guards.rs` in `programs/holdout/src/` — `in_breach(now)`, `standing(now)`, cohort status from chain time, slot normalisation moving `pending` into `running`, the two gate checks (data-model §1.4, §1.5)
- [X] T026 [P] Declare every event in `programs/holdout/src/events.rs` and every error code 6000–6060 in `errors.rs`, exactly as `contracts/program-instructions.md` §4–§5 lists them
- [X] T027 Implement `initialize` in `programs/holdout/src/instructions/initialize.rs` — **decide and record the mock SKR mint's decimals, which the spec never fixes (the T016 harness assumes 6)**; **accepts only the program's current upgrade authority as signer, checked against the `ProgramData` account before anything is created (`NotUpgradeAuthority`, 6003), and stores it as `Config.admin`** (R14); creates `Config`, the mock SKR mint with authority PDA `["mint_authority"]`, the fee destination token account and the faucet vault PDA `["faucet_vault"]`; flags start `false`
- [X] T028 [P] Implement `create_league` and `set_flags` in `programs/holdout/src/instructions/` until the `create_league` block of T017b passes (its `initialize` block is T027's, and its chip-set block stays red until `publish_chip_set` and `open_cohort` land in T058 and T055) — admin only; **`exercise` stored but validated to `0`, rejecting the reserved `1` and `2` with `ExerciseNotSupported` (6053)**; the `league_id ≥ 100` test-league case of the same instruction compiled only behind the `test-leagues` feature — no separate instruction (research R11, R14, H30)

### Shared Kotlin and design foundations

- [X] T029 Generate fixtures in `tests/fixtures/generate.ts` — accounts, PDAs, instruction bytes, derived figures — and commit them under `fixtures/` (contracts/program-instructions §7)
- [X] T030 Implement account codecs, PDA derivation and instruction builders in `kotlin/core/src/main/kotlin/holdout/core/` (`Accounts.kt`, `Pdas.kt`, `Instructions.kt`), with hand-written encodings pinned by the fixtures (research R2 deviation). **Decode only**: nothing off-chain writes a program account, so there is no encoder; `Profile` and the whole `Membership` joined the views in `AccountLayout.kt`
- [X] T031 Write the fixture replay tests in `kotlin/core/src/test/kotlin/holdout/core/FixtureReplayTest.kt` — every instruction fixture rebuilds byte-identically and every account fixture decodes to its recorded fields (decode only, T030)
- [X] T032 [P] Implement `Derive.kt` in `kotlin/core/src/main/kotlin/holdout/core/` — survivor counter, projected pool, projected share, roster order, time left, warning window, **at stake**, carry-over received, time until joining closes (data-model §5)
- [X] T033 [P] Implement chain-time tracking in `kotlin/core/src/main/kotlin/holdout/core/Clock.kt` — cached `{chainNow, slot, readAtElapsedRealtime}`, advanced with `SystemClock.elapsedRealtime()`, never the wall clock (research R7)
- [X] T034 [P] Implement RPC reads in `kotlin/core/src/main/kotlin/holdout/core/Rpc.kt` — `getProgramAccounts` with a `memcmp` on the cohort key and a `dataSlice`, 30-second refresh while a cohort screen is visible (research R8)
- [ ] T035 [P] Transcribe the design tokens into `kotlin/app/src/main/kotlin/holdout/app/ui/theme/` — `Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`: one dark `ColorScheme`, **dynamic colour disabled**, JetBrains Mono in `kotlin/app/src/main/res/font/` (PRD §11, research R20)
- [X] T036 [P] Scaffold the crank in `kotlin/crank/src/main/kotlin/holdout/crank/Main.kt` — argument parsing, RPC config, `status` subcommand printing cohorts plus the faucet vault, the sponsor's balance against its float and the day's lamport spend, single runnable JAR (contracts/crank-cli.md)

**Checkpoint (21 Sep)**: math and schedule tests pass, accounts compile, `kotlin/core` replays every fixture, the theme renders, and the no-oracle check passes.

---

## Phase 3: US1 — First run (Priority: P1)

**Goal**: someone installs the app, connects a wallet, is taught what this is, and can fund themselves.

**Independent test**: a fresh install on a phone with no wallet shows the connect screen and the walkthrough only; after connecting, the tour appears, the faucet funds the wallet, and the ladder lists both Holdout lengths.

- [X] T037 [US1] Write the `claim_faucet` scenario in `tests/scenarios/faucet.spec.ts` — sponsor-paid claim for a zero-SOL wallet, one claim per recipient per day, the **daily lamport budget** counting every lamport that leaves the vault, and vault exhaustion returning a specific error. Include the already-funded recipient that still needs its accounts: a wallet holding `sol_top_up` with no `Profile` and no token account takes rent and fee reimbursement out of the vault, and that must be charged to the budget like any other claim — recycling the same 0.02 SOL across fresh recipients may not buy uncapped account creation (FR-042a, research R13)
- [X] T037a [P] [US1] Write `tests/scenarios/sponsor-float.spec.ts` — the sponsor's float emptied by a plain System Program transfer (anyone can: the key ships in the app), `top_up_sponsor` sent by an unrelated wallet restoring it so a zero-SOL claim succeeds again, refusal with `SponsorFloatSufficient` above half the float, and the refill charged to the same daily lamport budget as a claim (FR-042b, research R13)
- [X] T038 [US1] Implement `claim_faucet` in `programs/holdout/src/instructions/claim_faucet.rs` — mints `chip_set.max_top_tier` — the largest top tier ever published, so the claim funds any open cohort's top chip (FR-042a) — tops the recipient up to `faucet.sol_top_up`, reimburses the sponsor's fee and rent, adds **every lamport that leaves the vault** to `faucet.lamports_today` against `daily_lamport_cap` (resetting it on a new day), sets `last_faucet_day`, needs **no member signature** (contracts/program-instructions §3)
- [X] T038a [US1] Implement `top_up_sponsor` in `programs/holdout/src/instructions/top_up_sponsor.rs` — permissionless, no sponsor signature, refuses above half `sponsor_float`, tops the sponsor up from the faucet vault and charges the transfer to the day's lamport budget (FR-042b, contracts/program-instructions §2)
- [ ] T039 [P] [US1] Build the connect screen in `kotlin/app/src/main/kotlin/holdout/app/wallet/ConnectScreen.kt` — the two-line promise, one line each for the Daily 25 and a Holdout, live Holdouts with still-standing and prize pool (running) and joined count with **at stake** (open), `Connect wallet`, and the quiet judges link (FR-001a, FR-053)
- [ ] T040 [P] [US1] Implement MWA connection in `kotlin/app/src/main/kotlin/holdout/app/wallet/WalletRepository.kt` — `MobileWalletAdapter` with `ConnectionIdentity` on our domain, `blockchain = Solana.Devnet`, `connect`/`disconnect`, auth-token reuse on resume (contracts/android-app §3)
- [ ] T041 [US1] Handle `TransactionResult.NoWalletFound` with the no-wallet screen — what a wallet is, how to get one, and the walkthrough, which plays without one (ES-0, FR-001a)
- [ ] T042 [P] [US1] Build the three-card tour in `kotlin/app/src/main/kotlin/holdout/app/tour/TourScreen.kt` — the Daily 25, what a Holdout is, how reps are counted (the Daily 25 with the phone in front or at 45°, knee push-ups welcome; a Holdout at 45° only and from the toes, in the words of `Stakes.STAKED_RULE` — FR-021a, 002 FR-013/FR-014); skippable at any card, re-openable from About, `tour:{wallet}` written to MMKV (FR-067, research R20)
- [ ] T043 [P] [US1] Build the faucet sheet in `kotlin/app/src/main/kotlin/holdout/app/faucet/FaucetSheet.kt` — one tap, used-today variant with the time it returns, and the can't-pay-out variant that never blames the member (ES-0a, ES-0b)
- [ ] T044 [P] [US1] Build the ladder in `kotlin/app/src/main/kotlin/holdout/app/ladder/LadderScreen.kt` — the Daily 25, both Holdout lengths open, the 21-day requirement stated but not enforced, Human Flag locked (US-1, FR-003)
- [X] T045 [P] [US1] Implement MMKV storage in `kotlin/app/src/main/kotlin/holdout/app/storage/Store.kt` — keys scoped per wallet, cohort (or `challenge`) and day index, with synchronous writes before anything shows as banked. Android backup and device transfer are both opted out of in the manifest and the extraction rules, so no restore can carry an unfinished day to another device (FR-024a, ES-3a). Because nothing syncs, a wiped or replaced phone simply starts that day at zero and the member may finish it there before the deadline; a lost day is never recoverable (data-model §4, research R6, FR-024a, ES-3a)

**Checkpoint**: a brand-new wallet reaches a funded ladder without leaving the app.

---

## Phase 4: US5A — The Daily 25 (Priority: P1)

**Goal**: anyone with a wallet can do 25 verified push-ups today, free, and keep a streak.

**Independent test**: with no Holdout joined, the home screen leads with the Daily 25; 10 + 10 + 5 across three sittings completes it and the streak goes up by one; nothing touches the chain.

- [X] T046 [P] [US5A] Implement the pre-flight checks in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Preflight.kt` — four visual checks, each failure saying what to fix, **no audio** (FR-019)
- [~] T047 **DROPPED 2026-09-22** (was: implement palm liveness in `kotlin/repcounter/.../Liveness.kt`). Built, tested and then removed after the first sitting on real hardware: with the pills green the member is already face-down, and the palm asked them to lift a hand off the floor. Replaced by `ReadyGate` — the top of a push-up, held about a second, which the pose model already sees and which reuses `RepRules` so it cannot drift from the counting rule. Consequences recorded in spec FR-020 and OD-3, PRD §8.3 and §8.8, and the README's known weaknesses; the hand model went with it, ~6 MB off the APK
- [X] T048 [US5A] Implement the push-up state machine in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/RepCounter.kt`. **The rule** (FR-021, PRD §8): a rep counts only when one cycle shows top — elbow > 160°, shoulder–hip–ankle within ±10° of straight; bottom — elbow < 90°, body line held; back to top; cycle time 0.8–6 s. **The implementation** (research R4): angles from 3D world landmarks, one-euro smoothing, and hysteresis triggers at **≥ 165° top / ≤ 85° bottom** so the model's ~2° error cannot double-count; frames below the landmark presence threshold are dropped, not scored. A rep missing the rule is discarded silently with a hint — "Go lower", "Keep your body straight", "Slow down" — never an error. **Spike S2 sets the final trigger values from its measured angle table.**
- [X] T049 [US5A] Write the recorded-trace tests in `kotlin/repcounter/src/test/kotlin/holdout/repcounter/TraceTest.kt` — 10 honest videos counted within 1 rep of 25, 3 cheat videos rejected, palm timeout at 30 s. **Assert the rule, never the trigger values**, so tuning the hysteresis after S2 does not rewrite the tests
- [X] T050 [US5A] Build the sitting screens in `kotlin/app/src/main/kotlin/holdout/app/sitting/` — start card, pre-flight, liveness, countdown, counting with the skeleton overlay and bank ring, stopped part-way, complete (PRD §10.1 screen 8). **As built** (with `specs/002-rep-placement-rules`): liveness is the `ReadyGate` hold (T047); a set-up guide dialog opens first; the bank ring is a line, "N banked · M to go", under the count
- [X] T051 [US5A] Bank verified reps in blocks of 10 in `kotlin/app/src/main/kotlin/holdout/app/sitting/BankingController.kt` — written to MMKV before the UI shows them, carried across sittings for the day, reps short of the next block discarded when a sitting ends (FR-024, FR-023)
- [X] T052 [P] [US5A] Implement the streak in `kotlin/app/src/main/kotlin/holdout/app/streak/StreakStore.kt` — `+1` on a completed day **from either source: a Daily 25 or a cohort day counts as that day's training, so nobody trains twice** (FR-050); broken by a day with none; untouched by a forfeit; turning over at 05:00 UTC by chain time. Its tests assert both the shared-day rule and that a completed day stops counting once the boundary passes, so the app never shows yesterday's done state (FR-049…FR-052, ES-9)
- [ ] T053 [P] [US5A] Build the home screen's Daily 25 variant in `kotlin/app/src/main/kotlin/holdout/app/home/HomeScreen.kt` — the hero 25 with Start and the countdown, the streak chip, and the Holdouts open to join below it with **at stake**, joined count, carry-over and the closing countdown (FR-053). **Built so far**: the hero 25 with Start, the countdown and the streak chip; the Holdouts list waits on chain reads (T034)

**Checkpoint**: the free habit loop works end to end on a device, with no chain writes. **This plus Phase 3 is the MVP.**

---

## Phase 5: US6 — Staking into a Holdout, with US2 the lobby it waits in (Priority: P1)

**Goal**: a member picks a chip, stakes, sits in the lobby, and can leave until joining closes.

**Independent test**: a join and a leave landing one second before the cutoff both succeed with a full refund on the leave; both are rejected one second after it; the cohort then shows as locked until it starts.

- [X] T054 [US6] Write `tests/scenarios/join-leave.spec.ts` — join at each tier, refusal of any amount that is not one of the cohort's four, leave with a full refund and no deduction, one lobby place at a time (AC-13, FR-055, FR-009)
- [X] T054a [P] [US2] Write `tests/scenarios/lobby.spec.ts` — the cutoff decided by chain time alone: a join and a leave one second before it both succeed, both are rejected one second after it; `Locked` holds from the cutoff to the start with a final roster; a lobby place is refused in a cohort starting before the member's running one ends (ES-12, ES-12a, ES-13, ES-14, FR-006a, FR-006b, FR-010)
- [X] T055 [US6] Implement `open_cohort` in `programs/holdout/src/instructions/open_cohort.rs` — permissionless, succeeds only for `i == next_index`, after cohort `i − 1` has started, and once a chip set exists (`chip_set.version > 0`, else `ChipSetNotPublished` 6012 — otherwise the cohort would lock four zeroed amounts for life); copies the current chip set and every parameter including `exercise` (research R11, FR-007, FR-007a, FR-055b)
- [X] T056 [US6] Implement `join_cohort` in `programs/holdout/src/instructions/join_cohort.rs` — status `Open`, `tier ≤ 3`, gates, slot normalisation, `Membership` created with the member paying rent, stake moved to the vault, `held_stake` and `member_count` raised (contracts/program-instructions §3)
- [X] T057 [US6] Implement `leave_cohort` in `programs/holdout/src/instructions/leave_cohort.rs` — status `Open` only, full refund with no deduction, membership closed and its rent returned, `profile.pending` cleared (FR-009)
- [X] T058 [P] [US6] Implement `publish_chip_set` in `programs/holdout/src/instructions/publish_chip_set.rs` — operator only, takes `amounts: [u64; 4]` and no price, raises `max_top_tier` to `max(max_top_tier, a3)` and never lowers it (constitution III, FR-055a, FR-042a)
- [ ] T059 [P] [US6] Build the stake-confirm sheet in `kotlin/app/src/main/kotlin/holdout/app/stake/StakeSheet.kt` — four tier chips with their exact SKR amounts and `≈ $` labels, daily obligation, time to the cutoff and the start, the pool including carry-over, and the sentence "if everyone finishes, you get your stake back plus your share of any carry-over" — what FR-041 promises, and what the sheet's own carry-over line already shows (US-6, FR-041, FR-059). **Above the stake button, `Stakes.STAKED_RULE`**: at 45° only (not in front, not straight beside), full push-ups from the toes, knee push-ups do not count — read before anything is staked, not discovered at the first sitting; every sitting of the cohort then runs with `Stakes.STAKED` (FR-021a)
- [ ] T060 [P] [US2] Build the lobby in `kotlin/app/src/main/kotlin/holdout/app/lobby/LobbyScreen.kt` — countdowns to cutoff and start, live roster count, held stake, unconditional leave, and the locked variant with the final roster and no actions (US-2, ES-12a)
- [ ] T061 [US6] Send transactions through MWA in `kotlin/app/src/main/kotlin/holdout/app/wallet/Signer.kt` — `transact { signAndSendTransactions(...) }`, the member as fee payer, retryable errors surfaced plainly (contracts/android-app §3)
- [X] T062 [P] [US6] Implement `crank chips publish --rate <usd-per-skr>` in `kotlin/crank/src/main/kotlin/holdout/crank/Chips.kt` — converts the four tiers, rounds down to the nearest 10 SKR, minimum 10 SKR (research R15)

**Checkpoint**: a member can stake into a Holdout and get out again before the cutoff.

---

## Phase 6: US3 — A day's reps inside a Holdout (Priority: P1)

**Goal**: a cohort day is completed on the device and lands on-chain before its deadline.

**Independent test**: 10 + 10 + 5 across three sittings records the day on-chain only after the last rep; a sitting stopped at 17 keeps 10; killing the app keeps every banked block; airplane mode completes the day and one tap submits it when the network returns.

- [X] T063 [US3] Write `tests/scenarios/submit-day.spec.ts` — one completed day per member per day, a day-0 submission in the cohort's first second accepted and leaving `days_completed == 1`, `days_completed` never past `max_days_completed(now)` (INV-3), rejection once in breach (INV-8), rejection after the cohort's final deadline
- [X] T064 [US3] Implement `submit_day` in `programs/holdout/src/instructions/submit_day.rs` — member-signed, `day_index` and a 32-byte `proof_hash` emitted in `DayCompleted` and not stored (research R16, FR-025)
- [ ] T065 [US3] Wire the cohort day into the sitting flow in `kotlin/app/src/main/kotlin/holdout/app/sitting/CohortDayController.kt` — target and exercise read from `Cohort.reps_per_day` and `Cohort.exercise`, never constants; an unknown exercise value refuses the sitting rather than guessing (FR-014, H30)
- [ ] T066 [P] [US3] Implement the offline path in `kotlin/app/src/main/kotlin/holdout/app/sitting/PendingSubmission.kt` — the warning before an offline cohort sitting and again on completion, the pending record in MMKV, and "Done — tap to submit" once online. **Phase 1 half only**; T098 replaces the tap with automatic submission if spec Phase 2 lands (FR-024b, ES-3b)
- [ ] T067 [P] [US3] Show reps still needed and time left before every sitting, with the clear warning inside the last 10 minutes and no refusal to start (FR-047)

**Checkpoint**: a real day in a real Holdout can be completed and submitted from a phone.

---

## Phase 7: US4 — Missing a day (Priority: P1)

**Goal**: a missed deadline forfeits the stake, funds the pool, and tells the member what happened.

**Independent test**: with the crank stopped, a member who misses a day cannot submit, and when the crank runs the pool rises by the stake minus exactly 20%.

- [X] T068 [US4] Write `tests/scenarios/forfeit.spec.ts` — breach derived from chain time before any automation, `record_forfeits` idempotent, pages in any order and repeated, fee exactly `floor(stake × 20 ÷ 100)` (AC-2, AC-5, INV-7)
- [X] T069 [US4] Implement `record_forfeits` in `programs/holdout/src/instructions/record_forfeits.rs` — up to 10 `(Membership, Profile)` pairs, skips pairs not in breach or already forfeited, moves the fee, raises `pool`, lowers `held_stake`, raises `profile.forfeited`, sets `last_changed = max(last_changed, deadline_of(days_completed))` — the missed deadline, never `now`, so the crank's timing cannot reorder tied members (FR-060b) — clears the `running` slot, and **leaves the league streak untouched** — the streak is written by `settle_count`, in cohort order (research R9, FR-027, FR-028, FR-061a)
- [ ] T070 [P] [US4] Build the forfeit screen in `kotlin/app/src/main/kotlin/holdout/app/cohort/ForfeitScreen.kt` — what happened, the streak explicitly still alive, and the two ways back: today's Daily 25 and the next 7-day Holdout (US-4, FR-052)
- [X] T071 [US4] Implement `crank run` in `kotlin/crank/src/main/kotlin/holdout/crank/Run.kt` — opens due cohorts **before settling any, so a no-finisher finalize always has the league's open lobby to deliver into (6047 never survives a pass; if it fires, open the next cohort and retry inside the same run)**, pages forfeits for breached members, settles finished cohorts **oldest first, so counting never hits `PreviousCohortUnsettled`**, and sends `top_up_sponsor` whenever the sponsor sits below half its float so a drained sponsor never outlasts one pass; idempotent, with `--dry-run` (contracts/crank-cli.md, FR-042b, FR-061a). **Built 2026-09-26** (`Run.kt`, `RunTest`): the planning rules are pure and tested alone; a whole pass is tested against a cluster in miniature. A failed step is reported and the pass carries on with what does not depend on it (exit 2, full summary printed). Not yet run against devnet (T093)

**Checkpoint**: money moves on a missed day, and nobody has to be online for it to be true.

---

## Phase 8: US7 — Watching the crowd thin (Priority: P1)

**Goal**: the survivor counter and the prize are the screen, and they move at the deadline itself.

**Independent test**: with the crank stopped, one second after a deadline every device already excludes the members who missed it; recording their forfeits later changes no displayed figure.

- [X] T072 [US7] Write `tests/scenarios/derived-figures.spec.ts` — figures computed at the deadline match the figures after recording, for a 1,000-member cohort (FR-038a, AC-8)
- [ ] T073 [US7] Build the cohort home variant in `kotlin/app/src/main/kotlin/holdout/app/home/CohortHome.kt` — survivor counter and prize pool as the largest things, projected share, days remaining, the member's own record, and the counter rolling rather than cutting (FR-038, PRD §10.1a, §11.5)
- [ ] T074 [P] [US7] Build the roster in `kotlin/app/src/main/kotlin/holdout/app/roster/RosterScreen.kt` — still standing first, then forfeited by days lasted, scripted members labelled from the bundled list (FR-062, FR-064)
- [ ] T075 [P] [US7] Build cohort detail in `kotlin/app/src/main/kotlin/holdout/app/cohort/CohortDetailScreen.kt` — days remaining, own completion record, projected share, survivor history (US-7)
- [X] T076 [US7] Run the randomised invariant suite at 1,000 members in `tests/invariants/randomised.spec.ts` — joins, sittings, misses, claims, pages out of order and repeated, INV-1…INV-9 after every step, and **one sequence replayed in two processing orders** — every forfeit recorded before the earlier cohorts settle, then the reverse — with every profile required to end identical (INV-9, FR-061a). **This is spike S3** (research S3): run it as soon as the instruction set first compiles, not at the end of the phase; if it fails, cut the page size or add address lookup tables before building further (AC-7)

**Checkpoint**: the number that sells the product is live and provably backed by chain state.

---

## Phase 9: US8 — Settlement and the claim (Priority: P1)

**Goal**: every member settles on the same figures at the same moment, and a finisher claims.

**Independent test**: two finishers on equal stakes receive byte-identical payouts; the rounding remainder reaches the fee destination; the sum of claims equals total finisher stake plus pool minus that remainder, to the unit.

- [X] T077 [US8] Write `tests/scenarios/settlement.spec.ts` — `settle_count` paged in any order and repeated, `settle_finalize` once only (INV-5), remainder to the fee destination, no-finisher 50/50 with the odd unit to carry-over, a cohort that received carry-over and also has no finishers (AC-3, AC-11, ES-7…ES-7b). **Two account-substitution cases**: a page carrying a `(Membership, Profile)` pair from another cohort fails with `AccountMismatch` (6060) and leaves both cohorts' `counted`, `member_count` and the foreign membership untouched; and `settle_finalize` on a no-finisher cohort fails with `CarryOverTargetRequired` (6046) when the league's cohort `next_index − 1` is withheld, so no caller can decide by omission whether the carry-over is delivered or held (FR-030a, contracts §2). Each cohort this file settles is index 0 of its own test league unless the case is about ordering, so error 6045 never fires incidentally (T016). **Carry-over destination is order-free**: finalize a no-finisher cohort with no open lobby and assert `CarryOverTargetNotOpen` (6047) with every account byte unchanged, then open the league's next cohort and finalize again; run the same pair in the other order — lobby opened first, then finalize — and assert both runs leave the carry-over in the same cohort with the same `carry_over_received` (FR-037a, FR-030a)
- [X] T077a [US8] Write `tests/scenarios/progress-order.spec.ts` — the interleaving that made streaks order-dependent: a member clears cohort A, joins the reserved cohort B, misses B's first day, and B's forfeit is recorded **before** A is counted. Their current streak must end at zero and their `longest_streak` must be identical whichever order the two cohorts are processed in; also assert that `settle_count` on B fails with `PreviousCohortUnsettled` until A has settled, and that a cohort of the other league in between changes nothing. Then the leg FR-002 promises: the same member **joins again after forfeiting**, clears cohort C, and the streak reads 1 — a forfeit ends a cohort, never the member's place in the league (FR-002, FR-061a, INV-9)
- [X] T078 [US8] Implement `settle_count` in `programs/holdout/src/instructions/settle_count.rs` — **validates every remaining-account pair before mutating anything: `membership.cohort == cohort` and the profile derived from the membership's wallet, else `AccountMismatch`** (contracts §2); refuses cohort `i > 0` until the league's cohort `i − 1` is settled (`PreviousCohortUnsettled`), forfeits any unrecorded breach in the page first, then counts finishers per tier into `finishers_per_tier` and `total_finisher_stake`, writes the streak for every membership it counts — up for a finisher, zero for a forfeit, whenever that forfeit was recorded — sets `last_changed` from the outcome's instant (`cohort.end` for a clear, the missed deadline for a forfeit) as a `max`, and sets `counted` once (research R9, R10, FR-061a)
- [X] T079 [US8] Implement `settle_finalize` in `programs/holdout/src/instructions/settle_finalize.rs` — constant-time per-tier shares, remainder recorded and sent, carry-over delivered into the league's open lobby (FR-032a, FR-037, FR-037a). **The target is neither optional nor substitutable**: a no-finisher cohort must be given the league's cohort `next_index − 1` and its vault, seeds-derived, and that cohort must be another cohort and `Open` — otherwise `CarryOverTargetRequired` or `CarryOverTargetNotOpen`, and nothing is written. Phase 1 therefore never sets `carry_over_held`: it refuses instead of parking money the caller's timing chose to strand (FR-030a)
- [X] T080 [US8] Implement `claim_payout` in `programs/holdout/src/instructions/claim_payout.rs` — pays `stake + share_tier` read from the finalised cohort, deliberate member action, claimable indefinitely (FR-034, FR-035)
- [ ] T081 [P] [US8] Build the claim screen in `kotlin/app/src/main/kotlin/holdout/app/claim/ClaimScreen.kt` — the amount, the breakdown of stake plus share, the single gradient sweep on success, the settled transaction link, and the claimed state (PRD §10.1 screen 10, §11.5)
- [ ] T082 [P] [US8] Show the settling state in cohort detail — "you finished · settlement in progress" until finalisation (contracts/android-app §5)

**Checkpoint (by 21 Sep, contract side)**: the full money loop passes on LiteSVM, including a 1,000-member cohort.

---

## Phase 10: US5 — Qualifying (Priority: P2)

**Goal**: clearing a 7-day Holdout records the 21-day unlock permanently, even though the gate is off.

**Independent test**: a member who clears a 7-day cohort has `qualified = true`; with the flag flipped on, a wallet without it is refused a 21-day join.

- [X] T083 [US5] Write `tests/scenarios/qualify.spec.ts` — unlock recorded on every 7-day clear with the gate off, refusal with the gate on, unlock never revoked by a later forfeit; the two cohorts it needs are consecutive in one test league and are settled in that order (T016, FR-061a) (AC-1, FR-003a, FR-004)
- [X] T084 [US5] Set `qualified` inside `settle_count` when the cleared cohort belongs to league 0, and enforce `ladder_gate_enabled` in `join_cohort` (FR-003, FR-003a)
- [ ] T085 [P] [US5] Show the unlock in the ladder and after a clear — stated plainly, with the note that both lengths are open in this build (US-5)

**Checkpoint**: every 7-day clear records the unlock with the gate off, and flipping `ladder_gate_enabled` on refuses an unqualified 21-day join — proving the flag is the only difference between this build and an enforced ladder (AC-1). Nothing downstream waits on this phase.

---

## Phase 11: US9 — A judge installs the app cold (Priority: P1)

**Goal**: on any day of judging the app is populated, honest about itself, and explains itself without a wallet.

**This phase is not last in time.** It runs in parallel from 22 September: the judged deploy (T093) and the first scripted joins (T087) have to be live long before the app stories finish, because cohort #3's lobby must be open and joinable by **28 Sep 04:00 UTC**.

**Independent test**: a fresh install on any judging day finds a running Holdout at or above its floor, every scripted member labelled, and the walkthrough playable with no wallet connected.

- [ ] T086 [US9] Generate the 56 scripted wallets, commit the public keys to `kotlin/core/src/main/resources/scripted-wallets.json`, and keep the keypairs in the crank's secret store (research R12, FR-043a)
- [ ] T087 [US9] Implement `crank scripted run` in `kotlin/crank/src/main/kotlin/holdout/crank/Scripted.kt` — fills each cohort only to its league floor (20 for 7-day, 12 for 21-day) computed from the live member count, half at lobby open and the rest 24 h before the cutoff, then submits days for wallets not scheduled to forfeit (FR-043, research R12)
- [ ] T088 [P] [US9] Implement `crank scripted plan` and `crank scripted fund` — print floor, real and scripted counts, deficit, tiers and forfeit days; top each wallet up once to 0.02 SOL (contracts/crank-cli.md)
- [ ] T089 [P] [US9] Label scripted members wherever they appear, from the bundled list, with no server (FR-064)
- [ ] T090 [P] [US9] Build the disclosure screen and the About / for judges route in `kotlin/app/src/main/kotlin/holdout/app/about/` — the walkthrough player (Media3 ExoPlayer, bundled MP4, "recording in progress" until it exists), the scripted-wallet list, and the known weaknesses **rendered from `kotlin/app/src/main/assets/weaknesses.json`, the one source T103 also generates the README table from**, so the two can never drift (FR-043a, FR-044, constitution XI)
- [X] T091 [US9] Implement `crank init` in `kotlin/crank/src/main/kotlin/holdout/crank/Init.kt` — `initialize` **signed by the upgrade-authority keypair**, then the **first `publish_chip_set` from `--rate` before any league exists** so no cohort can open against a zero chip set (FR-055b, 6012), both leagues with `first_start` Monday 5 October 05:00 UTC (moved from 28 September by the product owner, 2026-09-26), the mock mint, the funded faucet vault (quickstart §6). **Built 2026-09-26** (`Init.kt`, `InitTest`, with `initialize`/`create_league`/transfer builders pinned to `fixtures/instructions.json` in `SetupInstructionsTest`): every step reads the chain first and is skipped when done, every input is checked before the first send, `--first-start` must be Monday 05:00 UTC, `--fund-faucet <sol>` tops the vault up **to** a balance. **Not yet run against devnet** — the program is not deployed there (T093). The `--test-league` rehearsal variant in quickstart §5 is not built
- [ ] T092 [US9] *(Amended 2026-09-26, constitution v1.4.0.)* Deploy the devnet program **built with `test-leagues`**, and run the one disclosed short test cohort on it: the whole cycle with the beta testers, recorded for the walkthrough and demo video (FR-044, FR-044a)
- [X] T091a [US9] Implement `crank init --test-league` in `kotlin/crank/src/main/kotlin/holdout/crank/Init.kt` — admin-only `create_league` for id 100 with `--day-seconds`, `--reps` and `--days` and a first start the admin chooses, alongside the judged leagues; refused against a program built without `test-leagues` (quickstart §5, FR-044a). **Built 2026-09-26** (`Init.runTestLeague`, `TestLeagueTest`): `--first-start` is required, `--cadence` defaults to back-to-back cohorts and `--join-lock` to 300 s; a 6010 refusal is reported as a build without `test-leagues`
- [ ] T089a [P] [US9] Label the short test cohort, with its day length, everywhere it appears — ladder, stake sheet, lobby, home, roster, cohort detail — from `LeagueConfig.league_id ≥ 100` (FR-044a)
- [ ] T093 [US9] Run the `NO_DNA=1 anchor test` Surfpool smoke suite in `tests/smoke/` — one happy path per instruction plus a time-travelled settlement — then deploy the judged program keeping its upgrade authority (FR-007b, quickstart §6)
- [ ] T094 [US9] Schedule `crank run && crank scripted run` every 10 minutes as a Fly.io machine via `fly.toml`, with `.github/workflows/crank.yml` as the disabled-by-default fallback (research R12)
- [ ] T095 [US9] Run the beta test on the rehearsal deployment, 24–26 Sep: seven 10-minute days at 5 reps, each tester completing a full cycle, findings triaged the same day (`docs/holdout_demo_video.md` §9)

**Checkpoint (5 Oct, moved from 28 Sep on 2026-09-26)**: the first judged cohort starts with real testers and scripted members in it. **Feature freeze.** The short test cohort (FR-044a) has run the whole cycle before it.

---

## Phase 12: Spec Phase 2 — deferred items (Priority: P3)

**Built only after every Phase 1 check is green, and only if it lands before the 5 October freeze** (spec §3a). Four separate items live here: league leaderboards (US10), automatic offline submission (US3), carry-over delivery (US8) and weekly chip re-pricing (US6). Cut them independently, smallest first, and log every cut in `docs/holdout_decisions.md`.

- [ ] T096 [P] [US10] Write `tests/scenarios/leaderboard.spec.ts` — ranking by survival rate, the 3-cohort minimum, the tie-breaks (including two members whose outcomes share an instant, ordered by wallet address, and the same ranking whichever page order settled them), "not ranked yet" below the line, a running or locked cohort counted in neither figure, and **a cohort mid-count contributing to nobody's figures until it is settled** (AC-14, FR-060a, FR-060b)
- [ ] T097 [US10] Build the leaderboard screen in `kotlin/app/src/main/kotlin/holdout/app/leaderboard/LeaderboardScreen.kt` — figures as of the league's last **settled** cohort, subtracting any membership already counted in a cohort still `Settling` (one `getProgramAccounts` for that cohort's memberships, FR-060a); survival rate, cohorts played and cleared, current and longest league streak, shortened addresses, no amounts anywhere (FR-060…FR-063)
- [ ] T098 [P] [US3] Implement automatic offline submission — the pending day written by T066 sent as soon as a connection returns, with "Done — waiting for network" until it lands, replacing the Phase 1 tap (FR-024b Phase 2, ES-3b)
- [ ] T099 [P] [US8] Implement `deliver_carry_over` in `programs/holdout/src/instructions/deliver_carry_over.rs` — moves a held carry-over exactly once into the next cohort of that league when it opens (FR-037a, INV-6). Phase 1 cannot produce a held carry-over, so this covers only the league nobody ever opens another cohort in; cutting it strands nothing that Phase 1 created
- [ ] T100 [P] [US6] Implement `crank chips publish --auto --if-drift 0.20` — weekly Monday check against a public rate, exits without publishing when no rate is available (FR-056, FR-057)

**Checkpoint (27 Sep)**: each item has either landed with its acceptance scenario green, or been cut and logged in `docs/holdout_decisions.md` with its Phase 1 stand-in still in place. Phase 13 does not wait for any of it.

---

## Phase 13: Polish and cross-cutting

- [ ] T101 [P] Add the manifest check `:app:checkNoAudioPermission` to `kotlin/app/build.gradle.kts` — the build fails if `RECORD_AUDIO` ever appears (FR-022a)
- [ ] T102 [P] Capture outbound traffic during a sitting and confirm only RPC calls leave the device (AC-10, constitution X)
- [ ] T103 [P] Write the known weaknesses with their real fixes into `kotlin/app/src/main/assets/weaknesses.json` and render the README table from it — the bundled faucet sponsor key with its daily lamport budget and permissionless refill, the retained upgrade authority, client-signed days, device-local banked reps, no audio anti-spoofing, the off-chain rate, scripted members, devnet. T090 shows the same file in the app (constitution XI)
- [ ] T103a [P] Check the app's own wording before the freeze — grep `kotlin/app/src/main/res/values/strings.xml` and the Compose sources for "winnings", "returns", "yield", "earnings", "bet", "wager", "gamble", "odds" and "luck", confirm every fiat figure carries "≈", and confirm the test-network notice is present (FR-039, FR-040, FR-042, constitution XIII)
- [ ] T104 [P] Write the README's clone-and-run instructions and verify them on a machine that has never built the project (FR-066, AC-19)
- [ ] T105 [P] Draw the app icon, wordmark and splash from the design tokens (PRD §11.8)
- [ ] T106 *(Amended 2026-09-26: filmed on the disclosed short test cohort, labelled on screen — FR-044a.)* Film the cohort #3 shot list, 27 Sep – 5 Oct — join and lobby, daily sittings, the forfeiter's forfeit, the daily 05:05 UTC captures, settlement and the real claim, each row logged with its transaction signature (`docs/holdout_demo_video.md` §2, §5)
- [ ] T107 Cut the two exports, 5–7 Oct — the 16:9 three-minute demo video and the 9:16 in-app walkthrough under 40 MB — plus the short explainer for the tour's card 2 (research R18, FR-067)
- [ ] T108 Build the final APK with the walkthrough bundled, install it on a Seeker and a plain Android phone, and check every §3 screen appears in the video (AC-19)
- [ ] T109 Write the pitch deck, disclosing scripted members and the test network, and submit the APK, repository, video and deck on 8 Oct. Before submitting, log every cut Phase 2 item in `docs/holdout_decisions.md` and confirm the README's weakness table matches what the app actually ships (FR-066, spec §3a, constitution XI)
- [ ] T110 Monitor the live period 9 Oct – 11 Nov from `crank status` — the faucet vault, the sponsor's float, the day's lamport spend, the crank's passes, the scripted floors — fixing only contract defects, by upgrade (FR-007b, FR-042b, FR-043)

---

## Dependencies

**Phase order**: Setup → Foundational → US1 → US5A → US6/US2 → US3 → US4 → US7 → US8 → US5 → US9 → US10 → Polish.

**Hard blockers**:

- Everything waits on Phase 2. The fixtures (T029) pin the Kotlin client, so T030 cannot start before them.
- `open_cohort` (T055) precedes every cohort-scoped story.
- `record_forfeits` (T069) precedes settlement (T078), because `settle_count` forfeits stragglers first.
- Cohorts are **counted in index order**: `settle_count` (T078) refuses cohort *i* until cohort *i − 1* of that league is settled (FR-061a), so `crank run` (T071) settles oldest first and T077a tests the interleaving that rule exists for.
- The faucet (T038) precedes any device test that stakes, including the beta test (T095). The sponsor refill (T038a) precedes leaving the faucet unattended — without it a drained fee float ends zero-SOL onboarding instead of delaying it.
- The judged deploy (T093) must land before the first judged cohort's join cutoff, **5 Oct 04:00 UTC** (moved 2026-09-26). The walkthrough and demo video come from the short test cohort (T092, FR-044a), which must finish before the 9 October submission.
- T106 cannot start before T093 and the release candidate; T107 cannot start before T106.

**Story independence**: US5A (the Daily 25) touches no chain state and can ship alone. US1 needs only the faucet instruction. **US2 is not separately deliverable** — a lobby needs the join it holds, so it ships inside Phase 5 with its own contract assertions (T054a) and screen (T060). Everything from US6 onward shares the cohort lifecycle, so they land in order.

## Parallel execution examples

- **Phase 1**: T004–T012b in parallel — different toolchains, no shared files. The two spikes (T012a, T012b) need hardware, so start them first; everything else can proceed while they run.
- **Phase 2**: T020–T024 (one account file each), then T032–T036 in parallel.
- **Phase 3**: T037a with T037 (separate scenario files), then T039, T042, T043, T044, T045 — separate screens and stores.
- **Phase 4**: T046, T047 in parallel; T052, T053 in parallel once banking exists.
- **Phase 13**: T101–T105 in parallel while filming runs.

## Implementation strategy

1. **MVP** = Phases 1–4: install, connect, tour, faucet, and the Daily 25 counted on the device. It demonstrates the camera, which is the product's magic, without any money.
2. **The money loop** = Phases 5–9, in order. Nothing here is optional: the spec's acceptance criteria 1–11 and 13 all live in it.
3. **Demo integrity** = Phase 11, which must be live before the freeze because judging depends on it.
4. **Phase 2 of the spec** = Phase 12 here, cut without regret if 26–27 September is tight — each item has a Phase 1 stand-in, and each cut is logged in the decision record.
5. **Everything after the freeze** is Phase 13: filming, writing and the submission.
