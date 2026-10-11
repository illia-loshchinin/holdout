# Contract: What the member is shown and told

Source of truth is the code named beside each group; this list is for review and for the tour and stake sheet in `specs/001-holdout-hackathon` (T042, T059), which must reuse the same sentences.

## The rule sentence — one per level, word for word, everywhere (FR-012, FR-014; amended 2026-10-11)

Members read the two staked levels as **Spark · 7-day** and **Habit · 21-day**; `Easy`, `Hard` and `staked` are the code's internal names and are never member copy. Each level has one rule sentence, held once in `kotlin/repcounter` and read by `Format.levelRule`:

**Spark · 7-day** — `Stakes.EASY_RULE`:

> At 45° only. Aim for full push-ups from your toes; push-ups from the knees still count.

**Habit · 21-day** — `Stakes.STAKED_RULE`:

> At 45° only: phone on the floor ahead of your shoulder. Full push-ups from your toes — push-ups from the knees don't count.

Each is shown on the stake sheet of that level (001 T059, T228), and on the set-up guide and the pre-flight page of a sitting in that Holdout, after FR-015's opening lines. A Spark screen never shows the Habit sentence, nor the reverse. The tour's counting card (card 3 of 4, FR-013) states the placement and the knee rule in its own words, not by quoting a sentence. The Daily 25 shows the caption "counted as the Spark" instead of a level sentence (FR-014). The hips-resting rule (FR-008, 2026-10-04) is applied by the counter and is not part of either sentence. *(Until 2026-10-11 one sentence, "Staked Holdouts are counted at 45° …", served every Holdout.)*

## Refused placement (FR-004) — `Stakes.cameraFix`, `Preflight`

Every sitting is at 45° *(since 2026-10-08; the Daily 25 said "Put the phone in front of you, or at 45°" until then)*. The band's front edge is about 35° for a Hard Holdout (members read Habit · 21-day) and about 40° for an Easy Holdout (Spark · 7-day) and the Daily 25.

- "Turn the phone to 45°" — square head-on, where no side of the body is nearer the phone
- The phone a little too far in front *(added 2026-10-02 for staked; every sitting since 2026-10-08)*: "The phone is too far in front of you: turn your body slightly to your left (right), away from it" — the coach says "Turn slightly left" or "Turn slightly right"
- The phone beside the member: "The phone is too far round to your side: turn your body slightly to your right (left), towards it" — the coach says the same two words

## Getting into position (FR-018, FR-019) — `Coach`, in priority order, shown large and spoken

More light · Stand phone up · Get in view · Phone to 45° · Get down · Turn slightly left / right / away · Further · Closer · Move left · Move right · Arms straight · Knees up · Can't see knees · Body straight · Hold

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

## The set-up guide's headline (FR-015) — `GUIDE_HEADLINE`, on every page of the guide

- "Two things decide whether a rep counts: your whole body in the picture, head to feet, and your knees clearly visible to the phone." *(added 2026-10-10)* — under the guide's title, before anything else on each page. The phone page then says "From there it sees all of you, and the knee nearer to it."; the body page opens with the whole-body and knees lines.

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

## Feedback (FR-021)

- A short tone and a rising "+1" per counted rep; the banked chime instead on every tenth.
