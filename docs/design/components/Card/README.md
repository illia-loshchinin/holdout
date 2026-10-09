# Card

The only container: a `surface-1` panel with a `hairline` border and no shadow.

**Use for:** the prize card on Home, Holdout facts on the stake sheet, list groups.

**Anatomy:** `surface-1` fill, `size-border` `hairline` border, `radius-card`, `space-card` padding, rows `space-tight` apart. Label left in `label`/`text-tertiary`, value right in `amount`. Any dollar figure sits under its SKR amount in `text-tertiary`, prefixed `≈`.

**The consumer provides:** rows. A change since yesterday goes last in `success` with ▲ and words, not colour alone.

**Don't:** nest cards, add a coloured left border, or put a primary button inside one. Compose: `HoldoutCard { … }`.
