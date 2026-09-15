# Holdout — decision record

**Companion to `specs/001-holdout-hackathon/spec.md` and `.specify/memory/constitution.md` (v1.2.0).** The spec says *what to build*. The constitution says *what may never be violated*. This file says *why it is that way, what was tried and rejected, and what is still open* — so settled questions are not reopened by accident.

**Precedence when documents disagree:** constitution → spec → this file → `holdout_scope_and_state_machines.md` → `holdout_v1_prd.md`. The PRD (v2.2) is the hackathon build's product companion to the spec — screens, copy, verification rules, settlement vectors; the original full-vision PRD is archived and is deliberately not what is being built.

**Rule of thumb:** before proposing a change to the mechanic, search this file. Most of it has been proposed before, and several things were adopted, reversed and adopted again in a single day.

**Archive:** the previous version of this file — the full product-vision decision record (three-league ladder, USD chip ladders, free cohorts, simulation findings), plus the PRD archive (changelog, positioning, competitive landscape, rollout plan, launch checklist, success metrics, grant strategy, worked examples) — is kept verbatim at `docs/archive/holdout_decisions_pre-hackathon-spec_2026-09-15.md`, and the original full-vision PRD at `docs/archive/holdout_v1_prd_full-vision_2026-09-15.md`. Much of it is still useful for the pitch; none of it is the build. **"PRD §…" references in §3 below point into that archived PRD.**

---

## 1. Decisions in force — hackathon build

### H1. The build is a hackathon submission, not a launch

**Decided:** devnet only, mock SKR mint, feature-frozen **28 September** when the first in-app cohort opens; submission **9 October**. Anything not required by a scenario in the spec is out of scope, and scope grows only by amending the spec.

**Why:** judged over a month on stickiness, UX, memorability and presentation. Half the rubric is UX; pool economics score approximately nothing. A feature earns its place only if it appears in the video or is load-bearing for something that does.

**Source:** constitution Hackathon Constraints and Governance; spec §1.

### H2. The ladder: a free daily habit under two staked leagues

**Decided:**

| Layer | What it is |
|---|---|
| **Daily challenge** | 25 push-ups, every day, free, for everyone. No cohort, no stake, no prize. Builds a cosmetic streak. |
| **7-day league** | Staked, 25 reps/day. The entry rung and the comeback lane. **Never closes** — not on a clear, not on a forfeit. |
| **21-day league** | Staked, 25 reps/day. Where the money is. |
| Human Flag | Locked third rung in the UI only. Does not exist in the contract. |

Clearing a 7-day cohort permanently records the 21-day unlock. **The gate is off for the hackathon** (`ladder_gate_enabled = false`): both leagues are joinable from first launch, the unlock is still computed and recorded, and enabling the gate later is one flag, never a migration.

**Why:** a free loop anyone can run on day one, forever, is a far stronger stickiness story than a cohort you must pay to enter. The 7-day league must never close because with no free cohort it is the only short way back after a forfeit. The gate is off so a judge reaches the 21-day league without spending a week first; it is a flag rather than deleted code so the repo still shows the designed ladder.

**Reverses:** the archived D3 (staked qualifier closes on a clear). **Supersedes:** archived D1 (three leagues) and D2 (free clear qualifies).

**Source:** spec §2, FR-002…FR-005; scope doc D-I.

### H3. No free cohorts of any kind

**Decided (clarified 2026-09-15):** every cohort is staked. The only no-stake path is the daily challenge. The `Finished` membership state — which existed only for free-cohort finishers — is removed.

**Why:** the constitution already put free cohorts out of scope, but the spec still described a free league in the first-run flow, the forfeit flow and settlement, plus a state that would have needed its own screen and tests (constitution IX) while being unreachable.

**What was given up:** the free qualifying route, and with it the free-entry lane that made the product legal where paid entry is not. A launch concern, not a hackathon one. The "does this even work for my body, my camera, my room" objection is still answered for free by the daily challenge, which is the same session.

**Supersedes:** archived D12 (free cohorts beside staked ones) and the free half of D14 (free week as comeback path). After a forfeit the app now offers today's daily challenge and the open 7-day cohort.

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

**Why unconditional is cheap when the PRD version was not:** PRD §5.6a's pre-join depended on clearing the current cohort and was refunded automatically on forfeit, which put refund transfers inside the forfeiture run (scope doc E8, compute budget unverified). With the gate off, a lobby place depends on nothing, so there is nothing to resolve.

