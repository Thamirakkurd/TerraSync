package com.terrasync.app

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
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
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterDemSource
import org.maplibre.android.style.sources.TileSet
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
        private const val RULER_LINE_CASING_LAYER = "terrasync-ruler-line-casing-layer"
        private const val RULER_LINE_LAYER = "terrasync-ruler-line-layer"
        private const val RULER_POINTS_SOURCE = "terrasync-ruler-points-source"
        private const val RULER_POINTS_CIRCLE_LAYER = "terrasync-ruler-points-circle-layer"
        private const val RULER_POINTS_NUM_LAYER = "terrasync-ruler-points-num-layer"
        private const val PINS_SOURCE = "terrasync-pins-source"
        private const val PINS_CLUSTER_SOURCE = "terrasync-pins-cluster-source"
        private const val PINS_LAYER = "terrasync-pins-layer"
        private const val PINS_LABELS_LAYER = "terrasync-pins-labels-layer"
        private const val PINS_CLUSTER_UNCLUSTERED_LAYER = "terrasync-pins-cluster-unclustered"
        private const val PINS_CLUSTER_LABELS_LAYER = "terrasync-pins-cluster-labels"
        private const val PINS_CLUSTER_CIRCLE_LAYER = "terrasync-pins-cluster-circles"
        private const val PINS_CLUSTER_COUNT_LAYER = "terrasync-pins-cluster-count"
        private const val SELECTED_SOURCE = "terrasync-selected-source"
        private const val SELECTED_HALO_LAYER = "terrasync-selected-halo-layer"
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

        private val SCALE_STEPS_METRIC = doubleArrayOf(
            5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0,
            10000.0, 20000.0, 50000.0, 100000.0, 200000.0
        )
        private val SCALE_STEPS_IMPERIAL = doubleArrayOf(
            20.0, 50.0, 100.0, 200.0, 500.0, 1000.0, 2640.0, 5280.0, 10560.0,
            26400.0, 52800.0, 105600.0, 264000.0
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

    private data class SharedPinTarget(
        val lat: Double,
        val lng: Double,
        val name: String,
        val pinId: String,
        val zoom: Double? = null,
        val sourceHint: String = "Shared via TerraSync",
        val hasExplicitLabel: Boolean = false
    )
    private data class PendingRouteReq(
        val destLat: Double,
        val destLng: Double,
        val destTitle: String,
        val mode: TravelMode,
        val requestedAtMs: Long
    )

    private lateinit var mapView: MapView
    private lateinit var topCluster: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var routeFromBanner: LinearLayout
    private lateinit var tvRouteFromText: TextView
    private lateinit var tvRouteFromHint: TextView
    private lateinit var btnClearRouteFrom: ImageButton
    private lateinit var btnSyncStatus: ImageButton
    private lateinit var btnCompass: ImageButton
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
    private lateinit var leftBottomColumn: LinearLayout
    private lateinit var rightChromeColumn: LinearLayout
    private lateinit var rightArcCluster: FrameLayout
    private lateinit var zoomPillContainer: LinearLayout
    private lateinit var zoomPillDivider: View
    private lateinit var btnZoomIn: ImageButton
    private lateinit var btnZoomOut: ImageButton
    private lateinit var btnLocate: ImageButton
    private lateinit var btnTabSearch: ImageButton
    private lateinit var btnTabBookmarks: ImageButton
    private lateinit var btnTabSettings: ImageButton
    private lateinit var btnTabAbout: ImageButton
    private lateinit var bottomDock: LinearLayout
    private lateinit var mapControlsRow: LinearLayout
    private lateinit var bottomNavDock: SwipeDockFrameLayout
    private lateinit var bottomIconRow: LinearLayout
    private lateinit var fullScreenSwipeUpCatcher: View
    private var activeOpenNavTab: Int = -1
    private var isFullScreenMapMode: Boolean = false
    private var isCompassVisible: Boolean = false
    private var isSwappingSheetInPlace: Boolean = false
    private var speedSampleState = SpeedFallbackEstimator.SpeedSampleState()
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
    private lateinit var tvNavStepDist: TextView
    private lateinit var tvNavInstruction: TextView
    private lateinit var navBottomBar: LinearLayout
    private lateinit var tvNavEta: TextView
    private lateinit var tvNavMeta: TextView
    private lateinit var btnNavVoice: ImageButton
    private lateinit var btnNavStop: ImageButton
    private lateinit var tvScaleLabel: TextView
    private lateinit var scaleBarLine: ScaleBarView
    private lateinit var scaleBarContainer: LinearLayout

    private lateinit var inScreenSheet: LinearLayout
    private lateinit var sheetDragHandle: View
    private lateinit var sheetScrollView: androidx.core.widget.NestedScrollView
    private lateinit var sheetContent: LinearLayout
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var pageOverlayContainer: FrameLayout

    private var map: MapLibreMap? = null
    private val contourManager = ContourOverlayManager()
    private var isActivityStarted = false
    private var isActivityResumed = false
    private lateinit var offlineMaps: OfflineMapManager
    private lateinit var repo: SyncRepo

    private var layers: List<LayerItem> = emptyList()
    private var pins: List<PinItem> = emptyList()
    private var tracks: List<TrackItem> = emptyList()
    private var pinById: Map<String, PinItem> = emptyMap()
    private var layerById: Map<String, LayerItem> = emptyMap()
    private var pinsByLayerId: Map<String, List<PinItem>> = emptyMap()
    private var tracksByLayerId: Map<String, List<TrackItem>> = emptyMap()
    private var dataVersion: Long = 0L
    private val stackedCellSize = 0.00003
    private var stackedPinBuckets: Map<Long, List<PinItem>> = emptyMap()
    private fun stackedBucketKey(cx: Int, cy: Int): Long =
        (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)

    private data class SearchIndexEntry(
        val pin: PinItem,
        val searchBlob: String,
        val folderName: String,
        val statusTag: String,
        val dotColor: Int,
        val coordsText: String
    )
    @Volatile private var cachedSearchIndex: Pair<Long, List<SearchIndexEntry>>? = null
    private val searchIndexLock = Any()

    private fun getOrBuildSearchIndex(
        ver: Long,
        pinsSnap: List<PinItem>,
        folderNames: Map<String, String>
    ): List<SearchIndexEntry> {
        cachedSearchIndex?.let { if (it.first == ver) return it.second }
        return synchronized(searchIndexLock) {
            cachedSearchIndex?.let { if (it.first == ver) return it.second }
            val built = pinsSnap.map { pin ->
                val fName = folderNames[pin.layerId] ?: "Folder"
                val st = PinStatus.fromRaw(pin.status)
                val cleanNote = DailyReportGenerator.flattenSingleLine(pin.issueReason)
                val statusTag = if ((st == PinStatus.ISSUE || st == PinStatus.DONE) && cleanNote.isNotBlank()) {
                    "${st.label}: $cleanNote"
                } else {
                    st.label
                }
                val dotColor = Color.parseColor(st.colorHex)
                val blob = buildString(
                    pin.name.length + pin.description.length + pin.issueReason.length + st.label.length + fName.length + 8
                ) {
                    append(pin.name).append(' ')
                    if (pin.description.isNotBlank()) append(pin.description).append(' ')
                    if (pin.issueReason.isNotBlank()) append(pin.issueReason).append(' ')
                    append(st.label).append(' ').append(fName)
                }.lowercase(Locale.ROOT)
                val coordsText = "${"%.6f".format(Locale.US, pin.lat)}, ${"%.6f".format(Locale.US, pin.lng)}"
                SearchIndexEntry(
                    pin = pin,
                    searchBlob = blob,
                    folderName = fName,
                    statusTag = statusTag,
                    dotColor = dotColor,
                    coordsText = coordsText
                )
            }
            cachedSearchIndex = ver to built
            built
        }
    }

    private fun filterSearchEntries(
        index: List<SearchIndexEntry>,
        tokens: List<String>,
        hasDigits: Boolean,
        loc: android.location.Location?,
        unit: SpeedUnit
    ): List<Pair<SearchIndexEntry, String?>> {
        if (tokens.isEmpty()) return emptyList()
        val matched = index.filter { entry ->
            tokens.all { tok ->
                tok in entry.searchBlob || (hasDigits && tok in entry.coordsText)
            }
        }
        return matched.map { entry ->
            val dist = if (loc != null) {
                formatDistanceShort(
                    RoutingEngine.haversineMeters(loc.latitude, loc.longitude, entry.pin.lat, entry.pin.lng),
                    unit
                )
            } else {
                null
            }
            entry to dist
        }
    }

    private fun appendPaginatedSearchCard(
        container: LinearLayout,
        p: ThemePalette,
        matches: List<Pair<SearchIndexEntry, String?>>,
        showFolderInSub: Boolean,
        pageSize: Int = 60
    ) {
        val resultsCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            DesignSystem.applyGroupedCardSurface(this@MainActivity, this, p, radiusDp = 16f)
        }
        var rendered = 0

        fun appendSlice(toIdx: Int) {
            val end = minOf(toIdx, matches.size)
            for (idx in rendered until end) {
                val (entry, distText) = matches[idx]
                val pin = entry.pin
                if (idx > 0) {
                    resultsCard.addView(DesignSystem.createHairlineDivider(this, p, 16, 16))
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(52)
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                    UiAnimations.applyRowRipple(this@MainActivity, this, p.isDark)
                    setOnClickListener {
                        ensurePinVisibleOnMap(pin)
                        closeFullPage()
                        selectPinAndOpenCard(pin, zoomIfFar = true)
                    }
                }
                row.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(entry.dotColor)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(12) }
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
                    setTextColor(p.textPrimary)
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                })
                val st = PinStatus.fromRaw(pin.status)
                val subText = if (showFolderInSub && entry.folderName.isNotBlank()) {
                    "${entry.folderName} • ${entry.statusTag}"
                } else {
                    entry.statusTag
                }
                col.addView(TextView(this).apply {
                    text = subText
                    textSize = 12f
                    setTextColor(
                        when {
                            st == PinStatus.ISSUE -> p.red
                            st == PinStatus.DONE && pin.issueReason.isNotBlank() -> p.green
                            else -> p.textSecondary
                        }
                    )
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                })
                row.addView(col)
                if (distText != null) {
                    row.addView(TextView(this).apply {
                        text = distText
                        textSize = 12f
                        setTextColor(p.accentPrimary)
                        setTypeface(null, Typeface.BOLD)
                        setSingleLine(true)
                        DesignSystem.applyTabularNumerals(this)
                    })
                }
                resultsCard.addView(row)
            }
            rendered = end
        }

        appendSlice(pageSize)
        container.addView(resultsCard)
        UiAnimations.animateResultReveal(resultsCard)

        if (rendered < matches.size) {
            lateinit var btnMore: MaterialButton
            btnMore = MaterialButton(this).apply {
                text = "Show more (${matches.size - rendered} remaining)"
                textSize = 13f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setTextColor(p.accentPrimary)
                backgroundTintList = android.content.res.ColorStateList.valueOf(p.chipSurface)
                strokeWidth = dp(1)
                strokeColor = android.content.res.ColorStateList.valueOf(p.borderSubtle)
                cornerRadius = dp(12)
                elevation = 0f
                stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(42)
                ).apply { topMargin = dp(8) }
                UiAnimations.attachSnappyPress(this)
                setOnClickListener {
                    appendSlice(rendered + pageSize)
                    val left = matches.size - rendered
                    if (left <= 0) {
                        btnMore.visibility = View.GONE
                    } else {
                        btnMore.text = "Show more ($left remaining)"
                    }
                }
            }
            container.addView(btnMore)
        }
    }

    @Volatile private var pinRenderSeq: Long = 0L
    @Volatile private var trackRenderSeq: Long = 0L
    private var lastPinsRenderKey: String? = null
    private var lastTracksRenderKey: String? = null

    private enum class GpsPollProfile { OFF, ACTIVE_MAP, FOLLOW_LOCATING, NAVIGATION }
    private var activeGpsProfile = GpsPollProfile.OFF

    private val trackPointCache = HashMap<String, Pair<Long, List<Point>>>()
    private var cloudLayers: List<LayerItem> = emptyList()
    private var cloudPins: List<PinItem> = emptyList()
    private var cloudTracks: List<TrackItem> = emptyList()
    private var visibleStatuses: MutableSet<String> = mutableSetOf("pending", "done", "issue")
    private var isLiveConnected = false
    private var mapStyleMode: MapStyleMode = MapStyleMode.VECTOR
    private var travelMode: TravelMode = TravelMode.CAR
    private var cachedScaleBarEnabled: Boolean = true
    private var cachedSpeedUnit: SpeedUnit = SpeedUnit.METRIC
    private var lastScaleLabel: String = ""
    private var lastScaleWidthPx: Int = -1
    private var isFollowingGps = false
    private var hasAutoZoomedInitial = false
    private var statusBarInsetTop = 0
    private var navBarInsetBottom = 0
    private var imeInsetBottom = 0
    private var sideInsetLeft = 0
    private var sideInsetRight = 0
    private var activeSheetPeekDp = 280
    private var sheetQuickSelectPinId: String? = null
    private var pendingRouteReq: PendingRouteReq? = null
    private val uploadingLayerIds = mutableSetOf<String>()
    private var activePageCleanup: (() -> Unit)? = null
    private var webSearchSeq = 0
    private var pendingSharedPin: SharedPinTarget? = null
    private var pendingZoomPoints: List<LatLng>? = null
    private var activeSharedPinId: String? = null
    private var activeTappedLatLng: LatLng? = null
    private var tappedTitleView: TextView? = null
    private var tappedSubView: TextView? = null
    private var tappedDistView: TextView? = null
    private var tappedResolvedTitle: String = ""
    private var tappedResolvedSub: String = ""
    private var sheetOpenSeq: Int = 0
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

    // Selected pin or tapped location sky-blue dot marker
    private var selectedPinId: String? = null
    private var selectedMarkerLatLng: LatLng? = null
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
    private var liveEditHistoryRefresh: (() -> Unit)? = null

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
            val queued = pendingRouteReq
            val loc = currentLocation
            if (queued != null && loc != null && locationAgeMs(loc) <= 120_000L &&
                System.currentTimeMillis() - queued.requestedAtMs <= 30_000L && activeNavRoute == null
            ) {
                pendingRouteReq = null
                openRoutePreview(queued.destLat, queued.destLng, queued.destTitle, queued.mode)
            } else if (queued == null) {
                isFollowingGps = true
                updateLocationIconTint()
                loc?.let {
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16.5))
                }
            }
        } else {
            pendingRouteReq = null
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private fun ensureNotificationPermissionForDownloads() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun applyTransparentSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.clearFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            run { window.navigationBarDividerColor = Color.TRANSPARENT }
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTransparentSystemBars()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        val installedVer = getInstalledVersionName()
        val appUserAgent = "TerraSync/$installedVer (https://github.com/Thamirakkurd/TerraSync)"
        RoutingEngine.userAgent = appUserAgent

        MapLibre.getInstance(this)
        HttpRequestUtil.setOkHttpClient(RoutingEngine.sharedOkHttpClient)
        offlineMaps = OfflineMapManager.getInstance(this).apply { refreshAll() }
        repo = SyncRepo(applicationContext)
        RoutingEngine.appContext = applicationContext
        LocalFolderStore.preloadAsync(applicationContext)
        RoutingEngine.ioExecutor.execute { OfflineRoutingEngine.cleanupOrphanedTempFiles(applicationContext) }
        setContentView(R.layout.activity_main)

        visibleStatuses = DevicePrefs.getVisibleStatuses(this).toMutableSet()
        hideSentPins = DevicePrefs.isHideSentPins(this)
        mapStyleMode = DevicePrefs.getMapStyleMode(this)
        travelMode = DevicePrefs.getTravelMode(this)
        cachedScaleBarEnabled = DevicePrefs.isScaleBarEnabled(this)
        cachedSpeedUnit = DevicePrefs.getSpeedUnit(this)

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
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val navOnly = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            statusBarInsetTop = sys.top
            navBarInsetBottom = navOnly.bottom
            imeInsetBottom = ime.bottom
            sideInsetLeft = sys.left
            sideInsetRight = sys.right
            val isLayoutRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            val insetStart = if (isLayoutRtl) sideInsetRight else sideInsetLeft
            val insetEnd = if (isLayoutRtl) sideInsetLeft else sideInsetRight
            (topCluster.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = statusBarInsetTop + dp(12)
                marginStart = insetStart + dp(14)
                marginEnd = insetEnd + dp(14)
            }
            (navBanner.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = statusBarInsetTop + dp(12)
                marginStart = insetStart + dp(14)
                marginEnd = insetEnd + dp(14)
            }
            (bottomDock.layoutParams as ViewGroup.MarginLayoutParams).apply {
                bottomMargin = maxOf(navBarInsetBottom, dp(12)) + dp(10)
                marginStart = insetStart
                marginEnd = insetEnd
            }
            (fullScreenSwipeUpCatcher.layoutParams as ViewGroup.MarginLayoutParams).apply {
                height = maxOf(navBarInsetBottom, dp(14)) + dp(28)
            }
            (inScreenSheet.layoutParams as ViewGroup.MarginLayoutParams).apply {
                topMargin = 0
                marginStart = insetStart
                marginEnd = insetEnd
            }
            val maxSheetH = (resources.displayMetrics.heightPixels - statusBarInsetTop - dp(12)).coerceAtLeast(dp(240))
            if (::sheetBehavior.isInitialized) {
                sheetBehavior.maxHeight = maxSheetH
            }
            val sheetBottomInset = maxOf(navBarInsetBottom, imeInsetBottom)
            sheetContent.setPadding(dp(16), 0, dp(16), sheetBottomInset + dp(20))
            val pageBottom = maxOf(navBarInsetBottom, imeInsetBottom)
            for (i in 0 until pageOverlayContainer.childCount) {
                pageOverlayContainer.getChildAt(i).setPadding(sideInsetLeft, statusBarInsetTop, sideInsetRight, pageBottom)
            }
            topCluster.requestLayout()
            navBanner.requestLayout()
            bottomDock.requestLayout()
            fullScreenSwipeUpCatcher.requestLayout()
            inScreenSheet.requestLayout()
            if (imeInsetBottom > 0 && ::sheetBehavior.isInitialized && sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                if (sheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                    sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
                if (::sheetScrollView.isInitialized) {
                    sheetScrollView.post { sheetScrollView.fullScroll(View.FOCUS_DOWN) }
                    sheetScrollView.postDelayed({
                        if (!isFinishing && !isDestroyed) sheetScrollView.fullScroll(View.FOCUS_DOWN)
                    }, 120L)
                }
            }
            insets
        }

        sheetBehavior = BottomSheetBehavior.from(inScreenSheet).apply {
            state = BottomSheetBehavior.STATE_HIDDEN
            isHideable = true
            isFitToContents = true
            skipCollapsed = false
            maxHeight = (resources.displayMetrics.heightPixels - statusBarInsetTop - dp(12)).coerceAtLeast(dp(240))
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        sheetQuickSelectPinId = null
                        pendingRouteReq = null
                        clearSelectedMarker()
                        if (previewRoute != null && activeNavRoute == null) {
                            routeRequestSeq++
                            previewRoute = null
                            renderRouteOverlay()
                        }
                        if (activeNavRoute == null) {
                            resetMapPadding()
                        }
                        if (!isFullScreenMapMode) {
                            bottomDock.translationY = 0f
                            UiAnimations.fadeShow(bottomDock, 165L)
                        }
                    } else if ((newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_EXPANDED) &&
                        !isRulerMode && !isLassoMode && activeNavRoute == null
                    ) {
                        bottomDock.animate().cancel()
                        bottomDock.alpha = 0f
                        bottomDock.translationY = 0f
                        bottomDock.visibility = View.GONE
                    }
                }
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    if (UiAnimations.isReducedMotion(this@MainActivity)) return
                    if (isSwappingSheetInPlace || isFullScreenMapMode || isRulerMode || isLassoMode || activeNavRoute != null) return
                    bottomDock.animate().cancel()
                    if (slideOffset < -0.15f) {
                        val reveal = ((-slideOffset - 0.15f) / 0.85f).coerceIn(0f, 1f)
                        if (bottomDock.visibility != View.VISIBLE) {
                            bottomDock.visibility = View.VISIBLE
                        }
                        bottomDock.alpha = reveal
                        bottomDock.translationY = (1f - reveal) * dp(14)
                    } else if (bottomDock.visibility == View.VISIBLE) {
                        bottomDock.alpha = 0f
                        bottomDock.visibility = View.INVISIBLE
                    }
                }
            })
        }

        setupClickListeners()
        setupBackNavigation()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(36.8669, 42.9900))
                .zoom(11.0)
                .build()

            loadMapStyle()
            updateScaleBar()
            updateCompassForBearing(m.cameraPosition.bearing, animate = false)

            m.addOnCameraMoveListener {
                updateScaleBar()
                updateCompassForBearing(m.cameraPosition.bearing, animate = true)
                if (isLassoMode && lassoPendingPinIds.isNotEmpty()) {
                    clearLassoSelection()
                }
            }

            m.addOnCameraIdleListener {
                contourManager.scheduleUpdate(
                    m,
                    DevicePrefs.isContoursEnabled(this),
                    mapStyleMode == MapStyleMode.VECTOR
                )
            }

            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && isFollowingGps) {
                    isFollowingGps = false
                    updateLocationIconTint()
                    syncGpsPollingProfile()
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
        if (!DevicePrefs.isOperatorSetupDone(this)) {
            window.decorView.post {
                if (!isFinishing && !isDestroyed && !DevicePrefs.isOperatorSetupDone(this)) {
                    showFirstInstallOperatorDialog()
                }
            }
        }
    }

    private fun themedDialogBuilder(title: String): AlertDialog.Builder {
        val p = currentPalette()
        return AlertDialog.Builder(this)
            .setCustomTitle(DesignSystem.createThemedDialogTitle(this, title, p))
    }

    private fun showThemedDialog(dlg: AlertDialog, destructivePositive: Boolean = false): AlertDialog {
        val p = currentPalette()
        dlg.show()
        DesignSystem.styleAlertDialog(this, dlg, p, destructivePositive = destructivePositive)
        return dlg
    }

    private fun showFirstInstallOperatorDialog() {
        val p = currentPalette()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        box.addView(TextView(this).apply {
            text = "تیما خو بنڤیسە"
            textSize = 15f
            textDirection = View.TEXT_DIRECTION_RTL
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.accentPrimary)
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                setColor(p.accentPrimarySoft)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START
                bottomMargin = dp(10)
            }
        })
        box.addView(TextView(this).apply {
            text = "Enter your operator or team name (e.g. Team 212, Ali, Supervisor) so every pin edit and cloud submission is stamped accurately."
            textSize = 13.5f
            setLineSpacing(dp(2).toFloat(), 1.08f)
            setTextColor(p.textSecondary)
            setPadding(0, 0, 0, dp(14))
        })

        val etName = DesignSystem.createThemedEditText(this, p, "Operator or Team Name").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        box.addView(etName)

        val baseDeviceStamp = DevicePrefs.getDeviceStamp(this)
        val devIdShort = DevicePrefs.getDeviceId(this).take(6)
        val tvPreview = TextView(this).apply {
            text = "Preview: $baseDeviceStamp"
            textSize = 11.5f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
            setTextColor(p.accentPrimary)
            setTypeface(null, Typeface.BOLD)
            DesignSystem.applyTabularNumerals(this)
            setPadding(dp(4), dp(10), dp(4), dp(4))
        }
        box.addView(tvPreview)

        etName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val typed = s?.toString()?.trim().orEmpty()
                tvPreview.text = if (typed.isNotBlank()) {
                    "Preview: $typed • #$devIdShort"
                } else {
                    "Preview: $baseDeviceStamp"
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        val dlgScroll = ScrollView(this).apply {
            isFillViewport = true
            addView(box)
        }
        val dlg = themedDialogBuilder("Field Operator Stamp")
            .setView(dlgScroll)
            .setCancelable(false)
            .setPositiveButton("Save & Continue", null)
            .setNegativeButton("Skip for now") { _, _ ->
                DevicePrefs.setOperatorSetupDone(this, true)
                Toast.makeText(this, "You can set your stamp anytime in Settings → Field Data & Sync", Toast.LENGTH_SHORT).show()
            }
            .create()

        fun trySave() {
            val entered = etName.text.toString().trim()
            if (entered.isBlank()) {
                Toast.makeText(this, "Please enter your operator or team name (or tap Skip)", Toast.LENGTH_SHORT).show()
                return
            }
            DevicePrefs.setCustomName(this, entered)
            DevicePrefs.setOperatorSetupDone(this, true)
            Toast.makeText(this, "Field Stamp set: ${DevicePrefs.getDeviceStamp(this)}", Toast.LENGTH_SHORT).show()
            dlg.dismiss()
        }

        etName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                trySave()
                true
            } else false
        }

        dlg.setOnShowListener {
            DesignSystem.styleAlertDialog(this, dlg, p)
            dlg.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                background = GradientDrawable().apply {
                    setColor(p.accentPrimary)
                    cornerRadius = dp(20).toFloat()
                }
                setTextColor(Color.WHITE)
                setPadding(dp(18), dp(8), dp(18), dp(8))
                minHeight = dp(40)
                setOnClickListener { trySave() }
            }
            etName.requestFocus()
        }
        dlg.show()
    }

    private fun bindViews() {
        mapView = findViewById(R.id.mapView)
        topCluster = findViewById(R.id.topCluster)
        topBar = findViewById(R.id.topBar)
        routeFromBanner = findViewById(R.id.routeFromBanner)
        tvRouteFromText = findViewById(R.id.tvRouteFromText)
        tvRouteFromHint = findViewById(R.id.tvRouteFromHint)
        btnClearRouteFrom = findViewById(R.id.btnClearRouteFrom)
        btnSyncStatus = findViewById(R.id.btnSyncStatus)
        btnCompass = findViewById(R.id.btnCompass)
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
        leftBottomColumn = findViewById(R.id.leftBottomColumn)
        rightChromeColumn = findViewById(R.id.rightChromeColumn)
        rightArcCluster = findViewById(R.id.rightArcCluster)
        zoomPillContainer = findViewById(R.id.zoomPillContainer)
        zoomPillDivider = findViewById(R.id.zoomPillDivider)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        btnLocate = findViewById(R.id.btnLocate)
        applyExtraMapToolsVisibility()
        applyZoomButtonsVisibility()
        applyResponsiveChromeSizing()
        btnTabSearch = findViewById(R.id.btnTabSearch)
        btnTabBookmarks = findViewById(R.id.btnTabBookmarks)
        btnTabSettings = findViewById(R.id.btnTabSettings)
        btnTabAbout = findViewById(R.id.btnTabAbout)
        bottomDock = findViewById(R.id.bottomDock)
        mapControlsRow = findViewById(R.id.mapControlsRow)
        bottomNavDock = findViewById(R.id.bottomNavDock)
        bottomIconRow = findViewById(R.id.bottomIconRow)
        fullScreenSwipeUpCatcher = findViewById(R.id.fullScreenSwipeUpCatcher)
        bottomNavDock.onSwipeDown = {
            if (activeNavRoute == null &&
                !isRulerMode &&
                !isLassoMode &&
                pageOverlayContainer.visibility != View.VISIBLE &&
                sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN
            ) {
                setFullScreenMapMode(true)
            }
        }
        setupFullScreenSwipeUpCatcher()
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
        tvNavStepDist = findViewById(R.id.tvNavStepDist)
        tvNavInstruction = findViewById(R.id.tvNavInstruction)
        navBottomBar = findViewById(R.id.navBottomBar)
        tvNavEta = findViewById(R.id.tvNavEta)
        tvNavMeta = findViewById(R.id.tvNavMeta)
        btnNavVoice = findViewById(R.id.btnNavVoice)
        btnNavStop = findViewById(R.id.btnNavStop)
        tvScaleLabel = findViewById(R.id.tvScaleLabel)
        scaleBarLine = findViewById(R.id.scaleBarLine)
        scaleBarContainer = findViewById(R.id.scaleBarContainer)
        DesignSystem.applyTabularNumerals(tvSpeedValue)
        DesignSystem.applyTabularNumerals(tvScaleLabel)
        DesignSystem.applyTabularNumerals(tvRulerDistance)
        DesignSystem.applyTabularNumerals(tvNavStepDist)
        DesignSystem.applyTabularNumerals(tvNavEta)
        DesignSystem.applyTabularNumerals(tvNavMeta)
        inScreenSheet = findViewById(R.id.inScreenSheet)
        sheetDragHandle = findViewById(R.id.sheetDragHandle)
        sheetScrollView = findViewById(R.id.sheetScrollView)
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

    private fun currentPalette(): ThemePalette = DesignSystem.palette(isDarkMapUi())

    private fun updateSystemBarContrast(forFullPage: Boolean = ::pageOverlayContainer.isInitialized && pageOverlayContainer.visibility == View.VISIBLE) {
        val night = isDarkMapUi()
        val useLightIcons = if (forFullPage) night else (night || mapStyleMode == MapStyleMode.SATELLITE)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !useLightIcons
            isAppearanceLightNavigationBars = !useLightIcons
        }
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun setupFullScreenSwipeUpCatcher() {
        var downY = 0f
        var restored = false
        val triggerPx = dp(22).toFloat()
        fullScreenSwipeUpCatcher.setOnTouchListener { _, ev ->
            if (!isFullScreenMapMode) return@setOnTouchListener false
            when (ev.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    downY = ev.rawY
                    restored = false
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    if (!restored && (downY - ev.rawY) >= triggerPx) {
                        restored = true
                        setFullScreenMapMode(false)
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    if (!restored && (downY - ev.rawY) >= triggerPx * 0.5f) {
                        restored = true
                        setFullScreenMapMode(false)
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Auto-hides the top-right Compass when facing North (~0°) and smoothly fades/scales it in on rotation. */
    private fun updateCompassForBearing(bearing: Double, animate: Boolean = true) {
        if (!::btnCompass.isInitialized) return
        val norm = ((bearing % 360.0) + 540.0) % 360.0 - 180.0
        val shouldShow = kotlin.math.abs(norm) > 1.2 && !isFullScreenMapMode
        btnCompass.rotation = (-bearing).toFloat()
        if (shouldShow == isCompassVisible) {
            if (!shouldShow && btnCompass.visibility != View.INVISIBLE) {
                btnCompass.visibility = View.INVISIBLE
                btnCompass.alpha = 0f
                btnCompass.isEnabled = false
            }
            return
        }
        isCompassVisible = shouldShow
        btnCompass.animate().cancel()
        if (shouldShow) {
            btnCompass.isEnabled = true
            btnCompass.visibility = View.VISIBLE
            if (!animate || UiAnimations.isReducedMotion(this)) {
                btnCompass.alpha = 1f
                btnCompass.scaleX = 1f
                btnCompass.scaleY = 1f
                return
            }
            btnCompass.alpha = 0f
            btnCompass.scaleX = 0.84f
            btnCompass.scaleY = 0.84f
            btnCompass.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(165L)
                .setInterpolator(UiAnimations.EMPHASIZED_DECELERATE)
                .withLayer()
                .start()
        } else {
            btnCompass.isEnabled = false
            if (!animate || UiAnimations.isReducedMotion(this)) {
                btnCompass.alpha = 0f
                btnCompass.scaleX = 0.84f
                btnCompass.scaleY = 0.84f
                btnCompass.visibility = View.INVISIBLE
                return
            }
            btnCompass.animate()
                .alpha(0f)
                .scaleX(0.84f)
                .scaleY(0.84f)
                .setDuration(150L)
                .setInterpolator(UiAnimations.FAST_EXIT)
                .withLayer()
                .withEndAction {
                    if (!isCompassVisible) btnCompass.visibility = View.INVISIBLE
                }
                .start()
        }
    }

    /**
     * 120fps GPU-only Full-Screen Clean Map Mode:
     * Top controls glide UP, right-side arc + FAB glide RIGHT, bottom dock + scale bar glide DOWN.
     * Transparent Android status bar stays visible so time/signal/battery remain readable outdoors.
     */
    private fun setFullScreenMapMode(enabled: Boolean, animate: Boolean = true) {
        if (isFullScreenMapMode == enabled) return
        isFullScreenMapMode = enabled
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val rightShift = (if (isRtl) -dp(170) else dp(170)).toFloat()
        val topShift = -dp(96).toFloat()
        val bottomShift = dp(110).toFloat()
        val dur = if (animate) 220L else 0L

        if (enabled) {
            fullScreenSwipeUpCatcher.visibility = View.VISIBLE
            UiAnimations.animateChromeSlide(topCluster, hide = true, shiftY = topShift, durationMs = dur)
            UiAnimations.animateChromeSlide(rightChromeColumn, hide = true, shiftX = rightShift, durationMs = dur)
            UiAnimations.animateChromeSlide(leftBottomColumn, hide = true, shiftY = bottomShift, durationMs = dur)
            UiAnimations.animateChromeSlide(bottomNavDock, hide = true, shiftY = bottomShift, durationMs = dur)
        } else {
            fullScreenSwipeUpCatcher.visibility = View.GONE
            if (bottomDock.visibility != View.VISIBLE && sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomDock.alpha = 1f
                bottomDock.translationY = 0f
                bottomDock.visibility = View.VISIBLE
            }
            if (activeNavRoute == null) {
                UiAnimations.animateChromeSlide(topCluster, hide = false, shiftY = topShift, durationMs = dur)
                map?.cameraPosition?.bearing?.let { updateCompassForBearing(it, animate = false) }
            }
            UiAnimations.animateChromeSlide(rightChromeColumn, hide = false, shiftX = rightShift, durationMs = dur)
            UiAnimations.animateChromeSlide(leftBottomColumn, hide = false, shiftY = bottomShift, durationMs = dur)
            if (activeNavRoute == null && !isRulerMode && !isLassoMode) {
                UiAnimations.animateChromeSlide(bottomNavDock, hide = false, shiftY = bottomShift, durationMs = dur)
            }
        }
    }

    /**
     * Styles the 4 individual equal-width inner pills inside the frosted bottom navigation tray.
     * On the map (activeOpenNavTab == -1), ALL 4 pills remain neutral white with dark icons.
     * Only a pressed pill or currently open section turns solid forest emerald with a white icon.
     */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun syncNavDockPills(pressedIdx: Int = -1) {
        if (!::bottomNavDock.isInitialized) return
        val p = currentPalette()
        val tabs = listOf(btnTabSearch, btnTabBookmarks, btnTabSettings, btnTabAbout)
        tabs.forEachIndexed { idx, btn ->
            val active = (idx == activeOpenNavTab) || (idx == pressedIdx)
            val fill = if (active) p.accentPrimary else p.navPillNeutral
            val iconColor = if (active) Color.WHITE else p.inactiveNavIcon
            val content = DesignSystem.buildOpaqueDrawable(
                ctx = this,
                fillColor = fill,
                radiusDp = 25f
            )
            val mask = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(25).toFloat()
            }
            val rippleCol = if (active) Color.parseColor("#2EFFFFFF") else p.rippleColor
            btn.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(rippleCol),
                content,
                mask
            )
            btn.setColorFilter(iconColor)
            btn.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        if (!UiAnimations.isReducedMotion(v.context)) {
                            v.animate().cancel()
                            v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(60L)
                                .setInterpolator(UiAnimations.FAST_OUT_SLOW_IN).withLayer().start()
                        }
                        if (activeOpenNavTab != idx) {
                            (v as? ImageButton)?.let { ib ->
                                ib.background = DesignSystem.buildOpaqueDrawable(this, p.accentPrimary, 25f)
                                ib.setColorFilter(Color.WHITE)
                            }
                        }
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL,
                    android.view.MotionEvent.ACTION_OUTSIDE -> {
                        if (!UiAnimations.isReducedMotion(v.context)) {
                            v.animate().cancel()
                            v.animate().scaleX(1f).scaleY(1f).setDuration(110L)
                                .setInterpolator(UiAnimations.FAST_OUT_SLOW_IN).withLayer().start()
                        }
                        if (event.actionMasked != android.view.MotionEvent.ACTION_UP || activeOpenNavTab != idx) {
                            v.post { if (activeOpenNavTab != idx) syncNavDockPills() }
                        }
                    }
                }
                false
            }
        }
    }

    private fun applyMapChromeTheme() {
        applyTransparentSystemBars()
        val p = currentPalette()
        val night = p.isDark
        val darkMapBg = night || mapStyleMode == MapStyleMode.SATELLITE
        updateSystemBarContrast()
        val iconTint = p.iconTint
        val scaleColor = Color.parseColor(if (darkMapBg) "#F4F4F0" else "#1C1E1B")
        val scaleHaloColor = Color.parseColor(if (darkMapBg) "#CC161715" else "#E6F9F9F7")

        // Opaque milky-matte 52dp circular utility controls (#F3F5F1 Light / #222421 Dark + directional downward shadows)
        DesignSystem.applyFloatingControl(this, btnSyncStatus, p, 26f, 6f)
        DesignSystem.applyFloatingControl(this, btnCompass, p, 26f, 6f)
        DesignSystem.applyFloatingControl(this, btnStyleToggle, p, 26f, 6f)
        DesignSystem.applyFloatingControl(this, btnStatusFilter, p, 26f, 6f)
        DesignSystem.applyFloatingControl(
            this,
            btnLasso,
            p,
            26f,
            6f,
            activeFillColor = if (isLassoMode) p.accentPrimary else null
        )
        DesignSystem.applyFloatingControl(
            this,
            btnRuler,
            p,
            26f,
            6f,
            activeFillColor = if (isRulerMode) p.accentPrimary else null
        )

        // Custom milky-matte compass dial with apricot-orange North tick (#F0651F)
        btnCompass.setImageResource(if (night) R.drawable.ic_compass_dial_dark else R.drawable.ic_compass_dial)
        btnCompass.clearColorFilter()

        // Option B: Slim Milky-Matte Vertical Zoom Pill (46dp wide, 23dp radius)
        DesignSystem.applyFloatingSurface(
            this,
            zoomPillContainer,
            p,
            radiusDp = 23f,
            elevationDp = 6f,
            fillOverride = p.surfaceFrosted,
            strokeOverride = p.floatingBorder
        )
        zoomPillDivider.setBackgroundColor(p.borderSubtle)
        UiAnimations.applyRoundedRipple(this, btnZoomIn, Color.TRANSPARENT, 20f, night)
        UiAnimations.applyRoundedRipple(this, btnZoomOut, Color.TRANSPARENT, 20f, night)
        UiAnimations.attachSnappyPress(btnZoomIn, 0.90f)
        UiAnimations.attachSnappyPress(btnZoomOut, 0.90f)

        // Two-Layer Floating Bottom Navigation Bar: Opaque Milky-Matte Outer Tray + 4 Crisp White Inner Pills
        DesignSystem.applyFloatingSurface(
            this,
            bottomNavDock,
            p,
            radiusDp = 31f,
            elevationDp = 9f,
            fillOverride = p.surfaceFrostedTray,
            strokeOverride = p.floatingBorder
        )
        bottomIconRow.background = null
        bottomIconRow.elevation = 0f
        syncNavDockPills()

        btnStyleToggle.setColorFilter(
            when (mapStyleMode) {
                MapStyleMode.SATELLITE -> p.accentWarm
                MapStyleMode.VECTOR -> iconTint
            }
        )
        btnLasso.setColorFilter(if (isLassoMode) Color.WHITE else iconTint)
        btnRuler.setColorFilter(if (isRulerMode) Color.WHITE else iconTint)
        updateSentToggleUi()
        btnZoomIn.setColorFilter(iconTint)
        btnZoomOut.setColorFilter(iconTint)
        updateFilterIconTint()
        updateLocationIconTint()
        btnSyncStatus.setColorFilter(if (isLiveConnected) p.accentPrimary else p.amber)

        // Crisp minimalist bracketed scale bar (locked on 14dp left rail, zero relayout on zoom/pan)
        scaleBarContainer.background = null
        scaleBarContainer.elevation = 0f
        scaleBarContainer.setPadding(0, dp(2), dp(2), dp(2))
        tvScaleLabel.setTextColor(scaleColor)
        tvScaleLabel.setShadowLayer(4f, 0f, 0f, scaleHaloColor)
        scaleBarLine.setBarStyle(scaleColor, scaleHaloColor)
        scaleBarContainer.visibility = if (DevicePrefs.isScaleBarEnabled(this)) View.VISIBLE else View.GONE

        // Crisp circular Speed HUD with tabular numerals
        DesignSystem.applyFloatingSurface(this, speedBadge, p, radiusDp = 30f, elevationDp = 5f)
        tvSpeedValue.setTextColor(p.textPrimary)
        tvSpeedUnit.setTextColor(p.textMuted)

        DesignSystem.applyFloatingSurface(this, routeFromBanner, p, radiusDp = 20f, elevationDp = 5f, strokeOverride = p.accentPrimary)
        tvRouteFromText.setTextColor(p.textPrimary)
        tvRouteFromHint.setTextColor(p.textSecondary)
        btnClearRouteFrom.setColorFilter(p.textMuted)

        DesignSystem.applyFloatingSurface(this, rulerCard, p, radiusDp = 24f, elevationDp = 8f)
        tvRulerDistance.setTextColor(p.textPrimary)
        tvRulerSubtitle.setTextColor(p.textMuted)
        btnRulerReset.setTextColor(p.accentWarm)
        UiAnimations.applyRoundedRipple(this, btnRulerReset, p.accentWarmContainer, 18f, night)
        UiAnimations.attachSnappyPress(btnRulerReset)
        btnRulerUndo.setColorFilter(iconTint)
        UiAnimations.attachSnappyPress(btnRulerUndo)
        btnRulerClose.setColorFilter(iconTint)
        UiAnimations.attachSnappyPress(btnRulerClose)

        DesignSystem.applyFloatingSurface(this, lassoCard, p, radiusDp = 24f, elevationDp = 8f)
        tvLassoTitle.setTextColor(p.textPrimary)
        tvLassoSubtitle.setTextColor(p.textMuted)
        btnLassoClose.setColorFilter(iconTint)
        UiAnimations.attachSnappyPress(btnLassoClose)
        UiAnimations.attachSnappyPress(btnLassoModeToggle)
        UiAnimations.applyRoundedRipple(this, btnLassoMarkSent, p.accentPrimary, 12f, isDark = true)
        UiAnimations.attachSnappyPress(btnLassoMarkSent)
        btnLassoRedraw.setTextColor(p.textPrimary)
        UiAnimations.applyRoundedRipple(this, btnLassoRedraw, p.surfaceTonal, 12f, night)
        UiAnimations.attachSnappyPress(btnLassoRedraw)
        btnLassoUndo.setTextColor(p.textPrimary)
        UiAnimations.applyRoundedRipple(this, btnLassoUndo, p.surfaceTonal, 12f, night)
        UiAnimations.attachSnappyPress(btnLassoUndo)
        btnLassoReset.setTextColor(p.red)
        UiAnimations.applyRoundedRipple(this, btnLassoReset, p.redSoft, 12f, night)
        UiAnimations.attachSnappyPress(btnLassoReset)

        // Bottom Sheet: 28dp top corners, 0dp bottom, warm canvas (#FAF9F6 / #161715)
        DesignSystem.applyFloatingSurface(
            this,
            inScreenSheet,
            p,
            radiusDp = 28f,
            elevationDp = 12f,
            topOnly = true,
            fillOverride = p.surfaceCanvas,
            strokeOverride = if (night) p.borderSubtle else Color.TRANSPARENT
        )
        sheetDragHandle.background = GradientDrawable().apply {
            setColor(p.handleColor)
            cornerRadius = dp(4).toFloat()
        }
        DesignSystem.applyNavBannerSurface(this, navBanner, p)
        tvNavArrow.background = GradientDrawable().apply {
            setColor(Color.parseColor("#29FFFFFF"))
            cornerRadius = dp(12).toFloat()
        }
        tvNavStepDist.setTextColor(Color.WHITE)
        tvNavInstruction.setTextColor(Color.parseColor("#EBF9F0"))
        DesignSystem.applyFloatingSurface(this, navBottomBar, p, radiusDp = 24f, elevationDp = 8f)
        tvNavEta.setTextColor(p.accentPrimary)
        tvNavMeta.setTextColor(p.textSecondary)
        UiAnimations.applyRoundedRipple(this, btnNavVoice, p.surfaceTonal, 22f, night)
        btnNavVoice.setColorFilter(iconTint)
        UiAnimations.attachSnappyPress(btnNavVoice)
        UiAnimations.applyRoundedRipple(this, btnNavStop, p.redSoft, 22f, night)
        btnNavStop.setColorFilter(p.red)
        UiAnimations.attachSnappyPress(btnNavStop)
        UiAnimations.attachSnappyPress(btnClearRouteFrom)
        if (isLassoMode) updateLassoUi()
    }

    private fun setupClickListeners() {
        btnSyncStatus.setOnClickListener {
            UiAnimations.animatePulseBump(btnSyncStatus)
            val state = if (isLiveConnected) "Cloud Synced" else "Offline Cache"
            val localCnt = layers.count { LocalFolderStore.isLocalLayerId(it.id) }
            val cloudCnt = layers.size - localCnt
            Toast.makeText(this, "$state • $cloudCnt cloud, $localCnt local • ${pins.size} bookmarks", Toast.LENGTH_SHORT).show()
        }

        btnCompass.setOnClickListener {
            UiAnimations.animatePulseBump(btnCompass, 1.08f)
            map?.let { m ->
                val reset = CameraPosition.Builder(m.cameraPosition)
                    .bearing(0.0)
                    .tilt(0.0)
                    .build()
                m.animateCamera(CameraUpdateFactory.newCameraPosition(reset), 280)
            }
        }

        btnStyleToggle.setOnClickListener {
            val p = currentPalette()
            val d = resources.displayMetrics.density
            DesignSystem.showActionBottomSheet(
                ctx = this,
                palette = p,
                title = "Map Layers",
                subtitle = "Style and visual map overlays",
                dimAmount = 0.18f,
                actions = emptyList(),
                headerContent = { listCol, dismiss ->
                    val swatchRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = dp(4)
                        }
                    }

                    MapStyleMode.entries.forEachIndexed { idx, mode ->
                        val active = mode == mapStyleMode
                        val isSat = mode == MapStyleMode.SATELLITE
                        val card = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(6), dp(6), dp(6), dp(8))
                            isClickable = true
                            isFocusable = true
                            background = DesignSystem.buildOpaqueDrawable(
                                ctx = this@MainActivity,
                                fillColor = if (active) p.accentPrimarySoft else p.surfaceTonal,
                                radiusDp = 18f,
                                strokeColor = if (active) p.accentPrimary else p.borderSubtle,
                                strokeDp = if (active) 2f else 1f
                            )
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                if (idx == 0) marginEnd = dp(10)
                            }
                            UiAnimations.attachSnappyPress(this, 0.97f)
                            setOnClickListener {
                                dismiss()
                                if (mapStyleMode != mode) {
                                    mapStyleMode = mode
                                    DevicePrefs.setMapStyleMode(this@MainActivity, mode)
                                    applyMapChromeTheme()
                                    loadMapStyle()
                                    Toast.makeText(this@MainActivity, mode.label, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }

                        val previewFrame = FrameLayout(this).apply {
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(78))
                            background = GradientDrawable().apply {
                                cornerRadius = dp(12).toFloat()
                            }
                            clipToOutline = true
                        }
                        val swatchGraphic = object : View(this) {
                            private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                            private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                style = Paint.Style.STROKE
                                strokeCap = Paint.Cap.ROUND
                                strokeJoin = Paint.Join.ROUND
                            }
                            private val riverPath = Path()
                            private val patchPath = Path()

                            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                                super.onSizeChanged(w, h, oldw, oldh)
                                val wf = w.toFloat()
                                val hf = h.toFloat()
                                riverPath.reset()
                                riverPath.moveTo(-4f * d, hf * 0.72f)
                                riverPath.cubicTo(wf * 0.30f, hf * 0.50f, wf * 0.62f, hf * 0.88f, wf + 4f * d, hf * 0.38f)

                                patchPath.reset()
                                patchPath.moveTo(wf * 0.58f, 0f)
                                patchPath.lineTo(wf, 0f)
                                patchPath.lineTo(wf, hf * 0.56f)
                                patchPath.cubicTo(wf * 0.76f, hf * 0.44f, wf * 0.64f, hf * 0.24f, wf * 0.58f, 0f)
                                patchPath.close()
                            }

                            override fun onDraw(canvas: Canvas) {
                                val wf = width.toFloat()
                                val hf = height.toFloat()
                                if (wf <= 0f || hf <= 0f) return
                                val nightPreview = p.isDark

                                if (isSat) {
                                    canvas.drawColor(Color.parseColor("#243227"))
                                    paint.style = Paint.Style.FILL
                                    paint.color = Color.parseColor("#2E4130")
                                    canvas.drawPath(patchPath, paint)
                                    paint.color = Color.parseColor("#3B4732")
                                    canvas.drawCircle(wf * 0.22f, hf * 0.28f, wf * 0.28f, paint)
                                    paint.color = Color.parseColor("#1D332B")
                                    canvas.drawCircle(wf * 0.82f, hf * 0.84f, wf * 0.26f, paint)

                                    roadPaint.color = Color.parseColor("#1B3B45")
                                    roadPaint.strokeWidth = 8.5f * d
                                    canvas.drawPath(riverPath, roadPaint)

                                    roadPaint.color = Color.parseColor("#73FFFFFF")
                                    roadPaint.strokeWidth = 1.8f * d
                                    canvas.drawLine(0f, hf * 0.32f, wf, hf * 0.24f, roadPaint)
                                    canvas.drawLine(wf * 0.36f, 0f, wf * 0.50f, hf, roadPaint)

                                    roadPaint.color = Color.parseColor("#CCEB9E38")
                                    roadPaint.strokeWidth = 2.6f * d
                                    canvas.drawLine(0f, hf * 0.54f, wf, hf * 0.48f, roadPaint)
                                } else {
                                    canvas.drawColor(Color.parseColor(if (nightPreview) "#1F2622" else "#EDEFE9"))
                                    paint.style = Paint.Style.FILL
                                    paint.color = Color.parseColor(if (nightPreview) "#24382C" else "#D5E8D0")
                                    canvas.drawPath(patchPath, paint)
                                    paint.color = Color.parseColor(if (nightPreview) "#242A26" else "#E2E4DD")
                                    canvas.drawRect(wf * 0.08f, hf * 0.14f, wf * 0.30f, hf * 0.38f, paint)
                                    canvas.drawRect(wf * 0.42f, hf * 0.58f, wf * 0.64f, hf * 0.82f, paint)

                                    roadPaint.color = Color.parseColor(if (nightPreview) "#20404D" else "#B8DDF0")
                                    roadPaint.strokeWidth = 7.5f * d
                                    canvas.drawPath(riverPath, roadPaint)

                                    roadPaint.color = Color.parseColor(if (nightPreview) "#3B423D" else "#FFFFFF")
                                    roadPaint.strokeWidth = 2.8f * d
                                    canvas.drawLine(0f, hf * 0.30f, wf, hf * 0.22f, roadPaint)
                                    canvas.drawLine(wf * 0.36f, 0f, wf * 0.48f, hf, roadPaint)
                                    canvas.drawLine(wf * 0.72f, 0f, wf * 0.64f, hf, roadPaint)

                                    roadPaint.color = Color.parseColor(if (nightPreview) "#A86C2B" else "#F2C478")
                                    roadPaint.strokeWidth = 3.6f * d
                                    canvas.drawLine(0f, hf * 0.54f, wf, hf * 0.48f, roadPaint)
                                }

                                // Tiny Organic pin accents
                                fun drawPinDot(cx: Float, cy: Float, fillHex: String) {
                                    paint.color = Color.WHITE
                                    canvas.drawCircle(cx, cy, 4.3f * d, paint)
                                    paint.color = Color.parseColor(fillHex)
                                    canvas.drawCircle(cx, cy, 3.0f * d, paint)
                                }
                                drawPinDot(wf * 0.31f, hf * 0.45f, "#2E8B47")
                                drawPinDot(wf * 0.68f, hf * 0.33f, "#F0651F")
                            }
                        }.apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        previewFrame.addView(swatchGraphic)

                        if (active) {
                            val badge = FrameLayout(this).apply {
                                layoutParams = FrameLayout.LayoutParams(dp(22), dp(22), Gravity.TOP or Gravity.END).apply {
                                    topMargin = dp(6)
                                    marginEnd = dp(6)
                                }
                                background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(p.accentPrimary)
                                }
                            }
                            badge.addView(ImageView(this).apply {
                                setImageResource(R.drawable.ic_check)
                                setColorFilter(Color.WHITE)
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                layoutParams = FrameLayout.LayoutParams(dp(13), dp(13), Gravity.CENTER)
                            })
                            previewFrame.addView(badge)
                        }
                        card.addView(previewFrame)

                        card.addView(TextView(this).apply {
                            text = mode.label
                            textSize = 13.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(if (active) p.accentPrimary else p.textPrimary)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            setPadding(dp(4), dp(8), dp(4), dp(1))
                        })
                        card.addView(TextView(this).apply {
                            text = if (isSat) "Aerial imagery + roads" else "Vector roads & terrain"
                            textSize = 11.5f
                            setTextColor(p.textMuted)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            setPadding(dp(4), 0, dp(4), 0)
                        })

                        swatchRow.addView(card)
                    }
                    listCol.addView(swatchRow)
                },
                extraContent = { listCol ->
                    listCol.addView(DesignSystem.createSectionHeader(this, "Visual Overlays", p).apply {
                        setPadding(dp(4), dp(14), dp(4), dp(8))
                    })
                    val overlayCard = DesignSystem.createGroupedCard(
                        this,
                        p,
                        radiusDp = 18f,
                        paddingHDp = 14,
                        paddingVDp = 4,
                        bottomMarginDp = 4
                    )
                    overlayCard.addView(
                        DesignSystem.createSettingsSwitchRow(
                            ctx = this,
                            title = "Mountain Hillshading",
                            subtitle = "Organic terrain relief shading",
                            checked = DevicePrefs.isHillshadeEnabled(this),
                            palette = p
                        ) { enabled ->
                            DevicePrefs.setHillshadeEnabled(this, enabled)
                            map?.style?.let { st -> if (st.isFullyLoaded) applyHillshadeSettings(st) }
                        }
                    )
                    overlayCard.addView(
                        DesignSystem.createSettingsSwitchRow(
                            ctx = this,
                            title = "3D Buildings",
                            subtitle = "Extruded building footprints in urban areas",
                            checked = DevicePrefs.is3dBuildingsEnabled(this),
                            palette = p
                        ) { enabled ->
                            DevicePrefs.set3dBuildingsEnabled(this, enabled)
                            map?.style?.let { st -> if (st.isFullyLoaded) apply3dBuildingsToggle(st) }
                        }
                    )
                    overlayCard.addView(
                        DesignSystem.createSettingsSwitchRow(
                            ctx = this,
                            title = "Elevation Contour Lines",
                            subtitle = "100 m / 500 m / 1000 m contour lines & labels",
                            checked = DevicePrefs.isContoursEnabled(this),
                            palette = p
                        ) { enabled ->
                            DevicePrefs.setContoursEnabled(this, enabled)
                            val m = map
                            val st = m?.style
                            if (st != null && st.isFullyLoaded) {
                                contourManager.attachLayers(st, isDarkMapUi(), enabled)
                                contourManager.setVisible(st, enabled)
                                if (enabled) contourManager.scheduleUpdate(m, true, mapStyleMode == MapStyleMode.VECTOR)
                            }
                        }
                    )
                    listCol.addView(overlayCard)
                }
            )
        }

        btnStatusFilter.setOnClickListener { showMultiStatusFilterDialog() }
        btnSentToggle.setOnClickListener {
            // Tapping the active-filter pill clears active status / unsent filters cleanly back to All Pins
            visibleStatuses.clear()
            visibleStatuses.addAll(ALL_STATUSES)
            DevicePrefs.setVisibleStatuses(this, visibleStatuses)
            hideSentPins = false
            DevicePrefs.setHideSentPins(this, false)
            updateSentToggleUi()
            updateFilterIconTint()
            renderMapData()
            Toast.makeText(this, "Showing all pins", Toast.LENGTH_SHORT).show()
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

        btnLocate.setOnClickListener {
            if (!hasLocationPermission()) {
                locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return@setOnClickListener
            }
            if (!ensureLocationServiceOn()) return@setOnClickListener
            val staleStream = System.currentTimeMillis() - lastLiveLocationFixMs > 10_000L
            isFollowingGps = true
            startLiveGpsUpdates(forceRestart = staleStream)
            updateLocationIconTint()
            UiAnimations.animatePulseBump(btnLocate)
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
            UiAnimations.animateScalePop(btnNavVoice, 0.84f, 150L)
        }
        btnNavVoice.alpha = if (DevicePrefs.isVoiceNav(this)) 1.0f else 0.4f

        btnNavStop.setOnClickListener { stopNavigation() }

        btnTabSearch.setOnClickListener {
            if (activeOpenNavTab == 0 && pageOverlayContainer.visibility == View.VISIBLE && pageBackStack.isEmpty()) return@setOnClickListener
            activeOpenNavTab = 0
            syncNavDockPills()
            openFullPage(::buildSearchPage)
        }
        btnTabBookmarks.setOnClickListener {
            if (activeOpenNavTab == 1 && pageOverlayContainer.visibility == View.VISIBLE && pageBackStack.isEmpty()) return@setOnClickListener
            activeOpenNavTab = 1
            syncNavDockPills()
            openFullPage(::buildBookmarksAndTracksPage)
        }
        btnTabSettings.setOnClickListener {
            if (activeOpenNavTab == 2 && pageOverlayContainer.visibility == View.VISIBLE && pageBackStack.isEmpty()) return@setOnClickListener
            activeOpenNavTab = 2
            syncNavDockPills()
            openFullPage(::buildSettingsPage)
        }
        btnTabAbout.setOnClickListener {
            if (activeOpenNavTab == 3 && pageOverlayContainer.visibility == View.VISIBLE && pageBackStack.isEmpty()) return@setOnClickListener
            activeOpenNavTab = 3
            syncNavDockPills()
            openFullPage(::buildAboutPage)
        }
    }

    private fun showMultiStatusFilterDialog() {
        sheetQuickSelectPinId = null
        val p = currentPalette()
        val night = p.isDark

        sheetContent.removeAllViews()

        val hiddenLayerIds = DevicePrefs.getHiddenLayerIds(this)
        val visibleFolderPins = pins.filter { it.layerId !in hiddenLayerIds }
        val cntDone = visibleFolderPins.count { PinStatus.fromRaw(it.status) == PinStatus.DONE }
        val cntIssue = visibleFolderPins.count { PinStatus.fromRaw(it.status) == PinStatus.ISSUE }
        val cntPending = visibleFolderPins.size - cntDone - cntIssue

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(14)
            }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        titleCol.addView(TextView(this).apply {
            text = "Filter Pins"
            textSize = 19.5f
            letterSpacing = -0.01f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.textPrimary)
        })
        titleCol.addView(TextView(this).apply {
            text = "${visibleFolderPins.size} pins in visible folders"
            textSize = 12.5f
            setTextColor(p.textMuted)
            DesignSystem.applyTabularNumerals(this)
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(titleCol)

        val btnResetFilter = TextView(this).apply {
            text = "Reset"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.accentPrimary)
            setPadding(dp(11), dp(6), dp(11), dp(6))
            UiAnimations.applyRoundedRipple(this@MainActivity, this, p.accentPrimarySoft, 14f, night)
            UiAnimations.attachSnappyPress(this)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dp(6)
            }
        }
        header.addView(btnResetFilter)
        header.addView(DesignSystem.createSheetCloseButton(this, p) { hideInScreenSheet() })
        sheetContent.addView(header)

        val pillsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }

        data class FilterPillUi(
            val key: String,
            val card: LinearLayout,
            val tvCount: TextView,
            val tvLabel: TextView,
            val solidColor: Int,
            val softColor: Int
        )

        val pillItems = mutableListOf<FilterPillUi>()
        var updatingUi = false
        lateinit var swUnsent: MaterialSwitch

        fun refreshFilterVisuals() {
            updatingUi = true
            pillItems.forEach { item ->
                val active = visibleStatuses.contains(item.key)
                item.card.background = if (active) {
                    DesignSystem.buildOpaqueDrawable(
                        ctx = this,
                        fillColor = item.softColor,
                        radiusDp = 16f,
                        strokeColor = item.solidColor,
                        strokeDp = 1.5f
                    )
                } else {
                    DesignSystem.buildOpaqueDrawable(
                        ctx = this,
                        fillColor = p.surfaceTonal,
                        radiusDp = 16f,
                        strokeColor = p.borderSubtle,
                        strokeDp = 1f
                    )
                }
                item.tvCount.setTextColor(if (active) item.solidColor else p.textMuted)
                item.tvLabel.setTextColor(if (active) p.textPrimary else p.textMuted)
                item.tvLabel.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                item.card.alpha = if (active) 1f else 0.72f
            }
            swUnsent.isChecked = hideSentPins
            btnResetFilter.visibility =
                if (!visibleStatuses.containsAll(ALL_STATUSES) || hideSentPins) View.VISIBLE else View.GONE
            updatingUi = false
        }

        fun applyFilterChanges() {
            refreshFilterVisuals()
            DevicePrefs.setVisibleStatuses(this, visibleStatuses)
            DevicePrefs.setHideSentPins(this, hideSentPins)
            updateFilterIconTint()
            updateSentToggleUi()
            renderMapData()
        }

        val statusDefs = listOf(
            listOf("pending", AppStrings.pending(this), cntPending, p.amber, p.amberSoft),
            listOf("done", AppStrings.done(this), cntDone, p.green, p.greenSoft),
            listOf("issue", AppStrings.issue(this), cntIssue, p.red, p.redSoft)
        )
        statusDefs.forEachIndexed { idx, def ->
            val key = def[0] as String
            val labelText = def[1] as String
            val count = def[2] as Int
            val solidColor = def[3] as Int
            val softColor = def[4] as Int

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(68)
                setPadding(dp(8), dp(10), dp(8), dp(10))
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < statusDefs.lastIndex) marginEnd = dp(8)
                }
                UiAnimations.attachSnappyPress(this, 0.95f)
            }
            val topLine = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            topLine.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(solidColor)
                }
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(6) }
            })
            val tvCount = TextView(this).apply {
                text = "$count"
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                DesignSystem.applyTabularNumerals(this)
            }
            topLine.addView(tvCount)
            card.addView(topLine)

            val tvLabel = TextView(this).apply {
                text = labelText
                textSize = 12f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(3), 0, 0)
            }
            card.addView(tvLabel)

            card.setOnClickListener {
                if (visibleStatuses.contains(key)) visibleStatuses.remove(key) else visibleStatuses.add(key)
                UiAnimations.animateScalePop(card, 0.94f, 140L)
                applyFilterChanges()
            }

            pillItems += FilterPillUi(key, card, tvCount, tvLabel, solidColor, softColor)
            pillsRow.addView(card)
        }
        sheetContent.addView(pillsRow)

        val unsentCard = DesignSystem.createGroupedCard(
            this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 4, bottomMarginDp = 4
        )
        val sentCount = activeLocalSentCount()
        val unsentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(54)
            setPadding(dp(2), dp(6), dp(2), dp(6))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
        }
        val unsentTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(10)
            }
        }
        unsentTextCol.addView(TextView(this).apply {
            text = "Unsent Only"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.textPrimary)
        })
        unsentTextCol.addView(TextView(this).apply {
            text = "Hide $sentCount pin${if (sentCount == 1) "" else "s"} marked sent on this phone"
            textSize = 12f
            setTextColor(p.textSecondary)
            DesignSystem.applyTabularNumerals(this)
        })
        swUnsent = MaterialSwitch(this).apply {
            isChecked = hideSentPins
            DesignSystem.styleMaterialSwitch(this, p)
            setOnCheckedChangeListener { _, checked ->
                if (updatingUi) return@setOnCheckedChangeListener
                hideSentPins = checked
                applyFilterChanges()
            }
        }
        unsentRow.setOnClickListener { swUnsent.toggle() }
        unsentRow.addView(unsentTextCol)
        unsentRow.addView(swUnsent)
        unsentCard.addView(unsentRow)
        sheetContent.addView(unsentCard)

        btnResetFilter.setOnClickListener {
            visibleStatuses.clear()
            visibleStatuses.addAll(ALL_STATUSES)
            hideSentPins = false
            applyFilterChanges()
        }

        refreshFilterVisuals()
        UiAnimations.animateSheetContentReveal(sheetContent)
        showInScreenSheet(peekDp = 280, expandToFit = true)
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
                    renderRouteOverlay()
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
                    val qsPinId = sheetQuickSelectPinId
                    if (qsPinId != null) {
                        val freshPin = pinById[qsPinId]
                        sheetQuickSelectPinId = null
                        if (freshPin != null) {
                            showPinCard(freshPin, animateSwap = true, swapForward = false)
                            return
                        }
                    }
                    hideInScreenSheet()
                    return
                }
                if (isFullScreenMapMode) {
                    setFullScreenMapMode(false)
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
            val rawName = data.getQueryParameter("name")?.trim().orEmpty()
            val pinId = data.getQueryParameter("id")?.trim().orEmpty()
            if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                hasAutoZoomedInitial = true
                val target = SharedPinTarget(
                    lat = lat,
                    lng = lng,
                    name = rawName.ifBlank { "Selected Location" },
                    pinId = pinId,
                    sourceHint = "Shared via TerraSync",
                    hasExplicitLabel = rawName.isNotBlank()
                )
                if (map != null) openSharedPinTarget(target) else pendingSharedPin = target
            }
            return
        }

        if (GeoIntentParser.isSupportedLocationUri(data)) {
            hasAutoZoomedInitial = true
            val parsed = GeoIntentParser.parse(data)
            if (parsed != null) {
                handleParsedLocationIntent(parsed)
            } else {
                Toast.makeText(this, "Could not read coordinates from link", Toast.LENGTH_SHORT).show()
            }
            return
        }

        val code = data.getQueryParameter("code")
        if (!code.isNullOrBlank()) {
            joinShareCode(code)
        }
    }

    private fun handleParsedLocationIntent(parsed: ParsedLocationIntent) {
        if (parsed.needsRedirectResolution && parsed.originalUrl.isNotBlank()) {
            Toast.makeText(this, "Opening shared location...", Toast.LENGTH_SHORT).show()
            RoutingEngine.ioExecutor.execute {
                val resolved = GeoIntentParser.resolveRedirectAndParse(parsed.originalUrl)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (resolved != null && !resolved.needsRedirectResolution) {
                        handleParsedLocationIntent(resolved)
                    } else {
                        Toast.makeText(this, "Unable to resolve shared map link", Toast.LENGTH_LONG).show()
                    }
                }
            }
            return
        }

        if (parsed.hasValidCoordinates) {
            val lat = parsed.lat!!
            val lng = parsed.lng!!
            val hasLabel = parsed.label.isNotBlank()
            val target = SharedPinTarget(
                lat = lat,
                lng = lng,
                name = if (hasLabel) parsed.label else "Selected Location",
                pinId = "",
                zoom = parsed.zoom,
                sourceHint = parsed.sourceHint,
                hasExplicitLabel = hasLabel
            )
            if (map != null) openSharedPinTarget(target) else pendingSharedPin = target
            return
        }

        if (parsed.searchQuery.isNotBlank()) {
            Toast.makeText(this, "Searching \"${parsed.searchQuery}\"...", Toast.LENGTH_SHORT).show()
            val center = map?.cameraPosition?.target
            RoutingEngine.searchPlaces(parsed.searchQuery, center?.latitude, center?.longitude) { places, err ->
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    val top = places.firstOrNull()
                    if (top != null) {
                        val resolvedName = top.title.ifBlank { parsed.searchQuery }
                        val target = SharedPinTarget(
                            lat = top.lat,
                            lng = top.lng,
                            name = resolvedName,
                            pinId = "",
                            zoom = parsed.zoom ?: 16.0,
                            sourceHint = top.subtitle.ifBlank { parsed.sourceHint },
                            hasExplicitLabel = resolvedName.isNotBlank()
                        )
                        if (map != null) openSharedPinTarget(target) else pendingSharedPin = target
                    } else {
                        Toast.makeText(this, err ?: "No location found for \"${parsed.searchQuery}\"", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun ensurePinVisibleOnMap(pin: PinItem) {
        var changed = false
        if (!DevicePrefs.isLayerVisible(this, pin.layerId)) {
            DevicePrefs.setLayerVisible(this, pin.layerId, true)
            changed = true
        }
        val normStatus = PinStatus.fromRaw(pin.status).key
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
        hasAutoZoomedInitial = true
        if (isFullScreenMapMode) setFullScreenMapMode(false, animate = false)
        if (isFollowingGps) {
            isFollowingGps = false
            updateLocationIconTint()
        }
        if (pageOverlayContainer.visibility == View.VISIBLE) {
            closeFullPage()
        }
        if (isRulerMode) toggleRulerMode(false)
        if (isLassoMode) toggleLassoMode(false)

        val matched = if (target.pinId.isNotBlank()) pinById[target.pinId] else null
        if (matched != null) {
            activeSharedPinId = null
            ensurePinVisibleOnMap(matched)
            selectPinAndOpenCard(matched, zoomIfFar = true)
            return
        }
        val destLatLng = LatLng(target.lat, target.lng)
        activeSharedPinId = target.pinId.ifBlank { null }
        val desiredZoom = (target.zoom ?: (map?.cameraPosition?.zoom ?: 15.0).coerceAtLeast(16.0)).coerceIn(4.0, 20.0)
        // Route straight into native map-coordinate selection (single sky-blue dot + native place sheet, zero orange overlay pin)
        selectCoordinateOnMap(
            latLng = destLatLng,
            explicitTitle = if (target.hasExplicitLabel) target.name else null,
            fallbackSub = target.sourceHint,
            zoomTo = desiredZoom
        )
    }

    private fun selectCoordinateOnMap(
        latLng: LatLng,
        explicitTitle: String?,
        fallbackSub: String = "",
        zoomTo: Double? = null
    ) {
        selectedPinId = null
        selectedMarkerLatLng = latLng
        renderSelectedMarker()

        if (zoomTo != null) {
            mapView.post {
                if (!isFinishing && !isDestroyed) {
                    centerUpperHalf(latLng, zoomTo)
                }
            }
        }

        val cleanExplicit = explicitTitle?.trim().orEmpty()
        val initialTitle = cleanExplicit.ifBlank { "Selected Location" }
        showTappedPlaceCard(initialTitle, fallbackSub, latLng.latitude, latLng.longitude)

        RoutingEngine.reverseGeocode(latLng.latitude, latLng.longitude) { place ->
            if (place != null) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    if (activeTappedLatLng != latLng || selectedPinId != null) return@runOnUiThread
                    if (sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) return@runOnUiThread
                    val bestTitle = if (cleanExplicit.isNotBlank()) cleanExplicit else place.title
                    val bestSub = place.subtitle.ifBlank { fallbackSub }
                    tappedResolvedTitle = bestTitle
                    tappedResolvedSub = bestSub
                    tappedTitleView?.text = bestTitle
                    val unit = DevicePrefs.getSpeedUnit(this@MainActivity)
                    val distText = currentLocation?.let {
                        "${formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, latLng.latitude, latLng.longitude), unit)} away"
                    } ?: ""
                    tappedSubView?.text = bestSub
                    tappedSubView?.visibility = if (bestSub.isNotBlank()) View.VISIBLE else View.GONE
                    tappedDistView?.text = if (distText.isNotBlank()) {
                        if (bestSub.isNotBlank()) " • $distText" else distText
                    } else ""
                    tappedDistView?.visibility = if (distText.isNotBlank()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    // --- Map Style, Optional 3D Buildings, Hillshading & Overlay Layers ---

    private fun loadMapStyle() {
        val m = map ?: return
        pulseAnimator?.cancel()
        pulseAnimator = null
        contourManager.cancelPending()
        val night = isDarkMapUi()
        val builder = when (mapStyleMode) {
            MapStyleMode.SATELLITE -> Style.Builder().fromJson(STYLE_SATELLITE)
            MapStyleMode.VECTOR -> Style.Builder().fromUri(
                if (night) OfflineMapManager.STYLE_DARK_URL else OfflineMapManager.STYLE_STREET_URL
            )
        }
        m.setStyle(builder) { style ->
            lastRoutePillKey = null
            lastPinsRenderKey = null
            lastTracksRenderKey = null
            apply3dBuildingsToggle(style)
            MapCartography.registerMapBitmaps(this, style, night)
            if (mapStyleMode == MapStyleMode.VECTOR) {
                MapCartography.applyOrganicCartography(this, style, night)
                attachTerrainHillshade(style)
                contourManager.attachLayers(style, night, DevicePrefs.isContoursEnabled(this))
            }
            attachOverlayLayers(style)
            renderMapData()
            flushPendingZoomIfReady()
            contourManager.scheduleUpdate(m, DevicePrefs.isContoursEnabled(this), mapStyleMode == MapStyleMode.VECTOR)
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

    private fun applyHillshadeSettings(style: Style) {
        val enabled = DevicePrefs.isHillshadeEnabled(this) && mapStyleMode == MapStyleMode.VECTOR
        val intensity = DevicePrefs.getHillshadeIntensity(this)
        val night = isDarkMapUi()
        val existing = style.getLayerAs<HillshadeLayer>(HILLSHADE_LAYER)
        if (existing != null) {
            existing.setProperties(
                PropertyFactory.visibility(if (enabled) Property.VISIBLE else Property.NONE),
                PropertyFactory.hillshadeExaggeration(if (night) intensity.exaggeration * 0.55f else intensity.exaggeration),
                PropertyFactory.hillshadeShadowColor(Color.parseColor(if (night) "#0A140D" else "#3C5E2B")),
                PropertyFactory.hillshadeHighlightColor(Color.parseColor(if (night) "#2A3E31" else "#FFFFFF")),
                PropertyFactory.hillshadeAccentColor(Color.parseColor(if (night) "#1F3527" else "#5D8346"))
            )
        } else if (enabled) {
            attachTerrainHillshade(style)
        }
    }

    private fun attachTerrainHillshade(style: Style) {
        if (mapStyleMode != MapStyleMode.VECTOR || !DevicePrefs.isHillshadeEnabled(this)) return
        runCatching {
            if (map?.style !== style || !style.isFullyLoaded) return
            if (style.getSource(HILLSHADE_SOURCE) == null) {
                val tileSet = TileSet(
                    "2.2.0",
                    "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"
                ).apply {
                    encoding = "terrarium"
                    minZoom = 0f
                    maxZoom = 14f
                }
                style.addSource(RasterDemSource(HILLSHADE_SOURCE, tileSet, 256))
            }
            if (style.getLayer(HILLSHADE_LAYER) == null) {
                val night = isDarkMapUi()
                val intensity = DevicePrefs.getHillshadeIntensity(this)
                val hill = HillshadeLayer(HILLSHADE_LAYER, HILLSHADE_SOURCE).withProperties(
                    PropertyFactory.hillshadeExaggeration(if (night) intensity.exaggeration * 0.55f else intensity.exaggeration),
                    PropertyFactory.hillshadeShadowColor(Color.parseColor(if (night) "#0A140D" else "#3C5E2B")),
                    PropertyFactory.hillshadeHighlightColor(Color.parseColor(if (night) "#2A3E31" else "#FFFFFF")),
                    PropertyFactory.hillshadeAccentColor(Color.parseColor(if (night) "#1F3527" else "#5D8346")),
                    PropertyFactory.hillshadeIlluminationDirection(315f)
                )
                val belowLayer = style.layers.firstOrNull { l ->
                    val id = l.id.lowercase()
                    id.contains("water") || id.contains("road") || id.contains("highway") || id.contains("building") || id.contains("contours")
                }
                if (belowLayer != null) style.addLayerBelow(hill, belowLayer.id) else style.addLayer(hill)
            } else {
                applyHillshadeSettings(style)
            }
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
                    PropertyFactory.circleStrokeColor(Expression.get("color")),
                    PropertyFactory.circleStrokeWidth(2f),
                    PropertyFactory.circleStrokeOpacity(0f),
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleOpacity(0f)
                )
            )
        }

        attachPinsSourceAndLayers(style)

        // Unified solid borderless sky-blue selection dot strictly BELOW bookmark pins
        if (style.getSource(SELECTED_SOURCE) == null) {
            style.addSource(GeoJsonSource(SELECTED_SOURCE))
            val haloLayer = CircleLayer(SELECTED_HALO_LAYER, SELECTED_SOURCE).withProperties(
                PropertyFactory.circleColor("#5AA8E8"),
                PropertyFactory.circleOpacity(0.90f),
                PropertyFactory.circleRadius(15.5f),
                PropertyFactory.circleStrokeWidth(0f),
                PropertyFactory.circleStrokeOpacity(0f)
            )
            if (style.getLayer(PINS_LAYER) != null) {
                style.addLayerBelow(haloLayer, PINS_LAYER)
            } else {
                style.addLayer(haloLayer)
            }
        }

        // Ruler Measurement Layers (Terracotta line + white casing + numbered waypoints)
        if (style.getSource(RULER_LINE_SOURCE) == null) {
            style.addSource(GeoJsonSource(RULER_LINE_SOURCE))
            style.addLayer(
                LineLayer(RULER_LINE_CASING_LAYER, RULER_LINE_SOURCE).withProperties(
                    PropertyFactory.lineColor("#FFFFFF"),
                    PropertyFactory.lineOpacity(0.90f),
                    PropertyFactory.lineWidth(6.4f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
                )
            )
            style.addLayer(
                LineLayer(RULER_LINE_LAYER, RULER_LINE_SOURCE).withProperties(
                    PropertyFactory.lineColor("#F0651F"),
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
                    PropertyFactory.circleStrokeColor("#F0651F"),
                    PropertyFactory.circleStrokeWidth(2.8f)
                )
            )
            style.addLayer(
                SymbolLayer(RULER_POINTS_NUM_LAYER, RULER_POINTS_SOURCE).withProperties(
                    PropertyFactory.textField(Expression.get("num")),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(12f),
                    PropertyFactory.textColor("#B8440E"),
                    PropertyFactory.textAllowOverlap(true),
                    PropertyFactory.textIgnorePlacement(true)
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

    private fun attachPinsSourceAndLayers(style: Style) {
        if (style.getSource(PINS_SOURCE) != null) return
        val darkLabels = isDarkMapUi() || mapStyleMode == MapStyleMode.SATELLITE
        val labelColor = if (darkLabels) "#F8FAFC" else "#0F172A"
        val haloColor = if (darkLabels) "#0F172A" else "#FFFFFF"
        val showTitles = DevicePrefs.isShowBookmarkTitles(this)
        val clustered = DevicePrefs.isClusterDensePins(this)

        style.addSource(GeoJsonSource(PINS_SOURCE, FeatureCollection.fromFeatures(emptyList<Feature>())))
        style.addSource(
            GeoJsonSource(
                PINS_CLUSTER_SOURCE,
                FeatureCollection.fromFeatures(emptyList<Feature>()),
                GeoJsonOptions()
                    .withCluster(true)
                    .withClusterMaxZoom(13)
                    .withClusterRadius(46)
            )
        )

        fun makePinLabelLayer(layerId: String, srcId: String) = SymbolLayer(layerId, srcId).withProperties(
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
        ).apply { minZoom = 13.5f }

        fun makePinIconLayer(layerId: String, srcId: String) = SymbolLayer(layerId, srcId).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
            PropertyFactory.iconOffset(arrayOf(0f, MapBitmaps.BOOKMARK_PIN_TIP_BOTTOM_PAD_DP)),
            PropertyFactory.iconSize(1.0f),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true)
        )

        val labelLayer = makePinLabelLayer(PINS_LABELS_LAYER, PINS_SOURCE)
        val clusterLabelLayer = makePinLabelLayer(PINS_CLUSTER_LABELS_LAYER, PINS_CLUSTER_SOURCE).apply {
            setFilter(Expression.not(Expression.has("point_count")))
        }
        val pinLayer = makePinIconLayer(PINS_LAYER, PINS_SOURCE)
        val clusterPinLayer = makePinIconLayer(PINS_CLUSTER_UNCLUSTERED_LAYER, PINS_CLUSTER_SOURCE).apply {
            setFilter(Expression.not(Expression.has("point_count")))
        }
        val clusterCircleLayer = CircleLayer(PINS_CLUSTER_CIRCLE_LAYER, PINS_CLUSTER_SOURCE).apply {
            setFilter(Expression.has("point_count"))
            setProperties(
                PropertyFactory.circleColor(Color.parseColor("#18794E")),
                PropertyFactory.circleRadius(
                    Expression.switchCase(
                        Expression.gte(Expression.toNumber(Expression.get("point_count")), Expression.literal(250)),
                        Expression.literal(25f),
                        Expression.gte(Expression.toNumber(Expression.get("point_count")), Expression.literal(50)),
                        Expression.literal(21f),
                        Expression.literal(17f)
                    )
                ),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(2.5f),
                PropertyFactory.circleOpacity(0.95f)
            )
        }
        val clusterCountLayer = SymbolLayer(PINS_CLUSTER_COUNT_LAYER, PINS_CLUSTER_SOURCE).apply {
            setFilter(Expression.has("point_count"))
            setProperties(
                PropertyFactory.textField(
                    Expression.coalesce(
                        Expression.get("point_count_abbreviated"),
                        Expression.toString(Expression.get("point_count"))
                    )
                ),
                PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                PropertyFactory.textSize(12.5f),
                PropertyFactory.textColor(Color.WHITE),
                PropertyFactory.textAllowOverlap(true),
                PropertyFactory.textIgnorePlacement(true)
            )
        }

        if (style.getLayer(SELECTED_HALO_LAYER) != null) {
            style.addLayerBelow(labelLayer, SELECTED_HALO_LAYER)
            style.addLayerBelow(clusterLabelLayer, SELECTED_HALO_LAYER)
            style.addLayerAbove(pinLayer, SELECTED_HALO_LAYER)
        } else {
            style.addLayer(labelLayer)
            style.addLayer(clusterLabelLayer)
            style.addLayer(pinLayer)
        }
        style.addLayerAbove(clusterPinLayer, PINS_LAYER)
        style.addLayerAbove(clusterCircleLayer, PINS_CLUSTER_UNCLUSTERED_LAYER)
        style.addLayerAbove(clusterCountLayer, PINS_CLUSTER_CIRCLE_LAYER)

        applyPinClusterModeVisibility(style, clustered, showTitles)
    }

    private fun applyPinClusterModeVisibility(style: Style, clustered: Boolean, showTitles: Boolean) {
        val visUnclustered = if (!clustered) Property.VISIBLE else Property.NONE
        val visUnclusteredLabels = if (!clustered && showTitles) Property.VISIBLE else Property.NONE
        val visClustered = if (clustered) Property.VISIBLE else Property.NONE
        val visClusteredLabels = if (clustered && showTitles) Property.VISIBLE else Property.NONE
        style.getLayer(PINS_LAYER)?.setProperties(PropertyFactory.visibility(visUnclustered))
        style.getLayer(PINS_LABELS_LAYER)?.setProperties(PropertyFactory.visibility(visUnclusteredLabels))
        style.getLayer(PINS_CLUSTER_UNCLUSTERED_LAYER)?.setProperties(PropertyFactory.visibility(visClustered))
        style.getLayer(PINS_CLUSTER_LABELS_LAYER)?.setProperties(PropertyFactory.visibility(visClusteredLabels))
        style.getLayer(PINS_CLUSTER_CIRCLE_LAYER)?.setProperties(PropertyFactory.visibility(visClustered))
        style.getLayer(PINS_CLUSTER_COUNT_LAYER)?.setProperties(PropertyFactory.visibility(visClustered))
    }

    private fun getVisiblePins(): List<PinItem> {
        val hiddenLayerIds = DevicePrefs.getHiddenLayerIds(this)
        val sentIds = if (hideSentPins) DevicePrefs.getLocalSentPinIds(this) else emptySet()
        return pins.filter { p ->
            val normStatus = PinStatus.fromRaw(p.status).key
            p.layerId !in hiddenLayerIds && normStatus in visibleStatuses && p.id !in sentIds
        }
    }

    private fun findExactStackedPins(pin: PinItem): List<PinItem> {
        val hiddenLayerIds = DevicePrefs.getHiddenLayerIds(this)
        val sentIds = if (hideSentPins) DevicePrefs.getLocalSentPinIds(this) else emptySet()
        val cx = kotlin.math.floor(pin.lat / stackedCellSize).toInt()
        val cy = kotlin.math.floor(pin.lng / stackedCellSize).toInt()
        val stack = ArrayList<PinItem>(4)
        for (dx in -1..1) {
            for (dy in -1..1) {
                val bucket = stackedPinBuckets[stackedBucketKey(cx + dx, cy + dy)] ?: continue
                for (it in bucket) {
                    val normStatus = PinStatus.fromRaw(it.status).key
                    if (it.layerId !in hiddenLayerIds &&
                        normStatus in visibleStatuses &&
                        it.id !in sentIds &&
                        kotlin.math.abs(it.lat - pin.lat) <= stackedCellSize &&
                        kotlin.math.abs(it.lng - pin.lng) <= stackedCellSize &&
                        RoutingEngine.haversineMeters(it.lat, it.lng, pin.lat, pin.lng) <= 2.0
                    ) {
                        stack.add(it)
                    }
                }
            }
        }
        if (stack.none { it.id == pin.id }) return listOf(pin)
        if (stack.size > 1) stack.sortBy { it.id }
        return stack
    }

    private fun parseTrackPointsCached(t: TrackItem): List<Point> = synchronized(trackPointCache) {
        val cached = trackPointCache[t.id]
        if (cached != null && cached.first == t.updatedAt) return cached.second
        val pts = ArrayList<Point>()
        val raw = t.coords
        var start = 0
        val len = raw.length
        while (start < len) {
            val semi = raw.indexOf(';', start).let { if (it < 0) len else it }
            val comma = raw.indexOf(',', start)
            if (comma in (start + 1) until semi) {
                val lng = raw.substring(start, comma).trim().toDoubleOrNull()
                val lat = raw.substring(comma + 1, semi).trim().toDoubleOrNull()
                if (lng != null && lat != null && lat.isFinite() && lng.isFinite()) {
                    pts.add(Point.fromLngLat(lng, lat))
                }
            }
            start = semi + 1
        }
        trackPointCache[t.id] = t.updatedAt to pts
        pts
    }

    private fun renderPinsAndTracks(style: Style) {
        val hiddenLayerIds = DevicePrefs.getHiddenLayerIds(this)
        val sentIds = if (hideSentPins) DevicePrefs.getLocalSentPinIds(this) else emptySet()
        val statusesSnap = visibleStatuses.toSet()
        val clustered = DevicePrefs.isClusterDensePins(this)
        val showTitles = DevicePrefs.isShowBookmarkTitles(this)

        applyPinClusterModeVisibility(style, clustered, showTitles)

        val selId = selectedPinId
        val pinsKey = "$dataVersion|$selId|$clustered|${statusesSnap.hashCode()}|${statusesSnap.size}|${hiddenLayerIds.hashCode()}|${hiddenLayerIds.size}|$hideSentPins|${sentIds.hashCode()}|${sentIds.size}"
        if (pinsKey != lastPinsRenderKey) {
            lastPinsRenderKey = pinsKey
            val seq = ++pinRenderSeq
            val pinsSnap = pins
            RoutingEngine.ioExecutor.execute {
                val doneFeatures = ArrayList<Feature>(pinsSnap.size / 2)
                val issueFeatures = ArrayList<Feature>(pinsSnap.size / 4)
                val pendingFeatures = ArrayList<Feature>(pinsSnap.size / 4)
                var selectedPinItem: PinItem? = null

                fun toPinFeature(pin: PinItem, st: PinStatus): Feature {
                    val trimmed = pin.name.trim()
                    val shortLabel = if (trimmed.length > 22) trimmed.take(21).trimEnd() + "…" else trimmed
                    return Feature.fromGeometry(Point.fromLngLat(pin.lng, pin.lat)).apply {
                        addStringProperty("id", pin.id)
                        addStringProperty("name", pin.name)
                        addStringProperty("label", shortLabel)
                        addStringProperty("icon", st.iconId)
                    }
                }

                for (pin in pinsSnap) {
                    if (pin.layerId in hiddenLayerIds || pin.id in sentIds) continue
                    val st = PinStatus.fromRaw(pin.status)
                    if (st.key !in statusesSnap) continue
                    if (selId != null && pin.id == selId) {
                        selectedPinItem = pin
                        continue
                    }
                    val f = toPinFeature(pin, st)
                    when (st) {
                        PinStatus.DONE -> doneFeatures.add(f)
                        PinStatus.ISSUE -> issueFeatures.add(f)
                        PinStatus.PENDING -> pendingFeatures.add(f)
                    }
                }

                val pinFeatures = ArrayList<Feature>(
                    doneFeatures.size + issueFeatures.size + pendingFeatures.size + (if (selectedPinItem != null) 1 else 0)
                )
                pinFeatures.addAll(doneFeatures)
                pinFeatures.addAll(issueFeatures)
                pinFeatures.addAll(pendingFeatures)
                selectedPinItem?.let { sel ->
                    pinFeatures.add(toPinFeature(sel, PinStatus.fromRaw(sel.status)))
                }
                val fc = FeatureCollection.fromFeatures(pinFeatures)
                val emptyFc = FeatureCollection.fromFeatures(emptyList<Feature>())
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && seq == pinRenderSeq && map?.style === style && style.isFullyLoaded) {
                        applyPinClusterModeVisibility(style, clustered, showTitles)
                        if (clustered) {
                            style.getSourceAs<GeoJsonSource>(PINS_CLUSTER_SOURCE)?.setGeoJson(fc)
                            style.getSourceAs<GeoJsonSource>(PINS_SOURCE)?.setGeoJson(emptyFc)
                        } else {
                            style.getSourceAs<GeoJsonSource>(PINS_SOURCE)?.setGeoJson(fc)
                            style.getSourceAs<GeoJsonSource>(PINS_CLUSTER_SOURCE)?.setGeoJson(emptyFc)
                        }
                    }
                }
            }
        }

        val tracksKey = "$dataVersion|${hiddenLayerIds.hashCode()}|${hiddenLayerIds.size}"
        if (tracksKey != lastTracksRenderKey) {
            lastTracksRenderKey = tracksKey
            val seqT = ++trackRenderSeq
            val tracksSnap = tracks
            RoutingEngine.ioExecutor.execute {
                val trackFeatures = ArrayList<Feature>(tracksSnap.size)
                for (t in tracksSnap) {
                    if (t.layerId in hiddenLayerIds) continue
                    val pts = parseTrackPointsCached(t)
                    if (pts.size >= 2) trackFeatures.add(Feature.fromGeometry(LineString.fromLngLats(pts)))
                }
                val fcT = FeatureCollection.fromFeatures(trackFeatures)
                runOnUiThread {
                    if (!isFinishing && !isDestroyed && seqT == trackRenderSeq && map?.style === style && style.isFullyLoaded) {
                        style.getSourceAs<GeoJsonSource>(TRACKS_SOURCE)?.setGeoJson(fcT)
                    }
                }
            }
        }
    }

    private fun renderRouteOverlay(styleArg: Style? = null) {
        val style = styleArg ?: map?.style ?: return
        if (!style.isFullyLoaded) return
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
    }

    private fun renderRulerOverlay(styleArg: Style? = null) {
        val style = styleArg ?: map?.style ?: return
        if (!style.isFullyLoaded) return
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
    }

    private fun renderSelectedMarker(styleArg: Style? = null) {
        val style = styleArg ?: map?.style ?: return
        if (!style.isFullyLoaded) return
        val selLoc = selectedMarkerLatLng
        val selFeatures = if (selLoc != null) {
            listOf(Feature.fromGeometry(Point.fromLngLat(selLoc.longitude, selLoc.latitude)))
        } else emptyList()
        style.getSourceAs<GeoJsonSource>(SELECTED_SOURCE)
            ?.setGeoJson(FeatureCollection.fromFeatures(selFeatures))
    }

    private fun renderMapData() {
        val p = currentPalette()
        btnSyncStatus.setColorFilter(if (isLiveConnected) p.accentPrimary else p.amber)

        val style = map?.style ?: return
        if (!style.isFullyLoaded) return

        renderPinsAndTracks(style)
        renderRouteOverlay(style)
        renderRulerOverlay(style)
        renderSelectedMarker(style)

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
        if (!isActivityResumed || UiAnimations.isReducedMotion(this)) return
        val style = map?.style ?: return
        if (!style.isFullyLoaded) return
        val f = Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply {
            addStringProperty("color", statusHex)
        }
        style.getSourceAs<GeoJsonSource>(PULSE_SOURCE)?.setGeoJson(FeatureCollection.fromFeatures(listOf(f)))
        if (style.getLayerAs<CircleLayer>(PULSE_LAYER) == null) return

        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520L
            interpolator = UiAnimations.EMPHASIZED_DECELERATE
            addUpdateListener { anim ->
                val curStyle = map?.style
                if (!isActivityStarted || curStyle !== style || !curStyle.isFullyLoaded) {
                    anim.cancel()
                    return@addUpdateListener
                }
                val v = anim.animatedValue as Float
                val fade = 1f - v
                runCatching {
                    curStyle.getLayerAs<CircleLayer>(PULSE_LAYER)?.setProperties(
                        PropertyFactory.circleRadius(8f + 30f * v),
                        PropertyFactory.circleOpacity(0.42f * fade),
                        PropertyFactory.circleStrokeWidth(2.4f - 1.6f * v),
                        PropertyFactory.circleStrokeOpacity(0.85f * fade)
                    )
                }
            }
            start()
        }
    }

    // --- Multi-Point Distance Ruler Tool ---

    // --- Local-Only Sent Pin Tracking & Draw-a-Circle (Lasso) Selection ---

    private fun updateSentToggleUi() {
        if (!::btnSentToggle.isInitialized) return
        val p = currentPalette()
        val hasStatusFilter = !visibleStatuses.containsAll(ALL_STATUSES)
        val hasAnyFilter = hasStatusFilter || hideSentPins
        if (!hasAnyFilter) {
            btnSentToggle.visibility = View.GONE
            updateFilterIconTint()
            return
        }
        val parts = mutableListOf<String>()
        if (hasStatusFilter) {
            if (visibleStatuses.isEmpty()) {
                parts.add("Hidden")
            } else {
                if ("pending" in visibleStatuses) parts.add(AppStrings.pending(this))
                if ("done" in visibleStatuses) parts.add(AppStrings.done(this))
                if ("issue" in visibleStatuses) parts.add(AppStrings.issue(this))
            }
        }
        if (hideSentPins) parts.add("Unsent")
        val pillColor = when {
            visibleStatuses == setOf("pending") && !hideSentPins -> p.amber
            visibleStatuses == setOf("done") && !hideSentPins -> p.green
            visibleStatuses == setOf("issue") && !hideSentPins -> p.red
            else -> p.accentPrimary
        }
        btnSentToggle.text = "${parts.joinToString(" • ")} ×"
        btnSentToggle.setTextColor(Color.WHITE)
        DesignSystem.applyFloatingControl(this, btnSentToggle, p, 22f, 6f, activeFillColor = pillColor)
        btnSentToggle.visibility = View.VISIBLE
        updateFilterIconTint()
    }

    private fun activeLocalSentCount(): Int {
        val sentIds = DevicePrefs.getLocalSentPinIds(this)
        if (sentIds.isEmpty()) return 0
        return pins.count { it.id in sentIds }
    }

    private fun toggleLassoMode(enabled: Boolean) {
        if (enabled && isFullScreenMapMode) setFullScreenMapMode(false, animate = false)
        if (enabled && isRulerMode) toggleRulerMode(false)
        isLassoMode = enabled
        lassoMoveMapMode = false
        if (enabled) {
            hideInScreenSheet()
            UiAnimations.fadeShow(bottomDock, 160L)
            bottomNavDock.visibility = View.GONE
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
            if (activeNavRoute == null && !isRulerMode) UiAnimations.showSlideBottom(bottomNavDock)
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
        showThemedDialog(
            themedDialogBuilder("Reset Sent Marks?")
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
                .create(),
            destructivePositive = true
        )
    }

    private fun updateLassoUi() {
        val p = currentPalette()
        val sel = lassoPendingPinIds.size
        val totalSent = activeLocalSentCount()
        if (lassoMoveMapMode) {
            btnLassoModeToggle.text = "Draw"
            btnLassoModeToggle.setTextColor(Color.WHITE)
            UiAnimations.applyRoundedRipple(this, btnLassoModeToggle, p.green, 10f, isDark = true)
            tvLassoTitle.text = "Move or zoom the map"
            tvLassoSubtitle.text = "Tap Draw when ready to circle pins"
            btnLassoMarkSent.text = "Mark Sent"
            btnLassoMarkSent.alpha = 0.45f
            btnLassoRedraw.alpha = 0.45f
        } else {
            btnLassoModeToggle.text = "Move Map"
            btnLassoModeToggle.setTextColor(p.accent)
            UiAnimations.applyRoundedRipple(this, btnLassoModeToggle, p.accentSoft, 10f, p.isDark)
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
        if (enabled && isFullScreenMapMode) setFullScreenMapMode(false, animate = false)
        if (enabled && isLassoMode) toggleLassoMode(false)
        isRulerMode = enabled
        if (enabled) {
            hideInScreenSheet()
            UiAnimations.fadeShow(bottomDock, 160L)
            bottomNavDock.visibility = View.GONE
            UiAnimations.showSlideBottom(rulerCard)
            updateRulerUi()
        } else {
            rulerPoints.clear()
            UiAnimations.hideSlideBottom(rulerCard)
            if (activeNavRoute == null && !isLassoMode) UiAnimations.showSlideBottom(bottomNavDock)
        }
        applyMapChromeTheme()
        renderRulerOverlay()
    }

    private fun addRulerPoint(latLng: LatLng) {
        rulerPoints.add(latLng)
        fireStatusPulse(latLng.latitude, latLng.longitude, "#F0651F")
        updateRulerUi()
        renderRulerOverlay()
    }

    private fun undoRulerPoint() {
        if (rulerPoints.isNotEmpty()) {
            rulerPoints.removeAt(rulerPoints.lastIndex)
            updateRulerUi()
            renderRulerOverlay()
        }
    }

    private fun resetRulerPoints() {
        rulerPoints.clear()
        updateRulerUi()
        renderRulerOverlay()
    }

    private fun updateRulerUi() {
        var totalMeters = 0.0
        for (i in 0 until rulerPoints.size - 1) {
            val a = rulerPoints[i]
            val b = rulerPoints[i + 1]
            totalMeters += RoutingEngine.haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        }
        val unit = DevicePrefs.getSpeedUnit(this)
        tvRulerDistance.text = when {
            rulerPoints.size < 2 -> if (unit == SpeedUnit.IMPERIAL) "0 ft" else "0 m"
            unit == SpeedUnit.IMPERIAL -> {
                val ft = totalMeters * 3.28084
                val mi = totalMeters / 1609.344
                when {
                    ft < 1000.0 -> "${ft.roundToInt()} ft"
                    mi < 10.0 -> "${"%.2f".format(Locale.US, mi)} mi"
                    else -> "${"%.1f".format(Locale.US, mi)} mi"
                }
            }
            totalMeters < 1000.0 -> "${totalMeters.roundToInt()} m"
            totalMeters < 10000.0 -> "${"%.2f".format(Locale.US, totalMeters / 1000.0)} km"
            else -> "${"%.1f".format(Locale.US, totalMeters / 1000.0)} km"
        }
        tvRulerSubtitle.text = when (rulerPoints.size) {
            0 -> "Tap map to drop point 1"
            1 -> "Tap next point to measure"
            else -> "Total distance between ${rulerPoints.size} points"
        }
        btnRulerUndo.alpha = if (rulerPoints.isNotEmpty()) 1.0f else 0.35f
        btnRulerReset.visibility = if (rulerPoints.isNotEmpty()) View.VISIBLE else View.GONE
    }

    // --- Route From Origin Helpers ---

    private fun setRouteOrigin(lat: Double, lng: Double, label: String) {
        routeOriginLatLng = LatLng(lat, lng)
        routeOriginName = label.ifBlank { "${"%.5f".format(Locale.US, lat)}, ${"%.5f".format(Locale.US, lng)}" }
        tvRouteFromText.text = "From: $routeOriginName"
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

        val clustered = DevicePrefs.isClusterDensePins(this)
        if (clustered) {
            val clusterHits = m.queryRenderedFeatures(hitBox, PINS_CLUSTER_CIRCLE_LAYER)
            val clusterPt = clusterHits.firstOrNull()?.geometry() as? Point
            if (clusterPt != null) {
                val targetZoom = (m.cameraPosition.zoom + 2.2).coerceAtMost(16.0)
                m.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(clusterPt.latitude(), clusterPt.longitude()),
                        targetZoom
                    ),
                    350
                )
                return
            }
        }

        val pinHits = if (clustered) {
            m.queryRenderedFeatures(hitBox, PINS_CLUSTER_UNCLUSTERED_LAYER, PINS_CLUSTER_LABELS_LAYER)
        } else {
            m.queryRenderedFeatures(hitBox, PINS_LAYER, PINS_LABELS_LAYER)
        }
        val pin = pinHits
            .mapNotNull { hit -> hit.getStringProperty("id")?.let { id -> pinById[id] } }
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

        // In Full-Screen Clean Map Mode, tapping empty map restores controls cleanly without dropping a dot
        if (isFullScreenMapMode) {
            setFullScreenMapMode(false)
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
        val mapLang = DevicePrefs.getMapLabelLanguage(this)
        for (f in vectorFeatures) {
            val props = f.properties() ?: continue
            val name = MapCartography.resolveFeatureDisplayName(props, mapLang)
            if (name != null) {
                detectedTitle = name
                val cls = if (props.has("class") && !props.get("class").isJsonNull) props.get("class").asString else ""
                if (cls.isNotBlank()) detectedSub = cls.replace("_", " ").replaceFirstChar { it.uppercase() }
                break
            }
        }

        selectCoordinateOnMap(
            latLng = latLng,
            explicitTitle = detectedTitle.ifBlank { null },
            fallbackSub = detectedSub,
            zoomTo = null
        )
    }

    private fun selectPinAndOpenCard(pin: PinItem, zoomIfFar: Boolean = false) {
        selectedPinId = pin.id
        selectedMarkerLatLng = LatLng(pin.lat, pin.lng)
        renderSelectedMarker()
        val stackedCount = findExactStackedPins(pin).size
        // Only re-sort PINS_SOURCE when stacked pins share this coordinate; avoids single-pin tile flicker behind the blue halo
        if (stackedCount > 1) {
            map?.style?.takeIf { it.isFullyLoaded }?.let { renderPinsAndTracks(it) }
        }
        val currentZoom = map?.cameraPosition?.zoom ?: 15.0
        val targetZoom = if (zoomIfFar) currentZoom.coerceAtLeast(15.8) else currentZoom
        centerUpperHalf(
            LatLng(pin.lat, pin.lng),
            targetZoom,
            bottomPadDp = if (stackedCount > 1) 340 else 285
        )
        showPinCard(pin, animateSwap = false)
    }

    private fun clearSelectedMarker() {
        val hadSelection = selectedPinId != null || selectedMarkerLatLng != null
        selectedPinId = null
        selectedMarkerLatLng = null
        activeSharedPinId = null
        activeTappedLatLng = null
        tappedTitleView = null
        tappedSubView = null
        tappedDistView = null
        if (hadSelection) renderSelectedMarker()
    }

    private fun centerUpperHalf(target: LatLng, zoom: Double, bottomPadDp: Int = 270) {
        val m = map ?: return
        val cam = CameraPosition.Builder()
            .target(target)
            .zoom(zoom)
            .padding(0.0, 0.0, 0.0, dp(bottomPadDp).toDouble())
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

    private fun showInScreenSheet(
        peekDp: Int = 280,
        inPlace: Boolean = false,
        expandToFit: Boolean = false
    ) {
        activeSheetPeekDp = peekDp
        val bottomClearance = maxOf(navBarInsetBottom, imeInsetBottom)
        val screenH = resources.displayMetrics.heightPixels
        val maxSheetH = (screenH - statusBarInsetTop - dp(12)).coerceAtLeast(dp(240))
        sheetBehavior.maxHeight = maxSheetH
        val targetPeek = if (expandToFit) {
            val availW = (resources.displayMetrics.widthPixels - sideInsetLeft - sideInsetRight).coerceAtLeast(dp(240))
            inScreenSheet.measure(
                View.MeasureSpec.makeMeasureSpec(availW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val measured = inScreenSheet.measuredHeight.coerceAtLeast(dp(peekDp) + bottomClearance)
            val capH = if (imeInsetBottom > 0) maxSheetH else (screenH * 0.85f).toInt().coerceAtLeast(dp(240))
            minOf(measured, capH)
        } else {
            val maxPeek = (screenH * 0.68f).toInt().coerceAtLeast(dp(200))
            minOf(dp(peekDp) + bottomClearance, maxPeek)
        }

        val alreadyOpen = sheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED ||
            sheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
        if (alreadyOpen) {
            isSwappingSheetInPlace = true
            if (::sheetScrollView.isInitialized && !inPlace) sheetScrollView.scrollTo(0, 0)
            sheetBehavior.setPeekHeight(targetPeek, false)
            inScreenSheet.requestLayout()
            if (expandToFit) {
                inScreenSheet.post {
                    if (!isFinishing && !isDestroyed && sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                        sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
                    }
                    isSwappingSheetInPlace = false
                }
            } else {
                inScreenSheet.post { isSwappingSheetInPlace = false }
            }
        } else {
            bottomDock.translationY = 0f
            UiAnimations.fadeHide(bottomDock, 120L)
            if (::sheetScrollView.isInitialized) sheetScrollView.scrollTo(0, 0)
            sheetBehavior.setPeekHeight(targetPeek, false)
            inScreenSheet.requestLayout()
            val openToken = ++sheetOpenSeq
            inScreenSheet.post {
                if (!isFinishing && !isDestroyed && openToken == sheetOpenSeq) {
                    sheetBehavior.state = if (expandToFit) {
                        BottomSheetBehavior.STATE_EXPANDED
                    } else {
                        BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }
        }
    }

    private fun hideInScreenSheet() {
        hideSoftKeyboard()
        sheetOpenSeq++
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    private fun makePinShareMessage(name: String, lat: Double, lng: Double, pinId: String = ""): String {
        val encodedName = Uri.encode(name)
        val idPart = if (pinId.isNotBlank()) "&id=${Uri.encode(pinId)}" else ""
        val link = "https://thamirakkurd.github.io/TerraSync/pin?lat=$lat&lng=$lng&name=$encodedName$idPart"
        return "$name\n${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lng)}\nOpen in TerraSync: $link"
    }

    // --- Polished Pin Card (Stacked Cycler, Route To / Route From, Owner-Only Delete) ---

    private fun showPinCard(pin: PinItem, animateSwap: Boolean = false, swapForward: Boolean = true) {
        hideSoftKeyboard()
        sheetQuickSelectPinId = null
        val p = currentPalette()
        val night = p.isDark
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary
        val chipBg = p.chipSurface
        val cardTint = p.cardSurface

        sheetContent.removeAllViews()

        // 1. Exact-Coordinate Stacked Pin Banner + Animated Prev/Next Buttons
        val stackedPins = findExactStackedPins(pin)
        if (stackedPins.size > 1) {
            val currentIdx = stackedPins.indexOfFirst { it.id == pin.id }.coerceAtLeast(0)
            val stackRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(8), dp(8), dp(8))
                background = GradientDrawable().apply {
                    setColor(p.accentPrimarySoft)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), p.borderSubtle)
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }

            stackRow.addView(TextView(this).apply {
                text = "${stackedPins.size} stacked • ${currentIdx + 1}/${stackedPins.size}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.accent)
                DesignSystem.applyTabularNumerals(this)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            fun jumpStacked(delta: Int, forward: Boolean) {
                val nextIdx = (currentIdx + delta + stackedPins.size) % stackedPins.size
                val nextPin = stackedPins[nextIdx]
                selectedPinId = nextPin.id
                selectedMarkerLatLng = LatLng(nextPin.lat, nextPin.lng)
                renderSelectedMarker()
                map?.style?.takeIf { it.isFullyLoaded }?.let { renderPinsAndTracks(it) }
                fireStatusPulse(nextPin.lat, nextPin.lng, statusColorHex(nextPin.status))
                showPinCard(nextPin, animateSwap = true, swapForward = forward)
            }

            if (stackedPins.size > 2) {
                stackRow.addView(MaterialButton(this).apply {
                    text = "‹"
                    textSize = 17f
                    setTypeface(null, Typeface.BOLD)
                    isAllCaps = false
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(11), 0, dp(11), 0)
                    setTextColor(if (night) p.textPrimary else p.accent)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(if (night) p.cardSurface else Color.WHITE)
                    strokeWidth = dp(1)
                    strokeColor = android.content.res.ColorStateList.valueOf(p.borderSubtle)
                    cornerRadius = dp(10)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                        marginEnd = dp(6)
                    }
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener { jumpStacked(-1, forward = false) }
                })
            }

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
                setBackgroundColor(p.accent)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))
                UiAnimations.attachSnappyPress(this)
                setOnClickListener { jumpStacked(1, forward = true) }
            })
            sheetContent.addView(stackRow)
        }

        // 2. Top Header: Status Badge + Title/Coords + Star + Share + Circular Close
        val pinStatus = PinStatus.fromRaw(pin.status)
        val badgeBg = when (pinStatus) {
            PinStatus.DONE -> p.greenSoft
            PinStatus.ISSUE -> p.redSoft
            PinStatus.PENDING -> p.amberSoft
        }
        val badgeGlyph = when (pinStatus) {
            PinStatus.DONE -> p.green
            PinStatus.ISSUE -> p.red
            PinStatus.PENDING -> p.amber
        }
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(8)
            }
        }
        val pinBadge = DesignSystem.createHeaderBadge(
            this,
            R.drawable.ic_location_pin_glyph,
            p,
            bgColor = badgeBg,
            glyphColor = badgeGlyph,
            sizeDp = 54
        ).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12)
        }
        headerRow.addView(pinBadge)

        val layer = layerById[pin.layerId]
        val layerName = layer?.name ?: "Field Pin"
        val speedUnit = DevicePrefs.getSpeedUnit(this)
        val distText = currentLocation?.let {
            " • ${formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, pin.lat, pin.lng), speedUnit)} away"
        } ?: ""

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        titleCol.addView(TextView(this).apply {
            text = pin.name
            textSize = 19.5f
            letterSpacing = -0.01f
            setLineSpacing(0f, 1.12f)
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        val subRow = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                if (childCount == 2 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                    val distChild = getChildAt(1)
                    if (distChild.visibility == View.VISIBLE) {
                        distChild.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST), heightMeasureSpec)
                        (getChildAt(0) as? TextView)?.maxWidth = (avail - distChild.measuredWidth).coerceAtLeast(dp(56))
                    } else {
                        (getChildAt(0) as? TextView)?.maxWidth = avail
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        subRow.addView(TextView(this).apply {
            text = layerName
            textSize = 12.5f
            setTextColor(p.accentPrimary)
            setTypeface(null, Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        })
        if (distText.isNotBlank()) {
            subRow.addView(TextView(this).apply {
                text = distText
                textSize = 12.5f
                setTextColor(p.accentPrimary)
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                DesignSystem.applyTabularNumerals(this)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            })
        }
        titleCol.addView(subRow)
        headerRow.addView(titleCol)
        headerRow.addView(DesignSystem.createSheetCloseButton(this, p) { hideInScreenSheet() })
        sheetContent.addView(headerRow)

        // Full-width Coordinates + Unclipped Copy / Star / Share Utility Row
        val coordActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
        coordActionRow.addView(TextView(this).apply {
            text = "${"%.6f".format(Locale.US, pin.lat)}, ${"%.6f".format(Locale.US, pin.lng)}"
            textSize = 13f
            setTextColor(txtSub)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            DesignSystem.applyTabularNumerals(this)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        })
        coordActionRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_copy)
            setColorFilter(p.accentPrimary)
            contentDescription = "Copy Coordinates"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(2) }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSnappyPress(this, 0.90f)
            setOnClickListener {
                copyToClipboard("GPS", "${"%.6f".format(Locale.US, pin.lat)}, ${"%.6f".format(Locale.US, pin.lng)}")
            }
        })

        var starred = DevicePrefs.isBookmarked(this, pin.id)
        coordActionRow.addView(ImageButton(this).apply {
            setImageResource(if (starred) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            if (!starred) setColorFilter(p.iconMuted)
            contentDescription = if (starred) "Unstar Pin" else "Star Pin"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(2) }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                starred = DevicePrefs.toggleBookmark(this@MainActivity, pin.id)
                UiAnimations.animateIconSwap(
                    this,
                    if (starred) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                    newTint = if (starred) null else p.iconMuted,
                    clearTint = starred
                )
                Toast.makeText(this@MainActivity, if (starred) "Starred" else "Unstarred", Toast.LENGTH_SHORT).show()
            }
        })

        coordActionRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_share)
            setColorFilter(p.iconTint)
            contentDescription = "Share Pin"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val msg = makePinShareMessage(pin.name, pin.lat, pin.lng, pin.id)
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, msg)
                }, "Share TerraSync Pin"))
            }
        })
        sheetContent.addView(coordActionRow)

        // 3. Active Status Banner (Issue / Done note)
        if (pinStatus == PinStatus.ISSUE && pin.issueReason.isNotBlank()) {
            sheetContent.addView(TextView(this).apply {
                text = "⚠ Issue: ${DailyReportGenerator.flattenSingleLine(pin.issueReason)}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.red)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(p.redSoft)
                    cornerRadius = dp(12).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            })
        } else if (pinStatus == PinStatus.DONE && pin.issueReason.isNotBlank()) {
            sheetContent.addView(TextView(this).apply {
                text = "✓ Done: ${DailyReportGenerator.flattenSingleLine(pin.issueReason)}"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.green)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(p.greenSoft)
                    cornerRadius = dp(12).toFloat()
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
        data class StatusButtonStyle(val key: String, val title: String, val solidColor: Int, val softColor: Int, val inactiveTextColor: Int)
        listOf(
            StatusButtonStyle("pending", AppStrings.pending(this), p.amber, p.amberSoft, if (night) p.amber else Color.parseColor("#B36B00")),
            StatusButtonStyle("done", AppStrings.done(this), p.green, p.greenSoft, p.green),
            StatusButtonStyle("issue", AppStrings.issue(this), p.red, p.redSoft, p.red)
        ).forEachIndexed { idx, (key, title, solidColor, softColor, inactiveTextColor) ->
            val active = pinStatus.key == key
            statusRow.addView(MaterialButton(this).apply {
                text = if (active) "✓ $title" else title
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(6), 0, dp(6), 0)
                if (active) {
                    setTextColor(Color.WHITE)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(solidColor)
                    strokeWidth = 0
                } else {
                    setTextColor(inactiveTextColor)
                    backgroundTintList = android.content.res.ColorStateList.valueOf(softColor)
                    strokeWidth = dp(1)
                    strokeColor = android.content.res.ColorStateList.valueOf(
                        Color.argb(if (night) 76 else 64, Color.red(solidColor), Color.green(solidColor), Color.blue(solidColor))
                    )
                }
                alpha = 1f
                cornerRadius = dp(12)
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (idx < 2) marginEnd = dp(8)
                }
                UiAnimations.attachSnappyPress(this)
                setOnClickListener {
                    if (key == "issue") {
                        showFieldQuickSelectSheet(pin, isIssue = true)
                    } else if (key == "done") {
                        showFieldQuickSelectSheet(pin, isIssue = false)
                    } else {
                        savePinStatusUnified(pin, key, stamp)
                        fireStatusPulse(pin.lat, pin.lng, statusColorHex(key))
                        renderMapData()
                        Toast.makeText(this@MainActivity, "Marked $title", Toast.LENGTH_SHORT).show()
                        hideInScreenSheet()
                    }
                }
            })
        }
        sheetContent.addView(statusRow)

        // 5. Full-width Route To Primary Pill CTA
        sheetContent.addView(MaterialButton(this).apply {
            text = AppStrings.routeTo(this@MainActivity)
            textSize = 14.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            maxLines = 1
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            cornerRadius = dp(23)
            DesignSystem.applyPrimaryCtaButton(this, p)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply { bottomMargin = dp(8) }
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { openRoutePreview(pin.lat, pin.lng, pin.name) }
        })

        // 6. Compact 4-Tile Secondary Action Strip: From | Edit | External | Mark Sent
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        fun applyTileVisual(tile: LinearLayout, iconIv: ImageView, labelTv: TextView, active: Boolean) {
            val fill = DesignSystem.buildOpaqueDrawable(
                ctx = this,
                fillColor = if (active) p.accentPrimary else p.surfaceTonal,
                radiusDp = 14f,
                strokeColor = if (active) Color.TRANSPARENT else p.borderSubtle,
                strokeDp = if (active) 0f else 1f
            )
            val rippleColor = Color.parseColor(if (night) "#2AFFFFFF" else "#1A1C1E1B")
            val mask = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
            }
            tile.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(rippleColor),
                fill,
                mask
            )
            val fg = if (active) Color.WHITE else txtPrimary
            iconIv.setColorFilter(fg)
            labelTv.setTextColor(fg)
        }
        fun addCompactActionTile(
            label: String,
            iconRes: Int,
            active: Boolean = false,
            addEndGap: Boolean = true,
            onClick: (tile: LinearLayout, iconIv: ImageView, labelTv: TextView) -> Unit
        ) {
            val tile = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(dp(4), dp(6), dp(4), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    if (addEndGap) marginEnd = dp(6)
                }
            }
            val iconIv = ImageView(this).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { bottomMargin = dp(3) }
            }
            val labelTv = TextView(this).apply {
                text = label
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
            }
            tile.addView(iconIv)
            tile.addView(labelTv)
            applyTileVisual(tile, iconIv, labelTv, active)
            UiAnimations.attachSnappyPress(tile)
            tile.setOnClickListener { onClick(tile, iconIv, labelTv) }
            actionRow.addView(tile)
        }
        addCompactActionTile("From", R.drawable.ic_my_location) { _, _, _ ->
            setRouteOrigin(pin.lat, pin.lng, pin.name)
        }
        addCompactActionTile("Edit", R.drawable.ic_edit) { _, _, _ ->
            showEditPinDialog(pin)
        }
        addCompactActionTile("External", R.drawable.ic_map) { _, _, _ ->
            val label = Uri.encode(pin.name)
            val geoUri = Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}($label)")
            safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, geoUri), "Open in"), "No external map app installed")
        }
        var localSent = DevicePrefs.isPinLocalSent(this, pin.id)
        addCompactActionTile(
            label = if (localSent) "Sent ✓" else "Mark Sent",
            iconRes = R.drawable.ic_check,
            active = localSent,
            addEndGap = false
        ) { tile, iconIv, labelTv ->
            localSent = DevicePrefs.toggleLocalSentPin(this@MainActivity, pin.id)
            labelTv.text = if (localSent) "Sent ✓" else "Mark Sent"
            applyTileVisual(tile, iconIv, labelTv, localSent)
            UiAnimations.animateScalePop(tile, 0.94f, 160L)
            updateSentToggleUi()
            renderMapData()
            Toast.makeText(
                this@MainActivity,
                if (localSent) "Marked sent (local only)" else "Removed sent mark",
                Toast.LENGTH_SHORT
            ).show()
            if (localSent && hideSentPins) hideInScreenSheet()
        }
        sheetContent.addView(actionRow)

        // 7. Expanded Details Section (separated by generous whitespace, zero divider lines)
        if (pin.description.isNotBlank()) {
            sheetContent.addView(DesignSystem.createSectionHeader(this, "Details", p).apply {
                setPadding(dp(2), dp(14), dp(2), dp(8))
            })
            sheetContent.addView(TextView(this).apply {
                text = pin.description
                textSize = 14f
                setLineSpacing(dp(3).toFloat(), 1.05f)
                setTextColor(p.textBody)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    setColor(cardTint)
                    cornerRadius = dp(14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(12) }
            })
        }

        if (pin.isEdited && pin.editedBy.isNotBlank()) {
            val stampTime = if (pin.editedAt > 0L) SimpleDateFormat("dd MMM HH:mm", Locale.US).format(Date(pin.editedAt)) else ""
            val tealText = Color.parseColor(if (night) "#2DD4BF" else "#0F766E")
            val tealBg = Color.parseColor(if (night) "#222DD4BF" else "#180F766E")
            sheetContent.addView(TextView(this).apply {
                text = "✎ Edited by ${pin.editedBy}${if (stampTime.isNotBlank()) " • $stampTime" else ""}"
                textSize = 12.5f
                setTextColor(tealText)
                DesignSystem.applyTabularNumerals(this)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = GradientDrawable().apply {
                    setColor(tealBg)
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
            val workStart = DevicePrefs.getWorkStartMinutes(this)
            val workEnd = DevicePrefs.getWorkEndMinutes(this)
            if (isOwner && histLines.isNotEmpty()) {
                sheetContent.addView(TextView(this).apply {
                    text = "Edit Log (Tap ✕ to delete an edit without deleting pin):"
                    textSize = 12f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(txtSub)
                    setPadding(0, dp(2), 0, dp(4))
                })
                histLines.forEachIndexed { idx, line ->
                    val afterHours = PinEditHistoryAudit.isHistoryLineOutsideWorkHours(line, workStart, workEnd)
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding(dp(10), dp(6), dp(4), dp(6))
                        background = GradientDrawable().apply {
                            cornerRadius = dp(10).toFloat()
                            setColor(if (afterHours) p.redSoft else p.cardSurface)
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(5) }
                    }
                    val textCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    if (afterHours) {
                        textCol.addView(TextView(this).apply {
                            text = "⏰ AFTER WORKING HOURS"
                            textSize = 10f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(p.red)
                            setPadding(0, 0, 0, dp(2))
                        })
                    }
                    textCol.addView(TextView(this).apply {
                        text = line
                        textSize = 11.5f
                        setTextColor(if (afterHours) p.textPrimary else txtSub)
                        DesignSystem.applyTabularNumerals(this)
                    })
                    row.addView(textCol)
                    row.addView(TextView(this).apply {
                        text = "✕"
                        textSize = 14f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(p.red)
                        gravity = Gravity.CENTER
                        setPadding(dp(10), dp(4), dp(8), dp(4))
                        setOnClickListener {
                            showThemedDialog(
                                themedDialogBuilder("Delete this edit?")
                                    .setMessage("$line\n\nOnly this edit will be removed. The pin stays on the map and its status recalculates from the remaining edits.")
                                    .setPositiveButton("Delete Edit") { _, _ ->
                                        val updated = deletePinHistoryEntryUnified(pin, idx)
                                        renderSelectedMarker()
                                        fireStatusPulse(updated.lat, updated.lng, statusColorHex(updated.status))
                                        showPinCard(updated, animateSwap = true)
                                        Toast.makeText(this@MainActivity, "Edit deleted — pin kept", Toast.LENGTH_SHORT).show()
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .create(),
                                destructivePositive = true
                            )
                        }
                    })
                    sheetContent.addView(row)
                }
            } else if (histLines.isNotEmpty()) {
                sheetContent.addView(TextView(this).apply {
                    text = "Edit Log:"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtSub)
                    setPadding(0, dp(2), 0, dp(4))
                })
                histLines.forEach { line ->
                    val afterHours = PinEditHistoryAudit.isHistoryLineOutsideWorkHours(line, workStart, workEnd)
                    sheetContent.addView(TextView(this).apply {
                        text = if (afterHours) "⏰ $line" else line
                        textSize = 11.5f
                        setLineSpacing(dp(2).toFloat(), 1.0f)
                        setTextColor(if (afterHours) p.red else txtSub)
                        DesignSystem.applyTabularNumerals(this)
                        setPadding(0, 0, 0, dp(4))
                    })
                }
            }
        }
        if (isOwner && pin.editHistory.isBlank() && pinStatus != PinStatus.PENDING) {
            sheetContent.addView(MaterialButton(this).apply {
                text = "Reset Status to Pending (Keep Pin)"
                textSize = 13f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setTextColor(p.amber)
                setBackgroundColor(p.amberSoft)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply {
                    bottomMargin = dp(6)
                }
                UiAnimations.attachSnappyPress(this)
                setOnClickListener {
                    showThemedDialog(
                        themedDialogBuilder("Reset status to Pending?")
                            .setMessage("Clears the current status and note while keeping \"${pin.name}\" on the map.")
                            .setPositiveButton("Reset") { _, _ ->
                                val updated = resetPinStatusUnified(pin)
                                renderSelectedMarker()
                                fireStatusPulse(updated.lat, updated.lng, statusColorHex(updated.status))
                                showPinCard(updated, animateSwap = true)
                                Toast.makeText(this@MainActivity, "Status reset to Pending", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .create()
                    )
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
                setTextColor(p.red)
                setBackgroundColor(p.redSoft)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42))
                UiAnimations.attachSnappyPress(this)
                setOnClickListener {
                    val delMsg = if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
                        "This removes the pin from this phone."
                    } else {
                        "This removes the pin for everyone in this folder."
                    }
                    showThemedDialog(
                        themedDialogBuilder("Delete \"${pin.name}\"?")
                            .setMessage(delMsg)
                            .setPositiveButton("Delete") { _, _ ->
                                deletePinUnified(pin)
                                hideInScreenSheet()
                            }
                            .setNegativeButton("Cancel", null)
                            .create(),
                        destructivePositive = true
                    )
                }
            })
        }

        if (animateSwap) {
            UiAnimations.animateContentSwap(sheetContent, forward = swapForward)
        } else {
            UiAnimations.animateSheetContentReveal(sheetContent)
        }

        val peekHeight = if (stackedPins.size > 1) 390 else 340
        showInScreenSheet(peekDp = peekHeight, inPlace = animateSwap)
    }

    // --- Tap-Anywhere Place Card (With Route To & Route From) ---

    private fun fitCleanSubtitle(raw: String, tv: TextView, maxWidthPx: Int): String {
        val clean = raw.trim()
        if (clean.isEmpty() || maxWidthPx <= 0) return clean
        if (tv.paint.measureText(clean) <= maxWidthPx) return clean
        val parts = clean.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            val sb = StringBuilder()
            for (part in parts) {
                val candidate = if (sb.isEmpty()) part else "$sb, $part"
                if (tv.paint.measureText(candidate) <= maxWidthPx) {
                    sb.clear()
                    sb.append(candidate)
                } else {
                    break
                }
            }
            if (sb.isNotEmpty()) return sb.toString()
        }
        return clean
    }

    private fun showTappedPlaceCard(title: String, subtitle: String, lat: Double, lng: Double) {
        hideSoftKeyboard()
        sheetQuickSelectPinId = null
        val p = currentPalette()
        val night = p.isDark
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary

        activeTappedLatLng = LatLng(lat, lng)
        tappedResolvedTitle = title
        tappedResolvedSub = subtitle
        sheetContent.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(8)
            }
        }
        header.addView(
            DesignSystem.createHeaderBadge(this, R.drawable.ic_location_pin_glyph, p, sizeDp = 54).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12)
            }
        )
        val unit = DevicePrefs.getSpeedUnit(this)
        val distText = currentLocation?.let {
            "${formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, lat, lng), unit)} away"
        } ?: ""

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        tappedTitleView = TextView(this).apply {
            text = title
            textSize = 19.5f
            letterSpacing = -0.01f
            setLineSpacing(0f, 1.12f)
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        titleCol.addView(tappedTitleView)
        val tappedSubRow = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                if (childCount == 2 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                    val subTv = getChildAt(0) as? TextView
                    val distChild = getChildAt(1)
                    val subMaxW = if (distChild.visibility == View.VISIBLE) {
                        distChild.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST), heightMeasureSpec)
                        (avail - distChild.measuredWidth).coerceAtLeast(dp(56))
                    } else {
                        avail
                    }
                    if (subTv != null) {
                        subTv.maxWidth = subMaxW
                        val fitted = fitCleanSubtitle(tappedResolvedSub, subTv, subMaxW)
                        if (subTv.text.toString() != fitted) subTv.text = fitted
                    }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(3), 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        tappedSubView = TextView(this).apply {
            text = subtitle
            textSize = 12.5f
            setTextColor(p.accentPrimary)
            setTypeface(null, Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = if (subtitle.isNotBlank()) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        tappedDistView = TextView(this).apply {
            text = if (distText.isNotBlank()) {
                if (subtitle.isNotBlank()) " • $distText" else distText
            } else ""
            textSize = 12.5f
            setTextColor(p.accentPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            DesignSystem.applyTabularNumerals(this)
            visibility = if (distText.isNotBlank()) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        tappedSubRow.addView(tappedSubView)
        tappedSubRow.addView(tappedDistView)
        titleCol.addView(tappedSubRow)
        header.addView(titleCol)
        header.addView(DesignSystem.createSheetCloseButton(this, p) { hideInScreenSheet() })
        sheetContent.addView(header)

        // Full-width Coordinates + Unclipped Copy & Share Utility Row
        val tappedCoordRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
        tappedCoordRow.addView(TextView(this).apply {
            text = "${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lng)}"
            textSize = 13f
            setTextColor(txtSub)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            DesignSystem.applyTabularNumerals(this)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        })
        tappedCoordRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_copy)
            setColorFilter(p.accentPrimary)
            contentDescription = "Copy Coordinates"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(2) }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSnappyPress(this, 0.90f)
            setOnClickListener {
                copyToClipboard("GPS", "${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lng)}")
            }
        })
        tappedCoordRow.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_share)
            setColorFilter(p.iconTint)
            contentDescription = "Share Place"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val msg = makePinShareMessage(tappedResolvedTitle, lat, lng)
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, msg)
                }, "Share TerraSync Place"))
            }
        })
        sheetContent.addView(tappedCoordRow)

        val routeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        routeRow.addView(MaterialButton(this).apply {
            text = AppStrings.routeTo(this@MainActivity)
            textSize = 14f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            cornerRadius = dp(23)
            DesignSystem.applyPrimaryCtaButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { openRoutePreview(lat, lng, tappedResolvedTitle) }
        })
        routeRow.addView(MaterialButton(this).apply {
            text = AppStrings.routeFrom(this@MainActivity)
            textSize = 14f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            cornerRadius = dp(23)
            DesignSystem.applySecondaryTonalButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { setRouteOrigin(lat, lng, tappedResolvedTitle) }
        })
        sheetContent.addView(routeRow)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
        }
        btnRow.addView(MaterialButton(this).apply {
            text = AppStrings.savePin(this@MainActivity)
            textSize = 13.5f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            cornerRadius = dp(21)
            DesignSystem.applySecondaryTonalButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val preName = if (tappedResolvedTitle == "Selected Location" || tappedResolvedTitle == "Shared Location") "" else tappedResolvedTitle
                showAddPinDialog(LatLng(lat, lng), prefillName = preName, prefillDesc = tappedResolvedSub)
            }
        })
        btnRow.addView(MaterialButton(this).apply {
            text = AppStrings.copyGps(this@MainActivity)
            textSize = 13.5f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            cornerRadius = dp(21)
            DesignSystem.applySecondaryTonalButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { copyToClipboard("GPS", "${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lng)}") }
        })
        sheetContent.addView(btnRow)

        UiAnimations.animateSheetContentReveal(sheetContent)
        showInScreenSheet(peekDp = 315, expandToFit = true)
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
                pendingRouteReq = PendingRouteReq(destLat, destLng, destTitle, modeOverride, System.currentTimeMillis())
                locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                return
            }
            if (!ensureLocationServiceOn()) return
            val staleStream = System.currentTimeMillis() - lastLiveLocationFixMs > 10_000L
            startLiveGpsUpdates(forceRestart = staleStream)
            val loc = currentLocation
            if (loc == null || locationAgeMs(loc) > 120_000L) {
                pendingRouteReq = PendingRouteReq(destLat, destLng, destTitle, modeOverride, System.currentTimeMillis())
                syncGpsPollingProfile()
                Toast.makeText(this, "Acquiring fresh GPS fix — route will build automatically...", Toast.LENGTH_SHORT).show()
                return
            }
            pendingRouteReq = null
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
                renderRouteOverlay()

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

    private fun travelModeIconRes(mode: TravelMode): Int = when (mode) {
        TravelMode.CAR -> R.drawable.ic_mode_car
        TravelMode.WALKING -> R.drawable.ic_mode_walk
        TravelMode.BICYCLE -> R.drawable.ic_mode_bike
    }

    private fun showRoutePreviewCard(route: NavRoute, startLabel: String, destLat: Double, destLng: Double, destTitle: String) {
        sheetQuickSelectPinId = null
        val p = currentPalette()
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary

        sheetContent.removeAllViews()

        val etaMin = (route.totalDurationSeconds / 60.0).toInt().coerceAtLeast(1)
        val distStr = formatDistanceShort(route.totalDistanceMeters, DevicePrefs.getSpeedUnit(this))

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
            }
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        val metaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val etaBadge = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(11), dp(5), dp(12), dp(5))
            background = GradientDrawable().apply {
                setColor(p.accentPrimary)
                cornerRadius = dp(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(10) }
        }
        etaBadge.addView(ImageView(this).apply {
            setImageResource(travelModeIconRes(route.mode))
            setColorFilter(Color.WHITE)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = LinearLayout.LayoutParams(dp(17), dp(17)).apply { marginEnd = dp(6) }
        })
        etaBadge.addView(TextView(this).apply {
            text = "$etaMin min"
            textSize = 15f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            DesignSystem.applyTabularNumerals(this)
        })
        metaRow.addView(etaBadge)
        metaRow.addView(TextView(this).apply {
            text = distStr
            textSize = 15.5f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            DesignSystem.applyTabularNumerals(this)
        })
        val isOfflineRoute = route.engineLabel.contains("Offline", true)
        val engTextColor = if (isOfflineRoute) p.amber else p.accentPrimary
        val engBgColor = if (isOfflineRoute) p.amberSoft else p.accentPrimarySoft
        metaRow.addView(TextView(this).apply {
            text = if (isOfflineRoute) "● Offline Roads" else "● Online"
            textSize = 11f
            setTextColor(engTextColor)
            setTypeface(null, Typeface.BOLD)
            setPadding(dp(9), dp(3), dp(9), dp(3))
            background = GradientDrawable().apply {
                setColor(engBgColor)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        })
        titleCol.addView(metaRow)
        titleCol.addView(TextView(this).apply {
            text = "$startLabel → $destTitle"
            textSize = 13f
            setTextColor(txtSub)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(6), 0, 0)
        })
        header.addView(titleCol)

        header.addView(DesignSystem.createSheetCloseButton(this, p) {
            routeRequestSeq++
            previewRoute = null
            renderRouteOverlay()
            hideInScreenSheet()
        })
        sheetContent.addView(header)

        // Travel Mode Switcher (Car / Walk / Bicycle) — warm espresso/stone pills with crisp outlined vectors
        val modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
                bottomMargin = dp(12)
            }
        }
        TravelMode.entries.forEachIndexed { idx, mode ->
            val active = mode == route.mode
            val fgColor = if (active) p.onActionDark else p.textPrimary
            val bgColor = if (active) p.actionDark else p.surfaceTonal
            modeRow.addView(MaterialButton(this).apply {
                text = mode.label
                textSize = 13f
                setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                isAllCaps = false
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                elevation = 0f
                stateListAnimator = null
                setPadding(dp(8), 0, dp(10), 0)
                setIconResource(travelModeIconRes(mode))
                iconSize = dp(18)
                iconPadding = dp(6)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                iconTint = android.content.res.ColorStateList.valueOf(fgColor)
                setTextColor(fgColor)
                backgroundTintList = android.content.res.ColorStateList.valueOf(bgColor)
                strokeWidth = if (active) 0 else dp(1)
                strokeColor = android.content.res.ColorStateList.valueOf(if (active) Color.TRANSPARENT else p.borderSubtle)
                cornerRadius = dp(22)
                layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                    if (idx < TravelMode.entries.lastIndex) marginEnd = dp(8)
                }
                UiAnimations.attachSnappyPress(this)
                setOnClickListener {
                    if (mode != route.mode) openRoutePreview(destLat, destLng, destTitle, mode)
                }
            })
        }
        sheetContent.addView(modeRow)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }
        btnRow.addView(MaterialButton(this).apply {
            text = if (routeOriginLatLng != null) "Navigate from GPS" else "Start Navigation"
            textSize = 14f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            cornerRadius = dp(24)
            DesignSystem.applyPrimaryCtaButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 1.4f).apply { marginEnd = dp(8) }
            UiAnimations.attachSnappyPress(this)
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
            textSize = 14f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            cornerRadius = dp(24)
            DesignSystem.applySecondaryTonalButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(48), 0.8f)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val label = Uri.encode(destTitle)
                val geoUri = Uri.parse("geo:$destLat,$destLng?q=$destLat,$destLng($label)")
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, geoUri), "Open in"), "No external map app installed")
            }
        })
        sheetContent.addView(btnRow)

        UiAnimations.animateSheetContentReveal(sheetContent)
        showInScreenSheet(peekDp = 310, inPlace = true, expandToFit = true)
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
            .padding(0.0, dp(165).toDouble(), 0.0, dp(75).toDouble())
            .build()
        m.animateCamera(CameraUpdateFactory.newCameraPosition(cam), durationMs)
    }

    private fun formatNavInstructionBidi(raw: String): String {
        val markers = arrayOf(" onto ", " towards ", " toward ", " on ", " at ")
        for (marker in markers) {
            val idx = raw.indexOf(marker, ignoreCase = true)
            if (idx >= 0) {
                val splitPos = idx + marker.length
                val head = raw.substring(0, splitPos)
                val tail = raw.substring(splitPos).trim()
                if (tail.isNotEmpty()) return "$head\u2068$tail\u2069"
            }
        }
        return raw
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
        syncGpsPollingProfile(forceRestart = true)
        updateSpeedBadge(loc)
        renderRouteOverlay()
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

        val unit = cachedSpeedUnit
        val nextInstr = if (isRerouting) "Recalculating route..." else step.instruction
        UiAnimations.animateCrossfadeText(tvNavArrow, step.arrow, slideDp = 4f)
        tvNavStepDist.text = if (isRerouting) "Rerouting" else formatDistanceShort(distToStep, unit)
        UiAnimations.animateCrossfadeText(tvNavInstruction, formatNavInstructionBidi(nextInstr), slideDp = 4f)
        tvNavEta.text = "$etaMin min"
        val modeTag = if (route.engineLabel.contains("Offline", ignoreCase = true)) "Offline" else "Online"
        tvNavMeta.text = "${formatDistanceShort(remainingRoadMeters, unit)} • $modeTag"

        val speakDist = if (loc.hasSpeed() && loc.speed > 16f) 450.0 else 280.0
        if (!isRerouting && navCurrentStepIdx != lastSpokenStepIdx && distToStep < speakDist) {
            lastSpokenStepIdx = navCurrentStepIdx
            speakPrompt("In ${formatDistanceShort(distToStep, unit)}, ${step.instruction}")
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
            tvNavStepDist.text = "Rerouting"
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
                        tvNavInstruction.text = formatNavInstructionBidi(fallbackStep?.instruction ?: "Continue to destination")
                        return@runOnUiThread
                    }
                    activeNavRoute = newRoute
                    activeNavTrimmedGeom = newRoute.geometry
                    navLastSegIdx = 0
                    navCurrentStepIdx = if (newRoute.steps.size > 1) 1 else 0
                    lastSpokenStepIdx = -1
                    renderRouteOverlay()
                    currentLocation?.let { latest ->
                        updateNavigationProgress(latest, newRoute, dest)
                    }
                }
            }
        }
    }

    private fun applyNavigationChrome(inNav: Boolean) {
        if (inNav) {
            if (isFullScreenMapMode) setFullScreenMapMode(false, animate = false)
            if (isLassoMode) toggleLassoMode(false)
            if (isRulerMode) toggleRulerMode(false)
            UiAnimations.hideSlideTop(topCluster)
            UiAnimations.showSlideTop(navBanner)
            bottomNavDock.visibility = View.GONE
            btnRuler.visibility = View.GONE
            btnLasso.visibility = View.GONE
            UiAnimations.showSlideBottom(navBottomBar)
            UiAnimations.fadeShow(bottomDock, 180L)
        } else {
            UiAnimations.hideSlideTop(navBanner)
            navBottomBar.visibility = View.GONE
            UiAnimations.showSlideTop(topCluster)
            btnRuler.visibility = View.VISIBLE
            applyExtraMapToolsVisibility()
            applyZoomButtonsVisibility()
            if (!isRulerMode && !isLassoMode && sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                UiAnimations.showSlideBottom(bottomNavDock)
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
        syncGpsPollingProfile()
        updateSpeedBadge(currentLocation)
        renderRouteOverlay()
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

        showThemedDialog(
            themedDialogBuilder("Location is turned off")
                .setMessage("Turn on your phone's Location / GPS to find your position, measure speed, and start navigation.")
                .setPositiveButton("Turn On") { _, _ ->
                    runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                }
                .setNegativeButton("Cancel", null)
                .create()
        )
        return false
    }

    private fun locationAgeMs(loc: Location): Long {
        return if (loc.elapsedRealtimeNanos > 0L) {
            ((android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000L).coerceAtLeast(0L)
        } else {
            (System.currentTimeMillis() - loc.time).coerceAtLeast(0L)
        }
    }

    private fun desiredGpsProfile(): GpsPollProfile {
        if (activeNavRoute != null) return GpsPollProfile.NAVIGATION
        if (!isActivityResumed) return GpsPollProfile.OFF
        val overlayVisible = ::pageOverlayContainer.isInitialized && pageOverlayContainer.visibility == View.VISIBLE
        if (overlayVisible) return GpsPollProfile.OFF
        if (isFollowingGps || pendingRouteReq != null) return GpsPollProfile.FOLLOW_LOCATING
        return GpsPollProfile.ACTIVE_MAP
    }

    @SuppressLint("MissingPermission")
    private fun syncGpsPollingProfile(forceRestart: Boolean = false) {
        val desired = desiredGpsProfile()
        if (desired == GpsPollProfile.OFF) {
            stopLiveGpsUpdates()
            return
        }
        if (!hasLocationPermission()) return
        if (gpsTrackingActive && activeGpsProfile == desired && !forceRestart) return

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val specs = when (desired) {
            GpsPollProfile.NAVIGATION -> buildList {
                add(Triple(LocationManager.GPS_PROVIDER, 600L, 0f))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    add(Triple(LocationManager.FUSED_PROVIDER, 1200L, 0f))
                }
            }
            GpsPollProfile.FOLLOW_LOCATING -> buildList {
                add(Triple(LocationManager.GPS_PROVIDER, 1000L, 0f))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    add(Triple(LocationManager.FUSED_PROVIDER, 1500L, 0f))
                }
                add(Triple(LocationManager.NETWORK_PROVIDER, 5000L, 5f))
            }
            GpsPollProfile.ACTIVE_MAP -> buildList {
                add(Triple(LocationManager.GPS_PROVIDER, 2500L, 2f))
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    add(Triple(LocationManager.FUSED_PROVIDER, 3000L, 3f))
                }
                add(Triple(LocationManager.NETWORK_PROVIDER, 15_000L, 15f))
            }
            GpsPollProfile.OFF -> emptyList()
        }

        val wasTracking = gpsTrackingActive
        if (!wasTracking) {
            val bestLast = specs
                .mapNotNull { (p, _, _) -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
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
        } else {
            runCatching { lm.removeUpdates(gpsListener) }
        }

        var registeredAny = false
        specs.forEach { (provider, minTimeMs, minDistM) ->
            runCatching {
                if (lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, minTimeMs, minDistM, gpsListener)
                    registeredAny = true
                }
            }
        }
        gpsTrackingActive = registeredAny
        activeGpsProfile = if (registeredAny) desired else GpsPollProfile.OFF
    }

    @SuppressLint("MissingPermission")
    private fun startLiveGpsUpdates(forceRestart: Boolean = false) {
        syncGpsPollingProfile(forceRestart)
    }

    private fun stopLiveGpsUpdates() {
        speedSampleState = SpeedFallbackEstimator.SpeedSampleState()
        activeGpsProfile = GpsPollProfile.OFF
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
            UiAnimations.animateSpeedBadgeVisibility(speedBadge, false)
            return
        }
        val unit = cachedSpeedUnit
        val accurateFix = loc != null &&
            (loc.provider == LocationManager.GPS_PROVIDER || !loc.hasAccuracy() || loc.accuracy <= 35f)
        val rawMps = when {
            accurateFix && loc!!.hasSpeed() && loc.speed > 0.1f -> loc.speed
            accurateFix && speedSampleState.derivedSpeedMps > 0f -> speedSampleState.derivedSpeedMps
            else -> 0f
        }
        val rawKmh = (rawMps * 3.6f).roundToInt().coerceAtLeast(0)
        val kmh = if (rawKmh < 2) 0 else rawKmh
        val displayVal = if (unit == SpeedUnit.IMPERIAL) (kmh * 0.621371f).roundToInt() else kmh
        val shouldShow = activeNavRoute != null || kmh >= 4
        if (shouldShow) {
            tvSpeedValue.text = "$displayVal"
            tvSpeedUnit.text = unit.speedSuffix
        }
        UiAnimations.animateSpeedBadgeVisibility(speedBadge, shouldShow)
    }

    private fun onNewLocation(loc: Location) {
        if (!isBetterLocation(loc, currentLocation)) return
        lastLiveLocationFixMs = System.currentTimeMillis()
        speedSampleState = SpeedFallbackEstimator.update(
            prev = speedSampleState,
            lat = loc.latitude,
            lng = loc.longitude,
            provider = loc.provider.orEmpty(),
            hasSpeed = loc.hasSpeed(),
            speedMps = loc.speed,
            accurate = !loc.hasAccuracy() || loc.accuracy <= 35f,
            elapsedMs = android.os.SystemClock.elapsedRealtime()
        )
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

        // Automatically fulfill a queued Route To request once a fresh GPS fix arrives
        val queuedRoute = pendingRouteReq
        if (queuedRoute != null && locationAgeMs(loc) <= 120_000L) {
            pendingRouteReq = null
            if (System.currentTimeMillis() - queuedRoute.requestedAtMs <= 20_000L && activeNavRoute == null) {
                openRoutePreview(queuedRoute.destLat, queuedRoute.destLng, queuedRoute.destTitle, queuedRoute.mode)
            }
        }

        gpsAnim?.cancel()
        val movedMeters = if (prevLoc == null) Double.MAX_VALUE else {
            RoutingEngine.haversineMeters(prevLat, prevLng, loc.latitude, loc.longitude)
        }
        val shouldAnimateGps = isActivityResumed &&
            !UiAnimations.isReducedMotion(this) &&
            (movedMeters >= 0.35 || kotlin.math.abs(deltaBearing) >= 1.5f)
        if (shouldAnimateGps) {
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
            if (prevLoc == null || movedMeters >= 0.1 || kotlin.math.abs(deltaBearing) >= 0.5f) {
                updateGpsLayerFeature(displayedLat, displayedLng, displayedBearing)
            }
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
        val p = currentPalette()
        if (isFollowingGps) {
            DesignSystem.applyFloatingControl(this, btnLocate, p, 33f, 10f, activeFillColor = p.accentPrimary)
            btnLocate.setColorFilter(Color.WHITE)
        } else if (p.isDark) {
            DesignSystem.applyFloatingControl(
                this,
                btnLocate,
                p,
                33f,
                10f,
                activeFillColor = Color.parseColor("#242926")
            )
            btnLocate.setColorFilter(p.accentPrimary)
        } else {
            DesignSystem.applyFloatingControl(this, btnLocate, p, 33f, 10f, activeFillColor = p.actionDark)
            btnLocate.setColorFilter(p.onActionDark)
        }
    }

    private fun updateFilterIconTint() {
        val p = currentPalette()
        val color = when {
            visibleStatuses.containsAll(ALL_STATUSES) && !hideSentPins -> p.iconTint
            visibleStatuses == setOf("pending") && !hideSentPins -> p.amber
            visibleStatuses == setOf("issue") && !hideSentPins -> p.red
            visibleStatuses == setOf("done") && !hideSentPins -> p.green
            else -> p.accent
        }
        btnStatusFilter.setColorFilter(color)
    }

    private fun updateScaleBar() {
        if (::scaleBarContainer.isInitialized) {
            if (!cachedScaleBarEnabled) {
                if (scaleBarContainer.visibility != View.GONE) scaleBarContainer.visibility = View.GONE
                return
            } else if (scaleBarContainer.visibility != View.VISIBLE) {
                scaleBarContainer.visibility = View.VISIBLE
            }
        }
        val m = map ?: return
        val center = m.cameraPosition.target ?: return
        val mpp = m.projection.getMetersPerPixelAtLatitude(center.latitude)
        if (mpp <= 0.0 || !mpp.isFinite()) return
        val density = resources.displayMetrics.density
        val maxPx = 110f * density
        val maxMeters = mpp * maxPx

        val label: String
        val widthPx: Int
        if (cachedSpeedUnit == SpeedUnit.IMPERIAL) {
            val maxFt = maxMeters * 3.28084
            var chosenFt = SCALE_STEPS_IMPERIAL[0]
            for (s in SCALE_STEPS_IMPERIAL) {
                if (s <= maxFt) chosenFt = s else break
            }
            val chosenMeters = chosenFt / 3.28084
            widthPx = (chosenMeters / mpp).toInt().coerceIn(dp(32), dp(120))
            label = when {
                chosenFt >= 5280.0 -> "${(chosenFt / 5280.0).toInt()} mi"
                chosenFt == 2640.0 -> "0.5 mi"
                else -> "${chosenFt.toInt()} ft"
            }
        } else {
            var chosen = SCALE_STEPS_METRIC[0]
            for (s in SCALE_STEPS_METRIC) {
                if (s <= maxMeters) chosen = s else break
            }
            widthPx = (chosen / mpp).toInt().coerceIn(dp(32), dp(120))
            label = if (chosen >= 1000.0) "${(chosen / 1000.0).toInt()} km" else "${chosen.toInt()} m"
        }

        if (label != lastScaleLabel) {
            lastScaleLabel = label
            tvScaleLabel.text = label
        }
        if (kotlin.math.abs(widthPx - lastScaleWidthPx) >= 2) {
            lastScaleWidthPx = widthPx
            scaleBarLine.setBarWidthPx(widthPx)
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
        liveEditHistoryRefresh = null
        hideSoftKeyboard()
        pageBackStack.clear()
        pageOverlayContainer.setBackgroundColor(currentPalette().pageBackdrop)
        pageOverlayContainer.removeAllViews()
        pageOverlayContainer.addView(builder())
        UiAnimations.animatePageEnter(pageOverlayContainer)
        syncGpsPollingProfile()
        updateSystemBarContrast(forFullPage = true)
    }

    private fun pushSubPage(onBack: () -> Unit, builder: () -> View) {
        pageOverlayContainer.animate().cancel()
        activePageCleanup?.invoke()
        activePageCleanup = null
        liveBookmarksRefresh = null
        livePinListRefresh = null
        liveEditHistoryRefresh = null
        hideSoftKeyboard()
        pageBackStack.add(onBack)
        pageOverlayContainer.setBackgroundColor(currentPalette().pageBackdrop)
        val v = builder()
        UiAnimations.transitionSubPagePush(pageOverlayContainer, v, AppStrings.isRtl(this))
        updateSystemBarContrast(forFullPage = true)
    }

    private fun popSubPage(builder: () -> View) {
        pageOverlayContainer.animate().cancel()
        activePageCleanup?.invoke()
        activePageCleanup = null
        liveBookmarksRefresh = null
        livePinListRefresh = null
        liveEditHistoryRefresh = null
        offlineMaps.setStateListener(null)
        hideSoftKeyboard()
        pageOverlayContainer.setBackgroundColor(currentPalette().pageBackdrop)
        val backView = builder()
        UiAnimations.transitionSubPagePop(pageOverlayContainer, backView, AppStrings.isRtl(this))
        updateSystemBarContrast(forFullPage = true)
    }

    private fun closeFullPage() {
        pageOverlayContainer.animate().cancel()
        activePageCleanup?.invoke()
        activePageCleanup = null
        webSearchSeq++
        liveBookmarksRefresh = null
        livePinListRefresh = null
        liveEditHistoryRefresh = null
        offlineMaps.setStateListener(null)
        hideSoftKeyboard()
        pageBackStack.clear()
        // Reset nav bar pills back to neutral white whenever returning to the map
        activeOpenNavTab = -1
        syncNavDockPills()
        updateSystemBarContrast(forFullPage = false)
        if (pageOverlayContainer.visibility != View.VISIBLE) {
            pageOverlayContainer.removeAllViews()
            syncGpsPollingProfile()
            return
        }
        UiAnimations.animatePageExit(pageOverlayContainer) {
            pageOverlayContainer.removeAllViews()
            syncGpsPollingProfile()
        }
    }

    private fun makePageFrame(title: String, onBack: () -> Unit, rightAction: View? = null): Pair<LinearLayout, LinearLayout> {
        val p = currentPalette()
        val night = p.isDark

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (AppStrings.isRtl(this@MainActivity)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(p.pageBackdrop)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(sideInsetLeft, statusBarInsetTop, sideInsetRight, maxOf(navBarInsetBottom, imeInsetBottom))
        }

        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(8), dp(14), dp(8))
        }
        appBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(p.textPrimary)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { onBack() }
        })
        appBar.addView(TextView(this).apply {
            text = title
            textSize = 20f
            letterSpacing = -0.01f
            setTextColor(p.textPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
                marginEnd = dp(8)
            }
        })
        if (rightAction != null) appBar.addView(rightAction)
        outer.addView(appBar)

        outer.addView(DesignSystem.createHairlineDivider(this, p))

        val scroll = ScrollView(this).apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(28))
        }
        scroll.addView(body)
        outer.addView(scroll)
        return outer to body
    }

    // --- Page 2: Unified Bookmarks (KML Folders + Cross-Folder Search + Bottom Toggles) ---

    private fun buildBookmarksAndTracksPage(): View {
        val p = currentPalette()
        val night = p.isDark
        val bg = p.pageBackdrop
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary
        val dividerColor = p.divider

        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (AppStrings.isRtl(this@MainActivity)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(bg)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setPadding(sideInsetLeft, statusBarInsetTop, sideInsetRight, maxOf(navBarInsetBottom, imeInsetBottom))
        }

        val appBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(8), dp(10), dp(6))
        }
        appBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(txtPrimary)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { closeFullPage() }
        })
        appBar.addView(TextView(this).apply {
            text = AppStrings.bookmarks(this@MainActivity)
            textSize = 20f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        })
        appBar.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_plus)
            setColorFilter(p.accent)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                DesignSystem.showActionBottomSheet(
                    ctx = this@MainActivity,
                    palette = p,
                    title = "Bookmarks Actions",
                    subtitle = "${layers.size} folders • ${pins.size} bookmarks",
                    actions = listOf(
                        SheetActionItem(
                            title = "Import KML / KMZ Folder",
                            subtitle = "Load field map file from this phone",
                            iconRes = R.drawable.ic_import_file
                        ) { closeFullPage(); kmlPicker.launch(arrayOf("*/*")) },
                        SheetActionItem(
                            title = "Join Folder by Share Code",
                            subtitle = "Sync a live shared team folder",
                            iconRes = R.drawable.ic_cloud_sync
                        ) { showJoinCodeDialog() },
                        SheetActionItem(
                            title = "Combine Folders into One",
                            subtitle = "Merge multiple folders with smart dedup",
                            iconRes = R.drawable.ic_layers
                        ) { showCombineFoldersDialog() },
                        SheetActionItem(
                            title = "Daily Report (Visible Folders)",
                            subtitle = "Generate WhatsApp / Telegram field summary",
                            iconRes = R.drawable.ic_report
                        ) { showVisibleFoldersDailyReportDialog() },
                        SheetActionItem(
                            title = "Nearest Pending Pin",
                            subtitle = "Jump to closest unvisited location",
                            iconRes = R.drawable.ic_my_location
                        ) { jumpToNearestPendingPin() }
                    )
                )
            }
        })
        outer.addView(appBar)

        val searchWrap = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                topMargin = dp(4)
                bottomMargin = dp(10)
            }
            background = DesignSystem.buildOpaqueDrawable(
                ctx = this@MainActivity,
                fillColor = p.surfaceTonal,
                radiusDp = 24f,
                strokeColor = p.borderSubtle,
                strokeDp = 1f
            )
            clipToOutline = true
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
            setPadding(dp(18), dp(10), dp(18), dp(16))
        }

        var suppressAllSwitch = false
        val swShowAll = MaterialSwitch(this).apply {
            DesignSystem.styleMaterialSwitch(this, p)
        }

        val rowShowAll = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(50)
            setPadding(dp(2), dp(4), dp(2), dp(4))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener { swShowAll.toggle() }
        }
        val showAllCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        showAllCol.addView(TextView(this).apply {
            text = AppStrings.showAllBookmarks(this@MainActivity)
            textSize = 14.5f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
        })
        val tvTotalBookmarksCount = TextView(this).apply {
            text = "${pins.size} bookmarks"
            textSize = 12.5f
            setTextColor(txtSub)
            DesignSystem.applyTabularNumerals(this)
        }
        showAllCol.addView(tvTotalBookmarksCount)
        rowShowAll.addView(showAllCol)
        rowShowAll.addView(swShowAll)
        footer.addView(rowShowAll)

        val swShowTitles = MaterialSwitch(this).apply {
            isChecked = DevicePrefs.isShowBookmarkTitles(this@MainActivity)
            DesignSystem.styleMaterialSwitch(this, p)
            setOnCheckedChangeListener { _, checked ->
                DevicePrefs.setShowBookmarkTitles(this@MainActivity, checked)
                renderMapData()
            }
        }
        val rowShowTitles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(50)
            setPadding(dp(2), dp(2), dp(2), dp(2))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener { swShowTitles.toggle() }
        }
        rowShowTitles.addView(TextView(this).apply {
            text = AppStrings.showBookmarkTitles(this@MainActivity)
            textSize = 14.5f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        rowShowTitles.addView(swShowTitles)
        footer.addView(rowShowTitles)
        outer.addView(footer)

        fun syncMasterVisibilitySwitch() {
            val hiddenIds = DevicePrefs.getHiddenLayerIds(this)
            val allVisible = layers.isNotEmpty() && layers.all { it.id !in hiddenIds }
            suppressAllSwitch = true
            swShowAll.isChecked = allVisible
            suppressAllSwitch = false
        }

        var bookmarkSearchSeq = 0

        fun renderFoldersOrSearch(query: String) {
            val mySeq = ++bookmarkSearchSeq
            val ver = dataVersion
            val pinsSnap = pins
            val folderNameById = layers.associate { it.id to it.name }
            tvTotalBookmarksCount.text = "${pinsSnap.size} bookmarks"
            val trimmed = query.trim()

            if (trimmed.isNotEmpty()) {
                val tokens = trimmed.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
                val hasDigits = trimmed.any { it.isDigit() }
                val loc = currentLocation
                val unit = cachedSpeedUnit
                RoutingEngine.ioExecutor.execute {
                    val index = getOrBuildSearchIndex(ver, pinsSnap, folderNameById)
                    val matches = filterSearchEntries(index, tokens, hasDigits, loc, unit)
                    runOnUiThread {
                        if (isFinishing || isDestroyed || mySeq != bookmarkSearchSeq) return@runOnUiThread
                        body.removeAllViews()
                        body.addView(TextView(this@MainActivity).apply {
                            text = "${matches.size} match${if (matches.size == 1) "" else "es"} across all folders"
                            textSize = 13f
                            setTextColor(txtSub)
                            setTypeface(null, Typeface.BOLD)
                            setPadding(dp(4), dp(4), dp(4), dp(8))
                        })

                        if (matches.isEmpty()) {
                            body.addView(TextView(this@MainActivity).apply {
                                text = "No bookmarks matched \"$trimmed\"."
                                textSize = 14f
                                setTextColor(txtSub)
                                setPadding(dp(4), dp(12), dp(4), dp(12))
                            })
                            return@runOnUiThread
                        }

                        appendPaginatedSearchCard(body, p, matches, showFolderInSub = true, pageSize = 60)
                    }
                }
                return
            }

            body.removeAllViews()

            val bookmarkedIds = DevicePrefs.getBookmarks(this)
            val bookmarkedPins = pinsSnap.filter { it.id in bookmarkedIds }

            body.addView(TextView(this).apply {
                text = "${layers.size} folder${if (layers.size == 1) "" else "s"}"
                textSize = 12.5f
                letterSpacing = 0.04f
                setTextColor(txtSub)
                setTypeface(null, Typeface.BOLD)
                DesignSystem.applyTabularNumerals(this)
                setPadding(dp(4), dp(4), dp(4), dp(8))
            })

            if (layers.isEmpty() && bookmarkedPins.isEmpty()) {
                val emptyCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 16, paddingVDp = 18)
                emptyCard.addView(TextView(this).apply {
                    text = "No bookmark folders yet"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                })
                emptyCard.addView(TextView(this).apply {
                    text = "Import a KML / KMZ field map from your phone or join a live team folder using a 6-character share code."
                    textSize = 13.5f
                    setLineSpacing(dp(2).toFloat(), 1.08f)
                    setTextColor(txtSub)
                    setPadding(0, dp(6), 0, dp(14))
                })
                val emptyBtnRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    isBaselineAligned = false
                    gravity = Gravity.CENTER_VERTICAL
                }
                emptyBtnRow.addView(MaterialButton(this).apply {
                    text = "Import KML/KMZ"
                    textSize = 13f
                    isAllCaps = false
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(dp(10), 0, dp(10), 0)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(p.green)
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(8) }
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        closeFullPage()
                        kmlPicker.launch(arrayOf("*/*"))
                    }
                })
                emptyBtnRow.addView(MaterialButton(this).apply {
                    text = "Join by Code"
                    textSize = 13f
                    isAllCaps = false
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(dp(10), 0, dp(10), 0)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(p.accent)
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener { showJoinCodeDialog() }
                })
                emptyCard.addView(emptyBtnRow)
                body.addView(emptyCard)
                return
            }

            val listCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                DesignSystem.applyGroupedCardSurface(this@MainActivity, this, p, radiusDp = 16f)
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
                        pushSubPage({ popSubPage(::buildBookmarksAndTracksPage) }) {
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

            val hiddenLayerIds = DevicePrefs.getHiddenLayerIds(this)
            val pinnedLayerIds = DevicePrefs.getPinnedLayerIds(this)
            val orderedLayers = layers.sortedByDescending { it.id in pinnedLayerIds }
            orderedLayers.forEach { layer ->
                addDividerIfNeeded()
                val layerPins = pinsByLayerId[layer.id].orEmpty()
                val layerTracks = tracksByLayerId[layer.id].orEmpty()
                val visible = layer.id !in hiddenLayerIds
                val isPinned = layer.id in pinnedLayerIds
                val isLocal = LocalFolderStore.isLocalLayerId(layer.id)
                val isCreator = isLayerOwner(layer)

                var doneCnt = 0
                var issueCnt = 0
                var pendingCnt = 0
                for (item in layerPins) {
                    when (PinStatus.fromRaw(item.status)) {
                        PinStatus.DONE -> doneCnt++
                        PinStatus.ISSUE -> issueCnt++
                        else -> pendingCnt++
                    }
                }

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(8), dp(12), dp(6), dp(12))
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                }

                row.addView(ImageButton(this).apply {
                    setImageResource(if (visible) R.drawable.ic_eye_on else R.drawable.ic_eye_off)
                    setColorFilter(if (visible) p.accent else p.iconMuted)
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        val next = !DevicePrefs.isLayerVisible(this@MainActivity, layer.id)
                        DevicePrefs.setLayerVisible(this@MainActivity, layer.id, next)
                        UiAnimations.animateIconSwap(
                            this,
                            if (next) R.drawable.ic_eye_on else R.drawable.ic_eye_off,
                            newTint = if (next) p.accent else p.iconMuted
                        )
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
                        pushSubPage({ popSubPage(::buildBookmarksAndTracksPage) }) {
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
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (isLocal) {
                    titleRow.addView(TextView(this).apply {
                        text = "LOCAL"
                        textSize = 10f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(p.accentPrimary)
                        setPadding(dp(7), dp(2), dp(7), dp(2))
                        background = GradientDrawable().apply {
                            setColor(p.accentPrimarySoft)
                            cornerRadius = dp(8).toFloat()
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
                        setTextColor(p.amber)
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        background = GradientDrawable().apply {
                            setColor(p.amberSoft)
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
                    val sb = android.text.SpannableStringBuilder("${layerPins.size} bookmarks$trackStr   ")
                    fun appendColoredStat(token: String, color: Int) {
                        val start = sb.length
                        sb.append(token)
                        sb.setSpan(android.text.style.ForegroundColorSpan(color), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(android.text.style.StyleSpan(Typeface.BOLD), start, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    appendColoredStat("✓$doneCnt", p.green)
                    sb.append("  ")
                    appendColoredStat("•$pendingCnt", p.amber)
                    sb.append("  ")
                    appendColoredStat("!$issueCnt", p.red)
                    text = sb
                    textSize = 12.5f
                    setTextColor(txtSub)
                    DesignSystem.applyTabularNumerals(this)
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
                        setBackgroundColor(p.green)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, doneCnt.toFloat())
                    })
                    if (pendingCnt > 0) bar.addView(View(this).apply {
                        setBackgroundColor(p.amber)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, pendingCnt.toFloat())
                    })
                    if (issueCnt > 0) bar.addView(View(this).apply {
                        setBackgroundColor(p.red)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, issueCnt.toFloat())
                    })
                    infoCol.addView(bar)
                }
                row.addView(infoCol)

                row.addView(ImageButton(this).apply {
                    setImageResource(R.drawable.ic_more_vert)
                    setColorFilter(p.iconMuted)
                    layoutParams = LinearLayout.LayoutParams(dp(44), dp(48))
                    setPadding(dp(10), dp(12), dp(10), dp(12))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    setOnClickListener {
                        val folderSubtitle = buildString {
                            append("${layerPins.size} bookmark${if (layerPins.size == 1) "" else "s"}")
                            append(" • $doneCnt Done • $pendingCnt Pending • $issueCnt Issue")
                        }
                        val folderActions = buildList {
                            add(
                                SheetActionItem(
                                    title = if (isPinned) "Unpin Folder (Unlock)" else "Pin & Protect Folder",
                                    subtitle = if (isPinned) "Currently protected against accidental deletion" else null,
                                    iconRes = R.drawable.ic_pin_lock,
                                    checked = isPinned
                                ) {
                                    val nowPinned = DevicePrefs.toggleLayerPinned(this@MainActivity, layer.id)
                                    Toast.makeText(
                                        this@MainActivity,
                                        if (nowPinned) "Pinned & protected \"${layer.name}\"" else "Unpinned \"${layer.name}\"",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    renderFoldersOrSearch(etFolderSearch.text?.toString() ?: "")
                                }
                            )
                            if (isCreator) {
                                add(
                                    SheetActionItem(
                                        title = "Rename Folder",
                                        iconRes = R.drawable.ic_edit
                                    ) { showRenameFolderDialog(layer) }
                                )
                            }
                            if (isLocal) {
                                add(
                                    SheetActionItem(
                                        title = "Delete Local Folder",
                                        subtitle = if (isPinned) "Unpin folder first to delete" else "Removes folder from this phone",
                                        iconRes = R.drawable.ic_trash,
                                        destructive = true
                                    ) {
                                        if (isPinned) {
                                            Toast.makeText(this@MainActivity, "Folder is pinned. Unpin it first to delete.", Toast.LENGTH_LONG).show()
                                        } else {
                                            val freshCount = pinsByLayerId[layer.id]?.size ?: 0
                                            showDeleteLocalDialog(layer, freshCount)
                                        }
                                    }
                                )
                            } else {
                                add(
                                    SheetActionItem(
                                        title = "Leave Folder (This Phone)",
                                        subtitle = if (isPinned) "Unpin folder first to leave" else null,
                                        iconRes = R.drawable.ic_close,
                                        destructive = true
                                    ) {
                                        if (isPinned) {
                                            Toast.makeText(this@MainActivity, "Folder is pinned. Unpin it first to leave.", Toast.LENGTH_LONG).show()
                                        } else {
                                            DevicePrefs.removeJoinedLayerId(this@MainActivity, layer.id)
                                            refreshJoinedSync()
                                            Toast.makeText(this@MainActivity, "Left \"${layer.name}\"", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                                if (isCreator) {
                                    add(
                                        SheetActionItem(
                                            title = "Delete from Cloud (All Users)",
                                            subtitle = if (isPinned) "Unpin folder first to delete" else "Permanently deletes for everyone",
                                            iconRes = R.drawable.ic_trash,
                                            destructive = true
                                        ) {
                                            if (isPinned) {
                                                Toast.makeText(this@MainActivity, "Folder is pinned. Unpin it first to delete.", Toast.LENGTH_LONG).show()
                                            } else {
                                                val freshPins = pinsByLayerId[layer.id].orEmpty()
                                                val freshTracks = tracksByLayerId[layer.id].orEmpty()
                                                showDeleteCloudDialog(layer, freshPins, freshTracks)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        DesignSystem.showActionBottomSheet(
                            ctx = this@MainActivity,
                            palette = p,
                            title = layer.name,
                            subtitle = folderSubtitle,
                            actions = folderActions,
                            headerContent = { listCol, dismissSheet ->
                                fun tileRippleBg(fill: Int, stroke: Int, radiusDp: Float): android.graphics.drawable.RippleDrawable {
                                    val content = DesignSystem.buildOpaqueDrawable(
                                        ctx = this@MainActivity,
                                        fillColor = fill,
                                        radiusDp = radiusDp,
                                        strokeColor = stroke,
                                        strokeDp = if (stroke == Color.TRANSPARENT) 0f else 1f
                                    )
                                    val ripple = Color.parseColor(if (night) "#2AFFFFFF" else "#1A1C1E1B")
                                    val mask = GradientDrawable().apply {
                                        setColor(Color.WHITE)
                                        cornerRadius = dp(radiusDp.toInt()).toFloat()
                                    }
                                    return android.graphics.drawable.RippleDrawable(
                                        android.content.res.ColorStateList.valueOf(ripple),
                                        content,
                                        mask
                                    )
                                }
                                fun buildGridTile(
                                    label: String,
                                    iconRes: Int,
                                    highlight: Boolean = false,
                                    addEndGap: Boolean = true,
                                    onTap: () -> Unit
                                ): LinearLayout = LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.VERTICAL
                                    gravity = Gravity.CENTER
                                    isClickable = true
                                    isFocusable = true
                                    setPadding(dp(8), dp(6), dp(8), dp(6))
                                    background = tileRippleBg(
                                        fill = if (highlight) p.accentPrimarySoft else p.surfaceTonal,
                                        stroke = if (highlight) Color.TRANSPARENT else p.borderSubtle,
                                        radiusDp = 16f
                                    )
                                    layoutParams = LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                                        if (addEndGap) marginEnd = dp(8)
                                    }
                                    val fg = if (highlight) p.accentPrimary else txtPrimary
                                    addView(ImageView(this@MainActivity).apply {
                                        setImageResource(iconRes)
                                        setColorFilter(fg)
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        layoutParams = LinearLayout.LayoutParams(dp(20), dp(20)).apply { bottomMargin = dp(4) }
                                    })
                                    addView(TextView(this@MainActivity).apply {
                                        text = label
                                        textSize = 12.5f
                                        setTypeface(null, Typeface.BOLD)
                                        setTextColor(fg)
                                        maxLines = 1
                                        ellipsize = TextUtils.TruncateAt.END
                                        gravity = Gravity.CENTER
                                    })
                                    UiAnimations.attachSnappyPress(this, 0.97f)
                                    setOnClickListener {
                                        dismissSheet()
                                        onTap()
                                    }
                                }
                                fun buildExportPill(
                                    label: String,
                                    iconRes: Int,
                                    addEndGap: Boolean = true,
                                    onTap: () -> Unit
                                ): LinearLayout = LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    gravity = Gravity.CENTER
                                    isClickable = true
                                    isFocusable = true
                                    setPadding(dp(10), 0, dp(10), 0)
                                    background = tileRippleBg(p.surfaceTonal, p.borderSubtle, 14f)
                                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                                        if (addEndGap) marginEnd = dp(8)
                                    }
                                    addView(ImageView(this@MainActivity).apply {
                                        setImageResource(iconRes)
                                        setColorFilter(txtPrimary)
                                        scaleType = ImageView.ScaleType.FIT_CENTER
                                        layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(7) }
                                    })
                                    addView(TextView(this@MainActivity).apply {
                                        text = label
                                        textSize = 13f
                                        setTypeface(null, Typeface.BOLD)
                                        setTextColor(txtPrimary)
                                        maxLines = 1
                                        ellipsize = TextUtils.TruncateAt.END
                                    })
                                    UiAnimations.attachSnappyPress(this, 0.97f)
                                    setOnClickListener {
                                        dismissSheet()
                                        onTap()
                                    }
                                }

                                val row1 = LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply { bottomMargin = dp(8) }
                                }
                                if (isLocal) {
                                    row1.addView(buildGridTile("Upload Cloud", R.drawable.ic_upload_cloud, highlight = true) {
                                        val freshPins = pinsByLayerId[layer.id].orEmpty()
                                        val freshTracks = tracksByLayerId[layer.id].orEmpty()
                                        confirmUploadLocalToCloud(layer, freshPins, freshTracks)
                                    })
                                } else {
                                    row1.addView(buildGridTile("Share (${layer.shareCode})", R.drawable.ic_share, highlight = true) {
                                        shareLayerLink(layer)
                                    })
                                }
                                row1.addView(buildGridTile("Zoom Map", R.drawable.ic_my_location, addEndGap = false) {
                                    val freshPins = pins.filter { it.layerId == layer.id }
                                    val freshTracks = tracks.filter { it.layerId == layer.id }
                                    closeFullPage()
                                    zoomToKmlBounds(collectLayerLatLngs(freshPins, freshTracks), layer.id)
                                })
                                listCol.addView(row1)

                                val row2 = LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                }
                                row2.addView(buildGridTile("Team Report", R.drawable.ic_report) {
                                    val freshPins = pinsByLayerId[layer.id].orEmpty()
                                    val freshTracks = tracksByLayerId[layer.id].orEmpty()
                                    showDailyWhatsAppReportDialog(layer, freshPins, freshTracks)
                                })
                                row2.addView(buildGridTile("Edit History", R.drawable.ic_history, addEndGap = false) {
                                    pushSubPage({ popSubPage(::buildBookmarksAndTracksPage) }) {
                                        buildEditHistoryPage(layer)
                                    }
                                })
                                listCol.addView(row2)

                                val exportRow = LinearLayout(this@MainActivity).apply {
                                    orientation = LinearLayout.HORIZONTAL
                                    layoutParams = LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.WRAP_CONTENT
                                    ).apply {
                                        topMargin = dp(8)
                                        bottomMargin = dp(10)
                                    }
                                }
                                exportRow.addView(buildExportPill("Export KML", R.drawable.ic_download) {
                                    val freshPins = pinsByLayerId[layer.id].orEmpty()
                                    val freshTracks = tracksByLayerId[layer.id].orEmpty()
                                    showExportKmlDialog(layer, freshPins, freshTracks)
                                })
                                exportRow.addView(buildExportPill("Export CSV", R.drawable.ic_import_file, addEndGap = false) {
                                    val freshPins = pinsByLayerId[layer.id].orEmpty()
                                    KmlCsvExporter.exportAndShareCsv(this@MainActivity, layer, freshPins)
                                })
                                listCol.addView(exportRow)
                            }
                        )
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
            bookmarkSearchSeq++
            searchRunnable?.let { etFolderSearch.removeCallbacks(it) }
        }
        renderFoldersOrSearch("")
        return outer
    }

    private fun showExportKmlDialog(layer: LayerItem, layerPins: List<PinItem>, layerTracks: List<TrackItem>) {
        val sentIds = DevicePrefs.getLocalSentPinIds(this)
        val sentInLayer = layerPins.count { it.id in sentIds }
        val unsentPins = layerPins.filter { it.id !in sentIds }
        val pendingPins = layerPins.filter { PinStatus.fromRaw(it.status) == PinStatus.PENDING }
        val pendingUnsentPins = pendingPins.filter { it.id !in sentIds }
        val issuePins = layerPins.filter { PinStatus.fromRaw(it.status) == PinStatus.ISSUE }
        val donePins = layerPins.filter { PinStatus.fromRaw(it.status) == PinStatus.DONE }

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

        val p = currentPalette()
        DesignSystem.showActionBottomSheet(
            ctx = this,
            palette = p,
            title = "Export KML",
            subtitle = layer.name,
            actions = entries.map { (label, subset, suffix) ->
                SheetActionItem(
                    title = label,
                    iconRes = R.drawable.ic_download
                ) {
                    val tracksToExport = if (suffix.isEmpty()) layerTracks else emptyList()
                    if (subset.isEmpty() && tracksToExport.isEmpty()) {
                        Toast.makeText(this, "No matching pins to export", Toast.LENGTH_SHORT).show()
                    } else {
                        KmlCsvExporter.exportAndShareKml(this, layer, subset, tracksToExport, suffix)
                    }
                }
            }
        )
    }

    private fun buildLocalDeviceAliasMap(): Map<String, String> {
        val custom = DevicePrefs.getCustomName(this).trim()
        if (custom.isBlank()) return emptyMap()
        val devId = DevicePrefs.getDeviceId(this).trim()
        return if (devId.isNotBlank()) mapOf(devId to custom) else emptyMap()
    }

    private fun showVisibleFoldersDailyReportDialog() {
        val hiddenIds = DevicePrefs.getHiddenLayerIds(this)
        val visibleLayers = layers.filter { it.id !in hiddenIds }
        if (visibleLayers.isEmpty()) {
            Toast.makeText(this, "No visible folders on the map", Toast.LENGTH_SHORT).show()
            return
        }
        if (visibleLayers.size == 1) {
            val only = visibleLayers.first()
            showDailyWhatsAppReportDialog(
                only,
                pinsByLayerId[only.id].orEmpty(),
                tracksByLayerId[only.id].orEmpty()
            )
            return
        }
        val visibleLayerIds = visibleLayers.mapTo(HashSet(visibleLayers.size)) { it.id }
        val scopedPins = pins.filter { it.layerId in visibleLayerIds }
        val scopedTracks = tracks.filter { it.layerId in visibleLayerIds }
        val hasLocal = visibleLayers.any { LocalFolderStore.isLocalLayerId(it.id) }
        val cloudLayersVisible = visibleLayers.filterNot { LocalFolderStore.isLocalLayerId(it.id) }
        val allCloudLive = cloudLayersVisible.isNotEmpty() && cloudLayersVisible.all { repo.isLayerLive(it.id) }
        val syncBadge = when {
            cloudLayersVisible.isEmpty() -> "Local"
            !hasLocal && allCloudLive -> "Live Cloud"
            !hasLocal -> "Offline Cache"
            allCloudLive -> "Live Cloud + Local"
            else -> "Offline Cache + Local"
        }
        val syntheticLayer = LayerItem(
            id = "visible_scope",
            name = "Visible Folders (${visibleLayers.size})"
        )
        showDailyWhatsAppReportDialog(
            layer = syntheticLayer,
            layerPins = scopedPins,
            layerTracks = scopedTracks,
            syncBadgeOverride = syncBadge
        )
    }

    private fun formatWhatsAppReportPreview(raw: String, p: ThemePalette): CharSequence {
        val sb = SpannableStringBuilder()
        val display = raw.replace("⏳", "○").replace("⏰", "◷")
        val lines = display.split('\n')

        fun appendGlyphAware(seg: String) {
            for (ch in seg) {
                val start = sb.length
                sb.append(ch)
                val symColor = when (ch) {
                    '✓' -> p.green
                    '⚠', '◷' -> p.red
                    '○' -> p.amber
                    else -> null
                }
                if (symColor != null) {
                    sb.setSpan(ForegroundColorSpan(symColor), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        }

        lines.forEachIndexed { lineIdx, rawLine ->
            if (lineIdx > 0) sb.append('\n')
            val line = rawLine.trimEnd('\r')
            val startPos = sb.length

            if (line.length >= 3 && line.startsWith('*') && line.endsWith('*') && line.indexOf('*', 1) == line.length - 1) {
                val inner = line.substring(1, line.length - 1)
                appendGlyphAware(inner)
                val endPos = sb.length
                sb.setSpan(StyleSpan(Typeface.BOLD), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(
                    RelativeSizeSpan(if (lineIdx == 0) 1.10f else 1.04f),
                    startPos,
                    endPos,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                val headingColor = when {
                    inner.contains('✓') -> p.green
                    inner.contains('⚠') -> p.red
                    else -> p.textPrimary
                }
                sb.setSpan(ForegroundColorSpan(headingColor), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (line.length >= 3 && line.startsWith('_') && line.endsWith('_') && line.indexOf('_', 1) == line.length - 1) {
                val inner = line.substring(1, line.length - 1)
                sb.append(inner)
                val endPos = sb.length
                sb.setSpan(StyleSpan(Typeface.ITALIC), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(0.92f), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(ForegroundColorSpan(p.textMuted), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else {
                var i = 0
                while (i < line.length) {
                    val ch = line[i]
                    if (ch == '*') {
                        val close = line.indexOf('*', i + 1)
                        if (close > i + 1) {
                            val s = sb.length
                            appendGlyphAware(line.substring(i + 1, close))
                            val e = sb.length
                            sb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            sb.setSpan(ForegroundColorSpan(p.textPrimary), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = close + 1
                            continue
                        }
                    } else if (ch == '_' && (i == 0 || !line[i - 1].isLetterOrDigit())) {
                        val close = line.indexOf('_', i + 1)
                        if (close > i + 1 && (close == line.length - 1 || !line[close + 1].isLetterOrDigit())) {
                            val s = sb.length
                            sb.append(line.substring(i + 1, close))
                            val e = sb.length
                            sb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            sb.setSpan(ForegroundColorSpan(p.textMuted), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            i = close + 1
                            continue
                        }
                    }
                    appendGlyphAware(ch.toString())
                    i++
                }
            }
        }
        return sb
    }

    private fun showDailyWhatsAppReportDialog(
        layer: LayerItem,
        layerPins: List<PinItem>,
        layerTracks: List<TrackItem>,
        syncBadgeOverride: String? = null
    ) {
        val p = currentPalette()
        val workStart = DevicePrefs.getWorkStartMinutes(this)
        val workEnd = DevicePrefs.getWorkEndMinutes(this)
        val aliases = buildLocalDeviceAliasMap()
        val allPinsSnapshot = pins.toList()
        val layerPinsSnapshot = layerPins.toList()
        val isLocal = LocalFolderStore.isLocalLayerId(layer.id)
        val isLayerLive = !isLocal && repo.isLayerLive(layer.id)
        val resolvedBadge = syncBadgeOverride ?: when {
            isLocal -> "Local"
            isLayerLive -> "Live Cloud"
            else -> "Offline Cache"
        }

        val sentIds = if (hideSentPins) DevicePrefs.getLocalSentPinIds(this) else emptySet()
        val mapFilteredPins = layerPinsSnapshot.filter { pin ->
            PinStatus.fromRaw(pin.status).key in visibleStatuses && pin.id !in sentIds
        }
        val mapFilterHidesSome = mapFilteredPins.size != layerPinsSnapshot.size

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(14))
        }
        val tvScopeBanner = TextView(this).apply {
            textSize = 12f
            setTextColor(p.textSecondary)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                setColor(p.cardSurface)
                cornerRadius = dp(12).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            visibility = if (mapFilterHidesSome) View.VISIBLE else View.GONE
        }
        container.addView(tvScopeBanner)

        val scopeToggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
            visibility = if (mapFilterHidesSome) View.VISIBLE else View.GONE
        }
        val btnScopeFull = MaterialButton(this).apply {
            text = "All Folder Pins (${layerPinsSnapshot.size})"
            textSize = 12f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(10)
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
            UiAnimations.attachSnappyPress(this)
        }
        val btnScopeVisible = MaterialButton(this).apply {
            text = "Map Filtered (${mapFilteredPins.size})"
            textSize = 12f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(10)
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
            UiAnimations.attachSnappyPress(this)
        }
        scopeToggleRow.addView(btnScopeFull)
        scopeToggleRow.addView(btnScopeVisible)
        container.addView(scopeToggleRow)

        // Visual Summary Strip (Visited progress bar + tinted Done / Issue / Pending chips)
        val summaryCard = DesignSystem.createGroupedCard(
            this, p, radiusDp = 14f, paddingHDp = 12, paddingVDp = 10, bottomMarginDp = 10
        )
        val progHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        progHeader.addView(TextView(this).apply {
            text = "Visited Progress"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.textMuted)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val tvProgressPct = TextView(this).apply {
            text = "0% (0 / 0)"
            textSize = 12.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.textPrimary)
            DesignSystem.applyTabularNumerals(this)
        }
        progHeader.addView(tvProgressPct)
        summaryCard.addView(progHeader)

        val barTrack = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(p.borderSubtle)
                cornerRadius = dp(4).toFloat()
            }
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
        }
        val segDone = View(this).apply {
            setBackgroundColor(p.green)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f)
        }
        val segIssue = View(this).apply {
            setBackgroundColor(p.red)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f)
        }
        val segPending = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        barTrack.addView(segDone)
        barTrack.addView(segIssue)
        barTrack.addView(segPending)
        summaryCard.addView(barTrack)

        val chipsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun makeStatChip(fgColor: Int, bgColor: Int, addEndGap: Boolean): TextView {
            return TextView(this).apply {
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(fgColor)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(6), dp(5), dp(6), dp(5))
                DesignSystem.applyTabularNumerals(this)
                background = GradientDrawable().apply {
                    setColor(bgColor)
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (addEndGap) marginEnd = dp(6)
                }
            }
        }
        val chipDone = makeStatChip(p.green, p.greenSoft, addEndGap = true).apply { text = "✓ 0 Done" }
        val chipIssue = makeStatChip(p.red, p.redSoft, addEndGap = true).apply { text = "⚠ 0 Issue" }
        val chipPending = makeStatChip(p.amber, p.amberSoft, addEndGap = false).apply { text = "○ 0 Pending" }
        chipsRow.addView(chipDone)
        chipsRow.addView(chipIssue)
        chipsRow.addView(chipPending)
        summaryCard.addView(chipsRow)
        container.addView(summaryCard)

        val maxScrollPx = (resources.displayMetrics.heightPixels * 0.42f).toInt().coerceAtLeast(dp(180))
        val scroll = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val cappedSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                    MeasureSpec.makeMeasureSpec(maxScrollPx, MeasureSpec.AT_MOST)
                } else {
                    MeasureSpec.makeMeasureSpec(
                        minOf(MeasureSpec.getSize(heightMeasureSpec), maxScrollPx),
                        MeasureSpec.AT_MOST
                    )
                }
                super.onMeasure(widthMeasureSpec, cappedSpec)
            }
        }.apply {
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val tvReport = TextView(this).apply {
            text = "Generating report..."
            textSize = 13.5f
            setTextColor(p.textBody)
            setTextIsSelectable(true)
            setLineSpacing(dp(2).toFloat(), 1.16f)
            DesignSystem.applyTabularNumerals(this)
        }
        scroll.addView(tvReport)
        container.addView(scroll)

        var activeReportText = ""
        var activeScopedPins = layerPinsSnapshot
        var genSeq = 0

        fun computeAndDisplay(useMapFiltered: Boolean) {
            val mySeq = ++genSeq
            activeScopedPins = if (useMapFiltered) mapFilteredPins else layerPinsSnapshot
            if (mapFilterHidesSome) {
                DesignSystem.styleSegmentedButton(btnScopeFull, !useMapFiltered, p)
                DesignSystem.styleSegmentedButton(btnScopeVisible, useMapFiltered, p)
                tvScopeBanner.text = if (useMapFiltered) {
                    "Matching active map filters (${mapFilteredPins.size} of ${layerPinsSnapshot.size} pins visible on map)."
                } else {
                    "Showing full folder scope (${layerPinsSnapshot.size} pins). Active map filters hide ${layerPinsSnapshot.size - mapFilteredPins.size} pins on map."
                }
            }
            val titleForScope = if (useMapFiltered) "${layer.name} (Filtered)" else layer.name
            val pinsForRun = activeScopedPins
            RoutingEngine.ioExecutor.execute {
                val report = DailyReportGenerator.buildReport(
                    folderName = titleForScope,
                    pins = pinsForRun,
                    isCloudLive = isLayerLive,
                    workStartMin = workStart,
                    workEndMin = workEnd,
                    syncBadgeOverride = resolvedBadge,
                    configuredDeviceAliases = aliases,
                    allKnownPinsForDeviceMap = allPinsSnapshot
                )
                runOnUiThread {
                    if (isFinishing || isDestroyed || mySeq != genSeq) return@runOnUiThread
                    activeReportText = report.whatsappText
                    tvReport.text = formatWhatsAppReportPreview(report.whatsappText, p)

                    val visited = report.doneCount + report.issueCount
                    val total = report.totalPins
                    val pct = if (total > 0) ((visited * 100.0) / total).roundToInt() else 0
                    tvProgressPct.text = "$pct% ($visited / $total)"
                    chipDone.text = "✓ ${report.doneCount} Done"
                    chipIssue.text = "⚠ ${report.issueCount} Issue"
                    chipPending.text = "○ ${report.pendingCount} Pending"

                    (segDone.layoutParams as LinearLayout.LayoutParams).weight = report.doneCount.toFloat()
                    (segIssue.layoutParams as LinearLayout.LayoutParams).weight = report.issueCount.toFloat()
                    (segPending.layoutParams as LinearLayout.LayoutParams).weight =
                        if (total > 0) report.pendingCount.toFloat() else 1f
                    barTrack.requestLayout()
                }
            }
        }

        btnScopeFull.setOnClickListener { computeAndDisplay(false) }
        btnScopeVisible.setOnClickListener { computeAndDisplay(true) }
        computeAndDisplay(false)

        var reportDialog: AlertDialog? = null
        val ctaRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }
        val btnCopy = MaterialButton(this).apply {
            text = "Copy"
            textSize = 13f
            isAllCaps = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            cornerRadius = dp(18)
            DesignSystem.applySecondaryTonalButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val textToCopy = activeReportText.ifBlank { tvReport.text.toString() }
                copyToClipboard("Daily Report", textToCopy)
                reportDialog?.dismiss()
            }
        }
        val btnKml = MaterialButton(this).apply {
            text = "KML"
            textSize = 13f
            isAllCaps = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            cornerRadius = dp(18)
            DesignSystem.applySecondaryTonalButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val textToShare = activeReportText.ifBlank { tvReport.text.toString() }
                KmlCsvExporter.exportAndShareKml(this@MainActivity, layer, activeScopedPins, layerTracks, "", textToShare)
                reportDialog?.dismiss()
            }
        }
        val btnShare = MaterialButton(this).apply {
            text = "Share"
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(10), 0, dp(10), 0)
            cornerRadius = dp(18)
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(p.accentPrimary)
            icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_share)
            iconTint = ColorStateList.valueOf(Color.WHITE)
            iconSize = dp(15)
            iconPadding = dp(6)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            elevation = 0f
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.35f)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val textToShare = activeReportText.ifBlank { tvReport.text.toString() }
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, textToShare)
                }
                reportDialog?.dismiss()
                safeStartActivity(Intent.createChooser(share, "Share Daily Report"))
            }
        }
        ctaRow.addView(btnCopy)
        ctaRow.addView(btnKml)
        ctaRow.addView(btnShare)
        container.addView(ctaRow)

        reportDialog = showThemedDialog(
            themedDialogBuilder("Daily Field Report")
                .setView(container)
                .create()
        )
    }

    private fun showRenameFolderDialog(layer: LayerItem) {
        val p = currentPalette()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val etName = DesignSystem.createThemedEditText(this, p, "Folder name", initialText = layer.name)
        box.addView(etName)

        showThemedDialog(
            themedDialogBuilder("Rename Folder")
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
                .create()
        )
    }

    private fun showDeleteCloudDialog(layer: LayerItem, layerPins: List<PinItem>, layerTracks: List<TrackItem>) {
        showThemedDialog(
            themedDialogBuilder("Delete \"${layer.name}\" from Cloud?")
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
                .create(),
            destructivePositive = true
        )
    }

    // --- Subpage: Folder Bookmarks List ---

    private fun buildPinsListPage(title: String, sourcePins: List<PinItem>, layer: LayerItem?): View {
        val p = currentPalette()
        val night = p.isDark
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary

        fun currentSourcePins(): List<PinItem> = when {
            layer != null -> pinsByLayerId[layer.id].orEmpty()
            title == "Starred" -> {
                val bm = DevicePrefs.getBookmarks(this)
                pins.filter { it.id in bm }
            }
            else -> sourcePins
        }

        val topActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        if (layer != null) {
            topActions.addView(MaterialButton(this).apply {
                text = "History"
                textSize = 12f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(10), 0, dp(10), 0)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                    marginEnd = dp(6)
                }
                DesignSystem.styleSegmentedButton(this, false, p)
                UiAnimations.attachSnappyPress(this)
                setOnClickListener {
                    pushSubPage({
                        popSubPage {
                            buildPinsListPage(layer.name, currentSourcePins(), layer)
                        }
                    }) {
                        buildEditHistoryPage(layer)
                    }
                }
            })
        }
        topActions.addView(MaterialButton(this).apply {
            text = AppStrings.zoom(this@MainActivity)
            textSize = 12f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setTextColor(Color.WHITE)
            setBackgroundColor(p.accent)
            cornerRadius = dp(10)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val targetPins = currentSourcePins()
                val targetTracks = if (layer != null) tracks.filter { it.layerId == layer.id } else emptyList()
                val targetPts = collectLayerLatLngs(targetPins, targetTracks)
                closeFullPage()
                zoomToKmlBounds(targetPts, layer?.id)
            }
        })

        val (outer, body) = makePageFrame(title, {
            if (pageBackStack.isNotEmpty()) pageBackStack.removeAt(pageBackStack.lastIndex).invoke() else closeFullPage()
        }, topActions)

        var sortNearest = currentLocation != null
        var filterKey = "all"
        var selectionMode = false
        val selectedPinIds = linkedSetOf<String>()
        val canDeletePins = layer == null || isLayerOwner(layer)

        fun pinPriorityRank(pin: PinItem): Int = when (PinStatus.fromRaw(pin.status)) {
            PinStatus.DONE -> 3
            PinStatus.ISSUE -> 2
            PinStatus.PENDING -> if (pin.isEdited) 1 else 0
        }

        fun sortKeepPrimaryFirst(group: List<PinItem>): List<PinItem> =
            group.sortedWith(
                compareByDescending<PinItem> { pinPriorityRank(it) }
                    .thenByDescending { it.editedAt }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.id }
            )

        /** Finds extra duplicate pin IDs (same normalized name + same coordinates), keeping 1 primary per group. */
        fun findDuplicateExtraIds(list: List<PinItem>): Set<String> {
            val byKey = list.groupBy { pin -> DailyReportGenerator.pinDedupKey(pin) }
            val extras = linkedSetOf<String>()
            byKey.values.forEach { grp ->
                if (grp.size >= 2) {
                    val ordered = sortKeepPrimaryFirst(grp)
                    for (i in 1 until ordered.size) extras.add(ordered[i].id)
                }
            }
            return extras
        }

        /**
         * Clusters pins sharing the same GPS coordinates (<= 2.0 m) using a fast spatial grid.
         * Returns (extraStackedPinIds keeping 1 best primary per cluster, clusterSizeByPinId).
         */
        fun analyzeSameCoordClusters(list: List<PinItem>): Pair<Set<String>, Map<String, Int>> {
            val cellSize = 0.00003 // ~3.3 m
            val buckets = HashMap<Long, MutableList<Int>>()
            fun bucketKey(cx: Int, cy: Int): Long = (cx.toLong() shl 32) xor (cy.toLong() and 0xFFFFFFFFL)
            list.forEachIndexed { idx, pin ->
                val cx = kotlin.math.floor(pin.lat / cellSize).toInt()
                val cy = kotlin.math.floor(pin.lng / cellSize).toInt()
                buckets.getOrPut(bucketKey(cx, cy)) { mutableListOf() }.add(idx)
            }

            val visited = BooleanArray(list.size)
            val extras = linkedSetOf<String>()
            val clusterSizes = HashMap<String, Int>()

            for (i in list.indices) {
                if (visited[i]) continue
                val cluster = mutableListOf<PinItem>()
                val queue = ArrayDeque<Int>()
                visited[i] = true
                queue.add(i)

                while (queue.isNotEmpty()) {
                    val curIdx = queue.removeFirst()
                    val curPin = list[curIdx]
                    cluster.add(curPin)
                    val curCx = kotlin.math.floor(curPin.lat / cellSize).toInt()
                    val curCy = kotlin.math.floor(curPin.lng / cellSize).toInt()
                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            val candIndices = buckets[bucketKey(curCx + dx, curCy + dy)] ?: continue
                            for (candIdx in candIndices) {
                                if (visited[candIdx]) continue
                                val cand = list[candIdx]
                                if (RoutingEngine.haversineMeters(curPin.lat, curPin.lng, cand.lat, cand.lng) <= 2.0) {
                                    visited[candIdx] = true
                                    queue.add(candIdx)
                                }
                            }
                        }
                    }
                }

                if (cluster.size >= 2) {
                    val ordered = sortKeepPrimaryFirst(cluster)
                    ordered.forEach { clusterSizes[it.id] = ordered.size }
                    for (k in 1 until ordered.size) extras.add(ordered[k].id)
                }
            }
            return extras to clusterSizes
        }

        val controlsScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val controlsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(4), 0, dp(8))
        }
        val btnSelectMode = MaterialButton(this).apply {
            text = "Select"
            textSize = 12f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(10)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(8) }
            UiAnimations.attachSnappyPress(this)
        }
        controlsRow.addView(btnSelectMode)

        val btnSort = MaterialButton(this).apply {
            textSize = 12f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(10)
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setTextColor(Color.WHITE)
            setBackgroundColor(p.green)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(8) }
        }
        controlsRow.addView(btnSort)

        val filterBtns = mutableMapOf<String, MaterialButton>()
        listOf("all" to "All", "pending" to AppStrings.pending(this), "done" to AppStrings.done(this), "issue" to AppStrings.issue(this)).forEach { (k, lbl) ->
            val b = MaterialButton(this).apply {
                text = lbl
                textSize = 12f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(12), 0, dp(12), 0)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply { marginEnd = dp(6) }
            }
            filterBtns[k] = b
            controlsRow.addView(b)
        }
        controlsScroll.addView(controlsRow)
        body.addView(controlsScroll)

        // Auto-Selection Bar (Visible in Selection Mode: Duplicates, Same Coords, All, Clear)
        val autoSelectScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            visibility = View.GONE
        }
        val autoSelectRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val btnAutoDuplicates = MaterialButton(this).apply {
            text = "Auto: Duplicates"
            textSize = 12f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(11), 0, dp(11), 0)
            cornerRadius = dp(10)
            DesignSystem.applyTabularNumerals(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) }
            DesignSystem.styleSegmentedButton(this, false, p)
            UiAnimations.attachSnappyPress(this)
        }
        val btnAutoSameCoords = MaterialButton(this).apply {
            text = "Auto: Same Coords"
            textSize = 12f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(11), 0, dp(11), 0)
            cornerRadius = dp(10)
            DesignSystem.applyTabularNumerals(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) }
            DesignSystem.styleSegmentedButton(this, false, p)
            UiAnimations.attachSnappyPress(this)
        }
        val btnSelectAll = MaterialButton(this).apply {
            text = "All"
            textSize = 12f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(11), 0, dp(11), 0)
            cornerRadius = dp(10)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { marginEnd = dp(6) }
            DesignSystem.styleSegmentedButton(this, false, p)
            UiAnimations.attachSnappyPress(this)
        }
        val btnClearSel = MaterialButton(this).apply {
            text = "Clear"
            textSize = 12f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(11), 0, dp(11), 0)
            cornerRadius = dp(10)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
            DesignSystem.styleSegmentedButton(this, false, p)
            UiAnimations.attachSnappyPress(this)
        }
        autoSelectRow.addView(btnAutoDuplicates)
        autoSelectRow.addView(btnAutoSameCoords)
        autoSelectRow.addView(btnSelectAll)
        autoSelectRow.addView(btnClearSel)
        autoSelectScroll.addView(autoSelectRow)
        body.addView(autoSelectScroll)

        val listCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            DesignSystem.applyGroupedCardSurface(this@MainActivity, this, p, radiusDp = 16f)
        }
        body.addView(listCard)

        // Bottom Selection Action Bar pinned at bottom of `outer`
        val selectionDivider = DesignSystem.createHairlineDivider(this, p).apply {
            visibility = View.GONE
        }
        val selectionBar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(p.pageBackdrop)
            setPadding(dp(14), dp(8), dp(14), dp(10))
            visibility = View.GONE
        }
        val tvSelSummary = TextView(this).apply {
            text = "0 selected"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(txtPrimary)
            DesignSystem.applyTabularNumerals(this)
            setPadding(dp(2), 0, dp(2), dp(8))
        }
        val selBtnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun makeSelActionBtn(label: String, bgColor: Int, txtColor: Int, weight: Float, marginEndDp: Int): MaterialButton {
            return MaterialButton(this).apply {
                text = label
                textSize = 12f
                isAllCaps = false
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setPadding(dp(6), 0, dp(6), 0)
                setTextColor(txtColor)
                setBackgroundColor(bgColor)
                cornerRadius = dp(10)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), weight).apply {
                    if (marginEndDp > 0) marginEnd = dp(6)
                }
                UiAnimations.attachSnappyPress(this)
            }
        }
        val btnSelZoom = makeSelActionBtn("Zoom", p.chipSurface, txtPrimary, 0.85f, 6)
        val btnSelExport = makeSelActionBtn("Export", p.accent, Color.WHITE, 0.95f, 6)
        val btnSelStatus = makeSelActionBtn("Status", p.green, Color.WHITE, 0.95f, if (canDeletePins) 6 else 0)
        val btnSelDelete = makeSelActionBtn("Delete", p.red, Color.WHITE, 0.95f, 0).apply {
            visibility = if (canDeletePins) View.VISIBLE else View.GONE
        }
        selBtnRow.addView(btnSelZoom)
        selBtnRow.addView(btnSelExport)
        selBtnRow.addView(btnSelStatus)
        if (canDeletePins) selBtnRow.addView(btnSelDelete)
        selectionBar.addView(tvSelSummary)
        selectionBar.addView(selBtnRow)
        outer.addView(selectionDivider)
        outer.addView(selectionBar)

        var displayLimit = 150
        var latestFiltered: List<PinItem> = emptyList()
        var cachedAnalysisKey: Pair<String, Long>? = null
        var cachedDupExtras: Set<String> = emptySet()
        var cachedSameCoordExtras: Set<String> = emptySet()
        var cachedClusterSizes: Map<String, Int> = emptyMap()

        fun ensureAnalysisCached(filtered: List<PinItem>) {
            val key = filterKey to dataVersion
            if (cachedAnalysisKey == key) return
            cachedAnalysisKey = key
            cachedDupExtras = findDuplicateExtraIds(filtered)
            val (sameExtras, sizes) = analyzeSameCoordClusters(filtered)
            cachedSameCoordExtras = sameExtras
            cachedClusterSizes = sizes
        }

        fun syncAutoSelectButtons() {
            btnAutoDuplicates.text = "Duplicates (${cachedDupExtras.size})"
            btnAutoSameCoords.text = "Same Coords (${cachedSameCoordExtras.size})"
            DesignSystem.styleSegmentedButton(
                btnAutoDuplicates,
                selectionMode && cachedDupExtras.isNotEmpty() && selectedPinIds.containsAll(cachedDupExtras) && selectedPinIds.size == cachedDupExtras.size,
                p,
                p.amber
            )
            DesignSystem.styleSegmentedButton(
                btnAutoSameCoords,
                selectionMode && cachedSameCoordExtras.isNotEmpty() && selectedPinIds.containsAll(cachedSameCoordExtras) && selectedPinIds.size == cachedSameCoordExtras.size,
                p,
                p.accent
            )
        }

        fun updateSelectionBarState() {
            val liveIds = currentSourcePins().mapTo(HashSet()) { it.id }
            selectedPinIds.retainAll(liveIds)
            val cnt = selectedPinIds.size
            btnSelectMode.text = if (selectionMode) "Done" else "Select"
            DesignSystem.styleSegmentedButton(btnSelectMode, selectionMode, p, p.accent)
            if (selectionMode) {
                UiAnimations.fadeShow(autoSelectScroll, 160L)
                selectionDivider.visibility = View.VISIBLE
                UiAnimations.showSlideBottom(selectionBar, 180L)
            } else {
                UiAnimations.fadeHide(autoSelectScroll, 120L)
                selectionDivider.visibility = View.GONE
                UiAnimations.hideSlideBottom(selectionBar, 140L)
            }
            tvSelSummary.text = if (cnt == 0) "Tap bookmarks or use Auto-Select above" else "$cnt bookmark${if (cnt == 1) "" else "s"} selected"
            val hasAny = cnt > 0
            listOf(btnSelZoom, btnSelExport, btnSelStatus, btnSelDelete).forEach { b ->
                b.isEnabled = hasAny
                b.alpha = if (hasAny) 1f else 0.45f
            }
            syncAutoSelectButtons()
        }

        fun renderPinRows() {
            btnSort.text = if (sortNearest) "Nearest ↓" else "A–Z ↓"
            filterBtns.forEach { (k, b) ->
                val active = k == filterKey
                DesignSystem.styleSegmentedButton(b, active, p)
            }

            listCard.removeAllViews()
            val loc = currentLocation
            val unit = cachedSpeedUnit
            val allInFolder = currentSourcePins()
            val filtered = allInFolder.filter { filterKey == "all" || PinStatus.fromRaw(it.status).key == filterKey }
            latestFiltered = filtered

            ensureAnalysisCached(filtered)
            val clusterSizes = cachedClusterSizes

            updateSelectionBarState()

            val ordered = if (selectionMode && selectedPinIds.isNotEmpty()) {
                val baseSorted = if (sortNearest && loc != null) {
                    filtered.sortedBy { RoutingEngine.haversineMeters(loc.latitude, loc.longitude, it.lat, it.lng) }
                } else {
                    filtered.sortedBy { it.name.lowercase() }
                }
                baseSorted.sortedByDescending { it.id in selectedPinIds }
            } else if (sortNearest && loc != null) {
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
                    listCard.addView(DesignSystem.createHairlineDivider(this, p, 16, 16))
                }
                var isSelected = pin.id in selectedPinIds
                val sameCount = clusterSizes[pin.id] ?: 0
                var checkBadge: TextView? = null
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(52)
                    setPadding(dp(14), dp(11), dp(14), dp(11))
                    if (selectionMode && isSelected) {
                        setBackgroundColor(p.accentSoft)
                    } else {
                        UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    }
                    setOnClickListener {
                        if (selectionMode) {
                            isSelected = if (selectedPinIds.add(pin.id)) true else {
                                selectedPinIds.remove(pin.id)
                                false
                            }
                            if (isSelected) {
                                setBackgroundColor(p.accentSoft)
                            } else {
                                UiAnimations.applyRowRipple(this@MainActivity, this, night)
                            }
                            checkBadge?.let { badge ->
                                badge.text = if (isSelected) "✓" else ""
                                badge.background = GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    if (isSelected) {
                                        setColor(p.accent)
                                    } else {
                                        setColor(Color.TRANSPARENT)
                                        setStroke(dp(2), p.iconMuted)
                                    }
                                }
                                UiAnimations.animateScalePop(badge, 0.72f, 160L)
                            }
                            updateSelectionBarState()
                        } else {
                            ensurePinVisibleOnMap(pin)
                            closeFullPage()
                            selectPinAndOpenCard(pin, zoomIfFar = true)
                        }
                    }
                    setOnLongClickListener {
                        if (!selectionMode) {
                            selectionMode = true
                            selectedPinIds.add(pin.id)
                            renderPinRows()
                            true
                        } else false
                    }
                }

                if (selectionMode) {
                    val badge = TextView(this).apply {
                        text = if (isSelected) "✓" else ""
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            if (isSelected) {
                                setColor(p.accent)
                            } else {
                                setColor(Color.TRANSPARENT)
                                setStroke(dp(2), p.iconMuted)
                            }
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(12) }
                    }
                    checkBadge = badge
                    row.addView(badge)
                } else {
                    row.addView(View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(Color.parseColor(statusColorHex(pin.status)))
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(12) }
                    })
                }

                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(8)
                    }
                }
                val nameRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                nameRow.addView(TextView(this).apply {
                    text = pin.name
                    textSize = 15f
                    setTextColor(txtPrimary)
                    setTypeface(null, Typeface.BOLD)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (sameCount >= 2) {
                    nameRow.addView(TextView(this).apply {
                        text = "${sameCount}× GPS"
                        textSize = 10f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(p.amber)
                        DesignSystem.applyTabularNumerals(this)
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        background = GradientDrawable().apply {
                            setColor(p.amberSoft)
                            cornerRadius = dp(6).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(6) }
                    })
                }
                col.addView(nameRow)

                val st = PinStatus.fromRaw(pin.status)
                val cleanNote = DailyReportGenerator.flattenSingleLine(pin.issueReason)
                val subParts = mutableListOf(st.label)
                if (st == PinStatus.ISSUE && cleanNote.isNotBlank()) subParts += "⚠ $cleanNote"
                else if (st == PinStatus.DONE && cleanNote.isNotBlank()) subParts += "✓ $cleanNote"
                if (pin.isEdited && pin.editedBy.isNotBlank()) subParts += "Edited by ${pin.editedBy}"
                col.addView(TextView(this).apply {
                    text = subParts.joinToString(" • ")
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    setTextColor(
                        when {
                            st == PinStatus.ISSUE -> p.red
                            st == PinStatus.DONE && cleanNote.isNotBlank() -> p.green
                            else -> txtSub
                        }
                    )
                })
                row.addView(col)

                if (loc != null) {
                    val d = RoutingEngine.haversineMeters(loc.latitude, loc.longitude, pin.lat, pin.lng)
                    row.addView(TextView(this).apply {
                        text = formatDistanceShort(d, unit)
                        textSize = 12f
                        setTextColor(p.accent)
                        setTypeface(null, Typeface.BOLD)
                        DesignSystem.applyTabularNumerals(this)
                    })
                }
                listCard.addView(row)
            }

            val remainingCount = ordered.size - pageItems.size
            if (remainingCount > 0) {
                listCard.addView(DesignSystem.createHairlineDivider(this, p))
                listCard.addView(TextView(this).apply {
                    text = "Show more ($remainingCount remaining)"
                    textSize = 13.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(p.accent)
                    gravity = Gravity.CENTER
                    minimumHeight = dp(48)
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                    UiAnimations.applyRowRipple(this@MainActivity, this, night)
                    setOnClickListener {
                        displayLimit += 150
                        renderPinRows()
                    }
                })
            }
        }

        btnSelectMode.setOnClickListener {
            selectionMode = !selectionMode
            if (!selectionMode) selectedPinIds.clear()
            renderPinRows()
        }
        btnAutoDuplicates.setOnClickListener {
            ensureAnalysisCached(latestFiltered)
            val extras = cachedDupExtras
            selectedPinIds.clear()
            selectedPinIds.addAll(extras)
            renderPinRows()
            val msg = if (extras.isEmpty()) {
                "No duplicate bookmarks found in this folder"
            } else {
                "Auto-selected ${extras.size} duplicate cop${if (extras.size == 1) "y" else "ies"} (kept 1 primary per group)"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        btnAutoSameCoords.setOnClickListener {
            ensureAnalysisCached(latestFiltered)
            val extras = cachedSameCoordExtras
            selectedPinIds.clear()
            selectedPinIds.addAll(extras)
            renderPinRows()
            val msg = if (extras.isEmpty()) {
                "No bookmarks sharing the same coordinates found"
            } else {
                "Auto-selected ${extras.size} extra bookmark${if (extras.size == 1) "" else "s"} at same coordinates (kept 1 primary per spot)"
            }
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        btnSelectAll.setOnClickListener {
            selectedPinIds.clear()
            selectedPinIds.addAll(latestFiltered.map { it.id })
            renderPinRows()
        }
        btnClearSel.setOnClickListener {
            selectedPinIds.clear()
            renderPinRows()
        }

        btnSelZoom.setOnClickListener {
            val chosen = currentSourcePins().filter { it.id in selectedPinIds }
            if (chosen.isEmpty()) return@setOnClickListener
            closeFullPage()
            zoomToKmlBounds(collectLayerLatLngs(chosen, emptyList()), layer?.id)
        }
        btnSelExport.setOnClickListener {
            val chosen = currentSourcePins().filter { it.id in selectedPinIds }
            if (chosen.isEmpty()) return@setOnClickListener
            val exportLayer = layer ?: LayerItem(id = "selected", name = title)
            KmlCsvExporter.exportAndShareKml(this, exportLayer, chosen, emptyList(), "Selected_${chosen.size}")
        }
        btnSelStatus.setOnClickListener {
            val chosen = currentSourcePins().filter { it.id in selectedPinIds }
            if (chosen.isEmpty()) return@setOnClickListener
            fun applyBulk(action: () -> Unit) {
                action()
                selectedPinIds.clear()
                selectionMode = false
                renderPinRows()
                Toast.makeText(this, "Updated ${chosen.size} bookmark${if (chosen.size == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
            }
            val stamp = DevicePrefs.getDeviceStamp(this)
            DesignSystem.showActionBottomSheet(
                ctx = this,
                palette = p,
                title = "Update ${chosen.size} Selected",
                subtitle = "Apply status across selected bookmarks",
                actions = listOf(
                    SheetActionItem("Mark Pending", R.drawable.ic_location_pin_glyph, tintOverride = p.amber) {
                        applyBulk { savePinsStatusBatchUnified(chosen, "pending", stamp) }
                    },
                    SheetActionItem("Mark Done", R.drawable.ic_check, tintOverride = p.green) {
                        applyBulk { savePinsStatusBatchUnified(chosen, "done", stamp) }
                    },
                    SheetActionItem("Mark Issue", R.drawable.ic_info, tintOverride = p.red) {
                        applyBulk { savePinsStatusBatchUnified(chosen, "issue", stamp, "Flagged in bulk") }
                    },
                    SheetActionItem("Mark Sent (This Phone)", R.drawable.ic_cloud_sync, tintOverride = p.accentPrimary) {
                        applyBulk {
                            DevicePrefs.addLocalSentPinIds(this, chosen.map { it.id })
                            updateSentToggleUi()
                            renderMapData()
                        }
                    }
                )
            )
        }
        btnSelDelete.setOnClickListener {
            val chosen = currentSourcePins()
                .filter { it.id in selectedPinIds }
                .filter { pin -> isLayerOwner(layerById[pin.layerId]) }
            if (chosen.isEmpty()) {
                Toast.makeText(this, "Only folder owners can delete cloud bookmarks", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val scopeMsg = if (layer != null && !LocalFolderStore.isLocalLayerId(layer.id)) {
                "Permanently delete ${chosen.size} selected bookmark${if (chosen.size == 1) "" else "s"} from this cloud folder for all team members?"
            } else {
                "Delete ${chosen.size} selected bookmark${if (chosen.size == 1) "" else "s"} from this phone?"
            }
            showThemedDialog(
                themedDialogBuilder("Delete ${chosen.size} Bookmark${if (chosen.size == 1) "" else "s"}?")
                    .setMessage(scopeMsg)
                    .setPositiveButton("Delete") { _, _ ->
                        deletePinsUnified(chosen)
                        selectedPinIds.clear()
                        selectionMode = false
                        renderPinRows()
                        Toast.makeText(this, "Deleted ${chosen.size} bookmark${if (chosen.size == 1) "" else "s"}", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .create(),
                destructivePositive = true
            )
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

    // --- Subpage: KML Folder Pin Edit History & Working-Hours Audit ---

    private fun showWorkingHoursDialog(onSaved: () -> Unit) {
        val p = currentPalette()
        val curStart = DevicePrefs.getWorkStartMinutes(this)
        val curEnd = DevicePrefs.getWorkEndMinutes(this)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        box.addView(TextView(this).apply {
            text = "Submissions made outside this shift window are automatically flagged as After Hours."
            textSize = 13f
            setLineSpacing(dp(2).toFloat(), 1.05f)
            setTextColor(p.textSecondary)
            setPadding(0, 0, 0, dp(12))
        })

        var chosenStartMin = curStart
        var chosenEndMin = curEnd

        fun makeTimePickerRow(label: String, initialMin: Int, onPicked: (Int) -> Unit): LinearLayout {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            row.addView(TextView(this).apply {
                text = label
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            val btnPick = MaterialButton(this).apply {
                text = PinEditHistoryAudit.formatMinutes(initialMin)
                textSize = 14f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                setTextColor(Color.WHITE)
                setBackgroundColor(p.accent)
                cornerRadius = dp(10)
                DesignSystem.applyTabularNumerals(this)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
                UiAnimations.attachSnappyPress(this)
                setOnClickListener {
                    val cur = if (label.startsWith("Start")) chosenStartMin else chosenEndMin
                    val pickerTheme = if (p.isDark) {
                        android.R.style.Theme_Material_Dialog_Alert
                    } else {
                        android.R.style.Theme_Material_Light_Dialog_Alert
                    }
                    android.app.TimePickerDialog(
                        this@MainActivity,
                        pickerTheme,
                        { _, h, m ->
                            val total = h * 60 + m
                            onPicked(total)
                            text = PinEditHistoryAudit.formatMinutes(total)
                        },
                        cur / 60,
                        cur % 60,
                        true
                    ).show()
                }
            }
            row.addView(btnPick)
            return row
        }

        box.addView(makeTimePickerRow("Start Time", curStart) { chosenStartMin = it })
        box.addView(makeTimePickerRow("End Time", curEnd) { chosenEndMin = it })

        // Customizable quick shift presets
        var dialogRef: AlertDialog? = null
        val presetsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lateinit var renderPresets: () -> Unit

        val presetHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(6))
        }
        presetHeader.addView(TextView(this).apply {
            text = "Quick presets (tap to apply, hold to delete):"
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.textSecondary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        })
        presetHeader.addView(TextView(this).apply {
            text = "+ Save Current"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.accentPrimary)
            setPadding(dp(6), dp(4), dp(2), dp(4))
            UiAnimations.applyRowRipple(this@MainActivity, this, p.isDark)
            setOnClickListener {
                if (chosenStartMin == chosenEndMin) {
                    Toast.makeText(this@MainActivity, "Start and End times cannot be identical", Toast.LENGTH_SHORT).show()
                } else {
                    DevicePrefs.addWorkHourPreset(this@MainActivity, chosenStartMin, chosenEndMin)
                    renderPresets()
                    val lbl = "${PinEditHistoryAudit.formatMinutes(chosenStartMin)}–${PinEditHistoryAudit.formatMinutes(chosenEndMin)}"
                    Toast.makeText(this@MainActivity, "Saved preset $lbl", Toast.LENGTH_SHORT).show()
                }
            }
        })
        box.addView(presetHeader)

        renderPresets = {
            presetsBox.removeAllViews()
            val nightNow = currentPalette().isDark
            val presets = DevicePrefs.getWorkHourPresets(this)
            presets.chunked(3).forEachIndexed { rowIdx, rowItems ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    if (rowIdx > 0) setPadding(0, dp(6), 0, 0)
                }
                rowItems.forEachIndexed { colIdx, (sMin, eMin) ->
                    val lbl = "${PinEditHistoryAudit.formatMinutes(sMin)}–${PinEditHistoryAudit.formatMinutes(eMin)}"
                    row.addView(MaterialButton(this).apply {
                        text = lbl
                        textSize = 11.5f
                        isAllCaps = false
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(dp(4), 0, dp(4), 0)
                        setTextColor(p.textPrimary)
                        setBackgroundColor(p.chipSurface)
                        cornerRadius = dp(8)
                        DesignSystem.applyTabularNumerals(this)
                        layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                            if (colIdx < 2) marginEnd = dp(6)
                        }
                        UiAnimations.attachSnappyPress(this)
                        setOnClickListener {
                            chosenStartMin = sMin
                            chosenEndMin = eMin
                            DevicePrefs.setWorkStartMinutes(this@MainActivity, sMin)
                            DevicePrefs.setWorkEndMinutes(this@MainActivity, eMin)
                            Toast.makeText(this@MainActivity, "Working hours set to $lbl", Toast.LENGTH_SHORT).show()
                            onSaved()
                            dialogRef?.dismiss()
                        }
                        setOnLongClickListener {
                            if (presets.size <= 1) {
                                Toast.makeText(this@MainActivity, "Keep at least 1 preset", Toast.LENGTH_SHORT).show()
                            } else {
                                DevicePrefs.removeWorkHourPreset(this@MainActivity, sMin, eMin)
                                renderPresets()
                                Toast.makeText(this@MainActivity, "Removed preset $lbl", Toast.LENGTH_SHORT).show()
                            }
                            true
                        }
                    })
                }
                repeat(3 - rowItems.size) { remIdx ->
                    row.addView(android.widget.Space(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                            if (rowItems.size + remIdx < 2) marginEnd = dp(6)
                        }
                    })
                }
                presetsBox.addView(row)
            }
            if (presets != DevicePrefs.DEFAULT_WORK_HOUR_PRESETS) {
                presetsBox.addView(TextView(this).apply {
                    text = "Reset Presets"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(p.red)
                    setPadding(dp(2), dp(8), dp(6), dp(2))
                    UiAnimations.applyRowRipple(this@MainActivity, this, nightNow)
                    setOnClickListener {
                        DevicePrefs.resetWorkHourPresets(this@MainActivity)
                        renderPresets()
                        Toast.makeText(this@MainActivity, "Default presets restored", Toast.LENGTH_SHORT).show()
                    }
                })
            }
        }
        renderPresets()
        box.addView(presetsBox)

        dialogRef = showThemedDialog(
            themedDialogBuilder("Working Hours Window")
                .setView(box)
                .setPositiveButton("Save") { _, _ ->
                    DevicePrefs.setWorkStartMinutes(this, chosenStartMin)
                    DevicePrefs.setWorkEndMinutes(this, chosenEndMin)
                    val win = "${PinEditHistoryAudit.formatMinutes(chosenStartMin)}–${PinEditHistoryAudit.formatMinutes(chosenEndMin)}"
                    Toast.makeText(this, "Working hours set to $win", Toast.LENGTH_SHORT).show()
                    onSaved()
                }
                .setNegativeButton("Cancel", null)
                .create()
        )
    }

    private fun buildEditHistoryPage(layer: LayerItem): View {
        val p = currentPalette()
        val night = p.isDark
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary
        val isOwner = isLayerOwner(layer)
        val isLocal = LocalFolderStore.isLocalLayerId(layer.id)

        var currentEntries: List<PinEditEntry> = emptyList()

        val btnShareAudit = MaterialButton(this).apply {
            text = "Share"
            textSize = 12.5f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            setTextColor(Color.WHITE)
            setBackgroundColor(p.accent)
            cornerRadius = dp(10)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val txt = PinEditHistoryAudit.buildAuditShareText(
                    layer.name,
                    currentEntries,
                    DevicePrefs.getWorkStartMinutes(this@MainActivity),
                    DevicePrefs.getWorkEndMinutes(this@MainActivity)
                )
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, txt)
                }, "Share Edit History Audit"))
            }
        }

        val (outer, body) = makePageFrame("Edit History", {
            if (pageBackStack.isNotEmpty()) pageBackStack.removeAt(pageBackStack.lastIndex).invoke() else closeFullPage()
        }, btnShareAudit)

        var timeFilter = "all" // "all", "after_hours", "in_hours"
        var operatorFilter: String? = null
        var displayLimit = 120

        val contentRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        body.addView(contentRoot)

        fun renderAudit() {
            contentRoot.removeAllViews()
            val workStart = DevicePrefs.getWorkStartMinutes(this)
            val workEnd = DevicePrefs.getWorkEndMinutes(this)
            val windowStr = "${PinEditHistoryAudit.formatMinutes(workStart)} – ${PinEditHistoryAudit.formatMinutes(workEnd)}"
            val layerPins = pinsByLayerId[layer.id].orEmpty()
            val deviceMap = DailyReportGenerator.buildDeviceToTeamMap(pins, buildLocalDeviceAliasMap())
            val allEntries = PinEditHistoryAudit.collectFolderHistory(layerPins, workStart, workEnd, deviceMap)
            currentEntries = allEntries

            val afterHoursCnt = allEntries.count { it.isOutsideWorkHours }
            val inHoursCnt = allEntries.size - afterHoursCnt

            // 1. Summary & Working-Hours Control Card
            val summaryCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 12, bottomMarginDp = 10)

            val folderRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 0, 0, dp(8))
            }
            folderRow.addView(TextView(this).apply {
                text = layer.name
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(txtPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                }
            })
            folderRow.addView(TextView(this).apply {
                text = if (isLocal) "LOCAL" else if (isOwner) "CLOUD OWNER" else "CLOUD"
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isLocal) p.accentPrimary else p.green)
                setPadding(dp(8), dp(3), dp(8), dp(3))
                background = GradientDrawable().apply {
                    setColor(if (isLocal) p.accentPrimarySoft else p.greenSoft)
                    cornerRadius = dp(8).toFloat()
                }
            })
            summaryCard.addView(folderRow)

            // Working Hours Row
            val hoursRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(10))
            }
            val hoursCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            hoursCol.addView(TextView(this).apply {
                text = "Working Hours"
                textSize = 11.5f
                setTextColor(txtSub)
            })
            hoursCol.addView(TextView(this).apply {
                text = windowStr
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(txtPrimary)
                DesignSystem.applyTabularNumerals(this)
            })
            hoursRow.addView(hoursCol)
            hoursRow.addView(MaterialButton(this).apply {
                text = "Change"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                minWidth = 0
                minimumWidth = 0
                setPadding(dp(14), 0, dp(14), 0)
                cornerRadius = dp(18)
                setTextColor(p.accentPrimary)
                backgroundTintList = ColorStateList.valueOf(p.accentPrimarySoft)
                strokeWidth = 0
                elevation = 0f
                stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
                UiAnimations.attachSnappyPress(this)
                setOnClickListener {
                    showWorkingHoursDialog { renderAudit() }
                }
            })
            summaryCard.addView(hoursRow)

            summaryCard.addView(DesignSystem.createHairlineDivider(this, p))

            // 3 Tabular Stat Counters
            val statsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, dp(2))
            }
            fun addStat(label: String, count: Int, color: Int) {
                val col = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                col.addView(TextView(this).apply {
                    text = "$count"
                    textSize = 20f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(color)
                    DesignSystem.applyTabularNumerals(this)
                })
                col.addView(TextView(this).apply {
                    text = label
                    textSize = 11.5f
                    setTextColor(txtSub)
                })
                statsRow.addView(col)
            }
            addStat("Total Edits", allEntries.size, txtPrimary)
            addStat("In Hours", inHoursCnt, p.green)
            addStat("After Hours", afterHoursCnt, if (afterHoursCnt > 0) p.red else txtSub)
            summaryCard.addView(statsRow)
            contentRoot.addView(summaryCard)

            // 2. Time Filter Pills (All / After Hours / In Hours)
            val filterScroll = android.widget.HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
            }
            val filterRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(2), 0, dp(8))
            }
            listOf(
                listOf("all", "All (${allEntries.size})", p.accent, 0),
                listOf("after_hours", "After Hours ($afterHoursCnt)", p.red, R.drawable.ic_history),
                listOf("in_hours", "In Hours ($inHoursCnt)", p.green, R.drawable.ic_check)
            ).forEachIndexed { idx, spec ->
                val key = spec[0] as String
                val lbl = spec[1] as String
                val activeCol = spec[2] as Int
                val iconRes = spec[3] as Int
                val selected = timeFilter == key
                filterRow.addView(MaterialButton(this).apply {
                    text = lbl
                    textSize = 12f
                    isAllCaps = false
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(12), 0, dp(12), 0)
                    cornerRadius = dp(12)
                    DesignSystem.applyTabularNumerals(this)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                        if (idx < 2) marginEnd = dp(6)
                    }
                    DesignSystem.styleSegmentedButton(this, selected, p, activeCol)
                    if (iconRes != 0) {
                        icon = ContextCompat.getDrawable(this@MainActivity, iconRes)
                        iconSize = dp(14)
                        iconPadding = dp(5)
                        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                        iconTint = ColorStateList.valueOf(if (selected) Color.WHITE else activeCol)
                    }
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        timeFilter = key
                        displayLimit = 120
                        renderAudit()
                    }
                })
            }
            filterScroll.addView(filterRow)
            contentRoot.addView(filterScroll)

            // 3. Operator Filter Pills (if multiple team members contributed)
            val operatorCounts = allEntries.groupingBy { it.operatorShort }.eachCount()
                .entries.sortedByDescending { it.value }
            if (operatorCounts.size > 1) {
                val opScroll = android.widget.HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                }
                val opRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, dp(10))
                }
                fun addOpChip(label: String, opKey: String?) {
                    opRow.addView(MaterialButton(this).apply {
                        text = label
                        textSize = 11.5f
                        isAllCaps = false
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(dp(10), 0, dp(10), 0)
                        cornerRadius = dp(9)
                        DesignSystem.applyTabularNumerals(this)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                            marginEnd = dp(6)
                        }
                        DesignSystem.styleSegmentedButton(this, operatorFilter == opKey, p)
                        UiAnimations.attachSnappyPress(this)
                        setOnClickListener {
                            operatorFilter = opKey
                            displayLimit = 120
                            renderAudit()
                        }
                    })
                }
                addOpChip("All Teams", null)
                operatorCounts.forEach { (opName, cnt) ->
                    addOpChip("$opName ($cnt)", opName)
                }
                opScroll.addView(opRow)
                contentRoot.addView(opScroll)
            }

            // 4. Filtered Chronological Timeline Grouped by Date
            val filtered = allEntries.filter { e ->
                val timeOk = when (timeFilter) {
                    "after_hours" -> e.isOutsideWorkHours
                    "in_hours" -> !e.isOutsideWorkHours
                    else -> true
                }
                val opOk = operatorFilter == null || e.operatorShort == operatorFilter
                timeOk && opOk
            }

            if (filtered.isEmpty()) {
                val emptyCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 16, paddingVDp = 18)
                emptyCard.addView(TextView(this).apply {
                    text = when {
                        allEntries.isEmpty() -> "No edits recorded yet in this folder. Whenever team members update a pin, each timestamped submission appears here."
                        timeFilter == "after_hours" -> "✓ Great — zero submissions were made outside working hours ($windowStr)."
                        else -> "No matching submissions for this filter."
                    }
                    textSize = 13.5f
                    setLineSpacing(dp(2).toFloat(), 1.05f)
                    setTextColor(txtSub)
                })
                contentRoot.addView(emptyCard)
                return
            }

            val pageItems = filtered.take(displayLimit)
            val groupedByDate = pageItems.groupBy { it.dateGroup }

            groupedByDate.forEach { (dateLabel, dayEntries) ->
                val dayLate = dayEntries.count { it.isOutsideWorkHours }
                val headerTxt = if (dayLate > 0) "$dateLabel  •  $dayLate after hours" else "$dateLabel  •  ${dayEntries.size} edit${if (dayEntries.size == 1) "" else "s"}"
                contentRoot.addView(DesignSystem.createSectionHeader(this, headerTxt, p))

                val dayCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 0, paddingVDp = 2, bottomMarginDp = 8)
                dayEntries.forEachIndexed { idx, entry ->
                    if (idx > 0) {
                        dayCard.addView(DesignSystem.createHairlineDivider(this, p, 84, 14))
                    }
                    val row = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = dp(58)
                        setPadding(dp(12), 0, dp(10), 0)
                        if (entry.isOutsideWorkHours) {
                            setBackgroundColor(p.redSoft)
                        } else {
                            UiAnimations.applyRowRipple(this@MainActivity, this, night)
                        }
                        setOnClickListener {
                            ensurePinVisibleOnMap(entry.pin)
                            closeFullPage()
                            selectPinAndOpenCard(entry.pin, zoomIfFar = true)
                        }
                    }

                    // Left timestamp column
                    val timeCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        setPadding(0, dp(12), 0, dp(12))
                        layoutParams = LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            marginEnd = dp(6)
                        }
                    }
                    timeCol.addView(TextView(this).apply {
                        text = entry.clockTime
                        textSize = 13.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(if (entry.isOutsideWorkHours) p.red else txtPrimary)
                        DesignSystem.applyTabularNumerals(this)
                    })
                    row.addView(timeCol)

                    // Continuous vertical timeline rail + status dot
                    val dotColor = when (entry.actionStatus) {
                        "done" -> p.green
                        "issue" -> p.red
                        "pending" -> p.amber
                        else -> p.accent
                    }
                    val railCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(dp(16), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                            marginEnd = dp(8)
                        }
                    }
                    railCol.addView(View(this).apply {
                        setBackgroundColor(if (idx > 0) p.borderSubtle else Color.TRANSPARENT)
                        layoutParams = LinearLayout.LayoutParams(dp(2), 0, 1f)
                    })
                    railCol.addView(View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(dotColor)
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply {
                            topMargin = dp(3)
                            bottomMargin = dp(3)
                        }
                    })
                    railCol.addView(View(this).apply {
                        setBackgroundColor(if (idx < dayEntries.lastIndex) p.borderSubtle else Color.TRANSPARENT)
                        layoutParams = LinearLayout.LayoutParams(dp(2), 0, 1f)
                    })
                    row.addView(railCol)

                    // Center details column
                    val infoCol = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, dp(10), 0, dp(10))
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(6)
                        }
                    }
                    val titleRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    titleRow.addView(TextView(this).apply {
                        text = entry.pin.name
                        textSize = 14.5f
                        textDirection = View.TEXT_DIRECTION_LOCALE
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(txtPrimary)
                        setLineSpacing(dp(1).toFloat(), 1.05f)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    if (entry.isOutsideWorkHours) {
                        titleRow.addView(TextView(this).apply {
                            text = "AFTER HOURS"
                            textSize = 9.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            setPadding(dp(6), dp(2), dp(6), dp(2))
                            background = GradientDrawable().apply {
                                setColor(p.red)
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
                        text = "${entry.operatorShort} • ${entry.actionSummary}"
                        textSize = 12.5f
                        setTextColor(
                            when {
                                entry.actionStatus == "issue" -> p.red
                                entry.actionStatus == "done" -> p.green
                                else -> txtSub
                            }
                        )
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                        setPadding(0, dp(2), 0, 0)
                    })
                    row.addView(infoCol)

                    // Folder owner can delete a specific out-of-hours / mistaken submission directly
                    if (isOwner && entry.historyLineIndex >= 0) {
                        row.addView(TextView(this).apply {
                            text = "✕"
                            textSize = 13f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(p.red)
                            gravity = Gravity.CENTER
                            background = GradientDrawable().apply {
                                setColor(if (entry.isOutsideWorkHours) Color.parseColor(if (night) "#452328" else "#FCE3E1") else p.chipSurface)
                                cornerRadius = dp(10).toFloat()
                            }
                            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                                marginStart = dp(6)
                            }
                            UiAnimations.attachSnappyPress(this)
                            setOnClickListener {
                                showThemedDialog(
                                    themedDialogBuilder("Remove this submission?")
                                        .setMessage("[${entry.fullTimeLabel}] ${entry.operatorShort}\n${entry.pin.name} → ${entry.actionSummary}\n\nOnly this edit will be removed. The pin remains on the map.")
                                        .setPositiveButton("Delete Edit") { _, _ ->
                                            deletePinHistoryEntryUnified(entry.pin, entry.historyLineIndex)
                                            Toast.makeText(this@MainActivity, "Submission removed", Toast.LENGTH_SHORT).show()
                                            renderAudit()
                                        }
                                        .setNegativeButton("Cancel", null)
                                        .create(),
                                    destructivePositive = true
                                )
                            }
                        })
                    }
                    dayCard.addView(row)
                }
                contentRoot.addView(dayCard)
            }

            val remaining = filtered.size - pageItems.size
            if (remaining > 0) {
                contentRoot.addView(MaterialButton(this).apply {
                    text = "Show more ($remaining remaining)"
                    textSize = 13f
                    isAllCaps = false
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                        topMargin = dp(4)
                    }
                    DesignSystem.styleSegmentedButton(this, false, p)
                    setOnClickListener {
                        displayLimit += 120
                        renderAudit()
                    }
                })
            }
        }

        liveEditHistoryRefresh = { renderAudit() }
        renderAudit()
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
        val pending = pins.filter { it.layerId !in hidden && PinStatus.fromRaw(it.status) == PinStatus.PENDING && it.id !in sentIds }
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
        val p = currentPalette()
        val night = p.isDark
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary

        val (outer, body) = makePageFrame(AppStrings.search(this), { closeFullPage() })

        val searchBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(2), dp(8), dp(2))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
            background = DesignSystem.buildOpaqueDrawable(
                ctx = this@MainActivity,
                fillColor = p.surfaceTonal,
                radiusDp = 24f,
                strokeColor = p.borderSubtle,
                strokeDp = 1f
            )
            clipToOutline = true
        }
        searchBox.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search)
            setColorFilter(txtSub)
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) }
        })
        val etQuery = EditText(this).apply {
            hint = "Search pins, places, or coords..."
            textSize = 14.5f
            setTextColor(txtPrimary)
            setHintTextColor(txtSub)
            setBackgroundColor(Color.TRANSPARENT)
            setSingleLine(true)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val btnClearQuery = ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(txtSub)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener { etQuery.setText("") }
        }
        searchBox.addView(etQuery)
        searchBox.addView(btnClearQuery)
        body.addView(searchBox)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, dp(12))
        }
        val btnWebSearch = MaterialButton(this).apply {
            text = AppStrings.searchMapPlaces(this@MainActivity)
            textSize = 13.5f
            isAllCaps = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(22)
            DesignSystem.applyPrimaryCtaButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1.05f).apply { marginEnd = dp(8) }
            UiAnimations.attachSnappyPress(this)
        }
        val btnNearest = MaterialButton(this).apply {
            text = AppStrings.nearestPending(this@MainActivity)
            textSize = 13.5f
            isAllCaps = false
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(p.accentPrimary)
            elevation = 0f
            stateListAnimator = null
            cornerRadius = dp(22)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { jumpToNearestPendingPin() }
        }
        btnRow.addView(btnWebSearch)
        btnRow.addView(btnNearest)
        body.addView(btnRow)

        val webResultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val resultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(webResultsBox)
        body.addView(resultsBox)

        var localSearchSeq = 0

        fun renderLocalMatches(q: String) {
            val mySeq = ++localSearchSeq
            val trimmed = q.trim()
            if (trimmed.isEmpty()) {
                resultsBox.removeAllViews()
                webSearchSeq++
                webResultsBox.removeAllViews()
                val hintCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 14)
                hintCard.addView(TextView(this).apply {
                    text = "Type a bookmark name, status, or lat, lng coordinates — or tap Search Map Places for online locations."
                    textSize = 13.5f
                    setLineSpacing(dp(2).toFloat(), 1.05f)
                    setTextColor(p.textBody)
                })
                resultsBox.addView(hintCard)
                return
            }

            val ver = dataVersion
            val pinsSnapshot = pins
            val folderNameById = layers.associate { it.id to it.name }
            val tokens = trimmed.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
            val hasDigits = trimmed.any { it.isDigit() }
            val loc = currentLocation
            val unit = cachedSpeedUnit
            RoutingEngine.ioExecutor.execute {
                val coordParts = trimmed.split(",").map { it.trim().toDoubleOrNull() }
                val parsedCoord: Pair<Double, Double>? = if (coordParts.size == 2 && coordParts[0] != null && coordParts[1] != null) {
                    val lat = coordParts[0]!!
                    val lng = coordParts[1]!!
                    if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
                } else null

                val index = getOrBuildSearchIndex(ver, pinsSnapshot, folderNameById)
                val matches = filterSearchEntries(index, tokens, hasDigits, loc, unit)

                runOnUiThread {
                    if (isFinishing || isDestroyed || mySeq != localSearchSeq) return@runOnUiThread
                    resultsBox.removeAllViews()
                    if (parsedCoord != null) {
                        val (lat, lng) = parsedCoord
                        val coordCard = DesignSystem.createGroupedCard(this@MainActivity, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 12)
                        coordCard.addView(TextView(this@MainActivity).apply {
                            text = "📍 Go to coordinates: ${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lng)}"
                            textSize = 14.5f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(p.accentPrimary)
                            DesignSystem.applyTabularNumerals(this)
                            UiAnimations.applyRowRipple(this@MainActivity, this, night)
                            setOnClickListener {
                                closeFullPage()
                                selectCoordinateOnMap(LatLng(lat, lng), explicitTitle = "Coordinates", fallbackSub = "", zoomTo = 16.5)
                            }
                        })
                        resultsBox.addView(coordCard)
                    }

                    if (matches.isNotEmpty()) {
                        resultsBox.addView(DesignSystem.createSectionHeader(this@MainActivity, "Bookmarks (${matches.size})", p))
                        appendPaginatedSearchCard(resultsBox, p, matches, showFolderInSub = true, pageSize = 60)
                    }
                }
            }
        }

        renderLocalMatches("")
        var searchPageRunnable: Runnable? = null
        etQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val q = s?.toString() ?: ""
                btnClearQuery.visibility = if (q.isNotEmpty()) View.VISIBLE else View.GONE
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
                    btnWebSearch.text = AppStrings.searchMapPlaces(this)
                    webResultsBox.removeAllViews()
                    if (!err.isNullOrBlank()) {
                        Toast.makeText(this, err, Toast.LENGTH_LONG).show()
                        return@runOnUiThread
                    }
                    if (places.isEmpty()) {
                        Toast.makeText(this, "No places found", Toast.LENGTH_SHORT).show()
                        return@runOnUiThread
                    }
                    webResultsBox.addView(DesignSystem.createSectionHeader(this, "Map Places (${places.size})", p))
                    val webCard = DesignSystem.createGroupedCard(this, p, radiusDp = 18f, paddingHDp = 0, paddingVDp = 2)
                    places.forEachIndexed { idx, place ->
                        if (idx > 0) {
                            webCard.addView(View(this).apply {
                                setBackgroundColor(p.divider)
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                                    marginStart = dp(16)
                                    marginEnd = dp(16)
                                }
                            })
                        }
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.VERTICAL
                            setPadding(dp(14), dp(12), dp(14), dp(12))
                            UiAnimations.applyRowRipple(this@MainActivity, this, night)
                            setOnClickListener {
                                closeFullPage()
                                selectCoordinateOnMap(LatLng(place.lat, place.lng), explicitTitle = place.title, fallbackSub = place.subtitle, zoomTo = 16.0)
                            }
                        }
                        row.addView(TextView(this).apply {
                            text = "🌐 ${place.title}"
                            textSize = 14.5f
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
                        webCard.addView(row)
                    }
                    webResultsBox.addView(webCard)
                    UiAnimations.animateResultReveal(webCard)
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
            localSearchSeq++
            searchPageRunnable?.let { etQuery.removeCallbacks(it) }
            webSearchSeq++
        }

        UiAnimations.staggerRevealChildren(body, maxAnimated = 5)
        return outer
    }

    // --- Page 3: Settings Menu + Dedicated Section Sub-Pages (Organic Maps Style) ---

    private enum class SettingsSection {
        MAP_SHADERS,
        LANGUAGE_LABELS,
        NAVIGATION_ROUTING,
        FIELD_DATA_SYNC,
        OFFLINE_CACHE,
        ABOUT_BUILD
    }

    private fun openSettingsSection(section: SettingsSection) {
        pushSubPage({ popSubPage(::buildSettingsPage) }) {
            buildSettingsSubPage(section)
        }
    }

    private fun reopenSettingsSection(section: SettingsSection) {
        pageOverlayContainer.animate().cancel()
        activePageCleanup?.invoke()
        activePageCleanup = null
        liveBookmarksRefresh = null
        livePinListRefresh = null
        liveEditHistoryRefresh = null
        offlineMaps.setStateListener(null)
        hideSoftKeyboard()
        pageBackStack.clear()
        pageBackStack.add { popSubPage(::buildSettingsPage) }
        pageOverlayContainer.setBackgroundColor(currentPalette().pageBackdrop)
        UiAnimations.transitionCrossfade(pageOverlayContainer, buildSettingsSubPage(section))
        updateSystemBarContrast(forFullPage = true)
    }

    private fun buildSettingsPage(): View {
        val p = currentPalette()
        val night = p.isDark
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary
        val isRtl = AppStrings.isRtl(this)

        val (outer, body) = makePageFrame(AppStrings.settings(this), { closeFullPage() })

        fun addMenuRow(
            card: LinearLayout,
            iconRes: Int,
            iconColor: Int,
            title: String,
            summary: String,
            onClick: () -> Unit
        ) {
            if (card.childCount > 0) {
                card.addView(DesignSystem.createHairlineDivider(this, p, insetStartDp = 54, insetEndDp = 14))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(62)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                UiAnimations.applyRowRipple(this@MainActivity, this, night)
                UiAnimations.attachSnappyPress(this, 0.985f)
                setOnClickListener { onClick() }
            }
            val iconWrap = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply {
                    marginEnd = dp(12)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(p.surfaceFloating)
                    setStroke(dp(1), p.borderSubtle)
                }
            }
            iconWrap.addView(ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(iconColor)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER)
            })
            row.addView(iconWrap)

            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                }
            }
            textCol.addView(TextView(this).apply {
                text = title
                textSize = 15.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(txtPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            textCol.addView(TextView(this).apply {
                text = summary
                textSize = 12.5f
                setTextColor(txtSub)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                DesignSystem.applyTabularNumerals(this)
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(textCol)

            row.addView(TextView(this).apply {
                text = if (isRtl) "‹" else "›"
                textSize = 22f
                setTextColor(p.iconMuted)
                gravity = Gravity.CENTER
                setPadding(dp(4), 0, dp(2), 0)
            })
            card.addView(row)
        }

        // Live concise summaries for each menu section
        val themeLabel = when (DevicePrefs.getThemeMode(this)) {
            "night" -> "Night"
            "auto" -> "Auto"
            else -> "Day"
        }
        val mapSummary = buildList {
            add(mapStyleMode.label)
            add(themeLabel)
            add(DevicePrefs.getMapPinStyle(this@MainActivity).label)
            if (DevicePrefs.isHillshadeEnabled(this@MainActivity)) add("Hillshade")
            if (DevicePrefs.isContoursEnabled(this@MainActivity)) add("Contours")
        }.joinToString(" • ")

        val langSummary = listOf(
            DevicePrefs.getAppLanguage(this).label,
            DevicePrefs.getMapLabelLanguage(this).label,
            DevicePrefs.getPoiLabelBehavior(this).label
        ).joinToString(" • ")

        val navSummary = buildList {
            add(travelMode.label)
            add(DevicePrefs.getSpeedUnit(this@MainActivity).speedSuffix)
            if (DevicePrefs.isSpeedIndicatorEnabled(this@MainActivity)) add("Speed HUD")
            if (DevicePrefs.isVoiceNav(this@MainActivity)) add("Voice")
        }.joinToString(" • ")

        val localCount = layers.count { LocalFolderStore.isLocalLayerId(it.id) }
        val cloudCount = layers.size - localCount
        val syncSummary = "${if (isLiveConnected) "Cloud live" else "Offline cache"} • $localCount local, $cloudCount cloud • ${pins.size} pins"

        val downloadedPacks = OfflineMapManager.REGIONAL_PACKS.count { offlineMaps.getState(it.id).isDownloaded }
        val offlineSummary = if (downloadedPacks > 0) {
            "$downloadedPacks of ${OfflineMapManager.REGIONAL_PACKS.size} regions downloaded"
        } else {
            "Kurdistan & Southern Iraq • Embedded roads"
        }

        val aboutSummary = "v${getInstalledVersionName()} • Device #${DevicePrefs.getDeviceId(this)}"

        // Primary grouped menu card (5 functional settings categories)
        val mainMenuCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 0, paddingVDp = 2, bottomMarginDp = 14)

        addMenuRow(
            mainMenuCard,
            R.drawable.ic_layers,
            p.accentPrimary,
            AppStrings.secMapShaders(this),
            mapSummary
        ) { openSettingsSection(SettingsSection.MAP_SHADERS) }

        addMenuRow(
            mainMenuCard,
            R.drawable.ic_map,
            p.accentPrimary,
            AppStrings.secLanguageLabels(this),
            langSummary
        ) { openSettingsSection(SettingsSection.LANGUAGE_LABELS) }

        addMenuRow(
            mainMenuCard,
            R.drawable.ic_my_location,
            p.accentPrimary,
            AppStrings.secNavigationRouting(this),
            navSummary
        ) { openSettingsSection(SettingsSection.NAVIGATION_ROUTING) }

        addMenuRow(
            mainMenuCard,
            R.drawable.ic_cloud_sync,
            if (isLiveConnected) p.accentPrimary else p.amber,
            AppStrings.secFieldDataSync(this),
            syncSummary
        ) { openSettingsSection(SettingsSection.FIELD_DATA_SYNC) }

        addMenuRow(
            mainMenuCard,
            R.drawable.ic_import_file,
            p.iconTint,
            AppStrings.secOfflineCache(this),
            offlineSummary
        ) { openSettingsSection(SettingsSection.OFFLINE_CACHE) }

        body.addView(mainMenuCard)

        // Secondary grouped menu card (About & Build)
        val metaMenuCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 0, paddingVDp = 2, bottomMarginDp = 12)
        addMenuRow(
            metaMenuCard,
            R.drawable.ic_info,
            p.iconTint,
            AppStrings.secAboutBuild(this),
            aboutSummary
        ) { openSettingsSection(SettingsSection.ABOUT_BUILD) }
        body.addView(metaMenuCard)

        UiAnimations.staggerRevealChildren(body)
        return outer
    }

    private fun buildSettingsSubPage(section: SettingsSection): View {
        val p = currentPalette()
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary

        val pageTitle = when (section) {
            SettingsSection.MAP_SHADERS -> AppStrings.secMapShaders(this)
            SettingsSection.LANGUAGE_LABELS -> AppStrings.secLanguageLabels(this)
            SettingsSection.NAVIGATION_ROUTING -> AppStrings.secNavigationRouting(this)
            SettingsSection.FIELD_DATA_SYNC -> AppStrings.secFieldDataSync(this)
            SettingsSection.OFFLINE_CACHE -> AppStrings.secOfflineCache(this)
            SettingsSection.ABOUT_BUILD -> AppStrings.secAboutBuild(this)
        }

        val (outer, body) = makePageFrame(pageTitle, {
            if (pageBackStack.isNotEmpty()) {
                pageBackStack.removeAt(pageBackStack.lastIndex).invoke()
            } else {
                openFullPage(::buildSettingsPage)
            }
        })

        fun addSegmentedBlock(
            card: LinearLayout,
            title: String,
            subtitle: String?,
            options: List<Pair<String, String>>,
            selectedId: String,
            activeColor: ((String) -> Int)? = null,
            onSelect: (String) -> Unit
        ) {
            card.addView(TextView(this).apply {
                text = title
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(txtPrimary)
                setPadding(0, dp(8), 0, if (subtitle != null) dp(2) else dp(8))
            })
            if (!subtitle.isNullOrBlank()) {
                card.addView(TextView(this).apply {
                    text = subtitle
                    textSize = 12f
                    setTextColor(txtSub)
                    setPadding(0, 0, 0, dp(8))
                })
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, dp(10))
            }
            val btns = mutableMapOf<String, MaterialButton>()
            fun sync(activeId: String) {
                btns.forEach { (id, b) ->
                    DesignSystem.styleSegmentedButton(
                        b,
                        id == activeId,
                        p,
                        activeColor?.invoke(id) ?: p.accentPrimary,
                        onTonalCard = true
                    )
                }
            }
            options.forEachIndexed { idx, (id, label) ->
                val btn = MaterialButton(this).apply {
                    text = label
                    textSize = 12.5f
                    isAllCaps = false
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(6), 0, dp(6), 0)
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f).apply {
                        if (idx < options.lastIndex) marginEnd = dp(6)
                    }
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        sync(id)
                        onSelect(id)
                    }
                }
                btns[id] = btn
                row.addView(btn)
            }
            sync(selectedId)
            card.addView(row)
        }

        fun addCardSwitch(card: LinearLayout, title: String, subtitle: String?, checked: Boolean, addDivider: Boolean = true, onChange: (Boolean) -> Unit) {
            if (addDivider && card.childCount > 0) {
                card.addView(DesignSystem.createHairlineDivider(this, p))
            }
            card.addView(DesignSystem.createSettingsSwitchRow(this, title, subtitle, checked, p, onChange))
        }

        when (section) {
            SettingsSection.MAP_SHADERS -> {
                val mapCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 6)

                addSegmentedBlock(
                    mapCard,
                    title = "Map Style",
                    subtitle = "Crisp vector street grid or satellite hybrid imagery",
                    options = MapStyleMode.entries.map { it.id.toString() to it.label },
                    selectedId = mapStyleMode.id.toString(),
                    activeColor = { p.accentPrimary }
                ) { chosenId ->
                    val mode = MapStyleMode.fromId(chosenId.toIntOrNull() ?: MapStyleMode.VECTOR.id)
                    if (mapStyleMode != mode) {
                        mapStyleMode = mode
                        DevicePrefs.setMapStyleMode(this, mode)
                        applyMapChromeTheme()
                        loadMapStyle()
                    }
                }

                mapCard.addView(DesignSystem.createHairlineDivider(this, p))
                addSegmentedBlock(
                    mapCard,
                    title = "Appearance Theme",
                    subtitle = "Automatically adapts map canvas and system bars",
                    options = listOf("day" to "Day", "night" to "Night", "auto" to "Auto"),
                    selectedId = DevicePrefs.getThemeMode(this)
                ) { key ->
                    if (DevicePrefs.getThemeMode(this) != key) {
                        DevicePrefs.setThemeMode(this, key)
                        applyMapChromeTheme()
                        if (mapStyleMode == MapStyleMode.VECTOR) loadMapStyle()
                        reopenSettingsSection(SettingsSection.MAP_SHADERS)
                    }
                }

                addCardSwitch(
                    mapCard,
                    title = "Mountain Hillshading",
                    subtitle = "Organic green terrain relief shading (Vector Street)",
                    checked = DevicePrefs.isHillshadeEnabled(this)
                ) { enabled ->
                    DevicePrefs.setHillshadeEnabled(this, enabled)
                    map?.style?.let { st -> if (st.isFullyLoaded) applyHillshadeSettings(st) }
                }

                mapCard.addView(DesignSystem.createHairlineDivider(this, p))
                addSegmentedBlock(
                    mapCard,
                    title = "Hillshading Intensity",
                    subtitle = "Adjust relief depth for sunlight readability",
                    options = HillshadeIntensity.entries.map { it.id to it.label },
                    selectedId = DevicePrefs.getHillshadeIntensity(this).id,
                    activeColor = { p.accentPrimary }
                ) { id ->
                    val next = HillshadeIntensity.fromId(id)
                    DevicePrefs.setHillshadeIntensity(this, next)
                    map?.style?.let { st -> if (st.isFullyLoaded) applyHillshadeSettings(st) }
                }

                addCardSwitch(
                    mapCard,
                    title = "Elevation Contour Lines",
                    subtitle = "Lightweight 100 m / 500 m / 1000 m lines & elevation labels",
                    checked = DevicePrefs.isContoursEnabled(this)
                ) { enabled ->
                    DevicePrefs.setContoursEnabled(this, enabled)
                    val m = map
                    val st = m?.style
                    if (st != null && st.isFullyLoaded) {
                        contourManager.attachLayers(st, isDarkMapUi(), enabled)
                        contourManager.setVisible(st, enabled)
                        if (enabled) contourManager.scheduleUpdate(m, true, mapStyleMode == MapStyleMode.VECTOR)
                    }
                }

                addCardSwitch(
                    mapCard,
                    title = "3D Buildings",
                    subtitle = "Extruded building footprints in urban areas",
                    checked = DevicePrefs.is3dBuildingsEnabled(this)
                ) { enabled ->
                    DevicePrefs.set3dBuildingsEnabled(this, enabled)
                    map?.style?.let { st -> if (st.isFullyLoaded) apply3dBuildingsToggle(st) }
                }

                mapCard.addView(DesignSystem.createHairlineDivider(this, p))
                mapCard.addView(TextView(this).apply {
                    text = "Map Pin Style"
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    setPadding(0, dp(8), 0, dp(2))
                })
                mapCard.addView(TextView(this).apply {
                    text = "Switch instantly between white Concept Droplet rings and Classic Teardrop pins"
                    textSize = 12f
                    setTextColor(txtSub)
                    setPadding(0, 0, 0, dp(8))
                })
                val pinRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, dp(10))
                }
                data class PinStyleCardUi(
                    val style: MapPinStyle,
                    val card: LinearLayout,
                    val previewRow: LinearLayout,
                    val label: TextView
                )
                val pinCards = mutableListOf<PinStyleCardUi>()
                var selectedPinStyle = DevicePrefs.getMapPinStyle(this)

                fun syncPinStyleCards(popSelected: Boolean) {
                    pinCards.forEach { item ->
                        val active = item.style == selectedPinStyle
                        item.card.background = if (active) {
                            DesignSystem.buildOpaqueDrawable(
                                ctx = this,
                                fillColor = p.accentPrimarySoft,
                                radiusDp = 14f,
                                strokeColor = p.accentPrimary,
                                strokeDp = 1.75f
                            )
                        } else {
                            DesignSystem.buildOpaqueDrawable(
                                ctx = this,
                                fillColor = p.surfaceFloating,
                                radiusDp = 14f,
                                strokeColor = p.borderSubtle,
                                strokeDp = 1f
                            )
                        }
                        item.label.setTextColor(if (active) p.accentPrimary else p.textPrimary)
                        item.label.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
                        if (active && popSelected) {
                            UiAnimations.animateScalePop(item.previewRow, 0.85f, 160L)
                        }
                    }
                }

                MapPinStyle.entries.forEachIndexed { idx, styleOption ->
                    val styleCard = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(dp(10), dp(10), dp(10), dp(10))
                        isClickable = true
                        isFocusable = true
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (idx < MapPinStyle.entries.lastIndex) marginEnd = dp(8)
                        }
                        UiAnimations.attachSnappyPress(this, 0.96f)
                    }
                    val previewCluster = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(6) }
                    }
                    listOf("#E69500", "#2E8B47").forEachIndexed { pinIdx, hex ->
                        val bmp = if (styleOption == MapPinStyle.CONCEPT) {
                            MapBitmaps.createConceptDropletPin(this, hex, scale = 0.85f)
                        } else {
                            MapBitmaps.createBookmarkPin(this, hex, scale = 0.85f)
                        }
                        previewCluster.addView(ImageView(this).apply {
                            setImageBitmap(bmp)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply {
                                if (pinIdx == 0) marginEnd = dp(4)
                            }
                        })
                    }
                    val lblView = TextView(this).apply {
                        text = styleOption.label
                        textSize = 12.5f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        gravity = Gravity.CENTER
                    }
                    styleCard.addView(previewCluster)
                    styleCard.addView(lblView)
                    styleCard.setOnClickListener {
                        if (selectedPinStyle != styleOption) {
                            selectedPinStyle = styleOption
                            syncPinStyleCards(popSelected = true)
                            DevicePrefs.setMapPinStyle(this, styleOption)
                            map?.style?.let { st ->
                                if (st.isFullyLoaded) {
                                    MapCartography.registerPinBitmaps(this, st)
                                    renderMapData()
                                }
                            }
                        }
                    }
                    pinCards += PinStyleCardUi(styleOption, styleCard, previewCluster, lblView)
                    pinRow.addView(styleCard)
                }
                syncPinStyleCards(popSelected = false)
                mapCard.addView(pinRow)

                addCardSwitch(
                    mapCard,
                    title = "Show Zoom Buttons",
                    subtitle = "Show slim frosted + / − pill above the right-side map arc (pinch zoom always active)",
                    checked = DevicePrefs.isShowZoomButtons(this)
                ) { enabled ->
                    DevicePrefs.setShowZoomButtons(this, enabled)
                    applyZoomButtonsVisibility()
                }

                addCardSwitch(
                    mapCard,
                    title = "Map Scale Bar",
                    subtitle = "Metric (m / km) or imperial distance reference on canvas",
                    checked = DevicePrefs.isScaleBarEnabled(this)
                ) { enabled ->
                    DevicePrefs.setScaleBarEnabled(this, enabled)
                    cachedScaleBarEnabled = enabled
                    lastScaleLabel = ""
                    lastScaleWidthPx = -1
                    updateScaleBar()
                }

                addCardSwitch(
                    mapCard,
                    title = "Cluster Dense Pins",
                    subtitle = "Group nearby pins into count badges when zoomed out for clearer overview panning",
                    checked = DevicePrefs.isClusterDensePins(this)
                ) { enabled ->
                    DevicePrefs.setClusterDensePins(this, enabled)
                    lastPinsRenderKey = null
                    renderMapData()
                }
                body.addView(mapCard)
            }

            SettingsSection.LANGUAGE_LABELS -> {
                val langCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 6)

                addSegmentedBlock(
                    langCard,
                    title = "App Language",
                    subtitle = "Interface labels with RTL layout for Kurdish & Arabic",
                    options = AppLanguage.entries.map { it.id to it.label },
                    selectedId = DevicePrefs.getAppLanguage(this).id
                ) { id ->
                    val next = AppLanguage.fromId(id)
                    if (DevicePrefs.getAppLanguage(this) != next) {
                        DevicePrefs.setAppLanguage(this, next)
                        reopenSettingsSection(SettingsSection.LANGUAGE_LABELS)
                    }
                }

                langCard.addView(DesignSystem.createHairlineDivider(this, p))
                langCard.addView(TextView(this).apply {
                    text = "Map Label Language & Script"
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    setPadding(0, dp(8), 0, dp(2))
                })
                val curMapLang = DevicePrefs.getMapLabelLanguage(this)
                val tvMapLangSub = TextView(this).apply {
                    text = curMapLang.subtitle
                    textSize = 12f
                    setTextColor(txtSub)
                    setPadding(0, 0, 0, dp(8))
                }
                langCard.addView(tvMapLangSub)

                val mapLangScroll = android.widget.HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                }
                val mapLangRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, dp(10))
                }
                val mapLangBtns = mutableMapOf<MapLabelLanguage, MaterialButton>()
                fun syncMapLangBtns(activeLang: MapLabelLanguage) {
                    tvMapLangSub.text = activeLang.subtitle
                    mapLangBtns.forEach { (l, b) -> DesignSystem.styleSegmentedButton(b, l == activeLang, p, onTonalCard = true) }
                }
                MapLabelLanguage.entries.forEachIndexed { idx, lang ->
                    val btn = MaterialButton(this).apply {
                        text = lang.label
                        textSize = 12.5f
                        isAllCaps = false
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(dp(12), 0, dp(12), 0)
                        cornerRadius = dp(12)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                            if (idx < MapLabelLanguage.entries.lastIndex) marginEnd = dp(6)
                        }
                        UiAnimations.attachSnappyPress(this)
                        setOnClickListener {
                            if (DevicePrefs.getMapLabelLanguage(this@MainActivity) == lang) return@setOnClickListener
                            DevicePrefs.setMapLabelLanguage(this@MainActivity, lang)
                            syncMapLangBtns(lang)
                            map?.style?.let { st ->
                                if (st.isFullyLoaded && mapStyleMode == MapStyleMode.VECTOR) {
                                    MapCartography.applyOrganicCartography(this@MainActivity, st, isDarkMapUi())
                                }
                            }
                        }
                    }
                    mapLangBtns[lang] = btn
                    mapLangRow.addView(btn)
                }
                syncMapLangBtns(curMapLang)
                mapLangScroll.addView(mapLangRow)
                langCard.addView(mapLangScroll)

                langCard.addView(DesignSystem.createHairlineDivider(this, p))
                langCard.addView(TextView(this).apply {
                    text = "POI & Icon Labels"
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    setPadding(0, dp(8), 0, dp(2))
                })
                langCard.addView(TextView(this).apply {
                    text = "Control text labels shown beneath map POI badges"
                    textSize = 12f
                    setTextColor(txtSub)
                    setPadding(0, 0, 0, dp(8))
                })

                val curPoiBehavior = DevicePrefs.getPoiLabelBehavior(this)
                val poiScroll = android.widget.HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                }
                val poiRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 0, 0, dp(10))
                }
                val poiBtns = mutableMapOf<PoiLabelBehavior, MaterialButton>()
                fun syncPoiBtns(activeBehavior: PoiLabelBehavior) {
                    poiBtns.forEach { (bBehavior, b) ->
                        DesignSystem.styleSegmentedButton(b, bBehavior == activeBehavior, p, onTonalCard = true)
                    }
                }
                PoiLabelBehavior.entries.forEachIndexed { idx, behavior ->
                    val btn = MaterialButton(this).apply {
                        text = behavior.label
                        textSize = 12.5f
                        isAllCaps = false
                        maxLines = 1
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(dp(11), 0, dp(11), 0)
                        cornerRadius = dp(12)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                            if (idx < PoiLabelBehavior.entries.lastIndex) marginEnd = dp(6)
                        }
                        UiAnimations.attachSnappyPress(this)
                        setOnClickListener {
                            if (DevicePrefs.getPoiLabelBehavior(this@MainActivity) == behavior) return@setOnClickListener
                            DevicePrefs.setPoiLabelBehavior(this@MainActivity, behavior)
                            syncPoiBtns(behavior)
                            map?.style?.let { st ->
                                if (st.isFullyLoaded && mapStyleMode == MapStyleMode.VECTOR) {
                                    MapCartography.applyOrganicCartography(this@MainActivity, st, isDarkMapUi())
                                }
                            }
                        }
                    }
                    poiBtns[behavior] = btn
                    poiRow.addView(btn)
                }
                syncPoiBtns(curPoiBehavior)
                poiScroll.addView(poiRow)
                langCard.addView(poiScroll)
                body.addView(langCard)
            }

            SettingsSection.NAVIGATION_ROUTING -> {
                val navCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 6)

                addSegmentedBlock(
                    navCard,
                    title = "Default Routing Mode",
                    subtitle = "Used for Route To previews and turn-by-turn navigation",
                    options = TravelMode.entries.map { it.id to it.label },
                    selectedId = travelMode.id,
                    activeColor = { p.accentPrimary }
                ) { id ->
                    val mode = TravelMode.fromId(id)
                    travelMode = mode
                    DevicePrefs.setTravelMode(this, mode)
                }

                navCard.addView(DesignSystem.createHairlineDivider(this, p))
                addSegmentedBlock(
                    navCard,
                    title = "Speed & Distance Units",
                    subtitle = "Applies to Speed HUD, scale bar, ruler, and distances",
                    options = SpeedUnit.entries.map { it.id to it.label },
                    selectedId = DevicePrefs.getSpeedUnit(this).id
                ) { id ->
                    val nextUnit = SpeedUnit.fromId(id)
                    DevicePrefs.setSpeedUnit(this, nextUnit)
                    cachedSpeedUnit = nextUnit
                    lastScaleLabel = ""
                    lastScaleWidthPx = -1
                    updateSpeedBadge(currentLocation)
                    updateScaleBar()
                    if (isRulerMode) updateRulerUi()
                }

                addCardSwitch(
                    navCard,
                    title = "Live Speed HUD",
                    subtitle = "Tabular speedometer readout while driving or navigating",
                    checked = DevicePrefs.isSpeedIndicatorEnabled(this)
                ) { enabled ->
                    DevicePrefs.setSpeedIndicatorEnabled(this, enabled)
                    updateSpeedBadge(currentLocation)
                }
                addCardSwitch(
                    navCard,
                    title = "Auto-Follow Live GPS",
                    subtitle = "Keep map centered on GPS arrow while moving",
                    checked = DevicePrefs.isAutoFollow(this)
                ) { DevicePrefs.setAutoFollow(this, it) }
                addCardSwitch(
                    navCard,
                    title = "Turn-by-Turn Voice Prompts",
                    subtitle = "Spoken maneuver guidance during active navigation",
                    checked = DevicePrefs.isVoiceNav(this)
                ) {
                    DevicePrefs.setVoiceNav(this, it)
                    btnNavVoice.alpha = if (it) 1.0f else 0.4f
                }
                addCardSwitch(
                    navCard,
                    title = "Keep Screen Awake",
                    subtitle = "Prevent display sleep during field surveys",
                    checked = DevicePrefs.isKeepAwake(this)
                ) {
                    DevicePrefs.setKeepAwake(this, it)
                    applyKeepAwakeFlag()
                }
                body.addView(navCard)
            }

            SettingsSection.FIELD_DATA_SYNC -> {
                val fieldCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 6)

                addCardSwitch(
                    fieldCard,
                    title = "Show Bookmark Titles on Map",
                    subtitle = "Display non-overlapping pin names beneath teardrop pins",
                    checked = DevicePrefs.isShowBookmarkTitles(this),
                    addDivider = false
                ) {
                    DevicePrefs.setShowBookmarkTitles(this, it)
                    renderMapData()
                }
                addCardSwitch(
                    fieldCard,
                    title = "Quick Map Tools",
                    subtitle = "Show Lasso circle-select tool on the map",
                    checked = DevicePrefs.isShowExtraMapTools(this)
                ) {
                    DevicePrefs.setShowExtraMapTools(this, it)
                    applyExtraMapToolsVisibility()
                }

                fieldCard.addView(DesignSystem.createHairlineDivider(this, p))

                val localCount = layers.count { LocalFolderStore.isLocalLayerId(it.id) }
                val cloudCount = layers.size - localCount
                fieldCard.addView(TextView(this).apply {
                    text = "Firebase Sync & KoboToolbox / KML"
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    setPadding(0, dp(10), 0, dp(2))
                })
                fieldCard.addView(TextView(this).apply {
                    val statusStr = if (isLiveConnected) "Cloud live connected" else "Offline cache active"
                    text = "$statusStr • $localCount local & $cloudCount cloud folders (${pins.size} pins)"
                    textSize = 12f
                    setTextColor(if (isLiveConnected) p.green else p.amber)
                    DesignSystem.applyTabularNumerals(this)
                    setPadding(0, 0, 0, dp(10))
                })

                val syncActionRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, dp(10))
                }
                syncActionRow.addView(MaterialButton(this).apply {
                    text = "Import KML"
                    textSize = 12.5f
                    isAllCaps = false
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(dp(8), 0, dp(8), 0)
                    setTextColor(Color.WHITE)
                    setBackgroundColor(p.accentPrimary)
                    elevation = 0f
                    stateListAnimator = null
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.05f).apply { marginEnd = dp(6) }
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        closeFullPage()
                        kmlPicker.launch(arrayOf("*/*"))
                    }
                })
                syncActionRow.addView(MaterialButton(this).apply {
                    text = "Bookmarks"
                    textSize = 12.5f
                    isAllCaps = false
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(dp(8), 0, dp(8), 0)
                    setBackgroundColor(p.actionDark)
                    setTextColor(p.onActionDark)
                    elevation = 0f
                    stateListAnimator = null
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.05f).apply { marginEnd = dp(6) }
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener { openFullPage(::buildBookmarksAndTracksPage) }
                })
                syncActionRow.addView(MaterialButton(this).apply {
                    text = "Sync"
                    textSize = 12.5f
                    isAllCaps = false
                    setSingleLine(true)
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(dp(12), 0, dp(12), 0)
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(0, dp(42), 0.72f)
                    DesignSystem.styleSegmentedButton(this, false, p, onTonalCard = true)
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        refreshJoinedSync()
                        Toast.makeText(this@MainActivity, "Syncing folders...", Toast.LENGTH_SHORT).show()
                    }
                })
                fieldCard.addView(syncActionRow)

                fieldCard.addView(DesignSystem.createHairlineDivider(this, p))

                // Working Hours Audit Window row
                val hoursRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(52)
                    setPadding(0, dp(8), 0, dp(8))
                }
                val hoursCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(10)
                    }
                }
                hoursCol.addView(TextView(this).apply {
                    text = "Working Hours Audit Window"
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                })
                val tvWorkWindow = TextView(this).apply {
                    val s = PinEditHistoryAudit.formatMinutes(DevicePrefs.getWorkStartMinutes(this@MainActivity))
                    val e = PinEditHistoryAudit.formatMinutes(DevicePrefs.getWorkEndMinutes(this@MainActivity))
                    text = "$s – $e • Flags after-hours pin edits"
                    textSize = 12f
                    setTextColor(txtSub)
                    DesignSystem.applyTabularNumerals(this)
                    setPadding(0, dp(2), 0, 0)
                }
                hoursCol.addView(tvWorkWindow)
                hoursRow.addView(hoursCol)
                hoursRow.addView(MaterialButton(this).apply {
                    text = "Change"
                    textSize = 12.5f
                    isAllCaps = false
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(14), 0, dp(14), 0)
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
                    DesignSystem.styleSegmentedButton(this, false, p, onTonalCard = true)
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        showWorkingHoursDialog {
                            val s = PinEditHistoryAudit.formatMinutes(DevicePrefs.getWorkStartMinutes(this@MainActivity))
                            val e = PinEditHistoryAudit.formatMinutes(DevicePrefs.getWorkEndMinutes(this@MainActivity))
                            tvWorkWindow.text = "$s – $e • Flags after-hours pin edits"
                        }
                    }
                })
                fieldCard.addView(hoursRow)

                fieldCard.addView(DesignSystem.createHairlineDivider(this, p))
                fieldCard.addView(TextView(this).apply {
                    text = "Field Operator Stamp"
                    textSize = 14.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    setPadding(0, dp(10), 0, dp(2))
                })
                val tvCurrentStamp = TextView(this).apply {
                    text = DevicePrefs.getDeviceStamp(this@MainActivity)
                    textSize = 12f
                    setTextColor(txtSub)
                    setPadding(0, 0, 0, dp(8))
                }
                fieldCard.addView(tvCurrentStamp)

                val nameRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, dp(8))
                }
                val etDeviceName = DesignSystem.createThemedEditText(
                    ctx = this,
                    palette = p,
                    hintText = "Display name (e.g. Team 4 / Supervisor)",
                    initialText = DevicePrefs.getCustomName(this@MainActivity),
                    singleLine = true,
                    onTonalCard = true
                ).apply {
                    textSize = 14f
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
                    setBackgroundColor(p.accentPrimary)
                    elevation = 0f
                    stateListAnimator = null
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        dp(42)
                    ).apply { marginStart = dp(8) }
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        DevicePrefs.setCustomName(this@MainActivity, etDeviceName.text.toString())
                        val updated = DevicePrefs.getDeviceStamp(this@MainActivity)
                        tvCurrentStamp.text = updated
                        Toast.makeText(this@MainActivity, "Updated: $updated", Toast.LENGTH_SHORT).show()
                    }
                })
                fieldCard.addView(nameRow)
                body.addView(fieldCard)
            }

            SettingsSection.OFFLINE_CACHE -> {
                body.addView(TextView(this).apply {
                    text = "Each offline map pack bundles vector tiles and embedded turn-by-turn road routing."
                    textSize = 12.5f
                    setTextColor(txtSub)
                    setPadding(dp(4), 0, dp(4), dp(10))
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
                    val card = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 12)
                    val topRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                    }
                    val iconBadge = FrameLayout(this).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                            marginEnd = dp(10)
                        }
                    }
                    val iconView = ImageView(this).apply {
                        setImageResource(R.drawable.ic_map)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
                    }
                    iconBadge.addView(iconView)
                    topRow.addView(iconBadge)

                    topRow.addView(TextView(this).apply {
                        text = title
                        textSize = 15.5f
                        setTextColor(txtPrimary)
                        setTypeface(null, Typeface.BOLD)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(8)
                        }
                    })
                    val tvStatusPill = TextView(this).apply {
                        textSize = 10.5f
                        setTypeface(null, Typeface.BOLD)
                        setPadding(dp(8), dp(3), dp(8), dp(3))
                        DesignSystem.applyTabularNumerals(this)
                    }
                    topRow.addView(tvStatusPill)
                    card.addView(topRow)

                    card.addView(TextView(this).apply {
                        text = subtitle
                        textSize = 12f
                        setTextColor(txtSub)
                        setPadding(0, dp(6), 0, dp(8))
                    })

                    val chipRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 0, 0, dp(4))
                    }
                    fun makePackChip(initialText: String, addEndGap: Boolean): TextView {
                        return TextView(this).apply {
                            text = initialText
                            textSize = 11f
                            setTypeface(null, Typeface.BOLD)
                            setPadding(dp(8), dp(3), dp(8), dp(3))
                            DesignSystem.applyTabularNumerals(this)
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                if (addEndGap) marginEnd = dp(6)
                            }
                        }
                    }
                    val tvTileChip = makePackChip("Vector Tiles", addEndGap = true)
                    val tvRoadChip = makePackChip("Road Graph", addEndGap = false)
                    chipRow.addView(tvTileChip)
                    chipRow.addView(tvRoadChip)
                    card.addView(chipRow)

                    val tvStatus = TextView(this).apply {
                        textSize = 12.5f
                        setTextColor(p.textSecondary)
                        DesignSystem.applyTabularNumerals(this)
                        visibility = View.GONE
                        setPadding(0, dp(4), 0, 0)
                    }
                    card.addView(tvStatus)

                    val tvRoadStatus = TextView(this).apply {
                        textSize = 12f
                        setTextColor(p.textSecondary)
                        DesignSystem.applyTabularNumerals(this)
                        visibility = View.GONE
                        setPadding(0, dp(3), 0, 0)
                    }
                    card.addView(tvRoadStatus)

                    val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 100
                        visibility = View.GONE
                        progressTintList = android.content.res.ColorStateList.valueOf(p.accentPrimary)
                        indeterminateTintList = android.content.res.ColorStateList.valueOf(p.accentPrimary)
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = dp(6) }
                    }
                    card.addView(progressBar)

                    val btnRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(10), 0, 0)
                    }
                    val btnDl = MaterialButton(this).apply {
                        textSize = 12.5f
                        setTypeface(null, Typeface.BOLD)
                        isAllCaps = false
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(dp(12), 0, dp(12), 0)
                        setTextColor(Color.WHITE)
                        backgroundTintList = android.content.res.ColorStateList.valueOf(p.accentPrimary)
                        cornerRadius = dp(12)
                        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
                        UiAnimations.attachSnappyPress(this)
                    }
                    val btnDel = MaterialButton(this).apply {
                        text = "Delete"
                        textSize = 12.5f
                        setTypeface(null, Typeface.BOLD)
                        isAllCaps = false
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(dp(12), 0, dp(12), 0)
                        setTextColor(p.red)
                        backgroundTintList = android.content.res.ColorStateList.valueOf(p.redSoft)
                        strokeWidth = dp(1)
                        strokeColor = android.content.res.ColorStateList.valueOf(
                            Color.argb(if (p.isDark) 76 else 56, Color.red(p.red), Color.green(p.red), Color.blue(p.red))
                        )
                        cornerRadius = dp(12)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42))
                    }
                    val btnRetryRoads = MaterialButton(this).apply {
                        text = "Retry Roads"
                        textSize = 12.5f
                        isAllCaps = false
                        maxLines = 1
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(dp(12), 0, dp(12), 0)
                        cornerRadius = dp(12)
                        DesignSystem.styleSegmentedButton(this, false, p, onTonalCard = true)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply {
                            topMargin = dp(8)
                        }
                        visibility = View.GONE
                        UiAnimations.attachSnappyPress(this)
                    }

                    fun refreshCard() {
                        val st = offlineMaps.getState(packId)
                        val busy = st.isDownloading || st.isRoadDownloading
                        val fullyReady = st.isDownloaded && st.hasRoadGraph
                        val hasPartialTiles = !st.isDownloaded && st.completedResources > 0L
                        val hasAnyContent = st.hasAnyData
                        val sizeStr = offlineMaps.formatSize(st.sizeBytes)

                        val (pillLabel, pillFg, pillBg) = when {
                            st.mapError != null && !st.isDownloaded -> Triple("Tile Error", p.red, p.redSoft)
                            st.roadError != null && !st.hasRoadGraph -> Triple("Roads Error", p.red, p.redSoft)
                            busy -> Triple("Downloading", p.accentPrimary, p.accentPrimarySoft)
                            fullyReady -> Triple("Offline Ready", p.green, p.greenSoft)
                            st.isDownloaded || st.hasRoadGraph || hasPartialTiles -> Triple("Partial", p.amber, p.amberSoft)
                            else -> Triple("Online Only", p.textMuted, p.surfaceFloating)
                        }
                        tvStatusPill.text = pillLabel
                        tvStatusPill.setTextColor(pillFg)
                        tvStatusPill.background = DesignSystem.buildOpaqueDrawable(
                            ctx = this@MainActivity,
                            fillColor = pillBg,
                            radiusDp = 10f,
                            strokeColor = if (!hasAnyContent && !busy && st.mapError == null && st.roadError == null) p.borderSubtle else Color.TRANSPARENT,
                            strokeDp = 1f
                        )
                        iconBadge.background = DesignSystem.buildOpaqueDrawable(
                            ctx = this@MainActivity,
                            fillColor = if (fullyReady || busy) p.accentPrimarySoft else p.surfaceFloating,
                            radiusDp = 10f,
                            strokeColor = p.borderSubtle,
                            strokeDp = if (fullyReady || busy) 0f else 1f
                        )
                        iconView.setColorFilter(if (fullyReady || busy) p.accentPrimary else p.textMuted)

                        fun styleChip(chip: TextView, label: String, ready: Boolean, failed: Boolean) {
                            chip.text = label
                            chip.setTextColor(
                                when {
                                    failed -> p.red
                                    ready -> p.green
                                    else -> p.textMuted
                                }
                            )
                            chip.background = DesignSystem.buildOpaqueDrawable(
                                ctx = this@MainActivity,
                                fillColor = when {
                                    failed -> p.redSoft
                                    ready -> p.greenSoft
                                    else -> p.surfaceFloating
                                },
                                radiusDp = 8f,
                                strokeColor = if (!ready && !failed) p.borderSubtle else Color.TRANSPARENT,
                                strokeDp = 1f
                            )
                        }
                        styleChip(
                            tvTileChip,
                            when {
                                st.isDownloaded -> "Tiles • $sizeStr"
                                hasPartialTiles -> "Tiles • ${st.progressPercent}%"
                                else -> "Vector Tiles"
                            },
                            ready = st.isDownloaded,
                            failed = st.mapError != null && !st.isDownloaded
                        )
                        styleChip(
                            tvRoadChip,
                            when {
                                st.hasRoadGraph -> "Roads • ${offlineMaps.formatSize(st.roadSizeBytes)}"
                                st.isRoadQueued -> "Roads • Queued"
                                else -> "Road Graph"
                            },
                            ready = st.hasRoadGraph,
                            failed = st.roadError != null && !st.hasRoadGraph
                        )

                        val (mapText, mapColor, mapBold) = when {
                            st.mapError != null && !st.isDownloaded ->
                                Triple("Map Tiles: ${st.mapError}", p.red, true)
                            st.isDownloading -> {
                                val countPart = if (st.requiredResources > 0) " (${st.completedResources}/${st.requiredResources})" else ""
                                val sizePart = if (st.sizeBytes > 0L) " • $sizeStr" else ""
                                Triple("Map Tiles: Downloading ${st.progressPercent}%$countPart$sizePart", p.accentPrimary, true)
                            }
                            hasPartialTiles -> {
                                val countPart = if (st.requiredResources > 0) " (${st.completedResources}/${st.requiredResources})" else ""
                                Triple("Map Tiles: Paused ${st.progressPercent}%$countPart • $sizeStr", if (p.isDark) p.amber else Color.parseColor("#B36B00"), false)
                            }
                            else -> Triple("", p.textSecondary, false)
                        }
                        if (mapText.isNotEmpty()) {
                            tvStatus.text = mapText
                            tvStatus.setTextColor(mapColor)
                            tvStatus.setTypeface(null, if (mapBold) Typeface.BOLD else Typeface.NORMAL)
                            tvStatus.visibility = View.VISIBLE
                        } else {
                            tvStatus.visibility = View.GONE
                        }

                        if (st.roadError != null || st.isRoadDownloading || st.isRoadQueued) {
                            val (roadText, roadColor, roadBold) = when {
                                st.roadError != null ->
                                    Triple("Roads failed: ${st.roadError}", p.red, true)
                                st.isRoadDownloading ->
                                    Triple("Road Routing: ${st.roadStatusText.ifBlank { "Downloading..." }}", p.accentPrimary, true)
                                else ->
                                    Triple("Road Routing: Queued after map tiles finish", p.textSecondary, false)
                            }
                            tvRoadStatus.text = roadText
                            tvRoadStatus.setTextColor(roadColor)
                            tvRoadStatus.setTypeface(null, if (roadBold) Typeface.BOLD else Typeface.NORMAL)
                            tvRoadStatus.visibility = View.VISIBLE
                        } else {
                            tvRoadStatus.visibility = View.GONE
                        }

                        val showProgress = busy || hasPartialTiles
                        progressBar.visibility = if (showProgress) View.VISIBLE else View.GONE
                        val activeTint = if (busy) p.accentPrimary else p.textMuted
                        progressBar.progressTintList = android.content.res.ColorStateList.valueOf(activeTint)
                        progressBar.indeterminateTintList = android.content.res.ColorStateList.valueOf(activeTint)
                        if (st.isDownloading) {
                            progressBar.isIndeterminate = st.requiredResources <= 0L
                            progressBar.setProgress(st.progressPercent, !UiAnimations.isReducedMotion(this@MainActivity))
                        } else if (st.isRoadDownloading) {
                            progressBar.isIndeterminate = st.roadProgressPercent <= 0
                            if (st.roadProgressPercent > 0) {
                                progressBar.setProgress(st.roadProgressPercent, !UiAnimations.isReducedMotion(this@MainActivity))
                            }
                        } else {
                            progressBar.isIndeterminate = false
                            progressBar.progress = st.progressPercent
                        }

                        btnDl.text = when {
                            busy -> "Pause"
                            fullyReady -> "Map + Routing Ready"
                            st.isDownloaded && !st.hasRoadGraph && st.roadError != null -> "Retry Roads"
                            st.isDownloaded && !st.hasRoadGraph -> "Download Offline Roads"
                            hasPartialTiles -> "Resume Download"
                            else -> "Download Map + Roads"
                        }
                        btnDl.isEnabled = !fullyReady || busy
                        val dlBg = if (btnDl.isEnabled) p.accentPrimary else p.accentPrimarySoft
                        val dlFg = if (btnDl.isEnabled) Color.WHITE else p.accentPrimary
                        btnDl.backgroundTintList = android.content.res.ColorStateList.valueOf(dlBg)
                        btnDl.setTextColor(dlFg)
                        btnDl.alpha = 1f

                        val showDelete = hasAnyContent || st.roadError != null || st.mapError != null
                        btnDel.visibility = if (showDelete) View.VISIBLE else View.GONE
                        (btnDl.layoutParams as LinearLayout.LayoutParams).marginEnd = if (showDelete) dp(8) else 0

                        val showSeparateRoadRetry = !st.isDownloaded && !st.hasRoadGraph && !busy &&
                            (st.roadError != null || hasPartialTiles)
                        btnRetryRoads.visibility = if (showSeparateRoadRetry) View.VISIBLE else View.GONE
                        btnRetryRoads.text = if (st.roadError != null) "Retry Roads Only" else "Download Roads Only"
                    }

                    btnDl.setOnClickListener {
                        val st = offlineMaps.getState(packId)
                        when {
                            st.isDownloading || st.isRoadDownloading -> offlineMaps.pauseDownload(packId)
                            st.isDownloaded && !st.hasRoadGraph -> onDownloadRoadsOnly()
                            else -> onDownload()
                        }
                    }
                    btnRetryRoads.setOnClickListener {
                        onDownloadRoadsOnly()
                    }
                    btnDel.setOnClickListener {
                        val st = offlineMaps.getState(packId)
                        if (!st.hasAnyData && st.roadError == null && st.mapError == null) {
                            return@setOnClickListener
                        }
                        showThemedDialog(
                            themedDialogBuilder("Delete \"$title\" Offline Pack?")
                                .setMessage("This removes downloaded offline map tiles, partial road files, and offline routing data for $title from this phone.")
                                .setPositiveButton("Delete") { _, _ ->
                                    offlineMaps.deletePack(packId) { msg ->
                                        runOnUiThread { Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show() }
                                    }
                                }
                                .setNegativeButton("Cancel", null)
                                .create(),
                            destructivePositive = true
                        )
                    }

                    btnRow.addView(btnDl)
                    btnRow.addView(btnDel)
                    card.addView(btnRow)
                    card.addView(btnRetryRoads)
                    body.addView(card)

                    refreshCard()
                    packUpdaters += ::refreshCard
                }

                OfflineMapManager.REGIONAL_PACKS.forEach { pack ->
                    addOfflineCard(
                        pack.id,
                        pack.title,
                        pack.subtitle,
                        onDownload = {
                            ensureNotificationPermissionForDownloads()
                            offlineMaps.downloadPack(pack, pixelRatio)
                        },
                        onDownloadRoadsOnly = {
                            ensureNotificationPermissionForDownloads()
                            offlineMaps.downloadRoadGraphOnly(pack)
                        }
                    )
                }

                val tvCacheSize = TextView(this).apply {
                    text = "…"
                    textSize = 11.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(p.textSecondary)
                    DesignSystem.applyTabularNumerals(this)
                    setPadding(dp(9), dp(4), dp(9), dp(4))
                    background = DesignSystem.buildOpaqueDrawable(
                        ctx = this@MainActivity,
                        fillColor = p.surfaceFloating,
                        radiusDp = 10f,
                        strokeColor = p.borderSubtle,
                        strokeDp = 1f
                    )
                }

                fun refreshCacheSize() {
                    RoutingEngine.ioExecutor.execute {
                        val bytes = offlineMaps.estimateAmbientCacheBytes()
                        val label = offlineMaps.formatSize(bytes)
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                tvCacheSize.text = label
                            }
                        }
                    }
                }

                val cacheCard = DesignSystem.createGroupedCard(
                    this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 12, bottomMarginDp = 4
                ).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    UiAnimations.attachSnappyPress(this, 0.985f)
                    setOnClickListener {
                        offlineMaps.clearAmbientCache { msg ->
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                                refreshCacheSize()
                            }
                        }
                    }
                }
                val trashBadge = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) }
                    background = DesignSystem.buildOpaqueDrawable(
                        ctx = this@MainActivity,
                        fillColor = p.surfaceFloating,
                        radiusDp = 18f,
                        strokeColor = p.borderSubtle,
                        strokeDp = 1f
                    )
                }
                trashBadge.addView(ImageView(this).apply {
                    setImageResource(R.drawable.ic_trash)
                    setColorFilter(p.textMuted)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
                })
                cacheCard.addView(trashBadge)

                val cacheTextCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(8)
                    }
                }
                cacheTextCol.addView(TextView(this).apply {
                    text = "Clear Temporary Map Cache"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                })
                cacheTextCol.addView(TextView(this).apply {
                    text = "Free cached map tiles from online browsing"
                    textSize = 12f
                    setTextColor(txtSub)
                })
                cacheCard.addView(cacheTextCol)
                cacheCard.addView(tvCacheSize)
                body.addView(cacheCard)
                refreshCacheSize()

                offlineMaps.setStateListener {
                    runOnUiThread { packUpdaters.forEach { it() } }
                }
                offlineMaps.refreshAll()
                activePageCleanup = { offlineMaps.setStateListener(null) }
            }

            SettingsSection.ABOUT_BUILD -> {
                val aboutCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 6)

                addCardSwitch(
                    aboutCard,
                    title = "Reduce Motion",
                    subtitle = "Disable slide transitions for instant screen swaps",
                    checked = DevicePrefs.isReducedMotion(this),
                    addDivider = false
                ) { DevicePrefs.setReducedMotion(this, it) }

                aboutCard.addView(DesignSystem.createHairlineDivider(this, p))
                aboutCard.addView(TextView(this).apply {
                    text = "TerraSync v${getInstalledVersionName()} • MapLibre GL"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    DesignSystem.applyTabularNumerals(this)
                    setPadding(0, dp(10), 0, dp(2))
                })
                aboutCard.addView(TextView(this).apply {
                    val diag = "Device #${DevicePrefs.getDeviceId(this@MainActivity)} • ${layers.size} folders • ${pins.size} pins • ${tracks.size} tracks"
                    text = diag
                    textSize = 12f
                    setTextColor(txtSub)
                    DesignSystem.applyTabularNumerals(this)
                    setPadding(0, 0, 0, dp(10))
                })
                aboutCard.addView(MaterialButton(this).apply {
                    text = "Open About & Diagnostics"
                    textSize = 13f
                    isAllCaps = false
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(12)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
                        bottomMargin = dp(8)
                    }
                    DesignSystem.styleSegmentedButton(this, false, p, onTonalCard = true)
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener { openFullPage(::buildAboutPage) }
                })
                body.addView(aboutCard)
            }
        }

        return outer
    }

    // --- Page 4: About Page ---

    private fun buildAboutPage(): View {
        val p = currentPalette()
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary

        val (outer, body) = makePageFrame(AppStrings.about(this), { closeFullPage() })

        val heroCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 16, paddingVDp = 16, bottomMarginDp = 12)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(12))
        }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_app_icon)
            layoutParams = LinearLayout.LayoutParams(dp(58), dp(58)).apply { marginEnd = dp(14) }
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat() }
            clipToOutline = true
        })
        val heroText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heroText.addView(TextView(this).apply {
            text = "TerraSync"
            textSize = 22f
            letterSpacing = -0.01f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
        })
        heroText.addView(TextView(this).apply {
            text = "Version ${getInstalledVersionName()} • Kotlin + MapLibre GL"
            textSize = 12.5f
            setTextColor(p.green)
            setTypeface(null, Typeface.BOLD)
            DesignSystem.applyTabularNumerals(this)
        })
        hero.addView(heroText)
        heroCard.addView(hero)
        heroCard.addView(TextView(this).apply {
            text = "Minimalist field mapping built with Organic Maps-inspired cartography: shaded relief, elevation contours, multilingual English/Kurdish/Arabic labels, Valhalla + embedded offline turn-by-turn routing, status-preserving KML/KMZ round-tripping, Firebase sync, and Android Auto support."
            textSize = 13.5f
            setLineSpacing(dp(2).toFloat(), 1.05f)
            setTextColor(txtSub)
        })
        body.addView(heroCard)

        val devCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 16, paddingVDp = 14, bottomMarginDp = 14)
        devCard.addView(TextView(this).apply {
            text = "DEVELOPER & DIAGNOSTICS"
            textSize = 11.5f
            letterSpacing = 0.06f
            setTypeface(null, Typeface.BOLD)
            setTextColor(txtSub)
        })
        devCard.addView(TextView(this).apply {
            text = "Thamir (@Thamirakkurd)"
            textSize = 16.5f
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(3), 0, dp(6))
        })
        devCard.addView(TextView(this).apply {
            text = "Device Stamp: ${DevicePrefs.getDeviceStamp(this@MainActivity)}"
            textSize = 12f
            setTextColor(txtSub)
        })
        devCard.addView(TextView(this).apply {
            val fLabel = if (layers.size == 1) "folder" else "folders"
            val pLabel = if (pins.size == 1) "bookmark" else "bookmarks"
            val tLabel = if (tracks.size == 1) "track" else "tracks"
            text = "Data: ${layers.size} $fLabel • ${pins.size} $pLabel • ${tracks.size} $tLabel • ${if (isLiveConnected) "Cloud live" else "Offline cache"}"
            textSize = 12f
            setTextColor(txtSub)
            DesignSystem.applyTabularNumerals(this)
            setPadding(0, dp(4), 0, 0)
        })
        body.addView(devCard)

        val linksCard = DesignSystem.createGroupedCard(
            this, p, radiusDp = 16f, paddingHDp = 0, paddingVDp = 2, bottomMarginDp = 14
        )
        val linkSpecs = listOf(
            listOf("GitHub Profile", "@Thamirakkurd", "https://github.com/Thamirakkurd", R.drawable.ic_info),
            listOf("TerraSync Repository", "Source code & issue tracker", "https://github.com/Thamirakkurd/TerraSync", R.drawable.ic_layers),
            listOf("Releases & Updates", "Latest signed APK builds", "https://github.com/Thamirakkurd/TerraSync/releases", R.drawable.ic_download)
        )
        linkSpecs.forEachIndexed { idx, spec ->
            if (idx > 0) {
                linksCard.addView(DesignSystem.createHairlineDivider(this, p, 62, 14))
            }
            val titleText = spec[0] as String
            val subText = spec[1] as String
            val url = spec[2] as String
            val iconRes = spec[3] as Int

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(58)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                UiAnimations.applyRowRipple(this@MainActivity, this, p.isDark)
                UiAnimations.attachSnappyPress(this, 0.985f)
                setOnClickListener {
                    safeStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No browser app installed")
                }
            }
            val badge = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(p.accentPrimarySoft)
                }
            }
            badge.addView(ImageView(this).apply {
                setImageResource(iconRes)
                setColorFilter(p.accentPrimary)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = FrameLayout.LayoutParams(dp(18), dp(18), Gravity.CENTER)
            })
            row.addView(badge)

            val txtCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = dp(8)
                }
            }
            txtCol.addView(TextView(this).apply {
                text = titleText
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(txtPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            txtCol.addView(TextView(this).apply {
                text = subText
                textSize = 12f
                setTextColor(txtSub)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            row.addView(txtCol)

            row.addView(TextView(this).apply {
                text = "›"
                textSize = 20f
                setTextColor(p.textMuted)
            })
            linksCard.addView(row)
        }
        body.addView(linksCard)

        UiAnimations.staggerRevealChildren(body)
        return outer
    }

    // --- Field Quick-Select Bottom Sheet (Organic Maps Style: Done / Issue Presets) ---

    private fun showFieldQuickSelectSheet(pin: PinItem, isIssue: Boolean) {
        sheetQuickSelectPinId = pin.id
        val p = currentPalette()
        val night = p.isDark
        val accentColor = if (isIssue) p.red else p.green
        val presets = if (isIssue) ISSUE_PRESETS else DONE_PRESETS
        val statusKey = if (isIssue) "issue" else "done"

        sheetContent.removeAllViews()

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(p.textPrimary)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            UiAnimations.applyRowRipple(this@MainActivity, this, night)
            setOnClickListener {
                val fresh = pinById[pin.id] ?: pin
                showPinCard(fresh, animateSwap = true, swapForward = false)
            }
        })
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
            }
        }
        titleCol.addView(TextView(this).apply {
            text = if (isIssue) "Report Field Issue" else "Mark as Done"
            textSize = 19f
            letterSpacing = -0.01f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.textPrimary)
        })
        titleCol.addView(TextView(this).apply {
            text = pin.name
            textSize = 12.5f
            setTextColor(p.textSecondary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        header.addView(titleCol)
        header.addView(DesignSystem.createSheetCloseButton(this, p) { hideInScreenSheet() })
        sheetContent.addView(header)

        val etReason = EditText(this).apply {
            hint = if (isIssue) "Type custom issue details..." else "Optional completion note..."
            textSize = 14f
            setTextColor(p.textPrimary)
            setHintTextColor(p.textSecondary)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 1
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            if (PinStatus.fromRaw(pin.status).key == statusKey) {
                setText(pin.issueReason)
            }
            background = GradientDrawable().apply {
                setColor(p.cardSurface)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), p.borderSubtle)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        fun keepReasonVisibleAboveKeyboard() {
            if (sheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            sheetScrollView.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    sheetScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }, 140L)
        }
        etReason.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) keepReasonVisibleAboveKeyboard()
        }
        etReason.setOnClickListener { keepReasonVisibleAboveKeyboard() }

        var committed = false
        fun commitSelection(noteText: String) {
            if (committed) return
            val clean = noteText.trim()
            if (isIssue && clean.isBlank()) {
                Toast.makeText(this, "Please select or enter the issue", Toast.LENGTH_SHORT).show()
                return
            }
            committed = true
            val stamp = DevicePrefs.getDeviceStamp(this)
            savePinStatusUnified(pin, statusKey, stamp, clean)
            fireStatusPulse(pin.lat, pin.lng, statusColorHex(statusKey))
            renderMapData()
            val toastMsg = if (isIssue) {
                "Marked Issue: $clean"
            } else if (clean.isNotBlank()) {
                "Marked Done: $clean"
            } else {
                "Marked Done"
            }
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show()
            hideInScreenSheet()
        }

        // Grouped rounded card of 1-tap presets (48dp touch rows)
        val presetsCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 0, paddingVDp = 2, bottomMarginDp = 10)
        presets.forEachIndexed { idx, preset ->
            if (idx > 0) {
                presetsCard.addView(DesignSystem.createHairlineDivider(this, p, 14, 14))
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                UiAnimations.applyRowRipple(this@MainActivity, this, night)
                setOnClickListener { commitSelection(preset) }
            }
            row.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColor)
                }
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(12) }
            })
            row.addView(TextView(this).apply {
                text = preset
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = "Tap"
                textSize = 12f
                setTextColor(p.accent)
                setTypeface(null, Typeface.BOLD)
            })
            presetsCard.addView(row)
        }
        sheetContent.addView(presetsCard)
        sheetContent.addView(etReason)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
        }
        btnRow.addView(MaterialButton(this).apply {
            text = "Cancel"
            textSize = 13.5f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 0.8f).apply { marginEnd = dp(8) }
            DesignSystem.styleSegmentedButton(this, false, p)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val fresh = pinById[pin.id] ?: pin
                showPinCard(fresh, animateSwap = true, swapForward = false)
            }
        })
        btnRow.addView(MaterialButton(this).apply {
            text = if (isIssue) "Save Issue" else "Save Done"
            textSize = 13.5f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            setTextColor(Color.WHITE)
            setBackgroundColor(accentColor)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1.3f)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { commitSelection(etReason.text.toString()) }
        })
        sheetContent.addView(btnRow)

        UiAnimations.animateContentSwap(sheetContent)
        showInScreenSheet(peekDp = 420, inPlace = true, expandToFit = true)
    }

    private fun showEditPinDialog(pin: PinItem) {
        val p = currentPalette()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val etName = DesignSystem.createThemedEditText(this, p, "Pin name", initialText = pin.name).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10)
        }
        val etDesc = DesignSystem.createThemedEditText(this, p, "Description / notes", initialText = pin.description, singleLine = false, minLines = 2)
        box.addView(etName)
        box.addView(etDesc)

        showThemedDialog(
            themedDialogBuilder("Edit Pin")
                .setView(box)
                .setPositiveButton("Save") { _, _ ->
                    val stamp = DevicePrefs.getDeviceStamp(this)
                    savePinTextUnified(pin, etName.text.toString().trim(), etDesc.text.toString().trim(), stamp)
                    Toast.makeText(this, "Saved & stamped", Toast.LENGTH_SHORT).show()
                    hideInScreenSheet()
                }
                .setNegativeButton("Cancel", null)
                .create()
        )
    }

    private fun showAddPinDialog(latLng: LatLng, prefillName: String = "", prefillDesc: String = "") {
        val p = currentPalette()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        box.addView(TextView(this).apply {
            text = "${"%.6f".format(Locale.US, latLng.latitude)}, ${"%.6f".format(Locale.US, latLng.longitude)}"
            textSize = 12.5f
            setTextColor(p.textSecondary)
            DesignSystem.applyTabularNumerals(this)
            setPadding(dp(2), 0, 0, dp(10))
        })
        val etName = DesignSystem.createThemedEditText(this, p, "Pin name", initialText = prefillName).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10)
        }
        val etDesc = DesignSystem.createThemedEditText(this, p, "Description (optional)", initialText = prefillDesc, singleLine = false, minLines = 2)
        box.addView(etName)
        box.addView(etDesc)

        showThemedDialog(
            themedDialogBuilder("Save Bookmark")
                .setView(box)
                .setPositiveButton("Save") { _, _ ->
                    val stamp = DevicePrefs.getDeviceStamp(this)
                    val devId = DevicePrefs.getDeviceId(this)
                    val savedPin = LocalFolderStore.addManualPin(
                        this,
                        etName.text.toString().trim(),
                        etDesc.text.toString().trim(),
                        latLng.latitude,
                        latLng.longitude,
                        stamp,
                        devId
                    )
                    DevicePrefs.setLayerVisible(this, savedPin.layerId, true)
                    ensurePinVisibleOnMap(savedPin)
                    applyMergedFoldersAndRender()
                    fireStatusPulse(savedPin.lat, savedPin.lng, "#F59E0B")
                    Toast.makeText(this, "Saved \"${savedPin.name}\" locally", Toast.LENGTH_SHORT).show()
                    hideInScreenSheet()
                }
                .setNegativeButton("Cancel", null)
                .create()
        )
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
        val p = currentPalette()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val input = DesignSystem.createThemedEditText(this, p, "Enter 6-character code (e.g. AKR742)").apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        box.addView(input)
        showThemedDialog(
            themedDialogBuilder("Join Shared Folder")
                .setView(box)
                .setPositiveButton("Join") { _, _ -> joinShareCode(input.text.toString()) }
                .setNegativeButton("Cancel", null)
                .create()
        )
    }

    private fun showCombineFoldersDialog() {
        if (layers.size < 2) {
            Toast.makeText(this, "Need at least 2 folders to combine", Toast.LENGTH_SHORT).show()
            return
        }
        val p = currentPalette()
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(8))
        }

        val etName = DesignSystem.createThemedEditText(this, p, "New combined folder name")
        box.addView(etName)

        box.addView(TextView(this).apply {
            text = "Select folders to combine:"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.textSecondary)
            setPadding(0, dp(14), 0, dp(6))
        })

        val selectedIds = mutableSetOf<String>()
        val cbTint = android.content.res.ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf(-android.R.attr.state_checked)),
            intArrayOf(p.accent, p.iconMuted)
        )
        layers.forEach { layer ->
            val count = pinsByLayerId[layer.id]?.size ?: 0
            val cb = CheckBox(this).apply {
                text = "${layer.name} ($count pins)"
                textSize = 14f
                setTextColor(p.textPrimary)
                buttonTintList = cbTint
                setPadding(dp(4), dp(6), dp(4), dp(6))
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selectedIds.add(layer.id) else selectedIds.remove(layer.id)
                }
            }
            box.addView(cb)
        }

        box.addView(View(this).apply {
            setBackgroundColor(p.divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(8)
                bottomMargin = dp(8)
            }
        })

        val cbDedup = CheckBox(this).apply {
            text = "Deduplicate matching pins (same name & GPS, keep latest)"
            textSize = 13f
            setTextColor(p.textSecondary)
            buttonTintList = cbTint
            isChecked = true
        }
        box.addView(cbDedup)

        val cbHideOriginals = CheckBox(this).apply {
            text = "Hide original folders on map"
            textSize = 13f
            setTextColor(p.textSecondary)
            buttonTintList = cbTint
            isChecked = true
        }
        box.addView(cbHideOriginals)

        scroll.addView(box)

        val dlg = themedDialogBuilder("Combine Folders")
            .setView(scroll)
            .setPositiveButton("Combine", null)
            .setNegativeButton("Cancel", null)
            .create()

        dlg.setOnShowListener {
            DesignSystem.styleAlertDialog(this, dlg, p)
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
                    DailyReportGenerator.deduplicatePins(rawPins)
                } else {
                    rawPins
                }

                if (finalPins.isEmpty() && rawTracks.isEmpty()) {
                    Toast.makeText(this, "Selected folders have no pins or tracks", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newLayerId = LocalFolderStore.newLocalLayerId()
                val stamp = DevicePrefs.getDeviceStamp(this)
                val devId = DevicePrefs.getDeviceId(this)
                val skippedCount = rawPins.size - finalPins.size
                val uniqueName = makeUniqueLayerName(combinedName)
                val parsedCombined = ParsedKml(
                    pins = finalPins.map { it.copy(id = UUID.randomUUID().toString(), layerId = newLayerId) },
                    tracks = rawTracks.map { it.copy(id = UUID.randomUUID().toString(), layerId = newLayerId) },
                    documentName = uniqueName
                )
                LocalFolderStore.saveNewLayer(this, newLayerId, uniqueName, stamp, devId, parsedCombined)
                DevicePrefs.addCachedLayerName(this, uniqueName)
                DevicePrefs.setLayerVisible(this, newLayerId, true)
                if (cbHideOriginals.isChecked) {
                    selectedIds.forEach { oldId ->
                        DevicePrefs.setLayerVisible(this, oldId, false)
                    }
                }
                applyMergedFoldersAndRender()
                val skipNote = if (skippedCount > 0) " ($skippedCount duplicates skipped)" else ""
                Toast.makeText(this, "Combined ${finalPins.size} pins locally into \"$uniqueName\"$skipNote", Toast.LENGTH_LONG).show()
                dlg.dismiss()
            }
        }
        dlg.show()
    }

    // --- Silent Local KML Import & Firestore Sync ---

    private fun handleImportUri(uri: Uri) {
        val fileName = getDisplayName(uri).substringBeforeLast('.')
        val localId = LocalFolderStore.newLocalLayerId()
        val stamp = DevicePrefs.getDeviceStamp(this)
        val devId = DevicePrefs.getDeviceId(this)
        val existingNameKeys = buildSet {
            layers.forEach { add(normalizeLayerNameKey(it.name)) }
            DevicePrefs.getCachedLayerNames(this@MainActivity).forEach { add(normalizeLayerNameKey(it)) }
        }
        val appCtx = applicationContext

        RoutingEngine.ioExecutor.execute {
            val parsed = runCatching {
                contentResolver.openInputStream(uri)?.use { KmlParser.parse(it, localId) }
            }.getOrNull()

            if (parsed == null || (parsed.pins.isEmpty() && parsed.tracks.isEmpty())) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) {
                        Toast.makeText(this, "No pins or tracks found in file", Toast.LENGTH_SHORT).show()
                    }
                }
                return@execute
            }

            val rawDocName = parsed.documentName.trim()
            val preferredBase = when {
                rawDocName.isNotBlank() && !rawDocName.equals("Document", true) && !rawDocName.equals("KML", true) -> rawDocName
                fileName.isNotBlank() -> fileName.replace('_', ' ').trim()
                else -> "Imported Folder"
            }.ifBlank { "Imported Folder" }

            val chosenName = if (normalizeLayerNameKey(preferredBase) !in existingNameKeys) {
                preferredBase
            } else {
                val stem = preferredBase.replace(Regex("\\s*\\(\\d+\\)$"), "").trim().ifBlank { preferredBase }
                var n = 2
                while (normalizeLayerNameKey("$stem ($n)") in existingNameKeys) {
                    n++
                }
                "$stem ($n)"
            }

            LocalFolderStore.saveNewLayer(appCtx, localId, chosenName, stamp, devId, parsed)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                DevicePrefs.addCachedLayerName(this, chosenName)
                DevicePrefs.setLayerVisible(this, localId, true)
                applyMergedFoldersAndRender()

                val allCoords = collectLayerLatLngs(parsed.pins, parsed.tracks)
                if (allCoords.isNotEmpty()) {
                    zoomToKmlBounds(allCoords, localId)
                }

                val summary = buildString {
                    append("Imported ")
                    if (parsed.pins.isNotEmpty()) append("${parsed.pins.size} pin${if (parsed.pins.size == 1) "" else "s"}")
                    if (parsed.tracks.isNotEmpty()) {
                        if (parsed.pins.isNotEmpty()) append(" & ")
                        append("${parsed.tracks.size} track${if (parsed.tracks.size == 1) "" else "s"}")
                    }
                    append(" into \"$chosenName\"")
                }
                Toast.makeText(this, summary, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun confirmUploadLocalToCloud(layer: LayerItem, layerPins: List<PinItem>, layerTracks: List<TrackItem>) {
        if (layer.id in uploadingLayerIds) {
            Toast.makeText(this, "Already uploading \"${layer.name}\"...", Toast.LENGTH_SHORT).show()
            return
        }
        showThemedDialog(
            themedDialogBuilder("Upload \"${layer.name}\" to Cloud?")
                .setMessage("Creates a live shared folder with a 6-character code for your teams and replaces the local-only copy.")
                .setPositiveButton("Upload") { _, _ ->
                    if (!uploadingLayerIds.add(layer.id)) return@setPositiveButton
                    Toast.makeText(this, "Uploading \"${layer.name}\" to cloud...", Toast.LENGTH_SHORT).show()
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
                            uploadingLayerIds.remove(layer.id)
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
                .create()
        )
    }

    private fun showDeleteLocalDialog(layer: LayerItem, pinCount: Int) {
        showThemedDialog(
            themedDialogBuilder("Delete Local Folder \"${layer.name}\"?")
                .setMessage("Removes all $pinCount pins stored locally on this phone.")
                .setPositiveButton("Delete") { _, _ ->
                    val deletedPinIds = pinsByLayerId[layer.id]?.map { it.id }.orEmpty()
                    LocalFolderStore.deleteLayer(this, layer.id)
                    DevicePrefs.removeJoinedLayerId(this, layer.id)
                    DevicePrefs.removePinReferences(this, deletedPinIds)
                    applyMergedFoldersAndRender()
                    Toast.makeText(this, "Deleted local folder \"${layer.name}\"", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .create(),
            destructivePositive = true
        )
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

    private fun applyOptimisticCloudPinUpdate(updated: PinItem) {
        if (cloudPins.any { it.id == updated.id }) {
            cloudPins = cloudPins.map { if (it.id == updated.id) updated else it }
        }
        applyMergedFoldersAndRender()
    }

    private fun savePinStatusUnified(pin: PinItem, status: String, stamp: String, reason: String = "") {
        if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            LocalFolderStore.updatePinStatus(this, pin, status, stamp, reason)
            applyMergedFoldersAndRender()
        } else {
            val updated = repo.updatePinStatus(pin, status, stamp, reason, ::notifySyncError)
            applyOptimisticCloudPinUpdate(updated)
        }
    }

    private fun savePinsStatusBatchUnified(targetPins: List<PinItem>, status: String, stamp: String, reason: String = "") {
        if (targetPins.isEmpty()) return
        val localPins = targetPins.filter { LocalFolderStore.isLocalLayerId(it.layerId) }
        val remotePins = targetPins.filterNot { LocalFolderStore.isLocalLayerId(it.layerId) }
        if (localPins.isNotEmpty()) {
            LocalFolderStore.updatePinsStatusBatch(this, localPins, status, stamp, reason)
        }
        if (remotePins.isNotEmpty()) {
            val updatedCloud = repo.updatePinsStatusBatch(remotePins, status, stamp, reason, ::notifySyncError)
            val byId = updatedCloud.associateBy { it.id }
            cloudPins = cloudPins.map { byId[it.id] ?: it }
        }
        applyMergedFoldersAndRender()
    }

    private fun savePinTextUnified(pin: PinItem, newName: String, newDesc: String, stamp: String) {
        if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            LocalFolderStore.updatePinText(this, pin, newName, newDesc, stamp)
            applyMergedFoldersAndRender()
        } else {
            val updated = repo.updatePinText(pin, newName, newDesc, stamp, ::notifySyncError)
            applyOptimisticCloudPinUpdate(updated)
        }
    }

    private fun deletePinHistoryEntryUnified(pin: PinItem, idx: Int): PinItem {
        return if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            val updated = LocalFolderStore.deletePinHistoryEntry(this, pin, idx)
            applyMergedFoldersAndRender()
            updated
        } else {
            val updated = repo.deletePinHistoryEntry(pin, idx, ::notifySyncError)
            applyOptimisticCloudPinUpdate(updated)
            updated
        }
    }

    private fun resetPinStatusUnified(pin: PinItem): PinItem {
        return if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            val updated = LocalFolderStore.resetPinStatusToPending(this, pin)
            applyMergedFoldersAndRender()
            updated
        } else {
            val updated = repo.resetPinStatusToPending(pin, ::notifySyncError)
            applyOptimisticCloudPinUpdate(updated)
            updated
        }
    }

    private fun deletePinUnified(pin: PinItem) {
        DevicePrefs.removePinReferences(this, listOf(pin.id))
        if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
            LocalFolderStore.deletePin(this, pin.id)
            applyMergedFoldersAndRender()
        } else {
            repo.deletePin(pin.id, ::notifySyncError)
            cloudPins = cloudPins.filterNot { it.id == pin.id }
            applyMergedFoldersAndRender()
        }
    }

    private fun deletePinsUnified(pinsToDelete: List<PinItem>) {
        if (pinsToDelete.isEmpty()) return
        DevicePrefs.removePinReferences(this, pinsToDelete.map { it.id })
        val localIds = pinsToDelete.filter { LocalFolderStore.isLocalLayerId(it.layerId) }.map { it.id }
        val cloudIds = pinsToDelete.filterNot { LocalFolderStore.isLocalLayerId(it.layerId) }.map { it.id }
        if (localIds.isNotEmpty()) {
            LocalFolderStore.deletePins(this, localIds)
        }
        if (cloudIds.isNotEmpty()) {
            val dropSet = cloudIds.toSet()
            repo.deletePinsBatch(cloudIds, ::notifySyncError)
            cloudPins = cloudPins.filterNot { it.id in dropSet }
        }
        applyMergedFoldersAndRender()
    }

    private fun applyExtraMapToolsVisibility() {
        val show = DevicePrefs.isShowExtraMapTools(this)
        val vis = if (show) View.VISIBLE else View.GONE
        if (::btnLasso.isInitialized) btnLasso.visibility = vis
        if (!show && isLassoMode) toggleLassoMode(false)
    }

    private fun applyZoomButtonsVisibility() {
        if (!::zoomPillContainer.isInitialized) return
        val show = DevicePrefs.isShowZoomButtons(this)
        zoomPillContainer.visibility = if (show) View.VISIBLE else View.GONE
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
        val cachedLocal = LocalFolderStore.getCachedOrNull()
        val local = if (cachedLocal != null) {
            cachedLocal
        } else {
            LocalFolderStore.preloadAsync(applicationContext) {
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) applyMergedFoldersAndRender()
                }
            }
            LocalStoreSnapshot(emptyList(), emptyList(), emptyList())
        }
        layers = (cloudLayers + local.layers).sortedByDescending { it.updatedAt }
        pins = cloudPins + local.pins
        tracks = cloudTracks + local.tracks
        layerById = layers.associateBy { it.id }
        pinById = pins.associateBy { it.id }
        pinsByLayerId = pins.groupBy { it.layerId }
        tracksByLayerId = tracks.groupBy { it.layerId }
        val activeTrackIds = tracks.mapTo(HashSet(tracks.size)) { it.id }
        synchronized(trackPointCache) {
            trackPointCache.keys.retainAll(activeTrackIds)
        }
        val buckets = HashMap<Long, MutableList<PinItem>>(pins.size)
        for (pItem in pins) {
            val bx = kotlin.math.floor(pItem.lat / stackedCellSize).toInt()
            val by = kotlin.math.floor(pItem.lng / stackedCellSize).toInt()
            buckets.getOrPut(stackedBucketKey(bx, by)) { ArrayList(2) }.add(pItem)
        }
        stackedPinBuckets = buckets
        dataVersion++
        if (layers.isNotEmpty()) {
            DevicePrefs.saveCachedLayerNames(this, layers.map { it.name })
        }
        renderMapData()
        liveBookmarksRefresh?.invoke()
        livePinListRefresh?.invoke()
        liveEditHistoryRefresh?.invoke()
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
                    val matched = pinById[waitingId]
                    if (matched != null) {
                        activeSharedPinId = null
                        hasAutoZoomedInitial = true
                        ensurePinVisibleOnMap(matched)
                        selectPinAndOpenCard(matched, zoomIfFar = true)
                        return@runOnUiThread
                    }
                }
                if (!hasAutoZoomedInitial && (pins.isNotEmpty() || tracks.isNotEmpty())) {
                    hasAutoZoomedInitial = true
                    zoomToKmlBounds(collectLayerLatLngs(pins, tracks))
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

    private fun collectLayerLatLngs(layerPins: List<PinItem>, layerTracks: List<TrackItem>): List<LatLng> {
        val out = ArrayList<LatLng>(layerPins.size + layerTracks.size * 4)
        layerPins.forEach { p ->
            if (p.lat.isFinite() && p.lng.isFinite() && p.lat in -90.0..90.0 && p.lng in -180.0..180.0) {
                out.add(LatLng(p.lat, p.lng))
            }
        }
        layerTracks.forEach { t ->
            t.coords.split(";").forEach { pair ->
                val parts = pair.split(",")
                if (parts.size >= 2) {
                    val lng = parts[0].trim().toDoubleOrNull()
                    val lat = parts[1].trim().toDoubleOrNull()
                    if (lat != null && lng != null && lat.isFinite() && lng.isFinite() &&
                        lat in -90.0..90.0 && lng in -180.0..180.0
                    ) {
                        out.add(LatLng(lat, lng))
                    }
                }
            }
        }
        return out
    }

    private fun zoomToKmlBounds(pts: List<LatLng>, layerIdToReveal: String? = null) {
        if (layerIdToReveal != null && !DevicePrefs.isLayerVisible(this, layerIdToReveal)) {
            DevicePrefs.setLayerVisible(this, layerIdToReveal, true)
            renderMapData()
        }
        val valid = pts.filter {
            it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0
        }
        if (valid.isEmpty()) {
            if (layerIdToReveal != null) {
                Toast.makeText(this, "No coordinates found in this KML folder", Toast.LENGTH_SHORT).show()
            }
            return
        }
        hasAutoZoomedInitial = true
        if (isFollowingGps) {
            isFollowingGps = false
            updateLocationIconTint()
        }
        pendingZoomPoints = valid
        flushPendingZoomIfReady()
    }

    private fun flushPendingZoomIfReady() {
        val pts = pendingZoomPoints ?: return
        val m = map ?: return
        val style = m.style
        if (style == null || !style.isFullyLoaded) return
        if (mapView.width <= 0 || mapView.height <= 0) {
            mapView.post { flushPendingZoomIfReady() }
            return
        }
        pendingZoomPoints = null
        mapView.post {
            if (isFinishing || isDestroyed) return@post
            val curMap = map ?: return@post
            var minLat = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var minLng = Double.POSITIVE_INFINITY
            var maxLng = Double.NEGATIVE_INFINITY
            pts.forEach { p ->
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLng) minLng = p.longitude
                if (p.longitude > maxLng) maxLng = p.longitude
            }
            val center = LatLng((minLat + maxLat) / 2.0, (minLng + maxLng) / 2.0)
            val isSingleOrTight = pts.size == 1 || (maxLat - minLat < 1e-5 && maxLng - minLng < 1e-5)
            if (isSingleOrTight) {
                val cam = CameraPosition.Builder()
                    .target(center)
                    .zoom(16.5)
                    .padding(0.0, 0.0, 0.0, 0.0)
                    .build()
                curMap.animateCamera(CameraUpdateFactory.newCameraPosition(cam), 550)
                return@post
            }
            val maxPadH = (mapView.width / 3).coerceAtLeast(16)
            val maxPadV = (mapView.height / 3).coerceAtLeast(16)
            val padLeft = dp(48).coerceAtMost(maxPadH)
            val padRight = dp(60).coerceAtMost(maxPadH)
            val padTop = (statusBarInsetTop + dp(84)).coerceAtMost(maxPadV)
            val padBottom = (navBarInsetBottom + dp(112)).coerceAtMost(maxPadV)
            runCatching {
                val bounds = LatLngBounds.Builder()
                    .include(LatLng(minLat, minLng))
                    .include(LatLng(maxLat, maxLng))
                    .build()
                curMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(bounds, padLeft, padTop, padRight, padBottom),
                    650
                )
            }.onFailure {
                curMap.animateCamera(CameraUpdateFactory.newLatLngZoom(center, 15.5), 550)
            }
        }
    }

    private fun zoomToPoints(pts: List<LatLng>) {
        zoomToKmlBounds(pts)
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyTransparentSystemBars()
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
        contourManager.cancelPending()
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
        routeRequestSeq++
        webSearchSeq++
        pendingSharedPin = null
        pendingRouteReq = null
        pendingZoomPoints = null
        liveBookmarksRefresh = null
        livePinListRefresh = null
        liveEditHistoryRefresh = null
        stopLiveGpsUpdates()
        activePageCleanup?.invoke()
        activePageCleanup = null
        pageOverlayContainer.animate().cancel()
        pulseAnimator?.cancel()
        gpsAnim?.cancel()
        contourManager.destroy()
        offlineMaps.setStateListener(null)
        tts?.stop()
        tts?.shutdown()
        tts = null
        if (::repo.isInitialized) repo.stopListening()
        mapView.onDestroy()
        super.onDestroy()
    }
    private fun applyResponsiveChromeSizing() {
        if (!::btnLocate.isInitialized) return
        val compact = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val utilSize = dp(if (compact) 44 else 52)
        val locSize = dp(if (compact) 56 else 66)
        val utilPad = dp(if (compact) 9 else 11)
        val locPad = dp(if (compact) 14 else 17)
        val arcBox = dp(if (compact) 118 else 138)
        val arcCardOffset = dp(if (compact) 66 else 78)
        val arcDiagOffset = dp(if (compact) 50 else 59)

        fun sizeCircle(v: View, size: Int, padPx: Int, marginEndPx: Int? = null, marginBottomPx: Int? = null) {
            val lp = v.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            lp.width = size
            lp.height = size
            if (marginEndPx != null) lp.marginEnd = marginEndPx
            if (marginBottomPx != null) lp.bottomMargin = marginBottomPx
            v.layoutParams = lp
            v.setPadding(padPx, padPx, padPx, padPx)
        }

        if (::rightArcCluster.isInitialized) {
            rightArcCluster.layoutParams = rightArcCluster.layoutParams.apply {
                width = arcBox
                height = arcBox
            }
        }
        sizeCircle(btnSyncStatus, utilSize, utilPad)
        if (::btnCompass.isInitialized) sizeCircle(btnCompass, utilSize, dp(if (compact) 8 else 9))
        sizeCircle(btnStyleToggle, utilSize, utilPad, marginEndPx = 0, marginBottomPx = arcCardOffset)
        sizeCircle(btnRuler, utilSize, utilPad, marginEndPx = arcDiagOffset, marginBottomPx = arcDiagOffset)
        sizeCircle(btnStatusFilter, utilSize, utilPad, marginEndPx = arcCardOffset, marginBottomPx = 0)
        sizeCircle(btnLocate, locSize, locPad, marginEndPx = 0, marginBottomPx = 0)
        sizeCircle(btnLasso, utilSize, utilPad, marginBottomPx = dp(if (compact) 6 else 10))
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyResponsiveChromeSizing()
        updateScaleBar()
        if (::sheetBehavior.isInitialized && sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
            val maxSheetH = (resources.displayMetrics.heightPixels - statusBarInsetTop - dp(12)).coerceAtLeast(dp(240))
            sheetBehavior.maxHeight = maxSheetH
            val maxPeek = (resources.displayMetrics.heightPixels * 0.62f).toInt().coerceAtLeast(dp(180))
            sheetBehavior.peekHeight = minOf(dp(activeSheetPeekDp) + navBarInsetBottom, maxPeek)
        }
        if (DevicePrefs.getThemeMode(this) == "auto") {
            applyMapChromeTheme()
            if (mapStyleMode == MapStyleMode.VECTOR) loadMapStyle()
            if (::pageOverlayContainer.isInitialized && pageOverlayContainer.visibility == View.VISIBLE) {
                pageOverlayContainer.setBackgroundColor(currentPalette().pageBackdrop)
            }
            val curPinId = selectedPinId
            val curTapped = activeTappedLatLng
            if (sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                if (curPinId != null) {
                    pinById[curPinId]?.let { showPinCard(it, animateSwap = false) }
                } else if (curTapped != null) {
                    showTappedPlaceCard(tappedResolvedTitle, tappedResolvedSub, curTapped.latitude, curTapped.longitude)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
