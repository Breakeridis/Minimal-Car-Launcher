package com.minimal.carlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.minimal.carlauncher.service.DiagnosticsManager
import com.minimal.carlauncher.service.PackageDiagnosticInfo
import com.minimal.carlauncher.service.SystemDiagnosticInfo
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.CarBg
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary
import com.minimal.carlauncher.ui.viewmodel.LauncherViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DiagnosticsDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    viewModel: LauncherViewModel
) {
    if (!isOpen) return

    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var refreshCounter by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    var sysInfo by remember { mutableStateOf<SystemDiagnosticInfo?>(null) }
    var allPackages by remember { mutableStateOf<List<PackageDiagnosticInfo>>(emptyList()) }
    var settingsList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val radioPackages = remember(allPackages) {
        allPackages.filter { it.isRadioCandidate }
    }

    LaunchedEffect(refreshCounter) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val sys = DiagnosticsManager.getSystemInfo(context)
            val pkgs = DiagnosticsManager.getInstalledPackagesInfo(context)
            val stg = DiagnosticsManager.getAutomotiveSettings(context)
            withContext(Dispatchers.Main) {
                sysInfo = sys
                allPackages = pkgs
                settingsList = stg
                isLoading = false
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.76f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(min = 380.dp, max = 920.dp)
                    .fillMaxHeight(0.94f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CarSurface)
                    .border(1.5.dp, CarBorder, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Prevent click through */ }
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AccentCyan.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "HEAD UNIT HARDWARE, CPU & RADIO INSPECTOR",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "Model, Motherboard, SoC/CPU, MCU & Installed Radio Apps",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }

                        Button(
                            onClick = { viewModel.copyDiagnosticsReport() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Copy Report",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = { refreshCounter++ },
                            colors = ButtonDefaults.buttonColors(containerColor = CarSurfaceVariant),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Rescan",
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. Tab Selector Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(CarBg)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val tabs = listOf(
                            "1. Hardware, CPU & Radio Summary",
                            "2. Radio Apps (${radioPackages.size})",
                            "3. All System Apps (${allPackages.size})",
                            "4. Settings Keys (${settingsList.size})"
                        )
                        tabs.forEachIndexed { index, label ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) CarSurfaceVariant else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.dp else 0.dp,
                                        color = if (isSelected) AccentCyan.copy(alpha = 0.7f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedTab = index }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentCyan else TextSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3. Tab Content Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CarBg.copy(alpha = 0.85f))
                            .border(1.dp, CarBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        if (isLoading) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = AccentCyan,
                                        modifier = Modifier.size(26.dp),
                                        strokeWidth = 3.dp
                                    )
                                    Text(
                                        text = "Scanning Head Unit Hardware & Installed Radio Apps...",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                }
                            }
                        } else {
                            when (selectedTab) {
                                0 -> HardwareAndRadioOverviewTab(
                                    sys = sysInfo,
                                    radioPackages = radioPackages,
                                    onLaunchPackage = { pkg -> viewModel.launchPackageByName(pkg) }
                                )
                                1 -> PackagesListTab(
                                    packages = radioPackages,
                                    emptyMessage = "No explicit radio packages matched. Check '3. All System Apps' tab.",
                                    onLaunchPackage = { pkg -> viewModel.launchPackageByName(pkg) }
                                )
                                2 -> PackagesListTab(
                                    packages = allPackages,
                                    emptyMessage = "No packages found",
                                    onLaunchPackage = { pkg -> viewModel.launchPackageByName(pkg) }
                                )
                                3 -> SettingsKeysTab(settingsList = settingsList)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareAndRadioOverviewTab(
    sys: SystemDiagnosticInfo?,
    radioPackages: List<PackageDiagnosticInfo>,
    onLaunchPackage: (String) -> Unit
) {
    if (sys == null) return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section A: Installed Radio App(s) Highlight Box
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CarSurfaceVariant.copy(alpha = 0.75f))
                .border(1.5.dp, AccentAmber.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "INSTALLED RADIO APP(S) DETECTED (${radioPackages.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = AccentAmber
            )
            if (radioPackages.isEmpty()) {
                Text(
                    text = "No package with 'radio' or 'fm' in its name was found. Switch to Tab 3 ('All System Apps') to see all installed apps.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            } else {
                for (pkg in radioPackages) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(CarBg)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${pkg.label}  —  ${pkg.packageName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGreen
                            )
                            Text(
                                text = "APK: ${pkg.apkPath}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                            if (pkg.activities.isNotEmpty()) {
                                Text(
                                    text = "Activities: ${pkg.activities.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = AccentCyan
                                )
                            }
                            if (pkg.services.isNotEmpty()) {
                                Text(
                                    text = "Services: ${pkg.services.joinToString(", ")}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = { onLaunchPackage(pkg.packageName) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentAmber),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Open",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }
        }

        // Section B: Core Hardware, Motherboard & CPU Specs
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(CarSurfaceVariant.copy(alpha = 0.75f))
                .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "MODEL, MOTHERBOARD & CPU SPECIFICATIONS",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = AccentCyan
            )

            val primarySpecs = listOf(
                "Model / Manufacturer" to "${sys.model}  (${sys.manufacturer} / ${sys.brand})",
                "Product / Device" to "${sys.product}  /  ${sys.device}",
                "Motherboard / Board" to "${sys.board}  (Platform: ${sys.platform})",
                "Hardware / SoC Chip" to "${sys.hardware}  |  /proc/cpuinfo: ${sys.cpuHardware}",
                "CPU Processor" to "${sys.cpuModel}  (${sys.cpuCores} Cores • ABIs: ${sys.supportedAbis})",
                "Memory (RAM)" to sys.ramSummary,
                "Android Version" to "Android ${sys.androidVersion} (API ${sys.apiLevel})  •  Screen: ${sys.screenMetrics}",
                "Build Display ID" to sys.buildDisplay,
                "Kernel Version" to sys.kernelVersion,
                "Fingerprint" to sys.fingerprint
            )

            for ((label, value) in primarySpecs) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        modifier = Modifier.width(175.dp)
                    )
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Section C: MCU & Vendor System Properties
        if (sys.mcuAndVendorProps.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(CarSurfaceVariant.copy(alpha = 0.75f))
                    .border(1.dp, CarBorder.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "MCU, MOTHERBOARD & VENDOR SYSTEM PROPERTIES (${sys.mcuAndVendorProps.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    color = AccentCyan
                )
                for ((k, v) in sys.mcuAndVendorProps) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(5.dp))
                            .background(CarBg)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = k,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentAmber,
                            modifier = Modifier.weight(0.45f)
                        )
                        Text(
                            text = v,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                            modifier = Modifier.weight(0.55f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PackagesListTab(
    packages: List<PackageDiagnosticInfo>,
    emptyMessage: String,
    onLaunchPackage: (String) -> Unit
) {
    if (packages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emptyMessage,
                fontSize = 13.sp,
                color = TextMuted
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(packages, key = { it.packageName }) { pkg ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CarSurfaceVariant.copy(alpha = 0.75f))
                        .border(
                            1.dp,
                            if (pkg.isRadioCandidate) AccentAmber.copy(alpha = 0.6f) else CarBorder.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${pkg.label}  (${pkg.packageName})",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (pkg.isRadioCandidate) AccentGreen else TextPrimary
                            )
                            Text(
                                text = "APK: ${pkg.apkPath}  •  v${pkg.versionName}",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextMuted
                            )
                        }

                        Button(
                            onClick = { onLaunchPackage(pkg.packageName) },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Launch",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                        }
                    }

                    if (pkg.activities.isNotEmpty()) {
                        Text(
                            text = "Activities (${pkg.activities.size}): ${pkg.activities.joinToString(", ")}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentCyan
                        )
                    }
                    if (pkg.services.isNotEmpty()) {
                        Text(
                            text = "Services (${pkg.services.size}): ${pkg.services.joinToString(", ")}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                    if (pkg.receivers.isNotEmpty()) {
                        Text(
                            text = "Receivers (${pkg.receivers.size}): ${pkg.receivers.joinToString(", ")}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                    if (pkg.manifestActions.isNotEmpty()) {
                        Text(
                            text = "Manifest Broadcast Actions (${pkg.manifestActions.size}): ${pkg.manifestActions.joinToString(", ")}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = AccentAmber
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsKeysTab(settingsList: List<Pair<String, String>>) {
    if (settingsList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No automotive/radio keys found in Settings.System or Settings.Global",
                fontSize = 13.sp,
                color = TextMuted
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(settingsList, key = { it.first }) { (key, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarSurfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = key,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AccentCyan,
                        modifier = Modifier.weight(0.5f)
                    )
                    Text(
                        text = value,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = AccentGreen,
                        modifier = Modifier.weight(0.5f)
                    )
                }
            }
        }
    }
}
