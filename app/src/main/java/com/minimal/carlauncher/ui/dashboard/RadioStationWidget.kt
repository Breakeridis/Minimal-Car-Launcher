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
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary

/**
 * Dedicated hero Radio Station Controller card placed on the driver's instrument cluster.
 * Displays real-time radio frequency and RDS station name, quick-access collected presets,
 * and tactile Prev/Next station seek buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RadioStationWidget(
    radioStation: String?,
    isRadioActive: Boolean,
    savedStations: List<String> = emptyList(),
    onTunePrevious: () -> Unit,
    onTuneNext: () -> Unit,
    onSelectSavedStation: (String, Int) -> Unit = { _, _ -> },
    onSaveCurrentStation: (Int) -> Unit = {},
    onLaunchRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cardGradient = Brush.verticalGradient(
        colors = listOf(
            CarSurfaceVariant.copy(alpha = 0.5f),
            CarSurface
        )
    )

    val hasStation = !radioStation.isNullOrBlank()

    // Parse frequency digits, band, and RDS station name
    val (freqDisplay, bandDisplay, stationName) = if (hasStation) {
        val full = radioStation!!
        val parts = full.split("•").map { it.trim() }
        val freqPart = parts.firstOrNull() ?: full
        val namePart = if (parts.size > 1) parts[1] else null

        val freqTokens = freqPart.split(" ")
        val num = freqTokens.firstOrNull() ?: freqPart
        val band = if (freqTokens.size > 1) freqTokens[1] else "FM"
        Triple(num, band, namePart)
    } else {
        Triple("FM TUNER", "", "Tap to launch Radio")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cardGradient)
            .border(1.dp, CarBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Top Header: Radio Icon, Title, and On-Air Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = "Radio Tuner",
                        tint = AccentAmber,
                        modifier = Modifier.size(17.dp)
                    )
                    Text(
                        text = "RADIO TUNER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber,
                        letterSpacing = 0.5.sp
                    )
                }

                // Status Pill
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
                            .background(if (hasStation || isRadioActive) AccentGreen else AccentAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasStation || isRadioActive) "ON AIR" else "STANDBY",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hasStation || isRadioActive) AccentGreen else AccentAmber,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Center Hero Display (Clickable to launch native headunit radio app)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onLaunchRadio() }
                    .padding(vertical = 2.dp)
            ) {
                if (hasStation) {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = freqDisplay,
                            fontSize = 50.sp,
                            fontWeight = FontWeight.Black,
                            color = TextPrimary,
                            letterSpacing = (-0.5).sp,
                            lineHeight = 50.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = bandDisplay,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentAmber,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    if (!stationName.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stationName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = "FM RADIO",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tap to open tuner",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                }
            }

            // Quick-Access Saved / Collected Stations Row (Top 4)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val displayStations = if (savedStations.size >= 4) {
                    savedStations.take(4)
                } else {
                    val list = savedStations.toMutableList()
                    while (list.size < 4) list.add("P${list.size + 1}")
                    list
                }

                displayStations.forEachIndexed { index, station ->
                    val cleanStation = station.replace(" FM", "").replace(" AM", "").trim()
                    val isCurrent = hasStation && freqDisplay.contains(cleanStation)
                    val chipBorder = if (isCurrent) AccentAmber else CarBorder.copy(alpha = 0.55f)
                    val chipBg = if (isCurrent) AccentAmber.copy(alpha = 0.22f) else CarSurfaceVariant.copy(alpha = 0.85f)
                    val chipTextColor = if (isCurrent) AccentAmber else TextPrimary

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(chipBg)
                            .border(1.2.dp, chipBorder, RoundedCornerShape(10.dp))
                            .combinedClickable(
                                onClick = { onSelectSavedStation(station, index) },
                                onLongClick = { onSaveCurrentStation(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = station,
                            fontSize = 15.sp,
                            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                            color = chipTextColor,
                            maxLines = 1
                        )
                    }
                }
            }

            // Bottom Controls: Minus (-) and Plus (+) Station Seek Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Minus / Previous Station Button
                Box(
                    modifier = Modifier
                        .size(width = 82.dp, height = 55.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CarSurfaceVariant)
                        .border(1.dp, CarBorder, RoundedCornerShape(16.dp))
                        .clickable { onTunePrevious() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "−",
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber,
                        lineHeight = 38.sp
                    )
                }

                Text(
                    text = "SEEK STATION",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary.copy(alpha = 0.8f),
                    letterSpacing = 0.5.sp
                )

                // Plus / Next Station Button
                Box(
                    modifier = Modifier
                        .size(width = 82.dp, height = 55.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(CarSurfaceVariant)
                        .border(1.dp, CarBorder, RoundedCornerShape(16.dp))
                        .clickable { onTuneNext() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentAmber,
                        lineHeight = 34.sp
                    )
                }
            }
        }
    }
}
