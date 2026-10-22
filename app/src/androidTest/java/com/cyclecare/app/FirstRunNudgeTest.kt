package com.cyclecare.app

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.domain.FirstRunNudgeRepository
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one-time nudge towards Settings.
 *
 * Spec `15` §1.3.4: a one-time, dismissible nudge on first run to
 * Settings after onboarding, never repeated, never a badge.
 */
@RunWith(AndroidJUnit4::class)
class FirstRunNudgeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * The real repository, against a throwaway database, so the flag's
     * durability is exercised rather than mocked.
     */
    private fun realRepository(): FirstRunNudgeRepository {
        val database = androidx.room.Room
            .inMemoryDatabaseBuilder(context, com.cyclecare.core.database.CycleCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        return com.cyclecare.core.data.RoomFirstRunNudgeRepository(database)
    }

    @Test
    fun theNudgeIsOfferedBeforeItHasBeenShown() = runBlocking {
        val repository = realRepository()

        assertThat(repository.shouldShowFirstRunNudge()).isTrue()
    }

    @Test
    fun theNudgeIsNotOfferedAgainOnceItHasBeenShown() = runBlocking {
        val repository = realRepository()

        repository.markFirstRunNudgeShown()

        assertThat(repository.shouldShowFirstRunNudge()).isFalse()
    }

    @Test
    fun theFlagSurvivesTheRepositoryBeingRecreated() = runBlocking {
        // A durable flag, so the nudge does not come back after a process
        // restart. The in-memory database stands in for the on-disk one here,
        // and the point being checked is that the value is read back rather
        // than held in the repository.
        val database = androidx.room.Room
            .inMemoryDatabaseBuilder(context, com.cyclecare.core.database.CycleCareDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val first = com.cyclecare.core.data.RoomFirstRunNudgeRepository(database)
        first.markFirstRunNudgeShown()

        val second = com.cyclecare.core.data.RoomFirstRunNudgeRepository(database)

        assertThat(second.shouldShowFirstRunNudge()).isFalse()
    }

    @Test
    fun theNudgeDialogOffersSettingsAndADismissal() {
        // The dialog has to be dismissible, and it has to point at
        // Settings rather than being a badge.
        val repository = FakeFirstRunNudgeRepository(shown = false)
        composeRule.setContent {

            CycleCareTheme {
                FirstRunNudge(
                    repository = repository,
                    onOpenSettings = {},
                )
            }
        
}
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Open Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Not now").assertIsDisplayed()
    }

    @Test
    fun dismissingTheNudgeHidesIt() {
        val repository = FakeFirstRunNudgeRepository(shown = false)
        composeRule.setContent {

            CycleCareTheme {
                FirstRunNudge(
                    repository = repository,
                    onOpenSettings = {},
                )
            }
        
}
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Not now").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Open Settings").assertDoesNotExist()
        // The flag was set as the nudge appeared, not when it was
        // dismissed, so it does not come back.
        assertThat(repository.showCount).isEqualTo(1)
    }
}
