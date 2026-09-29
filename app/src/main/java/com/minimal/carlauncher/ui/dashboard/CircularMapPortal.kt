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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.minimal.carlauncher.service.NavigationRoute
import com.minimal.carlauncher.ui.theme.AccentAmber
import com.minimal.carlauncher.ui.theme.AccentCyan
import com.minimal.carlauncher.ui.theme.AccentGreen
import com.minimal.carlauncher.ui.theme.AccentRed
import com.minimal.carlauncher.ui.theme.CarBg
import com.minimal.carlauncher.ui.theme.CarBorder
import com.minimal.carlauncher.ui.theme.CarSurface
import com.minimal.carlauncher.ui.theme.CarSurfaceVariant
import com.minimal.carlauncher.ui.theme.TextPrimary
import com.minimal.carlauncher.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.File
import java.util.Locale
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

/**
 * Custom osmdroid Overlay that renders a high-visibility destination pin (teardrop marker)
 * at the selected coordinate. Counter-rotates by -mapOrientation so that the pin
 * always stands upright on screen regardless of course-up map rotation.
 */
private class DestinationMarkerOverlay(private val density: Float) : Overlay() {
    var location: GeoPoint? = null

    private val shadowPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        color = android.graphics.Color.parseColor("#80000000")
    }

    private val pinBodyPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        color = android.graphics.Color.parseColor("#EF4444") // Crimson Red
    }

    private val pinBorderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.STROKE
        strokeWidth = 2f * density
        color = android.graphics.Color.WHITE
    }

    private val pinCorePaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        style = AndroidPaint.Style.FILL
        color = android.graphics.Color.WHITE
    }

    private val screenPoint = Point()
    private val pinPath = android.graphics.Path()

    override fun draw(canvas: android.graphics.Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val loc = location ?: return

        mapView.projection.toPixels(loc, screenPoint)

        canvas.save()
        canvas.translate(screenPoint.x.toFloat(), screenPoint.y.toFloat())
        canvas.rotate(-mapView.mapOrientation)

        // Ground drop-shadow beneath the pin
        canvas.drawOval(
            -7f * density, -2.5f * density,
            7f * density, 2.5f * density,
            shadowPaint
        )

        val headCenterY = -24f * density
        val headRadius = 10f * density

        pinPath.reset()
        pinPath.moveTo(0f, 0f)
        pinPath.lineTo(-headRadius * 0.9f, headCenterY + headRadius * 0.4f)
        pinPath.arcTo(
            -headRadius, headCenterY - headRadius,
            headRadius, headCenterY + headRadius,
            155f, 230f, false
        )
        pinPath.lineTo(0f, 0f)
        pinPath.close()

        canvas.drawPath(pinPath, pinBodyPaint)
        canvas.drawPath(pinPath, pinBorderPaint)
        canvas.drawCircle(0f, headCenterY, 3.8f * density, pinCorePaint)

        canvas.restore()
    }
}

/**
 * Calculates automotive adaptive camera zoom based on vehicle speed in km/h.
 * - Low speed / crawling / stopped (0 - 15 km/h): Zoom 17.5 (close-up street & intersection detail)
 * - City driving (15 - 40 km/h): Zoom 17.5 -> 16.7
 * - Arterials / ring roads (40 - 70 km/h): Zoom 16.7 -> 15.8
 * - Fast suburban / expressways (70 - 100 km/h): Zoom 15.8 -> 14.8
 * - Highway / Motorway (> 100 km/h): Zoom 14.8 -> 13.5 (broad horizon & interchange visibility)
 */
