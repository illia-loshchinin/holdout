//! The on-chain byte layout of every account, pinned across languages. Task T016.
//!
//! `tests/helpers/layout-vectors.json` holds one hex string per account, produced
//! by *this* file from Anchor's own serializer. The TypeScript codecs in
//! `tests/helpers/accounts.ts` must decode each string back into the values below,
//! field for field — so a field the Rust struct gained, dropped or reordered fails
//! one suite or the other instead of silently mis-reading a cohort in a scenario.
//!
//! Every numeric field carries a distinct value, so two swapped fields of the same
//! width cannot cancel out. The values are repeated, by name, in
//! `tests/helpers/harness.spec.ts`; the golden hex is what ties the two copies.
//!
//! To regenerate after a deliberate layout change (which is a migration, and so
//! also a spec change — constitution V):
//!
//! ```text
//! HOLDOUT_REGEN_LAYOUT=1 cargo test -p holdout layout -- --nocapture
//! ```

use anchor_lang::prelude::Pubkey;
use anchor_lang::{AccountSerialize, Discriminator, Space};

use super::cohort::Cohort;
use super::config::{ChipSet, Config, Faucet};
use super::league::LeagueConfig;
use super::membership::{Membership, MembershipState};
use super::profile::{LeagueProgress, PendingSlot, Profile, RunningSlot};

const VECTORS: &str = include_str!("../../../../tests/helpers/layout-vectors.json");

fn key(byte: u8) -> Pubkey {
    Pubkey::new_from_array([byte; 32])
}

fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

fn serialize<T: AccountSerialize>(account: &T) -> Vec<u8> {
    let mut out = Vec::new();
    account.try_serialize(&mut out).expect("account serializes");
    out
}

fn config() -> Config {
    Config {
        admin: key(0x11),
        operator: key(0x22),
        sponsor: key(0x33),
        skr_mint: key(0x44),
        fee_destination: key(0x55),
        ladder_gate_enabled: true,
        genesis_gate_enabled: false,
        chip_set: ChipSet {
            amounts: [10_000_000, 25_000_000, 50_000_000, 100_000_000],
            version: 7,
            published_at: 1_790_000_123,
            max_top_tier: 120_000_000,
        },
        faucet: Faucet {
            sol_top_up: 20_000_000,
            sponsor_float: 50_000_000,
            daily_lamport_cap: 720_000_000,
            lamport_day: 20_350,
            lamports_today: 123_456,
        },
        bump: 254,
    }
}

fn league_config() -> LeagueConfig {
    LeagueConfig {
        league_id: 1,
        duration_days: 21,
        reps_per_day: 25,
        exercise: 0,
        day_length: 86_400,
        first_start: 1_790_571_600,
        cadence: 604_800,
        join_lock: 3_600,
        next_index: 3,
        bump: 253,
    }
}

fn cohort() -> Cohort {
    Cohort {
        league_id: 1,
        index: 2,
        start: 1_791_176_400,
        join_cutoff: 1_791_172_800,
        duration_days: 21,
        reps_per_day: 25,
        day_length: 86_400,
        exercise: 0,
        chips: [10_000_000, 25_000_000, 50_000_000, 100_000_000],
        member_count: 12,
        held_stake: 900,
        pool: 680,
        carry_over_received: 600,
        forfeits_recorded: 3,
        counted: 9,
        finisher_count: 4,
        finishers_per_tier: [1, 0, 2, 1],
        total_finisher_stake: 400,
        settled: true,
        share_per_tier: [17, 0, 42, 85],
        rounding_remainder: 2,
        no_finisher_fee: 500,
        carry_over_sent: 501,
        carry_over_held: 7,
        carry_over_target: key(0x66),
        vault_bump: 251,
        bump: 250,
    }
}

fn membership() -> Membership {
    Membership {
        cohort: key(0x77),
        wallet: key(0x88),
        tier: 2,
        stake: 50_000_000,
        joined_at: 1_791_000_000,
        days_completed: 13,
        state: MembershipState::Finished,
        counted: true,
        bump: 249,
    }
}

