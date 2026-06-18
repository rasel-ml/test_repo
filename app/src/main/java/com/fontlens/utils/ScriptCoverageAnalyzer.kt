package com.fontlens.utils

/**
 * Computes which language/script codes a font supports based on its cmap glyph set.
 * Coverage = (matched glyphs in range) / (total range size).
 * If coverage >= threshold, the script is considered supported.
 */
object ScriptCoverageAnalyzer {

    data class ScriptDef(val code: String, val ranges: List<IntRange>)

    val SCRIPTS: List<ScriptDef> = listOf(
        ScriptDef("en",   listOf(0x0041..0x005A, 0x0061..0x007A, 0x00C0..0x00FF)),
        ScriptDef("bn",   listOf(0x0980..0x09FF)),
        ScriptDef("hi",   listOf(0x0900..0x097F)),
        ScriptDef("ar",   listOf(0x0600..0x06FF, 0x0750..0x077F)),
        ScriptDef("he",   listOf(0x0590..0x05FF)),
        ScriptDef("zh",   listOf(0x4E00..0x9FFF)),
        ScriptDef("ja",   listOf(0x3040..0x309F, 0x30A0..0x30FF)),
        ScriptDef("ko",   listOf(0xAC00..0xD7A3)),
        ScriptDef("ta",   listOf(0x0B80..0x0BFF)),
        ScriptDef("te",   listOf(0x0C00..0x0C7F)),
        ScriptDef("kn",   listOf(0x0C80..0x0CFF)),
        ScriptDef("ml",   listOf(0x0D00..0x0D7F)),
        ScriptDef("gu",   listOf(0x0A80..0x0AFF)),
        ScriptDef("pa",   listOf(0x0A00..0x0A7F)),
        ScriptDef("or",   listOf(0x0B00..0x0B7F)),
        ScriptDef("si",   listOf(0x0D80..0x0DFF)),
        ScriptDef("th",   listOf(0x0E00..0x0E7F)),
        ScriptDef("km",   listOf(0x1780..0x17FF)),
        ScriptDef("my",   listOf(0x1000..0x109F)),
        ScriptDef("ka",   listOf(0x10A0..0x10FF)),
        ScriptDef("hy",   listOf(0x0530..0x058F)),
        ScriptDef("am",   listOf(0x1200..0x137F)),
        ScriptDef("ru",   listOf(0x0400..0x04FF)),
        ScriptDef("el",   listOf(0x0370..0x03FF)),
        ScriptDef("bo",   listOf(0x0F00..0x0FFF)),
        ScriptDef("lo",   listOf(0x0E80..0x0EFF)),
        ScriptDef("mn",   listOf(0x1800..0x18AF)),
        ScriptDef("pl",   listOf(0x0100..0x017F)),
        ScriptDef("ipa",  listOf(0x0250..0x02AF))
    )

    // Pre-compute range sizes once
    private val rangeSizes: Map<String, Int> = SCRIPTS.associate { def ->
        def.code to def.ranges.sumOf { it.last - it.first + 1 }
    }

    /**
     * Returns script codes whose coverage of [supportedChars] meets [thresholdPercent] (0–100).
     */
    fun analyze(supportedChars: List<Int>, thresholdPercent: Int): List<String> {
        if (supportedChars.isEmpty() || thresholdPercent <= 0) return emptyList()
        val charSet = supportedChars.toHashSet()
        val result  = mutableListOf<String>()
        for (def in SCRIPTS) {
            val total = rangeSizes[def.code] ?: continue
            if (total == 0) continue
            var matched = 0
            for (range in def.ranges) {
                for (cp in range) { if (charSet.contains(cp)) matched++ }
            }
            if (matched * 100 / total >= thresholdPercent) result.add(def.code)
        }
        return result
    }
}
