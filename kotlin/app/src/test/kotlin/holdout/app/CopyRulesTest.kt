package holdout.app

import holdout.app.cohort.DetailText
import holdout.app.nav.Tab
import holdout.app.nav.Tour
import holdout.app.ui.Format
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The v2 tab screens' words, held to the member-facing source (T365, FR-067, FR-069, FR-069h): the
 * strings of `src/main` and `src/member` are read as text and must not say what the redesign retired,
 * and must say what it specified. The same patterns, in shell form, are in `scripts/check-wording.sh`.
 *
 * Only string literals are read — a comment that names a retired word is not a finding — and a literal
 * with no letter of its own (a key, a format) is not member copy.
 */
class CopyRulesTest {
    /** Every string literal of the member app's Kotlin and string resources, with the file it is in. */
    private val literals: List<Literal> by lazy {
        val kotlin = listOf("src/main/kotlin", "src/member/kotlin").flatMap { root ->
            File(root).walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
                file.readLines().asSequence().filterNot { isComment(it) }.flatMap { line ->
                    STRING.findAll(stripTrailingComment(line)).map { Literal(file.path.replace('\\', '/'), it.groupValues[1]) }
                }
            }.toList()
        }
        val resources = File("src/main/res/values/strings.xml").takeIf { it.exists() }?.let { xml ->
            RES_STRING.findAll(xml.readText()).map { Literal("src/main/res/values/strings.xml", it.groupValues[1]) }.toList()
        }.orEmpty()
        kotlin + resources
    }

    /** What a member can read: the literal with its `$name` and `${…}` pieces taken out, if letters remain. */
    private val copy: List<Literal> by lazy {
        literals.map { it.copy(text = TEMPLATE.replace(it.text, "")) }.filter { LETTER.containsMatchIn(it.text) && ' ' in it.text.trim() || it.file.endsWith("strings.xml") }
    }

    @Test
    fun `the source was found`() {
        assertTrue(literals.size > 1_000, "read ${literals.size} literals - is the working directory the app module?")
        assertTrue(copy.isNotEmpty())
    }

    @Test
    fun `no string says The roster, Start today's, 5 cards or Check again`() {
        listOf("The roster", "Start today's", "5 cards", "Check again").forEach { retired ->
            assertNone("\"$retired\" is retired copy") { retired in it.text }
        }
    }

    @Test
    fun `the tour is six cards and no pager says of 5`() {
        assertEquals(6, Tour.CARDS)
        listOf("tour", "about", "welcome").forEach { screen ->
            val files = literals.filter { "/$screen/" in it.file }
            assertTrue(files.isNotEmpty(), "no literals read in $screen/")
            assertTrue(files.none { Regex("""\bof 5\b""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }, "\"of 5\" in $screen/")
        }
    }

    @Test
    fun `Standing is not a tab or a screen name`() {
        assertEquals(listOf("Today", "Holdouts", "Me"), Tab.entries.map { it.label })
        assertNone("\"Standing\" as a name") { Regex("""(?<!still )(?<!STILL )\bStanding\b|^STANDING$""").containsMatchIn(it.text) }
    }

    @Test
    fun `no boost reads plus a number - the boost is up to`() {
        assertNone("a \"+{n} boost\"") { Regex("""\+\s?(\$\{[^}]*}|\$\w+|[\d,.]+)\s*(SKR\s*)?boost""", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
        assertTrue(Format.boostLine(480_000_000uL).startsWith("Prize boost: up to "))
        assertEquals("Prize boost: up to 480 SKR", Format.boostLine(480_000_000uL))
        assertNone("a literal \"+ 480 boost\"") { Regex("""\+\d""").containsMatchIn(it.text) && "boost" in it.text }
    }

    @Test
    fun `the words the redesign specified are in the source`() {
        assertEquals("Members", DetailText.MEMBERS)
        assertTrue(literals.any { it.text == "Start" && it.file.endsWith("home/HomeScreen.kt") }, "Start on Today")
        assertTrue(literals.any { it.text == "Start" && it.file.endsWith("home/DayModel.kt") }, "Start on a Holdout's day")
        assertTrue(literals.any { it.text == "6 cards" && it.file.endsWith("about/AboutScreen.kt") }, "6 cards on About")
        assertTrue(literals.any { it.text == "up to " && it.file.endsWith("wallet/ConnectScreen.kt") }, "up to … boost on the first-open card")
        assertTrue(literals.any { it.text == " boost" && it.file.endsWith("wallet/ConnectScreen.kt") }, "up to … boost on the first-open card")
        assertTrue(literals.any { it.text.startsWith("Prize boost: up to ") })
    }

    @Test
    fun `the banned words of the copy rules still fail`() {
        val banned = listOf(
            "cohort" to Regex("""(?i)(?<!DEVNET )\bcohorts?\b"""),
            "league" to Regex("""(?i)\bleagues?\b"""),
            "challenge" to Regex("""(?i)\bchallenges?\b"""),
            "earn" to Regex("""(?i)\bearn(s|ed|ing|ings)?\b"""),
            "survivors" to Regex("""(?i)\bsurvivors?\b"""),
            "Easy" to Regex("""\bEasy\b"""),
            "Hard" to Regex("""\bHard\b"""),
        )
        // The patterns catch what they should, so the scan below is not vacuous.
        val samples = mapOf(
            "cohort" to "Join the cohort",
            "league" to "the league",
            "challenge" to "a challenge",
            "earn" to "you earn it",
            "survivors" to "3 survivors",
            "Easy" to "The Easy one",
            "Hard" to "The Hard one",
        )
        banned.forEach { (word, pattern) -> assertTrue(pattern.containsMatchIn(samples.getValue(word)), word) }
        assertTrue(!banned[0].second.containsMatchIn("DEVNET COHORT"))
        banned.forEach { (word, pattern) ->
            assertNone("\"$word\" in member copy") { literal -> pattern.containsMatchIn(literal.text) && literal.text !in ALLOWED }
        }
    }

    private fun assertNone(what: String, hit: (Literal) -> Boolean) {
        val hits = copy.filter(hit)
        assertTrue(hits.isEmpty(), "$what:\n" + hits.joinToString("\n") { "  ${it.file}: \"${it.text}\"" })
    }

    private fun isComment(line: String): Boolean = line.trim().let { it.startsWith("*") || it.startsWith("//") || it.startsWith("/*") }

    private fun stripTrailingComment(line: String): String = line.indexOf(" // ").let { if (it >= 0) line.substring(0, it) else line }

    private data class Literal(val file: String, val text: String)

    private companion object {
        val STRING = Regex(""""((?:[^"\\\n]|\\.)*)"""")
        val RES_STRING = Regex("""<string[^>]*>([^<]*)</string>""")
        val TEMPLATE = Regex("""\$\{[^}]*}|\$\w+""")
        val LETTER = Regex("[A-Za-z]")

        /** The one member sentence that uses a banned word in its harmless sense (the design book's streak-freeze line). */
        val ALLOWED = setOf("7 days in a row earn a streak freeze")
    }
}
