//! The program's instructions (contracts/program-instructions.md §1–§3).
//!
//! Each one lands with its own task and its own scenario, and nothing is
//! implemented before the scenario that describes it exists (constitution VII).
//! Setup first — `initialize` T027, `create_league` and `set_flags` T028 — then
//! the faucet, the lobby, the day, the forfeit and settlement.

pub mod create_league;
pub mod initialize;
pub mod join_cohort;
pub mod leave_cohort;
pub mod open_cohort;
pub mod publish_chip_set;
pub mod set_flags;
pub mod submit_day;

// `#[program]` names the `Accounts` structs and the client/CPI modules Anchor
// generates beside them, so the glob is what the macro needs. Each module's
// `handler` is `pub(crate)` rather than `pub`, so three of them do not collide
// here — lib.rs calls each by its full path.
pub use create_league::*;
pub use initialize::*;
pub use join_cohort::*;
pub use leave_cohort::*;
pub use open_cohort::*;
pub use publish_chip_set::*;
pub use set_flags::*;
pub use submit_day::*;
