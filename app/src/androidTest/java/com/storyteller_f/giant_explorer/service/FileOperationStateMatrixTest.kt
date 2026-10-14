package com.storyteller_f.giant_explorer.service

import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.file_system.getFileInstance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileOperationStateMatrixTest {
    @Test fun missingSourcesAndInvalidDestinationsFailWithoutDeletingOrOverwritingFiles() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "operation-failure-matrix").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val binder = FileOperateBinder(app, scope, FileOperationEventBus())
            val source = File(root, "source.txt").apply { writeText("fixture") }
            val selected = getFileInstance(app, source.toUri())!!.getFileInfo()
            val folder = File(root, "destination").apply { mkdirs() }
            val target = getFileInstance(app, folder.toUri())!!
            source.delete()
            binder.moveOrCopy(target, listOf(selected), null, false, "missing")
            val missing = awaitTerminal(binder, "missing")
            assertEquals(FileTaskStatus.FAILED, missing.status)
            assertFalse(missing.countsKnown)
            source.writeText("fixture")
            val blocked = File(root, "not-a-directory").apply { writeText("unchanged") }
            for (move in listOf(false, true)) {
                val key = "blocked-$move"
                binder.moveOrCopy(getFileInstance(app, blocked.toUri())!!, listOf(selected), null, move, key)
                val result = awaitTerminal(binder, key)
                assertEquals(FileTaskStatus.FAILED, result.status)
                assertEquals("fixture", source.readText())
                assertEquals("unchanged", blocked.readText())
            }
            val protectedDirectory = File(root, "read-only").apply { mkdirs() }
            val protectedFile = File(protectedDirectory, "protected.txt").apply { writeText("fixture") }
            val protected = getFileInstance(app, protectedFile.toUri())!!.getFileInfo()
            assertTrue(protectedDirectory.setWritable(false, false))
            try {
                binder.delete(protected, listOf(protected), "protected-delete")
                assertEquals(FileTaskStatus.FAILED, awaitTerminal(binder, "protected-delete").status)
                assertTrue(protectedFile.exists())
            } finally {
                protectedDirectory.setWritable(true, true)
            }
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test fun emptyDirectoryAndZeroByteFileCopyReachCompletion() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "operation-empty-matrix").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val source = File(root, "empty-folder").apply { mkdirs() }
            val empty = File(root, "empty.txt").apply { writeText("") }
            val target = File(root, "target").apply { mkdirs() }
            val binder = FileOperateBinder(app, scope, FileOperationEventBus())
            val selected = listOf(source, empty).map { getFileInstance(app, it.toUri())!!.getFileInfo() }
            binder.moveOrCopy(getFileInstance(app, target.toUri())!!, selected, null, false, "empty-copy")
            val result = awaitTerminal(binder, "empty-copy")
            assertEquals(result.message, FileTaskStatus.SUCCEEDED, result.status)
            assertEquals(FileTaskCounts(1, 1, 0), result.total)
            assertEquals(100, result.progress)
            assertTrue(File(target, "empty-folder").isDirectory)
            assertTrue(File(target, "empty.txt").isFile)
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test fun copyMoveAndDeleteMixedTreesAndZeroByteFilesReportCorrectResults() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(app.cacheDir, "operation-matrix").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val binder = FileOperateBinder(app, scope, FileOperationEventBus())
            for (move in listOf(false, true)) {
                val source = File(root, "source-$move").apply { mkdirs() }
                File(source, "empty.txt").writeText("")
                File(source, "child").apply { mkdirs() }.resolve("notes.txt").writeText("fixture")
                val destination = File(root, "destination-$move").apply { mkdirs() }
                val item = getFileInstance(app, source.toUri())!!.getFileInfo()
                val target = getFileInstance(app, destination.toUri())!!
                val key = "transfer-$move"
                binder.moveOrCopy(target, listOf(item), null, move, key)
                val result = awaitTerminal(binder, key)
                assertEquals(result.message, FileTaskStatus.SUCCEEDED, result.status)
                assertEquals(if (move) FileOperationKind.MOVE else FileOperationKind.COPY, result.context.operation)
                assertEquals(FileTaskCounts(2, 2, 7), result.total)
                assertEquals(FileTaskCounts(), result.remaining)
                assertEquals("fixture", File(destination, "${source.name}/child/notes.txt").readText())
                assertTrue(File(destination, "${source.name}/empty.txt").exists())
                assertEquals(!move, source.exists())
                val copied = getFileInstance(app, File(destination, source.name).toUri())!!.getFileInfo()
                binder.delete(target.getFileInfo(), listOf(copied), "delete-$move")
                assertEquals(FileTaskStatus.SUCCEEDED, awaitTerminal(binder, "delete-$move").status)
                assertFalse(File(destination, source.name).exists())
            }
        } finally {
            scope.cancel()
            root.deleteRecursively()
        }
    }

    @Test fun pluginUnknownProgressFailureAndActualCancellationAreDistinct() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val binder = FileOperateBinder(app, scope, FileOperationEventBus())
            val entered = CompletableDeferred<Unit>()
            binder.pluginTask("cancel") {
                reportRunning()
                entered.complete(Unit)
                awaitCancellation()
            }
            withTimeout(10_000) { entered.await() }
            val running = withTimeout(10_000) {
                binder.taskHost.tasks.first { it["cancel"]?.status == FileTaskStatus.RUNNING }
            }.getValue("cancel")
            assertFalse(running.countsKnown)
            binder.taskHost.cancel("cancel").join()
            assertEquals(FileTaskStatus.CANCELLED, awaitTerminal(binder, "cancel").status)
            binder.pluginTask("failure") { throw SecurityException("Fixture permission failure") }
            val failed = awaitTerminal(binder, "failure")
            assertEquals(FileTaskStatus.FAILED, failed.status)
            assertTrue(failed.message.contains("permission"))
            binder.pluginTask("success") { reportRunning(); true }
            assertEquals(FileTaskStatus.SUCCEEDED, awaitTerminal(binder, "success").status)
        } finally { scope.cancel() }
    }

    private suspend fun awaitTerminal(binder: FileOperateBinder, key: String): FileTaskSnapshot = withTimeout(20_000) {
        binder.taskHost.tasks.first {
            it[key]?.status in setOf(FileTaskStatus.SUCCEEDED, FileTaskStatus.FAILED, FileTaskStatus.CANCELLED)
        }.getValue(key)
    }
}
