# Holdout — product requirements (hackathon build)

**Version:** 2.6 · **Date:** 2026-09-17 · **Status:** product companion to the spec · **Target:** CLOCK IN submission, devnet only

> **Changed in 2.6:** members' names for the two things — **the Daily 25** (free, solo) and **a Holdout** (a staked 7-day or 21-day run), mapped to the on-chain vocabulary in spec §1a · a **three-card first-run tour** (FR-067) as screen 0 · the recorded full-cycle walkthrough is **for judges**, moved to About plus a quiet connect-screen link · Holdouts open to join show **what is at stake** in them.

> **Changed in 2.5:** §11 replaced by a **native-on-Seeker dark design system** — black canvas, Solana-palette tokens sampled from the brand kit, Roboto and JetBrains Mono instead of PP Mori, Seeker-style components, and brand boundaries (H27). The §10.1a mocks are annotated to match.

> **Changed in 2.4:** the recorded walkthrough plays from the connect screen **without a wallet** and runs the whole flow from launching the app to the claim (FR-001a, FR-044) · cohorts are filled by **real members first**, with scripted members topping up only to a floor of 20 (7-day) or 12 (21-day) (FR-043) · production detail for the video lives in `holdout_demo_video.md`.

> **Phasing (spec §3a, decisions H23).** Four items are **Phase 2** — built only after the Phase 1 core works end to end, and only if done before the 28 September freeze: league leaderboards, weekly chip re-pricing, delivery of a held carry-over, and automatic offline submission. They are marked **[Phase 2]** below, each with its Phase 1 stand-in.

> **What this file is.** The product-level detail behind `specs/001-holdout-hackathon/spec.md`: screens, copy, verification rules, settlement math, test vectors and glossary. It adds detail; it never adds scope. Every behaviour here traces to a spec requirement (cited as FR-/US-/ES-/INV-), and where this file and the spec disagree, **the spec is right and this file is a bug**.
>
> **Precedence:** `.specify/memory/constitution.md` (v1.3.0) → spec → `holdout_decisions.md` (H1–H26, the why) → `holdout_scope_and_state_machines.md` (state machines, edge cases) → this file.
>
> **Not here, on purpose.** Stack, accounts, PDAs and instruction signatures belong to `plan.md`. The post-hackathon vision — three leagues, USDC pool, free cohorts, badges, share cards, referrals, Swelfie, geo-blocking, ToS, audit, treasury policy, cold-start matching, simulated economics — is archived verbatim at `archive/holdout_v1_prd_full-vision_2026-09-15.md`. Top-level section numbers match that archive, so an older reference like "§8" still lands on the same subject; subsection numbers do not.

---

## Contents

- **§1** Summary
- **§4** Members and access
- **§5** The mechanic — *ladder, cohorts, lobby, daily obligation, failure, settlement*
- **§6** Stakes and the fee
- **§7** Settlement and pool design — *the payout math*
- **§8** Rep verification — *sittings, banked progress*
- **§9** On-chain and off-chain responsibilities
- **§10** Screens and flows
- **§11** Design system
- **§12** Streaks and leaderboards
- **§13** Honest language
- **§14** Risk register
- **§19** Appendix A — settlement test vectors
- **§20** Appendix B — glossary

*§2, §3 and §15–§18 exist only in the archive.*

---

## 1. Summary

Holdout is a Solana Mobile fitness commitment app. Anyone can do a **free Daily 25** — 25 push-ups verified on the device — and build a streak. On top of it sit **two staked leagues**: a member stakes SKR on themselves to do 25 push-ups every day for a fixed cohort of 7 or 21 days. Everyone in a cohort starts and finishes together. Miss a day and the stake is forfeited into that cohort's prize pool; finish and take back the stake plus a share of what the quitters left.

> Stake. Do your reps every day.
> Miss a day and you lose your stake to everyone still standing.
> Finish and you get your stake back plus a share of theirs.

**The number that makes it work:** `189 / 312 still standing`. A fixed cohort visibly shrinks, and no rolling-join product can show that.

**Build constraints:** devnet with a mock SKR mint · feature freeze 28 September · submission 9 October · judged for a month after the last real cohort (constitution XII).

**Against the incumbent (Squadletics on Worldcoin):**

| Lever | Squadletics | Holdout |
|---|---|---|
| Fee | 0% entry, 35% slash | **One fee: 20% of forfeited stakes.** Finishers pay nothing |
| Verification | Pose detection only | Pose detection + palm liveness + four-check pre-flight, all on-device |
| Currency | WLD or USDC | **SKR only** — stakes, pool, payouts, fee |
| Structure | 21-day, single tier | **Free Daily 25 + staked 7-day and 21-day leagues** |
| Pool | One shared pool | **One pool per cohort** |
| Cohort timing | Rolling | **Fixed weekly starts** — a live survivor counter |
| Day boundary | UTC | **One instant, 05:00 UTC, for everyone** — shown as a countdown |
| Settlement | Auto-paid | **Manual claim** — signing it is the moment |
| Leaderboard | — | **Ranked by discipline** (survival rate), never by money |

---

## 4. Members and access

### 4.1 Who uses the build

| Actor | In this build |
|---|---|
| **Member** | Anyone with a Solana wallet connected to the app, identified by the wallet (FR-001). Real testers from cohort #3 onward, and judges — who need a wallet too (FR-001a). |
| **Judge** | Installs cold, weeks after the last real cohort, and must immediately see the product working (US-9). |
| **Scheduler** | Automation that normally records forfeits, settles cohorts and opens each league's next cohort. It holds **no special permission** — any wallet may do the same once a step is due (FR-030a). Never decides outcomes (constitution II). |
| **Operator** | Publishes the SKR chip set after the weekly rate check (FR-056). The only privileged actor, and only for that. |

### 4.2 Access

- **No Seeker requirement.** The Genesis Token check is written but off (`genesis_gate_enabled = false`) because testers have no Seeker. Named in the README.
- **No ladder gate.** Both leagues are joinable from first launch (`ladder_gate_enabled = false`, FR-003); the 21-day unlock is still computed and recorded (FR-003a).
- **No geo-blocking, no terms-of-service screen** — out of scope for a devnet build with a test token (spec §3).
- **Wallet first.** Connecting a wallet is the first thing the app asks for, and nothing — not even the Daily 25 — is usable before it (FR-001a). A person without a wallet is told plainly that they need one and how to get one (ES-0). The daily streak is kept on the phone for the connected wallet.
- **One wallet, one member.** No username, no password, no display name, no avatar. Leaderboards show a shortened wallet address (FR-063).
- **Funds for testing come from the app.** An in-app faucet gives a connected wallet test SKR and devnet SOL once a day (§6.1, FR-042a), so nobody needs an outside faucet.
- The app states on first run and on every staking screen that it runs on a **test network with a test token** (FR-042).

---

## 5. The mechanic

### 5.1 The ladder

| Layer | Duration | Reps/day | Stake | Open to |
|---|---|---|---|---|
| **Daily 25** | today | 25 | none | everyone, every day |
| **7-day league** | 7 days | 25 | one of four chips | everyone; never closes |
| **21-day league** | 21 days | 25 | one of four chips | requirement: clear a 7-day cohort — **stated, not enforced** in this build |
| Human Flag | — | — | — | locked rung in the ladder UI only |

- **The 7-day league never closes** — not after a clear, not after a forfeit. It is the entry rung and the way back (FR-002).
- **The unlock is permanent** and never revoked, including after a forfeit (FR-004).
- **The Daily 25 is not a league.** It grants no unlock, no prize and no league progress — only the streak (FR-005).
- **A completed cohort day counts as the day's challenge** — nobody trains twice (FR-050).

### 5.2 Fixed weekly cohorts

**A cohort is a group of members who start at the same instant and finish at the same instant.** No rolling joins, no late entry.

- Cohorts start **weekly, Monday at 05:00 UTC** (FR-007a).
- Each league opens its next cohort **at the instant its current cohort starts**, so a league always has one cohort open for joining — except during the locked hour before each start.
- The 21-day league runs up to **three cohorts in parallel**.
- **Opening is automated and permissionless** — the Scheduler normally does it, but any wallet can trigger a due opening, and nobody can choose its parameters (FR-030a).
- **No cohort size cap.** Expected up to **1,000 members**; forfeits and settlement are processed in pages, and the build is tested at that size (FR-011a).
- Every parameter — start, join cutoff, duration, reps per day, day length, chip set — is **fixed at creation** and never changes (FR-007).
- Cohorts are named by league and number (`21-day · cohort 14`), never by calendar date (constitution VI).

