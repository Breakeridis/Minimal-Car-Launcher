package com.minimal.carlauncher.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

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

    // Known music players
    private val musicPackages = setOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "com.amazon.mp3",
        "com.apple.android.music",
        "deezer.android.app",
        "com.soundcloud.android",
        "com.pandora.android"
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

                    AppInfo(
                        label = label,
                        packageName = pkg,
                        activityName = activityName,
                        icon = icon,
                        isZLink = isZLink,
                        isNavigation = isNav,
                        isMusic = isMusic
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
                lowerLabel.contains("music") ||
                lowerLabel.contains("spotify")
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
