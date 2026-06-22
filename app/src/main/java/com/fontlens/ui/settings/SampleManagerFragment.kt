package com.fontlens.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fontlens.data.ALL_LANGUAGES
import com.fontlens.data.FontRepository
import com.fontlens.data.defaultLanguageSamples
import com.fontlens.data.defaultLangSamples
import com.fontlens.data.scriptDisplayName
import com.fontlens.databinding.FragmentSampleManagerBinding
import com.fontlens.databinding.ItemSampleScriptBinding

class SampleManagerFragment : Fragment() {

    private var _binding: FragmentSampleManagerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSampleManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val adapter = ScriptSampleAdapter(buildItems()) { updatedItems ->
            // Persist: split back into script samples and language ISO samples
            val scriptDefaults = defaultLangSamples().keys.toSet()
            val newScriptSamples = updatedItems
                .filter { it.code in scriptDefaults }
                .associate { it.code to it.sampleText }
            val newScriptOrder = updatedItems
                .filter { it.code in scriptDefaults }
                .map { it.code }
            val newLangSamples = updatedItems
                .filter { it.code !in scriptDefaults }
                .associate { it.code to it.sampleText }

            FontRepository.settings = FontRepository.settings.copy(
                langSamples      = newScriptSamples,
                scriptOrder      = newScriptOrder,
                langSamplesByIso = FontRepository.settings.langSamplesByIso + newLangSamples
            )
            FontRepository.saveSettings(requireContext())
        }

        binding.rvScripts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvScripts.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                adapter.moveItem(vh.adapterPosition, target.adapterPosition)
                return true
            }
            override fun onSwiped(vh: RecyclerView.ViewHolder, dir: Int) {}
            override fun onSelectedChanged(vh: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(vh, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    vh?.itemView?.alpha  = 0.85f
                    vh?.itemView?.scaleX = 1.02f
                    vh?.itemView?.scaleY = 1.02f
                }
            }
            override fun clearView(rv: RecyclerView, vh: RecyclerView.ViewHolder) {
                super.clearView(rv, vh)
                vh.itemView.alpha  = 1f
                vh.itemView.scaleX = 1f
                vh.itemView.scaleY = 1f
            }
        })
        touchHelper.attachToRecyclerView(binding.rvScripts)
        adapter.touchHelper = touchHelper
    }

    /**
     * Build the full item list:
     *  1. Script-level entries (ordered by user's scriptOrder)
     *  2. Language-level entries (grouped under their script, sorted by script then name)
     *     Format used in the adapter: code = ISO code, displayLabel set separately
     */
    private fun buildItems(): MutableList<ScriptItem> {
        val s        = FontRepository.settings
        val scriptDefaults = defaultLangSamples()
        val langDefaults   = defaultLanguageSamples()

        // ── Script entries (draggable, reorderable) ────────────────────────
        val allScriptCodes = (s.scriptOrder + scriptDefaults.keys).distinct()
        val scriptItems = allScriptCodes.map { code ->
            ScriptItem(
                code         = code,
                sampleText   = s.langSamples[code] ?: scriptDefaults[code] ?: "",
                displayLabel = scriptDisplayName(code),
                isoLabel     = null,               // not a language entry
                isLanguage   = false
            )
        }

        // ── Language entries (non-draggable, grouped) ──────────────────────
        // One entry per unique language in ALL_LANGUAGES (dedup by isoCode)
        val seen = mutableSetOf<String>()
        val langItems = ALL_LANGUAGES
            .filter { seen.add(it.isoCode) }      // deduplicate
            .sortedWith(compareBy({ it.scriptCode }, { it.name }))
            .map { lang ->
                ScriptItem(
                    code         = lang.isoCode,
                    sampleText   = s.langSamplesByIso[lang.isoCode]
                                       ?: langDefaults[lang.isoCode] ?: "",
                    displayLabel = lang.name,
                    isoLabel     = lang.isoCode.uppercase(),
                    scriptLabel  = scriptDisplayName(lang.scriptCode),
                    isLanguage   = true
                )
            }

        return (scriptItems + langItems).toMutableList()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Data ──────────────────────────────────────────────────────────────────────

data class ScriptItem(
    val code: String,
    var sampleText: String,
    val displayLabel: String = code,
    val isoLabel: String? = null,
    val scriptLabel: String = "",
    val isLanguage: Boolean = false
)

// ── Adapter ───────────────────────────────────────────────────────────────────

class ScriptSampleAdapter(
    private val items: MutableList<ScriptItem>,
    private val onChanged: (List<ScriptItem>) -> Unit
) : RecyclerView.Adapter<ScriptSampleAdapter.VH>() {

    var touchHelper: ItemTouchHelper? = null

    inner class VH(val b: ItemSampleScriptBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSampleScriptBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b    = holder.b

        if (item.isLanguage) {
            // ── Language entry: "Language Name · Script Name  [ISO]" ──────
            // Name label: "English · Latin"
            b.tvScriptName.text = "${item.displayLabel} · ${item.scriptLabel}"
            // ISO badge
            b.tvScriptCode.text    = item.isoLabel ?: item.code.uppercase()
            b.tvScriptCode.visibility = android.view.View.VISIBLE
            // Drag handle hidden for language rows
            b.ivDragHandle.visibility = android.view.View.INVISIBLE
        } else {
            // ── Script entry (original behaviour) ─────────────────────────
            b.tvScriptName.text       = item.displayLabel
            b.tvScriptCode.text       = item.code.uppercase()
            b.tvScriptCode.visibility = android.view.View.VISIBLE
            b.ivDragHandle.visibility = android.view.View.VISIBLE
        }

        b.etSampleText.setText(item.sampleText)

        b.etSampleText.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_ID.toInt()) {
                    items[pos] = items[pos].copy(sampleText = b.etSampleText.text?.toString() ?: "")
                    onChanged(items.toList())
                }
            }
        }

        b.btnRestoreDefault.setOnClickListener {
            val default = if (item.isLanguage) {
                defaultLanguageSamples()[item.code] ?: ""
            } else {
                defaultLangSamples()[item.code] ?: ""
            }
            b.etSampleText.setText(default)
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) {
                items[pos] = items[pos].copy(sampleText = default)
                onChanged(items.toList())
            }
        }

        // Only script entries are draggable
        if (!item.isLanguage) {
            b.ivDragHandle.setOnTouchListener { _, _ ->
                touchHelper?.startDrag(holder)
                false
            }
        }
    }

    fun moveItem(from: Int, to: Int) {
        // Only allow dragging script-level items
        if (items[from].isLanguage || items[to].isLanguage) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
        onChanged(items.toList())
    }
}