#### The lobby

```
      21-DAY · cohort 14
      joining closes in 2d 13h · starts in 2d 14h

                    47
                already in

      your stake 1,040 SKR · held       ≈ $25 tier
      [ leave — full refund until joining closes ]
```

- **Joining and leaving both close at the join cutoff, 1 hour before the start** (FR-008, FR-009). Until then leaving refunds the full stake, no fee, no condition.
- From the cutoff the cohort is **`Locked`**: the roster is final, no join or leave is offered, and the screen shows the final count and the countdown to the start (ES-12a).
- **From the cutoff there is no way out and no refund** (FR-010).
- A join or leave that is signed before the cutoff but **lands after it is rejected** by chain time; the app says which happened and warns in the final minutes before the cutoff (ES-12).
- While waiting, the member can do the **Daily 25** and keep their streak running.

#### A lobby place alongside a running cohort

A member may hold **one lobby place** (in either league) while already in a running cohort (FR-006a):

- It is **independent** of the running cohort — finishing or forfeiting that cohort changes nothing about it (ES-14).
- It is accepted only in a cohort that **starts no earlier than the running cohort ends**, so a member never owes two days' reps at once (FR-006b). A 7-day member can line up next week's cohort; a 21-day member cannot join one that starts while theirs still runs.
- At most one lobby place at a time, and at most one running cohort (FR-006, INV-4).

### 5.3 The daily obligation

- **The day's reps, verified, before the day's deadline** — in one sitting or several (FR-013). Reps come from the cohort, never from a constant (FR-014).
- **Reps bank in blocks of 10** for the whole day: `10 + 10 + 5` across a morning, lunch and evening completes a 25-rep day. Reps short of the next block when a sitting ends are lost (FR-024, §8.6).
- **Day N runs from `start + N × day length`**, identically for every member. There is no timezone, offset or locale anywhere (FR-045).
- The app shows the obligation as **reps still needed and time remaining** — `15 to go · 5h 12m left today` — never as a date or a local clock time (FR-046).
- **Unlimited sittings** within the day (FR-015).
- **No grace day, no postponement, no carry-forward.** Banked reps don't survive the deadline, and one missed day ends the cohort for that member (FR-016).
- **Late in the day?** The app never refuses to start a sitting. In the last 10 minutes it warns clearly, before and during the sitting, that unfinished reps won't count (FR-047, ES-3).

### 5.4 Failure

A member is **in breach the moment their current day's deadline passes** without that day recorded as complete — banked reps short of the requirement don't count, and it doesn't matter whether the Scheduler has run (FR-017). From that instant they cannot submit a completed day (FR-018, INV-8).

**From the same instant** the survivor counter, projected pool, projected shares and cohort roster treat them as out, on every device (FR-038a).

When the forfeit is recorded — by the Scheduler or by anyone (FR-030a):

- The **entire stake** is forfeited — no partial credit (FR-026).
- **20%** goes to the fee destination; **80%** joins that cohort's prize pool (FR-027).
- The member's **league streak** in that league resets to zero and their participation ends (FR-028).
- Their **daily streak is untouched** (FR-052).
- The app tells them plainly what happened and offers **today's Daily 25 and the open 7-day cohort** as the way back (US-4).

### 5.5 Progress

- Clearing a 7-day cohort **records the 21-day unlock** permanently (US-5).
- Per league, the member's on-chain progress counts **cohorts cleared, cohorts forfeited, current league streak and longest league streak** — the only source for leaderboards (FR-060…FR-061).
- **No demotion.** A forfeit costs the stake, not the unlock.

### 5.7 No refunds after the cutoff

No refunds for missed days, technical problems during a sitting, or a bad environment. Three things protect members instead:

1. **Pre-flight check** (§8.2) — no sitting starts until the environment is right, and each failure says what to fix (FR-019).
2. **Banked progress** (§8.6) — every block of 10 is saved on the device at once, survives a crash or restart, and waits for the member until the day's deadline (FR-023, FR-024, ES-1).
3. **A last-10-minutes warning** — nobody is surprised by the deadline (FR-047).

### 5.8 Settlement and the claim

1. At the cohort's final deadline it stops accepting completed days (`Settling`).
2. It is settled — normally by the Scheduler, but anyone may trigger it (FR-030a): every finisher is counted, the payout denominator is written **once**, shares are rounded down and the rounding remainder goes to the fee destination, and every finisher settles on **the same figures at the same moment** (FR-031, FR-032a, FR-033).
3. Each finisher sees **Claim 1,560 SKR** and signs it themselves — **never an automatic deposit** (FR-034).
4. An unclaimed payout **stays claimable indefinitely** (FR-035).
5. A forfeited member has nothing to do; their stake left when they breached.

---

## 6. Stakes and the fee

### 6.1 SKR only

Stakes, prize pools, payouts and the fee are **all SKR** (constitution IV). No USDC, no swap, no conversion path. On devnet the token is a **mock SKR mint**.

**Stated honestly in the app and README:** "finish and get your stake back" is true **in SKR**. The dollar value of that SKR can move during a cohort.

#### The in-app faucet (FR-042a)

```
            GET TEST TOKENS

      4,160 test SKR   enough for any $100 chip
      devnet SOL       enough fees for a 21-day run

            [ get them ]
      once a day · test tokens have no value
```

- **One tap** gives the connected wallet enough test SKR for the top chip of any open cohort, plus enough devnet SOL to pay fees for a full 21-day cycle — join, every completed day, claim.
- **Once per wallet per day**, resetting at the 05:00 UTC boundary.
- **No server.** Funded in advance for the whole judging month; nobody tops it up by hand.
- **Offered where it's needed** — on the stake screen, before submitting a completed day, before a claim — whenever the wallet lacks test SKR or fee SOL (ES-0a).
- **If it can't pay out**, the app says so plainly, keeps the Daily 25 and browsing available, and never blames the member (ES-0b).

### 6.2 The fee — one number, 20%

**Holdout takes 20% of every forfeited stake, and nothing else.** No entry fee, no withdrawal fee.

```
member joins            stake (SKR)  →  held for that member, never in the pool

on a forfeit            forfeited stake  →  20%  →  fee destination
                                         →  80%  →  that cohort's prize pool
```

**If you finish, Holdout takes nothing from you.**

### 6.3 Stake chips

Four chips, the same tiers in both leagues (FR-054):

| Tier | Chip shows | Staked |
|---|---|---|
| $10 | `≈ $10 · 416 SKR` | exactly the SKR amount shown |
| $25 | `≈ $25 · 1,040 SKR` | 〃 |
| $50 | `≈ $50 · 2,080 SKR` | 〃 |
| $100 | `≈ $100 · 4,160 SKR` | 〃 |

*SKR amounts are illustrative.*

- **The contract validates SKR only and never reads a price** (FR-055, constitution III).
- **Phase 1:** the Operator publishes **one chip set at launch** from that day's rate, and every cohort locks it (FR-055a).
- **[Phase 2] Once a week**, at the 05:00 UTC boundary, the Operator checks the chip set against an off-chain rate and publishes a new one **only if the chips have drifted more than 20%** from their tiers (FR-056). If no rate is available, the current set stays (FR-057, ES-11).
- **Each cohort locks the chip set current at its creation** for its whole life. A new set applies only to cohorts created afterwards (FR-055, ES-10).
- **Within one cohort, everyone on the same tier stakes identical SKR.** A stake never changes after join (FR-058).
- USD labels are **always marked approximate** and never presented as what a member will receive; every amount staked or received is shown in SKR (FR-059, FR-039).
- The rate source and the Operator's re-pricing are **trusted off-chain inputs**, named in the README (constitution XI).

---

## 7. Settlement and pool design

### 7.1 One pool per cohort

**Each cohort has its own prize pool**, funded only by its own members' forfeits — plus any carry-over it received from a cohort of the same league where nobody finished (§7.4). Held stakes and the prize pool are **segregated at every instant** (FR-011, INV-1, INV-2).

```
prize pool   =  0.80 × Σ forfeited stakes in this cohort  +  carry-over received
your payout  =  your stake  +  prize pool × (your stake ÷ total stake of all finishers)
```

No tier multipliers, no weights, no cross-league accounting. Two finishers with equal stakes receive identical payouts, because everyone settles at once on one denominator (acceptance criterion 3).

### 7.2 During the cohort

