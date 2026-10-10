# Dialog

A question that must be answered before leaving a screen, over the `scrim`.

**Use for:** Holdout Lab's *What did you just do?* (the label of a set) and *This set was not saved*. The member app uses none today: its focused steps are sheets (the faucet, the stake confirm), and a sitting is never interrupted by a question.

**Anatomy:** `surface-1`, a `hairline` border, `radius-card`, `space-card` padding, full width inside the screen gutter (at most 350px wide). A centred `title`, a `body` sentence, the content (counters, choice chips, a text field), then one actions row: a `QuietAction` on the left (*Skip*, *Cancel*) and a compact primary pill on the right (*Save*, *OK*). Tapping the scrim is the quiet action.

**The consumer provides:** the title, the sentence, the content and both actions. A dialog with one answer has only the primary pill.

Each stepper names its own row: *Fewer full push-ups* / *More full push-ups*.

**Built today:** Lab's two dialogs (`TraceLabelDialog`, `TraceNotSavedDialog`) fill the gutter with no `hairline` and no width cap, and set the title in M3's `titleLarge`. This page is the target; the gap is listed as known drift in the `holdout-ui` skill's compose map.

**Motion:** enters over 300ms like a sheet; no bounce.

**Don't:** stack dialogs, put a dialog over the camera while counting, or use one where a notice card would do.
