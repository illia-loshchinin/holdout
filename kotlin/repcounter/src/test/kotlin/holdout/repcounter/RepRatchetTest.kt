package holdout.repcounter

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The regression ratchet ([Ratchet]): every labelled rep under
 * `fixtures/rep-traces/` is replayed and held to `baseline.csv`. A rep may
 * never do worse than it did when the baseline was written; when one does
 * better, the baseline must be written again — so a rep put right stays right,
 * and a change cannot trade one person's honest reps for another's cheats.
 */
class RepRatchetTest {

    private val dir = File(System.getProperty("holdout.repTraces") ?: "../../fixtures/rep-traces")
    private val baseline by lazy { Ratchet.read(File(dir, Ratchet.BASELINE)).associateBy { it.key } }
    private val today by lazy { Ratchet.today(dir) }

    private fun describe(r: RatchetRep) = "${r.fixture} t=${r.t} ${r.label}: ${r.state.name.lowercase()} (${r.verdict})"

    @Test
    fun `no labelled rep does worse than in the baseline`() {
        val now = today.associateBy { it.key }
        val worse = baseline.values.mapNotNull { was ->
            val r = now[was.key]
            when {
                // A count where no rep was labelled that is no longer made: better, not gone.
                r == null && was.label == RepLabels.UNLABELLED_COUNT -> null
                r == null -> "${describe(was)} → its label is gone"
                r.state < was.state -> "${describe(was)} → ${r.state.name.lowercase()} (${r.verdict})"
                else -> null
            }
        }
        assertTrue(worse.isEmpty(), "Labelled reps the counter now gets worse:\n" + worse.joinToString("\n"))
    }

    @Test
    fun `the baseline holds every labelled rep as the counter does today`() {
        val stale = today.mapNotNull { r ->
            val was = baseline[r.key]
            when {
                was == null -> "new: ${describe(r)}"
                r.state > was.state -> "better: ${describe(was)} → ${r.state.name.lowercase()} (${r.verdict})"
                r.label != was.label -> "relabelled: ${describe(was)} → ${r.label}"
                else -> null
            }
        }
        assertTrue(
            stale.isEmpty(),
            "The baseline is out of date — lock these in with ./gradlew :repcounter:updateBaseline:\n" + stale.joinToString("\n"),
        )
    }
}
