#!/usr/bin/env node
// Labels each rep of a recorded sitting from its video, for the test bench
// (`./gradlew :repcounter:testBench`).
//
//   node scripts/label-reps.mjs [dir]     default: phone_traces/
//   then open http://localhost:8766
//
// Serves, on this machine only, the sittings `scripts/pull-traces.sh` pulled
// into [dir] (a trace, its video and the video's .video.json) and a page that
// plays each rep as a loop and records what it was. Labels go to
// `<sitting>.labels.csv` beside the trace: the trace time each rep ended, a
// label and a note — never a picture. A useful sitting becomes a fixture with
// its labels: copy both, under the same new name, into fixtures/rep-traces/.
//
// Videos are of people who agreed in writing to be filmed (constitution X);
// phone_traces/ is git-ignored, and so is everything this serves.
import { createServer } from 'node:http';
import { createReadStream, existsSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, extname, join, relative, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const root = resolve(process.argv[2] ?? join(here, '..', 'phone_traces'));
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
