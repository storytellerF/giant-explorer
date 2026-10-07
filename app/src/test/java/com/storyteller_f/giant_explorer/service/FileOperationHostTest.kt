package com.storyteller_f.giant_explorer.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationHostTest {
    @Test(timeout = 10000)
    fun backgroundTaskRetainsProgressWithoutRequestingAnotherDialog() = runBlocking {
        val host = FileOperationHost(Dispatchers.Default.limitedParallelism(1), null)
        try {
            host.start("background", FileTaskContext(FileOperationKind.DELETE)).await()
            host.running("background", FileTaskCounts(2, 0, 1024)).join()
            host.dismiss("background").join()
            host.progress("background", 50, FileTaskCounts(1, 0, 512)).join()
            val task = host.tasks.value.getValue("background")
            assertFalse(task.showDialog)
            assertEquals(FileTaskStatus.RUNNING, task.status)
            assertEquals(50, task.progress)
            host.finish("background", FileTaskStatus.SUCCEEDED).join()
            assertFalse(host.tasks.value.getValue("background").showDialog)
        } finally { host.close() }
    }

    @Test(timeout = 10000)
    fun cancellationBeforeWorkerAttachmentIsNotLost() = runBlocking {
        val host = FileOperationHost(Dispatchers.Default.limitedParallelism(1), null)
        val worker = Job()
        try {
            host.start("task", FileTaskContext(FileOperationKind.MOVE)).await()
            host.cancel("task").join()
            host.attachWorker("task", worker).join()
            assertTrue(worker.isCancelled)
            host.running("task", FileTaskCounts(2, 0, 1024)).join()
            assertEquals(FileTaskStatus.CANCELLING, host.tasks.value["task"]!!.status)
            host.finish("task", FileTaskStatus.CANCELLED).join()
            assertEquals(FileTaskStatus.CANCELLED, host.tasks.value["task"]!!.status)
            assertEquals(FileTaskCounts(2, 0, 1024), host.tasks.value["task"]!!.remaining)
        } finally { host.close() }
    }

    @Test(timeout = 10000)
    fun cancelledAndFailedTasksKeepPartialProgressAndCompletedTasksCannotBeCancelled() = runBlocking {
        val host = FileOperationHost(Dispatchers.Default.limitedParallelism(1), null)
        try {
            for (status in listOf(FileTaskStatus.FAILED, FileTaskStatus.CANCELLED, FileTaskStatus.SUCCEEDED)) {
                val key = status.name
                host.start(key, FileTaskContext(FileOperationKind.COPY)).await()
                host.running(key, FileTaskCounts(2, 0, 1024)).join()
                host.progress(key, remaining = FileTaskCounts(1, 0, 512)).join()
                host.progress(key, 50).join()
                host.finish(key, status).join()
                host.cancel(key).join()
                val task = host.tasks.value.getValue(key)
                assertEquals(status, task.status)
                assertEquals(if (status == FileTaskStatus.SUCCEEDED) 100 else 50, task.progress)
                val expectedRemaining = if (status == FileTaskStatus.SUCCEEDED) {
                    FileTaskCounts()
                } else {
                    FileTaskCounts(1, 0, 512)
                }
                assertEquals(expectedRemaining, task.remaining)
            }
        } finally { host.close() }
    }

    @Test(timeout = 10000)
    fun duplicateStartKeepsExistingProgress() = runBlocking {
        val host = FileOperationHost(Dispatchers.Default.limitedParallelism(1), null)
        try {
            assertTrue(host.start("first").await())
            host.running("first", FileTaskCounts(2, 1, 12)).join()
            host.progress("first", 40).join()
            assertFalse(host.start("first").await())
            assertEquals(40, host.tasks.value["first"]!!.progress)
            val starts = List(4) { async { host.start("second").await() } }
            assertEquals(1, starts.count { it.await() })
        } finally { host.close() }
    }

    @Test(timeout = 10000)
    fun newTaskDoesNotReusePreviousResultAndLateProgressCannotOverrideCompletion() = runBlocking {
        val host = FileOperationHost(Dispatchers.Default.limitedParallelism(1), null)
        try {
            host.start("first").await()
            host.running("first", FileTaskCounts(3, 1, 12)).join()
            host.finish("first", FileTaskStatus.SUCCEEDED).join()
            host.progress("first", 2).join()
            host.start("second").await()
            assertEquals(FileTaskSnapshot(), host.tasks.value["second"])
            assertEquals(100, host.tasks.value["first"]!!.progress)
            assertEquals(FileTaskCounts(), host.tasks.value["first"]!!.remaining)
            host.finish("second", FileTaskStatus.FAILED, "fixture failure").join()
            assertEquals(FileTaskStatus.SUCCEEDED, host.tasks.value["first"]!!.status)
            assertEquals("fixture failure", host.tasks.value["second"]!!.message)
        } finally { host.close() }
    }

    @Test(timeout = 10000)
    fun closingOneCompletedDialogDoesNotRemoveOtherTaskState() = runBlocking {
        val host = FileOperationHost(Dispatchers.Default.limitedParallelism(1), null)
        try {
            host.start("first").await()
            host.start("second").await()
            host.finish("first", FileTaskStatus.SUCCEEDED).join()
            host.dismiss("second").join()
            assertTrue(host.tasks.value.containsKey("second"))
            host.dismiss("first").join()
            assertEquals(setOf("second"), host.tasks.value.keys)
        } finally { host.close() }
    }
}
