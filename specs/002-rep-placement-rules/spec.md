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
- Q: What counts as the top of a rep at 45°, where straight arms read 158–163°? → A: The rule's own 160°, instead of the stricter 165° used head-on; a rep still needs a full bottom to count.
- Q: How is the body line judged at 45°, where honest planks read 152–167°? → A: On the rep's median, and only a fold below 140° is refused; the ±10° rule cannot be measured there and is a stated weakness.
- Q: When does a rep's time start? → A: When the arms actually start down; resting at the top is not time spent in the rep.
- Q: Push-ups from the knees with the shins flat on the floor still counted at 45° (knee angle 124–139° on the push, too close to honest 139–160°) — how are they caught? → A: By how high the near knee and ankle stand above the hand, which is on the floor, measured on the push: honest 0.44–0.65 arm-lengths, knees 0.32–0.39; a rep below 0.41 is knees-down. The knee angle stays as a second signal at 135°, for a deep kneel.
- Q: How is depth judged at 45°, where some half push-ups read 55–67° at the elbow, as deep as honest ones? → A: The near shoulder must also come down to 0.32 arm-lengths or less above the near hand at the bottom: honest 0.20–0.28, half push-ups 0.43–0.68.
- Q: Does a "cobra" count — lying flat, then pressing the chest up with the hips left on the floor? → A: No, in the Daily 25 or staked: it is not a push-up. At 45° the body line is judged on the push back up (from the bottom, once the elbow is past 100°): its median must be at least 145°. On the phone the push read 157–174° on honest reps and 120–134° on cobra reps, which had counted because the flat part of the rep hid the arch in the whole-rep median.
- Q: How are thresholds kept honest as they are tuned? → A: Debug builds record one trace per sitting — landmarks and the counter's decisions, never a picture; committed traces that carry a person's count replay as tests and must count within one rep of it.

### Session 2026-09-25/26

Decided from eleven more labelled phone traces (seventeen in all), with the phone on the member's left and right.

