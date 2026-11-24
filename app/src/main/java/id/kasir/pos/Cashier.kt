package id.kasir.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CashierScreen(
    products: List<Product>,
    cart: Map<Long, Int>,
    model: RegisterModel,
    query: String,
    onQueryChange: (String) -> Unit,
    compact: Boolean,
    searching: Boolean,
    onCart: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val results = products.filter { it.name.contains(query.trim(), true) || it.category.contains(query.trim(), true) }
    val count = cartItemCount(cart)
    val total = cartTotal(products, cart)
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 9.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    if (!compact) Surface(color = Pine, shape = Shape) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("01  /  MEJA KASIR", fontWeight = FontWeight.Bold, color = Lime, fontSize = 11.sp, letterSpacing = 2.sp)
                            Text("Penjualan baru", color = Color.White, fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                            Text("Pilih barang untuk memulai transaksi.", color = Color(0xFFD4E8DF), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(query, onQueryChange, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text(if (compact) "Cari produk" else "Cari nama atau kategori", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = if (query.isEmpty()) null else {
                            { IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Close, "Hapus pencarian") } }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        shape = Shape, colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Line, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Katalog toko", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("${results.size} produk", style = MaterialTheme.typography.labelMedium, color = Muted)
                    }
                    ErrorText(model.error)
                }
            }
            if (results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyPanel("Produk tidak ditemukan", "Coba kata kunci lain atau tambahkan produk di tab Produk.")
            }
            items(results, key = { it.id }) { product ->
                CatalogCard(product, cart[product.id] ?: 0) { model.adjust(product, 1) }
            }
        }
        if (!searching && (!compact || cart.isNotEmpty())) Surface(color = Paper) {
            Button(
                onClick = onCart,
                enabled = cart.isNotEmpty(),
                shape = Shape,
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = if (compact) 6.dp else 11.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
            ) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(Modifier.align(Alignment.CenterVertically), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ShoppingCart, null, Modifier.size(21.dp))
                        Column(Modifier.padding(start = 9.dp, end = 8.dp)) {
                            Text(when {
                                count == 0L -> "Keranjang kosong"
                                compact -> "Keranjang"
                                else -> "Lihat keranjang"
                            }, fontWeight = FontWeight.Bold)
                            if (!compact && count > 0) Text("$count barang", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(
                        if (compact && count > 0) "$count • ${rupiah(total)}" else rupiah(total),
                        modifier = Modifier.align(Alignment.CenterVertically),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CatalogCard(product: Product, quantity: Int, onAdd: () -> Unit) {
    Surface(shape = Shape, color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CategoryMark(product.category)
                Text(if (quantity > 0) "$quantity di keranjang" else "Stok ${product.stock}",
                    color = Muted, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 2)
            }
            Text(product.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(product.category, color = Muted, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(rupiah(product.price), modifier = Modifier.align(Alignment.CenterVertically), fontWeight = FontWeight.Bold,
                    color = Pine, style = MaterialTheme.typography.bodySmall)
                FilledTonalIconButton(
                    onClick = onAdd, enabled = quantity < product.stock,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Lime, contentColor = Pine)
                ) { Icon(Icons.Default.Add, "Tambah ${product.name}") }
            }
        }
    }
}

@Composable
internal fun CategoryMark(category: String) {
    val initial = category.trim().take(1).uppercase(indonesia)
    val tint = when (initial) {
        "M" -> Color(0xFFE4EDC7)
        "C" -> Color(0xFFF7E6CE)
        else -> SoftGreen
    }
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint), contentAlignment = Alignment.Center) {
        Text(initial, fontWeight = FontWeight.Bold, color = Pine)
    }
}

internal fun cartTotal(products: List<Product>, cart: Map<Long, Int>) =
    products.sumOf { it.price * (cart[it.id] ?: 0) }

