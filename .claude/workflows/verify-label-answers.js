export const meta = {
  name: 'verify-label-answers',
  description: "Independently verify Claude's answers to reps the reviewer ticked \"Review & fix\" before they are written back",
  whenToUse: 'Step 5 of the check-labels skill: after drafting an answer (and any fix) for every ticked rep, before any `label-reps.mjs --reply`.',
  phases: [
    { title: 'Verify', detail: 'two independent skeptics per answer: the evidence, and the fix and its wording' },
  ],
}

// args: [{ sitting, t, video, label, appSaid, shouldSay, reviewerNote, answer, claims: [..], evidence, change }]
//   sitting  phone_traces-relative, e.g. "batch12/20261004-071455-staked"
//   t        the trace time the rep ended (labels file `t`)
//   answer   the reply Claude intends to write for the reviewer
//   claims   each factual statement the answer rests on, one per entry
//   evidence how Claude reached it: commands run, numbers read, frames looked at
//   change   the code change made for it (commit, files, rule), or "" when none
const items = Array.isArray(args) ? args : []
if (!items.length) {
  log('No answers to verify: pass the drafted answers as args.')
  return { verified: [], refuted: [], summary: 'nothing to verify' }
}

const VERDICT = {
  type: 'object',
  properties: {
    refuted: { type: 'boolean', description: 'true if any claim is wrong, unsupported, or the answer overclaims' },
    problems: { type: 'array', items: { type: 'string' }, description: 'each wrong or unsupported point, with the evidence' },
    checked: { type: 'array', items: { type: 'string' }, description: 'what was re-checked and how (commands, files, numbers)' },
    correctedAnswer: { type: 'string', description: 'the answer as it should read, when refuted; empty otherwise' },
  },
  required: ['refuted', 'problems', 'checked', 'correctedAnswer'],
}

const context = (f) => `Repository: W:/Holdout (branch feat/core-codecs). The reviewer labelled a push-up rep from its video
and ticked it "Review & fix". Their label is the ground truth for what happened on video — do NOT re-label it.

Rep: ${f.sitting} at trace time t=${f.t} (video ${f.video}).
Reviewer's label: ${f.label}. The app said: ${f.appSaid}. Reviewer wants it refused with: ${f.shouldSay || '(no preference)'}.
Reviewer's note: ${f.reviewerNote || '(none)'}

Claude's drafted answer to the reviewer:
"""${f.answer}"""

Claims it rests on:
${(f.claims || []).map((c, i) => `${i + 1}. ${c}`).join('\n')}

How Claude reached it: ${f.evidence || '(not given)'}
Code change made for it: ${f.change || '(none)'}

Files: the trace is phone_traces/${f.sitting}.trace.csv (rows: t,step,view,... , last columns elbow,posture,knee,event),
labels phone_traces/${f.sitting}.labels.csv, today's verdicts phone_traces/${f.sitting}.today.csv.
Tools (run from W:/Holdout/kotlin, read-only use): ./gradlew -q :repcounter:replayTraces -Ptraces=<dir with only this trace copied> -Pdetail
(per-rep readings), ./gradlew -q :repcounter:testBench -Ptraces=../phone_traces (scores against all labels, per person),
./gradlew -q :repcounter:test. Do not edit any repository file; copy a trace into your own temp dir if you need a one-trace replay.`

const lenses = [
  {
    key: 'evidence',
    prompt: (f) => `${context(f)}

You are a skeptic checking the EVIDENCE. Re-derive every claim yourself from the trace, the replays and the code —
do not trust Claude's numbers. Recompute the readings for this rep (replayTraces -Pdetail on a copy of the trace),
find the rule in kotlin/repcounter/src/main/kotlin/holdout/repcounter that decided the verdict, and check each claim
against what you measured. Refute if any number is off beyond rounding, any claim is unsupported, or the cause named
is not the rule that actually fired. Default to refuted=true when you cannot confirm a claim.`,
  },
  {
    key: 'fix',
    prompt: (f) => `${context(f)}

You are a skeptic checking the FIX AND THE WORDING. If a code change is named: read the diff (git show / git diff),
run ./gradlew -q :repcounter:test, and run the test bench on ../phone_traces — refute if any test fails, if any
person's honest reps counted drops, or any cheat type counted rises, or if the change only fits this one person's
numbers with no margin stated. If no change is named: check the answer says honestly why not and what it would take.
Either way refute if the answer overclaims ("fixed", "always", "never") beyond what was shown, contradicts the
reviewer's label, or would mislead the reviewer. Default to refuted=true when unsure.`,
  },
]

phase('Verify')
const results = await pipeline(
  items,
  (f, _item, i) => parallel(lenses.map((lens) => () =>
    agent(lens.prompt(f), { label: `${lens.key}: ${f.sitting.split('/').pop()} ${f.video}`, phase: 'Verify', schema: VERDICT })
      .then((v) => (v ? { lens: lens.key, ...v } : null)))),
  (votes, f) => {
    const got = votes.filter(Boolean)
    // Both skeptics must have run and neither refuted: a missing vote is not a pass.
    const survives = got.length === lenses.length && got.every((v) => !v.refuted)
    return { ...f, survives, votes: got }
  },
)

const done = results.filter(Boolean)
const verified = done.filter((r) => r.survives)
const refuted = done.filter((r) => !r.survives)
log(`${verified.length} of ${items.length} answers survived; ${refuted.length} refuted or not fully checked`)
return {
  verified: verified.map((r) => ({ sitting: r.sitting, t: r.t, answer: r.answer, checked: r.votes.flatMap((v) => v.checked) })),
  refuted: refuted.map((r) => ({
    sitting: r.sitting,
    t: r.t,
    answer: r.answer,
    problems: r.votes.flatMap((v) => v.problems.map((p) => `[${v.lens}] ${p}`)),
    correctedAnswers: r.votes.map((v) => v.correctedAnswer).filter(Boolean),
    missingVotes: lenses.length - r.votes.length,
  })),
}
