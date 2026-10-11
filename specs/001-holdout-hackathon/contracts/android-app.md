# Contract — Android app (Kotlin)

**Phase 1 output of `/speckit-plan`.** What the native app (`kotlin/app`) promises to the member, the chain and the constitution. What each screen shows and says is fixed by `spec.md`; the screens as built are described in `docs/holdout_screen_map.md` and their look in `docs/design/`. A `PRD §n` citation below names the archived hackathon PRD as a screen's origin only (spec header). This contract fixes the boundaries: permissions, network, storage, wallet use, and which screen shows each state.

## 1. Platform and permissions

| Item | Contract |
|---|---|
| Android SDK levels | min SDK 26, compile/target SDK 37 (Solana Mobile CLI Kotlin Compose Minimal template); primary device Solana Seeker. The JVM Compose tests run under Robolectric pinned to SDK 36 (research R23); that pin changes nothing shipped |
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

Nothing else: the names endpoint is the app's only contact with mainnet, and it only reads. Reminders (FR-068), the finished notice among them (FR-068b), are scheduled and shown on the device and use no network: the finished notice does not read the chain when it fires. Only **Holdout Lab**, the separate tester app (`holdout.app.lab`, spec 004), may save a set's camera as video in its private storage for rep-recognition testing (constitution X v2.1.0, spec 002 FR-026); it never sends it anywhere, and has no network permission at all. The member app contains no recorder in any build. **Camera frames, landmarks and rep traces are never sent anywhere** (FR-022, constitution X); the only rep-derived data leaving the device is the 32-byte `proof_hash` inside `submit_day`. Acceptance criterion 10 is checked by capturing all outbound traffic during a sitting and finding only RPC calls.

## 3. Wallet use (Mobile Wallet Adapter)

Client: `com.solanamobile:mobile-wallet-adapter-clientlib-ktx` 2.2.0, used as in the template — one `MobileWalletAdapter` with a `ConnectionIdentity` (identity URI on our domain with a Digital Asset Links file, icon, name "Holdout") and `blockchain = Solana.Devnet`, and an `ActivityResultSender` created in the activity.

| Action | Client call | Notes |
|---|---|---|
| Connect | `walletAdapter.connect(sender)` | First screen; nothing else is usable before it (FR-001a) |
| No wallet installed | `TransactionResult.NoWalletFound` from any call | Shows the no-wallet screen with how to get a wallet (ES-0) |
| Join, leave, submit completed day, claim | `walletAdapter.transact(sender) { signAndSendTransactions(...) }` | The member's wallet is the fee payer; `Failure` results show a retryable error. While the call is pending the app shows its own signing flow (§5a, FR-070); the session cannot be aborted from the app, so that screen has **no Cancel** |
| Faucet | **none** | Built with `web3-solana`, signed with the bundled sponsor key as fee payer and sent with `rpc-core`; recipient = connected address; no member signature (R13). If the sponsor's float has been emptied and the connected wallet already holds SOL, the same claim is re-sent through `transact` with the member as fee payer — `claim_faucet` accepts either (FR-042b) |
| Reconnect | Stored auth token reused on resume; `connect` again if it was revoked | — |
| Network | Authorized on `solana:devnet` only: the chains the wallet reports for its accounts are checked at `connect` and inside every `transact` before a signature is requested; any other network is refused and the saved token dropped (FR-001b) | A wallet reporting no chains (MWA 1.x) is allowed |
| Disconnect | `walletAdapter.disconnect(sender)` | Returns to the connect screen |

## 3a. Appearance

| Item | Contract |
|---|---|
| Theme | **A single dark theme** in `ui/theme/`, its tokens the design book's (`docs/design/tokens.json`; direction and the hand-run check in research R20) — black canvas, Solana-palette accents. No light variant |
| Dynamic colour | **Disabled.** Material You never recolours the app; brand colours are fixed |
| Fonts | System Roboto for UI; Roboto Flex is neither a system font nor bundled (**to be bundled in `res/font/`**, T035 — until then the design book's `counter` and `display` styles fall back to the theme's default face); JetBrains Mono (open licence; **to be bundled in `res/font/`**, T035 — until then amounts and countdowns fall back to the system monospace face) for amounts and countdowns. **No licensed brand font ships in the APK** (`docs/design/README.md`, Type) |
| Camera screens | Designed for black: full-bleed preview, with a scrim behind any text |

