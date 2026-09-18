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

- [ ] T001 Install the WSL2 Ubuntu 24.04 toolchain — Rust ≥ 1.89, Solana CLI 3.1.10, Anchor 1.1.2, Node 22, Surfpool via `curl -sL https://run.surfpool.run/ | bash` (research R1, R19; quickstart §1)
- [ ] T002 Create the GitHub repository and push the existing history; judges score technical depth from commits (FR-066)
- [ ] T003 Scaffold the Anchor workspace: `Anchor.toml` (program id, `[test]` runner surfpool), root `Cargo.toml`, `programs/holdout/Cargo.toml` with `anchor-lang`/`anchor-spl` 1.1.2, `solana-*` `^3`, feature `test-leagues`
- [ ] T004 [P] Generate the Android app with `npx solana-mobile@latest create` (Kotlin Compose Minimal) into `kotlin/`, then fix the template: delete the stray root `dependencies {}` and `android {}` blocks in `kotlin/build.gradle.kts`, replace the placeholder namespace and identity, add `rpc-core` to `kotlin/gradle/libs.versions.toml` (research R3)
- [ ] T005 [P] Add the Gradle modules `:core`, `:repcounter`, `:crank` to `kotlin/settings.gradle.kts` with JVM-only source sets (research R12)
- [ ] T006 [P] Initialise the TypeScript test package in `tests/package.json` — `@solana/kit`, `@solana/kit-plugin-litesvm`, `litesvm`, `codama`, `vitest` (research R2)
- [ ] T007 [P] Install the agent tooling: `npx skills add solana-foundation/solana-dev-skill` and `claude mcp add --transport http solana-mcp-server https://mcp.solana.com/mcp` (research R19)
- [ ] T008 [P] Configure formatting and linting: `rustfmt.toml`, `clippy` in CI, `.editorconfig`, ktlint for the Kotlin modules
- [ ] T009 [P] Register the domain (or subdomain) for the MWA identity URI and publish its Digital Asset Links file (research R3)
- [ ] T010 [P] Start gathering devnet SOL on the plan's schedule: deploy rent, ~1.12 SOL for scripted wallets by 22 Sep, 25 SOL in the faucet vault by 8 Oct (research R12, R13)
- [ ] T011 [P] Recruit 5–7 beta testers, open the group chat, send the one-page onboarding note and collect filming consent (`docs/holdout_demo_video.md` §9)
- [ ] T012 [P] Install `scrcpy` and check whether the wallet's approval screen records or comes out black (`FLAG_SECURE`), which decides whether those shots need the second camera; record the answer in `docs/holdout_demo_video.md` §4 (research R18)
- [ ] T012a [P] **Spike S1 — wallet signing on real hardware**: the fixed template builds; a Seeker connects Seed Vault Wallet over MWA and lands a devnet transaction with `signAndSendTransactions`; a plain Android phone does the same with its own MWA wallet; a phone with no wallet returns `NoWalletFound`; a sponsor-paid transaction built with `web3-solana` and sent with `rpc-core` lands; both libraries run in a plain JVM module. **Fallback**: another MWA wallet, or sol4k for transaction building in that module (research S1). **Blocks T038, T040, T061.**
- [ ] T012b [P] **Spike S2 — rep counting on real video**: ≥ 15 FPS pose inference on a Seeker; 10 honest push-up videos counted within 1 rep of 25; 3 cheat videos — half reps, sagging hips, too fast — rejected; an open palm detected in normal indoor light. **Fallback**: lite model, lower analysis resolution, CPU delegate, or thresholds relaxed against the recorded evidence (research S2). **Blocks T046–T050.**

**Checkpoint (17–18 Sep)**: `NO_DNA=1 anchor build` succeeds in WSL2, the Gradle project builds, the repository is public, and **spikes S1 and S2 have passed or their fallbacks are chosen — implementation does not begin before that** (constitution, Development Workflow).

---

## Phase 2: Foundational (blocking prerequisites)

**Purpose**: the maths, the accounts and the shared Kotlin client every story below stands on.

**⚠️ CRITICAL**: no user story work starts until this phase is green.

### Tests first (constitution VII)

