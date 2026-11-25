package id.kasir.pos

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class ReceiptPdfTest {
    @Test fun sharesReadablePaginatedPdfWithLogo() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val logoFile = File(context.filesDir, "receipt-test-${UUID.randomUUID()}.png")
        val logo = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888)
        try {
            logo.eraseColor(Color.RED)
            logoFile.outputStream().use { assertTrue(logo.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val receipt = Receipt(
                Sale(42, 0, 20000, 25000, Payment.CASH.label, "Jus.go", "", logoFile.name, "Dewi"),
                List(100) { SaleLine(saleId = 42, productName = "Jus Mangga", unitPrice = 200, quantity = 1) }
            )
            val uri = writeReceiptPdf(context, receipt)
            val intent = receiptPdfIntent(context, receipt, uri)
            assertEquals("content", uri.scheme)
            assertEquals("application/pdf", context.contentResolver.getType(uri))
            assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            PdfRenderer(checkNotNull(context.contentResolver.openFileDescriptor(uri, "r"))).use { pdf ->
                assertTrue(pdf.pageCount > 1)
                pdf.openPage(0).use { page ->
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        assertEquals(Color.RED, bitmap.getPixel(page.width / 2, 30))
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        } finally {
            logo.recycle()
            logoFile.delete()
        }
    }
}
