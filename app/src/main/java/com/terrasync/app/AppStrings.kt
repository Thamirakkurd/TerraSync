package com.terrasync.app

import android.content.Context

/**
 * Lightweight, zero-overhead UI localization supporting English, Kurdish (Sorani),
 * Kurdish (Badini), and Arabic with consistent utilitarian Organic Maps phrasing.
 */
object AppStrings {

    fun isRtl(ctx: Context): Boolean = DevicePrefs.getAppLanguage(ctx).isRtl

    fun tr(ctx: Context, en: String, kuSorani: String, kuBadini: String, ar: String): String {
        return when (DevicePrefs.getAppLanguage(ctx)) {
            AppLanguage.KURDISH_SORANI -> kuSorani
            AppLanguage.KURDISH_BADINI -> kuBadini
            AppLanguage.ARABIC -> ar
            AppLanguage.ENGLISH -> en
        }
    }

    // Backwards-compatible 3-arg overload (Badini falls back to Sorani if not explicitly passed)
    fun tr(ctx: Context, en: String, ku: String, ar: String): String = tr(ctx, en, ku, ku, ar)

    fun search(ctx: Context) = tr(ctx, "Search", "گەڕان", "گەڕیان", "بحث")
    fun bookmarks(ctx: Context) = tr(ctx, "Bookmarks", "نیشانکراوەکان", "نیشانکری", "الإشارات المرجعية")
    fun settings(ctx: Context) = tr(ctx, "Settings", "ڕێکخستنەکان", "ڕێکخستن", "الإعدادات")
    fun about(ctx: Context) = tr(ctx, "About TerraSync", "دەربارەی TerraSync", "دەربارەی TerraSync", "حول TerraSync")

    // Settings Sections
    fun secMapShaders(ctx: Context) = tr(ctx, "Map & Shaders", "نەخشە و شێدەرەکان", "نەخشە و شێدەر", "الخريطة والتضاريس")
    fun secLanguageLabels(ctx: Context) = tr(ctx, "Language & Labels", "زمان و ناوەکان", "زمان و ناڤ", "اللغة والتسميات")
    fun secNavigationRouting(ctx: Context) = tr(ctx, "Navigation & Routing", "ڕێنمایی و ڕێگا", "ڕێنیشاندان و ڕێک", "الملاحة والتوجيه")
    fun secFieldDataSync(ctx: Context) = tr(ctx, "Field Data & Sync", "داتای مەیدانی و هاوکاتکردن", "داتایێن مەیدانی و هەڤدەمکرن", "البيانات الميدانية والمزامنة")
    fun secOfflineCache(ctx: Context) = tr(ctx, "Offline Maps & Cache", "نەخشەی بێ ئینتەرنێت", "نەخشەیێ بێ ئینتەرنێت و کاش", "الخرائط دون اتصال والذاكرة")
    fun secAboutBuild(ctx: Context) = tr(ctx, "About & Build", "دەربارە و وەشان", "دەربارە و وەشان", "حول التطبيق والبناء")

    // Common UI labels
    fun routeTo(ctx: Context) = tr(ctx, "Route To", "ڕێگا بۆ ئێرە", "ڕێک بو ڤێرێ", "المسار إلى هنا")
    fun routeFrom(ctx: Context) = tr(ctx, "Route From", "ڕێگا لە ئێرەوە", "ڕێک ژ ڤێرێ", "المسار من هنا")
    fun startNav(ctx: Context) = tr(ctx, "Start Navigation", "دەستپێکردنی ڕێنمایی", "دەستپێکرنا ڕێنیشاندانێ", "بدء الملاحة")
    fun edit(ctx: Context) = tr(ctx, "Edit", "دەستکاری", "گوهارتن", "تعديل")
    fun external(ctx: Context) = tr(ctx, "External", "دەرەکی", "دەرڤەیی", "خارجي")
    fun savePin(ctx: Context) = tr(ctx, "Save Pin", "پاشەکەوتکردن", "پاشکەفتکرن", "حفظ النقطة")
    fun copyGps(ctx: Context) = tr(ctx, "Copy GPS", "کۆپیکردنی GPS", "کۆپیکرنا GPS", "نسخ الإحداثيات")
    fun pending(ctx: Context) = tr(ctx, "Pending", "چاوەڕوان", "چاڤەڕێ", "قيد الانتظار")
    fun done(ctx: Context) = tr(ctx, "Done", "تەواوبوو", "ب دوماهیک هات", "مكتمل")
    fun issue(ctx: Context) = tr(ctx, "Issue", "کێشە", "ئاریشە", "مشكلة")
    fun showAllBookmarks(ctx: Context) = tr(ctx, "Show all bookmarks on map", "پیشاندانی هەموو نیشانکراوەکان", "نیشاندانا هەمی نیشانکریان", "إظهار جميع الإشارات على الخريطة")
    fun showBookmarkTitles(ctx: Context) = tr(ctx, "Show bookmark titles", "پیشاندانی ناوی نیشانکراوەکان", "نیشاندانا ناڤێ نیشانکریان", "إظهار أسماء الإشارات")
    fun searchMapPlaces(ctx: Context) = tr(ctx, "Search Map Places", "گەڕان لە نەخشە", "ل نەخشەی بگەڕە", "البحث في الخريطة")
    fun nearestPending(ctx: Context) = tr(ctx, "Nearest Pending", "نزیکترین چاوەڕوان", "نێزیکترین چاڤەڕێ", "أقرب نقطة معلقة")
    fun zoom(ctx: Context) = tr(ctx, "Zoom", "نزیککردنەوە", "نێزیککرن", "تكبير")

