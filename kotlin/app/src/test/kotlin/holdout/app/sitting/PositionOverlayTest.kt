package holdout.app.sitting

import holdout.app.sitting.PositionOverlay.Part
import holdout.repcounter.Guidance
import holdout.repcounter.GuideBox
import holdout.repcounter.PositionHint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The pre-flight draws the floor line and a faint body outline, and no frame around the place to lie (FR-019, 2026-10-13; T363, T382). */
class PositionOverlayTest {
    private val target = GuideBox(centreX = 0.5f, centreY = 0.6f, halfWidth = 0.3f, halfHeight = 0.1f)

    private fun guidance(horizonY: Float? = 0.55f, hint: PositionHint? = PositionHint.MOVE_BACK, aligned: Boolean = false) = Guidance(horizonY = horizonY, target = target, bodyBox = null, fit = 0f, hint = hint, aligned = aligned)

    @Test
    fun `the parts are the body outline and the horizon line`() {
        val parts = PositionOverlay.parts(guidance())

        assertEquals(listOf(Part.Horizon(0.55f), Part.BodyOutline(target)), parts)
    }

    @Test
    fun `no part is a rounded frame around the place to lie`() {
        val parts = PositionOverlay.parts(guidance())

        assertTrue(parts.isNotEmpty())
        assertTrue(parts.all { it is Part.Horizon || it is Part.BodyOutline }, "only the horizon and the outline: $parts")
        assertTrue(parts.none { it::class.simpleName.orEmpty().contains("Frame", ignoreCase = true) }, "no frame part: $parts")
    }

    @Test
    fun `no floor line while the tilt cannot place one`() {
        val parts = PositionOverlay.parts(guidance(horizonY = null))

        assertEquals(listOf(Part.BodyOutline(target)), parts)
    }

    @Test
    fun `nothing at all while the phone does not see the floor`() {
        assertEquals(emptyList(), PositionOverlay.parts(guidance(hint = PositionHint.AIM_AT_THE_FLOOR)))
    }

    @Test
    fun `nothing once the member is in position`() {
        assertEquals(emptyList(), PositionOverlay.parts(guidance(hint = null, aligned = true)))
    }

    @Test
    fun `every other hint keeps the outline and the floor line`() {
        PositionHint.entries.filter { it != PositionHint.AIM_AT_THE_FLOOR }.forEach { hint ->
            val parts = PositionOverlay.parts(guidance(hint = hint))
            assertEquals(2, parts.size, "$hint: $parts")
        }
    }
}
