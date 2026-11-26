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
import android.view.animation.LinearInterpolator
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
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
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    companion object {
        internal val ALL_STATUSES = setOf("pending", "done", "issue")

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
    }

    private lateinit var mapView: MapView
    private lateinit var topCluster: LinearLayout
    private lateinit var topBar: LinearLayout
    private lateinit var routeFromBanner: LinearLayout
    private lateinit var tvRouteFromText: TextView
    private lateinit var tvRouteFromHint: TextView
    private lateinit var btnClearRouteFrom: ImageButton
    private lateinit var syncStatusContainer: FrameLayout
    private lateinit var btnSyncStatus: ImageButton
    private lateinit var tvSyncPendingBadge: TextView
    private lateinit var undoController: StatusUndoController
    private lateinit var btnCompass: ImageButton
    private lateinit var btnStyleToggle: ImageButton
    private lateinit var btnStatusFilter: ImageButton
    internal lateinit var btnSentToggle: TextView
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
    private var isFullScreenMapMode: Boolean
        get() = viewModel.isFullScreenMap
        set(value) { viewModel.isFullScreenMap = value }
    private var isCompassVisible: Boolean = false
    private var isSwappingSheetInPlace: Boolean = false
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
    internal lateinit var btnNavVoice: ImageButton
    private lateinit var btnNavStop: ImageButton
    private lateinit var tvScaleLabel: TextView
    private lateinit var scaleBarLine: ScaleBarView
    private lateinit var scaleBarContainer: LinearLayout

    private lateinit var inScreenSheet: LinearLayout
    private lateinit var sheetDragHandle: View
    internal lateinit var sheetStickyHeader: LinearLayout
    private lateinit var sheetScrollView: androidx.core.widget.NestedScrollView
    internal lateinit var sheetContent: LinearLayout
    private lateinit var sheetBehavior: BottomSheetBehavior<LinearLayout>
    internal lateinit var pageOverlayContainer: FrameLayout

    internal val map: MapLibreMap?
        get() = if (::mapRenderer.isInitialized) mapRenderer.map else null
    internal val contourManager = ContourOverlayManager()
    private val viewModel: MapViewModel by viewModels()
    private var isActivityStarted = false
    private var isActivityResumed = false
    internal lateinit var offlineMaps: OfflineMapManager
    internal lateinit var repository: TerraSyncRepository
    internal val repo: SyncRepo get() = repository.syncRepo
    private lateinit var mapRenderer: MapLayerRenderer
    private lateinit var gpsController: GpsLocationController
    private lateinit var navigationController: NavigationController
    private lateinit var mapToolsController: MapToolsController

    internal val layers: List<LayerItem> get() = viewModel.folderSnapshot.layers
    internal val pins: List<PinItem> get() = viewModel.folderSnapshot.pins
    internal val tracks: List<TrackItem> get() = viewModel.folderSnapshot.tracks
    private val pinById: Map<String, PinItem> get() = viewModel.folderSnapshot.pinById
    private val layerById: Map<String, LayerItem> get() = viewModel.folderSnapshot.layerById
    internal val pinsByLayerId: Map<String, List<PinItem>> get() = viewModel.folderSnapshot.pinsByLayerId
    private val tracksByLayerId: Map<String, List<TrackItem>> get() = viewModel.folderSnapshot.tracksByLayerId

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

    private sealed interface SearchRowItem {
        val stableId: String
        data class HintAndChips(val langKey: String) : SearchRowItem {
            override val stableId: String = "hint_chips"
        }
        data class WebStatus(val message: String, val showRetry: Boolean) : SearchRowItem {
            override val stableId: String = "web_status"
        }
        data class Header(val title: String) : SearchRowItem {
            override val stableId: String = "hdr:$title"
        }
        data class WebPlace(val place: PlaceResult, val isFirst: Boolean, val isLast: Boolean) : SearchRowItem {
            override val stableId: String = "web:${place.lat},${place.lng}:${place.title}"
        }
        data class CoordJump(val lat: Double, val lng: Double) : SearchRowItem {
            override val stableId: String = "coord:$lat,$lng"
        }
        data class BookmarkMatch(
            val entry: SearchIndexEntry,
            val distText: String?,
            val showFolderInSub: Boolean,
            val isFirst: Boolean,
            val isLast: Boolean
        ) : SearchRowItem {
            override val stableId: String = "pin:${entry.pin.id}"
        }
    }

    private sealed interface PinListRowItem {
        val stableId: String
        data class MessageRow(val text: String) : PinListRowItem {
            override val stableId: String = "msg:$text"
        }
        data class PinRow(
            val pin: PinItem,
            val isFirst: Boolean,
            val isLast: Boolean,
            val selectionMode: Boolean,
            val isSelected: Boolean,
            val sameCount: Int,
            val distLabel: String?
        ) : PinListRowItem {
            override val stableId: String = "pin:${pin.id}"
        }
        data class ShareCodeRow(val shareCode: String) : PinListRowItem {
            override val stableId: String = "share:$shareCode"
        }
    }

    private inner class BookmarkSearchAdapter(
        private val palette: ThemePalette,
        private val onQuickFilter: ((String) -> Unit)? = null,
        private val onRetryWebSearch: (() -> Unit)? = null
    ) : ListAdapter<SearchRowItem, RecyclerView.ViewHolder>(
        object : DiffUtil.ItemCallback<SearchRowItem>() {
            override fun areItemsTheSame(oldItem: SearchRowItem, newItem: SearchRowItem) =
                oldItem.stableId == newItem.stableId
            override fun areContentsTheSame(oldItem: SearchRowItem, newItem: SearchRowItem) =
                oldItem == newItem
        }
    ) {
        private inner class SimpleVH(val view: View) : RecyclerView.ViewHolder(view)

        private inner class BookmarkVH(
            val cardWrap: LinearLayout,
            val topDivider: View,
            val clickRow: LinearLayout,
            val dot: View,
            val tvTitle: TextView,
            val tvSub: TextView,
            val tvDist: TextView
        ) : RecyclerView.ViewHolder(cardWrap)

        private inner class WebPlaceVH(
            val cardWrap: LinearLayout,
            val topDivider: View,
            val clickRow: LinearLayout,
            val tvTitle: TextView,
            val tvSub: TextView
        ) : RecyclerView.ViewHolder(cardWrap)

        override fun getItemViewType(position: Int): Int = when (getItem(position)) {
            is SearchRowItem.HintAndChips -> 0
            is SearchRowItem.WebStatus -> 1
            is SearchRowItem.Header -> 2
            is SearchRowItem.WebPlace -> 3
            is SearchRowItem.CoordJump -> 4
            is SearchRowItem.BookmarkMatch -> 5
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val ctx = this@MainActivity
            return when (viewType) {
                0 -> {
                    val card = DesignSystem.createGroupedCard(ctx, palette, radiusDp = 16f, paddingHDp = 14, paddingVDp = 14)
                    card.addView(TextView(ctx).apply {
                        text = AppStrings.searchHintPrompt(ctx)
                        textSize = 13.5f
                        setLineSpacing(dp(2).toFloat(), 1.05f)
                        setTextColor(palette.textBody)
                    })
                    val chipRow = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, dp(10), 0, 0)
                    }
                    fun makeChip(label: String, actionKey: String, accent: Int, marginEndDp: Int): MaterialButton = MaterialButton(ctx).apply {
                        text = label
                        textSize = 12f
                        isAllCaps = false
                        setSingleLine(true)
                        ellipsize = TextUtils.TruncateAt.END
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = 0
                        minimumHeight = 0
                        setPadding(dp(10), 0, dp(10), 0)
                        cornerRadius = dp(12)
                        DesignSystem.styleSegmentedButton(this, false, palette, accent, onTonalCard = true)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                            if (marginEndDp > 0) marginEnd = dp(marginEndDp)
                        }
                        UiAnimations.attachSnappyPress(this)
                        setOnClickListener { onQuickFilter?.invoke(actionKey) }
                    }
                    chipRow.addView(makeChip(AppStrings.pending(ctx), "pending", palette.amber, 6))
                    chipRow.addView(makeChip(AppStrings.issue(ctx), "issue", palette.red, 6))
                    chipRow.addView(makeChip(AppStrings.nearestPending(ctx), "nearest", palette.accentPrimary, 0))
                    card.addView(chipRow)
                    SimpleVH(card)
                }
                1 -> {
                    val card = DesignSystem.createGroupedCard(ctx, palette, radiusDp = 16f, paddingHDp = 14, paddingVDp = 14, bottomMarginDp = 10)
                    card.orientation = LinearLayout.HORIZONTAL
                    card.gravity = Gravity.CENTER_VERTICAL
                    val tvMsg = TextView(ctx).apply {
                        tag = "msg"
                        textSize = 13.5f
                        setTextColor(palette.textBody)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(8)
                        }
                    }
                    val btnRetry = MaterialButton(ctx).apply {
                        tag = "retry"
                        text = AppStrings.retry(ctx)
                        textSize = 12f
                        isAllCaps = false
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        minHeight = 0
                        minimumHeight = 0
                        setPadding(dp(12), 0, dp(12), 0)
                        cornerRadius = dp(12)
                        DesignSystem.applyPrimaryCtaButton(this, palette)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34))
                        UiAnimations.attachSnappyPress(this)
                        setOnClickListener { onRetryWebSearch?.invoke() }
                    }
                    card.addView(tvMsg)
                    card.addView(btnRetry)
                    SimpleVH(card)
                }
                2 -> {
                    val tv = DesignSystem.createSectionHeader(ctx, "", palette)
                    tv.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    SimpleVH(tv)
                }
                3 -> {
                    val cardWrap = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        clipToOutline = true
                    }
                    val topDivider = DesignSystem.createHairlineDivider(ctx, palette, 16, 16)
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(14), dp(12), dp(14), dp(12))
                        UiAnimations.applyRowRipple(ctx, this, palette.isDark)
                    }
                    val tvTitle = TextView(ctx).apply {
                        textSize = 14.5f
                        setTextColor(palette.textPrimary)
                        setTypeface(null, Typeface.BOLD)
                    }
                    val tvSub = TextView(ctx).apply {
                        textSize = 12f
                        setTextColor(palette.textSecondary)
                    }
                    row.addView(tvTitle)
                    row.addView(tvSub)
                    cardWrap.addView(topDivider)
                    cardWrap.addView(row)
                    WebPlaceVH(cardWrap, topDivider, row, tvTitle, tvSub)
                }
                4 -> {
                    val card = DesignSystem.createGroupedCard(ctx, palette, radiusDp = 16f, paddingHDp = 14, paddingVDp = 12, bottomMarginDp = 8)
                    val tv = TextView(ctx).apply {
                        tag = "coord"
                        textSize = 14.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(palette.accentPrimary)
                        DesignSystem.applyTabularNumerals(this)
                        UiAnimations.applyRowRipple(ctx, this, palette.isDark)
                    }
                    card.addView(tv)
                    SimpleVH(card)
                }
                else -> {
                    val cardWrap = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        clipToOutline = true
                    }
                    val topDivider = DesignSystem.createHairlineDivider(ctx, palette, 16, 16)
                    val row = LinearLayout(ctx).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = dp(52)
                        setPadding(dp(14), dp(11), dp(14), dp(11))
                        UiAnimations.applyRowRipple(ctx, this, palette.isDark)
                    }
                    val dot = View(ctx).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(12) }
                    }
                    val col = LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                            marginEnd = dp(8)
                        }
                    }
                    val tvTitle = TextView(ctx).apply {
                        textSize = 14.5f
                        setTextColor(palette.textPrimary)
                        setTypeface(null, Typeface.BOLD)
                        maxLines = 2
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    val tvSub = TextView(ctx).apply {
                        textSize = 12f
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    col.addView(tvTitle)
                    col.addView(tvSub)
                    val tvDist = TextView(ctx).apply {
                        textSize = 12f
                        setTextColor(palette.accentPrimary)
                        setTypeface(null, Typeface.BOLD)
                        setSingleLine(true)
                        DesignSystem.applyTabularNumerals(this)
                    }
                    row.addView(dot)
                    row.addView(col)
                    row.addView(tvDist)
                    cardWrap.addView(topDivider)
                    cardWrap.addView(row)
                    BookmarkVH(cardWrap, topDivider, row, dot, tvTitle, tvSub, tvDist)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val ctx = this@MainActivity
            when (val item = getItem(position)) {
                is SearchRowItem.HintAndChips -> Unit
                is SearchRowItem.WebStatus -> {
                    val v = (holder as SimpleVH).view
                    v.findViewWithTag<TextView>("msg")?.text = item.message
                    v.findViewWithTag<MaterialButton>("retry")?.visibility = if (item.showRetry) View.VISIBLE else View.GONE
                }
                is SearchRowItem.Header -> {
                    ((holder as SimpleVH).view as TextView).text = item.title
                }
                is SearchRowItem.WebPlace -> {
                    val vh = holder as WebPlaceVH
                    vh.topDivider.visibility = if (item.isFirst) View.GONE else View.VISIBLE
                    vh.cardWrap.background = DesignSystem.createGroupedRowBackground(ctx, palette, item.isFirst, item.isLast, 18f)
                    vh.tvTitle.text = "🌐 ${DesignSystem.isolateBidi(item.place.title)}"
                    if (item.place.subtitle.isNotBlank()) {
                        vh.tvSub.text = DesignSystem.isolateBidi(item.place.subtitle)
                        vh.tvSub.visibility = View.VISIBLE
                    } else {
                        vh.tvSub.text = ""
                        vh.tvSub.visibility = View.GONE
                    }
                    vh.clickRow.setOnClickListener {
                        closeFullPage()
                        selectCoordinateOnMap(LatLng(item.place.lat, item.place.lng), explicitTitle = item.place.title, fallbackSub = item.place.subtitle, zoomTo = 16.0)
                    }
                }
                is SearchRowItem.CoordJump -> {
                    val tv = (holder as SimpleVH).view.findViewWithTag<TextView>("coord") ?: return
                    tv.text = "📍 ${AppStrings.goToCoordinates(ctx)}: ${"%.6f".format(Locale.US, item.lat)}, ${"%.6f".format(Locale.US, item.lng)}"
                    tv.setOnClickListener {
                        closeFullPage()
                        selectCoordinateOnMap(LatLng(item.lat, item.lng), explicitTitle = "Coordinates", fallbackSub = "", zoomTo = 16.5)
                    }
                }
                is SearchRowItem.BookmarkMatch -> {
                    val vh = holder as BookmarkVH
                    val pin = item.entry.pin
                    vh.topDivider.visibility = if (item.isFirst) View.GONE else View.VISIBLE
                    vh.cardWrap.background = DesignSystem.createGroupedRowBackground(ctx, palette, item.isFirst, item.isLast, 16f)
                    vh.dot.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(item.entry.dotColor)
                    }
                    vh.tvTitle.text = DesignSystem.isolateBidi(pin.name)
                    val st = PinStatus.fromRaw(pin.status)
                    vh.tvSub.text = if (item.showFolderInSub && item.entry.folderName.isNotBlank()) {
                        DesignSystem.joinBidi(item.entry.folderName, item.entry.statusTag)
                    } else {
                        item.entry.statusTag
                    }
                    vh.tvSub.setTextColor(
                        when {
                            st == PinStatus.ISSUE -> palette.red
                            st == PinStatus.DONE && pin.issueReason.isNotBlank() -> palette.green
                            else -> palette.textSecondary
                        }
                    )
                    if (item.distText != null) {
                        vh.tvDist.text = item.distText
                        vh.tvDist.visibility = View.VISIBLE
                    } else {
                        vh.tvDist.text = ""
                        vh.tvDist.visibility = View.GONE
                    }
                    vh.clickRow.setOnClickListener {
                        ensurePinVisibleOnMap(pin)
                        closeFullPage()
                        selectPinAndOpenCard(pin, zoomIfFar = true)
                    }
                }
            }
        }
    }

    internal var lastPinsRenderKey: String?
        get() = if (::mapRenderer.isInitialized) mapRenderer.lastPinsRenderKey else null
        set(value) { if (::mapRenderer.isInitialized) mapRenderer.lastPinsRenderKey = value }

    internal var visibleStatuses: MutableSet<String>
        get() = viewModel.visibleStatuses
        set(value) { viewModel.visibleStatuses = value }
    internal val isLiveConnected: Boolean
        get() = viewModel.folderSnapshot.isLiveConnected
    internal var mapStyleMode: MapStyleMode
        get() = viewModel.mapStyleMode
        set(value) { viewModel.mapStyleMode = value }
    internal var travelMode: TravelMode
        get() = viewModel.travelMode
        set(value) { viewModel.travelMode = value }
    internal var cachedScaleBarEnabled: Boolean = true
    internal var cachedSpeedUnit: SpeedUnit = SpeedUnit.METRIC
    internal var lastScaleLabel: String = ""
    internal var lastScaleWidthPx: Int = -1
    private var isFollowingGps: Boolean
        get() = viewModel.isFollowingGps
        set(value) { viewModel.isFollowingGps = value }
    private var hasAutoZoomedInitial: Boolean
        get() = viewModel.hasAutoZoomedInitial
        set(value) { viewModel.hasAutoZoomedInitial = value }
    private var statusBarInsetTop = 0
    private var navBarInsetBottom = 0
    private var imeInsetBottom = 0
    private var sideInsetLeft = 0
    private var sideInsetRight = 0
    private var activeSheetPeekDp = 280
    internal var sheetQuickSelectPinId: String? = null
    private val uploadingLayerIds = mutableSetOf<String>()
    internal var activePageCleanup: (() -> Unit)? = null
    private var webSearchSeq = 0
    private var tappedTitleView: TextView? = null
    private var tappedSubView: TextView? = null
    private var tappedDistView: TextView? = null
    private var sheetOpenSeq: Int = 0
    private var isSheetPendingOpen: Boolean = false

    internal var hideSentPins: Boolean
        get() = viewModel.hideSentPins
        set(value) { viewModel.hideSentPins = value }
    private val isLassoMode: Boolean get() = viewModel.isLassoMode
    private val lassoPendingPinIds: List<String> get() = viewModel.lassoPendingPinIds
    internal var isRulerMode: Boolean
        get() = viewModel.isRulerMode
        set(value) { viewModel.isRulerMode = value }
    private val selectedPinId: String? get() = viewModel.selectedPinId
    internal val currentLocation: Location?
        get() = if (::gpsController.isInitialized) gpsController.currentLocation else null
    private val previewRoute: NavRoute? get() = viewModel.previewRoute
    private val activeNavRoute: NavRoute? get() = viewModel.activeNavRoute

    // Full-screen overlay stack
    internal val pageBackStack = mutableListOf<() -> Unit>()
    private var backCallback: OnBackPressedCallback? = null
    internal var liveBookmarksRefresh: (() -> Unit)? = null
    internal var livePinListRefresh: (() -> Unit)? = null
    internal var liveEditHistoryRefresh: (() -> Unit)? = null

    internal val kmlPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
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
            if (::navigationController.isInitialized) navigationController.handleLocationPermissionGranted()
        } else {
            if (::navigationController.isInitialized) navigationController.onLocationPermissionDenied()
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    internal fun ensureNotificationPermissionForDownloads() {
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
        contourManager.initDiskCache(cacheDir)
        offlineMaps = OfflineMapManager.getInstance(this).apply { refreshAll() }
        repository = TerraSyncRepository(applicationContext).apply {
            onSnapshotUpdated = { snap ->
                if (!isFinishing && !isDestroyed) {
                    onRepositorySnapshotUpdated(snap)
                }
            }
        }
        RoutingEngine.appContext = applicationContext
        LocalFolderStore.preloadAsync(applicationContext) {
            repository.applyMergedFolders()
        }
        AppExecutors.batchIoExecutor.execute { OfflineRoutingEngine.cleanupOrphanedTempFiles(applicationContext) }
        setContentView(R.layout.activity_main)

        visibleStatuses = DevicePrefs.getVisibleStatuses(this).toMutableSet()
        hideSentPins = DevicePrefs.isHideSentPins(this)
        mapStyleMode = DevicePrefs.getMapStyleMode(this)
        travelMode = DevicePrefs.getTravelMode(this)
        cachedScaleBarEnabled = DevicePrefs.isScaleBarEnabled(this)
        cachedSpeedUnit = DevicePrefs.getSpeedUnit(this)

        bindViews()
        mapRenderer = MapLayerRenderer(
            context = this,
            viewModel = viewModel,
            contourManager = contourManager,
            isDarkMapUi = ::isDarkMapUi,
            isActivityAlive = { !isFinishing && !isDestroyed },
            isActivityStarted = { isActivityStarted },
            isActivityResumed = { isActivityResumed },
            getRulerGeoPoints = { viewModel.rulerPoints },
            getGpsDisplayPose = { if (::gpsController.isInitialized) gpsController.currentPoseOrNull() else null },
            getActiveNavTrimmedGeom = { if (::navigationController.isInitialized) navigationController.activeNavTrimmedGeom else null },
            onUpdateSyncBadge = ::updateSyncStatusBadge,
            onFlushPendingZoom = { if (::mapToolsController.isInitialized) mapToolsController.flushPendingZoomIfReady() }
        )
        gpsController = GpsLocationController(
            context = this,
            viewModel = viewModel,
            renderer = mapRenderer,
            speedBadge = speedBadge,
            tvSpeedValue = tvSpeedValue,
            tvSpeedUnit = tvSpeedUnit,
            btnLocate = btnLocate,
            isOverlayVisible = { ::pageOverlayContainer.isInitialized && pageOverlayContainer.visibility == View.VISIBLE },
            isActivityStarted = { isActivityStarted },
            isActivityResumed = { isActivityResumed },
            currentPalette = ::currentPalette,
            themedDialogBuilder = ::themedDialogBuilder,
            showThemedDialog = ::showThemedDialog,
            getCachedSpeedUnit = { cachedSpeedUnit }
        )

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
                        isSheetPendingOpen = false
                        sheetQuickSelectPinId = null
                        clearSheetStickyHeader()
                        clearSelectedMarker()
                        if (::navigationController.isInitialized) navigationController.cancelPreviewOnSheetClosed()
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

        navigationController = NavigationController(
            activity = this,
            viewModel = viewModel,
            renderer = mapRenderer,
            gpsController = gpsController,
            topCluster = topCluster,
            routeFromBanner = routeFromBanner,
            tvRouteFromText = tvRouteFromText,
            navBanner = navBanner,
            tvNavArrow = tvNavArrow,
            tvNavStepDist = tvNavStepDist,
            tvNavInstruction = tvNavInstruction,
            navBottomBar = navBottomBar,
            tvNavEta = tvNavEta,
            tvNavMeta = tvNavMeta,
            bottomDock = bottomDock,
            bottomNavDock = bottomNavDock,
            btnRuler = btnRuler,
            btnLasso = btnLasso,
            sheetContent = sheetContent,
            sheetBehavior = sheetBehavior,
            undoController = undoController,
            dp = ::dp,
            currentPalette = ::currentPalette,
            isActivityAlive = { !isFinishing && !isDestroyed },
            isActivityResumed = { isActivityResumed },
            getCachedSpeedUnit = { cachedSpeedUnit },
            launchLocationPermission = {
                locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            clearSheetStickyHeader = ::clearSheetStickyHeader,
            clearQuickSelectPinId = { sheetQuickSelectPinId = null },
            showInScreenSheet = ::showInScreenSheet,
            hideInScreenSheet = ::hideInScreenSheet,
            safeStartActivity = ::safeStartActivity,
            applyKeepAwakeFlag = ::applyKeepAwakeFlag,
            applyExtraMapToolsVisibility = ::applyExtraMapToolsVisibility,
            applyZoomButtonsVisibility = ::applyZoomButtonsVisibility,
            dismissToolsForNav = {
                if (isFullScreenMapMode) setFullScreenMapMode(false, animate = false)
                if (isLassoMode) toggleLassoMode(false)
                if (isRulerMode) toggleRulerMode(false)
            }
        ).apply { initTts() }

        mapToolsController = MapToolsController(
            activity = this,
            viewModel = viewModel,
            renderer = mapRenderer,
            gpsController = gpsController,
            mapView = mapView,
            lassoOverlay = lassoOverlay,
            lassoCard = lassoCard,
            tvLassoTitle = tvLassoTitle,
            tvLassoSubtitle = tvLassoSubtitle,
            btnLassoModeToggle = btnLassoModeToggle,
            btnLassoMarkSent = btnLassoMarkSent,
            btnLassoRedraw = btnLassoRedraw,
            btnLassoUndo = btnLassoUndo,
            btnLassoReset = btnLassoReset,
            rulerCard = rulerCard,
            btnRulerUndo = btnRulerUndo,
            tvRulerDistance = tvRulerDistance,
            tvRulerSubtitle = tvRulerSubtitle,
            btnRulerReset = btnRulerReset,
            btnSentToggle = btnSentToggle,
            bottomDock = bottomDock,
            bottomNavDock = bottomNavDock,
            pageOverlayContainer = pageOverlayContainer,
            undoController = undoController,
            dp = ::dp,
            currentPalette = ::currentPalette,
            themedDialogBuilder = ::themedDialogBuilder,
            showThemedDialog = ::showThemedDialog,
            isActivityAlive = { !isFinishing && !isDestroyed },
            getInsetsTopBottom = { statusBarInsetTop to navBarInsetBottom },
            getSpeedUnit = { DevicePrefs.getSpeedUnit(this@MainActivity) },
            updateFilterIconTint = ::updateFilterIconTint,
            applyMapChromeTheme = ::applyMapChromeTheme,
            isFullScreenMapMode = { isFullScreenMapMode },
            setFullScreenMapMode = ::setFullScreenMapMode,
            hideInScreenSheet = ::hideInScreenSheet,
            isSheetHidden = { sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN },
            closeFullPage = ::closeFullPage,
            showPinCard = { pin, animateSwap -> showPinCard(pin, animateSwap = animateSwap) },
            showTappedPlaceCard = ::showTappedPlaceCard,
            onReverseGeocodeResult = { _, bestTitle, bestSub, distShort ->
                tappedTitleView?.text = bestTitle
                val isolatedSub = DesignSystem.isolateBidi(bestSub)
                tappedSubView?.text = isolatedSub
                tappedSubView?.visibility = if (bestSub.isNotBlank()) View.VISIBLE else View.GONE
                tappedDistView?.text = if (distShort.isNotBlank()) {
                    if (bestSub.isNotBlank()) " • $distShort" else distShort
                } else ""
                tappedDistView?.visibility = if (distShort.isNotBlank()) View.VISIBLE else View.GONE
            },
            onJoinShareCode = ::joinShareCode,
            onImportUri = ::handleImportUri
        )

        gpsController.hasPendingRouteRequest = { navigationController.pendingRouteReq != null }
        gpsController.onPendingRouteCheck = { loc -> navigationController.handlePendingRouteOnFix(loc) }
        gpsController.onActiveNavigationLocation = { loc, route, dest -> navigationController.updateNavigationProgress(loc, route, dest) }

        applyMapChromeTheme()
        refreshLocalizedChrome()
        applyKeepAwakeFlag()

        setupClickListeners()
        setupBackNavigation()

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            mapRenderer.map = m
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
                    mapToolsController.clearLassoSelection()
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
                    mapToolsController.addRulerPoint(latLng)
                } else if (!isLassoMode) {
                    mapToolsController.handleMapTap(m, latLng)
                }
                true
            }

            m.addOnMapLongClickListener { latLng ->
                if (activeNavRoute == null && !isRulerMode && !isLassoMode) showAddPinDialog(latLng)
                true
            }

            mapToolsController.consumePendingSharedPinOnMapReady()
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

    internal fun themedDialogBuilder(title: String): AlertDialog.Builder {
        val p = currentPalette()
        return AlertDialog.Builder(this)
            .setCustomTitle(DesignSystem.createThemedDialogTitle(this, title, p))
    }

    internal fun showThemedDialog(dlg: AlertDialog, destructivePositive: Boolean = false): AlertDialog {
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
                if (this is MaterialButton) {
                    backgroundTintList = android.content.res.ColorStateList.valueOf(p.accentPrimary)
                    setTextColor(Color.WHITE)
                    cornerRadius = dp(20)
                    insetTop = 0
                    insetBottom = 0
                    strokeWidth = 0
                    elevation = 0f
                    stateListAnimator = null
                } else {
                    ViewCompat.setBackgroundTintList(this, null)
                    background = GradientDrawable().apply {
                        setColor(p.accentPrimary)
                        cornerRadius = dp(20).toFloat()
                    }
                    setTextColor(Color.WHITE)
                }
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
        syncStatusContainer = findViewById(R.id.syncStatusContainer)
        btnSyncStatus = findViewById(R.id.btnSyncStatus)
        tvSyncPendingBadge = findViewById(R.id.tvSyncPendingBadge)
        undoController = StatusUndoController(
            findViewById(R.id.undoBanner),
            findViewById(R.id.tvUndoMessage),
            findViewById(R.id.btnUndoAction)
        )
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
        bottomNavDock.onSwipeProgress = { prog ->
            if (!isFullScreenMapMode &&
                activeNavRoute == null &&
                !isRulerMode &&
                !isLassoMode &&
                pageOverlayContainer.visibility != View.VISIBLE &&
                sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN &&
                !UiAnimations.isReducedMotion(this)
            ) {
                val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
                val dir = if (isRtl) -1f else 1f
                topCluster.translationY = -dp(20) * prog
                topCluster.alpha = 1f - 0.25f * prog
                rightChromeColumn.translationX = dir * dp(28) * prog
                rightChromeColumn.alpha = 1f - 0.25f * prog
                leftBottomColumn.translationY = dp(18) * prog
                leftBottomColumn.alpha = 1f - 0.25f * prog
            }
        }
        bottomNavDock.onSwipeCancel = {
            if (!isFullScreenMapMode && !UiAnimations.isReducedMotion(this)) {
                listOf(topCluster, rightChromeColumn, leftBottomColumn).forEach { v ->
                    v.animate().cancel()
                    v.animate()
                        .translationX(0f)
                        .translationY(0f)
                        .alpha(1f)
                        .setDuration(150L)
                        .setInterpolator(UiAnimations.EMPHASIZED_DECELERATE)
                        .start()
                }
            }
        }
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
        sheetStickyHeader = findViewById(R.id.sheetStickyHeader)
        sheetScrollView = findViewById(R.id.sheetScrollView)
        sheetContent = findViewById(R.id.sheetContent)
        pageOverlayContainer = findViewById(R.id.pageOverlayContainer)
    }

    internal fun isDarkMapUi(): Boolean {
        return when (DevicePrefs.getThemeMode(this)) {
            "night" -> true
            "auto" -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            else -> false
        }
    }

    internal fun currentPalette(): ThemePalette = DesignSystem.palette(isDarkMapUi())

    internal fun updateSystemBarContrast(forFullPage: Boolean = ::pageOverlayContainer.isInitialized && pageOverlayContainer.visibility == View.VISIBLE) {
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
        val hideDur = if (animate) 185L else 0L
        val showDur = if (animate) 235L else 0L

        if (enabled) {
            fullScreenSwipeUpCatcher.visibility = View.VISIBLE
            UiAnimations.animateChromeSlide(bottomNavDock, hide = true, shiftY = bottomShift, durationMs = hideDur, startDelayMs = 0L)
            UiAnimations.animateChromeSlide(leftBottomColumn, hide = true, shiftY = bottomShift, durationMs = hideDur, startDelayMs = 15L)
            UiAnimations.animateChromeSlide(rightChromeColumn, hide = true, shiftX = rightShift, durationMs = hideDur, startDelayMs = 30L)
            UiAnimations.animateChromeSlide(topCluster, hide = true, shiftY = topShift, durationMs = hideDur, startDelayMs = 45L)
        } else {
            if (animate) UiAnimations.hapticTick(bottomNavDock)
            fullScreenSwipeUpCatcher.visibility = View.GONE
            if (bottomDock.visibility != View.VISIBLE && sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomDock.alpha = 1f
                bottomDock.translationY = 0f
                bottomDock.visibility = View.VISIBLE
            }
            if (activeNavRoute == null) {
                UiAnimations.animateChromeSlide(topCluster, hide = false, shiftY = topShift, durationMs = showDur, startDelayMs = 0L)
                map?.cameraPosition?.bearing?.let { updateCompassForBearing(it, animate = false) }
            }
            UiAnimations.animateChromeSlide(rightChromeColumn, hide = false, shiftX = rightShift, durationMs = showDur, startDelayMs = 20L)
            UiAnimations.animateChromeSlide(leftBottomColumn, hide = false, shiftY = bottomShift, durationMs = showDur, startDelayMs = 35L)
            if (activeNavRoute == null && !isRulerMode && !isLassoMode) {
                UiAnimations.animateChromeSlide(bottomNavDock, hide = false, shiftY = bottomShift, durationMs = showDur, startDelayMs = 45L)
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

    internal fun applyMapChromeTheme() {
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
        updateSyncStatusBadge()
        undoController.applyTheme(this, p)
        btnRulerReset.text = AppStrings.clear(this)

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

    internal fun refreshLocalizedChrome() {
        if (::btnTabSearch.isInitialized) {
            btnTabSearch.contentDescription = AppStrings.search(this)
            btnTabBookmarks.contentDescription = AppStrings.bookmarks(this)
            btnTabSettings.contentDescription = AppStrings.settings(this)
            btnTabAbout.contentDescription = AppStrings.about(this)
        }
        applyMapChromeTheme()
        updateFilterIconTint()
        if (isRulerMode) updateRulerUi()
    }

    private fun setupClickListeners() {
        btnSyncStatus.setOnClickListener {
            UiAnimations.hapticTick(btnSyncStatus)
            UiAnimations.animatePulseBump(btnSyncStatus)
            val state = if (isLiveConnected) AppStrings.cloudSynced(this) else AppStrings.offlineCache(this)
            val pendingUploads = repo.pendingUploadCount()
            val pendingPart = if (pendingUploads > 0) AppStrings.pendingUploadCount(this, pendingUploads) else AppStrings.allSynced(this)
            val localCnt = layers.count { LocalFolderStore.isLocalLayerId(it.id) }
            val cloudCnt = layers.size - localCnt
            Toast.makeText(this, "$state • $pendingPart • $cloudCnt cloud, $localCnt local • ${pins.size} bookmarks", Toast.LENGTH_SHORT).show()
        }

        btnCompass.setOnClickListener {
            UiAnimations.hapticTick(btnCompass)
            UiAnimations.animatePulseBump(btnCompass, 1.08f)
            map?.let { m ->
                val reset = CameraPosition.Builder(m.cameraPosition)
                    .bearing(0.0)
                    .tilt(0.0)
                    .build()
                m.animateCamera(CameraUpdateFactory.newCameraPosition(reset), 280)
            }
        }

        btnStyleToggle.setOnClickListener { showMapLayersSheet() }

        btnStatusFilter.setOnClickListener { showMultiStatusFilterDialog() }
        btnSentToggle.setOnClickListener {
            // Tapping the active-filter pill clears active status / unsent filters cleanly back to All Pins
            UiAnimations.hapticTick(btnSentToggle)
            visibleStatuses.clear()
            visibleStatuses.addAll(ALL_STATUSES)
            DevicePrefs.setVisibleStatuses(this, visibleStatuses)
            hideSentPins = false
            DevicePrefs.setHideSentPins(this, false)
            updateSentToggleUi()
            updateFilterIconTint()
            renderMapData()
            Toast.makeText(this, AppStrings.showingAllPins(this), Toast.LENGTH_SHORT).show()
        }
        btnLasso.setOnClickListener { toggleLassoMode(!isLassoMode) }
        btnLassoModeToggle.setOnClickListener { mapToolsController.toggleLassoMoveMapMode() }
        btnLassoMarkSent.setOnClickListener { mapToolsController.confirmLassoMarkSent() }
        btnLassoRedraw.setOnClickListener { mapToolsController.clearLassoSelection() }
        btnLassoUndo.setOnClickListener { mapToolsController.undoLastLassoMark() }
        btnLassoReset.setOnClickListener { mapToolsController.confirmResetAllSentPins() }
        btnLassoClose.setOnClickListener { toggleLassoMode(false) }
        lassoOverlay.onLoopStarted = {
            viewModel.clearLassoSelection()
            mapToolsController.updateLassoUi()
        }
        lassoOverlay.onLoopCompleted = { poly -> mapToolsController.handleLassoLoopCompleted(poly) }
        btnRuler.setOnClickListener { toggleRulerMode(!isRulerMode) }
        btnRulerUndo.setOnClickListener { mapToolsController.undoRulerPoint() }
        btnRulerReset.setOnClickListener { mapToolsController.resetRulerPoints() }
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
            val staleStream = System.currentTimeMillis() - gpsController.lastLiveLocationFixMs > 10_000L
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
                    if (::navigationController.isInitialized) navigationController.cancelPreviewOnSheetClosed()
                    hideInScreenSheet()
                    return
                }
                if (viewModel.routeOrigin != null) {
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
                if (sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN || isSheetPendingOpen) {
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
        if (::mapToolsController.isInitialized) mapToolsController.handleDeepLinkIntent(intent)
    }

    internal fun ensurePinVisibleOnMap(pin: PinItem) {
        if (::mapToolsController.isInitialized) mapToolsController.ensurePinVisibleOnMap(pin)
    }

    private fun selectCoordinateOnMap(
        latLng: LatLng,
        explicitTitle: String?,
        fallbackSub: String = "",
        zoomTo: Double? = null
    ) {
        if (::mapToolsController.isInitialized) mapToolsController.selectCoordinateOnMap(latLng, explicitTitle, fallbackSub, zoomTo)
    }

    internal fun loadMapStyle() = mapRenderer.loadMapStyle()
    internal fun apply3dBuildingsToggle(style: Style) = mapRenderer.apply3dBuildingsToggle(style)
    internal fun applyHillshadeSettings(style: Style) = mapRenderer.applyHillshadeSettings(style)
    private fun findExactStackedPins(pin: PinItem): List<PinItem> = mapToolsController.findExactStackedPins(pin)

    internal fun updateSyncStatusBadge() {
        if (!::btnSyncStatus.isInitialized || !::tvSyncPendingBadge.isInitialized) return
        val p = currentPalette()
        val pendingUploads = if (::repository.isInitialized) repo.pendingUploadCount() else 0
        btnSyncStatus.setColorFilter(
            when {
                pendingUploads > 0 -> p.accentWarm
                isLiveConnected -> p.accentPrimary
                else -> p.amber
            }
        )
        if (pendingUploads > 0) {
            tvSyncPendingBadge.text = if (pendingUploads > 99) "99+" else "$pendingUploads"
            tvSyncPendingBadge.background = DesignSystem.buildOpaqueDrawable(
                ctx = this,
                fillColor = p.accentWarm,
                radiusDp = 9f,
                strokeColor = p.surfaceFrosted,
                strokeDp = 1.5f
            )
            UiAnimations.animateSpeedBadgeVisibility(tvSyncPendingBadge, true)
        } else {
            UiAnimations.animateSpeedBadgeVisibility(tvSyncPendingBadge, false)
        }
    }

    internal fun renderMapData() {
        if (::mapRenderer.isInitialized) mapRenderer.renderMapData() else updateSyncStatusBadge()
    }

    private fun renderRouteOverlay(styleArg: Style? = null) {
        if (::mapRenderer.isInitialized) mapRenderer.renderRouteOverlay(styleArg)
    }

    private fun fireStatusPulse(lat: Double, lng: Double, statusHex: String) {
        if (::mapRenderer.isInitialized) mapRenderer.fireStatusPulse(lat, lng, statusHex)
    }

    internal fun updateSentToggleUi() {
        if (::btnSentToggle.isInitialized && ::mapToolsController.isInitialized) mapToolsController.updateSentToggleUi()
    }

    internal fun activeLocalSentCount(): Int =
        if (::mapToolsController.isInitialized) mapToolsController.activeLocalSentCount() else 0

    private fun toggleLassoMode(enabled: Boolean) {
        if (::mapToolsController.isInitialized) mapToolsController.toggleLassoMode(enabled)
    }

    private fun updateLassoUi() {
        if (::mapToolsController.isInitialized) mapToolsController.updateLassoUi()
    }

    private fun toggleRulerMode(enabled: Boolean) {
        if (::mapToolsController.isInitialized) mapToolsController.toggleRulerMode(enabled)
    }

    internal fun updateRulerUi() {
        if (::mapToolsController.isInitialized) mapToolsController.updateRulerUi()
    }

    private fun setRouteOrigin(lat: Double, lng: Double, label: String) {
        if (::navigationController.isInitialized) navigationController.setRouteOrigin(lat, lng, label)
    }

    private fun clearRouteOrigin() {
        if (::navigationController.isInitialized) navigationController.clearRouteOrigin()
    }

    internal fun selectPinAndOpenCard(pin: PinItem, zoomIfFar: Boolean = false) {
        if (::mapToolsController.isInitialized) mapToolsController.selectPinAndOpenCard(pin, zoomIfFar)
    }

    private fun clearSelectedMarker() {
        tappedTitleView = null
        tappedSubView = null
        tappedDistView = null
        if (::mapToolsController.isInitialized) mapToolsController.clearSelectedMarker()
    }

    private fun resetMapPadding() {
        if (::mapToolsController.isInitialized) mapToolsController.resetMapPadding()
    }

    internal fun showInScreenSheet(
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
            sheetBehavior.setPeekHeight(targetPeek, !UiAnimations.isReducedMotion(this))
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
            isSheetPendingOpen = true
            inScreenSheet.post {
                if (!isFinishing && !isDestroyed && openToken == sheetOpenSeq) {
                    isSheetPendingOpen = false
                    sheetBehavior.state = if (expandToFit) {
                        BottomSheetBehavior.STATE_EXPANDED
                    } else {
                        BottomSheetBehavior.STATE_COLLAPSED
                    }
                }
            }
        }
    }

    internal fun clearSheetStickyHeader() {
        if (::sheetStickyHeader.isInitialized) {
            sheetStickyHeader.removeAllViews()
            sheetStickyHeader.visibility = View.GONE
        }
    }

    internal fun hideInScreenSheet() {
        hideSoftKeyboard()
        val wasPending = isSheetPendingOpen
        isSheetPendingOpen = false
        sheetOpenSeq++
        if (wasPending && sheetBehavior.state == BottomSheetBehavior.STATE_HIDDEN) {
            sheetQuickSelectPinId = null
            clearSheetStickyHeader()
            clearSelectedMarker()
            if (::navigationController.isInitialized) navigationController.cancelPreviewOnSheetClosed()
            if (activeNavRoute == null) {
                resetMapPadding()
            }
            if (!isFullScreenMapMode) {
                bottomDock.translationY = 0f
                UiAnimations.fadeShow(bottomDock, 165L)
            }
        }
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

        clearSheetStickyHeader()
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
                text = AppStrings.stackedSummary(this@MainActivity, stackedPins.size, currentIdx + 1, stackedPins.size)
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
                text = AppStrings.nextShort(this@MainActivity)
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
            " • ${AppStrings.distanceAway(this, formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, pin.lat, pin.lng), speedUnit))}"
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
            text = DesignSystem.isolateBidi(layerName)
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
                        UiAnimations.hapticConfirm(sheetContent)
                        savePinStatusUnified(pin, key, stamp)
                        fireStatusPulse(pin.lat, pin.lng, statusColorHex(key))
                        renderMapData()
                        showStatusUndo(pin, AppStrings.markedPending(this@MainActivity))
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
            val stampTime = if (pin.editedAt > 0L) TimeFormatters.canonicalHistory(pin.editedAt) else ""
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

        val prevTapped = viewModel.tappedLocation
        viewModel.tappedLocation = TappedLocationState(title, subtitle, lat, lng)
        val tappedResolvedTitle = title
        val tappedResolvedSub = subtitle
        clearSheetStickyHeader()
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
            AppStrings.distanceAway(this, formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, lat, lng), unit))
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
                        val latestSub = viewModel.tappedLocation?.subtitle ?: tappedResolvedSub
                        val fitted = fitCleanSubtitle(latestSub, subTv, subMaxW)
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
            text = DesignSystem.isolateBidi(subtitle)
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
                val titleNow = viewModel.tappedLocation?.title ?: tappedResolvedTitle
                val msg = makePinShareMessage(titleNow, lat, lng)
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
            setOnClickListener { openRoutePreview(lat, lng, viewModel.tappedLocation?.title ?: tappedResolvedTitle) }
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
            setOnClickListener { setRouteOrigin(lat, lng, viewModel.tappedLocation?.title ?: tappedResolvedTitle) }
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
                val curTitle = viewModel.tappedLocation?.title ?: tappedResolvedTitle
                val curSub = viewModel.tappedLocation?.subtitle ?: tappedResolvedSub
                val preName = if (curTitle == "Selected Location" || curTitle == "Shared Location") "" else curTitle
                showAddPinDialog(LatLng(lat, lng), prefillName = preName, prefillDesc = curSub)
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
        navigationController.openRoutePreview(destLat, destLng, destTitle, modeOverride)
    }

    private fun moveCameraForNavigation(loc: Location, durationMs: Int = 500) {
        navigationController.moveCameraForNavigation(loc, durationMs)
    }

    private fun stopNavigation() {
        navigationController.stopNavigation()
    }

    private fun hasLocationPermission(): Boolean = gpsController.hasLocationPermission()
    private fun ensureLocationServiceOn(): Boolean = gpsController.ensureLocationServiceOn()
    private fun syncGpsPollingProfile(forceRestart: Boolean = false) = gpsController.syncGpsPollingProfile(forceRestart)
    private fun startLiveGpsUpdates(forceRestart: Boolean = false) = gpsController.startLiveGpsUpdates(forceRestart)
    private fun stopLiveGpsUpdates() = gpsController.stopLiveGpsUpdates()
    internal fun updateSpeedBadge(loc: Location?) = gpsController.updateSpeedBadge(loc)
    private fun updateLocationIconTint() = gpsController.updateLocationIconTint()

    internal fun updateFilterIconTint() {
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

    internal fun updateScaleBar() {
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

    internal fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        val token = currentFocus?.windowToken ?: window.decorView.windowToken
        imm?.hideSoftInputFromWindow(token, 0)
        currentFocus?.clearFocus()
    }

    internal fun openFullPage(builder: () -> View) {
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

    internal fun pushSubPage(onBack: () -> Unit, builder: () -> View) {
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

    internal fun popSubPage(builder: () -> View) {
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

    internal fun closeFullPage() {
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

    internal fun makePageFrame(
        title: String,
        onBack: () -> Unit,
        rightAction: View? = null,
        scrollable: Boolean = true
    ): Pair<LinearLayout, LinearLayout> {
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

        if (scrollable) {
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
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        outer.addView(body)
        return outer to body
    }

    // --- Page 2: Unified Bookmarks (KML Folders + Cross-Folder Search + Bottom Toggles) ---

    internal fun buildBookmarksAndTracksPage(): View {
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

        val bmSearchAdapter = BookmarkSearchAdapter(p)
        val searchRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = bmSearchAdapter
            itemAnimator = null
            clipToPadding = false
            setPadding(dp(16), dp(4), dp(16), dp(16))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        outer.addView(searchRecycler)

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
                RoutingEngine.uiQueryExecutor.execute {
                    val index = getOrBuildSearchIndex(ver, pinsSnap, folderNameById)
                    val matches = filterSearchEntries(index, tokens, hasDigits, loc, unit)
                    val rows = ArrayList<SearchRowItem>(matches.size + 2).apply {
                        add(SearchRowItem.Header("${matches.size} match${if (matches.size == 1) "" else "es"} across all folders"))
                        if (matches.isEmpty()) {
                            add(SearchRowItem.WebStatus("No bookmarks matched \"$trimmed\".", showRetry = false))
                        } else {
                            matches.forEachIndexed { idx, (entry, dist) ->
                                add(
                                    SearchRowItem.BookmarkMatch(
                                        entry = entry,
                                        distText = dist,
                                        showFolderInSub = true,
                                        isFirst = idx == 0,
                                        isLast = idx == matches.lastIndex
                                    )
                                )
                            }
                        }
                    }
                    runOnUiThread {
                        if (isFinishing || isDestroyed || mySeq != bookmarkSearchSeq) return@runOnUiThread
                        scroll.visibility = View.GONE
                        searchRecycler.visibility = View.VISIBLE
                        bmSearchAdapter.submitList(rows)
                    }
                }
                return
            }

            searchRecycler.visibility = View.GONE
            scroll.visibility = View.VISIBLE
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
                                            val leftPinIds = pinsByLayerId[layer.id]?.map { it.id }.orEmpty()
                                            DevicePrefs.removeJoinedLayerId(this@MainActivity, layer.id)
                                            DevicePrefs.removePinReferences(this@MainActivity, leftPinIds)
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

    internal fun buildLocalDeviceAliasMap(): Map<String, String> = repository.buildLocalDeviceAliasMap()

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
                        repository.renameLayer(layer, next, ::notifySyncError)
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
        }, topActions, scrollable = false)

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

        val listCard = FrameLayout(this).apply {
            DesignSystem.applyGroupedCardSurface(this@MainActivity, this, p, radiusDp = 16f)
            clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        listCard.addView(recycler)
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

        var latestFiltered: List<PinItem> = emptyList()
        var cachedAnalysisKey: Pair<String, Long>? = null
        var cachedDupExtras: Set<String> = emptySet()
        var cachedSameCoordExtras: Set<String> = emptySet()
        var cachedClusterSizes: Map<String, Int> = emptyMap()
        var renderSeq = 0
        var analysisSeq = 0
        lateinit var pinAdapter: ListAdapter<PinListRowItem, RecyclerView.ViewHolder>
        lateinit var rebuildSubmittedRows: () -> Unit

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

        var latestSortedPins: List<PinItem> = emptyList()
        var latestDistLabelById: Map<String, String> = emptyMap()

        pinAdapter = object : ListAdapter<PinListRowItem, RecyclerView.ViewHolder>(
            object : DiffUtil.ItemCallback<PinListRowItem>() {
                override fun areItemsTheSame(o: PinListRowItem, n: PinListRowItem) = o.stableId == n.stableId
                override fun areContentsTheSame(o: PinListRowItem, n: PinListRowItem) = o == n
            }
        ) {
            inner class MsgVH(val tv: TextView) : RecyclerView.ViewHolder(tv)
            inner class PinVH(
                val wrap: LinearLayout,
                val divider: View,
                val clickRow: LinearLayout,
                val checkBadge: TextView,
                val dot: View,
                val tvTitle: TextView,
                val tvGpsBadge: TextView,
                val tvSub: TextView,
                val tvDist: TextView
            ) : RecyclerView.ViewHolder(wrap)

            override fun getItemViewType(position: Int): Int = when (getItem(position)) {
                is PinListRowItem.MessageRow -> 0
                is PinListRowItem.PinRow -> 1
                is PinListRowItem.ShareCodeRow -> 2
            }

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                return when (viewType) {
                    0, 2 -> MsgVH(TextView(this@MainActivity).apply {
                        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        textSize = if (viewType == 0) 13f else 12.5f
                        setTextColor(txtSub)
                        setPadding(dp(16), dp(14), dp(16), dp(14))
                    })
                    else -> {
                        val wrap = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        }
                        val divider = DesignSystem.createHairlineDivider(this@MainActivity, p, 16, 16)
                        val row = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            minimumHeight = dp(52)
                            setPadding(dp(14), dp(11), dp(14), dp(11))
                        }
                        val badge = TextView(this@MainActivity).apply {
                            textSize = 13f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(Color.WHITE)
                            gravity = Gravity.CENTER
                            layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(12) }
                        }
                        val dot = View(this@MainActivity).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(12) }
                        }
                        val col = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                marginEnd = dp(8)
                            }
                        }
                        val nameRow = LinearLayout(this@MainActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                        }
                        val tvTitle = TextView(this@MainActivity).apply {
                            textSize = 15f
                            setTextColor(txtPrimary)
                            setTypeface(null, Typeface.BOLD)
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        val tvGps = TextView(this@MainActivity).apply {
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
                        }
                        nameRow.addView(tvTitle)
                        nameRow.addView(tvGps)
                        val tvSub = TextView(this@MainActivity).apply {
                            textSize = 12f
                            maxLines = 1
                            ellipsize = TextUtils.TruncateAt.END
                        }
                        col.addView(nameRow)
                        col.addView(tvSub)
                        val tvDist = TextView(this@MainActivity).apply {
                            textSize = 12f
                            setTextColor(p.accent)
                            setTypeface(null, Typeface.BOLD)
                            DesignSystem.applyTabularNumerals(this)
                        }
                        row.addView(badge)
                        row.addView(dot)
                        row.addView(col)
                        row.addView(tvDist)
                        wrap.addView(divider)
                        wrap.addView(row)
                        PinVH(wrap, divider, row, badge, dot, tvTitle, tvGps, tvSub, tvDist)
                    }
                }
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                when (val item = getItem(position)) {
                    is PinListRowItem.MessageRow -> (holder as MsgVH).tv.text = item.text
                    is PinListRowItem.ShareCodeRow -> (holder as MsgVH).tv.text = "Share Code: ${item.shareCode}"
                    is PinListRowItem.PinRow -> {
                        val vh = holder as PinVH
                        val pin = item.pin
                        vh.divider.visibility = if (item.isFirst) View.GONE else View.VISIBLE
                        if (item.selectionMode && item.isSelected) {
                            vh.clickRow.setBackgroundColor(p.accentSoft)
                        } else {
                            UiAnimations.applyRowRipple(this@MainActivity, vh.clickRow, night)
                        }
                        if (item.selectionMode) {
                            vh.dot.visibility = View.GONE
                            vh.checkBadge.visibility = View.VISIBLE
                            vh.checkBadge.text = if (item.isSelected) "✓" else ""
                            vh.checkBadge.background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                if (item.isSelected) {
                                    setColor(p.accent)
                                } else {
                                    setColor(Color.TRANSPARENT)
                                    setStroke(dp(2), p.iconMuted)
                                }
                            }
                        } else {
                            vh.checkBadge.visibility = View.GONE
                            vh.dot.visibility = View.VISIBLE
                            vh.dot.background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(Color.parseColor(statusColorHex(pin.status)))
                            }
                        }
                        vh.tvTitle.text = DesignSystem.isolateBidi(pin.name)
                        if (item.sameCount >= 2) {
                            vh.tvGpsBadge.text = "${item.sameCount}× GPS"
                            vh.tvGpsBadge.visibility = View.VISIBLE
                        } else {
                            vh.tvGpsBadge.visibility = View.GONE
                        }
                        val st = PinStatus.fromRaw(pin.status)
                        val cleanNote = DailyReportGenerator.flattenSingleLine(pin.issueReason)
                        val subParts = mutableListOf(st.label)
                        if (st == PinStatus.ISSUE && cleanNote.isNotBlank()) subParts += "⚠ $cleanNote"
                        else if (st == PinStatus.DONE && cleanNote.isNotBlank()) subParts += "✓ $cleanNote"
                        if (pin.isEdited && pin.editedBy.isNotBlank()) subParts += "Edited by ${pin.editedBy}"
                        vh.tvSub.text = DesignSystem.joinBidi(*subParts.toTypedArray())
                        vh.tvSub.setTextColor(
                            when {
                                st == PinStatus.ISSUE -> p.red
                                st == PinStatus.DONE && cleanNote.isNotBlank() -> p.green
                                else -> txtSub
                            }
                        )
                        if (item.distLabel != null) {
                            vh.tvDist.text = item.distLabel
                            vh.tvDist.visibility = View.VISIBLE
                        } else {
                            vh.tvDist.text = ""
                            vh.tvDist.visibility = View.GONE
                        }
                        vh.clickRow.setOnClickListener {
                            if (selectionMode) {
                                if (!selectedPinIds.add(pin.id)) selectedPinIds.remove(pin.id)
                                updateSelectionBarState()
                                rebuildSubmittedRows()
                            } else {
                                ensurePinVisibleOnMap(pin)
                                closeFullPage()
                                selectPinAndOpenCard(pin, zoomIfFar = true)
                            }
                        }
                        vh.clickRow.setOnLongClickListener {
                            if (!selectionMode) {
                                selectionMode = true
                                selectedPinIds.add(pin.id)
                                updateSelectionBarState()
                                rebuildSubmittedRows()
                                true
                            } else false
                        }
                    }
                }
            }
        }
        recycler.adapter = pinAdapter
        pinAdapter.submitList(listOf(PinListRowItem.MessageRow(AppStrings.loadingBookmarks(this))))

        rebuildSubmittedRows = {
            if (latestSortedPins.isEmpty()) {
                pinAdapter.submitList(listOf(PinListRowItem.MessageRow(AppStrings.noBookmarksMatchFilter(this@MainActivity))))
            } else {
                val rows = ArrayList<PinListRowItem>(latestSortedPins.size + 1)
                latestSortedPins.forEachIndexed { idx, pin ->
                    rows.add(
                        PinListRowItem.PinRow(
                            pin = pin,
                            isFirst = idx == 0,
                            isLast = idx == latestSortedPins.lastIndex,
                            selectionMode = selectionMode,
                            isSelected = pin.id in selectedPinIds,
                            sameCount = cachedClusterSizes[pin.id] ?: 0,
                            distLabel = latestDistLabelById[pin.id]
                        )
                    )
                }
                if (layer != null && layer.shareCode.isNotBlank()) {
                    rows.add(PinListRowItem.ShareCodeRow(layer.shareCode))
                }
                pinAdapter.submitList(rows)
            }
        }

        fun renderPinRows() {
            val mySeq = ++renderSeq
            btnSort.text = if (sortNearest) "Nearest ↓" else "A–Z ↓"
            filterBtns.forEach { (k, b) ->
                val active = k == filterKey
                DesignSystem.styleSegmentedButton(b, active, p)
            }

            val loc = currentLocation
            val unit = cachedSpeedUnit
            val curFilterKey = filterKey
            val curSortNearest = sortNearest
            val curSelectionMode = selectionMode
            val curSelectedSnap = if (curSelectionMode) selectedPinIds.toSet() else emptySet()
            val allInFolder = currentSourcePins().toList()
            val curKey = curFilterKey to dataVersion
            val prevCachedOk = cachedAnalysisKey == curKey
            val prevDupExtras = cachedDupExtras
            val prevSameExtras = cachedSameCoordExtras
            val prevClusterSizes = cachedClusterSizes

            RoutingEngine.uiQueryExecutor.execute {
                val filtered = if (curFilterKey == "all") {
                    allInFolder
                } else {
                    allInFolder.filter { PinStatus.fromRaw(it.status).key == curFilterKey }
                }
                val dupExtras: Set<String>
                val sameExtras: Set<String>
                val clusterSizes: Map<String, Int>
                if (prevCachedOk) {
                    dupExtras = prevDupExtras
                    sameExtras = prevSameExtras
                    clusterSizes = prevClusterSizes
                } else {
                    dupExtras = findDuplicateExtraIds(filtered)
                    val (sExtras, cSizes) = analyzeSameCoordClusters(filtered)
                    sameExtras = sExtras
                    clusterSizes = cSizes
                }

                val useNearest = curSortNearest && loc != null
                val sortedPins: List<PinItem>
                val distLabelById: Map<String, String>
                if (loc != null) {
                    val uLat = loc.latitude
                    val uLng = loc.longitude
                    val scored = ArrayList<Pair<PinItem, Double>>(filtered.size)
                    for (pin in filtered) {
                        scored.add(pin to RoutingEngine.haversineMeters(uLat, uLng, pin.lat, pin.lng))
                    }
                    if (useNearest) {
                        scored.sortWith(
                            compareBy<Pair<PinItem, Double>> {
                                if (curSelectionMode && curSelectedSnap.isNotEmpty() && it.first.id in curSelectedSnap) 0 else 1
                            }.thenBy { it.second }
                        )
                    } else {
                        scored.sortWith(
                            compareBy<Pair<PinItem, Double>> {
                                if (curSelectionMode && curSelectedSnap.isNotEmpty() && it.first.id in curSelectedSnap) 0 else 1
                            }.thenBy { it.first.name.lowercase(Locale.ROOT) }
                        )
                    }
                    sortedPins = scored.map { it.first }
                    val dMap = HashMap<String, String>(scored.size)
                    for ((pin, distM) in scored) {
                        dMap[pin.id] = formatDistanceShort(distM, unit)
                    }
                    distLabelById = dMap
                } else {
                    sortedPins = filtered.sortedWith(
                        compareBy<PinItem> {
                            if (curSelectionMode && curSelectedSnap.isNotEmpty() && it.id in curSelectedSnap) 0 else 1
                        }.thenBy { it.name.lowercase(Locale.ROOT) }
                    )
                    distLabelById = emptyMap()
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed || mySeq != renderSeq) return@runOnUiThread
                    latestFiltered = filtered
                    latestSortedPins = sortedPins
                    latestDistLabelById = distLabelById
                    if (!prevCachedOk) {
                        cachedAnalysisKey = curKey
                        cachedDupExtras = dupExtras
                        cachedSameCoordExtras = sameExtras
                        cachedClusterSizes = clusterSizes
                    }
                    updateSelectionBarState()
                    rebuildSubmittedRows()
                }
            }
        }

        btnSelectMode.setOnClickListener {
            selectionMode = !selectionMode
            if (!selectionMode) selectedPinIds.clear()
            renderPinRows()
        }
        btnAutoDuplicates.setOnClickListener {
            val curKey = filterKey to dataVersion
            fun applyDup(extras: Set<String>) {
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
            if (cachedAnalysisKey == curKey) {
                applyDup(cachedDupExtras)
            } else {
                val myASeq = ++analysisSeq
                val listSnap = if (latestFiltered.isNotEmpty()) {
                    latestFiltered
                } else {
                    val fk = filterKey
                    currentSourcePins().filter { fk == "all" || PinStatus.fromRaw(it.status).key == fk }
                }
                RoutingEngine.uiQueryExecutor.execute {
                    val dup = findDuplicateExtraIds(listSnap)
                    val (same, sizes) = analyzeSameCoordClusters(listSnap)
                    runOnUiThread {
                        if (isFinishing || isDestroyed || myASeq != analysisSeq) return@runOnUiThread
                        cachedAnalysisKey = curKey
                        cachedDupExtras = dup
                        cachedSameCoordExtras = same
                        cachedClusterSizes = sizes
                        applyDup(dup)
                    }
                }
            }
        }
        btnAutoSameCoords.setOnClickListener {
            val curKey = filterKey to dataVersion
            fun applySame(extras: Set<String>) {
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
            if (cachedAnalysisKey == curKey) {
                applySame(cachedSameCoordExtras)
            } else {
                val myASeq = ++analysisSeq
                val listSnap = if (latestFiltered.isNotEmpty()) {
                    latestFiltered
                } else {
                    val fk = filterKey
                    currentSourcePins().filter { fk == "all" || PinStatus.fromRaw(it.status).key == fk }
                }
                RoutingEngine.uiQueryExecutor.execute {
                    val dup = findDuplicateExtraIds(listSnap)
                    val (same, sizes) = analyzeSameCoordClusters(listSnap)
                    runOnUiThread {
                        if (isFinishing || isDestroyed || myASeq != analysisSeq) return@runOnUiThread
                        cachedAnalysisKey = curKey
                        cachedDupExtras = dup
                        cachedSameCoordExtras = same
                        cachedClusterSizes = sizes
                        applySame(same)
                    }
                }
            }
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
            renderPinRows()
        }
        filterBtns.forEach { (k, b) ->
            b.setOnClickListener { filterKey = k; renderPinRows() }
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

        val (outer, body) = makePageFrame(AppStrings.search(this), { closeFullPage() }, scrollable = false)

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
            hint = AppStrings.searchPlaceholder(this@MainActivity)
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
            setPadding(0, dp(10), 0, dp(10))
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

        var latestWebItems: List<SearchRowItem> = emptyList()
        var latestLocalItems: List<SearchRowItem> = emptyList()
        lateinit var searchAdapter: BookmarkSearchAdapter
        lateinit var triggerWebSearchRef: () -> Unit

        val searchRecycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            itemAnimator = null
            clipToPadding = false
            setPadding(0, dp(2), 0, dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        searchAdapter = BookmarkSearchAdapter(
            palette = p,
            onQuickFilter = { key ->
                if (key == "nearest") {
                    jumpToNearestPendingPin()
                } else {
                    etQuery.setText(key)
                    etQuery.setSelection(key.length)
                }
            },
            onRetryWebSearch = { triggerWebSearchRef() }
        )
        searchRecycler.adapter = searchAdapter
        body.addView(searchRecycler)

        fun publishSearchList(scrollToTop: Boolean = false) {
            val combined = ArrayList<SearchRowItem>(latestWebItems.size + latestLocalItems.size)
            combined.addAll(latestWebItems)
            combined.addAll(latestLocalItems)
            searchAdapter.submitList(combined) {
                if (scrollToTop && combined.isNotEmpty()) searchRecycler.scrollToPosition(0)
            }
        }

        var localSearchSeq = 0

        fun renderLocalMatches(q: String) {
            val mySeq = ++localSearchSeq
            val trimmed = q.trim()
            if (trimmed.isEmpty()) {
                webSearchSeq++
                latestWebItems = emptyList()
                latestLocalItems = listOf(SearchRowItem.HintAndChips(DevicePrefs.getAppLanguage(this).name))
                publishSearchList(scrollToTop = true)
                return
            }

            val ver = dataVersion
            val pinsSnapshot = pins
            val folderNameById = layers.associate { it.id to it.name }
            val tokens = trimmed.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
            val hasDigits = trimmed.any { it.isDigit() }
            val loc = currentLocation
            val unit = cachedSpeedUnit
            RoutingEngine.uiQueryExecutor.execute {
                val coordParts = trimmed.split(",").map { it.trim().toDoubleOrNull() }
                val parsedCoord: Pair<Double, Double>? = if (coordParts.size == 2 && coordParts[0] != null && coordParts[1] != null) {
                    val lat = coordParts[0]!!
                    val lng = coordParts[1]!!
                    if (lat in -90.0..90.0 && lng in -180.0..180.0) lat to lng else null
                } else null

                val index = getOrBuildSearchIndex(ver, pinsSnapshot, folderNameById)
                val matches = filterSearchEntries(index, tokens, hasDigits, loc, unit)
                val nextLocal = ArrayList<SearchRowItem>(matches.size + 3)
                if (parsedCoord != null) {
                    nextLocal.add(SearchRowItem.CoordJump(parsedCoord.first, parsedCoord.second))
                }
                if (matches.isNotEmpty()) {
                    nextLocal.add(SearchRowItem.Header(AppStrings.bookmarksCount(this@MainActivity, matches.size)))
                    matches.forEachIndexed { idx, (entry, dist) ->
                        nextLocal.add(
                            SearchRowItem.BookmarkMatch(
                                entry = entry,
                                distText = dist,
                                showFolderInSub = true,
                                isFirst = idx == 0,
                                isLast = idx == matches.lastIndex
                            )
                        )
                    }
                } else if (parsedCoord == null && latestWebItems.isEmpty()) {
                    nextLocal.add(SearchRowItem.WebStatus(AppStrings.noBookmarksMatchFilter(this@MainActivity), showRetry = false))
                }

                runOnUiThread {
                    if (isFinishing || isDestroyed || mySeq != localSearchSeq) return@runOnUiThread
                    latestLocalItems = nextLocal
                    publishSearchList()
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
            btnWebSearch.text = AppStrings.searching(this)
            val center = map?.cameraPosition?.target
            RoutingEngine.searchPlaces(q, center?.latitude, center?.longitude) { places, err ->
                runOnUiThread {
                    if (isFinishing || isDestroyed || seq != webSearchSeq) return@runOnUiThread
                    btnWebSearch.isEnabled = true
                    btnWebSearch.text = AppStrings.searchMapPlaces(this)
                    if (!err.isNullOrBlank()) {
                        latestWebItems = listOf(SearchRowItem.WebStatus(err, showRetry = true))
                        publishSearchList(scrollToTop = true)
                        return@runOnUiThread
                    }
                    if (places.isEmpty()) {
                        latestWebItems = listOf(SearchRowItem.WebStatus(AppStrings.noPlacesFound(this), showRetry = true))
                        publishSearchList(scrollToTop = true)
                        return@runOnUiThread
                    }
                    latestWebItems = ArrayList<SearchRowItem>(places.size + 1).apply {
                        add(SearchRowItem.Header(AppStrings.mapPlacesCount(this@MainActivity, places.size)))
                        places.forEachIndexed { idx, place ->
                            add(SearchRowItem.WebPlace(place, isFirst = idx == 0, isLast = idx == places.lastIndex))
                        }
                    }
                    publishSearchList(scrollToTop = true)
                }
            }
        }
        triggerWebSearchRef = ::runWebSearch

        etQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                runWebSearch()
                true
            } else false
        }
        btnWebSearch.setOnClickListener { runWebSearch() }
        activePageCleanup = {
            localSearchSeq++
            webSearchSeq++
            RoutingEngine.cancelPendingSearch()
            searchPageRunnable?.let { etQuery.removeCallbacks(it) }
        }

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

        clearSheetStickyHeader()
        sheetContent.removeAllViews()
        val layoutDir = if (AppStrings.isRtl(this)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        sheetContent.layoutDirection = layoutDir
        sheetStickyHeader.layoutDirection = layoutDir

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
            text = if (isIssue) AppStrings.reportFieldIssue(this@MainActivity) else AppStrings.markAsDone(this@MainActivity)
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
        sheetStickyHeader.addView(header)
        sheetStickyHeader.visibility = View.VISIBLE

        val etReason = EditText(this).apply {
            hint = if (isIssue) AppStrings.customIssueHint(this@MainActivity) else AppStrings.optionalNoteHint(this@MainActivity)
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
                Toast.makeText(this, AppStrings.enterIssuePrompt(this), Toast.LENGTH_SHORT).show()
                return
            }
            committed = true
            UiAnimations.hapticConfirm(sheetContent)
            val stamp = DevicePrefs.getDeviceStamp(this)
            savePinStatusUnified(pin, statusKey, stamp, clean)
            fireStatusPulse(pin.lat, pin.lng, statusColorHex(statusKey))
            renderMapData()
            val undoMsg = if (isIssue) {
                "${AppStrings.markedIssue(this)}: ${AppStrings.presetLabel(this, clean)}"
            } else if (clean.isNotBlank()) {
                "${AppStrings.markedDone(this)}: ${AppStrings.presetLabel(this, clean)}"
            } else {
                AppStrings.markedDone(this)
            }
            showStatusUndo(pin, undoMsg)
            hideInScreenSheet()
        }

        // Grouped rounded card of 1-tap presets (48dp touch rows; localized display with canonical value)
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
                text = AppStrings.presetLabel(this@MainActivity, preset)
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = AppStrings.tap(this@MainActivity)
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
            text = AppStrings.cancel(this@MainActivity)
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
            text = if (isIssue) AppStrings.saveIssue(this@MainActivity) else AppStrings.saveDone(this@MainActivity)
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

        UiAnimations.animateContentSwap(sheetContent, forward = true)
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
                    val savedPin = repository.addManualPin(
                        etName.text.toString().trim(),
                        etDesc.text.toString().trim(),
                        latLng.latitude,
                        latLng.longitude,
                        stamp,
                        devId
                    )
                    DevicePrefs.setLayerVisible(this, savedPin.layerId, true)
                    ensurePinVisibleOnMap(savedPin)
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
            val btnPos = dlg.getButton(AlertDialog.BUTTON_POSITIVE)
            btnPos.setOnClickListener {
                if (selectedIds.size < 2) {
                    Toast.makeText(this, "Select at least 2 folders", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val selIdsSnap = selectedIds.toSet()
                val combinedName = etName.text.toString().trim().ifBlank {
                    "Combined (${selIdsSnap.size} folders)"
                }
                val doDedup = cbDedup.isChecked
                val doHideOriginals = cbHideOriginals.isChecked
                val pinsSnap = pins.toList()
                val tracksSnap = tracks.toList()
                val newLayerId = LocalFolderStore.newLocalLayerId()
                val stamp = DevicePrefs.getDeviceStamp(this)
                val devId = DevicePrefs.getDeviceId(this)
                val uniqueName = makeUniqueLayerName(combinedName)
                btnPos.isEnabled = false
                btnPos.text = "Combining..."

                RoutingEngine.ioExecutor.execute {
                    val rawPins = pinsSnap.filter { it.layerId in selIdsSnap }
                    val rawTracks = tracksSnap.filter { it.layerId in selIdsSnap }
                    val finalPins = if (doDedup) {
                        DailyReportGenerator.deduplicatePins(rawPins)
                    } else {
                        rawPins
                    }

                    if (finalPins.isEmpty() && rawTracks.isEmpty()) {
                        runOnUiThread {
                            if (isFinishing || isDestroyed) return@runOnUiThread
                            btnPos.isEnabled = true
                            btnPos.text = "Combine"
                            Toast.makeText(this, "Selected folders have no pins or tracks", Toast.LENGTH_SHORT).show()
                        }
                        return@execute
                    }

                    val skippedCount = rawPins.size - finalPins.size
                    val parsedCombined = ParsedKml(
                        pins = finalPins.map { it.copy(id = UUID.randomUUID().toString(), layerId = newLayerId) },
                        tracks = rawTracks.map { it.copy(id = UUID.randomUUID().toString(), layerId = newLayerId) },
                        documentName = uniqueName
                    )
                    LocalFolderStore.saveNewLayer(applicationContext, newLayerId, uniqueName, stamp, devId, parsedCombined)

                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        DevicePrefs.addCachedLayerName(this, uniqueName)
                        DevicePrefs.setLayerVisible(this, newLayerId, true)
                        if (doHideOriginals) {
                            selIdsSnap.forEach { oldId ->
                                DevicePrefs.setLayerVisible(this, oldId, false)
                            }
                        }
                        applyMergedFoldersAndRender()
                        val skipNote = if (skippedCount > 0) " ($skippedCount duplicates skipped)" else ""
                        Toast.makeText(this, "Combined ${finalPins.size} pins locally into \"$uniqueName\"$skipNote", Toast.LENGTH_LONG).show()
                        dlg.dismiss()
                    }
                }
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
                    repository.deleteLocalLayer(layer.id)
                    Toast.makeText(this, "Deleted local folder \"${layer.name}\"", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .create(),
            destructivePositive = true
        )
    }

    internal fun isLayerOwner(layer: LayerItem?): Boolean = repository.isLayerOwner(layer)

    private fun notifySyncError(err: String) {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            Toast.makeText(this, "Sync failed: $err", Toast.LENGTH_LONG).show()
        }
    }

    private fun savePinStatusUnified(pin: PinItem, status: String, stamp: String, reason: String = "") {
        repository.savePinStatus(pin, status, stamp, reason, ::notifySyncError)
    }

    private fun savePinsStatusBatchUnified(targetPins: List<PinItem>, status: String, stamp: String, reason: String = "") {
        repository.savePinsStatusBatch(targetPins, status, stamp, reason, ::notifySyncError)
    }

    private fun savePinTextUnified(pin: PinItem, newName: String, newDesc: String, stamp: String) {
        repository.savePinText(pin, newName, newDesc, stamp, ::notifySyncError)
    }

    internal fun deletePinHistoryEntryUnified(pin: PinItem, idx: Int): PinItem {
        return repository.deletePinHistoryEntry(pin, idx, ::notifySyncError)
    }

    internal fun restorePinStatusSnapshotUnified(previousPin: PinItem) {
        repository.restorePinStatusSnapshot(previousPin, ::notifySyncError)
    }

    private fun showStatusUndo(previousPin: PinItem, summaryMsg: String) {
        undoController.show(this, previousPin, summaryMsg) { snap ->
            restorePinStatusSnapshotUnified(snap)
            val prevStatusKey = PinStatus.fromRaw(snap.status).key
            fireStatusPulse(snap.lat, snap.lng, statusColorHex(prevStatusKey))
            renderMapData()
            Toast.makeText(this, AppStrings.undone(this), Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetPinStatusUnified(pin: PinItem): PinItem {
        return repository.resetPinStatus(pin, ::notifySyncError)
    }

    private fun deletePinUnified(pin: PinItem) {
        repository.deletePin(pin, ::notifySyncError)
    }

    private fun deletePinsUnified(pinsToDelete: List<PinItem>) {
        repository.deletePins(pinsToDelete, ::notifySyncError)
    }

    internal fun applyExtraMapToolsVisibility() {
        val show = DevicePrefs.isShowExtraMapTools(this)
        if (::btnLasso.isInitialized) {
            if (btnLasso.isAttachedToWindow && activeNavRoute == null) {
                UiAnimations.animateSpeedBadgeVisibility(btnLasso, show)
            } else {
                btnLasso.visibility = if (show && activeNavRoute == null) View.VISIBLE else View.GONE
            }
        }
        if (!show && isLassoMode) toggleLassoMode(false)
    }

    internal fun applyZoomButtonsVisibility() {
        if (!::zoomPillContainer.isInitialized) return
        val show = DevicePrefs.isShowZoomButtons(this)
        if (zoomPillContainer.isAttachedToWindow) {
            UiAnimations.animateSpeedBadgeVisibility(zoomPillContainer, show)
        } else {
            zoomPillContainer.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    private fun normalizeLayerNameKey(name: String): String = repository.normalizeLayerNameKey(name)

    private fun makeUniqueLayerName(baseName: String): String = repository.makeUniqueLayerName(baseName)

    private fun onRepositorySnapshotUpdated(snap: FolderIndexSnapshot) {
        viewModel.updateFolderSnapshot(snap)
        if (::mapRenderer.isInitialized) {
            val activeTrackIds = snap.tracks.mapTo(HashSet(snap.tracks.size)) { it.id }
            mapRenderer.retainTrackPointCache(activeTrackIds)
        }
        renderMapData()
        liveBookmarksRefresh?.invoke()
        livePinListRefresh?.invoke()
        liveEditHistoryRefresh?.invoke()
        if (isLassoMode) updateLassoUi()
    }

    private fun applyMergedFoldersAndRender() {
        repository.applyMergedFolders()
    }

    internal fun refreshJoinedSync() {
        repository.refreshJoinedSync { snap ->
            if (isFinishing || isDestroyed) return@refreshJoinedSync
            if (::mapToolsController.isInitialized) {
                mapToolsController.handleSyncedSnapshotForDeepLinkAndAutoZoom(snap)
            }
        }
    }

    private fun joinShareCode(rawCode: String) {
        repository.joinShareCode(rawCode) { ok, msg, layerId ->
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

    internal fun applyKeepAwakeFlag() {
        if (DevicePrefs.isKeepAwake(this) || activeNavRoute != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    internal fun safeStartActivity(intent: Intent, fallbackMsg: String = "No app available to handle this action") {
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, fallbackMsg, Toast.LENGTH_SHORT).show() }
    }

    internal fun copyToClipboard(label: String, value: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, value))
        Toast.makeText(this, "$label copied", Toast.LENGTH_SHORT).show()
    }

    private fun collectLayerLatLngs(layerPins: List<PinItem>, layerTracks: List<TrackItem>): List<LatLng> =
        mapToolsController.collectLayerLatLngs(layerPins, layerTracks)

    private fun zoomToKmlBounds(pts: List<LatLng>, layerIdToReveal: String? = null) =
        mapToolsController.zoomToKmlBounds(pts, layerIdToReveal)

    private fun zoomToPoints(pts: List<LatLng>) =
        mapToolsController.zoomToKmlBounds(pts)

    private fun getDisplayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx) ?: "Folder"
        }
        return uri.lastPathSegment ?: "Folder"
    }

    internal fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onStart() {
        super.onStart()
        isActivityStarted = true
        mapView.onStart()
    }
    @Suppress("DEPRECATION")
    internal fun getInstalledVersionName(): String {
        val fallback = BuildConfig.VERSION_NAME.trim().removePrefix("v").removePrefix("V")
        return runCatching {
            val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            (info.versionName ?: fallback).trim().removePrefix("v").removePrefix("V")
        }.getOrDefault(fallback)
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
        if (::mapRenderer.isInitialized) mapRenderer.pause()
        if (::gpsController.isInitialized) gpsController.pause()
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
        webSearchSeq++
        liveBookmarksRefresh = null
        livePinListRefresh = null
        liveEditHistoryRefresh = null
        activePageCleanup?.invoke()
        activePageCleanup = null
        pageOverlayContainer.animate().cancel()
        if (::undoController.isInitialized) undoController.dismiss(animate = false)
        contourManager.destroy()
        offlineMaps.setStateListener(null)
        if (::mapToolsController.isInitialized) mapToolsController.destroy()
        if (::navigationController.isInitialized) navigationController.destroy()
        if (::gpsController.isInitialized) gpsController.destroy()
        if (::mapRenderer.isInitialized) mapRenderer.destroy()
        if (::repository.isInitialized) repository.stop()
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
        if (::syncStatusContainer.isInitialized) {
            sizeCircle(syncStatusContainer, utilSize, 0)
            btnSyncStatus.setPadding(utilPad, utilPad, utilPad, utilPad)
        } else {
            sizeCircle(btnSyncStatus, utilSize, utilPad)
        }
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
            val curTapped = viewModel.tappedLocation
            if (sheetBehavior.state != BottomSheetBehavior.STATE_HIDDEN) {
                if (curPinId != null) {
                    pinById[curPinId]?.let { showPinCard(it, animateSwap = false) }
                } else if (curTapped != null) {
                    showTappedPlaceCard(curTapped.title, curTapped.subtitle, curTapped.lat, curTapped.lng)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
