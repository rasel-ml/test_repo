package com.fontlens.ui.preview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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

// ── Persistent preview state (survives navigation, cleared on app restart) ────

object PreviewState {
    var isBold:    Boolean = false
    var isItalic:  Boolean = false
    var fontSize:  Int     = 32
    var fontColor: Int?    = null   // null = use theme default
    var bgColor:   Int?    = null   // null = use theme default
}

// ── Fragment ──────────────────────────────────────────────────────────────────

class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!
    private val args: PreviewFragmentArgs by navArgs()

    private lateinit var storageDeleteHelper: StorageDeleteHelper
    private var pendingDeleteFontId: String? = null

    // Local working copies — written back to PreviewState on every change
    private var isBold    get() = PreviewState.isBold;    set(v) { PreviewState.isBold    = v }
    private var isItalic  get() = PreviewState.isItalic;  set(v) { PreviewState.isItalic  = v }
    private var fontSize  get() = PreviewState.fontSize;  set(v) { PreviewState.fontSize  = v }
    private var fontColor get() = PreviewState.fontColor  ?: ThemeManager.activePalette.textPrimary
                          set(v) { PreviewState.fontColor = v }
    private var bgColor   get() = PreviewState.bgColor    ?: ThemeManager.activePalette.bgPrimary
                          set(v) { PreviewState.bgColor   = v }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val p = ThemeManager.activePalette

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

        // ── Header ────────────────────────────────────────────────────────
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

        // ── Navigation ────────────────────────────────────────────────────
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

        if (TypefaceLoader.isLoaded(font.id)) applyTypeface()
        else lifecycleScope.launch {
            TypefaceLoader.loadSingle(requireContext(), font.id, font.uri)
            if (_binding != null) applyTypeface()
        }

        // ── Apply all persistent state ────────────────────────────────────
        fun applyAll() {
            // Size
            binding.tvSizeLabel.text   = "${fontSize}px"
            binding.etPreview.textSize = fontSize.toFloat()
            binding.seekbarSize.progress = (fontSize - 8).coerceIn(0, 152)
            // Colors
            binding.etPreview.setTextColor(fontColor)
            binding.etPreview.setBackgroundColor(bgColor)
            binding.scrollPreview.setBackgroundColor(bgColor)
            binding.fontColorIndicator.setBackgroundColor(fontColor)
            binding.bgColorIndicator.setBackgroundColor(bgColor)
            // Bold / Italic
            binding.btnBold.setTextColor(if (isBold) p.accent else p.textMuted)
            binding.btnItalic.setTextColor(if (isItalic) p.accent else p.textMuted)
            binding.btnBold.setBackgroundResource(
                if (isBold) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
            binding.btnItalic.setBackgroundResource(
                if (isItalic) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
            applyTypeface()
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

        // ── Color pickers ─────────────────────────────────────────────────
        binding.btnFontColor.setOnClickListener {
            showHsvColorPicker("Font Color", fontColor) { picked ->
                fontColor = picked
                binding.etPreview.setTextColor(fontColor)
                binding.scrollPreview.setBackgroundColor(bgColor)
                binding.fontColorIndicator.setBackgroundColor(fontColor)
            }
        }
        binding.btnBgColor.setOnClickListener {
            showHsvColorPicker("Background Color", bgColor) { picked ->
                bgColor = picked
                binding.etPreview.setBackgroundColor(bgColor)
                binding.scrollPreview.setBackgroundColor(bgColor)
                binding.bgColorIndicator.setBackgroundColor(bgColor)
            }
        }

        // ── Reset ─────────────────────────────────────────────────────────
        binding.btnReset.setOnClickListener {
            PreviewState.isBold    = false
            PreviewState.isItalic  = false
            PreviewState.fontSize  = 32
            PreviewState.fontColor = null
            PreviewState.bgColor   = null
            applyAll()
        }
    }

    // ── HSV Color Picker ──────────────────────────────────────────────────────
    //
    // Layout: [  SV square  ] [ Hue bar ]
    // - Hue bar: vertical gradient strip on the right, tap/drag to choose hue
    // - SV square: left large area, X = saturation (0→1), Y = value (1→0)
    // - Preview strip at top showing current color
    // - Hex input at bottom

    @SuppressLint("ClickableViewAccessibility")
    private fun showHsvColorPicker(title: String, initial: Int, onPick: (Int) -> Unit) {
        val ctx = requireContext()
        val dp  = ctx.resources.displayMetrics.density

        // Working HSV state
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        var hue = hsv[0]
        var sat = hsv[1]
        var value = hsv[2]

        fun currentColor() = Color.HSVToColor(floatArrayOf(hue, sat, value))

        // ── SV Square canvas view ─────────────────────────────────────────
        val svSize = (260 * dp).toInt()

        val svView = object : View(ctx) {
            var bitmap: Bitmap? = null

            fun rebuild() {
                val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                // White→hue gradient (saturation axis, horizontal)
                val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                val satShader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                    Color.WHITE, hueColor, Shader.TileMode.CLAMP)
                // Transparent→black gradient (value axis, vertical)
                val valShader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
                val paint = Paint()
                paint.shader = satShader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = valShader
                paint.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.xfermode = null
                bitmap = bmp
                invalidate()
            }

            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rebuild() }

            override fun onDraw(canvas: Canvas) {
                bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                // Draw crosshair
                val cx = sat * width
                val cy = (1f - value) * height
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2f * dp
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, 10f * dp, paint)
                paint.color = Color.BLACK
                paint.strokeWidth = 1f * dp
                canvas.drawCircle(cx, cy, 10f * dp, paint)
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(e: MotionEvent): Boolean {
                sat   = (e.x / width).coerceIn(0f, 1f)
                value = 1f - (e.y / height).coerceIn(0f, 1f)
                invalidate()
                updateAll()
                return true
            }
        }
        svView.layoutParams = ViewGroup.LayoutParams(svSize, svSize)

        // ── Hue bar ───────────────────────────────────────────────────────
        val hueBarWidth  = (28 * dp).toInt()
        val hueBarHeight = svSize

        val hueView = object : View(ctx) {
            var bitmap: Bitmap? = null

            fun rebuild() {
                val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bmp)
                val colors = IntArray(361) { i -> Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f)) }
                val shader = LinearGradient(0f, 0f, 0f, height.toFloat(), colors, null, Shader.TileMode.CLAMP)
                val paint = Paint()
                paint.shader = shader
                canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                bitmap = bmp
                invalidate()
            }

            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rebuild() }

            override fun onDraw(canvas: Canvas) {
                bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                // Indicator line
                val y = hue / 360f * height
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = Color.WHITE
                paint.strokeWidth = 2.5f * dp
                canvas.drawLine(0f, y, width.toFloat(), y, paint)
                paint.color = Color.BLACK
                paint.strokeWidth = 1f * dp
                canvas.drawLine(0f, y, width.toFloat(), y, paint)
            }

            @SuppressLint("ClickableViewAccessibility")
            override fun onTouchEvent(e: MotionEvent): Boolean {
                hue = ((e.y / height) * 360f).coerceIn(0f, 360f)
                svView.rebuild()
                invalidate()
                updateAll()
                return true
            }
        }
        hueView.layoutParams = ViewGroup.LayoutParams(hueBarWidth, hueBarHeight)

        // ── Preview bar ───────────────────────────────────────────────────
        val previewBar = View(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (40 * dp).toInt())
            setBackgroundColor(initial)
        }

        // ── Hex input ─────────────────────────────────────────────────────
        val pad = (12 * dp).toInt()
        val hexInput = android.widget.EditText(ctx).apply {
            hint      = "#RRGGBB"
            setText(String.format("#%06X", 0xFFFFFF and initial))
            setPadding(pad, (6 * dp).toInt(), pad, (6 * dp).toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines  = 1
        }

        // ── Update all views when color changes ────────────────────────────
        fun updateAll() {
            val color = currentColor()
            previewBar.setBackgroundColor(color)
            hexInput.setText(String.format("#%06X", 0xFFFFFF and color))
        }

        // Store reference so touch handlers can call it
        val updateAllRef = ::updateAll

        // ── Picker row: [SV square] [gap] [hue bar] ───────────────────────
        val pickerRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, (6 * dp).toInt())
        }
        pickerRow.addView(svView)
        val gap = View(ctx).apply { layoutParams = ViewGroup.LayoutParams((10 * dp).toInt(), 1) }
        pickerRow.addView(gap)
        pickerRow.addView(hueView)

        // ── Container ─────────────────────────────────────────────────────
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(previewBar)
            addView(pickerRow)
            addView(hexInput)
        }

        // Sync hex input → HSV
        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val str = s.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                if (str.length == 7) {
                    try {
                        val parsed = Color.parseColor(str)
                        val h = FloatArray(3)
                        Color.colorToHSV(parsed, h)
                        hue   = h[0]; sat = h[1]; value = h[2]
                        svView.rebuild()
                        hueView.invalidate()
                        previewBar.setBackgroundColor(parsed)
                    } catch (_: Exception) {}
                }
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        android.app.AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Apply") { _, _ -> onPick(currentColor()) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
