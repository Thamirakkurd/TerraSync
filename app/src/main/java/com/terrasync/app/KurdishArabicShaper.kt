package com.terrasync.app

/**
 * Contextual Arabic/Kurdish (Sorani) Presentation-Form shaper for MapLibre Native.
 *
 * MapLibre Native's vendored ICU u_shapeArabic (0x0622..0x06D3) fails to join Kurdish Sorani
 * letters:
 *   - ە (U+06D5): outside 0x0622..0x06D3 so ICU breaks the join with the preceding letter
 *   - ێ (U+06CE), ڵ (U+06B5), ڕ (U+0695): no standard Unicode Presentation Forms
 *   - ڤ (U+06A4): missing presentation-form mapping in ICU 61.1
 * Meanwhile MapLibre Native's HarfBuzz "font-faces" shaper hardcodes HB_DIRECTION_LTR and skips
 * BiDi (maplibre-native#4513), reversing RTL text.
 *
 * By pre-shaping Kurdish runs into Arabic Presentation Forms (U+FB50..FEFC, which ICU leaves
 * untouched and ubidi reorders cleanly as Strong AL RTL) and mapping ێ/ڵ/ڕ to 10 assigned AL
 * slots in 64256-64511.pbf (U+FB5A..U+FB63) supplied by assets/fonts/64256-64511.pbf, every
 * Kurdish word joins seamlessly in true RTL visual order across roads, places, POIs, and pins.
 */
object KurdishArabicShaper {

    // Assigned Arabic Presentation Forms-A (bidi=AL, ICU presALink=0) slots in 64256-64511.pbf
    const val YEH_SMALL_V_ISOL = '\uFB5A' // ێ
    const val YEH_SMALL_V_FINA = '\uFB5B' // ـێ
    const val YEH_SMALL_V_INIT = '\uFB5C' // ێـ
    const val YEH_SMALL_V_MEDI = '\uFB5D' // ـێـ

    const val LAM_SMALL_V_ISOL = '\uFB5E' // ڵ
    const val LAM_SMALL_V_FINA = '\uFB5F' // ـڵ
    const val LAM_SMALL_V_INIT = '\uFB60' // ڵـ
    const val LAM_SMALL_V_MEDI = '\uFB61' // ـڵـ

    const val REH_SMALL_V_ISOL = '\uFB62' // ڕ
    const val REH_SMALL_V_FINA = '\uFB63' // ـڕ

    private class Forms(
        val isol: Char,
        val fina: Char,
        val init: Char = '\u0000',
        val medi: Char = '\u0000'
    ) {
        val joinsRight: Boolean get() = fina != '\u0000'
        val joinsLeft: Boolean get() = init != '\u0000' && medi != '\u0000'
    }