- [ ] T013 Write `fixtures/settlement-vectors.json` from PRD §19 V1–V22 — equal stakes, mixed tiers, rounding remainders, no-finisher 50/50, carry-over cases
- [ ] T014 Write the failing Rust math tests in `programs/holdout/src/math/settlement.rs` (`#[cfg(test)]`) replaying `fixtures/settlement-vectors.json`
- [ ] T015 [P] Write the failing schedule tests in `programs/holdout/src/math/schedule.rs` — `deadline_of(k) = start + (k+1) × day_length − 1`, day index `floor((now − 18,000) ÷ 86,400)`, join cutoff `start − 3,600` (data-model §1.3, §5)
- [ ] T016 [P] Build the LiteSVM harness with clock control in `tests/helpers/harness.ts` — chain-time travel, cohort builders, account decoding (research R2)
- [ ] T017 [P] Write the invariant checker in `tests/invariants/checker.ts` asserting INV-1…INV-8 after every step (spec §8.4)
- [ ] T017a [P] Write the failing no-oracle check — `tests/scenarios/no-oracle.spec.ts` asserting that no instruction takes a price, a fiat figure or an oracle account among its accounts or arguments, plus `scripts/check-no-oracle.sh` grepping `programs/holdout/src/` for `price`, `oracle`, `pyth`, `switchboard` and `usd` identifiers and failing CI on a hit; the only rate use in the repository stays `crank chips publish` (AC-9, constitution III)

### Program foundations

- [ ] T018 Implement the settlement maths in `programs/holdout/src/math/settlement.rs` until T014 passes — per-tier `share_t = floor(pool × chip_t ÷ T)`, remainder to the fee destination, `T = 0` → `fee_half = floor(pool ÷ 2)` with the odd unit to carry-over (research R10, FR-032a, FR-037)
- [ ] T019 Implement the schedule maths in `programs/holdout/src/math/schedule.rs` until T015 passes
- [ ] T020 [P] Define `Config` in `programs/holdout/src/state/config.rs` — admin, operator, sponsor, fee destination, SKR mint, `chip_set.amounts: [u64; 4]`, `faucet.sol_top_up = 20,000,000` lamports, `faucet.daily_sol_cap = 30`, `ladder_gate_enabled = false`, `genesis_gate_enabled = false` (data-model §1.1, research R13)
- [ ] T021 [P] Define `LeagueConfig` in `programs/holdout/src/state/league.rs` — `first_start` (Monday 05:00 UTC), `cadence = 7 days`, `duration_days`, `reps_per_day = 25`, `day_length`, `exercise: u8` (**`0` = push-ups; `1` negative and `2` decline are reserved and rejected by `create_league` with `ExerciseNotSupported`**), `next_index` (data-model §1.2, H30)
- [ ] T022 [P] Define `Cohort` in `programs/holdout/src/state/cohort.rs` — start, join cutoff, duration, reps, day length, `exercise`, the four locked chip amounts, status, `member_count`, `held_stake`, `pool`, `carry_over_in`, and the write-once settlement fields (data-model §1.3, FR-007)
- [ ] T023 [P] Define `Membership` in `programs/holdout/src/state/membership.rs` — `tier: u8` (0–3), `stake: u64` equal to `cohort.chips[tier]` at join and never changed, `days_completed: u8` never above the days reached, `state`, `counted: bool` set once, `bump` (data-model §1.4)
- [ ] T024 [P] Define `Profile` in `programs/holdout/src/state/profile.rs` — `qualified` set on any 7-day clear and never cleared, per-league `cleared`/`forfeited`/`current_streak`/`longest_streak`/`last_changed`, `running`, `pending`, `last_faucet_day` (−1 = never) (data-model §1.5)
- [ ] T025 Implement `guards.rs` in `programs/holdout/src/` — `in_breach(now)`, `standing(now)`, cohort status from chain time, slot normalisation moving `pending` into `running`, the two gate checks (data-model §1.4, §1.5)
- [ ] T026 [P] Declare every event in `programs/holdout/src/events.rs` and every error code 6000–6060 in `errors.rs`, exactly as `contracts/program-instructions.md` §4–§5 lists them
- [ ] T027 Implement `initialize` in `programs/holdout/src/instructions/initialize.rs` — creates `Config`, the mock SKR mint with authority PDA `["mint_authority"]`, the fee destination token account and the faucet vault PDA `["faucet_vault"]`; flags start `false`
- [ ] T028 [P] Implement `create_league` and `set_flags` in `programs/holdout/src/instructions/` — admin only, `create_test_league` behind the `test-leagues` feature (research R11, R14)

### Shared Kotlin and design foundations

