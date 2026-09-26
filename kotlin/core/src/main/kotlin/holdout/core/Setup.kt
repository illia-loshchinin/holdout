package holdout.core

/**
 * What `create_league` stores, and so what every cohort of the league copies
 * for life (constitution V). Mirrors `state/league.rs`.
 */
data class LeagueParams(
    val leagueId: Int,
    val durationDays: Int,
    val repsPerDay: Int,
    val exercise: Int,
    /** Chain time, seconds: the start of cohort 0. */
    val firstStart: Long,
    val dayLength: Long,
    val cadence: Long,
    val joinLock: Long,
) {
    companion object {
        const val LEAGUE_7D = 0
        const val LEAGUE_21D = 1

        /** Ids from here up are test leagues, which only a `test-leagues` build accepts (research R11). */
        const val TEST_LEAGUE_MIN = 100

        const val EXERCISE_PUSHUP = 0
        const val DEVNET_REPS_PER_DAY = 25
        const val DEVNET_DAY_LENGTH = HoldoutDay.SECONDS_PER_DAY
        const val DEVNET_CADENCE = 604_800L
        const val DEVNET_JOIN_LOCK = 3_600L

        /** Monday 28 September 2026, 05:00 UTC — the plan's first judged start (quickstart §6). */
        const val FIRST_JUDGED_START = 1_790_571_600L

        /** 1 January 1970 was a Thursday, so Monday's boundary is 4 days into a week (`MONDAY_0500_UTC`). */
        private const val MONDAY_0500_UTC = 4 * HoldoutDay.SECONDS_PER_DAY + HoldoutDay.BOUNDARY_OFFSET_SECONDS

        /** The program's `is_monday_0500_utc`: a real league refuses any other first start. */
        fun isMonday0500Utc(at: Long): Boolean = Math.floorMod(at, DEVNET_CADENCE) == MONDAY_0500_UTC

        /** One of the two judged leagues — the Daily 25 on the devnet clock, as `create_league` demands. */
        fun judged(leagueId: Int, firstStart: Long): LeagueParams {
            val days =
                when (leagueId) {
                    LEAGUE_7D -> 7
                    LEAGUE_21D -> 21
                    else -> throw IllegalArgumentException("the judged leagues are 0 (7-day) and 1 (21-day), not $leagueId")
                }
            return LeagueParams(leagueId, days, DEVNET_REPS_PER_DAY, EXERCISE_PUSHUP, firstStart, DEVNET_DAY_LENGTH, DEVNET_CADENCE, DEVNET_JOIN_LOCK)
        }
    }
}

/** A `LeagueConfig` account as stored: its parameters and the next cohort index to open. */
data class LeagueSnapshot(val params: LeagueParams, val nextIndex: Long) {
    companion object {
        /** 8 + league_id, duration_days, reps_per_day, exercise, four i64, next_index, bump. */
        const val SIZE = 50

        /** `sha256("account:LeagueConfig")[..8]`. */
        private val DISCRIMINATOR = byteArrayOf(0xe0.toByte(), 0xce.toByte(), 0x26, 0x66, 0x62, 0x6a, 0x21, 0x28)

        fun decode(data: ByteArray): LeagueSnapshot {
            require(data.size >= SIZE && data.copyOf(8).contentEquals(DISCRIMINATOR)) { "not a LeagueConfig account" }
            val b = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val params =
                LeagueParams(
                    leagueId = data[8].toInt() and 0xFF,
                    durationDays = data[9].toInt() and 0xFF,
                    repsPerDay = b.getShort(10).toInt() and 0xFFFF,
                    exercise = data[12].toInt() and 0xFF,
                    dayLength = b.getLong(13),
                    firstStart = b.getLong(21),
                    cadence = b.getLong(29),
                    joinLock = b.getLong(37),
                )
            return LeagueSnapshot(params, b.getInt(45).toLong() and 0xFFFF_FFFFL)
        }
    }
}

/**
 * The faucet's limits, fixed by `initialize` (research R13): the top-up a
 * claim brings a wallet to, the day's lamport budget, and the sponsor's float.
 */
data class FaucetParams(
    val solTopUp: ULong = 20_000_000uL,
    val dailyLamportCap: ULong = 720_000_000uL,
    val sponsorFloat: ULong = 50_000_000uL,
)
