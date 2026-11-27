package com.terrasync.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.Layer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.VectorSource
import java.util.WeakHashMap

/**
 * Organic Maps cartographic styling, highway route shields, high-DPI POI badges,
 * and multilingual (English + Kurdish/Arabic RTL) map label configuration for MapLibre GL.
 */
object MapCartography {

    const val MOSQUES_LAYER_ID = "terrasync-mosques-layer"
    const val POI_BADGES_LAYER_ID = "terrasync-poi-badges-layer"

    private class CachedStyleIndex(
        val backgroundLayers: List<BackgroundLayer>,
        val fillTargets: List<Pair<FillLayer, String>>,
        val lineTargets: List<Triple<LineLayer, Boolean, String>>,
        val shieldLayers: List<Pair<SymbolLayer, Boolean>>,
        val mainLabelLayers: List<Pair<SymbolLayer, Boolean>>,
        val poiTextLayers: List<SymbolLayer>,
        val building3dLayers: List<Layer>,
        var basePoiSuppressed: Boolean = false
    )

    private val styleCacheLock = Any()
    private val styleIndexCache = WeakHashMap<Style, CachedStyleIndex>()

    private fun getOrBuildStyleIndex(style: Style): CachedStyleIndex {
        synchronized(styleCacheLock) {
            styleIndexCache[style]?.let { return it }
        }
        val bg = ArrayList<BackgroundLayer>()
        val fills = ArrayList<Pair<FillLayer, String>>()
        val lines = ArrayList<Triple<LineLayer, Boolean, String>>()
        val shields = ArrayList<Pair<SymbolLayer, Boolean>>()
        val mainLabels = ArrayList<Pair<SymbolLayer, Boolean>>()
        val poiText = ArrayList<SymbolLayer>()
        val bldgs3d = ArrayList<Layer>()

        for (layer in style.layers) {
            val id = layer.id.lowercase()
            if (layer is FillExtrusionLayer ||
                (id.contains("building") && id.contains("3d"))
            ) {
                bldgs3d.add(layer)
            }
            when (layer) {
                is BackgroundLayer -> bg.add(layer)
                is FillLayer -> {
                    when (id) {
                        "landuse_residential", "landcover_wood", "landcover_grass", "park", "building", "water" ->
                            fills.add(layer to id)
                    }
                }
                is LineLayer -> {
                    val isMinorOrTrack = id.contains("minor") || id.contains("service") ||
                        id.contains("track") || id.contains("path") ||
                        id.contains("residential") || id.contains("unclassified")
                    val role = when {
                        id.contains("motorway") && id.contains("casing") -> "mw_casing"
                        id.contains("motorway") -> "mw"
                        (id.contains("trunk") || id.contains("primary") || id.contains("major_inner")) && !id.contains("casing") -> "major"
                        (id.contains("trunk") || id.contains("primary") || id.contains("major_casing")) && id.contains("casing") -> "major_casing"
                        id.contains("secondary") && !id.contains("casing") -> "sec"
                        id.contains("secondary") && id.contains("casing") -> "sec_casing"
                        (id.contains("road_minor") || id.contains("highway_minor") || id.contains("road_service") || id.contains("road_link")) && !id.contains("casing") -> "minor"
                        (id.contains("road_minor") || id.contains("road_service") || id.contains("road_link")) && id.contains("casing") -> "minor_casing"
                        else -> ""
                    }
                    if (isMinorOrTrack || role.isNotEmpty()) lines.add(Triple(layer, isMinorOrTrack, role))
                }
                is SymbolLayer -> {
                    val sl = layer.sourceLayer.lowercase()
                    when {
                        id.contains("shield") || id == "highway_ref" -> shields.add(layer to (id == "highway_ref"))
                        sl == "place" || sl == "water_name" || sl == "waterway" ||
                            sl == "mountain_peak" ||
                            (sl == "transportation_name" && !id.contains("shield") && id != "highway_ref") ->
                            mainLabels.add(layer to (sl == "transportation_name"))
                        (sl == "poi" || id.contains("poi")) &&
                            id != MOSQUES_LAYER_ID && id != POI_BADGES_LAYER_ID -> poiText.add(layer)
                    }
                }
            }
        }
        return CachedStyleIndex(bg, fills, lines, shields, mainLabels, poiText, bldgs3d).also { built ->
            synchronized(styleCacheLock) { styleIndexCache[style] = built }
        }
    }

    fun getBuilding3dLayers(style: Style): List<Layer> = getOrBuildStyleIndex(style).building3dLayers