    // Map Layers
    fun mapLayers(ctx: Context) = tr(ctx, "Map Layers", "چینەکانی نەخشە", "تەبەقێن نەخشەی", "طبقات الخريطة")
    fun mapLayersSubtitle(ctx: Context) = tr(ctx, "Style and visual map overlays", "شێواز و چینە بینراوەکانی نەخشە", "شێواز و تەبەقێن دیتنێ", "نمط وطبقات الخريطة")
    fun vectorStreet(ctx: Context) = tr(ctx, "Vector Street", "نەخشەی شەقام", "نەخشەیا جاددان", "خريطة الشوارع")
    fun vectorStreetSub(ctx: Context) = tr(ctx, "Vector roads & terrain", "ڕێگای ڤێکتەر و بەرزی", "ڕێکێن ڤێکتەر و عەرد", "طرق وتضاريس")
    fun satelliteHybrid(ctx: Context) = tr(ctx, "Satellite Hybrid", "مانگی دەستکرد", "سەتەلایت تێکەڵ", "صور الأقمار الصناعية")
    fun satelliteHybridSub(ctx: Context) = tr(ctx, "Aerial imagery + roads", "وێنەی ئاسمانی + ڕێگا", "وێنێن ئاسمانی + ڕێک", "تصوير جوي + طرق")
    fun visualOverlays(ctx: Context) = tr(ctx, "Visual Overlays", "چینە بینراوەکان", "تەبەقێن زێدە", "طبقات العرض")
    fun mountainHillshading(ctx: Context) = tr(ctx, "Mountain Hillshading", "سێبەری شاخەکان", "سیبەرا چیایان", "تظليل التضاريس")
    fun mountainHillshadingSub(ctx: Context) = tr(ctx, "Organic terrain relief shading", "سێبەری بەرزی و نزمی زەوی", "سیبەرا بلنداهی و نزمیا عەردی", "تظليل طبيعي للتضاريس")
    fun buildings3d(ctx: Context) = tr(ctx, "3D Buildings", "بینای سێ ڕەهەندی", "ئاڤاهیێن 3D", "مباني ثلاثية الأبعاد")
    fun buildings3dSub(ctx: Context) = tr(ctx, "Extruded building footprints in urban areas", "شێوەی سێ ڕەهەندی بیناکان", "ئاڤاهیێن ب شێوازێ 3D ل باژێڕان", "مجسمات المباني في المناطق الحضرية")
    fun elevationContours(ctx: Context) = tr(ctx, "Elevation Contour Lines", "هێڵەکانی بەرزی", "هێلێن بلنداهیێ", "خطوط الكنتور")
    fun elevationContoursSub(ctx: Context) = tr(ctx, "100 m / 500 m / 1000 m contour lines & labels", "هێڵی بەرزی ١٠٠ / ٥٠٠ / ١٠٠٠ م", "هێلێن بلنداهیێ 100 / 500 / 1000 م", "خطوط ارتفاع 100 / 500 / 1000 م")

