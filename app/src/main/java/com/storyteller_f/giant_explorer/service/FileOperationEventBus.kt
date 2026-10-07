package com.storyteller_f.giant_explorer.service

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** File-list invalidations, independent of activities and task result callbacks. */
class FileOperationEventBus {
    private val mutableCompletions = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val completions = mutableCompletions.asSharedFlow()

    suspend fun <T> runTask(block: suspend () -> T): T = try {
        block()
    } finally {
        // Failed or cancelled operations may have changed some files too.
        // Replaying an invalidation refreshes lists returning from the background.
        mutableCompletions.tryEmit(Unit)
    }

    companion object {
        val shared = FileOperationEventBus()
    }
}
