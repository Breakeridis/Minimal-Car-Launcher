package com.minimal.carlauncher.service

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.provider.Settings
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
 * Native controller for Nowada (NWD) Allwinner head unit radio
 * (`com.nwd.radio` UI + `com.nwd.radio.service.RadioService` backend).
 *
 * Uses the verified AIDL interface `com.nwd.radio.service.RadioFeature` and
 * `com.nwd.radio.service.RadioConstant` broadcasts discovered from the head unit.
 * All IPC and reflection run strictly on Dispatchers.IO.
 */
class NwdRadioController(private val context: Context) {

    companion object {
        const val NWD_RADIO_UI_PKG = "com.nwd.radio"
        const val NWD_RADIO_UI_ACTIVITY = "com.nwd.radio.home_horizontalActivity"
        const val NWD_RADIO_SERVICE_PKG = "com.nwd.radio.service"
        const val NWD_RADIO_SERVICE_CLASS = "com.nwd.radio.service.RadioService"
        const val NWD_RADIO_SERVICE_ACTION = "com.nwd.radio.service.ACTION_RADIO_SERVICE"

        // Verified NWD RadioConstant Broadcast Actions
        const val ACTION_RADIO_STATE = "com.nwd.action.ACTION_RADIO_STATE"
        const val ACTION_REQUEST_RADIO_FREQUENCE = "com.nwd.action.radiowidget_request_frequence"
        const val ACTION_SEND_RADIO_FREQUENCE = "com.nwd.action.ACTION_SEND_RADIO_FREQUENCE"
        const val ACTION_SEND_RADIO_FREQUENCE_NEW = "com.nwd.action.ACTION_SEND_RADIO_FREQUENCE_NEW"
        const val ACTION_SEND_SCAN_RADIO_FREQUENCE = "com.nwd.action.ACTION_SEND_SCAN_RADIO_FREQUENCE"
        const val ACTION_SET_RADIO_FREQUENCE = "com.nwd.action.ACTION_SET_RADIO_FREQUENCE"

        // Verified NWD RadioConstant Extras & Settings Keys
        const val EXTRA_BAND = "extra_band"
        const val EXTRA_FREQUENCE = "extra_frequence"
        const val EXTRA_RADIO_FREQUENCE = "extra_radio_frequence"
        const val EXTRA_RADIO_PRESTORE_NUM = "extra_radio_prestore_num"
        const val EXTRA_RADIO_STATE = "nwd_radio_state"
        const val KEY_RADIO_CURRENT_FREQ = "nwd_radio_current_freq"
        const val KEY_RADIO_CURRENT_BAND = "nwd_radio_current_band"
        const val KEY_RADIO_CURRENT_PS_DATA = "nwd_radio_current_ps_data"

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
    private var currentBandByte: Byte = 0

    @Volatile
    private var lastReceivedBroadcastInfo: String = "None"

    @Volatile
    private var lastFrequencyObjectDump: String = "None"

    @Volatile
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            radioBinder = service
            if (service != null) {
                scope.launch {
                    inspectAndAttachBinder(service)
                    requestWidgetFrequencyBroadcast()
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
                        }
                        // Check verified NWD frequency extras ("extra_frequence", "extra_radio_frequence")
                        val rawFreq = when {
                            extras.containsKey(EXTRA_FREQUENCE) -> extras.getInt(EXTRA_FREQUENCE, -1)
                            extras.containsKey(EXTRA_RADIO_FREQUENCE) -> extras.getInt(EXTRA_RADIO_FREQUENCE, -1)
                            else -> -1
                        }
                        if (rawFreq > 0) {
                            normalizeRawFreq(rawFreq)?.let { updateFrequencyFromSystem(it) }
                        }
                        if (extras.containsKey(EXTRA_BAND)) {
                            val b = extras.get(EXTRA_BAND)
                            if (b is Byte) currentBandByte = b
                            else if (b is Int) currentBandByte = b.toByte()
                        }
                    }
                    lastReceivedBroadcastInfo = sb.toString()
                    pollRadioStatusAndFrequency()
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
            requestWidgetFrequencyBroadcast()
            while (isActive) {
                pollRadioStatusAndFrequency()
                delay(2000L)
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
            tuneFrequency(currentPresets[index], presetIndex = index)
        }
    }

