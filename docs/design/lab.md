# Holdout Lab

Holdout Lab is the team's tester app: it records and labels push-up sets so the counting can be tuned, and it ships nothing to members. It installs beside Holdout on a tester's phone, needs no wallet and no network, and is never published, linked or shown to a member. Everything below exists so a tester always knows which app is open, and so the Lab boards can be drawn in the same hand as the member app's.

## Same system, one marker

Lab uses the same tokens, type, components and the same sitting screen as the member app, so what the team tests is what members see. It adds one thing: a **LAB marker**.

| Where | What |
| --- | --- |
| Launcher | Its own icon: the same 5×5 crowd as Holdout's, with the H of seven in `warning` (coral) instead of `accent`. The one exception to the brand rule that the H is only ever `accent`. Its themed-icon layer is the whole crowd with the H in one ink, so a themed home screen tells it from Holdout's H alone too, and the Android 12+ splash plays the coral H. The status-bar icon stays Holdout's H alone. |
| App name | **Holdout Lab** |
| First screen | The wordmark **Holdout** with a `LAB` status chip (`accent` text) on the same line. Decided, not built yet: Choose still shows a plain *Holdout Lab* headline (Lab screen map, L0) |
| Settings | Every tester switch label starts `TEST ·`; `accent` when on, `text-tertiary` when off (Switch) |
| During a set | The Readouts under the count. A member never sees them; they are also the marker |

## Words

The tester voice is the coach's, without the selling: plain, technical, second person. Say **set**, not sitting; name the **trace** and the **video** as such; say what was lost and what to do, never who is to blame. The consent sentence is read word for word on the video switch: *only people who agreed in writing to be filmed*.

Lab's own screens: **Choose** (*Holdout Lab* · *Record and label sets to tune rep counting. No wallet and no network: nothing here is staked or sent.*), the set, **What did you just do?** and **This set was not saved**. The three counting modes are rows, and their lines say that the Daily 25 and the 7-day count the same way and the 21-day counts more strictly: **Daily 25** · *45° · knees allowed · counted as the 7-day*; **Easy Holdout** · *7-day · 45° · knees allowed*; **Hard Holdout** · *21-day · 45° · from the toes · the stricter count*.

## Components Lab adds

Switch, Stepper, Readouts and Dialog (Lab only: the member app has no dialog); Choice chips, Notice card and Status chip (shared with the member app). Each has its own page.

## Where the screens are described

`docs/holdout_lab_screen_map.md` in the repository: every screen, its content as built, the use cases and the journeys. The member app's map is `docs/holdout_screen_map.md`.
