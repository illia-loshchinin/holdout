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
/// send the whole pool to the boost reserve instead; this returns 0 rather than
/// dividing by zero.
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

/// Percents of a forfeited stake for each destination of the fee (FR-027, H35;
/// re-split 2026-10-12 to 70 / 10 / 10 / 10). The operations part is not a
/// constant: it takes whatever the floors leave, so the parts always add back
/// to the stake.
///
/// The comeback part is **the forfeiting member's own**: it is pledged to them,
/// and comes back to them if they finish the next round of the league, or goes
/// to the boost reserve if they do not (FR-037e). Nothing goes to the boost
/// reserve at forfeit time any more.
pub const COMEBACK_PERCENT: u128 = 10;
pub const DEVELOPER_PERCENT: u128 = 10;

/// The most a boost may add to a cohort's own pool, in percent (FR-037d; 10 since
/// 2026-10-10, so a group forfeiting into its own finisher recovers under 1 SKR per SKR lost).
pub const BOOST_CAP_PERCENT: u128 = 10;

fn percent_of(amount: u64, percent: u128) -> u64 {
    // u128: `amount * percent` overflows u64 for large amounts.
    ((amount as u128) * percent / 100) as u64
}

/// Where one forfeited stake goes (FR-027, H35).
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct ForfeitSplit {
    /// To this cohort's prize pool.
    pub pool: u64,
    /// The member's comeback pledge: held in the cohort's vault until the
    /// forfeit is counted, then in the league's comeback reserve (FR-037c).
    pub comeback: u64,
    /// To the developer destination at once.
    pub developer: u64,
    /// To the operations destination at once; takes what the floors leave.
    pub operations: u64,
}

impl ForfeitSplit {
    /// Everything that did not reach the pool: always [`fee_of`] the stake.
    pub fn fee(&self) -> u64 {
        self.comeback + self.developer + self.operations
    }

    /// The parts that leave the cohort's vault when the forfeit is recorded.
    pub fn paid_out_now(&self) -> u64 {
        self.developer + self.operations
    }
}

/// Split one forfeited stake (FR-027, H35).
pub fn forfeit_split(forfeited_stake: u64) -> ForfeitSplit {
    let fee = fee_of(forfeited_stake);
    let comeback = comeback_pledge(forfeited_stake);
    let developer = percent_of(forfeited_stake, DEVELOPER_PERCENT);
    ForfeitSplit {
        pool: forfeited_stake - fee,
        comeback,
        developer,
        // 10 + 10 of the stake can never exceed 30 of it, so this cannot underflow.
        operations: fee - comeback - developer,
    }
}

/// The comeback pledged to a member who forfeited this stake: 10 %, rounded
/// down (FR-027, FR-037e). The same figure at forfeit, at counting and at
/// release, because it is always recomputed from the stake.
pub fn comeback_pledge(forfeited_stake: u64) -> u64 {
    percent_of(forfeited_stake, COMEBACK_PERCENT)
}

/// The boost a finishing cohort draws: 10% of its own pool, never more than the
/// league's boost reserve holds (FR-037d). A cohort with no forfeits gets none.
/// The boost reserve never includes a pledge, so a boost cannot spend one.
pub fn boost_for(own_pool: u64, boost_reserve: u64) -> u64 {
    percent_of(own_pool, BOOST_CAP_PERCENT).min(boost_reserve)
}

/// What a comeback owes a finisher of the comeback round: the pledge, never
/// more than the stake they finished on (FR-037e). The rest of the pledge, if
/// any, goes to the boost reserve.
pub fn comeback_owed(forfeited_stake: u64, new_stake: u64) -> u64 {
    comeback_pledge(forfeited_stake).min(new_stake)
}

