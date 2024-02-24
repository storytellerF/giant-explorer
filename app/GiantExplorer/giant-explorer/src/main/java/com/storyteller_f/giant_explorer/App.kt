package com.storyteller_f.giant_explorer

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.material.color.DynamicColors
import com.storyteller_f.config_core.EditorKey
import com.storyteller_f.config_core.editor
import com.storyteller_f.file_system.checkFilePermission
import com.storyteller_f.file_system.instance.FileCreatePolicy
import com.storyteller_f.file_system.instance.FileInstance
import com.storyteller_f.file_system.instance.FileKind
import com.storyteller_f.file_system.model.FileInfo
import com.storyteller_f.file_system_ktx.getFileInstance
import com.storyteller_f.filter_core.config.FilterConfig
import com.storyteller_f.filter_core.config.FilterConfigItem
import com.storyteller_f.filter_core.filterConfigAdapterFactory
import com.storyteller_f.giant_explorer.control.plugin.PluginManager
import com.storyteller_f.giant_explorer.database.FileMDRecord
import com.storyteller_f.giant_explorer.database.FileSizeRecord
import com.storyteller_f.giant_explorer.database.FileTorrentRecord
import com.storyteller_f.giant_explorer.database.requireDatabase
import com.storyteller_f.giant_explorer.dialog.FilterDialogFragment
import com.storyteller_f.giant_explorer.dialog.SortDialogFragment
import com.storyteller_f.giant_explorer.dialog.activeFilters
import com.storyteller_f.giant_explorer.dialog.activeSortChains
import com.storyteller_f.giant_explorer.dialog.buildFilters
import com.storyteller_f.giant_explorer.dialog.buildSorts
import com.storyteller_f.giant_explorer.utils.getTorrentName
import com.storyteller_f.slim_ktx.exceptionMessage
import com.storyteller_f.sort_core.config.SortConfig
import com.storyteller_f.sort_core.config.SortConfigItem
import com.storyteller_f.sort_core.config.sortConfigAdapterFactory
import com.storyteller_f.ui_list.core.holders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.File
import java.math.BigInteger
import java.security.MessageDigest

val pluginManagerRegister = PluginManager()

// val defaultFactory = object : ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
//        return super.create(modelClass, extras)
//    }
// }

const val DEFAULT_DEBOUNCE = 200L
const val DEFAULT_WEB_VIEW_HEIGHT = 0.7f

object WorkCategory {
    const val MESSAGE_DIGEST = "message-digest"
    const val FOLDER_SIZE = "folder-size"
    const val TORRENT_NAME = "torrent-name"
}

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        holders(
            com.storyteller_f.giant_explorer.control.plugin.ui_list.HolderBuilder::add,
            com.storyteller_f.giant_explorer.control.remote.ui_list.HolderBuilder::add,
            com.storyteller_f.giant_explorer.control.task.ui_list.HolderBuilder::add,
            com.storyteller_f.giant_explorer.control.ui_list.HolderBuilder::add,
        )
        MainScope().launch {
            requireDatabase.bigTimeDao().fetchSuspend().groupBy {
                it.category
            }.forEach { entry ->
                WorkManager.getInstance(this@App).enqueueUniqueWork(
                    entry.key,
                    ExistingWorkPolicy.KEEP,
                    when (entry.key) {
                        WorkCategory.MESSAGE_DIGEST -> OneTimeWorkRequestBuilder<MDWorker>()
                        WorkCategory.FOLDER_SIZE -> OneTimeWorkRequestBuilder<FolderWorker>()
                        WorkCategory.TORRENT_NAME -> OneTimeWorkRequestBuilder<TorrentWorker>()
                        else -> error("unrecognized type ${entry.key}")
                    }.setInputData(
                        Data.Builder().putStringArray(
                            "folders",
                            entry.value.mapNotNull {
                                when {
                                    !it.enable -> null
                                    else -> it.uri.toString()
                                }
                            }.toTypedArray()
                        )
                            .build()
                    ).build()
                )
            }
            refreshPlugin(this@App)
        }
        activeFilters.value =
            EditorKey.createEditorKey(filesDir.absolutePath, FilterDialogFragment.SUFFIX).editor(
                FilterConfig.emptyFilterListener,
                filterConfigAdapterFactory,
                FilterDialogFragment.factory
            ).lastConfig?.run {
                configItems.filterIsInstance<FilterConfigItem>().buildFilters()
            }
        activeSortChains.value =
            EditorKey.createEditorKey(filesDir.absolutePath, SortDialogFragment.suffix).editor(
                SortConfig.emptySortListener,
                sortConfigAdapterFactory,
                SortDialogFragment.adapterFactory
            ).lastConfig?.run {
                configItems.filterIsInstance<SortConfigItem>().buildSorts()
            }
    }
}

