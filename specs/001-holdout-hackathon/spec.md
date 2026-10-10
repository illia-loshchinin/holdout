# Holdout — feature specification (hackathon build)

**Date:** 2026-09-15, last clarified 2026-09-18 · **Status:** clarified, planned and tasked — implementation starts at T001 · **Target:** CLOCK IN submission due 9 Oct 2026 08:59 CEST · judging 10 Oct – 9 Nov 2026

> This IS the spec-kit feature spec for `001-holdout-hackathon`. All open decisions in §9 are resolved (see Clarifications). Work is split into **Phase 1 (core)** and **Phase 2 (deferred)** — see §3a. The plan, its design artifacts and the 116-task list are written; next is `/speckit-implement`.
> **This file describes *what* and *why* only.** Stack, accounts, PDAs, instruction signatures and framework choices belong in `plan.md`.
> Governed by `.specify/memory/constitution.md`. Behavioural detail and the reasoning behind the cuts live in `docs/holdout_scope_and_state_machines.md`; screens, copy, verification rules and settlement test vectors for this build live in `docs/holdout_v1_prd.md`; the full post-hackathon product vision is archived in `docs/archive/holdout_v1_prd_full-vision_2026-09-15.md`, which this spec deliberately does **not** try to satisfy.

---

## 1. What this is

A fitness commitment contract. A member stakes on themselves to complete a daily push-up obligation for the length of a fixed cohort. Everyone in a cohort starts and finishes together. Miss a day and the stake is forfeited into that cohort's prize pool; finish and take back the stake plus a share of what the quitters left behind.

> Stake. Do your reps every day.
> Miss a day and you lose your stake to everyone still standing.
> Finish and you get your stake back plus a share of theirs.

**Why it works as a product:** the cohort is visible and it shrinks. `189 / 312 still standing` is a number no rolling-join competitor can display, and it is the reason to open the app on a day when nothing else happened.

**Why it is being built now:** a submission to CLOCK IN, the Solana Mobile hackathon, judged from 13 October to 9 November 2026 in four equal parts — stickiness and product fit with the Seeker community (habits and daily engagement), user experience, innovation, and presentation and demo quality — not a startup launch.

---

## 1a. What members call things

| Members read | The system stores | Notes |
|---|---|---|
| **The Daily 25** | no record on-chain; a device-local streak | The free tier: 25 push-ups, any day, alone, nothing staked (FR-048) |
| **A Holdout** — "a 7-day Holdout", "a 21-day Holdout" | `Cohort`, in one of the two leagues | A staked run. "189 still standing in this Holdout" |
| **Still standing** | memberships not in breach | Never "survivors left alive" or similar |
| **At stake** | the sum of held stakes in a cohort | What a lobby shows. It is **not** a prize |
| **Prize pool** | `Cohort.pool` | Only a running Holdout has one, because only forfeits create it (FR-041) |

On-chain and technical names — `Cohort`, `LeagueConfig`, cohort index — are unchanged, so the data model, contracts and automation keep their vocabulary. This table is the mapping between the two.

## Clarifications

### Session 2026-09-15

- Q: How does a judge see a full cycle (join → sessions → forfeit → settle → claim) without waiting days, while the home screen always shows a live, thinning cohort? (OD-4) → A: Real-time cohorts run continuously with disclosed scripted members; the full cycle is shown as a recorded walkthrough inside the app — watched, not performed. *(Superseded 2026-10-09: shown in the demo video, sent as a link — FR-044.)*
- Q: If nobody in a staked cohort finishes, where does that cohort's prize pool go? (OD-5) → A: At settlement, 50% goes to the fee destination and 50% carries over into the prize pool of the next cohort of the same league, as extra prize for its finishers.
- Q: Should every reference to free cohorts and a "free league" be removed, leaving only the two staked leagues plus the daily challenge? → A: Yes — free cohorts are removed entirely; every cohort is staked, the no-stake path is the daily challenge, and the `Finished` membership state is dropped.
- Q: Should audio be dropped from rep verification entirely — both the environment check and the rep condition? (OD-3) → A: Yes — drop both. Four environment checks, no audio rep condition, no microphone permission; documented in the README as a known weakness.
- Q: What SKR amounts should the four stake chips have? (OD-2) → A: Chips are SKR amounts tracking $10 / $25 / $50 / $100. The Operator checks the rate weekly and re-prices only when it has drifted drastically. Each cohort locks in the chip set current at its creation, so members of the same cohort who pick the same tier stake identical SKR. The contract validates SKR only and never reads a price. *(Revised in-session from daily re-pricing applied to all open cohorts. Revised 2026-10-06: re-priced every week, with no drift threshold — FR-056.)*
- Q: When does registration for a league's next cohort open, and can a member still in a running cohort join it? → A: Every league opens its next cohort the moment its current one starts; cohorts start weekly on Monday 05:00 UTC. Joining and leaving both close at a join cutoff **1 hour before the start**, after which the cohort is `Locked` until it starts. A member may hold one lobby place alongside a running cohort, independent of how that cohort ends. *(Revised in-session: briefly specified as join and leave open until the start with no `Locked` state, then rolled back.)*
- Q: Which extras beyond the core loop are in scope — daily streak, leaderboards, share cards, badges? → A: Only the daily streak (already specified) and leaderboards. Leaderboards rank on-chain progress only and exist to show discipline: per league, members are ranked by **survival rate** (cohorts cleared ÷ cohorts finished), among members with **at least 3 finished cohorts** in that league; cohorts played and league streak (consistency) are shown beside it. Each running cohort also has a roster. The daily streak stays personal and unranked. Share cards and badges are out of scope.
- Q: Who may trigger recording forfeits, settling a finished cohort and opening the next scheduled cohort — anyone, or only the Scheduler's key? → A: Anyone. The Scheduler is the usual caller, not a privileged one; the caller can never choose a parameter, outcome or amount. Publishing the chip set stays Operator-only.
- Q: What is the largest number of members a single cohort must support? → A: No limit is enforced; the expected size is up to 1,000 members per cohort, and the build is designed and tested for that.
- Q: When finisher payouts are rounded down to the smallest SKR unit, where do the leftover units go? → A: At settlement, the total rounding remainder is computed and sent to the fee destination, and recorded on the cohort.
- Q: At a day's deadline, should the survivor counter and projected prize change immediately for members who missed the day, or only once their forfeits are recorded? → A: Immediately. The survivor counter, projected pool, projected shares and cohort roster reflect every breach from the instant the deadline passes; claimable amounts come only from settlement.
- Q: How long may a paused session wait before it's abandoned, and how much time must be left before a session may start? → A: Neither — there is no single session. Verified reps are **banked in blocks of 10** on the device and kept for the whole day, so a member can do 10 + 10 + 5 across the day in separate sittings. Reps short of the next block when a sitting ends are discarded. Every sitting re-runs the environment checks and palm liveness. The app never refuses a late start; it shows reps still needed and time left, and warns clearly in the last 10 minutes.
- Q: Can someone do the free daily challenge and build a streak without connecting a wallet, or is a wallet required from the first screen? → A: A wallet is required from the first screen, before anything else — including the daily challenge, the streak, cohorts and leaderboards.
- Q: How does a member or judge with a freshly connected wallet get test SKR to stake and devnet SOL for transaction fees? → A: An in-app faucet: one tap gives a connected wallet enough test SKR for the top chip plus a little devnet SOL for fees, once per wallet per day, with no server involved.
- Q: Are scripted members real on-chain wallets playing by the same contract rules, or display-only? → A: Real wallets, same rules. They stake test SKR, submit completed days and forfeit or finish on a planned schedule; the contract treats them like any member, so their forfeits fund real prizes and their finishes share them. They are labelled everywhere.
- Q: If a member finishes their last rep offline and the completed day can't reach the chain before the deadline, what happens? → A: Offline sittings are allowed and blocks still bank; the app warns clearly before an offline sitting and when a day completes offline, retries the submission automatically as soon as a connection returns, and the deadline stays strictly chain time — a day that lands late does not count.
- Q: When the palm check can't detect an open palm, how long does the app keep trying and what happens then? → A: After 30 seconds without a detected palm, show a tip and return to the environment checks; retries are unlimited, the member can cancel at any time, and banked reps are unaffected.
- Q: Given 13 days to the feature freeze, how should leaderboards, weekly chip re-pricing, delivery of a held carry-over and automatic offline retry be scheduled? → A: As **Phase 2 inside this spec**: built only after every Phase 1 item works end to end, with simple Phase 1 stand-ins. The data they need is still designed and recorded in Phase 1, so Phase 2 needs no migration.
- Q: May Phase 2 items land after the feature freeze? → A: No. The freeze stands; any Phase 2 item not finished by it is cut from the hackathon build and recorded in the decision record for later. *(The freeze moved from 28 September to 5 October on 2026-09-26, and was removed on 2026-10-06 — constitution v2.0.0.)*

### Session 2026-09-17

- Q: Where should the in-app walkthrough's full cycle be recorded — only from a real 7-day cohort on the judged program, or from a labelled rehearsal cohort with shortened days? (FR-044) → A: Real cohort only. It is recorded from the first judged 7-day cohort, including its real settlement on 5 October and a real claim, and added to the build submitted before 9 October; until then the app says the recording is in progress. No shortened-day recording is used. *(Superseded 2026-09-26: see FR-044a — the walkthrough may be recorded from the disclosed short test cohort.)* *(Superseded 2026-10-09: no walkthrough in the app; the demo video is sent as a link — FR-044.)*
- Q: Once the judged program is deployed on devnet, may it still be upgraded to fix bugs while cohorts hold stakes, or should it be locked? → A: Upgradeable through judging, to fix defects only. An upgrade never changes the rules, parameters or stored layout of any existing cohort, membership or progress record, and the upgrade authority is named in the README as a known weakness.
- Q: Must the hackathon build be published on the Solana dApp Store for real users? → A: No — the CLOCK IN rules do not require it by the submission deadline. The product launches as is on devnet and reaches judges as a signed APK with the submission. If it wins, it must be published on the dApp Store within 30 calendar days of the winners' announcement; that listing, and any mainnet launch, are post-hackathon work. *(Revised in-session once the official rules were read.)*
- Q: How long must the devnet build keep running — live cohorts, the faucet and the scheduler? (FR-042a, FR-043) → A: Per the CLOCK IN rules, judging runs 10 October – 9 November 2026 and winners are announced on 10–11 November 2026 (the rules page gives both dates). The build stays live, with no manual intervention, from submission through **11 November 2026**. *(Dates moved 2026-10-09: judging 13 October – 9 November 2026.)*
- Q: Can the pose model actually measure the stated rep rules, and should other push-up variants be supported? (FR-021) → A: Yes, with care — published validation puts the model's joint-angle error at about 2° (RMSE ≈ 2.6°), so the counter measures angles from 3D world landmarks, smooths them, and triggers on wider thresholds than the stated rule so jitter cannot count a rep; spike S2 sets the final numbers (research R4). **Negative (eccentric) and decline push-ups are post-hackathon**, but the **exercise becomes a cohort parameter now** so they need no migration later (FR-007, FR-014).
- Q: What do members call the free tier and a staked run? → A: **The Daily 25** and **a Holdout** (a 7-day Holdout, a 21-day Holdout). On-chain names stay as they are; §1a maps the two vocabularies.
- Q: How does a first-timer learn what the app is? → A: A **three-card tour** right after the wallet connects — the Daily 25, what a Holdout is, and how reps are counted — skippable and re-openable later (FR-067). A short explainer video joins it once cohort #3 has been filmed.
- Q: Who is the recorded full-cycle walkthrough for, now that first-timers have a tour? → A: **Judges.** It stays in the app as FR-044 requires, reachable from an About section and from a quiet link on the connect screen — the judge who cannot connect a devnet wallet — but it is not a member-facing feature. *(Superseded 2026-10-09: no walkthrough in the app; the demo video is sent as a link — FR-044.)*
- Q: Should someone be able to use the app as a guest, doing the Daily 25 and watching Holdouts without a wallet? → A: No. Considered and dropped in this session; **connecting a wallet stays the first step** (FR-001a, H21 unchanged).
- Q: With real beta testers available, are scripted members still needed to populate judging-period cohorts? (FR-043) → A: Real members come first; scripted wallets only top a cohort up to a minimum — **20 members for a 7-day cohort, 12 for a 21-day one** — so they fill gaps instead of forming the crowd. Once the minimum is reached, no further scripted wallet joins.
- Q: Can the recorded walkthrough be watched before a wallet is connected? (FR-001a, FR-044) → A: Yes, and it is the only thing that can. The connect screen offers it, so a judge without a devnet wallet still sees the whole product; everything else still requires a wallet. The walkthrough runs the whole flow, from launching the app to the claim. *(Superseded 2026-10-09: no walkthrough in the app; the demo video is sent as a link — FR-044.)*
- Q: What must the hackathon submission contain? → A: Per the CLOCK IN rules, before 9 October 2026 08:59 CEST: a functional Android APK that runs on a real device, a GitHub repository someone else can clone and run, a demo video of about three minutes, and a pitch deck. Judges score completion from the demo video and technical depth from the commit history; false or misleading submission materials disqualify, so scripted members and the test network are disclosed in the video and deck as well as in the app and README. *(Date moved 2026-10-09: before 13 October 2026, 01:59 CEST — FR-066.)*

