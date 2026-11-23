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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
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

class MainActivity : AppCompatActivity() {

    companion object {
        private const val HILLSHADE_SOURCE = "terrasync-dem-source"
        private const val HILLSHADE_LAYER = "terrasync-hillshade-layer"
        private const val TRACKS_SOURCE = "terrasync-tracks-source"
        private const val TRACKS_LAYER = "terrasync-tracks-layer"
        private const val ROUTE_SOURCE = "terrasync-route-source"
        private const val ROUTE_LAYER = "terrasync-route-layer"
        private const val ROUTE_CASING_LAYER = "terrasync-route-casing-layer"
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
            "Meter Missing / Damaged",
            "Wrong Location / Coordinates",
            "Closed / Nobody Home",
            "Safety Hazard / Network Fault"
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

        private const val STYLE_NIGHT_STREET = """
        {
          "version": 8,
          "glyphs": "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf",
          "sources": {
            "dark_osm": {
              "type": "raster",
              "tiles": ["https://a.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png"],
              "tileSize": 256,
              "maxzoom": 19
            }
          },
          "layers": [
            { "id": "dark-layer", "type": "raster", "source": "dark_osm", "minzoom": 0, "maxzoom": 22 }
          ]
        }
        """
    }

    private data class SharedPinTarget(val lat: Double, val lng: Double, val name: String, val pinId: String)

    private lateinit var mapView: MapView
    private lateinit var topBar: LinearLayout
    private lateinit var btnSyncStatus: ImageButton
    private lateinit var btnStyleToggle: ImageButton
    private lateinit var btnStatusFilter: ImageButton
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var btnLocate: ImageButton
    private lateinit var btnImport: ImageButton
    private lateinit var btnTabSearch: ImageButton
    private lateinit var btnTabBookmarks: ImageButton
    private lateinit var btnTabSettings: ImageButton
    private lateinit var btnTabAbout: ImageButton
    private lateinit var bottomDock: LinearLayout
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
    private lateinit var offlineMaps: OfflineMapManager
    private val repo = SyncRepo()

    private var layers: List<LayerItem> = emptyList()
    private var pins: List<PinItem> = emptyList()
    private var tracks: List<TrackItem> = emptyList()
    private var visibleStatuses: MutableSet<String> = mutableSetOf("pending", "done", "issue")
    private var isLiveConnected = false
    private var isSatellite = false
    private var isFollowingGps = false
    private var hasAutoZoomedInitial = false
    private var statusBarInsetTop = 0
    private var navBarInsetBottom = 0
    private var pendingSharedPin: SharedPinTarget? = null

    // Selected pin or tapped building/place marker
    private var selectedPinId: String? = null
    private var selectedMarkerLatLng: LatLng? = null
    private var selectedMarkerIcon: String = "pin-target"
    private var pulseAnimator: ValueAnimator? = null

    // Interpolated GPS arrow state
    private var currentLocation: Location? = null
    private var displayedLat: Double = 0.0
    private var displayedLng: Double = 0.0
    private var displayedBearing: Float = 0f
    private var gpsAnim: ValueAnimator? = null
    private var gpsTrackingActive = false

    // Route Preview & Active Turn-by-Turn Navigation State
    private var previewRoute: NavRoute? = null
    private var activeNavRoute: NavRoute? = null
    private var navDestLatLng: LatLng? = null
    private var navDestName: String = ""
    private var navCurrentStepIdx: Int = 0
    private var lastSpokenStepIdx: Int = -1
    private var lastRerouteTime: Long = 0L
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    // Full-screen overlay stack for smooth back navigation
    private val pageBackStack = mutableListOf<() -> Unit>()

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

        visibleStatuses = DevicePrefs.getVisibleStatuses(this).toMutableSet()
        isSatellite = DevicePrefs.isDefaultSatellite(this)

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
            statusBarInsetTop = sys.top
            navBarInsetBottom = sys.bottom
            (topBar.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(10)
            (navBanner.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(10)
            (bottomDock.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = sys.bottom + dp(10)
            sheetContent.setPadding(dp(16), 0, dp(16), sys.bottom + dp(20))
            topBar.requestLayout()
            navBanner.requestLayout()
            bottomDock.requestLayout()
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
                            previewRoute = null
                            renderMapData()
                        }
                        resetMapPadding()
                        bottomDock.animate().alpha(1f).setDuration(180).start()
                        bottomDock.visibility = View.VISIBLE
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

            m.addOnCameraMoveListener { updateScaleBar() }

            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && isFollowingGps) {
                    isFollowingGps = false
                    updateLocationIconTint()
                }
            }

            m.addOnMapClickListener { latLng ->
                handleMapTap(m, latLng)
                true
            }

