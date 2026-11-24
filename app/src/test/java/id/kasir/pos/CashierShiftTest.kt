package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CashierShiftTest {
    @Test fun expectedCashIncludesOpeningCashAndCashSales() {
        val summary = ShiftSummary(CashierShift(openedAt = 1L, openingCash = 50_000L), cashSales = 35_000L)
        assertEquals(85_000L, summary.expectedCash)
        assertEquals(-5_000L, summary.difference(80_000L))
        assertEquals(0L, summary.difference(85_000L))
        assertEquals(5_000L, summary.difference(90_000L))
    }

    @Test fun openingAndClosingAcceptZeroAndWholeRupiahOnly() {
        assertEquals(0L, parseShiftCash("0"))
        assertEquals(50_000L, parseShiftCash("Rp50.000"))
        listOf("", "-10000", "50.000,50", "1000000000000000000").forEach {
            assertNull(parseShiftCash(it))
        }
    }
}
