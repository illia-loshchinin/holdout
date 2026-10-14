# Data model — 001-holdout-hackathon

**Phase 1 output of `/speckit-plan`.** Behaviour, state machines and edge cases are fixed by `spec.md` §5–§8 (the edge scenarios ES-* are in §5). This file fixes **what is stored where**. Instruction signatures are in `contracts/program-instructions.md`.

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
| `fee_destination` | Pubkey | The **operations** destination: the treasury wallet's SKR token account. Receives the operations part of every forfeit and rounding remainders (FR-027, FR-032a, R14, H35); since 2026-10-12 nothing of a no-finisher pool (FR-037) |
| `developer_destination` | Pubkey | The **developer** destination: an SKR token account fixed at `initialize`. Receives the developer part of every forfeit (FR-027, FR-037f, H35); since 2026-10-12 nothing of a no-finisher pool, and `settle_finalize` no longer takes the account |
| `genesis_gate_enabled` | bool | `false` on devnet, turned on at mainnet launch (R17, H38). The group it checks against is the compile-time constant `SEEKER_GENESIS_GROUP` (`GT22s89nU4iWFkNXj1Bw6uYhJJWDRPpShHt4Bk8f99Te`), not a field: no layout change |
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
| `league_id` | u8 | 0 = 7-day, 1 = 21-day; test leagues ≥ 100 (admin, `test-leagues` builds only: LiteSVM and the local validator, per the `create_league` row of `contracts/program-instructions.md` and quickstart §5a; and the devnet program, which is built with the feature for the short test lobby — FR-044a, T092, constitution v2.3.0; a mainnet build never carries it) |
| `duration_days` | u8 | 7 for league 0, 21 for league 1, enforced by `create_league`; a test league's is free but never zero. `open_cohort` copies it into every cohort permanently, and a zero-day league would make every member who never sat a day a finisher |
| `reps_per_day` | u16 | 25 for both judged leagues, enforced by `create_league`; a test league's is free but never zero. Copied into every cohort permanently, and a zero would be a daily obligation with nothing to do |
| `exercise` | u8 | `0` = push-ups. `1` = negative (eccentric) push-ups and `2` = decline push-ups are **reserved and rejected** by `create_league` in this build, so a later exercise needs no new layout (H30, constitution V) |
| `day_length` | i64 | 86,400 on devnet; smaller only for test leagues (research R11) |
| `first_start` | i64 | A Monday 05:00:00 UTC |
| `cadence` | i64 | 604,800 (7 days) |
| `join_lock` | i64 | 3,600 (join cutoff = start − 1 h, FR-008); `0` for the test lobby, so a test cohort stays joinable until its own start (FR-044a) |
| `next_index` | u32 | Index the next `open_cohort` must use |
| `form` | u8 | How reps are judged, copied to every cohort (H35): `FORM_KNEES_ALLOWED` for the 7-day league (internally Easy; members read **Spark · 7-day**), `FORM_TOES` for the 21-day league (internally Hard; **Habit · 21-day**) — spec §1a |
| `boost_reserve` | u64 | SKR in the league's reserve vault free for prize boosts. Credited by a pledge released to the boost (`settle_count`: replaced, or the excess over a smaller refund; `release_comeback`) and by the pool of a cohort nobody finished (`settle_finalize`); debited only by a boost drawn at finalize (FR-037c, FR-037d, FR-037e, FR-037h, INV-10) |
| `comeback_reserve` | u64 | The sum of **open pledges plus refunds owed but not yet drawn** — SKR in the reserve vault that no boost may touch. + the pledge when `settle_count` counts a forfeit; − a pledge replaced or released to `boost_reserve`; − the owed refunds drawn at the round's `settle_finalize` (FR-037c, FR-037e, INV-10) |
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
| `member_count` | u32 | +1 `join_cohort` (also a comeback late join after the start, FR-037e), −1 `leave_cohort` |
| `held_stake` | u64 | + stake on join; − stake on leave, forfeit, claim |
| `pool` | u64 | + 70 % of each recorded forfeit (the stake minus its 30 % fee); + the boost drawn at finalize; − rounding remainder at finalize; − the whole pool moved to the boost reserve when nobody finished; − shares paid on claim (FR-027, FR-037, FR-037d, INV-2) |
| `comeback_due` | u64 | + the 10 % pledge of each recorded forfeit, held in this cohort's vault; − when `settle_count` counts that forfeit and moves the pledge to the league's reserve vault. Back to 0 once every member is counted (FR-037c) *(2026-10-12; was the 5 % comeback-reserve share, and `boost_due` — the 5 % boost share — is removed)* |
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
| `no_finisher_boost` | u64 | The whole pool when there are no finishers, moved to the league's boost reserve; nothing is paid to a destination and nothing carries over into another cohort (FR-037; to the boost reserve since 2026-10-12, renamed from `no_finisher_fee`) |
| `boost_received` | u64 | `min(floor(10 % × pool), league.boost_reserve)` drawn into the pool before shares (cap 10 % since 2026-10-10); 0 with no finishers or no forfeits (FR-037d) |
| `comeback_owed` | u64 | Σ over the cohort's finishers who made their comeback good of `min(floor(10 % × forfeited stake), their stake)`, summed as `settle_count` counts them (FR-037e) |
| `comeback_funded` | u64 | `min(comeback_owed, league.comeback_reserve)` — always `comeback_owed`, because the reserve holds every owed refund: drawn from the league's `comeback_reserve` at finalize, in full (FR-037e, INV-10) |
| `comeback_claimed` | u64 | Refunds paid out with claims so far; a claim pays the member's `comeback_entitled` in full (FR-037e). Not written once: rises per claim |

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
| `comeback_entitled` | u64 | Set once by `settle_count` when this finisher made a comeback good (the cohort is one of the member's comeback rounds, `comeback_round` to `comeback_last_round`): `min(floor(10 % × forfeited stake), stake)`; paid in full with the claim; 0 otherwise (FR-037e) |
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
| `leagues[l].comeback_stake` | u64 | The stake of the latest **counted** forfeit in that slot while its pledge is open; 0 once used or released. The pledge is `floor(10 % × comeback_stake)`. Written only by `settle_count` (opening, replacing, making good, releasing) and `release_comeback`, in cohort order for counts (FR-037e, INV-9) |
| `leagues[l].comeback_league` | u8 | The league of the open pledge (`league_id`): every test league shares one progress slot, so the slot alone does not say which league's round it is *(2026-10-12; replaces `comeback_from`)* |
| `leagues[l].comeback_round` | u32 | `first`: the index of the first cohort of `comeback_league` that starts after the missed deadline — the smallest `k` with `first_start + k × cadence > missed_deadline` (FR-037e) *(2026-10-12; replaces `comeback_until`; the 14-day window is gone)* |
| `leagues[l].comeback_last_round` | u32 | `last`: `== comeback_round` when that round is still open to join the second after the miss (`missed_deadline + 1 < first_start + first × cadence − join_lock`), else `comeback_round + 1` — after a miss on a round's last day the member may join `first` late or `first + 1`. A finish of any index from `comeback_round` to here makes the pledge good (FR-037e) |
| `leagues[l].last_changed` | i64 | The instant of the member's most recent outcome in that league — the cohort's `end` for a clear, the missed `deadline_of(days_completed)` for a forfeit — and **never the time the transaction landed**. Written as `max(last_changed, t)`, so neither page order nor a late crank run can change it. Leaderboard tie-break (FR-060b) |
| `running` | {cohort: Pubkey, end: i64} | Membership in a started cohort; default = none |
| `pending` | {cohort: Pubkey, start: i64, end: i64} | Lobby place; default = none. `end` is the lobby cohort's own end, copied in as the place is taken: normalisation promotes the place without the cohort account in hand, and `running.end` is the only figure the overlap guard has (FR-006b, INV-4) |
| `last_faucet_day` | i64 | Day index of last faucet claim; −1 = never. **Informational since 2026-10-12**: `claim_faucet` still stamps it, nothing reads it (the per-wallet daily limit is removed, FR-042a) |
| `bump` | u8 | |

`cleared` and `forfeited` are order-independent counters: `forfeited` rises the moment a forfeit is recorded (FR-060a), `cleared` when the cohort is counted. Only the two streak figures need cohort order, and only `settle_count` writes them.

**Slot normalisation** (run at the start of `join_cohort`, `leave_cohort`, `submit_day`, and when forfeits and counts touch the profile): if `pending.start ≤ now`, move `pending` into `running`, **carrying `pending.end` over as `running.end`** — a promotion that left the end at zero would read as “no obligation” and let the very next join start inside the cohort it just promoted. Recording a forfeit or counting a finisher clears `running` when it matches that cohort.

Per-league progress is recorded from Phase 1; league leaderboards reading it are Phase 2 (spec §3a).

### 1.6 Program-owned helpers

| Account | Seeds | Purpose |
|---|---|---|
| Faucet vault | `["faucet_vault"]` | Program-owned lamport store for SOL top-ups and sponsor reimbursement (R13) |
| Mint authority | `["mint_authority"]` | Signs `mint_to` for faucet SKR |
| Cohort vault | `["vault", cohort]` | SKR token account holding held stakes + pool + the pledges of recorded forfeits not yet counted + refunds drawn and not yet claimed (INV-1) |
| League reserve vault | `["reserve", league_id]` | SKR token account holding the league's boost and comeback reserves. Tokens move in at `settle_count` (a counted forfeit's pledge) and `settle_finalize` (a no-finisher pool) and out only at `settle_finalize` (a boost, owed refunds); a released pledge moves no token, only the two reserve figures (FR-037c–e, FR-037h) |

