package com.storyteller_f.giant_explorer.control

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.Menu
import android.view.SubMenu
import android.widget.Toast
import androidx.core.net.toUri
import androidx.lifecycle.LifecycleOwner
import com.storyteller_f.common_ui.scope
import com.storyteller_f.file_system_local.FileSystemUriStore
import com.storyteller_f.file_system_remote.RemoteAccessType
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.database.requireDatabase
import kotlinx.coroutines.launch
import java.io.File

class DocumentProviderMenuProvider(
    private val menu: Menu,
    private val activity: MainActivity,
    private val switchDocumentProviderRoot: (String, String?) -> Unit,
    private val switchUriRoot: (Uri) -> Unit
) : LifecycleOwner by activity {
    private val packageManager = activity.packageManager

    fun flashFileSystemRootMenu() {
        menu.clear()
        inflateDefault(menu)
        inflateLocal(menu)
        inflateRemote(menu)
    }

    private fun inflateDefault(menu: Menu) {
        menu.add("DEFAULT").setIcon(R.drawable.baseline_home_24).setOnMenuItemClickListener {
            switchUriRoot(File("/").toUri())
            true
        }
    }

    private fun inflateLocal(
        menu: Menu
    ) {
        val provider = Intent("android.content.action.DOCUMENTS_PROVIDER")
        val info = packageManager.queryIntentContentProvidersCompat(provider, 0)
        val savedUris = FileSystemUriStore.instance.savedUris(activity)
        info.forEach { resolveInfo ->
            val authority = resolveInfo.providerInfo.authority
            val menuItem = menu.addSubMenu(resolveInfo.loadLabel(packageManager))
            menuItem.inflateTree(
                authority,
                savedUris,
                resolveInfo.providerInfo.loadIcon(packageManager)
            )
        }
    }

    private fun SubMenu.inflateTree(
        authority: String,
        savedUris: Map<String, List<String>>,
        iconResource: Drawable
    ) {
        add(authority).setIcon(iconResource).setOnMenuItemClickListener {
            Toast.makeText(activity, authority, Toast.LENGTH_SHORT).show()
            true
        }
        add("ADD").setIcon(R.drawable.baseline_add_circle_24)
            .setOnMenuItemClickListener {
                this@DocumentProviderMenuProvider.switchDocumentProviderRoot(authority, null)
                true
            }
        savedUris[authority]?.forEach { key ->
            add(key).setOnMenuItemClickListener {
                this@DocumentProviderMenuProvider.switchDocumentProviderRoot(authority, key)
                true
            }.let {
                scope.launch {
                    if (activity.documentProviderRoot(authority, key) != null) {
                        it.setIcon(R.drawable.baseline_verified_24)
                    }
                }
            }
        }
    }

    private fun inflateRemote(menu: Menu) {
        scope.launch {
            activity.requireDatabase.remoteAccessDao().listAsync().groupBy {
                it.type
            }.forEach { (key, value) ->
                val addSubMenu = menu.addSubMenu(key)
                value.forEach { spec ->
                    val uri = if (spec.type == RemoteAccessType.SMB) {
                        spec.toShareSpec().toUri()
                    } else {
                        spec.toRemoteSpec().toUri()
                    }

                    addSubMenu.add(spec.name.takeIf { it.isNotEmpty() } ?: spec.server)
                        .setOnMenuItemClickListener {
                            switchUriRoot(uri)
                            true
                        }
                }
            }
        }
    }
}
