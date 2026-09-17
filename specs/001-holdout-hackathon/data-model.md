# Data model — 001-holdout-hackathon

**Phase 1 output of `/speckit-plan`.** Behaviour is fixed by `spec.md` §6–§8; state machines and edge cases by `docs/holdout_scope_and_state_machines.md` §3–§4. This file fixes **what is stored where**. Instruction signatures are in `contracts/program-instructions.md`.

Units: SKR amounts are **base units** (`u64`); times are **Unix seconds of chain time** (`i64`); lamports for SOL.

---

## 1. On-chain accounts (program `holdout`)

### 1.1 `Config` — singleton · PDA `["config"]`

| Field | Type | Rule |
|---|---|---|
| `admin` | Pubkey | May only run `initialize`, `set_flags`, `create_test_league` (R14) |
| `operator` | Pubkey | May only run `publish_chip_set` |
| `sponsor` | Pubkey | The only fee payer accepted by `claim_faucet` (R13) |
| `skr_mint` | Pubkey | Mock SKR mint; its mint authority is PDA `["mint_authority"]` |
| `fee_destination` | Pubkey | SKR token account of the treasury wallet (R14) |
| `ladder_gate_enabled` | bool | `false` on devnet (FR-003) |
| `genesis_gate_enabled` | bool | `false` on devnet (R17) |
| `chip_set.amounts` | [u64; 4] | $10, $25, $50, $100 tiers in SKR; strictly increasing, all > 0 |
| `chip_set.version` | u32 | +1 on every publish |
| `chip_set.published_at` | i64 | Chain time of publish |
| `faucet.sol_top_up` | u64 | Lamports a claim tops a recipient up to (20,000,000 = 0.02 SOL) |
| `faucet.daily_sol_cap` | u16 | 30 SOL-paying claims per day (R13) |
| `faucet.sol_claims_day` | i64 | Day index the counter belongs to |
| `faucet.sol_claims_today` | u16 | Resets when `sol_claims_day` changes |
| `bump` | u8 | |

### 1.2 `LeagueConfig` — one per league · PDA `["league", league_id: u8]`

| Field | Type | Rule |
|---|---|---|
| `league_id` | u8 | 0 = 7-day, 1 = 21-day; test leagues ≥ 100 (admin, `test-leagues` builds only: LiteSVM and the rehearsal deployment) |
| `duration_days` | u8 | 7 or 21 |
| `reps_per_day` | u16 | 25 |
| `day_length` | i64 | 86,400 on devnet; smaller only for test leagues (scope doc D-A) |
| `first_start` | i64 | A Monday 05:00:00 UTC |
| `cadence` | i64 | 604,800 (7 days) |
| `join_lock` | i64 | 3,600 (join cutoff = start − 1 h, FR-008) |
| `next_index` | u32 | Index the next `open_cohort` must use |
| `bump` | u8 | |

### 1.3 `Cohort` · PDA `["cohort", league_id: u8, index: u32 LE]`

**Fixed at creation (FR-007, constitution V):**

| Field | Type | Value at `open_cohort` |
|---|---|---|
| `league_id`, `index` | u8, u32 | From seeds |
| `start` | i64 | `first_start + index × cadence` |
| `join_cutoff` | i64 | `start − join_lock` |
| `duration_days`, `reps_per_day`, `day_length` | u8, u16, i64 | Copied from `LeagueConfig` |
| `chips` | [u64; 4] | Copied from `Config.chip_set.amounts` (FR-055) |
| `vault_bump`, `bump` | u8 | Vault = token account PDA `["vault", cohort]`, authority = cohort PDA |

**Running totals:**

| Field | Type | Changed by |
|---|---|---|
| `member_count` | u32 | +1 `join_cohort`, −1 `leave_cohort` |
| `held_stake` | u64 | + stake on join; − stake on leave, forfeit, claim |
| `pool` | u64 | + (stake − fee) on each recorded forfeit; + carry-over received; − shares paid on claim |
| `carry_over_received` | u64 | + on carry-over arrival (FR-037b) |
| `forfeits_recorded` | u32 | +1 per recorded forfeit |
| `counted` | u32 | +1 per membership processed by `settle_count` |
| `finisher_count` | u32 | +1 per finisher counted |
| `finishers_per_tier` | [u32; 4] | Per-tier finisher counts (R10) |
| `total_finisher_stake` | u64 | Σ finisher stakes — the payout denominator (FR-033) |

**Written once by `settle_finalize` (INV-5, INV-6):**

