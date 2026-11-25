package id.kasir.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun OwnerEmailLink(model: RegisterModel, onboarding: Boolean = false, onDismiss: () -> Unit = {}) {
    val dismiss = { if (!model.busy) onDismiss() }
    val linked = model.linkedOwnerEmail != null
    val google = googleOwnerCredential()
    var unlinking by rememberSaveable { mutableStateOf(!model.emailAvailable) }
    var showPassword by rememberSaveable { mutableStateOf(linked && !model.isGoogleOwner) }
    var pin by remember { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var confirmation by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var createAccount by rememberSaveable { mutableStateOf(true) }
    val confirmed = ownerEmailsMatch(email, confirmation)
    val options: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!onboarding) {
                OutlinedTextField(pin, { pin = it.filter { c -> c in '0'..'9' }.take(6); model.clearError() },
                    Modifier.fillMaxWidth(), label = { Text("PIN pemilik saat ini") },
                    enabled = !model.busy, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
            if (unlinking) {
                Text("Email ini tidak akan bisa digunakan untuk memulihkan PIN perangkat ini. Data toko tetap tersimpan.")
            } else {
                Text("Gunakan akun email untuk membuat PIN baru jika lupa atau memakai backup cloud jika tersedia. Ini opsional dan perlu internet.")
                if (!linked) Text("Dalam mode tamu, data toko hanya disimpan di perangkat ini.")
                if (model.googleOwnerAvailable) {
                    OwnerLinkOption(
                        label = if (model.busy) "Memproses…" else "Masuk dengan Google",
                        onClick = { model.linkGoogleOwner(pin, google, onDismiss) },
                        enabled = !model.busy && (onboarding || pin.length == 6),
                        google = true
                    )
                }
                if (showPassword || !model.googleOwnerAvailable) {
                    OwnerEmailField(email, "Email", !model.busy) { email = it; model.clearError() }
                    OwnerEmailField(confirmation, "Konfirmasi email", !model.busy) { confirmation = it; model.clearError() }
                    if (confirmation.isNotBlank() && !confirmed) ErrorText("Konfirmasi email tidak sama.")
                    OwnerPasswordField(password, !model.busy, createAccount) { password = it; model.clearError() }
                    TextButton(enabled = !model.busy, onClick = {
                        createAccount = !createAccount
                        password = ""
                        model.clearError()
                    }) { Text(if (createAccount) "Sudah punya akun? Masuk" else "Belum punya akun? Daftar") }
                } else {
                    OwnerLinkOption("Gunakan email dan kata sandi", enabled = !model.busy, onClick = {
                        model.clearError()
                        showPassword = true
                    })
                }
                if (linked) {
                    TextButton(enabled = !model.busy, onClick = {
                        unlinking = true
                        pin = ""
                        password = ""
                        model.clearError()
                    }) { Text("Hapus tautan email") }
                } else {
                    OwnerLinkOption(
                        "Lanjutkan sebagai tamu",
                        onClick = if (onboarding) model::continueAsGuest else dismiss,
                        enabled = !model.busy
                    )
                }
            }
            ErrorText(model.error)
        }
    }
    val confirm: @Composable () -> Unit = {
        if (unlinking || showPassword || !model.googleOwnerAvailable) {
            TextButton(
                modifier = if (onboarding) Modifier.fillMaxWidth() else Modifier,
                enabled = !model.busy && (onboarding || pin.length == 6) &&
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
        }
    }
    if (onboarding) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            StoreLogo("", Modifier.size(50.dp))
            Header("KASIR UNTUK TOKO ANDA", stringResource(R.string.owner_welcome, stringResource(R.string.app_name)),
                "Pilih cara masuk sebelum membuat PIN pemilik.")
            options()
            confirm()
        }
    } else {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(when {
                unlinking -> "Hapus tautan email pemilik"
                linked -> "Ubah email pemilik"
                else -> "Hubungkan email pemilik"
            }) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { options() } },
            confirmButton = confirm,
            dismissButton = if (linked) {
                { TextButton(onClick = dismiss, enabled = !model.busy) { Text("Batal") } }
            } else null
        )
    }
}

@Composable
private fun OwnerLinkOption(label: String, onClick: () -> Unit, enabled: Boolean, google: Boolean = false) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        colors = if (google) {
            ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F))
        } else ButtonDefaults.outlinedButtonColors(),
        border = if (google && enabled) BorderStroke(1.dp, Color(0xFF747775))
            else ButtonDefaults.outlinedButtonBorder(enabled)
    ) {
        if (google) {
            Image(
                painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp).size(20.dp),
                alpha = if (enabled) 1f else 0.38f
            )
        }
        Text(label)
    }
}

@Composable
internal fun OwnerEmailField(value: String, label: String, enabled: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(),
        label = { Text(label) }, enabled = enabled, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
}

@Composable
internal fun RecoverOwnerPinDialog(model: RegisterModel, onDismiss: () -> Unit) {
    val dismiss = { if (!model.busy) onDismiss() }
    val email = model.linkedOwnerEmail
    if (!model.emailAvailable || email == null || model.isGoogleOwner && !model.googleOwnerAvailable) {
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Lupa PIN?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (email != null) {
                        Text(stringResource(R.string.owner_recovery_update, stringResource(R.string.app_name)))
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

    if (model.isGoogleOwner) {
        val google = googleOwnerCredential()
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("Pulihkan akses pemilik") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Masuk dengan akun Google pemilik yang terhubung untuk membuat PIN baru tanpa menghapus data. Perlu internet.")
                    Text("Email terhubung: ${maskedOwnerEmail(email)}")
                    ErrorText(model.error)
                }
            },
            confirmButton = {
                TextButton(enabled = !model.busy, onClick = { model.verifyOwnerForPinReset(google, onDismiss) }) {
                    Text(if (model.busy) "Memproses…" else "Masuk dengan Google")
                }
            },
            dismissButton = { TextButton(onClick = dismiss, enabled = !model.busy) { Text("Batal") } }
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
                model.verifyOwnerForPinReset(OwnerCredential.Password(enteredEmail, password), onDismiss)
            }) { Text(if (model.busy) "Memproses…" else "Masuk") }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !model.busy) { Text("Batal") } }
    )
}

@Composable
internal fun OwnerPasswordField(value: String, enabled: Boolean, newAccount: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(),
        label = { Text("Kata sandi") }, enabled = enabled, singleLine = true,
        supportingText = if (newAccount) ({ Text("Minimal 6 karakter") }) else null,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
}