**Still cut:** conditional pre-joining (constitution Hackathon Constraints, amended v1.1.0).

**Guard that matters:** a 21-day member cannot take a place in next Monday's cohort, which would start while theirs still runs. INV-4 covers it.

**Source:** spec FR-006, FR-006a, FR-006b, ES-13, ES-14, INV-4.

### H11. Settlement: one pool per cohort, proportional payout, manual claim

**Decided:**

- 20% of each forfeited stake is fee; the rest enters that cohort's prize pool. Only forfeited stakes (and carry-over, H12) ever enter a pool; held stakes are segregated at every instant.
- Every finisher settles at the same moment on the same figures. Payout = `stake + pool × (stake ÷ total finisher stake)`. The denominator is written exactly once.
- **Each share rounds down** to the smallest SKR unit. At settlement the **rounding remainder** — always fewer units than there are finishers — **goes to the fee destination** and is recorded on the cohort, so the pool then holds exactly the shares (clarified 2026-09-15).
- **Claim is a deliberate member action**, never an automatic deposit, and stays claimable indefinitely.
- If nobody forfeits, finishers get exactly their stake back (plus any carry-over share), and the app says so before they join.

**Why the remainder goes to the fee destination:** it can be computed exactly at settlement, since every finisher's stake is known then; it needs no change to INV-2, which only admits forfeits and no-finisher carry-overs into a pool; and it is the only option that leaves nothing stranded in a settled cohort. **Rejected:** adding it to the carry-over (would let carry-over come from cohorts with finishers, changing INV-2); leaving it in the cohort forever; giving it to one finisher (two equal stakes would then be paid differently, breaking acceptance criterion 3).

**Why one pool per cohort (archived D9, still in force):** shared pools with tier multipliers transfer wealth from newcomers to veterans; separate pools deleted the entire multiplier apparatus and dropped the fairness spread to 2.4 pp.

**Why manual claim (archived D13, still in force):** signing the transaction *is* the wow moment. An automatic deposit is noticed silently and missed.

**Source:** spec FR-026…FR-037b, FR-032a, INV-1, INV-2, INV-5, INV-6, acceptance criterion 3; constitution I.

### H12. When nobody finishes: 50% fee, 50% carry-over

**Decided (OD-5, clarified 2026-09-15):** at settlement of a cohort with no finishers, **50% of the pool goes to the fee destination and 50% carries over** into the prize pool of the next cohort of the same league that is still open for joining. Any indivisible remainder goes to the carry-over. Both amounts are recorded on the settled cohort. If no cohort of that league is open, the carry-over is held against the settled cohort and delivered exactly once when one opens. A carry-over never enters a cohort that has already started. A cohort that received a carry-over and also has no finishers applies the same rule to its whole pool.

**Why:** the contract cannot be silent about stranded money (scope doc E6). Half to the next cohort keeps the quitters' money inside the game as extra prize; half to fee keeps a no-finisher cohort from being a pure subsidy.

**Rejected:** roll 100% forward as seed (PRD §7.4 — needed cross-cohort linkage with no rule for a missing next cohort); hold forever on the cohort (lost in all but name); send 100% to fee (simplest, but removes the forward-looking prize).

**Consequences:** INV-1, INV-2 and INV-6 each gained a carry-over clause; the "no forfeits, no prize" statement became "no prize beyond any carry-over already received".

**Source:** spec FR-037…FR-037b, ES-5…ES-7b, FR-041, acceptance criterion 11.

### H13. Rep verification: on-device, visual only, no audio

**Decided (OD-3, clarified 2026-09-15):**

- Four environment checks, all must pass, each failure says what to fix — at the start of **every sitting** (H18).
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

**Decided (OD-4, clarified 2026-09-15):** real-time 7-day and 21-day cohorts run continuously through the judging month on the weekly schedule, populated by **scripted members whose forfeits are spread across the cohort** so the survivor counter visibly falls. The scripted membership is **disclosed in the app and the README**. The full cycle — join, sessions, forfeit, settle, claim — is a **recorded walkthrough inside the app**, captured from the real app against the real devnet contract. There is no compressed or simulated cohort for judges.

**Why:** judging starts weeks after the last real cohort, so any live cohort a judge sees must be populated by script regardless. A recording of the real thing shows the complete money loop without asking a judge for 175 push-ups in an hour.