            m.addOnMapLongClickListener { latLng ->
                showAddPinDialog(latLng)
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
        topBar = findViewById(R.id.topBar)
        btnSyncStatus = findViewById(R.id.btnSyncStatus)
        btnStyleToggle = findViewById(R.id.btnStyleToggle)
        btnStatusFilter = findViewById(R.id.btnStatusFilter)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        btnLocate = findViewById(R.id.btnLocate)
        btnImport = findViewById(R.id.btnImport)
        btnTabSearch = findViewById(R.id.btnTabSearch)
        btnTabBookmarks = findViewById(R.id.btnTabBookmarks)
        btnTabSettings = findViewById(R.id.btnTabSettings)
        btnTabAbout = findViewById(R.id.btnTabAbout)
        bottomDock = findViewById(R.id.bottomDock)
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

    private fun applyMapChromeTheme() {
        val night = DevicePrefs.isNightActive(this)
        val tileBg = Color.parseColor(if (night) "#E61E293B" else "#F7FFFFFF")
        val iconTint = Color.parseColor(if (night) "#F1F5F9" else "#334155")
        val sheetBg = Color.parseColor(if (night) "#1E293B" else "#FFFFFF")
        val handleColor = Color.parseColor(if (night) "#475569" else "#CBD5E1")
        val scaleColor = Color.parseColor(if (night || isSatellite) "#FFFFFF" else "#1E293B")

        UiAnimations.styleFloatingTile(this, btnStyleToggle, tileBg, 22f, 6f)
        UiAnimations.styleFloatingTile(this, btnStatusFilter, tileBg, 22f, 5f)
        UiAnimations.styleFloatingTile(this, btnSyncStatus, tileBg, 20f, 5f)
        UiAnimations.styleFloatingTile(this, btnZoomIn, tileBg, 22f, 6f)
        UiAnimations.styleFloatingTile(this, btnZoomOut, tileBg, 22f, 6f)
        UiAnimations.styleFloatingTile(this, btnImport, Color.parseColor("#0F766E"), 22f, 6f, "#44FFFFFF")
        UiAnimations.styleFloatingTile(this, btnLocate, tileBg, 24f, 7f)

        listOf(btnTabSearch, btnTabBookmarks, btnTabSettings, btnTabAbout).forEach { btn ->
            UiAnimations.styleFloatingTile(this, btn, tileBg, 15f, 7f)
            btn.setColorFilter(iconTint)
        }

        btnStyleToggle.setColorFilter(Color.parseColor(if (isSatellite) "#0EA5E9" else "#2563EB"))
        btnZoomIn.setColorFilter(iconTint)
        btnZoomOut.setColorFilter(iconTint)
        btnImport.setColorFilter(Color.WHITE)
        updateFilterIconTint()
        updateLocationIconTint()
        btnSyncStatus.setColorFilter(Color.parseColor(if (isLiveConnected) "#22C55E" else "#F59E0B"))

        tvScaleLabel.setTextColor(scaleColor)
        scaleBarLine.setBackgroundColor(scaleColor)

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

        btnStyleToggle.setOnClickListener {
            isSatellite = !isSatellite
            applyMapChromeTheme()
            loadMapStyle()
            Toast.makeText(this, if (isSatellite) "Satellite Hybrid" else "Street Terrain", Toast.LENGTH_SHORT).show()
        }

        btnStatusFilter.setOnClickListener { showMultiStatusFilterDialog() }

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

        findViewById<ImageButton>(R.id.btnNavStop).setOnClickListener { stopNavigation() }

        btnTabSearch.setOnClickListener { openFullPage(::buildSearchPage) }
        btnTabBookmarks.setOnClickListener { openFullPage(::buildBookmarksAndTracksPage) }
        btnTabSettings.setOnClickListener { openFullPage(::buildSettingsPage) }
        btnTabAbout.setOnClickListener { openFullPage(::buildAboutPage) }
    }

    private fun showMultiStatusFilterDialog() {
        val night = DevicePrefs.isNightActive(this)
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#0F172A")
        val dividerColor = Color.parseColor(if (night) "#334155" else "#E2E8F0")

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(6))
        }

        var updatingUi = false
        lateinit var cbAll: CheckBox
        val statusChecks = mutableMapOf<String, CheckBox>()

        fun syncCheckboxesAndMap() {
            updatingUi = true
            cbAll.isChecked = visibleStatuses.containsAll(DevicePrefs.ALL_STATUSES)
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
            isChecked = visibleStatuses.containsAll(DevicePrefs.ALL_STATUSES)
            setPadding(dp(8), dp(10), dp(8), dp(10))
            setOnCheckedChangeListener { _, checked ->
                if (updatingUi) return@setOnCheckedChangeListener
                if (checked) {
                    visibleStatuses.clear()
                    visibleStatuses.addAll(DevicePrefs.ALL_STATUSES)
                } else {
                    visibleStatuses.clear()
                }
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

        AlertDialog.Builder(this)
            .setTitle("Filter Pins by Status")
            .setView(box)
            .setPositiveButton("Done", null)
            .show()
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
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
                if (sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                    hideInScreenSheet()
                    return
                }
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        })
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

        // 1. Open KML / KMZ files tapped from WhatsApp, Telegram, or file managers
        if (scheme == "content" || scheme == "file") {
            handleImportUri(data)
            return
        }

        // 2. Shared TerraSync pin link
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

