package holdout.app.sitting

import android.content.Context
import java.io.File

/**
 * Release builds never record a frame (constitution X): there is no recorder
 * here, and no CameraX video library to build one with.
 */
@Suppress("UNUSED_PARAMETER")
fun sittingVideo(context: Context, dir: File, stakes: String): SittingVideo? = null
