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
internal fun LinkOwnerEmailDialog(model: RegisterModel, skippable: Boolean, onDismiss: () -> Unit) {
    val dismiss = { if (!model.busy) onDismiss() }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by rememberSaveable { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Hubungkan email pemilik") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Gunakan akun email untuk membuat PIN baru jika lupa. Ini opsional dan perlu internet. Data toko tetap di perangkat ini.")
                OutlinedTextField(email, { email = it; model.clearError() }, Modifier.fillMaxWidth(),
                    label = { Text("Email") }, enabled = !model.busy, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OwnerPasswordField(password, !model.busy, createAccount) { password = it; model.clearError() }
                ErrorText(model.error)
                TextButton(enabled = !model.busy, onClick = {
                    createAccount = !createAccount
                    password = ""
                    model.clearError()
                }) { Text(if (createAccount) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar") }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !model.busy && email.isNotBlank() && if (createAccount) password.length >= 6 else password.isNotEmpty(),
                onClick = { model.linkOwnerEmail(email.trim(), password, createAccount, onDismiss) }
            ) { Text(if (model.busy) "Menghubungkan…" else if (createAccount) "Daftar" else "Masuk") }
        },
        dismissButton = {
            TextButton(onClick = dismiss, enabled = !model.busy) { Text(if (skippable) "Nanti saja" else "Batal") }
        }
    )
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
                Text("PIN tidak dapat dipulihkan tanpa email pemilik yang terhubung. Jika PIN benar-benar lupa, " +
                    "Anda hanya dapat memulai ulang dengan menghapus data aplikasi di pengaturan Android. " +
                    "Tindakan itu menghapus semua data toko di perangkat ini, termasuk produk dan riwayat transaksi.")
            },
            confirmButton = { TextButton(onClick = dismiss) { Text("Mengerti") } }
        )
        return
    }

    var password by remember { mutableStateOf("") }
    var resetSent by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Pulihkan akses pemilik") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Masuk dengan akun pemilik di bawah ini untuk membuat PIN baru tanpa menghapus data. Perlu internet.")
                Text(email)
                OwnerPasswordField(password, !model.busy) { password = it; model.clearError() }
                ErrorText(model.error)
                if (resetSent) Text("Periksa kotak masuk dan folder spam untuk tautan pengaturan ulang kata sandi.")
                TextButton(enabled = !model.busy, onClick = {
                    resetSent = false
                    model.sendOwnerPasswordReset { resetSent = true }
                }) { Text("Kirim tautan atur ulang kata sandi") }
            }
        },
        confirmButton = {
            TextButton(enabled = !model.busy && password.isNotEmpty(), onClick = {
                model.verifyOwnerForPinReset(password, onDismiss)
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
