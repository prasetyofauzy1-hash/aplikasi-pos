package id.kasir.pos

import android.content.Context
import androidx.room.ColumnInfo
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
import java.util.Locale

@Entity
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val price: Long,
    val stock: Int,
    @ColumnInfo(defaultValue = "") val options: String = "",
    @ColumnInfo(defaultValue = "0") val promoPercent: Int = 0,
    @ColumnInfo(defaultValue = "0") val promoAmount: Long = 0,
    val directHpp: Long? = null
)

@Entity(
    foreignKeys = [ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["productId", "options", "note"], unique = true)]
)
data class CartItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val quantity: Int,
    val options: String = "",
    val note: String = ""
)

const val OWNER_CASHIER_ID = 1L

@Entity
data class CashierProfile(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

private val cashierNameWhitespace = Regex("[\\s\\p{Z}\\u0085]+")

internal fun normalizeCashierName(name: String): String =
    name.replace(cashierNameWhitespace, " ").trim()

@Entity
data class StoreSettings(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val address: String,
    val logoName: String,
    val qrisImage: String,
    val extraCharges: String
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
    val logoName: String,
    val cashierName: String? = null,
    @ColumnInfo(defaultValue = "0") val subtotal: Long = total,
    val ingredientDeductions: String? = null,
    val productDeductions: String? = null
) {
    val cashierLabel: String? get() = cashierName?.let { "Kasir: $it" }
}

@Entity(
    foreignKeys = [ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("saleId")]
)
data class SaleLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val productName: String,
    val unitPrice: Long,
    val quantity: Int,
    val productId: Long? = null,
    @ColumnInfo(defaultValue = "") val options: String = "",
    @ColumnInfo(defaultValue = "") val note: String = "",
    @ColumnInfo(defaultValue = "0") val discount: Long = 0,
    val unitHpp: Long? = null
)

