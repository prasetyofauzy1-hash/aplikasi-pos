package id.kasir.pos

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
import java.math.RoundingMode

internal const val MAX_PURCHASE_COST = 999_999_999L

internal fun MenuItem.unitHpp(): Long? {
    if (recipe.isEmpty()) return product.directHpp?.takeIf { it >= 0 }
    val total = recipe.fold(BigDecimal.ZERO) { sum, (line, ingredient) ->
        val cost = ingredient.purchaseCost?.takeIf { it >= 0 } ?: return null
        val quantity = ingredient.purchaseQuantity?.takeIf { it > 0 } ?: return null
        if (line.quantity <= 0) return null
        sum + (cost.toBigDecimal() * line.quantity.toBigDecimal())
            .divide(quantity.toBigDecimal(), MathContext.DECIMAL128)
    }
    return try {
        total.setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (_: ArithmeticException) {
        null
    }
}

internal data class ProfitAmounts(
    val quantity: Long = 0,
    val withoutHpp: Long = 0,
    val revenue: BigInteger = BigInteger.ZERO,
    val hpp: BigInteger = BigInteger.ZERO
) {
    val grossProfit: BigInteger? get() = if (quantity > withoutHpp) revenue - hpp else null

    operator fun plus(other: ProfitAmounts) = ProfitAmounts(
        quantity + other.quantity, withoutHpp + other.withoutHpp, revenue + other.revenue, hpp + other.hpp
    )
}

internal data class MenuProfit(val id: Long?, val name: String, val amounts: ProfitAmounts) {
    val key: Any get() = id ?: name
}

internal data class ProfitReport(val totals: ProfitAmounts, val menus: List<MenuProfit>)

internal fun profitReport(receipts: List<Receipt>): ProfitReport {
    val products = mutableMapOf<Any, MenuProfit>()
    receipts.sortedWith(compareByDescending<Receipt> { it.sale.time }.thenByDescending { it.sale.id }).forEach { receipt ->
        receipt.lines.forEach { line ->
            val key = line.productId ?: line.productName
            val previous = products[key] ?: MenuProfit(line.productId, line.productName, ProfitAmounts())
            val quantity = line.quantity.toLong()
            val unitHpp = line.unitHpp
            val amounts = if (unitHpp == null) {
                ProfitAmounts(quantity = quantity, withoutHpp = quantity)
            } else {
                ProfitAmounts(
                    quantity = quantity,
                    revenue = line.unitPrice.toBigInteger() * quantity.toBigInteger(),
                    hpp = unitHpp.toBigInteger() * quantity.toBigInteger()
                )
            }
            products[key] = previous.copy(amounts = previous.amounts + amounts)
        }
    }
    return ProfitReport(
        products.values.fold(ProfitAmounts()) { total, product -> total + product.amounts },
        products.values.sortedWith(compareBy({ it.name.lowercase(indonesia) }, { it.id }))
    )
}