    private val FORMS: HashMap<Char, Forms> = hashMapOf(
        // Standard Arabic (U+0621..U+064A)
        '\u0621' to Forms('\uFE80', '\u0000'), // ء
        '\u0622' to Forms('\uFE81', '\uFE82'), // آ
        '\u0623' to Forms('\uFE83', '\uFE84'), // أ
        '\u0624' to Forms('\uFE85', '\uFE86'), // ؤ
        '\u0625' to Forms('\uFE87', '\uFE88'), // إ
        '\u0626' to Forms('\uFE89', '\uFE8A', '\uFE8B', '\uFE8C'), // ئ
        '\u0627' to Forms('\uFE8D', '\uFE8E'), // ا
        '\u0628' to Forms('\uFE8F', '\uFE90', '\uFE91', '\uFE92'), // ب
        '\u0629' to Forms('\uFE93', '\uFE94'), // ة
        '\u062A' to Forms('\uFE95', '\uFE96', '\uFE97', '\uFE98'), // ت
        '\u062B' to Forms('\uFE99', '\uFE9A', '\uFE9B', '\uFE9C'), // ث
        '\u062C' to Forms('\uFE9D', '\uFE9E', '\uFE9F', '\uFEA0'), // ج
        '\u062D' to Forms('\uFEA1', '\uFEA2', '\uFEA3', '\uFEA4'), // ح
        '\u062E' to Forms('\uFEA5', '\uFEA6', '\uFEA7', '\uFEA8'), // خ
        '\u062F' to Forms('\uFEA9', '\uFEAA'), // د
        '\u0630' to Forms('\uFEAB', '\uFEAC'), // ذ
        '\u0631' to Forms('\uFEAD', '\uFEAE'), // ر
        '\u0632' to Forms('\uFEAF', '\uFEB0'), // ز
        '\u0633' to Forms('\uFEB1', '\uFEB2', '\uFEB3', '\uFEB4'), // س
        '\u0634' to Forms('\uFEB5', '\uFEB6', '\uFEB7', '\uFEB8'), // ش
        '\u0635' to Forms('\uFEB9', '\uFEBA', '\uFEBB', '\uFEBC'), // ص
        '\u0636' to Forms('\uFEBD', '\uFEBE', '\uFEBF', '\uFEC0'), // ض
        '\u0637' to Forms('\uFEC1', '\uFEC2', '\uFEC3', '\uFEC4'), // ط
        '\u0638' to Forms('\uFEC5', '\uFEC6', '\uFEC7', '\uFEC8'), // ظ
        '\u0639' to Forms('\uFEC9', '\uFECA', '\uFECB', '\uFECC'), // ع
        '\u063A' to Forms('\uFECD', '\uFECE', '\uFECF', '\uFED0'), // غ
        '\u0640' to Forms('\u0640', '\u0640', '\u0640', '\u0640'), // ـ tatweel
        '\u0641' to Forms('\uFED1', '\uFED2', '\uFED3', '\uFED4'), // ف
        '\u0642' to Forms('\uFED5', '\uFED6', '\uFED7', '\uFED8'), // ق
        '\u0643' to Forms('\uFED9', '\uFEDA', '\uFEDB', '\uFEDC'), // ك
        '\u0644' to Forms('\uFEDD', '\uFEDE', '\uFEDF', '\uFEE0'), // ل
        '\u0645' to Forms('\uFEE1', '\uFEE2', '\uFEE3', '\uFEE4'), // م
        '\u0646' to Forms('\uFEE5', '\uFEE6', '\uFEE7', '\uFEE8'), // ن
        '\u0647' to Forms('\uFEE9', '\uFEEA', '\uFEEB', '\uFEEC'), // ه
        '\u0648' to Forms('\uFEED', '\uFEEE'), // و
        '\u0649' to Forms('\uFEEF', '\uFEF0'), // ى
        '\u064A' to Forms('\uFEF1', '\uFEF2', '\uFEF3', '\uFEF4'), // ي

        // Persian & Kurdish Sorani extensions
        '\u067E' to Forms('\uFB56', '\uFB57', '\uFB58', '\uFB59'), // پ
        '\u0686' to Forms('\uFB7A', '\uFB7B', '\uFB7C', '\uFB7D'), // چ
        '\u0695' to Forms(REH_SMALL_V_ISOL, REH_SMALL_V_FINA),     // ڕ
        '\u0698' to Forms('\uFB8A', '\uFB8B'),                     // ژ
        '\u06A4' to Forms('\uFB6A', '\uFB6B', '\uFB6C', '\uFB6D'), // ڤ
        '\u06A9' to Forms('\uFB8E', '\uFB8F', '\uFB90', '\uFB91'), // ک
        '\u06AF' to Forms('\uFB92', '\uFB93', '\uFB94', '\uFB95'), // گ
        '\u06B5' to Forms(LAM_SMALL_V_ISOL, LAM_SMALL_V_FINA, LAM_SMALL_V_INIT, LAM_SMALL_V_MEDI), // ڵ
        '\u06BE' to Forms('\uFBAA', '\uFBAB', '\uFBAC', '\uFBAD'), // ھ
        '\u06C6' to Forms('\uFBD9', '\uFBDA'),                     // ۆ
        '\u06CC' to Forms('\uFBFC', '\uFBFD', '\uFBFE', '\uFBFF'), // ی
        '\u06CE' to Forms(YEH_SMALL_V_ISOL, YEH_SMALL_V_FINA, YEH_SMALL_V_INIT, YEH_SMALL_V_MEDI), // ێ
        '\u06D5' to Forms('\uFEE9', '\uFEEA')                      // ە (Kurdish AE, right-joining only)
    )

    private val UNSHAPE_MAP: HashMap<Char, String> = HashMap<Char, String>(180).apply {
        for ((base, f) in FORMS) {
            if (base == '\u0640' || base == '\u06D5') continue
            val s = base.toString()
            put(f.isol, s)
            if (f.fina != '\u0000') put(f.fina, s)
            if (f.init != '\u0000') put(f.init, s)
            if (f.medi != '\u0000') put(f.medi, s)
        }
        // Lam-Alef ligatures
        put('\uFEF5', "\u0644\u0622")
        put('\uFEF6', "\u0644\u0622")
        put('\uFEF7', "\u0644\u0623")
        put('\uFEF8', "\u0644\u0623")
        put('\uFEF9', "\u0644\u0625")
        put('\uFEFA', "\u0644\u0625")
        put('\uFEFB', "\u0644\u0627")
        put('\uFEFC', "\u0644\u0627")
    }

