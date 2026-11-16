package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MeeshoMagentaLight,
    onPrimary = Color(0xFF39001A),
    primaryContainer = MeeshoPlumSurface,
    onPrimaryContainer = Color(0xFFFFD9E3),
    secondary = ProfitGold,
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5B4300),
    onSecondaryContainer = Color(0xFFFFDF9E),
    tertiary = SavingsGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF005229),
    onTertiaryContainer = SavingsGreenContainer,
    background = DarkBackground,
    onBackground = OnDarkText,
    surface = DarkSurface,
    onSurface = OnDarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = OnDarkSubtle,
    error = Color(0xFFFFB4AB)
)

private val LightColorScheme = lightColorScheme(
    primary = MeeshoMagenta,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9E3),
    onPrimaryContainer = MeeshoPlumDark,
    secondary = Color(0xFFB27B00),
    onSecondary = Color.White,
    secondaryContainer = ProfitGoldContainer,
    onSecondaryContainer = OnProfitGoldContainer,
    tertiary = SavingsGreen,
    onTertiary = Color.White,
    tertiaryContainer = SavingsGreenContainer,
    onTertiaryContainer = OnSavingsGreenContainer,
    background = LightBackground,
    onBackground = OnLightText,
    surface = LightSurface,
    onSurface = OnLightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = OnLightSubtle,
    error = WarningCoral
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
