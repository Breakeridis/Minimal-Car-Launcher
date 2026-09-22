package com.minimal.carlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager
    private val prefs: SharedPreferences =
        context.getSharedPreferences("car_launcher_dock_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_PINNED_PACKAGES = "key_pinned_dock_packages"
        private const val KEY_CUSTOM_NAV_PACKAGE = "key_custom_nav_package"
        private const val KEY_CUSTOM_MUSIC_PACKAGE = "key_custom_music_package"
        private const val KEY_CUSTOM_DVR_PACKAGE = "key_custom_dvr_package"
        private const val KEY_DVR_AUTOSTART_ENABLED = "key_dvr_autostart_enabled"
        const val MAX_DOCK_APPS = 6
    }

    // Known ZLink package signatures found on Android head units
    private val zlinkPackages = setOf(
        "com.zjinnova.zlink",
        "com.zjinnova.zlinkx",
        "com.xyauto.zlink",
        "com.carletter.zlink",
        "com.syu.zlink",
        "com.suding.speedplay",
        "cn.manstep.phonemirrorbox",
        "com.autonavi.amapauto"
    )

    // Known navigation apps
    private val navigationPackages = setOf(
        "com.google.android.apps.maps",
        "com.waze",
        "com.here.app.maps",
        "com.sygic.aura",
        "com.navfree.android.OSMALL"
    )

    // Known music players and radio apps
    private val musicPackages = setOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.amazon.mp3",
        "com.apple.android.music",
        "deezer.android.app",
        "com.soundcloud.android",
        "com.pandora.android",
        "com.nwd.radio",
        "com.navimods.radio",
        "com.navimods.radio_free",
        "com.android.fmradio",
        "com.softwinner.radio",
        "com.allwinner.radio",
        "com.ts.radio",
        "com.microntek.radio"
    )

    // Known dashcam / DVR packages found on Android head units
    private val dvrPackages = setOf(
        "com.car.dvr",
        "com.android.dvr",
        "com.xyauto.dvr",
        "com.syu.dvr",
        "com.topway.dvr",
        "com.teyes.dvr",
        "com.hcn.dvr",
        "com.autonavi.dvr",
        "com.tchip.weatherstation",
        "com.anket.dvr",
        "com.mediatek.dvr",
        "com.allwinner.dvr",
        "com.cardvr",
        "com.camera.dvr"
    )

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(intent, 0)
            }

            val selfPackage = context.packageName

            val apps = resolveInfos.mapNotNull { resolveInfo ->
                try {
                    val activityInfo = resolveInfo.activityInfo ?: return@mapNotNull null
                    val pkg = activityInfo.packageName ?: return@mapNotNull null
                    if (pkg == selfPackage) return@mapNotNull null

                    val label = try {
                        resolveInfo.loadLabel(packageManager)?.toString() ?: pkg
                    } catch (e: Throwable) {
                        pkg
                    }

                    val icon = try {
                        resolveInfo.loadIcon(packageManager)
                    } catch (e: Throwable) {
                        null
                    }

                    val activityName = activityInfo.name ?: ""

                    val isZLink = isZLinkPackage(pkg, label)
                    val isNav = isNavPackage(pkg, label)
                    val isMusic = isMusicPackage(pkg, label)
                    val isDvr = isDvrPackage(pkg, label)

                    AppInfo(
                        label = label,
                        packageName = pkg,
                        activityName = activityName,
                        icon = icon,
                        isZLink = isZLink,
                        isNavigation = isNav,
                        isMusic = isMusic,
                        isDvr = isDvr
                    )
                } catch (e: Throwable) {
                    null
                }
            }

            apps.sortedBy { it.label.lowercase() }
        } catch (e: Throwable) {
            e.printStackTrace()
            emptyList()
        }
    }

    // --- Dock Persistence ---

    fun getPinnedPackageNames(): List<String> {
        val jsonString = prefs.getString(KEY_PINNED_PACKAGES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val list = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getString(i))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun savePinnedPackageNames(packages: List<String>) {
        val jsonArray = JSONArray(packages)
        prefs.edit().putString(KEY_PINNED_PACKAGES, jsonArray.toString()).apply()
    }

    fun getPinnedApps(allApps: List<AppInfo>): List<AppInfo> {
        val saved = getPinnedPackageNames()
        if (saved.isNotEmpty()) {
            val appMap = allApps.associateBy { it.packageName }
            return saved.mapNotNull { appMap[it] }
        }

        // First launch default: pick up to MAX_DOCK_APPS
        val defaults = mutableListOf<AppInfo>()
        val nav = allApps.firstOrNull { it.isNavigation }
        val music = allApps.firstOrNull { it.isMusic }
        val zlink = allApps.firstOrNull { it.isZLink }

        if (nav != null) defaults.add(nav)
        if (music != null && !defaults.contains(music)) defaults.add(music)
        if (zlink != null && !defaults.contains(zlink)) defaults.add(zlink)

        for (app in allApps) {
            if (defaults.size >= MAX_DOCK_APPS) break
            if (!defaults.contains(app)) {
                defaults.add(app)
            }
        }

        savePinnedPackageNames(defaults.map { it.packageName })
        return defaults
    }

    fun addPinnedApp(allApps: List<AppInfo>, newApp: AppInfo): Boolean {
        val current = getPinnedApps(allApps).toMutableList()
        if (current.any { it.packageName == newApp.packageName }) {
            return false // Already pinned
        }
        if (current.size >= MAX_DOCK_APPS) {
            // Drop last or refuse if full
            current.removeAt(current.size - 1)
        }
        current.add(newApp)
        savePinnedPackageNames(current.map { it.packageName })
        return true
    }

    fun removePinnedApp(allApps: List<AppInfo>, appToRemove: AppInfo) {
        val current = getPinnedApps(allApps).toMutableList()
        current.removeAll { it.packageName == appToRemove.packageName }
        savePinnedPackageNames(current.map { it.packageName })
    }

    fun replacePinnedApp(allApps: List<AppInfo>, oldApp: AppInfo, newApp: AppInfo) {
        val current = getPinnedApps(allApps).toMutableList()
        val index = current.indexOfFirst { it.packageName == oldApp.packageName }
        if (index != -1) {
            current[index] = newApp
        } else {
            current.add(newApp)
        }
        savePinnedPackageNames(current.map { it.packageName })
    }

    // --- Custom Navigation & Music Preferences ---

    fun getCustomNavPackage(): String? = prefs.getString(KEY_CUSTOM_NAV_PACKAGE, null)

    fun setCustomNavPackage(packageName: String) {
        prefs.edit().putString(KEY_CUSTOM_NAV_PACKAGE, packageName).apply()
    }

    fun getCustomMusicPackage(): String? = prefs.getString(KEY_CUSTOM_MUSIC_PACKAGE, null)

    fun setCustomMusicPackage(packageName: String) {
        prefs.edit().putString(KEY_CUSTOM_MUSIC_PACKAGE, packageName).apply()
    }

    fun resolveNavApp(allApps: List<AppInfo>): AppInfo? {
        val customPkg = getCustomNavPackage()
        if (!customPkg.isNullOrBlank()) {
            val found = allApps.firstOrNull { it.packageName == customPkg }
            if (found != null) return found
        }
        return allApps.firstOrNull { it.isNavigation }
    }

    fun resolveMusicApp(allApps: List<AppInfo>): AppInfo? {
        val customPkg = getCustomMusicPackage()
        if (!customPkg.isNullOrBlank()) {
            val found = allApps.firstOrNull { it.packageName == customPkg }
            if (found != null) return found
        }
        return allApps.firstOrNull { it.isMusic }
    }

    fun getCustomDvrPackage(): String? = prefs.getString(KEY_CUSTOM_DVR_PACKAGE, null)

    fun setCustomDvrPackage(packageName: String) {
        prefs.edit().putString(KEY_CUSTOM_DVR_PACKAGE, packageName).apply()
    }

    fun isDvrAutoStartEnabled(): Boolean = prefs.getBoolean(KEY_DVR_AUTOSTART_ENABLED, true)

    fun setDvrAutoStartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DVR_AUTOSTART_ENABLED, enabled).apply()
    }

    fun resolveDvrApp(allApps: List<AppInfo>): AppInfo? {
        val customPkg = getCustomDvrPackage()
        if (!customPkg.isNullOrBlank()) {
            val found = allApps.firstOrNull { it.packageName == customPkg }
            if (found != null) return found
        }
        return allApps.firstOrNull { it.isDvr }
    }

    // --- Package Helpers ---

    fun isZLinkPackage(pkg: String, label: String = ""): Boolean {
        val lowerPkg = pkg.lowercase()
        val lowerLabel = label.lowercase()
        return zlinkPackages.contains(pkg) ||
                lowerPkg.contains("zlink") ||
                lowerPkg.contains("phonemirror") ||
                lowerPkg.contains("speedplay") ||
                lowerPkg.contains("autokit") ||
                lowerLabel.contains("zlink") ||
                lowerLabel.contains("android auto")
    }

    private fun isNavPackage(pkg: String, label: String): Boolean {
        val lowerPkg = pkg.lowercase()
        val lowerLabel = label.lowercase()
        return navigationPackages.contains(pkg) ||
                lowerPkg.contains("map") ||
                lowerPkg.contains("waze") ||
                lowerPkg.contains("navi") ||
                lowerLabel.contains("maps") ||
                lowerLabel.contains("navigation")
    }

    private fun isMusicPackage(pkg: String, label: String): Boolean {
        val lowerPkg = pkg.lowercase()
        val lowerLabel = label.lowercase()
        return musicPackages.contains(pkg) ||
                lowerPkg.contains("spotify") ||
                lowerPkg.contains("music") ||
                lowerPkg.contains("audio") ||
                lowerPkg.contains("radio") ||
                lowerLabel.contains("music") ||
                lowerLabel.contains("spotify") ||
                lowerLabel.contains("radio")
    }

    fun isDvrPackage(pkg: String, label: String = ""): Boolean {
        val lowerPkg = pkg.lowercase()
        val lowerLabel = label.lowercase()
        return dvrPackages.contains(pkg) ||
                lowerPkg.contains(".dvr") ||
                lowerPkg.endsWith("dvr") ||
                lowerPkg.contains("dashcam") ||
                lowerPkg.contains("carcamera") ||
                lowerLabel == "dvr" ||
                lowerLabel.contains("dvr") ||
                lowerLabel.contains("dashcam")
    }

    fun launchApp(appInfo: AppInfo): Boolean {
        return launchPackageAndActivity(appInfo.packageName, appInfo.activityName)
    }

    fun launchPackage(packageName: String): Boolean {
        return try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun launchPackageAndActivity(packageName: String, activityName: String? = null): Boolean {
        return try {
            if (!activityName.isNullOrBlank()) {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(packageName, activityName)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            } else {
                launchPackage(packageName)
            }
        } catch (e: Exception) {
            launchPackage(packageName)
        }
    }

    fun launchSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open Settings", Toast.LENGTH_SHORT).show()
        }
    }
}
