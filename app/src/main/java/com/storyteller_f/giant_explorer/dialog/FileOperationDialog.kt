package com.storyteller_f.giant_explorer.dialog

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
import com.storyteller_f.giant_explorer.service.FileTaskSnapshot
import com.storyteller_f.giant_explorer.service.FileTaskStatus
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

    override fun onBindViewEvent(binding: DialogFileOperationBinding) {
        binding.closeWhenError.setOnClickListener { closeTask() }
        binding.closeWhenDone.setOnClickListener { closeTask() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setCanceledOnTouchOutside(false)
        val key = arguments?.getString(TASK_KEY) ?: return dismiss()
        val viewBinding = binding
        val activity = requireActivity() as MainActivity
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                activity.fileOperateBinder.asFlow().filterNotNull().flatMapLatest { current ->
                    binder = current
                    current.taskHost.tasks.map { it[key] }
                }.distinctUntilChanged().collect { task ->
                    if (task != null) render(viewBinding, task) else dismiss()
                }
            }
        }
    }

    private fun render(binding: DialogFileOperationBinding, task: FileTaskSnapshot) {
        val running = task.status == FileTaskStatus.RUNNING
        val computing = task.status == FileTaskStatus.COMPUTING
        binding.stateProgress.isVisible = computing
        binding.stateRunning.isVisible = running
        binding.stateDone.isVisible = !computing && !running
        binding.closeWhenError.isVisible = false
        binding.textViewTask.text = getString(
            R.string.operation_task_total, task.total.bytes, task.total.files, task.total.folders
        )
        binding.textViewLeft.text = getString(
            R.string.operation_task_remaining, task.remaining.bytes, task.remaining.files, task.remaining.folders
        )
        binding.progressBar.progress = task.progress
        binding.textViewState.text = task.message
        binding.textViewDetail.text = task.details
        binding.doneText.text = when (task.status) {
            FileTaskStatus.FAILED -> getString(
                R.string.operation_task_failed,
                task.message.ifBlank { getString(R.string.operation_task_unknown_error) }
            )
            FileTaskStatus.CANCELLED -> getString(R.string.operation_task_cancelled)
            else -> getString(R.string.operation_task_done)
        }
    }

    private fun closeTask() {
        arguments?.getString(TASK_KEY)?.let { binder?.taskHost?.forget(it) }
        dismiss()
    }

    companion object {
        const val DIALOG_TAG = "file-operation"
        private const val TASK_KEY = "task-key"
        fun forTask(key: String) = FileOperationDialog().apply {
            arguments = Bundle().apply { putString(TASK_KEY, key) }
        }
    }
}
