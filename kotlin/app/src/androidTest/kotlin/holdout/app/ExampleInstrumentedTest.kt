package holdout.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device. Shared by both
 * apps, so the package it expects follows the flavor (spec 004 research R10).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Context of the app under test: Holdout, or Holdout Lab beside it.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val expected = if (BuildConfig.FLAVOR == "lab") "holdout.app.lab" else "holdout.app"
        assertEquals(expected, appContext.packageName)
    }
}
