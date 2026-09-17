package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = ArduinoTealLight,
    onPrimary = Color.White,
    primaryContainer = ArduinoTealDark,
    onPrimaryContainer = Color(0xFFC7F9FF),
    secondary = ArduinoAccentOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF4E2600),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = ArduinoAccentGreen,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorder,
    error = ArduinoAccentRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = ArduinoTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB5F3F7),
    onPrimaryContainer = ArduinoTealDark,
    secondary = ArduinoAccentOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF331400),
    tertiary = ArduinoAccentGreen,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = ArduinoAccentRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Use intentional Arduino brand palette by default
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
