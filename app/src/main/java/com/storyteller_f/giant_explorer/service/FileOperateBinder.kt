package com.storyteller_f.giant_explorer.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Binder
import android.util.Log
import androidx.annotation.WorkerThread
import com.storyteller_f.file_system.getFileInstance
import com.storyteller_f.file_system.instance.FileInstance
import com.storyteller_f.file_system.instance.FileKind
import com.storyteller_f.file_system.model.FileInfo
import com.storyteller_f.file_system.operate.FileOperationForemanProgressListener
import com.storyteller_f.file_system.size
import com.storyteller_f.giant_explorer.App
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.service.FileOperateService.FileOperateResultContainer
import com.storyteller_f.plugin_core.GiantExplorerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileNotFoundException
import java.lang.ref.WeakReference

class FileOperateBinder(
    val context: Context,
    private val taskScope: CoroutineScope,
    private val events: FileOperationEventBus = FileOperationEventBus.shared
) : Binder() {
    val taskHost = FileOperationHost(
        (context.applicationContext as App).hostCoordinationDispatcher,
        taskScope.coroutineContext[Job]
    )
    private val deleteMutex = Mutex()
    private val progressListenerLocal = object : FileOperationForemanProgressListener {
        override fun onProgress(progress: Int, key: String) { taskHost.progress(key, progress) }
        override fun onState(state: String?, key: String) { taskHost.message(key, state.orEmpty()) }
        override fun onTip(tip: String?, key: String) { taskHost.detail(key, tip.orEmpty()) }
        override fun onDetail(detail: String?, level: Int, key: String) { taskHost.detail(key, detail.orEmpty()) }
        override fun onLeft(fileCount: Int, folderCount: Int, size: Long, key: String) {
            taskHost.remaining(key, FileTaskCounts(fileCount, folderCount, size))
        }
        override fun onComplete(dest: String?, isSuccess: Boolean, key: String) = Unit
    }
    var fileOperateResultContainer: WeakReference<FileOperateResultContainer> = WeakReference(null)

    /**
     * 删除文件或者文件夹
     * @param selected  要删除的多个文件
     * @param focused     内存卡根部tree FileInfo
     */
    fun delete(focused: FileInfo, selected: List<FileInfo>, key: String) {
        launchTask(key) {
            deleteMutex.withLock { startDeleteTask(focused, selected.distinctBy { it.uri }, key) }
        }
    }

    fun moveOrCopy(
        dest: FileInstance,
        selected: List<FileInfo>,
        focused: FileInfo?,
        deleteOrigin: Boolean,
        key: String
    ) {
        launchTask(key) { startCopyTask(dest, focused, deleteOrigin, selected, key) }
    }

    fun pluginTask(key: String, block: suspend GiantExplorerService.() -> Boolean) {
        launchTask(key) {
            val service = object : GiantExplorerService {
                override fun reportRunning() { whenRunning(key, TaskAssessResult.empty) }
            }
            if (block(service)) whenEnd(key) else error(context.getString(R.string.operation_task_unknown_error))
        }
    }

    private fun launchTask(key: String, block: suspend () -> Unit) {
        taskScope.launch {
            if (!whenStart(key)) return@launch
            events.runTask {
                try {
                    block()
                } catch (cancelled: CancellationException) {
                    taskHost.finish(key, FileTaskStatus.CANCELLED)
                    throw cancelled
                } catch (error: Exception) {
                    Log.e(TAG, "File task failed: ${error.javaClass.simpleName}")
                    val message = error.localizedMessage ?: context.getString(R.string.operation_task_unknown_error)
                    taskHost.finish(key, FileTaskStatus.FAILED, message).join()
                    withContext(Dispatchers.Main) {
                        fileOperateResultContainer.get()?.onError(message)
                    }
                }
            }
        }
    }

    @WorkerThread
    private suspend fun startDeleteTask(
        focused: FileInfo,
        selected: List<FileInfo>,
        key: String
    ) {
        val assessResult = TaskAssessor(selected, context, null).assess()
        whenRunning(key, assessResult)
        val deleteForemanImpl = DeleteForemanImpl(
            selected,
            context,
            assessResult.toOverview(),
            focused,
            key
        ).attachListener()
        check(deleteForemanImpl.call()) { context.getString(R.string.operation_delete_failed) }
        whenEnd(key)
        fileOperateResultContainer.get()?.onSuccess(null, focused.uri)
    }

    /**
     * 启动复制任务。跳过detect 阶段
     *
     * @param dest          复制到
     * @param focused       被复制的路径
     * @param selected      分配好的任务
     */
    @WorkerThread
    private suspend fun startCopyTask(
        dest: FileInstance,
        focused: FileInfo?,
        deleteOrigin: Boolean,
        selected: List<FileInfo>,
        key: String
    ) {
        val assessResult = TaskAssessor(selected, context, dest).assess()
        whenRunning(key, assessResult)
        val copyForemanImpl = CopyForemanImpl(
            selected,
            deleteOrigin,
            dest,
            context,
            assessResult.toOverview(),
            focused,
            key
        ).attachListener()
        check(copyForemanImpl.call()) { context.getString(R.string.operation_task_unknown_error) }
        whenEnd(key)
        fileOperateResultContainer.get()?.onSuccess(dest.uri, focused?.uri)
    }

    private fun whenRunning(key: String, computeSize: TaskAssessResult) {
        Log.d(TAG, "whenRunning() called with: key = $key, computeSize = $computeSize")
        taskHost.running(key, FileTaskCounts(computeSize.fileCount, computeSize.folderCount, computeSize.size))
    }

    private suspend fun whenStart(key: String): Boolean {
        return taskHost.start(key).await()
    }

    private suspend fun whenEnd(key: String) {
        taskHost.finish(key, FileTaskStatus.SUCCEEDED).join()
    }

    private fun FileOperationForeman.attachListener(): FileOperationForeman {
        fileOperationForemanProgressListener = progressListenerLocal
        return this
    }

    companion object {
        private const val TAG = "FileOperateHandler"

        val supportUri = listOf(
            ContentResolver.SCHEME_CONTENT,
            ContentResolver.SCHEME_FILE,
            "http",
            "https",
            "ftp",
            "ftpes",
            "ftps",
            "sftp",
            "smb",
            "webdav"
        )

        fun checkOperationValid(source: Uri, destination: Uri, isDirectory: Boolean = true): Boolean {
            return isCopyDestinationValid(source.operationLocation(), destination.operationLocation(), isDirectory)
        }

        private fun Uri.operationLocation(): FileOperationLocation {
            // The file-system library uses the first content path segment as its storage-tree key.
            val root = if (scheme == ContentResolver.SCHEME_CONTENT) pathSegments.firstOrNull() else null
            return FileOperationLocation(scheme, authority, root, path.orEmpty())
        }
    }
}

