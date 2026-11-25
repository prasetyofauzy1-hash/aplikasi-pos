package id.kasir.pos

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun Modifier.verticalContent() =
    verticalScroll(rememberScrollState()).padding(vertical = 12.dp)

@Composable
internal fun EmptyPanel(title: String, detail: String) {
    Surface(color = SoftGreen, shape = Shape) {
        Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold, color = Pine)
            Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun MetricRow(label: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = if (strong) Ink else Muted,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
        Text(value, color = if (strong) Pine else Ink, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End)
    }
}

@Composable
internal fun ProductsScreen(menu: List<MenuItem>, onEdit: (Long) -> Unit, onIngredients: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = menu.filter { it.product.name.contains(filter.trim(), true) || it.product.category.contains(filter.trim(), true) }
    val counts = menu.map { it.availableStock() }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Header("02  /  INVENTARIS", "Produk", "Isi stok bahan, lalu atur resep, kategori, dan harga menu.")
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${menu.size} produk • ${counts.count { it == 0 }} habis • ${counts.count { it in 1..5 }} menipis",
                    color = Muted, style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onIngredients, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) { Text("Stok bahan") }
                    Button(onClick = { onEdit(0L) }, modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Tambah produk")
                    }
                }
            }
        }
        item {
            OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Cari nama atau kategori") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = Shape, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line,
                    unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
        }
        if (shown.isEmpty()) item { EmptyPanel("Belum ada hasil", "Tambahkan produk atau ubah pencarian.") }
        items(shown, key = { it.product.id }) { item ->
            val product = item.product
            val available = item.availableStock()
            Surface(onClick = { onEdit(product.id) }, shape = Shape, border = BorderStroke(1.dp, Line), color = Color.White) {
                Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CategoryMark(product.category)
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(product.category, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(rupiah(product.price), color = Pine, fontWeight = FontWeight.Bold)
                        Text(when {
                            available == 0 -> "Stok habis"
                            item.recipe.isNotEmpty() -> "$available porsi"
                            else -> "Stok $available"
                        }, color = if (available <= 5) Color(0xFF9C5007) else Muted,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProductForm(item: MenuItem, ingredients: List<Ingredient>, model: RegisterModel, onClose: () -> Unit) {
    val product = item.product
    val isNew = product.id == 0L
    var name by rememberSaveable { mutableStateOf(product.name) }
    var category by rememberSaveable { mutableStateOf(product.category) }
    var price by rememberSaveable { mutableStateOf(if (isNew) "" else product.price.toString()) }
    var stock by rememberSaveable { mutableStateOf(product.stock.toString()) }
    var optionDraft by rememberSaveable { mutableStateOf(formatOptionDraft(parseProductOptions(product.options))) }
    var promoMode by rememberSaveable { mutableIntStateOf(if (product.promoPercent > 0) 1 else if (product.promoAmount > 0) 2 else 0) }
    var promoInput by rememberSaveable {
        mutableStateOf(when { product.promoPercent > 0 -> percentLabel(product.promoPercent).removeSuffix("%")
            product.promoAmount > 0 -> product.promoAmount.toString(); else -> "" })
    }
    var draft by rememberSaveable(stateSaver = recipeSaver) {
        mutableStateOf(item.recipe.associate { it.ingredient.id to it.line.quantity.toString() })
    }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val numericPrice = parseRupiah(price, maxDigits = 9)
    val numericStock = stock.toIntOrNull()
    val useDirectStock = !isNew && item.recipe.isEmpty() && draft.isEmpty()
    val recipe = draft.mapNotNull { (id, amount) ->
        amount.toIntOrNull()?.takeIf { it in 1..MAX_INGREDIENT_STOCK }?.let { id to it }
    }.toMap()
    val options = parseOptionDraft(optionDraft)
    val promo = when (promoMode) {
        1 -> parsePercent(promoInput)?.let { Discount(percent = it) }
        2 -> parseRupiah(promoInput, maxDigits = 9)?.takeIf { it > 0 && (numericPrice == null || it <= numericPrice) }?.let { Discount(amount = it) }
        else -> Discount()
    }
    val validRecipe = recipe.isNotEmpty() && recipe.size == draft.size && recipe.keys.all { id -> ingredients.any { it.id == id } }
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp).verticalContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("INVENTARIS", if (isNew) "Produk baru" else "Edit produk", "Lengkapi informasi yang digunakan di kasir.")
        OutlinedTextField(name, { name = it.take(70) }, Modifier.fillMaxWidth(), label = { Text("Nama produk") }, singleLine = true)
        OutlinedTextField(category, { category = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Kategori") }, singleLine = true)
        OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(), label = { Text("Harga jual") },
            prefix = { if (!price.trimStart().startsWith("Rp", ignoreCase = true)) Text("Rp ") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (price.isNotBlank() && numericPrice == null) ErrorText("Gunakan format Rupiah hingga 9 digit, misalnya Rp19.000,00.")
        Text("Promo produk", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Tanpa promo", "Persen", "Potongan Rp").forEachIndexed { index, label ->
                FilterChip(selected = promoMode == index, onClick = { promoMode = index; promoInput = "" }, label = { Text(label) })
            }
        }
        if (promoMode != 0) {
            OutlinedTextField(promoInput, { promoInput = it }, Modifier.fillMaxWidth(),
                label = { Text(if (promoMode == 1) "Diskon per barang" else "Potongan per barang") },
                prefix = { if (promoMode == 2) Text("Rp ") }, suffix = { if (promoMode == 1) Text("%") }, singleLine = true,
                supportingText = {
                    val final = if (promo != null && numericPrice != null) numericPrice - promo.of(numericPrice) else null
                    Text(if (final != null) "Harga setelah promo: ${rupiah(final)}" else "Promo dipotong otomatis saat checkout.")
                },
                isError = promoInput.isNotBlank() && promo == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        OutlinedTextField(optionDraft, { if (it.length <= 600 || it.length < optionDraft.length) optionDraft = it }, Modifier.fillMaxWidth(), label = { Text("Varian (opsional)") },
            placeholder = { Text("Less sugar\nCup besar +5000") }, minLines = 2,
            supportingText = { Text(if (options == null) "Satu varian per baris, nama tidak boleh sama. Tambahan harga: +5000."
                else "Satu varian per baris. Tulis +5000 untuk tambahan harga.") },
            isError = options == null)
        if (useDirectStock) {
            OutlinedTextField(stock, { if (it.length <= 5 && it.all { c -> c in '0'..'9' }) stock = it }, Modifier.fillMaxWidth(),
                label = { Text("Stok produk") }, supportingText = { Text("Stok langsung dipakai sampai resep ditambahkan.") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        RecipeEditor(ingredients, draft, { draft = it }, enabled = !model.busy)
        ErrorText(model.error)
        Button(
            onClick = {
                if (numericPrice != null && promo != null && options != null && (validRecipe || (useDirectStock && numericStock != null))) {
                    model.saveProduct(
                        product.copy(name = name.trim(), category = category.trim(), price = numericPrice, stock = numericStock ?: 0,
                            options = formatProductOptions(options), promoPercent = promo.percent, promoAmount = promo.amount),
                        recipe, onClose
                    )
                }
            },
            enabled = !model.busy && name.isNotBlank() && category.isNotBlank() && numericPrice != null && promo != null && options != null &&
                (validRecipe || (useDirectStock && numericStock != null)),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
        ) { Text("Simpan produk") }
        if (!isNew) TextButton(onClick = { confirming = true }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
            Text("Hapus produk", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Hapus produk?") },
        text = { Text("${product.name} dihapus dari katalog. Struk lama tidak berubah.") },
        confirmButton = {
            TextButton(onClick = { confirming = false; model.deleteProduct(product, onClose) }) { Text("Ya, hapus") }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Batal") } }
    )
}

@Composable
internal fun rememberReportClock(): State<Pair<Long, TimeZone>> {
    val context = LocalContext.current
    fun currentDay(): Pair<Long, TimeZone> {
        val zone = TimeZone.getDefault()
        return reportDate(System.currentTimeMillis(), zone) to zone
    }
    return produceState(currentDay(), context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                value = currentDay()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        value = currentDay()
        awaitDispose { context.unregisterReceiver(receiver) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportDateControls(date: Long, today: Long, onSelect: (Long) -> Unit) {
    var choosingDate by rememberSaveable { mutableStateOf(false) }
    if (choosingDate) {
        val picker = remember(date) { DatePickerState(locale = indonesia, initialSelectedDateMillis = date) }
        val configuration = LocalConfiguration.current
        val localized = remember(configuration) { Configuration(configuration).apply { setLocale(indonesia) } }
        DatePickerDialog(
            onDismissRequest = { choosingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let(onSelect)
                    choosingDate = false
                }) { Text("Pilih") }
            },
            dismissButton = { TextButton(onClick = { choosingDate = false }) { Text("Batal") } }
        ) {
            CompositionLocalProvider(LocalConfiguration provides localized) {
                DatePicker(
                    state = picker,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    title = { Text("Pilih tanggal", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                    headline = {
                        Text(
                            picker.selectedDateMillis?.let(::reportDateLabel).orEmpty(),
                            modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                            style = MaterialTheme.typography.headlineSmall
                        )
                    },
                    showModeToggle = false
                )
            }
        }
    }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onSelect(moveReportDate(date, -1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Hari sebelumnya")
            }
            OutlinedButton(onClick = { choosingDate = true }, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.DateRange, contentDescription = "Pilih tanggal", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(reportDateLabel(date))
            }
            IconButton(onClick = { onSelect(moveReportDate(date, 1)) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Hari berikutnya")
            }
        }
        if (date != today) {
            TextButton(onClick = { onSelect(today) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("Kembali ke hari ini")
            }
        }
    }
}

@Composable
internal fun HistoryScreen(model: RegisterModel) {
    val (today, zone) = rememberReportClock().value
    var selectedDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var showProducts by rememberSaveable { mutableStateOf(false) }
    var saleToDelete by remember { mutableStateOf<SaleDeletion?>(null) }
    val listState = rememberLazyListState()
    val date = selectedDate ?: today
    val period = remember(date, zone) { reportPeriod(date, zone) }
    val report = key(period) {
        val receipts by remember(model, period) { model.dailyReceipts(period) }
            .collectAsStateWithLifecycle(initialValue = null)
        remember(receipts) { receipts?.let(::dailyReport) }
    }
    if (report == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val duplicateProductNames = remember(report.products) {
        report.products.groupingBy { it.name.lowercase(indonesia) }.eachCount().filterValues { it > 1 }.keys
    }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), state = listState
    ) {
        item { Header("03  /  LAPORAN PENJUALAN", "Riwayat", "Cocokkan penjualan harian dan buka kembali struk.") }
        item { ReportDateControls(date, today) { selectedDate = it.takeUnless { it == today } } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val label = if (date == today) "Penjualan hari ini" else "Penjualan harian"
                TotalBanner("$label  •  ${report.sales.size} transaksi", report.total)
                Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Payment.entries.forEach { method ->
                            MetricRow(method.label, rupiah(report.totalsByPayment[method.label] ?: 0L))
                        }
                    }
                }
                SalesExportButton(model)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!showProducts, onClick = { showProducts = false }, label = { Text("Transaksi") })
                FilterChip(showProducts, onClick = { showProducts = true }, label = { Text("Produk terjual") })
            }
            if (saleToDelete == null) ErrorText(model.error)
        }
        if (report.sales.isEmpty()) {
            item { EmptyPanel("Tidak ada transaksi", "Tidak ada penjualan pada tanggal ini.") }
        } else if (showProducts) {
            items(report.products, key = { it.key }) { product ->
                Surface(shape = Shape, color = Color.White, border = BorderStroke(1.dp, Line)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold)
                            if (product.name.lowercase(indonesia) in duplicateProductNames) {
                                Text(product.duplicateNameDetail, color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(rupiah(product.revenue), color = Pine, fontWeight = FontWeight.Bold)
                            Text("${product.quantity} terjual", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else items(report.sales, key = { it.id }) { sale ->
            Surface(shape = Shape, color = Color.White, border = BorderStroke(1.dp, Line)) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = !model.busy) { model.openReceipt(sale.id) }.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ticket(sale.id), fontWeight = FontWeight.Bold)
                            Text(dateLabel(sale.time), color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(rupiah(sale.total), color = Pine, fontWeight = FontWeight.Bold)
                            Text(sale.method, color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Text("›", color = Pine)
                    }
                    TextButton(
                        onClick = { model.previewSaleDeletion(sale) { saleToDelete = it } },
                        enabled = !model.busy,
                        modifier = Modifier.align(Alignment.End).padding(horizontal = 14.dp)
                    ) { Text("Hapus transaksi", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    saleToDelete?.let { deletion ->
        DeleteSaleDialog(deletion, model) {
            model.clearError()
            saleToDelete = null
        }
    }
}

@Composable
private fun DeleteSaleDialog(deletion: SaleDeletion, model: RegisterModel, onDismiss: () -> Unit) {
    val sale = deletion.sale
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!model.busy) onDismiss() },
        title = { Text("Hapus transaksi?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${ticket(sale.id)} senilai ${rupiah(sale.total)} akan dihapus dari riwayat dan laporan penjualan.")
                Text(if (sale.ingredientDeductions == null && sale.productDeductions == null) {
                    "Catatan stok tidak tersedia untuk transaksi lama ini. Sesuaikan stok secara manual jika diperlukan."
                } else {
                    "Stok akan dikembalikan untuk bahan dan produk yang masih tersedia. Penghapusan tidak dapat dibatalkan."
                })
                if (deletion.countedIngredients.isNotEmpty()) {
                    val names = deletion.countedIngredients.joinToString(", ") { it.name }
                    Text("Stok bahan berikut tidak dikembalikan karena sudah dihitung lewat stok opname setelah transaksi: $names.")
                }
                Text("Masukkan PIN pemilik untuk mengonfirmasi.")
                PinInput(pin, "PIN pemilik", !model.busy, model.error != null, ImeAction.Done, KeyboardActions()) {
                    pin = it
                    model.clearError()
                }
                ErrorText(model.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                model.deleteSale(deletion, pin, onDismiss)
                pin = ""
            }, enabled = !model.busy && pin.length == 6) {
                Text(if (model.busy) "Menghapus…" else "Hapus")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !model.busy) { Text("Batal") } }
    )
}

@Composable
internal fun ReceiptScreen(receipt: Receipt, model: RegisterModel, fromCheckout: Boolean, onDone: () -> Unit) {
    val sale = receipt.sale
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp).verticalContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        EmptyPanel(if (fromCheckout) "✓  Transaksi tersimpan" else "Struk tersimpan", "${ticket(sale.id)}  •  ${sale.method}")
        Surface(shape = Shape, color = Color.White, border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val logoSize = if (sale.logoName.isBlank()) Modifier.size(54.dp)
                        else Modifier.size(width = 128.dp, height = 88.dp)
                    StoreLogo(sale.logoName, logoSize)
                    Text(sale.storeName, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    if (sale.storeAddress.isNotBlank()) Text(sale.storeAddress, textAlign = TextAlign.Center, color = Muted)
                    Text(dateLabel(sale.time), color = Muted, style = MaterialTheme.typography.bodySmall)
                    sale.cashierLabel?.let { Text(it, textAlign = TextAlign.Center, color = Muted) }
                }
                HorizontalDivider(color = Line)
                receipt.lines.forEach { line ->
                    Column {
                        MetricRow("${line.quantity} × ${line.productName}", rupiah(line.quantity * (line.unitPrice + line.discount)))
                        line.detailLines().forEach { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                HorizontalDivider(color = Line)
                if (receipt.hasAdjustments) MetricRow("Subtotal", rupiah(sale.subtotal))
                receipt.charges.forEach { MetricRow(it.name, rupiah(it.amount)) }
                MetricRow("Total", rupiah(sale.total), strong = true)
                MetricRow(sale.method, rupiah(sale.paid))
                if (sale.method == Payment.CASH.label) MetricRow("Kembalian", rupiah(sale.paid - sale.total))
                Text("Terima kasih sudah berbelanja!", modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        PrinterActionButton(model, "Cetak struk") { onResult -> model.printReceipt(receipt, onResult) }
        ShareReceiptButton(receipt)
        EmailReceiptButton(receipt)
        if (fromCheckout) {
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Transaksi baru") }
        } else {
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Kembali ke riwayat") }
        }
    }
}

@Composable
internal fun SettingsScreen(model: RegisterModel, onLinkOwner: () -> Unit) {
    val profile = model.profile
    var name by rememberSaveable(profile.name) { mutableStateOf(profile.name) }
    var address by rememberSaveable(profile.address) { mutableStateOf(profile.address) }
    var newLogo by rememberSaveable { mutableStateOf<Uri?>(null) }
    var removeLogo by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val edited = {
        saved = false
        model.clearError()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { newLogo = uri; removeLogo = false; edited() }
    }
    val selection = newLogo
    val preview by key(selection) {
        produceState<Result<Bitmap>?>(initialValue = null) {
            if (selection != null) {
                value = try {
                    Result.success(withContext(Dispatchers.IO) { decodeStoreImage(context, selection) })
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }
    val image = remember(preview) { preview?.getOrNull()?.asImageBitmap() }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp).verticalContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Header("04  /  PENGATURAN", "Profil toko", "Identitas ini muncul di struk transaksi baru.")
        CashierProfilesSettings(model)
        StaticQrisSettings(model, onEdit = { saved = false })
        Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
            Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val logoModifier = Modifier.size(72.dp)
                if (image != null) Image(image, "Pratinjau logo toko", logoModifier, contentScale = ContentScale.Fit)
                else StoreLogo(if (removeLogo || selection != null) "" else profile.logoName, logoModifier)
                Column(Modifier.weight(1f)) {
                    Text("Logo toko", fontWeight = FontWeight.Bold)
                    Text(when {
                        image != null -> "Logo belum disimpan"
                        preview?.isFailure == true -> "Foto tidak dapat dibuka"
                        selection != null -> "Memuat foto…"
                        removeLogo -> "Logo akan dihapus"
                        profile.logoName.isNotBlank() -> "Logo tersimpan"
                        else -> "Opsional"
                    }, style = MaterialTheme.typography.bodySmall, color = Muted)
                }
                TextButton(onClick = { picker.launch("image/*") }, enabled = !model.busy) { Text("Pilih foto") }
            }
        }
        if (selection != null || removeLogo || profile.logoName.isNotBlank()) {
            TextButton(onClick = {
                if (selection != null) newLogo = null else removeLogo = !removeLogo
                edited()
            }, enabled = !model.busy) {
                Text(when {
                    selection != null -> "Batalkan pilihan foto"
                    removeLogo -> "Urungkan penghapusan logo"
                    else -> "Hapus logo"
                })
            }
        }
        OutlinedTextField(name, { name = it.take(60); edited() }, Modifier.fillMaxWidth(), label = { Text("Nama toko") },
            enabled = !model.busy, singleLine = true)
        OutlinedTextField(address, { address = it.take(180); edited() }, Modifier.fillMaxWidth(), label = { Text("Alamat toko (opsional)") },
            enabled = !model.busy, minLines = 2)
        ErrorText(model.error)
        if (saved) Text("Pengaturan tersimpan.", color = Pine, fontWeight = FontWeight.SemiBold)
        Button(
            onClick = {
                model.saveProfile(name.trim(), address.trim(), newLogo, removeLogo) {
                    saved = true
                    name = model.profile.name
                    address = model.profile.address
                    newLogo = null
                    removeLogo = false
                }
            },
            enabled = name.isNotBlank() && !model.busy && (selection == null || image != null),
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
        ) { Text("Simpan profil toko") }
        HorizontalDivider(color = Line)
        ExtraChargeSettings(model)
        HorizontalDivider(color = Line)
        PrinterSettings(model)
        HorizontalDivider(color = Line)
        EmptyPanel("Akses pemilik", "Satu PIN untuk mengunci akses kasir di perangkat ini.")
        val email = model.linkedOwnerEmail
        if (email == null) {
            Text("Mode tamu · Data hanya disimpan di perangkat ini", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        if (model.emailAvailable || email != null) {
            if (email != null) {
                Text("Email pemilik terhubung: ${maskedOwnerEmail(email)}", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onLinkOwner, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (email == null) "Hubungkan email pemilik" else "Kelola email pemilik")
            }
        }
        CloudBackupSettings(model)
        OutlinedButton(onClick = model::lock, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Kunci aplikasi") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ExtraChargeSettings(model: RegisterModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var percentMode by rememberSaveable { mutableStateOf(true) }
    var value by rememberSaveable { mutableStateOf("") }
    val charges = model.extraCharges
    val percent = if (percentMode) parsePercent(value) else null
    val amount = if (percentMode) null else parseRupiah(value, maxDigits = 9)?.takeIf { it > 0 }
    val valid = name.isNotBlank() && (percent != null || amount != null) && charges.none { it.name.equals(name.trim(), true) }
    EmptyPanel("Biaya tambahan", "PPN atau biaya layanan ditambahkan ke total transaksi baru saat diaktifkan.")
    charges.forEachIndexed { index, charge ->
        Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(charge.name, fontWeight = FontWeight.Bold)
                    Text(if (charge.percent > 0) "${percentLabel(charge.percent)} dari subtotal" else "${rupiah(charge.amount)} per transaksi",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = charge.enabled, enabled = !model.busy, onCheckedChange = { on ->
                    model.saveExtraCharges(charges.toMutableList().also { it[index] = charge.copy(enabled = on) })
                }, modifier = Modifier.semantics { contentDescription = "Aktifkan ${charge.name}" })
                TextButton(onClick = { model.saveExtraCharges(charges.filterIndexed { i, _ -> i != index }) }, enabled = !model.busy) {
                    Text("Hapus")
                }
            }
        }
    }
    OutlinedTextField(name, { name = cleanOptionText(it, 30) }, Modifier.fillMaxWidth(), label = { Text("Nama biaya") },
        placeholder = { Text("Mis. PPN atau Biaya layanan") }, enabled = !model.busy, singleLine = true)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = percentMode, onClick = { percentMode = true; value = "" }, label = { Text("Persen") })
        FilterChip(selected = !percentMode, onClick = { percentMode = false; value = "" }, label = { Text("Nominal Rp") })
    }
    OutlinedTextField(value, { value = it }, Modifier.fillMaxWidth(), label = { Text(if (percentMode) "Besaran persen" else "Nominal per transaksi") },
        prefix = { if (!percentMode) Text("Rp ") }, suffix = { if (percentMode) Text("%") }, enabled = !model.busy, singleLine = true,
        isError = value.isNotBlank() && percent == null && amount == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
    OutlinedButton(
        onClick = {
            model.saveExtraCharges(charges + ExtraCharge(name.trim(), percent ?: 0, amount ?: 0)) { name = ""; value = "" }
        },
        enabled = valid && !model.busy, modifier = Modifier.fillMaxWidth()
    ) { Text("Tambah biaya") }
}