---

## 2. Validation rules (enforced on-chain)

| Rule | Source | Where |
|---|---|---|
| Join only while `status == Open` — except the comeback late join: the member whose comeback round `first` it is (their forfeited or in-breach membership and its cohort, passed as `comeback_membership` and `comeback_cohort`) may join it while `Locked` or `Running`, until it is settled or its first day's deadline passes (else `JoiningClosed`, 6021) | FR-008, FR-010, FR-037e | `join_cohort` |
| `stake == chips[tier]` for `tier ∈ 0..=3` | FR-055 | `join_cohort` |
| At most one `pending`; new cohort `start ≥ running.end` | FR-006a/b, INV-4 | `join_cohort` |
| Either league joinable by any member — no ladder gate, no qualification (removed 2026-10-10 before the first deploy, so no stored account changes format) | FR-003 | `join_cohort` |
| Genesis check **only if** `genesis_gate_enabled`: remaining accounts `[token account, mint]`, both Token-2022; token account owned by the member, of that mint, amount ≥ 1; the mint's `TokenGroupMember.group == SEEKER_GENESIS_GROUP` — else 6024 | FR-003b, R17 | `join_cohort` |
| Leave only while `Open`; full stake refund | FR-009 | `leave_cohort` |
| Submit only while `Running`, `day_index == days_completed`, `now ≤ deadline_of(days_completed)` | FR-013, FR-017, FR-018, FR-025, INV-8 | `submit_day` |
| Forfeit only if `in_breach(now)`; fee `floor(stake × 30 ÷ 100)`, split 10 comeback pledge / 10 developer / 10 operations, pledge and developer floored, operations taking the rounding, the pool taking the remainder of the stake | FR-026, FR-027, INV-2 | `record_forfeits` |
| Count only when `status == Settling`; any member of the page still in unrecorded breach is forfeited first by the same rule as `record_forfeits`, then counted; a counted forfeit moves its pledge into the reserve vault and opens or replaces the comeback; a counted finisher of a comeback round (`first` to `last`) is owed `min(pledge, stake)` and the excess is released; a finisher after `last` releases the pledge | spec §8.1, FR-030, FR-037c, FR-037e | `settle_count` |
| Count cohort `i > 0` only once the league's cohort `i − 1` is `settled`, so streaks are written in cohort order | FR-061a, INV-9 | `settle_count` |
| Finalize only when `counted == member_count` | R10, INV-5 | `settle_finalize` |
| Claim only when `settled`, `state == Finished` | FR-034, INV-6 | `claim_payout` |
| Open only `index == next_index`, previous cohort started | R11, FR-007a | `open_cohort` |
| A no-finisher cohort's whole pool goes to the league's boost reserve and is recorded as `no_finisher_boost`; the developer and operations already took their parts at each forfeit; nothing carries over into another cohort's pool, so no other cohort's money is involved — finalization reads the previous cohort only for its `settled` flag, to keep cohort order (revised 2026-10-05; half each 2026-10-09; ordering check 2026-10-06; to the boost reserve 2026-10-12) | FR-037, FR-030a | `settle_finalize` |
| Release a pledge only when no round can still make it good: every round the league has opened is settled (once `next_index > first`, the passed cohort `min(last, next_index − 1)` is settled; the wrong cohort is `AccountMismatch`), and a round not opened yet (`next_index ≤ last`) is past its first day's deadline by the schedule; the pledge must be of the passed league (`NoComebackToRelease`, `ComebackStillOpen`) | FR-037h, INV-10 | `release_comeback` |
| Chip set: 4 increasing positive amounts; operator only | FR-054, R14 | `publish_chip_set` |
| Only the program's current upgrade authority may `initialize`, verified against `ProgramData` | R14, constitution I | `initialize` |
| No cohort opens before the first chip set is published (`chip_set.version > 0`) | FR-055b | `open_cohort` |
| Faucet: payer ∈ {`sponsor`, recipient}; no per-recipient limit (removed 2026-10-12); every lamport leaving the vault charged to the day's budget; mints `chip_set.max_top_tier`, not today's `amounts[3]` | FR-042a, R13 | `claim_faucet` |
| Sponsor refill only below half its float, charged to the same daily budget; no signature from the sponsor | FR-042b, R13 | `top_up_sponsor` |
| `exercise` must be `0` — the reserved values are layout, not behaviour, in this build | H30, constitution V | `create_league` |

