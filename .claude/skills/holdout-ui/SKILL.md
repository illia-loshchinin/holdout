---
name: holdout-ui
description: Build or change an Android Compose screen or UI component in the Holdout app so it follows the Holdout design book (docs/design) — tokens, components, copy and naming rules, a screen template and a visual check. Use for any work on kotlin/app screens (*Screen.kt, *Sheet.kt), ui/Components.kt or ui/theme, for "make a screen", "restyle", "add a card/button/sheet", or "does this match the design".
---

# Holdout UI — build screens from the design book

The design book is `docs/design/` — a read-only mirror of the Holdout design system artifact.
Every colour, size, word and component on a screen comes from it. A value you did not read
from the book or from `holdout.app.ui.theme` is a guess; do not ship guesses.

## Before writing any UI code

Read, in one go:

1. `docs/design/README.md` — the brand book: principles, voice, naming, visual foundations.
2. `references/compose-map.md` — token → Kotlin name, component → Composable, known drift.
3. `docs/design/components/<Comp>/README.md` for every component the screen uses.
4. `references/copy-rules.md` — the words checklist (run it on every string you write).
5. The nearest existing screen (`home/HomeScreen.kt`, `claim/ClaimScreen.kt`, `lobby/LobbyScreen.kt`)
   to match local conventions.

If the screen is new, also read its entry in `docs/holdout_screen_map.md` and the PRD section it
cites (`docs/holdout_v1_prd.md` §11 for visuals). Product behaviour comes from the spec, not from
the design book: if the two disagree, stop and say so. Spec-kit files (`specs/*`) change only
through the `/speckit-*` skills — never edit them from here (see `CLAUDE.md`).

## Hard rules

- **Only theme values.** Colours from `HoldoutColors`, type from `CounterStyle` / `DisplayStyle` /
  `AmountStyle` / `LabelStyle` / `MaterialTheme.typography.{headlineMedium,titleMedium,bodyLarge}`,
  spacing from `Spacing`, shapes from `MaterialTheme.shapes`. No `Color(0x…)`, no literal `.sp`, no
  spacing `.dp` outside `ui/theme/`. If a token has no Kotlin twin yet (see compose-map "Missing"),
  add it to the theme file first — with the exact value from `docs/design/tokens.json` — then use it.
- **Shared components first.** Use `PrimaryButton`, `SecondaryButton`, `HoldoutCard`, `SectionLabel`,
  `Body`, `Notice`, `Reading`, `HoldoutLoader`, `Format.*` from `holdout.app.ui`. When the book names a
  component that has no shared Composable yet (StatBlock, BankRing, Countdown, StreakFlame, TierChips, StatusChip, NoticeCard, Dialog, ChoiceChips, Switch, Stepper, Readouts,
  Sheet, RosterRow, BottomNav, QuietAction), grep for an inline version first; extract it into
  `ui/Components.kt` (or its own file in `ui/`) instead of writing a second copy.
- **One column, no app bar, hero number centred.** `Spacing.screenGutter` edges, `Spacing.betweenSections`
  between sections, `Spacing.betweenItems` between cards/rows. Dark only; no dynamic colour; no shadows
  or elevation — surface step + 1dp `Hairline` border separates.
- **One primary button per screen**, white pill, no icon, no colour, no gradient.
- **`Accent` is the only interactive colour.** `Success` / `Warning` always paired with a word or icon.
  `Celebration` is decoration only (gradient, PB flame); readable purple uses `celebration-text`.
- **Gradient** (`GradientStart → GradientEnd`, 45°, ~900 ms, once) only on *day complete* and *claim success*.
- **Numbers** that change use tabular figures (the Number styles already set `tnum`); the survivor counter
  rolls, never cuts. Countdowns via `Format.countdown`, never a clock time or date.
- **Touch targets** ≥ `Spacing.touchTarget`; buttons `Spacing.primaryButtonHeight`. Every icon-only control
  has a `contentDescription`; status meaning is in the accessible name too.
- **Motion:** 200 ms standard, 300 ms entering. No confetti, no emoji in the UI.

## Workflow

1. **Plan** the screen as an ASCII sketch in the file's KDoc (the codebase does this — see `HomeScreen.kt`),
   naming each element's component and token.
2. **Scaffold** from `references/screen-template.kt` — stateless `XScreen(state, callbacks)`, a
   `data class XState`, and one `@Preview` per meaningful state (fresh, part-way, done, empty, error).
3. **Build** with the hard rules above. Write copy last and run it through `references/copy-rules.md`.
4. **Check** — follow `references/verify.md`:
   - `python scripts/design-book.py check --report-only` (theme drift + raw colours),
   - `bash scripts/check-wording.sh` (forbidden words, `≈` on fiat, test-network notice),
   - `./gradlew :app:compileDebugKotlin` from `kotlin/`,
   - render and look: compare the preview/emulator/scrcpy screenshot with `docs/design/gallery.html`
     and the component READMEs; fix what differs before calling it done.
5. **Report** what you used, anything you added to the theme, and any rule you could not meet and why.

## When the design book changes

`docs/design/` is never edited by hand. To refresh it, follow `references/sync.md`, then run
`python scripts/design-book.py all` and fix any drift it reports in `ui/theme/`.
