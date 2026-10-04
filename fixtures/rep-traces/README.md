# Rep traces — what spike S2 hands to the rep counter

`kotlin/repcounter` is a plain JVM module on purpose: the push-up rule is tested
against recorded landmark traces, including deliberate cheats, without a phone
(T049). This directory holds those recordings.

Spike S2 (T012b) produces the first and most important one. Nothing in T046–T050
starts before it exists, because the hysteresis triggers in T048 are **set from
measured angles**, not guessed.

## `s2-angles.json` — the spike's output

One entry per recorded video. Angles are degrees, times are milliseconds from
the first frame of the sitting.

```json
{
  "capturedOn": "2026-09-23",
  "device": "Solana Seeker",
  "model": "pose_landmarker_full.task",
  "delegate": "GPU",
  "traces": [
    {
      "id": "honest-side-01",
      "kind": "honest",
      "view": "side",
      "expectedReps": 25,
      "fps": 24.6,
      "frames": [
        { "t": 0, "elbow": 171.2, "bodyLine": 178.4, "presence": 0.94 },
        { "t": 41, "elbow": 168.0, "bodyLine": 178.1, "presence": 0.93 }
      ]
    }
  ]
}
```

| Field | |
|---|---|
| `kind` | `honest`, or the cheat it is: `cheat-half`, `cheat-sag`, `cheat-fast` |
| `view` | **Required.** `side` or `front` — where the phone was. Both placements are accepted by the app, so both have to be measured, and a trace that does not say which it is cannot be used to set a threshold for either |
| `expectedReps` | what a human counted watching the video — the only ground truth there is |
| `fps` | measured inference rate for that recording. S2's bar is **≥ 15** on a Seeker |
| `elbow` | shoulder–elbow–wrist, from 3D world landmarks, one-euro smoothed (R4) |
| `bodyLine` | shoulder–hip–ankle. 180° is straight; the rule allows ±10° |
| `presence` | the model's landmark presence score. Frames below the threshold are **dropped, not scored** (T048) — keep them in the trace so the test can prove they are dropped |

## `front-honest-01.csv` — the first head-on recording

Two honest push-ups with the phone on the floor in front of the member, re-run
through `pose_landmarker_full.task` from a screen recording. Unlike
`s2-angles.json` it keeps **raw landmarks** (world and image, for the ten joints
the rule reads), because head-on the angle itself is what had to change: the
world elbow never read above ~163° or much below 90° here, while the picture
read 175–179° and ~40°. `FrontViewTest` replays it and must count 2; read the
old way it counts 0. The header gives the picture size and the column order.

## What the head-on recordings showed about knees

A second recording (`phone_screens/screen-20260923-25-reps.mp4`, not kept here)
did knee push-ups at an angle and head-on, and knees-down rocks with the hips
pushed back. Every landmark signal tried — the knee angle in 3D and in the
picture, the knees' height above the hands, how far the hips drop against the
shoulders — overlapped with the honest set. That is why staked sittings are
at 45° only, while the Daily 25 keeps head-on as well (FR-021a, `Stakes`). The same recordings set the view cut-off: shoulder span against torso length stayed at or under 1.35 at 45° (95th percentile) and never fell below 1.73 head-on (5th), so the cut-off is 1.55 — the old 0.9 called most 45° frames head-on.

At about 45° the picture does show the knee — on the **near** leg; the model
places the far leg badly. Near-leg knee minimum per knee push-up on that
recording: 74°, 89°, 102°, 93° in the clearest reps, 142° and 98° in the
earlier, flatter ones. The 150° threshold is provisional until S2 records an
**honest** angled set and an honest side-on set: the question is whether real
push-ups stay above it there.

## Phone traces (`*.trace.csv`) — recorded by the app itself

Debug builds write one trace per sitting (landmarks and the counter's
decisions, never a picture — `kotlin/repcounter` `Trace.kt`);
`scripts/pull-traces.sh` fetches them over USB. `RecordedTracesTest` replays
every trace whose header carries `expectedReps=N` and requires the count to be
within one of it. A trace with `expectedReps=?` is evidence, not a test. A
trace may be gzipped (`*.trace.csv.gz`); the tests read either.

