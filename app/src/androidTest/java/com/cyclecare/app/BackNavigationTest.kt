package com.cyclecare.app

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.designsystem.CycleCareTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The back gesture.
 *
 * Two behaviours are specified.
 *
 * Spec `15` §1.4: "System back from a tab returns to `today`". The app
 * has four top-level tabs, and back from any of them goes to Today rather
 * than closing the app, which is the behaviour the specification asks for
 * and the one the app had before the redesign.
 *
 * Spec `15` §1.3.4: the notification-permission gate "must not trap
 * back/switch-access users". A handler that swallowed back with an empty
 * body left someone with no way out of the app at all. The gate now lets
 * the system back through, so the activity closes and the gate is waiting
 * when the user comes back.
 */
@RunWith(AndroidJUnit4::class)
class BackNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Whether the activity would finish on a back press.
     *
     * The gate is a full-screen replacement for the app's content rather
     * than a navigation destination, so the check is on the activity's own
     * back stack rather than on a NavController.
     */
    private fun activityWouldFinishOnBack(): Boolean {
        var finished = false
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.addCallback(
                activity,
                object : androidx.activity.OnBackPressedCallback(false) {
                    override fun handleOnBackPressed() = Unit
                },
            )
            // Ask the activity what the system would do. The dispatcher
            // reports whether it has a callback that will handle the
            // gesture, and if nothing handles it the activity finishes.
            val handled = activity.onBackPressedDispatcher.hasEnabledCallbacks()
            finished = !handled
        }
        return finished
    }

    @Test
    fun theNotificationGateDoesNotTrapBack() {
        // The gate is shown on its own, outside the nav host, so it is
        // rendered directly here.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CycleCareTheme {
                    NotificationPermissionRequiredGate(
                        actionLabel = "Allow notifications",
                        onResolveNotificationAccess = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Nothing in the gate handles the back gesture, so the system
        // closes the activity. The user is not trapped.
        assertThat(activityWouldFinishOnBack()).isTrue()
    }

    @Test
    fun theGateIsShownAndOffersAnAction() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CycleCareTheme {
                    NotificationPermissionRequiredGate(
                        actionLabel = "Allow notifications",
                        onResolveNotificationAccess = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(NotificationPermissionTags.REQUIRED_GATE).assertExists()
        composeRule.onNodeWithTag(NotificationPermissionTags.REQUIRED_ACTION).assertExists()
    }
}
