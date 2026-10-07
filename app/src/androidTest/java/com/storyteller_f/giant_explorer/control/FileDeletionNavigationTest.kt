package com.storyteller_f.giant_explorer.control

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.net.toUri
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.file_system.getFileInstance
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.service.FileOperateBinder
import com.storyteller_f.giant_explorer.service.FileTaskCounts
import com.storyteller_f.giant_explorer.service.FileTaskStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileDeletionNavigationTest {
    @Test fun deletionRefreshesRecreatedListAndRepeatingStaleSelectionIsSuccessful() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "delete-navigation-regression").apply { mkdirs() }
        File(root, "folder").apply { mkdirs() }.resolve("nested.txt").writeText("nested")
        val source = File(root, "notes.txt").apply { writeText("notes") }
        val parent = getFileInstance(context, root.toUri())!!.getFileInfo()
        val selected = getFileInstance(context, source.toUri())!!.getFileInfo()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("start", FileListFragmentArgs(root.toUri()).toBundle())
        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                awaitPage(scenario, "notes.txt")
                onView(allOf(withId(R.id.fileName), withText("folder"), isDisplayed())).perform(click())
                awaitPage(scenario, "nested.txt")
                pressBack()
                awaitPage(scenario, "notes.txt")
                val ready = CompletableDeferred<FileOperateBinder>()
                scenario.onActivity { activity ->
                    activity.fileOperateBinder.observe(activity) { binder ->
                        if (binder != null) ready.complete(binder)
                    }
                }
                val binder = withTimeout(10_000) { ready.await() }
                scenario.onActivity { binder.delete(parent, listOf(selected, selected), "first-delete") }
                val first = withTimeout(10_000) {
                    binder.taskHost.tasks.first { it["first-delete"]?.status == FileTaskStatus.SUCCEEDED }
                }.getValue("first-delete")
                assertEquals(FileTaskCounts(1, 0, 5), first.total)
                assertEquals(FileTaskCounts(), first.remaining)
                assertFalse(source.exists())
                awaitPage(scenario, "folder", absent = "notes.txt")
                onView(withId(R.id.close_when_done)).perform(click())

                scenario.onActivity { binder.delete(parent, listOf(selected), "repeat-delete") }
                val repeated = withTimeout(10_000) {
                    binder.taskHost.tasks.first { it["repeat-delete"]?.status == FileTaskStatus.SUCCEEDED }
                }.getValue("repeat-delete")
                assertEquals(FileTaskCounts(), repeated.total)
                assertEquals(100, repeated.progress)
                assertEquals(context.getString(R.string.operation_delete_already_absent), repeated.message)
                awaitPage(scenario, "folder", absent = "notes.txt")
            }
        } finally { root.deleteRecursively() }
    }

    private suspend fun awaitPage(scenario: ActivityScenario<MainActivity>, present: String, absent: String? = null) {
        withTimeout(10_000) {
            while (true) {
                val matched = CompletableDeferred<Boolean>()
                scenario.onActivity { activity ->
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_main)
                        as NavHostFragment
                    val page = host.childFragmentManager.primaryNavigationFragment?.view
                    val names = page?.descendants()?.filterIsInstance<TextView>()
                        ?.filter { it.id == R.id.fileName }?.map { it.text.toString() }?.toList().orEmpty()
                    matched.complete(present in names && (absent == null || absent !in names))
                }
                if (matched.await()) return@withTimeout
                delay(100)
            }
        }
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }
}
