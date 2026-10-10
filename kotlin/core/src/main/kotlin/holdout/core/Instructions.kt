package holdout.core

import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.AccountMeta
import com.solana.transaction.TransactionInstruction
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * Instruction builders (contracts/program-instructions §1–§3), with the
 * encodings written by hand rather than generated (research R2 deviation).
 *
 * Account order is the one `tests/helpers/instructions.ts` fixes and the
 * program's `Accounts` structs follow: signers first, then the state the
 * instruction reads or writes, then programs. Started for T062 with
 * `publish_chip_set`; T091 added what `crank init` sends; T030 added the rest,
 * and `FixtureReplayTest` pins every one of them to `fixtures/instructions.json`.
 *
 * An optional account the program takes as `Option<Account>` is sent as the
 * program's own id when absent: Anchor's convention for "none", and what the
 * TypeScript builders send.
 */
object Instructions {
    val SYSTEM_PROGRAM = SolanaPublicKey.from("11111111111111111111111111111111")
    val TOKEN_PROGRAM = SolanaPublicKey.from("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    val ASSOCIATED_TOKEN_PROGRAM = SolanaPublicKey.from("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
    val UPGRADEABLE_LOADER = SolanaPublicKey.from("BPFLoaderUpgradeab1e11111111111111111111111")

    /** Anchor's default: the first 8 bytes of `sha256("global:<snake_case_name>")`. */
    fun discriminator(name: String): ByteArray = MessageDigest.getInstance("SHA-256").digest("global:$name".encodeToByteArray()).copyOf(8)

    /**
     * `initialize(operator, sponsor, treasury_wallet, developer_wallet,
     * faucet_sol_top_up, faucet_daily_lamport_cap, faucet_sponsor_float)` —
     * once, signed by the program's upgrade authority as [admin], who pays.
     * [skrMint] is a fresh keypair that signs too: the mock SKR mint is created
     * at its address. The program creates both fee destinations as associated
     * token accounts: the operations one ([treasuryWallet]'s, `fee_destination`)
     * and the developer one ([developerWallet]'s, `developer_destination`) —
     * H35, FR-037f.
     */
    suspend fun initialize(
        programId: SolanaPublicKey,
        admin: SolanaPublicKey,
        skrMint: SolanaPublicKey,
        operator: SolanaPublicKey,
        sponsor: SolanaPublicKey,
        treasuryWallet: SolanaPublicKey,
        developerWallet: SolanaPublicKey,
        faucet: FaucetParams,
    ): TransactionInstruction {
        val data = ByteBuffer.allocate(8 + 4 * 32 + 3 * 8).order(ByteOrder.LITTLE_ENDIAN)
        data.put(discriminator("initialize"))
        data.put(operator.bytes).put(sponsor.bytes).put(treasuryWallet.bytes).put(developerWallet.bytes)
        data.putLong(faucet.solTopUp.toLong()).putLong(faucet.dailyLamportCap.toLong()).putLong(faucet.sponsorFloat.toLong())
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(admin, true, true),
                AccountMeta(Pdas.config(programId), false, true),
                AccountMeta(programId, false, false),
                AccountMeta(Pdas.programData(programId), false, false),
                AccountMeta(skrMint, true, true),
                AccountMeta(Pdas.mintAuthority(programId), false, false),
                AccountMeta(treasuryWallet, false, false),
                AccountMeta(Pdas.associatedToken(treasuryWallet, skrMint), false, true),
                AccountMeta(developerWallet, false, false),
                AccountMeta(Pdas.associatedToken(developerWallet, skrMint), false, true),
                AccountMeta(Pdas.faucetVault(programId), false, true),
                AccountMeta(SYSTEM_PROGRAM, false, false),
                AccountMeta(TOKEN_PROGRAM, false, false),
                AccountMeta(ASSOCIATED_TOKEN_PROGRAM, false, false),
            ),
            data.array(),
        )
    }

    /**
     * `create_league(league_id, duration_days, reps_per_day, exercise, form,
     * first_start, day_length, cadence, join_lock)` — admin only. Also creates
     * the league's reserve vault, an SKR token account of [skrMint]
     * (`Config.skr_mint`) owned by the league PDA (H35). A form other than 0 or
     * 1 is refused by the program ([LeagueParams.INVALID_FORM]).
     */
    suspend fun createLeague(
        programId: SolanaPublicKey,
        admin: SolanaPublicKey,
        skrMint: SolanaPublicKey,
        league: LeagueParams,
    ): TransactionInstruction {
        val data = ByteBuffer.allocate(8 + 1 + 1 + 2 + 1 + 1 + 4 * 8).order(ByteOrder.LITTLE_ENDIAN)
        data.put(discriminator("create_league"))
        data.put(league.leagueId.toByte()).put(league.durationDays.toByte()).putShort(league.repsPerDay.toShort()).put(league.exercise.toByte()).put(league.form.toByte())
        data.putLong(league.firstStart).putLong(league.dayLength).putLong(league.cadence).putLong(league.joinLock)
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(admin, true, true),
                AccountMeta(Pdas.config(programId), false, false),
                AccountMeta(Pdas.league(programId, league.leagueId), false, true),
                AccountMeta(skrMint, false, false),
                AccountMeta(Pdas.reserveVault(programId, league.leagueId), false, true),
                AccountMeta(SYSTEM_PROGRAM, false, false),
                AccountMeta(TOKEN_PROGRAM, false, false),
            ),
            data.array(),
        )
    }

    /** The System Program's `Transfer` (instruction 2): [lamports] from [from], who signs, to [to]. */
    fun systemTransfer(from: SolanaPublicKey, to: SolanaPublicKey, lamports: ULong): TransactionInstruction {
        val data = ByteBuffer.allocate(4 + 8).order(ByteOrder.LITTLE_ENDIAN).putInt(2).putLong(lamports.toLong())
        return TransactionInstruction(SYSTEM_PROGRAM, listOf(AccountMeta(from, true, true), AccountMeta(to, false, true)), data.array())
    }

    /**
     * `publish_chip_set(amounts: [u64; 4], valid_until: i64)` — four SKR amounts
     * in base units and the unix second from which the program refuses the set
     * (`ChipSetExpired`, 6014; FR-055a, FR-056). No rate, no USD figure
     * (constitution III).
     *
     * [operator] signs, and in the crank it also pays the fee, so it is marked
     * writable: a fee payer must be. The program only requires a signer, so the
     * extra flag changes nothing it checks.
     */
    fun publishChipSet(
        programId: SolanaPublicKey,
        operator: SolanaPublicKey,
        config: SolanaPublicKey,
        amounts: List<ULong>,
        validUntil: Long,
    ): TransactionInstruction {
        require(amounts.size == 4) { "a chip set is four amounts, got ${amounts.size}" }
        val data = ByteBuffer.allocate(8 + 4 * 8 + 8).order(ByteOrder.LITTLE_ENDIAN)
        data.put(discriminator("publish_chip_set"))
        amounts.forEach { data.putLong(it.toLong()) }
        data.putLong(validUntil)
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(operator, true, true),
                AccountMeta(config, false, true),
            ),
            data.array(),
        )
    }

    /** `set_flags(ladder_gate_enabled, genesis_gate_enabled)` — admin only; the only difference between this build and an enforced ladder (AC-1). */
    suspend fun setFlags(programId: SolanaPublicKey, admin: SolanaPublicKey, ladderGateEnabled: Boolean, genesisGateEnabled: Boolean): TransactionInstruction = TransactionInstruction(
        programId,
        listOf(AccountMeta(admin, true, false), AccountMeta(Pdas.config(programId), false, true)),
        discriminator("set_flags") + byteArrayOf(ladderGateEnabled.byte(), genesisGateEnabled.byte()),
    )

    /**
     * `open_cohort(league_id, index)` — permissionless; [payer] only pays the
     * rent. [previousCohort] is required for `index > 0` and absent for index 0.
     */
    suspend fun openCohort(
        programId: SolanaPublicKey,
        payer: SolanaPublicKey,
        skrMint: SolanaPublicKey,
        leagueId: Int,
        index: Long,
        previousCohort: SolanaPublicKey? = null,
    ): TransactionInstruction {
        val cohort = Pdas.cohort(programId, leagueId, index)
        val data = ByteBuffer.allocate(8 + 1 + 4).order(ByteOrder.LITTLE_ENDIAN)
        data.put(discriminator("open_cohort")).put(leagueId.toByte()).putInt(index.toInt())
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(payer, true, true),
                AccountMeta(Pdas.config(programId), false, false),
                AccountMeta(Pdas.league(programId, leagueId), false, true),
                AccountMeta(cohort, false, true),
                AccountMeta(Pdas.vault(programId, cohort), false, true),
                AccountMeta(previousCohort ?: programId, false, false),
                AccountMeta(skrMint, false, false),
                AccountMeta(SYSTEM_PROGRAM, false, false),
                AccountMeta(TOKEN_PROGRAM, false, false),
            ),
            data.array(),
        )
    }

    /**
     * `join_cohort(tier)` — the member signs, pays the rent for their
     * `Membership` (and a first `Profile`), and the stake moves from their
     * associated SKR account into the cohort's vault. [runningCohort] is the
     * cohort in `profile.running`, which the program requires whenever that
     * slot is set: the overlap rule is checked against its account.
     */
    suspend fun joinCohort(
        programId: SolanaPublicKey,
        member: SolanaPublicKey,
        skrMint: SolanaPublicKey,
        cohort: SolanaPublicKey,
        tier: Int,
        runningCohort: SolanaPublicKey? = null,
    ): TransactionInstruction {
        require(tier in 0..3) { "a tier is 0..3, got $tier" }
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(member, true, true),
                AccountMeta(Pdas.config(programId), false, false),
                AccountMeta(cohort, false, true),
                AccountMeta(Pdas.vault(programId, cohort), false, true),
                AccountMeta(Pdas.membership(programId, cohort, member), false, true),
                AccountMeta(Pdas.profile(programId, member), false, true),
                AccountMeta(Pdas.associatedToken(member, skrMint), false, true),
                AccountMeta(runningCohort ?: programId, false, false),
                AccountMeta(skrMint, false, false),
                AccountMeta(SYSTEM_PROGRAM, false, false),
                AccountMeta(TOKEN_PROGRAM, false, false),
            ),
            discriminator("join_cohort") + byteArrayOf(tier.toByte()),
        )
    }

    /** `leave_cohort()` — `Open` only; a full refund, the membership closed and its rent returned (FR-009). */
    suspend fun leaveCohort(programId: SolanaPublicKey, member: SolanaPublicKey, skrMint: SolanaPublicKey, cohort: SolanaPublicKey): TransactionInstruction = TransactionInstruction(
        programId,
        listOf(
            AccountMeta(member, true, true),
            AccountMeta(Pdas.config(programId), false, false),
            AccountMeta(cohort, false, true),
            AccountMeta(Pdas.vault(programId, cohort), false, true),
            AccountMeta(Pdas.membership(programId, cohort, member), false, true),
            AccountMeta(Pdas.profile(programId, member), false, true),
            AccountMeta(Pdas.associatedToken(member, skrMint), false, true),
            AccountMeta(TOKEN_PROGRAM, false, false),
        ),
        discriminator("leave_cohort"),
    )

    /**
     * `submit_day(day_index, proof_hash)` — member-signed; no money moves. The
     * 32-byte proof is emitted in `DayCompleted` and not stored (research R16).
     */
    suspend fun submitDay(programId: SolanaPublicKey, member: SolanaPublicKey, cohort: SolanaPublicKey, dayIndex: Int, proofHash: ByteArray): TransactionInstruction {
        require(proofHash.size == 32) { "a proof hash is 32 bytes, got ${proofHash.size}" }
        require(dayIndex in 0..255) { "a day index is a u8, got $dayIndex" }
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(member, true, true),
                AccountMeta(cohort, false, false),
                AccountMeta(Pdas.membership(programId, cohort, member), false, true),
                AccountMeta(Pdas.profile(programId, member), false, true),
            ),
            discriminator("submit_day") + byteArrayOf(dayIndex.toByte()) + proofHash,
        )
    }

    /** A page pair for `record_forfeits` and `settle_count`: a membership and its wallet's profile. */
    data class MemberPair(val membership: SolanaPublicKey, val profile: SolanaPublicKey) {
        companion object {
            suspend fun of(programId: SolanaPublicKey, cohort: SolanaPublicKey, wallet: SolanaPublicKey) = MemberPair(Pdas.membership(programId, cohort, wallet), Pdas.profile(programId, wallet))
        }
    }

    /** The most pairs one page may carry (contracts/program-instructions §2). */
    const val MAX_PAGE_PAIRS = 10

    /**
     * `record_forfeits()` — permissionless, up to ten pairs in the remaining
     * accounts; pairs not in breach or already forfeited are skipped, so a page
     * may be repeated and re-ordered freely (research R9). Each forfeit splits
     * 70/5/5/10/10 (FR-027, H35): the pool and the two reserve dues stay in the
     * vault, the developer part goes to [developerDestination] and the
     * operations part to [feeDestination] at once.
     */
    suspend fun recordForfeits(
        programId: SolanaPublicKey,
        caller: SolanaPublicKey,
        feeDestination: SolanaPublicKey,
        developerDestination: SolanaPublicKey,
        cohort: SolanaPublicKey,
        pairs: List<MemberPair>,
    ): TransactionInstruction {
        require(pairs.size in 1..MAX_PAGE_PAIRS) { "a page is 1..$MAX_PAGE_PAIRS pairs, got ${pairs.size}" }
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(caller, true, true),
                AccountMeta(Pdas.config(programId), false, false),
                AccountMeta(cohort, false, true),
                AccountMeta(Pdas.vault(programId, cohort), false, true),
                AccountMeta(feeDestination, false, true),
                AccountMeta(developerDestination, false, true),
                AccountMeta(TOKEN_PROGRAM, false, false),
            ) + pairs.pageAccounts(),
            discriminator("record_forfeits"),
        )
    }

    /**
     * `settle_count()` — permissionless and paged like [recordForfeits], with
     * the same split for a breach nobody recorded. The league's cohort
     * `index − 1` is required for every index but 0: counting runs in cohort
     * order so every streak, and every comeback record, does too (FR-061a,
     * FR-037e).
     */
    suspend fun settleCount(
        programId: SolanaPublicKey,
        caller: SolanaPublicKey,
        feeDestination: SolanaPublicKey,
        developerDestination: SolanaPublicKey,
        leagueId: Int,
        cohort: SolanaPublicKey,
        pairs: List<MemberPair>,
        previousCohort: SolanaPublicKey? = null,
    ): TransactionInstruction {
        require(pairs.size in 1..MAX_PAGE_PAIRS) { "a page is 1..$MAX_PAGE_PAIRS pairs, got ${pairs.size}" }
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(caller, true, true),
                AccountMeta(Pdas.config(programId), false, false),
                AccountMeta(Pdas.league(programId, leagueId), false, false),
                AccountMeta(cohort, false, true),
                AccountMeta(Pdas.vault(programId, cohort), false, true),
                AccountMeta(feeDestination, false, true),
                AccountMeta(developerDestination, false, true),
                AccountMeta(previousCohort ?: programId, false, false),
                AccountMeta(TOKEN_PROGRAM, false, false),
            ) + pairs.pageAccounts(),
            discriminator("settle_count"),
        )
    }

    /**
     * `settle_finalize()`. With finishers it draws the boost from the league's
     * reserve vault into the pool, writes the shares, sends the rounding
     * remainder to [feeDestination] and funds the comeback refunds; with none
     * it sends half of the pool to [developerDestination] and the rest to
     * [feeDestination] (FR-037). Either way the cohort's reserve dues then move
     * to the league's reserve vault, which is why the league is writable
     * (FR-037c–e, H35). It needs no other cohort, so no caller can change where
     * the money goes (FR-030a).
     */
    suspend fun settleFinalize(
        programId: SolanaPublicKey,
        caller: SolanaPublicKey,
        feeDestination: SolanaPublicKey,
        developerDestination: SolanaPublicKey,
        leagueId: Int,
        cohort: SolanaPublicKey,
        previousCohort: SolanaPublicKey? = null,
    ): TransactionInstruction = TransactionInstruction(
        programId,
        listOf(
            AccountMeta(caller, true, true),
            AccountMeta(Pdas.config(programId), false, false),
            AccountMeta(Pdas.league(programId, leagueId), false, true),
            AccountMeta(cohort, false, true),
            AccountMeta(Pdas.vault(programId, cohort), false, true),
            AccountMeta(feeDestination, false, true),
            AccountMeta(developerDestination, false, true),
            AccountMeta(Pdas.reserveVault(programId, leagueId), false, true),
            // The league's cohort `index - 1`, settled: required for every index
            // but 0, as for settle_count — an empty cohort is never counted (FR-061a).
            AccountMeta(previousCohort ?: programId, false, false),
            AccountMeta(TOKEN_PROGRAM, false, false),
        ),
        discriminator("settle_finalize"),
    )

    /**
     * `claim_payout()` — `stake + share_per_tier[tier]` plus the member's
     * comeback refund, read from the finalised cohort (FR-034, FR-037e).
     * Accounts unchanged by H35.
     */
    suspend fun claimPayout(programId: SolanaPublicKey, member: SolanaPublicKey, skrMint: SolanaPublicKey, cohort: SolanaPublicKey): TransactionInstruction = TransactionInstruction(
        programId,
        listOf(
            AccountMeta(member, true, true),
            AccountMeta(Pdas.config(programId), false, false),
            AccountMeta(cohort, false, true),
            AccountMeta(Pdas.vault(programId, cohort), false, true),
            AccountMeta(Pdas.membership(programId, cohort, member), false, true),
            AccountMeta(Pdas.associatedToken(member, skrMint), false, true),
            AccountMeta(TOKEN_PROGRAM, false, false),
        ),
        discriminator("claim_payout"),
    )

    /**
     * `claim_faucet(recipient)` — **the recipient does not sign** (research
     * R13). [payer] is the bundled sponsor for a zero-SOL wallet, or the
     * recipient itself when it can pay (FR-042b).
     */
    suspend fun claimFaucet(
        programId: SolanaPublicKey,
        payer: SolanaPublicKey,
        recipient: SolanaPublicKey,
        skrMint: SolanaPublicKey,
        sponsor: SolanaPublicKey,
    ): TransactionInstruction = TransactionInstruction(
        programId,
        listOf(
            AccountMeta(payer, true, true),
            AccountMeta(Pdas.config(programId), false, true),
            AccountMeta(recipient, false, true),
            AccountMeta(Pdas.profile(programId, recipient), false, true),
            AccountMeta(skrMint, false, true),
            AccountMeta(Pdas.mintAuthority(programId), false, false),
            AccountMeta(Pdas.associatedToken(recipient, skrMint), false, true),
            AccountMeta(Pdas.faucetVault(programId), false, true),
            AccountMeta(sponsor, false, true),
            AccountMeta(SYSTEM_PROGRAM, false, false),
            AccountMeta(TOKEN_PROGRAM, false, false),
            AccountMeta(ASSOCIATED_TOKEN_PROGRAM, false, false),
        ),
        discriminator("claim_faucet") + recipient.bytes,
    )

    /** `top_up_sponsor()` — permissionless, and the sponsor does not sign (FR-042b). */
    suspend fun topUpSponsor(programId: SolanaPublicKey, caller: SolanaPublicKey, sponsor: SolanaPublicKey): TransactionInstruction = TransactionInstruction(
        programId,
        listOf(
            AccountMeta(caller, true, true),
            AccountMeta(Pdas.config(programId), false, true),
            AccountMeta(Pdas.faucetVault(programId), false, true),
            AccountMeta(sponsor, false, true),
            AccountMeta(SYSTEM_PROGRAM, false, false),
        ),
        discriminator("top_up_sponsor"),
    )

    private fun List<MemberPair>.pageAccounts() = flatMap { listOf(AccountMeta(it.membership, false, true), AccountMeta(it.profile, false, true)) }

    private fun Boolean.byte(): Byte = if (this) 1 else 0
}
