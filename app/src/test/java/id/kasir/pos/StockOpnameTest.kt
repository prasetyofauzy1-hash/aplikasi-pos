package id.kasir.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StockOpnameTest {
    private val berries = Ingredient(1, "Berry", "g", 600)
    private val banana = Ingredient(2, "pisang", "g", 400)
    private val milk = Ingredient(3, "Susu", "ml", 500)

    @Test fun recordsSystemAndPhysicalStockWithDifference() {
        val lines = stockCountLines(listOf(berries, banana, milk), mapOf(3L to 520, 1L to 550, 2L to 400))
        assertEquals(listOf("Berry", "pisang", "Susu"), lines.map { it.ingredientName })
        assertEquals(listOf(600, 400, 500), lines.map { it.systemStock })
        assertEquals(listOf(550, 400, 520), lines.map { it.countedStock })
        assertEquals(listOf(-50L, 0L, 20L), lines.map { it.difference })
        assertEquals(listOf("g", "g", "ml"), lines.map { it.unit })
    }

    @Test fun onlyCountedIngredientsAreRecorded() {
        assertEquals(listOf(2L), stockCountLines(listOf(berries, banana), mapOf(2L to 0)).map { it.ingredientId })
    }

    @Test fun rejectsEmptyMissingOrInvalidCounts() {
        assertThrows(SaleInputException::class.java) { stockCountLines(listOf(berries), emptyMap()) }
        assertThrows(SaleInputException::class.java) { stockCountLines(listOf(berries), mapOf(9L to 10)) }
        assertThrows(SaleInputException::class.java) { stockCountLines(listOf(berries), mapOf(1L to -1)) }
    }

    @Test fun formatsDifferenceWithSignAndUnit() {
        assertEquals("Sesuai", stockDifference(0, "g"))
        assertEquals("+1.250 ml", stockDifference(1250, "ml"))
        assertEquals("−50 g", stockDifference(-50, "g"))
    }
}
