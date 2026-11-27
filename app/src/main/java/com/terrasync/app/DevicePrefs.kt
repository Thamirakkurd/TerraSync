package com.terrasync.app

import android.content.Context
import android.os.Build
import java.util.UUID

enum class HillshadeIntensity(val id: String, val label: String, val exaggeration: Float) {
    LIGHT("light", "Light", 0.36f),
    MEDIUM("medium", "Medium", 0.56f),
    STRONG("strong", "Strong", 0.78f);

    companion object {
        fun fromId(id: String?): HillshadeIntensity =
            entries.firstOrNull { it.id == id } ?: MEDIUM
    }
}

enum class AppLanguage(val id: String, val label: String, val isRtl: Boolean) {
    ENGLISH("en", "English", false),
    KURDISH_SORANI("ku", "کوردی (سۆرانی)", true),
    KURDISH_BADINI("ku_badini", "کوردی (بادینی)", true),
    ARABIC("ar", "العربية", true);

    companion object {
        fun fromId(id: String?): AppLanguage =
            entries.firstOrNull { it.id == id } ?: ENGLISH
    }
}

enum class MapLabelLanguage(val id: String, val label: String, val subtitle: String) {
    BILINGUAL("bilingual", "Bilingual", "English + Kurdish / Arabic"),
    ENGLISH("en", "English", "Latin / English labels"),
    KURDISH("ku", "Kurdish", "Kurdish script with fallback"),
    ARABIC("ar", "Arabic", "Arabic script with fallback"),
    LOCAL("local", "Local", "Native OSM tag names");

    companion object {
        fun fromId(id: String?): MapLabelLanguage =
            entries.firstOrNull { it.id == id } ?: BILINGUAL
    }
}

enum class PoiLabelBehavior(val id: String, val label: String) {
    MATCH_MAP("match", "Match Map"),
    ENGLISH_ONLY("en", "English"),
    LOCAL_ONLY("local", "Local"),
    ICONS_ONLY("icons", "Icons Only");

    companion object {
        fun fromId(id: String?): PoiLabelBehavior =
            entries.firstOrNull { it.id == id } ?: MATCH_MAP
    }
}

enum class SpeedUnit(val id: String, val label: String, val speedSuffix: String) {
    METRIC("metric", "km/h • km", "km/h"),
    IMPERIAL("imperial", "mph • mi", "mph");

    companion object {
        fun fromId(id: String?): SpeedUnit =
            entries.firstOrNull { it.id == id } ?: METRIC
    }
}

enum class MapPinStyle(val id: String, val label: String) {
    CONCEPT("concept", "Concept Droplet"),
    CLASSIC("classic", "Classic Teardrop");

    companion object {
        fun fromId(id: String?): MapPinStyle =
            entries.firstOrNull { it.id == id } ?: CONCEPT
    }
}

