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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun CashierScreen(products: List<Product>, model: RegisterModel, onCart: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = products.filter { it.name.contains(query.trim(), true) || it.category.contains(query.trim(), true) }
    val count = model.cart.values.sum()
    val total = cartTotal(products, model.cart)
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
                    Surface(color = Pine, shape = Shape) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("01  /  MEJA KASIR", fontWeight = FontWeight.Bold, color = Lime, fontSize = 11.sp, letterSpacing = 2.sp)
                            Text("Penjualan baru", color = Color.White, fontSize = 31.sp, lineHeight = 35.sp, fontWeight = FontWeight.Bold)
                            Text("Pilih barang untuk memulai transaksi.", color = Color(0xFFD4E8DF), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        placeholder = { Text("Cari nama atau kategori") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        shape = Shape, colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = Line, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Katalog toko", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        Text("${results.size} produk", style = MaterialTheme.typography.labelMedium, color = Muted)
                    }
                }
            }
            if (results.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyPanel("Produk tidak ditemukan", "Coba kata kunci lain atau tambahkan produk di tab Produk.")
            }
            items(results, key = { it.id }) { product ->
                CatalogCard(product, model.cart[product.id] ?: 0) { model.adjust(product, 1) }
            }
        }
        Surface(color = Paper) {
            Button(
                onClick = onCart,
                enabled = count > 0,
                shape = Shape,
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 11.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 5.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, null, Modifier.size(21.dp))
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text(if (count == 0) "Keranjang kosong" else "Lihat keranjang", fontWeight = FontWeight.Bold)
                    if (count > 0) Text("$count barang", style = MaterialTheme.typography.labelSmall)
                }
                Text(rupiah(total), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(rupiah(product.price), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                    color = Pine, style = MaterialTheme.typography.bodySmall)
                FilledTonalIconButton(
                    onClick = onAdd, enabled = quantity < product.stock,
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
internal fun CartScreen(products: List<Product>, model: RegisterModel, onPay: () -> Unit) {
    val listed = products.filter { it.id in model.cart }
    val count = model.cart.values.sum()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Header("02  /  PERIKSA PESANAN", "Keranjang", "$count barang untuk transaksi ini.") }
            if (listed.isEmpty()) item { EmptyPanel("Belum ada barang", "Kembali ke kasir dan pilih produk.") }
            items(listed, key = { it.id }) { product ->
                val qty = model.cart[product.id] ?: 0
                Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold)
                            Text("${rupiah(product.price)} per barang", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { model.adjust(product, -1) }) { Text("−", fontSize = 22.sp) }
                        Text("$qty", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { model.adjust(product, 1) }, enabled = qty < product.stock) {
                            Icon(Icons.Default.Add, "Tambah ${product.name}")
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TotalBanner("Total pesanan", cartTotal(products, model.cart))
            Button(onClick = onPay, enabled = listed.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                Text("Lanjut ke pembayaran")
            }
        }
    }
}

@Composable
internal fun PaymentScreen(products: List<Product>, model: RegisterModel, onPaid: () -> Unit) {
    var method by rememberSaveable { mutableStateOf(Payment.CASH) }
    var amount by rememberSaveable { mutableStateOf("") }
    val total = cartTotal(products, model.cart)
    val cash = amount.toLongOrNull()
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp).verticalContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header("03  /  SELESAIKAN TRANSAKSI", "Pembayaran", "Catat pembayaran yang diterima dari pelanggan.")
        TotalBanner("Total yang harus dibayar", total)
        Text("Metode pembayaran", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Payment.entries.forEach { value ->
                FilterChip(selected = method == value, onClick = { method = value; model.clearError() }, label = { Text(value.label) })
            }
        }
        if (method == Payment.CASH) {
            OutlinedTextField(amount, { amount = it.filter { c -> c in '0'..'9' }.take(18); model.clearError() },
                modifier = Modifier.fillMaxWidth(), label = { Text("Uang diterima") }, prefix = { Text("Rp ") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedButton(onClick = { amount = total.toString(); model.clearError() }) { Text("Isi uang pas") }
            MetricRow("Kembalian", rupiah(((cash ?: 0) - total).coerceAtLeast(0)), true)
            if (cash != null && cash < total) ErrorText("Uang tunai kurang ${rupiah(total - cash)}.")
        } else {
            EmptyPanel("QRIS dicatat manual", "Pastikan dana sudah diterima sebelum menyimpan. Aplikasi tidak memproses pembayaran QRIS.")
        }
        ErrorText(model.error)
        Button(
            onClick = { model.sell(total, method, cash, onPaid) },
            enabled = model.cart.isNotEmpty() && !model.busy && (method == Payment.QRIS || (cash ?: 0) >= total),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
        ) { Text(if (model.busy) "Menyimpan…" else "Simpan transaksi") }
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
