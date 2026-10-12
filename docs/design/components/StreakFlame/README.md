# Streak flame

The daily streak: days in a row with a completed day. It is free, personal and cosmetic.

**Use for:** Home, always visible. Never on a leaderboard, and never paid against.

**States:**

- **Active:** white flame, number in `display` and `text-primary`, a subtle pulse.
- **Extended today:** the flame bursts once and the number flips up in `success`.
- **Broken:** a cracked, outlined flame and the number in `text-tertiary`, with *"Your 14-day streak broke. Start a new one today."* There is no save button because there is no grace day.
- **Personal best:** the flame in `celebration` with a **PB** pill.
- **Milestone:** a larger flame in `text-primary`, ringed by a 2px `accent` circle, with one pulse of 900ms when it appears. It sits above the headline *"{N}-day streak"* (`title`, `text-primary`) and one line in `text-secondary`; the copy set is under **Real copy, by moment** in the brand book. With reduced motion or animations off there is no pulse: the same ring, static. It shows on the day-complete screen when the day takes the streak to 7, 14, 21, 28, 30, 50, 60, 90, 100, every further 100 days or 365, and on Today for the rest of that day; the next day it is the plain flame again. It is part of the page; nothing to dismiss, and it unlocks nothing.

**Never** conflate it with the Holdout streak (Holdouts of one length finished in a row), which lives on-chain and resets on a forfeit. The daily streak survives a forfeit.
