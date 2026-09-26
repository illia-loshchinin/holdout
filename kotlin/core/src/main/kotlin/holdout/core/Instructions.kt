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
 * `publish_chip_set`; T091 added what `crank init` sends; T030 adds the rest and pins every one of them to
 * `fixtures/instructions.json`.
 */
object Instructions {
    val SYSTEM_PROGRAM = SolanaPublicKey.from("11111111111111111111111111111111")
    val TOKEN_PROGRAM = SolanaPublicKey.from("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    val ASSOCIATED_TOKEN_PROGRAM = SolanaPublicKey.from("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
    val UPGRADEABLE_LOADER = SolanaPublicKey.from("BPFLoaderUpgradeab1e11111111111111111111111")

    /** Anchor's default: the first 8 bytes of `sha256("global:<snake_case_name>")`. */
    fun discriminator(name: String): ByteArray = MessageDigest.getInstance("SHA-256").digest("global:$name".encodeToByteArray()).copyOf(8)

    /**
     * `initialize(operator, sponsor, treasury_wallet, faucet_sol_top_up,
     * faucet_daily_lamport_cap, faucet_sponsor_float)` — once, signed by the
     * program's upgrade authority as [admin], who pays. [skrMint] is a fresh
     * keypair that signs too: the mock SKR mint is created at its address.
     */
    suspend fun initialize(
        programId: SolanaPublicKey,
        admin: SolanaPublicKey,
        skrMint: SolanaPublicKey,
        operator: SolanaPublicKey,
        sponsor: SolanaPublicKey,
        treasuryWallet: SolanaPublicKey,
        faucet: FaucetParams,
    ): TransactionInstruction {
        val data = ByteBuffer.allocate(8 + 3 * 32 + 3 * 8).order(ByteOrder.LITTLE_ENDIAN)
        data.put(discriminator("initialize"))
        data.put(operator.bytes).put(sponsor.bytes).put(treasuryWallet.bytes)
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
                AccountMeta(Pdas.faucetVault(programId), false, true),
                AccountMeta(SYSTEM_PROGRAM, false, false),
                AccountMeta(TOKEN_PROGRAM, false, false),
                AccountMeta(ASSOCIATED_TOKEN_PROGRAM, false, false),
            ),
            data.array(),
        )
    }

    /** `create_league(league_id, duration_days, reps_per_day, exercise, first_start, day_length, cadence, join_lock)` — admin only. */
    suspend fun createLeague(
        programId: SolanaPublicKey,
        admin: SolanaPublicKey,
        league: LeagueParams,
    ): TransactionInstruction {
        val data = ByteBuffer.allocate(8 + 1 + 1 + 2 + 1 + 4 * 8).order(ByteOrder.LITTLE_ENDIAN)
        data.put(discriminator("create_league"))
        data.put(league.leagueId.toByte()).put(league.durationDays.toByte()).putShort(league.repsPerDay.toShort()).put(league.exercise.toByte())
        data.putLong(league.firstStart).putLong(league.dayLength).putLong(league.cadence).putLong(league.joinLock)
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(admin, true, true),
                AccountMeta(Pdas.config(programId), false, false),
                AccountMeta(Pdas.league(programId, league.leagueId), false, true),
                AccountMeta(SYSTEM_PROGRAM, false, false),
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
     * `publish_chip_set(amounts: [u64; 4])` — four SKR amounts in base units and
     * nothing else: no rate, no USD figure (constitution III).
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
    ): TransactionInstruction {
        require(amounts.size == 4) { "a chip set is four amounts, got ${amounts.size}" }
        val data = ByteBuffer.allocate(8 + 4 * 8).order(ByteOrder.LITTLE_ENDIAN)
        data.put(discriminator("publish_chip_set"))
        amounts.forEach { data.putLong(it.toLong()) }
        return TransactionInstruction(
            programId,
            listOf(
                AccountMeta(operator, true, true),
                AccountMeta(config, false, true),
            ),
            data.array(),
        )
    }
}
