package com.cyclecare.core.domain.ads

import com.cyclecare.core.domain.notification.MIN_MINUTES_BETWEEN_INTERSTITIALS
import com.cyclecare.core.domain.notification.MIN_SESSIONS_BEFORE_INTERSTITIAL
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The daily interstitial cap.
 *
 * The cap is a product decision of 3 per day. Spec 15 §11.3 condition 8
 * requires a cap but gives no number, so these tests pin the decision
 * rather than a specification requirement.
 *
 * The other seven conditions are unaffected by the cap, and are checked
 * here to make sure that changing the cap cannot quietly relax them.
 */
class InterstitialDailyCapTest {

    private val minute = 60_000L

    /** A state in which every condition except the cap holds. */
    private fun stateWithImpressionsToday(impressions: Int) = InterstitialGateState(
        onboardingComplete = true,
        completedSessions = MIN_SESSIONS_BEFORE_INTERSTITIAL,
        millisSinceLastInterstitial = MIN_MINUTES_BETWEEN_INTERSTITIALS * minute,
        hasUnsavedDraft = false,
        millisSinceLastReminderDelivery = MIN_MINUTES_AFTER_REMINDER * minute,
        currentRoute = "home",
        adsConsentGranted = true,
        interstitialsShownToday = impressions,
    )

    private fun decisionFor(impressions: Int) =
        InterstitialGate.evaluate(stateWithImpressionsToday(impressions))

    // ── zero through three ─────────────────────────────────────────

    @Test
    fun theFirstInterstitialOfTheDayIsAllowed() {
        assertThat(decisionFor(0)).isEqualTo(InterstitialGateDecision.Show)
    }

    @Test
    fun theSecondInterstitialOfTheDayIsAllowed() {
        assertThat(decisionFor(1)).isEqualTo(InterstitialGateDecision.Show)
    }

    @Test
    fun theThirdInterstitialOfTheDayIsAllowed() {
        assertThat(decisionFor(2)).isEqualTo(InterstitialGateDecision.Show)
    }

    @Test
    fun theFourthInterstitialOfTheDayIsSuppressed() {
        assertThat(decisionFor(3))
            .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.DAILY_CAP_REACHED))
    }

    @Test
    fun anythingBeyondTheCapIsAlsoSuppressed() {
        // A count above the cap cannot happen in normal operation, but if it
        // did, the gate must still refuse rather than wrap or allow.
        listOf(4, 5, 50).forEach { impressions ->
            assertThat(decisionFor(impressions))
                .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.DAILY_CAP_REACHED))
        }
    }

    // ── the cap is configurable ─────────────────────────────────────

    @Test
    fun theCapIsThreeByDefault() {
        assertThat(MAX_INTERSTITIALS_PER_DAY).isEqualTo(3)
    }

    @Test
    fun aDifferentCapChangesOnlyTheCap() {
        // Raising the cap lets a fourth through, and nothing else changes.
        assertThat(
            InterstitialGate.evaluate(stateWithImpressionsToday(3), dailyCap = 4),
        ).isEqualTo(InterstitialGateDecision.Show)

        // Lowering it suppresses earlier, and the reason is still the cap.
        assertThat(
            InterstitialGate.evaluate(stateWithImpressionsToday(1), dailyCap = 1),
        ).isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.DAILY_CAP_REACHED))
    }

    @Test
    fun aCapOfZeroSuppressesEveryInterstitial() {
        assertThat(
            InterstitialGate.evaluate(stateWithImpressionsToday(0), dailyCap = 0),
        ).isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.DAILY_CAP_REACHED))
    }

    // ── the cap does not relax the other conditions ───────────────

    @Test
    fun theCapNeverOverridesAnEarlierCondition() {
        // A draft is unsaved, so the request is dropped for that reason
        // rather than being allowed by a raised cap.
        val withDraft = stateWithImpressionsToday(0).copy(hasUnsavedDraft = true)
        assertThat(InterstitialGate.evaluate(withDraft, dailyCap = 10))
            .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.UNSAVED_DRAFT))
    }

    @Test
    fun aRecentReminderIsStillSuppressedUnderARaisedCap() {
        val justDelivered = stateWithImpressionsToday(0).copy(
            millisSinceLastReminderDelivery = 1 * minute,
        )
        assertThat(InterstitialGate.evaluate(justDelivered, dailyCap = 10))
            .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.RECENT_REMINDER_DELIVERY))
    }

    @Test
    fun aForbiddenScreenIsStillSuppressedUnderARaisedCap() {
        val onLog = stateWithImpressionsToday(0).copy(currentRoute = "log")
        assertThat(InterstitialGate.evaluate(onLog, dailyCap = 10))
            .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.FORBIDDEN_SCREEN))
    }

    @Test
    fun tooFewSessionsIsStillSuppressedUnderARaisedCap() {
        val tooFew = stateWithImpressionsToday(0).copy(
            completedSessions = MIN_SESSIONS_BEFORE_INTERSTITIAL - 1,
        )
        assertThat(InterstitialGate.evaluate(tooFew, dailyCap = 10))
            .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.TOO_FEW_SESSIONS))
    }

    @Test
    fun anIncompleteOnboardingIsStillSuppressedUnderARaisedCap() {
        val onboarding = stateWithImpressionsToday(0).copy(onboardingComplete = false)
        assertThat(InterstitialGate.evaluate(onboarding, dailyCap = 10))
            .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.ONBOARDING_INCOMPLETE))
    }

    @Test
    fun missingConsentIsStillSuppressedUnderARaisedCap() {
        val noConsent = stateWithImpressionsToday(0).copy(adsConsentGranted = false)
        assertThat(InterstitialGate.evaluate(noConsent, dailyCap = 10))
            .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.CONSENT_NOT_GRANTED))
    }

    @Test
    fun aRecentInterstitialIsStillSuppressedUnderARaisedCap() {
        // Condition 3, the ten minute spacing rule, is independent of the
        // daily cap and must hold even when the cap is generous.
        val justShown = stateWithImpressionsToday(0).copy(millisSinceLastInterstitial = 1 * minute)
        assertThat(InterstitialGate.evaluate(justShown, dailyCap = 10))
            .isEqualTo(InterstitialGateDecision.Drop(InterstitialDropReason.TOO_SOON_SINCE_LAST))
    }
}
