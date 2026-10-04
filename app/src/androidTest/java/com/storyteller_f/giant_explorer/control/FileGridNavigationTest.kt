package com.storyteller_f.giant_explorer.control

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.net.toUri
import androidx.navigation.fragment.NavHostFragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.giant_explorer.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.hamcrest.Matchers.allOf
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileGridNavigationTest {
    @Test fun gridReturnsFromChildRepeatedlyAndOpensFileMenu() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "grid-navigation-regression").apply { mkdirs() }
        File(root, "folder").apply { mkdirs() }.resolve("nested.txt").writeText("nested")
        File(root, "notes.txt").writeText("notes")
        val intent = Intent(context, MainActivity::class.java)
            .putExtra("start", FileListFragmentArgs(root.toUri()).toBundle())
        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                awaitPage(scenario, "folder")
                scenario.onActivity { activity ->
                    val switch = activity.findViewById<View>(R.id.switchDisplay)
                    if (!switch.isActivated) switch.performClick()
                }
                awaitPage(scenario, "folder", grid = true)
                repeat(3) {
                    onView(allOf(withId(R.id.fileName), withText("folder"))).perform(click())
                    awaitPage(scenario, "nested.txt", grid = true)
                    pressBack()
                    awaitPage(scenario, "folder", grid = true)
                }
                // Espresso waits for layout/animations and clicks a visible anchor. Calling
                // performClick directly can open the popup before the restored row is laid out.
                onView(
                    allOf(
                        withId(R.id.fileIcon),
                        withParent(hasDescendant(allOf(withId(R.id.fileName), withText("notes.txt"))))
                    )
                ).perform(click())
                onView(withText(R.string.copy_to)).check(matches(isDisplayed())).perform(click())
                onView(withText(R.string.choose_location_heading)).check(matches(isDisplayed()))
                pressBack()
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private suspend fun awaitPage(scenario: ActivityScenario<MainActivity>, name: String, grid: Boolean = false) {
        withTimeout(60_000) {
            while (true) {
                var ready = false
                scenario.onActivity { activity ->
                    val views = page(activity).descendants().toList()
                    ready = views.filterIsInstance<TextView>().any { it.id == R.id.fileName && it.text.toString() == name } &&
                        (!grid || views.filterIsInstance<RecyclerView>().any { it.layoutManager is GridLayoutManager })
                }
                if (ready) return@withTimeout
                delay(100)
            }
        }
    }

    private fun page(activity: MainActivity): View {
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment_main) as NavHostFragment
        return host.childFragmentManager.primaryNavigationFragment!!.requireView()
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }
}
