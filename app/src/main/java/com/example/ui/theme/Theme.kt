package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Custom theme color bundle representing clean liquid glass palette
data class CustomThemeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val glassBg: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val activePill: Color
)

val darkThemeColors = CustomThemeColors(
    isDark = true,
    background = DarkBg,
    surface = DarkSurface,
    glassBg = DarkGlass,
    primary = DarkPrimary,
    secondary = DarkSecondary,
    tertiary = DarkTertiary,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    activePill = DarkPrimary.copy(alpha = 0.22f)
)

val lightThemeColors = CustomThemeColors(
    isDark = false,
    background = LightBg,
    surface = LightSurface,
    glassBg = LightGlass,
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = LightTertiary,
    textPrimary = LightTextPrimary,
    textSecondary = LightTextSecondary,
    activePill = LightPrimary.copy(alpha = 0.18f)
)

val oledThemeColors = CustomThemeColors(
    isDark = true,
    background = OledBg,
    surface = OledSurface,
    glassBg = OledGlass,
    primary = OledPrimary,
    secondary = OledSecondary,
    tertiary = OledTertiary,
    textPrimary = OledTextPrimary,
    textSecondary = OledTextSecondary,
    activePill = OledPrimary.copy(alpha = 0.25f)
)

// Data class representing a selectable theme choice inside the application list
data class ThemeOption(
    val key: String,
    val name: String,
    val description: String,
    val colors: CustomThemeColors
)

val ThemeOptionsList = listOf(
    ThemeOption("DARK", "Темный", "Глубокая ночная стилистика Liquid Glass", darkThemeColors),
    ThemeOption("LIGHT", "Светлый", "Свежий светлый стиль с прозрачными акцентами", lightThemeColors),
    ThemeOption("DARK_OLED", "Темный OLED", "Абсолютно черный фон для экономии энергии", oledThemeColors)
)

val LocalAppThemeColors = staticCompositionLocalOf {
    darkThemeColors
}

@Composable
fun MyApplicationTheme(
    themeKey: String = "DARK",
    content: @Composable () -> Unit,
) {
    val activeOption = ThemeOptionsList.find { it.key == themeKey } ?: ThemeOptionsList[0]
    val activeColors = activeOption.colors

    val materialColorScheme = if (activeColors.isDark) {
        darkColorScheme(
            primary = activeColors.primary,
            secondary = activeColors.secondary,
            tertiary = activeColors.tertiary,
            background = activeColors.background,
            surface = activeColors.surface,
            onBackground = activeColors.textPrimary,
            onSurface = activeColors.textPrimary,
            onPrimary = Color.Black
        )
    } else {
        lightColorScheme(
            primary = activeColors.primary,
            secondary = activeColors.secondary,
            tertiary = activeColors.tertiary,
            background = activeColors.background,
            surface = activeColors.surface,
            onBackground = activeColors.textPrimary,
            onSurface = activeColors.textPrimary,
            onPrimary = Color.White
        )
    }

    CompositionLocalProvider(
        LocalAppThemeColors provides activeColors
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}
