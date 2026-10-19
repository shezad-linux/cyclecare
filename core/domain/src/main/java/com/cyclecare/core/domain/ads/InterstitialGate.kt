package com.cyclecare.core.domain.ads

import com.cyclecare.core.domain.notification.MIN_MINUTES_BETWEEN_INTERSTITIALS
import com.cyclecare.core.domain.notification.MIN_SESSIONS_BEFORE_INTERSTITIAL

/**
 * Interstitial gating.
 *
 * Spec 15 §11.3 lists eight conditions, all of which must hold for an
 * interstitial to be shown. The rule is that a failed condition **drops** the
 * interstitial rather than deferring it to the next screen, because
 * deferring is the interruption pattern that produces uninstalls.
 *
 * This file is deliberately pure. It has no Android dependencies, no clock and
 * no repository, so every condition is directly testable and the decision
 * cannot drift from the specification.
 *
 * The caller supplies the state; this object only decides.
 */

/**
 * Maximum interstitials shown in one calendar day.
 *
 * Spec 15 §11.3 condition 8 requires a daily cap and §11.4 says interstitials
 * "count toward the interstitial cap", but no numeric value is given in `15`
 * or in `13` §9. The value used here matches the app's existing
 * `MAX_SENDS_PER_DAY = 3` budget, which is the same posture applied to a
 * more intrusive format. It is a single constant so it can be corrected in
 * one place.
 */
const val MAX_INTERSTITIALS_PER_DAY = 3

/**
 * Minimum minutes after a reminder was delivered before an interstitial may
 * be shown.
 *
 * Spec 15 §11.3 condition 5: "No reminder delivered within the last 30
 * minutes."
 *
 * This is deliberately not `POST_LOG_GRACE_MINUTES`, which is the grace
 * period after a period or symptom *log* and governs banner placement under
 * §11.2. The two windows are numerically the same and semantically
 * unrelated, so conflating them would make a future change to one silently
 * change the other.
 */
const val MIN_MINUTES_AFTER_REMINDER = 30

/**
 * Routes on which an interstitial must never be shown.
 *
 * Spec 15 §11.3 condition 6 refers to "the forbidden list" without
 * enumerating it. The enumeration lives in §11.2, "Never place an ad", and
 * the interstitials are triggered by a tab navigation, so the screen that
 * matters is the one the user is leaving.
 *
 * Each entry and its §11.2 justification:
 *
 * | route | §11.2 clause |
 * |---|---|
 * | `log` | "On the logging screen, or during an unsaved draft" |
 * | `home/fertility-detail` | "Adjacent to insight content or fertility detail being read" |
 * | `insights/cycle-trends` | "Adjacent to insight content … being read" |
 * | `insights/symptom-patterns` | "Adjacent to insight content … being read" |
 * | `settings`, `reminders`, `privacy`, `export` | never any detail screen (§11.2 table) |
 * | `onboarding` | "On the first launch, or in the first session" |
 */
val FORBIDDEN_INTERSTITIAL_ROUTES: Set<String> = setOf(
    "log",
    "home/fertility-detail",
    "insights/cycle-trends",
    "insights/symptom-patterns",
    "settings",
    "reminders",
    "privacy",
    "export",
    "onboarding",
)

/**
 * Why an interstitial was not shown.
 *
 * Every value corresponds to exactly one condition in §11.3, so a dropped
 * interstitial can always be explained. The ordering follows the
 * specification's own numbering.
 */
enum class InterstitialDropReason {
    /** Condition 1: the user has not completed onboarding. */
    ONBOARDING_INCOMPLETE,

    /** Condition 2: fewer than `MIN_SESSIONS_BEFORE_INTERSTITIAL` sessions. */
    TOO_FEW_SESSIONS,

    /** Condition 3: shown too recently. */
    TOO_SOON_SINCE_LAST,

    /** Condition 4: an unsaved draft exists somewhere in the app. */
    UNSAVED_DRAFT,

    /** Condition 5: a reminder was delivered recently. */
    RECENT_REMINDER_DELIVERY,

