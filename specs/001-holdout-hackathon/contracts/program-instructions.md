# Contract — `holdout` program instructions

**Phase 1 output of `/speckit-plan`.** The on-chain interface the Kotlin app, the Kotlin crank and the TypeScript tests all target. Account layouts: `data-model.md` §1. State machines: spec §8, scope doc §3. Every guard below is enforced by the program, never only by a client.

Conventions: `now` = Clock sysvar `unix_timestamp`. "Anyone" = any signer paying the fee; the caller's identity never changes the outcome (FR-030a). **[Phase 2]** marks items built only after Phase 1 is complete (spec §3a).

---

## 1. Privileged instructions

| Instruction | Signer | Args | Guards | Effects |
|---|---|---|---|---|
| `initialize` | the program's **upgrade authority** | `operator`, `sponsor`, `treasury_wallet`, `developer_wallet` (H35), `faucet_sol_top_up`, `faucet_daily_lamport_cap`, `faucet_sponsor_float` | `Config` does not exist; the signer **is the program's current upgrade authority**, read from the program's `ProgramData` account (passed as an account, seeds-derived), else `NotUpgradeAuthority` (6003). Before `Config` exists there is nothing else to authenticate a signer against, so without this guard any stranger could initialize the freshly deployed program first, keep `admin`, and set `operator`, `sponsor` and the fee destination to their own addresses — leaving no way to reclaim the program ID (R14) | Creates `Config` with `admin = signer` and `chip_set` zeroed, `version = 0`, `max_top_tier = 0`; creates the mock SKR mint with mint authority `["mint_authority"]`; creates `fee_destination` (the treasury's SKR token account — **operations**) and `developer_destination` (the developer's — H35); creates the faucet vault. Flags start `false` |
| `create_league` | admin | `league_id`, `duration_days`, `reps_per_day`, `exercise`, `form` (H35), `first_start`, `day_length`, `cadence`, `join_lock` | `exercise == 0` (push-ups); `1` and `2` are reserved for later exercises and rejected here (H30). `form` is 0 (from the toes, Hard) or 1 (knees allowed, Easy) — and fixed for the judged leagues: league 0 (7-day) only 1, league 1 (21-day) only 0; a test league takes either — else `InvalidForm` (6013). For `league_id < 100`: `day_length == 86,400`, `cadence == 604,800`, `join_lock == 3,600`, `first_start` is a Monday 05:00:00 UTC. `league_id ≥ 100` (test leagues, any timing) **only in builds with the `test-leagues` feature** — used on LiteSVM and on a separate **rehearsal** deployment with its own program ID, never on the judged program | Creates `LeagueConfig` with `next_index = 0`, the `form` and zero reserves, and the league's **reserve vault** `["reserve", league_id]` (SKR token account, authority the league PDA) |
| `set_flags` | admin | `ladder_gate_enabled`, `genesis_gate_enabled` | — | Updates flags; emits `FlagsSet` |
| `publish_chip_set` | operator | `amounts: [u64; 4]` | `0 < a0 < a1 < a2 < a3` | Replaces `Config.chip_set`, `version += 1`, `max_top_tier = max(max_top_tier, a3)`; emits `ChipSetPublished`. Never changes an existing cohort (FR-055). `max_top_tier` never decreases, and no cohort exists whose top tier exceeded it when the cohort was created (FR-055b), so the faucet always mints enough for every open cohort |

There is **no** instruction that lets admin, operator or sponsor move a held stake or a pool (constitution I).

## 2. Permissionless automation

| Instruction | Accounts (beyond signer, programs) | Guards | Effects |
|---|---|---|---|
| `open_cohort(league_id, index)` | `LeagueConfig`, new `Cohort`, new vault, previous `Cohort` (if `index > 0`), `Config` | `index == next_index`; if `index > 0`, `now ≥ previous.start`; `Config.chip_set.version > 0`, else `ChipSetNotPublished` (6012) — a cohort opened before the first chip set is published would copy four zeroed amounts and keep them for its whole life (FR-007, FR-055), accepting zero-stake joins that no later publish can correct | Creates `Cohort` with `start = first_start + index × cadence`, `join_cutoff = start − join_lock`, league parameters — `duration_days`, `reps_per_day`, `day_length`, `exercise` — and `Config.chip_set.amounts` copied; `next_index += 1`; emits `CohortOpened`. **[Phase 2]** also delivers any held carry-over of this league into the new cohort |
| `record_forfeits` | `Cohort`, vault, `fee_destination` (operations), `developer_destination`, `Config`; remaining accounts = up to 10 `(Membership, Profile)` pairs | Each pair: membership belongs to cohort, profile matches membership wallet | For each pair where `state == Joined ∧ in_breach(now)`: split the stake 70 / 5 / 5 / 10 / 10 (FR-027, H35; re-split 2026-10-09): `pool += pool part`, `boost_due += boost part`, `comeback_due += comeback part` (both stay in the vault), and page-batched transfers of the developer parts → developer destination and the operations parts → fee destination; `held_stake −= stake`; `state = Forfeited`; `forfeits_recorded += 1`; profile: `forfeited += 1`, `last_changed = max(last_changed, deadline_of(days_completed))` — **the deadline they missed, never `now`**: the instant is derived from the outcome, so neither the page order nor how late the crank runs can reorder two tied members on a leaderboard (FR-060b), and the `max` keeps a late-recorded older outcome from moving it backwards — clear `running` if it is this cohort — **the league streak is not touched here**, because a mid-cohort forfeit can be recorded before an earlier cohort of the same league has been counted (FR-061a); emit `ForfeitRecorded`. Pairs not in breach, or already forfeited, are skipped without error (idempotent, any order) |
| `settle_count` | `Cohort`, vault, `fee_destination`, `developer_destination`, `Config`, `LeagueConfig`, the league's cohort `index − 1` (only when `index > 0`); remaining accounts = up to 10 `(Membership, Profile)` pairs | **Each pair: `membership.cohort == cohort` and the profile is the one the membership's wallet derives, both checked before any mutation — a pair from another cohort fails the whole page with `AccountMismatch` (6060), exactly as `record_forfeits` requires. Without it a caller could count a foreign membership, mark it `counted`, and drive this cohort's `counted` to `member_count` without its own members ever being counted.** `status == Settling` (`now ≥ end ∧ !settled`); when `index > 0`, the previous cohort of this league is `settled` — counting runs in cohort order so every profile's streak does too (FR-061a, INV-9) | For each pair with `counted == false`: if `Joined ∧ in_breach` → forfeit exactly as `record_forfeits`; then if `Joined ∧ days_completed == duration_days` → `state = Finished`, `finisher_count += 1`, `finishers_per_tier[tier] += 1`, `total_finisher_stake += stake`, profile `cleared += 1`, `qualified = true` if 7-day league, clear `running`. **This is the only step that writes a streak:** `current_streak += 1` and `longest_streak = max` for a finisher, `current_streak = 0` for a membership counted in any forfeited state — whenever it was recorded — and `last_changed = max(last_changed, t)` either way, where `t` is the outcome's own instant: the cohort's `end` for a finisher (identical for everyone who cleared it, FR-031) and the missed `deadline_of(days_completed)` for a forfeit. Never `now`, so the crank's timing cannot rank one tied member above another (FR-060b). **These profile figures land page by page**, so a cohort mid-count has some of its members' figures written and not others; leaderboards therefore read a league only as of its last `settled` cohort (FR-060a), and FR-061a bounds this to one cohort per league at a time. Set `counted = true`, `cohort.counted += 1`; emit `MemberCounted`. Already-counted pairs are skipped **H35:** an unrecorded breach is split as in `record_forfeits`; counting a forfeit opens the member's comeback in that league (`comeback_stake`, the missed deadline, and until that deadline + 14 × `day_length`; only the latest counted forfeit); counting a finisher of a cohort that starts inside that window sets `membership.comeback_entitled = min(20% of the forfeited stake, the new stake)` (20% since 2026-10-09), adds it to `cohort.comeback_owed`, and uses the comeback up |
| `settle_finalize` | `Cohort`, vault, `fee_destination` (operations), `developer_destination`, `LeagueConfig` (writable), the league's reserve vault, the league's cohort `index − 1` (only when `index > 0`) | `status == Settling`; `counted == member_count`; for `index > 0`, the previous cohort is the league's `index − 1` (`AccountMismatch`, 6060) and settled (`PreviousCohortUnsettled`, 6045) — a cohort nobody joined is never counted, so this is its own ordering check. Finalization runs in cohort order within a league (`settle_count` and `settle_finalize` both refuse cohort *i* until *i − 1* is settled), so what a cohort draws from its league's reserve depends only on earlier cohorts, never on the crank's timing (FR-037c, INV-10) | **If `total_finisher_stake > 0`, in order:** (1) `boost = min(floor(20% × pool), league.boost_reserve)` reserve → vault, `pool += boost`, `boost_received = boost`; (2) `share_per_tier[t] = floor(pool × chips[t] ÷ T)`; `rounding_remainder` → fee destination; `pool −= rounding_remainder`; (3) `comeback_funded = min(comeback_owed, league.comeback_reserve)` reserve → vault. **If no finishers:** `floor(pool ÷ 2)` → developer destination, the rest → fee destination (half each since 2026-10-09), `no_finisher_fee = pool`, `pool = 0`. **Then always:** (4) `boost_due + comeback_due` vault → reserve, added to the league's two reserves, dues zeroed; `settled = true`; emit `CohortSettled { …, boost, comeback_funded }` (FR-037c–e, H35) |
| `top_up_sponsor` | `Config`, faucet vault, the `Config.sponsor` address (lamport recipient, does not sign) | `sponsor` lamports `< sponsor_float ÷ 2`; the refill fits `daily_lamport_cap − lamports_today` and the vault holds it | Transfers `sponsor_float − sponsor lamports` faucet vault → sponsor; adds it to `faucet.lamports_today`; emits `SponsorToppedUp { lamports }`. Anyone may call it, so a sponsor key drained by whoever extracted it from the app is restored by the next crank pass instead of ending zero-SOL onboarding (R13, FR-042b) |
| `deliver_carry_over` | *Cut 2026-10-05 (H34): nothing carries over, so there is nothing to deliver* | — | — |

## 3. Member instructions

| Instruction | Signer | Args | Guards | Effects |
|---|---|---|---|---|
| `claim_faucet` | fee payer ∈ {`Config.sponsor`, `recipient`} | `recipient: Pubkey` | `chip_set` published, else `ChipSetNotPublished` — an unpublished set mints nothing and would spend the recipient's one claim of the day on it (FR-055b); `Profile(recipient).last_faucet_day < today` where `today = floor((now − 18,000) ÷ 86,400)` (days turn over at 05:00 UTC); **every lamport the claim takes out of the vault** — the top-up, plus the sponsor's fee and any rent it paid — must fit `daily_lamport_cap − lamports_today` and be held by the vault (R13) | Creates `Profile` and the recipient's SKR token account if missing; mints `chip_set.max_top_tier` SKR to the recipient — the largest top tier ever published, not today's, so a wallet can always stake the top chip of **any** open cohort even when a Phase 2 re-price lowered the tiers after that cohort copied them (FR-042a, FR-057); tops recipient lamports up to `sol_top_up` from the faucet vault; if the payer is the sponsor, reimburses the sponsor's fee and any rent it paid from the vault; adds every lamport that left the vault to `faucet.lamports_today` (resetting it when `lamports_day != today`); `last_faucet_day = today`; emit `FaucetClaimed`. Errors (not partial success) when the budget or the vault is exhausted. Only a claim that moves **no** lamports at all — the recipient pays its own fee *and* already holds `sol_top_up` — spends none of the budget. A sponsor-paid claim always charges the budget, because the fee and rent reimbursement leave the vault even when no top-up is due; that is the whole reason the cap counts lamports rather than claims (R13) |
| `join_cohort` | member | `tier: u8` | `status == Open`; `tier ≤ 3`; member SKR balance ≥ `chips[tier]`; `ladder_gate_enabled ⇒ (league 0 ∨ qualified)`; `genesis_gate_enabled ⇒ genesis check` (R17); after slot normalisation: `pending` is empty, and if the running cohort has not ended then `cohort.start ≥ running_cohort.end()`. The member's **running cohort is passed as an account** whenever `profile.running` is set (else `AccountMismatch`), and the end is read from it rather than from `profile.running.end`, so the rule rests on chain state alone (constitution II) and cannot be skipped by a cached end that is stale or zero | Creates `Profile` if missing and `Membership` (member pays rent); transfers `chips[tier]` member → vault; `held_stake += stake`, `member_count += 1`; `profile.pending = {cohort, start, end}`; emit `MemberJoined` |
| `leave_cohort` | member | — | `status == Open`; membership `state == Joined` | Transfers `stake` vault → member; `held_stake −= stake`, `member_count −= 1`; clears `profile.pending`; **closes** `Membership` (rent to member); emit `MemberLeft` |
| `submit_day` | member | `day_index: u8`, `proof_hash: [u8; 32]` | `status == Running`; `state == Joined`; then, **in this order**, `day_index ≥ days_completed` (6033), `day_index == days_completed` (6031), `now ≤ deadline_of(day_index)` (6032), `day_index == day_index(now)` (6031) — see §5 | `days_completed += 1`; slot normalisation; emit `DayCompleted { cohort, wallet, day_index, proof_hash }` |
| `claim_payout` | member | — | `cohort.settled`; `state == Finished` | Transfers `stake + share_per_tier[tier] + refund` vault → member, where `refund = floor(comeback_entitled × comeback_funded ÷ comeback_owed)` (0 with no comeback, FR-037e); `held_stake −= stake`, `pool −= share`, `comeback_claimed += refund`; `state = Claimed`; emit `PayoutClaimed { cohort, wallet, amount, comeback }` |

A second `claim_payout`, `submit_day` for the same day, `leave_cohort` after the cutoff, or `settle_finalize` on a settled cohort each fail with the errors below.

## 4. Events

`FlagsSet` · `ChipSetPublished { version, amounts }` · `FaucetClaimed { recipient, skr, lamports }` · `SponsorToppedUp { lamports }` · `CohortOpened { league, index, start, chips }` · `MemberJoined { cohort, wallet, tier, stake }` · `MemberLeft { cohort, wallet, stake }` · `DayCompleted { cohort, wallet, day_index, proof_hash }` · `ForfeitRecorded { cohort, wallet, days_completed, fee }` · `MemberCounted { cohort, wallet, finished }` · `CohortSettled { cohort, finishers, total_finisher_stake, share_per_tier, rounding_remainder }` · `PayoutClaimed { cohort, wallet, amount }`

## 5. Errors

| Code | Name | Raised when |
|---|---|---|
| 6000 | `NotAdmin` / 6001 `NotOperator` / 6002 `NotSponsorOrRecipient` | Wrong privileged signer |
| 6003 | `NotUpgradeAuthority` | `initialize` signed by anyone but the program's current upgrade authority (R14) |
| 6010 | `InvalidLeagueTiming` | Devnet league with non-default timing or the wrong duration or rep count for its id (league 0 is 7 days, league 1 is 21, both at 25 reps, and no other real id is defined), a zero-day or zero-rep league of any kind, or a test league outside a `test-leagues` build |
| 6011 | `InvalidChipSet` | Amounts not strictly increasing and positive |
| 6012 | `ChipSetNotPublished` | `open_cohort` before the first `publish_chip_set` — `Config.chip_set.version == 0` (FR-055b) |
| 6013 | `InvalidForm` | `create_league` with a form other than 0 (from the toes) or 1 (knees allowed), or league 0 not Easy (1) or league 1 not Hard (0) — H35 |
| 6020 | `CohortNotDue` | `open_cohort` with wrong index or before the previous cohort started |
| 6021 | `JoiningClosed` | Join or leave at or after the join cutoff (ES-12) |
| 6022 | `InvalidTier` | `tier > 3` |
| 6023 | `LadderGateClosed` | 21-day join without qualification while the gate flag is on |
| 6024 | `GenesisGateClosed` | Genesis check failed while the flag is on |
| 6025 | `LobbyPlaceTaken` | A pending membership already exists (FR-006a) |
| 6026 | `ObligationsOverlap` | New cohort starts before the running cohort ends (FR-006b) |
| 6030 | `CohortNotRunning` | Submit outside `Running` |
| 6031 | `WrongDay` | `day_index` is not today, or is past `days_completed` — a day the member has never reached, so nothing about it has been completed |
| 6032 | `DeadlinePassed` | Submit after `deadline_of(day_index)` (INV-8) |
| 6033 | `DayAlreadyCompleted` | Second submit for a day already behind `days_completed` (FR-025). The three are ordered so each case gets its own code: a day below `days_completed` is 6033 whatever the clock says, one above it is 6031, and only for the member's own next day does the deadline speak — before the calendar does, or a missed day would answer 6031 and 6032 would be unreachable |
| 6040 | `NotSettling` / 6041 `AlreadySettled` | Settlement steps at the wrong time |
| 6042 | `CountIncomplete` | `settle_finalize` before every member is counted |
| 6043 | `NotClaimable` / 6044 `AlreadyClaimed` | Claim by a non-finisher or twice |
| 6045 | `PreviousCohortUnsettled` | `settle_count` or `settle_finalize` on cohort `i > 0` while the league's cohort `i − 1` is not settled (FR-061a, INV-10) |
| 6046 | *(retired 2026-10-05)* | Was `CarryOverTargetRequired`; there is no carry-over target any more. The number is not reused |
| 6047 | *(retired 2026-10-05)* | Was `CarryOverTargetNotOpen`; a no-finisher settlement no longer needs an open lobby. The number is not reused |
| 6050 | `FaucetAlreadyClaimedToday` | ES-0a |
| 6051 | `FaucetCapReached` / 6052 `FaucetEmpty` | The day's faucet lamport budget is spent, or the vault cannot cover what the instruction would move — ES-0b |
| 6053 | `ExerciseNotSupported` | `create_league` with an `exercise` other than `0` — the reserved values are layout for later exercises, not behaviour in this build (H30) |
| 6054 | `SponsorFloatSufficient` | `top_up_sponsor` while the sponsor still holds at least half its float |
| 6060 | `AccountMismatch` | A page pair, a previous cohort or a target account doesn't match the cohort, wallet or league |

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
