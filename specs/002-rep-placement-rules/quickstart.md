# Quickstart: checking rep placement and form rules

## 1. Replay the recorded sittings (no phone needed)

```bash
cd kotlin && ./gradlew :repcounter:test
```

Expected: all tests pass. `RecordedTracesTest` replays every labelled trace in `fixtures/rep-traces/` — head-on and 45°, honest, half, knees and cobra sets — and each counts within one rep of the person's count. A trace listed in the fixtures README table with its expected count is the evidence for SC-001, SC-002, SC-002a and SC-002b.

## 2. On a phone (debug build)

```bash
cd kotlin && ./gradlew :app:installDebug
```

On the home screen, debug switches: **TEST · staked rules** (a staked sitting without a stake), **TEST · clean screen** (nothing drawn over the camera) and **Reset today**.

| Check | Do | Expect |
|---|---|---|
| US1-1 | Daily 25, phone head-on, 10 full push-ups | 10 counted, a tone and "+1" each |
| US1-2 | Daily 25, phone at 45°, 10 full push-ups | 10 counted |
| US1-3 | Phone square to your side | camera check red: "Put the phone in front of you, or at 45°" |
| US1-5 | Daily 25, 5 push-ups from the knees | 5 counted |
| US1-6 | Head-on, 5 half push-ups | 0 counted, "Go lower" |
| US2-2 | Staked switch on, 45°, 5 from the knees (feet down, then raised) | 0 counted, "Knees off the floor" |
| US2-3 | Staked switch on, phone head-on | red: "Turn the phone to 45°", no countdown |
| Cobra | 45°, lie flat, press only the chest up, 5 times | 0 counted (at most one slips through), "Keep your body straight" |
| US3 | Start a sitting | set-up guide first; tick "Don't show this again", start again: not shown |
| FR-024 | Put the phone too close so your head leaves the picture at the bottom | "Head out of view · move back", spoken "Move back" |
| FR-025 | Press Stop | "What did you just do?" form; Save writes it into the trace |

## 3. Turn a phone session into a test

```bash
bash scripts/pull-traces.sh
```

Traces land in `phone_traces/`. A labelled one (header `expectedReps=N`) is moved to `fixtures/rep-traces/` with a descriptive name and added to the README table; step 1 then covers it.
