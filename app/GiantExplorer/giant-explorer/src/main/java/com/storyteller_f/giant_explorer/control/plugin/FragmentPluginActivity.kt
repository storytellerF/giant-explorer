package com.storyteller_f.giant_explorer.control.plugin

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.storyteller_f.compat_ktx.packageInfoCompat
import com.storyteller_f.file_system.getFileInstance
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.pluginManagerRegister
import com.storyteller_f.plugin_core.GiantExplorerPlugin
import com.storyteller_f.plugin_core.GiantExplorerPluginManager
import com.storyteller_f.plugin_core.GiantExplorerService
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

const val GIANT_EXPLORER_PLUGIN_INI = "META-INF/giant-explorer-plugin.ini"
suspend fun Context.fileInputStream1(uriString: String) =
    getFileInstance(this, uriString.toUri())!!.apply {
        createFile()
    }.getFileInputStream()

abstract class DefaultPluginManager(val context: Context) : GiantExplorerPluginManager {

    override val resolver: GiantExplorerPluginManager.Resolve
        get() = object : GiantExplorerPluginManager.Resolve {
            override fun resolveParentUri(uri: Uri): Uri = resolveParentUri1(uri)!!

            override fun resolveParentPath(uri: Uri): String? {
                val resolvePath =
                    FileSystemProviderResolver.resolve(uri)?.path ?: return null
                return File(resolvePath).parent
            }

            override fun resolvePath(uri: Uri): String? {
                return FileSystemProviderResolver.resolve(uri)?.path
            }

            override fun resolveChildUri(uri: Uri, name: String): Uri {
                return uri.buildUpon().appendPath(name).build()
            }
        }

    override suspend fun fileInputStream(uri: Uri): FileInputStream {
        return getFileInstance(context, uri)!!.getFileInputStream()
    }

    override suspend fun fileOutputStream(uri: Uri): FileOutputStream {
        return getFileInstance(context, uri)!!.apply {
            createFile()
        }.getFileOutputStream()
    }

    override suspend fun listFiles(uri: Uri): List<Uri> {
        return getFileInstance(context, uri)!!.list().let { filesAndDirectories ->
            filesAndDirectories.files.map {
                it.uri
            } + filesAndDirectories.directories.map {
                it.uri
            }
        }
    }

    private fun resolveParentUri1(uri: Uri): Uri? {
        val path = FileSystemProviderResolver.resolve(uri)?.path ?: return null
        val parent = File(path).parent ?: return null
        return FileSystemProviderResolver.share(false, uri.buildUpon().path(parent).build())
    }

    override suspend fun ensureDir(uri: Uri) {
        getFileInstance(context, uri)!!.createDirectory()
    }

    override suspend fun ensureFile(uri: Uri) {
        getFileInstance(context, uri)!!.createFile()
    }

    override suspend fun isFile(uri: Uri): Boolean {
        return getFileInstance(context, uri)!!.fileKind().isFile
    }

    override suspend fun getName(uri: Uri): String {
        return getFileInstance(context, uri)!!.name
    }
}

class FragmentPluginActivity : AppCompatActivity() {
    private lateinit var pluginName: String
    private lateinit var pluginFragments: List<String>
    private val pluginFile by lazy { File(filesDir, "plugins/$pluginName") }

    private val pluginResources by lazy {
        val absolutePath = pluginFile.absolutePath
        val packageArchiveInfo = packageManager.packageInfoCompat(absolutePath)
        val applicationInfo = packageArchiveInfo?.applicationInfo
        if (applicationInfo != null) {
            applicationInfo.publicSourceDir = absolutePath
            applicationInfo.sourceDir = absolutePath
            packageManager.getResourcesForApplication(applicationInfo)
        } else {
            null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment_plugin)
        val uri = intent.data
        pluginName = intent.getStringExtra("plugin-name")!!
        val pluginManager = object : DefaultPluginManager(this) {
            override suspend fun requestPath(initUri: Uri?): Uri {
                TODO("Not yet implemented")
            }

            override fun runInService(block: suspend GiantExplorerService.() -> Boolean) {
                TODO("Not yet implemented")
            }
        }
        lifecycleScope.launch {
            val revolvePlugin = pluginManagerRegister.resolvePluginName(
                pluginName,
                this@FragmentPluginActivity
            ) as FragmentPluginConfiguration
            val dexClassLoader = revolvePlugin.classLoader
            val name = revolvePlugin.startFragment
            pluginFragments = revolvePlugin.pluginFragments
            val loadClass = dexClassLoader.loadClass(name)
            val newInstance = loadClass.getDeclaredConstructor().newInstance()
            if (newInstance is Fragment) {
                if (newInstance is GiantExplorerPlugin) {
                    newInstance.plugPluginManager(pluginManager)
                }
                newInstance.arguments = Bundle().apply {
                    putParcelable("uri", uri)
                }
                if (savedInstanceState == null) {
                    val beginTransaction = supportFragmentManager.beginTransaction()
                    beginTransaction.replace(R.id.content, newInstance)
                    beginTransaction.commit()
                }
            }
        }
    }

    override fun getResources(): Resources {
        val stackTrace = Thread.currentThread().stackTrace
        val listOf = if (this::pluginFragments.isInitialized) pluginFragments else listOf()
        if (stackTrace.any {
                listOf.contains(it.className)
            }
        ) {
            return pluginResources!!
        }
        return super.getResources()
    }
}
