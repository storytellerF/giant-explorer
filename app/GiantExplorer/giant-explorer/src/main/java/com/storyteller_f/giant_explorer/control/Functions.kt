package com.storyteller_f.giant_explorer.control

import android.app.Activity
import android.content.Intent
import android.os.Build
import com.storyteller_f.giant_explorer.R

fun Activity.newWindow(extras: Intent.() -> Unit = {}) {
    val openMode = defaultSettings.getString(
        getString(R.string.setting_key_open_window_mode),
        getString(R.string.default_open_window_mode)
    )
    val base = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
    val flag =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            openMode == getString(R.string.adjacent_open_window_mode)
        ) {
            base or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT
        } else {
            base
        }
    startActivity(
        Intent(this, MainActivity::class.java).apply {
            addFlags(flag)
            extras()
        }
    )
}
