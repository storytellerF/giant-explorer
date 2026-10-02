package com.storyteller_f.giant_explorer.view

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Keep screen controls above system bars and the keyboard without accumulating padding. */
fun View.applyScreenInsets() {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val safe = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout() or
                WindowInsetsCompat.Type.ime()
        )
        view.updatePadding(
            left = initialLeft + safe.left,
            top = initialTop + safe.top,
            right = initialRight + safe.right,
            bottom = initialBottom + safe.bottom
        )
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
