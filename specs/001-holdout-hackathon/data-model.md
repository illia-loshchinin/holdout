# Data model — 001-holdout-hackathon

**Phase 1 output of `/speckit-plan`.** Behaviour, state machines and edge cases are fixed by `spec.md` §6–§8. This file fixes **what is stored where**. Instruction signatures are in `contracts/program-instructions.md`.

Units: SKR amounts are **base units** (`u64`); times are **Unix seconds of chain time** (`i64`); lamports for SOL.

---

## 1. On-chain accounts (program `holdout`)

### 1.1 `Config` — singleton · PDA `["config"]`

| Field | Type | Rule |
|---|---|---|
| `admin` | Pubkey | The signer of `initialize`, which that instruction accepts only if it is the program's current upgrade authority (R14). May only run `initialize`, `create_league` and `set_flags` — nothing else, and none of them moves funds. `create_league` covers both real leagues (ids 0 and 1, created by `crank init`) and, in builds carrying the `test-leagues` feature, a test league with `league_id ≥ 100`; there is no separate instruction for the latter |
| `operator` | Pubkey | May only run `publish_chip_set` |
| `sponsor` | Pubkey | Fee payer accepted by `claim_faucet` beside the recipient itself; holds only its float, which anyone may refill through `top_up_sponsor` (R13) |
| `skr_mint` | Pubkey | Mock SKR mint; its mint authority is PDA `["mint_authority"]` |
| `fee_destination` | Pubkey | The **operations** destination: the treasury wallet's SKR token account. Receives the operations part of every forfeit, the rest of a no-finisher pool after the developer's half, and rounding remainders (FR-027, FR-037, FR-032a, R14, H35) |
| `developer_destination` | Pubkey | The **developer** destination: an SKR token account fixed at `initialize`. Receives the developer part of every forfeit and half (floored) of a no-finisher pool (FR-027, FR-037, FR-037f, H35) |
| `genesis_gate_enabled` | bool | `false` on devnet (R17) |
| `chip_set.amounts` | [u64; 4] | $10, $25, $50, $100 tiers in SKR; strictly increasing, all > 0 |
| `chip_set.version` | u32 | +1 on every publish; `0` until the first publish, which no cohort may precede (FR-055b) |
| `chip_set.max_top_tier` | u64 | The largest `amounts[3]` ever published — `max(max_top_tier, a3)` at each publish, never decreasing. What the faucet mints, so a claim funds the top chip of any open cohort even after a Phase 2 re-price lowered the tiers (FR-042a) |
| `chip_set.published_at` | i64 | Chain time of publish |
| `faucet.sol_top_up` | u64 | Lamports a claim tops a recipient up to (20,000,000 = 0.02 SOL) |
| `faucet.sponsor_float` | u64 | Lamports `top_up_sponsor` tops the sponsor up to (50,000,000 = 0.05 SOL); a refill is allowed only below half of it (R13) |
| `faucet.daily_lamport_cap` | u64 | 720,000,000 (0.72 SOL): the budget for **every** lamport leaving the faucet vault in a day — top-ups, sponsor reimbursements and sponsor refills alike (R13) |
| `faucet.lamport_day` | i64 | Day index the budget belongs to |
| `faucet.lamports_today` | u64 | Lamports already out of the vault today; resets when `lamport_day` changes |
| `bump` | u8 | |

The faucet figures above are **normative**: research R13 explains where they come from, and the README and `docs/holdout_decisions.md` H22 restate them for readers, but a change to any of them lands here first.

### 1.2 `LeagueConfig` — one per league · PDA `["league", league_id: u8]`

