package com.minimal.carlauncher.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.service.SpeedUnit
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary

@Composable
fun SpeedometerWidget(
    speed: Int,
    unit: SpeedUnit,
    isGpsActive: Boolean,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxDisplaySpeed = if (unit == SpeedUnit.KMH) 220f else 140f
    val speedFraction = (speed.toFloat() / maxDisplaySpeed).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = speedFraction,
        animationSpec = tween(durationMillis = 350),
        label = "speedFraction"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CarSurface)
            .clickable { onToggleUnit() }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxHeight()
        ) {
            // Top: GPS Status Pill
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
                        .background(if (isGpsActive) AccentGreen else AccentAmber)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isGpsActive) "GPS LOCKED" else "ACQUIRING",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isGpsActive) TextSecondary else AccentAmber,
                    letterSpacing = 0.5.sp
                )
            }

            // Center: Large Digital Speed
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = speed.toString(),
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    letterSpacing = (-1).sp
                )
            }

            // Bottom: Clickable Speed Unit Pill & Visual Speed Bar
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CarSurfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = unit.label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }

                // Sleek Speed Gauge Bar
                Box(
                    modifier = Modifier
                        .width(72.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(CarSurfaceVariant)
                ) {
                    if (animatedProgress > 0.01f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = animatedProgress)
                                .fillMaxHeight()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(AccentCyan, AccentGreen)
                                    )
                                )
                        )
                    }
                }
            }
        }
    }
}
