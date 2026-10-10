# Notice card

A card that reports something the member or tester needs to know, with a quiet way out.

**Use for:** *Not saved* on Holdout Lab's first screen (a label or video lost after the set), the offline notice on Today (*devnet unreachable — the Daily 25 still works*), *Joining closed before your transaction landed. Nothing was taken.* after a late stake, and the faucet's outcome lines.

**Anatomy:** a `Card` whose first line is a `title` in `text-primary`, or in `warning` when something was lost or refused; a `body` sentence that says what happened and what to do next; and one `QuietAction` (*OK*, *Try again*) when the card can be dismissed. No icon.

**The consumer provides:** the title, the sentence and the action. A warning card never blames the person: *The phone may be out of space*, not *You ran out of space*.

**Don't:** stack more than three; use `success` for a notice (good news is a number, not a card); put a primary button inside.
