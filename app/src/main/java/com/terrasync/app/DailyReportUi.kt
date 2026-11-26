package com.terrasync.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

internal fun MainActivity.formatWhatsAppReportPreview(raw: String, p: ThemePalette): CharSequence {
    val sb = SpannableStringBuilder()
    val display = raw.replace("⏳", "○").replace("⏰", "◷")
    val lines = display.split('\n')

    fun appendGlyphAware(seg: String) {
        for (ch in seg) {
            val start = sb.length
            sb.append(ch)
            val symColor = when (ch) {
                '✓' -> p.green
                '⚠', '◷' -> p.red
                '○' -> p.amber
                else -> null
            }
            if (symColor != null) {
                sb.setSpan(ForegroundColorSpan(symColor), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    lines.forEachIndexed { lineIdx, rawLine ->
        if (lineIdx > 0) sb.append('\n')
        val line = rawLine.trimEnd('\r')
        val startPos = sb.length

        if (line.length >= 3 && line.startsWith('*') && line.endsWith('*') && line.indexOf('*', 1) == line.length - 1) {
            val inner = line.substring(1, line.length - 1)
            appendGlyphAware(inner)
            val endPos = sb.length
            sb.setSpan(StyleSpan(Typeface.BOLD), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(
                RelativeSizeSpan(if (lineIdx == 0) 1.10f else 1.04f),
                startPos,
                endPos,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            val headingColor = when {
                inner.contains('✓') -> p.green
                inner.contains('⚠') -> p.red
                else -> p.textPrimary
            }
            sb.setSpan(ForegroundColorSpan(headingColor), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else if (line.length >= 3 && line.startsWith('_') && line.endsWith('_') && line.indexOf('_', 1) == line.length - 1) {
            val inner = line.substring(1, line.length - 1)
            sb.append(inner)
            val endPos = sb.length
            sb.setSpan(StyleSpan(Typeface.ITALIC), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.92f), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(ForegroundColorSpan(p.textMuted), startPos, endPos, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else {
            var i = 0
            while (i < line.length) {
                val ch = line[i]
                if (ch == '*') {
                    val close = line.indexOf('*', i + 1)
                    if (close > i + 1) {
                        val s = sb.length
                        appendGlyphAware(line.substring(i + 1, close))
                        val e = sb.length
                        sb.setSpan(StyleSpan(Typeface.BOLD), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(ForegroundColorSpan(p.textPrimary), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = close + 1
                        continue
                    }
                } else if (ch == '_' && (i == 0 || !line[i - 1].isLetterOrDigit())) {
                    val close = line.indexOf('_', i + 1)
                    if (close > i + 1 && (close == line.length - 1 || !line[close + 1].isLetterOrDigit())) {
                        val s = sb.length
                        sb.append(line.substring(i + 1, close))
                        val e = sb.length
                        sb.setSpan(StyleSpan(Typeface.ITALIC), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(ForegroundColorSpan(p.textMuted), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = close + 1
                        continue
                    }
                }
                appendGlyphAware(ch.toString())
                i++
            }
        }
    }
    return sb
}

internal fun MainActivity.showDailyWhatsAppReportDialog(
    layer: LayerItem,
    layerPins: List<PinItem>,
    layerTracks: List<TrackItem>,
    syncBadgeOverride: String? = null
) {
    val p = currentPalette()
    val workStart = DevicePrefs.getWorkStartMinutes(this)
    val workEnd = DevicePrefs.getWorkEndMinutes(this)
    val aliases = buildLocalDeviceAliasMap()
    val allPinsSnapshot = pins.toList()
    val layerPinsSnapshot = layerPins.toList()
    val isLocal = LocalFolderStore.isLocalLayerId(layer.id)
    val isLayerLive = !isLocal && repo.isLayerLive(layer.id)
    val resolvedBadge = syncBadgeOverride ?: when {
        isLocal -> "Local"
        isLayerLive -> "Live Cloud"
        else -> "Offline Cache"
    }

    val sentIds = if (hideSentPins) DevicePrefs.getLocalSentPinIds(this) else emptySet()
    val mapFilteredPins = layerPinsSnapshot.filter { pin ->
        PinStatus.fromRaw(pin.status).key in visibleStatuses && pin.id !in sentIds
    }
    val mapFilterHidesSome = mapFilteredPins.size != layerPinsSnapshot.size

    val container = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutDirection = if (AppStrings.isRtl(this@showDailyWhatsAppReportDialog)) {
            View.LAYOUT_DIRECTION_RTL
        } else {
            View.LAYOUT_DIRECTION_LTR
        }
        setPadding(dp(20), dp(8), dp(20), dp(14))
    }
    val tvScopeBanner = TextView(this).apply {
        textSize = 12f
        setTextColor(p.textSecondary)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        background = GradientDrawable().apply {
            setColor(p.cardSurface)
            cornerRadius = dp(12).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) }
        visibility = if (mapFilterHidesSome) View.VISIBLE else View.GONE
    }
    container.addView(tvScopeBanner)

    val scopeToggleRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) }
        visibility = if (mapFilterHidesSome) View.VISIBLE else View.GONE
    }
    val btnScopeFull = MaterialButton(this).apply {
        text = "All Folder Pins (${layerPinsSnapshot.size})"
        textSize = 12f
        isAllCaps = false
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(10)
        layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply { marginEnd = dp(6) }
        UiAnimations.attachSnappyPress(this)
    }
    val btnScopeVisible = MaterialButton(this).apply {
        text = "Map Filtered (${mapFilteredPins.size})"
        textSize = 12f
        isAllCaps = false
        insetTop = 0
        insetBottom = 0
        cornerRadius = dp(10)
        layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f)
        UiAnimations.attachSnappyPress(this)
    }
    scopeToggleRow.addView(btnScopeFull)
    scopeToggleRow.addView(btnScopeVisible)
    container.addView(scopeToggleRow)

    // Visual Summary Strip (Visited progress bar + tinted Done / Issue / Pending chips)
    val summaryCard = DesignSystem.createGroupedCard(
        this, p, radiusDp = 14f, paddingHDp = 12, paddingVDp = 10, bottomMarginDp = 10
    )
    val progHeader = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    progHeader.addView(TextView(this).apply {
        text = AppStrings.visitedProgress(this@showDailyWhatsAppReportDialog)
        textSize = 11.5f
        setTypeface(null, Typeface.BOLD)
        setTextColor(p.textMuted)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    })
    val tvProgressPct = TextView(this).apply {
        text = "0% (0 / 0)"
        textSize = 12.5f
        setTypeface(null, Typeface.BOLD)
        setTextColor(p.textPrimary)
        DesignSystem.applyTabularNumerals(this)
    }
    progHeader.addView(tvProgressPct)
    summaryCard.addView(progHeader)

    val barTrack = FrameLayout(this).apply {
        background = GradientDrawable().apply {
            setColor(p.borderSubtle)
            cornerRadius = dp(4).toFloat()
        }
        clipToOutline = true
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(7)).apply {
            topMargin = dp(6)
            bottomMargin = dp(8)
        }
    }
    val barSegments = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }
    val segDone = View(this).apply {
        setBackgroundColor(p.green)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f)
    }
    val segIssue = View(this).apply {
        setBackgroundColor(p.red)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 0f)
    }
    val segPending = View(this).apply {
        setBackgroundColor(Color.TRANSPARENT)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
    }
    barSegments.addView(segDone)
    barSegments.addView(segIssue)
    barSegments.addView(segPending)
    barTrack.addView(barSegments)
    summaryCard.addView(barTrack)

    val chipsRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
    }
    fun makeStatChip(fgColor: Int, bgColor: Int, addEndGap: Boolean): TextView {
        return TextView(this).apply {
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(fgColor)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(6), dp(5), dp(6), dp(5))
            DesignSystem.applyTabularNumerals(this)
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(10).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                if (addEndGap) marginEnd = dp(6)
            }
        }
    }
    val doneLbl = AppStrings.done(this)
    val issueLbl = AppStrings.issue(this)
    val pendingLbl = AppStrings.pending(this)
    val chipDone = makeStatChip(p.green, p.greenSoft, addEndGap = true).apply { text = "✓ 0 $doneLbl" }
    val chipIssue = makeStatChip(p.red, p.redSoft, addEndGap = true).apply { text = "⚠ 0 $issueLbl" }
    val chipPending = makeStatChip(p.amber, p.amberSoft, addEndGap = false).apply { text = "○ 0 $pendingLbl" }
    chipsRow.addView(chipDone)
    chipsRow.addView(chipIssue)
    chipsRow.addView(chipPending)
    summaryCard.addView(chipsRow)
    container.addView(summaryCard)

    val maxScrollPx = (resources.displayMetrics.heightPixels * 0.42f).toInt().coerceAtLeast(dp(180))
    val scroll = object : ScrollView(this) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cappedSpec = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
                MeasureSpec.makeMeasureSpec(maxScrollPx, MeasureSpec.AT_MOST)
            } else {
                MeasureSpec.makeMeasureSpec(
                    minOf(MeasureSpec.getSize(heightMeasureSpec), maxScrollPx),
                    MeasureSpec.AT_MOST
                )
            }
            super.onMeasure(widthMeasureSpec, cappedSpec)
        }
    }.apply {
        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    val tvReport = TextView(this).apply {
        text = "Generating report..."
        textSize = 13.5f
        setTextColor(p.textBody)
        setTextIsSelectable(true)
        setLineSpacing(dp(2).toFloat(), 1.16f)
        DesignSystem.applyTabularNumerals(this)
    }
    scroll.addView(tvReport)
    container.addView(scroll)

    var activeReportText = ""
    var activeScopedPins = layerPinsSnapshot
    var genSeq = 0

    fun applyProgressWeights(doneW: Float, issueW: Float, pendingW: Float) {
        (segDone.layoutParams as LinearLayout.LayoutParams).weight = doneW
        (segIssue.layoutParams as LinearLayout.LayoutParams).weight = issueW
        (segPending.layoutParams as LinearLayout.LayoutParams).weight = pendingW
        barSegments.requestLayout()
    }

    fun computeAndDisplay(useMapFiltered: Boolean) {
        val mySeq = ++genSeq
        activeScopedPins = if (useMapFiltered) mapFilteredPins else layerPinsSnapshot
        if (mapFilterHidesSome) {
            DesignSystem.styleSegmentedButton(btnScopeFull, !useMapFiltered, p)
            DesignSystem.styleSegmentedButton(btnScopeVisible, useMapFiltered, p)
            tvScopeBanner.text = if (useMapFiltered) {
                "Matching active map filters (${mapFilteredPins.size} of ${layerPinsSnapshot.size} pins visible on map)."
            } else {
                "Showing full folder scope (${layerPinsSnapshot.size} pins). Active map filters hide ${layerPinsSnapshot.size - mapFilteredPins.size} pins on map."
            }
        }
        val titleForScope = if (useMapFiltered) "${layer.name} (Filtered)" else layer.name
        val pinsForRun = activeScopedPins
        RoutingEngine.ioExecutor.execute {
            val report = DailyReportGenerator.buildReport(
                folderName = titleForScope,
                pins = pinsForRun,
                isCloudLive = isLayerLive,
                workStartMin = workStart,
                workEndMin = workEnd,
                syncBadgeOverride = resolvedBadge,
                configuredDeviceAliases = aliases,
                allKnownPinsForDeviceMap = allPinsSnapshot
            )
            runOnUiThread {
                if (isFinishing || isDestroyed || mySeq != genSeq) return@runOnUiThread
                activeReportText = report.whatsappText
                tvReport.text = formatWhatsAppReportPreview(report.whatsappText, p)

                val visited = report.doneCount + report.issueCount
                val total = report.totalPins
                val pct = if (total > 0) ((visited * 100.0) / total).roundToInt() else 0
                tvProgressPct.text = "$pct% ($visited / $total)"
                chipDone.text = "✓ ${report.doneCount} $doneLbl"
                chipIssue.text = "⚠ ${report.issueCount} $issueLbl"
                chipPending.text = "○ ${report.pendingCount} $pendingLbl"

                barSegments.animate().cancel()
                applyProgressWeights(
                    report.doneCount.toFloat(),
                    report.issueCount.toFloat(),
                    if (total > 0) report.pendingCount.toFloat() else 1f
                )
                if (total <= 0 || UiAnimations.isReducedMotion(this@showDailyWhatsAppReportDialog)) {
                    barSegments.scaleX = 1f
                } else {
                    barSegments.scaleX = 0f
                    val rtlBar = AppStrings.isRtl(this@showDailyWhatsAppReportDialog)
                    barSegments.post {
                        if (isFinishing || isDestroyed || mySeq != genSeq) return@post
                        barSegments.pivotX = if (rtlBar) barTrack.width.toFloat() else 0f
                        barSegments.pivotY = barTrack.height * 0.5f
                        barSegments.animate()
                            .scaleX(1f)
                            .setDuration(280L)
                            .setInterpolator(UiAnimations.EMPHASIZED_DECELERATE)
                            .start()
                    }
                }
            }
        }
    }

    btnScopeFull.setOnClickListener { computeAndDisplay(false) }
    btnScopeVisible.setOnClickListener { computeAndDisplay(true) }
    computeAndDisplay(false)

    var reportDialog: AlertDialog? = null
    val ctaRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dp(12)
        }
    }
    val btnCopy = MaterialButton(this).apply {
        text = AppStrings.copy(this@showDailyWhatsAppReportDialog)
        textSize = 13f
        isAllCaps = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
        cornerRadius = dp(18)
        DesignSystem.applySecondaryTonalButton(this, p)
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }
        UiAnimations.attachSnappyPress(this)
        setOnClickListener {
            val textToCopy = activeReportText.ifBlank { tvReport.text.toString() }
            copyToClipboard("Daily Report", textToCopy)
            reportDialog?.dismiss()
        }
    }
    val btnKml = MaterialButton(this).apply {
        text = "KML"
        textSize = 13f
        isAllCaps = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(8), 0, dp(8), 0)
        cornerRadius = dp(18)
        DesignSystem.applySecondaryTonalButton(this, p)
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) }
        UiAnimations.attachSnappyPress(this)
        setOnClickListener {
            val textToShare = activeReportText.ifBlank { tvReport.text.toString() }
            KmlCsvExporter.exportAndShareKml(this@showDailyWhatsAppReportDialog, layer, activeScopedPins, layerTracks, "", textToShare)
            reportDialog?.dismiss()
        }
    }
    val btnShare = MaterialButton(this).apply {
        text = AppStrings.share(this@showDailyWhatsAppReportDialog)
        textSize = 13.5f
        setTypeface(null, Typeface.BOLD)
        isAllCaps = false
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        insetTop = 0
        insetBottom = 0
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(10), 0, dp(10), 0)
        cornerRadius = dp(18)
        setTextColor(Color.WHITE)
        backgroundTintList = ColorStateList.valueOf(p.accentPrimary)
        icon = ContextCompat.getDrawable(this@showDailyWhatsAppReportDialog, R.drawable.ic_share)
        iconTint = ColorStateList.valueOf(Color.WHITE)
        iconSize = dp(15)
        iconPadding = dp(6)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        elevation = 0f
        stateListAnimator = null
        layoutParams = LinearLayout.LayoutParams(0, dp(42), 1.35f)
        UiAnimations.attachSnappyPress(this)
        setOnClickListener {
            val textToShare = activeReportText.ifBlank { tvReport.text.toString() }
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, textToShare)
            }
            reportDialog?.dismiss()
            safeStartActivity(Intent.createChooser(share, "Share Daily Report"))
        }
    }
    ctaRow.addView(btnCopy)
    ctaRow.addView(btnKml)
    ctaRow.addView(btnShare)
    container.addView(ctaRow)

    reportDialog = showThemedDialog(
        themedDialogBuilder(AppStrings.dailyFieldReport(this))
            .setView(container)
            .create()
    )
    reportDialog.setOnDismissListener { barSegments.animate().cancel() }
}
