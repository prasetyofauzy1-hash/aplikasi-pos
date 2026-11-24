package id.kasir.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun CashierScreen(
    products: List<Product>,
    cart: List<CartItem>,
    model: RegisterModel,
    query: String,
    onQueryChange: (String) -> Unit,
    compact: Boolean,
    searching: Boolean,
    shiftLoaded: Boolean,
    shift: ShiftSummary?,
    onCart: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val results = products.filter { it.name.contains(query.trim(), true) || it.category.contains(query.trim(), true) }
    val count = cartItemCount(cart)
    val total = cartSubtotal(products, cart)
    val quantities = cartQuantities(cart)
    var choosingId by rememberSaveable { mutableStateOf<Long?>(null) }
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
                    CashierProfilePicker(model)
                    ShiftPanel(model, shiftLoaded, shift)
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
                CatalogCard(product, quantities[product.id] ?: 0, onChoose = { choosingId = product.id }) {
                    if (parseProductOptions(product.options).isEmpty()) model.addToCart(product) else choosingId = product.id
                }
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
    products.firstOrNull { it.id == choosingId }?.let { product ->
        OrderOptionsDialog(product, onDismiss = { choosingId = null }) { options, note ->
            model.addToCart(product, options, note)
            choosingId = null
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun OrderOptionsDialog(product: Product, onDismiss: () -> Unit, onAdd: (List<String>, String) -> Unit) {
    val options = parseProductOptions(product.options)
    var selected by rememberSaveable { mutableStateOf(listOf<String>()) }
    var note by rememberSaveable { mutableStateOf("") }
    val price = product.price - product.promoPerUnit + options.filter { it.name in selected }.sumOf { it.extraPrice }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.isNotEmpty()) {
                    Text("Varian", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            val on = option.name in selected
                            FilterChip(selected = on, onClick = { selected = if (on) selected - option.name else selected + option.name },
                                label = { Text(if (option.extraPrice > 0) "${option.name} +${rupiah(option.extraPrice)}" else option.name) })
                        }
                    }
                }
                OutlinedTextField(note, { note = cleanOptionText(it, 80) }, Modifier.fillMaxWidth(),
                    label = { Text("Catatan pesanan") }, placeholder = { Text("Mis. less sugar, tanpa es") }, singleLine = true)
                Text("${rupiah(price)} per barang", color = Pine, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(options.map { it.name }.filter { it in selected }, note) }, enabled = product.stock > 0) {
                Text("Tambah ke keranjang")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CatalogCard(product: Product, quantity: Int, onChoose: () -> Unit, onAdd: () -> Unit) {
    Surface(onClick = onChoose, enabled = quantity < product.stock, shape = Shape, color = Color.White, border = BorderStroke(1.dp, Line)) {
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
                Column(Modifier.align(Alignment.CenterVertically)) {
                    if (product.promoPerUnit > 0) Text(rupiah(product.price), color = Muted, style = MaterialTheme.typography.labelSmall,
                        textDecoration = TextDecoration.LineThrough)
                    Text(rupiah(product.price - product.promoPerUnit), fontWeight = FontWeight.Bold,
                        color = Pine, style = MaterialTheme.typography.bodySmall)
                }
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

internal fun cartIssue(products: List<Product>, cart: List<CartItem>) =
    cartStockIssue(products, cartQuantities(cart)) ?: cartOptionIssue(products, cart)

@Composable
internal fun CartScreen(
    products: List<Product>, cart: List<CartItem>, model: RegisterModel, short: Boolean, shiftOpen: Boolean, onPay: () -> Unit
) {
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val byId = products.associateBy { it.id }
    val listed = cart.filter { it.productId in byId }
    val quantities = cartQuantities(cart)
    val count = cartItemCount(cart)
    val total = cartSubtotal(products, cart)
    val stockIssue = cartIssue(products, cart)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                if (short) Text("$count barang", fontWeight = FontWeight.Bold)
                else Header("02  /  PERIKSA PESANAN", "Keranjang", "$count barang untuk transaksi ini.")
                ErrorText(model.error)
                ErrorText(stockIssue)
            }
            if (listed.isEmpty()) item { EmptyPanel("Belum ada barang", "Kembali ke kasir dan pilih produk.") }
            items(listed, key = { it.id }) { line ->
                val product = byId.getValue(line.productId)
                val qty = line.quantity
                val unitPrice = cartLineUnitPrice(product, line)
                val details = (selectedOptionNames(line.options) + listOfNotNull(line.note.takeIf { it.isNotBlank() }?.let { "Catatan: $it" }))
                Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold)
                            details.forEach { Text(it, color = Pine, style = MaterialTheme.typography.bodySmall) }
                            Text(if (unitPrice == null) "Pilihan sudah berubah" else "${rupiah(unitPrice)} per barang" +
                                if (product.promoPerUnit > 0) " (promo −${rupiah(product.promoPerUnit)})" else "",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { model.adjustLine(line, -1) }) {
                            Text("−", Modifier.semantics { contentDescription = "Kurangi ${product.name}" }, fontSize = 22.sp)
                        }
                        OutlinedButton(
                            onClick = { editingId = line.id },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            modifier = Modifier.defaultMinSize(minWidth = 48.dp)
                                .semantics { contentDescription = "Ubah jumlah ${product.name}" }
                        ) { Text("$qty", fontWeight = FontWeight.Bold) }
                        IconButton(onClick = { model.adjustLine(line, 1) }, enabled = (quantities[product.id] ?: 0) < product.stock) {
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
            if (!shiftOpen && listed.isNotEmpty()) {
                Text("Buka shift di tab Kasir sebelum membayar.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = onPay, enabled = shiftOpen && listed.isNotEmpty() && stockIssue == null, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Text("Lanjut ke pembayaran")
            }
        }
    }
    listed.firstOrNull { it.id == editingId }?.let { line ->
        val product = byId.getValue(line.productId)
        val others = (quantities[product.id] ?: 0) - line.quantity
        QuantityDialog(product.copy(stock = product.stock - others), line.quantity, onDismiss = { editingId = null }) { quantity ->
            model.setLineQuantity(line, quantity)
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
internal fun PaymentScreen(products: List<Product>, cart: List<CartItem>, model: RegisterModel, onPaid: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().navigationBarsPadding()) {
        PaymentContent(products, cart, model, (maxHeight - 24.dp).coerceAtLeast(0.dp), onPaid)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PaymentContent(
    products: List<Product>, cart: List<CartItem>, model: RegisterModel, maxQrSide: Dp, onPaid: () -> Unit
) {
    var method by rememberSaveable { mutableStateOf(Payment.CASH) }
    var amount by rememberSaveable { mutableStateOf("") }
    val totals = runCatching { orderTotals(cartSubtotal(products, cart), model.extraCharges) }
    val total = totals.getOrNull()?.total ?: 0
    val stockIssue = cartIssue(products, cart) ?: totals.exceptionOrNull()?.message
    val cash = parseRupiah(amount, maxDigits = 18)
    val focusManager = LocalFocusManager.current
    val setCash: (Long) -> Unit = {
        amount = it.toString()
        model.clearError()
        focusManager.clearFocus()
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header("03  /  SELESAIKAN TRANSAKSI", "Pembayaran", "Catat pembayaran yang diterima dari pelanggan.")
        if (stockIssue != null || cart.isEmpty()) {
            EmptyPanel("Pembayaran belum tersedia", stockIssue ?: "Keranjang masih kosong.")
            return@Column
        }
        totals.getOrNull()?.takeIf { it.charges.isNotEmpty() }?.let { order ->
            MetricRow("Subtotal", rupiah(order.subtotal))
            order.charges.forEach { MetricRow(it.name, rupiah(it.amount)) }
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
            StaticQrisPayment(model.qrisImageName, maxQrSide, Modifier.align(Alignment.CenterHorizontally))
            EmptyPanel("QRIS dicatat manual", "Minta pelanggan memasukkan total yang tertera. Pastikan dana sudah diterima sebelum menyimpan.")
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
