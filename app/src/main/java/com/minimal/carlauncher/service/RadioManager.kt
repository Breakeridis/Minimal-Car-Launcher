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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.roundToInt

/**
 * RadioManager tracks the active radio station on Android automotive head units,
 * with primary optimization for Allwinner K2401 / NWD (Nowada) hardware platforms.
 */
class RadioManager(private val context: Context) {

    private val _radioStation = MutableStateFlow<String?>(null)
    val radioStation: StateFlow<String?> = _radioStation.asStateFlow()

    private val _isRadioActive = MutableStateFlow(false)
    val isRadioActive: StateFlow<Boolean> = _isRadioActive.asStateFlow()

    private var isMonitoring = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // NWD and generic automotive Settings.System keys
    private val observedSettingsKeys = listOf(
        "nwd_radio_current_freq",
        "nwd_radio_freq",
        "nwd_radio_name",
        "nwd_radio_band",
        "radio_cur_freq",
        "radio_freq",
        "cur_freq",
        "radio_current_freq"
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

            val extras = intent.extras
            var freq: Any? = null
            var name: String? = null
            var band: String? = null

            if (extras != null) {
                freq = extras.get("extra_radio_frequence")
                    ?: extras.get("freq")
                    ?: extras.get("frequency")
                    ?: extras.get("cur_freq")
                    ?: extras.get("current_freq")
                    ?: extras.get("radio:freq")
                    ?: extras.get("radio_freq")

                name = (extras.get("extra_radio_name")
                    ?: extras.get("name")
                    ?: extras.get("station")
                    ?: extras.get("ps")
                    ?: extras.get("radio:name")
                    ?: extras.get("rds"))?.toString()

                band = (extras.get("extra_radio_band")
                    ?: extras.get("band")
                    ?: extras.get("radio:band"))?.toString()
            }

            if (freq != null) {
                val formatted = formatFrequency(freq, band, name)
                if (formatted != null) {
                    _radioStation.value = formatted
                    _isRadioActive.value = true
                    return
                }
            }

            // If intent had no explicit extras, query system settings
            readCurrentSettingsFrequency()
        }
    }

    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true

        // 1. Initial read from System Settings
        readCurrentSettingsFrequency()

        // 2. Register ContentObservers for real-time changes
        registerSettingsObservers()

        // 3. Register BroadcastReceiver for vendor events
        registerBroadcastReceiver()
    }

    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false

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

            for (key in observedSettingsKeys) {
                val v = Settings.System.getString(resolver, key)
                if (!v.isNullOrBlank() && v != "0" && v != "-1") {
                    freqVal = v
                    break
                }
            }

            nameVal = Settings.System.getString(resolver, "nwd_radio_name")
                ?: Settings.System.getString(resolver, "radio_name")
            bandVal = Settings.System.getString(resolver, "nwd_radio_band")
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

    private fun registerSettingsObservers() {
        val resolver = context.contentResolver
        for (key in observedSettingsKeys) {
            try {
                val uri = Settings.System.getUriFor(key)
                if (uri != null) {
                    resolver.registerContentObserver(uri, false, settingsObserver)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun registerBroadcastReceiver() {
        val filter = IntentFilter().apply {
            addAction("com.nwd.action.ACTION_SEND_RADIO_FREQUENCE_NEW")
            addAction("ACTION_SEND_RADIO_FREQUENCE_NEW")
            addAction("com.nwd.radio.REPORT")
            addAction("com.nwd.ACTION_CHANGE_SOURCE")
            addAction("com.microntek.radiostate")
            addAction("com.microntek.radio.report")
            addAction("com.syu.radio")
            addAction("com.ts.radio.broadcast")
            addAction("com.allwinner.radio.station_changed")
            addAction("com.softwinner.radio.REPORT")
            addAction("com.navimods.radio.status")
            addAction("android.intent.action.RADIO_STATE_CHANGED")
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

    companion object {
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
                    // e.g. 6500 to 10800 (FM in 10 kHz, standard NWD K2401 format: 9850 -> 98.5 FM)
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
