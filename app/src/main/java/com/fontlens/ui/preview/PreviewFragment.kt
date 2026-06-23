package com.fontlens.ui.preview

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.fontlens.R
import com.fontlens.data.FontRepository
import com.fontlens.databinding.FragmentPreviewBinding
import com.fontlens.ui.DeleteFontDialog
import com.fontlens.utils.StorageDeleteHelper
import com.fontlens.utils.ThemeManager
import com.fontlens.utils.TypefaceLoader
import kotlinx.coroutines.launch

class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!
    private val args: PreviewFragmentArgs by navArgs()

    private lateinit var storageDeleteHelper: StorageDeleteHelper
    private var pendingDeleteFontId: String? = null

    private var isBold   = false
    private var isItalic = false
    private var fontSize = 32
    private var fontColor: Int = Color.BLACK      // updated on view creation from theme
    private var bgColor:   Int = Color.WHITE

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val p = ThemeManager.activePalette
        fontColor = p.textPrimary
        bgColor   = p.bgPrimary

        storageDeleteHelper = StorageDeleteHelper(this) { success ->
            if (success) {
                pendingDeleteFontId?.let { id ->
                    FontRepository.removeFont(id, requireContext())
                    findNavController().popBackStack()
                }
            } else {
                Toast.makeText(requireContext(), "Delete cancelled or failed", Toast.LENGTH_SHORT).show()
            }
            pendingDeleteFontId = null
        }

        val font = FontRepository.getById(args.fontId)
            ?: run { findNavController().popBackStack(); return }
        val tempMode = args.tempMode

        // ── Header ───────────────────────────────────────────────────────
        binding.tvFontName.text = font.effectiveMeta.family.ifEmpty { font.displayName }
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // ── Menu bar visibility ───────────────────────────────────────────
        val inLibrary = !tempMode || FontRepository.isInLibrary(font.id)

        if (tempMode && !FontRepository.isInLibrary(font.id)) {
            binding.btnAddToLibrary.visibility = View.VISIBLE
            binding.btnAddToLibrary.setOnClickListener {
                FontRepository.promoteToLibrary(font.id, requireContext())
                binding.btnAddToLibrary.visibility = View.GONE
                binding.btnDelete.visibility   = View.VISIBLE
                binding.btnFavorite.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "Added to library", Toast.LENGTH_SHORT).show()
            }
        } else {
            binding.btnAddToLibrary.visibility = View.GONE
        }

        binding.btnDelete.visibility   = if (inLibrary) View.VISIBLE else View.GONE
        binding.btnFavorite.visibility = if (inLibrary) View.VISIBLE else View.GONE

        // ── Delete ────────────────────────────────────────────────────────
        binding.btnDelete.setOnClickListener {
            DeleteFontDialog.show(
                context = requireContext(),
                font = font,
                onRemoveFromLibrary = { findNavController().popBackStack() },
                onDeletePermanently = {
                    pendingDeleteFontId = font.id
                    storageDeleteHelper.requestDelete(font.uri)
                }
            )
        }

        // ── Favorite ──────────────────────────────────────────────────────
        fun updateFav() {
            val fav = FontRepository.isFavorite(font.id)
            binding.btnFavorite.setImageResource(
                if (fav) R.drawable.ic_star else R.drawable.ic_star_outline)
            binding.btnFavorite.imageTintList =
                android.content.res.ColorStateList.valueOf(if (fav) p.accent else p.textMuted)
        }
        updateFav()
        binding.btnFavorite.setOnClickListener {
            FontRepository.toggleFavorite(font.id, requireContext()); updateFav()
        }

        // ── Navigation buttons ────────────────────────────────────────────
        binding.btnGlyph.setOnClickListener {
            findNavController().navigate(R.id.action_preview_to_glyph,
                Bundle().apply { putString("fontId", font.id) })
        }
        binding.btnMeta.setOnClickListener {
            findNavController().navigate(R.id.action_preview_to_meta,
                Bundle().apply { putString("fontId", font.id) })
        }
        binding.btnInfo.setOnClickListener {
            findNavController().navigate(R.id.action_preview_to_info,
                Bundle().apply { putString("fontId", font.id) })
        }

        // ── Preview text ──────────────────────────────────────────────────
        binding.etPreview.setText(
            args.initialSampleText.ifEmpty { FontRepository.getSampleText(font) }
        )
        applyColors()

        // ── Typeface ──────────────────────────────────────────────────────
        fun applyTypeface() {
            val tf = TypefaceLoader.getTypeface(font.id) ?: return
            val style = when {
                isBold && isItalic -> Typeface.BOLD_ITALIC
                isBold             -> Typeface.BOLD
                isItalic           -> Typeface.ITALIC
                else               -> Typeface.NORMAL
            }
            binding.etPreview.setTypeface(tf, style)
        }

        if (TypefaceLoader.isLoaded(font.id)) {
            applyTypeface()
        } else {
            lifecycleScope.launch {
                TypefaceLoader.loadSingle(requireContext(), font.id, font.uri)
                if (_binding != null) applyTypeface()
            }
        }

        // ── Font size slider ──────────────────────────────────────────────
        fontSize = 32
        binding.seekbarSize.max      = 152
        binding.seekbarSize.progress = fontSize - 8
        binding.tvSizeLabel.text     = "${fontSize}px"
        binding.etPreview.textSize   = fontSize.toFloat()

        fun applySize() {
            binding.tvSizeLabel.text   = "${fontSize}px"
            binding.etPreview.textSize = fontSize.toFloat()
            binding.seekbarSize.progress = (fontSize - 8).coerceIn(0, 152)
        }

        binding.seekbarSize.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { fontSize = progress + 8; applySize() }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        binding.btnSizeMinus.setOnClickListener {
            if (fontSize > 8) { fontSize -= 2; applySize() }
        }
        binding.btnSizePlus.setOnClickListener {
            if (fontSize < 160) { fontSize += 2; applySize() }
        }

        // ── Bold / Italic ─────────────────────────────────────────────────
        fun updateStyle() {
            applyTypeface()
            binding.btnBold.setTextColor(if (isBold) p.accent else p.textMuted)
            binding.btnItalic.setTextColor(if (isItalic) p.accent else p.textMuted)
            binding.btnBold.setBackgroundResource(
                if (isBold) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
            binding.btnItalic.setBackgroundResource(
                if (isItalic) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
        }
        binding.btnBold.setOnClickListener   { isBold   = !isBold;   updateStyle() }
        binding.btnItalic.setOnClickListener { isItalic = !isItalic; updateStyle() }

        // ── Font color picker ─────────────────────────────────────────────
        binding.btnFontColor.setOnClickListener {
            showColorPicker(initial = fontColor, title = "Font Color") { picked ->
                fontColor = picked
                applyColors()
            }
        }

        // ── Background color picker ───────────────────────────────────────
        binding.btnBgColor.setOnClickListener {
            showColorPicker(initial = bgColor, title = "Background Color") { picked ->
                bgColor = picked
                applyColors()
            }
        }
    }

    // ── Color helpers ─────────────────────────────────────────────────────────

    private fun applyColors() {
        binding.etPreview.setTextColor(fontColor)
        binding.etPreview.setBackgroundColor(bgColor)
        binding.scrollPreview.setBackgroundColor(bgColor)
        binding.fontColorIndicator.setBackgroundColor(fontColor)
        binding.bgColorIndicator.setBackgroundColor(bgColor)
    }

    private fun showColorPicker(initial: Int, title: String, onPick: (Int) -> Unit) {
        // Build a simple HSV color picker dialog using Android's built-in color int support
        val ctx = requireContext()

        // Predefined palette for quick pick + manual RGB input
        val colors = intArrayOf(
            // Neutrals
            Color.BLACK, Color.DKGRAY, Color.GRAY, Color.LTGRAY, Color.WHITE,
            // Reds
            0xFFB71C1C.toInt(), 0xFFE53935.toInt(), 0xFFEF9A9A.toInt(),
            // Oranges
            0xFFE65100.toInt(), 0xFFFB8C00.toInt(), 0xFFFFCC80.toInt(),
            // Yellows
            0xFFF9A825.toInt(), 0xFFFFEE58.toInt(), 0xFFFFF9C4.toInt(),
            // Greens
            0xFF1B5E20.toInt(), 0xFF2E7D32.toInt(), 0xFF66BB6A.toInt(), 0xFFC8E6C9.toInt(),
            // Blues
            0xFF0D47A1.toInt(), 0xFF1565C0.toInt(), 0xFF42A5F5.toInt(), 0xFFBBDEFB.toInt(),
            // Purples
            0xFF4A148C.toInt(), 0xFF7B1FA2.toInt(), 0xFFCE93D8.toInt(),
            // Pinks
            0xFF880E4F.toInt(), 0xFFEC407A.toInt(), 0xFFF48FB1.toInt(),
        )

        val dp  = ctx.resources.displayMetrics.density
        val pad = (12 * dp).toInt()

        // Grid of color swatches
        val grid = android.widget.GridLayout(ctx).apply {
            columnCount = 5
            setPadding(pad, pad, pad, 0)
        }

        val swatchSize = (44 * dp).toInt()
        val swatchPad  = (3 * dp).toInt()

        colors.forEach { color ->
            val swatch = View(ctx).apply {
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width  = swatchSize
                    height = swatchSize
                    setMargins(swatchPad, swatchPad, swatchPad, swatchPad)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = 6 * dp
                    setColor(color)
                    // Border for light colors so they're visible
                    setStroke((1 * dp).toInt(), Color.argb(40, 0, 0, 0))
                }
                setOnClickListener {
                    onPick(color)
                    // dismiss handled by dialog reference below
                    (tag as? android.app.AlertDialog)?.dismiss()
                }
            }
            grid.addView(swatch)
        }

        // Current color preview + hex input
        val previewBar = View(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (32 * dp).toInt())
            setBackgroundColor(initial)
        }

        val hexInput = android.widget.EditText(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            hint         = "#RRGGBB"
            setText(String.format("#%06X", 0xFFFFFF and initial))
            setPadding(pad, (8 * dp).toInt(), pad, (8 * dp).toInt())
            inputType    = android.text.InputType.TYPE_CLASS_TEXT
            maxLines     = 1
        }

        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(previewBar)
            addView(grid)
            addView(hexInput)
        }

        val dialog = android.app.AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Apply") { _, _ ->
                val hex = hexInput.text.toString().trim()
                try {
                    onPick(Color.parseColor(if (hex.startsWith("#")) hex else "#$hex"))
                } catch (e: Exception) { /* ignore invalid hex */ }
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Tag each swatch with the dialog for dismiss on tap
        for (i in 0 until grid.childCount) {
            grid.getChildAt(i).tag = dialog
        }

        // Update preview bar as user types hex
        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                try {
                    val c = Color.parseColor(s.toString().let { if (it.startsWith("#")) it else "#$it" })
                    previewBar.setBackgroundColor(c)
                } catch (_: Exception) {}
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        dialog.show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
