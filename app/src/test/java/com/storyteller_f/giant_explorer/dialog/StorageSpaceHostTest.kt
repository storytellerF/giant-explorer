package com.storyteller_f.giant_explorer.dialog

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

class StorageSpaceHostTest {
    @Test
    fun capacityUsesSpaceAvailableForNewFiles() {
        val capacity = StorageCapacity.fromBytes(128_000_000_000L, 96_000_000_000L)!!
        assertEquals(32_000_000_000L, capacity.used)
        assertEquals(capacity.total, capacity.used + capacity.available)
        assertEquals(128L, StorageCapacity.fromBytes(128, 0)!!.used)
        assertEquals(0L, StorageCapacity.fromBytes(128, 128)!!.used)
    }

    @Test
    fun unavailableOrInvalidCapacityIsNotShownAsZero() {
        assertNull(StorageCapacity.fromBytes(0, 0))
        assertNull(StorageCapacity.fromBytes(-1, 0))
        assertNull(StorageCapacity.fromBytes(128, -1))
        assertNull(StorageCapacity.fromBytes(128, 129))
    }

    @Test(timeout = 10000)
    fun capacityReadRunsOnInjectedWorkerAndPublishesState() = runBlocking {
        val worker = Dispatchers.IO.limitedParallelism(1)
        val volume = StorageSpaceVolume("fixture", "mounted", StorageCapacity.fromBytes(128, 96))
        val host = StorageSpaceHost(Dispatchers.Default.limitedParallelism(1), worker) {
            assertEquals(worker, currentCoroutineContext()[ContinuationInterceptor])
            listOf(volume)
        }
        try {
            host.load().join()
            assertEquals(StorageSpaceState(loading = false, volumes = listOf(volume)), host.state.value)
        } finally { host.close() }
    }

    @Test(timeout = 10000)
    fun readFailureProducesAnExplicitErrorState() = runBlocking {
        val host = StorageSpaceHost(Dispatchers.Default.limitedParallelism(1), Dispatchers.IO) {
            throw SecurityException("fixture")
        }
        try {
            host.load().join()
            assertEquals(StorageSpaceState(loading = false, failed = true), host.state.value)
        } finally { host.close() }
    }

    @Test(timeout = 10000)
    fun closingDialogCancelsReadWithoutReportingFailure() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val host = StorageSpaceHost(Dispatchers.Default.limitedParallelism(1), Dispatchers.IO) {
            started.complete(Unit)
            awaitCancellation()
        }
        try {
            val read = host.load()
            started.await()
            host.close()
            read.join()
            assertTrue(read.isCancelled)
            assertFalse(host.state.value.failed)
        } finally { host.close() }
    }
}
