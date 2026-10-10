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
        assertEquals(229, bytes("Cohort").size)
        assertEquals(AccountLayout.Cohort.SIZE, bytes("Cohort").size)
        assertEquals(101, bytes("Membership").size)
        assertEquals(AccountLayout.Membership.SIZE, bytes("Membership").size)
        assertEquals(294, bytes("Config").size)
        assertEquals(AccountLayout.Config.SIZE, bytes("Config").size)
        assertEquals(68, bytes("LeagueConfig").size)
        assertEquals(LeagueSnapshot.SIZE, bytes("LeagueConfig").size)
        assertEquals(257, bytes("Profile").size)
        assertEquals(ProfileLayout.SIZE, bytes("Profile").size)
    }

    @Test
    fun `discriminators are Anchor's sha256 prefixes`() {
        assertContentEquals(bytes("Cohort").copyOf(8), AccountLayout.Cohort.DISCRIMINATOR)
        assertContentEquals(bytes("Membership").copyOf(8), AccountLayout.Membership.DISCRIMINATOR)
        assertContentEquals(bytes("Config").copyOf(8), AccountLayout.Config.DISCRIMINATOR)
        assertContentEquals(bytes("LeagueConfig").copyOf(8), LeagueSnapshot.DISCRIMINATOR)
        assertContentEquals(bytes("Profile").copyOf(8), ProfileLayout.DISCRIMINATOR)
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
        assertEquals(LeagueParams.FORM_KNEES_ALLOWED, c.form)
        assertEquals(listOf(10_000_000uL, 25_000_000uL, 50_000_000uL, 100_000_000uL), c.chips)
        assertEquals(12, c.memberCount)
        assertEquals(900uL, c.heldStake)
        assertEquals(680uL, c.pool)
        assertEquals(600uL, c.boostDue)
        assertEquals(300uL, c.comebackDue)
        assertEquals(3, c.forfeitsRecorded)
        assertEquals(9, c.counted)
        assertEquals(4, c.finisherCount)
        assertEquals(listOf(1L, 0L, 2L, 1L), c.finishersPerTier)
        assertEquals(400uL, c.totalFinisherStake)
        assertTrue(c.settled)
        assertEquals(listOf(17uL, 0uL, 42uL, 85uL), c.sharePerTier)
        assertEquals(2uL, c.roundingRemainder)
        assertEquals(500uL, c.noFinisherFee)
        assertEquals(501uL, c.boostReceived)
        assertEquals(77uL, c.comebackOwed)
        assertEquals(66uL, c.comebackFunded)
        assertEquals(11uL, c.comebackClaimed)
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
                comebackEntitled = 5_000_000uL,
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
        assertEquals(key(0x56), c.developerDestination)
        assertEquals(true, c.genesisGateEnabled)
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
    fun `given the golden league, when decoded, then its form and both reserves read back`() {
        val l = LeagueSnapshot.decode(bytes("LeagueConfig"))
        assertEquals(LeagueParams(1, 21, 25, 0, LeagueParams.FORM_KNEES_ALLOWED, 1_790_571_600, 86_400, 604_800, 3_600), l.params)
        assertEquals(3, l.nextIndex)
        assertEquals(LeagueParams.FORM_KNEES_ALLOWED, l.form)
        assertEquals(4_000_000uL, l.boostReserve)
        assertEquals(2_000_000uL, l.comebackReserve)
        assertEquals(252, l.reserveVaultBump)
    }

    @Test
    fun `given the golden profile, when decoded, then every slot carries its open comeback`() {
        val p = ProfileSnapshot.decode(bytes("Profile"))
        assertEquals(key(0x99), p.wallet)
        assertEquals(
            listOf(
                ProfileSnapshot.LeagueProgress(5, 2, 3, 4, 1_790_600_000, 25_000_000uL, 1_790_599_999, 1_791_809_999),
                ProfileSnapshot.LeagueProgress(1, 0, 1, 1, 1_791_400_000, 0uL, 1_791_300_000, 1_792_500_000),
                ProfileSnapshot.LeagueProgress(0, 1, 0, 0, 1_790_700_000, 10_000_000uL, 1_790_699_000, 1_790_840_000),
            ),
            p.leagues,
        )
        assertEquals(ProfileSnapshot.Running(key(0xaa), 1_791_800_000), p.running)
        assertEquals(ProfileSnapshot.Pending(key(0xbb), 1_792_000_000, 1_792_600_000), p.pending)
        assertEquals(20_351, p.lastFaucetDay)
    }

    @Test
    fun `given an open comeback, when a cohort starts inside its window, then only that cohort is the comeback`() {
        val slot = ProfileSnapshot.LeagueProgress(0, 1, 0, 0, 1_000, 25uL, 1_000, 2_000)
        assertTrue(!slot.comebackOpenFor(1_000), "a cohort starting at the missed deadline itself is not after it")
        assertTrue(slot.comebackOpenFor(1_001))
        assertTrue(slot.comebackOpenFor(2_000), "the last instant of the window still counts")
        assertTrue(!slot.comebackOpenFor(2_001))
        assertTrue(!slot.copy(comebackStake = 0uL).comebackOpenFor(1_500), "a used record opens nothing")
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
