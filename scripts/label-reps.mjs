#!/usr/bin/env node
// Labels each rep of a recorded sitting from its video, for the test bench
// (`./gradlew :repcounter:testBench`).
//
//   node scripts/label-reps.mjs [dir]           default: phone_traces/
//   then open http://localhost:8766
//   node scripts/label-reps.mjs --flags [dir]   the reps the reviewer ticked for Claude
//   node scripts/label-reps.mjs --reply <sitting> <t> <answer…> [--dir dir]
//                                                Claude's answer to a ticked rep
//   node scripts/label-reps.mjs --promote [dir]  finished sittings into fixtures/rep-traces/
//   node scripts/label-reps.mjs --prune [dir]    delete videos 30 days after finishing (asks first)
//
// Serves, on this machine only, the sittings `scripts/pull-traces.sh` pulled
// into [dir] (a trace, its video and the video's .video.json) and a page that
// plays each rep as a loop and records what it was. Labels go to
// `<sitting>.labels.csv` beside the trace: the trace time each rep ended, the
// label, who set it, whether the reviewer ticked "Review & fix", the words the
// reviewer would want the app to refuse it with, the reviewer's note and
// Claude's, and Claude's answer to a ticked rep — never a picture. "Sitting
// finished" marks every rep labelled: the labels are final, and the sitting can
// become a fixture. `<sitting>.today.csv`, when `./gradlew
// :repcounter:todayVerdicts` has written it, is today's counter's verdict on
// each rep, shown beside the one the app gave when the sitting was recorded.
//
// The page opens on "Needs you": Claude's answers to read, Claude's labels to
// confirm (those the app disagrees with first), then reps nobody has labelled.
//
// `--flags` prints the labelled sittings, finished or not, and the reps ticked
// "Review & fix" — only those are Claude's to take up. A changed label or chosen
// words without the tick is the reviewer's record, not a request. A finished
// sitting becomes a fixture with its labels: copy both, under the same new
// name, into fixtures/rep-traces/.
//
// Promote and prune: a finished sitting's trace (gzip) and labels are copied
// into fixtures/rep-traces/, where the regression ratchet (RepRatchetTest)
// holds every labelled rep; the repository gets numbers and text, never
// video. A video is deleted 30 days after its sitting was finished, and only
// once the sitting has been promoted — listed first, deleted on "yes". Without
// its video a sitting still opens: the skeleton plays alone.
//
// Videos are of people who agreed in writing to be filmed (constitution X);
// phone_traces/ is git-ignored, and so is everything this serves.
import { createServer } from 'node:http';
import { createReadStream, existsSync, readdirSync, readFileSync, statSync, unlinkSync, writeFileSync } from 'node:fs';
import { createInterface } from 'node:readline/promises';
import { gunzipSync, gzipSync } from 'node:zlib';
import { dirname, extname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const argv = process.argv.slice(2);
const flagsOnly = argv.includes('--flags');
const promoteOnly = argv.includes('--promote');
const pruneOnly = argv.includes('--prune');
const replyAt = argv.indexOf('--reply');
const dirAt = argv.indexOf('--dir');
const root = resolve(
  dirAt >= 0 ? argv[dirAt + 1]
    : replyAt >= 0 ? join(here, '..', 'phone_traces')
      : argv.find((a) => !a.startsWith('--')) ?? join(here, '..', 'phone_traces'),
);
const port = Number(process.env.LABEL_PORT ?? 8766);
const page = join(here, 'label-reps.html');
const types = { '.html': 'text/html; charset=utf-8', '.mp4': 'video/mp4', '.json': 'application/json', '.csv': 'text/plain; charset=utf-8' };

if (!existsSync(root)) {
  console.error(`label-reps: no ${root}; pull a sitting first (scripts/pull-traces.sh)`);
  process.exit(2);
}

/** Every trace under the root that has its video, as a path without `.trace.csv`, with forward slashes. */
function sittings(dir = root) {
  return readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = join(dir, e.name);
    if (e.isDirectory()) return sittings(p);
    if (!e.name.endsWith('.trace.csv')) return [];
    const base = p.slice(0, -'.trace.csv'.length);
    // A sitting opens with its video — or, once the video is deleted, with its labels: the skeleton plays alone.
    const video = existsSync(`${base}.mp4`);
    if (!existsSync(`${base}.video.json`) || (!video && !existsSync(`${base}.labels.csv`))) return [];
    const labels = existsSync(`${base}.labels.csv`) ? readFileSync(`${base}.labels.csv`, 'utf8').split('\n').filter((l) => /^\d/.test(l)).length : 0;
    return [{ base: relative(root, base).split(sep).join('/'), labels, video }];
  }).sort((a, b) => b.base.split('/').pop().localeCompare(a.base.split('/').pop())); // newest recording first
}

