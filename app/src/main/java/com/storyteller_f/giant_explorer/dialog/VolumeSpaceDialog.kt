package com.storyteller_f.giant_explorer.dialog

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.storyteller_f.common_ui.scope
import com.storyteller_f.file_system_local.LocalFileSystemPaths
import com.storyteller_f.file_system_local.getStorageVolume
import com.storyteller_f.file_system_local.permission.requestFilePermission
import com.storyteller_f.file_system_local.volumePathName
import com.storyteller_f.giant_explorer.App
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.DialogVolumeSpaceBinding
import com.storyteller_f.giant_explorer.databinding.LayoutVolumeItemBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class VolumeSpaceDialog :
    GiantDialogFragment<DialogVolumeSpaceBinding>(DialogVolumeSpaceBinding::inflate) {
    private lateinit var host: StorageSpaceHost

    override fun onBindViewEvent(binding: DialogVolumeSpaceBinding) {
        val app = requireContext().applicationContext as App
        host = StorageSpaceHost(app.hostCoordinationDispatcher, Dispatchers.IO) { readVolumes(app) }
        host.load()
        val regularUri = Uri.Builder().scheme(ContentResolver.SCHEME_FILE)
            .path(LocalFileSystemPaths.ROOT_USER_EMULATED_PATH).build()
        binding.managePermission.setOnClickListener {
            scope.launch {
                it.context.requestFilePermission(regularUri)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val viewBinding = binding
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                host.state.collect { state -> render(viewBinding, state) }
            }
        }
    }

    private fun render(binding: DialogVolumeSpaceBinding, state: StorageSpaceState) {
        binding.spaceList.removeAllViews()
        binding.storageStatus.isVisible = state.loading || state.failed || state.volumes.isEmpty()
        binding.storageStatus.setText(
            when {
                state.loading -> R.string.storage_loading
                state.failed -> R.string.storage_read_failed
                else -> R.string.storage_empty
            }
        )
        state.volumes.forEach { volume -> renderVolume(binding, volume) }
    }

    private fun renderVolume(binding: DialogVolumeSpaceBinding, volume: StorageSpaceVolume) {
        val item = LayoutVolumeItemBinding.inflate(layoutInflater, binding.spaceList, true)
        item.volumeName.text = volume.name
        item.volumeState.setText(storageStatusLabel(volume.status))
        val capacity = volume.capacity
        val values = listOf(item.volumeSpace, item.volumeFree, item.volumeTotal)
        values.forEach { it.isVisible = capacity != null }
        item.capacityMessage.isVisible = capacity == null
        capacity ?: return
        item.volumeSpace.text = getString(R.string.storage_used, formatBytes(capacity.used))
        item.volumeFree.text = getString(R.string.storage_available, formatBytes(capacity.available))
        item.volumeTotal.text = getString(R.string.storage_total, formatBytes(capacity.total))
        values.forEach { it.copyTextFeature() }
    }

    private fun formatBytes(bytes: Long) = Formatter.formatShortFileSize(requireContext(), bytes)

    override fun onDestroyView() {
        host.close()
        super.onDestroyView()
    }
}

private fun readVolumes(context: Context): List<StorageSpaceVolume> =
    context.getStorageVolume().map { volume ->
        val status = volume.state
        val path = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            volume.directory?.absolutePath
        } else {
            volumePathName(volume.uuid)
        }
        val readable = status == Environment.MEDIA_MOUNTED || status == Environment.MEDIA_MOUNTED_READ_ONLY
        val capacity = if (path != null && readable) {
            try {
                val stats = StatFs(path)
                StorageCapacity.fromBytes(stats.totalBytes, stats.availableBytes)
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: SecurityException) {
                null
            }
        } else {
            null
        }
        StorageSpaceVolume(volume.getDescription(context), status, capacity)
    }

private fun storageStatusLabel(status: String) = when (status) {
    Environment.MEDIA_MOUNTED -> R.string.storage_status_available
    Environment.MEDIA_MOUNTED_READ_ONLY -> R.string.storage_status_read_only
    Environment.MEDIA_UNMOUNTED -> R.string.storage_status_unmounted
    Environment.MEDIA_REMOVED -> R.string.storage_status_removed
    Environment.MEDIA_BAD_REMOVAL -> R.string.storage_status_bad_removal
    Environment.MEDIA_CHECKING -> R.string.storage_status_checking
    Environment.MEDIA_EJECTING -> R.string.storage_status_ejecting
    Environment.MEDIA_NOFS -> R.string.storage_status_unformatted
    Environment.MEDIA_UNMOUNTABLE -> R.string.storage_status_unmountable
    else -> R.string.storage_status_unavailable
}