## 3. Invariants → where they are checked

| Invariant | Mechanism | Test |
|---|---|---|
| **INV-1** custody | `vault.amount == held_stake + pool + comeback_due + (comeback_funded − comeback_claimed)` after every instruction touching a cohort, and the reserve vault holds `boost_reserve + comeback_reserve` | Assert in every integration test step; property test |
| **INV-2** pool sources | Only `record_forfeits` (and the unrecorded breach `settle_count` splits), `settle_finalize` (the boost in, the rounding remainder and a no-finisher pool out) and claims change `pool` | Property test over random instruction sequences |
| **INV-3** days | `submit_day` requires `day_index == days_completed == day_index(now)`, so `days_completed ≤ max_days_completed(now)` | Unit + property test |
| **INV-4** one running, one pending, no overlap | Profile slot rules | Scenario tests incl. 21-day refusal (AC-13) |
| **INV-5** denominator once | `settle_finalize` requires `!settled` | Double-finalize test |
| **INV-6** settled is final | Only `claim_payout` accepts a settled cohort | Instruction-matrix test |
| **INV-7** idempotent automation | Per-membership state and `counted` flags | Pages out of order and repeated (AC-7) |
| **INV-8** breach blocks submit | `submit_day` deadline guard, no dependency on forfeits. The guard is ordered so the deadline is read **before** the current-day check: a missed day always falls on a day that has since turned over, so the reverse order answers every late submission with `WrongDay` and 6032 is never raised. Checked per step at the **post-transaction** clock — reading the breach at the pre-step instant passes a submission the step's own clock advance made late | V16 |
| **INV-9** streaks follow cohort order | Streaks written only by `settle_count`, which requires the league's previous cohort to be settled. Checkable on any snapshot: for a wallet, take its memberships whose cohort is `settled`, order them by cohort `start`, and count the finishes since the last forfeit — that number is `current_streak`, and the largest it reaches across that history is `longest_streak` | Per-step derivation in the invariant checker (T017) + the interleaved-settlement scenario across two cohorts of one league (T077a) |
| **INV-10** reserves *(amended 2026-10-12)* | `comeback_reserve == Σ over profiles of open pledges + Σ over cohorts of (comeback_owed − comeback_funded)` (it covers every open pledge and every owed refund, with nothing else in it); `settle_finalize` draws the boost from `boost_reserve` only; the boost reserve changes only at `settle_count`, `settle_finalize` and `release_comeback` | Per-step derivation in the invariant checker (T017) + the comeback scenarios (replace, make good, release, no finisher, shared test slot) |

