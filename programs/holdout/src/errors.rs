//! Error codes 6000–6060 (contracts/program-instructions §5). Task T026.
//!
//! The codes are part of the contract: the app, the crank and the TypeScript
//! suite all match on them, and `quickstart.md` quotes them. They come in blocks
//! with gaps between, so each block has room to grow without renumbering the
//! next one — and Anchor allows a program exactly **one** `#[error_code]` enum
//! (the IDL build refuses more), so the gaps are explicit discriminants here
//! rather than several enums with offsets.
//!
//! A new error is added at the end of its block, never in the middle of one, and
//! `every_code_lands_where_the_contract_says` fails if a number moves.

use anchor_lang::prelude::*;

#[error_code]
pub enum HoldoutError {
    // ---- 6000–6003 · wrong privileged signer ----------------------------
    #[msg("Only the admin may do this")]
    NotAdmin = 0,
    #[msg("Only the operator may publish a chip set")]
    NotOperator = 1,
    #[msg("A faucet claim is paid by the sponsor or by the recipient itself")]
    NotSponsorOrRecipient = 2,
    #[msg("Only the program's upgrade authority may initialize it")]
    NotUpgradeAuthority = 3,

    // ---- 6010–6012 · league and chip-set setup --------------------------
    #[msg("A devnet league must use the default timing; test leagues need a test-leagues build")]
    InvalidLeagueTiming = 10,
    #[msg("Chip amounts must be strictly increasing and all above zero")]
    InvalidChipSet = 11,
    #[msg("No cohort can open before the first chip set is published")]
    ChipSetNotPublished = 12,

    // ---- 6020–6026 · opening, joining and the lobby ---------------------
    #[msg("This cohort is not due yet, or its index is wrong")]
    CohortNotDue = 20,
    #[msg("Joining and leaving closed an hour before the start")]
    JoiningClosed = 21,
    #[msg("A tier is 0, 1, 2 or 3")]
    InvalidTier = 22,
    #[msg("A 21-day Holdout needs a cleared 7-day one while the ladder gate is on")]
    LadderGateClosed = 23,
    #[msg("The genesis gate is on and this wallet did not pass it")]
    GenesisGateClosed = 24,
    #[msg("This wallet already holds a lobby place")]
    LobbyPlaceTaken = 25,
    #[msg("This cohort starts before the running one ends")]
    ObligationsOverlap = 26,

    // ---- 6030–6033 · the daily obligation -------------------------------
    #[msg("Completed days are only accepted while the cohort is running")]
    CohortNotRunning = 30,
    #[msg("That is not today's day index, or not the member's next one")]
    WrongDay = 31,
    #[msg("This day's deadline has passed")]
    DeadlinePassed = 32,
    #[msg("This day is already completed")]
    DayAlreadyCompleted = 33,

    // ---- 6040–6045 · settlement and the claim ---------------------------
    #[msg("This cohort is not settling")]
    NotSettling = 40,
    #[msg("This cohort is already settled")]
    AlreadySettled = 41,
    #[msg("Every member must be counted before settlement can finalize")]
    CountIncomplete = 42,
    #[msg("Only a finisher can claim")]
    NotClaimable = 43,
    #[msg("This payout is already claimed")]
    AlreadyClaimed = 44,
    #[msg("The previous cohort of this league must settle first")]
    PreviousCohortUnsettled = 45,

    // ---- 6050–6054 · the faucet, and the exercise guard that shares it ---
    #[msg("This wallet has already used the faucet today")]
    FaucetAlreadyClaimedToday = 50,
    #[msg("The faucet has spent today's allowance")]
    FaucetCapReached = 51,
    #[msg("The faucet vault cannot cover this")]
    FaucetEmpty = 52,
    #[msg("Only push-ups are supported in this build")]
    ExerciseNotSupported = 53,
    #[msg("The sponsor still holds at least half its float")]
    SponsorFloatSufficient = 54,

    // ---- 6060 · an account passed where it does not belong --------------
    #[msg("This account does not match the cohort, wallet or league it was passed for")]
    AccountMismatch = 60,
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The numbers are the contract, not an implementation detail: a client that
    /// matches on 6045 must keep meaning `PreviousCohortUnsettled`. Anchor's
    /// generated `From<HoldoutError> for u32` already adds `ERROR_CODE_OFFSET`,
    /// so this is the number an `AnchorError` carries as `error_code_number` and
    /// the one the built IDL lists.
    fn code(err: HoldoutError) -> u32 {
        u32::from(err)
    }

    #[test]
    fn every_code_lands_where_the_contract_says() {
        assert_eq!(
            anchor_lang::error::ERROR_CODE_OFFSET,
            6_000,
            "the base Anchor counts from"
        );

        assert_eq!(code(HoldoutError::NotAdmin), 6000);
        assert_eq!(code(HoldoutError::NotOperator), 6001);
        assert_eq!(code(HoldoutError::NotSponsorOrRecipient), 6002);
        assert_eq!(code(HoldoutError::NotUpgradeAuthority), 6003);

        assert_eq!(code(HoldoutError::InvalidLeagueTiming), 6010);
        assert_eq!(code(HoldoutError::InvalidChipSet), 6011);
        assert_eq!(code(HoldoutError::ChipSetNotPublished), 6012);

        assert_eq!(code(HoldoutError::CohortNotDue), 6020);
        assert_eq!(code(HoldoutError::JoiningClosed), 6021);
        assert_eq!(code(HoldoutError::InvalidTier), 6022);
        assert_eq!(code(HoldoutError::LadderGateClosed), 6023);
        assert_eq!(code(HoldoutError::GenesisGateClosed), 6024);
        assert_eq!(code(HoldoutError::LobbyPlaceTaken), 6025);
        assert_eq!(code(HoldoutError::ObligationsOverlap), 6026);

        assert_eq!(code(HoldoutError::CohortNotRunning), 6030);
        assert_eq!(code(HoldoutError::WrongDay), 6031);
        assert_eq!(code(HoldoutError::DeadlinePassed), 6032);
        assert_eq!(code(HoldoutError::DayAlreadyCompleted), 6033);

        assert_eq!(code(HoldoutError::NotSettling), 6040);
        assert_eq!(code(HoldoutError::AlreadySettled), 6041);
        assert_eq!(code(HoldoutError::CountIncomplete), 6042);
        assert_eq!(code(HoldoutError::NotClaimable), 6043);
        assert_eq!(code(HoldoutError::AlreadyClaimed), 6044);
        assert_eq!(code(HoldoutError::PreviousCohortUnsettled), 6045);

        assert_eq!(code(HoldoutError::FaucetAlreadyClaimedToday), 6050);
        assert_eq!(code(HoldoutError::FaucetCapReached), 6051);
        assert_eq!(code(HoldoutError::FaucetEmpty), 6052);
        assert_eq!(code(HoldoutError::ExerciseNotSupported), 6053);
        assert_eq!(code(HoldoutError::SponsorFloatSufficient), 6054);

        assert_eq!(code(HoldoutError::AccountMismatch), 6060);
    }
}