    // Filter Pins
    fun filterPins(ctx: Context) = tr(ctx, "Filter Pins", "پاڵاوتنی نیشانەکان", "فلتەرکرنا نیشانان", "تصفية النقاط")
    fun pinsInVisibleFolders(ctx: Context, count: Int) = tr(
        ctx,
        "$count pins in visible folders",
        "$count نیشانە لە فۆڵدەرە پیشاندراوەکاندا",
        "$count نیشان ل فۆلدەرێن دیارکری دا",
        "$count نقطة في المجلدات الظاهرة"
    )
    fun reset(ctx: Context) = tr(ctx, "Reset", "ڕێکخستنەوە", "ژ نووی ڤە", "إعادة تعيين")
    fun unsentOnly(ctx: Context) = tr(ctx, "Unsent Only", "تەنها نەنێردراوەکان", "بتنێ یێن نەهاتینە هنارتن", "غير المرسلة فقط")
    fun hideSentPinsSub(ctx: Context, count: Int): String {
        val pluralEn = if (count == 1) "" else "s"
        return tr(
            ctx,
            "Hide $count pin$pluralEn marked sent on this phone",
            "شاردنەوەی $count نیشانەی نێردراو لەم مۆبایلە",
            "ڤەشارتنا $count نیشانێن هنارتی لسەر ڤێ موبایلێ",
            "إخفاء $count نقطة مُعلمة كمُرسلة على هذا الهاتف"
        )
    }
    fun showingAllPins(ctx: Context) = tr(ctx, "Showing all pins", "پیشاندانی هەموو نیشانەکان", "هەمی نیشان دیارن", "عرض جميع النقاط")
    fun allPins(ctx: Context) = tr(ctx, "All Pins", "هەموو نیشانەکان", "هەمی نیشان", "جميع النقاط")

    // Ruler
    fun clear(ctx: Context) = tr(ctx, "Clear", "سڕینەوە", "پاقژکرن", "مسح")
    fun rulerTapPoint1(ctx: Context) = tr(ctx, "Tap map to drop point 1", "دەست بنێ بە نەخشە بۆ خاڵی ١", "دەست ل نەخشەی بدە بو خالا ١", "انقر على الخريطة لوضع النقطة 1")
    fun rulerTapNext(ctx: Context) = tr(ctx, "Tap next point to measure", "دەست بنێ بە خاڵی دواتر", "دەست ل خالا دی بدە بو پیڤانێ", "انقر على النقطة التالية للقياس")
    fun rulerTotalPoints(ctx: Context, count: Int) = tr(
        ctx,
        "Across $count points",
        "دووری نێوان $count خاڵ",
        "دووراتیا $count خالان",
        "المسافة بين $count نقاط"
    )

    // Field Quick-Select & Undo
    fun markAsDone(ctx: Context) = tr(ctx, "Mark as Done", "نیشانکردن وەک تەواوبوو", "وەک ب دوماهیک هاتی نیشان بکە", "تحديد كمكتمل")
    fun reportFieldIssue(ctx: Context) = tr(ctx, "Report Field Issue", "تۆمارکردنی کێشەی مەیدانی", "تۆمارکرنا ئاریشەیا مەیدانی", "الإبلاغ عن مشكلة ميدانية")
    fun saveDone(ctx: Context) = tr(ctx, "Save Done", "پاشەکەوتکردن", "پاشکەفتکرن", "حفظ الإنجاز")
    fun saveIssue(ctx: Context) = tr(ctx, "Save Issue", "پاشەکەوتی کێشە", "پاشکەفتکرنا ئاریشێ", "حفظ المشكلة")
    fun cancel(ctx: Context) = tr(ctx, "Cancel", "هەڵوەشاندنەوە", "هەلوەشاندن", "إلغاء")
    fun tap(ctx: Context) = tr(ctx, "Tap", "داگرتن", "ب هەلبژێرە", "اختر")
    fun customIssueHint(ctx: Context) = tr(ctx, "Type custom issue details...", "وردەکاری کێشە بنووسە...", "هویرکاریێن ئاریشێ بنڤیسە...", "اكتب تفاصيل المشكلة...")
    fun optionalNoteHint(ctx: Context) = tr(ctx, "Optional completion note...", "تێبینی تەواوبوون (ئارەزوومەندانە)...", "تێبینیا ب دوماهیک هاتنێ (ئارەزوومەندانە)...", "ملاحظة الإنجاز (اختياري)...")
    fun enterIssuePrompt(ctx: Context) = tr(ctx, "Please select or enter the issue", "تکایە کێشەیەک هەڵبژێرە یان بنووسە", "هیڤییە ئاریشەکێ هەلبژێرە یان بنڤیسە", "يرجى اختيار أو كتابة المشكلة")
    fun markedDone(ctx: Context) = tr(ctx, "Marked Done", "وەک تەواوبوو دیاریکرا", "هاتە نیشانکرن: ب دوماهیک هات", "تم التحديد كمكتمل")
    fun markedIssue(ctx: Context) = tr(ctx, "Marked Issue", "وەک کێشە دیاریکرا", "هاتە نیشانکرن: ئاریشە", "تم التحديد كمشكلة")
    fun markedPending(ctx: Context) = tr(ctx, "Marked Pending", "وەک چاوەڕوان دیاریکرا", "هاتە نیشانکرن: چاڤەڕێ", "تم التحديد كقيد الانتظار")
    fun undo(ctx: Context) = tr(ctx, "Undo", "گەڕاندنەوە", "زڤڕاندن", "تراجع")
    fun undone(ctx: Context) = tr(ctx, "Action undone", "دەستکارییەکە گەڕێندرایەوە", "کار هاتە زڤڕاندن", "تم التراجع عن التعديل")

