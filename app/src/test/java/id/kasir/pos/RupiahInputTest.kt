package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RupiahInputTest {
    @Test fun parsesPlainAndIndonesianFormattedRupiah() {
        assertEquals(19_000L, parseRupiah("19000", 9))
        assertEquals(19_000L, parseRupiah("Rp19.000,00", 9))
        assertEquals(1_234_567L, parseRupiah(" rp 1.234.567,89 ", 9))
        assertEquals(0L, parseRupiah("0,75", 18))
    }

    @Test fun rejectsMalformedOrOverLimitAmountsWithoutTruncating() {
        listOf("", "Rp", "-19.000", "Rp19.00,00", "1,900.00", "19.000.000.000").forEach {
            assertNull(it, parseRupiah(it, 9))
        }
        assertEquals(19_000_000_000L, parseRupiah("19.000.000.000", 18))
    }
}
