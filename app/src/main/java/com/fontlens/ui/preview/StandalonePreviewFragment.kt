package com.fontlens.ui.preview

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fontlens.R
import com.fontlens.data.FontRepository
import com.fontlens.databinding.FragmentPreviewBinding
import com.fontlens.ui.DeleteFontDialog
import com.fontlens.utils.TypefaceLoader
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class StandalonePreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    private var isBold   = false
    private var isItalic = false
    private var fontSize = 32

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fontId = arguments?.getString("fontId") ?: run { requireActivity().finish(); return }
        val font   = FontRepository.getById(fontId)  ?: run { requireActivity().finish(); return }

        binding.tvFontName.text = font.effectiveMeta.family.ifEmpty { font.displayName }
        binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }

        // ── Load typeface (may not be cached yet) ─────────────────────────────
        fun applyTypeface(tf: android.graphics.Typeface?) {
            if (tf == null) return
            val style = when {
                isBold && isItalic -> android.graphics.Typeface.BOLD_ITALIC
                isBold             -> android.graphics.Typeface.BOLD
                isItalic           -> android.graphics.Typeface.ITALIC
                else               -> android.graphics.Typeface.NORMAL
            }
            binding.etPreview.setTypeface(tf, style)
        }

        val cachedTf = TypefaceLoader.getTypeface(font.id)
        if (cachedTf != null) {
            applyTypeface(cachedTf)
        } else {
            lifecycleScope.launch {
                val tf = TypefaceLoader.loadSingle(requireContext(), font.id, font.uri)
                if (_binding != null) applyTypeface(tf)
            }
        }
        // ── Add / Delete / Favorite ───────────────────────────────────────────
        var isInLib = FontRepository.isInLibrary(font.id)
        val p = com.fontlens.utils.ThemeManager.activePalette

        // Add button — toolbar, only when not in library
        binding.btnAddToLibrary.visibility = if (!isInLib) View.VISIBLE else View.GONE

        // Delete and Favorite always visible
        binding.btnDelete.visibility   = View.VISIBLE
        binding.btnFavorite.visibility = View.VISIBLE

        fun updateFavoriteState() {
            val fav = isInLib && FontRepository.isFavorite(font.id)
            binding.btnFavorite.setImageResource(
                if (fav) R.drawable.ic_star else R.drawable.ic_star_outline)
            binding.btnFavorite.imageTintList =
                android.content.res.ColorStateList.valueOf(
                    if (fav) p.accent else p.textMuted)
            binding.btnFavorite.alpha = if (isInLib) 1f else 0.38f
        }
        updateFavoriteState()

        binding.btnAddToLibrary.setOnClickListener {
            FontRepository.promoteToLibrary(font.id, requireContext())
            isInLib = true
            binding.btnAddToLibrary.visibility = View.GONE
            updateFavoriteState()
            Toast.makeText(requireContext(), "Added to library", Toast.LENGTH_SHORT).show()
        }
        binding.btnAddToLibrary.setOnLongClickListener {
            showTooltip(it, getString(R.string.tooltip_add_library)); true
        }

        binding.btnDelete.setOnClickListener {
            if (isInLib) {
                DeleteFontDialog.show(requireContext(), font,
                    onRemoveFromLibrary = { requireActivity().finish() },
                    onDeletePermanently = { requireActivity().finish() }
                )
            } else {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete font")
                    .setMessage("Permanently delete \"${font.displayName}\"?")
                    .setPositiveButton("Delete") { _, _ -> requireActivity().finish() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        binding.btnFavorite.setOnClickListener {
            if (!isInLib) {
                Toast.makeText(requireContext(),
                    "Add to library first to favourite", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FontRepository.toggleFavorite(font.id, requireContext())
            updateFavoriteState()
        }

        // ── Long press tooltips ───────────────────────────────────────────────
        binding.btnInfo.setOnLongClickListener     { showTooltip(it, getString(R.string.tooltip_font_info));     true }
        binding.btnMeta.setOnLongClickListener     { showTooltip(it, getString(R.string.tooltip_font_metadata)); true }
        binding.btnGlyph.setOnLongClickListener    { showTooltip(it, getString(R.string.tooltip_glyph_map));     true }
        binding.btnDelete.setOnLongClickListener   { showTooltip(it, getString(R.string.tooltip_delete));        true }
        binding.btnFavorite.setOnLongClickListener { showTooltip(it, getString(R.string.tooltip_favorite));      true }

        // ── Preview text ──────────────────────────────────────────────────────
        binding.etPreview.setText(FontRepository.getSampleText(font))

        // ── Size seekbar ──────────────────────────────────────────────────────
        fontSize = 32
        binding.seekbarSize.max = 152
        binding.seekbarSize.progress = fontSize - 8
        binding.tvSizeLabel.text = "${fontSize}px"
        binding.etPreview.textSize = fontSize.toFloat()
        binding.seekbarSize.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                fontSize = p + 8
                binding.tvSizeLabel.text = "${fontSize}px"
                binding.etPreview.textSize = fontSize.toFloat()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        // ── Bold / Italic ─────────────────────────────────────────────────────
        fun updateStyle() {
            val tf = TypefaceLoader.getTypeface(font.id)
            val style = when {
                isBold && isItalic -> android.graphics.Typeface.BOLD_ITALIC
                isBold             -> android.graphics.Typeface.BOLD
                isItalic           -> android.graphics.Typeface.ITALIC
                else               -> android.graphics.Typeface.NORMAL
            }
            binding.etPreview.setTypeface(tf, style)
            val accent = ContextCompat.getColor(requireContext(), R.color.accent)
            val muted  = ContextCompat.getColor(requireContext(), R.color.text_muted)
            binding.btnBold.setTextColor(if (isBold) accent else muted)
            binding.btnItalic.setTextColor(if (isItalic) accent else muted)
            binding.btnBold.setBackgroundResource(
                if (isBold) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
            binding.btnItalic.setBackgroundResource(
                if (isItalic) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
        }
        binding.btnBold.setOnClickListener   { isBold   = !isBold;   updateStyle() }
        binding.btnItalic.setOnClickListener { isItalic = !isItalic; updateStyle() }

        // ── Sub-screens ───────────────────────────────────────────────────────
        binding.btnGlyph.setOnClickListener {
            openSubFragment(StandaloneGlyphFragment().apply {
                arguments = Bundle().apply { putString("fontId", font.id) }
            })
        }
        binding.btnMeta.setOnClickListener {
            openSubFragment(StandaloneMetaFragment().apply {
                arguments = Bundle().apply { putString("fontId", font.id) }
            })
        }
        binding.btnInfo.setOnClickListener {
            openSubFragment(StandaloneInfoFragment().apply {
                arguments = Bundle().apply { putString("fontId", font.id) }
            })
        }
    }

    private fun openSubFragment(fragment: Fragment) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.preview_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun showTooltip(anchor: View, text: String) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density
        val tv  = android.widget.TextView(ctx).apply {
            this.text = text
            textSize  = 12f
            setTextColor(android.graphics.Color.WHITE)
            setPadding((10 * dp).toInt(), (5 * dp).toInt(), (10 * dp).toInt(), (5 * dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.argb(220, 30, 30, 30))
                cornerRadius = 4f * dp
            }
        }
        val popup = android.widget.PopupWindow(tv,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        popup.isOutsideTouchable = true
        popup.isFocusable        = false
        popup.elevation          = 8f * dp
        anchor.post {
            tv.measure(android.view.View.MeasureSpec.UNSPECIFIED, android.view.View.MeasureSpec.UNSPECIFIED)
            val xOff = (anchor.width - tv.measuredWidth) / 2
            popup.showAsDropDown(anchor, xOff, -(anchor.height + tv.measuredHeight + (4 * dp).toInt()))
        }
        anchor.postDelayed({ popup.dismiss() }, 1500)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