---

## 4. Device-local records (MMKV for Android, never on-chain)

| Key | Value | Rules |
|---|---|---|
| `clock` | `{chainNow, slot, readAtElapsedRealtime}` | Refreshed on start, resume, each confirmed transaction; advanced with `SystemClock.elapsedRealtime()` (R7) |
| `progress:{wallet}:{scope}:{dayIndex}` | `{banked, target, completedAt?}` | `scope` = cohort address or `challenge`; `banked` is a multiple of 10; **written before "banked" is shown** (FR-023, R6) |
| `pending:{wallet}:{cohort}` | `{dayIndex, proofHash, completedAt}` | A completed cohort day not yet landed; Phase 1 "Done — tap to submit" (FR-024b) |
| `streak:{wallet}` | `{current, lastDay, longest, freezes}` | Stored as the one string `streak,day,longest,freezes` under `streak_rec:{wallet}` (`StreakRecord` in `storage/Store.kt`), so a day and its freezes are never saved apart; records written before the freezes existed are read from the old `streak:`, `streak_day:`, `streak_best:` and `streak_freezes:` keys. `lastDay` = global day index `floor((chainNow − 18,000) ÷ 86,400)` (turns over at 05:00 UTC, OD-6). A completed day on `lastDay + 1` adds 1. A **freeze** (`freezes`, 0–2) is earned when `current` reaches a multiple of 7 (7, 14, 21 …) and at most two are held; each missed day uses one automatically, and a frozen day bridges the streak without adding to it (FR-049, H35, `StreakStore.recordCompletedDay`). Only when the missed days outnumber the freezes held does `current` reset to 1, with the freezes used up. Untouched by forfeits (FR-049…FR-052) *(amended 2026-10-11: was `{current, longest, lastDay}`, "reset when a day is skipped", before the freezes were recorded as built)* |
| `toes:{wallet}:{dayIndex}` · `toes_best:{wallet}` | `{toes, measured}` · best `toes` | The Daily 25's toes meter (FR-052a, H35): of the reps measured at 45° that day, how many the Habit (Hard) form's own knee rule would have counted, stored as the one string `toes,measured` so the two never disagree after a kill; `toes_best` is the best day's `toes`. Reps whose stance was not measured are not counted. On the phone only; gates nothing, appears on no leaderboard, never paid against (FR-051) |
| `seeker:{wallet}` | `{mint, confirmedAt, tokenAccount}` | Written only when the mainnet check found a Seeker Genesis Token for this wallet (FR-003b, R17); then never re-asked, so an outage never blocks a confirmed owner (FR-003d). Absence is never stored — a wallet with none is checked again on its next join. `tokenAccount` is the Token-2022 account the token was found in, kept with the mint so the mainnet join can send both as `join_cohort`'s remaining accounts (R17); stored as the string `<confirmedAtMillis>\|<mint>\|<tokenAccount>` (`SeekerConfirmation` in `storage/Store.kt`), the last part empty when a confirmation written before the account was kept is read back |
| `tour:{wallet}` | `{seenVersion}` | The first-run welcome and tour (six cards since 2026-10-13, the welcome before them, FR-067a; five from 2026-10-12, four from 2026-10-11) has been completed or skipped by this wallet; skipping lands on card 6 first, so the disclosure is always seen on first run (FR-067). Absent = show it after connecting; the tour stays re-openable from the app whatever this holds. *(2026-10-13)* `seenVersion` is not raised for the six-card tour: a wallet that saw the five-card one is not shown the welcome or the tour again (research R25) |
| `seen:{wallet}` | `{dayIndex}` *(added 2026-10-12, FR-069f, ES-3a)* | The global day index (as `streak:`'s `lastDay`) of the day this phone first saw the wallet — one long, written once when the wallet first connects on the phone and never rewritten — but for one value: a wallet first met while chain time is unknown (an offline first run) is kept as `SEEN_PENDING`, and the first call with a known day settles it once, to that day, or to `SEEN_EARLIER` when the phone by then holds training of the wallet (a streak, reps banked or a day done; the tour that run showed does not count) *(2026-10-13, Session 2026-10-13)*. With the chain showing the wallet in a Holdout that started before that day and nothing banked on this phone today, it makes Today draw the new-phone banner on that day only. **Upgrade:** a wallet with no `seen:` key but a `tour:`, a streak record or any banked-rep record on this phone is treated as seen before today and nothing is written, so an update never shows the banner. On the phone only; gates nothing |
| `looked:{wallet}:{cohort}` | `{standing}` *(added 2026-10-11, FR-069; **retired 2026-10-13** with the Standing tab and its dot — no longer written or read; a key already on a phone is left in place and ignored)* | How many were still standing in the Holdout when the member last had its detail (the Standing tab's root) on screen — one integer, rewritten each time the detail is shown. Standing's tab shows its dot while fewer are standing now (`Derive.survivors`) than that number, and its name says how many dropped; a settled Holdout does not light it (the claim lights Holdouts'). It is the only dot that needs a stored record: Today's (today's reps not done), Holdouts' (a claim is waiting) and Me's (SKR or SOL too low to stake or submit) are derived from state already read (FR-069). Absent = never looked: the count is taken from the number who started, so a Holdout the member has just joined shows no dot until someone drops out. On the phone only; gates nothing |
| `sitting:{wallet}:{scope}` | `{startedAt}` *(added 2026-10-11, ES-1, ES-2)* | The sitting-in-progress marker: written when counting starts, removed on every clean exit of the sitting (stop, completion, the app leaving the foreground, which ends the sitting, FR-020). If it is present at the next Today (reading it clears it, so it is said once; a Daily 25 marker waits while the day is not yet known), the app crashed or was killed mid-sitting, and the Stopped screen's crash variant says what was kept — the blocks in `progress:` of the day in `sittingday:` (the current day for a marker from an older build, with none), with no time left once that day has gone — and what was lost; every Holdout of the member's is read, a forfeited or ended one included *(2026-10-13)*; nothing is shown when nothing was banked or the day was completed anyway. `scope` as in `progress:`. Never read by the counter; changes no banked figure |
| `sittingday:{wallet}:{scope}` | `{dayIndex}` *(added 2026-10-13, ES-1, ES-2; Session 2026-10-13)* | The day the sitting under the `sitting:` marker banks into — one long: the global day index for the Daily 25, the cohort day for a Holdout. Written with the marker when counting starts (`SittingMarker.start`), moved when a Daily 25 rolls over at 05:00 UTC (`moveTo`), removed with the marker. The crash page reads `progress:` of this day, not the day current at restart. Absent (a marker from an older build) = the current day. A Daily 25 started while the day is unknown keeps `UNKNOWN_DAY`; when the chain's answer moves its reps to their day (`OfflineDays.settle`), this day moves with them, and a crash page read before that reads the reps still under the unknown day *(2026-10-13)*. On the phone only; changes no banked figure |
| `unconfirmed:{wallet}` | `{action},{cohort},{day},{sentAt}` per send, `;` between *(added 2026-10-13, FR-070a, ES-20; Session 2026-10-13)* | The joins, days and claims the wallet sent that the network did not confirm and that may still land (`PendingTx`): `action` is `Join`, `Submit` or `Claim`, `cohort` the Holdout's address in base58, `day` the Holdout day of a submit (else 0), `sentAt` the monotonic clock (`elapsedRealtime`) when the send reached the network. Written when a send reaches the network (the Confirming step), kept if it ends may-still-land, and rewritten by any other outcome and whenever a chain read settles one; removed when none is left. Read when the wallet is watched, keeping only a send with 0 ≤ now − `sentAt` < 120 s, so one from before the phone restarted (the clock restarts with it) is dropped; each kept one keeps its action shut until a read settles it (FR-070a). On the phone only; trusted by nothing on chain |
| *(bundled resource)* | JSON | Scripted wallet public keys from `kotlin/core/src/main/resources/scripted-wallets.json` (FR-043a) — read-only, not in MMKV |

Streak milestones (FR-049a) add no key: "reached today" is read from `streak:` (today's day complete, `current` a milestone). The pre-flight tip sheet (002 FR-020a) keeps nothing either: its counters live in the sitting's state and end with it.

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
| **Comeback offer** *(added 2026-10-11, FR-037g, R22; rewritten 2026-10-12, R24)* | Per progress slot (test leagues share one): the member's latest forfeit — a `Forfeited` membership, or one in breach and not yet recorded — by missed deadline `missed = deadline_of(days_completed)`; `lost = stake`. **The rounds:** `first` = the smallest `k` with `first_start + k × cadence > missed` over the league's schedule (the forfeit's own league's; amended 2026-10-12, FR-044a: a test league retired by a newer one still opens the rounds its comebacks need, and while the shared test slot records an open pledge the offer comes from the latest forfeit in the pledged league, `comeback_league`, never from a newer forfeit in another test league, which opens no comeback; with no pledge recorded the latest forfeit counts; a pledge whose forfeit the phone cannot see gives no offer); the **late** case is `missed + 1 ≥ first_start + first × cadence − join_lock` (the round is closed to everyone else), in which `last = first + 1`, else `last = first` — the program's own rule (FR-037e), pinned by `fixtures/derived-figures.json`. Each round has its cohort if opened and its status. A round is joinable plainly while `Open` and no place is held; in the late case `first` is also joinable by this member while it is `Locked` or `Running` and `now ≤ its start + day_length − 1` (its first day's deadline), through `join_cohort`'s comeback accounts. `next` = the joinable round (the late one first); `upcoming` = a round not opened yet that joining will still be possible in when it opens — its start is ahead and it is not inside its own join lock (`start − join_lock > now`) — shown as "opens for joining in …", or "opens for joining shortly" once that instant has passed without automation opening it. **Place:** a `Joined` membership not in breach in a cohort from `first` to `last`. **Used up:** a membership in such a cohort that is `Finished`, `Claimed`, or `Joined` with every day completed — before `settle_count` counts it. **Amount:** `comebackOwed(lost, chip) = min(floor(lost × 10 ÷ 100), chip)`, exact and never "up to"; with no chip chosen, `floor(lost × 10 ÷ 100)`. **No offer** when used up, or when there is no place held, no joinable round and no `upcoming` — for instance the last round's joining closed without the member |
| **Since the end** *(added 2026-10-11)* | `chainNow − end`, while the cohort is not settled: *"Ended 4m ago"* on the settling card (FR-035a) |
| **Usually settled in** *(added 2026-10-11)* | `max(0, end + SETTLE_USUAL_SECONDS − chainNow)`, `SETTLE_USUAL_SECONDS = 900` — the crank's 10-minute pass plus 5 minutes for the pass itself (R12). Shown as *"usually within 11m"*; at `0`, *"any moment"* on Home/H0 and *"taking longer than usual"* on H4. Never a promise (FR-035a); the finished notice is set for the same instant (FR-068b) |

`member_count_at_start` = `member_count` read at or after `start` (the roster is final from the join cutoff, FR-010, except a comeback late join, FR-037e, which can raise it until the cohort's first day's deadline).
