//! Error codes 6000–6060 (contracts/program-instructions §5). Task T026.
//!
//! The codes are part of the contract: the app, the crank and the TypeScript
//! suite all match on them, and `quickstart.md` quotes them. Anchor numbers
//! variants sequentially from 6000, so each block declares its own enum with an
//! `offset` to keep the documented gaps — a new error goes at the end of its
//! block and never in the middle of one.

use anchor_lang::prelude::*;

/// 6000–6003 · wrong privileged signer.
#[error_code(offset = 0)]
pub enum PrivilegedError {
    #[msg("Only the admin may do this")]
    NotAdmin,
    #[msg("Only the operator may publish a chip set")]
    NotOperator,
    #[msg("A faucet claim is paid by the sponsor or by the recipient itself")]
    NotSponsorOrRecipient,
    #[msg("Only the program's upgrade authority may initialize it")]
    NotUpgradeAuthority,
}

/// 6010–6012 · league and chip-set setup.
#[error_code(offset = 10)]
pub enum SetupError {
    #[msg("A devnet league must use the default timing; test leagues need a test-leagues build")]
    InvalidLeagueTiming,
    #[msg("Chip amounts must be strictly increasing and all above zero")]
    InvalidChipSet,
    #[msg("No cohort can open before the first chip set is published")]
    ChipSetNotPublished,
}

/// 6020–6026 · opening, joining and the lobby.
#[error_code(offset = 20)]
pub enum LobbyError {
    #[msg("This cohort is not due yet, or its index is wrong")]
    CohortNotDue,
    #[msg("Joining and leaving closed an hour before the start")]
    JoiningClosed,
    #[msg("A tier is 0, 1, 2 or 3")]
    InvalidTier,
    #[msg("A 21-day Holdout needs a cleared 7-day one while the ladder gate is on")]
    LadderGateClosed,
    #[msg("The genesis gate is on and this wallet did not pass it")]
    GenesisGateClosed,
    #[msg("This wallet already holds a lobby place")]
    LobbyPlaceTaken,
    #[msg("This cohort starts before the running one ends")]
    ObligationsOverlap,
}

/// 6030–6033 · the daily obligation.
#[error_code(offset = 30)]
pub enum DayError {
    #[msg("Completed days are only accepted while the cohort is running")]
    CohortNotRunning,
    #[msg("That is not today's day index, or not the member's next one")]
    WrongDay,
    #[msg("This day's deadline has passed")]
    DeadlinePassed,
    #[msg("This day is already completed")]
    DayAlreadyCompleted,
}

/// 6040–6047 · settlement and the claim.
#[error_code(offset = 40)]
pub enum SettlementError {
    #[msg("This cohort is not settling")]
    NotSettling,
    #[msg("This cohort is already settled")]
    AlreadySettled,
    #[msg("Every member must be counted before settlement can finalize")]
    CountIncomplete,
    #[msg("Only a finisher can claim")]
    NotClaimable,
    #[msg("This payout is already claimed")]
    AlreadyClaimed,
    #[msg("The previous cohort of this league must settle first")]
    PreviousCohortUnsettled,
    #[msg("A no-finisher settlement needs the league's latest cohort and its vault")]
    CarryOverTargetRequired,
    #[msg("The league has no cohort open for joining: open the next one, then settle")]
    CarryOverTargetNotOpen,
}

/// 6050–6054 · the faucet, and the exercise guard that shares this block.
#[error_code(offset = 50)]
pub enum FaucetError {
    #[msg("This wallet has already used the faucet today")]
    FaucetAlreadyClaimedToday,
    #[msg("The faucet has spent today's allowance")]
    FaucetCapReached,
    #[msg("The faucet vault cannot cover this")]
    FaucetEmpty,
    #[msg("Only push-ups are supported in this build")]
    ExerciseNotSupported,
    #[msg("The sponsor still holds at least half its float")]
    SponsorFloatSufficient,
}

/// 6060 · an account that does not belong where it was passed.
#[error_code(offset = 60)]
pub enum AccountError {
    #[msg("This account does not match the cohort, wallet or league it was passed for")]
    AccountMismatch,
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The numbers are the contract, not an implementation detail: a client that
    /// matches on 6045 must keep meaning `PreviousCohortUnsettled`. Anchor's
    /// `From<_> for u32` yields a variant's offset inside its own enum; the
    /// number a client sees is that plus `ERROR_CODE_OFFSET`, which is what an
    /// `AnchorError` carries as `error_code_number`.
    fn code<E: Into<u32>>(err: E) -> u32 {
        anchor_lang::error::ERROR_CODE_OFFSET + err.into()
    }

    #[test]
    fn every_code_lands_where_the_contract_says() {
        assert_eq!(anchor_lang::error::ERROR_CODE_OFFSET, 6_000, "the base Anchor counts from");

        assert_eq!(code(PrivilegedError::NotAdmin), 6000);
        assert_eq!(code(PrivilegedError::NotOperator), 6001);
        assert_eq!(code(PrivilegedError::NotSponsorOrRecipient), 6002);
        assert_eq!(code(PrivilegedError::NotUpgradeAuthority), 6003);

        assert_eq!(code(SetupError::InvalidLeagueTiming), 6010);
        assert_eq!(code(SetupError::InvalidChipSet), 6011);
        assert_eq!(code(SetupError::ChipSetNotPublished), 6012);

        assert_eq!(code(LobbyError::CohortNotDue), 6020);
        assert_eq!(code(LobbyError::JoiningClosed), 6021);
        assert_eq!(code(LobbyError::InvalidTier), 6022);
        assert_eq!(code(LobbyError::LadderGateClosed), 6023);
        assert_eq!(code(LobbyError::GenesisGateClosed), 6024);
        assert_eq!(code(LobbyError::LobbyPlaceTaken), 6025);
        assert_eq!(code(LobbyError::ObligationsOverlap), 6026);

        assert_eq!(code(DayError::CohortNotRunning), 6030);
        assert_eq!(code(DayError::WrongDay), 6031);
        assert_eq!(code(DayError::DeadlinePassed), 6032);
        assert_eq!(code(DayError::DayAlreadyCompleted), 6033);

        assert_eq!(code(SettlementError::NotSettling), 6040);
        assert_eq!(code(SettlementError::AlreadySettled), 6041);
        assert_eq!(code(SettlementError::CountIncomplete), 6042);
        assert_eq!(code(SettlementError::NotClaimable), 6043);
        assert_eq!(code(SettlementError::AlreadyClaimed), 6044);
        assert_eq!(code(SettlementError::PreviousCohortUnsettled), 6045);
        assert_eq!(code(SettlementError::CarryOverTargetRequired), 6046);
        assert_eq!(code(SettlementError::CarryOverTargetNotOpen), 6047);

        assert_eq!(code(FaucetError::FaucetAlreadyClaimedToday), 6050);
        assert_eq!(code(FaucetError::FaucetCapReached), 6051);
        assert_eq!(code(FaucetError::FaucetEmpty), 6052);
        assert_eq!(code(FaucetError::ExerciseNotSupported), 6053);
        assert_eq!(code(FaucetError::SponsorFloatSufficient), 6054);

        assert_eq!(code(AccountError::AccountMismatch), 6060);
    }
}