@Entity(
    foreignKeys = [ForeignKey(entity = Sale::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("saleId")]
)
data class SaleCharge(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val saleId: Long,
    val name: String,
    val amount: Long
)

data class Receipt(
    @Embedded val sale: Sale,
    @Relation(parentColumn = "id", entityColumn = "saleId") val lines: List<SaleLine>,
    @Relation(parentColumn = "id", entityColumn = "saleId") val charges: List<SaleCharge> = emptyList()
)

internal data class SaleDeletion(val sale: Sale, val countedIngredients: List<Ingredient>) {
    val countedIds get() = countedIngredients.mapTo(mutableSetOf()) { it.id }
}

@Dao
interface PosDao {
    @Query("SELECT * FROM CashierProfile ORDER BY id") fun cashiers(): Flow<List<CashierProfile>>
    @Query("SELECT * FROM CashierProfile WHERE id = :id") suspend fun cashier(id: Long): CashierProfile?
    @Insert suspend fun addCashier(cashier: CashierProfile)
    @Update suspend fun updateCashier(cashier: CashierProfile)
    @Query("DELETE FROM CashierProfile WHERE id = :id") suspend fun deleteCashier(id: Long)
    @Query("SELECT * FROM StoreSettings WHERE id = 1") fun storeSettings(): Flow<StoreSettings?>
    @Query("SELECT * FROM StoreSettings WHERE id = 1") suspend fun storeSettingsSnapshot(): StoreSettings?
    @Upsert suspend fun saveStoreSettings(settings: StoreSettings)
    @Transaction @Query("SELECT * FROM Product ORDER BY name COLLATE NOCASE") fun menu(): Flow<List<MenuItem>>
    @Transaction @Query("SELECT * FROM Product WHERE id IN (:ids)") suspend fun menuById(ids: Set<Long>): List<MenuItem>
    @Insert suspend fun add(product: Product): Long
    @Update suspend fun update(product: Product)
    @Query("UPDATE Product SET directHpp = :hpp WHERE id = :id AND NOT EXISTS(SELECT 1 FROM RecipeLine WHERE productId = :id)")
    suspend fun setDirectHpp(id: Long, hpp: Long?): Int
    @Query("DELETE FROM Product WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM CartItem") fun cart(): Flow<List<CartItem>>
    @Query("SELECT * FROM CartItem") suspend fun cartSnapshot(): List<CartItem>
    @Insert suspend fun addCartItem(item: CartItem): Long
    @Query("UPDATE CartItem SET quantity = :quantity WHERE id = :id") suspend fun setCartQuantity(id: Long, quantity: Int)
    @Query("DELETE FROM CartItem WHERE id = :id") suspend fun removeCartLine(id: Long)
    @Query("DELETE FROM CartItem WHERE productId = :id") suspend fun removeCartItem(id: Long)
    @Query("DELETE FROM CartItem") suspend fun clearCart()
    @Query("UPDATE Product SET stock = stock - :quantity WHERE id = :id AND stock >= :quantity")
    suspend fun decrement(id: Long, quantity: Int): Int
    @Query("SELECT EXISTS(SELECT 1 FROM Product WHERE id = :id)")
    suspend fun productExists(id: Long): Boolean
    @Query("UPDATE Product SET stock = stock + :quantity WHERE id = :id AND stock <= :maxStock - :quantity")
    suspend fun restockProduct(id: Long, quantity: Int, maxStock: Int): Int
    @Query("SELECT * FROM Ingredient ORDER BY name COLLATE NOCASE") fun ingredients(): Flow<List<Ingredient>>
    @Query("SELECT EXISTS(SELECT 1 FROM Ingredient WHERE id = :id)")
    suspend fun ingredientExists(id: Long): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM Ingredient WHERE name = :name AND id != :id)")
    suspend fun ingredientNameExists(name: String, id: Long): Boolean
    @Query("SELECT EXISTS(SELECT 1 FROM RecipeLine WHERE ingredientId = :ingredientId)")
    suspend fun ingredientInUse(ingredientId: Long): Boolean
    @Insert suspend fun addIngredient(ingredient: Ingredient)
    @Query("UPDATE Ingredient SET name = :name, stock = :stock WHERE id = :id")
    suspend fun updateIngredient(id: Long, name: String, stock: Int)
    @Query("UPDATE Ingredient SET purchaseCost = :cost, purchaseQuantity = :quantity WHERE id = :id")
    suspend fun setIngredientCost(id: Long, cost: Long?, quantity: Int?): Int
    @Query("DELETE FROM Ingredient WHERE id = :id") suspend fun deleteIngredient(id: Long)
    @Query("UPDATE Ingredient SET stock = stock + :quantity WHERE id = :id AND stock <= :maxStock - :quantity")
    suspend fun restockIngredient(id: Long, quantity: Int, maxStock: Int): Int
    @Query("UPDATE Ingredient SET stock = stock - :quantity WHERE id = :id AND stock >= :quantity")
    suspend fun decrementIngredient(id: Long, quantity: Int): Int
    @Query("SELECT * FROM Ingredient WHERE id IN (:ids)") suspend fun ingredientsById(ids: Set<Long>): List<Ingredient>
    @Query("UPDATE Ingredient SET stock = :stock WHERE id = :id") suspend fun setIngredientStock(id: Long, stock: Int): Int
    @Insert suspend fun addStockCount(count: StockCount): Long
    @Insert suspend fun addStockCountLines(lines: List<StockCountLine>)
    @Transaction @Query("SELECT * FROM StockCount ORDER BY time DESC, id DESC") fun stockCounts(): Flow<List<StockCountReport>>
    @Query("""
        SELECT * FROM Ingredient WHERE EXISTS (
            SELECT 1 FROM StockCountLine JOIN StockCount ON StockCount.id = StockCountLine.countId
            WHERE StockCountLine.ingredientId = Ingredient.id AND StockCount.time > :time
        ) ORDER BY name COLLATE NOCASE
    """)
    suspend fun ingredientsCountedAfter(time: Long): List<Ingredient>
    @Query("DELETE FROM RecipeLine WHERE productId = :productId") suspend fun deleteRecipe(productId: Long)
    @Insert suspend fun addRecipe(recipe: List<RecipeLine>)
    @Transaction
    @Query("SELECT * FROM Sale WHERE time >= :start AND time < :end ORDER BY time DESC, id DESC")
    fun dailyReceipts(start: Long, end: Long): Flow<List<Receipt>>
    @Transaction @Query("SELECT * FROM Sale WHERE id = :id") suspend fun receipt(id: Long): Receipt?
    @Query("SELECT * FROM Sale WHERE id = :id") suspend fun sale(id: Long): Sale?
    @Query("DELETE FROM Sale WHERE id = :id") suspend fun deleteSale(id: Long)
    @Insert suspend fun addSale(sale: Sale): Long
    @Insert suspend fun addLines(lines: List<SaleLine>)
    @Insert suspend fun addCharges(charges: List<SaleCharge>)
    @Transaction
    @Query("SELECT * FROM Sale WHERE time >= :start AND time < :endExclusive ORDER BY time, id")
    suspend fun receiptsForExport(start: Long, endExclusive: Long): List<Receipt>
}

