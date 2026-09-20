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
 * `--update` only ever removes entries: it refuses to run while a failure is
 * missing from the list, so it can never adopt a regression as intentional.
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
/**
 * Files that failed without any test failing in them: an import that does not
 * resolve, a syntax error, a throw at the top of the file. Vitest reports those
 * with no assertions at all, so a file full of green tests can vanish from the
 * report entirely — and a gate that only reads assertions would call that a
 * clean run (it is the one way this script could wave a broken suite through).
 */
const broken = [];
for (const file of report.testResults) {
  let failedHere = 0;
  for (const a of file.assertionResults) {
    if (a.status === 'failed') {
      failing.add(idOf(file.name, a));
      failedHere += 1;
    } else if (a.status === 'passed') passing.add(idOf(file.name, a));
  }
  if (file.status === 'failed' && failedHere === 0) {
    broken.push({
      file: relative(TESTS, file.name).split(sep).join('/'),
      why: (file.message || 'no message').split('\n')[0].trim(),
    });
  }
}

/**
 * Vitest exits non-zero whenever anything is red, which is the normal state
 * here, so the exit code alone says nothing. It does say something when nothing
 * red is visible: the run then failed for a reason the report does not show.
 */
const unexplained = run.status !== 0 && failing.size === 0 && broken.length === 0;

// ------------------------------------------------------------ the baseline

const readBaseline = () => {
  if (!existsSync(BASELINE)) return [];
  const raw = JSON.parse(readFileSync(BASELINE, 'utf8'));
  return Array.isArray(raw) ? raw : raw.tests;
};

if (update) {
  if (broken.length > 0 || unexplained) {
    console.error('ci-gate: the suite did not run cleanly — refusing to write a baseline from it.');
    for (const b of broken) console.error(`  ${b.file}: ${b.why}`);
    if (unexplained) console.error(`  vitest exited ${run.status} with nothing red to explain it.`);
    process.exit(1);
  }
  // The list may only shrink. Rewriting it from whatever is failing right now
  // would quietly adopt a regression as intentional the first time someone runs
  // `--update` on a tree that has both a test that landed and a test that broke.
  const seeding = !existsSync(BASELINE);
  const listed = new Set(readBaseline());
  const unlisted = [...failing].filter((t) => !listed.has(t)).sort();
  if (!seeding && unlisted.length > 0) {
    console.error(`ci-gate: ${unlisted.length} failing test(s) are not on the list, so this is not`);
    console.error('a run to update from — fix them first. `--update` only removes entries:');
    for (const t of unlisted) console.error(`  ${t}`);
    console.error('');
    console.error('A test that was renamed keeps its old entry; take that one out by hand.');
    process.exit(1);
  }
  const tests = (seeding ? [...failing] : [...listed].filter((t) => failing.has(t))).sort();
  const dropped = seeding ? 0 : listed.size - tests.length;
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
  console.log(
    seeding
      ? `ci-gate: baseline seeded — ${tests.length} test(s) red on purpose.`
      : `ci-gate: ${dropped} test(s) taken off the list, ${tests.length} still red on purpose.`,
  );
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

if (broken.length > 0) {
  say('');
  say(`**${broken.length} file(s) failed before their tests ran** — every test in them is missing`);
  say('from this report, so nothing here vouches for them:');
  for (const b of broken) say(`  - \`${b.file}\` — ${b.why}`);
}

if (unexplained) {
  say('');
  say(`**vitest exited ${run.status} with nothing red to explain it** — treat the run as failed.`);
}

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

const problems = regressions.length + landed.length + vanished.length + broken.length;
process.exit(problems > 0 || unexplained ? 1 : 0);
