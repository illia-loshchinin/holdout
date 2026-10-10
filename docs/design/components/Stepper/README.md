# Stepper

A small whole number set one tap at a time: a `−`, the value, a `+`.

**Use for:** the target reps on Holdout Lab's first screen (1–100, default 25) and the five movement counters in its label dialog. Nothing a member sets is a number: tiers are chips, reps are counted.

**Anatomy:** two 48px round touch targets with the signs in `amount` style and `accent`, the value between them in `amount` style and `text-primary`, at least 40px wide so two digits never shift the buttons. At a bound the button stays visible and does nothing. Accessible names say what moves: *Fewer reps* / *More reps*.

**The consumer provides:** the value, its bounds and the step (always 1 here).

**Don't:** let the value be typed, hide a disabled sign, or use a stepper where a slider's range matters more than the exact number.
