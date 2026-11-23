package com.terrasync.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarLocation
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Metadata
import androidx.car.app.model.Pane
import androidx.car.app.model.PaneTemplate
import androidx.car.app.model.Place
import androidx.car.app.model.PlaceListMapTemplate
import androidx.car.app.model.PlaceMarker
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

class TerraSyncCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = TerraSyncCarSession()
}

class TerraSyncCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return TerraSyncCarFoldersScreen(carContext)
    }
}

private class TerraSyncCarFoldersScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = SyncRepo()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var layers: List<LayerItem> = emptyList()
    private var pins: List<PinItem> = emptyList()
    private var loaded = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                val joined = DevicePrefs.getJoinedLayerIds(carContext)
                repo.syncJoinedLayers(joined) { newLayers, newPins, _, _ ->
                    layers = newLayers
                    pins = newPins
                    loaded = true
                    mainHandler.post { invalidate() }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                repo.stopListening()
            }
        })
    }

    override fun onGetTemplate(): Template {
        if (!loaded) {
            return ListTemplate.Builder()
                .setTitle("TerraSync")
                .setHeaderAction(Action.APP_ICON)
                .setLoading(true)
                .build()
        }

        val listBuilder = ItemList.Builder()
            .setNoItemsMessage("No bookmark folders synced yet. Import a KML on your phone.")

        val hidden = DevicePrefs.getHiddenLayerIds(carContext)
        val visiblePins = pins.filter { it.layerId !in hidden }

        if (visiblePins.isNotEmpty()) {
            val pendingCount = visiblePins.count { it.status.equals("pending", true) }
            val issueCount = visiblePins.count { it.status.equals("issue", true) }
            listBuilder.addItem(
                Row.Builder()
                    .setTitle("All Visible Pins (${visiblePins.size})")
                    .addText("Pending: $pendingCount  •  Issue: $issueCount")
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(
                            TerraSyncCarPinsScreen(carContext, "All Visible Pins", visiblePins, layers, repo)
                        )
                    }
                    .build()
            )
        }

        layers.take(5).forEach { layer ->
            val folderPins = pins.filter { it.layerId == layer.id }
            val pending = folderPins.count { it.status.equals("pending", true) }
            val issues = folderPins.count { it.status.equals("issue", true) }
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(layer.name.ifBlank { "Folder" })
                    .addText("${folderPins.size} pins  •  Pending: $pending  •  Issue: $issues")
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(
                            TerraSyncCarPinsScreen(carContext, layer.name, folderPins, layers, repo)
                        )
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("TerraSync Folders")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(listBuilder.build())
            .build()
    }
}

