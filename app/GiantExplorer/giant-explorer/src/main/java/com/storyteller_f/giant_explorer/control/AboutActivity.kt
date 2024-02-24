package com.storyteller_f.giant_explorer.control

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsCallback
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import com.bumptech.glide.Glide
import com.storyteller_f.common_ui.CommonActivity
import com.storyteller_f.common_ui.setOnClick
import com.storyteller_f.giant_explorer.DEFAULT_WEB_VIEW_HEIGHT
import com.storyteller_f.giant_explorer.control.root.KERNEL_SU_URL
import com.storyteller_f.giant_explorer.control.root.MAGISK_URL
import com.storyteller_f.giant_explorer.control.root.ScreenMetricsCompat
import com.storyteller_f.giant_explorer.databinding.ActivityAboutBinding
import com.storyteller_f.ui_list.event.viewBinding

class AboutActivity : CommonActivity() {
    private val binding by viewBinding(ActivityAboutBinding::inflate)
    var newSession: CustomTabsSession? = null
    private val connection: CustomTabsServiceConnection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
            client.warmup(0)
            newSession = client.newSession(object : CustomTabsCallback() {
            })
            newSession?.mayLaunchUrl(Uri.parse(MAGISK_URL), null, null)
            newSession?.mayLaunchUrl(Uri.parse(KERNEL_SU_URL), null, null)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            newSession = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Glide.with(this).load("https://crd.so/i/storytellerF").into(binding.image)
        CustomTabsClient.bindCustomTabsService(this, CUSTOM_TAB_PACKAGE_NAME, connection)
        binding.image.setOnClick {
            val newSession = newSession
            val height = ScreenMetricsCompat.getScreenSize(this).height
            val builder = CustomTabsIntent.Builder().setInitialActivityHeightPx(
                (height * DEFAULT_WEB_VIEW_HEIGHT).toInt()
            )
            if (newSession != null) builder.setSession(newSession)
            val customTabsIntent = builder.build()
            customTabsIntent.launchUrl(this, Uri.parse("https://github.com/storytellerF/common-ui-list-structure"))
        }
    }

    companion object {
        private const val CUSTOM_TAB_PACKAGE_NAME = "com.android.chrome" // Change when in stable
    }
}
