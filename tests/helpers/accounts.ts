/**
 * Byte-level codecs for the program's five accounts (data-model §1). Task T016.
 *
 * Anchor accounts are an 8-byte discriminator followed by packed Borsh: no
 * padding, little-endian integers, a bool as one byte, an enum as one byte for
 * its variant index. The codecs here are written by hand, field for field, in
 * the order of the Rust structs — and pinned to the Rust side by a shared
 * golden file (`layout-vectors.json`, read by both `cargo test` and the harness
 * spec), so a swapped or missing field fails a build in one language or the other.
 *
 * The Codama-generated client (T029) replaces these once instructions exist. Until
 * then these are what lets a scenario build an arbitrary state and read it back.
 */

import { createHash } from 'node:crypto';

import { address, getAddressDecoder, getAddressEncoder, type Address } from '@solana/kit';

// ------------------------------------------------------------------ mini-codec

class Reader {
  offset = 0;
  private readonly view: DataView;
  constructor(readonly bytes: Uint8Array) {
    this.view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  }
  take(n: number): Uint8Array {
    if (this.offset + n > this.bytes.length) {
      throw new Error(`account data ends at ${this.bytes.length}, a field needs ${this.offset + n}`);
    }
    const out = this.bytes.subarray(this.offset, this.offset + n);
    this.offset += n;
    return out;
  }
  u8(): number { const v = this.view.getUint8(this.offset); this.take(1); return v; }
  u16(): number { const v = this.view.getUint16(this.offset, true); this.take(2); return v; }
  u32(): number { const v = this.view.getUint32(this.offset, true); this.take(4); return v; }
  u64(): bigint { const v = this.view.getBigUint64(this.offset, true); this.take(8); return v; }
  i64(): bigint { const v = this.view.getBigInt64(this.offset, true); this.take(8); return v; }
}

class Writer {
  private readonly out: Uint8Array;
  private readonly view: DataView;
  offset = 0;
  constructor(size: number) {
    this.out = new Uint8Array(size);
    this.view = new DataView(this.out.buffer);
  }
  put(bytes: Uint8Array): void { this.out.set(bytes, this.offset); this.offset += bytes.length; }
  u8(v: number): void { this.view.setUint8(this.offset, v); this.offset += 1; }
  u16(v: number): void { this.view.setUint16(this.offset, v, true); this.offset += 2; }
  u32(v: number): void { this.view.setUint32(this.offset, v, true); this.offset += 4; }
  u64(v: bigint): void { this.view.setBigUint64(this.offset, v, true); this.offset += 8; }
  i64(v: bigint): void { this.view.setBigInt64(this.offset, v, true); this.offset += 8; }
  finish(): Uint8Array {
    if (this.offset !== this.out.length) {
      throw new Error(`wrote ${this.offset} bytes into a ${this.out.length}-byte layout`);
    }
    return this.out;
  }
}

interface Codec<T> {
  readonly size: number;
  read(r: Reader): T;
  write(w: Writer, v: T): void;
}

const u8: Codec<number> = { size: 1, read: (r) => r.u8(), write: (w, v) => w.u8(v) };
const u16: Codec<number> = { size: 2, read: (r) => r.u16(), write: (w, v) => w.u16(v) };
const u32: Codec<number> = { size: 4, read: (r) => r.u32(), write: (w, v) => w.u32(v) };
const u64: Codec<bigint> = { size: 8, read: (r) => r.u64(), write: (w, v) => w.u64(v) };
const i64: Codec<bigint> = { size: 8, read: (r) => r.i64(), write: (w, v) => w.i64(v) };
const bool: Codec<boolean> = {
  size: 1,
  read: (r) => {
    const b = r.u8();
    if (b > 1) throw new Error(`a bool byte is 0 or 1, found ${b}`);
    return b === 1;
  },
  write: (w, v) => w.u8(v ? 1 : 0),
};
const pubkey: Codec<Address> = {
  size: 32,
  read: (r) => getAddressDecoder().decode(r.take(32)),
  write: (w, v) => w.put(new Uint8Array(getAddressEncoder().encode(v))),
};

const array = <T>(item: Codec<T>, length: number): Codec<T[]> => ({
  size: item.size * length,
  read: (r) => Array.from({ length }, () => item.read(r)),
  write: (w, v) => {
    if (v.length !== length) throw new Error(`expected ${length} elements, got ${v.length}`);
    v.forEach((x) => item.write(w, x));
  },
});

/** A fixed-length tuple of `T` — narrower than `array` for the typed accounts below. */
const tuple4 = <T>(item: Codec<T>): Codec<[T, T, T, T]> => array(item, 4) as unknown as Codec<[T, T, T, T]>;

type Fields<T> = { [K in keyof T]: Codec<T[K]> };

/** Fields are written in the order of `order`, which is the Rust struct's order. */
const struct = <T extends object>(fields: Fields<T>, order: readonly (keyof T)[]): Codec<T> => ({
  size: order.reduce((n, k) => n + fields[k].size, 0),
  read: (r) => {
    const out = {} as T;
    for (const k of order) out[k] = fields[k].read(r);
    return out;
  },
  write: (w, v) => {
    for (const k of order) fields[k].write(w, v[k]);
  },
});

