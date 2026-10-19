package com.cyclecare.core.domain.ads

import com.cyclecare.core.domain.notification.MIN_MINUTES_BETWEEN_INTERSTITIALS
import com.cyclecare.core.domain.notification.MIN_SESSIONS_BEFORE_INTERSTITIAL
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Spec 15 §11.3, condition by condition.
 *
 * The eight conditions must all hold for an interstitial to be shown. Each
 * test here turns exactly one condition false and asserts the interstitial
 * is dropped with the reason that corresponds to that condition.
 *
 * The behaviour matrix that these tests implement is written up in
 * `CycleCare-Redesign-Docs/18-interstitial-gating-matrix.md`.
 */
class InterstitialGateTest {

    private val minute = 60_000L

    /** A state in which every condition holds. */
    private fun allowingState() = InterstitialGateState(
        onboardingComplete = true,
        completedSessions = MIN_SESSIONS_BEFORE_INTERSTITIAL,
        millisSinceLastInterstitial = MIN_MINUTES_BETWEEN_INTERSTITIALS * minute,
        hasUnsavedDraft = false,
        millisSinceLastReminderDelivery = MIN_MINUTES_AFTER_REMINDER * minute,
        currentRoute = "home",
        adsConsentGranted = true,
        interstitialsShownToday = 0,
    )

    private fun assertDropped(
        state: InterstitialGateState,
        expected: InterstitialDropReason,
    ) {
        val decision = InterstitialGate.evaluate(state)
        assertThat(decision).isEqualTo(InterstitialGateDecision.Drop(expected))
    }

    // ── baseline ───────────────────────────────────────────────

    @Test
    fun shows_whenEveryConditionHolds() {
        assertThat(InterstitialGate.evaluate(allowingState()))
            .isEqualTo(InterstitialGateDecision.Show)
    }

    // ── condition 1: onboarding complete ───────────────────────

    @Test
    fun drop_whenOnboardingIsNotComplete() {
        assertDropped(
            allowingState().copy(onboardingComplete = false),
            InterstitialDropReason.ONBOARDING_INCOMPLETE,
        )
    }

    // ── condition 2: at least three sessions ───────────────────

    @Test
    fun drop_whenFewerThanThreeSessions() {
        assertDropped(
            allowingState().copy(completedSessions = MIN_SESSIONS_BEFORE_INTERSTITIAL - 1),
            InterstitialDropReason.TOO_FEW_SESSIONS,
        )
    }

    @Test
    fun shows_whenExactlyThreeSessions() {
        assertThat(
            InterstitialGate.evaluate(
                allowingState().copy(completedSessions = MIN_SESSIONS_BEFORE_INTERSTITIAL),
            ),
        ).isEqualTo(InterstitialGateDecision.Show)
    }

    @Test
    fun drop_whenNoSessionsAtAll() {
        assertDropped(
            allowingState().copy(completedSessions = 0),
            InterstitialDropReason.TOO_FEW_SESSIONS,
        )
    }

    // ── condition 3: ten minutes since the last one ────────────

    @Test
    fun drop_whenShownLessThanTenMinutesAgo() {
        assertDropped(
            allowingState().copy(
                millisSinceLastInterstitial = (MIN_MINUTES_BETWEEN_INTERSTITIALS - 1) * minute,
            ),
            InterstitialDropReason.TOO_SOON_SINCE_LAST,
        )
    }

    @Test
    fun shows_whenShownExactlyTenMinutesAgo() {
        assertThat(
            InterstitialGate.evaluate(
                allowingState().copy(
                    millisSinceLastInterstitial = MIN_MINUTES_BETWEEN_INTERSTITIALS * minute,
                ),
            ),
        ).isEqualTo(InterstitialGateDecision.Show)
    }

    @Test
    fun shows_whenNoInterstitialHasEverBeenShown() {
        assertThat(
            InterstitialGate.evaluate(allowingState().copy(millisSinceLastInterstitial = null)),
        ).isEqualTo(InterstitialGateDecision.Show)
    }

    // ── condition 4: no unsaved draft ──────────────────────────

    @Test
    fun drop_whenAnUnsavedDraftExists() {
        assertDropped(
            allowingState().copy(hasUnsavedDraft = true),
            InterstitialDropReason.UNSAVED_DRAFT,
        )
    }

    // ── condition 5: no reminder delivered recently ────────────

    @Test
    fun drop_whenAReminderWasDeliveredRecently() {
        assertDropped(
            allowingState().copy(
                millisSinceLastReminderDelivery = (MIN_MINUTES_AFTER_REMINDER - 1) * minute,
            ),
            InterstitialDropReason.RECENT_REMINDER_DELIVERY,
        )
    }

