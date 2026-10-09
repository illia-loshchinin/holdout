package holdout.app.ui

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Tells [onShowing] which members' rows of [list] are on screen, top first,
 * each time that changes — and an empty list when the screen goes away — so
 * Seeker IDs are looked up only for rows someone can see (spec 003 FR-012,
 * research R11). [walletOf] turns a row's key into its wallet, or null for a
 * row that names no member.
 */
@Composable
fun ReportShownWallets(list: LazyListState, onShowing: (Collection<String>) -> Unit, walletOf: (key: Any) -> String?) {
    val report by rememberUpdatedState(onShowing)
    val wallet by rememberUpdatedState(walletOf)
    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.visibleItemsInfo.mapNotNull { wallet(it.key) } }
            .distinctUntilChanged()
            .collect { report(it) }
    }
    DisposableEffect(Unit) { onDispose { report(emptyList()) } }
}
