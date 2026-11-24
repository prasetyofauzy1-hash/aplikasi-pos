package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SalesExportTest {
    private val jakarta = TimeZone.getTimeZone("Asia/Jakarta")

    @Test fun dayUsesLocalMidnightAndHandlesDaylightSaving() {
        val newYork = TimeZone.getTimeZone("America/New_York")
        val date = exportDate(2026, Calendar.MARCH, 8)
        val period = salesExportPeriod(SalesGrouping.DAY, date, newYork)

        assertEquals(date + 5 * HOUR, period.start)
        assertEquals(date + 28 * HOUR, period.endExclusive)
        assertEquals("penjualan-harian-2026-03-08.csv", period.filename)
    }

    @Test fun sundayBelongsToMondayThroughSundayWeekAcrossYears() {
        val period = salesExportPeriod(SalesGrouping.WEEK, exportDate(2026, Calendar.JANUARY, 4), jakarta)

        assertEquals(exportDate(2025, Calendar.DECEMBER, 29) - 7 * HOUR, period.start)
        assertEquals(exportDate(2026, Calendar.JANUARY, 5) - 7 * HOUR, period.endExclusive)
        assertEquals("penjualan-mingguan-2025-12-29_2026-01-04.csv", period.filename)
    }

    @Test fun monthIncludesLeapDayAndExcludesFirstDayOfNextMonth() {
        val period = salesExportPeriod(SalesGrouping.MONTH, exportDate(2024, Calendar.FEBRUARY, 15), jakarta)

        assertEquals(exportDate(2024, Calendar.FEBRUARY, 1) - 7 * HOUR, period.start)
        assertEquals(exportDate(2024, Calendar.MARCH, 1) - 7 * HOUR, period.endExclusive)
        assertEquals("penjualan-bulanan-2024-02-01_2024-02-29.csv", period.filename)
    }

    @Test fun csvSeparatesPaymentCountsAndTotalsAndEscapesProductNames() {
        val period = salesExportPeriod(SalesGrouping.DAY, exportDate(2026, Calendar.MARCH, 9), jakarta)
        val cash = receipt(1, period.start, 9_000, 10_000, Payment.CASH, "Roti; \"Cokelat\"\nKacang")
        val qris = receipt(2, period.start + HOUR, 15_000, 15_000, Payment.QRIS, "Kopi Susu")

        assertEquals(
            listOf(
                "\uFEFFPeriode penjualan;09 Mar 2026;;;",
                ";;;;",
                "Ringkasan;Jumlah transaksi;Total (Rp);;",
                "Tunai;1;9000;;",
                "QRIS;1;15000;;",
                "Total keseluruhan;2;24000;;",
                ";;;;",
                "Tanggal dan waktu;No. transaksi;Barang;Metode pembayaran;Total (Rp)",
                "09/03/2026 00:00:00;TRX-00001;\"2 × Roti; \"\"Cokelat\"\"\nKacang\";Tunai;9000",
                "09/03/2026 01:00:00;TRX-00002;2 × Kopi Susu;QRIS;15000",
                ""
            ).joinToString("\r\n"),
            salesCsv(period, listOf(cash, qris))
        )
    }

    @Test fun emptyPeriodStillExportsBothPaymentMethods() {
        val period = salesExportPeriod(SalesGrouping.DAY, exportDate(2026, Calendar.MARCH, 9), jakarta)
        val summary = salesCsv(period, emptyList()).split("\r\n").subList(3, 6)
        assertEquals(listOf("Tunai;0;0;;", "QRIS;0;0;;", "Total keseluruhan;0;0;;"), summary)
    }

    private fun receipt(id: Long, time: Long, total: Long, paid: Long, method: Payment, product: String) =
        Receipt(Sale(id, time, total, paid, method.label, "Toko", "", ""),
            listOf(SaleLine(saleId = id, productName = product, unitPrice = total / 2, quantity = 2)))

    private companion object {
        const val HOUR = 3_600_000L
    }
}