### Session 2026-09-26

Product owner decisions, with constitution v1.4.0.

- Q: When does the first judged cohort start, and when is the freeze? → A: **Monday 5 October 2026, 05:00 UTC**, moved a week from 28 September, and the feature freeze moves with it. A 7-day Holdout starting then settles on 12 October — after the 9 October submission. *(The freeze was removed on 2026-10-06.)*
- Q: How is the whole cycle shown before the deadline, then? → A: On **one short test cohort, devnet only** (FR-044a): a test league with a compressed day, created by the admin on the devnet program built with the `test-leagues` feature, labelled as a short test cohort wherever it appears, and disclosed in the README, the demo video and the pitch deck. The demo video may be recorded from it (FR-044). The judged 7-day and 21-day leagues keep real 24-hour days. A mainnet build never carries `test-leagues`. *(Supersedes the 2026-09-17 answer that no shortened-day recording is used. Superseded 2026-10-06: one cohort became a test lobby kept running — FR-044a.)*

### Session 2026-09-27

Product owner decisions, from the first local run on a Seeker.

- Q: How long is the short test cohort's day, and how many reps? (FR-044a) → A: **Seven 10-minute days at 5 reps a day** — a 7-day Holdout in 70 minutes (H32, amended 2026-09-29 from seven one-hour days). The judged leagues keep 24-hour days and 25 reps. *(Superseded 2026-10-06: three 10-minute days.)*
- Q: Does a member get a notification when their Holdout starts, or before a deadline? → A: **Yes — on-device reminders** (FR-068, H33). The app schedules them on the phone itself when the member joins: one when the Holdout starts, and one before each day's deadline while that day is not yet recorded. No server and no push service; nothing leaves the device. Timed from chain time, delivered by the phone's alarm service, so a reminder may arrive a few minutes late — the deadline is the chain's, never the reminder's. A member who declines notifications loses nothing else. *(Supersedes "notification infrastructure (manual for the demo)" in §3.)*

### Session 2026-10-05

Product owner decisions, before the program is first deployed.

- Q: How is a forfeited stake split? (FR-027) → A: **70/30** — 70% to that cohort's prize pool, 30% to the fee destination (was 80/20). How the 30% is then divided — the next Holdout's prize, a Daily 25 promotion, the treasury — is **still open**, to be researched before anything is built; until then all of it reaches the fee destination on chain.
- Q: Where does a no-finisher cohort's pool go? (FR-037) → A: **All of it to the fee destination.** Nothing carries over any more, so settling such a cohort needs no other cohort, error 6047's refusal is gone, and the Phase 2 delivery of a held carry-over (`deliver_carry_over`, T099) is cut rather than built.
- Q: How is the 30% fee divided, and what does it fund? (FR-027, H35) → A: **10 boost reserve / 5 comeback reserve / 5 developer / 10 operations**, all by program rule. Reserves are credited at settlement; a boost of up to 20% of a cohort's own pool, and a comeback refund of 10% of a forfeited stake for finishing a later Holdout within 14 days. **The Daily 25 is never paid**, on any network. Random airdrops rejected. *(Re-split 2026-10-09: 5 boost / 5 comeback / 10 developer / 10 operations; the comeback refund is now 20%.)*
- Q: Easy and Hard? (FR-007, FR-021a, H35) → A: The league carries a **form**, copied to each cohort: the 7-day league is **Easy** (knee push-ups count, phone at 40–60° only), the 21-day league **Hard** (from the toes). Shipped now, overriding the freeze (constitution v1.6.0).

### Session 2026-10-06

- Q: Does the ladder keep a locked Human Flag rung above the two leagues? (US-1) → A: **No — removed entirely.** The ladder is the Daily 25 under the 7-day and 21-day leagues, nothing above them. It never existed in the contract, so nothing on chain changes. *(Removes it from US-1 and from the §3 out-of-scope list.)* *(Superseded 2026-10-10: there is no ladder at all — the two leagues are separate products.)*
- Q: Are the league leaderboards and automatic offline submission built? (§3a, H36) → A: **Yes — both**, client and crank only, with no contract change.
- Q: Which time windows does a league leaderboard cover? (FR-060) → A: **All-time only, one board per league**, from on-chain progress. Quarterly and yearly boards are **deferred** — not built in this phase.
- Q: Does the short test league get a leaderboard? (FR-060, FR-044a) → A: **Yes — one combined board, labelled as a short test cohort.** Every test league shares one progress record, so their figures are combined; it is the one board on which ranking can be seen before judging, since no member can finish three judged cohorts by 9 October.
- Q: Does the 5 October feature freeze still apply? (§3a) → A: **No — removed** (constitution v2.0.0). Phase 2 items are built after Phase 1 works end to end, with no date gate; an item cut by decision (the carry-over delivery) stays cut.
- Q: How does a judge try the whole cycle on devnet alone? (FR-044a) → A: **A short test lobby kept running.** The devnet build carries a test league whose labelled short cohorts keep opening alongside the real 7-day and 21-day lobbies through the whole live period, so one judge can join, sit, finish (or miss a day) and claim alone. The presentation video shows the cycle on a test cohort and says so.
- Q: How long is a test-lobby Holdout, and how often does one start? (FR-044a) → A: **Three 10-minute days at 5 reps a day — 30 minutes — with a new cohort every 30 minutes**, back to back, joining closing 5 minutes before each start. *(Supersedes seven 10-minute days. Join lock superseded 2026-10-07: 0.)*
- Q: Where does the weekly check read the SKR rate from? (FR-056, FR-057) → A: **The Jupiter Price API only**, keyless, by the mainnet SKR mint. No answer, or an unusable one, is no rate: nothing is published that week.
- Q: How is drift measured against the current chip set? (FR-056) → A: **On the $100 tier**: |the $100 chip's SKR × rate − $100| ÷ $100. Above 20% re-prices all four. The top tier is the least distorted by rounding down to 10 SKR, so rounding alone never triggers a re-price. *(Superseded the same day: there is no threshold — see below.)*
- Q: What runs the weekly check, and how often may it publish? (FR-056) → A: **A standalone Operator command**, started by the Operator's own scheduler after Monday 05:00 UTC — never the always-on automation, so the Operator key is used only for that run. It publishes **at most once per weekly boundary**: a set already published since the latest Monday 05:00 UTC means nothing more to do. A missed Monday is skipped, not caught up (FR-057). *(Schedule superseded 2026-10-07: run hourly, and a failed run is retried within the same week.)*
- Q: Does the weekly check still wait for 20% drift before re-pricing? (FR-056) → A: **No — every week, the chip set is re-priced from that week's Jupiter rate**, with no threshold and no `--if-drift` option. It publishes even when the rounded amounts equal the current set, so there is a new version every week. Still at most once per weekly boundary, still the Operator's standalone command, still nothing when there is no rate (FR-057). The residual race of two overlapping runs is accepted: one Operator key, and the worst case is a near-identical duplicate set that week.

### Session 2026-10-09

Product owner decisions after the organisers moved the CLOCK IN dates, and with spec 004 (Holdout Lab and the member app, constitution v2.1.0).

