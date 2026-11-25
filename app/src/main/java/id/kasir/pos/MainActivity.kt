package id.kasir.pos

import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
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
    private val model by lazy { ViewModelProvider(this)[RegisterModel::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(false)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Pine, onPrimary = Color.White, primaryContainer = Lime, onPrimaryContainer = Ink,
                secondary = Pine, secondaryContainer = SoftGreen, onSecondaryContainer = Ink,
                background = Paper, onBackground = Ink, surface = Color.White, onSurface = Ink,
                surfaceVariant = SoftGreen, onSurfaceVariant = Muted, outlineVariant = Line, outline = Muted,
                surfaceContainer = Paper
            )) {
                Surface(Modifier.fillMaxSize(), color = Paper) {
                    when {
                        model.unlocked -> RegisterApp(model, window)
                        model.needsOwnerOnboarding -> OwnerEmailLink(model, onboarding = true)
                        else -> UnlockScreen(model)
                    }
                }
            }
        }
    }

    override fun onPause() {
        model.lockProfit()
        super.onPause()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        // Android Overview can show the live activity without pausing it.
        if (!isTopResumedActivity) model.lockProfit()
    }
}

private enum class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    REGISTER("Kasir", Icons.Default.ShoppingCart),
    PRODUCTS("Produk", Icons.Default.List),
    HISTORY("Riwayat", Icons.Default.DateRange),
    PROFIT("Laba", Icons.Default.Star),
    SETTINGS("Toko", Icons.Default.Settings)
}

@Composable
private fun RegisterApp(model: RegisterModel, window: Window) {
    val menu by model.menu.collectAsStateWithLifecycle()
    val ingredients by model.ingredients.collectAsStateWithLifecycle()
    val cart by model.cart.collectAsStateWithLifecycle()
    val latestShift by model.latestShift.collectAsStateWithLifecycle()
    val shift = latestShift?.firstOrNull()
    val products = remember(menu, cart) { availableCatalog(menu, cartQuantities(cart)) }
    var tab by rememberSaveable { mutableStateOf(if (model.firstUnlock) Tab.SETTINGS else Tab.REGISTER) }
    DisposableEffect(tab, window) {
        val protectWindow = tab == Tab.PROFIT
        if (protectWindow) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (protectWindow) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    var showEmailLink by rememberSaveable { mutableStateOf(false) }
    var orderScreen by rememberSaveable { mutableStateOf("") }
    var cashierQuery by rememberSaveable { mutableStateOf("") }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedId = editingId
    var showIngredients by rememberSaveable { mutableStateOf(false) }
    var editingIngredient by rememberSaveable { mutableStateOf<Long?>(null) }
    var stockOpnameScreen by rememberSaveable { mutableStateOf("") }
    val tabState = rememberSaveableStateHolder()
    val receipt = model.receipt
    val closeSubScreen: () -> Unit = {
        if (!model.busy) {
            when {
                receipt != null -> model.closeReceipt()
                selectedId != null -> editingId = null
                editingIngredient != null -> editingIngredient = null
                stockOpnameScreen.isNotEmpty() -> stockOpnameScreen = ""
                showIngredients -> showIngredients = false
                orderScreen == "payment" -> orderScreen = "cart"
                else -> orderScreen = ""
            }
            model.clearError()
        }
    }
    val main = receipt == null && orderScreen.isEmpty() && selectedId == null && !showIngredients
    BackHandler(enabled = !main, onBack = closeSubScreen)
    val selectTab: (Tab) -> Unit = { selected ->
        if (!model.busy) {
            if (tab == Tab.PROFIT && selected != Tab.PROFIT) model.lockProfit()
            tab = selected
            model.clearError()
        }
    }
    BackHandler(enabled = main && tab == Tab.PROFIT) { selectTab(Tab.REGISTER) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val short = maxHeight < 400.dp
        val narrow = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Row(Modifier.fillMaxSize()) {
            if (main && short) CompactNavigation(tab, selectTab)
            RegisterScaffold(
                modifier = Modifier.weight(1f),
                model = model,
                tab = tab,
                selectTab = selectTab,
                main = main,
                short = short,
                narrow = narrow,
                imeVisible = imeVisible,
                title = when {
                    receipt != null -> "Struk transaksi"
                    selectedId != null -> if (selectedId == 0L) "Tambah produk" else "Edit produk"
                    editingIngredient != null -> if (editingIngredient == 0L) "Tambah bahan" else "Edit bahan"
                    stockOpnameScreen == "count" -> "Stok opname"
                    stockOpnameScreen == "history" -> "Riwayat opname"
                    showIngredients -> "Stok bahan"
                    orderScreen == "payment" -> "Pembayaran"
                    else -> "Keranjang"
                },
                closeSubScreen = closeSubScreen
            ) {
                when {
                    receipt != null -> ReceiptScreen(receipt, model, fromCheckout = tab == Tab.REGISTER, onDone = closeSubScreen)
                    selectedId != null -> {
                        val item = if (selectedId == 0L) MenuItem(Product(name = "", category = "", price = 0, stock = 0), emptyList())
                            else menu.firstOrNull { it.product.id == selectedId }
                        if (item != null) key(selectedId) {
                            ProductForm(item, ingredients, model, onClose = { editingId = null })
                        } else {
                            Box(Modifier.padding(20.dp)) {
                                EmptyPanel("Produk tidak tersedia", "Kembali ke daftar dan buka produk lagi.")
                            }
                        }
                    }
                    editingIngredient != null -> {
                        val ingredient = if (editingIngredient == 0L) Ingredient(name = "", unit = ingredientUnits.first(), stock = 0)
                            else ingredients.firstOrNull { it.id == editingIngredient }
                        if (ingredient != null) key(ingredient.id) {
                            IngredientForm(ingredient, model, onClose = { editingIngredient = null })
                        } else {
                            Box(Modifier.padding(20.dp)) {
                                EmptyPanel("Bahan tidak tersedia", "Kembali ke daftar dan buka bahan lagi.")
                            }
                        }
                    }
                    stockOpnameScreen == "count" -> StockOpnameScreen(ingredients, model, onSaved = { stockOpnameScreen = "history" })
                    stockOpnameScreen == "history" -> {
                        val stockCounts by model.stockCounts.collectAsStateWithLifecycle()
                        StockOpnameHistoryScreen(stockCounts)
                    }
                    showIngredients -> IngredientStockScreen(ingredients, model, onEdit = { editingIngredient = it },
                        onStockOpname = { model.clearError(); stockOpnameScreen = "count" },
                        onStockOpnameHistory = { stockOpnameScreen = "history" })
                    orderScreen == "cart" -> CartScreen(
                        products, cart, model, short, shiftOpen = shift?.isOpen == true,
                        onPay = { orderScreen = "payment" }
                    )
                    orderScreen == "payment" -> PaymentScreen(products, cart, model, onPaid = {
                        cashierQuery = ""
                        orderScreen = ""
                    })
                    tab == Tab.REGISTER -> CashierScreen(
                        products, cart, model, cashierQuery, onQueryChange = { cashierQuery = it },
                        compact = short || narrow, searching = imeVisible, shiftLoaded = latestShift != null,
                        shift = shift, onCart = { orderScreen = "cart" }
                    )
                    tab == Tab.PRODUCTS -> tabState.SaveableStateProvider(Tab.PRODUCTS.name) {
                        ProductsScreen(menu, onEdit = { editingId = it }, onIngredients = { showIngredients = true })
                    }
                    tab == Tab.HISTORY -> tabState.SaveableStateProvider(Tab.HISTORY.name) { HistoryScreen(model) }
                    tab == Tab.PROFIT -> if (model.profitUnlocked) ProfitScreen(menu, ingredients, model) else ProfitGate(model)
                    tab == Tab.SETTINGS -> SettingsScreen(model, onLinkOwner = {
                        model.clearError()
                        showEmailLink = true
                    })
                }
            }
        }
    }
    if (showEmailLink) {
        OwnerEmailLink(model) {
            model.clearError()
            showEmailLink = false
        }
    }
}

@Composable
private fun RegisterScaffold(
    modifier: Modifier,
    model: RegisterModel,
    tab: Tab,
    selectTab: (Tab) -> Unit,
    main: Boolean,
    short: Boolean,
    narrow: Boolean,
    imeVisible: Boolean,
    title: String,
    closeSubScreen: () -> Unit,
    content: @Composable () -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = Paper,
        topBar = {
            if (main && !short) BrandBar(model.profile, narrow)
            if (!main) SubBar(title, short, closeSubScreen)
        },
        bottomBar = {
            if (main && !short && !imeVisible) NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { selectTab(entry) },
                        icon = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(21.dp)) },
                        label = { BottomTabLabel(entry, narrow) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            content()
        }
    }
}

