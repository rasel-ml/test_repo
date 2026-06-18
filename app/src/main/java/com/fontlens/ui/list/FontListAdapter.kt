package com.fontlens.ui.list

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fontlens.R
import com.fontlens.data.FontItem
import com.fontlens.data.FontListItem
import com.fontlens.data.FontRepository
import com.fontlens.data.scriptDisplayName
import com.fontlens.databinding.ItemFontCardBinding
import com.fontlens.databinding.ItemFolderHeaderBinding
import com.fontlens.utils.ThemeManager
import com.fontlens.utils.TypefaceLoader

class FontListAdapter(
    private val onFontClick: (FontItem) -> Unit,
    private val onFavoriteClick: (FontItem) -> Unit,
    private val onRemoveClick: (FontItem) -> Unit,
    private val isFavorite: (String) -> Boolean,
    private val getSample: (FontItem) -> String,
    private val onSelectionChanged: (Set<String>) -> Unit
) : ListAdapter<FontListItem, RecyclerView.ViewHolder>(DIFF) {

    private val selected = mutableSetOf<String>()
    var selectionMode = false
        private set

    /** Remembers which script chip is active per font id */
    private val activeScriptIndex = mutableMapOf<String, Int>()

    fun exitSelectionMode() {
        selectionMode = false; selected.clear()
        notifyDataSetChanged(); onSelectionChanged(emptySet())
    }

    fun selectAll(items: List<FontItem>) {
        items.forEach { selected.add(it.id) }
        onSelectionChanged(selected.toSet()); notifyDataSetChanged()
    }

    fun getSelectedIds() = selected.toSet()

    fun notifyTypefaceReady(fontId: String) {
        val idx = currentList.indexOfFirst { it is FontListItem.Font && it.font.id == fontId }
        if (idx >= 0) notifyItemChanged(idx, "typeface")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty() && payloads[0] == "typeface" && holder is FontVH) {
            val item = getItem(position)
            if (item is FontListItem.Font) {
                holder.binding.tvPreviewLarge.typeface =
                    TypefaceLoader.getTypeface(item.font.id) ?: Typeface.DEFAULT
                holder.binding.root.alpha = 1f
            }
        } else super.onBindViewHolder(holder, position, payloads)
    }

    companion object {
        private const val TYPE_FONT   = 0
        private const val TYPE_HEADER = 1
        val DIFF = object : DiffUtil.ItemCallback<FontListItem>() {
            override fun areItemsTheSame(a: FontListItem, b: FontListItem) = when {
                a is FontListItem.Font   && b is FontListItem.Font   -> a.font.id == b.font.id
                a is FontListItem.FolderHeader && b is FontListItem.FolderHeader -> a.path == b.path
                else -> false
            }
            override fun areContentsTheSame(a: FontListItem, b: FontListItem) = a == b
        }
    }

    override fun getItemViewType(pos: Int) = when (getItem(pos)) {
        is FontListItem.Font         -> TYPE_FONT
        is FontListItem.FolderHeader -> TYPE_HEADER
    }

    inner class FontVH(val binding: ItemFontCardBinding)   : RecyclerView.ViewHolder(binding.root)
    inner class HeaderVH(val binding: ItemFolderHeaderBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_FONT)
            FontVH(ItemFontCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        else
            HeaderVH(ItemFolderHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is FontListItem.FolderHeader -> (holder as HeaderVH).binding.tvFolderHeader.text = item.path
            is FontListItem.Font         -> bindFont(holder as FontVH, item.font)
        }
    }

    // ── Main bind ─────────────────────────────────────────────────────────

    private fun bindFont(holder: FontVH, font: FontItem) {
        val b   = holder.binding
        val m   = font.effectiveMeta
        val p   = ThemeManager.activePalette
        val ctx = holder.itemView.context
        val dp  = ctx.resources.displayMetrics.density

        // Font name
        val showFull = FontRepository.settings.showFullFontName
        b.tvFontName.text = if (showFull) m.fullName.ifEmpty { m.family.ifEmpty { font.displayName } }
                            else m.family.ifEmpty { font.displayName }

        // Subtitle
        val sub = buildString {
            if (m.weightName.isNotEmpty() && m.weightName != "Regular") append(m.weightName)
            if (m.subfamily.isNotEmpty() && m.subfamily != "Regular") {
                if (isNotEmpty()) append(" · "); append(m.subfamily)
            }
        }
        b.tvFontSub.text = sub
        b.tvFontSub.visibility = if (sub.isBlank()) View.GONE else View.VISIBLE

        // Ordered script codes for this font
        val orderedCodes = FontRepository.orderedScriptCodesForFont(font)
        val safeActive   = activeScriptIndex.getOrDefault(font.id, 0)
            .coerceIn(0, (orderedCodes.size - 1).coerceAtLeast(0))
        activeScriptIndex[font.id] = safeActive

        // Build chip row
        buildChipRow(ctx, b, font, orderedCodes, safeActive, p, dp)

        // Typeface
        val tf = TypefaceLoader.getTypeface(font.id) ?: Typeface.DEFAULT
        b.root.alpha = if (TypefaceLoader.isLoaded(font.id)) 1f else 0.6f

        // Preview text
        applyPreview(b, font, orderedCodes, safeActive, tf)

        // Cycle button (only if >1 script)
        if (orderedCodes.size > 1) {
            b.btnCycleScript.visibility = View.VISIBLE
            b.btnCycleScript.setOnClickListener {
                val next = ((activeScriptIndex[font.id] ?: 0) + 1) % orderedCodes.size
                activeScriptIndex[font.id] = next
                val newTf = TypefaceLoader.getTypeface(font.id) ?: Typeface.DEFAULT
                applyPreview(b, font, orderedCodes, next, newTf)
                refreshChipStates(ctx, b, orderedCodes, next, p, dp)
                scrollChipIntoView(b, next)
            }
        } else {
            b.btnCycleScript.visibility = View.GONE
        }

        // Selection
        val isSelected = selected.contains(font.id)
        b.root.strokeColor = if (isSelected) p.accent else p.divider
        b.root.strokeWidth = if (isSelected) 2 else 1

        // Favorite
        b.btnFavorite.setImageResource(if (isFavorite(font.id)) R.drawable.ic_star else R.drawable.ic_star_outline)
        b.btnFavorite.imageTintList = android.content.res.ColorStateList.valueOf(
            if (isFavorite(font.id)) p.accent else p.textMuted)
        b.btnFavorite.visibility = if (selectionMode) View.GONE else View.VISIBLE
        b.btnFavorite.setOnClickListener { if (!selectionMode) onFavoriteClick(font) }

        b.btnRemove.visibility = if (selectionMode) View.GONE else View.VISIBLE
        b.btnRemove.setOnClickListener { if (!selectionMode) onRemoveClick(font) }

        b.root.setOnClickListener {
            if (selectionMode) {
                if (selected.contains(font.id)) selected.remove(font.id) else selected.add(font.id)
                onSelectionChanged(selected.toSet()); notifyItemChanged(holder.adapterPosition)
            } else onFontClick(font)
        }
        b.root.setOnLongClickListener {
            if (!selectionMode) {
                selectionMode = true; selected.add(font.id)
                onSelectionChanged(selected.toSet()); notifyDataSetChanged()
            }; true
        }
    }

    // ── Chip row ──────────────────────────────────────────────────────────

    private fun buildChipRow(
        ctx: Context,
        b: ItemFontCardBinding,
        font: FontItem,
        codes: List<String>,
        activeIdx: Int,
        p: ThemeManager.Palette,
        dp: Float
    ) {
        if (codes.isEmpty()) { b.hsvScriptChips.visibility = View.GONE; return }
        b.hsvScriptChips.visibility = View.VISIBLE
        b.llScriptChips.removeAllViews()

        codes.forEachIndexed { idx, code ->
            val hasSample = FontRepository.getSampleForLang(code) != null
            val chip = makeChip(ctx, code, idx == activeIdx, hasSample, dp, p)
            chip.setOnClickListener {
                activeScriptIndex[font.id] = idx
                val tf = TypefaceLoader.getTypeface(font.id) ?: Typeface.DEFAULT
                applyPreview(b, font, codes, idx, tf)
                refreshChipStates(ctx, b, codes, idx, p, dp)
                scrollChipIntoView(b, idx)
            }
            b.llScriptChips.addView(chip)
        }
    }

    private fun makeChip(
        ctx: Context,
        code: String,
        isActive: Boolean,
        hasSample: Boolean,
        dp: Float,
        p: ThemeManager.Palette
    ): TextView {
        val chip = TextView(ctx)
        // Use full script name in UPPERCASE
        chip.text      = scriptDisplayName(code).uppercase()
        chip.textSize  = 12f
        chip.letterSpacing = 0.05f

        val padH = (10f * dp).toInt(); val padV = (5f * dp).toInt()
        chip.setPadding(padH, padV, padH, padV)
        val lp = ViewGroup.MarginLayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.marginEnd = (6f * dp).toInt()
        chip.layoutParams = lp

        styleChip(chip, isActive, hasSample, dp, p)
        return chip
    }

    private fun styleChip(
        chip: TextView,
        isActive: Boolean,
        hasSample: Boolean,
        dp: Float,
        p: ThemeManager.Palette
    ) {
        val bg = GradientDrawable()
        bg.shape = GradientDrawable.RECTANGLE
        bg.cornerRadius = 20f * dp

        when {
            isActive && hasSample -> {
                bg.setColor(p.accent)
                bg.setStroke((1.5f * dp).toInt(), p.accent)
                chip.setTextColor(Color.WHITE)
                chip.alpha = 1f
            }
            isActive && !hasSample -> {
                // Active but no sample — pale accent
                bg.setColor(Color.argb(35, Color.red(p.accent), Color.green(p.accent), Color.blue(p.accent)))
                bg.setStroke((1.5f * dp).toInt(), p.accent)
                chip.setTextColor(p.accent)
                chip.alpha = 0.55f
            }
            !isActive && hasSample -> {
                bg.setColor(Color.TRANSPARENT)
                bg.setStroke((1f * dp).toInt(), p.border)
                chip.setTextColor(p.textMuted)
                chip.alpha = 1f
            }
            else -> { // inactive + no sample
                bg.setColor(Color.TRANSPARENT)
                bg.setStroke((1f * dp).toInt(), p.divider)
                chip.setTextColor(p.textMuted)
                chip.alpha = 0.35f
            }
        }
        chip.background = bg
    }

    private fun refreshChipStates(
        ctx: Context,
        b: ItemFontCardBinding,
        codes: List<String>,
        activeIdx: Int,
        p: ThemeManager.Palette,
        dp: Float
    ) {
        codes.forEachIndexed { idx, code ->
            val chip = b.llScriptChips.getChildAt(idx) as? TextView ?: return@forEachIndexed
            val hasSample = FontRepository.getSampleForLang(code) != null
            chip.animate().cancel()
            chip.animate()
                .scaleX(if (idx == activeIdx) 1.06f else 1f)
                .scaleY(if (idx == activeIdx) 1.06f else 1f)
                .setDuration(140).start()
            styleChip(chip, idx == activeIdx, hasSample, dp, p)
        }
    }

    private fun scrollChipIntoView(b: ItemFontCardBinding, idx: Int) {
        b.hsvScriptChips.post {
            b.llScriptChips.getChildAt(idx)?.let {
                b.hsvScriptChips.smoothScrollTo(it.left, 0)
            }
        }
    }

    // ── Preview text ──────────────────────────────────────────────────────

    private fun applyPreview(
        b: ItemFontCardBinding,
        font: FontItem,
        codes: List<String>,
        activeIdx: Int,
        tf: Typeface
    ) {
        val s = FontRepository.settings
        val text = when {
            // Built-in meta sample: highest priority when switch is ON, no script chip active
            s.preferMetaSample && font.effectiveMeta.sampleText.isNotEmpty() ->
                font.effectiveMeta.sampleText
            codes.isNotEmpty() -> {
                val code = codes[activeIdx]
                FontRepository.getSampleForLang(code) ?: getSample(font)
            }
            else -> getSample(font)
        }
        b.tvPreviewLarge.text     = text.replace("\n", "  ").replace("\r", "")
        b.tvPreviewLarge.typeface = tf
    }
}
