package com.storyteller_f.plugin_core

import android.net.Uri
import java.io.FileInputStream
import java.io.FileOutputStream

interface GiantExplorerPlugin {
    fun plugPluginManager(pluginManager: GiantExplorerPluginManager)

    /**
     * 至少包含一个。
     */
    fun group(file: List<Uri>, extension: String): List<Pair<List<String>, Int>>
}

interface GiantExplorerService {
    fun reportRunning()
}

interface GiantExplorerShellPlugin : GiantExplorerPlugin {
    suspend fun start(uri: Uri, id: Int)
}

interface GiantExplorerPluginManager {

    suspend fun fileInputStream(uri: Uri): FileInputStream

    suspend fun fileOutputStream(uri: Uri): FileOutputStream

    suspend fun listFiles(uri: Uri): List<Uri>

    /**
     * @return uri
     */
    suspend fun requestPath(initUri: Uri? = null): Uri

    suspend fun ensureDir(uri: Uri)

    /**
     * 一般来说不需要ensureFile，fileOutputStream 会自动处理
     */
    suspend fun ensureFile(uri: Uri)

    fun runInService(block: suspend GiantExplorerService.() -> Boolean)

    suspend fun isFile(uri: Uri): Boolean

    suspend fun getName(uri: Uri): String

    val resolver: Resolve

    interface Resolve {
        /**
         * @return uri
         */
        fun resolveChildUri(uri: Uri, name: String): Uri

        /**
         * 获取uri 对应的path 的parent uri
         * @return uri
         */
        fun resolveParentUri(uri: Uri): Uri

        /**
         * 获取uri 对应的path
         * @return path
         */
        fun resolvePath(uri: Uri): String?

        /**
         * 获取uri 对应的path 的parent path
         * @return path
         */
        fun resolveParentPath(uri: Uri): String?
    }
}
