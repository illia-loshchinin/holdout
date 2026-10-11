package holdout.app

import com.solana.publickey.SolanaPublicKey
import holdout.app.cohort.survivorHistory
import holdout.app.faucet.SponsorKey
import holdout.app.holdout.HoldoutViewModel
import holdout.app.holdout.ProgramErrors
import holdout.app.home.boostLine
import holdout.app.ui.Format
import holdout.app.wallet.WalletRepository
import holdout.core.CohortSnapshot
import holdout.core.MembershipSnapshot
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pieces of the staked app that need no phone: the sponsor's signature, errors, amounts, the crowd's history. */
class HoldoutPiecesTest {
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    @Test
    fun `an offer shows the boost the league reserve can add, and nothing while it is empty`() {
        assertEquals("Prize boost: up to 12.5 SKR from the reserve of Easy · 7-day Holdouts when it settles", boostLine(12_500_000uL, "Easy · 7-day"))
        assertNull(boostLine(0uL, "Easy · 7-day"))
        assertNull(boostLine(null, "Easy · 7-day"), "a league not read yet")
    }

    @Test
    fun `the lost-stake sentence names the level and length, says where the pool goes when nobody finishes, and never says league`() {
        val easy = CohortSnapshot(0, 0, 0, 0, 7, 25, 86_400, 0, listOf(1uL, 2uL, 3uL, 4uL), form = holdout.core.LeagueParams.FORM_KNEES_ALLOWED)
        val hard = easy.copy(durationDays = 21, form = holdout.core.LeagueParams.FORM_TOES)
        assertEquals(
            "80 % of it stays with members: 70 % to the prize pool and 10 % to the boost and comeback reserves of Easy · 7-day Holdouts. " +
                "20 % goes to the project, and so does the prize pool if nobody finishes.",
            Format.lostStakeSplit(easy, "the prize pool"),
        )
        assertTrue("Hard · 21-day Holdouts" in Format.lostStakeSplit(hard, "this Holdout's prize pool"))
        assertTrue("league" !in Format.lostStakeSplit(hard, "the prize pool").lowercase())
        assertTrue(" %" !in Format.lostStakeSplit(easy, "the prize pool"), "a % never starts a line on its own")
    }

    @Test
    fun `the bundled sponsor key signs what an Ed25519 verifier accepts, and refuses a mismatched pair`() {
        val seed = ByteArray(32) { (it * 7).toByte() }
        val public = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        val key = assertNotNull(SponsorKey.fromHex(hex(seed + public)))
        assertEquals(SolanaPublicKey(public), key.publicKey)
        val message = "claim_faucet".encodeToByteArray()
        val signature = key.sign(message)
        val verifier = Ed25519Signer().apply {
            init(false, Ed25519PublicKeyParameters(public, 0))
            update(message, 0, message.size)
        }
        assertTrue(verifier.verifySignature(signature))
        assertNull(SponsorKey.fromHex(hex(seed + ByteArray(32))), "a public half that is not the seed's")
        assertNull(SponsorKey.fromHex(""), "no key bundled")
    }

    @Test
    fun `a refusal is explained in words a member can act on`() {
        assertEquals(6021, WalletRepository.failure("Transaction simulation failed: custom program error: 0x1785").programError)
        // A refusal came back through the wallet: the member was asked.
        assertFalse(WalletRepository.failure("Transaction simulation failed: custom program error: 0x1785").beforeWallet)
        assertEquals("Joining for this Holdout has closed.", ProgramErrors.explain(6021))
        assertEquals(
            "Staking in a Holdout needs a Seeker. You can still do the Daily 25 and watch every Holdout.",
            ProgramErrors.explain(6024),
        )
        assertNull(ProgramErrors.explain(null))
    }

    @Test
    fun `amounts are SKR with the mint's six decimals, and dollars are always approximate`() {
        assertEquals("46 SKR", Format.skr(46_000_000uL))
        assertEquals("12.5 SKR", Format.skr(12_500_000uL))
        assertEquals("0.000001 SKR", Format.skr(1uL))
        assertEquals("≈ $25", Format.tierUsd(1))
        assertEquals("3d 4h", Format.countdown(3 * 86_400 + 4 * 3_600 + 59))
        assertEquals("4h 06m", Format.countdown(4 * 3_600 + 6 * 60))
        assertEquals("0m 00s", Format.countdown(-5))
        assertEquals("10-minute", Format.dayLength(600))
        assertEquals("Ho1d…yoy4", Format.short("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4"))
    }

    @Test
    fun `a completed day's proof is fixed by the cohort, the day and the reps`() {
        val cohort = SolanaPublicKey(ByteArray(32) { 3 })
        val proof = HoldoutViewModel.proofOf(cohort, 2, 25)
        assertEquals(32, proof.size)
        assertContentEquals(proof, HoldoutViewModel.proofOf(cohort, 2, 25))
        assertFalse(proof.contentEquals(HoldoutViewModel.proofOf(cohort, 3, 25)))
    }

    @Test
    fun `the crowd thins by the deadlines that have passed, read from days completed`() {
        val start = 1_000_000L
        val c = CohortSnapshot(0, 0, start, start - 3_600, 7, 25, 86_400, 0, listOf(1uL, 2uL, 3uL, 4uL))
        val wallet = SolanaPublicKey(ByteArray(32))
        val members = listOf(0, 1, 2, 3, 3).map { MembershipSnapshot(wallet, 0, 1uL, start, it, MembershipSnapshot.State.Joined) }
        assertEquals(emptyList(), survivorHistory(c, members, start + 60))
        assertEquals(listOf(0 to 4, 1 to 3, 2 to 2), survivorHistory(c, members, start + 3 * 86_400))
    }
}