- [ ] T029 Generate fixtures in `tests/fixtures/generate.ts` — accounts, PDAs, instruction bytes, derived figures — and commit them under `fixtures/` (contracts/program-instructions §7)
- [ ] T030 Implement account codecs, PDA derivation and instruction builders in `kotlin/core/src/main/kotlin/holdout/core/` (`Accounts.kt`, `Pdas.kt`, `Instructions.kt`), with hand-written encodings pinned by the fixtures (research R2 deviation)
- [ ] T031 Write the fixture replay tests in `kotlin/core/src/test/kotlin/holdout/core/FixtureReplayTest.kt` — every fixture decodes and re-encodes byte-identically
- [ ] T032 [P] Implement `Derive.kt` in `kotlin/core/src/main/kotlin/holdout/core/` — survivor counter, projected pool, projected share, roster order, time left, warning window, **at stake**, carry-over received, time until joining closes (data-model §5)
- [ ] T033 [P] Implement chain-time tracking in `kotlin/core/src/main/kotlin/holdout/core/Clock.kt` — cached `{chainNow, slot, readAtElapsedRealtime}`, advanced with `SystemClock.elapsedRealtime()`, never the wall clock (research R7)
- [ ] T034 [P] Implement RPC reads in `kotlin/core/src/main/kotlin/holdout/core/Rpc.kt` — `getProgramAccounts` with a `memcmp` on the cohort key and a `dataSlice`, 30-second refresh while a cohort screen is visible (research R8)
- [ ] T035 [P] Transcribe the design tokens into `kotlin/app/src/main/kotlin/holdout/app/ui/theme/` — `Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`: one dark `ColorScheme`, **dynamic colour disabled**, JetBrains Mono in `kotlin/app/src/main/res/font/` (PRD §11, research R20)
- [ ] T036 [P] Scaffold the crank in `kotlin/crank/src/main/kotlin/holdout/crank/Main.kt` — argument parsing, RPC config, `status` subcommand, single runnable JAR (contracts/crank-cli.md)

**Checkpoint (21 Sep)**: math and schedule tests pass, accounts compile, `kotlin/core` replays every fixture, the theme renders, and the no-oracle check passes.

---

## Phase 3: US1 — First run (Priority: P1)

**Goal**: someone installs the app, connects a wallet, is taught what this is, and can fund themselves.

**Independent test**: a fresh install on a phone with no wallet shows the connect screen and the walkthrough only; after connecting, the tour appears, the faucet funds the wallet, and the ladder lists both Holdout lengths.

- [ ] T037 [US1] Write the `claim_faucet` scenario in `tests/scenarios/faucet.spec.ts` — sponsor-paid claim for a zero-SOL wallet, one claim per recipient per day, the 30-claim daily cap, vault exhaustion returning a specific error (FR-042a, research R13)
- [ ] T038 [US1] Implement `claim_faucet` in `programs/holdout/src/instructions/claim_faucet.rs` — mints the `$100`-tier amount, tops the recipient up to `faucet.sol_top_up`, reimburses the sponsor's fee and rent, sets `last_faucet_day`, needs **no member signature** (contracts/program-instructions §3)
- [ ] T039 [P] [US1] Build the connect screen in `kotlin/app/src/main/kotlin/holdout/app/wallet/ConnectScreen.kt` — the two-line promise, one line each for the Daily 25 and a Holdout, live Holdouts with still-standing and prize pool (running) and joined count with **at stake** (open), `Connect wallet`, and the quiet judges link (FR-001a, FR-053)
- [ ] T040 [P] [US1] Implement MWA connection in `kotlin/app/src/main/kotlin/holdout/app/wallet/WalletRepository.kt` — `MobileWalletAdapter` with `ConnectionIdentity` on our domain, `blockchain = Solana.Devnet`, `connect`/`disconnect`, auth-token reuse on resume (contracts/android-app §3)
- [ ] T041 [US1] Handle `TransactionResult.NoWalletFound` with the no-wallet screen — what a wallet is, how to get one, and the walkthrough, which plays without one (ES-0, FR-001a)
- [ ] T042 [P] [US1] Build the three-card tour in `kotlin/app/src/main/kotlin/holdout/app/tour/TourScreen.kt` — the Daily 25, what a Holdout is, how reps are counted; skippable at any card, re-openable from About, `tour:{wallet}` written to MMKV (FR-067, research R20)
- [ ] T043 [P] [US1] Build the faucet sheet in `kotlin/app/src/main/kotlin/holdout/app/faucet/FaucetSheet.kt` — one tap, used-today variant with the time it returns, and the can't-pay-out variant that never blames the member (ES-0a, ES-0b)
- [ ] T044 [P] [US1] Build the ladder in `kotlin/app/src/main/kotlin/holdout/app/ladder/LadderScreen.kt` — the Daily 25, both Holdout lengths open, the 21-day requirement stated but not enforced, Human Flag locked (US-1, FR-003)
- [ ] T045 [P] [US1] Implement MMKV storage in `kotlin/app/src/main/kotlin/holdout/app/storage/Store.kt` — keys scoped per wallet, cohort (or `challenge`) and day index, with synchronous writes before anything shows as banked. Because nothing syncs, a wiped or replaced phone simply starts that day at zero and the member may finish it there before the deadline; a lost day is never recoverable (data-model §4, research R6, FR-024a, ES-3a)

