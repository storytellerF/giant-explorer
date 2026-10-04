package com.storyteller_f.giant_explorer.service

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.file_system.getFileInstance
import com.storyteller_f.giant_explorer.control.plugin.FileSystemProviderResolver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.ref.WeakReference

@RunWith(AndroidJUnit4::class)
class FileOperationRegressionTest {
    @Test fun sharedProviderReadsWithoutCallerApplicationClassLoader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "binder-loader-regression.txt").apply { writeText("fixture") }
        val uri = FileSystemProviderResolver.share(false, source.toUri())!!
        // Binder pool threads do not necessarily inherit the application's context class loader.
        val caller = Thread.currentThread()
        val originalLoader = caller.contextClassLoader
        val contents = try {
            caller.contextClassLoader = ClassLoader.getSystemClassLoader().parent
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        } finally {
            caller.contextClassLoader = originalLoader
            source.delete()
        }
        assertEquals("fixture", contents)
    }

    @Test fun copyAssessmentAcceptsOrdinaryFileAndReportsInvalidDirectory() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "copy-regression").apply { mkdirs() }
        val source = File(root, "notes.txt").apply { writeText("fixture") }
        val folder = File(root, "folder").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dest = getFileInstance(context, folder.toUri())!!
            val file = getFileInstance(context, source.toUri())!!.getFileInfo()
            val assessment = TaskAssessor(listOf(file), context, dest).assess()
            assertEquals(1, assessment.fileCount)
            assertEquals(source.length(), assessment.size)

            val copied = CompletableDeferred<Unit>()
            val copyCallback = object : FileOperateService.FileOperateResultContainer {
                override fun onError(errorMessage: String?) {
                    copied.completeExceptionally(IllegalStateException(errorMessage))
                }
                override fun onSuccess(uri: Uri?, originUri: Uri?) { copied.complete(Unit) }
                override fun onCancel() { copied.cancel() }
            }
            val copyBinder = FileOperateBinder(context, scope)
            copyBinder.fileOperateResultContainer = WeakReference(copyCallback)
            instrumentation.runOnMainSync {
                copyBinder.moveOrCopy(dest, listOf(file), null, false, "valid-copy")
            }
            withTimeout(10_000) { copied.await() }
            // The binder intentionally keeps only a weak reference to its UI callback.
            assertSame(copyCallback, copyBinder.fileOperateResultContainer.get())
            assertEquals("fixture", File(folder, "notes.txt").readText())
            assertEquals("fixture", source.readText())

            val failed = CompletableDeferred<String?>()
            val callback = object : FileOperateService.FileOperateResultContainer {
                override fun onError(errorMessage: String?) { failed.complete(errorMessage) }
                override fun onSuccess(uri: Uri?, originUri: Uri?) { failed.complete("unexpected success") }
                override fun onCancel() { failed.cancel() }
            }
            val binder = FileOperateBinder(context, scope)
            binder.fileOperateResultContainer = WeakReference(callback)
            val directory = getFileInstance(context, root.toUri())!!.getFileInfo()
            instrumentation.runOnMainSync {
                binder.moveOrCopy(dest, listOf(directory), null, false, "invalid-copy")
            }
            assertEquals("不能将父文件夹移动到子文件夹", withTimeout(10_000) { failed.await() })
            assertSame(callback, binder.fileOperateResultContainer.get())
            instrumentation.runOnMainSync {
                assertEquals(FileOperateBinder.state_error, binder.state.value)
                assertNotNull(binder.map["invalid-copy"]?.message)
            }
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test fun sharedProviderAllowsTemporaryReadGrants() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "grant-regression.txt").apply { writeText("fixture") }
        val uri = FileSystemProviderResolver.share(false, source.toUri())!!
        try {
            // A missing grantUriPermissions declaration makes this framework call throw.
            context.grantUriPermission(instrumentation.context.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.contentResolver.openInputStream(uri).use { stream ->
                assertEquals("fixture", stream!!.bufferedReader().readText())
            }
        } finally {
            context.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            source.delete()
        }
    }
}
