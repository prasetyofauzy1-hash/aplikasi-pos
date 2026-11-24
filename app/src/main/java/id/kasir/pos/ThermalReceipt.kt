package id.kasir.pos

import java.text.Normalizer

internal enum class ThermalPaper(val label: String, val columns: Int) {
    MM58("58 mm", 32),
    MM80("80 mm", 48)
}

private val combiningMarks = Regex("\\p{M}+")
private const val vulgarFractions = "¼½¾⅐-⅞"
private val mixedNumberBoundary = Regex("(?<=\\p{Nd})(?=[$vulgarFractions])")
private val printerSymbols = Regex("[$vulgarFractions⁄‘’‚‛“”„‟‐‑‒–—―−…]")
private val whitespace = Regex("[\\s\\p{Z}]+")
private val nonPrintable = Regex("[^ -~]")

private fun String.printerText() = Normalizer.normalize(this, Normalizer.Form.NFD)
    .replace(combiningMarks, "")
    .replace(mixedNumberBoundary, " ")
    .replace(printerSymbols) { match ->
        when (match.value[0]) {
            '‘', '’', '‚', '‛' -> "'"
            '“', '”', '„', '‟' -> "\""
            '‐', '‑', '‒', '–', '—', '―', '−' -> "-"
            '…' -> "..."
            else -> Normalizer.normalize(match.value, Normalizer.Form.NFKD).replace('⁄', '/')
        }
    }
    .replace(whitespace, " ").trim().replace(nonPrintable, "?")

internal fun thermalReceipt(receipt: Receipt, paper: ThermalPaper): ByteArray = thermalDocument(paper) {
    val sale = receipt.sale
    alignCenter()
    line(sale.storeName)
    line(sale.storeAddress)
    alignLeft()
    line(dateLabel(sale.time).replace(" • ", " - "))
    if (sale.id > 0) line(ticket(sale.id))
    divider()
    receipt.lines.forEach { item ->
        line(item.productName)
        row("${item.quantity} x ${rupiah(item.unitPrice)}", rupiah(item.quantity * item.unitPrice))
    }
    divider()
    row("Total", rupiah(sale.total))
    line("Pembayaran: ${sale.method}")
    if (sale.method == Payment.CASH.label) {
        row("Tunai", rupiah(sale.paid))
        row("Kembalian", rupiah(sale.paid - sale.total))
    }
    divider()
    alignCenter()
    line("Terima kasih")
}

internal fun thermalPrinterTest(profile: StoreProfile, paper: ThermalPaper): ByteArray = thermalDocument(paper) {
    alignCenter()
    line(profile.name)
    line(profile.address)
    line("Tes cetak printer")
    line("Kertas ${paper.label}")
    divider()
}

private fun thermalDocument(paper: ThermalPaper, content: ThermalDocument.() -> Unit): ByteArray =
    ThermalDocument(paper.columns).apply(content).finish()

private class ThermalDocument(private val width: Int) {
    private val output = StringBuilder("\u001B@")

    fun alignCenter() { output.append("\u001Ba\u0001") }

    fun alignLeft() { output.append("\u001Ba\u0000") }

    fun divider() { output.appendLine("-".repeat(width)) }

    fun line(text: String) {
        var remaining = text.printerText()
        while (remaining.length > width) {
            val split = remaining.lastIndexOf(' ', width).takeIf { it > 0 } ?: width
            output.appendLine(remaining.substring(0, split))
            remaining = remaining.substring(split).trimStart()
        }
        if (remaining.isNotEmpty()) output.appendLine(remaining)
    }

    fun row(label: String, amount: String) {
        val space = width - label.length - amount.length
        if (space > 0) {
            output.appendLine(label + " ".repeat(space) + amount)
        } else {
            line(label)
            output.appendLine(amount.padStart(width))
        }
    }

    fun finish(): ByteArray = output.append("\u001Bd\u0004\u001DV\u0000").toString().toByteArray(Charsets.US_ASCII)
}
