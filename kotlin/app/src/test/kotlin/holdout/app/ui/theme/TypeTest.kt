package holdout.app.ui.theme

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.test.core.app.ApplicationProvider
import holdout.app.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** The design book's faces are bundled (T035): JetBrains Mono for amounts, Roboto Flex for the big numbers. */
@RunWith(RobolectricTestRunner::class)
class TypeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `amounts are set in the bundled JetBrains Mono, regular and medium`() {
        assertEquals(HoldoutFonts.Mono, AmountStyle.fontFamily)
        assertNotEquals<FontFamily?>(FontFamily.Monospace, AmountStyle.fontFamily)
        val fonts = (HoldoutFonts.Mono as androidx.compose.ui.text.font.FontListFontFamily).fonts.map { it as ResourceFont }
        assertEquals(listOf(FontWeight.Normal, FontWeight.Medium), fonts.map { it.weight })
        assertEquals(listOf(R.font.jetbrains_mono_regular, R.font.jetbrains_mono_medium), fonts.map { it.resId })
    }

    @Test
    fun `the counter and display numbers are set in the bundled Roboto Flex at weight 300`() {
        assertEquals(HoldoutFonts.FlexCounter, CounterStyle.fontFamily)
        assertEquals(HoldoutFonts.FlexDisplay, DisplayStyle.fontFamily)
        assertEquals(FontWeight.Light, CounterStyle.fontWeight)
    }

    @Test
    fun `every bundled face is a real font file in the APK`() {
        listOf(R.font.jetbrains_mono_regular, R.font.jetbrains_mono_medium, R.font.roboto_flex).forEach { id ->
            context.resources.openRawResource(id).use { assertTrue(it.available() > 100_000) }
        }
    }
}
