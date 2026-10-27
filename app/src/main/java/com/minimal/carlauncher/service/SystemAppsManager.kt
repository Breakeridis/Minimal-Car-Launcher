package com.minimal.carlauncher.service

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/** Current state of a (system) package on the head unit. */
data class SystemPackageStatus(
    val packageName: String,
    val label: String,
    val description: String,
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val isSystemApp: Boolean
)

/** Outcome of an enable / disable attempt. */
data class ToggleResult(
    val success: Boolean,
    val method: String,
    val message: String
)

/** Where the Aurora Store APK will be downloaded from. */
data class AuroraRelease(
    val versionName: String,
    val apkUrl: String,
    val source: String
)

/**
 * Manages Google system packages (Play Services, Services Framework, Play Store) and the
 * Aurora Store (open-source Google Play client) installer.
 *
 * Enabling / disabling another app requires the `CHANGE_COMPONENT_ENABLED_STATE` permission,
 * which Android only grants to platform-signed / privileged apps. Therefore toggling is attempted
 * in this order:
 *  1. Direct PackageManager API (works only if the launcher is platform-signed on this firmware).
 *  2. Root shell (`su`) running `pm enable` / `pm disable-user` (works on rooted head units).
 * If both fail, the UI offers the system "App info" screen and the equivalent ADB command.
 */
class SystemAppsManager(private val context: Context) {

    companion object {
        const val PKG_GSF = "com.google.android.gsf"
        const val PKG_GMS = "com.google.android.gms"
        const val PKG_PLAY_STORE = "com.android.vending"
        const val PKG_AURORA = "com.aurora.store"

        /** Order matters when enabling: framework -> services -> store. */
        val GOOGLE_PACKAGES = listOf(
            Triple(PKG_GSF, "Google Services Framework", "Core Google account & sync framework"),
            Triple(PKG_GMS, "Google Play Services", "Required by most Google apps and Maps"),
            Triple(PKG_PLAY_STORE, "Google Play Store", "Official Google app store")
        )

        private const val USER_AGENT = "MinimalCarLauncher-Android"
        private const val FDROID_API = "https://f-droid.org/api/v1/packages/$PKG_AURORA"
        private const val FDROID_REPO = "https://f-droid.org/repo/"
        private const val GITLAB_RELEASES =
            "https://gitlab.com/api/v4/projects/AuroraOSS%2FAuroraStore/releases"
    }

    private val pm: PackageManager = context.packageManager
    val localAdbClient = LocalAdbClient(context)

    // ---------------------------------------------------------------------------------------
    // Status
    // ---------------------------------------------------------------------------------------

    fun getGoogleStatuses(): List<SystemPackageStatus> =
        GOOGLE_PACKAGES.map { (pkg, label, desc) -> getStatus(pkg, label, desc) }

    fun getStatus(packageName: String, label: String, description: String = ""): SystemPackageStatus {
        val info = getAppInfo(packageName)
            ?: return SystemPackageStatus(packageName, label, description, false, false, false)

        val installedForUser = (info.flags and ApplicationInfo.FLAG_INSTALLED) != 0
        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        val enabled = try {
            when (pm.getApplicationEnabledSetting(packageName)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> info.enabled
                else -> false // DISABLED, DISABLED_USER, DISABLED_UNTIL_USED
            }
        } catch (e: Exception) {
            info.enabled
        }

        return SystemPackageStatus(
            packageName = packageName,
            label = label,
            description = description,
            // Present on the device (even if removed for the current user / disabled)
            isInstalled = true,
            isEnabled = installedForUser && enabled,
            isSystemApp = isSystem
        )
    }

    fun isPackageInstalled(packageName: String): Boolean {
        val info = getAppInfo(packageName) ?: return false
        return (info.flags and ApplicationInfo.FLAG_INSTALLED) != 0
    }

