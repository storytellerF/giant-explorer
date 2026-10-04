package com.storyteller_f.giant_explorer.control.root

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.google.android.material.snackbar.Snackbar
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.ActivityRootAccessBinding
import com.storyteller_f.giant_explorer.view.applyScreenInsets
import com.topjohnwu.superuser.Shell
import kotlin.concurrent.thread

class RootAccessActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityRootAccessBinding

    var newSession: CustomTabsSession? = null
    private val connection: CustomTabsServiceConnection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
            thread {
                val warmup = client.warmup(0)
                Log.i(TAG, "onCustomTabsServiceConnected: warmup $warmup")
                newSession = client.newSession(object : CustomTabsCallback() {
                })
                newSession?.mayLaunchUrl(MAGISK_URL.toUri(), null, null)
                newSession?.mayLaunchUrl(KERNEL_SU_URL.toUri(), null, null)
            }
        }

        override fun onServiceDisconnected(name: ComponentName) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)

        binding = ActivityRootAccessBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applyScreenInsets()

        setSupportActionBar(binding.toolbar)

        val navHost = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_content_root_access
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
            binding.fab.isVisible = destination.id == R.id.RootAccessStatusFragment
        }

        binding.fab.setOnClickListener {
            Shell.getShell { shell ->
                val message = if (shell.isRoot) R.string.root_available else R.string.root_unavailable
                Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
            }
        }
        val bindCustomTabsService = CustomTabsClient.bindCustomTabsService(this, CUSTOM_TAB_PACKAGE_NAME, connection)
        Log.i(TAG, "onCreate: bind $bindCustomTabsService")
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
    }

    override fun onSupportNavigateUp(): Boolean {
        val navHost = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment_content_root_access
        ) as NavHostFragment
        val navController = navHost.navController
        return navController.navigateUp(appBarConfiguration) ||
            super.onSupportNavigateUp()
    }

    companion object {
        private const val TAG = "RootAccessActivity"
        private const val CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome" // Change when in stable
    }
}