@Composable
private fun BottomTabLabel(tab: Tab, narrow: Boolean) {
    val style = (if (narrow) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium)
        .copy(fontWeight = FontWeight.SemiBold)
    if (LocalDensity.current.fontScale <= 1f) {
        Text(tab.label, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        BoxWithConstraints {
            val measurer = rememberTextMeasurer()
            val availableWidth = constraints.maxWidth
            val fontSize = remember(measurer, style, availableWidth) {
                generateSequence(style.fontSize.value) { (it - 0.5f).takeIf { size -> size >= 6f } }
                    .map { it.sp }
                    .firstOrNull { size ->
                        Tab.entries.all {
                            measurer.measure(it.label, style.copy(fontSize = size), maxLines = 1).size.width <= availableWidth
                        }
                    } ?: 6.sp
            }
            Text(tab.label, style = style, fontSize = fontSize, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CompactNavigation(tab: Tab, selectTab: (Tab) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(72.dp).background(Color.White)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Tab.entries.forEach { entry ->
            NavigationRailItem(selected = tab == entry, onClick = { selectTab(entry) },
                icon = { Icon(entry.icon, contentDescription = entry.label) })
        }
    }
}

@Composable
private fun BrandBar(profile: StoreProfile, narrow: Boolean) {
    Surface(color = Paper) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(
                horizontal = if (narrow) 12.dp else 20.dp, vertical = if (narrow) 6.dp else 12.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StoreLogo(profile.logoName, Modifier.size(if (narrow) 32.dp else 40.dp).clip(RoundedCornerShape(13.dp)))
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(stringResource(R.string.app_name), color = Ink, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(profile.name, color = Muted, style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.clip(RoundedCornerShape(50)).background(SoftGreen).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text(if (narrow) "Offline" else "●  Offline", color = Pine, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SubBar(title: String, short: Boolean, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = if (short) 0.dp else 8.dp),
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
                Text(stringResource(R.string.app_name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 39.sp, lineHeight = 45.sp)
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
internal fun PinInput(
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
