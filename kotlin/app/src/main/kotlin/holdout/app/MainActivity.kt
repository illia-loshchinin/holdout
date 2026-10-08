package holdout.app

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import holdout.app.ui.theme.HoldoutTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        holdSplashForItsAnimation()

        val sender = ActivityResultSender(this)

        setContent {
            HoldoutTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HoldoutApp(
                        sender = sender,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    /**
     * Android 12+ plays the Holdout mark on the splash (`values-v31/themes.xml`),
     * but takes the splash down on the app's first frame, which a fast launch draws
     * before the animation reaches the Holdout growing. Hold that frame until the
     * one-second animation has played.
     */
    private fun holdSplashForItsAnimation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val shownAt = SystemClock.uptimeMillis()
        val content = findViewById<View>(android.R.id.content)
        content.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (SystemClock.uptimeMillis() - shownAt < SPLASH_ANIMATION_MS) return false
                    content.viewTreeObserver.removeOnPreDrawListener(this)
                    return true
                }
            },
        )
    }

    private companion object {
        /** The length of `avd_holdout_splash`. */
        const val SPLASH_ANIMATION_MS = 1_000L
    }
}
