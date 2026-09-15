# Holdout — build specification

**Version:** 1.2 · **Date:** 2026-09-14 · **Status:** post-hackathon product vision — **not the hackathon build**

> **Superseded for the hackathon build (2026-09-15).** What is being built is defined by `.specify/memory/constitution.md` (v1.2.0) and `specs/001-holdout-hackathon/spec.md`, which override this document wherever they differ. The major divergences — SKR only with no USDC or swap, two staked leagues over a free daily challenge instead of three leagues with a free qualifier, no free cohorts, one 05:00 UTC clock instead of local-time deadlines, a 1-hour join cutoff, an unconditional lobby place instead of conditional pre-join, USD-tier chips priced in SKR, a 50/50 split when nobody finishes, no audio in verification, no Firebase, on-chain leaderboards ranked by survival rate — are listed in `holdout_decisions.md` §3. **Do not write the contract or the IDL spec from this file.**

**This document says what the full product would be.** Rationale, rejected alternatives, revision history, rollout plans, success metrics and the pitch material for this vision live in **`archive/holdout_decisions_pre-hackathon-spec_2026-09-15.md`**; the decisions governing the hackathon build live in **`holdout_decisions.md`**.

| Companion file | Holds |
|---|---|
| `archive/holdout_decisions_pre-hackathon-spec_2026-09-15.md` | Decision records D1–D16 and O1–O12 for this vision, metric thresholds, changelog, vision/positioning, rollout, launch checklist |
| `holdout_decisions.md` | Decision record H1–H17 for the hackathon build |
| `pool_mechanics_findings.md` | Simulated economics and what they mean |
| `pool_mechanics_sim.py` | Regenerates every economic figure — `python3 pool_mechanics_sim.py` |
| `competitor_analysis_squadletics.md` | Incumbent teardown |

**Section numbering is unchanged from v1.1** so existing cross-references stay valid. §2, §3, §15, §16, §17 and §18 have moved to `holdout_decisions.md`.

---

## Contents

- **§1** Executive summary
- **§4** Target user and access model
- **§5** Core challenge mechanic — *the ladder, cohorts, obligation, failure, settlement*
- **§6** Tokenomics and fee flows
- **§7** Settlement and pool design — *the payout math*
- **§8** Exercise tracking and anti-cheat
- **§9** Smart contract architecture — *data structures and operations*
- **§10** Mobile app — screen inventory and flows
- **§11** Design system
- **§12** Social sharing and viral loops
- **§13** Regulatory constraints
- **§14** Risk register
- **§19** Appendix A — settlement test vectors
- **§20** Appendix B — glossary

---
## 1. Executive summary

Holdout is a Solana Mobile (Seeker) native fitness commitment-contract dApp. Members stake SKR on themselves to complete a daily bodyweight exercise obligation for the length of a fixed cohort — 7, 21 or 28 days depending on the league they have unlocked. Everyone in a cohort starts on the same Monday and finishes together; anyone who misses a day forfeits their stake into that cohort's prize pool, and the finishers split it in proportion to what they staked. The first league can be cleared for free, and clearing it for free still unlocks the ladder.

**Core differentiation versus the incumbent (Squadletics on Worldcoin):**

| Lever | Squadletics | Holdout |
|---|---|---|
| Fee structure | 0% entry, 35% slash | **One fee: 20% of forfeitures.** Finishers pay nothing — no entry fee, no withdrawal fee |
| Anti-cheat | Pose detection only | Pose + audio + palm liveness + pre-flight environment check |
| Stake currency | WLD or USDC | **SKR-in, USDC-pool, USDC-out** — the 20% fee is swapped to SKR and held permanently, a standing volume-proportional bid (§6.2 states the net position honestly) |
| Structure | 21-day, single tier, open to all | **Gated league ladder** — 7/21/28-day leagues, each unlocked by clearing the one below, each also running a free cohort |
| Pool design | One shared pool | **One pool per cohort** — no cross-league transfer, no weights, no multipliers |
| Cohort timing | Rolling | **Fixed weekly starts** — everyone begins and finishes together, enabling a live survivor counter |
| Settlement | Auto-paid | **Manual claim with auto-share-card** (wow moment + viral loop) |
| Day-end window | UTC | **User-local midnight** (geo-IP locked at cohort start) |
| Free-tier access | None | **A free cohort in every league, every Monday** — same contest, same survivor counter, same leaderboard, no money and no progression. Plus solo sessions for personal use |
| In-app SKR purchase | Bounce to external DEX | **Native Jupiter swap** with Holdout referral fee |
| Badge progression | None | **One evolving cNFT per league**, advancing bronze → silver → gold → platinum on repeat completions (1/3/6/12) |
| Streak counter | None | **Duolingo-style** big home-screen counter w/ milestone forced share |
| Hardware integration | None | **Seeker Genesis Token as humanity proof + access gate** |

**MVP scope (months 1 to 4.5):** **one exercise** — push-ups, 25 reps/day, rising to 30 in the top league — across the three-league ladder (7-day qualifier → 21-day Front Lever → 28-day Human Flag), with the SKR stake loop, the live survivor counter and prize display, single-event cohort settlement with manual claim, the anti-cheat stack, and per-league badge cNFTs. Seeker-only access at launch.

**The whole mechanic, as a user reads it:**

> Stake money. Do your reps every day.
> Miss a day and you lose your stake to everyone still standing.
> Finish and you get your stake back plus a share of theirs.


---

---

## 4. Target user and access model

### 4.1 Primary persona (MVP)

**"Crypto-native fitness curious"** — Seeker owner, 25-40, holds SKR or other Solana ecosystem tokens, has tried a fitness app before but didn't stick with it, is willing to stake $10-50 to test whether financial commitment changes their behavior. Lives in the Americas or Europe. Tech-comfortable, on-chain-comfortable.

### 4.2 Why Seeker-only at MVP

This was an explicit narrowing decision made on 2026-05-21. Tradeoffs:

| Benefit of Seeker-only | Cost |
|---|---|
| No need for separate humanity-proof integration (Genesis Token = built-in sybil resistance) | Smaller initial TAM (~150K Seeker shipped units estimated) |
| Lower regulatory surface (smaller user base = lower attention) | Cannot accept iPhone or non-Seeker Android users |
| Strong ecosystem narrative ("Solana Mobile-native dApp") | Need to add public access later (v1.x or v2.0) |
| Simpler MVP — no Civic Pass, no geo-IP infra | Marketing reach limited to Solana Mobile channels at first |
| Free distribution via Solana dApp Store | |

The Genesis Token also gives us a free distribution channel: featured placement in the Solana dApp Store is much more attainable as a Seeker-native dApp than as a generic mobile app.

### 4.3 Geographic eligibility

**Launch-eligible:** Americas + Europe (excluding the geo-blocks below).

**US geo-block at MVP (explicit prohibition or hostile case law):** AR, CT, DE, LA, SD, WA, AZ, MT, IA, TN.

**Non-US geo-block at MVP:** Mainland China, Hong Kong, all sanctioned jurisdictions. Germany, Netherlands, France, Australia — pending legal review.

Geo-block is enforced at the wallet level via Seeker's location attestation plus IP check **at the moment a stake is placed**. Only staked cohorts are gated.

**Free cohorts are not gated anywhere** (§5.1). They carry no stake and no prize, so they are legal in every jurisdiction, and a member in a blocked region gets the full contest — the crowd, the survivor counter, the leaderboard — indefinitely. What they cannot reach is the prize money and the ladder. That is a real limit but it is not exclusion from the product, and it needs no special-case handling anywhere in the code.

### 4.4 Account model

One Seeker = one Holdout account. The Seeker Genesis Token serves as the humanity proof and account anchor. No traditional username/password. Display name is user-chosen and editable; pseudonymous on-chain.

---

---

## 5. Core challenge mechanic

### 5.1 The league ladder

One exercise, three leagues. One is a door; the other two are the product.

| League | Duration | Reps/day | Stakes (USD) | Unlocked by |
|---|---|---|---|---|
| **Push-Up** | 7 days | 25 | free **or** $5 / $10 / $25 | open to everyone — **the qualifier** |
| **Front Lever** | 21 days | 25 | $25 / $50 / $75 / $100 | qualifying once |
| **Human Flag** | 28 days | **30** | $50 / $100 / $150 / $200 | clearing Front Lever once |

> **Human Flag is double the chips, one more week, five more reps.**

**Qualification**

- Clearing the 7-day league unlocks Front Lever. **It counts whether the cohort was free or staked.**
- A **free** qualifying clear grants the unlock and **nothing else** — no prize, no badge, no league streak. This is the only exception to "free cohorts grant no progression" anywhere in the product.
- **Qualification is one-time.** Once qualified, the *staked* 7-day cohort leaves the member's options permanently. The **free** 7-day cohort never closes.
- **Only a clear closes it.** A member who forfeits the qualifier may re-enter it, free or staked, as many times as it takes.
- Front Lever and Human Flag **never close**. A member may stay in either indefinitely and is never forced from 21 days to 28.

**Stakes**

- Both paid ladders are linear — $25 steps in Front Lever, $50 in Human Flag — and Human Flag is exactly 2× Front Lever chip for chip.
- The qualifier's top chip ($25) equals Front Lever's floor, so money can be held constant across the only league transition that exists.
- Stakes are **discrete chips, not free entry**: a chip selector rather than a text field, no dust stakes, predictable pools.

**Expected economics** — simulated; regenerate with `pool_mechanics_sim.py`, full write-up in `pool_mechanics_findings.md`.

| League | Cohort clear rate | Prize as % of stake | Median prize |
|---|---|---|---|
| Push-Up (qualifier) | 67.4% | 39.4% | $3.09 |
| **Front Lever** | **38.7%** | **129.5%** | **$53.88** |
| Human Flag | 54.6% | 67.7% | $55.86 |

| Chip | Prize | Payout | | Chip | Prize | Payout |
|---|---|---|---|---|---|---|
| Qualifier $5 | +$1.97 | $6.97 | | Front Lever $25 | +$32.38 | $57.38 |
| Qualifier $10 | +$3.94 | $13.94 | | Front Lever $50 | +$64.75 | $114.75 |
| Qualifier $25 | +$9.85 | $34.85 | | Front Lever $75 | +$97.13 | $172.13 |
| Human Flag $50 | +$33.85 | $83.85 | | Front Lever $100 | +$129.50 | $229.50 |
| Human Flag $100 | +$67.70 | $167.70 | | | | |
| Human Flag $150 | +$101.55 | $251.55 | | | | |
| Human Flag $200 | +$135.40 | $335.40 | | | | |

**Three facts the product must state honestly, in the UI, not only here:**

1. **Front Lever's prize is large because its failure rate is large.** ~61% of a cohort does not finish. Say both halves.
2. **"Clear rate" is two different numbers.** A member's chance of clearing Front Lever on their **first attempt** is 49.8%; the clear rate **inside a cohort** is 38.7% (repeat entrants skew it). Quote the cohort rate about a cohort, the first-attempt rate about a member's own odds. Never mix them.
3. **Human Flag pays a lower percentage than Front Lever** (67.7% vs 129.5%) and roughly the same in dollars. Every surface showing both leagues shows both prize figures; the chip selector states Human Flag's return next to Front Lever's. *Front Lever is where the money is. Human Flag is where the glory is.*

