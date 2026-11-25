package id.kasir.pos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

internal const val receiptPdfMime = "application/pdf"
private const val pageWidth = 288
private const val maxPageHeight = 1056
private const val padding = 16
private const val contentWidth = pageWidth - 2 * padding

class ReceiptPdfFileProvider : FileProvider()

internal fun writeReceiptPdf(context: Context, receipt: Receipt): Uri {
    val root = File(context.cacheDir, "receipt-pdfs")
    val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
    root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    val directory = File(root, UUID.randomUUID().toString())
    if (!directory.mkdirs()) throw IOException("Cannot create receipt PDF folder")
    val file = File(directory, "Struk-${ticket(receipt.sale.id)}.pdf")
    val logo = loadReceiptLogo(context, receipt.sale.logoName)
    try {
        val document = PdfDocument()
        try {
            drawReceiptPdf(document, receipt.entries(showStoreName = logo == null), logo)
            file.outputStream().use(document::writeTo)
        } finally {
            document.close()
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.receipt-pdf", file)
    } catch (error: Exception) {
        directory.deleteRecursively()
        throw error
    } finally {
        logo?.recycle()
    }
}

internal class PdfReceiptBlock(
    val height: Int,
    val keepWithNext: Boolean = false,
    val draw: Canvas.() -> Unit
)

internal fun paginateReceiptPdf(blocks: List<PdfReceiptBlock>): List<List<PdfReceiptBlock>> = buildList {
    val capacity = maxPageHeight - 2 * padding
    var available = capacity
    var start = 0
    blocks.forEachIndexed { index, block ->
        var required = block.height
        if (block.keepWithNext && (index == 0 || !blocks[index - 1].keepWithNext)) {
            var last = index
            var groupHeight = block.height
            while (last < blocks.lastIndex && blocks[last].keepWithNext) {
                groupHeight += blocks[++last].height
            }
            val firstPairHeight = block.height + (blocks.getOrNull(index + 1)?.height ?: 0)
            required = when {
                groupHeight <= capacity -> groupHeight
                firstPairHeight <= capacity -> firstPairHeight
                else -> block.height
            }
        }
        if (required > available && index > start) {
            add(blocks.subList(start, index))
            start = index
            available = capacity
        }
        available -= block.height
    }
    add(blocks.subList(start, blocks.size))
}

private fun drawReceiptPdf(document: PdfDocument, entries: List<ReceiptEntry>, logo: Bitmap?) {
    val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        strokeWidth = 0.7f
        pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
    }
    val blocks = buildList {
        if (logo != null) {
            val (width, height) = logo.fittedSize(136, 116)
            val left = (contentWidth - width) / 2
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            add(PdfReceiptBlock(height + 5) { drawBitmap(logo, null, Rect(left, 0, left + width, height), paint) })
        }
        entries.forEach {
            when (it) {
                ReceiptEntry.Rule -> add(PdfReceiptBlock(12) { drawLine(0f, 6f, contentWidth.toFloat(), 6f, rule) })
                is ReceiptEntry.Row -> addAll(pdfReceiptRow(it))
            }
        }
    }
    paginateReceiptPdf(blocks).forEachIndexed { index, pageBlocks ->
        val height = 2 * padding + pageBlocks.sumOf { it.height }
        val page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, height, index + 1).create())
        page.canvas.apply {
            drawColor(Color.WHITE)
            translate(padding.toFloat(), padding.toFloat())
            pageBlocks.forEach { block ->
                save()
                block.draw(this)
                restore()
                translate(0f, block.height.toFloat())
            }
        }
        document.finishPage(page)
    }
}

private fun pdfReceiptRow(row: ReceiptEntry.Row): List<PdfReceiptBlock> {
    val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = when (row.style) {
            ReceiptStyle.STORE -> 19f
            ReceiptStyle.TOTAL -> 15f
            ReceiptStyle.ITEM, ReceiptStyle.PAYMENT -> 12f
            else -> 10.5f
        }
        val bold = row.style in listOf(ReceiptStyle.STORE, ReceiptStyle.ITEM, ReceiptStyle.TOTAL, ReceiptStyle.PAYMENT)
        typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }
    val amount = row.amount
    val amountWidth = if (amount == null) 0 else kotlin.math.ceil(text.measureText(amount)).toInt()
    val centered = row.style == ReceiptStyle.STORE || row.style == ReceiptStyle.ADDRESS
    val rowTop = if (row.style == ReceiptStyle.ITEM) 4 else 0
    val availableWidth = if (amount == null) contentWidth else contentWidth - amountWidth - 8
    val layout = StaticLayout.Builder.obtain(row.label, 0, row.label.length, text, availableWidth)
        .setIncludePad(false)
        .setAlignment(if (centered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
        .build()
    if (layout.height + rowTop + 3 <= maxPageHeight - 2 * padding) {
        return listOf(PdfReceiptBlock(layout.height + rowTop + 3, keepWithNext = row.keepWithNext) {
            translate(0f, rowTop.toFloat())
            layout.draw(this)
            if (amount != null) drawText(amount, (contentWidth - amountWidth).toFloat(), layout.getLineBaseline(0).toFloat(), text)
        })
    }
    return List(layout.lineCount) { line ->
        val top = layout.getLineTop(line)
        val lineHeight = layout.getLineBottom(line) - top
        PdfReceiptBlock(lineHeight, keepWithNext = line < layout.lineCount - 1 || row.keepWithNext) {
            clipRect(0, 0, contentWidth, lineHeight)
            translate(0f, -top.toFloat())
            layout.draw(this)
            if (line == 0 && amount != null) {
                drawText(amount, (contentWidth - amountWidth).toFloat(), layout.getLineBaseline(0).toFloat(), text)
            }
        }
    }
}
