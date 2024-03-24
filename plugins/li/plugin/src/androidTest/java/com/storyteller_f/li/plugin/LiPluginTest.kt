package com.storyteller_f.li.plugin

import android.net.Uri
import androidx.core.net.toFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.plugin_core.GiantExplorerPluginManager
import com.storyteller_f.plugin_core.GiantExplorerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class LiPluginTest {

    @Test
    fun test() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val zipFile = File(appContext.filesDir, "target.zip")
        var nextPath = zipFile
        val helloTxtFile = File(appContext.filesDir, "hello.txt")
        helloTxtFile.writeText("hello")
        val pluginManager = object : GiantExplorerPluginManager {
            override suspend fun fileInputStream(uri: Uri): FileInputStream {
                return uri.toFile().inputStream()
            }

            override suspend fun fileOutputStream(uri: Uri): FileOutputStream {
                ensureFile(uri)
                return uri.toFile().outputStream()
            }

            override suspend fun listFiles(uri: Uri): List<Uri> {
                TODO("Not yet implemented")
            }

            override suspend fun requestPath(initUri: Uri?): Uri {
                return Uri.fromFile(nextPath)
            }

            override suspend fun ensureDir(uri: Uri) {
                TODO("Not yet implemented")
            }

            override suspend fun ensureFile(uri: Uri) {
                val file = uri.toFile()
                if (!file.exists()) {
                    file.parentFile?.let {
                        if (!it.exists())
                            it.mkdirs()
                    }
                    withContext(Dispatchers.IO) {
                        file.createNewFile()
                    }
                }
            }

            override fun runInService(block: suspend GiantExplorerService.() -> Boolean) {
                val value = object : GiantExplorerService {
                    override fun reportRunning() {
                    }

                }
                runBlocking {
                    value.block()
                }
            }

            override val resolver: GiantExplorerPluginManager.Resolve
                get() = object : GiantExplorerPluginManager.Resolve {
                    override fun resolveParentUri(uri: Uri): Uri {
                        TODO("Not yet implemented")
                    }

                    override fun resolvePath(uri: Uri): String? {
                        TODO("Not yet implemented")
                    }

                    override fun resolveParentPath(uri: Uri): String? {
                        TODO("Not yet implemented")
                    }

                    override fun resolveChildUri(uri: Uri, name: String): Uri {
                        return uri.buildUpon().appendPath(name).build()
                    }
                }


            override suspend fun isFile(uri: Uri): Boolean {
                return uri.toFile().isFile
            }

            override suspend fun getName(uri: Uri): String {
                return uri.toFile().name
            }


        }
        runBlocking {
            val plugin = LiPlugin().apply {
                plugPluginManager(pluginManager)
            }
            plugin.start(Uri.fromFile(helloTxtFile), LiPlugin.COMPRESS_EVENT)
            val output = File(appContext.filesDir, "extract")
            nextPath = output
            plugin.start(Uri.fromFile(zipFile), LiPlugin.EXTRACT_EVENT)
            assertEquals("hello", File(output, "hello.txt").readText())
        }
    }
}