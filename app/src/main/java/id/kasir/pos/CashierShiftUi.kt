package id.kasir.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private enum class ShiftDialog { OPEN, CLOSE, SUMMARY }

@Composable
internal fun ShiftPanel(model: RegisterModel, loaded: Boolean, latest: ShiftSummary?) {
    var dialog by rememberSaveable { mutableStateOf<ShiftDialog?>(null) }
    val active = latest?.takeIf { it.isOpen }
    fun show(mode: ShiftDialog?) {
        model.clearError()
        dialog = mode
    }

    Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Shift kasir", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                when {
                    !loaded -> "Memuat shift…"
                    active != null -> "Dibuka ${dateLabel(active.shift.openedAt)}"
                    else -> "Belum ada shift aktif."
                },
                color = Muted, style = MaterialTheme.typography.bodySmall
            )
            if (active != null) {
                MetricRow("Kas yang seharusnya", rupiah(active.expectedCash), strong = true)
                OutlinedButton(onClick = { show(ShiftDialog.CLOSE) }, enabled = !model.busy) { Text("Tutup shift") }
            } else if (loaded) {
                Button(onClick = { show(ShiftDialog.OPEN) }, enabled = !model.busy) { Text("Buka shift") }
                if (latest != null) {
                    TextButton(onClick = { show(ShiftDialog.SUMMARY) }, enabled = !model.busy) {
                        Text("Ringkasan shift terakhir")
                    }
                }
            }
        }
    }

    when (dialog) {
        ShiftDialog.OPEN -> if (loaded && active == null) ShiftCashDialog(
            summary = null, model = model, onDismiss = { show(null) },
            onSubmit = { model.openShift(it) { show(null) } }
        )
        ShiftDialog.CLOSE -> if (active != null) key(active.shift.id) {
            ShiftCashDialog(
                summary = active, model = model, onDismiss = { show(null) },
                onSubmit = { amount -> model.closeShift(active, amount) { show(ShiftDialog.SUMMARY) } }
            )
        }
        ShiftDialog.SUMMARY -> if (latest != null && active == null) ClosedShiftDialog(latest) { show(null) }
        null -> Unit
    }
}

@Composable
private fun ShiftCashDialog(
    summary: ShiftSummary?, model: RegisterModel, onDismiss: () -> Unit, onSubmit: (Long) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf("") }
    val cash = parseShiftCash(amount)
    val title = if (summary == null) "Buka shift" else "Tutup shift"
    AlertDialog(
        onDismissRequest = { if (!model.busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (summary == null) {
                    Text("Masukkan uang tunai yang ada di laci sebelum mulai berjualan.")
                } else {
                    ShiftCashTotals(summary)
                    Text("Hitung uang tunai di laci. Pembayaran QRIS tidak masuk hitungan kas.")
                }
                OutlinedTextField(
                    amount, { amount = it; model.clearError() },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (summary == null) "Kas awal" else "Kas akhir terhitung") },
                    prefix = { if (!amount.trimStart().startsWith("Rp", ignoreCase = true)) Text("Rp ") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = !model.busy, singleLine = true, isError = amount.isNotBlank() && cash == null
                )
                if (amount.isNotBlank() && cash == null) ErrorText("Masukkan Rupiah tanpa pecahan, misalnya 50.000.")
                if (summary != null && cash != null) ShiftDifference(summary.difference(cash))
                ErrorText(model.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !model.busy) { Text("Batal") } },
        confirmButton = {
            Button(onClick = { cash?.let(onSubmit) }, enabled = cash != null && !model.busy) { Text(title) }
        }
    )
}

@Composable
private fun ClosedShiftDialog(summary: ShiftSummary, onDismiss: () -> Unit) {
    val closedAt = summary.shift.closedAt ?: return
    val closingCash = summary.shift.closingCash ?: return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ringkasan shift terakhir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Dibuka ${dateLabel(summary.shift.openedAt)}")
                Text("Ditutup ${dateLabel(closedAt)}")
                ShiftCashTotals(summary)
                MetricRow("Kas akhir terhitung", rupiah(closingCash))
                ShiftDifference(summary.difference(closingCash))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Selesai") } }
    )
}

@Composable
private fun ShiftCashTotals(summary: ShiftSummary) {
    MetricRow("Kas awal", rupiah(summary.shift.openingCash))
    MetricRow("Penjualan tunai", rupiah(summary.cashSales))
    MetricRow("Kas yang seharusnya", rupiah(summary.expectedCash), strong = true)
}

@Composable
private fun ShiftDifference(difference: Long) {
    val label = when {
        difference < 0 -> "Kurang ${rupiah(-difference)}"
        difference > 0 -> "Lebih ${rupiah(difference)}"
        else -> "Sesuai"
    }
    MetricRow("Selisih", label, strong = true)
}
