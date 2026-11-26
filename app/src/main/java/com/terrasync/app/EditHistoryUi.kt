package com.terrasync.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton

internal fun MainActivity.showWorkingHoursDialog(onSaved: () -> Unit) {
    val p = currentPalette()
    val curStart = DevicePrefs.getWorkStartMinutes(this)
    val curEnd = DevicePrefs.getWorkEndMinutes(this)

    val box = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(22), dp(8), dp(22), dp(4))
    }
    box.addView(TextView(this).apply {
        text = "Submissions made outside this shift window are automatically flagged as After Hours."
        textSize = 13f
        setLineSpacing(dp(2).toFloat(), 1.05f)
        setTextColor(p.textSecondary)
        setPadding(0, 0, 0, dp(12))
    })

    var chosenStartMin = curStart
    var chosenEndMin = curEnd

    fun makeTimePickerRow(label: String, initialMin: Int, onPicked: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            setTextColor(p.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        val btnPick = MaterialButton(this).apply {
            text = PinEditHistoryAudit.formatMinutes(initialMin)
            textSize = 14f
            isAllCaps = false
            insetTop = 0
            insetBottom = 0
            setTextColor(Color.WHITE)
            setBackgroundColor(p.accent)
            cornerRadius = dp(10)
            DesignSystem.applyTabularNumerals(this)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
            UiAnimations.attachSnappyPress(this)
            setOnClickListener {
                val cur = if (label.startsWith("Start")) chosenStartMin else chosenEndMin
                val pickerTheme = if (p.isDark) {
                    android.R.style.Theme_Material_Dialog_Alert
                } else {
                    android.R.style.Theme_Material_Light_Dialog_Alert
                }
                android.app.TimePickerDialog(
                    this@showWorkingHoursDialog,
                    pickerTheme,
                    { _, h, m ->
                        val total = h * 60 + m
                        onPicked(total)
                        text = PinEditHistoryAudit.formatMinutes(total)
                    },
                    cur / 60,
                    cur % 60,
                    true
                ).show()
            }
        }
        row.addView(btnPick)
        return row
    }

    box.addView(makeTimePickerRow("Start Time", curStart) { chosenStartMin = it })
    box.addView(makeTimePickerRow("End Time", curEnd) { chosenEndMin = it })

    var dialogRef: AlertDialog? = null
    val presetsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    lateinit var renderPresets: () -> Unit

    val presetHeader = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(6))
    }
    presetHeader.addView(TextView(this).apply {
        text = "Quick presets (tap to apply, hold to delete):"
        textSize = 11.5f
        setTypeface(null, Typeface.BOLD)
        setTextColor(p.textSecondary)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
    })
    presetHeader.addView(TextView(this).apply {
        text = "+ Save Current"
        textSize = 12f
        setTypeface(null, Typeface.BOLD)
        setTextColor(p.accentPrimary)
        setPadding(dp(6), dp(4), dp(2), dp(4))
        UiAnimations.applyRowRipple(this@showWorkingHoursDialog, this, p.isDark)
        setOnClickListener {
            if (chosenStartMin == chosenEndMin) {
                Toast.makeText(this@showWorkingHoursDialog, "Start and End times cannot be identical", Toast.LENGTH_SHORT).show()
            } else {
                DevicePrefs.addWorkHourPreset(this@showWorkingHoursDialog, chosenStartMin, chosenEndMin)
                renderPresets()
                val lbl = "${PinEditHistoryAudit.formatMinutes(chosenStartMin)}–${PinEditHistoryAudit.formatMinutes(chosenEndMin)}"
                Toast.makeText(this@showWorkingHoursDialog, "Saved preset $lbl", Toast.LENGTH_SHORT).show()
            }
        }
    })
    box.addView(presetHeader)

    renderPresets = {
        presetsBox.removeAllViews()
        val nightNow = currentPalette().isDark
        val presets = DevicePrefs.getWorkHourPresets(this)
        presets.chunked(3).forEachIndexed { rowIdx, rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                if (rowIdx > 0) setPadding(0, dp(6), 0, 0)
            }
            rowItems.forEachIndexed { colIdx, (sMin, eMin) ->
                val lbl = "${PinEditHistoryAudit.formatMinutes(sMin)}–${PinEditHistoryAudit.formatMinutes(eMin)}"
                row.addView(MaterialButton(this).apply {
                    text = lbl
                    textSize = 11.5f
                    isAllCaps = false
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(4), 0, dp(4), 0)
                    setTextColor(p.textPrimary)
                    setBackgroundColor(p.chipSurface)
                    cornerRadius = dp(8)
                    DesignSystem.applyTabularNumerals(this)
                    layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                        if (colIdx < 2) marginEnd = dp(6)
                    }
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        chosenStartMin = sMin
                        chosenEndMin = eMin
                        DevicePrefs.setWorkStartMinutes(this@showWorkingHoursDialog, sMin)
                        DevicePrefs.setWorkEndMinutes(this@showWorkingHoursDialog, eMin)
                        Toast.makeText(this@showWorkingHoursDialog, "Working hours set to $lbl", Toast.LENGTH_SHORT).show()
                        onSaved()
                        dialogRef?.dismiss()
                    }
                    setOnLongClickListener {
                        if (presets.size <= 1) {
                            Toast.makeText(this@showWorkingHoursDialog, "Keep at least 1 preset", Toast.LENGTH_SHORT).show()
                        } else {
                            DevicePrefs.removeWorkHourPreset(this@showWorkingHoursDialog, sMin, eMin)
                            renderPresets()
                            Toast.makeText(this@showWorkingHoursDialog, "Removed preset $lbl", Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                })
            }
            repeat(3 - rowItems.size) { remIdx ->
                row.addView(android.widget.Space(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                        if (rowItems.size + remIdx < 2) marginEnd = dp(6)
                    }
                })
            }
            presetsBox.addView(row)
        }
        if (presets != DevicePrefs.DEFAULT_WORK_HOUR_PRESETS) {
            presetsBox.addView(TextView(this).apply {
                text = "Reset Presets"
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                setTextColor(p.red)
                setPadding(dp(2), dp(8), dp(6), dp(2))
                UiAnimations.applyRowRipple(this@showWorkingHoursDialog, this, nightNow)
                setOnClickListener {
                    DevicePrefs.resetWorkHourPresets(this@showWorkingHoursDialog)
                    renderPresets()
                    Toast.makeText(this@showWorkingHoursDialog, "Default presets restored", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }
    renderPresets()
    box.addView(presetsBox)

    dialogRef = showThemedDialog(
        themedDialogBuilder("Working Hours Window")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                DevicePrefs.setWorkStartMinutes(this, chosenStartMin)
                DevicePrefs.setWorkEndMinutes(this, chosenEndMin)
                val win = "${PinEditHistoryAudit.formatMinutes(chosenStartMin)}–${PinEditHistoryAudit.formatMinutes(chosenEndMin)}"
                Toast.makeText(this, "Working hours set to $win", Toast.LENGTH_SHORT).show()
                onSaved()
            }
            .setNegativeButton(AppStrings.cancel(this), null)
            .create()
    )
}

internal fun MainActivity.buildEditHistoryPage(layer: LayerItem): View {
    val p = currentPalette()
    val night = p.isDark
    val txtPrimary = p.textPrimary
    val txtSub = p.textSecondary
    val isOwner = isLayerOwner(layer)
    val isLocal = LocalFolderStore.isLocalLayerId(layer.id)

    var currentEntries: List<PinEditEntry> = emptyList()

    val btnShareAudit = MaterialButton(this).apply {
        text = AppStrings.share(this@buildEditHistoryPage)
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
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38))
        UiAnimations.attachSnappyPress(this)
        setOnClickListener {
            val txt = PinEditHistoryAudit.buildAuditShareText(
                layer.name,
                currentEntries,
                DevicePrefs.getWorkStartMinutes(this@buildEditHistoryPage),
                DevicePrefs.getWorkEndMinutes(this@buildEditHistoryPage)
            )
            safeStartActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, txt)
            }, "Share Edit History Audit"))
        }
    }

    val (outer, body) = makePageFrame(AppStrings.editHistory(this), {
        if (pageBackStack.isNotEmpty()) pageBackStack.removeAt(pageBackStack.lastIndex).invoke() else closeFullPage()
    }, btnShareAudit)

    var timeFilter = "all" // "all", "after_hours", "in_hours"
    var operatorFilter: String? = null
    var displayLimit = 120
    var auditSeq = 0

    val contentRoot = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    body.addView(contentRoot)

    fun renderAudit(animateReload: Boolean = false) {
        val mySeq = ++auditSeq
        val workStart = DevicePrefs.getWorkStartMinutes(this)
        val workEnd = DevicePrefs.getWorkEndMinutes(this)
        val windowStr = "${PinEditHistoryAudit.formatMinutes(workStart)} – ${PinEditHistoryAudit.formatMinutes(workEnd)}"
        val layerPinsSnap = pinsByLayerId[layer.id].orEmpty().toList()
        val allPinsSnap = pins.toList()
        val aliasesSnap = buildLocalDeviceAliasMap()
        val curTimeFilter = timeFilter
        val curOpFilter = operatorFilter
        val curDisplayLimit = displayLimit

        if (contentRoot.childCount == 0) {
            contentRoot.addView(TextView(this).apply {
                text = "Loading edit history..."
                textSize = 13.5f
                setTextColor(txtSub)
                setPadding(dp(4), dp(14), dp(4), dp(14))
            })
        }

        RoutingEngine.ioExecutor.execute {
            val deviceMap = DailyReportGenerator.buildDeviceToTeamMap(allPinsSnap, aliasesSnap)
            val allEntries = PinEditHistoryAudit.collectFolderHistory(layerPinsSnap, workStart, workEnd, deviceMap)
            val afterHoursCnt = allEntries.count { it.isOutsideWorkHours }
            val inHoursCnt = allEntries.size - afterHoursCnt
            val operatorCounts = allEntries.groupingBy { it.operatorShort }.eachCount()
                .entries.sortedByDescending { it.value }
            val filtered = allEntries.filter { e ->
                val timeOk = when (curTimeFilter) {
                    "after_hours" -> e.isOutsideWorkHours
                    "in_hours" -> !e.isOutsideWorkHours
                    else -> true
                }
                val opOk = curOpFilter == null || e.operatorShort == curOpFilter
                timeOk && opOk
            }
            val pageItems = filtered.take(curDisplayLimit)
            val groupedByDate = pageItems.groupBy { it.dateGroup }

            runOnUiThread {
                if (isFinishing || isDestroyed || mySeq != auditSeq) return@runOnUiThread
                currentEntries = allEntries
                contentRoot.removeAllViews()

                // 1. Summary & Working-Hours Control Card
                val summaryCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 14, paddingVDp = 12, bottomMarginDp = 10)

                val folderRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, dp(8))
                }
                folderRow.addView(TextView(this).apply {
                    text = layer.name
                    textSize = 15.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(8)
                    }
                })
                folderRow.addView(TextView(this).apply {
                    text = if (isLocal) "LOCAL" else if (isOwner) "CLOUD OWNER" else "CLOUD"
                    textSize = 10f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (isLocal) p.accentPrimary else p.green)
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                    background = GradientDrawable().apply {
                        setColor(if (isLocal) p.accentPrimarySoft else p.greenSoft)
                        cornerRadius = dp(8).toFloat()
                    }
                })
                summaryCard.addView(folderRow)

                val hoursRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(4), 0, dp(10))
                }
                val hoursCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                hoursCol.addView(TextView(this).apply {
                    text = AppStrings.workingHours(this@buildEditHistoryPage)
                    textSize = 11.5f
                    setTextColor(txtSub)
                })
                hoursCol.addView(TextView(this).apply {
                    text = windowStr
                    textSize = 15f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    DesignSystem.applyTabularNumerals(this)
                })
                hoursRow.addView(hoursCol)
                hoursRow.addView(MaterialButton(this).apply {
                    text = AppStrings.change(this@buildEditHistoryPage)
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    isAllCaps = false
                    insetTop = 0
                    insetBottom = 0
                    minWidth = 0
                    minimumWidth = 0
                    setPadding(dp(14), 0, dp(14), 0)
                    cornerRadius = dp(18)
                    setTextColor(p.accentPrimary)
                    backgroundTintList = ColorStateList.valueOf(p.accentPrimarySoft)
                    strokeWidth = 0
                    elevation = 0f
                    stateListAnimator = null
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36))
                    UiAnimations.attachSnappyPress(this)
                    setOnClickListener {
                        showWorkingHoursDialog { renderAudit(false) }
                    }
                })
                summaryCard.addView(hoursRow)

                summaryCard.addView(DesignSystem.createHairlineDivider(this, p))

                // 3 Tabular Stat Counters
                val statsRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(10), 0, dp(2))
                }
                fun addStat(label: String, count: Int, color: Int) {
                    val col = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER_HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    col.addView(TextView(this).apply {
                        text = "$count"
                        textSize = 20f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(color)
                        DesignSystem.applyTabularNumerals(this)
                    })
                    col.addView(TextView(this).apply {
                        text = label
                        textSize = 11.5f
                        setTextColor(txtSub)
                    })
                    statsRow.addView(col)
                }
                addStat(AppStrings.totalEdits(this), allEntries.size, txtPrimary)
                addStat(AppStrings.inHours(this), inHoursCnt, p.green)
                addStat(AppStrings.afterHours(this), afterHoursCnt, if (afterHoursCnt > 0) p.red else txtSub)
                summaryCard.addView(statsRow)
                contentRoot.addView(summaryCard)

                // 2. Time Filter Pills (All / After Hours / In Hours)
                val filterScroll = android.widget.HorizontalScrollView(this).apply {
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = View.OVER_SCROLL_NEVER
                }
                val filterRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(2), 0, dp(8))
                }
                listOf(
                    listOf("all", "${AppStrings.all(this)} (${allEntries.size})", p.accent, 0),
                    listOf("after_hours", "${AppStrings.afterHours(this)} ($afterHoursCnt)", p.red, R.drawable.ic_history),
                    listOf("in_hours", "${AppStrings.inHours(this)} ($inHoursCnt)", p.green, R.drawable.ic_check)
                ).forEachIndexed { idx, spec ->
                    val key = spec[0] as String
                    val lbl = spec[1] as String
                    val activeCol = spec[2] as Int
                    val iconRes = spec[3] as Int
                    val selected = curTimeFilter == key
                    filterRow.addView(MaterialButton(this).apply {
                        text = lbl
                        textSize = 12f
                        isAllCaps = false
                        insetTop = 0
                        insetBottom = 0
                        minWidth = 0
                        minimumWidth = 0
                        setPadding(dp(12), 0, dp(12), 0)
                        cornerRadius = dp(12)
                        DesignSystem.applyTabularNumerals(this)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                            if (idx < 2) marginEnd = dp(6)
                        }
                        DesignSystem.styleSegmentedButton(this, selected, p, activeCol)
                        if (iconRes != 0) {
                            icon = ContextCompat.getDrawable(this@buildEditHistoryPage, iconRes)
                            iconSize = dp(14)
                            iconPadding = dp(5)
                            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                            iconTint = ColorStateList.valueOf(if (selected) Color.WHITE else activeCol)
                        }
                        UiAnimations.attachSnappyPress(this)
                        setOnClickListener {
                            if (timeFilter != key) {
                                timeFilter = key
                                displayLimit = 120
                                renderAudit(animateReload = true)
                            }
                        }
                    })
                }
                filterScroll.addView(filterRow)
                contentRoot.addView(filterScroll)

                // 3. Operator Filter Pills
                if (operatorCounts.size > 1) {
                    val opScroll = android.widget.HorizontalScrollView(this).apply {
                        isHorizontalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_NEVER
                    }
                    val opRow = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, 0, 0, dp(10))
                    }
                    fun addOpChip(label: String, opKey: String?) {
                        opRow.addView(MaterialButton(this).apply {
                            text = label
                            textSize = 11.5f
                            isAllCaps = false
                            insetTop = 0
                            insetBottom = 0
                            minWidth = 0
                            minimumWidth = 0
                            setPadding(dp(10), 0, dp(10), 0)
                            cornerRadius = dp(9)
                            DesignSystem.applyTabularNumerals(this)
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)).apply {
                                marginEnd = dp(6)
                            }
                            DesignSystem.styleSegmentedButton(this, curOpFilter == opKey, p)
                            UiAnimations.attachSnappyPress(this)
                            setOnClickListener {
                                if (operatorFilter != opKey) {
                                    operatorFilter = opKey
                                    displayLimit = 120
                                    renderAudit(animateReload = true)
                                }
                            }
                        })
                    }
                    addOpChip(AppStrings.allTeams(this), null)
                    operatorCounts.forEach { (opName, cnt) ->
                        addOpChip("$opName ($cnt)", opName)
                    }
                    opScroll.addView(opRow)
                    contentRoot.addView(opScroll)
                }

                // 4. Filtered Chronological Timeline Grouped by Date
                if (filtered.isEmpty()) {
                    val emptyCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 16, paddingVDp = 18)
                    emptyCard.addView(TextView(this).apply {
                        text = when {
                            allEntries.isEmpty() -> "No edits recorded yet in this folder. Whenever team members update a pin, each timestamped submission appears here."
                            curTimeFilter == "after_hours" -> "✓ Great — zero submissions were made outside working hours ($windowStr)."
                            else -> "No matching submissions for this filter."
                        }
                        textSize = 13.5f
                        setLineSpacing(dp(2).toFloat(), 1.05f)
                        setTextColor(txtSub)
                    })
                    contentRoot.addView(emptyCard)
                    if (animateReload) UiAnimations.staggerRevealChildren(contentRoot, maxAnimated = 4)
                    return@runOnUiThread
                }

        groupedByDate.forEach { (dateLabel, dayEntries) ->
            val dayLate = dayEntries.count { it.isOutsideWorkHours }
            val headerTxt = if (dayLate > 0) "$dateLabel  •  $dayLate after hours" else "$dateLabel  •  ${dayEntries.size} edit${if (dayEntries.size == 1) "" else "s"}"
            contentRoot.addView(DesignSystem.createSectionHeader(this, headerTxt, p))

            val dayCard = DesignSystem.createGroupedCard(this, p, radiusDp = 16f, paddingHDp = 0, paddingVDp = 2, bottomMarginDp = 8)
            dayEntries.forEachIndexed { idx, entry ->
                if (idx > 0) {
                    dayCard.addView(DesignSystem.createHairlineDivider(this, p, 84, 14))
                }
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = dp(58)
                    setPadding(dp(12), 0, dp(10), 0)
                    if (entry.isOutsideWorkHours) {
                        setBackgroundColor(p.redSoft)
                    } else {
                        UiAnimations.applyRowRipple(this@buildEditHistoryPage, this, night)
                    }
                    setOnClickListener {
                        ensurePinVisibleOnMap(entry.pin)
                        closeFullPage()
                        selectPinAndOpenCard(entry.pin, zoomIfFar = true)
                    }
                }

                val timeCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, dp(12), 0, dp(12))
                    layoutParams = LinearLayout.LayoutParams(dp(46), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        marginEnd = dp(6)
                    }
                }
                timeCol.addView(TextView(this).apply {
                    text = entry.clockTime
                    textSize = 13.5f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (entry.isOutsideWorkHours) p.red else txtPrimary)
                    DesignSystem.applyTabularNumerals(this)
                })
                row.addView(timeCol)

                val dotColor = when (entry.actionStatus) {
                    "done" -> p.green
                    "issue" -> p.red
                    "pending" -> p.amber
                    else -> p.accent
                }
                val railCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(dp(16), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                        marginEnd = dp(8)
                    }
                }
                railCol.addView(View(this).apply {
                    setBackgroundColor(if (idx > 0) p.borderSubtle else Color.TRANSPARENT)
                    layoutParams = LinearLayout.LayoutParams(dp(2), 0, 1f)
                })
                railCol.addView(View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(dotColor)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(9), dp(9)).apply {
                        topMargin = dp(3)
                        bottomMargin = dp(3)
                    }
                })
                railCol.addView(View(this).apply {
                    setBackgroundColor(if (idx < dayEntries.lastIndex) p.borderSubtle else Color.TRANSPARENT)
                    layoutParams = LinearLayout.LayoutParams(dp(2), 0, 1f)
                })
                row.addView(railCol)

                val infoCol = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, dp(10), 0, dp(10))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(6)
                    }
                }
                val titleRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                titleRow.addView(TextView(this).apply {
                    text = entry.pin.name
                    textSize = 14.5f
                    textDirection = View.TEXT_DIRECTION_LOCALE
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(txtPrimary)
                    setLineSpacing(dp(1).toFloat(), 1.05f)
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (entry.isOutsideWorkHours) {
                    titleRow.addView(TextView(this).apply {
                        text = "AFTER HOURS"
                        textSize = 9.5f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        setPadding(dp(6), dp(2), dp(6), dp(2))
                        background = GradientDrawable().apply {
                            setColor(p.red)
                            cornerRadius = dp(6).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { marginStart = dp(6) }
                    })
                }
                infoCol.addView(titleRow)

                infoCol.addView(TextView(this).apply {
                    text = "${entry.operatorShort} • ${entry.actionSummary}"
                    textSize = 12.5f
                    setTextColor(
                        when {
                            entry.actionStatus == "issue" -> p.red
                            entry.actionStatus == "done" -> p.green
                            else -> txtSub
                        }
                    )
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                    setPadding(0, dp(2), 0, 0)
                })
                row.addView(infoCol)

                if (isOwner && entry.historyLineIndex >= 0) {
                    row.addView(TextView(this).apply {
                        text = "✕"
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(p.red)
                        gravity = Gravity.CENTER
                        background = GradientDrawable().apply {
                            setColor(if (entry.isOutsideWorkHours) Color.parseColor(if (night) "#452328" else "#FCE3E1") else p.chipSurface)
                            cornerRadius = dp(10).toFloat()
                        }
                        layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply {
                            marginStart = dp(6)
                        }
                        UiAnimations.attachSnappyPress(this)
                        setOnClickListener {
                            showThemedDialog(
                                themedDialogBuilder("Remove this submission?")
                                    .setMessage("[${entry.fullTimeLabel}] ${entry.operatorShort}\n${entry.pin.name} → ${entry.actionSummary}\n\nOnly this edit will be removed. The pin remains on the map.")
                                    .setPositiveButton("Delete Edit") { _, _ ->
                                        deletePinHistoryEntryUnified(entry.pin, entry.historyLineIndex)
                                        Toast.makeText(this@buildEditHistoryPage, "Submission removed", Toast.LENGTH_SHORT).show()
                                        renderAudit(false)
                                    }
                                    .setNegativeButton(AppStrings.cancel(this@buildEditHistoryPage), null)
                                    .create(),
                                destructivePositive = true
                            )
                        }
                    })
                }
                dayCard.addView(row)
            }
            contentRoot.addView(dayCard)
        }

        val remaining = filtered.size - pageItems.size
        if (remaining > 0) {
            contentRoot.addView(MaterialButton(this).apply {
                text = "Show more ($remaining remaining)"
                textSize = 13f
                isAllCaps = false
                insetTop = 0
                insetBottom = 0
                cornerRadius = dp(12)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                    topMargin = dp(4)
                }
                DesignSystem.styleSegmentedButton(this, false, p)
                setOnClickListener {
                    displayLimit += 120
                    renderAudit(false)
                }
            })
        }

        if (animateReload) {
            UiAnimations.staggerRevealChildren(contentRoot, maxAnimated = 4)
        }
            }
        }
    }

    liveEditHistoryRefresh = { renderAudit(false) }
    renderAudit(false)
    return outer
}
