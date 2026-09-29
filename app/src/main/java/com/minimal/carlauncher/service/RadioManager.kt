package com.minimal.carlauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Locale
import kotlin.math.roundToInt

/**
 * RadioManager tracks the active radio station and frequency on Android automotive head units,
 * with deep optimization for Allwinner K2401 / QF (QuickFish / K706 / ROCO) and NWD (Nowada) platforms.
 *
 * Implements strict frequency validation and token blacklisting to completely eliminate false detections
 * (such as system airplane mode network settings "cell,bluetooth,wifi,nfc").
 */
class RadioManager(private val context: Context) {

    private val _radioStation = MutableStateFlow<String?>(null)
    val radioStation: StateFlow<String?> = _radioStation.asStateFlow()

    private val _isRadioActive = MutableStateFlow(false)
    val isRadioActive: StateFlow<Boolean> = _isRadioActive.asStateFlow()

    private var isMonitoring = false
    private var pollingJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Comprehensive QF, Allwinner, NWD, and generic automotive Settings keys
    private val observedSettingsKeys = listOf(
        // QuickFish (QF / K2401 / K706 / ROCO)
        "qf_radio_cur_freq",
        "qf_radio_freq",
        "qf_radio_current_freq",
        "qf_radio_frequency",
        "qf_cur_freq",
        "qf_curfreq",
        "qf_freq",
        "qf_radio_station",
        "qf_radio_name",
        "qf_radio_band",
        "com.qf.radio.freq",
        "com.qf.radio.frequency",
        "com.qf.radio.cur_freq",
        "com.qf.radio.station",
        "com.qf.radio.name",

        // Allwinner / Softwinner
        "allwinner_radio_cur_freq",
        "allwinner_radio_freq",
        "allwinner_freq",
        "allwinner_radio_station",
        "allwinner_radio_name",
        "softwinner_radio_cur_freq",
        "softwinner_radio_freq",
        "softwinner_freq",
        "com.allwinner.radio.freq",
        "com.allwinner.radio.cur_freq",
        "com.allwinner.radio.station",

        // Nowada (NWD)
        "nwd_radio_current_freq",
        "nwd_radio_freq",
        "nwd_radio_name",
        "nwd_radio_band",
        "nwd_freq",
        "nwd_cur_freq",

        // Generic Automotive & MCU
        "radio_cur_freq",
        "radio_freq",
        "cur_freq",
        "curfreq",
        "radio_current_freq",
        "radio_frequency",
        "radio_station",
        "radio_station_name",
        "radio_name",
        "radio_band",
        "radio_channel",
        "radio_play_freq",
        "radio_play_frequency",
        "fm_freq",
        "fm_frequency",
        "fm_station",
        "curRadioFreq",
        "currentRadioFreq",
        "current_radio_freq",
        "Radio_Freq",
        "RADIO_FREQ",
        "Radio_Current_Freq",
        "RADIO_CURRENT_FREQ",
        "mcu_radio_freq",
        "mcu_radio_cur_freq",
        "mcu_freq",
        "radio_last_freq",
        "last_radio_freq",
        "radio_ps",
        "radio_rds",
        "radio_rt",
        "sys.radio.freq",
        "persist.sys.radio.freq",

        // TopWay / TS / FYT / Syu / MTK
        "ts_radio_freq",
        "fyt_radio_freq",
        "syu_radio_freq",
        "tuner_freq",
        "hw_radio_freq"
    )

    private val systemPropertyKeys = listOf(
        "persist.sys.radio.freq",
        "persist.radio.freq",
        "persist.radio.cur_freq",
        "sys.radio.freq",
        "sys.radio.cur_freq",
        "ro.radio.freq",
        "qf.radio.freq",
        "qf.radio.cur_freq",
        "persist.qf.radio.freq",
        "persist.qf.cur_freq",
        "nwd.radio.freq",
        "persist.nwd.radio.freq",
        "persist.sys.radio.cur_freq",
        "allwinner.radio.freq",
        "persist.allwinner.radio.freq",
        "radio.freq",
        "hw.radio.freq"
    )