fun refreshPlugin(context: Context) {
    File(context.filesDir, "plugins").listFiles { file ->
        file.extension == "apk" || file.extension == "zip"
    }?.forEach {
        pluginManagerRegister.foundPlugin(it)
    }
}

abstract class BigTimeWorker(
    private val context: Context,
    private val workerParams: WorkerParameters
) :
    CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val uriStringArray =
            workerParams.inputData.getStringArray("folders") ?: return Result.failure(
                Data.Builder().putString("error", "input empty").build()
            )
        return withContext(Dispatchers.IO) {
            val results = uriStringArray.asList().map { uriString ->
                when {
                    !context.checkFilePermission(uriString.toUri()) -> WorkerResult.Failure(
                        java.lang.Exception(
                            "don't have permission"
                        )
                    )

                    isStopped -> WorkerResult.Stopped
                    else -> doWork(context, uriString)
                }
            }
            if (results.none { it is WorkerResult.Failure || it is WorkerResult.Stopped }) {
                Result.success()
            } else {
                Result.failure(
                    Data.Builder().putString(
                        "error",
                        results.joinToString(",") {
                            when (it) {
                                is WorkerResult.Stopped -> "stop"
                                is WorkerResult.Failure -> it.exception.exceptionMessage
                                else -> ""
                            }
                        }
                    ).build()
                )
            }
        }
    }

    abstract suspend fun doWork(context: Context, uriString: String): WorkerResult
}

