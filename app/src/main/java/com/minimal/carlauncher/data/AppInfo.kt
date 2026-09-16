package com.minimal.carlauncher.data

import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val icon: Drawable? = null,
    val isZLink: Boolean = false,
    val isNavigation: Boolean = false,
    val isMusic: Boolean = false,
    val isDvr: Boolean = false
)
