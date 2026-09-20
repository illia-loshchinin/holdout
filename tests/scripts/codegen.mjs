// Regenerates the Codama Kit client in `tests/clients/holdout/` from the Anchor IDL
// committed at `idl/holdout.json` (quickstart §3, research R2).
//
// The IDL is the build's output: after `anchor build`, copy `target/idl/holdout.json`
// to `idl/holdout.json` and commit both it and the regenerated client together, so the
// client in the repository is always the one the committed IDL describes.

import { existsSync, rmSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import { rootNodeFromAnchor } from '@codama/nodes-from-anchor';
import { renderVisitor } from '@codama/renderers-js';
import { createFromRoot } from 'codama';

const testsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const idlPath = path.resolve(testsDir, '..', 'idl', 'holdout.json');
const outDir = path.resolve(testsDir, 'clients', 'holdout');

if (!existsSync(idlPath)) {
  console.error(`No IDL at ${idlPath}. Run \`NO_DNA=1 anchor build\` and copy target/idl/holdout.json there.`);
  process.exit(1);
}

const idl = JSON.parse(await readFile(idlPath, 'utf8'));
const codama = createFromRoot(rootNodeFromAnchor(idl));

// A stale file from a removed instruction would otherwise linger in the client.
rmSync(outDir, { recursive: true, force: true });
codama.accept(renderVisitor(outDir, { formatCode: false }));

console.log(`Generated ${path.relative(path.resolve(testsDir, '..'), outDir)} from ${path.relative(path.resolve(testsDir, '..'), idlPath)}`);
