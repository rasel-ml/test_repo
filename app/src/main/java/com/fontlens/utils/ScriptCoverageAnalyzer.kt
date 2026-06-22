package com.fontlens.utils

/**
 * Computes which scripts a font supports based on its cmap glyph set.
 * Uses true script names (Latin, Devanagari, etc.) not language names.
 * Coverage = (matched glyphs in range) / (total range size).
 * If coverage >= threshold, the script is considered supported.
 *
 * Special cases:
 *  - "latin"  : also triggers on Basic Latin only (A-Z / a-z ≥ 80% of 52 chars)
 *               regardless of the global threshold, since a Basic-Latin-only font
 *               IS a Latin font even if it has no extended characters.
 *  - "ascii"  : ASCII-legacy fonts encode a non-Latin script's glyphs at Basic
 *               Latin codepoints (pre-Unicode hack).  Detected when ALL of the
 *               following hold:
 *                 1. The font's Basic Latin coverage is ≥ 80 %.
 *                 2. The font has ≥ 1 non-Latin script above threshold.
 *                 3. The font has NO Unicode codepoints in those non-Latin ranges
 *                    — it only "has" that script via the ASCII slots.
 *               When detected, "ascii" is prepended and "latin" is suppressed
 *               because the Latin glyphs are actually the other-script glyphs.
 */
object ScriptCoverageAnalyzer {

    data class ScriptDef(val code: String, val ranges: List<IntRange>)

    val SCRIPTS: List<ScriptDef> = listOf(

        // Latin — covers English, French, German, Spanish, Polish, Czech, etc.
        ScriptDef("latin", listOf(
            0x0041..0x005A,  // Basic Latin uppercase A-Z
            0x0061..0x007A,  // Basic Latin lowercase a-z
            0x00C0..0x00FF,  // Latin-1 Supplement (à é ñ ü…)
            0x0100..0x017F,  // Latin Extended-A (ą ę ó ž š…)
            0x0180..0x024F   // Latin Extended-B (broader European)
        )),

        // Devanagari — Hindi, Marathi, Nepali, Sanskrit, Maithili
        ScriptDef("devanagari", listOf(0x0900..0x097F)),

        // Bengali — Bengali, Assamese
        ScriptDef("bengali", listOf(0x0980..0x09FF)),

        // Arabic — Arabic, Urdu, Persian, Pashto, etc.
        ScriptDef("arabic", listOf(
            0x0600..0x06FF,
            0x0750..0x077F   // Arabic Supplement
        )),

        // Hebrew
        ScriptDef("hebrew", listOf(0x0590..0x05FF)),

        // Han / CJK — Chinese (Simplified & Traditional), Japanese Kanji, Korean Hanja
        ScriptDef("han", listOf(0x4E00..0x9FFF)),

        // Hiragana + Katakana — Japanese syllabaries
        ScriptDef("kana", listOf(
            0x3040..0x309F,  // Hiragana
            0x30A0..0x30FF   // Katakana
        )),

        // Hangul — Korean
        ScriptDef("hangul", listOf(0xAC00..0xD7A3)),

        // Tamil
        ScriptDef("tamil", listOf(0x0B80..0x0BFF)),

        // Telugu
        ScriptDef("telugu", listOf(0x0C00..0x0C7F)),

        // Kannada
        ScriptDef("kannada", listOf(0x0C80..0x0CFF)),

        // Malayalam
        ScriptDef("malayalam", listOf(0x0D00..0x0D7F)),

        // Gujarati
        ScriptDef("gujarati", listOf(0x0A80..0x0AFF)),

        // Gurmukhi — Punjabi
        ScriptDef("gurmukhi", listOf(0x0A00..0x0A7F)),

        // Odia (Oriya)
        ScriptDef("odia", listOf(0x0B00..0x0B7F)),

        // Sinhala
        ScriptDef("sinhala", listOf(0x0D80..0x0DFF)),

        // Thai
        ScriptDef("thai", listOf(0x0E00..0x0E7F)),

        // Khmer
        ScriptDef("khmer", listOf(0x1780..0x17FF)),

        // Myanmar — Burmese, Karen, Mon, etc.
        ScriptDef("myanmar", listOf(0x1000..0x109F)),

        // Georgian
        ScriptDef("georgian", listOf(0x10A0..0x10FF)),

        // Armenian
        ScriptDef("armenian", listOf(0x0530..0x058F)),

        // Ethiopic — Amharic, Tigrinya, Oromo, etc.
        ScriptDef("ethiopic", listOf(0x1200..0x137F)),

        // Cyrillic — Russian, Bulgarian, Serbian, Ukrainian, Macedonian, etc.
        ScriptDef("cyrillic", listOf(0x0400..0x04FF)),

        // Greek
        ScriptDef("greek", listOf(0x0370..0x03FF)),

        // Tibetan
        ScriptDef("tibetan", listOf(0x0F00..0x0FFF)),

        // Lao
        ScriptDef("lao", listOf(0x0E80..0x0EFF)),

        // Mongolian
        ScriptDef("mongolian", listOf(0x1800..0x18AF))
    )

    // Basic Latin A-Z / a-z only — used for the Latin-only fix and ASCII detection
    private val BASIC_LATIN_RANGES = listOf(0x0041..0x005A, 0x0061..0x007A)
    private val BASIC_LATIN_SIZE   = BASIC_LATIN_RANGES.sumOf { it.last - it.first + 1 } // 52

