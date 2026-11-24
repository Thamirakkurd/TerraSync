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
    CAR("car", "Car", "🚗", "routed-car", "#2563EB"),
    BICYCLE("bike", "Bicycle", "🚲", "routed-bike", "#0F766E"),
    WALKING("walk", "Walk", "🚶", "routed-foot", "#7C3AED");

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
)

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
    "done" -> "#22C55E"
    "issue" -> "#EF4444"
    "pending" -> "#F59E0B"
    else -> "#2563EB"
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

fun formatDistanceShort(meters: Double): String {
    return when {
        meters < 1000.0 -> "${meters.toInt()} m"
        meters < 10000.0 -> "${"%.1f".format(meters / 1000.0)} km"
        else -> "${(meters / 1000.0).toInt()} km"
    }
}