/** One CSV line: a quoted field may hold commas and doubled quotes. */
function csvSplit(line) {
  const out = [];
  let cur = '', quoted = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (quoted) {
      if (c === '"' && line[i + 1] === '"') { cur += '"'; i++; } else if (c === '"') quoted = false; else cur += c;
    } else if (c === '"') quoted = true;
    else if (c === ',') { out.push(cur); cur = ''; } else cur += c;
  }
  out.push(cur);
  return out;
}

/** A labels file, v2 (`t,label,by,flag,note,claude`) or v1 (`t,label,note`, the reviewer's). */
function readLabels(path) {
  const meta = {}, reps = [];
  let cols = null;
  for (const l of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const m = /^#\s*(\w+)=(.*)$/.exec(l);
    if (m) { meta[m[1]] = m[2].trim(); continue; }
    if (l.startsWith('t,')) { cols = csvSplit(l); continue; }
    if (!/^\d/.test(l)) continue;
    if (!cols || cols.length === 3) {
      const [t, label, ...note] = l.split(',');
      reps.push({ t: Number(t), label, by: 'reviewer', flag: '', hint: '', note: note.join(','), claude: '', reply: '' });
    } else {
      const f = csvSplit(l), get = (n) => f[cols.indexOf(n)] ?? '';
      reps.push({ t: Number(get('t')), label: get('label'), by: get('by') || 'reviewer', flag: get('flag'), hint: get('hint'), note: get('note'), claude: get('claude'), reply: get('reply') });
    }
  }
  return { meta, reps };
}

