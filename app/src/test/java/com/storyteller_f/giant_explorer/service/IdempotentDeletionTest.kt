package com.storyteller_f.giant_explorer.service

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdempotentDeletionTest {
    @Test fun absentFileDoesNotRunDelete() = runBlocking {
        assertTrue(ensureDeleted({ false }, { error("Deletion must not run") }))
    }

    @Test fun repeatDeleteIsSuccessfulAndDoesNotRepeatSideEffects() = runBlocking {
        val files = mutableSetOf("fixture")
        val deletions = mutableListOf<String>()
        suspend fun delete() = ensureDeleted({ "fixture" in files }, {
            deletions.add("fixture")
            files.remove("fixture")
        })
        assertTrue(delete())
        assertTrue(delete())
        assertEquals(listOf("fixture"), deletions)
    }

    @Test fun concurrentlyRemovedFileIsSuccessful() = runBlocking {
        val files = mutableSetOf("fixture")
        assertTrue(ensureDeleted({ "fixture" in files }, {
            files.clear()
            false
        }))
    }

    @Test fun unsuccessfulDeleteOfExistingFileRemainsAFailure() = runBlocking {
        assertFalse(ensureDeleted({ true }, { false }))
    }

    @Test fun reportedSuccessStillRequiresTheFileToBeAbsent() = runBlocking {
        assertFalse(ensureDeleted({ true }, { true }))
    }

    @Test fun permissionErrorIsPropagated() = runBlocking {
        val failure = SecurityException("fixture")
        assertEquals(failure, runCatching { ensureDeleted({ true }, { throw failure }) }.exceptionOrNull())
    }
}
