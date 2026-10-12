# Holdout design book

> Mirrored from the Holdout design system artifact. **Do not edit here** — change the artifact, then re-sync (see `SOURCE.md`). Compose rules for building screens live in the `holdout-ui` skill (`.claude/skills/holdout-ui/`).

Holdout is a Solana Mobile app where you stake on yourself. Anyone can do the free **Daily 25**, 25 push-ups counted on the phone, and build a streak. On top of that sit **Holdouts**: 7-day and 21-day runs with a stake in SKR. Miss a day and your stake goes into the prize pool. Finish, and you get your stake back plus a share of the pool.

The look is **native on Seeker**. It should feel like it shipped with the device: a black canvas, quiet chrome, and big numbers that carry the screen. Every colour is sampled from Solana's own palette, so nothing looks borrowed. Fitness gives the product its rhythm (reps, blocks of 10, a streak flame). Crypto gives it its stakes (SKR, a prize pool, a claim you sign). Wellbeing gives it its tone: calm, honest, never shouting.

> Stake. Do your reps every day.
> Miss a day and your stake goes into the prize pool.
> Finish and you get your stake back plus a share of the pool.

## Principles

1. **The crowd is the product.** Everyone in a Holdout starts and finishes together, so the group visibly shrinks. Make the survivor counter (`189 / 312` in `counter`) and the prize the largest things on screen.
2. **Numbers carry the screen.** Pair one big tabular number with a `label` caption under it (the Stat block). Leave chrome quiet and space generous.
3. **One interactive colour.** `accent` means "you can act on this" or "this is in progress". Everything else is white, grey, or a state colour.
4. **Celebrate twice, not constantly.** The Solana gradient (`gradient-start` → `gradient-end`) plays only on *day complete* and *claim success*. Never use it as a background, a button, or a logo treatment.
5. **Honest before exciting.** This is a contest of physical consistency, not a game of chance. Every word and number on screen must be true in SKR.

## Content fundamentals

**Voice:** a calm coach who can also do maths. Second person ("you", "your stake"), present tense, short sentences, and numbers before adjectives. Say what happened and what to do next. Never guilt-trip, never hype.

**Short, and it sells (decided 2026-10-11).** Nobody reads a paragraph on a phone. Every screen is checked against these rules before it ships:

1. **Benefit first, mechanics second.** *"Free, every day: get stronger at your own pace. 25 push-ups, counted by the camera."*
2. **One idea per line.** A line break beats a comma. Two short sentences beat one long one.
3. **Never restate the title or the button.** Title *The deadline passed*, body *Your run in this Holdout has ended.* — not the deadline again.
4. **Show a number once; let the button act.** *15 to go* above **Keep going**, not **Do 15 more**.
5. **True in the worst case.** *Up to* on every boost, *exactly your stake back* if nobody drops out, dollars only as `≈` beside a tier.
6. **One name for each thing, everywhere.** *Spark · 7-day*, *stake back*, *Waiting for your wallet…*, and each level's rule sentence word for word.
7. **Say what happened, then what to do, with no jargon.** *Nothing was sent. Your 1,412 SKR waits here.*
8. **No dead ends; offer the next good action.** *Until Monday, just keep your streak.*
9. **Sentence case, even when a line would start with a lowercase brand word.** *The network didn't confirm it.*

Most lines fit in 12 words; a body under a title is one or two of them.

**Casing:** sentence case for everything a person reads (*Connect wallet*, *Join the 7-day Holdout*). `label` text is uppercase, applied at the call site (`STILL STANDING`, `HABIT · 21-DAY · FROM 21 SEP · DAY 12 OF 21`). What to call things is under **Naming**.

**Words to use / never use** (constitution XIII):

| Use | Never |
| --- | --- |
| prize, prize pool, at stake, payout, stake, Holdout, the Daily 25, still standing, finish, claim | winnings, returns, yield, earnings |
| "do your reps", "10 banked", "15 to go" | bet, wager, gamble, odds, luck, jackpot, pot |
| `≈ $25` beside a tier | a dollar figure presented as what you will receive |

**Always stated, word for word:**

- *"Test network, test token."* on first run and on every staking screen.
- *"If nobody drops out, you get exactly your stake back."* before every join.
- *"Dollar values are approximate."* wherever a USD label appears (stakes and payouts are always in SKR).
- *"Some members in these Holdouts are scripted."* on first run, with a label on each scripted member.
- *"Miss a day and you lose your stake: 70 % to the prize pool, 10 % to Spark · 7-day boosts and comebacks, 20 % to the project. If nobody finishes, the pool goes to the project too."* wherever a forfeit split shows: the stake sheet and the forfeit screen (*"It goes 70 % to this Holdout's prize pool, 10 % to Habit · 21-day boosts and comebacks, 20 % to the project. If nobody finishes, the pool goes to the project too."*, under a **Stake lost** row), nowhere else. The level and length are the Holdout's own, and a no-break space keeps every % on its number's line. Decided 2026-10-10, shortened 2026-10-11 with every fact kept; the rule itself is 70 / 5 / 5 / 10 / 10, and the 10 % comes back as the boost and comeback lines below.