    fun registerPinBitmaps(ctx: Context, style: Style) {
        val pinStyle = DevicePrefs.getMapPinStyle(ctx)
        fun addAndRecycle(id: String, bmp: Bitmap) {
            try {
                style.addImage(id, bmp)
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        fun makePin(hex: String): Bitmap = when (pinStyle) {
            MapPinStyle.CONCEPT -> MapBitmaps.createConceptDropletPin(ctx, hex, 1.0f)
            MapPinStyle.CLASSIC -> MapBitmaps.createBookmarkPin(ctx, hex, 1.0f)
        }
        addAndRecycle("pin-pending", makePin("#E69500"))
        addAndRecycle("pin-done", makePin("#2E8B47"))
        addAndRecycle("pin-issue", makePin("#D93025"))
    }

    fun registerMapBitmaps(ctx: Context, style: Style, isDark: Boolean) {
        fun addAndRecycle(id: String, bmp: Bitmap) {
            try {
                style.addImage(id, bmp)
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        // Register active user-selected bookmark pin style (Concept Droplet or Classic Teardrop)
        registerPinBitmaps(ctx, style)

        // Crisp Organic Maps white rectangular highway shields with thin black borders
        for (len in 1..6) {
            addAndRecycle("road_$len", MapBitmaps.createHighwayShield(ctx, len, isDark))
        }
        addAndRecycle("terrasync-highway-shield", MapBitmaps.createHighwayShield(ctx, 3, isDark))

        // High-DPI Organic Maps POI badges
        addAndRecycle("mosque-icon", MapBitmaps.createMosqueIcon(ctx))
        addAndRecycle("poi-fuel", MapBitmaps.createFuelIcon(ctx))
        addAndRecycle("poi-transit", MapBitmaps.createTransitIcon(ctx))
        addAndRecycle("poi-viewpoint", MapBitmaps.createViewpointIcon(ctx))
        addAndRecycle("poi-medical", MapBitmaps.createMedicalIcon(ctx))

        // Navigation arrow
        addAndRecycle("gps-arrow", MapBitmaps.createGpsArrow(ctx))
    }

    /**
     * Builds a MapLibre text-field Expression supporting bilingual English + Kurdish/Arabic
     * script labels with clean fallback and native ICU/HarfBuzz RTL shaping.
     */
    fun buildLabelExpression(lang: MapLabelLanguage): Expression {
        val en = Expression.coalesce(
            Expression.get("name:en"),
            Expression.get("name:latin"),
            Expression.get("name_en"),
            Expression.get("name")
        )
        val ku = Expression.coalesce(
            Expression.get("name:ku"),
            Expression.get("name:nonlatin"),
            Expression.get("name")
        )
        val ar = Expression.coalesce(
            Expression.get("name:ar"),
            Expression.get("name:nonlatin"),
            Expression.get("name")
        )
        val local = Expression.coalesce(
            Expression.get("name"),
            Expression.get("name:nonlatin"),
            Expression.get("name:latin")
        )

        return when (lang) {
            MapLabelLanguage.ENGLISH -> en
            MapLabelLanguage.KURDISH -> ku
            MapLabelLanguage.ARABIC -> ar
            MapLabelLanguage.LOCAL -> local
            MapLabelLanguage.BILINGUAL -> {
                val latinPart = Expression.coalesce(
                    Expression.get("name:en"),
                    Expression.get("name:latin"),
                    Expression.literal("")
                )
                val localScriptPart = Expression.coalesce(
                    Expression.get("name:ku"),
                    Expression.get("name:ar"),
                    Expression.get("name:nonlatin"),
                    Expression.literal("")
                )
                Expression.switchCase(
                    Expression.all(
                        Expression.not(Expression.eq(latinPart, Expression.literal(""))),
                        Expression.not(Expression.eq(localScriptPart, Expression.literal(""))),
                        Expression.not(Expression.eq(latinPart, localScriptPart))
                    ),
                    Expression.concat(latinPart, Expression.literal("\n"), localScriptPart),
                    Expression.coalesce(
                        Expression.get("name:en"),
                        Expression.get("name:latin"),
                        Expression.get("name:ku"),
                        Expression.get("name:ar"),
                        Expression.get("name")
                    )
                )
            }
        }
    }

    /** Resolves a tapped vector feature name respecting the user's MapLabelLanguage preference. */
    fun resolveFeatureDisplayName(props: com.google.gson.JsonObject, lang: MapLabelLanguage): String? {
        fun pick(vararg keys: String): String? = keys.firstNotNullOfOrNull { k ->
            if (props.has(k) && !props.get(k).isJsonNull) {
                props.get(k).asString.trim().takeIf { it.isNotBlank() }
            } else null
        }
        return when (lang) {
            MapLabelLanguage.ENGLISH -> pick("name:en", "name:latin", "name_en", "name")
            MapLabelLanguage.KURDISH -> pick("name:ku", "name:nonlatin", "name", "name:en")
            MapLabelLanguage.ARABIC -> pick("name:ar", "name:nonlatin", "name", "name:en")
            MapLabelLanguage.LOCAL -> pick("name", "name:nonlatin", "name:latin", "name:en")
            MapLabelLanguage.BILINGUAL -> {
                val en = pick("name:en", "name:latin")
                val loc = pick("name:ku", "name:ar", "name:nonlatin")
                when {
                    en != null && loc != null && !en.equals(loc, ignoreCase = true) -> "$en • $loc"
                    en != null -> en
                    loc != null -> loc
                    else -> pick("name")
                }
            }
        }
    }

    /**
     * Applies Organic Maps cartographic hierarchy, route shields, POI badges,
     * and language expressions to an OpenFreeMap vector style.
     */
    fun applyOrganicCartography(ctx: Context, style: Style, isDark: Boolean) {
        val mapLang = DevicePrefs.getMapLabelLanguage(ctx)
        val poiBehavior = DevicePrefs.getPoiLabelBehavior(ctx)
        val mainLabelExpr = buildLabelExpression(mapLang)
        val poiLabelExpr: Expression? = when (poiBehavior) {
            PoiLabelBehavior.MATCH_MAP -> mainLabelExpr
            PoiLabelBehavior.ENGLISH_ONLY -> buildLabelExpression(MapLabelLanguage.ENGLISH)
            PoiLabelBehavior.LOCAL_ONLY -> buildLabelExpression(MapLabelLanguage.LOCAL)
            PoiLabelBehavior.ICONS_ONLY -> null
        }

        runCatching {
            val idx = getOrBuildStyleIndex(style)
            for (layer in idx.backgroundLayers) {
                layer.setProperties(
                    PropertyFactory.backgroundColor(
                        Color.parseColor(if (isDark) "#14181F" else "#F7F6F2")
                    )
                )
            }
            for ((layer, id) in idx.fillTargets) {
                when (id) {
                    "landuse_residential" -> layer.setProperties(
                        PropertyFactory.fillColor(Color.parseColor(if (isDark) "#1A2029" else "#F2F0EA"))
                    )
                    "landcover_wood" -> layer.setProperties(
                        PropertyFactory.fillColor(Color.parseColor(if (isDark) "#1C2C22" else "#D2E6B8")),
                        PropertyFactory.fillOpacity(if (isDark) 0.75f else 0.54f)
                    )
                    "landcover_grass", "park" -> layer.setProperties(
                        PropertyFactory.fillColor(Color.parseColor(if (isDark) "#18261E" else "#DFEECC")),
                        PropertyFactory.fillOpacity(if (isDark) 0.70f else 0.48f)
                    )
                    "building" -> {
                        if (layer.minZoom > 13.0f) layer.minZoom = 13.0f
                        layer.setProperties(
                            PropertyFactory.fillColor(Color.parseColor(if (isDark) "#272F3C" else "#DBD7CD")),
                            PropertyFactory.fillOutlineColor(Color.parseColor(if (isDark) "#374254" else "#C2BDB0"))
                        )
                    }
                    "water" -> layer.setProperties(
                        PropertyFactory.fillColor(Color.parseColor(if (isDark) "#162A3D" else "#B8DBF2"))
                    )
                }
            }
            for ((layer, isMinorOrTrack, role) in idx.lineTargets) {
                if (isMinorOrTrack && layer.minZoom > 12.0f) {
                    layer.minZoom = 12.0f
                }
                when (role) {
                    "mw_casing" -> layer.setProperties(PropertyFactory.lineColor(Color.parseColor(if (isDark) "#7C4A0A" else "#C96D00")))
                    "mw" -> layer.setProperties(PropertyFactory.lineColor(Color.parseColor(if (isDark) "#D97706" else "#F59E0B")))
                    "major" -> layer.setProperties(PropertyFactory.lineColor(Color.parseColor(if (isDark) "#C28727" else "#FBBF24")))
                    "major_casing" -> layer.setProperties(PropertyFactory.lineColor(Color.parseColor(if (isDark) "#6B4C18" else "#D98A1B")))
                    "sec" -> layer.setProperties(PropertyFactory.lineColor(Color.parseColor(if (isDark) "#46402B" else "#FDE68A")))
                    "sec_casing" -> layer.setProperties(PropertyFactory.lineColor(Color.parseColor(if (isDark) "#2B2F3A" else "#D9C37E")))
                    "minor" -> layer.setProperties(PropertyFactory.lineColor(Color.parseColor(if (isDark) "#2A323F" else "#FFFFFF")))
                    "minor_casing" -> layer.setProperties(PropertyFactory.lineColor(Color.parseColor(if (isDark) "#181D26" else "#D6D3CB")))
                }
            }
            for ((layer, isHighwayRef) in idx.shieldLayers) {
                layer.setProperties(
                    PropertyFactory.textColor(Color.parseColor(if (isDark) "#F2F3F5" else "#1C1C1E")),
                    PropertyFactory.textHaloWidth(0f),
                    PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
                    PropertyFactory.textSize(10.5f)
                )
                if (isHighwayRef) {
                    layer.setProperties(
                        PropertyFactory.iconImage("terrasync-highway-shield"),
                        PropertyFactory.iconTextFit(Property.ICON_TEXT_FIT_BOTH),
                        PropertyFactory.iconTextFitPadding(arrayOf(2f, 5f, 2f, 5f))
                    )
                }
            }
            for ((layer, isRoadLabel) in idx.mainLabelLayers) {
                layer.setProperties(
                    PropertyFactory.textField(mainLabelExpr),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular"))
                )
                if (isDark && isRoadLabel) {
                    layer.setProperties(
                        PropertyFactory.textColor(Color.parseColor("#E8ECF2")),
                        PropertyFactory.textHaloColor(Color.parseColor("#14181F")),
                        PropertyFactory.textHaloWidth(1.6f)
                    )
                }
            }
            for (layer in idx.poiTextLayers) {
                if (poiLabelExpr == null) {
                    layer.setProperties(PropertyFactory.textField(Expression.literal("")))
                } else {
                    layer.setProperties(
                        PropertyFactory.textField(poiLabelExpr),
                        PropertyFactory.textFont(arrayOf("Noto Sans Regular"))
                    )
                }
            }

            attachOrganicPoiBadgeLayers(style, isDark, poiLabelExpr, idx)
        }
    }

    private fun attachOrganicPoiBadgeLayers(style: Style, isDark: Boolean, poiLabelExpr: Expression?, idx: CachedStyleIndex) {
        val vecSource = style.sources.firstOrNull { it is VectorSource } ?: return

        val isMosqueExpr = Expression.any(
            Expression.eq(Expression.get("subclass"), Expression.literal("mosque")),
            Expression.eq(Expression.get("subclass"), Expression.literal("muslim")),
            Expression.eq(Expression.get("subclass"), Expression.literal("islam")),
            Expression.all(
                Expression.eq(Expression.get("class"), Expression.literal("place_of_worship")),
                Expression.eq(Expression.get("religion"), Expression.literal("muslim"))
            )
        )

        val isCustomBadgePoiExpr = Expression.any(
            Expression.eq(Expression.get("subclass"), Expression.literal("fuel")),
            Expression.eq(Expression.get("class"), Expression.literal("fuel")),
            Expression.eq(Expression.get("subclass"), Expression.literal("bus_stop")),
            Expression.eq(Expression.get("subclass"), Expression.literal("bus_station")),
            Expression.eq(Expression.get("class"), Expression.literal("bus")),
            Expression.eq(Expression.get("subclass"), Expression.literal("viewpoint")),
            Expression.eq(Expression.get("subclass"), Expression.literal("hospital")),
            Expression.eq(Expression.get("subclass"), Expression.literal("clinic")),
            Expression.eq(Expression.get("subclass"), Expression.literal("pharmacy")),
            Expression.eq(Expression.get("class"), Expression.literal("hospital"))
        )

        val suppressBaseExpr = Expression.not(Expression.any(isMosqueExpr, isCustomBadgePoiExpr))

        // Suppress duplicates on base style POI layers (once per style load via CachedStyleIndex)
        if (!idx.basePoiSuppressed) {
            idx.basePoiSuppressed = true
            for (layer in idx.poiTextLayers) {
                runCatching {
                    val existing = layer.filter
                    if (existing == null) {
                        layer.setFilter(suppressBaseExpr)
                    } else {
                        val existingJson = existing.toString()
                        val alreadySuppressed = existingJson.contains("\"mosque\"") && existingJson.contains("\"bus_stop\"")
                        if (!alreadySuppressed) {
                            val isLegacyFilter = !existingJson.contains("[\"get\"") &&
                                !existingJson.contains("[\"match\"") &&
                                !existingJson.contains("[\"case\"")
                            if (isLegacyFilter) {
                                layer.setFilter(
                                    Expression.raw(
                                        """["all",$existingJson,["!in","subclass","mosque","muslim","islam","fuel","bus_stop","bus_station","viewpoint","hospital","clinic","pharmacy"]]"""
                                    )
                                )
                            } else {
                                layer.setFilter(Expression.all(existing, suppressBaseExpr))
                            }
                        }
                    }
                }
            }
        }

        val labelExpr = poiLabelExpr ?: Expression.literal("")
        val haloColor = if (isDark) "#14181F" else "#FFFFFF"

        // 1. Dedicated High-DPI Mosque Layer
        val existingMosque = style.getLayerAs<SymbolLayer>(MOSQUES_LAYER_ID)
        if (existingMosque != null) {
            existingMosque.setProperties(
                PropertyFactory.textField(labelExpr),
                PropertyFactory.textColor(if (isDark) "#F3D399" else "#5D4037"),
                PropertyFactory.textHaloColor(haloColor)
            )
        } else {
            val mosqueLayer = SymbolLayer(MOSQUES_LAYER_ID, vecSource.id).apply {
                sourceLayer = "poi"
                minZoom = 12.0f
                setFilter(isMosqueExpr)
                setProperties(
                    PropertyFactory.iconImage("mosque-icon"),
                    PropertyFactory.iconSize(0.92f),
                    PropertyFactory.iconAllowOverlap(false),
                    PropertyFactory.iconIgnorePlacement(false),
                    PropertyFactory.iconPadding(12f),
                    PropertyFactory.textField(labelExpr),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(10.5f),
                    PropertyFactory.textColor(if (isDark) "#F3D399" else "#5D4037"),
                    PropertyFactory.textHaloColor(haloColor),
                    PropertyFactory.textHaloWidth(1.6f),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textOffset(arrayOf(0f, 0.72f)),
                    PropertyFactory.textMaxWidth(9.0f),
                    PropertyFactory.textOptional(true),
                    PropertyFactory.textAllowOverlap(false)
                )
            }
            style.addLayer(mosqueLayer)
        }

        // 2. High-DPI Organic Maps POI Badges Layer (Fuel, Transit, Viewpoint, Medical)
        val badgeIconExpr = Expression.switchCase(
            Expression.any(
                Expression.eq(Expression.get("subclass"), Expression.literal("fuel")),
                Expression.eq(Expression.get("class"), Expression.literal("fuel"))
            ),
            Expression.literal("poi-fuel"),
            Expression.any(
                Expression.eq(Expression.get("subclass"), Expression.literal("bus_stop")),
                Expression.eq(Expression.get("subclass"), Expression.literal("bus_station")),
                Expression.eq(Expression.get("class"), Expression.literal("bus"))
            ),
            Expression.literal("poi-transit"),
            Expression.eq(Expression.get("subclass"), Expression.literal("viewpoint")),
            Expression.literal("poi-viewpoint"),
            Expression.literal("poi-medical")
        )

        val existingBadges = style.getLayerAs<SymbolLayer>(POI_BADGES_LAYER_ID)
        if (existingBadges != null) {
            existingBadges.setProperties(
                PropertyFactory.textField(labelExpr),
                PropertyFactory.textColor(if (isDark) "#D4DAE4" else "#3A3A3C"),
                PropertyFactory.textHaloColor(haloColor)
            )
        } else {
            val badgeLayer = SymbolLayer(POI_BADGES_LAYER_ID, vecSource.id).apply {
                sourceLayer = "poi"
                minZoom = 13.0f
                setFilter(isCustomBadgePoiExpr)
                setProperties(
                    PropertyFactory.iconImage(badgeIconExpr),
                    PropertyFactory.iconSize(0.88f),
                    PropertyFactory.iconAllowOverlap(false),
                    PropertyFactory.iconIgnorePlacement(false),
                    PropertyFactory.iconPadding(10f),
                    PropertyFactory.textField(labelExpr),
                    PropertyFactory.textFont(arrayOf("Noto Sans Regular")),
                    PropertyFactory.textSize(10f),
                    PropertyFactory.textColor(if (isDark) "#D4DAE4" else "#3A3A3C"),
                    PropertyFactory.textHaloColor(haloColor),
                    PropertyFactory.textHaloWidth(1.5f),
                    PropertyFactory.textAnchor(Property.TEXT_ANCHOR_TOP),
                    PropertyFactory.textOffset(arrayOf(0f, 0.72f)),
                    PropertyFactory.textMaxWidth(8.5f),
                    PropertyFactory.textOptional(true),
                    PropertyFactory.textAllowOverlap(false)
                )
            }
            style.addLayer(badgeLayer)
        }
    }
}