    fun presetLabel(ctx: Context, canonical: String): String = when (canonical) {
        "Meter Linked" -> tr(ctx, canonical, "پێوەر بەستراوە", "پێڤەر هاتە گرێدان", "تم ربط العداد")
        "Inaccessible" -> tr(ctx, canonical, "دەستی پێناگات", "دەست پێ ناگەهیت", "يتعذر الوصول")
        "No Bill" -> tr(ctx, canonical, "پسووڵە نییە", "پسوولە نینە", "لا توجد فاتورة")
        "Customer Refused / No Access" -> tr(ctx, canonical, "بەشداربوو ڕەتیکردەوە / ڕێگە نەدرا", "بەشداربوویی ڕەتکر / دەست پێ نەگەهشت", "رفض المشترك / لا يوجد دخول")
        "Need Electricity" -> tr(ctx, canonical, "پێویستی بە کارەبایە", "پێتڤی ب کارەبێ هەیە", "يحتاج إلى كهرباء")
        "Wrong Location / Coordinates" -> tr(ctx, canonical, "شوێن یان کۆردینەیتی هەڵەیە", "جهـ یان کۆردینەیت خەلەتە", "موقع أو إحداثيات خاطئة")
        "Closed / Nobody Home" -> tr(ctx, canonical, "داخراوە / کەس لە ماڵ نییە", "داخستیە / کەس ل مال نینە", "مغلق / لا يوجد أحد")
        "Safety Hazard / Network Fault" -> tr(ctx, canonical, "مەترسی سەلامەتی / کێشەی تۆڕ", "مەترسیا سلامەتی / خەلەتیا تۆڕێ", "خطر سلامة / عطل في الشبكة")
        else -> canonical
    }

    // Daily Field Report & Edit History
    fun dailyFieldReport(ctx: Context) = tr(ctx, "Daily Field Report", "ڕاپۆرتی ڕۆژانەی مەیدانی", "ڕاپورتا ڕۆژانە یا مەیدانی", "التقرير الميداني اليومي")
    fun visitedProgress(ctx: Context) = tr(ctx, "Visited Progress", "ڕێژەی سەردانکراو", "پێشکەفتنا سەرەدانێ", "نسبة الإنجاز الميداني")
    fun copy(ctx: Context) = tr(ctx, "Copy", "کۆپی", "کۆپی", "نسخ")
    fun share(ctx: Context) = tr(ctx, "Share", "هاوبەشکردن", "پشکدارکرن", "مشاركة")
    fun editHistory(ctx: Context) = tr(ctx, "Edit History", "مێژووی دەستکاری", "مێژوویا گوهارتنان", "سجل التعديلات")
    fun workingHours(ctx: Context) = tr(ctx, "Working Hours", "کاتژمێری کارکردن", "دەمژمێرێن کاری", "ساعات العمل")
    fun change(ctx: Context) = tr(ctx, "Change", "گۆڕین", "گوهارتن", "تغيير")
    fun all(ctx: Context) = tr(ctx, "All", "هەموو", "هەمی", "الكل")
    fun afterHours(ctx: Context) = tr(ctx, "After Hours", "دەرەوەی کار", "دەرڤەی دەمێ کاری", "خارج الدوام")
    fun inHours(ctx: Context) = tr(ctx, "In Hours", "لە کاتی کار", "ل دەمێ کاری", "ضمن الدوام")
    fun totalEdits(ctx: Context) = tr(ctx, "Total Edits", "کۆی گۆڕانکاری", "سەرجەمێ گوهارتنان", "إجمالي التعديلات")
    fun allTeams(ctx: Context) = tr(ctx, "All Teams", "هەموو تیمەکان", "هەمی تیم", "كل الفرق")

