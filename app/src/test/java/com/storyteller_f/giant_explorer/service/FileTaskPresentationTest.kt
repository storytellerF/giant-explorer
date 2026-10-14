package com.storyteller_f.giant_explorer.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class FileTaskPresentationTest(
    private val operation: FileOperationKind,
    private val status: FileTaskStatus,
    private val total: FileTaskCounts
) {
    @Test fun statePreservesTruthfulProgressSummaryAndActions() {
        val terminal = status in listOf(FileTaskStatus.SUCCEEDED, FileTaskStatus.FAILED, FileTaskStatus.CANCELLED)
        val known = operation != FileOperationKind.PLUGIN && status != FileTaskStatus.COMPUTING
        val remaining = if (status == FileTaskStatus.SUCCEEDED) FileTaskCounts() else total
        val task = FileTaskSnapshot(
            context = FileTaskContext(operation, "fixture", "/destination"),
            status = status,
            countsKnown = known,
            total = total,
            remaining = remaining
        )
        val display = FileTaskPresentation.from(task)
        assertEquals(terminal, display.terminal)
        assertEquals(!terminal && status != FileTaskStatus.CANCELLING, display.canCancel)
        assertEquals(known && total.files + total.folders > 0, display.showSummary)
        val expectedProgress = when {
            terminal -> TaskProgressMode.HIDDEN
            status == FileTaskStatus.RUNNING && known && total.files + total.folders > 0 -> TaskProgressMode.DETERMINATE
            else -> TaskProgressMode.INDETERMINATE
        }
        assertEquals(expectedProgress, display.progressMode)
        assertEquals(if (status == FileTaskStatus.SUCCEEDED) total else FileTaskCounts(), display.completed)
        assertTrue(display.completedItems in 0..display.totalItems)
        assertEquals(operation, task.context.operation)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}-{1}-{2}")
        fun cases(): List<Array<Any>> = buildList {
            val totals = listOf(
                FileTaskCounts(),
                FileTaskCounts(1, 0, 0),
                FileTaskCounts(3, 0, 4096),
                FileTaskCounts(0, 2, 0),
                FileTaskCounts(4, 2, 8192)
            )
            for (operation in listOf(
                FileOperationKind.COPY,
                FileOperationKind.MOVE,
                FileOperationKind.DELETE,
                FileOperationKind.PLUGIN
            )) {
                for (status in FileTaskStatus.entries) {
                    for (total in totals) add(arrayOf(operation, status, total))
                }
            }
        }
    }
}
