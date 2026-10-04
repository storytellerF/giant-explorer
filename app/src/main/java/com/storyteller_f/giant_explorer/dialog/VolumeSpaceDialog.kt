package com.storyteller_f.giant_explorer.dialog

import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.os.storage.StorageVolume
import androidx.core.view.isVisible
import com.storyteller_f.common_ui.scope
import com.storyteller_f.file_system_local.LocalFileSystemPaths
import com.storyteller_f.file_system_local.getFree
import com.storyteller_f.file_system_local.getSpace
import com.storyteller_f.file_system_local.getStorageVolume
import com.storyteller_f.file_system_local.getTotal
import com.storyteller_f.file_system_local.permission.requestFilePermission
import com.storyteller_f.file_system_local.volumePathName
import com.storyteller_f.giant_explorer.control.format1024
import com.storyteller_f.giant_explorer.databinding.DialogVolumeSpaceBinding
import com.storyteller_f.giant_explorer.databinding.LayoutVolumeItemBinding
import kotlinx.coroutines.launch

class VolumeSpaceDialog :
    GiantDialogFragment<DialogVolumeSpaceBinding>(DialogVolumeSpaceBinding::inflate) {
    override fun onBindViewEvent(binding: DialogVolumeSpaceBinding) {
        requireContext().getStorageVolume().forEach {
            deployStorage(binding, it)
        }
        val regularUri = Uri.Builder().scheme(ContentResolver.SCHEME_FILE)
            .path(LocalFileSystemPaths.ROOT_USER_EMULATED_PATH).build()
        binding.managePermission.setOnClickListener {
            scope.launch {
                it.context.requestFilePermission(regularUri)
            }
        }
    }

    private fun deployStorage(
        binding: DialogVolumeSpaceBinding,
        it: StorageVolume
    ) {
        LayoutVolumeItemBinding.inflate(layoutInflater, binding.spaceList, true).apply {
            val prefix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                it.directory?.absolutePath
            } else {
                volumePathName(it.uuid)
            }
            scope.launch {
                volumeSpace.text = format1024(getSpace(prefix))
                volumeFree.text = format1024(getFree(prefix))
                volumeTotal.text = format1024(getTotal(prefix))
            }
            listOf(volumeSpace, volumeTotal, volumeFree).forEach {
                it.copyTextFeature()
            }
            volumeName.text = it.getDescription(requireContext())
            info.isVisible = true
            volumeState.text = it.state
        }
    }
}
