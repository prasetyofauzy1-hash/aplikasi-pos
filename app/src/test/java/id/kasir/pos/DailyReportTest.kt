package id.kasir.pos

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyReportTest {
    @Test fun localDayIsBoundedByBothMidnights() {
        val jakarta = TimeZone.getTimeZone("Asia/Jakarta")
        val date = reportDate(2026, Calendar.NOVEMBER, 24)
        val period = reportPeriod(date, jakarta)

        assertEquals(date - 7 * 3_600_000L, period.start)
        assertEquals(moveReportDate(date, -1), reportDate(period.start - 1, jakarta))
        assertEquals(date, reportDate(period.start, jakarta))
        assertEquals(date, reportDate(period.end - 1, jakarta))
        assertEquals(moveReportDate(date, 1), reportDate(period.end, jakarta))
    }

    @Test fun nextMidnightUsesCalendarDayDuringDaylightSaving() {
        val zone = TimeZone.getTimeZone("America/New_York")
        val spring = reportPeriod(reportDate(2026, Calendar.MARCH, 8), zone)
        val fall = reportPeriod(reportDate(2026, Calendar.NOVEMBER, 1), zone)

        assertEquals(23 * 3_600_000L, spring.end - spring.start)
        assertEquals(25 * 3_600_000L, fall.end - fall.start)
    }

    @Test fun repeatedMidnightBelongsToTheNewDayFromItsFirstOccurrence() {
        val havana = TimeZone.getTimeZone("America/Havana")
        val date = reportDate(2026, Calendar.NOVEMBER, 1)
        val firstMidnight = date + 4 * 3_600_000L
        val period = reportPeriod(date, havana)

        assertEquals(firstMidnight, period.start)
        assertEquals(firstMidnight, reportPeriod(moveReportDate(date, -1), havana).end)
        assertEquals(25 * 3_600_000L, period.end - period.start)
    }

    @Test fun reconcilesMethodsAndProductsUsingRecordedSaleAndLineValues() {
        val cash = Sale(id = 1, time = 5, total = 30_000, paid = 50_000, method = Payment.CASH.label,
            storeName = "Jus.go", storeAddress = "Manahan", logoName = "")
        val cashReceipt = Receipt(cash, listOf(SaleLine(saleId = 1, productName = "Berry Go", unitPrice = 15_000, quantity = 2)))
        val qris = cash.copy(id = 2, total = 28_000, paid = 28_000, method = Payment.QRIS.label)
        val qrisReceipt = Receipt(qris, listOf(
            SaleLine(saleId = 2, productName = "Berry Go", unitPrice = 16_000, quantity = 1),
            SaleLine(saleId = 2, productName = "Jus Pisang", unitPrice = 12_000, quantity = 1)
        ))

        val report = dailyReport(listOf(qrisReceipt, cashReceipt))

        assertEquals(listOf(qris, cash), report.sales)
        assertEquals(58_000L, report.total)
        assertEquals(mapOf("Tunai" to 30_000L, "QRIS" to 28_000L), report.totalsByPayment)
        assertEquals(listOf(ProductSales("Berry Go", 3, 46_000), ProductSales("Jus Pisang", 1, 12_000)), report.products)
        assertEquals(report.total, report.products.sumOf { it.revenue })
    }

    @Test fun emptyDayHasNoSalesOrProductsAndZeroRevenue() {
        assertEquals(DailyReport(emptyList(), 0, emptyMap(), emptyList()), dailyReport(emptyList()))
    }
}
