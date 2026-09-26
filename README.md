# Holdout

[![CI](https://github.com/illia-loshchinin/holdout/actions/workflows/ci.yml/badge.svg)](https://github.com/illia-loshchinin/holdout/actions/workflows/ci.yml)

Solana Mobile fitness commitment dApp. Built for the CLOCK IN hackathon, submission due 9 Oct 2026.

**Devnet only, mock SKR mint — a test network with a test token.** Nothing here touches mainnet or real value.

## What it is

**The Daily 25** — 25 push-ups, free, verified on the phone — with staked **Holdouts** on top:

| Layer | |
|---|---|
| The Daily 25 | Free, every day, for everyone. Builds a personal streak. Unlocks nothing, pays nothing, and a forfeit doesn't break it. |
| 7-day Holdout | Staked. Entry rung and comeback lane. Never closes. |
| 21-day Holdout | Staked. Where the money is. |

A Holdout is a cohort of members who start and finish together; "Holdout" is what members read, `Cohort` is what the contract stores (spec §1a).

You need a Solana wallet to use the app — even the Daily 25. The app has a built-in faucet for test SKR and devnet fee SOL, once a day.

Cohorts start every Monday 05:00 UTC. Joining and leaving close 1 hour before the start. Miss a day and the stake is forfeited into that cohort's prize pool; finish and take back the stake plus a share of what the quitters left. Leaderboards rank members by discipline — the share of their cohorts they survived — never by money.

The build lands in two phases: the core loop first, then — only if there's time before the 28 September freeze — league leaderboards, weekly chip re-pricing, carry-over delivery and automatic offline submission (spec §3a).

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
| Head-on, sagging hips are not detected; knee push-ups are counted in the Daily 25 by design | The Daily 25 accepts the phone head-on or at 45° and **counts push-ups from the knees on purpose** — a free habit for building strength. Staked Holdouts accept 45° only and refuse knee push-ups. Head-on the legs and hips point into the lens: on the phone, knee angle, knee and ankle height, hip height, torso angle and body line all read the same for knee push-ups as for honest ones (`fixtures/rep-traces/front-knees-01`), so knees could not be judged there anyway | Money is only ever staked at 45°. A lower-body classifier would be needed to judge knees head-on |
| At 45° the body line is checked only coarsely, and the knee rule is 'off the floor' | Recorded on the phone, honest planks read 152–167° at 45°, so the ±10° body-line rule cannot be measured there; a rep is refused only when its median body line folds below 140°. In staked sittings, knees are judged on the push back up, mainly by how high the knee and ankle stand above the hands (honest 0.44–0.65 arm-lengths, knees down 0.32–0.39, line at 0.41), with the knee angle (below 135°) as a second signal — a leg bent a little in the air is allowed. All of it is one person's recordings, with the phone at floor level; the margins are 0.03–0.05. Knee push-ups filmed at a flatter angle, nearer head-on, read like honest reps | More recorded sets, especially 45° knee push-ups recorded on the phone; a lower-body classifier if the angle proves too loose |
| A cobra is refused at 45°, not proven head-on | Lying flat and pressing only the chest up, hips on the floor, counted at 45° until the body line was judged on the push back up (honest 157–174°, cobra 120–134°). Head-on no cobra has been recorded | Record head-on cobra reps before relying on the Daily 25 there |
| No liveness check and no audio anti-spoofing in rep verification | The palm-liveness check was dropped on 22 September after the first sitting on real hardware: by the time the checks pass the member is face-down on the floor, and it asked them to lift a hand off it. Holding the top of a push-up starts the sitting instead — a readiness check, not a liveness one. So nothing here tells a live person from a video played to the lens, or one member from another. Microphone permission, which would, adds first-run friction and protects no real value on devnet | Reinstate the audio check and the audio rep condition together — one move restores liveness and anti-spoofing both |
| Stake chips ($10 / $25 / $50 / $100) are priced in SKR by the Operator from an off-chain rate — once at launch, re-checked weekly in Phase 2 | The contract must never read a price | On-chain chip governance before real value is at stake |
| Cohorts are filled by real members first; scripted members — real wallets under the same contract rules, whose forfeits fund real prizes and whose completed days are **not camera-verified** — top a cohort up only to a minimum (20 members in a 7-day cohort, 12 in a 21-day one) | A cohort must never be empty when someone installs the app during judging | Disclosed here, in the app, in the demo video and in the pitch deck; their addresses are published in the build and every scripted member is labelled wherever it appears |
| A wallet is required before anything, even the Daily 25 | One identity from the first tap; guest mode was considered and dropped | The connect screen plays the recorded walkthrough without a wallet, so judges who cannot connect one still see the whole product |
| The full cycle is shown as a recorded walkthrough in the app, for judges | A judge can't wait 7 days | It is a recording of the real app against the real devnet contract, taken from the first judged cohort. Members get a three-card tour instead |
| The faucet's fee sponsor key ships inside the app | A wallet with zero SOL cannot pay for its own first transaction, and there is no server to sign for it | Anyone can extract the key, and empty the 0.05 SOL float it holds, so the contract bounds that instead of assuming it will not happen: one claim per wallet a day, a single daily budget of 0.72 SOL covering **every** lamport that leaves the faucet — top-ups and reimbursements alike — and a permissionless refill, so a drained sponsor is restored by the next crank pass rather than ending onboarding. Real fix: no faucet on mainnet, or a rate-limited relayer |
| The program keeps its upgrade authority during judging | A defect found while cohort #3 holds real test stakes must be fixable | Used only for defect fixes that change no existing cohort, account layout or fund movement. Real fix: revoke the authority once the contract is final |
| A cohort nobody finished cannot settle until its league has a lobby open for joining | The carry-over has to go into a cohort members can still join, and the contract will not let a caller's timing decide otherwise: settlement is refused while no such cohort exists (error 6047). Opening the league's next cohort is permissionless and already due, and the crank does it before settling anything, so the refusal costs one automation pass | Ship `deliver_carry_over` (Phase 2, already specified) so a league that stops opening cohorts entirely can still move the amount it would then hold |
| Notifications are sent manually | No notification infrastructure in the build | Push notification service |

## Layout

| Path | |
|---|---|
| `.specify/memory/constitution.md` | The principles every phase is checked against (v1.3.1). Read first. |
| `specs/001-holdout-hackathon/spec.md` | The feature spec — what and why, no stack. Clarified; ready for planning. |
| `docs/holdout_decisions.md` | Decision record H1–H30: why the build is what it is, what was rejected, what is still open. Read before changing the mechanic. |
| `docs/holdout_demo_video.md` | The demo video and in-app walkthrough: storyboard, screen checklist, recording workflow, filming calendar and the beta-test plan. |
| `docs/holdout_setup_runbook.md` | The steps only a human can do: toolchain, keys, devnet SOL, the domain, the two hardware spikes and the beta testers. Start here to unblock the app. |
| `docs/holdout_scope_and_state_machines.md` | Scope cut, decisions D-A…D-I, state machines and edge cases, synced to the spec. Read before coding. |
| `docs/holdout_v1_prd.md` | Product requirements for this build: screens, copy, verification rules, settlement math and test vectors. Adds detail to the spec, never scope. |
| `docs/archive/` | The full post-hackathon vision — the original PRD and its decision record — kept for the pitch. Not the build. |

When documents disagree: constitution → spec → decision record → scope doc → PRD.

## Workflow

`/speckit-plan` → `/speckit-tasks` → `/speckit-implement`.

`specs/001-holdout-hackathon/quickstart.md` builds, tests and deploys it;
`docs/holdout_setup_runbook.md` covers the parts that need a domain, a phone or
a person.

Constitution and spec are written and clarified; amend them rather than regenerating them.
