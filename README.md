# Holdout

[![CI](https://github.com/illia-loshchinin/holdout/actions/workflows/ci.yml/badge.svg)](https://github.com/illia-loshchinin/holdout/actions/workflows/ci.yml)

Solana Mobile fitness commitment dApp. Built for the CLOCK IN hackathon, submission due 13 Oct 2026, 01:59 CEST (12 Oct, 23:59 UTC); judging runs 13 Oct – 9 Nov 2026.

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

The build lands in two phases: the core loop first, then — only if there's time before the 28 September freeze — league leaderboards, weekly chip re-pricing and automatic offline submission (spec §3a). Carry-over delivery was cut on 5 October, when nothing carried over any more (H34).

## Known weaknesses

Named here on purpose (constitution XI). Each has its real fix beside it.

| What | Why it is like this | Real fix |
|---|---|---|
| Devnet with a mock SKR mint | Hackathon build; no real value at stake | Mainnet with real SKR |
| "Stake back" is true in SKR, not in dollars | Everything is SKR-denominated with no oracle in the contract | A known property of SKR-native staking, stated in the app |
| `ladder_gate_enabled = false` — both leagues open from first launch | Nobody should have to spend a week before reaching the 21-day league; the unlock is still computed and recorded | Flip the flag |
| `genesis_gate_enabled = false` | Testers have no Seeker | Flip the flag on mainnet |
| Non-default `day_seconds` for test cohorts — a **short test lobby kept running on devnet**, labelled and open to anyone, from which the demo video is recorded | Lets a single person run a whole cycle alone: three 10-minute days, a new cohort every 30 minutes (FR-044a, H36). Only one test league runs at a time, since streaks are written when a cohort is counted and two test leagues of different lengths could count a member's outcomes out of order on the combined test board | Admin-guarded; real cohorts always use 24 hours |
| A completed day's proof is client-signed | No backend in the build | Server-side attestation signer with pinned traces |
| Reps banked during the day (in blocks of 10), and a completed day waiting for network, live only on the phone | No wallet prompt mid-workout; a lost phone loses that day's partial progress, and a rooted phone could edit it. A day finished offline counts only if it reaches the network before the deadline | Covered by the attestation fix; cross-device progress later |
| Knee push-ups count in the Daily 25 and in Easy Holdouts, by design | The Daily 25 and Easy Holdouts **count push-ups from the knees on purpose** — a free habit, and a 7-day Holdout, for building strength; only Hard Holdouts refuse them. Every sitting is counted at 45° since 8 Oct, and the Daily 25 is held to the Easy set-up: until then it also accepted the phone head-on, where the legs and hips point into the lens and knee angle, knee and ankle height, hip height, torso angle and body line all read the same for knee push-ups as for honest ones (`fixtures/rep-traces/front-knees-01`), so it counted there what a Holdout refuses | Nothing to fix for the knees: the Daily 25 shows how many of the day's reps came from the toes, so a member can see how far they are from Hard |
| At 45° the body line is checked only coarsely, and the knee rule is 'off the floor' | Recorded on the phone, honest planks read 152–167° at 45°, so the ±10° body-line rule cannot be measured there; a rep is refused only when its median body line folds below 140°. In staked sittings, knees are judged on the push back up, mainly by how high the knee and ankle stand above the hands (honest 0.44–0.65 arm-lengths, knees down 0.32–0.39, line at 0.41), with the knee angle (below 90°: a deep kneel, or the feet raised behind) as a second signal — a leg bent in the air is allowed. The lines were set on one person's recordings, with the phone at floor level; the margins are 0.03–0.05. A second person's knee push-ups read the legs over the 0.41 line (0.42–0.44; his honest reps 0.51–0.79), so the legs on the push are also judged against the member's own plank held through the countdown: under 0.66 of it is knees down (honest reps on every recording 0.70 or more, his knee reps 0.60–0.63). Knee push-ups filmed at a flatter angle, nearer head-on, read like honest reps. Knees are judged only while the elbow is above 100°, on the way down and on the push back up: at the bottom the landmarks read honest reps lower than push-ups from the knees (legs 0.27–0.43 against 0.42–0.65), so a rep pushed from the knees through the bottom and back on the toes by 100° is not caught | More recorded sets, especially 45° knee push-ups recorded on the phone; a lower-body classifier if the angle proves too loose |
| Loose trousers hide the knees from the pose model | In loose trousers the model cannot find the knee and puts it on the ankle, so nothing about the knee can be judged: a staked sitting does not start, and says so ("Can't see knees"). Measured on one person in loose white trousers — the knee unreadable in 60–67 % of frames, against 0–1 % for fitted clothing | Wear something fitted or roll the trousers up; the knee-on-the-floor image classifier would not depend on the knee landmark |
| A cobra is refused at 45°, but not in every case | Lying flat and pressing only the chest up, hips on the floor, counted at 45° until the body line was judged on the push back up (honest 157–174°, cobra 120–134°). A cobra done with the body nearly straight, and a rep pushed up from lying on the floor, passed that check; they are refused by how far the hips have risen half-way up the push against the shoulders (honest 0.92–1.06, those 0.63–0.87, line 0.90) — with a margin of 0.02–0.03, measured on two people; in a staked sitting a rep whose rise cannot be read is not credited. From further in front than about 35° that check does not hold, so no sitting starts there; the Daily 25 and Easy, which accept a body line straight to the ankle **or to the knee** (so knee push-ups with the feet raised count), stop at about 40°. Until 8 Oct the Daily 25 started further in front, and cobras got through there: 4 of 5 on one recording (`angled-mixed-03`). Not caught: a rep let down until the hips rest on the floor and pushed up straight in one piece — no landmark reading tells a hip on the floor from a deep honest bottom (four counted on 4 Oct) — and, on a second person whose head covered the near hand at the bottom, two cobras, because the near wrist was misplaced and every arm reading with it | The knee-on-the-floor image classifier, extended to hips on the floor; a wrist reading that holds when the head covers the near hand |
| No liveness check and no audio anti-spoofing in rep verification | The palm-liveness check was dropped on 22 September after the first sitting on real hardware: by the time the checks pass the member is face-down on the floor, and it asked them to lift a hand off it. Holding the top of a push-up starts the sitting instead — a readiness check, not a liveness one. So nothing here tells a live person from a video played to the lens, or one member from another. Microphone permission, which would, adds first-run friction and protects no real value on devnet | Reinstate the audio check and the audio rep condition together — one move restores liveness and anti-spoofing both |
| Stake chips (≈ $10 / $25 / $50 / $100) are priced in SKR by the Operator from an off-chain rate — **a trusted input**: by hand at launch, then re-priced every week by the Operator's `crank chips publish --auto`, which reads the **Jupiter Price API** (mainnet SKR mint) | The contract must never read a price | On-chain chip governance before real value is at stake |
| Cohorts are filled by real members first; scripted members — real wallets under the same contract rules, whose forfeits fund real prizes and whose completed days are **not camera-verified** — top a cohort up only to a minimum (20 members in a 7-day cohort, 12 in a 21-day one) | A cohort must never be empty when someone installs the app during judging | Disclosed here, in the app, in the demo video and in the pitch deck; their addresses are published in the build and every scripted member is labelled wherever it appears |
| A wallet is required before anything, even the Daily 25 | One identity from the first tap; guest mode was considered and dropped | The connect screen says how to get a wallet; the demo video, sent with the submission, shows the whole product without one |
| The full cycle is shown in the demo video, not inside the app | Nobody can wait 7 days to see a settlement; the submission is the app's final state, so it carries no recording or section for judges | The video is a recording of the real app against the real devnet contract, on a labelled short test Holdout, which anyone can also join and run in about half an hour |
| The faucet's fee sponsor key ships inside the app | A wallet with zero SOL cannot pay for its own first transaction, and there is no server to sign for it | Anyone can extract the key, and empty the 0.05 SOL float it holds, so the contract bounds that instead of assuming it will not happen: one claim per wallet a day, a single daily budget of 0.72 SOL covering **every** lamport that leaves the faucet — top-ups and reimbursements alike — and a permissionless refill, so a drained sponsor is restored by the next crank pass rather than ending onboarding. Real fix: no faucet on mainnet, or a rate-limited relayer |
| The program keeps its upgrade authority during judging | A defect found while cohort #3 holds real test stakes must be fixable | Used only for defect fixes that change no existing cohort, account layout or fund movement. Real fix: revoke the authority once the contract is final |
| The fee funds reserves, a developer share and operations by rule, but the developer and operations shares are ordinary wallets | Of every forfeited stake, 10% and 5% go to the league's boost and comeback reserves, which only the program can move, and 5% and 10% to the developer and operations wallets named in this README (H35). What those two wallets then do with it is not enforced by the contract | Publish both wallets' addresses and their spending; later, move operations spending on chain or under a multisig |
| Easy Holdouts count push-ups from the knees on thin evidence | Knees-allowed counting at 45 degrees was measured on about 26 recordings from mostly one person: 87% of honest knee reps counted, 3 of 56 cobras slipped through - 2 of them filmed at about 30 degrees, which the Easy camera check now refuses (H35) | Record at least 3 people, 100 knee reps and cobras at 30-60 degrees, and replay them before any real money rides on Easy |
| A Daily 25 done before the phone has ever read chain time is dated afterwards, from the phone's own uptime clock | The Daily 25 needs no connection, but a day is chain time (05:00 UTC), never the phone's clock. The last chain reading is kept, so this only happens after a reboot with no network since. Several such sittings are dated together, from the latest one's start; reps done offline across a reboot stay undated | Keep one record per offline sitting and date each separately |
| Reminders are on-device only — no push | No server knows anyone's deadlines. The phone schedules a reminder when a Holdout starts and before each day's deadline (FR-068), from chain time; Android may deliver it a few minutes late, not at all after a restart until the app is opened, and never if notifications are declined. The deadline is the chain's, never the reminder's | A push service fed by the chain, alongside the on-device reminders |

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
