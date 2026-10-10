package holdout.core

import com.solana.networking.HttpNetworkDriver
import com.solana.networking.HttpRequest
import com.solana.networking.Rpc20Driver
import com.solana.publickey.SolanaPublicKey
import holdout.core.SeekerGenesisReader.Companion.SEEKER_GENESIS_GROUP
import holdout.core.SeekerGenesisReader.Companion.TOKEN_2022_PROGRAM
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Seeker Genesis Token check over the wire (spec FR-003b–FR-003d,
 * research R17): the two reads it sends to mainnet and what each answer
 * becomes. No network: the HTTP layer is a fake that records each request and
 * answers from a script, as in `SeekerNameReaderTest`.
 */
class SeekerGenesisReaderTest {

    private val wallet = SolanaPublicKey(ByteArray(32) { 0x11 })
    private val sgt = SolanaPublicKey(ByteArray(32) { 0x22 }).base58()
    private val other = SolanaPublicKey(ByteArray(32) { 0x33 }).base58()

    private class FakeHttp(private val reply: suspend (method: String, body: String) -> String) : HttpNetworkDriver {
        val sent = mutableListOf<String>()

        override suspend fun makeHttpRequest(request: HttpRequest): String {
            val body = request.body ?: ""
            sent += body
            return reply(Json.parseToJsonElement(body).jsonObject.getValue("method").jsonPrimitive.content, body)
        }
    }

    private fun readerWith(http: FakeHttp, timeoutMillis: Long = 10_000) = SeekerGenesisReader("http://mainnet.test", Rpc20Driver("http://mainnet.test", http), timeoutMillis)

    private fun ok(result: String) = """{"jsonrpc":"2.0","result":$result,"id":"x"}"""

    /** `getTokenAccountsByOwner` holding `amount` of each mint. */
    /** The token account holding the i-th mint in [tokenAccounts]. */
    private fun holder(i: Int) = SolanaPublicKey(ByteArray(32) { (0x40 + i).toByte() })

    private fun tokenAccounts(vararg held: Pair<String, String>) = ok(
        """{"context":{"slot":1},"value":""" +
            held.withIndex().joinToString(",", "[", "]") { (i, pair) ->
                val (mint, amount) = pair
                """{"pubkey":"${holder(i).base58()}","account":{"data":{"parsed":{"info":{"isNative":false,"mint":"$mint","owner":"${wallet.base58()}","state":"initialized","tokenAmount":{"amount":"$amount","decimals":0,"uiAmount":1.0,"uiAmountString":"$amount"}},"type":"account"},"program":"spl-token-2022","space":170},"executable":false,"lamports":2074080,"owner":"$TOKEN_2022_PROGRAM","rentEpoch":0,"space":170}}"""
            } + "}",
    )

    /** `getMultipleAccounts` on mints, each a member of `group`, or with no group member extension when null. */
    private fun mints(vararg mintAndGroup: Pair<String, String?>, owner: String = TOKEN_2022_PROGRAM) = ok(
        """{"context":{"slot":1},"value":""" +
            mintAndGroup.joinToString(",", "[", "]") { (mint, group) ->
                val member = group?.let { """,{"extension":"tokenGroupMember","state":{"group":"$it","memberNumber":7,"mint":"$mint"}}""" } ?: ""
                """{"data":{"parsed":{"info":{"decimals":0,"extensions":[{"extension":"metadataPointer","state":{"authority":null,"metadataAddress":"$SEEKER_GENESIS_GROUP"}}$member],"freezeAuthority":null,"isInitialized":true,"mintAuthority":null,"supply":"1"},"type":"mint"},"program":"spl-token-2022","space":400},"executable":false,"lamports":1,"owner":"$owner","rentEpoch":0,"space":400}"""
            } + "}",
    )

    @Test
    fun `a wallet holding a Seeker Genesis Token is found in two reads`() = runBlocking {
        val http = FakeHttp { method, _ -> if (method == "getTokenAccountsByOwner") tokenAccounts(other to "5", sgt to "1") else mints(other to null, sgt to SEEKER_GENESIS_GROUP) }

        assertEquals(GenesisCheck.Holds(SolanaPublicKey.from(sgt), holder(1)), readerWith(http).check(wallet), "the mint and the account holding it")

        // The two reads, checked against the literal requests the whole check rests on.
        assertEquals(2, http.sent.size)
        val search = Json.parseToJsonElement(http.sent[0]).jsonObject
        assertEquals("getTokenAccountsByOwner", search.getValue("method").jsonPrimitive.content)
        assertEquals(
            Json.parseToJsonElement("""["${wallet.base58()}",{"programId":"TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"},{"encoding":"jsonParsed","commitment":"confirmed"}]"""),
            search.getValue("params"),
        )
        val read = Json.parseToJsonElement(http.sent[1]).jsonObject
        assertEquals("getMultipleAccounts", read.getValue("method").jsonPrimitive.content)
        assertEquals(
            Json.parseToJsonElement("""[["$other","$sgt"],{"encoding":"jsonParsed","commitment":"confirmed"}]"""),
            read.getValue("params"),
        )
    }

