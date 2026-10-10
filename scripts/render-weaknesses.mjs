#!/usr/bin/env node
// Renders the README's "Known weaknesses" table from docs/weaknesses.json, its
// one source (constitution XI). The app shows no weaknesses screen: they are
// documented in the README only (spec 004 FR-015).
//
//   node scripts/render-weaknesses.mjs           rewrite the table in README.md
//   node scripts/render-weaknesses.mjs --check   exit 1 if README.md is out of date
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const readmePath = join(root, 'README.md');
const source = JSON.parse(readFileSync(join(root, 'docs/weaknesses.json'), 'utf8'));

const header = '| What | Why it is like this | Real fix |\n|---|---|---|';
const table = [header, ...source.weaknesses.map((w) => `| ${w.what} | ${w.why} | ${w.fix} |`)].join('\n');

const readme = readFileSync(readmePath, 'utf8').replace(/\r\n/g, '\n');
const start = readme.indexOf('| What | Why it is like this | Real fix |');
const end = readme.indexOf('\n\n', start);
if (start < 0 || end < 0) {
  console.error('render-weaknesses: no weaknesses table found in README.md');
  process.exit(1);
}
const next = readme.slice(0, start) + table + readme.slice(end);

if (process.argv.includes('--check')) {
  if (next !== readme) {
    console.error('render-weaknesses: README.md is out of date with weaknesses.json — run node scripts/render-weaknesses.mjs');
    process.exit(1);
  }
  console.log(`render-weaknesses: README.md matches weaknesses.json (${source.weaknesses.length} rows)`);
} else {
  writeFileSync(readmePath, next);
  console.log(`render-weaknesses: wrote ${source.weaknesses.length} rows to README.md`);
}
