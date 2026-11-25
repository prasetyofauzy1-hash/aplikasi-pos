package id.kasir.pos

internal enum class ReceiptStyle { STORE, ADDRESS, METADATA, ITEM, DETAIL, TOTAL, PAYMENT }

internal sealed interface ReceiptEntry {
    data object Rule : ReceiptEntry
    data class Row(
        val label: String,
        val amount: String? = null,
        val style: ReceiptStyle,
        val keepWithNext: Boolean = false
    ) : ReceiptEntry
}

internal fun Receipt.entries(showStoreName: Boolean): List<ReceiptEntry> = buildList {
    fun row(label: String, style: ReceiptStyle, amount: Long? = null, keepWithNext: Boolean = false) {
        if (label.isNotBlank()) add(ReceiptEntry.Row(label, amount?.let(::rupiah), style, keepWithNext))
    }

    if (showStoreName) row(sale.storeName, ReceiptStyle.STORE)
    row(sale.storeAddress, ReceiptStyle.ADDRESS)
    row("Tanggal: ${dateLabel(sale.time)}", ReceiptStyle.METADATA)
    if (sale.id > 0) row(ticket(sale.id), ReceiptStyle.METADATA)
    sale.cashierLabel?.let { row(it, ReceiptStyle.METADATA) }
    add(ReceiptEntry.Rule)
    lines.forEach { item ->
        val details = item.detailLines()
        row(item.productName, ReceiptStyle.ITEM, keepWithNext = true)
        val unitPrice = item.unitPrice + item.discount
        row("${item.quantity} x ${rupiah(unitPrice)}", ReceiptStyle.DETAIL, item.quantity * unitPrice,
            keepWithNext = details.isNotEmpty())
        details.forEachIndexed { index, detail ->
            row(detail, ReceiptStyle.DETAIL, keepWithNext = index < details.lastIndex)
        }
    }
    add(ReceiptEntry.Rule)
    if (hasAdjustments) row("Subtotal", ReceiptStyle.DETAIL, sale.subtotal)
    charges.forEach { row(it.name, ReceiptStyle.DETAIL, it.amount) }
    row("Total", ReceiptStyle.TOTAL, sale.total)
    add(ReceiptEntry.Rule)
    row(sale.method, ReceiptStyle.PAYMENT, sale.paid)
    if (sale.method == Payment.CASH.label && sale.paid > sale.total) {
        row("Kembalian", ReceiptStyle.PAYMENT, sale.paid - sale.total)
    }
}
