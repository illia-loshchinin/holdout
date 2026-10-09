package holdout.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import holdout.app.lab.LabApp

/** Holdout Lab: no wallet, so the wallet's activity sender goes unused (spec 004). */
@Composable
@Suppress("UNUSED_PARAMETER")
fun AppRoot(sender: ActivityResultSender, modifier: Modifier = Modifier) {
    LabApp(modifier = modifier)
}
