package com.terrasync.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

data class PlaceResult(
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double
)

object RoutingEngine {

    fun fetchDrivingRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        onResult: (NavRoute?, String?) -> Unit
    ) {
        Thread {
            try {
                val urlStr = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson&steps=true"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "TerraSync/2.2.1")
                }
                if (conn.responseCode != 200) {
                    onResult(null, "Routing server error (${conn.responseCode})")
                    return@Thread
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val routes = root.optJSONArray("routes")
                if (routes == null || routes.length() == 0) {
                    onResult(null, "No driving road route found")
                    return@Thread
                }
                val r0 = routes.getJSONObject(0)
                val totalDist = r0.optDouble("distance", 0.0)
                val totalDur = r0.optDouble("duration", 0.0)

                val coordsArr = r0.getJSONObject("geometry").getJSONArray("coordinates")
                val geom = mutableListOf<Pair<Double, Double>>()
                for (i in 0 until coordsArr.length()) {
                    val pt = coordsArr.getJSONArray(i)
                    geom.add(pt.getDouble(0) to pt.getDouble(1))
                }

                val steps = mutableListOf<NavStep>()
                val legs = r0.optJSONArray("legs")
                if (legs != null && legs.length() > 0) {
                    val stepsArr = legs.getJSONObject(0).optJSONArray("steps") ?: JSONArray()
                    for (i in 0 until stepsArr.length()) {
                        val s = stepsArr.getJSONObject(i)
                        val dist = s.optDouble("distance", 0.0)
                        val roadName = s.optString("name", "").trim()
                        val man = s.optJSONObject("maneuver") ?: JSONObject()
                        val type = man.optString("type", "")
                        val mod = man.optString("modifier", "")
                        val loc = man.optJSONArray("location")
                        val lng = loc?.optDouble(0) ?: endLng
                        val lat = loc?.optDouble(1) ?: endLat

                        val (arrow, instr) = formatStep(type, mod, roadName)
                        steps.add(NavStep(instr, arrow, dist, lat, lng))
                    }
                }
                onResult(NavRoute(geom, totalDist, totalDur, steps), null)
            } catch (e: Exception) {
                onResult(null, e.message ?: "Route request failed")
            }
        }.start()
    }

    private fun formatStep(type: String, modifier: String, roadName: String): Pair<String, String> {
        val onto = if (roadName.isNotBlank()) " onto $roadName" else ""
        return when (type) {
            "arrive" -> "🏁" to "Arrive at destination"
            "depart" -> "↑" to "Head out$onto"
            "roundabout", "rotary" -> "⟳" to "Enter roundabout$onto"
            else -> when {
                modifier.contains("sharp left") -> "↙" to "Sharp left$onto"
                modifier.contains("slight left") -> "↖" to "Keep left$onto"
                modifier.contains("left") -> "↰" to "Turn left$onto"
                modifier.contains("sharp right") -> "↘" to "Sharp right$onto"
                modifier.contains("slight right") -> "↗" to "Keep right$onto"
                modifier.contains("right") -> "↱" to "Turn right$onto"
                modifier.contains("uturn") -> "↺" to "Make a U-turn$onto"
                else -> "↑" to "Continue straight$onto"
            }
        }
    }

    fun searchPlaces(query: String, onResult: (List<PlaceResult>) -> Unit) {
        Thread {
            val out = mutableListOf<PlaceResult>()
            runCatching {
                val q = Uri.encode(query)
                val urlStr = "https://nominatim.openstreetmap.org/search?q=$q&format=json&limit=8&countrycodes=iq"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "TerraSync/2.2.1 (https://github.com/Thamirakkurd/TerraSync)")
                }
                if (conn.responseCode == 200) {
                    val arr = JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val display = obj.optString("display_name", "Place")
                        val parts = display.split(",").map { it.trim() }
                        val title = parts.firstOrNull() ?: display
                        val sub = parts.drop(1).take(3).joinToString(", ")
                        val lat = obj.optString("lat").toDoubleOrNull()
                        val lng = obj.optString("lon").toDoubleOrNull()
                        if (lat != null && lng != null) {
                            out.add(PlaceResult(title, sub, lat, lng))
                        }
                    }
                }
            }
            onResult(out)
        }.start()
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Returns Pair(offRouteMeters, remainingRoadMetersAlongPolyline) */
    fun measureAlongRoute(lat: Double, lng: Double, geom: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (geom.size < 2) return 0.0 to 0.0
        val cosLat = cos(Math.toRadians(lat))
        val mPerDegLat = 111132.0
        val mPerDegLng = 111320.0 * cosLat

        var bestSegIdx = 0
        var bestDist = Double.MAX_VALUE
        var bestT = 0.0

        for (i in 0 until geom.size - 1) {
            val aLng = geom[i].first
            val aLat = geom[i].second
            val bLng = geom[i + 1].first
            val bLat = geom[i + 1].second

            val ax = (aLng - lng) * mPerDegLng
            val ay = (aLat - lat) * mPerDegLat
            val bx = (bLng - lng) * mPerDegLng
            val by = (bLat - lat) * mPerDegLat

            val dx = bx - ax
            val dy = by - ay
            val lenSq = dx * dx + dy * dy
            val t = if (lenSq > 1e-6) (-(ax * dx + ay * dy) / lenSq).coerceIn(0.0, 1.0) else 0.0
            val px = ax + t * dx
            val py = ay + t * dy
            val d = hypot(px, py)
            if (d < bestDist) {
                bestDist = d
                bestSegIdx = i
                bestT = t
            }
        }

        val segStart = geom[bestSegIdx]
        val segEnd = geom[bestSegIdx + 1]
        val projLng = segStart.first + bestT * (segEnd.first - segStart.first)
        val projLat = segStart.second + bestT * (segEnd.second - segStart.second)

        var remaining = haversineMeters(projLat, projLng, segEnd.second, segEnd.first)
        for (i in bestSegIdx + 1 until geom.size - 1) {
            remaining += haversineMeters(geom[i].second, geom[i].first, geom[i + 1].second, geom[i + 1].first)
        }
        return bestDist to remaining
    }
}
