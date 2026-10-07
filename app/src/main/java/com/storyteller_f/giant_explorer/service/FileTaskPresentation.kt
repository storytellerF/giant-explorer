package com.storyteller_f.giant_explorer.service

enum class TaskProgressMode { HIDDEN, INDETERMINATE, DETERMINATE }

/** Rendering decisions shared by the dialog and state-matrix tests. */
data class FileTaskPresentation(
    val terminal: Boolean,
    val canCancel: Boolean,
    val progressMode: TaskProgressMode,
    val showSummary: Boolean,
    val completed: FileTaskCounts,
    val totalItems: Int,
    val completedItems: Int
) {
    companion object {
        private val TERMINAL_STATUSES = setOf(
            FileTaskStatus.SUCCEEDED,
            FileTaskStatus.FAILED,
            FileTaskStatus.CANCELLED
        )

        fun from(task: FileTaskSnapshot): FileTaskPresentation {
            val terminal = task.status in TERMINAL_STATUSES
            val totalItems = task.total.files + task.total.folders
            val completed = FileTaskCounts(
                files = (task.total.files - task.remaining.files).coerceIn(0, task.total.files.coerceAtLeast(0)),
                folders = (task.total.folders - task.remaining.folders).coerceIn(
                    0,
                    task.total.folders.coerceAtLeast(0)
                ),
                bytes = (task.total.bytes - task.remaining.bytes).coerceIn(0, task.total.bytes.coerceAtLeast(0))
            )
            val hasPercentage = task.status == FileTaskStatus.RUNNING && task.countsKnown && totalItems > 0
            val progressMode = when {
                terminal -> TaskProgressMode.HIDDEN
                hasPercentage -> TaskProgressMode.DETERMINATE
                else -> TaskProgressMode.INDETERMINATE
            }
            return FileTaskPresentation(
                terminal = terminal,
                canCancel = !terminal && task.status != FileTaskStatus.CANCELLING,
                progressMode = progressMode,
                showSummary = task.countsKnown && totalItems > 0,
                completed = completed,
                totalItems = totalItems,
                completedItems = completed.files + completed.folders
            )
        }
    }
}
