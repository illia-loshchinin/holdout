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
    /**
     * How reps are judged (H35): [FORM_TOES] (Hard) or [FORM_KNEES_ALLOWED]
     * (Easy). Fixed for life and copied to every cohort, like the rest.
     */
    val form: Int,
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

        /** From the toes — **Hard**, the 21-day league's form (H35). */
        const val FORM_TOES = 0

        /** Knees allowed — **Easy**, the 7-day league's form (H35). */
        const val FORM_KNEES_ALLOWED = 1

        /** The program refuses any other form with this error (`InvalidForm`). */
        const val INVALID_FORM = 6013

        /** The form a judged league is created with: Easy for 7 days, Hard for 21 (H35). */
        fun judgedForm(leagueId: Int): Int = when (leagueId) {
            LEAGUE_7D -> FORM_KNEES_ALLOWED
            LEAGUE_21D -> FORM_TOES
            else -> throw IllegalArgumentException("the judged leagues are 0 (7-day) and 1 (21-day), not $leagueId")
        }

        /** "Easy" or "Hard", as the app and `crank status` name a form; anything else is named by its number. */
        fun formName(form: Int): String = when (form) {
            FORM_KNEES_ALLOWED -> "Easy"
            FORM_TOES -> "Hard"
            else -> "form $form"
        }
        const val DEVNET_REPS_PER_DAY = 25
        const val DEVNET_DAY_LENGTH = HoldoutDay.SECONDS_PER_DAY
        const val DEVNET_CADENCE = 604_800L
        const val DEVNET_JOIN_LOCK = 3_600L

        /**
         * Monday 5 October 2026, 05:00 UTC — the first judged start, moved a
         * week from 28 September by the product owner on 26 September.
         */
        const val FIRST_JUDGED_START = 1_791_176_400L

        /** 1 January 1970 was a Thursday, so Monday's boundary is 4 days into a week (`MONDAY_0500_UTC`). */
        private const val MONDAY_0500_UTC = 4 * HoldoutDay.SECONDS_PER_DAY + HoldoutDay.BOUNDARY_OFFSET_SECONDS

        /** The program's `is_monday_0500_utc`: a real league refuses any other first start. */
        fun isMonday0500Utc(at: Long): Boolean = Math.floorMod(at, DEVNET_CADENCE) == MONDAY_0500_UTC

        /**
         * One of the two judged leagues — the Daily 25 on the devnet clock, as
         * `create_league` demands — 7-day Easy or 21-day Hard (H35).
         */
        fun judged(leagueId: Int, firstStart: Long): LeagueParams {
            val days =
                when (leagueId) {
                    LEAGUE_7D -> 7
                    LEAGUE_21D -> 21
                    else -> throw IllegalArgumentException("the judged leagues are 0 (7-day) and 1 (21-day), not $leagueId")
                }
            return LeagueParams(leagueId, days, DEVNET_REPS_PER_DAY, EXERCISE_PUSHUP, judgedForm(leagueId), firstStart, DEVNET_DAY_LENGTH, DEVNET_CADENCE, DEVNET_JOIN_LOCK)
        }
    }
}

/**
 * A `LeagueConfig` account as stored: its parameters, the next cohort index to
 * open, and the league's two reserves (H35, FR-037c–e) — what its reserve
 * vault holds, `boost_reserve + comeback_reserve` (INV-1). Boosts draw on the
 * first only, so a pledge in the second is always there for its member.
 */
data class LeagueSnapshot(
    val params: LeagueParams,
    val nextIndex: Long,
    /** Free for boosts: pools nobody won and pledges nobody made good. A finishing cohort's boost is `min(10% × its own pool, this)` (FR-037d). */
    val boostReserve: ULong = 0uL,
    /** Pledged to members: every open comeback's pledge and every refund owed but not yet drawn (FR-037e). */
    val comebackReserve: ULong = 0uL,
    val reserveVaultBump: Int = 0,
) {
    /** [LeagueParams.form]. */
    val form: Int get() = params.form

    companion object {
        /**
         * 8 + league_id, duration_days, reps_per_day, exercise, four i64,
         * next_index, form, boost_reserve, comeback_reserve, reserve_vault_bump, bump.
         */
        const val SIZE = 68

        const val FORM = 49
        const val BOOST_RESERVE = 50
        const val COMEBACK_RESERVE = 58
        const val RESERVE_VAULT_BUMP = 66
        const val BUMP = 67

        /** `sha256("account:LeagueConfig")[..8]`. */
        val DISCRIMINATOR: ByteArray get() = byteArrayOf(0xe0.toByte(), 0xce.toByte(), 0x26, 0x66, 0x62, 0x6a, 0x21, 0x28)

        fun decode(data: ByteArray): LeagueSnapshot {
            require(data.size >= SIZE && data.copyOf(8).contentEquals(DISCRIMINATOR)) { "not a LeagueConfig account" }
            val b = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            val params =
                LeagueParams(
                    leagueId = data[8].toInt() and 0xFF,
                    durationDays = data[9].toInt() and 0xFF,
                    repsPerDay = b.getShort(10).toInt() and 0xFFFF,
                    exercise = data[12].toInt() and 0xFF,
                    form = data[FORM].toInt() and 0xFF,
                    dayLength = b.getLong(13),
                    firstStart = b.getLong(21),
                    cadence = b.getLong(29),
                    joinLock = b.getLong(37),
                )
            return LeagueSnapshot(
                params,
                nextIndex = b.getInt(45).toLong() and 0xFFFF_FFFFL,
                boostReserve = b.getLong(BOOST_RESERVE).toULong(),
                comebackReserve = b.getLong(COMEBACK_RESERVE).toULong(),
                reserveVaultBump = data[RESERVE_VAULT_BUMP].toInt() and 0xFF,
            )
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