| Field | Type | Rule |
|---|---|---|
| `league_id` | u8 | 0 = 7-day, 1 = 21-day; test leagues ≥ 100 (admin, `test-leagues` builds only: LiteSVM and the rehearsal deployment) |
| `duration_days` | u8 | 7 for league 0, 21 for league 1, enforced by `create_league`; a test league's is free but never zero. `open_cohort` copies it into every cohort permanently, and a zero-day league would make every member who never sat a day a finisher |
| `reps_per_day` | u16 | 25 for both judged leagues, enforced by `create_league`; a test league's is free but never zero. Copied into every cohort permanently, and a zero would be a daily obligation with nothing to do |
| `exercise` | u8 | `0` = push-ups. `1` = negative (eccentric) push-ups and `2` = decline push-ups are **reserved and rejected** by `create_league` in this build, so a later exercise needs no new layout (H30, constitution V) |
| `day_length` | i64 | 86,400 on devnet; smaller only for test leagues (scope doc D-A) |
| `first_start` | i64 | A Monday 05:00:00 UTC |
| `cadence` | i64 | 604,800 (7 days) |
| `join_lock` | i64 | 3,600 (join cutoff = start − 1 h, FR-008); `0` for the test lobby, so a test cohort stays joinable until its own start (FR-044a) |
| `next_index` | u32 | Index the next `open_cohort` must use |
| `form` | u8 | How reps are judged, copied to every cohort (H35): `FORM_KNEES_ALLOWED` for the Easy 7-day league, `FORM_TOES` for the Hard 21-day league |
| `boost_reserve` | u64 | SKR in the league's reserve vault set aside for prize boosts; credited only when a cohort of this league finalizes (FR-037c, FR-037d, INV-10) |
| `comeback_reserve` | u64 | SKR in the reserve vault set aside for comeback refunds (FR-037c, FR-037e) |
| `reserve_vault_bump`, `bump` | u8 | Reserve vault = token account PDA `["reserve", league_id]`, authority = league PDA |

### 1.3 `Cohort` · PDA `["cohort", league_id: u8, index: u32 LE]`

**Fixed at creation (FR-007, constitution V):**

| Field | Type | Value at `open_cohort` |
|---|---|---|
| `league_id`, `index` | u8, u32 | From seeds |
| `start` | i64 | `first_start + index × cadence` |
| `join_cutoff` | i64 | `start − join_lock` |
| `duration_days`, `reps_per_day`, `day_length`, `exercise`, `form` | u8, u16, i64, u8, u8 | Copied from `LeagueConfig` |
| `chips` | [u64; 4] | Copied from `Config.chip_set.amounts` (FR-055) |
| `vault_bump`, `bump` | u8 | Vault = token account PDA `["vault", cohort]`, authority = cohort PDA |

**Running totals:**

| Field | Type | Changed by |
|---|---|---|
| `member_count` | u32 | +1 `join_cohort`, −1 `leave_cohort` |
| `held_stake` | u64 | + stake on join; − stake on leave, forfeit, claim |
| `pool` | u64 | + 70 % of each recorded forfeit (the stake minus its 30 % fee); + the boost drawn at finalize; − rounding remainder at finalize; − shares paid on claim (FR-027, FR-037d, INV-2) |
| `boost_due` | u64 | + 5 % of each recorded forfeit: the boost-reserve share, held in this cohort's vault until it finalizes and moves to the league (FR-037c) |
| `comeback_due` | u64 | + 5 % of each recorded forfeit: the comeback-reserve share, held likewise (FR-037c) |
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
| `no_finisher_fee` | u64 | The whole pool when no finishers: `floor(pool ÷ 2)` goes to the developer destination and the rest to operations; nothing carries over (FR-037, half each since 2026-10-09) |
| `boost_received` | u64 | `min(floor(10 % × pool), league.boost_reserve)` drawn into the pool before shares (cap 10 % since 2026-10-10); 0 with no finishers or no forfeits (FR-037d) |
| `comeback_owed` | u64 | Comeback refunds owed to this cohort's finishers, summed as `settle_count` counts them (FR-037e) |
| `comeback_funded` | u64 | `min(comeback_owed, league.comeback_reserve)`, drawn at finalize (FR-037e) |
| `comeback_claimed` | u64 | Refunds paid out with claims so far; a claim pays `floor(entitled × comeback_funded ÷ comeback_owed)` (FR-037e). Not written once: rises per claim |

**Derived, never stored** (`kotlin/core` `Derive.kt`, mirrored in program guards and pinned by `fixtures/`):

