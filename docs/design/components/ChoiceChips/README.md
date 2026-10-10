# Choice chips

A small single choice among short labels, remembered between uses.

**Use for:** *Where was the phone?* in Holdout Lab's label dialog (`front · 30° · 35° · 40° · 45° · 50° · 55° · 60° · side`, then `on my left · on my right`), and the board picker on Leaderboards (`Easy · 7-day · Hard · 21-day · Short test`).

**Anatomy:** pills 36px tall on `surface-3` with a `hairline` border, 14px/500 labels, wrapping to as many rows as needed with `space-tight` gaps. Selected: `accent-tint` fill, `accent` border and text. A radio group: one tab stop, arrows move the selection.

**The consumer provides:** the options, the selection, and whether an empty selection is allowed (the label dialog allows it; a board picker does not).

**Don't:** use for more than about ten options (that is a list), mix two questions in one row, or use these for the stake tiers, which have their own component.
