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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
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

    val menu = dao.menu().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val ingredients = dao.ingredients().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val sales = dao.sales().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var pinConfigured by mutableStateOf(preferences.contains("pinHash"))
        private set
    var unlocked by mutableStateOf(false)
        private set
    var profile by mutableStateOf(StoreProfile(
        preferences.getString("name", null) ?: "Toko Saya",
        preferences.getString("address", null).orEmpty(),
        preferences.getString("logo", null).orEmpty()
    ))
        private set
    var cart by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set
    var receipt by mutableStateOf<Receipt?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun clearError() { error = null }

    fun authenticate(pin: String, confirmation: String) {
        if (pin.length != 6 || !pin.all { it in '0'..'9' }) {
            error = "Masukkan PIN enam angka."
            return
        }
        if (!pinConfigured && pin != confirmation) {
            error = "Konfirmasi PIN tidak sama."
            return
        }
        runAction("PIN tidak dapat diperiksa.") {
            val valid = withContext(Dispatchers.IO) {
                if (pinConfigured) {
                    verifyOwnerPin(pin)
                } else {
                    val salt = ByteArray(16).also(SecureRandom()::nextBytes)
                    if (!preferences.edit()
                            .putString("pinSalt", Base64.encodeToString(salt, Base64.NO_WRAP))
                            .putString("pinHash", Base64.encodeToString(derivePin(pin, salt), Base64.NO_WRAP))
                            .commit()) throw IOException()
                    true
                }
            }
            if (valid) {
                pinConfigured = true
                unlocked = true
            } else {
                error = "PIN salah. Coba lagi."
            }
        }
    }

    fun lock() {
        if (busy) return
        receipt = null
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

    fun adjust(product: Product, delta: Int) {
        val old = cart[product.id] ?: 0
        val next = old + delta
        if (delta > 0 && next > product.stock) return
        cart = if (next <= 0) cart - product.id else cart + (product.id to next)
    }

    fun saveProduct(product: Product, recipe: Map<Long, Int>, onDone: () -> Unit) = runAction("Produk tidak dapat disimpan.") {
        database.withTransaction {
            if (recipe.isEmpty() && (product.id == 0L || dao.menuById(setOf(product.id)).any { it.recipe.isNotEmpty() })) {
                throw SaleInputException("Tambahkan setidaknya satu bahan ke resep.")
            }
            if (recipe.values.any { it !in 1..MAX_INGREDIENT_STOCK }) {
                throw SaleInputException("Jumlah setiap bahan harus lebih dari 0.")
            }
            val storedProduct = if (recipe.isEmpty()) product else product.copy(stock = 0)
            val id = if (product.id == 0L) dao.add(storedProduct) else {
                dao.update(storedProduct)
                product.id
            }
            dao.deleteRecipe(id)
            if (recipe.isNotEmpty()) dao.addRecipe(recipe.map { (ingredientId, quantity) -> RecipeLine(id, ingredientId, quantity) })
        }
        if (recipe.isEmpty()) {
            val inCart = cart[product.id] ?: 0
            if (product.stock == 0) cart = cart - product.id
            else if (inCart > product.stock) cart = cart + (product.id to product.stock)
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

    fun deleteProduct(product: Product, onDone: () -> Unit) = runAction("Produk tidak dapat dihapus.") {
        dao.delete(product.id)
        cart = cart - product.id
        onDone()
    }

    fun sell(total: Long, payment: Payment, cash: Long?, onDone: () -> Unit) = runAction("Transaksi tidak dapat disimpan.") {
        receipt = register.sell(cart, total, payment, cash, profile)
        cart = emptyMap()
        onDone()
    }

    fun openReceipt(id: Long) = runAction("Struk tidak dapat dibuka.") {
        receipt = dao.receipt(id) ?: throw SaleInputException("Struk tidak ditemukan.")
    }

    fun closeReceipt() { receipt = null }

    fun saveProfile(name: String, address: String, newLogo: Uri?, removeLogo: Boolean, onDone: () -> Unit) =
        runAction("Pengaturan tidak dapat disimpan. Periksa kembali file logo.") {
            profile = withContext(Dispatchers.IO) {
                val logo = when {
                    newLogo != null -> writeLogo(getApplication(), newLogo)
                    removeLogo -> ""
                    else -> profile.logoName
                }
                if (!preferences.edit().putString("name", name).putString("address", address)
                        .putString("logo", logo).commit()) throw IOException()
                StoreProfile(name, address, logo)
            }
            onDone()
        }

    private fun runAction(fallback: String, work: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                work()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: SaleInputException) {
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

private fun writeLogo(context: Context, uri: Uri): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw IOException()
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) {
            inSampleSize *= 2
        }
    }
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: throw IOException()
    val file = File(context.filesDir, "logo-${UUID.randomUUID()}.png")
    file.outputStream().use {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) throw IOException()
    }
    bitmap.recycle()
    return file.name
}
