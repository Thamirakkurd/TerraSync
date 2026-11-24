package com.terrasync.app

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.HillshadeLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    companion object {
        private val ALL_STATUSES = setOf("pending", "done", "issue")
        private const val HILLSHADE_SOURCE = "terrasync-dem-source"
        private const val HILLSHADE_LAYER = "terrasync-hillshade-layer"
        private const val TRACKS_SOURCE = "terrasync-tracks-source"
        private const val TRACKS_LAYER = "terrasync-tracks-layer"
        private const val ROUTE_SOURCE = "terrasync-route-source"
        private const val ROUTE_LAYER = "terrasync-route-layer"
        private const val ROUTE_CASING_LAYER = "terrasync-route-casing-layer"
        private const val ROUTE_WALK_CASING_LAYER = "terrasync-route-walk-casing-layer"
        private const val ROUTE_WALK_LAYER = "terrasync-route-walk-layer"
        private const val ROUTE_PILL_SOURCE = "terrasync-route-pill-source"
        private const val ROUTE_PILL_LAYER = "terrasync-route-pill-layer"
        private const val ROUTE_PILL_ICON = "route-time-pill"
        private const val RULER_LINE_SOURCE = "terrasync-ruler-line-source"
        private const val RULER_LINE_LAYER = "terrasync-ruler-line-layer"
        private const val RULER_POINTS_SOURCE = "terrasync-ruler-points-source"
        private const val RULER_POINTS_CIRCLE_LAYER = "terrasync-ruler-points-circle-layer"
        private const val RULER_POINTS_NUM_LAYER = "terrasync-ruler-points-num-layer"
        private const val PINS_SOURCE = "terrasync-pins-source"
        private const val PINS_LAYER = "terrasync-pins-layer"
        private const val PINS_LABELS_LAYER = "terrasync-pins-labels-layer"
        private const val SELECTED_SOURCE = "terrasync-selected-source"
        private const val SELECTED_HALO_LAYER = "terrasync-selected-halo-layer"
        private const val SELECTED_PIN_LAYER = "terrasync-selected-pin-layer"
        private const val PULSE_SOURCE = "terrasync-pulse-source"
        private const val PULSE_LAYER = "terrasync-pulse-layer"
        private const val USER_LOC_SOURCE = "terrasync-userloc-source"
        private const val USER_LOC_LAYER = "terrasync-userloc-layer"

        private val ISSUE_PRESETS = listOf(
            "Customer Refused / No Access",
            "Need Electricity",
            "Wrong Location / Coordinates",
            "Closed / Nobody Home",
            "Safety Hazard / Network Fault"
        )

        private val DONE_PRESETS = listOf(
            "Meter Linked",
            "Inaccessible",
            "No Bill"
        )

        private const val STYLE_SATELLITE = """
        {
          "version": 8,
          "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
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

    private data class SharedPinTarget(val lat: Double, val lng: Double, val name: String, val pinId: String)

    private lateinit var mapView: MapView
    private lateinit var topCluster: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var routeFromBanner: LinearLayout
    private lateinit var tvRouteFromText: TextView
    private lateinit var btnClearRouteFrom: ImageButton
    private lateinit var btnSyncStatus: ImageButton
    private lateinit var btnStyleToggle: ImageButton
    private lateinit var btnStatusFilter: ImageButton
    private lateinit var btnSentToggle: TextView
    private lateinit var btnLasso: ImageButton
    private lateinit var btnRuler: ImageButton
    private lateinit var lassoOverlay: LassoOverlayView
    private lateinit var lassoCard: LinearLayout
    private lateinit var tvLassoTitle: TextView
    private lateinit var tvLassoSubtitle: TextView
    private lateinit var btnLassoMarkSent: TextView
    private lateinit var btnLassoRedraw: TextView
    private lateinit var btnLassoUndo: TextView
    private lateinit var btnLassoReset: TextView
    private lateinit var btnLassoModeToggle: TextView
    private lateinit var btnLassoClose: ImageButton
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var btnLocate: ImageButton
    private lateinit var btnImport: ImageButton
    private lateinit var btnTabSearch: ImageButton
    private lateinit var btnTabBookmarks: ImageButton
    private lateinit var btnTabSettings: ImageButton
    private lateinit var btnTabAbout: ImageButton
    private lateinit var bottomDock: LinearLayout
    private lateinit var mapControlsRow: LinearLayout
    private lateinit var bottomIconRow: LinearLayout
    private lateinit var speedBadge: LinearLayout
    private lateinit var tvSpeedValue: TextView
    private lateinit var tvSpeedUnit: TextView
    private lateinit var rulerCard: LinearLayout
    private lateinit var btnRulerUndo: ImageButton
    private lateinit var tvRulerDistance: TextView
    private lateinit var tvRulerSubtitle: TextView
    private lateinit var btnRulerReset: TextView
    private lateinit var btnRulerClose: ImageButton
    private lateinit var navBanner: LinearLayout
    private lateinit var tvNavArrow: TextView
    private lateinit var tvNavInstruction: TextView
    private lateinit var tvNavMeta: TextView
    private lateinit var btnNavVoice: ImageButton
    private lateinit var tvScaleLabel: TextView
    private lateinit var scaleBarLine: View

    private lateinit var inScreenSheet: LinearLayout
    private lateinit var sheetDragHandle: View
    private lateinit var sheetContent: LinearLayout
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var pageOverlayContainer: FrameLayout

    private var map: MapLibreMap? = null
    private var isActivityStarted = false
    private var isActivityResumed = false
    private lateinit var offlineMaps: OfflineMapManager
    private lateinit var repo: SyncRepo

    private var layers: List<LayerItem> = emptyList()
    private var pins: List<PinItem> = emptyList()
    private var tracks: List<TrackItem> = emptyList()
    private var cloudLayers: List<LayerItem> = emptyList()
    private var cloudPins: List<PinItem> = emptyList()
    private var cloudTracks: List<TrackItem> = emptyList()
    private var visibleStatuses: MutableSet<String> = mutableSetOf("pending", "done", "issue")
    private var isLiveConnected = false
    private var mapStyleMode: MapStyleMode = MapStyleMode.VECTOR
    private var travelMode: TravelMode = TravelMode.CAR
    private var isFollowingGps = false
    private var hasAutoZoomedInitial = false
    private var statusBarInsetTop = 0
    private var navBarInsetBottom = 0
    private var imeInsetBottom = 0
    private var activePageCleanup: (() -> Unit)? = null
    private var webSearchSeq = 0
    private var pendingSharedPin: SharedPinTarget? = null
    private var activeSharedPinId: String? = null
    private var activeTappedLatLng: LatLng? = null
    private var tappedTitleView: TextView? = null
    private var tappedSubView: TextView? = null
    private var tappedResolvedTitle: String = ""
    private var tappedResolvedSub: String = ""
    private var lastGpsFixTimeMs: Long = 0L

    // Local-only Sent Tracking & Circle-Select State
    private var hideSentPins = false
    private var isLassoMode = false
    private var lassoMoveMapMode = false
    private var lassoPendingPinIds: List<String> = emptyList()
    private val sentUndoStack = mutableListOf<List<String>>()

    // Ruler state
    private var isRulerMode = false
    private val rulerPoints = mutableListOf<LatLng>()

    // Selected pin or tapped place marker
    private var selectedPinId: String? = null
    private var selectedMarkerLatLng: LatLng? = null
    private var selectedMarkerIcon: String = "pin-target"
    private var pulseAnimator: ValueAnimator? = null

    // Custom Route From Origin
    private var routeOriginLatLng: LatLng? = null
    private var routeOriginName: String = ""

    // Interpolated GPS arrow state
    private var currentLocation: Location? = null
    private var displayedLat: Double = 0.0
    private var displayedLng: Double = 0.0
    private var displayedBearing: Float = 0f
    private var gpsAnim: ValueAnimator? = null
    private var gpsTrackingActive = false
    private var lastLiveLocationFixMs: Long = 0L

    // Route Preview & Active Turn-by-Turn Navigation State
    private var previewRoute: NavRoute? = null
    private var routeRequestSeq: Int = 0
    private var lastRoutePillKey: String? = null
    private var activeNavRoute: NavRoute? = null
    private var activeNavTrimmedGeom: List<Pair<Double, Double>>? = null
    private var navLastSegIdx: Int = 0
    private var isRerouting: Boolean = false
    private var navDestLatLng: LatLng? = null
    private var navDestName: String = ""
    private var navCurrentStepIdx: Int = 0
    private var lastSpokenStepIdx: Int = -1
    private var lastRerouteTime: Long = 0L
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Full-screen overlay stack
    private val pageBackStack = mutableListOf<() -> Unit>()
    private var backCallback: OnBackPressedCallback? = null
    private var liveBookmarksRefresh: (() -> Unit)? = null
    private var livePinListRefresh: (() -> Unit)? = null

    private val gpsListener = LocationListener { loc -> onNewLocation(loc) }

    private val kmlPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleImportUri(uri)
    }

    private val locPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            if (!ensureLocationServiceOn()) return@registerForActivityResult
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
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val installedVer = getInstalledVersionName()
        val appUserAgent = "TerraSync/$installedVer (https://github.com/Thamirakkurd/TerraSync)"
        RoutingEngine.userAgent = appUserAgent

        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(RoutingEngine.sharedOkHttpClient)
        offlineMaps = OfflineMapManager(this).apply { refreshAll() }
        repo = SyncRepo(applicationContext)
        RoutingEngine.appContext = applicationContext
        setContentView(R.layout.activity_main)

        visibleStatuses = DevicePrefs.getVisibleStatuses(this).toMutableSet()
        hideSentPins = DevicePrefs.isHideSentPins(this)
        mapStyleMode = DevicePrefs.getMapStyleMode(this)
        travelMode = DevicePrefs.getTravelMode(this)

        bindViews()
        applyMapChromeTheme()
        applyKeepAwakeFlag()

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }

        val root = findViewById<ViewGroup>(R.id.rootContainer)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            statusBarInsetTop = sys.top
            navBarInsetBottom = sys.bottom
            imeInsetBottom = ime.bottom
            (topCluster.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(10)
            (navBanner.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(10)
            (bottomDock.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = sys.bottom + dp(10)
            (inScreenSheet.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(12)
            sheetContent.setPadding(dp(16), 0, dp(16), sys.bottom + dp(20))
            if (pageOverlayContainer.childCount > 0) {
                pageOverlayContainer.getChildAt(0).setPadding(0, statusBarInsetTop, 0, maxOf(navBarInsetBottom, imeInsetBottom))
            }
            topCluster.requestLayout()
            navBanner.requestLayout()
            bottomDock.requestLayout()
            inScreenSheet.requestLayout()
            map?.uiSettings?.setCompassMargins(0, statusBarInsetTop + dp(12), dp(14), 0)
            insets
        }

        sheetBehavior = BottomSheetBehavior.from(inScreenSheet).apply {
            state = BottomSheetBehavior.STATE_HIDDEN
            isHideable = true
            skipCollapsed = false
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        clearSelectedMarker()
                        if (previewRoute != null && activeNavRoute == null) {
                            routeRequestSeq++
                            previewRoute = null
                            renderMapData()
                        }
                        if (activeNavRoute == null) {
                            resetMapPadding()
                        }
                        UiAnimations.fadeShow(bottomDock, 180L)
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) {}
            })
        }

        setupClickListeners()
        setupBackNavigation()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.setCompassFadeFacingNorth(true)
            m.uiSettings.setCompassMargins(0, statusBarInsetTop + dp(12), dp(14), 0)
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(36.8669, 42.9900))
                .zoom(11.0)
                .build()

            loadMapStyle()
            updateScaleBar()

            m.addOnCameraMoveListener {
                updateScaleBar()
                if (isLassoMode && lassoPendingPinIds.isNotEmpty()) {
                    clearLassoSelection()
                }
            }

            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && isFollowingGps) {
                    isFollowingGps = false
                    updateLocationIconTint()
                }
            }

            m.addOnMapClickListener { latLng ->
                if (activeNavRoute != null) return@addOnMapClickListener true
                if (isRulerMode) {
                    addRulerPoint(latLng)
                } else if (!isLassoMode) {
                    handleMapTap(m, latLng)
                }
                true
            }

            m.addOnMapLongClickListener { latLng ->
                if (activeNavRoute == null && !isRulerMode && !isLassoMode) showAddPinDialog(latLng)
                true
            }

            pendingSharedPin?.let { target ->
                pendingSharedPin = null
                openSharedPinTarget(target)
            }
        }

        refreshJoinedSync()
        handleDeepLinkIntent(intent)
    }

    private fun bindViews() {
        mapView = findViewById(R.id.mapView)
        topCluster = findViewById(R.id.topCluster)
        topBar = findViewById(R.id.topBar)
        routeFromBanner = findViewById(R.id.routeFromBanner)
        tvRouteFromText = findViewById(R.id.tvRouteFromText)
        btnClearRouteFrom = findViewById(R.id.btnClearRouteFrom)
        btnSyncStatus = findViewById(R.id.btnSyncStatus)
        btnStyleToggle = findViewById(R.id.btnStyleToggle)
        btnStatusFilter = findViewById(R.id.btnStatusFilter)
        btnSentToggle = findViewById(R.id.btnSentToggle)
        btnLasso = findViewById(R.id.btnLasso)
        btnRuler = findViewById(R.id.btnRuler)
        lassoOverlay = findViewById(R.id.lassoOverlay)
        lassoCard = findViewById(R.id.lassoCard)
        tvLassoTitle = findViewById(R.id.tvLassoTitle)
        tvLassoSubtitle = findViewById(R.id.tvLassoSubtitle)
        btnLassoMarkSent = findViewById(R.id.btnLassoMarkSent)
        btnLassoRedraw = findViewById(R.id.btnLassoRedraw)
        btnLassoUndo = findViewById(R.id.btnLassoUndo)
        btnLassoReset = findViewById(R.id.btnLassoReset)
        btnLassoModeToggle = findViewById(R.id.btnLassoModeToggle)
        btnLassoClose = findViewById(R.id.btnLassoClose)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        btnLocate = findViewById(R.id.btnLocate)
        btnImport = findViewById(R.id.btnImport)
        applyExtraMapToolsVisibility()
        applyResponsiveChromeSizing()
        btnTabSearch = findViewById(R.id.btnTabSearch)
        btnTabBookmarks = findViewById(R.id.btnTabBookmarks)
        btnTabSettings = findViewById(R.id.btnTabSettings)
        btnTabAbout = findViewById(R.id.btnTabAbout)
        bottomDock = findViewById(R.id.bottomDock)
        mapControlsRow = findViewById(R.id.mapControlsRow)
        bottomIconRow = findViewById(R.id.bottomIconRow)
        speedBadge = findViewById(R.id.speedBadge)
        tvSpeedValue = findViewById(R.id.tvSpeedValue)
        tvSpeedUnit = findViewById(R.id.tvSpeedUnit)
        rulerCard = findViewById(R.id.rulerCard)
        btnRulerUndo = findViewById(R.id.btnRulerUndo)
        tvRulerDistance = findViewById(R.id.tvRulerDistance)
        tvRulerSubtitle = findViewById(R.id.tvRulerSubtitle)
        btnRulerReset = findViewById(R.id.btnRulerReset)
        btnRulerClose = findViewById(R.id.btnRulerClose)
        navBanner = findViewById(R.id.navBanner)
        tvNavArrow = findViewById(R.id.tvNavArrow)
        tvNavInstruction = findViewById(R.id.tvNavInstruction)
        tvNavMeta = findViewById(R.id.tvNavMeta)
        btnNavVoice = findViewById(R.id.btnNavVoice)
        tvScaleLabel = findViewById(R.id.tvScaleLabel)
        scaleBarLine = findViewById(R.id.scaleBarLine)
        inScreenSheet = findViewById(R.id.inScreenSheet)
        sheetDragHandle = findViewById(R.id.sheetDragHandle)
        sheetContent = findViewById(R.id.sheetContent)
        pageOverlayContainer = findViewById(R.id.pageOverlayContainer)
    }

    private fun isDarkMapUi(): Boolean {
        return when (DevicePrefs.getThemeMode(this)) {
            "night" -> true
            "auto" -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            else -> false
        }
    }

    private fun applyMapChromeTheme() {
        val night = isDarkMapUi()
        val darkMapBg = night || mapStyleMode == MapStyleMode.SATELLITE
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkMapBg
            isAppearanceLightNavigationBars = !darkMapBg
        }
        val tileBg = Color.parseColor(if (night) "#1E293B" else "#FFFFFF")
        val tileStroke = Color.parseColor(if (night) "#334155" else "#E2E8F0")
        val iconTint = Color.parseColor(if (night) "#F1F5F9" else "#334155")
        val sheetBg = Color.parseColor(if (night) "#1E293B" else "#FFFFFF")
        val handleColor = Color.parseColor(if (night) "#475569" else "#CBD5E1")
        val scaleColor = Color.parseColor(if (darkMapBg) "#FFFFFF" else "#1E293B")

        UiAnimations.styleFloatingTile(this, btnStyleToggle, tileBg, 21f, 6f, strokeColor = tileStroke)
        UiAnimations.styleFloatingTile(this, btnStatusFilter, tileBg, 21f, 6f, strokeColor = tileStroke)
        UiAnimations.styleFloatingTile(this, btnSyncStatus, tileBg, 21f, 6f, strokeColor = tileStroke)
        UiAnimations.styleFloatingTile(
            this,
            btnLasso,
            if (isLassoMode) Color.parseColor("#16A34A") else tileBg,
            22f,
            6f,
            if (isLassoMode) "#44FFFFFF" else "#2616A34A",
            if (isLassoMode) Color.TRANSPARENT else tileStroke
        )
        UiAnimations.styleFloatingTile(
            this,
            btnRuler,
            if (isRulerMode) Color.parseColor("#2563EB") else tileBg,
            22f,
            6f,
            if (isRulerMode) "#44FFFFFF" else "#262563EB",
            if (isRulerMode) Color.TRANSPARENT else tileStroke
        )
        UiAnimations.styleFloatingTile(this, btnZoomIn, tileBg, 22f, 6f, strokeColor = tileStroke)
        UiAnimations.styleFloatingTile(this, btnZoomOut, tileBg, 22f, 6f, strokeColor = tileStroke)
        UiAnimations.styleFloatingTile(this, btnImport, Color.parseColor("#0F766E"), 22f, 6f, "#44FFFFFF", Color.TRANSPARENT, 0f)
        UiAnimations.styleFloatingTile(this, btnLocate, tileBg, 24f, 7f, strokeColor = tileStroke)

        bottomIconRow.background = null
        bottomIconRow.elevation = 0f
        listOf(btnTabSearch, btnTabBookmarks, btnTabSettings, btnTabAbout).forEach { btn ->
            UiAnimations.styleFloatingTile(this, btn, tileBg, 16f, 7f, strokeColor = tileStroke)
            btn.setColorFilter(iconTint)
        }

        btnStyleToggle.setColorFilter(
            Color.parseColor(
                when (mapStyleMode) {
                    MapStyleMode.SATELLITE -> "#0EA5E9"
                    MapStyleMode.VECTOR -> "#10B981"
                }
            )
        )
        btnLasso.setColorFilter(if (isLassoMode) Color.WHITE else iconTint)
        btnRuler.setColorFilter(if (isRulerMode) Color.WHITE else iconTint)
        updateSentToggleUi()
        btnZoomIn.setColorFilter(iconTint)
        btnZoomOut.setColorFilter(iconTint)
        btnImport.setColorFilter(Color.WHITE)
        updateFilterIconTint()
        updateLocationIconTint()
        btnSyncStatus.setColorFilter(Color.parseColor(if (isLiveConnected) "#22C55E" else "#F59E0B"))

        tvScaleLabel.setTextColor(scaleColor)
        tvScaleLabel.setShadowLayer(4f, 0f, 0f, Color.parseColor(if (darkMapBg) "#CC0F172A" else "#E6FFFFFF"))
        scaleBarLine.setBackgroundColor(scaleColor)

        speedBadge.background = GradientDrawable().apply {
            setColor(tileBg)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), Color.parseColor(if (night) "#334155" else "#E2E8F0"))
        }
        tvSpeedValue.setTextColor(Color.parseColor(if (night) "#F8FAFC" else "#0F172A"))
        tvSpeedUnit.setTextColor(Color.parseColor(if (night) "#94A3B8" else "#64748B"))

        routeFromBanner.background = GradientDrawable().apply {
            setColor(Color.parseColor(if (night) "#1E293B" else "#FFFFFF"))
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Color.parseColor("#2563EB"))
        }
        tvRouteFromText.setTextColor(Color.parseColor(if (night) "#F8FAFC" else "#0F172A"))
        btnClearRouteFrom.setColorFilter(Color.parseColor(if (night) "#94A3B8" else "#64748B"))

        rulerCard.background = GradientDrawable().apply {
            setColor(sheetBg)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), Color.parseColor(if (night) "#334155" else "#E2E8F0"))
        }
        tvRulerDistance.setTextColor(Color.parseColor(if (night) "#F8FAFC" else "#0F172A"))
        tvRulerSubtitle.setTextColor(Color.parseColor(if (night) "#94A3B8" else "#64748B"))
        btnRulerUndo.setColorFilter(iconTint)
        btnRulerClose.setColorFilter(iconTint)

        lassoCard.background = GradientDrawable().apply {
            setColor(sheetBg)
            cornerRadius = dp(18).toFloat()
            setStroke(dp(1), tileStroke)
        }
        tvLassoTitle.setTextColor(Color.parseColor(if (night) "#F8FAFC" else "#0F172A"))
        tvLassoSubtitle.setTextColor(Color.parseColor(if (night) "#94A3B8" else "#64748B"))
        btnLassoClose.setColorFilter(iconTint)
        btnLassoMarkSent.background = GradientDrawable().apply {
            setColor(Color.parseColor("#16A34A"))
            cornerRadius = dp(10).toFloat()
        }
        val lassoSecBg = Color.parseColor(if (night) "#334155" else "#F1F5F9")
        val lassoSecTxt = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        btnLassoRedraw.setTextColor(lassoSecTxt)
        btnLassoRedraw.background = GradientDrawable().apply {
            setColor(lassoSecBg)
            cornerRadius = dp(10).toFloat()
        }
        btnLassoUndo.setTextColor(lassoSecTxt)
        btnLassoUndo.background = GradientDrawable().apply {
            setColor(lassoSecBg)
            cornerRadius = dp(10).toFloat()
        }
        btnLassoReset.setTextColor(Color.parseColor(if (night) "#FCA5A5" else "#DC2626"))
        btnLassoReset.background = GradientDrawable().apply {
            setColor(Color.parseColor(if (night) "#3B1D24" else "#FEE2E2"))
            cornerRadius = dp(10).toFloat()
        }

        inScreenSheet.background = GradientDrawable().apply {
            setColor(sheetBg)
            val r = dp(22).toFloat()
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
        sheetDragHandle.background = GradientDrawable().apply {
            setColor(handleColor)
            cornerRadius = dp(4).toFloat()
        }
        navBanner.background = GradientDrawable().apply {
            setColor(Color.parseColor("#F20F172A"))
            cornerRadius = dp(18).toFloat()
        }
    }

    private fun setupClickListeners() {
        btnSyncStatus.setOnClickListener {
            val state = if (isLiveConnected) "Cloud Synced" else "Offline Cache"
            Toast.makeText(this, "$state • ${pins.size} bookmarks", Toast.LENGTH_SHORT).show()
        }

        btnStyleToggle.setOnClickListener { anchor ->
            val menu = PopupMenu(this, anchor)
            MapStyleMode.entries.forEachIndexed { idx, mode ->
                val prefix = if (mode == mapStyleMode) "✓ " else ""
                menu.menu.add(0, idx, idx, "$prefix${mode.label}")
            }
            menu.setOnMenuItemClickListener { item ->
                val chosen = MapStyleMode.entries[item.itemId]
                mapStyleMode = chosen
                DevicePrefs.setMapStyleMode(this, chosen)
                applyMapChromeTheme()
                loadMapStyle()
                Toast.makeText(this, chosen.label, Toast.LENGTH_SHORT).show()
                true
            }
            menu.show()
        }

        btnStatusFilter.setOnClickListener { showMultiStatusFilterDialog() }
        btnSentToggle.setOnClickListener {
            hideSentPins = !hideSentPins
            DevicePrefs.setHideSentPins(this, hideSentPins)
            updateSentToggleUi()
            renderMapData()
            val sentCount = activeLocalSentCount()
            val msg = if (hideSentPins) {
                if (sentCount > 0) "Showing unsent pins only ($sentCount sent hidden)" else "Unsent Only mode (no pins marked sent yet)"
            } else {
                "Showing all pins"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        btnLasso.setOnClickListener { toggleLassoMode(!isLassoMode) }
        btnLassoModeToggle.setOnClickListener { toggleLassoMoveMapMode() }
        btnLassoMarkSent.setOnClickListener { confirmLassoMarkSent() }
        btnLassoRedraw.setOnClickListener { clearLassoSelection() }
        btnLassoUndo.setOnClickListener { undoLastLassoMark() }
        btnLassoReset.setOnClickListener { confirmResetAllSentPins() }
        btnLassoClose.setOnClickListener { toggleLassoMode(false) }
        lassoOverlay.onLoopStarted = {
            lassoPendingPinIds = emptyList()
            updateLassoUi()
        }
        lassoOverlay.onLoopCompleted = { poly -> handleLassoLoopCompleted(poly) }
        btnRuler.setOnClickListener { toggleRulerMode(!isRulerMode) }
        btnRulerUndo.setOnClickListener { undoRulerPoint() }
        btnRulerReset.setOnClickListener { resetRulerPoints() }
        btnRulerClose.setOnClickListener { toggleRulerMode(false) }
        btnClearRouteFrom.setOnClickListener { clearRouteOrigin() }

        btnZoomIn.setOnClickListener {
            map?.let { m -> m.animateCamera(CameraUpdateFactory.zoomTo((m.cameraPosition.zoom + 1.0).coerceAtMost(20.0)), 250) }
        }
        btnZoomOut.setOnClickListener {
            map?.let { m -> m.animateCamera(CameraUpdateFactory.zoomTo((m.cameraPosition.zoom - 1.0).coerceAtLeast(3.0)), 250) }
        }

        btnImport.setOnClickListener { kmlPicker.launch(arrayOf("*/*")) }

        btnLocate.setOnClickListener {
            if (!hasLocationPermission()) {
                locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return@setOnClickListener
            }
            if (!ensureLocationServiceOn()) return@setOnClickListener
            val staleStream = System.currentTimeMillis() - lastLiveLocationFixMs > 10_000L
            startLiveGpsUpdates(forceRestart = staleStream)
            isFollowingGps = true
            updateLocationIconTint()
            val loc = currentLocation
            if (loc != null) {
                if (activeNavRoute != null) {
                    moveCameraForNavigation(loc, 550)
                } else {
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 16.5))
                }
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

        findViewById<ImageButton>(R.id.btnNavStop).setOnClickListener { stopNavigation() }

        btnTabSearch.setOnClickListener { openFullPage(::buildSearchPage) }
        btnTabBookmarks.setOnClickListener { openFullPage(::buildBookmarksAndTracksPage) }
        btnTabSettings.setOnClickListener { openFullPage(::buildSettingsPage) }
        btnTabAbout.setOnClickListener { openFullPage(::buildAboutPage) }
    }

    private fun showMultiStatusFilterDialog() {
        val txtPrimary = Color.parseColor("#0F172A")
        val dividerColor = Color.parseColor("#E2E8F0")

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(6))
        }

        var updatingUi = false
        lateinit var cbAll: CheckBox
        val statusChecks = mutableMapOf<String, CheckBox>()

        fun syncCheckboxesAndMap() {
            updatingUi = true
            cbAll.isChecked = visibleStatuses.containsAll(ALL_STATUSES)
            statusChecks.forEach { (key, cb) -> cb.isChecked = visibleStatuses.contains(key) }
            updatingUi = false
            DevicePrefs.setVisibleStatuses(this, visibleStatuses)
            updateFilterIconTint()
            renderMapData()
        }

        cbAll = CheckBox(this).apply {
            text = "All Pins"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(txtPrimary)
            isChecked = visibleStatuses.containsAll(ALL_STATUSES)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnCheckedChangeListener { _, checked ->
                if (updatingUi) return@setOnCheckedChangeListener
                visibleStatuses.clear()
                if (checked) visibleStatuses.addAll(ALL_STATUSES)
                syncCheckboxesAndMap()
            }
        }
        box.addView(cbAll)

        box.addView(View(this).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(4)
                bottomMargin = dp(6)
            }
        })

        listOf(
            Triple("pending", "Pending", "#F59E0B"),
            Triple("done", "Done", "#22C55E"),
            Triple("issue", "Issue", "#EF4444")
        ).forEach { (key, label, hex) ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
            }
            row.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                }
                layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(10) }
            })
            val cb = CheckBox(this).apply {
                text = label
                textSize = 14.5f
                setTextColor(txtPrimary)
                isChecked = visibleStatuses.contains(key)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnCheckedChangeListener { _, checked ->
                    if (updatingUi) return@setOnCheckedChangeListener
                    if (checked) visibleStatuses.add(key) else visibleStatuses.remove(key)
                    syncCheckboxesAndMap()
                }
            }
            statusChecks[key] = cb
            row.addView(cb)
            box.addView(row)
        }

        box.addView(View(this).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(6)
            }
        })
        val sentCount = activeLocalSentCount()
        box.addView(CheckBox(this).apply {
            text = "Unsent Only (hide $sentCount sent on this phone)"
            textSize = 14f
            setTextColor(txtPrimary)
            isChecked = hideSentPins
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setOnCheckedChangeListener { _, checked ->
                hideSentPins = checked
                DevicePrefs.setHideSentPins(this@MainActivity, checked)
                updateSentToggleUi()
                renderMapData()
            }
        })

        AlertDialog.Builder(this)
            .setTitle("Filter Pins by Status")
            .setView(box)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun setupBackNavigation() {
        val cb = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (pageOverlayContainer.visibility == View.VISIBLE) {
                    if (pageBackStack.isNotEmpty()) {
                        val back = pageBackStack.removeAt(pageBackStack.lastIndex)
                        back()
                    } else {
                        closeFullPage()
                    }
                    return
                }
                if (activeNavRoute != null) {
                    stopNavigation()
                    return
                }
                if (previewRoute != null) {
                    routeRequestSeq++
                    previewRoute = null
                    renderMapData()
                    hideInScreenSheet()
                    return
                }
                if (routeOriginLatLng != null) {
                    clearRouteOrigin()
                    return
                }
                if (isLassoMode) {
                    toggleLassoMode(false)
                    return
                }
                if (isRulerMode) {
                    toggleRulerMode(false)
                    return
                }
                if (sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    hideInScreenSheet()
                    return
                }
                moveTaskToBack(true)
            }
        }
        backCallback = cb
        onBackPressedDispatcher.addCallback(this, cb)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        val data = intent?.data ?: return
        intent.data = null

        val scheme = data.scheme?.lowercase() ?: ""
        val host = data.host?.lowercase() ?: ""
        val path = data.path?.lowercase() ?: ""

        if (scheme == "content" || scheme == "file") {
            handleImportUri(data)
            return
        }

        if ((scheme == "terrasync" && host == "pin") ||
            (scheme == "https" && host == "thamirakkurd.github.io" && path.startsWith("/terrasync/pin"))
        ) {
            val lat = data.getQueryParameter("lat")?.toDoubleOrNull()
            val lng = data.getQueryParameter("lng")?.toDoubleOrNull()
            val name = data.getQueryParameter("name")?.trim().orEmpty().ifBlank { "Shared Pin" }
            val pinId = data.getQueryParameter("id")?.trim().orEmpty()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                val target = SharedPinTarget(lat, lng, name, pinId)
                if (map != null) openSharedPinTarget(target) else pendingSharedPin = target
            }
            return
        }

        val code = data.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            joinShareCode(code)
        }
    }

    private fun ensurePinVisibleOnMap(pin: PinItem) {
        var changed = false
        if (!DevicePrefs.isLayerVisible(this, pin.layerId)) {
            DevicePrefs.setLayerVisible(this, pin.layerId, true)
            changed = true
        }
        val normStatus = pin.status.lowercase().ifBlank { "pending" }
        if (normStatus !in visibleStatuses) {
            visibleStatuses.add(normStatus)
            DevicePrefs.setVisibleStatuses(this, visibleStatuses)
            updateFilterIconTint()
            changed = true
            Toast.makeText(this, "Enabled ${statusLabel(normStatus)} filter to show pin", Toast.LENGTH_SHORT).show()
        }
        if (hideSentPins && DevicePrefs.isPinLocalSent(this, pin.id)) {
            hideSentPins = false
            DevicePrefs.setHideSentPins(this, false)
            updateSentToggleUi()
            changed = true
            Toast.makeText(this, "Showing all pins to display sent pin", Toast.LENGTH_SHORT).show()
        }
        if (changed) renderMapData()
    }

    private fun openSharedPinTarget(target: SharedPinTarget) {
        val matched = if (target.pinId.isNotBlank()) pins.find { it.id == target.pinId } else null
        if (matched != null) {
            activeSharedPinId = null
            ensurePinVisibleOnMap(matched)
            selectPinAndOpenCard(matched, zoomIfFar = true)
            return
        }
        activeSharedPinId = target.pinId.ifBlank { null }
        selectedPinId = null
        selectedMarkerLatLng = LatLng(target.lat, target.lng)
        selectedMarkerIcon = "pin-target"
        renderMapData()
        centerUpperHalf(LatLng(target.lat, target.lng), (map?.cameraPosition?.zoom ?: 15.0).coerceAtLeast(16.0))
        showTappedPlaceCard(target.name, "Shared via TerraSync", target.lat, target.lng)
    }

    // --- Map Style, Optional 3D Buildings, Hillshading & Overlay Layers ---

    private fun loadMapStyle() {
        val m = map ?: return
        pulseAnimator?.cancel()
        pulseAnimator = null
        val builder = when (mapStyleMode) {
            MapStyleMode.SATELLITE -> Style.Builder().fromJson(STYLE_SATELLITE)
            MapStyleMode.VECTOR -> Style.Builder().fromUri(
                if (isDarkMapUi()) OfflineMapManager.STYLE_DARK_URL else OfflineMapManager.STYLE_STREET_URL
            )
        }
        m.setStyle(builder) { style ->
            lastRoutePillKey = null
            apply3dBuildingsToggle(style)
            registerMapBitmaps(style)
            enhanceVectorStyleForField(style)
            attachTerrainHillshade(style)
            attachOverlayLayers(style)
            renderMapData()
        }
    }

    private fun apply3dBuildingsToggle(style: Style) {
        val show3d = DevicePrefs.is3dBuildingsEnabled(this)
        val vis = if (show3d) Property.VISIBLE else Property.NONE
        for (layer in style.layers) {
            if (layer is FillExtrusionLayer ||
                (layer.id.contains("building", ignoreCase = true) && layer.id.contains("3d", ignoreCase = true))
            ) {
                layer.setProperties(PropertyFactory.visibility(vis))
            }
        }
    }

    private fun registerMapBitmaps(style: Style) {
        fun addAndRecycle(id: String, bmp: android.graphics.Bitmap) {
            try {
                style.addImage(id, bmp)
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        addAndRecycle("pin-pending", MapBitmaps.createTeardropPin(this, "#F59E0B", 1.0f))
        addAndRecycle("pin-done", MapBitmaps.createTeardropPin(this, "#22C55E", 1.0f))
        addAndRecycle("pin-issue", MapBitmaps.createTeardropPin(this, "#EF4444", 1.0f))
        addAndRecycle("pin-target", MapBitmaps.createTeardropPin(this, "#2563EB", 1.05f))
        addAndRecycle("mosque-icon", MapBitmaps.createMosqueIcon(this))
        addAndRecycle("gps-arrow", MapBitmaps.createGpsArrow(this))
    }

    private fun enhanceVectorStyleForField(style: Style) {
        if (mapStyleMode != MapStyleMode.VECTOR) return
        runCatching {
            // Expose minor neighborhood roads, rural tracks & footpaths earlier
            for (layer in style.layers) {
                if (layer is LineLayer) {
                    val id = layer.id.lowercase()
                    if (id.contains("minor") || id.contains("service") ||
                        id.contains("track") || id.contains("path") ||
                        id.contains("residential") || id.contains("unclassified")
                    ) {
                        if (layer.minZoom > 12.0f) layer.minZoom = 12.0f
                    }
                }
            }
            // Dedicated Organic Maps-style Mosque POI layer across zoom levels
            val vecSource = style.sources.firstOrNull {
                it is org.maplibre.android.style.sources.VectorSource
            } ?: return
            val mosqueLayerId = "terrasync-mosques-layer"
            val isMosqueExpr = Expression.any(
                Expression.eq(Expression.get("subclass"), Expression.literal("mosque")),
                Expression.eq(Expression.get("subclass"), Expression.literal("muslim")),
                Expression.eq(Expression.get("subclass"), Expression.literal("islam")),
                Expression.all(
                    Expression.eq(Expression.get("class"), Expression.literal("place_of_worship")),
                    Expression.eq(Expression.get("religion"), Expression.literal("muslim"))
                )
            )
            // Suppress duplicate mosque icons on base style POI layers (supporting both expression & legacy filters)
            val notMosque = Expression.not(isMosqueExpr)
            for (layer in style.layers) {
                if (layer is SymbolLayer && layer.id != mosqueLayerId &&
                    (layer.sourceLayer == "poi" || layer.id.lowercase().contains("poi"))
                ) {
                    runCatching {
                        val existing = layer.filter
                        if (existing == null) {
                            layer.setFilter(notMosque)
                        } else {
                            val existingJson = existing.toString()
                            val isLegacyFilter = !existingJson.contains("[\"get\"") &&
                                !existingJson.contains("[\"match\"") &&
                                !existingJson.contains("[\"case\"")
                            if (isLegacyFilter) {
                                layer.setFilter(
                                    Expression.raw("""["all",$existingJson,["!in","subclass","mosque","muslim","islam"]]""")
                                )
                            } else {
                                layer.setFilter(Expression.all(existing, notMosque))
                            }
                        }
                    }
                }
            }
            if (style.getLayer(mosqueLayerId) == null) {
                val night = isDarkMapUi()
                val mosqueLayer = SymbolLayer(mosqueLayerId, vecSource.id).apply {
                    sourceLayer = "poi"
                    minZoom = 12.0f
                    setFilter(isMosqueExpr)
                    setProperties(
                        PropertyFactory.iconImage("mosque-icon"),
                        PropertyFactory.iconSize(0.92f),
                        PropertyFactory.iconAllowOverlap(false),
                        PropertyFactory.iconIgnorePlacement(false),
                        PropertyFactory.iconPadding(14f),
                        PropertyFactory.textField(Expression.get("name")),
                        PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                        PropertyFactory.textSize(10.5f),
                        PropertyFactory.textColor(if (night) "#FDE68A" else "#5D4037"),
                        PropertyFactory.textHaloColor(if (night) "#0F172A" else "#FFFFFF"),
                        PropertyFactory.textHaloWidth(1.6f),
                        PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                        PropertyFactory.textOffset(arrayOf(0f, 0.72f)),
                        PropertyFactory.textMaxWidth(8.5f),
                        PropertyFactory.textOptional(true),
                        PropertyFactory.textAllowOverlap(false)
                    )
                }
                style.addLayer(mosqueLayer)
            }
        }
    }

    private fun attachTerrainHillshade(style: Style) {
        if (mapStyleMode != MapStyleMode.VECTOR || isDarkMapUi() || !DevicePrefs.isHillshadeEnabled(this)) return
        val demFile = java.io.File(cacheDir, "terrarium_dem.json")
        fun addHillshadeFromFile() {
            runCatching {
                if (map?.style !== style || !style.isFullyLoaded) return
                if (style.getSource(HILLSHADE_SOURCE) == null) {
                    style.addSource(RasterDemSource(HILLSHADE_SOURCE, demFile.toURI()))
                }
                if (style.getLayer(HILLSHADE_LAYER) == null) {
                    val hill = HillshadeLayer(HILLSHADE_LAYER, HILLSHADE_SOURCE).withProperties(
                        PropertyFactory.hillshadeExaggeration(0.32f),
                        PropertyFactory.hillshadeShadowColor(Color.parseColor("#546B46")),
                        PropertyFactory.hillshadeHighlightColor(Color.parseColor("#FFFFFF")),
                        PropertyFactory.hillshadeAccentColor(Color.parseColor("#6F8A5B")),
                        PropertyFactory.hillshadeIlluminationDirection(315f)
                    )
                    val belowLayer = style.layers.firstOrNull { l ->
                        val id = l.id.lowercase()
                        id.contains("water") || id.contains("road") || id.contains("highway") || id.contains("building")
                    }
                    if (belowLayer != null) style.addLayerBelow(hill, belowLayer.id) else style.addLayer(hill)
                }
            }
        }
        if (demFile.exists()) {
            addHillshadeFromFile()
        } else {
            RoutingEngine.ioExecutor.execute {
                runCatching {
                    demFile.writeText("""{"tilejson":"2.2.0","tiles":["https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"],"minzoom":0,"maxzoom":14,"encoding":"terrarium"}""")
                }
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) addHillshadeFromFile()
                }
            }
        }
    }

    private fun attachOverlayLayers(style: Style) {
        val darkLabels = isDarkMapUi() || mapStyleMode == MapStyleMode.SATELLITE
        val labelColor = if (darkLabels) "#F8FAFC" else "#0F172A"
        val haloColor = if (darkLabels) "#0F172A" else "#FFFFFF"
        val showTitles = DevicePrefs.isShowBookmarkTitles(this)

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
                LineLayer(ROUTE_CASING_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor("#FFFFFF"),
                    PropertyFactory.lineWidth(9.5f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
            style.addLayer(
                LineLayer(ROUTE_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor(travelMode.routeColorHex),
                    PropertyFactory.lineWidth(6.2f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
            style.addLayer(
                LineLayer(ROUTE_WALK_CASING_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor("#FFFFFF"),
                    PropertyFactory.lineWidth(7.2f),
                    PropertyFactory.lineDasharray(arrayOf(1.6f, 1.3f)),
                    PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.visibility(Property.NONE)
                )
            )
            style.addLayer(
                LineLayer(ROUTE_WALK_LAYER, ROUTE_SOURCE).withProperties(
                    PropertyFactory.lineColor(TravelMode.WALKING.routeColorHex),
                    PropertyFactory.lineWidth(4.8f),
                    PropertyFactory.lineDasharray(arrayOf(2.4f, 1.8f)),
                    PropertyFactory.lineCap(Property.LINE_CAP_BUTT),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    PropertyFactory.visibility(Property.NONE)
                )
            )
        }

        if (style.getSource(ROUTE_PILL_SOURCE) == null) {
            style.addSource(GeoJsonSource(ROUTE_PILL_SOURCE))
            style.addLayer(
                SymbolLayer(ROUTE_PILL_LAYER, ROUTE_PILL_SOURCE).withProperties(
                    PropertyFactory.iconImage(ROUTE_PILL_ICON),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            )
        }

        if (style.getSource(PULSE_SOURCE) == null) {
            style.addSource(GeoJsonSource(PULSE_SOURCE))
            style.addLayer(
                CircleLayer(PULSE_LAYER, PULSE_SOURCE).withProperties(
                    PropertyFactory.circleColor(Expression.get("color")),
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleOpacity(0f)
                )
            )
        }

        if (style.getSource(PINS_SOURCE) == null) {
            style.addSource(GeoJsonSource(PINS_SOURCE))
            style.addLayer(
                SymbolLayer(PINS_LABELS_LAYER, PINS_SOURCE).withProperties(
                    PropertyFactory.visibility(if (showTitles) Property.VISIBLE else Property.NONE),
                    PropertyFactory.textField(Expression.get("label")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(10.5f),
                    PropertyFactory.textMaxWidth(9.0f),
                    PropertyFactory.textLineHeight(1.05f),
                    PropertyFactory.textPadding(6f),
                    PropertyFactory.textColor(labelColor),
                    PropertyFactory.textHaloColor(haloColor),
                    PropertyFactory.textHaloWidth(1.8f),
                    PropertyFactory.textOffset(arrayOf(0f, 0.25f)),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textAllowOverlap(false),
                    PropertyFactory.textIgnorePlacement(false),
                    PropertyFactory.textOptional(true)
                )
            )
            style.addLayer(
                SymbolLayer(PINS_LAYER, PINS_SOURCE).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            )
        }

        // Ruler Measurement Layers (Line + Numbered Waypoints)
        if (style.getSource(RULER_LINE_SOURCE) == null) {
            style.addSource(GeoJsonSource(RULER_LINE_SOURCE))
            style.addLayer(
                LineLayer(RULER_LINE_LAYER, RULER_LINE_SOURCE).withProperties(
                    PropertyFactory.lineColor("#2563EB"),
                    PropertyFactory.lineWidth(3.8f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
        }
        if (style.getSource(RULER_POINTS_SOURCE) == null) {
            style.addSource(GeoJsonSource(RULER_POINTS_SOURCE))
            style.addLayer(
                CircleLayer(RULER_POINTS_CIRCLE_LAYER, RULER_POINTS_SOURCE).withProperties(
                    PropertyFactory.circleColor("#FFFFFF"),
                    PropertyFactory.circleRadius(12.5f),
                    PropertyFactory.circleStrokeColor("#2563EB"),
                    PropertyFactory.circleStrokeWidth(2.6f)
                )
            )
            style.addLayer(
                SymbolLayer(RULER_POINTS_NUM_LAYER, RULER_POINTS_SOURCE).withProperties(
                    PropertyFactory.textField(Expression.get("num")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(12f),
                    PropertyFactory.textColor("#1E3A8A"),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true)
                )
            )
        }

        if (style.getSource(SELECTED_SOURCE) == null) {
            style.addSource(GeoJsonSource(SELECTED_SOURCE))
            style.addLayer(
                CircleLayer(SELECTED_HALO_LAYER, SELECTED_SOURCE).withProperties(
                    PropertyFactory.circleColor("#3B82F6"),
                    PropertyFactory.circleOpacity(0.22f),
                    PropertyFactory.circleRadius(13f),
                    PropertyFactory.circleStrokeColor("#2563EB"),
                    PropertyFactory.circleStrokeWidth(1.4f)
                )
            )
            style.addLayer(
                SymbolLayer(SELECTED_PIN_LAYER, SELECTED_SOURCE).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconSize(1.16f),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            )
        }

        if (style.getSource(USER_LOC_SOURCE) == null) {
            style.addSource(GeoJsonSource(USER_LOC_SOURCE))
            style.addLayer(
                SymbolLayer(USER_LOC_LAYER, USER_LOC_SOURCE).withProperties(
                    PropertyFactory.iconImage("gps-arrow"),
                    PropertyFactory.iconRotate(Expression.get("bearing")),
                    PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            )
        }
    }

    private fun getVisiblePins(): List<PinItem> {
        val hiddenLayerIds = DevicePrefs.getHiddenLayerIds(this)
        val sentIds = if (hideSentPins) DevicePrefs.getLocalSentPinIds(this) else emptySet()
        return pins.filter { p ->
            val normStatus = p.status.lowercase().ifBlank { "pending" }
            p.layerId !in hiddenLayerIds && normStatus in visibleStatuses && p.id !in sentIds
        }
    }

    private fun findExactStackedPins(pin: PinItem): List<PinItem> {
        val stack = getVisiblePins()
            .filter {
                kotlin.math.abs(it.lat - pin.lat) <= 0.00003 &&
                    kotlin.math.abs(it.lng - pin.lng) <= 0.00003 &&
                    RoutingEngine.haversineMeters(it.lat, it.lng, pin.lat, pin.lng) <= 2.0
            }
            .sortedBy { it.id }
        return if (stack.any { it.id == pin.id }) stack else listOf(pin)
    }

    private fun renderMapData() {
        btnSyncStatus.setColorFilter(Color.parseColor(if (isLiveConnected) "#22C55E" else "#F59E0B"))

        val hiddenLayerIds = DevicePrefs.getHiddenLayerIds(this)
        val visiblePins = getVisiblePins()
        val visibleTracks = tracks.filter { it.layerId !in hiddenLayerIds }

        val style = map?.style ?: return
        if (!style.isFullyLoaded) return

        val showTitles = DevicePrefs.isShowBookmarkTitles(this)
        style.getLayer(PINS_LABELS_LAYER)?.setProperties(
            PropertyFactory.visibility(if (showTitles) Property.VISIBLE else Property.NONE)
        )

        val pinFeatures = visiblePins.map { p ->
            val shortLabel = p.name.trim().let {
                if (it.length > 22) it.take(21).trimEnd() + "…" else it
            }
            Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                addStringProperty("id", p.id)
                addStringProperty("name", p.name)
                addStringProperty("label", shortLabel)
                addStringProperty("icon", statusIconId(p.status))
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

        val routeToShow = activeNavRoute ?: previewRoute
        val displayGeom = if (activeNavRoute != null && (activeNavTrimmedGeom?.size ?: 0) >= 2) {
            activeNavTrimmedGeom!!
        } else {
            routeToShow?.geometry ?: emptyList()
        }
        val routeFeatures = if (routeToShow != null && displayGeom.size >= 2) {
            listOf(Feature.fromGeometry(LineString.fromLngLats(displayGeom.map { Point.fromLngLat(it.first, it.second) })))
        } else emptyList()
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(routeFeatures))
        if (routeToShow != null) {
            val isWalk = routeToShow.mode == TravelMode.WALKING
            style.getLayerAs<LineLayer>(ROUTE_CASING_LAYER)?.setProperties(
                PropertyFactory.lineWidth(9.5f),
                PropertyFactory.lineOpacity(1.0f),
                PropertyFactory.visibility(if (isWalk) Property.NONE else Property.VISIBLE)
            )
            style.getLayerAs<LineLayer>(ROUTE_LAYER)?.setProperties(
                PropertyFactory.lineColor(routeToShow.mode.routeColorHex),
                PropertyFactory.visibility(if (isWalk) Property.NONE else Property.VISIBLE)
            )
            style.getLayerAs<LineLayer>(ROUTE_WALK_CASING_LAYER)?.setProperties(
                PropertyFactory.visibility(if (isWalk) Property.VISIBLE else Property.NONE)
            )
            style.getLayerAs<LineLayer>(ROUTE_WALK_LAYER)?.setProperties(
                PropertyFactory.lineColor(routeToShow.mode.routeColorHex),
                PropertyFactory.visibility(if (isWalk) Property.VISIBLE else Property.NONE)
            )
        }

        val pillRoute = if (activeNavRoute == null) previewRoute else null
        val pillFeatures = if (pillRoute != null && pillRoute.geometry.size >= 2) {
            val etaMin = (pillRoute.totalDurationSeconds / 60.0).toInt().coerceAtLeast(1)
            val pillLabel = if (etaMin >= 60) "${etaMin / 60}h ${etaMin % 60}m" else "$etaMin min"
            val pillKey = "$pillLabel|${pillRoute.mode.routeColorHex}"
            if (lastRoutePillKey != pillKey) {
                val pillBmp = MapBitmaps.createRoutePill(this, pillLabel, pillRoute.mode.routeColorHex)
                try {
                    style.addImage(ROUTE_PILL_ICON, pillBmp)
                } finally {
                    if (!pillBmp.isRecycled) pillBmp.recycle()
                }
                lastRoutePillKey = pillKey
            }
            val (midLng, midLat) = routeMidpoint(pillRoute.geometry)
            listOf(Feature.fromGeometry(Point.fromLngLat(midLng, midLat)))
        } else emptyList()
        style.getSourceAs<GeoJsonSource>(ROUTE_PILL_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(pillFeatures))

        // Ruler features
        val rulerLineFeatures = if (rulerPoints.size >= 2) {
            listOf(Feature.fromGeometry(LineString.fromLngLats(rulerPoints.map { Point.fromLngLat(it.longitude, it.latitude) })))
        } else emptyList()
        style.getSourceAs<GeoJsonSource>(RULER_LINE_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(rulerLineFeatures))

        val rulerPointFeatures = rulerPoints.mapIndexed { idx, pt ->
            Feature.fromGeometry(Point.fromLngLat(pt.longitude, pt.latitude)).apply {
                addStringProperty("num", "${idx + 1}")
            }
        }
        style.getSourceAs<GeoJsonSource>(RULER_POINTS_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(rulerPointFeatures))

        val selLoc = selectedMarkerLatLng
        val selFeatures = if (selLoc != null) {
            listOf(
                Feature.fromGeometry(Point.fromLngLat(selLoc.longitude, selLoc.latitude)).apply {
                    addStringProperty("icon", selectedMarkerIcon)
                }
            )
        } else emptyList()
        style.getSourceAs<GeoJsonSource>(SELECTED_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(selFeatures))

        if (currentLocation != null) {
            updateGpsLayerFeature(displayedLat, displayedLng, displayedBearing)
        }
    }

    private fun routeMidpoint(geom: List<Pair<Double, Double>>): Pair<Double, Double> {
        if (geom.size <= 2) return geom.first()
        var total = 0.0
        val segs = DoubleArray(geom.size - 1)
        for (i in 0 until geom.size - 1) {
            val d = RoutingEngine.haversineMeters(geom[i].second, geom[i].first, geom[i + 1].second, geom[i + 1].first)
            segs[i] = d
            total += d
        }
        val half = total * 0.5
        var acc = 0.0
        for (i in segs.indices) {
            if (acc + segs[i] >= half && segs[i] > 0.0) {
                val t = ((half - acc) / segs[i]).coerceIn(0.0, 1.0)
                val lng = geom[i].first + (geom[i + 1].first - geom[i].first) * t
                val lat = geom[i].second + (geom[i + 1].second - geom[i].second) * t
                return lng to lat
            }
            acc += segs[i]
        }
        return geom[geom.size / 2]
    }

    private fun updateActiveRouteLine(geom: List<Pair<Double, Double>>) {
        if (!isActivityStarted) return
        val style = map?.style ?: return
        if (!style.isFullyLoaded || geom.size < 2) return
        val feature = Feature.fromGeometry(LineString.fromLngLats(geom.map { Point.fromLngLat(it.first, it.second) }))
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(listOf(feature)))
    }

    private fun updateGpsLayerFeature(lat: Double, lng: Double, bearing: Float) {
        if (!isActivityStarted) return
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        val f = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addNumberProperty("bearing", bearing)
        }
        style.getSourceAs<GeoJsonSource>(USER_LOC_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(listOf(f)))
    }

    private fun fireStatusPulse(lat: Double, lng: Double, statusHex: String) {
        if (!isActivityResumed) return
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        val f = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("color", statusHex)
        }
        style.getSourceAs<GeoJsonSource>(PULSE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(listOf(f)))
        if (style.getLayerAs<CircleLayer>(PULSE_LAYER) == null) return

        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val curStyle = map?.style
                if (!isActivityStarted || curStyle !== style || !curStyle.isFullyLoaded) {
                    anim.cancel()
                    return@addUpdateListener
                }
                val v = anim.animatedValue as Float
                runCatching {
                    curStyle.getLayerAs<CircleLayer>(PULSE_LAYER)?.setProperties(
                        PropertyFactory.circleRadius(8f + 28f * v),
                        PropertyFactory.circleOpacity(0.65f * (1f - v))
                    )
                }
            }
            start()
        }
    }

    // --- Multi-Point Distance Ruler Tool ---

    // --- Local-Only Sent Pin Tracking & Draw-a-Circle (Lasso) Selection ---

    private fun updateSentToggleUi() {
        val night = isDarkMapUi()
        if (hideSentPins) {
            btnSentToggle.text = "Unsent Only"
            btnSentToggle.setTextColor(Color.WHITE)
            UiAnimations.styleFloatingTile(this, btnSentToggle, Color.parseColor("#16A34A"), 21f, 6f, "#44FFFFFF", Color.TRANSPARENT, 0f)
        } else {
            btnSentToggle.text = "All Pins"
            btnSentToggle.setTextColor(Color.parseColor(if (night) "#F1F5F9" else "#334155"))
            UiAnimations.styleFloatingTile(
                this,
                btnSentToggle,
                Color.parseColor(if (night) "#1E293B" else "#FFFFFF"),
                21f,
                6f,
                strokeColor = Color.parseColor(if (night) "#334155" else "#E2E8F0")
            )
        }
        updateFilterIconTint()
    }

    private fun activeLocalSentCount(): Int {
        val sentIds = DevicePrefs.getLocalSentPinIds(this)
        if (sentIds.isEmpty()) return 0
        return pins.count { it.id in sentIds }
    }

    private fun toggleLassoMode(enabled: Boolean) {
        if (enabled && isRulerMode) toggleRulerMode(false)
        isLassoMode = enabled
        lassoMoveMapMode = false
        if (enabled) {
            hideInScreenSheet()
            bottomIconRow.visibility = View.GONE
            UiAnimations.showSlideBottom(lassoCard)
            lassoOverlay.visibility = View.VISIBLE
            lassoOverlay.clearLoop()
            lassoPendingPinIds = emptyList()
            updateLassoUi()
        } else {
            lassoPendingPinIds = emptyList()
            lassoOverlay.clearLoop()
            lassoOverlay.visibility = View.GONE
            UiAnimations.hideSlideBottom(lassoCard)
            if (activeNavRoute == null && !isRulerMode) UiAnimations.showSlideBottom(bottomIconRow)
        }
        applyMapChromeTheme()
    }

    private fun toggleLassoMoveMapMode() {
        if (!isLassoMode) return
        lassoMoveMapMode = !lassoMoveMapMode
        if (lassoMoveMapMode) {
            lassoPendingPinIds = emptyList()
            lassoOverlay.clearLoop()
            lassoOverlay.visibility = View.GONE
        } else {
            lassoOverlay.visibility = View.VISIBLE
        }
        updateLassoUi()
    }

    private fun clearLassoSelection() {
        lassoPendingPinIds = emptyList()
        lassoOverlay.clearLoop()
        updateLassoUi()
    }

    private fun handleLassoLoopCompleted(poly: List<PointF>) {
        val m = map ?: return
        val inside = getVisiblePins().filter { p ->
            val pt = m.projection.toScreenLocation(LatLng(p.lat, p.lng))
            LassoOverlayView.isPointInPolygon(pt.x, pt.y, poly)
        }
        if (inside.isEmpty()) {
            lassoPendingPinIds = emptyList()
            lassoOverlay.clearLoop()
            updateLassoUi()
            Toast.makeText(this, "No visible pins inside circle", Toast.LENGTH_SHORT).show()
            return
        }
        val unsentInside = inside.filter { !DevicePrefs.isPinLocalSent(this, it.id) }
        if (unsentInside.isEmpty()) {
            lassoPendingPinIds = emptyList()
            lassoOverlay.clearLoop()
            updateLassoUi()
            Toast.makeText(this, "All ${inside.size} pins inside are already marked sent", Toast.LENGTH_SHORT).show()
            return
        }
        lassoPendingPinIds = unsentInside.map { it.id }
        updateLassoUi()
    }

    private fun confirmLassoMarkSent() {
        if (lassoPendingPinIds.isEmpty()) {
            Toast.makeText(this, "Draw a circle around pins first", Toast.LENGTH_SHORT).show()
            return
        }
        val batch = lassoPendingPinIds.toList()
        DevicePrefs.addLocalSentPinIds(this, batch)
        sentUndoStack.add(batch)
        lassoPendingPinIds = emptyList()
        lassoOverlay.clearLoop()
        updateSentToggleUi()
        updateLassoUi()
        renderMapData()
        Toast.makeText(this, "Marked ${batch.size} pin${if (batch.size == 1) "" else "s"} as sent (local only)", Toast.LENGTH_SHORT).show()
    }

    private fun undoLastLassoMark() {
        if (sentUndoStack.isEmpty()) return
        val lastBatch = sentUndoStack.removeAt(sentUndoStack.lastIndex)
        DevicePrefs.removeLocalSentPinIds(this, lastBatch)
        updateSentToggleUi()
        updateLassoUi()
        renderMapData()
        Toast.makeText(this, "Undid ${lastBatch.size} sent pin${if (lastBatch.size == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
    }

    private fun confirmResetAllSentPins() {
        val active = activeLocalSentCount()
        val stored = DevicePrefs.getLocalSentPinIds(this).size
        if (active == 0 && stored == 0) {
            Toast.makeText(this, "No sent pins to reset", Toast.LENGTH_SHORT).show()
            return
        }
        if (active == 0) {
            DevicePrefs.clearLocalSentPins(this)
            sentUndoStack.clear()
            updateLassoUi()
            Toast.makeText(this, "Cleared old sent marks", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Reset Sent Marks?")
            .setMessage("Clear all $active local sent marks on this phone? All pins remain safely in your folders.")
            .setPositiveButton("Reset") { _, _ ->
                DevicePrefs.clearLocalSentPins(this)
                sentUndoStack.clear()
                lassoPendingPinIds = emptyList()
                lassoOverlay.clearLoop()
                updateSentToggleUi()
                updateLassoUi()
                renderMapData()
                Toast.makeText(this, "Cleared all local sent marks", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateLassoUi() {
        val sel = lassoPendingPinIds.size
        val totalSent = activeLocalSentCount()
        if (lassoMoveMapMode) {
            btnLassoModeToggle.text = "Draw"
            btnLassoModeToggle.setTextColor(Color.WHITE)
            btnLassoModeToggle.background = GradientDrawable().apply {
                setColor(Color.parseColor("#16A34A"))
                cornerRadius = dp(10).toFloat()
            }
            tvLassoTitle.text = "Move or zoom the map"
            tvLassoSubtitle.text = "Tap Draw when ready to circle pins"
            btnLassoMarkSent.text = "Mark Sent"
            btnLassoMarkSent.alpha = 0.45f
            btnLassoRedraw.alpha = 0.45f
        } else {
            val night = isDarkMapUi()
            btnLassoModeToggle.text = "Move Map"
            btnLassoModeToggle.setTextColor(Color.parseColor(if (night) "#93C5FD" else "#1D4ED8"))
            btnLassoModeToggle.background = GradientDrawable().apply {
                setColor(Color.parseColor(if (night) "#1E3A5F" else "#EFF6FF"))
                cornerRadius = dp(10).toFloat()
            }
            if (sel > 0) {
                tvLassoTitle.text = "$sel pin${if (sel == 1) "" else "s"} selected"
                tvLassoSubtitle.text = "Tap Mark Sent (local only • $totalSent sent total)"
                btnLassoMarkSent.text = "Mark Sent ($sel)"
                btnLassoMarkSent.alpha = 1f
                btnLassoRedraw.alpha = 1f
            } else {
                tvLassoTitle.text = "Draw circle around sent pins"
                tvLassoSubtitle.text = if (totalSent > 0) "$totalSent marked sent on this phone" else "Local to this phone only"
                btnLassoMarkSent.text = "Mark Sent"
                btnLassoMarkSent.alpha = 0.45f
                btnLassoRedraw.alpha = 0.45f
            }
        }
        btnLassoUndo.alpha = if (sentUndoStack.isNotEmpty()) 1f else 0.4f
        btnLassoReset.alpha = if (totalSent > 0) 1f else 0.4f
    }

    private fun toggleRulerMode(enabled: Boolean) {
        if (enabled && isLassoMode) toggleLassoMode(false)
        isRulerMode = enabled
        if (enabled) {
            hideInScreenSheet()
            bottomIconRow.visibility = View.GONE
            UiAnimations.showSlideBottom(rulerCard)
            updateRulerUi()
        } else {
            rulerPoints.clear()
            UiAnimations.hideSlideBottom(rulerCard)
            if (activeNavRoute == null && !isLassoMode) UiAnimations.showSlideBottom(bottomIconRow)
        }
        applyMapChromeTheme()
        renderMapData()
    }

    private fun addRulerPoint(latLng: LatLng) {
        rulerPoints.add(latLng)
        fireStatusPulse(latLng.latitude, latLng.longitude, "#2563EB")
        updateRulerUi()
        renderMapData()
    }

    private fun undoRulerPoint() {
        if (rulerPoints.isNotEmpty()) {
            rulerPoints.removeAt(rulerPoints.lastIndex)
            updateRulerUi()
            renderMapData()
        }
    }

    private fun resetRulerPoints() {
        rulerPoints.clear()
        updateRulerUi()
        renderMapData()
    }

    private fun updateRulerUi() {
        var totalMeters = 0.0
        for (i in 0 until rulerPoints.size - 1) {
            val a = rulerPoints[i]
            val b = rulerPoints[i + 1]
            totalMeters += RoutingEngine.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        tvRulerDistance.text = when {
            rulerPoints.size < 2 -> "0 m"
            totalMeters < 1000.0 -> "${totalMeters.roundToInt()} m"
            totalMeters < 10000.0 -> "${"%.2f".format(totalMeters / 1000.0)} km"
            else -> "${"%.1f".format(totalMeters / 1000.0)} km"
        }
        tvRulerSubtitle.text = when (rulerPoints.size) {
            0 -> "Tap map to drop point 1"
            1 -> "Tap next point to measure"
            else -> "Total distance between ${rulerPoints.size} points"
        }
        btnRulerUndo.alpha = if (rulerPoints.isNotEmpty()) 1.0f else 0.35f
        btnRulerReset.visibility = if (rulerPoints.isNotEmpty()) View.VISIBLE else View.INVISIBLE
    }

    // --- Route From Origin Helpers ---

    private fun setRouteOrigin(lat: Double, lng: Double, label: String) {
        routeOriginLatLng = LatLng(lat, lng)
        routeOriginName = label.ifBlank { "${"%.5f".format(lat)}, ${"%.5f".format(lng)}" }
        tvRouteFromText.text = "From: $routeOriginName (tap a destination)"
        UiAnimations.showSlideTop(routeFromBanner)
        hideInScreenSheet()
        Toast.makeText(this, "Start set: $routeOriginName — tap any pin or place & choose Route To", Toast.LENGTH_LONG).show()
    }

    private fun clearRouteOrigin() {
        routeOriginLatLng = null
        routeOriginName = ""
        UiAnimations.hideSlideTop(routeFromBanner)
    }

    // --- Map Tap Handling (Zero Auto-Zoom) ---

    private fun handleMapTap(m: MapLibreMap, latLng: LatLng) {
        val screenPt = m.projection.toScreenLocation(latLng)
        val hitBox = RectF(
            screenPt.x - dp(24).toFloat(),
            screenPt.y - dp(36).toFloat(),
            screenPt.x + dp(24).toFloat(),
            screenPt.y + dp(14).toFloat()
        )

        val pinHits = m.queryRenderedFeatures(hitBox, PINS_LAYER, PINS_LABELS_LAYER)
        val pin = pinHits
            .mapNotNull { hit -> hit.getStringProperty("id")?.let { id -> pins.find { it.id == id } } }
            .distinctBy { it.id }
            .minByOrNull { candidate ->
                val pt = m.projection.toScreenLocation(LatLng(candidate.lat, candidate.lng))
                val dx = pt.x - screenPt.x
                val dy = (pt.y - dp(12)) - screenPt.y
                dx * dx + dy * dy
            }
        if (pin != null) {
            selectPinAndOpenCard(pin, zoomIfFar = false)
            return
        }

        val smallBox = RectF(
            screenPt.x - dp(14).toFloat(),
            screenPt.y - dp(14).toFloat(),
            screenPt.x + dp(14).toFloat(),
            screenPt.y + dp(14).toFloat()
        )
        val vectorFeatures = m.queryRenderedFeatures(smallBox)
        var detectedTitle = ""
        var detectedSub = ""
        for (f in vectorFeatures) {
            val props = f.properties() ?: continue
            val name = listOf("name", "name:en", "name:ku", "name:ar", "name:latin")
                .firstNotNullOfOrNull { k ->
                    if (props.has(k) && !props.get(k).isJsonNull) props.get(k).asString.trim().takeIf { it.isNotBlank() } else null
                }
            if (name != null) {
                detectedTitle = name
                val cls = if (props.has("class") && !props.get("class").isJsonNull) props.get("class").asString else ""
                if (cls.isNotBlank()) detectedSub = cls.replace("_", " ").replaceFirstChar { it.uppercase() }
                break
            }
        }

        selectedPinId = null
        selectedMarkerLatLng = latLng
        selectedMarkerIcon = "pin-target"
        renderMapData()
        fireStatusPulse(latLng.latitude, latLng.longitude, "#2563EB")

        val initialTitle = detectedTitle.ifBlank { "Selected Location" }
        showTappedPlaceCard(initialTitle, detectedSub, latLng.latitude, latLng.longitude)

        RoutingEngine.reverseGeocode(latLng.latitude, latLng.longitude) { place ->
            if (place != null) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (activeTappedLatLng != latLng || selectedPinId != null) return@runOnUiThread
                    if (sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) return@runOnUiThread
                    val bestTitle = if (detectedTitle.isNotBlank()) detectedTitle else place.title
                    val bestSub = place.subtitle.ifBlank { detectedSub }
                    tappedResolvedTitle = bestTitle
                    tappedResolvedSub = bestSub
                    tappedTitleView?.text = bestTitle
                    val distText = currentLocation?.let {
                        "${formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, latLng.latitude, latLng.longitude))} away"
                    } ?: ""
                    val subLine = listOf(bestSub, distText).filter { it.isNotBlank() }.joinToString(" • ")
                    tappedSubView?.text = subLine
                    tappedSubView?.visibility = if (subLine.isNotBlank()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    private fun selectPinAndOpenCard(pin: PinItem, zoomIfFar: Boolean = false) {
        selectedPinId = pin.id
        selectedMarkerLatLng = LatLng(pin.lat, pin.lng)
        selectedMarkerIcon = statusIconId(pin.status)
        renderMapData()
        val currentZoom = map?.cameraPosition?.zoom ?: 15.0
        val targetZoom = if (zoomIfFar) currentZoom.coerceAtLeast(15.8) else currentZoom
        centerUpperHalf(LatLng(pin.lat, pin.lng), targetZoom)
        showPinCard(pin, animateSwap = false)
    }

    private fun clearSelectedMarker() {
        selectedPinId = null
        selectedMarkerLatLng = null
        activeSharedPinId = null
        activeTappedLatLng = null
        tappedTitleView = null
        tappedSubView = null
        renderMapData()
    }

    private fun centerUpperHalf(target: LatLng, zoom: Double) {
        val m = map ?: return
        val cam = CameraPosition.Builder()
            .target(target)
            .zoom(zoom)
            .padding(0.0, 0.0, 0.0, dp(235).toDouble())
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 420)
    }

    private fun resetMapPadding() {
        val m = map ?: return
        val cam = CameraPosition.Builder()
            .target(m.cameraPosition.target)
            .zoom(m.cameraPosition.zoom)
            .padding(0.0, 0.0, 0.0, 0.0)
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 240)
    }

    private fun showInScreenSheet(peekDp: Int = 250) {
        UiAnimations.fadeHide(bottomDock, 140L)
        val maxPeek = (resources.displayMetrics.heightPixels * 0.48f).toInt().coerceAtLeast(dp(160))
        sheetBehavior.peekHeight = minOf(dp(peekDp) + navBarInsetBottom, maxPeek)
        sheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun hideInScreenSheet() {
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun makePinShareMessage(name: String, lat: Double, lng: Double, pinId: String = ""): String {
        val encodedName = Uri.encode(name)
        val idPart = if (pinId.isNotBlank()) "&id=${Uri.encode(pinId)}" else ""
        val link = "https://thamirakkurd.github.io/TerraSync/pin?lat=$lat&lng=$lng&name=$encodedName$idPart"
        return "$name\n${"%.6f".format(lat)}, ${"%.6f".format(lng)}\nOpen in TerraSync: $link"
    }

    // --- Polished Pin Card (Stacked Cycler, Route To / Route From, Owner-Only Delete) ---

    private fun showPinCard(pin: PinItem, animateSwap: Boolean = false) {
        val night = isDarkMapUi()
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#0F172A")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val chipBg = Color.parseColor(if (night) "#334155" else "#F1F5F9")
        val cardTint = Color.parseColor(if (night) "#263449" else "#F8FAFC")
        val dividerColor = Color.parseColor(if (night) "#334155" else "#E2E8F0")

        sheetContent.removeAllViews()

        // 1. Exact-Coordinate Stacked Pin Banner + Animated Next Button
        val stackedPins = findExactStackedPins(pin)
        if (stackedPins.size > 1) {
            val currentIdx = stackedPins.indexOfFirst { it.id == pin.id }.coerceAtLeast(0)
            val stackRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (night) "#1E3A5F" else "#EFF6FF"))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.parseColor(if (night) "#3B82F6" else "#BFDBFE"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }

            stackRow.addView(TextView(this).apply {
                text = "${stackedPins.size} stacked here • ${currentIdx + 1}/${stackedPins.size}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(if (night) "#93C5FD" else "#1D4ED8"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            stackRow.addView(MaterialButton(this).apply {
                text = "Next →"
                textSize = 12.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(12), 0, dp(12), 0)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    val nextPin = stackedPins[(currentIdx + 1) % stackedPins.size]
                    selectedPinId = nextPin.id
                    selectedMarkerLatLng = LatLng(nextPin.lat, nextPin.lng)
                    selectedMarkerIcon = statusIconId(nextPin.status)
                    renderMapData()
                    fireStatusPulse(nextPin.lat, nextPin.lng, statusColorHex(nextPin.status))
                    showPinCard(nextPin, animateSwap = true)
                }
            })
            sheetContent.addView(stackRow)
        }

        // 2. Top Header: Title + Star + Share + Close
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        headerRow.addView(TextView(this).apply {
            text = pin.name
            textSize = 17f
            setLineSpacing(0f, 1.15f)
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        })

        var starred = DevicePrefs.isBookmarked(this, pin.id)
        headerRow.addView(ImageButton(this).apply {
            setImageResource(if (starred) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setPadding(dp(6), dp(6), dp(6), dp(6))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                starred = DevicePrefs.toggleBookmark(this@MainActivity, pin.id)
                setImageResource(if (starred) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
                Toast.makeText(this@MainActivity, if (starred) "Starred" else "Unstarred", Toast.LENGTH_SHORT).show()
            }
        })

        headerRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_share)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setPadding(dp(7), dp(7), dp(7), dp(7))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                val msg = makePinShareMessage(pin.name, pin.lat, pin.lng, pin.id)
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, msg)
                }, "Share TerraSync Pin"))
            }
        })

        headerRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(txtSub)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setPadding(dp(7), dp(7), dp(7), dp(7))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { hideInScreenSheet() }
        })
        sheetContent.addView(headerRow)

        // 3. Folder + Distance + Coordinates
        val layer = layers.find { it.id == pin.layerId }
        val layerName = layer?.name ?: "Field Pin"
        val distText = currentLocation?.let {
            " • ${formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, pin.lat, pin.lng))} away"
        } ?: ""
        sheetContent.addView(TextView(this).apply {
            text = "$layerName$distText"
            textSize = 13f
            setTextColor(Color.parseColor("#2563EB"))
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(3))
        })
        sheetContent.addView(TextView(this).apply {
            text = "${"%.6f".format(pin.lat)}, ${"%.6f".format(pin.lng)}"
            textSize = 12.5f
            setTextColor(txtSub)
            setPadding(0, 0, 0, dp(12))
        })

        if (pin.status.equals("issue", ignoreCase = true) && pin.issueReason.isNotBlank()) {
            sheetContent.addView(TextView(this).apply {
                text = "⚠ Issue: ${pin.issueReason}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#DC2626"))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1AEF4444"))
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            })
        } else if (pin.status.equals("done", ignoreCase = true) && pin.issueReason.isNotBlank()) {
            sheetContent.addView(TextView(this).apply {
                text = "✓ Done: ${pin.issueReason}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#15803D"))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A22C55E"))
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            })
        }

        // 4. Status Buttons
        val statusRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
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
                textSize = 13f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor(hex))
                alpha = if (active) 1f else 0.55f
                cornerRadius = dp(12)
                layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    if (idx < 2) marginEnd = dp(8)
                }
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    if (key == "issue") {
                        showIssueReasonDialog(pin)
                    } else if (key == "done") {
                        showDoneReasonDialog(pin)
                    } else {
                        savePinStatusUnified(pin, key, stamp)
                        fireStatusPulse(pin.lat, pin.lng, hex)
                        selectedMarkerIcon = statusIconId(key)
                        renderMapData()
                        Toast.makeText(this@MainActivity, "Marked $title", Toast.LENGTH_SHORT).show()
                        hideInScreenSheet()
                    }
                }
            })
        }
        sheetContent.addView(statusRow)

        // 5. Route To & Route From Row
        val routeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        routeRow.addView(MaterialButton(this).apply {
            text = "Route To"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            maxLines = 1
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { openRoutePreview(pin.lat, pin.lng, pin.name) }
        })
        routeRow.addView(MaterialButton(this).apply {
            text = "Route From"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            maxLines = 1
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0F766E"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { setRouteOrigin(pin.lat, pin.lng, pin.name) }
        })
        sheetContent.addView(routeRow)

        // 6. Secondary Row: Edit + External
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        actionRow.addView(MaterialButton(this).apply {
            text = "Edit"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            maxLines = 1
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            setTextColor(txtPrimary)
            setBackgroundColor(chipBg)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { showEditPinDialog(pin) }
        })
        actionRow.addView(MaterialButton(this).apply {
            text = "External"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            maxLines = 1
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            setTextColor(txtPrimary)
            setBackgroundColor(chipBg)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                val label = Uri.encode(pin.name)
                val geoUri = Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}($label)")
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, geoUri), "Open in"), "No external map app installed")
            }
        })
        var localSent = DevicePrefs.isPinLocalSent(this, pin.id)
        actionRow.addView(MaterialButton(this).apply {
            text = if (localSent) "Sent ✓" else "Mark Sent"
            textSize = 12.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            maxLines = 1
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(6), 0, dp(6), 0)
            setTextColor(if (localSent) Color.WHITE else txtPrimary)
            setBackgroundColor(if (localSent) Color.parseColor("#16A34A") else chipBg)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                localSent = DevicePrefs.toggleLocalSentPin(this@MainActivity, pin.id)
                text = if (localSent) "Sent ✓" else "Mark Sent"
                setTextColor(if (localSent) Color.WHITE else txtPrimary)
                setBackgroundColor(if (localSent) Color.parseColor("#16A34A") else chipBg)
                updateSentToggleUi()
                renderMapData()
                Toast.makeText(
                    this@MainActivity,
                    if (localSent) "Marked sent (local only)" else "Removed sent mark",
                    Toast.LENGTH_SHORT
                ).show()
                if (localSent && hideSentPins) hideInScreenSheet()
            }
        })
        sheetContent.addView(actionRow)

        // 7. Expanded Details Section
        sheetContent.addView(View(this).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                bottomMargin = dp(12)
            }
        })

        if (pin.description.isNotBlank()) {
            sheetContent.addView(TextView(this).apply {
                text = pin.description
                textSize = 13.5f
                setLineSpacing(dp(2).toFloat(), 1.0f)
                setTextColor(txtPrimary)
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    setColor(cardTint)
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            })
        }

        if (pin.isEdited && pin.editedBy.isNotBlank()) {
            val stampTime = if (pin.editedAt > 0L) SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(pin.editedAt)) else ""
            sheetContent.addView(TextView(this).apply {
                text = "✎ Edited by ${pin.editedBy}${if (stampTime.isNotBlank()) " • $stampTime" else ""}"
                textSize = 12.5f
                setTextColor(Color.parseColor("#0F766E"))
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1A0F766E"))
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            })
        }

        val copyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        listOf(
            "Copy Name" to pin.name,
            "Copy Desc" to pin.description,
            "Copy GPS" to "${pin.lat}, ${pin.lng}"
        ).forEachIndexed { idx, (label, clipVal) ->
            copyRow.addView(MaterialButton(this).apply {
                text = label
                textSize = 12.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                setTextColor(txtPrimary)
                setBackgroundColor(chipBg)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (idx < 2) marginEnd = dp(8)
                }
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    if (clipVal.isBlank()) {
                        Toast.makeText(this@MainActivity, "No description to copy", Toast.LENGTH_SHORT).show()
                    } else {
                        copyToClipboard(label, clipVal)
                    }
                }
            })
        }
        sheetContent.addView(copyRow)

        // 8. Owner Check (Selective Edit Deletion & Pin Deletion)
        val isOwner = isLayerOwner(layer)

        if (pin.editHistory.isNotBlank()) {
            val histLines = pin.editHistory.lines().map { it.trim() }.filter { it.isNotBlank() }
            if (isOwner && histLines.isNotEmpty()) {
                sheetContent.addView(TextView(this).apply {
                    text = "Edit Log (Tap ✕ to delete an edit without deleting pin):"
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(txtSub)
                    setPadding(0, dp(2), 0, dp(4))
                })
                val rowBgColor = Color.parseColor(if (isDarkMapUi()) "#1E293B" else "#F1F5F9")
                histLines.forEachIndexed { idx, line ->
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(8), dp(5), dp(4), dp(5))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(8).toFloat()
                            setColor(rowBgColor)
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(5) }
                    }
                    row.addView(TextView(this).apply {
                        text = line
                        textSize = 11.5f
                        setTextColor(txtSub)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    row.addView(TextView(this).apply {
                        text = "✕"
                        textSize = 14f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.parseColor("#EF4444"))
                        gravity = Gravity.CENTER
                        setPadding(dp(10), dp(4), dp(8), dp(4))
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Delete this edit?")
                                .setMessage("$line\n\nOnly this edit will be removed. The pin stays on the map and its status recalculates from the remaining edits.")
                                .setPositiveButton("Delete Edit") { _, _ ->
                                    val updated = deletePinHistoryEntryUnified(pin, idx)
                                    selectedMarkerIcon = statusIconId(updated.status)
                                    renderMapData()
                                    fireStatusPulse(updated.lat, updated.lng, statusColorHex(updated.status))
                                    showPinCard(updated, animateSwap = true)
                                    Toast.makeText(this@MainActivity, "Edit deleted — pin kept", Toast.LENGTH_SHORT).show()
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    })
                    sheetContent.addView(row)
                }
            } else {
                sheetContent.addView(TextView(this).apply {
                    text = "Edit Log:\n${pin.editHistory}"
                    textSize = 12f
                    setLineSpacing(dp(2).toFloat(), 1.0f)
                    setTextColor(txtSub)
                    setPadding(0, 0, 0, dp(10))
                })
            }
        }
        if (isOwner && pin.editHistory.isBlank() && !pin.status.equals("pending", true)) {
            sheetContent.addView(MaterialButton(this).apply {
                text = "Reset Status to Pending (Keep Pin)"
                textSize = 13f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setTextColor(Color.parseColor("#D97706"))
                setBackgroundColor(Color.parseColor("#1AF59E0B"))
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply {
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Reset status to Pending?")
                        .setMessage("Clears the current status and note while keeping \"${pin.name}\" on the map.")
                        .setPositiveButton("Reset") { _, _ ->
                            val updated = resetPinStatusUnified(pin)
                            selectedMarkerIcon = statusIconId(updated.status)
                            renderMapData()
                            fireStatusPulse(updated.lat, updated.lng, statusColorHex(updated.status))
                            showPinCard(updated, animateSwap = true)
                            Toast.makeText(this@MainActivity, "Status reset to Pending", Toast.LENGTH_SHORT).show()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            })
        }
        if (isOwner) {
            sheetContent.addView(MaterialButton(this).apply {
                text = "Delete Pin"
                textSize = 13.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setTextColor(Color.parseColor("#EF4444"))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
                setOnClickListener {
                    val delMsg = if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
                        "This removes the pin from this phone."
                    } else {
                        "This removes the pin for everyone in this folder."
                    }
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete \"${pin.name}\"?")
                        .setMessage(delMsg)
                        .setPositiveButton("Delete") { _, _ ->
                            deletePinUnified(pin)
                            hideInScreenSheet()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            })
        }

        if (animateSwap) {
            sheetContent.alpha = 0f
            sheetContent.translationX = dp(24).toFloat()
            sheetContent.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(200)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }

        val peekHeight = if (stackedPins.size > 1) 304 else 264
        showInScreenSheet(peekDp = peekHeight)
    }

    // --- Tap-Anywhere Place Card (With Route To & Route From) ---

    private fun showTappedPlaceCard(title: String, subtitle: String, lat: Double, lng: Double) {
        val night = isDarkMapUi()
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#0F172A")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val chipBg = Color.parseColor(if (night) "#334155" else "#F1F5F9")

        activeTappedLatLng = LatLng(lat, lng)
        tappedResolvedTitle = title
        tappedResolvedSub = subtitle
        sheetContent.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        tappedTitleView = TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        header.addView(tappedTitleView)
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_share)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setPadding(dp(7), dp(7), dp(7), dp(7))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener {
                val msg = makePinShareMessage(tappedResolvedTitle, lat, lng)
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, msg)
                }, "Share TerraSync Place"))
            }
        })
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(txtSub)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setPadding(dp(7), dp(7), dp(7), dp(7))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener { hideInScreenSheet() }
        })
        sheetContent.addView(header)

        val distText = currentLocation?.let {
            "${formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, lat, lng))} away"
        } ?: ""
        val subLine = listOf(subtitle, distText).filter { it.isNotBlank() }.joinToString(" • ")
        tappedSubView = TextView(this).apply {
            text = subLine
            textSize = 13f
            setTextColor(Color.parseColor("#2563EB"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(3))
            visibility = if (subLine.isNotBlank()) View.VISIBLE else View.GONE
        }
        sheetContent.addView(tappedSubView)
        sheetContent.addView(TextView(this).apply {
            text = "${"%.6f".format(lat)}, ${"%.6f".format(lng)}"
            textSize = 12.5f
            setTextColor(txtSub)
            setPadding(0, 0, 0, dp(12))
        })

        val routeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        routeRow.addView(MaterialButton(this).apply {
            text = "Route To"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { openRoutePreview(lat, lng, tappedResolvedTitle) }
        })
        routeRow.addView(MaterialButton(this).apply {
            text = "Route From"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0F766E"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { setRouteOrigin(lat, lng, tappedResolvedTitle) }
        })
        sheetContent.addView(routeRow)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(MaterialButton(this).apply {
            text = "Save Pin"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(txtPrimary)
            setBackgroundColor(chipBg)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                showAddPinDialog(LatLng(lat, lng), prefillName = if (tappedResolvedTitle == "Selected Location") "" else tappedResolvedTitle, prefillDesc = tappedResolvedSub)
            }
        })
        btnRow.addView(MaterialButton(this).apply {
            text = "Copy GPS"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(txtPrimary)
            setBackgroundColor(chipBg)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { copyToClipboard("GPS", "$lat, $lng") }
        })
        sheetContent.addView(btnRow)

        showInScreenSheet(peekDp = 230)
    }

    // --- Route Preview (Route From + Car / Bicycle / Walk Modes) & Active Navigation ---

    private fun openRoutePreview(destLat: Double, destLng: Double, destTitle: String, modeOverride: TravelMode = travelMode) {
        val customStart = routeOriginLatLng
        val startLat: Double
        val startLng: Double
        val startLabel: String

        var startBearing: Float? = null
        if (customStart != null) {
            startLat = customStart.latitude
            startLng = customStart.longitude
            startLabel = routeOriginName
        } else {
            if (!hasLocationPermission()) {
                locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return
            }
            if (!ensureLocationServiceOn()) return
            val staleStream = System.currentTimeMillis() - lastLiveLocationFixMs > 10_000L
            startLiveGpsUpdates(forceRestart = staleStream)
            val loc = currentLocation
            if (loc == null || locationAgeMs(loc) > 120_000L) {
                Toast.makeText(this, "Acquiring fresh GPS fix before building route...", Toast.LENGTH_SHORT).show()
                return
            }
            startLat = loc.latitude
            startLng = loc.longitude
            startLabel = "My Location"
            if (loc.hasBearing() && loc.speed > 1.5f) startBearing = loc.bearing
        }

        travelMode = modeOverride
        DevicePrefs.setTravelMode(this, modeOverride)
        val reqId = ++routeRequestSeq
        Toast.makeText(this, "Building ${modeOverride.label.lowercase()} route...", Toast.LENGTH_SHORT).show()

        RoutingEngine.fetchRoute(
            startLat, startLng, destLat, destLng, modeOverride, startBearing,
            isCancelled = { reqId != routeRequestSeq }
        ) { route, err ->
            runOnUiThread {
                if (isFinishing || isDestroyed || reqId != routeRequestSeq) return@runOnUiThread
                if (route == null) {
                    Toast.makeText(this, err ?: "Route failed", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                previewRoute = route
                navDestLatLng = LatLng(destLat, destLng)
                navDestName = destTitle
                renderMapData()

                val pts = route.geometry.map { LatLng(it.second, it.first) }
                if (pts.size >= 2) {
                    runCatching {
                        val b = LatLngBounds.Builder()
                        pts.forEach { b.include(it) }
                        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(48), dp(90), dp(48), dp(270)), 600)
                    }
                }
                showRoutePreviewCard(route, startLabel, destLat, destLng, destTitle)
            }
        }
    }

    private fun showRoutePreviewCard(route: NavRoute, startLabel: String, destLat: Double, destLng: Double, destTitle: String) {
        val night = isDarkMapUi()
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#0F172A")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val chipBg = Color.parseColor(if (night) "#334155" else "#F1F5F9")

        sheetContent.removeAllViews()

        val etaMin = (route.totalDurationSeconds / 60.0).toInt().coerceAtLeast(1)
        val distStr = formatDistanceShort(route.totalDistanceMeters)
        val modeColor = Color.parseColor(route.mode.routeColorHex)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        metaRow.addView(TextView(this).apply {
            text = "${route.mode.icon} $etaMin min"
            textSize = 15.5f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(10), dp(3), dp(10), dp(3))
            background = GradientDrawable().apply {
                setColor(modeColor)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) }
        })
        metaRow.addView(TextView(this).apply {
            text = distStr
            textSize = 15.5f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
        })
        val engColor = when {
            route.engineLabel.contains("Offline", true) -> "#16A34A"
            route.engineLabel.contains("Valhalla", true) -> "#2563EB"
            else -> "#475569"
        }
        metaRow.addView(TextView(this).apply {
            text = route.engineLabel
            textSize = 11f
            setTextColor(Color.parseColor(engColor))
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = GradientDrawable().apply {
                setColor(Color.parseColor(if (engColor == "#16A34A") "#DCFCE7" else if (engColor == "#2563EB") "#DBEAFE" else "#E2E8F0"))
                cornerRadius = dp(8).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        })
        titleCol.addView(metaRow)
        titleCol.addView(TextView(this).apply {
            text = "$startLabel → $destTitle"
            textSize = 12.5f
            setTextColor(txtSub)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(4), 0, 0)
        })
        header.addView(titleCol)

        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(txtSub)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener {
                routeRequestSeq++
                previewRoute = null
                renderMapData()
                hideInScreenSheet()
            }
        })
        sheetContent.addView(header)

        // Travel Mode Switcher (Car / Bicycle / Walk)
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
            }
        }
        TravelMode.entries.forEachIndexed { idx, mode ->
            val active = mode == route.mode
            modeRow.addView(MaterialButton(this).apply {
                text = "${mode.icon} ${mode.label}"
                textSize = 12.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                setTextColor(if (active) Color.WHITE else txtPrimary)
                setBackgroundColor(if (active) Color.parseColor(mode.routeColorHex) else chipBg)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
                    if (idx < TravelMode.entries.lastIndex) marginEnd = dp(6)
                }
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    if (mode != route.mode) openRoutePreview(destLat, destLng, destTitle, mode)
                }
            })
        }
        sheetContent.addView(modeRow)

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(MaterialButton(this).apply {
            text = if (routeOriginLatLng != null) "Navigate from GPS" else "Start Navigation"
            textSize = 13.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(modeColor)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1.4f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                if (routeOriginLatLng != null) {
                    clearRouteOrigin()
                    openRoutePreview(destLat, destLng, destTitle, route.mode)
                } else {
                    confirmStartNavigation(route, destLat, destLng, destTitle)
                }
            }
        })
        btnRow.addView(MaterialButton(this).apply {
            text = "External"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(txtPrimary)
            setBackgroundColor(chipBg)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 0.8f)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                val label = Uri.encode(destTitle)
                val geoUri = Uri.parse("geo:$destLat,$destLng?q=$destLat,$destLng($label)")
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, geoUri), "Open in"), "No external map app installed")
            }
        })
        sheetContent.addView(btnRow)

        showInScreenSheet(peekDp = 225)
    }

    private fun moveCameraForNavigation(loc: Location, durationMs: Int = 500) {
        if (!isActivityResumed) return
        val m = map ?: return
        val speedKmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
        val targetZoom = when {
            speedKmh >= 75f -> 15.6
            speedKmh >= 45f -> 16.3
            else -> 17.0
        }
        val camBearing = when {
            loc.hasBearing() && loc.speed > 1.0f -> loc.bearing.toDouble()
            displayedBearing != 0f -> displayedBearing.toDouble()
            else -> m.cameraPosition.bearing
        }
        val cam = CameraPosition.Builder()
            .target(LatLng(loc.latitude, loc.longitude))
            .zoom(targetZoom)
            .tilt(45.0)
            .bearing(camBearing)
            .padding(0.0, dp(200).toDouble(), 0.0, 0.0)
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), durationMs)
    }

    private fun confirmStartNavigation(route: NavRoute, destLat: Double, destLng: Double, destTitle: String) {
        if (!ensureLocationServiceOn()) return
        startLiveGpsUpdates(forceRestart = true)
        val loc = currentLocation
        if (loc == null) {
            Toast.makeText(this, "Waiting for GPS fix before starting navigation...", Toast.LENGTH_SHORT).show()
            return
        }
        activeNavRoute = route
        activeNavTrimmedGeom = route.geometry
        navLastSegIdx = 0
        isRerouting = false
        previewRoute = null
        navDestLatLng = LatLng(destLat, destLng)
        navDestName = destTitle
        navCurrentStepIdx = if (route.steps.size > 1) 1 else 0
        lastSpokenStepIdx = -1

        hideInScreenSheet()
        applyNavigationChrome(true)
        isFollowingGps = true
        updateLocationIconTint()
        updateSpeedBadge(loc)
        renderMapData()
        updateNavigationProgress(loc, route, LatLng(destLat, destLng))
        moveCameraForNavigation(loc, 650)
    }

    private fun updateNavigationProgress(loc: Location, route: NavRoute, dest: LatLng) {
        val directToDest = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, dest.latitude, dest.longitude)
        if (directToDest < 28.0) {
            speakPrompt("You have arrived at $navDestName")
            Toast.makeText(this, "Arrived at $navDestName", Toast.LENGTH_LONG).show()
            stopNavigation()
            return
        }

        val prog = RoutingEngine.trackRouteProgress(loc.latitude, loc.longitude, route, navLastSegIdx)
        navLastSegIdx = prog.segmentIndex
        activeNavTrimmedGeom = prog.remainingGeometry
        updateActiveRouteLine(prog.remainingGeometry)

        val remainingRoadMeters = prog.remainingMeters
        if (route.steps.isNotEmpty()) {
            val nextIdx = route.steps.indexOfFirst { it.routeDistMeters > prog.traveledMeters + 18.0 }
            navCurrentStepIdx = when {
                nextIdx >= 0 -> nextIdx.coerceAtLeast(if (route.steps.size > 1) 1 else 0)
                else -> route.steps.lastIndex
            }
        }

        val step = route.steps.getOrElse(navCurrentStepIdx) {
            NavStep("Continue to destination", "↑", remainingRoadMeters, dest.latitude, dest.longitude, prog.traveledMeters + remainingRoadMeters)
        }
        val distToStep = if (step.routeDistMeters > 0.0) {
            (step.routeDistMeters - prog.traveledMeters).coerceAtLeast(0.0)
        } else {
            RoutingEngine.haversineMeters(loc.latitude, loc.longitude, step.lat, step.lng)
        }

        val etaSec = if (route.totalDistanceMeters > 10.0 && route.totalDurationSeconds > 0.0) {
            (remainingRoadMeters / route.totalDistanceMeters) * route.totalDurationSeconds
        } else {
            (remainingRoadMeters / 1000.0) / 45.0 * 3600.0
        }
        val etaMin = (etaSec / 60.0).toInt().coerceAtLeast(1)

        tvNavArrow.text = step.arrow
        tvNavInstruction.text = if (isRerouting) "Recalculating route..." else step.instruction
        tvNavMeta.text = "${formatDistanceShort(distToStep)} • ${formatDistanceShort(remainingRoadMeters)} (${etaMin}m) • ${route.engineLabel}"

        val speakDist = if (loc.hasSpeed() && loc.speed > 16f) 450.0 else 280.0
        if (!isRerouting && navCurrentStepIdx != lastSpokenStepIdx && distToStep < speakDist) {
            lastSpokenStepIdx = navCurrentStepIdx
            speakPrompt("In ${formatDistanceShort(distToStep)}, ${step.instruction}")
        }

        if (isFollowingGps) {
            moveCameraForNavigation(loc, 450)
        }

        val now = System.currentTimeMillis()
        val rerouteThreshold = if (loc.hasAccuracy() && loc.accuracy > 20f) {
            (45.0 + loc.accuracy * 0.5).coerceAtMost(75.0)
        } else 45.0
        if (prog.offRouteMeters > rerouteThreshold && !isRerouting && now - lastRerouteTime > 7000L) {
            isRerouting = true
            lastRerouteTime = now
            tvNavInstruction.text = "Recalculating route..."
            speakPrompt("Recalculating route")
            val heading = if (loc.hasBearing() && loc.speed > 1.5f) loc.bearing else null
            val navReqId = ++routeRequestSeq
            RoutingEngine.fetchRoute(
                loc.latitude, loc.longitude, dest.latitude, dest.longitude, route.mode, heading,
                isCancelled = { activeNavRoute == null || navReqId != routeRequestSeq }
            ) { newRoute, _ ->
                runOnUiThread {
                    if (isFinishing || isDestroyed || navReqId != routeRequestSeq) return@runOnUiThread
                    isRerouting = false
                    val active = activeNavRoute ?: return@runOnUiThread
                    if (newRoute == null) {
                        val fallbackStep = active.steps.getOrNull(navCurrentStepIdx)
                        tvNavInstruction.text = fallbackStep?.instruction ?: "Continue to destination"
                        return@runOnUiThread
                    }
                    activeNavRoute = newRoute
                    activeNavTrimmedGeom = newRoute.geometry
                    navLastSegIdx = 0
                    navCurrentStepIdx = if (newRoute.steps.size > 1) 1 else 0
                    lastSpokenStepIdx = -1
                    renderMapData()
                    currentLocation?.let { latest ->
                        updateNavigationProgress(latest, newRoute, dest)
                    }
                }
            }
        }
    }

    private fun applyNavigationChrome(inNav: Boolean) {
        if (inNav) {
            if (isLassoMode) toggleLassoMode(false)
            if (isRulerMode) toggleRulerMode(false)
            UiAnimations.hideSlideTop(topCluster)
            UiAnimations.showSlideTop(navBanner)
            bottomIconRow.visibility = View.GONE
            btnRuler.visibility = View.GONE
            btnLasso.visibility = View.GONE
            btnImport.visibility = View.GONE
            UiAnimations.fadeShow(bottomDock, 180L)
        } else {
            UiAnimations.hideSlideTop(navBanner)
            UiAnimations.showSlideTop(topCluster)
            btnRuler.visibility = View.VISIBLE
            applyExtraMapToolsVisibility()
            if (!isRulerMode && !isLassoMode && sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                UiAnimations.showSlideBottom(bottomIconRow)
            }
        }
    }

    private fun stopNavigation() {
        routeRequestSeq++
        activeNavRoute = null
        activeNavTrimmedGeom = null
        navLastSegIdx = 0
        isRerouting = false
        previewRoute = null
        navDestLatLng = null
        navCurrentStepIdx = 0
        applyNavigationChrome(false)
        updateSpeedBadge(currentLocation)
        renderMapData()
        map?.let { m ->
            val cam = CameraPosition.Builder()
                .target(m.cameraPosition.target)
                .zoom(m.cameraPosition.zoom)
                .tilt(0.0)
                .bearing(0.0)
                .padding(0.0, 0.0, 0.0, 0.0)
                .build()
            m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 400)
        }
    }

    private fun speakPrompt(text: String) {
        if (ttsReady && DevicePrefs.isVoiceNav(this)) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_prompt")
        }
    }

    // --- Live GPS, Location-Off Prompt & Driving Speed Indicator ---

    private fun hasLocationPermission(): Boolean {
        val f = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val c = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return f == PackageManager.PERMISSION_GRANTED || c == PackageManager.PERMISSION_GRANTED
    }

    private fun ensureLocationServiceOn(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (LocationManagerCompat.isLocationEnabled(lm)) return true

        AlertDialog.Builder(this)
            .setTitle("Location is turned off")
            .setMessage("Turn on your phone's Location / GPS to find your position, measure speed, and start navigation.")
            .setPositiveButton("Turn On") { _, _ ->
                runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            }
            .setNegativeButton("Cancel", null)
            .show()
        return false
    }

    private fun locationAgeMs(loc: Location): Long {
        return if (loc.elapsedRealtimeNanos > 0L) {
            ((android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
        } else {
            (System.currentTimeMillis() - loc.time).coerceAtLeast(0L)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLiveGpsUpdates(forceRestart: Boolean = false) {
        if (forceRestart) stopLiveGpsUpdates()
        if (gpsTrackingActive || !hasLocationPermission()) return
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
        }

        val bestLast = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { locationAgeMs(it) <= 120_000L }
            .minByOrNull { locationAgeMs(it) }
        if (bestLast != null && (currentLocation == null || locationAgeMs(bestLast) < locationAgeMs(currentLocation!!))) {
            currentLocation = bestLast
            displayedLat = bestLast.latitude
            displayedLng = bestLast.longitude
            if (bestLast.hasBearing()) displayedBearing = bestLast.bearing
            updateGpsLayerFeature(displayedLat, displayedLng, displayedBearing)
            updateSpeedBadge(bestLast)
        }

        var registeredAny = false
        providers.forEach { provider ->
            runCatching {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 600L, 0f, gpsListener)
                    registeredAny = true
                }
            }
        }
        gpsTrackingActive = registeredAny
    }

    private fun stopLiveGpsUpdates() {
        if (!gpsTrackingActive) return
        runCatching {
            val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.removeUpdates(gpsListener)
        }
        gpsTrackingActive = false
    }

    private fun isBetterLocation(candidate: Location, current: Location?): Boolean {
        val now = System.currentTimeMillis()
        val isGps = candidate.provider == LocationManager.GPS_PROVIDER
        val isFused = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
            candidate.provider == LocationManager.FUSED_PROVIDER
        if (isGps) {
            lastGpsFixTimeMs = now
        } else if (!isFused && now - lastGpsFixTimeMs < 10_000L) {
            return false
        } else if (isFused && now - lastGpsFixTimeMs < 3_000L && candidate.hasAccuracy() && candidate.accuracy > 25f) {
            return false
        }
        if (current == null) return true
        val candAge = locationAgeMs(candidate)
        val currAge = locationAgeMs(current)
        if (candAge > currAge + 3_000L) return false
        val isSignificantlyNewer = currAge - candAge > 5_000L
        if (!isSignificantlyNewer && candidate.hasAccuracy() && current.hasAccuracy()) {
            if (!isGps && !isFused && candidate.accuracy > current.accuracy + 25f) return false
            if ((isGps || isFused) && candidate.accuracy > 80f && current.accuracy < 25f) return false
        }
        return true
    }

    private fun updateSpeedBadge(loc: Location?) {
        if (!DevicePrefs.isSpeedIndicatorEnabled(this)) {
            speedBadge.visibility = View.GONE
            return
        }
        val reliableSpeed = loc != null && loc.hasSpeed() &&
            (loc.provider == LocationManager.GPS_PROVIDER || !loc.hasAccuracy() || loc.accuracy <= 35f)
        val rawKmh = if (reliableSpeed) (loc!!.speed * 3.6f).roundToInt().coerceAtLeast(0) else 0
        val kmh = if (rawKmh < 2) 0 else rawKmh
        val shouldShow = activeNavRoute != null || kmh >= 4
        if (shouldShow) {
            tvSpeedValue.text = "$kmh"
            tvSpeedUnit.text = "km/h"
            speedBadge.visibility = View.VISIBLE
        } else {
            speedBadge.visibility = View.GONE
        }
    }

    private fun onNewLocation(loc: Location) {
        if (!isBetterLocation(loc, currentLocation)) return
        lastLiveLocationFixMs = System.currentTimeMillis()
        val prevLoc = currentLocation
        val prevLat = if (prevLoc == null) loc.latitude else displayedLat
        val prevLng = if (prevLoc == null) loc.longitude else displayedLng
        val prevBearing = displayedBearing
        val targetBearing = when {
            loc.hasBearing() && loc.speed > 0.8f -> loc.bearing
            prevLoc != null && prevLoc.distanceTo(loc) > 3.5f -> (prevLoc.bearingTo(loc) + 360f) % 360f
            else -> prevBearing
        }
        var deltaBearing = (targetBearing - prevBearing) % 360f
        if (deltaBearing > 180f) deltaBearing -= 360f
        if (deltaBearing < -180f) deltaBearing += 360f

        currentLocation = loc
        updateSpeedBadge(loc)

        gpsAnim?.cancel()
        if (isActivityResumed) {
            gpsAnim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 450L
                interpolator = DecelerateInterpolator()
                addUpdateListener { anim ->
                    if (!isActivityStarted) {
                        anim.cancel()
                        return@addUpdateListener
                    }
                    val t = anim.animatedValue as Float
                    displayedLat = prevLat + (loc.latitude - prevLat) * t
                    displayedLng = prevLng + (loc.longitude - prevLng) * t
                    displayedBearing = (prevBearing + deltaBearing * t + 360f) % 360f
                    updateGpsLayerFeature(displayedLat, displayedLng, displayedBearing)
                }
                start()
            }
        } else {
            displayedLat = loc.latitude
            displayedLng = loc.longitude
            displayedBearing = targetBearing
        }

        val activeRoute = activeNavRoute
        val dest = navDestLatLng
        if (activeRoute != null && dest != null) {
            updateNavigationProgress(loc, activeRoute, dest)
        } else if (isActivityResumed && isFollowingGps && DevicePrefs.isAutoFollow(this)) {
            map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude)), 600)
        }
    }

    private fun updateLocationIconTint() {
        val night = isDarkMapUi()
        val idle = if (night) "#F1F5F9" else "#334155"
        btnLocate.setColorFilter(Color.parseColor(if (isFollowingGps) "#2563EB" else idle))
    }

    private fun updateFilterIconTint() {
        val hex = when {
            visibleStatuses.containsAll(ALL_STATUSES) && !hideSentPins ->
                if (isDarkMapUi()) "#F1F5F9" else "#334155"
            visibleStatuses == setOf("pending") && !hideSentPins -> "#F59E0B"
            visibleStatuses == setOf("issue") && !hideSentPins -> "#EF4444"
            visibleStatuses == setOf("done") && !hideSentPins -> "#22C55E"
            else -> "#2563EB"
        }
        btnStatusFilter.setColorFilter(Color.parseColor(hex))
    }

    private fun updateScaleBar() {
        val m = map ?: return
        val center = m.cameraPosition.target ?: return
        val mpp = m.projection.getMetersPerPixelAtLatitude(center.latitude)
        if (mpp <= 0.0) return
        val density = resources.displayMetrics.density
        val maxPx = 110f * density
        val maxMeters = mpp * maxPx

        val steps = doubleArrayOf(5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0, 10000.0, 20000.0, 50000.0, 100000.0, 200000.0)
        val chosen = steps.lastOrNull { it <= maxMeters } ?: 5.0
        val widthPx = (chosen / mpp).toInt().coerceIn(dp(32), dp(120))

        tvScaleLabel.text = if (chosen >= 1000.0) "${(chosen / 1000.0).toInt()} km" else "${chosen.toInt()} m"
        if (scaleBarLine.layoutParams.width != widthPx) {
            scaleBarLine.layoutParams.width = widthPx
            scaleBarLine.requestLayout()
        }
    }

    // --- Full-Screen Overlay Page Host ---

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        val token = currentFocus?.windowToken ?: window.decorView.windowToken
        imm?.hideSoftInputFromWindow(token, 0)
        currentFocus?.clearFocus()
    }

    private fun openFullPage(builder: () -> View) {
        pageOverlayContainer.animate().cancel()
        activePageCleanup?.invoke()
        activePageCleanup = null
        liveBookmarksRefresh = null
        livePinListRefresh = null
        hideSoftKeyboard()
        pageBackStack.clear()
        pageOverlayContainer.removeAllViews()
        pageOverlayContainer.addView(builder())
        pageOverlayContainer.visibility = View.VISIBLE
        pageOverlayContainer.alpha = 0f
        pageOverlayContainer.translationY = dp(24).toFloat()
        pageOverlayContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun pushSubPage(onBack: () -> Unit, builder: () -> View) {
        pageOverlayContainer.animate().cancel()
        activePageCleanup?.invoke()
        activePageCleanup = null
        liveBookmarksRefresh = null
        livePinListRefresh = null
        hideSoftKeyboard()
        pageBackStack.add(onBack)
        pageOverlayContainer.removeAllViews()
        val v = builder()
        pageOverlayContainer.addView(v)
        v.alpha = 0f
        v.translationX = dp(24).toFloat()
        v.animate().alpha(1f).translationX(0f).setDuration(190).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun closeFullPage() {
        pageOverlayContainer.animate().cancel()
        activePageCleanup?.invoke()
        activePageCleanup = null
        webSearchSeq++
        liveBookmarksRefresh = null
        livePinListRefresh = null
        offlineMaps.setStateListener(null)
        hideSoftKeyboard()
        pageBackStack.clear()
        if (pageOverlayContainer.visibility != View.VISIBLE) {
            pageOverlayContainer.removeAllViews()
            return
        }
        pageOverlayContainer.animate()
            .alpha(0f)
            .translationY(dp(20).toFloat())
            .setDuration(170)
            .withEndAction {
                pageOverlayContainer.visibility = View.GONE
                pageOverlayContainer.alpha = 1f
                pageOverlayContainer.translationY = 0f
                pageOverlayContainer.removeAllViews()
            }
            .start()
    }

    private fun makePageFrame(title: String, onBack: () -> Unit, rightAction: View? = null): Pair<LinearLayout, LinearLayout> {
        val night = isDarkMapUi()
        val bg = Color.parseColor(if (night) "#0F172A" else "#FFFFFF")
        val txt = Color.parseColor(if (night) "#F8FAFC" else "#0F172A")

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(0, statusBarInsetTop, 0, maxOf(navBarInsetBottom, imeInsetBottom))
        }

        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(12), dp(8))
        }
        appBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(txt)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { onBack() }
        })
        appBar.addView(TextView(this).apply {
            text = title
            textSize = 20f
            setTextColor(txt)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        })
        if (rightAction != null) appBar.addView(rightAction)
        outer.addView(appBar)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(28))
        }
        scroll.addView(body)
        outer.addView(scroll)
        return outer to body
    }

    // --- Page 2: Unified Bookmarks (KML Folders + Cross-Folder Search + Bottom Toggles) ---

    private fun buildBookmarksAndTracksPage(): View {
        val night = isDarkMapUi()
        val bg = Color.parseColor(if (night) "#0F172A" else "#FFFFFF")
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val cardBg = Color.parseColor(if (night) "#1E293B" else "#F3F4F6")
        val dividerColor = Color.parseColor(if (night) "#334155" else "#E2E8F0")

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(0, statusBarInsetTop, 0, maxOf(navBarInsetBottom, imeInsetBottom))
        }

        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(12), dp(6))
        }
        appBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(txtPrimary)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { closeFullPage() }
        })
        appBar.addView(TextView(this).apply {
            text = "Bookmarks"
            textSize = 20f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(8)
            }
        })
        appBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_plus)
            setColorFilter(txtPrimary)
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { anchor ->
                val menu = PopupMenu(this@MainActivity, anchor)
                menu.menu.add(0, 1, 1, "Import KML / KMZ Folder")
                menu.menu.add(0, 2, 2, "Join Folder by Share Code")
                menu.menu.add(0, 3, 3, "Combine Folders into One")
                menu.menu.add(0, 4, 4, "Nearest Pending Pin")
                menu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { closeFullPage(); kmlPicker.launch(arrayOf("*/*")) }
                        2 -> showJoinCodeDialog()
                        3 -> showCombineFoldersDialog()
                        4 -> jumpToNearestPendingPin()
                    }
                    true
                }
                menu.show()
            }
        })
        outer.addView(appBar)

        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                bottomMargin = dp(8)
            }
        }
        searchWrap.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(txtSub)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(8) }
        })
        val etFolderSearch = EditText(this).apply {
            hint = "Search across all folders (KMLs)..."
            textSize = 14f
            setTextColor(txtPrimary)
            setHintTextColor(txtSub)
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                    hideSoftKeyboard()
                    true
                } else false
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val btnClearSearch = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(txtSub)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            setPadding(dp(7), dp(7), dp(7), dp(7))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener { etFolderSearch.setText("") }
        }
        searchWrap.addView(etFolderSearch)
        searchWrap.addView(btnClearSearch)
        outer.addView(searchWrap)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(16))
        }
        scroll.addView(body)
        outer.addView(scroll)

        outer.addView(View(this).apply {
            setBackgroundColor(dividerColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
        })
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(18), dp(10), dp(18), dp(12))
        }

        var suppressAllSwitch = false
        val swShowAll = MaterialSwitch(this)

        val rowShowAll = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(6))
        }
        val showAllCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        showAllCol.addView(TextView(this).apply {
            text = "Show all bookmarks on map"
            textSize = 15f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
        })
        val tvTotalBookmarksCount = TextView(this).apply {
            text = "${pins.size} bookmarks"
            textSize = 12.5f
            setTextColor(txtSub)
        }
        showAllCol.addView(tvTotalBookmarksCount)
        rowShowAll.addView(showAllCol)
        rowShowAll.addView(swShowAll)
        footer.addView(rowShowAll)

        val rowShowTitles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        rowShowTitles.addView(TextView(this).apply {
            text = "Show bookmark titles"
            textSize = 15f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        rowShowTitles.addView(MaterialSwitch(this).apply {
            isChecked = DevicePrefs.isShowBookmarkTitles(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                DevicePrefs.setShowBookmarkTitles(this@MainActivity, checked)
                renderMapData()
            }
        })
        footer.addView(rowShowTitles)
        outer.addView(footer)

        val myDevId = DevicePrefs.getDeviceId(this)

        fun syncMasterVisibilitySwitch() {
            val allVisible = layers.isNotEmpty() && layers.all { DevicePrefs.isLayerVisible(this, it.id) }
            suppressAllSwitch = true
            swShowAll.isChecked = allVisible
            suppressAllSwitch = false
        }

        fun renderFoldersOrSearch(query: String) {
            val folderNameById = layers.associate { it.id to it.name }
            tvTotalBookmarksCount.text = "${pins.size} bookmarks"
            body.removeAllViews()
            val trimmed = query.trim()

            if (trimmed.isNotEmpty()) {
                val matches = pins.filter { p ->
                    val fName = folderNameById[p.layerId] ?: ""
                    p.name.contains(trimmed, true) ||
                        p.description.contains(trimmed, true) ||
                        p.issueReason.contains(trimmed, true) ||
                        fName.contains(trimmed, true) ||
                        statusLabel(p.status).contains(trimmed, true) ||
                        "${"%.6f".format(p.lat)}, ${"%.6f".format(p.lng)}".contains(trimmed, true)
                }

                body.addView(TextView(this).apply {
                    text = "${matches.size} match${if (matches.size == 1) "" else "es"} across all folders"
                    textSize = 13f
                    setTextColor(txtSub)
                    setTypeface(null, Typeface.BOLD)
                    setPadding(dp(4), dp(4), dp(4), dp(8))
                })

                if (matches.isEmpty()) {
                    body.addView(TextView(this).apply {
                        text = "No bookmarks matched \"$trimmed\"."
                        textSize = 14f
                        setTextColor(txtSub)
                        setPadding(dp(4), dp(12), dp(4), dp(12))
                    })
                    return
                }

                val resultsCard = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        setColor(cardBg)
                        cornerRadius = dp(18).toFloat()
                    }
                    clipToOutline = true
                }
                val loc = currentLocation
                matches.take(120).forEachIndexed { idx, pin ->
                    if (idx > 0) {
                        resultsCard.addView(View(this).apply {
                            setBackgroundColor(dividerColor)
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                                marginStart = dp(16)
                                marginEnd = dp(16)
                            }
                        })
                    }
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        UiAnimations.applyRowRipple(this@MainActivity, this, night)
                        setOnClickListener {
                            ensurePinVisibleOnMap(pin)
                            closeFullPage()
                            selectPinAndOpenCard(pin, zoomIfFar = true)
                        }
                    }
                    row.addView(View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor(statusColorHex(pin.status)))
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(11), dp(11)).apply { marginEnd = dp(12) }
                    })
                    val col = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(8)
                        }
                    }
                    col.addView(TextView(this).apply {
                        text = pin.name
                        textSize = 14.5f
                        setTextColor(txtPrimary)
                        setTypeface(null, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    val fName = folderNameById[pin.layerId] ?: "Folder"
                    val sub = buildString {
                        append(fName)
                        append(" • ")
                        append(statusLabel(pin.status))
                        if (pin.status.equals("issue", true) && pin.issueReason.isNotBlank()) append(" • ⚠ ${pin.issueReason}")
                        else if (pin.status.equals("done", true) && pin.issueReason.isNotBlank()) append(" • ✓ ${pin.issueReason}")
                    }
                    col.addView(TextView(this).apply {
                        text = sub
                        textSize = 12f
                        setTextColor(
                            when {
                                pin.status.equals("issue", true) -> Color.parseColor("#DC2626")
                                pin.status.equals("done", true) && pin.issueReason.isNotBlank() -> Color.parseColor("#15803D")
                                else -> txtSub
                            }
                        )
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    })
                    row.addView(col)

                    if (loc != null) {
                        val d = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, pin.lat, pin.lng)
                        row.addView(TextView(this).apply {
                            text = formatDistanceShort(d)
                            textSize = 12f
                            setTextColor(Color.parseColor("#2563EB"))
                            setTypeface(null, Typeface.BOLD)
                        })
                    }
                    resultsCard.addView(row)
                }
                body.addView(resultsCard)
                return
            }

            val bookmarkedPins = pins.filter { it.id in DevicePrefs.getBookmarks(this) }

            body.addView(TextView(this).apply {
                text = "${layers.size} folder${if (layers.size == 1) "" else "s"}"
                textSize = 13.5f
                setTextColor(txtSub)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(4), dp(4), dp(4), dp(8))
            })

            if (layers.isEmpty() && bookmarkedPins.isEmpty()) {
                body.addView(TextView(this).apply {
                    text = "No bookmark folders yet. Tap + above to import a KML/KMZ file or join a shared folder."
                    textSize = 14f
                    setTextColor(txtSub)
                    setPadding(dp(6), dp(18), dp(6), dp(18))
                })
                return
            }

            val listCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(cardBg)
                    cornerRadius = dp(18).toFloat()
                }
                clipToOutline = true
            }

            var rowIndex = 0
            fun addDividerIfNeeded() {
                if (rowIndex > 0) {
                    listCard.addView(View(this).apply {
                        setBackgroundColor(dividerColor)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                            marginStart = dp(18)
                            marginEnd = dp(18)
                        }
                    })
                }
                rowIndex++
            }

            if (bookmarkedPins.isNotEmpty()) {
                addDividerIfNeeded()
                val starRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(14), dp(10), dp(14))
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    setOnClickListener {
                        pushSubPage({ openFullPage(::buildBookmarksAndTracksPage) }) {
                            buildPinsListPage("Starred", bookmarkedPins, null)
                        }
                    }
                }
                starRow.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_star_filled)
                    layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(14) }
                })
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                col.addView(TextView(this).apply {
                    text = "Starred"
                    textSize = 15.5f
                    setTextColor(txtPrimary)
                    setTypeface(null, Typeface.BOLD)
                })
                col.addView(TextView(this).apply {
                    text = "${bookmarkedPins.size} bookmarks"
                    textSize = 12.5f
                    setTextColor(txtSub)
                })
                starRow.addView(col)
                listCard.addView(starRow)
            }

            val orderedLayers = layers.sortedByDescending { DevicePrefs.isLayerPinned(this, it.id) }
            orderedLayers.forEach { layer ->
                addDividerIfNeeded()
                val layerPins = pins.filter { it.layerId == layer.id }
                val layerTracks = tracks.filter { it.layerId == layer.id }
                val visible = DevicePrefs.isLayerVisible(this, layer.id)
                val isPinned = DevicePrefs.isLayerPinned(this, layer.id)
                val isLocal = LocalFolderStore.isLocalLayerId(layer.id)
                val isCreator = isLayerOwner(layer)

                val doneCnt = layerPins.count { it.status.equals("done", true) }
                val issueCnt = layerPins.count { it.status.equals("issue", true) }
                val pendingCnt = (layerPins.size - doneCnt - issueCnt).coerceAtLeast(0)

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(12), dp(6), dp(12))
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                }

                row.addView(ImageButton(this).apply {
                    setImageResource(if (visible) R.drawable.ic_eye_on else R.drawable.ic_eye_off)
                    layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
                    setPadding(dp(9), dp(9), dp(9), dp(9))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    UiAnimations.attachSpringTouch(this)
                    setOnClickListener {
                        val next = !DevicePrefs.isLayerVisible(this@MainActivity, layer.id)
                        DevicePrefs.setLayerVisible(this@MainActivity, layer.id, next)
                        setImageResource(if (next) R.drawable.ic_eye_on else R.drawable.ic_eye_off)
                        syncMasterVisibilitySwitch()
                        renderMapData()
                    }
                })

                val infoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(6)
                        marginEnd = dp(4)
                    }
                    setOnClickListener {
                        pushSubPage({ openFullPage(::buildBookmarksAndTracksPage) }) {
                            buildPinsListPage(layer.name, layerPins, layer)
                        }
                    }
                }
                val titleRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                titleRow.addView(TextView(this).apply {
                    text = layer.name
                    textSize = 15.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (isLocal) {
                    titleRow.addView(TextView(this).apply {
                        text = "LOCAL"
                        textSize = 10f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#0F766E"))
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#CCFBF1"))
                            cornerRadius = dp(6).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(6) }
                    })
                }
                if (isPinned) {
                    titleRow.addView(TextView(this).apply {
                        text = "📌 PINNED"
                        textSize = 10f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#B45309"))
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#FEF3C7"))
                            cornerRadius = dp(6).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(6) }
                    })
                }
                infoCol.addView(titleRow)
                infoCol.addView(TextView(this).apply {
                    val trackStr = if (layerTracks.isNotEmpty()) " • ${layerTracks.size} tracks" else ""
                    text = "${layerPins.size} bookmarks$trackStr   ✓$doneCnt  •$pendingCnt  !$issueCnt"
                    textSize = 12.5f
                    setTextColor(txtSub)
                    setPadding(0, dp(2), 0, dp(5))
                })

                if (layerPins.isNotEmpty()) {
                    val bar = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(4))
                        background = GradientDrawable().apply {
                            setColor(dividerColor)
                            cornerRadius = dp(2).toFloat()
                        }
                        clipToOutline = true
                    }
                    if (doneCnt > 0) bar.addView(View(this).apply {
                        setBackgroundColor(Color.parseColor("#22C55E"))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, doneCnt.toFloat())
                    })
                    if (pendingCnt > 0) bar.addView(View(this).apply {
                        setBackgroundColor(Color.parseColor("#F59E0B"))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pendingCnt.toFloat())
                    })
                    if (issueCnt > 0) bar.addView(View(this).apply {
                        setBackgroundColor(Color.parseColor("#EF4444"))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, issueCnt.toFloat())
                    })
                    infoCol.addView(bar)
                }
                row.addView(infoCol)

                row.addView(ImageButton(this).apply {
                    setImageResource(R.drawable.ic_more_vert)
                    layoutParams = LinearLayout.LayoutParams(dp(40), dp(42))
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    setOnClickListener { anchor ->
                        val menu = PopupMenu(this@MainActivity, anchor)
                        if (isLocal) {
                            menu.menu.add(0, 10, 1, "Upload to Cloud (Shareable)")
                        } else {
                            menu.menu.add(0, 1, 1, "Share Link & Code (${layer.shareCode})")
                        }
                        menu.menu.add(0, 2, 2, "Zoom to Folder")
                        menu.menu.add(0, 3, 3, if (isPinned) "Unpin Folder (Unlock)" else "Pin & Protect Folder")
                        menu.menu.add(0, 4, 4, "Daily Team Report (WhatsApp)")
                        menu.menu.add(0, 5, 5, "Export as KML...")
                        menu.menu.add(0, 6, 6, "Export as CSV")
                        if (isCreator) menu.menu.add(0, 7, 7, "Rename Folder")
                        if (isLocal) {
                            menu.menu.add(0, 11, 8, if (isPinned) "🔒 Delete Local Folder (Pinned)" else "Delete Local Folder")
                        } else {
                            menu.menu.add(0, 8, 8, if (isPinned) "🔒 Leave Folder (Pinned)" else "Leave Folder (This Phone)")
                            if (isCreator) menu.menu.add(0, 9, 9, if (isPinned) "🔒 Delete from Cloud (Pinned)" else "Delete from Cloud (All Users)")
                        }
                        menu.setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                1 -> shareLayerLink(layer)
                                2 -> { closeFullPage(); zoomToPoints(layerPins.map { LatLng(it.lat, it.lng) }) }
                                3 -> {
                                    val nowPinned = DevicePrefs.toggleLayerPinned(this@MainActivity, layer.id)
                                    Toast.makeText(
                                        this@MainActivity,
                                        if (nowPinned) "Pinned & protected \"${layer.name}\"" else "Unpinned \"${layer.name}\"",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    renderFoldersOrSearch(etFolderSearch.text?.toString() ?: "")
                                }
                                4 -> showDailyWhatsAppReportDialog(layer, layerPins, layerTracks)
                                5 -> showExportKmlDialog(layer, layerPins, layerTracks)
                                6 -> KmlCsvExporter.exportAndShareCsv(this@MainActivity, layer, layerPins)
                                7 -> showRenameFolderDialog(layer)
                                8 -> {
                                    if (isPinned) {
                                        Toast.makeText(this@MainActivity, "Folder is pinned. Unpin it first to leave.", Toast.LENGTH_LONG).show()
                                    } else {
                                        DevicePrefs.removeJoinedLayerId(this@MainActivity, layer.id)
                                        refreshJoinedSync()
                                        Toast.makeText(this@MainActivity, "Left \"${layer.name}\"", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                9 -> {
                                    if (isPinned) {
                                        Toast.makeText(this@MainActivity, "Folder is pinned. Unpin it first to delete.", Toast.LENGTH_LONG).show()
                                    } else {
                                        showDeleteCloudDialog(layer, layerPins, layerTracks)
                                    }
                                }
                                10 -> confirmUploadLocalToCloud(layer, layerPins, layerTracks)
                                11 -> {
                                    if (isPinned) {
                                        Toast.makeText(this@MainActivity, "Folder is pinned. Unpin it first to delete.", Toast.LENGTH_LONG).show()
                                    } else {
                                        showDeleteLocalDialog(layer, layerPins.size)
                                    }
                                }
                            }
                            true
                        }
                        menu.show()
                    }
                })

                listCard.addView(row)
            }

            body.addView(listCard)
        }

        syncMasterVisibilitySwitch()
        swShowAll.setOnCheckedChangeListener { _, checked ->
            if (suppressAllSwitch) return@setOnCheckedChangeListener
            DevicePrefs.setAllLayersVisible(this, layers.map { it.id }, checked)
            renderMapData()
            renderFoldersOrSearch(etFolderSearch.text?.toString() ?: "")
        }

        var searchRunnable: Runnable? = null
        etFolderSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString() ?: ""
                btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                searchRunnable?.let { etFolderSearch.removeCallbacks(it) }
                val r = Runnable { renderFoldersOrSearch(q) }
                searchRunnable = r
                etFolderSearch.postDelayed(r, if (q.isEmpty()) 0L else 120L)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        liveBookmarksRefresh = {
            syncMasterVisibilitySwitch()
            renderFoldersOrSearch(etFolderSearch.text?.toString() ?: "")
        }
        activePageCleanup = {
            searchRunnable?.let { etFolderSearch.removeCallbacks(it) }
        }
        renderFoldersOrSearch("")
        return outer
    }

    private fun showExportKmlDialog(layer: LayerItem, layerPins: List<PinItem>, layerTracks: List<TrackItem>) {
        val sentIds = DevicePrefs.getLocalSentPinIds(this)
        val sentInLayer = layerPins.count { it.id in sentIds }
        val unsentPins = layerPins.filter { it.id !in sentIds }
        val pendingPins = layerPins.filter { it.status.equals("pending", true) }
        val pendingUnsentPins = pendingPins.filter { it.id !in sentIds }
        val issuePins = layerPins.filter { it.status.equals("issue", true) }
        val donePins = layerPins.filter { it.status.equals("done", true) }

        val entries = mutableListOf(
            Triple("All Pins (${layerPins.size})", layerPins, ""),
            Triple("Done Only (${donePins.size})", donePins, "Done"),
            Triple("Pending Only (${pendingPins.size})", pendingPins, "Pending"),
            Triple("Issues Only (${issuePins.size})", issuePins, "Issues")
        )
        if (sentInLayer > 0) {
            entries.add(Triple("Unsent Only (${unsentPins.size})", unsentPins, "Unsent"))
            entries.add(Triple("Pending & Unsent (${pendingUnsentPins.size})", pendingUnsentPins, "Pending_Unsent"))
        }

        val labels = entries.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Export KML: ${layer.name}")
            .setItems(labels) { _, which ->
                val (_, subset, suffix) = entries[which]
                val tracksToExport = if (suffix.isEmpty()) layerTracks else emptyList()
                if (subset.isEmpty() && tracksToExport.isEmpty()) {
                    Toast.makeText(this, "No matching pins to export", Toast.LENGTH_SHORT).show()
                } else {
                    KmlCsvExporter.exportAndShareKml(this, layer, subset, tracksToExport, suffix)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDailyWhatsAppReportDialog(layer: LayerItem, layerPins: List<PinItem>, layerTracks: List<TrackItem>) {
        val report = DailyReportGenerator.buildReport(layer.name, layerPins, !LocalFolderStore.isLocalLayerId(layer.id))
        val scroll = android.widget.ScrollView(this).apply {
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        scroll.addView(TextView(this).apply {
            text = report.whatsappText
            textSize = 13.5f
            setTextIsSelectable(true)
            setLineSpacing(dp(2).toFloat(), 1.0f)
        })
        AlertDialog.Builder(this)
            .setTitle("End-of-Day Team Report")
            .setView(scroll)
            .setPositiveButton("Share to WhatsApp") { _, _ ->
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_TEXT, report.whatsappText)
                }
                safeStartActivity(android.content.Intent.createChooser(share, "Share Daily Report"))
            }
            .setNeutralButton("Copy") { _, _ ->
                copyToClipboard("Daily Report", report.whatsappText)
            }
            .setNegativeButton("Share KML + Text") { _, _ ->
                KmlCsvExporter.exportAndShareKml(this, layer, layerPins, layerTracks, "", report.whatsappText)
            }
            .show()
    }

    private fun showRenameFolderDialog(layer: LayerItem) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val etName = EditText(this).apply {
            hint = "Folder name"
            setText(layer.name)
            setSingleLine(true)
            setSelection(layer.name.length)
        }
        box.addView(etName)

        AlertDialog.Builder(this)
            .setTitle("Rename Folder")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val next = etName.text.toString().trim()
                if (next.isBlank()) {
                    Toast.makeText(this, "Folder name cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    if (LocalFolderStore.isLocalLayerId(layer.id)) {
                        LocalFolderStore.renameLayer(this, layer.id, next)
                        applyMergedFoldersAndRender()
                    } else {
                        repo.renameLayer(layer.id, next, ::notifySyncError)
                    }
                    Toast.makeText(this, "Renamed to \"$next\"", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteCloudDialog(layer: LayerItem, layerPins: List<PinItem>, layerTracks: List<TrackItem>) {
        AlertDialog.Builder(this)
            .setTitle("Delete \"${layer.name}\" from Cloud?")
            .setMessage("This permanently removes all ${layerPins.size} bookmarks for every team member.")
            .setPositiveButton("Delete Cloud") { _, _ ->
                val deletedPinIds = layerPins.map { it.id }
                DevicePrefs.removeJoinedLayerId(this, layer.id)
                DevicePrefs.removePinReferences(this, deletedPinIds)
                repo.deleteLayer(layer.id, deletedPinIds, layerTracks.map { it.id }, ::notifySyncError)
                refreshJoinedSync()
                Toast.makeText(this, "Deleted \"${layer.name}\" from cloud", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // --- Subpage: Folder Bookmarks List ---

    private fun buildPinsListPage(title: String, sourcePins: List<PinItem>, layer: LayerItem?): View {
        val night = isDarkMapUi()
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val cardBg = Color.parseColor(if (night) "#1E293B" else "#F3F4F6")
        val dividerColor = Color.parseColor(if (night) "#334155" else "#E2E8F0")

        fun currentSourcePins(): List<PinItem> = when {
            layer != null -> pins.filter { it.layerId == layer.id }
            title == "Starred" -> pins.filter { it.id in DevicePrefs.getBookmarks(this) }
            else -> sourcePins
        }

        val btnZoom = MaterialButton(this).apply {
            text = "Zoom"
            textSize = 12.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(10)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                val targetPts = currentSourcePins().map { LatLng(it.lat, it.lng) }
                closeFullPage()
                zoomToPoints(targetPts)
            }
        }

        val (outer, body) = makePageFrame(title, {
            if (pageBackStack.isNotEmpty()) pageBackStack.removeAt(pageBackStack.lastIndex).invoke() else closeFullPage()
        }, btnZoom)

        var sortNearest = currentLocation != null
        var filterKey = "all"

        val controlsScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(10))
        }
        val btnSort = MaterialButton(this).apply {
            textSize = 11.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(10)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0F766E"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(8) }
        }
        controlsRow.addView(btnSort)

        val filterBtns = mutableMapOf<String, MaterialButton>()
        listOf("all" to "All", "pending" to "Pending", "done" to "Done", "issue" to "Issue").forEach { (k, lbl) ->
            val b = MaterialButton(this).apply {
                text = lbl
                textSize = 11.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(12), 0, dp(12), 0)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) }
            }
            filterBtns[k] = b
            controlsRow.addView(b)
        }
        controlsScroll.addView(controlsRow)
        body.addView(controlsScroll)

        val listCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(18).toFloat()
            }
            clipToOutline = true
        }
        body.addView(listCard)

        var displayLimit = 150

        fun renderPinRows() {
            btnSort.text = if (sortNearest) "Nearest ↓" else "A–Z ↓"
            filterBtns.forEach { (k, b) ->
                val active = k == filterKey
                b.setBackgroundColor(Color.parseColor(if (active) "#2563EB" else if (night) "#334155" else "#E2E8F0"))
                b.setTextColor(if (active) Color.WHITE else txtPrimary)
            }

            listCard.removeAllViews()
            val loc = currentLocation
            val filtered = currentSourcePins().filter { filterKey == "all" || it.status.equals(filterKey, true) }
            val ordered = if (sortNearest && loc != null) {
                filtered.sortedBy { RoutingEngine.haversineMeters(loc.latitude, loc.longitude, it.lat, it.lng) }
            } else {
                filtered.sortedBy { it.name.lowercase() }
            }

            if (ordered.isEmpty()) {
                listCard.addView(TextView(this).apply {
                    text = "No bookmarks match this filter."
                    textSize = 13f
                    setTextColor(txtSub)
                    setPadding(dp(16), dp(16), dp(16), dp(16))
                })
                return
            }

            val pageItems = ordered.take(displayLimit)
            pageItems.forEachIndexed { idx, pin ->
                if (idx > 0) {
                    listCard.addView(View(this).apply {
                        setBackgroundColor(dividerColor)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                            marginStart = dp(16)
                            marginEnd = dp(16)
                        }
                    })
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    setOnClickListener {
                        ensurePinVisibleOnMap(pin)
                        closeFullPage()
                        selectPinAndOpenCard(pin, zoomIfFar = true)
                    }
                }
                row.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(statusColorHex(pin.status)))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(12) }
                })
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                col.addView(TextView(this).apply {
                    text = pin.name
                    textSize = 15f
                    setTextColor(txtPrimary)
                    setTypeface(null, Typeface.BOLD)
                })
                val subParts = mutableListOf(statusLabel(pin.status))
                if (pin.status.equals("issue", true) && pin.issueReason.isNotBlank()) subParts += "⚠ ${pin.issueReason}"
                else if (pin.status.equals("done", true) && pin.issueReason.isNotBlank()) subParts += "✓ ${pin.issueReason}"
                if (pin.isEdited && pin.editedBy.isNotBlank()) subParts += "Edited by ${pin.editedBy}"
                col.addView(TextView(this).apply {
                    text = subParts.joinToString(" • ")
                    textSize = 12f
                    setTextColor(
                        when {
                            pin.status.equals("issue", true) -> Color.parseColor("#DC2626")
                            pin.status.equals("done", true) && pin.issueReason.isNotBlank() -> Color.parseColor("#15803D")
                            else -> txtSub
                        }
                    )
                })
                row.addView(col)

                if (loc != null) {
                    val d = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, pin.lat, pin.lng)
                    row.addView(TextView(this).apply {
                        text = formatDistanceShort(d)
                        textSize = 12f
                        setTextColor(Color.parseColor("#2563EB"))
                        setTypeface(null, Typeface.BOLD)
                    })
                }
                listCard.addView(row)
            }

            val remainingCount = ordered.size - pageItems.size
            if (remainingCount > 0) {
                listCard.addView(View(this).apply {
                    setBackgroundColor(dividerColor)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
                })
                listCard.addView(TextView(this).apply {
                    text = "Show more ($remainingCount remaining)"
                    textSize = 13.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#2563EB"))
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(14), dp(16), dp(14))
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    setOnClickListener {
                        displayLimit += 150
                        renderPinRows()
                    }
                })
            }
        }

        btnSort.setOnClickListener {
            sortNearest = !sortNearest
            displayLimit = 150
            renderPinRows()
        }
        filterBtns.forEach { (k, b) ->
            b.setOnClickListener { filterKey = k; displayLimit = 150; renderPinRows() }
        }

        if (layer != null && layer.shareCode.isNotBlank()) {
            body.addView(TextView(this).apply {
                text = "Share Code: ${layer.shareCode}"
                textSize = 12.5f
                setTextColor(txtSub)
                setPadding(dp(6), dp(10), dp(6), 0)
            })
        }

        livePinListRefresh = { renderPinRows() }
        renderPinRows()
        return outer
    }

    private fun jumpToNearestPendingPin() {
        if (!hasLocationPermission()) {
            locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        if (!ensureLocationServiceOn()) return
        val loc = currentLocation
        if (loc == null) {
            Toast.makeText(this, "Waiting for GPS fix first", Toast.LENGTH_SHORT).show()
            return
        }
        val hidden = DevicePrefs.getHiddenLayerIds(this)
        val sentIds = if (hideSentPins) DevicePrefs.getLocalSentPinIds(this) else emptySet()
        val pending = pins.filter { it.layerId !in hidden && it.status.equals("pending", true) && it.id !in sentIds }
        val nearest = pending.minByOrNull { RoutingEngine.haversineMeters(loc.latitude, loc.longitude, it.lat, it.lng) }
        if (nearest == null) {
            val msg = if (hideSentPins) "No unsent pending pins in visible folders!" else "No pending pins in visible folders!"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return
        }
        closeFullPage()
        selectPinAndOpenCard(nearest, zoomIfFar = true)
    }

    // --- Page 1: Full-Screen Search Page ---

    private fun buildSearchPage(): View {
        val night = isDarkMapUi()
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val cardBg = Color.parseColor(if (night) "#1E293B" else "#F3F4F6")

        val (outer, body) = makePageFrame("Search", { closeFullPage() })

        val etQuery = EditText(this).apply {
            hint = "Search pins, places, or coords..."
            setTextColor(txtPrimary)
            setHintTextColor(txtSub)
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(14).toFloat()
            }
        }
        body.addView(etQuery)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(12))
        }
        val btnWebSearch = MaterialButton(this).apply {
            text = "Search Map Places"
            textSize = 12.5f
            isAllCaps = false
            maxLines = 1
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.05f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
        }
        val btnNearest = MaterialButton(this).apply {
            text = "Nearest Pending"
            textSize = 12.5f
            isAllCaps = false
            maxLines = 1
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0F766E"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { jumpToNearestPendingPin() }
        }
        btnRow.addView(btnWebSearch)
        btnRow.addView(btnNearest)
        body.addView(btnRow)

        val webResultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val resultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(webResultsBox)
        body.addView(resultsBox)

        fun renderLocalMatches(q: String) {
            resultsBox.removeAllViews()
            val trimmed = q.trim()
            if (trimmed.isEmpty()) {
                webSearchSeq++
                webResultsBox.removeAllViews()
                resultsBox.addView(TextView(this).apply {
                    text = "Type a pin name, street, or coordinates to search."
                    textSize = 13f
                    setTextColor(txtSub)
                    setPadding(dp(6), dp(14), dp(6), dp(8))
                })
                return
            }

            val coordParts = trimmed.split(",").map { it.trim().toDoubleOrNull() }
            if (coordParts.size == 2 && coordParts[0] != null && coordParts[1] != null) {
                val lat = coordParts[0]!!
                val lng = coordParts[1]!!
                if (lat in -90.0..90.0 && lng in -180.0..180.0) {
                    resultsBox.addView(TextView(this).apply {
                        text = "📍 Go to coordinates: $lat, $lng"
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#2563EB"))
                        setPadding(dp(8), dp(12), dp(8), dp(12))
                        UiAnimations.applyRowRipple(this@MainActivity, this, night)
                        setOnClickListener {
                            closeFullPage()
                            selectedPinId = null
                            selectedMarkerLatLng = LatLng(lat, lng)
                            selectedMarkerIcon = "pin-target"
                            renderMapData()
                            centerUpperHalf(LatLng(lat, lng), 16.5)
                            showTappedPlaceCard("Coordinates", "", lat, lng)
                        }
                    })
                }
            }

            val matches = pins.filter {
                it.name.contains(trimmed, true) ||
                    it.description.contains(trimmed, true) ||
                    it.issueReason.contains(trimmed, true) ||
                    statusLabel(it.status).contains(trimmed, true)
            }.take(100)

            val loc = currentLocation
            matches.forEach { pin ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(11), dp(8), dp(11))
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    setOnClickListener {
                        ensurePinVisibleOnMap(pin)
                        closeFullPage()
                        selectPinAndOpenCard(pin, zoomIfFar = true)
                    }
                }
                row.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.parseColor(statusColorHex(pin.status)))
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(11), dp(11)).apply { marginEnd = dp(12) }
                })
                val statusTag = if ((pin.status.equals("issue", true) || pin.status.equals("done", true)) && pin.issueReason.isNotBlank()) {
                    "${statusLabel(pin.status)}: ${pin.issueReason}"
                } else {
                    statusLabel(pin.status)
                }
                row.addView(TextView(this).apply {
                    text = "${pin.name} ($statusTag)"
                    textSize = 14f
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(txtPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(12)
                    }
                })
                if (loc != null) {
                    val d = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, pin.lat, pin.lng)
                    row.addView(TextView(this).apply {
                        text = formatDistanceShort(d)
                        textSize = 12f
                        setSingleLine(true)
                        setTextColor(txtSub)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(4) }
                    })
                }
                resultsBox.addView(row)
            }
        }

        renderLocalMatches("")
        var searchPageRunnable: Runnable? = null
        etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString() ?: ""
                searchPageRunnable?.let { etQuery.removeCallbacks(it) }
                val r = Runnable { renderLocalMatches(q) }
                searchPageRunnable = r
                etQuery.postDelayed(r, if (q.isEmpty()) 0L else 120L)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        fun runWebSearch() {
            val q = etQuery.text.toString().trim()
            if (q.isBlank()) return
            hideSoftKeyboard()
            val seq = ++webSearchSeq
            btnWebSearch.isEnabled = false
            btnWebSearch.text = "Searching..."
            val center = map?.cameraPosition?.target
            RoutingEngine.searchPlaces(q, center?.latitude, center?.longitude) { places, err ->
                runOnUiThread {
                    if (isFinishing || isDestroyed || seq != webSearchSeq) return@runOnUiThread
                    btnWebSearch.isEnabled = true
                    btnWebSearch.text = "Search Map Places"
                    webResultsBox.removeAllViews()
                    if (!err.isNullOrBlank()) {
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    if (places.isEmpty()) {
                        Toast.makeText(this, "No places found", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    places.forEach { place ->
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(8), dp(11), dp(8), dp(11))
                            UiAnimations.applyRowRipple(this@MainActivity, this, night)
                            setOnClickListener {
                                closeFullPage()
                                selectedPinId = null
                                selectedMarkerLatLng = LatLng(place.lat, place.lng)
                                selectedMarkerIcon = "pin-target"
                                renderMapData()
                                centerUpperHalf(LatLng(place.lat, place.lng), 16.0)
                                showTappedPlaceCard(place.title, place.subtitle, place.lat, place.lng)
                            }
                        }
                        row.addView(TextView(this).apply {
                            text = "🌐 ${place.title}"
                            textSize = 15f
                            setTextColor(txtPrimary)
                            setTypeface(null, Typeface.BOLD)
                        })
                        if (place.subtitle.isNotBlank()) {
                            row.addView(TextView(this).apply {
                                text = place.subtitle
                                textSize = 12f
                                setTextColor(txtSub)
                            })
                        }
                        webResultsBox.addView(row)
                    }
                }
            }
        }

        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                runWebSearch()
                true
            } else false
        }
        btnWebSearch.setOnClickListener { runWebSearch() }
        activePageCleanup = {
            searchPageRunnable?.let { etQuery.removeCallbacks(it) }
            webSearchSeq++
        }

        return outer
    }

    // --- Page 3: Settings Page (Map Style, 3D Buildings, Travel Mode, Speed, Offline Maps) ---

    private fun buildSettingsPage(): View {
        val night = isDarkMapUi()
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val cardBg = Color.parseColor(if (night) "#1E293B" else "#F3F4F6")
        val chipIdle = Color.parseColor(if (night) "#334155" else "#E2E8F0")

        val (outer, body) = makePageFrame("Settings", { closeFullPage() })

        // 1. Device Identity Stamp
        body.addView(TextView(this).apply {
            text = "Device Edit Stamp"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dp(6), 0, dp(2))
        })
        val tvCurrentStamp = TextView(this).apply {
            text = DevicePrefs.getDeviceStamp(this@MainActivity)
            textSize = 12f
            setTextColor(txtSub)
            setPadding(0, 0, 0, dp(6))
        }
        body.addView(tvCurrentStamp)

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(14))
        }
        val etDeviceName = EditText(this).apply {
            hint = "Custom display name (e.g. Supervisor)"
            setTextColor(txtPrimary)
            setHintTextColor(txtSub)
            setText(DevicePrefs.getCustomName(this@MainActivity))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameRow.addView(etDeviceName)
        nameRow.addView(MaterialButton(this).apply {
            text = "Save"
            textSize = 12.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(10)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(40)
            ).apply { marginStart = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                DevicePrefs.setCustomName(this@MainActivity, etDeviceName.text.toString())
                val updated = DevicePrefs.getDeviceStamp(this@MainActivity)
                tvCurrentStamp.text = updated
                Toast.makeText(this@MainActivity, "Updated: $updated", Toast.LENGTH_SHORT).show()
            }
        })
        body.addView(nameRow)

        // 2. Map Style Selector (Vector Street / Satellite Hybrid)
        body.addView(TextView(this).apply {
            text = "Map Style"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dp(4), 0, dp(6))
        })
        val styleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        val styleButtons = mutableMapOf<MapStyleMode, MaterialButton>()
        fun syncStyleButtons() {
            styleButtons.forEach { (m, b) ->
                val active = m == mapStyleMode
                b.setTextColor(if (active) Color.WHITE else txtPrimary)
                b.setBackgroundColor(if (active) Color.parseColor("#2563EB") else chipIdle)
            }
        }
        MapStyleMode.entries.forEachIndexed { idx, mode ->
            val btn = MaterialButton(this).apply {
                text = mode.label
                textSize = 12.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (idx < MapStyleMode.entries.lastIndex) marginEnd = dp(6)
                }
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    if (mapStyleMode == mode) return@setOnClickListener
                    mapStyleMode = mode
                    DevicePrefs.setMapStyleMode(this@MainActivity, mode)
                    syncStyleButtons()
                    applyMapChromeTheme()
                    loadMapStyle()
                }
            }
            styleButtons[mode] = btn
            styleRow.addView(btn)
        }
        syncStyleButtons()
        body.addView(styleRow)

        // 2b. App & Map Theme (Day / Night / Auto)
        body.addView(TextView(this).apply {
            text = "Theme"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dp(2), 0, dp(6))
        })
        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        val curTheme = DevicePrefs.getThemeMode(this)
        listOf("day" to "Day", "night" to "Night", "auto" to "Auto").forEachIndexed { idx, (key, label) ->
            val active = curTheme == key
            themeRow.addView(MaterialButton(this).apply {
                text = label
                textSize = 12.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                setTextColor(if (active) Color.WHITE else txtPrimary)
                setBackgroundColor(if (active) Color.parseColor("#2563EB") else chipIdle)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (idx < 2) marginEnd = dp(6)
                }
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    if (DevicePrefs.getThemeMode(this@MainActivity) == key) return@setOnClickListener
                    DevicePrefs.setThemeMode(this@MainActivity, key)
                    applyMapChromeTheme()
                    if (mapStyleMode == MapStyleMode.VECTOR) loadMapStyle()
                    openFullPage(::buildSettingsPage)
                }
            })
        }
        body.addView(themeRow)

        // 3. Default Travel Mode (Car / Bicycle / Walk)
        body.addView(TextView(this).apply {
            text = "Default Travel Mode"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dp(2), 0, dp(6))
        })
        val travelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        val travelButtons = mutableMapOf<TravelMode, MaterialButton>()
        fun syncTravelButtons() {
            travelButtons.forEach { (m, b) ->
                val active = m == travelMode
                b.setTextColor(if (active) Color.WHITE else txtPrimary)
                b.setBackgroundColor(if (active) Color.parseColor(m.routeColorHex) else chipIdle)
            }
        }
        TravelMode.entries.forEachIndexed { idx, mode ->
            val btn = MaterialButton(this).apply {
                text = "${mode.icon} ${mode.label}"
                textSize = 12.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (idx < TravelMode.entries.lastIndex) marginEnd = dp(6)
                }
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    if (travelMode == mode) return@setOnClickListener
                    travelMode = mode
                    DevicePrefs.setTravelMode(this@MainActivity, mode)
                    syncTravelButtons()
                }
            }
            travelButtons[mode] = btn
            travelRow.addView(btn)
        }
        syncTravelButtons()
        body.addView(travelRow)

        // 4. Map & Driving Toggles
        fun addSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            row.addView(TextView(this).apply {
                text = title
                textSize = 14f
                setTextColor(txtPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(MaterialSwitch(this).apply {
                isChecked = checked
                setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
            })
            body.addView(row)
        }

        addSwitchRow("3D Buildings (Vector Street)", DevicePrefs.is3dBuildingsEnabled(this)) {
            DevicePrefs.set3dBuildingsEnabled(this, it)
            map?.style?.let { st -> if (st.isFullyLoaded) apply3dBuildingsToggle(st) }
        }
        addSwitchRow("Live Driving Speed Indicator (km/h)", DevicePrefs.isSpeedIndicatorEnabled(this)) {
            DevicePrefs.setSpeedIndicatorEnabled(this, it)
            updateSpeedBadge(currentLocation)
        }
        addSwitchRow("3D Mountain Hillshading (Vector)", DevicePrefs.isHillshadeEnabled(this)) {
            DevicePrefs.setHillshadeEnabled(this, it)
            map?.style?.let { st ->
                if (!st.isFullyLoaded) return@let
                val existing = st.getLayer(HILLSHADE_LAYER)
                if (existing != null) {
                    existing.setProperties(PropertyFactory.visibility(if (it && mapStyleMode == MapStyleMode.VECTOR && !isDarkMapUi()) Property.VISIBLE else Property.NONE))
                } else if (it) {
                    attachTerrainHillshade(st)
                }
            }
        }
        addSwitchRow("Show Bookmark Titles on Map", DevicePrefs.isShowBookmarkTitles(this)) {
            DevicePrefs.setShowBookmarkTitles(this, it)
            renderMapData()
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
            btnNavVoice.alpha = if (it) 1.0f else 0.4f
        }
        addSwitchRow("Show Quick Map Buttons (All Pins, Lasso, Import)", DevicePrefs.isShowExtraMapTools(this)) {
            DevicePrefs.setShowExtraMapTools(this, it)
            applyExtraMapToolsVisibility()
        }

        // 6. Offline Maps & Embedded Turn-by-Turn Routing
        body.addView(TextView(this).apply {
            text = "Offline Maps & Routing"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dp(16), 0, dp(2))
        })
        body.addView(TextView(this).apply {
            text = "Each offline map pack automatically bundles turn-by-turn offline road routing for that area."
            textSize = 12f
            setTextColor(txtSub)
            setPadding(0, 0, 0, dp(10))
        })

        val packUpdaters = mutableListOf<() -> Unit>()
        val pixelRatio = resources.displayMetrics.density

        fun addOfflineCard(
            packId: String,
            title: String,
            subtitle: String,
            onDownload: () -> Unit,
            onDownloadRoadsOnly: () -> Unit
        ) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(cardBg)
                    cornerRadius = dp(14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(10)
                }
            }
            card.addView(TextView(this).apply {
                text = title
                textSize = 14.5f
                setTextColor(txtPrimary)
                setTypeface(null, Typeface.BOLD)
            })
            card.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(txtSub)
                setPadding(0, dp(2), 0, dp(6))
            })
            val tvStatus = TextView(this).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#0F766E"))
            }
            card.addView(tvStatus)

            val tvRoadStatus = TextView(this).apply {
                textSize = 11.5f
                setTextColor(Color.parseColor("#2563EB"))
                visibility = View.GONE
                setPadding(0, dp(2), 0, 0)
            }
            card.addView(tvRoadStatus)

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
                textSize = 12.5f
                isAllCaps = false
                maxLines = 1
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(10), 0, dp(10), 0)
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor("#0F766E"))
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(8) }
                UiAnimations.attachSpringTouch(this)
            }
            val btnDel = MaterialButton(this).apply {
                text = "Delete"
                textSize = 12.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(10), 0, dp(10), 0)
                setTextColor(Color.parseColor("#EF4444"))
                setBackgroundColor(Color.TRANSPARENT)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            }

            fun refreshCard() {
                val st = offlineMaps.getState(packId)
                val busy = st.isDownloading || st.isRoadDownloading
                val fullyReady = st.isDownloaded && st.hasRoadGraph
                tvStatus.text = st.statusText
                if (st.roadStatusText.isNotBlank() && !fullyReady) {
                    tvRoadStatus.text = st.roadStatusText
                    tvRoadStatus.visibility = View.VISIBLE
                } else {
                    tvRoadStatus.visibility = View.GONE
                }
                progressBar.visibility = if (busy) View.VISIBLE else View.GONE
                progressBar.isIndeterminate = !st.isDownloading && st.isRoadDownloading
                if (st.isDownloading) progressBar.progress = st.progressPercent

                btnDl.text = when {
                    busy -> "Pause"
                    fullyReady -> "Map + Routing Ready"
                    st.isDownloaded && !st.hasRoadGraph -> "Add Offline Roads"
                    else -> "Download Map + Roads"
                }
                btnDl.isEnabled = !fullyReady || busy
                btnDl.alpha = if (btnDl.isEnabled) 1f else 0.55f
            }

            btnDl.setOnClickListener {
                val st = offlineMaps.getState(packId)
                when {
                    st.isDownloading || st.isRoadDownloading -> offlineMaps.pauseDownload(packId)
                    st.isDownloaded && !st.hasRoadGraph -> onDownloadRoadsOnly()
                    else -> onDownload()
                }
            }
            btnDel.setOnClickListener {
                offlineMaps.deletePack(packId) { msg ->
                    runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
                }
            }

            btnRow.addView(btnDl)
            btnRow.addView(btnDel)
            card.addView(btnRow)
            body.addView(card)

            refreshCard()
            packUpdaters += ::refreshCard
        }

        OfflineMapManager.REGIONAL_PACKS.forEach { pack ->
            addOfflineCard(
                pack.id,
                pack.title,
                pack.subtitle,
                onDownload = { offlineMaps.downloadPack(pack, pixelRatio) },
                onDownloadRoadsOnly = { offlineMaps.downloadRoadGraphOnly(pack) }
            )
        }

        body.addView(MaterialButton(this).apply {
            text = "Clear Temporary Map Cache"
            textSize = 12.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(txtPrimary)
            setBackgroundColor(cardBg)
            cornerRadius = dp(10)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                topMargin = dp(4)
            }
            UiAnimations.attachSpringTouch(this)
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
        return outer
    }

    // --- Page 4: About Page ---

    private fun buildAboutPage(): View {
        val night = isDarkMapUi()
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val cardBg = Color.parseColor(if (night) "#1E293B" else "#F3F4F6")

        val (outer, body) = makePageFrame("About TerraSync", { closeFullPage() })

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_app_icon)
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { marginEnd = dp(14) }
            background = GradientDrawable().apply { cornerRadius = dp(16).toFloat() }
            clipToOutline = true
        })
        val heroText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroText.addView(TextView(this).apply {
            text = "TerraSync"
            textSize = 24f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
        })
        heroText.addView(TextView(this).apply {
            text = "Version ${getInstalledVersionName()} • Kotlin + MapLibre GL"
            textSize = 13f
            setTextColor(Color.parseColor("#0F766E"))
            setTypeface(null, Typeface.BOLD)
        })
        hero.addView(heroText)
        body.addView(hero)

        body.addView(TextView(this).apply {
            text = "Collaborative field mapping with Vector Street & Satellite Hybrid styles, Valhalla + embedded offline turn-by-turn routing, status-preserving KML round-tripping, deduplicated WhatsApp team reports, owner edit-log moderation, and Android Auto support."
            textSize = 14f
            setTextColor(txtPrimary)
            setPadding(0, 0, 0, dp(16))
        })

        val devCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        }
        devCard.addView(TextView(this).apply {
            text = "Developer"
            textSize = 12f
            setTextColor(txtSub)
        })
        devCard.addView(TextView(this).apply {
            text = "Thamir (@Thamirakkurd)"
            textSize = 17f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(2), 0, dp(6))
        })
        devCard.addView(TextView(this).apply {
            text = "Device Stamp: ${DevicePrefs.getDeviceStamp(this@MainActivity)}"
            textSize = 12f
            setTextColor(txtSub)
        })
        body.addView(devCard)

        listOf(
            Triple("GitHub Profile (@Thamirakkurd)", "https://github.com/Thamirakkurd", "#1E293B"),
            Triple("TerraSync Repository", "https://github.com/Thamirakkurd/TerraSync", "#2563EB"),
            Triple("Releases & Updates", "https://github.com/Thamirakkurd/TerraSync/releases", "#0F766E")
        ).forEach { (label, url, colorHex) ->
            body.addView(MaterialButton(this).apply {
                text = label
                textSize = 13f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor(colorHex))
                cornerRadius = dp(12)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                    bottomMargin = dp(10)
                }
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    safeStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No browser app installed")
                }
            })
        }

        return outer
    }

    // --- Dialogs: Issue Reason, Edit Pin, Add Manual Pin, Join Code ---

    private fun showIssueReasonDialog(pin: PinItem) {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        box.addView(TextView(this).apply {
            text = "Select a reason or type details below:"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 0, 0, dp(8))
        })
        val etReason = EditText(this).apply {
            hint = "What is the issue?"
            if (pin.status.equals("issue", ignoreCase = true)) {
                setText(pin.issueReason)
            }
            minLines = 2
        }
        ISSUE_PRESETS.forEach { preset ->
            box.addView(MaterialButton(this).apply {
                text = preset
                textSize = 12.5f
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
                    Toast.makeText(this, "Please select or enter the issue", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val stamp = DevicePrefs.getDeviceStamp(this)
                savePinStatusUnified(pin, "issue", stamp, reason)
                fireStatusPulse(pin.lat, pin.lng, "#EF4444")
                selectedMarkerIcon = "pin-issue"
                renderMapData()
                Toast.makeText(this, "Marked Issue: $reason", Toast.LENGTH_SHORT).show()
                dlg.dismiss()
                hideInScreenSheet()
            }
        }
        dlg.show()
    }

    private fun showDoneReasonDialog(pin: PinItem) {
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        box.addView(TextView(this).apply {
            text = "Select completion status or type details below:"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 0, 0, dp(8))
        })
        val etNote = EditText(this).apply {
            hint = "Optional details..."
            if (pin.status.equals("done", ignoreCase = true)) {
                setText(pin.issueReason)
            }
            minLines = 2
        }
        DONE_PRESETS.forEach { preset ->
            box.addView(MaterialButton(this).apply {
                text = preset
                textSize = 12.5f
                isAllCaps = false
                setTextColor(Color.parseColor("#1E293B"))
                setBackgroundColor(Color.parseColor("#F1F5F9"))
                cornerRadius = dp(10)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply {
                    bottomMargin = dp(6)
                }
                setOnClickListener {
                    etNote.setText(preset)
                    etNote.setSelection(preset.length)
                }
            })
        }
        box.addView(etNote)
        scroll.addView(box)

        val dlg = AlertDialog.Builder(this)
            .setTitle("Mark as Done")
            .setView(scroll)
            .setPositiveButton("Save Done", null)
            .setNegativeButton("Cancel", null)
            .create()

        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val note = etNote.text.toString().trim()
                val stamp = DevicePrefs.getDeviceStamp(this)
                savePinStatusUnified(pin, "done", stamp, note)
                fireStatusPulse(pin.lat, pin.lng, "#22C55E")
                selectedMarkerIcon = statusIconId("done")
                renderMapData()
                val msg = if (note.isNotBlank()) "Marked Done: $note" else "Marked Done"
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                dlg.dismiss()
                hideInScreenSheet()
            }
        }
        dlg.show()
    }

    private fun showEditPinDialog(pin: PinItem) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val etName = EditText(this).apply { hint = "Pin name"; setText(pin.name) }
        val etDesc = EditText(this).apply { hint = "Description / notes"; setText(pin.description); minLines = 2 }
        box.addView(etName)
        box.addView(etDesc)

        AlertDialog.Builder(this)
            .setTitle("Edit Pin")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val stamp = DevicePrefs.getDeviceStamp(this)
                savePinTextUnified(pin, etName.text.toString().trim(), etDesc.text.toString().trim(), stamp)
                Toast.makeText(this, "Saved & stamped", Toast.LENGTH_SHORT).show()
                hideInScreenSheet()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddPinDialog(latLng: LatLng, prefillName: String = "", prefillDesc: String = "") {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(4))
        }
        val etName = EditText(this).apply { hint = "Pin name"; setText(prefillName) }
        val etDesc = EditText(this).apply { hint = "Description (optional)"; setText(prefillDesc) }
        box.addView(etName)
        box.addView(etDesc)

        AlertDialog.Builder(this)
            .setTitle("Save Synced Pin")
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
                    devId,
                    ::notifySyncError
                )
                DevicePrefs.addJoinedLayerId(this, layerId)
                refreshJoinedSync()
                hideInScreenSheet()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareLayerLink(layer: LayerItem) {
        val code = layer.shareCode
        val link = "https://thamirakkurd.github.io/TerraSync/join/?code=$code"
        val msg = "TerraSync Folder: ${layer.name}\nShare Code: $code\nOpen link: $link"
        safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, msg)
        }, "Share Folder"))
    }

    private fun showJoinCodeDialog() {
        val input = EditText(this).apply { hint = "Enter 6-character code (e.g. AKR742)" }
        AlertDialog.Builder(this)
            .setTitle("Join Shared Folder")
            .setView(input)
            .setPositiveButton("Join") { _, _ -> joinShareCode(input.text.toString()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCombineFoldersDialog() {
        if (layers.size < 2) {
            Toast.makeText(this, "Need at least 2 folders to combine", Toast.LENGTH_SHORT).show()
            return
        }

        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }

        val etName = EditText(this).apply {
            hint = "New combined folder name"
            setSingleLine(true)
        }
        box.addView(etName)

        box.addView(TextView(this).apply {
            text = "Select folders to combine:"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(12), 0, dp(4))
        })

        val selectedIds = mutableSetOf<String>()
        layers.forEach { layer ->
            val count = pins.count { it.layerId == layer.id }
            val cb = CheckBox(this).apply {
                text = "${layer.name} ($count pins)"
                textSize = 14f
                setTextColor(Color.parseColor("#0F172A"))
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedIds.add(layer.id) else selectedIds.remove(layer.id)
                }
            }
            box.addView(cb)
        }

        box.addView(View(this).apply {
            setBackgroundColor(Color.parseColor("#E2E8F0"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        })

        val cbDedup = CheckBox(this).apply {
            text = "Skip exact duplicates (same name, desc & GPS)"
            textSize = 13f
            setTextColor(Color.parseColor("#334155"))
            isChecked = false
        }
        box.addView(cbDedup)

        val cbHideOriginals = CheckBox(this).apply {
            text = "Hide original folders on map"
            textSize = 13f
            setTextColor(Color.parseColor("#334155"))
            isChecked = true
        }
        box.addView(cbHideOriginals)

        scroll.addView(box)

        val dlg = AlertDialog.Builder(this)
            .setTitle("Combine Folders")
            .setView(scroll)
            .setPositiveButton("Combine", null)
            .setNegativeButton("Cancel", null)
            .create()

        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (selectedIds.size < 2) {
                    Toast.makeText(this, "Select at least 2 folders", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val combinedName = etName.text.toString().trim().ifBlank {
                    "Combined (${selectedIds.size} folders)"
                }
                val rawPins = pins.filter { it.layerId in selectedIds }
                val rawTracks = tracks.filter { it.layerId in selectedIds }
                val finalPins = if (cbDedup.isChecked) {
                    rawPins
                        .sortedWith(
                            compareByDescending<PinItem> { !it.status.equals("pending", true) }
                                .thenByDescending { it.updatedAt }
                        )
                        .distinctBy {
                            "${it.name.trim().lowercase()}|${it.description.trim().lowercase()}|${"%.6f".format(Locale.US, it.lat)}|${"%.6f".format(Locale.US, it.lng)}"
                        }
                } else {
                    rawPins
                }

                if (finalPins.isEmpty() && rawTracks.isEmpty()) {
                    Toast.makeText(this, "Selected folders have no pins or tracks", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newLayerId = UUID.randomUUID().toString()
                val stamp = DevicePrefs.getDeviceStamp(this)
                val devId = DevicePrefs.getDeviceId(this)
                val skippedCount = rawPins.size - finalPins.size
                repo.combineLayers(newLayerId, combinedName, stamp, devId, finalPins, rawTracks) { ok, msg, _ ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        val fullMsg = if (skippedCount > 0) "$msg ($skippedCount duplicates skipped)" else msg
                        Toast.makeText(this, fullMsg, Toast.LENGTH_LONG).show()
                        if (ok) {
                            DevicePrefs.addJoinedLayerId(this, newLayerId)
                            if (cbHideOriginals.isChecked) {
                                selectedIds.forEach { oldId ->
                                    DevicePrefs.setLayerVisible(this, oldId, false)
                                }
                            }
                            refreshJoinedSync()
                        }
                    }
                }
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    // --- KML Import & Firestore Sync ---

    private fun handleImportUri(uri: Uri) {
        val fileName = getDisplayName(uri).substringBeforeLast('.')
        val tempId = UUID.randomUUID().toString()
        Toast.makeText(this, "Reading $fileName...", Toast.LENGTH_SHORT).show()

        RoutingEngine.ioExecutor.execute {
            val parsed = runCatching {
                contentResolver.openInputStream(uri)?.use { KmlParser.parse(it, tempId) }
            }.getOrNull()

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (parsed == null || (parsed.pins.isEmpty() && parsed.tracks.isEmpty())) {
                    Toast.makeText(this, "No pins or tracks found in file", Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
                val rawDocName = parsed.documentName.trim()
                val preferredBase = when {
                    rawDocName.isNotBlank() && !rawDocName.equals("Document", true) && !rawDocName.equals("KML", true) -> rawDocName
                    fileName.isNotBlank() -> fileName.replace('_', ' ').trim()
                    else -> "Imported Folder"
                }
                showImportDestinationDialog(preferredBase, parsed)
            }
        }
    }

    private fun showImportDestinationDialog(defaultBaseName: String, parsed: ParsedKml) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(12), dp(20), dp(8))
        }
        val trackSummary = if (parsed.tracks.isNotEmpty()) " • ${parsed.tracks.size} tracks" else ""
        box.addView(TextView(this).apply {
            text = "${parsed.pins.size} pins$trackSummary found. Choose where to save:"
            textSize = 13.5f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, 0, 0, dp(10))
        })

        val etName = EditText(this).apply {
            hint = "Folder name"
            setText(makeUniqueLayerName(defaultBaseName))
            setSingleLine(true)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F1F5F9"))
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        box.addView(etName)

        val dlg = AlertDialog.Builder(this)
            .setTitle("Import KML / KMZ")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .create()

        fun addChoiceBtn(title: String, hex: String, onTap: () -> Unit) {
            box.addView(MaterialButton(this).apply {
                text = title
                textSize = 13.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setTextColor(Color.WHITE)
                setBackgroundColor(Color.parseColor(hex))
                cornerRadius = dp(12)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                    bottomMargin = dp(8)
                }
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    dlg.dismiss()
                    onTap()
                }
            })
        }

        val stamp = DevicePrefs.getDeviceStamp(this)
        val devId = DevicePrefs.getDeviceId(this)

        addChoiceBtn("Save Locally (This Phone Only)", "#0F766E") {
            val chosenName = makeUniqueLayerName(etName.text.toString().trim().ifBlank { defaultBaseName })
            val localId = LocalFolderStore.newLocalLayerId()
            LocalFolderStore.saveNewLayer(this, localId, chosenName, stamp, devId, parsed)
            DevicePrefs.addCachedLayerName(this, chosenName)
            applyMergedFoldersAndRender()
            if (parsed.pins.isNotEmpty()) zoomToPoints(parsed.pins.map { LatLng(it.lat, it.lng) })
            Toast.makeText(this, "Saved ${parsed.pins.size} pins locally in \"$chosenName\"", Toast.LENGTH_LONG).show()
        }

        addChoiceBtn("Upload to Cloud (Shared Folder)", "#2563EB") {
            val chosenName = makeUniqueLayerName(etName.text.toString().trim().ifBlank { defaultBaseName })
            val cloudId = UUID.randomUUID().toString()
            DevicePrefs.addCachedLayerName(this, chosenName)
            repo.importLayer(cloudId, chosenName, stamp, devId, parsed) { ok, msg, _ ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    if (ok) {
                        DevicePrefs.addJoinedLayerId(this, cloudId)
                        refreshJoinedSync()
                        if (parsed.pins.isNotEmpty()) zoomToPoints(parsed.pins.map { LatLng(it.lat, it.lng) })
                    }
                }
            }
        }

        if (layers.isNotEmpty()) {
            addChoiceBtn("Merge into Existing Folder...", "#475569") {
                showMergeTargetFolderDialog(parsed)
            }
        }

        dlg.show()
    }

    private fun showMergeTargetFolderDialog(parsed: ParsedKml) {
        if (layers.isEmpty()) {
            Toast.makeText(this, "No existing folders to merge into", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = layers.map { l ->
            val tag = if (LocalFolderStore.isLocalLayerId(l.id)) "Local" else "Cloud"
            val cnt = pins.count { it.layerId == l.id }
            "${l.name} [$tag • $cnt pins]"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Merge into Folder")
            .setItems(labels) { _, which ->
                val target = layers[which]
                if (LocalFolderStore.isLocalLayerId(target.id)) {
                    LocalFolderStore.appendToLayer(this, target.id, parsed.pins, parsed.tracks)
                    DevicePrefs.setLayerVisible(this, target.id, true)
                    applyMergedFoldersAndRender()
                    if (parsed.pins.isNotEmpty()) zoomToPoints(parsed.pins.map { LatLng(it.lat, it.lng) })
                    Toast.makeText(this, "Merged ${parsed.pins.size} pins into \"${target.name}\"", Toast.LENGTH_LONG).show()
                } else {
                    repo.appendToCloudLayer(target.id, target.name, parsed.pins, parsed.tracks) { ok, msg, _ ->
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                            if (ok) {
                                DevicePrefs.setLayerVisible(this, target.id, true)
                                refreshJoinedSync()
                                if (parsed.pins.isNotEmpty()) zoomToPoints(parsed.pins.map { LatLng(it.lat, it.lng) })
                            }
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmUploadLocalToCloud(layer: LayerItem, layerPins: List<PinItem>, layerTracks: List<TrackItem>) {
        AlertDialog.Builder(this)
            .setTitle("Upload \"${layer.name}\" to Cloud?")
            .setMessage("Creates a live shared folder with a 6-character code for your teams and replaces the local-only copy.")
            .setPositiveButton("Upload") { _, _ ->
                val newCloudId = UUID.randomUUID().toString()
                val stamp = DevicePrefs.getDeviceStamp(this)
                val devId = DevicePrefs.getDeviceId(this)
                val parsed = ParsedKml(
                    pins = layerPins.map { it.copy(id = it.id.ifBlank { UUID.randomUUID().toString() }, layerId = newCloudId) },
                    tracks = layerTracks.map { it.copy(id = it.id.ifBlank { UUID.randomUUID().toString() }, layerId = newCloudId) },
                    documentName = layer.name
                )
                repo.importLayer(newCloudId, layer.name, stamp, devId, parsed, requireServerCommit = true) { ok, msg, _ ->
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        if (ok) {
                            val wasPinned = DevicePrefs.isLayerPinned(this, layer.id)
                            LocalFolderStore.deleteLayer(this, layer.id)
                            DevicePrefs.removeJoinedLayerId(this, layer.id)
                            DevicePrefs.addJoinedLayerId(this, newCloudId)
                            if (wasPinned) DevicePrefs.toggleLayerPinned(this, newCloudId)
                            refreshJoinedSync()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDeleteLocalDialog(layer: LayerItem, pinCount: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Local Folder \"${layer.name}\"?")
            .setMessage("Removes all $pinCount pins stored locally on this phone.")
            .setPositiveButton("Delete") { _, _ ->
                val deletedPinIds = pins.filter { it.layerId == layer.id }.map { it.id }
                LocalFolderStore.deleteLayer(this, layer.id)
                DevicePrefs.removeJoinedLayerId(this, layer.id)
                DevicePrefs.removePinReferences(this, deletedPinIds)
                applyMergedFoldersAndRender()
                Toast.makeText(this, "Deleted local folder \"${layer.name}\"", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun isLayerOwner(layer: LayerItem?): Boolean {
        if (layer == null) return false
        if (LocalFolderStore.isLocalLayerId(layer.id)) return true
        val uid = repo.currentUid()
        if (layer.creatorUid.isNotBlank()) {
            return !uid.isNullOrBlank() && layer.creatorUid == uid
        }
        val myDevId = DevicePrefs.getDeviceId(this)
        return (layer.creatorDeviceId.isNotBlank() && layer.creatorDeviceId.equals(myDevId, ignoreCase = true)) ||
            (layer.creatorDeviceId.isBlank() && layer.createdBy.contains("#$myDevId", ignoreCase = true))
    }

    private fun notifySyncError(err: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            Toast.makeText(this, "Sync failed: $err", Toast.LENGTH_LONG).show()
        }
    }

    private fun savePinStatusUnified(pin: PinItem, status: String, stamp: String, reason: String = "") {
        if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            LocalFolderStore.updatePinStatus(this, pin, status, stamp, reason)
            applyMergedFoldersAndRender()
        } else {
            repo.updatePinStatus(pin, status, stamp, reason, ::notifySyncError)
        }
    }

    private fun savePinTextUnified(pin: PinItem, newName: String, newDesc: String, stamp: String) {
        if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            LocalFolderStore.updatePinText(this, pin, newName, newDesc, stamp)
            applyMergedFoldersAndRender()
        } else {
            repo.updatePinText(pin, newName, newDesc, stamp, ::notifySyncError)
        }
    }

    private fun deletePinHistoryEntryUnified(pin: PinItem, idx: Int): PinItem {
        return if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            val updated = LocalFolderStore.deletePinHistoryEntry(this, pin, idx)
            applyMergedFoldersAndRender()
            updated
        } else {
            repo.deletePinHistoryEntry(pin, idx, ::notifySyncError)
        }
    }

    private fun resetPinStatusUnified(pin: PinItem): PinItem {
        return if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            val updated = LocalFolderStore.resetPinStatusToPending(this, pin)
            applyMergedFoldersAndRender()
            updated
        } else {
            repo.resetPinStatusToPending(pin, ::notifySyncError)
        }
    }

    private fun deletePinUnified(pin: PinItem) {
        DevicePrefs.removePinReferences(this, listOf(pin.id))
        if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            LocalFolderStore.deletePin(this, pin.id)
            applyMergedFoldersAndRender()
        } else {
            repo.deletePin(pin.id, ::notifySyncError)
        }
    }

    private fun applyExtraMapToolsVisibility() {
        val show = DevicePrefs.isShowExtraMapTools(this)
        val vis = if (show) View.VISIBLE else View.GONE
        if (::btnSentToggle.isInitialized) btnSentToggle.visibility = vis
        if (::btnLasso.isInitialized) btnLasso.visibility = vis
        if (::btnImport.isInitialized) btnImport.visibility = vis
        if (!show && isLassoMode) toggleLassoMode(false)
    }

    private fun normalizeLayerNameKey(name: String): String {
        return name.trim().lowercase(java.util.Locale.US).replace(Regex("[_\\s-]+"), " ")
    }

    private fun makeUniqueLayerName(baseName: String): String {
        val clean = baseName.trim().ifBlank { "Imported Folder" }
        val existingKeys = buildSet {
            layers.forEach { add(normalizeLayerNameKey(it.name)) }
            DevicePrefs.getCachedLayerNames(this@MainActivity).forEach { add(normalizeLayerNameKey(it)) }
        }
        if (normalizeLayerNameKey(clean) !in existingKeys) return clean
        val stem = clean.replace(Regex("\\s*\\(\\d+\\)$"), "").trim().ifBlank { clean }
        var n = 2
        while (normalizeLayerNameKey("$stem ($n)") in existingKeys) {
            n++
        }
        return "$stem ($n)"
    }

    private fun applyMergedFoldersAndRender() {
        val local = LocalFolderStore.loadAll(this)
        layers = (cloudLayers + local.layers).sortedByDescending { it.updatedAt }
        pins = cloudPins + local.pins
        tracks = cloudTracks + local.tracks
        if (layers.isNotEmpty()) {
            DevicePrefs.saveCachedLayerNames(this, layers.map { it.name })
        }
        renderMapData()
        liveBookmarksRefresh?.invoke()
        livePinListRefresh?.invoke()
        if (isLassoMode) updateLassoUi()
    }

    private fun refreshJoinedSync() {
        val joined = DevicePrefs.getJoinedLayerIds(this)
        repo.syncJoinedLayers(joined) { newLayers, newPins, newTracks, connected ->
            cloudLayers = newLayers
            cloudPins = newPins
            cloudTracks = newTracks
            isLiveConnected = connected
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                applyMergedFoldersAndRender()
                val waitingId = activeSharedPinId
                if (waitingId != null) {
                    val matched = pins.find { it.id == waitingId }
                    if (matched != null) {
                        activeSharedPinId = null
                        hasAutoZoomedInitial = true
                        ensurePinVisibleOnMap(matched)
                        selectPinAndOpenCard(matched, zoomIfFar = true)
                        return@runOnUiThread
                    }
                }
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
                if (isFinishing || isDestroyed) return@runOnUiThread
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                if (ok && layerId != null) {
                    DevicePrefs.addJoinedLayerId(this, layerId)
                    hasAutoZoomedInitial = false
                    refreshJoinedSync()
                }
            }
        }
    }

    // --- Utilities & Lifecycle ---

    private fun applyKeepAwakeFlag() {
        if (DevicePrefs.isKeepAwake(this)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun safeStartActivity(intent: Intent, fallbackMsg: String = "No app available to handle this action") {
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, fallbackMsg, Toast.LENGTH_SHORT).show() }
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
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(pts.first(), 16.5), 550)
            return
        }
        runCatching {
            val b = LatLngBounds.Builder()
            pts.forEach { b.include(it) }
            m.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(70)), 650)
        }.onFailure {
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(pts.first(), 15.5), 550)
        }
    }

    private fun getDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: "Folder"
        }
        return uri.lastPathSegment ?: "Folder"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onStart() {
        super.onStart()
        isActivityStarted = true
        mapView.onStart()
    }
    @Suppress("DEPRECATION")
    private fun getInstalledVersionName(): String {
        return runCatching {
            val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            (info.versionName ?: "2.8.0").trim().removePrefix("v").removePrefix("V")
        }.getOrDefault("2.8.0")
    }

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        backCallback?.isEnabled = true
        mapView.onResume()
        if (hasLocationPermission()) startLiveGpsUpdates()
        renderMapData()
    }
    override fun onPause() {
        isActivityResumed = false
        pulseAnimator?.cancel()
        gpsAnim?.cancel()
        if (activeNavRoute == null) stopLiveGpsUpdates()
        mapView.onPause()
        super.onPause()
    }
    override fun onStop() {
        isActivityStarted = false
        mapView.onStop()
        super.onStop()
    }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() {
        stopLiveGpsUpdates()
        activePageCleanup?.invoke()
        activePageCleanup = null
        pageOverlayContainer.animate().cancel()
        pulseAnimator?.cancel()
        gpsAnim?.cancel()
        offlineMaps.setStateListener(null)
        tts?.shutdown()
        if (::repo.isInitialized) repo.stopListening()
        mapView.onDestroy()
        super.onDestroy()
    }
    private fun applyResponsiveChromeSizing() {
        if (!::btnLocate.isInitialized) return
        val compact = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val btnSize = dp(if (compact) 38 else 44)
        val locSize = dp(if (compact) 42 else 48)
        val gap = dp(if (compact) 4 else 10)
        val smallGap = dp(if (compact) 4 else 8)
        fun resize(v: View, size: Int, bottomMarginPx: Int) {
            val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            lp.width = size
            lp.height = size
            lp.bottomMargin = bottomMarginPx
            v.layoutParams = lp
        }
        resize(btnLasso, btnSize, gap)
        resize(btnRuler, btnSize, gap)
        resize(btnZoomIn, btnSize, smallGap)
        resize(btnZoomOut, btnSize, gap)
        resize(btnImport, btnSize, gap)
        resize(btnLocate, locSize, 0)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyResponsiveChromeSizing()
        if (DevicePrefs.getThemeMode(this) == "auto") {
            applyMapChromeTheme()
            if (mapStyleMode == MapStyleMode.VECTOR) loadMapStyle()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
