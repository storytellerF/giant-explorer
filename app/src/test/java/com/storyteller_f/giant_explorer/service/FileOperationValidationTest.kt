package com.storyteller_f.giant_explorer.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOperationValidationTest {
    @Test fun ordinaryFileCanBeCopiedToTestSubdirectory() {
        assertTrue(isCopyDestinationValid("/GiantPR3/notes.txt", "/GiantPR3/folder", false))
    }

    @Test fun directoryCannotBeCopiedIntoItselfOrDescendant() {
        assertFalse(isCopyDestinationValid("/test/folder", "/test/folder", true))
        assertFalse(isCopyDestinationValid("/test/folder/", "/test/folder/child", true))
        assertFalse(isCopyDestinationValid("/test/folder", "/test/other/../folder/child", true))
    }

    @Test fun similarNamesAndParentsAreValidDestinations() {
        assertTrue(isCopyDestinationValid("/test/folder", "/test/folder2", true))
        assertTrue(isCopyDestinationValid("/test/folder", "/test", true))
    }
}