    fun saveCurrentToPreset(index: Int) {
        if (index !in 0..3) return
        val currentFreq = _frequencyMhz.value
        val updated = _presets.value.toMutableList()
        updated[index] = currentFreq
        _presets.value = updated
        prefs.edit().putFloat("$KEY_PRESET_PREFIX$index", currentFreq).apply()

        // Also save to NWD RadioService preset slot if bound
        scope.launch {
            val proxy = radioAidlProxy ?: return@launch
            try {
                val saveMethod = proxyMethods.firstOrNull {
                    it.name == "saveCurrentFrequency" && it.parameterTypes.size == 1
                }
                saveMethod?.invoke(proxy, index.toByte())
            } catch (e: Throwable) {
                // Ignore
            }
        }
    }

    /**
     * Tunes directly to a specific FM frequency in MHz (e.g. 100.2 MHz -> 10020 in NWD 10kHz format).
     * Uses both `RadioFeature.setCurrentFrequency(int, byte, int)` and `ACTION_SET_RADIO_FREQUENCE`.
     */
    fun tuneFrequency(freqMhz: Float, presetIndex: Int = -1) {
        val target = ((freqMhz * 10f).roundToInt() / 10f).coerceIn(87.5f, 108.0f)
        saveFrequency(target)
        scope.launch {
            ensureServiceBound()
            // NWD AWFmUtils uses 8750..10800 (10 kHz units, STEP_FM = 10)
            val freq10Khz = (target * 100f).roundToInt()

            // 1. Invoke verified RadioFeature.setCurrentFrequency(int, byte, int)
            val invokedAidl = invokeSetCurrentFrequency(freq10Khz, currentBandByte, presetIndex)

            // 2. Also broadcast verified RadioConstant.ACTION_SET_RADIO_FREQUENCE
            try {
                val setFreqIntent = Intent(ACTION_SET_RADIO_FREQUENCE).apply {
                    putExtra(EXTRA_FREQUENCE, freq10Khz)
                    putExtra(EXTRA_RADIO_FREQUENCE, freq10Khz)
                    putExtra(EXTRA_BAND, currentBandByte)
                    if (presetIndex >= 0) {
                        putExtra(EXTRA_RADIO_PRESTORE_NUM, presetIndex)
                    }
                }
                context.sendBroadcast(setFreqIntent)
            } catch (e: Throwable) {
                // Ignore
            }

            if (invokedAidl) {
                delay(200L)
                pollRadioStatusAndFrequency()
            }
        }
    }

    /**
     * Seeks to the next strong FM station via `RadioFeature.seek(boolean)`.
     */
    fun seekNext() {
        scope.launch {
            ensureServiceBound()
            val beforeFreq = _frequencyMhz.value
            val invoked = invokeRadioSeek(isNext = true)
            if (!invoked) {
                // Fallback to +0.1 MHz step via setCurrentFrequency
                val next = if (beforeFreq + 0.1f > 108.0f) 87.5f else beforeFreq + 0.1f
                tuneFrequency(next)
                return@launch
            }
            // Poll frequency updates as the tuner seeks
            repeat(6) {
                delay(250L)
                pollRadioStatusAndFrequency()
            }
        }
    }

    /**
     * Seeks to the previous strong FM station via `RadioFeature.seek(boolean)`.
     */
    fun seekPrev() {
        scope.launch {
            ensureServiceBound()
            val beforeFreq = _frequencyMhz.value
            val invoked = invokeRadioSeek(isNext = false)
            if (!invoked) {
                // Fallback to -0.1 MHz step via setCurrentFrequency
                val prev = if (beforeFreq - 0.1f < 87.5f) 108.0f else beforeFreq - 0.1f
                tuneFrequency(prev)
                return@launch
            }
            // Poll frequency updates as the tuner seeks
            repeat(6) {
                delay(250L)
                pollRadioStatusAndFrequency()
            }
        }
    }

