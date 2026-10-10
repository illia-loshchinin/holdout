/**
 * The program's error codes, by name (contracts/program-instructions §5). Task T016.
 *
 * A scenario asserts `expectProgramError(result, 'PreviousCohortUnsettled')`
 * rather than a bare number, so a failed assertion reads as a sentence. The
 * numbers are pinned two ways: `errors.rs` has a test that every variant lands
 * where the contract says, and `harness.spec.ts` compares this table with the
 * built IDL — so a code that moves fails a build on both sides.
 */

export const ERROR_CODES = {
  // 6000–6003 · wrong privileged signer
  NotAdmin: 6000,
  NotOperator: 6001,
  NotSponsorOrRecipient: 6002,
  NotUpgradeAuthority: 6003,
  // 6010–6013 · league and chip-set setup
  InvalidLeagueTiming: 6010,
  InvalidChipSet: 6011,
  ChipSetNotPublished: 6012,
  ChipSetExpired: 6014,
  /** A league's form is 0 (from the toes) or 1 (knees allowed) — H35. */
  InvalidForm: 6013,
  // 6020–6026 · opening, joining and the lobby
  CohortNotDue: 6020,
  JoiningClosed: 6021,
  InvalidTier: 6022,
  LadderGateClosed: 6023,
  GenesisGateClosed: 6024,
  LobbyPlaceTaken: 6025,
  ObligationsOverlap: 6026,
  // 6030–6033 · the daily obligation
  CohortNotRunning: 6030,
  WrongDay: 6031,
  DeadlinePassed: 6032,
  DayAlreadyCompleted: 6033,
  // 6040–6045 · settlement and the claim
  NotSettling: 6040,
  AlreadySettled: 6041,
  CountIncomplete: 6042,
  NotClaimable: 6043,
  AlreadyClaimed: 6044,
  PreviousCohortUnsettled: 6045,
  // 6050–6054 · the faucet, and the exercise guard that shares it
  FaucetAlreadyClaimedToday: 6050,
  FaucetCapReached: 6051,
  FaucetEmpty: 6052,
  ExerciseNotSupported: 6053,
  SponsorFloatSufficient: 6054,
  // 6060 · an account passed where it does not belong
  AccountMismatch: 6060,
} as const;

export type ErrorName = keyof typeof ERROR_CODES;

/** Anchor's own framework errors a scenario may legitimately meet. */
export const ANCHOR_ERRORS = {
  InstructionFallbackNotFound: 101,
  ConstraintSeeds: 2006,
  AccountNotInitialized: 3012,
} as const;

/**
 * The custom error number a failed transaction carries, from its logs: Anchor
 * prints `Error Number: N`, and any program failure ends `custom program error:
 * 0xN`. `null` when the failure was not a program error at all (for example an
 * unfunded fee payer).
 */
export const errorCodeFromLogs = (logs: readonly string[]): number | null => {
  for (const line of [...logs].reverse()) {
    const anchor = /Error Number: (\d+)/.exec(line);
    if (anchor?.[1]) return Number(anchor[1]);
  }
  for (const line of [...logs].reverse()) {
    const raw = /custom program error: 0x([0-9a-fA-F]+)/.exec(line);
    if (raw?.[1]) return parseInt(raw[1], 16);
  }
  return null;
};