**Checkpoint**: a brand-new wallet reaches a funded ladder without leaving the app.

---

## Phase 4: US5A — The Daily 25 (Priority: P1)

**Goal**: anyone with a wallet can do 25 verified push-ups today, free, and keep a streak.

**Independent test**: with no Holdout joined, the home screen leads with the Daily 25; 10 + 10 + 5 across three sittings completes it and the streak goes up by one; nothing touches the chain.

- [ ] T046 [P] [US5A] Implement the pre-flight checks in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Preflight.kt` — four visual checks, each failure saying what to fix, **no audio** (FR-019)
- [ ] T047 [P] [US5A] Implement palm liveness in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/Liveness.kt` — MediaPipe Hand Landmarker, open palm held 3 s, 30-second timeout back to pre-flight, unlimited retries, banked reps untouched (FR-020, research R5)
- [ ] T048 [US5A] Implement the push-up state machine in `kotlin/repcounter/src/main/kotlin/holdout/repcounter/RepCounter.kt`. **The rule** (FR-021, PRD §8): a rep counts only when one cycle shows top — elbow > 160°, shoulder–hip–ankle within ±10° of straight; bottom — elbow < 90°, body line held; back to top; cycle time 0.8–6 s. **The implementation** (research R4): angles from 3D world landmarks, one-euro smoothing, and hysteresis triggers at **≥ 165° top / ≤ 85° bottom** so the model's ~2° error cannot double-count; frames below the landmark presence threshold are dropped, not scored. A rep missing the rule is discarded silently with a hint — "Go lower", "Keep your body straight", "Slow down" — never an error. **Spike S2 sets the final trigger values from its measured angle table.**
- [ ] T049 [US5A] Write the recorded-trace tests in `kotlin/repcounter/src/test/kotlin/holdout/repcounter/TraceTest.kt` — 10 honest videos counted within 1 rep of 25, 3 cheat videos rejected, palm timeout at 30 s. **Assert the rule, never the trigger values**, so tuning the hysteresis after S2 does not rewrite the tests
- [ ] T050 [US5A] Build the sitting screens in `kotlin/app/src/main/kotlin/holdout/app/sitting/` — start card, pre-flight, liveness, countdown, counting with the skeleton overlay and bank ring, stopped part-way, complete (PRD §10.1 screen 8)
- [ ] T051 [US5A] Bank verified reps in blocks of 10 in `kotlin/app/src/main/kotlin/holdout/app/sitting/BankingController.kt` — written to MMKV before the UI shows them, carried across sittings for the day, reps short of the next block discarded when a sitting ends (FR-024, FR-023)
- [ ] T052 [P] [US5A] Implement the streak in `kotlin/app/src/main/kotlin/holdout/app/streak/StreakStore.kt` — `+1` on a completed day **from either source: a Daily 25 or a cohort day counts as that day's training, so nobody trains twice** (FR-050); broken by a day with none; untouched by a forfeit; turning over at 05:00 UTC by chain time. Its tests assert both the shared-day rule and that a completed day stops counting once the boundary passes, so the app never shows yesterday's done state (FR-049…FR-052, ES-9)
- [ ] T053 [P] [US5A] Build the home screen's Daily 25 variant in `kotlin/app/src/main/kotlin/holdout/app/home/HomeScreen.kt` — the hero 25 with Start and the countdown, the streak chip, and the Holdouts open to join below it with **at stake**, joined count, carry-over and the closing countdown (FR-053)

**Checkpoint**: the free habit loop works end to end on a device, with no chain writes. **This plus Phase 3 is the MVP.**

---

## Phase 5: US6 — Staking into a Holdout, with US2 the lobby it waits in (Priority: P1)

**Goal**: a member picks a chip, stakes, sits in the lobby, and can leave until joining closes.

**Independent test**: a join and a leave landing one second before the cutoff both succeed with a full refund on the leave; both are rejected one second after it; the cohort then shows as locked until it starts.

