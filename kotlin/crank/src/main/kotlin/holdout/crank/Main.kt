package holdout.crank

/**
 * The crank: opens due cohorts, records forfeits, settles, drives scripted
 * members and publishes chip sets (contracts/crank-cli.md).
 *
 * Every step is permissionless and idempotent — a stranger running it produces
 * the same state as the Scheduler (FR-030a, constitution VIII).
 */
fun main(args: Array<String>) {
    println("holdout crank — subcommands land with tasks T036, T062, T071, T087, T088, T091")
    println("args: ${args.joinToString(" ")}")
}