fn profile() -> Profile {
    Profile {
        wallet: key(0x99),
        qualified: true,
        leagues: [
            LeagueProgress {
                cleared: 5,
                forfeited: 2,
                current_streak: 3,
                longest_streak: 4,
                last_changed: 1_790_600_000,
            },
            LeagueProgress {
                cleared: 1,
                forfeited: 0,
                current_streak: 1,
                longest_streak: 1,
                last_changed: 1_791_400_000,
            },
            LeagueProgress {
                cleared: 0,
                forfeited: 1,
                current_streak: 0,
                longest_streak: 0,
                last_changed: 1_790_700_000,
            },
        ],
        running: RunningSlot {
            cohort: key(0xaa),
            end: 1_791_800_000,
        },
        pending: PendingSlot {
            cohort: key(0xbb),
            start: 1_792_000_000,
            end: 1_792_600_000,
        },
        last_faucet_day: 20_351,
        bump: 248,
    }
}

/// Every account, in the order the JSON lists them.
fn all() -> Vec<(&'static str, Vec<u8>)> {
    vec![
        ("Config", serialize(&config())),
        ("LeagueConfig", serialize(&league_config())),
        ("Cohort", serialize(&cohort())),
        ("Membership", serialize(&membership())),
        ("Profile", serialize(&profile())),
    ]
}

#[test]
fn layouts_match_the_golden_bytes_the_typescript_codecs_decode() {
    if std::env::var("HOLDOUT_REGEN_LAYOUT").is_ok() {
        let body: Vec<String> = all()
            .iter()
            .map(|(name, bytes)| format!("  \"{name}\": \"{}\"", hex(bytes)))
            .collect();
        println!(
            "--- layout-vectors.json ---\n{{\n{}\n}}\n--- end ---",
            body.join(",\n")
        );
        return;
    }

    let golden: serde_json::Value =
        serde_json::from_str(VECTORS).expect("layout-vectors.json parses");
    for (name, bytes) in all() {
        let expected = golden[name]
            .as_str()
            .unwrap_or_else(|| panic!("no golden for {name}"));
        assert_eq!(
            hex(&bytes),
            expected,
            "{name} serializes differently from its golden bytes"
        );
    }
}

#[test]
fn every_account_is_the_size_the_typescript_side_allocates() {
    // 8 bytes of discriminator + the packed fields. If one of these moves, the
    // TS `space` for the account moves with it, or a scenario would fund and
    // allocate a different account than the program does.
    assert_eq!(8 + Config::INIT_SPACE, 263);
    assert_eq!(8 + LeagueConfig::INIT_SPACE, 50);
    assert_eq!(8 + Cohort::INIT_SPACE, 236);
    assert_eq!(8 + Membership::INIT_SPACE, 93);
    assert_eq!(8 + Profile::INIT_SPACE, 186);
}

#[test]
fn discriminators_are_anchors_default_sha256_prefixes() {
    // sha256("account:<Name>")[..8], computed independently in TypeScript.
    assert_eq!(
        Config::DISCRIMINATOR,
        [0x9b, 0x0c, 0xaa, 0xe0, 0x1e, 0xfa, 0xcc, 0x82]
    );
    assert_eq!(
        LeagueConfig::DISCRIMINATOR,
        [0xe0, 0xce, 0x26, 0x66, 0x62, 0x6a, 0x21, 0x28]
    );
    assert_eq!(
        Cohort::DISCRIMINATOR,
        [0x89, 0x11, 0xd5, 0x3d, 0x69, 0x40, 0x90, 0xa9]
    );
    assert_eq!(
        Membership::DISCRIMINATOR,
        [0xe7, 0x8d, 0xb4, 0x62, 0x6d, 0xa8, 0xaf, 0xa6]
    );
    assert_eq!(
        Profile::DISCRIMINATOR,
        [0xb8, 0x65, 0xa5, 0xbc, 0x5f, 0x3f, 0x7f, 0xbc]
    );
}