    // Offline Maps, Sync & Backup
    fun offlineReady(ctx: Context) = tr(ctx, "Offline Ready", "ئامادەی ئۆفلاین", "ئامادەیا ئۆفلاین", "جاهز دون اتصال")
    fun downloading(ctx: Context) = tr(ctx, "Downloading", "دادەبەزێت", "دهێتە داگرتن", "جارٍ التنزيل")
    fun onlineOnly(ctx: Context) = tr(ctx, "Online Only", "تەنها ئۆنلاین", "بتنێ ئۆنلاین", "عبر الإنترنت فقط")
    fun partial(ctx: Context) = tr(ctx, "Partial", "بەشێکی", "پشکەک", "جزئي")
    fun vectorTiles(ctx: Context) = tr(ctx, "Vector Tiles", "نەخشەی ڤێکتەر", "نەخشەیێ ڤێکتەر", "خرائط متجهة")
    fun roadGraph(ctx: Context) = tr(ctx, "Road Graph", "نەخشەی ڕێگا", "تۆڕا ڕێکان", "شبكة الطرق")
    fun downloadMapAndRoads(ctx: Context) = tr(ctx, "Download Map + Roads", "داگرتنی نەخشە + ڕێگا", "داگرتنا نەخشە + ڕێک", "تنزيل الخريطة والطرق")
    fun pause(ctx: Context) = tr(ctx, "Pause", "وەستان", "ڕاگرتن", "إيقاف مؤقت")
    fun resumeDownload(ctx: Context) = tr(ctx, "Resume Download", "بەردەوامبوون", "بەردەوامبوون", "استئناف التنزيل")
    fun delete(ctx: Context) = tr(ctx, "Delete", "سڕینەوە", "ژێبرن", "حذف")
    fun clearTempCache(ctx: Context) = tr(ctx, "Clear Temporary Map Cache", "سڕینەوەی کاشی کاتی نەخشە", "پاقژکرنا کاشێ بەروەختێ نەخشەی", "مسح ذاكرة الخريطة المؤقتة")
    fun clearTempCacheSub(ctx: Context) = tr(ctx, "Free cached map tiles from online browsing", "بەتاڵکردنی نەخشە کاتییەکانی ئینتەرنێت", "بەتالکرنا نەخشێن کاتی یێن ئینتەرنێتێ", "تفريغ بلاطات الخريطة المخزنة مؤقتاً")
    fun backupLocalFolders(ctx: Context, count: Int) = tr(
        ctx,
        "Backup Local ($count)",
        "پاڵپشتی ناوخۆیی ($count)",
        "باکئەپا ناڤخویی ($count)",
        "نسخ احتياطي محلي ($count)"
    )
    fun noLocalFoldersBackup(ctx: Context) = tr(ctx, "No local folders to back up", "هیچ فۆڵدەرێکی ناوخۆیی نییە بۆ پاڵپشتی", "چ فۆلدەرێن ناڤخویی بو باکئەپێ نینن", "لا توجد مجلدات محلية للنسخ الاحتياطي")
    fun preparingBackup(ctx: Context) = tr(ctx, "Preparing local folders backup ZIP...", "فایلی پاڵپشتی ZIP ئامادە دەکرێت...", "فایلا باکئەپێ ZIP دهێتە ئامادەکرن...", "جارٍ تجهيز النسخة الاحتياطية ZIP...")

