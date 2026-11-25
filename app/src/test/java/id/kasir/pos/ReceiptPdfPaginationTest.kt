package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptPdfPaginationTest {
    @Test fun movesTheNameAndPriceTogetherWhenOnlyTheNameWouldFit() {
        val precedingRows = PdfReceiptBlock(1000) {}
        val name = PdfReceiptBlock(20, keepWithNext = true) {}
        val price = PdfReceiptBlock(16) {}
        assertEquals(listOf(listOf(precedingRows), listOf(name, price)),
            paginateReceiptPdf(listOf(precedingRows, name, price)))
    }

    @Test fun keepsBothRowsOnTheSamePageWhenTheyJustFit() {
        val precedingRows = PdfReceiptBlock(988) {}
        val name = PdfReceiptBlock(20, keepWithNext = true) {}
        val price = PdfReceiptBlock(16) {}
        val rows = listOf(precedingRows, name, price)
        assertEquals(listOf(rows), paginateReceiptPdf(rows))
    }

    @Test fun movesTheWholeItemWhenItsNotesWouldSpillOntoTheNextPage() {
        val item = SaleLine(saleId = 1, productName = "Jus Jeruk", unitPrice = 15000, quantity = 1,
            options = "Gula sedikit", note = "Tanpa es")
        val receipt = Receipt(
            Sale(1, 0, 30000, 30000, Payment.QRIS.label, "Jus.go", "", ""),
            listOf(item, item.copy(productName = "Jus Apel", options = "", note = ""))
        )
        val rows = receipt.entries(showStoreName = false).filterIsInstance<ReceiptEntry.Row>()
            .dropWhile { it.style != ReceiptStyle.ITEM }.takeWhile { it.style != ReceiptStyle.TOTAL }
        assertEquals(listOf("Jus Jeruk", "1 x Rp15.000", "Gula sedikit", "Catatan: Tanpa es", "Jus Apel", "1 x Rp15.000"),
            rows.map { it.label })
        val blocks = rows.map { PdfReceiptBlock(16, it.keepWithNext) {} }
        val precedingRows = PdfReceiptBlock(976) {}
        assertEquals(listOf(listOf(precedingRows), blocks),
            paginateReceiptPdf(listOf(precedingRows) + blocks))
        val exactFit = PdfReceiptBlock(960) {}
        assertEquals(listOf(listOf(exactFit) + blocks.take(4), blocks.drop(4)),
            paginateReceiptPdf(listOf(exactFit) + blocks))
    }

    @Test fun letsAnOversizedItemBreakWithoutOrphaningItsPrice() {
        val precedingRows = PdfReceiptBlock(1008) {}
        val name = PdfReceiptBlock(16, keepWithNext = true) {}
        val price = PdfReceiptBlock(16, keepWithNext = true) {}
        val firstNote = PdfReceiptBlock(800, keepWithNext = true) {}
        val secondNote = PdfReceiptBlock(300) {}
        assertEquals(listOf(listOf(precedingRows), listOf(name, price, firstNote), listOf(secondNote)),
            paginateReceiptPdf(listOf(precedingRows, name, price, firstNote, secondNote)))
    }
}
