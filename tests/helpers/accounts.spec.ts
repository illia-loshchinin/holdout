/**
 * The account codecs against the bytes Rust produced. Task T016.
 *
 * `layout-vectors.json` is written by `programs/holdout/src/state/layout_tests.rs`
 * from Anchor's own serializer. Each account below carries the *same* distinct
 * value per field as that Rust test, so decoding must land every value on its
 * named field: a swapped pair of same-width fields, a missing field, or a wrong
 * endianness cannot survive.
 */

import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

import { getAddressDecoder, type Address } from '@solana/kit';
import { describe, expect, it } from 'vitest';

import {
  Cohort,
  Config,
  LeagueConfig,
  Membership,
  Profile,
  discriminatorOf,
  fromHex,
  toHex,
  type CohortAccount,
  type ConfigAccount,
  type LeagueConfigAccount,
  type MembershipAccount,
  type ProfileAccount,
} from './accounts.js';

const HERE = dirname(fileURLToPath(import.meta.url));
const golden = JSON.parse(readFileSync(resolve(HERE, 'layout-vectors.json'), 'utf8')) as Record<string, string>;

/** A pubkey of 32 identical bytes, matching `Pubkey::new_from_array([b; 32])` in Rust. */
const key = (b: number): Address => getAddressDecoder().decode(new Uint8Array(32).fill(b));

const config: ConfigAccount = {
  admin: key(0x11),
  operator: key(0x22),
  sponsor: key(0x33),
  skrMint: key(0x44),
  feeDestination: key(0x55),
  developerDestination: key(0x56),
  genesisGateEnabled: true,
  chipSet: {
    amounts: [10_000_000n, 25_000_000n, 50_000_000n, 100_000_000n],
    version: 7,
    publishedAt: 1_790_000_123n,
    maxTopTier: 120_000_000n,
  },
  faucet: {
    solTopUp: 20_000_000n,
    sponsorFloat: 50_000_000n,
    dailyLamportCap: 720_000_000n,
    lamportDay: 20_350n,
    lamportsToday: 123_456n,
  },
  bump: 254,
};

const leagueConfig: LeagueConfigAccount = {
  leagueId: 1,
  durationDays: 21,
  repsPerDay: 25,
  exercise: 0,
  dayLength: 86_400n,
  firstStart: 1_790_571_600n,
  cadence: 604_800n,
  joinLock: 3_600n,
  nextIndex: 3,
  form: 1,
  boostReserve: 4_000_000n,
  comebackReserve: 2_000_000n,
  reserveVaultBump: 252,
  bump: 253,
};

const cohort: CohortAccount = {
  leagueId: 1,
  index: 2,
  start: 1_791_176_400n,
  joinCutoff: 1_791_172_800n,
  durationDays: 21,
  repsPerDay: 25,
  dayLength: 86_400n,
  exercise: 0,
  form: 1,
  chips: [10_000_000n, 25_000_000n, 50_000_000n, 100_000_000n],
  memberCount: 12,
  heldStake: 900n,
  pool: 680n,
  comebackDue: 300n,
  forfeitsRecorded: 3,
  counted: 9,
  finisherCount: 4,
  finishersPerTier: [1, 0, 2, 1],
  totalFinisherStake: 400n,
  settled: true,
  sharePerTier: [17n, 0n, 42n, 85n],
  roundingRemainder: 2n,
  noFinisherBoost: 500n,
  boostReceived: 501n,
  comebackOwed: 77n,
  comebackFunded: 66n,
  comebackClaimed: 11n,
  vaultBump: 251,
  bump: 250,
};

const membership: MembershipAccount = {
  cohort: key(0x77),
  wallet: key(0x88),
  tier: 2,
  stake: 50_000_000n,
  joinedAt: 1_791_000_000n,
  daysCompleted: 13,
  state: 'Finished',
  counted: true,
  comebackEntitled: 5_000_000n,
  bump: 249,
};

