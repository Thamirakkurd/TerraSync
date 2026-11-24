package com.terrasync.app

import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object RoutingEngine {

    @Volatile var userAgent: String = "TerraSync/2.6.1 (https://github.com/Thamirakkurd/TerraSync)"

    fun fetchRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        mode: TravelMode = TravelMode.CAR,
        startBearing: Float? = null,
        onResult: (NavRoute?, String?) -> Unit
    ) {
        Thread {
            val profile = when (mode) {
                TravelMode.CAR -> "driving"
                TravelMode.BICYCLE -> "bike"
                TravelMode.WALKING -> "foot"
            }
            val extra = if (mode == TravelMode.WALKING) {
                "&alternatives=true&continue_straight=false"
            } else ""
            val bearingParam = if (startBearing != null && mode != TravelMode.WALKING) {
                val deg = ((startBearing.roundToInt() % 360) + 360) % 360
                "&bearings=$deg,60;&continue_straight=true"
            } else ""

            val basePrimary = "https://routing.openstreetmap.de/${mode.osrmPrefix}/route/v1/$profile/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson&steps=true$extra"
            val urls = buildList {
                if (bearingParam.isNotEmpty()) {
                    add("$basePrimary$bearingParam")
                }
                add(basePrimary)
                if (mode == TravelMode.CAR) {
                    val fallbackCar = "https://router.project-osrm.org/route/v1/driving/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson&steps=true"
                    if (bearingParam.isNotEmpty()) add("$fallbackCar$bearingParam")
                    add(fallbackCar)
                } else if (mode == TravelMode.WALKING) {
                    add("https://routing.openstreetmap.de/routed-foot/route/v1/foot/$startLng,$startLat;$endLng,$endLat?overview=full&geometries=geojson&steps=true&continue_straight=false")
                }
            }

            var lastError = "Route request failed"
            for (urlStr in urls) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10000
                        readTimeout = 12000
                        setRequestProperty("User-Agent", userAgent)
                    }
                    if (conn.responseCode != 200) {
                        lastError = "Routing server error (${conn.responseCode})"
                        continue
                    }
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val routes = root.optJSONArray("routes")
                    if (routes == null || routes.length() == 0) {
                        lastError = "No ${mode.label.lowercase()} route found"
                        continue
                    }
                    var r0 = routes.getJSONObject(0)
                    if (mode == TravelMode.WALKING && routes.length() > 1) {
                        var bestDist = r0.optDouble("distance", Double.MAX_VALUE)
                        for (idx in 1 until routes.length()) {
                            val cand = routes.getJSONObject(idx)
                            val d = cand.optDouble("distance", Double.MAX_VALUE)
                            if (d < bestDist) {
                                bestDist = d
                                r0 = cand
                            }
                        }
                    }
                    val totalDist = r0.optDouble("distance", 0.0)
                    val totalDur = r0.optDouble("duration", 0.0)

                    val coordsArr = r0.getJSONObject("geometry").getJSONArray("coordinates")
                    val geom = mutableListOf<Pair<Double, Double>>()
                    for (i in 0 until coordsArr.length()) {
                        val pt = coordsArr.getJSONArray(i)
                        geom.add(pt.getDouble(0) to pt.getDouble(1))
                    }
                    var geomTotal = 0.0
                    for (i in 0 until geom.size - 1) {
                        geomTotal += haversineMeters(geom[i].second, geom[i].first, geom[i + 1].second, geom[i + 1].first)
                    }

                    val steps = mutableListOf<NavStep>()
                    val legs = r0.optJSONArray("legs")
                    if (legs != null && legs.length() > 0) {
                        val stepsArr = legs.getJSONObject(0).optJSONArray("steps") ?: JSONArray()
                        var cumDist = 0.0
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

                            val stepRouteDist = when {
                                i == 0 -> 0.0
                                i == stepsArr.length() - 1 -> geomTotal
                                totalDist > 1.0 && geomTotal > 1.0 -> (cumDist / totalDist) * geomTotal
                                else -> cumDist
                            }
                            val (arrow, instr) = formatStep(type, mod, roadName)
                            steps.add(NavStep(instr, arrow, dist, lat, lng, stepRouteDist))
                            cumDist += dist
                        }
                    }
                    onResult(NavRoute(geom, totalDist, totalDur, steps, mode), null)
                    return@Thread
                } catch (e: Exception) {
                    lastError = e.message ?: "Route request failed"
                } finally {
                    conn?.disconnect()
                }
            }
            onResult(null, lastError)
        }.start()
    }

    fun fetchDrivingRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        onResult: (NavRoute?, String?) -> Unit
    ) = fetchRoute(startLat, startLng, endLat, endLng, TravelMode.CAR, null, onResult)

    private fun formatStep(type: String, modifier: String, roadName: String): Pair<String, String> {
        val onto = if (roadName.isNotBlank()) " onto $roadName" else ""
        return when (type) {
            "arrive" -> "🏁" to "Arrive at destination"
            "depart" -> "↑" to "Head out$onto"
            "roundabout", "rotary" -> "⟳" to "Enter roundabout$onto"
            "merge" -> "↑" to "Merge$onto"
            "off ramp" -> "↗" to "Take exit$onto"
            "on ramp" -> "↗" to "Take ramp$onto"
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
                    setRequestProperty("User-Agent", userAgent)
                }
                try {
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
                } finally {
                    conn.disconnect()
                }
            }
            onResult(out)
        }.start()
    }

    fun reverseGeocode(lat: Double, lng: Double, onResult: (PlaceResult?) -> Unit) {
        Thread {
            val result = runCatching {
                val urlStr = "https://nominatim.openstreetmap.org/reverse?lat=$lat&lon=$lng&format=json&zoom=18&addressdetails=1"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", userAgent)
                }
                try {
                    if (conn.responseCode == 200) {
                        val obj = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        val name = obj.optString("name", "").trim()
                        val display = obj.optString("display_name", "").trim()
                        val parts = display.split(",").map { it.trim() }.filter { it.isNotBlank() }
                        val title = name.ifBlank { parts.firstOrNull() ?: "Selected Location" }
                        val sub = parts.filterNot { it.equals(title, ignoreCase = true) }.take(3).joinToString(", ")
                        PlaceResult(title, sub, lat, lng)
                    } else null
                } finally {
                    conn.disconnect()
                }
            }.getOrNull()
            onResult(result)
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

    fun trackRouteProgress(
        lat: Double,
        lng: Double,
        geom: List<Pair<Double, Double>>,
        lastSegIdx: Int = 0
    ): RouteProgress {
        if (geom.size < 2) {
            return RouteProgress(0.0, 0.0, 0.0, lat, lng, 0, geom)
        }
        val cosLat = cos(Math.toRadians(lat))
        val mPerDegLat = 111132.0
        val mPerDegLng = 111320.0 * cosLat

        val segCount = geom.size - 1
        val segLens = DoubleArray(segCount)
        val cumLens = DoubleArray(geom.size)
        for (i in 0 until segCount) {
            val len = haversineMeters(geom[i].second, geom[i].first, geom[i + 1].second, geom[i + 1].first)
            segLens[i] = len
            cumLens[i + 1] = cumLens[i] + len
        }

        fun scanRange(startIdx: Int, endIdx: Int): Triple<Int, Double, Double> {
            var bestIdx = startIdx
            var bestDist = Double.MAX_VALUE
            var bestT = 0.0
            for (i in startIdx..endIdx) {
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
                    bestIdx = i
                    bestT = t
                }
            }
            return Triple(bestIdx, bestT, bestDist)
        }

        val safeLast = lastSegIdx.coerceIn(0, segCount - 1)
        val winStart = (safeLast - 3).coerceAtLeast(0)
        var winEnd = safeLast
        while (winEnd < segCount - 1) {
            val aheadDist = cumLens[winEnd + 1] - cumLens[safeLast]
            if (aheadDist >= 2000.0 && winEnd - safeLast >= 35) break
            winEnd++
        }

        var (bestSegIdx, bestT, bestDist) = scanRange(winStart, winEnd)
        if (bestDist > 45.0) {
            val (fullIdx, fullT, fullDist) = scanRange(0, segCount - 1)
            if (fullDist < bestDist - 12.0) {
                bestSegIdx = fullIdx
                bestT = fullT
                bestDist = fullDist
            }
        }

        val segStart = geom[bestSegIdx]
        val segEnd = geom[bestSegIdx + 1]
        val projLng = segStart.first + bestT * (segEnd.first - segStart.first)
        val projLat = segStart.second + bestT * (segEnd.second - segStart.second)

        val traveled = cumLens[bestSegIdx] + segLens[bestSegIdx] * bestT
        val remaining = (cumLens.last() - traveled).coerceAtLeast(0.0)

        val remGeom = ArrayList<Pair<Double, Double>>(geom.size - bestSegIdx + 1)
        remGeom.add(projLng to projLat)
        for (i in (bestSegIdx + 1) until geom.size) {
            remGeom.add(geom[i])
        }

        return RouteProgress(
            offRouteMeters = bestDist,
            traveledMeters = traveled,
            remainingMeters = remaining,
            snappedLat = projLat,
            snappedLng = projLng,
            segmentIndex = bestSegIdx,
            remainingGeometry = remGeom
        )
    }

    fun measureAlongRoute(lat: Double, lng: Double, geom: List<Pair<Double, Double>>): Pair<Double, Double> {
        val p = trackRouteProgress(lat, lng, geom, 0)
        return p.offRouteMeters to p.remainingMeters
    }
}
