# Roster row

One member of a Holdout, by Seeker ID, or shortened address when there is none.

**Use for:** the roster on the **Standing** tab and the leaderboards (where the right-hand figure is the survival rate, not days). Members still standing come first, then forfeited members ordered by days lasted.

**Anatomy:** `size-button` tall, `hairline` dividers. The member's identity uses `amount` at 16px: their Seeker ID in full with its ending (`alice.skr`), or, without one, their shortened address (`ABcd…wxYZ`, `Format.short`). Both are set alike, in the same face and colour, so a row with a name and a row without one look like the same kind of row. A name too long for the row keeps its start and the whole `.skr` ending (`averyveryverylo….skr`). Most members have no Seeker ID, so the address is the common case and must look intended. The connected member's row ends in *· you*. Days sit right-aligned in mono, `success` with a ✓ while standing. A forfeited member's address drops to `text-tertiary` with *lasted N days*. A scripted member carries a `◇ SCRIPTED` label chip on every roster.

**Never:** show a stake, prize or payout amount on a roster, ever. Rank by discipline, never by money. Never mark a name as verified or an address as missing (no badge, icon or colour), and never order or label anyone by name: the order, *you* and `◇ SCRIPTED` come from the wallet.
