package com.minimal.carlauncher.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.data.AppInfo
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary
import com.minimal.carlauncher.util.BitmapHelper

/**
 * Left Bottom Dock: Hosts the All Apps trigger button, pinned custom apps with smooth horizontal
 * scrolling, and the Add (+) button to customize the launcher dock.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LeftBottomDock(
    pinnedApps: List<AppInfo>,
    onOpenAppDrawer: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onLongClickApp: (AppInfo) -> Unit,
    onAddApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dockGradient = Brush.horizontalGradient(
        colors = listOf(
            CarSurface,
            CarSurfaceVariant.copy(alpha = 0.5f),
            CarSurface
        )
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(dockGradient)
            .border(1.dp, CarBorder.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Drawer Toggle Button
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(CarSurfaceVariant.copy(alpha = 0.85f))
                .border(1.dp, CarBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .clickable { onOpenAppDrawer() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = "All Apps",
                tint = AccentCyan,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Apps",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Pinned Custom Apps + Add (+) Button
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            pinnedApps.forEach { app ->
                val imageBitmap = BitmapHelper.safeDrawableToImageBitmap(app.icon, 72, 72)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CarSurfaceVariant.copy(alpha = 0.8f))
                        .border(1.dp, CarBorder.copy(alpha = 0.45f), CircleShape)
                        .combinedClickable(
                            onClick = { onLaunchApp(app) },
                            onLongClick = { onLongClickApp(app) }
                        )
                        .padding(5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = app.label,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Text(
                            text = app.label.take(1).uppercase(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan
                        )
                    }
                }
            }

            // Add (+) Button to open app list and pin apps to bar
            if (pinnedApps.size < com.minimal.carlauncher.data.AppRepository.MAX_DOCK_APPS) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CarSurfaceVariant.copy(alpha = 0.8f))
                        .border(1.dp, AccentCyan.copy(alpha = 0.45f), CircleShape)
                        .clickable { onAddApp() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add App to Bar",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * Right Bottom Dock: Hosts vehicle utility tools including the About / Update checker and
 * Android System Settings shortcut.
 */
@Composable
fun RightBottomDock(
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    isUpdateAvailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dockGradient = Brush.horizontalGradient(
        colors = listOf(
            CarSurface,
            CarSurfaceVariant.copy(alpha = 0.5f),
            CarSurface
        )
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(dockGradient)
            .border(1.dp, CarBorder.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // About & Updates Button
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(CarSurfaceVariant.copy(alpha = 0.85f))
                .border(
                    1.dp,
                    if (isUpdateAvailable) AccentCyan.copy(alpha = 0.7f) else CarBorder.copy(alpha = 0.45f),
                    RoundedCornerShape(12.dp)
                )
                .clickable { onOpenAbout() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About & Updates",
                    tint = AccentCyan,
                    modifier = Modifier.size(19.dp)
                )
                if (isUpdateAvailable) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentCyan)
                            .border(1.5.dp, CarSurfaceVariant, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (isUpdateAvailable) "Update" else "About",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isUpdateAvailable) AccentCyan else TextPrimary,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        // System Settings Button
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(CarSurfaceVariant.copy(alpha = 0.85f))
                .border(1.dp, CarBorder.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                .clickable { onOpenSettings() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "System Settings",
                tint = TextSecondary,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Settings",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

/**
 * Backward compatibility wrapper for BottomDock.
 */
@Composable
fun BottomDock(
    pinnedApps: List<AppInfo>,
    onOpenAppDrawer: () -> Unit,
    onLaunchApp: (AppInfo) -> Unit,
    onLongClickApp: (AppInfo) -> Unit,
    onAddApp: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenSettings: () -> Unit,
    isUpdateAvailable: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LeftBottomDock(
            pinnedApps = pinnedApps,
            onOpenAppDrawer = onOpenAppDrawer,
            onLaunchApp = onLaunchApp,
            onLongClickApp = onLongClickApp,
            onAddApp = onAddApp,
            modifier = Modifier.weight(1f)
        )
        RightBottomDock(
            onOpenAbout = onOpenAbout,
            onOpenSettings = onOpenSettings,
            isUpdateAvailable = isUpdateAvailable,
            modifier = Modifier.weight(1f)
        )
    }
}
