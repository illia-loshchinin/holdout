# Feature Specification: Rep Placement and Form Rules

**Feature Branch**: `002-rep-placement-rules`

**Created**: 2026-09-24

**Status**: Draft

**Input**: User description: "Update the existing feature spec (specs/001-holdout-hackathon/spec.md) for rep verification placement and form rules, decided 2026-09-23/24 from two Seeker recordings: (1) Daily 25 accepts the phone head-on or at ~45°; staked Holdouts accept ~45° only; square side-on is accepted by neither. (2) Head-on verifies depth, lockout and tempo only (elbow read from the picture, torso-is-down check instead of body line); knee push-ups and sagging hips are not detected head-on and the app says so. (3) At 45° the elbow, body line and knee are read in the picture on the side nearer the lens; a push-up from the knees is discarded with "Up on your toes, knees off the floor" (knee threshold 150°, provisional until an honest 45° set is recorded). (4) The staked rule is stated in one sentence before staking (stake sheet) and again by the camera check; view cut-off head-on at shoulder-span/torso ratio 1.55. (5) Sitting UX: a set-up guide dialog (Phone / Body / Start tabs, "Don't show this again" per wallet and stakes) opens when a sitting starts while the member still holds the phone, and the countdown cannot start behind it; while getting into position one short correction (≤3 words: Closer, Further, Phone to 45°, More light, Get down, Hold…) is shown large and spoken; each counted rep plays a short tone and a "+1" animation."

## Context

This feature amends how a sitting is verified in `specs/001-holdout-hackathon` (FR-019, FR-021, FR-021a, US-6, FR-067). It records decisions taken on 23–24 September 2026 after two screen recordings of real sittings on a Solana Seeker:

- **Recording 1 — head-on, two honest push-ups.** Nothing was counted. Everything the rep rule measures happened along the line of sight, which the pose model estimates worst: the arm never read as straight at the top or as bent enough at the bottom, and the body line of one honest plank read anywhere between 146° and 177°.
- **Recording 2 — 25 reps, including cheats.** Push-ups from the knees, at an angle and head-on, and knees-down rocking with the hips pushed back, were all counted. Head-on, knees bent on the floor read as 146–164° against an honest plank's 156°; no measurement separated them. At about 45°, read on the side of the body nearer the camera, the same knee push-ups bottomed out at 74–102° at the knee.

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

---

### User Story 2 - Money is only staked on push-ups the camera can hold everyone to (Priority: P1)

A member in a staked Holdout films every sitting at about 45°, where a push-up from the knees is visible and discarded, so nobody's knee push-up takes somebody else's stake.

**Why this priority**: The fairness of every staked cohort depends on it. Recording 2 showed knee push-ups counting from the other placements.

**Independent Test**: Replay a 45° set of push-ups from the knees; none counts, and each attempt carries the hint to get up on the toes. Attempt to start a staked sitting head-on or square side-on; it does not start.

**Acceptance Scenarios**:

1. **Given** a staked sitting with the phone at about 45°, **When** the member does full push-ups from the toes, **Then** each counts.
2. **Given** a staked sitting at 45°, **When** the member does push-ups from the knees, **Then** none counts and the screen shows "Up on your toes, knees off the floor".
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
- The side of the body nearer the camera is briefly hidden (a hand, the member's own arm): that moment is skipped, not scored from the far side.
- Speech is unavailable on the device: corrections are still shown large; tones still play.
- The member reopens the set-up guide after ticking "Don't show this again": it opens; the choice is not undone.
- A Daily 25 head-on: a push-up from the knees counts. This is a stated limitation, not a defect.

## Requirements *(mandatory)*

### Functional Requirements

**Placement**

- **FR-001**: The Daily 25 MUST accept the phone head-on (in front of the member, the member's head towards it) or at about 45° to the member.
- **FR-002**: A staked Holdout MUST accept the phone at about 45° only.
- **FR-003**: Neither MUST accept the phone square to the member's side.
- **FR-004**: When the phone is in a placement the stakes do not accept, the camera check MUST stay red and say where to put it: "Put the phone in front of you, or at 45°" for the Daily 25, "Turn the phone to 45°" for a staked Holdout.
- **FR-005**: The placement is judged while the member holds the ready position and MUST stay fixed for the rest of the sitting.

**What each placement verifies**

- **FR-006**: Head-on, a rep MUST meet depth, lockout and tempo, and the member's torso MUST be down in push-up position rather than upright.
- **FR-007**: Head-on, the app MUST NOT claim to check for push-ups from the knees or sagging hips, and MUST say so where it explains how reps are counted.
- **FR-008**: At 45°, a rep MUST meet depth, lockout, tempo, a straight body line, and straight legs on the toes.
- **FR-009**: At 45°, measurements MUST be taken from the side of the body nearer the camera; a moment when that side is not clearly visible MUST be skipped, not scored.
- **FR-010**: At 45°, a rep with the knees bent below 150° MUST be discarded with the hint "Up on your toes, knees off the floor". This threshold is provisional until an honest 45° set is recorded (see Assumptions).
- **FR-011**: A member who starts on their knees MUST receive that hint on each attempt, not silence.

**Stating the rule**

- **FR-012**: Before staking, the stake sheet MUST state in one sentence that a Holdout is counted at 45° only — not in front, not straight beside — with full push-ups from the toes, and that push-ups from the knees do not count.
- **FR-013**: The first-run tour's card on how reps are counted MUST state both placements: the Daily 25 in front or at 45°, a Holdout at 45° only and from the toes.
- **FR-014**: The sentence stating the staked rule MUST read the same everywhere it appears.

**Setting up**

- **FR-015**: When a sitting opens, a set-up guide MUST be shown before the camera checks can start a countdown, with three pages: where the phone goes (only the placements these stakes accept, drawn from above with distances), how to lie, and how counting starts.
- **FR-016**: The guide MUST offer "Don't show this again", remembered per wallet and per stakes, and MUST be reopenable from the sitting screen until the member is lined up.
- **FR-017**: No countdown MAY start while the guide is open.
- **FR-018**: While the member gets into position, the screen MUST show one correction at a time, at most three words, in type readable from about two metres, chosen in this order: more light; the phone standing up; getting into view; the phone's placement; getting down; further; closer; left or right; a still background; then "Hold".
- **FR-019**: Each correction MUST be spoken once it has been the current correction for about 0.7 seconds, repeated every 5 seconds while it stays unresolved, and "Hold" spoken once only.
- **FR-020**: Instructions MUST NOT be shown over a member already in position in a way they have to dismiss.

**Feedback while counting**

- **FR-021**: Each counted rep MUST play a short tone and show a "+1" that rises from the count, unless the rep completes a block of ten, which plays the banked chime instead.

**Privacy**

- **FR-022**: All of the above MUST work with camera frames processed on the device and discarded, as today.

### Key Entities

- **Stakes**: what a sitting has riding on it — the free Daily 25 or a staked Holdout. Decides which placements are accepted and which rule sentence is shown.
- **Placement**: where the phone stands relative to the member — head-on, 45°, or square side-on — recognised from the picture while the member holds the ready position.
- **Set-up guide preference**: per wallet and per stakes, whether the member asked not to see the guide again.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Every honest set, head-on or at 45°, is counted within one rep of what a person watching the recording counts.
- **SC-002**: In a staked sitting at 45°, no push-up from the knees counts across a recorded set of at least ten.
- **SC-003**: A phone correctly placed at 45° is recognised as 45° in at least 99% of frames while the member holds the top of a push-up.
- **SC-004**: A member can go from opening a sitting to the countdown without reading anything on the screen after putting the phone down.
- **SC-005**: A first-time member places the phone correctly on their first attempt in at least 8 of 10 tries with the set-up guide.
- **SC-006**: 100% of members who stake have been shown the staked rule sentence before the stake is taken.

## Assumptions

- The 45° knee threshold (150°) and the 45° lockout reading are set from one angled recording of knee push-ups; an honest 45° set and a knee set, recorded with the in-app test switch for staked rules, confirm or move them before a staked cohort relies on them.
- Placement is recognised from how wide the shoulders appear against the torso's length. On the two recordings that ratio stayed at or under 1.35 at 45° and never fell below 1.73 head-on, so the head-on boundary sits at 1.55; the earlier boundary of 0.9 mistook most 45° frames for head-on.
- Square side-on is dropped because a body lying across a portrait frame needs the phone about two metres away; 45° needs about one and a half.
- The device may have no text-to-speech voice; spoken corrections are best effort, shown corrections are not.
- Head-on limitations are acceptable for the free Daily 25 and are stated there, consistent with the constitution's rule that weaknesses are documented, not hidden.
- Out of scope: detecting knee push-ups head-on, which would need a classifier trained on the lower body and labelled recordings from several people.