1. Each recorded forfeit moves the stake out of the member's hold at once: **fee first, remainder into the pool**. The pool is one running number.
2. The **pool, survivor count and each member's projected share are live** on the home screen (§10.1a). They are computed from on-chain state and move **at each day's deadline**, counting every breach at once — including forfeits nobody has recorded yet, whose 80% is already in the projected pool. Recording them later changes no displayed figure (FR-038a).
3. At the final deadline the cohort moves to `Settling`; at settlement every finisher becomes claimable.

### 7.3 The math

For a cohort with members `i`:

```
pool          = 0.80 × Σ(stake_i, forfeited)  +  carry_over_received
share_you     = floor( pool × stake_you ÷ Σ(stake_j, finishers) )     // smallest SKR unit
payout_you    = stake_you + share_you
remainder     = pool − Σ share_j                                       // < number of finishers
```

Each share is **rounded down**. At settlement the **remainder goes to the fee destination** and is recorded on the cohort, so the pool then holds exactly the finishers' shares (FR-032a).

Checked on every settlement: `Σ payouts + remainder = Σ finisher stakes + pool`.

### 7.4 Edge cases

| Case | Behaviour | Spec |
|---|---|---|
| **Nobody forfeits** | Finishers get exactly their stake back, plus their share of any carry-over the cohort received. The app said this was possible before they joined. | ES-5, FR-041 |
| **One finisher** | Stake plus the entire pool, carry-over included. | ES-6 |
| **Nobody finishes** | 50% of the pool to the fee destination, 50% as **carry-over** into the next cohort of the same league that is still open for joining. Any indivisible remainder goes to the carry-over. Both amounts are recorded on the settled cohort. | FR-037, ES-7 |
| **Nobody finishes, no cohort of that league open** | The carry-over is held and recorded against the settled cohort (Phase 1) and delivered **exactly once** when the league's next cohort opens **[Phase 2]**. It never enters a cohort that has started. | FR-037a, ES-7a |
| **A cohort that received carry-over also has no finishers** | The same 50/50 rule applies to its whole pool, carry-over included. | ES-7b |
| **Scheduler misses a run** | Transfers and leaderboards lag; the survivor counter, projected pool and roster do not. No outcome changes, no member in breach can submit meanwhile, and any wallet can record the forfeits and settle instead. | ES-8, FR-030a, FR-038a |
| **Shares don't divide exactly** | Every share rounds down; the remainder goes to the fee destination at settlement. | FR-032a |
| **After settlement** | The only changes possible are claims and the single delivery of a held carry-over. | INV-6 |

---

## 8. Rep verification

A **sitting** is one continuous run of checks, liveness and counting. A day's reps can take any number of sittings; each one is verified from scratch.

### 8.1 Principles

- **On-device and live.** Camera frames are processed in real time and discarded — never transmitted, never stored, anywhere (FR-022, constitution X). Verified by inspecting outbound traffic (acceptance criterion 10).
- **Visual only.** No audio check, no audio rep condition, no microphone permission (FR-022a).
- **No human review, no flagging, no bounty.** The rep counter's verdict is final.
- **Same verification everywhere** — cohort day and Daily 25 alike, every sitting (US-5a).

### 8.2 Pre-flight environment check

Before **every** sitting — not only the day's first — the app runs four checks on the live camera feed, each a status pill updating in real time:

1. **Lighting adequate** — average frame brightness within the target range
2. **Whole body in frame** — every pose keypoint visible
3. **Camera angle correct** — torso orientation within the expected range for push-ups
4. **Background still** — no major motion outside the person

**The sitting cannot start until all four are green** (FR-019). A red pill says exactly what to fix: *"Add light"* · *"Step back so your whole body is visible"* · *"Lower the phone to chest height"* · *"Find a spot with nothing moving behind you."*

### 8.3 Palm liveness

With all four pills green:

> Show your palm to the camera for 3 seconds.

An open palm held for 3 continuous seconds confirms a live person (FR-020). A 3-second countdown follows and counting begins. **At the start of every sitting, never mid-sitting** — mid-sitting prompts break the flow, and re-checking on each return is what stops one person banking the first 10 reps and another doing the rest.

**If no palm is detected within 30 seconds** (FR-020): the app shows *"Can't see your palm — try more light, and hold it flat toward the camera"* and returns to the pre-flight checks, since light or framing is the likely cause. Retries are unlimited, the member can cancel at any moment, and banked reps are never touched.

### 8.4 Push-up rules

A rep counts only when, within one rep cycle, all of these are detected (FR-021):

1. **Top:** elbow angle > 160°, shoulder–hip–ankle within ±10° of a straight line
2. **Bottom:** elbow angle < 90°, body line held
3. **Back to top:** elbow angle > 160°, body line held
4. **Cycle time:** between 0.8 and 6 seconds

A rep that misses a rule is **discarded silently with a hint** — *"Go lower"* · *"Keep your body straight"* · *"Slow down"* — never an error.

**How those angles are measured (H30, research R4).** The rule above is what the product promises; the counter is built to survive the model's own error. Angles come from 3D world landmarks rather than image coordinates, so they do not depend on where the phone lies; they are smoothed before any comparison; and the state machine triggers on **wider** thresholds than the rule states — around 165° at the top and 85° at the bottom — because the pose model's joint-angle error is roughly 2° and a hard edge would count a rep twice on jitter. Frames where the elbow, shoulder, hip or ankle are not clearly visible are dropped rather than scored. The final trigger values come from spike S2's measured angle table, not from this document.

**Other push-up variants are post-hackathon** (L12). The cohort already records which exercise it is, with negative (eccentric) and decline push-ups reserved, so adding them later changes rules and traces — never an account layout.

The target is the cohort's reps per day, or 25 for the Daily 25. The pose model and the rep state machine on top of it are chosen in `plan.md`.

### 8.6 Banked progress

Reps are **banked in blocks of 10** and kept for the whole day (FR-024).

```
morning   ──  sitting 1:  17 reps   →  banks 10, the 7 extra are lost    banked 10
lunch     ──  sitting 2:  10 reps   →  banks 10                         banked 20
evening   ──  sitting 3:   5 reps   →  day complete at the 25th rep     done
```

- **A block banks the instant its 10th verified rep is counted.** The screen shows it: *"10 banked · 15 to go."*
- **The day completes the moment banked + current reps reach the target**, so the last block can be smaller than 10.
- **When a sitting ends for any reason** — the member stops, the app closes, the phone crashes — reps short of the next block are discarded. Banked blocks stay.
- **No pause limit.** The member comes back whenever they like before the deadline; every return starts with pre-flight and palm liveness again (§8.2, §8.3).
- **On the device only.** No server, no Firebase, nothing on-chain until the day is complete (FR-024a). A lost or wiped phone loses that day's banked reps; the member can still do the whole day from zero on another device (ES-3a).
- **Banked reps expire at the day's deadline** — they never carry into tomorrow (FR-016).
- **Works offline.** Counting and banking need no connection (FR-024b). See §8.7 for what happens when a cohort day completes offline.
- **At most one completed day is recorded per member per day** (FR-025).

### 8.7 Completion

When the rep target is reached:

1. **Cohort day:** submitted on-chain for that day. It counts only if it lands before the deadline, by chain time (FR-013, ES-3). The survivor counter, prize figure and the member's day counter update (US-3).
2. **Daily 25:** recorded on the device and extends the streak. Nothing on-chain, no connection needed (FR-051).
3. A **"Day 12 done"** celebration. No share card, no selfie (spec §3).

**Offline (FR-024b, ES-3b).** A cohort sitting can run with no connection, but the app says so first:

```
      You're offline
      Your reps will count only if today's day reaches
      the network before the deadline — 2h 14m left.
      [ start anyway ]
```

When the day completes offline, it is kept on the phone and the member never redoes anything.

- **Phase 1:** once the app is online again, the home screen shows **"Done — tap to submit · 2h 09m left"**; one tap submits it.
- **[Phase 2]** The app submits it automatically as soon as it has a connection, showing **"Done — waiting for network · 2h 09m left"** until it lands.

Either way: If it lands before the deadline, the day counts. If it lands after, it doesn't — **nothing on the phone can extend a deadline**, and the member is in breach exactly as if they hadn't finished.

**Known weakness:** the completed-day proof is **client-signed** — a rooted phone could forge it. Named in the README with the real fix: a server-side attestation signer (constitution XI).

### 8.8 Attacks and what stops them

