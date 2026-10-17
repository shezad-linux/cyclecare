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
        manager.showAd(FakeActivity()) {
            callbackInvoked = true
        }

        assertThat(callbackInvoked).isTrue()
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
        manager.showAd(FakeActivity()) {
            callbackInvoked = true
        }

        // Ad show should trigger dismissed callback and then onAdClosedOrNotReady lambda
        assertThat(callbackInvoked).isTrue()
        // AdManager should automatically set interstitialAd to null and preload the next ad
        assertThat(manager.isLoaded()).isTrue() // loaded because it preloaded successfully immediately
        assertThat(loader.loadCount).isEqualTo(2) // 1 initial load + 1 preload
    }

    @Test
    fun showAd_whenCooldownActive_proceedsImmediatelyWithoutShowingAd() {
        val loader = FakeInterstitialAdLoader()
        val manager = InterstitialAdManager(
            context = FakeContext(),
            adLoader = loader,
            minIntervalMillis = 60000L,
            adUnitIdProvider = { "test_unit_id" }
        )

        // Load and show first ad (sets lastShownTimeMillis)
        manager.loadAd()
        var callback1Invoked = false
        manager.showAd(FakeActivity()) {
            callback1Invoked = true
        }
        assertThat(callback1Invoked).isTrue()
        assertThat(loader.loadCount).isEqualTo(2) // 1 initial load + 1 preload

        // Try to show again immediately (within 60 seconds)
        var callback2Invoked = false
        manager.showAd(FakeActivity()) {
            callback2Invoked = true
        }
        
        // Should proceed immediately without invoking show or requesting another load
        assertThat(callback2Invoked).isTrue()
        assertThat(loader.loadCount).isEqualTo(2) 
    }
}
