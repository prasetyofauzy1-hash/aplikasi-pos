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
}
