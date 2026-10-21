package com.cyclecare.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bottom navigation bar.
 *
 * Two things are asserted here that no other test covers.
 *
 * First, that only the selected tab carries a selection indicator. The
 * indicator is a filled shape painted behind the icon, so it is a visual
 * property and has to be asserted visually: a semantics assertion can tell
 * us which tab is selected, but not whether an unselected tab is painting a
 * pill behind its icon.
 *
 * Second, that the selection state moves with the tap, so the indicator
 * cannot be left behind on the tab the user has navigated away from.
 *
 * The reading is taken just outside the icon's own bounds. The icon is
 * centred in the tab and the indicator is a wider pill centred on the same
 * point, so a point a little to the side of the icon is inside the pill
 * but not on the glyph. Using the icon's bounds rather than a fixed
 * fraction of the tab keeps the reading the same on a 320dp phone and on a
 * 1000dp tablet.
 */
@RunWith(AndroidJUnit4::class)
class BottomNavigationIndicatorTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderApp() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val noOpInterstitial = InterstitialAdManager(
                context = activity.applicationContext,
                adLoader = object : InterstitialAdLoader {
                    override fun load(
                        context: Context,
                        adUnitId: String,
                        adRequest: AdRequest,
                        callback: InterstitialAdLoadCallback,
                    ) = Unit
                },
            )
            val (container, authController) = populatedScreenshotEnvironment()
            activity.setContent {
                CycleCareTheme {
                    CycleCareApp(
                        container = container,
                        secureAuthController = authController,
                        interstitialAdManager = noOpInterstitial,
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    /**
     * The colour just to the left of a tab's icon, where the selection
     * indicator would be.
     */
    private fun indicatorAreaColour(route: String): Int {
        // The icon carries no content description, so it is not a
        // semantics merging boundary and its node only exists in the
        // unmerged tree.
        val iconNode = composeRule
            .onNodeWithTag(navIconTag(route), useUnmergedTree = true)
            .fetchSemanticsNode()
        val itemNode = composeRule.onNodeWithTag(navTag(route)).fetchSemanticsNode()
        val bitmap = composeRule.onNodeWithTag(navTag(route)).captureToImage().asAndroidBitmap()

        val iconBounds = iconNode.boundsInRoot
        val itemBounds = itemNode.boundsInRoot

        // Three quarters of the icon's width to the left of its centre.
        // The icon is 24dp and the indicator is 64dp across, both centred
        // on the same point, so this lands six dp clear of the glyph and
        // well inside the indicator. Measuring it against the icon's own
        // width keeps the reading identical at any density.
        val offsetFromIconCentre = -(iconBounds.width * 0.75f).toInt()
        val x = (iconBounds.center.x - itemBounds.left + offsetFromIconCentre)
            .toInt()
            .coerceIn(0, bitmap.width - 1)
        val y = (iconBounds.center.y - itemBounds.top)
            .toInt()
            .coerceIn(0, bitmap.height - 1)
        return bitmap.getPixel(x, y)
    }

    /** The largest per-channel difference between two colours. */
    private fun channelDistance(a: Int, b: Int): Int =
        maxOf(
            kotlin.math.abs(((a shr 24) and 0xFF) - ((b shr 24) and 0xFF)),
            kotlin.math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
            kotlin.math.abs((a and 0xFF) - (b and 0xFF)),
        )

    @Test
    fun onlyTheSelectedTabShowsASelectionIndicator() {
        // The app opens on Today and no tab has been tapped, so nothing is
        // mid-ripple and the reading is of the resting state.
        renderApp()

        val selected = indicatorAreaColour("home")
        val unselected = listOf("calendar", "log", "insights").map { indicatorAreaColour(it) }

        // Every unselected tab paints the bar's own background in the area
        // the indicator would occupy, so they all read the same.
        unselected.forEach { colour ->
            assertThat(channelDistance(colour, unselected.first())).isEqualTo(0)
        }
        // And none of them paints what the selected tab paints, which is
        // the indicator.
        unselected.forEach { colour ->
            assertThat(channelDistance(colour, selected)).isGreaterThan(6)
        }
    }

    @Test
    fun theSelectedTabShowsADistinctIndicator() {
        renderApp()

        val selected = indicatorAreaColour("home")
        val unselected = indicatorAreaColour("calendar")

        // The selected tab's indicator must be visibly different from the
        // unselected background, otherwise nothing marks the current tab.
        assertThat(channelDistance(selected, unselected)).isGreaterThan(6)
    }

    @Test
    fun tappingATabMovesTheSelectionIndicatorWithIt() {
        renderApp()

        val before = indicatorAreaColour("home")
        composeRule.openTabAndAwait("insights")
        composeRule.waitForIdle()

        // The tab that was selected is no longer painting an indicator. It
        // was not the one tapped, so no ripple is drawn over it, and it
        // now reads the same as the other unselected tabs.
        val home = indicatorAreaColour("home")
        val calendar = indicatorAreaColour("calendar")
        assertThat(channelDistance(home, calendar)).isEqualTo(0)
        // And the tapped tab carries the indicator now.
        assertThat(channelDistance(indicatorAreaColour("insights"), home)).isGreaterThan(6)
        assertThat(channelDistance(before, home)).isGreaterThan(6)
    }
}
