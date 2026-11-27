package com.terrasync.app

/**
 * ICU-aware Kurdish (Sorani) Arabic shaper for MapLibre Native.
 *
 * MapLibre Native's vendored ICU 61.1 `u_shapeArabic` (vendor/icu/src/ushape.cpp) has two
 * critical behaviors that break Kurdish Sorani labels:
 *  1. Its pre-loop (`shapeUnicode` lines 1213-1226) converts Presentation Forms in
 *     `convertFBto06` / `convertFEto06` BACK to `U+06xx` before re-shaping — so emitting
 *     `U+FEEA` for Kurdish `ە` caused ICU to convert `U+FEEA` -> `U+0647` (dual-joining `ه`)
 *     and join it to the FOLLOWING letter as medial `ـهـ`!
 *  2. Its `araLink` table only covers `U+0622..U+06D3` (so `ە` `U+06D5` is non-joining) and
 *     lacks Presentation Form mappings for `ێ` (`U+06CE`), `ڵ` (`U+06B5`), `ڕ` (`U+0695`),
 *     and `ڤ` (`U+06A4`).
 *
 * Solution that cooperates WITH ICU `u_shapeArabic` & `ubidi_setPara`:
 *  - Keep all standard Arabic/Kurdish letters ICU already shapes (`0621..064A`, `پ چ ژ ک گ ۆ ی ھ`)
 *    in logical `U+06xx` so ICU handles them & Lam-Alef ligatures natively.
 *  - Rewrite `ە` (`U+06D5`, right-joining only) to `\u0647\u061C` (`ه` + Arabic Letter Mark `ALM`,
 *    which is 0x0 invisible, `bidi=AL`, and `getLink=0`): ICU joins `ه` to the preceding letter
 *    as final `ـە` (`U+FEEA`) or isolated `ە` (`U+FEE9`), and NEVER joins it to the next letter!
 *  - Rewrite `ێ`, `ڵ`, `ڤ`, `ڕ` to assigned `bidi=AL` slots inside `64256-64511.pbf` that ALL
 *    have `convertFBto06 == 0` in ICU (`U+FB5A..FB5D`, `U+FB62..FB65`, `U+FB6A..FB6D`, `U+FB6E..FB6F`),
 *    flanked on their joined sides by `ZWJ` (`\u200D`, `getLink=3`, zeroed to 0x0 invisible in our
 *    bundled `8192-8447.pbf`) so neighboring `U+06xx` letters join seamlessly!
 */
object KurdishArabicShaper {

    private const val ALM = '\u061C' // Arabic Letter Mark: bidi=AL, getLink=0, not in PBF (0x0 invisible)
    private const val ZWJ = '\u200D' // Zero Width Joiner: getLink=3, zeroed to 0x0 in bundled 8192-8447.pbf

    // All slots below have bidi=AL, cat=Lo, convertFBto06==0, presALink==0 in MapLibre ICU 61.1
    const val YEH_SMALL_V_ISOL = '\uFB5A' // ێ
    const val YEH_SMALL_V_FINA = '\uFB5B' // ـێ
    const val YEH_SMALL_V_INIT = '\uFB5C' // ێـ
    const val YEH_SMALL_V_MEDI = '\uFB5D' // ـێـ

    const val LAM_SMALL_V_ISOL = '\uFB62' // ڵ
    const val LAM_SMALL_V_FINA = '\uFB63' // ـڵ
    const val LAM_SMALL_V_INIT = '\uFB64' // ڵـ
    const val LAM_SMALL_V_MEDI = '\uFB65' // ـڵـ

    const val VEH_ISOL = '\uFB6A'         // ڤ (already in OpenFreeMap 64256-64511.pbf)
    const val VEH_FINA = '\uFB6B'         // ـڤ
    const val VEH_INIT = '\uFB6C'         // ڤـ
    const val VEH_MEDI = '\uFB6D'         // ـڤـ

    const val REH_SMALL_V_ISOL = '\uFB6E' // ڕ
    const val REH_SMALL_V_FINA = '\uFB6F' // ـڕ

    private fun isTransparent(c: Char): Boolean {
        val cp = c.code
        return (cp in 0x064B..0x065F) || cp == 0x0670 || cp == 0x0610 || (cp in 0x06D6..0x06ED)
    }

    private fun isDualJoining(c: Char): Boolean = when (c) {
        '\u0626', '\u0628', '\u062A', '\u062B', '\u062C', '\u062D', '\u062E',
        '\u0633', '\u0634', '\u0635', '\u0636', '\u0637', '\u0638', '\u0639', '\u063A',
        '\u0640', '\u0641', '\u0642', '\u0643', '\u0644', '\u0645', '\u0646', '\u0647',
        '\u0649', '\u064A', '\u067E', '\u0686', '\u06A4', '\u06A9', '\u06AF', '\u06B5',
        '\u06BE', '\u06CC', '\u06CE', '\u200D' -> true
        else -> false
    }

