---
name: check-labels
description: Take up the reps the reviewer ticked "Review & fix" in the labelling page — analyse each, fix the counter or explain, have every answer independently verified, then write the answers back into the page. Use when the user says "check the labels", "check my flags", or names labelled sittings to look at.
---

# Check the labels

The reviewer labels reps in `node scripts/label-reps.mjs` (http://localhost:8766) and ticks
**⚑ Review & fix** on the ones they want taken up. Only ticked reps are requests: a changed label
or chosen words without the tick is the reviewer's record. Their label is the ground truth for what
happened on video — never re-label it; if you disagree, say so in the answer.

All commands run from `W:/Holdout` unless noted. `phone_traces/` is git-ignored and holds videos of
people who consented to be filmed — never commit or publish anything from it but labels promoted
with their trace into `fixtures/rep-traces/`.

## 1. What is asked

```bash
node scripts/label-reps.mjs --flags
```

Lists every labelled sitting (finished or not) and every rep ticked and not yet answered: sitting,
video time, label, what the app said, the words the reviewer wants, both notes.

## 2. Where the counter stands today

```bash
cd kotlin && ./gradlew -q :repcounter:todayVerdicts && ./gradlew -q :repcounter:testBench -Ptraces=../phone_traces
```

`todayVerdicts` writes `<sitting>.today.csv` (today's verdict per rep, shown in the page); the bench
scores every label per person and lists misses. A ticked rep the current code already gets right
needs only an answer saying so (and which commit did it).

## 3. Analyse each ticked rep

- Readings: copy the one trace into a temp dir and run `./gradlew -q :repcounter:replayTraces
  -Ptraces=<that dir> -Pdetail` — the rep's row shows depth, push knee and legs, descent knee, body
  lines, hip rise. Trace time → video time: `(t − startedElapsedRealtimeNanos/1e6)/1000 − 0.5`.
- Video: look at the rep itself (the labelling page, or a frame grid saved as PNG) before believing
  any reading — on 4 Oct a second person's wrist was misplaced at the bottom and every arm reading
  went wrong with it.
- Group ticked reps by root cause; one cause, one fix.

## 4. Fix, or say why not

- A rule change must not move any person's honest reps counted down or any cheat type up on the
  bench, and must keep `./gradlew -q ktlintCheck :repcounter:test` green. Check it on every person,
  not only the one the rep came from, and state the margin.
- If the landmarks cannot tell the case apart (as with hips resting on the floor), do not force a
  line: answer with why, and record it as a known weakness.
- Finished sittings with useful reps become fixtures: trace and labels copied under the same new
  name into `fixtures/rep-traces/`, `trace=` set to it, a README row added.

## 5. Verify every answer — mandatory

Draft one entry per ticked rep and run the saved workflow before writing anything back:

```
Workflow({ name: "verify-label-answers", args: [
  { sitting, t, video, label, appSaid, shouldSay, reviewerNote,
    answer,            // the reply as the reviewer will read it
    claims: [...],     // each factual statement it rests on
    evidence,          // commands run, numbers read, frames looked at
    change }           // commit/files/rule changed for it, or ""
]})
```

If the name is not found (the list of saved workflows is read when a session starts), run the same
script by path: `Workflow({ scriptPath: "W:/Holdout/.claude/workflows/verify-label-answers.js", args: [...] })`.

Two independent agents try to refute each answer — one re-derives the evidence, one checks the fix
(diff, tests, bench per person) and the wording. Only answers in `verified` are written back. For a
refuted one: fix what the problems name, revise the answer (the `correctedAnswers` help), and verify
it again. If it still cannot be verified, the answer says plainly what is unknown.

## 6. Answer in the page

```bash
node scripts/label-reps.mjs --reply <sitting> <t> "<answer>"
```

For each verified answer: what was wrong, what changed (commit) or why nothing did, and what the
reviewer will see now. The rep turns "answered" and is first in the reviewer's **Needs you** queue;
they close it with Done or tick it again. Re-run `todayVerdicts` after a fix so the page shows the
reps it put right. Commit and push the fix (conventional message from the diff, no Co-Authored-By
trailer), then tell the user in chat: how many answered, what changed, and anything refuted that
needs their decision.
