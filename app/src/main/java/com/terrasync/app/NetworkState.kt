package com.terrasync.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Checks for genuinely usable internet (NET_CAPABILITY_INTERNET + NET_CAPABILITY_VALIDATED)
 * so dead 4G+ links fall back immediately to offline PMTiles and offline routing.
 */
object NetworkState {
    fun hasValidatedInternet(ctx: Context?): Boolean {
        if (ctx == null) return true
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val net = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(net) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrDefault(false)
    }
}
