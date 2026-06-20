package com.fontlens.ui.glyph

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import com.fontlens.data.FontItem
import com.fontlens.data.FontRepository
import com.fontlens.databinding.FragmentGlyphBinding
import com.fontlens.utils.ThemeManager
import com.fontlens.utils.TypefaceLoader

/**
 * Shared logic used by both GlyphFragment and StandaloneGlyphFragment.
 * Holds all state and wires up the UI — caller just provides the binding and font.
 */
class GlyphUiHelper(
    private val binding: FragmentGlyphBinding,
    private val font: FontItem,
    private val onNavigateBack: () -> Unit
) {
    private var allPages       = listOf<GlyphScriptDefs.BlockPage>()
    private var searchPages    = listOf<GlyphScriptDefs.BlockPage>()
    private var currentPageIdx = 0
    private var searchQuery    = ""
    private var searchMode     = "char"   // "char" | "unicode"

    private lateinit var adapter: GlyphAdapter

    fun setup() {
        val tf      = TypefaceLoader.getTypeface(font.id)
        val showAll = FontRepository.settings.glyphShowAll
        val p       = ThemeManager.activePalette
        val dp      = binding.root.context.resources.displayMetrics.density
        val ctx     = binding.root.context

        binding.toolbar.setNavigationOnClickListener { onNavigateBack() }

        // ── Build pages sorted by available glyph count descending ────────
        val supportedSet = font.meta.supportedChars.toSet()
        allPages = GlyphScriptDefs.buildPages(supportedSet, showAll)
            .sortedByDescending { it.presentSet.size }

        // ── Total glyph count badge ────────────────────────────────────────
        binding.tvGlyphCount.text = font.meta.supportedChars.size.toString()
        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * dp
            setColor(p.accent)
        }
        binding.tvGlyphCount.background = badgeBg
        binding.tvGlyphCount.setTextColor(Color.WHITE)

        // ── Single toggle button Aa ↔ UN ──────────────────────────────────
        styleToggleBtn(p, dp)
        binding.btnSearchToggle.setOnClickListener {
            searchMode = if (searchMode == "char") "unicode" else "char"
            styleToggleBtn(p, dp)
            reapplySearch()
        }

        // ── Search text ───────────────────────────────────────────────────
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(e: Editable?) {
                searchQuery = e?.toString() ?: ""
                currentPageIdx = 0
                reapplySearch()
            }
        })

        // ── Adapter + grid ────────────────────────────────────────────────
        adapter = GlyphAdapter(tf, supportedSet, showAll)
        binding.rvGlyphs.layoutManager = GridLayoutManager(ctx, 6)
        binding.rvGlyphs.adapter = adapter

        // ── Pagination ────────────────────────────────────────────────────
        binding.btnPrev.setOnClickListener {
            if (currentPageIdx > 0) { currentPageIdx--; renderPage() }
        }
        binding.btnNext.setOnClickListener {
            if (currentPageIdx < activePages().size - 1) { currentPageIdx++; renderPage() }
        }

        searchPages = allPages
        renderPage()
    }

    private fun activePages() = if (searchQuery.isBlank()) allPages else searchPages

    private fun reapplySearch() {
        if (searchQuery.isBlank()) { searchPages = allPages; renderPage(); return }
        val q = searchQuery.trim()
        searchPages = allPages.mapNotNull { page ->
            val matched = page.codepoints.filter { cp ->
                when (searchMode) {
                    "unicode" -> {
                        // Match if the hex value contains the query string (case-insensitive)
                        // e.g. query "A" matches U+0041, U+00A0, U+1A00 etc.
                        val hex = cp.toString(16).uppercase()
                        val qUp = q.uppercase()
                        hex.contains(qUp) || "U+$hex".contains(qUp)
                    }
                    else -> {
                        // Exact character match — each input character is matched individually
                        // e.g. query "A" shows only U+0041, query "a" shows only U+0061
                        try {
                            val ch = String(Character.toChars(cp))
                            q.any { inputChar -> ch == inputChar.toString() }
                        } catch (e: Exception) { false }
                    }
                }
            }
            if (matched.isEmpty()) null else page.copy(codepoints = matched)
        }
        renderPage()
    }

    private fun renderPage() {
        val pages = activePages()
        if (pages.isEmpty()) {
            binding.tvScriptName.text  = "No results"
            binding.tvScriptStats.text = ""
            binding.tvPage.text        = "0 / 0"
            binding.btnPrev.alpha      = 0.3f
            binding.btnNext.alpha      = 0.3f
            adapter.update(emptyList(), emptySet(), false, null)
            return
        }
        val safeIdx = currentPageIdx.coerceIn(0, pages.size - 1)
        currentPageIdx = safeIdx
        val page    = pages[safeIdx]
        val showAll = FontRepository.settings.glyphShowAll

        binding.tvScriptName.text  = page.block.name
        val totalInBlock           = page.block.range.count()
        binding.tvScriptStats.text = "${page.presentSet.size} / $totalInBlock"
        binding.tvPage.text        = "${safeIdx + 1} / ${pages.size}"
        binding.btnPrev.alpha      = if (safeIdx > 0) 1f else 0.3f
        binding.btnNext.alpha      = if (safeIdx < pages.size - 1) 1f else 0.3f

        val tf = TypefaceLoader.getTypeface(font.id)
        adapter.update(page.codepoints, page.presentSet, showAll, tf)
    }

    private fun styleToggleBtn(p: ThemeManager.Palette, dp: Float) {
        val isChar = searchMode == "char"
        binding.btnSearchToggle.text = if (isChar) "Aa" else "UN"
        // Round button: cornerRadius = 50% makes it a pill/circle
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f * dp   // fully round
            setColor(Color.argb(75,
                Color.red(p.accent), Color.green(p.accent), Color.blue(p.accent)))
        }
        binding.btnSearchToggle.background = bg
        binding.btnSearchToggle.setTextColor(p.accent)
    }
}
