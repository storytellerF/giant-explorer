package com.storyteller_f.giant_explorer.control.plugin

import androidx.annotation.DrawableRes
import com.storyteller_f.giant_explorer.R

/** Built-in identities do not depend on the filename chosen when importing a plugin. */
@get:DrawableRes
internal val PluginConfiguration.menuIcon: Int
    get() = when {
        this is ShellPluginConfiguration && entryClass == "com.storyteller_f.li.plugin.LiPlugin" ->
            R.drawable.ic_plugin_li
        this is FragmentPluginConfiguration && startFragment == "com.storyteller_f.yue_plugin.YueFragment" ->
            R.drawable.ic_plugin_yue
        this is HtmlPluginConfiguration -> R.drawable.ic_plugin_web
        else -> R.drawable.ic_plugin_generic
    }
