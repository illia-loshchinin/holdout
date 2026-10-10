package holdout.core

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * T031 — every fixture `tests/fixtures/generate.ts` writes, replayed through
 * the Kotlin client. Instructions are rebuilt from their arguments and must
 * match byte for byte, account for account; accounts are decoded and must read
 * back every recorded field. The generator uses the same builders and layouts
 * the LiteSVM suite runs against the program, so a drift on either side fails
 * here.
 *
 * Accounts are pinned by decoding, not by re-encoding: nothing off-chain writes
 * a program account, so the client has no encoder to replay (see `Accounts.kt`).
 */
class FixtureReplayTest {
    private val programId = SolanaPublicKey.from("Ho1d924DTXrCgVfbW1U2NkAc3rGk1F723weN8rduyoy4")

    private fun load(file: String, key: String): List<JsonObject> = Json
        .parseToJsonElement(RepoFiles.read(file))
        .jsonObject
        .getValue(key)
        .jsonArray
        .map { it.jsonObject }

    private val instructions = load("fixtures/instructions.json", "instructions")
    private val accounts = load("fixtures/accounts.json", "accounts")

    private fun JsonObject.args() = getValue("args").jsonObject

    private fun JsonObject.accountAt(i: Int) = SolanaPublicKey.from(getValue("accounts").jsonArray[i].jsonObject.getValue("address").jsonPrimitive.content)

    private fun JsonObject.key(name: String) = SolanaPublicKey.from(getValue(name).jsonPrimitive.content)

    private fun JsonObject.optionalKey(name: String) = get(name)?.takeUnless { it is JsonNull }?.let { SolanaPublicKey.from(it.jsonPrimitive.content) }

    /** `@solana/kit`'s `AccountRole`: 0 read-only, 1 writable, 2 read-only signer, 3 writable signer. */
    private fun role(meta: AccountMeta) = (if (meta.isSigner) 2 else 0) + (if (meta.isWritable) 1 else 0)

    private fun assertMatches(expected: JsonObject, ix: TransactionInstruction, roles: (List<Int>) -> List<Int> = { it }) {
        val name = expected.getValue("name").jsonPrimitive.content
        assertEquals(programId, ix.programId, name)
        assertContentEquals(RepoFiles.hex(expected.getValue("data").jsonPrimitive.content), ix.data, "data of $name")
        val metas = expected.getValue("accounts").jsonArray.map { it.jsonObject }
        assertEquals(metas.map { it.getValue("address").jsonPrimitive.content }, ix.accounts.map { it.publicKey.base58() }, "account order of $name")
        assertEquals(roles(metas.map { it.getValue("role").jsonPrimitive.int }), ix.accounts.map(::role), "account roles of $name")
    }

