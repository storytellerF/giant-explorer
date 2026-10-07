package com.storyteller_f.giant_explorer.service

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class FileTaskStatus { COMPUTING, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class FileTaskCounts(val files: Int = 0, val folders: Int = 0, val bytes: Long = 0)

data class FileTaskSnapshot(
    val status: FileTaskStatus = FileTaskStatus.COMPUTING,
    val total: FileTaskCounts = FileTaskCounts(),
    val remaining: FileTaskCounts = FileTaskCounts(),
    val progress: Int = 0,
    val message: String = "",
    val details: String = ""
)

/** Per-task render state. Progress survives closing or recreating a dialog. */
class FileOperationHost(coordination: CoroutineDispatcher, parent: Job?) {
    private val scope = CoroutineScope(SupervisorJob(parent) + coordination)
    private val mutableTasks = MutableStateFlow<Map<String, FileTaskSnapshot>>(emptyMap())
    val tasks = mutableTasks.asStateFlow()
    private val mutableStarts = MutableSharedFlow<String>(replay = 1)
    val starts = mutableStarts.asSharedFlow()

    fun start(key: String) = scope.async {
        if (mutableTasks.value.containsKey(key)) return@async false
        mutableTasks.value = mutableTasks.value + (key to FileTaskSnapshot())
        mutableStarts.emit(key)
        true
    }

    fun running(key: String, counts: FileTaskCounts) = update(key) {
        it.copy(status = FileTaskStatus.RUNNING, total = counts, remaining = counts)
    }

    fun progress(key: String, value: Int) = update(key) { it.copy(progress = value.coerceIn(0, COMPLETE_PROGRESS)) }

    fun remaining(key: String, counts: FileTaskCounts) = update(key) {
        it.copy(
            remaining = FileTaskCounts(
                files = counts.files.coerceAtLeast(0),
                folders = counts.folders.coerceAtLeast(0),
                bytes = counts.bytes.coerceAtLeast(0)
            )
        )
    }

    fun message(key: String, value: String) = update(key) { it.copy(message = value) }

    fun detail(key: String, value: String) = update(key) {
        it.copy(details = (it.details + value + "\n").takeLast(MAX_DETAIL_LENGTH))
    }

    fun finish(key: String, status: FileTaskStatus, message: String = "") = update(key) {
        it.copy(
            status = status,
            message = message,
            progress = if (status == FileTaskStatus.SUCCEEDED) COMPLETE_PROGRESS else it.progress,
            remaining = if (status == FileTaskStatus.SUCCEEDED) FileTaskCounts() else it.remaining
        )
    }

    private fun update(key: String, reduce: (FileTaskSnapshot) -> FileTaskSnapshot) = scope.launch {
        val current = mutableTasks.value[key] ?: return@launch
        if (current.status in TERMINAL_STATUSES) return@launch
        mutableTasks.value = mutableTasks.value + (key to reduce(current))
    }

    fun forget(key: String) = scope.launch {
        if (mutableTasks.value[key]?.status in TERMINAL_STATUSES) mutableTasks.value = mutableTasks.value - key
    }

    fun close() = scope.cancel()

    companion object {
        private const val COMPLETE_PROGRESS = 100
        private const val MAX_DETAIL_LENGTH = 16_384
        private val TERMINAL_STATUSES = setOf(
            FileTaskStatus.SUCCEEDED,
            FileTaskStatus.FAILED,
            FileTaskStatus.CANCELLED
        )
    }
}
