package com.minimal.carlauncher.service

import android.content.Context
import android.provider.Settings
import dadb.AdbKeyPair
import dadb.Dadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket

/** Result of an ADB shell command executed against the head unit's own adbd. */
data class AdbResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val error: String? = null
)

/** Snapshot of how ADB is configured on the head unit. */
data class AdbStatus(
    val usbDebuggingEnabled: Boolean,
    val tcpPort: Int?,
    val portReachable: Boolean
)

/**
 * Minimal "ADB to myself" client (same approach as LADB / Shizuku).
 *
 * The launcher connects to the head unit's own ADB daemon over TCP (localhost:5555) and runs
 * shell commands as the `shell` user, which is allowed to run `pm enable` / `pm disable-user`.
 *
 * Requirements (one-time, done by the user):
 *  - USB / ADB debugging enabled in Developer options (or the head unit's factory settings)
 *  - ADB over TCP active (built-in "ADB over network" option, or `adb tcpip 5555` from a PC)
 *  - Tap "Allow" (tick "Always allow") on the RSA prompt the first time the launcher connects
 */
class LocalAdbClient(private val context: Context) {

    companion object {
        private const val HOST = "127.0.0.1"
        private const val DEFAULT_PORT = 5555
        private const val CONNECT_TIMEOUT_MS = 3000
        /** Long enough for the user to accept the "Allow USB debugging?" RSA prompt. */
        private const val SOCKET_TIMEOUT_MS = 60000
    }

    private val keyDir = File(context.filesDir, "adb")
    private val privateKeyFile = File(keyDir, "adbkey")
    private val publicKeyFile = File(keyDir, "adbkey.pub")

    private fun keyPair(): AdbKeyPair {
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
            keyDir.mkdirs()
            AdbKeyPair.generate(privateKeyFile, publicKeyFile)
        }
        return AdbKeyPair.read(privateKeyFile, publicKeyFile)
    }

    private var customPort: Int? = null

    fun setCustomPort(port: Int?) {
        customPort = if (port != null && port in 1..65535) port else null
    }

    fun getActivePort(): Int = customPort ?: DEFAULT_PORT

    /** Candidate ports: custom port if set, then system properties, then standard 5555. */
    private fun candidatePorts(): List<Int> {
        val list = mutableListOf<Int>()
        customPort?.let { list.add(it) }
        val fromProps = listOf("service.adb.tcp.port", "persist.adb.tcp.port")
            .mapNotNull { readSystemProperty(it)?.toIntOrNull() }
            .filter { it in 1..65535 }
        list.addAll(fromProps)
        list.add(DEFAULT_PORT)
        return list.distinct()
    }

    suspend fun getStatus(): AdbStatus = withContext(Dispatchers.IO) {
        val usb = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) {
            false
        }
        val propPort = listOf("service.adb.tcp.port", "persist.adb.tcp.port")
            .mapNotNull { readSystemProperty(it)?.toIntOrNull() }
            .firstOrNull { it in 1..65535 }
        val reachablePort = candidatePorts().firstOrNull { isPortOpen(it) }
        AdbStatus(
            usbDebuggingEnabled = usb,
            tcpPort = reachablePort ?: propPort,
            portReachable = reachablePort != null
        )
    }

    /** Runs a shell command as the ADB `shell` user. */
    suspend fun shell(command: String): AdbResult = withContext(Dispatchers.IO) {
        val port = candidatePorts().firstOrNull { isPortOpen(it) }
            ?: return@withContext AdbResult(
                false, -1, "",
                "ADB over network is not active on this head unit (nothing listening on port $DEFAULT_PORT)."
            )
        try {
            Dadb.create(
                HOST, port, keyPair(),
                connectTimeout = CONNECT_TIMEOUT_MS,
                socketTimeout = SOCKET_TIMEOUT_MS
            ).use { dadb ->
                val response = dadb.shell(command)
                AdbResult(
                    success = response.exitCode == 0,
                    exitCode = response.exitCode,
                    output = (response.output + response.errorOutput).trim()
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            AdbResult(
                false, -1, "",
                "ADB connection refused or not authorized (${e.javaClass.simpleName}: ${e.message}). " +
                    "Accept the \"Allow USB debugging\" prompt on screen and tick \"Always allow\"."
            )
        }
    }

    private fun isPortOpen(port: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(HOST, port), 800); true }
    } catch (e: Exception) {
        false
    }

    private fun readSystemProperty(key: String): String? = try {
        val process = ProcessBuilder("getprop", key).redirectErrorStream(true).start()
        val value = process.inputStream.bufferedReader().use { it.readText() }.trim()
        process.waitFor()
        value.ifBlank { null }
    } catch (e: Exception) {
        null
    }
}
