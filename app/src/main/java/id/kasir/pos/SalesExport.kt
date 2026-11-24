package id.kasir.pos

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

internal enum class SalesGrouping(val label: String, val fileLabel: String) {
    DAY("Hari", "harian"), WEEK("Minggu", "mingguan"), MONTH("Bulan", "bulanan")
}

internal val exportDateZone: TimeZone
    get() = TimeZone.getTimeZone("UTC")

internal fun exportDate(year: Int, month: Int, day: Int): Long =
    GregorianCalendar(exportDateZone).apply {
        clear()
        set(year, month, day)
    }.timeInMillis

internal fun salesDateLabel(date: Long): String =
    exportDateFormat("dd MMM yyyy", exportDateZone).format(Date(date))

internal data class SalesExportPeriod(
    val grouping: SalesGrouping,
    val firstDate: Long,
    val lastDate: Long,
    val start: Long,
    val endExclusive: Long,
    val zone: TimeZone
) {
    val label: String
        get() = if (firstDate == lastDate) salesDateLabel(firstDate)
            else "${salesDateLabel(firstDate)} – ${salesDateLabel(lastDate)}"

    val filename: String
        get() {
            val format = exportDateFormat("yyyy-MM-dd", exportDateZone)
            val first = format.format(Date(firstDate))
            val last = format.format(Date(lastDate))
            val suffix = if (first == last) first else "${first}_${last}"
            return "penjualan-${grouping.fileLabel}-$suffix.csv"
        }
}

internal fun salesExportPeriod(grouping: SalesGrouping, date: Long, zone: TimeZone): SalesExportPeriod {
    val calendar = GregorianCalendar(exportDateZone).apply { timeInMillis = date }
    when (grouping) {
        SalesGrouping.DAY -> Unit
        SalesGrouping.WEEK -> calendar.add(Calendar.DAY_OF_MONTH, -((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7))
        SalesGrouping.MONTH -> calendar.set(Calendar.DAY_OF_MONTH, 1)
    }

    fun localMidnight() = GregorianCalendar(zone).apply {
        clear()
        set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis

    val firstDate = calendar.timeInMillis
    val start = localMidnight()
    when (grouping) {
        SalesGrouping.DAY -> calendar.add(Calendar.DAY_OF_MONTH, 1)
        SalesGrouping.WEEK -> calendar.add(Calendar.DAY_OF_MONTH, 7)
        SalesGrouping.MONTH -> calendar.add(Calendar.MONTH, 1)
    }
    val end = localMidnight()
    calendar.add(Calendar.DAY_OF_MONTH, -1)
    return SalesExportPeriod(grouping, firstDate, calendar.timeInMillis, start, end, zone)
}

internal fun salesCsv(period: SalesExportPeriod, receipts: List<Receipt>): String = buildString {
    fun row(vararg cells: String) {
        repeat(5) { index ->
            if (index > 0) append(';')
            val cell = cells.getOrNull(index).orEmpty()
            if (cell.any { it == ';' || it == '\r' || it == '\n' || it == '"' }) {
                append('"').append(cell.replace("\"", "\"\"")).append('"')
            } else {
                append(cell)
            }
        }
        append("\r\n")
    }

    append('\uFEFF')
    row("Periode penjualan", period.label)
    row()
    row("Ringkasan", "Jumlah transaksi", "Total (Rp)")
    val byPayment = receipts.groupBy { it.sale.method }
    for (method in Payment.entries) {
        if (method == Payment.QRIS_AUTO && method.label !in byPayment) continue
        val matching = byPayment[method.label].orEmpty()
        row(method.label, matching.size.toString(), matching.sumOf { it.sale.total }.toString())
    }
    row("Total keseluruhan", receipts.size.toString(), receipts.sumOf { it.sale.total }.toString())
    row()
    row("Tanggal dan waktu", "No. transaksi", "Barang", "Metode pembayaran", "Total (Rp)")
    val dateTime = exportDateFormat("dd/MM/yyyy HH:mm:ss", period.zone)
    for (receipt in receipts) {
        val sale = receipt.sale
        row(dateTime.format(Date(sale.time)), ticket(sale.id),
            receipt.lines.sortedBy { it.id }.joinToString(", ") { "${it.quantity} × ${it.productName}" },
            sale.method, sale.total.toString())
    }
}

private fun exportDateFormat(pattern: String, zone: TimeZone) =
    SimpleDateFormat(pattern, Locale("id", "ID")).apply { timeZone = zone }
