package id.kasir.pos

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

internal val Paper = Color(0xFFF7F8F2)
internal val Pine = Color(0xFF144D40)
internal val Lime = Color(0xFFDCFA87)
internal val Ink = Color(0xFF172C24)
internal val Muted = Color(0xFF52665E)
internal val Line = Color(0xFFE0E8DD)
internal val SoftGreen = Color(0xFFE5EFE4)
internal val Shape = RoundedCornerShape(20.dp)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[RegisterModel::class.java]
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Pine, onPrimary = Color.White, primaryContainer = Lime, onPrimaryContainer = Ink,
                secondary = Pine, secondaryContainer = SoftGreen, onSecondaryContainer = Ink,
                background = Paper, onBackground = Ink, surface = Color.White, onSurface = Ink,
                surfaceVariant = SoftGreen, onSurfaceVariant = Muted, outlineVariant = Line, outline = Muted,
                surfaceContainer = Paper
            )) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    if (model.unlocked) RegisterApp(model) else UnlockScreen(model)
                }
            }
        }
    }
}

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    REGISTER("Kasir", Icons.Default.ShoppingCart),
    PRODUCTS("Produk", Icons.Default.List),
    HISTORY("Riwayat", Icons.Default.DateRange),
    SETTINGS("Toko", Icons.Default.Settings)
}

@Composable
private fun RegisterApp(model: RegisterModel) {
    val products by model.products.collectAsStateWithLifecycle()
    val sales by model.sales.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(if (model.firstUnlock) Tab.SETTINGS else Tab.REGISTER) }
    var showEmailLink by rememberSaveable {
        mutableStateOf(model.firstUnlock && model.emailAvailable && model.linkedOwnerEmail == null)
    }
    var orderScreen by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedId = editingId
    val receipt = model.receipt
    val closeSubScreen: () -> Unit = {
        if (!model.busy) {
            when {
                receipt != null -> model.closeReceipt()
                selectedId != null -> editingId = null
                orderScreen == "payment" -> orderScreen = "cart"
                else -> orderScreen = ""
            }
            model.clearError()
        }
    }
    val main = receipt == null && orderScreen.isEmpty() && selectedId == null
    BackHandler(enabled = !main, onBack = closeSubScreen)
    Scaffold(
        containerColor = Paper,
        topBar = {
            if (main) BrandBar(model.profile)
            else SubBar(when {
                receipt != null -> "Struk transaksi"
                selectedId != null -> if (selectedId == 0L) "Tambah produk" else "Edit produk"
                orderScreen == "payment" -> "Pembayaran"
                else -> "Keranjang"
            }, closeSubScreen)
        },
        bottomBar = {
            if (main) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { if (!model.busy) { tab = entry; model.clearError() } },
                        icon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(21.dp)) },
                        label = { Text(entry.label, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            when {
                receipt != null -> ReceiptScreen(receipt, closeSubScreen)
                selectedId != null -> {
                    val product = if (selectedId == 0L) Product(name = "", category = "", price = 0, stock = 0)
                        else products.firstOrNull { it.id == selectedId }
                    if (product != null) key(selectedId) {
                        ProductForm(product, model, onClose = { editingId = null })
                    } else {
                        Box(Modifier.padding(20.dp)) {
                            EmptyPanel("Produk tidak tersedia", "Kembali ke daftar dan buka produk lagi.")
                        }
                    }
                }
                orderScreen == "cart" -> CartScreen(products, model, onPay = { orderScreen = "payment" })
                orderScreen == "payment" -> PaymentScreen(products, model, onPaid = { orderScreen = "" })
                tab == Tab.REGISTER -> CashierScreen(products, model, onCart = { orderScreen = "cart" })
                tab == Tab.PRODUCTS -> ProductsScreen(products, onEdit = { editingId = it })
                tab == Tab.HISTORY -> HistoryScreen(sales, model)
                tab == Tab.SETTINGS -> SettingsScreen(model, onLinkOwner = {
                    model.clearError()
                    showEmailLink = true
                })
            }
        }
    }
    if (showEmailLink) {
        LinkOwnerEmailDialog(model, skippable = model.firstUnlock) {
            model.clearError()
            showEmailLink = false
        }
    }
}