**Rejected:** a labelled compressed "Demo" cohort on-chain (real contract, but judges would still have to perform the cycle); compressed cohorts only during judging (the home screen would stop looking like the real product); an off-chain simulated demo mode (a second code path that proves nothing about the contract).

**`day_seconds` stays** (scope doc D-A) — as a testing and rehearsal tool, behind an admin guard, not as a judge-facing feature.

**Scripted members are real wallets under the same rules (clarified 2026-09-15).** Each is an ordinary wallet run by automation: it joins with a real test-SKR stake on a normal chip, submits completed days through the same path as anyone, and forfeits or finishes on a planned schedule. The contract has no idea which wallets are scripted. Their forfeits fund the prizes real members win, and a scripted finisher takes its share like anyone else. Their completed days are not camera-verified — part of the documented client-signed trust gap. The scripted wallet addresses are published in the build, so the app labels them with no server and anyone can check the labels.

**Why:** it is the only option that keeps every figure on screen backed by on-chain state (FR-038a, FR-065), and it needs no special case in the contract or in the app's reads. **Rejected:** real wallets that always forfeit before the end (never takes a real prize share, but a crowd where no scripted member ever finishes is its own tell and skews every rate on the leaderboard); display-only scripted members added to counters off-chain (breaks FR-065 and puts fake figures next to real money).

**Source:** spec US-9, FR-043, FR-043a, FR-044, FR-064, acceptance criterion 8; constitution XI, XII.

### H15. Deliberately cut for the demo

**Decided:** no minimum cohort size and no cancellation path (`min_cohort_size` effectively 1, scope doc D-H); the Genesis Token gate is written but off (`genesis_gate_enabled = false`, scope doc D-D) because testers have no Seeker; notifications are manual.

**Why:** cancellation with auto-enrolment needs a cohort that may not exist yet and a rent payer for the new accounts — neither on the critical path. Without the Genesis flag off, the demo plan dies at the first join.

**Source:** spec §3; constitution Hackathon Constraints.

### H16. Honesty is a feature

**Decided:** everything faked, stubbed, flagged off or left trusting is named in the README with its real fix beside it. The current list:

| Weakness | Real fix |
|---|---|
| `ladder_gate_enabled = false` | Flip the flag; the unlock is already recorded |
| `genesis_gate_enabled = false` | Flip the flag on mainnet |
| Non-default `day_seconds` for test cohorts | Admin-guarded; mainnet cohorts cannot use it |
| Client-signed completed-day attestation (scope doc D-E) | Server-side attestation signer, pinned traces |
| Banked reps stored on the device, editable on a rooted phone, lost with the phone (H18) | Covered by the attestation fix; cross-device progress post-hackathon (L9) |
| No audio anti-spoofing filter | Reinstate audio check and rep condition together |
| Off-chain rate source and weekly chip re-pricing by the Operator | Documented trust; on-chain chip governance post-hackathon |
| Scripted members in judging-period cohorts — real wallets whose completed days are not camera-verified (H14) | Disclosed in app and README; addresses published in the build and labelled everywhere |
| Devnet, mock SKR mint | Mainnet with real SKR |

Copy uses "prize", never "winnings", "returns", "yield" or "earnings"; never "bet", "wager", "gamble", "odds" or "luck". Fiat is always approximate and never presented as what a member will receive. The app says it runs on a test network with a test token.

**Source:** constitution XI, XIII; spec FR-038…FR-042.

### H17. Leaderboards rank on-chain progress only

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

**Known consequence:** in the 21-day league, 3 cohorts is 9 weeks, so no real member can be ranked there during the judging month — that board is ranked by scripted members only, with real members under "not ranked yet". In the 7-day league a real member from cohort #3 can be ranked by mid-October.

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
- **Every sitting re-runs** the four environment checks and palm liveness.
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

**Why:** every outcome is already derived from on-chain state (H6) and every step is idempotent, so a stranger calling them can only do what is already due. It takes the Scheduler's key and server off the critical path for the entire judging month: if the box dies, anyone with the repo can crank a stuck cohort. It also makes the demo claim stronger — the contract, not our server, runs the game.

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

**Decided (clarified 2026-09-15):** the app has an **in-app faucet**. One tap gives the connected wallet enough test SKR to stake the top chip ($100 tier) of any open cohort, plus enough devnet SOL to pay fees for a full 21-day cycle — join, every completed day, claim. **Once per wallet per day** (05:00 UTC boundary). **No server:** it is funded in advance for the whole judging month and needs no manual top-up. It is offered wherever a member lacks test SKR or fee SOL, and it never touches a stake, a pool or the fee destination.

