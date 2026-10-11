# Holdout — decision record

**Companion to `specs/001-holdout-hackathon/spec.md` and `.specify/memory/constitution.md` (v2.3.0).** The spec says *what to build*. The constitution says *what may never be violated*. This file says *why it is that way, what was tried and rejected, and what is still open* — so settled questions are not reopened by accident.

**Precedence when documents disagree:** constitution → spec → this file. The specs (`specs/001-holdout-hackathon` with 002, 003 and 004) are the only description of what is built. `docs/holdout_screen_map.md` and `docs/holdout_lab_screen_map.md` describe the screens as built, `docs/design/` the look, and `fixtures/` the numbers the program must reproduce; they describe and decide nothing. The hackathon PRD (v2.7, 18 September) is archived at `docs/archive/holdout_v1_prd_hackathon-build_2026-09-18.md` and is not maintained. The scope-and-state-machines document was retired on 2026-10-10; its one surviving note, the bounded-oracle pattern for per-member local midnight, moved to research R7.

**Rule of thumb:** before proposing a change to the mechanic, search this file. Most of it has been proposed before, and several things were adopted, reversed and adopted again in a single day.

**Archive:** the full product-vision decision record (three-league ladder, USD chip ladders, free cohorts, simulation findings, positioning, rollout plan, success metrics) and the original full-vision PRD were removed from the tree on 2026-10-10 and live in git history: `git show ba1e06c:docs/archive/holdout_decisions_pre-hackathon-spec_2026-09-15.md` and `git show ba1e06c:docs/archive/holdout_v1_prd_full-vision_2026-09-15.md`. §2 keeps what still holds from them and §3 what was dropped. **In §3, "archived Dn" and "PRD §…" point into those two files, and "scope doc …" into the retired scope document (`git show 7dc976a:docs/holdout_scope_and_state_machines.md`).**

---

## 1. Decisions in force — hackathon build

### H1. The build is a hackathon submission, not a launch

**Decided:** devnet only, mock SKR mint, feature-frozen **28 September** when the first in-app cohort opens; submission **13 October 2026, 01:59 CEST** (moved by the organisers from 9 October 08:59 CEST; amended 2026-10-09). Anything not required by a scenario in the spec is out of scope, and scope grows only by amending the spec. The submission's contents, the judging dates and the live period are in H25.

**Why:** the official CLOCK IN rubric has four equal parts:
- stickiness and product fit with the Seeker community (habits and daily engagement);
- user experience;
- innovation;
- presentation and demo quality.

Judges score completion from the demo video and technical depth from the GitHub commit history. User experience and presentation together are half the rubric; pool economics score approximately nothing. A feature earns its place only if it appears in the video or is load-bearing for something that does.

**Source:** constitution Hackathon Constraints and Governance; spec §1.

### H2. A free daily habit beside two staked leagues *(ladder removed 2026-10-10, H37)*

**Decided:**

| Layer | What it is |
|---|---|
| **Daily challenge** | 25 push-ups, every day, free, for everyone. No cohort, no stake, no prize. Builds a cosmetic streak. |
| **7-day league** | Staked, 25 reps/day, Easy. **Never closes** — not on a clear, not on a forfeit. |
| **21-day league** | Staked, 25 reps/day, Hard. ~~Open to every member, always.~~ **Amended by H38 (2026-10-10):** open at all times to every member whose wallet holds a Seeker Genesis Token. |

~~Clearing a 7-day cohort permanently records the 21-day unlock, behind a gate flag that is off for the hackathon.~~ **Superseded by H37 (2026-10-10):** there is no ladder, no unlock and no gate flag; both leagues are open to every member at all times. *(Amended by H38, 2026-10-10: "every member" means every member whose wallet holds a Seeker Genesis Token; the daily challenge stays open to everyone.)*

**Why:** a free loop anyone can run on day one, forever, is a far stronger stickiness story than a cohort you must pay to enter. Neither league ever closes to a member; after a forfeit the way back is the same league's next cohort (H37). *(The gate reasoning that stood here — off for judges, kept as a flag to show the designed ladder — is withdrawn by H37.)*

**Reverses:** the archived D3 (staked qualifier closes on a clear). **Supersedes:** archived D1 (three leagues) and D2 (free clear qualifies).

**Source:** spec §2, FR-002…FR-005; scope doc D-I.

### H3. No free cohorts of any kind

**Decided (clarified 2026-09-15):** every cohort is staked. The only no-stake path is the daily challenge. The `Finished` membership state — which existed only for free-cohort finishers — is removed.

**Why:** the constitution already put free cohorts out of scope, but the spec still described a free league in the first-run flow, the forfeit flow and settlement, plus a state that would have needed its own screen and tests (constitution IX) while being unreachable.

**What was given up:** the free qualifying route, and with it the free-entry lane that made the product legal where paid entry is not. A launch concern, not a hackathon one. The "does this even work for my body, my camera, my room" objection is still answered for free by the daily challenge, which is the same session.

**Supersedes:** archived D12 (free cohorts beside staked ones) and the free half of D14 (free week as comeback path). After a forfeit the app now offers today's daily challenge and the next cohort of the same league (H37).

**Source:** spec US-1, US-4, US-8, FR-011, §8.2.

### H4. The streak is cosmetic, off-chain, and survives a forfeit

**Decided:** completing any session — daily challenge or cohort session — extends the streak; a day with no completed session breaks it. A cohort session counts as the day's challenge, so nobody trains twice. The streak is personal: it unlocks nothing, gates nothing, appears on no leaderboard (H17) and is never paid against. **A forfeit does not break it.**

**Why:** the moment a streak gates or pays anything, free sessions need economic anti-cheat, and free sessions are exactly where there is no stake to make cheating expensive. Losing a stake and losing a habit are different failures; conflating them punishes one bad day twice and pushes out exactly the people the free tier retains.

**Source:** spec FR-048…FR-053; constitution Hackathon Constraints.

### H5. One clock: 05:00 UTC, cohort-anchored

**Decided (OD-1, OD-6):** day N of a cohort runs from `cohort_start + N × day_length`, identically for every member. The boundary is **05:00 UTC**. No timezone, offset or locale exists anywhere in the system. The streak day resets on the same instant — one notion of "today" for money and habit alike. Members see a countdown, never a calendar date. The boundary instant is a property of the cohort.

**Why 05:00:** Europe and US West have no mutually quiet hour. At 05:00 Warsaw is 07:00 and Kyiv 08:00, both before the morning session; Los Angeles loses the last two evening hours (22:00). At 06:00 a Kyiv 08:00 workout would land on the previous day — a far more common and more confusing failure.

**Rejected — evaluating deadlines in a backend:** it knows nothing the contract does not; a backend that decides outcomes can mark everyone forfeited and one account a finisher, draining a pool without touching a withdrawal function; it enters the hot path of every session; a dead backend at a boundary destroys the only record of who made it; and it turns the on-chain story into a payment rail. If per-member local midnight is ever required, the safe pattern is a **bounded oracle** (backend may only assert a miss inside the UTC+14…UTC−12 window the contract computes itself).

**Rejected — local-midnight streaks:** safe, because the streak is cosmetic, but two notions of "today" in one app is a support burden that outweighs the gain. Revisit at launch.

**Known failure:** 05:00 UTC serves the Atlantic and fails Asia-Pacific (Mumbai 10:30, Tokyo 14:00). If that market turns out to matter, the fix is a second reference instant at **18:00 UTC** — configuration, not redesign.

**Source:** spec OD-1, OD-6, FR-045…FR-047; constitution VI; scope doc D-B.

### H6. Forfeiture is derived, never declared

**Decided:** a member is in breach the moment `now > deadline_of(days_completed)`, computable from on-chain state alone. Automation only materialises breach into a transfer. A member in breach cannot submit, whether or not the Scheduler has run. Recording forfeitures and settling are idempotent, chunkable and permissionless (H19); two callers at once are harmless. A missed run delays money and leaderboards, never outcomes.

**Displays follow the same rule (clarified 2026-09-15):** the survivor counter, projected pool, projected shares and cohort roster count a member as out **from the instant of breach**, before anyone records the forfeit. At 05:00 UTC every device shows the same thinner crowd; recording the forfeit later changes no displayed figure. Only league leaderboards wait for the recorded forfeit, and claimable money only ever comes from settlement.

**Rejected:** moving counters only when forfeits are recorded — the crowd would look bigger than it is for as long as the Scheduler is slow, and different members could see different numbers. Splitting the two (counter immediate, prize on record) — two numbers on one screen telling different stories.

**Source:** spec FR-017, FR-018, FR-030, FR-038a, ES-8, INV-7, INV-8, acceptance criterion 8; constitution II, VIII; scope doc D-F.

### H7. SKR only — no USDC, no swap

**Decided:** stakes, pool, payouts and the 20% fee are all SKR. Devnet with a mock mint. No USDC leg, no Jupiter swap, no slippage handling.

**Why:** the largest single subtraction available for the build — the swap CPI, slippage revert path, USDC vault and fee conversion all disappear.

**The cost, stated honestly:** "finish and you get exactly your stake back" is true **in SKR**, not in dollars. A 21-day cohort during a drawdown returns the same number of tokens and less money. This belongs in the README and the deck as a known property. On devnet it does not bite.

**Supersedes:** PRD §6.4 (USDC pool), §9.1 (`JupiterSwapRouter`), archived §5.4 in-app swap revenue.

**Source:** spec §2; constitution IV; scope doc D-C.

### H8. Stake chips: USD tiers, priced in SKR, checked weekly, locked per cohort

> **Revised 2026-10-06 (H36):** the 20% drift threshold below is gone — the Operator re-prices the chips **every week** from the Jupiter rate, even when the amounts come out unchanged, at most once per Monday 05:00 UTC boundary. Locked per cohort as before.

> **Phasing (H23):** Phase 1 publishes one chip set at launch; the weekly check and re-pricing below are Phase 2.

**Decided (OD-2, clarified and revised 2026-09-15):**

- Four chips identified by USD tier: **$10 / $25 / $50 / $100**, the same tiers in both leagues.
- The contract accepts and validates **SKR amounts only** and never reads a price.
- **Once a week**, at a fixed 05:00 UTC boundary, the Operator compares the current chip set against an off-chain rate. Only if the chips have drifted **more than 20%** from their USD tiers does it publish a new set, rounded to readable SKR amounts. If no rate is available, the current set stays.
- **Every cohort copies the chip set current at its creation and keeps it for life.** A new set applies only to cohorts created after it.
- A stake never changes after join. Everything the member stakes or receives is shown in SKR; USD tier labels and equivalents are marked approximate.

**Why:** members think in dollars; the contract must not. Locking chips per cohort guarantees that two members of one cohort who chose the same tier hold identical SKR stakes, and it removes the race where a join signed just before a re-price lands after it.

**Rejected, in order:**

1. **Fixed SKR chips (100 / 250 / 500 / 1,000 SKR)** — the original D-C recommendation. Simplest, but the dollar meaning of a chip drifts without bound.
2. **Daily re-pricing applied to every open cohort** — members who joined the same cohort on different days would hold different SKR for the same tier.
3. **Converting at each member's join** — same mismatch, the contract can no longer validate chips, and joining fails whenever the price feed is down, including for a judge.
4. **Re-pricing on every drift, however small** — churns the chip set for no visible benefit; the 20% threshold keeps chips stable while staying recognisably "$10".

**Consequences:**

- Constitution **III amended in v1.1.0**: a rate may be used in exactly one place — off-chain Operator tooling that prices chips before a cohort is created. **V amended**: chip amounts are cohort parameters.
- **Softens D-C's UI rule** ("quote SKR everywhere and never a dollar figure"): USD tier labels are now shown, always marked approximate, never as what the member receives.
- The rate source and the Operator's re-pricing are **trusted off-chain inputs** and are named in the README (constitution XI).
- Cohorts in the same league can have different SKR amounts for one tier. Harmless — each cohort has its own pool.

**Supersedes:** archived D5, D6, D7 (USD chip ladders of $5…$200 per league).

**Source:** spec §2, FR-054…FR-059, ES-10, ES-11, acceptance criterion 12; constitution III, V.

### H9. Fixed weekly cohorts; joining and leaving close 1 hour before the start

**Decided (clarified 2026-09-15, revised the same day):**

- Cohorts start **weekly, Monday 05:00 UTC**.
- Every league opens its next cohort **at the instant its current cohort starts**, so each league has exactly one open lobby at all times except the locked hour before each start. Opening is automated.
- **Joining and leaving both close at the join cutoff, 1 hour before the start.** Until then, leaving refunds in full, unconditionally. From the cutoff the roster is final and there is no way out; the obligation begins at the start.
- The cohort is **`Locked`** for that hour: the app shows that joining has closed, the final roster count and the countdown to the start.
- Whether a join or leave landed in time is decided by **chain time alone**. A transaction signed before the cutoff but landing after it is rejected; the app says which happened and warns in the final minutes before the cutoff.
- The 21-day league runs up to three cohorts in parallel.

**Why fixed cohorts at all (archived D11, still in force):** everyone in, everyone out, one settlement and no moving denominator; it creates the "we" — *312 of us started, 189 are left* — that a rolling pool cannot display; and it removes the unfairness of late finishers collecting against a different pool state.

**Why a locked hour:** it makes "joining closed, not yet started" a legal and visible state rather than a bug, freezes the roster so the starting number on screen is real, and keeps any join or leave from landing in the same instant the obligation begins.

**Why 1 hour and not 24:** the 24-hour cutoff (scope doc §3.1, PRD §5.2) existed mainly to host the minimum-size check, which is cut (H15). Without it, a full closed day only costs members who decide late. One hour keeps every benefit of `Locked` at almost none of that cost.

**Rejected the same day:** joining and leaving open until the start instant with no `Locked` state. Briefly adopted, then rolled back — the roster could change up to the last second and there was no final number to show before the start.

**Supersedes:** scope doc §3.1 (24-hour cutoff); PRD §5.2 (join closes Sunday 00:00 local, starts Monday 00:00 local); archived rollout note "weekly starts per league replacing cadence-equals-length".

**Source:** spec FR-007, FR-007a, FR-008…FR-010, ES-12, ES-12a, ES-15, §8.1, §8.2, acceptance criterion 13.

### H10. One lobby place alongside a running cohort

