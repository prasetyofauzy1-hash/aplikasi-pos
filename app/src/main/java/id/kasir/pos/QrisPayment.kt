package id.kasir.pos

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal enum class QrisStatus { PENDING, PAID, EXPIRED, CANCELED }
internal enum class QrisResolution { WAIT, SAVE, RELEASE }

internal data class QrisOrder(
    val id: String,
    val total: Long,
    val status: QrisStatus,
    val expiresAt: Long,
    val qrUrl: String?
)

internal fun PendingQris?.resolution(order: QrisOrder): QrisResolution {
    if (this == null || order.id != orderId || order.total != total) return QrisResolution.WAIT
    return when (order.status) {
        QrisStatus.PENDING -> QrisResolution.WAIT
        QrisStatus.PAID -> QrisResolution.SAVE
        QrisStatus.EXPIRED, QrisStatus.CANCELED -> QrisResolution.RELEASE
    }
}

internal class QrisClient(private val endpoint: URL, private val token: String) {
    suspend fun create(pending: PendingQris): QrisOrder = order("/orders", pending, create = true)

    suspend fun status(pending: PendingQris): QrisOrder = order("/orders/${pending.orderId}", pending, create = false)

    private suspend fun order(path: String, pending: PendingQris, create: Boolean): QrisOrder = withContext(Dispatchers.IO) {
        val connection = connect(URL(endpoint.toString().trimEnd('/') + path))
        try {
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (create) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                val payload = JSONObject().put("order_id", pending.orderId).put("gross_amount", pending.total)
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            if (connection.responseCode != 200) throw IOException("Layanan QRIS belum tersedia.")
            val result = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val id = result.getString("order_id")
            val total = result.getLong("gross_amount")
            if (id != pending.orderId || total != pending.total) throw IOException("Pesanan QRIS tidak cocok.")
            QrisOrder(id, total, when (result.getString("status")) {
                "pending" -> QrisStatus.PENDING
                "paid" -> QrisStatus.PAID
                "expired" -> QrisStatus.EXPIRED
                "canceled" -> QrisStatus.CANCELED
                else -> throw IOException("Status QRIS tidak dikenal.")
            }, result.getLong("expires_at"), result.optString("qr_url").takeUnless { it.isBlank() || it == "null" })
        } finally {
            connection.disconnect()
        }
    }

    suspend fun image(qrUrl: String): Bitmap = withContext(Dispatchers.IO) {
        val url = URL(qrUrl)
        if (url.host !in setOf("api.sandbox.midtrans.com", "api.midtrans.com") ||
            url.userInfo != null || !url.path.startsWith("/v2/qris/")) throw IOException("URL QRIS tidak valid.")
        val connection = connect(url)
        try {
            if (connection.responseCode != 200) throw IOException("Gambar QRIS tidak tersedia.")
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
                ?: throw IOException("Gambar QRIS tidak dapat dibaca.")
        } finally {
            connection.disconnect()
        }
    }

    private fun connect(url: URL): HttpsURLConnection =
        (url.openConnection() as? HttpsURLConnection ?: throw IOException("QRIS memerlukan HTTPS.")).apply {
            connectTimeout = 6000
            readTimeout = 12000
            instanceFollowRedirects = false
            useCaches = false
        }

    companion object {
        fun configured(): QrisClient? = runCatching {
            val url = URL(BuildConfig.QRIS_WORKER_URL)
            if (url.protocol == "https" && url.host.isNotBlank() && url.userInfo == null &&
                url.query == null && url.ref == null && BuildConfig.QRIS_APP_TOKEN.isNotBlank()) {
                QrisClient(url, BuildConfig.QRIS_APP_TOKEN)
            } else null
        }.getOrNull()
    }
}
