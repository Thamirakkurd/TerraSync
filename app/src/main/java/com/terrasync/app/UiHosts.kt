package com.terrasync.app

import android.content.Intent
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import org.maplibre.android.geometry.LatLng

/**
 * Focused host interfaces to replace 25-45 lambda controller constructors incrementally.
 */
interface UiChromeHost {
    fun dp(v: Int): Int
    fun currentPalette(): ThemePalette
    fun isActivityAlive(): Boolean
    fun hideSoftKeyboard()
    fun themedDialogBuilder(title: String): AlertDialog.Builder
    fun showThemedDialog(dlg: AlertDialog, destructivePositive: Boolean = false): AlertDialog
    fun safeStartActivity(intent: Intent, fallbackMsg: String = "No app available to handle this action")
    fun copyToClipboard(label: String, value: String)
    fun notifySyncError(err: String)
}

interface SheetAndPageHost {
    fun showInScreenSheet(peekDp: Int = 280, inPlace: Boolean = false, expandToFit: Boolean = false)
    fun hideInScreenSheet()
    fun clearSheetStickyHeader()
    fun openFullPage(builder: () -> View)
    fun pushSubPage(onBack: () -> Unit, builder: () -> View)
    fun popSubPage(builder: () -> View)
    fun closeFullPage()
    fun makePageFrame(
        title: String,
        onBack: () -> Unit,
        rightAction: View? = null,
        scrollable: Boolean = true
    ): Pair<LinearLayout, LinearLayout>
}

interface MapNavigationHost {
    fun renderMapData()
    fun ensurePinVisibleOnMap(pin: PinItem)
    fun selectPinAndOpenCard(pin: PinItem, zoomIfFar: Boolean = false)
    fun selectCoordinateOnMap(latLng: LatLng, explicitTitle: String?, fallbackSub: String = "", zoomTo: Double? = null)
    fun zoomToKmlBounds(pts: List<LatLng>, layerIdToReveal: String? = null)
}
