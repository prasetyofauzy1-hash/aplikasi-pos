package id.kasir.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun CashierProfilePicker(model: RegisterModel) {
    val profiles by model.cashiers.collectAsStateWithLifecycle()
    val active = profiles.firstOrNull { it.id == model.activeCashierId }
        ?: profiles.firstOrNull { it.id == OWNER_CASHIER_ID }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { showPicker = true }, enabled = active != null && !model.busy) {
        Text(active?.let { "Kasir: ${it.name} · Ganti" } ?: "Pilih kasir", maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    if (showPicker) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text("Pilih kasir") },
            text = {
                CashierProfileList(profiles, active?.id) {
                    model.selectCashier(it)
                    showPicker = false
                }
            },
            confirmButton = { TextButton(onClick = { showPicker = false }) { Text("Tutup") } }
        )
    }
}

@Composable
internal fun CashierProfilesSettings(model: RegisterModel) {
    val profiles by model.cashiers.collectAsStateWithLifecycle()
    var showProfiles by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val id = editingId
    val edited = profiles.firstOrNull { it.id == id }
    OutlinedButton(
        onClick = { model.clearError(); showProfiles = true },
        enabled = !model.busy,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Kelola profil kasir") }
    if (showProfiles) {
        if (id != null && (id == 0L || edited != null)) {
            CashierProfileEditor(model, id, edited?.name.orEmpty()) {
                model.clearError()
                editingId = null
            }
        } else {
            AlertDialog(
                onDismissRequest = { showProfiles = false; editingId = null },
                title = { Text("Profil kasir") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pilih profil untuk diedit. Perubahan memerlukan PIN pemilik.")
                        CashierProfileList(profiles, selectedId = null) { editingId = it }
                    }
                },
                confirmButton = { TextButton(onClick = { editingId = 0L }) { Text("Tambah pegawai") } },
                dismissButton = { TextButton(onClick = { showProfiles = false; editingId = null }) { Text("Tutup") } }
            )
        }
    }
}

@Composable
private fun CashierProfileList(profiles: List<CashierProfile>, selectedId: Long?, onSelect: (Long) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 240.dp)) {
        items(profiles, key = { it.id }) { profile ->
            ListItem(
                headlineContent = { Text(profile.name) },
                supportingContent = { Text(if (profile.id == OWNER_CASHIER_ID) "Pemilik" else "Pegawai") },
                trailingContent = if (selectedId == profile.id) ({ Text("Aktif", color = Pine) }) else null,
                modifier = Modifier.clickable { onSelect(profile.id) }
            )
        }
    }
}

@Composable
private fun CashierProfileEditor(model: RegisterModel, id: Long, initialName: String, onClose: () -> Unit) {
    var name by rememberSaveable(id) { mutableStateOf(initialName) }
    var pin by remember { mutableStateOf("") }
    var deleting by rememberSaveable(id) { mutableStateOf(false) }
    val canSubmit = !model.busy && pin.length == 6 && (deleting || name.isNotBlank())
    val submit = {
        if (deleting) model.deleteCashier(id, pin, onClose)
        else model.saveCashier(id, name, pin, onClose)
        pin = ""
    }
    AlertDialog(
        onDismissRequest = { if (!model.busy) onClose() },
        title = {
            Text(when {
                deleting -> "Hapus pegawai?"
                id == 0L -> "Tambah pegawai"
                id == OWNER_CASHIER_ID -> "Edit profil pemilik"
                else -> "Edit pegawai"
            })
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (deleting) {
                    Text("Hapus profil $initialName? Nama di struk lama tidak berubah.")
                } else {
                    OutlinedTextField(
                        name, { name = it.take(60); model.clearError() }, Modifier.fillMaxWidth(),
                        label = { Text("Nama") }, singleLine = true, enabled = !model.busy
                    )
                }
                PinInput(pin, "PIN pemilik", !model.busy, model.error != null, ImeAction.Done,
                    KeyboardActions(onDone = { if (canSubmit) submit() })) { pin = it; model.clearError() }
                ErrorText(model.error)
                if (id != 0L && id != OWNER_CASHIER_ID && !deleting) {
                    TextButton(onClick = { deleting = true; pin = ""; model.clearError() }, enabled = !model.busy) {
                        Text("Hapus pegawai", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = canSubmit) { Text(if (deleting) "Hapus" else "Simpan") }
        },
        dismissButton = {
            TextButton(onClick = onClose, enabled = !model.busy) { Text("Batal") }
        }
    )
}