@Database(
    entities = [Product::class, Ingredient::class, RecipeLine::class, Sale::class, SaleLine::class,
        CartItem::class, CashierProfile::class, StockCount::class, StockCountLine::class, SaleCharge::class, StoreSettings::class],
    version = 10
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun dao(): PosDao

    companion object {
        private val addProfitCosts = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Ingredient ADD COLUMN purchaseCost INTEGER")
                db.execSQL("ALTER TABLE Ingredient ADD COLUMN purchaseQuantity INTEGER")
                db.execSQL("ALTER TABLE Product ADD COLUMN directHpp INTEGER")
                db.execSQL("ALTER TABLE SaleLine ADD COLUMN unitHpp INTEGER")
            }
        }

        private val addSaleStockDeductions = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Sale ADD COLUMN ingredientDeductions TEXT")
                db.execSQL("ALTER TABLE Sale ADD COLUMN productDeductions TEXT")
            }
        }

        private fun addOwnerCashier(db: SupportSQLiteDatabase) {
            db.execSQL("INSERT INTO CashierProfile (id, name) VALUES (?, ?)", arrayOf<Any>(OWNER_CASHIER_ID, "Pemilik"))
        }

        private val addCashierProfiles = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE CashierProfile (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL)")
                db.execSQL("ALTER TABLE Sale ADD COLUMN cashierName TEXT")
                addOwnerCashier(db)
            }
        }

        private val addStockCounts = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `StockCount` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `time` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `StockCountLine` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `countId` INTEGER NOT NULL, `ingredientId` INTEGER, `ingredientName` TEXT NOT NULL, `unit` TEXT NOT NULL, `systemStock` INTEGER NOT NULL, `countedStock` INTEGER NOT NULL, FOREIGN KEY(`countId`) REFERENCES `StockCount`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_StockCountLine_countId` ON `StockCountLine` (`countId`)")
            }
        }

        private val addVariantsDiscountsCharges = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Product ADD COLUMN options TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE Product ADD COLUMN promoPercent INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Product ADD COLUMN promoAmount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE Sale ADD COLUMN subtotal INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE Sale SET subtotal = total")
                db.execSQL("ALTER TABLE SaleLine ADD COLUMN options TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE SaleLine ADD COLUMN note TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE SaleLine ADD COLUMN discount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `SaleCharge` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `saleId` INTEGER NOT NULL, `name` TEXT NOT NULL, `amount` INTEGER NOT NULL, FOREIGN KEY(`saleId`) REFERENCES `Sale`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_SaleCharge_saleId` ON `SaleCharge` (`saleId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `CartItem_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productId` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, `options` TEXT NOT NULL, `note` TEXT NOT NULL, FOREIGN KEY(`productId`) REFERENCES `Product`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
                db.execSQL("INSERT INTO CartItem_new (productId, quantity, options, note) SELECT productId, quantity, '', '' FROM CartItem")
                db.execSQL("DROP TABLE CartItem")
                db.execSQL("ALTER TABLE CartItem_new RENAME TO CartItem")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_CartItem_productId_options_note` ON `CartItem` (`productId`, `options`, `note`)")
            }
        }

        private val addStoreSettings = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE StoreSettings (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                        "address TEXT NOT NULL, logoName TEXT NOT NULL, qrisImage TEXT NOT NULL, extraCharges TEXT NOT NULL)"
                )
            }
        }

        private val addSaleLineProductId = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE SaleLine ADD COLUMN productId INTEGER")
            }
        }

        private val addIngredients = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS Ingredient (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL COLLATE NOCASE, unit TEXT NOT NULL, stock INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_Ingredient_name ON Ingredient (name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS RecipeLine (productId INTEGER NOT NULL, ingredientId INTEGER NOT NULL, quantity INTEGER NOT NULL, PRIMARY KEY (productId, ingredientId), FOREIGN KEY (productId) REFERENCES Product (id) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY (ingredientId) REFERENCES Ingredient (id) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_RecipeLine_ingredientId ON RecipeLine (ingredientId)")
            }
        }

        private val addCart = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `CartItem` (`productId` INTEGER NOT NULL, `quantity` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`productId`), FOREIGN KEY(`productId`) REFERENCES `Product`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
            }
        }

        internal inline fun <T> withBackupDatabase(context: Context, name: String, block: (PosDatabase) -> T): T {
            val database = backupDatabase(context, name)
            return try {
                block(database)
            } finally {
                database.close()
            }
        }

        private fun backupDatabase(context: Context, name: String): PosDatabase =
            builder(context, name).setJournalMode(JournalMode.TRUNCATE).build()

        private fun builder(context: Context, name: String) = Room.databaseBuilder(context, PosDatabase::class.java, name)
            .addMigrations(
                addCart, addIngredients, addSaleLineProductId, addCashierProfiles,
                addStockCounts, addVariantsDiscountsCharges, addStoreSettings, addSaleStockDeductions, addProfitCosts
            )

        fun open(context: Context): PosDatabase = builder(context, "kasir-rapi.db")
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    addOwnerCashier(db)
                    listOf(
                        Product(1, "Berry Go", "Jus", 20000, 0),
                        Product(2, "Pisang Susu", "Smoothie", 18000, 0),
                        Product(3, "Jeruk Segar", "Jus", 15000, 0)
                    ).forEach {
                        db.execSQL("INSERT INTO Product (id, name, category, price, stock) VALUES (?, ?, ?, ?, ?)",
                            arrayOf<Any>(it.id, it.name, it.category, it.price, it.stock))
                    }
                    listOf(
                        Ingredient(1, "Berry", "g", 0), Ingredient(2, "Pisang", "g", 0),
                        Ingredient(3, "Susu", "ml", 0), Ingredient(4, "Jeruk", "g", 0)
                    ).forEach {
                        db.execSQL("INSERT INTO Ingredient (id, name, unit, stock) VALUES (?, ?, ?, ?)",
                            arrayOf<Any>(it.id, it.name, it.unit, it.stock))
                    }
                    listOf(
                        RecipeLine(1, 1, 200), RecipeLine(1, 2, 200), RecipeLine(2, 2, 200),
                        RecipeLine(2, 3, 150), RecipeLine(3, 4, 250)
                    ).forEach {
                        db.execSQL("INSERT INTO RecipeLine (productId, ingredientId, quantity) VALUES (?, ?, ?)",
                            arrayOf<Any>(it.productId, it.ingredientId, it.quantity))
                    }
                }
            }).build()
    }
}

