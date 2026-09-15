# Holdout — decision record and archive

**Companion to `holdout_v1_prd.md`.** The PRD says *what to build*. This file says *why it is that way, what was tried and rejected, and what is still open* — so that settled questions are not reopened by accident.

**Rule of thumb:** if you are about to propose a change to the mechanic, search this file first. Most of it has been proposed before, and several things were adopted, reversed, and adopted again in a single day.

Economic figures live in `pool_mechanics_findings.md` (with `pool_mechanics_sim.py` to regenerate them). Competitor detail lives in `competitor_analysis_squadletics.md`.

---

## 1. Decision records — the contested ones

### D1. Three leagues, not four (2026-09-14)

**Decided:** Push-Up 7d (qualifier) → Front Lever 21d → Human Flag 28d. The 14-day Muscle-Up rung is deleted.

**Why:** every rung is a gate, every gate filters, and filtering shrinks the pot — prize money comes only from failures. The four-rung ladder took a median ~90 days to climb and got ~31% of stakers to the top. Three rungs get more people to the top, faster, in fatter cohorts. **More rungs is not more game; it is more waiting and less money.**

**What we expected vs what happened:** we expected Front Lever's prize to *fall* (less filtering → more clears → thinner pot). The simulation found the opposite — it pays 129.5%, not the estimated 65% — because the dominant effect is **repeat entries**, not the removed gate. See D8.

**Do not reopen by:** proposing an intermediate rung "to soften the 7→21 jump". That jump is the design. The softener is the free cohort, not a paid rung.

### D2. A free 7-day clear qualifies (2026-09-14)

**Decided:** clearing the one-week league unlocks Front Lever **whether the cohort was free or staked**.

**Why:** the barrier between a curious installer and a paying member should be seven days of proving it to themselves, not a payment. Install → 5 solo days → free week → Front Lever at $25 is **twelve days from install to staking**.

**The carve-out is deliberately narrow and must stay narrow.** A free qualifying clear grants the unlock and **nothing else** — no prize, no badge, no league streak. Everywhere else "free cohorts grant no progression" holds verbatim. This is the only exception in the product.

### D3. The staked qualifier closes on a clear; the free one never does (2026-09-14, reversing 2026-09-10)

**Decided:** once a member has qualified, the staked one-week cohort leaves their options permanently. The free one-week cohort never closes.

**Reversed reasoning.** The 2026-09-10 decision kept it open forever on a retention argument: *a member paying $5 a week to keep a habit is a paying, engaged member, and locking them out moves them out rather than up.* That was right about the member and wrong about the product — with a small base, every open rung splits the population, and the cheapest shortest rung absorbs traffic indefinitely.

**What made the reversal safe** is the free cohort, which did not exist as a concept when the original decision was taken. It supplies everything the retention argument protected — habit, daily streak, crowd, somewhere soft to land after a forfeit — permanently and free.

**Only a clear closes it.** Forfeit the qualifier and it is there again next Monday, as many times as it takes.

**Interim states that are obsolete:** two earlier same-day drafts — one retiring only the staked Push-Up, then one retiring a staked Muscle-Up as well ("two qualifiers, two homes") — are superseded.

### D4. Front Lever and Human Flag never close

They are **homes, not rungs**. A member may live in either indefinitely and is never forced from 21 days to 28. Only the qualifier is one-time, because a qualifier exists to be passed.

**The principle is "qualifiers close, homes do not" — not "each rung closes in turn."** Closing Front Lever would leave one paid league, which is not a ladder, and Front Lever carries the most staked volume. **Do not re-propose closing it on consistency grounds.**

### D5. Human Flag is the summit, not the payday (2026-09-14)

**Decided:** Human Flag keeps $50/$100/$150/$200 and is positioned as glory. We stop trying to make it out-pay Front Lever.

**Two levers were aimed at the split and neither works.** The 30-rep escalation: Human Flag stays below Front Lever even at m = 2.2, a step far larger than 25→30 reps could deliver. The doubled chip ladder: stake 2.00× × prize-percentage 0.52× = **1.04× in dollars**, which is noise, and it inverts at churn ≥ 25%.

**The only working lever was rejected.** A ~3× ladder ($75/$150/$225/$300) lifts the median Human Flag prize to ~$101 against Front Lever's ~$54 — and prices the top league out of reach for most members, which is a worse product than an honest one.

> **Front Lever is where the money is. Human Flag is where the glory is.**

**This obliges copy work, not maths:** every surface showing both leagues shows both prize percentages, and the chip selector states Human Flag's return next to Front Lever's. A member who works the arithmetic out themselves and finds we implied otherwise will not trust any other number we publish.

**Why it is defensible rather than a flaw to hide:** the top league of a gated ladder is always the safest league, and the safest league always has the thinnest pot. Any ladder with progression has this property; ours is just honest about it.

**Re-open only** if data shows members abandoning Human Flag *because of the return* rather than the difficulty — and the fix would still be the chip ladder, never the rep count.

### D6. Why $25 is not in Human Flag (2026-09-14)

At $25 the top league would pay about **$16.93** against Front Lever's **$32.38** for the same money — seven more days, five more reps a day, and roughly half the money. It would have been **the only strictly dominated option in the product**: worse on every measurable axis, chosen by nobody who did the arithmetic, and a quiet betrayal of anyone who did not.

Accessibility lives where it works: **$25 still buys a full three-week Front Lever run**, and free cohorts cost nothing.

### D7. Stake ceilings came down deliberately (2026-09-14)

Front Lever's top chip went $200 → $100; Human Flag's $300 → $200. That costs staked volume and caps the biggest single payout at ~$121 rather than ~$181. It buys a selector where every step is the same size, and a top chip a normal member can picture choosing.

**Raising a ceiling later is easy and uncontroversial. Lowering one after members have anchored on it is neither.**

### D8. Repeat entries, not gating, drive the cohort economics (2026-09-14, from simulation)

A forfeit costs the stake but **not** the league level, so failure-prone members re-enter and are over-represented inside any cohort. This is the single most important dynamic in the model and it was missed by every earlier estimate.

Consequence: **"clear rate" is two different numbers.** Front Lever is 49.8% on a member's first attempt and 38.7% inside a real cohort. Human Flag moves the other way (47.4% → 54.6%) because it is gated and its entrants stay.

**Quote the cohort rate about a member's cohort, the first-attempt rate about their own odds. Never mix them.**

### D9. One prize pool per cohort; tier multipliers deleted entirely

