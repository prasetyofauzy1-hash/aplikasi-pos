package id.kasir.pos

private val rupiahInput = Regex(
    """(?:Rp\s*)?([0-9]+|[0-9]{1,3}(?:\.[0-9]{3})+)(?:,[0-9]{1,2})?""",
    RegexOption.IGNORE_CASE
)

internal fun parseRupiah(input: String, maxDigits: Int): Long? {
    val whole = rupiahInput.matchEntire(input.trim())?.groupValues?.get(1)?.replace(".", "") ?: return null
    return whole.takeIf { it.length <= maxDigits }?.toLongOrNull()
}
