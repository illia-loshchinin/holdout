package holdout.app.chain

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import holdout.core.AccountLayout
import holdout.core.CohortSnapshot
import holdout.core.LeagueSnapshot
import holdout.core.Pdas
import holdout.core.Rpc
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * How often the app's snapshot reads each account (R8): one failed call is asked
 * again before the pass gives up, `Config` and the leagues are kept between passes,
 * and the leaderboard takes a fresh cohorts read from the snapshot. No network: the
 * HTTP layer is a fake that answers each call from the program's golden bytes.
 */
class ChainRepositoryReadsTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val wallet = SolanaPublicKey(ByteArray(32) { 9 })
    private val cohortKey = SolanaPublicKey(ByteArray(32) { 3 })

    private val vectors = repoFile("tests/helpers/layout-vectors.json")
    private val configBytes = vector("Config")
    private val leagueBytes = vector("LeagueConfig")
    private val configAddress = runBlocking { Pdas.config(programId) }.base58()

    /** One account's golden bytes from the vectors file, which maps each account name to its hex. */
    private fun vector(name: String): ByteArray = hex(Regex(""""$name"\s*:\s*"([0-9a-f]+)"""").find(vectors)!!.groupValues[1])
    private val leagueId = LeagueSnapshot.decode(leagueBytes).params.leagueId

    /** The golden cohort, moved into the golden league (or [league]), settled or not. */
    private fun cohort(league: Int = leagueId, settled: Boolean = false): ByteArray = vector("Cohort").also {
        it[AccountLayout.Cohort.LEAGUE_ID] = league.toByte()
        it[AccountLayout.Cohort.SETTLED] = if (settled) 1 else 0
    }

    private var now = 0L
    private val pauses = mutableListOf<Long>()

    /** What each kind of call answers, by its name below; a kind left out fails. Calls are counted by kind. */
    private val answers = mutableMapOf<String, () -> String?>()
    private val calls = mutableMapOf<String, Int>()

    /** Fails the next [n] calls of [kind], then answers it as before. */
    private fun failNext(kind: String, n: Int = 1) {
        val then = answers.getValue(kind)
        var left = n
        answers[kind] = { if (left-- > 0) null else then() }
    }

    private val http =
        object : HttpNetworkDriver {
            override suspend fun makeHttpRequest(request: HttpRequest): String {
                val kind = kindOf(request.body.orEmpty())
                calls[kind] = (calls[kind] ?: 0) + 1
                val result = answers[kind]?.invoke() ?: return """{"jsonrpc":"2.0","error":{"code":429,"message":"Too many requests"},"id":"x"}"""
                return """{"jsonrpc":"2.0","result":$result,"id":"x"}"""
            }
        }

    /** Which read [body] is: its method, and for `getProgramAccounts` the account size it filters on. */
    private fun kindOf(body: String): String = when (Regex(""""method"\s*:\s*"(\w+)"""").find(body)!!.groupValues[1]) {
        "getAccountInfo" -> if (configAddress in body) "config" else "account"
        "getBalance" -> "balance"
        "getProgramAccounts" ->
            when (Regex(""""dataSize"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toInt()) {
                AccountLayout.Cohort.SIZE -> "cohorts"
                LeagueSnapshot.SIZE -> "leagues"
                AccountLayout.Membership.SIZE -> if (Regex("memcmp").findAll(body).count() > 2) "forfeits" else "memberships"
                else -> "profiles"
            }
        else -> "other"
    }

    private val repo = ChainRepository(Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", http)), programId, clock = { now }, pause = { pauses += it }, log = {})

    private fun account(bytes: ByteArray?) = """{"context":{"slot":1},"value":${bytes?.let { """{"data":["${b64(it)}","base64"],"lamports":1,"owner":"${programId.base58()}"}""" } ?: "null"}}"""

    private fun accounts(vararg bytes: ByteArray) = bytes.joinToString(",", "[", "]") { """{"pubkey":"${cohortKey.base58()}","account":{"data":["${b64(it)}","base64"],"lamports":1,"owner":"${programId.base58()}"}}""" }

    private var cohorts = cohort()

    init {
        answers["config"] = { account(configBytes) }
        answers["cohorts"] = { accounts(cohorts) }
        answers["leagues"] = { accounts(leagueBytes) }
        answers["memberships"] = { "[]" }
        answers["profiles"] = { "[]" }
        answers["forfeits"] = { "[]" }
        answers["account"] = { account(null) }
        answers["balance"] = { """{"context":{"slot":1},"value":5}""" }
    }

    private fun load(wallet: SolanaPublicKey? = null) = runBlocking { repo.load(wallet) }

    @Test
    fun `a call that fails once is asked again after a short wait, and the pass still answers`() {
        failNext("cohorts")
        failNext("balance")

        val view = assertNotNull(load(wallet))

        assertEquals(5L, view.lamports)
        assertEquals(1, view.holdouts.size)
        assertEquals(2, calls["cohorts"])
        assertEquals(2, calls["balance"])
        assertEquals(listOf(ChainRepository.RETRY_MILLIS, ChainRepository.RETRY_MILLIS), pauses)
    }

    @Test
    fun `a call that fails twice leaves the view unknown, and nothing after it is read`() {
        failNext("cohorts", 2)

        assertNull(load(wallet))

        assertEquals(2, calls["cohorts"])
        assertNull(calls["memberships"])
        assertNull(calls["balance"])
    }

    @Test
    fun `config and the leagues are read on the first pass and kept for ten minutes`() {
        load()
        now = 30_000
        load()
        now = ChainRepository.SETTINGS_MAX_AGE_MILLIS - 1
        load()
        assertEquals(1, calls["config"])
        assertEquals(1, calls["leagues"])
        assertEquals(3, calls["cohorts"])

        now = ChainRepository.SETTINGS_MAX_AGE_MILLIS
        load()
        assertEquals(2, calls["config"])
        assertEquals(2, calls["leagues"])
    }

    @Test
    fun `a kept config and leagues still make a whole view`() {
        val first = assertNotNull(load())
        now = 30_000

        val second = assertNotNull(load())

        assertEquals(first.config, second.config)
        assertEquals(first.holdouts.map { it.league }, second.holdouts.map { it.league })
        assertNotNull(second.holdouts.single().league)
    }

    @Test
    fun `a cohort of a league not read yet has the leagues read again`() {
        load()
        cohorts = cohort(league = leagueId + 1)
        now = 30_000

        load()

        assertEquals(2, calls["leagues"])
        assertEquals(1, calls["config"])
    }

    @Test
    fun `a cohort that settled since has the leagues read again, for their reserves`() {
        load()
        cohorts = cohort(settled = true)
        now = 30_000
        load()
        assertEquals(2, calls["leagues"])

        now = 60_000
        load()
        assertEquals(2, calls["leagues"])
    }

    @Test
    fun `a program not live here is not kept, so it is read again next pass`() {
        answers["config"] = { account(null) }

        assertEquals(ChainView(null, emptyList(), null, 0uL, 0L), load())
        load()

        assertEquals(2, calls["config"])
    }

    @Test
    fun `the leaderboard takes the snapshot's cohorts read of the last 30 seconds as its first read`() {
        load()
        now = ChainRepository.COHORTS_REUSE_MILLIS

        assertNotNull(runBlocking { repo.leaderboard() })
        // One by the snapshot, one after the profiles.
        assertEquals(2, calls["cohorts"])

        now = ChainRepository.COHORTS_REUSE_MILLIS * 2 + 1
        runBlocking { repo.leaderboard() }
        assertEquals(4, calls["cohorts"])
    }

    @Test
    fun `the leaderboard with no snapshot read yet reads the cohorts on both sides`() {
        val board = assertNotNull(runBlocking { repo.leaderboard() })

        assertEquals(2, calls["cohorts"])
        assertEquals(listOf(CohortSnapshot.decode(cohorts)), board.cohortsBefore)
    }

    private companion object {
        fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

        /** A file committed elsewhere in the repository: the program's golden vectors (research R2). */
        fun repoFile(relative: String): String {
            var dir: File? = File(System.getProperty("user.dir")).absoluteFile
            while (dir != null) {
                File(dir, relative).takeIf { it.isFile }?.let { return it.readText() }
                dir = dir.parentFile
            }
            error("$relative not found above ${System.getProperty("user.dir")}")
        }
    }
}
