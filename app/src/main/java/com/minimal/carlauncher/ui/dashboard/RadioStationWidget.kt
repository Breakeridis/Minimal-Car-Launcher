package com.minimal.carlauncher.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.abs

/**
 * Automotive FM Radio Cockpit Widget tailored for Nowada (NWD) Allwinner head units
 * (`com.nwd.radio` + `com.nwd.radio.service.RadioService`).
 *
 * Features:
 * - Live ON AIR / STANDBY indicator driven by `sys.nwd.audio.channel` & `nwd_radio_state`
 * - Seek Previous / Seek Next controls + large MHz frequency readout
 * - 4 customizable station presets (Tap to tune, Long-press to save current frequency)
 * - Direct launch of `com.nwd.radio.home_horizontalActivity`
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioStationWidget(
    frequencyMhz: Float,
    presets: List<Float>,
    isRadioActive: Boolean,
    stationInfo: String,
    onSeekPrev: () -> Unit,
    onSeekNext: () -> Unit,
    onSelectPreset: (Int) -> Unit,
    onSavePreset: (Int) -> Unit,
    onLaunchRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            CarSurfaceVariant.copy(alpha = 0.5f),
            CarSurface
        )
    )

    val formattedFreq = String.format(Locale.US, "%.1f", frequencyMhz)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cardGradient)
            .border(1.dp, CarBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // 1. Top Header: FM RADIO Title, Live ON AIR / STANDBY Status, and Open App Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.clickable { onLaunchRadio() }
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "FM Radio",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stationInfo.ifBlank { "FM RADIO" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Live ON AIR / STANDBY Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarSurfaceVariant)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isRadioActive) AccentGreen else AccentAmber)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isRadioActive) "ON AIR" else "STANDBY",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRadioActive) AccentGreen else AccentAmber,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Open Full Radio App Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CarSurfaceVariant)
                            .border(1.dp, CarBorder, RoundedCornerShape(6.dp))
                            .clickable { onLaunchRadio() }
                            .padding(horizontal = 7.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = "Open Radio App",
                                tint = TextSecondary,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "APP",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            // 2. Center Frequency Display & Seek Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(CarSurfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, CarBorder.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Seek Previous Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CarSurface)
                        .border(1.dp, CarBorder, RoundedCornerShape(12.dp))
                        .clickable { onSeekPrev() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Seek Previous",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Frequency Readout (Tap to open NWD Radio UI)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onLaunchRadio() }
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = formattedFreq,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "MHz",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentCyan,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                    }
                    Text(
                        text = "Tap to open Radio • Hold preset to save",
                        fontSize = 9.sp,
                        color = TextMuted
                    )
                }

                // Seek Next Button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CarSurface)
                        .border(1.dp, CarBorder, RoundedCornerShape(12.dp))
                        .clickable { onSeekNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Seek Next",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // 3. Bottom Preset Slots (4 Presets: Tap to tune, Long-press to save current frequency)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                presets.take(4).forEachIndexed { index, presetFreq ->
                    val isSelected = abs(presetFreq - frequencyMhz) < 0.05f
                    val presetText = String.format(Locale.US, "%.1f", presetFreq)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) AccentCyan else CarSurfaceVariant)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) AccentCyan else CarBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .combinedClickable(
                                onClick = { onSelectPreset(index) },
                                onLongClick = { onSavePreset(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "P${index + 1}",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black.copy(alpha = 0.7f) else TextMuted
                            )
                            Text(
                                text = presetText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                color = if (isSelected) Color.Black else TextPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