    /** Rebuilds one instruction fixture with the Kotlin builder of the same name, reading the stand-in keys off its accounts. */
    private suspend fun rebuild(f: JsonObject): TransactionInstruction {
        val a = f.args()
        return when (val name = f.getValue("name").jsonPrimitive.content) {
            "initialize" -> Instructions.initialize(
                programId,
                f.accountAt(0),
                f.accountAt(4),
                a.key("operator"),
                a.key("sponsor"),
                a.key("treasuryWallet"),
                a.key("developerWallet"),
                FaucetParams(
                    a.getValue("faucetSolTopUp").jsonPrimitive.content.toULong(),
                    a.getValue("faucetDailyLamportCap").jsonPrimitive.content.toULong(),
                    a.getValue("faucetSponsorFloat").jsonPrimitive.content.toULong(),
                ),
            )
            "create_league" -> Instructions.createLeague(
                programId,
                f.accountAt(0),
                f.accountAt(3),
                LeagueParams(
                    a.getValue("leagueId").jsonPrimitive.int,
                    a.getValue("durationDays").jsonPrimitive.int,
                    a.getValue("repsPerDay").jsonPrimitive.int,
                    a.getValue("exercise").jsonPrimitive.int,
                    a.getValue("form").jsonPrimitive.int,
                    a.getValue("firstStart").jsonPrimitive.content.toLong(),
                    a.getValue("dayLength").jsonPrimitive.content.toLong(),
                    a.getValue("cadence").jsonPrimitive.content.toLong(),
                    a.getValue("joinLock").jsonPrimitive.content.toLong(),
                ),
            )
            "set_flags" -> Instructions.setFlags(programId, f.accountAt(0), a.getValue("genesisGateEnabled").jsonPrimitive.boolean)
            "publish_chip_set" -> Instructions.publishChipSet(
                programId,
                f.accountAt(0),
                f.accountAt(1),
                a.getValue("amounts").jsonArray.map { it.jsonPrimitive.content.toULong() },
                a.getValue("validUntil").jsonPrimitive.content.toLong(),
            )
            "open_cohort" -> Instructions.openCohort(
                programId,
                f.accountAt(0),
                f.accountAt(6),
                a.getValue("leagueId").jsonPrimitive.int,
                a.getValue("index").jsonPrimitive.content.toLong(),
                a.optionalKey("previousCohort"),
            )
            "join_cohort" -> Instructions.joinCohort(programId, f.accountAt(0), f.accountAt(8), f.accountAt(2), a.getValue("tier").jsonPrimitive.int, a.optionalKey("runningCohort"))
            "leave_cohort" -> Instructions.leaveCohort(programId, f.accountAt(0), mintOf(f), f.accountAt(2))
            "submit_day" -> Instructions.submitDay(programId, f.accountAt(0), f.accountAt(1), a.getValue("dayIndex").jsonPrimitive.int, RepoFiles.hex(a.getValue("proofHash").jsonPrimitive.content))
            "record_forfeits" -> Instructions.recordForfeits(programId, f.accountAt(0), f.accountAt(4), f.accountAt(5), f.accountAt(2), pairsOf(f, from = 7, count = a.getValue("pairs").jsonPrimitive.int))
            "settle_count" -> Instructions.settleCount(
                programId,
                f.accountAt(0),
                f.accountAt(5),
                f.accountAt(6),
                leagueOf(f),
                f.accountAt(3),
                pairsOf(f, from = 9, count = a.getValue("pairs").jsonPrimitive.int),
                a.optionalKey("previousCohort"),
            )
            "settle_finalize" -> Instructions.settleFinalize(programId, f.accountAt(0), f.accountAt(5), f.accountAt(6), leagueOf(f), f.accountAt(3), a.optionalKey("previousCohort"))
            "claim_payout" -> Instructions.claimPayout(programId, f.accountAt(0), mintOf(f), f.accountAt(2))
            "claim_faucet" -> Instructions.claimFaucet(programId, f.accountAt(0), a.key("recipient"), f.accountAt(4), f.accountAt(8))
            "top_up_sponsor" -> Instructions.topUpSponsor(programId, f.accountAt(0), f.accountAt(3))
            else -> error("no Kotlin builder for $name")
        }
    }

    /** The fixtures' member uses the same stand-in mint as `join_cohort`, whose accounts name it. */
    private fun mintOf(@Suppress("UNUSED_PARAMETER") f: JsonObject) = instructions.first { it.getValue("name").jsonPrimitive.content == "join_cohort" }.accountAt(8)

    /**
     * The generator settles cohorts of league 0; the league PDA sits third (and
     * `settle_finalize`'s reserve vault eighth), both checked by the replay itself.
     */
    private fun leagueOf(@Suppress("UNUSED_PARAMETER") f: JsonObject) = 0

    private fun pairsOf(f: JsonObject, from: Int, count: Int) = List(count) { Instructions.MemberPair(f.accountAt(from + 2 * it), f.accountAt(from + 2 * it + 1)) }

    @Test
    fun `every instruction fixture rebuilds byte for byte`() = runBlocking {
        val names = instructions.map { it.getValue("name").jsonPrimitive.content }.toSet()
        assertEquals(
            setOf(
                "initialize", "create_league", "set_flags", "publish_chip_set", "open_cohort", "join_cohort", "leave_cohort", "submit_day",
                "record_forfeits", "settle_count", "settle_finalize", "claim_payout", "claim_faucet", "top_up_sponsor",
            ),
            names,
            "an instruction the program gained needs a Kotlin builder and a case here",
        )
        instructions.forEach { f ->
            val ix = rebuild(f)
            if (f.getValue("name").jsonPrimitive.content == "publish_chip_set") {
                // The one deliberate difference: in the crank the operator also pays the fee, so the
                // builder marks it writable. The program only requires a signer (InstructionsTest).
                assertMatches(f, ix) { roles -> listOf(3) + roles.drop(1) }
            } else {
                assertMatches(f, ix)
            }
        }
    }

