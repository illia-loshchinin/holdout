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
| `size-ring-stroke`, `size-border` | — | 4dp, 1dp |

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
| Countdown | `Format.countdown(seconds)` in `AmountStyle` + `Accent` (→ `Warning` in last 10 min) | ◻ no Composable yet |
| Stat block | inline in `HomeScreen`, `CohortHome` (`CounterStyle` + caption) | ◻ extract `StatBlock(label?, value, of?, caption)` |
| Quiet action | screens use `TextButton` "← Back" in `TextSecondary` | ◻ book: `bodyLarge` Medium in `Accent` (or `Warning`), ≥48dp |
| Bank ring, Streak flame, Tier chips, Sheet, Roster row, Bottom nav, Camera sitting | partly inline in their screens (`sitting/SittingScreen.kt`, `stake/StakeSheet.kt`, `roster/RosterScreen.kt`, `faucet/FaucetSheet.kt`; no streak flame UI yet) | ◻ grep before building; extract to `ui/` on second use |
| Leaderboard line | inline in `leaderboard/LeaderboardScreen.kt` (place + identity, rate, figures) | ✅ its own anatomy, described on the Roster row page; not a roster row |
| Notice card | Lab's *Not saved* card in `LabApp.kt` | ◻ no shared Composable. `Notice(text)` is the one-line notice, a different component (the faucet's outcomes, Today's offline line) |
| Status chip | `TestCohortBadge` (text form) | ◻ book's chip form `ho-tag`; Lab's `LAB` chip is not built yet |
| Choice chips | Lab: M3 `FilterChip`s in `TraceLabelDialog`; member: the Leaderboards picker, a row of `TextButton`s | ⚠ drift: neither matches the book (36px pill, 48dp target, `accent-tint` when picked, one choice, arrow keys) |
| Dialog (Lab only) | `TraceLabelDialog`, `TraceNotSavedDialog` in `src/lab/.../sitting/` | ⚠ drift: no `hairline`, no width cap, title in M3 `titleLarge` (book: `title` = `titleMedium`) |
| Switch (Lab only) | `TestSwitch`, `RecordVideoSwitch` in `src/lab/.../ui/TestSwitch.kt` | ✅ M3 `Switch` with the book's labels and colours |
| Stepper (Lab only) | the *Target* stepper in `LabApp.kt`; `Counter` in `TraceLabelDialog` | ⚠ drift: `Counter` shows its value in `CounterStyle`; book: `amount` |
| Readouts (Lab only) | `LabReadouts` in `src/lab/.../sitting/LabReadouts.kt` | ✅ `LabelStyle`, not uppercased |

HTML reference implementations of every component are in `docs/design/components/bundle.css` (`ho-*`
classes) — read the CSS for exact paddings, gaps and states when the README is not enough.

## Known copy drift (not fixed by this skill)

- `home/HomeScreen.kt` label `TODAY'S CHALLENGE` — the book says *challenge* is never an in-app noun.
  Raise it with the user rather than silently changing it.
