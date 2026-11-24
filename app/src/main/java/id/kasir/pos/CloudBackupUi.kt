package id.kasir.pos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

private enum class CloudAction(val label: String, val success: String, val warning: String) {
    BACKUP("Cadangkan ke cloud", "Cadangan cloud tersimpan.",
        "Cadangan ini menggantikan cadangan cloud sebelumnya."),
    RESTORE("Pulihkan dari cloud", "Data toko berhasil dipulihkan.",
        "Data toko di perangkat ini akan diganti oleh cadangan. Transaksi setelah cadangan dibuat tidak ikut dipulihkan.")
}

@Composable
internal fun CloudBackupSettings(model: RegisterModel) {
    if (!model.cloudBackupAvailable) return
    val linkedEmail = model.linkedOwnerEmail
    var action by remember(linkedEmail) { mutableStateOf<CloudAction?>(null) }
    var result by remember(linkedEmail) { mutableStateOf<String?>(null) }
    HorizontalDivider(color = Line)
    EmptyPanel("Backup cloud", if (linkedEmail == null) "Hubungkan email pemilik di atas untuk mencadangkan atau memulihkan data."
        else "Simpan salinan produk, stok, riwayat, dan profil toko. Perlu internet dan akun pemilik yang sama.")
    if (linkedEmail != null) {
        CloudAction.entries.forEach { choice ->
            OutlinedButton(
                onClick = { model.clearError(); result = null; action = choice },
                enabled = !model.busy, modifier = Modifier.fillMaxWidth()
            ) { Text(choice.label) }
        }
        result?.let { Text(it, color = Pine) }
        action?.let { choice ->
            CloudBackupDialog(model, choice, linkedEmail, onDismiss = { action = null }, onDone = {
                result = choice.success
                action = null
            })
        }
    }
}

@Composable
private fun CloudBackupDialog(
    model: RegisterModel, action: CloudAction, linkedEmail: String, onDismiss: () -> Unit, onDone: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val dismiss = {
        if (!model.busy) {
            model.clearError()
            onDismiss()
        }
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(action.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(action.warning)
                Text("Akun pemilik: ${maskedOwnerEmail(linkedEmail)}")
                OutlinedTextField(
                    pin, { pin = it.filter { c -> c in '0'..'9' }.take(6); model.clearError() },
                    Modifier.fillMaxWidth(), label = { Text("PIN pemilik saat ini") },
                    enabled = !model.busy, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                )
                OwnerEmailField(email, "Email pemilik", !model.busy) { email = it; model.clearError() }
                OwnerPasswordField(password, !model.busy) { password = it; model.clearError() }
                ErrorText(model.error)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !model.busy && pin.length == 6 && email.isNotBlank() && password.isNotEmpty(),
                onClick = { model.runCloudBackup(action == CloudAction.RESTORE, pin, email, password, onDone) }
            ) { Text(if (model.busy) "Memproses…" else if (action == CloudAction.RESTORE) "Ganti data perangkat" else "Cadangkan") }
        },
        dismissButton = { TextButton(onClick = dismiss, enabled = !model.busy) { Text("Batal") } }
    )
}
