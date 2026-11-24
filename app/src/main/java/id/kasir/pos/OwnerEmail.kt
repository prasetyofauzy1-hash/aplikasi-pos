package id.kasir.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun LinkOwnerEmailDialog(model: RegisterModel, onDismiss: () -> Unit) {
    val dismiss = { if (!model.busy) onDismiss() }
    val skippable = model.initialOwnerEmailOffer
    val linked = model.linkedOwnerEmail != null
    var unlinking by rememberSaveable { mutableStateOf(!model.emailAvailable) }
    var pin by remember { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by rememberSaveable { mutableStateOf(true) }
    val confirmed = ownerEmailsMatch(email, confirmation)
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(when {
            unlinking -> "Hapus tautan email pemilik"
            linked -> "Ubah email pemilik"
            else -> "Hubungkan email pemilik"
        }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!skippable) {
                    OutlinedTextField(pin, { pin = it.filter { c -> c in '0'..'9' }.take(6); model.clearError() },
                        Modifier.fillMaxWidth(), label = { Text("PIN pemilik saat ini") },
                        enabled = !model.busy, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
                if (unlinking) {
                    Text("Email ini tidak akan bisa digunakan untuk memulihkan PIN perangkat ini. Data toko tetap tersimpan.")
                } else {
                    Text("Gunakan akun email untuk membuat PIN baru jika lupa. Ini opsional dan perlu internet. Data toko tetap di perangkat ini.")
                    OwnerEmailField(email, "Email", !model.busy) { email = it; model.clearError() }
                    OwnerEmailField(confirmation, "Konfirmasi email", !model.busy) { confirmation = it; model.clearError() }
                    if (confirmation.isNotBlank() && !confirmed) ErrorText("Konfirmasi email tidak sama.")
                    OwnerPasswordField(password, !model.busy, createAccount) { password = it; model.clearError() }
                    TextButton(enabled = !model.busy, onClick = {
                        createAccount = !createAccount
                        password = ""
                        model.clearError()
                    }) { Text(if (createAccount) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar") }
                    if (linked) {
                        TextButton(enabled = !model.busy, onClick = {
                            unlinking = true
                            pin = ""
                            password = ""
                            model.clearError()
                        }) { Text("Hapus tautan email") }
                    }
                }
                ErrorText(model.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !model.busy && (skippable || pin.length == 6) &&
                    (unlinking || confirmed && if (createAccount) password.length >= 6 else password.isNotEmpty()),
                onClick = {
                    if (unlinking) model.unlinkOwnerEmail(pin, onDismiss)
                    else model.linkOwnerEmail(pin, email, confirmation, password, createAccount, onDismiss)
                }
            ) { Text(when {
                model.busy -> "Memproses…"
                unlinking -> "Hapus tautan"
                createAccount -> "Daftar"
                else -> "Masuk"
            }) }
        },
        dismissButton = {
            TextButton(onClick = dismiss, enabled = !model.busy) { Text(if (skippable) "Nanti saja" else "Batal") }
        }
    )
}

@Composable
private fun OwnerEmailField(value: String, label: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(),
        label = { Text(label) }, enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
}

@Composable
internal fun RecoverOwnerPinDialog(model: RegisterModel, onDismiss: () -> Unit) {
    val dismiss = { if (!model.busy) onDismiss() }
    val email = model.linkedOwnerEmail
    if (!model.emailAvailable || email == null) {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Lupa PIN?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (email != null) {
                        Text("Email pemilik sudah terhubung, tetapi versi ini tidak mendukung login email. " +
                            "Perbarui ke versi Kasir Rapi dengan login email aktif tanpa menghapus aplikasi. Data toko tetap tersimpan.")
                    } else {
                        Text("Tidak ada email pemilik yang terhubung, sehingga PIN tidak dapat dipulihkan.")
                    }
                    Text("Sebagai pilihan terakhir saja, hapus data aplikasi di pengaturan Android untuk memulai dari awal. " +
                        "Ini menghapus semua data toko, termasuk produk dan riwayat transaksi.")
                }
            },
            confirmButton = { TextButton(onClick = dismiss) { Text("Mengerti") } }
        )
        return
    }

    var enteredEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var resetSent by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Pulihkan akses pemilik") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Masukkan email lengkap dan kata sandi akun pemilik untuk membuat PIN baru tanpa menghapus data. Perlu internet.")
                Text("Email terhubung: ${maskedOwnerEmail(email)}")
                OwnerEmailField(enteredEmail, "Email pemilik", !model.busy) {
                    enteredEmail = it
                    resetSent = false
                    model.clearError()
                }
                OwnerPasswordField(password, !model.busy) { password = it; model.clearError() }
                ErrorText(model.error)
                if (resetSent) Text("Periksa kotak masuk dan folder spam untuk tautan pengaturan ulang kata sandi.")
                TextButton(enabled = !model.busy && enteredEmail.isNotBlank(), onClick = {
                    resetSent = false
                    model.sendOwnerPasswordReset(enteredEmail) { resetSent = true }
                }) { Text("Kirim tautan atur ulang kata sandi") }
            }
        },
        confirmButton = {
            TextButton(enabled = !model.busy && enteredEmail.isNotBlank() && password.isNotEmpty(), onClick = {
                model.verifyOwnerForPinReset(enteredEmail, password, onDismiss)
            }) { Text(if (model.busy) "Memproses…" else "Masuk") }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !model.busy) { Text("Batal") } }
    )
}

@Composable
private fun OwnerPasswordField(value: String, enabled: Boolean, newAccount: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(),
        label = { Text("Kata sandi") }, enabled = enabled, singleLine = true,
        supportingText = if (newAccount) ({ Text("Minimal 6 karakter") }) else null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
