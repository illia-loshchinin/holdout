//! Holdout — push-up commitment cohorts on Solana devnet.
//!
//! The contract is the only authority on funds (constitution I): no off-chain
//! key can move a stake, a pool or a carry-over. Breach is derived from chain
//! time (II), never declared by automation.
//!
//! Members read "the Daily 25" and "a Holdout"; this code stores a `Cohort` in
//! a league (spec §1a).

use anchor_lang::prelude::*;

declare_id!("Ho1dout111111111111111111111111111111111111");

pub mod errors;
pub mod events;
pub mod guards;
pub mod math;
pub mod state;

#[program]
pub mod holdout {
    // Instructions land here as their tasks complete (tasks.md T027–T080).
    // Nothing is implemented before its failing test exists (constitution VII).
}