data class StoreProfile(val name: String = "Toko Saya", val address: String = "", val logoName: String = "")

enum class Payment(val label: String) { CASH("Tunai"), QRIS("QRIS") }

class SaleInputException(message: String) : IllegalArgumentException(message)

data class Checkout(
    val lines: List<SaleLine>,
    val total: Long,
    val paid: Long,
    val subtotal: Long = total,
    val charges: List<SaleCharge> = emptyList()
)

internal fun cartItemCount(cart: Map<Long, Int>): Long = cart.values.sumOf { it.toLong() }

internal fun cartItemCount(cart: List<CartItem>): Long = cart.sumOf { it.quantity.toLong() }

internal fun cartQuantities(cart: List<CartItem>): Map<Long, Int> =
    cart.groupingBy { it.productId }.fold(0) { sum, item -> (sum.toLong() + item.quantity).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }

fun calculateCheckout(products: List<Product>, cart: Map<Long, Int>, payment: Payment, cash: Long?): Checkout =
    calculateCheckout(products, cart.map { (id, qty) -> CartItem(productId = id, quantity = qty) }, payment, cash)

fun calculateCheckout(
    products: List<Product>, cart: List<CartItem>, payment: Payment, cash: Long?,
    charges: List<ExtraCharge> = emptyList()
): Checkout {
    if (cart.isEmpty()) throw SaleInputException("Keranjang masih kosong.")
    val byId = products.associateBy { it.id }
    for ((id, qty) in cartQuantities(cart)) {
        val item = byId[id] ?: throw SaleInputException("Ada produk yang sudah dihapus. Perbarui keranjang.")
        if (qty <= 0 || qty > item.stock) throw SaleInputException("Stok ${item.name} tidak mencukupi.")
    }
    val lines = cart.map { line ->
        val item = byId.getValue(line.productId)
        if (line.quantity <= 0) throw SaleInputException("Stok ${item.name} tidak mencukupi.")
        val unitPrice = cartLineUnitPrice(item, line)
            ?: throw SaleInputException("Pilihan ${item.name} sudah berubah. Perbarui keranjang.")
        SaleLine(saleId = 0, productName = item.name, unitPrice = unitPrice, quantity = line.quantity, productId = item.id,
            options = selectedOptionNames(line.options).joinToString(", "), note = line.note.trim(), discount = item.promoPerUnit)
    }
    val totals = orderTotals(lines.sumOf { it.unitPrice * it.quantity }, charges)
    val paid = if (payment == Payment.QRIS) totals.total else cash ?: 0
    if (paid < totals.total) throw SaleInputException("Uang tunai kurang dari total belanja.")
    return Checkout(lines, totals.total, paid, totals.subtotal, totals.charges)
}

