package holdout.core

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.lang.reflect.Modifier
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Seeker ID lookups over the wire (contracts §1, §3.2): the two requests the
 * reader sends to the names endpoint and what each answer becomes. No
 * network: the HTTP layer is a fake that records each request body and
 * answers from a script, as in `RpcTest`.
 */
class SeekerNameReaderTest {

    private val wallet = SolanaPublicKey(ByteArray(32) { 0x11 })

    /** Answers each JSON-RPC call by its method, and keeps what was sent. */
    private class FakeHttp(private val reply: suspend (method: String, body: String) -> String) : HttpNetworkDriver {
        val sent = mutableListOf<String>()

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            val body = request.body ?: ""
            sent += body
            return reply(Json.parseToJsonElement(body).jsonObject.getValue("method").jsonPrimitive.content, body)
        }
    }

    private fun readerWith(http: FakeHttp, timeoutMillis: Long = 10_000) = SeekerNameReader("http://names.test", Rpc20Driver("http://names.test", http), timeoutMillis)

    private fun ok(result: String) = """{"jsonrpc":"2.0","result":$result,"id":"x"}"""

    private fun nameAccounts(vararg keys: SolanaPublicKey) = ok(
        keys.joinToString(",", "[", "]") {
            """{"pubkey":"${it.base58()}","account":{"data":["","base64"],"executable":false,"lamports":1,"owner":"ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK","rentEpoch":0,"space":0}}"""
        },
    )

    private fun reverseRecords(vararg names: String?) = ok(
        """{"context":{"slot":1},"value":""" +
            names.joinToString(",", "[", "]") { name ->
                if (name == null) {
                    "null"
                } else {
                    val data = Base64.getEncoder().encodeToString(ByteArray(200) + name.encodeToByteArray() + ByteArray(8))
                    """{"data":["$data","base64"],"executable":false,"lamports":1,"owner":"ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK","rentEpoch":0,"space":${200 + name.length + 8}}"""
                }
            } + "}",
    )

    private fun account(n: Int) = SolanaPublicKey(ByteArray(32) { n.toByte() })

    @Test
    fun `a wallet with a name is searched once, then its reverse records are read in one call`() = runBlocking {
        val named = account(0x22)
        val http = FakeHttp { method, _ -> if (method == "getProgramAccounts") nameAccounts(named) else reverseRecords("alice") }

        assertEquals(Lookup.Found("alice.skr"), readerWith(http).nameOf(wallet))

        assertEquals(2, http.sent.size)
        val search = Json.parseToJsonElement(http.sent[0]).jsonObject
        assertEquals(SeekerNames.ownerSearchParams(wallet), search.getValue("params"))
        val read = Json.parseToJsonElement(http.sent[1]).jsonObject
        assertEquals("getMultipleAccounts", read.getValue("method").jsonPrimitive.content)
        assertEquals(
            listOf(SeekerNames.reverseRecordAddress(named).base58()),
            read.getValue("params").jsonArray[0].jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `several names show the first by the fixed rule`() = runBlocking {
        val http = FakeHttp { method, _ ->
            if (method == "getProgramAccounts") nameAccounts(account(1), account(2), account(3)) else reverseRecords("zed", null, "Bob")
        }
        assertEquals(Lookup.Found("Bob.skr"), readerWith(http).nameOf(wallet))
    }

    @Test
    fun `a wallet with no skr name account has no Seeker ID, and nothing more is read`() = runBlocking {
        val http = FakeHttp { _, _ -> ok("[]") }
        assertEquals(Lookup.None, readerWith(http).nameOf(wallet))
        assertEquals(1, http.sent.size)
    }

    @Test
    fun `name accounts whose reverse records are missing or invalid give no Seeker ID`() = runBlocking {
        val http = FakeHttp { method, _ ->
            if (method == "getProgramAccounts") nameAccounts(account(1), account(2)) else reverseRecords(null, "ali‮ce")
        }
        assertEquals(Lookup.None, readerWith(http).nameOf(wallet))
    }

    @Test
    fun `reverse records are read at most 100 to a call`() = runBlocking {
        val keys = (1..150).map { account(it) }.toTypedArray()
        val http = FakeHttp { method, body ->
            if (method == "getProgramAccounts") {
                nameAccounts(*keys)
            } else {
                val asked = Json.parseToJsonElement(body).jsonObject.getValue("params").jsonArray[0].jsonArray.size
                reverseRecords(*arrayOfNulls<String>(asked))
            }
        }
        assertEquals(Lookup.None, readerWith(http).nameOf(wallet))
        val reads = http.sent.drop(1).map { Json.parseToJsonElement(it).jsonObject.getValue("params").jsonArray[0].jsonArray.size }
        assertEquals(listOf(100, 50), reads)
    }

    @Test
    fun `a refusal for too many requests is unknown and rate-limited`() = runBlocking {
        val http = FakeHttp { _, _ -> """{"jsonrpc":"2.0","error":{"code":429,"message":"Too many requests for a specific RPC call"},"id":"x"}""" }
        assertEquals(Lookup.Unknown(rateLimited = true), readerWith(http).nameOf(wallet))
    }

    @Test
    fun `any other failure is unknown, never no name`() = runBlocking {
        val rpcError = FakeHttp { _, _ -> """{"jsonrpc":"2.0","error":{"code":-32010,"message":"excluded from account secondary indexes"},"id":"x"}""" }
        assertEquals(Lookup.Unknown(rateLimited = false), readerWith(rpcError).nameOf(wallet))

        val unreachable = FakeHttp { _, _ -> throw IOException("unreachable") }
        assertEquals(Lookup.Unknown(rateLimited = false), readerWith(unreachable).nameOf(wallet))

        val slow = FakeHttp { _, _ ->
            delay(5_000)
            ok("[]")
        }
        assertEquals(Lookup.Unknown(rateLimited = false), readerWith(slow, timeoutMillis = 50).nameOf(wallet))

        val failingRead = FakeHttp { method, _ -> if (method == "getProgramAccounts") nameAccounts(account(1)) else throw IOException("dropped") }
        assertEquals(Lookup.Unknown(rateLimited = false), readerWith(failingRead).nameOf(wallet))
    }

    /**
     * Mainnet's own answers for a real Seeker, recorded on 2026-10-09: the
     * wallet `CkJn…jfWE` owns one `.skr` name account, whose reverse record
     * (derived here, and asked for by address) holds "illai".
     */
    @Test
    fun `the recorded mainnet answers for a real Seeker resolve to its name`() = runBlocking {
        val seeker = SolanaPublicKey.from("CkJn1a53c1ZXe5XLZ38ZgsPa1WX28PUiMWHqtJayjfWE")
        val http = FakeHttp { method, _ ->
            when (method) {
                "getProgramAccounts" -> ok(
                    """[{"pubkey":"3exaN8mJ3m3sBWWgg2zXWTX2RABUSWZwge6fLApxDdJt","account":{"lamports":2282880,"data":["","base64"],"owner":"ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK","executable":false,"rentEpoch":18446744073709551615,"space":200}}]""",
                )
                else -> ok(
                    """{"context":{"apiVersion":"4.3.0","slot":454943087},"value":[{"data":["REhYLA+nZ/MAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAADLNyGaxO//RCF0Rl1kGc3U+b+1pe+kjdy1basUzfH67Ms3IZrE7/9EIXRGXWQZzdT5v7Wl76SN3LVtqxTN8frsAAAAAAAAAADWivmgAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABpbGxhaQ==","base64"],"executable":false,"lamports":2317680,"owner":"ALTNSZ46uaAUU7XUV6awvdorLGqAsPwa9shm7h4uP2FK","rentEpoch":18446744073709551615,"space":205}]}""",
                )
            }
        }
        assertEquals(Lookup.Found("illai.skr"), readerWith(http).nameOf(seeker))
        val read = Json.parseToJsonElement(http.sent[1]).jsonObject
        assertEquals(
            listOf("5CrGdLbhr5vTS6mKmpRot2pf3VcqTyvfodSqgSHhyxrh"),
            read.getValue("params").jsonArray[0].jsonArray.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun `the reader can only look names up`() {
        val public = SeekerNameReader::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()
        assertEquals(setOf("nameOf"), public)
    }
}