    fun cloudSynced(ctx: Context) = tr(ctx, "Cloud Synced", "هاوکاتکراو لەگەڵ هەور", "لگەل عەوری هاتە هەڤدەمکرن", "متزامن مع السحابة")
    fun offlineCache(ctx: Context) = tr(ctx, "Offline Cache", "کاشی ئۆفلاین", "کاشێ ئۆفلاین", "ذاكرة دون اتصال")
    fun pendingUploadCount(ctx: Context, count: Int) = tr(
        ctx,
        "$count waiting for signal",
        "$count چاوەڕوانی ئینتەرنێتە",
        "$count ل هێڤیا ئینتەرنێتێ نە",
        "$count بانتظار الإشارة"
    )
    fun allSynced(ctx: Context) = tr(ctx, "All edits synced", "هەموو گۆڕانکارییەکان نێردران", "هەمی گوهارتن هاتنە هنارتن", "تمت مزامنة جميع التعديلات")
    fun appLanguageTitle(ctx: Context) = tr(ctx, "App Language", "زمانی بەرنامە", "زمانێ ئەپی", "لغة التطبيق")
    fun appLanguageSub(ctx: Context) = tr(ctx, "Interface labels with RTL layout for Kurdish (Sorani/Badini) & Arabic", "ڕووکاری ڕاست-بۆ-چەپ بۆ کوردی (سۆرانی/بادینی) و عەرەبی", "ڕوویێ ڕاست-بو-چەپ بو کوردی (سۆرانی/بادینی) و عەرەبی", "واجهة من اليمين لليسار للكردية (السورانية/البادينية) والعربية")

    // Pin Card, Search & Stacked Localization (#REC-01, #REC-04)
    fun stackedSummary(ctx: Context, count: Int, currentIdx: Int, total: Int) = tr(
        ctx,
        "$count stacked • $currentIdx/$total",
        "$count لەسەریەک • $currentIdx/$total",
        "$count لسەرهەڤ • $currentIdx/$total",
        "$count متراكبة • $currentIdx/$total"
    )
    fun nextShort(ctx: Context) = tr(ctx, "Next →", "دواتر ←", "یێ دی ←", "التالي ←")
    fun distanceAway(ctx: Context, distText: String) = tr(
        ctx,
        "$distText away",
        "$distText دوورە",
        "$distText دوورە",
        "يبعد $distText"
    )
    fun bookmarksCount(ctx: Context, count: Int) = tr(
        ctx,
        "Bookmarks ($count)",
        "نیشانکراوەکان ($count)",
        "نیشانکری ($count)",
        "الإشارات المرجعية ($count)"
    )
    fun searching(ctx: Context) = tr(ctx, "Searching...", "دەگەڕێت...", "گەڕیان...", "جارٍ البحث...")
    fun noPlacesFound(ctx: Context) = tr(ctx, "No places found", "هیچ شوێنێک نەدۆزرایەوە", "چ جهـ نەهاتنە دیتن", "لم يتم العثور على أماكن")
    fun mapPlacesCount(ctx: Context, count: Int) = tr(
        ctx,
        "Map Places ($count)",
        "شوێنەکانی نەخشە ($count)",
        "جهێن نەخشەی ($count)",
        "أماكن الخريطة ($count)"
    )
    fun goToCoordinates(ctx: Context) = tr(ctx, "Go to coordinates", "بڕۆ بۆ کۆردینەیت", "هەرە بو کۆردینەیتان", "الانتقال إلى الإحداثيات")
    fun searchPlaceholder(ctx: Context) = tr(ctx, "Search pins, places, or coords...", "گەڕان بۆ نیشانە، شوێن یان کۆردینەیت...", "ل نیشان، جهـ یان کۆردینەیتان بگەڕە...", "ابحث عن نقاط أو أماكن أو إحداثيات...")
    fun searchHintPrompt(ctx: Context) = tr(
        ctx,
        "Type a bookmark name, status, or lat, lng coordinates — or tap Search Map Places for online locations.",
        "ناوی نیشانە، دۆخ، یان کۆردینەیت بنووسە — یان گەڕان لە نەخشە داگرە بۆ شوێنە ئۆنلاینەکان.",
        "ناڤێ نیشانێ، بارودۆخ یان کۆردینەیتان بنڤیسە — یان ل نەخشەی بگەڕە بو جهێن ئۆنلاین.",
        "اكتب اسم النقطة أو الحالة أو الإحداثيات — أو اضغط البحث في الخريطة للمواقع عبر الإنترنت."
    )
    fun retry(ctx: Context) = tr(ctx, "Retry", "هەوڵدانەوە", "دووبارە هەولبدە", "إعادة المحاولة")
    fun loadingBookmarks(ctx: Context) = tr(ctx, "Loading bookmarks...", "نیشانکراوەکان بار دەکرێت...", "نیشانکری دهێنە بارکرن...", "جارٍ تحميل الإشارات...")
    fun loadingEditHistory(ctx: Context) = tr(ctx, "Loading edit history...", "مێژووی دەستکاری بار دەکرێت...", "مێژوویا گوهارتنان دهێتە بارکرن...", "جارٍ تحميل سجل التعديلات...")
    fun noBookmarksMatchFilter(ctx: Context) = tr(ctx, "No bookmarks match this filter.", "هیچ نیشانەیەک لەگەڵ ئەم پاڵاوتنە ناگونجێت.", "چ نیشان لگەل ڤێ فلتەرێ ناگونجن.", "لا توجد نقاط تطابق هذا التصفية.")

