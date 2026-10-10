# Switch

A tester setting in Holdout Lab, labelled for what it does when on and when off. Never in the member app.

**Use for:** *Record video* and *Clean screen* on Holdout Lab's first screen. Nothing a member uses is a switch: members have no settings.

**Anatomy:** a 44×24 track (border included) on `surface-3` with a `hairline` border and an 18px knob centred 2px inside it in `text-secondary`; on, the track turns `accent` and the knob `on-light`. The text is `label` style and starts `TEST ·`; it changes with the state and turns `accent` when on, so the state is read, not inferred. The whole row is one 48px touch target.

**The consumer provides:** both labels. The video switch's labels both end with constitution X's words, *only people who agreed in writing to be filmed*: on, *TEST · recording sets as video — only people who agreed in writing to be filmed*; off, *TEST · video off — only people who agreed in writing to be filmed*. The clean-screen switch: on, *TEST · clean screen: no dots or text, for training recordings*; off, *TEST · clean screen off*.

**Don't:** show a switch without the `TEST ·` prefix, use one for a member-facing choice, or remember the video switch as on across installs (it starts off on every fresh install).
