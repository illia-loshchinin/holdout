# Data Model: Rep Placement and Form Rules

All types live in `kotlin/repcounter` unless noted. Nothing here is on-chain. *Refreshed 2026-10-08: one placement for every sitting, the Daily 25 held to Easy, head-on not read, face down (research D10–D12).*

## Stakes

| Value | Cohort form | Placement band | Knees off the floor judged | Legs in view, body line, hip rise, face down |
|---|---|---|---|---|
| `STAKED` — a Hard Holdout | 0 (21-day) | 45°: placement ratio over 0.25 and under 1.1 (about 60° to 35°) | yes | yes |
| `EASY` — an Easy Holdout (H35) | 1 (7-day) | 45°: over 0.25 and under 0.90 (about 60° to 40°) | no — knee push-ups count | yes |
| `DAILY` — the free Daily 25 | — | as Easy *(since 2026-10-08; was head-on or 45°)* | no — knee push-ups count | yes |

`Stakes.staked` is only "money is on it" (a rule sentence before staking). `Stakes.judgesKnees` is Hard only. Every stakes accepts `OBLIQUE` alone and says "Turn the phone to 45°" when refused. `Stakes.STAKED_RULE` and `EASY_RULE` are the one sentence each, wherever the rule appears (FR-014). `Stakes.ofForm(form)` picks the stakes a cohort is counted at.

## CameraView (placement)

`FRONT` (head-on), `OBLIQUE` (about 45°), `SIDE` (square side-on). Classified from shoulder span ÷ torso length while the ready position is held — `SIDE` ≤ 0.25, `FRONT` from the stakes' front line (1.1 Hard, 0.90 Easy and the Daily 25) — then **locked for the sitting** (FR-005). Only `OBLIQUE` is ever counted: `FRONT` and `SIDE` exist so the camera check can say which way to turn. A `RepCounter` cannot be built for `FRONT`; `SIDE` is the synthetic traces' state-machine view only.

## ViewReading (one frame)

- `Angled(elbow, bodyLine, knee, legsAboveHands, shoulderOverHand, kneeUnreadable, hipAboveHand, kneeAboveHip, ankleBelowHand)` — near side only, in the picture. `bodyLine` is shoulder–hip–ankle on Hard, and the straighter of that and shoulder–hip–knee where knee push-ups count.
- No reading head-on *(`HeadOn` removed 2026-10-08)*: a `FRONT` frame is dropped.

Lengths are in arm-lengths, so the distance from the phone does not matter.

## RepRules (defaults)

| Rule | Value | Applies |
|---|---|---|
| Top / bottom elbow | 160° / 90° (triggers 160° / 85°); the top at 45° is the arm held through the countdown less 10°, between 148° and 160° | all |
| Descent starts the rep clock | elbow < 140° | all |
| 45° depth | shoulder over hand ≤ 0.32 — or ≤ 0.36 with the elbow closed to 66° or less | all |
| Body line, rep median | ≥ 140° | all |
| Body line, push median | ≥ 145° | all |
| Body as one piece | hip rise over shoulder rise, half-way up the push, ≥ 0.90 (placement ratio < 1.1; Hard: a rise that cannot be read is not credited) | all |
| Face down | over the rep, knee above hip ≤ 0.3 and ankle below hand ≤ 0.6 (arm-lengths) *(2026-10-07)* | all |
| Legs in view | near knee readable and ≥ 13 % inside every edge in ≥ 75 % of the push and of the way down | all *(Daily 25 since 2026-10-08)* |
| Knees down | push: legs above hands < 0.41, or knee < 90°, or knee < 135° (from 120° only if lowered on a knee under 112°) with legs < 0.48; way down: knee < 90°; legs on the push < 0.66 of the plank held through the countdown | Hard |
| Push phase | from the bottom while elbow ≥ 100° | all |
| Tempo | 0.8–6 s | all |

*Retired 2026-10-08 with head-on:* torso elevation ≤ 55°, head-on depth (shoulder height at the bottom ≤ 0.285 of the top).