**Decided (clarified 2026-09-15):** a member may hold **at most one lobby place** (`Pending` membership) at any time, including while in a running cohort, in either league. It is **unconditional**: finishing or forfeiting the running cohort does not touch it, and it can be left for a full refund until its cohort's join cutoff. A lobby place is accepted only in a cohort that starts **no earlier than the running cohort's final deadline**, so no member ever carries two daily obligations.

**Why:** a 7-day cohort ends at the same instant the next one starts. Without a lobby place alongside a running cohort, every finisher — at their most motivated — sits out a week, and cohort #3's finishers could not appear in cohort #4 during filming.

**Why unconditional is cheap when the PRD version was not:** PRD §5.6a's pre-join depended on clearing the current cohort and was refunded automatically on forfeit, which put refund transfers inside the forfeiture run (scope doc E8, compute budget unverified). With no gate, a lobby place depends on nothing, so there is nothing to resolve.

**Still cut:** conditional pre-joining (constitution Hackathon Constraints, amended v1.1.0).

**Guard that matters:** a 21-day member cannot take a place in next Monday's cohort, which would start while theirs still runs. INV-4 covers it.

**Source:** spec FR-006, FR-006a, FR-006b, ES-13, ES-14, INV-4.

### H11. Settlement: one pool per cohort, proportional payout, manual claim

**Decided:**

- **30%** of each forfeited stake is fee; the remaining 70% enters that cohort's prize pool (**revised 2026-10-05 from 20/80, H34**). Only forfeited stakes ever enter a pool; held stakes are segregated at every instant.
- Every finisher settles at the same moment on the same figures. Payout = `stake + pool × (stake ÷ total finisher stake)`. The denominator is written exactly once.
- **Each share rounds down** to the smallest SKR unit. At settlement the **rounding remainder** — always fewer units than there are finishers — **goes to the fee destination** and is recorded on the cohort, so the pool then holds exactly the shares (clarified 2026-09-15).
- **Claim is a deliberate member action**, never an automatic deposit, and stays claimable indefinitely.
- If nobody forfeits, finishers get exactly their stake back, and the app says so before they join.

**Why the remainder goes to the fee destination:** it can be computed exactly at settlement, since every finisher's stake is known then; it needs no change to INV-2, which only admits forfeits and no-finisher carry-overs into a pool; and it is the only option that leaves nothing stranded in a settled cohort. **Rejected:** adding it to the carry-over (would let carry-over come from cohorts with finishers, changing INV-2); leaving it in the cohort forever; giving it to one finisher (two equal stakes would then be paid differently, breaking acceptance criterion 3).

**Why one pool per cohort (archived D9, still in force):** shared pools with tier multipliers transfer wealth from newcomers to veterans; separate pools deleted the entire multiplier apparatus and dropped the fairness spread to 2.4 pp.

**Why manual claim (archived D13, still in force):** signing the transaction *is* the wow moment. An automatic deposit is noticed silently and missed.

**Source:** spec FR-026…FR-037b, FR-032a, INV-1, INV-2, INV-5, INV-6, acceptance criterion 3; constitution I.

### H12. When nobody finishes: 50% fee, 50% carry-over — **SUPERSEDED 2026-10-05 by H34**

**Decided (OD-5, clarified 2026-09-15):** at settlement of a cohort with no finishers, **50% of the pool goes to the fee destination and 50% carries over** into the prize pool of the next cohort of the same league that is still open for joining. Any indivisible remainder goes to the carry-over. Both amounts are recorded on the settled cohort. If no cohort of that league is open, **settlement is refused until one is** (revised 2026-09-19, from a review of `settle_finalize`): holding the carry-over instead let whoever settled first decide, by timing alone, whether it ever reached a prize pool — and with delivery in Phase 2, a cut Phase 2 would have stranded it. Opening the league's next cohort is permissionless and already due, so any caller can clear the refusal; the held field survives only for a league in which nobody ever opens another cohort, and error 6047 names the refusal. A carry-over never enters a cohort that has already started. A cohort that received a carry-over and also has no finishers applies the same rule to its whole pool.

**Why:** the contract cannot be silent about stranded money (scope doc E6). Half to the next cohort keeps the quitters' money inside the game as extra prize; half to fee keeps a no-finisher cohort from being a pure subsidy.

**Rejected:** roll 100% forward as seed (PRD §7.4 — needed cross-cohort linkage with no rule for a missing next cohort); hold forever on the cohort (lost in all but name); send 100% to fee (simplest, but removes the forward-looking prize).

**Consequences:** INV-1, INV-2 and INV-6 each gained a carry-over clause; the "no forfeits, no prize" statement became "no prize beyond any carry-over already received".

**Source:** spec FR-037…FR-037b, ES-5…ES-7b, FR-041, acceptance criterion 11.

### H13. Rep verification: on-device, visual only, no audio

**Decided (OD-3, clarified 2026-09-15):**

- The environment checks — four when this was decided, five since 2026-10-10, when the knees got their own pill (002 FR-024a) — all gating ones must pass, each failure says what to fix — at the start of **every sitting** (H18).
- A liveness check (palm) confirms a live human — also every sitting.
- Reps count only if they meet the **visual** form and timing rules; non-conforming reps are discarded with a hint.
- **No audio anywhere**: no audio environment check, no audio rep condition, the app never requests the microphone.
- Camera frames are processed on the device and never transmitted or stored.
- Banked progress is **device-local** and survives a crash (H18); no Firebase.

**Why drop audio:** the stakes are a test token on devnet, so an extra anti-spoofing filter protects no real value; a microphone prompt adds friction to first run on a rubric that scores UX and stickiness; noisy rooms reject honest reps. The rule that mattered (scope doc E12): never drop the check while keeping the rep condition, or vice versa. Both went.

**Why no Firebase (scope doc D-G):** its jobs were session resume and a Merkle trace nothing verifies on devnet. Removing it removes an auth integration, a security-rules surface and a second source of truth.

