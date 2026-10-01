package com.minimal.carlauncher.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Manifest-registered broadcast receiver to catch vendor radio broadcasts
 * even when the launcher is in the background or starting up.
 */
class RadioBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra("is_launcher_source", false)) return
        RadioManager.onGlobalBroadcast(intent)
    }
}
