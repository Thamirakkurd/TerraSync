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
    private const val KEY_DEFAULT_SAT = "default_satellite"
    private const val KEY_AUTO_FOLLOW = "auto_follow_gps"
    private const val KEY_KEEP_AWAKE = "keep_screen_awake"
    private const val KEY_VOICE_NAV = "voice_guidance"

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
        val deviceModel = if (mdl.startsWith(mfg, ignoreCase = true)) mdl else "$mfg$mdl".trim()
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
        prefs(ctx).edit().putStringSet(KEY_JOINED_LAYERS, next).apply()
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

    fun isDefaultSatellite(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_DEFAULT_SAT, false)
    fun setDefaultSatellite(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_DEFAULT_SAT, v).apply()

    fun isAutoFollow(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_AUTO_FOLLOW, true)
    fun setAutoFollow(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_AUTO_FOLLOW, v).apply()

    fun isKeepAwake(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_KEEP_AWAKE, true)
    fun setKeepAwake(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_KEEP_AWAKE, v).apply()

    fun isVoiceNav(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_VOICE_NAV, true)
    fun setVoiceNav(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_VOICE_NAV, v).apply()
}
