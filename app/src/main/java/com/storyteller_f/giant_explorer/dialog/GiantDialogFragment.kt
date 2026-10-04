package com.storyteller_f.giant_explorer.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import androidx.viewbinding.ViewBinding
import com.storyteller_f.common_ui.Registry
import com.storyteller_f.common_ui.ResponseFragment
import com.storyteller_f.common_ui.observeResponse
import com.storyteller_f.common_ui.responseModel
import com.storyteller_f.giant_explorer.R
import kotlin.math.roundToInt

/** Shared window presentation, separate from each dialog’s feature logic. */
abstract class GiantDialogFragment<T : ViewBinding>(private val factory: (LayoutInflater) -> T) :
    DialogFragment(), ResponseFragment, Registry {
    override val vm by responseModel
    private var nullableBinding: T? = null
    val binding: T get() = checkNotNull(nullableBinding)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val localBinding = factory(inflater)
        nullableBinding = localBinding
        onBindViewEvent(localBinding)
        return localBinding.root
    }

    abstract fun onBindViewEvent(binding: T)

    override fun onDestroyView() {
        nullableBinding = null
        super.onDestroyView()
    }

    override fun getTheme() = R.style.ThemeOverlay_Giant_ContentDialog

    override fun onStart() {
        super.onStart()
        observeResponse()
        val density = resources.displayMetrics.density
        val configuration = resources.configuration
        dialog?.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            setLayout(
                (minOf(configuration.screenWidthDp - HORIZONTAL_MARGIN_DP, MAX_WIDTH_DP) * density).roundToInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        binding.root.findViewById<View>(R.id.dismiss_dialog)?.setOnClickListener { dismiss() }
        binding.root.apply {
            setBackgroundResource(R.drawable.giant_dialog_surface)
            clipToOutline = true
        }
    }

    private companion object {
        const val HORIZONTAL_MARGIN_DP = 32
        const val MAX_WIDTH_DP = 560
    }
}