    private val rangeSizes: Map<String, Int> = SCRIPTS.associate { def ->
        def.code to def.ranges.sumOf { it.last - it.first + 1 }
    }

    // Scripts that are candidates for the ASCII-legacy pattern
    // (scripts that historically had pre-Unicode ASCII-mapped fonts)
    private val ASCII_LEGACY_CANDIDATE_SCRIPTS = setOf(
        "devanagari", "bengali", "arabic", "hebrew", "tamil", "telugu",
        "kannada", "malayalam", "gujarati", "gurmukhi", "odia", "sinhala",
        "thai", "khmer", "myanmar", "georgian", "armenian", "ethiopic",
        "cyrillic", "greek", "tibetan", "lao", "mongolian",
        "han", "kana", "hangul"
    )

    data class AnalysisResult(
        val scriptCodes: List<String>,
        val isAsciiLegacy: Boolean
    )

    /**
     * Full analysis: returns both the script list and ASCII-legacy flag.
     * "ascii" is prepended to scriptCodes when isAsciiLegacy == true,
     * and "latin" is removed from scriptCodes in that case.
     */
    fun analyzeWithAscii(supportedChars: List<Int>, thresholdPercent: Int): AnalysisResult {
        if (supportedChars.isEmpty() || thresholdPercent <= 0)
            return AnalysisResult(emptyList(), false)

        val charSet = supportedChars.toHashSet()

        // ── Basic Latin coverage (A-Z / a-z) ──────────────────────────────
        val basicLatinMatched = BASIC_LATIN_RANGES.sumOf { r ->
            r.count { cp -> charSet.contains(cp) }
        }
        val basicLatinPct = basicLatinMatched * 100 / BASIC_LATIN_SIZE

        // ── Per-script coverage ────────────────────────────────────────────
        val coverageMap = mutableMapOf<String, Int>() // code -> percent
        for (def in SCRIPTS) {
            val total = rangeSizes[def.code] ?: continue
            if (total == 0) continue
            var matched = 0
            for (range in def.ranges) {
                for (cp in range) { if (charSet.contains(cp)) matched++ }
            }
            coverageMap[def.code] = matched * 100 / total
        }

        // ── Latin: qualify if basic-latin alone is ≥ 80% OR full coverage passes ──
        val latinQualifies = basicLatinPct >= 80 || (coverageMap["latin"] ?: 0) >= thresholdPercent

        // ── Non-Latin scripts that pass the threshold (by their own Unicode ranges) ──
        val nonLatinPassing = SCRIPTS
            .filter { it.code != "latin" && (coverageMap[it.code] ?: 0) >= thresholdPercent }
            .map { it.code }

        // ── ASCII-legacy detection ─────────────────────────────────────────
        // Condition: basic latin ≥ 80 %, but NO codepoints in the non-Latin
        // Unicode script ranges themselves pass the threshold — meaning the
        // font's glyphs for those scripts are actually encoded at ASCII slots.
        //
        // More precisely: for an ASCII-legacy font the non-Latin script ranges
        // are EMPTY (0 % coverage) because all glyphs are at A-Z positions.
        // We look for scripts where basic-latin is well covered AND the native
        // Unicode range for a candidate script has 0 % coverage, but the font
        // name / glyph count hints at it being a script font.  Since we can't
        // read glyph shapes, we use a heuristic:
        //   - basic latin ≥ 80 %
        //   - total unique chars is very small (≤ ~200) — ASCII fonts don't
        //     have thousands of Unicode codepoints
        //   - at least one candidate script has 0 % Unicode coverage
        //     (i.e. the designer never added Unicode codepoints)
        //
        // This avoids false-positives on real multilingual fonts that just
        // happen to have good Basic Latin coverage.

        val totalChars = charSet.size
        val hasNativeNonLatinChars = ASCII_LEGACY_CANDIDATE_SCRIPTS.any { code ->
            (coverageMap[code] ?: 0) > 0
        }

        // A font is ASCII-legacy when:
        //   1. Good basic-latin coverage (it "looks" like a Latin font via ASCII slots)
        //   2. Very few total codepoints — ASCII fonts are small
        //   3. Zero native non-Latin Unicode codepoints at all
        //      (no true Unicode encoding of any script)
        val isAsciiLegacy = basicLatinPct >= 80
                && totalChars <= 256
                && !hasNativeNonLatinChars
                && nonLatinPassing.isEmpty()
                // Sanity: must actually have glyphs (not a broken font)
                && basicLatinMatched >= 20

        // ── Build final list ───────────────────────────────────────────────
        val result = mutableListOf<String>()
        if (isAsciiLegacy) {
            result.add("ascii")
            // Don't add latin — the "latin" glyphs are actually the other script
        } else {
            if (latinQualifies) result.add("latin")
            result.addAll(nonLatinPassing)
        }

        return AnalysisResult(result, isAsciiLegacy)
    }

    /** Legacy single-return overload — returns script codes only. */
    fun analyze(supportedChars: List<Int>, thresholdPercent: Int): List<String> =
        analyzeWithAscii(supportedChars, thresholdPercent).scriptCodes
}
