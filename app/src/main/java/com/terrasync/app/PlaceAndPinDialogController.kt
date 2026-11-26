package com.terrasync.app

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Location
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import org.maplibre.android.geometry.LatLng
import java.util.Locale

/**
 * Owns tap-anywhere coordinate cards, reverse-geocode label updates, Field Issue / Done
 * preset quick-select sheets, Edit Pin dialogs, and manual bookmark creation dialogs.
 */
class PlaceAndPinDialogController(
    private val activity: AppCompatActivity,
    private val viewModel: MapViewModel,
    private val repository: TerraSyncRepository,
    private val sheetStickyHeader: LinearLayout,
    private val sheetScrollView: NestedScrollView,
    private val sheetContent: LinearLayout,
    private val sheetBehavior: BottomSheetBehavior<LinearLayout>,
    private val dp: (Int) -> Int,
    private val currentPalette: () -> ThemePalette,
    private val themedDialogBuilder: (String) -> AlertDialog.Builder,
    private val showThemedDialog: (AlertDialog, Boolean) -> AlertDialog,
    private val isActivityAlive: () -> Boolean,
    private val hideSoftKeyboard: () -> Unit,
    private val clearSheetStickyHeader: () -> Unit,
    private val setQuickSelectPinId: (String?) -> Unit,
    private val showInScreenSheet: (Int, Boolean, Boolean) -> Unit,
    private val hideInScreenSheet: () -> Unit,
    private val safeStartActivity: (Intent, String) -> Unit,
    private val copyToClipboard: (String, String) -> Unit,
    private val makePinShareMessage: (String, Double, Double, String) -> String,
    private val getCurrentLocation: () -> Location?,
    private val openRoutePreview: (Double, Double, String, TravelMode) -> Unit,
    private val setRouteOrigin: (Double, Double, String) -> Unit,
    private val ensurePinVisibleOnMap: (PinItem) -> Unit,
    private val fireStatusPulse: (Double, Double, String) -> Unit,
    private val renderMapData: () -> Unit,
    private val showStatusUndo: (PinItem, String) -> Unit,
    private val showPinCard: (PinItem, Boolean, Boolean) -> Unit,
    private val notifySyncError: (String) -> Unit
) {

    companion object {
        private val ISSUE_PRESETS = listOf(
            "Customer Refused / No Access",
            "Need Electricity",
            "Wrong Location / Coordinates",
            "Closed / Nobody Home",
            "Safety Hazard / Network Fault"
        )

        private val DONE_PRESETS = listOf(
            "Meter Linked",
            "Inaccessible",
            "No Bill"
        )
    }

    private var tappedTitleView: TextView? = null
    private var tappedSubView: TextView? = null
    private var tappedDistView: TextView? = null

    fun clearTappedViews() {
        tappedTitleView = null
        tappedSubView = null
        tappedDistView = null
    }

    fun updateReverseGeocodeViews(bestTitle: String, bestSub: String, distShort: String) {
        tappedTitleView?.text = bestTitle
        val isolatedSub = DesignSystem.isolateBidi(bestSub)
        tappedSubView?.text = isolatedSub
        tappedSubView?.visibility = if (bestSub.isNotBlank()) View.VISIBLE else View.GONE
        tappedDistView?.text = if (distShort.isNotBlank()) {
            if (bestSub.isNotBlank()) " • $distShort" else distShort
        } else ""
        tappedDistView?.visibility = if (distShort.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun fitCleanSubtitle(raw: String, tv: TextView, maxWidthPx: Int): String {
        val clean = raw.trim()
        if (clean.isEmpty() || maxWidthPx <= 0) return clean
        if (tv.paint.measureText(clean) <= maxWidthPx) return clean
        val parts = clean.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size > 1) {
            val sb = StringBuilder()
            for (part in parts) {
                val candidate = if (sb.isEmpty()) part else "$sb, $part"
                if (tv.paint.measureText(candidate) <= maxWidthPx) {
                    sb.clear()
                    sb.append(candidate)
                } else {
                    break
                }
            }
            if (sb.isNotEmpty()) return sb.toString()
        }
        return clean
    }

    fun showTappedPlaceCard(title: String, subtitle: String, lat: Double, lng: Double) {
        hideSoftKeyboard()
        setQuickSelectPinId(null)
        val p = currentPalette()
        val night = p.isDark
        val txtPrimary = p.textPrimary
        val txtSub = p.textSecondary

        viewModel.tappedLocation = TappedLocationState(title, subtitle, lat, lng)
        val tappedResolvedTitle = title
        val tappedResolvedSub = subtitle
        clearSheetStickyHeader()
        sheetContent.removeAllViews()

        val header = LinearLayout(activity).apply {
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
        header.addView(
            DesignSystem.createHeaderBadge(activity, R.drawable.ic_location_pin_glyph, p, sizeDp = 54).apply {
                (layoutParams as LinearLayout.LayoutParams).marginEnd = dp(12)
            }
        )
        val unit = DevicePrefs.getSpeedUnit(activity)
        val distText = getCurrentLocation()?.let {
            AppStrings.distanceAway(activity, formatDistanceShort(RoutingEngine.haversineMeters(it.latitude, it.longitude, lat, lng), unit))
        } ?: ""

        val titleCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }
        tappedTitleView = TextView(activity).apply {
            text = title
            textSize = 19.5f
            letterSpacing = -0.01f
            setLineSpacing(0f, 1.12f)
            setTextColor(txtPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        titleCol.addView(tappedTitleView)
        val tappedSubRow = object : LinearLayout(activity) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                if (childCount == 2 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                    val avail = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                    val subTv = getChildAt(0) as? TextView
                    val distChild = getChildAt(1)
                    val subMaxW = if (distChild.visibility == View.VISIBLE) {
                        distChild.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST), heightMeasureSpec)
                        (avail - distChild.measuredWidth).coerceAtLeast(dp(56))
                    } else {
                        avail
                    }
                    if (subTv != null) {
                        subTv.maxWidth = subMaxW
                        val latestSub = viewModel.tappedLocation?.subtitle ?: tappedResolvedSub
                        val fitted = fitCleanSubtitle(latestSub, subTv, subMaxW)
                        if (subTv.text.toString() != fitted) subTv.text = fitted
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
        tappedSubView = TextView(activity).apply {
            text = DesignSystem.isolateBidi(subtitle)
            textSize = 12.5f
            setTextColor(p.accentPrimary)
            setTypeface(null, Typeface.BOLD)
            textDirection = View.TEXT_DIRECTION_FIRST_STRONG
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            visibility = if (subtitle.isNotBlank()) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        tappedDistView = TextView(activity).apply {
            text = if (distText.isNotBlank()) {
                if (subtitle.isNotBlank()) " • $distText" else distText
            } else ""
            textSize = 12.5f
            setTextColor(p.accentPrimary)
            setTypeface(null, Typeface.BOLD)
            maxLines = 1
            DesignSystem.applyTabularNumerals(this)
            visibility = if (distText.isNotBlank()) View.VISIBLE else View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        tappedSubRow.addView(tappedSubView)
        tappedSubRow.addView(tappedDistView)
        titleCol.addView(tappedSubRow)
        header.addView(titleCol)
        header.addView(DesignSystem.createSheetCloseButton(activity, p) { hideInScreenSheet() })
        sheetContent.addView(header)

        val tappedCoordRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
        tappedCoordRow.addView(TextView(activity).apply {
            text = "${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lng)}"
            textSize = 13f
            setTextColor(txtSub)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            DesignSystem.applyTabularNumerals(this)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        })
        tappedCoordRow.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_copy)
            setColorFilter(p.accentPrimary)
            contentDescription = "Copy Coordinates"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(2) }
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(activity, this, night)
            UiAnimations.attachSnappyPress(this, 0.90f)
            setOnClickListener {
                copyToClipboard("GPS", "${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lng)}")
            }
        })
        tappedCoordRow.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_share)
            setColorFilter(p.iconTint)
            contentDescription = "Share Place"
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            scaleType = ImageView.ScaleType.FIT_CENTER
            UiAnimations.applyRowRipple(activity, this, night)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val titleNow = viewModel.tappedLocation?.title ?: tappedResolvedTitle
                val msg = makePinShareMessage(titleNow, lat, lng, "")
                safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, msg)
                }, "Share TerraSync Place"), "No app available to handle this action")
            }
        })
        sheetContent.addView(tappedCoordRow)

        val routeRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        routeRow.addView(MaterialButton(activity).apply {
            text = AppStrings.routeTo(activity)
            textSize = 14f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            cornerRadius = dp(23)
            DesignSystem.applyPrimaryCtaButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { openRoutePreview(lat, lng, viewModel.tappedLocation?.title ?: tappedResolvedTitle, viewModel.travelMode) }
        })
        routeRow.addView(MaterialButton(activity).apply {
            text = AppStrings.routeFrom(activity)
            textSize = 14f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(12), 0, dp(12), 0)
            cornerRadius = dp(23)
            DesignSystem.applySecondaryTonalButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { setRouteOrigin(lat, lng, viewModel.tappedLocation?.title ?: tappedResolvedTitle) }
        })
        sheetContent.addView(routeRow)

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
        }
        btnRow.addView(MaterialButton(activity).apply {
            text = AppStrings.savePin(activity)
            textSize = 13.5f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            cornerRadius = dp(21)
            DesignSystem.applySecondaryTonalButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) }
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val curTitle = viewModel.tappedLocation?.title ?: tappedResolvedTitle
                val curSub = viewModel.tappedLocation?.subtitle ?: tappedResolvedSub
                val preName = if (curTitle == "Selected Location" || curTitle == "Shared Location") "" else curTitle
                showAddPinDialog(LatLng(lat, lng), prefillName = preName, prefillDesc = curSub)
            }
        })
        btnRow.addView(MaterialButton(activity).apply {
            text = AppStrings.copyGps(activity)
            textSize = 13.5f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            cornerRadius = dp(21)
            DesignSystem.applySecondaryTonalButton(this, p)
            layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { copyToClipboard("GPS", "${"%.6f".format(Locale.US, lat)}, ${"%.6f".format(Locale.US, lng)}") }
        })
        sheetContent.addView(btnRow)

        UiAnimations.animateSheetContentReveal(sheetContent)
        showInScreenSheet(315, false, true)
    }

    fun showFieldQuickSelectSheet(pin: PinItem, isIssue: Boolean) {
        setQuickSelectPinId(pin.id)
        val p = currentPalette()
        val night = p.isDark
        val accentColor = if (isIssue) p.red else p.green
        val presets = if (isIssue) ISSUE_PRESETS else DONE_PRESETS
        val statusKey = if (isIssue) "issue" else "done"

        clearSheetStickyHeader()
        sheetContent.removeAllViews()
        val layoutDir = if (AppStrings.isRtl(activity)) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        sheetContent.layoutDirection = layoutDir
        sheetStickyHeader.layoutDirection = layoutDir

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        header.addView(ImageButton(activity).apply {
            setImageResource(R.drawable.ic_back)
            setColorFilter(p.textPrimary)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            setPadding(dp(10), dp(10), dp(10), dp(10))
            UiAnimations.applyRowRipple(activity, this, night)
            setOnClickListener {
                val fresh = viewModel.folderSnapshot.pinById[pin.id] ?: pin
                showPinCard(fresh, true, false)
            }
        })
        val titleCol = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(4)
            }
        }
        titleCol.addView(TextView(activity).apply {
            text = if (isIssue) AppStrings.reportFieldIssue(activity) else AppStrings.markAsDone(activity)
            textSize = 19f
            letterSpacing = -0.01f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.textPrimary)
        })
        titleCol.addView(TextView(activity).apply {
            text = pin.name
            textSize = 12.5f
            setTextColor(p.textSecondary)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        header.addView(titleCol)
        header.addView(DesignSystem.createSheetCloseButton(activity, p) { hideInScreenSheet() })
        sheetStickyHeader.addView(header)
        sheetStickyHeader.visibility = View.VISIBLE

        val etReason = EditText(activity).apply {
            hint = if (isIssue) AppStrings.customIssueHint(activity) else AppStrings.optionalNoteHint(activity)
            textSize = 14f
            setTextColor(p.textPrimary)
            setHintTextColor(p.textSecondary)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            minLines = 1
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            if (PinStatus.fromRaw(pin.status).key == statusKey) {
                setText(pin.issueReason)
            }
            background = GradientDrawable().apply {
                setColor(p.cardSurface)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), p.borderSubtle)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        fun keepReasonVisibleAboveKeyboard() {
            if (sheetBehavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                sheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
            }
            sheetScrollView.postDelayed({
                if (isActivityAlive()) {
                    sheetScrollView.fullScroll(View.FOCUS_DOWN)
                }
            }, 140L)
        }
        etReason.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) keepReasonVisibleAboveKeyboard()
        }
        etReason.setOnClickListener { keepReasonVisibleAboveKeyboard() }

        var committed = false
        fun commitSelection(noteText: String) {
            if (committed) return
            val clean = noteText.trim()
            if (isIssue && clean.isBlank()) {
                Toast.makeText(activity, AppStrings.enterIssuePrompt(activity), Toast.LENGTH_SHORT).show()
                return
            }
            committed = true
            UiAnimations.hapticConfirm(sheetContent)
            val stamp = DevicePrefs.getDeviceStamp(activity)
            repository.savePinStatus(pin, statusKey, stamp, clean, notifySyncError)
            fireStatusPulse(pin.lat, pin.lng, statusColorHex(statusKey))
            renderMapData()
            val undoMsg = if (isIssue) {
                "${AppStrings.markedIssue(activity)}: ${AppStrings.presetLabel(activity, clean)}"
            } else if (clean.isNotBlank()) {
                "${AppStrings.markedDone(activity)}: ${AppStrings.presetLabel(activity, clean)}"
            } else {
                AppStrings.markedDone(activity)
            }
            showStatusUndo(pin, undoMsg)
            hideInScreenSheet()
        }

        val presetsCard = DesignSystem.createGroupedCard(activity, p, radiusDp = 16f, paddingHDp = 0, paddingVDp = 2, bottomMarginDp = 10)
        presets.forEachIndexed { idx, preset ->
            if (idx > 0) {
                presetsCard.addView(DesignSystem.createHairlineDivider(activity, p, 14, 14))
            }
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(48)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                UiAnimations.applyRowRipple(activity, this, night)
                setOnClickListener { commitSelection(preset) }
            }
            row.addView(View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(accentColor)
                }
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(12) }
            })
            row.addView(TextView(activity).apply {
                text = AppStrings.presetLabel(activity, preset)
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(activity).apply {
                text = AppStrings.tap(activity)
                textSize = 12f
                setTextColor(p.accent)
                setTypeface(null, Typeface.BOLD)
            })
            presetsCard.addView(row)
        }
        sheetContent.addView(presetsCard)
        sheetContent.addView(etReason)

        val btnRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            gravity = Gravity.CENTER_VERTICAL
        }
        btnRow.addView(MaterialButton(activity).apply {
            text = AppStrings.cancel(activity)
            textSize = 13.5f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 0.8f).apply { marginEnd = dp(8) }
            DesignSystem.styleSegmentedButton(this, false, p)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val fresh = viewModel.folderSnapshot.pinById[pin.id] ?: pin
                showPinCard(fresh, true, false)
            }
        })
        btnRow.addView(MaterialButton(activity).apply {
            text = if (isIssue) AppStrings.saveIssue(activity) else AppStrings.saveDone(activity)
            textSize = 13.5f
            isAllCaps = false
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            insetTop = 0
            insetBottom = 0
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(10), 0, dp(10), 0)
            setTextColor(Color.WHITE)
            setBackgroundColor(accentColor)
            cornerRadius = dp(12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1.3f)
            UiAnimations.attachSnappyPress(this)
            setOnClickListener { commitSelection(etReason.text.toString()) }
        })
        sheetContent.addView(btnRow)

        UiAnimations.animateContentSwap(sheetContent, forward = true)
        showInScreenSheet(420, true, true)
    }

    fun showEditPinDialog(pin: PinItem) {
        val p = currentPalette()
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(10), dp(22), dp(4))
        }
        val etName = DesignSystem.createThemedEditText(activity, p, "Pin name", initialText = pin.name).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10)
        }
        val etDesc = DesignSystem.createThemedEditText(activity, p, "Description / notes", initialText = pin.description, singleLine = false, minLines = 2)
        box.addView(etName)
        box.addView(etDesc)

        showThemedDialog(
            themedDialogBuilder("Edit Pin")
                .setView(box)
                .setPositiveButton("Save") { _, _ ->
                    val stamp = DevicePrefs.getDeviceStamp(activity)
                    repository.savePinText(pin, etName.text.toString().trim(), etDesc.text.toString().trim(), stamp, notifySyncError)
                    Toast.makeText(activity, "Saved & stamped", Toast.LENGTH_SHORT).show()
                    hideInScreenSheet()
                }
                .setNegativeButton("Cancel", null)
                .create(),
            false
        )
    }

    fun showAddPinDialog(latLng: LatLng, prefillName: String = "", prefillDesc: String = "") {
        val p = currentPalette()
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(8), dp(22), dp(4))
        }
        box.addView(TextView(activity).apply {
            text = "${"%.6f".format(Locale.US, latLng.latitude)}, ${"%.6f".format(Locale.US, latLng.longitude)}"
            textSize = 12.5f
            setTextColor(p.textSecondary)
            DesignSystem.applyTabularNumerals(this)
            setPadding(dp(2), 0, 0, dp(10))
        })
        val etName = DesignSystem.createThemedEditText(activity, p, "Pin name", initialText = prefillName).apply {
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10)
        }
        val etDesc = DesignSystem.createThemedEditText(activity, p, "Description (optional)", initialText = prefillDesc, singleLine = false, minLines = 2)
        box.addView(etName)
        box.addView(etDesc)

        showThemedDialog(
            themedDialogBuilder("Save Bookmark")
                .setView(box)
                .setPositiveButton("Save") { _, _ ->
                    val stamp = DevicePrefs.getDeviceStamp(activity)
                    val devId = DevicePrefs.getDeviceId(activity)
                    val savedPin = repository.addManualPin(
                        etName.text.toString().trim(),
                        etDesc.text.toString().trim(),
                        latLng.latitude,
                        latLng.longitude,
                        stamp,
                        devId
                    )
                    DevicePrefs.setLayerVisible(activity, savedPin.layerId, true)
                    ensurePinVisibleOnMap(savedPin)
                    fireStatusPulse(savedPin.lat, savedPin.lng, "#F59E0B")
                    Toast.makeText(activity, "Saved \"${savedPin.name}\" locally", Toast.LENGTH_SHORT).show()
                    hideInScreenSheet()
                }
                .setNegativeButton("Cancel", null)
                .create(),
            false
        )
    }
}
