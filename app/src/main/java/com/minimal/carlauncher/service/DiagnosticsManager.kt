package com.minimal.carlauncher.service

import android.app.ActivityManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import com.minimal.carlauncher.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

data class PackageDiagnosticInfo(
    val label: String,
    val packageName: String,
    val apkPath: String,
    val sharedUserId: String,
    val isSystem: Boolean,
    val isRadioCandidate: Boolean,
    val versionName: String,
    val versionCode: Long,
    val activities: List<String>,
    val receivers: List<String>,
    val services: List<String>,
    val providers: List<String>,
    val manifestActions: List<String> = emptyList()
)

data class SystemDiagnosticInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val product: String,
    val board: String,
    val hardware: String,
    val platform: String,
    val cpuHardware: String,
    val cpuModel: String,
    val cpuCores: Int,
    val supportedAbis: String,
    val ramSummary: String,
    val kernelVersion: String,
    val androidVersion: String,
    val apiLevel: Int,
    val buildDisplay: String,
    val fingerprint: String,
    val screenMetrics: String,
    val mcuAndVendorProps: List<Pair<String, String>>
)

object DiagnosticsManager {

    private fun readSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getter = clazz.getMethod("get", String::class.java, String::class.java)
            (getter.invoke(null, key, "") as? String)?.trim().orEmpty()
        } catch (e: Throwable) {
            ""
        }
    }

    private fun parseCpuInfo(): Triple<String, String, Int> {
        var hardware = ""
        var modelName = ""
        var processorCount = 0
        try {
            val file = File("/proc/cpuinfo")
            if (file.exists() && file.canRead()) {
                file.forEachLine { line ->
                    val parts = line.split(":", limit = 2)
                    if (parts.size == 2) {
                        val k = parts[0].trim().lowercase()
                        val v = parts[1].trim()
                        when (k) {
                            "hardware" -> if (hardware.isEmpty()) hardware = v
                            "model name", "processor", "cpu model" -> {
                                if (k == "processor" && v.all { it.isDigit() }) {
                                    processorCount++
                                } else if (modelName.isEmpty() && v.isNotEmpty()) {
                                    modelName = v
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            // Ignore
        }
        val cores = if (processorCount > 0) processorCount else Runtime.getRuntime().availableProcessors()
        return Triple(
            hardware.ifEmpty { Build.HARDWARE ?: "Unknown" },
            modelName.ifEmpty { Build.SUPPORTED_ABIS.firstOrNull() ?: "ARM" },
            cores
        )
    }

    private fun readKernelVersion(): String {
        return try {
            val file = File("/proc/version")
            if (file.exists() && file.canRead()) {
                file.bufferedReader().use { it.readLine()?.trim().orEmpty() }
            } else {
                System.getProperty("os.version") ?: "Unknown"
            }
        } catch (e: Throwable) {
            System.getProperty("os.version") ?: "Unknown"
        }
    }

    private fun readMcuAndVendorProps(): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val seenKeys = HashSet<String>()

        // 1. Check high-priority head unit & motherboard keys directly via SystemProperties
        val priorityKeys = listOf(
            "ro.board.platform",
            "ro.product.board",
            "ro.hardware",
            "ro.arch",
            "ro.soc.manufacturer",
            "ro.soc.model",
            "ro.product.device",
            "ro.product.name",
            "ro.product.model",
            "ro.product.brand",
            "ro.product.manufacturer",
            "ro.build.product",
            "ro.build.description",
            "ro.build.display.id",
            "ro.build.flavor",
            "ro.build.characteristics",
            "ro.mcu.version",
            "sys.mcu.version",
            "persist.sys.mcu.version",
            "ro.build.mcu.version",
            "ro.nwd.version",
            "ro.nwd.mcu.version",
            "persist.sys.nwd.radio.freq",
            "ro.qf.version",
            "ro.fyt.platform",
            "ro.topway.platform",
            "ro.syu.version",
            "ro.ts.version",
            "ro.forfan.version",
            "ro.xyauto.version",
            "ro.custom.build.version"
        )
        for (k in priorityKeys) {
            val v = readSystemProperty(k)
            if (v.isNotEmpty() && seenKeys.add(k)) {
                result.add(k to v)
            }
        }

        // 2. Run `getprop` once to catch any vendor-specific MCU / headunit / radio properties
        try {
            val proc = Runtime.getRuntime().exec("getprop")
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val keywords = listOf(
                "mcu", "nwd", "qf", "syu", "fyt", "topway", "ts.", "forfan", "xyauto",
                "allwinner", "softwinner", "sunxi", "t507", "a133", "k2401", "junsun",
                "radio", "tuner", "carkit", "car.", "platform", "board", "hardware",
                "soc", "customer", "project", "custom"
            )
            var line: String? = reader.readLine()
            while (line != null) {
                val trimmed = line.trim()
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    val sep = trimmed.indexOf("]: [")
                    if (sep > 1) {
                        val key = trimmed.substring(1, sep)
                        val value = trimmed.substring(sep + 4, trimmed.length - 1).trim()
                        val lowerKey = key.lowercase()
                        val lowerVal = value.lowercase()
                        if (value.isNotEmpty() && !seenKeys.contains(key)) {
                            if (keywords.any { lowerKey.contains(it) || lowerVal.contains(it) }) {
                                seenKeys.add(key)
                                result.add(key to value)
                            }
                        }
                    }
                }
                line = reader.readLine()
            }
            reader.close()
        } catch (e: Throwable) {
            // Ignore if getprop is restricted
        }

        return result
    }

    fun getSystemInfo(context: Context): SystemDiagnosticInfo {
        val dm = context.resources.displayMetrics
        val screen = "${dm.widthPixels}x${dm.heightPixels} (${dm.densityDpi} dpi, ${dm.density}x)"
        val (cpuHw, cpuMod, cores) = parseCpuInfo()
        val platform = readSystemProperty("ro.board.platform")
            .ifEmpty { readSystemProperty("ro.product.board") }
            .ifEmpty { Build.BOARD ?: "Unknown" }

        val ramSummary = try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am?.getMemoryInfo(memInfo)
            if (memInfo.totalMem > 0) {
                val totalGb = memInfo.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
                val availGb = memInfo.availMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
                String.format(Locale.US, "%.2f GB Total (%.2f GB Free)", totalGb, availGb)
            } else {
                "Unknown"
            }
        } catch (e: Throwable) {
            "Unknown"
        }

        return SystemDiagnosticInfo(
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Unknown",
            brand = Build.BRAND ?: "Unknown",
            device = Build.DEVICE ?: "Unknown",
            product = Build.PRODUCT ?: "Unknown",
            board = Build.BOARD ?: "Unknown",
            hardware = Build.HARDWARE ?: "Unknown",
            platform = platform,
            cpuHardware = cpuHw,
            cpuModel = cpuMod,
            cpuCores = cores,
            supportedAbis = Build.SUPPORTED_ABIS?.joinToString(", ") ?: "Unknown",
            ramSummary = ramSummary,
            kernelVersion = readKernelVersion(),
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            apiLevel = Build.VERSION.SDK_INT,
            buildDisplay = Build.DISPLAY ?: "Unknown",
            fingerprint = Build.FINGERPRINT ?: "Unknown",
            screenMetrics = screen,
            mcuAndVendorProps = readMcuAndVendorProps()
        )
    }

    /**
     * Lightweight extraction of `<action android:name="..." />` from an APK's compiled AndroidManifest.xml.
     * Takes < 1ms per APK because AndroidManifest.xml is only a few kilobytes.
     */
    private fun extractManifestActions(apkPath: String): List<String> {
        if (apkPath.isBlank()) return emptyList()
        val file = File(apkPath)
        if (!file.exists() || !file.canRead()) return emptyList()
        return try {
            val xmlBytes = ZipFile(file).use { zip ->
                val entry = zip.getEntry("AndroidManifest.xml") ?: return emptyList()
                zip.getInputStream(entry).use { it.readBytes() }
            }
            val elements = BinaryXml.parse(xmlBytes)
            val actions = LinkedHashSet<String>()
            for (el in elements) {
                if (el.tag == "action") {
                    val name = el.attrs["name"]?.trim().orEmpty()
                    if (name.isNotEmpty() && !name.startsWith("android.intent.action.MAIN") && !name.startsWith("android.intent.action.VIEW")) {
                        actions.add(name)
                    }
                }
            }
            actions.toList()
        } catch (e: Throwable) {
            emptyList()
        }
    }

    /**
     * Scans installed packages on the head unit and returns detailed info on Radio candidates
     * as well as vendor/automotive system packages.
     */
    fun getInstalledPackagesInfo(context: Context): List<PackageDiagnosticInfo> {
        val pm = context.packageManager
        val results = mutableListOf<PackageDiagnosticInfo>()

        val flags = PackageManager.GET_ACTIVITIES or
            PackageManager.GET_RECEIVERS or
            PackageManager.GET_SERVICES or
            PackageManager.GET_PROVIDERS

        val packages = try {
            @Suppress("DEPRECATION")
            pm.getInstalledPackages(flags)
        } catch (e: Throwable) {
            emptyList()
        }

        val radioKeywords = listOf(
            "radio", "fmradio", "tuner", "fmapplication", "mainui", "carassist"
        )
        val vendorKeywords = listOf(
            "nwd", "qf", "syu", "ts.", "forfan", "xyauto", "microntek",
            "yecon", "allwinner", "softwinner", "carkit", "autochips",
            "topway", "fyt", "chaochuang", "navimods", "car"
        )

        for (pkgInfo in packages) {
            try {
                val pkg = pkgInfo.packageName ?: continue
                if (pkg == context.packageName) continue
                val appInfo = pkgInfo.applicationInfo ?: continue

                val label = try {
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Throwable) {
                    pkg
                }
                val apkPath = appInfo.sourceDir ?: ""
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                val activities = pkgInfo.activities?.mapNotNull { it.name } ?: emptyList()
                val receivers = pkgInfo.receivers?.mapNotNull { it.name } ?: emptyList()
                val services = pkgInfo.services?.mapNotNull { it.name } ?: emptyList()
                val providers = pkgInfo.providers?.mapNotNull { it.authority } ?: emptyList()

                val lowerPkg = pkg.lowercase()
                val lowerLabel = label.lowercase()
                val lowerPath = apkPath.lowercase()

                val hasRadioComponent = activities.any { it.lowercase().contains("radio") || it.lowercase().contains("fm") } ||
                    services.any { it.lowercase().contains("radio") || it.lowercase().contains("fm") } ||
                    receivers.any { it.lowercase().contains("radio") || it.lowercase().contains("fm") }

                val isRadioCandidate = radioKeywords.any {
                    lowerPkg.contains(it) || lowerLabel.contains(it) || lowerPath.contains(it)
                } || hasRadioComponent

                val isVendorOrLaunchable = isRadioCandidate ||
                    vendorKeywords.any { lowerPkg.contains(it) || lowerPath.contains(it) } ||
                    pm.getLaunchIntentForPackage(pkg) != null

                if (!isVendorOrLaunchable) continue

                val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }

                val manifestActions = if (isRadioCandidate) {
                    extractManifestActions(apkPath)
                } else {
                    emptyList()
                }

                results.add(
                    PackageDiagnosticInfo(
                        label = label,
                        packageName = pkg,
                        apkPath = apkPath,
                        sharedUserId = pkgInfo.sharedUserId ?: "",
                        isSystem = isSystem,
                        isRadioCandidate = isRadioCandidate,
                        versionName = pkgInfo.versionName ?: "N/A",
                        versionCode = vCode,
                        activities = activities,
                        receivers = receivers,
                        services = services,
                        providers = providers,
                        manifestActions = manifestActions
                    )
                )
            } catch (e: Throwable) {
                // Ignore individual package failure
            }
        }

        return results.sortedWith(
            compareByDescending<PackageDiagnosticInfo> { it.isRadioCandidate }
                .thenByDescending { it.isSystem }
                .thenBy { it.label.lowercase() }
        )
    }

    /**
     * Scans Settings.System and Settings.Global on-demand for any keys related to radio, frequency, MCU, or audio.
     */
    fun getAutomotiveSettings(context: Context): List<Pair<String, String>> {
        val resolver = context.contentResolver
        val list = mutableListOf<Pair<String, String>>()
        val seen = HashSet<String>()

        val keywords = listOf(
            "radio", "freq", "fm", "am", "band", "channel", "tuner", "rds",
            "mcu", "nwd", "qf", "syu", "ts", "forfan", "source", "audio", "car"
        )

        val tables = listOf(
            "System" to Settings.System.CONTENT_URI,
            "Global" to Settings.Global.CONTENT_URI
        )

        for ((prefix, uri) in tables) {
            try {
                resolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    val valIdx = cursor.getColumnIndex("value")
                    if (nameIdx >= 0 && valIdx >= 0) {
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(nameIdx) ?: continue
                            val value = cursor.getString(valIdx) ?: continue
                            val lower = name.lowercase()
                            if (keywords.any { lower.contains(it) }) {
                                val fullKey = "$prefix.$name"
                                if (seen.add(fullKey)) {
                                    list.add(fullKey to value)
                                }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                // Ignore if cursor query restricted
            }
        }

        return list.sortedBy { it.first }
    }

    fun buildFullReport(context: Context): String = buildString {
        appendLine("==================================================")
        appendLine("HEAD UNIT HARDWARE, CPU & RADIO INSPECTOR REPORT")
        appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
        appendLine("==================================================")
        appendLine()

        val sys = getSystemInfo(context)
        appendLine("### 1. HEAD UNIT HARDWARE, MOTHERBOARD & CPU")
        appendLine("- Manufacturer / Brand: ${sys.manufacturer} / ${sys.brand}")
        appendLine("- Model / Product / Device: ${sys.model} / ${sys.product} / ${sys.device}")
        appendLine("- Motherboard / Board / Hardware: ${sys.board} / ${sys.hardware}")
        appendLine("- SoC Platform: ${sys.platform}")
        appendLine("- CPU Hardware (/proc/cpuinfo): ${sys.cpuHardware}")
        appendLine("- CPU Model / Cores: ${sys.cpuModel} (${sys.cpuCores} cores)")
        appendLine("- CPU Architectures (ABIs): ${sys.supportedAbis}")
        appendLine("- Memory (RAM): ${sys.ramSummary}")
        appendLine("- Kernel Version: ${sys.kernelVersion}")
        appendLine("- Android OS: Android ${sys.androidVersion} (API ${sys.apiLevel})")
        appendLine("- Build Display ID: ${sys.buildDisplay}")
        appendLine("- Fingerprint: ${sys.fingerprint}")
        appendLine("- Screen Metrics: ${sys.screenMetrics}")
        appendLine()

        appendLine("### 2. MCU & VENDOR SYSTEM PROPERTIES (${sys.mcuAndVendorProps.size})")
        if (sys.mcuAndVendorProps.isEmpty()) {
            appendLine("(None detected)")
        } else {
            for ((k, v) in sys.mcuAndVendorProps) {
                appendLine("- $k = $v")
            }
        }
        appendLine()

        val allPkgs = getInstalledPackagesInfo(context)
        val radioPkgs = allPkgs.filter { it.isRadioCandidate }

        appendLine("### 3. DETECTED RADIO / TUNER APPS (${radioPkgs.size})")
        if (radioPkgs.isEmpty()) {
            appendLine("(No explicit radio packages matched)")
        } else {
            for (p in radioPkgs) {
                appendLine("- **${p.label}** (`${p.packageName}`)")
                appendLine("  • APK Path: ${p.apkPath}")
                if (p.sharedUserId.isNotEmpty()) appendLine("  • SharedUserId: ${p.sharedUserId}")
                appendLine("  • Version: ${p.versionName} (${p.versionCode}) | System: ${p.isSystem}")
                if (p.activities.isNotEmpty()) {
                    appendLine("  • Activities (${p.activities.size}): ${p.activities.joinToString(", ")}")
                }
                if (p.services.isNotEmpty()) {
                    appendLine("  • Services (${p.services.size}): ${p.services.joinToString(", ")}")
                }
                if (p.receivers.isNotEmpty()) {
                    appendLine("  • Receivers (${p.receivers.size}): ${p.receivers.joinToString(", ")}")
                }
                if (p.providers.isNotEmpty()) {
                    appendLine("  • Providers (${p.providers.size}): ${p.providers.joinToString(", ")}")
                }
                if (p.manifestActions.isNotEmpty()) {
                    appendLine("  • Manifest Intent Actions (${p.manifestActions.size}): ${p.manifestActions.joinToString(", ")}")
                }
            }
        }
        appendLine()

        appendLine("### 4. OTHER VENDOR & LAUNCHABLE APPS (${allPkgs.size - radioPkgs.size})")
        for (p in allPkgs.filter { !it.isRadioCandidate }) {
            appendLine("- ${p.label} (`${p.packageName}`) [path: ${p.apkPath}]")
        }
        appendLine()

        appendLine("### 5. AUTOMOTIVE & RADIO SETTINGS KEYS")
        val settings = getAutomotiveSettings(context)
        if (settings.isEmpty()) {
            appendLine("(No automotive/radio keys found in Settings.System/Global)")
        } else {
            for ((k, v) in settings) {
                appendLine("- $k = $v")
            }
        }
        appendLine("==================================================")
    }

    private const val NTFY_TOPIC = "breakeridis_carlauncher_diag_6974c07f"
    private const val NTFY_URL = "https://ntfy.sh/$NTFY_TOPIC"
    private const val GITHUB_ISSUES_URL = "https://api.github.com/repos/Breakeridis/Minimal-Car-Launcher/issues"

    suspend fun uploadReportToCloud(
        context: Context,
        userNote: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val fullReport = buildString {
                if (userNote.isNotBlank()) {
                    appendLine("### USER FEEDBACK / NOTE")
                    appendLine(userNote.trim())
                    appendLine()
                }
                append(buildFullReport(context))
            }

            // 1. Upload to zero-token ntfy.sh cloud channel as attachment file
            val conn = (URL(NTFY_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
                val modelTag = "${Build.MANUFACTURER ?: ""} ${Build.MODEL ?: "HeadUnit"}".trim()
                    .replace(Regex("[^A-Za-z0-9 _.-]"), "")
                setRequestProperty("Title", "HeadUnit Report: $modelTag")
                setRequestProperty("Filename", "headunit_report.txt")
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            }

            conn.outputStream.use { out ->
                out.write(fullReport.toByteArray(Charsets.UTF_8))
                out.flush()
            }

            val code = conn.responseCode
            val respText = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                return@withContext Result.failure(Exception("HTTP $code: $respText"))
            }

            val msgId = try {
                JSONObject(respText).optString("id", "OK")
            } catch (e: Throwable) {
                "OK"
            }

            // 2. Optional: If GITHUB_FEEDBACK_TOKEN is configured at build time, also post a GitHub Issue
            val ghToken = BuildConfig.GITHUB_FEEDBACK_TOKEN.trim()
            var ghIssueNumber: Int? = null
            if (ghToken.isNotEmpty()) {
                try {
                    val issueJson = JSONObject().apply {
                        put("title", "Head Unit Report: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.BOARD})")
                        put("body", "```text\n${fullReport.take(60_000)}\n```")
                    }
                    val ghConn = (URL(GITHUB_ISSUES_URL).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 12_000
                        readTimeout = 12_000
                        doOutput = true
                        setRequestProperty("Authorization", "Bearer $ghToken")
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        setRequestProperty("User-Agent", "Minimal-Car-Launcher")
                    }
                    ghConn.outputStream.use { out ->
                        out.write(issueJson.toString().toByteArray(Charsets.UTF_8))
                        out.flush()
                    }
                    if (ghConn.responseCode in 200..299) {
                        val ghResp = ghConn.inputStream.bufferedReader().use { it.readText() }
                        ghIssueNumber = JSONObject(ghResp).optInt("number").takeIf { it > 0 }
                    }
                } catch (e: Throwable) {
                    // Ignore optional GitHub error since primary cloud upload succeeded
                }
            }

            val summary = if (ghIssueNumber != null) {
                "Uploaded! (Cloud ID: $msgId, GitHub Issue #$ghIssueNumber)"
            } else {
                "Uploaded! (ID: $msgId)"
            }
            Result.success(summary)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }

    fun copyReportToClipboard(context: Context): Boolean {
        return try {
            val report = buildFullReport(context)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = ClipData.newPlainText("Head Unit Info Report", report)
            clipboard?.setPrimaryClip(clip)
            Toast.makeText(context, "Head Unit Info copied to clipboard!", Toast.LENGTH_SHORT).show()
            true
        } catch (e: Throwable) {
            Toast.makeText(context, "Failed to copy: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
