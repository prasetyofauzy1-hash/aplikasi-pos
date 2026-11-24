package id.kasir.pos

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class OwnerAuthTest {
    @Test fun matchesOnlyTheConfirmedEmailIgnoringCaseAndOuterSpaces() {
        assertTrue(ownerEmailsMatch(" Owner@example.com ", "owner@example.com"))
        assertFalse(ownerEmailsMatch("owner@example.com", "ower@example.com"))
        assertFalse(ownerEmailsMatch("owner@example.com", "ow****@example.com"))
        assertFalse(ownerEmailsMatch(" ", " "))
    }

    @Test fun masksTheLinkedAddressWithoutRevealingShortLocalParts() {
        assertEquals("ow****@example.com", maskedOwnerEmail("owner@example.com"))
        assertEquals("a****@example.com", maskedOwnerEmail("ab@example.com"))
        assertEquals("****@example.com", maskedOwnerEmail("a@example.com"))
        assertEquals("****", maskedOwnerEmail("invalid"))
    }

    @Test fun signsUpAndSignsInWithFirebase() {
        withServer { auth, server ->
            val email = "owner@example.com"
            for ((create, action) in listOf(true to "signUp", false to "signInWithPassword")) {
                server.enqueue(MockResponse().setBody("""{"localId":"owner-123","email":"owner@example.com","idToken":"notStored"}"""))
                val account = auth.authenticate(email, "test-password", create)
                assertEquals(OwnerAccount("owner-123", email), account)
                val request = server.next()
                val body = JSONObject(request.body.readUtf8())
                assertEquals("POST", request.method)
                assertEquals("/v1/accounts:$action?key=test-key", request.path)
                assertEquals(email, body.getString("email"))
                assertEquals("test-password", body.getString("password"))
                assertTrue(body.getBoolean("returnSecureToken"))
                assertTrue(account.matches("owner-123"))
                assertFalse(account.matches("other-account"))
                assertFalse(account.matches(null))
            }
        }
    }

    @Test fun requestsPasswordResetForTheLinkedEmail() {
        withServer { auth, server ->
            server.enqueue(MockResponse().setBody("{}"))
            auth.sendPasswordReset("owner@example.com")
            val request = server.next()
            val body = JSONObject(request.body.readUtf8())
            assertEquals("POST", request.method)
            assertEquals("/v1/accounts:sendOobCode?key=test-key", request.path)
            assertEquals("PASSWORD_RESET", body.getString("requestType"))
            assertEquals("owner@example.com", body.getString("email"))
            assertFalse(body.has("password"))
        }
    }

    @Test fun cloudSessionIncludesTheFirebaseIdTokenForTheAuthenticatedAccount() {
        withServer { auth, server ->
            server.enqueue(MockResponse().setBody("""{"localId":"owner-123","email":"owner@example.com","idToken":"signed-token"}"""))
            val session = auth.session("owner@example.com", "test-password")
            assertEquals(OwnerAccount("owner-123", "owner@example.com"), session.account)
            assertEquals("signed-token", session.idToken)
            assertEquals("/v1/accounts:signInWithPassword?key=test-key", server.next().path)
        }
    }

    @Test fun rejectsInvalidCredentialsWithAnIndonesianMessage() {
        withServer { auth, server ->
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"message":"INVALID_LOGIN_CREDENTIALS"}}"""))
            val error = assertThrows(OwnerAuthException::class.java) {
                auth.authenticate("owner@example.com", "wrong-password", createAccount = false)
            }
            assertEquals("Email atau kata sandi salah.", error.message)
        }
    }

    private fun MockWebServer.next(): RecordedRequest = checkNotNull(takeRequest(2, TimeUnit.SECONDS))

    private fun withServer(test: (OwnerAuth, MockWebServer) -> Unit) {
        MockWebServer().use { server ->
            test(OwnerAuth("test-key", server.url("/v1").toString()), server)
        }
    }
}
