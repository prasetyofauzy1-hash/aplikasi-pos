package id.kasir.pos

internal sealed interface OwnerOnboardingChoice {
    data object Guest : OwnerOnboardingChoice
    class Linked(val account: OwnerAccount, val google: Boolean) : OwnerOnboardingChoice
}

internal fun shouldOfferOwnerOnboarding(pinConfigured: Boolean, emailAvailable: Boolean, choice: OwnerOnboardingChoice?) =
    !pinConfigured && emailAvailable && choice == null