        // 3. Join shared layer link
        val code = data.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            joinShareCode(code)
        }
    }

    private fun openSharedPinTarget(target: SharedPinTarget) {
        val matched = if (target.pinId.isNotBlank()) pins.find { it.id == target.pinId } else null
        if (matched != null) {
            selectPinAndOpenCard(matched, zoomIfFar = true)
            return
        }
        selectedPinId = null
        selectedMarkerLatLng = LatLng(target.lat, target.lng)
        selectedMarkerIcon = "pin-target"
        renderMapData()
        centerUpperHalf(LatLng(target.lat, target.lng), (map?.cameraPosition?.zoom ?: 15.0).coerceAtLeast(16.0))
        showTappedPlaceCard(target.name, "Shared via TerraSync", target.lat, target.lng)
    }

    // --- Map Style, 3D Hillshading & Vector Layers (No Cluster Circles) ---

    private fun loadMapStyle() {
        val m = map ?: return
        val night = DevicePrefs.isNightActive(this)
        val builder = when {
            isSatellite -> Style.Builder().fromJson(STYLE_SATELLITE)
            night -> Style.Builder().fromJson(STYLE_NIGHT_STREET)
            else -> Style.Builder().fromUri(OfflineMapManager.STYLE_STREET_URL)
        }
        m.setStyle(builder) { style ->
            registerMapBitmaps(style)
            attachTerrainHillshade(style)
            attachOverlayLayers(style)
            renderMapData()
        }
    }

    private fun registerMapBitmaps(style: Style) {
        style.addImage("pin-pending", MapBitmaps.createTeardropPin(this, "#F59E0B", 1.0f))
        style.addImage("pin-done", MapBitmaps.createTeardropPin(this, "#22C55E", 1.0f))
        style.addImage("pin-issue", MapBitmaps.createTeardropPin(this, "#EF4444", 1.0f))
        style.addImage("pin-target", MapBitmaps.createTeardropPin(this, "#2563EB", 1.05f))
        style.addImage("gps-arrow", MapBitmaps.createGpsArrow(this))
    }

    private fun attachTerrainHillshade(style: Style) {
        if (isSatellite || DevicePrefs.isNightActive(this) || !DevicePrefs.isHillshadeEnabled(this)) return
        runCatching {
            if (style.getSource(HILLSHADE_SOURCE) == null) {
                val demFile = java.io.File(cacheDir, "terrarium_dem.json")
                if (!demFile.exists()) {
                    demFile.writeText("""{"tilejson":"2.2.0","tiles":["https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"],"minzoom":0,"maxzoom":14,"encoding":"terrarium"}""")
                }
                style.addSource(RasterDemSource(HILLSHADE_SOURCE, demFile.toURI()))
            }
            if (style.getLayer(HILLSHADE_LAYER) == null) {
                val hill = HillshadeLayer(HILLSHADE_LAYER, HILLSHADE_SOURCE).withProperties(
                    PropertyFactory.hillshadeExaggeration(0.42f),
                    PropertyFactory.hillshadeShadowColor(Color.parseColor("#546B46")),
                    PropertyFactory.hillshadeHighlightColor(Color.parseColor("#FFFFFF")),
                    PropertyFactory.hillshadeAccentColor(Color.parseColor("#6F8A5B")),
                    PropertyFactory.hillshadeIlluminationDirection(315f)
                )
                val belowLayer = style.layers.firstOrNull { l ->
                    val id = l.id.lowercase()
                    id.contains("water") || id.contains("road") || id.contains("highway") || id.contains("building")
                }
                if (belowLayer != null) {
                    style.addLayerBelow(hill, belowLayer.id)
                } else {
                    style.addLayer(hill)
                }
            }
        }
    }

    private fun attachOverlayLayers(style: Style) {
        val night = DevicePrefs.isNightActive(this) || isSatellite
        val labelColor = if (night) "#F8FAFC" else "#0F172A"
        val haloColor = if (night) "#0F172A" else "#FFFFFF"
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
                    PropertyFactory.lineColor("#2563EB"),
                    PropertyFactory.lineWidth(6.2f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
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
            // Direct unclustered pins (no cluster circles)
            style.addSource(GeoJsonSource(PINS_SOURCE))

            style.addLayer(
                SymbolLayer(PINS_LAYER, PINS_SOURCE).withProperties(
                    PropertyFactory.iconImage(Expression.get("icon")),
                    PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                    PropertyFactory.iconAllowOverlap(true),
                    PropertyFactory.iconIgnorePlacement(true)
                )
            )

            // User-toggled Bookmark Titles Layer
            style.addLayer(
                SymbolLayer(PINS_LABELS_LAYER, PINS_SOURCE).withProperties(
                    PropertyFactory.visibility(if (showTitles) Property.VISIBLE else Property.NONE),
                    PropertyFactory.textField(Expression.get("name")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(11f),
                    PropertyFactory.textColor(labelColor),
                    PropertyFactory.textHaloColor(haloColor),
                    PropertyFactory.textHaloWidth(1.6f),
                    PropertyFactory.textOffset(arrayOf(0f, 0.35f)),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textAllowOverlap(false),
                    PropertyFactory.textOptional(true)
                )
            )
        }

        // Selected Pin Halo & Subtle Pop
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

        // Smooth Directional GPS Arrow
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
        return pins.filter { p ->
            val normStatus = p.status.lowercase().ifBlank { "pending" }
            p.layerId !in hiddenLayerIds && normStatus in visibleStatuses
        }
    }

    private fun findExactStackedPins(pin: PinItem): List<PinItem> {
        val stack = getVisiblePins()
            .filter { it.lat == pin.lat && it.lng == pin.lng }
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
            Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                addStringProperty("id", p.id)
                addStringProperty("name", p.name)
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
        val routeFeatures = if (routeToShow != null && routeToShow.geometry.size >= 2) {
            listOf(Feature.fromGeometry(LineString.fromLngLats(routeToShow.geometry.map { Point.fromLngLat(it.first, it.second) })))
        } else emptyList()
        style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(routeFeatures))

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

    private fun updateGpsLayerFeature(lat: Double, lng: Double, bearing: Float) {
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        val f = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addNumberProperty("bearing", bearing)
        }
        style.getSourceAs<GeoJsonSource>(USER_LOC_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(listOf(f)))
    }

    private fun fireStatusPulse(lat: Double, lng: Double, statusHex: String) {
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        val f = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("color", statusHex)
        }
        style.getSourceAs<GeoJsonSource>(PULSE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(listOf(f)))
        val layer = style.getLayerAs<CircleLayer>(PULSE_LAYER) ?: return

        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val v = anim.animatedValue as Float
                layer.setProperties(
                    PropertyFactory.circleRadius(8f + 28f * v),
                    PropertyFactory.circleOpacity(0.65f * (1f - v))
                )
            }
            start()
        }
    }

    // --- Map Tap Handling (Zero Auto-Zoom) ---

    private fun handleMapTap(m: MapLibreMap, latLng: LatLng) {
        val screenPt = m.projection.toScreenLocation(latLng)
        val hitBox = RectF(screenPt.x - 42f, screenPt.y - 52f, screenPt.x + 42f, screenPt.y + 24f)

        // 1. Check user KML/synced pins first
        val pinHit = m.queryRenderedFeatures(hitBox, PINS_LAYER).firstOrNull()
        val pinId = pinHit?.getStringProperty("id")
        val pin = pins.find { it.id == pinId }
        if (pin != null) {
            selectPinAndOpenCard(pin, zoomIfFar = false)
            return
        }

        // 2. Tap-Anywhere on map: keep exact current zoom level
        val smallBox = RectF(screenPt.x - 24f, screenPt.y - 24f, screenPt.x + 24f, screenPt.y + 24f)
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
            if (place != null && selectedMarkerLatLng == latLng && selectedPinId == null) {
                runOnUiThread {
                    val bestTitle = if (detectedTitle.isNotBlank()) detectedTitle else place.title
                    val bestSub = place.subtitle.ifBlank { detectedSub }
                    showTappedPlaceCard(bestTitle, bestSub, latLng.latitude, latLng.longitude)
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
        bottomDock.animate().alpha(0f).setDuration(150).withEndAction {
            if (sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                bottomDock.visibility = View.GONE
            }
        }.start()
        sheetBehavior.peekHeight = dp(peekDp) + navBarInsetBottom
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

    // --- Polished Pin Card (Consistent Spacing/Typography, Exact Stacked Cycler, Owner-Only Delete) ---

    private fun showPinCard(pin: PinItem, animateSwap: Boolean = false) {
        val night = DevicePrefs.isNightActive(this)
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
        val btnStar = ImageButton(this).apply {
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
        }
        headerRow.addView(btnStar)

        headerRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_share)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setPadding(dp(7), dp(7), dp(7), dp(7))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                val msg = makePinShareMessage(pin.name, pin.lat, pin.lng, pin.id)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
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
        }

        // 4. Status Buttons (Uniform height, font size, and gaps)
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
                    } else {
                        repo.updatePinStatus(pin, key, stamp)
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

        // 5. Primary Actions: Route To + Edit + External (Balanced widths, never clipped)
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        actionRow.addView(MaterialButton(this).apply {
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
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.2f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { openRoutePreview(pin.lat, pin.lng, pin.name) }
        })
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
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 0.9f).apply { marginEnd = dp(8) }
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
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.0f)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                val label = Uri.encode(pin.name)
                val geoUri = Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}($label)")
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, geoUri), "Open in"))
            }
        })
        sheetContent.addView(actionRow)

        // 6. Expanded Details Section
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

        if (pin.editHistory.isNotBlank()) {
            sheetContent.addView(TextView(this).apply {
                text = "Edit Log:\n${pin.editHistory}"
                textSize = 12f
                setLineSpacing(dp(2).toFloat(), 1.0f)
                setTextColor(txtSub)
                setPadding(0, 0, 0, dp(10))
            })
        }

        // 7. Owner-Only Pin Deletion (Device-ID Ownership)
        val myDevId = DevicePrefs.getDeviceId(this)
        val isOwner = layer == null || layer.creatorDeviceId.isBlank() || layer.creatorDeviceId.equals(myDevId, ignoreCase = true)
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
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Delete \"${pin.name}\"?")
                        .setMessage("This removes the pin for everyone in this folder.")
                        .setPositiveButton("Delete") { _, _ ->
                            repo.deletePin(pin.id)
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

        val peekHeight = if (stackedPins.size > 1) 292 else 252
        showInScreenSheet(peekDp = peekHeight)
    }

    // --- Tap-Anywhere Building / Place Card ---

    private fun showTappedPlaceCard(title: String, subtitle: String, lat: Double, lng: Double) {
        val night = DevicePrefs.isNightActive(this)
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#0F172A")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val chipBg = Color.parseColor(if (night) "#334155" else "#F1F5F9")

        sheetContent.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(4) }
        }
        header.addView(TextView(this).apply {
            text = title
            textSize = 17f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        })
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_share)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setPadding(dp(7), dp(7), dp(7), dp(7))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener {
                val msg = makePinShareMessage(title, lat, lng)
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
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
        if (subLine.isNotBlank()) {
            sheetContent.addView(TextView(this).apply {
                text = subLine
                textSize = 13f
                setTextColor(Color.parseColor("#2563EB"))
                setTypeface(null, Typeface.BOLD)
                setPadding(0, 0, 0, dp(3))
            })
        }
        sheetContent.addView(TextView(this).apply {
            text = "${"%.6f".format(lat)}, ${"%.6f".format(lng)}"
            textSize = 12.5f
            setTextColor(txtSub)
            setPadding(0, 0, 0, dp(12))
        })

        val btnRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnRow.addView(MaterialButton(this).apply {
            text = "Route To"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.2f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { openRoutePreview(lat, lng, title) }
        })
        btnRow.addView(MaterialButton(this).apply {
            text = "Save Pin"
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#0F766E"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                showAddPinDialog(LatLng(lat, lng), prefillName = if (title == "Selected Location") "" else title, prefillDesc = subtitle)
            }
        })
        btnRow.addView(MaterialButton(this).apply {
            text = "Copy GPS"
            textSize = 12.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(txtPrimary)
            setBackgroundColor(chipBg)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 0.95f)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { copyToClipboard("GPS", "$lat, $lng") }
        })
        sheetContent.addView(btnRow)

        showInScreenSheet(peekDp = 185)
    }

    // --- Route Preview Before Starting Navigation ---

    private fun openRoutePreview(destLat: Double, destLng: Double, destTitle: String) {
        if (!hasLocationPermission()) {
            locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        startLiveGpsUpdates()
        val loc = currentLocation
        if (loc == null) {
            Toast.makeText(this, "Waiting for GPS signal to build route...", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(this, "Building route preview...", Toast.LENGTH_SHORT).show()
        RoutingEngine.fetchDrivingRoute(loc.latitude, loc.longitude, destLat, destLng) { route, err ->
            runOnUiThread {
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
                        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(b.build(), dp(48), dp(80), dp(48), dp(250)), 600)
                    }
                }
                showRoutePreviewCard(route, destLat, destLng, destTitle)
            }
        }
    }

    private fun showRoutePreviewCard(route: NavRoute, destLat: Double, destLng: Double, destTitle: String) {
        val night = DevicePrefs.isNightActive(this)
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#0F172A")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val chipBg = Color.parseColor(if (night) "#334155" else "#F1F5F9")

        sheetContent.removeAllViews()

        val etaMin = (route.totalDurationSeconds / 60.0).toInt().coerceAtLeast(1)
        val distStr = formatDistanceShort(route.totalDistanceMeters)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "$etaMin min  ($distStr)"
            textSize = 19f
            setTextColor(Color.parseColor("#2563EB"))
            setTypeface(null, Typeface.BOLD)
        })
        titleCol.addView(TextView(this).apply {
            text = "To: $destTitle  •  ${route.steps.size} steps"
            textSize = 13f
            setTextColor(txtSub)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        header.addView(titleCol)

        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(txtSub)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener {
                previewRoute = null
                renderMapData()
                hideInScreenSheet()
            }
        })
        sheetContent.addView(header)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        btnRow.addView(MaterialButton(this).apply {
            text = "Start Navigation"
            textSize = 13.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1.4f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
            setOnClickListener { confirmStartNavigation(route, destLat, destLng, destTitle) }
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
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 0.8f)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                val label = Uri.encode(destTitle)
                val geoUri = Uri.parse("geo:$destLat,$destLng?q=$destLat,$destLng($label)")
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, geoUri), "Open in"))
            }
        })
        sheetContent.addView(btnRow)

        showInScreenSheet(peekDp = 170)
    }

    private fun confirmStartNavigation(route: NavRoute, destLat: Double, destLng: Double, destTitle: String) {
        val loc = currentLocation ?: return
        activeNavRoute = route
        previewRoute = null
        navDestLatLng = LatLng(destLat, destLng)
        navDestName = destTitle
        navCurrentStepIdx = if (route.steps.size > 1) 1 else 0
        lastSpokenStepIdx = -1

        hideInScreenSheet()
        topBar.visibility = View.GONE
        navBanner.visibility = View.VISIBLE
        isFollowingGps = true
        updateLocationIconTint()
        renderMapData()
        updateNavigationProgress(loc, route, LatLng(destLat, destLng))

        val cam = CameraPosition.Builder()
            .target(LatLng(loc.latitude, loc.longitude))
            .zoom(17.0)
            .tilt(48.0)
            .bearing(if (loc.hasBearing()) loc.bearing.toDouble() else 0.0)
            .padding(0.0, 0.0, 0.0, 0.0)
            .build()
        map?.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 700)
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

        if (route.steps.isNotEmpty()) {
            navCurrentStepIdx = navCurrentStepIdx.coerceIn(0, route.steps.lastIndex)
            while (navCurrentStepIdx < route.steps.lastIndex) {
                val s = route.steps[navCurrentStepIdx]
                val d = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, s.lat, s.lng)
                if (d < 30.0) navCurrentStepIdx++ else break
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
        tvNavMeta.text = "In ${formatDistanceShort(distToStep)}  •  ${formatDistanceShort(remainingRoadMeters)} left (~$etaMin min)"

        if (navCurrentStepIdx != lastSpokenStepIdx && distToStep < 250.0) {
            lastSpokenStepIdx = navCurrentStepIdx
            speakPrompt("In ${formatDistanceShort(distToStep)}, ${step.instruction}")
        }

        if (isFollowingGps) {
            val cam = CameraPosition.Builder()
                .target(LatLng(loc.latitude, loc.longitude))
                .zoom(17.0)
                .tilt(48.0)
                .bearing(if (loc.hasBearing()) loc.bearing.toDouble() else (map?.cameraPosition?.bearing ?: 0.0))
                .build()
            map?.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 700)
        }

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
        previewRoute = null
        navDestLatLng = null
        navCurrentStepIdx = 0
        navBanner.visibility = View.GONE
        topBar.visibility = View.VISIBLE
        renderMapData()
        map?.animateCamera(CameraUpdateFactory.tiltTo(0.0), 400)
    }

    private fun speakPrompt(text: String) {
        if (ttsReady && DevicePrefs.isVoiceNav(this)) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "nav_prompt")
        }
    }

    // --- Live GPS Tracking with Smooth Arrow Interpolation ---

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
        if (bestLast != null) {
            currentLocation = bestLast
            displayedLat = bestLast.latitude
            displayedLng = bestLast.longitude
            if (bestLast.hasBearing()) displayedBearing = bestLast.bearing
            updateGpsLayerFeature(displayedLat, displayedLng, displayedBearing)
        }

        providers.forEach { provider ->
            runCatching {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 1200L, 1.5f, gpsListener)
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
        val prevLat = if (currentLocation == null) loc.latitude else displayedLat
        val prevLng = if (currentLocation == null) loc.longitude else displayedLng
        val prevBearing = displayedBearing
        val targetBearing = if (loc.hasBearing() && loc.speed > 0.5f) loc.bearing else prevBearing
        var deltaBearing = (targetBearing - prevBearing) % 360f
        if (deltaBearing > 180f) deltaBearing -= 360f
        if (deltaBearing < -180f) deltaBearing += 360f

        currentLocation = loc

        gpsAnim?.cancel()
        gpsAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650L
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                displayedLat = prevLat + (loc.latitude - prevLat) * t
                displayedLng = prevLng + (loc.longitude - prevLng) * t
                displayedBearing = (prevBearing + deltaBearing * t + 360f) % 360f
                updateGpsLayerFeature(displayedLat, displayedLng, displayedBearing)
            }
            start()
        }

        val activeRoute = activeNavRoute
        val dest = navDestLatLng
        if (activeRoute != null && dest != null) {
            updateNavigationProgress(loc, activeRoute, dest)
        } else if (isFollowingGps && DevicePrefs.isAutoFollow(this)) {
            map?.animateCamera(CameraUpdateFactory.newLatLng(LatLng(loc.latitude, loc.longitude)), 600)
        }
    }

    private fun updateLocationIconTint() {
        val night = DevicePrefs.isNightActive(this)
        val idle = if (night) "#F1F5F9" else "#334155"
        btnLocate.setColorFilter(Color.parseColor(if (isFollowingGps) "#2563EB" else idle))
    }

    private fun updateFilterIconTint() {
        val hex = when {
            visibleStatuses.containsAll(DevicePrefs.ALL_STATUSES) ->
                if (DevicePrefs.isNightActive(this)) "#F1F5F9" else "#334155"
            visibleStatuses == setOf("pending") -> "#F59E0B"
            visibleStatuses == setOf("issue") -> "#EF4444"
            visibleStatuses == setOf("done") -> "#22C55E"
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

    private fun openFullPage(builder: () -> View) {
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
        pageBackStack.add(onBack)
        pageOverlayContainer.removeAllViews()
        val v = builder()
        pageOverlayContainer.addView(v)
        v.alpha = 0f
        v.translationX = dp(24).toFloat()
        v.animate().alpha(1f).translationX(0f).setDuration(190).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun closeFullPage() {
        offlineMaps.setStateListener(null)
        pageBackStack.clear()
        pageOverlayContainer.animate()
            .alpha(0f)
            .translationY(dp(20).toFloat())
            .setDuration(170)
            .withEndAction {
                pageOverlayContainer.visibility = View.GONE
                pageOverlayContainer.removeAllViews()
            }
            .start()
    }

    private fun makePageFrame(title: String, onBack: () -> Unit, rightAction: View? = null): Pair<LinearLayout, LinearLayout> {
        val night = DevicePrefs.isNightActive(this)
        val bg = Color.parseColor(if (night) "#0F172A" else "#FFFFFF")
        val txt = Color.parseColor(if (night) "#F8FAFC" else "#0F172A")

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(0, statusBarInsetTop, 0, navBarInsetBottom)
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

    // --- Page 2: Unified Bookmarks (Each KML is a Folder + Cross-Folder Search + Bottom Toggles) ---

    private fun buildBookmarksAndTracksPage(): View {
        val night = DevicePrefs.isNightActive(this)
        val bg = Color.parseColor(if (night) "#0F172A" else "#FFFFFF")
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val cardBg = Color.parseColor(if (night) "#1E293B" else "#F3F4F6")
        val dividerColor = Color.parseColor(if (night) "#334155" else "#E2E8F0")

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(0, statusBarInsetTop, 0, navBarInsetBottom)
        }

        // AppBar
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
                menu.menu.add(0, 3, 3, "Nearest Pending Pin")
                menu.setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        1 -> { closeFullPage(); kmlPicker.launch(arrayOf("*/*")) }
                        2 -> showJoinCodeDialog()
                        3 -> jumpToNearestPendingPin()
                    }
                    true
                }
                menu.show()
            }
        })
        outer.addView(appBar)

        // Cross-Folder Search Bar
        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            ).apply {
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

        // Middle Scrollable Container (Folders or Cross-Folder Search Matches)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(16))
        }
        scroll.addView(body)
        outer.addView(scroll)

        // Pinned Bottom Toggles Panel (Show all bookmarks on map + Show bookmark titles)
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
        showAllCol.addView(TextView(this).apply {
            text = "${pins.size} bookmarks"
            textSize = 12.5f
            setTextColor(txtSub)
        })
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
        val swShowTitles = MaterialSwitch(this).apply {
            isChecked = DevicePrefs.isShowBookmarkTitles(this@MainActivity)
            setOnCheckedChangeListener { _, checked ->
                DevicePrefs.setShowBookmarkTitles(this@MainActivity, checked)
                renderMapData()
            }
        }
        rowShowTitles.addView(swShowTitles)
        footer.addView(rowShowTitles)
        outer.addView(footer)

        val folderNameById = layers.associate { it.id to it.name }
        val myDevId = DevicePrefs.getDeviceId(this)

        fun syncMasterVisibilitySwitch() {
            val anyVisible = layers.isNotEmpty() && layers.any { DevicePrefs.isLayerVisible(this, it.id) }
            suppressAllSwitch = true
            swShowAll.isChecked = anyVisible
            suppressAllSwitch = false
        }

        fun renderFoldersOrSearch(query: String) {
            body.removeAllViews()
            val trimmed = query.trim()

            // Cross-Folder Search Results Mode
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
                            if (!DevicePrefs.isLayerVisible(this@MainActivity, pin.layerId)) {
                                DevicePrefs.setLayerVisible(this@MainActivity, pin.layerId, true)
                                renderMapData()
                            }
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
                        if (pin.status.equals("issue", true) && pin.issueReason.isNotBlank()) {
                            append(" • ⚠ ${pin.issueReason}")
                        }
                    }
                    col.addView(TextView(this).apply {
                        text = sub
                        textSize = 12f
                        setTextColor(if (pin.status.equals("issue", true)) Color.parseColor("#DC2626") else txtSub)
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

            // Default Unified KML Folders View
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

            layers.forEach { layer ->
                addDividerIfNeeded()
                val layerPins = pins.filter { it.layerId == layer.id }
                val layerTracks = tracks.filter { it.layerId == layer.id }
                val visible = DevicePrefs.isLayerVisible(this, layer.id)
                val isCreator = layer.creatorDeviceId.isBlank() || layer.creatorDeviceId.equals(myDevId, ignoreCase = true)

                val doneCnt = layerPins.count { it.status.equals("done", true) }
                val issueCnt = layerPins.count { it.status.equals("issue", true) }
                val pendingCnt = (layerPins.size - doneCnt - issueCnt).coerceAtLeast(0)

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(12), dp(6), dp(12))
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                }

                val eyeBtn = ImageButton(this).apply {
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
                }
                row.addView(eyeBtn)

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
                infoCol.addView(TextView(this).apply {
                    text = layer.name
                    textSize = 15.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.MIDDLE
                })
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
                        menu.menu.add(0, 1, 1, "Share Link & Code (${layer.shareCode})")
                        menu.menu.add(0, 2, 2, "Zoom to Folder")
                        menu.menu.add(0, 3, 3, "Export as KML")
                        menu.menu.add(0, 4, 4, "Export as CSV")
                        menu.menu.add(0, 5, 5, "Leave Folder (This Phone)")
                        if (isCreator) menu.menu.add(0, 6, 6, "Delete from Cloud (All Users)")
                        menu.setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                1 -> shareLayerLink(layer)
                                2 -> { closeFullPage(); zoomToPoints(layerPins.map { LatLng(it.lat, it.lng) }) }
                                3 -> KmlCsvExporter.exportAndShareKml(this@MainActivity, layer, layerPins, layerTracks)
                                4 -> KmlCsvExporter.exportAndShareCsv(this@MainActivity, layer, layerPins)
                                5 -> {
                                    DevicePrefs.removeJoinedLayerId(this@MainActivity, layer.id)
                                    refreshJoinedSync()
                                    openFullPage(::buildBookmarksAndTracksPage)
                                }
                                6 -> {
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("Delete \"${layer.name}\" from Cloud?")
                                        .setMessage("This permanently removes all bookmarks for every team member.")
                                        .setPositiveButton("Delete Cloud") { _, _ ->
                                            DevicePrefs.removeJoinedLayerId(this@MainActivity, layer.id)
                                            repo.deleteLayer(layer.id, layerPins.map { it.id }, layerTracks.map { it.id })
                                            refreshJoinedSync()
                                            openFullPage(::buildBookmarksAndTracksPage)
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .show()
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

        etFolderSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString() ?: ""
                btnClearSearch.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
                renderFoldersOrSearch(q)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        renderFoldersOrSearch("")
        return outer
    }

    // --- Subpage: Folder Bookmarks List ---

    private fun buildPinsListPage(title: String, sourcePins: List<PinItem>, layer: LayerItem?): View {
        val night = DevicePrefs.isNightActive(this)
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val cardBg = Color.parseColor(if (night) "#1E293B" else "#F3F4F6")
        val dividerColor = Color.parseColor(if (night) "#334155" else "#E2E8F0")

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
                closeFullPage()
                zoomToPoints(sourcePins.map { LatLng(it.lat, it.lng) })
            }
        }

        val (outer, body) = makePageFrame(title, {
            if (pageBackStack.isNotEmpty()) pageBackStack.removeAt(pageBackStack.lastIndex).invoke() else closeFullPage()
        }, btnZoom)

        var sortNearest = currentLocation != null
        var filterKey = "all"

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
            setPadding(dp(10), 0, dp(10), 0)
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
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(4) }
            }
            filterBtns[k] = b
            controlsRow.addView(b)
        }
        body.addView(controlsRow)

        val listCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(cardBg)
                cornerRadius = dp(18).toFloat()
            }
            clipToOutline = true
        }
        body.addView(listCard)

        fun renderPinRows() {
            btnSort.text = if (sortNearest) "Nearest ↓" else "A–Z ↓"
            filterBtns.forEach { (k, b) ->
                val active = k == filterKey
                b.setBackgroundColor(Color.parseColor(if (active) "#2563EB" else if (night) "#334155" else "#E2E8F0"))
                b.setTextColor(if (active) Color.WHITE else txtPrimary)
            }

            listCard.removeAllViews()
            val loc = currentLocation
            val filtered = sourcePins.filter { filterKey == "all" || it.status.equals(filterKey, true) }
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

            ordered.forEachIndexed { idx, pin ->
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
                if (pin.isEdited && pin.editedBy.isNotBlank()) subParts += "Edited by ${pin.editedBy}"
                col.addView(TextView(this).apply {
                    text = subParts.joinToString(" • ")
                    textSize = 12f
                    setTextColor(if (pin.status.equals("issue", true)) Color.parseColor("#DC2626") else txtSub)
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
        }

        btnSort.setOnClickListener {
            sortNearest = !sortNearest
            renderPinRows()
        }
        filterBtns.forEach { (k, b) ->
            b.setOnClickListener { filterKey = k; renderPinRows() }
        }

        if (layer != null && layer.shareCode.isNotBlank()) {
            body.addView(TextView(this).apply {
                text = "Share Code: ${layer.shareCode}"
                textSize = 12.5f
                setTextColor(txtSub)
                setPadding(dp(6), dp(10), dp(6), 0)
            })
        }

        renderPinRows()
        return outer
    }

    private fun jumpToNearestPendingPin() {
        val loc = currentLocation
        if (loc == null) {
            Toast.makeText(this, "Waiting for GPS fix first", Toast.LENGTH_SHORT).show()
            return
        }
        val hidden = DevicePrefs.getHiddenLayerIds(this)
        val pending = pins.filter { it.layerId !in hidden && it.status.equals("pending", true) }
        val nearest = pending.minByOrNull { RoutingEngine.haversineMeters(loc.latitude, loc.longitude, it.lat, it.lng) }
        if (nearest == null) {
            Toast.makeText(this, "No pending pins in visible folders!", Toast.LENGTH_SHORT).show()
            return
        }
        closeFullPage()
        selectPinAndOpenCard(nearest, zoomIfFar = true)
    }

    // --- Page 1: Full-Screen Search Page ---

    private fun buildSearchPage(): View {
        val night = DevicePrefs.isNightActive(this)
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val cardBg = Color.parseColor(if (night) "#1E293B" else "#F3F4F6")

        val (outer, body) = makePageFrame("Search", { closeFullPage() })

        val etQuery = EditText(this).apply {
            hint = "Pin name, issue, street, or 36.86, 42.99..."
            setTextColor(txtPrimary)
            setHintTextColor(txtSub)
            setSingleLine(true)
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
            textSize = 13f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.3f).apply { marginEnd = dp(8) }
            UiAnimations.attachSpringTouch(this)
        }
        val btnNearest = MaterialButton(this).apply {
            text = "Nearest Pending"
            textSize = 12.5f
            isAllCaps = false
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

        val resultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(resultsBox)

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

            val matches = if (trimmed.isEmpty()) {
                pins.take(30)
            } else {
                pins.filter {
                    it.name.contains(trimmed, true) ||
                        it.description.contains(trimmed, true) ||
                        it.issueReason.contains(trimmed, true) ||
                        statusLabel(it.status).contains(trimmed, true)
                }
            }

            val loc = currentLocation
            matches.forEach { pin ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(11), dp(8), dp(11))
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    setOnClickListener {
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
                row.addView(TextView(this).apply {
                    text = "${pin.name} (${statusLabel(pin.status)})"
                    textSize = 14f
                    setTextColor(txtPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (loc != null) {
                    val d = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, pin.lat, pin.lng)
                    row.addView(TextView(this).apply {
                        text = formatDistanceShort(d)
                        textSize = 12f
                        setTextColor(txtSub)
                    })
                }
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
                    btnWebSearch.text = "Search Map Places"
                    if (places.isEmpty()) {
                        Toast.makeText(this, "No places found", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    places.reversed().forEach { place ->
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
                        resultsBox.addView(row, 0)
                    }
                }
            }
        }

        return outer
    }

    // --- Page 3: Full-Screen Settings Page ---

    private fun buildSettingsPage(): View {
        val night = DevicePrefs.isNightActive(this)
        val txtPrimary = Color.parseColor(if (night) "#F8FAFC" else "#1E293B")
        val txtSub = Color.parseColor(if (night) "#94A3B8" else "#64748B")
        val cardBg = Color.parseColor(if (night) "#1E293B" else "#F3F4F6")

        val (outer, body) = makePageFrame("Settings", { closeFullPage() })

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
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(10)
            UiAnimations.attachSpringTouch(this)
            setOnClickListener {
                DevicePrefs.setCustomName(this@MainActivity, etDeviceName.text.toString())
                val updated = DevicePrefs.getDeviceStamp(this@MainActivity)
                tvCurrentStamp.text = updated
                Toast.makeText(this@MainActivity, "Updated: $updated", Toast.LENGTH_SHORT).show()
            }
        })
        body.addView(nameRow)

        body.addView(TextView(this).apply {
            text = "Map Theme (Night Mode)"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dp(4), 0, dp(6))
        })
        val themeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(12))
        }
        val curNight = DevicePrefs.getNightMode(this)
        listOf(0 to "Day", 1 to "Auto", 2 to "Night").forEachIndexed { idx, (mode, label) ->
            val active = mode == curNight
            themeRow.addView(MaterialButton(this).apply {
                text = label
                textSize = 12.5f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setTextColor(if (active) Color.WHITE else txtPrimary)
                setBackgroundColor(Color.parseColor(if (active) "#2563EB" else if (night) "#334155" else "#E2E8F0"))
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                    if (idx < 2) marginEnd = dp(6)
                }
                UiAnimations.attachSpringTouch(this)
                setOnClickListener {
                    DevicePrefs.setNightMode(this@MainActivity, mode)
                    applyMapChromeTheme()
                    loadMapStyle()
                    openFullPage(::buildSettingsPage)
                }
            })
        }
        body.addView(themeRow)

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

        addSwitchRow("3D Mountain Hillshading", DevicePrefs.isHillshadeEnabled(this)) {
            DevicePrefs.setHillshadeEnabled(this, it)
            loadMapStyle()
        }
        addSwitchRow("Show Bookmark Titles on Map", DevicePrefs.isShowBookmarkTitles(this)) {
            DevicePrefs.setShowBookmarkTitles(this, it)
            renderMapData()
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

        body.addView(TextView(this).apply {
            text = "Offline Street Maps (Optional)"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2563EB"))
            setPadding(0, dp(16), 0, dp(2))
        })
        body.addView(TextView(this).apply {
            text = "Online by default. Download a regional vector street pack to use the regular map with zero signal."
            textSize = 12f
            setTextColor(txtSub)
            setPadding(0, 0, 0, dp(10))
        })

        val packUpdaters = mutableListOf<() -> Unit>()
        val pixelRatio = resources.displayMetrics.density

        fun addOfflineCard(packId: String, title: String, subtitle: String, onDownload: () -> Unit) {
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
                if (st.isDownloading) offlineMaps.pauseDownload(packId) else onDownload()
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

        body.addView(MaterialButton(this).apply {
            text = "Clear Temporary Map Cache"
            textSize = 12.5f
            isAllCaps = false
            setTextColor(txtPrimary)
            setBackgroundColor(cardBg)
            cornerRadius = dp(10)
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

    // --- Page 4: Dedicated About Page ---

    private fun buildAboutPage(): View {
        val night = DevicePrefs.isNightActive(this)
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
            text = "Version 2.4.0 • Kotlin + MapLibre GL"
            textSize = 13f
            setTextColor(Color.parseColor("#0F766E"))
            setTypeface(null, Typeface.BOLD)
        })
        hero.addView(heroText)
        body.addView(hero)

        body.addView(TextView(this).apply {
            text = "High-performance field mapping with unified KML bookmark folders, cross-folder search, exact-coordinate stacked pin cycling, multi-status filtering, owner-protected pins, offline vector maps, and turn-by-turn navigation."
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
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            })
        }

        return outer
    }

    // --- Dialogs: Issue Reason Pop-Up, Edit Pin, Add Manual Pin, Join Code ---

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
            setText(pin.issueReason)
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
                repo.updatePinStatus(pin, "issue", stamp, reason)
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
                repo.updatePinText(pin, etName.text.toString().trim(), etDesc.text.toString().trim(), stamp)
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
                    devId
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
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
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

    // --- KML Import & Firestore Sync ---

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
                repo.importLayer(layerId, fileName.ifBlank { "Imported Folder" }, stamp, devId, parsed) { ok, msg, _ ->
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
                    if (pageOverlayContainer.visibility == View.VISIBLE) {
                        openFullPage(::buildBookmarksAndTracksPage)
                    }
                }
            }
        }
    }

    // --- Utilities ---

    private fun applyKeepAwakeFlag() {
        if (DevicePrefs.isKeepAwake(this)) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        pulseAnimator?.cancel()
        gpsAnim?.cancel()
        offlineMaps.setStateListener(null)
        tts?.shutdown()
        repo.stopListening()
        mapView.onDestroy()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