    @Test
    fun `every PDA fixture derives to its recorded address`() = runBlocking {
        val pdas = Json.parseToJsonElement(RepoFiles.read("fixtures/pdas.json")).jsonObject.getValue("pdas").jsonArray.map { it.jsonObject }
        assertTrue(pdas.isNotEmpty())
        pdas.forEach { p ->
            val seeds = p.getValue("seeds").jsonArray.map { it.jsonPrimitive }
            fun n(i: Int) = seeds[i].content.toInt()
            fun k(i: Int) = SolanaPublicKey.from(seeds[i].content)
            val derived =
                when (val name = p.getValue("name").jsonPrimitive.content) {
                    "config" -> Pdas.config(programId)
                    "league" -> Pdas.league(programId, n(1))
                    "reserve" -> Pdas.reserveVault(programId, n(1))
                    "cohort" -> Pdas.cohort(programId, n(1), n(2).toLong())
                    "vault" -> Pdas.vault(programId, k(1))
                    "member" -> Pdas.membership(programId, k(1), k(2))
                    "profile" -> Pdas.profile(programId, k(1))
                    "faucet_vault" -> Pdas.faucetVault(programId)
                    "mint_authority" -> Pdas.mintAuthority(programId)
                    else -> error("no Kotlin derivation for the PDA $name")
                }
            assertEquals(p.getValue("address").jsonPrimitive.content, derived.base58(), "${p.getValue("name")} ${p.getValue("seeds")}")
        }
    }

    @Test
    fun `the builders refuse what the program would`() = runBlocking {
        val cohort = SolanaPublicKey(ByteArray(32) { 3 })
        val wallet = SolanaPublicKey(ByteArray(32) { 4 })
        assertFailsWith<IllegalArgumentException> { Instructions.joinCohort(programId, wallet, wallet, cohort, 4) }
        assertFailsWith<IllegalArgumentException> { Instructions.submitDay(programId, wallet, cohort, 0, ByteArray(31)) }
        assertFailsWith<IllegalArgumentException> { Instructions.submitDay(programId, wallet, cohort, 256, ByteArray(32)) }
        val pair = Instructions.MemberPair.of(programId, cohort, wallet)
        assertFailsWith<IllegalArgumentException> { Instructions.recordForfeits(programId, wallet, wallet, wallet, cohort, emptyList()) }
        assertFailsWith<IllegalArgumentException> { Instructions.settleCount(programId, wallet, wallet, wallet, 0, cohort, List(11) { pair }) }
        assertEquals(Pdas.membership(programId, cohort, wallet), pair.membership)
        assertEquals(Pdas.profile(programId, wallet), pair.profile)
    }

    private fun JsonObject.u(name: String) = getValue(name).jsonPrimitive.content.toULong()

    private fun JsonObject.l(name: String) = getValue(name).jsonPrimitive.content.toLong()

    private fun JsonObject.i(name: String) = getValue(name).jsonPrimitive.int

    private fun JsonObject.b(name: String) = getValue(name).jsonPrimitive.boolean

    private fun JsonElement.ulongs() = jsonArray.map { it.jsonPrimitive.content.toULong() }

    private fun JsonElement.longs() = jsonArray.map { it.jsonPrimitive.content.toLong() }

    private fun fields(name: String) = accounts.filter { it.getValue("name").jsonPrimitive.content == name }.map { it.getValue("fields").jsonObject to RepoFiles.hex(it.getValue("bytes").jsonPrimitive.content) }

