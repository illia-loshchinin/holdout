/**
 * No instruction takes a price, a fiat figure or an oracle account. Task T017a,
 * acceptance criterion 9, constitution III.
 *
 * Two halves, because either one alone can be fooled:
 *
 *  - the **source** check (`scripts/check-no-oracle.sh`) catches an identifier
 *    that would read a rate, even in code no instruction reaches yet;
 *  - the **IDL** check reads the built interface and asserts that nothing a
 *    caller can pass — an argument, an account, a type field — is named after a
 *    price. The IDL is what clients generate from, so this is the surface a
 *    reviewer would actually inspect.
 *
 * Until `anchor build` has produced `target/idl/holdout.json` the IDL half fails
 * with a message saying so, which is the state constitution VII asks for.
 */

import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { describe, expect, it } from 'vitest';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = resolve(HERE, '../..');
const IDL_PATH = resolve(ROOT, 'target/idl/holdout.json');

/** Words that may not name anything a caller can pass in. */
const FORBIDDEN = /price|oracle|pyth|switchboard|usd|fiat|dollar/i;

/** Every string that names something in an IDL: args, accounts, fields, types. */
const namesIn = (node: unknown, found: string[] = []): string[] => {
  if (Array.isArray(node)) {
    for (const item of node) namesIn(item, found);
    return found;
  }
  if (node && typeof node === 'object') {
    for (const [key, value] of Object.entries(node as Record<string, unknown>)) {
      if (key === 'name' && typeof value === 'string') found.push(value);
      else namesIn(value, found);
    }
  }
  return found;
};

describe('the contract never reads a price (constitution III)', () => {
  it('has no price, oracle or fiat identifier in its source', () => {
    // Throws with the offending lines on a hit, which is the assertion.
    const out = execFileSync('bash', [resolve(ROOT, 'scripts/check-no-oracle.sh')], {
      encoding: 'utf8',
    });
    expect(out).toContain('clean');
  });

  it('exposes no argument, account or field named after a price', () => {
    expect(
      existsSync(IDL_PATH),
      `no IDL at target/idl/holdout.json — run \`anchor build\` first`,
    ).toBe(true);

    const idl = JSON.parse(readFileSync(IDL_PATH, 'utf8')) as unknown;
    const offenders = namesIn(idl).filter((name) => FORBIDDEN.test(name));
    expect(offenders, `IDL names a price: ${offenders.join(', ')}`).toEqual([]);
  });

  it('keeps the only rate in the crank, where it stays off-chain', () => {
    // The Operator prices the chips before a cohort exists; the instruction that
    // stores them takes four SKR amounts and nothing else (FR-055a, research R15).
    const contract = readFileSync(
      resolve(ROOT, 'specs/001-holdout-hackathon/contracts/program-instructions.md'),
      'utf8',
    );
    const publishRow = contract.split('\n').find((line) => line.includes('`publish_chip_set`'));
    expect(publishRow).toBeDefined();
    expect(publishRow).toContain('amounts: [u64; 4]');
    expect(publishRow).not.toMatch(/\brate\b|\bprice\b/i);
  });
});
