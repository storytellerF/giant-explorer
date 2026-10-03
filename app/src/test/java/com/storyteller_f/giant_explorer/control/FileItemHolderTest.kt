package com.storyteller_f.giant_explorer.control

import android.net.TestUri
import com.storyteller_f.file_system.instance.FileKind
import com.storyteller_f.file_system.instance.FilePermissions
import com.storyteller_f.file_system.instance.FileTime
import com.storyteller_f.file_system.model.FileInfo
import com.storyteller_f.giant_explorer.model.FileModel
import org.junit.Assert.assertEquals
import org.junit.Test

class FileItemHolderTest {
    @Test
    fun sentinelUsesItsOwnViewTypeInBothDisplayModes() {
        for (display in listOf("", "grid")) {
            assertEquals("sentinel", holder("__file_list_sentinel__", display).type)
        }
    }

    @Test
    fun realFileWithSentinelNameStillUsesRequestedDisplayMode() {
        for (display in listOf("", "grid")) {
            assertEquals(display, holder("/storage/__file_list_sentinel__", display).type)
        }
    }

    private fun holder(path: String, display: String): FileItemHolder {
        val name = path.substringAfterLast('/')
        val info = FileInfo(
            name,
            TestUri(path),
            FileTime(),
            FileKind.File(null, false, 0, ""),
            FilePermissions.USER_READABLE
        )
        return FileItemHolder(
            FileModel(info, name, path, 0, false, false, null, null, ""),
            emptyList(),
            display
        )
    }
}