class Register(private val db: PosDatabase) {
    private val dao = db.dao()

    suspend fun sell(
        cart: List<CartItem>, expectedTotal: Long, payment: Payment, cash: Long?, profile: StoreProfile, cashierId: Long,
        charges: List<ExtraCharge> = emptyList()
    ): Receipt = db.withTransaction {
        if (dao.cartSnapshot().sortedBy { it.id } != cart.sortedBy { it.id }) {
            throw SaleInputException("Keranjang berubah. Periksa kembali pesanan.")
        }
        val quantities = cartQuantities(cart)
        val menu = dao.menuById(quantities.keys)
        val order = calculateCheckout(availableCatalog(menu, emptyMap()), cart, payment, cash, charges)
        if (order.total != expectedTotal) throw SaleInputException("Total berubah. Periksa kembali pesanan.")
        val ingredients = checkedIngredientDemand(menu, quantities)
        val products = productStockDeductions(menu, quantities)
        val cashier = dao.cashier(cashierId) ?: dao.cashier(OWNER_CASHIER_ID)
            ?: throw SaleInputException("Profil kasir tidak ditemukan.")
        val sale = Sale(time = System.currentTimeMillis(), total = order.total, paid = order.paid,
            method = payment.label, storeName = profile.name, storeAddress = profile.address, logoName = profile.logoName,
            cashierName = cashier.name, subtotal = order.subtotal, ingredientDeductions = encodeStockDeductions(ingredients),
            productDeductions = encodeStockDeductions(products))
        val id = dao.addSale(sale)
        for ((productId, quantity) in products) {
            if (dao.decrement(productId, quantity) != 1) {
                throw SaleInputException("Stok berubah. Periksa keranjang.")
            }
        }
        for ((ingredientId, quantity) in ingredients) {
            if (dao.decrementIngredient(ingredientId, quantity) != 1) {
                throw SaleInputException("Stok bahan berubah. Periksa keranjang.")
            }
        }
        val unitCosts = menu.associate { it.product.id to it.unitHpp() }
        val lines = order.lines.map { it.copy(saleId = id, unitHpp = unitCosts[it.productId]) }
        dao.addLines(lines)
        val charges = order.charges.map { it.copy(saleId = id) }
        if (charges.isNotEmpty()) dao.addCharges(charges)
        dao.clearCart()
        Receipt(sale.copy(id = id), lines, charges)
    }

