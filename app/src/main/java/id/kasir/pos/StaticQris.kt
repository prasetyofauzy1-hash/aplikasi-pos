package id.kasir.pos

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun storedQrisImage(name: String): ImageBitmap? {
    val directory = LocalContext.current.filesDir
    val image by key(name, directory) {
        produceState<ImageBitmap?>(null) {
            if (name.isNotEmpty()) value = withContext(Dispatchers.IO) {
                BitmapFactory.decodeFile(File(directory, name).path)?.asImageBitmap()
            }
        }
    }
    return image
}

@Composable
internal fun StaticQrisSettings(model: RegisterModel, onEdit: () -> Unit) {
    val name = model.qrisImageName
    val image = storedQrisImage(name)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            onEdit()
            model.saveQrisImage(uri)
        }
    }
    Surface(color = Color.White, shape = Shape, border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("QRIS statis toko", fontWeight = FontWeight.Bold)
            Text(
                if (image == null) "Pilih gambar QRIS toko agar pelanggan dapat memindainya saat pembayaran."
                else "Gambar tersimpan dan akan ditampilkan pada pembayaran QRIS.",
                style = MaterialTheme.typography.bodySmall, color = Muted
            )
            if (image != null) QrisImage(image, Modifier.size(96.dp), "Pratinjau QRIS toko")
            Button(onClick = { picker.launch("image/*") }, enabled = !model.busy) {
                Text(if (name.isEmpty()) "Pilih gambar QRIS" else "Ganti gambar QRIS")
            }
            if (name.isNotEmpty()) {
                TextButton(onClick = {
                    onEdit()
                    model.saveQrisImage(null)
                }, enabled = !model.busy) { Text("Hapus gambar QRIS") }
            }
        }
    }
}

@Composable
internal fun StaticQrisPayment(name: String, maxSide: Dp, modifier: Modifier = Modifier) {
    val image = storedQrisImage(name)
    if (image == null) {
        if (name.isEmpty()) {
            EmptyPanel("Gambar QRIS belum dipasang", "Tambahkan gambar QRIS statis di tab Toko, atau gunakan kode QRIS toko di luar aplikasi.")
        } else {
            Text("Jika gambar tidak muncul, ganti gambar QRIS di tab Toko.", color = Muted)
        }
    } else {
        Surface(modifier.widthIn(max = maxSide).fillMaxWidth().aspectRatio(1f), color = Color.White, shape = Shape) {
            QrisImage(image, Modifier.padding(12.dp), "Kode QRIS toko untuk dipindai")
        }
    }
}

@Composable
private fun QrisImage(image: ImageBitmap, modifier: Modifier, description: String) {
    Image(BitmapPainter(image, filterQuality = FilterQuality.None), description, modifier,
        contentScale = ContentScale.Fit)
}
