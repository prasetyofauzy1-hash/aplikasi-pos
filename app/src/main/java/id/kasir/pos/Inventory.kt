package id.kasir.pos

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import org.json.JSONObject

internal const val MAX_INGREDIENT_STOCK = 999_999_999
internal val ingredientUnits = listOf("g", "ml", "buah")

@Entity(indices = [Index(value = ["name"], unique = true)])
data class Ingredient(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val unit: String,
    val stock: Int
)

@Entity(
    primaryKeys = ["productId", "ingredientId"],
    foreignKeys = [
        ForeignKey(entity = Product::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Ingredient::class, parentColumns = ["id"], childColumns = ["ingredientId"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [Index("ingredientId")]
)
data class RecipeLine(
    val productId: Long,
    val ingredientId: Long,
    val quantity: Int
)

data class RecipeComponent(
    @Embedded val line: RecipeLine,
    @Relation(parentColumn = "ingredientId", entityColumn = "id") val ingredient: Ingredient
)

data class MenuItem(
    @Embedded val product: Product,
    @Relation(entity = RecipeLine::class, parentColumn = "id", entityColumn = "productId")
    val recipe: List<RecipeComponent>
) {
    fun availableStock(reserved: Map<Long, Long> = emptyMap(), inCart: Int = 0): Int =
        recipe.minOfOrNull { (line, ingredient) ->
            if (line.quantity <= 0) 0
            else {
                val usedByOthers = ((reserved[ingredient.id] ?: 0L) - line.quantity.toLong() * inCart).coerceAtLeast(0)
                val remaining = (ingredient.stock - usedByOthers).coerceAtLeast(0)
                (remaining / line.quantity).toInt()
            }
        } ?: product.stock
}

internal fun ingredientDemand(menu: List<MenuItem>, cart: Map<Long, Int>): Map<Long, Long> = buildMap {
    menu.forEach { item ->
        val servings = cart[item.product.id] ?: return@forEach
        if (servings <= 0) return@forEach
        for ((line, _) in item.recipe) {
            val required = line.quantity.toLong() * servings
            if (required <= 0) continue
            val current = get(line.ingredientId) ?: 0L
            put(line.ingredientId, if (current > Long.MAX_VALUE - required) Long.MAX_VALUE else current + required)
        }
    }
}

internal fun availableCatalog(menu: List<MenuItem>, cart: Map<Long, Int>): List<Product> {
    val reserved = ingredientDemand(menu, cart)
    return menu.map { item ->
        item.product.copy(stock = item.availableStock(reserved, cart[item.product.id] ?: 0))
    }
}

internal fun cartStockIssue(available: List<Product>, cart: Map<Long, Int>): String? {
    val byId = available.associateBy { it.id }
    return cart.entries.firstNotNullOfOrNull { (id, quantity) ->
        val product = byId[id]
        when {
            product == null -> "Ada produk yang tidak tersedia. Perbarui keranjang."
            quantity !in 1..product.stock -> "Stok ${product.name} tidak cukup. Kurangi jumlah atau tambah stok."
            else -> null
        }
    }
}

internal fun checkedIngredientDemand(menu: List<MenuItem>, cart: Map<Long, Int>): Map<Long, Int> {
    val ingredients = menu.flatMap { it.recipe }.associate { it.ingredient.id to it.ingredient }
    return ingredientDemand(menu, cart).mapValues { (id, required) ->
        val ingredient = ingredients.getValue(id)
        if (required > ingredient.stock) throw SaleInputException("Stok bahan ${ingredient.name} tidak mencukupi.")
        required.toInt()
    }
}

@Entity
data class StockCount(@PrimaryKey(autoGenerate = true) val id: Long = 0, val time: Long)

@Entity(
    foreignKeys = [ForeignKey(entity = StockCount::class, parentColumns = ["id"], childColumns = ["countId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("countId")]
)
data class StockCountLine(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val countId: Long,
    val ingredientId: Long?,
    val ingredientName: String,
    val unit: String,
    val systemStock: Int,
    val countedStock: Int
) {
    val difference: Long get() = countedStock.toLong() - systemStock
}

data class StockCountReport(
    @Embedded val count: StockCount,
    @Relation(parentColumn = "id", entityColumn = "countId") val lines: List<StockCountLine>
)

internal fun stockCountLines(ingredients: List<Ingredient>, counted: Map<Long, Int>): List<StockCountLine> {
    if (counted.isEmpty()) throw SaleInputException("Isi hasil hitungan fisik setidaknya satu bahan.")
    val byId = ingredients.associateBy { it.id }
    return counted.map { (id, quantity) ->
        val ingredient = byId[id] ?: throw SaleInputException("Ada bahan yang sudah dihapus. Buka stok opname lagi.")
        if (quantity !in 0..MAX_INGREDIENT_STOCK) throw SaleInputException("Jumlah ${ingredient.name} tidak valid.")
        StockCountLine(countId = 0, ingredientId = id, ingredientName = ingredient.name, unit = ingredient.unit,
            systemStock = ingredient.stock, countedStock = quantity)
    }.sortedBy { it.ingredientName.lowercase() }
}

internal fun productStockDeductions(menu: List<MenuItem>, cart: Map<Long, Int>): Map<Long, Int> =
    menu.filter { it.recipe.isEmpty() }.associate { it.product.id to cart.getValue(it.product.id) }

internal fun encodeStockDeductions(deductions: Map<Long, Int>): String =
    JSONObject().apply {
        for ((id, quantity) in deductions) put(id.toString(), quantity)
    }.toString()

internal fun decodeStockDeductions(snapshot: String?): Map<Long, Int> {
    if (snapshot == null) return emptyMap()
    val deductions = JSONObject(snapshot)
    return deductions.keys().asSequence().associate { it.toLong() to deductions.getInt(it) }
}

internal suspend fun restoreStockDeductions(
    snapshot: String?,
    stockName: String,
    exists: suspend (id: Long) -> Boolean,
    skipIds: Set<Long> = emptySet(),
    restock: suspend (id: Long, quantity: Int) -> Int
) {
    for ((id, quantity) in decodeStockDeductions(snapshot)) {
        if (id in skipIds) continue
        if (quantity <= 0 || (restock(id, quantity) != 1 && exists(id))) {
            throw SaleInputException("Stok $stockName tidak dapat dikembalikan. Periksa apakah stok melebihi batas.")
        }
    }
}