    internal suspend fun previewSaleDeletion(sale: Sale): SaleDeletion {
        val deductionIds = decodeStockDeductions(sale.ingredientDeductions).keys
        val counted = if (deductionIds.isEmpty()) emptyList() else {
            dao.ingredientsCountedAfter(sale.time).filter { it.id in deductionIds }
        }
        return SaleDeletion(sale, counted)
    }

    internal suspend fun deleteSale(deletion: SaleDeletion) {
        db.withTransaction {
            val id = deletion.sale.id
            val sale = dao.sale(id) ?: throw SaleInputException("Transaksi tidak ditemukan.")
            val countedIds = previewSaleDeletion(sale).countedIds
            if (countedIds != deletion.countedIds) {
                throw SaleInputException("Stok opname berubah. Tutup konfirmasi lalu coba lagi.")
            }
            restoreStockDeductions(sale.ingredientDeductions, "bahan", dao::ingredientExists, countedIds) { ingredientId, quantity ->
                dao.restockIngredient(ingredientId, quantity, MAX_INGREDIENT_STOCK)
            }
            restoreStockDeductions(sale.productDeductions, "produk", dao::productExists) { productId, quantity ->
                dao.restockProduct(productId, quantity, Int.MAX_VALUE)
            }
            dao.deleteSale(id)
        }
    }
}

val indonesia = Locale("id", "ID")
fun rupiah(amount: Long): String = "Rp" + NumberFormat.getIntegerInstance(indonesia).format(amount)
fun ticket(id: Long): String = "TRX-" + id.toString().padStart(5, '0')
fun dateLabel(time: Long): String = SimpleDateFormat("dd MMM yyyy • HH:mm", indonesia).format(time)
fun Receipt.asText() = buildString {
    appendLine(sale.storeName)
    if (sale.storeAddress.isNotBlank()) appendLine(sale.storeAddress)
    appendLine("${ticket(sale.id)} • ${dateLabel(sale.time)}")
    sale.cashierLabel?.let { appendLine(it) }
    appendLine()
    lines.forEach {
        appendLine("${it.quantity} × ${it.productName} — ${rupiah((it.unitPrice + it.discount) * it.quantity)}")
        it.detailLines().forEach { detail -> appendLine("  $detail") }
    }
    appendLine()
    if (hasAdjustments) appendLine("Subtotal: ${rupiah(sale.subtotal)}")
    charges.forEach { appendLine("${it.name}: ${rupiah(it.amount)}") }
    appendLine("Total: ${rupiah(sale.total)}")
    appendLine("${sale.method}: ${rupiah(sale.paid)}")
    if (sale.method == Payment.CASH.label) appendLine("Kembalian: ${rupiah(sale.paid - sale.total)}")
    append("Terima kasih sudah berbelanja!")
}

internal val Receipt.hasAdjustments get() = charges.isNotEmpty()
