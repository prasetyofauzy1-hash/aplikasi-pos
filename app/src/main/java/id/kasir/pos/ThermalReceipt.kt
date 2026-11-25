package id.kasir.pos

import java.io.ByteArrayOutputStream
import java.text.Normalizer

internal enum class ThermalPaper(val label: String, val columns: Int, val logoWidth: Int) {
    MM58("58 mm", 32, 192),
    MM80("80 mm", 48, 256)
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

internal fun thermalReceipt(receipt: Receipt, paper: ThermalPaper, logo: ByteArray? = null): ByteArray =
    thermalDocument(paper) {
        alignCenter()
        if (logo != null) raster(logo)
        var centered = true
        receipt.entries().forEach { entry ->
            when (entry) {
                ReceiptEntry.Rule -> divider()
                is ReceiptEntry.Row -> {
                    if (centered && entry.style == ReceiptStyle.METADATA) {
                        alignLeft()
                        centered = false
                    }
                    val label = if (entry.style == ReceiptStyle.METADATA) entry.label.replace(" • ", " - ") else entry.label
                    if (entry.amount == null) line(label) else row(label, entry.amount)
                }
            }
        }
    }

internal fun thermalRaster(width: Int, height: Int, pixels: IntArray): ByteArray {
    val stride = (width + 7) / 8
    val data = ByteArray(8 + stride * height)
    byteArrayOf(0x1D, 0x76, 0x30, 0, stride.toByte(), (stride shr 8).toByte(), height.toByte(), (height shr 8).toByte())
        .copyInto(data)
    for (y in 0 until height) {
        for (x in 0 until width) {
            val argb = pixels[y * width + x]
            val alpha = argb ushr 24
            val gray = ((argb shr 16 and 255) * 30 + (argb shr 8 and 255) * 59 + (argb and 255) * 11) / 100
            if ((255 - gray) * alpha > 127 * 255) {
                val index = 8 + y * stride + x / 8
                data[index] = (data[index].toInt() or (0x80 shr (x % 8))).toByte()
            }
        }
    }
    return data
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
    private val bytes = ByteArrayOutputStream()

    private fun flushText() {
        bytes.write(output.toString().toByteArray(Charsets.US_ASCII))
        output.clear()
    }

    fun raster(data: ByteArray) {
        flushText()
        bytes.write(data)
    }

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

    fun row(rawLabel: String, rawAmount: String) {
        val label = rawLabel.printerText()
        val amount = rawAmount.printerText()
        val space = width - label.length - amount.length
        if (space > 0) {
            output.appendLine(label + " ".repeat(space) + amount)
        } else {
            line(label)
            output.appendLine(amount.padStart(width))
        }
    }

    fun finish(): ByteArray {
        output.append("\u001Bd\u0004\u001DV\u0000")
        flushText()
        return bytes.toByteArray()
    }
}