| Attack | Mitigation in this build |
|---|---|
| Play a recorded video to the camera | Palm liveness at the start of every sitting; pre-flight rejects a static or wrong-angle scene |
| Freeze the frame mid-sitting | Rep cycle bounds and full-range rules; **no audio filter** — documented weakness |
| Impossibly fast reps | 0.8-second minimum cycle |
| Camera covered or off | Pre-flight requires a live, lit, full-body frame |
| Hand the phone over between sittings | Every sitting re-runs pre-flight and palm liveness (FR-019, FR-020) — raises the bar, doesn't prove identity |
| Someone else does all the reps | Not defended — one wallet is one member and the Genesis gate is off; documented |
| Submit a completed day late, claiming it finished in time offline | Rejected — only chain time at submission counts (FR-017, FR-024b) |
| Edit banked progress on the device | Not defended — banked reps are device-local and the completed-day proof is client-signed; documented |
| Forge the on-device model output | Not defended — needs attested hardware; documented |

---

## 9. On-chain and off-chain responsibilities

Accounts, PDAs, instruction signatures and framework belong to `plan.md`. This section fixes **who is allowed to do what**, which the plan must honour.

### 9.1 What lives on-chain

Cohorts with their fixed parameters and locked chip set · memberships and held stakes · each cohort's prize pool and any held carry-over · the payout denominator, written once · each member's per-league progress (cleared, forfeited, current and longest league streak, the 21-day unlock) · the current chip set · the fee destination · the faucet's pre-funded test SKR and devnet SOL, and when each wallet last used it.

**Not on-chain:** the daily streak, Daily 25 days, banked reps and anything else about an unfinished day, a completed day still waiting for network, camera data. A cohort day goes on-chain only once it is complete and submitted.

**Scripted members are ordinary on-chain members** — the contract has no idea which wallets are scripted (FR-043a).

### 9.2 Principles the contract must satisfy

- **The contract is the only authority on funds** (constitution I). No off-chain service moves, holds or decides the fate of a stake.
- **Breach is derived:** `now > deadline_of(days_completed)`, from chain state alone (constitution II).
- **No instruction reads a price** (constitution III).
- **Parameters are read from the cohort**, never from constants (constitution V).
- **Every automation-callable operation is idempotent, chunkable and permissionless** — two Schedulers, or a Scheduler and a stranger, running at once are harmless, and no caller can choose a parameter, outcome, recipient or amount (constitution VIII, FR-030a).
- **Built for cohorts of up to 1,000 members** with no enforced cap; no single step's cost grows with cohort size (FR-011a).
- **Invariants INV-1…INV-8** (spec §8.4) and the vectors in §19 exist as **failing tests before the logic they describe** (constitution VII).

### 9.3 Operations

State machines, guards and effects are specified in `holdout_scope_and_state_machines.md` §3 — the source for the plan. In summary:

| Operation | Who | What it does |
|---|---|---|
| **open cohort** | **Anyone** (usually the Scheduler) | Once a league's current cohort has started, opens its next one with every parameter and the current chip set fixed; delivers any held carry-over for that league. Does nothing if not due or already done |
| **publish chip set** | **Operator only** | Phase 1: once at launch. **[Phase 2]** weekly check, new set only on >20% drift. Always affects future cohorts only |
| **join cohort** | Member | While the cohort is `Open`; stake must equal one of that cohort's chips; lobby-place rules (§5.2) |
| **leave cohort** | Member | While `Open`; full refund |
| **submit completed day** | Member | Once per day index, when the day's reps are done, while the cohort is `Running` and the member is not in breach |
| **record forfeits** | **Anyone** (usually the Scheduler) | Materialises breaches: 20% fee, 80% to pool, progress updated; safe to repeat, safe to run late, pageable |
| **settle cohort** | **Anyone** (usually the Scheduler) | After the final deadline: finishers counted, denominator written once, shares rounded down with the remainder to the fee destination, progress and unlock updated, 50/50 carry-over if nobody finished; pageable and resumable through `Settling` |
| **claim payout** | Member | Transfers stake plus share; a second claim fails |
| **use faucet** | Any connected wallet | Sends the fixed amount of test SKR and fee SOL; a second use by the same wallet before the next 05:00 UTC boundary fails; no server (FR-042a) |

### 9.4 Flags

| Flag | Value | Effect |
|---|---|---|
| `ladder_gate_enabled` | `false` | 21-day join not gated; unlock still recorded |
| `genesis_gate_enabled` | `false` | No Seeker check |
| `day_seconds` | non-default for test cohorts only | Compressed cycles for testing; admin-guarded; not offered to judges (FR-044) |

**Flags disable checks; they never delete the logic behind them.**

### 9.5 Off-chain components

| Component | Role | Trust |
|---|---|---|
| **Mobile app** | UI, on-device verification, banked progress, completed days waiting for network and their automatic submission, daily streak, transaction signing, reading chain state for counters, rosters and leaderboards | Holds no authority over funds, and cannot extend a deadline |
| **Scheduler** | Opens cohorts on schedule, records forfeits, settles cohorts | No special permission — anyone could do its job; only materialises outcomes already true on-chain |
| **Operator tooling** | Runs the weekly chip check against a rate source and publishes chip sets | Trusted for chip pricing only; named in README |
| **Scripted members** | Ordinary wallets run by automation: join with a real test-SKR stake, submit completed days, forfeit or finish on a planned schedule spread across each cohort. Their forfeits fund real prizes; their finishes take a share (FR-043a) | Same contract rules as anyone. Their days are not camera-verified (part of the client-signed trust gap). Addresses published in the build, so the app labels them with no server (constitution XI, XII) |

**No backend decides deadlines, outcomes or rankings** (H5, H17).

---

## 10. Screens and flows

**Every state in spec §8 has a screen, or is provably unreachable** (constitution IX).

### 10.1 Screen inventory

**First run**
1. **Welcome** — what Holdout is in three lines; *"Test network, test token."*
2. **Connect wallet** — required before anything else (FR-001a). The screen carries the promise in two lines (*"Stake on yourself. Prove it every day."*), one line each for **the Daily 25** and **a Holdout** so the free and staked paths are unmistakable, and the **live Holdouts**: a running one with its still-standing count and prize pool, the open one with how many have joined and what is at stake. Primary action **Connect wallet**; under it, quietly, *"No wallet yet?"* and *"For judges: full cycle, 3 min"*, which opens the walkthrough (13) with no wallet connected — the single exception to FR-001a. **No-wallet variant:** *"Holdout needs a Solana wallet — even for the free Daily 25."* with how to get one (ES-0)
2a. **How it works** — the three-card first-run tour, shown once the wallet connects and re-openable later (FR-067): the Daily 25; what a Holdout is; how reps are counted on the device. Skippable at any card. A short explainer video joins it after cohort #3 is filmed
3. **Disclosure** — Holdouts during judging include scripted members: real wallets playing by the same rules, labelled wherever they appear (FR-043a)

**Funds**
3a. **Get test tokens** — the in-app faucet (§6.1): test SKR and fee SOL, once a day. Variants: **already used today** (*"Available again in 6h 40m"*), **faucet can't pay out** (ES-0a, ES-0b)

**Home**
4. **Home** — the running Holdout (§10.1a) if the member is in one; otherwise **today's Daily 25 as the primary action**, with the Holdouts open to join underneath — each showing how many have joined, **what is at stake**, any carry-over, and the closing countdown (FR-053). Daily streak always visible. Never empty, never *"come back later"*. **Offline-completion variant:** *"Done — tap to submit"* once online (Phase 1), or *"Done — waiting for network"* with automatic submission **[Phase 2]** (FR-024b)

**Ladder and staking**
5. **Ladder** — the Daily 25, then the 7-day and 21-day Holdouts both open, the 21-day requirement stated plainly, Human Flag as a locked rung (US-1)
6. **Stake confirm** — league, chip picker (§6.3), daily obligation, duration, time until joining closes and until start, current prize pool with any carry-over shown separately, and: *"If nobody drops out, you get exactly your stake back."* Test-network notice (US-6, FR-041, FR-042)
7. **Lobby** — countdown to cutoff and start, live roster count, held stake, leave button (§5.2). **Locked variant:** joining closed, final roster, countdown to start (ES-12a)

