package id.kasir.pos

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val MAX_RESPONSE_BYTES = ((MAX_BACKUP_BYTES + 2L) / 3) * 4 + 4096

internal class FirebaseBackup(private val root: String) {
    fun upload(session: OwnerSession, file: File) {
        if (file.length() !in 1..MAX_BACKUP_BYTES.toLong()) {
            throw BackupException("Ukuran cadangan melebihi batas aplikasi.")
        }
        val payload = JSONObject()
            .put("createdAt", JSONObject().put(".sv", "timestamp"))
            .put("archive", Base64.encodeToString(file.readBytes(), Base64.NO_WRAP))
            .toString().toByteArray(Charsets.UTF_8)
        request(session, "PUT", payload) { }
    }

    fun download(session: OwnerSession, destination: File) {
        request(session, "GET") { connection ->
            val body = connection.inputStream.use { it.readBounded(MAX_RESPONSE_BYTES) }.toString(Charsets.UTF_8)
            if (body.trim() == "null") throw BackupException("Belum ada cadangan cloud untuk akun ini.")
            val encoded = JSONObject(body).getString("archive")
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            if (bytes.size !in 1..MAX_BACKUP_BYTES) throw BackupException("Ukuran cadangan tidak valid.")
            destination.writeBytes(bytes)
        }
    }

    private fun request(
        session: OwnerSession,
        method: String,
        payload: ByteArray? = null,
        onSuccess: (HttpURLConnection) -> Unit
    ) {
        val uid = session.account.uid
        require(uid.matches(Regex("[a-zA-Z0-9_-]{1,128}")))
        val token = URLEncoder.encode(session.idToken, "UTF-8")
        val query = if (method == "PUT") "print=silent&auth=$token" else "auth=$token"
        val connection = URL("${root.trimEnd('/')}/posBackups/$uid.json?$query").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 30_000
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
            }
            when (connection.responseCode) {
                in 200..299 -> onSuccess(connection)
                401, 403, 404 -> throw BackupException("Backup cloud belum tersedia. Periksa pengaturan Firebase atau masuk ulang.")
                else -> throw IOException()
            }
        } finally {
            connection.disconnect()
        }
    }
}