private class TerraSyncCarPinsScreen(
    carContext: CarContext,
    private val title: String,
    private val sourcePins: List<PinItem>,
    private val layers: List<LayerItem>,
    private val repo: SyncRepo
) : Screen(carContext) {

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(): Location? {
        val hasPerm = ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(carContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return null
        val lm = carContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    override fun onGetTemplate(): Template {
        val myLoc = getLastKnownLocation()
        val prioritized = sourcePins.sortedWith(
            compareBy<PinItem> {
                when (it.status.lowercase()) {
                    "issue" -> 0
                    "pending" -> 1
                    else -> 2
                }
            }.thenBy {
                if (myLoc != null) {
                    RoutingEngine.haversineMeters(myLoc.latitude, myLoc.longitude, it.lat, it.lng)
                } else 0.0
            }
        ).take(6)

        val listBuilder = ItemList.Builder()
            .setNoItemsMessage("No pins available in this folder.")

        prioritized.forEach { pin ->
            val meters = if (myLoc != null) {
                RoutingEngine.haversineMeters(myLoc.latitude, myLoc.longitude, pin.lat, pin.lng)
            } else 100.0

            val distanceObj = if (meters < 1000.0) {
                Distance.create(meters.coerceAtLeast(1.0), Distance.UNIT_METERS)
            } else {
                Distance.create(meters / 1000.0, Distance.UNIT_KILOMETERS_P1)
            }

            val folderName = layers.find { it.id == pin.layerId }?.name ?: "Pin"
            val details = buildString {
                append("  • ")
                append(statusLabel(pin.status))
                if (pin.status.equals("issue", true) && pin.issueReason.isNotBlank()) {
                    append(" (${pin.issueReason})")
                } else if (folderName.isNotBlank()) {
                    append(" • $folderName")
                }
            }

            val line = SpannableString(details).apply {
                setSpan(DistanceSpan.create(distanceObj), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            }

            val markerColor = when (pin.status.lowercase()) {
                "done" -> CarColor.GREEN
                "issue" -> CarColor.RED
                else -> CarColor.YELLOW
            }

            val place = Place.Builder(CarLocation.create(pin.lat, pin.lng))
                .setMarker(PlaceMarker.Builder().setColor(markerColor).build())
                .build()

            listBuilder.addItem(
                Row.Builder()
                    .setTitle(pin.name.ifBlank { "Pin" })
                    .addText(line)
                    .setMetadata(Metadata.Builder().setPlace(place).build())
                    .setBrowsable(true)
                    .setOnClickListener {
                        screenManager.push(
                            TerraSyncCarPinDetailScreen(carContext, pin, folderName, repo)
                        )
                    }
                    .build()
            )
        }

        val builder = PlaceListMapTemplate.Builder()
            .setTitle(title)
            .setHeaderAction(Action.BACK)
            .setItemList(listBuilder.build())

        if (myLoc != null) {
            builder.setCurrentLocationEnabled(true)
        }
        return builder.build()
    }
}

private class TerraSyncCarPinDetailScreen(
    carContext: CarContext,
    private val pin: PinItem,
    private val folderName: String,
    private val repo: SyncRepo
) : Screen(carContext) {

    private var currentStatus = pin.status

    override fun onGetTemplate(): Template {
        val paneBuilder = Pane.Builder()
            .addRow(
                Row.Builder()
                    .setTitle("Status: ${statusLabel(currentStatus)}")
                    .addText("Folder: $folderName")
                    .build()
            )
            .addRow(
                Row.Builder()
                    .setTitle("Coordinates")
                    .addText("${"%.6f".format(pin.lat)}, ${"%.6f".format(pin.lng)}")
                    .build()
            )

        if (pin.issueReason.isNotBlank()) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Issue")
                    .addText(pin.issueReason)
                    .build()
            )
        } else if (pin.description.isNotBlank()) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle("Notes")
                    .addText(pin.description.take(120))
                    .build()
            )
        }

        val navAction = Action.Builder()
            .setTitle("Navigate")
            .setBackgroundColor(CarColor.BLUE)
            .setOnClickListener {
                val uri = Uri.parse("geo:0,0?q=${pin.lat},${pin.lng}(${Uri.encode(pin.name)})")
                val navIntent = Intent(CarContext.ACTION_NAVIGATE, uri)
                runCatching {
                    carContext.startCarApp(navIntent)
                }.onFailure {
                    CarToast.makeText(carContext, "No car navigation app available", CarToast.LENGTH_SHORT).show()
                }
            }
            .build()
        paneBuilder.addAction(navAction)

        if (!currentStatus.equals("done", true)) {
            val doneAction = Action.Builder()
                .setTitle("Mark Done")
                .setBackgroundColor(CarColor.GREEN)
                .setOnClickListener {
                    val stamp = DevicePrefs.getDeviceStamp(carContext)
                    repo.updatePinStatus(pin, "done", stamp)
                    currentStatus = "done"
                    CarToast.makeText(carContext, "Marked Done", CarToast.LENGTH_SHORT).show()
                    invalidate()
                }
                .build()
            paneBuilder.addAction(doneAction)
        }

        return PaneTemplate.Builder(paneBuilder.build())
            .setTitle(pin.name.ifBlank { "Pin Details" })
            .setHeaderAction(Action.BACK)
            .build()
    }
}
