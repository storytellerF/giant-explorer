package com.storyteller_f.giant_explorer.dialog

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import androidx.activity.ComponentDialog
import androidx.activity.addCallback
import androidx.core.net.toUri
import androidx.lifecycle.flowWithLifecycle
import androidx.navigation.fragment.navArgs
import com.storyteller_f.annotation_defination.BindClickEvent
import com.storyteller_f.common_ui.Registry
import com.storyteller_f.common_ui.SimpleDialogFragment
import com.storyteller_f.common_ui.observeResponse
import com.storyteller_f.common_ui.request
import com.storyteller_f.common_ui.scope
import com.storyteller_f.common_ui.setFragmentResult
import com.storyteller_f.common_ui.setOnClick
import com.storyteller_f.common_vm_ktx.activityScope
import com.storyteller_f.file_system.getCurrentUserEmulatedPath
import com.storyteller_f.file_system.instance.FileCreatePolicy
import com.storyteller_f.file_system.toChildEfficiently
import com.storyteller_f.file_system.toParentEfficiently
import com.storyteller_f.file_system_ktx.getFileInstance
import com.storyteller_f.file_system_ktx.isDirectory
import com.storyteller_f.giant_explorer.control.FileItemHolder
import com.storyteller_f.giant_explorer.control.FileListFragmentArgs
import com.storyteller_f.giant_explorer.control.FileListObserver
import com.storyteller_f.giant_explorer.control.FileViewHolder
import com.storyteller_f.giant_explorer.databinding.DialogRequestPathBinding
import com.storyteller_f.giant_explorer.view.PathMan
import com.storyteller_f.giant_explorer.view.flash
import com.storyteller_f.giant_explorer.view.setup
import com.storyteller_f.ui_list.adapter.SimpleSourceAdapter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import java.io.File

class RequestPathDialog :
    SimpleDialogFragment<DialogRequestPathBinding>(DialogRequestPathBinding::inflate),
    Registry {
    private val args by navArgs<RequestPathDialogArgs>()

    private val observer = FileListObserver(this, {
        FileListFragmentArgs(args.start)
    }, activityScope)

    @Parcelize
    class RequestPathResult(val uri: Uri) : Parcelable

    private val adapter = SimpleSourceAdapter<FileItemHolder, FileViewHolder>(REQUEST_KEY)

    companion object {
        const val REQUEST_KEY = "request-path"

        fun bundle(context: Context): Bundle {
            val path = context.getCurrentUserEmulatedPath()
            return RequestPathDialogArgs(
                File(
                    path
                ).toUri()
            ).toBundle()
        }
    }

    override fun onBindViewEvent(binding: DialogRequestPathBinding) {
        binding.bottom.requestScreenOn.setOnClick {
            it.keepScreenOn = it.isChecked
        }
        binding.bottom.positive.setOnClick {
            observer.fileInstance?.uri?.let {
                setFragmentResult(RequestPathResult(it))
                dismiss()
            }
        }
        val filterHiddenFile = observer.fileListViewModel.filterHiddenFile
        filterHiddenFile.observe(viewLifecycleOwner) {
            binding.filterHiddenFile.isActivated = it
        }
        binding.filterHiddenFile.setOnClick {
            filterHiddenFile.value =
                filterHiddenFile.value?.not() ?: false
        }
        binding.bottom.negative.setOnClick {
            dismiss()
        }
        binding.newFile.setOnClick {
            val requestDialog = request(NewNameDialog::class.java)
            observeResponse(requestDialog, NewNameDialog.NewNameResult::class.java) { nameResult ->
                scope.launch {
                    observer.fileInstance?.toChild(nameResult.name, FileCreatePolicy.Create(false))
                }
            }
        }
        (dialog as? ComponentDialog)?.onBackPressedDispatcher?.addCallback(this) {
            val currentInstance = observer.fileInstance
            if (currentInstance != null) {
                val context = requireContext()
                val userEmulatedPath = context.getCurrentUserEmulatedPath()
                if (currentInstance.path == "/" || currentInstance.path.startsWith(userEmulatedPath)) {
                    isEnabled = false
                    @Suppress("DEPRECATION")
                    dialog?.onBackPressed()
                } else {
                    scope.launch {
                        observer.update(
                            currentInstance.toParentEfficiently(context)
                        )
                    }
                }
            }
        }
        binding.filter.setOnClick {
            request(FilterDialogFragment::class.java)
        }
        binding.sort.setOnClick {
            request(SortDialogFragment::class.java)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val pathManLayout = binding.pathManLayout
        pathManLayout.setup()
        observer.setup(binding.content, adapter, {}) {
            pathManLayout.flash(it)
        }
        scope.launch {
            callbackFlow {
                pathManLayout.pathMan.pathChangeListener =
                    PathMan.PathChangeListener { pathString -> trySend(pathString) }
                awaitClose {
                    pathManLayout.pathMan.pathChangeListener = null
                }
            }.flowWithLifecycle(lifecycle).collectLatest {
                val uri = observer.fileInstance?.uri?.buildUpon()?.path(it)?.build()
                    ?: return@collectLatest
                observer.update(getFileInstance(requireContext(), uri))
            }
        }
    }

    @BindClickEvent(FileItemHolder::class, group = REQUEST_KEY)
    fun toChild(itemHolder: FileItemHolder) {
        if (itemHolder.file.item.isDirectory) {
            val current = observer.fileInstance ?: return
            scope.launch {
                observer.update(
                    current.toChildEfficiently(
                        requireContext(),
                        itemHolder.file.name,
                        FileCreatePolicy.NotCreate
                    )
                )
            }
        } else {
            setFragmentResult(RequestPathResult(itemHolder.file.item.uri))
            dismiss()
        }
    }
}
