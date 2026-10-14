package com.storyteller_f.giant_explorer.service

/** Absence is the desired result, including a concurrent removal during deletion. */
internal suspend fun ensureDeleted(exists: suspend () -> Boolean, delete: suspend () -> Boolean): Boolean {
    if (!exists()) return true
    delete()
    return !exists()
}
