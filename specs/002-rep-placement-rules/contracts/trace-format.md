# Contract: Sitting trace, v2

Written by Holdout Lab (`kotlin/app/src/lab` `TraceRecorder`; spec 004, amended 2026-10-09 — before that, by debug builds), read by `kotlin/repcounter` `Trace.parse` and `RecordedTracesTest`. Landmark numbers only — never a picture (constitution X). The member app writes nothing, in any build. The format is unchanged by the move.

## Location

On the phone: the app's private `files/traces/*.trace.csv`, pulled with `scripts/pull-traces.sh` into `phone_traces/` (git-ignored). A useful one is committed to `fixtures/rep-traces/` with a descriptive name: `<front|angled[-left|-right]>-<movement>-NN.trace.csv`.

## Header

```text
# holdout-trace v2
# stakes=STAKED width=720 height=1280 expectedReps=10
# done=honest:10,half:0,knees:0,cobra:0,other:0      ← only once labelled
# note=phone on the floor at 45° on my right          ← optional
# landmarks=11,12,13,14,15,16,23,24,25,26,27,28,0
t,step,view,<w{i}x,w{i}y,w{i}z,w{i}p per landmark>,<i{i}x,i{i}y,i{i}p per landmark>,elbow,posture,knee,event
```

- `stakes`: `DAILY` or `STAKED`. `width`/`height`: the analysed frame, used to scale image coordinates to pixels.
- `expectedReps`: `?` until labelled; then what the rule **should** credit — full push-ups, plus knee push-ups in the Daily 25.
- `landmarks`: MediaPipe pose indices in file order. The nose (`0`) is listed since 2026-09-25; older traces omit it and still parse.
- Header lines are `#`-prefixed, space-separated `key=value` facts; a note can never forge one (`=` becomes `:`).

## Rows

| Column | Meaning |
|---|---|
| `t` | frame timestamp, ms |
| `step` | sitting step when the frame arrived (`PREFLIGHT`, `COUNTDOWN`, `COUNTING`, `COMPLETE`) |
| `view` | `FRONT` / `OBLIQUE` / `SIDE` as seen by the camera check, or the locked view while counting; empty if unknown |
| `w…` | world landmark x, y, z (metres) and presence |
| `i…` | image landmark x, y (0..1) and presence |
| `elbow`, `posture`, `knee` | the counter's smoothed readout, degrees; empty outside counting |
| `event` | `counted`, `discarded:<RepHint or none>`, or empty |

## Guarantees

- The header is on disk as soon as the sitting starts; rows are flushed at least every 30 frames and on every event.
- A trace is complete however the sitting ends, and is never reopened once closed.
- Committed traces with a numeric `expectedReps` are tests: replayed from their first `COUNTING` frame with the recorded stakes and view, the count must be within one of `expectedReps`.
