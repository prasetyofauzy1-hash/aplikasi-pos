package id.kasir.pos

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class RegisterModel(app: Application) : AndroidViewModel(app) {
    private val preferences = app.getSharedPreferences("kasir", Context.MODE_PRIVATE)
    private val database = PosDatabase.open(app)
    private val dao = database.dao()
    private val register = Register(database)
    private val ownerAuth = BuildConfig.FIREBASE_API_KEY.takeIf { it.isNotBlank() }?.let { OwnerAuth(it) }
    private val cloudBackup = BuildConfig.FIREBASE_BACKUP_URL.takeIf {
        ownerAuth != null && it.matches(Regex("https://[a-z0-9.-]+\\.(firebaseio\\.com|firebasedatabase\\.app)/?"))
    }?.let(::FirebaseBackup)

    val menu = dao.menu().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ingredients = dao.ingredients().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    internal val cashiers = dao.cashiers().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val stockCounts = dao.stockCounts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val cart = dao.cart().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    internal fun dailyReceipts(period: ReportPeriod) = dao.dailyReceipts(period.start, period.end)

    var pinConfigured by mutableStateOf(preferences.contains("pinHash"))
        private set
    var unlocked by mutableStateOf(false)
        private set
    internal var profitUnlocked by mutableStateOf(false)
        private set
    internal var profitAccessGeneration by mutableLongStateOf(0L)
        private set
    var firstUnlock by mutableStateOf(false)
        private set
    var initialOwnerEmailOffer by mutableStateOf(false)
        private set
    var linkedOwnerEmail by mutableStateOf(preferences.getString("ownerEmail", null))
        private set
    var resettingPin by mutableStateOf(false)
        private set
    val emailAvailable get() = ownerAuth != null
    internal val cloudBackupAvailable get() = cloudBackup != null
    private val legacyShop = BackupProfile(
        StoreProfile(
            preferences.getString("name", null) ?: "Toko Saya",
            preferences.getString("address", null).orEmpty(),
            preferences.getString("logo", null).orEmpty()
        ),
        preferences.getString("qrisImage", null).orEmpty(),
        chargesFromJson(preferences.getString("extraCharges", null))
    )
    private var shop by mutableStateOf(legacyShop)
    val profile get() = shop.store
    val qrisImageName get() = shop.qris
    val extraCharges get() = shop.extraCharges
    var receipt by mutableStateOf<Receipt?>(null)
        private set
    internal var printer by mutableStateOf(preferences.getString("printerAddress", null)?.let { address ->
        PairedPrinter(address, preferences.getString("printerName", null) ?: "Perangkat Bluetooth")
    })
        private set
    internal var thermalPaper by mutableStateOf(
        ThermalPaper.entries.firstOrNull { it.name == preferences.getString("printerPaper", null) } ?: ThermalPaper.MM58
    )
        private set
    internal var printing by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    internal var activeCashierId by mutableStateOf(preferences.getLong("activeCashierId", OWNER_CASHIER_ID))
        private set

    init {
        viewModelScope.launch {
            dao.storeSettings().collect { settings -> shop = settings?.backupProfile() ?: legacyShop }
        }
    }

    fun clearError() { error = null }

    fun authenticate(pin: String, confirmation: String, onRejected: () -> Unit) {
        if (busy) return
        val needsNewPin = !pinConfigured || resettingPin
        if (pin.length != 6 || !pin.all { it in '0'..'9' }) {
            error = "Masukkan PIN enam angka."
            return
        }
        if (needsNewPin && pin != confirmation) {
            error = "Konfirmasi PIN tidak sama."
            onRejected()
            return
        }
        runAction("PIN tidak dapat diperiksa.") {
            val valid = try {
                withContext(Dispatchers.IO) {
                    if (needsNewPin) {
                        savePin(pin)
                        true
                    } else {
                        verifyOwnerPin(pin)
                    }
                }
            } catch (e: SaleInputException) {
                onRejected()
                throw e
            }
            if (valid) {
                firstUnlock = !pinConfigured
                initialOwnerEmailOffer = firstUnlock && emailAvailable && linkedOwnerEmail == null
                pinConfigured = true
                resettingPin = false
                unlocked = true
            } else {
                error = "PIN salah. Coba lagi."
                onRejected()
            }
        }
    }

    private fun savePin(pin: String) {
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        if (!preferences.edit()
                .putString("pinSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString("pinHash", Base64.encodeToString(derivePin(pin, salt), Base64.NO_WRAP))
                .remove("pinFailures").remove("pinUntilWall").remove("pinUntilElapsed").remove("pinBoot")
                .commit()) throw IOException()
    }

    fun dismissInitialOwnerEmailOffer() {
        initialOwnerEmailOffer = false
    }

    fun linkOwnerEmail(
        pin: String, email: String, confirmation: String, password: String, createAccount: Boolean, onDone: () -> Unit
    ) {
        val auth = ownerAuth ?: return
        if (!unlocked || busy) return
        if (!ownerEmailsMatch(email, confirmation)) {
            error = "Konfirmasi email tidak sama."
            return
        }
        val requiresPin = !initialOwnerEmailOffer || linkedOwnerEmail != null
        runAction("Tidak dapat terhubung. Periksa koneksi internet, lalu coba lagi.") {
            linkedOwnerEmail = withContext(Dispatchers.IO) {
                if (requiresPin) requireOwnerPin(pin)
                val account = auth.authenticate(email.trim(), password, createAccount)
                if (!preferences.edit().putString("ownerEmail", account.email)
                        .putString("ownerUid", account.uid).commit()) throw IOException()
                account.email
            }
            dismissInitialOwnerEmailOffer()
            onDone()
        }
    }

    fun unlinkOwnerEmail(pin: String, onDone: () -> Unit) {
        if (!unlocked || linkedOwnerEmail == null) return
        runAction("Tautan email tidak dapat dihapus. Coba lagi.") {
            withContext(Dispatchers.IO) {
                requireOwnerPin(pin)
                if (!preferences.edit().remove("ownerEmail").remove("ownerUid").commit()) throw IOException()
            }
            linkedOwnerEmail = null
            onDone()
        }
    }

    private fun requireOwnerPin(pin: String) {
        if (pin.length != 6 || !pin.all { it in '0'..'9' }) throw SaleInputException("Masukkan PIN enam angka.")
        if (!verifyOwnerPin(pin)) throw SaleInputException("PIN salah. Coba lagi.")
    }

    internal fun selectCashier(id: Long) {
        if (!unlocked || busy) return
        setActiveCashier(id)
    }

    private fun setActiveCashier(id: Long) {
        preferences.edit().putLong("activeCashierId", id).apply()
        activeCashierId = id
    }

    internal fun saveCashier(id: Long, name: String, pin: String, onDone: () -> Unit) {
        if (!unlocked) return
        runAction("Profil kasir tidak dapat disimpan.") {
            val normalized = normalizeCashierName(name)
            if (normalized.isEmpty()) throw SaleInputException("Masukkan nama kasir.")
            withContext(Dispatchers.IO) { requireOwnerPin(pin) }
            val cashier = CashierProfile(id, normalized)
            if (id == 0L) dao.addCashier(cashier) else dao.updateCashier(cashier)
            onDone()
        }
    }

    internal fun deleteCashier(id: Long, pin: String, onDone: () -> Unit) {
        if (!unlocked || id == OWNER_CASHIER_ID) return
        runAction("Profil kasir tidak dapat dihapus.") {
            withContext(Dispatchers.IO) { requireOwnerPin(pin) }
            dao.deleteCashier(id)
            if (activeCashierId == id) setActiveCashier(OWNER_CASHIER_ID)
            onDone()
        }
    }

    internal fun runCloudBackup(
        restore: Boolean, pin: String, email: String, password: String, onDone: () -> Unit
    ) {
        val auth = ownerAuth ?: return
        val cloud = cloudBackup ?: return
        if (!unlocked || linkedOwnerEmail == null) return
        val fallback = if (restore) "Cadangan tidak dapat dipulihkan. Periksa koneksi atau coba lagi."
            else "Cadangan tidak dapat dibuat. Periksa koneksi atau coba lagi."
        runAction(fallback) {
            val restored = withContext(Dispatchers.IO) {
                requireOwnerPin(pin)
                val session = auth.session(requireRecoveryEmail(email), password)
                if (!session.account.matches(preferences.getString("ownerUid", null))) {
                    throw OwnerAuthException("Akun ini bukan akun pemilik yang terhubung di perangkat ini.")
                }
                val context = getApplication<Application>()
                val archive = BackupArchive(context, database)
                val file = File.createTempFile("cloud-backup-", ".zip", context.cacheDir)
                try {
                    if (restore) {
                        cloud.download(session, file)
                        archive.restore(file)
                    } else {
                        archive.create(file, currentShop())
                        cloud.upload(session, file)
                        null
                    }
                } finally {
                    file.delete()
                }
            }
            if (restored != null) {
                shop = restored
                setActiveCashier(OWNER_CASHIER_ID)
                receipt = null
            }
            onDone()
        }
    }

    private suspend fun currentShop() = dao.storeSettingsSnapshot()?.backupProfile() ?: legacyShop

    internal fun unlockProfit(pin: String) {
        if (!unlocked || profitUnlocked) return
        val request = profitAccessGeneration
        runAction("PIN tidak dapat diperiksa.") {
            withContext(Dispatchers.IO) { requireOwnerPin(pin) }
            if (unlocked && request == profitAccessGeneration) profitUnlocked = true
        }
    }

    internal fun lockProfit() {
        profitAccessGeneration++
        profitUnlocked = false
    }

    fun verifyOwnerForPinReset(enteredEmail: String, password: String, onDone: () -> Unit) {
        val auth = ownerAuth ?: return
        if (unlocked || !pinConfigured || resettingPin) return
        runAction("Tidak dapat terhubung. Periksa koneksi internet, lalu coba lagi.") {
            val email = requireRecoveryEmail(enteredEmail)
            val account = withContext(Dispatchers.IO) { auth.authenticate(email, password, createAccount = false) }
            if (!account.matches(preferences.getString("ownerUid", null))) {
                throw OwnerAuthException("Akun ini bukan akun pemilik yang terhubung di perangkat ini.")
            }
            resettingPin = true
            onDone()
        }
    }

    fun sendOwnerPasswordReset(enteredEmail: String, onDone: () -> Unit) {
        val auth = ownerAuth ?: return
        if (unlocked || !pinConfigured || resettingPin) return
        runAction("Tidak dapat terhubung. Periksa koneksi internet, lalu coba lagi.") {
            val email = requireRecoveryEmail(enteredEmail)
            withContext(Dispatchers.IO) { auth.sendPasswordReset(email) }
            onDone()
        }
    }

    private fun requireRecoveryEmail(enteredEmail: String): String {
        val email = linkedOwnerEmail
        if (email == null || !ownerEmailsMatch(email, enteredEmail)) {
            throw OwnerAuthException("Email tidak cocok dengan akun pemilik yang terhubung.")
        }
        return email
    }

    fun cancelPinReset() {
        if (busy) return
        resettingPin = false
        clearError()
    }

    fun lock() {
        if (busy) return
        receipt = null
        lockProfit()
        dismissInitialOwnerEmailOffer()
        clearError()
        unlocked = false
    }

    private fun verifyOwnerPin(pin: String): Boolean {
        val boot = Settings.Global.getInt(getApplication<Application>().contentResolver, Settings.Global.BOOT_COUNT, -1)
        val attempts = PinAttempts(
            failures = preferences.getInt("pinFailures", 0),
            untilWall = preferences.getLong("pinUntilWall", 0),
            untilElapsed = preferences.getLong("pinUntilElapsed", 0),
            boot = preferences.getInt("pinBoot", -1)
        )
        fun requireAvailable(state: PinAttempts) {
            val remaining = state.remainingMillis(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot)
            if (remaining > 0) {
                throw SaleInputException("Terlalu banyak percobaan PIN. Coba lagi dalam ${(remaining + 999) / 1000} detik.")
            }
        }

        requireAvailable(attempts)
        val salt = Base64.decode(preferences.getString("pinSalt", "").orEmpty(), Base64.NO_WRAP)
        val saved = Base64.decode(preferences.getString("pinHash", "").orEmpty(), Base64.NO_WRAP)
        val valid = MessageDigest.isEqual(saved, derivePin(pin, salt))
        val next = if (valid) PinAttempts() else attempts.failed(System.currentTimeMillis(), SystemClock.elapsedRealtime(), boot)
        if (!preferences.edit()
                .putInt("pinFailures", next.failures)
                .putLong("pinUntilWall", next.untilWall)
                .putLong("pinUntilElapsed", next.untilElapsed)
                .putInt("pinBoot", next.boot)
                .commit()) throw IOException()
        requireAvailable(next)
        return valid
    }

    private fun derivePin(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    fun addToCart(product: Product, options: List<String> = emptyList(), note: String = "", quantity: Int = 1) {
        val optionText = formatSelectedOptions(options)
        val cleanNote = cleanOptionText(note, 80).trim()
        changeCart(product) { lines ->
            val line = lines.firstOrNull { it.productId == product.id && it.options == optionText && it.note == cleanNote }
            (line ?: CartItem(productId = product.id, quantity = 0, options = optionText, note = cleanNote)) to
                (line?.quantity ?: 0).toLong() + quantity
        }
    }

    fun adjustLine(item: CartItem, delta: Int) = changeCart(null, item.productId) { lines ->
        val line = lines.firstOrNull { it.id == item.id }
        line to (line?.quantity ?: 0).toLong() + delta
    }

    fun setLineQuantity(item: CartItem, quantity: Int) = changeCart(null, item.productId) { lines ->
        lines.firstOrNull { it.id == item.id } to quantity.toLong()
    }

    private fun changeCart(product: Product?, productId: Long = product?.id ?: 0, target: (List<CartItem>) -> Pair<CartItem?, Long>) {
        if (busy) return
        viewModelScope.launch {
            try {
                database.withTransaction {
                    val lines = dao.cartSnapshot()
                    val (line, requested) = target(lines)
                    if (line == null) return@withTransaction
                    val next = requested.coerceAtLeast(0L)
                    if (next > Int.MAX_VALUE) return@withTransaction
                    val previous = cartQuantities(lines)
                    val oldTotal = previous[productId] ?: 0
                    val newTotal = oldTotal.toLong() - line.quantity + next
                    if (next > line.quantity) {
                        val items = dao.menuById(previous.keys + productId)
                        val available = items.firstOrNull { it.product.id == productId }
                            ?.availableStock(ingredientDemand(items, previous), oldTotal) ?: 0
                        if (newTotal > available) return@withTransaction
                    }
                    when {
                        next == 0L -> if (line.id != 0L) dao.removeCartLine(line.id)
                        line.id == 0L -> dao.addCartItem(line.copy(quantity = next.toInt()))
                        else -> dao.setCartQuantity(line.id, next.toInt())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = "Keranjang tidak dapat diperbarui."
            }
        }
    }

    fun saveExtraCharges(charges: List<ExtraCharge>, onDone: () -> Unit = {}) = runAction("Biaya tambahan tidak dapat disimpan.") {
        shop = withContext(Dispatchers.IO) {
            currentShop().copy(extraCharges = charges).also { dao.saveStoreSettings(it.settings()) }
        }
        onDone()
    }

    fun saveProduct(product: Product, recipe: Map<Long, Int>, onDone: () -> Unit) = runAction("Produk tidak dapat disimpan.") {
        database.withTransaction {
            val current = if (product.id == 0L) null else dao.menuById(setOf(product.id)).singleOrNull()
            if (recipe.isEmpty() && (product.id == 0L || current?.recipe?.isNotEmpty() == true)) {
                throw SaleInputException("Tambahkan setidaknya satu bahan ke resep.")
            }
            if (recipe.values.any { it !in 1..MAX_INGREDIENT_STOCK }) {
                throw SaleInputException("Jumlah setiap bahan harus lebih dari 0.")
            }
            val storedProduct = if (recipe.isEmpty()) product.copy(directHpp = current?.product?.directHpp)
                else product.copy(stock = 0, directHpp = null)
            val id = if (product.id == 0L) dao.add(storedProduct) else {
                dao.update(storedProduct)
                product.id
            }
            dao.deleteRecipe(id)
            if (recipe.isNotEmpty()) {
                dao.addRecipe(recipe.map { (ingredientId, quantity) -> RecipeLine(id, ingredientId, quantity) })
            } else {
                var remaining = product.stock
                for (line in dao.cartSnapshot().filter { it.productId == product.id }.sortedBy { it.id }) {
                    val kept = minOf(line.quantity, remaining)
                    remaining -= kept
                    if (kept == 0) dao.removeCartLine(line.id) else if (kept != line.quantity) dao.setCartQuantity(line.id, kept)
                }
            }
        }
        onDone()
    }

    fun saveIngredient(ingredient: Ingredient, onDone: () -> Unit) = runAction("Bahan tidak dapat disimpan.") {
        database.withTransaction {
            if (dao.ingredientNameExists(ingredient.name, ingredient.id)) {
                throw SaleInputException("Nama bahan sudah dipakai.")
            }
            if (ingredient.id == 0L) dao.addIngredient(ingredient)
            else dao.updateIngredient(ingredient.id, ingredient.name, ingredient.stock)
        }
        onDone()
    }

    internal fun saveIngredientCost(id: Long, cost: Long?, quantity: Int?, onDone: () -> Unit) {
        if (!profitUnlocked) return
        runAction("Biaya bahan tidak dapat disimpan.") {
            val valid = (cost == null && quantity == null) ||
                (cost != null && cost in 0..MAX_PURCHASE_COST && quantity != null && quantity in 1..MAX_INGREDIENT_STOCK)
            if (!valid) throw SaleInputException("Isi harga dan jumlah pembelian yang valid.")
            if (dao.setIngredientCost(id, cost, quantity) != 1) throw SaleInputException("Bahan tidak ditemukan.")
            onDone()
        }
    }

    internal fun saveDirectHpp(id: Long, cost: Long?, onDone: () -> Unit) {
        if (!profitUnlocked) return
        runAction("HPP produk tidak dapat disimpan.") {
            if (cost != null && cost !in 0..MAX_PURCHASE_COST) throw SaleInputException("Isi HPP yang valid.")
            if (dao.setDirectHpp(id, cost) != 1) throw SaleInputException("Produk tanpa resep tidak ditemukan.")
            onDone()
        }
    }

    fun deleteIngredient(id: Long, onDone: () -> Unit) = runAction("Bahan tidak dapat dihapus.") {
        database.withTransaction {
            if (dao.ingredientInUse(id)) throw SaleInputException("Bahan masih dipakai dalam resep.")
            dao.deleteIngredient(id)
        }
        onDone()
    }

    fun restockIngredient(id: Long, quantity: Int, onDone: () -> Unit) = runAction("Stok tidak dapat ditambah.") {
        if (quantity !in 1..MAX_INGREDIENT_STOCK || dao.restockIngredient(id, quantity, MAX_INGREDIENT_STOCK) != 1) {
            throw SaleInputException("Periksa jumlah tambahan dan stok saat ini.")
        }
        onDone()
    }

    fun saveStockCount(counted: Map<Long, Int>, onDone: () -> Unit) = runAction("Stok opname tidak dapat disimpan.") {
        database.withTransaction {
            val lines = stockCountLines(dao.ingredientsById(counted.keys), counted)
            val id = dao.addStockCount(StockCount(time = System.currentTimeMillis()))
            dao.addStockCountLines(lines.map { it.copy(countId = id) })
            lines.forEach { dao.setIngredientStock(requireNotNull(it.ingredientId), it.countedStock) }
        }
        onDone()
    }

    fun deleteProduct(product: Product, onDone: () -> Unit) = runAction("Produk tidak dapat dihapus.") {
        dao.delete(product.id)
        onDone()
    }

    fun sell(cart: List<CartItem>, total: Long, payment: Payment, cash: Long?, onDone: () -> Unit) =
        runAction("Transaksi tidak dapat disimpan.") {
            receipt = register.sell(cart, total, payment, cash, profile, activeCashierId, extraCharges)
            onDone()
        }

    fun openReceipt(id: Long) = runAction("Struk tidak dapat dibuka.") {
        receipt = dao.receipt(id) ?: throw SaleInputException("Struk tidak ditemukan.")
    }

    internal fun previewSaleDeletion(sale: Sale, onReady: (SaleDeletion) -> Unit) =
        runAction("Konfirmasi hapus transaksi tidak dapat dibuka.") {
            onReady(register.previewSaleDeletion(sale))
        }

    internal fun deleteSale(deletion: SaleDeletion, pin: String, onDone: () -> Unit) {
        if (!unlocked || !pinConfigured) return
        runAction("Transaksi tidak dapat dihapus.") {
            withContext(Dispatchers.IO) { requireOwnerPin(pin) }
            register.deleteSale(deletion)
            onDone()
        }
    }

    fun closeReceipt() { receipt = null }

    internal fun selectPrinter(device: PairedPrinter) {
        preferences.edit().putString("printerAddress", device.address).putString("printerName", device.name).apply()
        printer = device
    }

    internal fun setThermalPaper(paper: ThermalPaper) {
        preferences.edit().putString("printerPaper", paper.name).apply()
        thermalPaper = paper
    }

    internal fun testPrinter(onResult: (String?) -> Unit) {
        val currentProfile = profile
        print(onResult) { paper -> thermalPrinterTest(currentProfile, paper) }
    }

    internal fun printReceipt(receipt: Receipt, onResult: (String?) -> Unit) =
        print(onResult) { paper -> thermalReceipt(receipt, paper) }

    private fun print(onResult: (String?) -> Unit, document: (ThermalPaper) -> ByteArray) {
        if (printing) return
        val device = printer
        if (device == null) {
            onResult("Pilih printer terlebih dahulu di Toko → Printer.")
            return
        }
        val paper = thermalPaper
        printing = true
        viewModelScope.launch {
            val error = try {
                sendToPrinter(getApplication(), device) { document(paper) }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: PrinterException) {
                e.message ?: "Data cetak tidak dapat dikirim. Coba lagi."
            } catch (_: Exception) {
                "Data cetak tidak dapat dikirim. Coba lagi."
            } finally {
                printing = false
            }
            onResult(error)
        }
    }

    internal fun exportSales(period: SalesExportPeriod, onReady: (Uri) -> Unit) =
        runAction("CSV penjualan tidak dapat dibagikan. Coba lagi.") {
            val receipts = dao.receiptsForExport(period.start, period.endExclusive)
            val uri = withContext(Dispatchers.IO) { writeSalesCsv(getApplication(), period, receipts) }
            onReady(uri)
        }

    fun saveProfile(name: String, address: String, newLogo: Uri?, removeLogo: Boolean, onDone: () -> Unit) =
        runAction("Pengaturan tidak dapat disimpan. Periksa kembali file logo.") {
            shop = withContext(Dispatchers.IO) {
                val current = currentShop()
                val logo = when {
                    newLogo != null -> writeLogo(getApplication(), newLogo)
                    removeLogo -> ""
                    else -> current.store.logoName
                }
                current.copy(store = StoreProfile(name, address, logo)).also {
                    dao.saveStoreSettings(it.settings())
                }
            }
            onDone()
        }

    fun saveQrisImage(uri: Uri?) = runAction("Gambar QRIS tidak dapat disimpan. Periksa kembali file gambar.") {
        shop = withContext(Dispatchers.IO) {
            val context = getApplication<Application>()
            val current = currentShop()
            val file = uri?.let { File(context.filesDir, "qris-${UUID.randomUUID()}.png") }
            var committed = false
            try {
                if (uri != null && file != null) {
                    val bitmap = decodeStoreImage(context, uri, maxDimension = 1600)
                    try {
                        file.outputStream().use {
                            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException()
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                current.copy(qris = file?.name.orEmpty()).also {
                    withContext(NonCancellable) {
                        dao.saveStoreSettings(it.settings())
                        committed = true
                        if (current.qris.isNotEmpty()) File(context.filesDir, current.qris).delete()
                    }
                }
            } catch (e: Exception) {
                if (!committed) file?.delete()
                throw e
            }
        }
    }

    private fun runAction(fallback: String, work: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                work()
            } catch (e: CancellationException) {
                throw e
            } catch (e: SaleInputException) {
                error = e.message
            } catch (e: OwnerAuthException) {
                error = e.message
            } catch (e: BackupException) {
                error = e.message
            } catch (_: Exception) {
                error = fallback
            } finally {
                busy = false
            }
        }
    }

    override fun onCleared() {
        database.close()
    }
}

internal fun decodeStoreImage(context: Context, uri: Uri, maxDimension: Int = 512): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException()
    val options = BitmapFactory.Options().apply {
        inSampleSize = storeImageSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    return context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: throw IOException()
}

internal fun storeImageSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sampleSize = 1
    while (maxOf(width, height) / sampleSize > maxDimension) sampleSize *= 2
    return sampleSize
}

private fun writeLogo(context: Context, uri: Uri): String {
    val bitmap = decodeStoreImage(context, uri)
    val file = File(context.filesDir, "logo-${UUID.randomUUID()}.png")
    try {
        file.outputStream().use {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException()
        }
    } finally {
        bitmap.recycle()
    }
    return file.name
}
