package com.cyclecare.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.isVisible
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView

private const val PROD_BANNER_AD_UNIT_ID = "ca-app-pub-2324135355814969/5000575657"
private const val PROD_NATIVE_AD_UNIT_ID = "ca-app-pub-2324135355814969/5327878717"
private const val PROD_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-2324135355814969/3684915559"
private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/9214589741"
private const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

private enum class AdLoadState {
    Loading,
    Loaded,
    Failed,
}

@Composable
fun AdBanner(
    modifier: Modifier = Modifier,
    canRequestAds: Boolean,
) {
    if (!canRequestAds) return

    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val adWidthDp = maxWidth.value.toInt().coerceAtLeast(1)
        val adSize = remember(context, adWidthDp) {
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, adWidthDp)
        }
        val adHeight = remember(context, density, adSize) {
            with(density) { adSize.getHeightInPixels(context).toDp() }
        }
        var loadState by remember(adWidthDp) { mutableStateOf(AdLoadState.Loading) }
        val adView = remember(context, adWidthDp) {
            AdView(context).apply {
                adUnitId = bannerAdUnitId()
                setAdSize(adSize)
            }
        }

        DisposableEffect(adView) {
            adView.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    loadState = AdLoadState.Loaded
                }

                override fun onAdFailedToLoad(adError: LoadAdError) {
                    loadState = AdLoadState.Failed
                }
            }
            onDispose {
                (adView.parent as? ViewGroup)?.removeView(adView)
                adView.destroy()
            }
        }

        LaunchedEffect(adView, canRequestAds) {
            loadState = AdLoadState.Loading
            adView.loadAd(AdRequest.Builder().build())
        }

        if (loadState == AdLoadState.Failed) {
            return@BoxWithConstraints
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(adHeight),
            contentAlignment = Alignment.Center,
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = {
                    (adView.parent as? ViewGroup)?.removeView(adView)
                    adView
                },
            )
            if (loadState == AdLoadState.Loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
fun NativeAdvancedAd(
    modifier: Modifier = Modifier,
    canRequestAds: Boolean,
) {
    if (!canRequestAds) return

    val context = LocalContext.current
    var loadState by remember { mutableStateOf(AdLoadState.Loading) }
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    LaunchedEffect(context, canRequestAds) {
        loadState = AdLoadState.Loading
        AdLoader.Builder(context, nativeAdUnitId())
            .forNativeAd { loadedAd ->
                nativeAd?.destroy()
                nativeAd = loadedAd
                loadState = AdLoadState.Loaded
            }
            .withNativeAdOptions(
                NativeAdOptions.Builder().build(),
            )
            .withAdListener(
                object : AdListener() {
                    override fun onAdFailedToLoad(adError: LoadAdError) {
                        nativeAd?.destroy()
                        nativeAd = null
                        loadState = AdLoadState.Failed
                    }
                },
            )
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    when (loadState) {
        AdLoadState.Failed -> Unit
        AdLoadState.Loading -> {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.height(28.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
        AdLoadState.Loaded -> {
            val currentNativeAd = nativeAd ?: return
            AndroidView(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                factory = { createNativeAdView(it) },
                update = { adView ->
                    populateNativeAdView(adView, currentNativeAd)
                },
            )
        }
    }
}

private fun bannerAdUnitId(): String = if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT_ID else PROD_BANNER_AD_UNIT_ID

private fun nativeAdUnitId(): String = if (BuildConfig.DEBUG) TEST_NATIVE_AD_UNIT_ID else PROD_NATIVE_AD_UNIT_ID

private fun interstitialAdUnitId(): String = if (BuildConfig.DEBUG) TEST_INTERSTITIAL_AD_UNIT_ID else PROD_INTERSTITIAL_AD_UNIT_ID

private data class NativeAdAssetViews(
    val attribution: TextView,
    val headline: TextView,
    val body: TextView,
    val media: MediaView,
    val callToAction: Button,
    val icon: ImageView,
)

private fun createNativeAdView(context: Context): NativeAdView {
    val fallbackColors = androidx.compose.material3.lightColorScheme()
    val surfaceColor = fallbackColors.surface.toArgb()
    val surfaceVariantColor = fallbackColors.surfaceVariant.toArgb()
    val onSurfaceColor = fallbackColors.onSurface.toArgb()
    val onSurfaceVariantColor = fallbackColors.onSurfaceVariant.toArgb()
    val primaryColor = fallbackColors.primary.toArgb()
    val density = context.resources.displayMetrics.density

    fun Int.dp(): Int = (this * density).toInt()

    val adView = NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        background = GradientDrawable().apply {
            setColor(surfaceColor)
            cornerRadius = 28.dp().toFloat()
        }
        clipToPadding = false
    }

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(18.dp(), 18.dp(), 18.dp(), 18.dp())
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val topRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    val attribution = TextView(context).apply {
        text = "Ad"
        setTextColor(primaryColor)
        setTypeface(typeface, Typeface.BOLD)
        textSize = 12f
        background = GradientDrawable().apply {
            setColor(surfaceVariantColor)
            cornerRadius = 14.dp().toFloat()
        }
        setPadding(10.dp(), 4.dp(), 10.dp(), 4.dp())
    }

    val icon = ImageView(context).apply {
        layoutParams = LinearLayout.LayoutParams(40.dp(), 40.dp()).apply {
            marginStart = 12.dp()
        }
        visibility = ImageView.GONE
    }

    topRow.addView(attribution)
    topRow.addView(icon)

    val headline = TextView(context).apply {
        setTextColor(onSurfaceColor)
        setTypeface(typeface, Typeface.BOLD)
        textSize = 18f
        setPadding(0, 14.dp(), 0, 6.dp())
    }

    val body = TextView(context).apply {
        setTextColor(onSurfaceVariantColor)
        textSize = 14f
        setPadding(0, 0, 0, 14.dp())
    }

    val media = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            180.dp(),
        ).apply {
            bottomMargin = 14.dp()
        }
        background = GradientDrawable().apply {
            setColor(surfaceVariantColor)
            cornerRadius = 22.dp().toFloat()
        }
        clipToOutline = true
    }

    val callToAction = Button(context).apply {
        isAllCaps = false
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    container.addView(topRow)
    container.addView(headline)
    container.addView(body)
    container.addView(media)
    container.addView(callToAction)
    adView.addView(container)

    adView.tag = NativeAdAssetViews(
        attribution = attribution,
        headline = headline,
        body = body,
        media = media,
        callToAction = callToAction,
        icon = icon,
    )
    adView.headlineView = headline
    adView.bodyView = body
    adView.mediaView = media
    adView.callToActionView = callToAction
    adView.iconView = icon
    return adView
}

private fun populateNativeAdView(
    adView: NativeAdView,
    nativeAd: NativeAd,
) {
    val assets = adView.tag as NativeAdAssetViews

    assets.headline.text = nativeAd.headline

    assets.body.text = nativeAd.body.orEmpty()
    assets.body.isVisible = !nativeAd.body.isNullOrBlank()

    assets.callToAction.text = nativeAd.callToAction.orEmpty()
    assets.callToAction.isVisible = !nativeAd.callToAction.isNullOrBlank()

    assets.icon.setImageDrawable(nativeAd.icon?.drawable)
    assets.icon.isVisible = nativeAd.icon?.drawable != null

    assets.media.mediaContent = nativeAd.mediaContent
    assets.media.isVisible = nativeAd.mediaContent != null

    adView.setNativeAd(nativeAd)
}

interface InterstitialAdLoader {
    fun load(
        context: Context,
        adUnitId: String,
        adRequest: AdRequest,
        callback: InterstitialAdLoadCallback
    )
}

class DefaultInterstitialAdLoader : InterstitialAdLoader {
    override fun load(
        context: Context,
        adUnitId: String,
        adRequest: AdRequest,
        callback: InterstitialAdLoadCallback
    ) {
        InterstitialAd.load(context, adUnitId, adRequest, callback)
    }
}

class InterstitialAdManager(
    private val context: Context,
    private val adLoader: InterstitialAdLoader = DefaultInterstitialAdLoader(),
    private val minIntervalMillis: Long = 60000L, // 60 seconds default cooldown
    private val adUnitIdProvider: () -> String = { interstitialAdUnitId() }
) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false
    private var lastShownTimeMillis = 0L

    fun loadAd() {
        if (interstitialAd != null || isLoading) return
        isLoading = true
        val adRequest = AdRequest.Builder().build()
        adLoader.load(
            context,
            adUnitIdProvider(),
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            interstitialAd = null
                            loadAd()
                        }

                        override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                            interstitialAd = null
                            loadAd()
                        }
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    fun showAd(activity: android.app.Activity, onAdClosedOrNotReady: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastShownTimeMillis < minIntervalMillis) {
            onAdClosedOrNotReady()
            return
        }

        val ad = interstitialAd
        if (ad != null) {
            val oldCallback = ad.fullScreenContentCallback
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    lastShownTimeMillis = System.currentTimeMillis()
                    oldCallback?.onAdDismissedFullScreenContent()
                    onAdClosedOrNotReady()
                }

                override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                    oldCallback?.onAdFailedToShowFullScreenContent(error)
                    onAdClosedOrNotReady()
                }
            }
            ad.show(activity)
        } else {
            loadAd()
            onAdClosedOrNotReady()
        }
    }

    fun isLoaded(): Boolean = interstitialAd != null
}
