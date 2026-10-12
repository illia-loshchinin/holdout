# Quickstart: checking rep placement and form rules

*Refreshed 2026-10-08: every sitting at 45°, the Daily 25 held to Easy, face down, the bench and the ratchet.*

## 1. Replay the recorded sittings (no phone needed)

```bash
cd kotlin && ./gradlew :repcounter:test
```

Expected: all tests pass. `RecordedTracesTest` replays every counted trace in `fixtures/rep-traces/` that today's app would start, within one rep of the person's count, and named sittings rep by rep; `RepRatchetTest` holds every reviewer-confirmed labelled rep to `fixtures/rep-traces/baseline.csv`. The head-on traces (`front-*`) are evidence only and are not replayed. Together they are the evidence for SC-001, SC-002, SC-002a, SC-002b and SC-002c.

## 2. Score the labelled reps (no phone needed)

```bash
cd kotlin && ./gradlew :repcounter:testBench
```

Expected: a table per label, person and sitting, every miss with its time and notes, and the gate (honest ≥ 98 % counted, each cheat ≤ 2 %; a knee rep counts where knees are not judged). "Counted where no rep is labelled" lists counts nobody has vouched for — label them (§4). Point it at the phone's recordings with `-Ptraces=../phone_traces`.

## 3. On a phone (Holdout Lab)

```bash
cd kotlin && ./gradlew :app:installLabDebug
```

Holdout Lab (spec 004) opens on the counting mode: **Daily 25**, **Easy Holdout** or **Hard Holdout** (a Hard sitting without a stake). Its switches: **TEST · clean screen** (nothing drawn over the camera) and **record video** (consenting people only). Every set starts from zero. No wallet is needed. The member app (`installMemberDebug`) counts the same way but records nothing and shows none of these switches.

| Check | Do | Expect |
|---|---|---|
| US1-1 | Daily 25, phone head-on | camera check red: "Turn the phone to 45°", no countdown |
| US1-2 | Daily 25, phone at 45°, 10 full push-ups | 10 counted, a tone and "+1" each |
| US1-3 | Daily 25, phone square to your side, or turned in front of about 40° | red, and it says which way to turn your body |
| US1-4 | Daily 25 at 45°: 5 crunches on your back, then stand and bend your arms 5 times | 0 counted, nothing said |
| US1-5 | Daily 25 at 45°, 5 push-ups from the knees | 5 counted; the toes meter says they were not from your toes |
| US1-6 | Daily 25 at 45°, 5 half push-ups | 0 counted, "Go lower" |
| Legs | Daily 25 at 45°, your knees outside the picture | no start: "Keep your knees in the picture — move the phone further away" |
| Knees visible | Daily 25 at 45°, turned so the near knee is hidden behind your body (or before the camera has found your pose) | the Knees visible pill is red, never green, with "Turn your body slightly to your left (right), away from the phone, so it sees your knees" ("…a little away from the phone…" when the side is unclear); no countdown. With the head or feet out of the picture the coach says "Further" first, not "Turn slightly away" |
| Guide | Open the set-up guide, then go through its three tabs (1. Positioning · 2. Rules · 3. Start), once as a Habit, once as a Spark, once in the Daily 25 | no intro paragraph under the title; Positioning shows the animated "At 45°" card; Rules lists that sitting's own lines, "All the way down and up — short reps are skipped." and the "What you'll hear and see" card ("Knees off the floor" in the Habit's only); Start ends "Every 10 push-ups are saved on your phone." and "Stop any time with ✕ in the corner." *(FR-015, FR-015a, 2026-10-12)* |
| Place | Start a sitting with the phone in your hand, then stand it on the floor | the coach says "Place your phone" once (not every 5 seconds) with "On the floor, ahead of your shoulder, turned to you at 45°." under it and no camera outline; once the phone stands the outline is the dashed box alone, with no push-up figure; in the last 10 minutes of a day neither the pre-flight page nor the counting page shows "Under 10 minutes left…", only the time line turns warning *(FR-018, FR-019, FR-020b, 001 FR-047)* |
| Tour | Open the tour (About): card 1 ends with "Each day done grows your streak." and no rule sentences, and card 2 says "knees allowed" for Spark and "toes only" for Habit; go to card 4 of 5, which has no heading | the three steps, the one green "Private and fair by design" note and the "from above" panel with the top view held still at the bottom of the push-up, left of the rep count and not covering it, labelled "~1.5m ahead of your shoulder" and carrying two "45°" labels (beside the elbow and at the phone); the line "No microphone, ever. Reps are counted on this phone — no video or body data leaves the app." *(FR-013)* |
| Tips | Any sitting, the phone leaning so one check stays red: wait 30 seconds; or make a green check go red three times | before either, no button; then "Still not green? Tips" under the fix sentence, never opened by itself; it opens "Getting the checks green" with every pill's label and fix sentence, the red ones marked "Not green yet", and "Open the set-up guide" / "Back to the checks"; either button, Back, a tap outside and a drag close it, the checks keep running and no countdown starts; a new sitting has no button |
| US2-2 | Hard Holdout, 45°, 5 from the knees (feet down, then raised) | 0 counted, "Knees off the floor" |
| US2-3 | Hard Holdout, phone head-on | red: "Turn the phone to 45°", no countdown |
| Cobra | 45°, lie flat, press only the chest up, 5 times | 0 counted (at most one slips through), "Keep your body straight" |
| US3 | Start a sitting | set-up guide first; tick "Don't show this again", start again: not shown |
| FR-024 | Put your head out of the picture while getting ready | "Keep your head in the picture"; nothing said about the head once counting |
| FR-025 | Press Stop in Holdout Lab | "What did you just do?" form; Save writes it into the trace |

## 4. Turn a phone session into a test

```bash
bash scripts/pull-traces.sh
```

It reads Holdout Lab (`holdout.app.lab`). Traces and videos land in `phone_traces/` (git-ignored); videos are deleted from the phone once copied. Traces left in the member app by debug builds made before 2026-10-09 are drained once with `--package holdout.app`.

```bash
cd kotlin && ./gradlew :repcounter:todayVerdicts
```

Then label every rep from the video:

```bash
node scripts/label-reps.mjs
```

Open http://localhost:8766: the queue lists Claude's answers, Claude's labels to confirm, reps only today's counter counts ("not recorded"), then reps nobody has labelled. Tick "Sitting finished" when every rep has your label, then:

```bash
node scripts/label-reps.mjs --promote
```

A sitting is promoted only when every candidate rep has a label you confirmed. Then `cd kotlin && ./gradlew :repcounter:updateBaseline` and commit `fixtures/rep-traces/`; §1 and §2 then cover it.