const COLUMNS = ['t', 'label', 'by', 'flag', 'hint', 'note', 'claude', 'reply'];
const csvField = (v) => (/[",]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v);
const oneLine = (v) => String(v ?? '').replace(/[\r\n]+/g, ' ').trim();

/** The same file the page writes: header lines kept, then one row per rep. */
function writeLabels(path, meta, reps) {
  const lines = ['# holdout-rep-labels v2', ...Object.entries(meta).filter(([k, v]) => k !== 'holdout' && v !== '').map(([k, v]) => `# ${k}=${v}`), COLUMNS.join(',')];
  for (const r of reps) lines.push(COLUMNS.map((c) => csvField(oneLine(r[c]))).join(','));
  writeFileSync(path, lines.join('\n') + '\n');
}

/**
 * Today's counter's verdict per trace time, from `<sitting>.today.csv`; null
 * when not written. An empty map is a replay that judged nothing — every rep
 * then reads "no rep seen", not the verdict recorded on the phone.
 */
function today(base) {
  if (!existsSync(`${base}.today.csv`)) return null;
  const byT = new Map();
  for (const l of readFileSync(`${base}.today.csv`, 'utf8').split(/\r?\n/)) {
    // A sitting today's app would not record: no verdicts, and why (TodayVerdicts).
    const skip = /^#\s*not counted:\s*(.*)$/.exec(l);
    if (skip) byT.notCounted = skip[1].trim();
    if (!/^\d/.test(l)) continue;
    const [t, v] = l.split(',');
    byT.set(Number(t), v);
  }
  return byT;
}

/**
 * Which replay count judges each recorded rep — one count per rep, as the test
 * bench pairs them: each count, in order, takes the nearest recorded rep within
 * 400 ms not yet taken; a rep still without one takes the one count left inside
 * its stretch (after the rep before it, on no other rep), when there is exactly
 * one. Recorded reps can end 200 ms apart, so a count is never given to two.
 * Map: recorded rep's end → the replay count's time.
 */
function pairToday(today, ends) {
  const pairs = new Map();
  if (!today) return pairs;
  const times = [...today.keys()].sort((a, b) => a - b);
  const taken = new Set();
  for (const k of times) {
    const e = ends.filter((x) => !pairs.has(x) && Math.abs(x - k) <= 400).sort((a, b) => Math.abs(a - k) - Math.abs(b - k))[0];
    if (e === undefined) continue;
    pairs.set(e, k);
    taken.add(k);
  }
  ends.forEach((e, i) => {
    if (pairs.has(e)) return;
    const from = i > 0 ? ends[i - 1] : -Infinity;
    const inside = times.filter((k) => !taken.has(k) && k > from && k <= e + 400 && !ends.some((o) => o !== e && Math.abs(o - k) <= 400));
    if (inside.length === 1) {
      pairs.set(e, inside[0]);
      taken.add(inside[0]);
    }
  });
  return pairs;
}

/**
 * Trace times where today's counter judges a rep — counts or refuses it — and the
 * recording ended none: no recorded rep is paired with it ([pairToday]). The
 * recorded counter missed a movement there, or today's sees one too many —
 * either way nobody could label it until it is listed (review, PR #14). The
 * page lists the same reps.
 */
function onlyToday(byT, ends) {
  if (!byT) return [];
  // Counted or refused: a real rep the recording missed is a candidate either way (review, PR #14).
  const paired = new Set(pairToday(byT, ends).values());
  return [...byT.keys()].filter((k) => !paired.has(k));
}

/**
 * What needs the reviewer, across every sitting with a video, in order:
 * Claude's answers to read; Claude's labels to confirm, those the app gets
 * wrong today first; then reps nobody has labelled, newest sitting first.
 */
function queue() {
  const items = [];
  for (const { base } of sittings()) {
    const abs = join(root, base);
    const recorded = verdicts(`${abs}.trace.csv`);
    const now = today(abs);
    const stakes = stakesOf(`${abs}.trace.csv`);
    const labels = existsSync(`${abs}.labels.csv`) ? readLabels(`${abs}.labels.csv`).reps : [];
    const byT = new Map(labels.map((r) => [r.t, r]));
    const ends = [...recorded.keys()];
    // Replay counts pair with recorded reps and with reps already labelled where the
    // recording had none: a count moved a little by a later counter is the same rep.
    const anchors = [...new Set([...ends, ...byT.keys()])].sort((a, b) => a - b);
    const pairing = pairToday(now, anchors);
    // Every rep with a place in the sitting: each the recording ended, and each labelled
    // where it ended none — a rep only a replay judged, once labelled, is still a rep
    // to confirm or to read an answer on (review, PR #14).
    const labelledOnly = [...byT.keys()].filter((t) => !recorded.has(t));
    for (const t of [...ends, ...labelledOnly]) {
      const l = byT.get(t);
      // Once the replay has run, a rep it judged nothing near — nor one rep alone
      // inside its stretch (as the page and the bench pair them) — is one today's
      // counter does not see.
      const was = recorded.get(t) ?? 'not-recorded';
      const verdict = now ? (pairing.has(t) ? now.get(pairing.get(t)) : 'not-judged') : was;
      const wrong = l && l.label !== 'unsure' && shouldCount(l.label, stakes) !== (verdict === 'counted');
      const rank = !l ? 3 : l.flag === 'answered' ? 0 : l.by === 'claude' ? (wrong ? 1 : 2) : null;
      if (rank === null) continue;
      const why = ['Claude answered', "Claude's label: the app disagrees", "Claude's label", 'not labelled'][rank];
      items.push({ base, t, rank, why, label: l?.label ?? '', verdict });
    }
    for (const t of onlyToday(now, anchors)) {
      const l = byT.get(t);
      const rank = !l ? 3 : l.flag === 'answered' ? 0 : l.by === 'claude' ? 2 : null;
      if (rank === null) continue;
      const verdict = now.get(t);
      const why = ['Claude answered', '', "Claude's label", `not recorded: only today ${verdict === 'counted' ? 'counts' : 'refuses'} it`][rank];
      items.push({ base, t, rank, why, label: l?.label ?? '', verdict });
    }
  }
  const order = sittings().map((x) => x.base);
  return items.sort((a, b) => a.rank - b.rank || order.indexOf(a.base) - order.indexOf(b.base) || a.t - b.t);
}

/** `--reply <sitting> <t> <answer…>`: Claude's answer to a ticked rep; the tick becomes "answered". */
function reply() {
  const [base, t, ...words] = argv.slice(replyAt + 1).filter((a, i, all) => a !== '--dir' && all[i - 1] !== '--dir');
  const path = join(root, `${base}.labels.csv`);
  if (!base || !t || !words.length || !existsSync(path)) {
    console.error('label-reps: --reply <sitting, e.g. batch12/20261004-071455-staked> <trace time> <answer>');
    process.exit(2);
  }
  const { meta, reps } = readLabels(path);
  const rep = reps.find((r) => r.t === Number(t));
  if (!rep) {
    console.error(`label-reps: no labelled rep at t=${t} in ${base}`);
    process.exit(2);
  }
  rep.reply = words.join(' ');
  rep.flag = 'answered';
  writeLabels(path, meta, reps);
  console.log(`answered ${base} t=${t}`);
}

/** STAKED, EASY or DAILY, from the trace's header. */
function stakesOf(tracePath) {
  return /stakes=(\w+)/.exec(readFileSync(tracePath, 'utf8').slice(0, 400))?.[1] ?? 'STAKED';
}

/** What should count: honest — and where knees are not judged (the Daily 25, an Easy Holdout), a push-up from the knees. */
const shouldCount = (label, stakes) => label === 'honest' || (label === 'knees' && (stakes === 'DAILY' || stakes === 'EASY'));

/** The verdict the app wrote at each trace time, and when counting began. */
function verdicts(tracePath) {
  const rows = readFileSync(tracePath, 'utf8').split(/\r?\n/).filter((l) => /^\d/.test(l)).map((l) => l.split(','));
  const byT = new Map();
  for (const r of rows) if (r[1] === 'COUNTING' && r[r.length - 1]) byT.set(Number(r[0]), r[r.length - 1].replace('discarded:', ''));
  return byT;
}

/** `--flags`: what the reviewer left for Claude, as Markdown. */
function flagsReport() {
  const all = [];
  const walk = (dir) => readdirSync(dir, { withFileTypes: true }).forEach((e) => {
    const p = join(dir, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith('.labels.csv') && existsSync(p.replace(/\.labels\.csv$/, '.trace.csv'))) all.push(p);
  });
  walk(root);
  const mmss = (s) => `${Math.floor(s / 60)}:${String(Math.floor(s % 60)).padStart(2, '0')}`;
  const sittingsRows = [], asked = [];
  for (const path of all.sort()) {
    const base = path.slice(0, -'.labels.csv'.length);
    const name = relative(root, base).split(sep).join('/');
    const { meta, reps } = readLabels(path);
    const said = verdicts(`${base}.trace.csv`);
    const started = existsSync(`${base}.video.json`) ? JSON.parse(readFileSync(`${base}.video.json`, 'utf8')).startedElapsedRealtimeNanos / 1e6 : null;
    const offset = Number(meta.offset ?? 0.5);
    const when = (t) => (started === null ? `t=${t}` : mmss((t - started) / 1000 - offset));
    const stakes = stakesOf(`${base}.trace.csv`);
    const disagree = reps.filter((r) => r.label !== 'unsure' && said.has(r.t) && shouldCount(r.label, stakes) !== (said.get(r.t) === 'counted')).length;
    sittingsRows.push(`| ${name} | ${meta.person ?? ''} | ${meta.reviewed || 'no'} | ${reps.length} | ${reps.filter((r) => r.by !== 'claude').length} | ${reps.filter((r) => r.flag).length} | ${disagree} |`);
    // Only what the reviewer ticked "Review & fix" and Claude has not answered yet.
    for (const r of reps.filter((x) => x.flag === 'check')) {
      asked.push(`| ${name} | ${when(r.t)} | ${r.label} | ${said.get(r.t) ?? '?'} | ${r.hint || ''} | ${r.note} | ${r.claude} |`);
    }
  }
  console.log(`# Labels under ${root}\n`);
  console.log('| sitting | person | finished | labelled | by the reviewer | ticked Review & fix | app disagrees |');
  console.log('|---|---|---|---|---|---|---|');
  sittingsRows.forEach((r) => console.log(r));
  console.log(`\n## Ticked "Review & fix" (${asked.length})\n`);
  if (asked.length) {
    console.log('| sitting | video | label | the app said | should say | reviewer\'s note | Claude\'s note |');
    console.log('|---|---|---|---|---|---|---|');
    asked.forEach((r) => console.log(r));
  }
}

const fixtures = join(here, '..', 'fixtures', 'rep-traces');
const DAY = 24 * 3600 * 1000;
const RETAIN_DAYS = 30;

/**
 * Every labels file under the root that is ticked "Sitting finished" **and**
 * whose every label a reviewer has confirmed, with its sitting's base path. A
 * label Claude set is not ground truth until confirmed: promoted, it would be
 * held by the ratchet; pruned, its video would go before anyone checked it
 * (review, PR #14). Those finished but not confirmed are in [unconfirmed].
 */
const unconfirmed = [];
function finished() {
  unconfirmed.length = 0;
  const out = [];
  const walk = (dir) => readdirSync(dir, { withFileTypes: true }).forEach((e) => {
    const p = join(dir, e.name);
    if (e.isDirectory()) return walk(p);
    if (!e.name.endsWith('.labels.csv')) return;
    const base = p.slice(0, -'.labels.csv'.length);
    if (!existsSync(`${base}.trace.csv`)) return;
    const { meta, reps } = readLabels(p);
    if (!meta.reviewed) return;
    const name = relative(root, base).split(sep).join('/');
    const byClaude = reps.filter((r) => r.by === 'claude').length;
    // "Sitting finished" is ticked by hand: every rep a candidate — recorded, or counted
    // only today — must have a label too, or a half-labelled sitting becomes ground truth.
    const labelled = new Set(reps.map((r) => r.t));
    const ends = [...verdicts(`${base}.trace.csv`).keys()];
    const anchors = [...new Set([...ends, ...labelled])].sort((a, b) => a - b);
    // No replay is not "nothing only today judges": the reps the recording missed were
    // never looked for, so the sitting is not finished until one is run (review, PR #14).
    const now = today(base);
    const unlabelled = [...ends, ...onlyToday(now, anchors)].filter((t) => !labelled.has(t)).length;
    if (!now) unconfirmed.push(`${name}: today's replay not run — cd kotlin && ./gradlew :repcounter:todayVerdicts`);
    if (byClaude) unconfirmed.push(`${name}: ${byClaude} label(s) set by Claude, not confirmed`);
    if (unlabelled) unconfirmed.push(`${name}: ${unlabelled} rep(s) not labelled`);
    // Not counted by today's app: no replay looked for the reps the recording missed, so its
    // labels are not known to be whole — never promoted, its video never pruned (review, PR #14).
    if (now?.notCounted) unconfirmed.push(`${name}: today's app does not count it (${now.notCounted})`);
    if (now && !now.notCounted && !byClaude && !unlabelled) out.push({ base, name: base.split(sep).pop(), meta });
  });
  walk(root);
  return out;
}

/**
 * The fixture a sitting was promoted to: the fixtures labels file whose `source=`
 * names it, holding the same recording. The name alone is not enough — two
 * sittings in different folders can share a timestamp, and the second would take
 * over the first's fixture (review, PR #14).
 */
function promotedAs(s) {
  const want = fingerprint(`${s.base}.trace.csv`);
  for (const f of readdirSync(fixtures)) {
    if (!f.endsWith('.labels.csv')) continue;
    if (readLabels(join(fixtures, f)).meta.source !== s.name) continue;
    const fixture = f.slice(0, -'.labels.csv'.length), trace = fixtureTrace(fixture);
    if (trace && fingerprint(trace) === want) return fixture;
  }
  return null;
}

/** A fixture's trace file, gzipped or not; null when it has none. */
function fixtureTrace(fixture) {
  return [`${fixture}.trace.csv.gz`, `${fixture}.trace.csv`].map((f) => join(fixtures, f)).find((f) => existsSync(f)) ?? null;
}

/** A fixture name for a new recording: the sitting's own, numbered when another recording already has it. */
function freeName(name) {
  let fixture = name;
  for (let n = 2; fixtureTrace(fixture) || existsSync(join(fixtures, `${fixture}.labels.csv`)); n++) fixture = `${name}-${n}`;
  return fixture;
}

/**
 * A recording's fingerprint: its frame count and its first and last frame
 * times — the same recording under any name has the same (the first time alone
 * does not do: early traces start at 0).
 */
function fingerprint(path) {
  const text = path.endsWith('.gz') ? gunzipSync(readFileSync(path)).toString('utf8') : readFileSync(path, 'utf8');
  const times = text.split(/\r?\n/).filter((l) => /^\d/.test(l)).map((l) => l.slice(0, l.indexOf(',')));
  return `${times.length}:${times[0]}:${times[times.length - 1]}`;
}

/** An existing fixture holding the same recording: the labels go to it rather than to a second copy. */
function sameRecording(base) {
  const want = fingerprint(`${base}.trace.csv`);
  for (const f of readdirSync(fixtures)) {
    const m = /^(.*)\.trace\.csv(\.gz)?$/.exec(f);
    if (m && fingerprint(join(fixtures, f)) === want) return m[1];
  }
  return null;
}

/**
 * `--promote`: every finished sitting into fixtures/rep-traces/ — the trace
 * gzipped, the labels with `trace=` set to the fixture and `source=` to the
 * sitting. One already promoted gets its labels again (the reviewer's copy in
 * phone_traces/ is the working one). Then `./gradlew :repcounter:updateBaseline`.
 */
function promote() {
  let added = 0, refreshed = 0;
  for (const s of finished()) {
    const fixture = promotedAs(s) ?? sameRecording(s.base) ?? freeName(s.name);
    const traceGz = join(fixtures, `${fixture}.trace.csv.gz`), traceCsv = join(fixtures, `${fixture}.trace.csv`);
    const isNew = !existsSync(traceGz) && !existsSync(traceCsv);
    if (isNew) writeFileSync(traceGz, gzipSync(readFileSync(`${s.base}.trace.csv`), { level: 9 }));
    const { meta, reps } = readLabels(`${s.base}.labels.csv`);
    const target = join(fixtures, `${fixture}.labels.csv`);
    const before = existsSync(target) ? readFileSync(target, 'utf8') : '';
    writeLabels(target, { ...meta, trace: `${fixture}.trace.csv${existsSync(traceCsv) ? '' : '.gz'}`, source: s.name }, reps);
    if (isNew) added++;
    else if (readFileSync(target, 'utf8') !== before) refreshed++;
    console.log(`${isNew ? 'promoted ' : 'labels   '} ${relative(root, s.base).split(sep).join('/')} -> fixtures/rep-traces/${fixture}`);
  }
  if (unconfirmed.length) console.log(`\nNot promoted — ticked finished, but not every rep has a label a reviewer confirmed:\n  ${unconfirmed.join('\n  ')}`);
  console.log(`\n${added} promoted, ${refreshed} with new labels. Next: cd kotlin && ./gradlew :repcounter:updateBaseline, then commit fixtures/rep-traces/.`);
}

/**
 * Whether the fixture this sitting was promoted to holds its labels as they are
 * now, row for row. A label changed after promoting keeps the date the sitting
 * was finished; pruned on that date, its video would go with the correction
 * still unpromoted and nothing left to check it against (review, PR #14).
 */
function promotedCurrent(s) {
  const fixture = promotedAs(s);
  if (!fixture) return false;
  const rows = (path) => JSON.stringify(readLabels(path).reps.map((r) => COLUMNS.map((c) => oneLine(r[c]))));
  return rows(join(fixtures, `${fixture}.labels.csv`)) === rows(`${s.base}.labels.csv`);
}

/** Videos to delete: the sitting finished more than RETAIN_DAYS ago, and promoted with the labels it has now. */
function dueVideos() {
  const now = Date.now();
  return finished()
    .filter((s) => existsSync(`${s.base}.mp4`) && promotedCurrent(s) && now - Date.parse(s.meta.reviewed) >= RETAIN_DAYS * DAY)
    .map((s) => ({ base: relative(root, s.base).split(sep).join('/'), finished: s.meta.reviewed, mb: Math.round(statSync(`${s.base}.mp4`).size / 1e6) }));
}

function deleteVideos(list) {
  for (const v of list) unlinkSync(join(root, `${v.base}.mp4`));
}

if (flagsOnly) {
  flagsReport();
  process.exit(0);
}
if (promoteOnly) {
  promote();
  process.exit(0);
}
if (pruneOnly) {
  const due = dueVideos();
  if (!due.length) {
    console.log(`No video to delete: none of a sitting finished ${RETAIN_DAYS} days ago and promoted.`);
    process.exit(0);
  }
  due.forEach((v) => console.log(`  ${v.base}  finished ${v.finished}  ${v.mb} MB`));
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  const yes = /^y(es)?$/i.test((await rl.question(`Delete these ${due.length} videos? Their traces and labels stay in fixtures/rep-traces/. [y/N] `)).trim());
  rl.close();
  if (yes) deleteVideos(due);
  console.log(yes ? `Deleted ${due.length}.` : 'Nothing deleted.');
  process.exit(0);
}
if (replyAt >= 0) {
  reply();
  process.exit(0);
}

/** A path under the root, or null: nothing outside it is served or written. */
function inside(urlPath) {
  const p = resolve(root, decodeURIComponent(urlPath));
  return p === root || p.startsWith(root + sep) ? p : null;
}

createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': types['.html'] });
    return createReadStream(page).pipe(res);
  }
  if (url.pathname === '/api/prune') {
    // GET lists the videos due for deletion; POST deletes exactly those, after the page asked.
    // Only from this page: any site can post a form to 127.0.0.1, but not with a custom
    // header, and a browser names the page a request came from (review, PR #14).
    const own = [`http://localhost:${port}`, `http://127.0.0.1:${port}`];
    if (req.method === 'POST' && (req.headers['x-label-reps'] !== 'prune' || (req.headers.origin && !own.includes(req.headers.origin)))) {
      res.writeHead(403);
      return res.end();
    }
    const due = dueVideos();
    if (req.method === 'POST') deleteVideos(due);
    res.writeHead(200, { 'Content-Type': types['.json'] });
    return res.end(JSON.stringify(req.method === 'POST' ? [] : due));
  }
  if (url.pathname === '/api/queue') {
    res.writeHead(200, { 'Content-Type': types['.json'] });
    return res.end(JSON.stringify(queue()));
  }
  if (url.pathname === '/api/sittings') {
    res.writeHead(200, { 'Content-Type': types['.json'] });
    return res.end(JSON.stringify(sittings()));
  }
  if (!url.pathname.startsWith('/files/')) {
    res.writeHead(404);
    return res.end();
  }
  const p = inside(url.pathname.slice('/files/'.length));
  if (!p) {
    res.writeHead(403);
    return res.end();
  }
  if (req.method === 'PUT') {
    // Only a labels file, and only beside its trace.
    if (!p.endsWith('.labels.csv') || !existsSync(p.replace(/\.labels\.csv$/, '.trace.csv'))) {
      res.writeHead(403);
      return res.end();
    }
    let body = '';
    req.setEncoding('utf8');
    req.on('data', (c) => { body += c; });
    req.on('end', () => {
      writeFileSync(p, body);
      res.writeHead(204);
      res.end();
    });
    return;
  }
  if (!existsSync(p) || statSync(p).isDirectory()) {
    res.writeHead(404);
    return res.end();
  }
  const size = statSync(p).size;
  const type = types[extname(p)] ?? 'application/octet-stream';
  // A stream the browser hangs up on is closed with it: every rep's loop is a
  // seek, and each seek abandons the request before it.
  const send = (stream) => {
    res.on('close', () => stream.destroy());
    stream.on('error', () => res.destroy());
    stream.pipe(res);
  };
  // Ranges, or the browser cannot seek a video of a few hundred megabytes. An
  // open-ended one ("from here on") is answered 4 MB at a time, not to the end.
  const range = /bytes=(\d*)-(\d*)/.exec(req.headers.range ?? '');
  if (range) {
    const start = range[1] ? Number(range[1]) : size - Number(range[2]);
    const end = range[1] && range[2] ? Number(range[2]) : Math.min(size - 1, start + 4 * 1024 * 1024 - 1);
    res.writeHead(206, { 'Content-Type': type, 'Accept-Ranges': 'bytes', 'Content-Range': `bytes ${start}-${end}/${size}`, 'Content-Length': end - start + 1 });
    return send(createReadStream(p, { start, end }));
  }
  res.writeHead(200, { 'Content-Type': type, 'Accept-Ranges': 'bytes', 'Content-Length': size, 'Cache-Control': 'no-store' });
  send(createReadStream(p));
}).listen(port, '127.0.0.1', () => {
  console.log(`label-reps: ${root}\n  open http://localhost:${port}`);
  const due = dueVideos();
  if (due.length) console.log(`\n${due.length} videos are due for deletion (finished ${RETAIN_DAYS}+ days ago, promoted): the page asks, or run --prune.`);
});
