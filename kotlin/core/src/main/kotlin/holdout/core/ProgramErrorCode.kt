package holdout.core

/**
 * The program error a refusal names, however the node, the wallet or a library
 * words it — `custom program error: 0x…`, `"Custom":n` or `Custom(n)`. One
 * reading for the crank and the app alike, so an expected refusal is never
 * recognised by one and missed by the other.
 */
object ProgramErrorCode {
    private val patterns = listOf(
        Regex("custom program error: 0x([0-9a-fA-F]+)") to 16,
        Regex("\"Custom\"\\s*:\\s*(\\d+)") to 10,
        Regex("Custom\\((\\d+)\\)") to 10,
    )

    fun of(message: String?): Int? {
        if (message == null) return null
        return patterns.firstNotNullOfOrNull { (regex, radix) -> regex.find(message)?.groupValues?.get(1)?.toIntOrNull(radix) }
    }
}
