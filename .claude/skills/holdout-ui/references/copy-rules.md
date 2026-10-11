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

Never in member copy: *cohort*, *league*, *challenge* (as an in-app noun), *earn*, *survivors*, *Easy*, *Hard*, a Holdout numbered like *cohort 14*. A level's reserves are named by its level and length: *Spark · 7-day boosts and comebacks*.

## Naming
- **Holdout** — one word, capital H. Once per screen; after that the length and dates name it.
- A specific Holdout: level and length, then its state, never a calendar date or a clock time (constitution VI): **Habit · 21-day**; header label `HABIT · 21-DAY · DAY 12 OF 21`, lobby `SPARK · 7-DAY · STARTS IN 2D 4H`, finished `SPARK · 7-DAY · FINISHED` (`Format.headerLabel`, `lobbyHeaderLabel`, `finishedHeaderLabel`).
- The one mention on a screen: *Join the 7-day Holdout*.
- No noun for the group: *still standing*, *everyone who started with you*, *3 dropped since yesterday*.
- *Hold out* (verb) only at the payoff: *You held out 21 days.* · *Held out 7 of 7*.
- Levels come first: **Spark · 7-day** (*Light it up · knees allowed*) and **Habit · 21-day** (*Make it stick · toes only*); grammar *a Spark*, *a Habit*. Each level has one rule sentence, read the same everywhere (`Format.levelRule(c)`, from `Stakes`); never the Habit sentence on a Spark screen. *Staked*, *Easy* and *Hard* are the code's words and never a member's.
- Tabs: **Today · Standing · Holdouts · Me**. The tagline never appears inside the app.

## Always, word for word
- *Test network, test token.* — first run and every staking screen.
- *If nobody drops out, you get exactly your stake back.* — before every join.
- *Stakes and payouts are in SKR; dollar values are approximate.* — wherever a USD label appears.
- *Some members in these Holdouts are scripted.* — first run; label each scripted member `◇ SCRIPTED`.
- *Miss a day and you lose your stake: 70 % to the prize pool, 10 % to Spark · 7-day boosts and comebacks, 20 % to the project. If nobody finishes, the pool goes to the project too.* — the stake sheet (`Format.lostStakeSplit(c)`); the forfeit screen says *It goes 70 % to this Holdout's prize pool, 10 % to Habit · 21-day boosts and comebacks, 20 % to the project. If nobody finishes, the pool goes to the project too.* under a **Stake lost** row (`Format.lostStakeSplitForfeit(c)`). Nowhere else, with the Holdout's own level and length and a no-break space before every %.

## Moments (reuse these shapes)
- Obligation: *15 to go · 5h 12m left today* — reps and time remaining, never a clock time or date.
- Stopped part-way: *17 done — 10 banked, 7 not kept. Come back any time before 5h 12m runs out.*
- After a crash: *Your 10 banked reps are safe.*
- Overnight drop: *Three dropped since yesterday. Your share went up 17 SKR.*
- Forfeit: *You missed day 7.* → *Your 9-day streak is still alive.* → *Today's Daily 25* · *The next {N}-day Holdout*, the same league as the one lost (a 21-day forfeit offers the next 21-day Holdout, never the 7-day one; US-4, H37).
- Claim: *You held out 21 days.* above **Claim 1,560 SKR**.
- Broken streak: *Your 14-day streak broke. Start a new one today.* (no save button)
- Coach cues: *More light · Get in view · Phone to 45° · Arms straight · Hold*; refused reps: *Go lower · Keep your body straight · Slow down*.
- Boost, on an offer: *Prize boost: up to 480 SKR* (`Format.boostLine`; only while the reserve holds more than 0; "up to", never a promise). On a running Holdout: *+ about 1,200 SKR boost if anyone finishes* (`Format.runningBoostLine`).
- Comeback, on Today: *Get back up to 250 SKR of your lost stake: finish the Spark · 7-day starting in 2d 4h.* (`Format.comebackToday`); on the forfeit screen *Finish a Habit · 21-day starting within 14 days: up to 250 SKR comes back.* (`Format.comebackForfeit`); on the claim, a **Comeback** row: *part of your last lost stake, back*.
- Holdout Lab (testers only): say *set*, *trace*, *video*; every switch label starts `TEST ·`; the consent sentence word for word: *only people who agreed in writing to be filmed*.
- A roster never shows a stake, prize or payout amount.
