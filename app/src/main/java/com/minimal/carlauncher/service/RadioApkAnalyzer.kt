package com.minimal.carlauncher.service

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

data class ManifestComponentDetail(
    val tag: String,
    val name: String,
    val exported: String?,
    val permission: String?,
    val authorities: String?,
    val actions: List<String>
)

data class RadioApkAnalysis(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    val sharedUserId: String?,
    val apkPath: String?,
    val components: List<ManifestComponentDetail>,
    val receiverActions: List<String>,
    val serviceActions: List<String>,
    val providerAuthorities: List<String>,
    val codeSource: String,
    val codeActions: List<String>,
    val codeKeys: List<String>
)

/**
 * Inspects installed vendor radio APKs (such as `com.nwd.radio`, `com.qf.radio`, `com.ts.MainUI`,
 * `com.allwinner.radio`) directly on the head unit by parsing their compiled `AndroidManifest.xml`
 * and `.dex` / `.vdex` / `.odex` string tables.
 *
 * This allows `RadioManager` to automatically discover and subscribe to the exact broadcast
 * actions, receiver filters, ContentProvider authorities, Settings keys, and extra keys compiled
 * into the head unit's stock radio app—even when undocumented by the vendor.
 */
object RadioApkAnalyzer {

    private val KEYWORDS = listOf(
        "radio", "freq", "tuner", "band", "rds", "station", "preset", "collect",
        "seek", "step", "fm", "am_", "nwd", "qf", "channel", "customize/radio"
    )

    private val ACTION_PATTERN = Regex(
        """^(?:[A-Za-z][\w$]*(?:\.[\w$]+)+|[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+|/customize/radio/[\w/-]+)$"""
    )

    private val KEY_PATTERN = Regex(
        """^[A-Za-z0-9_.:-]{3,48}$"""
    )

    private val cache = ConcurrentHashMap<String, RadioApkAnalysis>()

    fun analyzeInstalledRadioPackages(context: Context): List<RadioApkAnalysis> {
        val pkgs = DiagnosticsManager.getInstalledRadioPackages(context)
        return pkgs.mapNotNull { info ->
            analyzePackage(context, info.packageName)
        }
    }