| Value | Definition |
|---|---|
| `end` | `start + duration_days × day_length` |
| `status(now)` | `Open` if `now < join_cutoff` · `Locked` if `< start` · `Running` if `< end` · `Settling` if `!settled` · `Settled` |
| `day_index(now)` | `floor((now − start) ÷ day_length)` while `Running` — the program's `schedule::days_elapsed`, which clamps to `0 … duration_days` outside that window |
| `deadline_of(k)` | `start + (k + 1) × day_length − 1` — the **last second** of day `k` |
| `max_days_completed(now)` | `0` before `start`, else `min(day_index(now) + 1, duration_days)` — the INV-3 ceiling. The day in progress may be completed at any second of it, so a member submitting day 0 in the cohort's first second holds `days_completed == 1` while no day has yet elapsed |

The day boundary therefore falls exactly on 05:00:00 UTC: day `k` accepts completions at chain times `start + k·L … start + (k+1)·L − 1`, and a member is in breach from the next second (constitution II: `now > deadline_of(days_completed)`).

### 1.4 `Membership` · PDA `["member", cohort, wallet]`

| Field | Type | Rule |
|---|---|---|
| `cohort`, `wallet` | Pubkey | Seeds |
| `tier` | u8 | 0–3 |
| `stake` | u64 | `== cohort.chips[tier]` at join; never changes (FR-058) |
| `joined_at` | i64 | Chain time of join |
| `days_completed` | u8 | +1 per accepted `submit_day`; never above `max_days_completed(now)` (INV-3) |
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
| `leagues[l].cleared` | u16 | +1 when counted as finisher |
| `leagues[l].forfeited` | u16 | +1 when a forfeit is recorded |
| `leagues[l].current_streak` | u16 | +1 on clear, 0 on forfeit (FR-061). Written **only by `settle_count`**, which runs in cohort order, so the figure never depends on the order automation happens to run in (FR-061a, INV-9) |
| `leagues[l].longest_streak` | u16 | `max(longest, current)`, written in the same step |
| `leagues[l].comeback_stake` | u64 | The stake of the latest **counted** forfeit in that league while it can still earn a comeback; 0 once used. Written only by `settle_count`, in cohort order (FR-037e, INV-9) |
| `leagues[l].comeback_from`, `leagues[l].comeback_until` | i64 | That forfeit's missed deadline, and the last instant a cohort may start and still be its comeback: 14 of the league's days after it (FR-037e) |
| `leagues[l].last_changed` | i64 | The instant of the member's most recent outcome in that league — the cohort's `end` for a clear, the missed `deadline_of(days_completed)` for a forfeit — and **never the time the transaction landed**. Written as `max(last_changed, t)`, so neither page order nor a late crank run can change it. Leaderboard tie-break (FR-060b) |
| `running` | {cohort: Pubkey, end: i64} | Membership in a started cohort; default = none |
| `pending` | {cohort: Pubkey, start: i64, end: i64} | Lobby place; default = none. `end` is the lobby cohort's own end, copied in as the place is taken: normalisation promotes the place without the cohort account in hand, and `running.end` is the only figure the overlap guard has (FR-006b, INV-4) |
| `last_faucet_day` | i64 | Day index of last faucet claim; −1 = never |
| `bump` | u8 | |

`cleared` and `forfeited` are order-independent counters: `forfeited` rises the moment a forfeit is recorded (FR-060a), `cleared` when the cohort is counted. Only the two streak figures need cohort order, and only `settle_count` writes them.

**Slot normalisation** (run at the start of `join_cohort`, `leave_cohort`, `submit_day`, and when forfeits and counts touch the profile): if `pending.start ≤ now`, move `pending` into `running`, **carrying `pending.end` over as `running.end`** — a promotion that left the end at zero would read as “no obligation” and let the very next join start inside the cohort it just promoted. Recording a forfeit or counting a finisher clears `running` when it matches that cohort.

Per-league progress is recorded from Phase 1; league leaderboards reading it are Phase 2 (spec §3a).

### 1.6 Program-owned helpers

| Account | Seeds | Purpose |
|---|---|---|
| Faucet vault | `["faucet_vault"]` | Program-owned lamport store for SOL top-ups and sponsor reimbursement (R13) |
| Mint authority | `["mint_authority"]` | Signs `mint_to` for faucet SKR |
| Cohort vault | `["vault", cohort]` | SKR token account holding held stakes + pool + the boost and comeback dues not yet moved to the league (INV-1) |
| League reserve vault | `["reserve", league_id]` | SKR token account holding the league's boost and comeback reserves; only `settle_finalize` moves anything in or out (FR-037c–e) |

