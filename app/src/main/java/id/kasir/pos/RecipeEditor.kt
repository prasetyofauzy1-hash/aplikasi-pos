package id.kasir.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal val recipeSaver = listSaver<Map<Long, String>, Any>(
    save = { draft -> draft.flatMap { (id, amount) -> listOf(id, amount) } },
    restore = { saved -> saved.chunked(2).associate { (id, amount) -> id as Long to amount as String } }
)

@Composable
internal fun RecipeEditor(
    ingredients: List<Ingredient>,
    draft: Map<Long, String>,
    onChange: (Map<Long, String>) -> Unit,
    enabled: Boolean
) {
    var choosing by remember { mutableStateOf(false) }
    val byId = ingredients.associateBy { it.id }
    val choices = ingredients.filterNot { it.id in draft }
    Text("Resep per porsi", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    Text("Stok menu dihitung dari bahan yang tersedia.", color = Muted, style = MaterialTheme.typography.bodySmall)
    if (ingredients.isEmpty() && draft.isEmpty()) {
        EmptyPanel("Belum ada bahan", "Tambahkan bahan dan stoknya di Produk → Stok bahan, lalu kembali untuk mengisi resep.")
    }
    draft.forEach { (id, quantity) ->
        key(id) {
            val ingredient = byId[id]
            Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ingredient?.name ?: "Bahan tidak tersedia", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { onChange(draft - id) }, enabled = enabled) { Text("Hapus") }
                    }
                    val unit = ingredient?.unit.orEmpty()
                    OutlinedTextField(quantity, { value ->
                        if (value.length <= 9 && value.all { it in '0'..'9' }) onChange(draft + (id to value))
                    }, Modifier.fillMaxWidth(), label = { Text("Jumlah per porsi ($unit)") },
                        supportingText = {
                            if (ingredient != null) Text("Stok: ${stockAmount(ingredient.stock, unit)}")
                        }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        enabled = enabled, isError = quantity.isNotEmpty() && (quantity.toIntOrNull() ?: 0) !in 1..MAX_INGREDIENT_STOCK)
                }
            }
        }
    }
    if (choices.isNotEmpty()) Box {
        OutlinedButton(onClick = { choosing = true }, enabled = enabled) { Text("Tambah bahan ke resep") }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            choices.forEach { ingredient ->
                DropdownMenuItem(
                    text = { Text("${ingredient.name} (${ingredient.unit})") },
                    onClick = { choosing = false; onChange(draft + (ingredient.id to "")) }
                )
            }
        }
    }
}
