# Research: Rep Placement and Form Rules

Every number here was measured on a Solana Seeker, from traces the app recorded itself (committed under `fixtures/rep-traces/`), 23–26 September 2026. One person, filmed from the floor. Later decisions, from more sittings, more people and video-labelled reps, are dated where they land; D10–D12 are from 7–8 October.

## D1. Read the picture, not the 3D estimate

- **Decision**: Angles come from MediaPipe's image landmarks, scaled to pixels. World landmarks are used only when no image is available.
- **Rationale**: Head-on, all the motion is along the line of sight, and the world estimate's depth is the model's weakest output: an honest plank read anywhere from 146° to 177° at the body line. In the picture, a straight arm and a bent arm are unmistakable.
- **Alternatives considered**: Force a side-on phone (needs about 2 m of floor for a body across a portrait frame, and was refused by the member); world landmarks with heavier filtering (the bias is systematic, not noise).

## D2. Which placements each stake accepts

- **Decision**: Daily 25 accepts head-on or 45°; staked accepts 45° only; square side-on is accepted by neither. **Superseded 2026-10-08 by D10**: every sitting is at 45°.
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

- **Head-on** *(retired 2026-10-08 with the placement, D10)*: the elbow alone over-reads the bend. The shoulders must come down to at most 0.25 of their top height above the hands (honest 0.16–0.19, half 0.26–0.53). *(Amended 2026-10-02: 0.285.)* On the 4:3 picture a Daily 25 set read 0.22–0.30 on every rep and 0.25 refused seven of fifteen (`front-honest-knees-04`).
- **45°**: the near shoulder must come to 0.32 arm-lengths or less above the near hand (honest 0.20–0.28, half 0.43–0.68). *(Amended 2026-10-02.)* Between 0.32 and 0.36 the rep still counts when the elbow closed to 66° or less: from an unmeasured spot near 40°, three honest reps of twelve stopped at 0.33–0.34 with the elbow at 62–64° and were refused, while half push-ups in the same band (0.34–0.35) kept the elbow at 68–71° (`angled40-right-mixed-08`). Neither reading alone separates the two there; both must say shallow (`angled-right-honest-10`). **Knees on the way down** *(same day)*: the median knee angle is judged on the descent as well as the push, under the same 90° line — a rep let down from the knees and pushed up on the toes had counted (`angled60-right-knees-down-up-09`: 77° on the descent; honest descents 108° at the least). **A bent knee needs higher legs** *(same day)*: a knee under 135° on the push needs the legs at 0.48 arm-lengths, not 0.41 — kneeling with the feet raised read a knee of 90–112° with the legs at 0.43–0.45 and counted five times (`angled30-right-knees-feetup-13`); the one honest set with a bent-reading knee has the legs at 0.51–0.56. **The body as one piece** *(same day, third batch)*: a rep let down onto the floor and pushed up from there counted (legs 0.42, knee 141°, push body line 152°), and so did three of a second person's cobras done with the body nearly straight (push body line 145–150°, against a 145° line). Neither the body line nor a line set from the plank held in the countdown separates them: honest reps from a more frontal spot read 149–156° on the push, and that person's own plank read 151°. What does: how far the hips have risen half-way up the push against how far the shoulders have — honest 0.92–1.06 on ninety reps from 40° to 60°, the floor-start rep 0.87 (two more like it 0.83, 0.86), her cobras 0.63–0.72, cobras with the hips left down 0.37–0.67. Line 0.90, judged only under a placement ratio of 1.1; the older, more frontal fixtures read honest reps down to 0.84 and are left to the body line alone. **So the staked band ends there too** (product owner, the same day): a staked sitting does not start at a ratio of 1.1 or more — about 35°; 30° held 1.05–1.26, 40° 0.78–0.95 — and the camera pill says which way to turn rather than "Turn the phone to 45°". The Daily 25 still takes anything from 45° to head-on *(until 2026-10-08: D10)*. **Loose trousers**: the model puts the knee on the ankle (shin under 0.35 of the thigh in 60–67 % of her frames, 0–1 % of everyone else's), the knee angle reads 10–120° in a clean plank, and the heights read the ankle's. Nothing about such a knee can be judged, so it is treated as unseen and said so. **Feet at the edge**: two honest reps were refused "knees in the picture" with the knee 13–17 % inside the frame — the ankle, 2–5 % from the edge, dropped below the model's confidence and took the body line and the knee angle with it. Fixed where it starts: the set-up asks for the foot 6 % inside. The legs' height is not judged on the descent: honest reps with the phone on the left read as low there as the cheat.
- **Lockout at 45°**: straight arms read 158–163°, so the top trigger is the rule's own 160° instead of 165°.

## D6. Knees, in staked sittings only

- **Decision**: A rep is knees-down if, on the push, the near knee and ankle together stand less than 0.41 arm-lengths above the near hand, or the near knee's median angle is below 90°.
- **Numbers**: height honest 0.44–0.65, knees 0.32–0.39. Knee angle: deep kneel or raised feet 40–98°; honest reps with the phone on the member's right 99–125° (which is why the earlier 135° line was lowered to 90°). A sweep over all seventeen traces refused no honest rep.
- **Status**: stopgap, margins 0.03–0.05. See D9.

## D7. Head as a tracked point

- **Decision**: The nose point is required by the camera check and watched while counting; out of view for 0.5 s → shown and spoken warning, repeated at most every 5 s; reps are kept. *(Revised 2026-09-29: the head is required to enter and ignored while counting — spec FR-024.)*
- **Rationale**: With the phone too close, the head leaves the frame first at the bottom of a rep, and the model then guesses the rest of the body.

## D8. Traces as the test suite

- **Decision**: Holdout Lab writes a CSV per set (debug builds did until 2026-10-09, spec 004; contract: [trace-format.md](contracts/trace-format.md)); at Stop the tester logs what they did; committed traces replay in `RecordedTracesTest` and must count within one rep.
- **Rationale**: Thresholds tuned by eye drift; a labelled trace turns each phone session into a regression test.
- **Alternatives considered**: Screen recordings re-run on the desktop — slower, and not the phone's own model output.

## D9. Deferred: a "knee on the floor" image classifier

- **Decision**: Not now. Later, a small on-device image classifier on crops of the legs around the knee and ankle, run only in the push phase of staked sittings, replacing D6.
- **Rationale**: Landmark knees are fragile across bodies and camera heights; a classifier sees the floor contact itself. It needs labelled recordings from several people, which do not exist yet.
- **Preconditions**: a clean-screen recording mode (built); a constitution note on principle X about members sharing their own recordings for training, before any are collected from anyone other than the developer; a spec of its own.

## D10. The Daily 25 is an Easy Holdout with nothing staked *(2026-10-08)*

- **Decision**: Every sitting is counted at 45°. The Daily 25 takes the Easy band — no more frontal than about 40° (placement ratio under 0.90) — and the Easy checks: the body line (to the ankle or the knee), the hip rise, and the near knee and foot in the picture to start and through every rep. Push-ups from the knees still count. Head-on is not read at all: a counter cannot be built for it, and a head-on frame is no reading.
- **Rationale**: Head-on could not see what a Holdout refuses — knees, a sag, a cobra, hips on the floor — so the Daily 25 counted there what money is never staked on, and taught the wrong habit; it was also a second way of measuring (both elbows, shoulder height share, torso angle) tuned for the free tier alone. At 45° every Daily 25 sitting rehearses the staked set-up and is a recording of the placement money rides on. Under the new rules the two Daily 25 sittings of 7 Oct still count 22 of 22 and 7 of 7 push-ups.
- **Alternatives considered**: Keep head-on as a fallback for small rooms (rejected: more floor and a harder set-up are accepted, for a habit meant to lead to a Holdout); the Daily 25 at any 45° the classifier accepts, without Easy's band (rejected: in front of about 35° the hip-rise check does not hold, and cobras got through, 4 of 5 on `angled-mixed-03`); the Daily 25 without Easy's leg checks (rejected by the product owner: loose trousers then block a Daily 25 too, and the member is told why).

## D11. Face down *(2026-10-07)*

- **Decision**: In every sitting, a rep is refused in silence when, over the rep, the near knee stands more than 0.3 arm-lengths above the near hip (lying on the back with the knees up) or the near ankle more than 0.6 arm-lengths below the near hand (standing). Judged after every other body check, so a rep any of them refuses keeps its words.
- **Rationale**: On 7 Oct a Daily 25 at 45° counted sixteen crunches and two kettlebell stand-ups among seven push-ups, and three crunches at the end of another set: the arms bent and straightened, and the body line read straight lying on the back (152–177°) and standing. The Daily 25 did not judge knees, and nothing checked that the body was face down. Every labelled push-up, from the toes or the knees, read the knee −0.59 to 0.02 arm-lengths above the hip and the ankle at most 0.04 below the hand; crunches read the knee 0.51–0.81, standing the ankle 1.87–1.94. Silence, because there is nothing to coach.
- **Alternatives considered**: Torso elevation (rejected: at 45° it depends on the placement — honest reps from the older frontal fixtures read up to 81°, standing 85–86°); the hand under the shoulder (rejected: standing with a weight reads like the top of a push-up).

## D12. Labelled reps, the bench and the ratchet *(4–8 October)*

- **Decision**: A reviewer labels every rep of a sitting from its video in a local page (`scripts/label-reps.mjs`): honest, half, knees, floor, cobra, other, unseen or unsure. Today's counter is replayed over each sitting (`todayVerdicts`), and each replayed rep is paired with at most one recorded rep or label — the nearest within 400 ms, else the one rep left inside its stretch. **The bench** scores every labelled rep against a gate (honest ≥ 98 % counted, each cheat ≤ 2 %), counting a knee rep as one that should count wherever knees are not judged (the Daily 25, Easy). **The ratchet** holds every reviewer-confirmed labelled rep to `fixtures/rep-traces/baseline.csv` and fails when one does worse. A rep counted where no label lies fails the gate and is held as wrong until it is labelled; a rep only today's counter counts is listed in the page so it can be; labels Claude set are not ground truth until a reviewer confirms them. Sittings today's app would not start — head-on, in front of their stakes' band, or read in the old 9:16 crop — stay as evidence, not scored.
- **Rationale**: A count within one rep of a person's total hides a cheat counted and an honest rep refused in the same set; labelled reps do not. The ratchet lets no change trade anything silently: one person's honest reps for another's cheats, or a new count between labelled reps.
- **Alternatives considered**: Totals per trace only (`RecordedTracesTest`, kept for unlabelled traces); hand-checking frames per change (slow, and not repeatable).