const enumU8 = <N extends string>(names: readonly N[]): Codec<N> => ({
  size: 1,
  read: (r) => {
    const i = r.u8();
    const name = names[i];
    if (name === undefined) throw new Error(`enum variant ${i} does not exist (${names.join(', ')})`);
    return name;
  },
  write: (w, v) => {
    const i = names.indexOf(v);
    if (i < 0) throw new Error(`unknown enum variant ${v}`);
    w.u8(i);
  },
});

// -------------------------------------------------------------------- accounts

export type MembershipState = 'Joined' | 'Forfeited' | 'Finished' | 'Claimed';
export const MEMBERSHIP_STATES = ['Joined', 'Forfeited', 'Finished', 'Claimed'] as const;

export interface ChipSet {
  amounts: [bigint, bigint, bigint, bigint];
  version: number;
  publishedAt: bigint;
  maxTopTier: bigint;
}

export interface Faucet {
  solTopUp: bigint;
  sponsorFloat: bigint;
  dailyLamportCap: bigint;
  lamportDay: bigint;
  lamportsToday: bigint;
}

export interface ConfigAccount {
  admin: Address;
  operator: Address;
  sponsor: Address;
  skrMint: Address;
  feeDestination: Address;
  ladderGateEnabled: boolean;
  genesisGateEnabled: boolean;
  chipSet: ChipSet;
  faucet: Faucet;
  bump: number;
}

export interface LeagueConfigAccount {
  leagueId: number;
  durationDays: number;
  repsPerDay: number;
  exercise: number;
  dayLength: bigint;
  firstStart: bigint;
  cadence: bigint;
  joinLock: bigint;
  nextIndex: number;
  bump: number;
}

export interface CohortAccount {
  leagueId: number;
  index: number;
  start: bigint;
  joinCutoff: bigint;
  durationDays: number;
  repsPerDay: number;
  dayLength: bigint;
  exercise: number;
  chips: [bigint, bigint, bigint, bigint];
  memberCount: number;
  heldStake: bigint;
  pool: bigint;
  carryOverReceived: bigint;
  forfeitsRecorded: number;
  counted: number;
  finisherCount: number;
  finishersPerTier: [number, number, number, number];
  totalFinisherStake: bigint;
  settled: boolean;
  sharePerTier: [bigint, bigint, bigint, bigint];
  roundingRemainder: bigint;
  noFinisherFee: bigint;
  carryOverSent: bigint;
  carryOverHeld: bigint;
  carryOverTarget: Address;
  vaultBump: number;
  bump: number;
}

export interface MembershipAccount {
  cohort: Address;
  wallet: Address;
  tier: number;
  stake: bigint;
  joinedAt: bigint;
  daysCompleted: number;
  state: MembershipState;
  counted: boolean;
  bump: number;
}

export interface LeagueProgress {
  cleared: number;
  forfeited: number;
  currentStreak: number;
  longestStreak: number;
  lastChanged: bigint;
}

export interface RunningSlot { cohort: Address; end: bigint }
export interface PendingSlot { cohort: Address; start: bigint; end: bigint }

export interface ProfileAccount {
  wallet: Address;
  qualified: boolean;
  /** Slot 0 = 7-day, 1 = 21-day, 2 = every test league (`Profile::slot_of`). */
  leagues: [LeagueProgress, LeagueProgress, LeagueProgress];
  running: RunningSlot;
  pending: PendingSlot;
  lastFaucetDay: bigint;
  bump: number;
}

const chipSet = struct<ChipSet>(
  { amounts: tuple4(u64), version: u32, publishedAt: i64, maxTopTier: u64 },
  ['amounts', 'version', 'publishedAt', 'maxTopTier'],
);

const faucet = struct<Faucet>(
  { solTopUp: u64, sponsorFloat: u64, dailyLamportCap: u64, lamportDay: i64, lamportsToday: u64 },
  ['solTopUp', 'sponsorFloat', 'dailyLamportCap', 'lamportDay', 'lamportsToday'],
);

const leagueProgress = struct<LeagueProgress>(
  { cleared: u16, forfeited: u16, currentStreak: u16, longestStreak: u16, lastChanged: i64 },
  ['cleared', 'forfeited', 'currentStreak', 'longestStreak', 'lastChanged'],
);

const runningSlot = struct<RunningSlot>({ cohort: pubkey, end: i64 }, ['cohort', 'end']);
const pendingSlot = struct<PendingSlot>(
  { cohort: pubkey, start: i64, end: i64 },
  ['cohort', 'start', 'end'],
);

const configCodec = struct<ConfigAccount>(
  {
    admin: pubkey, operator: pubkey, sponsor: pubkey, skrMint: pubkey, feeDestination: pubkey,
    ladderGateEnabled: bool, genesisGateEnabled: bool, chipSet, faucet, bump: u8,
  },
  [
    'admin', 'operator', 'sponsor', 'skrMint', 'feeDestination',
    'ladderGateEnabled', 'genesisGateEnabled', 'chipSet', 'faucet', 'bump',
  ],
);

