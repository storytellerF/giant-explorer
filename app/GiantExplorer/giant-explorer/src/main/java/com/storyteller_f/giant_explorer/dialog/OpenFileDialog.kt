package com.storyteller_f.giant_explorer.dialog

import android.graphics.Color
import android.os.Bundle
import android.os.Parcelable
import android.view.View
import android.webkit.MimeTypeMap
import androidx.navigation.fragment.navArgs
import com.j256.simplemagic.ContentInfo
import com.j256.simplemagic.ContentInfoUtil
import com.storyteller_f.common_ui.SimpleDialogFragment
import com.storyteller_f.common_ui.scope
import com.storyteller_f.common_ui.setFragmentResult
import com.storyteller_f.common_ui.setOnClick
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
    val uri by lazy { args.uri }
    private val mimeTypeFromExtension = MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(getExtension(uri.path!!))

    @Parcelize
    class OpenFileResult(val mimeType: String) : Parcelable

    override fun onBindViewEvent(binding: DialogOpenFileBinding) {
        val type = listOf("image/*", "video/*", "text/*", "audio/*", "application/*", "*/*")
        val view = listOf(
            binding.openByPicture,
            binding.openByVideo,
            binding.openByText,
            binding.openByMusic,
            binding.openByHex,
            binding.openByAny
        )
        type.forEachIndexed { i, e ->
            view[i].setOnClick {
                openFile(e)
            }
        }

        binding.typeDeduced.setOnClick {
            val value = dataType.data.value?.mimeType
            if (value != null) {
                openFile(value)
            }
        }
        binding.type.setOnClick {
            if (mimeTypeFromExtension != null) {
                openFile(mimeTypeFromExtension)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val fileInstance = getFileInstance(requireContext(), uri)
        binding.fileName.text = fileInstance.name
        binding.fileName.copyTextFeature()
        scope.launch {
            delay(DEFAULT_DEBOUNCE)
            dataType.data.value =
                ContentInfoUtil().findMatch(fileInstance.getFileInputStream().buffered())
        }

        binding.type.isEnabled = mimeTypeFromExtension != null
        if (mimeTypeFromExtension != null) {
            binding.type.text = mimeTypeFromExtension
        }
        dataType.data.observe(viewLifecycleOwner) {
            val deduceSuccess = it?.contentType != null
            binding.typeDeduced.isEnabled = deduceSuccess
            if (deduceSuccess) {
                binding.typeDeduced.text = it?.mimeType
            }
            binding.openByPicture.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "image"))
            binding.openByText.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "text"))
            binding.openByMusic.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "audio"))
            binding.openByVideo.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "video"))
            binding.openByHex.setBackgroundColor(mixColor(mimeTypeFromExtension, it, "application"))
        }
    }

    private fun openFile(e: String) {
        setFragmentResult(OpenFileResult(e))
        dismiss()
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
