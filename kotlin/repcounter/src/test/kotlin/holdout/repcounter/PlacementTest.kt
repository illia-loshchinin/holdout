package holdout.repcounter

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Where the floor is, from gravity and the lens alone — no body required. */
class PlacementTest {

    private val lens = Lens(60f)

    @Test
    fun `a level camera puts the horizon across the middle`() {
        val floor = Placement.floorView(Tilt(0f), lens)
        assertEquals(0.5f, assertIs<FloorView.Horizon>(floor).y, 0.001f)
    }

    @Test
    fun `tilting up pushes the horizon down the frame, leaving less floor`() {
        val level = assertIs<FloorView.Horizon>(Placement.floorView(Tilt(0f), lens)).y
        val tilted = assertIs<FloorView.Horizon>(Placement.floorView(Tilt(10f), lens)).y
        assertTrue(tilted > level, "tilting up should move the horizon down, $tilted vs $level")
    }

    @Test
    fun `tilting down pulls it up, leaving more floor`() {
        val tilted = assertIs<FloorView.Horizon>(Placement.floorView(Tilt(-10f), lens)).y
        assertTrue(tilted < 0.5f)
    }

    @Test
    fun `a phone lying on its back sees no floor at all`() {
        // Front camera pointing at the ceiling. This is the case worth catching
        // before the member gets down on the floor to discover it.
        assertEquals(FloorView.NoFloor, Placement.floorView(Tilt(90f), lens))
        assertEquals(FloorView.NoFloor, Placement.floorView(Tilt(45f), lens))
    }

    @Test
    fun `a camera angled well down sees nothing but floor`() {
        assertEquals(FloorView.AllFloor, Placement.floorView(Tilt(-90f), lens))
        assertEquals(FloorView.AllFloor, Placement.floorView(Tilt(-45f), lens))
    }

    @Test
    fun `a wider lens keeps the horizon in frame at a steeper tilt`() {
        val narrow = Placement.floorView(Tilt(25f), Lens(40f))
        val wide = Placement.floorView(Tilt(25f), Lens(90f))
        assertEquals(FloorView.NoFloor, narrow)
        assertIs<FloorView.Horizon>(wide)
    }

    @Test
    fun `gravity gives the pitch of the front camera`() {
        // Readings as Android reports them: at rest, the gravity sensor and the
        // accelerometer both give +9.81 on the axis pointing away from the ground.
        // Upright, screen facing the member: +y. The camera looks along the horizon.
        assertEquals(0f, Placement.pitchFromGravity(0f, 9.81f, 0f).pitchDegrees, 0.5f)
        // Flat on its back, screen up: +z. The front camera looks straight up.
        assertEquals(90f, Placement.pitchFromGravity(0f, 0f, 9.81f).pitchDegrees, 0.5f)
        // Face down on a table: −z. The camera looks straight at the table.
        assertEquals(-90f, Placement.pitchFromGravity(0f, 0f, -9.81f).pitchDegrees, 0.5f)
    }

    @Test
    fun `a phone lying on its back is told to stand up - it sees the ceiling, not the floor`() {
        val tilt = Placement.pitchFromGravity(0f, 0f, 9.81f)
        assertEquals(FloorView.NoFloor, Placement.floorView(tilt, Lens.DEFAULT))
    }

    @Test
    fun `a leaning phone reads as a partial tilt`() {
        // Propped back about 30° from upright: the screen, and the camera, face a little up.
        val tilt = Placement.pitchFromGravity(0f, 8.5f, 4.9f)
        assertTrue(tilt.pitchDegrees in 25f..35f, "expected about 30°, got ${tilt.pitchDegrees}")
    }

    @Test
    fun `a dead sensor reading does not become a wild angle`() {
        assertEquals(0f, Placement.pitchFromGravity(0f, 0f, 0f).pitchDegrees)
    }

    @Test
    fun `a lens has to be a lens`() {
        assertTrue(runCatching { Lens(0f) }.isFailure)
        assertTrue(runCatching { Lens(180f) }.isFailure)
    }
}
