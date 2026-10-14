package com.storyteller_f.giant_explorer.service

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTaskWindowTest {
    @Test(timeout = 10000)
    fun concurrentSubmissionsOnlyAdmitOneTaskPerWindow() = runBlocking {
        val host = FileOperationHost(Dispatchers.Default.limitedParallelism(1), null)
        val window = FileTaskOrigin.Window("first-window")
        try {
            val accepted = (1..20).map { index ->
                async { host.start("task-$index", FileTaskContext(origin = window)).await() }
            }.awaitAll()
            assertEquals(1, accepted.count { it })
            val state = host.tasks.value.dialogState(window) as FileTaskDialogState.Showing
            assertEquals(setOf(state.taskKey), host.tasks.value.keys)
            assertTrue(host.start("other-window", FileTaskContext(origin = FileTaskOrigin.Window("second"))).await())
            assertEquals(state, host.tasks.value.dialogState(FileTaskOrigin.Window(window.id)))
        } finally { host.close() }
    }

    @Test(timeout = 10000)
    fun everyTaskStatusKeepsWindowOccupiedUntilDialogDismissal() = runBlocking {
        for (status in FileTaskStatus.entries) {
            val host = FileOperationHost(Dispatchers.Default.limitedParallelism(1), null)
            val window = FileTaskOrigin.Window("window")
            try {
                val context = FileTaskContext(origin = window)
                assertTrue(host.start("first", context).await())
                when (status) {
                    FileTaskStatus.COMPUTING -> Unit
                    FileTaskStatus.RUNNING -> host.running("first", FileTaskCounts(1, 0, 0)).join()
                    FileTaskStatus.CANCELLING -> host.cancel("first").join()
                    else -> host.finish("first", status).join()
                }
                assertFalse(host.start("blocked", context).await())
                assertFalse(host.tasks.value.containsKey("blocked"))
                host.dismiss("first").join()
                assertEquals(FileTaskDialogState.Idle, host.tasks.value.dialogState(window))
                assertTrue(host.start("next", context).await())
                assertEquals(FileTaskDialogState.Showing("next"), host.tasks.value.dialogState(window))
            } finally { host.close() }
        }
    }
}
