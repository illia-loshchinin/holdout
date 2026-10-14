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

Never in member copy: *cohort*, *league*, *challenge* (as an in-app noun), *earn*, *survivors*, *Easy*, *Hard*, a Holdout numbered like *cohort 14*. A level's boost is named by its level and length: *boosts later Spark · 7-day prizes*.

## Naming
- **Holdout** — one word, capital H. Once per screen; after that the length and dates name it.
- A specific Holdout: level and length, then its state, never a calendar date or a clock time (constitution VI): **Habit · 21-day**; header label `HABIT · 21-DAY · DAY 12 OF 21`, lobby `SPARK · 7-DAY · STARTS IN 2D 4H`, finished `SPARK · 7-DAY · FINISHED` (`Format.headerLabel`, `lobbyHeaderLabel`, `finishedHeaderLabel`).
- The one mention on a screen: *Join the 7-day Holdout*.
- No noun for the group: *still standing*, *everyone who started with you*, *3 dropped since yesterday*.
- *Hold out* (verb) only at the payoff: *You held out 21 days.* · *Held out 7 of 7*.
- Levels come first: **Spark · 7-day** (*Light it up · knees allowed*) and **Habit · 21-day** (*Make it stick · toes only*); grammar *a Spark*, *a Habit*. Each level has one rule sentence, read the same everywhere (`Format.levelRule(c)`, from `Stakes`); never the Habit sentence on a Spark screen. *Staked*, *Easy* and *Hard* are the code's words and never a member's.
- Tabs: **Today · Holdouts · Me** (three, icon-only bar: the accessible names are the words). *Standing* was a fourth tab until 13 October and is gone — never name a tab or a screen *Standing*; *still standing* stays as a count. A Holdout's own screen (H4) is pushed over the tab that opened it. The tagline never appears inside the app.
- The page of everyone in a Holdout is **Members** (button *Members*, header `members`, *Reading the members…*) — never *The roster*. Only the code says roster.
- The day's button is **Start** (*Keep going* part-way; the tonal *Next Daily 25 in {t}* once done) — never *Start today's …*. The tour's last card ends on **Start** too.
- The tour is **6 cards**: the pager reads `HOW IT WORKS · N OF 6`, About's row says *6 cards*, the welcome says *Six cards, about a minute.* Never *5 cards* or *of 5*.
- Colour carries tone, always beside words. **Soft warm** (`SoftWarm`, #FFB08F) is for nudges and status — the devnet bar's dot and ring, the wrong-network notice, the sitting's last-10-minutes time line. **Warning** (#FF623A) is for losses and errors — a forfeit, *Disconnect wallet*, *Leave*, a failed send. A refused-rep hint (*Go lower*) is never Warning.

## Always, word for word
- *Test network, test token.* — first run and every staking screen.
- *If nobody drops out, you get exactly your stake back.* — before every join.
- *Stakes and payouts are in SKR; dollar values are approximate.* — wherever a USD label appears.
- *Some members in these Holdouts are scripted.* — first run; label each scripted member `◇ SCRIPTED`.
- *Miss a day and you lose your stake.* — the stake sheet, in one line with a *How it works* action beside it (opens the tour at card 2 and returns to the sheet) and *Full refund until joining closes.* under it; the sheet no longer says the 70 / 10 / 20 split. The forfeit screen says *It goes 70 % to this Holdout's prize pool, 10 % set aside for your comeback, 20 % to the project. If nobody finishes, the pool boosts later Habit · 21-day prizes.* under a **Stake lost** row (`Format.lostStakeSplitForfeit(c, name)`), and tour card 4 (*Where a lost stake goes*) shows the split. The 10 % is the member's own comeback, never "boosts and comebacks". Nowhere else, with the Holdout's own level and length as `name` (a Hackathon Holdout names itself: *…boosts later Hackathon Holdout prizes*) and a no-break space before every %.

## Moments (reuse these shapes)
- Obligation: *15 to go · 5h 12m left today* — reps and time remaining, never a clock time or date.
- Stopped part-way: *17 done — 10 banked, 7 not kept. Come back any time before 5h 12m runs out.*
- After a crash: *Your 10 banked reps are safe.*
- Overnight drop: *Three dropped since yesterday. Your share went up 17 SKR.*
- Forfeit: *You missed day 7.* → *Your 9-day streak is still alive.* → *Today's Daily 25* · *The next {N}-day Holdout*, the same league as the one lost (a 21-day forfeit offers the next 21-day Holdout, never the 7-day one; US-4, H37).
- Claim: *You held out 21 days.* above **Claim 1,560 SKR**.
- Broken streak: *Your 14-day streak broke. Start a new one today.* (no save button)
- Coach cues: *More light · Get in view · Phone to 45° · Arms straight · Hold*; refused reps: *Go lower · Keep your body straight · Slow down*.
- Boost, on an offer: *Prize boost: up to 480 SKR* (`Format.boostLine`; only while the reserve holds more than 0; "up to", never a promise) — on the stake sheet as its own line; on the first-open card's next-round block *up to 480 boost*. Never *+480 boost* or any *+{n} boost* on an offer. On a running Holdout: *+ about 1,200 SKR boost if anyone finishes* (`Format.runningBoostLine`).
- Devnet not answering — one bar under the header (`DevnetBar`), never a red box and never *offline*: *Devnet isn't answering* (after a failed retry *Devnet still isn't answering*; a local build says *The local validator isn't answering*), a sub line for the screen — Today *The Daily 25 still works.* / *Your reps still count on this phone.*; Holdouts, a Holdout's screen and the forfeit *Showing what it said 4m ago.* — and the action **Retry** (*Checking…* while it reads). No screen says *can't be reached right now* or has a *Check again* button.
- Comeback (exact and guaranteed, `min(10 % of the lost stake, the new chip)` — never "up to"; good for the next round of the same league only): on Today *Get back 125 SKR of your lost stake: finish the Spark · 7-day starting in 2d 4h. Joining closes in 2d 3h.* (`Format.comebackToday`; a round already started: *…finish the Spark · 7-day that started today. Joining closes in …*); on the forfeit screen the headline *Win back 125 SKR* (`Format.winBack`) over *Finish the next Habit · 21-day and 125 SKR comes back.* (`Format.comebackForfeit`) and *Starts in 2d 4h · joining closes in 2d 3h*; on the stake sheet *Finish it and 125 SKR of the stake you lost comes back.* (`StakeText.comeback`); on H0's Out row *Win back 125 SKR*, or *Comeback joined* once a place is held; on the claim, a **Comeback** row: *part of your last lost stake, back*. The boost stays "up to": a comeback nobody makes good and a pool nobody won feed it.
- Holdout Lab (testers only): say *set*, *trace*, *video*; every switch label starts `TEST ·`; the consent sentence word for word: *only people who agreed in writing to be filmed*.
- The Members page never shows a stake, prize or payout amount.
