package holdout.app.holdout

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.wallet.SignAction
import holdout.app.wallet.TxOutcome
import holdout.core.MembershipSnapshot

/**
 * A join, a day or a claim that left the wallet and that devnet did not
 * confirm ([TxOutcome.Failed] with `mayStillLand`): it may land yet, so the
 * same action on the same Holdout stays shut until a chain read settles it
 * (PR #62 review) — a second one would pay another fee for a result that
 * could contradict the first. [day] is the Holdout day of a submit, else 0;
 * [sentAt] is `elapsedRealtime` when the send reached the network (the Confirming step).
 */
data class PendingTx(val action: SignAction, val cohort: SolanaPublicKey, val day: Int, val sentAt: Long) {
    fun isFor(action: SignAction, cohort: SolanaPublicKey, day: Int = 0): Boolean = this.action == action && this.cohort == cohort && (action != SignAction.Submit || this.day == day)

    /** Whether [view] shows it on chain. */
    fun landedIn(view: ChainView): Boolean {
        val m = view.holdouts.firstOrNull { it.address == cohort }?.membership
        return when (action) {
            SignAction.Join -> m != null
            SignAction.Submit -> m != null && m.daysCompleted > day
            SignAction.Claim -> m?.state == MembershipSnapshot.State.Claimed
        }
    }

    /** Whether a read started at [readAt] (null: before it was sent) that answered with [view] settles it: it landed, or it can no longer land. */
    fun settledBy(view: ChainView, readAt: Long?): Boolean = landedIn(view) || (readAt != null && readAt - sentAt >= LANDS_WITHIN_MILLIS)

    companion object {
        /**
         * Past this, a transaction devnet has not shown can no longer land: its
         * blockhash (150 slots, about a minute) has expired, with room to spare.
         */
        const val LANDS_WITHIN_MILLIS = 120_000L

        /** [pending] as one string for the phone (`Store.unconfirmedSends`), or null when there are none: `action,cohort,day,sentAt` each, `;` between. */
        fun encode(pending: List<PendingTx>): String? = pending.takeIf { it.isNotEmpty() }?.joinToString(";") { "${it.action.name},${it.cohort.base58()},${it.day},${it.sentAt}" }

        /**
         * What [encode] kept that may still land at [now]: one sent no longer ago than its window.
         * One "sent" after [now] was sent before the phone restarted, which restarts the clock: gone.
         */
        fun restore(encoded: String, now: Long): List<PendingTx> = encoded.split(';').mapNotNull { record ->
            val f = record.split(',').takeIf { it.size == 4 } ?: return@mapNotNull null
            runCatching { PendingTx(SignAction.valueOf(f[0]), SolanaPublicKey.from(f[1]), f[2].toInt(), f[3].toLong()) }.getOrNull()
        }.filter { now - it.sentAt in 0 until LANDS_WITHIN_MILLIS }
    }
}

/**
 * The outcome of a signature the member did not wait for — they left the
 * confirming screen ("You can leave this screen", G7) — kept for the screen
 * they went back to, as a Notice card, until they dismiss it. A landed one
 * needs no card: the chain read shows it.
 */
data class UnseenTx(val action: SignAction, val cohort: SolanaPublicKey, val day: Int, val outcome: TxOutcome) {
    /** The card's title, and whether it is a warning (something was lost or refused). */
    val title: String get() = when (outcome) {
        is TxOutcome.Failed -> if (outcome.mayStillLand) "Not confirmed yet" else "It didn't land"
        TxOutcome.JoiningClosed -> "Joining closed first"
        TxOutcome.DeadlinePassed -> "The deadline passed"
        TxOutcome.NotEnoughSol -> "Not enough SOL"
        else -> "It didn't go through"
    }

    val warning: Boolean get() = !(outcome is TxOutcome.Failed && outcome.mayStillLand)

    /** The card's sentence: what happened to which action, and what to do next. */
    val text: String get() {
        val what = when (action) {
            SignAction.Join -> "Your join"
            SignAction.Submit -> "Day ${day + 1}"
            SignAction.Claim -> "Your claim"
        }
        val safe = when (action) {
            SignAction.Join -> "Your SKR is still yours."
            SignAction.Submit -> "Your day is saved here."
            SignAction.Claim -> "Your claim waits here."
        }
        return when (outcome) {
            is TxOutcome.Failed -> if (outcome.mayStillLand) {
                "$what was sent, but the network hasn't confirmed it. It may still land — check again in a minute."
            } else {
                "$what didn't land. $safe"
            }
            TxOutcome.JoiningClosed -> "Your transaction landed too late. Nothing was taken."
            TxOutcome.DeadlinePassed -> "Day ${day + 1} landed too late. Your run in this Holdout has ended."
            TxOutcome.NotEnoughSol -> "Your wallet has too little devnet SOL to pay for it. The faucet sends some with the test tokens."
            is TxOutcome.Refused -> outcome.message
            TxOutcome.Declined -> "You declined in your wallet. Nothing was sent."
            TxOutcome.NoWallet -> "Install a Solana wallet, then try again. Nothing was sent."
            is TxOutcome.Landed -> ""
        }
    }

    /** The same action as [p]. */
    fun isFor(p: PendingTx): Boolean = p.isFor(action, cohort, day)

    /** Whether it is the outcome of [action] on [cohort] — for a submit, of the same [day]. */
    fun isFor(action: SignAction, cohort: SolanaPublicKey, day: Int): Boolean = this.action == action && this.cohort == cohort && (action != SignAction.Submit || this.day == day)

    companion object {
        /** Cards kept at once; an older one goes to make room (design book, Notice card: never more than three). */
        const val MAX_CARDS = 3
    }
}

/** What an action shut by a [PendingTx] says beside its button. */
fun PendingTx.waitLine(): String = when (action) {
    SignAction.Join -> "Your last join may still land. Staking opens again once devnet answers."
    SignAction.Submit -> "Day ${day + 1} may still land. Sending opens again once devnet answers."
    SignAction.Claim -> "Your claim may still land. Claiming opens again once devnet answers."
}