Front Lever's prize is **80%–130% of stake** depending on churn-on-fail, which is unmeasured. Do not publish a point estimate.

### 5.1a Free cohorts

**Every league opens two cohorts every Monday: one staked, one free.** Same league, duration, obligation and start — the only difference is whether there is money in it.

| | Staked | Free |
|---|---|---|
| Stake | $5 – $200 by league | none |
| Survivor counter, crowd, leaderboard | yes | **yes** |
| Prize pool | yes | none |
| Badge | yes | no |
| Unlocks the next league | yes | **only the 7-day qualifier does** |
| Still available after you qualify | no — staked qualifier closes | **yes, forever** |

Free cohorts run **beside** staked ones, never mixed into them, so no staked pool is ever diluted.

**Solo sessions are the third lane** (§12.2): no cohort, just the daily session and the streak.

### 5.1b The comeback path

After **any** forfeit the app offers the **free one-week cohort starting the next Monday** — real crowd, real counter, no stake — and the daily streak (§12.2) survives the forfeit and runs through it.

This is a copy-and-UI requirement, not a new mechanic. It exists because a member who forfeits Front Lever has no short *paid* league to rebuild on: their cheapest paid re-entry is $25 over 21 days, the thing that just beat them. Post-forfeit return rate is the metric that prices it.

### 5.2 Fixed weekly cohorts

**A cohort is a group of people who start on the same Monday and finish on the same day.** No rolling joins. Every league opens a new cohort every Monday — one staked, one free.

| League | Live at once |
|---|---|
| Push-Up (qualifier) | 2 |
| Front Lever | 6 |
| Human Flag | 8 |

Each cohort is identified by its start week — "Human Flag · week of 21 September" — so overlapping cohorts of the same league stay distinguishable in the UI and on share cards.

**Minimum size 8, checked at the start after cancellations** (§7.4). A cohort below 8 does not run: everyone is refunded and auto-enrolled into the next opening. This makes cadence self-tuning at low volume — Human Flag effectively fills every third or fourth week until volume grows — with no configuration switch and nothing to migrate later.

**Launch sequencing is emergent, not scheduled.** On launch Monday the only cohorts in the app are Push-Up staked and Push-Up free. Front Lever cannot open until somebody has qualified; Human Flag not until somebody has cleared Front Lever.

| Week | First possible cohort |
|---|---|
| 1 | Push-Up |
| 2 | Front Lever |
| 5 | Human Flag |
| 8 | first Human Flag clears |

#### The lobby

A cohort is **joinable from the moment the previous one of its league starts.** Someone installing on a Wednesday joins the upcoming Monday cohort that same Wednesday and appears in the roster immediately; only the training starts Monday.

```
      FRONT LEVER · starts in 2 days

                47
          already in

      your stake $25 · held
      [ leave, any time before it starts ]
```

- **Free cancellation until the cohort starts.** Stake is taken at join and held; leaving before the start refunds in full, no fee. After the start, §5.7 applies.
- **Join closes Sunday 00:00 local. The cohort starts Monday 00:00 local.** The cutoff is evaluated in the **joining member's own local time** (§5.3), not UTC. Contract-side: `member_local_now < member_local_sunday_00:00`, from the geo-IP offset resolved during the join transaction.
- Sunday is a full closed day because it is the **closing day of the running cohort** — settlement fires at the Sunday→Monday tick. Sunday is only about finishing; Monday 00:00 is only about starting.
- **There is no late entry.**

**Push notifications are part of the feature, not a follow-up:** on join, on the last join day, on Sunday evening, and on Monday morning.

**The wait is not idle.** From the moment they join, a member can run **solo sessions** (building the daily streak before the cohort begins) and **calibrate** — pre-flight setup, camera placement, form-check walkthrough, baseline test. Day-one failures caused by bad camera setup are a funnel killer and the pre-start days are exactly when to eliminate them.

### 5.3 The daily obligation

One verified session per calendar day of the cohort, before local midnight.

| League | Reps per day |
|---|---|
| Push-Up (qualifier) | 25 |
| Front Lever | 25 |
| **Human Flag** | **30** |

The count is stored on the cohort (`reps_per_day`, §9.2) and **fixed at creation**, so a running cohort's bar can never move under its members. Never read it from a constant.

MVP ships **one exercise only.**

- **Day window:** each member's day runs local midnight to local midnight, detected via geo-IP at cohort start and **locked thereafter** (prevents timezone arbitrage by members who travel mid-cohort).
- **Unlimited retries within the same local day.** Failure triggers only if no qualifying session completes before the local midnight deadline.
- **No grace day, no postponement, no carry-forward.** A missed day is a failed cohort. Soft postponement lets members batch on the final days, which destroys the daily-discipline frame that is the entire product, and end-of-cohort grinding is easier to spoof.

### 5.4 Failure semantics

If a member misses any single day:

- **The entire stake is forfeited** at the next daily tick — no partial credit, no skip token, no insurance.
- **The forfeited USDC moves into that cohort's own prize pool**, minus the platform fee (§6.2).
- **Failure is irrevocable for that cohort.** The member keeps their unlocked league level and may enter the next cohort of the same league when it opens.

### 5.5 Promotion, staying, and demotion

- **Clearing a league unlocks the next one permanently.** One clear is enough; the unlock never expires.
- **A member may stay in Front Lever or Human Flag indefinitely.** Nobody is forced up a rung.
- **Qualification is one-time and route-agnostic** (§5.1) — free or staked both count; once qualified, the staked qualifier closes and the free one does not.
- **A forfeit closes nothing.**
- **There is no demotion.** Failing a cohort costs the stake, not the league level.

### 5.6 Active stake limit

**One *running* cohort at a time, plus one lobby entry for the next.** A member performs one daily obligation, never two — but a lobby entry is not a running cohort, so the next one can be lined up while the current finishes (§5.6a). 1 Seeker = 1 obligation.

### 5.6a Pre-joining the next league

A member who has not missed a day may join the lobby of the next cohort **while their current one is still running**, including a league they have not unlocked yet but would unlock by clearing the one they are in.

- The stake is taken and held segregated exactly as any lobby entry (§5.2).
- At the member's settlement tick the entry **resolves**: cleared → the lobby membership activates and they start Monday; forfeited → **refunded in full, automatically**, no action needed.
- Manual cancellation before the cutoff works as normal.

**This is load-bearing, not a convenience.** A member clears a league at the Sunday→Monday settlement tick — the same instant the next cohort starts. Without pre-joining, the unlock would arrive a day after the join window closed and a member who had just won would sit out a full week at the moment they are most motivated.

### 5.7 No refund policy

**No refunds** for missed days, technical issues during a session, or environmental problems. Two design choices protect this:

1. **Pre-flight environment check** (§8.2) — refuses to start a session if lighting, camera angle or framing are inadequate, and says exactly what to fix.
2. **Off-chain session checkpointing** (§8.6) — in-progress rep count is persisted continuously, so a crash, disconnect or app close does not lose verified progress.

**Sole exception:** a verifiable platform-side outage (server down, contract bug, chain congestion preventing settlement). Case by case by ops, funded from the Treasury/Insurance allocation, documented in the TOS.

### 5.8 Settlement and the claim moment

Every member of a cohort settles at the same moment: the cohort's final local midnight.

1. The contract computes each finisher's payout — **stake back plus their share of the prize pool** (§7) — and marks it claimable.
2. Push notification: *"You outlasted the crowd. $XX.XX is yours — tap to claim."*
3. The member opens the app, taps **Claim $XX.XX**, signs in their Seeker wallet, receives USDC.
4. The app **auto-generates a share card** the moment funds arrive (§12), one-tap share.
5. Anyone already forfeited needs to do nothing; their stake left the account when they missed a day.

**Claim is manual by design** — signing the transaction *is* the wow moment, and an automatic deposit would waste it. Payout is denominated in USDC; a "convert winnings to SKR on claim" toggle is deferred.

---

## 6. Tokenomics and fee flows

### 6.1 Stake denomination

**Holdout accepts SKR only as the input currency.** Users pay SKR; the contract handles routing. Stake values are denominated in USD (§5.1) and priced to SKR at the moment of staking.

### 6.2 The fee — one number, 20%

**Holdout charges exactly one fee: 20% of every forfeited stake.** There is no entry fee, no subscription, no withdrawal fee.

```
User pays X SKR (= $Y at the current SKR/USDC oracle price)
    |
    +--> 100% swapped via Jupiter to ~$Y USDC → the cohort's pool as the user's own stake
         (segregated; returned in full if they finish)

On a forfeit:
    forfeited stake  --> 20% --> Fee Treasury (swapped USDC → SKR, held)
                     --> 80% --> the cohort's prize pool, for the finishers
```

**If you finish, Holdout takes nothing from you.** That is the entire fee policy, and it fits on one line of a landing page.

**Why 20% (calculation).** Two quantities govern the choice, where `s` is the fee on forfeitures and `f` is the share of a cohort that fails:

```
platform revenue per $1 staked = s × f
prize per $1 staked            = (1 − s) × f / (1 − f)
```

The previous model (2% entry + 15% slash) is matched by a fee-only rate of `s = 0.02/f + 0.147`, which lands at **18.4%–19.7%** across the paid leagues. **20% is therefore revenue-neutral-to-slightly-positive versus the old hybrid**, while removing a fee entirely from the pitch.

Higher rates were considered and rejected for launch:

| Fee | Qualifier prize | Front Lever prize | Human Flag prize | Effective take on volume |
|---|---|---|---|---|
| **20%** | 39% of stake | **130%** | 68% | 9.6% |
| 25% | 37% | 121% | 63% | 12.0% |
| 30% | 34% | 113% | 59% | 14.4% |
| *Squadletics (35% slash)* | — | — | — | *16.8%* |

The deciding factor is **"Front Lever roughly doubles your stake"** — comfortably true at 20% (95%), already a stretch at 25% (89%), and gone at 30% (83%). Front Lever is expected to be the most popular league (§5.1), so its headline is the one that matters most. That sentence is worth more than the ~$2.6K/month of extra revenue that 30% would produce at $50K monthly volume. A fee is a contract parameter: raising it later, once people love the product, is far easier than cutting prizes at launch, which reads as a broken promise. At launch the goal is large visible prizes for social proof — taking 30% would be fighting our own cold-start budget (§6.4).

**Honest accounting of the "SKR buy pressure" claim.** The pool is USDC-denominated, so every stake sells SKR and only the fee buys it back. Per $1 staked: $1.00 of SKR sold on entry, about $0.096 bought back by fees — **net SKR sell pressure of roughly $0.90 per dollar staked**. The old 2%-entry model was marginally worse (net $0.98). The accurate claim is that *the fee is a permanent, volume-proportional SKR bid that is never sold*, not that the app is net-positive for SKR. Marketing and grant materials must not overstate this.

### 6.3 The Fee Treasury

The 20% fee arrives as USDC and is immediately swapped to SKR, which is held permanently and staked via the Solana Mobile Guardian program (~18% APY).

Allocation policy (post-MVP — at MVP all fees accumulate in a single multisig account without subdivision):

