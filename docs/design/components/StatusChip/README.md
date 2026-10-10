# Status chip

A small uppercase word that names a state beside a number or a title: `devnet`, `free`, `open`, `running`, `settled`, `TEST COHORT · 10-MIN DAYS`, `◇ SCRIPTED`, `LAB`.

**Use for:** the state of a Holdout on a card or header, the network, the free tier, the disclosed labels on scripted members and short test Holdouts, and the `LAB` marker on Holdout Lab's first screen.

**Anatomy:** a 24px pill on `surface-3`, `radius-chip`, 11px/500 uppercase with 0.04em tracking, `text-secondary`. The word may take a state colour: `success` for *running*, *settled*, *standing*; `accent` for *open*, *free* and `LAB`; `warning` for `TEST COHORT`, *out*, *forfeited*. The diamond ◇ precedes `SCRIPTED` everywhere.

**Rules:** the chip is always a word, never a colour alone, so a colour-blind reader loses nothing. One chip per card at most; two on a header only when one is a disclosure (`TEST COHORT`, `SCRIPTED`). Never a count in a chip: counts are numbers on the card.

**Don't:** invent new state words per screen (use the Holdout's stage names), put a chip inside a button, or colour a chip with `celebration`.