class FolderWorker(context: Context, workerParams: WorkerParameters) :
    BigTimeWorker(context, workerParams) {
    override suspend fun doWork(context: Context, uriString: String): WorkerResult {
        return try {
            val uri = uriString.toUri()
            val fileInstance = getFileInstance(context, uri)
            val record = context.requireDatabase.sizeDao().search(uri)
            val lastModified = fileInstance.getFileInfo().time.lastModified ?: 0
            if (record != null && record.lastUpdateTime > lastModified) {
                WorkerResult.SizeWorker(
                    record.size
                )
            } else {
                val listSafe = fileInstance.list()
                val mapNullNull = listSafe.directories.map {
                    doWork(context, it.fullPath)
                }
                val filter =
                    mapNullNull.filter { it is WorkerResult.Failure || it is WorkerResult.Stopped }
                if (filter.valid()) {
                    return filter.first()
                }

                val filesSize =
                    listSafe.files.map {
                        (it.kind as FileKind.File).size
                    }.plus(0).reduce { acc, s ->
                        acc + s
                    }
                val size =
                    filesSize + mapNullNull.map { (it as WorkerResult.SizeWorker).size }.plus(0)
                        .reduce { acc, s ->
                            acc + s
                        }
                context.requireDatabase.sizeDao()
                    .save(FileSizeRecord(uri, size, System.currentTimeMillis()))
                WorkerResult.SizeWorker(size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "work: ", e)
            WorkerResult.Failure(e)
        }
    }

    companion object {
        private const val TAG = "App"
    }
}

class MDWorker(context: Context, workerParams: WorkerParameters) :
    BigTimeWorker(context, workerParams) {

    override suspend fun doWork(context: Context, uriString: String): WorkerResult {
        return try {
            val uri = uriString.toUri()
            val fileInstance = getFileInstance(context, uri)
            val listSafe = fileInstance.list()
            listSafe.directories.mapNullNull {
                doWork(context, it.fullPath)
            }
            listSafe.files.forEach {
                val child = uri.buildUpon().path(it.fullPath).build()
                val search = context.requireDatabase.mdDao().search(child)
                val lastModified = it.time.lastModified ?: 0
                if ((search?.lastUpdateTime ?: 0) <= lastModified) {
                    processAndSave(fileInstance, it, context, child)
                }
            }
            WorkerResult.Success
        } catch (e: Exception) {
            WorkerResult.Failure(e)
        }
    }

    private suspend fun processAndSave(
        fileInstance: FileInstance,
        it: FileInfo,
        context: Context,
        child: Uri
    ) {
        getFileMD5(
            fileInstance.toChild(it.name, FileCreatePolicy.NotCreate)!!
        )?.let { data ->
            context.requireDatabase.mdDao()
                .save(FileMDRecord(child, data, System.currentTimeMillis()))
        }
    }

    companion object
}

class TorrentWorker(context: Context, workerParams: WorkerParameters) :
    BigTimeWorker(context, workerParams) {

    override suspend fun doWork(context: Context, uriString: String): WorkerResult {
        return try {
            val uri = uriString.toUri()
            val fileInstance = getFileInstance(context, uri)
            val listSafe = fileInstance.list()
            listSafe.directories.mapNullNull {
                doWork(context, it.fullPath)
            }
            listSafe.files.filter {
                it.extension == "torrent"
            }.forEach {
                val child = uri.buildUpon().path(it.fullPath).build()
                val search = context.requireDatabase.torrentDao().search(child)
                val lastModified = it.time.lastModified ?: 0
                if ((search?.lastUpdateTime ?: 0) <= lastModified) {
                    processAndSave(fileInstance, it, context, child)
                }
            }
            WorkerResult.Success
        } catch (e: Exception) {
            Log.e(TAG, "work: ", e)
            WorkerResult.Failure(e)
        }
    }

    private suspend fun processAndSave(
        fileInstance: FileInstance,
        it: FileInfo,
        context: Context,
        child: Uri
    ) {
        getTorrentName(
            fileInstance.toChild(it.name, FileCreatePolicy.NotCreate)!!,
        ).takeIf { it.isNotEmpty() }?.let { torrentName ->
            context.requireDatabase.torrentDao()
                .save(
                    FileTorrentRecord(
                        child,
                        torrentName,
                        System.currentTimeMillis()
                    )
                )
        }
    }

    companion object {
        private const val TAG = "App"
    }
}

sealed class WorkerResult {
    data object Success : WorkerResult()
    class Failure(val exception: Exception) : WorkerResult()
    data object Stopped : WorkerResult()
    class SizeWorker(val size: Long) : WorkerResult()
}

/**
 * 不影响后续文件夹执行，但是最终返回值会是 null
 */
inline fun <T, R> List<T>.mapNullNull(
    transform: (T) -> R?
): List<R>? {
    val destination = ArrayList<R>()
    var hasNull = false
    for (item in this) {
        val element = transform(item)
        if (element != null) {
            destination.add(element)
        } else if (!hasNull) {
            hasNull = true
        }
    }
    return if (hasNull) null else destination
}

const val PC_END_ON = 1024
const val RADIX = 16

suspend fun getFileMD5(fileInstance: FileInstance): String? {
    val buffer = ByteArray(PC_END_ON)
    return try {
        var len: Int
        val digest = MessageDigest.getInstance("MD5")
        fileInstance.getFileInputStream().buffered().use { stream ->
            while (stream.read(buffer).also { len = it } != -1) {
                yield()
                digest.update(buffer, 0, len)
            }
        }
        val bigInt = BigInteger(1, digest.digest())
        bigInt.toString(RADIX)
    } catch (_: Exception) {
        null
    }
}

fun <T> Collection<T>?.valid(): Boolean = !isNullOrEmpty()
