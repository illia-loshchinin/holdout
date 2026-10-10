# Bottom nav

The app's only chrome, mirrored from the Seeker system UI: four labelled tabs, **Today · Standing · Holdouts · Me**.

**Tabs:**

| Tab | Root | Holds | Dot when |
| --- | --- | --- | --- |
| **Today** | Today's action | The Daily 25 or today's reps in your Holdout, plus Holdouts open to join | today's reps are not done |
| **Standing** | Your running (or last) Holdout | Still standing, the prize pool, how the crowd thinned, your record, the roster, and the leaderboards. Empty: the running Holdouts you could watch, then *"Join one to have a crowd"* | people dropped since you last looked |
| **Holdouts** | My Holdouts | Stake sheet, lobby, forfeit, claim, finished Holdouts | a claim is waiting |
| **Me** | Wallet | Address, SKR and SOL, the faucet, streak, About Holdout | SKR or SOL is too low to stake or submit |

**Anatomy:** a `size-nav` bar on `surface-2` with a `hairline` top edge. Each tab is a 56×32 pill holding a 24px icon, with a 12px/500 label under it. The active tab's pill is `inverse-surface` with an `on-light` icon and a `text-primary` label. Inactive icons and labels are `text-tertiary`. A badge is an 8px `accent` dot on the pill's corner, never a count, and its meaning is also in the tab's accessible name.

**Icons:** house · pulse · list · person, drawn here as placeholders. Swap them for the chosen icon set (see Iconography).

**Rules:** the bar shows on tab roots only. The sitting and first run are full screen with no bar, because the phone is on the floor. The app opens on **Today**, including from a reminder.

**Don't:** drop the labels, show a number in a badge, or add a fifth tab.
