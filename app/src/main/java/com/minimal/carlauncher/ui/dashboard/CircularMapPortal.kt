package com.minimal.carlauncher.ui.dashboard

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextMuted
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import java.io.File
import kotlin.math.roundToInt

@Composable
fun CircularMapPortal(
    location: Location?,
    bearing: Float,
    cardinalDirection: String,
    isGpsActive: Boolean,
    onOpenNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var isUserPanning by remember { mutableStateOf(false) }

    // Initialize osmdroid configuration safely
    remember {
        try {
            val basePath = context.getExternalFilesDir("osmdroid") ?: context.cacheDir
            Configuration.getInstance().osmdroidBasePath = basePath
            Configuration.getInstance().osmdroidTileCache = File(basePath, "tiles")
            Configuration.getInstance().userAgentValue = context.packageName
        } catch (e: Exception) {
            e.printStackTrace()
        }
        true
    }

    // Shortest angular difference tracking for smooth compass rotation
    var targetRotation by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(bearing) {
        val diff = ((bearing - (targetRotation % 360f) + 540f) % 360f) - 180f
        targetRotation += diff
    }

    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "compassRotation"
    )

    // Automatically follow vehicle GPS position
    LaunchedEffect(location, isUserPanning) {
        if (!isUserPanning && location != null && mapViewRef != null) {
            val geoPoint = GeoPoint(location.latitude, location.longitude)
            mapViewRef?.controller?.animateTo(geoPoint)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onPause()
        }
    }

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
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(CarSurface),
        contentAlignment = Alignment.Center
    ) {
        // 1. Live Circular OpenStreetMap (Clipped with inset to leave room for the compass bezel)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(26.dp)
                .clip(CircleShape)
                .background(Color(0xFF0F172A))
        ) {
            AndroidView(
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        isTilesScaledToDpi = true
                        controller.setZoom(16.5)

                        val initialLat = location?.latitude ?: 37.9838
                        val initialLon = location?.longitude ?: 23.7275
                        controller.setCenter(GeoPoint(initialLat, initialLon))

                        // Automotive Dark Mode Color Matrix filter
                        val darkMatrix = ColorMatrix(
                            floatArrayOf(
                                -0.85f, 0f, 0f, 0f, 240f,
                                0f, -0.85f, 0f, 0f, 240f,
                                0f, 0f, -0.85f, 0f, 240f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                        overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(darkMatrix))

                        mapViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Centered Vehicle Location Marker Arrow
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(CarSurface.copy(alpha = 0.85f))
                    .border(1.5.dp, AccentCyan, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Vehicle Heading",
                    tint = AccentCyan,
                    modifier = Modifier
                        .size(24.dp)
                        .rotate(bearing)
                )
            }

            // Floating Map Controls (Zoom In, Zoom Out, Recenter)
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Zoom In
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CarSurface.copy(alpha = 0.9f))
                        .border(1.dp, CarBorder, CircleShape)
                        .clickable { mapViewRef?.controller?.zoomIn() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Zoom In",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Zoom Out
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CarSurface.copy(alpha = 0.9f))
                        .border(1.dp, CarBorder, CircleShape)
                        .clickable { mapViewRef?.controller?.zoomOut() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Zoom Out",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Recenter GPS
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CarSurface.copy(alpha = 0.9f))
                        .border(1.dp, CarBorder, CircleShape)
                        .clickable {
                            isUserPanning = false
                            location?.let {
                                mapViewRef?.controller?.animateTo(GeoPoint(it.latitude, it.longitude))
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Recenter",
                        tint = if (isGpsActive) AccentGreen else AccentAmber,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Bottom Tap To Navigate Pill
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CarSurface.copy(alpha = 0.92f))
                    .border(1.dp, CarBorder, RoundedCornerShape(12.dp))
                    .clickable { onOpenNavigation() }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isGpsActive) AccentGreen else AccentAmber)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "TAP FOR FULL MAP",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // 2. Rotating Outer Compass Rose Bezel (Course-Up dial framing the circular map)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.width / 2f) - 6.dp.toPx()

            if (radius > 40.dp.toPx()) {
                // Outer bezel stroke
                drawCircle(
                    color = CarSurfaceVariant,
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Inner bezel stroke (bordering the map)
                drawCircle(
                    color = CarBorder,
                    radius = radius - 20.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Rotating dial markers
                rotate(degrees = -animatedRotation, pivot = center) {
                    for (angle in 0 until 360 step 15) {
                        rotate(degrees = angle.toFloat(), pivot = center) {
                            when (angle) {
                                0 -> {
                                    // North (0°)
                                    drawLine(
                                        color = AccentCyan,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 11.dp.toPx()),
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            "N",
                                            center.x,
                                            center.y - radius + 23.dp.toPx(),
                                            textPaintNorth
                                        )
                                    }
                                }
                                90 -> {
                                    // East (90°)
                                    drawLine(
                                        color = TextSecondary,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 9.dp.toPx()),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            "E",
                                            center.x,
                                            center.y - radius + 20.dp.toPx(),
                                            textPaintCardinal
                                        )
                                    }
                                }
                                180 -> {
                                    // South (180°)
                                    drawLine(
                                        color = TextSecondary,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 9.dp.toPx()),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            "S",
                                            center.x,
                                            center.y - radius + 20.dp.toPx(),
                                            textPaintCardinal
                                        )
                                    }
                                }
                                270 -> {
                                    // West (270°)
                                    drawLine(
                                        color = TextSecondary,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 9.dp.toPx()),
                                        strokeWidth = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            "W",
                                            center.x,
                                            center.y - radius + 20.dp.toPx(),
                                            textPaintCardinal
                                        )
                                    }
                                }
                                45, 135, 225, 315 -> {
                                    val label = when (angle) {
                                        45 -> "NE"
                                        135 -> "SE"
                                        225 -> "SW"
                                        else -> "NW"
                                    }
                                    drawLine(
                                        color = TextMuted,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 7.dp.toPx()),
                                        strokeWidth = 1.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            label,
                                            center.x,
                                            center.y - radius + 17.dp.toPx(),
                                            textPaintSub
                                        )
                                    }
                                }
                                else -> {
                                    drawLine(
                                        color = CarBorder.copy(alpha = 0.5f),
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 4.dp.toPx()),
                                        strokeWidth = 1.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }
                }

                // 12 o'clock Apex Forward Course Pointer
                val pointerPath = Path().apply {
                    moveTo(center.x, center.y - radius + 2.dp.toPx())
                    lineTo(center.x - 5.dp.toPx(), center.y - radius - 5.dp.toPx())
                    lineTo(center.x + 5.dp.toPx(), center.y - radius - 5.dp.toPx())
                    close()
                }
                drawPath(pointerPath, color = AccentCyan)
            }
        }

        // Top Floating Heading Pill
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(CarSurface.copy(alpha = 0.95f))
                .border(1.dp, CarBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp)
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
}
