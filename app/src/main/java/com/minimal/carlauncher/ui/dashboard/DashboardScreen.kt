package com.minimal.carlauncher.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimal.carlauncher.ui.dialogs.AppPickerDialog
import com.minimal.carlauncher.ui.dialogs.DockActionDialog
import com.minimal.carlauncher.ui.dialogs.DrawerActionDialog
import com.minimal.carlauncher.ui.dialogs.UpdateDialog
import com.minimal.carlauncher.ui.drawer.AppDrawerDialog
import com.minimal.carlauncher.ui.theme.CarBg
import com.minimal.carlauncher.ui.viewmodel.LauncherViewModel

@Composable
fun DashboardScreen(
    viewModel: LauncherViewModel,
    modifier: Modifier = Modifier
) {
    val currentTime by viewModel.currentTime.collectAsState()
    val currentSeconds by viewModel.currentSeconds.collectAsState()
    val currentDate by viewModel.currentDate.collectAsState()

    val currentSpeed by viewModel.speedometer.currentSpeed.collectAsState()
    val speedUnit by viewModel.speedometer.unit.collectAsState()
    val isGpsActive by viewModel.speedometer.isGpsActive.collectAsState()

    val allApps by viewModel.allApps.collectAsState()
    val pinnedApps by viewModel.pinnedApps.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isDrawerOpen by viewModel.isAppDrawerOpen.collectAsState()
    val zlinkApp by viewModel.zlinkApp.collectAsState()

    // Dialog States
    val selectedDockApp by viewModel.selectedDockAppForAction.collectAsState()
    val isReplacePickerOpen by viewModel.isReplacePickerOpen.collectAsState()
    val selectedDrawerApp by viewModel.selectedDrawerAppForAction.collectAsState()

    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateProgress by viewModel.updateDownloadProgress.collectAsState()
    val isUpdateDialogOpen by viewModel.isUpdateDialogOpen.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarBg)
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Main Top & Middle Section (Left: Telemetry, Right: Hero Launch Cards)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Clock + Speedometer
                Column(
                    modifier = Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ClockWidget(
                        time = currentTime,
                        seconds = currentSeconds,
                        date = currentDate,
                        modifier = Modifier.fillMaxWidth()
                    )

                    SpeedometerWidget(
                        speed = currentSpeed,
                        unit = speedUnit,
                        isGpsActive = isGpsActive,
                        onToggleUnit = { viewModel.toggleSpeedUnit() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                // Right Column: Hero Quick-Launch Cards (ZLink, Nav, Music, Settings)
                QuickLaunchCards(
                    zlinkLabel = zlinkApp?.label ?: "ZLink",
                    onLaunchZLink = { viewModel.launchZLink() },
                    onLaunchNavigation = { viewModel.launchNavigation() },
                    onLaunchMusic = { viewModel.launchMusic() },
                    onLaunchSettings = { viewModel.launchSettings() },
                    modifier = Modifier.weight(2f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Persistent Dock
            BottomDock(
                pinnedApps = pinnedApps,
                isCheckingUpdate = isCheckingUpdate,
                onOpenAppDrawer = { viewModel.openAppDrawer() },
                onLaunchApp = { app -> viewModel.launchApp(app) },
                onLongClickApp = { app -> viewModel.onDockAppLongClick(app) },
                onCheckUpdate = { viewModel.checkForUpdates(isManualCheck = true) },
                onOpenSettings = { viewModel.launchSettings() }
            )
        }

        // Overlay All Apps Drawer
        AppDrawerDialog(
            isOpen = isDrawerOpen,
            apps = filteredApps,
            searchQuery = searchQuery,
            onSearchChange = { viewModel.onSearchQueryChange(it) },
            onAppClick = { app -> viewModel.launchApp(app) },
            onAppLongClick = { app -> viewModel.onDrawerAppLongClick(app) },
            onClose = { viewModel.closeAppDrawer() }
        )

        // Dock Long-Press Action Dialog (Remove or Replace)
        DockActionDialog(
            app = selectedDockApp,
            onReplace = { viewModel.openReplacePicker() },
            onRemove = { selectedDockApp?.let { viewModel.removeDockApp(it) } },
            onDismiss = { viewModel.dismissDockActionDialog() }
        )

        // App Picker Dialog (when Replace is chosen)
        AppPickerDialog(
            isOpen = isReplacePickerOpen,
            apps = allApps,
            onAppSelected = { newApp -> viewModel.replaceDockAppWith(newApp) },
            onDismiss = { viewModel.closeReplacePicker() }
        )

        // Drawer Long-Press Action Dialog (Add to Bottom Bar)
        DrawerActionDialog(
            app = selectedDrawerApp,
            onAddToDock = { selectedDrawerApp?.let { viewModel.pinDrawerAppToDock(it) } },
            onDismiss = { viewModel.dismissDrawerActionDialog() }
        )

        // In-App GitHub Update Dialog
        UpdateDialog(
            isOpen = isUpdateDialogOpen,
            updateInfo = updateInfo,
            downloadProgress = updateProgress,
            onInstall = { viewModel.startDownloadAndInstall() },
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }
}
