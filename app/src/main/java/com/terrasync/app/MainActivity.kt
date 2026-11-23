package com.terrasync.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import com.google.android.material.materialswitch.MaterialSwitch
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

        private val ISSUE_PRESETS = listOf(
            "Customer Refused / No Access",
            "Meter Missing / Damaged",
            "Wrong Location / Coordinates",
            "Closed / Nobody Home",
            "Safety Hazard / Network Fault"
        )

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
    private lateinit var offlineMaps: OfflineMapManager
    private val repo = SyncRepo()

    private var layers: List<LayerItem> = emptyList()
    private var pins: List<PinItem> = emptyList()
    private var tracks: List<TrackItem> = emptyList()
    private var isLiveConnected = false
    private var isSatellite = false
    private var isFollowingGps = false
    private var hasAutoZoomedInitial = false
    private var statusBarInsetTop = 0

    private var currentLocation: Location? = null
    private var gpsTrackingActive = false

    // Active Turn-by-Turn Navigation State
    private var activeNavRoute: NavRoute? = null
    private var navDestLatLng: LatLng? = null
    private var navDestName: String = ""
    private var navCurrentStepIdx: Int = 0
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
        offlineMaps = OfflineMapManager(this).apply { refreshAll() }
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

        styleRipplePill(btnSyncStatus, "#CC0F172A", 22)
        styleRipplePill(btnStyleToggle, "#CC0F172A", 22)
        styleRipplePill(btnImport, "#CC0F766E", 24)
        styleRipplePill(btnLocate, "#CC0F172A", 24)
        stylePill(bottomIconBar, "#E60F172A", 28)
        stylePill(navBanner, "#F20F172A", 18)

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
            statusBarInsetTop = sys.top
            (topBar.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(10)
            (navBanner.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(10)
            (bottomDock.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = sys.bottom + dp(14)
            topBar.requestLayout()
            navBanner.requestLayout()
            bottomDock.requestLayout()
            map?.uiSettings?.setCompassMargins(0, statusBarInsetTop + dp(62), dp(14), 0)
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

        // 4-Icon Bottom Bar: Search | Layers & Bookmarks | Settings | About
        findViewById<ImageButton>(R.id.btnTabSearch).setOnClickListener { showSearchSheet() }
        findViewById<ImageButton>(R.id.btnTabLayers).setOnClickListener { showLayersAndBookmarksSheet(startOnBookmarks = false) }
        findViewById<ImageButton>(R.id.btnTabSettings).setOnClickListener { showSettingsSheet() }
        findViewById<ImageButton>(R.id.btnTabAbout).setOnClickListener { showAboutSheet() }

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.setCompassMargins(0, statusBarInsetTop + dp(62), dp(14), 0)
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(36.75, 43.88))
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

    private fun loadMapStyle() {
        val m = map ?: return
        val builder = if (isSatellite) {
            Style.Builder().fromJson(STYLE_SATELLITE)
        } else {
            Style.Builder().fromUri(OfflineMapManager.STYLE_STREET_URL)
        }
        m.setStyle(builder) { style ->
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

        val hiddenLayerIds = DevicePrefs.getHiddenLayerIds(this)
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

    // --- Live GPS Tracking (Battery-Safe Lifecycle) ---

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

    private fun stopLiveGpsUpdates() {
        if (!gpsTrackingActive) return
        runCatching {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(gpsListener)
        }
        gpsTrackingActive = false
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

    // --- Built-In Turn-by-Turn Navigation (Fixed Segment Math + Ordered Steps) ---

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
                navCurrentStepIdx = if (route.steps.size > 1) 1 else 0
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
        val directToDest = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, dest.latitude, dest.longitude)
        if (directToDest < 28.0) {
            speakPrompt("You have arrived at $navDestName")
            Toast.makeText(this, "Arrived at $navDestName", Toast.LENGTH_LONG).show()
            stopNavigation()
            return
        }

        val (offRouteMeters, remainingRoadMeters) = RoutingEngine.measureAlongRoute(loc.latitude, loc.longitude, route.geometry)

        // Advance maneuver step monotonically when within 30m of current maneuver
        if (route.steps.isNotEmpty()) {
            navCurrentStepIdx = navCurrentStepIdx.coerceIn(0, route.steps.lastIndex)
            while (navCurrentStepIdx < route.steps.lastIndex) {
                val s = route.steps[navCurrentStepIdx]
                val d = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, s.lat, s.lng)
                if (d < 30.0) {
                    navCurrentStepIdx++
                } else {
                    break
                }
            }
        }

        val step = route.steps.getOrElse(navCurrentStepIdx) {
            NavStep("Continue to destination", "↑", remainingRoadMeters, dest.latitude, dest.longitude)
        }
        val distToStep = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, step.lat, step.lng)

        val etaSec = if (route.totalDistanceMeters > 10.0 && route.totalDurationSeconds > 0.0) {
            (remainingRoadMeters / route.totalDistanceMeters) * route.totalDurationSeconds
        } else {
            (remainingRoadMeters / 1000.0) / 45.0 * 3600.0
        }
        val etaMin = (etaSec / 60.0).toInt().coerceAtLeast(1)

        tvNavArrow.text = step.arrow
        tvNavInstruction.text = step.instruction
        tvNavMeta.text = "In ${formatDistance(distToStep)}  •  ${formatDistance(remainingRoadMeters)} left (~$etaMin min)"

        if (navCurrentStepIdx != lastSpokenStepIdx && distToStep < 250.0) {
            lastSpokenStepIdx = navCurrentStepIdx
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

        // True point-to-segment off-route check (> 65m off road line)
        val now = System.currentTimeMillis()
        if (offRouteMeters > 65.0 && now - lastRerouteTime > 12000L) {
            lastRerouteTime = now
            speakPrompt("Recalculating route")
            RoutingEngine.fetchDrivingRoute(loc.latitude, loc.longitude, dest.latitude, dest.longitude) { newRoute, _ ->
                if (newRoute != null) {
                    runOnUiThread {
                        activeNavRoute = newRoute
                        navCurrentStepIdx = if (newRoute.steps.size > 1) 1 else 0
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
        navCurrentStepIdx = 0
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
        val devId = DevicePrefs.getDeviceId(this)
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
                repo.importLayer(layerId, fileName.ifBlank { "Imported Layer" }, stamp, devId, parsed) { ok, msg, _ ->
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

    // --- Pin Details Sheet (Issue Pop-up Menu, Edit, Copy, Device Stamp, Bookmark, Turn-by-Turn Nav) ---

    private fun showPinSheet(pin: PinItem) {
        val dialog = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }

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

        // Active Issue Banner
        if (pin.status.equals("issue", ignoreCase = true) && pin.issueReason.isNotBlank()) {
            root.addView(TextView(this).apply {
                text = "⚠ Issue: ${pin.issueReason}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#DC2626"))
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1AEF4444"))
                    cornerRadius = dp(8).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(8)
                }
            })
        }

        // Edited Stamp Banner
        if (pin.isEdited && pin.editedBy.isNotBlank()) {
            val stampTime = if (pin.editedAt > 0L) SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(pin.editedAt)) else ""
            root.addView(TextView(this).apply {
                text = "✎ Edited by ${pin.editedBy}${if (stampTime.isNotBlank()) " • $stampTime" else ""}"
                textSize = 12f
                setTextColor(Color.parseColor("#0F766E"))
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A0F766E"))
                    cornerRadius = dp(8).toFloat()
                }
            })
        }

        // Copy Chips Row
        val copyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(6))
        }
        listOf(
            "Copy Name" to pin.name,
            "Copy Desc" to pin.description,
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
                    if (clipVal.isBlank()) {
                        Toast.makeText(this@MainActivity, "No description to copy", Toast.LENGTH_SHORT).show()
                    } else {
                        copyToClipboard(label, clipVal)
                    }
                }
            })
        }
        root.addView(copyRow)

        // Status Buttons Row (Tapping Issue opens the "What is the issue?" pop-up menu!)
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
                    if (key == "issue") {
                        dialog.dismiss()
                        showIssueReasonDialog(pin)
                    } else {
                        repo.updatePinStatus(pin, key, stamp)
                        dialog.dismiss()
                    }
                }
            })
        }
        root.addView(statusRow)

        // Navigation Row
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
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Delete \"${pin.name}\"?")
                    .setMessage("This removes the pin for everyone in this layer.")
                    .setPositiveButton("Delete") { _, _ ->
                        repo.deletePin(pin.id)
                        dialog.dismiss()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
    }

    private fun showIssueReasonDialog(pin: PinItem) {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }

        box.addView(TextView(this).apply {
            text = "Tap a common issue or type details below:"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 0, 0, dp(8))
        })

        val etReason = EditText(this).apply {
            hint = "Describe what the issue is..."
            setText(pin.issueReason)
            minLines = 2
        }

        ISSUE_PRESETS.forEach { preset ->
            box.addView(MaterialButton(this).apply {
                text = preset
                textSize = 12f
                isAllCaps = false
                setTextColor(Color.parseColor("#1E293B"))
                setBackgroundColor(Color.parseColor("#F1F5F9"))
                cornerRadius = dp(10)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply {
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    etReason.setText(preset)
                    etReason.setSelection(preset.length)
                }
            })
        }

        box.addView(etReason)
        scroll.addView(box)

        val dlg = AlertDialog.Builder(this)
            .setTitle("What is the issue?")
            .setView(scroll)
            .setPositiveButton("Save Issue", null)
            .setNegativeButton("Cancel", null)
            .create()

        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val reason = etReason.text.toString().trim()
                if (reason.isBlank()) {
                    Toast.makeText(this, "Please select or type the issue first", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val stamp = DevicePrefs.getDeviceStamp(this)
                repo.updatePinStatus(pin, "issue", stamp, reason)
                Toast.makeText(this, "Marked Issue: $reason", Toast.LENGTH_SHORT).show()
                dlg.dismiss()
            }
        }
        dlg.show()
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
            hint = "Search pin name, issue, street, or 36.86, 42.99..."
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

            val coordParts = trimmed.split(",").map { it.trim().toDoubleOrNull() }
            if (coordParts.size == 2 && coordParts[0] != null && coordParts[1] != null) {
                val lat = coordParts[0]!!
                val lng = coordParts[1]!!
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    resultsBox.addView(TextView(this).apply {
                        text = "📍 Go to coordinates: $lat, $lng"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#2563EB"))
                        setPadding(dp(4), dp(10), dp(4), dp(10))
                        setOnClickListener {
                            dialog.dismiss()
                            showPlaceActionDialog("Coordinates", "$lat, $lng", lat, lng)
                        }
                    })
                }
            }

            val matches = if (trimmed.isEmpty()) {
                pins.take(25)
            } else {
                pins.filter {
                    it.name.contains(trimmed, ignoreCase = true) ||
                        it.description.contains(trimmed, ignoreCase = true) ||
                        it.issueReason.contains(trimmed, ignoreCase = true) ||
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
                    if (places.isEmpty()) {
                        Toast.makeText(this, "No places found", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    places.reversed().forEach { place ->
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(4), dp(10), dp(4), dp(10))
                            setOnClickListener {
                                dialog.dismiss()
                                showPlaceActionDialog(place.title, place.subtitle, place.lat, place.lng)
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

    private fun showPlaceActionDialog(title: String, subtitle: String, lat: Double, lng: Double) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.0))
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(if (subtitle.isNotBlank()) "$subtitle\n${"%.6f".format(lat)}, ${"%.6f".format(lng)}" else "${"%.6f".format(lat)}, ${"%.6f".format(lng)}")
            .setPositiveButton("Drive Here") { _, _ ->
                startInAppNavigation(lat, lng, title)
            }
            .setNeutralButton("Save Pin") { _, _ ->
                val stamp = DevicePrefs.getDeviceStamp(this)
                val devId = DevicePrefs.getDeviceId(this)
                val layerId = repo.addManualPin(title, subtitle, lat, lng, stamp, devId)
                DevicePrefs.addJoinedLayerId(this, layerId)
                refreshJoinedSync()
                Toast.makeText(this, "Saved \"$title\"", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // --- Tab 2: Combined Layers & Bookmarks Tab ---

    private fun showLayersAndBookmarksSheet(startOnBookmarks: Boolean = false) {
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }

        val bookmarkedIds = DevicePrefs.getBookmarks(this)
        val bookmarkedPins = pins.filter { it.id in bookmarkedIds }

        // Segmented Toggle: Layers | Bookmarks
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        val btnSegLayers = MaterialButton(this).apply {
            text = "Layers (${layers.size})"
            textSize = 13f
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }
        }
        val btnSegBookmarks = MaterialButton(this).apply {
            text = "Bookmarks (${bookmarkedPins.size})"
            textSize = 13f
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
        }
        toggleRow.addView(btnSegLayers)
        toggleRow.addView(btnSegBookmarks)
        root.addView(toggleRow)

        val contentBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(contentBox) }
        root.addView(scroll)

        fun styleSegment(showBookmarks: Boolean) {
            btnSegLayers.setBackgroundColor(Color.parseColor(if (!showBookmarks) "#2563EB" else "#E2E8F0"))
            btnSegLayers.setTextColor(if (!showBookmarks) Color.WHITE else Color.parseColor("#334155"))
            btnSegBookmarks.setBackgroundColor(Color.parseColor(if (showBookmarks) "#2563EB" else "#E2E8F0"))
            btnSegBookmarks.setTextColor(if (showBookmarks) Color.WHITE else Color.parseColor("#334155"))
        }

        fun renderLayersView() {
            styleSegment(false)
            contentBox.removeAllViews()

            val actionRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))
            }
            actionRow.addView(TextView(this).apply {
                text = "Private & Shared Layers"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#64748B"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            actionRow.addView(MaterialButton(this).apply {
                text = "+ Join Code"
                textSize = 12f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#0F766E"))
                cornerRadius = dp(10)
                setOnClickListener {
                    dialog.dismiss()
                    showJoinCodeDialog()
                }
            })
            contentBox.addView(actionRow)

            if (layers.isEmpty()) {
                contentBox.addView(TextView(this).apply {
                    text = "No private layers yet. Import a KML/KMZ file or tap + Join Code to unlock a shared layer."
                    textSize = 13f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, dp(6), 0, dp(12))
                })
                return
            }

            val myDevId = DevicePrefs.getDeviceId(this)
            layers.forEach { layer ->
                val layerPins = pins.filter { it.layerId == layer.id }
                val layerTracks = tracks.filter { it.layerId == layer.id }
                val isCreator = layer.creatorDeviceId.isBlank() || layer.creatorDeviceId.equals(myDevId, ignoreCase = true)

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, dp(6))
                }
                row.addView(CheckBox(this).apply {
                    isChecked = DevicePrefs.isLayerVisible(this@MainActivity, layer.id)
                    setOnCheckedChangeListener { _, checked ->
                        DevicePrefs.setLayerVisible(this@MainActivity, layer.id, checked)
                        renderMapData()
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
                    setOnClickListener { shareLayerLink(layer) }
                })

                row.addView(MaterialButton(this).apply {
                    text = if (isCreator) "Del" else "Leave"
                    textSize = 11f
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(8), 0, dp(8), 0)
                    setTextColor(Color.parseColor("#EF4444"))
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnClickListener {
                        val b = AlertDialog.Builder(this@MainActivity)
                            .setTitle("Remove ${layer.name}?")
                            .setNeutralButton("Leave Layer") { _, _ ->
                                DevicePrefs.removeJoinedLayerId(this@MainActivity, layer.id)
                                refreshJoinedSync()
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel", null)

                        if (isCreator) {
                            b.setMessage("Leave removes it from this phone only. Delete Cloud deletes it for everyone.")
                            b.setPositiveButton("Delete Cloud") { _, _ ->
                                DevicePrefs.removeJoinedLayerId(this@MainActivity, layer.id)
                                repo.deleteLayer(layer.id, layerPins.map { it.id }, layerTracks.map { it.id })
                                refreshJoinedSync()
                                dialog.dismiss()
                            }
                        } else {
                            b.setMessage("Removes this shared layer from your phone.")
                        }
                        b.show()
                    }
                })
                contentBox.addView(row)
            }
        }

        fun renderBookmarksView() {
            styleSegment(true)
            contentBox.removeAllViews()

            val currentBookmarked = pins.filter { it.id in DevicePrefs.getBookmarks(this) }
            if (currentBookmarked.isEmpty()) {
                contentBox.addView(TextView(this).apply {
                    text = "No bookmarked pins yet. Tap a pin on the map and press ☆ to save it here."
                    textSize = 13f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, dp(8), 0, dp(12))
                })
                return
            }

            currentBookmarked.forEach { pin ->
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
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                col.addView(TextView(this).apply {
                    text = pin.name
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                })
                if (pin.status.equals("issue", ignoreCase = true) && pin.issueReason.isNotBlank()) {
                    col.addView(TextView(this).apply {
                        text = "⚠ ${pin.issueReason}"
                        textSize = 11f
                        setTextColor(Color.parseColor("#DC2626"))
                    })
                }
                row.addView(col)
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
                contentBox.addView(row)
            }
        }

        btnSegLayers.setOnClickListener { renderLayersView() }
        btnSegBookmarks.setOnClickListener { renderBookmarksView() }

        if (startOnBookmarks) renderBookmarksView() else renderLayersView()

        dialog.setContentView(root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
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
                showLayersAndBookmarksSheet(startOnBookmarks = false)
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
            if (pin.status.equals("issue", ignoreCase = true) && pin.issueReason.isNotBlank()) {
                labelCol.addView(TextView(this).apply {
                    text = "⚠ ${pin.issueReason}"
                    textSize = 11f
                    setTextColor(Color.parseColor("#DC2626"))
                })
            }
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
        val link = "https://thamirakkurd.github.io/TerraSync/join/?code=$code"
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

    // --- Tab 3: Settings Page (Settings + Native Offline Vector Street Maps Only) ---

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
        val tvCurrentStamp = TextView(this).apply {
            text = "Current stamp: ${DevicePrefs.getDeviceStamp(this@MainActivity)}"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(2), 0, dp(6))
        }
        root.addView(tvCurrentStamp)

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
                val updated = DevicePrefs.getDeviceStamp(this@MainActivity)
                tvCurrentStamp.text = "Current stamp: $updated"
                Toast.makeText(this@MainActivity, "Updated: $updated", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(nameRow)

        // 2. Map & Navigation Preferences
        fun addSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(TextView(this).apply {
                text = title
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(MaterialSwitch(this).apply {
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

        // 3. Optional Offline Vector Street Maps (Native MapLibre OfflineManager)
        root.addView(TextView(this).apply {
            text = "Offline Street Maps (Optional)"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dp(16), 0, dp(2))
        })
        root.addView(TextView(this).apply {
            text = "Online by default. Download a regional vector street pack to use the regular map completely offline."
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 0, 0, dp(10))
        })

        val packUpdaters = mutableListOf<() -> Unit>()
        val pixelRatio = resources.displayMetrics.density

        fun addOfflineCard(
            packId: String,
            title: String,
            subtitle: String,
            onDownload: () -> Unit
        ) {
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
                text = title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
            })
            card.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, dp(2), 0, dp(6))
            })
            val tvStatus = TextView(this).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#0F766E"))
            }
            card.addView(tvStatus)

            val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                visibility = View.GONE
            }
            card.addView(progressBar)

            val btnRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(6), 0, 0)
            }
            val btnDl = MaterialButton(this).apply {
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

            fun refreshCard() {
                val st = offlineMaps.getState(packId)
                tvStatus.text = st.statusText
                progressBar.visibility = if (st.isDownloading) View.VISIBLE else View.GONE
                progressBar.progress = st.progressPercent
                btnDl.text = when {
                    st.isDownloading -> "Pause"
                    st.isDownloaded -> "Downloaded"
                    else -> "Download"
                }
                btnDl.isEnabled = !st.isDownloaded || st.isDownloading
                btnDl.alpha = if (btnDl.isEnabled) 1f else 0.55f
            }

            btnDl.setOnClickListener {
                val st = offlineMaps.getState(packId)
                if (st.isDownloading) {
                    offlineMaps.pauseDownload(packId)
                } else {
                    onDownload()
                }
            }

            btnDel.setOnClickListener {
                offlineMaps.deletePack(packId) { msg ->
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            }

            btnRow.addView(btnDl)
            btnRow.addView(btnDel)
            card.addView(btnRow)
            root.addView(card)

            refreshCard()
            packUpdaters += ::refreshCard
        }

        OfflineMapManager.REGIONAL_PACKS.forEach { pack ->
            addOfflineCard(pack.id, pack.title, pack.subtitle) {
                offlineMaps.downloadPack(pack, pixelRatio)
            }
        }

        addOfflineCard(
            OfflineMapManager.CUSTOM_VIEW_ID,
            "Current Map View (Full Street Detail)",
            "Saves every street & alley visible on your current screen (zoom 10–14)"
        ) {
            val bounds = map?.projection?.visibleRegion?.latLngBounds
            if (bounds == null) {
                Toast.makeText(this, "Move map to desired city first", Toast.LENGTH_SHORT).show()
            } else {
                offlineMaps.downloadCustomBounds(bounds, pixelRatio)
            }
        }

        root.addView(MaterialButton(this).apply {
            text = "Clear Temporary Map Cache"
            textSize = 12f
            setTextColor(Color.parseColor("#334155"))
            setBackgroundColor(Color.parseColor("#E2E8F0"))
            cornerRadius = dp(10)
            setOnClickListener {
                offlineMaps.clearAmbientCache { msg ->
                    runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
                }
            }
        })

        offlineMaps.setStateListener {
            runOnUiThread { packUpdaters.forEach { it() } }
        }
        offlineMaps.refreshAll()

        dialog.setOnDismissListener {
            offlineMaps.setStateListener(null)
        }

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.show()
    }

    // --- Tab 4: Dedicated About Page ---

    private fun showAboutSheet() {
        val dialog = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(28))
        }

        root.addView(TextView(this).apply {
            text = "TerraSync"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Version 2.2.1  •  Native Kotlin + MapLibre GL + Firestore"
            textSize = 13f
            setTextColor(Color.parseColor("#0F766E"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(12))
        })

        root.addView(TextView(this).apply {
            text = "Field mapping, KML/KMZ layer sharing, live team pin collaboration, issue tracking, offline regional vector maps, and turn-by-turn driving navigation."
            textSize = 14f
            setPadding(0, 0, 0, dp(14))
        })

        val devCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1464748B"))
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(14)
            }
        }
        devCard.addView(TextView(this).apply {
            text = "Developer"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        })
        devCard.addView(TextView(this).apply {
            text = "Thamir (@Thamirakkurd)"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(4))
        })
        devCard.addView(TextView(this).apply {
            text = "Device Stamp: ${DevicePrefs.getDeviceStamp(this@MainActivity)}"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
        })
        root.addView(devCard)

        listOf(
            Triple("GitHub Profile (@Thamirakkurd)", "https://github.com/Thamirakkurd", "#1E293B"),
            Triple("TerraSync Repository", "https://github.com/Thamirakkurd/TerraSync", "#2563EB"),
            Triple("Check Releases / Updates", "https://github.com/Thamirakkurd/TerraSync/releases", "#0F766E")
        ).forEach { (label, url, colorHex) ->
            root.addView(MaterialButton(this).apply {
                text = label
                textSize = 13f
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor(colorHex))
                cornerRadius = dp(12)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply {
                    bottomMargin = dp(8)
                }
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            })
        }

        scroll.addView(root)
        dialog.setContentView(scroll)
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
                val devId = DevicePrefs.getDeviceId(this)
                val layerId = repo.addManualPin(
                    etName.text.toString().trim(),
                    etDesc.text.toString().trim(),
                    latLng.latitude,
                    latLng.longitude,
                    stamp,
                    devId
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

    private fun stylePill(v: View, hex: String, radiusDp: Int) {
        v.background = GradientDrawable().apply {
            setColor(Color.parseColor(hex))
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun styleRipplePill(v: View, hex: String, radiusDp: Int) {
        val bg = GradientDrawable().apply {
            setColor(Color.parseColor(hex))
            cornerRadius = dp(radiusDp).toFloat()
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(radiusDp).toFloat()
        }
        v.background = RippleDrawable(ColorStateList.valueOf(Color.parseColor("#55FFFFFF")), bg, mask)
    }

    private fun formatDistance(meters: Double): String {
        return if (meters < 1000) "${meters.toInt()} m" else "${"%.1f".format(meters / 1000.0)} km"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
        if (hasLocationPermission()) startLiveGpsUpdates()
    }
    override fun onPause() {
        if (activeNavRoute == null) stopLiveGpsUpdates()
        mapView.onPause()
        super.onPause()
    }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        stopLiveGpsUpdates()
        offlineMaps.setStateListener(null)
        tts?.shutdown()
        repo.stopListening()
        mapView.onDestroy()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
