package holdout.app.tour

import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

/** The tour figure's data is parsed once per process, and a load that fails is reported, and in debug builds thrown. */
class FigureCacheTest {
    private val reported = mutableListOf<Throwable>()

    private fun asset(): InputStream = File("src/main/assets/$ASSET").inputStream()

    @Test
    fun `the asset is parsed once, however many times the card is entered`() {
        var opened = 0
        val cache = FigureCache(open = {
            opened++
            asset()
        }, debug = false, report = { reported += it })
        val first = cache.get()
        val second = cache.get()
        assertSame(first, second)
        assertEquals(1, opened)
        assertEquals(emptyList(), reported)
    }

    @Test
    fun `a missing asset is reported and shows nothing in a release build`() {
        val cache = FigureCache(open = { throw FileNotFoundException(ASSET) }, debug = false, report = { reported += it })
        assertNull(cache.get())
        assertEquals(1, reported.size)
    }

    @Test
    fun `a malformed asset is thrown in a debug build, after it is reported`() {
        val cache = FigureCache(open = { ByteArrayInputStream("V 1\nN scene\n".toByteArray()) }, debug = true, report = { reported += it })
        assertFailsWith<NoSuchElementException> { cache.get() }
        assertEquals(1, reported.size)
    }

    @Test
    fun `a failed load is tried again on the next entry`() {
        var broken = true
        val cache = FigureCache(open = { if (broken) throw FileNotFoundException(ASSET) else asset() }, debug = false, report = { reported += it })
        assertNull(cache.get())
        broken = false
        assertNotNull(cache.get())
    }
}
