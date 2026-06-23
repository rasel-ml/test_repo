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

        // Working HSV state
        val hsv = FloatArray(3)
        Color.colorToHSV(initial, hsv)
        var hue   = hsv[0]
        var sat   = hsv[1]
        var value = hsv[2]

        fun currentColor() = Color.HSVToColor(floatArrayOf(hue, sat, value))

        // Forward-reference holders
        var svViewRef:       View?                        = null
        var hueViewRef:      View?                        = null
        var prevBoxRef:      View?                        = null
        var currBoxRef:      View?                        = null
        var prevHexRef:      android.widget.TextView?     = null
        var currHexRef:      android.widget.TextView?     = null
        var hexInputRef:     android.widget.EditText?     = null
        var suppressHexSync  = false

        fun colorHex(c: Int) = String.format("#%06X", 0xFFFFFF and c)

        val updateAll: () -> Unit = {
            val color = currentColor()
            currBoxRef?.setBackgroundColor(color)
            currHexRef?.text = colorHex(color)
            if (!suppressHexSync) {
                hexInputRef?.let { et ->
                    suppressHexSync = true
                    et.setText(colorHex(color))
                    et.setSelection(et.text.length)
                    suppressHexSync = false
                }
            }
            svViewRef?.invalidate()
            hueViewRef?.invalidate()
        }

        val pad   = (12 * dp).toInt()
        val sqSz  = (0).toInt()   // will be set in onSizeChanged
        val hueW  = (20 * dp).toInt()
        val hueGap= (8  * dp).toInt()

        // ── SV Square ─────────────────────────────────────────────────────
        val svView = object : android.view.View(ctx) {
            var bmp: Bitmap? = null

            fun rebuild() {
                if (width < 1 || height < 1) return
                val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                val hueColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                // Sat: white → pure hue (horizontal)
                val satShader = LinearGradient(0f, 0f, width.toFloat(), 0f,
                    Color.WHITE, hueColor, Shader.TileMode.CLAMP)
                // Val: transparent → black (vertical, multiplied)
                val valShader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.shader = satShader
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.shader = valShader
                paint.xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
                c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
                paint.xfermode = null
                bmp = b
                invalidate()
            }

            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = rebuild()

            override fun onDraw(canvas: Canvas) {
                bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                val cx = sat * width
                val cy = (1f - value) * height
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.style = Paint.Style.STROKE
                // Outer white ring
                paint.strokeWidth = 3f * dp
                paint.color = Color.WHITE
                canvas.drawCircle(cx, cy, 9f * dp, paint)
                // Inner black ring
                paint.strokeWidth = 1.5f * dp
                paint.color = Color.BLACK
                canvas.drawCircle(cx, cy, 9f * dp, paint)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN ||
                    e.action == MotionEvent.ACTION_MOVE) {
                    sat   = (e.x / width).coerceIn(0f, 1f)
                    value = 1f - (e.y / height).coerceIn(0f, 1f)
                    updateAll()
                }
                return true
            }
        }
        svViewRef = svView

        // ── Hue bar ───────────────────────────────────────────────────────
        val hueView = object : android.view.View(ctx) {
            var bmp: Bitmap? = null

            fun rebuild() {
                if (width < 1 || height < 1) return
                val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val c = Canvas(b)
                val colors = IntArray(361) { i -> Color.HSVToColor(floatArrayOf(i.toFloat(), 1f, 1f)) }
                val shader = LinearGradient(0f, 0f, 0f, height.toFloat(),
                    colors, null, Shader.TileMode.CLAMP)
                val paint = Paint()
                paint.shader = shader
                val r = (4 * dp)
                c.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), r, r, paint)
                bmp = b
                invalidate()
            }

            override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) = rebuild()

            override fun onDraw(canvas: Canvas) {
                bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
                // Thumb: rounded rectangle indicator
                val y    = (hue / 360f * height).coerceIn(0f, height.toFloat())
                val th   = 6f * dp
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                canvas.drawRoundRect(RectF(1f * dp, y - th / 2, width - 1f * dp, y + th / 2),
                    3f * dp, 3f * dp, paint)
                paint.color = Color.argb(120, 0, 0, 0)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * dp
                canvas.drawRoundRect(RectF(1f * dp, y - th / 2, width - 1f * dp, y + th / 2),
                    3f * dp, 3f * dp, paint)
            }

            override fun onTouchEvent(e: MotionEvent): Boolean {
                if (e.action == MotionEvent.ACTION_DOWN ||
                    e.action == MotionEvent.ACTION_MOVE) {
                    hue = ((e.y / height) * 360f).coerceIn(0f, 360f)
                    svView.rebuild()
                    invalidate()
                    updateAll()
                }
                return true
            }
        }
        hueViewRef = hueView

        // ── Picker row: [SV square] [gap] [hue bar] ───────────────────────
        val pickerRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        val svLp  = android.widget.LinearLayout.LayoutParams(0, (220 * dp).toInt(), 1f)
        svView.layoutParams = svLp
        val hueLp = android.widget.LinearLayout.LayoutParams(hueW, (220 * dp).toInt())
        hueView.layoutParams = hueLp
        val gapV  = android.view.View(ctx).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(hueGap, 1)
        }
        pickerRow.addView(svView)
        pickerRow.addView(gapV)
        pickerRow.addView(hueView)

        // ── Hex row: [prev hex] [arrow] [curr hex] ────────────────────────
        val prevHex = android.widget.TextView(ctx).apply {
            text      = colorHex(initial)
            textSize  = 12f
            setTextColor(Color.argb(140, 80, 80, 80))
            gravity   = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        prevHexRef = prevHex

        val arrowHex = android.widget.TextView(ctx).apply {
            text      = "→"
            textSize  = 14f
            setTextColor(Color.argb(160, 80, 80, 80))
            gravity   = android.view.Gravity.CENTER
            setPadding((6 * dp).toInt(), 0, (6 * dp).toInt(), 0)
        }

        val currHex = android.widget.TextView(ctx).apply {
            text      = colorHex(initial)
            textSize  = 12f
            setTextColor(Color.argb(220, 20, 20, 20))
            gravity   = android.view.Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        currHexRef = currHex

        val hexLabelRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            setPadding(pad, (10 * dp).toInt(), pad, (4 * dp).toInt())
            addView(prevHex)
            addView(arrowHex)
            addView(currHex)
        }

        // ── Color preview boxes: [prev] [arrow] [curr] ────────────────────
        val boxH    = (44 * dp).toInt()
        val boxCorner = 8f * dp

        val prevBox = object : android.view.View(ctx) {
            override fun onDraw(canvas: Canvas) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = initial
                canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()),
                    boxCorner, boxCorner, paint)
                paint.color = Color.argb(30, 0, 0, 0)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * dp
                canvas.drawRoundRect(RectF(0.5f * dp, 0.5f * dp,
                    width - 0.5f * dp, height - 0.5f * dp), boxCorner, boxCorner, paint)
            }
        }.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, boxH, 1f)
        }
        prevBoxRef = prevBox

        val arrowBox = android.widget.TextView(ctx).apply {
            text      = "→"
            textSize  = 18f
            setTextColor(Color.argb(160, 80, 80, 80))
            gravity   = android.view.Gravity.CENTER
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }

        val currBox = object : android.view.View(ctx) {
            override fun onDraw(canvas: Canvas) {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                paint.color = currentColor()
                canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()),
                    boxCorner, boxCorner, paint)
                paint.color = Color.argb(30, 0, 0, 0)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f * dp
                canvas.drawRoundRect(RectF(0.5f * dp, 0.5f * dp,
                    width - 0.5f * dp, height - 0.5f * dp), boxCorner, boxCorner, paint)
            }
        }.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, boxH, 1f)
        }
        currBoxRef = currBox

        val previewRow = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity     = android.view.Gravity.CENTER_VERTICAL
            setPadding(pad, 0, pad, (10 * dp).toInt())
            addView(prevBox)
            addView(arrowBox)
            addView(currBox)
        }

        // ── Hex text input ─────────────────────────────────────────────────
        val hexInput = android.widget.EditText(ctx).apply {
            hint      = "#RRGGBB"
            setText(colorHex(initial))
            setSelection(text.length)
            setPadding(pad, (6 * dp).toInt(), pad, (6 * dp).toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines  = 1
            gravity   = android.view.Gravity.CENTER
            textSize  = 13f
            setTypeface(android.graphics.Typeface.MONOSPACE)
        }
        hexInputRef = hexInput

        hexInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                if (suppressHexSync) return
                val str = s.toString().trim().let { if (it.startsWith("#")) it else "#$it" }
                if (str.length == 7) {
                    try {
                        val parsed = Color.parseColor(str)
                        val h = FloatArray(3); Color.colorToHSV(parsed, h)
                        hue = h[0]; sat = h[1]; value = h[2]
                        suppressHexSync = true
                        svView.rebuild()
                        hueView.invalidate()
                        currBox.invalidate()
                        currHex.text = colorHex(currentColor())
                        suppressHexSync = false
                    } catch (_: Exception) {}
                }
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // ── Container ─────────────────────────────────────────────────────
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            addView(pickerRow)
            addView(hexLabelRow)
            addView(previewRow)
            addView(hexInput)
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
