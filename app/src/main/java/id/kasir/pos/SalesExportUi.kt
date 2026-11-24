package id.kasir.pos

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.TimeZone
import java.util.UUID

@Composable
internal fun SalesExportButton(model: RegisterModel) {
    var open by rememberSaveable { mutableStateOf(false) }
    var grouping by rememberSaveable { mutableStateOf(SalesGrouping.DAY) }
    var date by rememberSaveable { mutableStateOf(0L) }
    val context = LocalContext.current

    OutlinedButton(
        onClick = {
            model.clearError()
            val today = Calendar.getInstance()
            date = exportDate(today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH))
            grouping = SalesGrouping.DAY
            open = true
        },
        enabled = !model.busy,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Ekspor penjualan") }

    if (open) {
        val period = salesExportPeriod(grouping, date, TimeZone.getDefault())
        AlertDialog(
            onDismissRequest = { if (!model.busy) open = false },
            title = { Text("Ekspor penjualan") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pilih periode laporan CSV.")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SalesGrouping.entries.forEach { option ->
                            FilterChip(
                                selected = grouping == option, onClick = { grouping = option },
                                label = { Text(option.label) }, enabled = !model.busy
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { pickExportDate(context, date) { date = it } },
                        enabled = !model.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Pilih tanggal: ${salesDateLabel(date)}") }
                    Text("Periode: ${period.label}")
                    ErrorText(model.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        model.exportSales(period) { uri ->
                            shareSalesCsv(context, uri)
                            open = false
                        }
                    },
                    enabled = !model.busy
                ) { Text(if (model.busy) "Menyiapkan…" else "Bagikan CSV") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }, enabled = !model.busy) { Text("Batal") }
            }
        )
    }
}

private fun pickExportDate(context: Context, date: Long, onSelected: (Long) -> Unit) {
    val calendar = GregorianCalendar(exportDateZone).apply { timeInMillis = date }
    DatePickerDialog(
        context,
        { _, year, month, day -> onSelected(exportDate(year, month, day)) },
        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

class SalesExportFileProvider : FileProvider()

internal fun writeSalesCsv(context: Context, period: SalesExportPeriod, receipts: List<Receipt>): Uri {
    val directory = File(context.cacheDir, "sales-exports/${UUID.randomUUID()}")
    if (!directory.mkdirs()) throw IOException("Cannot create sales export folder")
    val file = File(directory, period.filename)
    file.writeText(salesCsv(period, receipts))
    return FileProvider.getUriForFile(context, "${context.packageName}.sales-export", file)
}

private fun shareSalesCsv(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, "Laporan penjualan", uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Bagikan laporan penjualan"))
}
