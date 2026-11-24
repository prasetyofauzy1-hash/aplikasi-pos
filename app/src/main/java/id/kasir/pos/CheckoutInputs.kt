package id.kasir.pos

internal fun canSetCartQuantity(current: Int, quantity: Int, stock: Int): Boolean =
    quantity > 0 && (quantity <= stock || quantity < current)

internal fun updateCartQuantity(cart: Map<Long, Int>, product: Product, quantity: Int): Map<Long, Int> = when {
    quantity == 0 -> cart - product.id
    canSetCartQuantity(cart[product.id] ?: 0, quantity, product.stock) -> cart + (product.id to quantity)
    else -> cart
}

internal fun quickCashAmounts(total: Long): List<Long> =
    listOf(5_000L, 10_000L, 20_000L, 50_000L, 100_000L).filter { it > total }.take(2)
