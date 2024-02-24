package com.storyteller_f.giant_explorer.control.remote

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.flowWithLifecycle
import androidx.navigation.fragment.findNavController
import com.storyteller_f.annotation_defination.BindItemHolder
import com.storyteller_f.annotation_defination.ItemHolder
import com.storyteller_f.common_ui.SimpleFragment
import com.storyteller_f.common_ui.request
import com.storyteller_f.common_ui.scope
import com.storyteller_f.common_ui.setOnClick
import com.storyteller_f.giant_explorer.control.remote.RemoteListFragmentDirections.Companion.actionFirstFragmentToSecondFragment
import com.storyteller_f.giant_explorer.database.RemoteAccessSpec
import com.storyteller_f.giant_explorer.database.requireDatabase
import com.storyteller_f.giant_explorer.databinding.FragmentRemoteListBinding
import com.storyteller_f.giant_explorer.databinding.ViewHolderRemoteAccessSpecBinding
import com.storyteller_f.ui_list.adapter.ManualAdapter
import com.storyteller_f.ui_list.core.BindingViewHolder
import com.storyteller_f.ui_list.core.DataItemHolder
import com.storyteller_f.ui_list.event.findFragmentOrNull
import com.storyteller_f.ui_list.ui.ListWithState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class RemoteListFragment :
    SimpleFragment<FragmentRemoteListBinding>(FragmentRemoteListBinding::inflate) {
    override fun onBindViewEvent(binding: FragmentRemoteListBinding) = Unit

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = ManualAdapter<RemoteAccessSpecHolder, RemoteAccessSpecViewHolder>()

        binding.content.manualUp(adapter)
        binding.content.flash(ListWithState.UIState.loading)
        scope.launch {
            requireDatabase.remoteAccessDao().list()
                .flowWithLifecycle(viewLifecycleOwner.lifecycle)
                .shareIn(scope, SharingStarted.WhileSubscribed())
                .collectLatest {
                    binding.content.flash(
                        ListWithState.UIState(
                            false,
                            it.isNotEmpty(),
                            empty = false,
                            progress = false,
                            null,
                            null
                        )
                    )
                    adapter.submitList(
                        it.map { spec ->
                            RemoteAccessSpecHolder(spec)
                        }
                    )
                }
        }
    }

    fun clickSpec(spec: RemoteAccessSpec) {
        findNavController().request(
            actionFirstFragmentToSecondFragment(
                spec.id
            )
        )
    }
}

@ItemHolder("remote access")
data class RemoteAccessSpecHolder(val spec: RemoteAccessSpec) : DataItemHolder() {
    override fun areItemsTheSame(other: DataItemHolder): Boolean {
        val remoteAccessSpec = other as RemoteAccessSpecHolder
        return remoteAccessSpec.spec == this.spec
    }
}

@BindItemHolder(RemoteAccessSpecHolder::class)
class RemoteAccessSpecViewHolder(
    private val binding: ViewHolderRemoteAccessSpecBinding
) : BindingViewHolder<RemoteAccessSpecHolder>(
    binding
) {
    init {
        itemView.setOnClick {
            it.findFragmentOrNull<RemoteListFragment>()?.clickSpec(itemHolder.spec)
        }
    }

    override fun bindData(itemHolder: RemoteAccessSpecHolder) {
        val spec = itemHolder.spec
        binding.url.text = spec.toRemoteSpec().toUri().toString()
        binding.server.text = spec.server
    }
}
