package com.storyteller_f.giant_explorer.control

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import androidx.core.app.ActivityOptionsCompat
import androidx.window.layout.WindowMetricsCalculator
import com.storyteller_f.giant_explorer.R

//copy from Intent.java
const val COPIED_FLAG_ACTIVITY_LAUNCH_ADJACENT = 0x00001000

fun Rect.scale(scale: Float) {
    if (scale != 1.0f) {
        left = (left * scale + 0.5f).toInt()
        top = (top * scale + 0.5f).toInt()
        right = (right * scale + 0.5f).toInt()
        bottom = (bottom * scale + 0.5f).toInt()
    }
}

fun Activity.newWindow(extrasIntent: Intent.() -> Unit = {}) {
    val openMode = defaultSettings.getString(
        getString(R.string.setting_key_open_window_mode),
        getString(R.string.default_open_window_mode)
    )
    val base = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    when (openMode) {
        getString(R.string.normal_open_window_mode) -> {
            newWindow(base, extrasIntent = extrasIntent)
        }

        getString(R.string.adjacent_open_window_mode) -> {
            newWindow(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    base or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
                } else {
                    base or COPIED_FLAG_ACTIVITY_LAUNCH_ADJACENT
                }, extrasIntent = extrasIntent
            )
        }

        getString(R.string.freeform_open_window_mode) -> {
            val optionsCompat = ActivityOptionsCompat.makeBasic()
            val rect =
                WindowMetricsCalculator.getOrCreate().computeMaximumWindowMetrics(this).bounds.apply {
                    scale(0.5f)
                }
            newWindow(base, optionsCompat.setLaunchBounds(rect).toBundle(), extrasIntent)
        }
    }

}

private fun Activity.newWindow(
    flag: Int,
    bundle: Bundle? = null,
    extrasIntent: Intent.() -> Unit
) {
    startActivity(
        Intent(this, MainActivity::class.java).apply {
            addFlags(flag)
            extrasIntent()
        }, bundle
    )
}
