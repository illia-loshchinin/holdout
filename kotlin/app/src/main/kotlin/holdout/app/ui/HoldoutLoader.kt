package holdout.app.ui

import android.animation.ValueAnimator
import android.graphics.drawable.Animatable2
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import holdout.app.R

/**
 * The Holdout mark, looping: the crowd forfeits one by one, the Holdout grows,
 * then everyone returns. Shown wherever the app waits on the wallet or on
 * devnet. Decorative: the words beside it say what the app is waiting for.
 *
 * `avd_holdout_loop` is one 4 s cycle that starts and ends on the static mark,
 * so it is restarted each time it ends. With animations turned off it stays on
 * the static mark; restarting there would spin, as every cycle ends at once.
 *
 * [size] is the mark's own size: the drawable is the 108-unit icon grid with
 * the crowd in its middle 50 units, so the view is scaled up to drop the margin.
 */
@Composable
fun HoldoutLoader(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                val mark = context.getDrawable(R.drawable.avd_holdout_loop) as AnimatedVectorDrawable
                setImageDrawable(mark)
                scaleX = GRID_TO_MARK
                scaleY = GRID_TO_MARK
                if (ValueAnimator.areAnimatorsEnabled()) {
                    mark.registerAnimationCallback(
                        object : Animatable2.AnimationCallback() {
                            override fun onAnimationEnd(drawable: Drawable) {
                                post { mark.start() }
                            }
                        },
                    )
                    mark.start()
                }
            }
        },
        onRelease = { view ->
            (view.drawable as? AnimatedVectorDrawable)?.run {
                clearAnimationCallbacks()
                stop()
            }
        },
        modifier = modifier.size(size),
    )
}

/** The 108-unit grid over the crowd's 50 units, plus a unit either side. */
private const val GRID_TO_MARK = 108f / 52f
