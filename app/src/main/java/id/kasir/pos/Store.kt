package id.kasir.pos

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.withTransaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Entity
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val price: Long,
    val stock: Int
)

@Entity
data class Sale(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val total: Long,
    val paid: Long,
    val method: String,
    val storeName: String,
    val storeAddress: String,
    val logoName: String
)

@Entity(
    foreignKeys = [ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("saleId")]
)
data class SaleLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int
)

data class Receipt(@Embedded val sale: Sale, @Relation(parentColumn = "id", entityColumn = "saleId") val lines: List<SaleLine>)

@Dao
interface PosDao {
    @Transaction @Query("SELECT * FROM Product ORDER BY name COLLATE NOCASE") fun menu(): Flow<List<MenuItem>>
    @Transaction @Query("SELECT * FROM Product WHERE id IN (:ids)") suspend fun menuById(ids: Set<Long>): List<MenuItem>
    @Insert suspend fun add(product: Product): Long
    @Update suspend fun update(product: Product)
    @Query("DELETE FROM Product WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE Product SET stock = stock - :quantity WHERE id = :id AND stock >= :quantity")
    suspend fun decrement(id: Long, quantity: Int): Int
    @Query("SELECT * FROM Ingredient ORDER BY name COLLATE NOCASE") fun ingredients(): Flow<List<Ingredient>>
    @Query("SELECT EXISTS(SELECT 1 FROM Ingredient WHERE name = :name AND id != :id)")
    suspend fun ingredientNameExists(name: String, id: Long): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM RecipeLine WHERE ingredientId = :ingredientId)")
    suspend fun ingredientInUse(ingredientId: Long): Boolean
    @Insert suspend fun addIngredient(ingredient: Ingredient)
    @Query("UPDATE Ingredient SET name = :name, stock = :stock WHERE id = :id")
    suspend fun updateIngredient(id: Long, name: String, stock: Int)
    @Query("DELETE FROM Ingredient WHERE id = :id") suspend fun deleteIngredient(id: Long)
    @Query("UPDATE Ingredient SET stock = stock + :quantity WHERE id = :id AND stock <= :maxStock - :quantity")
    suspend fun restockIngredient(id: Long, quantity: Int, maxStock: Int): Int
    @Query("UPDATE Ingredient SET stock = stock - :quantity WHERE id = :id AND stock >= :quantity")
    suspend fun decrementIngredient(id: Long, quantity: Int): Int
    @Query("DELETE FROM RecipeLine WHERE productId = :productId") suspend fun deleteRecipe(productId: Long)
    @Insert suspend fun addRecipe(recipe: List<RecipeLine>)
    @Query("SELECT * FROM Sale ORDER BY id DESC") fun sales(): Flow<List<Sale>>
    @Transaction @Query("SELECT * FROM Sale WHERE id = :id") suspend fun receipt(id: Long): Receipt?
    @Insert suspend fun addSale(sale: Sale): Long
    @Insert suspend fun addLines(lines: List<SaleLine>)
}

@Database(entities = [Product::class, Ingredient::class, RecipeLine::class, Sale::class, SaleLine::class], version = 2)
abstract class PosDatabase : RoomDatabase() {
    abstract fun dao(): PosDao

    companion object {
        private val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS Ingredient (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL COLLATE NOCASE, unit TEXT NOT NULL, stock INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Ingredient_name ON Ingredient (name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS RecipeLine (productId INTEGER NOT NULL, ingredientId INTEGER NOT NULL, quantity INTEGER NOT NULL, PRIMARY KEY (productId, ingredientId), FOREIGN KEY (productId) REFERENCES Product (id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY (ingredientId) REFERENCES Ingredient (id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_RecipeLine_ingredientId ON RecipeLine (ingredientId)")
            }
        }

        fun open(context: Context): PosDatabase = Room.databaseBuilder(context, PosDatabase::class.java, "kasir-rapi.db")
            .addMigrations(migration1To2)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    listOf(
                        Product(1, "Berry Go", "Jus", 20000, 0),
                        Product(2, "Pisang Susu", "Smoothie", 18000, 0),
                        Product(3, "Jeruk Segar", "Jus", 15000, 0)
                    ).forEach {
                        db.execSQL("INSERT INTO Product (id, name, category, price, stock) VALUES (?, ?, ?, ?, ?)",
                            arrayOf(it.id, it.name, it.category, it.price, it.stock))
                    }
                    listOf(
                        Ingredient(1, "Berry", "g", 0), Ingredient(2, "Pisang", "g", 0),
                        Ingredient(3, "Susu", "ml", 0), Ingredient(4, "Jeruk", "g", 0)
                    ).forEach {
                        db.execSQL("INSERT INTO Ingredient (id, name, unit, stock) VALUES (?, ?, ?, ?)",
                            arrayOf(it.id, it.name, it.unit, it.stock))
                    }
                    listOf(
                        RecipeLine(1, 1, 200), RecipeLine(1, 2, 200), RecipeLine(2, 2, 200),
                        RecipeLine(2, 3, 150), RecipeLine(3, 4, 250)
                    ).forEach {
                        db.execSQL("INSERT INTO RecipeLine (productId, ingredientId, quantity) VALUES (?, ?, ?)",
                            arrayOf(it.productId, it.ingredientId, it.quantity))
                    }
                }
            }).build()
    }
}

