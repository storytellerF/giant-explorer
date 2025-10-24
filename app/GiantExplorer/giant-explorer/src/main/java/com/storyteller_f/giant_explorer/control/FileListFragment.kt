package com.storyteller_f.giant_explorer.control

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.text.TextPaint
import android.text.TextUtils
import android.util.Log
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.iterator
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.RecyclerView
import com.storyteller_f.annotation_defination.BindClickEvent
import com.storyteller_f.common_pr.dipToInt
import com.storyteller_f.common_pr.response
import com.storyteller_f.common_ui.SimpleFragment
import com.storyteller_f.common_ui.observeResponse
import com.storyteller_f.common_ui.owner
import com.storyteller_f.common_ui.request
import com.storyteller_f.common_ui.scope
import com.storyteller_f.common_vm_ktx.activityScope
import com.storyteller_f.common_vm_ktx.avm
import com.storyteller_f.common_vm_ktx.genericValueModel
import com.storyteller_f.common_vm_ktx.keyPrefix
import com.storyteller_f.common_vm_ktx.pvm
import com.storyteller_f.file_system.getFileInstance
import com.storyteller_f.file_system.instance.FileCreatePolicy
import com.storyteller_f.file_system.instance.FileInstance
import com.storyteller_f.file_system_archive.ArchiveFileInstanceFactory
import com.storyteller_f.file_system_ktx.isDirectory
import com.storyteller_f.giant_explorer.BuildConfig
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.control.plugin.DefaultPluginManager
import com.storyteller_f.giant_explorer.control.plugin.FileSystemProviderResolver
import com.storyteller_f.giant_explorer.control.plugin.FragmentPluginActivity
import com.storyteller_f.giant_explorer.control.plugin.WebViewPluginActivity
import com.storyteller_f.giant_explorer.databinding.FragmentFileListBinding
import com.storyteller_f.giant_explorer.dialog.NewNameDialog
import com.storyteller_f.giant_explorer.dialog.OpenFileDialog
import com.storyteller_f.giant_explorer.dialog.OpenFileDialogArgs
import com.storyteller_f.giant_explorer.dialog.PropertiesDialogArgs
import com.storyteller_f.giant_explorer.dialog.RequestPathDialog
import com.storyteller_f.giant_explorer.dialog.TaskConfirmDialog
import com.storyteller_f.giant_explorer.model.FileModel
import com.storyteller_f.giant_explorer.pluginManagerRegister
import com.storyteller_f.plugin_core.GiantExplorerService
import com.storyteller_f.plugin_core.GiantExplorerShellPlugin
import com.storyteller_f.ui_list.adapter.SimpleSourceAdapter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class SharePasteTargetViewModel : ViewModel() {
    var list: MutableList<String> = mutableListOf()
    var dest: String? = null

    fun replace(uriList: List<Uri>, file: FileInstance) {
        list.clear()
        list.addAll(uriList.map { it.toString() })
        dest = file.path
    }
}