object DevicePrefs {
    private const val PREFS = "terrasync_prefs_v2"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_CUSTOM_NAME = "custom_device_name"
    private const val KEY_JOINED_LAYERS = "joined_layer_ids"
    private const val KEY_HIDDEN_LAYERS = "hidden_layer_ids"
    private const val KEY_BOOKMARKS = "bookmarked_pin_ids"
    private const val KEY_VISIBLE_STATUSES = "visible_pin_statuses"
    private const val KEY_SHOW_BOOKMARK_TITLES = "show_bookmark_titles"
    private const val KEY_MAP_STYLE_MODE = "map_style_mode"
    private const val KEY_DEFAULT_SAT = "default_satellite"
    private const val KEY_AUTO_FOLLOW = "auto_follow_gps"
    private const val KEY_KEEP_AWAKE = "keep_screen_awake"
    private const val KEY_VOICE_NAV = "voice_guidance"
    private const val KEY_HILLSHADE = "terrain_hillshade"
    private const val KEY_HILLSHADE_INTENSITY = "terrain_hillshade_intensity"
    private const val KEY_CONTOURS = "terrain_contours_enabled"
    private const val KEY_3D_BUILDINGS = "buildings_3d_enabled"
    private const val KEY_SCALE_BAR = "show_scale_bar"
    private const val KEY_TRAVEL_MODE = "travel_mode"
    private const val KEY_SPEED_INDICATOR = "speed_indicator_enabled"
    private const val KEY_SPEED_UNIT = "speed_unit_system"
    private const val KEY_LOCAL_SENT_PINS = "local_sent_pin_ids"
    private const val KEY_HIDE_SENT_PINS = "hide_sent_pins"
    private const val KEY_PINNED_LAYERS = "pinned_layer_ids"
    private const val KEY_SHOW_EXTRA_MAP_TOOLS = "show_extra_map_tools"
    private const val KEY_CACHED_LAYER_NAMES = "cached_layer_names"
    private const val KEY_THEME_MODE = "ui_theme_mode"
    private const val KEY_REDUCED_MOTION = "ui_reduced_motion"
    private const val KEY_APP_LANGUAGE = "app_ui_language"
    private const val KEY_MAP_LABEL_LANGUAGE = "map_label_language"
    private const val KEY_POI_LABEL_BEHAVIOR = "poi_label_behavior"
    private const val KEY_WORK_START_MIN = "work_hours_start_min"
    private const val KEY_WORK_END_MIN = "work_hours_end_min"
    private const val KEY_OPERATOR_SETUP_DONE = "operator_setup_prompt_done"
    private const val KEY_SHOW_ZOOM_BUTTONS = "show_zoom_buttons"
    private const val KEY_MAP_PIN_STYLE = "map_pin_style"
    private const val KEY_WORK_HOURS_PRESETS = "work_hours_presets"
    private const val KEY_CLUSTER_DENSE_PINS = "cluster_dense_pins"
    private const val KEY_PENDING_ROAD_CHAIN = "pending_road_chain_packs"

    val ALL_STATUSES = setOf("pending", "done", "issue")
    internal val DEFAULT_WORK_HOUR_PRESETS: List<Pair<Int, Int>> = listOf(
        8 * 60 to 16 * 60,
        8 * 60 to 17 * 60,
        7 * 60 to 15 * 60
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getDeviceId(ctx: Context): String {
        val p = prefs(ctx)
        var id = p.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
            p.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getCustomName(ctx: Context): String = prefs(ctx).getString(KEY_CUSTOM_NAME, "") ?: ""

    fun setCustomName(ctx: Context, name: String) {
        val clean = name.trim()
        prefs(ctx).edit()
            .putString(KEY_CUSTOM_NAME, clean)
            .apply { if (clean.isNotBlank()) putBoolean(KEY_OPERATOR_SETUP_DONE, true) }
            .apply()
    }

    fun isOperatorSetupDone(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_OPERATOR_SETUP_DONE, false) || getCustomName(ctx).isNotBlank()

    fun setOperatorSetupDone(ctx: Context, done: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_OPERATOR_SETUP_DONE, done).apply()
    }

    fun getDeviceStamp(ctx: Context): String {
        val id = getDeviceId(ctx)
        val mfg = Build.MANUFACTURER.trim().replaceFirstChar { it.uppercase() }
        val mdl = Build.MODEL.trim()
        val deviceModel = if (mdl.startsWith(mfg, ignoreCase = true)) mdl else "$mfg $mdl".trim()
        val custom = getCustomName(ctx)
        return if (custom.isNotBlank()) "$custom ($deviceModel • #$id)" else "$deviceModel • #$id"
    }

    fun getJoinedLayerIds(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_JOINED_LAYERS, emptySet())?.toSet() ?: emptySet()
    }

    fun addJoinedLayerId(ctx: Context, layerId: String) {
        val next = getJoinedLayerIds(ctx).toMutableSet().apply { add(layerId) }
        prefs(ctx).edit().putStringSet(KEY_JOINED_LAYERS, next).apply()
    }

    fun removeJoinedLayerId(ctx: Context, layerId: String) {
        val next = getJoinedLayerIds(ctx).toMutableSet().apply { remove(layerId) }
        val nextHidden = getHiddenLayerIds(ctx).toMutableSet().apply { remove(layerId) }
        val nextPinned = getPinnedLayerIds(ctx).toMutableSet().apply { remove(layerId) }
        prefs(ctx).edit()
            .putStringSet(KEY_JOINED_LAYERS, next)
            .putStringSet(KEY_HIDDEN_LAYERS, nextHidden)
            .putStringSet(KEY_PINNED_LAYERS, nextPinned)
            .apply()
    }