private fun calculateTargetZoomForSpeed(speedKmH: Float): Double {
    return when {
        speedKmH <= 15f -> 17.5
        speedKmH <= 40f -> 17.5 - ((speedKmH - 15f) / 25f) * 0.8
        speedKmH <= 70f -> 16.7 - ((speedKmH - 40f) / 30f) * 0.9
        speedKmH <= 100f -> 15.8 - ((speedKmH - 70f) / 30f) * 1.0
        else -> (14.8 - ((speedKmH - 100f) / 40f) * 0.8).coerceAtLeast(13.5)
    }
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun CircularMapPortal(
    location: Location?,
    bearing: Float,
    cardinalDirection: String,
    speedKmH: Float = 0f,
    isGpsActive: Boolean,
    activeRoute: NavigationRoute? = null,
    isNavigating: Boolean = false,
    isCalculatingRoute: Boolean = false,
    onStartNavigation: (GeoPoint) -> Unit = {},
    onStopNavigation: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenNavigation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenDensity = context.resources.displayMetrics.density

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var vehicleOverlayRef by remember { mutableStateOf<VehicleMarkerOverlay?>(null) }
    var destinationOverlayRef by remember { mutableStateOf<DestinationMarkerOverlay?>(null) }
    var routeCasingRef by remember { mutableStateOf<Polyline?>(null) }
    var routeCoreRef by remember { mutableStateOf<Polyline?>(null) }
    var selectedDestination by remember { mutableStateOf<GeoPoint?>(null) }
    var isUserPanning by remember { mutableStateOf(false) }
    var lastPanTimestamp by remember { mutableLongStateOf(0L) }
    var currentZoom by remember { mutableDoubleStateOf(16.5) }

    // Sync route polylines whenever activeRoute changes
    LaunchedEffect(activeRoute) {
        val pts = activeRoute?.points ?: emptyList()
        routeCasingRef?.setPoints(pts)
        routeCoreRef?.setPoints(pts)
        if (pts.isNotEmpty()) {
            val endPt = pts.last()
            selectedDestination = endPt
            destinationOverlayRef?.location = endPt
        } else if (!isNavigating) {
            selectedDestination = null
            destinationOverlayRef?.location = null
        }
        mapViewRef?.invalidate()
    }

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
    LaunchedEffect(location, bearing, speedKmH) {
        val targetLat = location?.latitude ?: currentLat
        val targetLon = location?.longitude ?: currentLon
        val targetBearing = bearing

        if (isFirstFix && location != null) {
            isFirstFix = false
            currentLat = targetLat
            currentLon = targetLon
            currentBearing = targetBearing
            dialRotation = targetBearing

            val initialZoom = calculateTargetZoomForSpeed(speedKmH)
            currentZoom = initialZoom

            val pt = GeoPoint(targetLat, targetLon)
            vehicleOverlayRef?.location = pt
            mapViewRef?.controller?.setCenter(pt)
            mapViewRef?.controller?.setZoom(initialZoom)
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

                // Automated speed-based dynamic zooming when centered on vehicle
                val targetZoom = calculateTargetZoomForSpeed(speedKmH)
                val zoomDiff = targetZoom - currentZoom
                if (kotlin.math.abs(zoomDiff) > 0.01) {
                    currentZoom += zoomDiff * 0.04
                    mapViewRef?.controller?.setZoom(currentZoom)
                }
            }
            mapViewRef?.invalidate()

            if (fraction >= 1f) break
            delay(16L) // ~60fps smooth step
        }
    }

    // Auto-recenter back to vehicle after 1 minute (60 seconds) of pan/manual idle
    LaunchedEffect(isUserPanning, lastPanTimestamp) {
        if (isUserPanning) {
            delay(60_000L) // 1 minute idle timeout
            isUserPanning = false
            val pt = GeoPoint(currentLat, currentLon)
            mapViewRef?.controller?.animateTo(pt)
            currentZoom = mapViewRef?.zoomLevelDouble ?: currentZoom
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
                .background(CarBg)
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

                        // Elegant Automotive Dark Night Color Matrix filter
                        // Inverts luminance while preserving a deep cockpit slate-navy undertone
                        val darkMatrix = ColorMatrix(
                            floatArrayOf(
                                -0.72f, 0f, 0f, 0f, 215f,
                                0f, -0.72f, 0f, 0f, 220f,
                                0f, 0f, -0.66f, 0f, 232f,
                                0f, 0f, 0f, 1f, 0f
                            )
                        )
                        overlayManager.tilesOverlay.setColorFilter(ColorMatrixColorFilter(darkMatrix))

                        // Add Route Polylines (Casing + Core)
                        val routeCasing = Polyline(this).apply {
                            outlinePaint.apply {
                                color = android.graphics.Color.parseColor("#0A0E17")
                                strokeWidth = 14f * screenDensity
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                                isAntiAlias = true
                            }
                            setPoints(activeRoute?.points ?: emptyList())
                        }
                        overlays.add(routeCasing)
                        routeCasingRef = routeCasing

                        val routeCore = Polyline(this).apply {
                            outlinePaint.apply {
                                color = android.graphics.Color.parseColor("#00D2EE")
                                strokeWidth = 8f * screenDensity
                                strokeCap = android.graphics.Paint.Cap.ROUND
                                strokeJoin = android.graphics.Paint.Join.ROUND
                                isAntiAlias = true
                            }
                            setPoints(activeRoute?.points ?: emptyList())
                        }
                        overlays.add(routeCore)
                        routeCoreRef = routeCore

                        // Add Destination Marker Map Overlay
                        val destOverlay = DestinationMarkerOverlay(screenDensity).apply {
                            this.location = activeRoute?.points?.lastOrNull() ?: selectedDestination
                        }
                        overlays.add(destOverlay)
                        destinationOverlayRef = destOverlay

                        // Add Vehicle Marker Map Overlay
                        val overlay = VehicleMarkerOverlay(screenDensity).apply {
                            this.location = initialPoint
                        }
                        overlays.add(overlay)
                        vehicleOverlayRef = overlay

                        // Add MapEventsOverlay to capture tap and long-press coordinates
                        val mapEventsReceiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                if (isNavigating) return false
                                selectedDestination = p
                                destOverlay.location = p
                                invalidate()
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean {
                                if (isNavigating) return false
                                selectedDestination = p
                                destOverlay.location = p
                                invalidate()
                                return true
                            }
                        }
                        overlays.add(0, MapEventsOverlay(mapEventsReceiver))

                        // Detect finger touch for free map panning & multi-touch pinch zoom
                        setOnTouchListener { _, event ->
                            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE || event.pointerCount > 1) {
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

            // Floating Map Controls (Search, Recenter) - Zoom in/out handled naturally via 2-finger pinch
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Search Address Autocomplete
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CarSurface.copy(alpha = 0.88f))
                        .border(1.dp, CarBorder.copy(alpha = 0.6f), CircleShape)
                        .clickable { onOpenSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Address",
                        tint = AccentCyan,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Recenter GPS
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(CarSurface.copy(alpha = 0.88f))
                        .border(
                            1.dp,
                            if (isUserPanning) AccentAmber.copy(alpha = 0.8f) else CarBorder.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .clickable {
                            isUserPanning = false
                            val pt = GeoPoint(currentLat, currentLon)
                            mapViewRef?.controller?.animateTo(pt)
                            currentZoom = mapViewRef?.zoomLevelDouble ?: currentZoom
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Recenter",
                        tint = if (isUserPanning) AccentAmber else if (isGpsActive) AccentGreen else AccentAmber,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            // Bottom Navigation Bar
            when {
                isCalculatingRoute -> {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CarSurface.copy(alpha = 0.95f))
                            .border(1.dp, AccentCyan, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                color = AccentCyan,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "CALCULATING ROUTE...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentCyan,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                isNavigating && activeRoute != null -> {
                    val distKm = activeRoute.distanceMeters / 1000.0
                    val distText = if (distKm < 1.0) {
                        "${activeRoute.distanceMeters.roundToInt()} m"
                    } else {
                        String.format(Locale.US, "%.1f km", distKm)
                    }

                    val mins = (activeRoute.durationSeconds / 60.0).roundToInt()
                    val timeText = if (mins < 60) "${mins} min" else "${mins / 60}h ${mins % 60}m"

                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Route Telemetry (Distance & ETA)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CarSurface.copy(alpha = 0.94f))
                                .border(1.dp, AccentGreen.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(AccentGreen)
                                )
                                Text(
                                    text = "$distText • $timeText",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGreen,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Stop Navigation Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentRed)
                                .clickable { onStopNavigation() }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Stop Navigation",
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "STOP",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }
                }

                selectedDestination != null -> {
                    val dest = selectedDestination!!
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Start Navigation Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(AccentGreen)
                                .clickable { onStartNavigation(dest) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = "Start Navigation",
                                    tint = Color.Black,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "START NAVIGATION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }

                        // Clear Pin Button
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(CarSurface.copy(alpha = 0.95f))
                                .border(1.dp, CarBorder, CircleShape)
                                .clickable {
                                    selectedDestination = null
                                    destinationOverlayRef?.location = null
                                    mapViewRef?.invalidate()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Pin",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                else -> {
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
            }
        }

        // 2. Rotating Outer Compass Rose Bezel (Course-Up dial framing the circular map)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = (size.width / 2f) - 6.dp.toPx()

            if (radius > 40.dp.toPx()) {
                // Outer bezel stroke
                drawCircle(
                    color = CarBorder.copy(alpha = 0.7f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // Inner bezel stroke (subtle cyan halo bordering the circular map)
                drawCircle(
                    color = AccentCyan.copy(alpha = 0.35f),
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

        // Top Floating Pill: Navigation Next Turn Maneuver OR Compass Heading
        if (isNavigating && activeRoute != null) {
            val steps = activeRoute.steps
            val nextStep = steps.firstOrNull()
            val instructionText = nextStep?.instruction ?: "Navigating to ${activeRoute.destinationName.ifBlank { "Destination" }}"

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CarSurface.copy(alpha = 0.94f))
                    .border(1.dp, AccentCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = instructionText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // Normal Heading Pill
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
}
