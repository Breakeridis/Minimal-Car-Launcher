package com.minimal.carlauncher.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minimal.carlauncher.data.AppInfo
import com.minimal.carlauncher.data.AppRepository
import com.minimal.carlauncher.service.SpeedUnit
import com.minimal.carlauncher.service.SpeedometerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AppRepository(application)
    val speedometer = SpeedometerManager(application)

    // Applications State
    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val allApps: StateFlow<List<AppInfo>> = _allApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = combine(_allApps, _searchQuery) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { it.label.contains(query, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _zlinkApp = MutableStateFlow<AppInfo?>(null)
    val zlinkApp: StateFlow<AppInfo?> = _zlinkApp.asStateFlow()

    private val _navigationApp = MutableStateFlow<AppInfo?>(null)
    val navigationApp: StateFlow<AppInfo?> = _navigationApp.asStateFlow()

    private val _musicApp = MutableStateFlow<AppInfo?>(null)
    val musicApp: StateFlow<AppInfo?> = _musicApp.asStateFlow()

    private val _isAppDrawerOpen = MutableStateFlow(false)
    val isAppDrawerOpen: StateFlow<Boolean> = _isAppDrawerOpen.asStateFlow()

    // Clock and Date State
    private val _currentTime = MutableStateFlow("")
    val currentTime: StateFlow<String> = _currentTime.asStateFlow()

    private val _currentSeconds = MutableStateFlow("")
    val currentSeconds: StateFlow<String> = _currentSeconds.asStateFlow()

    private val _currentDate = MutableStateFlow("")
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    init {
        loadApps()
        startClockTicker()
    }

    fun loadApps() {
        viewModelScope.launch {
            val apps = repository.getInstalledApps()
            _allApps.value = apps

            // Auto-detect key apps
            _zlinkApp.value = apps.firstOrNull { it.isZLink }
            _navigationApp.value = apps.firstOrNull { it.isNavigation }
            _musicApp.value = apps.firstOrNull { it.isMusic }
        }
    }

    private fun startClockTicker() {
        viewModelScope.launch {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val secondsFormat = SimpleDateFormat("ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

            while (isActive) {
                val now = Date()
                _currentTime.value = timeFormat.format(now)
                _currentSeconds.value = secondsFormat.format(now)
                _currentDate.value = dateFormat.format(now)
                delay(1000L)
            }
        }
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun openAppDrawer() {
        _searchQuery.value = ""
        _isAppDrawerOpen.value = true
    }

    fun closeAppDrawer() {
        _isAppDrawerOpen.value = false
        _searchQuery.value = ""
    }

    fun launchApp(app: AppInfo) {
        repository.launchApp(app)
        closeAppDrawer()
    }

    fun launchZLink() {
        val zlink = _zlinkApp.value
        if (zlink != null) {
            repository.launchApp(zlink)
        } else {
            // Fallback: try common ZLink package names directly
            val launched = repository.launchPackage("com.zjinnova.zlink") ||
                    repository.launchPackage("com.zjinnova.zlinkx") ||
                    repository.launchPackage("com.xyauto.zlink") ||
                    repository.launchPackage("com.carletter.zlink") ||
                    repository.launchPackage("com.suding.speedplay")
            if (!launched) {
                // Open app drawer so user can locate their mirror app
                openAppDrawer()
            }
        }
    }

    fun launchNavigation() {
        val nav = _navigationApp.value
        if (nav != null) {
            repository.launchApp(nav)
        } else {
            // Attempt generic Google Maps or open drawer
            if (!repository.launchPackage("com.google.android.apps.maps")) {
                openAppDrawer()
            }
        }
    }

    fun launchMusic() {
        val music = _musicApp.value
        if (music != null) {
            repository.launchApp(music)
        } else {
            if (!repository.launchPackage("com.spotify.music")) {
                openAppDrawer()
            }
        }
    }

    fun launchSettings() {
        repository.launchSettings()
    }

    fun toggleSpeedUnit() {
        speedometer.toggleUnit()
    }

    override fun onCleared() {
        super.onCleared()
        speedometer.stopTracking()
    }
}
