# Tier chips

The stake picker: four chips across, one per tier, a single choice.

**Use for:** the stake sheet only. The tiers are the same for 7-day and 21-day Holdouts: ≈ $10, $25, $50, $100.

**Anatomy:** a 4-column grid with `space-tight` gaps. Each chip is `surface-3` with a `hairline` border and `radius-chip`. It shows the approximate dollar tier in 16px/500 over the exact SKR in `amount` at 12px, `text-secondary`. Selected: `accent-tint` fill, `accent` border and text. It is a radio group, so arrow keys move between chips.

**The consumer provides:** the Holdout's locked chip set (SKR amounts) and the selection.

**Always:** show *"Stakes and payouts are in SKR; dollar values are approximate."* nearby. The dollar label is never what the member receives.