@Composable
internal fun CartScreen(products: List<Product>, cart: Map<Long, Int>, model: RegisterModel, short: Boolean, onPay: () -> Unit) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val listed = products.filter { it.id in cart }
    val count = cartItemCount(cart)
    val total = cartTotal(products, cart)
    val stockIssue = cartStockIssue(products, cart)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (short) Text("$count barang", fontWeight = FontWeight.Bold)
                else Header("02  /  PERIKSA PESANAN", "Keranjang", "$count barang untuk transaksi ini.")
                ErrorText(model.error)
                ErrorText(stockIssue)
            }
            if (listed.isEmpty()) item { EmptyPanel("Belum ada barang", "Kembali ke kasir dan pilih produk.") }
            items(listed, key = { it.id }) { product ->
                val qty = cart[product.id] ?: 0
                Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold)
                            Text("${rupiah(product.price)} per barang", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { model.adjust(product, -1) }) {
                            Text("−", Modifier.semantics { contentDescription = "Kurangi ${product.name}" }, fontSize = 22.sp)
                        }
                        OutlinedButton(
                            onClick = { editingId = product.id },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp)
                                .semantics { contentDescription = "Ubah jumlah ${product.name}" }
                        ) { Text("$qty", fontWeight = FontWeight.Bold) }
                        IconButton(onClick = { model.adjust(product, 1) }, enabled = qty < product.stock) {
                            Icon(Icons.Default.Add, "Tambah ${product.name}")
                        }
                    }
                }
            }
            if (short) item { TotalBanner("Total pesanan", total) }
        }
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = if (short) 4.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!short) TotalBanner("Total pesanan", total)
            Button(onClick = onPay, enabled = listed.isNotEmpty() && stockIssue == null, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Text("Lanjut ke pembayaran")
            }
        }
    }
    listed.firstOrNull { it.id == editingId }?.let { product ->
        QuantityDialog(product, cart.getValue(product.id), onDismiss = { editingId = null }) { quantity ->
            model.setQuantity(product, quantity)
            editingId = null
        }
    }
}

@Composable
private fun QuantityDialog(product: Product, quantity: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val initial = quantity.toString()
        mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    }
    val entered = input.text.toIntOrNull()
    val valid = entered != null && canSetCartQuantity(quantity, entered, product.stock)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah jumlah") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(product.name, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.text.length <= Int.MAX_VALUE.toString().length && it.text.all { c -> c in '0'..'9' }) input = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = { Text("Jumlah barang") },
                    supportingText = { Text("Stok tersedia: ${product.stock}") },
                    isError = input.text.isNotEmpty() && !valid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onSet(checkNotNull(entered)) })
                )
                TextButton(onClick = { onSet(0) }) { Text("Hapus barang") }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSet(checkNotNull(entered)) }, enabled = valid) { Text("Terapkan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun PaymentScreen(products: List<Product>, cart: Map<Long, Int>, model: RegisterModel, onPaid: () -> Unit) {
    var method by rememberSaveable { mutableStateOf(Payment.CASH) }
    var amount by rememberSaveable { mutableStateOf("") }
    val total = cartTotal(products, cart)
    val stockIssue = cartStockIssue(products, cart)
    val cash = parseRupiah(amount, maxDigits = 18)
    val focusManager = LocalFocusManager.current
    val setCash: (Long) -> Unit = {
        amount = it.toString()
        model.clearError()
        focusManager.clearFocus()
    }
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp).verticalContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header("03  /  SELESAIKAN TRANSAKSI", "Pembayaran", "Catat pembayaran yang diterima dari pelanggan.")
        if (stockIssue != null || cart.isEmpty()) {
            EmptyPanel("Pembayaran belum tersedia", stockIssue ?: "Keranjang masih kosong.")
            return@Column
        }
        TotalBanner("Total yang harus dibayar", total)
        Text("Metode pembayaran", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Payment.entries.forEach { value ->
                FilterChip(selected = method == value, onClick = { method = value; model.clearError() }, label = { Text(value.label) })
            }
        }
        if (method == Payment.CASH) {
            OutlinedTextField(amount, { amount = it; model.clearError() },
                modifier = Modifier.fillMaxWidth(), label = { Text("Uang diterima") },
                prefix = { if (!amount.trimStart().startsWith("Rp", ignoreCase = true)) Text("Rp ") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { setCash(total) }) { Text("Uang pas") }
                quickCashAmounts(total).forEach { value ->
                    OutlinedButton(onClick = { setCash(value) }) { Text(rupiah(value)) }
                }
            }
            when {
                amount.isNotBlank() && cash == null -> ErrorText("Gunakan format Rupiah, misalnya Rp19.000,00.")
                cash != null && cash < total -> ErrorText("Uang tunai kurang ${rupiah(total - cash)}.")
                cash != null -> MetricRow("Kembalian", rupiah(cash - total), true)
            }
        } else {
            EmptyPanel("QRIS dicatat manual", "Pastikan dana sudah diterima sebelum menyimpan. Aplikasi tidak memproses pembayaran QRIS.")
        }
        ErrorText(model.error)
        Button(
            onClick = { model.sell(cart, total, method, cash, onPaid) },
            enabled = cart.isNotEmpty() && !model.busy && (method == Payment.QRIS || (cash != null && cash >= total)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
        ) {
            Text(when {
                model.busy -> "Menyimpan…"
                method == Payment.QRIS -> "Konfirmasi & simpan QRIS"
                else -> "Simpan transaksi"
            })
        }
    }
}

@Composable
internal fun TotalBanner(label: String, amount: Long) {
    Surface(shape = Shape, color = Pine) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, color = Color(0xFFD4E8DF), style = MaterialTheme.typography.labelLarge)
            Text(rupiah(amount), color = Lime, fontSize = 30.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
        }
    }
}
