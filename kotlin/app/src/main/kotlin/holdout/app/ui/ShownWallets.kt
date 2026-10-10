package holdout.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tells [onShowing] which members' rows of [list] are on screen, top first,
 * each time that changes — and an empty list whenever none can be seen: the
 * app went to the background, or the screen went away — so Seeker IDs are
 * looked up only for rows someone can see (spec 003 FR-012, research R11).
 * Back in the foreground, the rows on screen are reported again.
 * [walletOf] turns a row's key into its wallet, or null for a row that names
 * no member.
 */
@Composable
fun ReportShownWallets(list: LazyListState, onShowing: (Collection<String>) -> Unit, walletOf: (key: Any) -> String?) {
    val report by rememberUpdatedState(onShowing)
    val wallet by rememberUpdatedState(walletOf)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(list, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { wallet(it.key) } }
                    .distinctUntilChanged()
                    .collect { report(it) }
            } finally {
                // Stopped, or the screen left composition: nothing is on screen.
                report(emptyList())
            }
        }
    }
}
