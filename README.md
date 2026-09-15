# Holdout

Solana Mobile fitness commitment dApp. Built for the CLOCK IN hackathon, submission due 9 Oct 2026.

**Devnet only, mock SKR mint — a test network with a test token.** Nothing here touches mainnet or real value.

## What it is

A free daily challenge — 25 push-ups, verified on-device — with two staked leagues on top:

| Layer | |
|---|---|
| Daily challenge | Free, every day, for everyone. Builds a personal streak. Unlocks nothing, pays nothing, and a forfeit doesn't break it. |
| 7-day league | Staked. Entry rung and comeback lane. Never closes. |
| 21-day league | Staked. Where the money is. |

You need a Solana wallet to use the app — even the free challenge. The app has a built-in faucet for test SKR and devnet fee SOL, once a day.

Cohorts start every Monday 05:00 UTC. Joining and leaving close 1 hour before the start. Miss a day and the stake is forfeited into that cohort's prize pool; finish and take back the stake plus a share of what the quitters left. Leaderboards rank members by discipline — the share of their cohorts they survived — never by money.

## Known weaknesses

Named here on purpose (constitution XI). Each has its real fix beside it.

| What | Why it is like this | Real fix |
|---|---|---|
| Devnet with a mock SKR mint | Hackathon build; no real value at stake | Mainnet with real SKR |
| "Stake back" is true in SKR, not in dollars | Everything is SKR-denominated with no oracle in the contract | A known property of SKR-native staking, stated in the app |
| `ladder_gate_enabled = false` — both leagues open from first launch | Nobody should have to spend a week before reaching the 21-day league; the unlock is still computed and recorded | Flip the flag |
| `genesis_gate_enabled = false` | Testers have no Seeker | Flip the flag on mainnet |
| Non-default `day_seconds` for test cohorts | Lets a full cycle run in minutes during testing | Admin-guarded; real cohorts always use 24 hours |
| A completed day's proof is client-signed | No backend in the build | Server-side attestation signer with pinned traces |
| Reps banked during the day (in blocks of 10), and a completed day waiting for network, live only on the phone | No wallet prompt mid-workout; a lost phone loses that day's partial progress, and a rooted phone could edit it. A day finished offline counts only if it reaches the network before the deadline | Covered by the attestation fix; cross-device progress later |
| No audio anti-spoofing in rep verification | Microphone permission adds first-run friction and protects no real value on devnet | Reinstate the audio check and audio rep condition together |
| Stake chips ($10 / $25 / $50 / $100) are priced in SKR by the Operator from an off-chain rate, re-checked weekly | The contract must never read a price | On-chain chip governance before real value is at stake |
| Judging-period cohorts are populated by scripted members — real wallets under the same contract rules, whose forfeits fund real prizes and whose completed days are **not camera-verified** | Judging runs weeks after the last real cohort | Disclosed here and in the app; their addresses are published in the build and every scripted member is labelled wherever it appears |
| A wallet is required before anything, even the free challenge | One identity from the first tap | Judges without a wallet can see the product in the in-app walkthrough and this README |
| The full cycle is shown as a recorded walkthrough in the app | A judge can't wait 7 days | It is a recording of the real app against the real devnet contract |
| Notifications are sent manually | No notification infrastructure in the build | Push notification service |

## Layout

| Path | |
|---|---|
| `.specify/memory/constitution.md` | The principles every phase is checked against (v1.2.0). Read first. |
| `specs/001-holdout-hackathon/spec.md` | The feature spec — what and why, no stack. Clarified; ready for planning. |
| `docs/holdout_decisions.md` | Decision record H1–H22: why the build is what it is, what was rejected, what is still open. Read before changing the mechanic. |
| `docs/holdout_scope_and_state_machines.md` | Scope cut, decisions D-A…D-I, state machines and edge cases, synced to the spec. Read before coding. |
| `docs/holdout_v1_prd.md` | Product requirements for this build: screens, copy, verification rules, settlement math and test vectors. Adds detail to the spec, never scope. |
| `docs/archive/` | The full post-hackathon vision — the original PRD and its decision record — kept for the pitch. Not the build. |

When documents disagree: constitution → spec → decision record → scope doc → PRD.

## Workflow

`/speckit-plan` → `/speckit-tasks` → `/speckit-implement`.

Constitution and spec are written and clarified; amend them rather than regenerating them.
