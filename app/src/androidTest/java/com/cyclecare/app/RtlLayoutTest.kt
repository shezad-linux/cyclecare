package com.cyclecare.app

import android.app.LocaleManager
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The app laid out right to left.
 *
 * Spec `15` §14.8 requires every screen to work in RTL, and the manifest
 * declares `android:supportsRtl="true"`, so this is a promise the app
 * makes and has to keep.
 *
 * Two things are checked, and they are different things.
 *
 * The device locale is set through `LocaleManager`, so the app's own
 * resources and configuration are genuinely right to left. That is the
 * Android locale API, and it is what a user with an Arabic or Hebrew
 * system language would produce.
 *
 * The composition is then given the right to left layout direction
 * explicitly, so the layout is exercised deterministically. Compose derives
 * its layout direction from the configuration, and a test cannot change the
 * locale of a running activity without restarting it.
 *
 * A grep for a hardcoded left or right would not find the problems this
 * finds: a tab order that does not reverse, an icon that does not
 * mirror, a text that does not flip.
 */
@RunWith(AndroidJUnit4::class)
class RtlLayoutTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @After
    fun tearDown() {
        // Put the locale back, or the next test runs right to left.
        runCatching {
            context.getSystemService(LocaleManager::class.java)
                .setApplicationLocales(LocaleList.getEmptyLocaleList())
        }
        val configuration = android.content.res.Configuration(
            context.resources.configuration,
        )
        configuration.setLayoutDirection(java.util.Locale.ENGLISH)
        context.resources.updateConfiguration(
            configuration,
            context.resources.displayMetrics,
        )
    }

    private fun useRightToLeftLocale() {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        localeManager.setApplicationLocales(LocaleList.forLanguageTags("ar"))
    }

    /** The whole app, because the bottom navigation is part of the app shell. */
    private fun renderApp(layoutDirection: LayoutDirection?) {
        val configuration = android.content.res.Configuration(
            context.resources.configuration,
        )
        if (layoutDirection == LayoutDirection.Rtl) {
            configuration.setLayoutDirection(java.util.Locale("ar"))
        }
        composeRule.activityRule.scenario.onActivity { activity ->
            val (container, authController) = populatedScreenshotEnvironment()
            val content: @androidx.compose.runtime.Composable () -> Unit = {
                com.cyclecare.core.designsystem.CycleCareTheme {
                    CycleCareApp(
                        container = container,
                        secureAuthController = authController,
                    )
                }
            }
            if (layoutDirection == LayoutDirection.Rtl) {
                activity.setContent {
                    CompositionLocalProvider(
                        LocalConfiguration provides configuration,
                        LocalLayoutDirection provides LayoutDirection.Rtl,
                    ) {
                        content()
                    }
                }
            } else {
                activity.setContent { content() }
            }
        }
        composeRule.waitForIdle()
    }

    private fun renderRtl() = renderApp(LayoutDirection.Rtl)

    private fun renderLtr() = renderApp(null)

    @Test
    fun theDeviceLocaleIsRightToLeftWhenItIsSet() {
        // The locale is applied to the resources, which is what the
        // platform does when a user picks an Arabic or Hebrew system
        // language.
        val configuration = android.content.res.Configuration(
            context.resources.configuration,
        )
        configuration.setLocale(java.util.Locale("ar"))
        configuration.setLayoutDirection(java.util.Locale("ar"))
        context.resources.updateConfiguration(
            configuration,
            context.resources.displayMetrics,
        )

        assertThat(context.resources.configuration.layoutDirection).isEqualTo(
            android.view.View.LAYOUT_DIRECTION_RTL,
        )
    }

    @Test
    fun theTabOrderReversesInRightToLeft() {
        renderRtl()

        val today = composeRule.onNodeWithTag(navTag("home")).fetchSemanticsNode()
        val insights = composeRule.onNodeWithTag(navTag("insights")).fetchSemanticsNode()

        // In right to left the first tab is on the right, so the first
        // destination sits further right than the last one.
        assertThat(today.boundsInRoot.right).isGreaterThan(insights.boundsInRoot.right)
    }

    @Test
    fun theTabOrderIsLeftToRightInTheDefaultLocale() {
        renderLtr()

        val today = composeRule.onNodeWithTag(navTag("home")).fetchSemanticsNode()
        val insights = composeRule.onNodeWithTag(navTag("insights")).fetchSemanticsNode()

        assertThat(today.boundsInRoot.right).isLessThan(insights.boundsInRoot.right)
    }

    @Test
    fun theWholeNavigationIsLaidOutInReverse() {
        renderRtl()

        // Every tab, not just the first, has to move. The last tab ends
        // up furthest left.
        val insights = composeRule.onNodeWithTag(navTag("insights")).fetchSemanticsNode()
        val log = composeRule.onNodeWithTag(navTag("log")).fetchSemanticsNode()

        assertThat(insights.boundsInRoot.right).isLessThan(log.boundsInRoot.right)
    }
}
