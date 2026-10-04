package com.storyteller_f.giant_explorer.control.plugin

import android.os.Bundle
import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.storyteller_f.annotation_defination.BindClickEvent
import com.storyteller_f.annotation_defination.BindItemHolder
import com.storyteller_f.annotation_defination.ItemHolder
import com.storyteller_f.common_ui.SimpleFragment
import com.storyteller_f.common_ui.owner
import com.storyteller_f.common_vm_ktx.vm
import com.storyteller_f.giant_explorer.*
import com.storyteller_f.giant_explorer.control.plugin.ui_list.registerPluginHolder
import com.storyteller_f.giant_explorer.databinding.FragmentPluginListBinding
import com.storyteller_f.giant_explorer.databinding.ViewHolderPluginBinding
import com.storyteller_f.ui_list.adapter.SimpleSourceAdapter
import com.storyteller_f.ui_list.core.BindingViewHolder
import com.storyteller_f.ui_list.core.DataItemHolder
import com.storyteller_f.ui_list.core.Model
import com.storyteller_f.ui_list.data.SimpleResponse
import com.storyteller_f.ui_list.source.SearchHandler
import com.storyteller_f.ui_list.source.SimpleSearchRepository
import com.storyteller_f.ui_list.ui.ListWithState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SimplePlugin(val path: String) : Model {
    override fun commonId(): String {
        return path
    }
}

class PluginListViewModel : ViewModel() {
    val handler = SearchHandler(
        SimpleSearchRepository<SimplePlugin, String> { _, startPage, count ->
            val toList = pluginManagerRegister.pluginsName().toList()
            val startIndex = (startPage - 1) * count
            val toIndex = (startIndex + count).coerceAtMost(toList.size)
            val data = toList.subList(startIndex, toIndex).map {
                SimplePlugin(it)
            }
            SimpleResponse(toList.size, data)
        }
    ) { p, _ -> PluginHolder(p.path) }
}

class PluginListFragment : SimpleFragment<FragmentPluginListBinding>(FragmentPluginListBinding::inflate) {

    private val adapter = SimpleSourceAdapter<PluginHolder, PluginViewHolder>(buildMap {
        registerPluginHolder(this)
    })
    private val sourceViewModel by vm({}) {
        PluginListViewModel()
    }

    private val source
        get() = sourceViewModel.handler

    override fun onBindViewEvent(binding: FragmentPluginListBinding) = Unit

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.content.sourceUp(adapter, owner, refresh = {
            pluginManagerRegister.removeAllPlugin()
            refreshPlugin(requireContext())
        }, flash = ListWithState.Companion::remote)
        source.lastJob?.cancel()
        source.lastJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                source.search("", viewLifecycleOwner.lifecycleScope).collectLatest {
                    adapter.submitData(it)
                }
            }
        }
    }

    @BindClickEvent(PluginHolder::class)
    fun clickPlugin(bindingAdapterPosition: Int) {
        val itemHolder = adapter.getItemHolder(bindingAdapterPosition) ?: return
        findNavController().navigate(
            R.id.action_FirstFragment_to_SecondFragment,
            PluginInfoFragmentArgs(itemHolder.name).toBundle()
        )
    }
}

@ItemHolder("plugin")
data class PluginHolder(val name: String) : DataItemHolder() {
    override fun areItemsTheSame(other: DataItemHolder): Boolean {
        return (other as PluginHolder).name == name
    }
}

@BindItemHolder(PluginHolder::class)
class PluginViewHolder(private val binding: ViewHolderPluginBinding) : BindingViewHolder<PluginHolder>(binding) {
    override fun bindData(itemHolder: PluginHolder) {
        binding.pluginName.text = itemHolder.name
    }
}
