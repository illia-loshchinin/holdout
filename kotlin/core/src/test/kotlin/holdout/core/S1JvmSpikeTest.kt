package holdout.core

import com.solana.networking.KtorNetworkDriver
import com.solana.programs.MemoProgram
import com.solana.publickey.ProgramDerivedAddress
import com.solana.publickey.SolanaPublicKey
import com.solana.rpc.SolanaRpcClient
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import com.solana.transaction.toUnsignedTransaction
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Spike S1, the half that needs no phone (tasks T012a, research R3, R12).
 *
 * `web3-solana` and `rpc-core` are Android-published libraries, and the crank is
 * a plain JVM program that has to build and send the same transactions the app
 * does (R12). If either only worked under Android, the crank would need a second
 * client implementation and the two would drift — which is the one thing the
 * shared `kotlin/core` module exists to prevent.
 *
 * So this suite runs on the JVM with no Android anywhere, and proves the three
 * things T030 and T036 are about to be built on: keys parse, program addresses
 * derive, and a transaction serialises to bytes that decode back unchanged.
 *
 * The devnet round trip is the fourth thing, and it needs the network, so it is
 * opt-in:
 *
 *     HOLDOUT_DEVNET_SPIKE=1 ./gradlew :core:test
 *
 * On-device signing — Seed Vault over MWA, `signAndSendTransactions`,
 * `NoWalletFound` — is the other half of S1 and cannot be tested here. It runs
 * from the app's spike screen; see docs/holdout_setup_runbook.md step 6.
 */
class S1JvmSpikeTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")

    @Test
    fun `web3-solana parses and re-encodes a base58 key on a plain JVM`() {
        assertEquals("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4", programId.base58())
        assertEquals(32, programId.bytes.size)
    }

    @Test
    fun `program addresses derive off the curve and deterministically`() = runBlocking {
        // The two PDAs `initialize` creates (T027). Nothing here asserts a literal
        // address: the Rust side is not written yet, and a hard-coded expectation
        // would only pin this test to itself. What matters for T030 is that the
        // derivation runs on the JVM and returns the same answer twice.
        for (seed in listOf("mint_authority", "faucet_vault")) {
            val first = ProgramDerivedAddress.find(listOf(seed.encodeToByteArray()), programId).getOrThrow()
            val again = ProgramDerivedAddress.find(listOf(seed.encodeToByteArray()), programId).getOrThrow()
            assertContentEquals(first.bytes, again.bytes, "$seed derived differently twice")
            assertTrue(first.nonce.toInt() and 0xFF in 0..255)
        }
    }

    @Test
    fun `a transaction built on the JVM serialises to bytes that decode back`() {
        val payer = SolanaPublicKey.from("Ho5vmods3ZvjAegHJ8mZTnDa1zbp2Rw4HDNy8Ni2bjfU")
        // Any 32 bytes stand in for a blockhash here; the shape is what is under test.
        val blockhash = SolanaPublicKey(ByteArray(32) { it.toByte() })

        val message =
            Message
                .Builder()
                .addInstruction(MemoProgram.publishMemo(payer, "holdout s1"))
                .setRecentBlockhash(blockhash)
                .build()
        val bytes = message.toUnsignedTransaction().serialize()

        val decoded = Transaction.from(bytes)
        assertEquals(
            payer.base58(),
            decoded.message.accounts
                .first()
                .base58(),
            "the payer must be account 0",
        )
        assertContentEquals(bytes, decoded.serialize(), "re-encoding changed the bytes")
    }

    @Test
    fun `rpc-core reaches devnet from a plain JVM`() {
        if (System.getenv("HOLDOUT_DEVNET_SPIKE") != "1") {
            println("S1: devnet round trip skipped — set HOLDOUT_DEVNET_SPIKE=1 to run it")
            return
        }
        val rpc =
            SolanaRpcClient(
                System.getenv("HOLDOUT_RPC") ?: "https://api.devnet.solana.com",
                KtorNetworkDriver(),
            )
        val response = runBlocking { rpc.getLatestBlockhash() }
        val blockhash = response.result?.blockhash
        assertTrue(response.error == null, "RPC error: ${response.error}")
        assertTrue(!blockhash.isNullOrBlank(), "devnet returned no blockhash")
        println("S1: devnet blockhash $blockhash")
    }
}