- [ ] T054 [US6] Write `tests/scenarios/join-leave.spec.ts` — join at each tier, refusal of any amount that is not one of the cohort's four, leave with a full refund and no deduction, one lobby place at a time (AC-13, FR-055, FR-009)
- [ ] T054a [P] [US2] Write `tests/scenarios/lobby.spec.ts` — the cutoff decided by chain time alone: a join and a leave one second before it both succeed, both are rejected one second after it; `Locked` holds from the cutoff to the start with a final roster; a lobby place is refused in a cohort starting before the member's running one ends (ES-12, ES-12a, ES-13, ES-14, FR-006a, FR-006b, FR-010)
- [ ] T055 [US6] Implement `open_cohort` in `programs/holdout/src/instructions/open_cohort.rs` — permissionless, succeeds only for `i == next_index` and after cohort `i − 1` has started, copies the current chip set and every parameter including `exercise` (research R11, FR-007, FR-007a)
- [ ] T056 [US6] Implement `join_cohort` in `programs/holdout/src/instructions/join_cohort.rs` — status `Open`, `tier ≤ 3`, gates, slot normalisation, `Membership` created with the member paying rent, stake moved to the vault, `held_stake` and `member_count` raised (contracts/program-instructions §3)
- [ ] T057 [US6] Implement `leave_cohort` in `programs/holdout/src/instructions/leave_cohort.rs` — status `Open` only, full refund with no deduction, membership closed and its rent returned, `profile.pending` cleared (FR-009)
- [ ] T058 [P] [US6] Implement `publish_chip_set` in `programs/holdout/src/instructions/publish_chip_set.rs` — operator only, takes `amounts: [u64; 4]` and no price (constitution III, FR-055a)
- [ ] T059 [P] [US6] Build the stake-confirm sheet in `kotlin/app/src/main/kotlin/holdout/app/stake/StakeSheet.kt` — four tier chips with their exact SKR amounts and `≈ $` labels, daily obligation, time to the cutoff and the start, the pool including carry-over, and the sentence "if nobody drops out you get exactly your stake back" (US-6, FR-041, FR-059)
- [ ] T060 [P] [US2] Build the lobby in `kotlin/app/src/main/kotlin/holdout/app/lobby/LobbyScreen.kt` — countdowns to cutoff and start, live roster count, held stake, unconditional leave, and the locked variant with the final roster and no actions (US-2, ES-12a)
- [ ] T061 [US6] Send transactions through MWA in `kotlin/app/src/main/kotlin/holdout/app/wallet/Signer.kt` — `transact { signAndSendTransactions(...) }`, the member as fee payer, retryable errors surfaced plainly (contracts/android-app §3)
- [ ] T062 [P] [US6] Implement `crank chips publish --rate <usd-per-skr>` in `kotlin/crank/src/main/kotlin/holdout/crank/Chips.kt` — converts the four tiers, rounds down to the nearest 10 SKR, minimum 10 SKR (research R15)

**Checkpoint**: a member can stake into a Holdout and get out again before the cutoff.

---

## Phase 6: US3 — A day's reps inside a Holdout (Priority: P1)

**Goal**: a cohort day is completed on the device and lands on-chain before its deadline.

**Independent test**: 10 + 10 + 5 across three sittings records the day on-chain only after the last rep; a sitting stopped at 17 keeps 10; killing the app keeps every banked block; airplane mode completes the day and one tap submits it when the network returns.

- [ ] T063 [US3] Write `tests/scenarios/submit-day.spec.ts` — one completed day per member per day, `days_completed` never past the days reached (INV-3), rejection once in breach (INV-8), rejection after the cohort's final deadline
- [ ] T064 [US3] Implement `submit_day` in `programs/holdout/src/instructions/submit_day.rs` — member-signed, `day_index` and a 32-byte `proof_hash` emitted in `DayCompleted` and not stored (research R16, FR-025)
- [ ] T065 [US3] Wire the cohort day into the sitting flow in `kotlin/app/src/main/kotlin/holdout/app/sitting/CohortDayController.kt` — target and exercise read from `Cohort.reps_per_day` and `Cohort.exercise`, never constants; an unknown exercise value refuses the sitting rather than guessing (FR-014, H30)
- [ ] T066 [P] [US3] Implement the offline path in `kotlin/app/src/main/kotlin/holdout/app/sitting/PendingSubmission.kt` — the warning before an offline cohort sitting and again on completion, the pending record in MMKV, and "Done — tap to submit" once online. **Phase 1 half only**; T098 replaces the tap with automatic submission if spec Phase 2 lands (FR-024b, ES-3b)
- [ ] T067 [P] [US3] Show reps still needed and time left before every sitting, with the clear warning inside the last 10 minutes and no refusal to start (FR-047)

**Checkpoint**: a real day in a real Holdout can be completed and submitted from a phone.

---

## Phase 7: US4 — Missing a day (Priority: P1)

