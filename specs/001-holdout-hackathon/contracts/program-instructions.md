# Contract — `holdout` program instructions

**Phase 1 output of `/speckit-plan`.** The on-chain interface the Kotlin app, the Kotlin crank and the TypeScript tests all target. Account layouts: `data-model.md` §1. State machines: spec §8, scope doc §3. Every guard below is enforced by the program, never only by a client.

Conventions: `now` = Clock sysvar `unix_timestamp`. "Anyone" = any signer paying the fee; the caller's identity never changes the outcome (FR-030a). **[Phase 2]** marks items built only after Phase 1 is complete (spec §3a).

---

## 1. Privileged instructions

| Instruction | Signer | Args | Guards | Effects |
|---|---|---|---|---|
| `initialize` | admin | `operator`, `sponsor`, `treasury_wallet`, `faucet_sol_top_up`, `faucet_daily_sol_cap` | `Config` does not exist | Creates `Config`; creates the mock SKR mint with mint authority `["mint_authority"]`; creates `fee_destination` (treasury's SKR token account); creates the faucet vault. Flags start `false` |
| `create_league` | admin | `league_id`, `duration_days`, `reps_per_day`, `exercise`, `first_start`, `day_length`, `cadence`, `join_lock` | `exercise == 0` (push-ups); `1` and `2` are reserved for later exercises and rejected here (H30). For `league_id < 100`: `day_length == 86,400`, `cadence == 604,800`, `join_lock == 3,600`, `first_start` is a Monday 05:00:00 UTC. `league_id ≥ 100` (test leagues, any timing) **only in builds with the `test-leagues` feature** — used on LiteSVM and on a separate **rehearsal** deployment with its own program ID, never on the judged program | Creates `LeagueConfig` with `next_index = 0` |
| `set_flags` | admin | `ladder_gate_enabled`, `genesis_gate_enabled` | — | Updates flags; emits `FlagsSet` |
| `publish_chip_set` | operator | `amounts: [u64; 4]` | `0 < a0 < a1 < a2 < a3` | Replaces `Config.chip_set`, `version += 1`; emits `ChipSetPublished`. Never changes an existing cohort (FR-055) |

There is **no** instruction that lets admin, operator or sponsor move a held stake, a pool or a carry-over (constitution I).

## 2. Permissionless automation

| Instruction | Accounts (beyond signer, programs) | Guards | Effects |
|---|---|---|---|
| `open_cohort(league_id, index)` | `LeagueConfig`, new `Cohort`, new vault, previous `Cohort` (if `index > 0`), `Config` | `index == next_index`; if `index > 0`, `now ≥ previous.start` | Creates `Cohort` with `start = first_start + index × cadence`, `join_cutoff = start − join_lock`, league parameters — `duration_days`, `reps_per_day`, `day_length`, `exercise` — and `Config.chip_set.amounts` copied; `next_index += 1`; emits `CohortOpened`. **[Phase 2]** also delivers any held carry-over of this league into the new cohort |
| `record_forfeits` | `Cohort`, vault, `fee_destination`, `Config`; remaining accounts = up to 10 `(Membership, Profile)` pairs | Each pair: membership belongs to cohort, profile matches membership wallet | For each pair where `state == Joined ∧ in_breach(now)`: transfer `fee = floor(stake × 20 ÷ 100)` vault → fee destination; `pool += stake − fee`; `held_stake −= stake`; `state = Forfeited`; `forfeits_recorded += 1`; profile: `forfeited += 1`, `current_streak = 0`, `last_changed = now`, clear `running` if it is this cohort; emit `ForfeitRecorded`. Pairs not in breach, or already forfeited, are skipped without error (idempotent, any order) |
| `settle_count` | `Cohort`, vault, `fee_destination`, `Config`; remaining accounts = up to 10 `(Membership, Profile)` pairs | `status == Settling` (`now ≥ end ∧ !settled`) | For each pair with `counted == false`: if `Joined ∧ in_breach` → forfeit exactly as `record_forfeits`; then if `Joined ∧ days_completed == duration_days` → `state = Finished`, `finisher_count += 1`, `finishers_per_tier[tier] += 1`, `total_finisher_stake += stake`, profile `cleared += 1`, `current_streak += 1`, `longest_streak = max`, `last_changed = now`, `qualified = true` if 7-day league, clear `running`; set `counted = true`, `cohort.counted += 1`; emit `MemberCounted`. Already-counted pairs are skipped |
| `settle_finalize` | `Cohort`, vault, `fee_destination`, `LeagueConfig`, optional carry-over target `Cohort` + its vault | `status == Settling`; `counted == member_count` | **If `total_finisher_stake > 0`:** `share_per_tier[t] = floor(pool × chips[t] ÷ T)`; `rounding_remainder = pool − Σ finishers_per_tier[t] × share_per_tier[t]`, transferred to fee destination; `pool −= rounding_remainder`. **If no finishers:** `no_finisher_fee = floor(pool ÷ 2)` → fee destination; `carry = pool − no_finisher_fee`; if the target is this league's cohort `next_index − 1` and `status(target) == Open` → transfer to target vault, `target.pool += carry`, `target.carry_over_received += carry`, `carry_over_target = target`; otherwise `carry_over_held = carry` (Phase 1 holds; delivery is Phase 2); `pool = 0`. Then `settled = true`; emit `CohortSettled` (+ `CarryOverSent` or `CarryOverHeld`) |
| `deliver_carry_over` **[Phase 2]** | source `Cohort` + vault, target `Cohort` + vault, `LeagueConfig` | `source.settled ∧ source.carry_over_held > 0`; target is same league and `Open` | Moves the held amount once; `carry_over_held = 0`; target `pool` and `carry_over_received` increase; emit `CarryOverDelivered` |

## 3. Member instructions

| Instruction | Signer | Args | Guards | Effects |
|---|---|---|---|---|
| `claim_faucet` | fee payer ∈ {`Config.sponsor`, `recipient`} | `recipient: Pubkey` | `Profile(recipient).last_faucet_day < today` where `today = floor((now − 18,000) ÷ 86,400)` (days turn over at 05:00 UTC); if a SOL top-up is needed, `sol_claims_today < daily_sol_cap` and vault holds enough | Creates `Profile` and the recipient's SKR token account if missing; mints `chip_set.amounts[3]` SKR to the recipient; tops recipient lamports up to `sol_top_up` from the faucet vault; if the payer is the sponsor, reimburses the sponsor's fee and any rent it paid from the vault; `last_faucet_day = today`; emit `FaucetClaimed`. Errors (not partial success) when the cap or vault is exhausted |
| `join_cohort` | member | `tier: u8` | `status == Open`; `tier ≤ 3`; member SKR balance ≥ `chips[tier]`; `ladder_gate_enabled ⇒ (league 0 ∨ qualified)`; `genesis_gate_enabled ⇒ genesis check` (R17); after slot normalisation: `pending` is empty, and if `running.end > now` then `cohort.start ≥ running.end` | Creates `Profile` if missing and `Membership` (member pays rent); transfers `chips[tier]` member → vault; `held_stake += stake`, `member_count += 1`; `profile.pending = {cohort, start}`; emit `MemberJoined` |
| `leave_cohort` | member | — | `status == Open`; membership `state == Joined` | Transfers `stake` vault → member; `held_stake −= stake`, `member_count −= 1`; clears `profile.pending`; **closes** `Membership` (rent to member); emit `MemberLeft` |
| `submit_day` | member | `day_index: u8`, `proof_hash: [u8; 32]` | `status == Running`; `state == Joined`; `day_index == days_completed`; `day_index == day_index(now)`; `now ≤ deadline_of(day_index)` | `days_completed += 1`; slot normalisation; emit `DayCompleted { cohort, wallet, day_index, proof_hash }` |
| `claim_payout` | member | — | `cohort.settled`; `state == Finished` | Transfers `stake + share_per_tier[tier]` vault → member; `held_stake −= stake`, `pool −= share`; `state = Claimed`; emit `PayoutClaimed` |

A second `claim_payout`, `submit_day` for the same day, `leave_cohort` after the cutoff, or `settle_finalize` on a settled cohort each fail with the errors below.

## 4. Events

`FlagsSet` · `ChipSetPublished { version, amounts }` · `FaucetClaimed { recipient, skr, lamports }` · `CohortOpened { league, index, start, chips }` · `MemberJoined { cohort, wallet, tier, stake }` · `MemberLeft { cohort, wallet, stake }` · `DayCompleted { cohort, wallet, day_index, proof_hash }` · `ForfeitRecorded { cohort, wallet, days_completed, fee }` · `MemberCounted { cohort, wallet, finished }` · `CohortSettled { cohort, finishers, total_finisher_stake, share_per_tier, rounding_remainder }` · `CarryOverSent { from, to, amount, fee }` · `CarryOverHeld { cohort, amount, fee }` · `CarryOverDelivered { from, to, amount }` **[Phase 2]** · `PayoutClaimed { cohort, wallet, amount }`

## 5. Errors

| Code | Name | Raised when |
|---|---|---|
| 6000 | `NotAdmin` / 6001 `NotOperator` / 6002 `NotSponsorOrRecipient` | Wrong privileged signer |
| 6010 | `InvalidLeagueTiming` | Devnet league with non-default timing, or test league outside a `test-leagues` build |
| 6011 | `InvalidChipSet` | Amounts not strictly increasing and positive |
| 6020 | `CohortNotDue` | `open_cohort` with wrong index or before the previous cohort started |
| 6021 | `JoiningClosed` | Join or leave at or after the join cutoff (ES-12) |
| 6022 | `InvalidTier` | `tier > 3` |
| 6023 | `LadderGateClosed` | 21-day join without qualification while the gate flag is on |
| 6024 | `GenesisGateClosed` | Genesis check failed while the flag is on |
| 6025 | `LobbyPlaceTaken` | A pending membership already exists (FR-006a) |
| 6026 | `ObligationsOverlap` | New cohort starts before the running cohort ends (FR-006b) |
| 6030 | `CohortNotRunning` | Submit outside `Running` |
| 6031 | `WrongDay` | `day_index` not today or not `days_completed` |
| 6032 | `DeadlinePassed` | Submit after `deadline_of(day_index)` (INV-8) |
| 6033 | `DayAlreadyCompleted` | Second submit for the same day (FR-025) |
| 6040 | `NotSettling` / 6041 `AlreadySettled` | Settlement steps at the wrong time |
| 6042 | `CountIncomplete` | `settle_finalize` before every member is counted |
| 6043 | `NotClaimable` / 6044 `AlreadyClaimed` | Claim by a non-finisher or twice |
| 6050 | `FaucetAlreadyClaimedToday` | ES-0a |
| 6051 | `FaucetCapReached` / 6052 `FaucetEmpty` | ES-0b |
| 6053 | `ExerciseNotSupported` | `create_league` with an `exercise` other than `0` — the reserved values are layout for later exercises, not behaviour in this build (H30) |
| 6060 | `AccountMismatch` | A page pair or target account doesn't match the cohort, wallet or league |

## 6. PDA seeds

| Account | Seeds |
|---|---|
| `Config` | `"config"` |
| `LeagueConfig` | `"league"`, `league_id` |
| `Cohort` | `"cohort"`, `league_id`, `index` (u32 little-endian) |
| Cohort vault | `"vault"`, cohort |
| `Membership` | `"member"`, cohort, wallet |
| `Profile` | `"profile"`, wallet |
| Faucet vault | `"faucet_vault"` |
| Mint authority | `"mint_authority"` |

## 7. Golden fixtures (cross-language contract)

`fixtures/` is generated by the TypeScript test suite from the real program and committed:

| File | Contents | Consumed by |
|---|---|---|
| `accounts.json` | Serialized bytes + decoded fields for each account type at representative states | Kotlin `core` decoders |
| `pdas.json` | Seed inputs → addresses | Kotlin `core` PDA helpers |
| `instructions.json` | Args → instruction data bytes and account order | Kotlin `core` instruction builders |
| `derived-figures.json` | Cohort + memberships + chain time → status, breach, survivor counter, projected pool and share, roster order, time left | Kotlin `core` `Derive.kt` |
| `settlement-vectors.json` | PRD §19 V1–V22 inputs and expected outputs | Rust math tests, TypeScript integration tests, Kotlin projections |

A Kotlin test that disagrees with a fixture fails the build.
