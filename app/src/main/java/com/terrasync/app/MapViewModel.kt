package com.terrasync.app

import androidx.lifecycle.ViewModel

/**
 * Pure UI-agnostic coordinate pair kept inside MapViewModel so ViewModel remains
 * 100% free of Android Context, View, and MapLibre Style dependencies.
 */
data class GeoPoint(
    val lat: Double,
    val lng: Double
)

data class RouteOriginState(
    val lat: Double,
    val lng: Double,
    val label: String
)

data class TappedLocationState(
    val title: String,
    val subtitle: String,
    val lat: Double,
    val lng: Double
)

/**
 * Configuration-resilient ViewModel owning domain state across theme switches,
 * locale switches, and Activity configuration changes.
 */
class MapViewModel : ViewModel() {

    companion object {
        val ALL_STATUSES: Set<String> = setOf("pending", "done", "issue")
    }

    var folderSnapshot: FolderIndexSnapshot = FolderIndexSnapshot()
        private set

    var visibleStatuses: MutableSet<String> = ALL_STATUSES.toMutableSet()
    var hideSentPins: Boolean = false

    var selectedPinId: String? = null
    var selectedStackedPinIds: List<String> = emptyList()
    var selectedStackedIndex: Int = 0
    var selectedMarkerGeoPoint: GeoPoint? = null
    var tappedLocation: TappedLocationState? = null

    var searchQuery: String = ""
    var searchQuickFilter: String = "all"

    var routeOrigin: RouteOriginState? = null
    var previewRoute: NavRoute? = null
    var activeNavRoute: NavRoute? = null
    var activeNavDest: GeoPoint? = null
    var activeNavDestTitle: String = ""

    var travelMode: TravelMode = TravelMode.CAR
    var mapStyleMode: MapStyleMode = MapStyleMode.VECTOR
    var isFullScreenMap: Boolean = false
    var isFollowingGps: Boolean = false
    var hasAutoZoomedInitial: Boolean = false

    var isRulerMode: Boolean = false
    val rulerPoints: MutableList<GeoPoint> = mutableListOf()

    var isLassoMode: Boolean = false
    var lassoMoveMapMode: Boolean = false
    var lassoPendingPinIds: List<String> = emptyList()
    val sentUndoStack: MutableList<List<String>> = mutableListOf()

    var operatorStamp: String = ""
    var pendingSharedPinId: String? = null
    var pendingZoomLayerId: String? = null

    fun updateFolderSnapshot(next: FolderIndexSnapshot) {
        folderSnapshot = next
    }

    fun selectPin(pinId: String, stackedIds: List<String> = listOf(pinId)) {
        selectedPinId = pinId
        selectedStackedPinIds = if (stackedIds.isNotEmpty()) stackedIds else listOf(pinId)
        val idx = selectedStackedPinIds.indexOf(pinId)
        selectedStackedIndex = if (idx >= 0) idx else 0
        tappedLocation = null
    }

    fun cycleStackedPin(delta: Int): String? {
        val list = selectedStackedPinIds
        if (list.isEmpty()) return null
        val nextIdx = Math.floorMod(selectedStackedIndex + delta, list.size)
        selectedStackedIndex = nextIdx
        val nextId = list[nextIdx]
        selectedPinId = nextId
        return nextId
    }

    fun selectTappedLocation(lat: Double, lng: Double, title: String, subtitle: String) {
        selectedPinId = null
        selectedStackedPinIds = emptyList()
        selectedStackedIndex = 0
        selectedMarkerGeoPoint = GeoPoint(lat, lng)
        tappedLocation = TappedLocationState(title, subtitle, lat, lng)
    }

    fun clearSelection() {
        selectedPinId = null
        selectedStackedPinIds = emptyList()
        selectedStackedIndex = 0
        selectedMarkerGeoPoint = null
        tappedLocation = null
    }

    fun toggleStatusFilter(statusKey: String): Boolean {
        val added = if (visibleStatuses.contains(statusKey)) {
            visibleStatuses.remove(statusKey)
            false
        } else {
            visibleStatuses.add(statusKey)
            true
        }
        return added
    }

    fun resetFilters() {
        visibleStatuses.clear()
        visibleStatuses.addAll(ALL_STATUSES)
        hideSentPins = false
    }

    fun setRouteOrigin(lat: Double, lng: Double, label: String) {
        routeOrigin = RouteOriginState(lat, lng, label)
    }

    fun clearRouteOrigin() {
        routeOrigin = null
    }

    fun startNavigation(route: NavRoute, destLat: Double, destLng: Double, destTitle: String) {
        previewRoute = null
        activeNavRoute = route
        activeNavDest = GeoPoint(destLat, destLng)
        activeNavDestTitle = destTitle
        isFollowingGps = true
    }

    fun stopNavigation() {
        activeNavRoute = null
        activeNavDest = null
        activeNavDestTitle = ""
    }

    fun applyRulerMode(enabled: Boolean) {
        isRulerMode = enabled
        if (!enabled) {
            rulerPoints.clear()
        }
    }

    fun addRulerPoint(lat: Double, lng: Double) {
        rulerPoints.add(GeoPoint(lat, lng))
    }

    fun undoRulerPoint(): Boolean {
        if (rulerPoints.isEmpty()) return false
        rulerPoints.removeAt(rulerPoints.lastIndex)
        return true
    }

    fun clearRulerPoints() {
        rulerPoints.clear()
    }

    fun applyLassoMode(enabled: Boolean) {
        isLassoMode = enabled
        if (!enabled) {
            lassoMoveMapMode = false
            lassoPendingPinIds = emptyList()
        }
    }

    fun clearLassoSelection() {
        lassoPendingPinIds = emptyList()
    }

    fun setSearchState(query: String, quickFilter: String = searchQuickFilter) {
        searchQuery = query
        searchQuickFilter = quickFilter
    }
}