**Goal**: a missed deadline forfeits the stake, funds the pool, and tells the member what happened.

**Independent test**: with the crank stopped, a member who misses a day cannot submit, and when the crank runs the pool rises by the stake minus exactly 20%.

- [ ] T068 [US4] Write `tests/scenarios/forfeit.spec.ts` — breach derived from chain time before any automation, `record_forfeits` idempotent, pages in any order and repeated, fee exactly `floor(stake × 20 ÷ 100)` (AC-2, AC-5, INV-7)
- [ ] T069 [US4] Implement `record_forfeits` in `programs/holdout/src/instructions/record_forfeits.rs` — up to 10 `(Membership, Profile)` pairs, skips pairs not in breach or already forfeited, moves the fee, raises `pool`, lowers `held_stake`, resets the league streak (research R9, FR-027, FR-028)
- [ ] T070 [P] [US4] Build the forfeit screen in `kotlin/app/src/main/kotlin/holdout/app/cohort/ForfeitScreen.kt` — what happened, the streak explicitly still alive, and the two ways back: today's Daily 25 and the next 7-day Holdout (US-4, FR-052)
- [ ] T071 [US4] Implement `crank run` in `kotlin/crank/src/main/kotlin/holdout/crank/Run.kt` — opens due cohorts, pages forfeits for breached members, settles finished cohorts; idempotent, with `--dry-run` (contracts/crank-cli.md)

**Checkpoint**: money moves on a missed day, and nobody has to be online for it to be true.

---

## Phase 8: US7 — Watching the crowd thin (Priority: P1)

**Goal**: the survivor counter and the prize are the screen, and they move at the deadline itself.

**Independent test**: with the crank stopped, one second after a deadline every device already excludes the members who missed it; recording their forfeits later changes no displayed figure.

- [ ] T072 [US7] Write `tests/scenarios/derived-figures.spec.ts` — figures computed at the deadline match the figures after recording, for a 1,000-member cohort (FR-038a, AC-8)
- [ ] T073 [US7] Build the cohort home variant in `kotlin/app/src/main/kotlin/holdout/app/home/CohortHome.kt` — survivor counter and prize pool as the largest things, projected share, days remaining, the member's own record, and the counter rolling rather than cutting (FR-038, PRD §10.1a, §11.5)
- [ ] T074 [P] [US7] Build the roster in `kotlin/app/src/main/kotlin/holdout/app/roster/RosterScreen.kt` — still standing first, then forfeited by days lasted, scripted members labelled from the bundled list (FR-062, FR-064)
- [ ] T075 [P] [US7] Build cohort detail in `kotlin/app/src/main/kotlin/holdout/app/cohort/CohortDetailScreen.kt` — days remaining, own completion record, projected share, survivor history (US-7)
- [ ] T076 [US7] Run the randomised invariant suite at 1,000 members in `tests/invariants/randomised.spec.ts` — joins, sittings, misses, claims, pages out of order and repeated, INV-1…INV-8 after every step. **This is spike S3** (research S3): run it as soon as the instruction set first compiles, not at the end of the phase; if it fails, cut the page size or add address lookup tables before building further (AC-7)

**Checkpoint**: the number that sells the product is live and provably backed by chain state.

---

## Phase 9: US8 — Settlement and the claim (Priority: P1)

**Goal**: every member settles on the same figures at the same moment, and a finisher claims.

**Independent test**: two finishers on equal stakes receive byte-identical payouts; the rounding remainder reaches the fee destination; the sum of claims equals total finisher stake plus pool minus that remainder, to the unit.

- [ ] T077 [US8] Write `tests/scenarios/settlement.spec.ts` — `settle_count` paged in any order and repeated, `settle_finalize` once only (INV-5), remainder to the fee destination, no-finisher 50/50 with the odd unit to carry-over, a cohort that received carry-over and also has no finishers (AC-3, AC-11, ES-7…ES-7b)
- [ ] T078 [US8] Implement `settle_count` in `programs/holdout/src/instructions/settle_count.rs` — forfeits any unrecorded breach in the page first, then counts finishers per tier into `finishers_per_tier` and `total_finisher_stake`, sets `counted` once (research R9, R10)
- [ ] T079 [US8] Implement `settle_finalize` in `programs/holdout/src/instructions/settle_finalize.rs` — constant-time per-tier shares, remainder recorded and sent, carry-over written to the league's open cohort or held on this one (FR-032a, FR-037, FR-037a)
- [ ] T080 [US8] Implement `claim_payout` in `programs/holdout/src/instructions/claim_payout.rs` — pays `stake + share_tier` read from the finalised cohort, deliberate member action, claimable indefinitely (FR-034, FR-035)
- [ ] T081 [P] [US8] Build the claim screen in `kotlin/app/src/main/kotlin/holdout/app/claim/ClaimScreen.kt` — the amount, the breakdown of stake plus share, the single gradient sweep on success, the settled transaction link, and the claimed state (PRD §10.1 screen 10, §11.5)
- [ ] T082 [P] [US8] Show the settling state in cohort detail — "you finished · settlement in progress" until finalisation (contracts/android-app §5)