    @Test
    fun `every account fixture decodes to its recorded fields`() {
        assertEquals(setOf("Config", "LeagueConfig", "Cohort", "Membership", "Profile"), accounts.map { it.getValue("name").jsonPrimitive.content }.toSet())

        fields("Config").forEach { (f, bytes) ->
            val c = ConfigSnapshot.decode(bytes)
            assertEquals(
                listOf(f.key("admin"), f.key("operator"), f.key("sponsor"), f.key("skrMint"), f.key("feeDestination"), f.key("developerDestination")),
                listOf(c.admin, c.operator, c.sponsor, c.skrMint, c.feeDestination, c.developerDestination),
            )
            assertEquals(f.b("genesisGateEnabled"), c.genesisGateEnabled)
            val chips = f.getValue("chipSet").jsonObject
            assertEquals(chips.getValue("amounts").ulongs(), c.chipAmounts)
            assertEquals(chips.l("version"), c.chipVersion)
            assertEquals(chips.u("maxTopTier"), c.maxTopTier)
            assertEquals(chips.l("publishedAt"), c.chipPublishedAt)
            val faucet = f.getValue("faucet").jsonObject
            assertEquals(listOf(faucet.u("solTopUp"), faucet.u("sponsorFloat"), faucet.u("dailyLamportCap"), faucet.u("lamportsToday")), listOf(c.solTopUp, c.sponsorFloat, c.dailyLamportCap, c.lamportsToday))
            assertEquals(faucet.l("lamportDay"), c.lamportDay)
        }

        fields("Cohort").forEach { (f, bytes) ->
            val c = CohortSnapshot.decode(bytes)
            assertEquals(f.i("leagueId"), c.leagueId)
            assertEquals(f.l("index"), c.index)
            assertEquals(listOf(f.l("start"), f.l("joinCutoff"), f.l("dayLength")), listOf(c.start, c.joinCutoff, c.dayLength))
            assertEquals(listOf(f.i("durationDays"), f.i("repsPerDay"), f.i("exercise"), f.i("form")), listOf(c.durationDays, c.repsPerDay, c.exercise, c.form))
            assertEquals(f.getValue("chips").ulongs(), c.chips)
            assertEquals(listOf(f.l("memberCount"), f.l("forfeitsRecorded"), f.l("counted"), f.l("finisherCount")), listOf(c.memberCount, c.forfeitsRecorded, c.counted, c.finisherCount))
            assertEquals(listOf(f.u("heldStake"), f.u("pool"), f.u("boostDue"), f.u("comebackDue"), f.u("totalFinisherStake")), listOf(c.heldStake, c.pool, c.boostDue, c.comebackDue, c.totalFinisherStake))
            assertEquals(f.getValue("finishersPerTier").longs(), c.finishersPerTier)
            assertEquals(f.b("settled"), c.settled)
            assertEquals(f.getValue("sharePerTier").ulongs(), c.sharePerTier)
            assertEquals(
                listOf(f.u("roundingRemainder"), f.u("noFinisherFee"), f.u("boostReceived"), f.u("comebackOwed"), f.u("comebackFunded"), f.u("comebackClaimed")),
                listOf(c.roundingRemainder, c.noFinisherFee, c.boostReceived, c.comebackOwed, c.comebackFunded, c.comebackClaimed),
            )
        }

        fields("Membership").forEach { (f, bytes) ->
            val m = MembershipAccount.decode(bytes)
            assertEquals(f.key("cohort"), m.cohort)
            assertEquals(f.key("wallet"), m.member.wallet)
            assertEquals(f.i("tier"), m.member.tier)
            assertEquals(f.u("stake"), m.member.stake)
            assertEquals(f.l("joinedAt"), m.member.joinedAt)
            assertEquals(f.i("daysCompleted"), m.member.daysCompleted)
            assertEquals(f.getValue("state").jsonPrimitive.content, m.member.state.name)
            assertEquals(f.b("counted"), m.counted)
            assertEquals(f.u("comebackEntitled"), m.member.comebackEntitled)
        }

        val profiles = fields("Profile")
        assertTrue(profiles.isNotEmpty())
        profiles.forEach { (f, bytes) ->
            val p = ProfileSnapshot.decode(bytes)
            assertEquals(f.key("wallet"), p.wallet)
            assertEquals(
                f.getValue("leagues").jsonArray.map { it.jsonObject }.map {
                    ProfileSnapshot.LeagueProgress(
                        it.i("cleared"),
                        it.i("forfeited"),
                        it.i("currentStreak"),
                        it.i("longestStreak"),
                        it.l("lastChanged"),
                        it.u("comebackStake"),
                        it.l("comebackFrom"),
                        it.l("comebackUntil"),
                    )
                },
                p.leagues,
            )
            val running = f.getValue("running").jsonObject
            if (running.getValue("cohort").jsonPrimitive.content == "11111111111111111111111111111111") assertNull(p.running) else assertEquals(ProfileSnapshot.Running(running.key("cohort"), running.l("end")), p.running)
            val pending = f.getValue("pending").jsonObject
            if (pending.getValue("cohort").jsonPrimitive.content == "11111111111111111111111111111111") assertNull(p.pending) else assertEquals(ProfileSnapshot.Pending(pending.key("cohort"), pending.l("start"), pending.l("end")), p.pending)
            assertEquals(f.l("lastFaucetDay"), p.lastFaucetDay)
        }
        assertFailsWith<IllegalArgumentException> { ProfileSnapshot.decode(ByteArray(ProfileLayout.SIZE)) }
    }

    @Test
    fun `a lobby place that has started reads as the running cohort, as the program normalises it`() {
        val cohort = SolanaPublicKey(ByteArray(32) { 9 })
        val p = ProfileSnapshot(cohort, List(3) { ProfileSnapshot.LeagueProgress(0, 0, 0, 0, 0) }, null, ProfileSnapshot.Pending(cohort, 1_000, 2_000), ProfileSnapshot.NEVER_CLAIMED)
        assertEquals(p, p.normalised(999))
        assertEquals(ProfileSnapshot.Running(cohort, 2_000), p.normalised(1_000).running)
        assertNull(p.normalised(1_000).pending)
        assertEquals(listOf(0, 1, 2, 2), listOf(0, 1, 100, 2).map(ProfileSnapshot::slotOf))
    }
}
