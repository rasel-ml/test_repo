package com.fontlens.ui.glyph

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fontlens.databinding.ItemGlyphCellBinding
import com.fontlens.utils.ThemeManager

class GlyphAdapter(
    private var typeface: Typeface?,
    private var presentSet: Set<Int> = emptySet(),
    private var showAll: Boolean = false
) : ListAdapter<Int, GlyphAdapter.VH>(DIFF) {

    fun update(
        codepoints: List<Int>,
        present: Set<Int>,
        showAllMode: Boolean,
        tf: Typeface?
    ) {
        presentSet = present
        showAll    = showAllMode
        typeface   = tf
        submitList(codepoints)
    }

    inner class VH(val b: ItemGlyphCellBinding) : RecyclerView.ViewHolder(b.root)

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<Int>() {
            override fun areItemsTheSame(a: Int, b: Int) = a == b
            override fun areContentsTheSame(a: Int, b: Int) = a == b
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemGlyphCellBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val cp  = getItem(position)
        val ch  = try { String(Character.toChars(cp)) } catch (e: Exception) { "" }
        val b   = holder.b
        val ctx = b.root.context
        val p   = ThemeManager.activePalette
        val isPresent = presentSet.contains(cp)

        b.tvGlyph.text     = ch
        b.tvGlyph.typeface = typeface ?: Typeface.DEFAULT
        b.tvCodepoint.text = "U+${cp.toString(16).uppercase().padStart(4, '0')}"

        // Shadow coloring: present = accent glow, absent (show-all mode) = red glow
        if (showAll) {
            if (isPresent) {
                val r = Color.red(p.accent); val g = Color.green(p.accent); val bl = Color.blue(p.accent)
                b.tvGlyph.setShadowLayer(8f, 0f, 0f, Color.argb(180, r, g, bl))
                b.tvGlyph.setTextColor(p.textPrimary)
                b.tvCodepoint.setTextColor(p.textMuted)
                b.root.alpha = 1f
            } else {
                b.tvGlyph.setShadowLayer(6f, 0f, 0f, Color.argb(160, 220, 60, 60))
                b.tvGlyph.setTextColor(Color.argb(120, 220, 60, 60))
                b.tvCodepoint.setTextColor(Color.argb(80, 200, 60, 60))
                b.root.alpha = 0.7f
            }
        } else {
            // Normal mode — no red glyphs, all shown are present
            val r = Color.red(p.accent); val g = Color.green(p.accent); val bl = Color.blue(p.accent)
            b.tvGlyph.setShadowLayer(6f, 0f, 0f, Color.argb(100, r, g, bl))
            b.tvGlyph.setTextColor(p.textPrimary)
            b.tvCodepoint.setTextColor(p.textMuted)
            b.root.alpha = 1f
        }

        // Single tap — copy character
        b.root.setOnClickListener {
            if (ch.isNotEmpty()) {
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("glyph", ch))
                Toast.makeText(ctx, "Copied: $ch", Toast.LENGTH_SHORT).show()
            }
        }

        // Long press — copy unicode value
        b.root.setOnLongClickListener {
            val uni = "U+${cp.toString(16).uppercase().padStart(4, '0')}"
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("unicode", uni))
            Toast.makeText(ctx, "Copied: $uni", Toast.LENGTH_SHORT).show()
            true
        }
    }
}
