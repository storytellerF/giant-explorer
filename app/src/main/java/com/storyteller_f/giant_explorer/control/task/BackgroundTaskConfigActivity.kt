@file:Suppress("ImportOrdering")

package com.storyteller_f.giant_explorer.control.task

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.storyteller_f.common_ui.viewBinding
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.ActivityBackgroundTaskConfigBinding
import com.storyteller_f.giant_explorer.view.applyScreenInsets

class BackgroundTaskConfigActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private val binding by viewBinding(ActivityBackgroundTaskConfigBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding.root.applyScreenInsets()

        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_content_background_task_config
        ) as NavHostFragment
        val navController = navHost.navController
        appBarConfiguration = AppBarConfiguration.Builder(emptySet<Int>())
            .setFallbackOnNavigateUpListener {
                finish()
                true
            }
            .build()
        setupActionBarWithNavController(navController, appBarConfiguration)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.fab.isVisible = destination.id == R.id.BackgroundTaskListFragment
        }

        binding.fab.setOnClickListener {
            navController.navigate(R.id.AddTaskFragment)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHost = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_content_background_task_config
        ) as NavHostFragment
        val navController = navHost.navController
        return navController.navigateUp(appBarConfiguration) ||
            super.onSupportNavigateUp()
    }
}
