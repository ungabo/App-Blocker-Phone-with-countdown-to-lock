package com.gabe.focusblocker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme

private val LightColors = lightColorScheme(
    primary = Moss,
    onPrimary = Mist,
    secondary = Clay,
    onSecondary = Mist,
    tertiary = Sage,
    background = Sand,
    surface = Mist,
    onSurface = Ink,
    onBackground = Ink,
    outline = Slate
)

private val DarkColors = darkColorScheme(
    primary = Sage,
    onPrimary = Pine,
    secondary = Clay,
    onSecondary = Mist,
    background = Pine,
    surface = Moss,
    onSurface = Mist,
    onBackground = Mist,
    outline = Sage
)

@Composable
fun FocusBlockerTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
