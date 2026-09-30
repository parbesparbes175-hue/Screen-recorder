package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonEmerald,
    onPrimary = CarbonDark,
    primaryContainer = CardDark,
    onPrimaryContainer = NeonEmerald,
    secondary = ElectricCyan,
    onSecondary = CarbonDark,
    tertiary = CrimsonRecord,
    onTertiary = Color.White,
    background = CarbonDark,
    onBackground = TextWhite,
    surface = SurfaceDark,
    onSurface = TextWhite,
    surfaceVariant = CardDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark,
    error = CrimsonRecord,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // GameCapture Lite always uses the dark gaming palette for minimal power consumption
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