---

## 2. Validation rules (enforced on-chain)

| Rule | Source | Where |
|---|---|---|
| Join only while `status == Open` | FR-008, FR-010 | `join_cohort` |
| `stake == chips[tier]` for `tier ∈ 0..=3` | FR-055 | `join_cohort` |
| At most one `pending`; new cohort `start ≥ running.end` | FR-006a/b, INV-4 | `join_cohort` |
| Either league joinable by any member — no ladder gate, no qualification (removed 2026-10-10 before the first deploy, so no stored account changes format) | FR-003 | `join_cohort` |
| Genesis check **only if** `genesis_gate_enabled` | R17 | `join_cohort` |
| Leave only while `Open`; full stake refund | FR-009 | `leave_cohort` |
| Submit only while `Running`, `day_index == days_completed`, `now ≤ deadline_of(days_completed)` | FR-013, FR-017, FR-018, FR-025, INV-8 | `submit_day` |
| Forfeit only if `in_breach(now)`; fee `floor(stake × 30 ÷ 100)`, split 5 boost / 5 comeback / 10 developer / 10 operations, each floored, operations taking the rounding | FR-026, FR-027, INV-2 | `record_forfeits` |
| Count only when `status == Settling`; any member of the page still in unrecorded breach is forfeited first by the same rule as `record_forfeits`, then counted | spec §8.1, FR-030 | `settle_count` |
| Count cohort `i > 0` only once the league's cohort `i − 1` is `settled`, so streaks are written in cohort order | FR-061a, INV-9 | `settle_count` |
| Finalize only when `counted == member_count` | R10, INV-5 | `settle_finalize` |
| Claim only when `settled`, `state == Finished` | FR-034, INV-6 | `claim_payout` |
| Open only `index == next_index`, previous cohort started | R11, FR-007a | `open_cohort` |
| A no-finisher cohort's whole pool goes out: half (floored) to the developer destination, the rest to operations; nothing carries over, so no other cohort's money is involved — finalization reads the previous cohort only for its `settled` flag, to keep cohort order (revised 2026-10-05; half each 2026-10-09; ordering check 2026-10-06) | FR-037, FR-030a | `settle_finalize` |
| Chip set: 4 increasing positive amounts; operator only | FR-054, R14 | `publish_chip_set` |
| Only the program's current upgrade authority may `initialize`, verified against `ProgramData` | R14, constitution I | `initialize` |
| No cohort opens before the first chip set is published (`chip_set.version > 0`) | FR-055b | `open_cohort` |
| Faucet: payer ∈ {`sponsor`, recipient}; once per recipient per day; every lamport leaving the vault charged to the day's budget; mints `chip_set.max_top_tier`, not today's `amounts[3]` | FR-042a, R13 | `claim_faucet` |
| Sponsor refill only below half its float, charged to the same daily budget; no signature from the sponsor | FR-042b, R13 | `top_up_sponsor` |
| `exercise` must be `0` — the reserved values are layout, not behaviour, in this build | H30, constitution V | `create_league` |

## 3. Invariants → where they are checked

