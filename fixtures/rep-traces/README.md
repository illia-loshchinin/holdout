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
within one of it. A trace with `expectedReps=?` is evidence, not a test.

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
| `angled-half-01` | 45° | staked: half push-ups | 0 — was 7 of 11 on the elbow alone (some half reps read 55–67°, as deep as honest). Refused by depth at 45°: at the bottom the near shoulder stood 0.43–0.68 arm-lengths over the hand; honest reps 0.20–0.28, line at 0.32 |
| `angled-mixed-02` | 45° | staked: 10 honest, 5 cobra, 5 push-ups from the knees with the shins flat | 10 — the shallow kneel had counted (knee 124–139°, too close to honest); refused by the legs' height above the hands on the push: honest 0.44–0.65, knees 0.32–0.39 |
| `angled-mixed-03` | 45° | staked: 10 honest, 5 cobra, 5 from the knees — recorded on the build with the legs-above-hands rule | 10, live and on replay |
| `angled-knees-out-of-frame-01` | 45° | staked: 5 full push-ups with the knees out of the **counter's** picture (Seeker, 29 Sep, a 10-minute test day). The debug video of the same set shows the whole body: it records the sensor's full 4:3 width, while analysis and the preview use a 16:9 crop, about 12 % narrower each side — the knees were inside the one and outside the other | 0 — all 5 had counted: the model placed the knees just inside the frame at full confidence. Refused as `LEGS_IN_VIEW` once the knee spends over 25 % of the push within 13 % of an edge; it was there 28–50 % of every rep, honest sets 0–3 % |

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
(from `kotlin/`). The report gives, per labelled angle, the placement ratio's
5th/50th/95th percentile, what the camera check decided, and how many sets counted
within one rep of you. The 45° band's limits (`sideRatio`, `frontRatio`) are set
from it so every angle you want accepted falls inside; the traces that pin a
decision are committed here with the rest.
