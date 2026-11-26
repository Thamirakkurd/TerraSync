package com.terrasync.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.location.Location
import android.net.Uri
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.Locale

/**
 * Owns the pin detail bottom-sheet card (stacked-pin cycler, status actions,
 * quick-copy/star/share strip, edit log audit rows, owner deletion, and 5s undo bar).
 */
class PinCardSheetController(
    private val activity: AppCompatActivity,
    private val viewModel: MapViewModel,
    private val repository: TerraSyncRepository,
    private val sheetContent: LinearLayout,
    private val undoController: StatusUndoController,
    private val dp: (Int) -> Int,
    private val currentPalette: () -> ThemePalette,
    private val themedDialogBuilder: (String) -> AlertDialog.Builder,
    private val showThemedDialog: (AlertDialog, Boolean) -> AlertDialog,
    private val hideSoftKeyboard: () -> Unit,
    private val clearSheetStickyHeader: () -> Unit,
    private val clearQuickSelectPinId: () -> Unit,
    private val showInScreenSheet: (Int, Boolean, Boolean) -> Unit,
    private val hideInScreenSheet: () -> Unit,
    private val safeStartActivity: (Intent, String) -> Unit,
    private val copyToClipboard: (String, String) -> Unit,
    private val findExactStackedPins: (PinItem) -> List<PinItem>,
    private val renderSelectedMarker: () -> Unit,
    private val fireStatusPulse: (Double, Double, String) -> Unit,
    private val renderMapData: () -> Unit,
    private val updateSentToggleUi: () -> Unit,
    private val getCurrentLocation: () -> Location?,
    private val openRoutePreview: (Double, Double, String, TravelMode) -> Unit,
    private val setRouteOrigin: (Double, Double, String) -> Unit,
    private val showFieldQuickSelectSheet: (PinItem, Boolean) -> Unit,
    private val showEditPinDialog: (PinItem) -> Unit,
    private val notifySyncError: (String) -> Unit
) {

    fun makePinShareMessage(name: String, lat: Double, lng: Double, pinId: String = ""): String {
        val encodedName = Uri.encode(name)
        val idPart = if (pinId.isNotBlank()) "&id=${Uri.encode(pinId)}" else ""
        val link = "https://thamirakkurd.github.io/TerraSync/pin?lat=$lat&lng=$lng&name=$encodedName$idPart"
        return "$name\n${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lng)}\nOpen in TerraSync: $link"
    }

    fun showStatusUndo(previousPin: PinItem, summaryMsg: String) {
        undoController.show(activity, previousPin, summaryMsg) { snap ->
            repository.restorePinStatusSnapshot(snap, notifySyncError)
            val prevStatusKey = PinStatus.fromRaw(snap.status).key
            fireStatusPulse(snap.lat, snap.lng, statusColorHex(prevStatusKey))
            renderMapData()
            Toast.makeText(activity, AppStrings.undone(activity), Toast.LENGTH_SHORT).show()
        }
    }

    fun showPinCard(pin: PinItem, animateSwap: Boolean = false, swapForward: Boolean = true) {
        hideSoftKeyboard()
        clearQuickSelectPinId()
        val p = currentPalette()
        val night = p.isDark
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary
        val chipBg = p.chipSurface
        val cardTint = p.cardSurface

        clearSheetStickyHeader()
        sheetContent.removeAllViews()

        val stackedPins = findExactStackedPins(pin)
        if (stackedPins.size > 1) {
            val currentIdx = stackedPins.indexOfFirst { it.id == pin.id }.coerceAtLeast(0)
            val stackRow = LinearLayout(activity).apply {
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

            stackRow.addView(TextView(activity).apply {
                text = AppStrings.stackedSummary(activity, stackedPins.size, currentIdx + 1, stackedPins.size)
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.accent)
                DesignSystem.applyTabularNumerals(this)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            fun jumpStacked(delta: Int, forward: Boolean) {
                val nextIdx = (currentIdx + delta + stackedPins.size) % stackedPins.size
                val nextPin = stackedPins[nextIdx]
                viewModel.selectPin(nextPin.id, stackedPins.map { it.id })
                viewModel.selectedMarkerGeoPoint = GeoPoint(nextPin.lat, nextPin.lng)
                renderSelectedMarker()
                fireStatusPulse(nextPin.lat, nextPin.lng, statusColorHex(nextPin.status))
                showPinCard(nextPin, animateSwap = true, swapForward = forward)
            }

            if (stackedPins.size > 2) {
                stackRow.addView(MaterialButton(activity).apply {
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
                    backgroundTintList = ColorStateList.valueOf(if (night) p.cardSurface else Color.WHITE)
                    strokeWidth = dp(1)
                    strokeColor = ColorStateList.valueOf(p.borderSubtle)
                    cornerRadius = dp(10)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                        marginEnd = dp(6)
                    }
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener { jumpStacked(-1, forward = false) }
                })
            }

            stackRow.addView(MaterialButton(activity).apply {
                text = AppStrings.nextShort(activity)
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
        val headerRow = LinearLayout(activity).apply {
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
            activity,
            R.drawable.ic_location_pin_glyph,
            p,
            bgColor = badgeBg,
            glyphColor = badgeGlyph,
            sizeDp = 54
        ).apply {
            (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12)
        }
        headerRow.addView(pinBadge)

        val layer = viewModel.folderSnapshot.layerById[pin.layerId]
        val layerName = layer?.name ?: "Field Pin"
        val speedUnit = DevicePrefs.getSpeedUnit(activity)
        val distText = getCurrentLocation()?.let {
            " • ${AppStrings.distanceAway(activity, formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, pin.lat, pin.lng), speedUnit))}"
        } ?: ""

        val titleCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        titleCol.addView(TextView(activity).apply {
            text = pin.name
            textSize = 19.5f
            letterSpacing = -0.01f
            setLineSpacing(0f, 1.12f)
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        val subRow = object : LinearLayout(activity) {
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
        subRow.addView(TextView(activity).apply {
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
            subRow.addView(TextView(activity).apply {
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
        headerRow.addView(DesignSystem.createSheetCloseButton(activity, p) { hideInScreenSheet() })
        sheetContent.addView(headerRow)

        val coordActionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
        coordActionRow.addView(TextView(activity).apply {
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
        coordActionRow.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_copy)
            setColorFilter(p.accentPrimary)
            contentDescription = "Copy Coordinates"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(2) }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(activity, this, night)
            UiAnimations.attachSnappyPress(this, 0.90f)
            setOnClickListener {
                copyToClipboard("GPS", "${"%.6f".format(Locale.US, pin.lat)}, ${"%.6f".format(Locale.US, pin.lng)}")
            }
        })

        var starred = DevicePrefs.isBookmarked(activity, pin.id)
        coordActionRow.addView(ImageButton(activity).apply {
            setImageResource(if (starred) R.drawable.ic_star_filled else R.drawable.ic_star_outline)
            if (!starred) setColorFilter(p.iconMuted)
            contentDescription = if (starred) "Unstar Pin" else "Star Pin"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(2) }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(activity, this, night)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                starred = DevicePrefs.toggleBookmark(activity, pin.id)
                UiAnimations.animateIconSwap(
                    this,
                    if (starred) R.drawable.ic_star_filled else R.drawable.ic_star_outline,
                    newTint = if (starred) null else p.iconMuted,
                    clearTint = starred
                )
                Toast.makeText(activity, if (starred) "Starred" else "Unstarred", Toast.LENGTH_SHORT).show()
            }
        })

        coordActionRow.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_share)
            setColorFilter(p.iconTint)
            contentDescription = "Share Pin"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(activity, this, night)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val msg = makePinShareMessage(pin.name, pin.lat, pin.lng, pin.id)
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, msg)
                }, "Share TerraSync Pin"), "No app available to handle this action")
            }
        })
        sheetContent.addView(coordActionRow)

        if (pinStatus == PinStatus.ISSUE && pin.issueReason.isNotBlank()) {
            sheetContent.addView(TextView(activity).apply {
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
            sheetContent.addView(TextView(activity).apply {
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

        val statusRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val stamp = DevicePrefs.getDeviceStamp(activity)
        data class StatusButtonStyle(val key: String, val title: String, val solidColor: Int, val softColor: Int, val inactiveTextColor: Int)
        listOf(
            StatusButtonStyle("pending", AppStrings.pending(activity), p.amber, p.amberSoft, if (night) p.amber else Color.parseColor("#B36B00")),
            StatusButtonStyle("done", AppStrings.done(activity), p.green, p.greenSoft, p.green),
            StatusButtonStyle("issue", AppStrings.issue(activity), p.red, p.redSoft, p.red)
        ).forEachIndexed { idx, (key, title, solidColor, softColor, inactiveTextColor) ->
            val active = pinStatus.key == key
            statusRow.addView(MaterialButton(activity).apply {
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
                    backgroundTintList = ColorStateList.valueOf(solidColor)
                    strokeWidth = 0
                } else {
                    setTextColor(inactiveTextColor)
                    backgroundTintList = ColorStateList.valueOf(softColor)
                    strokeWidth = dp(1)
                    strokeColor = ColorStateList.valueOf(
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
                        showFieldQuickSelectSheet(pin, true)
                    } else if (key == "done") {
                        showFieldQuickSelectSheet(pin, false)
                    } else {
                        UiAnimations.hapticConfirm(sheetContent)
                        repository.savePinStatus(pin, key, stamp, "", notifySyncError)
                        fireStatusPulse(pin.lat, pin.lng, statusColorHex(key))
                        renderMapData()
                        showStatusUndo(pin, AppStrings.markedPending(activity))
                        hideInScreenSheet()
                    }
                }
            })
        }
        sheetContent.addView(statusRow)

        sheetContent.addView(MaterialButton(activity).apply {
            text = AppStrings.routeTo(activity)
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
            setOnClickListener { openRoutePreview(pin.lat, pin.lng, pin.name, viewModel.travelMode) }
        })

        val actionRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }
        fun applyTileVisual(tile: LinearLayout, iconIv: ImageView, labelTv: TextView, active: Boolean) {
            val fill = DesignSystem.buildOpaqueDrawable(
                ctx = activity,
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
            tile.background = RippleDrawable(
                ColorStateList.valueOf(rippleColor),
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
            val tile = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(dp(4), dp(6), dp(4), dp(6))
                layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    if (addEndGap) marginEnd = dp(6)
                }
            }
            val iconIv = ImageView(activity).apply {
                setImageResource(iconRes)
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { bottomMargin = dp(3) }
            }
            val labelTv = TextView(activity).apply {
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
        var localSent = DevicePrefs.isPinLocalSent(activity, pin.id)
        addCompactActionTile(
            label = if (localSent) "Sent ✓" else "Mark Sent",
            iconRes = R.drawable.ic_check,
            active = localSent,
            addEndGap = false
        ) { tile, iconIv, labelTv ->
            localSent = DevicePrefs.toggleLocalSentPin(activity, pin.id)
            labelTv.text = if (localSent) "Sent ✓" else "Mark Sent"
            applyTileVisual(tile, iconIv, labelTv, localSent)
            UiAnimations.animateScalePop(tile, 0.94f, 160L)
            updateSentToggleUi()
            renderMapData()
            Toast.makeText(
                activity,
                if (localSent) "Marked sent (local only)" else "Removed sent mark",
                Toast.LENGTH_SHORT
            ).show()
            if (localSent && viewModel.hideSentPins) hideInScreenSheet()
        }
        sheetContent.addView(actionRow)

        if (pin.description.isNotBlank()) {
            sheetContent.addView(DesignSystem.createSectionHeader(activity, "Details", p).apply {
                setPadding(dp(2), dp(14), dp(2), dp(8))
            })
            sheetContent.addView(TextView(activity).apply {
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
            sheetContent.addView(TextView(activity).apply {
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

        val copyRow = LinearLayout(activity).apply {
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
            copyRow.addView(MaterialButton(activity).apply {
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
                        Toast.makeText(activity, "No description to copy", Toast.LENGTH_SHORT).show()
                    } else {
                        copyToClipboard(label, clipVal)
                    }
                }
            })
        }
        sheetContent.addView(copyRow)

        val isOwner = repository.isLayerOwner(layer)

        if (pin.editHistory.isNotBlank()) {
            val histLines = pin.editHistory.lines().map { it.trim() }.filter { it.isNotBlank() }
            val workStart = DevicePrefs.getWorkStartMinutes(activity)
            val workEnd = DevicePrefs.getWorkEndMinutes(activity)
            if (isOwner && histLines.isNotEmpty()) {
                sheetContent.addView(TextView(activity).apply {
                    text = "Edit Log (Tap ✕ to delete an edit without deleting pin):"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtSub)
                    setPadding(0, dp(2), 0, dp(4))
                })
                histLines.forEachIndexed { idx, line ->
                    val afterHours = PinEditHistoryAudit.isHistoryLineOutsideWorkHours(line, workStart, workEnd)
                    val row = LinearLayout(activity).apply {
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
                    val textCol = LinearLayout(activity).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    if (afterHours) {
                        textCol.addView(TextView(activity).apply {
                            text = "⏰ AFTER WORKING HOURS"
                            textSize = 10f
                            setTypeface(null, Typeface.BOLD)
                            setTextColor(p.red)
                            setPadding(0, 0, 0, dp(2))
                        })
                    }
                    textCol.addView(TextView(activity).apply {
                        text = line
                        textSize = 11.5f
                        setTextColor(if (afterHours) p.textPrimary else txtSub)
                        DesignSystem.applyTabularNumerals(this)
                    })
                    row.addView(textCol)
                    row.addView(TextView(activity).apply {
                        text = "✕"
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(p.red)
                        gravity = Gravity.CENTER
                        setPadding(dp(10), dp(4), dp(8), dp(4))
                        setOnClickListener {
                            showThemedDialog(
                                themedDialogBuilder("Delete this edit?")
                                    .setMessage("$line\n\nOnly this edit will be removed. The pin stays on the map and its status recalculates from the remaining edits.")
                                    .setPositiveButton("Delete Edit") { _, _ ->
                                        val updated = repository.deletePinHistoryEntry(pin, idx, notifySyncError)
                                        renderSelectedMarker()
                                        fireStatusPulse(updated.lat, updated.lng, statusColorHex(updated.status))
                                        showPinCard(updated, animateSwap = true)
                                        Toast.makeText(activity, "Edit deleted — pin kept", Toast.LENGTH_SHORT).show()
                                    }
                                    .setNegativeButton("Cancel", null)
                                    .create(),
                                true
                            )
                        }
                    })
                    sheetContent.addView(row)
                }
            } else if (histLines.isNotEmpty()) {
                sheetContent.addView(TextView(activity).apply {
                    text = "Edit Log:"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtSub)
                    setPadding(0, dp(2), 0, dp(4))
                })
                histLines.forEach { line ->
                    val afterHours = PinEditHistoryAudit.isHistoryLineOutsideWorkHours(line, workStart, workEnd)
                    sheetContent.addView(TextView(activity).apply {
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
            sheetContent.addView(MaterialButton(activity).apply {
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
                                val updated = repository.resetPinStatus(pin, notifySyncError)
                                renderSelectedMarker()
                                fireStatusPulse(updated.lat, updated.lng, statusColorHex(updated.status))
                                showPinCard(updated, animateSwap = true)
                                Toast.makeText(activity, "Status reset to Pending", Toast.LENGTH_SHORT).show()
                            }
                            .setNegativeButton("Cancel", null)
                            .create(),
                        false
                    )
                }
            })
        }
        if (isOwner) {
            sheetContent.addView(MaterialButton(activity).apply {
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
                                repository.deletePin(pin, notifySyncError)
                                hideInScreenSheet()
                            }
                            .setNegativeButton("Cancel", null)
                            .create(),
                        true
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
        showInScreenSheet(peekHeight, animateSwap, false)
    }
}