    private val settingsObserver = object : ContentObserver(mainHandler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            readCurrentSettingsFrequency()
        }
    }

    private val radioReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            extractFromIntent(intent)
        }
    }

    init {
        activeInstance = WeakReference(this)
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        activeInstance = WeakReference(this)

        // 1. Initial read from System Settings and Properties
        readCurrentSettingsFrequency()

        // 2. Register ContentObservers for real-time changes
        registerSettingsObservers()

        // 3. Register BroadcastReceiver for vendor events
        registerBroadcastReceiver()

        // 4. Background polling loop every 2.5s for continuous sync
        pollingJob?.cancel()
        pollingJob = CoroutineScope(Dispatchers.IO).launch {
            while (isMonitoring) {
                delay(2500L)
                readCurrentSettingsFrequency()
            }
        }
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false

        pollingJob?.cancel()
        pollingJob = null

        try {
            context.contentResolver.unregisterContentObserver(settingsObserver)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            context.unregisterReceiver(radioReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun readCurrentSettingsFrequency() {
        try {
            // First sanity check: purge any stale blacklisted values from current state
            val currentStation = _radioStation.value
            if (currentStation != null && BLACKLISTED_TOKENS.any { currentStation.lowercase().contains(it) }) {
                _radioStation.value = null
                _isRadioActive.value = false
            }

            val resolver = context.contentResolver
            var freqVal: String? = null
            var nameVal: String? = null
            var bandVal: String? = null

            // Check if radio power is explicitly reported off
            val powerStateKeys = listOf(
                "qf_radio_power", "radio_power", "radio_state", "qf_radio_state",
                "radio_play", "radio_play_state", "nwd_radio_state"
            )
            for (pk in powerStateKeys) {
                val p = Settings.System.getString(resolver, pk)
                if (p == "0" || p.equals("false", ignoreCase = true) || p.equals("off", ignoreCase = true)) {
                    _isRadioActive.value = false
                    _radioStation.value = null
                    return
                }
            }

            // 1. Check known Settings.System keys
            for (key in observedSettingsKeys) {
                try {
                    val v = Settings.System.getString(resolver, key)
                    if (!v.isNullOrBlank() && v != "0" && v != "-1" && !v.equals("null", ignoreCase = true)) {
                        if (formatFrequency(v, null, null) != null) {
                            freqVal = v
                            break
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            // 2. Fallback: Check Settings.Global
            if (freqVal.isNullOrBlank()) {
                for (key in observedSettingsKeys) {
                    try {
                        val v = Settings.Global.getString(resolver, key)
                        if (!v.isNullOrBlank() && v != "0" && v != "-1" && !v.equals("null", ignoreCase = true)) {
                            if (formatFrequency(v, null, null) != null) {
                                freqVal = v
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            // 3. Fallback: Check Settings.Secure
            if (freqVal.isNullOrBlank()) {
                for (key in observedSettingsKeys) {
                    try {
                        val v = Settings.Secure.getString(resolver, key)
                        if (!v.isNullOrBlank() && v != "0" && v != "-1" && !v.equals("null", ignoreCase = true)) {
                            if (formatFrequency(v, null, null) != null) {
                                freqVal = v
                                break
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            // 4. Fallback: Query system settings table cursor directly (with strict filters)
            if (freqVal.isNullOrBlank()) {
                val cursorResult = scanSystemSettingsCursor()
                if (cursorResult != null) {
                    freqVal = cursorResult.first
                    if (nameVal.isNullOrBlank()) nameVal = cursorResult.second
                    if (bandVal.isNullOrBlank()) bandVal = cursorResult.third
                }
            }

            // 5. Fallback: Check SystemProperties via reflection
            if (freqVal.isNullOrBlank()) {
                for (prop in systemPropertyKeys) {
                    val pVal = readSystemProperty(prop)
                    if (!pVal.isNullOrBlank() && pVal != "0" && pVal != "-1") {
                        if (formatFrequency(pVal, null, null) != null) {
                            freqVal = pVal
                            break
                        }
                    }
                }
            }

            // Read Station Name
            if (nameVal.isNullOrBlank()) {
                val nameKeys = listOf(
                    "qf_radio_name", "qf_radio_station", "qf_station", "qf_name",
                    "allwinner_radio_station", "allwinner_radio_name",
                    "nwd_radio_name", "radio_name", "radio_station", "radio_station_name",
                    "radio_ps", "radio_rds", "radio_rt"
                )
                for (nk in nameKeys) {
                    val nv = Settings.System.getString(resolver, nk)
                        ?: Settings.Global.getString(resolver, nk)
                    if (!nv.isNullOrBlank() && !nv.equals("null", ignoreCase = true) && nv != "0" && nv != "-1") {
                        if (nv.length in 2..40 && BLACKLISTED_TOKENS.none { nv.lowercase().contains(it) }) {
                            nameVal = nv.trim()
                            break
                        }
                    }
                }
            }

            // Read Band
            if (bandVal.isNullOrBlank()) {
                val bandKeys = listOf("qf_radio_band", "nwd_radio_band", "radio_band", "qf_band")
                for (bk in bandKeys) {
                    val bv = Settings.System.getString(resolver, bk)
                        ?: Settings.Global.getString(resolver, bk)
                    if (!bv.isNullOrBlank() && !bv.equals("null", ignoreCase = true)) {
                        bandVal = bv.trim()
                        break
                    }
                }
            }

            if (!freqVal.isNullOrBlank()) {
                val formatted = formatFrequency(freqVal, bandVal, nameVal)
                if (formatted != null) {
                    _radioStation.value = formatted
                    _isRadioActive.value = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scanSystemSettingsCursor(): Triple<String, String?, String?>? {
        val uris = listOf(
            Settings.System.CONTENT_URI,
            Settings.Global.CONTENT_URI
        )

        for (uri in uris) {
            try {
                val cursor = context.contentResolver.query(
                    uri,
                    arrayOf("name", "value"),
                    null, null, null
                ) ?: continue

                var foundFreq: String? = null
                var foundName: String? = null
                var foundBand: String? = null

                cursor.use { c ->
                    val nameCol = c.getColumnIndex("name")
                    val valCol = c.getColumnIndex("value")
                    if (nameCol != -1 && valCol != -1) {
                        while (c.moveToNext()) {
                            val name = c.getString(nameCol) ?: continue
                            val value = c.getString(valCol) ?: continue
                            val lowerName = name.lowercase()

                            // 1. Immediately discard blacklisted system settings (airplane mode, wifi, bluetooth, etc.)
                            if (BLACKLISTED_TOKENS.any { lowerName.contains(it) }) continue

                            // 2. Discard empty / invalid values
                            if (value.isBlank() || value == "0" || value == "-1" || value.equals("null", ignoreCase = true)) continue

                            // 3. Scan candidate frequency settings
                            val isFreqCandidate = lowerName.startsWith("qf_") ||
                                lowerName.startsWith("nwd_") ||
                                lowerName.startsWith("allwinner_") ||
                                lowerName.startsWith("softwinner_") ||
                                lowerName.startsWith("mcu_") ||
                                lowerName.contains("radio_freq") ||
                                lowerName.contains("cur_freq") ||
                                lowerName.contains("current_freq") ||
                                lowerName.contains("curfreq") ||
                                lowerName.contains("fm_freq") ||
                                (lowerName.contains("radio") && (lowerName.contains("freq") || lowerName.contains("chan") || lowerName.contains("channel")))

                            if (foundFreq == null && isFreqCandidate) {
                                val formatted = formatFrequency(value, null, null)
                                if (formatted != null) {
                                    foundFreq = value
                                }
                            }

                            // 4. Scan candidate station name settings
                            val isNameCandidate = lowerName.contains("radio_name") ||
                                lowerName.contains("radio_station") ||
                                lowerName.contains("radio_ps") ||
                                lowerName.contains("radio_rds") ||
                                lowerName.contains("radio_rt") ||
                                lowerName.contains("qf_radio_name") ||
                                lowerName.contains("qf_radio_station") ||
                                lowerName.contains("qf_station") ||
                                lowerName.contains("qf_name") ||
                                lowerName.contains("nwd_radio_name") ||
                                lowerName.contains("allwinner_radio_station")

                            if (foundName == null && isNameCandidate) {
                                if (value.length in 2..40 && BLACKLISTED_TOKENS.none { value.lowercase().contains(it) }) {
                                    foundName = value.trim()
                                }
                            }

                            // 5. Scan candidate band settings
                            val isBandCandidate = lowerName.contains("radio_band") ||
                                lowerName.contains("qf_radio_band") ||
                                lowerName.contains("nwd_radio_band")

                            if (foundBand == null && isBandCandidate) {
                                foundBand = value.trim()
                            }
                        }
                    }
                }

                if (foundFreq != null) {
                    return Triple(foundFreq, foundName, foundBand)
                }
            } catch (e: Throwable) {
                // Ignore security exceptions on restricted tables
            }
        }
        return null
    }

    private fun readSystemProperty(key: String): String? {
        return try {
            val c = Class.forName("android.os.SystemProperties")
            val get = c.getMethod("get", String::class.java)
            val v = get.invoke(null, key) as? String
            if (!v.isNullOrBlank()) v else null
        } catch (e: Throwable) {
            null
        }
    }

    private fun registerSettingsObservers() {
        val resolver = context.contentResolver

        // 1. Observe entire Settings.System for any modification
        try {
            resolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                settingsObserver
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Observe specific key URIs as explicit backup
        for (key in observedSettingsKeys) {
            try {
                val uri = Settings.System.getUriFor(key)
                if (uri != null) {
                    resolver.registerContentObserver(uri, false, settingsObserver)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            // QuickFish (QF / K2401 / K706 / ROCO)
            addAction("com.qf.radio.update_action")
            addAction("com.qf.action.RADIO")
            addAction("com.qf.action.RADIO_INFO")
            addAction("com.qf.action.RADIO_STATE")
            addAction("com.qf.radio")
            addAction("com.qf.fmradio")
            addAction("com.qf.radio.REPORT")
            addAction("com.qf.radio.action")
            addAction("com.qf.action.ACC_ON")

            // Nowada (NWD)
            addAction("com.nwd.action.ACTION_SEND_RADIO_FREQUENCE_NEW")
            addAction("ACTION_SEND_RADIO_FREQUENCE_NEW")
            addAction("com.nwd.radio.REPORT")
            addAction("com.nwd.ACTION_CHANGE_SOURCE")
            addAction("com.nwd.action.ACTION_RADIO_INFO")
            addAction("com.nwd.action.ACTION_RADIO_STATE")
            addAction("com.nwd.action.ACTION_SEND_RADIO_INFO")
            addAction("com.nwd.radio")

            // Allwinner / Softwinner
            addAction("com.allwinner.radio.station_changed")
            addAction("com.allwinner.radio.REPORT")
            addAction("com.allwinner.radio")
            addAction("com.allwinner.action.RADIO_INFO")
            addAction("com.softwinner.radio.REPORT")
            addAction("com.softwinner.radio.station_changed")
            addAction("com.softwinner.radio")

            // Standard Android Media & Music
            addAction("com.android.music.metachanged")
            addAction("com.android.music.playstatechanged")
            addAction("com.android.music.playbackcomplete")
            addAction("com.android.music.queuechanged")
            addAction("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION")

            // Microntek (MTC)
            addAction("com.microntek.radiostate")
            addAction("com.microntek.radio.report")
            addAction("com.microntek.sync")

            // FYT / Syu / Joying
            addAction("com.syu.radio")
            addAction("com.syu.radio.freq")
            addAction("com.syu.ms.radio")
            addAction("com.syu.radio.station")

            // Topway / TS / XYAuto
            addAction("com.ts.radio.broadcast")
            addAction("com.ts.radio")
            addAction("com.xyauto.radio")
            addAction("com.forfan.radio")

            // NavRadio / Navimods
            addAction("com.navimods.radio.status")
            addAction("com.navimods.radio.station")
            addAction("com.navimods.radio")

            // AOSP / Generic Automotive
            addAction("android.intent.action.RADIO_STATE_CHANGED")
            addAction("android.hardware.radio.action.STATION_CHANGED")
            addAction("com.android.fmradio.FM_ENABLED")
            addAction("com.android.fmradio.FM_FREQUENCY_CHANGED")
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(radioReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(radioReceiver, filter)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun checkIntentRadioOff(intent: Intent): Boolean {
        val extras = intent.extras ?: return false
        val stateKeys = listOf("state", "play", "isPlay", "isPlaying", "power", "enable")
        for (k in stateKeys) {
            if (extras.containsKey(k)) {
                val v = extras.get(k)
                if (v == false || v == 0 || v == "0" || v == "false" || v == "off") {
                    return true
                }
            }
        }
        return false
    }

    fun extractFromIntent(intent: Intent) {
        val action = intent.action ?: ""
        val lowerAction = action.lowercase()
        if (BLACKLISTED_TOKENS.any { lowerAction.contains(it) }) return

        val extras = intent.extras ?: return

        // Check if intent signals radio turned off
        if (checkIntentRadioOff(intent)) {
            _isRadioActive.value = false
            _radioStation.value = null
            return
        }

        var detectedFreq: Any? = null
        var detectedName: String? = null
        var detectedBand: String? = null

        // 1. Check known frequency extra keys
        val freqKeys = listOf(
            "extra_radio_frequence", "freq", "frequency", "cur_freq", "current_freq",
            "radio:freq", "radio_freq", "qf_freq", "qf_radio_freq", "curRadioFreq",
            "currentRadioFreq", "channel", "frequence", "radio_frequence", "nwd_freq",
            "mFreq", "station_freq", "play_freq", "tuner_freq",
            "EXTRA_RADIO_FREQUENCY", "EXTRA_FREQUENCY", "FREQ", "CUR_FREQ"
        )
        for (k in freqKeys) {
            if (extras.containsKey(k)) {
                val v = extras.get(k) ?: continue
                val formatted = formatFrequency(v, null, null)
                if (formatted != null) {
                    detectedFreq = v
                    break
                }
            }
        }

        // 2. Check known station name extra keys
        val nameKeys = listOf(
            "extra_radio_name", "name", "station", "ps", "radio:name", "rds",
            "station_name", "title", "track", "label", "qf_station", "qf_name",
            "rds_ps", "rds_name", "radio_ps", "radio_name", "EXTRA_STATION_NAME"
        )
        for (k in nameKeys) {
            if (extras.containsKey(k)) {
                val v = extras.get(k)?.toString()?.trim()
                if (!v.isNullOrBlank() && !v.equals("null", ignoreCase = true) && v.length in 2..40) {
                    if (BLACKLISTED_TOKENS.none { v.lowercase().contains(it) }) {
                        detectedName = v
                        break
                    }
                }
            }
        }

        // 3. Check known band extra keys
        val bandKeys = listOf("extra_radio_band", "band", "radio:band", "type", "qf_band", "EXTRA_RADIO_BAND")
        for (k in bandKeys) {
            if (extras.containsKey(k)) {
                val v = extras.get(k)?.toString()?.trim()
                if (!v.isNullOrBlank()) {
                    detectedBand = v
                    break
                }
            }
        }

        // 4. Fallback: Scan all keys in the Bundle for any valid radio frequency value or array
        if (detectedFreq == null) {
            for (key in extras.keySet()) {
                val lowerKey = key.lowercase()
                if (BLACKLISTED_TOKENS.any { lowerKey.contains(it) }) continue

                val v = extras.get(key) ?: continue

                // Check IntArray or LongArray (common in MCU payloads)
                if (v is IntArray) {
                    for (intVal in v) {
                        if (formatFrequency(intVal, detectedBand, null) != null) {
                            detectedFreq = intVal
                            break
                        }
                    }
                    if (detectedFreq != null) break
                } else if (v is LongArray) {
                    for (longVal in v) {
                        if (formatFrequency(longVal, detectedBand, null) != null) {
                            detectedFreq = longVal
                            break
                        }
                    }
                    if (detectedFreq != null) break
                } else {
                    val formatted = formatFrequency(v, detectedBand, null)
                    if (formatted != null) {
                        detectedFreq = v
                        break
                    }
                }
            }
        }

        if (detectedFreq != null) {
            val formatted = formatFrequency(detectedFreq, detectedBand, detectedName)
            if (formatted != null) {
                _radioStation.value = formatted
                _isRadioActive.value = true
                return
            }
        }

        // If intent had no recognizable frequency, query system settings
        readCurrentSettingsFrequency()
    }

    companion object {
        private var activeInstance: WeakReference<RadioManager>? = null

        /**
         * Global blacklist of tokens that belong to Android OS system settings or connectivity features,
         * NEVER to broadcast radio.
         */
        val BLACKLISTED_TOKENS = listOf(
            "airplane", "toggleable", "cell", "wifi", "bluetooth", "nfc", "telephony",
            "network", "mobile", "carrier", "sim", "gps", "device_name", "volume",
            "mute", "gain", "switch", "package", "service", "provider", "version",
            "data_stall", "mode_radios", "audio_output", "com.android", "com.google"
        )

        fun onGlobalBroadcast(intent: Intent) {
            activeInstance?.get()?.extractFromIntent(intent)
        }

        /**
         * Strictly validates and formats raw radio frequency values into clean user-facing strings (e.g. "98.5 FM", "1050 AM • Rock Radio").
         * Rejects any non-radio string or out-of-band number by returning null.
         */
        fun formatFrequency(rawFreq: Any?, rawBand: String? = null, rawName: String? = null): String? {
            if (rawFreq == null) return null

            val str = when (rawFreq) {
                is Double -> if (rawFreq % 1.0 == 0.0) rawFreq.toLong().toString() else rawFreq.toString()
                is Float -> if (rawFreq % 1.0f == 0.0f) rawFreq.toLong().toString() else rawFreq.toString()
                is Number -> rawFreq.toLong().toString()
                else -> rawFreq.toString().trim()
            }

            if (str.isBlank() || str == "0" || str == "-1" || str.equals("null", ignoreCase = true)) return null

            val lower = str.lowercase()
            if (BLACKLISTED_TOKENS.any { lower.contains(it) }) return null

            var formattedFreq: String? = null
            val isExplicitAm = str.contains("AM", ignoreCase = true) || rawBand?.contains("AM", ignoreCase = true) == true
            val isExplicitFm = str.contains("FM", ignoreCase = true) || rawBand?.contains("FM", ignoreCase = true) == true

            val directNum = str.toDoubleOrNull()
            if (directNum != null) {
                when {
                    // FM in Hz (50 MHz - 115 MHz): 50,000,000 to 115,000,000 Hz
                    directNum in 50_000_000.0..115_000_000.0 -> {
                        val mhz = directNum / 1_000_000.0
                        formattedFreq = String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // AM in Hz (500 kHz - 1750 kHz): 500,000 to 1,750,000 Hz
                    directNum in 500_000.0..1_750_000.0 -> {
                        val khz = (directNum / 1000.0).roundToInt()
                        formattedFreq = "$khz AM"
                    }
                    // FM in kHz (65,000 - 115,000 kHz, e.g. 98500)
                    directNum in 65_000.0..115_000.0 -> {
                        val mhz = directNum / 1000.0
                        formattedFreq = String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // FM in 10 kHz (6,500 - 11,500, e.g. 9850 -> 98.5 FM, standard Chinese car stereos Allwinner/QF/NWD)
                    directNum in 6500.0..11500.0 -> {
                        val mhz = directNum / 100.0
                        formattedFreq = String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // AM in kHz (520 - 1750 kHz) OR FM in 100 kHz (650 - 1150)
                    directNum in 520.0..1750.0 -> {
                        formattedFreq = if (isExplicitFm && directNum <= 1150.0) {
                            String.format(Locale.US, "%.1f FM", directNum / 10.0)
                        } else {
                            "${directNum.roundToInt()} AM"
                        }
                    }
                    // FM in MHz (65.0 - 115.0 MHz, e.g. 98.5)
                    directNum in 65.0..115.0 -> {
                        formattedFreq = String.format(Locale.US, "%.1f FM", directNum)
                    }
                    // LW in kHz (140 - 300 kHz)
                    directNum in 140.0..300.0 -> {
                        formattedFreq = "${directNum.roundToInt()} LW"
                    }
                    else -> {
                        // Out of all valid broadcast radio frequency ranges
                        return null
                    }
                }
            } else {
                // String with text or units, e.g. "FM 98.5", "98.50 MHz", "1050 kHz AM"
                val match = Regex("""(\d{2,8}(?:\.\d{1,2})?)""").find(str)
                if (match != null) {
                    val extractedNum = match.value.toDoubleOrNull()
                    if (extractedNum != null) {
                        when {
                            extractedNum in 50_000_000.0..115_000_000.0 -> {
                                formattedFreq = String.format(Locale.US, "%.1f FM", extractedNum / 1_000_000.0)
                            }
                            extractedNum in 500_000.0..1_750_000.0 -> {
                                formattedFreq = "${(extractedNum / 1000.0).roundToInt()} AM"
                            }
                            extractedNum in 65_000.0..115_000.0 -> {
                                formattedFreq = String.format(Locale.US, "%.1f FM", extractedNum / 1000.0)
                            }
                            extractedNum in 6500.0..11500.0 -> {
                                formattedFreq = String.format(Locale.US, "%.1f FM", extractedNum / 100.0)
                            }
                            extractedNum in 520.0..1750.0 -> {
                                formattedFreq = if (isExplicitFm && extractedNum <= 1150.0) {
                                    String.format(Locale.US, "%.1f FM", extractedNum / 10.0)
                                } else {
                                    "${extractedNum.roundToInt()} AM"
                                }
                            }
                            extractedNum in 65.0..115.0 -> {
                                formattedFreq = String.format(Locale.US, "%.1f FM", extractedNum)
                            }
                        }
                    }
                }
            }

            if (formattedFreq == null) return null

            // Clean station name (RDS PS text) if provided
            val cleanName = rawName?.trim()?.takeIf { candidate ->
                candidate.isNotBlank() &&
                    !candidate.equals("null", ignoreCase = true) &&
                    !candidate.equals("0", ignoreCase = true) &&
                    !candidate.equals("-1", ignoreCase = true) &&
                    !candidate.equals(str, ignoreCase = true) &&
                    !candidate.equals(formattedFreq, ignoreCase = true) &&
                    candidate.length in 2..40 &&
                    BLACKLISTED_TOKENS.none { candidate.lowercase().contains(it) }
            }

            return if (cleanName != null) {
                "$formattedFreq • $cleanName"
            } else {
                formattedFreq
            }
        }
    }
}