    // Settings Map & Shaders Localization (#REC-01)
    fun mapStyleTitle(ctx: Context) = tr(ctx, "Map Style", "شێوازی نەخشە", "شێوازێ نەخشەی", "نمط الخريطة")
    fun mapStyleSub(ctx: Context) = tr(ctx, "Crisp vector street grid or satellite hybrid imagery", "تۆڕی شەقامی ڤێکتەر یان وێنەی مانگی دەستکرد", "تۆڕا جاددێن ڤێکتەر یان وێنێن سەتەلایت", "شبكة شوارع متجهة أو صور أقمار صناعية")
    fun appearanceThemeTitle(ctx: Context) = tr(ctx, "Appearance Theme", "ڕووکاری ڕەنگ", "ڕوویێ ڕەنگی", "مظهر التطبيق")
    fun appearanceThemeSub(ctx: Context) = tr(ctx, "Automatically adapts map canvas and system bars", "بە شێوەی خۆکار نەخشە و شریتەکان دەگونجێنێت", "ب شێوەیێ ئۆتۆماتیکی نەخشەی دگونجینیت", "يتكيف تلقائياً مع الخريطة وأشرطة النظام")
    fun themeDay(ctx: Context) = tr(ctx, "Day", "ڕۆژ", "ڕۆژ", "نهار")
    fun themeNight(ctx: Context) = tr(ctx, "Night", "شەو", "شەڤ", "ليل")
    fun themeAuto(ctx: Context) = tr(ctx, "Auto", "خۆکار", "ئۆتۆ", "تلقائي")
    fun hillshadeIntensityTitle(ctx: Context) = tr(ctx, "Hillshading Intensity", "بڕی سێبەری شاخەکان", "ڕێژەیا سیبەرا چیایان", "شدة تظليل التضاريس")
    fun hillshadeIntensitySub(ctx: Context) = tr(ctx, "Adjust relief depth for sunlight readability", "ڕێکخستنی قووڵی بەرزی و نزمی بۆ خوێندنەوە لە تیشکی خۆردا", "ڕێکخستنا کووراتیا عەردی بو خویندنا ب ڕۆژ", "اضبط عمق التضاريس لوضوح القراءة تحت الشمس")
    fun mapPinStyleTitle(ctx: Context) = tr(ctx, "Map Pin Style", "شێوازی نیشانەکانی نەخشە", "شێوازێ نیشانێن نەخشەی", "نمط دبابيس الخريطة")
    fun mapPinStyleSub(ctx: Context) = tr(ctx, "Switch instantly between white Concept Droplet rings and Classic Teardrop pins", "گۆڕینی خێرا لە نێوان ئەڵقەی Concept Droplet و نیشانەی کلاسیک", "گوهارتنا بلەز دناڤبەرا Concept Droplet و نیشانێن کلاسیک", "تبديل فوري بين حلقات Concept Droplet والدبابيس الكلاسيكية")
    fun showZoomButtonsTitle(ctx: Context) = tr(ctx, "Show Zoom Buttons", "پیشاندانی دوگمەی نزیککردنەوە", "نیشاندانا دوگمێن نێزیککرنێ", "إظهار أزرار التكبير")
    fun showZoomButtonsSub(ctx: Context) = tr(ctx, "Show slim frosted + / − pill above the right-side map arc (pinch zoom always active)", "پیشاندانی دوگمەی باریکی + / − لەسەر نەخشە (نزیککردنەوە بە دوو پەنجە هەمیشە چالاکە)", "نیشاندانا + / − لسەر نەخشەی (نێزیککرن ب دوو تبلان هەردەم کارا یە)", "إظهار زر + / − فوق قوس الخريطة الأيمن")
    fun mapScaleBarTitle(ctx: Context) = tr(ctx, "Map Scale Bar", "پێوەری دووری نەخشە", "پیڤەرێ دووراتیا نەخشەی", "شريط مقياس الخريطة")
    fun mapScaleBarSub(ctx: Context) = tr(ctx, "Metric (m / km) or imperial distance reference on canvas", "پێوانەی دووری مەتری (م / کم) یان ئیمپریال لەسەر نەخشە", "پیڤەرێ دووراتیا مەتری (م / کم) یان ئیمپریال لسەر نەخشەی", "مرجع المسافة المتري (م / كم) أو الإمبراطوري")
    fun clusterDensePinsTitle(ctx: Context) = tr(ctx, "Cluster Dense Pins", "کۆکردنەوەی نیشانە چڕەکان", "کومکرنا نیشانێن چڕ", "تجميع النقاط المتقاربة")
    fun clusterDensePinsSub(ctx: Context) = tr(ctx, "Group nearby pins into count badges when zoomed out for clearer overview panning", "کۆکردنەوەی نیشانە نزیکەکان لە کاتی دوورخستنەوەی نەخشە بۆ بینینێکی ڕوونتر", "کومکرنا نیشانێن نێزیک دەمێ دوورخستنێ بو دیتنەکا پاقژتر", "تجميع النقاط المتقاربة في شارات عدد عند التصغير")

