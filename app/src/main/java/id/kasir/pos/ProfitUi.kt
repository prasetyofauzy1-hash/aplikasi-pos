package id.kasir.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.math.BigInteger
import java.text.NumberFormat

private enum class ProfitPage(val label: String) {
    MENU("Per menu"), SALES("Penjualan"), COSTS("Biaya bahan")
}

@Composable
internal fun ProfitGate(model: RegisterModel) {
    var pin by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val canSubmit = pin.length == 6 && !model.busy
    val submit: () -> Unit = {
        if (canSubmit) {
            val entered = pin
            pin = ""
            focus.clearFocus()
            model.unlockProfit(entered)
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header("KHUSUS PEMILIK", "Laba", "Masukkan PIN pemilik untuk melihat HPP dan laba.")
        ProfitCard {
            PinInput(pin, "PIN pemilik", !model.busy, model.error != null, ImeAction.Done,
                KeyboardActions(onDone = { submit() })) {
                pin = it
                model.clearError()
            }
            ErrorText(model.error)
            Button(submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth()) { Text("Buka Laba") }
        }
    }
}

@Composable
internal fun ProfitScreen(menu: List<MenuItem>, ingredients: List<Ingredient>, model: RegisterModel) {
    var page by rememberSaveable { mutableStateOf(ProfitPage.MENU) }
    var selectedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingIngredient by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingProduct by rememberSaveable { mutableStateOf<Long?>(null) }
    val (today, zone) = rememberReportClock().value
    val date = selectedDate ?: today
    val report = if (page == ProfitPage.SALES) {
        val period = remember(date, zone) { reportPeriod(date, zone) }
        key(period) {
            val receipts by remember(model, period) { model.dailyReceipts(period) }
                .collectAsStateWithLifecycle(initialValue = null)
            remember(receipts) { receipts?.let(::profitReport) }
        }
    } else null
    val duplicateNames = remember(report) {
        report?.menus.orEmpty().groupingBy { it.name.lowercase(indonesia) }.eachCount().filterValues { it > 1 }.keys
    }
    val menuCosts = remember(menu) { menu.associate { it.product.id to it.unitHpp() } }
    key(page) {
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Header("KHUSUS PEMILIK", "Laba", "Harga pokok penjualan (HPP) dan laba kotor, sebelum biaya operasional.")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfitPage.entries.forEach {
                        FilterChip(page == it, onClick = { page = it }, label = { Text(it.label) })
                    }
                }
            }
            when (page) {
                ProfitPage.MENU -> {
                    item { Text("Estimasi per porsi berdasarkan harga jual dan biaya bahan saat ini.", color = Muted) }
                    if (menu.isEmpty()) item { EmptyPanel("Belum ada menu", "Tambahkan menu di tab Produk.") }
                    items(menu, key = { it.product.id }) { item ->
                        val product = item.product
                        val hpp = menuCosts[product.id]
                        ProfitCard {
                            Text(product.name, fontWeight = FontWeight.Bold)
                            MetricRow("Harga jual", rupiah(product.price))
                            MetricRow("HPP per porsi", hpp?.let(::rupiah) ?: "Belum tersedia")
                            MetricRow("Estimasi laba per porsi", hpp?.let { rupiah(product.price - it) } ?: "—", strong = true)
                            if (item.recipe.isEmpty()) {
                                TextButton(onClick = {
                                    model.clearError()
                                    editingProduct = product.id
                                }, enabled = !model.busy) { Text("Atur HPP") }
                            } else if (hpp == null) {
                                Text("Lengkapi biaya seluruh bahan resep di Biaya bahan.", color = Muted)
                            }
                        }
                    }
                }
                ProfitPage.SALES -> {
                    item { ReportDateControls(date, today) { selectedDate = it.takeUnless { it == today } } }
                    when {
                        report == null -> item {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                        }
                        report.menus.isEmpty() -> item { EmptyPanel("Tidak ada penjualan", "Pilih tanggal lain untuk melihat laba.") }
                        else -> {
                            item {
                                ProfitCard {
                                    Text("Ringkasan harian", fontWeight = FontWeight.Bold)
                                    SalesProfitMetrics(report.totals)
                                    Text("HPP memakai biaya pada saat transaksi. Penjualan tanpa HPP tidak masuk hitungan laba.",
                                        color = Muted)
                                }
                            }
                            items(report.menus, key = { it.key }) { product ->
                                ProfitCard {
                                    Text(product.name, fontWeight = FontWeight.Bold)
                                    if (product.name.lowercase(indonesia) in duplicateNames) {
                                        Text(product.id?.let { "Produk #$it" } ?: "Catatan lama (tanpa kode produk)", color = Muted)
                                    }
                                    SalesProfitMetrics(product.amounts)
                                }
                            }
                        }
                    }
                }
                ProfitPage.COSTS -> {
                    item {
                        Text("Masukkan harga dan jumlah bahan yang dibeli dalam satuan stok. Contoh: Rp25.000 untuk 1.000 g.",
                            color = Muted)
                    }
                    if (ingredients.isEmpty()) item { EmptyPanel("Belum ada bahan", "Tambahkan bahan di Produk → Stok bahan.") }
                    items(ingredients, key = { it.id }) { ingredient ->
                        ProfitCard {
                            Text(ingredient.name, fontWeight = FontWeight.Bold)
                            val cost = ingredient.purchaseCost
                            val quantity = ingredient.purchaseQuantity
                            Text(if (cost != null && quantity != null) "${rupiah(cost)} / ${stockAmount(quantity, ingredient.unit)}"
                                else "Biaya belum diisi", color = Muted)
                            TextButton(onClick = {
                                model.clearError()
                                editingIngredient = ingredient.id
                            }, enabled = !model.busy) { Text("Atur biaya bahan") }
                        }
                    }
                }
            }
        }
    }
    ingredients.firstOrNull { it.id == editingIngredient }?.let { ingredient ->
        HppCostDialog(ingredient.name, ingredient.purchaseCost, ingredient,
            model, onSave = { cost, quantity, onDone -> model.saveIngredientCost(ingredient.id, cost, quantity, onDone) },
            onClose = { editingIngredient = null })
    }
    menu.firstOrNull { it.product.id == editingProduct }?.product?.let { product ->
        HppCostDialog(product.name, product.directHpp, null,
            model, onSave = { cost, _, onDone -> model.saveDirectHpp(product.id, cost, onDone) },
            onClose = { editingProduct = null })
    }
}

