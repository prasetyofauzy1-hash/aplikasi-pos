package id.kasir.pos

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ReceiptShareButtons(receipt: Receipt) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember(receipt) { mutableStateOf(false) }
    var error by remember(receipt) { mutableStateOf<String?>(null) }

    fun send(email: Boolean) {
        pending = true
        error = null
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { writeReceiptPdf(context, receipt) }
                val intent = receiptPdfIntent(context, receipt, uri)
                if (email) startReceiptEmail(context, intent)
                else context.startActivity(Intent.createChooser(intent, "Bagikan struk"))
            } catch (e: CancellationException) {
                throw e
            } catch (_: ActivityNotFoundException) {
                error = if (email) "Tidak ada aplikasi email yang dapat melampirkan PDF. Pilih Bagikan struk."
                    else "Tidak ada aplikasi untuk membagikan PDF."
            } catch (_: Exception) {
                error = "PDF struk tidak dapat disiapkan. Coba lagi."
            } finally {
                pending = false
            }
        }
    }

    Button(modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp), enabled = !pending,
        onClick = { send(false) }) { Text("Bagikan struk") }
    OutlinedButton(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !pending,
        onClick = { send(true) }) { Text("Kirim via Email") }
    ErrorText(error)
}

internal fun receiptPdfIntent(context: Context, receipt: Receipt, uri: Uri) = Intent(Intent.ACTION_SEND).apply {
    type = receiptPdfMime
    putExtra(Intent.EXTRA_SUBJECT, "Struk ${ticket(receipt.sale.id)} • ${receipt.sale.storeName}")
    putExtra(Intent.EXTRA_STREAM, uri)
    clipData = ClipData.newUri(context.contentResolver, "Struk", uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
}

private fun startReceiptEmail(context: Context, intent: Intent) {
    val pm = context.packageManager
    val mailto = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
    val emailPackages = pm.queryIntentActivities(mailto, PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo.packageName }.toSet()
    val sendTargets = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
    val (emailTargets, otherTargets) = sendTargets.partition { it.packageName in emailPackages }
    when (emailTargets.size) {
        0 -> throw ActivityNotFoundException()
        1 -> context.startActivity(intent.setComponent(emailTargets.first()))
        else -> context.startActivity(Intent.createChooser(intent, "Kirim via Email").apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, otherTargets.toTypedArray())
        })
    }
}
