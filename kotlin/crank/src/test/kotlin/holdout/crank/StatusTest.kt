package holdout.crank

import com.solana.publickey.SolanaPublicKey
import holdout.core.CohortSnapshot
import holdout.core.ConfigSnapshot
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * T036 — what `crank status` reports: every cohort, the faucet vault, the
 * sponsor against its float and the day's lamport spend (contracts/crank-cli.md,
 * the figures T110 monitors the live period on).
 */
class StatusTest {

    private fun key(n: Int) = SolanaPublicKey(ByteArray(32) { n.toByte() })

    /** Monday 28 September 2026, 05:00:00 UTC. */
    private val firstStart = 1_790_571_600L

    /** Global day of [firstStart]: floor((t − 18,000) ÷ 86,400). */
    private val today = Math.floorDiv(firstStart - 18_000, 86_400L)

    private val config =
        ConfigSnapshot(
            admin = key(0x11),
            operator = key(0x22),
            sponsor = key(0x33),
            skrMint = key(0x44),
            feeDestination = key(0x55),
            ladderGateEnabled = false,
            genesisGateEnabled = false,
            chipAmounts = listOf(460_000_000uL, 1_170_000_000uL, 2_340_000_000uL, 4_690_000_000uL),
            chipVersion = 1,
            chipPublishedAt = firstStart - 86_400,
            maxTopTier = 4_690_000_000uL,
            solTopUp = 20_000_000uL,
            sponsorFloat = 50_000_000uL,
            dailyLamportCap = 720_000_000uL,
            lamportDay = today,
            lamportsToday = 123_456uL,
        )

    private fun cohort(index: Long, settled: Boolean = false) = CohortSnapshot(
        leagueId = 0,
        index = index,
        start = firstStart + index * 604_800,
        joinCutoff = firstStart + index * 604_800 - 3_600,
        durationDays = 7,
        repsPerDay = 25,
        dayLength = 86_400,
        exercise = 0,
        chips = config.chipAmounts,
        memberCount = 20,
        heldStake = 1_000uL,
        pool = 400uL,
        forfeitsRecorded = 5,
        counted = 3,
        settled = settled,
    )

    private fun report(
        now: Long = firstStart + 3_600,
        config: ConfigSnapshot? = this.config,
        sponsorLamports: Long? = 50_000_000,
        cohorts: List<Pair<SolanaPublicKey, CohortSnapshot>> = listOf(key(2) to cohort(1), key(1) to cohort(0)),
    ) = Status.report(
        chainTime = now,
        slot = 42,
        configAddress = key(0xc0),
        config = config,
        faucetVault = key(0xfa),
        faucetVaultLamports = 25_000_000_000,
        sponsorLamports = sponsorLamports,
        cohorts = cohorts,
    )

    @Test
    fun `every cohort is listed with its status from chain time, oldest first`() {
        val cohorts = report().getValue("cohorts").jsonArray.map { it.jsonObject }
        assertEquals(listOf(0L, 1L), cohorts.map { it.getValue("index").jsonPrimitive.long })
        assertEquals(listOf("Running", "Open"), cohorts.map { it.getValue("status").jsonPrimitive.content })
        val first = cohorts[0]
        assertEquals(key(1).base58(), first.getValue("address").jsonPrimitive.content)
        assertEquals(20, first.getValue("memberCount").jsonPrimitive.long)
        assertEquals(5, first.getValue("forfeitsRecorded").jsonPrimitive.long)
        assertEquals(3, first.getValue("counted").jsonPrimitive.long)
        assertEquals("false", first.getValue("settled").jsonPrimitive.content)
        assertEquals(0, first.getValue("carryOverHeld").jsonPrimitive.long)
    }

    @Test
    fun `the faucet section is what the live period is monitored on`() {
        val faucet = report().getValue("faucet").jsonObject
        assertEquals(key(0xfa).base58(), faucet.getValue("vault").jsonPrimitive.content)
        assertEquals(25_000_000_000, faucet.getValue("vaultLamports").jsonPrimitive.long)
        assertEquals(key(0x33).base58(), faucet.getValue("sponsor").jsonPrimitive.content)
        assertEquals(50_000_000, faucet.getValue("sponsorLamports").jsonPrimitive.long)
        assertEquals(50_000_000, faucet.getValue("sponsorFloat").jsonPrimitive.long)
        assertEquals("false", faucet.getValue("sponsorRefillDue").jsonPrimitive.content)
        assertEquals("123456 / 720000000", faucet.getValue("lamportSpend").jsonPrimitive.content)
    }

    @Test
    fun `a sponsor below half its float is flagged for a refill`() {
        // `Faucet::sponsor_refill`: a refill is allowed only below half the float.
        val atHalf = report(sponsorLamports = 25_000_000).getValue("faucet").jsonObject
        assertEquals("false", atHalf.getValue("sponsorRefillDue").jsonPrimitive.content)
        val below = report(sponsorLamports = 24_999_999).getValue("faucet").jsonObject
        assertEquals("true", below.getValue("sponsorRefillDue").jsonPrimitive.content)
    }

    @Test
    fun `yesterday's spend is not today's`() {
        val tomorrow = firstStart + 86_400
        val faucet = report(now = tomorrow).getValue("faucet").jsonObject
        assertEquals("0 / 720000000", faucet.getValue("lamportSpend").jsonPrimitive.content)
    }

    @Test
    fun `an uninitialised program reports no config rather than failing`() {
        val r = report(config = null, sponsorLamports = null, cohorts = emptyList())
        assertEquals(JsonNull, r.getValue("config"))
        assertEquals(JsonNull, r.getValue("faucet").jsonObject.getValue("sponsor"))
        assertEquals(0, r.getValue("cohorts").jsonArray.size)
    }
}
