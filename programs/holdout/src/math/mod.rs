//! Pure arithmetic: no Anchor types, no accounts, no clock reads.
//!
//! Everything here is replayed against `fixtures/settlement-vectors.json` by
//! failing tests written before the logic (constitution VII, tasks T013–T019).

pub mod schedule;
pub mod settlement;