**Real copy, by moment:**

- Obligation: *"15 to go · 5h 12m left today"*. Always reps and time remaining, never a clock time or date.
- Stopped part-way: *"17 done — 10 banked, 7 not kept. Come back any time before 5h 12m runs out."*
- After a crash: *"Your 10 banked reps are safe."*
- The overnight drop: *"Three dropped since yesterday. Your share went up 17 SKR."*
- First open: **The Daily 25** — *free, every day: get stronger at your own pace. / 25 push-ups, counted by the camera. Your streak grows.* **A Holdout** — *stake SKR and run 7 or 21 days with others. / Miss a day: your stake joins the prize pool. / Finish: take it back plus a share of the pool.*
- Forfeit: *"You missed day 7."* then *"It didn't reach the chain in time. Your run has ended."*, *"Your 9-day streak is still alive."* and two ways back: *Today's Daily 25* · *The next Habit · 21-day*.
- Claim: *"You held out 21 days."* above **Claim 1,560 SKR**; the rows are **Stake back** and **Share of the prize pool**.
- A transaction that failed or was declined: *"The network didn't confirm it."* / *"Nothing was sent."*, then what is still safe: *"Your 1,412 SKR waits here."*
- Broken streak: *"Your 14-day streak broke. Start a new one today."* There is no save button because there is no grace day.
- Streak milestone (decided 2026-10-12), on the day-complete screen and, for the rest of that day, under the streak on Today: the headline *"{N}-day streak"* with the flame in its milestone state, then one line. Milestones are 7, 14, 21, 28, 30, 50, 60, 90, 100, every further 100 days and 365. Frozen days do not count, as for the streak itself. It is part of the page; nothing to dismiss, nothing unlocked.
  - 7: *"A week, every day. That's how habits start."* · 14: *"Two weeks in. You're making this yours."* · 21: *"21 days. That's a habit now."* · 28: *"Four weeks without a gap."* · 30: *"A month of showing up."* · 50: *"50 days. Most people never get here."* · 60: *"Two months strong."* · 90: *"Three months. This is who you are now."* · 100: *"100 days. Triple digits."* · 365: *"A year, every day."*
  - Every other hundred (200, 300, …): *"{N} days. Still here, still going."*
- Pre-flight tips (decided 2026-10-12): once the checks have stayed short of green for a while, a text button under the fix sentence reads *"Still not green? Tips"*. It opens a Sheet titled *"Getting the checks green"*: one row per check in the pills' order, each the pill's label and that check's own fix sentence (no new per-check copy), a check that is red right now marked in `warning` and *"Not green yet"* for screen readers. It ends in **Open the set-up guide** and **Back to the checks**. It never opens by itself.
- New phone (ES-3a): *"No banked reps on this device today. 25 to go."* on Today with no Holdout; on a running Holdout the number is that day's target.
- The boost, on an offer: *"Prize boost: up to 480 SKR"*, in `success`, only while that level's reserve holds more than 0; the card already names the level, so the line doesn't. "Up to": the boost is capped at 10 % of the pool and paid only if someone forfeits, so it is never a promised amount. On a running Holdout: *"+ about 1,200 SKR boost if anyone finishes"*. Where a reserve has to be named, it is by its Holdouts' level and length, never "league". There is no *"+N SKR added to the prize pool"* line: a pool nobody finishes goes to the project, so nothing carries over, and an open Holdout has no pool yet.
- The comeback, on Today while a lost stake's 14-day window is open: *"Get back up to 250 SKR of your lost stake: finish the Spark · 7-day starting in 2d 4h."* On the forfeit screen: *"Finish a Habit · 21-day starting within 14 days: up to 250 SKR comes back."* On the claim, its own row: **Comeback** `250 SKR` *part of your last lost stake, back*.
- Coach cues, shown large and spoken: *More light · Get in view · Phone to 45° · Arms straight · Hold*. Refused reps: *Go lower · Keep your body straight · Slow down*.

**Emoji:** none in the interface. The streak flame is an icon, not 🔥.

## Naming

