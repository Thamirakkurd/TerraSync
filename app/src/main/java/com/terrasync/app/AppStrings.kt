package com.terrasync.app

import android.content.Context

/**
 * Lightweight, zero-overhead UI localization supporting English, Kurdish (Sorani),
 * and Arabic with consistent utilitarian Organic Maps phrasing.
 */
object AppStrings {

    fun isRtl(ctx: Context): Boolean = DevicePrefs.getAppLanguage(ctx).isRtl

    fun tr(ctx: Context, en: String, ku: String, ar: String): String {
        return when (DevicePrefs.getAppLanguage(ctx)) {
            AppLanguage.KURDISH -> ku
            AppLanguage.ARABIC -> ar
            AppLanguage.ENGLISH -> en
        }
    }

    fun search(ctx: Context) = tr(ctx, "Search", "گەڕان", "بحث")
    fun bookmarks(ctx: Context) = tr(ctx, "Bookmarks", "نیشانکراوەکان", "الإشارات المرجعية")
    fun settings(ctx: Context) = tr(ctx, "Settings", "ڕێکخستنەکان", "الإعدادات")
    fun about(ctx: Context) = tr(ctx, "About TerraSync", "دەربارەی TerraSync", "حول TerraSync")

    // Settings Sections
    fun secMapShaders(ctx: Context) = tr(ctx, "Map & Shaders", "نەخشە و شێدەرەکان", "الخريطة والتضاريس")
    fun secLanguageLabels(ctx: Context) = tr(ctx, "Language & Labels", "زمان و ناوەکان", "اللغة والتسميات")
    fun secNavigationRouting(ctx: Context) = tr(ctx, "Navigation & Routing", "ڕێنمایی و ڕێگا", "الملاحة والتوجيه")
    fun secFieldDataSync(ctx: Context) = tr(ctx, "Field Data & Sync", "داتای مەیدانی و هاوکاتکردن", "البيانات الميدانية والمزامنة")
    fun secOfflineCache(ctx: Context) = tr(ctx, "Offline Maps & Cache", "نەخشەی بێ ئینتەرنێت", "الخرائط دون اتصال والذاكرة")
    fun secAboutBuild(ctx: Context) = tr(ctx, "About & Build", "دەربارە و وەشان", "حول التطبيق والبناء")

    // Common UI labels
    fun routeTo(ctx: Context) = tr(ctx, "Route To", "ڕێگا بۆ ئێرە", "المسار إلى هنا")
    fun routeFrom(ctx: Context) = tr(ctx, "Route From", "ڕێگا لە ئێرەوە", "المسار من هنا")
    fun startNav(ctx: Context) = tr(ctx, "Start Navigation", "دەستپێکردنی ڕێنمایی", "بدء الملاحة")
    fun edit(ctx: Context) = tr(ctx, "Edit", "دەستکاری", "تعديل")
    fun external(ctx: Context) = tr(ctx, "External", "دەرەکی", "خارجي")
    fun savePin(ctx: Context) = tr(ctx, "Save Pin", "پاشەکەوتکردن", "حفظ النقطة")
    fun copyGps(ctx: Context) = tr(ctx, "Copy GPS", "کۆپیکردنی GPS", "نسخ الإحداثيات")
    fun pending(ctx: Context) = tr(ctx, "Pending", "چاوەڕوان", "قيد الانتظار")
    fun done(ctx: Context) = tr(ctx, "Done", "تەواوبوو", "مكتمل")
    fun issue(ctx: Context) = tr(ctx, "Issue", "کێشە", "مشكلة")
    fun showAllBookmarks(ctx: Context) = tr(ctx, "Show all bookmarks on map", "پیشاندانی هەموو نیشانکراوەکان", "إظهار جميع الإشارات على الخريطة")
    fun showBookmarkTitles(ctx: Context) = tr(ctx, "Show bookmark titles", "پیشاندانی ناوی نیشانکراوەکان", "إظهار أسماء الإشارات")
    fun searchMapPlaces(ctx: Context) = tr(ctx, "Search Map Places", "گەڕان لە نەخشە", "البحث في الخريطة")
    fun nearestPending(ctx: Context) = tr(ctx, "Nearest Pending", "نزیکترین چاوەڕوان", "أقرب نقطة معلقة")
    fun zoom(ctx: Context) = tr(ctx, "Zoom", "نزیککردنەوە", "تكبير")
}
