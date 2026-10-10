# Readouts

What the counter is reading, drawn under the live count during a Holdout Lab set. Never in a member build: the build check fails any member app that contains it.

**Use for:** Holdout Lab's set screen only, under the count, unless *Clean screen* is on.

**Anatomy:** three short lines in `label` style and `text-tertiary`, not uppercased: `rec · <trace name>`; `analysis 27 fps · video on`; `side · easy · elbow 152° · line 161° · knee 118°` (the detected view, the rules in force, the three angles; `knee —` when unseen). Two things turn a line `warning`: `rec · not saved` when the trace could not be written, and the analysis line under 15 fps. When the camera refuses a third use case the analysis line ends `· video could not start`.

**The consumer provides:** the trace name or its failure, the analysis rate, the video state and the current reading.

**Don't:** show readouts to a member, draw them over the camera without the scrim, or let them push the count or its caption out of place.
