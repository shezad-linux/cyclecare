package com.cyclecare.app

import android.app.Activity
import com.cyclecare.core.domain.ProfileRepository
import com.cyclecare.core.domain.ReminderEventsRepository
import com.cyclecare.core.domain.ads.DraftTracker
import com.cyclecare.core.domain.ads.InterstitialDropReason
import com.cyclecare.core.domain.ads.MAX_INTERSTITIALS_PER_DAY
import com.cyclecare.core.domain.ads.InterstitialGate
import com.cyclecare.core.domain.ads.InterstitialGateDecision
import com.cyclecare.core.domain.ads.InterstitialGateState
import com.cyclecare.core.domain.ads.InterstitialStatsRepository
import com.cyclecare.core.domain.ads.MIN_MINUTES_AFTER_REMINDER
import com.cyclecare.core.domain.time.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Decides whether an interstitial may be shown, and shows it if so.
 *
 * Spec 15 §11.3 sets eight conditions, all of which must hold. The decision
 * itself is pure and lives in [InterstitialGate]; this class gathers the
 * state the gate needs and applies the consequences.
 *
 * The rule that matters most here is "dropped, not deferred". When a
 * condition fails, the request is abandoned. It is not queued for the next
 * screen, because deferring an interstitial to the next screen is exactly
 * the interruption pattern that produces uninstalls.
 *
 * The caller supplies [onFinished] and it is invoked exactly once, whether
 * the ad was shown, could not be shown, or was dropped by the gate. That
 * keeps the navigation the user asked for from being swallowed.
 */
class InterstitialCoordinator(
    private val adManager: InterstitialAdManager,
    private val statsRepository: InterstitialStatsRepository,
    private val reminderEventsRepository: ReminderEventsRepository,
    private val profileRepository: ProfileRepository,
    private val draftTracker: DraftTracker,
    private val timeProvider: TimeProvider,
    /**
     * The daily interstitial cap.
     *
     * A product decision rather than a specification requirement, so it is
     * configurable here. The other seven conditions are fixed by spec 15
     * §11.3 and cannot be relaxed.
     */
    private val dailyCap: Int = MAX_INTERSTITIALS_PER_DAY,
    /**
     * Scope used to record the impression once the ad has actually been
     * displayed. The ad callback is not a suspend context, so the write has
     * to be launched rather than awaited.
     */
    private val scope: CoroutineScope,
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
    /**
     * Called with the reason whenever the gate drops a request. Used for
     * diagnostics; the product behaviour is the same either way.
     */
    private val onDropped: (InterstitialDropReason) -> Unit = {},
) {

    /**
     * The last time an interstitial was shown, mirrored in memory.
     *
     * The persisted timestamp is written from a coroutine, because the ad
     * SDK's dismiss callback is not a suspend context. Without this mirror a
     * second request arriving before that write lands would see a null
     * timestamp and pass condition 3, so two interstitials could be shown
     * inside the ten minute window. The mirror is updated synchronously
     * when the ad is shown; the persisted value covers process restarts.
     */
    private var lastShownAtEpochMillis: Long? = null

    /**
     * Evaluates the gate and, if it allows, shows the interstitial.
     *
     * @param currentRoute the route the user is leaving. The raw route is
     *   used rather than the selected tab, because the forbidden list
     *   contains detail routes such as `home/fertility-detail` that map
     *   onto a tab that is itself allowed.
     * @param adsConsentGranted whether the user has granted ad consent.
     * @param onFinished invoked exactly once when the request is complete.
     */
    suspend fun requestInterstitial(
        activity: Activity,
        currentRoute: String?,
        adsConsentGranted: Boolean,
        onFinished: () -> Unit,
    ) {
        val now = timeProvider.nowEpochMillis()
        val decision = InterstitialGate.evaluate(
            gateState(
                now = now,
                currentRoute = currentRoute,
                adsConsentGranted = adsConsentGranted,
            ),
            dailyCap = dailyCap,
        )

        when (decision) {
            is InterstitialGateDecision.Drop -> {
                onDropped(decision.reason)
                onFinished()
            }

            InterstitialGateDecision.Show -> {
                adManager.showAd(activity) { shown ->
                    if (shown) {
                        // Updated synchronously so a second request cannot
                        // slip past condition 3 before the write lands.
                        lastShownAtEpochMillis = now
                        scope.launch {
                            statsRepository.recordInterstitialShown(now, dayKey(now))
                        }
                    }
                    onFinished()
                }
            }
        }
    }

    private suspend fun gateState(
        now: Long,
        currentRoute: String?,
        adsConsentGranted: Boolean,
    ): InterstitialGateState {
        val lastShown = lastShownAtEpochMillis ?: statsRepository.lastInterstitialShownAtEpochMillis()
        val lastDelivery = reminderEventsRepository.latestDeliveryAtSince(
            sinceEpochMillis = now - MIN_MINUTES_AFTER_REMINDER * MILLIS_PER_MINUTE,
        )
        return InterstitialGateState(
            onboardingComplete = profileRepository.observeProfile().first() != null,
            completedSessions = statsRepository.completedSessions(),
            millisSinceLastInterstitial = lastShown?.let { now - it },
            hasUnsavedDraft = draftTracker.hasUnsavedDraft.value,
            millisSinceLastReminderDelivery = lastDelivery?.let { now - it },
            currentRoute = currentRoute,
            adsConsentGranted = adsConsentGranted,
            interstitialsShownToday = statsRepository.interstitialsShownOn(dayKey(now)),
        )
    }

    /**
     * The local calendar day an instant falls on.
     *
     * The daily counter resets at local midnight rather than at a UTC
     * boundary, which would put the reset at an inconvenient hour for most
     * of the world.
     */
    private fun dayKey(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis)
            .atZone(zone())
            .toLocalDate()
            .toString()

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
