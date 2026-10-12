# Sheet

A bottom sheet for a focused step that keeps Home behind it.

**Use for:** the faucet (*Get test tokens*), the stake confirm and the pre-flight tip sheet (*Getting the checks green*). Nothing else.

**Anatomy:** `surface-2` with `radius-sheet` on the top corners only, a `hairline` top edge, and a 32×4 `drag-handle` centred at the top. `space-gutter` sides, sections `space-items` apart. It ends in one primary button and a one-line note in `text-tertiary`.

**The consumer provides:** a `label` title, content rows and the action. A stake sheet always includes *"If nobody drops out, you get exactly your stake back."* and *"Test network, test token."*

**The tip sheet** is a usage of this sheet, not a new component: the title, then one row per pre-flight check in the pills' order (the pill's label above that check's own fix sentence, a check that is red right now in `warning` with *Not green yet* for screen readers), then **Open the set-up guide** as the primary button and **Back to the checks** as a quiet action. It opens only when the member taps *Still not green? Tips*, and the checks keep running underneath.

**Motion:** it enters over 300ms and dismisses on drag or scrim tap.