A gated top league contains only members who cleared everything below, so it stops being the riskiest tier and becomes the **safest**. Multipliers exist to compensate risk; with the risk gone but the multiplier left, a shared pool transfers wealth from newcomers to veterans (44.6 pp fairness spread). Recalibration cannot fix it — the *fair* multiplier for a gated top league is the **lowest** of the set, the opposite of what a ladder signals.

| Design | Fairness spread |
|---|---|
| Shared pool, old multipliers | 41 pp |
| Shared pool, corrected multipliers | 4.4 pp |
| Gated + shared pool, best multipliers | 14 pp |
| **Gated + one pool per cohort** | **2.4 pp** |

Separate pools also delete the entire multiplier apparatus and the 53 pp blow-up risk from mis-estimating the failure rate.

### D10. No demotion

Tested and worse on every axis at once: it roughly halves the top league's median prize (harsher filtering → fewer failures → smaller pot) and cuts the share of members who ever reach the top. **Losing the stake is punishment enough.**

### D11. Fixed weekly cohorts replace rolling joins (supersedes 2026-05-22)

- **The math becomes explainable.** Everyone in, everyone out, one settlement. No moving denominator.
- **It creates a "we".** *"312 of us started. 189 are left. You are one of them."* A rolling pool cannot produce that sentence, and that sentence is the product's most engaging asset.
- **It removes the last unfairness.** Under rolling settlement a late finisher collected against a different pool state than an early one.
- **It gives marketing a beat.** A new season every Monday.

**The original objection was pool thinness, and it does not hold.** Prize distribution converges at ~20 participants; a cohort of 20–30 is enough, and a small cohort with heavy attrition produces an unusually large payday — a story, not a defect.

### D12. Free cohorts run beside staked ones, never mixed in

An earlier draft put free members *inside* the staked cohort as zero-stake "paper entries" with a shadow prize. **Dropped:** it diluted the pool everyone was looking at — a 20-member cohort with 8 unstaked has only 12 funding the pot, so the prize shrinks 40% for everybody.

Running free **alongside** costs population split instead of pool dilution, which is far cheaper: a free cohort needs enough members to make a counter interesting, not enough to make a prize interesting.

**It also dissolved the geo question entirely** — an unstaked cohort carries no prize and is legal everywhere, so there is no special-case handling anywhere in the code.

### D13. Manual claim, not auto-payout

Signing the transaction **is** the wow moment. An automatic deposit would have the member notice "+$44.39 USDC" silently and miss the visceral *"I made this happen."* Manual claim plus an immediate share card converts pride into distribution. **This is the highest-leverage UX choice in the app.**

### D14. The comeback path is the free lane

A member who forfeits Front Lever or Human Flag has no short *paid* league to rebuild on — cheapest paid re-entry is $25 over 21 days, the thing that just beat them. **This is the largest churn risk the design carries.**

The answer: after any forfeit the app offers the **free one-week cohort starting the next Monday**, with the daily streak still running through it (it survives forfeits). Copy-and-UI commitment, not a new mechanic.

**Pre-agreed remedy if post-forfeit return rate < 35%:** a one-week **forfeit-gated paid comeback cohort**, usable once per forfeit (`comeback_credit` in `UserProgress`). **Never reinstate a 14-day rung** — that re-splits the population this structure exists to concentrate.

### D15. Why 21 days is the main league

Squadletics — the incumbent, with hundreds of participants per challenge — runs **21 days × 25 reps**. That is the one real-world data point available about what duration people actually sign up for, and it beats any simulation on the question of appeal. An earlier draft removed the 21-day rung on simulation grounds and it was reinstated on this evidence.

---

### D16. Renamed Swolana → Holdout (2026-09-15)

**What the name had to carry:** the product is a motivator — you stake against your own lack of discipline, and the people who give in pay the people who do not.

**Why not a "-bet" name.** Fitbet, SweatBet and SkinBet were all considered and all rejected on the same two grounds.

1. **Every one of them is taken, in this exact category.** Fitbet is live as at least five separate products (two on Google Play, two on the App Store, plus fit-bet.com "Get Fit or Pay" and joinfitbet.com "Get Moving, Win Cash"); *SweatBet: Fitness Challenges* is already on the App Store; and **SkinBet is an operating CS2/Rust skin-gambling site** with its own iGaming media-buying agency. That every obvious "-bet" name is occupied is itself the finding: the word is the first thing everyone in this category reaches for, so it differentiates nothing.
2. **"Bet" contradicts §13 in a single word.** The entire regulatory posture is *physical skill contest, not gambling*, and §2.3 already bans "bet", "wager", "gamble", "luck" and "odds" from product copy. A product named after the banned word loses that argument with the app-store reviewer before anyone opens the app.

**Why Holdout.** It names the winning side of the same tension — your money against your own weakness — rather than the wager. It is the survivor counter on the home screen (§10.1a) said as a word. Nothing found under that name in fitness or crypto. And it keeps every existing tagline working unchanged.

**What was given up:** "Swolana" was a Solana pun, worth something with Solana Mobile hackathon judges specifically. Judged a fair trade against a name that survives the product's own legal framing.

---

## 2. Removed from scope, with reasons

| Removed | Why |
|---|---|
| **Sweat Yield** (staking yield on locked stakes) | Added a DeFi surface, a yield source to maintain and a regulatory question, for a return that rounds to nothing on 7–28 day locks. |
| **Tier multipliers** | See D9. Deleted entirely — no weights, no basis points, no assumed failure rate. |
| **Glory Mode** (parallel free-to-play mode) | A second code path for what the free cohort already does: own flow, own leaderboard, own badge set, a Staked/Glory switcher. Two free lanes doing one job. |
| **Paper entries** (unstaked seats inside staked cohorts) | Pool dilution. See D12. |
| **A single free league (Push-Up only)** | Made the free tier the shortest cheapest rung, so nobody could learn how a long commitment feels without paying. |
| **Plank / second exercise** | Doubles the number of pools at exactly the moment pool concentration matters most. MVP is one exercise. |
| **Rolling joins** | See D11. |
| **Demotion** | See D10. |
| **p̂ self-calibrating multipliers** | Moot once pools were separated per cohort. |
| **Solo staking** (bet on yourself, no cohort) | Regulatory: without a crowd it stops looking like a skill contest. |
| **Multi-entry power mode** (two simultaneous obligations) | Deferred. One Seeker = one daily obligation keeps enforcement and the mental model simple. |

---

## 3. Still open

