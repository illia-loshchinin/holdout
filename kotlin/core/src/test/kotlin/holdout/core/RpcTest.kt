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
import kotlin.test.assertTrue

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
                    {"dataSize": 101},
                    {"memcmp": {"offset": 0, "bytes": "fjMqo7A2sRw"}},
                    {"memcmp": {"offset": 8, "bytes": "93MB2qRDNVLxbmmPuYpLdAqn3u2x9ZhaVZK5wELHueP8"}}
                  ],
                  "dataSlice": {"offset": 40, "length": 60}
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
            Json.parseToJsonElement("""[{"dataSize": 221}, {"memcmp": {"offset": 0, "bytes": "PvkMGFCafqS"}}]"""),
            config.getValue("filters"),
        )
        assertNull(config["dataSlice"], "status reads the whole cohort")
    }

    @Test
    fun `the profile query lists every Profile and nothing else`() {
        val config = Rpc.programAccountsParams(programId, AccountQueries.profiles()).jsonArray[1].jsonObject
        val filters = config.getValue("filters").jsonArray
        assertEquals(Json.parseToJsonElement("""{"dataSize": 236}"""), filters[0])
        assertEquals(0, filters[1].jsonObject.getValue("memcmp").jsonObject.getValue("offset").jsonPrimitive.content.toInt())
        assertNull(config["dataSlice"], "the board reads the whole profile")
    }

    @Test
    fun `the uncounted-forfeit query matches a Forfeited membership not yet counted`() {
        val filters = Rpc.programAccountsParams(programId, AccountQueries.uncountedForfeits()).jsonArray[1].jsonObject.getValue("filters").jsonArray
        val memcmp = filters.mapNotNull { it.jsonObject["memcmp"]?.jsonObject }.associate { it.getValue("offset").jsonPrimitive.content.toInt() to it.getValue("bytes").jsonPrimitive.content }
        assertEquals(Json.parseToJsonElement("""{"dataSize": 101}"""), filters[0])
        assertEquals("2", memcmp.getValue(AccountLayout.Membership.STATE), "state 1, Forfeited: the single byte 1 in base58")
        assertEquals("1", memcmp.getValue(AccountLayout.Membership.COUNTED), "counted false: the single byte 0 in base58")
    }

    // ---- the read --------------------------------------------------------------

    @Test
    fun `a roster read is one getProgramAccounts, decoded from the slices`() {
        val slice = "iIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIiIgCgPD6AgAAAADAfcBqAAAAAA0CAUBLTAAAAAAA"
        val http =
            FakeHttp {
                ok(
                    """[{"pubkey":"93MB2qRDNVLxbmmPuYpLdAqn3u2x9ZhaVZK5wELHueP8",
                        "account":{"data":["$slice","base64"],"executable":false,"lamports":1500000,
                        "owner":"Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4","rentEpoch":0,"space":60}}]""",
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
    fun `a failure names the node's host, never its API key - in the query or in the path`() {
        for (keyed in listOf("https://devnet.example-rpc.com/?api-key=SECRET", "https://devnet.example-rpc.com/v2/SECRET/")) {
            val heard = mutableListOf<String>()
            // The client may quote the endpoint as given, or a form of it (here without its trailing slash, with a port).
            val quoted = keyed.trimEnd('/').replace(".com/", ".com:443/")
            val timeout = FakeHttp { throw java.io.IOException("Request timeout has expired [url=$keyed, request_timeout=10000 ms] after $quoted") }
            val rpc = Rpc(keyed, Rpc20Driver(keyed, timeout), onFailure = { _, reason -> heard += reason })

            assertNull(runBlocking { rpc.balance(programId) })
            val unanswered = runBlocking { rpc.account(programId) } as Rpc.Answer.Unanswered

            assertEquals(
                "no answer from devnet.example-rpc.com: Request timeout has expired [url=devnet.example-rpc.com, request_timeout=10000 ms] after https://devnet.example-rpc.com:443",
                heard.first(),
            )
            assertTrue("SECRET" !in unanswered.message && heard.none { "SECRET" in it }, keyed)
        }
    }

    @Test
    fun `a read that fails still returns null, and the failure hook hears the method and why`() {
        val heard = mutableListOf<Pair<String, String>>()
        fun rpc(http: FakeHttp) = Rpc("http://rpc.test", Rpc20Driver("http://rpc.test", http), onFailure = { method, reason -> heard += method to reason })

        val limited = FakeHttp { """{"jsonrpc":"2.0","error":{"code":429,"message":"Too many requests for a specific RPC call"},"id":"x"}""" }
        assertNull(runBlocking { rpc(limited).memberships(programId, cohortKey) })
        assertNull(runBlocking { rpc(FakeHttp { throw java.io.IOException("offline") }).balance(programId) })
        assertTrue(runBlocking { rpc(limited).account(programId) } is Rpc.Answer.Refused)

        assertEquals(
            listOf(
                "getProgramAccounts" to "Too many requests for a specific RPC call",
                "getBalance" to "no answer from rpc.test: offline",
                "getAccountInfo" to "Too many requests for a specific RPC call",
            ),
            heard,
        )
    }

    @Test
    fun `a member that does not decode fails the read rather than vanishing from the roster`() {
        val bad = Base64.getEncoder().encodeToString(ByteArray(AccountLayout.Membership.ROSTER_SLICE_LENGTH).also { it[50] = 9 }) // state 9
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

    @Test
    fun `several accounts come in one call, in order, with a missing one as null and a wallet's lamports`() {
        val http =
            FakeHttp {
                ok(
                    """{"context":{"slot":9},"value":[{"data":["AQID","base64"],"lamports":7,"owner":"${programId.base58()}"},null,""" +
                        """{"data":["","base64"],"lamports":5,"owner":"11111111111111111111111111111111"}]}""",
                )
            }
        val read = runBlocking { rpcWith(http).multipleAccounts(listOf(cohortKey, programId, cohortKey)) }

        val accounts = read!!
        assertEquals(3, accounts.size)
        assertContentEquals(byteArrayOf(1, 2, 3), accounts[0]!!.data)
        assertEquals(7L, accounts[0]!!.lamports)
        assertNull(accounts[1])
        assertEquals(0, accounts[2]!!.data.size)
        assertEquals(5L, accounts[2]!!.lamports)
        assertEquals(programId, accounts[0]!!.owner)
        assertEquals(SolanaPublicKey.from("11111111111111111111111111111111"), accounts[2]!!.owner)
        val sent = Json.parseToJsonElement(http.sent.single()).jsonObject
        assertEquals("getMultipleAccounts", sent.getValue("method").jsonPrimitive.content)
    }

    @Test
    fun `an account at a token address holds its amount when the token program owns it, and 0 when another program does`() {
        val token = Instructions.TOKEN_PROGRAM
        val amount = ULong.MAX_VALUE - 1uL
        val full = ByteArray(72).also { java.nio.ByteBuffer.wrap(it, 64, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(amount.toLong()) }

        assertEquals(amount, Rpc.tokenAmountIn(Rpc.AccountInfo(full, 1, token)))
        assertNull(Rpc.tokenAmountIn(Rpc.AccountInfo(ByteArray(71), 1, token)), "a token account too short to be one is a failed read")
        assertEquals(0uL, Rpc.tokenAmountIn(Rpc.AccountInfo(ByteArray(0), 1000, SolanaPublicKey.from("11111111111111111111111111111111"))))
        assertEquals(0uL, Rpc.tokenAmountIn(Rpc.AccountInfo(full, 1, programId)), "another program's account holds no tokens, whatever its data")
    }

    @Test
    fun `several accounts that fail to answer, or answer short, are unknown`() {
        val refused = FakeHttp { """{"jsonrpc":"2.0","error":{"code":429,"message":"Too many requests"},"id":"x"}""" }
        assertNull(runBlocking { rpcWith(refused).multipleAccounts(listOf(cohortKey)) })
        val short = FakeHttp { ok("""{"context":{"slot":9},"value":[]}""") }
        assertNull(runBlocking { rpcWith(short).multipleAccounts(listOf(cohortKey)) })
    }

    @Test
    fun `more than a hundred accounts are asked for a hundred at a time`() {
        val http = FakeHttp { body -> ok("""{"context":{"slot":9},"value":[${List(Json.parseToJsonElement(body).jsonObject.getValue("params").jsonArray[0].jsonArray.size) { "null" }.joinToString(",")}]}""") }

        val read = runBlocking { rpcWith(http).multipleAccounts(List(Rpc.MULTIPLE_ACCOUNTS_MAX + 1) { cohortKey }) }

        assertEquals(Rpc.MULTIPLE_ACCOUNTS_MAX + 1, read!!.size)
        assertEquals(2, http.sent.size)
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

    // ---- sending (T062) --------------------------------------------------------

    private fun refused(message: String) = """{"jsonrpc":"2.0","error":{"code":-32002,"message":"$message"},"id":"x"}"""

    @Test
    fun `the blockhash comes from getLatestBlockhash at confirmed`() = runBlocking {
        val http = FakeHttp { ok("""{"context":{"slot":1},"value":{"blockhash":"EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N","lastValidBlockHeight":9}}""") }
        assertEquals("EkSnNWid2cvwEVnVx9aBqawnmiCNiDgp3gUdkDPTKN1N", rpcWith(http).latestBlockhash())
        val sent = Json.parseToJsonElement(http.sent.single()).jsonObject
        assertEquals("getLatestBlockhash", sent.getValue("method").jsonPrimitive.content)
        assertEquals("confirmed", sent.getValue("params").jsonArray[0].jsonObject.getValue("commitment").jsonPrimitive.content)
    }

    @Test
    fun `a send goes as base64 with preflight, and returns the signature`() = runBlocking {
        val http = FakeHttp { ok("\"5sig\"") }
        val tx = byteArrayOf(1, 2, 3)
        assertEquals(Rpc.Answer.Ok("5sig"), rpcWith(http).send(tx))
        val params = Json.parseToJsonElement(http.sent.single()).jsonObject.getValue("params").jsonArray
        assertEquals(Base64.getEncoder().encodeToString(tx), params[0].jsonPrimitive.content)
        assertEquals("base64", params[1].jsonObject.getValue("encoding").jsonPrimitive.content)
        assertEquals("confirmed", params[1].jsonObject.getValue("preflightCommitment").jsonPrimitive.content)
    }

    @Test
    fun `a refused send keeps the node's reason`() = runBlocking {
        val http = FakeHttp { refused("Transaction simulation failed: custom program error: 0x1771") }
        val answer = rpcWith(http).send(byteArrayOf(1))
        assertEquals(Rpc.Answer.Refused("Transaction simulation failed: custom program error: 0x1771"), answer)
    }

    @Test
    fun `a simulation reports the program's error and logs`() = runBlocking {
        val http = FakeHttp { ok("""{"context":{"slot":1},"value":{"err":{"InstructionError":[0,{"Custom":6001}]},"logs":["Program log: nope"]}}""") }
        val answer = rpcWith(http).simulate(byteArrayOf(1)) as Rpc.Answer.Ok
        assertEquals(listOf("Program log: nope"), answer.value.logs)
        assertTrue(answer.value.error!!.contains("6001"))
        val clean = FakeHttp { ok("""{"context":{"slot":1},"value":{"err":null,"logs":[]}}""") }
        assertEquals(Rpc.Answer.Ok(Rpc.Simulation(null, emptyList())), rpcWith(clean).simulate(byteArrayOf(1)))
    }

    @Test
    fun `a signature's landing reads pending, confirmed or failed`() = runBlocking {
        fun status(json: String) = runBlocking { rpcWith(FakeHttp { ok("""{"context":{"slot":1},"value":[$json]}""") }).landing("5sig") }
        assertEquals(Rpc.Landing.Pending, status("null"))
        assertEquals(Rpc.Landing.Pending, status("""{"err":null,"confirmationStatus":"processed"}"""))
        assertEquals(Rpc.Landing.Confirmed, status("""{"err":null,"confirmationStatus":"confirmed"}"""))
        assertEquals(Rpc.Landing.Confirmed, status("""{"err":null,"confirmationStatus":"finalized"}"""))
        assertTrue(status("""{"err":{"InstructionError":[0,{"Custom":6011}]},"confirmationStatus":"confirmed"}""") is Rpc.Landing.Failed)
        assertNull(rpcWith(FakeHttp { refused("down") }).landing("5sig"), "unknown, not pending")
    }

    @Test
    fun `a send with no answer is not a refusal - it may have landed`() = runBlocking {
        val timeout = FakeHttp { throw java.net.SocketTimeoutException("read timed out") }
        val answer = rpcWith(timeout).send(byteArrayOf(1))
        assertTrue(answer is Rpc.Answer.Unanswered, "a timeout is unknown, not refused: $answer")
        assertTrue((answer as Rpc.Answer.Unanswered).message.contains("read timed out"))

        val garbled = FakeHttp { ok("""{"not":"a signature"}""") }
        assertTrue(rpcWith(garbled).send(byteArrayOf(1)) is Rpc.Answer.Unanswered, "the node answered; it may have taken it")
    }

    @Test
    fun `an odd signature status reads as unknown, never an exception`() = runBlocking {
        for (value in listOf("""["not an object"]""", """[{"confirmationStatus":{"nested":1}}]""", "[]", "\"x\"")) {
            val http = FakeHttp { ok("""{"context":{"slot":1},"value":$value}""") }
            assertNull(rpcWith(http).landing("5sig"), value)
        }
    }

    @Test
    fun `cancellation is passed on, not read as a cluster that did not answer`() {
        val cancelled = FakeHttp { throw kotlinx.coroutines.CancellationException("screen left") }
        val rpc = rpcWith(cancelled)
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> { runBlocking { rpc.chainTime() } }
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> { runBlocking { rpc.accountData(programId) } }
        kotlin.test.assertFailsWith<kotlinx.coroutines.CancellationException> { runBlocking { rpc.send(byteArrayOf(1)) } }
    }

    @Test
    fun `an account read tells a missing account from a failed read`() = runBlocking {
        val missing = FakeHttp { ok("""{"context":{"slot":1},"value":null}""") }
        assertEquals(Rpc.Answer.Ok(null), rpcWith(missing).account(programId))
        assertNull(rpcWith(missing).accountData(programId))

        val down = FakeHttp { refused("Node is behind") }
        assertTrue(rpcWith(down).account(programId) is Rpc.Answer.Refused, "a failed read is not a missing account")
        assertNull(rpcWith(down).accountData(programId))

        val bytes = byteArrayOf(1, 2, 3)
        val found = FakeHttp { ok("""{"context":{"slot":1},"value":{"data":["${Base64.getEncoder().encodeToString(bytes)}","base64"]}}""") }
        assertContentEquals(bytes, (rpcWith(found).account(programId) as Rpc.Answer.Ok).value)
    }
}