**Checkpoint (by 21 Sep, contract side)**: the full money loop passes on LiteSVM, including a 1,000-member cohort.

---

## Phase 10: US5 — Qualifying (Priority: P2)

**Goal**: clearing a 7-day Holdout records the 21-day unlock permanently, even though the gate is off.

**Independent test**: a member who clears a 7-day cohort has `qualified = true`; with the flag flipped on, a wallet without it is refused a 21-day join.

- [ ] T083 [US5] Write `tests/scenarios/qualify.spec.ts` — unlock recorded on every 7-day clear with the gate off, refusal with the gate on, unlock never revoked by a later forfeit (AC-1, FR-003a, FR-004)
- [ ] T084 [US5] Set `qualified` inside `settle_count` when the cleared cohort belongs to league 0, and enforce `ladder_gate_enabled` in `join_cohort` (FR-003, FR-003a)
- [ ] T085 [P] [US5] Show the unlock in the ladder and after a clear — stated plainly, with the note that both lengths are open in this build (US-5)

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
- [ ] T091 [US9] Implement `crank init` in `kotlin/crank/src/main/kotlin/holdout/crank/Init.kt` — `initialize`, both leagues with `first_start` Monday 28 September 05:00 UTC, the mock mint, the funded faucet vault (quickstart §6)
- [ ] T092 [US9] Deploy the rehearsal program with a `test-leagues` build and run a compressed full cycle on devnet (plan 21–22 Sep)
- [ ] T093 [US9] Run the `NO_DNA=1 anchor test` Surfpool smoke suite in `tests/smoke/` — one happy path per instruction plus a time-travelled settlement — then deploy the judged program keeping its upgrade authority (FR-007b, quickstart §6)
- [ ] T094 [US9] Schedule `crank run && crank scripted run` every 10 minutes as a Fly.io machine via `fly.toml`, with `.github/workflows/crank.yml` as the disabled-by-default fallback (research R12)
- [ ] T095 [US9] Run the beta test on the rehearsal deployment, 24–26 Sep: seven 10-minute days at 5 reps, each tester completing a full cycle, findings triaged the same day (`docs/holdout_demo_video.md` §9)

**Checkpoint (28 Sep)**: cohort #3 starts with real testers and scripted members in it. **Feature freeze.**

---

## Phase 12: Spec Phase 2 — deferred items (Priority: P3)

**Built only after every Phase 1 check is green, and only if it lands before 28 September** (spec §3a). Four separate items live here: league leaderboards (US10), automatic offline submission (US3), carry-over delivery (US8) and weekly chip re-pricing (US6). Cut them independently, smallest first, and log every cut in `docs/holdout_decisions.md`.

- [ ] T096 [P] [US10] Write `tests/scenarios/leaderboard.spec.ts` — ranking by survival rate, the 3-cohort minimum, the tie-breaks, "not ranked yet" below the line, a running or locked cohort counted in neither figure (AC-14)
- [ ] T097 [US10] Build the leaderboard screen in `kotlin/app/src/main/kotlin/holdout/app/leaderboard/LeaderboardScreen.kt` — survival rate, cohorts played and cleared, current and longest league streak, shortened addresses, no amounts anywhere (FR-060…FR-063)
- [ ] T098 [P] [US3] Implement automatic offline submission — the pending day written by T066 sent as soon as a connection returns, with "Done — waiting for network" until it lands, replacing the Phase 1 tap (FR-024b Phase 2, ES-3b)
- [ ] T099 [P] [US8] Implement `deliver_carry_over` in `programs/holdout/src/instructions/deliver_carry_over.rs` — moves a held carry-over exactly once into the next cohort of that league when it opens (FR-037a, INV-6)
- [ ] T100 [P] [US6] Implement `crank chips publish --auto --if-drift 0.20` — weekly Monday check against a public rate, exits without publishing when no rate is available (FR-056, FR-057)

---

## Phase 13: Polish and cross-cutting

