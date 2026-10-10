package holdout.app.sitting

import holdout.repcounter.CameraView
import holdout.repcounter.PoseFrame
import holdout.repcounter.Readout
import holdout.repcounter.RepEvent

/**
 * Whatever keeps a record of a sitting — **Holdout Lab only** (spec 004,
 * constitution X v2.1.0). Lab hands a sitting its trace recorder; the member
 * app hands it none, so a member's sitting writes nothing about their body.
 *
 * The implementation lives in `src/lab`, out of reach of member code. Counting
 * itself never lives behind this seam: both apps count from `src/main` and
 * `:repcounter`, so a counting fix reaches both (FR-011).
 */
interface SittingRecorder {
    /** The file being written, once the first frame has opened one. */
    val fileName: String?

    /**
     * True once the record could not be written — storage full, or a write
     * or flush refused. The set is then missing or cut short, and the tester
     * is told it was not saved (spec 004, edge cases).
     */
    val failed: Boolean

    fun record(
        timestampMillis: Long,
        step: String,
        view: CameraView?,
        world: PoseFrame,
        image: PoseFrame,
        width: Int,
        height: Int,
        readout: Readout? = null,
        event: RepEvent? = null,
    )

    /**
     * A person's account of the set, written into its closed record (002 FR-025).
     * True once it is in the file; false leaves the record unlabelled and whole.
     */
    fun label(expectedReps: Int, done: Map<String, Int>, note: String, placement: String? = null, phoneSide: String? = null): Boolean

    fun close()
}
