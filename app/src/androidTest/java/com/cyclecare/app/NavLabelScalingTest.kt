package com.cyclecare.app

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The bottom navigation labels at a large font scale.
 *
 * A four-tab bar gives each label a quarter of the width, and a single
 * word like "Calendar" does not fit in that space at 1.5 times the user's
 * font size. It used to break mid-word, which rendered as "Calen dar" and
 * read as a fault rather than as a label.
 *
 * The label is now held to a single line, so it can never break mid-word
 * again, and it drops to the smaller text role at large font scales so
 * that it fits outright on the widths where that is possible. On the
 * narrowest supported width it truncates instead, which is the honest
 * outcome: the full label stays in the semantics either way, so a screen
 * reader still announces "Calendar" in full.
 */
@RunWith(AndroidJUnit4::class)
class NavLabelScalingTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun renderApp(fontScale: Float) {
        val noOpInterstitial = InterstitialAdManager(
            context = composeRule.activity.applicationContext,
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
        composeRule.setContent {

            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale),
            ) {
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

    /** The labels, in tab order. */
    private val labelTexts = listOf("Today", "Calendar", "Log", "Insights")

    @Test
    fun noLabelWrapsOntoASecondLineAtALargeFontScale() {
        renderApp(fontScale = 1.5f)

        val heights = labelTexts.map { text ->
            // The tab item merges its descendants' semantics, so a merged
            // match would give the height of the whole tab rather than of
            // the text. The unmerged tree has the Text node itself.
            composeRule.onNodeWithText(text, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .height
        }
        val shortest = heights.min()
        // A wrapped label is roughly twice the height of a single line. The
        // shortest label is always one line, so it is the reference.
        heights.forEachIndexed { index, height ->
            assertWithMessage("label %s wrapped", labelTexts[index])
                .that(height)
                .isLessThan(shortest * 1.5f)
        }
    }

    @Test
    fun noLabelWrapsAtTheDefaultFontScale() {
        renderApp(fontScale = 1.0f)

        val heights = labelTexts.map { text ->
            // The tab item merges its descendants' semantics, so a merged
            // match would give the height of the whole tab rather than of
            // the text. The unmerged tree has the Text node itself.
            composeRule.onNodeWithText(text, useUnmergedTree = true)
                .fetchSemanticsNode()
                .boundsInRoot
                .height
        }
        val shortest = heights.min()
        heights.forEachIndexed { index, height ->
            assertWithMessage("label %s wrapped", labelTexts[index])
                .that(height)
                .isLessThan(shortest * 1.5f)
        }
    }

    @Test
    fun theFullLabelIsAnnouncedEvenWhenItIsTruncated() {
        renderApp(fontScale = 1.5f)

        // The semantics carry the whole word, so a screen reader is not
        // given the truncated form.
        labelTexts.forEach { text ->
            composeRule.onNodeWithText(text).assertExists()
        }
    }

    @Test
    fun theFourTabsRemainSelectableAtALargeFontScale() {
        renderApp(fontScale = 1.5f)

        composeRule.onNodeWithTag(navTag("home")).assertIsSelected()

        composeRule.openTabAndAwait("insights")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(navTag("insights")).assertIsSelected()
        // The other tabs are still there, they are just not the current one.
        composeRule.onNodeWithTag(navTag("home")).assertIsNotSelected()
        composeRule.onNodeWithTag(navTag("calendar")).assertIsNotSelected()
        composeRule.onNodeWithTag(navTag("log")).assertIsNotSelected()
    }
}
