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
      "id": "honest-01",
      "kind": "honest",
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
| `expectedReps` | what a human counted watching the video — the only ground truth there is |
| `fps` | measured inference rate for that recording. S2's bar is **≥ 15** on a Seeker |
| `elbow` | shoulder–elbow–wrist, from 3D world landmarks, one-euro smoothed (R4) |
| `bodyLine` | shoulder–hip–ankle. 180° is straight; the rule allows ±10° |
| `presence` | the model's landmark presence score. Frames below the threshold are **dropped, not scored** (T048) — keep them in the trace so the test can prove they are dropped |

## What the suite must hold — 10 honest, 3 cheats

The spike's pass bar is the test's pass bar: every `honest` trace counted within
**1 rep** of its `expectedReps`, and every `cheat-*` trace rejected.

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