@Composable
private fun ProfitCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = Shape, color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

private fun profitRupiah(value: BigInteger): String = "Rp" + NumberFormat.getIntegerInstance(indonesia).format(value)

@Composable
private fun SalesProfitMetrics(amounts: ProfitAmounts) {
    val profit = amounts.grossProfit
    MetricRow("Porsi terjual", NumberFormat.getIntegerInstance(indonesia).format(amounts.quantity))
    if (amounts.withoutHpp > 0) {
        MetricRow("Porsi tanpa HPP", NumberFormat.getIntegerInstance(indonesia).format(amounts.withoutHpp))
    }
    MetricRow("Omzet dengan HPP", if (profit == null) "—" else profitRupiah(amounts.revenue))
    MetricRow("Total HPP", if (profit == null) "—" else profitRupiah(amounts.hpp))
    MetricRow(if (profit != null && amounts.withoutHpp > 0) "Laba kotor (sebagian)" else "Laba kotor",
        profit?.let(::profitRupiah) ?: "Belum tersedia", strong = true)
}

@Composable
private fun HppCostDialog(
    name: String, initialCost: Long?, ingredient: Ingredient?, model: RegisterModel,
    onSave: (Long?, Int?, () -> Unit) -> Unit, onClose: () -> Unit
) {
    var amount by rememberSaveable { mutableStateOf(initialCost?.toString().orEmpty()) }
    var units by rememberSaveable { mutableStateOf(ingredient?.purchaseQuantity?.toString().orEmpty()) }
    val cost = parseRupiah(amount, maxDigits = 9)
    val quantity = units.toIntOrNull()?.takeIf { it in 1..MAX_INGREDIENT_STOCK }
    val valid = (amount.isBlank() && units.isBlank()) || (cost != null && (ingredient == null || quantity != null))
    val close = { model.clearError(); onClose() }
    AlertDialog(
        onDismissRequest = { if (!model.busy) close() },
        title = { Text(if (ingredient == null) "HPP $name" else "Biaya $name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it.take(24); model.clearError() }, Modifier.fillMaxWidth(),
                    label = { Text(if (ingredient == null) "HPP per porsi (Rp)" else "Harga beli (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !model.busy)
                if (ingredient != null) {
                    OutlinedTextField(units, {
                        if (it.length <= 9 && it.all { digit -> digit in '0'..'9' }) {
                            units = it
                            model.clearError()
                        }
                    }, Modifier.fillMaxWidth(), label = { Text("Jumlah beli (${ingredient.unit})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !model.busy)
                }
                Text("Kosongkan isian jika biaya belum diketahui.", color = Muted)
                if (!valid) ErrorText("Isi harga yang valid${if (ingredient != null) " dan jumlah lebih dari 0" else ""}.")
                ErrorText(model.error)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(cost, quantity, close) }, enabled = valid && !model.busy) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = close, enabled = !model.busy) { Text("Batal") } }
    )
}