| # | Question | Decide when |
|---|---|---|
| **O1** | **Churn-on-fail — the dominant unmeasured parameter.** It moves Front Lever's prize between 80% and 130% of stake. Everything economic in the PRD rests on the 18% assumption. | First thing to instrument in production. |
| **O2** | **Should the paid one-week qualifier exist at all?** Once a free clear qualifies, its only job is training wheels for staking — top chip $25, pays ~$8.57, unlock identical. Delete it if paid-qualifier share of qualifying clears is <10%, or if free and paid qualifiers convert to Front Lever at similar rates. One-line change; removes a cohort from every Monday. | Month 2, on data. |
| **O3** | **Is a paid comeback format needed?** See D14. Candidates in order: (a) one-week forfeit-gated comeback cohort; (b) a sub-$25 Front Lever chip (treats a duration problem with a price lever — probably misses); (c) reinstating a 14-day rung (last resort). **Do not pre-build any of these.** | Month 2, on post-forfeit return rate. |
| **O4** | **Is 30 reps the right escalation?** Measure the actual daily-miss rate at 30 vs 25. If the ratio is under 1.2 and the split is judged a problem, the lever is 35 or 40 reps — decided on observed miss rates, never on modelling. | Months 1–2. |
| **O5** | **Do members park in free cohorts indefinitely?** Accepted by design rather than prevented — a member running free cohorts is engaged, visible and one nudge from staking. Track how many never convert and how long they stay. A large long-lived group signals the staked leagues are not attractive enough, not a reason to restrict the free lane. | Ongoing. |
| **O6** | **Free-cohort minimum size.** The staked floor is 8, sized so a prize is not a coin flip. A free cohort has no prize, so the floor exists only to make the counter interesting — likely lower. | Once there is real data on what cohort size feels alive. |
| **O7** | **Last-one-standing tournament** (v1.1 concept). Runs until only 5 survivors remain, who split the entire pool. At 500 × $25 that is ~$1,980 each. **Escalation is mandatory, not flavour** — a disciplined field may never thin out, so the obligation must rise over time (25 reps wk 1, 30 wk 2, 35 wk 3…), which guarantees termination *and* compounds the drama. Needs a hard backstop (e.g. 90 days then tiebreak) and **the lawyer** — winner-take-most at ~79× reads far more like a prize competition than the pool model. | v1.1, after the ladder is proven. |
| **O8** | **Minimum cohort size of 8** — validate against real join rates in month 1. Too high strands members; too low makes prizes a coin flip. | Month 1. |
| **O9** | **Fee rate**, launched at 20%. Revisit at month 6 with real completion data. Raising is far easier than cutting prizes, so any move should be upward and well-signalled. | Month 6. |
| **O10** | **Decentralized keeper via Switchboard** — recommend v2.0 once the manual keeper has run reliably for 6+ months. | v2.0. |
| **O11** | **Insurance fund sizing** — 15% of fees at MVP; revisit after three months of operation. | Month 3. |
| **O12** | **Founder/team token allocation policy** — at MVP all fees pool in the multisig treasury. | v1.1. |

---

## 4. Metric thresholds worth keeping

These are the numbers that change the *design* if they are missed, not general product analytics.

| Signal | Threshold | What it means |
|---|---|---|
| Post-forfeit return rate | **< 35%** | The comeback path is failing. Triggers O3. |
| Free-cohort uptake after a forfeit | **< 50%** | The comeback offer is mistimed or badly worded; the paid return rate will follow it down. |
| Qualifier → Front Lever conversion | **< 25%** | Split by route: if free converts far worse than paid, the free carve-out is buying installs rather than members. If both are weak, the 7→21 day jump is the problem, not the price. |
| Paid-qualifier share of qualifying clears | **< 10%** | The paid qualifier is vestigial. Triggers O2. |
| Failure rate in the first 3 days of a cohort | **> 50%** | Too hard; reconsider stake floors and the qualification bar. |
| Zero-forfeiture cohorts recurring | any league | The league is too easy to be interesting; lengthen it or raise the obligation. |
| Concentrated whale stakes | top 10% > 50% of volume | Reconsider the single-stake limit; may need caps. |
| Anti-cheat false-positive rate | **> 2%** | Honest reps being rejected — the fastest way to lose trust. |

---

## 5. Archive — sections removed from the PRD on 2026-09-14

Kept verbatim. None of this is needed to build the app; some of it is needed to pitch it.


### 5.1 Changelog (v1.0 → v1.1, all revisions)

**2026-09-14 — major simplification: three leagues, one gate, and a free route in.** The ladder is now **Push-Up 7d (qualifier, 25 reps) → Front Lever 21d (25 reps) → Human Flag 28d (30 reps)**. Three changes, in order of how much they move the product:

1. **A free clear qualifies.** Clearing the one-week league unlocks Front Lever whether the cohort was free or staked (§5.1, §9.3 Op 5b). This is the largest funnel change in the document: the barrier between a curious installer and a paying member is now seven days of self-proof rather than a payment. The carve-out is deliberately narrow — a free qualifying clear grants **the unlock and nothing else**: no prize, no badge, no league streak. Contract-side it is one new field, `UserProgress.qualified`, and Front Lever's gate keys off that rather than `completions[PushUp]`.
2. **The 14-day Muscle-Up rung is deleted.** Front Lever now sits behind one gate instead of two. Median install-to-top-league collapses from ~90 days to ~4 weeks, far more members reach the top, and every member who would have been sitting in a 14-day cohort is now in a 21-day one, which is where concentration was always meant to go. *(The economic effect was expected to be a thinner Front Lever prize; the simulation run later the same day found the opposite — see the simulation entry above.)*
3. **Human Flag escalates to 30 reps** (§5.3) — the only non-duration difficulty lever in the product, stored per-cohort as `reps_per_day` and fixed at creation. Modelled honestly in §5.1: for the top league to out-pay Front Lever the harder bar would need to make a day ~1.5× more likely to be missed, and five extra push-ups plausibly buys 1.1–1.3×. **So 30 reps is a narrative and identity lever, not an economic one** — *"Front Lever is where the money is, Human Flag is where the glory is"* survives, and §18 item 18c says to revisit on observed miss rates, not on modelling.

**Qualification is one-time:** once qualified, the staked one-week cohort closes; the **free** one never does, and it is both the permanent habit lane and the **comeback path** offered the Monday after any forfeit (§5.1, §17.6). This matters more than before, because with no short paid league left, a member who forfeits Front Lever faces a $25 × 21-day re-entry — the largest churn risk in the design, tracked as post-forfeit return rate.