class FileListFragment : SimpleFragment<FragmentFileListBinding>(
    FragmentFileListBinding::inflate
), FileItemHolderEvent {
    private val fileOperateBinder
        get() = (requireContext() as MainActivity).fileOperateBinder
    private val uuid by keyPrefix(
        { "uuid" },
        avm({}) {
            genericValueModel(UUID.randomUUID().toString())
        }
    )

    private val args by navArgs<FileListFragmentArgs>()

    private val observer = FileListObserver(this, { args }, activityScope)

    private val shareTarget by keyPrefix(
        { "shareTarget" },
        pvm({}) {
            SharePasteTargetViewModel()
        }
    )

    override fun onBindViewEvent(binding: FragmentFileListBinding) {
        val adapter = SimpleSourceAdapter<FileItemHolder, FileViewHolder>()

        val itemSpacing = requireContext().run {
            2.dipToInt
        }
        binding.content.recyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State,
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                if (parent.getChildAdapterPosition(view) != 0) {
                    outRect.top = itemSpacing
                }
            }
        })
        observer.setup(binding.content, adapter, { holder ->
            scope.launch {
                openFolderInNewPage(holder)
            }
        }) {
            (requireContext() as MainActivity).drawPath(it)
        }

        setupMenu()
    }

    private suspend fun openFolderInNewPage(holder: FileItemHolder) {
        val uri = observer.fileInstance?.toChild(holder.file.name, FileCreatePolicy.NotCreate)?.uri
            ?: return
        activity?.newWindow {
            putExtra(
                "start",
                FileListFragmentArgs(uri).toBundle()
            )
        }
    }

    private fun setupMenu() {
        (requireActivity() as? MenuHost)?.addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.file_list_menu, menu)
                }

                override fun onPrepareMenu(menu: Menu) {
                    super.onPrepareMenu(menu)
                    updatePasteButtonState(menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem) = when (menuItem.itemId) {
                    R.id.add_file -> addFile()
                    R.id.paste_file -> pasteFiles()
                    else -> false
                }
            },
            owner
        )
    }

    private fun updatePasteButtonState(menu: Menu) {
        menu.findItem(R.id.paste_file)?.let {
            it.isEnabled = ContextCompat.getSystemService(
                requireContext(),
                ClipboardManager::class.java
            )?.hasPrimaryClip() == true
        }
    }

    private fun addFile(): Boolean {
        val requestKey = findNavController().request(R.id.action_fileListFragment_to_newNameDialog)
        observeResponse(
            requestKey,
            NewNameDialog.NewNameResult::class.java
        ) { nameResult ->
            scope.launch {
                observer.fileInstance?.toChild(nameResult.name, FileCreatePolicy.Create(true))
            }
        }
        return true
    }

    private fun pasteFiles(): Boolean {
        ContextCompat.getSystemService(requireContext(), ClipboardManager::class.java)
            ?.let { manager ->
                manager.primaryClip?.let { data ->
                    pasteFiles(data)
                }
            }
        return true
    }

    fun pasteFiles(data: ClipData, destDirectory: Uri? = null) {
        val key = uuid.data.value ?: return
        Log.i(TAG, "handleClipData: key $key")
        val context = context ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            val dest = destDirectory?.let {
                getFileInstance(context, it)
            } ?: observer.fileInstance ?: kotlin.run {
                Toast.makeText(requireContext(), "无法确定目的地", Toast.LENGTH_LONG).show()
                return@launch
            }
            val uriList = resolveUri(data)
            if (uriList.any {
                    it.scheme == ContentResolver.SCHEME_FILE && it == destDirectory
                }
            ) {
                // 静默处理
                return@launch
            }
            if (uriList.isNotEmpty()) {
                startPasteFiles(uriList, dest, key)
            }
        }
    }

    private fun resolveUri(data: ClipData): List<Uri> {
        val mutableList = MutableList(data.itemCount) {
            data.getItemAt(it)
        }
        val filePathMatcher = Regex("^/([\\w.]+/)*[\\w.]+$")
        val uriList = mutableList.mapNotNull {
            val text = it.coerceToText(requireContext()).toString()
            val uriFromText = text.toUri()
            val u = when {
                uriFromText.scheme == ContentResolver.SCHEME_FILE -> uriFromText
                it.uri != null -> it.uri
                URLUtil.isNetworkUrl(text) -> Uri.parse(text)
                filePathMatcher.matches(text) -> {
                    Uri.fromFile(File(text))
                }

                else -> {
                    Toast.makeText(requireContext(), "正则失败 $text", Toast.LENGTH_LONG).show()
                    null
                }
            }
            u?.takeIf { uri -> uri.toString().isNotEmpty() }
        }
        return uriList
    }

    private suspend fun startPasteFiles(
        uriList: List<Uri>,
        dest: FileInstance,
        key: String,
    ) {
        val context = context ?: return
        val items = uriList.map {
            getFileInstance(context, it)!!.getFileInfo()
        }
        val fileOperateBinderLocal = fileOperateBinder.value ?: kotlin.run {
            Toast.makeText(context, "未连接服务", Toast.LENGTH_LONG).show()
            return
        }
        if (defaultSettings?.getBoolean("notify_before_paste", true) == true) {
            shareTarget.replace(uriList, dest)
            request(TaskConfirmDialog::class.java).response(TaskConfirmDialog.Result::class.java) { result ->
                if (result.confirm) fileOperateBinderLocal.moveOrCopy(dest, items, null, false, key)
            }
        } else {
            fileOperateBinderLocal.moveOrCopy(dest, items, null, false, key)
        }
    }

    fun toChild(itemHolder: FileItemHolder) {
        val old = observer.fileInstance ?: return
        scope.launch {
            val uri = old.uri.buildUpon().appendPath(itemHolder.file.name).build()
            if (itemHolder.file.item.isDirectory) {
                findNavController().navigate(
                    R.id.action_fileListFragment_self,
                    FileListFragmentArgs(
                        uri,
                    ).toBundle()
                )
            } else {
                findNavController().request(
                    R.id.action_fileListFragment_to_openFileDialog,
                    OpenFileDialogArgs(uri).toBundle()
                ).response(OpenFileDialog.OpenFileResult::class.java) { r ->
                    openUri(uri, r, itemHolder.file)
                }
            }
        }
    }

    private fun openUri(
        uri: Uri,
        result: OpenFileDialog.OpenFileResult,
        fileModel: FileModel,
    ) {
        val sharedUri = if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = File(fileModel.fullPath)
            FileProvider.getUriForFile(
                requireContext(),
                BuildConfig.FILE_PROVIDER_AUTHORITY,
                file
            )
        } else {
            FileSystemProviderResolver.share(false, uri)
        }
        openUri(sharedUri, result.mimeType, fileModel.name)
    }

    private fun openUri(
        sharedUri: Uri?,
        mimeType: String,
        fileName: String,
    ) {
        Intent("android.intent.action.VIEW").apply {
            addCategory("android.intent.category.DEFAULT")
            setDataAndType(sharedUri, mimeType)
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        }.let {
            val ellipsizedText = TextUtils.ellipsize(
                fileName,
                TextPaint(),
                DEFAULT_OPEN_PROMPT_TITLE_WIDTH,
                TextUtils.TruncateAt.MIDDLE
            )
            startActivity(Intent.createChooser(it, "open $ellipsizedText by"))
        }
    }

    @BindClickEvent(FileItemHolder::class, "fileIcon")
    fun fileMenu(view: View, itemHolder: FileItemHolder) {
        val fullPath = itemHolder.file.fullPath
        val name = itemHolder.file.name
        val key = uuid.data.value ?: return
        scope.launch {
            val uri = observer.fileInstance?.toChild(name, FileCreatePolicy.NotCreate)?.uri
                ?: return@launch
            showMenu(view, fullPath, itemHolder, key, uri)
        }
    }

    private fun showMenu(
        view: View,
        fullPath: String,
        itemHolder: FileItemHolder,
        key: String,
        uri: Uri,
    ) {
        PopupMenu(requireContext(), view).apply {
            inflate(R.menu.item_context_menu)
            val mimeTypeFromExtension =
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(File(fullPath).extension)

            resolveInstalledPlugins(itemHolder, mimeTypeFromExtension, uri)
            resolveNoInstalledPlugins(mimeTypeFromExtension, fullPath, uri)
            resolveModulePlugin(key, uri, fullPath)
            val isSupportArchiveFileInstance = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
            menu.findItem(R.id.preview_archive).isVisible =
                isSupportArchiveFileInstance || itemHolder.file.item.extension == "zip"

            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.delete -> delete(itemHolder, key)
                    R.id.move_to -> moveOrCopy(true, itemHolder)
                    R.id.copy_to -> moveOrCopy(false, itemHolder)
                    R.id.copy_file -> copyFilePathToClipboard(itemHolder)
                    R.id.properties -> showPropertiesDialog(uri)
                    R.id.preview_archive -> previewArchiveFile(uri)
                }
                true
            }
        }.show()
    }

    private fun previewArchiveFile(uri: Uri) {
        val context = context ?: return
        scope.launch {
            val fileInstance = getFileInstance(context, uri) ?: return@launch
            val newUri =
                ArchiveFileInstanceFactory().buildNestedFile(context, "/", fileInstance)
                    ?: return@launch
            findNavController().navigate(
                FileListFragmentDirections.actionFileListFragmentSelf(
                    newUri
                )
            )
        }
    }

    private fun PopupMenu.resolveModulePlugin(
        key: String,
        uri: Uri,
        fullPath: String,
    ) {
        val liPlugin = try {
            javaClass.classLoader?.loadClass("com.storyteller_f.li.plugin.LiPlugin")
                ?.getDeclaredConstructor()
                ?.newInstance() as? GiantExplorerShellPlugin
        } catch (_: Exception) {
            null
        } ?: return
        val pluginManager = defaultPluginManager(key)
        liPlugin.plugPluginManager(pluginManager)
        val group = liPlugin.group(listOf(uri), File(fullPath).extension)
        if (group.isNotEmpty()) {
            group.map {
                menu.loopAdd(it.first).add(0, it.second, 0, "li").setOnMenuItemClickListener {
                    scope.launch {
                        liPlugin.start(uri, it.itemId)
                    }
                    return@setOnMenuItemClickListener true
                }
            }
        }
    }

    private fun defaultPluginManager(key: String) =
        object : DefaultPluginManager(requireContext()) {
            override suspend fun requestPath(initUri: Uri?): Uri {
                val completableDeferred = CompletableDeferred<Uri>()
                val requestPathDialogArgs = RequestPathDialog.bundle(requireContext())
                request(
                    RequestPathDialog::class.java,
                    requestPathDialogArgs
                ).response(
                    RequestPathDialog.RequestPathResult::class.java
                ) { result ->
                    completableDeferred.complete(result.uri)
                }
                return completableDeferred.await()
            }

            override fun runInService(block: suspend GiantExplorerService.() -> Boolean) {
                fileOperateBinder.value?.pluginTask(key, block)
            }
        }

    private fun showPropertiesDialog(fullPath: Uri) {
        findNavController().navigate(
            R.id.action_fileListFragment_to_propertiesDialog,
            PropertiesDialogArgs(fullPath).toBundle()
        )
    }

    private fun delete(
        itemHolder: FileItemHolder,
        key: String,
    ) {
        fileOperateBinder.value?.delete(
            itemHolder.file.item,
            detectSelected(itemHolder),
            key
        )
    }

    private fun copyFilePathToClipboard(itemHolder: FileItemHolder) {
        ContextCompat.getSystemService(
            requireContext(),
            ClipboardManager::class.java
        )?.let { manager ->
            val map = detectSelected(itemHolder).map {
                Uri.fromFile(File(it.fullPath))
            }
            manager.setPrimaryClip(clipData(map))
        }
    }

    private fun clipData(map: List<Uri>): ClipData {
        return ClipData.newPlainText(CLIP_DATA_KEY, map.first().toString()).apply {
            if (map.size > 1) {
                map.subList(1, map.size).forEach {
                    addItem(ClipData.Item(it))
                }
            }
        }
    }

    private fun PopupMenu.resolveNoInstalledPlugins(
        mimeTypeFromExtension: String?,
        fullPath: String,
        uri: Uri,
    ) {
        pluginManagerRegister.pluginsName().forEach { pluginName: String ->
            val pluginFile = File(pluginName)
            val subMenu =
                pluginManagerRegister.resolvePluginName(pluginName, requireContext()).meta.subMenu
            menu.loopAdd(listOf(subMenu)).add(pluginName).setOnMenuItemClickListener {
                startNotInstalledPlugin(pluginFile, mimeTypeFromExtension, fullPath, uri)
            }
        }
    }

    private fun startNotInstalledPlugin(
        pluginFile: File,
        mimeTypeFromExtension: String?,
        fullPath: String,
        uri: Uri,
    ): Boolean {
        if (pluginFile.name.endsWith("apk")) {
            startActivity(
                Intent(
                    requireContext(),
                    FragmentPluginActivity::class.java
                ).apply {
                    putExtra("plugin-name", pluginFile.name)
                    plugUri(mimeTypeFromExtension, fullPath, uri)
                }
            )
        } else {
            startActivity(
                Intent(
                    requireContext(),
                    WebViewPluginActivity::class.java
                ).apply {
                    putExtra("plugin-name", pluginFile.name)
                    plugUri(mimeTypeFromExtension, fullPath, uri)
                }
            )
        }
        return true
    }

    private fun PopupMenu.resolveInstalledPlugins(
        itemHolder: FileItemHolder,
        mimeTypeFromExtension: String?,
        uri: Uri,
    ) {
        val intent = Intent("com.storyteller_f.action.giant_explorer.PLUGIN")
        intent.addCategory("android.intent.category.DEFAULT")
        intent.plugUri(mimeTypeFromExtension, itemHolder.file.fullPath, uri)

        val activities = requireContext().packageManager.queryIntentActivitiesCompat(
            intent,
            (PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_META_DATA).toLong()
        )
        activities.forEach {
            addToMenu(it, intent)
        }
    }

    private fun PopupMenu.addToMenu(
        it: ResolveInfo,
        intent: Intent,
    ) {
        val activityInfo = it.activityInfo ?: return
        val metaData = activityInfo.metaData ?: return
        val groups = metaData.getString("group")?.split("/") ?: return
        val title = metaData.getString("title") ?: return
        menu.loopAdd(groups).add(title).setOnMenuItemClickListener {
            intent.setPackage(requireContext().packageName).component =
                ComponentName(activityInfo.packageName, activityInfo.name)
            startActivity(intent)
            return@setOnMenuItemClickListener true
        }
    }

    private fun moveOrCopy(needMove: Boolean, itemHolder: FileItemHolder) {
        val requestPathDialogArgs = RequestPathDialog.bundle(requireContext())
        request(
            RequestPathDialog::class.java,
            requestPathDialogArgs
        ).response(RequestPathDialog.RequestPathResult::class.java) { result ->
            scope.launch {
                moveOrCopy(
                    itemHolder,
                    getFileInstance(requireContext(), result.uri)!!,
                    needMove
                )
            }
        }
    }

    private fun moveOrCopy(
        itemHolder: FileItemHolder,
        dest: FileInstance,
        move: Boolean,
    ) {
        val key = uuid.data.value ?: return
        val detectSelected = detectSelected(itemHolder)
        Log.i(TAG, "moveOrCopy: uuid: $key")
        fileOperateBinder.value?.moveOrCopy(
            dest,
            detectSelected,
            itemHolder.file.item,
            move,
            key
        )
    }

    companion object {
        const val CLIP_DATA_KEY = "file explorer"
        private const val TAG = "FileListFragment"
        private const val DEFAULT_OPEN_PROMPT_TITLE_WIDTH = 100f
    }

    private fun detectSelected(itemHolder: FileItemHolder) =
        observer.selected?.map { pair -> (pair as FileItemHolder).file.item } ?: listOf(
            itemHolder.file.item
        )

    override fun onClick(view: View, itemHolder: FileItemHolder) {
        toChild(itemHolder)
    }
}

private fun Menu.loopAdd(strings: List<String>): Menu {
    return strings.fold(this) { t, e ->
        val item = t.iterator().asSequence().firstOrNull {
            it.title == e
        }
        val subMenu = item?.subMenu
        if (item != null && subMenu != null) {
            subMenu
        } else {
            t.addSubMenu(e)
        }
    }
}

private fun Intent.plugUri(mimeType: String?, fullPath: String, uri: Uri) {
    val sharedUri = FileSystemProviderResolver.share(false, uri)
    putExtra("path", fullPath)
    setDataAndType(sharedUri, mimeType)
    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
}
