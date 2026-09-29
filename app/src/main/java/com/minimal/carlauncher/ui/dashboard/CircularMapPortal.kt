package com.minimal.carlauncher.ui.dashboard

import android.annotation.SuppressLint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.Point
import android.graphics.Typeface
import android.location.Location
import android.os.SystemClock
import android.view.MotionEvent
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.io.File
import kotlin.math.roundToInt

/**
 * Custom osmdroid Overlay that pins the vehicle marker directly to its geographic
 * coordinate on the map. In Course-Up mode, the map rotates to match the vehicle's heading,
 * and this overlay counter-rotates by -mapOrientation so the vehicle arrow ALWAYS points
 * straight forward / UP towards 12 o'clock on screen. When the user pans with fingers,
 * the marker moves naturally along with the road.
 */
private class VehicleMarkerOverlay(private val density: Float) : Overlay() {
    var location: GeoPoint? = null

    private val badgePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        color = android.graphics.Color.parseColor("#E60F172A") // Deep Slate 900
    }

    private val borderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 2.5f * density
        color = android.graphics.Color.parseColor("#06B6D4") // AccentCyan
    }

    private val arrowPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        color = android.graphics.Color.parseColor("#06B6D4") // AccentCyan
    }

    private val arrowCorePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        color = android.graphics.Color.WHITE
    }

    private val screenPoint = Point()
    private val arrowPath = android.graphics.Path()
    private val corePath = android.graphics.Path()

    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val loc = location ?: return

        // Convert the GeoPoint to pixel coordinates on the MapView
        mapView.projection.toPixels(loc, screenPoint)

        canvas.save()
        // MapView rotates its canvas by mapOrientation around its center.
        // We translate to the vehicle's projected pixel position on the map,
        // then counter-rotate by -mapView.mapOrientation so that the vehicle marker
        // always points straight forward / UP on the screen (Course-Up mode).
        canvas.translate(screenPoint.x.toFloat(), screenPoint.y.toFloat())
        canvas.rotate(-mapView.mapOrientation)

        val radius = 20f * density
        canvas.drawCircle(0f, 0f, radius, badgePaint)
        canvas.drawCircle(0f, 0f, radius, borderPaint)

        // Automotive Chevron Arrow pointing straight UP (12 o'clock)
        arrowPath.reset()
        arrowPath.moveTo(0f, -12f * density)
        arrowPath.lineTo(9f * density, 8f * density)
        arrowPath.lineTo(0f, 4f * density)
        arrowPath.lineTo(-9f * density, 8f * density)
        arrowPath.close()
        canvas.drawPath(arrowPath, arrowPaint)

        // Inner white core for sharp visual definition
        corePath.reset()
        corePath.moveTo(0f, -8f * density)
        corePath.lineTo(5f * density, 5f * density)
        corePath.lineTo(0f, 2.5f * density)
        corePath.lineTo(-5f * density, 5f * density)
        corePath.close()
        canvas.drawPath(corePath, arrowCorePaint)

        canvas.restore()
    }
}