| File | Placement | What was done | Counts |
|---|---|---|---|
| `front-honest-02` | head-on | honest set (24 Sep) | 11 of a stated 10 |
| `front-honest-03` | head-on | 10 honest | 10 |
| `front-half-01` | head-on | half push-ups | 0 (was 3 before the shoulder-depth rule) |
| `front-half-02` | head-on | 5 half push-ups | 0 |
| `front-knees-01` | head-on | 5 push-ups from the knees, Daily 25 | 5 — **correct**: knee push-ups count in the Daily 25 (FR-021a). It also shows why staked sittings are 45° only: head-on, knee angle, knee and ankle height, hip height, torso angle and body line all overlapped the honest set; the closest signal (hip width ÷ shoulder width, 0.48–0.49 honest vs 0.50–0.53 knees) is one session's 0.01 margin |
| `angled-honest-01` | 45° | honest set, Daily 25 | 8 of about 9 (was 0 before the median knee rule and the 160° top) |
| `angled-honest-02` | 45° | 10 honest, staked | 10 |
| `angled-knees-cobra-01` | 45° | staked: push-ups from the knees, then cobra reps (lying flat, chest pressed up, hips on the floor) | 0 — knees refused by the median knee rule; cobras (5 had counted) refused by the body line on the push back up: 120–134° against 157–174° honest |
| `angled-half-01` | 45° | staked: half push-ups | 0 — was 7 of 11 on the elbow alone (some half reps read 55–67°, as deep as honest). Refused by depth at 45°: at the bottom the near shoulder stood 0.43–0.68 arm-lengths over the hand; honest reps 0.20–0.28, line at 0.32. On the descent rule (3 Oct) the last rep counts: elbow 55°, shoulder 0.30 over the hand — as deep as honest reps, where the other ten stopped at 0.43–0.68; before, only the bent-knee line refused it. No video to check |
| `angled-mixed-02` | 45° | staked: 10 honest, 5 cobra, 5 push-ups from the knees with the shins flat | 10 — the shallow kneel had counted (knee 124–139°, too close to honest); refused by the legs' height above the hands on the push: honest 0.44–0.65, knees 0.32–0.39 |
| `angled-mixed-03` | 45° | staked: 10 honest, 5 cobra, 5 from the knees — recorded on the build with the legs-above-hands rule | 10, live and on replay |
| `angled45-right-mixed-04` | 45° measured on the floor, phone on the right, 4:3 at 480×640 | staked: 10 honest, then 5 from the knees, then 5 cobra | 10 — had counted 0: the locked-out arm reads 150–158° here, under the 160° top. Counted once the top is the arm held through the countdown less 10° (floor 148°); the knee and cobra reps are all still refused |
| `angled50-right-mixed-05` | 50°, as above | staked: 10 honest, 5 from the knees, 5 cobra | 10 — had counted 0, as above |
| `angled50-right-mixed-06` | 50°, phone on the right, 4:3 at 720×960 — the first set recorded **on** the per-sitting top | staked: 10 honest, 5 from the knees, 5 cobra, 5 half | 10, live and on replay — ten in a row, then all 15 cheats refused (knees and cobras as "Knees off the floor", halves as "Go lower"). Arm held at 156° through the countdown, so the top sat at the 148° floor; tops read 154–163°. Hold reached in 27 s (68 s before); 23 analysis frames a second while counting, with HD video recording |
| `angled55-right-mixed-07` | 55°, phone on the right, 720×960 | staked: 10 honest, 5 from the knees, 5 cobra, 5 half, 1 honest | 11, live and on replay — every cheat refused under its own hint. Placement ratio 0.33 / 0.47 / 0.60 (5th / 50th / 95th); read as 45° in 95 % of counting frames, side-on in none |
| `angled40-right-mixed-08` | 40°, phone on the right | staked: 5 honest, 5 half, 5 from the knees, 5 cobra | 5, live and on replay. Three of the half push-ups stopped with the shoulder 0.34–0.35 arm-lengths over the hand and the elbow at 69–71°: they pin the elbow side of the depth band (`deepElbowDegrees` 66). Ratio 0.53 / 0.72 / 1.02 |
| `side-right-hold-01` | square side-on, 1–1.5 m away | staked: a plank held about 10 s | 0 — it never starts: side-on in every frame of the hold (ratio median 0.13 against `sideRatio` 0.25) |
| `angled60-right-knees-down-up-09` | 60°, phone on the right | staked: 5 honest, 5 from the knees, then knee push-ups rocking back to child's pose — one of them let down from the knees and pushed up on the toes | 5 — had counted 6: knees were judged on the push only, and that one's push was honest (knee 154°, legs 0.45). Now the knee angle is judged on the way down too: 77° there, against 108° at the least on honest descents. Ratio 0.27 / 0.46 / 0.83 — 60° is the edge of the band, side-on in 2 % of counting frames |
| `angled-right-honest-10` | an unmeasured spot near 40° (ratio 0.60 / 0.79 / 0.96), phone on the right | staked: 12 honest | 12 — had counted 9: three stopped with the shoulder 0.33–0.34 over the hand (limit 0.32) and the elbow at 62–64°. Counted since the 0.32–0.36 band passes with the elbow at 66° or less |
| `angled45-left-mixed-11` | 45°, phone on the **left**, 720×960 | staked: full, from the knees, half, cobra, then two let down on the toes and pushed from the knees | 11, live and on replay (labelled 10 on the phone; 11 confirmed on video). All 17 cheats refused. Held ratio 0.44 (45° on the right holds 0.76): on the left the same floor mark reads much nearer side-on, and 18 % of counting frames fall under `sideRatio`; the near knee sat within the edge margin in 13–25 % of honest pushes, against the 25 % limit |
| `front-honest-knees-04` | head-on, Daily 25, 720×960 | 10 full and 5 from the knees (the Daily 25 counts both) | 14 — had counted 8: the shoulders came down to 0.22–0.30 of their top height and the 0.25 line cut the set in half. Line now 0.285 |
| `angled30-right-honest-12` | about 30–35°, close to the body, phone on the right (held ratio 1.26) | staked: 5 honest | 5, live and on replay |
| `angled30-right-knees-feetup-13` | about 30°, held ratio 1.08 | staked: 2 full and 4 half (confirmed on video), then 5 from the knees with the feet raised, then cobras | 2 on replay — live it counted 7: five of the knee and cobra reps read a knee of 90–112° with the legs at 0.43–0.45, over both lines. Refused since a knee under 135° needs the legs at 0.48 |
| `front-honest-05` | head-on, Daily 25, 720×960 | 10 honest, recorded on the 0.285 depth line | 9, live and on replay — the first rep read 0.30 and was refused, the other nine 0.24–0.28 |
| `angled30-right-knees-14` | about 30°, phone on the right (held ratio 1.05) | staked: 5 from the knees with the feet raised, 5 with the feet down — recorded on the bent-knee rule | 0, live and on replay: all ten refused as "Knees off the floor" |
| `angled45-left-floor-start-15` | 45°, phone on the left, a little further back than `-11` (held ratio 0.57) | staked: 11 full, 5 from the knees, 2 cobras, 3 let down onto the floor and pushed up from there | 9 — live it counted 10: nine full reps and one of the floor-start reps (legs 0.42, knee 141°, push body line 152°). That one is now refused by the hip rise (0.87 against 0.90). Two full reps were refused "legs in the picture" with the knee 13–17 % inside the frame: the left foot sat 2–5 % from the edge and dropped out of the model's confidence mid-rep. The set-up now asks for the foot 6 % inside |
| `angled45-right-loose-trousers-cobra-16` | 45°, phone on the right; a second person, loose white trousers | staked: six cobras done with the body nearly straight | 0 — live it counted 3 (push body line 145–150°). Hip rise 0.63–0.72 on all six. The knee unreadable in a third of frames |
| `angled45-right-loose-trousers-hold-17` | as above | staked: a proper plank held for most of a minute | it never starts: the model puts the knee on the ankle and the knee angle reads 10–120°. Live, four green pills and "Hold", and no word why; now "Can't see knees" with the reason under it |
| `angled45-right-mixed-18` | 45°, phone on the right (held ratio 0.93) | staked: 10 full, 5 from the knees, 4 pushed up from the floor, 1 with the body bent, then half push-ups on the toes with two full ones among them | 12 (labelled 10 on the phone; 1:59 is as deep as the first ten on video, and the member counts 2:07, at the depth line, as full). Live it counted 10: five in the half block were told "Knees off the floor". Four were halves — the leg a little bent (knee 127–132°), the legs at 0.41–0.48, one 0.408: half reps read the legs lower than full ones. Depth is now judged before knees, and all eight halves say "Go lower". The fifth, 2:07, read knee 130° and legs 0.47 on the push but came down at 135°: the bent-knee line now needs a bent knee on the way down too (under 112°) when the push reads 120–135° |
| `angled40-right-mixed-19` | about 40°, phone on the right (held ratio 1.01); a second person, white T-shirt, loose shorts | staked: 10 full, 5 from the knees, 3 cobras with the thighs on the floor, 2 half | 10 — live it counted 15. Two knee reps (4:04, 4:11) read the legs at 0.42–0.44, over the 0.41 line set on one body (his honest reps 0.51–0.79); refused since the legs are judged against his own plank too (0.60–0.63 of it; honest 0.70 or more everywhere), with one cobra. Two cobras (4:48, 4:59) still count: at the bottom his head covers the near hand and the model misplaces the wrist (it jumps 0.49–0.62 of the picture height while the hand is planted), so the elbow reads 20–41° and the hip rise is not to be trusted. Pinned at 12 in `RecordedTracesTest` |
| `angled40-right-floor-20` | about 40°, phone on the right (held ratio 0.91) | staked: 10 full, 5 from the knees, 5 let down until the hips rest on the carpet and pushed up straight in one piece, 1 from the knees | 10 (owner, 4 Oct: a push-up that touched the floor does not count) — counts 14: four of the floor reps (1:05–1:22) pass every check, the body coming up as one piece (hip rise 1.05–1.07). Within this set their hips stand lower at the bottom (0.28–0.34 of the held plank, honest 0.43–0.59) and the push body line reads 145–159° (honest 175–178°), but other people's honest reps read the same, so neither can be a line. Pinned at 14 in `RecordedTracesTest` |
| `20260927-171132-staked`, `20260927-181344-staked`, `20260929-153345-staked`, `-154046`, `-155215`, `-155837`, `-161244`, `-163059` (`.trace.csv.gz`) | 4:3 staked sittings, 27 and 29 Sep, promoted with their labels | 5–6 honest each, labelled from video | 49 honest labelled; 36 counted, 13 refused (6 "Keep your body straight", 5 "legs in the picture", 1 knees, 1 not seen as a rep). Held by the ratchet, not by `expectedReps` |
| *(note, 2 Oct)* | — | A staked sitting no longer starts from in front of about 35° (placement ratio 1.1 or more). The staked sets above that were recorded there — the September "45° by eye" sets, which held 1.1–1.4, and `angled30-right-honest-12` — stay as tests of the counter; on the phone today they would be asked to turn first | — |
| `angled-knees-out-of-frame-01` | 45° | staked: 5 full push-ups with the knees out of the **counter's** picture (Seeker, 29 Sep, a 10-minute test day). The debug video of the same set shows the whole body: it records the sensor's full 4:3 width, while analysis and the preview use a 16:9 crop, about 12 % narrower each side — the knees were inside the one and outside the other | 0 — all 5 had counted: the model placed the knees just inside the frame at full confidence. Refused as `LEGS_IN_VIEW` once the knee spends over 25 % of the push within 13 % of an edge; it was there 28–50 % of every rep, honest sets 0–3 % |