@Composable
private fun BrandBar(profile: StoreProfile) {
    Surface(color = Paper) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoreLogo(profile.logoName, Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text("Kasir Rapi", color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(profile.name, color = Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
            Box(Modifier.clip(RoundedCornerShape(50)).background(SoftGreen).padding(horizontal = 10.dp, vertical = 6.dp)) {
                Text("●  Offline", color = Pine, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SubBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) { Text("‹  Kembali") }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun StoreLogo(logoName: String, modifier: Modifier) {
    val context = LocalContext.current
    val image = remember(logoName, context) {
        if (logoName.isBlank()) null else BitmapFactory.decodeFile(File(context.filesDir, logoName).path)?.asImageBitmap()
    }
    if (image != null) Image(image, contentDescription = "Logo toko", modifier, contentScale = ContentScale.Fit)
    else Image(painterResource(R.drawable.ic_kasir), contentDescription = null, modifier)
}

@Composable
private fun UnlockScreen(model: RegisterModel) {
    val resetting = model.resettingPin
    val setup = !model.pinConfigured || resetting
    var pin by remember(resetting) { mutableStateOf("") }
    var confirmation by remember(resetting) { mutableStateOf("") }
    var showRecovery by rememberSaveable { mutableStateOf(false) }
    val canSubmit = !model.busy && pin.length == 6 && (!setup || confirmation.length == 6)
    val focus = LocalFocusManager.current
    val submit: () -> Unit = {
        if (canSubmit) {
            focus.clearFocus()
            model.authenticate(pin, confirmation) {
                if (setup) confirmation = "" else pin = ""
            }
        }
    }
    BackHandler(enabled = resetting, onBack = model::cancelPinReset)
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Surface(shape = Shape, color = Pine) {
            Column(Modifier.fillMaxWidth().padding(23.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StoreLogo("", Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)))
                Text("KASIR UNTUK TOKO ANDA", color = Lime, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("Kasir Rapi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 39.sp, lineHeight = 45.sp)
                Text("Kelola penjualan dan stok, bahkan tanpa internet.", color = Color(0xFFD4E8DF))
            }
        }
        Surface(shape = Shape, color = Color.White) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when {
                    resetting -> "Buat PIN baru"
                    setup -> "Buat PIN pemilik"
                    else -> "Masuk sebagai pemilik"
                }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(when {
                    resetting -> "PIN baru menggantikan PIN lama. Data toko tetap tersimpan."
                    setup -> "Pilih 6 angka yang mudah Anda ingat. PIN ini diperlukan setiap kali membuka kasir."
                    else -> "Masukkan 6 angka PIN untuk membuka kasir."
                }, color = Muted)
                PinInput(pin, if (resetting) "PIN baru" else "PIN pemilik", !model.busy,
                    isError = !setup && model.error != null, imeAction = if (setup) ImeAction.Next else ImeAction.Done,
                    keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }, onDone = { submit() })
                ) { pin = it; model.clearError() }
                if (setup) PinInput(confirmation, "Konfirmasi PIN", !model.busy, isError = model.error != null,
                    imeAction = ImeAction.Done, keyboardActions = KeyboardActions(onDone = { submit() })
                ) { confirmation = it; model.clearError() }
                ErrorText(model.error)
                Button(
                    onClick = submit,
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)
                ) { Text(if (resetting) "Simpan PIN baru" else if (setup) "Buat PIN & mulai" else "Buka kasir") }
                if (resetting) {
                    TextButton(onClick = model::cancelPinReset, enabled = !model.busy) { Text("Batal") }
                } else if (!setup) {
                    TextButton(onClick = {
                        model.clearError()
                        showRecovery = true
                    }, enabled = !model.busy) { Text("Lupa PIN?") }
                }
            }
        }
        Text("DATA DISIMPAN DI PERANGKAT INI", modifier = Modifier.align(Alignment.CenterHorizontally),
            color = Muted, letterSpacing = 1.sp, style = MaterialTheme.typography.labelSmall)
    }
    if (showRecovery) {
        RecoverOwnerPinDialog(model) {
            model.clearError()
            showRecovery = false
        }
    }
}

@Composable
private fun PinInput(
    value: String, label: String, enabled: Boolean, isError: Boolean, imeAction: ImeAction,
    keyboardActions: KeyboardActions, change: (String) -> Unit
) {
    OutlinedTextField(value, { change(it.filter { c -> c in '0'..'9' }.take(6)) }, Modifier.fillMaxWidth(),
        enabled = enabled, isError = isError,
        label = { Text(label) }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = imeAction),
        keyboardActions = keyboardActions)
}

@Composable
internal fun ErrorText(message: String?) {
    if (message != null) Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun Header(kicker: String, title: String, description: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(kicker, color = Pine, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(title, color = Ink, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 37.sp)
        Text(description, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
internal fun ShareReceiptButton(receipt: Receipt) {
    val context = LocalContext.current
    Button(modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), onClick = {
        val share = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, receipt.asText())
        }
        context.startActivity(Intent.createChooser(share, "Bagikan struk"))
    }) { Text("Bagikan struk") }
}