@SuppressLint("ClickableViewAccessibility")
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
    val density = LocalDensity.current
    val screenDensity = context.resources.displayMetrics.density

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var vehicleOverlayRef by remember { mutableStateOf<VehicleMarkerOverlay?>(null) }
    var isUserPanning by remember { mutableStateOf(false) }
    var lastPanTimestamp by remember { mutableLongStateOf(0L) }

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

    // Dynamic interpolated positions for 60fps continuous glide
    var currentLat by remember { mutableDoubleStateOf(location?.latitude ?: 37.9838) }
    var currentLon by remember { mutableDoubleStateOf(location?.longitude ?: 23.7275) }
    var currentBearing by remember { mutableFloatStateOf(bearing) }
    var dialRotation by remember { mutableFloatStateOf(bearing) }
    var isFirstFix by remember { mutableStateOf(true) }

    // Smooth 60fps continuous interpolation for car movement & course-up map rotation
    LaunchedEffect(location, bearing) {
        val targetLat = location?.latitude ?: currentLat
        val targetLon = location?.longitude ?: currentLon
        val targetBearing = bearing

        if (isFirstFix && location != null) {
            isFirstFix = false
            currentLat = targetLat
            currentLon = targetLon
            currentBearing = targetBearing
            dialRotation = targetBearing

            val pt = GeoPoint(targetLat, targetLon)
            vehicleOverlayRef?.location = pt
            mapViewRef?.controller?.setCenter(pt)
            mapViewRef?.mapOrientation = -targetBearing
            mapViewRef?.invalidate()
            return@LaunchedEffect
        }

        val startLat = currentLat
        val startLon = currentLon
        val startBearing = currentBearing
        val bearingDiff = ((targetBearing - (startBearing % 360f) + 540f) % 360f) - 180f

        val animDuration = 900L // 900ms smooth interpolation matching GPS 1Hz frequency
        val startTime = SystemClock.elapsedRealtime()

        while (true) {
            val elapsed = SystemClock.elapsedRealtime() - startTime
            val fraction = (elapsed.toFloat() / animDuration).coerceIn(0f, 1f)

            val interpolatedLat = startLat + (targetLat - startLat) * fraction
            val interpolatedLon = startLon + (targetLon - startLon) * fraction
            val interpolatedBearing = startBearing + bearingDiff * fraction

            currentLat = interpolatedLat
            currentLon = interpolatedLon
            val normBearing = ((interpolatedBearing % 360f) + 360f) % 360f
            currentBearing = normBearing
            dialRotation = normBearing

            val currentPoint = GeoPoint(interpolatedLat, interpolatedLon)
            vehicleOverlayRef?.location = currentPoint

            if (!isUserPanning) {
                mapViewRef?.controller?.setCenter(currentPoint)
                mapViewRef?.mapOrientation = -normBearing
            }
            mapViewRef?.invalidate()

            if (fraction >= 1f) break
            delay(16L) // ~60fps smooth step
        }
    }

    // Auto-recenter back to vehicle after 15 seconds of pan inactivity
    LaunchedEffect(isUserPanning, lastPanTimestamp) {
        if (isUserPanning) {
            delay(15_000L)
            isUserPanning = false
            val pt = GeoPoint(currentLat, currentLon)
            mapViewRef?.controller?.animateTo(pt)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mapViewRef?.onPause()
        }
    }

    // High-visibility, large automotive typography for compass dial
    val northTextSize = with(density) { 22.sp.toPx() }
    val cardinalTextSize = with(density) { 18.sp.toPx() }
    val subTextSize = with(density) { 13.sp.toPx() }

    val textPaintNorth = remember(northTextSize) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textAlign = AndroidPaint.Align.CENTER
            color = android.graphics.Color.parseColor("#00F0FF") // Vivid Electric Cyan
            textSize = northTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val textPaintCardinal = remember(cardinalTextSize) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textAlign = AndroidPaint.Align.CENTER
            color = android.graphics.Color.WHITE // Maximum contrast pure white
            textSize = cardinalTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    val textPaintSub = remember(subTextSize) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            textAlign = AndroidPaint.Align.CENTER
            color = android.graphics.Color.parseColor("#94A3B8") // Slate 400
            textSize = subTextSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(CircleShape)
            .background(CarSurface),
        contentAlignment = Alignment.Center
    ) {
        // 1. Live Circular OpenStreetMap (Inset with 30dp padding for clear compass bezel visibility)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(30.dp)
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
                        val initialPoint = GeoPoint(initialLat, initialLon)
                        controller.setCenter(initialPoint)

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

                        // Add Vehicle Marker Map Overlay
                        val overlay = VehicleMarkerOverlay(screenDensity).apply {
                            this.location = initialPoint
                        }
                        overlays.add(overlay)
                        vehicleOverlayRef = overlay

                        // Detect finger touch for free map panning
                        setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                                isUserPanning = true
                                lastPanTimestamp = SystemClock.elapsedRealtime()
                            }
                            false
                        }

                        mapViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

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
                        .border(
                            1.dp,
                            if (isUserPanning) AccentAmber else CarBorder,
                            CircleShape
                        )
                        .clickable {
                            isUserPanning = false
                            val pt = GeoPoint(currentLat, currentLon)
                            mapViewRef?.controller?.animateTo(pt)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Recenter",
                        tint = if (isUserPanning) AccentAmber else if (isGpsActive) AccentGreen else AccentAmber,
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
                    radius = radius - 24.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx())
                )

                // Rotating dial markers (synchronous with course-up map rotation)
                rotate(degrees = -dialRotation, pivot = center) {
                    for (angle in 0 until 360 step 15) {
                        rotate(degrees = angle.toFloat(), pivot = center) {
                            when (angle) {
                                0 -> {
                                    // North (0°) - Vibrant Cyan & Extra Bold Large Text
                                    drawLine(
                                        color = AccentCyan,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 12.dp.toPx()),
                                        strokeWidth = 3.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            "N",
                                            center.x,
                                            center.y - radius + 25.dp.toPx(),
                                            textPaintNorth
                                        )
                                    }
                                }
                                90 -> {
                                    // East (90°) - High-contrast Pure White Bold Text
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 10.dp.toPx()),
                                        strokeWidth = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            "E",
                                            center.x,
                                            center.y - radius + 23.dp.toPx(),
                                            textPaintCardinal
                                        )
                                    }
                                }
                                180 -> {
                                    // South (180°) - High-contrast Pure White Bold Text
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 10.dp.toPx()),
                                        strokeWidth = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            "S",
                                            center.x,
                                            center.y - radius + 23.dp.toPx(),
                                            textPaintCardinal
                                        )
                                    }
                                }
                                270 -> {
                                    // West (270°) - High-contrast Pure White Bold Text
                                    drawLine(
                                        color = Color.White,
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 10.dp.toPx()),
                                        strokeWidth = 2.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            "W",
                                            center.x,
                                            center.y - radius + 23.dp.toPx(),
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
                                        color = Color(0xFF94A3B8),
                                        start = Offset(center.x, center.y - radius),
                                        end = Offset(center.x, center.y - radius + 8.dp.toPx()),
                                        strokeWidth = 1.8.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                    drawIntoCanvas { canvas ->
                                        canvas.nativeCanvas.drawText(
                                            label,
                                            center.x,
                                            center.y - radius + 20.dp.toPx(),
                                            textPaintSub
                                        )
                                    }
                                }
                                else -> {
                                    drawLine(
                                        color = CarBorder.copy(alpha = 0.6f),
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

                // 12 o'clock Apex Forward Course Pointer
                val pointerPath = Path().apply {
                    moveTo(center.x, center.y - radius + 2.dp.toPx())
                    lineTo(center.x - 6.dp.toPx(), center.y - radius - 7.dp.toPx())
                    lineTo(center.x + 6.dp.toPx(), center.y - radius - 7.dp.toPx())
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