    private fun isTransparent(c: Char): Boolean {
        val cp = c.code
        return (cp in 0x064B..0x065F) || cp == 0x0670 || cp == 0x0610 || (cp in 0x06D6..0x06ED)
    }

    fun needsKurdishShaping(text: String): Boolean {
        for (i in text.indices) {
            when (text[i]) {
                '\u06D5', '\u06CE', '\u06B5', '\u0695', '\u06A4' -> return true
            }
        }
        return false
    }

    private fun lamAlefLigature(alef: Char, joinedRight: Boolean): Char = when (alef) {
        '\u0622' -> if (joinedRight) '\uFEF6' else '\uFEF5'
        '\u0623' -> if (joinedRight) '\uFEF8' else '\uFEF7'
        '\u0625' -> if (joinedRight) '\uFEFA' else '\uFEF9'
        '\u0627' -> if (joinedRight) '\uFEFC' else '\uFEFB'
        else -> '\u0000'
    }

    /**
     * Shapes Kurdish/Arabic runs in [text] into contextual Arabic Presentation Forms
     * (including our bundled Kurdish ێ/ڵ/ڕ glyphs at U+FB5A..U+FB63).
     * Non-Kurdish text is returned unchanged with zero allocation.
     */
    fun shape(text: String): String {
        if (!needsKurdishShaping(text)) return text
        val n = text.length
        val out = StringBuilder(n)

        fun prevChar(fromIdx: Int): Char {
            var j = fromIdx - 1
            while (j >= 0 && isTransparent(text[j])) j--
            return if (j >= 0) text[j] else '\u0000'
        }

        fun nextChar(fromIdx: Int): Char {
            var j = fromIdx + 1
            while (j < n && isTransparent(text[j])) j++
            return if (j < n) text[j] else '\u0000'
        }

        fun canJoinLeft(c: Char): Boolean {
            if (c == '\u200D') return true
            return FORMS[c]?.joinsLeft == true
        }

        fun canJoinRight(c: Char): Boolean {
            if (c == '\u200D') return true
            return FORMS[c]?.joinsRight == true
        }

        var i = 0
        while (i < n) {
            val ch = text[i]
            val forms = FORMS[ch]
            if (forms == null) {
                out.append(ch)
                i++
                continue
            }

            val prev = prevChar(i)
            val joinPrev = forms.joinsRight && canJoinLeft(prev)

            // Handle mandatory Arabic Lam-Alef ligature (ل + ا/أ/إ/آ)
            if (ch == '\u0644') {
                var nextIdx = i + 1
                while (nextIdx < n && isTransparent(text[nextIdx])) nextIdx++
                if (nextIdx < n) {
                    val lig = lamAlefLigature(text[nextIdx], joinPrev)
                    if (lig != '\u0000') {
                        out.append(lig)
                        // Preserve any intermediate transparent marks after ligature
                        for (m in (i + 1) until nextIdx) out.append(text[m])
                        i = nextIdx + 1
                        continue
                    }
                }
            }

            val next = nextChar(i)
            val joinNext = forms.joinsLeft && canJoinRight(next)

            val shaped = when {
                joinPrev && joinNext -> forms.medi
                joinPrev -> forms.fina
                joinNext -> forms.init
                else -> forms.isol
            }
            out.append(shaped)
            i++
        }
        return out.toString()
    }

    /**
     * Restores Presentation Forms (including U+FB5A..U+FB63 Kurdish slots) back to standard
     * logical Unicode for UI display, search, and clipboard copy when inspecting tapped features.
     */
    fun unshape(text: String): String {
        var hasPresForm = false
        for (i in text.indices) {
            val cp = text[i].code
            if (cp in 0xFB50..0xFEFC) {
                hasPresForm = true
                break
            }
        }
        if (!hasPresForm) return text
        val sb = StringBuilder(text.length)
        for (i in text.indices) {
            val ch = text[i]
            if (ch == '\uFEE9' || ch == '\uFEEA') {
                // Distinguish Kurdish AE (ە) vs Arabic Heh (ه) when text uses Kurdish letters
                sb.append(if (text.any { it in '\uFB5A'..'\uFB63' || it in '\uFBD9'..'\uFBDA' || it in '\uFB8E'..'\uFB95' }) '\u06D5' else '\u0647')
            } else {
                val mapped = UNSHAPE_MAP[ch]
                if (mapped != null) sb.append(mapped) else sb.append(ch)
            }
        }
        return sb.toString()
    }
}