**The name.** In text it is always **Holdout**: one word, capital H only. Never *Hold Out*, *HoldOut* or *HOLD OUT* with a space. *Holdout* is the noun (the run, and a person who refuses to give in); *hold out* is the verb, kept for the payoff. It is the same pattern as *workout* and *work out*.

Each word has one job. **Holdout** names the thing you join, **challenge** names the category people search for, and **cohort** never leaves the code.

| Word | Its job | Where it appears |
| --- | --- | --- |
| **Holdout** | The product, and a staked 7-day or 21-day run | Everywhere a member reads. Ownable: the product name is the thing you do, and *to hold out* is exactly the behaviour. |
| **The Daily 25** | The free tier: 25 push-ups, every day, on your own | Everywhere a member reads. The number is the promise. |
| **Challenge** | The category | Marketing only: store listing, website, ads (*"the push-up challenge you stake on"*). Never a noun or button in the app. |
| **Cohort** | The contract's word (`Cohort`, `LeagueConfig`) | Code, docs, the pitch deck's mechanics slide, and the disclosed `TEST COHORT` badge. Never in member copy. |

**By surface:**

| Surface | Write |
| --- | --- |
| Brand tagline | ***Be the holdout. Stake on yourself.*** |
| Store title and subtitle | *Holdout — the push-up challenge you stake on* |
| Website, social, ads | Hook with *challenge*, name with *Holdout*: *"Take the 7-day push-up challenge. Join a Holdout, stake on yourself, finish and share the pool with everyone still standing."* |
| Onboarding tour | *25 push-ups a day. Free, counted by your phone.* · *Stake SKR with everyone who starts on Monday. Spark · 7-day: knees allowed. Habit · 21-day: toes only.* |
| In the app | "Holdout" once per screen; after that, the length and dates name it |
| Pitch deck, for judges | Holdout, with *cohort* explained once: *"everyone in a Holdout starts and finishes together"* |

**Naming a specific Holdout.** A new 7-day and a new 21-day start every Monday, so the length alone is not unique. Name it by its length and start date; the number of days is the label.

| Where | Write | Not |
| --- | --- | --- |
| Cards under a *Holdouts* heading | **Habit · 21-day** · from 21 Sep | 21-day Holdout |
| A finished one | **Spark · 7-day** · 28 Sep – 5 Oct | 7-day Holdout |
| Header labels | `HABIT · 21-DAY · FROM 21 SEP · DAY 12 OF 21` | `21-DAY · COHORT 14` |
| The one mention on a screen | *Join the Spark · 7-day Holdout* | *Join the next 7-day cohort* |
| Sentences | *your run in this Holdout has ended* | *your cohort* |

**Levels (decided 2026-10-10, renamed 2026-10-11 to Spark / Habit).** A Holdout's level is its form, and it comes first: **Spark · 7-day**, where push-ups from the knees are allowed but never encouraged, and **Habit · 21-day**, from the toes only. The names tell one story, a spark that becomes a habit, and never judge the member: no *Easy* or *Hard* in front of a member (owner's call, 11 Oct; *Sprint / Marathon* was tried the same day and dropped). Taglines: Spark *Light it up · knees allowed*, Habit *Make it stick · toes only*; the stake sheet says *Light it up: 25 push-ups a day for 7 days* / *Make it stick: 25 push-ups a day for 21 days*; a finished Spark's claim opens *Spark lit: you held out 7 days.* and offers **Make it a Habit · 21-day**. Grammar: *a Spark*, *a Habit*, *20 per Spark*. Each level carries one rule sentence, read the same everywhere it appears: Spark, *"At 45° only. Aim for full push-ups from your toes; push-ups from the knees still count."* (inform, never encourage: the toes come first, knees are a permitted fallback; captions say *knees allowed*, decided 2026-10-11); Habit, *"At 45° only: phone on the floor ahead of your shoulder. Full push-ups from your toes — push-ups from the knees don't count."* A Spark screen never shows the Habit sentence. *Staked* is the code's word for the Habit rules and never a member's; the code's `Easy`/`Hard` enum names stay internal.

**The people in it.** There is no noun for the group. Say *still standing*, *everyone who started with you*, *3 dropped since yesterday*. The tab that shows them is **Standing**.

**The verb, at the payoff.** *Hold out* is the brand's verb and belongs to the moments a member earns it: the claim (*"You held out 21 days."*) and a finished card (*"Held out 7 of 7"*). It describes what you did, never money.

**The tagline: *Be the holdout. Stake on yourself.*** The first line plays on the plain-English meaning, a person who refuses to give in, so it speaks to the member, not the run. Use it where the brand introduces itself: the store listing, the website hero, ads, the pitch deck's title slide, and social posts. The second line says what you actually do. Keep both lines together, in this order. Where space is tight, such as an app icon caption or a social handle bio, *Be the holdout.* may stand alone. **Never inside the app.** There, *a Holdout* always means the run, and *holdout* is never a word for a person.