    fun togglePlayPauseOrPower() {
        scope.launch {
            ensureServiceBound()
            val proxy = radioAidlProxy
            if (proxy != null) {
                try {
                    val setBackOn = proxyMethods.firstOrNull {
                        it.name == "setRadioBackServiceOn" && it.parameterTypes.size == 1
                    }
                    setBackOn?.invoke(proxy, !_isRadioActive.value)
                } catch (e: Throwable) {
                    // Ignore
                }
            }
            requestWidgetFrequencyBroadcast()
            delay(250L)
            pollRadioStatusAndFrequency()
        }
    }

    /**
     * Launches the NWD Radio UI (`com.nwd.radio.home_horizontalActivity`) directly.
     */
    fun launchNwdRadioApp(): Boolean {
        try {
            val launch = context.packageManager.getLaunchIntentForPackage(NWD_RADIO_UI_PKG)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(launch)
                return true
            }
        } catch (e: Throwable) {
            // Try explicit activity next
        }

        return try {
            val explicit = Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(NWD_RADIO_UI_PKG, NWD_RADIO_UI_ACTIVITY)
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(explicit)
            true
        } catch (e: Throwable) {
            false
        }
    }

    private fun requestWidgetFrequencyBroadcast() {
        try {
            context.sendBroadcast(Intent(ACTION_REQUEST_RADIO_FREQUENCE))
        } catch (e: Throwable) {
            // Ignore
        }
    }

    private fun ensureServiceBound() {
        if (radioBinder?.isBinderAlive == true && radioAidlProxy != null) return
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

            val candidatePackages = listOf(NWD_RADIO_SERVICE_PKG, NWD_RADIO_UI_PKG)
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
                        }
                        add("com.nwd.radio.service.RadioFeature\$Stub")
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
                Settings.System.getInt(context.contentResolver, EXTRA_RADIO_STATE, 0)
            } catch (e: Throwable) {
                0
            }
            val mcuSource = try {
                Settings.System.getInt(context.contentResolver, "mcu_current_source", -1)
            } catch (e: Throwable) {
                -1
            }

            // Also check Settings.System "nwd_radio_current_freq" if populated
            try {
                val sysFreq = Settings.System.getInt(context.contentResolver, KEY_RADIO_CURRENT_FREQ, -1)
                if (sysFreq > 0) {
                    normalizeRawFreq(sysFreq)?.let { updateFrequencyFromSystem(it) }
                }
            } catch (e: Throwable) {
                // Ignore
            }

            val isFmChannel = audioChannel.equals("FM", ignoreCase = true) ||
                audioChannel.equals("AM", ignoreCase = true) ||
                streamProc.contains("com.nwd.radio", ignoreCase = true)
            val active = isFmChannel || (nwdRadioState == 1 && mcuSource == 4)
            _isRadioActive.value = active

            // Query `getCurrentFrequency(): Frequency` and `getRtMessage(): String` from RadioFeature proxy
            val proxy = radioAidlProxy
            var rdsText = ""
            if (proxy != null && proxyMethods.isNotEmpty()) {
                try {
                    val getCurrentFreqMethod = proxyMethods.firstOrNull {
                        it.name == "getCurrentFrequency" && it.parameterTypes.isEmpty()
                    }
                    val freqObj = getCurrentFreqMethod?.invoke(proxy)
                    if (freqObj != null) {
                        extractFrequencyFromNwdObject(freqObj)
                    }
                } catch (e: Throwable) {
                    // Ignore
                }

                try {
                    val getRtMethod = proxyMethods.firstOrNull {
                        it.name == "getRtMessage" && it.parameterTypes.isEmpty()
                    }
                    rdsText = (getRtMethod?.invoke(proxy) as? String)?.trim().orEmpty()
                } catch (e: Throwable) {
                    // Ignore
                }
            }

            _stationInfo.value = when {
                rdsText.isNotEmpty() -> rdsText
                isFmChannel -> "${audioChannel.uppercase(Locale.US)} • ON AIR"
                nwdRadioState == 1 -> "FM • READY"
                else -> "FM • STANDBY"
            }
        } catch (e: Throwable) {
            // Ignore
        }
    }

    /**
     * Extracts frequency (e.g. 8750..10800 -> 87.5..108.0 MHz) and bandType from NWD's `Frequency` Parcelable object.
     */
    private fun extractFrequencyFromNwdObject(freqObj: Any) {
        val cls = freqObj.javaClass
        val sb = StringBuilder(cls.name)

        // 1. Check declared fields on Frequency object
        for (f in cls.declaredFields) {
            try {
                f.isAccessible = true
                val v = f.get(freqObj)
                sb.append(" ${f.name}=$v")
                when (v) {
                    is Int -> normalizeRawFreq(v)?.let { updateFrequencyFromSystem(it) }
                    is Byte -> if (f.name.lowercase().contains("band")) currentBandByte = v
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }

        // 2. Check zero-arg getter methods on Frequency object
        for (m in cls.methods) {
            if (m.parameterTypes.isNotEmpty() || m.declaringClass == Any::class.java) continue
            try {
                val n = m.name.lowercase()
                if (m.returnType == Int::class.javaPrimitiveType && (n.contains("freq") || n.contains("value") || n.contains("station"))) {
                    val v = m.invoke(freqObj) as? Int ?: continue
                    sb.append(" ${m.name}()=$v")
                    normalizeRawFreq(v)?.let { updateFrequencyFromSystem(it) }
                } else if (m.returnType == Byte::class.javaPrimitiveType && n.contains("band")) {
                    val b = m.invoke(freqObj) as? Byte ?: continue
                    currentBandByte = b
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }
        lastFrequencyObjectDump = sb.toString()
    }

    /**
     * Invokes `RadioFeature.seek(boolean)` on `com.nwd.radio.service.RadioFeature`.
     */
    private fun invokeRadioSeek(isNext: Boolean): Boolean {
        val proxy = radioAidlProxy ?: return false
        val seekMethod = proxyMethods.firstOrNull {
            it.name == "seek" &&
                it.parameterTypes.size == 1 &&
                it.parameterTypes[0] == Boolean::class.javaPrimitiveType
        }
        if (seekMethod != null) {
            try {
                // In NWD RadioFeature: seek(boolean)
                seekMethod.invoke(proxy, isNext)
                return true
            } catch (e: Throwable) {
                // Ignore
            }
        }
        return false
    }

    /**
     * Invokes `RadioFeature.setCurrentFrequency(int freq, byte band, int index)` on `com.nwd.radio.service.RadioFeature`.
     */
    private fun invokeSetCurrentFrequency(freq10Khz: Int, band: Byte, presetIndex: Int): Boolean {
        val proxy = radioAidlProxy ?: return false
        val setFreqMethod = proxyMethods.firstOrNull {
            it.name == "setCurrentFrequency" && it.parameterTypes.size == 3
        }
        if (setFreqMethod != null) {
            try {
                setFreqMethod.invoke(proxy, freq10Khz, band, presetIndex.coerceAtLeast(0))
                return true
            } catch (e: Throwable) {
                // Ignore
            }
        }
        return false
    }

    private fun registerDynamicReceivers() {
        try {
            val filter = IntentFilter().apply {
                addAction(ACTION_SEND_RADIO_FREQUENCE)
                addAction(ACTION_SEND_RADIO_FREQUENCE_NEW)
                addAction(ACTION_SEND_SCAN_RADIO_FREQUENCE)
                addAction(ACTION_RADIO_STATE)
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
        appendLine("- Last Frequency Object Dump: $lastFrequencyObjectDump")
        appendLine("- Last Broadcast Received: $lastReceivedBroadcastInfo")
        if (proxyMethods.isNotEmpty()) {
            appendLine("- Discovered AIDL Proxy Methods (${proxyMethods.size}):")
            for (m in proxyMethods) {
                val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                appendLine("  • ${m.name}($params): ${m.returnType.simpleName}")
            }
        }
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
