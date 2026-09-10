package com.minimal.carlauncher.ui.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.minimal.carlauncher.service.SpeedUnit
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary

@Composable
fun SpeedometerWidget(
    speed: Int,
    unit: SpeedUnit,
    isGpsActive: Boolean,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val maxDisplaySpeed = if (unit == SpeedUnit.KMH) 220f else 140f
    val sweepFraction = (speed.toFloat() / maxDisplaySpeed).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = sweepFraction,
        animationSpec = tween(durationMillis = 350),
        label = "speedProgress"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CarSurface)
            .clickable { onToggleUnit() }
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Dial Canvas Arc
        Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            val strokeWidth = 14.dp.toPx()
            val startAngle = 145f
            val maxSweep = 250f
            val arcSize = Size(size.width, size.height)

            // Background Track
            drawArc(
                color = CarSurfaceVariant,
                startAngle = startAngle,
                sweepAngle = maxSweep,
                useCenter = false,
                topLeft = Offset(0f, 0f),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // Active Speed Progress
            if (animatedProgress > 0.01f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0.0f to AccentCyan,
                        0.5f to AccentGreen,
                        1.0f to AccentAmber
                    ),
                    startAngle = startAngle,
                    sweepAngle = maxSweep * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(0f, 0f),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Center Digital Display
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // GPS Status Pill
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isGpsActive) AccentGreen else AccentAmber)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isGpsActive) "GPS" else "ACQUIRING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Speed Value
            Text(
                text = speed.toString(),
                fontSize = 62.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-1).sp
            )

            // Unit (Clickable to switch between KM/H and MPH)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CarSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = unit.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }
        }
    }
}
