# Secondary button

The second-best path, sitting under a primary button: same pill shape, transparent, with a `hairline` border.

**Use for:** the alternative that is still a real choice, such as *Do today's Daily 25* under the comeback's **Join** on the forfeit screen, or *See the lobby* under *Do today's Daily 25* on You're in.

**Anatomy:** transparent fill, `hairline` border at `size-border`, `text-primary` label in `title`, height `size-button`, `radius-pill`. Pressed fills `surface-3`. Focus is a 2px `accent` ring.

**The consumer provides:** label and action.

**Note:** `hairline` is only 1.3:1 against `surface-1`, so the white label is what tells you this is a button. Never ship it label-less or icon-only. Compose: `SecondaryButton(text, onClick)`.
