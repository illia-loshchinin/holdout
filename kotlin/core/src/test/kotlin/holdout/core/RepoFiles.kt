package holdout.core

import java.io.File

/**
 * Reads a file committed elsewhere in the repository — the golden vectors the
 * program's own tests read, so the Kotlin client is pinned by the same bytes and
 * numbers rather than by a copy that could drift (research R2).
 *
 * Gradle runs tests from the module directory; walking up keeps this working
 * from an IDE or from a build rooted somewhere else.
 */
internal object RepoFiles {
    fun read(relative: String): String {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        error("$relative not found above ${System.getProperty("user.dir")}")
    }

    fun hex(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}
