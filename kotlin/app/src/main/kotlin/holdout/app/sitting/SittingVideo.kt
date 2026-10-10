package holdout.app.sitting

import androidx.camera.core.UseCase

/**
 * A set's camera, saved as video — **Holdout Lab only** (constitution X
 * v2.1.0; spec 002 FR-026, spec 004). Rep recognition is tested against what
 * really happened, and the knee classifier is trained on it.
 *
 * The one implementation is in `src/lab/`; `src/member/` returns null, and the
 * CameraX video library is a Lab-only dependency, so the member app contains
 * no recorder in any build — `checkMemberHasNoLabCode` fails a member build if
 * that ever changes. Video only: no audio, no microphone permission. Saved in
 * Lab's private storage beside the set's trace; the team pulls it over USB
 * and deletes it (`scripts/pull-traces.sh`).
 */
interface SittingVideo {
    /** Bound with the preview and the analysis. */
    val useCase: UseCase

    /** Starts recording once the camera is bound. */
    fun start()

    /**
     * Stops, and names the file after the sitting's trace — [traceName], or
     * the recorder's own timestamp when no trace was written — beside a
     * `.video.json` saying when recording started on the boot clock the
     * trace's frame timestamps use, so rows and frames can be matched.
     */
    fun stop(traceName: String?)
}
