package com.fontlens.ui.preview

import android.app.Dialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewFragment : Fragment() {

    private var _binding: FragmentPreviewBinding? = null
    private val binding get() = _binding!!
    private val args: PreviewFragmentArgs by navArgs()

    private lateinit var storageDeleteHelper: StorageDeleteHelper
    private var pendingDeleteFontId: String? = null

    // Preview state
    private var isBold      = false
    private var isItalic    = false
    private var fontSize    = DEFAULT_FONT_SIZE
    private var fontColor   = Color.TRANSPARENT   // TRANSPARENT = use theme default
    private var bgColor     = Color.TRANSPARENT   // TRANSPARENT = use theme default
    private var bgImageUri: Uri? = null

    companion object {
        const val DEFAULT_FONT_SIZE = 32
    }

    // Image picker
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Persist permission
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            bgImageUri = uri
            binding.ivBgImage.setImageURI(uri)
            binding.ivBgImage.visibility = View.VISIBLE
            // Clear solid bg color when image is set
            bgColor = Color.TRANSPARENT
            applyBgColor()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPreviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        binding.tvFontName.text = font.effectiveMeta.family.ifEmpty { font.displayName }
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        // Add to Library button
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

        binding.btnDelete.visibility =
            if (!tempMode || FontRepository.isInLibrary(font.id)) View.VISIBLE else View.GONE
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

        binding.btnFavorite.visibility =
            if (!tempMode || FontRepository.isInLibrary(font.id)) View.VISIBLE else View.GONE
        fun updateFav() {
            val fav = FontRepository.isFavorite(font.id)
            val p = ThemeManager.activePalette
            binding.btnFavorite.setImageResource(
                if (fav) R.drawable.ic_star else R.drawable.ic_star_outline)
            binding.btnFavorite.imageTintList =
                android.content.res.ColorStateList.valueOf(if (fav) p.accent else p.textMuted)
        }
        updateFav()
        binding.btnFavorite.setOnClickListener {
            FontRepository.toggleFavorite(font.id, requireContext()); updateFav()
        }

        // Navigation
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

        // Sample text
        binding.etPreview.setText(FontRepository.getSampleText(font))

        // Typeface
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

        if (TypefaceLoader.isLoaded(font.id)) {
            applyTypeface()
        } else {
            lifecycleScope.launch {
                TypefaceLoader.loadSingle(requireContext(), font.id, font.uri)
                if (_binding != null) applyTypeface()
            }
        }

        // ── Font size seekbar ────────────────────────────────────────────
        fontSize = DEFAULT_FONT_SIZE
        binding.seekbarSize.progress = fontSize - 8
        binding.tvSizeLabel.text = "${fontSize}px"
        binding.etPreview.textSize = fontSize.toFloat()
        binding.seekbarSize.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                fontSize = p + 8
                binding.tvSizeLabel.text = "${fontSize}px"
                binding.etPreview.animate().cancel()
                binding.etPreview.textSize = fontSize.toFloat()
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })

        // ── Bold / Italic ────────────────────────────────────────────────
        fun updateStyle() {
            val tf = TypefaceLoader.getTypeface(font.id) ?: return
            val style = when {
                isBold && isItalic -> android.graphics.Typeface.BOLD_ITALIC
                isBold             -> android.graphics.Typeface.BOLD
                isItalic           -> android.graphics.Typeface.ITALIC
                else               -> android.graphics.Typeface.NORMAL
            }
            binding.etPreview.setTypeface(tf, style)
            val p = ThemeManager.activePalette
            binding.btnBold.setTextColor(if (isBold) p.accent else p.textMuted)
            binding.btnItalic.setTextColor(if (isItalic) p.accent else p.textMuted)
            binding.btnBold.setBackgroundResource(
                if (isBold) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
            binding.btnItalic.setBackgroundResource(
                if (isItalic) R.drawable.bg_style_btn_active else R.drawable.bg_style_btn)
        }
        binding.btnBold.setOnClickListener   { isBold   = !isBold;   updateStyle() }
        binding.btnItalic.setOnClickListener { isItalic = !isItalic; updateStyle() }

        // ── Font color ───────────────────────────────────────────────────
        binding.btnFontColor.setOnClickListener {
            showColorPicker("Font Color") { color ->
                fontColor = color
                if (color == Color.TRANSPARENT) {
                    binding.etPreview.setTextColor(ThemeManager.activePalette.textPrimary)
                    binding.vFontColorSwatch.setBackgroundColor(ThemeManager.activePalette.textPrimary)
                } else {
                    binding.etPreview.setTextColor(color)
                    binding.vFontColorSwatch.setBackgroundColor(color)
                }
            }
        }

        // ── Background color ─────────────────────────────────────────────
        binding.btnBgColor.setOnClickListener {
            showColorPicker("Background Color") { color ->
                bgColor = color
                bgImageUri = null
                binding.ivBgImage.visibility = View.GONE
                applyBgColor()
                binding.vBgColorSwatch.setBackgroundColor(
                    if (color == Color.TRANSPARENT) Color.WHITE else color)
            }
        }

        // ── Background image ─────────────────────────────────────────────
        binding.btnBgImage.setOnClickListener {
            pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        // ── Capture ──────────────────────────────────────────────────────
        binding.btnCapture.setOnClickListener { captureCanvas() }

        // ── Reset ────────────────────────────────────────────────────────
        binding.btnReset.setOnClickListener {
            isBold    = false
            isItalic  = false
            fontSize  = DEFAULT_FONT_SIZE
            fontColor = Color.TRANSPARENT
            bgColor   = Color.TRANSPARENT
            bgImageUri = null

            binding.seekbarSize.progress = fontSize - 8
            binding.tvSizeLabel.text     = "${fontSize}px"
            binding.etPreview.textSize   = fontSize.toFloat()
            binding.etPreview.setTextColor(ThemeManager.activePalette.textPrimary)
            binding.ivBgImage.visibility = View.GONE
            binding.previewCanvas.background = null
            binding.vFontColorSwatch.setBackgroundColor(ThemeManager.activePalette.textPrimary)
            binding.vBgColorSwatch.setBackgroundColor(Color.WHITE)
            updateStyle()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun applyBgColor() {
        binding.previewCanvas.background = if (bgColor == Color.TRANSPARENT) null
            else ColorDrawable(bgColor)
    }

    private fun showColorPicker(title: String, onPick: (Int) -> Unit) {
        val themed = ContextThemeWrapper(requireContext(),
            ThemeManager.currentThemeResId(requireContext()))
        val dialogView = LayoutInflater.from(themed)
            .inflate(R.layout.dialog_color_picker, null)

        dialogView.findViewById<android.widget.TextView>(R.id.tv_picker_title).text = title

        val dialog = Dialog(themed, R.style.Theme_FontLens_Dialog)
        dialog.setContentView(dialogView)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setLayout(
                (requireContext().resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val swatchIds = listOf(
            R.id.swatch_black      to Color.parseColor("#000000"),
            R.id.swatch_white      to Color.parseColor("#FFFFFF"),
            R.id.swatch_red        to Color.parseColor("#F44336"),
            R.id.swatch_orange     to Color.parseColor("#FF9800"),
            R.id.swatch_yellow     to Color.parseColor("#FFEB3B"),
            R.id.swatch_green      to Color.parseColor("#4CAF50"),
            R.id.swatch_teal       to Color.parseColor("#009688"),
            R.id.swatch_blue       to Color.parseColor("#2196F3"),
            R.id.swatch_purple     to Color.parseColor("#9C27B0"),
            R.id.swatch_pink       to Color.parseColor("#E91E63"),
            R.id.swatch_gray_dark  to Color.parseColor("#424242"),
            R.id.swatch_gray       to Color.parseColor("#9E9E9E"),
            R.id.swatch_gray_light to Color.parseColor("#F5F5F5"),
            R.id.swatch_brown      to Color.parseColor("#795548"),
            R.id.swatch_transparent to Color.TRANSPARENT
        )

        swatchIds.forEach { (id, color) ->
            dialogView.findViewById<View>(id)?.setOnClickListener {
                onPick(color)
                dialog.dismiss()
            }
        }

        dialogView.findViewById<android.widget.TextView>(R.id.btn_picker_cancel)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun captureCanvas() {
        val canvas = binding.previewCanvas
        val bitmap = Bitmap.createBitmap(canvas.width, canvas.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        // Fill background
        c.drawColor(if (bgColor != Color.TRANSPARENT) bgColor else Color.WHITE)
        canvas.draw(c)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val fname = "FontLens_${System.currentTimeMillis()}.png"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, fname)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/FontLens")
                }
                val resolver = requireContext().contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw Exception("Could not create media entry")
                resolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                withContext(Dispatchers.Main) {
                    if (_binding != null)
                        Toast.makeText(requireContext(), "Saved to Pictures/FontLens", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (_binding != null)
                        Toast.makeText(requireContext(), "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
