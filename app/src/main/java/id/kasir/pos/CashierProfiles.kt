package id.kasir.pos

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
internal fun OwnerCashierSettings(model: RegisterModel) {
    val profiles by model.cashiers.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val id = editingId
    val edited = profiles.firstOrNull { it.id == id }
    EmptyPanel("Kasir akun pemilik", "Tambah kasir agar namanya tercatat di setiap struk. Perubahan memerlukan PIN pemilik.")
    profiles.forEach { profile ->
        Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (profile.id == OWNER_CASHIER_ID) "Pemilik" else "Kasir", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { model.clearError(); editingId = profile.id }, enabled = !model.busy,
                    modifier = Modifier.semantics { contentDescription = "Edit ${profile.name}" }) { Text("Edit") }
            }
        }
    }
    Button(
        onClick = { model.clearError(); editingId = 0L },
        enabled = !model.busy,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) { Text("Tambah kasir") }
    if (id != null && (id == 0L || edited != null)) {
        CashierProfileEditor(model, id, edited?.name.orEmpty()) {
            model.clearError()
            editingId = null
        }
    }
}

@Composable
private fun CashierProfileList(profiles: List<CashierProfile>, selectedId: Long?, onSelect: (Long) -> Unit) {
    LazyColumn(Modifier.heightIn(max = 240.dp)) {
        items(profiles, key = { it.id }) { profile ->
            ListItem(
                headlineContent = { Text(profile.name) },
                supportingContent = { Text(if (profile.id == OWNER_CASHIER_ID) "Pemilik" else "Kasir") },
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
                deleting -> "Hapus kasir?"
                id == 0L -> "Tambah kasir"
                id == OWNER_CASHIER_ID -> "Edit profil pemilik"
                else -> "Edit kasir"
            })
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (deleting) {
                    Text("Hapus profil $initialName? Nama di struk lama tidak berubah.")
                } else {
                    OutlinedTextField(
                        name, { name = it.take(60); model.clearError() }, Modifier.fillMaxWidth(),
                        label = { Text("Nama kasir") }, singleLine = true, enabled = !model.busy
                    )
                }
                PinInput(pin, "PIN pemilik", !model.busy, model.error != null, ImeAction.Done,
                    KeyboardActions(onDone = { if (canSubmit) submit() })) { pin = it; model.clearError() }
                ErrorText(model.error)
                if (id != 0L && id != OWNER_CASHIER_ID && !deleting) {
                    TextButton(onClick = { deleting = true; pin = ""; model.clearError() }, enabled = !model.busy) {
                        Text("Hapus kasir", color = MaterialTheme.colorScheme.error)
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