    fun getHiddenLayerIds(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_HIDDEN_LAYERS, emptySet())?.toSet() ?: emptySet()
    }

    fun isLayerVisible(ctx: Context, layerId: String): Boolean = !getHiddenLayerIds(ctx).contains(layerId)

    fun setLayerVisible(ctx: Context, layerId: String, visible: Boolean) {
        val next = getHiddenLayerIds(ctx).toMutableSet()
        if (visible) next.remove(layerId) else next.add(layerId)
        prefs(ctx).edit().putStringSet(KEY_HIDDEN_LAYERS, next).apply()
    }

    fun setAllLayersVisible(ctx: Context, allLayerIds: Collection<String>, visible: Boolean) {
        val next = if (visible) emptySet() else allLayerIds.toSet()
        prefs(ctx).edit().putStringSet(KEY_HIDDEN_LAYERS, next).apply()
    }

    fun getVisibleStatuses(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_VISIBLE_STATUSES, ALL_STATUSES)?.toSet() ?: ALL_STATUSES
    }

    fun setVisibleStatuses(ctx: Context, statuses: Set<String>) {
        prefs(ctx).edit().putStringSet(KEY_VISIBLE_STATUSES, statuses.toSet()).apply()
    }

    fun isShowBookmarkTitles(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_BOOKMARK_TITLES, false)
    fun setShowBookmarkTitles(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_SHOW_BOOKMARK_TITLES, v).apply()

    fun getMapStyleMode(ctx: Context): MapStyleMode {
        val p = prefs(ctx)
        if (!p.contains(KEY_MAP_STYLE_MODE)) {
            return if (p.getBoolean(KEY_DEFAULT_SAT, false)) MapStyleMode.SATELLITE else MapStyleMode.VECTOR
        }
        return MapStyleMode.fromId(p.getInt(KEY_MAP_STYLE_MODE, MapStyleMode.VECTOR.id))
    }

    fun setMapStyleMode(ctx: Context, mode: MapStyleMode) {
        prefs(ctx).edit().putInt(KEY_MAP_STYLE_MODE, mode.id).apply()
    }

    fun is3dBuildingsEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_3D_BUILDINGS, false)
    fun set3dBuildingsEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_3D_BUILDINGS, v).apply()

    fun isScaleBarEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SCALE_BAR, true)
    fun setScaleBarEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_SCALE_BAR, v).apply()

    fun getTravelMode(ctx: Context): TravelMode = TravelMode.fromId(prefs(ctx).getString(KEY_TRAVEL_MODE, TravelMode.CAR.id))
    fun setTravelMode(ctx: Context, mode: TravelMode) = prefs(ctx).edit().putString(KEY_TRAVEL_MODE, mode.id).apply()

    fun isSpeedIndicatorEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SPEED_INDICATOR, true)
    fun setSpeedIndicatorEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_SPEED_INDICATOR, v).apply()

    fun getSpeedUnit(ctx: Context): SpeedUnit = SpeedUnit.fromId(prefs(ctx).getString(KEY_SPEED_UNIT, SpeedUnit.METRIC.id))
    fun setSpeedUnit(ctx: Context, unit: SpeedUnit) = prefs(ctx).edit().putString(KEY_SPEED_UNIT, unit.id).apply()

    fun getBookmarks(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_BOOKMARKS, emptySet())?.toSet() ?: emptySet()
    }

    fun isBookmarked(ctx: Context, pinId: String): Boolean = getBookmarks(ctx).contains(pinId)

    fun toggleBookmark(ctx: Context, pinId: String): Boolean {
        val next = getBookmarks(ctx).toMutableSet()
        val added = if (next.contains(pinId)) {
            next.remove(pinId)
            false
        } else {
            next.add(pinId)
            true
        }
        prefs(ctx).edit().putStringSet(KEY_BOOKMARKS, next).apply()
        return added
    }

    fun isAutoFollow(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO_FOLLOW, true)
    fun setAutoFollow(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_AUTO_FOLLOW, v).apply()

    fun isKeepAwake(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_KEEP_AWAKE, true)
    fun setKeepAwake(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_KEEP_AWAKE, v).apply()

    fun isVoiceNav(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_VOICE_NAV, true)
    fun setVoiceNav(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_VOICE_NAV, v).apply()

    fun isHillshadeEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HILLSHADE, false)
    fun setHillshadeEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_HILLSHADE, v).apply()

    fun getHillshadeIntensity(ctx: Context): HillshadeIntensity =
        HillshadeIntensity.fromId(prefs(ctx).getString(KEY_HILLSHADE_INTENSITY, HillshadeIntensity.MEDIUM.id))
    fun setHillshadeIntensity(ctx: Context, intensity: HillshadeIntensity) =
        prefs(ctx).edit().putString(KEY_HILLSHADE_INTENSITY, intensity.id).apply()

    fun isContoursEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_CONTOURS, false)
    fun setContoursEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_CONTOURS, v).apply()

    fun getAppLanguage(ctx: Context): AppLanguage =
        AppLanguage.fromId(prefs(ctx).getString(KEY_APP_LANGUAGE, AppLanguage.ENGLISH.id))
    fun setAppLanguage(ctx: Context, lang: AppLanguage) =
        prefs(ctx).edit().putString(KEY_APP_LANGUAGE, lang.id).apply()

    fun getMapLabelLanguage(ctx: Context): MapLabelLanguage =
        MapLabelLanguage.fromId(prefs(ctx).getString(KEY_MAP_LABEL_LANGUAGE, MapLabelLanguage.BILINGUAL.id))
    fun setMapLabelLanguage(ctx: Context, lang: MapLabelLanguage) =
        prefs(ctx).edit().putString(KEY_MAP_LABEL_LANGUAGE, lang.id).apply()

    fun getPoiLabelBehavior(ctx: Context): PoiLabelBehavior =
        PoiLabelBehavior.fromId(prefs(ctx).getString(KEY_POI_LABEL_BEHAVIOR, PoiLabelBehavior.MATCH_MAP.id))
    fun setPoiLabelBehavior(ctx: Context, behavior: PoiLabelBehavior) =
        prefs(ctx).edit().putString(KEY_POI_LABEL_BEHAVIOR, behavior.id).apply()

    /** Working hours window in minutes since midnight (default 08:00 – 17:00). */
    fun getWorkStartMinutes(ctx: Context): Int = prefs(ctx).getInt(KEY_WORK_START_MIN, 8 * 60).coerceIn(0, 23 * 60 + 59)
    fun setWorkStartMinutes(ctx: Context, minutes: Int) =
        prefs(ctx).edit().putInt(KEY_WORK_START_MIN, minutes.coerceIn(0, 23 * 60 + 59)).apply()

    fun getWorkEndMinutes(ctx: Context): Int = prefs(ctx).getInt(KEY_WORK_END_MIN, 17 * 60).coerceIn(0, 23 * 60 + 59)
    fun setWorkEndMinutes(ctx: Context, minutes: Int) =
        prefs(ctx).edit().putInt(KEY_WORK_END_MIN, minutes.coerceIn(0, 23 * 60 + 59)).apply()

    fun getLocalSentPinIds(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_LOCAL_SENT_PINS, emptySet())?.toSet() ?: emptySet()
    }

    fun isPinLocalSent(ctx: Context, pinId: String): Boolean = getLocalSentPinIds(ctx).contains(pinId)

    fun addLocalSentPinIds(ctx: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val next = getLocalSentPinIds(ctx).toMutableSet().apply { addAll(ids) }
        prefs(ctx).edit().putStringSet(KEY_LOCAL_SENT_PINS, next).apply()
    }

    fun removeLocalSentPinIds(ctx: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val next = getLocalSentPinIds(ctx).toMutableSet().apply { removeAll(ids.toSet()) }
        prefs(ctx).edit().putStringSet(KEY_LOCAL_SENT_PINS, next).apply()
    }

    fun toggleLocalSentPin(ctx: Context, pinId: String): Boolean {
        val next = getLocalSentPinIds(ctx).toMutableSet()
        val nowSent = if (next.contains(pinId)) {
            next.remove(pinId)
            false
        } else {
            next.add(pinId)
            true
        }
        prefs(ctx).edit().putStringSet(KEY_LOCAL_SENT_PINS, next).apply()
        return nowSent
    }

    fun removePinReferences(ctx: Context, pinIds: Collection<String>) {
        if (pinIds.isEmpty()) return
        val drop = pinIds.toSet()
        val nextBookmarks = getBookmarks(ctx).toMutableSet().apply { removeAll(drop) }
        val nextSent = getLocalSentPinIds(ctx).toMutableSet().apply { removeAll(drop) }
        prefs(ctx).edit()
            .putStringSet(KEY_BOOKMARKS, nextBookmarks)
            .putStringSet(KEY_LOCAL_SENT_PINS, nextSent)
            .apply()
    }

    fun prunePinReferencesKeeping(ctx: Context, validPinIds: Set<String>) {
        val curBookmarks = getBookmarks(ctx)
        val curSent = getLocalSentPinIds(ctx)
        val prunedBookmarks = curBookmarks.filterTo(HashSet()) { it in validPinIds }
        val prunedSent = curSent.filterTo(HashSet()) { it in validPinIds }
        if (prunedBookmarks.size == curBookmarks.size && prunedSent.size == curSent.size) return
        val ed = prefs(ctx).edit()
        if (prunedBookmarks.size != curBookmarks.size) ed.putStringSet(KEY_BOOKMARKS, prunedBookmarks)
        if (prunedSent.size != curSent.size) ed.putStringSet(KEY_LOCAL_SENT_PINS, prunedSent)
        ed.apply()
    }

    fun getPendingRoadChainPackIds(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_PENDING_ROAD_CHAIN, emptySet())?.toSet() ?: emptySet()
    }

    fun setPackRoadChainPending(ctx: Context, packId: String, pending: Boolean) {
        val cur = getPendingRoadChainPackIds(ctx)
        if (pending && cur.contains(packId)) return
        if (!pending && !cur.contains(packId)) return
        val next = cur.toMutableSet().apply {
            if (pending) add(packId) else remove(packId)
        }
        prefs(ctx).edit().putStringSet(KEY_PENDING_ROAD_CHAIN, next).apply()
    }

    fun clearLocalSentPins(ctx: Context) {
        prefs(ctx).edit().putStringSet(KEY_LOCAL_SENT_PINS, emptySet()).apply()
    }

    fun isHideSentPins(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HIDE_SENT_PINS, false)
    fun setHideSentPins(ctx: Context, hide: Boolean) = prefs(ctx).edit().putBoolean(KEY_HIDE_SENT_PINS, hide).apply()

    fun getPinnedLayerIds(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_PINNED_LAYERS, emptySet())?.toSet() ?: emptySet()
    }

    fun isLayerPinned(ctx: Context, layerId: String): Boolean = getPinnedLayerIds(ctx).contains(layerId)

    fun toggleLayerPinned(ctx: Context, layerId: String): Boolean {
        val next = getPinnedLayerIds(ctx).toMutableSet()
        val nowPinned = if (next.contains(layerId)) {
            next.remove(layerId)
            false
        } else {
            next.add(layerId)
            true
        }
        prefs(ctx).edit().putStringSet(KEY_PINNED_LAYERS, next).apply()
        return nowPinned
    }

    fun getThemeMode(ctx: Context): String = prefs(ctx).getString(KEY_THEME_MODE, "day") ?: "day"
    fun setThemeMode(ctx: Context, mode: String) = prefs(ctx).edit().putString(KEY_THEME_MODE, mode).apply()

    fun isReducedMotion(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_REDUCED_MOTION, false)
    fun setReducedMotion(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_REDUCED_MOTION, enabled).apply()

    fun isShowExtraMapTools(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_EXTRA_MAP_TOOLS, false)
    fun setShowExtraMapTools(ctx: Context, show: Boolean) = prefs(ctx).edit().putBoolean(KEY_SHOW_EXTRA_MAP_TOOLS, show).apply()

    fun isShowZoomButtons(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SHOW_ZOOM_BUTTONS, false)
    fun setShowZoomButtons(ctx: Context, show: Boolean) = prefs(ctx).edit().putBoolean(KEY_SHOW_ZOOM_BUTTONS, show).apply()

    fun getMapPinStyle(ctx: Context): MapPinStyle =
        MapPinStyle.fromId(prefs(ctx).getString(KEY_MAP_PIN_STYLE, MapPinStyle.CLASSIC.id))
    fun setMapPinStyle(ctx: Context, style: MapPinStyle) =
        prefs(ctx).edit().putString(KEY_MAP_PIN_STYLE, style.id).apply()

    fun getCachedLayerNames(ctx: Context): Set<String> {
        return prefs(ctx).getStringSet(KEY_CACHED_LAYER_NAMES, emptySet())?.toSet() ?: emptySet()
    }

    fun saveCachedLayerNames(ctx: Context, names: Collection<String>) {
        val clean = names.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        prefs(ctx).edit().putStringSet(KEY_CACHED_LAYER_NAMES, clean).apply()
    }

    fun addCachedLayerName(ctx: Context, name: String) {
        val c = name.trim()
        if (c.isBlank()) return
        val next = getCachedLayerNames(ctx).toMutableSet().apply { add(c) }
        prefs(ctx).edit().putStringSet(KEY_CACHED_LAYER_NAMES, next).apply()
    }

    fun isClusterDensePins(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CLUSTER_DENSE_PINS, false)

    fun setClusterDensePins(ctx: Context, enabled: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_CLUSTER_DENSE_PINS, enabled).apply()

    internal fun parseWorkHourPresets(raw: String?): List<Pair<Int, Int>> {
        if (raw.isNullOrBlank()) return DEFAULT_WORK_HOUR_PRESETS
        val maxMin = 23 * 60 + 59
        val out = LinkedHashSet<Pair<Int, Int>>()
        raw.split(',').forEach { token ->
            val clean = token.trim()
            val dash = clean.indexOf('-')
            if (dash > 0) {
                val s = clean.substring(0, dash).toIntOrNull()?.coerceIn(0, maxMin)
                val e = clean.substring(dash + 1).toIntOrNull()?.coerceIn(0, maxMin)
                if (s != null && e != null && s != e) {
                    out.add(s to e)
                }
            }
        }
        return if (out.isEmpty()) DEFAULT_WORK_HOUR_PRESETS else out.take(6)
    }

    internal fun serializeWorkHourPresets(presets: List<Pair<Int, Int>>): String {
        val maxMin = 23 * 60 + 59
        return presets
            .asSequence()
            .map { (s, e) -> s.coerceIn(0, maxMin) to e.coerceIn(0, maxMin) }
            .filter { (s, e) -> s != e }
            .distinct()
            .take(6)
            .joinToString(",") { (s, e) -> "${s}-${e}" }
    }

    fun getWorkHourPresets(ctx: Context): List<Pair<Int, Int>> =
        parseWorkHourPresets(prefs(ctx).getString(KEY_WORK_HOURS_PRESETS, null))

    fun addWorkHourPreset(ctx: Context, startMin: Int, endMin: Int): List<Pair<Int, Int>> {
        val maxMin = 23 * 60 + 59
        val item = startMin.coerceIn(0, maxMin) to endMin.coerceIn(0, maxMin)
        if (item.first == item.second) return getWorkHourPresets(ctx)
        val next = (listOf(item) + getWorkHourPresets(ctx).filterNot { it == item }).take(6)
        prefs(ctx).edit().putString(KEY_WORK_HOURS_PRESETS, serializeWorkHourPresets(next)).apply()
        return next
    }

    fun removeWorkHourPreset(ctx: Context, startMin: Int, endMin: Int): List<Pair<Int, Int>> {
        val current = getWorkHourPresets(ctx)
        val filtered = current.filterNot { it == (startMin to endMin) }
        val next = if (filtered.isEmpty()) DEFAULT_WORK_HOUR_PRESETS else filtered
        prefs(ctx).edit().putString(KEY_WORK_HOURS_PRESETS, serializeWorkHourPresets(next)).apply()
        return next
    }

    fun resetWorkHourPresets(ctx: Context): List<Pair<Int, Int>> {
        prefs(ctx).edit().remove(KEY_WORK_HOURS_PRESETS).apply()
        return DEFAULT_WORK_HOUR_PRESETS
    }

    fun isKurdishGlyphCacheMigratedV2(ctx: Context): Boolean =
        prefs(ctx).getBoolean("kurdish_glyph_cache_migrated_v3", false)

    fun markKurdishGlyphCacheMigratedV2(ctx: Context) {
        prefs(ctx).edit().putBoolean("kurdish_glyph_cache_migrated_v3", true).apply()
    }
}
