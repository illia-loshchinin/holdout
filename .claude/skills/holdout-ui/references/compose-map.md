# Design book → Compose

Package: `holdout.app.ui.theme` (theme) and `holdout.app.ui` (shared components).
Source of values: `docs/design/tokens.json`. Re-check this page with
`python scripts/design-book.py check --report-only` — the script is the truth when they differ.

## Colour — `HoldoutColors.<PascalCase of token>`

| Token | Kotlin | Use |
| --- | --- | --- |
| `canvas` | `Canvas` | screen background |
| `surface-1` / `-2` / `-3` | `Surface1` / `Surface2` / `Surface3` | cards, dialogs · nav, sheets · chips, pressed, inputs |
| `hairline` | `Hairline` | every 1dp border |
| `scrim` | `Scrim` | bands over the camera |
| `drag-handle` | `DragHandle` | the sheet's drag handle |
| `text-primary` / `-secondary` / `-tertiary` | `TextPrimary` / `TextSecondary` / `TextTertiary` | numbers+copy · captions · labels, metadata |
| `accent`, `accent-strong` | `Accent`, `AccentStrong` | the one interactive colour |
| `accent-tint` | `AccentTint` | selected tier chip fill |
| `success`, `warning` | `Success`, `Warning` | still standing/done · forfeit/last 10 min/leave |
| `celebration` | `Celebration` | decoration only (gradient start, PB flame) |
| `celebration-text` | `CelebrationText` | readable purple (PB label) |
| `inverse-surface` | `TextPrimary` (same white; no own val yet) | primary button fill, active nav pill |
| `on-light`, `on-accent`, `on-success`, `on-warning` | `OnLight`, `OnAccent`, `OnSuccess`, `OnWarning` | text on those fills |
| `gradient-start` → `gradient-end` | `GradientStart` → `GradientEnd` | day complete, claim success — nothing else |

**Missing in Kotlin (add to `Color.kt` before first use, exact value):**
`inverse-surface` → `InverseSurface = Color(0xFFFFFFFF)` (optional, for clarity) ·
`palette-sky` `#0075ff`, `palette-charge` `#ff40c0`, `palette-kelp` `#59ff36`, `palette-beach` `#d0e300`
(spare hues, one per screen at most — add only when a screen needs one).
Kotlin stores ARGB (`0xAARRGGBB`); the book stores `#rrggbbaa`.

## Type

| Book style | Compose | Spec |
| --- | --- | --- |
| `counter` | `CounterStyle` | 56/60 Light, −0.01em, tnum, centred |
| `display` | `DisplayStyle` | 40/44 Light, tnum |
| `amount` | `AmountStyle` | 20/24 mono, tnum — SKR, countdowns, addresses |
| `headline` | `MaterialTheme.typography.headlineMedium` | 28/34 Medium |
| `title` | `MaterialTheme.typography.titleMedium` | 20/26 Medium — card titles, button labels |
| `body` | `MaterialTheme.typography.bodyLarge` | 16/24 |
| `label` | `LabelStyle` (= `labelMedium`) | 13/16 Medium, +0.02em, `.uppercase()` at call site |

Fonts: both faces the book names are bundled in `res/font/` (OFL, texts in `assets/licenses/`):
`HoldoutFonts.Mono` (JetBrains Mono 400/500) for amounts, countdowns and addresses — never
`FontFamily.Monospace` — and `HoldoutFonts.FlexCounter` / `FlexDisplay` (Roboto Flex 300, optical size
56 / 40) behind `CounterStyle` and `DisplayStyle`. UI text stays the system Roboto (`FontFamily.Default`).
Don't use `bodyMedium`/`bodySmall`/other M3 slots — they are not defined by the book and fall back to
Material defaults (`Notice` currently does this).

## Spacing and size — `Spacing.*`

