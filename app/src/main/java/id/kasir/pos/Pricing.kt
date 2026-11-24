package id.kasir.pos

import org.json.JSONArray
import org.json.JSONObject

internal const val MAX_PERCENT = 10_000

data class ProductOption(val name: String, val extraPrice: Long)

internal fun cleanOptionText(text: String, limit: Int): String =
    text.replace(Regex("[\\t\\r\\n]+"), " ").take(limit)

internal fun parseProductOptions(text: String): List<ProductOption> = text.lines().mapNotNull { line ->
    val parts = line.split('\t')
    val name = parts[0].trim()
    if (name.isEmpty()) null else ProductOption(name, parts.getOrNull(1)?.toLongOrNull()?.coerceAtLeast(0) ?: 0)
}

internal fun formatProductOptions(options: List<ProductOption>): String =
    options.filter { it.name.isNotBlank() }.joinToString("\n") { "${cleanOptionText(it.name, 40).trim()}\t${it.extraPrice}" }

internal fun selectedOptionNames(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

internal fun formatSelectedOptions(names: List<String>): String = names.joinToString("\n")

internal fun percentOf(base: Long, basisPoints: Int): Long = try {
    Math.addExact(Math.multiplyExact(base, basisPoints.toLong()), 5_000L) / 10_000L
} catch (_: ArithmeticException) {
    throw SaleInputException("Total terlalu besar untuk dihitung.")
}

internal fun parsePercent(text: String): Int? {
    val value = text.trim().removeSuffix("%").trim().replace(',', '.')
    if (!Regex("\\d{1,3}(\\.\\d{1,2})?").matches(value)) return null
    val whole = value.substringBefore('.').toInt()
    val fraction = value.substringAfter('.', "").padEnd(2, '0').toInt()
    return (whole * 100 + fraction).takeIf { it in 1..MAX_PERCENT }
}

internal fun percentLabel(basisPoints: Int): String {
    val whole = basisPoints / 100
    val fraction = basisPoints % 100
    return if (fraction == 0) "$whole%" else "$whole,${fraction.toString().padStart(2, '0').trimEnd('0')}%"
}

/** Per-unit product promo: a percentage (in basis points) or a fixed Rupiah amount; both zero means none. */
data class Discount(val percent: Int = 0, val amount: Long = 0) {
    val isNone get() = percent <= 0 && amount <= 0
    fun of(base: Long): Long = when {
        percent > 0 -> percentOf(base, percent.coerceAtMost(MAX_PERCENT))
        else -> amount.coerceAtLeast(0)
    }.coerceAtMost(base)
    val label get() = if (percent > 0) percentLabel(percent) else rupiah(amount)
}

val Product.promo: Discount get() = Discount(promoPercent, promoAmount)
val Product.promoPerUnit: Long get() = promo.of(price)

data class ExtraCharge(val name: String, val percent: Int = 0, val amount: Long = 0, val enabled: Boolean = true) {
    val label get() = if (percent > 0) "$name (${percentLabel(percent)})" else name
    fun of(base: Long): Long = if (percent > 0) percentOf(base, percent) else amount
}

internal fun chargesToJson(charges: List<ExtraCharge>): String = JSONArray().apply {
    charges.forEach {
        put(JSONObject().put("name", it.name).put("percent", it.percent).put("amount", it.amount).put("enabled", it.enabled))
    }
}.toString()

internal fun chargesFromJson(json: String?): List<ExtraCharge> = try {
    val array = JSONArray(json ?: return emptyList())
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        ExtraCharge(item.getString("name"), item.optInt("percent"), item.optLong("amount"), item.optBoolean("enabled", true))
    }
} catch (_: Exception) {
    emptyList()
}

/** Unit price for a cart line, or null when a chosen option no longer exists on the product. */
internal fun cartLineUnitPrice(product: Product, item: CartItem): Long? {
    val available = parseProductOptions(product.options).associateBy { it.name }
    var extra = 0L
    for (name in selectedOptionNames(item.options)) extra += available[name]?.extraPrice ?: return null
    return product.price - product.promoPerUnit + extra
}

internal fun cartSubtotal(products: List<Product>, cart: List<CartItem>): Long {
    val byId = products.associateBy { it.id }
    return cart.sumOf { item -> byId[item.productId]?.let { cartLineUnitPrice(it, item) }?.times(item.quantity) ?: 0L }
}

internal fun cartOptionIssue(products: List<Product>, cart: List<CartItem>): String? {
    val byId = products.associateBy { it.id }
    return cart.firstNotNullOfOrNull { item ->
        val product = byId[item.productId] ?: return@firstNotNullOfOrNull null
        if (cartLineUnitPrice(product, item) == null) "Pilihan ${product.name} sudah berubah. Hapus lalu tambahkan lagi." else null
    }
}

data class OrderTotals(val subtotal: Long, val charges: List<SaleCharge>, val total: Long)

internal fun orderTotals(subtotal: Long, charges: List<ExtraCharge>): OrderTotals {
    val applied = charges.filter { it.enabled }.map { SaleCharge(saleId = 0, name = it.label, amount = it.of(subtotal)) }
    val total = try {
        applied.fold(subtotal) { sum, charge -> Math.addExact(sum, charge.amount) }
    } catch (_: ArithmeticException) {
        throw SaleInputException("Total terlalu besar untuk dihitung.")
    }
    return OrderTotals(subtotal, applied, total)
}

private val optionDraftLine = Regex("^(.*)\\+\\s*((?:Rp\\.?\\s*)?\\d[\\d.,]*)$", RegexOption.IGNORE_CASE)

/** Parses the owner's option draft: one option per line, optionally ending in "+5000" for an extra price. */
internal fun parseOptionDraft(text: String): List<ProductOption>? = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { line ->
    val match = optionDraftLine.matchEntire(line)
    val name = cleanOptionText(match?.groupValues?.get(1) ?: line, 40).trim()
    val extra = match?.groupValues?.get(2)?.let { parseRupiah(it.replaceFirst(Regex("^Rp\\.", RegexOption.IGNORE_CASE), "Rp"), 9) }
    if (name.isEmpty() || (match != null && extra == null)) return null
    ProductOption(name, extra ?: 0)
}.takeIf { options -> options.map { it.name.lowercase(indonesia) }.toSet().size == options.size }

internal fun formatOptionDraft(options: List<ProductOption>): String =
    options.joinToString("\n") { when {
        it.extraPrice > 0 -> "${it.name} +${it.extraPrice}"
        optionDraftLine.matches(it.name) -> "${it.name} +0"
        else -> it.name
    } }

internal fun SaleLine.detailLines(): List<String> = buildList {
    if (options.isNotBlank()) add(options)
    if (note.isNotBlank()) add("Catatan: $note")
    if (discount > 0) add("Promo -${rupiah(discount * quantity)}")
}
