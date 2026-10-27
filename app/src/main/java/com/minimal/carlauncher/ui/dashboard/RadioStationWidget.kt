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
            .padding(14.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxHeight()
        ) {
            // 1. Top Header (40.dp): FM RADIO Title + Status Indicator on Left, Large OPEN button on Right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onLaunchRadio() }
                        .padding(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "FM Radio",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stationInfo.ifBlank { "FM RADIO" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCyan,
                        letterSpacing = 0.5.sp,
                        maxLines = 1
                    )
                    // Status Indicator Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CarSurfaceVariant)
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isRadioActive) AccentGreen else AccentAmber)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isRadioActive) "ON" else "IDLE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isRadioActive) AccentGreen else AccentAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Dedicated Large Open Radio App Button (40.dp height for easy tap)
                Box(
                    modifier = Modifier
                        .height(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CarSurfaceVariant)
                        .border(1.dp, CarBorder, RoundedCornerShape(12.dp))
                        .clickable { onLaunchRadio() }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open Radio App",
                            tint = AccentCyan,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "TUNER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
            }

            // 2. Main Frequency Display & Large Tactile Seek Paddles (62.dp wide each)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CarSurfaceVariant.copy(alpha = 0.55f))
                    .border(1.dp, CarBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Seek Previous Paddle Button (62.dp wide x full height)
                Box(
                    modifier = Modifier
                        .width(62.dp)
                        .fillMaxHeight()
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
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Center Frequency Readout (Tap to open NWD Radio UI)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onLaunchRadio() }
                ) {
                    Text(
                        text = formattedFreq,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary,
                        letterSpacing = (-1).sp
                    )
                    Text(
                        text = "MHz • FM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }

                // Seek Next Paddle Button (62.dp wide x full height)
                Box(
                    modifier = Modifier
                        .width(62.dp)
                        .fillMaxHeight()
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
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // 3. Bottom 2x2 Preset Matrix (4 Presets arranged in 2 rows x 2 columns with 10.dp spacing)
            // Gives each preset button ~122dp width x ~58dp height so misclicks are impossible
            val presetItems = presets.take(4)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                for (rowIndex in 0 until 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (colIndex in 0 until 2) {
                            val index = rowIndex * 2 + colIndex
                            val presetFreq = presetItems.getOrElse(index) { 87.5f }
                            val isSelected = abs(presetFreq - frequencyMhz) < 0.05f
                            val presetText = String.format(Locale.US, "%.1f", presetFreq)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) AccentCyan else CarSurfaceVariant)
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) AccentCyan else CarBorder,
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .combinedClickable(
                                        onClick = { onSelectPreset(index) },
                                        onLongClick = { onSavePreset(index) }
                                    )
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (isSelected) Color.Black.copy(alpha = 0.2f)
                                                else CarSurface
                                            )
                                            .padding(horizontal = 6.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "P${index + 1}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) Color.Black else AccentCyan
                                        )
                                    }

                                    Text(
                                        text = presetText,
                                        fontSize = 16.sp,
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
    }
}
