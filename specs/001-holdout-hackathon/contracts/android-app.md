# Contract — Android app (Kotlin)

**Phase 1 output of `/speckit-plan`.** What the native app (`kotlin/app`) promises to the member, the chain and the constitution. What each screen shows and says is fixed by `spec.md`; the screens as built are described in `docs/holdout_screen_map.md` and their look in `docs/design/`. A `PRD §n` citation below names the archived hackathon PRD as a screen's origin only (spec header). This contract fixes the boundaries: permissions, network, storage, wallet use, and which screen shows each state.

## 1. Platform and permissions

| Item | Contract |
|---|---|
| Android SDK levels | min SDK 26, compile/target SDK 37 (Solana Mobile CLI Kotlin Compose Minimal template); primary device Solana Seeker |
| `CAMERA` | Requested before the first sitting, with the reason stated |
| `RECORD_AUDIO` | **Never declared** in the manifest (FR-022a) — a build check fails if it appears |
| `INTERNET` | Declared; used only as in §2 |
| Merged from the MWA client library | `ACCESS_NETWORK_STATE`, `REORDER_TASKS` and the generated `holdout.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — all normal, none requested at runtime; read off the built APK with `aapt2 dump badging` |
| `POST_NOTIFICATIONS` | Runtime on Android 13+: asked **once** — at the first join, or the first time the app finds the member already in a Holdout — with the reason on the stake sheet (FR-068). Declining disables reminders and nothing else |
| Other dangerous permissions | None. No `SCHEDULE_EXACT_ALARM` and no `RECEIVE_BOOT_COMPLETED` — reminders are inexact and come back when the app next reads the chain (R21) |

## 2. Network egress

| Destination | Purpose |
|---|---|
| The configured devnet RPC endpoint | Account reads, Clock sysvar, sending signed transactions |
| The configured **names** endpoint (Solana mainnet, `holdout.namesRpcUrl`; spec 003) | Seeker ID lookups for display only (001 FR-063): `getProgramAccounts` and `getMultipleAccounts` on public name records. **And the Seeker Genesis Token check before a join** (FR-003b, R17): `getTokenAccountsByOwner` (Token-2022, `jsonParsed`) for the connected wallet, then `getMultipleAccounts` (`jsonParsed`) on those mints. Nothing else. Sends wallet addresses already public on chain; never a signature, a transaction or device data. Read-only clients that cannot send (003 research R5; `SeekerGenesisReader`) |
| `https://store.solanamobile.com/`, opened in the browser by the member's tap | The Seeker store, linked from the Seeker-exclusive notice (FR-003c). The app makes no request to it itself |
| Local MWA wallet app (Android intents / local websocket) | Authorisation and signing |

Nothing else: the names endpoint is the app's only contact with mainnet, and it only reads. Reminders (FR-068) are scheduled and shown on the device and use no network. Only **Holdout Lab**, the separate tester app (`holdout.app.lab`, spec 004), may save a set's camera as video in its private storage for rep-recognition testing (constitution X v2.1.0, spec 002 FR-026); it never sends it anywhere, and has no network permission at all. The member app contains no recorder in any build. **Camera frames, landmarks and rep traces are never sent anywhere** (FR-022, constitution X); the only rep-derived data leaving the device is the 32-byte `proof_hash` inside `submit_day`. Acceptance criterion 10 is checked by capturing all outbound traffic during a sitting and finding only RPC calls.

## 3. Wallet use (Mobile Wallet Adapter)

Client: `com.solanamobile:mobile-wallet-adapter-clientlib-ktx` 2.2.0, used as in the template — one `MobileWalletAdapter` with a `ConnectionIdentity` (identity URI on our domain with a Digital Asset Links file, icon, name "Holdout") and `blockchain = Solana.Devnet`, and an `ActivityResultSender` created in the activity.

| Action | Client call | Notes |
|---|---|---|
| Connect | `walletAdapter.connect(sender)` | First screen; nothing else is usable before it (FR-001a) |
| No wallet installed | `TransactionResult.NoWalletFound` from any call | Shows the no-wallet screen with how to get a wallet (ES-0) |
| Join, leave, submit completed day, claim | `walletAdapter.transact(sender) { signAndSendTransactions(...) }` | The member's wallet is the fee payer; `Failure` results show a retryable error |
| Faucet | **none** | Built with `web3-solana`, signed with the bundled sponsor key as fee payer and sent with `rpc-core`; recipient = connected address; no member signature (R13). If the sponsor's float has been emptied and the connected wallet already holds SOL, the same claim is re-sent through `transact` with the member as fee payer — `claim_faucet` accepts either (FR-042b) |
| Reconnect | Stored auth token reused on resume; `connect` again if it was revoked | — |
| Network | Authorized on `solana:devnet` only: the chains the wallet reports for its accounts are checked at `connect` and inside every `transact` before a signature is requested; any other network is refused and the saved token dropped (FR-001b) | A wallet reporting no chains (MWA 1.x) is allowed |
| Disconnect | `walletAdapter.disconnect(sender)` | Returns to the connect screen |

## 3a. Appearance

