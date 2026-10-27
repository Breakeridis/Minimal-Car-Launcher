package com.minimal.carlauncher.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.reflect.Method
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Lightweight, 100% non-blocking controller for Nowada (NWD) Allwinner head unit radio
 * (`com.nwd.radio` UI + `com.nwd.radio.service.RadioService` backend).
 *
 * All binder IPC, reflection, and system property checks run strictly on Dispatchers.IO.
 */
class NwdRadioController(private val context: Context) {

    companion object {
        const val NWD_RADIO_UI_PKG = "com.nwd.radio"
        const val NWD_RADIO_UI_ACTIVITY = "com.nwd.radio.home_horizontalActivity"
        const val NWD_RADIO_SERVICE_PKG = "com.nwd.radio.service"
        const val NWD_RADIO_SERVICE_CLASS = "com.nwd.radio.service.RadioService"
        const val NWD_RADIO_SERVICE_ACTION = "com.nwd.radio.service.ACTION_RADIO_SERVICE"

        private const val PREFS_NAME = "nwd_radio_controller_prefs"
        private const val KEY_LAST_FREQ = "last_freq_mhz"
        private const val KEY_PRESET_PREFIX = "preset_mhz_"
        private val DEFAULT_PRESETS = listOf(88.0f, 94.6f, 100.2f, 105.5f)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _frequencyMhz = MutableStateFlow(loadSavedFrequency())
    val frequencyMhz: StateFlow<Float> = _frequencyMhz.asStateFlow()

    private val _presets = MutableStateFlow(loadSavedPresets())
    val presets: StateFlow<List<Float>> = _presets.asStateFlow()

    private val _isRadioActive = MutableStateFlow(false)
    val isRadioActive: StateFlow<Boolean> = _isRadioActive.asStateFlow()

    private val _stationInfo = MutableStateFlow("FM TUNER")
    val stationInfo: StateFlow<String> = _stationInfo.asStateFlow()

    @Volatile
    private var radioBinder: IBinder? = null

    @Volatile
    private var binderDescriptor: String = ""

    @Volatile
    private var radioAidlProxy: Any? = null

    @Volatile
    private var proxyMethods: List<Method> = emptyList()

    @Volatile
    private var lastReceivedBroadcastInfo: String = "None"

    @Volatile
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            radioBinder = service
            if (service != null) {
                scope.launch {
                    inspectAndAttachBinder(service)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            radioBinder = null
            radioAidlProxy = null
            proxyMethods = emptyList()
            isBound = false
        }
    }

    private val radioBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            val action = intent?.action ?: return
            scope.launch {
                try {
                    val extras = intent.extras
                    val sb = StringBuilder(action)
                    if (extras != null) {
                        for (key in extras.keySet()) {
                            val v = extras.get(key)
                            sb.append(" [$key=$v]")
                            // Check if any numeric extra represents FM frequency
                            when (v) {
                                is Int -> normalizeRawFreq(v)?.let { updateFrequencyFromSystem(it) }
                                is Float -> normalizeRawFreqFloat(v)?.let { updateFrequencyFromSystem(it) }
                                is Double -> normalizeRawFreqFloat(v.toFloat())?.let { updateFrequencyFromSystem(it) }
                                is String -> v.toFloatOrNull()?.let { f ->
                                    normalizeRawFreqFloat(f)?.let { updateFrequencyFromSystem(it) }
                                }
                            }
                        }
                    }
                    lastReceivedBroadcastInfo = sb.toString()
                } catch (e: Throwable) {
                    // Ignore malformed extras
                }
            }
        }
    }

    init {
        scope.launch {
            registerDynamicReceivers()
            ensureServiceBound()
            while (isActive) {
                pollRadioStatusAndFrequency()
                delay(2500L)
            }
        }
    }

    private fun loadSavedFrequency(): Float {
        val raw = prefs.getFloat(KEY_LAST_FREQ, 100.2f)
        return raw.coerceIn(87.5f, 108.0f)
    }

    private fun saveFrequency(freq: Float) {
        val clamped = ((freq * 10f).roundToInt() / 10f).coerceIn(87.5f, 108.0f)
        _frequencyMhz.value = clamped
        prefs.edit().putFloat(KEY_LAST_FREQ, clamped).apply()
    }

    private fun loadSavedPresets(): List<Float> {
        return (0 until 4).map { idx ->
            prefs.getFloat("$KEY_PRESET_PREFIX$idx", DEFAULT_PRESETS[idx]).coerceIn(87.5f, 108.0f)
        }
    }

    fun selectPreset(index: Int) {
        val currentPresets = _presets.value
        if (index in currentPresets.indices) {
            tuneFrequency(currentPresets[index])
        }
    }

    fun saveCurrentToPreset(index: Int) {
        if (index !in 0..3) return
        val currentFreq = _frequencyMhz.value
        val updated = _presets.value.toMutableList()
        updated[index] = currentFreq
        _presets.value = updated
        prefs.edit().putFloat("$KEY_PRESET_PREFIX$index", currentFreq).apply()
    }

    fun stepFrequency(deltaMhz: Float) {
        val next = ((_frequencyMhz.value + deltaMhz) * 10f).roundToInt() / 10f
        val wrapped = when {
            next > 108.0f -> 87.5f
            next < 87.5f -> 108.0f
            else -> next
        }
        tuneFrequency(wrapped)
    }

    fun tuneFrequency(freqMhz: Float) {
        val target = ((freqMhz * 10f).roundToInt() / 10f).coerceIn(87.5f, 108.0f)
        saveFrequency(target)
        scope.launch {
            ensureServiceBound()
            val freqKhz = (target * 1000f).roundToInt()      // e.g. 100200
            val freq10Khz = (target * 100f).roundToInt()     // e.g. 10020
            val freq100Khz = (target * 10f).roundToInt()     // e.g. 1002

            // 1. Try AIDL proxy methods on com.nwd.radio.service.RadioService
            val invokedAidl = invokeProxyTuneMethods(freqKhz, freq10Khz, freq100Khz, target)

            // 2. Also send startService / broadcast command to com.nwd.radio.service
            sendServiceCommand(
                cmd = "tune",
                freqKhz = freqKhz,
                freq10Khz = freq10Khz,
                freq100Khz = freq100Khz,
                freqMhz = target
            )

            if (invokedAidl) {
                delay(250L)
                pollRadioStatusAndFrequency()
            }
        }
    }

    fun seekNext() {
        stepFrequency(+0.1f)
        scope.launch {
            ensureServiceBound()
            val invoked = invokeProxySeekMethod(isNext = true)
            sendServiceCommand(cmd = "next")
            if (!invoked && _isRadioActive.value) {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            }
            delay(400L)
            pollRadioStatusAndFrequency()
        }
    }

    fun seekPrev() {
        stepFrequency(-0.1f)
        scope.launch {
            ensureServiceBound()
            val invoked = invokeProxySeekMethod(isNext = false)
            sendServiceCommand(cmd = "prev")
            if (!invoked && _isRadioActive.value) {
                dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
            delay(400L)
            pollRadioStatusAndFrequency()
        }
    }

    fun togglePlayPauseOrPower() {
        scope.launch {
            ensureServiceBound()
            val proxy = radioAidlProxy
            if (proxy != null) {
                val powerMethod = proxyMethods.firstOrNull { m ->
                    val n = m.name.lowercase()
                    m.parameterTypes.isEmpty() && (
                        n.contains("toggle") || n.contains("play") || n.contains("pause") ||
                            n.contains("open") || n.contains("start") || n.contains("power")
                        )
                }
                try {
                    powerMethod?.invoke(proxy)
                } catch (e: Throwable) {
                    // Ignore
                }
            }
            sendServiceCommand(cmd = if (_isRadioActive.value) "pause" else "play")
            delay(300L)
            pollRadioStatusAndFrequency()
        }
    }

    /**
     * Launches the NWD Radio UI (`com.nwd.radio.home_horizontalActivity`) directly.
     */
    fun launchNwdRadioApp(): Boolean {
        return try {
            val explicit = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(NWD_RADIO_UI_PKG, NWD_RADIO_UI_ACTIVITY)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(explicit)
            true
        } catch (e: Throwable) {
            try {
                val launch = context.packageManager.getLaunchIntentForPackage(NWD_RADIO_UI_PKG)
                if (launch != null) {
                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launch)
                    true
                } else {
                    false
                }
            } catch (e2: Throwable) {
                false
            }
        }
    }

    private fun ensureServiceBound() {
        if (radioBinder?.isBinderAlive == true) return
        try {
            val actionIntent = Intent(NWD_RADIO_SERVICE_ACTION).apply {
                setPackage(NWD_RADIO_SERVICE_PKG)
            }
            val boundByAction = context.bindService(actionIntent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (boundByAction) {
                isBound = true
                return
            }
        } catch (e: Throwable) {
            // Try explicit component next
        }

        try {
            val compIntent = Intent().apply {
                component = ComponentName(NWD_RADIO_SERVICE_PKG, NWD_RADIO_SERVICE_CLASS)
            }
            isBound = context.bindService(compIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Throwable) {
            // Ignore if head unit blocks binding
        }
    }

    private fun inspectAndAttachBinder(service: IBinder) {
        try {
            val desc = try {
                service.interfaceDescriptor.orEmpty()
            } catch (e: Throwable) {
                ""
            }
            binderDescriptor = desc

            val candidatePackages = listOf(NWD_RADIO_SERVICE_PKG, NWD_RADIO_UI_PKG, "com.nwd.kernel")
            for (pkg in candidatePackages) {
                try {
                    val pkgCtx = context.createPackageContext(
                        pkg,
                        Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                    )
                    val cl = pkgCtx.classLoader
                    val stubClassNames = buildList {
                        if (desc.isNotEmpty()) {
                            add("$desc\$Stub")
                            add(desc)
                        }
                        add("com.nwd.radio.service.IRadioService\$Stub")
                        add("com.nwd.radio.IRadioService\$Stub")
                        add("com.nwd.radio.aidl.IRadioService\$Stub")
                        add("com.nwd.carkit.radio.IRadioService\$Stub")
                    }
                    for (stubName in stubClassNames) {
                        try {
                            val stubClass = Class.forName(stubName, false, cl)
                            val asInterface = stubClass.methods.firstOrNull {
                                it.name == "asInterface" && it.parameterTypes.size == 1
                            }
                            val proxy = asInterface?.invoke(null, service)
                            if (proxy != null) {
                                radioAidlProxy = proxy
                                proxyMethods = proxy.javaClass.methods.filter { m ->
                                    m.declaringClass != Any::class.java
                                }
                                pollRadioStatusAndFrequency()
                                return
                            }
                        } catch (e: Throwable) {
                            // Try next candidate
                        }
                    }
                } catch (e: Throwable) {
                    // Try next package
                }
            }
        } catch (e: Throwable) {
            // Ignore reflection errors
        }
    }

    private fun pollRadioStatusAndFrequency() {
        try {
            val audioChannel = readSystemProperty("sys.nwd.audio.channel")
            val streamProc = readSystemProperty("sys.nwd.cur.music.stream.proc")
            val nwdRadioState = try {
                Settings.System.getInt(context.contentResolver, "nwd_radio_state", 0)
            } catch (e: Throwable) {
                0
            }
            val mcuSource = try {
                Settings.System.getInt(context.contentResolver, "mcu_current_source", -1)
            } catch (e: Throwable) {
                -1
            }

            val isFmChannel = audioChannel.equals("FM", ignoreCase = true) ||
                audioChannel.equals("AM", ignoreCase = true) ||
                streamProc.contains("com.nwd.radio", ignoreCase = true)
            val active = isFmChannel || (nwdRadioState == 1 && mcuSource == 4)
            _isRadioActive.value = active

            _stationInfo.value = when {
                isFmChannel -> "${audioChannel.uppercase(Locale.US)} • ON AIR"
                nwdRadioState == 1 -> "FM • READY"
                else -> "FM • STANDBY"
            }

            // Query frequency from AIDL proxy if attached
            val proxy = radioAidlProxy
            if (proxy != null && proxyMethods.isNotEmpty()) {
                val freqGetters = proxyMethods.filter { m ->
                    val n = m.name.lowercase()
                    m.parameterTypes.isEmpty() &&
                        (m.returnType == Int::class.javaPrimitiveType ||
                            m.returnType == Float::class.javaPrimitiveType ||
                            m.returnType == Long::class.javaPrimitiveType) &&
                        (n.contains("freq") || n.contains("channel") || n.contains("station"))
                }
                for (getter in freqGetters) {
                    try {
                        val res = getter.invoke(proxy)
                        when (res) {
                            is Int -> normalizeRawFreq(res)?.let {
                                updateFrequencyFromSystem(it)
                                return
                            }
                            is Long -> normalizeRawFreq(res.toInt())?.let {
                                updateFrequencyFromSystem(it)
                                return
                            }
                            is Float -> normalizeRawFreqFloat(res)?.let {
                                updateFrequencyFromSystem(it)
                                return
                            }
                        }
                    } catch (e: Throwable) {
                        // Try next getter
                    }
                }
            }
        } catch (e: Throwable) {
            // Ignore
        }
    }

    private fun invokeProxySeekMethod(isNext: Boolean): Boolean {
        val proxy = radioAidlProxy ?: return false
        val keywords = if (isNext) {
            listOf("seekup", "seeknext", "searchnext", "searchup", "next", "stepup", "autosearchup")
        } else {
            listOf("seekdown", "seekprev", "searchprev", "searchdown", "prev", "previous", "stepdown", "autosearchdown")
        }

        for (kw in keywords) {
            val method = proxyMethods.firstOrNull { m ->
                m.name.lowercase().contains(kw) && m.parameterTypes.isEmpty()
            }
            if (method != null) {
                try {
                    method.invoke(proxy)
                    return true
                } catch (e: Throwable) {
                    // Continue
                }
            }
        }
        return false
    }

    private fun invokeProxyTuneMethods(
        freqKhz: Int,
        freq10Khz: Int,
        freq100Khz: Int,
        freqMhz: Float
    ): Boolean {
        val proxy = radioAidlProxy ?: return false
        val tuneCandidates = proxyMethods.filter { m ->
            val n = m.name.lowercase()
            m.parameterTypes.size == 1 && (
                n.contains("freq") || n.contains("tune") || n.contains("play") || n.contains("channel")
                )
        }
        var invoked = false
        for (m in tuneCandidates) {
            try {
                when (m.parameterTypes[0]) {
                    Int::class.javaPrimitiveType -> {
                        // NWD typically uses 10kHz (e.g., 8750..10800) or 100kHz (875..1080) or kHz (87500..108000)
                        m.invoke(proxy, freq10Khz)
                        invoked = true
                    }
                    Float::class.javaPrimitiveType -> {
                        m.invoke(proxy, freqMhz)
                        invoked = true
                    }
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }
        return invoked
    }

    private fun sendServiceCommand(
        cmd: String,
        freqKhz: Int? = null,
        freq10Khz: Int? = null,
        freq100Khz: Int? = null,
        freqMhz: Float? = null
    ) {
        try {
            val serviceIntent = Intent(NWD_RADIO_SERVICE_ACTION).apply {
                component = ComponentName(NWD_RADIO_SERVICE_PKG, NWD_RADIO_SERVICE_CLASS)
                putExtra("cmd", cmd)
                putExtra("command", cmd)
                putExtra("action", cmd)
                if (freq10Khz != null) {
                    putExtra("freq", freq10Khz)
                    putExtra("frequency", freq10Khz)
                }
                if (freqKhz != null) {
                    putExtra("freq_khz", freqKhz)
                }
                if (freq100Khz != null) {
                    putExtra("freq_100khz", freq100Khz)
                }
                if (freqMhz != null) {
                    putExtra("freq_mhz", freqMhz)
                }
            }
            context.startService(serviceIntent)
        } catch (e: Throwable) {
            // Ignore if startService restricted
        }
    }

    private fun dispatchMediaKey(keyCode: Int) {
        try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
            val now = SystemClock.uptimeMillis()
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
            am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        } catch (e: Throwable) {
            // Ignore
        }
    }

    private fun registerDynamicReceivers() {
        try {
            val filter = IntentFilter().apply {
                addAction("com.nwd.radio.ACTION_RADIO_INFO")
                addAction("com.nwd.radio.ACTION_FREQ_CHANGE")
                addAction("com.nwd.radio.service.ACTION_RADIO_INFO")
                addAction("com.nwd.action.RADIO_FREQ_CHANGE")
                addAction("com.nwd.action. RADIO_STATE_CHANGE")
                addAction("android.intent.action.RADIO_INFO")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(radioBroadcastReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(radioBroadcastReceiver, filter)
            }
        } catch (e: Throwable) {
            // Ignore
        }
    }

    private fun normalizeRawFreq(raw: Int): Float? {
        return when (raw) {
            in 875..1080 -> raw / 10f
            in 8750..10800 -> raw / 100f
            in 87500..108000 -> raw / 1000f
            else -> null
        }
    }

    private fun normalizeRawFreqFloat(raw: Float): Float? {
        return when {
            raw in 87.5f..108.0f -> ((raw * 10f).roundToInt() / 10f)
            raw in 875f..1080f -> ((raw).roundToInt() / 10f)
            raw in 8750f..10800f -> ((raw / 10f).roundToInt() / 10f)
            raw in 87500f..108000f -> ((raw / 100f).roundToInt() / 10f)
            else -> null
        }
    }

    private fun updateFrequencyFromSystem(freqMhz: Float) {
        val rounded = ((freqMhz * 10f).roundToInt() / 10f).coerceIn(87.5f, 108.0f)
        if (_frequencyMhz.value != rounded) {
            saveFrequency(rounded)
        }
    }

    private fun readSystemProperty(key: String): String {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val getter = clazz.getMethod("get", String::class.java, String::class.java)
            (getter.invoke(null, key, "") as? String)?.trim().orEmpty()
        } catch (e: Throwable) {
            ""
        }
    }

    /**
     * Used by DiagnosticsManager on-demand to report the live binder state and discovered methods.
     */
    fun getLiveBinderDiagnostics(): String = buildString {
        appendLine("- Bound to RadioService: $isBound (Binder alive: ${radioBinder?.isBinderAlive == true})")
        appendLine("- IBinder Descriptor: ${binderDescriptor.ifEmpty { "(None / Not yet bound)" }}")
        appendLine("- AIDL Proxy Class: ${radioAidlProxy?.javaClass?.name ?: "(None)"}")
        if (proxyMethods.isNotEmpty()) {
            appendLine("- Discovered AIDL Proxy Methods (${proxyMethods.size}):")
            for (m in proxyMethods) {
                val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                appendLine("  • ${m.name}($params): ${m.returnType.simpleName}")
            }
        }
        appendLine("- Last Broadcast Received: $lastReceivedBroadcastInfo")
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(radioBroadcastReceiver)
        } catch (e: Throwable) {
            // Ignore
        }
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
            } catch (e: Throwable) {
                // Ignore
            }
            isBound = false
        }
    }
}
