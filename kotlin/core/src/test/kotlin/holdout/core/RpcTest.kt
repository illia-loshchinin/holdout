package holdout.core

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T034 — account reads (research R8): what the roster query asks the RPC node
 * for, byte offsets included, and what comes back. No network: the HTTP layer
 * is a fake that records each request body and answers from a script.
 */
class RpcTest {

    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")
    private val cohortKey = SolanaPublicKey(ByteArray(32) { 0x77 })

    private val goldenMembership =
        RepoFiles.hex(
            Json
                .parseToJsonElement(RepoFiles.read("tests/helpers/layout-vectors.json"))
                .jsonObject
                .getValue("Membership")
                .jsonPrimitive.content,
        )

    /** Answers every JSON-RPC call with [reply] and keeps what was sent. */
    private class FakeHttp(private val reply: (String) -> String) : HttpNetworkDriver {
        val sent = mutableListOf<String>()

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            val body = request.body ?: ""
            sent += body
            return reply(body)
        }
    }

    private fun rpcWith(http: FakeHttp) = Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", http))

    private fun ok(result: String) = """{"jsonrpc":"2.0","result":$result,"id":"x"}"""

    // ---- the query -----------------------------------------------------------

    @Test
    fun `the roster query matches a cohort's memberships at the program's own offsets`() {
        val params = Rpc.programAccountsParams(programId, AccountQueries.membershipsOf(cohortKey))
        val expected =
            Json.parseToJsonElement(
                """
                ["Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4", {
                  "encoding": "base64",
                  "commitment": "confirmed",
                  "filters": [
                    {"dataSize": 93},
                    {"memcmp": {"offset": 0, "bytes": "fjMqo7A2sRw"}},
                    {"memcmp": {"offset": 8, "bytes": "93MB2qRDNVLxbmmPuYpLdAqn3u2x9ZhaVZK5wELHueP8"}}
                  ],
                  "dataSlice": {"offset": 40, "length": 51}
                }]
                """,
            )
        assertEquals(expected, params)
    }

    @Test
    fun `the offsets are where the golden bytes put the fields`() {
        val query = AccountQueries.membershipsOf(cohortKey)
        val cohortFilter = query.filters.filterIsInstance<AccountFilter.Memcmp>().single { it.offset != 0 }
        // The cohort key really is at the memcmp offset of a real Membership…
        val at = cohortFilter.offset
        assertContentEquals(cohortFilter.bytes, goldenMembership.copyOfRange(at, at + 32))
        // …and the slice decodes to the same member as the whole account.
        val slice = query.dataSlice!!
        assertEquals(
            MembershipSnapshot.decode(goldenMembership),
            MembershipSnapshot.decodeRosterSlice(goldenMembership.copyOfRange(slice.offset, slice.offset + slice.length)),
        )
    }

    @Test
    fun `the cohort query lists every cohort and nothing else`() {
        val params = Rpc.programAccountsParams(programId, AccountQueries.cohorts())
        val config = params.jsonArray[1].jsonObject
        assertEquals(
            Json.parseToJsonElement("""[{"dataSize": 236}, {"memcmp": {"offset": 0, "bytes": "PvkMGFCafqS"}}]"""),
            config.getValue("filters"),
        )
        assertNull(config["dataSlice"], "status reads the whole cohort")
    }

    // ---- the read --------------------------------------------------------------

    @Test
    fun `a roster read is one getProgramAccounts, decoded from the slices`() {
        val slice = "iIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIgCgPD6AgAAAADAfcBqAAAAAA0C"
        val http =
            FakeHttp {
                ok(
                    """[{"pubkey":"93MB2qRDNVLxbmmPuYpLdAqn3u2x9ZhaVZK5wELHueP8",
                        "account":{"data":["$slice","base64"],"executable":false,"lamports":1500000,
                        "owner":"Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4","rentEpoch":0,"space":51}}]""",
                )
            }
        val members = runBlocking { rpcWith(http).memberships(programId, cohortKey) }

        assertEquals(listOf(MembershipSnapshot.decode(goldenMembership)), members)
        assertEquals(1, http.sent.size, "one call for the whole cohort (R8)")
        val sent = Json.parseToJsonElement(http.sent.single()).jsonObject
        assertEquals("getProgramAccounts", sent.getValue("method").jsonPrimitive.content)
        assertEquals(Rpc.programAccountsParams(programId, AccountQueries.membershipsOf(cohortKey)), sent.getValue("params"))
    }

    @Test
    fun `an RPC error reads as unknown, never as an empty cohort`() {
        val http = FakeHttp { """{"jsonrpc":"2.0","error":{"code":-32010,"message":"excluded from account secondary indexes"},"id":"x"}""" }
        assertNull(runBlocking { rpcWith(http).memberships(programId, cohortKey) })
    }

    @Test
    fun `an unreachable cluster reads as unknown`() {
        val http = FakeHttp { throw java.io.IOException("offline") }
        assertNull(runBlocking { rpcWith(http).memberships(programId, cohortKey) })
        assertNull(runBlocking { rpcWith(http).balance(programId) })
    }

    @Test
    fun `a member that does not decode fails the read rather than vanishing from the roster`() {
        val bad = Base64.getEncoder().encodeToString(ByteArray(51).also { it[50] = 9 }) // state 9
        val http =
            FakeHttp {
                ok("""[{"pubkey":"${cohortKey.base58()}","account":{"data":["$bad","base64"],"lamports":1,"owner":"${programId.base58()}"}}]""")
            }
        assertNull(runBlocking { rpcWith(http).memberships(programId, cohortKey) })
    }

    @Test
    fun `balances and single accounts`() {
        val http =
            FakeHttp { body ->
                when (Json.parseToJsonElement(body).jsonObject.getValue("method").jsonPrimitive.content) {
                    "getBalance" -> ok("""{"context":{"slot":9},"value":50000000}""")
                    "getAccountInfo" ->
                        ok("""{"context":{"slot":9},"value":{"data":["AQID","base64"],"lamports":1,"owner":"${programId.base58()}"}}""")
                    else -> error("unexpected")
                }
            }
        val rpc = rpcWith(http)
        assertEquals(50_000_000L, runBlocking { rpc.balance(cohortKey) })
        assertContentEquals(byteArrayOf(1, 2, 3), runBlocking { rpc.accountData(cohortKey) })
    }

    @Test
    fun `an account that does not exist reads as null`() {
        val http = FakeHttp { ok("""{"context":{"slot":9},"value":null}""") }
        assertNull(runBlocking { rpcWith(http).accountData(cohortKey) })
    }

    // ---- the 30-second refresh -------------------------------------------------

    @Test
    fun `a visible cohort screen reads at once, then every 30 seconds`() {
        val pauses = mutableListOf<Long>()
        var reads = 0
        val seen =
            runBlocking {
                cohortRefreshes(pause = { pauses += it }) { ++reads }.take(3).toList()
            }
        assertEquals(listOf(1, 2, 3), seen, "the first read is immediate — a screen coming back shows fresh figures")
        assertEquals(listOf(30_000L, 30_000L), pauses)
        assertEquals(30_000L, COHORT_REFRESH_MILLIS)
    }

    @Test
    fun `a failed read keeps the last figures and tries again on the next beat`() {
        val pauses = mutableListOf<Long>()
        val script = ArrayDeque(listOf(null, 5, null, null, 6))
        val seen =
            runBlocking {
                cohortRefreshes(pause = { pauses += it }) { script.removeFirst() }.take(2).toList()
            }
        assertEquals(listOf(5, 6), seen, "nothing is emitted for a read that failed")
        assertEquals(4, pauses.size, "and the cadence does not speed up to make up for it")
    }
}
