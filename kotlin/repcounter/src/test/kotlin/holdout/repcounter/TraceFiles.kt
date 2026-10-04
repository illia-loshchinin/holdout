package holdout.repcounter

import java.io.File
import java.util.zip.GZIPInputStream

/**
 * A recorded sitting on disk: `<name>.trace.csv`, or `<name>.trace.csv.gz` as
 * promoted sittings are committed (scripts/label-reps.mjs --promote), with its
 * labels beside it as `<name>.labels.csv`.
 */
object TraceFiles {
    fun isTrace(file: File): Boolean = file.isFile && (file.name.endsWith(".trace.csv") || file.name.endsWith(".trace.csv.gz"))

    /** The path without `.trace.csv(.gz)`: where its labels, video and verdicts sit. */
    fun base(file: File): String = file.path.removeSuffix(".gz").removeSuffix(".trace.csv")

    /** The sitting's name: the file name without `.trace.csv(.gz)`. */
    fun name(file: File): String = file.name.removeSuffix(".gz").removeSuffix(".trace.csv")

    fun lines(file: File): List<String> = if (file.name.endsWith(".gz")) {
        GZIPInputStream(file.inputStream()).bufferedReader().use { it.readLines() }
    } else {
        file.readLines()
    }

    fun labels(file: File): File = File(base(file) + ".labels.csv")

    /** Every trace in [dir] and below, by path. */
    fun under(dir: File): List<File> = dir.walkTopDown().filter(::isTrace).sortedBy { it.path }.toList()
}
