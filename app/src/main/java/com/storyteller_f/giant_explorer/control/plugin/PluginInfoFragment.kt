package com.storyteller_f.giant_explorer.control.plugin

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.navArgs
import com.storyteller_f.common_ui.SimpleFragment
import com.storyteller_f.common_ui.repeatOnViewResumed
import com.storyteller_f.giant_explorer.R
import com.storyteller_f.giant_explorer.databinding.FragmentPluginInfoBinding
import com.storyteller_f.giant_explorer.pluginManagerRegister
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PluginInfoFragment : SimpleFragment<FragmentPluginInfoBinding>(FragmentPluginInfoBinding::inflate) {
    private val args by navArgs<PluginInfoFragmentArgs>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repeatOnViewResumed {
            val pluginConfiguration = withContext(Dispatchers.IO) {
                pluginManagerRegister.resolvePluginName(args.pluginName, requireContext())
            }
            binding.pluginName.text = "${args.pluginName} - ${pluginConfiguration.meta.version}"
            binding.pluginPath.text = pluginConfiguration.meta.path
            binding.other.text = getString(
                when (pluginConfiguration) {
                    is ShellPluginConfiguration -> R.string.plugin_type_shell
                    is FragmentPluginConfiguration -> R.string.plugin_type_fragment
                    is HtmlPluginConfiguration -> R.string.plugin_type_html
                    else -> R.string.plugin_details
                }
            )
        }
    }

    override fun onBindViewEvent(binding: FragmentPluginInfoBinding) = Unit
}
