package holdout.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

/** The member app: everything a member uses, and nothing else (spec 004). */
@Composable
fun AppRoot(sender: ActivityResultSender, modifier: Modifier = Modifier) {
    HoldoutApp(sender = sender, modifier = modifier)
}
