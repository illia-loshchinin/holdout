package holdout.core

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T091's part of T030/T031 — the instructions `crank init` sends, replayed
 * against `fixtures/instructions.json`, which `tests/fixtures/generate.ts`
 * writes from the same builders the LiteSVM suite runs against the program.
 * Bytes and account order must match exactly.
 */
class SetupInstructionsTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")

    private val fixtures: List<JsonObject> =
        Json
            .parseToJsonElement(RepoFiles.read("fixtures/instructions.json"))
            .jsonObject
            .getValue("instructions")
            .jsonArray
            .map { it.jsonObject }

    private fun fixture(name: String, where: (JsonObject) -> Boolean = { true }) = fixtures.first { it.getValue("name").jsonPrimitive.content == name && where(it.getValue("args").jsonObject) }

    private fun JsonObject.arg(name: String) = getValue("args").jsonObject.getValue(name).jsonPrimitive

    private fun JsonObject.accountAt(i: Int) = SolanaPublicKey.from(getValue("accounts").jsonArray[i].jsonObject.getValue("address").jsonPrimitive.content)

    /**
     * `@solana/kit`'s `AccountRole`: 0 read-only, 1 writable, 2 read-only
     * signer, 3 writable signer.
     */
    private fun role(meta: AccountMeta) = (if (meta.isSigner) 2 else 0) + (if (meta.isWritable) 1 else 0)

    private fun assertMatches(expected: JsonObject, ix: TransactionInstruction) {
        assertEquals(programId, ix.programId)
        assertContentEquals(RepoFiles.hex(expected.getValue("data").jsonPrimitive.content), ix.data, "data of ${expected["name"]}")
        val accounts = expected.getValue("accounts").jsonArray.map { it.jsonObject }
        assertEquals(accounts.map { it.getValue("address").jsonPrimitive.content }, ix.accounts.map { it.publicKey.base58() }, "account order")
        assertEquals(accounts.map { it.getValue("role").jsonPrimitive.int }, ix.accounts.map(::role), "account roles")
    }

    @Test
    fun `the loader's ProgramData and an associated token account derive as the TypeScript suite derives them`() = runBlocking {
        val init = fixture("initialize")
        assertEquals("J7HAVBVtxDEGrr6kcAfuit4WvNvEq6zwZJYiBr1DnNSG", Pdas.programData(programId).base58())
        // The fee destination in the fixture: the treasury's ATA for the stand-in mint.
        assertEquals(init.accountAt(7), Pdas.associatedToken(init.accountAt(6), init.accountAt(4)))
    }

    @Test
    fun `initialize replays the fixture byte for byte`() = runBlocking {
        val f = fixture("initialize")
        val ix =
            Instructions.initialize(
                programId = programId,
                // The generator's stand-ins: any keys do, the layout is what is pinned.
                admin = f.accountAt(0),
                skrMint = f.accountAt(4),
                operator = SolanaPublicKey.from(f.arg("operator").content),
                sponsor = SolanaPublicKey.from(f.arg("sponsor").content),
                treasuryWallet = SolanaPublicKey.from(f.arg("treasuryWallet").content),
                faucet =
                FaucetParams(
                    solTopUp = f.arg("faucetSolTopUp").content.toULong(),
                    dailyLamportCap = f.arg("faucetDailyLamportCap").content.toULong(),
                    sponsorFloat = f.arg("faucetSponsorFloat").content.toULong(),
                ),
            )
        assertMatches(f, ix)
    }

    @Test
    fun `create_league replays the fixture byte for byte`() = runBlocking {
        val f = fixture("create_league")
        val params =
            LeagueParams(
                leagueId = f.arg("leagueId").int,
                durationDays = f.arg("durationDays").int,
                repsPerDay = f.arg("repsPerDay").int,
                exercise = f.arg("exercise").int,
                firstStart = f.arg("firstStart").long,
                dayLength = f.arg("dayLength").long,
                cadence = f.arg("cadence").long,
                joinLock = f.arg("joinLock").long,
            )
        assertMatches(f, Instructions.createLeague(programId, f.accountAt(0), params))
    }

    @Test
    fun `the judged leagues are the Daily 25 on the devnet clock`() {
        val first = LeagueParams.FIRST_JUDGED_START
        val seven = LeagueParams.judged(LeagueParams.LEAGUE_7D, first)
        val twentyOne = LeagueParams.judged(LeagueParams.LEAGUE_21D, first)
        assertEquals(listOf(7, 21), listOf(seven.durationDays, twentyOne.durationDays))
        listOf(seven, twentyOne).forEach {
            assertEquals(25, it.repsPerDay)
            assertEquals(0, it.exercise)
            assertEquals(86_400L, it.dayLength)
            assertEquals(604_800L, it.cadence)
            assertEquals(3_600L, it.joinLock)
        }
        // The fixture's first start: Monday 28 September 2026, 05:00 UTC.
        assertEquals(fixture("create_league").arg("firstStart").long, first)
        assertFailsWith<IllegalArgumentException> { LeagueParams.judged(2, first) }
    }

    @Test
    fun `a first start must be a Monday at 0500 UTC, as the program checks`() {
        val first = LeagueParams.FIRST_JUDGED_START
        assertTrue(LeagueParams.isMonday0500Utc(first))
        assertTrue(LeagueParams.isMonday0500Utc(first + 3 * 604_800L))
        assertTrue(LeagueParams.isMonday0500Utc(first - 604_800L))
        assertFalse(LeagueParams.isMonday0500Utc(first + 1))
        assertFalse(LeagueParams.isMonday0500Utc(first - 3_600))
        assertFalse(LeagueParams.isMonday0500Utc(first + 86_400))
    }

    @Test
    fun `a system transfer is instruction 2 with the lamports, from a paying signer`() {
        val from = SolanaPublicKey(ByteArray(32) { 1 })
        val to = SolanaPublicKey(ByteArray(32) { 2 })
        val ix = Instructions.systemTransfer(from, to, 1_000_000_000uL)
        assertEquals(Instructions.SYSTEM_PROGRAM, ix.programId)
        assertContentEquals(byteArrayOf(2, 0, 0, 0, 0, 0xCA.toByte(), 0x9A.toByte(), 0x3B, 0, 0, 0, 0), ix.data)
        assertEquals(listOf(3, 1), ix.accounts.map(::role))
    }

    @Test
    fun `a stored league decodes as the fixture says`() {
        val leagues =
            Json
                .parseToJsonElement(RepoFiles.read("fixtures/accounts.json"))
                .jsonObject
                .getValue("accounts")
                .jsonArray
                .map { it.jsonObject }
                .filter { it.getValue("name").jsonPrimitive.content == "LeagueConfig" }
        assertEquals(2, leagues.size)
        leagues.forEach { f ->
            val fields = f.getValue("fields").jsonObject
            val league = LeagueSnapshot.decode(RepoFiles.hex(f.getValue("bytes").jsonPrimitive.content))
            assertEquals(LeagueParams.judged(fields.getValue("leagueId").jsonPrimitive.int, fields.getValue("firstStart").jsonPrimitive.content.toLong()), league.params)
            assertEquals(fields.getValue("nextIndex").jsonPrimitive.long, league.nextIndex)
        }
        assertFailsWith<IllegalArgumentException> { LeagueSnapshot.decode(ByteArray(50)) }
    }
}
