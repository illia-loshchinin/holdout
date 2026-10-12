package holdout.app

import holdout.app.ui.Format
import holdout.core.CohortSnapshot
import holdout.core.LeagueParams
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The member-facing copy `Format` owns: the two levels, their taglines and rule
 * sentences, the header labels, the fee sentence, the boost and comeback lines.
 * The words are the design book's and the rulings' (G2, G3, G5, G6), asserted
 * exactly, because every screen reads them from here.
 */
class FormatCopyTest {
    private val spark = CohortSnapshot(0, 0, 0, 0, 7, 25, 86_400, 0, listOf(1uL, 2uL, 3uL, 4uL), form = LeagueParams.FORM_KNEES_ALLOWED)
    private val habit = spark.copy(durationDays = 21, form = LeagueParams.FORM_TOES)

    @Test
    fun `a 7-day knees Holdout is a Spark and a 21-day toes Holdout is a Habit`() {
        assertEquals("Spark · 7-day", Format.level(spark))
        assertEquals("Habit · 21-day", Format.level(habit))
        assertEquals("Spark · 7-day Holdout", Format.holdoutTitle(spark))
        assertEquals("Habit · 21-day Holdout", Format.holdoutTitle(habit))
        assertEquals("Spark", Format.levelName(LeagueParams.FORM_KNEES_ALLOWED))
        assertEquals("Habit", Format.levelName(LeagueParams.FORM_TOES))
    }

    @Test
    fun `no member sees Easy or Hard`() {
        listOf(Format.level(spark), Format.level(habit), Format.holdoutTitle(spark), Format.holdoutTitle(habit)).forEach {
            assertFalse("Easy" in it || "Hard" in it, it)
        }
    }

    @Test
    fun `each level has its tagline`() {
        assertEquals("Light it up · knees allowed", Format.levelTagline(spark))
        assertEquals("Make it stick · toes only", Format.levelTagline(habit))
        assertNull(Format.levelTagline(7), "a form this build does not know")
    }

    @Test
    fun `each level has one rule sentence, word for word`() {
        assertEquals("At 45° only. Aim for full push-ups from your toes; push-ups from the knees still count.", Format.levelRule(spark))
        assertEquals(
            "At 45° only: phone on the floor ahead of your shoulder. Full push-ups from your toes — push-ups from the knees don't count.",
            Format.levelRule(habit),
        )
        assertNull(Format.levelRule(7), "a form this build does not know")
    }

    @Test
    fun `a Spark never reads the Habit sentence and a Habit never reads the Spark one`() {
        assertFalse("don't count" in Format.levelRule(spark).orEmpty())
        assertFalse("still count" in Format.levelRule(habit).orEmpty())
    }

    @Test
    fun `header labels name the level and length with a state, never a calendar date`() {
        assertEquals("SPARK · 7-DAY · DAY 3 OF 7", Format.headerLabel(spark, 3))
        assertEquals("HABIT · 21-DAY · DAY 12 OF 21", Format.headerLabel(habit, 12))
        assertEquals("SPARK · 7-DAY · STARTS IN 2D 4H", Format.lobbyHeaderLabel(spark, 2 * 86_400L + 4 * 3_600L))
        assertEquals("HABIT · 21-DAY · STARTS IN 4H 06M", Format.lobbyHeaderLabel(habit, 4 * 3_600L + 6 * 60L))
        assertEquals("SPARK · 7-DAY · FINISHED", Format.finishedHeaderLabel(spark))
    }

    @Test
    fun `the fee sentence is the one the stake sheet shows, with the Holdout's own level`() {
        assertEquals(
            "Miss a day and you lose your stake: 70 % to the prize pool, 10 % set aside for your comeback, " +
                "20 % to the project. If nobody finishes, the pool boosts later Spark · 7-day prizes.",
            Format.lostStakeSplit(spark),
        )
        assertEquals(
            "Miss a day and you lose your stake: 70 % to the prize pool, 10 % set aside for your comeback, " +
                "20 % to the project. If nobody finishes, the pool boosts later Habit · 21-day prizes.",
            Format.lostStakeSplit(habit),
        )
    }

    @Test
    fun `the fee sentence takes the noun it names the boosts after`() {
        assertEquals(
            "Miss a day and you lose your stake: 70 % to the prize pool, 10 % set aside for your comeback, " +
                "20 % to the project. If nobody finishes, the pool boosts later Hackathon · 3-day prizes.",
            Format.lostStakeSplit(spark, name = "Hackathon · 3-day"),
        )
    }

