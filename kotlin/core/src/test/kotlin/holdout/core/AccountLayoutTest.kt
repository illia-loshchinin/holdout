package holdout.core

import com.solana.publickey.SolanaPublicKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The read views [CohortSnapshot], [MembershipSnapshot] and [ConfigSnapshot]
 * against the golden bytes the program's `state/layout_tests.rs` produces from
 * Anchor's own serializer (T016). Every expected value below is the one that
 * file serializes, field for field.
 */
class AccountLayoutTest {

    private val golden = Json.parseToJsonElement(RepoFiles.read("tests/helpers/layout-vectors.json")).jsonObject

    private fun bytes(name: String) = RepoFiles.hex(golden.getValue(name).jsonPrimitive.content)

    private fun key(byte: Int) = SolanaPublicKey(ByteArray(32) { byte.toByte() })

    @Test
    fun `account sizes are the ones the program allocates`() {
        assertEquals(236, bytes("Cohort").size)
        assertEquals(AccountLayout.Cohort.SIZE, bytes("Cohort").size)
        assertEquals(93, bytes("Membership").size)
        assertEquals(AccountLayout.Membership.SIZE, bytes("Membership").size)
        assertEquals(263, bytes("Config").size)
        assertEquals(AccountLayout.Config.SIZE, bytes("Config").size)
    }

    @Test
    fun `discriminators are Anchor's sha256 prefixes`() {
        assertContentEquals(bytes("Cohort").copyOf(8), AccountLayout.Cohort.DISCRIMINATOR)
        assertContentEquals(bytes("Membership").copyOf(8), AccountLayout.Membership.DISCRIMINATOR)
        assertContentEquals(bytes("Config").copyOf(8), AccountLayout.Config.DISCRIMINATOR)
    }

    @Test
    fun `a cohort decodes field for field`() {
        val c = CohortSnapshot.decode(bytes("Cohort"))
        assertEquals(1, c.leagueId)
        assertEquals(2, c.index)
        assertEquals(1_791_176_400, c.start)
        assertEquals(1_791_172_800, c.joinCutoff)
        assertEquals(21, c.durationDays)
        assertEquals(25, c.repsPerDay)
        assertEquals(86_400, c.dayLength)
        assertEquals(0, c.exercise)
        assertEquals(listOf(10_000_000uL, 25_000_000uL, 50_000_000uL, 100_000_000uL), c.chips)
        assertEquals(12, c.memberCount)
        assertEquals(900uL, c.heldStake)
        assertEquals(680uL, c.pool)
        assertEquals(600uL, c.carryOverReceived)
        assertEquals(3, c.forfeitsRecorded)
        assertEquals(9, c.counted)
        assertEquals(4, c.finisherCount)
        assertEquals(listOf(1L, 0L, 2L, 1L), c.finishersPerTier)
        assertEquals(400uL, c.totalFinisherStake)
        assertTrue(c.settled)
        assertEquals(listOf(17uL, 0uL, 42uL, 85uL), c.sharePerTier)
        assertEquals(2uL, c.roundingRemainder)
        assertEquals(500uL, c.noFinisherFee)
        assertEquals(501uL, c.carryOverSent)
        assertEquals(7uL, c.carryOverHeld)
        assertEquals(key(0x66), c.carryOverTarget)
    }

    @Test
    fun `a membership decodes from the whole account and from the roster slice alike`() {
        val account = bytes("Membership")
        val expected =
            MembershipSnapshot(
                wallet = key(0x88),
                tier = 2,
                stake = 50_000_000uL,
                joinedAt = 1_791_000_000,
                daysCompleted = 13,
                state = MembershipSnapshot.State.Finished,
            )
        assertEquals(expected, MembershipSnapshot.decode(account))

        // What `getProgramAccounts` returns with the roster `dataSlice` (R8).
        val slice =
            account.copyOfRange(
                AccountLayout.Membership.ROSTER_SLICE_OFFSET,
                AccountLayout.Membership.ROSTER_SLICE_OFFSET + AccountLayout.Membership.ROSTER_SLICE_LENGTH,
            )
        assertEquals(expected, MembershipSnapshot.decodeRosterSlice(slice))
    }

    @Test
    fun `the membership's cohort key sits right after the discriminator`() {
        // The memcmp offset the roster query filters on (T034).
        val account = bytes("Membership")
        assertEquals(8, AccountLayout.Membership.COHORT)
        assertContentEquals(key(0x77).bytes, account.copyOfRange(8, 40))
    }

    @Test
    fun `config decodes the chip set and the faucet's books`() {
        val c = ConfigSnapshot.decode(bytes("Config"))
        assertEquals(key(0x11), c.admin)
        assertEquals(key(0x22), c.operator)
        assertEquals(key(0x33), c.sponsor)
        assertEquals(key(0x44), c.skrMint)
        assertEquals(key(0x55), c.feeDestination)
        assertTrue(c.ladderGateEnabled)
        assertEquals(false, c.genesisGateEnabled)
        assertEquals(listOf(10_000_000uL, 25_000_000uL, 50_000_000uL, 100_000_000uL), c.chipAmounts)
        assertEquals(7, c.chipVersion)
        assertEquals(1_790_000_123, c.chipPublishedAt)
        assertEquals(120_000_000uL, c.maxTopTier)
        assertEquals(20_000_000uL, c.solTopUp)
        assertEquals(50_000_000uL, c.sponsorFloat)
        assertEquals(720_000_000uL, c.dailyLamportCap)
        assertEquals(20_350, c.lamportDay)
        assertEquals(123_456uL, c.lamportsToday)
    }

    @Test
    fun `the day's faucet spend resets on a new day, as the program's own budget does`() {
        // `Faucet::remaining_today`: a counter left over from another day is not
        // today's spend, even before any claim has rolled it over.
        val c = ConfigSnapshot.decode(bytes("Config"))
        assertEquals(123_456uL, c.lamportsSpentOn(today = 20_350))
        assertEquals(0uL, c.lamportsSpentOn(today = 20_351))
    }

    @Test
    fun `an account of another type is refused, not misread`() {
        assertFailsWith<IllegalArgumentException> { CohortSnapshot.decode(bytes("Membership")) }
        assertFailsWith<IllegalArgumentException> { MembershipSnapshot.decode(bytes("Cohort")) }
        assertFailsWith<IllegalArgumentException> { ConfigSnapshot.decode(bytes("Cohort")) }
        assertFailsWith<IllegalArgumentException> { CohortSnapshot.decode(bytes("Cohort").copyOf(100)) }
    }

    @Test
    fun `an unknown membership state is refused rather than guessed`() {
        val account = bytes("Membership")
        account[AccountLayout.Membership.STATE] = 9
        assertFailsWith<IllegalArgumentException> { MembershipSnapshot.decode(account) }
    }
}
