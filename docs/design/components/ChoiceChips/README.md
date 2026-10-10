# Choice chips

A small single choice among short labels, remembered between uses.

**Use for:** *Where was the phone?* in Holdout Lab's label dialog (`front · 30° · 35° · 40° · 45° · 50° · 55° · 60° · side`, then `on my left · on my right`), and the board picker on Leaderboards (`Easy · 7-day · Hard · 21-day · Short test`).

**Anatomy:** pills 36px tall on `surface-3` with a `hairline` border, each with a `size-touch` (48px) touch target that reaches 6px above and below the pill, `label`-style text, not uppercased, wrapping to as many rows as needed with `space-tight` gaps. Selected: `accent-tint` fill, `accent` border and text. A radio group: one tab stop, arrows move the selection.

**Built today:** neither use matches this page yet. Lab's *Where was the phone?* uses M3 `FilterChip`s (32dp, a second tap clears the choice, M3 colours), and the Leaderboards board picker is a row of text buttons, `accent` when picked. This page is the target; the gap is listed as known drift in the `holdout-ui` skill's compose map.

**The consumer provides:** the options, the selection, and whether an empty selection is allowed (the label dialog allows it; a board picker does not).

**Don't:** use for more than about ten options (that is a list), mix two questions in one row, or use these for the stake tiers, which have their own component.
