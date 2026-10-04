package com.storyteller_f.giant_explorer.service

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import android.webkit.MimeTypeMap
import com.storyteller_f.file_system.FileInstanceFactory
import com.storyteller_f.file_system.instance.FileInstance
import com.storyteller_f.file_system.instance.FileKind
import com.storyteller_f.file_system.simplePath
import com.storyteller_f.file_system.size
import com.storyteller_f.giant_explorer.control.plugin.FileSystemProviderResolver
import com.storyteller_f.plugin_core.FileSystemProviderConstant
import kotlinx.coroutines.runBlocking
import java.util.ServiceLoader

class FileSystemProvider : ContentProvider() {

    override fun onCreate() = true

    private suspend fun current(uri: Uri): FileInstance? {
        val c = context ?: return null
        val path = FileSystemProviderResolver.resolve(uri) ?: return null
        val safeUri = path.buildUpon().path(simplePath(path.path!!)).build()
        // Binder callers may have a boot context class loader without application factories.
        val factories = ServiceLoader.load(FileInstanceFactory::class.java, c.classLoader)
        return factories.firstNotNullOfOrNull { it.buildInstance(c, safeUri) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        return runBlocking {
            val fileInstance = current(uri) ?: return@runBlocking null
            fileInstance.getFileInputStream().use { ParcelFileDescriptor.dup(it.fd) }
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return runBlocking {
            val fileInstance = current(uri) ?: return@runBlocking null
            if (fileInstance.fileKind().isFile) {
                MatrixCursor(fileProjection).apply {
                    val file = fileInstance.getFileInfo()
                    addRow(arrayOf(file.name, file.fullPath, fileInstance.size().toString()))
                }
            } else {
                queryFileInstanceChild(fileInstance)
            }
        }
    }

    private fun queryFileInstanceChild(fileInstance: FileInstance): MatrixCursor {
        val list = runBlocking {
            fileInstance.list()
        }
        Log.i(
            TAG,
            "queryFileInstance: ${fileInstance.path} ${list.directories.size} ${list.files.size}"
        )
        val matrixCursor = MatrixCursor(fileProjection)
        list.directories.forEach {
            matrixCursor.addRow(
                arrayOf(
                    it.name,
                    it.fullPath,
                    "0",
                    DocumentsContract.Document.MIME_TYPE_DIR
                )
            )
        }
        list.files.forEach {
            matrixCursor.addRow(
                arrayOf(
                    it.name,
                    it.fullPath,
                    (it.kind as FileKind.File).size.toString(),
                    singleton.getMimeTypeFromExtension(it.extension)
                )
            )
        }
        return matrixCursor
    }

    override fun getType(uri: Uri): String? {
        return runBlocking {
            val current = current(uri) ?: return@runBlocking null
            if (current.fileKind().isDirectory) {
                DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                singleton.getMimeTypeFromExtension(current.getFileInfo().extension)
            }
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        TODO("Not yet implemented")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        TODO("Not yet implemented")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        TODO("Not yet implemented")
    }

    companion object {
        private const val TAG = "FileSystemProvider"
        val singleton: MimeTypeMap = MimeTypeMap.getSingleton()
        private val fileProjection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            FileSystemProviderConstant.FILE_PATH,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
    }
}
