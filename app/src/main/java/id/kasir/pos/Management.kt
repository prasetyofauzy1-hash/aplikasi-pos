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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
internal fun ProductsScreen(products: List<Product>, onEdit: (Long) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val shown = products.filter { it.name.contains(filter.trim(), true) || it.category.contains(filter.trim(), true) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Header("02  /  INVENTARIS", "Produk", "Atur harga, kategori, dan stok barang.")
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${products.size} produk", color = Pine, fontWeight = FontWeight.Bold)
                    Text("${products.count { it.stock in 0..5 }} stok menipis", color = Muted, style = MaterialTheme.typography.labelMedium)
                }
                Button(onClick = { onEdit(0L) }) {
                    Icon(Icons.Default.Add, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Tambah produk")
                }
            }
        }
        item {
            OutlinedTextField(filter, { filter = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Cari produk") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = Shape, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line,
                    unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
        }
        if (shown.isEmpty()) item { EmptyPanel("Belum ada hasil", "Tambahkan produk atau ubah pencarian.") }
        items(shown, key = { it.id }) { product ->
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
                        Text("Stok ${product.stock}", color = if (product.stock <= 5) Color(0xFF9C5007) else Muted,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProductForm(product: Product, model: RegisterModel, onClose: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(product.name) }
    var category by rememberSaveable { mutableStateOf(product.category) }
    var price by rememberSaveable { mutableStateOf(if (product.id == 0L) "" else product.price.toString()) }
    var stock by rememberSaveable { mutableStateOf(if (product.id == 0L) "" else product.stock.toString()) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val numericPrice = parseRupiah(price, maxDigits = 9)
    val numericStock = stock.toIntOrNull()
    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = 20.dp).verticalContent(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Header("INVENTARIS", if (product.id == 0L) "Produk baru" else "Edit produk", "Lengkapi informasi yang digunakan di kasir.")
        OutlinedTextField(name, { name = it.take(70) }, Modifier.fillMaxWidth(), label = { Text("Nama produk") }, singleLine = true)
        OutlinedTextField(category, { category = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Kategori") }, singleLine = true)
        OutlinedTextField(price, { price = it }, Modifier.fillMaxWidth(),
            label = { Text("Harga jual") },
            prefix = { if (!price.trimStart().startsWith("Rp", ignoreCase = true)) Text("Rp ") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (price.isNotBlank() && numericPrice == null) ErrorText("Gunakan format Rupiah hingga 9 digit, misalnya Rp19.000,00.")
        OutlinedTextField(stock, { stock = it.filter { c -> c in '0'..'9' }.take(5) }, Modifier.fillMaxWidth(),
            label = { Text("Stok tersedia") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        ErrorText(model.error)
        Button(
            onClick = {
                if (numericPrice != null && numericStock != null) {
                    model.saveProduct(product.copy(name = name.trim(), category = category.trim(), price = numericPrice, stock = numericStock), onClose)
                }
            },
            enabled = !model.busy && name.isNotBlank() && category.isNotBlank() && numericPrice != null && numericStock != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
        ) { Text("Simpan produk") }
        if (product.id != 0L) TextButton(onClick = { confirming = true }, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
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
private fun rememberReportClock(): State<Pair<Long, TimeZone>> {
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
private fun ReportDateControls(date: Long, today: Long, onSelect: (Long) -> Unit) {
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
            ErrorText(model.error)
        }
        if (report.sales.isEmpty()) {
            item { EmptyPanel("Tidak ada transaksi", "Tidak ada penjualan pada tanggal ini.") }
        } else if (showProducts) {
            items(report.products, key = { it.name }) { product ->
                Surface(shape = Shape, color = Color.White, border = BorderStroke(1.dp, Line)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(product.name, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        Column(horizontalAlignment = Alignment.End) {
                            Text(rupiah(product.revenue), color = Pine, fontWeight = FontWeight.Bold)
                            Text("${product.quantity} terjual", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        } else items(report.sales, key = { it.id }) { sale ->
            Surface(shape = Shape, color = Color.White, border = BorderStroke(1.dp, Line)) {
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
            }
        }
    }
}

@Composable
internal fun ReceiptScreen(receipt: Receipt, fromCheckout: Boolean, onDone: () -> Unit) {
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
                }
                HorizontalDivider(color = Line)
                receipt.lines.forEach { line ->
                    MetricRow("${line.quantity} × ${line.productName}", rupiah(line.quantity * line.unitPrice))
                }
                HorizontalDivider(color = Line)
                MetricRow("Total", rupiah(sale.total), strong = true)
                MetricRow(sale.method, rupiah(sale.paid))
                if (sale.method == Payment.CASH.label) MetricRow("Kembalian", rupiah(sale.paid - sale.total))
                Text("Terima kasih sudah berbelanja!", modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
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
                    Result.success(withContext(Dispatchers.IO) { decodeStoreLogo(context, selection) })
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
        EmptyPanel("Akses pemilik", "Satu PIN untuk mengunci akses kasir di perangkat ini.")
        val email = model.linkedOwnerEmail
        if (model.emailAvailable || email != null) {
            if (email != null) {
                Text("Email pemilik terhubung: ${maskedOwnerEmail(email)}", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick = onLinkOwner, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (email == null) "Hubungkan email pemilik" else "Kelola email pemilik")
            }
        }
        OutlinedButton(onClick = model::lock, enabled = !model.busy, modifier = Modifier.fillMaxWidth()) { Text("Kunci aplikasi") }
        Spacer(Modifier.height(8.dp))
    }
}
