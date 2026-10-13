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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import java.io.File
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How the app's snapshot reads the chain (FR-071, R8): a pulse with nothing changed is the cohorts
 * not settled yet and one `getMultipleAccounts` for the wallet; settled cohorts, `Config`, the leagues
 * and the wallet's memberships are kept until what moves them does. One failed call is asked again
 * before the pass gives up. No network: the HTTP layer is a fake that answers each call from the
 * program's golden bytes.
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

    /** The golden cohort, moved into the golden league (or [league]), settled or not, with [counted] members counted of [members]. */
    private fun cohort(league: Int = leagueId, settled: Boolean = false, counted: Int = 0, members: Int = 1): ByteArray = vector("Cohort").also {
        it[AccountLayout.Cohort.LEAGUE_ID] = league.toByte()
        it[AccountLayout.Cohort.SETTLED] = if (settled) 1 else 0
        u32(it, AccountLayout.Cohort.COUNTED, counted)
        u32(it, AccountLayout.Cohort.MEMBER_COUNT, members)
    }

    /** The golden membership, moved into [cohortKey] and onto [wallet]. */
    private val membershipBytes = vector("Membership").also {
        cohortKey.bytes.copyInto(it, AccountLayout.Membership.COHORT)
        wallet.bytes.copyInto(it, AccountLayout.Membership.WALLET)
    }

    private var now = 0L
    private val pauses = mutableListOf<Long>()

    /** The program's one cohort as the chain holds it now, whether `Config` exists, and the wallet's memberships. */
    private var cohorts = cohort()
    private var live = true
    private var memberships = "[]"

    /** The not-settled read answers from a slot where the cohort is not there, while a fetch still finds it unsettled. */
    private var unsettledLags = false

    /** Kinds of call to fail next, with how many times; and the calls made, by kind. */
    private val failing = mutableMapOf<String, Int>()
    private val calls = mutableMapOf<String, Int>()

    private fun failNext(kind: String, n: Int = 1) {
        failing[kind] = n
    }

    /** Per kind of call, the next one answers what the chain held when it was made, but only once this completes. */
    private val holdNext = mutableMapOf<String, CompletableDeferred<Unit>>()

    private val http =
        object : HttpNetworkDriver {
            override suspend fun makeHttpRequest(request: HttpRequest): String {
                val body = request.body.orEmpty()
                val kind = kindOf(body)
                calls[kind] = (calls[kind] ?: 0) + 1
                val left = failing[kind] ?: 0
                if (left > 0) {
                    failing[kind] = left - 1
                    return """{"jsonrpc":"2.0","error":{"code":429,"message":"Too many requests"},"id":"x"}"""
                }
                val result = answer(kind, body)
                holdNext.remove(kind)?.await()
                return """{"jsonrpc":"2.0","result":$result,"id":"x"}"""
            }
        }

    /** Which read [body] is: its method, and for `getProgramAccounts` the accounts it filters on. */
    private fun kindOf(body: String): String = when (Regex(""""method"\s*:\s*"(\w+)"""").find(body)!!.groupValues[1]) {
        "getAccountInfo" -> if (configAddress in body) "config" else "account"
        "getMultipleAccounts" -> if (wallet.base58() in body) "wallet" else "fetch"
        "getProgramAccounts" -> {
            val memcmps = Regex("memcmp").findAll(body).count()
            when (Regex(""""dataSize"\s*:\s*(\d+)""").find(body)!!.groupValues[1].toInt()) {
                AccountLayout.Cohort.SIZE -> if (memcmps > 1) "unsettled" else "cohorts"
                LeagueSnapshot.SIZE -> "leagues"
                AccountLayout.Membership.SIZE -> if (memcmps > 2) "forfeits" else "memberships"
                else -> "profiles"
            }
        }
        else -> "other"
    }

    private fun answer(kind: String, body: String): String = when (kind) {
        "config" -> account(configBytes.takeIf { live })
        "cohorts" -> accounts(cohorts)
        "unsettled" -> if (!unsettledLags && cohorts[AccountLayout.Cohort.SETTLED] == 0.toByte()) accounts(cohorts) else "[]"
        "leagues" -> accounts(leagueBytes)
        "memberships" -> memberships
        "profiles", "forfeits" -> "[]"
        // No profile, no SKR account, and the wallet itself with 5 lamports.
        "wallet" -> """{"context":{"slot":1},"value":[null,null,{"data":["","base64"],"lamports":5,"owner":"11111111111111111111111111111111"}]}"""
        "fetch" -> """{"context":{"slot":1},"value":[${if (cohortKey.base58() in body) info(cohorts) else "null"}]}"""
        else -> account(null)
    }

    private val repo = ChainRepository(Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", http)), programId, clock = { now }, pause = { pauses += it }, log = {})

    private fun info(bytes: ByteArray) = """{"data":["${b64(bytes)}","base64"],"lamports":1,"owner":"${programId.base58()}"}"""

    private fun account(bytes: ByteArray?) = """{"context":{"slot":1},"value":${bytes?.let(::info) ?: "null"}}"""

    private fun accounts(vararg bytes: ByteArray) = bytes.joinToString(",", "[", "]") { """{"pubkey":"${cohortKey.base58()}","account":${info(it)}}""" }

    private fun load(wallet: SolanaPublicKey? = null) = runBlocking { repo.load(wallet) }

    private fun loadFresh(wallet: SolanaPublicKey? = null) = runBlocking { repo.loadFresh(wallet) }

    // ---- a pulse with nothing changed ---------------------------------------

    @Test
    fun `after the first pass, a pulse with nothing changed is two calls, the cohorts not settled and the wallet's accounts`() {
        load(wallet)
        calls.clear()
        now = 30_000

        val view = assertNotNull(load(wallet))

        assertEquals(mapOf("unsettled" to 1, "wallet" to 1), calls)
        assertEquals(5L, view.lamports)
        assertEquals(1, view.holdouts.size)
    }

    @Test
    fun `the wallet's profile, SKR and SOL come from one call`() {
        val view = assertNotNull(load(wallet))

        assertEquals(1, calls["wallet"])
        assertNull(view.profile)
        assertEquals(0uL, view.skr)
        assertEquals(5L, view.lamports)
        assertNull(calls["account"])
    }

    // ---- failures ---------------------------------------------------------------

    @Test
    fun `a call that fails once is asked again after a short wait, and the pass still answers`() {
        failNext("cohorts")
        failNext("wallet")

        val view = assertNotNull(load(wallet))

        assertEquals(5L, view.lamports)
        assertEquals(2, calls["cohorts"])
        assertEquals(2, calls["wallet"])
        assertEquals(listOf(ChainRepository.RETRY_MILLIS, ChainRepository.RETRY_MILLIS), pauses)
    }

    @Test
    fun `a call that fails twice leaves the view unknown, and nothing after it is read`() {
        failNext("cohorts", 2)

        assertNull(load(wallet))

        assertEquals(2, calls["cohorts"])
        assertNull(calls["memberships"])
        assertNull(calls["wallet"])
    }

    @Test
    fun `a pulse whose not-settled read fails twice leaves the view unknown`() {
        load()
        failNext("unsettled", 2)
        now = 30_000

        assertNull(load())
    }

    // ---- settled cohorts are kept ---------------------------------------------

    @Test
    fun `a cohort that settles is fetched once when it leaves the not-settled list, then kept`() {
        load()
        cohorts = cohort(settled = true)
        now = 30_000
        assertTrue(assertNotNull(load()).holdouts.single().cohort.settled)
        assertEquals(1, calls["fetch"])

        now = 60_000
        assertEquals(1, assertNotNull(load()).holdouts.size)
        assertEquals(1, calls["fetch"])
        assertEquals(1, calls["cohorts"])
    }

    @Test
    fun `every cohort is read whole again once the kept ones are ten minutes old`() {
        load()
        now = ChainRepository.FULL_READ_MILLIS - 1
        load()
        assertEquals(1, calls["cohorts"])

        now = ChainRepository.FULL_READ_MILLIS
        load()
        assertEquals(2, calls["cohorts"])
    }

    // ---- Config and the leagues ---------------------------------------------------

    @Test
    fun `Config is read on the first pass and kept for an hour`() {
        load()
        now = ChainRepository.CONFIG_MAX_AGE_MILLIS - 1
        load()
        assertEquals(1, calls["config"])

        now = ChainRepository.CONFIG_MAX_AGE_MILLIS
        load()
        assertEquals(2, calls["config"])
    }

    @Test
    fun `the leagues are kept for ten minutes when nothing moves them`() {
        load()
        now = 30_000
        load()
        now = ChainRepository.SETTINGS_MAX_AGE_MILLIS - 1
        load()
        assertEquals(1, calls["leagues"])

        now = ChainRepository.SETTINGS_MAX_AGE_MILLIS
        load()
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
    fun `a settlement counted in part since, not yet final, has the leagues read again`() {
        load()
        cohorts = cohort(counted = 1)
        now = 30_000
        load()
        assertEquals(2, calls["leagues"])

        cohorts = cohort(counted = 2)
        now = 60_000
        load()
        assertEquals(3, calls["leagues"])

        now = 90_000
        load()
        assertEquals(3, calls["leagues"])
    }

    @Test
    fun `a program not live here is not kept, so it is read again next pass`() {
        live = false

        assertEquals(ChainView(null, emptyList(), null, 0uL, 0L), load())
        load()

        assertEquals(2, calls["config"])
    }

    // ---- the wallet's memberships ---------------------------------------------

    @Test
    fun `the wallet's memberships are kept while the cohorts they are in do not move`() {
        memberships = accounts(membershipBytes)
        assertNotNull(assertNotNull(load(wallet)).holdouts.single().membership)
        now = 30_000

        val view = assertNotNull(load(wallet))

        assertEquals(1, calls["memberships"])
        assertNotNull(view.holdouts.single().membership)
    }

    @Test
    fun `a cohort the wallet is in that moves has its memberships read again`() {
        memberships = accounts(membershipBytes)
        load(wallet)
        cohorts = cohort(members = 2)
        now = 30_000

        load(wallet)

        assertEquals(2, calls["memberships"])
    }

    @Test
    fun `the wallet's memberships are read again after five minutes with nothing moved`() {
        load(wallet)
        now = ChainRepository.MEMBERSHIPS_MAX_AGE_MILLIS - 1
        load(wallet)
        assertEquals(1, calls["memberships"])

        now = ChainRepository.MEMBERSHIPS_MAX_AGE_MILLIS
        load(wallet)
        assertEquals(2, calls["memberships"])
    }

    @Test
    fun `a fresh read, after the wallet's own action, reads its memberships and its own settled cohorts again`() {
        cohorts = cohort(settled = true)
        memberships = accounts(membershipBytes)
        load(wallet)
        now = 30_000

        assertNotNull(loadFresh(wallet))

        assertEquals(2, calls["memberships"])
        assertEquals(1, calls["fetch"])
    }

    @Test
    fun `an older memberships read that answers after a newer one does not put its answer back over it`() {
        load(wallet)
        now = 30_000
        val held = CompletableDeferred<Unit>()
        holdNext["memberships"] = held
        runBlocking {
            // A fresh read goes out while the join has not landed, and is slow to answer.
            val older = async { repo.loadFresh(wallet) }
            yield()
            // The join lands; the read closing it starts later and answers first.
            memberships = accounts(membershipBytes)
            assertNotNull(assertNotNull(repo.loadFresh(wallet)).holdouts.single().membership)
            held.complete(Unit)
            assertNull(assertNotNull(older.await()).holdouts.single().membership, "the older read answers what it read")
        }
        now = 60_000

        val view = assertNotNull(load(wallet))

        assertEquals(3, calls["memberships"], "the pulse reuses what is kept")
        assertNotNull(view.holdouts.single().membership, "and what is kept is the newer answer")
    }

    @Test
    fun `an older pulse that resumes after a fresh read does not put the settled cohorts back as they were`() {
        cohorts = cohort(settled = true, counted = 1)
        memberships = accounts(membershipBytes)
        load(wallet)
        now = 30_000
        val held = CompletableDeferred<Unit>()
        holdNext["unsettled"] = held
        runBlocking {
            // A pulse goes out and is slow to answer.
            val older = async { repo.load(wallet) }
            yield()
            // The wallet's settled cohort moves; the fresh read after its action re-fetches it and answers first.
            cohorts = cohort(settled = true, counted = 2)
            assertEquals(2L, assertNotNull(repo.loadFresh(wallet)).holdouts.single().cohort.counted)
            held.complete(Unit)
            assertEquals(1L, assertNotNull(older.await()).holdouts.single().cohort.counted, "the older pulse answers what it kept")
        }
        now = 60_000

        assertEquals(2L, assertNotNull(load(wallet)).holdouts.single().cohort.counted, "what is kept is the fresh read's")
    }

    @Test
    fun `a fresh read that fails stays owed, so the next pulse reads the memberships, and once one answers the pulse reuses them`() {
        load(wallet)
        now = 30_000
        // The join lands; the read closing it fails.
        memberships = accounts(membershipBytes)
        failNext("unsettled", 2)
        assertNull(loadFresh(wallet))
        now = 60_000

        assertNotNull(assertNotNull(load(wallet)).holdouts.single().membership, "the next pulse reads fresh")
        assertEquals(2, calls["memberships"])

        now = 90_000
        load(wallet)
        assertEquals(2, calls["memberships"], "answered: the pulse reuses what is kept again")
    }

    @Test
    fun `an older pulse that resumes after a fresh read does not give the leaderboard its older cohorts`() {
        cohorts = cohort(counted = 1)
        load(wallet)
        now = 30_000
        val held = CompletableDeferred<Unit>()
        holdNext["unsettled"] = held
        runBlocking {
            val older = async { repo.load(wallet) }
            yield()
            cohorts = cohort(counted = 2)
            assertNotNull(repo.loadFresh(wallet))
            held.complete(Unit)
            assertNotNull(older.await())
        }

        val board = assertNotNull(runBlocking { repo.leaderboard() })

        assertEquals(listOf(2L), board.cohortsBefore.map { it.counted }, "the leaderboard's first read is the newer one")
    }

    @Test
    fun `a cohort the not-settled read misses but a fetch finds unsettled stays shown and is looked for again`() {
        load()
        now = 30_000
        unsettledLags = true

        val view = assertNotNull(load())
        assertEquals(1, view.holdouts.size, "still shown")
        assertEquals(1, calls["fetch"])

        now = 60_000
        assertEquals(1, assertNotNull(load()).holdouts.size)
        assertEquals(2, calls["fetch"], "looked for again by the next pulse")

        unsettledLags = false
        now = 90_000
        assertEquals(1, assertNotNull(load()).holdouts.size)
        assertEquals(2, calls["fetch"], "back in the not-settled read: nothing to fetch")
    }

    @Test
    fun `another wallet's memberships are read at once`() {
        load(wallet)
        now = 30_000

        load(SolanaPublicKey(ByteArray(32) { 8 }))

        assertEquals(2, calls["memberships"])
    }

    // ---- the leaderboard ---------------------------------------------------------

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

        fun u32(bytes: ByteArray, offset: Int, value: Int) {
            for (i in 0 until 4) bytes[offset + i] = (value shr (8 * i)).toByte()
        }

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
