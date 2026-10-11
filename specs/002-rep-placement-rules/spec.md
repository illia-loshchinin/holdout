# Feature Specification: Rep Placement and Form Rules

**Feature Branch**: `002-rep-placement-rules`

**Created**: 2026-09-24

**Status**: Implemented for recognition (2026-09-26); the staked flow it guards — stake sheet, tour card — lands with `specs/001-holdout-hackathon` T042, T059, T065

**Input**: User description: "Update the existing feature spec (specs/001-holdout-hackathon/spec.md) for rep verification placement and form rules, decided 2026-09-23/24 from two Seeker recordings: (1) Daily 25 accepts the phone head-on or at ~45°; staked Holdouts accept ~45° only; square side-on is accepted by neither. (2) Head-on verifies depth, lockout and tempo only (elbow read from the picture, torso-is-down check instead of body line); knee push-ups and sagging hips are not detected head-on and the app says so. (3) At 45° the elbow, body line and knee are read in the picture on the side nearer the lens; a push-up from the knees is discarded with "Up on your toes, knees off the floor" (knee threshold 150°, provisional until an honest 45° set is recorded). (4) The staked rule is stated in one sentence before staking (stake sheet) and again by the camera check; view cut-off head-on at shoulder-span/torso ratio 1.55. (5) Sitting UX: a set-up guide dialog (Phone / Body / Start tabs, "Don't show this again" per wallet and stakes) opens when a sitting starts while the member still holds the phone, and the countdown cannot start behind it; while getting into position one short correction (≤3 words: Closer, Further, Phone to 45°, More light, Get down, Hold…) is shown large and spoken; each counted rep plays a short tone and a "+1" animation."

## Context

This feature amends how a sitting is verified in `specs/001-holdout-hackathon` (FR-019, FR-021, FR-021a, US-6, FR-067). It records decisions taken on 23–24 September 2026 after two screen recordings of real sittings on a Solana Seeker:

- **Recording 1 — head-on, two honest push-ups.** Nothing was counted. Everything the rep rule measures happened along the line of sight, which the pose model estimates worst: the arm never read as straight at the top or as bent enough at the bottom, and the body line of one honest plank read anywhere between 146° and 177°.
- **Recording 2 — 25 reps, including cheats.** Push-ups from the knees, at an angle and head-on, and knees-down rocking with the hips pushed back, were all counted. Head-on, knees bent on the floor read as 146–164° against an honest plank's 156°; no measurement separated them. At about 45°, read on the side of the body nearer the camera, the same knee push-ups bottomed out at 74–102° at the knee.

## Clarifications

### Session 2026-09-24

Decided from sittings recorded by the app itself on a Solana Seeker (six traces, now test fixtures).

- Q: Do push-ups from the knees count in the Daily 25? → A: Yes, in either placement — it is a free habit for building strength. Knees are judged only in staked sittings, which are always at 45°: a rep whose median knee angle is below 140° is discarded with "Knees off the floor", and the ready position must be held from the toes.
- Q: How is depth judged head-on, where the elbow angle over-reads the bend? → A: The shoulders must also come down to at most a quarter of their height above the hands at the top (honest reps 0.16–0.19; half push-ups 0.26–0.53, three of which the elbow alone had counted).
- Q: What counts as the top of a rep at 45°, where straight arms read 158–163°? → A: The rule's own 160°, instead of the stricter 165° used head-on; a rep still needs a full bottom to count. *(Revised 2026-10-01, product owner, from two labelled sets at 45° and 50° measured on the floor: a locked-out arm reads 150–158° there and both sets counted 0 of 10. The top at 45° is now **the member's own**: the arm held through the countdown, less 10°, never under 148° and never over 160°. A flat 148° was tried first and refused: from more frontal spots, where the arm reads 167–173°, it lost honest reps to the body-line check.)*
- Q: How is the body line judged at 45°, where honest planks read 152–167°? → A: On the rep's median, and only a fold below 140° is refused; the ±10° rule cannot be measured there and is a stated weakness.
- Q: When does a rep's time start? → A: When the arms actually start down; resting at the top is not time spent in the rep.
- Q: Push-ups from the knees with the shins flat on the floor still counted at 45° (knee angle 124–139° on the push, too close to honest 139–160°) — how are they caught? → A: By how high the near knee and ankle stand above the hand, which is on the floor, measured on the push: honest 0.44–0.65 arm-lengths, knees 0.32–0.39; a rep below 0.41 is knees-down. The knee angle stays as a second signal at 135°, for a deep kneel.
- Q: How is depth judged at 45°, where some half push-ups read 55–67° at the elbow, as deep as honest ones? → A: The near shoulder must also come down to 0.32 arm-lengths or less above the near hand at the bottom: honest 0.20–0.28, half push-ups 0.43–0.68.
- Q: Does a "cobra" count — lying flat, then pressing the chest up with the hips left on the floor? → A: No, in the Daily 25 or staked: it is not a push-up. At 45° the body line is judged on the push back up (from the bottom, once the elbow is past 100°): its median must be at least 145°. On the phone the push read 157–174° on honest reps and 120–134° on cobra reps, which had counted because the flat part of the rep hid the arch in the whole-rep median.
- Q: How are thresholds kept honest as they are tuned? → A: Debug builds record one trace per sitting — landmarks and the counter's decisions, never a picture; committed traces that carry a person's count replay as tests and must count within one rep of it. *(2026-10-09: now in Holdout Lab, not debug builds — FR-023, FR-025.)*

