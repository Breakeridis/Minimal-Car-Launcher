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
 */
class RadioManager(private val context: Context) {

    private val _radioStation = MutableStateFlow<String?>(null)
    val radioStation: StateFlow<String?> = _radioStation.asStateFlow()

    private val _isRadioActive = MutableStateFlow(false)
    val isRadioActive: StateFlow<Boolean> = _isRadioActive.asStateFlow()

    private var isMonitoring = false
    private var pollingJob: Job? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    // Comprehensive QF, NWD, and generic automotive Settings.System keys
    private val observedSettingsKeys = listOf(
        "qf_radio_freq",
        "qf_radio_cur_freq",
        "qf_radio_current_freq",
        "qf_radio_station",
        "qf_radio_name",
        "qf_radio_band",
        "qf_current_freq",
        "qf_freq",
        "nwd_radio_current_freq",
        "nwd_radio_freq",
        "nwd_radio_name",
        "nwd_radio_band",
        "radio_cur_freq",
        "radio_freq",
        "cur_freq",
        "radio_current_freq",
        "radio_station",
        "radio_station_name",
        "radio_name",
        "radio_band",
        "radio_channel",
        "radio_play_freq",
        "radio_frequency",
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
        "com.qf.radio.freq",
        "com.qf.radio.frequency",
        "com.qf.radio.station",
        "com.qf.radio.cur_freq",
        "sys.radio.freq",
        "persist.sys.radio.freq",
        "mcu_radio_freq",
        "mcu_freq",
        "radio_last_freq",
        "last_radio_freq",
        "radio_ps",
        "radio_rds",
        "radio_rt"
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
            val resolver = context.contentResolver
            var freqVal: String? = null
            var nameVal: String? = null
            var bandVal: String? = null

            // 1. Check known Settings.System keys
            for (key in observedSettingsKeys) {
                val v = Settings.System.getString(resolver, key)
                if (!v.isNullOrBlank() && v != "0" && v != "-1") {
                    freqVal = v
                    break
                }
            }

            // 2. Fallback: Check Settings.Global if not found in Settings.System
            if (freqVal.isNullOrBlank()) {
                for (key in observedSettingsKeys) {
                    try {
                        val v = Settings.Global.getString(resolver, key)
                        if (!v.isNullOrBlank() && v != "0" && v != "-1") {
                            freqVal = v
                            break
                        }
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            // 3. Fallback: Query system settings table cursor directly
            if (freqVal.isNullOrBlank()) {
                freqVal = scanSystemSettingsCursor()
            }

            // 4. Fallback: Check SystemProperties via reflection
            if (freqVal.isNullOrBlank()) {
                val propKeys = listOf(
                    "persist.sys.radio.freq",
                    "persist.radio.freq",
                    "sys.radio.freq",
                    "ro.radio.freq",
                    "qf.radio.freq",
                    "persist.qf.radio.freq",
                    "nwd.radio.freq",
                    "persist.nwd.radio.freq",
                    "persist.sys.radio.cur_freq"
                )
                for (prop in propKeys) {
                    val pVal = readSystemProperty(prop)
                    if (!pVal.isNullOrBlank() && pVal != "0" && pVal != "-1") {
                        freqVal = pVal
                        break
                    }
                }
            }

            nameVal = Settings.System.getString(resolver, "qf_radio_name")
                ?: Settings.System.getString(resolver, "qf_radio_station")
                ?: Settings.System.getString(resolver, "nwd_radio_name")
                ?: Settings.System.getString(resolver, "radio_name")
                ?: Settings.System.getString(resolver, "radio_station")
                ?: Settings.System.getString(resolver, "radio_ps")

            bandVal = Settings.System.getString(resolver, "qf_radio_band")
                ?: Settings.System.getString(resolver, "nwd_radio_band")
                ?: Settings.System.getString(resolver, "radio_band")

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

    private fun scanSystemSettingsCursor(): String? {
        return try {
            val cursor = context.contentResolver.query(
                Settings.System.CONTENT_URI,
                arrayOf("name", "value"),
                null, null, null
            )
            var foundFreq: String? = null
            cursor?.use { c ->
                val nameCol = c.getColumnIndex("name")
                val valCol = c.getColumnIndex("value")
                if (nameCol != -1 && valCol != -1) {
                    while (c.moveToNext()) {
                        val name = c.getString(nameCol) ?: continue
                        val value = c.getString(valCol) ?: continue
                        val lowerName = name.lowercase()
                        if (lowerName.contains("radio") ||
                            lowerName.contains("freq") ||
                            lowerName.contains("station") ||
                            lowerName.contains("qf_") ||
                            lowerName.contains("nwd_")
                        ) {
                            if (value.isNotBlank() && value != "0" && value != "-1") {
                                val formatted = formatFrequency(value, null, null)
                                if (formatted != null) {
                                    foundFreq = value
                                    break
                                }
                            }
                        }
                    }
                }
            }
            foundFreq
        } catch (e: Throwable) {
            null
        }
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

    fun extractFromIntent(intent: Intent) {
        val extras = intent.extras ?: return

        var detectedFreq: Any? = null
        var detectedName: String? = null
        var detectedBand: String? = null

        // 1. Check known frequency extra keys
        val freqKeys = listOf(
            "extra_radio_frequence", "freq", "frequency", "cur_freq", "current_freq",
            "radio:freq", "radio_freq", "qf_freq", "qf_radio_freq", "curRadioFreq",
            "channel", "frequence", "radio_frequence", "nwd_freq", "mFreq"
        )
        for (k in freqKeys) {
            if (extras.containsKey(k)) {
                val v = extras.get(k)
                if (v != null && v.toString().isNotBlank() && v.toString() != "0" && v.toString() != "-1") {
                    detectedFreq = v
                    break
                }
            }
        }

        // 2. Check known station name extra keys
        val nameKeys = listOf(
            "extra_radio_name", "name", "station", "ps", "radio:name", "rds",
            "station_name", "title", "track", "label", "qf_station", "qf_name"
        )
        for (k in nameKeys) {
            if (extras.containsKey(k)) {
                val v = extras.get(k)?.toString()?.trim()
                if (!v.isNullOrBlank() && !v.equals("null", ignoreCase = true)) {
                    detectedName = v
                    break
                }
            }
        }

        // 3. Check known band extra keys
        val bandKeys = listOf("extra_radio_band", "band", "radio:band", "type")
        for (k in bandKeys) {
            if (extras.containsKey(k)) {
                val v = extras.get(k)?.toString()?.trim()
                if (!v.isNullOrBlank()) {
                    detectedBand = v
                    break
                }
            }
        }

        // 4. Fallback: Scan all keys in the Bundle for any numeric radio frequency value
        if (detectedFreq == null) {
            for (key in extras.keySet()) {
                val v = extras.get(key) ?: continue
                val str = v.toString().trim()
                val num = str.toDoubleOrNull()
                if (num != null) {
                    if (num in 65.0..108.0 ||
                        num in 650.0..1080.0 ||
                        num in 6500.0..10800.0 ||
                        num in 65000.0..108000.0 ||
                        num in 50_000_000.0..110_000_000.0 ||
                        num in 530.0..1710.0
                    ) {
                        detectedFreq = num
                        break
                    }
                } else if (str.contains("FM", ignoreCase = true) || str.contains("AM", ignoreCase = true)) {
                    detectedFreq = str
                    break
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

        fun onGlobalBroadcast(intent: Intent) {
            activeInstance?.get()?.extractFromIntent(intent)
        }

        fun formatFrequency(rawFreq: Any?, rawBand: String? = null, rawName: String? = null): String? {
            if (rawFreq == null) return null

            val str = rawFreq.toString().trim()
            if (str.isBlank() || str == "0" || str == "-1") return null

            // If already formatted with FM/AM
            if (str.contains("FM", ignoreCase = true) || str.contains("AM", ignoreCase = true)) {
                val cleanName = rawName?.takeIf { it.isNotBlank() && !it.equals(str, ignoreCase = true) }
                return if (cleanName != null) "$str • $cleanName" else str
            }

            val num = str.toDoubleOrNull()
            val formattedFreq = if (num != null) {
                when {
                    // e.g. 98500000 Hz (FM in Hz)
                    num >= 50_000_000 -> {
                        val mhz = num / 1_000_000.0
                        String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // e.g. 500000 to 1710000 Hz (AM in Hz)
                    num >= 500_000 -> {
                        val khz = (num / 1000.0).roundToInt()
                        "$khz AM"
                    }
                    // e.g. 65000 to 108000 kHz (FM in kHz)
                    num in 65_000.0..108_000.0 -> {
                        val mhz = num / 1000.0
                        String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // e.g. 6500 to 10800 (FM in 10 kHz, standard QF / NWD K2401 format: 9850 -> 98.5 FM)
                    num in 6500.0..10800.0 -> {
                        val mhz = num / 100.0
                        String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // e.g. 650 to 1080 (FM in 100 kHz: 985 -> 98.5 FM)
                    num in 650.0..1080.0 -> {
                        val mhz = num / 10.0
                        String.format(Locale.US, "%.1f FM", mhz)
                    }
                    // e.g. 530 to 1710 (AM in kHz)
                    num in 530.0..1710.0 -> {
                        val khz = num.roundToInt()
                        "$khz AM"
                    }
                    // e.g. 87.5 to 108.0 (FM directly in MHz)
                    num in 65.0..108.0 -> {
                        String.format(Locale.US, "%.1f FM", num)
                    }
                    else -> {
                        val band = rawBand?.uppercase() ?: "FM"
                        "$str $band"
                    }
                }
            } else {
                str
            }

            val cleanName = rawName?.trim()?.takeIf {
                it.isNotBlank() &&
                        !it.equals(str, ignoreCase = true) &&
                        !it.equals(formattedFreq, ignoreCase = true) &&
                        !it.equals("null", ignoreCase = true)
            }

            return if (cleanName != null) {
                "$formattedFreq • $cleanName"
            } else {
                formattedFreq
            }
        }
    }
}