## 4. Device storage

MMKV only, as specified in `data-model.md` §4 (since 2026-10-11 it also holds `looked:{wallet}:{cohort}`, the Standing badge's marker, and `sitting:{wallet}:{scope}`, the sitting-in-progress marker for a crash). Keys are scoped per wallet; switching wallets shows that wallet's own streak and progress. Nothing in storage is trusted by the contract.

## 5. Every state has a screen (constitution IX)

Since 2026-10-11 "Home" in this table means **Today**, the first of the four tabs (FR-069); §5a says which tab or full-screen route each screen belongs to.

| State (spec §8, data-model) | Screen / variant (PRD §10.1) |
|---|---|
| No wallet | Connect wallet — no-wallet variant (ES-0): what a wallet is and how to get one; nothing else is reachable before a wallet (FR-001a, amended 2026-10-09) |
| Wallet without test SKR or fee SOL | Faucet sheet; used-today and can't-pay-out variants (ES-0a, ES-0b) |
| Wallet just connected, first run | Tour — **four cards** (the Daily 25 · what a Holdout is · how reps are counted · the scripted-member disclosure), "how it works · N of 4"; Skip on cards 1–3 leads to card 4; re-openable from About (FR-067, amended 2026-10-11) |
| Running Holdouts the member is not in | Standing — empty state, **Running now** list (FR-062a, FR-069b): read-only, every connected wallet with no Holdout of its own, with or without a Seeker; each row opens that Holdout's cohort detail and roster; no join; below it "Join one to have a crowd" (→ Holdouts) |
| No Holdout running, Daily 25 available | Today — T1 (Daily 25 variant), with the Holdouts open to join under it: level · length, joined, at stake, "joining closes in …", "Prize boost: up to N SKR" (FR-053); "The next Holdout opens soon." when none is open (ES-15) |
| Cohort `Open`, member not joined | Home or Forfeit offer → Stake confirm (no ladder screen since 2026-10-09; Forfeit's "next Holdout" — the same league's (US-4) — opens the stake confirm directly). Before the stake confirm opens, the Seeker check (FR-003b, R17): a wallet remembered or found to hold a Seeker Genesis Token goes on; the two variants below otherwise |
| Joining, wallet holds no Seeker Genesis Token (checked: None) | **Seeker-exclusive sheet** (FR-003c, ES-0c): *"Holdouts are for Seeker owners"* · *"Staking in a Holdout needs a Seeker. You can still do the Daily 25 and watch every Holdout."* · **Get a Seeker** (opens `https://store.solanamobile.com/`) · **Not now**. No join is offered and nothing is signed |
| Joining, the check in progress | **Seeker sheet — checking** variant: *Checking your Seeker…* with the loader, until mainnet answers (FR-003b, R17). Every first join of a wallet not yet confirmed on this phone passes through it |
| Joining, the check could not be made (Unknown) | **Seeker sheet — couldn't-check** variant (FR-003d, ES-0d): *"We couldn't check your Seeker — try again."* · **Try again**. Never the Seeker-exclusive wording; nothing is signed |
| Membership `Pending` in `Open` cohort | Lobby (with leave) |
| Membership `Pending` in `Locked` cohort | Lobby — locked variant (ES-12a) |
| Membership `Active`, today not complete | Today — T2, the Holdout's day only (no Daily 25 card, no offers; at most one line each for a claim ready, a lobby closing and a comeback, FR-069a); Sitting (full screen) |
| Sitting `Preflight` / `Countdown` / `Counting` | Sitting sub-screens; the hold's reason when it will not fill (002 FR-018a, `contracts/words.md`); last-10-minutes warning (FR-047); offline warning (FR-024b) |
| Sitting ended part-way | Sitting — stopped part-way variant |
| Day `Completed`, pending submission | Home — "Done — tap to submit" (Phase 1); "Done — waiting for network" while the chain cannot be reached (Phase 2). Day-complete screen while the chain cannot be reached — "Done — not sent yet" with the time left to that day's own deadline, *Back to today*, no submit; past that deadline "Done — too late to send" |
| Day `Submitted` | Home — cohort variant, day counter advanced |
| Membership in breach (recorded or not) | Forfeit screen (US-4); roster shows member as out (FR-038a) |
| A comeback round open to join, no place held *(added 2026-10-11, FR-037g)* | Forfeit screen — **offer card**: up to N SKR back, the next round's level and length, start and time left to join, **Join** → Stake confirm on the lost chip (with the per-chip "up to" line), and the round after it; Today — the comeback nudge line, shown at once, before the Daily 25 is done (FR-037g). Joining before the forfeit is recorded sends the record with the join (ES-17) |
| A place held in a comeback round | Forfeit screen and Today — "finish it and up to N SKR of the stake you lost comes back", no Join (ES-18) |
| No comeback round open, the next scheduled and not opened yet *(added 2026-10-11)* | Forfeit screen and Today — "The next … opens for joining in … and starts in …" ("opens for joining shortly" once automation is late), no Join (FR-037g) |
| A lobby place held outside the comeback window | Forfeit screen — the offer, saying that place must be left first; no Join until it is (ES-18, FR-006a) |
| An older forfeit's screen, after a later forfeit *(added 2026-10-11)* | Forfeit screen without the offer: the Daily 25 and, if one is open, the next Holdout of the same level and length; the offer belongs to the latest forfeit (FR-037g, ES-19) |
| Several comeback offers (7-day and 21-day) *(added 2026-10-11)* | Today shows one line: a round to join now first, then a place held, then a round not opened yet (FR-037g) |
| Every comeback round closed, no place held | Forfeit screen without the offer: the Daily 25 and, if one is open, the next Holdout of the same level and length (US-4) |
| Cohort `Settling`, member finished | Cohort detail — *"You finished · settling"* · *"Automation settles it on its next pass, usually within 15 minutes of the end. Ended 4m ago."*; past those 15 minutes *"Taking longer than usual. It settles on the next automation pass; nothing you're owed can change."* Home/H0 card — *"You finished · settling, usually within 11m"*, then *"You finished · settling any moment"* (FR-035a, amended 2026-10-11). The **finished notice** (FR-068b): one notification 15 minutes after the end, *"You finished your N-day Holdout"* · *"Settlement usually comes within 15 minutes of the end, so your claim is probably ready: open Holdout to claim it."* (+ *"It should include a comeback refund too."*), no amount; cancelled once the app has seen the cohort settled |
| `ReadyToClaim` | Claim screen |
| `Claimed` | Cohort detail — claimed, with the payout's breakdown: stake back, share of the prize pool and, when owed, the Comeback row (FR-037g, UC-10) |
| Any running cohort | Roster (FR-062) |
| **[Phase 2]** League leaderboard | Leaderboards screen (US-10) |
| A Holdout of the member's own, running / in the lobby / finished / settled, and the offers to join | Holdouts tab — **My Holdouts** (H0, FR-069c): *Yours*, *Finished* and *Open to join*; a forfeit stays under *Yours* until its comeback window has closed |
| Connected wallet, its balances, streak and tokens | Me (W1, FR-069d): Seeker ID or short address with copy, SKR and SOL, **Get test tokens** (faucet sheet), the daily streak with freezes, About Holdout, Disconnect wallet |
| Wallet approval pending, then confirming, then a result *(added 2026-10-11)* | Signing flow (§5a, FR-070, FR-070a): *Waiting for your wallet…* → confirming checklist → success / Declined / Failed / Joining closed / Deadline passed / Not enough SOL |
| Join confirmed *(added 2026-10-11)* | *You're in* (FR-070b): level · length, the stake held, a countdown to the start, *Do today's Daily 25* and *See the lobby* |
| About Holdout | The test network, one line on scripted members, the tour again ("How Holdout works · 4 cards"), and Disconnect wallet. For every member; nothing in it is addressed to judges, and the known weaknesses are in the README only (spec 004, amended 2026-10-09). There is no walkthrough in the app: the full cycle is in the demo video, sent as a link (FR-044) |

`Withdrawn` returns the member to Holdouts with a refund confirmation; the cohort-level `Settled` state is shown in cohort detail.

## 5a. Navigation, sheets and the signing flow *(added 2026-10-11, evening; FR-069…FR-070c)*

**One pure navigation model.** What is on screen is a value, `NavState`, changed only by a pure function of `(NavState, NavEvent)` in plain Kotlin with no Android types, so every transition is a JVM unit test (research R23). `HoldoutApp` renders the state and sends events (tab selected, route opened, Back, sheet opened or dismissed, signing step reached); it holds no navigation logic of its own.

| Part | Contract |
|---|---|
| Tabs | Four: **Today · Standing · Holdouts · Me**, in that order. The app opens on Today, also when a reminder opens it. The selected tab survives rotation and process recreation of the activity (not a cold start) |
| Roots | Today = T1 (no running Holdout) or T2 (a running Holdout the member is standing in). Standing = the cohort detail (H4) of the running Holdout, else of the last one the member ran, else the empty state (FR-069b). Holdouts = H0 *My Holdouts*. Me = W1 |
| Bar | Shown on a tab's root only. Hidden on: the sitting (all its sub-screens), connect, no-wallet, the tour, the signing flow, *You're in*, and any screen pushed over a root (lobby, cohort detail from another tab, roster, leaderboards, forfeit, claim, About) |
| Back stack | One stack per tab, kept while the member switches tabs. Back pops the current tab's stack; at the root of Standing, Holdouts or Me it goes to Today; at Today it leaves the app. Roster, Leaderboards, Detail, Forfeit and Claim return to the screen that opened them, not to Today (screen map C11). Selecting the selected tab pops it to its root |
| Sheets | Overlay state on top of the opener, not a route: **stake confirm** (with the Seeker gate's checking / not-a-Seeker / couldn't-check variants as states of the same sheet) and **faucet**. Dismissing (drag down, Back, scrim) returns to the opener unchanged. Short of SKR or SOL, the stake sheet's state moves to the faucet sheet and back to the stake sheet when the faucet is done or dismissed (ES-0a) |
| Signing flow | Full screen, no bar, for join, submit day and claim; leave and the faucet show the same states inline. States: `Waiting` ("Waiting for your wallet…", spinner, "Approve or decline in your wallet. Nothing moves until you approve.", no Cancel) → `Confirming` (checklist: Approved in your wallet · Sent to devnet · Confirming · result row) → `Result`. Results: `Success`; `Declined` ("Nothing was sent."); `Failed` ("The network didn't confirm it.", with "Check again in a minute" when the transaction may still land); flow-specific: `JoiningClosed` (join), `DeadlinePassed` (submit → Forfeit screen), `NotEnoughSol` (→ faucet sheet, then back to the flow's opener). The mapping from wallet and chain outcomes (`TransactionResult`, the confirmation read, the program's `DeadlinePassed` / join-cutoff errors, a short balance) to a result is a pure function, unit-tested |
| After a result | Join success → *You're in* (then Today or the lobby by its two actions); submit success → Today with the day on chain; claim success → the claim result (amount, *Done*, and after a Spark *Make it a Habit · 21-day*); any other result → its own screen, with Back to the opener |
| Full-screen exits | The sitting returns to the screen that started it; the Stopped screen (anything banked or discarded this sitting) returns to it on dismiss; stopping with nothing at all goes straight back |

**Device-local markers this adds** (data-model §4): `looked:{wallet}:{cohort}` — the Holdout's standing (still standing, settled) as it was when the member last had its detail on screen; Standing's tab shows its dot while the current derived standing differs from it (FR-069). `sitting:{wallet}:{scope}` — written when counting starts, cleared on every clean exit (stop, completion, leaving the foreground); if it is found set when the sitting's screen is next opened, the app crashed or was killed mid-sitting, and the Stopped screen's crash variant says what was kept (ES-1, ES-2: the banked blocks).

## 6. Figures shown

Survivor counter, projected pool, projected share, roster order and time left come **only** from `kotlin/core` `Derive.kt` over chain accounts and chain time (`data-model.md` §5). Every amount a member stakes or receives is shown in SKR; USD tier labels carry `≈` and are the only dollar figure shown, with "Dollar values are approximate." near them (FR-059, amended 2026-10-11). No screen shows or ranks money on a roster or leaderboard (FR-063, constitution XIII).