**The four tabs:** **Today** (the one thing to do today) · **Standing** (your Holdout's crowd: still standing, how it thinned, the roster, and the leaderboards) · **Holdouts** (yours: joined, running, finished, claims waiting) · **Me** (wallet, balances, the faucet, streak, About).

**Never in member copy:** *cohort*, *league*, *challenge* as an in-app noun, *survivors*, or a Holdout numbered like *cohort 14*.

## Visual foundations

**Colour.** There is one theme, dark, and it is fixed. Material You dynamic colour is off, because the palette is the identity.

- Ground: `canvas` (pure black). Raise content onto `surface-1` (cards and dialogs), `surface-2` (nav and sheets) and `surface-3` (chips, pressed states, inputs).
- Text: `text-primary` for numbers and copy, `text-secondary` for captions and supporting lines, `text-tertiary` for labels and metadata. All three hold 4.5:1 or better on every surface.
- `accent` (Tide Light) is the only interactive colour. `success` (Solana Green) means still standing, done, or confirmed. `warning` (Coral) means forfeit, last 10 minutes, or leave/stop. `celebration` (Solana Purple, `#9945FF`) is decoration only: the gradient and the personal-best flame. When purple has to be read, such as a **PB** label, set it in `celebration-text`, the same hue lightened to hold 5.7:1 or better on every surface.
- Status is never colour alone. Pair `success` and `warning` with a word (*still standing*, *forfeited*) or an icon. They also differ in lightness by 2:1 (14:1 vs 7.1:1 on `canvas`).
- Spare hues (`palette-sky`, `palette-charge`, `palette-kelp`, `palette-beach`): use at most one per screen, and only when a screen truly needs a second hue.

**Type.** Use `counter` and `display` (Roboto Flex 300) for the numbers people come back for, and `amount` (JetBrains Mono) for SKR, countdowns, and members' Seeker IDs and wallet addresses. Everything else is Roboto: `headline`, `title`, `body`, `label`. Set tabular figures (`font-feature-settings: "tnum"`) everywhere a number changes, so counters and timers never jitter. Solana's ABC Diatype faces are licensed to Solana and are never used.

**Spacing and layout.** Use a 4px base. `space-gutter` sets the screen edges, `space-card` pads a card, `space-items` separates cards and rows, and `space-section` separates sections. Screens are one column, centred on the hero number, with no app bar. Touch targets are at least `size-touch`; buttons are `size-button` tall.

**Shape.** Cards use `radius-card`. Sheets use `radius-sheet` on the top corners only. Chips use `radius-chip`. Buttons and the active nav pill use `radius-pill`. Everything soft, nothing sharp: the body of the app is rounded, like the device.

**Depth.** None. There are no shadows anywhere on black. A surface step plus a `hairline` border does all the separating. (`hairline` is 1.3:1 on `surface-1`, below the 3:1 floor for control borders. It is kept exact from the source, so a bordered control always carries a white label too.)

**Focus.** A solid 2px `accent` ring, offset 2px. It holds at least 11:1 on every surface.

**Motion.** Use 200ms for standard changes and 300ms for anything entering the screen. The survivor counter **rolls** to its new value rather than cutting, because it is the product's one dramatic number. Day complete and claim each play a single gradient sweep of about 900ms, once. No confetti and no crypto stickers.

**Imagery.** The only imagery is the member's own camera. During a sitting it is full-bleed, with a `scrim` band at the top and bottom and the pose skeleton in `accent` at 80%. Text over the camera always sits on the scrim.

## Iconography

The source names no icon set, and the app ships none yet. This is a recommendation, flagged as such: use **Material Symbols Rounded**, outlined at rest and filled when active, sized 24px. Its rounded terminals match `radius-pill` and the Seeker system UI. Icons are white (`text-primary`) or `text-tertiary` when inactive. In the active nav pill they turn `on-light` on `inverse-surface`.

The product has three of its own marks, all built from simple geometry rather than pictures:

- **The bank ring**: a 4px arc split into one segment per block of 10.
- **The streak flame**: active, extended (flips up in `success`), broken (cracked, `text-tertiary`), personal best (`celebration` with a **PB** mark).
- **The scripted-member diamond (◇)**: always beside a `SCRIPTED` label chip.

**Logo:** the mark and the name. The mark is the app icon's crowd with its H: the same 25 members, the same seven in `accent`, so the icon and the logo are one symbol at two sizes. Beside it sits **Holdout** in Roboto Flex 300 (the `counter` face), title case, `text-primary`, tracking −2%, converted to outlines so it never depends on an installed font. The files are in the **Logo** asset group.

- **Lockups:** horizontal is the default (website header, deck, store banner, social). Stacked is for square spaces (avatar, title slide). The wordmark alone is for places where the icon already sits beside it. The mark alone is the icon without its tile.
- **Clear space:** two grid steps (the distance between two members' centres) on every side. The files already include it.
- **Minimum size:** horizontal 120px wide; the mark with its crowd 32px. Below 32px use the H alone, as the status-bar icon does.
- **Ground:** the colour versions live only on black (`canvas`). On a light page someone else controls (a hackathon listing, a partner site, print), use the one-ink black version: the bold H alone, the themed-icon geometry, with the name. Never put the colour version on white.
- **Never:** recolour the members or the H, stretch or rotate, add a gradient, glow or shadow, re-set the name in another font, rearrange the lockup, or put it on a photo without its black ground.

Inside the app the name is live text, not the outlined file: `ui/Wordmark.kt` sets *Holdout* in the `counter` face at weight 300, tracked −2%, and follows the theme's face. The outlined files are for everything outside the app.

Never borrow the Solana logomark as Holdout's own. It may appear only where Solana's brand guidelines allow, and nothing may imply an official Solana or Seeker product.

**App icon:** the crowd, and the H it leaves standing. A 5×5 grid of members on `canvas`, every dot the same size. Seven of them, in `accent`, form an H joined by three strokes: Holdout's initial, made of the people who held out. It is the product in one picture: everyone starts together, some drop out, and the ones still standing make the mark. This is the one place `accent` is decoration rather than "you can act on this": it marks the brand, never a control. A crowd of the same dots runs down the cover.

- **Geometry:** on the 108dp icon grid, 25 members of radius 3.4, centres from (32, 32) to (76, 76), 11 apart, so everything sits inside the 66dp safe zone. The H is (row, col) (1,1) (2,1) (3,1) (2,2) (1,3) (2,3) (3,3); its strokes are 2.6 wide with round caps: 43,43→43,65 · 65,43→65,65 · 43,54→65,54. Members are `text-primary`, the H is `accent`, faded members sit at 12% opacity.
- **Launcher:** an Android adaptive icon. The background is `canvas` and the foreground is the grid with the H, so no launcher mask clips it. The monochrome layer for themed icons is the H alone in one ink (dot radius 5.6, strokes 5).
- **Status bar:** the reminder's small icon is the same H alone, one ink, because Android tints it and 25 dots turn to noise at 24dp.
- **Store:** the full square on black; the store applies its own mask.
- **Motion:** the mark plays the mechanic. **The launch loop** (4000 ms): members fade to 12% one by one (0.32–1.92 s), seven random survivors turn `accent` (1.60–1.84 s), they travel into the H and leave dim members behind (2.24–2.88 s), the strokes draw in (2.88–3.20 s), hold, retract, and everyone returns (3.60–4.00 s). **The splash** on Android 12+ is the same motion compressed to 1000 ms; it plays once and ends on the connected H. **The loader** is its own, quicker motion for waiting states (a wallet signature, a chain read): over 2400 ms members drop out and seven random survivors turn `accent`, then it runs in reverse and the survivors stay `accent` until the grid is full. It sits beside words that say what it is waiting for, at 24dp cropped to the crowd, on `canvas` or `surface-1`. In the app the survivors are a new random pick every loop; the Android animated vectors and the animated SVGs use one fixed pick. With reduced motion or animations off, show the static icon.
- **Never** recolour the members, draw the H in anything but `accent`, add a second colour, or put the icon on anything but black. The one exception is **Holdout Lab**, the team's tester app, which members never see: its icon is the same grid with the H in `warning`, so the two apps are told apart at a glance on a tester's phone (see the Holdout Lab section).

## Components

These are mirrored from the Seeker system, so the app feels like part of the phone. Each component has its own page with a live preview and guidelines: Primary button, Secondary button, Quiet action, Card, Notice card, Dialog, Stat block, Tier chips, Choice chips, Switch, Stepper, Status chip, Bank ring, Countdown, Streak flame, Sheet, Roster row, Bottom nav, Camera sitting and Readouts. Switch, Stepper and Readouts exist for Holdout Lab only. Build them in Compose from `holdout.app.ui.theme` (`HoldoutColors`, `HoldoutTypography`, `HoldoutShapes`, `Spacing`). The HTML classes in `components/bundle.css` (`ho-*`) are a reference implementation for prototypes.
