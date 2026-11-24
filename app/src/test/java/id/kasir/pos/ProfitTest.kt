package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfitTest {
    private val product = Product(1, "Berry Go", "Jus", 20_000, 0)

    @Test fun `hpp prorates purchase cost in recipe units and rounds once per serving`() {
        val berry = component(1, 200, 25_000, 1_000)
        val banana = component(2, 200, 12_000, 1_000)
        assertEquals(7_400L, MenuItem(product, listOf(berry, banana)).unitHpp())

        val fractional = (1L..3L).map { component(it, 1, 1, 2) }
        assertEquals(2L, MenuItem(product, fractional).unitHpp())
    }

    @Test fun `repeating prorated costs still round up at an exact half rupiah`() {
        val thirds = (1L..3L).map { component(it, 1, 1, 3) }
        val half = component(4, 1, 1, 2)
        assertEquals(2L, MenuItem(product, thirds + half).unitHpp())
    }

    @Test fun `missing recipe cost or quantity is unknown even with a direct cost`() {
        val stocked = product.copy(directHpp = 3_000L)
        assertNull(MenuItem(stocked, listOf(component(1, 1, null, null))).unitHpp())
        assertNull(MenuItem(stocked, listOf(component(1, 1, 200, 0))).unitHpp())
        assertNull(MenuItem(stocked, listOf(component(1, 1, 200, null))).unitHpp())
    }

    @Test fun `zero cost is known and direct stock can have manual hpp`() {
        assertNull(MenuItem(product, emptyList()).unitHpp())
        assertEquals(3_000L, MenuItem(product.copy(directHpp = 3_000), emptyList()).unitHpp())
        assertEquals(0L, MenuItem(product, listOf(component(1, 1, 0, 1))).unitHpp())
    }

    @Test fun `profit uses recorded selling price and cost and omits unknown portions`() {
        val report = profitReport(listOf(
            receipt(1, line(product.id, "Berry Go", 20_000, 2, 7_400)),
            receipt(2, line(product.id, "Berry Baru", 18_000, 1, 8_000)),
            receipt(3, line(product.id, "Berry Baru", 20_000, 3, null)),
            receipt(4, line(null, "Berry Baru", 20_000, 1, null))
        ))
        val menu = report.menus.single { it.id == product.id }
        assertEquals("Berry Baru", menu.name)
        assertEquals(6L, menu.amounts.quantity)
        assertEquals(3L, menu.amounts.withoutHpp)
        assertEquals(58_000.toBigInteger(), menu.amounts.revenue)
        assertEquals(22_800.toBigInteger(), menu.amounts.hpp)
        assertEquals(35_200.toBigInteger(), menu.amounts.grossProfit)
        assertNull(report.menus.single { it.id == null }.amounts.grossProfit)
        assertEquals(4L, report.totals.withoutHpp)
        assertEquals(menu.amounts.grossProfit, report.totals.grossProfit)
    }

    @Test fun `gross loss is shown rather than clamped to zero`() {
        val report = profitReport(listOf(receipt(1, line(1, "Berry Go", 10_000, 4, 12_000))))
        assertEquals((-8_000).toBigInteger(), report.totals.grossProfit)
    }

    private fun component(id: Long, recipeAmount: Int, cost: Long?, purchaseAmount: Int?) =
        RecipeComponent(RecipeLine(product.id, id, recipeAmount),
            Ingredient(id, "Bahan $id", "g", 0, cost, purchaseAmount))

    private fun line(id: Long?, name: String, price: Long, quantity: Int, cost: Long?) =
        SaleLine(saleId = 0, productName = name, unitPrice = price, quantity = quantity, productId = id, unitHpp = cost)

    private fun receipt(id: Long, vararg lines: SaleLine) =
        Receipt(Sale(id, id, 0, 0, "Tunai", "", "", ""), lines.toList())
}
