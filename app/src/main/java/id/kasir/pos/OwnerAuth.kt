package id.kasir.pos

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class OwnerAccount(val uid: String, val email: String)

internal class OwnerAuth(
    key: String,
    private val baseUrl: String = "https://identitytoolkit.googleapis.com/v1"
) {
    private val apiKey = URLEncoder.encode(key, "UTF-8")

    fun authenticate(email: String, password: String, createAccount: Boolean): OwnerAccount {
        val result = post(if (createAccount) "signUp" else "signInWithPassword",
            JSONObject().put("email", email).put("password", password).put("returnSecureToken", true))
        val uid = result.getString("localId")
        val accountEmail = result.getString("email")
        if (uid.isBlank() || accountEmail.isBlank()) throw IOException()
        return OwnerAccount(uid, accountEmail)
    }

    fun sendPasswordReset(email: String) {
        post("sendOobCode", JSONObject().put("email", email).put("requestType", "PASSWORD_RESET"))
    }

    private fun post(action: String, payload: JSONObject): JSONObject {
        val connection = URL("$baseUrl/accounts:$action?key=$apiKey").openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val successful = connection.responseCode in 200..299
            val body = (if (successful) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (successful) JSONObject(body) else {
                val error = runCatching { JSONObject(body).getJSONObject("error").getString("message") }.getOrNull()
                    ?.substringBefore(" : ")
                throw OwnerAuthException(when (error) {
                    "EMAIL_EXISTS" -> "Email sudah terdaftar. Pilih Masuk untuk menghubungkannya."
                    "INVALID_EMAIL" -> "Periksa kembali alamat email."
                    "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD", "EMAIL_NOT_FOUND", "USER_DISABLED" ->
                        "Email atau kata sandi salah."
                    "WEAK_PASSWORD" -> "Kata sandi belum memenuhi syarat keamanan."
                    "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Terlalu banyak percobaan. Coba lagi nanti."
                    else -> "Layanan akun tidak tersedia. Coba lagi nanti."
                })
            }
        } finally {
            connection.disconnect()
        }
    }
}

internal class OwnerAuthException(message: String) : IOException(message)

internal fun OwnerAccount.matches(linkedUid: String?) = linkedUid != null && uid == linkedUid

internal fun ownerEmailsMatch(email: String, confirmation: String) =
    email.isNotBlank() && email.trim().equals(confirmation.trim(), ignoreCase = true)

internal fun maskedOwnerEmail(email: String): String {
    val at = email.lastIndexOf('@')
    if (at <= 0 || at == email.lastIndex) return "****"
    return email.take(minOf(2, at - 1)) + "****" + email.substring(at)
}
