package com.terrasync.app

enum class MapStyleMode(val id: Int, val label: String) {
    VECTOR(1, "Vector Street"),
    SATELLITE(2, "Satellite Hybrid");

    companion object {
        fun fromId(id: Int): MapStyleMode = entries.firstOrNull { it.id == id } ?: VECTOR
    }
}

enum class TravelMode(
    val id: String,
    val label: String,
    val icon: String,
    val osrmPrefix: String,
    val routeColorHex: String
) {
    CAR("car", "Car", "🚗", "routed-car", "#1E88E5"),
    WALKING("walk", "Walk", "🚶", "routed-foot", "#2E8B47"),
    BICYCLE("bike", "Bicycle", "🚲", "routed-bike", "#0F766E");

    companion object {
        fun fromId(id: String?): TravelMode = entries.firstOrNull { it.id == id } ?: CAR
    }
}

data class LayerItem(
    val id: String = "",
    val name: String = "",
    val shareCode: String = "",
    val createdBy: String = "",
    val creatorDeviceId: String = "",
    val creatorUid: String = "",
    val updatedAt: Long = 0L
)

data class PinItem(
    val id: String = "",
    val layerId: String = "",
    val name: String = "",
    val description: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val status: String = "pending",
    val issueReason: String = "",
    val updatedAt: Long = 0L,
    val isEdited: Boolean = false,
    val editedBy: String = "",
    val editedAt: Long = 0L,
    val editHistory: String = ""
)

data class TrackItem(
    val id: String = "",
    val layerId: String = "",
    val name: String = "",
    val coords: String = "",
    val updatedAt: Long = 0L
)

data class NavStep(
    val instruction: String,
    val arrow: String,
    val distanceMeters: Double,
    val lat: Double,
    val lng: Double,
    val routeDistMeters: Double = 0.0
)

data class NavRoute(
    val geometry: List<Pair<Double, Double>>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val steps: List<NavStep>,
    val mode: TravelMode = TravelMode.CAR,
    val engineLabel: String = "Valhalla"
) {
    val segmentMetrics: Pair<DoubleArray, DoubleArray> by lazy {
        val segCount = (geometry.size - 1).coerceAtLeast(0)
        val segLens = DoubleArray(segCount)
        val cumLens = DoubleArray(geometry.size)
        for (i in 0 until segCount) {
            val len = RoutingEngine.haversineMeters(
                geometry[i].second, geometry[i].first,
                geometry[i + 1].second, geometry[i + 1].first
            )
            segLens[i] = len
            cumLens[i + 1] = cumLens[i] + len
        }
        segLens to cumLens
    }
}

data class RouteProgress(
    val offRouteMeters: Double,
    val traveledMeters: Double,
    val remainingMeters: Double,
    val snappedLat: Double,
    val snappedLng: Double,
    val segmentIndex: Int,
    val remainingGeometry: List<Pair<Double, Double>>
)

data class PlaceResult(
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double
)

fun statusColorHex(status: String): String = when (status.lowercase()) {
    "done" -> "#2E8B47"
    "issue" -> "#D93025"
    "pending" -> "#E69500"
    else -> "#1E88E5"
}

fun statusIconId(status: String): String = when (status.lowercase()) {
    "done" -> "pin-done"
    "issue" -> "pin-issue"
    else -> "pin-pending"
}

fun statusLabel(status: String): String = when (status.lowercase()) {
    "done" -> "Done"
    "issue" -> "Issue"
    else -> "Pending"
}

fun formatDistanceShort(meters: Double, unit: SpeedUnit = SpeedUnit.METRIC): String {
    val safeMeters = if (meters.isFinite() && meters >= 0.0) meters else 0.0
    if (unit == SpeedUnit.IMPERIAL) {
        val feet = safeMeters * 3.28084
        val miles = safeMeters / 1609.344
        return when {
            feet < 1000.0 -> "${feet.toInt()} ft"
            miles < 10.0 -> "${"%.1f".format(java.util.Locale.US, miles)} mi"
            else -> "${miles.toInt()} mi"
        }
    }
    return when {
        safeMeters < 1000.0 -> "${safeMeters.toInt()} m"
        safeMeters < 10000.0 -> "${"%.1f".format(java.util.Locale.US, safeMeters / 1000.0)} km"
        else -> "${(safeMeters / 1000.0).toInt()} km"
    }
}
