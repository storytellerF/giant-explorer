package com.storyteller_f.giant_explorer.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationEventBusTest {
    @Test(timeout = 10000)
    fun completionReachesMultipleFileLists() = runBlocking {
        val bus = FileOperationEventBus()
        val firstList = async(start = CoroutineStart.UNDISPATCHED) { bus.completions.first() }
        val secondList = async(start = CoroutineStart.UNDISPATCHED) { bus.completions.first() }

        assertEquals("result", bus.runTask { "result" })

        assertEquals(Unit, firstList.await())
        assertEquals(Unit, secondList.await())
    }

    @Test(timeout = 10000)
    fun backgroundCompletionIsReplayedToReturningList() = runBlocking {
        val bus = FileOperationEventBus()
        val oldList = async(start = CoroutineStart.UNDISPATCHED) { bus.completions.first() }
        oldList.cancelAndJoin()

        repeat(3) { bus.runTask { Unit } }

        assertEquals(Unit, bus.completions.first())
        assertEquals(1, bus.completions.replayCache.size)
    }

    @Test(timeout = 10000)
    fun unsuccessfulTaskStillInvalidatesLists() = runBlocking {
        val bus = FileOperationEventBus()
        assertEquals(false, bus.runTask { false })
        assertEquals(Unit, bus.completions.first())
    }

    @Test(timeout = 10000)
    fun errorIsPropagatedAndListsAreInvalidated() = runBlocking {
        val bus = FileOperationEventBus()
        val failure = IllegalStateException("fixture failure")
        val caught = runCatching { bus.runTask { throw failure } }.exceptionOrNull()

        assertSame(failure, caught)
        assertEquals(Unit, bus.completions.first())
    }

    @Test(timeout = 10000)
    fun cancellationInvalidatesPartiallyChangedLists() = runBlocking {
        val bus = FileOperationEventBus()
        val started = CompletableDeferred<Unit>()
        val task = launch {
            bus.runTask {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        task.cancelAndJoin()

        assertTrue(task.isCancelled)
        assertEquals(Unit, bus.completions.first())
    }
}