## Rep labels (`*.labels.csv`) and the test bench

A trace's `expectedReps` says how many reps a person counted; it cannot say
**which** reps were honest, so a counter that refuses one honest rep and counts
one cheat still passes. A labels file says it rep by rep, from the video:

```
# holdout-rep-labels v2
# trace=angled40-right-mixed-19.trace.csv
# source=20261004-071455-staked   the phone recording it came from
# person=p2                       a pseudonym, never a name
# clothing=loose white T-shirt, loose shorts
# offset=0.5                      seconds the trace runs behind the video
# reviewed=2026-10-04             "Sitting finished": every rep labelled, the labels final
t,label,by,flag,hint,note,claude,reply
2840597786,honest,reviewer,,,too close to honest - let's count this.,"3:07, before the full reps; not checked",
```

- `t` — the trace time of the row the rep's verdict was written on.
- `label` — `honest`, `half`, `knees`, `floor`, `cobra`, `sag`, `other`, or
  `unsure` (left out). Only `honest` should count. Knees, floor and cobra
  overlap (the knees touch in all three), so the top of the rep decides first:
  kneeling at the top is `knees`, whatever happens at the bottom; on the toes at
  the top but resting on the floor at the bottom (hips or thighs carrying
  weight) is `floor`; hips that never leave the floor is `cobra`.
