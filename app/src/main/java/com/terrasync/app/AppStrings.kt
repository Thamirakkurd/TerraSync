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
}
