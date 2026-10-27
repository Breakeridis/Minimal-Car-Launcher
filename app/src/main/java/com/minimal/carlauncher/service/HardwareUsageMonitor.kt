package com.minimal.carlauncher.service

import android.app.ActivityManager
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Real-time, ultra-lightweight hardware usage snapshot for the head unit.
 */
data class HardwareUsage(
    val cpuPercent: Int = 0,
    val cpuCores: Int = 4,
    val ramUsedMb: Int = 0,
    val ramTotalMb: Int = 0,
    val ramPercent: Int = 0,
    val ramFreeMb: Int = 0
)

/**
 * Samples CPU and RAM usage on-demand with minimal overhead (< 1ms per sample).
 * No background processes or heavy `top` execs are spawned.
 */
class HardwareUsageMonitor(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)

    private var lastTotalTime: Long = 0L
    private var lastIdleTime: Long = 0L

    suspend fun sample(): HardwareUsage = withContext(Dispatchers.IO) {
        // 1. Memory Usage via ActivityManager.MemoryInfo (fast native IPC)
        val mi = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(mi)
        val totalMb = (mi.totalMem / (1024 * 1024)).toInt()
        val availMb = (mi.availMem / (1024 * 1024)).toInt()
        val usedMb = (totalMb - availMb).coerceAtLeast(0)
        val ramPercent = if (totalMb > 0) {
            ((usedMb.toDouble() / totalMb.toDouble()) * 100.0).toInt().coerceIn(0, 100)
        } else 0

        // 2. CPU Usage by parsing the first line of /proc/stat
        var cpuPercent = 0
        try {
            val statFile = File("/proc/stat")
            if (statFile.exists() && statFile.canRead()) {
                val firstLine = statFile.bufferedReader().use { it.readLine() }
                if (firstLine != null && firstLine.startsWith("cpu ")) {
                    val parts = firstLine.trim().split("\\s+".toRegex()).drop(1).mapNotNull { it.toLongOrNull() }
                    if (parts.size >= 4) {
                        val idle = parts[3] + (if (parts.size > 4) parts[4] else 0L) // idle + iowait
                        val total = parts.sum()

                        if (lastTotalTime > 0L) {
                            val diffTotal = total - lastTotalTime
                            val diffIdle = idle - lastIdleTime
                            if (diffTotal > 0L) {
                                val usage = ((diffTotal - diffIdle).toDouble() / diffTotal.toDouble() * 100.0).toInt()
                                cpuPercent = usage.coerceIn(0, 100)
                            }
                        }
                        lastTotalTime = total
                        lastIdleTime = idle
                    }
                }
            }
        } catch (_: Throwable) {
            // Safe fallback if SELinux blocks /proc/stat
        }

        HardwareUsage(
            cpuPercent = cpuPercent,
            cpuCores = cores,
            ramUsedMb = usedMb,
            ramTotalMb = totalMb,
            ramPercent = ramPercent,
            ramFreeMb = availMb
        )
    }
}
