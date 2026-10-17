package com.cyclecare.app

import android.app.Activity
import android.content.Context
import com.cyclecare.core.domain.AppLogger
import com.cyclecare.core.domain.NoOpAppLogger
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GoogleMobileAdsConsentManager(
    context: Context,
    private val logger: AppLogger = NoOpAppLogger,
) {
    private val appContext = context.applicationContext
    private val consentInformation = UserMessagingPlatform.getConsentInformation(appContext)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _canRequestAds = MutableStateFlow(false)
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _isPrivacyOptionsRequired = MutableStateFlow(false)
    val isPrivacyOptionsRequired: StateFlow<Boolean> = _isPrivacyOptionsRequired.asStateFlow()

    private var consentInfoUpdatedThisSession = false

    fun requestConsentInfoUpdate(activity: Activity) {
        syncState()
        if (consentInfoUpdatedThisSession) {
            maybeInitializeMobileAds()
            return
        }

        consentInfoUpdatedThisSession = true
        consentInformation.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                syncState()
                maybeInitializeMobileAds()
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) {
                        logWarning(event = "consent_form_dismissed_with_error", error = error)
                    }
                    syncState()
                    maybeInitializeMobileAds()
                }
            },
            { error ->
                logWarning(event = "consent_info_update_failed", error = error)
                syncState()
                maybeInitializeMobileAds()
            },
        )

        syncState()
        maybeInitializeMobileAds()
    }

    fun showPrivacyOptionsForm(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) {
                logWarning(event = "privacy_options_form_failed", error = error)
            }
            syncState()
            maybeInitializeMobileAds()
        }
    }

    private fun syncState() {
        _canRequestAds.value = consentInformation.canRequestAds()
        _isPrivacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    private fun maybeInitializeMobileAds() {
        if (!_canRequestAds.value) return
        if (mobileAdsInitialized) return

        synchronized(mobileAdsInitializationLock) {
            if (mobileAdsInitialized) return
            mobileAdsInitialized = true
        }

        logger.info(
            tag = "CycleCareAdsConsent",
            event = "mobile_ads_initializing",
        )
        backgroundScope.launch {
            MobileAds.initialize(appContext) {}
        }
    }

    private fun logWarning(
        event: String,
        error: FormError,
    ) {
        logger.warn(
            tag = "CycleCareAdsConsent",
            event = event,
            attributes = mapOf(
                "code" to error.errorCode.toString(),
                "message" to error.message,
            ),
        )
    }

    private companion object {
        private val mobileAdsInitializationLock = Any()

        @Volatile
        private var mobileAdsInitialized = false
    }
}
