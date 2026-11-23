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
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
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
import java.util.UUID

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TRACKS_SOURCE = "terrasync-tracks-source"
        private const val TRACKS_LAYER = "terrasync-tracks-layer"
        private const val PINS_SOURCE = "terrasync-pins-source"
        private const val PINS_LAYER = "terrasync-pins-layer"
        private const val USER_LOC_SOURCE = "terrasync-userloc-source"
        private const val USER_LOC_LAYER = "terrasync-userloc-layer"

        private const val STYLE_STREET = """
        {
          "version": 8,
          "sources": {
            "street": {
              "type": "raster",
              "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}"],
              "tileSize": 256,
              "maxzoom": 19
            }
          },
          "layers": [
            { "id": "street-layer", "type": "raster", "source": "street", "minzoom": 0, "maxzoom": 22 }
          ]
        }
        """

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
    private lateinit var statusBadge: TextView
    private lateinit var btnStyleToggle: MaterialButton
    private var map: MapLibreMap? = null

    private val repo = SyncRepo()
    private var layers: List<LayerItem> = emptyList()
    private var pins: List<PinItem> = emptyList()
    private var tracks: List<TrackItem> = emptyList()
    private var isLiveConnected = false
    private var isSatellite = false
    private var hasAutoZoomedInitial = false
    private var lastUserLoc: LatLng? = null

    private val kmlPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) handleImportUri(uri)
    }

    private val locPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            jumpToMyLocation()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        statusBadge = findViewById(R.id.statusBadge)
        btnStyleToggle = findViewById(R.id.btnStyleToggle)

        stylePill(statusBadge, "#CC0F172A")

        val root = findViewById<ViewGroup>(R.id.rootContainer)
        val topBar = findViewById<LinearLayout>(R.id.topBar)
        val fabStack = findViewById<LinearLayout>(R.id.fabStack)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val sys = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (topBar.layoutParams as ViewGroup.MarginLayoutParams).topMargin = sys.top + dp(10)
            (fabStack.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin = sys.bottom + dp(16)
            topBar.requestLayout()
            fabStack.requestLayout()
            insets
        }

        btnStyleToggle.setOnClickListener {
            isSatellite = !isSatellite
            btnStyleToggle.text = if (isSatellite) "Street" else "Satellite"
            loadMapStyle()
        }
        findViewById<MaterialButton>(R.id.btnLocate).setOnClickListener { requestOrLocate() }
        findViewById<MaterialButton>(R.id.btnImport).setOnClickListener { kmlPicker.launch(arrayOf("*/*")) }
        findViewById<MaterialButton>(R.id.btnLayers).setOnClickListener { showLayersAndPinsSheet() }

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isCompassEnabled = true
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.cameraPosition = CameraPosition.Builder()
                .target(LatLng(36.75, 43.88))
                .zoom(9.0)
                .build()

            loadMapStyle()

            m.addOnMapClickListener { latLng ->
                val screenPt = m.projection.toScreenLocation(latLng)
                val hitBox = RectF(screenPt.x - 52f, screenPt.y - 52f, screenPt.x + 52f, screenPt.y + 52f)
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

        repo.startListening { newLayers, newPins, newTracks, connected ->
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

    private fun loadMapStyle() {
        val m = map ?: return
        val json = if (isSatellite) STYLE_SATELLITE else STYLE_STREET
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
                    PropertyFactory.circleRadius(7f),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(3f)
                )
            )
        }
    }

    private fun renderMapData() {
        val hiddenLayerIds = layers.filter { !it.visible }.map { it.id }.toSet()
        val visiblePins = pins.filter { it.layerId !in hiddenLayerIds }
        val visibleTracks = tracks.filter { it.layerId !in hiddenLayerIds }

        val badgeColor = if (isLiveConnected) "#CC065F46" else "#CC92400E"
        val badgeText = if (isLiveConnected) {
            "● LIVE SYNC  •  ${visiblePins.size} pins"
        } else {
            "● CONNECTING  •  ${visiblePins.size} pins"
        }
        stylePill(statusBadge, badgeColor)
        statusBadge.text = badgeText

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

        lastUserLoc?.let { loc ->
            val f = Feature.fromGeometry(Point.fromLngLat(loc.longitude, loc.latitude))
            style.getSourceAs<GeoJsonSource>(USER_LOC_SOURCE)
                ?.setGeoJson(FeatureCollection.fromFeatures(listOf(f)))
        }
    }

    private fun handleImportUri(uri: Uri) {
        val fileName = getDisplayName(uri).substringBeforeLast('.')
        val layerId = UUID.randomUUID().toString()
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
                repo.importLayer(layerId, fileName.ifBlank { "Imported Layer" }, parsed) { ok, msg ->
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                        if (ok && parsed.pins.isNotEmpty()) {
                            zoomToPoints(parsed.pins.map { LatLng(it.lat, it.lng) })
                        }
                    }
                }
            }
        }.start()
    }

    private fun showPinSheet(pin: PinItem) {
        val dialog = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(24))
        }

        val layerName = layers.find { it.id == pin.layerId }?.name ?: "General"
        root.addView(TextView(this).apply {
            text = pin.name
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "$layerName  •  ${"%.6f".format(pin.lat)}, ${"%.6f".format(pin.lng)}"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(4), 0, dp(8))
        })
        if (pin.description.isNotBlank()) {
            root.addView(TextView(this).apply {
                text = pin.description
                textSize = 14f
                setPadding(0, 0, 0, dp(12))
            })
        }

        root.addView(TextView(this).apply {
            text = "Status (one-tap sync)"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, dp(4), 0, dp(8))
        })

        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            Triple("pending", "Pending", "#F59E0B"),
            Triple("done", "Done", "#22C55E"),
            Triple("issue", "Issue", "#EF4444")
        ).forEachIndexed { idx, (key, title, hex) ->
            val active = pin.status.equals(key, ignoreCase = true)
            val btn = MaterialButton(this).apply {
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
                    repo.updatePinStatus(pin.id, key)
                    dialog.dismiss()
                }
            }
            statusRow.addView(btn)
        }
        root.addView(statusRow)

        val navRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        navRow.addView(MaterialButton(this).apply {
            text = "Navigate"
            maxLines = 1
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2563EB"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1.4f).apply {
                marginEnd = dp(8)
            }
            setOnClickListener {
                val label = Uri.encode(pin.name)
                val geoUri = Uri.parse("geo:${pin.lat},${pin.lng}?q=${pin.lat},${pin.lng}($label)")
                val intent = Intent(Intent.ACTION_VIEW, geoUri)
                startActivity(Intent.createChooser(intent, "Navigate with"))
            }
        })
        navRow.addView(MaterialButton(this).apply {
            text = "Copy"
            maxLines = 1
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#334155"))
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 0.8f)
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("coords", "${pin.lat}, ${pin.lng}"))
                Toast.makeText(this@MainActivity, "Coordinates copied", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(navRow)

        root.addView(MaterialButton(this).apply {
            text = "Delete Pin"
            setTextColor(Color.parseColor("#EF4444"))
            setBackgroundColor(Color.TRANSPARENT)
            setOnClickListener {
                repo.deletePin(pin.id)
                dialog.dismiss()
            }
        })

        dialog.setContentView(root)
        dialog.show()
    }

    private fun showLayersAndPinsSheet() {
        val dialog = BottomSheetDialog(this)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(24))
        }

        root.addView(TextView(this).apply {
            text = "Layers (${layers.size})"
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(8))
        })

        if (layers.isEmpty()) {
            root.addView(TextView(this).apply {
                text = "No layers yet. Tap + Import KML/KMZ or long-press the map to drop a pin."
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 0, 0, dp(12))
            })
        } else {
            layers.forEach { layer ->
                val layerPins = pins.filter { it.layerId == layer.id }
                val layerTracks = tracks.filter { it.layerId == layer.id }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(4))
                }
                row.addView(CheckBox(this).apply {
                    isChecked = layer.visible
                    setOnCheckedChangeListener { _, checked ->
                        repo.toggleLayerVisibility(layer.id, checked)
                    }
                })
                row.addView(TextView(this).apply {
                    text = "${layer.name} (${layerPins.size} pins)"
                    textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener {
                        zoomToPoints(layerPins.map { LatLng(it.lat, it.lng) })
                        dialog.dismiss()
                    }
                })
                row.addView(MaterialButton(this).apply {
                    text = "Del"
                    textSize = 11f
                    setTextColor(Color.parseColor("#EF4444"))
                    setBackgroundColor(Color.TRANSPARENT)
                    setOnClickListener {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Delete ${layer.name}?")
                            .setMessage("Removes this layer and its ${layerPins.size} pins from Firestore.")
                            .setPositiveButton("Delete") { _, _ ->
                                repo.deleteLayer(layer.id, layerPins.map { it.id }, layerTracks.map { it.id })
                                dialog.dismiss()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                })
                root.addView(row)
            }
        }

        val doneCount = pins.count { it.status == "done" }
        val issueCount = pins.count { it.status == "issue" }
        val pendingCount = pins.size - doneCount - issueCount
        root.addView(TextView(this).apply {
            text = "Pins (${pins.size})  •  ✓$doneCount  !${issueCount}  •$pendingCount"
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(14), 0, dp(8))
        })

        pins.sortedBy { it.name }.forEach { pin ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
                setOnClickListener {
                    map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pin.lat, pin.lng), 17.0))
                    dialog.dismiss()
                    showPinSheet(pin)
                }
            }
            row.addView(View(this).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(statusColorHex(pin.status)))
                }
                layoutParams = LinearLayout.LayoutParams(dp(11), dp(11)).apply {
                    marginEnd = dp(10)
                }
            })
            row.addView(TextView(this).apply {
                text = "${pin.name}  (${statusLabel(pin.status)})"
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            root.addView(row)
        }

        scroll.addView(root)
        dialog.setContentView(scroll)
        dialog.show()
    }

    private fun showAddPinDialog(latLng: LatLng) {
        val input = EditText(this).apply { hint = "Pin name" }
        AlertDialog.Builder(this)
            .setTitle("Add Synced Pin")
            .setMessage("${"%.6f".format(latLng.latitude)}, ${"%.6f".format(latLng.longitude)}")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                repo.addManualPin(input.text.toString().trim(), latLng.latitude, latLng.longitude)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun requestOrLocate() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            jumpToMyLocation()
        } else {
            locPermLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    @SuppressLint("MissingPermission")
    private fun jumpToMyLocation() {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        val best: Location? = providers
            .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        if (best == null) {
            Toast.makeText(this, "Turn on GPS and wait a moment for a fix", Toast.LENGTH_SHORT).show()
            return
        }
        val target = LatLng(best.latitude, best.longitude)
        lastUserLoc = target
        renderMapData()
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, 16.5))
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

    private fun stylePill(tv: TextView, hex: String) {
        tv.background = GradientDrawable().apply {
            setColor(Color.parseColor(hex))
            cornerRadius = dp(20).toFloat()
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { super.onPause(); mapView.onPause() }
    override fun onStop() { super.onStop(); mapView.onStop() }
    override fun onLowMemory() { super.onLowMemory(); mapView.onLowMemory() }
    override fun onDestroy() { repo.stopListening(); mapView.onDestroy(); super.onDestroy() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); mapView.onSaveInstanceState(outState) }
}
