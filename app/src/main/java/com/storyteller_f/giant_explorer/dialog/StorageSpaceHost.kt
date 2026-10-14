package com.storyteller_f.giant_explorer.dialog

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class StorageCapacity(val total: Long, val available: Long) {
    // Includes system-reserved space that cannot be used for new files.
    val used: Long get() = total - available

    companion object {
        fun fromBytes(total: Long, available: Long): StorageCapacity? =
            if (total > 0 && available in 0..total) StorageCapacity(total, available) else null
    }
}

data class StorageSpaceVolume(val name: String, val status: String, val capacity: StorageCapacity?)

data class StorageSpaceState(
    val loading: Boolean = true,
    val volumes: List<StorageSpaceVolume> = emptyList(),
    val failed: Boolean = false
)

/** Owns capacity loading without retaining any view or Android lifecycle. */
class StorageSpaceHost(
    coordination: CoroutineDispatcher,
    private val worker: CoroutineDispatcher,
    private val readVolumes: suspend () -> List<StorageSpaceVolume>
) {
    private val scope = CoroutineScope(SupervisorJob() + coordination)
    private val mutableState = MutableStateFlow(StorageSpaceState())
    val state = mutableState.asStateFlow()

    fun load() = scope.launch {
        mutableState.value = StorageSpaceState()
        try {
            val volumes = withContext(worker) { readVolumes().toList() }
            mutableState.value = StorageSpaceState(loading = false, volumes = volumes)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableState.value = StorageSpaceState(loading = false, failed = true)
        }
    }

    fun close() = scope.cancel()
}
