package com.storyteller_f.giant_explorer.dialog

import android.content.Context
import android.content.res.ColorStateList
import android.text.format.Formatter
import androidx.core.view.isVisible
import androidx.core.widget.ImageViewCompat
import com.google.android.material.color.MaterialColors
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.DialogFileOperationBinding
import com.storyteller_f.giant_explorer.service.FileOperationKind
import com.storyteller_f.giant_explorer.service.FileTaskPresentation
import com.storyteller_f.giant_explorer.service.FileTaskSnapshot
import com.storyteller_f.giant_explorer.service.FileTaskStatus
import com.storyteller_f.giant_explorer.service.TaskProgressMode

/** Android rendering only; state and task cancellation belong to FileOperationHost. */
class FileOperationDialogRenderer(
    private val context: Context,
    private val binding: DialogFileOperationBinding
) {
    fun render(task: FileTaskSnapshot) {
        val display = FileTaskPresentation.from(task)
        binding.taskTitle.setText(operationTitle(task.context.operation))
        binding.taskStatus.setText(statusTitle(task.status))
        val color = MaterialColors.getColor(binding.root, statusColor(task.status))
        binding.taskStatus.setTextColor(color)
        binding.operationIcon.setImageResource(operationIcon(task.context.operation))
        ImageViewCompat.setImageTintList(binding.operationIcon, ColorStateList.valueOf(color))
        binding.doneText.text = description(task)
        binding.taskSubject.text = task.context.subject
        binding.taskSubject.isVisible = task.context.subject.isNotBlank()
        binding.taskDestination.text = task.context.destination
        binding.destinationSection.isVisible = task.context.destination.isNotBlank()
        renderProgress(task, display.progressMode)
        renderSummary(task, display)
        binding.textViewState.text = task.message
        binding.textViewState.isVisible = !display.terminal && task.message.isNotBlank()
        binding.textViewDetail.text = task.details
        binding.taskDetailsToggle.isVisible = task.details.isNotBlank()
        binding.cancelOperation.isVisible = !display.terminal
        binding.cancelOperation.isEnabled = display.canCancel
        binding.cancelOperation.setText(
            if (task.status == FileTaskStatus.CANCELLING) R.string.file_task_stopping else R.string.file_task_cancel
        )
        binding.closeWhenDone.setText(
            if (display.terminal) R.string.close_dialog else R.string.file_task_background
        )
    }

    private fun renderProgress(task: FileTaskSnapshot, mode: TaskProgressMode) {
        binding.taskProgressContainer.isVisible = mode != TaskProgressMode.HIDDEN
        binding.progressBar.isIndeterminate = mode == TaskProgressMode.INDETERMINATE
        if (mode == TaskProgressMode.DETERMINATE) binding.progressBar.progress = task.progress
        binding.taskProgressText.text = when {
            mode == TaskProgressMode.DETERMINATE -> context.getString(R.string.file_task_percentage, task.progress)
            task.status == FileTaskStatus.CANCELLING -> context.getString(R.string.file_task_stopping)
            task.status == FileTaskStatus.COMPUTING -> context.getString(R.string.file_task_preparing)
            else -> context.getString(R.string.file_task_indeterminate)
        }
        binding.progressBar.contentDescription = binding.taskProgressText.text
    }

    private fun renderSummary(task: FileTaskSnapshot, display: FileTaskPresentation) {
        binding.taskSummary.isVisible = display.showSummary
        binding.taskCounts.text = context.getString(
            R.string.file_task_items, display.completedItems, display.totalItems
        )
        binding.taskSize.text = context.getString(
            R.string.file_task_bytes,
            Formatter.formatShortFileSize(context, display.completed.bytes),
            Formatter.formatShortFileSize(context, task.total.bytes)
        )
        binding.taskBreakdown.text = context.getString(
            R.string.file_task_breakdown, task.total.files, task.total.folders
        )
    }

    private fun description(task: FileTaskSnapshot): String = when (task.status) {
        FileTaskStatus.COMPUTING -> context.getString(
            if (task.context.operation == FileOperationKind.PLUGIN) {
                R.string.file_task_plugin_preparing
            } else {
                R.string.file_task_preparing_description
            }
        )
        FileTaskStatus.RUNNING -> context.getString(R.string.file_task_running_description)
        FileTaskStatus.CANCELLING -> context.getString(R.string.file_task_stopping_description)
        FileTaskStatus.CANCELLED -> context.getString(R.string.file_task_cancelled_description)
        FileTaskStatus.SUCCEEDED -> task.message.ifBlank { context.getString(R.string.file_task_success_description) }
        FileTaskStatus.FAILED -> context.getString(
            R.string.operation_task_failed,
            task.message.ifBlank { context.getString(R.string.operation_task_unknown_error) }
        )
    }

    private fun operationTitle(operation: FileOperationKind) = when (operation) {
        FileOperationKind.GENERIC -> R.string.file_task_title
        FileOperationKind.COPY -> R.string.file_task_copy
        FileOperationKind.MOVE -> R.string.file_task_move
        FileOperationKind.DELETE -> R.string.file_task_delete
        FileOperationKind.PLUGIN -> R.string.file_task_plugin
    }

    private fun operationIcon(operation: FileOperationKind) = when (operation) {
        FileOperationKind.DELETE -> R.drawable.ic_overlay_delete
        FileOperationKind.PLUGIN -> R.drawable.ic_plugin_generic
        FileOperationKind.MOVE -> R.drawable.ic_baseline_content_paste_24
        else -> R.drawable.ic_overlay_copy
    }

    private fun statusTitle(status: FileTaskStatus) = when (status) {
        FileTaskStatus.COMPUTING -> R.string.file_task_preparing
        FileTaskStatus.RUNNING -> R.string.file_task_running
        FileTaskStatus.CANCELLING -> R.string.file_task_stopping
        FileTaskStatus.SUCCEEDED -> R.string.file_task_succeeded
        FileTaskStatus.FAILED -> R.string.file_task_failed
        FileTaskStatus.CANCELLED -> R.string.file_task_cancelled
    }

    private fun statusColor(status: FileTaskStatus) = when (status) {
        FileTaskStatus.FAILED -> androidx.appcompat.R.attr.colorError
        FileTaskStatus.CANCELLED -> com.google.android.material.R.attr.colorOnSurfaceVariant
        else -> androidx.appcompat.R.attr.colorPrimary
    }
}
