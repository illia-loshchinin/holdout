package holdout.app

import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import holdout.app.ui.theme.HoldoutTheme
import java.time.Duration
import java.time.Instant

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
     * before the animation reaches the Holdout growing. Keep the splash up for only
     * what is left of its animation, timed by the platform's own start and length,
     * and not at all when the member has turned animations off.
     */
    private fun holdSplashForItsAnimation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        splashScreen.setOnExitAnimationListener { splash ->
            val start = splash.iconAnimationStart
            val length = splash.iconAnimationDuration
            val remaining =
                if (start == null || length == null || !ValueAnimator.areAnimatorsEnabled()) {
                    0L
                } else {
                    Duration.between(Instant.now(), start.plus(length)).toMillis()
                }
            if (remaining > 0) splash.postDelayed({ splash.remove() }, remaining) else splash.remove()
        }
    }
}