    // About & Sync Status Localization (#REC-24)
    fun aboutTagline(ctx: Context) = tr(
        ctx,
        "Minimalist field mapping built with Organic Maps-inspired cartography: shaded relief, elevation contours, multilingual English/Kurdish/Arabic labels, Valhalla + embedded offline turn-by-turn routing, status-preserving KML/KMZ round-tripping, Firebase sync, and Android Auto support.",
        "نەخشەکێشانی مەیدانی سادە و خێرا: سێبەری شاخەکان، هێڵەکانی بەرزی، ناوی فرەزمانی کوردی/عەرەبی/ئینگلیزی، ڕێنمایی ئۆفلاین و ئۆنلاین، هاوردە و هەناردەی KML/KMZ، هاوکاتکردنی هەور و پشتگیری Android Auto.",
        "نەخشەکێشانا مەیدانی یا سادە و بلەز: سیبەرا چیایان، هێلێن بلنداهیێ، ناڤێن فرەزمان، ڕێنیشاندانا ئۆفلاین و ئۆنلاین، KML/KMZ، هەڤدەمکرنا عەوری و Android Auto.",
        "خرائط ميدانية خفيفة بدقة عالية: تظليل التضاريس، خطوط الكنتور، تسميات متعددة اللغات، توجيه دون اتصال، استيراد وتصدير KML/KMZ، مزامنة سحابية ودعم Android Auto."
    )
    fun aboutDeveloperHeader(ctx: Context) = tr(ctx, "DEVELOPER & DIAGNOSTICS", "گەشەپێدەر و زانیاری", "گەشەپێدەر و زانیاری", "المطور والتشخيص")
    fun aboutCloudLive(ctx: Context) = tr(ctx, "Cloud live", "هەور چالاکە", "عەور کارایە", "السحابة متصلة")
    fun aboutOfflineCache(ctx: Context) = tr(ctx, "Offline cache", "کاشی ئۆفلاین", "کاشێ ئۆفلاین", "ذاكرة دون اتصال")
    fun aboutGithubProfile(ctx: Context) = tr(ctx, "GitHub Profile", "هەژماری GitHub", "هەژمارا GitHub", "ملف GitHub")
    fun aboutRepoTitle(ctx: Context) = tr(ctx, "TerraSync Repository", "کۆگای سەرچاوەی TerraSync", "کۆگەها کۆدێ TerraSync", "مستودع TerraSync")
    fun aboutRepoSub(ctx: Context) = tr(ctx, "Source code & issue tracker", "کۆدی سەرچاوە و تۆماری کێشەکان", "کۆدێ ژێدەری و کێشە", "الكود المصدري ومتتبع المشكلات")
    fun aboutReleasesTitle(ctx: Context) = tr(ctx, "Releases & Updates", "وەشان و نوێکارییەکان", "وەشان و نویکرن", "الإصدارات والتحديثات")
    fun aboutReleasesSub(ctx: Context) = tr(ctx, "Latest signed APK builds", "نوێترین وەشانە واژووکراوەکانی APK", "نویترین وەشانێن APK یێن ئیمزاکری", "أحدث ملفات APK الموقعة")
}