    /** Condition 6: the current screen is forbidden. */
    FORBIDDEN_SCREEN,

    /** Condition 7: ads consent has not been granted. */
    CONSENT_NOT_GRANTED,

    /** Condition 8: the daily cap has been reached. */
    DAILY_CAP_REACHED,
}

/** The outcome of evaluating the gate. */
sealed interface InterstitialGateDecision {
    /** The interstitial may be shown. */
    data object Show : InterstitialGateDecision

    /** The interstitial must be dropped, not deferred. */
    data class Drop(val reason: InterstitialDropReason) : InterstitialGateDecision
}

/**
 * Everything the gate needs to decide.
 *
 * @param onboardingComplete condition 1
 * @param completedSessions condition 2
 * @param millisSinceLastInterstitial condition 3, null when none has ever been shown
 * @param hasUnsavedDraft condition 4
 * @param millisSinceLastReminderDelivery condition 5, null when no reminder has been delivered
 * @param currentRoute condition 6
 * @param adsConsentGranted condition 7
 * @param interstitialsShownToday condition 8
 */
data class InterstitialGateState(
    val onboardingComplete: Boolean,
    val completedSessions: Int,
    val millisSinceLastInterstitial: Long?,
    val hasUnsavedDraft: Boolean,
    val millisSinceLastReminderDelivery: Long?,
    val currentRoute: String?,
    val adsConsentGranted: Boolean,
    val interstitialsShownToday: Int,
)

/**
 * Evaluates the eight conditions of spec 15 §11.3.
 *
 * The conditions are checked in the specification's order so that a dropped
 * interstitial always reports the first condition that failed, which makes
 * the behaviour predictable when several are false at once.
 */
object InterstitialGate {

    fun evaluate(state: InterstitialGateState): InterstitialGateDecision {
        if (!state.onboardingComplete) {
            return InterstitialGateDecision.Drop(InterstitialDropReason.ONBOARDING_INCOMPLETE)
        }
        if (state.completedSessions < MIN_SESSIONS_BEFORE_INTERSTITIAL) {
            return InterstitialGateDecision.Drop(InterstitialDropReason.TOO_FEW_SESSIONS)
        }
        if (state.millisSinceLastInterstitial != null &&
            state.millisSinceLastInterstitial < MIN_MINUTES_BETWEEN_INTERSTITIALS * MILLIS_PER_MINUTE
        ) {
            return InterstitialGateDecision.Drop(InterstitialDropReason.TOO_SOON_SINCE_LAST)
        }
        if (state.hasUnsavedDraft) {
            return InterstitialGateDecision.Drop(InterstitialDropReason.UNSAVED_DRAFT)
        }
        if (state.millisSinceLastReminderDelivery != null &&
            state.millisSinceLastReminderDelivery < MIN_MINUTES_AFTER_REMINDER * MILLIS_PER_MINUTE
        ) {
            return InterstitialGateDecision.Drop(InterstitialDropReason.RECENT_REMINDER_DELIVERY)
        }
        if (isForbiddenScreen(state.currentRoute)) {
            return InterstitialGateDecision.Drop(InterstitialDropReason.FORBIDDEN_SCREEN)
        }
        if (!state.adsConsentGranted) {
            return InterstitialGateDecision.Drop(InterstitialDropReason.CONSENT_NOT_GRANTED)
        }
        if (state.interstitialsShownToday >= MAX_INTERSTITIALS_PER_DAY) {
            return InterstitialGateDecision.Drop(InterstitialDropReason.DAILY_CAP_REACHED)
        }
        return InterstitialGateDecision.Show
    }

    /**
     * Whether a route is one on which an interstitial must not be shown.
     *
     * A null route is a transient state rather than a screen, and is treated
     * as forbidden so an interstitial can never appear over an unknown view.
     */
    fun isForbiddenScreen(route: String?): Boolean =
        route == null || route in FORBIDDEN_INTERSTITIAL_ROUTES

    private const val MILLIS_PER_MINUTE = 60_000L
}
