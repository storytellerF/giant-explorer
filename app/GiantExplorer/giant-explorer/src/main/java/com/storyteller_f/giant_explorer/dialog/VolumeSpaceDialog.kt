package com.storyteller_f.giant_explorer.dialog

import android.os.Build
import android.os.storage.StorageVolume
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import com.storyteller_f.common_ui.SimpleDialogFragment
import com.storyteller_f.common_ui.scope
import com.storyteller_f.file_system_local.getFree
import com.storyteller_f.file_system_local.getSpace
import com.storyteller_f.file_system_local.getStorageCompat
import com.storyteller_f.file_system_local.getStorageVolume
import com.storyteller_f.file_system_local.getTotal
import com.storyteller_f.file_system_local.volumePathName
import com.storyteller_f.giant_explorer.control.format1024
import com.storyteller_f.giant_explorer.databinding.DialogVolumeSpaceBinding
import com.storyteller_f.giant_explorer.databinding.LayoutVolumeItemBinding
import kotlinx.coroutines.launch
import java.io.File

class VolumeSpaceDialog :
    SimpleDialogFragment<DialogVolumeSpaceBinding>(DialogVolumeSpaceBinding::inflate) {
    override fun onBindViewEvent(binding: DialogVolumeSpaceBinding) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requireContext().getStorageVolume().forEach {
                deployStorage(binding, it)
            }
        } else {
            requireContext().getStorageCompat().forEach {
                deployFile(binding, it)
            }
        }
    }

    private fun deployFile(
        binding: DialogVolumeSpaceBinding,
        it: File
    ) {
        LayoutVolumeItemBinding.inflate(layoutInflater, binding.spaceList, true).apply {
            scope.launch {
                volumeSpace.text = format1024(getSpace(it.absolutePath))
            }
            volumeName.text = it.name
            info.isVisible = false
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
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
