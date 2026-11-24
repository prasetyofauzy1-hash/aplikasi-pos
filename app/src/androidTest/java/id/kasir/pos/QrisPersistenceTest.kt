package id.kasir.pos

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class QrisPersistenceTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "qris-test-${UUID.randomUUID()}.db"
    private lateinit var db: PosDatabase
    private val cart = mapOf(1L to 2)
    private val profile = StoreProfile()
    private val order = QrisOrder("test-qris", 30_000, QrisStatus.PENDING, 0, null)

    private fun open() = Room.databaseBuilder(context, PosDatabase::class.java, databaseName).build()

    @Before fun setUp() = runBlocking {
        db = open()
        with(db.dao()) {
            add(Product(1, "Jus", "Minuman", 15_000, 0))
            addIngredient(Ingredient(1, "Jeruk", "g", 1_000))
            addRecipe(listOf(RecipeLine(1, 1, 250)))
            putCartItem(CartItem(1, 2))
        }
    }

    @After fun tearDown() {
        db.close()
        context.deleteDatabase(databaseName)
    }

    @Test fun paidOrderSurvivesReopeningAndConcurrentReplaySavesAndDeductsOnlyOnce() = runBlocking {
        val pending = Register(db).beginQris(order.id, cart, order.total)
        assertNull(Register(db).finishQris(order, profile))
        assertEquals(emptyList<Receipt>(), sales())
        assertEquals(1_000, stock())

        db.close()
        db = open()
        assertEquals(pending, db.dao().pendingQris())
        val register = Register(db)
        val paid = order.copy(status = QrisStatus.PAID)
        val receipts = listOf(async { register.finishQris(paid, profile) }, async { register.finishQris(paid, profile) })
            .awaitAll().filterNotNull()

        assertEquals(1, receipts.size)
        assertEquals(Payment.QRIS_AUTO.label, receipts.single().sale.method)
        assertEquals(order.total, receipts.single().sale.paid)
        assertEquals(1, sales().size)
        assertEquals(500, stock())
        assertEquals(emptyList<CartItem>(), db.dao().cartSnapshot())
        assertNull(db.dao().pendingQris())
    }

    @Test fun expiredOrderKeepsCartAndStockAndRejectsLaterReplay() = runBlocking {
        val register = Register(db)
        register.beginQris(order.id, cart, order.total)
        assertNull(register.finishQris(order.copy(status = QrisStatus.EXPIRED), profile))
        assertNull(register.finishQris(order.copy(status = QrisStatus.PAID), profile))

        assertNull(db.dao().pendingQris())
        assertEquals(emptyList<Receipt>(), sales())
        assertEquals(1_000, stock())
        assertEquals(listOf(CartItem(1, 2)), db.dao().cartSnapshot())
    }

    private suspend fun sales() = db.dao().receiptsForExport(0, Long.MAX_VALUE)
    private suspend fun stock() = db.dao().ingredients().first().single().stock
}
