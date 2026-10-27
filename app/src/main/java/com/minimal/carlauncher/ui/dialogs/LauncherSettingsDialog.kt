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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
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
import com.minimal.carlauncher.service.AdbStatus
import com.minimal.carlauncher.service.HardwareUsage
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
import java.util.Locale

/**
 * Custom launcher settings screen:
 *  - Live ultra-lightweight CPU & RAM hardware metrics
 *  - Shortcut to the Android system settings
 *  - Enable / disable Google Services Framework, Play Services and Play Store via Root / Local ADB
 *  - Download & install Aurora Store (open-source Google Play client)
 */
@Composable
fun LauncherSettingsDialog(
    isOpen: Boolean,
    hardwareUsage: HardwareUsage,
    adbStatus: AdbStatus?,
    isTestingAdb: Boolean,
    googleStatuses: List<SystemPackageStatus>,
    isRootAvailable: Boolean,
    busyPackage: String?,
    settingsMessage: String?,
    auroraInstalledVersion: String?,
    auroraDownloadProgress: Int?,
    auroraStatusMessage: String?,
    onToggleGooglePackage: (String, Boolean) -> Unit,
    onSetAllGoogle: (Boolean) -> Unit,
    onTestAdb: () -> Unit,
    onCopyAdbCommand: (String, Boolean) -> Unit,
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
                    .widthIn(min = 340.dp, max = 700.dp)
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
                                text = "Diagnostics, Google services & store tools",
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

                    // 1. Live Ultra-Lightweight Hardware Usage Monitor (CPU & RAM)
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "HARDWARE USAGE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Text(
                                    text = "Polled every 2s while open • 0% background overhead",
                                    fontSize = 9.sp,
                                    color = TextMuted
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // CPU Metric Tile
                                MetricBar(
                                    title = "CPU Usage",
                                    subtitle = "${hardwareUsage.cpuCores} Cores active",
                                    percent = hardwareUsage.cpuPercent,
                                    valueDisplay = "${hardwareUsage.cpuPercent}%",
                                    accentColor = if (hardwareUsage.cpuPercent > 80) AccentAmber else AccentCyan,
                                    modifier = Modifier.weight(1f)
                                )

                                // RAM Metric Tile
                                val usedGb = hardwareUsage.ramUsedMb.toDouble() / 1024.0
                                val totalGb = hardwareUsage.ramTotalMb.toDouble() / 1024.0
                                val ramDisplay = if (totalGb > 0) {
                                    String.format(Locale.US, "%.1f / %.1f GB (%d%%)", usedGb, totalGb, hardwareUsage.ramPercent)
                                } else "${hardwareUsage.ramPercent}%"

                                MetricBar(
                                    title = "RAM Usage",
                                    subtitle = "${hardwareUsage.ramFreeMb} MB Free",
                                    percent = hardwareUsage.ramPercent,
                                    valueDisplay = ramDisplay,
                                    accentColor = if (hardwareUsage.ramPercent > 85) AccentRed else AccentGreen,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Android System Settings shortcut
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
                                Text("Wi-Fi, Bluetooth, Developer Options, factory settings…", fontSize = 11.sp, color = TextSecondary)
                            }
                            Icon(Icons.Default.OpenInNew, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Google Services & Local ADB Controls
                    SectionCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "GOOGLE SERVICES & ADB",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentCyan,
                                    letterSpacing = 0.5.sp
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    StatusPill(
                                        text = if (isRootAvailable) "ROOT AVAILABLE" else "NO ROOT",
                                        color = if (isRootAvailable) AccentGreen else TextMuted
                                    )
                                    val isAdbReachable = adbStatus?.portReachable == true
                                    StatusPill(
                                        text = when {
                                            isAdbReachable -> "ADB PORT ${adbStatus?.tcpPort ?: 5555}: OPEN"
                                            adbStatus?.usbDebuggingEnabled == true -> "ADB USB: ON"
                                            else -> "ADB NET: INACTIVE"
                                        },
                                        color = if (isAdbReachable) AccentGreen else AccentAmber
                                    )
                                }
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
                                    onCopyCommand = { onCopyAdbCommand(status.packageName, !status.isEnabled) },
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
                                OutlinedButton(
                                    onClick = onTestAdb,
                                    enabled = !isTestingAdb,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    if (isTestingAdb) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = AccentCyan)
                                    } else {
                                        Icon(Icons.Default.Terminal, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Test / Connect ADB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
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
                                text = "• Toggling uses Root, Local ADB (port 5555), or System API automatically.\n" +
                                        "• If port 5555 is inactive, enable Network ADB in Developer Options or run 'adb tcpip 5555' via PC once.\n" +
                                        "• Tap the copy button to copy the ADB command for any service.",
                                fontSize = 10.sp,
                                color = TextMuted,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. Aurora Store
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
                                        text = "Open-source Google Play client — works without Play Store or Google account",
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
private fun MetricBar(
    title: String,
    subtitle: String,
    percent: Int,
    valueDisplay: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(CarSurface)
            .border(1.dp, CarBorder, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                Text(valueDisplay, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = accentColor)
            }
            LinearProgressIndicator(
                progress = (percent / 100f).coerceIn(0f, 1f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accentColor,
                trackColor = CarSurfaceVariant
            )
            Text(subtitle, fontSize = 9.sp, color = TextMuted)
        }
    }
}

@Composable
private fun GooglePackageRow(
    status: SystemPackageStatus,
    isBusy: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onCopyCommand: () -> Unit,
    onOpenAppInfo: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(CarSurface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Spacer(modifier = Modifier.width(6.dp))
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
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = TextMuted
            )
        }

        // Copy ADB command shortcut button
        IconButton(
            onClick = onCopyCommand,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = "Copy ADB command", tint = TextSecondary, modifier = Modifier.size(16.dp))
        }

        if (status.isInstalled) {
            IconButton(onClick = onOpenAppInfo, modifier = Modifier.size(30.dp)) {
                Text("ⓘ", fontSize = 17.sp, color = AccentCyan)
            }
        }

        if (isBusy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
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
        Text(text = text, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = color)
    }
}