**Why:** a judge with an empty wallet is one tap from a real stake without leaving the app, and nothing depends on a public devnet faucet being up during the judging month. A server-free faucet keeps "no backend decides anything" true (H5, H19). The daily limit per wallet stops one wallet draining it.

**Rejected:** linking to public devnet SOL and mock-SKR faucets (members leave the app; public faucets are often rate-limited or down); an in-app SKR faucet plus sponsored fees (no SOL needed, but a service co-signs every transaction — a backend on the hot path).

**Source:** spec FR-042a, ES-0a, ES-0b, acceptance criterion 16.

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
| A free 7-day clear qualifies | archived D2 | H2, H3 |
| Staked qualifier closes on a clear | archived D3 | H2 — the 7-day never closes |
| Front Lever and Human Flag as permanent "homes" | archived D4 | Post-hackathon only; Human Flag is a locked UI rung |
| USD chip ladders ($5…$200), Human Flag doubled, ceilings lowered | archived D5, D6, D7 | H8 |
| Free cohorts beside staked ones | archived D12 | H3 |
| Comeback path is the free week | archived D14 | H3 — daily challenge plus the open 7-day cohort |
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
| Invariants I1…I9 | scope doc §3.4; constitution v1.0.0 | spec §8.4, INV-1…INV-8 |

---

## 4. Out of scope for the hackathon

By decision, not by omission.

| Removed | Why |
|---|---|
| Human Flag league (contract) | Locked UI rung only (H2) |
| Free cohorts | H3 |
| Conditional pre-joining | H10 |
| Cohort cancellation and minimum size | H15 |
| Geo-blocking, terms of service, third-party audit | Devnet, test token, no real value at stake |
| Calibration flow, Swelfie | Not in the video, not load-bearing |
| Cold-start bonus matching, insurance fund | No treasury on devnet |
| Demotion | Archived D10 |
| Notification infrastructure | Manual for the demo |
| Audio in verification | H13 |
| Backend deadline evaluation | H5 |
| USDC, Jupiter swap, any on-chain price oracle | H7, H8 |
| Firebase | H13 |
| Share cards, badges | H17 — not in the video's critical path |
| Daily-streak leaderboard; any server-fed leaderboard | H17 |
| Anything else not required by a spec scenario | Constitution Governance. Add it by amending the spec, not by writing code. |

---

## 5. Still open

### Before or during `/speckit-plan`

| # | Question | Notes |
|---|---|---|
| P1 | ~~Pause bound~~ | **Resolved 2026-09-15 by H18** — no pause bound; reps bank in blocks of 10 for the whole day. |
| P2 | ~~Palm liveness failure path~~ | **Resolved 2026-09-15 by H13** — 30 seconds, tip, back to pre-flight, unlimited retries. |
| P3 | **Weekly chip check: which weekday**, which rate source, and the rounding rule for "readable SKR amounts" | H8 fixes the policy, not these parameters. |
| P4 | **Fee destination** | Where the 20% fee and the 50% no-finisher share land. |
| P5 | **Scripted member behaviour** | *Model decided by H14 (real wallets, same rules).* Still open: how many per cohort, the forfeit and finish schedule, how their stakes are funded, and how the scripted-address list is published in the build. |
| P6 | **Walkthrough recording** | Captured during cohort #3 or #4; must show a real claim transaction. |
| P7 | ~~Scope doc drift~~ | **Done 2026-09-15** — scope doc v1.4 matches spec §8. |
| P8 | **Reads at scale** | Reading up to 1,000 members' state from chain for rosters, leaderboards and the deadline-time survivor counter (H6, H20) — RPC limits, page size and caching are plan decisions; FR-065 only forbids showing unsupported figures. |
| P9 | ~~Share-rounding dust~~ | **Resolved 2026-09-15 by H11** — remainder to the fee destination at settlement. |
| P10 | **Banked-progress storage details** | Which device store, how a block is written atomically before "banked" is shown, how banked progress is keyed to the wallet and day so it expires at the boundary, and how a completed day waiting for network is persisted and retried (H18, H21). |
| P11 | **Faucet design** | How a server-free faucet holds devnet SOL and test SKR, enforces once per wallet per day, and is sized so it lasts the judging month without a top-up (H22). |

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
