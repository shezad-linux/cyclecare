package com.cyclecare.app

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.ResponseInfo
import com.google.android.gms.ads.OnPaidEventListener
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InterstitialAdManagerTest {

    private class FakeInterstitialAd(private val unitId: String) : InterstitialAd() {
        private var callback: FullScreenContentCallback? = null

        override fun getAdUnitId(): String = unitId
        
        @Suppress("UNCHECKED_CAST", "CAST_NEVER_SUCCEEDS")
        override fun getResponseInfo(): ResponseInfo = null as ResponseInfo
        
        override fun setFullScreenContentCallback(callback: FullScreenContentCallback?) {
            this.callback = callback
        }
        override fun getFullScreenContentCallback(): FullScreenContentCallback? = callback
        override fun setImmersiveMode(enabled: Boolean) {}
        override fun setOnPaidEventListener(listener: OnPaidEventListener?) {}
        override fun getOnPaidEventListener(): OnPaidEventListener? = null
        
        override fun getPlacementId(): Long = 0L
        override fun setPlacementId(p0: Long) {}
        
        override fun show(activity: Activity) {
            callback?.onAdDismissedFullScreenContent()
        }
        
        fun simulateFailedToShow(error: com.google.android.gms.ads.AdError) {
            callback?.onAdFailedToShowFullScreenContent(error)
        }
    }

    private class FakeInterstitialAdLoader : InterstitialAdLoader {
        var lastLoadedUnitId: String? = null
        var shouldSucceed: Boolean = true
        var mockAd: FakeInterstitialAd? = null
        var loadCount: Int = 0

        override fun load(
            context: Context,
            adUnitId: String,
            adRequest: AdRequest,
            callback: InterstitialAdLoadCallback
        ) {
            loadCount++
            lastLoadedUnitId = adUnitId
            if (shouldSucceed) {
                val ad = FakeInterstitialAd(adUnitId)
                mockAd = ad
                callback.onAdLoaded(ad)
            } else {
                mockAd = null
                // In standard unit tests, LoadAdError has a constructor we can call or subclass, 
                // but since LoadAdError extends AdError which doesn't need complex mocks, 
                // we can construct one:
                val error = LoadAdError(1, "Failed to load", "domain", null, null)
                callback.onAdFailedToLoad(error)
            }
        }
    }

    private class FakeContext : ContextWrapper(null)
    private class FakeActivity : Activity()

    @Test
    fun loadAd_whenSuccessful_updatesStateToLoaded() {
        val loader = FakeInterstitialAdLoader()
        val manager = InterstitialAdManager(
            context = FakeContext(),
            adLoader = loader,
            adUnitIdProvider = { "test_unit_id" }
        )

        assertThat(manager.isLoaded()).isFalse()
        manager.loadAd()
        assertThat(manager.isLoaded()).isTrue()
        assertThat(loader.lastLoadedUnitId).isEqualTo("test_unit_id")
        assertThat(loader.loadCount).isEqualTo(1)
    }

    @Test
    fun loadAd_whenFailed_doesNotUpdateStateToLoaded() {
        val loader = FakeInterstitialAdLoader().apply { shouldSucceed = false }
        val manager = InterstitialAdManager(
            context = FakeContext(),
            adLoader = loader,
            adUnitIdProvider = { "test_unit_id" }
        )

        assertThat(manager.isLoaded()).isFalse()
        manager.loadAd()
        assertThat(manager.isLoaded()).isFalse()
    }

    @Test
    fun showAd_whenNotLoaded_invokesCallbackAndLoadsAd() {
        val loader = FakeInterstitialAdLoader()
        val manager = InterstitialAdManager(
            context = FakeContext(),
            adLoader = loader,
            adUnitIdProvider = { "test_unit_id" }
        )

        var callbackInvoked = false
        var adWasShown = true
        manager.showAd(FakeActivity()) { shown ->
            callbackInvoked = true
            adWasShown = shown
        }

        assertThat(callbackInvoked).isTrue()
        // No ad was available, so nothing was shown and no impression may
        // be recorded for it.
        assertThat(adWasShown).isFalse()
        assertThat(loader.loadCount).isEqualTo(1)
    }

    @Test
    fun showAd_whenLoaded_showsAdInvokesCallbackAndTriggersPreload() {
        val loader = FakeInterstitialAdLoader()
        val manager = InterstitialAdManager(
            context = FakeContext(),
            adLoader = loader,
            adUnitIdProvider = { "test_unit_id" }
        )

        // Preload
        manager.loadAd()
        assertThat(manager.isLoaded()).isTrue()
        assertThat(loader.loadCount).isEqualTo(1)

        var callbackInvoked = false
        var adWasShown = false
        manager.showAd(FakeActivity()) { shown ->
            callbackInvoked = true
            adWasShown = shown
        }

        // Ad show should trigger dismissed callback and then onAdClosedOrNotReady lambda
        assertThat(callbackInvoked).isTrue()
        // The ad was displayed and dismissed, so an impression is recorded.
        assertThat(adWasShown).isTrue()
        // AdManager should automatically set interstitialAd to null and preload the next ad
        assertThat(manager.isLoaded()).isTrue() // loaded because it preloaded successfully immediately
        assertThat(loader.loadCount).isEqualTo(2) // 1 initial load + 1 preload
    }

}
