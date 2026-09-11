package com.lumarans30.hexamesh.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * The brand palette below is used as a fallback when the platform does not
 * provide a dynamic (wallpaper-based) color scheme. On Android 12+ the app
 * follows the system accent color instead.
 */
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF7FD4FF),
        onPrimary = Color(0xFF00344A),
        primaryContainer = Color(0xFF00506F),
        onPrimaryContainer = Color(0xFFC9E8FF),
        secondary = Color(0xFFB4C9D8),
        onSecondary = Color(0xFF1F333F),
        secondaryContainer = Color(0xFF354A57),
        onSecondaryContainer = Color(0xFFD0E5F4),
        tertiary = Color(0xFFB6F04A),
        onTertiary = Color(0xFF243600),
        tertiaryContainer = Color(0xFF354B00),
        onTertiaryContainer = Color(0xFFD3F58A),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0D1621),
        onBackground = Color(0xFFE1E8EE),
        surface = Color(0xFF0D1621),
        onSurface = Color(0xFFE1E8EE),
        surfaceVariant = Color(0xFF1C2833),
        onSurfaceVariant = Color(0xFFA9B8C4),
        outline = Color(0xFF7A8792),
        outlineVariant = Color(0xFF33414D),
    )

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF00658C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC6E7FF),
        onPrimaryContainer = Color(0xFF001E2E),
        secondary = Color(0xFF4E616D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFD1E5F2),
        onSecondaryContainer = Color(0xFF091E28),
        tertiary = Color(0xFF4C6A00),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFCDF284),
        onTertiaryContainer = Color(0xFF142000),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF6FAFE),
        onBackground = Color(0xFF171C20),
        surface = Color(0xFFF6FAFE),
        onSurface = Color(0xFF171C20),
        surfaceVariant = Color(0xFFDCE4EA),
        onSurfaceVariant = Color(0xFF40484E),
        outline = Color(0xFF70787E),
        outlineVariant = Color(0xFFC0C8CE),
    )

@Composable
fun hexaMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context)
                else dynamicLightColorScheme(context)
            }

            darkTheme -> DarkColors
            else -> LightColors
        }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
