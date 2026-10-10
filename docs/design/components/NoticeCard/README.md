# Notice card

A card that reports something the member or tester needs to know, with a quiet way out.

**Use for:** *Not saved* on Holdout Lab's first screen (a label or video lost after the set), and *Joining closed before your transaction landed. Nothing was taken.* after a late stake. It is not the one-line `Notice` (`ui/Components.kt`), a different component with no title and no action: the offline line on Today and the faucet's outcome lines are `Notice` lines, not cards.

**Anatomy:** a `Card` whose first line is a `title` in `text-primary`, or in `warning` when something was lost or refused; a `body` sentence that says what happened and what to do next; and one `QuietAction` (*OK*, *Try again*) when the card can be dismissed. No icon.

**The consumer provides:** the title, the sentence and the action. A warning card never blames the person: *The phone may be out of space*, not *You ran out of space*.

**Don't:** stack more than three; use `success` for a notice (good news is a number, not a card); put a primary button inside.
