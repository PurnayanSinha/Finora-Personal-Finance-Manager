package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class FinoraSemanticColors(
    val income: Color,
    val incomeContainer: Color,
    val expense: Color,
    val expenseContainer: Color,
    val credit: Color,
    val creditContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val heroCardStart: Color,
    val heroCardEnd: Color,
    val cardBorder: Color,
    val subtleText: Color
)

private val LightSemanticColors = FinoraSemanticColors(
    income = EmeraldGreen,
    incomeContainer = EmeraldLightContainer,
    expense = SoftRed,
    expenseContainer = SoftRedLightContainer,
    credit = FinoraBlue,
    creditContainer = FinoraBlueLightContainer,
    warning = AmberGold,
    warningContainer = AmberLightContainer,
    heroCardStart = DeepNavy,
    heroCardEnd = DeepNavyContainer,
    cardBorder = LightOutline,
    subtleText = LightTextSecondary
)

private val DarkSemanticColors = FinoraSemanticColors(
    income = Color(0xFF4ADE80),
    incomeContainer = EmeraldDarkContainer,
    expense = Color(0xFFF87171),
    expenseContainer = SoftRedDarkContainer,
    credit = Color(0xFF60A5FA),
    creditContainer = FinoraBlueDarkContainer,
    warning = Color(0xFFFBBF24),
    warningContainer = AmberDarkContainer,
    heroCardStart = Color(0xFF192B4D),
    heroCardEnd = Color(0xFF0F1B33),
    cardBorder = DarkOutline,
    subtleText = DarkTextSecondary
)

val LocalFinoraColors = staticCompositionLocalOf { LightSemanticColors }

private val LightColorScheme = lightColorScheme(
    primary = DeepNavy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE4F5),
    onPrimaryContainer = DeepNavy,
    secondary = EmeraldGreen,
    onSecondary = Color.White,
    secondaryContainer = EmeraldLightContainer,
    onSecondaryContainer = Color(0xFF052E16),
    tertiary = FinoraBlue,
    onTertiary = Color.White,
    tertiaryContainer = FinoraBlueLightContainer,
    onTertiaryContainer = Color(0xFF1E3A8A),
    error = SoftRed,
    onError = Color.White,
    errorContainer = SoftRedLightContainer,
    onErrorContainer = Color(0xFF7F1D1D),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightCardWhite,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    outlineVariant = Color(0xFFCBD5E1)
)

private val DarkColorScheme = darkColorScheme(
    primary = FinoraBlue,
    onPrimary = Color.White,
    primaryContainer = DeepNavyContainer,
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF22C55E),
    onSecondary = Color(0xFF052E16),
    secondaryContainer = EmeraldDarkContainer,
    onSecondaryContainer = Color(0xFFDCFCE7),
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color(0xFF0B1220),
    tertiaryContainer = FinoraBlueDarkContainer,
    onTertiaryContainer = Color(0xFFDBEAFE),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = SoftRedDarkContainer,
    onErrorContainer = Color(0xFFFEE2E2),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCardSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    outlineVariant = Color(0xFF334155)
)

val FinoraShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun FinoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalFinoraColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = FinoraTypography,
            shapes = FinoraShapes,
            content = content
        )
    }
}
