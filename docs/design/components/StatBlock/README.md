# Stat block

The signature of the app: one big number with a caption under it, borrowed from the Seeker's "1,000 SKR / your allocation" pattern.

**Use for:** the survivor counter (`189 / 312` *still standing*), the rep count, the Daily 25 (`25` *push-ups*), the claim amount.

**Anatomy:** an optional context `label` above in `text-tertiary`; the number in `counter` (56px Roboto Flex 300) or `display` (40px), always tabular; the caption in `label` style and `text-secondary` beneath, `space-1` apart. Centred. The denominator of a ratio drops to `text-tertiary`.

**Motion:** the survivor counter rolls digit by digit to its new value over 300ms. It never cuts.

**Don't:** put two `counter` blocks on one screen, or add units inside the number. The unit is the caption.