### Session 2026-09-25/26

Decided from eleven more labelled phone traces (seventeen in all), with the phone on the member's left and right.

- Q: Honest reps with the phone on the member's right read 99–125° at the knee and were refused as knee push-ups — where is the knee-angle line? → A: 90°. A deep kneel or raised feet read 40–98°; the knee-and-ankle height rule (0.41 arm-lengths) still catches knees down with the feet flat. Swept on all seventeen traces: no honest rep refused.
- Q: The head dropped out of frame at the bottom of reps with the phone set too close — what then? → A: The head (the pose model's nose point) becomes a tracked point. The camera check requires it in view before counting; while counting, a head out of the picture for half a second shows "Head out of view · move back" large and says "Move back" at most every 5 seconds. Reps are not thrown away for it. *(Revised 2026-09-29, product owner: the head is required **clearly inside the picture to enter** — the whole-body check, which the ready hold and the countdown need — and is **ignored while counting**: no warning, no effect on the count.)*
- Q: *(2026-09-29)* A staked set was done with the knees out of the camera and all five reps counted — the model put the knees just inside the edge at full confidence. What then? → A: A knee at the picture's edge is a guess, not a knee seen. A staked rep whose near knee spends over a quarter of the push back up within 13 % of an edge (or unseen) is refused with "Keep your knees in the picture", said aloud the first time; the staked start needs the near knee clear of the edges too.
- Q: How does a trace get its ground truth? → A: In debug builds, pressing Stop (or finishing the day) opens a form asking what was really done — full, half, from the knees, cobra, other, and a note — and writes it into the trace's header before the member leaves. The expected count is full push-ups, plus knee push-ups in the Daily 25. *(2026-10-09: now in Holdout Lab, not debug builds — FR-023, FR-025.)*
- Q: Should a moving background keep a member from starting? → A: No. It is measured (the share of background cells that changed between frames, the member's own box and whole-frame exposure shifts left out) and shown as an amber pill with its fix, but it never blocks the countdown: the threshold has not been tried in real homes, and a TV or a passer-by must not stop a set. It is not one of the coach's corrections.
- Q: A staked push shows no knee at all — pass or refuse? → A: Refuse, with "Keep your legs in view": a knee the camera never saw is not a knee off the floor. The staked ready position needs the knee in view too.
- Q: Should a small image classifier decide "knee on the floor" instead of the landmark rules? → A: Yes, eventually, and not now. It needs recordings from several people; until then the landmark rules stand, stated as measured on one person. A debug "clean screen" switch — nothing drawn over the camera — exists so screen recordings can be used as training data later.

### Session 2026-10-04

- Q: A push-up "that touched the floor does not count" (2026-10-02) — does a chest brushing the carpet at the bottom count as touching? → A: No. What does not count is **resting**: the hips or thighs carrying weight on the floor at the bottom, then pushed back up (`angled40-right-floor-20`, four reps that counted). A brushed chest is hard to see from 45° and many honest deep reps graze the floor. The rep is refused with "Hips off the floor", and the staked rule sentence says so. No check can see it yet: until the knee-on-the-floor image classifier judges the hips, such reps still count.

### Session 2026-10-08

Decided by the product owner on 7–8 October, after three sittings recorded on video on 7 October (two Daily 25, one staked, all at 45°).

- Q: Should the Daily 25 keep the head-on placement? → A: **No. The Daily 25 is counted at 45° only, in the same band as an Easy Holdout** — no more frontal than about 40° (placement ratio under 0.90), not square side-on — with the same checks, and push-ups from the knees still count: **the Daily 25 is an Easy Holdout with nothing staked.** It is the practice ground for staked Holdouts, so it rehearses their set-up every day. Head-on could not see what staked sittings refuse — knees, a sag, a cobra, hips on the floor — so the Daily 25 counted there what a Holdout would not, and taught the wrong habit; it was also a second way of measuring (both elbows, shoulder height, torso angle) with its own thresholds and fixtures, tuned for the free tier alone. At 45° every Daily 25 sitting is a recording of the placement money rides on. The price is more floor (about 1.5 m) and a harder set-up than a phone leant up in front.
- Q: At 45°, crunches and a kettlebell held standing counted as push-ups in the Daily 25 (sixteen crunches and two stand-ups among seven push-ups in one set; three crunches at the end of another) — how? → A: The arms bent and straightened, and lying on the back with the knees up, or standing, reads the body line straight; nothing checked that the body was face down, and the Daily 25 does not judge knees. **A rep is now refused in silence when the body is not face down** (FR-008a). Staked sittings had refused the same movements on their knee checks.
- Q: Does the Daily 25 also hold Easy's leg checks — no start until the near knee and foot are clearly in the picture, and reps refused with "Keep your knees in the picture" when the knee leaves it? → A: **Yes, exactly as an Easy Holdout.** The Daily 25 rehearses the whole staked set-up, legs in view and fitted clothing included. The cost is accepted: in loose trousers that hide the knee, a Daily 25 does not start until they are changed or rolled up, and the member is told why (FR-018a).

### Session 2026-10-09

Product owner decision with spec 004 (Holdout Lab and the member app) and constitution v2.1.0.

- Q: Where do traces, labelling, video and the tester switches live, now that the member app must hold only what members use? (FR-023, FR-025, FR-026) → A: **In Holdout Lab, a separate tester app with no wallet and no chain, installed only on the team's and consenting testers' phones.** It counts every set exactly as the member app counts it, from the same rules. No build of the member app, developer or release, records a trace or video, asks for a label or shows a tester switch, and a build check fails any member-app build that contains the recorder. Earlier answers that say "debug builds" now mean Holdout Lab.

### Session 2026-10-10

- Q: Should seeing the knees be its own pre-flight pill, now that "Whole body in frame" hides the most common failure inside it? (FR-018, FR-024a) → A: **Yes.** The pills are **Lighting · Whole body in frame · Knees visible · Camera view · Background still**. *Knees visible* holds the near-knee checks that *Whole body in frame* used to carry (the near knee found and clear of the picture's edges) and keeps their fixes; it blocks the start like the other three, and only *Background still* stays advisory. *Whole body in frame* keeps every joint present, the head inside the picture and the near foot clear of the edges. The valid plank stays the ready hold, not a pill (FR-018a). Decided by the product owner on the screen-map review.
- Q: What should the set-up guide say first, now that a fast reader sees only page 1, which is about the phone? (FR-015) → A: **The two things that decide whether a rep counts.** Under the guide's title, on every page: "Two things decide whether a rep counts: your whole body in the picture, head to feet, and your knees clearly visible to the phone." Page 1 adds after the 45° drawing "From there it sees all of you, and the knee nearer to it." Page 2 opens with the whole-body line and the knees line, then the position and the outline. Decided by the product owner on the screen-map review.
- Q: The Knees visible pill's requirement lists two fixes, but the built pill has three — which does it show, and what does it show when the frame carries no picture pose or the body is not whole in the picture? (FR-024a, FR-018) → A: **Three fixes, as built.** Turn left or right, away from the phone, when the side facing it is known; "Turn your body a little away from the phone, so it sees your knees" when it cannot be told (coach: "Turn slightly away"); "Keep your knees in the picture — move the phone further away" at an edge. A frame without the picture's pose is red with the second fix, never green. The coach ranks the knee turn fixes below a failing *Whole body in frame* pill, so a body partly out of the picture says "Further" (or the head's fix) first. Decided on the PR #43 review.
- Q: Where does the knee-turn correction ("Turn slightly left", "right" or "away") come in the coach's order, since FR-018's list does not name it? (FR-018, FR-024a) → A: **After "Get down" and before "Further" — and only while *Whole body in frame* passes.** While that pill fails, "Further" (or the head's fix) is the correction, because getting into view comes first (FR-018); the knee turn is shown once the body is whole in the picture and the near knee is still hidden, and then it comes before "Further" and "Closer". As built in `Coach.next`. Found by `/speckit-analyze` on PR #43.
- Q: Is the face-down check judged after every other body check, as FR-008a, research D11 and the data model say, or first, as the code does? (FR-008a, D11) → A: **First.** The 7 October sit-ups were each told "Go lower" or "Up on your toes" aloud because the checks that speak came first; since `6894c3c` (8 October, a reviewer's finding) `RepCounter.notFaceDown` is the first verdict, and an abandoned descent is told "Go lower" only if the rep is face down (`StakesTest`: the Daily 25 counts nothing done on the back or standing up, in silence; a face-down half rep is told to go lower). FR-008a, research D11 and the data model's verdict order now say so. Found by the PR #41 `/speckit-analyze` (F1); no behaviour changes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Daily 25 counts at 45°, the placement a Holdout is counted at (Priority: P1)

*(Revised 2026-10-07: head-on dropped — it was "Daily 25 counts from where people actually put the phone", head-on or at 45°.)*

A member doing the free Daily 25 stands the phone on the floor ahead of their shoulder, turned to about 45°, as they would for a Holdout, gets into a plank, and every full push-up counts — from the toes or from the knees.

**Why this priority**: Without it the free tier — the product's daily habit and first impression — counts nothing, which is what recording 1 showed. And it is where a member learns the set-up a staked Holdout will hold them to.

**Independent Test**: Replay a 45° honest set and a 45° set from the knees through the counter under Daily 25 rules; each counts its reps within one. Hold the phone head-on and start a Daily 25 sitting; it does not start.

**Acceptance Scenarios**:

1. **Given** a Daily 25 sitting with the phone head-on, **When** the camera check runs, **Then** it stays red and says to turn the phone to 45°.
2. **Given** a Daily 25 sitting with the phone at about 45°, **When** the member does full push-ups, **Then** each one counts.
3. **Given** a Daily 25 sitting with the phone square to the member's side, or turned more frontal than about 40°, **When** the camera check runs, **Then** it stays red and says which way to turn the phone, as for an Easy Holdout.
4. **Given** a Daily 25 sitting at 45°, **When** the member does crunches on their back, or stands and bends their arms, **Then** nothing counts and nothing is said.
5. **Given** a Daily 25 sitting at 45°, **When** the member does full-depth push-ups from the knees, **Then** each one counts.
6. **Given** a Daily 25 sitting at 45°, **When** the member does half push-ups, **Then** none counts and each is met with "Go lower".

---

### User Story 2 - Money is only staked on push-ups the camera can hold everyone to (Priority: P1)

A member in a staked Holdout films every sitting at about 45°, where a push-up from the knees is visible and discarded, so nobody's knee push-up takes somebody else's stake.

**Why this priority**: The fairness of every staked cohort depends on it. Recording 2 showed knee push-ups counting from the other placements.

**Independent Test**: Replay a 45° set of push-ups from the knees; none counts, and each attempt carries the hint to get up on the toes. Attempt to start a staked sitting head-on or square side-on; it does not start.

**Acceptance Scenarios**:

1. **Given** a staked sitting with the phone at about 45°, **When** the member does full push-ups from the toes, **Then** each counts.
2. **Given** a staked sitting at 45°, **When** the member does push-ups from the knees, **Then** none counts and the screen shows "Knees off the floor".
3. **Given** a staked sitting, **When** the phone is head-on or square side-on, **Then** the camera check stays red, says to turn the phone to 45°, and the sitting cannot start.
4. **Given** a member about to stake, **When** they see the stake sheet, **Then** it states in one sentence that Holdouts are counted at 45° only, with full push-ups from the toes, and that knee push-ups do not count.

---

### User Story 3 - Setting up without reading small print from the floor (Priority: P2)

A member starting a sitting is shown, while still holding the phone, where to put it and how to lie; once they are down on the floor, they are told the one thing to fix next in words large enough to read — and hear — from two metres away.

**Why this priority**: The previous guide was an outline with no explanation, and its instructions appeared over a member already in a plank who could neither read nor dismiss them.

**Independent Test**: Start a sitting; the set-up guide appears before the camera checks can start a countdown. Close it with "Don't show this again", start another sitting with the same stakes, and it does not appear. Stand too close to the phone; "Further" is shown large and spoken.

**Acceptance Scenarios**:

1. **Given** a member starts a sitting, **When** the screen opens, **Then** a set-up guide with three pages — the phone, the body, how counting starts — is shown before anything else, and no countdown can begin while it is open.
2. **Given** the guide, **When** the member ticks "Don't show this again" and closes it, **Then** it is not shown again for that wallet at those stakes, and can still be reopened from the sitting screen.
3. **Given** a member getting into position, **When** something needs fixing, **Then** a single correction of at most three words is shown large and spoken aloud, and it changes only when the next thing needs fixing.
4. **Given** everything is in place, **When** the member holds the top of a push-up, **Then** the screen says "Hold" and the countdown begins on its own.

---

### User Story 4 - Knowing a rep counted without looking (Priority: P3)

A member mid-set hears a short tone on every counted rep and, if they glance at the screen, sees the count jump with a "+1".

**Why this priority**: A member face-down cannot read the count; a sound per rep is their only proof it registered. Motivating, but the sitting works without it.

**Independent Test**: Count five reps; five tones sound and the count shows five "+1" effects; every tenth rep still plays the distinct banked chime.

**Acceptance Scenarios**:

1. **Given** a counting sitting, **When** a rep is counted, **Then** a short tone plays and a "+1" rises from the count.
2. **Given** a rep that completes a block of ten, **When** it is counted, **Then** the banked chime plays instead of the single-rep tone.

---

### Edge Cases

- The phone is placed near the front edge of the band (about 40°): the view must not flicker between accepted and refused while the member holds still at the top of a push-up; a correctly placed phone is never refused because of the boundary.
- The member does crunches on their back, or stands with a weight and bends their arms, in front of a counting phone: nothing counts and nothing is said, in every sitting *(2026-10-07)*.
- The member starts a staked sitting on their knees: nothing counts, and they are told why rather than met with silence.
- The member lies flat and presses only the chest up, hips on the floor ("cobra"), at 45°: nothing counts, with "Keep your body straight". On the phone, at most one cobra rep in a set of five has slipped through.
- The phone is set too close and the head leaves the picture at the bottom of a rep: counting goes on, and nothing is said — the head was checked on the way in *(revised 2026-09-29)*.
- A staked set — or, *since 2026-10-08*, a Daily 25 — is done with the knees out of the picture: each rep is refused with "Keep your knees in the picture", said aloud at once, until the member or the phone moves *(2026-09-29)*.
- Half push-ups at 45°: 7 of 11 had counted on the elbow alone; refused by the shoulder-over-hand depth rule (FR-008) since 2026-09-25.
- The side of the body nearer the camera is briefly hidden (a hand, the member's own arm): that moment is skipped, not scored from the far side.
- Speech is unavailable on the device: corrections are still shown large; tones still play.
- The member reopens the set-up guide after ticking "Don't show this again": it opens; the choice is not undone.
- A Daily 25 at 45°: a push-up from the knees counts. This is a product decision — the Daily 25 is a free habit for building strength — not a defect.

## Requirements *(mandatory)*

### Functional Requirements

**Placement**

- **FR-001** *(amended 2026-10-07; was head-on or about 45°)*: The Daily 25 MUST accept the phone at about 45° only, in the band an Easy Holdout accepts: no more frontal than about 40° (placement ratio under 0.90). Head-on is refused like any other placement the stakes do not accept.
- **FR-002**: A staked Holdout MUST accept the phone at about 45° only.
- **FR-003**: No sitting MAY accept the phone square to the member's side.
- **FR-004** *(amended 2026-10-07)*: When the phone is in a placement the stakes do not accept, the camera check MUST stay red and say where to put it — "Turn the phone to 45°", for every sitting — and, at the edges of the band, which way to turn (FR-008). *(Until 2026-10-07 the Daily 25 said "Put the phone in front of you, or at 45°".)*
- **FR-005**: The placement is judged while the member holds the ready position and MUST stay fixed for the rest of the sitting.

**What each placement verifies**

- **FR-006** *(retired 2026-10-07 with the head-on placement)*: No sitting is counted head-on. Until then, head-on a rep had to meet depth, lockout and tempo with the torso down rather than upright, depth judged by the shoulders coming down to 0.285 or less of their height above the hands at the top.
- **FR-007** *(amended 2026-10-07)*: **The Daily 25 MUST count push-ups from the knees** — a free habit a member can keep while building up to full push-ups — and MUST say so where it explains how reps are counted. In everything else it verifies what an Easy Holdout verifies (001 FR-021a): the body line, the hip rise, and *(since 2026-10-08)* the near knee and foot in the picture, on the way in and through every rep, as FR-010 states them for staked sittings.
- **FR-008**: At 45°, a rep MUST meet depth, lockout, tempo and a body not folded at the hips — and, in a staked sitting, knees off the floor (FR-010). Lockout at 45° is **the member's own held arm** *(revised 2026-10-01)*: how a locked-out arm reads depends on where the phone stands — 150–158° at 45–50° measured on the floor, 167–173° from a more frontal spot — so the top of a rep is the elbow held through the countdown less 10°, never below 148° and never above the rule's 160°; with no countdown to read, 160°. The ready hold accepts 148°. **Depth at 45°**: besides the elbow passing the bottom threshold, the near shoulder MUST come down to 0.32 arm-lengths or less above the near hand (or, *since 2026-10-02*, to 0.36 or less with the elbow closed to 66° or less) — honest reps reached 0.20–0.28, half push-ups stopped at 0.43–0.68, and the elbow alone had counted most of them. **The body comes up as one piece** *(added 2026-10-02; product owner: a push-up that touched the floor does not count)*: half-way up the push (elbow 110–140°) the hips MUST have risen as far as the shoulders have, each against its own height at the end of that push — hip share over shoulder share at least 0.90 — or the rep is refused with "Keep your body straight". Honest reps read 0.92–1.06; a rep pushed up from lying on the floor 0.87; a second person's near-straight cobras 0.63–0.72. Judged where the placement ratio held through the countdown is under 1.1 (about 35° and further round to the side): from further in front honest reps read down to 0.84. **A staked sitting MUST NOT start from further in front than that** *(product owner, 2026-10-02: no placement may be left where a cheat counts)*: the camera check refuses it on the same line and says which way to turn — away from the phone when it is too far in front, towards it when it is beside the member. *(Since 2026-10-07 the Daily 25 does not start there either — FR-001 — so the hip rise is judged in every sitting; until then the exemption reached the Daily 25.)* The body line is judged on the rep's median, and only a fold below 140° is refused: honest planks read 152–167° at 45°, so the ±10° body-line rule cannot be measured there, and this is stated as a known weakness. While pushing back up, the body line's median MUST be at least 145°, so a cobra — hips left on the floor while the chest is pressed up — is refused with "Keep your body straight" in the Daily 25 and in staked sittings alike. In every placement, a rep's time is measured from the moment the arms start down, so a rest at the top does not make a rep "too slow".
- **FR-008a** *(added 2026-10-07)*: **Face down.** In every sitting, a rep MUST be refused, with nothing said, when over the rep the near knee stands more than 0.3 arm-lengths above the near hip (lying on the back with the knees up), or the near ankle more than 0.6 arm-lengths below the near hand (standing). Every labelled push-up, from the toes or the knees, read the knee −0.59 to 0.02 and the ankle at most 0.04; crunches read the knee 0.51–0.81, standing the ankle 1.87–1.94. It is judged **first** *(amended 2026-10-11, recording the 8 October change)*: a rep that is not a push-up at all — on the back, or standing — is refused in silence whatever else it got wrong, so a sit-up is never told "Go lower" or "Up on your toes" aloud.
- **FR-009**: At 45°, measurements MUST be taken from the side of the body nearer the camera; a moment when that side is not clearly visible MUST be skipped, not scored.
- **FR-010**: **In a staked sitting** (always at 45°), knees are judged on the **push back up** (from the bottom, once the elbow is past 100°), and a rep is discarded with "Knees off the floor" when either: the near knee and ankle stand, together, less than 0.41 arm-lengths above the near hand (the hands are on the floor — honest reps read 0.44–0.65, push-ups from the knees 0.32–0.39); or the near knee's median angle is below 90° (a deep kneel or feet raised read 40–98°; honest reps with the phone on the member's right read 99–125°, so the line cannot be higher). *(Added 2026-10-02.)* A knee reading under 135° on the push needs the legs at 0.48 arm-lengths rather than 0.41 (kneeling with the feet raised). *(Amended 2026-10-03.)* From 120° up, only when the knee was also under 112° on the way down: kneeling is lowered onto a bent knee (61–106°), while a leg a little bent on the toes comes down at 117–147°. Depth is judged before knees, so a half push-up that reads either way is told "Go lower". *(Amended 2026-10-04.)* The legs on the push are also judged against the member's own plank, held through the countdown: under 0.66 of it, the knees are down (a second person's knee reps stood over the 0.41 line). And where the body's rise is judged, a staked rep whose rise cannot be read is not credited. The knee angle is judged the same way on the **way down** (until the elbow passes 100°): a rep let down from the knees and pushed up on the toes is refused too. A descent whose near knee was unseen, unreadable or at the picture's edge in more than 25 % of its frames is not credited either ("Keep your knees in the picture"), as on the push. These landmark rules are a stopgap, measured on one person, until a knee-on-the-floor image classifier replaces them. *(Added 2026-10-02.)* **A knee the model cannot place is not read**: in loose trousers it puts the knee on the ankle (a shin under 0.35 of the thigh in the picture) and the knee angle then reads anything; such a frame counts as the knee unseen, the ready hold does not pass on it, and the member is told why (FR-018a). A staked sitting also does not start until the near **foot** is at least 6 % inside every edge: at the very edge the model drops it every few frames, and two honest reps of eleven were refused for it. A staked rep whose push shows no knee reading at all is not credited — a knee the camera never saw is not a knee off the floor — and neither is one whose near knee spends **more than 25 % of the push within 13 % of any edge of the picture**: the pose model does not drop a knee that leaves the frame, it places it just inside the edge at full confidence *(2026-09-29: a set with the knees out of camera had it there 28–50 % of every rep; honest sets 0–3 %)*. Both are met with "Keep your knees in the picture", said aloud, as every refused rep's reason is (FR-019). Likewise the staked start (the whole-body check) needs the near knee in the picture and clear of the edges by the same margin. The ready position MUST be held from the toes. The Daily 25 does not judge knees off the floor; *(since 2026-10-08)* it holds the legs-in-view requirements of this paragraph — the knee readable and clear of the edges, the foot inside the picture, on the way in and through every rep — as an Easy Holdout does. A leg bent a little in the air passes both.
- **FR-011**: In a staked sitting, a member who starts on their knees MUST receive that hint on each attempt, not silence.

**Stating the rule**

- **FR-012**: Before staking, the stake sheet MUST state in one sentence that a Holdout is counted at 45° only — not in front, not straight beside — with full push-ups from the toes, and that push-ups from the knees do not count — nor, *(amended 2026-10-04)*, push-ups with the hips resting on the floor at the bottom.
- **FR-013** *(amended 2026-10-07)*: The first-run tour's card on how reps are counted MUST state the placement and the knee rule: every sitting at 45°; the Daily 25 and an Easy Holdout welcome knee push-ups, a Hard Holdout counts from the toes only.
- **FR-014**: The sentence stating the staked rule MUST read the same everywhere it appears.

**Setting up**

- **FR-015**: When a sitting opens, a set-up guide MUST be shown before the camera checks can start a countdown, with three pages: where the phone goes (only the placements these stakes accept, drawn from above with distances), how to lie, and how counting starts. *(Amended 2026-10-10.)* Under its title, on every page, it MUST first say "Two things decide whether a rep counts: your whole body in the picture, head to feet, and your knees clearly visible to the phone."; the phone page MUST say why the placement serves both ("From there it sees all of you, and the knee nearer to it."), and the body page MUST open with the whole-body and knees lines before the position and the outline.
- **FR-016**: The guide MUST offer "Don't show this again", remembered per wallet and per stakes, and MUST be reopenable from the sitting screen until the member is lined up.
- **FR-017**: No countdown MAY start while the guide is open.
- **FR-018**: While the member gets into position, the screen MUST show one correction at a time, at most three words, in type readable from about two metres, chosen in this order: more light; the phone standing up; getting into view; the phone's placement; getting down; the knee turn ("Turn slightly left", "right" or "away", FR-024a), shown only while *Whole body in frame* passes; further; closer; left or right; then "Hold". A moving background is shown as its own pill, never as the correction, because it does not block the start (001 FR-019).
- **FR-018a** *(added 2026-10-02)*: Once every check on the phone and the picture passes, the correction MUST say what the position itself still lacks — arms not straight, knees down, knees the model cannot make out, body not in one line — one at a time, with a sentence under it saying what to do. "Hold" is shown only when nothing is missing. Found on the phone: a second person in a proper plank, in loose trousers, saw "Hold" under four green pills for a minute and no countdown, with nothing to say why.
- **FR-019**: Each correction MUST be spoken once it has been the current correction for about 0.7 seconds, repeated every 5 seconds while it stays unresolved, and "Hold" spoken once only. *(Added 2026-10-04.)* While counting, every refused rep's reason MUST be spoken at once, not only when it repeats: face-down, the member cannot read the screen.
- **FR-020**: Instructions MUST NOT be shown over a member already in position in a way they have to dismiss.

**Feedback while counting**

- **FR-021**: Each counted rep MUST play a short tone and show a "+1" that rises from the count, unless the rep completes a block of ten, which plays the banked chime instead.

**Framing**

- **FR-024** *(revised 2026-09-29)*: The head MUST be tracked as a point **for entry**: the whole-body check — which the ready hold and the countdown both need — MUST NOT pass until the head is clearly inside the picture, and says "Keep your head in the picture" until it is. **While counting the head MUST be ignored**: a head out of the picture neither discards a rep nor raises a warning. *(Replaces the counting-time "Head out of view · move back" warning.)*
- **FR-024a** *(added 2026-10-10)*: The near knee MUST have its own pre-flight pill, **Knees visible**, between *Whole body in frame* and *Camera view*, blocking like them: it passes once the near knee is found and clear of the picture's edges, and while it fails it says which fix applies, **one of three** — "Turn your body slightly to your left (or right), away from the phone, so it sees your knees" when the knee is hidden behind the body and the side facing the phone is known; "Turn your body a little away from the phone, so it sees your knees" when the knee is hidden and that side cannot be told; "Keep your knees in the picture — move the phone further away" when it is at an edge. A frame without the picture's pose has shown no knee: the pill is red with the second fix, never green. The coach ranks the two turn fixes below a failing *Whole body in frame* pill (FR-018: getting into view comes first). *Whole body in frame* MUST NOT check the knee any more.

**Privacy**

- **FR-022**: All of the above MUST work with camera frames processed on the device and discarded, as today.

**Tuning evidence**

- **FR-023** *(amended 2026-10-09, spec 004)*: Holdout Lab MUST record one trace per set in its private storage — landmarks and the counter's decisions per frame, never a picture — and a trace MUST be complete however the sitting ends (Stop, finishing, or leaving the screen). Committed traces that carry a person's count MUST replay as tests and count within one rep of it. No build of the member app records anything.
- **FR-025** *(amended 2026-10-09, spec 004)*: In Holdout Lab, when a set that wrote a trace ends — by Stop or by reaching its target — the tester MUST be asked what they really did, by movement (full, half, from the knees, cobra, other) with an optional note, before leaving; the answer MUST be written into the trace so a pulled trace is already a test. Skipping leaves the trace unlabelled. Holdout Lab MUST also let the tester choose the counting mode (the Daily 25, an Easy Holdout or a Hard Holdout, with nothing staked), draw nothing over the camera ("clean screen"), and start a fresh set at any time.
- **FR-026** *(added 2026-09-27, constitution v1.5.0; amended 2026-10-09, v2.1.0)*: In Holdout Lab, with the switch "record video" on (off by default, remembered on the phone), a sitting MUST save the camera stream as a video file — **video only, no audio** — beside its trace, named after it, with a `.video.json` giving when recording started on the boot clock the trace's frame timestamps use. It is recorded with CameraX `VideoCapture` bound beside the preview and the analysis (the raw camera, nothing drawn over it); if the camera cannot carry all three, the sitting runs without video and says so. Pose analysis MUST still hold ≥ 15 FPS with video on — Lab's readout shows the analysis rate. The team pulls videos over USB and they are deleted from the phone once the copy is complete (`scripts/pull-traces.sh`). No build of the member app may contain the recorder: it lives in Holdout Lab only, and a build check fails any member-app build whose code touches `androidx.camera.video`. Only people who agreed in writing to be filmed are recorded.

### Key Entities

- **Stakes**: what a sitting has riding on it — the free Daily 25, an Easy Holdout or a Hard Holdout. Decides how far round the phone may stand, whether knees are judged, and which rule sentence is shown.
- **Placement**: where the phone stands relative to the member — head-on, 45°, or square side-on — recognised from the picture while the member holds the ready position. Only 45° is accepted, by every sitting *(since 2026-10-07)*; the others are recognised so the camera check can say which way to turn.
- **Set-up guide preference**: per wallet and per stakes, whether the member asked not to see the guide again.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001** *(amended 2026-10-07)*: Every honest set at 45° is counted within one rep of what a person watching the recording counts.
- **SC-002**: In a staked sitting at 45°, no push-up from the knees counts across a recorded set of at least ten.
- **SC-002a** *(amended 2026-10-07; was head-on)*: At 45°, no half push-up counts across a recorded set, and in the Daily 25 every full-depth push-up from the knees does.
- **SC-002c** *(added 2026-10-07)*: In any sitting, no crunch on the back and no arm bend while standing counts across a recorded set.
- **SC-002b**: At 45°, no cobra rep counts across a recorded set, in either stakes.
- **SC-003**: A phone correctly placed at 45° is recognised as 45° in at least 99% of frames while the member holds the top of a push-up.
- **SC-004**: A member can go from opening a sitting to the countdown without reading anything on the screen after putting the phone down.
- **SC-005**: A first-time member places the phone correctly on their first attempt in at least 8 of 10 tries with the set-up guide.
- **SC-006**: 100% of members who stake have been shown the staked rule sentence before the stake is taken.

## Assumptions

- Thresholds are set from sittings recorded on the phone itself (Holdout Lab records landmarks per set; committed ones live in `fixtures/rep-traces/` and replay as tests that must count within one rep of a person's count). By 26 September: seventeen labelled traces of one person on one Seeker — head-on honest, half and knee sets; 45° honest sets with the phone on either side; 45° half, knee (feet down and feet raised), cobra and mixed sets. Every one counts within one rep.
- Every threshold rests on one person, filmed from the floor. Another body, other clothes or another phone may move them; this is a stated weakness until traces from several people exist.
- Knee push-ups filmed at a flatter angle than 45° (nearer head-on) read like honest reps at the knee and are not caught; the camera check refuses head-on, but the boundary between 45° and head-on is where this weakness lives.
- Placement is recognised from how wide the shoulders appear against the torso's length. On the two recordings that ratio stayed at or under 1.35 at 45° and never fell below 1.73 head-on, so the head-on boundary sits at 1.55; the earlier boundary of 0.9 mistook most 45° frames for head-on.
- Square side-on is dropped because a body lying across a portrait frame needs the phone about two metres away; 45° needs about one and a half.
- The device may have no text-to-speech voice; spoken corrections are best effort, shown corrections are not.
- *(Until 2026-10-07)* Head-on limitations were acceptable for the free Daily 25 and stated there. Since then no sitting is head-on, and those limitations leave the README's known weaknesses with the placement.
- Out of scope: detecting knee push-ups head-on, which would need a classifier trained on the lower body and labelled recordings from several people. Not needed: no sitting is head-on.
- Dropping head-on costs the Daily 25 its easiest set-up. Accepted (2026-10-07): the Daily 25 exists to build the habit a Holdout asks for, and that includes the set-up.
- Deferred: a "knee on the floor" image classifier on crops of the legs, run on the device during the push phase of staked sittings only, trained on screen recordings made with the clean-screen switch. Training frames would come from members' own recordings shared by them, which needs a constitution note on principle X before any is collected from anyone other than the developer.
