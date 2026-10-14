package com.storyteller_f.giant_explorer.control

import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Environment
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.DialogFileOperationBinding
import com.storyteller_f.giant_explorer.dialog.FileOperationDialogRenderer
import com.storyteller_f.giant_explorer.service.FileOperationKind
import com.storyteller_f.giant_explorer.service.FileTaskContext
import com.storyteller_f.giant_explorer.service.FileTaskCounts
import com.storyteller_f.giant_explorer.service.FileTaskSnapshot
import com.storyteller_f.giant_explorer.service.FileTaskStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class FileOperationDialogStateTest {
    @Test fun rendersAllOperationStatesWithoutFakeProgressOrHiddenResults() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val root = File(app.cacheDir, "dialog-state-fixtures").apply { mkdirs() }
        val intent = Intent(app, MainActivity::class.java)
            .putExtra("start", FileListFragmentArgs(root.toUri()).toBundle())
        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                for (night in listOf(false, true)) {
                    for (operation in listOf(FileOperationKind.COPY, FileOperationKind.MOVE,
                        FileOperationKind.DELETE, FileOperationKind.PLUGIN)) {
                        for (status in FileTaskStatus.entries) {
                            val terminal = status in listOf(FileTaskStatus.SUCCEEDED,
                                FileTaskStatus.FAILED, FileTaskStatus.CANCELLED)
                            val known = operation != FileOperationKind.PLUGIN && status != FileTaskStatus.COMPUTING
                            val task = FileTaskSnapshot(
                                status = status,
                                context = FileTaskContext(operation, "very-long-fixture-file-name-for-wrapping.txt",
                                    if (operation in listOf(FileOperationKind.COPY, FileOperationKind.MOVE))
                                        "/storage/emulated/0/Download" else ""),
                                countsKnown = known,
                                total = FileTaskCounts(3, 1, 8192),
                                remaining = if (status == FileTaskStatus.SUCCEEDED) FileTaskCounts()
                                    else FileTaskCounts(2, 1, 4096),
                                progress = if (status == FileTaskStatus.SUCCEEDED) 100 else 50,
                                message = if (status == FileTaskStatus.FAILED) "Fixture permission failure" else "",
                                details = "Fixture operation details"
                            )
                            var dialog: Dialog? = null
                            var displayed: DialogFileOperationBinding? = null
                            scenario.onActivity { activity ->
                                val context = ContextThemeWrapper(activity, R.style.Theme_Giant_Base)
                                context.applyOverrideConfiguration(Configuration(activity.resources.configuration).apply {
                                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                        if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                                    fontScale = if (night) 1.5f else 1f
                                })
                                context.theme.applyStyle(R.style.ThemeOverlay_Giant_ContentDialog, true)
                                val binding = DialogFileOperationBinding.inflate(android.view.LayoutInflater.from(context))
                                displayed = binding
                                binding.root.setBackgroundResource(R.drawable.giant_dialog_surface)
                                binding.root.clipToOutline = true
                                FileOperationDialogRenderer(context, binding).render(task)
                                dialog = Dialog(context).apply {
                                    setContentView(binding.root)
                                    show()
                                    window!!.setLayout((320 * context.resources.displayMetrics.density).roundToInt(),
                                        ViewGroup.LayoutParams.WRAP_CONTENT)
                                }
                                assertEquals(if (terminal) View.GONE else View.VISIBLE,
                                    binding.taskProgressContainer.visibility)
                                assertEquals(known, binding.taskSummary.visibility == View.VISIBLE)
                                assertEquals(!terminal && status != FileTaskStatus.CANCELLING,
                                    binding.cancelOperation.isEnabled)
                                if (status == FileTaskStatus.RUNNING && !known) assertTrue(binding.progressBar.isIndeterminate)
                                if (status == FileTaskStatus.FAILED) assertTrue(binding.doneText.text.contains("permission"))
                                val surface = MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurface)
                                assertEquals("Night theme must use a dark surface", night, ColorUtils.calculateLuminance(surface) < 0.5)
                                assertTrue(binding.taskTitle.text.isNotBlank())
                                assertTrue(binding.taskStatus.text.isNotBlank())
                                assertEquals(View.VISIBLE, binding.closeWhenDone.visibility)
                            }
                            delay(150)
                            val screenshots = File(app.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "file-task-states")
                                .apply { mkdirs() }
                            scenario.onActivity {
                                val binding = displayed!!
                                assertTrue("Title must be visible", binding.taskTitle.getGlobalVisibleRect(Rect()))
                                assertTrue("Status must be visible", binding.taskStatus.getGlobalVisibleRect(Rect()))
                                assertTrue("Fixed action must be visible", binding.closeWhenDone.getGlobalVisibleRect(Rect()))
                                assertTrue("Body must have height", binding.taskContent.height > 0)
                                val bitmap = Bitmap.createBitmap(binding.root.width, binding.root.height,
                                    Bitmap.Config.ARGB_8888)
                                binding.root.draw(Canvas(bitmap))
                                File(screenshots, "${operation.name}-${status.name}-${if (night) "dark" else "light"}.png")
                                    .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                bitmap.recycle()
                            }
                            scenario.onActivity { dialog?.dismiss() }
                        }
                    }
                }
            }
        } finally { root.deleteRecursively() }
    }
}