    private fun getAppInfo(packageName: String): ApplicationInfo? {
        @Suppress("DEPRECATION")
        val flags = PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, flags)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Only checks whether an `su` binary exists; does NOT trigger a root prompt. */
    fun isRootBinaryPresent(): Boolean {
        val paths = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
            "/system/sbin/su", "/vendor/bin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/debug_ramdisk/su"
        )
        return paths.any { runCatching { File(it).exists() }.getOrDefault(false) }
    }

    fun adbCommandFor(packageName: String, enable: Boolean): String =
        if (enable) "adb shell pm enable $packageName"
        else "adb shell pm disable-user --user 0 $packageName"

    suspend fun getAdbStatus(): AdbStatus = localAdbClient.getStatus()

    suspend fun testAdbConnection(): AdbResult = localAdbClient.shell("getprop ro.product.model")

    fun setCustomAdbPort(port: Int?) {
        localAdbClient.setCustomPort(port)
    }

    // ---------------------------------------------------------------------------------------
    // Enable / Disable
    // ---------------------------------------------------------------------------------------

    suspend fun setPackageEnabled(packageName: String, enable: Boolean): ToggleResult =
        withContext(Dispatchers.IO) {
            if (getAppInfo(packageName) == null) {
                return@withContext ToggleResult(false, "none", "Package is not present on this head unit")
            }

            val cmd = if (enable) {
                // Unhide / reinstall-for-user too, in case the vendor hid or removed the
                // package for user 0 instead of simply disabling it.
                "pm unhide $packageName; pm install-existing $packageName; pm enable $packageName"
            } else {
                "pm disable-user --user 0 $packageName"
            }

            // 1. Root shell (if available)
            if (isRootBinaryPresent()) {
                val ok = runAsRoot(cmd)
                if (ok && verify(packageName, enable)) {
                    return@withContext ToggleResult(true, "root", "Success via root")
                }
            }

            // 2. Local ADB Client (talks directly to adbd on localhost:5555 / network ADB)
            val adbResult = localAdbClient.shell(cmd)
            if (adbResult.success) {
                kotlinx.coroutines.delay(100)
                if (verify(packageName, enable)) {
                    return@withContext ToggleResult(true, "local ADB", "Success via local ADB")
                } else {
                    return@withContext ToggleResult(
                        true,
                        "local ADB",
                        "ADB executed: ${adbResult.output.ifBlank { "Done" }}"
                    )
                }
            }

            // 3. Direct API (only works if this APK is platform-signed / privileged)
            try {
                pm.setApplicationEnabledSetting(
                    packageName,
                    if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,
                    0
                )
                if (verify(packageName, enable)) {
                    return@withContext ToggleResult(true, "system API", "Success via system API")
                }
            } catch (e: SecurityException) {
                // Expected for normal apps
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val adbErrDetail = adbResult.error ?: adbResult.output
            val reason = if (!adbErrDetail.isNullOrBlank()) {
                "ADB: $adbErrDetail"
            } else {
                "Local ADB (port 5555) not reachable and unit is not rooted."
            }

            ToggleResult(
                false, "none",
                "$reason\nRun once via PC ADB or enable Network ADB:\n${adbCommandFor(packageName, enable)}"
            )
        }

    private fun verify(packageName: String, enable: Boolean): Boolean {
        val status = getStatus(packageName, packageName)
        return status.isEnabled == enable
    }

    private fun runAsRoot(command: String): Boolean {
        return try {
            val process = ProcessBuilder("su", "-c", command)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().use { it.readText() }
            val finished = process.waitFor(20, TimeUnit.SECONDS)
            if (!finished) {
                process.destroy()
                false
            } else {
                process.exitValue() == 0
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /** Opens the system "App info" page for the package (may expose an Enable button). */
    fun openAppInfo(packageName: String): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
            Intent().setClassName(
                "com.android.settings",
                "com.android.settings.applications.InstalledAppDetails"
            ).setData(Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS)
        )
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (e: Exception) {
                // Try the next fallback
            }
        }
        return false
    }

    fun launchPackage(packageName: String): Boolean {
        return try {
            val intent = pm.getLaunchIntentForPackage(packageName) ?: return false
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------------------------------
    // Aurora Store
    // ---------------------------------------------------------------------------------------

    fun getInstalledAuroraVersion(): String? {
        if (!isPackageInstalled(PKG_AURORA)) return null
        return try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(PKG_AURORA, 0).versionName
        } catch (e: Exception) {
            null
        }
    }

    /** Resolves the latest Aurora Store APK, preferring F-Droid and falling back to GitLab. */
    suspend fun fetchLatestAurora(): AuroraRelease? = withContext(Dispatchers.IO) {
        fetchFromFdroid() ?: fetchFromGitLab()
    }

    private fun fetchFromFdroid(): AuroraRelease? {
        return try {
            val json = JSONObject(httpGet(FDROID_API) ?: return null)
            val suggested = json.optLong("suggestedVersionCode", -1L)
            val packages = json.optJSONArray("packages") ?: JSONArray()
            var versionName: String? = null
            var versionCode = suggested
            for (i in 0 until packages.length()) {
                val p = packages.getJSONObject(i)
                if (suggested == -1L || p.optLong("versionCode") == suggested) {
                    versionName = p.optString("versionName")
                    versionCode = p.optLong("versionCode")
                    break
                }
            }
            if (versionCode <= 0) return null
            AuroraRelease(
                versionName = versionName ?: versionCode.toString(),
                apkUrl = "$FDROID_REPO${PKG_AURORA}_$versionCode.apk",
                source = "F-Droid"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun fetchFromGitLab(): AuroraRelease? {
        return try {
            val releases = JSONArray(httpGet(GITLAB_RELEASES) ?: return null)
            for (i in 0 until releases.length()) {
                val rel = releases.getJSONObject(i)
                if (rel.optBoolean("upcoming_release", false)) continue
                val links = rel.optJSONObject("assets")?.optJSONArray("links") ?: continue
                for (j in 0 until links.length()) {
                    val link = links.getJSONObject(j)
                    val name = link.optString("name")
                    val url = link.optString("direct_asset_url").ifBlank { link.optString("url") }
                    if ((name.endsWith(".apk", true) || url.endsWith(".apk", true)) && url.isNotBlank()) {
                        return AuroraRelease(
                            versionName = rel.optString("tag_name", "latest"),
                            apkUrl = url,
                            source = "GitLab"
                        )
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun httpGet(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 10000
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            connection?.disconnect()
        }
    }
}
