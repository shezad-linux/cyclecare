package com.cyclecare.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 15 §11.2:
 *
 *   Banner | Today and Insights only. Never Calendar (it would shift the
 *   grid), never Log, never Settings, never any detail screen
 *
 * Acceptance criterion §14.3 repeats it as "No ad on Calendar, Log,
 * Settings or any detail screen".
 *
 * The banner is rendered inside the shared bottom navigation, so before
 * this rule existed it appeared on every tab. This test is the guard
 * against that regression.
 */
class AdBannerPlacementTest {

    @Test
    fun bannerIsAllowedOnToday() {
        assertTrue(routeAllowsBanner("home"))
    }

    @Test
    fun bannerIsAllowedOnInsights() {
        assertTrue(routeAllowsBanner("insights"))
    }

    @Test
    fun bannerIsForbiddenOnCalendar() {
        // An inserted banner strip shifts the month grid out from under
        // the finger that was dragging it.
        assertFalse(routeAllowsBanner("calendar"))
    }

    @Test
    fun bannerIsForbiddenOnLog() {
        // An ad beside an unsaved draft is both a distraction and a Play
        // policy problem.
        assertFalse(routeAllowsBanner("log"))
    }

    @Test
    fun bannerIsForbiddenOnSettings() {
        assertFalse(routeAllowsBanner("settings"))
    }

    @Test
    fun bannerIsForbiddenOnDetailRoutes() {
        val detailRoutes = listOf(
            "home_cycle_status",
            "home_fertility_detail",
            "privacy",
            "export",
            "reminders",
            "onboarding",
        )
        detailRoutes.forEach { route ->
            assertFalse("banner must not appear on $route", routeAllowsBanner(route))
        }
    }

    @Test
    fun bannerIsForbiddenWhenNoTabIsSelected() {
        // Detail screens and transient routes leave no top-level tab
        // selected. The banner must not leak in behind them.
        assertFalse(routeAllowsBanner(null))
    }
}