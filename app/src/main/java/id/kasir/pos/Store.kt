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
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.room.withTransaction
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

@Entity(foreignKeys = [ForeignKey(
    entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE
)])
data class CartItem(@PrimaryKey val productId: Long, val quantity: Int)

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
    @Query("SELECT * FROM Product ORDER BY name COLLATE NOCASE") fun products(): Flow<List<Product>>
    @Query("SELECT * FROM Product WHERE id IN (:ids)") suspend fun byId(ids: Set<Long>): List<Product>
    @Insert suspend fun add(product: Product)
    @Update suspend fun update(product: Product)
    @Query("DELETE FROM Product WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM CartItem") fun cart(): Flow<List<CartItem>>
    @Query("SELECT * FROM CartItem") suspend fun cartSnapshot(): List<CartItem>
    @Query("SELECT quantity FROM CartItem WHERE productId = :id") suspend fun cartQuantity(id: Long): Int?
    @Upsert suspend fun putCartItem(item: CartItem)
    @Query("DELETE FROM CartItem WHERE productId = :id") suspend fun removeCartItem(id: Long)
    @Query("UPDATE CartItem SET quantity = MIN(quantity, :stock) WHERE productId = :id")
    suspend fun capCartQuantity(id: Long, stock: Int)
    @Query("DELETE FROM CartItem") suspend fun clearCart()
    @Query("UPDATE Product SET stock = stock - :quantity WHERE id = :id AND stock >= :quantity")
    suspend fun decrement(id: Long, quantity: Int): Int
    @Query("SELECT * FROM Sale ORDER BY id DESC") fun sales(): Flow<List<Sale>>
    @Transaction @Query("SELECT * FROM Sale WHERE id = :id") suspend fun receipt(id: Long): Receipt?
    @Insert suspend fun addSale(sale: Sale): Long
    @Insert suspend fun addLines(lines: List<SaleLine>)
}

@Database(entities = [Product::class, Sale::class, SaleLine::class, CartItem::class], version = 2)
abstract class PosDatabase : RoomDatabase() {
    abstract fun dao(): PosDao

    companion object {
        private val addCart = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `CartItem` (`productId` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`productId`), FOREIGN KEY(`productId`) REFERENCES `Product`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
            }
        }

        fun open(context: Context): PosDatabase = Room.databaseBuilder(context, PosDatabase::class.java, "kasir-rapi.db")
            .addMigrations(addCart)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    listOf(
                        Product(name = "Air Mineral", category = "Minuman", price = 4000, stock = 40),
                        Product(name = "Keripik Singkong", category = "Camilan", price = 8000, stock = 15),
                        Product(name = "Kopi Susu", category = "Minuman", price = 15000, stock = 24),
                        Product(name = "Roti Cokelat", category = "Makanan", price = 9000, stock = 18),
                        Product(name = "Teh Melati", category = "Minuman", price = 7000, stock = 25),
                        Product(name = "Wafer Keju", category = "Camilan", price = 6000, stock = 6)
                    ).forEach {
                        db.execSQL("INSERT INTO Product (name, category, price, stock) VALUES (?, ?, ?, ?)",
                            arrayOf(it.name, it.category, it.price, it.stock))
                    }
                }
            }).build()
    }
}

data class StoreProfile(val name: String = "Toko Saya", val address: String = "", val logoName: String = "")

enum class Payment(val label: String) { CASH("Tunai"), QRIS("QRIS") }

class SaleInputException(message: String) : IllegalArgumentException(message)

data class Checkout(val lines: List<SaleLine>, val total: Long, val paid: Long)

internal fun cartItemCount(cart: Map<Long, Int>): Long = cart.values.sumOf { it.toLong() }

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
            if (dao.cartSnapshot().associate { it.productId to it.quantity } != cart) {
                throw SaleInputException("Keranjang berubah. Periksa kembali pesanan.")
            }
            val order = calculateCheckout(dao.byId(cart.keys), cart, payment, cash)
            if (order.total != expectedTotal) throw SaleInputException("Total berubah. Periksa kembali pesanan.")
            val sale = Sale(time = System.currentTimeMillis(), total = order.total, paid = order.paid,
                method = payment.label, storeName = profile.name, storeAddress = profile.address, logoName = profile.logoName)
            val id = dao.addSale(sale)
            cart.forEach { (productId, qty) ->
                if (dao.decrement(productId, qty) != 1) throw SaleInputException("Stok berubah. Periksa keranjang.")
            }
            val lines = order.lines.map { it.copy(saleId = id) }
            dao.addLines(lines)
            dao.clearCart()
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