const leagueConfigCodec = struct<LeagueConfigAccount>(
  {
    leagueId: u8, durationDays: u8, repsPerDay: u16, exercise: u8, dayLength: i64,
    firstStart: i64, cadence: i64, joinLock: i64, nextIndex: u32, bump: u8,
  },
  [
    'leagueId', 'durationDays', 'repsPerDay', 'exercise', 'dayLength',
    'firstStart', 'cadence', 'joinLock', 'nextIndex', 'bump',
  ],
);

const cohortCodec = struct<CohortAccount>(
  {
    leagueId: u8, index: u32, start: i64, joinCutoff: i64, durationDays: u8, repsPerDay: u16,
    dayLength: i64, exercise: u8, chips: tuple4(u64), memberCount: u32, heldStake: u64,
    pool: u64, carryOverReceived: u64, forfeitsRecorded: u32, counted: u32, finisherCount: u32,
    finishersPerTier: tuple4(u32), totalFinisherStake: u64, settled: bool,
    sharePerTier: tuple4(u64), roundingRemainder: u64, noFinisherFee: u64, carryOverSent: u64,
    carryOverHeld: u64, carryOverTarget: pubkey, vaultBump: u8, bump: u8,
  },
  [
    'leagueId', 'index', 'start', 'joinCutoff', 'durationDays', 'repsPerDay',
    'dayLength', 'exercise', 'chips', 'memberCount', 'heldStake',
    'pool', 'carryOverReceived', 'forfeitsRecorded', 'counted', 'finisherCount',
    'finishersPerTier', 'totalFinisherStake', 'settled',
    'sharePerTier', 'roundingRemainder', 'noFinisherFee', 'carryOverSent',
    'carryOverHeld', 'carryOverTarget', 'vaultBump', 'bump',
  ],
);

const membershipCodec = struct<MembershipAccount>(
  {
    cohort: pubkey, wallet: pubkey, tier: u8, stake: u64, joinedAt: i64, daysCompleted: u8,
    state: enumU8(MEMBERSHIP_STATES), counted: bool, bump: u8,
  },
  ['cohort', 'wallet', 'tier', 'stake', 'joinedAt', 'daysCompleted', 'state', 'counted', 'bump'],
);

const profileCodec = struct<ProfileAccount>(
  {
    wallet: pubkey, qualified: bool,
    leagues: array(leagueProgress, 3) as unknown as Codec<ProfileAccount['leagues']>,
    running: runningSlot, pending: pendingSlot, lastFaucetDay: i64, bump: u8,
  },
  ['wallet', 'qualified', 'leagues', 'running', 'pending', 'lastFaucetDay', 'bump'],
);

// ---------------------------------------------------------------- discriminators

/** Anchor's default: the first 8 bytes of `sha256("account:<Name>")`. */
export const discriminatorOf = (accountName: string): Uint8Array =>
  new Uint8Array(createHash('sha256').update(`account:${accountName}`).digest().subarray(0, 8));

export interface AccountLayout<T> {
  readonly name: string;
  readonly discriminator: Uint8Array;
  /** `8 + INIT_SPACE` — the data length the program allocates. */
  readonly space: number;
  encode(value: T): Uint8Array;
  decode(bytes: Uint8Array): T;
}

const layout = <T extends object>(name: string, codec: Codec<T>): AccountLayout<T> => {
  const discriminator = discriminatorOf(name);
  const space = 8 + codec.size;
  return {
    name,
    discriminator,
    space,
    encode: (value) => {
      const w = new Writer(space);
      w.put(discriminator);
      codec.write(w, value);
      return w.finish();
    },
    decode: (bytes) => {
      if (bytes.length !== space) {
        throw new Error(`${name}: expected ${space} bytes, found ${bytes.length}`);
      }
      const head = bytes.subarray(0, 8);
      if (!head.every((b, i) => b === discriminator[i])) {
        throw new Error(`${name}: discriminator mismatch (${Buffer.from(head).toString('hex')})`);
      }
      const r = new Reader(bytes);
      r.take(8);
      const value = codec.read(r);
      if (r.offset !== bytes.length) {
        throw new Error(`${name}: ${bytes.length - r.offset} trailing bytes after the last field`);
      }
      return value;
    },
  };
};

export const Config = layout('Config', configCodec);
export const LeagueConfig = layout('LeagueConfig', leagueConfigCodec);
export const Cohort = layout('Cohort', cohortCodec);
export const Membership = layout('Membership', membershipCodec);
export const Profile = layout('Profile', profileCodec);

/** Convenience for hex goldens shared with the Rust tests. */
export const toHex = (bytes: Uint8Array): string => Buffer.from(bytes).toString('hex');
export const fromHex = (hex: string): Uint8Array => new Uint8Array(Buffer.from(hex, 'hex'));

/** Re-exported so a spec can build an address without importing Kit twice. */
export { address };
