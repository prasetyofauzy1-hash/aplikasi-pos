package id.kasir.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private val Shortage = Color(0xFFB3261E)
private val Surplus = Color(0xFF9C5007)

internal fun stockDifference(difference: Long, unit: String): String = when {
    difference == 0L -> "Sesuai"
    difference > 0 -> "+${NumberFormat.getIntegerInstance(indonesia).format(difference)} $unit"
    else -> "−${NumberFormat.getIntegerInstance(indonesia).format(-difference)} $unit"
}

private fun differenceColor(difference: Long) = when {
    difference < 0 -> Shortage
    difference > 0 -> Surplus
    else -> Pine
}

@Composable
internal fun StockOpnameScreen(ingredients: List<Ingredient>, model: RegisterModel, onSaved: () -> Unit) {
    var entries by rememberSaveable { mutableStateOf(mapOf<Long, String>()) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val counted = ingredients.mapNotNull { ingredient ->
        entries[ingredient.id]?.toIntOrNull()?.takeIf { it in 0..MAX_INGREDIENT_STOCK }?.let { ingredient to it }
    }
    val mismatched = counted.filter { (ingredient, quantity) -> quantity != ingredient.stock }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Header("INVENTARIS", "Stok opname",
                "Hitung bahan secara fisik, lalu isi hasilnya. Selisih dengan stok sistem langsung terlihat. Bahan yang dikosongkan tidak diubah.")
        }
        if (ingredients.isEmpty()) item { EmptyPanel("Belum ada bahan", "Tambahkan bahan di Stok bahan terlebih dahulu.") }
        else item {
            Text("${counted.size} dari ${ingredients.size} bahan dihitung • ${mismatched.size} selisih",
                color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        items(ingredients, key = { it.id }) { ingredient ->
            val entry = entries[ingredient.id].orEmpty()
            val physical = entry.toIntOrNull()?.takeIf { it in 0..MAX_INGREDIENT_STOCK }
            Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ingredient.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Text("Sistem: ${stockAmount(ingredient.stock, ingredient.unit)}", color = Muted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(entry, { changed ->
                            if (changed.length <= 9 && changed.all { it in '0'..'9' }) {
                                entries = if (changed.isEmpty()) entries - ingredient.id else entries + (ingredient.id to changed)
                            }
                        }, Modifier.weight(1f), label = { Text("Fisik (${ingredient.unit})") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), enabled = !model.busy)
                        TextButton(onClick = { entries = entries + (ingredient.id to ingredient.stock.toString()) },
                            enabled = !model.busy) { Text("Sama") }
                    }
                    if (physical != null) {
                        val difference = physical.toLong() - ingredient.stock
                        Text("Selisih: ${stockDifference(difference, ingredient.unit)}", color = differenceColor(difference),
                            fontWeight = FontWeight.SemiBold)
                    } else if (entry.isNotEmpty()) ErrorText("Jumlah melebihi batas stok.")
                }
            }
        }
        if (ingredients.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ErrorText(model.error)
                Button(onClick = { model.clearError(); confirming = true }, enabled = counted.isNotEmpty() && !model.busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) { Text("Simpan stok opname") }
            }
        }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { if (!model.busy) confirming = false },
        title = { Text("Simpan stok opname?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Stok sistem ${counted.size} bahan akan diganti dengan hasil hitungan fisik.")
                if (mismatched.isEmpty()) Text("Semua bahan yang dihitung sesuai.", color = Pine)
                mismatched.forEach { (ingredient, quantity) ->
                    val difference = quantity.toLong() - ingredient.stock
                    Text("${ingredient.name}: ${stockDifference(difference, ingredient.unit)}", color = differenceColor(difference))
                }
                ErrorText(model.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                model.saveStockCount(counted.associate { (ingredient, quantity) -> ingredient.id to quantity }) {
                    confirming = false
                    entries = emptyMap()
                    onSaved()
                }
            }, enabled = !model.busy) { Text("Ya, simpan") }
        },
        dismissButton = { TextButton(onClick = { confirming = false }, enabled = !model.busy) { Text("Batal") } }
    )
}

@Composable
internal fun StockOpnameHistoryScreen(reports: List<StockCountReport>) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Header("INVENTARIS", "Riwayat opname", "Hasil hitungan fisik dibandingkan dengan stok sistem saat opname disimpan.") }
        if (reports.isEmpty()) item { EmptyPanel("Belum ada stok opname", "Simpan stok opname untuk melihat selisihnya di sini.") }
        items(reports, key = { it.count.id }) { report ->
            val mismatched = report.lines.count { it.difference != 0L }
            Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(dateLabel(report.count.time), fontWeight = FontWeight.Bold)
                    Text("${report.lines.size} bahan dihitung • $mismatched selisih", color = Muted,
                        style = MaterialTheme.typography.labelMedium)
                    HorizontalDivider(color = Line)
                    report.lines.sortedBy { it.ingredientName.lowercase() }.forEach { line ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(line.ingredientName, fontWeight = FontWeight.SemiBold)
                                Text("Sistem ${stockAmount(line.systemStock, line.unit)} • Fisik ${stockAmount(line.countedStock, line.unit)}",
                                    color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(stockDifference(line.difference, line.unit), color = differenceColor(line.difference),
                                fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