const profile: ProfileAccount = {
  wallet: key(0x99),
  leagues: [
    {
      cleared: 5, forfeited: 2, currentStreak: 3, longestStreak: 4, lastChanged: 1_790_600_000n,
      comebackStake: 25_000_000n, comebackLeague: 0, comebackRound: 7, comebackLastRound: 8,
    },
    {
      cleared: 1, forfeited: 0, currentStreak: 1, longestStreak: 1, lastChanged: 1_791_400_000n,
      comebackStake: 0n, comebackLeague: 0, comebackRound: 0, comebackLastRound: 0,
    },
    {
      cleared: 0, forfeited: 1, currentStreak: 0, longestStreak: 0, lastChanged: 1_790_700_000n,
      comebackStake: 10_000_000n, comebackLeague: 101, comebackRound: 12, comebackLastRound: 12,
    },
  ],
  running: { cohort: key(0xaa), end: 1_791_800_000n },
  pending: { cohort: key(0xbb), start: 1_792_000_000n, end: 1_792_600_000n },
  lastFaucetDay: 20_351n,
  bump: 248,
};

const cases = [
  ['Config', Config, config, 294],
  ['LeagueConfig', LeagueConfig, leagueConfig, 68],
  ['Cohort', Cohort, cohort, 221],
  ['Membership', Membership, membership, 101],
  ['Profile', Profile, profile, 236],
] as const;

describe.each(cases)('%s', (name, layout, expected, space) => {
  it('decodes the bytes Rust serialized into every named field', () => {
    const bytes = fromHex(golden[name] as string);
    expect(bytes.length).toBe(space);
    expect(layout.decode(bytes)).toEqual(expected);
  });

  it('encodes back to exactly the same bytes', () => {
    expect(toHex(layout.encode(expected as never))).toBe(golden[name]);
  });

  it('allocates 8 + the packed fields, and the size matches the Rust INIT_SPACE test', () => {
    expect(layout.space).toBe(space);
  });

  it('refuses bytes of the wrong length, a wrong discriminator, or trailing data', () => {
    const bytes = fromHex(golden[name] as string);
    expect(() => layout.decode(bytes.subarray(0, bytes.length - 1))).toThrow(/expected \d+ bytes/);

    const wrongHead = new Uint8Array(bytes);
    wrongHead[0] = (wrongHead[0]! + 1) & 0xff;
    expect(() => layout.decode(wrongHead)).toThrow(/discriminator/);

    const longer = new Uint8Array(bytes.length + 1);
    longer.set(bytes);
    expect(() => layout.decode(longer)).toThrow();
  });
});

describe('discriminators', () => {
  it('are the sha256 prefixes Anchor derives, matching the constants the Rust test asserts', () => {
    expect(toHex(discriminatorOf('Config'))).toBe('9b0caae01efacc82');
    expect(toHex(discriminatorOf('LeagueConfig'))).toBe('e0ce2666626a2128');
    expect(toHex(discriminatorOf('Cohort'))).toBe('8911d53d694090a9');
    expect(toHex(discriminatorOf('Membership'))).toBe('e78db4626da8afa6');
    expect(toHex(discriminatorOf('Profile'))).toBe('b865a5bc5f3f7fbc');
  });
});

describe('a membership state', () => {
  it('is one byte holding the variant index, in declaration order', () => {
    for (const [i, state] of (['Joined', 'Forfeited', 'Finished', 'Claimed'] as const).entries()) {
      const bytes = Membership.encode({ ...membership, state });
      // 8 discriminator + 32 + 32 + 1 tier + 8 stake + 8 joined_at + 1 days = offset 90.
      expect(bytes[90]).toBe(i);
      expect(Membership.decode(bytes).state).toBe(state);
    }
  });

  it('refuses a variant that does not exist', () => {
    const bytes = Membership.encode(membership);
    bytes[90] = 9;
    expect(() => Membership.decode(bytes)).toThrow(/variant 9/);
  });
});
