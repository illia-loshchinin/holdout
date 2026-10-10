package holdout.app

import com.solana.publickey.SolanaPublicKey
import holdout.app.stake.SeekerGate
import holdout.app.stake.SeekerStatus
import holdout.app.storage.InMemoryStore
import holdout.app.storage.SeekerConfirmation
import holdout.core.GenesisCheck
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Seeker check every join passes (spec FR-003b–FR-003d, ES-0c, ES-0d, AC-21). */
class SeekerGateTest {

    private val wallet = SolanaPublicKey(ByteArray(32) { 0x11 }).base58()
    private val mint = SolanaPublicKey(ByteArray(32) { 0x22 })

    private class Answers(vararg answers: GenesisCheck) {
        private val queue = ArrayDeque(answers.toList())
        var asked = 0

        suspend fun check(@Suppress("UNUSED_PARAMETER") wallet: String): GenesisCheck {
            asked += 1
            return queue.removeFirst()
        }
    }

    @Test
    fun `a holder is confirmed, remembered, and never asked again`() = runBlocking {
        val store = InMemoryStore()
        val answers = Answers(GenesisCheck.Holds(mint))
        val gate = SeekerGate(store, answers::check, clock = { 42L })

        assertEquals(SeekerStatus.Confirmed, gate.status(wallet))
        assertEquals(SeekerConfirmation(mint.base58(), 42L), store.seekerConfirmation(wallet))
        assertTrue(gate.remembered(wallet))

        assertEquals(SeekerStatus.Confirmed, gate.status(wallet), "an outage later never blocks a confirmed owner")
        assertEquals(1, answers.asked)
    }

    @Test
    fun `a wallet without one sees the Seeker-exclusive sheet, and is asked again next time`() = runBlocking {
        val store = InMemoryStore()
        val answers = Answers(GenesisCheck.None, GenesisCheck.Holds(mint))
        val gate = SeekerGate(store, answers::check)

        assertEquals(SeekerStatus.NotSeeker, gate.status(wallet))
        assertNull(store.seekerConfirmation(wallet), "an absence is never kept")
        assertFalse(gate.remembered(wallet))

        assertEquals(SeekerStatus.Confirmed, gate.status(wallet), "a wallet that gains a token is let in")
        assertEquals(2, answers.asked)
    }

    @Test
    fun `no answer from mainnet is a try-again, never the Seeker-exclusive sheet`() = runBlocking {
        val store = InMemoryStore()
        val gate = SeekerGate(store, Answers(GenesisCheck.Unknown(rateLimited = true))::check)

        assertEquals(SeekerStatus.CouldNotCheck, gate.status(wallet))
        assertNull(store.seekerConfirmation(wallet))
    }

    @Test
    fun `a confirmation is kept per wallet`() = runBlocking {
        val store = InMemoryStore()
        val other = SolanaPublicKey(ByteArray(32) { 0x33 }).base58()
        val gate = SeekerGate(store, Answers(GenesisCheck.Holds(mint), GenesisCheck.None)::check)

        assertEquals(SeekerStatus.Confirmed, gate.status(wallet))
        assertEquals(SeekerStatus.NotSeeker, gate.status(other), "another wallet on the same phone is checked on its own")
    }

    @Test
    fun `the confirmation record round-trips`() {
        val kept = SeekerConfirmation(mint.base58(), 1_760_000_000_000L)
        assertEquals(kept, SeekerConfirmation.decode(kept.encode()))
        assertNull(SeekerConfirmation.decode("garbage"))
        assertNull(SeekerConfirmation.decode("12|"))
    }
}
