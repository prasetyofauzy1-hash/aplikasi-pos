package id.kasir.pos

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

@Composable
internal fun googleOwnerCredential(): OwnerCredential.Google {
    val activity = LocalActivity.current
    return OwnerCredential.Google {
        if (activity == null || BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            throw OwnerAuthException(googleCredentialError(false, "DEVELOPER_ERROR"))
        }
        try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(BuildConfig.GOOGLE_WEB_CLIENT_ID).build())
                .build()
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential !is CustomCredential ||
                credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) throw OwnerAuthException("Akun Google tidak dapat diverifikasi. Silakan coba lagi.")
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (_: GetCredentialCancellationException) {
            null
        } catch (e: GetCredentialException) {
            throw OwnerAuthException(googleCredentialError(e is NoCredentialException, "${e.type} ${e.message.orEmpty()}"))
        } catch (_: GoogleIdTokenParsingException) {
            throw OwnerAuthException("Akun Google tidak dapat diverifikasi. Silakan coba lagi.")
        }
    }
}

internal fun googleCredentialError(noCredential: Boolean, detail: String): String = when {
    detail.contains("DEVELOPER_ERROR", ignoreCase = true) ||
        detail.contains("developer console", ignoreCase = true) ->
        "Login Google belum dikonfigurasi untuk versi aplikasi ini. Hubungi pengelola aplikasi."
    noCredential ->
        "Tidak ada akun Google yang dapat digunakan. Tambahkan akun Google di perangkat. Jika sudah ada, versi aplikasi ini mungkin belum terdaftar untuk login Google."
    else -> "Tidak dapat masuk dengan Google. Periksa koneksi internet, lalu coba lagi."
}
