package com.adera.sms.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Adera SMS Material 3 dark color scheme.
 *
 * Color slot mapping:
 *   primary          → Green800  (surface accent, filled buttons background)
 *   onPrimary        → White     (text on green buttons)
 *   secondary        → GoldPrimary (CTA accent — used for the master toggle when ON)
 *   onSecondary      → Black
 *   background       → GreenBgDark
 *   surface          → GreenSurface (cards, sheets)
 *   surfaceVariant   → GreenSurfaceVariant (elevated cards)
 *   onBackground     → OnDarkPrimary
 *   onSurface        → OnDarkPrimary
 *   onSurfaceVariant → OnDarkSecondary
 *   error            → Ember (failure/alert states ONLY — spec)
 *   outline          → GreenOutline
 */
private val AderaDarkColorScheme = darkColorScheme(
    primary          = Green800,
    onPrimary        = White,
    primaryContainer = Green900,
    onPrimaryContainer = OnDarkPrimary,

    secondary        = GoldPrimary,
    onSecondary      = Black,
    secondaryContainer = GoldDark,
    onSecondaryContainer = Black,

    background       = GreenBgDark,
    onBackground     = OnDarkPrimary,

    surface          = GreenSurface,
    onSurface        = OnDarkPrimary,
    surfaceVariant   = GreenSurfaceVariant,
    onSurfaceVariant = OnDarkSecondary,

    error            = Ember,
    onError          = White,
    errorContainer   = EmberLight,
    onErrorContainer = White,

    outline          = GreenOutline,
    outlineVariant   = GreenSurfaceVariant
)

@Composable
fun AderaSmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AderaDarkColorScheme,
        typography  = AderaTypography,
        content     = content
    )
}
