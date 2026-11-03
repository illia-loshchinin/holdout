package holdout.core

import com.solana.publickey.SolanaPublicKey
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Base64

/**
 * One finished round of a wallet's, as the phone keeps it once the chain may
 * forget it (FR-035d): `close_membership` closes a paid-out round's accounts
 * and emits the same figures as `MembershipClosed`. [paid] is everything the
 * claim paid — stake, share and comeback refund — and 0 for a forfeit.
 */
data class RoundSummary(
    val cohort: SolanaPublicKey,
    val leagueId: Int,
    val index: Long,
    val start: Long,
    val end: Long,
    val tier: Int,
    val stake: ULong,
    val daysCompleted: Int,
    val finished: Boolean,
    val paid: ULong,
    val comeback: ULong,
) {
    /** SKR this round won the member beyond the stake: the share and the comeback refund. 0 for a forfeit. */
    val won: ULong get() = if (finished && paid > stake) paid - stake else 0uL

    /** SKR this round cost the member: the stake of a forfeit, nothing for a finish. */
    val lost: ULong get() = if (finished) 0uL else stake

    /** One line, the data-model §4 `history:` record: comma-separated, no spaces. */
    fun encode(): String = listOf(cohort.base58(), leagueId, index, start, end, tier, stake, daysCompleted, if (finished) 1 else 0, paid, comeback).joinToString(",")

    companion object {
        /** A line written by [encode], or null for one that does not read — an older or damaged record is skipped, never fatal. */
        fun decode(line: String): RoundSummary? = runCatching {
            val f = line.split(",")
            require(f.size == 11)
            RoundSummary(
                cohort = SolanaPublicKey.from(f[0]),
                leagueId = f[1].toInt(),
                index = f[2].toLong(),
                start = f[3].toLong(),
                end = f[4].toLong(),
                tier = f[5].toInt(),
                stake = f[6].toULong(),
                daysCompleted = f[7].toInt(),
                finished = f[8] == "1",
                paid = f[9].toULong(),
                comeback = f[10].toULong(),
            )
        }.getOrNull()
    }
}

object History {
    /** Anchor's event discriminator for `MembershipClosed`: `sha256("event:MembershipClosed")[0..8]`. */
    private val MEMBERSHIP_CLOSED: ByteArray = MessageDigest.getInstance("SHA-256").digest("event:MembershipClosed".encodeToByteArray()).copyOf(8)

    /** `MembershipClosed`'s borsh payload: 2 keys, u8, u32, 2 × i64, u8, u64, u8, bool, 2 × u64. */
    private const val PAYLOAD_SIZE = 32 + 32 + 1 + 4 + 8 + 8 + 1 + 8 + 1 + 1 + 8 + 8

    /**
     * The summary a finished round of [wallet]'s reads on chain right now, or
     * null while its outcome can still change: a claim paid, or a forfeit of a
     * settled cohort. [paid] is the claim's amount ([Stage.Claimed]).
     */
    fun fromChain(address: SolanaPublicKey, cohort: CohortSnapshot, member: MembershipSnapshot, paid: ULong, comeback: ULong): RoundSummary? {
        val finished = when (member.state) {
            MembershipSnapshot.State.Claimed -> true
            MembershipSnapshot.State.Forfeited -> if (cohort.settled) false else return null
            else -> return null
        }
        return RoundSummary(
            cohort = address,
            leagueId = cohort.leagueId,
            index = cohort.index,
            start = cohort.start,
            end = Derive.end(cohort),
            tier = member.tier,
            stake = member.stake,
            daysCompleted = member.daysCompleted,
            finished = finished,
            paid = if (finished) paid else 0uL,
            comeback = if (finished) comeback else 0uL,
        )
    }

    /** The `MembershipClosed` events of [programId] in one transaction's [logs] whose wallet is [wallet]. */
    fun closedIn(logs: List<String>, programId: SolanaPublicKey, wallet: SolanaPublicKey): List<RoundSummary> {
        // `Program data:` lines belong to whichever program is executing; track the invocation stack.
        val stack = ArrayDeque<String>()
        val found = mutableListOf<RoundSummary>()
        val program = programId.base58()
        for (line in logs) {
            when {
                line.startsWith("Program ") && line.contains(" invoke [") -> stack.addLast(line.removePrefix("Program ").substringBefore(" "))
                line.startsWith("Program ") && (line.endsWith(" success") || line.contains(" failed")) -> stack.removeLastOrNull()
                line.startsWith("Program data: ") && stack.lastOrNull() == program -> {
                    val bytes = runCatching { Base64.getDecoder().decode(line.removePrefix("Program data: ").trim()) }.getOrNull() ?: continue
                    decodeClosed(bytes)?.takeIf { it.second == wallet }?.let { found += it.first }
                }
            }
        }
        return found
    }

    /** A `MembershipClosed` event — discriminator then payload — as its summary and its wallet, or null for any other event. */
    fun decodeClosed(event: ByteArray): Pair<RoundSummary, SolanaPublicKey>? {
        if (event.size < 8 + PAYLOAD_SIZE || !event.copyOf(8).contentEquals(MEMBERSHIP_CLOSED)) return null
        val b = ByteBuffer.wrap(event, 8, PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        fun key(): SolanaPublicKey = SolanaPublicKey(ByteArray(32).also { b.get(it) })
        val cohort = key()
        val wallet = key()
        val league = b.get().toInt() and 0xff
        val index = b.int.toLong() and 0xffffffffL
        val start = b.long
        val end = b.long
        val tier = b.get().toInt() and 0xff
        val stake = b.long.toULong()
        val days = b.get().toInt() and 0xff
        val finished = b.get().toInt() != 0
        val paid = b.long.toULong()
        val comeback = b.long.toULong()
        return RoundSummary(cohort, league, index, start, end, tier, stake, days, finished, paid, comeback) to wallet
    }

    /**
     * Every closed round of [wallet]'s, from its own transaction history: the
     * `MembershipClosed` events of the transactions that touched the wallet,
     * newest first, reading at most [maxSignatures] signatures. Null when the
     * node stops answering part-way, so a half-read history is never kept as
     * the whole one (FR-035d).
     */
    suspend fun rebuild(rpc: Rpc, programId: SolanaPublicKey, wallet: SolanaPublicKey, maxSignatures: Int = 2_000): List<RoundSummary>? {
        val found = mutableListOf<RoundSummary>()
        var before: String? = null
        var read = 0
        while (read < maxSignatures) {
            val page = rpc.signaturesFor(wallet, before, limit = minOf(1_000, maxSignatures - read)) ?: return null
            if (page.isEmpty()) break
            for (signature in page) {
                val logs = rpc.transactionLogs(signature) ?: return null
                found += closedIn(logs, programId, wallet)
            }
            read += page.size
            before = page.last()
        }
        return found.distinctBy { it.cohort }
    }

    /** [kept] with [more] added, one summary per cohort, the newest figure winning. */
    fun merge(kept: List<RoundSummary>, more: List<RoundSummary>): List<RoundSummary> = (kept + more).associateBy { it.cohort }.values.sortedByDescending { it.start }

    /** Lifetime SKR won beyond stakes and lost to forfeits, over every round in [history]. */
    fun totals(history: List<RoundSummary>): Pair<ULong, ULong> = history.fold(0uL to 0uL) { (won, lost), r -> (won + r.won) to (lost + r.lost) }
}
