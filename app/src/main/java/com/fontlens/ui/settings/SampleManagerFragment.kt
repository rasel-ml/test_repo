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
import com.fontlens.data.isSingleNameScript
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

        val adapter = LangSampleAdapter(buildItems()) { updatedItems ->
            FontRepository.settings = FontRepository.settings.copy(
                langOrder        = updatedItems.map { it.isoCode },
                langSamplesByIso = updatedItems.associate { it.isoCode to it.sampleText }
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

    private fun buildItems(): MutableList<LangItem> {
        val s        = FontRepository.settings
        val defaults = defaultLanguageSamples()

        // Deduplicate ALL_LANGUAGES by ISO code
        val seen = mutableSetOf<String>()
        val allLangs = ALL_LANGUAGES.filter { seen.add(it.isoCode) }

        // Start from user's saved order, then append any new languages not yet in the list
        val orderedIsos = (s.langOrder + allLangs.map { it.isoCode }).distinct()

        return orderedIsos.mapNotNull { iso ->
            val lang = allLangs.find { it.isoCode == iso } ?: return@mapNotNull null
            // Label: "Language · Script" or just "Language" if single-name script
            val label = if (isSingleNameScript(lang.scriptCode)) lang.name
                        else "${lang.name} · ${scriptDisplayName(lang.scriptCode)}"
            LangItem(
                isoCode    = iso,
                sampleText = s.langSamplesByIso[iso] ?: defaults[iso] ?: "",
                label      = label,
                isoLabel   = iso.uppercase()
            )
        }.toMutableList()
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

// ── Data ──────────────────────────────────────────────────────────────────────

data class LangItem(
    val isoCode: String,
    var sampleText: String,
    val label: String,
    val isoLabel: String
)

// ── Adapter ───────────────────────────────────────────────────────────────────

class LangSampleAdapter(
    private val items: MutableList<LangItem>,
    private val onChanged: (List<LangItem>) -> Unit
) : RecyclerView.Adapter<LangSampleAdapter.VH>() {

    var touchHelper: ItemTouchHelper? = null

    inner class VH(val b: ItemSampleScriptBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemSampleScriptBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b    = holder.b

        b.tvScriptName.text = item.label
        b.tvScriptCode.text = item.isoLabel

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
            val default = defaultLanguageSamples()[item.isoCode] ?: ""
            b.etSampleText.setText(default)
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_ID.toInt()) {
                items[pos] = items[pos].copy(sampleText = default)
                onChanged(items.toList())
            }
        }

        b.ivDragHandle.setOnTouchListener { _, _ ->
            touchHelper?.startDrag(holder)
            false
        }
    }

    fun moveItem(from: Int, to: Int) {
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
        onChanged(items.toList())
    }
}
