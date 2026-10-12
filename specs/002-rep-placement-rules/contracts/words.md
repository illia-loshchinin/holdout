# Contract: What the member is shown and told

Source of truth is the code named beside each group; this list is for review and for the tour and stake sheet in `specs/001-holdout-hackathon` (T042, T059), which must reuse the same sentences.

## The rule sentence — one per level, word for word, everywhere (FR-012, FR-014; amended 2026-10-11)

Members read the two staked levels as **Spark · 7-day** and **Habit · 21-day**; `Easy`, `Hard` and `staked` are the code's internal names and are never member copy. Each level has one rule sentence, held once in `kotlin/repcounter` and read by `Format.levelRule`:

**Spark · 7-day** — `Stakes.EASY_RULE`:

> At 45° only. Aim for full push-ups from your toes; push-ups from the knees still count.

**Habit · 21-day** — `Stakes.STAKED_RULE`:

> At 45° only: phone on the floor ahead of your shoulder. Full push-ups from your toes — push-ups from the knees don't count.

Each is shown on the stake sheet of that level (001 T059, T228), and on the pre-flight page of a sitting in that Holdout *(2026-10-12: no longer on the set-up guide, whose Rules tab lists the sitting's own rule lines, below)*. A Spark screen never shows the Habit sentence, nor the reverse. The tour states the knee rule on card 1 ("How reps are counted") and the placement on the counting card (card 4 of 5), both in its own words, not by quoting a sentence (FR-013). The Daily 25 shows the caption "counted as the Spark" instead of a level sentence (FR-014). The hips-resting rule (FR-008, 2026-10-04) is applied by the counter and is not part of either sentence. *(Until 2026-10-11 one sentence, "Staked Holdouts are counted at 45° …", served every Holdout.)*

## Refused placement (FR-004) — `Stakes.cameraFix`, `Preflight`

Every sitting is at 45° *(since 2026-10-08; the Daily 25 said "Put the phone in front of you, or at 45°" until then)*. The band's front edge is about 35° for a Hard Holdout (members read Habit · 21-day) and about 40° for an Easy Holdout (Spark · 7-day) and the Daily 25.

- "Turn the phone to 45°" — square head-on, where no side of the body is nearer the phone
- The phone a little too far in front *(added 2026-10-02 for staked; every sitting since 2026-10-08)*: "The phone is too far in front of you: turn your body slightly to your left (right), away from it" — the coach says "Turn slightly left" or "Turn slightly right"
- The phone beside the member: "The phone is too far round to your side: turn your body slightly to your right (left), towards it" — the coach says the same two words

## Getting into position (FR-018, FR-019) — `Coach`, in priority order, shown large and spoken

More light · Place your phone · Get in view · Phone to 45° · Get down · Turn slightly left / right / away · Further · Closer · Move left · Move right · Arms straight · Knees up · Can't see knees · Body straight · Hold

"Turn slightly left", "right" and "away" *(added 2026-10-10, FR-018, FR-024a)* sit after "Get down": they turn the body so the phone sees the near knee (the Knees visible pill's fixes below), and are shown only while the Whole body in frame pill passes; while it fails, "Further" comes first. "Turn slightly left" and "right" are also said earlier, with the phone's placement, when a staked phone stands a little too far in front of or beside the member (the Camera view pill's turn fixes).

The last four before "Hold" *(added 2026-10-02)* come only once every pill is green: they say what the position itself still lacks, and each has its sentence under it (`HoldBlocker.fix`):

- "Straighten your arms: the hold starts from the top of a push-up"
- "Lift your knees off the floor: a staked push-up starts from your toes" — Hard only
- "The phone cannot make out your knees. Loose trousers hide them: wear something fitted, or roll them up" — every sitting *(the Daily 25 since 2026-10-08)*: the model has put the knee on the ankle
- "Bring your body into one line from shoulders to feet"

A moving background is not a correction: its pill turns amber with "Find a spot with nothing moving behind you", and the countdown still starts.

## Refused reps — `RepHint`, shown and spoken

Every refused rep's reason is said aloud at once *(2026-10-04; it was said only when the same reason came twice in a row)*. A rep refused with no reason (too slow) says nothing.

- "Go lower" — too shallow
- "Keep your body straight" — folded, a cobra, or *(2026-10-02)* pushed up from lying on the floor: the chest came up first and the hips after
- "Slow down" — too fast
- "Knees off the floor" — Hard only
- "Keep your legs in the picture" *(was "…your knees…" until 2026-10-02)* — every sitting *(the Daily 25 since 2026-10-08)*: the near knee unseen, unreadable or at the picture's edge, or the foot slipping out of it, on the push or on the way down
- *(nothing)* — a rep done on the back or standing up *(added 2026-10-07, FR-008a)*: there is nothing to coach, so nothing is said
- "Hips off the floor" *(added 2026-10-04)* — staked only: the hips or thighs rested on the floor at the bottom, carrying weight, and the body was pushed back up. Resting, not a chest brushing the floor. **Not said yet**: no check can see it until the knee-on-the-floor image classifier judges the hips too; until then such reps count (`angled40-right-floor-20`), and the rule sentence says they do not

## The set-up guide (FR-015, FR-015a) — `PlacementGuide.kt`, three tabs *(rewritten 2026-10-12)*

Title "Before you get down", the sitting's level at its right; tabs **1. Positioning · 2. Rules · 3. Start**; no intro paragraph, so "Two things decide whether a rep counts…" and "From there it sees all of you, and the knee nearer to it." (`GUIDE_HEADLINE`, 2026-10-10) are gone. Buttons: Skip (Back from the second tab) and Next (Got it on the last); "Don't show this again".

- **Positioning**: "Phone on the floor, leaning on something, facing where you'll be." · the "At 45°" card (animated top view) with "Ahead of your shoulder, about 1.5 m away." · "Top of a push-up, hands under your shoulders, turned a little away: it sees you head to feet and your near knee." · "Line up with the outline on screen."
- **Rules**, by form: Habit — "Full push-ups from your toes, down to just above the floor." · "Knee push-ups, or hips resting on the floor, don't count."; Spark — "Aim for full push-ups from your toes." · "From the knees still counts, body straight from the knees."; Daily 25 — "From your toes or your knees — both count."; every sitting — "All the way down and up — short reps are skipped.", then the card **"What you'll hear and see"** (`sittingCues`): beep — a rep counted · "10 banked" — those 10 are saved · "Go lower" — not deep enough, not counted · "Keep your body straight" — hips sagged or piked, not counted · "Slow down" — too fast to read, not counted · "Knees off the floor" — a knee touched, not counted (Habit only) · "Keep your legs in the picture" — the phone lost your knees or feet · "Done" — the day is complete. The refused-rep words are `RepHint`'s.
- **Start**: "Hold the top for a second. A ring fills, then “three, two, one, go”." · "Nothing to tap. Each rep beeps." · "Every 10 push-ups are saved on your phone." · "Stop any time with ✕ in the corner."

## Pre-flight, first correction (FR-018, FR-019, FR-020b) *(added 2026-10-12)*

- "Place your phone" (was "Stand phone up", `Coach.AIM_AT_FLOOR`) with "On the floor, ahead of your shoulder, turned to you at 45°." under it (`SittingCopy.PLACE_PHONE`); spoken once, like "Hold". The camera outline is hidden until the phone stands, then is the dashed box alone; the coach's words have a soft shadow. No "Under 10 minutes left. Unfinished reps won't count." sentence on the pre-flight or counting page (001 FR-047).

## The tour's counting card (FR-013) *(amended 2026-10-12)*

- **Card 1** ends — under a hairline, after "Each day done grows your streak." — with the heading "How reps are counted" and the knee rule: "The Daily 25 and Spark: aim for your toes; knees are allowed." / "Habit: full push-ups from your toes." (moved from card 4, 2026-10-12, board 2c v225).
- **Card 4** has no heading: the figure, its three steps — "Phone on the floor, at 45°, ahead of your shoulder." · "Get into a push-up: head to feet, knees included, inside the outline." · "The checks tick green. Hold the top for a second, then 3 · 2 · 1 and every rep counts." — the "Private by design" note and, under it, a second green note with a chain mark, "Fair by design": "A pose model on the phone (Google MediaPipe) tracks your body; Holdout's rules, tuned on labelled sets, count the reps. Each day you finish goes to Solana, where the program holds the stakes and pays out by rules anyone can check."

- Card 4 of 5 shows the "from above" panel (the guide's "At 45°" top view **held still** at the bottom of the push-up with the elbows bent, about 120 × 74 dp, left of the rep count) labelled "~1.5m ahead of your shoulder" and marked with two "45°" labels, one beside the elbow (the upper arm's angle with the body) and one at the phone, and the note's line "No microphone, ever. Reps are counted on this phone — no video or body data leaves the app."

## Framing (FR-024, FR-010) — the Whole body in frame pill, before the hold

- "Keep your head in the picture" — the head at or past an edge
- "Keep your feet in the picture — move the phone further away" *(added 2026-10-02)* — every sitting: the near foot within 6 % of an edge
- While counting nothing is said about the head *(revised 2026-09-29)*

## Knees visible (FR-024a) — its own pill between Whole body in frame and Camera view, blocking *(added 2026-10-10; before it these lines sat under the whole-body check)*

Three fixes, `Preflight.KNEES_FIX`, `KNEES_TURN_LEFT_FIX`, `KNEES_TURN_RIGHT_FIX` and `KNEES_TURN_FIX`; every sitting *(the Daily 25 since 2026-10-08)*:

- "Keep your knees in the picture — move the phone further away" — the near knee at or past an edge
- "Turn your body slightly to your left (right), away from the phone, so it sees your knees" — the near knee hidden behind the body and the side facing the phone known; the coach says "Turn slightly left" or "Turn slightly right" by the side the phone is on
- "Turn your body a little away from the phone, so it sees your knees" — the near knee hidden and that side not known, or no picture pose in the frame (the pill is red, never green); the coach says "Turn slightly away"

The coach ranks the two turn fixes below a failing Whole body in frame pill, so a body partly out of the picture says "Further" (or the head's fix) first.

## Tip sheet (FR-020a) — the pre-flight page, every sitting kind *(added 2026-10-12)*

- **"Still not green? Tips"** — a text button under the fix sentence, shown from the moment the checks have been short of all green for 30 seconds in total in one sitting, or a check that was green has turned red for the third time (whichever is first), until the sitting ends; it never opens the sheet by itself
- **"Getting the checks green"** — the bottom sheet's title; one row per pill in the pills' order (Lighting · Whole body in frame · Knees visible · Camera view · Background still), each the pill's label and that check's own fix sentence from the sections above — no new per-check copy; a row whose check is red now is marked in the warning colour, with "Not green yet" for screen readers
- **"Open the set-up guide"** — reopens the guide for this sitting's stakes, as the page's link does; **"Back to the checks"** — closes the sheet. Back, a tap outside and a drag down close it too; the checks keep running underneath

## Feedback (FR-021)

- A short tone and a rising "+1" per counted rep; the banked chime instead on every tenth.
