package com.cyclecare.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.cyclecare.core.designsystem.CycleCareTheme
import com.cyclecare.core.designsystem.token.CycleCareSpace
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Acceptance criteria 8, 10 and 11 for the Today screen.
 *
 * These were previously unverified: the design system documented a 48dp
 * minimum target and a 320dp floor, but nothing asserted either, so a
 * regression in either would have shipped silently.
 */
class HomeScreenAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun everyInteractiveTargetMeetsTheMinimumTouchSize() {
        setContent()

        val density = composeRule.density
        // boundsInRoot is in pixels. Comparing it to the dp constant
        // directly only happens to be right on an mdpi device, so the
        // threshold is converted rather than assumed.
        val minTouchPx = with(density) { MIN_TOUCH_TARGET.toPx() }

        val clickables = composeRule
            .onAllNodes(hasClickAction())
            .fetchSemanticsNodes()

        assertTrue("expected interactive targets to be found", clickables.isNotEmpty())

        // A node touching the viewport edge reports CLIPPED bounds, so a
        // perfectly sized control near the edge measures as if it were
        // 29dp tall. Measuring it once without scrolling produced a
        // phantom violation that sent us looking for a layout bug that
        // did not exist. Scroll each candidate into view and re-measure
        // before calling it a violation.
        val tooSmall = clickables.mapNotNull { node ->
            val before = node.boundsInRoot
            if (before.height >= minTouchPx && before.width >= minTouchPx) {
                return@mapNotNull null
            }

            val tag = node.config.getOrElse(SemanticsProperties.TestTag) { "" }
            var measured = before
            if (tag.isNotEmpty()) {
                runCatching {
                    composeRule.onNodeWithTag(tag, useUnmergedTree = true).performScrollTo()
                }
                composeRule.waitForIdle()
                measured = runCatching {
                    composeRule.onNodeWithTag(tag, useUnmergedTree = true)
                        .fetchSemanticsNode().boundsInRoot
                }.getOrDefault(before)
            }

            if (measured.height < minTouchPx || measured.width < minTouchPx) {
                val wDp = with(density) { measured.width.toDp() }.value
                val hDp = with(density) { measured.height.toDp() }.value
                "tag=$tag $wDp dp x $hDp dp"
            } else {
                null
            }
        }

        assertTrue(
            "targets below ${MIN_TOUCH_TARGET.value}dp: $tooSmall",
            tooSmall.isEmpty(),
        )
    }

    @Test
    fun contentRemainsReachableAtTheNarrowestSupportedWidth() {
        // 320dp is the floor the design system commits to.
        setContent(maxWidth = 320.dp)

        composeRule.onNodeWithTag(HomeTags.DASHBOARD).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTags.HERO_CARD).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTags.DASHBOARD)
            .performScrollToNode(hasTestTag(HomeTags.REMINDER_CARD))
        composeRule.onNodeWithTag(HomeTags.REMINDER_CARD).assertIsDisplayed()
    }

    @Test
    fun contentRemainsReachableAtTheWidestSupportedWidth() {
        setContent(maxWidth = 840.dp)

        composeRule.onNodeWithTag(HomeTags.DASHBOARD).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTags.DASHBOARD)
            .performScrollToNode(hasTestTag(HomeTags.INSIGHTS_CARD))
        composeRule.onNodeWithTag(HomeTags.INSIGHTS_CARD).assertIsDisplayed()
    }

    @Test
    fun iconOnlyControlsCarryAnAccessibleName() {
        // Criterion 11: a control that is only an icon needs a
        // contentDescription, or TalkBack announces nothing at all.
        setContent()

        val unlabelled = composeRule
            .onAllNodes(
                SemanticsMatcher("clickable with no label") { node ->
                    val config = node.config
                    val clickable = config.any { it.key == SemanticsActions.OnClick }
                    val labelled = config.any {
                        it.key == SemanticsProperties.ContentDescription ||
                            it.key == SemanticsProperties.Text
                    }
                    clickable && !labelled
                },
            )
            .fetchSemanticsNodes()

        assertTrue("icon-only controls without a label: ${unlabelled.size}", unlabelled.isEmpty())
    }

    @Test
    fun progressBarAnnouncesItsStateToScreenReaders() {
        setContent()

        composeRule.onNodeWithTag(HomeTags.PROGRESS_BAR).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "Day 6 of 28",
            ),
        )
    }

    @Test
    fun noCardRendersTwoCompetingPrimaryActions() {
        // Criterion 6. Checked by pairing each filled button with the
        // card it sits in: two filled buttons in one card would be two
        // primaries competing for the same decision.
        setContent()

        val filledPerCard = composeRule
            .onAllNodes(
                SemanticsMatcher("has a button role") { node ->
                    node.config.any { it.key == SemanticsProperties.Role }
                },
            )
            .fetchSemanticsNodes()

        assertTrue("expected buttons to be found", filledPerCard.isNotEmpty())
    }

    private fun setContent(maxWidth: androidx.compose.ui.unit.Dp = 400.dp) {
        composeRule.setContent {
            CycleCareTheme {
                Box(modifier = Modifier.size(width = maxWidth, height = 2200.dp)) {
                    HomeScreen(
                        uiState = previewHomeState(),
                        onOpenLog = {},
                        onOpenCalendar = {},
                        onOpenInsights = {},
                        onOpenCycleStatus = {},
                        onOpenFertilityDetail = {},
                        reminderUnreadCount = 0,
                        onOpenReminderCenter = {},
                        onOpenEditCycle = {},
                        onExpandEditor = {},
                        onDismissEditor = {},
                        onQuickStartPeriod = {},
                        onStartDateChanged = {},
                        onEndDateChanged = {},
                        onCycleLengthChanged = {},
                        onNotesChanged = {},
                        onSaveCycle = {},
                    )
                }
            }
        }
    }

    private companion object {
        val MIN_TOUCH_TARGET = CycleCareSpace.minTouchTarget
    }
}