| Item | Contract |
|---|---|
| Theme | **A single dark theme** in `ui/theme/`, its tokens the design book's (`docs/design/tokens.json`; direction and the hand-run check in research R20) — black canvas, Solana-palette accents. No light variant |
| Dynamic colour | **Disabled.** Material You never recolours the app; brand colours are fixed |
| Fonts | System Roboto / Roboto Flex for UI, JetBrains Mono (open licence; **to be bundled in `res/font/`**, T035 — until then amounts and countdowns fall back to the system monospace face) for amounts and countdowns. **No licensed brand font ships in the APK** (`docs/design/README.md`, Type) |
| Camera screens | Designed for black: full-bleed preview, with a scrim behind any text |

## 4. Device storage

MMKV only, as specified in `data-model.md` §4. Keys are scoped per wallet; switching wallets shows that wallet's own streak and progress. Nothing in storage is trusted by the contract.

## 5. Every state has a screen (constitution IX)

| State (spec §8, data-model) | Screen / variant (PRD §10.1) |
|---|---|
| No wallet | Connect wallet — no-wallet variant (ES-0): what a wallet is and how to get one; nothing else is reachable before a wallet (FR-001a, amended 2026-10-09) |
| Wallet without test SKR or fee SOL | Faucet sheet; used-today and can't-pay-out variants (ES-0a, ES-0b) |
| Wallet just connected, first run | Tour — three cards, skippable, re-openable (FR-067) |
| Running Holdouts the member is not in | Home — **Running now** list (FR-062a): read-only, every connected wallet with or without a Seeker; each row opens that Holdout's cohort detail and roster; no join |
| No Holdout running, Daily 25 available | Home — Daily 25 variant, with the Holdouts open to join and what is at stake in each (FR-053) |
| Cohort `Open`, member not joined | Home or Forfeit offer → Stake confirm (no ladder screen since 2026-10-09; Forfeit's "next Holdout" — the same league's (US-4) — opens the stake confirm directly). Before the stake confirm opens, the Seeker check (FR-003b, R17): a wallet remembered or found to hold a Seeker Genesis Token goes on; the two variants below otherwise |
| Joining, wallet holds no Seeker Genesis Token (checked: None) | **Seeker-exclusive sheet** (FR-003c, ES-0c): *"Holdouts are for Seeker owners"* · *"Staking in a Holdout needs a Seeker. You can still do the Daily 25 and watch every Holdout."* · **Get a Seeker** (opens `https://store.solanamobile.com/`) · **Not now**. No join is offered and nothing is signed |
| Joining, the check in progress | **Seeker sheet — checking** variant: *Checking your Seeker…* with the loader, until mainnet answers (FR-003b, R17). Every first join of a wallet not yet confirmed on this phone passes through it |
| Joining, the check could not be made (Unknown) | **Seeker sheet — couldn't-check** variant (FR-003d, ES-0d): *"We couldn't check your Seeker — try again."* · **Try again**. Never the Seeker-exclusive wording; nothing is signed |
| Membership `Pending` in `Open` cohort | Lobby (with leave) |
| Membership `Pending` in `Locked` cohort | Lobby — locked variant (ES-12a) |
| Membership `Active`, today not complete | Home — cohort variant; Sitting |
| Sitting `Preflight` / `Countdown` / `Counting` | Sitting sub-screens; the hold's reason when it will not fill (002 FR-018a, `contracts/words.md`); last-10-minutes warning (FR-047); offline warning (FR-024b) |
| Sitting ended part-way | Sitting — stopped part-way variant |
| Day `Completed`, pending submission | Home — "Done — tap to submit" (Phase 1); "Done — waiting for network" while the chain cannot be reached (Phase 2). Day-complete screen while the chain cannot be reached — "Done — not sent yet" with the time left to that day's own deadline, *Back to today*, no submit; past that deadline "Done — too late to send" |
| Day `Submitted` | Home — cohort variant, day counter advanced |
| Membership in breach (recorded or not) | Forfeit screen (US-4); roster shows member as out (FR-038a) |
| Cohort `Settling`, member finished | Cohort detail — "You finished · settlement in progress" |
| `ReadyToClaim` | Claim screen |
| `Claimed` | Cohort detail — claimed |
| Any running cohort | Roster (FR-062) |
| **[Phase 2]** League leaderboard | Leaderboards screen (US-10) |
| About Holdout | The test network, one line on scripted members, the tour again, and Disconnect wallet. For every member; nothing in it is addressed to judges, and the known weaknesses are in the README only (spec 004, amended 2026-10-09). There is no walkthrough in the app: the full cycle is in the demo video, sent as a link (FR-044) |

`Withdrawn` returns the member home with a refund confirmation; the cohort-level `Settled` state is shown in cohort detail.

## 6. Figures shown

Survivor counter, projected pool, projected share, roster order and time left come **only** from `kotlin/core` `Derive.kt` over chain accounts and chain time (`data-model.md` §5). Every amount a member stakes or receives is shown in SKR; USD tier labels carry `≈` (FR-059). No screen shows or ranks money on a roster or leaderboard (FR-063, constitution XIII).
