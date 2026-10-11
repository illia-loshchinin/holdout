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

Fonts: the book names Roboto Flex (numbers) and JetBrains Mono (amounts). The app currently uses
`FontFamily.Default` and `FontFamily.Monospace` as stand-ins (see `Type.kt` KDoc). Don't add font
files as part of a screen task; mention it if the screen relies on it.
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
| Card | `HoldoutCard { … }` | ✅ `radius-card` (`shapes.medium`, 24dp) |
| Label / caption | `SectionLabel(text)` | ✅ uppercases for you |
| Body copy | `Body(text, center)` | ✅ |
| Loader | `HoldoutLoader(size = 24.dp)`, `Reading { … }` | ✅ |
| Test cohort badge | `TestCohortBadge(dayLengthSeconds)` | ✅ text form (book's chip form: `ho-tag`) |
| Countdown | `CountdownText(seconds, modifier, style = AmountStyle)` | ✅ `ui/CountdownText.kt`: `accent`, `warning` in the last 10 minutes (`Derive.inWarningWindow`), named "4h 06m left" / "Under 10 minutes left, …"; text is `Format.countdown` |
| Stat block | `StatBlock(value, caption, modifier, label = null, of = null, display = false, valueColor)` | ✅ `ui/StatBlock.kt`: `CounterStyle` (or `DisplayStyle`), denominator `of` in `TextTertiary`. Screens still inline their own in `HomeScreen`, `CohortHome`: swap on the next touch |
| Quiet action | `QuietAction(text, onClick, modifier, warning = false, enabled = true)` | ✅ `ui/QuietAction.kt`: `bodyLarge` Medium, `Accent` or `Warning`, ≥48dp. Screens' `TextButton` "← Back" is the old form: use `ScreenHeader` |
| Status chip | `StatusChip(word, modifier, tone = ChipTone.Neutral)`, `ScriptedChip()` | ✅ `ui/StatusChip.kt`: 24dp `Surface3` pill, `shapes.small`, word uppercased, `◇` before SCRIPTED, named "Status: running" / "Scripted member". `TestCohortBadge` keeps its own text form |
| Notice card | `NoticeCard(title, text, modifier, warning = false, actionText = null, onAction = null)` | ✅ `ui/NoticeCard.kt`: a `HoldoutCard` with title, sentence and a `QuietAction`. `Notice(text)` is still the one-line notice |
| Bank ring | `BankRing(reps, bankedBlocks, count, caption, modifier, size)`, `BlockBars(reps, bankedBlocks, modifier)`, `bankBlocks(reps)` | ✅ `ui/BankRing.kt`: segments proportional to the blocks (25 → 10/10/5), banked `Accent`, track `Hairline`, `Spacing.ringStroke`; the counting bars are `BlockBars`. The sitting still draws its own bars until its screen adopts these |
| Streak flame | `StreakFlame(days, state: StreakState, modifier)` | ✅ `ui/StreakFlame.kt`: `Active` white · `ExtendedToday` success · `Broken` outlined tertiary · `PersonalBest` celebration + PB pill; the flame is the DS path drawn on a canvas |
| Tier chips | `TierChips(tiers: List<TierChip(usd, skr)>, selected, onSelect, modifier)` | ✅ `ui/TierChips.kt`: `shapes.small`, `Surface3`, `AccentTint` + `Accent` when picked, radio group. ⚠ `stake/StakeSheet.kt` still draws its own chips (24dp, `Surface1`/`Surface3`): swap them for this |
| Choice chips | `ChoiceChips(options, selected: Int?, onSelect: (Int?) -> Unit, modifier, allowEmpty = false)` | ✅ `ui/ChoiceChips.kt`: 36dp pills in 48dp targets, wrapping, radio group, `allowEmpty` for the label dialog. ⚠ neither Lab's `FilterChip`s nor the Leaderboards picker use it yet |
| Roster row | `RosterRow(wallet, isYou, standing, days, scripted, modifier)` | ✅ `ui/RosterRow.kt`: `MemberIdentity` + "· you", "12 days ✓" in `Success` while standing, "lasted N days" tertiary when out, `◇ SCRIPTED`, no money. ⚠ `roster/RosterScreen.kt` still draws its own rows |
| Key-value row | `KeyValueRow(label, value, modifier, success = false)` | ✅ `ui/KeyValueRow.kt`: label `TextSecondary`, value `AmountStyle` |
| Screen header | `ScreenHeader(label, modifier, onBack, backDescription, onClose, closeDescription, trailing)` | ✅ `ui/ScreenHeader.kt`: 40dp round back button in a 48dp target, centred uppercase `LabelStyle` label, optional `StatusChip` (`trailing`) or ✕ (`onClose`). The label comes from `Format.headerLabel` / `lobbyHeaderLabel` / `finishedHeaderLabel` |
| Menu row | `MenuRow(title, caption = null, modifier, onClick)` | ✅ `ui/MenuRow.kt`: outlined pill, ≥48dp, title `text-primary`, caption `text-tertiary`, chevron; Me's *About Holdout* / *How Holdout works*, About's tour row |
| Pager dots | `PagerDots(count, current, modifier, noun = "Card")` | ✅ `ui/PagerDots.kt`: 22dp white pill + 7dp dots, named "Card 2 of 4" |
| Checklist row | `ChecklistRow(text, state: StepState)` | ✅ `ui/ChecklistRow.kt`: `Done` success tick · `Active` accent spinner · `Pending` tertiary ring, named with the state |
| Progress ring | `ProgressRing(description, modifier, done = false, size)` | ✅ `ui/ProgressRing.kt`: the 96dp confirming ring (accent arc turning; success ring + tick when `done`) |
| Sheet, Bottom nav, Camera sitting | partly inline in their screens (`stake/StakeSheet.kt`, `faucet/FaucetSheet.kt`, `sitting/SittingScreen.kt`) | ◻ grep before building; extract to `ui/` on second use. ⚠ drift: the sitting's coach cue sits on `shapes.large` (`radius-sheet`); book: the cue is the caption in the bottom scrim band |
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
| Fee sentence (G5) | `Format.lostStakeSplit(c)` (stake sheet); `Format.lostStakeSplitForfeit(c)` (forfeit, under **Stake lost**) |
| Boost (G6) | `Format.boostLine(amount)` on an offer; `Format.runningBoostLine(amount)` on a running Holdout |
| Comeback | `Format.comebackToday(amount, c, startsInSeconds)`, `Format.comebackForfeit(amount, c)`, `Format.COMEBACK_CLAIM_NOTE` |
| Wallet wait (G7) | `Format.WAITING_FOR_WALLET` |
| Amounts | `Format.skr(baseUnits)` (thousands grouped), `Format.tierUsd(tier)`, `Format.countdown(seconds)` |

HTML reference implementations of every component are in `docs/design/components/bundle.css` (`ho-*`
classes) — read the CSS for exact paddings, gaps and states when the README is not enough.

## Known copy drift (not fixed by this skill)

- None known. `HomeScreen` now reads *THE DAILY 25* (was *TODAY'S CHALLENGE*); check any new string against `copy-rules.md`.
