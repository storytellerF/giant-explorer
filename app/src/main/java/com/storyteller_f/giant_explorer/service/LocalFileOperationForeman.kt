package com.storyteller_f.giant_explorer.service

import android.content.Context
import android.util.Log
import com.storyteller_f.file_system.getFileInstance
import com.storyteller_f.file_system.instance.FileInstance
import com.storyteller_f.file_system.instance.FileKind
import com.storyteller_f.file_system.message.Message
import com.storyteller_f.file_system.model.FileInfo
import com.storyteller_f.file_system.operate.FileOperationForemanProgressListener
import com.storyteller_f.file_system.operate.FileOperationListener
import com.storyteller_f.file_system.operate.ScopeFileCopyOp
import com.storyteller_f.file_system.operate.ScopeFileMoveOp
import com.storyteller_f.file_system.operate.ScopeFileMoveOpInShell
import com.storyteller_f.file_system.operate.SuspendCallable
import com.storyteller_f.giant_explorer.R

class TaskOverview(val fileCount: Int, val folderCount: Int, val size: Long) {
    val sumCount = fileCount + folderCount
}

abstract class FileOperationForeman(
    val context: Context,
    val overview: TaskOverview,
    val key: String
) : SuspendCallable<Boolean>, FileOperationListener {
    var fileOperationForemanProgressListener: FileOperationForemanProgressListener? = null
    private var leftFileCount = overview.fileCount
    private var leftFolderCount = overview.folderCount
    var leftSize = overview.size
    private fun emitCurrentStateMessage() {
        fileOperationForemanProgressListener?.onLeft(leftFileCount, leftFolderCount, leftSize, key)
        fileOperationForemanProgressListener?.onProgress(progress, key)
    }

    protected fun emitDetailMessage(detail: String, level: Int) {
        fileOperationForemanProgressListener?.onDetail(detail, level, key)
    }

    protected fun emitStateMessage(tip: String) {
        fileOperationForemanProgressListener?.onState(tip, key)
    }

    open val progress: Int
        get() {
            val sumCount = overview.sumCount
            val completedCount = sumCount - leftFolderCount - leftFileCount
            return if (sumCount == 0) {
                PERCENT_BASE
            } else {
                (completedCount * 1.0 / sumCount * PERCENT_BASE).toInt().coerceIn(0, PERCENT_BASE)
            }
        }

    override fun onFileDone(fileInstance: FileInstance?, message: Message?, size: Long) {
        leftFileCount = (leftFileCount - 1).coerceAtLeast(0)
        leftSize = (leftSize - size).coerceAtLeast(0)
        emitCurrentStateMessage()
        emitStateMessage(context.getString(R.string.operation_file_done, fileInstance?.name.orEmpty()))
    }

    override fun onDirectoryDone(fileInstance: FileInstance?, message: Message?) {
        leftFolderCount = (leftFolderCount - 1).coerceAtLeast(0)
        emitCurrentStateMessage()
        emitStateMessage(context.getString(R.string.operation_directory_done, fileInstance?.name.orEmpty()))
    }

    override fun onError(message: Message?) {
        fileOperationForemanProgressListener?.onDetail(
            message?.name + message?.get(),
            Log.ERROR,
            key
        )
    }
    companion object {
        const val PERCENT_BASE = 100
    }
}

abstract class LocalFileOperationForeman(
    val focused: FileInfo?,
    context: Context,
    overview: TaskOverview,
    key: String
) : FileOperationForeman(context, overview, key) {
    abstract val description: String?
}

class CopyForemanImpl(
    private val items: List<FileInfo>,
    private val isMove: Boolean,
    private val target: FileInstance,
    context: Context,
    overview: TaskOverview,
    focused: FileInfo?,
    key: String
) : LocalFileOperationForeman(focused, context, overview, key) {
    override val description: String
        get() {
            val taskName: String
            val fileName: String = items[0].name
            taskName = if (items.size == 1) {
                fileName
            } else {
                fileName + "等" + items.size + "个文件"
            }
            return (if (isMove) "移动" else "复制") + taskName + "到" + target.name
        }

    override suspend fun call(): Boolean {
        val isSuccess = !items.any {
            val fileInstance = requireNotNull(getFileInstance(context, it.uri))
            emitStateMessage("处理${fileInstance.path}")
            val operationResult =
                when {
                    !isMove -> ScopeFileCopyOp(fileInstance, target, context).bind(this)
                    fileInstance.javaClass == target.javaClass -> ScopeFileMoveOpInShell(
                        fileInstance,
                        target,
                        context
                    ).bind(this)

                    else -> ScopeFileMoveOp(fileInstance, target, context)
                }.call()
            !operationResult // 如果失败了，提前结束
        }
        if (!isSuccess) {
            emitDetailMessage("error in copy impl", Log.ERROR)
        }
        fileOperationForemanProgressListener?.onComplete(
            target.path,
            isSuccess,
            key
        )
        return isSuccess
    }

    override val progress: Int
        get() {
            val doneSize: Long = overview.size - leftSize
            return if (overview.size == 0L) {
                super.progress
            } else {
                (doneSize.toDouble() * PERCENT_BASE / overview.size).toInt().coerceIn(0, PERCENT_BASE)
            }
        }
}

class DeleteForemanImpl(
    private val detectorTasks: List<FileInfo>,
    context: Context,
    overview: TaskOverview,
    focused: FileInfo,
    key: String
) : LocalFileOperationForeman(focused, context, overview, key) {

    override val description: String
        get() {
            val firstFile = detectorTasks.first()
            return if (detectorTasks.size == 1) {
                "删除" + firstFile.name
            } else {
                "删除" + firstFile.name + "等" + detectorTasks.size.toString() + "个文件"
            }
        }

    override suspend fun call(): Boolean {
        val isSuccess = detectorTasks.distinctBy { it.uri }.all { selected ->
            deleteRecursively(requireNotNull(getFileInstance(context, selected.uri)))
        }
        fileOperationForemanProgressListener?.onComplete(focused?.fullPath, isSuccess, key)
        return isSuccess
    }

    private suspend fun deleteRecursively(file: FileInstance): Boolean {
        if (!file.exists()) return true
        val kind = file.fileKind()
        emitStateMessage(context.getString(R.string.operation_deleting, file.name))
        if (kind.isDirectory) {
            val children = file.list().let { it.files + it.directories }
            for (child in children) {
                if (!deleteRecursively(requireNotNull(getFileInstance(context, child.uri)))) return false
            }
        }
        val success = ensureDeleted({ file.exists() }, { file.deleteFileOrEmptyDirectory() })
        if (success) {
            if (kind.isDirectory) {
                onDirectoryDone(file, null)
            } else {
                onFileDone(file, null, (kind as? FileKind.File)?.size ?: 0L)
            }
        } else {
            emitDetailMessage(context.getString(R.string.operation_delete_failed), Log.ERROR)
        }
        return success
    }
}
