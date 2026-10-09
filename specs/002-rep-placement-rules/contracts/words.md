# Contract: What the member is shown and told

Source of truth is the code named beside each group; this list is for review and for the tour and stake sheet in `specs/001-holdout-hackathon` (T042, T059), which must reuse the same sentences.

## The staked rule — one sentence, everywhere (FR-012, FR-014)

`Stakes.STAKED_RULE`:

> Staked Holdouts are counted at 45°: stand the phone on the floor ahead of your shoulder, turned to look at you at about 45° — not in front, not straight beside you — and do full push-ups from your toes. Push-ups from the knees, or with the hips resting on the floor at the bottom, do not count.

Shown today in the set-up guide for staked sittings. Must appear on the stake sheet (001 T059) and the tour's counting card (001 T042).

## Refused placement (FR-004) — `Stakes.cameraFix`, `Preflight`

Every sitting is at 45° *(since 2026-10-08; the Daily 25 said "Put the phone in front of you, or at 45°" until then)*. The band's front edge is about 35° for a Hard Holdout and about 40° for an Easy Holdout and the Daily 25.

- "Turn the phone to 45°" — square head-on, where no side of the body is nearer the phone
- The phone a little too far in front *(added 2026-10-02 for staked; every sitting since 2026-10-08)*: "The phone is too far in front of you: turn your body slightly to your left (right), away from it" — the coach says "Turn slightly left" or "Turn slightly right"
- The phone beside the member: "The phone is too far round to your side: turn your body slightly to your right (left), towards it" — the coach says the same two words

## Getting into position (FR-018, FR-019) — `Coach`, in priority order, shown large and spoken

More light · Stand phone up · Get in view · Phone to 45° · Get down · Further · Closer · Move left · Move right · Arms straight · Knees up · Can't see knees · Body straight · Hold

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

## Framing (FR-024, FR-010) — the whole-body check, before the hold

- "Keep your head in the picture" — the head at or past an edge
- "Keep your knees in the picture — move the phone further away" — every sitting *(the Daily 25 since 2026-10-08)*: the near knee at or past an edge
- "Keep your feet in the picture — move the phone further away" *(added 2026-10-02)* — every sitting: the near foot within 6 % of an edge
- "Turn your body slightly to your left (right), away from the phone, so it sees your knees" — every sitting: the near knee hidden behind the body; the coach says "Turn slightly left" or "Turn slightly right" by the side the phone is on ("Turn slightly away" when that is unclear)
- While counting nothing is said about the head *(revised 2026-09-29)*

## Feedback (FR-021)

- A short tone and a rising "+1" per counted rep; the banked chime instead on every tenth.
