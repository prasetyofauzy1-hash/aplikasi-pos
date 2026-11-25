package id.kasir.pos

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerOnboardingTest {
    @Test fun offersAccountChoicesOnlyBeforeTheFirstPinAndWithFirebaseConfigured() {
        assertTrue(shouldOfferOwnerOnboarding(pinConfigured = false, emailAvailable = true, choice = null))
        assertFalse(shouldOfferOwnerOnboarding(pinConfigured = true, emailAvailable = true, choice = null))
        assertFalse(shouldOfferOwnerOnboarding(pinConfigured = false, emailAvailable = false, choice = null))
    }

    @Test fun guestAndEitherLinkedProviderProceedToPinSetupWithoutAnotherPrompt() {
        val account = OwnerAccount("owner-id", "owner@example.com")
        for (choice in listOf(
            OwnerOnboardingChoice.Guest,
            OwnerOnboardingChoice.Linked(account, google = false),
            OwnerOnboardingChoice.Linked(account, google = true)
        )) {
            assertFalse(shouldOfferOwnerOnboarding(pinConfigured = false, emailAvailable = true, choice = choice))
        }
    }
}
