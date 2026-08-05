package com.adera.sms.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val AderaLightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = White,
    primaryContainer = PrimaryDarkVariantLight,
    onPrimaryContainer = White,
    secondary = AccentLight,
    onSecondary = Black,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = BackgroundLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceContainerLight,
    onSurfaceVariant = OnSurfaceLight,
    error = ErrorLight,
    onError = White
)

private val AderaDarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Black,
    primaryContainer = PrimaryDarkVariantLight,
    onPrimaryContainer = White,
    secondary = AccentDark,
    onSecondary = Black,
    background = BackgroundDark,
    onBackground = OnSurfaceDark,
    surface = BackgroundDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorDark,
    onError = Black
)

@Composable
fun AderaSmsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        AderaDarkColorScheme
    } else {
        AderaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AderaTypography,
        shapes = AderaShapes, // Requires Shape.kt to be updated
        content = content
    )
}