- Q: When is the submission due, and when does judging run, now that the organisers have moved the dates? (FR-043, FR-066) → A: **The submission is due 13 October 2026, 01:59 CEST (12 October, 23:59 UTC); judging runs 13 October – 9 November 2026.** The winners' announcement stays 11 November 2026 (constitution v2.0.1).
- Q: Does the app still carry a recorded full-cycle walkthrough, an About section for judges and links for judges? (FR-001a, FR-044) → A: **No.** The submission is judged as the app's final state, and the demo video, sent as a link with the submission, shows the full cycle. The app has no walkthrough, no *About · for judges* and no link addressed to judges. Before a wallet is connected, only the connect screen and, when the phone has no wallet app, help getting one are reachable (spec 004).
- Q: Who is the short test lobby for, once nothing in the app is addressed to judges? (FR-044a) → A: **Anyone.** It keeps running beside the 7-day and 21-day Holdouts, labelled as a short test Holdout, so a single person can run the whole cycle alone; its rules are unchanged (constitution v2.1.0).
- Q: Is Holdout Lab, the team's separate tester app that records and labels sets (spec 004), part of this build? (§2, §4) → A: **Yes, as spec 004 defines it.** It is installed only on the team's and consenting testers' phones, never published or offered to members, and never connects to a wallet or a network. It is the only place traces, labels and video are made (constitution X v2.1.0). The member app contains none of it.
- Q: Which cohort does the demo video's real claim in AC-19 come from, now that FR-044 records the video from the short test lobby? (AC-19, FR-044) → A: **A cohort of the short test lobby**, labelled on screen as a short test cohort throughout, as FR-044 says. *(Revises AC-19's "first judged 7-day cohort".)*
- Q: How is a forfeited stake split, now that operations also has to fund marketing and development? (FR-027, FR-037) → A: **70% prize pool / 5% boost reserve / 5% comeback reserve / 10% developer / 10% operations**: 80% stays with members, 20% goes to the project, and the fee stays 30%. A no-finisher pool follows the developer:operations ratio, now **half each** (developer rounded down) instead of a third and two thirds. The pool and the finisher headline are unchanged; the boost reserve's inflow halves, so the average boost falls from about +7% to about +3.5% of a pool.
- Q: How large is the comeback refund, and which Holdout earns it? (FR-037e) → A: **20% of the counted forfeited stake** (was 10%), capped at the new stake. Everything else is unchanged: only a later Holdout of the **same league** earns it (lose a 7-day, finish a 7-day; lose a 21-day, finish a 21-day), starting within 14 of that league's days after the missed deadline; the league's comeback reserve (5% of every forfeit) funds it, cut pro rata when short. The reserve holds while the share who come back and finish × the refund ≤ 5%, so 20% holds up to a 25% come-back-and-finish rate.
- Q: Should a Holdout's pool get extra money before people join, to draw them in, or does the prize boost stay at settlement? (FR-037d, FR-053) → A: **The boost stays at settlement** (FR-037d unchanged: min(20% of the cohort's own pool, the league's boost reserve), only when someone forfeited). **Every Holdout offered to join shows the league's boost reserve as it stands**: "Prize boost: up to N SKR from this league's reserve when it settles", only when the reserve holds more than 0; "up to", because the draw is capped at 20% of the pool and paid only if someone forfeits, so it is never a promised amount. Rejected for the hackathon: moving reserve into a cohort's pool when it opens, which would pay out with nobody having lost, drain the reserve weekly and need a contract change before the deploy.
- Q: How are members identified on leaderboards and rosters, now that every Seeker owner has a Seeker ID (a `.skr` name)? (FR-062, FR-063) → A: **By Seeker ID when the wallet holds one, and by shortened wallet address otherwise.** Names are read from Solana mainnet for display only and never rank, order or label anyone; still no stake, prize or payout amount on any board. Detail: `specs/003-seeker-id-names` FR-002, FR-008. *(Revises FR-063's "shortened wallet address only".)*
- Q: Where does a no-finisher pool go, now that the fee has two destinations? (FR-037) → A: **Half, rounded down, to the developer destination and the rest to the operations destination** (`Config.fee_destination`); nothing carries over. Decided 2026-10-09 with the re-split; the §3a table, ES-7 and OD-5 said "the whole pool goes to the fee destination" from 2026-10-05 and now say this. No rule changes.

### Session 2026-10-07

Product owner decisions, from the PR #20 and #22 reviews.

- Q: How long before a test-lobby cohort starts does joining close? (FR-044a) → A: **It doesn't close early: the test lobby's join lock is 0.** The next cohort opens only once the current one has started, so a 5-minute lock left 5 minutes in every 30 with nothing a judge could join. With 0, each test cohort stays joinable until its own start, and the next opens at that instant. The only remaining wait is the crank's pass interval. A member who joins at the last moment starts day 0 at once. The judged leagues keep their own join lock. *(Supersedes "joining closing 5 minutes before each start".)*
- Q: How often does the Operator's scheduler run the weekly chip check, given that it must not publish before Monday 05:00 UTC? (FR-056) → A: **Every hour, in any time zone; the at-most-once-per-weekly-boundary guard decides.** The first run after Monday 05:00 UTC publishes and the rest of the week's runs do nothing. A run that fails or finds no rate is retried by the next hourly run within the same week. A once-a-Monday schedule needed `CRON_TZ`, which Debian's cron ignores; in a zone ahead of UTC, or across daylight saving, it could run before 05:00 UTC and publish a week late. *(Supersedes "a missed Monday is skipped, not caught up".)*
- Q: Does the Daily 25 keep the head-on placement? (FR-019, FR-021a, FR-067) → A: **No — the Daily 25 is counted at 45° only, in the Easy band (no more frontal than about 40°), with the Easy checks: an Easy Holdout with nothing staked.** It is where members practise the set-up a Holdout holds them to, and every Daily 25 sitting becomes a recording of the placement money rides on. Head-on could not see knees, a sag, a cobra or hips on the floor. Detail and evidence: `specs/002-rep-placement-rules` Session 2026-10-07. *(Revises the 2026-09-23 answer below.)*
- Q: May more than one test league open cohorts at the same time? (FR-044a, FR-060) → A: **No — one test league at a time: the newest (highest id) is the lobby.** The automation opens no new cohorts in an older test league; it winds down, and its open and running cohorts still run, settle and pay. Streaks are written when a cohort is counted, which is only after it ends, so two test leagues of different lengths running together could count a member's later clear before their earlier forfeit, and the combined test board would show the wrong streak. One test league's cohorts end in order, so this can't happen. Disclosed in the README; no program change.

### Session 2026-10-10

Product owner decision: the two leagues serve different audiences.

- Q: Is there a ladder between the leagues — must a member clear a 7-day Holdout before the 21-day league opens to them? (FR-003, US-5) → A: **No — the ladder gate and the notion of qualification are deleted, not flagged off.** The 7-day (Easy) and 21-day (Hard) leagues are two separate products for different people and do not contradict each other, so the 21-day league is open to every member at all times and never needs "qualification" through the 7-day one. Nothing is unlocked by a clear, nothing is recorded as an unlock, and no flag remains that could restore the gate. *(Replaces FR-003, FR-003a, FR-004 and US-5; constitution v2.2.0.)*
- Q: After a forfeit, which Holdout does the app offer as the way back, alongside today's Daily 25? (US-4) → A: **The next cohort of the same league.** It is the league the member chose, it is the one the comeback refund (FR-037e) pays on, and offering the 7-day league after a 21-day forfeit would present it as a lower rung.

---

### Session 2026-09-23

- Q: Where may the phone stand, now that the pose model cannot tell a push-up from the toes from one on the knees head-on? (FR-019, FR-021a) → A: **Two placements, head-on and 45°; square side-on is dropped.** The **Daily 25 accepts head-on or 45°**; a **staked Holdout accepts 45° only**. Evidence from two Seeker recordings: head-on, knees bent on the floor read 146–164° against an honest plank's 156° and no landmark signal separated them, so head-on verifies depth, lockout and tempo but not knees or sagging hips — acceptable for a free habit and said so, not when money is at stake. At 45° the plane the body bends in is turned towards the lens: read in the picture on the side nearer the camera, knee push-ups bottomed out at 74–102° at the knee. Square side-on was dropped because a body lying across a portrait frame needs the phone ~2 m away; 45° needs about 1.5 m. *(Revised in-session the same day: first staked = side-on only, then side-on or 45°, then this. Revised 2026-10-07: the Daily 25 is 45° only too — see Session 2026-10-07.)* The 45° knee threshold (150°) stays provisional until S2 records an honest 45° set.

## 2. In scope

| | |
|---|---|
| Exercise | Push-ups only in this build. The exercise is a **cohort parameter**, so a later one — negative (eccentric) or decline push-ups — needs no new account layout; only push-ups are accepted here (FR-007, H30) |
| Leagues | **Staked 7-day** (Easy) and **staked 21-day** (Hard), 25 reps/day each — two separate products for different people, both open to every member at all times |
| Free tier | **The Daily 25** — 25 reps, offered every day to everyone, no cohort, no stake, no prize. Builds a streak and nothing else. This is the habit product; the leagues sit on top of it. |
| Gating | **None.** Neither league requires anything earned in the other — no ladder, no qualification, no flag (2026-10-10). |
| Asset | SKR, and only SKR — stakes, pool, payouts, fee |
| Stakes | Four SKR chips tracking **$10 / $25 / $50 / $100**, locked per cohort at creation; the same tiers in both leagues. One chip set at launch in Phase 1; re-priced every week in Phase 2 *(§3a, §9 OD-2)* |
| Phasing | **Phase 1** is everything not listed as Phase 2 in §3a and must work end to end before any Phase 2 work starts. There is no feature freeze (removed 2026-10-06, constitution v2.0.0). |
| Fee | 30% of each forfeited stake (70% reaches the prize pool) |
| Network | Devnet, test token |
| Distribution | A signed Android APK delivered with the hackathon submission — no store listing (FR-066) |
| Tester app | **Holdout Lab** (spec 004): a separate app for recording and labelling sets, installed only on the team's and consenting testers' phones, never published or offered to members, with no wallet and no network (constitution X) |
| Deliverables | APK, GitHub repository, demo video of about three minutes, pitch deck — all before 13 October 2026, 01:59 CEST (FR-066) |
| Live period | From submission through the winners' announcement, **11 November 2026** (FR-042a, FR-043) |

## 3. Out of scope

**Free cohorts of any league** · geo-blocking · terms of service · calibration flow · Swelfie · conditional pre-joining (a lobby place that depends on clearing the current cohort and is refunded automatically on forfeit — the unconditional lobby place of FR-006a is in scope) · cohort cancellation and minimum size · cold-start bonus matching · insurance fund · demotion · push notifications from a server (reminders are on-device only, FR-068) · share cards · badges · a daily-streak leaderboard, or any leaderboard fed by a server · audio of any kind in verification — no audio environment check, no audio rep condition, no microphone access *(§9 OD-3)* · publishing on the Solana dApp Store, and any mainnet deployment — not required for submission; if the app wins, the dApp Store listing is due within 30 calendar days of the winners' announcement, as post-hackathon work.

## 3a. Phasing

**Phase 1 — core.** Everything in this spec not listed below. It is the full money loop and the daily habit: wallet first, faucet, lobby with lock and lobby place, sittings with banked reps (including offline sittings with a manual submit), completed days, forfeits, settlement with rounding and carry-over into an open cohort, claim, the survivor counter and roster, the Daily 25 and streak, scripted cohorts and the short test lobby. **Phase 1 must work end to end on devnet before any Phase 2 work begins.**

**Phase 2 — deferred.** Built only after Phase 1 is complete; there is no feature freeze (removed 2026-10-06). An item cut by decision is recorded in `docs/holdout_decisions.md`.

| Phase 2 item | Requirements | Phase 1 stand-in |
|---|---|---|
| **League leaderboards** | US-10 (league boards), FR-060…FR-060c, FR-061 display, FR-063, AC-14 | No league leaderboard screen. On-chain progress — cohorts cleared and forfeited, current and longest league streak — **is still recorded** from the first cohort, so boards need no migration. The cohort roster (FR-062, FR-064) **is Phase 1**. |
| **Weekly chip re-pricing** | FR-056, FR-057, ES-10, ES-11, AC-12 | The Operator publishes **one chip set at launch** from that day's rate; every cohort locks it as usual (FR-055). Publishing a new set must already be possible, so re-pricing is tooling, not a contract change. |
| **Delivery of a held carry-over** — *cut 2026-10-05* | FR-037a's held case, its delivery, ES-7a | Nothing carries over any more: a no-finisher cohort's whole pool goes out, half (rounded down) to the developer destination and the rest to the operations destination (FR-037; half each since 2026-10-09), so there is nothing to hold or deliver. The cohort has no carry-over fields. |
| **Automatic offline submission** — *built 2026-10-05 (T098)* | The automatic-retry part of FR-024b and ES-3b, and of AC-18 | A completed day waiting for network stays on the device and the home screen shows **"Done — tap to submit"** once online; the member taps once. All offline warnings are Phase 1. |

Requirements, edge scenarios and acceptance criteria belonging to Phase 2 are marked **[Phase 2]** where they appear.

---

## 4. Actors

| Actor | Description |
|---|---|
| **Member** | A person with a wallet and the app. Joins cohorts, does their daily reps in one or more sittings, claims payouts. |
| **Scheduler** | The automated actor that normally records missed days, settles finished cohorts and opens each league's next cohort on schedule. Holds no special permission — anyone may perform the same steps (FR-030a). Never decides outcomes — only records ones already true (constitution II). |
| **Operator** | Publishes the SKR chip set: once at launch in Phase 1 (FR-055a), and every week from that week's rate in Phase 2 (FR-056). Also holds the contract's upgrade authority, usable only to fix defects (FR-007b). The only privileged actor, and only for these two things. |
| **Tester** | A member of the team, or a person who agreed in writing, using **Holdout Lab** (spec 004) to record and label sets so counting can be tested and improved. Never staked, never on a leaderboard, never connected to a wallet. |
| **Judge** | Scores the submission from its demo video, repository, pitch deck and APK. May install the submitted APK cold on any day from 13 October to 9 November 2026 and must immediately see the product working (constitution XII). The panel includes security researchers. |

---

## 5. User scenarios

### US-1 · First run

**Given** a person has installed the app and has a wallet
**When** they open it for the first time
**Then** they connect their wallet before anything else is available (FR-001a), are walked through the three-card tour (FR-067), and then see both Holdout lengths open to join, each open to them (FR-003), and can start today's Daily 25 in one tap.

### US-2 · Joining from the lobby

**Given** a cohort is open for joining
**When** the member joins
**Then** their stake is taken and held separately from any prize pool, they appear in the roster immediately, and they see a countdown to the join cutoff and to the start, the live roster count, and an unconditional way to leave until joining closes.

**And given** they leave before the join cutoff
**Then** the full stake is returned with no deduction of any kind.

### US-3 · A day's reps

**Given** a member's cohort is running and today's reps are not yet done
**When** they start a sitting
**Then** the app checks the environment and refuses to start until every check passes, telling them exactly what to fix; verifies a live human is present; counts reps that meet the form rules and silently discards ones that do not; banks every block of 10 verified reps on the device; and the moment the banked total reaches the day's requirement, records the day as complete.

**And given** they stop part-way — say after 10, then come back for 10 more, then 5
**Then** each sitting starts with the checks and the liveness step again, banked blocks carry across sittings for the whole day, and reps short of the next block of 10 when a sitting ends are not kept.

**And** the survivor counter, the prize figure and their own day counter update on completion.

**And given** they allowed notifications when they joined
**Then** the phone reminds them when the Holdout starts and, on any day not yet recorded, before that day's deadline — and stays quiet on a day already on chain; a day done on the phone but not yet on chain is reminded to be submitted (FR-068).

### US-4 · Missing a day

**Given** a member has not completed today's reps
**When** the day's deadline passes
**Then** their stake is forfeited, 30% is taken as fee and the remainder is added to that cohort's prize pool, their league progress resets, and they are told what happened and offered today's Daily 25 and the next cohort of the same league as the way back. Their daily streak is untouched (FR-052).

**And** they cannot submit any further completed day for that cohort, whether or not the Scheduler has run yet.

### US-5a · The Daily 25

**Given** any member with a connected wallet, on any day — in a cohort or not, staking or not
**When** they open it
**Then** today's challenge is there: 25 reps, the same verification and the same banking in blocks of 10 across sittings as a cohort day, no stake, no prize, no deadline beyond the day itself. Completing it extends their streak.

**And** a member already in a cohort does not train twice — the cohort day is the day's challenge.

**And** nothing is ever paid against a streak.

### US-6 · Staking

**Given** a member is joining a cohort of either league (both are open in this build — FR-003)
**When** they choose their stake
**Then** they choose one of the four chips — labelled by its USD tier ($10 / $25 / $50 / $100) and showing the exact SKR amount locked for this cohort, with the USD value marked approximate — and see the rule that a Holdout is counted **at 45° only — not in front, not straight beside — with full push-ups from the toes; knee push-ups do not count** (FR-021a), the daily obligation, the time until joining closes and until the start, the duration, and the cohort's current prize pool, and the plain statement that **if nobody drops out they get exactly their stake back**.

### US-7 · Watching the crowd thin

**Given** a member is in a running cohort
**When** other members forfeit
**Then** the survivor counter falls, the prize figure rises, and both are the largest things on the home screen. The member can see how many days remain, their own completion record, and their projected share.

**And** both figures move at the day's deadline itself, for every member at once — not whenever a forfeit happens to be recorded (FR-038a).

### US-8 · Settlement and claim

**Given** a cohort has reached its end and the member completed every day
**When** settlement occurs
**Then** every finisher settles at the same moment on the same figures, and each is invited to claim their stake plus their proportional share of the pool. Claiming is a deliberate act the member performs, not an automatic deposit.

### US-9 · A judge installs the app cold

**Given** a person installs the app on any day during judging (13 October – 9 November 2026) with no prior state
**When** they open it and connect a wallet
**Then** there is a real-time cohort running with members visibly thinning, today's Daily 25 is available to do immediately, and a short test Holdout is open to join, so the whole cycle — join, sittings, forfeit, settle, claim — can be run in about half an hour (FR-044a).

**And** the app, the README, the demo video and the pitch deck state plainly that the members of judging-period cohorts are scripted (constitution XI, FR-066).

### US-10 · Leaderboards *(league boards: Phase 2 · cohort roster: Phase 1)*

**Given** any member with a connected wallet, in a cohort or not
**When** they open a league's all-time leaderboard **[Phase 2]**
**Then** they see who is disciplined: members ranked by the share of their cohorts they survived in that league, each shown with that survival percentage, how many cohorts they have played, how many they cleared, and their current and best league streak — built entirely from on-chain progress.

**And** only members who have finished at least 3 cohorts in that league are ranked; everyone else is listed underneath as "not ranked yet", with their numbers visible and how many more cohorts they need. A member sees their own position or how far they are from being ranked.

**And given** a member is in a running cohort
**When** they open that cohort's roster
**Then** members still standing are listed first and forfeited members follow, ordered by how many days they lasted.

**And** no leaderboard shows or ranks any stake, prize or payout amount, and the daily streak appears on none of them.

### Edge scenarios

| # | Situation | Required behaviour |
|---|---|---|
| ES-1 | App crashes mid-sitting | Every banked block of 10 survives; reps since the last banked block are lost; the member continues from the banked total |
| ES-2 | Member stops part-way through the day | Banked blocks are kept until the day's deadline; the member may return any number of times; reps short of the next block of 10 in the ended sitting are not kept. There is no pause limit |
| ES-3 | Day's deadline passes mid-sitting, or with reps still missing | The day is not completed and banked reps for it no longer count; in a cohort the member is in breach. In the last 10 minutes the app warns before and during a sitting, but never refuses to start one |
| ES-0 | A person without a wallet opens the app | They are told a wallet is required and how to get one; nothing else — Daily 25 included — is usable until a wallet is connected (FR-001a) |
| ES-0a | A member with no test SKR or no fee SOL tries to stake, submit a completed day or claim | The app offers the faucet in place (FR-042a); if they have already used it today, it says when it is next available |
| ES-0b | The faucet cannot pay out | The app says so plainly, without blaming the member; the Daily 25 and browsing remain available, and any transaction already signed is unaffected |
| ES-3b | Member completes a cohort day with no connection | The app warns that the day counts only once it reaches the network before the deadline and shows the time left; it submits once a connection returns — by a single tap in Phase 1, automatically **[Phase 2]**. If it lands before the deadline the day counts; if not, the member is in breach exactly as if they had not finished (FR-024b) |
| ES-3a | Member's phone is lost, wiped or replaced mid-day | That day's banked progress is lost with the device; they can still complete the day from zero on another device before the deadline |
| ES-4 | A member in breach tries to submit | Rejected, regardless of whether the Scheduler has run |
| ES-5 | Nobody forfeits | Finishers get exactly their stake back, and nothing more; the app must have said this was possible up front |
| ES-6 | One finisher | Receives stake plus the entire pool |
| ES-7 | Nobody finishes | At settlement, the whole pool goes out: half (rounded down) to the developer destination, the rest to the operations destination; nothing carries over (FR-037) |
| ES-7a | Nobody finishes and the league has no other cohort | Settles exactly as ES-7: it needs no other cohort, so nothing waits on one (FR-037; superseded 2026-10-05 — it was refused until a lobby opened) |
| ES-7b | *(Superseded 2026-10-05: no cohort receives a carry-over any more.)* | — |
| ES-8 | Scheduler misses a run | Transfers and leaderboards lag; the survivor counter, projected pool and roster do not (FR-038a); no outcome changes |
| ES-9 | Member completes a day and immediately opens the app the next day | Sees the new day, not a stale completed state |
| ES-10 **[Phase 2]** | The rate moves sharply while a cohort is open for joining | That cohort's chip amounts do not change; every later joiner pays the same SKR per tier as earlier joiners. A new chip set, if triggered, applies only to cohorts created after it (FR-056) |
| ES-11 **[Phase 2]** | The rate source is unavailable at the weekly check | The current chip set stays in force; cohorts keep opening and joining continues uninterrupted (FR-057) |
| ES-12 | A join or a leave is signed before the join cutoff but lands after it | Decided by chain time: a late join is rejected and nothing is taken; a late leave is rejected and the member stays in the cohort. The app tells the member which happened, and in the final minutes before the cutoff warns that a transaction may land too late |
| ES-12a | A member opens a cohort during its locked hour | They see that joining has closed, the final roster count and the countdown to the start; no join or leave action is offered |
| ES-13 | A member finishes day 7 and holds a lobby place in the cohort starting at that same instant | Their day-7 completion must land before the instant; at the instant the old cohort stops accepting completed days and the new one's day 1 begins, with a fresh count of banked reps. The old payout is claimable once it settles, independently of the new cohort |
| ES-14 | A member forfeits their running cohort while holding a lobby place | The lobby place is untouched; it starts as normal unless the member leaves it |
| ES-15 | The automated opening of a league's next cohort runs late | The league has no open lobby until it runs; the next cohort's start instant is still the scheduled Monday, and the Daily 25 remains available (FR-053) |
| ES-16 | A contract defect is found while cohorts hold stakes | The fix is deployed to the same contract (FR-007b). Every existing cohort keeps its start, deadlines, chip set, members, stakes, completed days and pool; no member has to rejoin or resubmit |

---

## 6. Functional requirements

### Access

- **FR-001** A member is identified by their wallet.
- **FR-001a** Connecting a wallet is the first step in the app and is required before anything else is usable — the Daily 25, the streak, cohorts, rosters and leaderboards included. *(Amended 2026-10-09: the recorded walkthrough, once the one exception, is gone — FR-044.)* Before a wallet is connected, only the connect screen and, when the phone has no wallet app, help getting one are reachable. A person without a wallet is told plainly that one is needed and how to get one. The daily streak is kept on the device for the connected wallet.
- **FR-001b** *(added 2026-09-27)* **Devnet wallets only.** The app asks the wallet to authorize it on devnet and refuses to connect, or to have anything signed, when the wallet reports that it authorized another network — checked at connect and again inside every signing session, before a signature is requested. The member is told to switch the wallet to devnet and connect again, and the saved authorization is dropped so the next connect asks afresh. A wallet that reports no network (MWA 1.x) cannot be checked and is allowed.
- **FR-002** *(Amended 2026-10-10.)* Each league is joinable by anyone with a wallet and a stake whenever it has a cohort open, and it never closes to a member — after a clear or after a forfeit, in either league.
- **FR-003** *(Amended 2026-10-10.)* There is no ladder between the leagues. The 7-day and 21-day leagues are two separate products for different people, and both are open to every member at all times, unconditionally: neither requires a clear, a history or anything else earned in the other, and no gate, flag or qualification record exists for it anywhere in the system.
- **FR-003a** *(Removed 2026-10-10 — nothing is unlocked, so nothing is recorded; see FR-003.)*
- **FR-004** *(Removed 2026-10-10 — there is no unlock to keep; see FR-003.)*
- **FR-005** Daily 25 days completed outside a cohort grant no prize and no league progress — only the streak.
- **FR-006** A member may be in at most one running cohort at a time.
- **FR-006a** A member may additionally hold at most one lobby place (a `Pending` membership) at any time, including while in a running cohort, in either league. The lobby place is independent of the running cohort: finishing or forfeiting it changes nothing about the lobby place, which the member can still leave for a full refund until that cohort's join cutoff.
- **FR-006b** A lobby place is accepted only in a cohort that starts no earlier than the final deadline of the member's running cohort, so a member never carries two daily obligations at once. A 7-day member can hold a place in the cohort starting the instant theirs ends; a 21-day member cannot hold a place in a cohort starting while theirs still runs.

### Cohorts and the lobby

- **FR-007** A cohort has a fixed start, a fixed join cutoff, a fixed duration, a fixed daily rep requirement, a fixed day length, a fixed **exercise** and a fixed chip set, all set at creation and immutable thereafter. Only push-ups are accepted in this build; the parameter exists so a later exercise never has to change a running cohort or an existing account layout. *(2026-10-05, H35:)* a cohort also has a fixed **form**, copied from its league — 0, from the toes (Hard), or 1, knees allowed (Easy) — and the app refuses to start a sitting for a form it does not know.
- **FR-007a** Cohorts start weekly, on Monday at the 05:00 UTC boundary. Every league opens its next cohort for joining at the instant its current cohort starts, so each league has exactly one cohort open for joining at all times except the locked hour before each start. For the 21-day league this means up to three cohorts running at once. Opening is automated and needs no manual action (FR-043), and may be triggered by anyone once it is due (FR-030a).
- **FR-007b** The deployed contract remains upgradeable through the judging period, and an upgrade may only fix a defect. An upgrade never changes the rules, parameters or stored layout of any existing cohort, membership or progress record, and never moves a held stake or prize pool except through the rule-driven steps every member already relies on. The upgrade authority is named in the README as a known weakness, with its real fix — revoking it once the contract is final — beside it (constitution XI).
- **FR-008** A cohort accepts joins until its join cutoff, **1 hour before its start**, and starts for everyone at the same instant.
- **FR-009** Until the join cutoff, a member may leave and receive a full refund, unconditionally.
- **FR-010** From the join cutoff there is no way out and no refund; the roster is final and the daily obligation begins at the start. Whether a join or a leave landed before the cutoff is decided by chain time alone, never by when the member signed or by whether any automated process has run.
- **FR-011** Every cohort is staked. A cohort holds each member's stake separately from the prize pool at all times.
- **FR-011a** No maximum cohort size is enforced. The expected size is **up to 1,000 members**: recording forfeitures and settlement work in pages so that no single step depends on cohort size, and rosters and leaderboards remain correct at that size. The build is tested at 1,000 members; larger cohorts must still be correct, only untested.
- **FR-054** There are four stake chips, identified by USD tier: $10, $25, $50 and $100. The same tiers apply to both leagues.
- **FR-055** Every cohort copies the chip set current at its creation and keeps it for its whole life (FR-007). The contract accepts a join only for exactly one of that cohort's four SKR amounts, validates SKR only, and never reads a price.
- **FR-055a** *(Phase 1)* At launch the Operator publishes **one chip set**, converting each USD tier to SKR at that day's off-chain rate. Every cohort created in Phase 1 locks that set.
- **FR-055b** No cohort can be created before a chip set has been published. A cohort keeps the amounts it copied for its whole life (FR-007), so one opened in the gap between deployment and the first publish would hold four amounts of zero and take zero-stake joins until it settled, and nothing could correct it. Opening a cohort is permissionless (FR-030a), so this is enforced by the contract and not by the order the Operator happens to run things in.
- **FR-056** **[Phase 2]** Once a week, after a fixed day boundary (Monday 05:00 UTC), the Operator **re-prices the chip set** from the off-chain rate: each USD tier is converted to SKR at that rate and rounded to a readable SKR amount, and the new set is published — every week, with no drift threshold, even when the amounts come out the same. The re-pricing is the Operator's own scheduled command, never the always-on automation, run **every hour** in whatever time zone the Operator's machine keeps, and it publishes **at most once per weekly boundary**: the first run after Monday 05:00 UTC publishes, the rest of that week's runs do nothing, and a run that fails or finds no rate (FR-057) is retried by the next hourly run within the same week. A new set applies only to cohorts created after it; it never changes a cohort that already exists.
- **FR-057** **[Phase 2]** If no rate is available at the weekly check, the current chip set stays in force. Opening cohorts and joining never depend on a price being available.
- **FR-058** A member's stake is the SKR amount taken at join and never changes afterwards, whatever the rate does. Within one cohort, every member who chose the same tier holds an identical SKR stake; payouts remain proportional to stake (FR-032).
- **FR-059** Every figure the member will actually stake or receive is shown in SKR. USD tier labels and USD equivalents are marked approximate and never presented as what the member will receive (FR-039).

### The daily obligation

- **FR-013** Each day of the cohort, the day's required reps must be verified before that day's deadline — in one sitting or several (FR-024).
- **FR-014** The rep requirement **and the exercise** are read from the cohort, never from a constant.
- **FR-015** Unlimited retries within the same day.
- **FR-016** No grace day, no postponement, no carry-forward. One missed day ends the cohort for that member.
- **FR-017** A member is in breach the moment their current day's deadline passes without that day recorded as complete, independently of whether any automated process has run. Banked reps short of the requirement do not count.
- **FR-018** A member in breach cannot submit a completed day.
- **FR-068** *(added 2026-09-27)* **On-device reminders.** When a member joins a Holdout, the app schedules reminders on the phone itself: **one when the Holdout starts**, and **one before each day's deadline** while that day is not yet recorded complete — a quarter of the day ahead, at most 3 hours and at least 10 minutes, never more than the day — so 3 hours before a 24-hour day's deadline, 15 minutes before a one-hour day's, and as a 10-minute test day opens. Each names the Holdout (and labels the short test cohort, FR-044a), the reps still needed and the time left; none shows an amount. Reminders stop for a day once it is completed on the phone or recorded on-chain, and stop altogether when the member leaves, forfeits or the Holdout ends.
- **FR-068a** Reminders are **a convenience, never a promise**: scheduled from chain time (FR-017) with no server, no push service and no network use of their own; delivered by the phone's alarm service, which may hold them for a few minutes; re-scheduled whenever the app next reads the chain — so after the phone restarts, reminders resume once the app is opened. The member is asked for notification permission once — when they first join, or the first time the app finds them already in a Holdout — with the reason stated beside the stake button; declining changes nothing else, and a missed reminder never excuses a missed deadline.

### Session verification

- **FR-019** *(amended 2026-09-23; 2026-09-26: the background check is advisory)* A sitting cannot begin until the lighting, whole-body and camera-view checks pass; the still-background check is shown, with its fix, but never blocks the start; each failure states what to fix. No environment check uses audio. The checks run again at the start of **every** sitting, not only the day's first. The fourth check asks **whether the camera can see enough of the member to measure them**, not whether it is placed to one side: the phone may stand in front of them or beside them, the app names which it is, and only a view it cannot classify at all is refused. Alongside the checks the screen shows **where to lie** — the floor, found from the phone's own tilt, and an outline shaped for the placement in use, with one correction at a time. The outline disappears once the member is in position and does not return. **The fourth check accepts only the placements the stakes allow (FR-021a)** — 45° for every sitting *(since 2026-10-07; the Daily 25 also accepted head-on until then)*, square side-on and head-on for none — and its failure says where the phone goes. The view is classified from shoulder span against torso length in the picture; the head-on cut-off is 1.55, set from recordings in which 45° never exceeded 1.35 and head-on never fell below 1.73.
- **FR-020** *(amended 2026-09-22 — was palm liveness; see OD-3)* Every sitting begins once the environment checks pass **and** the member is holding the start position: the top of a push-up, arms extended and body straight, held about a second. Nothing is tapped, because by then the member is face-down on the floor. A spoken three-second countdown follows and counting begins. The member may cancel at any moment, and leaving the position before the countdown ends simply returns to the checks; nothing here ever touches banked reps. **This is a readiness check, not a liveness check** — it confirms a body in push-up position, not a live person and not the same person who set the phone up. What that costs is named in OD-3 and in the README.
- **FR-021** A rep counts only if it meets the form and timing rules, which are visual only; non-conforming reps are discarded with a hint rather than an error.
- **FR-021a** *(added 2026-09-23, revised 2026-09-24)* **Where the phone stands, and what is verified, depends on what is at stake.**
  | | Placements accepted | Verified |
  |---|---|---|
  | **Daily 25** (free) | **45° only, and only at 40–60°**, as an Easy Holdout *(since 2026-10-07; was head-on or 45°)* | what an Easy Holdout verifies. **Push-ups from the knees count** — a free habit for building strength (decided 2026-09-24), and the practice ground for a Holdout |
  | **Hard Holdout** (21-day, form 0) | **45° only** — phone on the floor ahead of the shoulder, turned to the member | depth, lockout, tempo, a body not folded at the hips, and **knees off the floor** (a leg bent a little in the air is allowed): a push-up from the knees is discarded with "Knees off the floor" |
  | **Easy Holdout** (7-day, form 1 — added 2026-10-05, H35) | **45° only, and only at 40–60°**: a sitting does not start outside that band | everything a Hard Holdout verifies, including body line, hip rise and the knees in view, except that **push-ups from the knees count** |

  Square side-on is accepted by neither. In every sitting, a rep done on the back or standing up is refused in silence *(added 2026-10-07; 002 FR-008a)*. Angles are read **in the picture**: at 45° elbow, body line and knee on the side nearer the lens, because the model guesses the hidden far side. The staked rule is stated in one sentence **before staking** — on the stake sheet beside the chips — and again by the camera check if the phone is placed anywhere else; the sentence has one source in the app (`Stakes.STAKED_RULE`), and the placements one source (`Stakes.allowedViews`).
- **FR-022** Camera frames are processed on the device and never transmitted or stored.
- **FR-022a** The app never requests microphone access. The absence of an audio anti-spoofing filter is named in the README as a known weakness, with its real fix beside it (constitution XI).
- **FR-023** Banked progress survives an app crash or restart.
- **FR-024** Verified reps are **banked in blocks of 10**. A day's reps may be spread across any number of sittings before its deadline; banked blocks carry across sittings for that day only. The day is complete the moment the banked total plus the current sitting's verified reps reach the day's requirement, so the final block may be smaller than 10 (10 + 10 + 5). When a sitting ends for any reason, reps short of the next block of 10 are discarded. There is no pause limit. *(2026-09-26)* A Daily 25 sitting running across the 05:00 UTC boundary rolls over: blocks banked before it stay on the day they were done, and the reps not yet in a block, with everything after, count for the new day.
- **FR-024a** Banked progress is kept **on the device only**. Nothing is written on-chain until the day is complete; a lost or wiped device loses that day's unfinished progress (ES-3a).
- **FR-024b** Sittings work **without a connection**: reps are verified and blocks bank offline. A completed cohort day counts only once its submission lands on-chain before the deadline, judged by chain time (FR-017). Before a cohort sitting starts offline, and again when a day completes offline, the app warns plainly that the day will not count unless it reaches the network before the deadline, with the time left. A completed day waiting to be submitted is kept on the device and the member never redoes any reps. *Phase 1:* once the app is online again the home screen shows "Done — tap to submit" and a single tap submits it. **[Phase 2, built 2026-10-05]** It is submitted automatically as soon as the app is open and has a connection, with "Done — waiting for network" shown while it has none. Every submission is signed in the member's wallet, so "automatically" means the wallet's approval opens by itself, without the tap; a member who declines there is not asked again until the connection drops and returns, and meanwhile the day shows "Done — tap to submit" and one tap sends it. Nothing on the device can extend or override a deadline. A Daily 25 day needs no connection at all.
- **FR-025** At most one completed day is recorded per member per day.

### Forfeiture

- **FR-026** On breach, the member's entire stake is forfeited. No partial credit.
- **FR-027** *(Amended 2026-10-05, H34, H35; re-split 2026-10-09.)* A forfeited stake is split by fixed program rule: **70%** to that cohort's prize pool, **5%** to its league's boost reserve, **5%** to its league's comeback reserve, **10%** to the developer destination and **10%** to the operations destination. Each part is rounded down and the operations part takes whatever rounding leaves, so the parts always add up to the stake. The two reserve shares stay in the cohort's vault until it settles (FR-037c).
- **FR-028** Forfeiture resets the member's league streak in that league and ends their participation in that cohort. The reset is written when that cohort settles, in cohort order, so it can never be undone by an earlier cohort settling later (FR-061a).
- **FR-030** The automated process that records forfeitures is safe to run twice, and safe to run late.
- **FR-030a** Recording forfeitures, settling a finished cohort, and opening a league's next cohort are **permissionless**: any wallet may trigger them once they are due. The caller cannot choose or influence any parameter, outcome, recipient or amount — each step only does what on-chain state and the schedule already require, and does nothing if it is not yet due or already done. A step that is not yet due may refuse plainly instead of silently doing nothing — settling a cohort before the previous cohort of its league has settled is refused (FR-061a) — and the caller simply runs it again once it is due. Publishing a chip set is the only Operator-restricted instruction; the contract's upgrade authority is governed by FR-007b.

### Settlement and claim

- **FR-031** Every member of a cohort settles on the same figures at the same moment.
- **FR-032** A finisher's payout is their stake plus `pool × (their stake ÷ total stake of all finishers)`.
- **FR-032a** Each finisher's share is rounded **down** to the smallest SKR unit. At settlement, the total rounding remainder — the pool minus the sum of all rounded-down shares, always fewer units than there are finishers — is sent to the fee destination and recorded on the cohort. After settlement the pool holds exactly the finishers' shares.
- **FR-033** The payout denominator is computed once and never recomputed.
- **FR-034** Payout is claimed by a deliberate member action, never deposited automatically.
- **FR-035** An unclaimed payout remains claimable indefinitely.
- **FR-037** If a cohort settles with no finishers, its whole prize pool goes out: half (rounded down) to the developer destination and the rest to the operations destination, recorded on the settled cohort. Nothing carries over into another cohort, so settling it needs no other cohort. *(Amended 2026-10-05, H34, H35; half each since 2026-10-09, was a third.)*
- **FR-037a** *(Superseded 2026-10-05 by FR-037: there is no carry-over, so no destination to choose, nothing held and nothing to deliver.)*
- **FR-037b** *(Superseded 2026-10-05: no cohort receives a carry-over.)*
- **FR-037c** *(Added 2026-10-05, H35.)* **League reserves are credited only at settlement.** When a cohort finalizes, the boost and comeback shares of every forfeit in it move from its vault into its league's program-owned reserve. Finalization runs in cohort order within a league (FR-061a), so the reserve a cohort can draw on depends only on earlier cohorts of its league, never on when automation recorded anyone's forfeit (constitution II).
- **FR-037d** *(Added 2026-10-05, H35.)* **Prize boost.** When a cohort with at least one finisher finalizes, its prize pool first receives `min(20% of its own pool, the league's boost reserve)` from that reserve; the shares are computed on the boosted pool, and the boost is recorded on the cohort. A cohort with no forfeits receives no boost. The boost is drawn before the cohort's own reserve shares are credited.
- **FR-037e** *(Added 2026-10-05, H35; refund raised to 20% 2026-10-09.)* **Comeback refund.** When settlement counts a member's forfeit, their league progress keeps that forfeited stake and its missed deadline (only the latest counted forfeit). When the same member is later counted as a finisher of a cohort of the same league that started after that deadline and no more than 14 days after it — days of the forfeited cohort's length, which is the league's — they are owed `min(20% of the forfeited stake, their new stake)`, and the record is used up. At finalization the cohort draws `min(total owed, the league's comeback reserve)`; each finisher's refund is paid with their claim, reduced pro rata if the reserve fell short.
- **FR-037f** *(Added 2026-10-05, H35.)* The developer and operations destinations are fixed at initialization and named in the README with what each is for (constitution XI).

### Display and honesty

- **FR-038** The home screen of a running cohort leads with the survivor count and the live prize.
- **FR-038a** The survivor count, projected prize pool, each member's projected share and the cohort roster treat a member as out **from the instant they are in breach** (FR-017), computed from on-chain state, whether or not the forfeit has been recorded. The projected pool includes the 70% of every breached stake not yet recorded. Recording the forfeit later changes none of these figures. Claimable amounts come only from settlement (FR-034), and league leaderboards count a forfeit once it is recorded (FR-065).
- **FR-039** Fiat amounts are always marked approximate and are never presented as what the member will receive.
- **FR-040** The app uses "prize" and never "winnings", "returns", "yield" or "earnings".
- **FR-041** Before joining a staked cohort, the member is told that a cohort with no forfeitures pays no prize: everyone who finishes gets exactly their stake back.
- **FR-042** The app states that it runs on a test network with a test token.
- **FR-042a** The app has an in-app **faucet**: one tap gives the connected wallet enough test SKR to stake the top chip ($100 tier) of **any** open cohort — including one that copied a larger chip set before a later re-price lowered the tiers, so the promise never depends on the order publishing and opening happened in, plus enough devnet SOL to pay the transaction fees of a full 21-day cohort cycle — join, every completed day, and claim. Each wallet may use it **once per day** (day boundary 05:00 UTC). No server is involved: the faucet is funded in advance for the whole live period — through the winners' announcement on 11 November 2026 — and needs no manual top-up. It is offered wherever a member lacks test SKR or fee SOL, and the test tokens it gives have no value.
- **FR-042b** The key that pays a zero-SOL wallet's first transaction ships inside the app, so anyone may extract it and empty the small fee float it holds. Onboarding therefore does not depend on that float surviving: **any wallet, including the automation, can restore it** from the faucet's own funds once it falls below half, without the Operator and without a server. Everything the faucet can spend in one day — top-ups, fee and rent reimbursements, and those refills together — is bounded by a **single daily allowance held on-chain**; while it lasts a drained sponsor is back within one automation pass, and once it is spent the faucet says plainly that it cannot pay today (ES-0b) and works again the next day. A restore that is refused — the float is already half full, or the day's allowance is gone — changes nothing and is simply tried again on the next pass. Neither the key nor the allowance can reach a stake, a prize pool or a carry-over.

### The Daily 25 and the streak

- **FR-048** The app offers **the Daily 25** every day to every member — 25 reps, same exercise, same verification — with no stake, no Holdout and no entry condition beyond a connected wallet (FR-001a). It is named and presented as free.
- **FR-049** Completing the Daily 25 extends the member's streak by one. A day passing with no completed day of any kind — challenge or cohort — breaks it; banked reps short of 25 do not keep it alive.
- **FR-050** A completed cohort day counts as that day's challenge. A member never trains twice in one day to hold both a cohort and a streak.
- **FR-051** The streak is cosmetic and personal. It unlocks nothing, gates nothing, appears on no leaderboard, and nothing is ever paid against it — which is why it needs no on-chain record and no economic anti-cheat.
- **FR-052** A forfeit does not break the streak. Losing a stake and losing a habit are different failures and the app must not conflate them.
- **FR-053** With no Holdout running, the Daily 25 is the home screen's primary action, and the Holdouts open to join are listed under it — each showing how many have joined, **what is at stake in it**, how long joining stays open, and — when the league's boost reserve holds more than 0 — "Prize boost: up to N SKR from this league's reserve when it settles" (FR-037d; never a promised amount). The same offer appears on the connect screen. The app is never empty and never says "come back Monday".
- **FR-067** A member who has just connected a wallet for the first time is shown a **three-card tour**: the Daily 25, what a Holdout is (stake, miss a day and it goes to the people still standing, finish and share), and how reps are counted (camera on the device, blocks of 10; every sitting with the phone at 45°; knee push-ups welcome in the Daily 25 and an Easy Holdout, a Hard Holdout from the toes only *(amended 2026-10-07; was the Daily 25 in front or at 45°)* — FR-021a). It can be skipped at any card and re-opened later from the app. A short explainer video joins the tour once it exists; until then the cards carry it alone, and no mock-up footage is ever used (FR-044).

### Leaderboards

*League leaderboards are **Phase 2** (§3a): FR-060…FR-060c, FR-063 and the display side of FR-061. The progress they read, FR-061's league streak, the cohort roster (FR-062), scripted labels (FR-064) and FR-065 are **Phase 1**.*

- **FR-060** **[Phase 2]** Each league has an **all-time** leaderboard built only from on-chain progress, with no server and no device-reported data; quarterly and yearly boards are deferred. The short test league (FR-044a) has **one combined board** across every test league, labelled as a short test cohort. For each member it shows, for that league: **survival rate**, **cohorts played**, **cohorts cleared**, and **current and longest league streak**.
- **FR-060a** **[Phase 2]** **Cohorts played** counts cohorts of that league the member has finished — cleared or forfeited. A running cohort counts once it resolves — a forfeit once it is recorded, a clear once the cohort settles; a lobby place the member left, or a cohort still `Open` or `Locked`, never counts. **Survival rate** is cohorts cleared ÷ cohorts played. A league's figures are shown **as of its last settled cohort**: settlement writes each member's progress page by page, so a cohort still being counted must contribute to nobody's figures until it is settled, or two members of one cohort would appear with different numbers of cohorts played (FR-031). At most one cohort per league is ever mid-count (FR-061a).
- **FR-060b** **[Phase 2]** A member is **ranked** once they have played at least **3** cohorts in that league. Ranked members are ordered by survival rate, highest first, compared exactly rather than as rounded percentages; ties go to more cohorts played, then to longer longest league streak, then to whoever reached those figures first — measured by the instant of the outcome itself, a cohort's end or the deadline a member missed, never by when automation recorded it, so no caller's timing can reorder two members (FR-030a). Members still tied on all of that are ordered by wallet address, ascending, so the order is the same on every device.
- **FR-060c** **[Phase 2]** Members with fewer than 3 cohorts played are listed below the ranked members as "not ranked yet", ordered by cohorts played and then survival rate, with their figures visible and the number of cohorts they still need. Survival rates are displayed as whole percentages.
- **FR-061** A league streak is the number of consecutive cohorts of one league a member has cleared — the leaderboard's measure of consistency. A forfeit in that league resets the current league streak to zero (FR-028); the longest league streak never decreases.
- **FR-061a** Progress is accounted in **cohort order**, never in the order automation happens to run in. Cohorts cleared and cohorts forfeited are plain counters and may be recorded whenever each outcome is known (FR-060a); the **current and longest league streak** are written only when a cohort is counted at settlement, and a cohort is counted only after the previous cohort of its league has settled. A member who clears one cohort, starts the next and forfeits it therefore ends with a streak of zero whichever of the two is processed first, and a late settlement can never restore a streak a later forfeit has already broken.
- **FR-062** Each running cohort has a roster view: members still standing first, then forfeited members ordered by how many days they lasted, most first. Members are identified as on leaderboards (FR-063).
- **FR-063** **[Phase 2]** Leaderboards identify members by Seeker ID when the wallet holds one, and by shortened wallet address otherwise (spec 003 FR-002). The name is for display only and never ranks or orders anyone (spec 003 FR-008). Leaderboards never show or rank any stake, prize or payout amount.
- **FR-064** Scripted members appear on leaderboards and rosters like any member and are labelled as scripted (FR-043).
- **FR-065** Leaderboards may lag the chain while data loads, but never show a figure that on-chain state does not support.

### Time

- **FR-045** Every deadline in a cohort is a single instant, identical for every member. No member-specific clock, offset or locale exists anywhere in the system.
- **FR-046** The app presents the obligation as time remaining, counting down, never as a calendar date. A member never has to work out which date a session belongs to.
- **FR-047** Before every sitting the app shows the reps still needed today and the time left. It never refuses to start a sitting because time is short; in the **last 10 minutes** before the deadline it warns clearly, before and during the sitting, that unfinished reps will not count.

### Demo integrity

- **FR-043** At any moment from submission (13 October 2026) through the winners' announcement (11 November 2026), a member installing the app finds at least one real-time cohort running and visibly populated, with no manual intervention for that whole period. Cohorts open on the weekly schedule (FR-007a) and are filled **by real members first**: scripted members join only until a cohort reaches its league's minimum — **20 members for a 7-day cohort, 12 for a 21-day one** — and their forfeits are spread across the cohort so the survivor count visibly falls. The scripted membership is disclosed in the app, the README, the demo video and the pitch deck (FR-066).
- **FR-043a** Scripted members are **ordinary wallets run by automation**, with no special treatment anywhere in the contract. Each joins with a real test-SKR stake on a normal chip, submits completed days through the same path as any member, and forfeits or finishes on a planned schedule. Their forfeits fund the prize pool real members win, and a scripted finisher takes its proportional share like anyone else. Every figure on screen therefore stays backed by on-chain state (FR-038a, FR-065). Their completed days are not camera-verified — this is named in the README as part of the client-signed trust gap (constitution XI). The set of scripted wallet addresses is public and fixed in the build, so labels (FR-064) need no server and anyone can check them. How many join is decided by the cohort's own membership: scripted wallets stop joining once the minimum of FR-043 is reached, and a scripted member already in a cohort never leaves to make room for a real one.
- **FR-044** *(amended 2026-09-26, 2026-10-09)* A full cycle — join, daily sessions, forfeiture, settlement, claim — can be watched without waiting days in the **demo video, sent as a link with the submission** (FR-066). It is a recording of the real app against the devnet contract, taken from **a cohort of the short test lobby** (FR-044a), which says on screen, throughout, that it is a short test cohort with compressed days. It is never a mock-up. **The app itself carries no walkthrough, no About section for judges and no link addressed to judges**: the submission is judged as the app's final state (spec 004). Members meet the product through the first-run tour (FR-067), and anyone can run the whole cycle on a short test Holdout (FR-044a).
- **FR-044a** *(added 2026-09-26, amended 2026-10-06, 2026-10-07, 2026-10-09)* **A short test lobby, devnet only, kept running, labelled, open to anyone.** The admin may create a test league (id 100 and up) with a compressed day on the devnet program built with the `test-leagues` feature: **three 10-minute days at 5 reps a day, a new cohort every 30 minutes**, joinable right up to each start (join lock 0), so one test cohort is always open for joining; the next opens as the current one starts. Its cohorts keep opening alongside the real 7-day and 21-day lobbies through the whole live period, so the devnet build always offers a test cohort and **a single person can run the whole cycle alone**. **One test league runs at a time:** the newest (highest id) is the lobby, and an older one opens no new cohorts and winds down (its open and running cohorts still run, settle and pay), so the combined test board's streaks follow the cohorts in order (FR-060); testers use it too, and the demo video is recorded from it. Only the admin can create the league; members join its cohorts like any Holdout. It MUST be labelled as a short test cohort — with its day length — everywhere it appears in the app: the stake sheet, the lobby, home, the roster and cohort detail. It MUST be disclosed in the README, the demo video and the pitch deck. It never touches the judged 7-day and 21-day leagues, which keep 24-hour days, and its progress never counts toward their streaks or leaderboards. A mainnet build never carries `test-leagues`, so no short cohort can exist there.
- **FR-066** The hackathon submission is made before **13 October 2026, 01:59 CEST** (12 October, 23:59 UTC; moved from 9 October, 08:59 CEST) — no late entries — and contains: a functional Android APK that installs and runs on a real device (not only a simulator); a GitHub repository with the full source that someone else can clone, build and run by following the README; a demo video of about **three minutes** showing the full cycle from the real app on devnet; and a pitch deck. Judges score completion from the demo video and technical depth from the repository's commit history. The submitted APK is assumed to be the build judges install, so app fixes after the deadline are not assumed to reach them; contract fixes can (FR-007b). Every submission material is accurate — scripted members, the test network and test token, and every weakness named in the README are never hidden in the video or deck — because false or misleading materials disqualify the entry.

---

## 7. Key entities

| Entity | Holds | Notes |
|---|---|---|
| **League** | Duration, daily reps, its form (Easy or Hard) | Two exist, both staked, each open to every member (FR-003). The Daily 25 is not a league. |
| **Cohort** | Its league, start, join cutoff (1 hour before start — joining and leaving both close), duration, daily reps, day length, its locked chip set (four SKR amounts), status, member count, prize pool, and — written once at settlement — finisher count, total finisher stake, the rounding remainder sent to the fee destination, and for a no-finisher cohort the pool sent to the fee destination. Its carry-over fields remain in the layout, always zero (2026-10-05) | Parameters immutable after creation (FR-007) |
| **Chip set** | The four USD tiers, the SKR amount for each, the rate it was priced at, and the boundary from which it applies | One current set shared by both leagues; replaced by the Operator every week from that week's rate (FR-056). Each cohort keeps a copy of the set current at its creation. |
| **Membership** | The member, the cohort, the SKR stake held, the chip tier chosen, days completed, status | The stake here is never pool money, and its SKR amount never changes after join (FR-058) |
| **Progress** | Per league: cohorts cleared, cohorts forfeited, current league streak, longest league streak, and when these figures last changed | Recorded from Phase 1 even though league leaderboards (FR-060) are Phase 2 — so they need no migration. The daily streak is separate, off-chain, and not part of this entity. |
| **Day progress** | Day index, banked reps (whole blocks of 10), verified reps in the current sitting, whether the day is complete | On the device only (FR-024a); a completed cohort day is recorded on-chain, a Daily 25 day never is |
| **Sitting** | One continuous run of checks, the held start position and counting | Any number per day; each one re-verifies (FR-019, FR-020) |

---

## 8. Behaviour: states and transitions

### 8.1 Cohort

`Open` → `Locked` → `Running` → `Settling` → `Settled`

| From → To | Trigger | Meaning |
|---|---|---|
| `[*] → Open` | the league's current cohort starts (FR-007a) | joining and leaving open; chip set and all parameters fixed |
| `Open → Locked` | join cutoff, 1 hour before start | joining and leaving both close; the roster is final |
| `Locked → Running` | start instant | every lobby membership becomes active |
| `Running → Settling` | final deadline | no further completed days accepted |
| `Settling → Settled` | settlement completes | finisher count and denominator written once |

`Settling` exists so that settlement can be resumed after an interruption without double-crediting anyone, and so no completed day can land against a day already scored.

`Locked` exists so that "joining has closed but the cohort has not started" is a legal, visible state: the roster and member count are final for the last hour, and no join or leave can land in the same instant the obligation begins. All time-triggered transitions are derived from chain time and take effect whether or not any automated process has run.

### 8.2 Membership

`Pending` → `Active` → one of `Forfeited` / `ReadyToClaim` → `Claimed`, with `Withdrawn` reachable from `Pending`

| From → To | Trigger | Guard |
|---|---|---|
| `[*] → Pending` | member joins | cohort `Open` (before its join cutoff); member holds no other `Pending` membership; cohort starts no earlier than the final deadline of any running membership (FR-006a, FR-006b) |
| `Pending → Withdrawn` | member leaves | cohort `Open` (before its join cutoff) |
| `Pending → Active` | start instant | — |
| `Active → Forfeited` | deadline passes with the day incomplete | — |
| `Active → ReadyToClaim` | settlement | every day completed |
| `ReadyToClaim → Claimed` | member claims | — |

Leaving the lobby is a transition out of `Pending`, not out of `Active`. A `Pending` membership is unaffected by any transition of the same member's running membership.

### 8.3 Sitting and day progress

A sitting: `Idle` → `Preflight` → `Liveness` → `Countdown` → `Counting`, then either back to `Idle` (sitting ended) or on to `Completed` → `Submitted`.

| From → To | Trigger | Effect |
|---|---|---|
| `Idle → Preflight` | member starts a sitting | shows reps still needed and time left; warns in the last 10 minutes (FR-047) |
| `Preflight → Idle` | an environment check fails | nothing banked changes |
| `Preflight → Liveness → Countdown → Counting` | checks pass, live human confirmed | — |
| `Preflight → Countdown` | four checks green **and** the start position held about a second | no tap; spoken countdown; leaving the position restarts the hold and nothing banked changes (FR-020) |
| `Liveness → Idle` | member cancels | nothing banked changes |
| within `Counting` | every 10th verified rep of the day | a block of 10 is banked on the device |
| `Counting → Idle` | sitting ends — member stops, app closes or crashes | reps short of the next block of 10 are discarded; banked blocks kept for the day |
| `Counting → Completed` | banked + current verified reps reach the day's requirement | the day is done |
| `Completed → Submitted` | the completed cohort day is confirmed on-chain | day counter advances; a Daily 25 day is recorded on the device instead |

Sittings may repeat without limit until the day's deadline, at which point that day's banked progress stops counting.

### 8.4 Invariants

- **INV-1** A cohort's vault always holds exactly its held stakes, plus its prize pool, plus its reserve shares not yet credited, plus comeback refunds funded and not yet claimed. A league's reserve vault always holds exactly its boost reserve plus its comeback reserve.
- **INV-2** Only forfeited stakes ever enter a prize pool: the cohort's own, or a boost drawn from its league's reserve, which holds nothing but shares of earlier forfeits of that league.
- **INV-3** A member's completed days never exceed the days their cohort has reached, counting the day in progress: at the cohort's first second at most one day can be complete, and never more than the cohort's duration.
- **INV-4** Per member: at most one membership in a `Running` cohort, at most one `Pending` membership, and no `Pending` membership whose cohort starts before the final deadline of the member's running one — so no two daily obligations ever overlap.
- **INV-5** The payout denominator is written exactly once.
- **INV-6** After a cohort settles, the only change possible anywhere in it is a claim.
- **INV-7** Recording forfeitures and settling are both safe to repeat.
- **INV-8** A member in breach cannot submit, regardless of automation state.
- **INV-9** A member's league streak depends only on the order of their own cohorts, never on the order in which forfeits and settlements are processed.
- **INV-10** *(Added 2026-10-05.)* A league reserve changes only when one of its cohorts finalizes, so what any cohort draws from it is independent of when forfeits were recorded.

---

## 9. Open decisions

All resolved as of 2026-09-15. Kept here as the decision record.

### OD-1 · How a day's boundary is defined — **RESOLVED 2026-09-15**

**Day N runs from `cohort_start + N × day_length` for every member alike.** There is no timezone anywhere in the system; the contract derives every deadline from chain time and asks nothing of any client.

The boundary instant is **05:00 UTC** — chosen for Europe and the Americas together: Warsaw 07:00 and Kyiv 08:00, both before the common morning session; New York 01:00, dead of night; Los Angeles 22:00, which loses the last two evening hours and is the deliberate cost. Europe and US West have no mutually quiet hour — they are ten hours apart — so the only real choice was which side pays, and a boundary landing mid-morning in Kyiv would have been worse than one landing at 22:00 in Los Angeles.

The boundary instant is a **property of the cohort**, so regional cohorts later are configuration, not redesign.

Per-member local midnight, and the bounded-oracle pattern that would make it safe, are recorded in `docs/holdout_scope_and_state_machines.md` D-B for a global launch. Evaluating deadlines in a backend was considered and rejected there.

### OD-2 · Stake chip amounts in SKR — **RESOLVED 2026-09-15: SKR chips tracking USD tiers, checked weekly, locked per cohort**

Four chips tracking $10 / $25 / $50 / $100. The contract accepts and validates SKR amounts only and never reads a price (constitution III). Once a week the Operator re-prices the chips from the off-chain rate and publishes the new set; if no rate is available, the current set stays. Each cohort locks in the chip set current at its creation, so two members of one cohort on the same tier always hold the same SKR stake. See FR-054…FR-059, ES-10, ES-11. **Phasing (§3a):** Phase 1 publishes one chip set at launch (FR-055a); the weekly check and re-pricing are Phase 2.

The rate source and the Operator's re-pricing are trusted off-chain inputs and are named as such in the README (constitution XI).

### OD-3 · Audio in rep verification — **RESOLVED 2026-09-15: dropped, both**

The audio environment check and the audio rep condition are both removed. The environment check has four items, rep rules are visual only, and the app never asks for the microphone (FR-019, FR-021, FR-022a). The lost anti-spoofing filter is accepted for a devnet build with a test token and is documented in the README.

**Amended 2026-09-22, after the first sitting on real hardware: palm liveness is dropped too.** The palm asked someone already face-down on the floor to lift a hand off it — the same mistake as the "I'm ready" button it followed, and found the same way, by holding the phone. The start position replaces it (FR-020): the top of a push-up, which the pose model already sees, held for about a second.

This leaves the build with **no liveness check at all**. Nothing confirms a live person rather than a photograph, and nothing confirms the member who banks the first ten reps is the one who set the phone up. That is accepted on devnet with a test token and named in the README.

**The real fix is the one this decision already chose:** audio. Reinstating the audio check and the audio rep condition together restores liveness and anti-spoofing in one move, and it is a later phase of development, not a hackathon task. *Sound the app plays* is unaffected by any of this and is used freely — the countdown and the banked-block chimes are spoken and played aloud, because a member on the floor cannot read the screen. What is forbidden is the microphone, and `RECORD_AUDIO` remains undeclared.

### OD-4 · How a judge experiences a full cycle (FR-044) — **RESOLVED 2026-09-15: recorded walkthrough; revised 2026-10-09: the demo video, sent as a link**

Real-time cohorts run continuously through judging and until winners are announced (FR-043), populated by disclosed scripted members (FR-043). The full cycle is shown in the demo video, sent as a link with the submission, recorded from the real app on a cohort of the disclosed short test lobby — its settlement and a real claim (FR-044, FR-044a, amended 2026-10-09). The app carries no walkthrough. The judged 7-day and 21-day leagues run on real 24-hour days; the short test lobby exists on devnet only, keeps opening cohorts for anyone through the live period, and is labelled wherever it appears.

### OD-6 · One clock or two — **RESOLVED 2026-09-15: one clock**

The streak day resets on the same instant as the cohort day. There is exactly one notion of "today" in the app, for money and for habit alike.

Local-midnight streaks would have been safe — the streak is cosmetic and off-chain, so none of the reasons timezones were removed from the contract apply to it — but two notions of "today" in one app is a support burden that outweighs the gain. Revisit at launch, when the habit tier becomes the growth surface.

### OD-5 · What happens to an unclaimed pool when nobody finishes (FR-037) — **RESOLVED 2026-09-15: split 50/50; REVISED 2026-10-05: all to the fee destination; REVISED 2026-10-09: half to the developer, the rest to operations**

At settlement, half goes to the fee destination and half carries over into the prize pool of the next cohort of the same league that is still open for joining. If none is open, settlement waits: it is refused until someone opens that cohort, which anyone may do (revised 2026-09-19 — holding the carry-over on the settled cohort let a caller's timing decide whether it ever reached a prize pool). See FR-037…FR-037b, ES-7…ES-7b.

**Revised 2026-10-05:** the whole pool goes to the fee destination and nothing carries over, so settlement never waits on another cohort (FR-037).

**Revised 2026-10-09:** the fee now has two destinations, so the whole pool goes out half (rounded down) to the developer destination and the rest to the operations destination; still nothing carries over (FR-027, FR-037, FR-037f).

---

## 10. Acceptance criteria

The build is done when all of the following are demonstrable on devnet:

1. A member joins a 7-day cohort, completes 7 days of verified sessions and claims a payout. A member with no 7-day history joins a 21-day cohort.
2. A member joins a staked cohort, misses a day, and their stake appears in the pool minus exactly 30%.
3. A cohort settles once, and two finishers with equal stakes receive byte-identical payouts. In a cohort whose shares do not divide exactly, every share is rounded down, the remainder reaches the fee destination at settlement, and the sum of all claims equals total finisher stake plus pool minus that remainder, to the unit.
4. A finisher claims and the funds arrive.
5. Killing the automated process for a full day changes no outcome: on restart the same members are forfeited, the same payouts result, and no member in breach was able to submit in the interim. With the Scheduler still stopped, an unrelated wallet can record the forfeits, settle the cohort and open the next one, producing exactly the same state; the same wallet calling any of them early, twice, or with different arguments changes nothing.
6. The published settlement vectors all pass as tests.
7. All invariants INV-1…INV-9 hold under a randomised sequence of joins, sessions, misses and claims, including a cohort of 1,000 members whose forfeitures and settlement are processed across multiple pages — with pages run out of order and repeated — and whose roster entries (and, in Phase 2, leaderboard entries) match on-chain state.
8. An app installed fresh, with no state, asks for a wallet before showing anything else, and offers nothing without one but help getting a wallet (FR-001a). Once a wallet is connected it shows a live populated cohort holding at least its league's minimum of members. Every member counted in that cohort — scripted or not — has a real on-chain membership and stake, every scripted member is labelled and appears in the published list of scripted wallets, and a real finisher's payout includes forfeits by scripted members exactly as by anyone else. With the Scheduler stopped, one second after a day's deadline every device shows the survivor count, projected pool and roster already excluding the members who missed that day; recording their forfeits afterwards leaves every displayed figure unchanged.
9. No instruction reads a price. The only place a rate is used is the Operator's off-chain weekly chip check; no fiat figure is an input to any on-chain branch, amount or validation.
10. No camera frame leaves the device, verified by inspecting outbound traffic during a sitting.
11. A staked cohort settles with no finishers: its whole pool goes out, half (rounded down) to the developer destination and the rest to the operations destination, nothing reaches any other cohort, and it settles the same whether or not its league has another cohort and whoever calls it.
12. **[Phase 2]** The weekly re-pricing publishes a new chip set from that week's rate, and a second run in the same week publishes nothing: cohorts created before it still accept only their original SKR amounts, a cohort created after it accepts only the new ones, and two members of one cohort on the same tier hold identical stakes. With the rate source switched off, the current set keeps working.
13. A member in a running 7-day cohort takes a lobby place in the next 7-day cohort, finishes the first, and is active in the second from the instant the first ends. A join and a leave landing one second before a join cutoff both succeed, with a full refund on the leave; both are rejected one second after it, and the cohort shows as `Locked` until it starts. A member in a running 21-day cohort is refused a lobby place in a cohort that starts before theirs ends.
14. **[Phase 2]** On the 7-day leaderboard, derived from on-chain state with no server contacted: a member with 4 cleared of 4 played (streak 4) ranks above one with 9 cleared of 10 played; a member with 3 of 3 ranks below 4 of 4 on the cohorts-played tie-break; a member with 2 of 2 is listed under "not ranked yet" needing 1 more cohort; a forfeit drops a member's current streak to 0 and their survival rate accordingly while their best streak is kept. A member whose cohort is still running or locked has it counted in neither figure. No leaderboard shows an SKR or fiat amount or anyone's daily streak, and scripted members are labelled.
15. A member completes a 25-rep cohort day in three sittings of 10, 10 and 5, each starting with the environment checks and the held start position; the day is recorded on-chain only after the fifth rep of the last sitting. A sitting stopped after 17 reps keeps 10 banked and discards 7. Killing the app mid-sitting keeps every banked block. A sitting started 8 minutes before the deadline is allowed, with a visible warning; if the deadline passes with 20 banked, the day is incomplete and the member is in breach.
16. A brand-new wallet with no SOL and no test SKR uses the in-app faucet once and can then stake the top chip, submit every completed day of a 7-day cohort and claim, without any other source of funds. A second faucet request from the same wallet before the next 05:00 UTC boundary is refused with the time it becomes available; no server is contacted. After a stranger empties the bundled key's fee float, the next brand-new wallet succeeds again once any wallet — the automation included — has restored it, with no server, no Operator and nothing beyond the day's on-chain allowance spent (FR-042b).
17. *(amended 2026-09-22 with FR-020.)* With the four checks green, a sitting begins only once the member holds the top of a push-up for about a second — no tap — and the countdown is spoken aloud, because the screen is out of sight. Dropping out of the position before the countdown ends restarts the hold; the member can wait as long as they like or cancel, and the day's banked reps are unchanged throughout.
18. With the phone in airplane mode, a member runs a cohort sitting, sees the offline warning, banks blocks and completes the day. Reconnecting before the deadline and tapping submit (automatically, without the tap, **[Phase 2]**) lands the day and it counts; in a second run, reconnecting one minute after the deadline submits nothing that counts, and the member is in breach.
19. The submission is complete before 13 October 2026, 01:59 CEST (FR-066): the submitted APK installs and runs on a Seeker and on an ordinary Android phone; someone who has not worked on the project clones the repository and builds the app and the contract by following the README alone; the demo video runs about three minutes and shows a join, a sitting, a forfeit, settlement and a real claim from a cohort of the short test lobby, labelled on screen as one (FR-044); and the app, README, demo video and pitch deck all disclose the scripted members and the test network.
20. *(added 2026-09-27.)* A member who allowed notifications joins a Holdout and receives a reminder when it starts; on a day they have not done, a reminder arrives before the deadline; on a day already done, none does; after leaving, none does. A member who declined notifications joins, sits and submits exactly as before (FR-068, FR-068a).
