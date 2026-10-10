# Copy checklist

Distilled from `docs/design/README.md` (Content fundamentals, Naming). The README wins if they differ.
Run every user-facing string through this list, then `bash scripts/check-wording.sh`.

## Voice
- A calm coach who can also do maths: second person, present tense, short sentences, numbers before adjectives.
- Say what happened and what to do next. Never guilt-trip, never hype. No emoji (the flame is an icon).
- Sentence case for everything read (*Connect wallet*). `label` text is UPPERCASE, applied with `.uppercase()`.

## Words
| Use | Never |
| --- | --- |
| prize, prize pool, at stake, payout, stake, Holdout, the Daily 25, still standing, finish, claim | winnings, returns, yield, earnings |
| do your reps, 10 banked, 15 to go | bet, wager, gamble, odds, luck, jackpot, pot |
| `≈ $25` beside a tier | a dollar figure presented as what you receive |

Never in member copy: *cohort*, *league*, *challenge* (as an in-app noun), *survivors*, a Holdout numbered like *cohort 14*. A league's reserves are named by their Holdouts' level and length: *the reserve of Easy · 7-day Holdouts*.

## Naming
- **Holdout** — one word, capital H. Once per screen; after that the length and dates name it.
- A specific Holdout: level and length, then the date: **Hard · 21-day** · from 21 Sep; header label `HARD · 21-DAY · FROM 21 SEP · DAY 12 OF 21`.
- The one mention on a screen: *Join the 7-day Holdout*.
- No noun for the group: *still standing*, *everyone who started with you*, *3 dropped since yesterday*.
- *Hold out* (verb) only at the payoff: *You held out 21 days.* · *Held out 7 of 7*.
- Levels come first: **Easy · 7-day** (knees count) and **Hard · 21-day** (from the toes); header label `EASY · 7-DAY · FROM 12 OCT · DAY 3 OF 7`. Each level has one rule sentence, read the same everywhere; never the Hard sentence on an Easy screen. *Staked* is never a member's word.
- Tabs: **Today · Standing · Holdouts · Me**. The tagline never appears inside the app.

## Always, word for word
- *Test network, test token.* — first run and every staking screen.
- *If nobody drops out, you get exactly your stake back.* — before every join.
- *Stakes and payouts are in SKR; dollar values are approximate.* — wherever a USD label appears.
- *Some members in these Holdouts are scripted.* — first run; label each scripted member `◇ SCRIPTED`.
- *Miss one and you lose your stake. 80 % of it stays with members: 70 % to the prize pool and 10 % to the boost and comeback reserves of Easy · 7-day Holdouts. 20 % goes to the project, and so does the prize pool if nobody finishes.* — the stake sheet and the forfeit screen (*…so your stake of 1,250 SKR is lost. 80 % of it stays with members: 70 % to this Holdout's prize pool and 10 % …*), nowhere else, with the Holdout's own level and length and a no-break space before every % (`Format.lostStakeSplit`; decided 2026-10-10, reworded the same day).

## Moments (reuse these shapes)
- Obligation: *15 to go · 5h 12m left today* — reps and time remaining, never a clock time or date.
- Stopped part-way: *17 done — 10 banked, 7 not kept. Come back any time before 5h 12m runs out.*
- After a crash: *Your 10 banked reps are safe.*
- Overnight drop: *Three dropped since yesterday. Your share went up 17 SKR.*
- Forfeit: *You missed day 7.* → *Your 9-day streak is still alive.* → *Today's Daily 25* · *The next 7-day Holdout*.
- Claim: *You held out 21 days.* above **Claim 1,560 SKR**.
- Broken streak: *Your 14-day streak broke. Start a new one today.* (no save button)
- Coach cues: *More light · Get in view · Phone to 45° · Arms straight · Hold*; refused reps: *Go lower · Keep your body straight · Slow down*.
- Boost, on an offer: *Prize boost: up to 480 SKR from the reserve of Easy · 7-day Holdouts when it settles* (only while the reserve holds more than 0; "up to", never a promise; capped at 10 % of the pool). On a running Holdout: *+ about 1,200 SKR boost from the reserve of Hard · 21-day Holdouts if anyone finishes*.
- Comeback, on Today: *Finish the Easy · 7-day Holdout starting in 2d 4h and up to 250 SKR of the stake you lost comes back.*; on the claim, a **Comeback** row: *part of your last lost stake, back*.
- Holdout Lab (testers only): say *set*, *trace*, *video*; every switch label starts `TEST ·`; the consent sentence word for word: *only people who agreed in writing to be filmed*.
- A roster never shows a stake, prize or payout amount.
