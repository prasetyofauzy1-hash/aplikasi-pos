package id.kasir.pos

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.TimeUnit

class FirebaseBackupTest {
    @get:Rule val files = TemporaryFolder()

    @Test fun readsOnlyTheSignedInOwnersBackupAndLeavesLocalCopyAloneWhenNoneExists() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("null"))
            val cloud = FirebaseBackup(server.url("/").toString())
            val file = files.newFile().apply { writeText("local") }
            val session = OwnerSession(OwnerAccount("owner-123", "owner@example.com"), "token+/")

            val error = assertThrows(BackupException::class.java) { cloud.download(session, file) }

            assertEquals("Belum ada cadangan cloud untuk akun ini.", error.message)
            assertEquals("local", file.readText())
            val request = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            assertEquals("GET", request.method)
            assertEquals("/posBackups/owner-123.json", request.requestUrl?.encodedPath)
            assertEquals("token+/", request.requestUrl?.queryParameter("auth"))
        }
    }

    @Test fun refusesRedirectsWithoutSendingCredentialsToTheRedirectTarget() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", server.url("/redirected")))
            val cloud = FirebaseBackup(server.url("/").toString())
            val session = OwnerSession(OwnerAccount("owner-123", "owner@example.com"), "token")

            assertThrows(java.io.IOException::class.java) { cloud.download(session, files.newFile()) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun refusesAnOversizedRead() {
        val bytes = ByteArray(5).inputStream()
        val error = assertThrows(BackupException::class.java) { bytes.readBounded(4) }
        assertTrue(error.message.orEmpty().contains("ukuran", ignoreCase = true))
    }
}
