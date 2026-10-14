package com.storyteller_f.giant_explorer.dialog

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.control.MainActivity
import com.storyteller_f.giant_explorer.databinding.DialogFileOperationBinding
import com.storyteller_f.giant_explorer.service.FileOperateBinder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class FileOperationDialog :
    GiantDialogFragment<DialogFileOperationBinding>(DialogFileOperationBinding::inflate) {
    var binder: FileOperateBinder? = null
    private var detailsExpanded = false

    override fun onBindViewEvent(binding: DialogFileOperationBinding) {
        binding.cancelOperation.setOnClickListener {
            arguments?.getString(TASK_KEY)?.let { binder?.taskHost?.cancel(it) }
        }
        binding.taskDetailsToggle.setOnClickListener {
            detailsExpanded = !detailsExpanded
            renderDetails()
        }
        binding.closeWhenDone.setOnClickListener { closeTask() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)
        val key = arguments?.getString(TASK_KEY) ?: return dismiss()
        detailsExpanded = savedInstanceState?.getBoolean(DETAILS_EXPANDED) ?: false
        val viewBinding = binding
        val renderer = FileOperationDialogRenderer(requireContext(), viewBinding)
        val activity = requireActivity() as MainActivity
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activity.fileOperateBinder.asFlow().filterNotNull().flatMapLatest { current ->
                    binder = current
                    current.taskHost.tasks.map { it[key] }
                }.distinctUntilChanged().collect { task ->
                    if (task != null) {
                        renderer.render(task)
                        renderDetails()
                    } else {
                        dismiss()
                    }
                }
            }
        }
    }

    private fun renderDetails() {
        binding.textViewDetail.isVisible = detailsExpanded && binding.textViewDetail.text.isNotBlank()
        binding.taskDetailsToggle.setText(
            if (detailsExpanded) R.string.file_task_hide_details else R.string.file_task_show_details
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(DETAILS_EXPANDED, detailsExpanded)
        super.onSaveInstanceState(outState)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (activity?.isChangingConfigurations != true) {
            arguments?.getString(TASK_KEY)?.let { binder?.taskHost?.dismiss(it) }
        }
        super.onDismiss(dialog)
    }

    private fun closeTask() {
        arguments?.getString(TASK_KEY)?.let { binder?.taskHost?.dismiss(it) }
        dismiss()
    }

    companion object {
        const val DIALOG_TAG = "file-operation"
        private const val TASK_KEY = "task-key"
        private const val DETAILS_EXPANDED = "details-expanded"
        fun forTask(key: String) = FileOperationDialog().apply {
            arguments = Bundle().apply { putString(TASK_KEY, key) }
        }
    }
}