| Allocation | Share | Purpose |
|---|---|---|
| Marketing | 25% | Paid acquisition, content sponsorship, ecosystem partnerships |
| Founder | 25% | Team compensation |
| Operations | 25% | Hosting, audits, legal, tooling, infrastructure |
| Treasury / Insurance | 15% | Emergency reserve; platform-side refund fund (§5.7 exception) |
| League Reward Fund | 10% | Free-tier and Push-Up league prizes, post-MVP |

**No token burn:** burns help holders, not the product. The fee already creates a standing SKR bid; Operations funding does more for users than a symbolic burn.

**Revenue is now failure-correlated, and that is a real business risk.** With no entry fee, a cohort in which everyone finishes generates zero revenue. Month-to-month income will be lumpier than under the hybrid model, and there is an optics attack — *"the platform profits when you fail"* — that is technically true and applies equally to Squadletics. The counter is to state the policy proudly and constantly: **"Finish and we take nothing."** No subscription product can say that.

### 6.4 Pool denomination — USDC

Prize pools are denominated in **USDC** for the whole cohort:

- Stable through a 2–4 week cohort; an SKR-denominated pool could halve or double in dollar terms mid-run
- Predictable expectations — "if I win, I get USDC"
- The prize display stays volatility-free

The SKR→USDC swap happens at stake time via Jupiter, with 0.5% slippage tolerance and a contract-level revert if exceeded. The estimated USDC amount is shown before the user confirms.

Pool USDC sits idle for the duration of the cohort. Deploying it to a money market (previously specified as "Sweat Yield") is **removed from scope** — see §6.6.

### 6.5 Cold-start bonus matching (6-month structured)

The Fee Treasury matches a percentage of every cohort's prize pool from the marketing budget, for a **6-month structured window** post-launch:

| Window | Match rate | Purpose |
|---|---|---|
| Months 1–3 (full match) | 50% of the pool | Maximum boost during the first-impression period |
| Months 4–6 (half match) | 25% of the pool | Soft cliff — signals finite runway without abruptly killing incentives |
| Month 7+ | 0% | Sunset; the organic flywheel should carry it |

**Example:** a Front Lever cohort that accumulates $2,000 of forfeitures in Month 2 pays out $2,000 + $1,000 of bonus. The same cohort in Month 5 gets $500 of bonus.

The match matters most in the qualifier and in thin early Front Lever cohorts, where absolute prizes are smallest — it is what makes a first share card worth posting during the cold start.

**Sunset enforcement:** match rates are hardcoded to deployment-time block heights (full→half, half→zero) and cannot be extended without an upgrade transaction. Intentional friction.

**Budget shape (illustrative):** matching up to $5K/week gives ~$20K/mo × 3 + ~$10K/mo × 3 ≈ **$90K total**, allocated from Ops Treasury, ideally funded by the Solana Mobile Builder Grant (`holdout_decisions.md` §5.6).

---

## 7. Settlement and pool design

### 7.1 One pool per cohort

**Each cohort has its own prize pool, funded only by the forfeitures of its own members.** Money never moves between leagues, and never between cohorts.

```
prize_pool  =  sum of forfeited stakes in this cohort  ×  0.80
               (+ cold-start bonus match, §6.5, while active)

your payout =  your stake  +  prize_pool × (your stake / total stake of all finishers)
```

There are **no tier multipliers, no pool weights, and no cross-league accounting.** Your share is simply your stake divided by the finishers' stakes. The entire mechanic explains in three lines:

> Stake money. Do your reps every day.
> Miss a day and you lose your stake to everyone still standing.
> Finish and you get your stake back plus a share of theirs.

**Why separate pools (decision 2026-09-10, supersedes the shared-pool design).** The previous design pooled every tier of an exercise together and used commitment-length multipliers (1.0× / 2.5× / 4.7× / 9.0×) to equalise risk across tiers. Under a **gated** ladder that construction fails badly, because gating changes who is in each league:

- A gated top league contains only members who already cleared everything below — the disciplined tail. Simulated, the longest league's clear rate rises from **47% (open access) to 69% (gated)**. It stops being the riskiest league and becomes the safest.
- Multipliers exist to compensate for risk. With the risk gone but the multiplier left in place, a shared pool transfers wealth from newcomers to veterans: **−27% average return for the bottom league versus +15% for the top**, a 44.6 pp spread. That is a pyramid, and it is what gets a product called a scam.
- Recalibrating the multipliers does not fix it. The *fair* multiplier for a gated top league is the **lowest** of the four, because it is the safest — which is the exact opposite of what a game ladder wants to signal. The pool wants to pay for risk; the ladder wants to pay for rank. One pot cannot do both.

Measured spread between the best-off and worst-off league:

| Design | Spread |
|---|---|
| Shared pool, old multipliers (1.0/1.7/2.5/3.4) | 41 pp |
| Shared pool, corrected multipliers (1.0/2.5/4.7/9.0) | 4.4 pp |
| Gated ladder + shared pool, best possible multipliers | 14 pp |
| **Gated ladder + one pool per cohort** | **2.4 pp** |

Separate pools also **delete the entire multiplier apparatus**: no weights, no basis-point parameters, no dependence on an assumed daily failure rate. The single largest modelling risk in the previous design — a mis-estimated failure probability blowing cross-tier fairness out to a 53 pp spread — has nowhere left to occur.

**And the old objection to separate pools no longer applies.** Fragmentation was the reason for pooling in the first place. But gating spreads participants far more evenly across leagues than open access does (the top league draws several times more volume once there is a road to it; the exact split must be re-simulated for the three-league ladder — `pool_mechanics_findings.md`), and a pool's prize distribution converges at roughly 20 participants (§5.2).

**The ladder's sense of progression comes from stake floors, the rep escalation and status — not from the pool math.** A Human Flag member stakes $50–200 at 30 reps a day against a Front Lever member's $25–100 at 25 — double the chip at every rung (§5.1) — so both the obligation and the absolute prize scale with rank even though the percentage does not. Rank itself pays in badges (§9.1) and league standing — never in other members' money.

### 7.2 Settlement — one event per cohort

1. **During the cohort:** each daily tick moves any missed-day member's stake out of their account. The contract takes its 20% fee immediately (§6.2) and adds the remaining 80% to the pool. Doing the fee cut at forfeit time rather than settlement keeps pool accounting to a single running number.
2. **The pool is displayed live.** Every member sees the pool, the number of survivors, and their own projected prize — recomputed at every tick (§10).
3. **At the cohort's final tick:** every finisher's payout is computed at once — stake plus pro-rata share — and marked claimable. **No automatic transfer** (§5.8).
4. **Unclaimed funds** remain claimable indefinitely; there is no expiry.

Because everyone settles simultaneously, there is no ordering effect: two members with identical stakes in the same cohort always receive identical payouts.

### 7.3 The math, in full

For a cohort with members `i`:

```
pool        = 0.80 × Σ(stake_i for forfeited i)  +  bonus_match
your_share  = pool × stake_you / Σ(stake_j for finishers j)
your_payout = stake_you + your_share
```

Simulated prize distribution by cohort size (heterogeneous population; run on the old 14-day league, but the convergence point is a property of cohort size rather than duration):

| Cohort size | Median prize | p10 | p90 |
|---|---|---|---|
| 20 | 57% of stake | 36% | 104% |
| 50 | 62% | 44% | 92% |
| 100 | 62% | 48% | 82% |
| 300 | 62% | 54% | 72% |
| 1000 | 63% | 58% | 68% |

Smaller cohorts are more volatile in the members' favour as often as against — a thin cohort with heavy attrition produces an outsized payday, which is good content.

### 7.4 Edge cases

- **Nobody forfeits.** Finishers get their stake back and nothing more. There is no prize because nobody funded one. The UI must state this possibility up front so it never reads as a bug.
- **A very small cohort.** At the 8-member floor a single forfeit is visible and the prize is lumpy. This is expected at launch and is why stakes are set high (§5.1) — twelve members at $50 still pay $40 each.
- **A free cohort** (§5.1) has no pool by construction, so settlement returns nothing and claims nothing. It still runs the full contest: survivor counter, leaderboard, completion record. The prize panel renders an honest "no money in this one" state rather than a zero. Its minimum size can be lower than the staked floor — a free cohort only needs enough members for the counter to be interesting, not enough to fund a prize.
- **Nobody finishes.** The pool has no claimants. It rolls forward into the next cohort of that same league as seed. Simulated frequency at cohort sizes ≥10: effectively zero, but the contract must handle it.
- **One finisher.** They receive their stake plus 100% of the pool.
- **A cohort fails to reach minimum size.** Measured at the join cutoff — Sunday 00:00 local — **after** all cancellations (§5.2). If fewer than **8** members remain, the cohort does not start: stakes are returned in full and members are auto-enrolled in the next week's cohort of the same league. This protects against a two-person cohort behaving like a coin flip, and it is also the mechanism that makes weekly starts safe at low volume (§5.2).
- **Pool can never exceed forfeitures.** Finishers' principal is segregated from the prize pool at all times; only forfeited stakes and the bonus match ever enter it. A shortfall is impossible by construction — and with Sweat Yield removed (§6.6) there is no longer any external path to one either.
- **Contract pause / emergency.** If the contract is paused mid-cohort for a critical bug, all active stakes are returnable case by case from the Treasury/Insurance allocation. Pause authority lives in a multisig.

---

---

## 8. Exercise tracking and anti-cheat

### 8.1 General principles

- **Live, on-device only.** Video frames are processed in real time and discarded; no video is persisted anywhere (not local, not cloud, not on-chain).
- **No human review tier** at MVP. The pose detection algorithm's verdict is final.
- **No flagging or bounty mechanic.** Users cannot review or report other users' sessions.
- **Multi-modal verification:** pose detection + audio + start-of-session palm liveness check + pre-flight environment check.
- **All checks must pass for a rep to count.**

### 8.2 Pre-flight environment check

Before any session can begin, the app runs a pre-flight checklist using the live camera feed. Each item shows a status pill (red/yellow/green) updated in real time:

1. **Lighting adequate** — average frame brightness within target range
2. **Person fully in frame** — pose-detection skeleton has all 17 keypoints visible
3. **Camera angle correct** — torso orientation within expected range for the exercise
4. **Background motion absent** — no major motion in the non-person regions of the frame
5. **Audio input live** — microphone detects ambient noise floor (not muted, not blocked)

**The user cannot start the session until all five are green.** If any check is red, the UI explains exactly what to fix ("Increase room lighting" / "Step back so your full body is visible" / "Lower your phone to chest height").

This is the linchpin of the no-refund policy. By the time a session starts, conditions are verified OK, so the user cannot claim "the camera didn't work" later.

### 8.3 Palm-liveness check

Once all five pre-flight pills are green, the app prompts:

> "Show your right palm to the camera for 3 seconds."

The pose detection model verifies that an open palm is in frame for 3 continuous seconds. This single liveness moment confirms a real human is present (not a propped-up video). After palm verification, a 3-second countdown starts and the session begins.

**Why only at start, not mid-session:** mid-session prompts were considered and rejected on 2026-05-21 as too annoying — they break user flow and create UX rage. Continuous pose + audio detection during the session provides ongoing tamper resistance.

### 8.4 Push-ups detection rules (MVP)

