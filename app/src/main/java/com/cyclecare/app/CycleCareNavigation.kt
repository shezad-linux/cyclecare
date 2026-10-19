package com.cyclecare.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

internal data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

/**
 * Four tabs, per spec 15. Settings is deliberately not one of them:
 * it is a destination reached from the app bar, so the bar carries no
 * more items than the content it frames. A fifth tab competed with
 * Today for attention and was the least-revisited screen in the app.
 */
internal val topLevelDestinations = listOf(
    TopLevelDestination("home", "Today", Icons.Rounded.Home),
    TopLevelDestination("calendar", "Calendar", Icons.Rounded.DateRange),
    TopLevelDestination("log", "Log", Icons.Rounded.Edit),
    TopLevelDestination("insights", "Insights", Icons.Rounded.Favorite),
)

/** Reachable from the app bar, not a tab. */
internal val topLevelRoutes = topLevelDestinations.map { it.route }.toSet()

/**
 * Routes allowed to carry an ad banner.
 *
 * Screen spec 15 §11.2 restricts the banner to Today and Insights. It must
 * never appear on Calendar, where an inserted strip shifts the month grid
 * out from under the finger that was just dragging it, nor on Log, where
 * an ad next to an unsaved draft is both a distraction and a Play policy
 * problem, nor on Settings or any detail screen.
 */
private val adBannerRoutes = setOf("home", "insights")

/**
 * Whether a route may carry an ad banner.
 *
 * Internal rather than private so the spec 15 §11.2 rule can be asserted
 * directly. The banner itself renders from live ad-network state, so its
 * presence is not observable from a UI test without an ad SDK stub.
 */
internal fun routeAllowsBanner(route: String?): Boolean = route in adBannerRoutes

internal fun navTag(route: String): String = "nav_$route"

internal fun NavHostController.navigateToTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

internal fun String?.toSelectedTopLevelRoute(): String? = when {
    this == null -> null
    this in topLevelRoutes -> this
    this.startsWith("home/") -> "home"
    this.startsWith("insights/") -> "insights"
    else -> null
}

internal const val SETTINGS_ROUTE = "settings"
