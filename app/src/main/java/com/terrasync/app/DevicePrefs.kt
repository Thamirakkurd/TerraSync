package com.terrasync.app

import android.content.Context
import android.os.Build
import java.util.UUID

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
    private const val KEY_3D_BUILDINGS = "buildings_3d_enabled"
    private const val KEY_TRAVEL_MODE = "travel_mode"
    private const val KEY_SPEED_INDICATOR = "speed_indicator_enabled"
    private const val KEY_LOCAL_SENT_PINS = "local_sent_pin_ids"
    private const val KEY_HIDE_SENT_PINS = "hide_sent_pins"
    private const val KEY_PINNED_LAYERS = "pinned_layer_ids"

    val ALL_STATUSES = setOf("pending", "done", "issue")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getDeviceId(ctx: Context): String {
        val p = prefs(ctx)
        var id = p.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank()) {
            id = UUID.randomUUID().toString().replace("-", "").take(4).uppercase()
            p.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    fun getCustomName(ctx: Context): String = prefs(ctx).getString(KEY_CUSTOM_NAME, "") ?: ""

    fun setCustomName(ctx: Context, name: String) {
        prefs(ctx).edit().putString(KEY_CUSTOM_NAME, name.trim()).apply()
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

    fun getTravelMode(ctx: Context): TravelMode = TravelMode.fromId(prefs(ctx).getString(KEY_TRAVEL_MODE, TravelMode.CAR.id))
    fun setTravelMode(ctx: Context, mode: TravelMode) = prefs(ctx).edit().putString(KEY_TRAVEL_MODE, mode.id).apply()

    fun isSpeedIndicatorEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SPEED_INDICATOR, true)
    fun setSpeedIndicatorEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_SPEED_INDICATOR, v).apply()

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

    fun isHillshadeEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_HILLSHADE, true)
    fun setHillshadeEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_HILLSHADE, v).apply()

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
}
