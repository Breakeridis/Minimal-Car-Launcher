package com.minimal.carlauncher.service

import android.location.Location
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlaceSuggestion(
    val title: String,
    val subtitle: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Float? = null
)

data class RouteStep(
    val instruction: String,
    val maneuverType: String,
    val modifier: String,
    val distanceMeters: Double,
    val location: GeoPoint
)

data class NavigationRoute(
    val points: List<GeoPoint>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val destinationName: String = "",
    val steps: List<RouteStep> = emptyList()
)

object NavigationService {

    suspend fun searchPlaces(
        query: String,
        currentLat: Double,
        currentLon: Double
    ): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        // 1. Primary Geocoding Autocomplete: Photon Komoot API (OSM based, location-biased)
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = "https://photon.komoot.io/api?q=$encodedQuery&limit=6&lat=$currentLat&lon=$currentLon"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "MinimalCarLauncher/1.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val features = root.optJSONArray("features") ?: JSONArray()

                val results = mutableListOf<PlaceSuggestion>()
                for (i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val properties = feature.optJSONObject("properties") ?: continue
                    val geometry = feature.optJSONObject("geometry") ?: continue
                    val coordinates = geometry.optJSONArray("coordinates") ?: continue
                    if (coordinates.length() < 2) continue

                    val lon = coordinates.getDouble(0)
                    val lat = coordinates.getDouble(1)

                    val name = properties.optString("name").takeIf { it.isNotBlank() }
                        ?: properties.optString("street").takeIf { it.isNotBlank() }
                        ?: properties.optString("city").takeIf { it.isNotBlank() }
                        ?: trimmed

                    val street = properties.optString("street").takeIf { it.isNotBlank() && it != name }
                    val housenumber = properties.optString("housenumber").takeIf { it.isNotBlank() }
                    val streetWithNumber = listOfNotNull(street, housenumber).joinToString(" ")
                    val city = properties.optString("city").takeIf { it.isNotBlank() && it != name }
                    val state = properties.optString("state").takeIf { it.isNotBlank() }
                    val country = properties.optString("country").takeIf { it.isNotBlank() }

                    val subtitleParts = mutableListOf<String>()
                    if (streetWithNumber.isNotBlank()) subtitleParts.add(streetWithNumber)
                    if (city != null) subtitleParts.add(city)
                    if (state != null && state != city) subtitleParts.add(state)
                    if (country != null) subtitleParts.add(country)

                    val subtitle = if (subtitleParts.isNotEmpty()) {
                        subtitleParts.joinToString(", ")
                    } else {
                        properties.optString("type", "Place")
                    }

                    val distanceMeters = FloatArray(1).also {
                        Location.distanceBetween(currentLat, currentLon, lat, lon, it)
                    }[0]

                    results.add(
                        PlaceSuggestion(
                            title = name,
                            subtitle = subtitle,
                            latitude = lat,
                            longitude = lon,
                            distanceMeters = distanceMeters
                        )
                    )
                }
                if (results.isNotEmpty()) return@withContext results
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Fallback Geocoding Autocomplete: OpenStreetMap Nominatim
        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&limit=6&addressdetails=1"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "MinimalCarLauncher/1.0 (Automotive Android Launcher)")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONArray(jsonStr)
                val results = mutableListOf<PlaceSuggestion>()
                for (i in 0 until root.length()) {
                    val obj = root.getJSONObject(i)
                    val lat = obj.optDouble("lat", 0.0)
                    val lon = obj.optDouble("lon", 0.0)
                    if (lat == 0.0 && lon == 0.0) continue

                    val displayName = obj.optString("display_name", "")
                    val parts = displayName.split(",")
                    val title = parts.firstOrNull()?.trim() ?: trimmed
                    val subtitle = parts.drop(1).joinToString(",").trim().takeIf { it.isNotBlank() } ?: displayName

                    val distanceMeters = FloatArray(1).also {
                        Location.distanceBetween(currentLat, currentLon, lat, lon, it)
                    }[0]

                    results.add(
                        PlaceSuggestion(
                            title = title,
                            subtitle = subtitle,
                            latitude = lat,
                            longitude = lon,
                            distanceMeters = distanceMeters
                        )
                    )
                }
                return@withContext results
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        emptyList()
    }

    suspend fun fetchRoute(
        startLat: Double,
        startLon: Double,
        destLat: Double,
        destLon: Double,
        destName: String = ""
    ): NavigationRoute? = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://router.project-osrm.org/route/v1/driving/$startLon,$startLat;$destLon,$destLat?overview=full&geometries=geojson&steps=true"
            val url = URL(urlString)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 7000
                readTimeout = 7000
                setRequestProperty("User-Agent", "MinimalCarLauncher/1.0")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(jsonStr)
                val routes = root.optJSONArray("routes") ?: return@withContext null
                if (routes.length() == 0) return@withContext null

                val primaryRoute = routes.getJSONObject(0)
                val totalDistance = primaryRoute.optDouble("distance", 0.0)
                val totalDuration = primaryRoute.optDouble("duration", 0.0)

                val geometry = primaryRoute.optJSONObject("geometry") ?: return@withContext null
                val coordinates = geometry.optJSONArray("coordinates") ?: return@withContext null

                val points = mutableListOf<GeoPoint>()
                for (i in 0 until coordinates.length()) {
                    val coord = coordinates.getJSONArray(i)
                    val lon = coord.getDouble(0)
                    val lat = coord.getDouble(1)
                    points.add(GeoPoint(lat, lon))
                }

                val steps = mutableListOf<RouteStep>()
                val legs = primaryRoute.optJSONArray("legs")
                if (legs != null && legs.length() > 0) {
                    val leg = legs.getJSONObject(0)
                    val rawSteps = leg.optJSONArray("steps")
                    if (rawSteps != null) {
                        for (j in 0 until rawSteps.length()) {
                            val stepObj = rawSteps.getJSONObject(j)
                            val stepName = stepObj.optString("name", "")
                            val stepDist = stepObj.optDouble("distance", 0.0)
                            val maneuver = stepObj.optJSONObject("maneuver")
                            val manType = maneuver?.optString("type", "turn") ?: "turn"
                            val modifier = maneuver?.optString("modifier", "") ?: ""
                            val locArray = maneuver?.optJSONArray("location")

                            val stepLoc = if (locArray != null && locArray.length() >= 2) {
                                GeoPoint(locArray.getDouble(1), locArray.getDouble(0))
                            } else {
                                points.firstOrNull() ?: GeoPoint(startLat, startLon)
                            }

                            val instruction = formatManeuverInstruction(manType, modifier, stepName)
                            steps.add(
                                RouteStep(
                                    instruction = instruction,
                                    maneuverType = manType,
                                    modifier = modifier,
                                    distanceMeters = stepDist,
                                    location = stepLoc
                                )
                            )
                        }
                    }
                }

                return@withContext NavigationRoute(
                    points = points,
                    distanceMeters = totalDistance,
                    durationSeconds = totalDuration,
                    destinationName = destName,
                    steps = steps
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Direct fallback line if network or OSRM fails
        val fallbackPoints = listOf(
            GeoPoint(startLat, startLon),
            GeoPoint(destLat, destLon)
        )
        val dist = FloatArray(1).also {
            Location.distanceBetween(startLat, startLon, destLat, destLon, it)
        }[0].toDouble()

        NavigationRoute(
            points = fallbackPoints,
            distanceMeters = dist,
            durationSeconds = (dist / 11.0),
            destinationName = destName,
            steps = listOf(
                RouteStep(
                    instruction = "Head to destination",
                    maneuverType = "depart",
                    modifier = "straight",
                    distanceMeters = dist,
                    location = GeoPoint(destLat, destLon)
                )
            )
        )
    }

    private fun formatManeuverInstruction(type: String, modifier: String, streetName: String): String {
        val road = if (streetName.isNotBlank()) " onto $streetName" else ""
        return when (type) {
            "depart" -> "Head forward$road"
            "arrive" -> "Arrive at destination"
            "roundabout" -> "Enter roundabout$road"
            "fork" -> when {
                modifier.contains("left") -> "Keep left at fork$road"
                modifier.contains("right") -> "Keep right at fork$road"
                else -> "Continue at fork$road"
            }
            "turn" -> when (modifier) {
                "right" -> "Turn right$road"
                "left" -> "Turn left$road"
                "slight right" -> "Bear right$road"
                "slight left" -> "Bear left$road"
                "sharp right" -> "Sharp right$road"
                "sharp left" -> "Sharp left$road"
                "uturn" -> "Make a U-turn"
                else -> "Turn$road"
            }
            "new name" -> "Continue$road"
            else -> when {
                modifier.contains("right") -> "Turn right$road"
                modifier.contains("left") -> "Turn left$road"
                else -> "Continue$road"
            }
        }
    }
}
