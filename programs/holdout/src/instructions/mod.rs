//! Instruction handlers (contracts/program-instructions §1–§3). Each lands with
//! its own task, after the scenario that describes it (constitution VII):
//! `initialize` T027, `create_league` and `set_flags` T028, `open_cohort` T055,
//! `publish_chip_set` T058.
//!
//! Account order in every `Accounts` struct is the one `tests/helpers/
//! instructions.ts` fixes: signers first, then the state the instruction reads
//! or writes, then programs. Anchor reads accounts positionally, so a reordered
//! field is a changed contract.

pub mod create_league;
pub mod initialize;
pub mod open_cohort;
pub mod publish_chip_set;
pub mod set_flags;

// Anchor's `#[program]` expansion looks for each context's generated client
// modules at the crate root, so the structs are re-exported from here and
// `lib.rs` re-exports this module. Handlers carry distinct names for the same
// reason: a glob of five `handler`s would be ambiguous.
pub use create_league::*;
pub use initialize::*;
pub use open_cohort::*;
pub use publish_chip_set::*;
pub use set_flags::*;
