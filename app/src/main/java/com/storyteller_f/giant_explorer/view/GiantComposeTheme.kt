package com.storyteller_f.giant_explorer.view

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.material.color.MaterialColors

/** Read the host's semantic colors so embedded Compose rows match the native screens. */
@Composable
fun GiantComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val fallback = if (isSystemInDarkTheme()) darkColors() else lightColors()
    fun color(attribute: Int, default: Color) = Color(
        MaterialColors.getColor(context, attribute, default.toArgb())
    )
    val colors = fallback.copy(
        primary = color(androidx.appcompat.R.attr.colorPrimary, fallback.primary),
        onPrimary = color(com.google.android.material.R.attr.colorOnPrimary, fallback.onPrimary),
        secondary = color(com.google.android.material.R.attr.colorSecondary, fallback.secondary),
        onSecondary = color(com.google.android.material.R.attr.colorOnSecondary, fallback.onSecondary),
        background = color(android.R.attr.colorBackground, fallback.background),
        surface = color(com.google.android.material.R.attr.colorSurface, fallback.surface),
        onSurface = color(com.google.android.material.R.attr.colorOnSurface, fallback.onSurface),
        onBackground = color(com.google.android.material.R.attr.colorOnBackground, fallback.onBackground),
        error = color(androidx.appcompat.R.attr.colorError, fallback.error),
        onError = color(com.google.android.material.R.attr.colorOnError, fallback.onError)
    )
    MaterialTheme(
        colors = colors,
        shapes = Shapes(
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp)
        ),
        content = content
    )
}
