package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutTest {
    private val coffee = Product(1, "Kopi Susu", "Minuman", 15_000, 6)
    private val wafer = Product(2, "Wafer Keju", "Camilan", 6_000, 2)

    @Test fun cashTotalsAndSnapshotsProducts() {
        val order = calculateCheckout(listOf(coffee, wafer), mapOf(1L to 2, 2L to 1), Payment.CASH, 40_000)
        assertEquals(36_000L, order.total)
        assertEquals(4_000L, order.paid - order.total)
        assertEquals(listOf(1L, 2L), order.lines.map { it.productId })
        assertEquals(listOf("Kopi Susu", "Wafer Keju"), order.lines.map { it.productName })
        assertEquals(listOf(15_000L, 6_000L), order.lines.map { it.unitPrice })
    }

    @Test fun cartQuantityCanBeReplacedOrRemovedWithoutExceedingStock() {
        val cart = mapOf(1L to 1, 2L to 1)
        assertEquals(mapOf(1L to 6, 2L to 1), updateCartQuantity(cart, coffee, 6))
        assertEquals(cart, updateCartQuantity(cart, coffee, 7))
        assertEquals(cart, updateCartQuantity(cart, coffee, -1))
        assertEquals(mapOf(2L to 1), updateCartQuantity(cart, coffee, 0))
    }

    @Test fun cashShortcutsOnlyOfferNotesAboveTheOrderTotal() {
        assertEquals(listOf(20_000L, 50_000L), quickCashAmounts(15_000L))
        assertEquals(listOf(50_000L, 100_000L), quickCashAmounts(49_000L))
        assertEquals(listOf(100_000L), quickCashAmounts(50_000L))
        assertTrue(quickCashAmounts(100_000L).isEmpty())
    }

    @Test fun rejectsEmptyCartDeletedProductInsufficientStockAndCash() {
        assertThrows(SaleInputException::class.java) { calculateCheckout(listOf(coffee), emptyMap(), Payment.CASH, 15_000) }
        assertThrows(SaleInputException::class.java) { calculateCheckout(emptyList(), mapOf(1L to 1), Payment.QRIS, null) }
        assertThrows(SaleInputException::class.java) { calculateCheckout(listOf(coffee), mapOf(1L to 7), Payment.QRIS, null) }
        assertThrows(SaleInputException::class.java) { calculateCheckout(listOf(coffee), mapOf(1L to 1), Payment.CASH, 14_000) }
    }

    @Test fun largePriceAndQuantityKeepExactRupiahTotals() {
        val premium = Product(3, "Jus Berry Go Premium", "Jus", 999_999_999, 99_999)
        val cart = mapOf(3L to 9_999)
        val order = calculateCheckout(listOf(premium), cart, Payment.CASH, 10_000_000_000_000)

        assertEquals(9_998_999_990_001L, order.total)
        assertEquals(order.total, cartSubtotal(listOf(premium), listOf(CartItem(productId = 3, quantity = 9_999))))
        assertEquals(1_000_009_999L, order.paid - order.total)
        assertEquals("Rp9.998.999.990.001", rupiah(order.total))
    }

    @Test fun itemCountDoesNotWrapAtIntLimit() {
        assertEquals(2_147_483_648L, cartItemCount(mapOf(1L to Int.MAX_VALUE, 2L to 1)))
    }

    @Test fun qrisRecordsExactTotalAndReceiptOmitsChange() {
        val order = calculateCheckout(listOf(coffee), mapOf(1L to 1), Payment.QRIS, null)
        assertEquals(15_000L, order.total)
        assertEquals(order.total, order.paid)
        val receipt = Receipt(Sale(id = 4, time = 0, total = order.total, paid = order.paid,
            method = Payment.QRIS.label, storeName = "Toko Uji", storeAddress = "Jalan 1", logoName = ""), order.lines)
        val text = receipt.asText()
        assertTrue(text.contains("Toko Uji\nJalan 1"))
        assertTrue(text.contains("1 × Kopi Susu — Rp15.000"))
        assertTrue(text.contains("QRIS: Rp15.000"))
        assertFalse(text.contains("Kembalian"))
    }
}
