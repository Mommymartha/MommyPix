package com.project.mommypix.ui.theme

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
import com.project.mommypix.data.preferences.AppTheme

private val MommyDarkColorScheme = darkColorScheme(
    primary = MommyPinkPrimary,
    onPrimary = Color.White,
    primaryContainer = MommyPinkDark,
    onPrimaryContainer = Color(0xFFFFD8E4),
    secondary = MommyVioletSecondary,
    onSecondary = Color.White,
    secondaryContainer = MommyVioletDark,
    onSecondaryContainer = Color(0xFFEADBFF),
    background = DarkBackground,
    onBackground = Color(0xFFE6E1E5),
    surface = DarkSurface,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFCAC4D0)
)

private val MommyLightColorScheme = lightColorScheme(
    primary = MommyPinkDark,
    onPrimary = Color.White,
    primaryContainer = MommyPinkLight,
    onPrimaryContainer = Color(0xFF3B0017),
    secondary = MommyVioletDark,
    onSecondary = Color.White,
    secondaryContainer = MommyVioletLight,
    onSecondaryContainer = Color(0xFF22005D),
    background = Color(0xFFFFFBFD),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFD),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF4EFF4),
    onSurfaceVariant = Color(0xFF49454F)
)

@Composable
fun MommyPixTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val isDark = when (appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        isDark -> MommyDarkColorScheme
        else -> MommyLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