**Reps required: 25, or 30 in Human Flag.** The count comes from the cohort's `reps_per_day` field (§5.3, §9.2), never from a constant — the detection rules below are identical either way.

A rep is counted when **all** of the following are detected within a single rep cycle:

1. **Top position:** elbow angle > 160° (arms extended), torso linearity (shoulder-hip-ankle within ±10° of a straight line)
2. **Bottom position:** elbow angle < 90° (arms bent past parallel), torso linearity maintained
3. **Return to top:** elbow angle > 160° again, torso linearity maintained
4. **Audio confirmation:** detectable exertion breath or movement sound within the rep window (filters out static-frame spoofing)
5. **Rep cycle duration:** between 0.8 seconds and 6 seconds (filters out impossibly fast or pause-and-resume cheating)

Reps detected outside these parameters are discarded silently with a UI hint ("Slow down — full range of motion") to avoid frustrating users with rejected reps.

Model used: MediaPipe Pose v0.10+ for on-device inference. Custom rep-counting state machine layered on top.

### 8.5 Plank detection — deferred

Plank is **not in MVP scope** (§5.3). A second exercise doubles the number of concurrent pools at exactly the point where pool concentration matters most, and it doubles the anti-cheat surface to be tuned and audited. The detection design (MediaPipe hip–shoulder–ankle linearity, continuous with a 2-second warning and 5-second fail threshold) is retained in the v1.1 backlog.

### 8.6 Off-chain session state via Firebase + on-chain success/fail only

**Architecture (locked 2026-05-22 — replaces the original 10-rep on-chain checkpoint design):**

| Layer | Stores | Why |
|---|---|---|
| **Firebase Realtime DB (off-chain)** | In-progress session state: rep count, last keypoint frame, pause timestamp, audio confidence rolling-avg | <100ms writes, no SOL spend, mid-session state has no consensus value, recoverable from any device |
| **Solana (on-chain)** | Exactly two events per user per day: `submit_session_success(cohort_id, day_index, proof_hash)` OR `submit_session_fail(cohort_id, day_index, reason)` | Authoritative settlement record. ~95% fewer writes vs original 10-rep design. |

**Mid-session pause handling:**
- User can pause for up to 15 minutes (water break, phone call, urgent interruption). State persists in Firebase only — no chain footprint.
- After 15 minutes idle: app auto-fails the session and the user must restart from zero within their local day window. (Still unlimited restarts within the day.)
- This 15-min cap prevents users from leaving a session "open" all day and resuming hours later with a different person performing.

**Phone crash mid-session:**
- App reads Firebase on next open, sees an unfinished session for today's day_index, prompts "Resume your push-up session? You were at 12/25 reps." (or 12/30 in Human Flag)
- User taps resume, MediaPipe re-initializes, continues from 12. No chain write happens until the session either completes or auto-fails.

**Why we do NOT allow long postponement (carry-forward across days):**
- See §5.3 — habit science, anti-cheat integrity, and product-positioning all converge on strict daily windows
- Firebase pause is bounded by 15 minutes precisely to prevent it from becoming a backdoor for cross-day postponement

**Cost model (revised):**
- Firebase: ~$5/month at 1K active users (well within free tier for v1)
- Solana writes per user per day: 1 (success or fail). At 1K active users × 21 days × $0.0005/tx = **~$10/cohort cycle**
- vs original 10-rep model: ~25 checkpoints/session × 21 days × 1K users = ~$260/cohort cycle. **26× cost reduction.**

**Trust model:** Firebase rows are written by the mobile client, but the on-chain `submit_session_success` instruction takes a proof_hash that includes a Merkle-rooted summary of the Firebase trace. If a user wants to dispute a fail later, the server-side trace is the source of truth. Server compromise risk exists but is bounded by: (a) Firebase access control via Firebase Auth + Solana wallet signature, (b) the proof_hash on-chain prevents post-hoc tampering. Acceptable risk at MVP; v2 considers IPFS-pinned session traces for full decentralization.

### 8.7 Session completion attestation

When the user completes the required reps/time:

1. Final session summary committed on-chain (count, duration, exercise, timestamp, user signature)
2. UI shows "Workout complete!" celebration animation
3. Workout share card generated (see §12); celebration screen also offers **"Take your Swelfie"** (live-only verified workout selfie — see §12.5)
4. Daily obligation marked complete in user's cohort record

The on-chain completion attestation is the authoritative record consulted at each member's local midnight tick (§9.3, Operation 3).

### 8.8 Known attack vectors and mitigations

| Attack | Mitigation |
|---|---|
| Play pre-recorded video to front camera | Pre-flight environment check verifies live ambient audio; palm liveness verifies real human at session start; intra-session audio breath detection |
| Have a friend do the exercise | Single Seeker per account (humanity proof) — economically irrational unless friend is also their Seeker holder; tracked via wallet behavior |
| Pose-skeleton manipulation (intercept client model output) | Audio + timing-cycle checks add multi-modal verification; pose skeleton alone is insufficient |
| Impossibly fast reps | Rep cycle duration bounds (0.8s minimum) |
| Static-frame freeze cheat | Audio breath detection during rep cycle |
| Camera covered or off | Pre-flight check requires live frame data |

**What we do NOT defend against** (acceptable risk at MVP):
- Determined ML-savvy adversary intercepting and forging the on-device model outputs (this is theoretically possible; addressing it would require attested-execution hardware which Solana Mobile may eventually provide)
- Two people sharing the same Seeker device (one stakes, the other performs)
- High-motion environments that interfere with pose detection (we just refuse to start the session via pre-flight)

---

---

## 9. Smart contract architecture

### 9.1 Contract inventory (Rust / Anchor framework)

| Contract | Responsibility |
|---|---|
| **HoldoutCore** | League registry, cohort lifecycle (open → run → settle), stake accounting, per-user league unlocks |
| **CohortPool** | One PDA per cohort. Holds segregated member stakes and the cohort's prize pool (USDC). Created when a cohort opens, closed when the last payout is claimed |
| **FeeTreasury** | Receives the 20% forfeiture fee in USDC, swaps to SKR via JupiterSwapRouter, holds permanently and routes to the Solana Mobile Guardian staking program |
| **AttestationRegistry** | Per-day session success/fail records (one row per member per day) |
| **LeagueBadgeMinter** | Mints and evolves one cNFT per league per user, by completion count |
| **JupiterSwapRouter** (CPI helper) | Wraps Jupiter for SKR→USDC on stake, USDC→SKR for the fee, and in-app SKR purchase (with Holdout referral fee) |

Removed versus the previous revision: **PushupPool / PlankPool** (replaced by per-cohort `CohortPool`) and **YieldVaultAdapter** (Sweat Yield is out of scope, §6.6). Both removals shrink the first audit materially.

**League badges.** The four calisthenics names are **league names**, so they cannot simultaneously be badge tiers as an earlier revision specified. Each league instead has its own evolving cNFT, and repeat completions of that league advance its material:

| Completions of a league | Material | Displayed as |
|---|---|---|
| 1 | Bronze, static | `Front Lever` |
| 3 | Silver, subtle shimmer | `Front Lever ×3` |
| 6 | Gold, particle effect | `Front Lever ×6` |
| 12 | Platinum, fully animated | `Front Lever ×12` |

**The qualifier is the exception: a single, non-evolving badge.** Qualification happens once (§5.1), so an account can only ever hold one counting Push-Up completion — a progression ladder there is structurally impossible, not merely undesirable. The badge is therefore a one-off certificate, and that is the right shape for what it certifies: you got through the door. **It is minted only on a *staked* qualifying clear** — a free clear unlocks Front Lever but mints nothing, which is the whole of what the free carve-out grants. **Material tiers exist only in Front Lever and Human Flag, where the commitment is both real and repeatable.**

**Why per-league rather than one global ladder:** a member who settles happily in Front Lever and never wants a four-week commitment at 30 reps still has somewhere to grow. Progression is available in both homes, not only at the top.

**One evolving cNFT per league, not one per completion:** cleaner wallet inventory, a stronger "I levelled up" share moment that fires on material change rather than on every clear, and cheap metadata-pointer updates via Bubblegum compression. A Push-Up clear mints its badge on the first completion — the proof of qualification and the first thing a new member owns.

### 9.2 Key data structures

```
League {
    id: Enum { PushUp, MuscleUp, FrontLever, HumanFlag },
    duration_days: u8,          // 7 / 21 / 28
    allowed_stakes_usd: [u32],  // free for PushUp
    cadence_days: u16,          // how often a new cohort opens
    min_cohort_size: u8,        // 8 — below this the cohort does not start (§7.4)
}

Cohort {
    league: Enum,
    index: u32,                 // monotonic per league
    start_unix: i64,            // always a Monday 00:00 UTC reference
    join_closes_unix: i64,      // latest possible cutoff instant (Sunday 00:00 at UTC-12).
                                // The binding check is per-member and local: a joiner is
                                // refused once THEIR local Sunday has begun (§5.2)
    end_unix: i64,
    reps_per_day: u8,           // 25, or 30 for Human Flag (§5.3). Fixed at creation so a
                                //  running cohort's bar can never move under its members.
    staked: bool,               // false = free cohort: no pool, no badge. Unlocks nothing
                                //  EXCEPT the 7-day qualifier, whose free clear sets
                                //  UserProgress.qualified (§5.1)
    status: Enum { Open, Running, Settled, Cancelled },
    member_count: u32,
    finisher_count: u32,        // written once, at settlement
    total_finisher_stake: u64,  // written once, at settlement — the payout denominator
    prize_pool_usdc: u64,       // 80% of forfeitures + bonus match
}

Membership {
    user: Pubkey,
    cohort: Pubkey,
    stake_usdc: u64,            // post-swap; 0 in a free cohort, otherwise one of the league's chips
    tz_offset_minutes: i16,     // locked at join from geo-IP
    days_completed: u8,
    status: Enum { Pending, Active, Forfeited, ReadyToClaim, Claimed },
                                // Pending = in the lobby, cohort has not started.
                                // Becomes Active at the Monday 00:00 local tick.
    unlocks_from: Option<Pubkey>,
                                // Set when this is a conditional pre-join (§5.6a): the
                                // running cohort whose clearing this entry depends on.
                                // Resolved at that cohort's settlement — activate or refund.
}

UserProgress {
    user: Pubkey,
    highest_unlocked: Enum,     // permanent; never decremented (§5.5)
    completions: [u16; 3],      // per league; drives badge material for the two paid leagues
                                // (the Push-Up badge does not evolve; its count is display-only)
    qualified: bool,            // set by ANY qualifying clear, staked or free (§5.1). This — not
                                //  completions[PushUp] — is what gates a Front Lever join.
    league_streak: u16,         // consecutive cohorts cleared without a forfeit; resets on forfeit
    longest_league_streak: u16,
}
// NOTE: the DAILY streak is deliberately NOT on-chain. It counts solo sessions too (§12.2),
// nothing is ever paid out against it, and a daily wallet signature for a cosmetic counter
// would be absurd friction. It lives in Firebase alongside session state (§8.6).
```

Note what is **absent**: no `tier_multiplier_bps`, no `total_active_pool_weight`, no yield accounting. The payout denominator is a single number written once at settlement.

### 9.3 Critical operations

