package id.kasir.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.text.NumberFormat

internal fun stockAmount(quantity: Int, unit: String): String =
    "${NumberFormat.getIntegerInstance(indonesia).format(quantity)} $unit"

@Composable
internal fun IngredientStockScreen(
    ingredients: List<Ingredient>,
    model: RegisterModel,
    onEdit: (Long) -> Unit,
    onStockOpname: () -> Unit,
    onStockOpnameHistory: () -> Unit
) {
    var restockingId by rememberSaveable { mutableStateOf<Long?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Header("INVENTARIS", "Stok bahan", "Isi stok bahan sebelum berjualan. Stok berkurang sesuai resep setiap kali penjualan disimpan.")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onEdit(0L) }, enabled = !model.busy) { Text("Tambah bahan") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onStockOpname, enabled = !model.busy && ingredients.isNotEmpty(),
                        modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Text("Stok opname")
                    }
                    OutlinedButton(onClick = onStockOpnameHistory, enabled = !model.busy, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) { Text("Riwayat opname") }
                }
            }
        }
        if (ingredients.isEmpty()) item { EmptyPanel("Belum ada bahan", "Tambahkan bahan dan jumlah stok yang tersedia.") }
        items(ingredients, key = { it.id }) { ingredient ->
            Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ingredient.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text(stockAmount(ingredient.stock, ingredient.unit),
                            color = if (ingredient.stock == 0) MaterialTheme.colorScheme.error else Pine,
                            fontWeight = FontWeight.SemiBold)
                    }
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onEdit(ingredient.id) }, enabled = !model.busy) { Text("Edit bahan") }
                        TextButton(onClick = { model.clearError(); restockingId = ingredient.id }, enabled = !model.busy) {
                            Text("Tambah stok")
                        }
                    }
                }
            }
        }
    }
    ingredients.firstOrNull { it.id == restockingId }?.let { ingredient ->
        RestockDialog(ingredient, model) { model.clearError(); restockingId = null }
    }
}

@Composable
private fun RestockDialog(ingredient: Ingredient, model: RegisterModel, onClose: () -> Unit) {
    var amount by rememberSaveable(ingredient.id) { mutableStateOf("") }
    val added = amount.toIntOrNull()?.takeIf { it > 0 && it <= MAX_INGREDIENT_STOCK - ingredient.stock }
    AlertDialog(
        onDismissRequest = { if (!model.busy) onClose() },
        title = { Text("Tambah stok ${ingredient.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Stok saat ini: ${stockAmount(ingredient.stock, ingredient.unit)}")
                StockField(amount, { amount = it }, "Jumlah tambahan (${ingredient.unit})", enabled = !model.busy)
                if (added != null) Text("Stok setelah ditambah: ${stockAmount(ingredient.stock + added, ingredient.unit)}")
                else if (amount.isNotEmpty()) ErrorText("Masukkan jumlah lebih dari 0 tanpa melebihi batas stok.")
                ErrorText(model.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { added?.let { model.restockIngredient(ingredient.id, it, onClose) } },
                enabled = added != null && !model.busy) { Text("Tambah stok") }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !model.busy) { Text("Batal") } }
    )
}

@Composable
internal fun IngredientForm(ingredient: Ingredient, model: RegisterModel, onClose: () -> Unit) {
    val isNew = ingredient.id == 0L
    var name by rememberSaveable(ingredient.id) { mutableStateOf(ingredient.name) }
    var unit by rememberSaveable(ingredient.id) { mutableStateOf(ingredient.unit) }
    var stock by rememberSaveable(ingredient.id) { mutableStateOf(if (isNew) "" else ingredient.stock.toString()) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val quantity = stock.toIntOrNull()?.takeIf { it in 0..MAX_INGREDIENT_STOCK }
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp).verticalContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("STOK BAHAN", if (isNew) "Bahan baru" else "Edit bahan", "Gunakan satuan yang sama saat mengisi resep.")
        OutlinedTextField(name, { name = it.take(70) }, Modifier.fillMaxWidth(), label = { Text("Nama bahan") },
            singleLine = true, enabled = !model.busy)
        if (isNew) {
            Text("Satuan", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ingredientUnits.forEach {
                    FilterChip(selected = unit == it, onClick = { unit = it }, label = { Text(it) }, enabled = !model.busy)
                }
            }
        } else Text("Satuan: $unit")
        StockField(stock, { stock = it }, if (isNew) "Stok awal ($unit)" else "Stok tersedia ($unit)", enabled = !model.busy)
        ErrorText(model.error)
        Button(onClick = {
            quantity?.let { model.saveIngredient(ingredient.copy(name = name.trim(), unit = unit, stock = it), onClose) }
        }, enabled = !model.busy && name.isNotBlank() && quantity != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Simpan bahan") }
        if (!isNew) TextButton(onClick = { confirming = true }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Hapus bahan", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Hapus bahan?") },
        text = { Text("${ingredient.name} hanya bisa dihapus jika tidak dipakai dalam resep.") },
        confirmButton = {
            TextButton(onClick = { confirming = false; model.deleteIngredient(ingredient.id, onClose) }) { Text("Ya, hapus") }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Batal") } }
    )
}

@Composable
private fun StockField(value: String, onChange: (String) -> Unit, label: String, enabled: Boolean) {
    OutlinedTextField(value, { changed ->
        if (changed.length <= 9 && changed.all { it in '0'..'9' }) onChange(changed)
    }, Modifier.fillMaxWidth(), label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true, enabled = enabled)
}
