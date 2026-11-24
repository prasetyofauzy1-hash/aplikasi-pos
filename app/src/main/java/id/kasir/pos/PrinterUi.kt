package id.kasir.pos

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun PrinterSettings(model: RegisterModel) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf<List<PairedPrinter>>(emptyList()) }
    var choosing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val choose = {
        error = null
        try {
            devices = pairedPrinters(context)
            if (devices.isEmpty()) error = "Belum ada perangkat yang dipasangkan. Pasangkan printer di pengaturan Android."
            else choosing = true
        } catch (e: PrinterException) {
            error = e.message
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) choose() else error = printerPermissionMessage
    }
    EmptyPanel("Printer", "Pasangkan printer Bluetooth ESC/POS di pengaturan Android, lalu pilih printer dan ukuran kertas di sini.")
    Text(model.printer?.name ?: "Belum ada printer yang dipilih", color = Muted, style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(
        onClick = {
            if (hasPrinterPermission(context)) choose() else permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        },
        enabled = !model.printing,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Pilih printer") }
    ErrorText(error)
    Text("Ukuran kertas", style = MaterialTheme.typography.bodyMedium)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThermalPaper.entries.forEach { paper ->
            FilterChip(paper == model.thermalPaper, { model.setThermalPaper(paper) },
                label = { Text(paper.label) }, enabled = !model.printing)
        }
    }
    key(model.printer, model.thermalPaper) {
        PrinterActionButton(model, "Tes cetak", enabled = model.printer != null, onPrint = model::testPrinter)
    }
    if (choosing) {
        AlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text("Pilih printer") },
            text = {
                Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) {
                    devices.forEach { device ->
                        TextButton(onClick = {
                            model.selectPrinter(device)
                            choosing = false
                        }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(device.name)
                                Text(device.address, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = false }) { Text("Batal") } }
        )
    }
}

@Composable
internal fun PrinterActionButton(
    model: RegisterModel,
    label: String,
    enabled: Boolean = true,
    onPrint: ((String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf(false) }
    val print = {
        onPrint { message ->
            error = message
            sent = message == null
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) print() else error = printerPermissionMessage
    }
    OutlinedButton(
        onClick = {
            error = null
            sent = false
            if (model.printer == null || hasPrinterPermission(context)) print()
            else permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        },
        enabled = enabled && !model.printing,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) { Text(if (model.printing) "Mengirim data cetak…" else label) }
    ErrorText(error)
    if (sent) Text("Data cetak dikirim ke printer.", color = Pine, style = MaterialTheme.typography.bodyMedium)
}
