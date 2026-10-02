# Data Model: Rep Placement and Form Rules

All types live in `kotlin/repcounter` unless noted. Nothing here is on-chain.

## Stakes

| Value | Accepted placements | Knees judged | Camera fix when refused |
|---|---|---|---|
| `DAILY` | `FRONT`, `OBLIQUE` | no | "Put the phone in front of you, or at 45°" |
| `STAKED` | `OBLIQUE` | yes | "Turn the phone to 45°" |

`Stakes.STAKED_RULE` is the one sentence shown wherever the staked rule appears (FR-014).

## CameraView (placement)

`FRONT` (head-on), `OBLIQUE` (about 45°), `SIDE` (square side-on). Classified from shoulder span ÷ torso length while the ready position is held (`SIDE` ≤ 0.5, `FRONT` ≥ 1.55), then **locked for the sitting** (FR-005). A `RepCounter` for staked stakes cannot be built for a view the stakes do not accept.

## ViewReading (one frame)

- `HeadOn(elbow, torsoElevation, shoulderHeight)` — for `FRONT`.
- `Angled(elbow, bodyLine, knee, legsAboveHands, shoulderOverHand)` — for `OBLIQUE`/`SIDE`, near side only.

Lengths are in arm-lengths so the distance from the phone does not matter.

## RepRules (defaults)

| Rule | Value | Applies |
|---|---|---|
| Top / bottom elbow | 160° / 90° (triggers 165° / 85°; 160° top at 45°) | all |
| Descent starts the rep clock | elbow < 140° | all |
| Torso down, head-on | elevation ≤ 55° | head-on |
| Head-on depth | shoulder height at bottom ≤ 0.285 × top | head-on |
| 45° depth | shoulder over hand ≤ 0.32 — or ≤ 0.36 with the elbow closed to 66° or less | 45° |
| Body line, rep median | ≥ 140° | 45° |
| Body line, push median | ≥ 145° | 45° |
| Knees down | push median legs-above-hands < 0.41, or push median knee < 90° | staked |
| Push phase | from the bottom while elbow ≥ 100° | 45° |

## Rep verdict order

knees (staked) → too shallow → go lower → body held → timing → **counted**. Each refusal carries a `RepHint` shown and spoken ("Knees off the floor", "Go lower", "Keep your body straight", …).

## Set-up guide preference (`app`, MMKV)

`setup_hidden:<wallet>:<stakes>` — true once the member ticks "Don't show this again". The guide can still be reopened.

## Trace (debug builds, `app` → file)

One per sitting, `files/traces/<time>-<stakes>.trace.csv`. Format: [contracts/trace-format.md](contracts/trace-format.md). Lifecycle: opened when the sitting starts → header flushed at once → rows flushed every 30 frames or on an event → closed on Stop, completion or leaving the screen (never reopened) → optionally labelled.

## Trace label

`expectedReps` = full push-ups, plus knee push-ups in the Daily 25. `done` = count per movement: `honest`, `half`, `knees`, `cobra`, `other`. `note` = one line, `=` replaced by `:`.
