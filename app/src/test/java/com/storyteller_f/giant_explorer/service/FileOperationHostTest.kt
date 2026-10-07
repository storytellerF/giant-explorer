package com.storyteller_f.giant_explorer.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationHostTest {
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
            host.forget("second").join()
            assertTrue(host.tasks.value.containsKey("second"))
            host.forget("first").join()
            assertEquals(setOf("second"), host.tasks.value.keys)
        } finally { host.close() }
    }
}
