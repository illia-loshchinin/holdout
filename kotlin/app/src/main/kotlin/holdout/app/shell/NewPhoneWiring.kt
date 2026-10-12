package holdout.app.shell

import com.solana.publickey.SolanaPublicKey
import holdout.app.chain.ChainView
import holdout.app.home.NewPhone
import holdout.app.storage.Store

/**
 * The two places the shell touches the new-phone banner (ES-3a), named so a JVM test can reach
 * them without composing the app: `HoldoutApp` calls [onConnect] when the wallet connects or the
 * day changes, and `TodayRoot` asks [shows] for T1 and for T2. The rule is [NewPhone]'s.
 */
internal object NewPhoneWiring {
    /** Keeps the day this phone first saw the connected wallet, once; nothing while no wallet is connected. */
    fun onConnect(store: Store, address: SolanaPublicKey?, today: Long) {
        address?.let { NewPhone.markSeen(store, it.base58(), today) }
    }

    /** The banner flag for Today: [banked] is what this phone has banked toward today's day (the Daily 25's, or the Holdout's). */
    fun shows(store: Store, wallet: String, dayIndex: Long, view: ChainView?, banked: Int): Boolean = NewPhone.shows(store, wallet, dayIndex, view, banked)
}
