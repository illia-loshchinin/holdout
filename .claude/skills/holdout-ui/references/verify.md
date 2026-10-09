# Verify a screen against the design book

Run from the repo root (`W:/Holdout`) unless noted. Don't call a screen done until all four pass or
each remaining gap is reported to the user.

## 1. Static checks
```bash
python scripts/design-book.py check --report-only   # theme drift + raw Color() in screens
bash scripts/check-wording.sh                       # forbidden words, ≈ on fiat, test-network notice
cd kotlin && ./gradlew :app:compileDebugKotlin      # it builds
```
A new `note screen … raw colour` line for a file you touched is a failure: replace it with a `HoldoutColors` value.

## 2. Self-review against the rules
Walk the screen top to bottom and answer each, citing the line:
- Every colour / text style / spacing / shape comes from the theme (no literals)?
- One `CounterStyle` at most; one `PrimaryButton` at most; no app bar; bottom nav only on tab roots?
- `Accent` only on interactive or in-progress things; `Success`/`Warning` paired with words?
- Gradient only on day complete / claim success?
- Touch targets ≥ 48dp; icon-only controls have `contentDescription`?
- Every string passes `copy-rules.md`; required sentences present on staking/join screens?
- Each `@Preview` state renders (fresh, part-way, done, empty, error, last-10-minutes where relevant)?

## 3. Look at it
Pick what is available, in this order:
1. **Compose preview render** — if the project has a screenshot tool (Paparazzi/Roborazzi), record the
   previews and open the PNGs. If not, ask the user for a screenshot of the preview pane rather than
   adding a new test dependency without asking.
2. **Device / emulator** — install the debug build, open the screen, then
   `adb exec-out screencap -p > %TEMP%/holdout-<screen>.png` and open the PNG.
   `scrcpy` (see `docs/holdout_setup_runbook.md` §5) shows the live phone; never save captures under
   `phone_traces/` or `phone_screens/`.
3. **Reference** — `python scripts/design-book.py build` then open `docs/design/gallery.html` at 412px wide
   (browser or Playwright). Compare component by component: radius, padding, colour, type size, casing.

Report what you compared and every difference you left, with the reason.

## 4. After the screen ships
If you added a token to `ui/theme/`, or extracted a component into `ui/`, update
`.claude/skills/holdout-ui/references/compose-map.md` in the same change.