| Token | Kotlin | Value |
| --- | --- | --- |
| `space-1` | `base` | 4 |
| `space-tight` | `Spacing.tight` | 8 |
| `space-items` | `Spacing.betweenItems` | 12 |
| `space-gutter` | `Spacing.screenGutter` | 20 |
| `space-card` | `Spacing.cardPadding` | 20 |
| `space-section` | `Spacing.betweenSections` | 24 |
| `size-touch` | `Spacing.touchTarget` | 48 |
| `size-button` | `Spacing.primaryButtonHeight` | 56 (buttons, roster rows) |
| `size-nav` | — (64.dp, add `navHeight` with BottomNav) | 64 |
| `size-ring-stroke` | `Spacing.ringStroke` | 4 |
| `size-border` | `Spacing.border` | 1 |

## Shape — `MaterialTheme.shapes.*`

| Token | Slot | Value |
| --- | --- | --- |
| `radius-chip` | `small` | 16dp |
| `radius-card` | `medium` | 24dp |
| `radius-sheet` | `large` (top corners only: `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)`) | 28dp |
| `radius-pill` | `extraLarge` | 50% |

## Components

| Book component | Compose today | Status |
| --- | --- | --- |
| Primary button | `PrimaryButton(text, onClick, enabled, busy)` | ✅ shared — some screens still inline a `Button`; prefer the shared one |
| Secondary button | `SecondaryButton(text, onClick, enabled, busy)` | ✅ shared |
| Card | `HoldoutCard(modifier, padding = PaddingValues(Spacing.cardPadding)) { … }` | ✅ `radius-card` (`shapes.medium`, 24dp); *(12 Oct)* `padding` is the one way to make a card compact: Today's Daily 25 card (`DailyCard` in `home/HomeScreen.kt`) passes `PaddingValues(horizontal = 16.dp, vertical = 14.dp)`, with the time left (`CountdownText` in the mono `LabelStyle` + *left today*) in the header row beside `SectionLabel("The Daily 25")` and the *free* `StatusChip`, a 44 sp / 48 sp counter (`DailyCounter` = `CounterStyle.copy(fontSize = 44.sp, lineHeight = 48.sp)`, not the book's 56/60) and a `PrimaryButton` at `Spacing.touchTarget` (48dp) instead of 56. Layout only; the words are unchanged |
| Label / caption | `SectionLabel(text)` | ✅ uppercases for you |
| Body copy | `Body(text, center)` | ✅ |
| Loader | `HoldoutLoader(size = 24.dp)`, `Reading { … }` | ✅ |
| Test cohort badge | `TestCohortBadge(dayLengthSeconds)` | ✅ text form (book's chip form: `ho-tag`); *(12 Oct)* a quiet neutral pill — `Surface3` fill, `TextSecondary` text, not a warning tone — reading `DEVNET COHORT · {day length} DAYS` on a Hackathon Holdout (`Components.kt`; `StatusChip.kt`'s comment still says `warning`) |
| Countdown | `CountdownText(seconds, modifier, style = AmountStyle)` | ✅ `ui/CountdownText.kt`: `accent`, `warning` in the last 10 minutes (`Derive.inWarningWindow`), named "4h 06m left" / "Under 10 minutes left, …"; text is `Format.countdown` |
| Stat block | `StatBlock(value, caption, modifier, label = null, of = null, display = false, valueColor)` | ✅ `ui/StatBlock.kt`: `CounterStyle` (or `DisplayStyle`), denominator `of` in `TextTertiary`. Screens still inline their own in `HomeScreen`, `CohortHome`: swap on the next touch |
| Quiet action | `QuietAction(text, onClick, modifier, warning = false, enabled = true)` | ✅ `ui/QuietAction.kt`: `bodyLarge` Medium, `Accent` or `Warning`, ≥48dp. Screens' `TextButton` "← Back" is the old form: use `ScreenHeader` |
| Status chip | `StatusChip(word, modifier, tone = ChipTone.Neutral)`, `ScriptedChip()` | ✅ `ui/StatusChip.kt`: 24dp `Surface3` pill, `shapes.small`, word uppercased, `◇` before SCRIPTED, named "Status: running" / "Scripted member". `TestCohortBadge` keeps its own text form |
| Notice card | `NoticeCard(title, text, modifier, warning = false, actionText = null, onAction = null)` | ✅ `ui/NoticeCard.kt`: a `HoldoutCard` with title, sentence and a `QuietAction`. `Notice(text)` is still the one-line notice |
| Bank ring | `BankRing(reps, bankedBlocks, count, caption, modifier, size)`, `BlockBars(reps, bankedBlocks, modifier)`, `bankBlocks(reps)` | ✅ `ui/BankRing.kt`: segments proportional to the blocks (25 → 10/10/5), banked `Accent`, track `Hairline`, `Spacing.ringStroke`; the counting bars are `BlockBars`. The sitting still draws its own bars until its screen adopts these |
| Streak flame | `StreakFlame(days, state: StreakState, modifier)` | ✅ `ui/StreakFlame.kt`: `Active` white · `ExtendedToday` success · `Broken` outlined tertiary · `PersonalBest` celebration + PB pill · `Milestone` (five states; a larger white flame in an `accent` ring, one pulse, none with reduced motion; text `{N}-day streak` and the line from `streak/StreakMilestones.kt` on S6 and T1); the flame is the DS path drawn on a canvas |
| Tier chips | `TierChips(tiers: List<TierChip(usd, skr)>, selected, onSelect, modifier)` | ✅ `ui/TierChips.kt`: `shapes.small`, `Surface3`, `AccentTint` + `Accent` when picked, radio group. ⚠ `stake/StakeSheet.kt` still draws its own chips (24dp, `Surface1`/`Surface3`): swap them for this |
| Choice chips | `ChoiceChips(options, selected: Int?, onSelect: (Int?) -> Unit, modifier, allowEmpty = false)` | ✅ `ui/ChoiceChips.kt`: 36dp pills in 48dp targets, wrapping, radio group, `allowEmpty` for the label dialog. ⚠ neither Lab's `FilterChip`s nor the Leaderboards picker use it yet |
| Roster row | `RosterRow(wallet, isYou, standing, days, scripted, modifier)` | ✅ `ui/RosterRow.kt`: `MemberIdentity` + "· you", "12 days ✓" in `Success` while standing, "lasted N days" tertiary when out, `◇ SCRIPTED`, no money. ⚠ `roster/RosterScreen.kt` still draws its own rows |
| Key-value row | `KeyValueRow(label, value, modifier, success = false)` | ✅ `ui/KeyValueRow.kt`: label `TextSecondary`, value `AmountStyle` |
| Screen header | `ScreenHeader(label, modifier, onBack, backDescription, onClose, closeDescription, trailing)` | ✅ `ui/ScreenHeader.kt`: 40dp round back button in a 48dp target, centred uppercase `LabelStyle` label, optional `StatusChip` (`trailing`) or ✕ (`onClose`). The label comes from `Format.headerLabel` / `lobbyHeaderLabel` / `finishedHeaderLabel` |
| Menu row | `MenuRow(title, caption = null, modifier, onClick)` | ✅ `ui/MenuRow.kt`: outlined pill, ≥48dp, title `text-primary`, caption `text-tertiary`, chevron; Me's *About Holdout* / *How Holdout works*, About's tour row |
| Pager dots | `PagerDots(count, current, modifier, noun = "Card")` | ✅ `ui/PagerDots.kt`: 22dp white pill + 7dp dots, named "Card 2 of 5" (the tour has five cards since 12 Oct) |
| Checklist row | `ChecklistRow(text, state: StepState)` | ✅ `ui/ChecklistRow.kt`: `Done` success tick · `Active` accent spinner · `Pending` tertiary ring, named with the state |
| Progress ring | `ProgressRing(description, modifier, done = false, size)` | ✅ `ui/ProgressRing.kt`: the 96dp confirming ring (accent arc turning; success ring + tick when `done`) |
| Sheet | `HoldoutSheet(onDismiss, modifier, dismissLabel) { … }` (a `BoxScope` extension) | ✅ `ui/Sheet.kt`: `surface-2`, 28dp top corners, handle, scrim, drag or scrim tap dismisses. **Wraps its content up to 92 % of the screen** — never give the content a fill-height modifier unless the sheet should stand at the ceiling. Used for the stake confirm (`stake/StakeSheet.kt`, Seeker gate states `stake/SeekerGateSheet.kt`) and the faucet (`faucet/FaucetSheet.kt`), through `shell/AppShell.kt`; and the pre-flight tip sheet (`sitting/PreflightTipsSheet.kt`, `PreflightTipsSheet`, a `BoxScope` over S3 inside `SittingScreen`, closed by its own `BackHandler`) |
| Bottom nav | `BottomNav(items: List<BottomNavItem>, selected, onSelect)` | ✅ `ui/BottomNav.kt`: 64dp, four tabs, 8dp accent dot with its meaning in the accessible name; drawn by `shell/AppShell.kt` on tab roots only |
| Segmented switch | `SegmentedSwitch(segments: List<Segment>, selected, onSelect)`, `Segment(label, count)` | ✅ *(12 Oct)* `ui/SegmentedSwitch.kt`: one pill row on `Surface2` with a `Hairline` border, equal 40dp segments in a 48dp target, the chosen one filled `TextPrimary` with `OnLight` text, each reading "label · count" with the count in the mono face; a tab group for the screen reader. Used by My Holdouts (H0): *Now · N* / *Past · N* |
| Offer card, Nudge row | `OfferCard(state: OfferCardState, onJoin)` (`home/HoldoutOffer.kt`), `NudgeRow(nudge: NudgeRowState, onClick)` (`home/HomeScreen.kt`) | ✅ state built by `home/TodayModel.kt` (`OfferCardState.of`, `TodayModel.nudges/comeback/holdouts`); a Holdout the member can join (`ChainView.joinable`: never one the program would refuse, so no greyed Join; `HoldoutsBlock.Hidden` draws nothing while a lobby place is held), and one line that opens the screen needing a tap (none for a Holdout the member is out of once they hold its comeback place); *(12 Oct)* the whole offer card is the tap target, the Join pill its visible cue (`HoldoutOffer.kt`, as on `holdouts/MyHoldoutsScreen.kt`) |
| Bank ring colour | `BankRing(…, bankedColor = HoldoutColors.Accent)` | ✅ `bankedColor` is `Warning` in a Holdout day's last ten minutes (`home/CohortHome.kt`) |
| Tour figure | `TourFigure(clock: FigureClock)` with `rememberFigureClock()` (`tour/TourFigure.kt`; frames in `FigureData.kt`, `FigureTimeline.kt`) | ✅ the counting figure of tour card 4 (was card 3 until 12 Oct), the design system's `TourCounting` ported as a Canvas drawing (top pose, the five pills, the count). *12 Oct:* the "from above" panel (120 × 74, left of the count) draws the shared top view `ui/TopView.kt` (`drawTopView(progress)`, the same drawing as the set-up guide's "At 45°" card) **still**, held at the bottom of the push-up (`drawFromAbove(TOP_VIEW_STILL, …)`; the side view carries the motion), with `drawAngles(measurer, density)` on top of it: an accent arc at the near shoulder from the body line to the upper arm (`the 45° labels` = 39.4°, `atan2(19.7, 24)` of the drawn arm), the body line dashed, and the text *45°* beside the elbow (the board's `su-angle` group). The tour's secondary *Back* button sits beside *Next* from card 2; `FigureTimeline` is pure and tested (`TourFigureTimelineTest`, `FigureDataTest`) |
| Tour card 3, where a lost stake goes | `MoneyCard()` with `SplitBar()` and `SplitRow(percent, colour, title, vararg lines)` (private, `tour/TourScreen.kt`; board `TourMoney`) | ✅ *(12 Oct)* `MoneyCard` = a `headlineMedium` title *Where a lost stake goes*, a `bodyLarge` sub (*80 %* in the mono face, accent, then *comes back to members. The rest runs Holdout.*), one `HoldoutCard` holding the bar and three rows, and a `bodyMedium` tertiary note (*Lost on the last day? Join the next one on its first day, or the one after. Nothing is taken from a stake that finishes.*). `SplitBar` is a 14dp pill row of three weighted boxes, 70 `Accent` · 10 `Success` · 20 `TextPrimary` at 24 % alpha, 2dp gaps, with the accessible name *Of every lost stake: 70 percent to the prize pool, 10 percent to your comeback, 20 percent to the project.* `SplitRow` = the percent in the mono `titleMedium` (52dp column, in the part's colour) beside a `titleSmall` title and `bodyMedium` secondary lines: **70 %** *This Holdout's prize pool* · **10 %** *Your comeback* (two lines: the set-aside sentence and *Don't, and it joins the boost: up to 10 % more on later prize pools, with every pool nobody won.*) · **20 %** *The project*. The tour is five cards (`CARDS = 5`, Skip goes to `LAST` = card 5, `PagerDots(CARDS, …)`), each page a private composable: `DailyCard`, `HoldoutCardPage`, `MoneyCard`, `CountingCard`, `ScriptedCard` |
| Comeback offer | `Format.winBack`, `Format.comebackToday`, `Format.comebackForfeit` (`ui/Components.kt`); `ForfeitText.offer(…)` → `OfferCopy`, drawn by the private `OfferCard(copy)` and `timingLine(text, countdowns)` (`cohort/ForfeitScreen.kt`); `DailyHoldoutLine.nudge(offers, now)` → `Nudge`, drawn by the private `ComebackCard(card, onClick)` (`home/HomeScreen.kt`, state `ComebackCardState` from `home/TodayModel.kt`); `StakeText.comeback(amount)` (`stake/StakeText.kt`); `MyHoldoutsModel.COMEBACK_JOINED` | ✅ *(12 Oct)* the comeback is **exact** (`Comeback.Offer.amount(chip)` = `min(10 % of the lost stake, the chip)`, guaranteed), so no copy says "up to". Forfeit `OfferCard`: a green-tinted card (`Success` at 8 % fill, 32 % border) with the label *COMEBACK*, the `titleMedium` headline `Format.winBack(amount)` (*Win back 125 SKR*), a body, and a timing line in the label style where **only the countdowns are mono** (*Starts in* `2d 4h` *· joining closes in* `2d 3h`; a late round: *Joining closes in* `…`); the primary button reads *Join the next {level}* or, for a late round (`lateJoin`), *Join the {level}*. Today's `ComebackCard`: *Get back 125 SKR of your lost stake: finish the Spark · 7-day starting in 2d 4h. Joining closes in 2d 3h.* (a round already running: *…finish the Spark · 7-day that started today. Joining closes in …*). Stake sheet: the green `bodyLarge` line under the chips, *Finish it and 125 SKR of the stake you lost comes back.*, for the chip picked, and a *Started today* pill in place of *Starts in …* once the round has started. H0's Out row: detail *Win back 125 SKR* (success tone), or *Comeback joined* once the member holds a place in the round |
| Member copy rules | `Format` (`ui/Components.kt`) | ✅ levels, taglines, rule sentences, the fee and boost lines, countdowns and the banned words (Easy, Hard, cohort, league, challenge, earn) are asserted word for word in `FormatCopyTest`; a screen takes its sentence from `Format`, never types it again |
| Camera sitting | stateful host `SittingScreen` (`sitting/SittingScreen.kt`, shared with Lab behind `tools == null`) over stateless pages: `CameraPermissionScreen`, `SetupGuide` / `SetupSheet` (`sitting/PlacementGuide.kt`: tabs *1. Positioning · 2. Rules · 3. Start*, no intro paragraph, the *What you'll hear and see* card on Rules — 12 Oct), `PreflightContent` (the coach says *Place your phone* once; the outline is the dashed box alone and appears once the phone stands; no *Under 10 minutes left…* pill — 12 Oct), `CountdownContent`, `CountingContent`, `DayCompleteContent` (+ `DayCompleteUi`, `DayCompleteOffer`), `StoppedScreen` (+ `StoppedUi`, the crash variant), `SittingBars`, `SittingChrome`; words in `SittingCopy` | ✅ wired by `shell/SittingHost.kt`: the finished page gets `headerLabel`, `onChain` and (Daily 25 only) the offer from `shell/SittingOffers.kt`; the crash page comes from `shell/SittingWiring.kt` `interruptedSittings` on Today. ⚠ drift: the sitting's coach cue sits on `shapes.large` (`radius-sheet`); book: the cue is the caption in the bottom scrim band |
| Test helpers (`src/test/.../testutil`) | `AnimationsOff` (rule), `TALL_PHONE` (`@Config(qualifiers = TALL_PHONE)`), `ChainFixtures` (`holdout(…)`, `member(…)`, `view(…)`) | ✅ any Robolectric screen that shows `HoldoutLoader` needs `AnimationsOff`, or Compose never goes idle; a screen taller than the phone needs `TALL_PHONE` |
| Leaderboard line | inline in `leaderboard/LeaderboardScreen.kt` (place + identity, rate, figures) | ✅ its own anatomy, described on the Roster row page; not a roster row |
| Dialog | Lab: `TraceLabelDialog`, `TraceNotSavedDialog` in `src/lab/.../sitting/`; member: `SetupDialog` (*Before you get down*) in `sitting/PlacementGuide.kt` | ⚠ drift: no `hairline`, no width cap, title in M3 `titleLarge` (book: `title` = `titleMedium`). The book says the member app uses no dialog; `SetupDialog` is one |
| Switch (Lab only) | `TestSwitch`, `RecordVideoSwitch` in `src/lab/.../ui/TestSwitch.kt` | ✅ M3 `Switch` with the book's labels and colours |
| Stepper (Lab only) | the *Target* stepper in `LabApp.kt`; `Counter` in `TraceLabelDialog` | ⚠ drift: `Counter` shows its value in `CounterStyle`; book: `amount` |
| Readouts (Lab only) | `LabReadouts` in `src/lab/.../sitting/LabReadouts.kt` | ✅ `LabelStyle`, not uppercased |

## Copy — `Format` (in `ui/Components.kt`)

| Need | Call |
| --- | --- |
| Level of a Holdout, `Spark · 7-day` / `Habit · 21-day` | `Format.level(c)`, `Format.levelName(form)`; the one-mention title is `Format.holdoutTitle(c)` |
| Level tagline, `Light it up · knees allowed` / `Make it stick · toes only` | `Format.levelTagline(c)` |
| Level rule sentence (G2), one source in `Stakes.EASY_RULE` / `STAKED_RULE` | `Format.levelRule(c)` — the Holdout's own, never the other level's |
| Header label, no dates (G3) | `Format.headerLabel(c, day)`, `Format.lobbyHeaderLabel(c, secondsToStart)`, `Format.finishedHeaderLabel(c)` |
| Fee sentence (G5) | `Format.lostStakeSplit(c, name)` (stake sheet); `Format.lostStakeSplitForfeit(c, name)` (forfeit, under **Stake lost**); `name` is `Format.boostName(c, test)`: the level, or *Hackathon Holdout* |
| Boost (G6) | `Format.boostLine(amount)` on an offer; `Format.runningBoostLine(amount)` on a running Holdout |
| Comeback (exact, never "up to") | `Format.winBack(amount)` (*Win back 125 SKR*), `Format.comebackToday(amount, c, startsInSeconds)` (`null` seconds: *…that started today.*), `Format.comebackForfeit(amount, c)`, `Format.COMEBACK_CLAIM_NOTE`; the stake sheet's line is `StakeText.comeback(amount)` |
| Wallet wait (G7) | `Format.WAITING_FOR_WALLET` |
| Amounts | `Format.skr(baseUnits)` (thousands grouped), `Format.tierUsd(tier)`, `Format.countdown(seconds)` |

HTML reference implementations of every component are in `docs/design/components/bundle.css` (`ho-*`
classes) — read the CSS for exact paddings, gaps and states when the README is not enough.

## Known copy drift (not fixed by this skill)

- None known. `HomeScreen` now reads *THE DAILY 25* (was *TODAY'S CHALLENGE*); check any new string against `copy-rules.md`.
