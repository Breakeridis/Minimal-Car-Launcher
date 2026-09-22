package com.minimal.carlauncher.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimal.carlauncher.ui.dialogs.AboutDialog
import com.minimal.carlauncher.ui.dialogs.AppPickerDialog
import com.minimal.carlauncher.ui.dialogs.DockActionDialog
import com.minimal.carlauncher.ui.dialogs.DrawerActionDialog
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
    val bearing by viewModel.speedometer.bearing.collectAsState()
    val cardinalDirection by viewModel.speedometer.cardinalDirection.collectAsState()
    val isGpsActive by viewModel.speedometer.isGpsActive.collectAsState()

    val allApps by viewModel.allApps.collectAsState()
    val pinnedApps by viewModel.pinnedApps.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isDrawerOpen by viewModel.isAppDrawerOpen.collectAsState()
    val zlinkApp by viewModel.zlinkApp.collectAsState()
    val navigationApp by viewModel.navigationApp.collectAsState()
    val musicApp by viewModel.musicApp.collectAsState()
    val dvrApp by viewModel.dvrApp.collectAsState()

    // Dialog States
    val selectedDockApp by viewModel.selectedDockAppForAction.collectAsState()
    val isReplacePickerOpen by viewModel.isReplacePickerOpen.collectAsState()
    val selectedDrawerApp by viewModel.selectedDrawerAppForAction.collectAsState()
    val isNavPickerOpen by viewModel.isNavPickerOpen.collectAsState()
    val isMusicPickerOpen by viewModel.isMusicPickerOpen.collectAsState()
    val isDvrPickerOpen by viewModel.isDvrPickerOpen.collectAsState()

    val currentVersion = viewModel.currentVersion
    val isAboutDialogOpen by viewModel.isAboutDialogOpen.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateProgress by viewModel.updateDownloadProgress.collectAsState()

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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        SpeedometerWidget(
                            speed = currentSpeed,
                            unit = speedUnit,
                            isGpsActive = isGpsActive,
                            onToggleUnit = { viewModel.toggleSpeedUnit() },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        CompassWidget(
                            bearing = bearing,
                            cardinalDirection = cardinalDirection,
                            isGpsActive = isGpsActive,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }
                }

                // Right Column: Hero Quick-Launch Cards (DVR, ZLink, Nav, Music)
                QuickLaunchCards(
                    dvrApp = dvrApp,
                    zlinkLabel = zlinkApp?.label ?: "ZLink",
                    navApp = navigationApp,
                    musicApp = musicApp,
                    onLaunchDvr = { viewModel.launchDvr() },
                    onLongClickDvr = { viewModel.openDvrPicker() },
                    onLaunchZLink = { viewModel.launchZLink() },
                    onLaunchNavigation = { viewModel.launchNavigation() },
                    onLaunchMusic = { viewModel.launchMusic() },
                    onLongClickNavigation = { viewModel.openNavPicker() },
                    onLongClickMusic = { viewModel.openMusicPicker() },
                    modifier = Modifier.weight(2f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Persistent Dock
            BottomDock(
                pinnedApps = pinnedApps,
                onOpenAppDrawer = { viewModel.openAppDrawer() },
                onLaunchApp = { app -> viewModel.launchApp(app) },
                onLongClickApp = { app -> viewModel.onDockAppLongClick(app) },
                onOpenAbout = { viewModel.openAboutDialog() },
                onOpenSettings = { viewModel.launchSettings() },
                isUpdateAvailable = updateInfo?.isUpdateAvailable == true
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

        // App Picker Dialog (when Replace is chosen for dock)
        AppPickerDialog(
            isOpen = isReplacePickerOpen,
            apps = allApps,
            title = "Choose App to Place in Bottom Bar",
            onAppSelected = { newApp -> viewModel.replaceDockAppWith(newApp) },
            onDismiss = { viewModel.closeReplacePicker() }
        )

        // Navigation App Picker Dialog (when Navigation tile is long-pressed)
        AppPickerDialog(
            isOpen = isNavPickerOpen,
            apps = allApps,
            title = "Select Default Navigation App",
            onAppSelected = { app -> viewModel.selectNavigationApp(app) },
            onDismiss = { viewModel.closeNavPicker() }
        )

        // Music App Picker Dialog (when Music tile is long-pressed)
        AppPickerDialog(
            isOpen = isMusicPickerOpen,
            apps = allApps,
            title = "Select Default Music App",
            onAppSelected = { app -> viewModel.selectMusicApp(app) },
            onDismiss = { viewModel.closeMusicPicker() }
        )

        // Dashcam / DVR App Picker Dialog (when Dashcam card is long-pressed)
        AppPickerDialog(
            isOpen = isDvrPickerOpen,
            apps = allApps,
            title = "Select Dashcam / DVR App",
            onAppSelected = { app -> viewModel.selectDvrApp(app) },
            onDismiss = { viewModel.closeDvrPicker() }
        )

        // Drawer Long-Press Action Dialog (Add to Bottom Bar)
        DrawerActionDialog(
            app = selectedDrawerApp,
            onAddToDock = { selectedDrawerApp?.let { viewModel.pinDrawerAppToDock(it) } },
            onDismiss = { viewModel.dismissDrawerActionDialog() }
        )

        // About App & Software Update Dialog
        AboutDialog(
            isOpen = isAboutDialogOpen,
            currentVersion = currentVersion,
            updateInfo = updateInfo,
            isCheckingUpdate = isCheckingUpdate,
            downloadProgress = updateProgress,
            onCheckUpdate = { viewModel.checkForUpdates(isManualCheck = true) },
            onInstall = { viewModel.startDownloadAndInstall() },
            onDismiss = { viewModel.dismissAboutDialog() }
        )
    }
}
