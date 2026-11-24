package id.kasir.pos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun PendingQrisScreen(pending: PendingQris, model: RegisterModel) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(pending.orderId, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive && model.pendingQris?.orderId == pending.orderId) {
                model.checkQris(pending)
                delay(4000)
            }
        }
    }
    BackHandler { }

    val order = model.qrisOrder
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val seconds = order?.let { ((it.expiresAt - now + 999) / 1000).coerceAtLeast(0) }
    val image = model.qrisImage
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header("03  /  SELESAIKAN TRANSAKSI", "QRIS Otomatis", "Pindai dengan aplikasi pembayaran yang mendukung QRIS.")
        TotalBanner("Total yang harus dibayar", pending.total)
        if (seconds != null && seconds > 0 && image != null) {
            Surface(color = Color.White, shape = Shape, modifier = Modifier.widthIn(max = 300.dp)) {
                Image(image.asImageBitmap(), contentDescription = "Kode pembayaran QRIS", modifier = Modifier
                    .fillMaxWidth().aspectRatio(1f).padding(12.dp))
            }
        } else if (model.qrisIssue == null && (seconds == null || seconds > 0)) {
            CircularProgressIndicator()
        }
        when {
            seconds == null -> Text("Menyiapkan kode QRIS…")
            seconds == 0L -> Text("Waktu pindai habis. Menunggu status akhir dari Midtrans.")
            else -> Text("Kode berlaku ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}", fontWeight = FontWeight.Bold)
        }
        if (seconds != null && seconds > 0) Text("Menunggu konfirmasi pembayaran…")
        ErrorText(model.qrisIssue)
        Text("Jika aplikasi ditutup, pembayaran ini akan diperiksa lagi saat dibuka.")
    }
}

@Composable
internal fun QrisResultScreen(status: QrisStatus, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        EmptyPanel(
            if (status == QrisStatus.EXPIRED) "QRIS kedaluwarsa" else "QRIS dibatalkan",
            "Transaksi tidak disimpan. Pesanan masih ada di keranjang."
        )
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke keranjang") }
    }
}