    private fun isRightJoining(c: Char): Boolean = isDualJoining(c) || when (c) {
        '\u0622', '\u0623', '\u0624', '\u0625', '\u0627', '\u0629',
        '\u062F', '\u0630', '\u0631', '\u0632', '\u0648',
        '\u0695', '\u0698', '\u06C6', '\u06D5' -> true
        else -> false
    }

    private fun isCustomSlotSource(c: Char): Boolean =
        c == '\u06CE' || c == '\u06B5' || c == '\u06A4' || c == '\u0695'

    fun needsKurdishShaping(text: String): Boolean {
        for (i in text.indices) {
            when (text[i]) {
                '\u06D5', '\u06CE', '\u06B5', '\u0695', '\u06A4' -> return true
            }
        }
        return false
    }

    fun shape(text: String): String {
        if (!needsKurdishShaping(text)) return text
        val n = text.length
        val out = StringBuilder(n + 8)

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

        for (i in 0 until n) {
            val ch = text[i]
            if (ch == '\u06D5') {
                // Kurdish AE (ە): emit Arabic Heh (ه) + invisible ALM (bidi=AL, getLink=0).
                // ICU joins ه to the preceding letter (U+FEEA final / U+FEE9 isolated) and
                // ALM prevents it from ever joining to the following letter.
                out.append('\u0647')
                out.append(ALM)
                continue
            }

            if (!isCustomSlotSource(ch)) {
                out.append(ch)
                continue
            }

            val prev = prevChar(i)
            val next = nextChar(i)
            val joinPrev = isDualJoining(prev)
            val joinNext = (ch != '\u0695') && isRightJoining(next)

            // Emit ZWJ before custom slot only when preceding char is a standard ICU-shaped letter
            // (so ICU joins it to our custom slot; avoid duplicate ZWJ between two custom slots)
            if (joinPrev && !isCustomSlotSource(prev) && prev != ZWJ) {
                out.append(ZWJ)
            }

            val glyph = when (ch) {
                '\u06CE' -> when {
                    joinPrev && joinNext -> YEH_SMALL_V_MEDI
                    joinPrev -> YEH_SMALL_V_FINA
                    joinNext -> YEH_SMALL_V_INIT
                    else -> YEH_SMALL_V_ISOL
                }
                '\u06B5' -> when {
                    joinPrev && joinNext -> LAM_SMALL_V_MEDI
                    joinPrev -> LAM_SMALL_V_FINA
                    joinNext -> LAM_SMALL_V_INIT
                    else -> LAM_SMALL_V_ISOL
                }
                '\u06A4' -> when {
                    joinPrev && joinNext -> VEH_MEDI
                    joinPrev -> VEH_FINA
                    joinNext -> VEH_INIT
                    else -> VEH_ISOL
                }
                else -> if (joinPrev) REH_SMALL_V_FINA else REH_SMALL_V_ISOL // '\u0695' (ڕ)
            }
            out.append(glyph)

            // Emit ZWJ after custom slot when next char is a standard ICU-shaped letter
            if (joinNext && !isCustomSlotSource(next) && next != ZWJ) {
                out.append(ZWJ)
            }
        }
        return out.toString()
    }

    /**
     * Exact lossless inverse of [shape] for Place Cards, search, and clipboard copy.
     */
    fun unshape(text: String): String {
        var needsClean = false
        for (i in text.indices) {
            val c = text[i]
            if (c == ALM || c == ZWJ || c in '\uFB5A'..'\uFB6F') {
                needsClean = true
                break
            }
        }
        if (!needsClean) return text

        val n = text.length
        val sb = StringBuilder(n)
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '\u0647' && i + 1 < n && text[i + 1] == ALM) {
                sb.append('\u06D5')
                i += 2
                continue
            }
            when (c) {
                ALM, ZWJ -> {}
                YEH_SMALL_V_ISOL, YEH_SMALL_V_FINA, YEH_SMALL_V_INIT, YEH_SMALL_V_MEDI -> sb.append('\u06CE')
                LAM_SMALL_V_ISOL, LAM_SMALL_V_FINA, LAM_SMALL_V_INIT, LAM_SMALL_V_MEDI -> sb.append('\u06B5')
                VEH_ISOL, VEH_FINA, VEH_INIT, VEH_MEDI -> sb.append('\u06A4')
                REH_SMALL_V_ISOL, REH_SMALL_V_FINA -> sb.append('\u0695')
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
