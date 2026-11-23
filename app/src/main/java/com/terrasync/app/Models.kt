package com.terrasync.app

data class LayerItem(
    val id: String = "",
    val name: String = "",
    val visible: Boolean = true,
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
    val updatedAt: Long = 0L
)

data class TrackItem(
    val id: String = "",
    val layerId: String = "",
    val name: String = "",
    val coords: String = "",
    val updatedAt: Long = 0L
)

fun statusColorHex(status: String): String = when (status.lowercase()) {
    "done" -> "#22C55E"
    "issue" -> "#EF4444"
    "pending" -> "#F59E0B"
    else -> "#3B82F6"
}

fun statusLabel(status: String): String = when (status.lowercase()) {
    "done" -> "Done"
    "issue" -> "Issue"
    else -> "Pending"
}
