package com.storyteller_f.giant_explorer

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentContainerView
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.storyteller_f.giant_explorer.control.plugin.PluginManageActivity
import com.storyteller_f.giant_explorer.control.remote.RemoteManagerActivity
import com.storyteller_f.giant_explorer.control.root.RootAccessActivity
import com.storyteller_f.giant_explorer.control.task.BackgroundTaskConfigActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationHostTest {
    @Test
    fun remoteHostSurvivesRecreation() = verifyHost(
        RemoteManagerActivity::class.java,
        R.id.nav_host_fragment_content_remote_manager,
        R.id.RemoteListFragment
    )

    @Test
    fun pluginHostSurvivesRecreation() = verifyHost(
        PluginManageActivity::class.java,
        R.id.nav_host_fragment_content_plugin_manage,
        R.id.FirstFragment
    )

    @Test
    fun taskHostSurvivesRecreation() = verifyHost(
        BackgroundTaskConfigActivity::class.java,
        R.id.nav_host_fragment_content_background_task_config,
        R.id.BackgroundTaskListFragment
    )

    @Test
    fun rootHostSurvivesRecreation() = verifyHost(
        RootAccessActivity::class.java,
        R.id.nav_host_fragment_content_root_access,
        R.id.RootAccessStatusFragment
    )

    private fun verifyHost(activityClass: Class<out AppCompatActivity>, containerId: Int, startId: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<AppCompatActivity>(Intent(context, activityClass)).use { scenario ->
            val assertHost: (AppCompatActivity) -> Unit = { activity ->
                assertTrue(activity.findViewById<FragmentContainerView>(containerId).isAttachedToWindow)
                val host = activity.supportFragmentManager.findFragmentById(containerId) as NavHostFragment
                assertEquals(startId, host.navController.currentDestination?.id)
            }
            scenario.onActivity(assertHost)
            scenario.recreate()
            scenario.onActivity(assertHost)
        }
    }
}
