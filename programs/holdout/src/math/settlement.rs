//! Settlement arithmetic — pure, no Anchor types, no clock.
//!
//! Every function here is described by `fixtures/settlement-vectors.json`
//! (PRD §19) and by the tests at the bottom of this file, which were written
//! **before** the logic (constitution VII). They fail until T018 implements it.
//!
//! The shape is per-tier rather than per-member so settlement finalises in
//! constant time: a cohort has exactly four chip amounts (FR-055), so four
//! shares describe every finisher (research R10).

/// Percent of a forfeited stake taken as the fee; the rest reaches the pool (FR-027).
pub const FEE_PERCENT: u128 = 30;

/// Fee taken from one forfeited stake: 30 %, rounded down (FR-027).
pub fn fee_of(forfeited_stake: u64) -> u64 {
    // u128 throughout: `stake * 30` overflows u64 above ~6.1e17 base units, and
    // a chip amount is an ordinary cohort parameter, not a bounded constant.
    ((forfeited_stake as u128) * FEE_PERCENT / 100) as u64
}

/// What one forfeited stake adds to the pool: the stake minus its fee.
pub fn pool_contribution(forfeited_stake: u64) -> u64 {
    // The whole stake leaves the member; the fee is the only part that does not
    // reach the pool, so the two always add back to the stake (INV-2).
    forfeited_stake - fee_of(forfeited_stake)
}

/// A tier's share of the pool, rounded down (FR-032, FR-032a).
///
/// `total_finisher_stake == 0` has no finishers to pay, so the caller must use
/// [`no_finisher_fee`] instead; this returns 0 rather than dividing by zero.
pub fn share_for_tier(pool: u64, chip: u64, total_finisher_stake: u64) -> u64 {
    if total_finisher_stake == 0 {
        return 0;
    }
    ((pool as u128) * (chip as u128) / (total_finisher_stake as u128)) as u64
}

/// Pool minus every rounded-down share. Always fewer units than there are
/// finishers, and it goes to the fee destination at settlement (FR-032a).
pub fn rounding_remainder(pool: u64, finishers_per_tier: [u32; 4], shares: [u64; 4]) -> u64 {
    let paid: u128 = finishers_per_tier
        .iter()
        .zip(shares.iter())
        .map(|(count, share)| u128::from(*count) * u128::from(*share))
        .sum();
    // Every share is floored, so the sum can never exceed the pool; saturating
    // rather than wrapping keeps a caller's arithmetic slip from minting units.
    (u128::from(pool).saturating_sub(paid)) as u64
}

/// A cohort nobody finished: its whole pool goes to the fee destination, and
/// nothing carries over (FR-037). Nothing is split, so nothing is lost to rounding.
pub fn no_finisher_fee(pool: u64) -> u64 {
    pool
}

#[cfg(test)]
mod tests {
    use super::*;

    const FIXTURES: &str = include_str!("../../../../fixtures/settlement-vectors.json");

    #[derive(serde::Deserialize)]
    struct Fixture {
        math_vectors: Vec<Vector>,
    }

    #[derive(serde::Deserialize)]
    struct Vector {
        id: String,
        name: String,
        #[serde(default)]
        pool_override: Option<u64>,
        #[serde(default)]
        forfeited_stakes: std::collections::BTreeMap<String, u32>,
        #[serde(default)]
        finisher_stakes: std::collections::BTreeMap<String, u32>,
        expected: Expected,
    }

    #[derive(serde::Deserialize)]
    struct Expected {
        #[serde(default)]
        forfeited: u64,
        #[serde(default)]
        fee: u64,
        pool: u64,
        #[serde(default)]
        total_finisher_stake: u64,
        #[serde(default)]
        shares: std::collections::BTreeMap<String, u64>,
        #[serde(default)]
        remainder: u64,
        #[serde(default)]
        to_fee_destination: Option<u64>,
    }

    fn load() -> Fixture {
        serde_json::from_str(FIXTURES).expect("fixtures/settlement-vectors.json parses")
    }

    #[test]
    fn every_vector_reproduces_exactly() {
        for v in load().math_vectors {
            let ctx = format!("{} — {}", v.id, v.name);

            // Fee and pool from the forfeits, unless the vector states a pool.
            let (fee, pool) = match v.pool_override {
                Some(p) => (0, p),
                None => {
                    let mut forfeited = 0u64;
                    let mut fee = 0u64;
                    let mut pool = 0u64;
                    for (chip, count) in &v.forfeited_stakes {
                        let chip: u64 = chip.parse().unwrap();
                        for _ in 0..*count {
                            forfeited += chip;
                            fee += fee_of(chip);
                            pool += pool_contribution(chip);
                        }
                    }
                    // The vector states the forfeited total; read it, so a vector
                    // whose stakes and total disagree fails here rather than
                    // silently agreeing with whatever the stakes add up to.
                    assert_eq!(forfeited, v.expected.forfeited, "forfeited — {ctx}");
                    assert_eq!(
                        fee + pool,
                        forfeited,
                        "fee and pool split the forfeits — {ctx}"
                    );
                    (fee, pool)
                }
            };
            assert_eq!(fee, v.expected.fee, "fee — {ctx}");
            assert_eq!(pool, v.expected.pool, "pool — {ctx}");

            let total: u64 = v
                .finisher_stakes
                .iter()
                .map(|(chip, count)| chip.parse::<u64>().unwrap() * u64::from(*count))
                .sum();
            assert_eq!(
                total, v.expected.total_finisher_stake,
                "finisher stake — {ctx}"
            );

            if total == 0 {
                assert_eq!(
                    Some(no_finisher_fee(pool)),
                    v.expected.to_fee_destination,
                    "to the fee destination — {ctx}"
                );
                assert_eq!(no_finisher_fee(pool), pool, "nothing left behind — {ctx}");
                continue;
            }

            let mut tiers = [0u64; 4];
            let mut counts = [0u32; 4];
            for (i, (chip, count)) in v.finisher_stakes.iter().enumerate() {
                let chip: u64 = chip.parse().unwrap();
                let share = share_for_tier(pool, chip, total);
                assert_eq!(
                    Some(&share),
                    v.expected.shares.get(&chip.to_string()),
                    "share for {chip} — {ctx}"
                );
                tiers[i] = share;
                counts[i] = *count;
            }

            let remainder = rounding_remainder(pool, counts, tiers);
            assert_eq!(remainder, v.expected.remainder, "remainder — {ctx}");

            let paid: u64 = counts
                .iter()
                .zip(tiers.iter())
                .map(|(c, s)| u64::from(*c) * s)
                .sum();
            assert_eq!(paid + remainder, pool, "pool fully allocated — {ctx}");
            assert!(
                remainder < counts.iter().map(|c| u64::from(*c)).sum::<u64>().max(1),
                "remainder is smaller than the finisher count — {ctx}"
            );
        }
    }

    #[test]
    fn fee_is_thirty_percent_rounded_down() {
        assert_eq!(fee_of(100), 30);
        assert_eq!(fee_of(1), 0);
        assert_eq!(fee_of(9), 2);
        assert_eq!(fee_of(1_250), 375);
        assert_eq!(pool_contribution(9), 7);
    }

    #[test]
    fn a_cohort_with_no_pool_pays_exactly_the_stakes() {
        assert_eq!(share_for_tier(0, 100, 500), 0);
    }
}
