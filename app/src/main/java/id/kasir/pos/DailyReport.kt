package id.kasir.pos

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone

private val reportUtc = TimeZone.getTimeZone("UTC")

private fun reportCalendar(date: Long): Calendar =
    Calendar.getInstance(reportUtc).apply { timeInMillis = date }

internal fun reportDate(year: Int, month: Int, day: Int): Long =
    Calendar.getInstance(reportUtc).apply { clear(); set(year, month, day) }.timeInMillis

internal fun reportDate(time: Long, zone: TimeZone): Long =
    Calendar.getInstance(zone).apply { timeInMillis = time }.run {
        reportDate(get(Calendar.YEAR), get(Calendar.MONTH), get(Calendar.DAY_OF_MONTH))
    }

internal fun moveReportDate(date: Long, days: Int): Long =
    reportCalendar(date).apply { add(Calendar.DAY_OF_MONTH, days) }.timeInMillis

internal fun reportDateLabel(date: Long): String =
    SimpleDateFormat("dd MMM yyyy", indonesia).apply { timeZone = reportUtc }.format(date)

internal data class ReportPeriod(val start: Long, val end: Long)

internal fun reportPeriod(date: Long, zone: TimeZone): ReportPeriod {
    fun midnight(value: Long): Long {
        val day = reportCalendar(value)
        val midnight = Calendar.getInstance(zone).apply {
            clear()
            set(day[Calendar.YEAR], day[Calendar.MONTH], day[Calendar.DAY_OF_MONTH])
        }.timeInMillis
        val offsetBefore = zone.getOffset(midnight - 86_400_000L)
        val earlierMidnight = value - offsetBefore
        return if (earlierMidnight < midnight && zone.getOffset(earlierMidnight) == offsetBefore) {
            earlierMidnight
        } else {
            midnight
        }
    }
    return ReportPeriod(midnight(date), midnight(moveReportDate(date, 1)))
}

internal data class ProductSales(
    val name: String,
    val quantity: Long,
    val revenue: Long,
    val productId: Long? = null,
    val latestUnitPrice: Long? = null
) {
    val key: Any get() = productId ?: name

    val duplicateNameDetail: String get() =
        if (productId == null) "Catatan lama (tanpa kode produk)"
        else "Harga terakhir ${rupiah(checkNotNull(latestUnitPrice))} · #$productId"
}

internal data class DailyReport(
    val sales: List<Sale>,
    val total: Long,
    val totalsByPayment: Map<String, Long>,
    val products: List<ProductSales>
)

internal fun dailyReport(receipts: List<Receipt>): DailyReport {
    val sales = receipts.map { it.sale }
    val products = mutableMapOf<Any, ProductSales>()
    receipts.sortedWith(compareByDescending<Receipt> { it.sale.time }.thenByDescending { it.sale.id }).forEach { receipt ->
        receipt.lines.forEach { line ->
            val key: Any = line.productId ?: line.productName
            val previous = products[key]
            products[key] = ProductSales(
                name = previous?.name ?: line.productName,
                quantity = (previous?.quantity ?: 0L) + line.quantity,
                revenue = (previous?.revenue ?: 0L) + line.unitPrice * line.quantity,
                productId = line.productId,
                latestUnitPrice = previous?.latestUnitPrice ?: line.unitPrice.takeIf { line.productId != null }
            )
        }
    }
    return DailyReport(
        sales = sales,
        total = sales.sumOf { it.total },
        totalsByPayment = sales.groupingBy { it.method }.fold(0L) { total, sale -> total + sale.total },
        products = products.values.sortedBy { it.name.lowercase(indonesia) }
    )
}
