package holdout.app.names

import holdout.app.ui.Format
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a member's line shows (spec 003 FR-001–FR-004, FR-006), and the debug
 * build's network rule that let a Seeker reach mainnet at all — both checked
 * on a phone against localnet on 2026-10-09.
 */
class MemberIdentityTextTest {

    private val wallet = "CkJn1a53c1ZXe5XLZ38ZgsPa1WX28PUiMWHqtJayjfWE"

    @Test
    fun `a member with a Seeker ID is shown by it in full`() {
        assertEquals("illai.skr", Format.identity(wallet, "illai.skr", 20))
    }

    @Test
    fun `a member without one, or whose lookup failed, is shown by shortened address`() {
        assertEquals("CkJn…jfWE", Format.identity(wallet, null, 20))
        assertEquals(Format.short(wallet), Format.identity(wallet, null, 28))
    }

    @Test
    fun `a name too long for its place keeps its start and the skr ending`() {
        assertEquals("averyveryverylo….skr", Format.identity(wallet, "averyveryverylongname.skr", 20))
        assertEquals("averyveryverylongname.skr", Format.identity(wallet, "averyveryverylongname.skr", 28))
    }

    /**
     * Any `<domain-config>` makes Android require hostname-aware certificate
     * checks, which Ktor's CIO engine does not do: every HTTPS call of a debug
     * build — the mainnet Seeker ID lookup, devnet — then fails with a
     * CertificateException, while localnet over plain HTTP keeps working and
     * hides it. Seen on a Seeker: the roster showed the address, not the name.
     */
    @Test
    fun `the debug network config sets cleartext on the base config, never per domain`() {
        val config = File("src/debug/res/xml/network_security_config.xml").readText()
            .replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        assertFalse("<domain-config" in config, "a <domain-config> breaks HTTPS through Ktor CIO in debug builds")
        assertTrue(Regex("""<base-config[^>]*cleartextTrafficPermitted="true"""").containsMatchIn(config))
    }
}
