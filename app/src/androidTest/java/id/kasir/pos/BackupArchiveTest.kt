package id.kasir.pos

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class BackupArchiveTest {
    @Test fun restoresSalesStockRecipesAndMediaWithoutRestoringTheCart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceName = "source-${UUID.randomUUID()}.db"
        val targetName = "target-${UUID.randomUUID()}.db"
        val archiveFile = File.createTempFile("backup-test-", ".zip", context.cacheDir)
        val logo = File(context.filesDir, "logo-${UUID.randomUUID()}.png")
        val qris = File(context.filesDir, "qris-${UUID.randomUUID()}.png")
        val profile = BackupProfile(StoreProfile("Jus Test", "Manahan", logo.name), qris.name)
        try {
            logo.writeText("logo")
            qris.writeText("qris")
            PosDatabase.backupDatabase(context, sourceName).use { source ->
                val dao = source.dao()
                val product = dao.add(Product(name = "Jus Berry", category = "Jus", price = 15000, stock = 4))
                val ingredient = Ingredient(id = 3, name = "Berry", unit = "g", stock = 200)
                dao.addIngredient(ingredient)
                dao.addRecipe(listOf(RecipeLine(product, ingredient.id, 100)))
                dao.putCartItem(CartItem(product, 1))
                val sale = Sale(41, 1234, 15000, 15000, "Tunai", profile.store.name, profile.store.address, logo.name)
                dao.addSale(sale)
                dao.addLines(listOf(SaleLine(saleId = sale.id, productName = "Jus Berry",
                    unitPrice = 15000, quantity = 1, productId = product)))
                BackupArchive(context, source).create(archiveFile, profile)

                logo.delete()
                qris.delete()
                PosDatabase.backupDatabase(context, targetName).use { target ->
                    val old = target.dao().add(Product(name = "Lama", category = "Jus", price = 100, stock = 1))
                    val backup = BackupArchive(context, target)
                    val failed = runCatching { backup.restore(archiveFile) { throw IOException("failed commit") } }
                    assertTrue(failed.exceptionOrNull() is IOException)
                    assertEquals("Lama", target.dao().menuById(setOf(old)).single().product.name)
                    assertFalse(logo.exists())
                    assertFalse(qris.exists())

                    val restored = backup.restore(archiveFile) { assertEquals(profile, it) }
                    assertEquals(profile, restored)
                    assertEquals("logo", logo.readText())
                    assertEquals("qris", qris.readText())
                    val menu = target.dao().menuById(setOf(product)).single()
                    assertEquals("Jus Berry", menu.product.name)
                    assertEquals(ingredient, menu.recipe.single().ingredient)
                    assertEquals(sale, target.dao().receipt(sale.id)?.sale)
                    assertEquals(product, target.dao().receipt(sale.id)?.lines?.single()?.productId)
                    assertTrue(target.dao().cartSnapshot().isEmpty())
                }
            }
        } finally {
            archiveFile.delete()
            logo.delete()
            qris.delete()
            context.deleteDatabase(sourceName)
            context.deleteDatabase(targetName)
        }
    }
}