/// One finisher's refund: their entitlement, scaled pro rata to what
/// finalization drew. Every owed refund is pledged in the reserve, so `funded`
/// always equals `total_owed` and this is the entitlement itself; the scaling
/// stays so a shortfall could never pay out more than was drawn (FR-037e).
pub fn comeback_refund(entitled: u64, funded: u64, total_owed: u64) -> u64 {
    if total_owed == 0 {
        return 0;
    }
    ((entitled as u128) * (funded as u128) / (total_owed as u128)) as u64
}

#[cfg(test)]
mod tests {
    use super::*;

    const FIXTURES: &str = include_str!("../../../../fixtures/settlement-vectors.json");

    #[derive(serde::Deserialize)]
    struct Fixture {
        math_vectors: Vec<Vector>,
        reserve_vectors: ReserveVectors,
    }

    #[derive(serde::Deserialize)]
    struct ReserveVectors {
        forfeit_split: Vec<SplitVector>,
        boost: Vec<BoostVector>,
        comeback_owed: Vec<OwedVector>,
        comeback_refund: Vec<RefundVector>,
        comeback_round: Vec<RoundVector>,
    }

    #[derive(serde::Deserialize)]
    struct SplitVector {
        stake: u64,
        pool: u64,
        comeback: u64,
        developer: u64,
        operations: u64,
    }

    #[derive(serde::Deserialize)]
    struct BoostVector {
        own_pool: u64,
        reserve: u64,
        boost: u64,
    }

    #[derive(serde::Deserialize)]
    struct OwedVector {
        forfeited_stake: u64,
        new_stake: u64,
        owed: u64,
    }

    #[derive(serde::Deserialize)]
    struct RefundVector {
        entitled: u64,
        funded: u64,
        owed: u64,
        refund: u64,
    }

    #[derive(serde::Deserialize)]
    struct RoundVector {
        first_start: i64,
        cadence: i64,
        join_lock: i64,
        missed_at: i64,
        first: u32,
        last: u32,
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
        /// A cohort nobody finished: its whole pool, to the league's boost reserve (FR-037).
        #[serde(default)]
        to_boost: Option<u64>,
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
                // Nobody to pay: the whole pool goes to the boost reserve (FR-037).
                assert_eq!(Some(pool), v.expected.to_boost, "to the boost — {ctx}");
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
    fn every_reserve_vector_reproduces_exactly() {
        let r = load().reserve_vectors;
        for v in r.forfeit_split {
            let split = forfeit_split(v.stake);
            let ctx = format!("split of {}", v.stake);
            assert_eq!(split.pool, v.pool, "pool — {ctx}");
            assert_eq!(split.comeback, v.comeback, "comeback — {ctx}");
            assert_eq!(split.developer, v.developer, "developer — {ctx}");
            assert_eq!(split.operations, v.operations, "operations — {ctx}");
            assert_eq!(
                split.fee(),
                fee_of(v.stake),
                "the parts of the fee add up — {ctx}"
            );
            assert_eq!(
                u128::from(split.pool) + u128::from(split.fee()),
                u128::from(v.stake),
                "the stake is fully allocated — {ctx}"
            );
        }
        for v in r.boost {
            assert_eq!(
                boost_for(v.own_pool, v.reserve),
                v.boost,
                "boost {} / {}",
                v.own_pool,
                v.reserve
            );
        }
        for v in r.comeback_owed {
            assert_eq!(
                comeback_owed(v.forfeited_stake, v.new_stake),
                v.owed,
                "owed {} / {}",
                v.forfeited_stake,
                v.new_stake
            );
        }
        for v in r.comeback_refund {
            assert_eq!(
                comeback_refund(v.entitled, v.funded, v.owed),
                v.refund,
                "refund {} {} {}",
                v.entitled,
                v.funded,
                v.owed
            );
        }
        for v in r.comeback_round {
            assert_eq!(
                crate::math::schedule::comeback_rounds(
                    v.first_start,
                    v.cadence,
                    v.join_lock,
                    v.missed_at
                ),
                (v.first, v.last),
                "comeback rounds after {}",
                v.missed_at
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