**Palm check failure (clarified 2026-09-15):** if no palm is detected within **30 seconds**, the app shows a tip (more light, palm flat toward the camera) and returns to the environment checks. Retries are unlimited, the member can cancel at any moment, and banked reps are never touched. **Why back to the checks rather than retrying the palm:** light and framing are the likely cause, so re-checking fixes the problem instead of looping on it. **Rejected:** 60 seconds and retry only the palm step (longer, and doesn't address the cause); three attempts then a 10-minute lockout (tighter against spoofing, but an honest member could miss the deadline).

**Known weakness:** no audio anti-static-frame filter; the palm check covers it only partly. Named in the README with the fix beside it.

**Source:** spec FR-019…FR-023, FR-022a, OD-3; constitution X.

### H14. Demo integrity: real-time cohorts plus a recorded walkthrough

**Decided (OD-4, clarified 2026-09-15; revised 2026-09-17):** real-time 7-day and 21-day cohorts run continuously through the live period (submission to 11 November 2026, H25) on the weekly schedule, populated by **scripted members whose forfeits are spread across the cohort** so the survivor counter visibly falls. The scripted membership is **disclosed in the app, the README, the demo video and the pitch deck**. The full cycle — join, sessions, forfeit, settle, claim — is a **recorded walkthrough inside the app**, captured from the real app against the real devnet contract. There is no compressed or simulated cohort for judges.

**Where the walkthrough comes from (clarified 2026-09-17):** only from **cohort #3**, the first judged 7-day cohort. That means its real settlement on 5 October and a real claim, recorded 5–8 October and added to the build submitted before 9 October. The same footage serves as the demo video. Until it exists, the app says the recording is in progress. Plan: research R18, which resolves P6.

**What it shows, and who can watch it (clarified 2026-09-17):** the whole flow in order — launching the app, connecting a wallet, the faucet, the ladder, staking, the lobby, a sitting, a completed day, the crowd thinning, a forfeit, settlement and the claim. It is the **one thing playable before a wallet is connected** (FR-001a), because a judge whose wallet cannot sign on devnet would otherwise see nothing but the connect screen. Storyboard, shot list, recording workflow and beta-test plan: `holdout_demo_video.md`.

**Why:** judges install the app between 13 October and 9 November, and a full staked cycle takes a week, so any live cohort a judge sees must be populated by script regardless. A recording of the real thing shows the complete money loop without asking a judge for 175 push-ups in an hour.

**Rejected:**
1. **A labelled compressed "Demo" cohort on-chain** — real contract, but judges would still have to perform the cycle.
2. **Compressed cohorts only during judging** — the home screen would stop looking like the real product.
3. **An off-chain simulated demo mode** — a second code path that proves nothing about the contract.
4. **Recording the walkthrough from a labelled shortened-day rehearsal cohort** (2026-09-17) — it would have de-risked the timeline, but it is not the product judges install.
5. **Cohort #4** — it settles 12 October, after submission.

**`day_seconds` stays** (scope doc D-A) — as a testing and rehearsal tool, behind an admin guard, not as a judge-facing feature.

**Scripted members are real wallets under the same rules (clarified 2026-09-15).** Each is an ordinary wallet run by automation: it joins with a real test-SKR stake on a normal chip, submits completed days through the same path as anyone, and forfeits or finishes on a planned schedule. The contract has no idea which wallets are scripted. Their forfeits fund the prizes real members win, and a scripted finisher takes its share like anyone else. Their completed days are not camera-verified — part of the documented client-signed trust gap. The scripted wallet addresses are published in the build, so the app labels them with no server and anyone can check the labels.

**Why:** it is the only option that keeps every figure on screen backed by on-chain state (FR-038a, FR-065), and it needs no special case in the contract or in the app's reads. **Rejected:** real wallets that always forfeit before the end (never takes a real prize share, but a crowd where no scripted member ever finishes is its own tell and skews every rate on the leaderboard); display-only scripted members added to counters off-chain (breaks FR-065 and puts fake figures next to real money).

**Real members first, scripted only to a floor (revised 2026-09-17).** With 5–7 beta testers available, scripted wallets stop being the crowd and become the guarantee that no cohort is ever empty:
- A cohort is filled to **20 members (7-day) or 12 (21-day)**, counting real and scripted alike; once it is there, no further scripted wallet joins, and none leaves to make room (FR-043, FR-043a).
- Filling happens in two stages — half the floor when the lobby opens, the rest 24 hours before the join cutoff — so a lobby is never dead and real members still have room.
- A fixed pool of **56 wallets**: 20 play every 7-day cohort, and 36 cover the 21-day league in three rotating groups of 12. Each is topped up once to 0.02 SOL, about 1.12 SOL in total.
- They join every cohort starting 28 September – 9 November. The crank keeps running until the last of those cohorts settles on 30 November, so none forfeits merely because automation stopped.
- The floors are crank configuration; nothing on-chain knows which wallets are scripted.

**Why not drop scripted members entirely,** now that real testers exist? It would save about a day and a half and make every figure real, but recruitment is not guaranteed week to week, and a judge installing the app in late October could find a two-person cohort — the one thing constitution XII forbids. **Why not keep the old fixed 80 and 60?** With 5–7 testers the crowd would have been about 93% scripted, which is the opposite of what the rubric rewards.

**Source:** spec US-9, FR-043, FR-043a, FR-044, FR-064, FR-066, acceptance criteria 8 and 19; constitution XI, XII.

### H15. Deliberately cut for the demo

**Decided:** no minimum cohort size and no cancellation path (`min_cohort_size` effectively 1, scope doc D-H); ~~the Genesis Token gate is written but off (`genesis_gate_enabled = false`, scope doc D-D) because testers have no Seeker;~~ **Superseded by H38 (2026-10-10):** staking is Seeker-exclusive — the flag stays off on devnet but the app checks the wallet's Seeker Genesis Token on mainnet, and the contract's check (written only now) is turned on at mainnet launch; notifications are manual.

**Why:** cancellation with auto-enrolment needs a cohort that may not exist yet and a rent payer for the new accounts — neither on the critical path. ~~Without the Genesis flag off, the demo plan dies at the first join.~~ *(Withdrawn by H38: testers without a Seeker can no longer stake.)*

**Source:** spec §3; constitution Hackathon Constraints.

### H16. Honesty is a feature

**Decided:** everything faked, stubbed, flagged off or left trusting is named in the README with its real fix beside it. The current list:

| Weakness | Real fix |
|---|---|
| ~~`genesis_gate_enabled = false`~~ **Superseded by H38 (2026-10-10):** staking is Seeker-exclusive, but on devnet the app enforces it, not the contract | ~~Flip the flag on mainnet~~ At mainnet launch, turn the flag on **and** have the app send the member's Seeker Genesis Token account and its mint as `join_cohort`'s remaining accounts; the contract check is written and tested |
| Non-default `day_seconds` for test cohorts | Admin-guarded; mainnet cohorts cannot use it |
| Client-signed completed-day attestation (scope doc D-E) | Server-side attestation signer, pinned traces |
| Banked reps stored on the device, editable on a rooted phone, lost with the phone (H18) | Covered by the attestation fix; cross-device progress post-hackathon (L9) |
| No audio anti-spoofing filter | Reinstate audio check and rep condition together |
| Off-chain rate source and weekly chip re-pricing by the Operator | Documented trust; on-chain chip governance post-hackathon |
| Scripted members in judging-period cohorts — real wallets whose completed days are not camera-verified (H14) | Disclosed in the app, README, demo video and pitch deck; addresses published in the build and labelled everywhere |
| Faucet fee sponsor key bundled in the APK (H22) | Anyone can extract it, and empty its 0.05 SOL float; the contract caps abuse at one claim per wallet a day and a daily 0.72 SOL budget on every lamport leaving the faucet, and anyone can refill the sponsor, so a drain delays onboarding by one crank pass instead of ending it. Real fix: no faucet on mainnet, or a rate-limited relayer |
| Program upgrade authority kept through the live period (H24) | Used only for defect fixes that change no existing cohort, account layout or fund movement. Real fix: revoke it once the contract is final |
| Devnet, mock SKR mint | Mainnet with real SKR |

Copy uses "prize", never "winnings", "returns", "yield" or "earnings"; never "bet", "wager", "gamble", "odds" or "luck". Fiat is always approximate and never presented as what a member will receive. The app says it runs on a test network with a test token.

**Source:** constitution XI, XIII; spec FR-038…FR-042.

### H17. Leaderboards rank on-chain progress only

> **Phasing (H23):** league boards are Phase 2. The progress they read and the cohort roster are Phase 1.

**Decided (2026-09-15):** of the extras in the scope doc — daily streak, leaderboards, share cards, badges — **only the daily streak and leaderboards** are in scope.

- **League leaderboards**, one per league, built to show **who is disciplined**. Each member shows **survival rate** (cleared ÷ played), **cohorts played** (cleared + forfeited — running, locked and withdrawn cohorts don't count), **cohorts cleared**, and **current and best league streak**.
- **Ranked by survival rate**, compared exactly, among members with **at least 3 cohorts played** in that league. Ties go to more cohorts played, then longer best streak, then whoever reached those figures first.
- Members below 3 are listed underneath as **"not ranked yet"**, with their figures and how many cohorts they still need.
- **Consistency is the league streak**: consecutive cleared cohorts of one league. A forfeit in that league resets the current streak; the best never decreases.
- **Cohort roster** for each running cohort: members still standing first, then forfeited members ordered by how many days they lasted.
- Built **only from on-chain progress** — no server, no device-reported data.
- Members are shown by shortened wallet address only. **No stake, prize or payout amount is ever shown or ranked.**
- Scripted members appear like anyone else and are **labelled as scripted**.
- The **daily streak stays personal** and appears on no leaderboard.

**Why on-chain progress:** the contract already records clears, forfeits and league streaks, so the boards cost no new infrastructure, cannot be faked by editing a phone, fill up automatically with scripted members for judges, and show real data.

**Why survival rate with a minimum:** the board's job is to show discipline, not volume. Rate alone would put a 1-of-1 newcomer above a 9-of-10 veteran; the 3-cohort minimum stops a single lucky week from topping the board, and the "not ranked yet" list keeps newcomers visible with a clear target.

**Why league streak for consistency:** the contract already tracks it, and "cleared 6 weeks straight" is the clearest discipline story a member can read at a glance.

**Known consequence (corrected 2026-09-17):** in the 21-day league, 3 cohorts is 9 weeks, longer than the whole live period (28 September – 11 November). So **nobody — real or scripted — can be ranked there before winners are announced**, and that board lists everyone under "not ranked yet". In the 7-day league a member who plays cohorts #3, #4 and #5 is ranked once #5 settles on 19 October.

**Rejected:**

1. **Ranking by cohorts cleared** (the first version of this decision, same day) — rewards volume; a member who plays often and fails often can rank high.
2. **Survival rate with no minimum** — a 1-of-1 newcomer outranks a 9-of-10 veteran.
3. **Days kept %** (days completed ÷ days owed) as the consistency measure — rewards lasting longer inside forfeited cohorts, but adds per-member day totals to on-chain progress and a busier board.
4. **Daily-streak leaderboard via a server** — adds a backend 13 days before freeze, and streaks would be self-reported and fakeable. The streak is safe to keep off-chain precisely because nothing competitive rides on it (H4).
5. **Daily streak recorded on-chain** — one source of truth, but a wallet signature every day for the free challenge, which is the product's lowest-friction surface.
6. **Ranking by prize or stake** — reads as a gambling scoreboard and contradicts constitution XIII.

**Cut:** share cards and badges (scope doc F8, S3).

**Supersedes:** scope doc S4 (leaderboard seeded from cohort #1 and #2 data, not live).

**Source:** spec US-10, FR-028, FR-051, FR-060…FR-065 (incl. FR-060a…FR-060c), Progress entity, acceptance criterion 14.

### H18. Reps bank in blocks of 10 across the day

**Decided (clarified 2026-09-15):** there is no single session. A day's reps can be done in **any number of sittings** before the deadline.

- Verified reps are **banked in blocks of 10** the instant the 10th is counted. The day completes the moment banked + current reps reach the requirement, so `10 + 10 + 5` is a complete 25-rep day.
- When a sitting ends for any reason, **reps short of the next block are discarded**; banked blocks stay until the day's deadline, then expire.
- **Every sitting re-runs** the environment checks (five pills since 2026-10-10) and palm liveness.
- Banked progress lives **on the device only**; nothing goes on-chain until the day is complete. A lost or wiped phone loses that day's banked reps.
- **No pause limit, and no refusal to start late.** The app shows reps still needed and time left before every sitting and warns clearly in the last 10 minutes.
- Applies identically to cohort days and the daily challenge.

**Why:** it fits real days — a set before work, one at lunch, one in the evening — and turns a crash, a phone call or a child walking in from a lost session into a lost handful of reps. Blocks of 10 keep what a crash can cost small and predictable, and they give the counter a satisfying beat (*"20 banked"*).

**Why re-verify every sitting:** without it one person could bank the first 10 and hand the phone to someone else for the rest. It raises the bar; it does not prove identity (H16).

**Why device-only:** no wallet prompt mid-workout, nothing extra to sign or pay for, and the on-chain record stays one completed day per member per day. The cost — losing a day's partial progress with the phone — is small and stated.

**Why never refuse a late start:** with banking, a late sitting risks at most fewer than 10 unbanked reps. Refusing would only ever cost a member who needed 5 more reps the chance to finish.

**Rejected:**

1. **One session with a 15-minute pause bound** (the previous spec and scope doc D-G) — a phone call or crash near the end cost the whole attempt.
2. **Writing each block of 10 on-chain** — survives a lost phone, but means a signature and a transaction mid-workout three times a day. The archived PRD had already dropped on-chain rep checkpoints once for the same reason.
3. **Verifying only the day's first sitting** — much weaker against someone else finishing the reps.
4. **Refusing to start with too little time left** (previous FR-047) — can block a member who could still finish.

**Supersedes:** spec FR-024 (bounded pause) and FR-047 (refusal) as first written; scope doc D-G's 15-minute bound; archived PRD §8.6 pause handling; open item P1.

**Offline (clarified 2026-09-15).** Sittings work with no connection: reps are verified and blocks bank offline. A completed cohort day counts only once its submission lands on-chain before the deadline, by chain time. The app warns plainly before a cohort sitting starts offline and again when a day completes offline, keeps the completed day on the phone, shows **"Done — waiting for network"**, and submits it automatically as soon as the app is back online. A daily-challenge day needs no connection at all.

**Why:** constitution II and FR-017 make chain time the only judge, so nothing on the phone may extend a deadline — but the app can make the risk impossible to miss and remove every manual step between reconnecting and counting. **Rejected:** blocking cohort sittings while offline (would stop members doing reps that would have landed in time once they reconnected); accepting late submissions with an offline "finished in time" claim (the phone would decide a breach — contradicts FR-017 and constitution II).

**Source:** spec US-3, US-5a, ES-1…ES-3b, FR-013, FR-015, FR-019, FR-020, FR-023…FR-025, FR-024b, FR-047, §8.3, acceptance criteria 15 and 18.

### H19. Automation is permissionless

**Decided (clarified 2026-09-15):** **recording forfeitures, settling a finished cohort, and opening a league's next cohort** (with delivery of any held carry-over) can be triggered by **any wallet** once they are due. The Scheduler is the usual caller, not a privileged one. No caller can choose or influence any parameter, outcome, recipient or amount; a step that isn't due, or is already done, does nothing. **Publishing a chip set is the only Operator-restricted action.**

**Why:** every outcome is already derived from on-chain state (H6) and every step is idempotent, so a stranger calling them can only do what is already due. It takes the Scheduler's key and server off the critical path for the entire live period: if the box dies, anyone with the repo can crank a stuck cohort. It also makes the demo claim stronger — the contract, not our server, runs the game.

**Rejected:**

1. **Scheduler key only** — simpler to reason about, but a lost key or dead server stops money, counters on leaderboards and new cohorts until fixed, with nobody else able to help.
2. **Scheduler first, anyone after a delay** — adds a timer and a second code path to protect a privilege that buys nothing, since callers can't affect results anyway.

**Consequence:** the Operator no longer opens cohorts; its only power is the chip set. Acceptance criterion 5 now requires an unrelated wallet to produce byte-identical state.

**Source:** spec FR-007a, FR-030a, Actors, acceptance criterion 5; constitution II, VIII; scope doc D-F.

### H20. Cohort size: no cap, built for 1,000

**Decided (clarified 2026-09-15):** no maximum cohort size is enforced. The expected size is **up to 1,000 members**, and the build is designed and tested for it: recording forfeitures and settlement work in pages so that no single step's cost grows with cohort size, and rosters and leaderboards stay correct at that size. Larger cohorts must still be correct, only untested.

**Why:** a cap would be one more rule to enforce and explain, and there is no product reason to turn members away. 1,000 covers the spec's `312` story with room, and is the size scripted cohorts can realistically reach.

**Rejected:** a hard cap of 100 (too small for the story), 500 (arbitrary), or 2,000 as the test target (heavier paging and tests for nothing the demo shows).

**Source:** spec FR-011a, acceptance criterion 7.

### H21. Wallet first

**Decided (clarified 2026-09-15):** connecting a wallet is the first step in the app and is **required before anything else is usable** — the daily challenge, the streak, cohorts, rosters and leaderboards included. A person without a wallet is told plainly that one is needed and how to get one. The daily streak is kept on the phone for the connected wallet.

**Why:** one identity from the first tap, with no second path where a streak or progress exists without a wallet and later has to be attached to one; every screen is keyed to the member the contract knows.

**What it costs, stated honestly:** a judge whose test phone has no Solana wallet sees only the connect screen until they install one. The README and the in-app walkthrough (H14) are what that judge has instead — so both must show the product fully without a wallet.

**Rejected:** wallet optional, requested only when joining a cohort (lowest friction for the free challenge, but two identity paths — anonymous streaks that must later be tied to a wallet); wallet optional with a prompt after the first completed challenge (same split, softer).

**Supersedes:** spec US-5a "any person with the app" and FR-048 "no entry condition of any kind" as first written.

**Source:** spec FR-001a, ES-0, US-1, US-5a, US-9, US-10, FR-048, acceptance criterion 8.

### H22. An in-app faucet, not public faucets

**Decided (clarified 2026-09-15):** the app has an **in-app faucet**. One tap gives the connected wallet enough test SKR to stake the top chip ($100 tier) of any open cohort, plus enough devnet SOL to pay fees for a full 21-day cycle — join, every completed day, claim. **Once per wallet per day** (05:00 UTC boundary). **No server:** it is funded in advance to last the whole live period, through 11 November 2026 (H25), and needs no manual top-up after submission. It is offered wherever a member lacks test SKR or fee SOL, and it never touches a stake, a pool or the fee destination.

**How it works (plan, research R13; resized 2026-09-17):**
- **SKR** is minted by the program.
- **SOL** comes from a program-owned vault that tops a wallet up to 0.02 SOL.
- **Fees:** a bundled devnet sponsor key pays the claim fee, so a wallet with zero SOL can claim; a wallet that already holds SOL may pay for itself. The key is a documented weakness (H16).
- **A drained sponsor heals itself (2026-09-19):** the key holds a 0.05 SOL float and nothing else, and anyone who extracts it can transfer that float away — which would stop zero-SOL onboarding, not merely cost a day's allowance. So `top_up_sponsor` is permissionless: the crank refills the float from the faucet vault on any pass where it is below half, and so can any wallet with a little devnet SOL.
- **Caps (revised 2026-09-19):** one claim per wallet per day, and a daily budget of **0.72 SOL on every lamport leaving the faucet vault** — top-ups, the sponsor's fee and rent reimbursements, and sponsor refills. Counting claims instead let a recipient already holding 0.02 SOL have its `Profile` and token account rent reimbursed outside the cap, so the same recycled 0.02 SOL could have funded fresh wallet after fresh wallet at the vault's expense.
- **Funding:** the vault holds 25 SOL by 8 October, the worst case from submission to 11 November.

**Why:** a judge with an empty wallet is one tap from a real stake without leaving the app, and nothing depends on a public devnet faucet being up during judging. A server-free faucet keeps "no backend decides anything" true (H5, H19). The daily limit per wallet stops one wallet draining it.

**Rejected:** linking to public devnet SOL and mock-SKR faucets (members leave the app; public faucets are often rate-limited or down); an in-app SKR faucet plus sponsored fees (no SOL needed, but a service co-signs every transaction — a backend on the hot path).

**Source:** spec FR-042a, ES-0a, ES-0b, acceptance criterion 16.

### H23. Core first: Phase 1 and Phase 2, and the freeze stands

**Decided (2026-09-15):** the spec is split into **Phase 1 (core)** and **Phase 2 (deferred)**, both inside spec 001. Phase 1 must work end to end on devnet before any Phase 2 work starts. Phase 2 items are built only if they can be finished **before the 28 September freeze**; anything unfinished is cut from the hackathon build and recorded here.

| Phase 2 item | Phase 1 stand-in |
|---|---|
| League leaderboards (H17) | No league board screen; on-chain progress is still recorded from the first cohort, and the cohort roster ships in Phase 1 |
| Weekly chip re-pricing (H8) | One chip set published at launch; publishing a new set is already possible, so re-pricing is tooling only |
| Delivery of a held carry-over (H12) — **cut 2026-10-05 (H34)**: nothing carries over any more | Phase 1 holds none: a no-finisher cohort settles only once its league has an open lobby, and delivers the carry-over into it (error 6047 until then). The field and the instruction remain for a league nobody opens another cohort in |
| Automatic offline submission (H18) — **built 2026-10-05** | "Done — tap to submit" once online; every offline warning ships in Phase 1 |

**Why:** after three clarify sessions the spec is more than 13 days of work. These four items are the ones cohort #3 doesn't need on its first day — nobody can be ranked until three cohorts have been played, a mock SKR price doesn't move, an open cohort almost always exists at settlement, and one tap after reconnecting is an acceptable first version — and each has a stand-in that keeps Phase 2 free of data migrations.

**Rejected:**

1. **Moving them to a later feature 002** — a smaller spec 001, but the data model would not be designed for them, and the constitution ties scope to spec 001.
2. **Allowing Phase 2 to land after the freeze** — would change the app judges are already using, needing a new release mid-judging, and would break constitution Hackathon Constraints.

**Source:** spec §3a, Clarifications; constitution Hackathon Constraints (feature freeze).

### H24. The contract stays upgradeable, for defect fixes only

**Decided (clarified 2026-09-17):** the judged devnet program keeps its upgrade authority through the live period, used only to fix defects. An upgrade may never change:
- the rules, parameters or stored layout of any existing cohort, membership or progress record;
- how a held stake, prize pool or carry-over moves, except through the rule-driven steps every member already relies on.

A fix that would need any of those is not deployed. The authority is named in the README as a known weakness, with its real fix — revoking it once the contract is final (H16).

**Why:** the walkthrough and demo video can only come from cohort #3 (H14), which holds real test stakes from 28 September until its claim on 5 October. A defect found after launch must be fixable without abandoning that cohort. Keeping the authority is the constitution's one named exception to "the contract is the only authority on funds", and it is bounded by principle V: a running cohort's rules never move under its members.

**Rejected:**
1. **Revoking the authority at the 28 September freeze** — any defect after that would be unfixable while cohort #3 carries the submission.
2. **An immutable program from launch** — a defect would mean a fresh deployment and new cohorts, losing cohort #3.

**Source:** spec FR-007b, ES-16, §4 Actors (Operator), FR-030a; constitution I, V, XI and Hackathon Constraints (v1.3.0); plan research R14.

### H25. The CLOCK IN rules: what is submitted, when judging runs, how long the build stays live

**Decided (clarified 2026-09-17 from the official CLOCK IN rules page; dates amended 2026-10-09 after the organisers moved them):**
- **Submission, before 13 October 2026 01:59 CEST (12 October, 23:59 UTC), no late entries:**
  - a functional Android APK that runs on a real device;
  - a GitHub repository someone else can clone and run;
  - a demo video of about three minutes;
  - a pitch deck.
- **Judging** runs 13 October – 9 November 2026 (was 10 October). Winners are announced on 10 or 11 November; the rules page gives both dates.
- **The build stays live with no manual intervention from submission through 11 November 2026:** cohorts, the faucet and the Scheduler.
- **Distribution:** judges get the signed APK. **Publishing on the Solana dApp Store is not required** for submission. If the app wins, it must be published there within 30 calendar days of the announcement; that listing, and any mainnet launch, are post-hackathon work (L10, L11).
- **No submission material may mislead:** scripted members, the test network and every README weakness are disclosed in the demo video and pitch deck as well as in the app and README (H16).

**Why:** these are the hackathon's own rules. Judges score completion from the demo video and technical depth from the commit history, so the video must show the real cycle (H14) and the repository is part of the product. Misleading materials disqualify an entry. A store listing before submission would add KYC and store review, and each app update would need a new review — the walkthrough build of 7–8 October could not be approved before the deadline.

**Rejected:**
1. **Publishing a devnet build on the dApp Store for the submission** — store users are real users, and review would block the final build.
2. **Stopping the build at the end of judging on 9 November** — winners are announced after it, and winners get a technical review.

**Source:** spec header, §1, §2 (Distribution, Deliverables, Live period), §3, §4 Actors (Judge), US-9, FR-042a, FR-043, FR-066, acceptance criterion 19; constitution XI, XII and Hackathon Constraints (v1.3.0); plan delivery sequence.

### H26. A native Kotlin Android app

**Decided (during `/speckit-plan`, 2026-09-15; toolchain aligned 2026-09-17):** the app is a **native Android app in Kotlin with Jetpack Compose**, generated from the Solana Mobile CLI's **Kotlin Compose Minimal** template. It uses Mobile Wallet Adapter's Kotlin client, and MediaPipe Tasks with CameraX for on-device pose and palm detection. The crank is a Kotlin/JVM tool that shares the app's `core` module, so one Kotlin client talks to the program.

**Why:** the camera pipeline is the product's magic, and native Kotlin gives MediaPipe and CameraX first-party, GPU-accelerated support with no bridge on the hot path. The CLI template gives a known-good set of library versions in a build measured in days. Mobile-first design also scores directly in the rubric (H1).

**Rejected:**
1. **React Native + Expo** — faster UI iteration, but pose detection through community camera plugins is the riskiest spike.
2. **Flutter** — weak Solana Mobile support.

**Known cost:** Codama has no Kotlin renderer, so the Kotlin encoders and decoders for the program's data are hand-written and pinned to the program by committed golden fixtures.

**Source:** plan Technical Context; research R3, R12, R19; scope doc D-F, D-G.

### H27. The app looks native on the Seeker

**Decided (2026-09-17):** the visual language is **dark, black-canvas and built from Solana's own palette**, so the app reads as part of the Seeker system rather than a web3 app ported to Android. It replaces the earlier light cream-and-mint direction.

- **Colour lead — Seeker-native.** Black canvas, teal-tinted surfaces, **Tide Light `#80ECFF`** as the single interactive accent, white pill buttons. **Solana Green `#14F195`** marks success, still-standing and the claim; **Coral `#FF623A`** marks a forfeit or a warning; **Solana Purple `#9945FF`** and the green-to-purple gradient are kept for two celebration moments only. Every value is sampled from the brand kit, not eyeballed.
- **Type — native.** System Roboto / Roboto Flex for all UI text with tabular figures, and **JetBrains Mono** (open licence) for amounts and countdowns, echoing Solana's semi-mono numerals.
- **One theme.** Dark only, and Material You dynamic colour is off: brand colours never get recoloured by the device.
- **Brand boundaries.** **ABC Diatype and ABC Diatype Semi-Mono are never bundled or committed** — they are licensed to Solana for Solana's own materials. The icon and wordmark are Holdout's own, and nothing implies an official Solana or Seeker product.

**Why:** user experience and presentation are half the CLOCK IN rubric, and the surest way to look considered on a Seeker is to speak the device's own visual language. Solana's palette gives a ready, legible set of accents on black — the brand's Tide Light is already the pale cyan the Seeker UI uses for interaction. The earlier palette (`#F6F6F5` cream, mint cards, PP Mori) was designed for a light app and its display font is not licensed for commercial embedding.

**Rejected:**
1. **Solana-forward** — gradient headers and gradient buttons throughout. Strong brand association, but further from the system look and harder to keep legible.
2. **Holdout-distinct** — our own accent on black, Solana colours only in "built on Solana" marks. More recognisable in a screenshot, less native on the device.
3. **Bundling a free display pair (Inter + Space Grotesk)** — closer to Diatype's character, but roughly 1 MB of APK to look less native than the system font.
4. **Licensing ABC Diatype** — exact Solana look, but it needs a commercial licence and the fonts must still stay out of a public repository.
5. **Shipping light and dark** — about twice the design and screenshot work in the days remaining.

**Source:** PRD §11 (v2.5) and the §10.1a mocks; `specs/001-holdout-hackathon/contracts/android-app.md` §3a; constitution XIII (honest language is unaffected by the palette).

### H28. The names members read: the Daily 25 and a Holdout

**Decided (2026-09-17):** the free tier is **the Daily 25** and a staked run is **a Holdout** — "join a 7-day Holdout", "189 still standing in this Holdout". The product name becomes the unit of play.

**On-chain and technical names do not change:** `Cohort`, `LeagueConfig` and cohort indexes stay, so the data model, contracts, crank and tests do not churn days before the freeze. Spec §1a maps the two vocabularies, and the PRD glossary repeats it.

**Money wording that comes with it:** a lobby shows what is **at stake**; only a running Holdout has a **prize pool**, because only forfeits create one. The stake-confirm line stays as it is (FR-041).

**Why:** "daily challenge" and "7-day league / cohort" never told anyone which one was free and solo and which one was staked and shared — the first screens read as one undifferentiated thing. Naming the run after the product makes every line of copy carry it.

**Rejected:** "Daily 25 + Run" (plainer, loses the tie to the product name); "Daily 25 + Standoff" (more dramatic, more combative than a habit app wants); keeping "daily challenge" and "cohort" (no rename cost, but the original problem stays).

**Source:** spec §1a, US-5a, FR-048, FR-053; PRD §10 screens and §20 glossary.

### H29. First-timers get a tour; the walkthrough is for judges

**Decided (2026-09-17):** the moment a wallet connects for the first time, the member gets a **three-card tour** — the Daily 25, what a Holdout is, how reps are counted — skippable and re-openable later (FR-067). A **short explainer video** joins it once cohort #3 has been filmed; until then the cards carry it alone, and no mock-up footage is ever used.

The **3-minute full-cycle walkthrough is for judges**: it stays inside the app as FR-044 requires, reached from an About section and a quiet link on the connect screen, but it is no longer offered to members as a call to action.

**Why:** a first-timer needs the rules in fifteen seconds, not a three-minute film of someone else's week. A judge needs the opposite, and one of them may have no devnet wallet at all — so the walkthrough stays reachable before connecting.

**Guest mode considered and dropped (2026-09-17):** letting someone do the Daily 25 and watch Holdouts without a wallet was proposed and then withdrawn in the same session. **H21 stands** — connecting a wallet is still the first step — so there is no second identity path and no device-only streak to migrate later.

**Source:** spec FR-067, FR-044, FR-001a, US-1, US-9; PRD screens 2, 2a and 13; `contracts/android-app.md` §5.


### H30. How reps are tracked, and room for more exercises

**Decided (2026-09-18):**

- **The model** is MediaPipe Pose Landmarker (BlazePose GHUM), FULL on the GPU delegate, LITE as the fallback if spike S2 misses 15 FPS on a Seeker. The Hand Landmarker for palm liveness shares the same runtime.
- **Angles come from 3D world landmarks**, not image coordinates, so they do not depend on where the phone lies; image coordinates drive the overlay only.
- **The stated rule and the trigger values are different things.** The rule stays as the PRD writes it — top > 160°, bottom < 90°, body line ±10°, cycle 0.8–6 s. The state machine triggers at **≥ 165° / ≤ 85°** over one-euro-smoothed angles, because published validation puts the model's joint-angle error at about **2° mean absolute, RMSE ≈ 2.6°**: a hard edge would double-count on jitter. Frames with low landmark presence are dropped rather than scored. **Spike S2 sets the final numbers** from a measured per-frame angle table.
- **The exercise is a cohort parameter** (`exercise: u8`, copied from the league at creation like every other parameter, constitution V). `0` = push-ups is the only value this build accepts; `1` = negative (eccentric) push-ups and `2` = decline push-ups are **reserved**, so adding them later needs no new account layout — which matters because the retained upgrade authority (H24) may never change one.

**Why:** the rep rules were quantified before anything checked that the model could measure them, and the margin turned out to be thin. Separating the rule from the trigger keeps the honest statement in the product and the noise handling in the code, and lets the tests assert the rule while the thresholds are still being tuned.

**Deferred, not dropped:** negative (eccentric) push-ups — a controlled 3–5 second descent, the standard beginner path to a first full rep — and decline push-ups. Both are post-hackathon (L12): each needs its own recorded traces and cheat videos, and the freeze is 10 days out.

**Rejected:** MoveNet Thunder (17 keypoints, and the body-line rule needs hips and ankles; its upper-limb error is under 10° RMSE, worse than BlazePose here); ML Kit Pose Detection (no world coordinates); hard-coding push-ups and adding a second exercise by redeploying the program later.

**Source:** research R4, spike S2; spec §2, FR-007, FR-014, FR-021; data-model §1.2, §1.3, §2; `contracts/program-instructions.md` (`create_league`, `open_cohort`, error 6053); constitution V, X.

### H31. The mock SKR mint has 6 decimals

**Decided (2026-09-23):** `initialize` creates the mock SKR mint with **6 decimals** (`SKR_DECIMALS` in `programs/holdout/src/instructions/initialize.rs`). One SKR is `1_000_000` base units everywhere: the program, the TypeScript harness's `SKR` unit, the Kotlin client and the crank's chip conversion.

**Why:** the spec never fixed the figure, and a mint's decimals cannot change after it is created, so it had to be decided before the first `initialize`. Chip amounts are whole multiples of 10 SKR (research R15), so more precision buys nothing, and 6 is what the contract tests were already written against.

**Source:** tasks T027, T062; research R15; `tests/helpers/harness.ts`.

---

### H32. The short test cohort is a 7-day Holdout in 70 minutes

> **Superseded 2026-10-06 by H36:** a test lobby kept running for judges, three 10-minute days, a new cohort every 30 minutes.

**Decided (2026-09-29, product owner; amends 2026-09-27):** the disclosed short test cohort (FR-044a, constitution v1.4.0) runs **seven 10-minute days at 5 reps a day** — a 7-day Holdout in 70 minutes. `crank init --test-league` makes that the default; `--day-seconds`, `--days` and `--reps` still override it (`--day-seconds 3600` gives the one-hour days of the 27 September version).

**Why:** one-hour days (27 Sep) proved hard to test: a tester who missed one sitting waited hours for the next cohort, and a full cycle took most of a day. At 5 reps, a real sitting — the set-up checks, the ready hold and the counting — fits in a 10-minute day, which the 27 September version doubted when the target was 25. Every step stays real — the checks, the counting, a missed real deadline, settlement and the claim — within about an hour. The real 7-day and 21-day leagues stay at 24-hour days and 25 reps.

**Consequences:** the beta cycle takes about 70 minutes per tester. A day leaves about 10 minutes for the sitting and the submit, so a tester starts each day's sitting as it opens. A reminder for a 10-minute day comes as the day opens, at any point within it (FR-068): Android may take up to 10 minutes to deliver one. Back-to-back cohorts (the default cadence) open a new lobby every 70 minutes; `--cadence 600` opens one every 10.

**Source:** quickstart §5 and §5a; `Init.testLeagueParams`; `TestLeagueTest`.

---

### H33. On-device reminders, no push

**Decided (2026-09-27, product owner):** a member who joins a Holdout is reminded **by the phone itself** — when the Holdout starts, and before each day's deadline while that day is not yet done (FR-068). Replaces "notification infrastructure: manual for the demo".

**Why:** the first local run on a Seeker: a cohort started and nothing said so. Reminders computed on the device from chain state need no server and send nothing anywhere, so the build stays server-free and constitution X holds; a push service would need a server that knows every member's deadlines.

**Consequences:** one runtime permission (`POST_NOTIFICATIONS`, asked at the first join). Reminders are inexact, resume after a reboot only once the app is opened, and never excuse a missed deadline — named in the README (FR-068a, R21).

**Amended 2026-10-10 (product owner, on the screen-map review):** 3 hours before a 05:00 UTC deadline is 04:00-05:00 in Europe, when nobody sees a reminder. A day of 12 hours or more therefore gets **two** reminders, by chain time only: one **halfway through the day, with sound** (17:00 UTC for a 24-hour day), and the one before the deadline, now **silent**, so it waits in the notification shade and wakes nobody. Shorter days (the test lobby) keep their single reminder, with sound. No timezone or locale enters the system (constitution II). Decided 2026-10-11 (PR #44 stand-in review) that the halfway reminder is delivered inside the same window as the deadline one, an hour for a 24-hour day, so 17:00-18:00 UTC.

**Amended 2026-10-11 (product owner):** a day **done on the phone but not yet on chain** is the one case where a nudge matters most (a failed submit), so FR-068's "reminders stop once a day is completed on the phone" is withdrawn for it. Such a day has a **standing notice**, "Day N is done, but not on chain yet": ongoing, silent, shown as soon as the day is done and kept until it is recorded on chain or its deadline passes, and **re-posted silently every hour** counted back from the deadline (at most 24 a day, inexact alarms). On a day of 12 hours or more they replace that day's halfway and deadline reminders; a shorter day keeps its sounding deadline reminder beside the notice. A day's reminders replace one another in the shade instead of stacking. The owner's words: *"the reminder should also hang there as a push notification; let's make a silent notification every hour until they submit."* The one way to make the notice un-swipeable on Android 14 is a foreground service; it was rejected (a permission, a service type and a running process for a courtesy), so from Android 14 the member may swipe it away and the next hourly re-post restores it.

**Source:** spec FR-068, FR-068a, clarification sessions 2026-09-27, 2026-10-10 and 2026-10-11; research R21; tasks T067a, T067b, T159, T171, T172, T173.

### H34. The fee becomes 30%, and a no-finisher pool goes to the treasury

**Decided (2026-10-05, product owner, before the program's first deploy):**

- A forfeited stake splits **70/30**: 70% to that Holdout's prize pool, 30% to the fee destination (was 80/20, H11).
- A Holdout nobody finished sends its **whole pool to the fee destination**. Nothing carries over into another Holdout (was 50/50 with a carry-over, H12).
- **Still open:** how the 30% is divided once it reaches the treasury. Candidates the product owner named are a share toward the next Holdout's prize, as a reason to join it, and a promotion pool for the Daily 25. These come from research and brainstorming first, before anything is built. Until then all of it reaches the fee destination on chain, so the division can be done off chain from the treasury, or added later, without touching a member's stake or pool. A random airdrop to Daily 25 players would be a luck mechanic, which constitution XIII's language rule and Principle I ("nothing the admin calls moves money") both bear on, so it needs its own decision and probably an amendment.

**Why:** the product owner wants a larger treasury share to fund retention, such as prizes and promotions that keep members coming back. Dropping the carry-over also removes the one place where settlement depended on another cohort. Since 19 September a no-finisher cohort had been refused (6047) until its league's next lobby opened, and the Phase 2 delivery of a held carry-over (T099) had turned out to be unreachable: `open_cohort` is permissionless and nothing stops a league. Settlement is now self-contained.

**Rejected:** building `deliver_carry_over` (T099), which nothing could reach; keeping the carry-over beside the larger fee, which the product owner chose not to do.

**Consequences:**

- **Contract:**
  - `settle_finalize` takes no target accounts.
  - Errors 6046 and 6047 and the `CarryOverSent`/`CarryOverHeld`/`CarryOverDelivered` events are removed.
  - The cohort's carry-over fields stay in its layout, always zero, so no client's decoder changed.
- **Tests and invariants:**
  - INV-1, INV-2 and INV-6 lose their carry-over clauses.
  - Golden vectors V5b and V8 are dropped, and V1–V19 are recomputed.
- **Off-chain:**
  - The crank no longer opens a lobby before settling.
  - The app says "70 % to the pool, 30 % fee", and "if everyone finishes, you get exactly your stake back".

**Source:** spec clarification 2026-10-05, FR-027, FR-037 (FR-037a/b superseded), ES-5…ES-7b, FR-041, acceptance criteria 2 and 11; tasks T099 (cut), T111.

### H35. League reserves, two fee destinations, Easy and Hard

**Decided (2026-10-05, product owner, after a proposer/critic brainstorm, before the program's first deploy; admitted past the freeze by constitution v1.6.0):**

- **Every forfeited stake splits 70 / 10 / 5 / 5 / 10:**
  - 70% to the Holdout's prize pool.
  - 10% to the league's **boost reserve** and 5% to its **comeback reserve**.
  - 5% to the **developer** destination and 10% to the **operations** destination (marketing, operations).
  - The fee is `floor(30% × stake)` and the pool takes the rest (decided 2026-10-11: stake 101 gives pool 71, fee 30); within the fee each part is floored and operations takes whatever rounding leaves (stake 101: boost 5, comeback 5, developer 10, operations 10). The fee is still 30% (H34), now split on chain instead of sent to one account.
- **Reserves are credited only at settlement:**
  - A Holdout's reserve share stays in its own vault, recorded as `boost_due` / `comeback_due`.
  - It moves into the league's program-owned reserve vault when that Holdout finalizes.
  - Finalization runs in Holdout order per league (FR-061a), so the reserve a Holdout draws on never depends on when the crank recorded anyone's forfeit (Principle II).
- **Prize boost:**
  - When a Holdout with finishers finalizes, its pool gets `min(10% × its own pool, boost reserve)` before the shares are computed (cap 10% since 2026-10-10, was 20%).
  - A Holdout with no forfeits gets nothing, so "everyone finishes, exactly your stake back" (FR-041) still holds.
  - The boost draws first; the Holdout's own `boost_due` is credited after it.
- **Comeback refund:**
  - When a forfeit is **counted** (`settle_count`, in Holdout order, as the league streak is), the member's league progress keeps that stake and its missed deadline. Only the latest counted forfeit is kept.
  - When the same member is counted as a **finisher** of a later Holdout in the same league whose start lies within 14 Holdout days of that deadline, they are owed `min(10% × the forfeited stake, their new stake)`, and the record is used up.
  - At finalization the Holdout takes `min(total owed, comeback reserve)` from the reserve. Each refund is paid with the claim, scaled down pro rata if the reserve was short.
- **No finishers:** the whole pool still goes out (H34), a third to developer and the rest to operations. Its reserve shares are still credited to the league.
- **The Daily 25 is never paid, on any network.** Its pull is the toes meter, the streak with earned freezes, and the money lines about Holdouts on its done screen.
- **Form:**
  - `LeagueConfig.form`, copied to each cohort at `open_cohort` (constitution V): 0 = from the toes (**Hard**, 21-day), 1 = knees allowed (**Easy**, 7-day).
  - Easy keeps every other check Hard makes: depth, body line, hip rise, legs and knees in view.
  - Easy refuses to start unless the phone reads 40–60° — the band in which no cobra counted on the recorded traces.
  - Easy and Hard clears never share a leaderboard.
- **Daily 25 streak freezes:** one earned for every seventh day of the streak (when the streak reaches 7, 14, 21 …; clarified 2026-10-11 to match the code), at most 2 held, used automatically. A frozen day bridges the streak without resetting it and is not counted as a day. Device only, never on a Holdout day (FR-016).

**Why:** the product owner wants part of the fee to bring members back daily and into the next Holdout, a declared developer fee, and an easier entry level.

**Rejected:**
- A random airdrop: a luck mechanic, legally close to a lottery, and it did not help daily exercise in trials.
- Paying the Daily 25: it would be farmed through the free faucet.
- Crediting reserves when a forfeit is recorded: the crank's timing would set the boost.
- Easy filmed head-on: no body-line check at that placement where money is at stake.

**Farm bound:** with m = 0.20 and r = 0.10, a group that forfeits on purpose into its own finishers recovers at most 0.7(1+m) + r = 0.94 per SKR lost. The critic re-derived this, including the boost and the comeback hitting the same wallet, and reserves building up over many Holdouts. *Re-checked 2026-10-10:* raising r to 0.20 on 9 October made the bound 1.04, so the boost cap m dropped to 0.10 and the bound is 0.97; from its own forfeits alone such a group recovers 0.80, and the excess it can draw is bounded by the two reserves, which hold 5% of every forfeit each.

**Known limits:**
- When the comeback reserve is short, pro-rata flooring can leave a few base units in the Holdout's vault, fewer than its finishers.
- Easy's evidence is mostly one person's recordings: 87% of knee reps counted, 3 of 56 cobras slipped through, 2 of them at about 30°. More recordings are being collected this week (README weakness).

**Source:** brainstorm files A-r2, B-r2 and C-r2 (session scratchpad, summarised here); constitution v1.6.0.

### H36. No feature freeze; leaderboards, offline auto-submit and a test lobby for judges

**Decided (2026-10-06, product owner; constitution v2.0.0):**
- **The 5 October feature freeze is removed.** Phase 2 items are built after Phase 1 works end to end, with no date gate. H35 is an ordinary decision, no longer an exception. (Earlier the same day, v1.7.0 admitted leaderboards and offline auto-submit past the freeze; v2.0.0 replaced that by removing the freeze.)
- **League leaderboards** (US-10, FR-060…FR-063) are built: **all-time, one per league**, from on-chain progress. The Easy 7-day and Hard 21-day boards are separate because the leagues are. **Quarterly and yearly boards are deferred.**
- **One labelled board for the short test league.** Every test league shares one progress slot, so it is one combined board. No member can finish three judged cohorts before 9 October, so it is where ranking can be seen during judging.
- **The ranking rules live once, in Kotlin core**, with given-when-then tests for AC-14. The contract suite checks the on-chain progress figures the board reads, not a second copy of the ranking.
- **Automatic offline submission** (T098, PR #16): the wallet opens on its own once the phone is back online.
- **A short test lobby, kept running on devnet for judges** (FR-044a): the devnet build carries a test league whose labelled cohorts keep opening alongside the real 7-day and 21-day lobbies through the live period, so **a single judge can run the whole cycle alone**. *(Amended by H38, 2026-10-10: only with a wallet holding a Seeker Genesis Token, as for every staked Holdout; a judge without one watches a test Holdout thin from Home's "Running now" list, FR-062a.)* **Three 10-minute days at 5 reps, a new cohort every 30 minutes**, joinable right up to each start (join lock 0 since 2026-10-07; it was 5 minutes, which left a gap with no cohort to join; before that, seven 10-minute days, H32). The presentation video shows the cycle on a test cohort and says so.

**Why:** the leaderboards and offline auto-submit are client and crank only, and their data has been recorded since the first cohort (H23). A judge installing the app on any day of judging must be able to see the whole loop by themselves, which a real 7-day Holdout cannot give in one sitting.

**Source:** spec clarifications, session 2026-10-06; constitution v2.0.0.

### H37. No ladder: two separate leagues, both open to everyone

**Decided (2026-10-10, product owner; constitution v2.2.0):** the 7-day (Easy) and 21-day (Hard) leagues are **two separate products for different people**. They do not contradict each other, so the 21-day league is never gated and never needs "qualification" through the 7-day one. The ladder gate is **deleted, not flagged off**: `Config.ladder_gate_enabled`, its `set_flags` parameter and `FlagsSet` field, the `join_cohort` check, `Profile.qualified` and the line of `settle_count` that set it are gone. Error **6023** (`LadderGateClosed`) is retired — never reused, and no other code moved. After a forfeit the way back is **the next cohort of the same league**, the one a comeback refund pays on.

**Why:** the two leagues serve different audiences, so a gate between them only ever blocked people from the product they came for. A disabled flag kept a rule nobody wants any more, and a README weakness that described a choice, not a shortcut.

**Account format:** the program was not on devnet when this was decided (2026-10-10), so the fields were removed outright — `Config` 295 → 294 bytes, `Profile` 258 → 257 — rather than kept as reserved bytes. No stored record changes format.

**Supersedes:** the unlock and the gate of H2; the `ladder_gate_enabled` weakness of H16; US-5, FR-003a and FR-004 of the spec.

**Source:** spec clarifications, session 2026-10-10; constitution v2.2.0; tasks T137–T151.

### H38. Staking is Seeker-exclusive

**Decided (2026-10-10, product owner; constitution v2.3.0):** joining any staked Holdout — 7-day, 21-day and the short test lobby — needs a wallet holding a **Seeker Genesis Token**. It is part of the hackathon pitch and of Holdout's marketing. The contract's check (research R17: a Token-2022 account owned by the member, holding one token whose mint is a member of the Seeker Genesis Token group) is written in `join_cohort` behind `genesis_gate_enabled`, and the flag is turned on at mainnet launch. Seeker Genesis Tokens exist only on mainnet, so on devnet the flag stays off and **the app** checks the connected wallet's token on mainnet before it signs a join. A wallet without one keeps the Daily 25 and can watch every Holdout; joining shows that staked Holdouts are Seeker-exclusive, with a link to the Seeker store. If mainnet cannot be reached, joining is refused with a try-again message, and a wallet once confirmed is remembered on the phone.

**Why:** Holdout is built for the Seeker community the hackathon judges. Until now the flag existed but the check had never been written (`join_cohort` passed `holds_genesis = false`), so turning it on would have closed joining to everyone, which broke the rule that flags never delete the logic behind them.

**Trade-off:** on devnet the gate is the app's, not the contract's, so a wallet that signs a join without the app is not stopped. It is named in the README as a known weakness, and the fix is the contract check at mainnet launch: turning the flag on **and** having the app send the member's Seeker Genesis Token account and its mint as `join_cohort`'s remaining accounts. Every beta tester of H14's real-members-first plan now needs a wallet holding a Seeker Genesis Token, or a debug build with the gate off (`-Pholdout.seekerGate=off`).

**Supersedes:** H15's genesis reasoning (the gate off because testers have no Seeker) and H16's `genesis_gate_enabled = false` row. **Amends:** H2's "open to every member, always" and H36's "a single judge can run the whole cycle alone", both now for wallets holding a Seeker Genesis Token.

**Source:** spec clarifications, session 2026-10-10 (FR-003b–FR-003d, ES-0c, ES-0d, AC-21); constitution v2.3.0.

---

## 2. Still valid from the product-vision record

Carried forward unchanged; full reasoning in the archive.

| # | Decision | Status in the hackathon build |
|---|---|---|
| D8 | Repeat entries, not gating, drive cohort economics; "clear rate" is two numbers (cohort vs first attempt) — never quote them interchangeably | Insight holds; the simulated figures were for the three-league ladder and are **not** valid for H2 |
| D9 | One prize pool per cohort; no tier multipliers | In force (H11) |
| D10 | No demotion | In force |
| D11 | Fixed weekly cohorts, not rolling joins | In force (H9) |
| D13 | Manual claim, not auto-payout | In force (H11) |
| D15 | 21 days is the main league — the incumbent's one real-world data point on duration | In force (H2) |
| D16 | Renamed Swolana → Holdout; every "-bet" name is taken and contradicts the no-gambling framing | In force |

---

## 3. Superseded — do not implement

| Was | Where it lived | Replaced by |
|---|---|---|
| Three leagues: Push-Up 7d → Front Lever 21d → Human Flag 28d (30 reps) | archived D1 | H2 |
| A free 7-day clear qualifies | archived D2 | H2, H3, H37 — nothing qualifies |
| Staked qualifier closes on a clear | archived D3 | H2 — the 7-day never closes |
| Front Lever and Human Flag as permanent "homes" | archived D4 | H2 — two leagues only |
| USD chip ladders ($5…$200), Human Flag doubled, ceilings lowered | archived D5, D6, D7 | H8 |
| Free cohorts beside staked ones | archived D12 | H3 |
| No-finisher pool split 50/50 with a carry-over into the league's open lobby; `deliver_carry_over` for a held one | H12, T099 | H34 — the whole pool to the fee destination |
| Comeback path is the free week | archived D14 | H3, H37 — daily challenge plus the same league's next cohort |
| Free 7-day → staked 21-day | scope doc D-I (first version) | H2 |
| Fixed SKR chips 100 / 250 / 500 / 1,000 | scope doc D-C | H8 |
| "Never show a dollar figure" | scope doc D-C | H8 — USD tier labels, marked approximate |
| USDC pool, Jupiter swap, slippage | PRD §6.4, §9.1 | H7 |
| Per-member local-midnight deadlines, geo-IP offsets | PRD §5.3, §9.3 | H5 |
| Join closes Sunday 00:00 local; 24-hour cutoff | PRD §5.2; scope doc §3.1 | H9 — 1-hour cutoff, `Locked` kept |
| Join and leave open until the start, no `Locked` state | this file, earlier 2026-09-15 | H9 — rolled back |
| Leaderboard seeded from cohort #1 and #2 data | scope doc S4 | H17 — live, from on-chain progress |
| Conditional pre-join, auto-refunded on forfeit | PRD §5.6a | H10 — unconditional lobby place |
| `Finished` membership state | scope doc §3.2, E3 | H3 |
| Nobody finishes → whole pool rolls forward as seed | PRD §7.4; scope doc E6 | H12 |
| Audio as a pre-flight pill and rep condition | PRD §8.2, §8.4; scope doc E12 | H13 |
| Firebase session state | PRD §8.6 | H13 |
| Daily challenge open to "any person with the app", no entry condition | spec US-5a, FR-048 (first version) | H21 — wallet first |
| Link out to a devnet faucet when a member has no test SKR | PRD v2.0 §10.4 | H22 — in-app faucet |
| One session per day with a 15-minute pause bound; refusing to start with too little time left | spec FR-024, FR-047 (first version); scope doc D-G, §3.3 v1.3 | H18 — reps banked in blocks of 10 across sittings |
| 10-rep checkpoints written on-chain | PRD §8.6 (original design, dropped 2026-05-22) | H18 — blocks banked on the device |
| Operator opens cohorts; only the keeper records forfeits and settles | spec Actors (first version); scope doc D-F, §3.1 | H19 — permissionless |
| Share-rounding dust left in the settled pool | PRD v2.0 §19 V14; open item P9 | H11 — remainder to the fee destination |
| Counters and prize move only when forfeits are recorded | spec ES-8 (first version) | H6 — they move at the deadline |
| Minimum cohort size 8 with cancellation and auto-enrolment | PRD §7.4; archived O8 | H15 |
| Invariants I1…I9 | scope doc §3.4; constitution v1.0.0 | spec §8.4, INV-1…INV-9 |

---

## 4. Out of scope for the hackathon

By decision, not by omission.

| Removed | Why |
|---|---|
| Free cohorts | H3 |
| Conditional pre-joining | H10 |
| Cohort cancellation and minimum size | H15 |
| Geo-blocking, terms of service, third-party audit | Devnet, test token, no real value at stake |
| Calibration flow, Swelfie | Not in the video, not load-bearing |
| Cold-start bonus matching, insurance fund | No treasury on devnet |
| Demotion | Archived D10 |
| Push notifications from a server | On-device reminders instead (H33) |
| Audio in verification | H13 |
| Backend deadline evaluation | H5 |
| USDC, Jupiter swap, any on-chain price oracle | H7, H8 |
| Firebase | H13 |
| Share cards, badges | H17 — not in the video's critical path |
| Daily-streak leaderboard; any server-fed leaderboard | H17 |
| Solana dApp Store listing, mainnet deployment | H25 — not required for submission; a winner's listing is post-hackathon (L10, L11) |
| Anything else not required by a spec scenario | Constitution Governance. Add it by amending the spec, not by writing code. |

---

## 5. Still open

### Before or during `/speckit-plan`

| # | Question | Notes |
|---|---|---|
| P1 | ~~Pause bound~~ | **Resolved 2026-09-15 by H18** — no pause bound; reps bank in blocks of 10 for the whole day. |
| P2 | ~~Palm liveness failure path~~ | **Resolved 2026-09-15 by H13** — 30 seconds, tip, back to pre-flight, unlimited retries. |
| P3 | ~~Weekly chip check parameters~~ | **Resolved 2026-09-15 by research R15:**<br>• Phase 1: one chip set published at launch from a rate read by hand and recorded in the README.<br>• Phase 2: the same command runs Mondays with a 20% drift check against a public price API.<br>• Amounts round down to the nearest 10 SKR. |
| P4 | ~~Fee destination~~ | **Resolved 2026-09-15 by research R14** — an SKR token account owned by a treasury wallet set at initialisation receives the 20% fee, the no-finisher 50% and rounding remainders. |
| P5 | ~~Scripted member behaviour~~ | **Resolved 2026-09-15 by research R12, changed to a floor 2026-09-17:**<br>• Real members first; scripted wallets fill a cohort only to 20 members (7-day) or 12 (21-day), across the four tiers.<br>• A seeded forfeit schedule, with about 40% finishing.<br>• A fixed pool of 56 wallets funding their stakes from the faucet (SKR only) and topped up once with SOL.<br>• Addresses in `scripted-wallets.json` bundled in the app and copied into the README. |
| P6 | ~~Walkthrough recording~~ | **Resolved 2026-09-17 by H14 and research R18** — only from cohort #3, including its 5 October settlement and a real claim, filmed with a finisher and a forfeiter wallet; the same footage is the demo video. |
| P7 | ~~Scope doc drift~~ | **Done 2026-09-15** — scope doc v1.4 matches spec §8. |
| P8 | ~~Reads at scale~~ | **Resolved 2026-09-15 by research R8** — the app reads memberships straight from a devnet RPC endpoint with `getProgramAccounts` filtered by cohort, cached in memory and refreshed every 30 seconds on the cohort screen; no Holdout server or indexer. |
| P9 | ~~Share-rounding dust~~ | **Resolved 2026-09-15 by H11** — remainder to the fee destination at settlement. |
| P10 | ~~Banked-progress storage details~~ | **Resolved 2026-09-15 by research R6:**<br>• MMKV on Android, keyed by wallet + cohort (or `challenge`) + day index, so progress expires at the boundary by construction.<br>• Each block is written synchronously before "banked" is shown.<br>• A completed cohort day not yet landed is kept as a pending submission and shown as "Done — tap to submit" (data-model §4). |
| P11 | ~~Faucet design~~ | **Resolved 2026-09-15 by research R13, resized 2026-09-17** — see H22 for how it works. Sized for the live period through 11 November. |

### Post-hackathon

| # | Question | Decide when |
|---|---|---|
| L1 | **Churn-on-fail** — the dominant unmeasured economic parameter (archived O1) | First thing to instrument on mainnet |
| L2 | **Asia-Pacific day boundary** — second reference instant at 18:00 UTC or regional cohorts (H5) | When Seeker owner geography is known |
| L3 | **Is a paid comeback format needed** beyond the open 7-day league (archived O3) | On post-forfeit return rate |
| L4 | **Fee rate**, launched at 20% (archived O9) | Month 6 on mainnet |
| L5 | **Decentralised keeper** (archived O10) — largely answered by H19: steps are already permissionless; what remains is paying someone to crank reliably | After the keeper has run reliably for months |
| L9 | **Banked progress across devices** — a member's partial day survives a lost phone (H18) | If members report losing partial days |
| L6 | **Chip governance on-chain** — replacing trusted Operator re-pricing (H8, H16) | Before real value is at stake |
| L7 | **Free-entry lane for jurisdictions where paid entry is not legal** (H3) | Before any launch outside devnet |
| L8 | **Last-one-standing tournament** (archived O7) | v1.1 concept, needs a lawyer |
| L10 | **Solana dApp Store listing** — required within 30 calendar days of the announcement if the app wins (H25) | On the winners' announcement, 10–11 November 2026 |
| L11 | **Mainnet launch** — real SKR, faucet removed or replaced, upgrade authority revoked once the contract is final (H24), Genesis gate on | Together with or after L10 |
| L12 | **More exercises** — negative (eccentric) push-ups, a controlled 3–5 second descent as the beginner path to a first full rep, and decline push-ups. The cohort already carries `exercise`, with both values reserved (H30), so each needs rules, recorded traces and cheat videos, not a migration | After the hackathon; sooner if beta testers cannot do 25 full push-ups |
| L13 | ~~**Most 21-day forfeits' comeback is not offered in time**~~ — **resolved 2026-10-11 by FR-037g (spec 001):** the app works the comeback offer out from the member's forfeited membership the moment the deadline passes (the missed deadline `deadline_of(days_completed)` and FR-037e's window of 14 of the league's days after it), instead of waiting for `progress.comeback_*`, which the program still writes at settlement and which still decides the refund. With weekly Holdouts exactly two start inside the window, the owner's "two rounds". The forfeit screen offers the next one (up to 20 % of the lost stake, its start, the time left to join, a Join into the stake sheet on the same chip) and names the one after; Today nudges until the member joins one. A Join before automation has recorded the forfeit sends `record_forfeits` for the member's own pair in the same transaction. **The program is unchanged** (no account layout or instruction change, H24 untouched); research R22 | Done in the submission build; removed from the known weaknesses table |
| L14 | **Reclaim the rent of settled cohorts; the crank payer.**<br>• **What it costs.** `open_cohort` is permissionless and its caller pays the rent of the `Cohort` (229 bytes, 1,813,560 lamports) and its SKR vault (165 bytes, 1,488,440 lamports): 3,302,000 lamports, about 0.0033 SOL a Holdout, the same on devnet and mainnet (read from `getMinimumBalanceForRentExemption` on both on 2026-10-11). Each `Membership` (101 bytes, 1,163,320 lamports, about 0.0012 SOL) is paid by the member who joins, and only `leave_cohort` ever returns it. Nothing returns the cohort or the vault.<br>• **It is spent on devnet now.** The test lobby opens a cohort every 30 minutes, 48 a day, about 0.16 SOL a day from the crank payer (the Operator key): about 4.8 SOL to 11 November and about 7.9 SOL to the crank's last day, 30 November (R12). The setup runbook's funding table carries a line for it. The program's `cohorts()` read grows with it, to about 2,400 cohorts by 30 November. This is accepted for the devnet build: it cannot be fixed there (next point).<br>• **Not buildable on the devnet program.** A `close_cohort` is a new instruction, not a defect fix, and any recipient or index it needs is a new `Cohort` field, a layout change; H24 forbids both while the upgrade authority is kept. It belongs to the mainnet build (L11), which is a fresh deployment whose layout can carry the fields from the start.<br>• **Constraints that design must meet.**<br>&nbsp;&nbsp;1. *Order.* `settle_count` and `settle_finalize` load the league's previous `Cohort` and require `previous.settled`, and `open_cohort` loads it to read its `start`. A cohort may therefore close only after the next one has settled, so the newest cohort of a league, and the last one of a retired test league, stay open. Alternatively `LeagueConfig` gains a `settled_through` index and those three stop loading the previous `Cohort`.<br>&nbsp;&nbsp;2. *All claims done.* Already readable from the cohort: `settled`, and `held_stake` and `pool` both 0, since `claim_payout` subtracts each finisher's stake and share from them. No claim counter is needed.<br>&nbsp;&nbsp;3. *An empty vault.* An SPL token account with a balance cannot be closed. Floor rounding in `comeback_refund` leaves `comeback_funded - comeback_claimed` in the vault after the last claim, and an unclaimed stake stays until claimed. `close_cohort` sweeps the rounding remainder to the fee destination, as `settle_finalize` does for the share remainder (H11), then closes the vault.<br>&nbsp;&nbsp;4. *Unclaimed payouts.* FR-035 (and H11) keep an unclaimed payout claimable indefinitely, so a cohort holding one is never closable. A claim window would amend FR-035 and H11 and takes members' money from them; it is not proposed.<br>&nbsp;&nbsp;5. *Who gets the rent.* Not the opener: the `Cohort` records no opener, and FR-030a says the caller of a permissionless step cannot choose or influence a recipient. The rent goes to one fixed destination set at `initialize`, like the fee destinations, and the project funds the mainnet crank payer from it. Nothing on devnet is funded from the rent destination: the devnet program has neither it nor `close_cohort`, and its crank payer is the Operator key (next point). Paying the opener would add an `opener` field and amend FR-030a.<br>&nbsp;&nbsp;6. *Memberships.* The member pays that rent, so a member-signed `close_membership` after a claim or a counted forfeit returns it to the member (a signed step, so FR-030a is not touched). `leave_cohort` already does this before the start.<br>&nbsp;&nbsp;7. *History.* The app builds its Holdout list from `cohorts()` and attaches the wallet's membership (`ChainRepository.load`), and the leaderboard snapshot reads every cohort too. A closed cohort drops out of a member's history, roster and detail, so closing needs another source for them (events, an indexer) or a period during which settled cohorts stay.<br>• **Devnet payer: the Operator key stays the payer (decided 2026-10-11, product owner).** The crank takes two keys, `HOLDOUT_PAYER_KEYPAIR` for the automation and `HOLDOUT_OPERATOR_KEYPAIR` for `chips publish`; a separate payer needs no code, and an earlier version of this item recommended one. The owner decided against it: there is **no separate crank payer key**, and `fly.toml` keeps storing `operator.json` as the payer secret. **The risk, stated plainly:** `operator.json` is then a hot secret on a Fly machine and the only key that can publish the chip set, so whoever takes it can re-price every stake of every Holdout not yet open (FR-056), and can drain its float. **It can also break the faucet for good:** `publish_chip_set` sets `max_top_tier` to the larger of its old value and the new top tier, so it only rises, and `claim_faucet` mints exactly that much SKR per claim. One chip set with a top tier near `u64::MAX` makes every claim mint an absurd amount or, once the mint supply overflows, fail; new wallets lose their way to SKR (FR-042a), and `Config.operator` has no rotation instruction, so only a program upgrade (H24) undoes it. The same machine, and the GitHub Actions fallback (`crank.yml`), also hold the keypairs of the 56 scripted members, so a compromise can stop their submissions (forfeits that move real pools), join or leave, and claim their payouts. This is the owner's decided, accepted risk of the devnet build, not an oversight (constitution XI), and it is named in the README's weaknesses table. **Mainnet payer:** decided again with L11, together with an operator rotation instruction, a cap on the top tier and the scripted keys held apart (mainnet has no scripted members).<br>Raised 2026-10-10 by the product owner while funding devnet | The payer decision and the runbook line: recorded 2026-10-11. `close_cohort` and the rent destination: with L11, the mainnet build |

---

## 6. Amendment log

| Date | Change |
|---|---|
| 2026-09-15 | Constitution v1.0.0 ratified. Spec `001-holdout-hackathon` written. OD-1 (05:00 UTC) and OD-6 (one clock) resolved. |
| 2026-09-15 | `/speckit-clarify` session: OD-4 recorded walkthrough (H14); OD-5 50/50 fee and carry-over (H12); free cohorts removed (H3); OD-3 audio dropped (H13); OD-2 USD-tier chips priced in SKR, checked weekly with a 20% drift threshold, locked per cohort (H8); weekly schedule with join and leave until start and no `Locked` state (H9); unconditional lobby place alongside a running cohort (H10). |
| 2026-09-15 | Constitution v1.1.0: III permits off-chain chip pricing only, no on-chain fiat input; V adds chip amounts to cohort parameters; "pre-joining" narrowed to "conditional pre-joining"; invariants referenced from spec §8.4. |
| 2026-09-15 | This file rewritten around the hackathon spec. Previous version archived at `docs/archive/holdout_decisions_pre-hackathon-spec_2026-09-15.md`. |
| 2026-09-15 | H9 revised: join cutoff restored at 1 hour before start, joining and leaving both close there, `Locked` state back. H17 added: leaderboards from on-chain progress; share cards and badges cut. |
| 2026-09-15 | H17 revised: leaderboards rank by survival rate among members with at least 3 cohorts played, show cohorts played and league streak; members below the minimum listed as "not ranked yet". |
| 2026-09-15 | Constitution v1.2.0: V adds the join cutoff to cohort parameters; XII permits scripted members only if disclosed and labelled wherever they appear; XIII forbids showing or ranking money on any leaderboard; Hackathon Constraints add on-chain-only leaderboards, keep the daily streak off leaderboards, cut share cards and badges, and correct the freeze to "when the first in-app cohort starts". |
| 2026-09-15 | Docs synced to spec: `holdout_scope_and_state_machines.md` v1.3 (state machines, invariants, edge table, scope tables), README, and superseded banners on `holdout_v1_prd.md` and `_source_constitution_draft.md`. |
| 2026-09-15 | `holdout_v1_prd.md` rewritten as v2.0, hackathon-only: every behaviour cited to the spec; test vectors recomputed in SKR with carry-over cases; three leagues, USDC, Jupiter, free cohorts, badges, share cards, referrals, Swelfie, geo-blocking, ToS, treasury policy, bonus matching and simulated economics removed. Original archived at `docs/archive/holdout_v1_prd_full-vision_2026-09-15.md`. New open item: where share-rounding dust finally goes (PRD V14). |
| 2026-09-15 | Second `/speckit-clarify` session: automation permissionless (H19); no cohort size cap, built for 1,000 (H20); rounding remainder to the fee destination (H11, closes P9); counters and projected pool move at the deadline (H6); reps banked in blocks of 10 across sittings with re-verification every sitting, no pause limit and no late-start refusal (H18, closes P1). Spec gained FR-011a, FR-024a, FR-030a, FR-032a, FR-038a, ES-3a and acceptance criterion 15. PRD synced to v2.1 (new vectors V17–V19), scope doc to v1.4 (§3.3 rewritten, E17–E19). New open item P10. |
| 2026-09-15 | Third `/speckit-clarify` session: wallet required first (H21); in-app server-free faucet, once per wallet per day (H22); scripted members are real wallets under the same rules, published in the build (H14); offline sittings with automatic submission, chain time still decides (H18); palm check times out after 30 seconds (H13, closes P2). Spec gained FR-001a, FR-024b, FR-042a, FR-043a, ES-0, ES-0a, ES-0b, ES-3b and acceptance criteria 16–18. PRD synced to v2.2 (faucet §6.1, offline §8.7, vectors V20–V22), scope doc to v1.5 (F10, F11, §3.3 waiting-for-network state, E11 resolved, E20–E22). P5 narrowed, P11 added. |
| 2026-09-15 | Phasing (H23): league leaderboards, weekly chip re-pricing, delivery of a held carry-over and automatic offline submission moved to Phase 2 inside spec 001, each with a Phase 1 stand-in; Phase 2 only before the freeze. Spec gained §3a and FR-055a, with [Phase 2] markers; PRD v2.3 and scope doc v1.6 marked to match. |
| 2026-09-15 | `/speckit-plan`: native Kotlin Android app (H26), Anchor program, TypeScript tests on LiteSVM, Kotlin/JVM crank. Research resolved P3 (R15), P4 (R14), P5 (R12), P8 (R8), P10 (R6) and P11 (R13). |
| 2026-09-17 | Plan aligned with the Solana Mobile CLI Kotlin template: Anchor 1.1.2, Solana CLI 3.1.10, Kit + LiteSVM + Codama tests with a Surfpool smoke run, WSL2 on Windows (H26). |
| 2026-09-17 | Fourth `/speckit-clarify` session, using the official CLOCK IN rules. Decisions:<br>• The walkthrough comes only from cohort #3 and doubles as the demo video (H14, closes P6).<br>• The contract stays upgradeable for defect fixes only (H24).<br>• No dApp Store listing for submission; judging runs 10 October – 9 November; the build stays live through 11 November; the submission is an APK, a GitHub repository, a demo video of about three minutes and a pitch deck (H25).<br><br>The spec gained FR-007b, FR-066, ES-16 and acceptance criterion 19. |
| 2026-09-17 | Constitution v1.3.0:<br>• XI: submission materials may not hide or contradict the README's weaknesses.<br>• XII: the real judging window and live period; scripted members disclosed in the demo video and pitch deck too.<br>• Hackathon Constraints add signed-APK distribution, the submission deliverables and deadline, and the defect-fix-only upgrade authority. |
| 2026-09-17 | Crowd model changed (H14): real members first, with 5–7 beta testers recruited for cohort #3, and scripted wallets filling a cohort only to a floor of 20 (7-day) or 12 (21-day); pool cut from 260 to 56 wallets. The walkthrough shows the whole flow and plays before a wallet is connected — the only exception to FR-001a. Spec gained those changes in FR-001a, ES-0, FR-043, FR-043a, FR-044 and acceptance criterion 8. New doc `holdout_demo_video.md`: storyboard, screen checklist, recording workflow, shot log, filming calendar and the beta-test plan. |
| 2026-09-18 | Constitution v1.3.1 (patch, from the third `/speckit-analyze` pass): Principle V's parameter list gains **the exercise** and says plainly that the list is not closed; XII and the Hackathon Constraints adopt the members' names — **the Daily 25** and **a Holdout** (H28, H30). Tasks gained T017b, the failing league-setup scenario that proves `create_league` rejects the reserved exercise values, and spike S2's measured angles now land in a committed file. |
| 2026-09-18 | Rep tracking grounded in a measured method (H30): world landmarks, one-euro smoothing and hysteresis triggers wider than the model's ~2° error, with spike S2 setting the final numbers; research R4 rewritten. The **exercise becomes a cohort parameter** with negative and decline push-ups reserved, so they need no migration — spec §2, FR-007, FR-014, data-model §1.2–§1.3, `create_league` error 6053. Negatives and decline deferred to L12. |
| 2026-09-17 | Names and onboarding (H28, H29): the free tier becomes **the Daily 25** and a staked run **a Holdout**, with on-chain names unchanged and spec §1a mapping them; a three-card first-run tour (FR-067); the full-cycle walkthrough becomes judge-facing; Holdouts open to join show what is at stake in them (FR-053). Guest mode proposed and dropped — H21 stands. PRD v2.6; prototype gained a tour artboard and rewritten first two screens. |
| 2026-09-17 | Design direction set (H27): native-on-Seeker dark design system, tokens sampled from the Solana brand kit, Roboto plus JetBrains Mono, dynamic colour off, licensed brand fonts excluded. PRD §11 rewritten (v2.5) with colour, type, spacing, components, motion, accessibility and brand boundaries; §10.1a mocks annotated; app contract gained §3a Appearance. |
| 2026-09-17 | Plan revised for the new dates:<br>• R12: scripted wallets in every cohort starting through 9 November, a pool of 260, and the crank running to 30 November.<br>• R13: SOL-paying faucet cap cut from 60 to 30 a day; worst case corrected to 0.024 SOL per claim; vault funded to 25 SOL by 8 October.<br>• R14: upgrade authority.<br>• R18: walkthrough from cohort #3 with finisher and forfeiter wallets.<br>• Delivery calendar re-planned from 17 September, with the judged launch on 22–23 September.<br><br>H16 gains the sponsor key and upgrade authority weaknesses; H17's 21-day ranking consequence corrected (nobody can be ranked there before 11 November); L10 and L11 added. |
| 2026-09-19 | Review pass over the planning artifacts, five defects fixed:<br>• **H22/H16, faucet:** the sponsor key holds a 0.05 SOL float and `top_up_sponsor` is permissionless, so a thief emptying it delays zero-SOL onboarding by one crank pass instead of ending it (spec FR-042b, research R13).<br>• **H22, faucet:** the daily cap becomes a 0.72 SOL **lamport budget** on everything leaving the vault, closing the reimbursement path an already-funded recipient used to create accounts outside the cap.<br>• **H17, streaks:** league streaks are written only by `settle_count`, which now refuses a cohort until the league's previous one has settled — so a forfeit recorded before an earlier cohort settles can no longer be overwritten (spec FR-061a, INV-9, error 6045).<br>• **INV-3:** completed days are bounded by `max_days_completed(now)`, not by elapsed days; `schedule.rs` gains that helper and the day-zero test, and `days_reached` becomes `days_elapsed`.<br>• **FR-041 copy:** the stake sheet says "if everyone finishes, you get your stake back plus your share of any carry-over" — the old wording contradicted the carry-over line beside it (T059). |
| 2026-09-19 | Constitution v1.3.2 (patch, from the fourth `/speckit-analyze` pass): the Development Workflow's test-suite bullet no longer enumerates the invariants as a closed list. Spec §8.4 is named the authoritative list, INV-1…INV-9 is given as the count at this version, and an invariant added there is stated to be a new failing test rather than an optional extra — so the next one needs no constitution patch. Prompted by INV-9 (league streaks follow cohort order, FR-061a), which the pass found the constitution still excluding. |
| 2026-09-19 | Carry-over made independent of transaction order (H12, OD-5), from a Codex review of `settle_finalize`: a no-finisher cohort now settles **only** when its league has a cohort open for joining, delivering the carry-over into it, and is refused with `CarryOverTargetNotOpen` (6047) until then. Holding it instead had let whoever settled first decide by timing whether the amount ever reached a prize pool, and a cut Phase 2 would have stranded it with no instruction able to move it. Phase 1 now never sets `carry_over_held`; the field, `CarryOverHeld` and `deliver_carry_over` remain **[Phase 2]** for a league in which nobody ever opens another cohort. Spec FR-037a, ES-7a, acceptance criterion 11 and §3a, data-model §1.3 and §2, the crank's ordering and the README weakness row all follow. |
| 2026-09-19 | Two setup-window holes closed, from a Codex review of the pushed branch (R14, FR-055b): **`initialize` now accepts only the program's upgrade authority**, checked against `ProgramData` before anything is created (`NotUpgradeAuthority`, 6003) — until `Config` exists there is no stored key to authenticate a signer, so a stranger could have claimed a freshly deployed program along with `operator`, `sponsor` and the fee destination, and the program ID the app and README name could not have been reclaimed; `initialize` must therefore run before the upgrade authority is revoked. And **no cohort opens before the first chip set is published** (`chip_set.version > 0`, `ChipSetNotPublished`, 6012): `open_cohort` is permissionless, a cohort keeps the amounts it copied for life (FR-007), so one opened in the gap after deployment would have taken zero-stake joins until it settled. `crank init` now publishes the first set between `initialize` and the leagues. Golden vector V7 rewritten to the refusal behaviour the 2026-09-19 carry-over change introduced. |
| 2026-09-26 | Constitution v1.4.0 (minor): **one disclosed short test cohort, devnet only** — a test league with a compressed day on the devnet program built with `test-leagues`, admin-created, labelled everywhere in the app and disclosed in the README, video and deck; the demo and walkthrough may be recorded from it. The first judged start and the feature freeze move from 28 September to **Monday 5 October 2026**. Mainnet builds never carry `test-leagues`. Spec: Session 2026-09-26, FR-044 amended, FR-044a added. |
| 2026-09-27 | Constitution v1.5.0 (minor): Principle X gains **one narrow exception for rep recognition** — a **debug build** may save a sitting's camera stream as video (no audio), on the device only, pulled by the team over USB and deleted after, from people who agreed in writing to be filmed; a release build must not contain the recorder, enforced by a build check. Members sharing their own recordings stays a later decision. Pulls forward spec 002 T035. |
| 2026-10-05 | **Automatic offline submission built (H18, T098).** Every submission needs the member's wallet signature, so "automatic" was decided to mean the wallet's approval opens by itself — no tap on "Done" — as soon as the app is open and the chain answers again; a returning network triggers a chain read at once. A member who declines in the wallet is not asked again until the connection drops and returns, and the day then shows "Done — tap to submit". Rejected: a notification while the app is in the background (more surface for no judged gain) and keeping the Phase 1 tap. Spec FR-024b amended to say so. |
| 2026-10-05 | **Fee 70/30, no carry-over (H34).** A forfeited stake now goes 70% to the prize pool and 30% to the fee destination. A no-finisher Holdout's whole pool goes to the fee destination, so `settle_finalize` needs no other cohort, 6046/6047 and the carry-over events are removed, and T099 is cut. How the 30% is divided afterwards is open, pending research. Program, IDL, Codama client, LiteSVM scenarios, golden vectors, Kotlin, crank and app copy changed together (T111). |
| 2026-10-05 | **Constitution v1.6.0 and H35.** The freeze is overridden for one package. League boost and comeback reserves are credited at settlement. Developer and operations fee destinations are on chain. The league `form` sets Easy (knees allowed, 40–60° only) for 7-day and Hard (from the toes) for 21-day. The Daily 25 gets a toes meter and earned streak freezes, and is never paid on any network. Principle I now admits a capped share of a league's earlier forfeits into a pool; Principle V names the form. Out of scope gains "any payment for the Daily 25" and loses "bonus matching" and "insurance fund". |
| 2026-10-06 | **Human Flag removed.** The locked third rung is gone from the ladder screen, US-1, the out-of-scope list, H2, the PRD, the scope doc and the demo script. The ladder is the Daily 25 under the 7-day and 21-day leagues, nothing above them. It never existed in the contract, so nothing on chain changes. The archive keeps its history. |
| 2026-10-06 | **Constitution v2.0.0 and H36.** The feature freeze is removed (v1.7.0 had admitted leaderboards and offline auto-submit past it earlier the same day). League leaderboards are built (all-time, one per league, plus one labelled test-league board; quarterly and yearly deferred), with automatic offline submission. The short test cohort becomes a test lobby kept running on devnet for judges: three 10-minute days, a new cohort every 30 minutes. |
| 2026-10-06 | **Weekly chip re-pricing (H8 revised, T100).** `crank chips publish --auto` reads the Jupiter Price API (mainnet SKR mint) and re-prices every week with no drift threshold, at most once per Monday 05:00 UTC boundary; a standalone Operator command, never `crank run`. The race of two overlapping runs is accepted. |
| 2026-10-07 | **Test lobby join lock 0** (FR-044a, H36). The next test cohort opens only once the current one starts, so a 5-minute join lock left 5 minutes in every 30 with nothing to join. Each test cohort now stays joinable until its own start. The judged leagues keep their one-hour lock. |
| 2026-10-07 | **Weekly chip check runs hourly** (FR-056, T100). The Operator schedules `crank chips publish --auto` every hour in any time zone, and the once-per-boundary guard publishes on the first run after Monday 05:00 UTC. A failed run, or one with no rate, is retried the next hour within the same week. A once-a-Monday cron needed `CRON_TZ`, which Debian's cron ignores. |
| 2026-10-07 | **One test league at a time** (FR-044a, FR-060). The newest test league is the lobby, and the crank opens no more cohorts in an older one, which winds down. Streaks are written when a cohort is counted, after it ends, so two concurrent test leagues of different lengths could write a member's later clear before an earlier forfeit on the combined test board. No program change. |
| 2026-10-09 | **Constitution v2.0.1 (patch): dates moved by the organisers.** Submission is due **13 October 2026, 01:59 CEST** (12 October, 23:59 UTC), no longer 9 October, 08:59 CEST. Judging runs **13 October – 9 November 2026** (was from 10 October). The winners' announcement stays 11 November 2026. No principle changed. The spec, plan, research and demo-video doc still quote the old dates. |
| 2026-10-09 | **Constitution v2.1.0 (minor) and spec 004: Holdout Lab and the member app.** Rep-counting work moves to **Holdout Lab**, a separate tester app with no wallet and no chain, installed only on the team's and consenting testers' phones. Principle X's recording exception moves from "a debug build" to Holdout Lab, and the member app may not contain the recorder in any build. The member app drops everything addressed to judges: About · for judges, the in-app walkthrough and the *For judges* links. The submission is the app's final state, and the demo video is sent as a link. The short test lobby stays, "kept running, labelled, open to anyone". Specs 001 (FR-001a, FR-044, FR-044a, FR-066, AC-19), 002 (FR-023, FR-025, FR-026) and 003 (FR-010) follow through `/speckit-clarify`. |
| 2026-10-09 | **Constitution v2.0.2 (patch, wording), carried into v2.1.1 with spec 004.** The reason the demo video may use the short test lobby is now the deploy date, not the deadline: the program reaches devnet only days before submission, so no judged 7-day Holdout can run its full week on it, while a test cohort shows the whole cycle in about half an hour. The permission itself is unchanged. |
| 2026-10-09 | **Fee re-split (FR-027, FR-037): 70 pool / 5 boost / 5 comeback / 10 developer / 10 operations.** The fee stays 30%, but 80% now stays with members and 20% goes to the project, because operations now also funds marketing and development. A no-finisher pool goes half each to developer and operations (was a third and two thirds). The pool and the finisher headline are unchanged; the boost reserve gets half as much, so the average boost falls from about +7% to about +3.5% of a pool. Decided in the plan-page comment thread, before the devnet deploy, since rules are fixed for the live period. |
| 2026-10-09 | **Comeback refund 20% (FR-037e).** A member who forfeited and then finishes a later Holdout of the same league, within the 14-day window, gets back 20% of the lost stake (was 10%), capped at the new stake and cut pro rata when the league's comeback reserve is short. Nothing else changes: same league only, per-league reserve fed by 5% of every forfeit. The reserve holds while the share who come back and finish × 20% ≤ 5%, that is up to a 25% come-back-and-finish rate. Decided in the plan-page comment thread, before the devnet deploy. |
| 2026-10-09 | **Prize boost shown from the lobby (FR-053), paid at settlement (FR-037d unchanged).** Every Holdout offered to join shows "Prize boost: up to N SKR from this league's reserve when it settles" when the reserve holds anything. It draws people in with no contract change and never pays out money nobody lost. Topping a pool up when a cohort opens was rejected for the hackathon. Decided in the plan-page thread (option A). |
| 2026-10-10 | **Boost cap 10% (FR-037d); the comeback refund stays 20%.** The Codex review of PR #28 showed that with both at 20% a group forfeiting on purpose into its own finisher recovers 0.7 × 1.2 + 0.2 = 1.04 per SKR lost once the league's reserves hold honest members' money, against the 0.94 bound H35 recorded for a 10% refund. The product owner kept the 20% comeback members were promised and cut the boost cap to 10% of a cohort's own pool: the bound is 0.97. The average boost is unchanged, because the reserve (5% of forfeits) binds long before the cap; only small pools feel it. Program constant, Kotlin core, checker and golden vectors changed together. |
| 2026-10-10 | **Fee sentence reworded after the PR #36 review (FR-041, FR-053; FR-027 unchanged).** The stake sheet and forfeit screen say "Miss one and you lose your stake. 80 % of it stays with members: 70 % to the prize pool and 10 % to the boost and comeback reserves of Easy · 7-day Holdouts. 20 % goes to the project, and so does the prize pool if nobody finishes." The morning's sentence contradicted "your stake goes into the prize pool", used the member-banned word "league" and was false when nobody finishes. Member copy names a league's reserves by level and length, so the boost lines read "…from the reserve of Easy · 7-day Holdouts…". Holdout Lab's mode lines now say the Daily 25 and the 7-day count the same way and the 21-day counts more strictly. Decided by the product owner. |
| 2026-10-10 | **Chip set published in the 4 hours before the cohorts open** (FR-056, T125). `crank chips publish --auto` still runs hourly, but publishes only inside Monday 01:00–05:00 UTC, once per window, by chain time. Publishing on the first run after 05:00 came after that week's cohorts had already opened, so every lobby locked a rate about a week old (two by its join cutoff), and a run at `:00` raced the opening. Now a new lobby locks a rate at most 4 hours old and keeps it for every joiner; a window with no rate leaves the previous set (FR-057). Supersedes the 2026-10-07 "first run after the boundary". |
| 2026-10-10 | **Docs pruned to the product being shipped.** The specs are the only source of rules. `docs/holdout_v1_prd.md` (v2.7, last revised 18 September) is archived as `docs/archive/holdout_v1_prd_hackathon-build_2026-09-18.md` and no longer maintained; `holdout_scope_and_state_machines.md`, `holdout_spike_s1.md`, `_source_constitution_draft.md` and the two 2026-09-15 full-vision files are deleted, with git history keeping them and the bounded-oracle note moved to research R7; the setup runbook is trimmed to what still has to be done by hand. The spec, data model, Android contract, README and the `holdout-ui` skill now point at the specs, the screen maps and `docs/design/` instead of the PRD. |
| 2026-10-10 | **Knees visible is a pre-flight pill of its own (002 FR-024a, FR-015).** The near-knee checks leave *Whole body in frame*: the pills are Lighting, Whole body in frame, Knees visible, Camera view, Background still, and only the last is advisory. The set-up guide says first, on every page, that the whole body and the knees decide whether a rep counts. Product owner, on the screen-map review. |
| 2026-10-10 | **Constitution v2.2.0 and H37: no ladder.** The 7-day and 21-day leagues are separate products, both open to every member at all times. The ladder gate, its flag, `Profile.qualified` and error 6023 (retired, not reused) are deleted from the program, IDL, client, fixtures, Kotlin and crank; `qualify.spec.ts` becomes `open-leagues.spec.ts`; the README weakness row is gone. Spec FR-003 amended, FR-003a/FR-004 and US-5 removed; after a forfeit the way back is the same league (US-4). Removed outright, not as reserved bytes, because nothing was on devnet yet. |
| 2026-10-10 | **Constitution v2.3.0 (minor) and H38: staking is Seeker-exclusive.** Joining any staked Holdout needs a wallet holding a Seeker Genesis Token: the contract checks it at mainnet (R17, flag turned on at launch), and on devnet the app checks the wallet's token on mainnet. Without one, the Daily 25 and watching stay open and joining shows the Seeker-exclusive notice with a Seeker store link. Spec FR-002, FR-003, FR-044a, US-1, US-6 and US-9 amended; FR-003b–FR-003d, ES-0c, ES-0d and AC-21 added. |
| 2026-10-10 | **H33 amended: two reminders on a long day (FR-068).** A day of 12 hours or more gets one halfway through it, with sound (17:00 UTC for a 24-hour day), and its reminder before the deadline is silent, because 3 hours before 05:00 UTC wakes Europe. Shorter days keep one sounding reminder. Decided by the product owner on the screen-map review. |
| 2026-10-11 | **H33 amended: a day done but not sent has a standing notice (FR-068, AC-20).** Silent, ongoing and re-posted silently every hour until the day is on chain or its deadline passes; it replaces that day's halfway and deadline reminders on a day of 12 hours or more, and a shorter day keeps its sounding deadline reminder beside it. Also decided: the halfway reminder arrives in the same window as the deadline one (17:00-18:00 UTC). The day-complete screen and the home say the day is not sent yet when the chain cannot be reached (FR-024b). Decided by the product owner on 11 October and found by the PR #44 and #45 reviews. |
| 2026-10-11 | **Open item L13: most 21-day forfeits' comeback is not offered in time.** Decided 2026-10-11 to leave the program as it is for the submission and design the fix afterwards: the member has two rounds to join the next Holdout, and right after losing the app should show what they can do next and invite them to join it. Recorded in the known weaknesses table. |
| 2026-10-11 | **L14 recorded: reclaiming cohort rent and a separate crank payer.** An open item, no code or spec change. Opening a cohort costs about 0.0033 SOL (Cohort 1,813,560 + vault 1,488,440 lamports) and nothing returns it; the devnet test lobby spends about 0.16 SOL a day, now a line in the setup runbook's funding table. `close_cohort` is not buildable on the devnet program (H24: a new instruction, a layout change) and waits for the mainnet build (L11) with seven constraints written down: close order against `settle_*` and `open_cohort`, a vault emptied of rounding dust, FR-035, FR-030a and a fixed rent recipient, membership rent, and member history. A separate crank payer is recommended on devnet now, because `fly.toml` stores the Operator key as the payer. |
| 2026-10-11 | **L13 resolved in the app: the comeback is offered from the forfeit (FR-037g).** Supersedes the entry above that left L13 until after the submission. The refund was always earned by joining in time; only the app could not see it before settlement. The app now derives the offer from the forfeited membership with FR-037e's own window, shows it on the forfeit screen (both rounds, a Join on the lost chip, the amount per chip on the stake sheet) and on Today, and sends the member's own `record_forfeits` with a join made before automation has recorded the forfeit. Refund rules unchanged: any chip, `min(20 % of the lost stake, the new stake)`, only the latest forfeit counts. A lobby place outside the window must be left first (ES-18). No program change. Decided by the product owner (clarification session 2026-10-11, research R22, tasks T178–T190). |
| 2026-10-11 | **The Operator key stays the crank payer on devnet (L14).** The product owner decided against a separate payer key that L14 had recommended: `operator.json` remains the payer secret in `fly.toml`. The risk is recorded in L14 and added to the README's weaknesses table: a hot key on a Fly machine that is also the only key that can publish the chip set. The runbook's "Cohort rent" line names the Operator as the payer. No code or spec change. A review added the worse case to L14 and the README row: a stolen `operator.json` can publish a chip set with an enormous top tier, which only ever rises and cannot be rotated away, and the same machine holds the 56 scripted members' keys. |
| 2026-10-11 | **Forfeit rounding kept as built (FR-027, H35).** The product owner decided to keep the code: the fee is `floor(30% × stake)` and the prize pool takes the remainder, so the pool rounds up, not down (stake 101: pool 71, boost 5, comeback 5, developer 10, operations 10). FR-027's "every part rounded down" is amended to say so (clarification 2026-10-11) and H35's "each part is floored" likewise. The data model, the program, its comment and the golden vectors already agreed; no code change. |
