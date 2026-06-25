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

        // ── Capture ───────────────────────────────────────────────────────
        binding.btnCapture.setOnClickListener {
            capturePreview(font.effectiveMeta.family.ifEmpty { font.displayName })
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

        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        var hue   = hsv[0]
        var sat   = hsv[1]
        var value = hsv[2]

        fun currentColor() = Color.HSVToColor(floatArrayOf(hue, sat, value))
        fun colorHex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

        // Forward refs
        var svRebuildRef: (() -> Unit)?            = null
        var hueViewRef:   android.view.View?       = null
        var currBoxRef:   android.view.View?       = null
        var currEditRef:  android.widget.EditText? = null
        var currBgRef:    android.graphics.drawable.GradientDrawable? = null
        var suppressSync  = false

        val updateAll: () -> Unit = {
            val color = currentColor()
            currBgRef?.setColor(color)
            currBoxRef?.invalidate()
            if (!suppressSync) {
                currEditRef?.let {
                    suppressSync = true
                    it.setText(colorHex(color))
                    it.setSelection(it.text.length)
                    suppressSync = false
                }
            }
            svRebuildRef?.invoke()
            hueViewRef?.invalidate()
        }

        val squareSz = (220 * dp).toInt()
        val pad    = (12 * dp).toInt()
        val hueW   = (22 * dp).toInt()
        val hueGap = (8  * dp).toInt()
        val corner = 6f * dp

        // ── SV Square ─────────────────────────────────────────────────────
        val svView = object : android.view.View(ctx) {
            var bmp: Bitmap? = null

            fun rebuild() {
                if (width < 1 || height < 1) return
                val b  = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val c  = Canvas(b)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)

                // Layer 1: white → pure hue color (saturation, left→right)
                val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                paint.shader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                    Color.WHITE, hueColor, Shader.TileMode.CLAMP)
                paint.xfermode = null
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

                // Layer 2: transparent → opaque black (value, top→bottom)
                // Use SRC_OVER not MULTIPLY: draw semi-transparent black gradient on top
                paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
                paint.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.xfermode = null; paint.shader = null
                bmp = b; invalidate()
            }

            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = rebuild()

            override fun onDraw(canvas: Canvas) {
                bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                val cx = sat * width
                val cy = (1f - value) * height
                val p  = Paint(Paint.ANTI_ALIAS_FLAG)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 3f * dp; p.color = Color.WHITE
                canvas.drawCircle(cx, cy, 9f * dp, p)
                p.strokeWidth = 1.5f * dp; p.color = Color.BLACK
                canvas.drawCircle(cx, cy, 9f * dp, p)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                    sat   = (e.x / width).coerceIn(0f, 1f)
                    value = 1f - (e.y / height).coerceIn(0f, 1f)
                    updateAll()
                }
                return true
            }
        }.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(squareSz, squareSz)
        }
        svRebuildRef = svView::rebuild

        // ── Hue bar ───────────────────────────────────────────────────────
        val hueView = object : android.view.View(ctx) {
            var bmp: Bitmap? = null

            fun rebuild() {
                if (width < 1 || height < 1) return
                val b  = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val c  = Canvas(b)
                val colors = IntArray(361) { i -> Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f)) }
                val paint  = Paint()
                paint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    colors, null, Shader.TileMode.CLAMP)
                c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), corner, corner, paint)
                bmp = b; invalidate()
            }

            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = rebuild()

            override fun onDraw(canvas: Canvas) {
                bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                val y  = (hue / 360f * height).coerceIn(0f, height.toFloat())
                val th = 5f * dp
                val p  = Paint(Paint.ANTI_ALIAS_FLAG)
                p.style = Paint.Style.FILL; p.color = Color.WHITE
                canvas.drawRoundRect(RectF(1f * dp, y - th, width - 1f * dp, y + th),
                    2f * dp, 2f * dp, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1f * dp
                p.color = Color.argb(100, 0, 0, 0)
                canvas.drawRoundRect(RectF(1f * dp, y - th, width - 1f * dp, y + th),
                    2f * dp, 2f * dp, p)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
                    hue = ((e.y / height) * 360f).coerceIn(0f, 360f)
                    svView.rebuild()
                    invalidate()
                    updateAll()
                }
                return true
            }
        }
        hueViewRef = hueView

        // ── Picker row: centered, SV square + gap + hue bar ───────────────
        val pickerRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER
            setPadding(pad, pad, pad, pad)
        }
        // SV already has fixed squareSz × squareSz layout params set above
        // Hue bar same height as square
        hueView.layoutParams = android.widget.LinearLayout.LayoutParams(hueW, squareSz)
        val gapV = android.view.View(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(hueGap, 1)
        }
        pickerRow.addView(svView)
        pickerRow.addView(gapV)
        pickerRow.addView(hueView)

        // ── Color preview boxes with hex inside ────────────────────────────
        // Each box is a custom view: colored bg, grey outline, hex label in center.
        // ── Color preview boxes ────────────────────────────────────────────
        // Both boxes wrap their hex text — no fixed minimum sizes.
        // Previous box: static canvas draw (color + grey outline + hex label with outline)
        // Current box: EditText with a custom background GradientDrawable updated on change

        val boxPadH = (10 * dp).toInt()
        val boxPadV = (6  * dp).toInt()
        val hexTextSizeSp = 12f   // same sp value for both boxes
        val hexTextSizePx = hexTextSizeSp * dp  // px for canvas Paint

        // Previous box — static View, draws bg + outline + hex text
        val prevBox = object : android.view.View(ctx) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize  = hexTextSizePx
                typeface  = android.graphics.Typeface.MONOSPACE
                textAlign = Paint.Align.CENTER
            }
            override fun onMeasure(wSpec: Int, hSpec: Int) {
                val hex   = colorHex(initial)
                val tw    = textPaint.measureText(hex).toInt() + boxPadH * 2
                val th    = (-textPaint.ascent() + textPaint.descent()).toInt() + boxPadV * 2
                setMeasuredDimension(tw, th)
            }
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                // Fill
                p.color = initial; p.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(0f, 0f, w, h), corner, corner, p)
                // Grey outline
                p.color = Color.argb(140, 128, 128, 128)
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f * dp
                canvas.drawRoundRect(RectF(0.6f * dp, 0.6f * dp, w - 0.6f * dp, h - 0.6f * dp), corner, corner, p)
                // Hex text: dark shadow then white
                val hex = colorHex(initial)
                val tx  = w / 2f
                val ty  = h / 2f - (textPaint.ascent() + textPaint.descent()) / 2f
                textPaint.color = Color.argb(150, 0, 0, 0)
                canvas.drawText(hex, tx + 1f * dp, ty + 1f * dp, textPaint)
                textPaint.color = Color.WHITE
                canvas.drawText(hex, tx, ty, textPaint)
            }
        }.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val arrowBox = android.widget.TextView(ctx).apply {
            text      = "→"
            textSize  = 14f
            setTextColor(Color.argb(160, 80, 80, 80))
            gravity   = android.view.Gravity.CENTER
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        // Current box — EditText with colored rounded-rect GradientDrawable background
        val currBg = android.graphics.drawable.GradientDrawable().apply {
            shape        = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = corner
            setColor(currentColor())
            setStroke((1.2f * dp).toInt(), Color.argb(140, 128, 128, 128))
        }
        currBgRef = currBg

        val currEdit = android.widget.EditText(ctx).apply {
            setText(colorHex(initial))
            textSize  = hexTextSizeSp
            setTypeface(android.graphics.Typeface.MONOSPACE)
            gravity   = android.view.Gravity.CENTER
            setTextColor(Color.WHITE)
            setShadowLayer(1.5f * dp, 0.8f * dp, 0.8f * dp, Color.argb(160, 0, 0, 0))
            background = currBg
            maxLines   = 1
            // Limit to exactly 7 chars: # + 6 hex digits
            inputType  = android.text.InputType.TYPE_CLASS_TEXT or
                         android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            filters    = arrayOf(android.text.InputFilter.LengthFilter(7))
            setPadding(boxPadH, boxPadV, boxPadH, boxPadV)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        currBoxRef  = currEdit
        currEditRef = currEdit

        currEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppressSync) return
                val str = s.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                if (str.length == 7) {
                    try {
                        val parsed = Color.parseColor(str)
                        val h = FloatArray(3); Color.colorToHSV(parsed, h)
                        hue = h[0]; sat = h[1]; value = h[2]
                        suppressSync = true
                        currBgRef?.setColor(currentColor())
                        svView.rebuild()
                        hueView.invalidate()
                        currEdit.invalidate()
                        suppressSync = false
                    } catch (_: Exception) {}
                }
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        val previewRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(prevBox)
            addView(arrowBox)
            addView(currEdit)
        }

        // ── Container ─────────────────────────────────────────────────────
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(pickerRow)
            addView(previewRow)
        }

        android.app.AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(container)
            .setPositiveButton("Apply") { _, _ -> onPick(currentColor()) }
            .setNegativeButton("Cancel", null)
            .show()
    }


    // ── Capture preview to PNG ────────────────────────────────────────────────

    private fun capturePreview(fontName: String) {
        val ctx = requireContext()

        // Draw the EditText content onto a Bitmap
        val target = binding.etPreview
        val bmp = Bitmap.createBitmap(
            target.width.coerceAtLeast(1),
            target.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        canvas.drawColor(bgColor)
        target.draw(canvas)

        // Sanitize font name for filename
        val safe = fontName.replace(Regex("[^A-Za-z0-9_\\-]"), "_").take(40)
        val timestamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val fileName = "FontLens_${safe}_$timestamp.png"

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ — use MediaStore, no permission needed
                val values = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                        android.os.Environment.DIRECTORY_PICTURES + "/FontLens")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = ctx.contentResolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("MediaStore insert failed")

                ctx.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                values.clear()
                values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                ctx.contentResolver.update(uri, values, null, null)
            } else {
                // Android 9 and below — write directly to Pictures/FontLens
                val dir = java.io.File(
                    android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES), "FontLens")
                dir.mkdirs()
                val file = java.io.File(dir, fileName)
                java.io.FileOutputStream(file).use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                // Notify gallery
                android.media.MediaScannerConnection.scanFile(
                    ctx, arrayOf(file.absolutePath), arrayOf("image/png"), null)
            }

            Toast.makeText(ctx, "Saved to Pictures/FontLens", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        } finally {
            bmp.recycle()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