| Field | Type | Meaning |
|---|---|---|
| `settled` | bool | `Settling → Settled` |
| `share_per_tier` | [u64; 4] | `floor(pool × chips[t] ÷ total_finisher_stake)`; all zero when no finishers |
| `rounding_remainder` | u64 | Sent to fee destination (FR-032a) |
| `no_finisher_fee` | u64 | `floor(pool ÷ 2)` when no finishers (FR-037) |
| `carry_over_sent` | u64 | `pool − no_finisher_fee`, delivered at finalize |
| `carry_over_held` | u64 | The same amount if no cohort of the league was open; 0 once delivered **[Phase 2]** |
| `carry_over_target` | Pubkey | Receiving cohort, or default while held |

**Derived, never stored** (`kotlin/core` `Derive.kt`, mirrored in program guards and pinned by `fixtures/`):

| Value | Definition |
|---|---|
| `end` | `start + duration_days × day_length` |
| `status(now)` | `Open` if `now < join_cutoff` · `Locked` if `< start` · `Running` if `< end` · `Settling` if `!settled` · `Settled` |
| `day_index(now)` | `floor((now − start) ÷ day_length)` while `Running` |
| `deadline_of(k)` | `start + (k + 1) × day_length − 1` — the **last second** of day `k` |

The day boundary therefore falls exactly on 05:00:00 UTC: day `k` accepts completions at chain times `start + k·L … start + (k+1)·L − 1`, and a member is in breach from the next second (constitution II: `now > deadline_of(days_completed)`).

### 1.4 `Membership` · PDA `["member", cohort, wallet]`

| Field | Type | Rule |
|---|---|---|
| `cohort`, `wallet` | Pubkey | Seeds |
| `tier` | u8 | 0–3 |
| `stake` | u64 | `== cohort.chips[tier]` at join; never changes (FR-058) |
| `joined_at` | i64 | Chain time of join |
| `days_completed` | u8 | +1 per accepted `submit_day`; ≤ reached days (INV-3) |
| `state` | enum | `Joined` → `Forfeited` \| `Finished` → `Claimed` |
| `counted` | bool | Set once by `settle_count` (order-independent paging, R9) |
| `bump` | u8 | |

`Withdrawn` is represented by **closing** the account on `leave_cohort` (rent returned). `Pending` and `Active` are **derived** from the cohort's status while `state == Joined` (spec §8.2).

| Derived value | Definition |
|---|---|
| `in_breach(now)` | `state == Joined` ∧ `now ≥ cohort.start` ∧ `days_completed < duration_days` ∧ `now > deadline_of(days_completed)` |
| `standing(now)` | `state ∈ {Finished, Claimed}` ∨ (`state == Joined` ∧ `!in_breach(now)`) |
| `days_lasted` | `days_completed` — roster order for forfeited members (FR-062) |

### 1.5 `Profile` — one per wallet · PDA `["profile", wallet]`

Created by the first `claim_faucet` or `join_cohort`, whichever comes first.

| Field | Type | Rule |
|---|---|---|
| `wallet` | Pubkey | Seed |
| `qualified` | bool | Set on any 7-day clear; never cleared (FR-003a, FR-004) |
| `leagues[l].cleared` | u16 | +1 when counted as finisher |
| `leagues[l].forfeited` | u16 | +1 when a forfeit is recorded |
| `leagues[l].current_streak` | u16 | +1 on clear, 0 on forfeit (FR-061) |
| `leagues[l].longest_streak` | u16 | `max(longest, current)` |
| `leagues[l].last_changed` | i64 | Leaderboard tie-break (FR-060b) |
| `running` | {cohort: Pubkey, end: i64} | Membership in a started cohort; default = none |
| `pending` | {cohort: Pubkey, start: i64} | Lobby place; default = none |
| `last_faucet_day` | i64 | Day index of last faucet claim; −1 = never |
| `bump` | u8 | |

**Slot normalisation** (run at the start of `join_cohort`, `leave_cohort`, `submit_day`, and when forfeits and counts touch the profile): if `pending.start ≤ now`, move `pending` into `running`. Recording a forfeit or counting a finisher clears `running` when it matches that cohort.

Per-league progress is recorded from Phase 1; league leaderboards reading it are Phase 2 (spec §3a).

### 1.6 Program-owned helpers

| Account | Seeds | Purpose |
|---|---|---|
| Faucet vault | `["faucet_vault"]` | Program-owned lamport store for SOL top-ups and sponsor reimbursement (R13) |
| Mint authority | `["mint_authority"]` | Signs `mint_to` for faucet SKR |
| Cohort vault | `["vault", cohort]` | SKR token account holding held stakes + pool (+ held carry-over) |

---

## 2. Validation rules (enforced on-chain)