- Q: Honest reps with the phone on the member's right read 99–125° at the knee and were refused as knee push-ups — where is the knee-angle line? → A: 90°. A deep kneel or raised feet read 40–98°; the knee-and-ankle height rule (0.41 arm-lengths) still catches knees down with the feet flat. Swept on all seventeen traces: no honest rep refused.
- Q: The head dropped out of frame at the bottom of reps with the phone set too close — what then? → A: The head (the pose model's nose point) becomes a tracked point. The camera check requires it in view before counting; while counting, a head out of the picture for half a second shows "Head out of view · move back" large and says "Move back" at most every 5 seconds. Reps are not thrown away for it. *(Revised 2026-09-29, product owner: the head is required **clearly inside the picture to enter** — the whole-body check, which the ready hold and the countdown need — and is **ignored while counting**: no warning, no effect on the count.)*
- Q: *(2026-09-29)* A staked set was done with the knees out of the camera and all five reps counted — the model put the knees just inside the edge at full confidence. What then? → A: A knee at the picture's edge is a guess, not a knee seen. A staked rep whose near knee spends over a quarter of the push back up within 13 % of an edge (or unseen) is refused with "Keep your knees in the picture", said aloud the first time; the staked start needs the near knee clear of the edges too.
- Q: How does a trace get its ground truth? → A: In debug builds, pressing Stop (or finishing the day) opens a form asking what was really done — full, half, from the knees, cobra, other, and a note — and writes it into the trace's header before the member leaves. The expected count is full push-ups, plus knee push-ups in the Daily 25.
- Q: Should a moving background keep a member from starting? → A: No. It is measured (the share of background cells that changed between frames, the member's own box and whole-frame exposure shifts left out) and shown as an amber pill with its fix, but it never blocks the countdown: the threshold has not been tried in real homes, and a TV or a passer-by must not stop a set. It is not one of the coach's corrections.
- Q: A staked push shows no knee at all — pass or refuse? → A: Refuse, with "Keep your legs in view": a knee the camera never saw is not a knee off the floor. The staked ready position needs the knee in view too.
- Q: Should a small image classifier decide "knee on the floor" instead of the landmark rules? → A: Yes, eventually, and not now. It needs recordings from several people; until then the landmark rules stand, stated as measured on one person. A debug "clean screen" switch — nothing drawn over the camera — exists so screen recordings can be used as training data later.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Daily 25 counts from where people actually put the phone (Priority: P1)

A member doing the free Daily 25 stands the phone on the floor in front of them, or turned to about 45°, gets into a plank, and every full push-up counts.

**Why this priority**: Without it the free tier — the product's daily habit and first impression — counts nothing, which is what recording 1 showed.

**Independent Test**: Replay recording 1 (head-on, two honest push-ups) and a 45° honest set through the counter; each counts its reps within one.

**Acceptance Scenarios**:

1. **Given** a Daily 25 sitting with the phone head-on, **When** the member does full push-ups, **Then** each one counts.
2. **Given** a Daily 25 sitting with the phone at about 45°, **When** the member does full push-ups, **Then** each one counts.
3. **Given** a Daily 25 sitting with the phone square to the member's side, **When** the camera check runs, **Then** it stays red and says to put the phone in front or at 45°.
4. **Given** a Daily 25 sitting head-on, **When** the member stands or kneels upright and bends their arms at the phone, **Then** nothing counts.
5. **Given** a Daily 25 sitting in either placement, **When** the member does full-depth push-ups from the knees, **Then** each one counts.
6. **Given** a Daily 25 sitting head-on, **When** the member does half push-ups, **Then** none counts and each is met with "Go lower".

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

- The phone is placed between head-on and 45°, near the boundary: the view must not flicker between placements while the member holds still at the top of a push-up; a correctly placed phone is never refused because of the boundary.
- The member starts a staked sitting on their knees: nothing counts, and they are told why rather than met with silence.
- The member lies flat and presses only the chest up, hips on the floor ("cobra"), at 45°: nothing counts, with "Keep your body straight". On the phone, at most one cobra rep in a set of five has slipped through; head-on, a cobra has not been recorded yet and its detection is unproven.
- The phone is set too close and the head leaves the picture at the bottom of a rep: counting goes on, and nothing is said — the head was checked on the way in *(revised 2026-09-29)*.
- A staked set is done with the knees out of the picture: each rep is refused with "Keep your knees in the picture", said aloud at once, until the member or the phone moves *(2026-09-29)*.
- Half push-ups at 45°: 7 of 11 had counted on the elbow alone; refused by the shoulder-over-hand depth rule (FR-008) since 2026-09-25.
- The side of the body nearer the camera is briefly hidden (a hand, the member's own arm): that moment is skipped, not scored from the far side.
- Speech is unavailable on the device: corrections are still shown large; tones still play.
- The member reopens the set-up guide after ticking "Don't show this again": it opens; the choice is not undone.
- A Daily 25, head-on or at 45°: a push-up from the knees counts. This is a product decision — the Daily 25 is a free habit for building strength — not a defect.

## Requirements *(mandatory)*

### Functional Requirements

**Placement**

- **FR-001**: The Daily 25 MUST accept the phone head-on (in front of the member, the member's head towards it) or at about 45° to the member.
- **FR-002**: A staked Holdout MUST accept the phone at about 45° only.
- **FR-003**: Neither MUST accept the phone square to the member's side.
- **FR-004**: When the phone is in a placement the stakes do not accept, the camera check MUST stay red and say where to put it: "Put the phone in front of you, or at 45°" for the Daily 25, "Turn the phone to 45°" for a staked Holdout.
- **FR-005**: The placement is judged while the member holds the ready position and MUST stay fixed for the rest of the sitting.

**What each placement verifies**

- **FR-006**: Head-on, a rep MUST meet depth, lockout and tempo, and the member's torso MUST be down in push-up position rather than upright. **Depth head-on is judged by how far the shoulders come down towards the hands**: at the bottom they MUST reach a quarter or less of their height above the hands at the top, as well as the elbow bending past the bottom threshold — the elbow angle alone over-reads the bend head-on, and counted three half push-ups on the phone.
- **FR-007**: **The Daily 25 MUST count push-ups from the knees**, in either placement — a free habit a member can keep while building up to full push-ups — and MUST say so where it explains how reps are counted. Head-on, the app MUST NOT claim to check for sagging hips.
- **FR-008**: At 45°, a rep MUST meet depth, lockout, tempo and a body not folded at the hips — and, in a staked sitting, knees off the floor (FR-010). Lockout at 45° is the rule's own 160°, because straight arms read 158–163° from that angle. **Depth at 45°**: besides the elbow passing the bottom threshold, the near shoulder MUST come down to 0.32 arm-lengths or less above the near hand — honest reps reached 0.20–0.28, half push-ups stopped at 0.43–0.68, and the elbow alone had counted most of them. The body line is judged on the rep's median, and only a fold below 140° is refused: honest planks read 152–167° at 45°, so the ±10° body-line rule cannot be measured there, and this is stated as a known weakness. While pushing back up, the body line's median MUST be at least 145°, so a cobra — hips left on the floor while the chest is pressed up — is refused with "Keep your body straight" in the Daily 25 and in staked sittings alike. In every placement, a rep's time is measured from the moment the arms start down, so a rest at the top does not make a rep "too slow".
- **FR-009**: At 45°, measurements MUST be taken from the side of the body nearer the camera; a moment when that side is not clearly visible MUST be skipped, not scored.
- **FR-010**: **In a staked sitting** (always at 45°), knees are judged on the **push back up** (from the bottom, once the elbow is past 100°), and a rep is discarded with "Knees off the floor" when either: the near knee and ankle stand, together, less than 0.41 arm-lengths above the near hand (the hands are on the floor — honest reps read 0.44–0.65, push-ups from the knees 0.32–0.39); or the near knee's median angle is below 90° (a deep kneel or feet raised read 40–98°; honest reps with the phone on the member's right read 99–125°, so the line cannot be higher). These landmark rules are a stopgap, measured on one person, until a knee-on-the-floor image classifier replaces them. A staked rep whose push shows no knee reading at all is not credited — a knee the camera never saw is not a knee off the floor — and neither is one whose near knee spends **more than 25 % of the push within 13 % of any edge of the picture**: the pose model does not drop a knee that leaves the frame, it places it just inside the edge at full confidence *(2026-09-29: a set with the knees out of camera had it there 28–50 % of every rep; honest sets 0–3 %)*. Both are met with "Keep your knees in the picture", said aloud the first time. Likewise the staked start (the whole-body check) needs the near knee in the picture and clear of the edges by the same margin. The ready position MUST be held from the toes. The Daily 25 does not judge knees. A leg bent a little in the air passes both.
- **FR-011**: In a staked sitting, a member who starts on their knees MUST receive that hint on each attempt, not silence.

**Stating the rule**

- **FR-012**: Before staking, the stake sheet MUST state in one sentence that a Holdout is counted at 45° only — not in front, not straight beside — with full push-ups from the toes, and that push-ups from the knees do not count.
- **FR-013**: The first-run tour's card on how reps are counted MUST state both placements and the knee rule: the Daily 25 in front or at 45°, knee push-ups welcome; a Holdout at 45° only and from the toes.
- **FR-014**: The sentence stating the staked rule MUST read the same everywhere it appears.

**Setting up**

- **FR-015**: When a sitting opens, a set-up guide MUST be shown before the camera checks can start a countdown, with three pages: where the phone goes (only the placements these stakes accept, drawn from above with distances), how to lie, and how counting starts.
- **FR-016**: The guide MUST offer "Don't show this again", remembered per wallet and per stakes, and MUST be reopenable from the sitting screen until the member is lined up.
- **FR-017**: No countdown MAY start while the guide is open.
- **FR-018**: While the member gets into position, the screen MUST show one correction at a time, at most three words, in type readable from about two metres, chosen in this order: more light; the phone standing up; getting into view; the phone's placement; getting down; further; closer; left or right; then "Hold". A moving background is shown as its own pill, never as the correction, because it does not block the start (001 FR-019).
- **FR-019**: Each correction MUST be spoken once it has been the current correction for about 0.7 seconds, repeated every 5 seconds while it stays unresolved, and "Hold" spoken once only.
- **FR-020**: Instructions MUST NOT be shown over a member already in position in a way they have to dismiss.

**Feedback while counting**

- **FR-021**: Each counted rep MUST play a short tone and show a "+1" that rises from the count, unless the rep completes a block of ten, which plays the banked chime instead.

**Framing**

- **FR-024** *(revised 2026-09-29)*: The head MUST be tracked as a point **for entry**: the whole-body check — which the ready hold and the countdown both need — MUST NOT pass until the head is clearly inside the picture, and says "Keep your head in the picture" until it is. **While counting the head MUST be ignored**: a head out of the picture neither discards a rep nor raises a warning. *(Replaces the counting-time "Head out of view · move back" warning.)*

**Privacy**

- **FR-022**: All of the above MUST work with camera frames processed on the device and discarded, as today.

**Tuning evidence**

- **FR-023**: Debug builds MUST record one trace per sitting in the app's private storage — landmarks and the counter's decisions per frame, never a picture — and a trace MUST be complete however the sitting ends (Stop, finishing, or leaving the screen). Committed traces that carry a person's count MUST replay as tests and count within one rep of it. Release builds record nothing.
- **FR-025**: In debug builds, when a sitting that wrote a trace ends — by Stop or by finishing the day — the member MUST be asked what they really did, by movement (full, half, from the knees, cobra, other) with an optional note, before leaving; the answer MUST be written into the trace so a pulled trace is already a test. Skipping leaves the trace unlabelled. Debug builds MUST also offer switches to run a sitting under staked rules without a stake, to draw nothing over the camera ("clean screen"), and to reset today's Daily 25.
- **FR-026** *(added 2026-09-27, constitution v1.5.0)*: In debug builds, with the test switch "record video" on (off by default, remembered on the phone), a sitting MUST save the camera stream as a video file — **video only, no audio** — beside its trace, named after it, with a `.video.json` giving when recording started on the boot clock the trace's frame timestamps use. It is recorded with CameraX `VideoCapture` bound beside the preview and the analysis (the raw camera, nothing drawn over it); if the camera cannot carry all three, the sitting runs without video and says so. Pose analysis MUST still hold ≥ 15 FPS with video on — the debug readout shows the analysis rate. The team pulls videos over USB and they are deleted from the phone once the copy is complete (`scripts/pull-traces.sh`). A release build MUST NOT contain the recorder: it lives in `src/debug` only and `checkReleaseHasNoRecorder` fails the release build if release code touches `androidx.camera.video`. Only people who agreed in writing to be filmed are recorded.

### Key Entities

- **Stakes**: what a sitting has riding on it — the free Daily 25 or a staked Holdout. Decides which placements are accepted and which rule sentence is shown.
- **Placement**: where the phone stands relative to the member — head-on, 45°, or square side-on — recognised from the picture while the member holds the ready position.
- **Set-up guide preference**: per wallet and per stakes, whether the member asked not to see the guide again.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every honest set, head-on or at 45°, is counted within one rep of what a person watching the recording counts.
- **SC-002**: In a staked sitting at 45°, no push-up from the knees counts across a recorded set of at least ten.
- **SC-002a**: Head-on, no half push-up counts across a recorded set, and in the Daily 25 every full-depth push-up from the knees does.
- **SC-002b**: At 45°, no cobra rep counts across a recorded set, in either stakes.
- **SC-003**: A phone correctly placed at 45° is recognised as 45° in at least 99% of frames while the member holds the top of a push-up.
- **SC-004**: A member can go from opening a sitting to the countdown without reading anything on the screen after putting the phone down.
- **SC-005**: A first-time member places the phone correctly on their first attempt in at least 8 of 10 tries with the set-up guide.
- **SC-006**: 100% of members who stake have been shown the staked rule sentence before the stake is taken.

## Assumptions

- Thresholds are set from sittings recorded on the phone itself (debug builds record landmarks per sitting; committed ones live in `fixtures/rep-traces/` and replay as tests that must count within one rep of a person's count). By 26 September: seventeen labelled traces of one person on one Seeker — head-on honest, half and knee sets; 45° honest sets with the phone on either side; 45° half, knee (feet down and feet raised), cobra and mixed sets. Every one counts within one rep.
- Every threshold rests on one person, filmed from the floor. Another body, other clothes or another phone may move them; this is a stated weakness until traces from several people exist.
- Knee push-ups filmed at a flatter angle than 45° (nearer head-on) read like honest reps at the knee and are not caught; the camera check refuses head-on, but the boundary between 45° and head-on is where this weakness lives.
- Placement is recognised from how wide the shoulders appear against the torso's length. On the two recordings that ratio stayed at or under 1.35 at 45° and never fell below 1.73 head-on, so the head-on boundary sits at 1.55; the earlier boundary of 0.9 mistook most 45° frames for head-on.
- Square side-on is dropped because a body lying across a portrait frame needs the phone about two metres away; 45° needs about one and a half.
- The device may have no text-to-speech voice; spoken corrections are best effort, shown corrections are not.
- Head-on limitations are acceptable for the free Daily 25 and are stated there, consistent with the constitution's rule that weaknesses are documented, not hidden.
- Out of scope: detecting knee push-ups head-on, which would need a classifier trained on the lower body and labelled recordings from several people. Not needed for this feature: the Daily 25 counts knee push-ups, and staked sittings are never head-on.
- Deferred: a "knee on the floor" image classifier on crops of the legs, run on the device during the push phase of staked sittings only, trained on screen recordings made with the clean-screen switch. Training frames would come from members' own recordings shared by them, which needs a constitution note on principle X before any is collected from anyone other than the developer.
