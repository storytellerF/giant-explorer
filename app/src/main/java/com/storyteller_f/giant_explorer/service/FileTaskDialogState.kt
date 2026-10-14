package com.storyteller_f.giant_explorer.service

sealed interface FileTaskOrigin {
    data object Detached : FileTaskOrigin
    data class Window(val id: String) : FileTaskOrigin
}

sealed interface FileTaskDialogState {
    data object Idle : FileTaskDialogState
    data class Showing(val taskKey: String) : FileTaskDialogState
}

/** Derived from durable task state; completion does not release an open dialog. */
fun Map<String, FileTaskSnapshot>.dialogState(window: FileTaskOrigin.Window): FileTaskDialogState =
    entries.firstOrNull { it.value.context.origin == window && it.value.showDialog }
        ?.let { FileTaskDialogState.Showing(it.key) } ?: FileTaskDialogState.Idle
