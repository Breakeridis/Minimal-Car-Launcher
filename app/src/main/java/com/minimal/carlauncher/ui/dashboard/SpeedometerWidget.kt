package com.minimal.carlauncher.ui.dashboard

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
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
import com.minimal.carlauncher.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun SpeedometerWidget(
    speed: Int,
    unit: SpeedUnit,
    bearing: Float,
    cardinalDirection: String,
    isGpsActive: Boolean,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Shortest angular difference tracking for smooth compass rotation without 360-degree flip
    var targetRotation by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(bearing) {
        val diff = ((bearing - (targetRotation % 360f) + 540f) % 360f) - 180f
        targetRotation += diff
    }

    val animatedCompassRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "compassRotation"
    )

    val density = LocalDensity.current
    val northTextSize = with(density) { 13.sp.toPx() }
    val cardinalTextSize = with(density) { 11.sp.toPx() }
    val subTextSize = with(density) { 9.sp.toPx() }

    val textPaintNorth = remember(northTextSize) {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.parseColor("#06B6D4") // AccentCyan
            textSize = northTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val textPaintCardinal = remember(cardinalTextSize) {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.parseColor("#CBD5E1") // TextSecondary
            textSize = cardinalTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val textPaintSub = remember(subTextSize) {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.parseColor("#64748B") // TextMuted
            textSize = subTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(CarSurface)
            .clickable { onToggleUnit() }
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        // Rotating Circular Compass Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (minOf(size.width, size.height) / 2f) - 14.dp.toPx()

            // Subtle outer and inner compass boundary rings
            drawCircle(
                color = CarSurfaceVariant.copy(alpha = 0.6f),
                radius = radius,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = CarSurfaceVariant.copy(alpha = 0.25f),
                radius = radius - 16.dp.toPx(),
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )

            // Compass Rose: Rotates counter-clockwise by current bearing so heading is at 12 o'clock
            rotate(degrees = -animatedCompassRotation, pivot = center) {
                for (angle in 0 until 360 step 15) {
                    rotate(degrees = angle.toFloat(), pivot = center) {
                        when (angle) {
                            0 -> {
                                // North (0°): Cyan bold tick + "N"
                                drawLine(
                                    color = AccentCyan,
                                    start = Offset(center.x, center.y - radius),
                                    end = Offset(center.x, center.y - radius + 15.dp.toPx()),
                                    strokeWidth = 3.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        "N",
                                        center.x,
                                        center.y - radius + 30.dp.toPx(),
                                        textPaintNorth
                                    )
                                }
                            }
                            90 -> {
                                // East (90°)
                                drawLine(
                                    color = TextSecondary,
                                    start = Offset(center.x, center.y - radius),
                                    end = Offset(center.x, center.y - radius + 13.dp.toPx()),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        "E",
                                        center.x,
                                        center.y - radius + 27.dp.toPx(),
                                        textPaintCardinal
                                    )
                                }
                            }
                            180 -> {
                                // South (180°)
                                drawLine(
                                    color = TextSecondary,
                                    start = Offset(center.x, center.y - radius),
                                    end = Offset(center.x, center.y - radius + 13.dp.toPx()),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        "S",
                                        center.x,
                                        center.y - radius + 27.dp.toPx(),
                                        textPaintCardinal
                                    )
                                }
                            }
                            270 -> {
                                // West (270°)
                                drawLine(
                                    color = TextSecondary,
                                    start = Offset(center.x, center.y - radius),
                                    end = Offset(center.x, center.y - radius + 13.dp.toPx()),
                                    strokeWidth = 2.5.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        "W",
                                        center.x,
                                        center.y - radius + 27.dp.toPx(),
                                        textPaintCardinal
                                    )
                                }
                            }
                            45, 135, 225, 315 -> {
                                // Semi-cardinals: NE, SE, SW, NW
                                val label = when (angle) {
                                    45 -> "NE"
                                    135 -> "SE"
                                    225 -> "SW"
                                    else -> "NW"
                                }
                                drawLine(
                                    color = TextMuted,
                                    start = Offset(center.x, center.y - radius),
                                    end = Offset(center.x, center.y - radius + 9.dp.toPx()),
                                    strokeWidth = 1.8.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                                drawIntoCanvas { canvas ->
                                    canvas.nativeCanvas.drawText(
                                        label,
                                        center.x,
                                        center.y - radius + 22.dp.toPx(),
                                        textPaintSub
                                    )
                                }
                            }
                            else -> {
                                // Intermediate minor ticks every 15°
                                drawLine(
                                    color = CarBorder.copy(alpha = 0.5f),
                                    start = Offset(center.x, center.y - radius),
                                    end = Offset(center.x, center.y - radius + 5.dp.toPx()),
                                    strokeWidth = 1.2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
                }
            }

            // Fixed Forward Heading Indicator Arrow (Apex at 12 o'clock)
            val pointerPath = Path().apply {
                moveTo(center.x, center.y - radius + 2.dp.toPx()) // tip pointing down into ring
                lineTo(center.x - 6.dp.toPx(), center.y - radius - 8.dp.toPx())
                lineTo(center.x + 6.dp.toPx(), center.y - radius - 8.dp.toPx())
                close()
            }
            drawPath(pointerPath, color = AccentCyan)
        }

        // Center Digital Display (Telemetry Cluster inside Compass Ring)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Heading Badge & GPS Status Row
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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CarSurfaceVariant)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$cardinalDirection • ${bearing.roundToInt()}°",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Main Speed Value
            Text(
                text = speed.toString(),
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = (-1).sp
            )

            // Clickable Unit Pill (KM/H / MPH)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CarSurfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = unit.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = AccentCyan
                )
            }
        }
    }
}
