package id.kasir.pos

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class OwnerAccount(val uid: String, val email: String)
internal class OwnerSession(val account: OwnerAccount, val idToken: String)

internal sealed interface OwnerCredential {
    class Password(val email: String, val password: String) : OwnerCredential
    class Google(val getIdToken: suspend () -> String?) : OwnerCredential
}

internal class OwnerAuth(
    key: String,
    private val baseUrl: String = "https://identitytoolkit.googleapis.com/v1"
) {
    private val apiKey = URLEncoder.encode(key, "UTF-8")

    fun authenticate(email: String, password: String, createAccount: Boolean): OwnerAccount =
        session(email, password, createAccount).account

    fun session(email: String, password: String, createAccount: Boolean = false): OwnerSession {
        val result = post(if (createAccount) "signUp" else "signInWithPassword",
            JSONObject().put("email", email).put("password", password).put("returnSecureToken", true))
        return sessionFrom(result)
    }

    fun googleSession(idToken: String, createAccount: Boolean): OwnerSession {
        val result = post("signInWithIdp", JSONObject()
            .put("postBody", "id_token=${URLEncoder.encode(idToken, "UTF-8")}&providerId=google.com")
            .put("requestUri", "http://localhost")
            .put("returnSecureToken", true)
            .put("autoCreate", createAccount))
        if (result.optBoolean("needConfirmation")) {
            throw OwnerAuthException("Email ini sudah terhubung dengan metode lain. Coba masuk dengan email dan kata sandi.")
        }
        return sessionFrom(result)
    }

    private fun sessionFrom(result: JSONObject): OwnerSession {
        val uid = result.getString("localId")
        val accountEmail = result.getString("email")
        val token = result.getString("idToken")
        if (uid.isBlank() || accountEmail.isBlank() || token.isBlank()) throw IOException()
        return OwnerSession(OwnerAccount(uid, accountEmail), token)
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
                    "FEDERATED_USER_ID_ALREADY_LINKED", "NEED_CONFIRMATION", "ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
                        "Email ini sudah terhubung dengan metode lain. Coba masuk dengan email dan kata sandi."
                    "OPERATION_NOT_ALLOWED" -> if (action == "signInWithIdp") {
                        "Login Google belum diaktifkan untuk aplikasi ini. Gunakan email dan kata sandi."
                    } else "Layanan akun tidak tersedia. Coba lagi nanti."
                    "INVALID_IDP_RESPONSE" -> "Login Google tidak dapat diverifikasi. Pilih akun dan coba lagi."
                    "USER_NOT_FOUND" -> if (action == "signInWithIdp") {
                        "Akun ini bukan akun pemilik yang terhubung di perangkat ini."
                    } else "Layanan akun tidak tersedia. Coba lagi nanti."
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
