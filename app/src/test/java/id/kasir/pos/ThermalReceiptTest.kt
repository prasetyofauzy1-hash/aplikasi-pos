package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalReceiptTest {
    private val name = "Minuman spesial dengan topping keju parut"
    private val longWord = "A".repeat(60)
    private val receipt = Receipt(
        Sale(
            id = 123, time = 0, total = 36_000, paid = 50_000, method = Payment.CASH.label,
            storeName = "Toko Café", storeAddress = "Jalan Uji\nNo. 1", logoName = ""
        ),
        listOf(SaleLine(saleId = 123, productName = name, unitPrice = 15_000, quantity = 2),
            SaleLine(saleId = 123, productName = longWord, unitPrice = 6_000, quantity = 1))
    )

    @Test fun bothPaperSizesWrapNamesAndAlignAmounts() {
        for ((paper, width) in listOf(ThermalPaper.MM58 to 32, ThermalPaper.MM80 to 48)) {
            val lines = printedLines(receipt, paper)
            assertTrue(lines.all { it.length <= width })
            assertEquals(3, lines.count { it == "-".repeat(width) })
            val start = lines.indexOf("-".repeat(width)) + 1
            val firstAmount = lines.indexOfFirst { it.startsWith("2 x") }
            assertEquals(name, lines.subList(start, firstAmount).joinToString(" "))
            assertEquals(if (width == 32) 2 else 1, firstAmount - start)
            val secondAmount = lines.indexOfFirst { it.startsWith("1 x") }
            assertEquals(listOf("A".repeat(width), "A".repeat(60 - width)), lines.subList(firstAmount + 1, secondAmount))
            assertEquals("2 x Rp15.000".padEnd(width - "Rp30.000".length) + "Rp30.000", lines[firstAmount])
            assertTrue(lines.any { it.startsWith("Total") && it.endsWith("Rp36.000") && it.length == width })
            assertTrue(lines.any { it.startsWith("Tunai") && it.endsWith("Rp50.000") })
            assertTrue(lines.any { it.startsWith("Kembalian") && it.endsWith("Rp14.000") })
        }
    }

    @Test fun outputsEscPosCommandsWithPrintableStoreDetails() {
        val output = thermalReceipt(receipt, ThermalPaper.MM58).toString(Charsets.US_ASCII)
        assertTrue(output.startsWith("\u001B@\u001Ba\u0001Toko Cafe\nJalan Uji No. 1\n\u001Ba\u0000"))
        assertTrue(output.contains(dateLabel(0).replace(" • ", " - ")))
        assertTrue(output.contains("\nTRX-00123\n"))
        assertTrue(output.endsWith("Rp14.000\n\u001Bd\u0004\u001DV\u0000"))
    }

    @Test fun qrisDoesNotPrintCashFieldsAndMissingIdIsOmitted() {
        val qris = receipt.copy(sale = receipt.sale.copy(id = 0, paid = receipt.sale.total, method = Payment.QRIS.label))
        val lines = printedLines(qris, ThermalPaper.MM58)
        assertTrue(lines.last { it.isNotBlank() }.let { it.startsWith("QRIS") && it.endsWith("Rp36.000") })
        assertFalse(lines.any { it.startsWith("Tunai") || it.startsWith("Kembalian") || it.startsWith("TRX-") })
    }

    @Test fun exactCashOmitsTheChangeRow() {
        val exact = receipt.copy(sale = receipt.sale.copy(paid = receipt.sale.total))
        val lines = printedLines(exact, ThermalPaper.MM58)
        assertTrue(lines.last { it.isNotBlank() }.let { it.startsWith("Tunai") && it.endsWith("Rp36.000") })
        assertFalse(lines.any { it.startsWith("Kembalian") })
    }

    @Test fun logoRasterPacksOpaqueDarkPixelsAndPreservesHeaderBytes() {
        val pixels = IntArray(9) { 0xFFFFFFFF.toInt() }
        pixels[0] = 0xFF000000.toInt()
        pixels[1] = 0x00000000
        pixels[8] = 0xFF000000.toInt()
        val logo = thermalRaster(9, 1, pixels)
        assertArrayEquals(byteArrayOf(0x1D, 0x76, 0x30, 0, 2, 0, 1, 0, 0x80.toByte(), 0x80.toByte()), logo)
        val output = thermalReceipt(receipt, ThermalPaper.MM58, logo)
        val header = "\u001B@\u001Ba\u0001".toByteArray(Charsets.US_ASCII)
        assertArrayEquals(header + logo + "Toko Cafe\n".toByteArray(Charsets.US_ASCII),
            output.copyOfRange(0, header.size + logo.size + "Toko Cafe\n".length))
    }

    @Test fun includesCashierSnapshotOnTextAndPrintedReceiptsButNotLegacyReceipts() {
        val withCashier = receipt.copy(sale = receipt.sale.copy(cashierName = "Dewi"))
        assertTrue(withCashier.asText().lines().contains("Kasir: Dewi"))
        assertTrue(printedLines(withCashier, ThermalPaper.MM58).contains("Kasir: Dewi"))
        assertFalse(receipt.asText().contains("Kasir:"))
        assertFalse(printedLines(receipt, ThermalPaper.MM58).any { it.startsWith("Kasir:") })
    }

    @Test fun pastedCashierNameIsNormalizedToOneReceiptLine() {
        val name = normalizeCashierName(" \tDewi\r\n\u00a0Ananda\u2028Putri ")
        assertEquals("Dewi Ananda Putri", name)
        assertEquals("", normalizeCashierName("\r\n\u2028"))
        val withCashier = receipt.copy(sale = receipt.sale.copy(cashierName = name))
        assertTrue(withCashier.asText().lines().contains("Kasir: Dewi Ananda Putri"))
    }

    @Test fun transliteratesCommonSymbolsBeforeReplacingUnsupportedCharacters() {
        val styled = receipt.copy(
            sale = receipt.sale.copy(storeName = "Toko ‘Café’"),
            lines = listOf(receipt.lines[0].copy(productName = "½, ¼, ¾ — “Jus” ⅓ ☕"))
        )
        val lines = printedLines(styled, ThermalPaper.MM58)
        assertTrue(lines.contains("Toko 'Cafe'"))
        assertTrue(lines.contains("1/2, 1/4, 3/4 - \"Jus\" 1/3 ?"))
    }

    @Test fun largeAmountsDoNotOverflowNarrowPaper() {
        val large = receipt.copy(lines = listOf(receipt.lines[0].copy(unitPrice = 999_999_999, quantity = 9_999)))
        val lines = printedLines(large, ThermalPaper.MM58)
        assertTrue(lines.all { it.length <= 32 })
        assertTrue(lines.contains("9999 x Rp999.999.999"))
        assertTrue(lines.contains("Rp9.998.999.990.001".padStart(32)))
    }

    @Test fun separatesTheWholeNumberFromAnAdjacentVulgarFraction() {
        val flour = receipt.copy(lines = listOf(receipt.lines[0].copy(productName = "Tepung 1½ kg, gula 12¾ kg")))
        val lines = printedLines(flour, ThermalPaper.MM58)
        assertTrue(lines.contains("Tepung 1 1/2 kg, gula 12 3/4 kg"))
    }

    private fun printedLines(receipt: Receipt, paper: ThermalPaper) = thermalReceipt(receipt, paper)
        .toString(Charsets.US_ASCII)
        .replace("\u001B@", "").replace("\u001Ba\u0001", "").replace("\u001Ba\u0000", "")
        .removeSuffix("\u001Bd\u0004\u001DV\u0000").lines()
}
