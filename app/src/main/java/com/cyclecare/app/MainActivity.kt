package com.cyclecare.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.cyclecare.core.designsystem.CycleCareTheme

class MainActivity : FragmentActivity() {
    private lateinit var container: AppContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        container = (application as CycleCareApplication).appContainer
        dispatchReminderIntent(intent)
        setContent {
            CycleCareTheme {
                CycleCareApp(container = container)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        dispatchReminderIntent(intent)
    }

    private fun dispatchReminderIntent(intent: android.content.Intent?) {
        intent?.toReminderNavigationRequest()?.let(container.reminderNavigationCoordinator::dispatch)
    }
}