| Rule | Source | Where |
|---|---|---|
| Join only while `status == Open` | FR-008, FR-010 | `join_cohort` |
| `stake == chips[tier]` for `tier ∈ 0..=3` | FR-055 | `join_cohort` |
| At most one `pending`; new cohort `start ≥ running.end` | FR-006a/b, INV-4 | `join_cohort` |
| 21-day join requires `qualified` **only if** `ladder_gate_enabled` | FR-003 | `join_cohort` |
| Genesis check **only if** `genesis_gate_enabled` | R17 | `join_cohort` |
| Leave only while `Open`; full stake refund | FR-009 | `leave_cohort` |
| Submit only while `Running`, `day_index == days_completed`, `now ≤ deadline_of(days_completed)` | FR-013, FR-017, FR-018, FR-025, INV-8 | `submit_day` |
| Forfeit only if `in_breach(now)`; fee `floor(stake × 20 ÷ 100)` | FR-026, FR-027, INV-2 | `record_forfeits` |
| Count only when `status == Settling`; any member of the page still in unrecorded breach is forfeited first by the same rule as `record_forfeits`, then counted | spec §8.1, FR-030 | `settle_count` |
| Finalize only when `counted == member_count` | R10, INV-5 | `settle_finalize` |
| Claim only when `settled`, `state == Finished` | FR-034, INV-6 | `claim_payout` |
| Open only `index == next_index`, previous cohort started | R11, FR-007a | `open_cohort` |
| Carry-over only into a cohort of the same league with `status == Open` | FR-037a | `settle_finalize`, `deliver_carry_over` **[Phase 2]** |
| Chip set: 4 increasing positive amounts; operator only | FR-054, R14 | `publish_chip_set` |
| Faucet: payer == `sponsor`; once per recipient per day; SOL cap per day | FR-042a, R13 | `claim_faucet` |

## 3. Invariants → where they are checked

| Invariant | Mechanism | Test |
|---|---|---|
| **INV-1** custody | `vault.amount == held_stake + pool + carry_over_held` after every instruction touching a cohort | Assert in every integration test step; property test |
| **INV-2** pool sources | Only `record_forfeits`, carry-over arrival and claims change `pool` | Property test over random instruction sequences |
| **INV-3** days | `submit_day` requires `day_index == days_completed` | Unit + property test |
| **INV-4** one running, one pending, no overlap | Profile slot rules | Scenario tests incl. 21-day refusal (AC-13) |
| **INV-5** denominator once | `settle_finalize` requires `!settled` | Double-finalize test |
| **INV-6** settled is final | Only `claim_payout` and **[Phase 2]** `deliver_carry_over` accept a settled cohort | Instruction-matrix test |
| **INV-7** idempotent automation | Per-membership state and `counted` flags | Pages out of order and repeated (AC-7) |
| **INV-8** breach blocks submit | `submit_day` deadline guard, no dependency on forfeits | V16 |

---

## 4. Device-local records (MMKV for Android, never on-chain)

| Key | Value | Rules |
|---|---|---|
| `clock` | `{chainNow, slot, readAtElapsedRealtime}` | Refreshed on start, resume, each confirmed transaction; advanced with `SystemClock.elapsedRealtime()` (R7) |
| `progress:{wallet}:{scope}:{dayIndex}` | `{banked, target, completedAt?}` | `scope` = cohort address or `challenge`; `banked` is a multiple of 10; **written before "banked" is shown** (FR-023, R6) |
| `pending:{wallet}:{cohort}` | `{dayIndex, proofHash, completedAt}` | A completed cohort day not yet landed; Phase 1 "Done — tap to submit" (FR-024b) |
| `streak:{wallet}` | `{current, longest, lastDay}` | `lastDay` = global day index `floor((chainNow − 18,000) ÷ 86,400)` (turns over at 05:00 UTC, OD-6); +1 when a day completes on `lastDay + 1`; reset when a day is skipped; untouched by forfeits (FR-049…FR-052) |
| *(bundled resource)* | JSON | Scripted wallet public keys from `kotlin/core/src/main/resources/scripted-wallets.json` (FR-043a) — read-only, not in MMKV |

Nothing here is trusted by the contract: a completed day only counts once `submit_day` lands, and the streak is cosmetic.

---

## 5. Client-side derived figures (FR-038a)

Computed in `kotlin/core` (`Derive.kt`) from chain accounts and chain time only, and replayed against `fixtures/derived-figures.json` in unit tests (R2).

| Figure | Definition |
|---|---|
| Survivor counter | `count(standing) / member_count_at_start` |
| Projected pool | `pool + Σ over breached-but-unrecorded members of (stake − floor(stake × 20 ÷ 100))` |
| Projected share (member *m*) | `floor(projected_pool × m.stake ÷ Σ stake of standing members)` |
| Roster | standing members first; then `Forfeited` or breached members by `days_lasted` descending (FR-062); scripted labels from the bundled list (FR-064) |
| Time left today | `deadline_of(day_index) − chainNow` (FR-046) |
| Warning window | Time left ≤ 600 s (FR-047) |

`member_count_at_start` = `member_count` read at or after `start` (the roster is final from the join cutoff, FR-010).
