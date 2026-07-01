package com.fontlens.ui.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.fontlens.R
import com.fontlens.data.FontRepository
import com.fontlens.databinding.FragmentPreviewBinding
import com.fontlens.ui.DeleteFontDialog
import com.fontlens.utils.ThemeManager
import com.fontlens.utils.TypefaceLoader
import kotlinx.coroutines.launch

class StandalonePreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!

    // Style state — uses PreviewState for persistence
    private var isBold    get() = PreviewState.isBold;    set(v) { PreviewState.isBold    = v }
    private var isItalic  get() = PreviewState.isItalic;  set(v) { PreviewState.isItalic  = v }
    private var fontSize  get() = PreviewState.fontSize;  set(v) { PreviewState.fontSize  = v }
    private var textAlign get() = PreviewState.textAlign; set(v) { PreviewState.textAlign  = v }
    private var fontColor get() = PreviewState.fontColor  ?: ThemeManager.activePalette.textPrimary
                          set(v) { PreviewState.fontColor = v }
    private var bgColor   get() = PreviewState.bgColor    ?: ThemeManager.activePalette.bgPrimary
                          set(v) { PreviewState.bgColor   = v }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            PreviewState.bgImageUri = uri
            PreviewState.bgColor    = null
            applyBgImage(uri)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fontId = arguments?.getString("fontId") ?: run { requireActivity().finish(); return }
        val font   = FontRepository.getById(fontId)  ?: run { requireActivity().finish(); return }
        val p      = ThemeManager.activePalette

        binding.tvFontName.text = font.effectiveMeta.family.ifEmpty { font.displayName }
        binding.toolbar.setNavigationOnClickListener { requireActivity().finish() }

        // ── Typeface ──────────────────────────────────────────────────────
        fun applyTypeface() {
            val tf = TypefaceLoader.getTypeface(font.id) ?: return
            val style = when {
                isBold && isItalic -> android.graphics.Typeface.BOLD_ITALIC
                isBold             -> android.graphics.Typeface.BOLD
                isItalic           -> android.graphics.Typeface.ITALIC
                else               -> android.graphics.Typeface.NORMAL
            }
            binding.etPreview.setTypeface(tf, style)
        }

        if (TypefaceLoader.isLoaded(font.id)) applyTypeface()
        else lifecycleScope.launch {
            TypefaceLoader.loadSingle(requireContext(), font.id, font.uri)
            if (_binding != null) applyTypeface()
        }

        // ── Add / Delete / Favorite ───────────────────────────────────────
        var isInLib = FontRepository.isInLibrary(font.id)
        binding.btnAddToLibrary.visibility = if (!isInLib) View.VISIBLE else View.GONE
        binding.btnDelete.visibility   = View.VISIBLE
        binding.btnFavorite.visibility = View.VISIBLE

        fun updateFavoriteState() {
            val fav = isInLib && FontRepository.isFavorite(font.id)
            binding.btnFavorite.setImageResource(if (fav) R.drawable.ic_star else R.drawable.ic_star_outline)
            binding.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(
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
        binding.btnDelete.setOnClickListener {
            if (isInLib) {
                DeleteFontDialog.show(requireContext(), font,
                    onRemoveFromLibrary = { requireActivity().finish() },
                    onDeletePermanently = { requireActivity().finish() })
            } else {
                android.app.AlertDialog.Builder(requireContext())
                    .setTitle("Delete font")
                    .setMessage("Permanently delete \"${font.displayName}\"?")
                    .setPositiveButton("Delete") { _, _ -> requireActivity().finish() }
                    .setNegativeButton("Cancel", null).show()
            }
        }
        binding.btnFavorite.setOnClickListener {
            if (!isInLib) {
                Toast.makeText(requireContext(), "Add to library first to favourite", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            FontRepository.toggleFavorite(font.id, requireContext())
            updateFavoriteState()
        }

        // ── Preview text ──────────────────────────────────────────────────
        binding.etPreview.setText(FontRepository.getSampleText(font))

        // ── Apply all persistent state ────────────────────────────────────
        fun updateAlignIcon() {
            val iconRes = when (textAlign) {
                Gravity.CENTER_HORIZONTAL -> R.drawable.ic_align_center
                Gravity.END               -> R.drawable.ic_align_right
                Gravity.FILL_HORIZONTAL   -> R.drawable.ic_align_justify
                else                      -> R.drawable.ic_align_left
            }
            binding.btnAlign.setImageResource(iconRes)
            binding.btnAlign.imageTintList = android.content.res.ColorStateList.valueOf(
                if (textAlign != Gravity.START) p.accent else p.textMuted)
        }

        fun updateStyle() {
            applyTypeface()
            binding.btnBold.setTextColor(if (isBold) p.accent else p.textMuted)
            binding.btnItalic.setTextColor(if (isItalic) p.accent else p.textMuted)
            binding.btnBold.setBackgroundResource(if (isBold) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
            binding.btnItalic.setBackgroundResource(if (isItalic) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
        }

        fun applyAll() {
            binding.tvSizeLabel.text     = "${fontSize}px"
            binding.etPreview.textSize   = fontSize.toFloat()
            binding.seekbarSize.progress = (fontSize - 8).coerceIn(0, 152)
            binding.etPreview.setTextColor(fontColor)
            if (PreviewState.bgImageUri == null) {
                binding.etPreview.setBackgroundColor(bgColor)
                binding.scrollPreview.setBackgroundColor(bgColor)
                binding.ivBgImage.visibility = View.GONE
            } else {
                binding.etPreview.setBackgroundColor(Color.TRANSPARENT)
                binding.scrollPreview.setBackgroundColor(Color.TRANSPARENT)
                applyBgImage(PreviewState.bgImageUri!!)
            }
            binding.fontColorIndicator.setBackgroundColor(fontColor)
            binding.bgColorIndicator.setBackgroundColor(bgColor)
            binding.etPreview.gravity = textAlign or Gravity.TOP
            updateAlignIcon()
            updateStyle()
        }
        applyAll()

        // ── Size slider ───────────────────────────────────────────────────
        fun applySize() {
            binding.tvSizeLabel.text     = "${fontSize}px"
            binding.etPreview.textSize   = fontSize.toFloat()
            binding.seekbarSize.progress = (fontSize - 8).coerceIn(0, 152)
        }
        binding.seekbarSize.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) { fontSize = progress + 8; applySize() }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        binding.btnSizeMinus.setOnClickListener { if (fontSize > 8)   { fontSize -= 2; applySize() } }
        binding.btnSizePlus.setOnClickListener  { if (fontSize < 160) { fontSize += 2; applySize() } }

        // ── Bold / Italic ─────────────────────────────────────────────────
        binding.btnBold.setOnClickListener   { isBold   = !isBold;   updateStyle() }
        binding.btnItalic.setOnClickListener { isItalic = !isItalic; updateStyle() }

        // ── Align popup ───────────────────────────────────────────────────
        binding.btnAlign.setOnClickListener { anchor ->
            val dp = resources.displayMetrics.density
            val popupView = android.widget.LinearLayout(requireContext()).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setBackgroundResource(R.drawable.bg_style_btn)
                setPadding((6*dp).toInt(), (6*dp).toInt(), (6*dp).toInt(), (6*dp).toInt())
                elevation = 8f * dp
            }
            val alignOptions = listOf(
                R.drawable.ic_align_left    to Gravity.START,
                R.drawable.ic_align_center  to Gravity.CENTER_HORIZONTAL,
                R.drawable.ic_align_right   to Gravity.END,
                R.drawable.ic_align_justify to Gravity.FILL_HORIZONTAL
            )
            val popup = PopupWindow(popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true)
            popup.elevation = 8f * dp
            alignOptions.forEach { (iconRes, gravity) ->
                val btn = ImageView(requireContext()).apply {
                    setImageResource(iconRes)
                    val isActive = textAlign == gravity
                    val size = (40 * dp).toInt()
                    layoutParams = android.widget.LinearLayout.LayoutParams(size, size).apply {
                        setMargins((4*dp).toInt(), 0, (4*dp).toInt(), 0)
                    }
                    setBackgroundResource(if (isActive) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
                    imageTintList = android.content.res.ColorStateList.valueOf(if (isActive) p.accent else p.textMuted)
                    setPadding((8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt(), (8*dp).toInt())
                    setOnClickListener {
                        textAlign = gravity
                        binding.etPreview.gravity = gravity or Gravity.TOP
                        updateAlignIcon()
                        popup.dismiss()
                    }
                }
                popupView.addView(btn)
            }
            popup.showAsDropDown(anchor, 0, -(anchor.height + (48*dp).toInt()))
        }

        // ── Font color ────────────────────────────────────────────────────
        binding.btnFontColor.setOnClickListener {
            showHsvColorPicker("Font Color", fontColor) { picked ->
                fontColor = picked
                binding.etPreview.setTextColor(fontColor)
                binding.fontColorIndicator.setBackgroundColor(fontColor)
            }
        }

        // ── Background color ──────────────────────────────────────────────
        binding.btnBgColor.setOnClickListener {
            showHsvColorPicker("Background Color", bgColor) { picked ->
                bgColor = picked
                PreviewState.bgImageUri = null
                binding.ivBgImage.setImageDrawable(null)
                binding.ivBgImage.visibility = View.GONE
                binding.etPreview.setBackgroundColor(bgColor)
                binding.scrollPreview.setBackgroundColor(bgColor)
                binding.bgColorIndicator.setBackgroundColor(bgColor)
            }
        }

        // ── Background image ──────────────────────────────────────────────
        binding.btnBgImage.setOnClickListener { pickImage.launch("image/*") }

        // ── Reset ─────────────────────────────────────────────────────────
        binding.btnReset.setOnClickListener {
            PreviewState.isBold     = false
            PreviewState.isItalic   = false
            PreviewState.fontSize   = 32
            PreviewState.fontColor  = null
            PreviewState.bgColor    = null
            PreviewState.textAlign  = Gravity.START
            PreviewState.bgImageUri = null
            binding.ivBgImage.setImageDrawable(null)
            binding.ivBgImage.visibility = View.GONE
            applyAll()
        }

        // ── Capture ───────────────────────────────────────────────────────
        binding.btnCapture.setOnClickListener {
            capturePreview(font.effectiveMeta.family.ifEmpty { font.displayName })
        }

        // ── Sub-screens ───────────────────────────────────────────────────
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

        // ── Long press tooltips ───────────────────────────────────────────
        binding.btnInfo.setOnLongClickListener      { showTooltip(it, getString(R.string.tooltip_font_info));     true }
        binding.btnMeta.setOnLongClickListener      { showTooltip(it, getString(R.string.tooltip_font_metadata)); true }
        binding.btnGlyph.setOnLongClickListener     { showTooltip(it, getString(R.string.tooltip_glyph_map));     true }
        binding.btnDelete.setOnLongClickListener    { showTooltip(it, getString(R.string.tooltip_delete));        true }
        binding.btnFavorite.setOnLongClickListener  { showTooltip(it, getString(R.string.tooltip_favorite));      true }
        binding.btnAddToLibrary.setOnLongClickListener { showTooltip(it, getString(R.string.tooltip_add_library)); true }
        binding.btnReset.setOnLongClickListener     { showTooltip(it, getString(R.string.tooltip_reset));         true }
        binding.btnBold.setOnLongClickListener      { showTooltip(it, getString(R.string.tooltip_bold));          true }
        binding.btnItalic.setOnLongClickListener    { showTooltip(it, getString(R.string.tooltip_italic));        true }
        binding.btnAlign.setOnLongClickListener     { showTooltip(it, getString(R.string.tooltip_alignment));     true }
        binding.btnFontColor.setOnLongClickListener { showTooltip(it, getString(R.string.tooltip_text_color));    true }
        binding.btnBgColor.setOnLongClickListener   { showTooltip(it, getString(R.string.tooltip_bg_color));      true }
        binding.btnBgImage.setOnLongClickListener   { showTooltip(it, getString(R.string.tooltip_bg_image));      true }
        binding.btnCapture.setOnLongClickListener   { showTooltip(it, getString(R.string.tooltip_capture));       true }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun applyBgImage(uri: Uri) {
        val iv  = binding.ivBgImage
        val ref = binding.scrollPreview
        iv.visibility = View.VISIBLE
        binding.etPreview.setBackgroundColor(Color.TRANSPARENT)
        binding.scrollPreview.setBackgroundColor(Color.TRANSPARENT)
        fun load() {
            val w = ref.width.takeIf  { it > 0 } ?: return
            val h = ref.height.takeIf { it > 0 } ?: return
            try {
                val ctx   = requireContext()
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null, bounds)
                }
                var sample = 1; var sw = bounds.outWidth; var sh = bounds.outHeight
                while (sw / 2 >= w && sh / 2 >= h) { sample *= 2; sw /= 2; sh /= 2 }
                val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                    android.graphics.BitmapFactory.decodeStream(it, null,
                        android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
                } ?: return
                val scale   = maxOf(w.toFloat() / bmp.width, h.toFloat() / bmp.height)
                val scaledW = (bmp.width  * scale).toInt()
                val scaledH = (bmp.height * scale).toInt()
                val scaled  = Bitmap.createScaledBitmap(bmp, scaledW, scaledH, true)
                if (scaled !== bmp) bmp.recycle()
                val x = ((scaledW - w) / 2).coerceAtLeast(0)
                val y = ((scaledH - h) / 2).coerceAtLeast(0)
                val cropped = Bitmap.createBitmap(scaled, x, y,
                    minOf(w, scaled.width), minOf(h, scaled.height))
                if (cropped !== scaled) scaled.recycle()
                iv.scaleType = ImageView.ScaleType.FIT_XY
                iv.setImageBitmap(cropped)
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Could not load image", Toast.LENGTH_SHORT).show()
            }
        }
        if (ref.width > 0) load()
        else ref.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() { ref.viewTreeObserver.removeOnGlobalLayoutListener(this); load() }
        })
    }

    private fun capturePreview(fontName: String) {
        val ctx = requireContext()
        val container = binding.scrollPreview
        val w = container.width.coerceAtLeast(1)
        val h = container.height.coerceAtLeast(1)
        val bmp    = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (PreviewState.bgImageUri != null && binding.ivBgImage.visibility == View.VISIBLE) {
            val d = binding.ivBgImage.drawable
            if (d != null) {
                val scale = maxOf(w.toFloat() / d.intrinsicWidth, h.toFloat() / d.intrinsicHeight)
                val dw = (d.intrinsicWidth * scale).toInt(); val dh = (d.intrinsicHeight * scale).toInt()
                d.setBounds((w-dw)/2, (h-dh)/2, (w+dw)/2, (h+dh)/2); d.draw(canvas)
            }
        } else { canvas.drawColor(PreviewState.bgColor ?: ThemeManager.activePalette.bgPrimary) }
        binding.etPreview.draw(canvas)
        val safe = fontName.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(40)
        val ts   = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
        val name = "FontLens_${safe}_$ts.png"
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/FontLens")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                values.clear(); values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                ctx.contentResolver.update(uri, values, null, null)
            } else {
                val dir  = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "FontLens")
                dir.mkdirs()
                java.io.FileOutputStream(java.io.File(dir, name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                android.media.MediaScannerConnection.scanFile(ctx, arrayOf(java.io.File(dir, name).absolutePath), arrayOf("image/png"), null)
            }
            Toast.makeText(ctx, "Saved to Pictures/FontLens", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally { bmp.recycle() }
    }

    private fun showHsvColorPicker(title: String, initial: Int, onPick: (Int) -> Unit) {
        // Delegate to PreviewFragment's picker via a shared static helper
        HsvColorPicker.show(requireContext(), title, initial, onPick)
    }

    private fun openSubFragment(fragment: Fragment) {
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.preview_container, fragment)
            .addToBackStack(null).commit()
    }

    private fun showTooltip(anchor: View, text: String) {
        val ctx = requireContext(); val dp = ctx.resources.displayMetrics.density
        val tv  = android.widget.TextView(ctx).apply {
            this.text = text; textSize = 12f; setTextColor(Color.WHITE)
            setPadding((10*dp).toInt(), (5*dp).toInt(), (10*dp).toInt(), (5*dp).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(220, 30, 30, 30)); cornerRadius = 4f * dp
            }
        }
        val popup = PopupWindow(tv, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        popup.isOutsideTouchable = true; popup.isFocusable = false; popup.elevation = 8f * dp
        anchor.post {
            tv.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            popup.showAsDropDown(anchor, (anchor.width - tv.measuredWidth) / 2,
                -(anchor.height + tv.measuredHeight + (4*dp).toInt()))
        }
        anchor.postDelayed({ popup.dismiss() }, 1500)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