    fun analyzePackage(context: Context, packageName: String): RadioApkAnalysis? {
        cache[packageName]?.let { return it }

        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val pi = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val ai = pi.applicationInfo ?: return null
            val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Throwable) { packageName }
            val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }
            val apkPath = ai.sourceDir

            val components = mutableListOf<ManifestComponentDetail>()
            val receiverActions = LinkedHashSet<String>()
            val serviceActions = LinkedHashSet<String>()
            val providerAuthorities = LinkedHashSet<String>()

            var codeSource = "None"
            val codeActions = LinkedHashSet<String>()
            val codeKeys = LinkedHashSet<String>()

            if (!apkPath.isNullOrBlank() && File(apkPath).exists()) {
                // 1. Parse binary AndroidManifest.xml
                try {
                    val xmlBytes = ZipFile(apkPath).use { zip ->
                        val entry = zip.getEntry("AndroidManifest.xml")
                        entry?.let { zip.getInputStream(it).use { s -> s.readBytes() } }
                    }
                    if (xmlBytes != null) {
                        val elements = BinaryXml.parse(xmlBytes)
                        val compTags = setOf("receiver", "service", "activity", "activity-alias", "provider")
                        var i = 0
                        while (i < elements.size) {
                            val el = elements[i]
                            if (el.tag in compTags) {
                                val actions = mutableListOf<String>()
                                var j = i + 1
                                while (j < elements.size && elements[j].depth > el.depth) {
                                    if (elements[j].tag == "action") {
                                        elements[j].attrs["name"]?.takeIf { it.isNotBlank() }?.let { actions.add(it) }
                                    }
                                    j++
                                }
                                val name = el.attrs["name"].orEmpty()
                                val exported = el.attrs["exported"]
                                val perm = el.attrs["permission"]
                                val auth = el.attrs["authorities"]

                                components.add(
                                    ManifestComponentDetail(
                                        tag = el.tag,
                                        name = name,
                                        exported = exported,
                                        permission = perm,
                                        authorities = auth,
                                        actions = actions
                                    )
                                )

                                if (el.tag == "receiver") {
                                    receiverActions.addAll(actions)
                                } else if (el.tag == "service") {
                                    serviceActions.addAll(actions)
                                } else if (el.tag == "provider" && !auth.isNullOrBlank()) {
                                    auth.split(";").map { it.trim() }.filter { it.isNotBlank() }.forEach {
                                        providerAuthorities.add(it)
                                    }
                                }
                                i = j
                            } else {
                                i++
                            }
                        }
                    }
                } catch (e: Throwable) {
                    // Ignore manifest parse error
                }

                // 2. Parse DEX or OAT/VDEX string tables
                try {
                    val rawStrings = LinkedHashSet<String>()
                    ZipFile(apkPath).use { zip ->
                        zip.entries().asSequence()
                            .filter { it.name.matches(Regex("classes\\d*\\.dex")) }
                            .forEach { entry ->
                                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                                rawStrings.addAll(DexStrings.read(bytes))
                            }
                    }
                    if (rawStrings.isNotEmpty()) {
                        codeSource = "APK classes.dex"
                    } else {
                        val oatDir = File(File(apkPath).parentFile, "oat")
                        if (oatDir.exists()) {
                            val oatFiles = oatDir.walkTopDown().maxDepth(2)
                                .filter { it.isFile && (it.name.endsWith(".vdex") || it.name.endsWith(".odex")) }
                                .toList()
                            if (oatFiles.isNotEmpty()) {
                                codeSource = oatFiles.joinToString { it.name }
                                oatFiles.forEach { f ->
                                    try {
                                        rawStrings.addAll(DexStrings.rawStrings(f.readBytes()))
                                    } catch (e: Throwable) {}
                                }
                            }
                        }
                    }

                    val pkgPrefix = packageName.substringBeforeLast('.')
                    for (raw in rawStrings) {
                        val s = raw.trim()
                        if (s.length !in 3..160) continue
                        val lower = s.lowercase(Locale.US)

                        // Skip standard Android / Kotlin / Java framework class names
                        if (s.startsWith("android.") && !s.startsWith("android.intent.action.") && !s.startsWith("android.hardware.radio")) continue
                        if (s.startsWith("androidx.") || s.startsWith("kotlin") || s.startsWith("java.") || s.startsWith("dalvik.") || s.startsWith("com.google.")) continue
                        if (s.startsWith("L") && s.contains('/')) continue

                        val matchesKeyword = KEYWORDS.any { lower.contains(it) } || lower.startsWith(pkgPrefix.lowercase(Locale.US))
                        if (!matchesKeyword) continue

                        if (ACTION_PATTERN.matches(s) && !s.endsWith(".class") && !s.endsWith(".java") && !s.endsWith(".kt")) {
                            // Likely an Intent action, broadcast constant, or class/interface identifier
                            val looksLikeAction = lower.contains("action") ||
                                lower.contains("broadcast") ||
                                lower.contains("freq") ||
                                lower.contains("radio") ||
                                lower.contains("seek") ||
                                lower.contains("step") ||
                                lower.contains("tune") ||
                                lower.contains("preset") ||
                                lower.contains("collect") ||
                                lower.contains("cmd") ||
                                lower.contains("command") ||
                                lower.contains("report") ||
                                lower.contains("status") ||
                                lower.contains("change") ||
                                lower.contains("update") ||
                                lower.startsWith("/customize/radio")
                            if (looksLikeAction && codeActions.size < 140) {
                                codeActions.add(s)
                            }
                        } else if (KEY_PATTERN.matches(s) && !s.contains('.')) {
                            val looksLikeKey = lower.contains("freq") ||
                                lower.contains("band") ||
                                lower.contains("station") ||
                                lower.contains("preset") ||
                                lower.contains("collect") ||
                                lower.contains("rds") ||
                                lower.contains("cmd") ||
                                lower.contains("command") ||
                                lower.contains("seek") ||
                                lower.contains("step") ||
                                lower.contains("channel") ||
                                lower.contains("nwd") ||
                                lower.contains("qf") ||
                                lower.contains("radio")
                            if (looksLikeKey && codeKeys.size < 100) {
                                codeKeys.add(s)
                            }
                        } else if (KEY_PATTERN.matches(s) && (lower.startsWith("persist.") || lower.startsWith("sys.") || lower.startsWith("ro.") || lower.startsWith("nwd.") || lower.startsWith("qf."))) {
                            if (codeKeys.size < 100) {
                                codeKeys.add(s)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    // Ignore DEX parse error
                }
            }

            val result = RadioApkAnalysis(
                packageName = packageName,
                label = label,
                versionName = pi.versionName ?: "N/A",
                versionCode = vCode,
                isSystem = isSystem,
                sharedUserId = pi.sharedUserId,
                apkPath = apkPath,
                components = components,
                receiverActions = receiverActions.toList(),
                serviceActions = serviceActions.toList(),
                providerAuthorities = providerAuthorities.toList(),
                codeSource = codeSource,
                codeActions = codeActions.toList(),
                codeKeys = codeKeys.toList()
            )
            cache[packageName] = result
            result
        } catch (e: Throwable) {
            null
        }
    }
}
