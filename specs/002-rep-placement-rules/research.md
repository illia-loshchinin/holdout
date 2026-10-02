# Research: Rep Placement and Form Rules

Every number here was measured on a Solana Seeker, from traces the app recorded itself (committed under `fixtures/rep-traces/`), 23–26 September 2026. One person, filmed from the floor.

## D1. Read the picture, not the 3D estimate

- **Decision**: Angles come from MediaPipe's image landmarks, scaled to pixels. World landmarks are used only when no image is available.
- **Rationale**: Head-on, all the motion is along the line of sight, and the world estimate's depth is the model's weakest output: an honest plank read anywhere from 146° to 177° at the body line. In the picture, a straight arm and a bent arm are unmistakable.
- **Alternatives considered**: Force a side-on phone (needs about 2 m of floor for a body across a portrait frame, and was refused by the member); world landmarks with heavier filtering (the bias is systematic, not noise).

## D2. Which placements each stake accepts

- **Decision**: Daily 25 accepts head-on or 45°; staked accepts 45° only; square side-on is accepted by neither.
- **Rationale**: Head-on cannot see the knees. At 45° the near-side knee bottoms out at 74–102° on knee push-ups. Side-on needs too much room.
- **Placement recognition**: shoulder span ÷ torso length. At 45° it stayed at or under 1.35, head-on it never fell below 1.73, so head-on starts at 1.55 (was 0.9, which mistook most 45° frames for head-on); side-on is 0.5 or less.

## D3. Near side at 45°

- **Decision**: Measure the arm, body line and leg on the side whose limbs are longer in the picture. If that side is not clearly visible, skip the frame.
- **Rationale**: The far side is partly hidden behind the body and its points are guesses.

## D4. Judge a rep on medians, and the push on its own

- **Decision**: Body line and knee are judged on the median over the rep, and again over the push phase (from the bottom until the elbow opens past 100°).
- **Rationale**: Single frames flicker; an any-frame rule refused every honest 45° rep. The push phase is where cobra and knees show: cobra's whole-rep median hid the arch behind the flat part of the rep.
- **Numbers**: push-phase body line honest 157–174°, cobra 120–134° → floor 145° (a 150° floor refused honest reps in `angled-mixed-02`). Whole-rep body line honest 152–167° → only a fold below 140° is refused.

## D5. Depth

- **Head-on**: the elbow alone over-reads the bend. The shoulders must come down to at most 0.25 of their top height above the hands (honest 0.16–0.19, half 0.26–0.53).
- **45°**: the near shoulder must come to 0.32 arm-lengths or less above the near hand (honest 0.20–0.28, half 0.43–0.68). *(Amended 2026-10-02.)* Between 0.32 and 0.36 the rep still counts when the elbow closed to 66° or less: from an unmeasured spot near 40°, three honest reps of twelve stopped at 0.33–0.34 with the elbow at 62–64° and were refused, while half push-ups in the same band (0.34–0.35) kept the elbow at 68–71° (`angled40-right-mixed-08`). Neither reading alone separates the two there; both must say shallow (`angled-right-honest-10`). **Knees on the way down** *(same day)*: the median knee angle is judged on the descent as well as the push, under the same 90° line — a rep let down from the knees and pushed up on the toes had counted (`angled60-right-knees-down-up-09`: 77° on the descent; honest descents 108° at the least). The legs' height is not judged on the descent: honest reps with the phone on the left read as low there as the cheat.
- **Lockout at 45°**: straight arms read 158–163°, so the top trigger is the rule's own 160° instead of 165°.

## D6. Knees, in staked sittings only

- **Decision**: A rep is knees-down if, on the push, the near knee and ankle together stand less than 0.41 arm-lengths above the near hand, or the near knee's median angle is below 90°.
- **Numbers**: height honest 0.44–0.65, knees 0.32–0.39. Knee angle: deep kneel or raised feet 40–98°; honest reps with the phone on the member's right 99–125° (which is why the earlier 135° line was lowered to 90°). A sweep over all seventeen traces refused no honest rep.
- **Status**: stopgap, margins 0.03–0.05. See D9.

## D7. Head as a tracked point

- **Decision**: The nose point is required by the camera check and watched while counting; out of view for 0.5 s → shown and spoken warning, repeated at most every 5 s; reps are kept.
- **Rationale**: With the phone too close, the head leaves the frame first at the bottom of a rep, and the model then guesses the rest of the body.

## D8. Traces as the test suite

- **Decision**: Debug builds write a CSV per sitting (contract: [trace-format.md](contracts/trace-format.md)); at Stop the member logs what they did; committed traces replay in `RecordedTracesTest` and must count within one rep.
- **Rationale**: Thresholds tuned by eye drift; a labelled trace turns each phone session into a regression test.
- **Alternatives considered**: Screen recordings re-run on the desktop — slower, and not the phone's own model output.

## D9. Deferred: a "knee on the floor" image classifier

- **Decision**: Not now. Later, a small on-device image classifier on crops of the legs around the knee and ankle, run only in the push phase of staked sittings, replacing D6.
- **Rationale**: Landmark knees are fragile across bodies and camera heights; a classifier sees the floor contact itself. It needs labelled recordings from several people, which do not exist yet.
- **Preconditions**: a clean-screen recording mode (built); a constitution note on principle X about members sharing their own recordings for training, before any are collected from anyone other than the developer; a spec of its own.
