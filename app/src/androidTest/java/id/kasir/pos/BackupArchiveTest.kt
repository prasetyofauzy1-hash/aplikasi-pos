package id.kasir.pos

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.util.UUID

class BackupArchiveTest {
    @Test fun upgradesThePreviousDatabaseWithoutReplacingStoreData() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "previous-${UUID.randomUUID()}.db"
        val schema = instrumentation.context.assets.open("id.kasir.pos.PosDatabase/7.json").bufferedReader().use {
            JSONObject(it.readText()).getJSONObject("database")
        }
        try {
            SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name), null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indexes = entity.optJSONArray("indices") ?: continue
                    for (j in 0 until indexes.length()) {
                        db.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                    }
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                db.execSQL("INSERT INTO CashierProfile (id, name) VALUES (1, 'Maya')")
                db.execSQL("INSERT INTO Product (id, name, category, price, stock) VALUES (12, 'Jus', 'Minum', 20000, 4)")
                db.execSQL("INSERT INTO Ingredient (id, name, unit, stock) VALUES (3, 'Berry', 'g', 200)")
                db.execSQL("INSERT INTO StockCount (id, time) VALUES (5, 1234)")
                db.execSQL("INSERT INTO StockCountLine (countId, ingredientId, ingredientName, unit, systemStock, countedStock) " +
                    "VALUES (5, 3, 'Berry', 'g', 250, 200)")
                db.execSQL("INSERT INTO Sale (id, time, total, paid, method, storeName, storeAddress, logoName, cashierName) " +
                    "VALUES (41, 5678, 20000, 20000, 'Tunai', 'Jus Test', '', '', 'Maya')")
                db.execSQL("INSERT INTO SaleLine (saleId, productName, unitPrice, quantity, productId) " +
                    "VALUES (41, 'Jus', 20000, 1, 12)")
                db.version = 7
            }
            PosDatabase.withBackupDatabase(context, name) { upgraded ->
                assertEquals("Maya", upgraded.dao().cashier(1)?.name)
                val product = upgraded.dao().menuById(setOf(12)).single().product
                assertEquals(4, product.stock)
                assertNull(product.directHpp)
                val ingredient = upgraded.dao().ingredientsById(setOf(3)).single()
                assertEquals(200, ingredient.stock)
                assertNull(ingredient.purchaseCost)
                assertNull(ingredient.purchaseQuantity)
                assertEquals(200, upgraded.dao().stockCounts().first().single().lines.single().countedStock)
                val receipt = requireNotNull(upgraded.dao().receipt(41))
                assertEquals(20000L, receipt.sale.total)
                assertEquals(12L, receipt.lines.single().productId)
                assertNull(receipt.lines.single().unitHpp)
                assertNull(upgraded.dao().storeSettingsSnapshot())
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test fun restoresSalesStockRecipesAndMediaWithoutRestoringTheCart() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceName = "source-${UUID.randomUUID()}.db"
        val targetName = "target-${UUID.randomUUID()}.db"
        val archiveFile = File.createTempFile("backup-test-", ".zip", context.cacheDir)
        val logo = File(context.filesDir, "logo-${UUID.randomUUID()}.png")
        val qris = File(context.filesDir, "qris-${UUID.randomUUID()}.png")
        val profile = BackupProfile(
            StoreProfile("Jus Test", "Manahan", logo.name), qris.name,
            listOf(ExtraCharge("PPN", percent = 1100), ExtraCharge("Layanan", amount = 1000, enabled = false))
        )
        try {
            logo.writeText("logo")
            qris.writeText("qris")
            PosDatabase.withBackupDatabase(context, sourceName) { source ->
                val dao = source.dao()
                val cashier = CashierProfile(id = OWNER_CASHIER_ID, name = "Maya")
                dao.addCashier(cashier)
                val product = dao.add(Product(name = "Jus Berry", category = "Jus", price = 15000, stock = 4))
                val direct = dao.add(Product(name = "Air", category = "Minum", price = 5000, stock = 4, directHpp = 2000))
                val ingredient = Ingredient(id = 3, name = "Berry", unit = "g", stock = 200,
                    purchaseCost = 25000, purchaseQuantity = 1000)
                dao.addIngredient(ingredient)
                val countId = dao.addStockCount(StockCount(time = 1234))
                val countLine = StockCountLine(1, countId, ingredient.id, ingredient.name, ingredient.unit, 250, 200)
                dao.addStockCountLines(listOf(countLine))
                dao.addRecipe(listOf(RecipeLine(product, ingredient.id, 100)))
                dao.addCartItem(CartItem(productId = product, quantity = 1))
                val sale = Sale(41, 1234, 16000, 16000, "Tunai", profile.store.name, profile.store.address,
                    logo.name, cashier.name, subtotal = 15000,
                    ingredientDeductions = encodeStockDeductions(mapOf(ingredient.id to 100)),
                    productDeductions = encodeStockDeductions(emptyMap()))
                dao.addSale(sale)
                dao.addLines(listOf(SaleLine(saleId = sale.id, productName = "Jus Berry",
                    unitPrice = 15000, quantity = 1, productId = product, options = "Besar", note = "Less sugar", unitHpp = 2500)))
                val charge = SaleCharge(1, sale.id, "Layanan", 1000)
                dao.addCharges(listOf(charge))
                BackupArchive(context, source).create(archiveFile, profile)

                logo.delete()
                qris.delete()
                PosDatabase.withBackupDatabase(context, targetName) { target ->
                    val old = target.dao().add(Product(name = "Lama", category = "Jus", price = 100, stock = 1))
                    val oldSettings = BackupProfile(
                        StoreProfile("Nama lama"), "", listOf(ExtraCharge("Biaya lama", amount = 500))
                    ).settings()
                    target.dao().saveStoreSettings(oldSettings)
                    val targetDb = target.openHelper.writableDatabase
                    targetDb.execSQL(
                        "CREATE TRIGGER fail_restore BEFORE INSERT ON StoreSettings " +
                            "BEGIN SELECT RAISE(ABORT, 'failed settings write'); END"
                    )
                    val backup = BackupArchive(context, target)
                    val failed = runCatching { backup.restore(archiveFile) }
                    assertTrue(failed.exceptionOrNull().toString(), failed.exceptionOrNull() is SQLiteConstraintException)
                    assertEquals("Lama", target.dao().menuById(setOf(old)).single().product.name)
                    assertEquals(oldSettings, target.dao().storeSettingsSnapshot())
                    assertFalse(logo.exists())
                    assertFalse(qris.exists())

                    targetDb.execSQL("DROP TRIGGER fail_restore")
                    val restored = backup.restore(archiveFile)
                    assertEquals(profile, restored)
                    assertEquals(profile.settings(), target.dao().storeSettingsSnapshot())
                    assertEquals("logo", logo.readText())
                    assertEquals("qris", qris.readText())
                    val menu = target.dao().menuById(setOf(product)).single()
                    assertEquals("Jus Berry", menu.product.name)
                    assertEquals(ingredient, menu.recipe.single().ingredient)
                    assertEquals(2000L, target.dao().menuById(setOf(direct)).single().product.directHpp)
                    assertEquals(countLine, target.dao().stockCounts().first().single().lines.single())
                    assertEquals(cashier, target.dao().cashier(cashier.id))
                    val receipt = requireNotNull(target.dao().receipt(sale.id))
                    assertEquals(sale, receipt.sale)
                    assertEquals(product, receipt.lines.single().productId)
                    assertEquals("Less sugar", receipt.lines.single().note)
                    assertEquals(2500L, receipt.lines.single().unitHpp)
                    assertEquals(charge, receipt.charges.single())
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
