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

enum class FileTaskStatus { COMPUTING, RUNNING, CANCELLING, SUCCEEDED, FAILED, CANCELLED }

enum class FileOperationKind { GENERIC, COPY, MOVE, DELETE, PLUGIN }

data class FileTaskContext(
    val operation: FileOperationKind = FileOperationKind.GENERIC,
    val subject: String = "",
    val destination: String = "",
    val origin: FileTaskOrigin = FileTaskOrigin.Detached
)

data class FileTaskCounts(val files: Int = 0, val folders: Int = 0, val bytes: Long = 0)

data class FileTaskSnapshot(
    val status: FileTaskStatus = FileTaskStatus.COMPUTING,
    val context: FileTaskContext = FileTaskContext(),
    val countsKnown: Boolean = false,
    val showDialog: Boolean = true,
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
    private val workerJobs = mutableMapOf<String, Job>()

    fun start(key: String, context: FileTaskContext = FileTaskContext()) = scope.async {
        if (mutableTasks.value.containsKey(key)) return@async false
        val origin = context.origin
        if (origin is FileTaskOrigin.Window &&
            mutableTasks.value.dialogState(origin) is FileTaskDialogState.Showing
        ) {
            return@async false
        }
        mutableTasks.value = mutableTasks.value + (key to FileTaskSnapshot(context = context))
        mutableStarts.emit(key)
        true
    }

    fun running(key: String, counts: FileTaskCounts, countsKnown: Boolean = true) = update(key) {
        it.copy(
            status = if (it.status == FileTaskStatus.CANCELLING) it.status else FileTaskStatus.RUNNING,
            total = counts,
            remaining = counts,
            countsKnown = countsKnown
        )
    }

    fun attachWorker(key: String, job: Job) = scope.launch {
        workerJobs[key] = job
        if (mutableTasks.value[key]?.status == FileTaskStatus.CANCELLING) job.cancel()
    }

    fun detachWorker(key: String, job: Job) = scope.launch {
        if (workerJobs[key] === job) workerJobs.remove(key)
    }

    fun cancel(key: String) = scope.launch {
        val current = mutableTasks.value[key] ?: return@launch
        if (current.status in TERMINAL_STATUSES || current.status == FileTaskStatus.CANCELLING) return@launch
        mutableTasks.value = mutableTasks.value + (key to current.copy(status = FileTaskStatus.CANCELLING))
        workerJobs[key]?.cancel()
    }

    fun progress(key: String, value: Int? = null, remaining: FileTaskCounts? = null) = update(key) {
        it.copy(
            progress = value?.coerceIn(0, COMPLETE_PROGRESS) ?: it.progress,
            remaining = remaining?.let { counts ->
                FileTaskCounts(
                    files = counts.files.coerceAtLeast(0),
                    folders = counts.folders.coerceAtLeast(0),
                    bytes = counts.bytes.coerceAtLeast(0)
                )
            } ?: it.remaining
        )
    }

    fun report(key: String, message: String? = null, detail: String? = null) = update(key) {
        it.copy(
            message = message ?: it.message,
            details = detail?.let { entry -> (it.details + entry + "\n").takeLast(MAX_DETAIL_LENGTH) } ?: it.details
        )
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

    fun dismiss(key: String) = scope.launch {
        val task = mutableTasks.value[key] ?: return@launch
        mutableTasks.value = if (task.status in TERMINAL_STATUSES) {
            mutableTasks.value - key
        } else {
            mutableTasks.value + (key to task.copy(showDialog = false))
        }
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