**Sitting**
8. **Sitting** — start card (*"10 banked · 15 to go · 5h 12m left today"*) → pre-flight (four pills) → palm liveness → 3-second countdown → counting (camera, skeleton overlay, rep counter, banked blocks, form hint, time left today) → block banked (*"20 banked"*) → day complete. Variants: **stopped part-way** (*"17 done — 10 banked, 7 not kept. Come back any time before 5h 12m runs out."*), **reopened after a crash** (*"Your 10 banked reps are safe."*), **last 10 minutes** (warning on the start card and during counting, never a block — FR-047), **palm not detected** (tip after 30 seconds, back to pre-flight — FR-020), **offline** (warning before a cohort sitting; *"Done — waiting for network"* on completion — FR-024b)

**Outcomes**
9. **Forfeit** — *"Your 21-day run ended on day 6. Your stake went to the members still standing. Your 9-day streak is still alive."* CTAs: **Do today's challenge** · **Join the next 7-day cohort** (US-4)
10. **Claim** — finisher payout in SKR, **Claim** button, signing, success. Unclaimed payouts stay reachable from the cohort detail (US-8, FR-035)

**Crowd and progress**
11. **Cohort detail** — days remaining, own completion record, projected share, survivor history (US-7)
12. **Cohort roster** — members still standing, then forfeited members by days lasted, scripted members labelled (Phase 1, FR-062). **League leaderboards** **[Phase 2]** (§12.3, US-10)

**Demo**
13. **Walkthrough — for judges** (About section plus the connect-screen link; members get the tour, 2a): the recorded full cycle, in order: launching the app, connecting a wallet, the faucet, the ladder, staking, the lobby, a sitting, a completed day, the crowd thinning, a forfeit, settlement and the claim (US-9, FR-044). Taken from cohort #3 on devnet, bundled in the APK, playable offline and without a wallet. Until it is recorded: *"Walkthrough recording in progress."* Storyboard and production detail: `holdout_demo_video.md`

### 10.1a The home screen

**The crowd is the product.** Fixed cohorts mean there is a crowd, and it visibly shrinks.

```
  ·············· black canvas, no app bar ··············

      21-DAY · cohort 14 · day 12 of 21        ← label, tertiary

                189 / 312                      ← counter 56sp, white
              still standing                   ← label, secondary

      ┌───────────────────────────────────┐    ← card: surface/1, 24dp
      │ prize pool          48,200 SKR    │      amounts in mono
      │ your share now         255 SKR    │
      │                        ≈ $6       │    ← tertiary, approximate
      │ ▲ +17 SKR — three dropped since   │    ← success green
      │   yesterday                       │
      └───────────────────────────────────┘

        [  10 banked · do 15 more  ]           ← white pill, black label
             ◍ bank ring: 1 of 3 segments

        🔥 12              4h 06m left today   ← mono, accent

  ────────────────────────────────────────     ← nav: surface/2 + hairline
     ⌂        ◎        ☰        ◍              ← active item = white pill
```

1. **The survivor counter and the prize are the largest things on screen** (FR-038).
2. **The drop happens at the deadline and is announced.** At 05:00 UTC every device's counter already excludes everyone who missed the day, whether or not their forfeits have been recorded (FR-038a). On the next open: *"Three dropped since yesterday. Your share went up 17 SKR."* No push infrastructure in this build — notifications to testers are sent by hand.
3. **Both numbers move together in the member's favour.** Every forfeit grows the pool and shrinks the divisor, so the share accelerates as the cohort thins.

Supporting beats: halfway (*"day 11 of 21 — 189 still standing"*), endgame (*"only 12 left of 340"*).

With no cohort running, the challenge is the hero:

```
              TODAY'S CHALLENGE               ← label, tertiary

                  25                          ← counter 56sp, white
               push-ups                       ← label, secondary

              [  start  ]                     ← white pill, black label
      do them all now, or 10 at a time today  ← body, secondary
         🔥 12-day streak · 4h 06m left today ← mono, accent
```

### 10.2 First reps in under three minutes

```
Open app (first run)
  ↓  connect wallet (required — no wallet, no app) · test-network notice · scripted-member disclosure
Home: today's challenge
  ↓  start
Pre-flight → palm → countdown → push-ups (banked every 10)
  ↓
Done at 25 — streak 1 — "Want money on it? Join a 7-day cohort."
   or stop early — "10 banked. Come back later today for 15 more."
```

**Staking path:** Ladder → 7-day league → chip → *(no test SKR? one-tap faucet)* → confirm → sign → Lobby.
**Target:** a returning member opens the app and starts counting in under 90 seconds.

### 10.3 The daily loop

- **Open:** survivor counter and prize — already updated at the deadline — with the changes announced.
- **Do the reps** before the countdown ends — all at once, or in blocks of 10 across the day.
- **After the last rep:** counter, prize and day counter update at once (US-3).
- **Next day:** the new day is shown, never a stale "done" state (ES-9).

### 10.4 Error and edge states

