package id.kasir.pos

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryTest {
    private val berries = Ingredient(1, "Berry", "g", 600)
    private val banana = Ingredient(2, "Pisang", "g", 400)
    private val milk = Ingredient(3, "Susu", "ml", 500)
    private val berryGo = menuItem(1, "Berry Go", berries to 200, banana to 200)
    private val smoothie = menuItem(2, "Pisang Susu", banana to 200, milk to 150)

    @Test fun availableServingsUseTheLimitingIngredient() {
        assertEquals(2, berryGo.availableStock())
        assertEquals(0, menuItem(1, "Berry Go", berries to 200, banana.copy(stock = 199) to 200).availableStock())
    }

    @Test fun sharedIngredientIsReservedAcrossCartLines() {
        val menu = listOf(berryGo, smoothie)
        assertEquals(listOf(2, 2), availableCatalog(menu, emptyMap()).map { it.stock })
        assertEquals(listOf(2, 1), availableCatalog(menu, mapOf(1L to 1)).map { it.stock })
        assertEquals(listOf(1, 1), availableCatalog(menu, mapOf(1L to 1, 2L to 1)).map { it.stock })
        assertEquals(listOf(2, 0), availableCatalog(menu, mapOf(1L to 2)).map { it.stock })
    }

    @Test fun saleSumsIngredientQuantitiesAcrossMenuItems() {
        assertEquals(mapOf(1L to 200, 2L to 400, 3L to 150),
            checkedIngredientDemand(listOf(berryGo, smoothie), mapOf(1L to 1, 2L to 1)))
        assertEquals(mapOf(1L to 400, 2L to 400),
            checkedIngredientDemand(listOf(berryGo), mapOf(1L to 2)))
    }

    @Test fun deletionRestoresTheOriginalQuantitiesEvenIfARecipeChanges() = runBlocking {
        val cart = mapOf(1L to 1, 2L to 1)
        val snapshot = encodeIngredientDeductions(checkedIngredientDemand(listOf(berryGo, smoothie), cart))
        val changedRecipe = menuItem(1, "Berry Go", berries to 100)
        val restored = mutableMapOf<Long, Int>()
        restoreSaleIngredients(snapshot) { id, quantity ->
            restored[id] = quantity
            1
        }
        assertEquals(mapOf(1L to 200, 2L to 400, 3L to 150), restored)
        assertFalse(checkedIngredientDemand(listOf(changedRecipe, smoothie), cart) == restored)
    }

    @Test fun deletionDoesNotGuessAtStockForOldSalesOrSalesWithoutRecipes() = runBlocking {
        restoreSaleIngredients(null) { _, _ -> error("A legacy sale must not use the current recipe") }
        restoreSaleIngredients(encodeIngredientDeductions(emptyMap())) { _, _ -> error("No ingredients were deducted") }
    }

    @Test fun deletionFailsWhenADeductionCannotBeRestored() {
        assertThrows(SaleInputException::class.java) {
            runBlocking {
                restoreSaleIngredients(encodeIngredientDeductions(mapOf(1L to 200))) { _, _ -> 0 }
            }
        }
    }

    @Test fun rejectsAnOrderThatIndividuallyFitsButTogetherRunsOut() {
        val failure = assertThrows(SaleInputException::class.java) {
            checkedIngredientDemand(listOf(berryGo, smoothie), mapOf(1L to 2, 2L to 1))
        }
        assertEquals("Stok bahan Pisang tidak mencukupi.", failure.message)
    }

    @Test fun cartBlocksPaymentAfterStockCorrectionUntilQuantityIsReduced() {
        val menu = listOf(menuItem(1, "Berry Go", berries to 200, banana.copy(stock = 200) to 200))
        var cart = mapOf(1L to 3)
        val product = availableCatalog(menu, cart).single()
        assertEquals("Stok Berry Go tidak cukup. Kurangi jumlah atau tambah stok.", cartStockIssue(listOf(product), cart))
        assertTrue(canSetCartQuantity(current = 3, quantity = 2, stock = product.stock))
        assertFalse(canSetCartQuantity(current = 3, quantity = 4, stock = product.stock))
        cart = updateCartQuantity(cart, product, 2)
        assertEquals(mapOf(1L to 2), cart)
        assertEquals("Stok Berry Go tidak cukup. Kurangi jumlah atau tambah stok.", cartStockIssue(listOf(product), cart))
        assertNull(cartStockIssue(listOf(product), updateCartQuantity(cart, product, 1)))
    }

    @Test fun largeDemandCannotWrapToAValidStockAmount() {
        val large = menuItem(1, "Besar", banana to MAX_INGREDIENT_STOCK)
        assertThrows(SaleInputException::class.java) {
            checkedIngredientDemand(listOf(large), mapOf(1L to MAX_INGREDIENT_STOCK))
        }
    }

    @Test fun existingProductsWithoutRecipesKeepTheirOwnStock() {
        val legacy = MenuItem(Product(7, "Air Mineral", "Minuman", 4000, 5), emptyList())
        assertEquals(5, availableCatalog(listOf(legacy), mapOf(7L to 2)).single().stock)
        assertEquals(emptyMap<Long, Int>(), checkedIngredientDemand(listOf(legacy), mapOf(7L to 2)))
    }

    private fun menuItem(id: Long, name: String, vararg recipe: Pair<Ingredient, Int>) =
        MenuItem(Product(id, name, "Jus", 20000, 0), recipe.map { (ingredient, quantity) ->
            RecipeComponent(RecipeLine(id, ingredient.id, quantity), ingredient)
        })
}
