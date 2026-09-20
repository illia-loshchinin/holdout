/**
 * The CI gate: runs the whole suite and judges it against a baseline of the
 * tests that are red *on purpose*.
 *
 * Constitution VII writes the suites before the logic, so a red test is the
 * normal state of work that has not been done yet. A gate that simply required
 * green would be red for weeks and be ignored; a gate that skipped the red
 * files would let a case that has already gone green break in silence — which
 * is exactly what happens between T027 and T058, as the scenarios turn green
 * one at a time.
 *
 * So the gate ratchets:
 *
 *   - a test that fails and is **not** in `known-red.json` is a regression —
 *     the build fails;
 *   - a test in `known-red.json` that now **passes** means the work landed —
 *     the build fails too, telling you to run `npm run ci:gate -- --update`,
 *     which takes it off the list. From then on it is gated like any other
 *     green test;
 *   - a test that fails and is on the list is expected, and only counted.
 *
 * The list is meant to shrink to nothing. When it is empty, delete it, delete
 * this script, and make the workflow run `npm test`.
 */

import { spawnSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync, rmSync, appendFileSync } from 'node:fs';
import { dirname, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const TESTS = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const BASELINE = resolve(TESTS, 'known-red.json');
const REPORT = resolve(TESTS, '.vitest-report.json');

const argv = process.argv.slice(2);
const update = argv.includes('--update');
const vitestArgs = argv.filter((a) => a !== '--update');

// --------------------------------------------------------------- run vitest

const run = spawnSync(
  'npx',
  // The same selection as `npm test` in package.json: the Surfpool smoke suite
  // needs a live validator and belongs to the quickstart, not to this gate.
  ['vitest', 'run', '--exclude', 'smoke/**', '--reporter=json', `--outputFile=${REPORT}`, ...vitestArgs],
  { cwd: TESTS, stdio: 'inherit', shell: process.platform === 'win32' },
);

if (!existsSync(REPORT)) {
  console.error('ci-gate: vitest produced no report — it failed to start, not to pass.');
  process.exit(run.status ?? 1);
}

const report = JSON.parse(readFileSync(REPORT, 'utf8'));
rmSync(REPORT, { force: true });

/** A test's identity across runs: the file it lives in, then its full name. */
const idOf = (file, assertion) => `${relative(TESTS, file).split(sep).join('/')} :: ${assertion.fullName}`;

const failing = new Set();
const passing = new Set();
for (const file of report.testResults) {
  for (const a of file.assertionResults) {
    if (a.status === 'failed') failing.add(idOf(file.name, a));
    else if (a.status === 'passed') passing.add(idOf(file.name, a));
  }
}

// ------------------------------------------------------------ the baseline

const readBaseline = () => {
  if (!existsSync(BASELINE)) return [];
  const raw = JSON.parse(readFileSync(BASELINE, 'utf8'));
  return Array.isArray(raw) ? raw : raw.tests;
};

if (update) {
  const tests = [...failing].sort();
  writeFileSync(
    BASELINE,
    `${JSON.stringify(
      {
        $comment:
          'Tests that are red on purpose: written before the logic they describe (constitution VII). ' +
          'Regenerate with `npm run ci:gate -- --update`. The list shrinks as tasks land; when it ' +
          'empties, delete it, delete scripts/ci-gate.mjs, and let the workflow run `npm test`.',
        tests,
      },
      null,
      2,
    )}\n`,
  );
  console.log(`ci-gate: baseline rewritten — ${tests.length} test(s) red on purpose.`);
  process.exit(0);
}

const baseline = new Set(readBaseline());

const regressions = [...failing].filter((t) => !baseline.has(t)).sort();
const landed = [...baseline].filter((t) => passing.has(t)).sort();
const vanished = [...baseline].filter((t) => !passing.has(t) && !failing.has(t)).sort();
const stillRed = [...failing].filter((t) => baseline.has(t)).length;

// ------------------------------------------------------------------ verdict

const lines = [];
const say = (s) => {
  console.log(s);
  lines.push(s);
};

say(`### Contract tests`);
say('');
say(`- **${passing.size} passing**, gated: any one of them breaking fails the build.`);
say(`- ${stillRed} red on purpose (constitution VII), listed in \`tests/known-red.json\`.`);

if (regressions.length > 0) {
  say('');
  say(`**${regressions.length} regression(s)** — failing, and not on the list:`);
  for (const t of regressions) say(`  - \`${t}\``);
}

if (landed.length > 0) {
  say('');
  say(`**${landed.length} test(s) on the list now pass.** Run \`npm --prefix tests run ci:gate -- --update\``);
  say('and commit `tests/known-red.json`, so they are gated from here on.');
  for (const t of landed) say(`  - \`${t}\``);
}

if (vanished.length > 0) {
  say('');
  say(`**${vanished.length} listed test(s) no longer exist** — renamed or removed. Same fix: \`--update\`.`);
  for (const t of vanished) say(`  - \`${t}\``);
}

if (process.env.GITHUB_STEP_SUMMARY) {
  appendFileSync(process.env.GITHUB_STEP_SUMMARY, `${lines.join('\n')}\n`);
}

process.exit(regressions.length + landed.length + vanished.length > 0 ? 1 : 0);