    @Test
    fun `SOL shows three decimals from lamports`() {
        assertEquals("0.012 SOL", Format.sol(12_000_000L))
        assertEquals("0.000 SOL", Format.sol(0L))
        assertEquals("1.500 SOL", Format.sol(1_500_000_000L))
        assertEquals("0.001 SOL", Format.sol(1_000_000L))
    }

    @Test
    fun `the forfeit variant opens on where the stake goes and names the Holdout's pool`() {
        assertEquals(
            "It goes 70 % to this Holdout's prize pool, 10 % set aside for your comeback, " +
                "20 % to the project. If nobody finishes, the pool boosts later Habit · 21-day prizes.",
            Format.lostStakeSplitForfeit(habit),
        )
    }

    @Test
    fun `a percent sign never starts a line on its own`() {
        listOf(Format.lostStakeSplit(spark), Format.lostStakeSplitForfeit(habit)).forEach { sentence ->
            assertFalse(" %" in sentence, "a breakable space before %")
            assertEquals(3, sentence.split(" %").size - 1)
        }
    }

    @Test
    fun `the boost is an up-to line on an offer and an about line on a running Holdout`() {
        assertEquals("Prize boost: up to 480 SKR", Format.boostLine(480_000_000uL))
        assertEquals("+ about 1,200 SKR boost if anyone finishes", Format.runningBoostLine(1_200_000_000uL))
    }

    @Test
    fun `the comeback lines are the design book's`() {
        assertEquals(
            "Get back 250 SKR of your lost stake: finish the Spark · 7-day starting in 2d 4h.",
            Format.comebackToday(250_000_000uL, spark, 2 * 86_400L + 4 * 3_600L),
        )
        assertEquals(
            "Get back 250 SKR of your lost stake: finish the Spark · 7-day that started today.",
            Format.comebackToday(250_000_000uL, spark, null),
        )
        assertEquals(
            "Finish the next Habit · 21-day and 250 SKR comes back.",
            Format.comebackForfeit(250_000_000uL, habit),
        )
        assertEquals("Win back 250 SKR", Format.winBack(250_000_000uL))
        assertEquals("part of your last lost stake, back", Format.COMEBACK_CLAIM_NOTE)
    }

    @Test
    fun `amounts group their thousands and show at most two decimals, rounded down`() {
        assertEquals("1,560 SKR", Format.skr(1_560_000_000uL))
        assertEquals("12.5 SKR", Format.skr(12_500_000uL))
        assertEquals("1,234,567.5 SKR", Format.skr(1_234_567_500_000uL))
        assertEquals("999 SKR", Format.skr(999_000_000uL))
        assertEquals("0 SKR", Format.skr(0uL))
        assertEquals("428.57 SKR", Format.skr(428_571_428uL))
        assertEquals("223.75 SKR", Format.skr(223_750_000uL))
        assertEquals("0.99 SKR", Format.skr(999_999uL))
        assertEquals("1.1 SKR", Format.skr(1_109_999uL))
        assertEquals("1,000 SKR", Format.skr(1_000_004_999uL))
        assertEquals("0 SKR", Format.skr(1uL))
    }

    @Test
    fun `the wallet wait is said one way`() {
        assertEquals("Waiting for your wallet…", Format.WAITING_FOR_WALLET)
    }

    @Test
    fun `no member-facing line says cohort, league, challenge or earn`() {
        val lines = listOf(
            Format.level(spark),
            Format.holdoutTitle(habit),
            Format.lostStakeSplit(spark),
            Format.lostStakeSplitForfeit(habit),
            Format.boostLine(1uL),
            Format.runningBoostLine(1uL),
            Format.comebackToday(1uL, spark, 60),
            Format.comebackForfeit(1uL, habit),
            Format.headerLabel(spark, 1),
            Format.lobbyHeaderLabel(habit, 60),
            Format.levelRule(spark).orEmpty(),
            Format.levelRule(habit).orEmpty(),
            Format.levelTagline(spark).orEmpty(),
            Format.levelTagline(habit).orEmpty(),
        ).map { it.lowercase() }
        listOf("cohort", "league", "challenge", "earn").forEach { banned ->
            assertTrue(lines.none { banned in it }, banned)
        }
    }
}
