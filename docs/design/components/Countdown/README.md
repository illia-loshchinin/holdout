# Countdown

Time remaining, never a clock time or a date. Everyone shares one day boundary, 05:00 UTC.

**Use for:** time left today, *joining closes in*, *starts in*, faucet *available again in*.

**Anatomy:** `amount` (JetBrains Mono 20px, tabular) in `accent`. Inside the last 10 minutes of the day it turns `warning`, and the start card adds a sentence saying unfinished reps won't count. Formats: `3d 4h`, `4h 06m`, `12m 30s` (`Format.countdown`).

**Don't:** show seconds above an hour, show a local time, or ever block a sitting because time is short.
