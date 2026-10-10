# Primary button

The one action a screen exists for: a full-width white pill with a black label.

**Use for:** *Start*, *Connect wallet*, *Join the 7-day Holdout*, *Claim 1,560 SKR*, *10 banked · do 15 more*. One per screen, at most.

**Anatomy:** `inverse-surface` fill, `on-light` label in `title` (20px/500), height `size-button`, corners `radius-pill`, full width inside `space-gutter`. Pressed dims the fill to `text-secondary`. Disabled drops to 38% opacity. Focus is a 2px `accent` ring, offset 2px.

**The consumer provides:** the label (sentence case, a verb or the state of the day) and the action.

**Do:** put the live number in the label when it is the reason to tap (*10 banked · do 15 more*). **Don't:** colour it, add an icon, use the gradient, or place two on one screen. Compose: `PrimaryButton(text, onClick)`.
