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

- **Head-on**: the elbow alone over-reads the bend. The shoulders must come down to at most 0.25 of their top height above the hands (honest 0.16–0.19, half 0.26–0.53). *(Amended 2026-10-02: 0.285.)* On the 4:3 picture a Daily 25 set read 0.22–0.30 on every rep and 0.25 refused seven of fifteen (`front-honest-knees-04`).
- **45°**: the near shoulder must come to 0.32 arm-lengths or less above the near hand (honest 0.20–0.28, half 0.43–0.68). *(Amended 2026-10-02.)* Between 0.32 and 0.36 the rep still counts when the elbow closed to 66° or less: from an unmeasured spot near 40°, three honest reps of twelve stopped at 0.33–0.34 with the elbow at 62–64° and were refused, while half push-ups in the same band (0.34–0.35) kept the elbow at 68–71° (`angled40-right-mixed-08`). Neither reading alone separates the two there; both must say shallow (`angled-right-honest-10`). **Knees on the way down** *(same day)*: the median knee angle is judged on the descent as well as the push, under the same 90° line — a rep let down from the knees and pushed up on the toes had counted (`angled60-right-knees-down-up-09`: 77° on the descent; honest descents 108° at the least). **A bent knee needs higher legs** *(same day)*: a knee under 135° on the push needs the legs at 0.48 arm-lengths, not 0.41 — kneeling with the feet raised read a knee of 90–112° with the legs at 0.43–0.45 and counted five times (`angled30-right-knees-feetup-13`); the one honest set with a bent-reading knee has the legs at 0.51–0.56. **The body as one piece** *(same day, third batch)*: a rep let down onto the floor and pushed up from there counted (legs 0.42, knee 141°, push body line 152°), and so did three of a second person's cobras done with the body nearly straight (push body line 145–150°, against a 145° line). Neither the body line nor a line set from the plank held in the countdown separates them: honest reps from a more frontal spot read 149–156° on the push, and that person's own plank read 151°. What does: how far the hips have risen half-way up the push against how far the shoulders have — honest 0.92–1.06 on ninety reps from 40° to 60°, the floor-start rep 0.87 (two more like it 0.83, 0.86), her cobras 0.63–0.72, cobras with the hips left down 0.37–0.67. Line 0.90, judged only under a placement ratio of 1.1; the older, more frontal fixtures read honest reps down to 0.84 and are left to the body line alone. **So the staked band ends there too** (product owner, the same day): a staked sitting does not start at a ratio of 1.1 or more — about 35°; 30° held 1.05–1.26, 40° 0.78–0.95 — and the camera pill says which way to turn rather than "Turn the phone to 45°". The Daily 25 still takes anything from 45° to head-on. **Loose trousers**: the model puts the knee on the ankle (shin under 0.35 of the thigh in 60–67 % of her frames, 0–1 % of everyone else's), the knee angle reads 10–120° in a clean plank, and the heights read the ankle's. Nothing about such a knee can be judged, so it is treated as unseen and said so. **Feet at the edge**: two honest reps were refused "knees in the picture" with the knee 13–17 % inside the frame — the ankle, 2–5 % from the edge, dropped below the model's confidence and took the body line and the knee angle with it. Fixed where it starts: the set-up asks for the foot 6 % inside. The legs' height is not judged on the descent: honest reps with the phone on the left read as low there as the cheat.
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
