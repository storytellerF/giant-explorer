package com.storyteller_f.giant_explorer.dialog

import android.graphics.Color
import android.os.Parcelable
import android.webkit.MimeTypeMap
import androidx.navigation.fragment.navArgs
import com.j256.simplemagic.ContentInfo
import com.j256.simplemagic.ContentInfoUtil
import com.storyteller_f.common_ui.SimpleDialogFragment
import com.storyteller_f.common_ui.scope
import com.storyteller_f.common_ui.setFragmentResult
import com.storyteller_f.common_vm_ktx.GenericValueModel
import com.storyteller_f.common_vm_ktx.vm
import com.storyteller_f.file_system.util.getExtension
import com.storyteller_f.file_system_ktx.getFileInstance
import com.storyteller_f.giant_explorer.DEFAULT_DEBOUNCE
import com.storyteller_f.giant_explorer.databinding.DialogOpenFileBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

interface StringResult {
    fun onResult(string: String?)
}

class OpenFileDialog : SimpleDialogFragment<DialogOpenFileBinding>(DialogOpenFileBinding::inflate) {

    private val dataType by vm({}) {
        GenericValueModel<ContentInfo?>()
    }

    private val args by navArgs<OpenFileDialogArgs>()

    @Parcelize
    class OpenFileResult(val mimeType: String) : Parcelable

    override fun onBindViewEvent(binding: DialogOpenFileBinding) {
        val uri = args.uri
        val fileInstance = getFileInstance(requireContext(), uri)
        binding.fileName.text = fileInstance.name
        binding.fileName.copyTextFeature()
        binding.dataType = dataType
        binding.handler = object : StringResult {
            override fun onResult(string: String?) {
                setFragmentResult(OpenFileResult(string ?: return))
                dismiss()
            }
        }
        val mimeTypeFromExtension = MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(getExtension(uri.path!!))
        binding.mimeType = mimeTypeFromExtension
        scope.launch {
            delay(DEFAULT_DEBOUNCE)
            dataType.data.value =
                ContentInfoUtil().findMatch(fileInstance.getFileInputStream().buffered())
        }
        dataType.data.observe(viewLifecycleOwner) {
            binding.openByPicture.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "image"))
            binding.openByText.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "text"))
            binding.openByMusic.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "audio"))
            binding.openByVideo.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "video"))
            binding.openByHex.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "application"))
        }
    }

    private fun mixColor(mimeTypeFromExtension: String?, contentInfo: ContentInfo?, t: String): Int {
        val fromMagicNumber = if (contentInfo?.contentType?.mimeType?.contains(t) == true) MAGIC_TARGET else 0
        val fromName = if (mimeTypeFromExtension?.contains(t) == true) EXTENSION_TARGET else 0
        return (fromMagicNumber + fromName).let {
            when (it) {
                MAGIC_TARGET -> Color.parseColor("#A25B32")
                EXTENSION_TARGET -> Color.parseColor("#667DDA")
                MIX_TARGET -> Color.parseColor("#D2D205")
                else -> Color.GRAY
            }
        }
    }

    companion object {
        const val MAGIC_TARGET = 1
        const val EXTENSION_TARGET = 2
        const val MIX_TARGET = 3
    }
}
