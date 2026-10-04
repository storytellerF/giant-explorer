package com.storyteller_f.giant_explorer.control.remote

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.ActivityRemoteManagerBinding
import com.storyteller_f.giant_explorer.view.applyScreenInsets

class RemoteManagerActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityRemoteManagerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityRemoteManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyScreenInsets()

        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_content_remote_manager
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
            binding.fab.isVisible = destination.id == R.id.RemoteListFragment
        }

        binding.fab.setOnClickListener {
            navController.navigate(R.id.RemoteDetailFragment)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHost = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_content_remote_manager
        ) as NavHostFragment
        val navController = navHost.navController
        return navController.navigateUp(appBarConfiguration) ||
            super.onSupportNavigateUp()
    }
}
