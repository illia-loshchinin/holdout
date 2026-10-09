package holdout.app.sitting

import android.content.Context
import java.io.File

/**
 * The member app never records a frame, in any build (constitution X v2.1.0,
 * spec 004): there is no recorder here, and its code may not use the CameraX
 * video library — `checkMemberHasNoLabCode` fails the build if it does.
 */
@Suppress("UNUSED_PARAMETER")
fun sittingVideo(context: Context, dir: File, stakes: String): SittingVideo? = null
