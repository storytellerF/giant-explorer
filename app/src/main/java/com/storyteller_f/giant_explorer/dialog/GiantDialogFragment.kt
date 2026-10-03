package com.storyteller_f.giant_explorer.dialog

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.viewbinding.ViewBinding
import com.storyteller_f.common_ui.SimpleDialogFragment
import com.storyteller_f.giant_explorer.R
import kotlin.math.roundToInt

/** Shared window presentation, separate from each dialog’s feature logic. */
abstract class GiantDialogFragment<T : ViewBinding>(factory: (LayoutInflater) -> T) :
    SimpleDialogFragment<T>(factory) {
    override fun getTheme() = R.style.ThemeOverlay_Giant_ContentDialog

    override fun onStart() {
        super.onStart()
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
