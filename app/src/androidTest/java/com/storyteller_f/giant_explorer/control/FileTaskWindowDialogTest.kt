package com.storyteller_f.giant_explorer.control

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.dialog.FileOperationDialog
import com.storyteller_f.giant_explorer.service.FileOperateBinder
import com.storyteller_f.giant_explorer.service.FileTaskContext
import com.storyteller_f.giant_explorer.service.FileTaskOrigin
import com.storyteller_f.giant_explorer.service.FileTaskStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileTaskWindowDialogTest {
    @Test fun dialogSurvivesRecreationAndOnlyClosingReleasesWindow() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use { scenario ->
            val ready = CompletableDeferred<Pair<FileOperateBinder, FileTaskOrigin.Window>>()
            scenario.onActivity { activity ->
                activity.fileOperateBinder.observe(activity) { binder ->
                    if (binder != null) ready.complete(binder to activity.taskOrigin)
                }
            }
            val (binder, window) = withTimeout(10_000) { ready.await() }
            try {
                // A foreign window's request must not produce a dialog here.
                assertTrue(binder.taskHost.start("foreign", FileTaskContext(origin = FileTaskOrigin.Window("foreign"))).await())
                delay(300)
                scenario.onActivity {
                    assertNull(it.supportFragmentManager.findFragmentByTag(FileOperationDialog.DIALOG_TAG))
                }
                assertTrue(binder.taskHost.start("owned", FileTaskContext(origin = window)).await())
                awaitDialog(scenario, "owned")
                pressBack()
                awaitDialog(scenario, "owned")
                scenario.recreate()
                awaitDialog(scenario, "owned")
                scenario.onActivity { assertEquals(window, it.taskOrigin) }
                binder.taskHost.finish("owned", FileTaskStatus.SUCCEEDED).join()
                assertFalse(binder.taskHost.start("blocked", FileTaskContext(origin = window)).await())
                onView(withId(R.id.close_when_done)).perform(click())
                withTimeout(10_000) {
                    while (binder.taskHost.tasks.value.containsKey("owned")) delay(50)
                }
                assertTrue(binder.taskHost.start("next", FileTaskContext(origin = window)).await())
                awaitDialog(scenario, "next")
                onView(withId(R.id.close_when_done)).perform(click())
                withTimeout(10_000) {
                    while (binder.taskHost.tasks.value.getValue("next").showDialog) delay(50)
                }
                assertTrue(binder.taskHost.start("after-background", FileTaskContext(origin = window)).await())
                awaitDialog(scenario, "after-background")
            } finally {
                for (key in listOf("foreign", "owned", "next", "after-background")) {
                    binder.taskHost.finish(key, FileTaskStatus.CANCELLED).join()
                    binder.taskHost.dismiss(key).join()
                }
            }
        }
    }

    private suspend fun awaitDialog(scenario: ActivityScenario<MainActivity>, key: String) {
        withTimeout(10_000) {
            var visible = false
            while (!visible) {
                scenario.onActivity {
                    visible = it.supportFragmentManager.findFragmentByTag(FileOperationDialog.DIALOG_TAG)
                        ?.arguments?.getString("task-key") == key
                }
                if (!visible) delay(50)
            }
        }
        scenario.onActivity {
            assertNotNull(it.supportFragmentManager.findFragmentByTag(FileOperationDialog.DIALOG_TAG))
        }
    }
}