- `by` — who set the label: `reviewer`, or `claude` until a reviewer confirms it.
- `flag` — `check` when the reviewer ticked "Review & fix": the rep is taken
  up for analysis and fixing. Only ticked reps are. `answered` once Claude has
  replied; the reviewer closes it with Done, or ticks it again to reopen.
- `hint` — the `RepHint` the reviewer would want the rep refused with
  (`GO_LOWER`, `UP_ON_YOUR_TOES`, `HIPS_OFF_FLOOR`, …); empty for no preference.
- `note` — the reviewer's; `claude` — Claude's, shown read-only; `reply` —
  Claude's answer to a ticked rep. Quoted when they hold a comma.

A v1 file (`t,label,note`) still reads, as the reviewer's labels and notes.

- **Labelling**: `node scripts/label-reps.mjs` serves `phone_traces/` on this
  machine only (http://localhost:8766). It opens on **Needs you**, one queue
  across every sitting: Claude's answers to read, Claude's labels to confirm
  (those the app gets wrong first), then reps nobody has labelled. Each rep
  plays as a loop with the skeleton the counter reads drawn bold; `Enter`
  confirms the label on screen, `1`–`7` and `0` set one, `f` ticks "Review &
  fix", `h` picks the words it should be refused with, `d` closes Claude's
  answer — and the queue moves on. Labels save beside the trace; "Sitting
  finished" ticks itself when every rep has a label the reviewer set or
  confirmed. The page reads the file again when it comes back to the front, and
  keeps an answer Claude wrote meanwhile.
- **Today's verdicts**: `./gradlew :repcounter:todayVerdicts` (from `kotlin/`)
  replays every sitting with a video through today's counter and writes
  `<sitting>.today.csv`; the page then shows "today" beside the verdict the app
  gave when the sitting was recorded, so a fix shows on the reps it put right.
- **Handing over**: say "check the labels". `node scripts/label-reps.mjs
  --flags` lists every sitting with labels and every rep ticked "Review & fix"
  and not yet answered — only those are taken up. A changed label or chosen
  words without the tick is the reviewer's record, not a request. Claude
  answers each in the file with `node scripts/label-reps.mjs --reply <sitting>
  <t> <answer>`, and the answer is first in the reviewer's queue.
- **Scoring**: `./gradlew :repcounter:testBench [-Ptraces=<dir>] [-Pgate]`
  replays every labelled sitting and reports, per label, per person and per
  sitting, how many reps counted, every miss with both notes, and the reps
  ticked "Review & fix" with the words the reviewer wants. The
  gate: honest reps counted 98 % or more, each cheat 2 % or less.
- **Promoting**: `node scripts/label-reps.mjs --promote` makes every finished
  sitting a fixture. A sitting already here (same frame count, first and last
  frame time) gets its labels beside that fixture; a new one comes in as
  `<sitting>.trace.csv.gz` with its labels. `source=` names the phone
  recording. Only traces and labels ever come in — never a video.
- **The ratchet**: `baseline.csv` holds how the counter does on every labelled
  rep here — `right`, `words` (refused, in other words than the label or the
  reviewer's "Should say" calls for) or `wrong` (a cheat counted, or a rep that
  should count refused; `not-judged` when no rep is seen where the label is).
  `RepRatchetTest` fails when any rep does worse than the baseline, and when one
  does better or is new or relabelled, until `./gradlew
  :repcounter:updateBaseline` (from `kotlin/`) writes it again — so a rep put
  right stays right, and a fix for one person cannot quietly undo another's.
  Commit the baseline with the change that moved it; its diff shows which reps
  did. 4 Oct: 344 labelled reps, 274 right, 31 words, 39 wrong.
- **Videos**: they stay in `phone_traces/` on this machine only, and go 30
  days after their sitting is marked finished, once it is promoted. Nothing is
  scheduled: `node scripts/label-reps.mjs --prune` lists the videos due and
  deletes them only on `y`, and the labelling page shows the same list with a
  button. A sitting whose video is gone still plays, as the skeleton alone.
- `RecordedTracesTest` replays every trace with `expectedReps` to within one;
  a labelled trace is held rep by rep by the ratchet instead.

## What the suite must hold — 10 honest, 3 cheats, **in each placement**

The spike's pass bar is the test's pass bar: every `honest` trace counted within
**1 rep** of its `expectedReps`, and every `cheat-*` trace rejected — **for the
phone in front and for the phone to the side**.

The `cheat-sag` traces matter most head-on. That is the measurement expected to
be weakest there, and these traces are what decides whether the front view needs
its own trigger set (`RepRules.forView`) or whether staked cohorts should
require the side view.

**T049 asserts the rule, never the trigger values.** The rule is FR-021 and
PRD §8 — top above 160°, bottom below 90°, body line held, cycle 0.8–6 s. The
triggers are an implementation detail (165° / 85°, tightened against the model's
~2° error) and S2 is allowed to move them. A test that pins 165 has to be
rewritten every time the counter is tuned, which is how a suite stops being
evidence and starts being a chore.

## Palm liveness

The 30-second timeout (T047, FR-020) needs no video: it is a clock, and the test
drives the clock. What S2 does have to answer is whether an open palm is
detected at all in normal indoor light — record that in the runbook's spike
table, not here.

## Recording

`docs/holdout_setup_runbook.md` step 7 has the procedure. Ten honest videos need
ten sets of 25; spread them over people and light if you can, since one person
in one room is one set of angles, not a sample.

## Calibration session — the 4:3 picture, 30–60° (2026-09-29, spec 002 T034c)

The camera now gives the counter the sensor's full 4:3 picture (T034b), and staked
sittings are meant to work anywhere from 45° to 55°. The placement thresholds were
set on 16:9 recordings at about 45°; they are converted exactly, but this session
checks them on real 4:3 frames and widens the 45° band if the data says so.

**The angle** is between your line from head to feet and the line to the phone:
0 would be the phone straight ahead of your head, 90 square beside you. On the
floor, measure from your shoulder 1 m **sideways**, then this far **ahead**:

| angle | ahead | | angle | ahead |
|---|---|---|---|---|
| 30° | 1.73 m | | 50° | 0.84 m |
| 35° | 1.43 m | | 55° | 0.70 m |
| 40° | 1.19 m | | 60° | 0.58 m |
| 45° | 1.00 m | | | |

**Set-up, once:** debug build, "record video" on, the Daily 25 home's *staked test*
switch on (staked rules, no stake). In the "What did you just do?" form after each
set, pick the angle and the phone's side — both are remembered for the next set.

**Record** (about 16 sets, 10 push-ups each unless said; 30° and 35° need more room ahead than most floors have, and are optional):

| placement | phone on your right | phone on your left |
|---|---|---|
| 45°, 50°, 55° | 10 honest · 5 from the knees · 5 cobra | 10 honest |
| 40°, 60° | 10 honest | — |
| head-on | 10 honest, *staked test* switched off (only the Daily 25 accepts head-on) | — |
| square side-on | hold the plank about 10 s — no sitting accepts side-on, so it never starts: press Stop and label it | — |

If the whole-body pill asks you to turn away or move the phone, do what it says
and note it in the form: those moments are part of what is being measured.

**Then:** `bash scripts/pull-traces.sh` and `./gradlew :repcounter:replayTraces`
(add `-Pdetail` for every cycle's verdict and the readings it was judged on)
(from `kotlin/`). The report gives, per labelled angle, the placement ratio's
5th/50th/95th percentile, what the camera check decided, and how many sets counted
within one rep of you. The 45° band's limits (`sideRatio`, `frontRatio`) are set
from it so every angle you want accepted falls inside; the traces that pin a
decision are committed here with the rest.