**Architectural principle:** state advances are **user-driven wherever possible, keeper-driven only for no-shows and cohort lifecycle.** This minimises keeper centralisation risk, operational cost, and transaction count.

#### Operation 1: `join_cohort(league, stake_amount)` — user-initiated

1. Validate Seeker Genesis Token ownership (cross-program check)
2. For a **staked** cohort: validate `league <= UserProgress.highest_unlocked`, **or** that clearing the caller's running staked cohort would unlock it — in which case the entry is recorded as conditional, with `unlocks_from` set to that cohort (§5.6a). **The qualifier is one-time:** a staked join to Push-Up is permitted only while `UserProgress.qualified == false` — qualifying retires the staked route permanently (§5.1) and any later staked Push-Up join must revert. Front Lever and Human Flag have no such cap. **Front Lever's gate is `qualified`, not `completions[PushUp]`**, because a free qualifying clear counts (§5.1). **Free cohorts have no unlock gate at all** — any league's free cohort is open to anyone
3. Validate the user has no other **running** Membership (`Active`). A `Pending` lobby entry is permitted alongside a running cohort — that is the pre-join path (§5.6a), and at most one Pending entry may exist
4. Validate the target cohort's status is `Open` and that the joiner's **local** Sunday has not yet begun (§5.2). `join_closes_unix` is the outer bound; the binding test uses the geo-IP offset resolved in step 8
5. Validate the stake against the cohort: in a staked cohort, `stake_amount` must be one of the league's three chips; in a free cohort it must be 0 (§5.1)
6. Transfer SKR from the user's wallet; CPI to JupiterSwapRouter, swap 100% to USDC with a 0.5% slippage limit and revert
7. Record the USDC as segregated principal in the `Membership` account — **not** in `prize_pool_usdc`
8. Detect geo-IP → lock `tz_offset_minutes`
9. Increment `Cohort.member_count`; emit `MemberJoined`

#### Operation 2: `submit_session(cohort, day_index, proof_hash)` — user-initiated, at session end

The high-volume operation — once per member per day on success days.

1. Validate signature and membership
2. Validate `proof_hash` format (Merkle root of the off-chain session trace, §8.6)
3. Validate `day_index` matches today given the locked timezone
4. Validate not already submitted for this `day_index` (idempotent)
5. Write the `DayCompletion` record; increment `days_completed`
6. Emit `DaySubmitted`

Settlement is **not** triggered here — it is a cohort-level event (Operation 4).

#### Operation 3: `tick_cohort(cohort, start_index, count)` — keeper-initiated, once per cohort per midnight window

The keeper's only job is to **mark no-shows as forfeited**. It does not iterate finishers (they self-submit) and it does not settle.

```
for each Membership in cohort where status == Active:
    if user_local_midnight_just_passed AND days_completed < expected_day_index:
        status = Forfeited
        UserProgress.league_streak = 0        // daily streak is untouched — it is off-chain (§12.2)
        if stake_usdc > 0:
            fee = stake_usdc * 20 / 100          → FeeTreasury (USDC, later swapped to SKR)
            cohort.prize_pool_usdc += stake_usdc - fee
            cohort.prize_pool_usdc += bonus_match(now)   // §6.5, 50% / 25% / 0%
        emit StakeForfeited
```

**Chunking:** cohorts larger than 100 active no-shows are processed across multiple paginated calls within the same minute window. **Idempotent** — repeat calls skip already-forfeited memberships.

#### Operation 4: `settle_cohort(cohort)` — keeper-initiated, once, at the cohort's final midnight window

1. Validate `end_unix` has passed for every member's locked timezone
2. Count finishers; sum their stakes into `total_finisher_stake`
3. Set `Cohort.status = Settled`
4. Mark every finishing `Membership` as `ReadyToClaim`
5. **Free cohort:** mark finishers complete for the record and the leaderboard, then stop — no badge, no unlock, no `completions` increment, no `league_streak` change, nothing to claim. Steps 6–7 are skipped entirely.
5a. **Staked cohort**, for each finisher: increment `UserProgress.completions[league]` and `league_streak` (updating `longest_league_streak`); set `qualified = true` if this was the Push-Up league; CPI to LeagueBadgeMinter if a material threshold was crossed (Push-Up mints once, non-evolving)

5b. **Free cohort, Push-Up only**, for each finisher: set `UserProgress.qualified = true`. Nothing else — no `completions` increment, no badge, no `league_streak` touch, no transfer. This one line is the entire free carve-out (§5.1)
6. Unlock step: if `league < HumanFlag`, set `highest_unlocked = max(highest_unlocked, league + 1)`. No eligibility branch — the unlock is granted unconditionally and is simply inert for an account that cannot legally stake, since geo-blocking is enforced at join time (§4.3). Nothing is ever locked back down
7. **Resolve pre-joins (§5.6a):** for every `Pending` Membership whose `unlocks_from` is this cohort — if its owner is a finisher, clear `unlocks_from` and leave it Pending until its own cohort starts; if its owner forfeited, refund the held stake in full and close the entry. Members who forfeited mid-cohort are resolved the same way at their `tick_cohort` forfeiture, so no stake is ever held past the moment it is known to be moot
8. Emit `CohortSettled { finisher_count, prize_pool_usdc }`

This is **one transaction per cohort**, not per user — the payout denominator is computed once and every claim reads it.

#### Operation 5: `claim_payout(cohort)` — user-initiated, post-settlement

1. Validate `Membership.status == ReadyToClaim`
2. `share = prize_pool_usdc * stake_usdc / total_finisher_stake`
3. Transfer `stake_usdc + share` USDC to the user's wallet
4. Mark `Claimed`; emit `PayoutClaimed`

No expiry — an unclaimed payout stays claimable indefinitely.

#### Operation 6: `leave_cohort(cohort)` — user-initiated, only while `Open`

1. Validate `Cohort.status == Open` and that the member's local Sunday has not yet begun
2. Validate the caller's `Membership` exists and is `Active`
3. Refund `stake_usdc` in full — no fee, no deduction. (The stake was never in the prize pool; it has sat segregated since join, so this is a straight transfer back)
4. Close the `Membership`; decrement `Cohort.member_count`
5. Emit `MemberLeft`

Deliberately unconditional and free. The whole purpose of the lobby is to let people commit early, and nobody commits early to something they cannot back out of.

#### Operation 7: `open_cohort(league)` / `cancel_cohort(cohort)` — keeper-initiated

Opens the next cohort for every league, **every Monday** (§5.2). A league's next cohort opens for joining the moment its current one starts, so there is always exactly one lobby per league accepting members — and for the longer leagues, several cohorts running in parallel at different days of progress.

Once the join window has closed for every timezone, if fewer than `min_cohort_size` members remain after cancellations, the cohort is `Cancelled`: every stake is refunded in full and members are auto-enrolled in the next opening (§7.4).

#### Cost model

- **Per-join / per-submit_session / per-claim (user-paid):** ~$0.0005 each
- **Per-tick_cohort (Holdout-paid):** ~$0.001, roughly 2 calls per day per live cohort. With one exercise and one live cohort per league, that is ~8 calls/day ≈ **$0.24/day**
- **Per-settle_cohort (Holdout-paid):** ~$0.001, a handful per month
- **Annual keeper cost at 1K active users:** **under $100/year**

Fewer live cohorts than the previous rolling design means fewer keeper calls, not more.

#### Decentralization roadmap

- **v1:** Holdout ops runs the keeper (Node.js on Railway or Fly.io, single replica with monitoring and alerting)
- **v1.5:** a redundant community-run keeper — idempotency makes a double call a no-op
- **v2:** migrate to Switchboard or Clockwork decentralized cron, removing Holdout as a single point of failure

### 9.4 Security and audit considerations

- **Audit requirement:** full third-party audit before mainnet. Budget **$25–45K** — lower than the previous revision's $30–60K because the YieldVaultAdapter and its deposit caps, circuit breaker, and loss waterfall are gone (§6.6), as is all multiplier arithmetic. Recommended firms: Halborn, Neodyme, Ottersec.
- **Multisig admin:** every admin function (pause, treasury withdrawal, parameter change) requires 3-of-5.
- **Upgrade strategy:** core contracts upgradable via Solana program-data accounts; upgrade authority is the multisig.
- **Segregation invariant — the single most important property to audit:** `prize_pool_usdc` may only ever be credited from forfeited stakes and the bonus match. No instruction may move a member's segregated principal into the prize pool while their status is `Active`. A shortfall is impossible by construction and the audit must confirm that.
- **Settlement immutability:** `total_finisher_stake` is written exactly once, by `settle_cohort`, and is immutable thereafter. Every claim divides by the same number.
- **Reentrancy:** Solana's account model prevents classical reentrancy; state-changing instructions still use an explicit lock pattern.
- **Oracle dependency:** the SKR/USDC price comes from Jupiter at swap time. No external oracle is needed mid-cohort, since USDC is the unit of account inside the pool.
- **Bonus matching sunset:** hardcoded to deployment-time block heights; cannot be extended without an upgrade transaction.

### 9.5 Off-chain components

- **Mobile client (React Native + Expo):** UI, on-device pose inference, transaction submission, share-card generation
- **Keeper bot (Node.js):** `tick_cohort` each minute window, `open_cohort` / `settle_cohort` on schedule, retries, pool health monitoring
- **Indexer (Helius or custom):** indexes events for the live survivor counter, leaderboards, stats, and share-card data
- **Pose model server:** none — inference is fully on-device

---

---

## 10. Mobile app — screen inventory and flows

### 10.1 Screen inventory (MVP)

