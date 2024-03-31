package com.storyteller_f.giant_explorer.control.remote

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.navArgs
import com.storyteller_f.common_pr.state
import com.storyteller_f.common_ui.SimpleFragment
import com.storyteller_f.common_ui.scope
import com.storyteller_f.common_ui.setOnClick
import com.storyteller_f.common_ui.waitingDialog
import com.storyteller_f.common_vm_ktx.BuilderValueModel
import com.storyteller_f.common_vm_ktx.vm
import com.storyteller_f.file_system_remote.RemoteAccessType
import com.storyteller_f.file_system_remote.RemoteSpec
import com.storyteller_f.file_system_remote.ShareSpec
import com.storyteller_f.file_system_remote.checkFtpConnection
import com.storyteller_f.file_system_remote.checkFtpsConnection
import com.storyteller_f.file_system_remote.checkSFtpConnection
import com.storyteller_f.file_system_remote.checkSmbConnection
import com.storyteller_f.file_system_remote.checkWebDavConnection
import com.storyteller_f.giant_explorer.database.RemoteAccessSpec
import com.storyteller_f.giant_explorer.database.requireDatabase
import com.storyteller_f.giant_explorer.databinding.FragmentRemoteDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RemoteDetailFragment : SimpleFragment<FragmentRemoteDetailBinding>(FragmentRemoteDetailBinding::inflate) {
    companion object {
        private const val TAG = "RemoteDetailFragment"
    }
    private val args by navArgs<RemoteDetailFragmentArgs>()

    private val model by vm({
        requireDatabase to args
    }) { (database, args) ->
        BuilderValueModel {
            if (args.specId == 0) {
                RemoteAccessSpec(0, "", -1, "", "", "", "", "")
            } else {
                withContext(Dispatchers.IO) {
                    database.remoteAccessDao().find(args.specId)
                }
            }
        }
    }

    override fun onBindViewEvent(binding: FragmentRemoteDetailBinding) {
        binding.testConnection.setOnClick {
            testConnection()
        }
        binding.buttonOk.setOnClickListener {
            save()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bind(model.data)

        val list = listOf(
            binding.typeSmb,
            binding.typeSftp,
            binding.typeFtp,
            binding.typeFtpsExplicit,
            binding.typeFtpsImplicit,
            binding.typeWebdav
        ).map {
            it.id
        }
        model.data.filterNotNull().map {
            it.type
        }.state {
            Log.i(TAG, "onViewCreated: mode $it")
            binding.shareInput.isVisible = it == RemoteAccessType.SMB
            if (it != "") {
                val id = list[RemoteAccessType.EXCLUDE_HTTP_PROTOCOL.indexOf(it)]
                if (binding.typeGroup.checkedRadioButtonId != id) {
                    binding.typeGroup.check(id)
                }
            }
        }
        binding.typeGroup.setOnCheckedChangeListener { _, checkedId ->
            Log.i(TAG, "onViewCreated: $checkedId")
            val indexOf = list.indexOf(checkedId)
            model.data.update {
                it!!.copy(type = RemoteAccessType.EXCLUDE_HTTP_PROTOCOL[indexOf])
            }
            if (binding.portInput.text.isEmpty()) {
                binding.portInput.setText(RemoteAccessType.DEFAULT_PORT[indexOf].toString())
            }
        }
    }

    private fun RemoteDetailFragment.bind(data: MutableStateFlow<RemoteAccessSpec?>) {
        data.bind(binding.serverInput, {
            server
        }) {
            copy(server = it)
        }
        data.bind(binding.passwordInput, {
            password
        }) {
            copy(password = it)
        }
        data.bind(binding.portInput, {
            if (port == -1) {
                ""
            } else {
                port.toString()
            }
        }) {
            copy(port = it.takeIf { it.isNotEmpty() }?.toInt() ?: -1)
        }
        data.bind(binding.userInput, {
            user
        }) {
            copy(user = it)
        }
        data.bind(binding.shareInput, {
            share
        }) {
            copy(share = it)
        }
        val filterNotNull = data.filterNotNull()
        filterNotNull.state {
            binding.nameInput.hint = it.toUri().toString()
        }
        data.bind(binding.nameInput, {
            name
        }) {
            copy(name = it)
        }
    }

    private fun <T> MutableStateFlow<T?>.bind(
        editText: EditText,
        map: T.() -> String,
        rebuild: T.(String) -> T
    ) {
        map {
            it?.map()
        }.state {
            if (it != editText.text.toString()) {
                editText.setText(it)
            }
        }
        editText.doAfterTextChanged { s ->
            update {
                it!!.rebuild(s.toString())
            }
        }
    }

    private fun save() {
        val value = model.data.value ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                requireDatabase.remoteAccessDao().add(value)
            }
            Toast.makeText(requireContext(), "success", Toast.LENGTH_SHORT).show()
        }
    }

    private fun testConnection() {
        scope.launch {
            waitingDialog {
                withContext(Dispatchers.IO) {
                    when (val t = model.data.value?.type) {
                        RemoteAccessType.SMB -> shareSpec().checkSmbConnection()
                        RemoteAccessType.FTP -> spec().checkFtpConnection()
                        RemoteAccessType.FTP_ES -> spec().checkFtpsConnection()
                        RemoteAccessType.FTPS -> spec().checkFtpsConnection()
                        RemoteAccessType.WEB_DAV -> spec().checkWebDavConnection()
                        RemoteAccessType.SFTP -> spec().checkSFtpConnection()
                        else -> error("impossible $t")
                    }
                }

                Toast.makeText(requireContext(), "success", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun spec(): RemoteSpec {
        return model.data.value!!.toRemoteSpec()
    }

    private fun shareSpec(): ShareSpec {
        return model.data.value!!.toShareSpec()
    }
}
