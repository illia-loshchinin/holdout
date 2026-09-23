package holdout.core

import com.solana.publickey.ProgramDerivedAddress
import com.solana.publickey.SolanaPublicKey
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Program-derived addresses (contracts/program-instructions §6).
 *
 * One definition of every seed for the Kotlin side, mirroring the seed
 * constants in `programs/holdout/src/state/` and `tests/helpers/pdas.ts`.
 * Started for T062, which needs `config`; T030 pins the rest to
 * `fixtures/pdas.json`.
 */
object Pdas {
    suspend fun config(programId: SolanaPublicKey): SolanaPublicKey = derive(programId, "config".encodeToByteArray())

    suspend fun faucetVault(programId: SolanaPublicKey): SolanaPublicKey = derive(programId, "faucet_vault".encodeToByteArray())

    suspend fun mintAuthority(programId: SolanaPublicKey): SolanaPublicKey = derive(programId, "mint_authority".encodeToByteArray())

    suspend fun league(programId: SolanaPublicKey, leagueId: Int): SolanaPublicKey = derive(programId, "league".encodeToByteArray(), byteArrayOf(leagueId.toByte()))

    /** `"cohort"`, `league_id`, `index` as a little-endian u32. */
    suspend fun cohort(programId: SolanaPublicKey, leagueId: Int, index: Long): SolanaPublicKey = derive(
        programId,
        "cohort".encodeToByteArray(),
        byteArrayOf(leagueId.toByte()),
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(index.toInt()).array(),
    )

    suspend fun vault(programId: SolanaPublicKey, cohort: SolanaPublicKey): SolanaPublicKey = derive(programId, "vault".encodeToByteArray(), cohort.bytes)

    suspend fun membership(programId: SolanaPublicKey, cohort: SolanaPublicKey, wallet: SolanaPublicKey): SolanaPublicKey = derive(programId, "member".encodeToByteArray(), cohort.bytes, wallet.bytes)

    suspend fun profile(programId: SolanaPublicKey, wallet: SolanaPublicKey): SolanaPublicKey = derive(programId, "profile".encodeToByteArray(), wallet.bytes)

    private suspend fun derive(programId: SolanaPublicKey, vararg seeds: ByteArray): SolanaPublicKey = SolanaPublicKey(ProgramDerivedAddress.find(seeds.toList(), programId).getOrThrow().bytes)
}
