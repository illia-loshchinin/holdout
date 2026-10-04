#!/usr/bin/env node
// Labels each rep of a recorded sitting from its video, for the test bench
// (`./gradlew :repcounter:testBench`).
//
//   node scripts/label-reps.mjs [dir]           default: phone_traces/
//   then open http://localhost:8766
//   node scripts/label-reps.mjs --flags [dir]   the reps the reviewer ticked for Claude
//
// Serves, on this machine only, the sittings `scripts/pull-traces.sh` pulled
// into [dir] (a trace, its video and the video's .video.json) and a page that
// plays each rep as a loop and records what it was. Labels go to
// `<sitting>.labels.csv` beside the trace: the trace time each rep ended, the
// label, who set it, whether the reviewer ticked "Review & fix", the words the
// reviewer would want the app to refuse it with, the reviewer's note and
// Claude's — never a picture. "Sitting finished" marks every rep labelled: the
// labels are final, and the sitting can become a fixture.
//
// `--flags` prints the labelled sittings, finished or not, and the reps ticked
// "Review & fix" — only those are Claude's to take up. A changed label or chosen
// words without the tick is the reviewer's record, not a request. A finished
// sitting becomes a fixture with its labels: copy both, under the same new
// name, into fixtures/rep-traces/.
//
// Videos are of people who agreed in writing to be filmed (constitution X);
// phone_traces/ is git-ignored, and so is everything this serves.
import { createServer } from 'node:http';
import { createReadStream, existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, extname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const flagsOnly = process.argv.includes('--flags');
const root = resolve(process.argv.slice(2).find((a) => !a.startsWith('--')) ?? join(here, '..', 'phone_traces'));
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
    if (!existsSync(`${base}.mp4`) || !existsSync(`${base}.video.json`)) return [];
    const labels = existsSync(`${base}.labels.csv`) ? readFileSync(`${base}.labels.csv`, 'utf8').split('\n').filter((l) => /^\d/.test(l)).length : 0;
    return [{ base: relative(root, base).split(sep).join('/'), labels }];
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
      reps.push({ t: Number(t), label, by: 'reviewer', flag: '', hint: '', note: note.join(','), claude: '' });
    } else {
      const f = csvSplit(l), get = (n) => f[cols.indexOf(n)] ?? '';
      reps.push({ t: Number(get('t')), label: get('label'), by: get('by') || 'reviewer', flag: get('flag'), hint: get('hint'), note: get('note'), claude: get('claude') });
    }
  }
  return { meta, reps };
}

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
    const disagree = reps.filter((r) => r.label !== 'unsure' && said.has(r.t) && (r.label === 'honest') !== (said.get(r.t) === 'counted')).length;
    sittingsRows.push(`| ${name} | ${meta.person ?? ''} | ${meta.reviewed || 'no'} | ${reps.length} | ${reps.filter((r) => r.by !== 'claude').length} | ${reps.filter((r) => r.flag).length} | ${disagree} |`);
    // Only what the reviewer ticked "Review & fix".
    for (const r of reps.filter((x) => x.flag)) {
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

if (flagsOnly) {
  flagsReport();
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
  // Ranges, or the browser cannot seek a video of a few hundred megabytes.
  const range = /bytes=(\d*)-(\d*)/.exec(req.headers.range ?? '');
  if (range) {
    const start = range[1] ? Number(range[1]) : size - Number(range[2]);
    const end = range[1] && range[2] ? Number(range[2]) : size - 1;
    res.writeHead(206, { 'Content-Type': type, 'Accept-Ranges': 'bytes', 'Content-Range': `bytes ${start}-${end}/${size}`, 'Content-Length': end - start + 1 });
    return createReadStream(p, { start, end }).pipe(res);
  }
  res.writeHead(200, { 'Content-Type': type, 'Accept-Ranges': 'bytes', 'Content-Length': size, 'Cache-Control': 'no-store' });
  createReadStream(p).pipe(res);
}).listen(port, '127.0.0.1', () => console.log(`label-reps: ${root}\n  open http://localhost:${port}`));
