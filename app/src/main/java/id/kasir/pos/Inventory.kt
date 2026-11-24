package id.kasir.pos

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

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

internal fun checkedIngredientDemand(menu: List<MenuItem>, cart: Map<Long, Int>): Map<Long, Int> {
    val ingredients = menu.flatMap { it.recipe }.associate { it.ingredient.id to it.ingredient }
    return ingredientDemand(menu, cart).mapValues { (id, required) ->
        val ingredient = ingredients.getValue(id)
        if (required > ingredient.stock) throw SaleInputException("Stok bahan ${ingredient.name} tidak mencukupi.")
        required.toInt()
    }
}
