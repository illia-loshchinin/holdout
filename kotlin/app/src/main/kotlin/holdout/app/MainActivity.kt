package holdout.app

import android.animation.ValueAnimator
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
     * what is left of its animation, timed by the platform's own start and length.
     * The platform runs that animation through a ValueAnimator, so its real length
     * is the nominal one times the animator duration scale (developer options):
     * 2x plays it for twice as long, and 0 (animations off) not at all.
     */
    private fun holdSplashForItsAnimation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        splashScreen.setOnExitAnimationListener { splash ->
            val start = splash.iconAnimationStart
            val length = splash.iconAnimationDuration
            val scale = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val remaining =
                if (start == null || length == null || !ValueAnimator.areAnimatorsEnabled() || scale <= 0f) {
                    0L
                } else {
                    val played = Duration.ofMillis((length.toMillis() * scale).toLong())
                    Duration.between(Instant.now(), start.plus(played)).toMillis()
                }
            if (remaining > 0) splash.postDelayed({ splash.remove() }, remaining) else splash.remove()
        }
    }
}