data class StoreProfile(val name: String = "Toko Saya", val address: String = "", val logoName: String = "")

enum class Payment(val label: String) { CASH("Tunai"), QRIS("QRIS") }

class SaleInputException(message: String) : IllegalArgumentException(message)

data class Checkout(val lines: List<SaleLine>, val total: Long, val paid: Long)

fun calculateCheckout(products: List<Product>, cart: Map<Long, Int>, payment: Payment, cash: Long?): Checkout {
    if (cart.isEmpty()) throw SaleInputException("Keranjang masih kosong.")
    val byId = products.associateBy { it.id }
    val lines = cart.map { (id, qty) ->
        val item = byId[id] ?: throw SaleInputException("Ada produk yang sudah dihapus. Perbarui keranjang.")
        if (qty <= 0 || qty > item.stock) throw SaleInputException("Stok ${item.name} tidak mencukupi.")
        SaleLine(saleId = 0, productName = item.name, unitPrice = item.price, quantity = qty)
    }
    val total = lines.sumOf { it.unitPrice * it.quantity }
    val paid = if (payment == Payment.QRIS) total else cash ?: 0
    if (paid < total) throw SaleInputException("Uang tunai kurang dari total belanja.")
    return Checkout(lines, total, paid)
}

class Register(private val db: PosDatabase) {
    private val dao = db.dao()

    suspend fun sell(cart: Map<Long, Int>, expectedTotal: Long, payment: Payment, cash: Long?, profile: StoreProfile): Receipt =
        db.withTransaction {
            val menu = dao.menuById(cart.keys)
            val order = calculateCheckout(availableCatalog(menu, emptyMap()), cart, payment, cash)
            if (order.total != expectedTotal) throw SaleInputException("Total berubah. Periksa kembali pesanan.")
            val ingredients = checkedIngredientDemand(menu, cart)
            val sale = Sale(time = System.currentTimeMillis(), total = order.total, paid = order.paid,
                method = payment.label, storeName = profile.name, storeAddress = profile.address, logoName = profile.logoName)
            val id = dao.addSale(sale)
            for (item in menu.filter { it.recipe.isEmpty() }) {
                if (dao.decrement(item.product.id, cart.getValue(item.product.id)) != 1) {
                    throw SaleInputException("Stok berubah. Periksa keranjang.")
                }
            }
            for ((ingredientId, quantity) in ingredients) {
                if (dao.decrementIngredient(ingredientId, quantity) != 1) {
                    throw SaleInputException("Stok bahan berubah. Periksa keranjang.")
                }
            }
            val lines = order.lines.map { it.copy(saleId = id) }
            dao.addLines(lines)
            Receipt(sale.copy(id = id), lines)
        }
}

val indonesia = Locale("id", "ID")
fun rupiah(amount: Long): String = "Rp" + NumberFormat.getIntegerInstance(indonesia).format(amount)
fun ticket(id: Long): String = "TRX-" + id.toString().padStart(5, '0')
fun dateLabel(time: Long): String = SimpleDateFormat("dd MMM yyyy • HH:mm", indonesia).format(time)
fun startOfDay(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun Receipt.asText() = buildString {
    appendLine(sale.storeName)
    if (sale.storeAddress.isNotBlank()) appendLine(sale.storeAddress)
    appendLine("${ticket(sale.id)} • ${dateLabel(sale.time)}")
    appendLine()
    lines.forEach {
        appendLine("${it.quantity} × ${it.productName} — ${rupiah(it.unitPrice * it.quantity)}")
    }
    appendLine()
    appendLine("Total: ${rupiah(sale.total)}")
    appendLine("${sale.method}: ${rupiah(sale.paid)}")
    if (sale.method == Payment.CASH.label) appendLine("Kembalian: ${rupiah(sale.paid - sale.total)}")
    append("Terima kasih sudah berbelanja!")
}