**Stake ladders revised the same day (final):** qualifier **free / $5 / $10 / $25** (top chip equals Front Lever's floor, so money can be held constant across the one league transition) · **Front Lever $25 / $50 / $75 / $100** · **Human Flag $50 / $100 / $150 / $200** — *Human Flag is exactly double Front Lever at every chip.* Both ladders are linear ($25 and $50 steps), so no step is bigger than the last. **The $25 chip is removed from Human Flag** because it was the only strictly dominated option in the product: $25 there paid ~$15.09 against ~$16.36 for the same $25 in Front Lever — seven more days, five more reps a day, and less money. Accessibility now lives where it works: $25 still buys a full three-week Front Lever run, and free cohorts cost nothing. The doubling is what makes the top league pay more in **absolute dollars at every rung** despite its lower percentage (60% vs 65%), which is the structural answer to the money-versus-glory split that copy alone could not fix. **Ceilings deliberately lowered** — Front Lever $200→$100, Human Flag $300→$200: costs some staked volume and caps the biggest payout at ~$121 rather than ~$181, buys a selector with even steps and a top chip a normal member can picture choosing. Raising a ceiling later is easy; lowering one after members anchor on it is not. Worked examples in §19 recomputed at the new rates.

**Simulation rebuilt and run (2026-09-14, end of day).** `pool_mechanics_sim.py` and `pool_mechanics_findings.md` were missing from the project folder and have been **written from scratch for the three-league ladder**; every economic figure in §5.1, §6.2, §17 and §19 now comes from them and is reproducible with one command. The simulation contradicted three assumptions: **(1) Front Lever pays 129.5% of stake, not the estimated 65%** — a finisher more than doubles their money, because 61% of a cohort does not finish; **(2) "clear rate" is two numbers** — 38.7% inside a Front Lever cohort but 49.8% for a member's first attempt, the gap being repeat entries by members who forfeited (§5.4 costs the stake, not the league level); **(3) the doubled chip ladder does not do its job** — Human Flag's stake is 2.00× but its prize percentage is 0.52×, so the two cancel to **1.04× in dollars**, and that inverts at churn ≥ 25%. Only a ~3× ladder would create a real gap, at the cost of pricing most members out — **rejected the same day (§18 item 18e): Human Flag keeps $50/$100/$150/$200 and is positioned as the summit, not the payday.** *"Front Lever is where the money is. Human Flag is where the glory is"* is now the design rather than an apology for it, which obliges every surface showing both leagues to show both prize percentages honestly. §18 items 18d and 18e both closed. **The dominant unmeasured parameter is churn-on-fail, which moves Front Lever's prize between 80% and 130% — it is the first thing to instrument in production.** Worked examples in §19 recomputed at simulated rates.

**Superseded within the same day:** two earlier 2026-09-14 drafts — one retiring only the staked Push-Up, then one retiring staked Muscle-Up as well ("two qualifiers, two homes") — are obsolete; this entry is the record.

**2026-09-13 — league structure and free access.** **Four leagues: 7 / 14 / 21 / 28 days.** The 21-day Front Lever rung was removed on simulation grounds and reinstated on stronger evidence — Squadletics, the incumbent with real traction, runs exactly 21 days. Accepted consequence: Front Lever pays the biggest prize (95% of stake) while Human Flag remains the status summit on a more expensive ladder ($75/$150/$300). *"Front Lever is where the money is. Human Flag is where the glory is."* · **Every league runs a free cohort alongside the staked one, every Monday** (§5.1) — same contest, same survivor counter, same leaderboard, but no prize, no badge and no league unlock. Free cohorts run *beside* staked ones rather than mixing unstaked seats into them, so they dilute no pool; paper entries were tried and dropped for exactly that reason. This also removes all geo special-casing: an unstaked cohort is legal everywhere (§4.3). Solo sessions remain as the third, lightest lane (§12.2). Stake ladders set deliberately high for a thin launch — Push-Up $5/$25/$50, Muscle-Up $25/$50/$100, Human Flag $50/$100/$200 — because with few members, stake size is the only lever that keeps a prize meaningful (a 12-member Human Flag cohort at $50 still pays $40 each; §5.1). Median prizes ~$9 / ~$37 / ~$64. **Three leagues, not four — the 21-day Front Lever rung removed** (§5.1): three rungs beat four on every axis simulated (42% vs 30% reach the top, 62 vs 90 day journey, $98 vs $78 median top prize, 72% vs 51% of stake volume in the top league). Last-one-standing tournament captured as a v1.1 concept (§18 item 20). **Every league starts every Monday** (§5.2) — cadence no longer varies by league, and minimum-size cancellation makes the effective cadence self-tuning at low volume, so the old "switch to weekly at 60 members" growth rule is deleted. **Pre-joining** (§5.6a): a member on track may line up the next league's cohort while the current one still runs, resolved automatically at settlement — this closes the between-leagues gap entirely. **The lobby** — joinable from the moment the previous cohort starts, free cancellation until the cutoff; join closes Sunday 00:00 local, cohorts begin Monday 00:00 local (§5.2, §9.3 Op 6). Every round still starts on a Monday, Push-Up included. Solo sessions promoted from optional gap-filler to Month 3 scope — they now cover the pre-start window, the highest-drop-off moment in the funnel (§12.2).

**2026-09-10 — mechanic simplification (major).** **Two streaks split — daily (free, off-chain, solo sessions count) vs league (paid, resets on forfeit)** (§12.2) · **Glory Mode removed — the free Push-Up league replaces it** (§10.5) · Push-Up is free, unlimited and never closes; clearing it only unlocks Muscle-Up (§5.1, §5.5) · League ladder replaces cohort tiers — Push-Up (free, 1wk) → Muscle-Up (2wk) → Front Lever (3wk) → Human Flag (4wk), gated progression, no demotion (§5.1, §5.5) · Fixed weekly cohorts replace rolling joins (§5.2) · **One prize pool per cohort; tier multipliers deleted entirely** (§7.1) · Single 20% fee on forfeitures replaces 2% entry + 15% slash (§6.2) · **Sweat Yield removed from scope** (§6.6) · Tournaments deferred (§15) · Plank deferred — MVP is one exercise (§5.3) · Contract inventory reduced to six programs; audit budget lowered to $25–45K (§9) · All worked examples recomputed (§19). Rationale and simulation results in `pool_mechanics_findings.md` and `league_ladder_design.md`.

**2026-05-31 — post-review revision.** Per-tier stake floors locked (§5.1) · Manual withdrawal replaces auto-payout (§5.7) · Fee structure changed to 2% entry + 15% slash (§6.2, §19) · Token burn allocation reassigned to Operations (§6.3) · Cold-start bonus extended to 6 months structured 3+3 (§6.5) · Off-chain checkpoints via Firebase replace 10-rep on-chain commits (§8.6) · No grace day, user-local day-end window (§5.3) · Calisthenics skill-badge progression added (§9.1) · Tick architecture rewritten to user-driven model (§9.3) · In-app Jupiter swap with referral fee (§10.4) · Glory Mode for geo-blocked users (§4.3, §10.4) · Duolingo-style streak counter (§12.2) · SMS Builder Grant strategy added (§17.7).

---

---

### 5.2 Vision, positioning and brand

### 2.1 Vision

A fitness app that uses the discipline of capital commitment to build the habit of physical commitment. Most fitness apps fail because they ask nothing of you. Holdout asks you to put your money where your push-ups are — and pays you when your discipline outlasts the crowd.

### 2.2 Positioning statement

> For Seeker owners who want their crypto and their physical discipline to compound together, Holdout is a stake-on-yourself fitness pool where your daily reps decide whether the pool pays you out or someone else. Unlike Squadletics, Holdout takes nothing at all from members who finish (they take 35% of every forfeiture either way), runs a gated league ladder you climb rather than one flat challenge, and is built natively for Solana Mobile from the ground up.

### 2.3 Brand

- **Name:** Holdout — *a holdout is the one who does not give in.* Renamed from Swolana on 2026-09-15 (D16).
- **Tagline:** "Put your money where your muscle is" — secondary: "Outlast the crowd" — short form: "Hold out. Get paid."
- **Voice:** Direct, athletic, no crypto jargon at the surface. Tone is closer to Apple Fitness or Nike Training Club than to a DEX.
- **Frame:** This is a *skill contest with a stake* — not gambling, not betting. Avoid the words "bet," "wager," "gamble," "luck," "odds" anywhere in product, marketing, or TOS. Use "stake," "challenge," "cohort," "survive," "earn," "payout."

### 2.4 What Holdout is not

- Not a gambling product (legal framing matters — see §13)
- Not a yield farm (no APY marketing on the stake itself)
- Not a fitness instruction app (we count reps; we don't teach form beyond pre-flight)
- Not a social network (sharing exists but isn't the primary loop)

---

---

### 5.3 Competitive landscape

*(Detail lives in `competitor_analysis_squadletics.md`.)*

### 3.1 Direct competitors

**Squadletics (Worldcoin mini-app)** — the proven incumbent. 1,200+ active participants per snapshot, ~16k WLD staked on top exercise (~$40K+ at typical WLD prices). Validates demand. See `competitor_analysis_squadletics.md` for the full teardown.

Key Squadletics weaknesses Holdout exploits:
1. **35% platform fee on slashed stakes** — punitive winner economics
2. **Pose-detection-only anti-cheat** — at least four known attack vectors
3. **Rolling daily-only challenges with no commitment tiers** — no way for ambitious users to opt into harder/bigger
4. **Per-entry cap and small pools per day** — limits big-stake users
5. **FIT engagement token** — adds complexity, unclear utility, distracts from the core loop
6. **"Squad" in the name but zero squad mechanic** — unfulfilled product promise
7. **Generic crypto-mini-app UI** — easy to beat with intentional design

### 3.2 Adjacent commitment-contract products

| Product | Model | Relevance |
|---|---|---|
| **DietBet** | $25-150 stake, 4-week weight loss bet, 10-25% house fee, lump-sum at end | Validates the stake-on-yourself model at scale; informs lump-sum settlement choice |
| **StickK** | User-defined commitment with anti-charity, no reward pool | Validates commitment device but lacks the win-pool draw |
| **HealthyWage** | 6-18 month weight bets, lump-sum payout | Validates long-horizon commitments |
| **Pact (RIP)** | Weekly settlement, $5-50/missed-day fines | Cautionary tale — tiny payouts ($0.30-$5) killed retention |
| **PoolTogether** | No-loss lottery, weekly draw, lump-sum | Validates "see the projected pot" mechanic |

### 3.3 The Holdout wedge in one sentence

Squadletics has the model, the demand signal, and the user base — and four exploitable flaws (fee rate, anti-cheat, no commitment tiers, generic UX). Holdout ships the same model with all four flaws fixed, on a platform (Seeker) where their Worldcoin distribution doesn't reach.

---

---

### 5.4 Phased rollout, months 1–9

> **Note:** written before the CLOCK IN hackathon deadline (9 Oct 2026) became the governing constraint. Treat as a post-hackathon plan, not a build order.

Solo founder + AI dev agents. Aggressive but achievable timeline assumes ruthless MVP scoping.

### Month 1 — Foundations

- Solana Anchor project scaffold
- HoldoutCore, CohortPool, FeeTreasury contract skeletons (§9.1)
- Mobile app scaffold (React Native + Expo)
- Wallet connection (Seeker mobile adapter)
- Design system token files (colors, typography, components)
- Pose detection module integration (MediaPipe Pose on-device test app)
- Initial TOS engagement with lawyer (let it run in background — 4-8 week turnaround)

### Month 2 — Core stake loop

- League registry + cohort lifecycle (`open_cohort`, min-size cancellation)
- Join flow contract logic (deposit, Jupiter swap, principal segregation)
- Per-user league unlock state (`UserProgress`)
- Mobile app stake-flow screens (1-5 above)
- Wallet transaction signing end-to-end
- Local testnet deployment + manual end-to-end test
- Pose detection: push-ups rep counter v1 (no audio, no checkpoints yet)
- Pre-flight environment check UI (5 pills, no validation logic yet)

### Month 3 — Anti-cheat + session loop

- Pose detection: push-ups full rules (audio, timing bounds, range of motion)
- Palm liveness check
- Pre-flight environment validation logic (lighting, framing, audio)
- **Firebase session-state schema + write/read SDK integration** (replaces the original 10-rep on-chain checkpoint)
- Session success / fail on-chain submit instructions
- Mobile app session flow screens (13-19)
- Duolingo-style daily streak counter on home screen + **solo session flow** (off-chain, no cohort — §12.2)

### Month 4 — Settlement + share

- Keeper bot for `tick_cohort`, `open_cohort`, `settle_cohort` (Node.js, per §9.3)
- **Live survivor counter + live prize calculation and home screen (§10.1a)** — the core engagement surface, not a nice-to-have
- **Lobby screen + `leave_cohort` refund path + join-cutoff enforcement** (§5.2, §9.3 Op 6)
- Lobby notification sequence: on join, last join day, Sunday evening, Monday morning
- Overnight-attrition push notification
- `settle_cohort` on-chain logic + **manual-claim "Claim $XX.XX" home-screen button**
- Auto-share-card generation immediately on successful claim
- LeagueBadgeMinter contract — one evolving cNFT per league, bronze/silver/gold/platinum at 1/3/6/12 completions, via Bubblegum metadata updates
- Share card generation (workout, recap, stats, milestone)
- In-app Jupiter swap sheet (with Holdout referral fee)
- Referral code embedding + deep link handling
- Leaderboard screens (per league, per live cohort)
- My Holdout screen
- Push notifications

### Month 4.5 — Audit + polish

- Submit contracts to auditor (Halborn/Neodyme/Ottersec)
- Fix audit findings
- TOS final review and incorporation
- Geo-block IP service integration
- Closed beta with 20-50 Seeker holders (friends + Solana community)
- Iterate on UX feedback

### Month 5 — MVP public launch

- Mainnet contract deployment with multisig admin
- Mobile app submission to Solana dApp Store
- **6-month structured cold-start bonus matching activated** (50% mo 1-3, 25% mo 4-6, ideally funded via SMS Builder Grant per §17.7)
- Free Push-Up league open in every geography (no stake, no prize, no gating) — **and it qualifies**, so the whole ladder is reachable without paying (§5.1)
- Launch announcement (X, Solana ecosystem channels, Seeker community)
- Active monitoring of pool health, anti-cheat false-positive rate, settlement integrity
- Daily standup with bug triage

### Months 6-7 — v1.1: breadth on a proven loop

Sequenced by what the launch data justifies, highest-confidence first:

- **Second exercise** (plank, then knee push-ups / full burpees) — only once a single league's cohorts routinely clear ~60 members, so splitting pools is safe
- **Weekly cohort starts per league**, replacing cadence-equals-length, under the same volume test (§5.2)
- Auto-convert prize to SKR on claim
- Weekly tournament contract (entry fee, fixed-payout structure, leaderboard) — deliberately after the base loop is proven, not alongside it
- Multi-entry power mode
- Sweat Yield reconsidered (§6.6) once the core contracts have a clean audit behind them

### Months 8-9 — v1.2: non-Seeker access + avatar system

- Civic Pass humanity proof integration
- Geographic expansion (broaden eligible jurisdictions)
- Non-Seeker Solana Mobile device support
- Avatar gamification system (cNFT badges, customization)
- Locked-stake trial mechanism (no-loss free entries using SKR yield)

---

---

### 5.5 Launch checklist

To be completed before MVP public launch (Month 5):

**Smart contracts**
- [ ] Full third-party audit complete; all critical and high findings fixed
- [ ] Mainnet deployment from multisig
- [ ] Multisig 3-of-5 set up with trusted signers (Illia + 2-4 advisors)
- [ ] Bug bounty program live (Immunefi or similar)
- [ ] Emergency pause tested end-to-end
- [ ] Keeper bot deployed with health monitoring + on-call rotation

**Mobile app**
- [ ] Solana dApp Store submission approved
- [ ] Performance acceptable on Seeker (cold start <3s, session start <2s)
- [ ] Pose detection false-positive rate <1% in internal testing
- [ ] Pose detection false-negative rate <5% in internal testing
- [ ] Crash rate <0.5% per session
- [ ] All copy reviewed for skill-contest framing

**Legal & compliance**
- [ ] TOS finalized by lawyer and live in-app
- [ ] Privacy policy finalized and live
- [ ] Geo-block enforcement tested for all listed jurisdictions
- [ ] Age attestation flow integrated
- [ ] Cookie / data handling disclosures (minimal — no video persistence makes this easier; Swelfie photos are composed on-device and never uploaded/hosted by Holdout — see §12.5)
- [ ] Swelfie consent sheet + EXIF/GPS stripping verified on export

**Treasury & ops**
- [ ] FeeTreasury connected to Seeker staking program
- [ ] **SMS Builder Grant application submitted (target: $140K — see §17.7)**
- [ ] Cold-start bonus matching budget funded (~$90K earmarked; sunset block heights set for mo 3 and mo 6 transitions)
- [ ] Firebase project provisioned, security rules audited, free-tier confirmed adequate for launch volume
- [ ] In-app Jupiter swap referral wallet configured + tested end-to-end
- [ ] Referral budget funded
- [ ] Support email + Discord live with response SLA defined
- [ ] Bug triage process documented

**Marketing**
- [ ] Launch announcement copy drafted (X thread, blog post, video)
- [ ] Solana ecosystem outreach: featured slots arranged where possible
- [ ] Seeker community AMA scheduled
- [ ] First 100 user activation plan ("get to 100 paid stakers in week 1")

**Internal**
- [ ] On-call rotation (just Illia at MVP — set expectations)
- [ ] Status page for outages
- [ ] Incident response runbook (escalation, communication, pause procedure)
- [ ] Daily metrics dashboard (active stakes, pool sizes, settlement counts, failure rates)

---

---

### 5.6 Success metrics and grant strategy

### 17.1 North star

**Weekly successfully-completed cohorts.** Tracks both demand (people staking) AND satisfaction (people completing rather than failing/abandoning).

### 17.2 Activation metrics (week 1 target)

**Free cohort → first staked cohort conversion is the headline funnel metric** (§18 item 18). Instrument from day one, split by (a) whether the member cleared or forfeited their free cohort and (b) the size of the prize the parallel staked cohort paid that week. The hypothesis is that a member who *cleared* a free cohort and then saw what the staked one next door paid is the single most convertible state in the product.


- 100 unique stakers
- 200 total cohort entries (some users stake both exercises)
- $5,000 USD-equivalent total staked
- $500 SKR equivalent permanently held in Fee Treasury

### 17.3 Engagement metrics (month 1 target)

- Daily active users / weekly active users > 60% (high — but expected because daily obligation forces it)
- Average sessions per active user per day: 1.0-1.2
- Session completion rate (started → completed within UTC day): >85%
- Cohort completion rate (joined → settled successfully): >35% in Front Lever, >50% in Human Flag (simulated baselines 38.7% / 54.6%, §5.1). Note these are **cohort** rates, not a member's first-attempt odds (49.8% / 47.4%) — the two must never be quoted interchangeably

### 17.4 Economic metrics (month 1 target)

- Total stake volume: $50,000 USD equivalent
- Fee Treasury accumulated: $5,000 SKR (permanent buy pressure)
- Average finisher payout: >190% of stake in Front Lever, >150% in Human Flag (simulated baselines 229% / 168% of stake returned, i.e. prizes of 129.5% / 67.7%). **The honest range for Front Lever is 80%–130% of stake** depending on churn-on-fail (§5.1); falling to the bottom of it means members are quitting after a loss rather than re-entering

### 17.5 Quality metrics (continuous)

- Anti-cheat false-positive rate: <2% (rejection of honest reps)
- Settlement accuracy: 100% (zero incorrect payouts)
- Smart contract uptime: 100% (Solana network dependent)
- Customer support tickets per active user per week: <0.1
- App crash rate: <0.5% of sessions

### 17.6 Anti-success signals to watch for

- High failure rate (>50%) within the first 3 days of a cohort → too hard; reconsider stake floors and the free-league qualification bar
- **Zero-forfeiture cohorts** (everyone finishes, so nobody wins a prize) recurring in any league → the league is too easy to be interesting; lengthen it or raise the obligation
- Low share-card share rate (<10% of completed sessions) → social loop weaker than designed; iterate copy/visuals
- High slippage events (>5% of stakes hit slippage limit) → liquidity issue with SKR-USDC pair; consider intermediate routing
- Concentrated whale stakes (top 10% of users contribute >50% of stake) → reconsider single-stake limit, may need stake caps
- **Post-forfeit return rate <35%** — the share of members who forfeit a Front Lever or Human Flag cohort and stake again within 4 weeks. **This is the single most important number the closure design produces** (§5.1): with both qualifiers retired, a member who fails has no short paid league to rebuild on, and their cheapest paid re-entry is $25 over 21 days. If it sags, the remedy is a shorter paid **comeback format** (§18 item 18a) — never reopening a qualifier
- **Free-cohort uptake after a forfeit <50%** — the comeback path is the free cohort of the highest closed rung, offered the Monday after a forfeit (§5.1). If members do not take it, the offer is mistimed or badly worded, and the paid return rate above will follow it down
- **Qualifier→Front Lever conversion <25%** — the headline funnel number now that a free clear qualifies. Split it by route: if **free** qualifiers convert far worse than staked ones, the free carve-out is buying installs rather than members and §18 item 18b is live. If both are weak, the 7d→21d jump is the problem, not the price
- **Paid-qualifier share <10% of qualifying clears** — if almost nobody stakes the one-week league once the free cohort unlocks the same thing, the paid qualifier is vestigial; delete it and make the qualifier free-only (§18 item 18b)

### 17.7 Solana Mobile Builder Grant strategy

Holdout maps closely to the published Solana Mobile Builder Grant priorities (mobile-first, Solana Mobile Stack integration, creative SKR integration, viral by default, activates Seeker users). Applying for grant funding is on the critical path because:

**Funding ask: $120K-$150K** structured as:

| Line item | Budget | Purpose |
|---|---|---|
| Cold-start matching pool | $90K | 6-month structured bonus matching per §6.5 |
| Smart contract audit | $25K | Halborn / Neodyme / Ottersec — required before mainnet |
| Initial keeper infrastructure | $10K | First-year hosting (Railway/Fly), monitoring (Datadog), redundancy |
| Genesis Token bonus airdrops to first 500 stakers | $15K | $30 SKR each — early-adopter incentive, separate from cold-start matching |
| **Total ask** | **$140K** | |

**Why this fits Solana Mobile's stated priorities:**
- ✓ **Mobile-first by necessity** — pose detection requires on-device camera + sensors; desktop wrapper doesn't make sense
- ✓ **Mobile Wallet Adapter + Seed Vault** — both core to our v1 architecture (Seeker quick-approve signing flow)
- ✓ **Creative SKR integration** — SKR is the *stake currency, pool currency, fee currency, and bonus currency*, not a bolt-on incentive. The product cannot function without SKR.
- ✓ **Viral or sticky by default** — commitment contracts have built-in social proof loops (the manual-claim moment → auto-share-card per §5.8 is engineered specifically for this)
- ✓ **Activates Seeker users** — Genesis Token humanity gate makes Seeker ownership *the* unlock to real-money cohorts

**Key narrative line for the application:** *"What we're not looking for are projects that just want SKR to distribute across an existing user base."* — Holdout is the inverse. We need SKR because the mechanic literally cannot work without a token-denominated pool. The product is built around SKR, not bolted to it.

**Application timeline:**
1. **This week:** review Airtable RFP list at airtable.com/appw7jfRXG6Joia2b for any fitness-adjacent open RFP (priority routing if matched)
2. **Week 2:** draft full application — project description, milestone breakdown, budget table, team-credibility framing, mockup screenshots
3. **Week 3:** submit via solanamobile.com/grants
4. **Weeks 4–10:** await response (4-6 week SLA per the published blog post)
5. **Week 10:** if approved, scope adjustments to incorporate grant funding; if not, fall back to self-funded smaller cold-start window

This puts grant decision in hand by ~July 2026, giving us 6 months of clarity before the Q1 2027 launch window.

---

---

### 5.7 Worked numerical examples (narrative versions)

*(The PRD keeps a compact test-vector table instead; these are the full scenarios.)*

**Model used throughout:** one fee of 20% on forfeitures (§6.2), no entry fee, one prize pool per cohort (§7.1), payout share proportional to stake among finishers. Cohort sizes and clear rates are the simulated values from `pool_mechanics_findings.md` and `league_ladder_design.md`. Cold-start bonus matching (§6.5) is shown separately where it applies, since it expires after month 6.

### 19.1 Qualifying for free — the whole on-ramp

Alice installs the app on a Wednesday. She runs **solo sessions** for five days to learn the flow, then joins the **free** Push-Up cohort on Monday and clears it — seven days, real crowd, real counter, no money, no card.

**She is now qualified.** Front Lever is unlocked. She earned no badge, no prize and no league streak — the free clear grants the unlock and nothing else (§5.1) — but the only thing standing between her and the real product was proving seven days to herself, and it cost nothing. She also saw what the staked cohort running alongside hers paid its finishers that same week, which is the most honest sales pitch the product can make: same league, same Monday, real number.

The following Sunday she skips straight to **Front Lever** at $25. Two weeks and one day later she has her first real payout. Total elapsed time from install to staking: **twelve days.**

### 19.1a The paid qualifier — training wheels

Ben is more cautious and wants to feel money on the line before committing three weeks. He joins the **staked** Push-Up cohort and puts in **$25**, the middle chip of the qualifier's three. His cohort has **40 members**. Over the week **13 forfeit** (~68% clear rate):

- Forfeited: 13 × $25 = **$325**
- Fee, 20%: **$65**
- Prize pool: **$260**
- Finishers: 27 members, total stake $675

Ben's share: $260 × ($25 / $675) = **$9.63**
His payout: $25 + $9.63 = **$34.63 USDC** — a 39% prize on a one-week commitment.

He also receives the **Push-Up** badge, which Alice does not get, and arrives at Monday with a 12-day streak. Both of them end the week with exactly the same thing that matters: Front Lever unlocked, and the staked qualifier now closed behind them. **That $9.63 and a badge is the entire difference the paid qualifier buys** — which is precisely why §18 item 18b asks whether it should survive month 2.

### 19.2 The step that replaced the 14-day rung

There is no worked example here any more, and the gap is the point. Under the four-league ladder a member's second paid cohort was a 14-day, $25 commitment. Now their second cohort — often their *first* paid one — is **21 days at $25 minimum**. That is the single largest step in the product, and the numbers on either side of it are worth stating together:

| | Old ladder | Now |
|---|---|---|
| Gates before the main league | 2 (7d, 14d) | **1 (7d, free or paid)** |
| Median install → top league | ~90 days | **~4 weeks** |
| Front Lever cohort clear rate | 47% (est.) | **38.7% (simulated)** |
| Front Lever prize | ~95% of stake (est.) | **129.5% of stake (simulated)** |

**The trade turned out better than the one we thought we were making.** The expectation was a thinner prize in exchange for a shorter road; the simulation found Front Lever's prize *rose* instead, because the dominant effect is not the removed gate but **repeat entries** — a forfeit costs the stake, not the league level (§5.4), so members who fail re-enter and make the cohort field harder-failing than a one-shot model predicts. A member who would have spent a fortnight in Muscle-Up is now in a Front Lever cohort, which is where the concentration goes, and the pot is fatter rather than thinner. §5.1 and `pool_mechanics_findings.md` carry the full reasoning.

### 19.3 Front Lever — 3 weeks, $100 stake

Carla reaches Front Lever by qualifying once — a single week. This is the league most members settle into, and the one carrying most of the product's staked volume. Her cohort has **150 members**; she stakes $100, the top chip.

**92 members miss a day** (38.7% cohort clear rate — the lowest anywhere in the product, and the reason its prize is the highest):

- Forfeited: 92 × $100 = **$9,200**
- Fee, 20%: **$1,840**
- Prize pool: **$7,360**
- Finishers: 58 members, total stake $5,800

Carla's share: $7,360 × ($100 / $5,800) = **$126.90**
Her payout: $100 + $126.90 = **$226.90 USDC** — a **127% prize. She more than doubled her stake.**

She taps "Claim $226.90", signs in her Seeker wallet, and the app generates the share card: *"I earned $126.90 with Holdout by doing 525 push-ups in 21 days."* Her Front Lever badge mints, and **Human Flag unlocks**.

**Carla's odds were not 38.7%.** That is the rate *inside her cohort*, inflated by members re-entering after previous forfeits. Her own first-attempt chance was closer to **50%** (§5.1). Both numbers are true; they answer different questions, and the app must never swap them.

### 19.4 Human Flag — 4 weeks, $150 stake

Dmitri goes on to Human Flag, the summit. His cohort has **90 members**; he stakes $150 — the third chip, and the exact double of Carla's $75 neighbours one league down.

Everyone here has cleared the qualifier and a full Front Lever, so this is the most disciplined population in the app — and even at 30 reps a day the cohort clear rate is the *highest* in the product at 54.6%. **41 members miss a day**:

- Forfeited: 41 × $150 = **$6,150**
- Fee, 20%: **$1,230**
- Prize pool: **$4,920**
- Finishers: 49 members, total stake $7,350

Dmitri's share: $4,920 × ($150 / $7,350) = **$100.41**
His payout: $150 + $100.41 = **$250.41 USDC** — a 67% prize.

**Note the comparison with Carla one rung below, because it is the design's sorest point.** He took 67% against her 127% — roughly half the percentage — on a stake 1.5× hers, at 30 reps a day instead of 25, for a week longer. In dollars: **$100.41 against her $126.90. He did more, risked more, and took less.** The doubled chip ladder was supposed to prevent exactly this and the simulation says it does not (§5.1); only a 3× ladder would, at the cost of pricing most members out. Human Flag's reward is therefore the badge, the summit, the hardest daily bar in the product — *"Front Lever is where the money is, Human Flag is where the glory is"* — and the app states it plainly rather than implying the top rung pays best. That is the ladder working as designed, not a mistake: the top league is the most filtered, so fewer members forfeit and the pool is thinner relative to the stakes in it. Raising the obligation to 30 narrows that gap but, on the modelling in §5.1, does not close it — it would take roughly a 1.5× increase in daily failure probability, and five extra push-ups almost certainly does not deliver that. So Human Flag's reward remains the badge, the summit, the harder daily bar and the largest absolute sums — *"Front Lever is where the money is, Human Flag is where the glory is"* (§5.1). The app states this plainly rather than implying the top rung pays best.

### 19.5 Failure case

Erik joins a Front Lever cohort and stakes $50. He completes days 1–5. On day 6 he falls asleep without doing his reps.

At his local midnight, `tick_cohort` marks him forfeited:

- $10 (20%) → Fee Treasury
- $40 → the cohort's prize pool, for the members still standing
- Erik receives nothing back
- A cracked-flame icon replaces his **league** streak — his **daily** streak is untouched and keeps running if he trains tomorrow (§12.2)

**He keeps his league level.** Front Lever stays unlocked, and Human Flag too if he had reached it. Only the stake is gone.

**And here is the moment the design is most exposed.** Erik's cheapest paid way back in is $25 over 21 days — the same three-week commitment that just beat him, because there is no short paid league left (§5.1). So the app does not offer him that first. It offers the **free one-week cohort opening Monday**: *"Your Front Lever run ended on day 6. Your 5-day streak is still alive. Take a free week — same crowd, same counter, no stake — and go again after."* Whether he takes it is the **post-forfeit return rate** and the **free-cohort uptake after a forfeit** in §17.6, and those two numbers are what decide whether a paid comeback format gets built (§18 item 18a).

### 19.6 Comparative scenario — Holdout versus Squadletics

**Scenario:** 1,000 users each stake $25 on a three-week challenge. 750 complete, 250 fail.

| | Holdout (20% on forfeitures) | Squadletics (35% slash) |
|---|---|---|
| Total staked | $25,000 | $25,000 |
| Forfeited | $6,250 | $6,250 |
| Platform fee | $1,250 | $2,187.50 |
| Prize pool | $5,000 | $4,062.50 |
| **Per-finisher payout** | **$31.67** | $30.42 |
| **Finisher return** | **+26.7%** | +21.7% |
| **Platform take on volume** | **5.0%** | 8.75% |

**Better for the user and cheaper to run at the same time.** The marketing line writes itself: *"Finish and we take nothing. They take 35% either way."*

### 19.7 In-app SKR swap revenue

A user without SKR taps the in-app swap sheet, routed through Jupiter with Holdout's referral wallet as fee recipient (~50 bps).

- $50 swap → **$0.25** referral revenue
- At 1,000 swaps/month → **$250/month**

Small, but it keeps users inside the funnel instead of bouncing to a CEX or Phantom mid-signup — which is worth far more than the fee itself.

---