**Onboarding & access**
1. Welcome / Seeker Genesis Token verification
2. Wallet connection (auto via Seeker)
3. Geo eligibility check (silent unless blocked)
4. Terms of Service acceptance (skill-contest framing)
5. Quick tour (3 screens: how it works, what's at stake, anti-cheat ethics)

**Home & navigation**
6. Home — the cohort screen when a cohort is active (see §10.1a); otherwise the league ladder with the next cohort's opening date
7. Bottom nav: Home / My Holdout / Leaderboard / Profile

**Stake flow**
8. League ladder — three rungs, showing which are unlocked, which is next, and when each opens. Locked rungs state their requirement plainly, and the qualifier states that a **free** week clears it
9. Stake amount picker — the league's three preset chips, live SKR equivalent
10. Join confirmation — league, cohort start date, duration, daily obligation, SKR cost, slippage estimate, the explicit line "if nobody drops out, you get exactly your stake back", and "you can leave any time before Monday and get your stake back in full"
10a. **Lobby** — countdown to Monday, live roster count, your stake, "Leave" button, and the two things to do while waiting: start a solo session, run the camera calibration and baseline test (§5.2)
11. Wallet transaction signing
12. Stake success — "You're in" celebration

**Daily session flow**
13. My Holdout — the active cohort with "Start today's session" CTA, days remaining, live prize. With no active cohort: "Keep your streak" CTA that starts a solo session (§12.2)
14. Pre-flight environment check — 5 status pills, real-time updates
15. Palm liveness — "Show your palm" prompt
16. 3-second countdown
17. Active session — live camera + skeleton overlay + rep/timer counter + audio level indicator
18. Session complete — workout summary, share CTA, survivor counter and live prize updated
19. Session failed (within-day) — "X/25 push-ups completed. Try again or come back later today. Deadline: HH:MM your time"

**My Holdout**
20. Active cohort view with live prize, survivor counter, days remaining, today's status
21. Cohort detail — full timeline, daily completion record, attrition graph, projected payout, share button
22. Settlement notification (push + in-app) — "You outlasted the crowd. $44.22 is yours — tap to claim."

**Stats & social**
23. Personal stats — total reps, longest streak, total earnings, league badges and their completion counts
24. Workout share card (full-screen preview, swap variants, share/save)
25. Stats share card
26. Cohort recap share card (after settlement)
26a. Swelfie capture (post-completion front camera, countdown + retake) — see §12.5
26b. Swelfie compose (Photo / Card / Both selector, watermark + referral QR, share)

**Leaderboard**
27. Leaderboard — per league, and per live cohort, sortable by streak / earnings / leagues cleared

**Profile & settings**
28. Profile — display name, country flag toggle, avatar (placeholder pre-avatar-system)
29. Settings — notifications, dark mode toggle, link to TOS / privacy / support
30. Help — FAQ, contact support, report a bug

### 10.1a The home screen — where the engagement actually comes from

With Sweat Yield and tournaments out of scope, the loop has to be interesting on its own. It is, and the material is already in the data: **fixed cohorts mean there is a crowd, and the crowd visibly shrinks.**

```
        FRONT LEVER · day 12 of 21

              189 / 312
            still standing

         your prize now   $46.20
      ▲ +$3.10 — three dropped overnight

         [ do today's session ]
```

Three mechanics, all free to build:

1. **The survivor counter is the headline, not the balance.** `189 / 312` is a battle-royale frame, and it is the single most compelling number the product owns. A rolling-join pool cannot display it; a fixed cohort can.
2. **The prize jumps, and the jump is announced.** Not a quietly refreshed figure — a morning push: *"Four people dropped out overnight. Your share went up $2.80."* This is the reason to open the app on a day when nothing else happened.
3. **Both numbers move together, in the member's favour.** Every forfeiture simultaneously grows the pot and shrinks the divisor, so the figure accelerates as the cohort thins. Toward the end of a heavily-attrited cohort, the daily jumps become dramatic on their own.

Supporting beats: a halfway marker ("day 11 of 21 — you're in the top 40% of this cohort"), the endgame ("only 12 left of 340"), and the cracked-flame streak break (§12.2).

**Copy rule:** the app never says "winnings," "returns," "yield," or "earnings" on the home screen. It says **prize**. See §13.2.

### 10.2 Critical flow: stake-to-first-session

The most important flow in the app. Friction here = lost conversion.

```
Home (Push-ups card tapped)
  ↓
Exercise detail (Standard 7d selected by default — lowest commitment)
  ↓
Stake amount ($1 selected by default — lowest stake)
  ↓
Confirmation (shows: "You're committing to do 25 push-ups every day for 7 days. Miss any day and your $1 stake is lost.")
  ↓
Wallet signs (Seeker quick-approve)
  ↓
"You're in" screen with two CTAs: "Do today's push-ups now" / "I'll do it later"
  ↓
If "now" → pre-flight → palm → countdown → session
If "later" → home with active stake card highlighted
```

Total time from app open to first push-up: target <90 seconds for a returning user, <3 minutes for first-time.

### 10.3 Daily engagement loop (post-stake)

For users with active stakes, the loop is designed to drive ≥1 app open per day:

- **Morning push notification:** "Day 12 of 21. Three dropped out overnight — your prize is now $46.20." (§10.1a)
- **Evening push notification (if not completed):** "3 hours left. Don't forfeit your stake."
- **Post-session:** survivor counter and live prize update immediately, shareable card generated
- **Settlement day:** big push notification with win amount, deep link to share card

### 10.4 Error states and edge cases

| State | UI treatment |
|---|---|
| User has no SKR balance | **In-app one-tap swap sheet** (new): "You need ~$25 SKR to stake. Swap USDC → SKR now (fee ~$0.13)." Powered by Jupiter Aggregator SDK with Holdout's referral wallet as fee recipient (~50bps commission, kept by Holdout as ancillary revenue). Keeps user in-app — they never bounce to Phantom, Jupiter web, or a CEX. |
| Slippage exceeds 0.5% | Modal: "SKR price moved. Updated estimate: X USDC. Continue or cancel?" |
| Pre-flight fails repeatedly (≥3 attempts) | Help tip: "Trouble starting? Common fixes: [list]. Need help? [support link]" |
| Session crashes mid-rep | Auto-resume from Firebase session state on app relaunch (see §8.6) |
| **Geo-blocked at join** | Modal: "Paid leagues aren't available in your region. **Push-Up is open to you every week — same workouts, same cohort, same leaderboard, no money involved.**" CTA: "Join the next Push-Up cohort" (default) / "Just view stats" |
| User on non-Seeker device | App allows install, but nothing is playable at MVP: "Holdout is currently Seeker-only. Other Solana Mobile devices are coming." Non-Seeker access lands in v1.2 (`holdout_decisions.md` §5.4) — until then this is a waitlist screen, not a product |

### 10.5 Free access — free cohorts and solo sessions

Holdout has **two free lanes**, and between them a member never has to spend anything to get the product:

**1. A free cohort in every league, every Monday** (§5.1). The complete contest — same duration, same daily obligation, same survivor counter, same crowd thinning out, same leaderboard. No stake, no prize, no badge, no league unlock. This is the sandbox: find out whether you can actually hold a four-week streak before putting $100 on it.

**2. Solo sessions** (§12.2). No cohort at all — just the daily session and the streak. For members using the app as a personal tool, between rounds or before ever joining one.

What is gated is exactly two things: **the prize money and the ladder.** Everything that makes the product engaging to look at is free.

**Three earlier designs were tried and dropped on the way here:**

1. **Glory Mode** — a parallel free-to-play mode with its own flow, leaderboard, badge set and a Staked/Glory switcher. Removed: two free lanes doing the same job, and a second code path for what the ladder already did.
2. **A single free league (Push-Up only)** — made the free tier the shortest, cheapest rung, so nobody could find out how a four-week commitment feels without paying for it.
3. **Paper entries inside staked cohorts** — a zero-stake seat in a real cohort with a "shadow prize". Good conversion mechanic, wrong mechanism: at low volume the unstaked seats diluted the pool that every member in that cohort was looking at.

Running a free cohort **alongside** the staked one keeps what paper entries were reaching for — the full experience, free — while removing the dilution entirely. The cost moves from pool depth to population split, which is far cheaper: a free cohort needs enough members for the counter to be interesting, not enough to fund a prize.

**Conversion path.** Not a hypothetical figure but a fact the member produced: they cleared a free cohort and saw what the staked cohort next door paid out that same week. The offer writes itself — *"You'd have finished. The staked Human Flag cohort that started the same Monday paid $64 to each of its 108 finishers."* Same league, same week, real number, and they are the proof they can do it.

**No geo special-casing.** Free cohorts carry no stake and no prize, so they are legal everywhere and need no jurisdiction logic (§4.3). A member who cannot stake still gets the whole contest.

---

---

## 11. Design system

Locked on 2026-05-21 to match the Seeker brand for ecosystem-native feel.

### 11.1 Color tokens

| Token | Hex | Usage |
|---|---|---|
| `bg/primary` | `#F6F6F5` | Main background (light mode) |
| `bg/secondary` | `#FAFAF4` | Secondary surface |
| `fg/primary` | `#10282C` | Primary text, headlines |
| `fg/secondary` | `#0B3446` | Secondary text |
| `surface/mint` | `#CFE6E4` | Cards, secondary surfaces |
| `cta/primary` | `#0099FF` | Primary action buttons (Seeker cyan) |
| `accent/celebration` | `#FF6B47` | Win moments, celebration accents (warm coral) |
| `accent/lime` | `#B8E62C` | Secondary celebration accent |

Dark mode toggle uses inverted scheme (dark teal background, cream text). Spec for dark mode deferred to design handoff phase.

### 11.2 Typography

- **Display:** PP Mori (paid license, ~$50-100)
- **Body:** Inter (free, system fallback)
- Scale: 32 / 24 / 20 / 16 / 14 / 12 px on mobile

### 11.3 Differentiation strategy

The visual identity must feel like **premium consumer fitness (Apple Fitness, Calm, Strava)** — NOT like a DEX or DeFi dashboard. This is a key differentiator from Squadletics' generic crypto-mini-app aesthetic.

Specific UI principles:
- No "swap" / "bridge" / "TVL" / "APY" language at the surface
- Large, confident type
- Generous whitespace
- Photographic or 2D-illustrated celebration moments (not crypto-icon stickers)
- Numbers (survivor counter, live prize, rep count, days remaining) are the visual hero

### 11.4 Iconography

- Use San Francisco SF Symbols where possible (Apple-native feel)
- Custom illustrations for the four league badges commissioned from Illia's wife (illustrator)
- Avatar system illustrations: AI-generated, vector-cleaned in post

### 11.5 Design tooling — Claude-native workflow

Locked 2026-05-22. Holdout does not hire a designer at MVP. The full design system, screen library, and interaction prototypes are built using Claude's visual tooling in-conversation, then exported as handoff specs for build:

| Tool | Used for |
|---|---|
| `visualize` widget (HTML/SVG) | Interactive screen prototypes — render full mobile-shape mockups inline, iterate in conversation, get instant feedback |
| `canvas-design` skill | Static illustrations, badge concepts, share-card layouts, app icon variants |
| `pptx` skill | Pitch decks, grant application visuals, investor-facing slide decks |
| `docx` skill | Final design handoff specs (Figma-equivalent component specs as a Word doc with embedded screenshots) |
| `design-handoff` skill | Generate developer-ready specs from finalized mockups: tokens, props, states, responsive breakpoints, animation details |
| `design-critique` skill | Self-review pass on each screen — usability, hierarchy, consistency, before locking |
| `accessibility-review` skill | WCAG 2.1 AA pass on every primary flow before build |

**Workflow:**
1. Brainstorm screen in chat → ask Claude to mock as `visualize` widget
2. Iterate on mockup live until it feels right
3. Run `design-critique` on the locked version
4. Run `accessibility-review` for contrast / hit-target / focus checks
5. Run `design-handoff` to generate the build spec
6. Mobile dev (Illia + AI agents) implements from the spec in React Native + Expo

**Why this is realistic at solo-founder scale:** modern AI mockup quality clears the bar for a v1 consumer-fitness app. We're not designing Apple Health; we're designing a focused 30-screen MVP with strong brand discipline. The visual tooling can carry us through MVP and into v1.1; we revisit hiring a real designer when revenue justifies it.

---

---

## 12. Social sharing and viral loops

### 12.1 Card types (MVP)

**Workout card** — generated automatically after each completed daily session. Shows: league, reps completed, day X of N, survivors remaining out of starters, current streak, live prize, app branding, embedded referral QR.

**Cohort recap card** — generated at settlement. Shows: total reps over the cohort, days completed, final payout amount, "I outlasted the crowd for X days" headline, referral QR.

**Stats card** — user-initiated, shareable anytime. Shows: lifetime reps, current streak, total winnings to date, highest league cleared and its badge, referral QR.

### 12.2 The two streaks

Holdout tracks **two separate streaks**, and conflating them is a design error. One measures the habit, the other measures status. They have different rules, different homes in the UI, and different prices.

| | **Daily streak** | **League streak** |
|---|---|---|
| Counts | days in a row with a completed verified session | cohorts cleared in a row without a forfeit |
| Home in the UI | home screen — huge numeral, flame icon | profile and leaderboard — quieter |
| Costs money? | **No.** Solo sessions count (below) | **Yes.** Only cohorts count |
| Survives a forfeit? | **Yes** — keep training and it lives | **No** — resets to zero |
| Drives | habit, milestone shares | status, leaderboard rank |

**The dividing line:** *status and progression are paid; the habit counter is free.* A member training every day for nothing is not a leak in the paid game — they are a retained member one nudge from staking. What we refuse to give away for free is rank, badges, and the cohort drama.

#### Solo sessions — the entire free tier

**Solo sessions are the lighter of Holdout's two free lanes** (§10.5) — the other being a free cohort in every league (§5.1). A solo session has no cohort at all: no crowd, no counter, no leaderboard. It exists for the member who wants the app purely as a personal tool.

A member not currently inside a running cohort can run a **solo session**: the ordinary session flow (pre-flight, palm liveness, pose detection, rep count) with no cohort attached. Three situations call for it:

- **Before ever staking.** Run the app on yourself for as long as you like. Build a streak, learn the camera setup, satisfy yourself the rep counter actually works — then join a round when you are ready to put money down. This is the front door.
- **Before a first start.** Someone who joins the lobby on a Wednesday has five days before day 1. Solo sessions in those days mean they arrive at Monday already carrying a streak — the wait becomes investment rather than delay.
- **After a mid-cohort forfeit.** Forfeit on a Wednesday and the next start is up to five days away. That is precisely when we least want someone to stop training.

A solo session gives **only the daily streak**. It has no cohort, no survivor counter, no leaderboard entry, no badge, no prize, and no bearing on league unlocks. It is one screen reusing components that already exist — it is not Glory Mode returning, because everything that made Glory Mode a parallel product is absent.

**Solo sessions are entirely off-chain.** Nothing financial depends on them, so they write to Firebase only (§8.6) — no attestation, no signature, no gas. This matters for feel as much as cost: a daily wallet prompt for a free cosmetic counter would be absurd friction. The consequence is that the daily streak is not trustlessly verifiable, which is correct — it is a cosmetic number, and nothing is ever paid out against it.

**No longer deferrable.** An earlier draft treated solo sessions as an optional gap-filler. With every round starting on a Monday, they cover the pre-start window too — which is the single highest-drop-off moment in the whole funnel, between deciding to join and actually beginning. They ship in Month 3.

#### Streak counter on the home screen (locked 2026-05-22)

The home screen shows the **daily** streak as a **huge numeral** with a flame icon, above the fold (alongside the survivor counter when a cohort is running — §10.1a). Animation states:

| State | Visual |
|---|---|
| Active streak ≥1 day | Bright flame icon, streak number in cream, subtle pulse animation |
| Streak extended today | Burst animation — flame grows momentarily, number flips up |
| Missed a day (cracked streak) | **Cracked-flame icon**, number greys out, emotional copy: "Your 14-day streak broke. Start a new one." (NO save button — see §5.3, we don't allow grace days) |
| New all-time record | Gold flame, "PR" badge added next to number, forced-share modal fires |

**The league streak breaking is a separate, harder moment.** Forfeiting a cohort costs the stake *and* resets the league streak — but the daily streak survives if the member keeps training. That asymmetry is deliberate: the money penalty is the penalty, and we do not also take away the habit the product exists to build.

#### Milestone forced share

At 3, 7, 14, 21, 30, 50, 100 and 365 **daily**-streak milestones the app **proactively** generates a milestone card with pre-written copy and prompts the user with one-tap share. Reward: first share at each milestone unlocks a cosmetic on the next-generated card (flame color upgrade, frame style, particle effect, etc).

**Why aggressive milestones at low days (3, 7, 14):** Duolingo's data showed that users who shared their streak early were 2.3× more likely to retain past 30 days. We're copying that mechanic directly — early sharing creates external accountability AND seeds viral growth before our matching budget burns down.

This is the strongest organic viral driver in the app — milestones are inherently shareable and crypto-native fitness users will share.

### 12.3 Referral mechanic

Every share card embeds a QR code + short URL + iOS/Android deep link with the user's referral code. Referral reward at MVP:

- **Referee** (new user) gets a **$5 USDC starter credit** applied as a discount on their first stake (max $5 off a $25 stake; they still pay the rest in SKR)
- **Referrer** earns **$5 USDC** when the referee completes their first cohort successfully (paid from Marketing allocation of Fee Treasury)

Why "first cohort successfully" rather than "first stake": prevents farming with self-referrals. Sybil risk further limited by Seeker-Genesis-Token-per-account.

### 12.4 No social network features at MVP

Explicitly excluded:
- In-app feed of other users' workouts
- Direct messaging
- Following / followers
- Comments or reactions
- World Chat equivalent

Rationale: social network features add huge moderation burden and don't directly drive the staking/completion loop. Leaderboard + share cards provide enough social presence for MVP.

### 12.5 Swelfie — verified workout selfie

A **Swelfie** ("swole" + "selfie") lets a user attach a real photo of themselves to the moment they just finished a workout, then share it to their own social media. It turns the abstract "I earned $X" share card into proof-of-effort with a human face on it — the single most shareable artifact a fitness app can produce.

**Capture — live only, strict (locked):**

- The Swelfie can **only** be taken in-app, with the live camera, in the seconds immediately after a verified session completes. There is **no gallery upload** at MVP.
- After §8.7 completion, the celebration screen offers a **"Take your Swelfie"** button. Tapping it opens a front-camera capture with a 3-2-1 countdown. The user gets one retake loop ("Use this / Retake") before confirming.
- Because capture is gated behind a just-completed, on-chain-attested session, every Swelfie is implicitly tied to a real workout. We do **not** embed the photo in the on-chain attestation (cost, privacy) — the tie is temporal and UX-level, not cryptographic. We make no on-chain claim that the photo is authentic; it is presented as a personal share, not as anti-cheat evidence.

**Format — photo + separate stat card (locked):**

The Swelfie does **not** become the background of the stat card. Instead the user is handed **two assets** and can post either or both:

1. The **raw Swelfie** — the user's photo, with only a small, tasteful Holdout watermark + the user's referral QR in a corner. Minimal branding so it reads as a genuine personal post, not an ad.
2. The **auto-generated stat card** (§12.1) — reps/time, day X of N, streak, projected/earned winnings, referral QR.

The compose screen lets the user select Photo only / Card only / Both (posted as a 2-image set via the OS share sheet). Default selection: Both.

**Incentive — none (locked):** posting a Swelfie earns **no reward** at MVP. It is a purely optional expression feature. This deliberately sidesteps any Sybil/farming surface — there is nothing to game because there is no payout, cosmetic, or referral credit attached to the act of taking or posting a Swelfie. (The referral QR on the assets still works through the normal §12.3 mechanic, which only pays out on a referee's first successful cohort.)

**Privacy, storage, and moderation — why this is low-risk:**

- **On-device only, no server hosting.** The Swelfie is captured, composed, and handed directly to the OS share sheet on the device. Holdout **never uploads, stores, or hosts** the photo on its own infrastructure. This keeps the feature consistent with the app's core §8.1 "no media persistence" posture — the only image that ever exists lives on the user's phone and in whatever social account *they* choose to post it to.
- **No in-app display.** Per §12.4 there is no feed; a Swelfie is never shown to other Holdout users inside the app. Because Holdout neither hosts nor displays user photos, it carries **no content-moderation hosting liability** — there is nothing for us to moderate or take down. This is the key reason the feature is MVP-safe.
- **Consent + metadata hygiene.** First use shows a one-time consent sheet ("Your Swelfie is created on your device and shared only where you choose. Holdout never stores it."). On compose, **EXIF/GPS metadata is stripped** from the exported image so the user's location isn't leaked in the post.
- **Anti-cheat separation (cross-ref §8.1):** the live frames used for pose/rep detection during the session are still processed in real time and discarded — never persisted. The Swelfie is a *separate, deliberate, user-initiated* capture that happens *after* the session ends. The two camera uses must not be conflated in code or copy: anti-cheat frames are never saved; the Swelfie is saved only to the user's own device at their explicit action.
- **Minors / inappropriate content:** governed by TOS (one human per Seeker via §8.3 palm-liveness + Genesis Token gate, 18+ attestation). Since Holdout never hosts the image, our exposure is limited to the same standard self-publishing disclaimer any camera app carries.

**Availability:** Swelfie is available in the free **Push-Up league** too — a free member finishes a verified session, then captures. It is one of the strongest organic-growth surfaces for non-staking members and costs us nothing.

**Edge cases (Swelfie):**

| Case | Behavior |
|---|---|
| User denies camera permission for Swelfie | Skip silently; fall back to stat-card-only share. Never block the completion/celebration flow. |
| User dismisses without capturing | Normal completion; Swelfie can still be taken later from the **Stake detail** screen *only for the most recent verified session that day* (keeps the "real workout" tie). |
| Low light / no face detected | Allow capture anyway — it's the user's photo, not anti-cheat. No face-detection gate. |
| OS share sheet cancelled | Return to compose screen; assets retained in memory until the user leaves the screen, then discarded. |

---

---

## 13. Regulatory framing

This section is informational and is **not legal advice**. A daily-fantasy-sports or skill-gaming lawyer must review and finalize the actual TOS and disclosures before public launch.

### 13.1 Core legal frame: skill contest

Holdout is positioned as a **physical skill contest with an entry stake**, not a game of chance. The skill being measured is the user's ability to perform a defined physical task consistently over a defined time period. This framing is critical because:
- Skill contests are broadly legal under US federal and most state laws
- Games of chance are subject to heavy gambling regulation (state-by-state licensing, fees, etc.)
- The skill-contest framing has strong legal precedent (DraftKings, FanDuel, Skillz, fitness commitment apps like DietBet)

### 13.2 Language guide

| Use | Avoid |
|---|---|
| Stake, challenge, cohort, survive, earn, payout, contest | Bet, wager, gamble, luck, odds, lottery, prize money |
| "Demonstrate physical skill" | "Win money" (use "earn payout") |
| "Complete your daily obligation" | "Place your bet" |
| "The stakes of members who did not finish are distributed to those who did" | "Jackpot", "pot", "winnings" |

### 13.3 Geo-block enforcement at MVP

**US blocked states (explicit gambling prohibition or hostile case law):** AR, CT, DE, LA, SD, WA, AZ, MT, IA, TN.

**Non-US blocked:** Mainland China, Hong Kong, sanctioned countries. Germany, Netherlands, France, Australia under review.

**Enforcement:** at the moment of stake (not at install), the contract checks the user's IP geolocation + Seeker location attestation. If blocked, the stake transaction reverts with a user-friendly error. Users can install the app and view leaderboards from anywhere; only the stake itself is gated.

### 13.4 TOS requirements (drafting checklist for legal review)

The TOS must explicitly:
1. Define Holdout as a skill contest, not a wagering platform
2. Disclose the no-refund policy with the pre-flight + checkpoint protections
3. Disclose the 10% platform fee
4. Disclose the SKR-to-USDC routing and Jupiter dependency
5. Disclose the cold-start bonus matching window and its sunset
6. Require user attestation that they are NOT physically located in a blocked jurisdiction
7. Require user attestation that they are 18+ (or local age of majority)
8. Disclose risks: smart contract risk, SKR price volatility, USDC redemption risk, slippage risk. State plainly that **a cohort in which nobody forfeits pays no prize** — finishers receive exactly their stake back
9. Disclaim Anthropic/Solana Labs/Solana Mobile/Seeker affiliation (Holdout is independent)
10. Specify dispute resolution (arbitration, jurisdiction)

**Budget:** $5K-15K for initial TOS drafting by a daily-fantasy-sports or skill-gaming attorney. Recommended firms with relevant experience: Sheppard Mullin, Greenberg Traurig (skill gaming practices), or boutiques familiar with Skillz/Yahoo DFS precedent.

### 13.5 Why Squadletics has it easier here

Squadletics operates as a **Worldcoin Mini App**, which means:
- Worldcoin's platform-vetted skill-contest framing applies
- Worldcoin's existing per-country app gating handles geo-block enforcement for free
- Worldcoin's developer policy provides legal cover (limited but real)

Holdout has no equivalent umbrella. Illia is the responsible party for compliance. This is a real cost but also a real moat — Worldcoin Mini App distribution comes with hard constraints we're free of.

---

---

## 14. Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| Pool too small at launch — no draw to stake | High | Cold-start 50% bonus matching; treasury-seeded early "phantom failer" stakes (TBD); Seeker dApp Store featured launch |
| SKR price crashes during user's cohort | Medium | Pool is USDC-denominated post-swap; user is insulated from SKR volatility once staked |
| SKR token illiquidity causes high swap slippage | Medium | 0.5% slippage limit with revert; user sees estimate; could route through SOL as intermediate if direct SKR-USDC pair is thin |
| Pose detection false-negatives frustrate honest users | High | Aggressive pre-flight to refuse bad conditions; permissive within-rep validation (silent discard with hint); fast iteration on rules post-launch |
| Pose detection false-positives let cheaters win | Medium | Multi-modal stack (audio, palm liveness, timing bounds) makes simple cheats hard; ML-savvy adversaries are out-of-scope at MVP |
| Smart contract bug drains pool | Critical | Third-party audit before mainnet; multisig pause power; insurance allocation in treasury; conservative initial pool size caps for first month |
| Yield-venue illiquidity blocks a user claim | High | Supply-only instant-redeemable position; deposit cap sized to guarantee full instant exit; idle-USDC fallback so settlement never depends on yield being live |
| Keeper bot fails to call `tick_cohort` in a midnight window | High | Idempotent design — multiple bots can run; manual override via multisig; deeper fix is decentralized cron (Switchboard) post-MVP |
| Regulatory action in a launch jurisdiction | High | Conservative geo-blocking; lawyer-drafted TOS; skill-contest framing throughout; Seeker-only narrowing limits attention surface |
| Squadletics ships a Solana version first | Medium | Sheer speed: ship MVP in 4.5 months. Squadletics has no public Solana plans. |
| Seeker hardware ships fewer units than expected | High | Expand to other Solana Mobile devices in v1.x; v2.0 adds non-Seeker access with Civic Pass humanity proof |
| Audit finds critical bug requiring redesign | Medium | Build in audit-fix buffer (4 weeks after first audit pass before launch) |
| User psychologically harmed by losing stake | Low-medium | Conservative max stake at MVP ($100); pre-flight protects against accidental loss; clear TOS warns of risk; no marketing aimed at gambling addicts |

---

---

## 19. Appendix A — settlement test vectors

Every vector uses the model in §7: one pool per cohort, 20% fee taken at forfeit time, payout share proportional to the finisher's own stake. **These are the cases the settlement contract must reproduce exactly.**

`pool = Σ forfeited stakes × 0.80` · `payout = own stake + pool × (own stake ÷ Σ finisher stakes)`

### 19.1 Uniform stakes

| # | League | Members | Stake | Forfeits | Pool | Finishers | Σ finisher stake | Share each | Payout each |
|---|---|---|---|---|---|---|---|---|---|
| V1 | Qualifier | 40 | $25 | 13 | $260.00 | 27 | $675 | $9.63 | $34.63 |
| V2 | Front Lever | 150 | $100 | 92 | $7,360.00 | 58 | $5,800 | $126.90 | $226.90 |
| V3 | Human Flag | 90 | $150 | 41 | $4,920.00 | 49 | $7,350 | $100.41 | $250.41 |
| V4 | Front Lever | 8 | $75 | 5 | $300.00 | 3 | $225 | $100.00 | $175.00 |

### 19.2 Mixed stakes — proportionality

**V5 · Front Lever, 10 members.** Stakes: 4×$25, 3×$50, 2×$75, 1×$100 (Σ $500).
Forfeits: 3×$25, 2×$50, 1×$75 = **$250 forfeited** → fee $50 → **pool $200**.
Finishers: 1×$25, 1×$50, 1×$75, 1×$100 (Σ $250).

| Finisher stake | Share | Payout |
|---|---|---|
| $25 | $20.00 | $45.00 |
| $50 | $40.00 | $90.00 |
| $75 | $60.00 | $135.00 |
| $100 | $80.00 | $180.00 |
| **Σ** | **$200.00** | **$450.00** |

**Invariant:** Σ payouts = Σ finisher stakes + pool = $250 + $200 = $450. Assert this on every settlement.

### 19.3 Edge cases the contract must handle

| # | Case | Required behaviour |
|---|---|---|
| V6 | **Zero forfeitures** — every member finishes | Pool = $0. Every finisher is paid exactly their stake back. No fee is taken. Settlement must not divide by zero or revert. |
| V7 | **Zero finishers** — every member forfeits | Pool has no claimants. The whole pool routes to the Insurance/Treasury allocation (§6.3); no payout PDAs are created. Must not strand funds. |
| V8 | **One finisher** | That member takes the entire pool plus their stake. |
| V9 | **Cohort below minimum size at start** (<8 after cancellations) | Cohort cancels. Every member refunded **in full**, no fee, and auto-enrolled into the next opening (§7.4). |
| V10 | **Pre-join resolution** (§5.6a) — member forfeits the running cohort while holding a `Pending` entry | The pending stake is refunded in full, automatically, in the same settlement transaction. |
| V11 | **Rounding** | Integer USDC base units. Truncate each share; the remainder (at most *n*−1 base units) stays in the pool PDA and sweeps to Treasury. Never mint to cover a rounding gap. |
| V12 | **Double claim** | Second `claim_payout` on the same membership must revert. Status moves `ReadyToClaim` → `Claimed` atomically with the transfer. |

### 19.4 Fee accounting

For every cohort: `fee = Σ forfeited stakes × 0.20`, taken **at forfeit time**, not at settlement — so pool accounting stays a single running number. Cross-check at settlement: `fee + pool == Σ forfeited stakes`.

---

## 20. Appendix B — glossary

| Term | Definition |
|---|---|
| **League** | One rung of the ladder: Push-Up (1 week, 25 reps — the qualifier), Front Lever (3 weeks, 25 reps), Human Flag (4 weeks, **30 reps**). Named after calisthenics milestones. Front Lever pays the biggest prize; Human Flag is the summit (§5.1) |
| **Cohort** | A group who start the same league on the same Monday and finish together. One live cohort per league at a time |
| **Unlock** | Clearing a league permanently unlocks the next one. Unlocks never expire and are never revoked (§5.5) |
| **Prize pool** | The USDC held by one cohort, funded by that cohort's forfeitures (80% of them) plus any cold-start match. Never shared with another cohort or league |
| **Forfeiture** | The loss of a member's entire stake to their cohort's prize pool when they miss a required daily session |
| **Fee (20%)** | Holdout's only fee: 20% of each forfeited stake, taken at forfeit time, routed to the Fee Treasury and swapped to SKR. Finishers pay nothing |
| **Segregated principal** | A member's own stake, held apart from the prize pool for the whole cohort and returned in full to every finisher |
| **Live prize** | The UI figure showing what a member would receive if the cohort ended now — recomputed at every daily tick |
| **Survivor counter** | The `N / M` figure on the home screen: how many of the cohort's starters are still standing |
| **Settlement** | The single cohort-wide event at the final midnight window: finishers counted, denominator fixed, payouts marked claimable |
| **Fee Treasury** | The permanent SKR holding built from the 20% fee — never sold, earns Seeker Guardian staking yield |
| **Cold-start bonus** | Treasury matching added to prize pools for six months post-launch — 50% in months 1–3, 25% in months 4–6, zero from month 7 |
| **Pre-flight check** | The environment validation required before any session can start |
| **Palm liveness** | The 3-second open-palm verification at session start |
| **League badge** | One evolving cNFT per league, advancing in material by repeat completions of that league: bronze (1) → silver (3) → gold (6) → platinum (12) |
| **Keeper bot** | Off-chain service calling `tick_cohort` each midnight window to mark no-shows, plus `open_cohort` and `settle_cohort` on schedule. The majority of state advances are user-driven via `submit_session` and `claim_payout` |
| **Daily streak** | Days in a row with a completed verified session, cohort or solo. Free, off-chain, survives a forfeit. The habit metric |
| **League streak** | Cohorts cleared in a row without a forfeit. On-chain, resets on forfeit. The status metric |
| **Solo session** | An ordinary verified session with no cohort attached. Feeds the daily streak and nothing else — no leaderboard, no badge, no prize, no gas |
| **Qualifier** | The one-week Push-Up league — the single gate in the product. Clearing it unlocks Front Lever **whether the cohort was free or staked**, and retires the staked route permanently. The free cohort never closes (§5.1) |
| **Qualifying clear** | Any clear of the one-week league, free or paid. Sets `UserProgress.qualified`. A free one grants the unlock and nothing else — no prize, no badge, no league streak (§5.1, §9.3 Op 5b) |
| **Home** | A league that never closes and can be run indefinitely: Front Lever (21d, 25 reps, $25/$50/$75/$100) and Human Flag (28d, 30 reps, $50/$100/$150/$200 — exactly double). After one qualifying week, these are a member's only staked options (§5.1) |
| **Push-Up league** | The qualifier: 1 week, 25 reps, free or $5/$10/$25, open to everyone every week until they clear it. The **free** cohort never closes and is the permanent habit lane and comeback path (§5.1) |
| **Comeback path** | The free one-week cohort, offered the Monday after any forfeit, with the member's daily streak still running. The designed substitute for the short paid league that no longer exists (§5.1b; thresholds and the pre-agreed remedy in `holdout_decisions.md` §4 and D14) |
| **Free cohort** | A cohort with no stake, running alongside the staked one in every league every Monday. Full contest — counter, crowd, leaderboard — but no prize, no badge and no league unlock. The sandbox (§5.1) |
| **Staked cohort** | A cohort with money in it. The only kind that pays a prize, mints a badge or advances the ladder |
| **Skill contest** | Legal framing — Holdout is a contest of physical skill (consistency), not a game of chance |

---

*End of document. Next steps: route to lawyer for TOS draft; engage a Solana smart-contract auditor; begin Month 1 sprint.*
