# Bank ring

A progress ring around the rep count that shows how much of today is safely banked.

**Use for:** Home under the primary button, and the sitting screen around the live rep count.

**Anatomy:** a circle split into one segment per block. A 25-rep day is 10 + 10 + 5. Each segment's arc is proportional to its reps, with a small gap between. The track is `hairline`, a banked segment is `accent`, and both are `size-ring-stroke` wide with round caps. Inside sits the count in `display` and a caption (`banked`, `day complete`).

**The consumer provides:** the day's reps and how many blocks are banked.

**Behaviour:** a segment fills (200ms) the moment a block of 10 banks, with the banked chime. Reps short of the next block are never drawn on the ring. They are not kept. On day complete, the ring plays the single `gradient-start` → `gradient-end` sweep.