## The held plank

Legs above hands through the countdown, from frames with the arms straight, the body in line and the knees seen off the floor — this person's plank on the toes. Hard needs at least 10 such frames before counting may start (`RepCounter.plankCalibrated`); a Hard countdown stops if the knees come down, the knee cannot be placed, or the body breaks line. A countdown held from the knees in the Daily 25 or Easy sets no plank.

## Rep verdict order

face down → legs in the picture → too shallow → knees (Hard: push, way down, own plank) → body held → hip rise → tempo → **counted**. Each refusal carries a `RepHint` shown and spoken ("Keep your legs in the picture", "Go lower", "Knees off the floor", "Keep your body straight", "Slow down"); a rep not face down, and a rep slower than 6 s, are refused in silence. Depth comes before knees (3 Oct): a half push-up on the toes reads the legs lower and the knee more bent than a full one. Face down comes first *(since 8 Oct, `6894c3c`; it was last on 7 Oct)*, so a rep that is not a push-up at all is refused in silence whatever else it got wrong; every other refusal keeps the order above and its words. The order picks the words only; a rep counts when it passes every check.

## RepStance (`RepEvent.Counted.stance`)

For the toes meter: whether Hard would have counted a rep the Daily 25 counted — `TOES`, `KNEES` (Hard's knee rules, own plank included), `NOT_VISIBLE` — at 45° only.

## Set-up guide preference (`app`, MMKV)

`setup_hidden:<wallet>:<stakes>` — true once the member ticks "Don't show this again". The guide can still be reopened.

## Trace (Holdout Lab, `app` lab flavor → file)

One per Lab set (before 2026-10-09, one per debug-build sitting), `files/traces/<time>-<stakes>.trace.csv` in Holdout Lab's own storage (`holdout.app.lab`). Format: [contracts/trace-format.md](contracts/trace-format.md). Lifecycle: opened when the sitting starts → header flushed at once → rows flushed every 30 frames or on an event → closed on Stop, completion or leaving the screen (never reopened) → optionally labelled. With "record video" on, `<trace>.mp4` and `<trace>.video.json` (recording start on the boot clock) beside it.

## Trace label

`expectedReps` = full push-ups, plus knee push-ups where they count (the Daily 25, Easy). `done` = count per movement: `honest`, `half`, `knees`, `cobra`, `other`. `note` = one line, `=` replaced by `:`.

## Rep labels (`<sitting>.labels.csv`, `scripts/label-reps.mjs`)

Header `# key=value` lines (`trace`, `offset` — the trace's lag behind the video, `person`, `clothing`, `reviewed` — the date "Sitting finished" was ticked, `source` — the sitting a fixture was promoted from), then `t,label,by,flag,hint,note,claude,reply`:

- `t` — the trace time the rep ended; a rep only today's counter counts is labelled at its own time.
- `label` — `honest`, `half`, `knees`, `floor`, `cobra`, `sag`, `other`, `unseen` (honest, but the legs out of the counter's picture), `unsure`.
- `by` — `reviewer` or `claude`. A label Claude set is not ground truth until a reviewer confirms it.
- `flag` — `check` when the reviewer asks Claude to look, `answered` once Claude has.

A sitting is promoted to `fixtures/rep-traces/` (trace gzipped, labels copied, `source=` set), and its video may be deleted 30 days after it was finished, only when every candidate rep — recorded, or counted only today — has a label a reviewer confirmed, and the promoted labels are the working ones.

## Baseline (`fixtures/rep-traces/baseline.csv`)

`fixture,t,label,state,verdict` — today's counter on every reviewer-confirmed labelled rep of every scored fixture: `right`, `words` (refused as it should be, in other words than the label calls for) or `wrong`; a rep counted where no label lies is held as `unlabelled-count`, `wrong`, until labelled. `RepRatchetTest` fails when any row does worse, or better until `./gradlew :repcounter:updateBaseline` writes it.
