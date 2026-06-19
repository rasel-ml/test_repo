package com.fontlens.ui.preview

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import com.fontlens.data.FontRepository
import com.fontlens.databinding.FragmentGlyphBinding
import com.fontlens.ui.glyph.GlyphAdapter
import com.fontlens.ui.glyph.GlyphScriptDefs
import com.fontlens.utils.ThemeManager
import com.fontlens.utils.TypefaceLoader

class StandaloneGlyphFragment : Fragment() {

    private var _binding: FragmentGlyphBinding? = null
    private val binding get() = _binding!!

    private var allPages    = listOf<GlyphScriptDefs.BlockPage>()
    private var searchPages = listOf<GlyphScriptDefs.BlockPage>()
    private var currentPageIdx = 0
    private var searchQuery = ""
    private var searchMode  = "char"

    private lateinit var adapter: GlyphAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlyphBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fontId  = arguments?.getString("fontId") ?: return
        val font    = FontRepository.getById(fontId)  ?: return
        val tf      = TypefaceLoader.getTypeface(font.id)
        val showAll = FontRepository.settings.glyphShowAll
        val p       = ThemeManager.activePalette
        val dp      = requireContext().resources.displayMetrics.density

        binding.toolbar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        // Build pages
        val supportedSet = font.meta.supportedChars.toSet()
        allPages = GlyphScriptDefs.buildPages(supportedSet, showAll)

        // Count badge
        binding.tvGlyphCount.text = font.meta.supportedChars.size.toString()
        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * dp
            setColor(p.accent)
        }
        binding.tvGlyphCount.background = badgeBg
        binding.tvGlyphCount.setTextColor(Color.WHITE)

        // Search mode toggles
        updateSearchModeUI()
        binding.btnSearchChar.setOnClickListener {
            searchMode = "char"; updateSearchModeUI(); reapplySearch()
        }
        binding.btnSearchUnicode.setOnClickListener {
            searchMode = "unicode"; updateSearchModeUI(); reapplySearch()
        }

        // Search text
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun afterTextChanged(e: Editable?) {
                searchQuery = e?.toString() ?: ""
                currentPageIdx = 0
                reapplySearch()
            }
        })

        // Adapter + grid
        adapter = GlyphAdapter(tf, supportedSet, showAll)
        binding.rvGlyphs.layoutManager = GridLayoutManager(requireContext(), 6)
        binding.rvGlyphs.adapter = adapter

        // Pagination
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
                    "unicode" -> cp.toString(16).contains(q, ignoreCase = true) ||
                                 "U+${cp.toString(16).uppercase()}".contains(q, ignoreCase = true)
                    else -> {
                        val ch = try { String(Character.toChars(cp)) } catch (e: Exception) { "" }
                        ch.contains(q) || cp.toString(16).contains(q, ignoreCase = true)
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
        binding.tvScriptStats.text = "${page.presentSet.size} / ${page.block.range.count()} glyphs available"
        binding.tvPage.text        = "${safeIdx + 1} / ${pages.size}"
        binding.btnPrev.alpha      = if (safeIdx > 0) 1f else 0.3f
        binding.btnNext.alpha      = if (safeIdx < pages.size - 1) 1f else 0.3f

        val fontId = arguments?.getString("fontId") ?: return
        val tf     = FontRepository.getById(fontId)?.let { TypefaceLoader.getTypeface(it.id) }
        adapter.update(page.codepoints, page.presentSet, showAll, tf)
    }

    private fun updateSearchModeUI() {
        val p  = ThemeManager.activePalette
        val dp = requireContext().resources.displayMetrics.density
        fun styleBtn(tv: TextView, active: Boolean) {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * dp
                if (active) { setColor(p.accent); setStroke(0, 0) }
                else        { setColor(Color.TRANSPARENT); setStroke((1f * dp).toInt(), p.border) }
            }
            tv.background = bg
            tv.setTextColor(if (active) Color.WHITE else p.textMuted)
        }
        styleBtn(binding.btnSearchChar,    searchMode == "char")
        styleBtn(binding.btnSearchUnicode, searchMode == "unicode")
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
