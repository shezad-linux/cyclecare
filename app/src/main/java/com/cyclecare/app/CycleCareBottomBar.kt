package com.cyclecare.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.cyclecare.core.designsystem.token.CycleCareSpace
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

@Composable
internal fun CycleCareTopBar(
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = CycleCareSpace.space4, vertical = CycleCareSpace.space2),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("app_bar_settings"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                )
            }
        }
    }
}

@Composable
internal fun CycleCareBottomNavigation(
    canRequestAds: Boolean,
    selectedTopLevelRoute: String?,
    onNavigate: (String) -> Unit,
) {
    val largeFontScale = LocalDensity.current.fontScale > 1.2f
    Column {
        // Spec 15 §11.2: Today and Insights only.
        if (canRequestAds && routeAllowsBanner(selectedTopLevelRoute)) {
            AdBanner(canRequestAds = true)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.18f)),
        ) {
            Column {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f),
                    thickness = 1.dp,
                )
                NavigationBar(
                    modifier = Modifier.navigationBarsPadding(),
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                ) {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedTopLevelRoute == destination.route,
                            onClick = { onNavigate(destination.route) },
                            modifier = Modifier.testTag(navTag(destination.route)),
                            icon = {
                                Icon(
                                    destination.icon,
                                    contentDescription = null,
                                    modifier = Modifier.testTag(navIconTag(destination.route)),
                                )
                            },
                            label = {
                                // A four-tab bar gives each label a quarter
                                // of the width, and at a large font scale a
                                // single word like "Calendar" no longer
                                // fits, so it broke mid-word into "Calen
                                // dar". That reads as a rendering fault
                                // rather than as a label.
                                //
                                // The label drops to the smaller role at
                                // large font scales, the way the metric grid
                                // stacks, and is held to a single line so
                                // it can never break mid-word again. The
                                // full text stays in the semantics, so a
                                // screen reader still announces the whole
                                // label.
                                Text(
                                    text = stringResource(destination.labelRes),
                                    style = if (largeFontScale) {
                                        MaterialTheme.typography.labelSmall
                                    } else {
                                        LocalTextStyle.current
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReminderBell(
    unreadReminderCount: Int,
) {
    BadgedBox(
        badge = {
            if (unreadReminderCount > 0) {
                Badge {
                    Text(unreadReminderCount.coerceAtMost(99).toString())
                }
            }
        },
    ) {
        Icon(Icons.Rounded.Notifications, contentDescription = null)
    }
}
