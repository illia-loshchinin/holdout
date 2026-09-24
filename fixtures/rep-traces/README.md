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
| `angled-honest-01` | 45° | honest set | 8 of about 9 (was 0 before the median knee rule and the 160° top) |

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