| State | Screen |
|---|---|
| No wallet | *"Holdout needs a Solana wallet — even for the free Daily 25."* How to get one; nothing else available until connected (ES-0) |
| No test SKR or fee SOL | The faucet sheet, in place — on the stake screen, before submitting a completed day, before a claim (ES-0a) |
| Faucet already used today | *"You've had today's test tokens. Available again in 6h 40m."* (ES-0a) |
| Faucet can't pay out | *"The faucet can't send tokens right now."* The Daily 25 and browsing stay available (ES-0b) |
| Palm not detected for 30 seconds | *"Can't see your palm — try more light, and hold it flat toward the camera."* Back to pre-flight; retry or cancel (FR-020) |
| Offline before a cohort sitting | *"You're offline. Today counts only if it reaches the network before the deadline — 2h 14m left."* **Start anyway** (FR-024b) |
| Day completed offline | Phase 1: home shows *"Done — tap to submit"* with the countdown once online. **[Phase 2]** *"Done — waiting for network"*, submitted automatically on reconnect. If it lands late, the forfeit screen (ES-3b) |
| Pre-flight fails three times | Tip sheet with the common fixes |
| App crashed mid-sitting | *"Your 10 banked reps are safe. 15 to go."* Reps since the last block are gone (ES-1) |
| Sitting stopped part-way | *"17 done — 10 banked, 7 not kept."* Countdown to the deadline (ES-2) |
| Last 10 minutes of the day | *"8m left. 15 reps to go — reps not finished by the deadline won't count."* Shown on the start card and during counting; the sitting is always allowed (FR-047) |
| Deadline passed with reps missing | In a cohort: the forfeit screen (§10.1 #9). Daily 25: *"Yesterday ended at 20 of 25. Today starts fresh."* (ES-3) |
| New or wiped phone mid-day | *"No banked reps on this device today. 25 to go."* (ES-3a) |
| Join or leave landed after the cutoff | *"Joining closed before your transaction landed. Nothing was taken."* / *"Leaving closed before your transaction landed. You're in the cohort."* (ES-12) |
| Cohort is locked | Final roster, countdown to start, no actions (ES-12a) |
| Lobby place refused | *"This cohort starts before your 21-day run ends."* (FR-006b) |
| Member in breach tries to submit | *"The deadline passed before your day was complete. Your run in this cohort has ended."* (ES-4) |
| League's next cohort not open yet | *"The next cohort opens soon."* Daily 25 offered (ES-15) |

---

## 11. Design system

**Direction: native on Seeker.** The app should look like it shipped with the device — black canvas, quiet chrome, the numbers carrying the screen — and be built from Solana's own palette, so nothing looks borrowed. Every value below is sampled from the Solana brand kit (`Color Palettes/Solana Brand Colors.png`, `Gradient/solanaGradient.png`).

**Dark only.** One theme, no light variant, and **Material You dynamic colour is disabled** — brand colours are fixed.

### 11.1 Colour tokens

**Canvas and surfaces** — pure black like the Seeker system UI, with surfaces tinted very slightly teal so cards separate without a shadow.

| Token | Value | Use |
|---|---|---|
| `canvas` | `#000000` | App background; the camera and walkthrough screens sit straight on it |
| `surface/1` | `#0B1214` | Cards, list rows |
| `surface/2` | `#121B1D` | Bottom nav, sheets, dialogs |
| `surface/3` | `#1A2426` | Pressed states, chips, input fields |
| `hairline` | `#FFFFFF` at 10% | 1dp borders — the only separation on black; no shadows anywhere |
| `scrim` | `#000000` at 60% | Over the camera preview, so overlaid text stays legible |

**Text**

| Token | Value | Contrast on `canvas` | Use |
|---|---|---|---|
| `text/primary` | `#FFFFFF` | 21:1 | Numbers, headlines, body |
| `text/secondary` | `#FFFFFF` at 70% | 11.3:1 | Captions under a number, supporting copy |
| `text/tertiary` | `#FFFFFF` at 48% | 4.9:1 | Labels, inactive nav, metadata |

**Brand and state** — from the Solana palette.

| Token | Hex | Palette name | Contrast | Use |
|---|---|---|---|---|
| `accent` | `#80ECFF` | Tide Light | 15.4:1 | The interactive colour: links, selection, focus, progress, the skeleton overlay, countdowns |
| `accent/strong` | `#00DAFF` | Tide | 12.5:1 | Rare emphasis on an accent surface |
| `success` | `#14F195` | Solana Green | 14.0:1 | Still standing, day complete, claim, confirmed transactions |
| `warning` | `#FF623A` | Coral | 7.0:1 | Forfeit, the last-10-minutes warning, faucet exhausted. Never a harsh red |
| `celebration` | `#9945FF` | Solana Purple | 4.6:1 | Decoration only — never text under 24sp |
| `gradient/solana` | `#9945FF` → `#14F195`, 45° | Solana Gradient | — | Reserved for two moments: day complete and claim success |
| `onLight` | `#000000` | — | 21:1 | Label on the white pill button |

Extra palette entries are available when a screen needs a second hue — Sky `#0075FF`, Charge `#FF40C0`, Kelp `#59FF36`, Beach `#D0E300` — but a screen uses at most one of them.

**Material 3 mapping**, so the Compose theme is a transcription rather than a reinterpretation:

| M3 slot | Token |
|---|---|
| `background` / `onBackground` | `canvas` / `text/primary` |
| `surface` / `onSurface` | `surface/1` / `text/primary` |
| `surfaceContainer` / `surfaceContainerHigh` | `surface/2` / `surface/3` |
| `onSurfaceVariant` | `text/secondary` |
| `primary` / `onPrimary` | `accent` / `#002731` |
| `secondary` / `onSecondary` | `success` / `#00110A` |
| `tertiary` | `celebration` |
| `error` / `onError` | `warning` / `#1A0400` |
| `outline` | `hairline` |
| `inverseSurface` / `inverseOnSurface` | `#FFFFFF` / `onLight` (the primary button) |

### 11.2 Typography

**Roboto / Roboto Flex** — the Android system family, so the app matches the Seeker's own UI, nothing is bundled and no licence is needed. **JetBrains Mono** (open licence, bundled) carries amounts and countdowns, echoing the semi-mono numerals in Solana's brand type without licensing it.

| Style | Family, weight, size | Use |
|---|---|---|
| `counter` | Roboto Flex 300, 56sp, tracking −1%, **tabular** | The survivor counter, the rep count |
| `display` | Roboto Flex 300, 40sp, tabular | Claim amount, streak |
| `headline` | Roboto 500, 28sp | Screen titles |
| `title` | Roboto 500, 20sp | Card titles, buttons |
| `body` | Roboto 400, 16sp / 24 line | Copy |
| `label` | Roboto 500, 13sp, tracking +2%, uppercase | Captions under numbers, nav labels, chips |
| `amount` | JetBrains Mono 400, 20sp, tabular | SKR amounts, countdowns, wallet addresses |

Tabular figures everywhere a number changes, so counters and timers never jitter.

### 11.3 Spacing, shape and depth

- **Spacing:** 4dp base; 20dp screen gutters; 20dp inside cards; 24dp between sections.
- **Radius:** cards 24dp; sheets 28dp on top corners; chips 16dp; buttons and nav pill fully rounded.
- **Depth:** none. No shadows on black — a surface level plus a hairline does the separating.
- **Targets:** 48dp minimum, and the primary button is 56dp tall.

### 11.4 Components

Mirrored from the Seeker system so the app feels like part of it:

| Component | Spec |
|---|---|
| **Bottom nav** | 64dp bar on `surface/2` with a hairline on top; the active item is a white rounded pill with a black icon; inactive icons `text/tertiary` |
| **Primary button** | Full-width pill, 56dp, white fill, `onLight` label in `title` |
| **Secondary button** | Same shape, transparent, `hairline` border, white label |
| **Quiet action** | Text button in `accent`; `warning` when it is a leave or a stop |
| **Card** | `surface/1`, 24dp radius, hairline, 20dp padding |
| **Stat block** | A big `counter` or `display` number with a `label` caption under it — the Seeker's "1,000 SKR / your allocation" pattern, which is exactly how the survivor count and prize read |
| **Tier chips** | Four across, `surface/3`, selected takes an `accent` border and an `accent` fill at 12% |
| **Bank ring** | Progress ring around the rep count, 4dp `accent` stroke on a `hairline` track, one segment per banked block of 10 |
| **Sheet** | `surface/2`, 28dp top radius, white 24% drag handle — used for the faucet and stake confirm |
| **Roster row** | 56dp; address in `amount`; days lasted right-aligned; scripted members carry a `label` chip |
| **Camera sitting** | Full-bleed preview, `scrim` at top and bottom, rep count in `counter` over it, skeleton overlay in `accent` at 80% |
| **Countdown** | `amount` in `accent`, turning `warning` inside the last 10 minutes (FR-047) |

### 11.5 Motion

- 200 ms standard, 300 ms for anything entering the screen.
- The survivor counter **rolls** to its new value rather than cutting — it is the product's one dramatic number.
- Day complete and claim play a single `gradient/solana` sweep, about 900 ms, once. Nothing else in the app uses the gradient.
- No confetti, no crypto stickers.

### 11.6 Accessibility

- Body text at or above 4.5:1 on its surface; numbers 24sp and above at or above 3:1. The table in §11.1 lists measured ratios.
- `celebration` purple is decoration only — it fails small-text contrast on black.
- 48dp targets, visible focus in `accent`, and text over the camera always sits on a `scrim`.

### 11.7 Design workflow

No designer for the build. Screens are prototyped, critiqued and handed off with Claude's design tooling:

1. Prototype the screen as an interactive artifact built from these tokens
2. Iterate until it feels right
3. Design critique pass
4. Accessibility pass (WCAG 2.1 AA: contrast, hit targets, focus)
5. Handoff — this section, plus the prototype, is what the Compose theme is written from

Thirteen screens (§10.1), each with every state it can be in. The four that carry the demo video — home in a cohort, the sitting while counting, stake confirm, claim — are prototyped first.

### 11.8 Brand boundaries

- Solana's palette and gradient are used as colour; **ABC Diatype and ABC Diatype Semi-Mono are not** — they are licensed to Solana for Solana's own materials, so they are never bundled in the APK or committed to this repository.
- The app's icon and wordmark are Holdout's own. Solana's logomark appears only where its brand guidelines allow, and nothing in the app or the store listing implies an official Solana or Seeker product.

---

## 12. Streaks and leaderboards

### 12.1 Two streaks, never conflated

| | **Daily streak** | **League streak** |
|---|---|---|
| Counts | days in a row with a completed day — challenge or cohort | cohorts of one league cleared in a row |
| Lives | on the device | on-chain progress |
| Costs money | no | yes — only cohorts count |
| Survives a forfeit | **yes** (FR-052) | **no** — resets in that league (FR-028) |
| Shown | home screen, big numeral with a flame | leaderboard and cohort detail |
| On a leaderboard | **never** (FR-051) | yes — as consistency (FR-061) |

### 12.2 The daily streak

- Completing the Daily 25, or a cohort day, extends it by one; a day that ends without a completed day breaks it — banked reps short of 25 don't keep it alive (FR-049, FR-050).
- It **resets on the same 05:00 UTC instant** as the cohort day — one notion of "today" (OD-6).
- **Cosmetic and personal:** it unlocks nothing, gates nothing, and nothing is ever paid against it (FR-051).

| State | Visual |
|---|---|
| Active | Flame, number in `text/primary`, subtle pulse |
| Extended today | Flame bursts, number flips up in `success` |
| Broken | Cracked flame, number drops to `text/tertiary`: *"Your 14-day streak broke. Start a new one today."* No save button — there is no grace day |
| New personal best | Flame in `celebration` purple with a **PB** mark |

### 12.3 Leaderboards

> **Phasing:** the league boards in this section are **[Phase 2]**. In Phase 1, on-chain progress is recorded from the first cohort and the cohort roster ships; there is no league board screen.

**Built to show who is disciplined**, entirely from on-chain progress — no server, nothing reported by a phone (FR-060, FR-065).

```
      7-DAY LEADERBOARD

  #   member        survived   played   cleared   streak (best)
  1   7xK2…9fQa       100%        6        6        6 (6)
  2   Bq41…m8Lr       100%        4        4        4 (4)
  3   9dTe…2wPc  ◇     86%        7        6        2 (4)
  4   F3nA…k7Hs        75%        4        3        0 (3)
      …
      NOT RANKED YET
      Hc8v…1xJd       100%        2        2        2 (2)   1 more cohort
      you             100%        1        1        1 (1)   2 more cohorts

  ◇ scripted member
```

- **Survival rate** = cohorts cleared ÷ cohorts played. **Cohorts played** = cleared + forfeited; running, locked and left cohorts don't count (FR-060a).
- **Ranked** once a member has played **3** cohorts in that league, highest survival first, compared exactly. Ties: more cohorts played, then longer best streak, then whoever got there first (FR-060b).
- Below 3: **"not ranked yet"**, with figures and how many more cohorts they need (FR-060c). Percentages display as whole numbers.
- **Cohort roster:** members still standing first, then forfeited members by how many days they lasted (FR-062).
- **Shortened wallet addresses only. No stake, prize or payout amount — ever** (FR-063, constitution XIII).
- **Scripted members are labelled** on every board and roster (FR-064, constitution XII). The label comes from the list of scripted wallet addresses published in the build, so it needs no server and anyone can check it (FR-043a). Scripted members' figures are real on-chain progress, not display padding.

**Judging-month note:** in the 21-day league, 3 cohorts takes 9 weeks, so only scripted members can be ranked there during judging (H17).

### 12.4 No social features

No feed, messaging, following, comments, reactions, share cards, badges, referrals or selfies. The survivor counter and the leaderboards are the social presence (spec §3).

---

## 13. Honest language

Constitution XIII, applied to every screen, notification and README line.

| Use | Never |
|---|---|
| prize, prize pool, payout, stake, cohort, challenge, still standing, finish, claim | winnings, returns, yield, earnings |
| "do your reps", "10 banked", "15 to go" | bet, wager, gamble, odds, luck, jackpot, pot |
| `≈ $25` next to a tier | a dollar figure presented as what a member will receive |

**Always stated:**

- *"Test network, test token."* — first run and every staking screen (FR-042)
- *"If nobody drops out, you get exactly your stake back."* — before every join (FR-041)
- *"Stakes and payouts are in SKR; dollar values are approximate."* — wherever a USD label appears (FR-039)
- *"Some members in these cohorts are scripted."* — first run, and a label on each scripted member (FR-043, FR-064)

**Framing:** Holdout is a contest of physical consistency, not a game of chance. Legal review, terms of service and geo-blocking are out of scope for a devnet build with a test token.

---

## 14. Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| Rep counter rejects honest reps | High | Pre-flight refuses bad conditions; silent discard with hints; test against real video in the risk spike |
| Rep counter accepts cheats | Medium | Palm liveness, cycle bounds, full-range rules; no audio filter and client-signed proofs documented as known weaknesses |
| Scheduler misses a run | Low | Breach is derived, so outcomes never change; counters move at the deadline anyway; any wallet can record forfeits, settle and open cohorts; acceptance criteria 5 and 8 |
| A 1,000-member cohort is too heavy to process | Medium | Forfeits and settlement paged, no step grows with cohort size; tested at 1,000 with pages out of order and repeated (acceptance criterion 7) |
| Members confused by banking | Medium | Always show *"10 banked · 15 to go"*; say which reps weren't kept when a sitting ends part-way |
| Banked progress edited on a rooted phone | Low | Devnet stakes only; documented with the client-signed proof as a known weakness |
| Cohorts look empty during judging | High | Scripted members on the weekly schedule, forfeits spread across each cohort, labelled everywhere |
| Scripted members read as deception | High | Disclosed on first run and in the README, labelled on every roster and board |
| Wallet signing fails on real hardware | High | Risk spike before implementation |
| Judges give up at "connect a wallet" | High | Wallet-first is deliberate (FR-001a); the no-wallet screen says plainly why and how to get one, and offers the recorded walkthrough, which plays without a wallet and shows the whole product — as does the README |
| Faucet drained or empty during judging | Medium | Once per wallet per day; funded in advance for the whole month; a clear message if it can't pay out, with the Daily 25 still available |
| Members lose a day by finishing offline | Medium | Warning before an offline cohort sitting and on completion; automatic submission on reconnect |
| Palm check fails for honest members | Medium | 30-second limit with a tip, back to pre-flight, unlimited retries, banked reps untouched |
| Real finishers' prizes include scripted members' money | Low | By design and disclosed: scripted members are real wallets under the same rules (FR-043a) |
| Settlement bug on devnet | High | Vectors (§19) and invariants as failing tests first; `day_seconds` rehearsal of full cycles |
| Rate source unavailable at the weekly check | Low | Current chip set stays; joining never depends on a price |
| Testers confused by the 05:00 UTC boundary | Medium | Countdown, never a date; last-10-minutes warning before and during every sitting |
| Leaderboard reads too slow from chain | Medium | Read strategy decided at plan (decisions P8); never show unsupported figures. League boards are Phase 2; the roster read is Phase 1 |
| Phase 1 overruns and leaves no room for Phase 2 | High | Expected and accepted: Phase 2 items are cut at the freeze with their Phase 1 stand-ins in place (spec §3a, H23) |
| Feature freeze slips past 28 September | High | Scope fixed by the spec; additions only by amending it |

---

## 19. Appendix A — settlement test vectors

The model in §7: one pool per cohort, 20% fee taken at forfeit, share proportional to stake among finishers. **The contract must reproduce these exactly.** Amounts are whole SKR and chosen to divide exactly; the chip amounts (100 / 250 / 500 / 1,000 SKR) are illustrative — only SKR arithmetic matters.

`pool = 0.80 × Σ forfeited + carry-over received` · `payout = stake + pool × stake ÷ Σ finisher stakes`

### 19.1 Uniform stakes

| # | League | Members | Stake | Forfeits | Fee | Pool | Finishers | Σ finisher stake | Share each | Payout each |
|---|---|---|---|---|---|---|---|---|---|---|
| V1 | 7-day | 10 | 100 | 5 | 100 | 400 | 5 | 500 | 80 | 180 |
| V2 | 21-day | 20 | 500 | 12 | 1,200 | 4,800 | 8 | 4,000 | 600 | 1,100 |
| V3 | 7-day | 8 | 1,000 | 6 | 1,200 | 4,800 | 2 | 2,000 | 2,400 | 3,400 |

### 19.2 Mixed tiers — proportionality

**V4 · 21-day, 10 members.** Stakes: 3×100, 4×250, 2×500, 1×1,000 (Σ 3,300).
Forfeits: 3×100, 2×250, 1×500 = **1,300 forfeited** → fee 260 → **pool 1,040**.
Finishers: 2×250, 1×500, 1×1,000 (Σ 2,000).

| Finisher stake | Share | Payout |
|---|---|---|
| 250 | 130 | 380 |
| 250 | 130 | 380 |
| 500 | 260 | 760 |
| 1,000 | 520 | 1,520 |
| **Σ 2,000** | **1,040** | **3,040** |

**Invariant:** Σ payouts = Σ finisher stakes + pool = 2,000 + 1,040 = 3,040.

### 19.3 Carry-over

**V5 · nobody finishes, then carry-over is shared.**
Cohort A (7-day): 6 members × 250, all forfeit → forfeited 1,500 → fee 300 → pool 1,200. Settles with no finishers → **600** to the fee destination, **600** carry-over to cohort B, the open 7-day cohort.
Cohort B (7-day): 5 members × 100, 1 forfeits → fee 20 → 80 + 600 carry-over = **pool 680**. Finishers 4, Σ 400 → share **170** each → payout **270** each.
**Check:** the fee destination receives 300 + 600 + 20 = 920; B's payouts total 1,080 = 400 + 680; and 920 + 1,080 = 2,000, everything staked across A and B.

**V6 · odd pool split.** A no-finisher cohort's pool is 1,001 base units → **500** to the fee destination, **501** carry-over. The indivisible unit goes to the carry-over (FR-037).

**V7 · no open cohort.** V5's cohort A settles while no 7-day cohort is open → the 600 is held and recorded against A (Phase 1) and **[Phase 2]** appears in the next 7-day cohort's pool **when it opens**, exactly once. A second delivery attempt changes nothing (FR-037a, INV-6).

**V8 · carry-over, no finishers again.** Cohort B receives 600 carry-over, all 5 members forfeit → pool 400 + 600 = 1,000 → **500** fee, **500** carry-over onward (ES-7b).

### 19.4 Edge cases

| # | Case | Required behaviour |
|---|---|---|
| V9 | **Zero forfeits, no carry-over** | Pool 0. Every finisher is paid exactly their stake. No fee. Settlement must not divide by zero or fail. |
| V10 | **One finisher** | Stake plus the entire pool. |
| V11 | **Leave while `Open`** | Full stake returned, no fee, member count falls. |
| V12 | **Leave or join after the cutoff** | Rejected by chain time; nothing moves (ES-12). |
| V13 | **Forfeit while holding a lobby place** | The running stake is forfeited as normal; the lobby place and its held stake are untouched (ES-14). |
| V14 | **Share rounding** | Integer base units; each share is rounded down. The remainder — fewer base units than there are finishers — goes to the fee destination **at settlement** and is recorded on the cohort; afterwards the pool holds exactly the shares (FR-032a). Worked in V17. |
| V15 | **Double claim** | A second claim on the same membership fails; claiming and the transfer happen together. |
| V16 | **Breach before the Scheduler runs** | The member's completed-day submission is rejected even though no forfeit has been recorded yet (INV-8); the projected pool already includes 80% of their stake (FR-038a). |

**V17 · rounding remainder.** 7-day cohort, 8 members: 5 × 250 forfeit, 3 × 100 finish.
Forfeited 1,250 → fee 250 → **pool 1,000**. Σ finisher stake 300.
Each share = floor(1,000 × 100 ÷ 300) = floor(333.33…) = **333** → payout **433** each.
Σ shares 999 → **remainder 1** to the fee destination at settlement.
**Check:** Σ payouts 1,299 + remainder 1 = 1,300 = 300 + 1,000. The fee destination receives 250 + 1 = 251 from this cohort.

**V20 · late offline completion.** A 7-day member completes day 3 offline with 20 minutes left and reconnects 25 minutes later. The submission lands after the deadline → rejected; the member is in breach and forfeits exactly as in V16. In a second run they reconnect with 5 minutes left → the day counts (FR-024b, ES-3b).

**V21 · faucet limit.** A fresh wallet uses the faucet → receives the fixed test SKR and fee SOL. A second use before the next 05:00 UTC boundary fails and moves nothing; a use after the boundary succeeds (FR-042a). The faucet never touches a stake, a pool or the fee destination.

**V22 · scripted forfeits fund real prizes.** In V1, the 5 forfeiting members are scripted and the 5 finishers are real → each real finisher's payout is still 180. Swapping which members are scripted changes no amount (FR-043a).

**V18 · permissionless steps.** With the Scheduler stopped, an unrelated wallet records V2's forfeits, settles it and opens the league's next cohort → state is byte-identical to the Scheduler doing it. The same wallet calling any step early, twice, or with other arguments changes nothing (FR-030a).

**V19 · 1,000-member cohort.** 21-day cohort of 1,000 × 100 SKR, 400 forfeit → fee 8,000 → pool 32,000; 600 finishers, Σ 60,000 → share floor(32,000 × 100 ÷ 60,000) = floor(53.33…) = **53** → payout 153; Σ shares 31,800 → **remainder 200**. Processed in pages, run out of order and repeated, the result is identical (FR-011a). The remainder (200) is fewer units than there are finishers (600), as it always must be.

### 19.5 Fee accounting

For every cohort: `fee = 0.20 × Σ forfeited`, taken at forfeit time. Cross-checks at settlement:

- `fee + (pool − carry-over received) = Σ forfeited`
- With finishers: `Σ shares + rounding remainder = pool`, and the remainder goes to the fee destination
- With no finishers: `fee share + carry-over sent = pool`

---

## 20. Appendix B — glossary

| Term | Meaning |
|---|---|
| **The Daily 25** | What members call the free tier: 25 verified push-ups, every day, alone, nothing staked. Extends the daily streak. Written as "the daily challenge" before 2026-09-17 (H28) |
| **A Holdout** | What members call a staked run — "a 7-day Holdout", "a 21-day Holdout". The system stores it as a `Cohort` in one of the two leagues; spec §1a maps the two vocabularies |
| **At stake** | The sum of the stakes held in a Holdout. What a lobby shows, and never called a prize: only forfeits create a prize pool |
| **Daily streak** | Days in a row with a completed day, challenge or cohort. On the device, cosmetic, survives a forfeit, never on a leaderboard |
| **Sitting** | One continuous run of pre-flight, palm liveness and counting. A day can take any number of sittings; each re-verifies |
| **Banked block** | 10 verified reps saved on the device the instant the 10th is counted. Kept until the day's deadline; reps short of a block are lost when a sitting ends |
| **Completed day** | The banked total reaching the day's requirement. A cohort day is then recorded on-chain; a challenge day only on the device |
| **League** | The 7-day or 21-day staked league, both 25 reps a day — a Holdout's length. Human Flag is a locked rung in the UI only |
| **Cohort** | The stored name for a Holdout: members of one league who start and finish at the same instants. Starts weekly, Monday 05:00 UTC |
| **Day boundary** | The single 05:00 UTC instant at which every cohort day, and the streak day, turns over |
| **Join cutoff** | 1 hour before a cohort's start. Joining and leaving both close |
| **Locked** | A cohort between its join cutoff and its start: roster final, no actions |
| **Lobby place** | A membership in a cohort that hasn't started. At most one, allowed alongside a running cohort if it starts no earlier than that cohort ends |
| **Chip** | One of four stake tiers — $10, $25, $50, $100 — each an exact SKR amount |
| **Chip set** | The four SKR amounts. Re-checked weekly, replaced on >20% drift, locked into each cohort at creation |
| **Held stake** | A member's own SKR, kept apart from the prize pool and returned in full to a finisher |
| **Forfeit** | Losing the entire stake after a missed day: 20% fee, 80% to the pool |
| **Breach** | A day's deadline passing without a completed day. Derived from chain time; counters treat the member as out from that instant, before any forfeit is recorded |
| **Fee** | 20% of each forfeited stake. The only fee |
| **Prize pool** | One cohort's 80% of forfeited stakes, plus any carry-over received |
| **Carry-over** | Half the pool of a cohort where nobody finished, added to the next open cohort of the same league |
| **Survivor counter** | `N / M` — members still standing out of starters |
| **Projected share** | What a member would receive if the cohort settled now |
| **Settlement** | The one cohort-wide event after the final deadline: finishers counted, denominator written once, shares rounded down with the remainder to the fee destination, payouts claimable. Anyone may trigger it |
| **Rounding remainder** | Pool minus the sum of rounded-down shares; fewer units than finishers; sent to the fee destination at settlement |
| **Claim** | The member's own signed transaction that collects stake plus share |
| **League streak** | Consecutive cleared cohorts in one league. Resets on a forfeit in that league. The leaderboard's consistency measure |
| **Survival rate** | Cohorts cleared ÷ cohorts played in a league |
| **Cohorts played** | Cohorts of a league finished — cleared or forfeited |
| **Unlock** | The permanent record that a member cleared a 7-day cohort; gates the 21-day league when the flag is on |
| **Scheduler** | Automation that normally opens cohorts, records forfeits and settles cohorts. No special permission — anyone may do the same. Never decides outcomes |
| **Operator** | Publishes the chip set after the weekly rate check. The only privileged actor |
| **Permissionless step** | Opening a cohort, recording forfeits or settling: any wallet may trigger it once due, and nobody can influence its result |
| **Scripted member** | An ordinary wallet run by automation in judging-period cohorts, under exactly the same contract rules. Its forfeits fund real prizes; its days are not camera-verified. Address published in the build; disclosed and labelled everywhere |
| **Faucet** | The in-app source of test SKR and devnet fee SOL: once per wallet per day, funded in advance, no server |
| **Waiting for network** | A completed cohort day kept on the phone until it can be submitted; it counts only if it lands before the deadline |
| **Walkthrough** | The recording of a full cycle on the real app and devnet contract, inside the app |
| **Pre-flight check** | Four visual environment checks before every sitting |
| **Palm liveness** | An open palm held for 3 seconds at the start of every sitting; after 30 seconds without one, a tip and back to pre-flight |

---

*End of document. Next: `/speckit-plan`.*