    @Test
    fun `a wallet with no Token-2022 accounts holds none, after one read`() = runBlocking {
        val http = FakeHttp { _, _ -> tokenAccounts() }

        assertEquals(GenesisCheck.None, readerWith(http).check(wallet))
        assertEquals(1, http.sent.size)
    }

    @Test
    fun `an emptied account is not a Seeker Genesis Token`() = runBlocking {
        val http = FakeHttp { _, _ -> tokenAccounts(sgt to "0") }

        assertEquals(GenesisCheck.None, readerWith(http).check(wallet))
        assertEquals(1, http.sent.size, "the mint of an emptied account is never read")
    }

    @Test
    fun `a mint of another group, or with no group member extension, holds none`() = runBlocking {
        val otherGroup = FakeHttp { method, _ -> if (method == "getTokenAccountsByOwner") tokenAccounts(sgt to "1") else mints(sgt to other) }
        assertEquals(GenesisCheck.None, readerWith(otherGroup).check(wallet))

        val noExtension = FakeHttp { method, _ -> if (method == "getTokenAccountsByOwner") tokenAccounts(sgt to "1") else mints(sgt to null) }
        assertEquals(GenesisCheck.None, readerWith(noExtension).check(wallet))
    }

    @Test
    fun `a mint not owned by Token-2022 holds none`() = runBlocking {
        val http = FakeHttp { method, _ ->
            if (method == "getTokenAccountsByOwner") tokenAccounts(sgt to "1") else mints(sgt to SEEKER_GENESIS_GROUP, owner = other)
        }
        assertEquals(GenesisCheck.None, readerWith(http).check(wallet))
    }

    @Test
    fun `a refusal is unknown, and a 429 says so`() = runBlocking {
        val refused = FakeHttp { _, _ -> """{"jsonrpc":"2.0","error":{"code":-32005,"message":"busy"},"id":"x"}""" }
        assertEquals(GenesisCheck.Unknown(rateLimited = false), readerWith(refused).check(wallet))

        val limited = FakeHttp { _, _ -> """{"jsonrpc":"2.0","error":{"code":429,"message":"Too many requests"},"id":"x"}""" }
        assertEquals(GenesisCheck.Unknown(rateLimited = true), readerWith(limited).check(wallet))
    }

    @Test
    fun `an unreachable node, or one answering garbage, is unknown`() = runBlocking {
        val unreachable = FakeHttp { _, _ -> throw IOException("Unable to resolve host") }
        assertEquals(GenesisCheck.Unknown(rateLimited = false), readerWith(unreachable).check(wallet))

        val garbage = FakeHttp { _, _ -> "<html>502 Bad Gateway</html>" }
        assertEquals(GenesisCheck.Unknown(rateLimited = false), readerWith(garbage).check(wallet))
    }

    @Test
    fun `an answer not in the parsed shape is unknown, never none`() = runBlocking {
        val base64Token = FakeHttp { _, _ ->
            ok("""{"context":{"slot":1},"value":[{"pubkey":"${holder(0).base58()}","account":{"data":["AAAA","base64"],"executable":false,"lamports":1,"owner":"$TOKEN_2022_PROGRAM","rentEpoch":0,"space":170}}]}""")
        }
        assertEquals(GenesisCheck.Unknown(rateLimited = false), readerWith(base64Token).check(wallet), "a token account the node did not parse")

        val base64Mint = FakeHttp { method, _ ->
            if (method == "getTokenAccountsByOwner") {
                tokenAccounts(sgt to "1")
            } else {
                ok("""{"context":{"slot":1},"value":[{"data":["AAAA","base64"],"executable":false,"lamports":1,"owner":"$TOKEN_2022_PROGRAM","rentEpoch":0,"space":400}]}""")
            }
        }
        assertEquals(GenesisCheck.Unknown(rateLimited = false), readerWith(base64Mint).check(wallet), "a mint the node did not parse")

        val short = FakeHttp { method, _ -> if (method == "getTokenAccountsByOwner") tokenAccounts(other to "1", sgt to "1") else mints(other to null) }
        assertEquals(GenesisCheck.Unknown(rateLimited = false), readerWith(short).check(wallet), "fewer mints answered than asked for")
    }

    @Test
    fun `a node too slow to answer is unknown, never none`() = runBlocking {
        val slow = FakeHttp { _, _ ->
            delay(5_000)
            tokenAccounts()
        }
        assertEquals(GenesisCheck.Unknown(rateLimited = false), readerWith(slow, timeoutMillis = 50).check(wallet))
    }
}