    @Test
    fun shows_whenTheLastReminderWasLongAgo() {
        assertThat(
            InterstitialGate.evaluate(
                allowingState().copy(
                    millisSinceLastReminderDelivery = MIN_MINUTES_AFTER_REMINDER * minute,
                ),
            ),
        ).isEqualTo(InterstitialGateDecision.Show)
    }

    @Test
    fun shows_whenNoReminderHasEverBeenDelivered() {
        assertThat(
            InterstitialGate.evaluate(allowingState().copy(millisSinceLastReminderDelivery = null)),
        ).isEqualTo(InterstitialGateDecision.Show)
    }

    // ── condition 6: not a forbidden screen ─────────────────────

    @Test
    fun drop_whenOnAForbiddenScreen() {
        FORBIDDEN_INTERSTITIAL_ROUTES.forEach { route ->
            assertDropped(
                allowingState().copy(currentRoute = route),
                InterstitialDropReason.FORBIDDEN_SCREEN,
            )
        }
    }

    @Test
    fun drop_whenNoRouteIsSelected() {
        assertDropped(
            allowingState().copy(currentRoute = null),
            InterstitialDropReason.FORBIDDEN_SCREEN,
        )
    }

    @Test
    fun shows_whenOnAnAllowedScreen() {
        // The four top-level tabs are the only screens an interstitial may
        // be triggered from. The tab itself is allowed even though some of
        // its detail screens are not.
        listOf("home", "calendar", "log", "insights").forEach { route ->
            if (route == "log") {
                // The logging screen is forbidden in its own right, by
                // §11.2 "On the logging screen".
                assertDropped(
                    allowingState().copy(currentRoute = route),
                    InterstitialDropReason.FORBIDDEN_SCREEN,
                )
            } else {
                assertThat(
                    InterstitialGate.evaluate(allowingState().copy(currentRoute = route)),
                ).isEqualTo(InterstitialGateDecision.Show)
            }
        }
    }

    @Test
    fun theLoggingScreenIsForbidden() {
        assertThat(InterstitialGate.isForbiddenScreen("log")).isTrue()
    }

    @Test
    fun theFertilityDetailScreenIsForbidden() {
        assertThat(InterstitialGate.isForbiddenScreen("home/fertility-detail")).isTrue()
    }

    @Test
    fun theTodayTabIsAllowed() {
        assertThat(InterstitialGate.isForbiddenScreen("home")).isFalse()
    }

    // ── condition 7: consent ────────────────────────────────────

    @Test
    fun drop_whenConsentIsNotGranted() {
        assertDropped(
            allowingState().copy(adsConsentGranted = false),
            InterstitialDropReason.CONSENT_NOT_GRANTED,
        )
    }

    // ── condition 8: daily cap ──────────────────────────────────

    @Test
    fun drop_whenTheDailyCapIsReached() {
        assertDropped(
            allowingState().copy(interstitialsShownToday = MAX_INTERSTITIALS_PER_DAY),
            InterstitialDropReason.DAILY_CAP_REACHED,
        )
    }

    @Test
    fun shows_whenTheDailyCapHasNotBeenReached() {
        assertThat(
            InterstitialGate.evaluate(
                allowingState().copy(interstitialsShownToday = MAX_INTERSTITIALS_PER_DAY - 1),
            ),
        ).isEqualTo(InterstitialGateDecision.Show)
    }

    // ── ordering and interaction ───────────────────────────────

    @Test
    fun theFirstFailedConditionIsTheOneReported() {
        // Every condition is false at once. The report must be the first one
        // in the specification's order, so the reason is predictable.
        assertDropped(
            InterstitialGateState(
                onboardingComplete = false,
                completedSessions = 0,
                millisSinceLastInterstitial = 0L,
                hasUnsavedDraft = true,
                millisSinceLastReminderDelivery = 0L,
                currentRoute = "log",
                adsConsentGranted = false,
                interstitialsShownToday = MAX_INTERSTITIALS_PER_DAY,
            ),
            InterstitialDropReason.ONBOARDING_INCOMPLETE,
        )
    }

    @Test
    fun aDraftSuppressesEvenWhenEverythingElseAllows() {
        assertDropped(
            allowingState().copy(hasUnsavedDraft = true),
            InterstitialDropReason.UNSAVED_DRAFT,
        )
    }

    @Test
    fun aForbiddenScreenSuppressesEvenWhenEverythingElseAllows() {
        assertDropped(
            allowingState().copy(currentRoute = "log"),
            InterstitialDropReason.FORBIDDEN_SCREEN,
        )
    }
}