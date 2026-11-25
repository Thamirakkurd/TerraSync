package com.terrasync.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
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
    override fun onCreate() {
        super.onCreate()
        RoutingEngine.appContext = applicationContext
        val ver = runCatching {
            val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0)
            }
            (info.versionName ?: "2.8.0").trim().removePrefix("v").removePrefix("V")
        }.getOrDefault("2.8.0")
        RoutingEngine.userAgent = "TerraSync/$ver (https://github.com/Thamirakkurd/TerraSync)"
    }

    override fun createHostValidator(): HostValidator {
        return if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }
    }

    override fun onCreateSession(): Session = TerraSyncCarSession()
}

class TerraSyncCarSession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        return TerraSyncCarFoldersScreen(carContext)
    }
}

private class TerraSyncCarFoldersScreen(carContext: CarContext) : Screen(carContext) {
    private val repo = SyncRepo(carContext.applicationContext)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var layers: List<LayerItem> = emptyList()
    private var pins: List<PinItem> = emptyList()
    private var loaded = false
    private var destroyed = false

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                LocalFolderStore.preloadAsync(carContext)
                val joined = DevicePrefs.getJoinedLayerIds(carContext)
                repo.syncJoinedLayers(joined) { newLayers, newPins, _, _ ->
                    if (destroyed) return@syncJoinedLayers
                    LocalFolderStore.preloadAsync(carContext) { local ->
                        if (destroyed) return@preloadAsync
                        mainHandler.post {
                            if (destroyed) return@post
                            layers = (newLayers + local.layers).sortedByDescending { it.updatedAt }
                            pins = newPins + local.pins
                            loaded = true
                            invalidate()
                        }
                    }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                destroyed = true
                mainHandler.removeCallbacksAndMessages(null)
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
            val pendingCount = visiblePins.count { PinStatus.fromRaw(it.status) == PinStatus.PENDING }
            val issueCount = visiblePins.count { PinStatus.fromRaw(it.status) == PinStatus.ISSUE }
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
            val pending = folderPins.count { PinStatus.fromRaw(it.status) == PinStatus.PENDING }
            val issues = folderPins.count { PinStatus.fromRaw(it.status) == PinStatus.ISSUE }
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

    private val workingPins = sourcePins.toMutableList()

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
        val prioritized = workingPins.sortedWith(
            compareBy<PinItem> {
                when (PinStatus.fromRaw(it.status)) {
                    PinStatus.ISSUE -> 0
                    PinStatus.PENDING -> 1
                    PinStatus.DONE -> 2
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
            val pinStatus = PinStatus.fromRaw(pin.status)
            val details = buildString {
                append("  • ")
                append(pinStatus.label)
                if ((pinStatus == PinStatus.ISSUE || pinStatus == PinStatus.DONE) && pin.issueReason.isNotBlank()) {
                    append(" (${DailyReportGenerator.flattenSingleLine(pin.issueReason)})")
                } else if (folderName.isNotBlank()) {
                    append(" • $folderName")
                }
            }

            val line = SpannableString(details).apply {
                setSpan(DistanceSpan.create(distanceObj), 0, 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            }

            val markerColor = when (pinStatus) {
                PinStatus.DONE -> CarColor.GREEN
                PinStatus.ISSUE -> CarColor.RED
                PinStatus.PENDING -> CarColor.YELLOW
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
                            TerraSyncCarPinDetailScreen(carContext, pin, folderName, repo) {
                                val idx = workingPins.indexOfFirst { it.id == pin.id }
                                if (idx >= 0) workingPins[idx] = workingPins[idx].copy(status = "done", issueReason = "")
                                invalidate()
                            }
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
    private val repo: SyncRepo,
    private val onMarkedDone: () -> Unit
) : Screen(carContext) {

    private var currentStatus = pin.status
    private var currentReason = pin.issueReason

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
                    .addText("${"%.6f".format(java.util.Locale.US, pin.lat)}, ${"%.6f".format(java.util.Locale.US, pin.lng)}")
                    .build()
            )

        if (currentReason.isNotBlank() && (currentStatus.equals("issue", true) || currentStatus.equals("done", true))) {
            paneBuilder.addRow(
                Row.Builder()
                    .setTitle(if (currentStatus.equals("done", true)) "Done Note" else "Issue")
                    .addText(currentReason)
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
                    if (LocalFolderStore.isLocalLayerId(pin.layerId)) {
                        LocalFolderStore.updatePinStatus(carContext, pin, "done", stamp)
                    } else {
                        repo.updatePinStatus(pin, "done", stamp)
                    }
                    currentStatus = "done"
                    currentReason = ""
                    onMarkedDone()
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
