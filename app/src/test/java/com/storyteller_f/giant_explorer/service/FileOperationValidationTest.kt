package com.storyteller_f.giant_explorer.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationValidationTest {
    @Test fun ordinaryFileCanBeCopiedToTestSubdirectory() {
        assertTrue(isCopyDestinationValid(local("/GiantPR3/notes.txt"), local("/GiantPR3/folder"), false))
    }

    @Test fun directoryCannotBeCopiedIntoItselfOrDescendant() {
        assertFalse(isCopyDestinationValid(local("/test/folder"), local("/test/folder"), true))
        assertFalse(isCopyDestinationValid(local("/test/folder/"), local("/test/folder/child"), true))
        assertFalse(isCopyDestinationValid(local("/test/folder"), local("/test/other/../folder/child"), true))
    }

    @Test fun similarNamesAndParentsAreValidDestinations() {
        assertTrue(isCopyDestinationValid(local("/test/folder"), local("/test/folder2"), true))
        assertTrue(isCopyDestinationValid(local("/test/folder"), local("/test"), true))
    }

    @Test fun matchingPathsOnDifferentFilesystemsAreValid() {
        val source = FileOperationLocation("sftp", "server-a:22", null, "/photos")
        val descendant = source.copy(path = "/photos/backup")
        assertFalse(isCopyDestinationValid(source, descendant, true))
        assertTrue(isCopyDestinationValid(source, descendant.copy(authority = "server-b:22"), true))
        assertTrue(isCopyDestinationValid(source, descendant.copy(authority = "server-a:2222"), true))
        assertTrue(isCopyDestinationValid(source, descendant.copy(scheme = "ftp"), true))
        assertTrue(isCopyDestinationValid(source, source.copy(authority = "server-b:22"), true))
        assertTrue(isCopyDestinationValid(local("/photos"), descendant, true))
    }

    @Test fun contentProvidersAndStorageTreesAreIndependent() {
        val source = FileOperationLocation("content", "provider-a", "tree-a", "/photos")
        val descendant = source.copy(path = "/photos/backup")
        assertFalse(isCopyDestinationValid(source, descendant, true))
        assertTrue(isCopyDestinationValid(source, descendant.copy(authority = "provider-b"), true))
        assertTrue(isCopyDestinationValid(source, descendant.copy(root = "tree-b"), true))
    }

    @Test fun filesystemRootCannotBeCopiedIntoItsDescendant() {
        assertFalse(isCopyDestinationValid(local("/"), local("/photos"), true))
    }

    @Test fun uriPathCharactersArePreservedWhileDotSegmentsAreNormalized() {
        val source = local("/photos #1/旅行%20")
        assertFalse(isCopyDestinationValid(source, local("/photos #1/旅行%20/./backup"), true))
        assertTrue(isCopyDestinationValid(source, local("/photos #1/旅行 /backup"), true))
        assertTrue(isCopyDestinationValid(source, local("/photos #1/旅行%20/../other"), true))
    }

    @Test fun redundantSlashesDoNotTurnPathSegmentsIntoAnAuthority() {
        assertFalse(isCopyDestinationValid(local("//photos//album/"), local("/photos/album/child"), true))
        assertTrue(isCopyDestinationValid(local("//photos/album"), local("/other/album"), true))
    }

    private fun local(path: String) = FileOperationLocation("file", null, null, path)
}
