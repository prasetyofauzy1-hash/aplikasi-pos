package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PricingTest {
    private val juice = Product(1, "Jus Mangga", "Minuman", 15_000, 20,
        options = formatProductOptions(listOf(ProductOption("Less sugar", 0), ProductOption("Cup besar", 5_000))),
        promoPercent = 1_000)
    private val toast = Product(2, "Roti", "Makanan", 10_000, 5, promoAmount = 2_000)

    @Test fun variantsPromosAndChargesShapeTheTotal() {
        val cart = listOf(
            CartItem(1, 1, 2, formatSelectedOptions(listOf("Cup besar")), "tanpa es"),
            CartItem(2, 1, 1),
            CartItem(3, 2, 1)
        )
        val charges = listOf(ExtraCharge("PPN", percent = 1_100), ExtraCharge("Biaya layanan", amount = 2_000),
            ExtraCharge("Nonaktif", amount = 9_999, enabled = false))
        val order = calculateCheckout(listOf(juice, toast), cart, Payment.QRIS, null, charges)
        assertEquals(listOf(18_500L, 13_500L, 8_000L), order.lines.map { it.unitPrice })
        assertEquals("Cup besar", order.lines[0].options)
        assertEquals("tanpa es", order.lines[0].note)
        assertEquals(1_500L, order.lines[0].discount)
        assertEquals(58_500L, order.subtotal)
        assertEquals(listOf(SaleCharge(saleId = 0, name = "PPN (11%)", amount = 6_435), SaleCharge(saleId = 0, name = "Biaya layanan", amount = 2_000)),
            order.charges)
        assertEquals(66_935L, order.total)
        assertEquals(66_935L, order.paid)
    }

    @Test fun rejectsStaleOptionsAndCombinedStock() {
        val stale = listOf(CartItem(1, 1, 1, "Topping keju"))
        assertNull(cartLineUnitPrice(juice, stale[0]))
        assertTrue(cartOptionIssue(listOf(juice), stale)!!.contains("Jus Mangga"))
        assertThrows(SaleInputException::class.java) { calculateCheckout(listOf(juice), stale, Payment.QRIS, null) }
        val split = listOf(CartItem(1, 2, 3), CartItem(2, 2, 3, note = "hangat"))
        assertThrows(SaleInputException::class.java) { calculateCheckout(listOf(toast), split, Payment.QRIS, null) }
        assertThrows(SaleInputException::class.java) {
            calculateCheckout(listOf(juice), listOf(CartItem(1, 1, 1)), Payment.CASH, 13_500, listOf(ExtraCharge("PPN", percent = 1_100)))
        }
    }

    @Test fun parsesOwnerInput() {
        assertEquals(1_100, parsePercent("11"))
        assertEquals(250, parsePercent("2,5"))
        assertEquals(1_075, parsePercent("10.75%"))
        assertNull(parsePercent("0"))
        assertNull(parsePercent("101"))
        assertNull(parsePercent("1,234"))
        assertEquals("2,5%", percentLabel(250))
        assertEquals(listOf(ProductOption("Cup besar", 5_000), ProductOption("Less sugar", 0)),
            parseOptionDraft("Cup besar +Rp5.000\n\n Less sugar "))
        assertNull(parseOptionDraft("Less sugar\nless sugar"))
        assertEquals(listOf(ProductOption("Cup besar", 5_000)), parseOptionDraft("Cup besar +Rp5.000,00"))
        assertNull(parseOptionDraft("Cup besar +5.00"))
        assertEquals(listOf(ProductOption("Cup besar", 5_000)), parseOptionDraft("Cup besar +Rp5.000,-"))
        assertNull(parseOptionDraft("Cup besar +5rb"))
        assertEquals(listOf(ProductOption("Cup besar", 5_000)), parseOptionDraft("Cup besar +Rp. 5.000"))
        assertEquals(listOf(ProductOption("Cookies + Cream", 0)), parseOptionDraft("Cookies + Cream"))
        val free = listOf(ProductOption("Topping +2", 0))
        assertEquals(free, parseOptionDraft(formatOptionDraft(free)))
        assertEquals("Cup besar +5000\nLess sugar", formatOptionDraft(parseOptionDraft("Cup besar +5000\nLess sugar")!!))
        val charges = listOf(ExtraCharge("PPN", percent = 1_100), ExtraCharge("Layanan", amount = 1_000, enabled = false))
        assertEquals(charges, chargesFromJson(chargesToJson(charges)))
        assertEquals(emptyList<ExtraCharge>(), chargesFromJson("bukan json"))
        assertEquals(10_000L, Discount(amount = 50_000).of(10_000))
    }

    @Test fun receiptsShowNotesPromosAndCharges() {
        val receipt = Receipt(
            Sale(9, 0, total = 18_870, paid = 20_000, method = Payment.CASH.label, storeName = "Jus.go", storeAddress = "", logoName = "",
                subtotal = 17_000),
            listOf(SaleLine(saleId = 9, productName = "Jus Mangga", unitPrice = 17_000, quantity = 1, productId = 1,
                options = "Less sugar, Cup besar", note = "tanpa es", discount = 3_000)),
            listOf(SaleCharge(saleId = 9, name = "PPN (11%)", amount = 1_870))
        )
        val text = receipt.asText()
        assertTrue(text, text.contains("1 × Jus Mangga — Rp20.000"))
        assertTrue(text, text.contains("  Less sugar, Cup besar\n  Catatan: tanpa es\n  Promo -Rp3.000"))
        assertTrue(text, text.contains("Subtotal: Rp17.000\nPPN (11%): Rp1.870\nTotal: Rp18.870"))
        val printed = thermalReceipt(receipt, ThermalPaper.MM58).toString(Charsets.US_ASCII)
        assertTrue(printed, printed.contains("\nCatatan: tanpa es\nPromo -Rp3.000"))
        assertTrue(printed, printed.contains("PPN (11%)"))
        val injected = receipt.copy(charges = listOf(SaleCharge(saleId = 9, name = "Biaya\u001BX", amount = 1_870)))
        val printedInjected = thermalReceipt(injected, ThermalPaper.MM58).toString(Charsets.US_ASCII)
        assertTrue(printedInjected, printedInjected.contains("Biaya?X"))
    }
}
