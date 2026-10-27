package com.minimal.carlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minimal.carlauncher.service.SystemPackageStatus
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.AccentRed
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary

/**
 * Custom launcher settings screen:
 *  - Shortcut to the Android system settings
 *  - Enable / disable Google Services Framework, Play Services and Play Store
 *  - Download & install Aurora Store (open-source Google Play client)
 */
@Composable
fun LauncherSettingsDialog(
    isOpen: Boolean,
    googleStatuses: List<SystemPackageStatus>,
    isRootAvailable: Boolean,
    busyPackage: String?,
    settingsMessage: String?,
    auroraInstalledVersion: String?,
    auroraDownloadProgress: Int?,
    auroraStatusMessage: String?,
    onToggleGooglePackage: (String, Boolean) -> Unit,
    onSetAllGoogle: (Boolean) -> Unit,
    onOpenAppInfo: (String) -> Unit,
    onRefresh: () -> Unit,
    onOpenAndroidSettings: () -> Unit,
    onInstallAurora: () -> Unit,
    onLaunchAurora: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    val isDownloadingAurora = auroraDownloadProgress != null
    val isBusy = busyPackage != null

    Dialog(
        onDismissRequest = { if (!isDownloadingAurora) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { if (!isDownloadingAurora) onDismiss() }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 340.dp, max = 680.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CarSurface)
                    .border(1.5.dp, CarBorder, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* consume clicks */ }
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconBadge(Icons.Default.Tune, AccentCyan, 42)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Launcher Settings",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Google services & app store tools",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CarSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Refresh, "Refresh", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { if (!isDownloadingAurora) onDismiss() },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(CarSurfaceVariant)
                        ) {
                            Icon(Icons.Default.Close, "Close", tint = TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 1. Android System Settings shortcut
                    SectionCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenAndroidSettings() },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            IconBadge(Icons.Default.Settings, TextSecondary, 36)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Android System Settings", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text("Wi-Fi, Bluetooth, display, apps…", fontSize = 11.sp, color = TextSecondary)
                            }
                            Icon(Icons.Default.OpenInNew, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Google Services
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "GOOGLE SERVICES",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    letterSpacing = 0.5.sp
                                )
                                StatusPill(
                                    text = if (isRootAvailable) "ROOT DETECTED" else "NO ROOT",
                                    color = if (isRootAvailable) AccentGreen else AccentAmber
                                )
                            }

                            if (googleStatuses.isEmpty()) {
                                Text("Reading package status…", fontSize = 12.sp, color = TextSecondary)
                            }

                            googleStatuses.forEach { status ->
                                GooglePackageRow(
                                    status = status,
                                    isBusy = busyPackage == status.packageName,
                                    enabled = !isBusy && status.isInstalled,
                                    onToggle = { onToggleGooglePackage(status.packageName, it) },
                                    onOpenAppInfo = { onOpenAppInfo(status.packageName) }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { onSetAllGoogle(true) },
                                    enabled = !isBusy,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Enable All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                                OutlinedButton(
                                    onClick = { onSetAllGoogle(false) },
                                    enabled = !isBusy,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Disable All", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                                }
                            }

                            if (!settingsMessage.isNullOrBlank()) {
                                Text(
                                    text = settingsMessage,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (settingsMessage.startsWith("❌")) AccentAmber else AccentGreen
                                )
                            }

                            Text(
                                text = "Android only lets system-signed or rooted apps change other system apps. " +
                                    "If toggling fails, tap ⓘ to open the system App Info page, or run the ADB command shown.",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Aurora Store
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                IconBadge(Icons.Default.Shop, AccentGreen, 36)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Aurora Store", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    Text(
                                        text = "Open-source Google Play client — works without Play Store / Google account",
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                }
                                StatusPill(
                                    text = auroraInstalledVersion?.let { "INSTALLED v$it" } ?: "NOT INSTALLED",
                                    color = if (auroraInstalledVersion != null) AccentGreen else TextMuted
                                )
                            }

                            if (isDownloadingAurora) {
                                val p = auroraDownloadProgress ?: 0
                                LinearProgressIndicator(
                                    progress = (p / 100f).coerceIn(0f, 1f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = AccentGreen,
                                    trackColor = CarSurface
                                )
                            }

                            if (!auroraStatusMessage.isNullOrBlank()) {
                                Text(
                                    text = auroraStatusMessage + if (isDownloadingAurora) " ${auroraDownloadProgress ?: 0}%" else "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (auroraStatusMessage.startsWith("❌")) AccentAmber else TextSecondary
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = onInstallAurora,
                                    enabled = !isDownloadingAurora,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Download, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = when {
                                            isDownloadingAurora -> "Downloading…"
                                            auroraInstalledVersion != null -> "Update / Reinstall"
                                            else -> "Download & Install"
                                        },
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black
                                    )
                                }
                                if (auroraInstalledVersion != null) {
                                    OutlinedButton(
                                        onClick = onLaunchAurora,
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.OpenInNew, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Aurora Store", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
                                    }
                                }
                            }

                            Text(
                                text = "Downloaded from F-Droid (fallback: official GitLab). If asked, allow " +
                                    "\"Install unknown apps\" for Car Launcher, then return and tap install again.",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isDownloadingAurora,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Close", color = TextSecondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GooglePackageRow(
    status: SystemPackageStatus,
    isBusy: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpenAppInfo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(
                    text = when {
                        !status.isInstalled -> "NOT PRESENT"
                        status.isEnabled -> "ENABLED"
                        else -> "DISABLED"
                    },
                    color = when {
                        !status.isInstalled -> TextMuted
                        status.isEnabled -> AccentGreen
                        else -> AccentRed
                    }
                )
            }
            Text(
                text = status.packageName,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        }

        if (status.isInstalled) {
            IconButton(onClick = onOpenAppInfo, modifier = Modifier.size(32.dp)) {
                Text("ⓘ", fontSize = 18.sp, color = AccentCyan)
            }
        }

        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = AccentCyan,
                strokeWidth = 2.dp
            )
        } else {
            Switch(
                checked = status.isEnabled,
                onCheckedChange = onToggle,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentGreen
                )
            )
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CarSurfaceVariant)
            .border(1.dp, CarBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color, sizeDp: Int) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size((sizeDp * 0.55f).dp))
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}
