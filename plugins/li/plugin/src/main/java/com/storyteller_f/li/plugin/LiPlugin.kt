package com.storyteller_f.li.plugin

import android.net.Uri
import com.storyteller_f.plugin_core.GiantExplorerPluginManager
import com.storyteller_f.plugin_core.GiantExplorerShellPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LiPlugin : GiantExplorerShellPlugin {
    companion object {
        const val COMPRESS_EVENT = 109
        const val EXTRACT_EVENT = 108
    }

    private lateinit var pluginManager: GiantExplorerPluginManager
    override fun plugPluginManager(pluginManager: GiantExplorerPluginManager) {
        this.pluginManager = pluginManager
    }

    override fun group(file: List<Uri>, extension: String): List<Pair<List<String>, Int>> {
        return if (file.all {
                extension == "zip"
            }) listOf(listOf("archive", "extract to") to EXTRACT_EVENT)
        else listOf(listOf("archive", "compress") to COMPRESS_EVENT)
    }

    override suspend fun start(uri: Uri, id: Int) {
        val requestPath = pluginManager.requestPath()
        println("request path $requestPath")
        if (id == EXTRACT_EVENT) {
            extract(requestPath, uri)
        } else if (id == COMPRESS_EVENT) {
            compress(requestPath, uri)
        }
    }

    /**
     * @param uri 需要压缩的文件
     */
    private suspend fun compress(
        destPath: Uri,
        uri: Uri
    ) {
        pluginManager.runInService {
            reportRunning()
            val dest = pluginManager.fileOutputStream(destPath)
            val zipOutputStream = ZipOutputStream(dest)
            zipOutputStream.use {
                compress(it, uri, "")
            }
            true
        }

    }

    private suspend fun compress(dest: ZipOutputStream, uriString: Uri, offset: String) {
        val name = pluginManager.getName(uriString)
        if (pluginManager.isFile(uriString)) {
            withContext(Dispatchers.IO) {
                val zipEntry = ZipEntry("$offset/$name")
                dest.putNextEntry(zipEntry)
                putFileToEntry(uriString, dest)
            }

        } else {
            val listFiles = pluginManager.listFiles(uriString)
            listFiles.forEach {
                if (!pluginManager.isFile(it)) {
                    val subDir = ZipEntry("$offset/$it/")
                    dest.putNextEntry(subDir)
                }
                compress(dest, it, name)
            }
        }
    }

    private suspend fun putFileToEntry(file: Uri, dest: ZipOutputStream) {
        pluginManager.fileInputStream(file).use { stream ->
            val buffer = ByteArray(1024)
            stream.buffered().use {
                while (true) {
                    val offset = it.read(buffer)
                    if (offset != -1) {
                        dest.write(buffer, 0, offset)
                    } else break
                }
            }
        }
    }

    /**
     * @param dest 解压目的地
     */
    private suspend fun extract(dest: Uri, archivePath: Uri) {
        val archiveStream = pluginManager.fileInputStream(archivePath)
        pluginManager.runInService {
            reportRunning()
            ZipInputStream(archiveStream).use { stream ->
                while (true) {
                    stream.nextEntry?.let {
                        processEntry(dest, it, stream)
                    } ?: break
                }
            }
            true
        }
    }

    private suspend fun processEntry(dest: Uri, nextEntry: ZipEntry, stream: ZipInputStream) {
        val childDestUri = pluginManager.resolver.resolveChildUri(dest, nextEntry.name)
        if (nextEntry.isDirectory) {
            pluginManager.ensureDir(childDestUri)
        } else {
            readEntryToFile(childDestUri, stream)
        }
    }

    private suspend fun readEntryToFile(dest: Uri, stream: ZipInputStream) {
        val destStream = pluginManager.fileOutputStream(dest)
        val buffer = ByteArray(1024)
        destStream.buffered().use {
            while (true) {
                val offset = stream.read(buffer)
                if (offset != -1) {
                    it.write(buffer, 0, offset)
                } else break
            }
        }
    }


}