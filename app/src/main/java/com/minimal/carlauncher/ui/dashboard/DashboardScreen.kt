package com.minimal.carlauncher.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.minimal.carlauncher.ui.dialogs.AboutDialog
import com.minimal.carlauncher.ui.dialogs.AddressSearchDialog
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
    val speedKmH = if (speedUnit == com.minimal.carlauncher.service.SpeedUnit.KMH) {
        currentSpeed.toFloat()
    } else {
        currentSpeed * 1.60934f
    }
    val bearing by viewModel.speedometer.bearing.collectAsState()
    val cardinalDirection by viewModel.speedometer.cardinalDirection.collectAsState()
    val currentLocation by viewModel.speedometer.currentLocation.collectAsState()
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
    val radioStation by viewModel.radioStation.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    // Dialog States
    val selectedDockApp by viewModel.selectedDockAppForAction.collectAsState()
    val isReplacePickerOpen by viewModel.isReplacePickerOpen.collectAsState()
    val isAddDockPickerOpen by viewModel.isAddDockPickerOpen.collectAsState()
    val selectedDrawerApp by viewModel.selectedDrawerAppForAction.collectAsState()
    val isNavPickerOpen by viewModel.isNavPickerOpen.collectAsState()
    val isMusicPickerOpen by viewModel.isMusicPickerOpen.collectAsState()
    val isDvrPickerOpen by viewModel.isDvrPickerOpen.collectAsState()

    val currentVersion = viewModel.currentVersion
    val isAboutDialogOpen by viewModel.isAboutDialogOpen.collectAsState()
    val isCheckingUpdate by viewModel.isCheckingUpdate.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateProgress by viewModel.updateDownloadProgress.collectAsState()

    // Minimap Navigation State
    val isSearchDialogOpen by viewModel.isAddressSearchOpen.collectAsState()
    val activeRoute by viewModel.activeRoute.collectAsState()
    val isNavigating by viewModel.isNavigating.collectAsState()
    val isCalculatingRoute by viewModel.isCalculatingRoute.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(CarBg)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Column: Telemetry (Clock, Speedometer) + Left Bottom Dock (Apps, Pinned, +)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ClockWidget(
                    time = currentTime,
                    seconds = currentSeconds,
                    date = currentDate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                SpeedometerWidget(
                    speed = currentSpeed,
                    unit = speedUnit,
                    isGpsActive = isGpsActive,
                    onToggleUnit = { viewModel.toggleSpeedUnit() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.3f)
                )

                LeftBottomDock(
                    pinnedApps = pinnedApps,
                    onOpenAppDrawer = { viewModel.openAppDrawer() },
                    onLaunchApp = { app -> viewModel.launchApp(app) },
                    onLongClickApp = { app -> viewModel.onDockAppLongClick(app) },
                    onAddApp = { viewModel.openAddDockPicker() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Center Column: Master Circular Moving Map Portal (FULL VERTICAL SPACE AVAILABLE)
            CircularMapPortal(
                location = currentLocation,
                bearing = bearing,
                cardinalDirection = cardinalDirection,
                speedKmH = speedKmH,
                isGpsActive = isGpsActive,
                activeRoute = activeRoute,
                isNavigating = isNavigating,
                isCalculatingRoute = isCalculatingRoute,
                onStartNavigation = { dest ->
                    viewModel.startNavigationTo(dest.latitude, dest.longitude)
                },
                onStopNavigation = { viewModel.stopNavigation() },
                onOpenSearch = { viewModel.openAddressSearch() },
                onOpenNavigation = { viewModel.launchNavigation() },
                isMapDarkMode = isDarkMode,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
            )

            // Right Column: Quick-Launch Tiles + Right Bottom Dock (About, Settings)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickLaunchCards(
                    dvrApp = dvrApp,
                    zlinkLabel = zlinkApp?.label ?: "ZLink",
                    navApp = navigationApp,
                    musicApp = musicApp,
                    radioStation = radioStation,
                    onLaunchDvr = { viewModel.launchDvr() },
                    onLongClickDvr = { viewModel.openDvrPicker() },
                    onLaunchZLink = { viewModel.launchZLink() },
                    onLaunchNavigation = { viewModel.launchNavigation() },
                    onLaunchMusic = { viewModel.launchMusic() },
                    onLongClickNavigation = { viewModel.openNavPicker() },
                    onLongClickMusic = { viewModel.openMusicPicker() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                RightBottomDock(
                    onOpenAbout = { viewModel.openAboutDialog() },
                    onOpenSettings = { viewModel.launchSettings() },
                    isUpdateAvailable = updateInfo?.isUpdateAvailable == true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
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

        // App Picker Dialog (when + is clicked to add to bottom bar)
        AppPickerDialog(
            isOpen = isAddDockPickerOpen,
            apps = allApps.filter { app -> pinnedApps.none { it.packageName == app.packageName } },
            title = "Add App to Bottom Bar",
            onAppSelected = { newApp -> viewModel.addDockApp(newApp) },
            onDismiss = { viewModel.closeAddDockPicker() }
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
            onDismiss = { viewModel.dismissAboutDialog() },
            isDarkMode = isDarkMode,
            onToggleDarkMode = { viewModel.toggleDarkMode() }
        )

        // Address Search Autocomplete Dialog
        AddressSearchDialog(
            isOpen = isSearchDialogOpen,
            currentLat = currentLocation?.latitude ?: 37.9838,
            currentLon = currentLocation?.longitude ?: 23.7275,
            onSelectDestination = { lat, lon, name ->
                viewModel.startNavigationTo(lat, lon, name)
            },
            onDismiss = { viewModel.closeAddressSearch() }
        )
    }
}
