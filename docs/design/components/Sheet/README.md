# Sheet

A bottom sheet for a focused step that keeps Home behind it.

**Use for:** the faucet (*Get test tokens*) and the stake confirm. Nothing else.

**Anatomy:** `surface-2` with `radius-sheet` on the top corners only, a `hairline` top edge, and a 32×4 `drag-handle` centred at the top. `space-gutter` sides, sections `space-items` apart. It ends in one primary button and a one-line note in `text-tertiary`.

**The consumer provides:** a `label` title, content rows and the action. A stake sheet always includes *"If nobody drops out, you get exactly your stake back."* and *"Test network, test token."*

**Motion:** it enters over 300ms and dismisses on drag or scrim tap.
