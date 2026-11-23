package com.terrasync.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TRACKS_SOURCE = "terrasync-tracks-source"
        private const val TRACKS_LAYER = "terrasync-tracks-layer"
        private const val ROUTE_SOURCE = "terrasync-route-source"
        private const val ROUTE_LAYER = "terrasync-route-layer"
        private const val PINS_SOURCE = "terrasync-pins-source"
        private const val PINS_LAYER = "terrasync-pins-layer"
        private const val USER_LOC_SOURCE = "terrasync-userloc-source"
        private const val USER_LOC_LAYER = "terrasync-userloc-layer"

        private const val STYLE_SATELLITE = """
        {
          "version": 8,
          "sources": {
            "sat": {
              "type": "raster",
              "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
              "tileSize": 256,
              "maxzoom": 19
            },
            "roads": {
              "type": "raster",
              "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Transportation/MapServer/tile/{z}/{y}/{x}"],
              "tileSize": 256,
              "maxzoom": 19
            },
            "places": {
              "type": "raster",
              "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/Reference/World_Boundaries_and_Places/MapServer/tile/{z}/{y}/{x}"],
              "tileSize": 256,
              "maxzoom": 19
            }
          },
          "layers": [
            { "id": "sat-layer", "type": "raster", "source": "sat", "minzoom": 0, "maxzoom": 22 },
            { "id": "roads-layer", "type": "raster", "source": "roads", "minzoom": 0, "maxzoom": 22 },
            { "id": "places-layer", "type": "raster", "source": "places", "minzoom": 0, "maxzoom": 22 }
          ]
        }
        """
    }

    private lateinit var mapView: MapView
    private lateinit var topBar: FrameLayout
    private lateinit var btnSyncStatus: ImageButton
    private lateinit var btnStyleToggle: ImageButton
    private lateinit var btnLocate: ImageButton
    private lateinit var btnImport: ImageButton
    private lateinit var bottomIconBar: LinearLayout
    private lateinit var navBanner: LinearLayout
    private lateinit var tvNavArrow: TextView
    private lateinit var tvNavInstruction: TextView
    private lateinit var tvNavMeta: TextView
    private lateinit var btnNavVoice: ImageButton

    private var map: MapLibreMap? = null
    private lateinit var tileServer: OfflineTileServer
    private val repo = SyncRepo()

    private var layers: List<LayerItem> = emptyList()
    private var pins: List<PinItem> = emptyList()
    private var tracks: List<TrackItem> = emptyList()
    private var isLiveConnected = false
    private var isSatellite = false
    private var isFollowingGps = false
    private var hasAutoZoomedInitial = false

    private var currentLocation: Location? = null
    private var gpsTrackingActive = false

    // Active Turn-by-Turn Navigation State
    private var activeNavRoute: NavRoute? = null
    private var navDestLatLng: LatLng? = null
    private var navDestName: String = ""
    private var lastSpokenStepIdx: Int = -1
    private var lastRerouteTime: Long = 0L
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private val gpsListener = LocationListener { loc ->
        onNewLocation(loc)
    }

    private val kmlPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleImportUri(uri)
    }

    private val locPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            startLiveGpsUpdates()
            isFollowingGps = true
            updateLocationIconTint()
            currentLocation?.let {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16.5))
            }
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        MapLibre.getInstance(this)
        tileServer = OfflineTileServer(this).apply { start() }
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        topBar = findViewById(R.id.topBar)
        btnSyncStatus = findViewById(R.id.btnSyncStatus)
        btnStyleToggle = findViewById(R.id.btnStyleToggle)
        btnLocate = findViewById(R.id.btnLocate)
        btnImport = findViewById(R.id.btnImport)
        bottomIconBar = findViewById(R.id.bottomIconBar)
        navBanner = findViewById(R.id.navBanner)
        tvNavArrow = findViewById(R.id.tvNavArrow)
        tvNavInstruction = findViewById(R.id.tvNavInstruction)
        tvNavMeta = findViewById(R.id.tvNavMeta)
        btnNavVoice = findViewById(R.id.btnNavVoice)

        styleCircle(btnSyncStatus, "#CC0F172A", 21)
        styleCircle(btnStyleToggle, "#CC0F172A", 22)
        styleCircle(btnImport, "#CC0F766E", 24)
        styleCircle(btnLocate, "#CC0F172A", 24)
        styleCircle(bottomIconBar, "#E60F172A", 28)
        styleCircle(navBanner, "#F20F172A", 18)

        isSatellite = DevicePrefs.isDefaultSatellite(this)
        btnStyleToggle.setImageResource(if (isSatellite) R.drawable.ic_map else R.drawable.ic_satellite)
        applyKeepAwakeFlag()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }

        val root = findViewById<ViewGroup>(R.id.rootContainer)
        val bottomDock = findViewById<LinearLayout>(R.id.bottomDock)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (topBar.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(10)
            (navBanner.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(10)
            (bottomDock.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = sys.bottom + dp(14)
            topBar.requestLayout()
            navBanner.requestLayout()
            bottomDock.requestLayout()
            insets
        }

        btnSyncStatus.setOnClickListener {
            val state = if (isLiveConnected) "Synced" else "Offline Cache"
            Toast.makeText(this, "$state • ${pins.size} pins", Toast.LENGTH_SHORT).show()
        }

        btnStyleToggle.setOnClickListener {
            isSatellite = !isSatellite
            btnStyleToggle.setImageResource(if (isSatellite) R.drawable.ic_map else R.drawable.ic_satellite)
            loadMapStyle()
        }

        btnImport.setOnClickListener { kmlPicker.launch(arrayOf("*/*")) }

        btnLocate.setOnClickListener {
            if (!hasLocationPermission()) {
                locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return@setOnClickListener
            }
            startLiveGpsUpdates()
            isFollowingGps = true
            updateLocationIconTint()
            val loc = currentLocation
            if (loc != null) {
                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16.5))
            } else {
                Toast.makeText(this, "Acquiring GPS fix...", Toast.LENGTH_SHORT).show()
            }
        }

        btnNavVoice.setOnClickListener {
            val next = !DevicePrefs.isVoiceNav(this)
            DevicePrefs.setVoiceNav(this, next)
            btnNavVoice.alpha = if (next) 1.0f else 0.4f
        }
        btnNavVoice.alpha = if (DevicePrefs.isVoiceNav(this)) 1.0f else 0.4f

        findViewById<ImageButton>(R.id.btnNavStop).setOnClickListener {
            stopNavigation()
        }

        findViewById<ImageButton>(R.id.btnTabSearch).setOnClickListener { showSearchSheet() }
        findViewById<ImageButton>(R.id.btnTabLayers).setOnClickListener { showLayersSheet() }
        findViewById<ImageButton>(R.id.btnTabBookmarks).setOnClickListener { showBookmarksSheet() }
        findViewById<ImageButton>(R.id.btnTabSettings).setOnClickListener { showSettingsSheet() }

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(36.86, 42.99))
                .zoom(10.0)
                .build()

            loadMapStyle()

            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && isFollowingGps) {
                    isFollowingGps = false
                    updateLocationIconTint()
                }
            }

            m.addOnMapClickListener { latLng ->
                val screenPt = m.projection.toScreenLocation(latLng)
                val hitBox = RectF(screenPt.x - 54f, screenPt.y - 54f, screenPt.x + 54f, screenPt.y + 54f)
                val hit = m.queryRenderedFeatures(hitBox, PINS_LAYER).firstOrNull()
                val pinId = hit?.getStringProperty("id")
                val pin = pins.find { it.id == pinId }
                if (pin != null) {
                    showPinSheet(pin)
                    true
                } else {
                    false
                }
            }

            m.addOnMapLongClickListener { latLng ->
                showAddPinDialog(latLng)
                true
            }
        }

        if (hasLocationPermission()) {
            startLiveGpsUpdates()
        }

        refreshJoinedSync()
        handleDeepLinkIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val data = intent?.data ?: return
        val code = data.getQueryParameter("code") ?: return
        joinShareCode(code)
    }

    private fun refreshJoinedSync() {
        val joined = DevicePrefs.getJoinedLayerIds(this)
        repo.syncJoinedLayers(joined) { newLayers, newPins, newTracks, connected ->
            layers = newLayers
            pins = newPins
            tracks = newTracks
            isLiveConnected = connected
            runOnUiThread {
                renderMapData()
                if (!hasAutoZoomedInitial && pins.isNotEmpty()) {
                    hasAutoZoomedInitial = true
                    zoomToPoints(pins.map { LatLng(it.lat, it.lng) })
                }
            }
        }
    }

    private fun joinShareCode(rawCode: String) {
        repo.joinLayerByCode(rawCode) { ok, msg, layerId ->
            runOnUiThread {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (ok && layerId != null) {
                    DevicePrefs.addJoinedLayerId(this, layerId)
                    hasAutoZoomedInitial = false
                    refreshJoinedSync()
                }
            }
        }
    }

    private fun osmStyleJson(): String {
        val p = tileServer.port
        return """
        {
          "version": 8,
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": ["http://127.0.0.1:$p/tiles/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "maxzoom": 19
            }
          },
          "layers": [
            { "id": "osm-layer", "type": "raster", "source": "osm", "minzoom": 0, "maxzoom": 22 }
          ]
        }
        """.trimIndent()
    }

    private fun loadMapStyle() {
        val m = map ?: return
        val json = if (isSatellite) STYLE_SATELLITE else osmStyleJson()
        m.setStyle(Style.Builder().fromJson(json)) { style ->
            attachOverlayLayers(style)
            renderMapData()
        }
    }

    private fun attachOverlayLayers(style: Style) {
        if (style.getSource(TRACKS_SOURCE) == null) {
            style.addSource(GeoJsonSource(TRACKS_SOURCE))
            style.addLayer(
                LineLayer(TRACKS_LAYER, TRACKS_SOURCE).withProperties(
                    PropertyFactory.lineColor("#06B6D4"),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        }
        if (style.getSource(ROUTE_SOURCE) == null) {
            style.addSource(GeoJsonSource(ROUTE_SOURCE))
            style.addLayer(
                LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor("#3B82F6"),
                    PropertyFactory.lineWidth(6.5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        }
        if (style.getSource(PINS_SOURCE) == null) {
            style.addSource(GeoJsonSource(PINS_SOURCE))
            style.addLayer(
                CircleLayer(PINS_LAYER, PINS_SOURCE).withProperties(
                    PropertyFactory.circleColor(Expression.get("color")),
                    PropertyFactory.circleRadius(9.5f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(2.5f)
                )
            )
        }
        if (style.getSource(USER_LOC_SOURCE) == null) {
            style.addSource(GeoJsonSource(USER_LOC_SOURCE))
            style.addLayer(
                CircleLayer(USER_LOC_LAYER, USER_LOC_SOURCE).withProperties(
                    PropertyFactory.circleColor("#2563EB"),
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(3f)
                )
            )
        }
    }

    private fun renderMapData() {
        btnSyncStatus.setColorFilter(Color.parseColor(if (isLiveConnected) "#22C55E" else "#F59E0B"))

        val hiddenLayerIds = layers.filter { !it.visible }.map { it.id }.toSet()
        val visiblePins = pins.filter { it.layerId !in hiddenLayerIds }
        val visibleTracks = tracks.filter { it.layerId !in hiddenLayerIds }

        val style = map?.style ?: return
        if (!style.isFullyLoaded) return

        val pinFeatures = visiblePins.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                addStringProperty("id", p.id)
                addStringProperty("color", statusColorHex(p.status))
            }
        }
        style.getSourceAs<GeoJsonSource>(PINS_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(pinFeatures))

        val trackFeatures = visibleTracks.mapNotNull { t ->
            val pts = t.coords.split(";").mapNotNull { pair ->
                val parts = pair.split(",")
                if (parts.size == 2) {
                    val lng = parts[0].toDoubleOrNull()
                    val lat = parts[1].toDoubleOrNull()
                    if (lng != null && lat != null) Point.fromLngLat(lng, lat) else null
                } else null
            }
            if (pts.size >= 2) Feature.fromGeometry(LineString.fromLngLats(pts)) else null
        }
        style.getSourceAs<GeoJsonSource>(TRACKS_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(trackFeatures))

        val route = activeNavRoute
        val routeFeatures = if (route != null && route.geometry.size >= 2) {
            listOf(Feature.fromGeometry(LineString.fromLngLats(route.geometry.map { Point.fromLngLat(it.first, it.second) })))
        } else emptyList()
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(routeFeatures))

        currentLocation?.let { loc ->
            val f = Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude))
            style.getSourceAs<GeoJsonSource>(USER_LOC_SOURCE)
                ?.setGeoJson(FeatureCollection.fromFeatures(listOf(f)))
        }
    }

    // --- Live GPS Tracking ---

    private fun hasLocationPermission(): Boolean {
        val f = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val c = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return f == PackageManager.PERMISSION_GRANTED || c == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun startLiveGpsUpdates() {
        if (gpsTrackingActive || !hasLocationPermission()) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val bestLast = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (bestLast != null) currentLocation = bestLast

        providers.forEach { provider ->
            runCatching {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 1500L, 2f, gpsListener)
                }
            }
        }
        gpsTrackingActive = true
    }

    private fun onNewLocation(loc: Location) {
        currentLocation = loc
        renderMapData()

        val activeRoute = activeNavRoute
        val dest = navDestLatLng
        if (activeRoute != null && dest != null) {
            updateNavigationProgress(loc, activeRoute, dest)
        } else if (isFollowingGps && DevicePrefs.isAutoFollow(this)) {
            map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude)))
        }
    }

    private fun updateLocationIconTint() {
        btnLocate.setColorFilter(Color.parseColor(if (isFollowingGps) "#38BDF8" else "#FFFFFF"))
    }

    // --- Built-In Turn-by-Turn Navigation ---

    private fun startInAppNavigation(destLat: Double, destLng: Double, destTitle: String) {
        if (!hasLocationPermission()) {
            locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        startLiveGpsUpdates()
        val loc = currentLocation
        if (loc == null) {
            Toast.makeText(this, "Waiting for GPS signal to start route...", Toast.LENGTH_SHORT).show()
            return
        }

        navDestLatLng = LatLng(destLat, destLng)
        navDestName = destTitle
        lastSpokenStepIdx = -1
        Toast.makeText(this, "Calculating route...", Toast.LENGTH_SHORT).show()

        RoutingEngine.fetchDrivingRoute(loc.latitude, loc.longitude, destLat, destLng) { route, err ->
            runOnUiThread {
                if (route == null) {
                    Toast.makeText(this, err ?: "Route failed", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                activeNavRoute = route
                topBar.visibility = View.GONE
                navBanner.visibility = View.VISIBLE
                isFollowingGps = true
                updateLocationIconTint()
                renderMapData()
                updateNavigationProgress(loc, route, LatLng(destLat, destLng))

                val cam = CameraPosition.Builder()
                    .target(LatLng(loc.latitude, loc.longitude))
                    .zoom(16.8)
                    .tilt(45.0)
                    .bearing(if (loc.hasBearing()) loc.bearing.toDouble() else 0.0)
                    .build()
                map?.animateCamera(CameraUpdateFactory.newCameraPosition(cam))
            }
        }
    }

    private fun updateNavigationProgress(loc: Location, route: NavRoute, dest: LatLng) {
        val distToDest = haversineMeters(loc.latitude, loc.longitude, dest.latitude, dest.longitude)
        if (distToDest < 30.0) {
            speakPrompt("You have arrived at $navDestName")
            Toast.makeText(this, "Arrived at $navDestName", Toast.LENGTH_LONG).show()
            stopNavigation()
            return
        }

        // Find closest upcoming step
        var bestStepIdx = 0
        var bestStepDist = Double.MAX_VALUE
        route.steps.forEachIndexed { idx, step ->
            val d = haversineMeters(loc.latitude, loc.longitude, step.lat, step.lng)
            if (d < bestStepDist) {
                bestStepDist = d
                bestStepIdx = idx
            }
        }
        val nextIdx = (bestStepIdx + 1).coerceAtMost(route.steps.lastIndex)
        val step = route.steps.getOrElse(nextIdx) { NavStep("Continue to destination", "↑", distToDest, dest.latitude, dest.longitude) }
        val distToStep = haversineMeters(loc.latitude, loc.longitude, step.lat, step.lng)

        tvNavArrow.text = step.arrow
        tvNavInstruction.text = step.instruction
        val etaMin = ((distToDest / 1000.0) / 45.0 * 60.0).toInt().coerceAtLeast(1)
        tvNavMeta.text = "In ${formatDistance(distToStep)}  •  ${formatDistance(distToDest)} left (~$etaMin min)"

        if (nextIdx != lastSpokenStepIdx && distToStep < 250.0) {
            lastSpokenStepIdx = nextIdx
            speakPrompt("In ${formatDistance(distToStep)}, ${step.instruction}")
        }

        if (isFollowingGps) {
            val cam = CameraPosition.Builder()
                .target(LatLng(loc.latitude, loc.longitude))
                .zoom(16.8)
                .tilt(45.0)
                .bearing(if (loc.hasBearing()) loc.bearing.toDouble() else (map?.cameraPosition?.bearing ?: 0.0))
                .build()
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 800)
        }

        // Auto-reroute if off route by > 70m
        val minDistToLine = route.geometry.minOfOrNull { haversineMeters(loc.latitude, loc.longitude, it.second, it.first) } ?: 0.0
        val now = System.currentTimeMillis()
        if (minDistToLine > 70.0 && now - lastRerouteTime > 12000L) {
            lastRerouteTime = now
            speakPrompt("Recalculating route")
            RoutingEngine.fetchDrivingRoute(loc.latitude, loc.longitude, dest.latitude, dest.longitude) { newRoute, _ ->
                if (newRoute != null) {
                    runOnUiThread {
                        activeNavRoute = newRoute
                        lastSpokenStepIdx = -1
                        renderMapData()
                    }
                }
            }
        }
    }

    private fun stopNavigation() {
        activeNavRoute = null
        navDestLatLng = null
        navBanner.visibility = View.GONE
        topBar.visibility = View.VISIBLE
        renderMapData()
        map?.animateCamera(CameraUpdateFactory.tiltTo(0.0))
    }

    private fun speakPrompt(text: String) {
        if (ttsReady && DevicePrefs.isVoiceNav(this)) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_prompt")
        }
    }

    // --- KML / KMZ Import ---

    private fun handleImportUri(uri: Uri) {
        val fileName = getDisplayName(uri).substringBeforeLast('.')
        val layerId = UUID.randomUUID().toString()
        val stamp = DevicePrefs.getDeviceStamp(this)
        Toast.makeText(this, "Importing $fileName...", Toast.LENGTH_SHORT).show()

        Thread {
            val parsed = runCatching {
                contentResolver.openInputStream(uri)?.use { KmlParser.parse(it, layerId) }
            }.getOrNull()

            runOnUiThread {
                if (parsed == null || (parsed.pins.isEmpty() && parsed.tracks.isEmpty())) {
                    Toast.makeText(this, "No pins or tracks found in file", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                repo.importLayer(layerId, fileName.ifBlank { "Imported Layer" }, stamp, parsed) { ok, msg, _ ->
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        if (ok) {
                            DevicePrefs.addJoinedLayerId(this, layerId)
                            refreshJoinedSync()
                            if (parsed.pins.isNotEmpty()) {
                                zoomToPoints(parsed.pins.map { LatLng(it.lat, it.lng) })
                            }
                        }
                    }
                }
            }
        }.start()
    }

    // --- Pin Details Sheet (Edit, Copy, Device Stamp, Bookmark, Turn-by-Turn Nav) ---

    private fun showPinSheet(pin: PinItem) {
        val dialog = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }

        // Header Row: Title + Bookmark + Edit + Copy Name
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(this).apply {
            text = pin.name
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })

        val btnStar = MaterialButton(this).apply {
            var starred = DevicePrefs.isBookmarked(this@MainActivity, pin.id)
            text = if (starred) "★" else "☆"
            textSize = 16f
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
            setTextColor(Color.parseColor("#F59E0B"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                starred = DevicePrefs.toggleBookmark(this@MainActivity, pin.id)
                text = if (starred) "★" else "☆"
                Toast.makeText(this@MainActivity, if (starred) "Bookmarked" else "Removed bookmark", Toast.LENGTH_SHORT).show()
            }
        }
        titleRow.addView(btnStar)

        titleRow.addView(MaterialButton(this).apply {
            text = "Edit"
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
            setTextColor(Color.parseColor("#2563EB"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                dialog.dismiss()
                showEditPinDialog(pin)
            }
        })
        root.addView(titleRow)

        val layerName = layers.find { it.id == pin.layerId }?.name ?: "Layer"
        root.addView(TextView(this).apply {
            text = "$layerName  •  ${"%.6f".format(pin.lat)}, ${"%.6f".format(pin.lng)}"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(2), 0, dp(8))
        })

        if (pin.description.isNotBlank()) {
            root.addView(TextView(this).apply {
                text = pin.description
                textSize = 14f
                setPadding(0, 0, 0, dp(8))
            })
        }

        // Edited Stamp Banner
        if (pin.isEdited && pin.editedBy.isNotBlank()) {
            val stampTime = if (pin.editedAt > 0L) SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(pin.editedAt)) else ""
            val stampBox = TextView(this).apply {
                text = "✎ Edited by ${pin.editedBy}${if (stampTime.isNotBlank()) " • $stampTime" else ""}"
                textSize = 12f
                setTextColor(Color.parseColor("#0F766E"))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A0F766E"))
                    cornerRadius = dp(8).toFloat()
                }
            }
            root.addView(stampBox)
        }

        // Copy Chips Row (Copy Name, Copy Desc, Copy Coords)
        val copyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(6))
        }
        listOf(
            "Copy Name" to pin.name,
            "Copy Desc" to pin.description.ifBlank { pin.name },
            "Copy GPS" to "${pin.lat}, ${pin.lng}"
        ).forEachIndexed { idx, (label, clipVal) ->
            copyRow.addView(MaterialButton(this).apply {
                text = label
                textSize = 11f
                maxLines = 1
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                setTextColor(Color.parseColor("#334155"))
                setBackgroundColor(Color.parseColor("#E2E8F0"))
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    if (idx < 2) marginEnd = dp(6)
                }
                setOnClickListener {
                    copyToClipboard(label, clipVal)
                }
            })
        }
        root.addView(copyRow)

        // Status Buttons Row
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
        }
        val stamp = DevicePrefs.getDeviceStamp(this)
        listOf(
            Triple("pending", "Pending", "#F59E0B"),
            Triple("done", "Done", "#22C55E"),
            Triple("issue", "Issue", "#EF4444")
        ).forEachIndexed { idx, (key, title, hex) ->
            val active = pin.status.equals(key, ignoreCase = true)
            statusRow.addView(MaterialButton(this).apply {
                text = if (active) "✓ $title" else title
                textSize = 12f
                maxLines = 1
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(4), 0, dp(4), 0)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor(hex))
                alpha = if (active) 1f else 0.55f
                cornerRadius = dp(12)
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (idx < 2) marginEnd = dp(6)
                }
                setOnClickListener {
                    repo.updatePinStatus(pin, key, stamp)
                    dialog.dismiss()
                }
            })
        }
        root.addView(statusRow)

        // Navigation Row: Built-in Turn-by-Turn + External App Fallback
        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        navRow.addView(MaterialButton(this).apply {
            text = "Drive Here (In-App)"
            maxLines = 1
            textSize = 13f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1.5f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                dialog.dismiss()
                startInAppNavigation(pin.lat, pin.lng, pin.name)
            }
        })
        navRow.addView(MaterialButton(this).apply {
            text = "External"
            maxLines = 1
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#334155"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 0.8f)
            setOnClickListener {
                val label = Uri.encode(pin.name)
                val geoUri = Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}($label)")
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, geoUri), "Open in"))
            }
        })
        root.addView(navRow)

        if (pin.editHistory.isNotBlank()) {
            root.addView(TextView(this).apply {
                text = "Edit Log:\n${pin.editHistory}"
                textSize = 11f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(10), 0, 0)
            })
        }

        root.addView(MaterialButton(this).apply {
            text = "Delete Pin"
            setTextColor(Color.parseColor("#EF4444"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                repo.deletePin(pin.id)
                dialog.dismiss()
            }
        })

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
    }

    private fun showEditPinDialog(pin: PinItem) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val etName = EditText(this).apply {
            hint = "Pin name"
            setText(pin.name)
        }
        val etDesc = EditText(this).apply {
            hint = "Description / field notes"
            setText(pin.description)
            minLines = 2
        }
        box.addView(etName)
        box.addView(etDesc)

        AlertDialog.Builder(this)
            .setTitle("Edit Pin")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val stamp = DevicePrefs.getDeviceStamp(this)
                repo.updatePinText(pin, etName.text.toString().trim(), etDesc.text.toString().trim(), stamp)
                Toast.makeText(this, "Saved & stamped ($stamp)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Tab 1: Search Page ---

    private fun showSearchSheet() {
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Search Pins, Places & Coordinates"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

        val etQuery = EditText(this).apply {
            hint = "Search pin name, street, or 36.86, 42.99..."
            setSingleLine(true)
        }
        root.addView(etQuery)

        val btnWebSearch = MaterialButton(this).apply {
            text = "Search OpenStreetMap Places"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(12)
        }
        root.addView(btnWebSearch)

        val resultsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val scroll = ScrollView(this).apply { addView(resultsBox) }
        root.addView(scroll)

        fun renderLocalMatches(q: String) {
            resultsBox.removeAllViews()
            val trimmed = q.trim()

            // Direct coordinate match: "lat, lng"
            val coordParts = trimmed.split(",").map { it.trim().toDoubleOrNull() }
            if (coordParts.size == 2 && coordParts[0] != null && coordParts[1] != null) {
                val lat = coordParts[0]!!
                val lng = coordParts[1]!!
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    val row = TextView(this).apply {
                        text = "📍 Jump to coordinates: $lat, $lng"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#2563EB"))
                        setPadding(dp(4), dp(10), dp(4), dp(10))
                        setOnClickListener {
                            dialog.dismiss()
                            map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.5))
                        }
                    }
                    resultsBox.addView(row)
                }
            }

            val matches = if (trimmed.isEmpty()) {
                pins.take(25)
            } else {
                pins.filter {
                    it.name.contains(trimmed, ignoreCase = true) ||
                        it.description.contains(trimmed, ignoreCase = true) ||
                        statusLabel(it.status).contains(trimmed, ignoreCase = true)
                }
            }

            matches.forEach { pin ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(9), dp(4), dp(9))
                    setOnClickListener {
                        dialog.dismiss()
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pin.lat, pin.lng), 17.0))
                        showPinSheet(pin)
                    }
                }
                row.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(statusColorHex(pin.status)))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(11), dp(11)).apply { marginEnd = dp(10) }
                })
                row.addView(TextView(this).apply {
                    text = "${pin.name}  (${statusLabel(pin.status)})"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                resultsBox.addView(row)
            }
        }

        renderLocalMatches("")
        etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderLocalMatches(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnWebSearch.setOnClickListener {
            val q = etQuery.text.toString().trim()
            if (q.isBlank()) return@setOnClickListener
            btnWebSearch.text = "Searching..."
            RoutingEngine.searchPlaces(q) { places ->
                runOnUiThread {
                    btnWebSearch.text = "Search OpenStreetMap Places"
                    places.forEach { place ->
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(4), dp(10), dp(4), dp(10))
                            setOnClickListener {
                                dialog.dismiss()
                                map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(place.lat, place.lng), 16.0))
                            }
                        }
                        row.addView(TextView(this).apply {
                            text = "🌐 ${place.title}"
                            textSize = 14f
                            setTypeface(null, Typeface.BOLD)
                        })
                        if (place.subtitle.isNotBlank()) {
                            row.addView(TextView(this).apply {
                                text = place.subtitle
                                textSize = 12f
                                setTextColor(Color.parseColor("#64748B"))
                            })
                        }
                        resultsBox.addView(row, 0)
                    }
                }
            }
        }

        dialog.setContentView(root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    // --- Tab 2: Layers Page -> Layer Pins Subpage ---

    private fun showLayersSheet() {
        val dialog = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        headerRow.addView(TextView(this).apply {
            text = "My Layers (${layers.size})"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        headerRow.addView(MaterialButton(this).apply {
            text = "+ Join Code"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0F766E"))
            cornerRadius = dp(12)
            setOnClickListener {
                dialog.dismiss()
                showJoinCodeDialog()
            }
        })
        root.addView(headerRow)

        if (layers.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No private layers yet. Import a KML/KMZ file or tap + Join Code to unlock a shared layer."
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(6), 0, dp(12))
            })
        } else {
            layers.forEach { layer ->
                val layerPins = pins.filter { it.layerId == layer.id }
                val layerTracks = tracks.filter { it.layerId == layer.id }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                row.addView(CheckBox(this).apply {
                    isChecked = layer.visible
                    setOnCheckedChangeListener { _, checked ->
                        repo.toggleLayerVisibility(layer.id, checked)
                    }
                })

                val infoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        dialog.dismiss()
                        showLayerPinsSubpage(layer, layerPins)
                    }
                }
                infoCol.addView(TextView(this).apply {
                    text = "${layer.name} (${layerPins.size}) ›"
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                })
                if (layer.shareCode.isNotBlank()) {
                    infoCol.addView(TextView(this).apply {
                        text = "Code: ${layer.shareCode}"
                        textSize = 11f
                        setTextColor(Color.parseColor("#64748B"))
                    })
                }
                row.addView(infoCol)

                row.addView(MaterialButton(this).apply {
                    text = "Share"
                    textSize = 11f
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(8), 0, dp(8), 0)
                    setTextColor(Color.parseColor("#2563EB"))
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnClickListener {
                        shareLayerLink(layer)
                    }
                })

                row.addView(MaterialButton(this).apply {
                    text = "Del"
                    textSize = 11f
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(8), 0, dp(8), 0)
                    setTextColor(Color.parseColor("#EF4444"))
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Remove ${layer.name}?")
                            .setMessage("Choose whether to remove from this phone only or delete from cloud.")
                            .setPositiveButton("Delete Cloud") { _, _ ->
                                DevicePrefs.removeJoinedLayerId(this@MainActivity, layer.id)
                                repo.deleteLayer(layer.id, layerPins.map { it.id }, layerTracks.map { it.id })
                                refreshJoinedSync()
                                dialog.dismiss()
                            }
                            .setNeutralButton("Leave Layer") { _, _ ->
                                DevicePrefs.removeJoinedLayerId(this@MainActivity, layer.id)
                                refreshJoinedSync()
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                })
                root.addView(row)
            }
        }

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
    }

    private fun showLayerPinsSubpage(layer: LayerItem, layerPins: List<PinItem>) {
        val dialog = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        topRow.addView(MaterialButton(this).apply {
            text = "‹ Back"
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(10), 0)
            setTextColor(Color.parseColor("#2563EB"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                dialog.dismiss()
                showLayersSheet()
            }
        })
        topRow.addView(TextView(this).apply {
            text = layer.name
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        topRow.addView(MaterialButton(this).apply {
            text = "Zoom"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(10)
            setOnClickListener {
                dialog.dismiss()
                zoomToPoints(layerPins.map { LatLng(it.lat, it.lng) })
            }
        })
        root.addView(topRow)

        val doneCount = layerPins.count { it.status == "done" }
        val issueCount = layerPins.count { it.status == "issue" }
        val pendingCount = layerPins.size - doneCount - issueCount
        root.addView(TextView(this).apply {
            text = "${layerPins.size} pins  •  ✓ $doneCount Done  •  ! $issueCount Issue  •  $pendingCount Pending"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 0, 0, dp(10))
        })

        layerPins.sortedBy { it.name }.forEach { pin ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(9), dp(4), dp(9))
                setOnClickListener {
                    dialog.dismiss()
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pin.lat, pin.lng), 17.0))
                    showPinSheet(pin)
                }
            }
            row.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(statusColorHex(pin.status)))
                }
                layoutParams = LinearLayout.LayoutParams(dp(11), dp(11)).apply { marginEnd = dp(10) }
            })
            val labelCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            labelCol.addView(TextView(this).apply {
                text = "${pin.name}  (${statusLabel(pin.status)})"
                textSize = 14f
            })
            if (pin.isEdited && pin.editedBy.isNotBlank()) {
                labelCol.addView(TextView(this).apply {
                    text = "Edited by ${pin.editedBy}"
                    textSize = 11f
                    setTextColor(Color.parseColor("#0F766E"))
                })
            }
            row.addView(labelCol)
            root.addView(row)
        }

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
    }

    private fun shareLayerLink(layer: LayerItem) {
        val code = layer.shareCode
        val link = "https://thamirakkurd.github.io/TerraSync/join?code=$code"
        val msg = "TerraSync Layer: ${layer.name}\nShare Code: $code\nOpen link: $link"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, msg)
        }
        startActivity(Intent.createChooser(intent, "Share Layer"))
    }

    private fun showJoinCodeDialog() {
        val input = EditText(this).apply { hint = "Enter 6-character code (e.g. AKR742)" }
        AlertDialog.Builder(this)
            .setTitle("Join Shared Layer")
            .setView(input)
            .setPositiveButton("Join") { _, _ ->
                joinShareCode(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Tab 3: Bookmarks Page ---

    private fun showBookmarksSheet() {
        val dialog = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }

        val bookmarkedIds = DevicePrefs.getBookmarks(this)
        val bookmarkedPins = pins.filter { it.id in bookmarkedIds }

        root.addView(TextView(this).apply {
            text = "Bookmarks (${bookmarkedPins.size})"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

        if (bookmarkedPins.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No bookmarked pins yet. Tap a pin on the map and press ☆ to save it here."
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
            })
        } else {
            bookmarkedPins.forEach { pin ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(10), dp(4), dp(10))
                    setOnClickListener {
                        dialog.dismiss()
                        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pin.lat, pin.lng), 17.0))
                        showPinSheet(pin)
                    }
                }
                row.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(statusColorHex(pin.status)))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(11), dp(11)).apply { marginEnd = dp(10) }
                })
                row.addView(TextView(this).apply {
                    text = pin.name
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                row.addView(MaterialButton(this).apply {
                    text = "Drive"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    setBackgroundColor(Color.parseColor("#2563EB"))
                    cornerRadius = dp(10)
                    setOnClickListener {
                        dialog.dismiss()
                        startInAppNavigation(pin.lat, pin.lng, pin.name)
                    }
                })
                root.addView(row)
            }
        }

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
    }

    // --- Tab 4: Settings Page (Settings First, Offline Regional Packs, App Info / About Below) ---

    private fun showSettingsSheet() {
        val dialog = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "Settings"
            textSize = 19f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(10))
        })

        // 1. Device Identity Stamp
        root.addView(TextView(this).apply {
            text = "Device Edit Stamp"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
        })
        root.addView(TextView(this).apply {
            text = "Current stamp: ${DevicePrefs.getDeviceStamp(this@MainActivity)}"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(2), 0, dp(6))
        })

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        val etDeviceName = EditText(this).apply {
            hint = "Optional custom name (e.g. Supervisor)"
            setText(DevicePrefs.getCustomName(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameRow.addView(etDeviceName)
        nameRow.addView(MaterialButton(this).apply {
            text = "Save"
            textSize = 12f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(10)
            setOnClickListener {
                DevicePrefs.setCustomName(this@MainActivity, etDeviceName.text.toString())
                Toast.makeText(this@MainActivity, "Updated: ${DevicePrefs.getDeviceStamp(this@MainActivity)}", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(nameRow)

        // 2. Map & Navigation Preferences
        fun addSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(TextView(this).apply {
                text = title
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(Switch(this).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
            })
            root.addView(row)
        }

        addSwitchRow("Default to Satellite Hybrid", DevicePrefs.isDefaultSatellite(this)) {
            DevicePrefs.setDefaultSatellite(this, it)
        }
        addSwitchRow("Auto-Follow Live GPS", DevicePrefs.isAutoFollow(this)) {
            DevicePrefs.setAutoFollow(this, it)
        }
        addSwitchRow("Keep Screen Awake", DevicePrefs.isKeepAwake(this)) {
            DevicePrefs.setKeepAwake(this, it)
            applyKeepAwakeFlag()
        }
        addSwitchRow("Turn-by-Turn Voice Prompts", DevicePrefs.isVoiceNav(this)) {
            DevicePrefs.setVoiceNav(this, it)
        }

        // 3. Optional Offline Street Maps (Separate Northern Kurdistan vs Central & Southern Iraq)
        root.addView(TextView(this).apply {
            text = "Offline Street Maps (Optional)"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dp(16), 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = "Online by default. Download a regional street pack below to use the regular map with zero signal."
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 0, 0, dp(8))
        })

        OfflineTileServer.REGIONS.forEach { region ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1464748B"))
                    cornerRadius = dp(12).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            }

            card.addView(TextView(this).apply {
                text = region.title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            })
            card.addView(TextView(this).apply {
                text = region.subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(2), 0, dp(6))
            })

            val tvStatus = TextView(this).apply {
                val (cnt, bytes) = tileServer.getRegionStats(region.id)
                val mb = bytes / (1024.0 * 1024.0)
                text = if (cnt > 0) "Downloaded: $cnt tiles (${"%.1f".format(mb)} MB)" else "Not downloaded (online only)"
                textSize = 12f
                setTextColor(Color.parseColor("#0F766E"))
            }
            card.addView(tvStatus)

            val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                visibility = View.GONE
                max = 100
            }
            card.addView(progressBar)

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
            }
            val btnDl = MaterialButton(this).apply {
                text = "Download"
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#0F766E"))
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
            }
            val btnDel = MaterialButton(this).apply {
                text = "Delete"
                textSize = 12f
                setTextColor(Color.parseColor("#EF4444"))
                setBackgroundColor(Color.TRANSPARENT)
            }

            btnDl.setOnClickListener {
                if (tileServer.activeDownloadRegionId != null) {
                    tileServer.cancelActiveDownload()
                    btnDl.text = "Download"
                    return@setOnClickListener
                }
                btnDl.text = "Cancel"
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
                tileServer.downloadRegion(region) { done, total, finished, err ->
                    runOnUiThread {
                        val pct = if (total > 0) (done * 100 / total) else 0
                        progressBar.progress = pct
                        tvStatus.text = if (err != null) err else "Downloading: $pct% ($done / $total tiles)"
                        if (finished) {
                            btnDl.text = "Download"
                            progressBar.visibility = View.GONE
                            val (cnt, bytes) = tileServer.getRegionStats(region.id)
                            val mb = bytes / (1024.0 * 1024.0)
                            tvStatus.text = if (cnt > 0) "Downloaded: $cnt tiles (${"%.1f".format(mb)} MB)" else "Not downloaded"
                            loadMapStyle()
                        }
                    }
                }
            }

            btnDel.setOnClickListener {
                tileServer.deleteRegion(region.id)
                tvStatus.text = "Deleted (online only)"
                Toast.makeText(this@MainActivity, "Removed ${region.title} offline pack", Toast.LENGTH_SHORT).show()
            }

            btnRow.addView(btnDl)
            btnRow.addView(btnDel)
            card.addView(btnRow)
            root.addView(card)
        }

        // 4. App Info / About Section (Below Settings)
        root.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#3364748B"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(12)
                bottomMargin = dp(14)
            }
        })

        root.addView(TextView(this).apply {
            text = "About TerraSync"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Version 2.2.0 • Field KML/KMZ Mapping, Live Team Sync & Turn-by-Turn Navigation.\nBuilt by Thamir (@Thamirakkurd) for field supervision, feeder mapping, and offline site navigation."
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(4), 0, dp(10))
        })

        val linkRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "GitHub Profile" to "https://github.com/Thamirakkurd",
            "Repository" to "https://github.com/Thamirakkurd/TerraSync",
            "Releases" to "https://github.com/Thamirakkurd/TerraSync/releases"
        ).forEachIndexed { idx, (label, url) ->
            linkRow.addView(MaterialButton(this).apply {
                text = label
                textSize = 11f
                maxLines = 1
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (idx < 2) marginEnd = dp(6)
                }
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            })
        }
        root.addView(linkRow)

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    // --- Add Manual Pin Dialog ---

    private fun showAddPinDialog(latLng: LatLng) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val etName = EditText(this).apply { hint = "Pin name" }
        val etDesc = EditText(this).apply { hint = "Description (optional)" }
        box.addView(etName)
        box.addView(etDesc)

        AlertDialog.Builder(this)
            .setTitle("Add Synced Pin")
            .setMessage("${"%.6f".format(latLng.latitude)}, ${"%.6f".format(latLng.longitude)}")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val stamp = DevicePrefs.getDeviceStamp(this)
                val layerId = repo.addManualPin(
                    etName.text.toString().trim(),
                    etDesc.text.toString().trim(),
                    latLng.latitude,
                    latLng.longitude,
                    stamp
                )
                DevicePrefs.addJoinedLayerId(this, layerId)
                refreshJoinedSync()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Utilities ---

    private fun applyKeepAwakeFlag() {
        if (DevicePrefs.isKeepAwake(this)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun copyToClipboard(label: String, value: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun zoomToPoints(pts: List<LatLng>) {
        val m = map ?: return
        if (pts.isEmpty()) return
        if (pts.size == 1) {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(pts.first(), 16.5))
            return
        }
        runCatching {
            val b = LatLngBounds.Builder()
            pts.forEach { b.include(it) }
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 140))
        }.onFailure {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(pts.first(), 15.5))
        }
    }

    private fun getDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: "Layer"
        }
        return uri.lastPathSegment ?: "Layer"
    }

    private fun styleCircle(v: View, hex: String, radiusDp: Int) {
        v.background = GradientDrawable().apply {
            setColor(Color.parseColor(hex))
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) "${meters.toInt()} m" else "${"%.1f".format(meters / 1000.0)} km"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        runCatching {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(gpsListener)
        }
        tts?.shutdown()
        repo.stopListening()
        tileServer.stop()
        mapView.onDestroy()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
