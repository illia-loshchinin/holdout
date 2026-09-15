# Contract — Android app (Kotlin)

**Phase 1 output of `/speckit-plan`.** What the native app (`kotlin/app`) promises to the member, the chain and the constitution. Screen designs and copy: `docs/holdout_v1_prd.md` §10–§13. This contract fixes the boundaries: permissions, network, storage, wallet use, and which screen shows each state.

## 1. Platform and permissions

| Item | Contract |
|---|---|
| Minimum Android | API 29 (Android 10); primary device Solana Seeker |
| `CAMERA` | Requested before the first sitting, with the reason stated |
| `RECORD_AUDIO` | **Never declared** in the manifest (FR-022a) — a build check fails if it appears |
| `INTERNET` | Declared; used only as in §2 |
| Other dangerous permissions | None |

## 2. Network egress

| Destination | Purpose |
|---|---|
| The configured devnet RPC endpoint | Account reads, Clock sysvar, sending signed transactions |
| Local MWA wallet app (Android intents / local websocket) | Authorisation and signing |

Nothing else. **Camera frames, landmarks and rep traces are never sent anywhere** (FR-022, constitution X); the only rep-derived data leaving the device is the 32-byte `proof_hash` inside `submit_day`. Acceptance criterion 10 is checked by capturing all outbound traffic during a sitting and finding only RPC calls.

## 3. Wallet use (Mobile Wallet Adapter)

| Action | MWA call | Notes |
|---|---|---|
| Connect | `authorize` (cluster: devnet) | First screen; nothing else is usable before it (FR-001a) |
| Join, leave, submit completed day, claim | `signAndSendTransactions` | The member's wallet is the fee payer |
| Faucet | **none** | Built and sent by the app with the bundled sponsor key as fee payer; recipient = connected address; no member signature (R13) |
| Reconnect | `reauthorize` with the stored auth token | On resume |

## 4. Device storage

MMKV only, as specified in `data-model.md` §4. Keys are scoped per wallet; switching wallets shows that wallet's own streak and progress. Nothing in storage is trusted by the contract.

## 5. Every state has a screen (constitution IX)

| State (spec §8, data-model) | Screen / variant (PRD §10.1) |
|---|---|
| No wallet | Connect wallet — no-wallet variant (ES-0) |
| Wallet without test SKR or fee SOL | Faucet sheet; used-today and can't-pay-out variants (ES-0a, ES-0b) |
| No cohort, daily challenge available | Home — challenge variant (FR-053) |
| Cohort `Open`, member not joined | Ladder → Stake confirm |
| Membership `Pending` in `Open` cohort | Lobby (with leave) |
| Membership `Pending` in `Locked` cohort | Lobby — locked variant (ES-12a) |
| Membership `Active`, today not complete | Home — cohort variant; Sitting |
| Sitting `Preflight` / `Liveness` / `Countdown` / `Counting` | Sitting sub-screens; palm-timeout variant (FR-020); last-10-minutes warning (FR-047); offline warning (FR-024b) |
| Sitting ended part-way | Sitting — stopped part-way variant |
| Day `Completed`, pending submission | Home — "Done — tap to submit" (Phase 1) |
| Day `Submitted` | Home — cohort variant, day counter advanced |
| Membership in breach (recorded or not) | Forfeit screen (US-4); roster shows member as out (FR-038a) |
| Cohort `Settling`, member finished | Cohort detail — "You finished · settlement in progress" |
| `ReadyToClaim` | Claim screen |
| `Claimed` | Cohort detail — claimed |
| Any running cohort | Roster (FR-062) |
| **[Phase 2]** League leaderboard | Leaderboards screen (US-10) |
| Walkthrough | Walkthrough player, or "recording in progress" (R18) |

`Withdrawn` returns the member to the ladder with a refund confirmation; the cohort-level `Settled` state is shown in cohort detail.

## 6. Figures shown

Survivor counter, projected pool, projected share, roster order and time left come **only** from `kotlin/core` `Derive.kt` over chain accounts and chain time (`data-model.md` §5). Every amount a member stakes or receives is shown in SKR; USD tier labels carry `≈` (FR-059). No screen shows or ranks money on a roster or leaderboard (FR-063, constitution XIII).