| Invariant | Mechanism | Test |
|---|---|---|
| **INV-1** custody | `vault.amount == held_stake + pool + boost_due + comeback_due` after every instruction touching a cohort, and the reserve vault holds `boost_reserve + comeback_reserve` | Assert in every integration test step; property test |
| **INV-2** pool sources | Only `record_forfeits`, settlement's remainder and claims change `pool` | Property test over random instruction sequences |
| **INV-3** days | `submit_day` requires `day_index == days_completed == day_index(now)`, so `days_completed ≤ max_days_completed(now)` | Unit + property test |
| **INV-4** one running, one pending, no overlap | Profile slot rules | Scenario tests incl. 21-day refusal (AC-13) |
| **INV-5** denominator once | `settle_finalize` requires `!settled` | Double-finalize test |
| **INV-6** settled is final | Only `claim_payout` accepts a settled cohort | Instruction-matrix test |
| **INV-7** idempotent automation | Per-membership state and `counted` flags | Pages out of order and repeated (AC-7) |
| **INV-8** breach blocks submit | `submit_day` deadline guard, no dependency on forfeits. The guard is ordered so the deadline is read **before** the current-day check: a missed day always falls on a day that has since turned over, so the reverse order answers every late submission with `WrongDay` and 6032 is never raised. Checked per step at the **post-transaction** clock — reading the breach at the pre-step instant passes a submission the step's own clock advance made late | V16 |
| **INV-9** streaks follow cohort order | Streaks written only by `settle_count`, which requires the league's previous cohort to be settled. Checkable on any snapshot: for a wallet, take its memberships whose cohort is `settled`, order them by cohort `start`, and count the finishes since the last forfeit — that number is `current_streak`, and the largest it reaches across that history is `longest_streak` | Per-step derivation in the invariant checker (T017) + the interleaved-settlement scenario across two cohorts of one league (T077a) |

---

## 4. Device-local records (MMKV for Android, never on-chain)

| Key | Value | Rules |
|---|---|---|
| `clock` | `{chainNow, slot, readAtElapsedRealtime}` | Refreshed on start, resume, each confirmed transaction; advanced with `SystemClock.elapsedRealtime()` (R7) |
| `progress:{wallet}:{scope}:{dayIndex}` | `{banked, target, completedAt?}` | `scope` = cohort address or `challenge`; `banked` is a multiple of 10; **written before "banked" is shown** (FR-023, R6) |
| `pending:{wallet}:{cohort}` | `{dayIndex, proofHash, completedAt}` | A completed cohort day not yet landed; Phase 1 "Done — tap to submit" (FR-024b) |
| `streak:{wallet}` | `{current, longest, lastDay}` | `lastDay` = global day index `floor((chainNow − 18,000) ÷ 86,400)` (turns over at 05:00 UTC, OD-6); +1 when a day completes on `lastDay + 1`; reset when a day is skipped; untouched by forfeits (FR-049…FR-052) |
| `tour:{wallet}` | `{seenVersion}` | The first-run tour has been completed or skipped by this wallet (FR-067). Absent = show it after connecting; the tour stays re-openable from the app whatever this holds |
| *(bundled resource)* | JSON | Scripted wallet public keys from `kotlin/core/src/main/resources/scripted-wallets.json` (FR-043a) — read-only, not in MMKV |

Nothing here is trusted by the contract: a completed day only counts once `submit_day` lands, and the streak is cosmetic.

The app **opts out of Android cloud backup and of device-to-device transfer** (`allowBackup="false"` plus explicit `<cloud-backup>` and `<device-transfer>` exclusions, since on Android 12+ turning backup off does not by itself stop a transfer). Without that, restoring a backup would carry an unfinished day's banked reps onto another device, which FR-024a and ES-3a say plainly does not happen.

---

## 5. Client-side derived figures (FR-038a)

Computed in `kotlin/core` (`Derive.kt`) from chain accounts and chain time only, and replayed against `fixtures/derived-figures.json` in unit tests (R2).

| Figure | Definition |
|---|---|
| Survivor counter | `count(standing) / member_count_at_start` |
| Projected pool | `pool + Σ over breached-but-unrecorded members of (stake − floor(stake × 30 ÷ 100))` |
| Projected share (member *m*) | `floor(projected_pool × m.stake ÷ Σ stake of standing members)` |
| Roster | standing members first; then `Forfeited` or breached members by `days_lasted` descending (FR-062); scripted labels from the bundled list (FR-064) |
| Time left today | `deadline_of(day_index) − chainNow` (FR-046) |
| Warning window | Time left ≤ 600 s (FR-047) |
| **At stake** (a Holdout open for joining) | `held_stake` — the sum of the stakes taken so far. Shown on the lobby card with the joined count (FR-053). Labelled "at stake", never "prize": an open cohort has no forfeits yet |
| **Carry-over received** | *(Removed 2026-10-05: always zero, no longer shown.)* |
| Time until joining closes | `join_cutoff − chainNow` (FR-008) |

`member_count_at_start` = `member_count` read at or after `start` (the roster is final from the join cutoff, FR-010).
