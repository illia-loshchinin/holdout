package holdout.core

import com.solana.publickey.SolanaPublicKey
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** T384 — the phone's round history: its record, the `MembershipClosed` event, and the totals (FR-035d). */
class HistoryTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val wallet = SolanaPublicKey(ByteArray(32) { 3 })
    private val other = SolanaPublicKey(ByteArray(32) { 4 })
    private val cohort = SolanaPublicKey(ByteArray(32) { 5 })

    private val won = RoundSummary(cohort, 0, 2, 1_790_571_600, 1_791_176_400, 2, 50_000_000uL, 7, finished = true, paid = 57_000_000uL, comeback = 1_000_000uL)
    private val lost = won.copy(cohort = SolanaPublicKey(ByteArray(32) { 6 }), index = 3, start = 1_791_176_400, end = 1_791_781_200, daysCompleted = 3, finished = false, paid = 0uL, comeback = 0uL)

    /** `MembershipClosed` as the program emits it: the 8-byte discriminator, then the borsh fields. */
    private fun event(r: RoundSummary, who: SolanaPublicKey): ByteArray {
        val b = ByteBuffer.allocate(8 + 113).order(ByteOrder.LITTLE_ENDIAN)
        b.put(MessageDigest.getInstance("SHA-256").digest("event:MembershipClosed".encodeToByteArray()).copyOf(8))
        b.put(r.cohort.bytes).put(who.bytes).put(r.leagueId.toByte()).putInt(r.index.toInt()).putLong(r.start).putLong(r.end)
        b.put(r.tier.toByte()).putLong(r.stake.toLong()).put(r.daysCompleted.toByte()).put(if (r.finished) 1 else 0).putLong(r.paid.toLong()).putLong(r.comeback.toLong())
        return b.array()
    }

    @Test
    fun `a summary survives its record, and a damaged line is skipped`() {
        assertEquals(won, RoundSummary.decode(won.encode()))
        assertEquals(lost, RoundSummary.decode(lost.encode()))
        assertNull(RoundSummary.decode("not,a,record"))
    }

    @Test
    fun `only this program's MembershipClosed for this wallet is read from a transaction's logs`() {
        val data = { r: RoundSummary, who: SolanaPublicKey -> "Program data: " + Base64.getEncoder().encodeToString(event(r, who)) }
        val logs = listOf(
            "Program ${programId.base58()} invoke [1]",
            "Program log: Instruction: CloseMembership",
            data(won, wallet),
            "Program ${programId.base58()} success",
            "Program ${programId.base58()} invoke [1]",
            data(lost, other),
            "Program ${programId.base58()} success",
            "Program 11111111111111111111111111111111 invoke [1]",
            data(lost, wallet),
            "Program 11111111111111111111111111111111 success",
        )
        assertEquals(listOf(won), History.closedIn(logs, programId, wallet))
    }

    @Test
    fun `lifetime totals count the share and refund won and the stakes lost`() {
        assertEquals(7_000_000uL to 50_000_000uL, History.totals(listOf(won, lost)))
        assertEquals(listOf(lost, won), History.merge(listOf(won), listOf(lost, won.copy())))
    }

    @Test
    fun `a page of only failed transactions does not end the rebuild (Codex on #77)`() = kotlinx.coroutines.runBlocking {
        val closeLogs = listOf(
            "Program ${programId.base58()} invoke [1]",
            "Program data: " + Base64.getEncoder().encodeToString(event(won, wallet)),
            "Program ${programId.base58()} success",
        )
        // Newest first: a full page of failed transactions, then the one that closed the round.
        val pages = mapOf<String?, List<Rpc.SignatureInfo>>(
            null to List(3) { Rpc.SignatureInfo("failed$it", failed = true) },
            "failed2" to listOf(Rpc.SignatureInfo("closed", failed = false)),
            "closed" to emptyList(),
        )
        val read = mutableListOf<String>()
        val found = History.rebuildFrom(
            page = { before, _ -> pages[before] },
            logs = { sig ->
                read += sig
                if (sig == "closed") closeLogs else emptyList()
            },
            programId = programId,
            wallet = wallet,
            maxSignatures = 100,
        )
        assertEquals(listOf(won), found)
        assertEquals(listOf("closed"), read, "a failed transaction's logs are never read")
    }
}