data class TaskAssessResult(val fileCount: Int, val folderCount: Int, val size: Long) {
    fun toOverview(fileCountExtra: Int = 0): TaskOverview {
        return TaskOverview(fileCount + fileCountExtra, folderCount, size)
    }

    companion object {
        val empty = TaskAssessResult(0, 0, 0)
    }
}

/**
 * 任务评估。
 */
class TaskAssessor(
    private val detectorTasks: List<FileInfo>,
    val context: Context,
    private val dest: FileInstance?
) {
    private var count = 0
    private var folderCount = 0
    suspend fun assess(): TaskAssessResult {
        val size = detectorTasks.distinctBy { it.uri }.map { selected ->
            val fileInstance = requireNotNull(getFileInstance(context, selected.uri))
            if (!fileInstance.exists()) {
                if (dest != null) throw FileNotFoundException(fileInstance.path)
                return@map 0L
            }
            val file = fileInstance.getFileInfo()
            if (dest != null) {
                require(FileOperateBinder.checkOperationValid(file.uri, dest.uri, file.kind.isDirectory)) {
                    "不能将父文件夹移动到子文件夹"
                }
            }
            if (file.kind.isFile) {
                count++
                fileInstance.size()
            } else {
                getDirectorySize(file)
            }
        }.sum()
        if (dest != null) require(count + folderCount > 0) { "无合法任务" }
        return TaskAssessResult(count, folderCount, size)
    }

    private suspend fun getDirectorySize(file: FileInfo): Long {
        folderCount++
        val fileInstance = getFileInstance(context, file.uri)
        val listSafe = fileInstance!!.list()

        val fileSize = listSafe.files.map {
            count++
            (it.kind as FileKind.File).size
        }.plus(0).reduce { acc, l -> acc + l }
        val directorySize = listSafe.directories.map {
            getDirectorySize(it)
        }.plus(0).reduce { acc, l -> acc + l }
        return fileSize + directorySize
    }
}
