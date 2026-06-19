package com.fontlens.ui.glyph

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.fontlens.data.FontRepository
import com.fontlens.databinding.FragmentGlyphBinding
import com.fontlens.utils.ThemeManager
import com.fontlens.utils.TypefaceLoader
import kotlin.math.roundToInt

class GlyphFragment : Fragment() {

    private var _binding: FragmentGlyphBinding? = null
    private val binding get() = _binding!!
    private val args: GlyphFragmentArgs by navArgs()

    // All pages built from font + settings
    private var allPages = listOf<GlyphScriptDefs.BlockPage>()
    // Pages after search filter (search re-flattens across all pages)
    private var searchPages = listOf<GlyphScriptDefs.BlockPage>()
    private var currentPageIdx = 0
    private var searchQuery = ""

    private lateinit var adapter: GlyphAdapter

    // Search mode: "char" or "unicode"
    private var searchMode = "char"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGlyphBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val font = FontRepository.getById(args.fontId) ?: run {
            findNavController().popBackStack(); return
        }
        val tf       = TypefaceLoader.getTypeface(font.id)
        val showAll  = FontRepository.settings.glyphShowAll
        val p        = ThemeManager.activePalette
        val dp       = requireContext().resources.displayMetrics.density

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // ── Build pages ────────────────────────────────────────────────────
        val supportedSet = font.meta.supportedChars.toSet()
        allPages = GlyphScriptDefs.buildPages(supportedSet, showAll)

        // ── Total glyph count badge in toolbar ────────────────────────────
        val totalGlyphs = font.meta.supportedChars.size
        binding.tvGlyphCount.text = totalGlyphs.toString()
        val badgeBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20f * dp
            setColor(p.accent)
        }
        binding.tvGlyphCount.background = badgeBg
        binding.tvGlyphCount.setTextColor(Color.WHITE)

        // ── Search toggle buttons ──────────────────────────────────────────
        updateSearchModeUI()
        binding.btnSearchChar.setOnClickListener {
            searchMode = "char"
            updateSearchModeUI()
            reapplySearch()
        }
        binding.btnSearchUnicode.setOnClickListener {
            searchMode = "unicode"
            updateSearchModeUI()
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

        // ── Adapter ───────────────────────────────────────────────────────
        adapter = GlyphAdapter(tf, supportedSet, showAll)
        binding.rvGlyphs.layoutManager = GridLayoutManager(requireContext(), 6)
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
        if (searchQuery.isBlank()) {
            searchPages = allPages
            renderPage()
            return
        }
        // Filter across all pages — rebuild synthetic pages per block
        val q = searchQuery.trim()
        searchPages = allPages.mapNotNull { page ->
            val matched = page.codepoints.filter { cp ->
                when (searchMode) {
                    "unicode" -> cp.toString(16).contains(q, ignoreCase = true) ||
                                 "U+${cp.toString(16).uppercase()}".contains(q, ignoreCase = true)
                    else -> { // char
                        val ch = try { String(Character.toChars(cp)) } catch (e: Exception) { "" }
                        ch.contains(q) || cp.toString(16).contains(q, ignoreCase = true)
                    }
                }
            }
            if (matched.isEmpty()) null
            else page.copy(codepoints = matched)
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

        // Script name header
        binding.tvScriptName.text = page.block.name

        // Stats: present / total in block
        val totalInBlock   = page.block.range.count()
        val presentInBlock = page.presentSet.size
        binding.tvScriptStats.text = "$presentInBlock / $totalInBlock glyphs available"

        // Page counter
        binding.tvPage.text = "${safeIdx + 1} / ${pages.size}"

        // Prev / next button opacity
        binding.btnPrev.alpha = if (safeIdx > 0) 1f else 0.3f
        binding.btnNext.alpha = if (safeIdx < pages.size - 1) 1f else 0.3f

        // Feed adapter
        val font = FontRepository.getById(args.fontId)
        val tf   = font?.let { TypefaceLoader.getTypeface(it.id) }
        adapter.update(page.codepoints, page.presentSet, showAll, tf)
    }

    private fun updateSearchModeUI() {
        val p  = ThemeManager.activePalette
        val dp = requireContext().resources.displayMetrics.density
        fun styleBtn(tv: TextView, active: Boolean) {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * dp
                if (active) {
                    setColor(p.accent)
                    setStroke(0, 0)
                } else {
                    setColor(Color.TRANSPARENT)
                    setStroke((1f * dp).toInt(), p.border)
                }
            }
            tv.background = bg
            tv.setTextColor(if (active) Color.WHITE else p.textMuted)
        }
        styleBtn(binding.btnSearchChar,    searchMode == "char")
        styleBtn(binding.btnSearchUnicode, searchMode == "unicode")
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
