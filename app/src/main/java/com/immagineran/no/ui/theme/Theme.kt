package com.immagineran.no.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.runtime.Composable

private val DarkColorPalette = darkColors(
    primary = LcarsOrange,
    primaryVariant = LcarsBlue,
    secondary = LcarsMagenta,
    background = LcarsBackground,
    surface = LcarsSurface,
    onPrimary = LcarsOnPrimary,
    onBackground = LcarsOnBackground,
    onSurface = LcarsOnBackground
)

/**
 * ImmaginarIA's retro-futuristic theme inspired by classic starship interfaces.
 */
@Composable
fun ImmaginarIATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colors = DarkColorPalette,
        typography = Typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
