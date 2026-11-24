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
        assertEquals(listOf("Kopi Susu", "Wafer Keju"), order.lines.map { it.productName })
        assertEquals(listOf(15_000L, 6_000L), order.lines.map { it.unitPrice })
    }

    @Test fun rejectsEmptyCartDeletedProductInsufficientStockAndCash() {
        assertThrows(SaleInputException::class.java) { calculateCheckout(listOf(coffee), emptyMap(), Payment.CASH, 15_000) }
        assertThrows(SaleInputException::class.java) { calculateCheckout(emptyList(), mapOf(1L to 1), Payment.QRIS, null) }
        assertThrows(SaleInputException::class.java) { calculateCheckout(listOf(coffee), mapOf(1L to 7), Payment.QRIS, null) }
        assertThrows(SaleInputException::class.java) { calculateCheckout(listOf(coffee), mapOf(1L to 1), Payment.CASH, 14_000) }
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