- [ ] T101 [P] Add the manifest check `:app:checkNoAudioPermission` to `kotlin/app/build.gradle.kts` — the build fails if `RECORD_AUDIO` ever appears (FR-022a)
- [ ] T102 [P] Capture outbound traffic during a sitting and confirm only RPC calls leave the device (AC-10, constitution X)
- [ ] T103 [P] Write the known weaknesses with their real fixes into `kotlin/app/src/main/assets/weaknesses.json` and render the README table from it — the bundled faucet sponsor key and its caps, the retained upgrade authority, client-signed days, device-local banked reps, no audio anti-spoofing, the off-chain rate, scripted members, devnet. T090 shows the same file in the app (constitution XI)
- [ ] T103a [P] Check the app's own wording before the freeze — grep `kotlin/app/src/main/res/values/strings.xml` and the Compose sources for "winnings", "returns", "yield", "earnings", "bet", "wager", "gamble", "odds" and "luck", confirm every fiat figure carries "≈", and confirm the test-network notice is present (FR-039, FR-040, FR-042, constitution XIII)
- [ ] T104 [P] Write the README's clone-and-run instructions and verify them on a machine that has never built the project (FR-066, AC-19)
- [ ] T105 [P] Draw the app icon, wordmark and splash from the design tokens (PRD §11.8)
- [ ] T106 Film the cohort #3 shot list, 27 Sep – 5 Oct — join and lobby, daily sittings, the forfeiter's forfeit, the daily 05:05 UTC captures, settlement and the real claim, each row logged with its transaction signature (`docs/holdout_demo_video.md` §2, §5)
- [ ] T107 Cut the two exports, 5–7 Oct — the 16:9 three-minute demo video and the 9:16 in-app walkthrough under 40 MB — plus the short explainer for the tour's card 2 (research R18, FR-067)
- [ ] T108 Build the final APK with the walkthrough bundled, install it on a Seeker and a plain Android phone, and check every §3 screen appears in the video (AC-19)
- [ ] T109 Write the pitch deck, disclosing scripted members and the test network, and submit the APK, repository, video and deck on 8 Oct. Before submitting, log every cut Phase 2 item in `docs/holdout_decisions.md` and confirm the README's weakness table matches what the app actually ships (FR-066, spec §3a, constitution XI)
- [ ] T110 Monitor the live period 9 Oct – 11 Nov — the faucet vault, the crank, the scripted floors — fixing only contract defects, by upgrade (FR-007b, FR-043)

---

## Dependencies

**Phase order**: Setup → Foundational → US1 → US5A → US6/US2 → US3 → US4 → US7 → US8 → US5 → US9 → US10 → Polish.

**Hard blockers**:

- Everything waits on Phase 2. The fixtures (T029) pin the Kotlin client, so T030 cannot start before them.
- `open_cohort` (T055) precedes every cohort-scoped story.
- `record_forfeits` (T069) precedes settlement (T078), because `settle_count` forfeits stragglers first.
- The faucet (T038) precedes any device test that stakes, including the beta test (T095).
- The judged deploy (T093) must land before cohort #3's join cutoff, **28 Sep 04:00 UTC**, since the walkthrough and demo video can only come from that cohort.
- T106 cannot start before T093 and the release candidate; T107 cannot start before T106.

**Story independence**: US5A (the Daily 25) touches no chain state and can ship alone. US1 needs only the faucet instruction. **US2 is not separately deliverable** — a lobby needs the join it holds, so it ships inside Phase 5 with its own contract assertions (T054a) and screen (T060). Everything from US6 onward shares the cohort lifecycle, so they land in order.

## Parallel execution examples

- **Phase 1**: T004–T012b in parallel — different toolchains, no shared files. The two spikes (T012a, T012b) need hardware, so start them first; everything else can proceed while they run.
- **Phase 2**: T020–T024 (one account file each), then T032–T036 in parallel.
- **Phase 3**: T039, T042, T043, T044, T045 — separate screens and stores.
- **Phase 4**: T046, T047 in parallel; T052, T053 in parallel once banking exists.
- **Phase 13**: T101–T105 in parallel while filming runs.

## Implementation strategy

1. **MVP** = Phases 1–4: install, connect, tour, faucet, and the Daily 25 counted on the device. It demonstrates the camera, which is the product's magic, without any money.
2. **The money loop** = Phases 5–9, in order. Nothing here is optional: the spec's acceptance criteria 1–11 and 13 all live in it.
3. **Demo integrity** = Phase 11, which must be live before the freeze because judging depends on it.
4. **Phase 2 of the spec** = Phase 12 here, cut without regret if 26–27 September is tight — each item has a Phase 1 stand-in, and each cut is logged in the decision record.
5. **Everything after the freeze** is Phase 13: filming, writing and the submission.
