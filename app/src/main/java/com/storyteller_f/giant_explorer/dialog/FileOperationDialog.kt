package com.storyteller_f.giant_explorer.dialog

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.distinctUntilChanged
import com.storyteller_f.common_pr.state
import com.storyteller_f.common_ui.onVisible
import com.storyteller_f.common_ui.pp
import com.storyteller_f.common_ui.repeatOnViewResumed
import com.storyteller_f.common_ui.setOnClick
import com.storyteller_f.common_vm_ktx.GenericValueModel
import com.storyteller_f.common_vm_ktx.avm
import com.storyteller_f.common_vm_ktx.debounce
import com.storyteller_f.common_vm_ktx.keyPrefix
import com.storyteller_f.common_vm_ktx.vm
import com.storyteller_f.file_system.operate.DefaultForemanProgressAdapter
import com.storyteller_f.giant_explorer.DEFAULT_DEBOUNCE
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.DialogFileOperationBinding
import com.storyteller_f.giant_explorer.service.FileOperateBinder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.util.UUID

class FileOperationDialog :
    GiantDialogFragment<DialogFileOperationBinding>(DialogFileOperationBinding::inflate) {
    lateinit var binder: FileOperateBinder

    private val progressVM by keyPrefix(
        { "progress" },
        vm({}) {
            GenericValueModel<Int>()
        }
    )
    private val leftVM by keyPrefix(
        "left",
        vm({}) {
            GenericValueModel<Triple<Int, Int, Long>>().apply {
                data.value = Triple(-1, -1, -1L)
            }
        }
    )
    private val stateVM by keyPrefix(
        "state",
        vm({}) {
            GenericValueModel<String>()
        }
    )
    private val tipVM by keyPrefix(
        "tip",
        vm({}) {
            GenericValueModel<String>()
        }
    )
    private val uuid by keyPrefix(
        { "uuid" },
        avm({}) {
            GenericValueModel<String>().apply {
                Log.i(TAG, "uuid: new")
                data.value = UUID.randomUUID().toString()
            }
        }
    )

    override fun onBindViewEvent(binding: DialogFileOperationBinding) {
        binding.closeWhenError.setOnClick {
            dismiss()
        }
        binding.closeWhenDone.setOnClick {
            dismiss()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        dialog?.setCanceledOnTouchOutside(false)
        val list = listOf(binding.stateProgress, binding.stateRunning, binding.stateDone)
        if (::binder.isInitialized) {
            Log.i(TAG, "onBindViewEvent: state ${binder.state.value}")
            val key = uuid.data.value ?: return
            if (!binder.map.containsKey(key)) dismiss()
            bindUi(key, list, binding)
            bindListener(key, binding)
        } else {
            dismiss()
        }
    }

    private fun bindListener(key: String, binding: DialogFileOperationBinding) {
        val orPut = binder.fileOperationProgressListener.getOrPut(key) { mutableListOf() }
        orPut.add(object : DefaultForemanProgressAdapter() {
            override fun onProgress(progress: Int, key: String) {
                progressVM.data.value = progress
            }

            override fun onState(state: String?, key: String) { stateVM.data.value = state }

            override fun onTip(tip: String?, key: String) { tipVM.data.value = tip }

            override fun onLeft(fileCount: Int, folderCount: Int, size: Long, key: String) {
                leftVM.data.value = Triple(fileCount, folderCount, size)
            }

            override fun onComplete(dest: String?, isSuccess: Boolean, key: String) {
                binding.closeWhenError.pp {
                    it.isVisible = true
                }
            }
        })
        val callbackFlow = callbackFlow {
            val defaultProgressListener = object : DefaultForemanProgressAdapter() {
                override fun onDetail(detail: String?, level: Int, key: String) {
                    trySend(detail ?: "null")
                }
            }
            orPut.add(defaultProgressListener)
            awaitClose { orPut.remove(defaultProgressListener) }
        }
        val detail = binding.textViewDetail
        repeatOnViewResumed {
            callbackFlow.collect {
                detail.append(it)
            }
        }
    }

    private fun bindUi(key: String, list: List<View>, binding: DialogFileOperationBinding) {
        binder.state.distinctUntilChanged().debounce(DEFAULT_DEBOUNCE).state {
            when (it) {
                FileOperateBinder.state_running -> {
                    val task = binder.map[key]?.taskAssessResult
                    list.onVisible(binding.stateRunning)
                    Log.i(TAG, "onBindViewEvent: $key $task ${binder.map.keys}")
                    binding.textViewTask.text = getString(
                        R.string.operation_task_total,
                        task?.size ?: 0L,
                        task?.fileCount ?: 0,
                        task?.folderCount ?: 0
                    )
                }

                FileOperateBinder.state_end -> {
                    binding.doneText.text = getString(R.string.operation_task_done)
                    list.onVisible(binding.stateDone)
                }

                FileOperateBinder.state_error -> {
                    val task = binder.map[key]
                    binding.doneText.text = getString(
                        R.string.operation_task_failed,
                        task?.message ?: getString(R.string.operation_task_unknown_error)
                    )
                    list.onVisible(binding.stateDone)
                }

                else -> list.onVisible(binding.stateProgress)
            }
        }
        progressVM.data.state {
            binding.progressBar.progress = it ?: 0
        }
        stateVM.data.state {
            binding.textViewState.text = it
        }
        tipVM.data.state {
            binding.textViewDetail.text = it
        }
        leftVM.data.state {
            Log.i(TAG, "onBindViewEvent: leftVM: $it")
            it?.let { snapshot -> binding.textViewLeft.text = presentTaskSnapshot(snapshot) }
        }
    }

    private fun presentTaskSnapshot(it: Triple<Int, Int, Long>) =
        getString(R.string.operation_task_remaining, it.third, it.first, it.second)

    override fun onDestroyView() {
        super.onDestroyView()
        binder.fileOperationProgressListener.clear()
    }

    companion object {
        private const val TAG = "FileOperationDialog"
        const val DIALOG_TAG = "file-operation"
    }

    interface Handler {
        fun close()
    }
}
