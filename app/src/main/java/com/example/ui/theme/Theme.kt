package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val IndustrialDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = SlateNavy950,
    primaryContainer = ElectricCyanDim,
    onPrimaryContainer = PureWhite,
    secondary = SafetyAmber,
    onSecondary = SlateNavy950,
    secondaryContainer = SafetyAmberDim,
    onSecondaryContainer = PureWhite,
    tertiary = SignalEmerald,
    onTertiary = SlateNavy950,
    tertiaryContainer = SignalEmeraldDim,
    onTertiaryContainer = PureWhite,
    error = HazardCrimson,
    onError = PureWhite,
    errorContainer = HazardCrimsonDim,
    onErrorContainer = PureWhite,
    background = SlateNavy950,
    onBackground = PureWhite,
    surface = SlateNavy900,
    onSurface = PureWhite,
    surfaceVariant = SlateNavy800,
    onSurfaceVariant = SlateSteel200,
    outline = SlateNavy700,
    outlineVariant = SlateSteel400
)

private val IndustrialLightColorScheme = lightColorScheme(
    primary = Color(0xFF006876),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6EEFF),
    onPrimaryContainer = Color(0xFF001F25),
    secondary = Color(0xFF7A5300),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA6),
    onSecondaryContainer = Color(0xFF261900),
    tertiary = Color(0xFF006D36),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF77F8A7),
    onTertiaryContainer = Color(0xFF00210C),
    background = Color(0xFFF3F7FA),
    onBackground = Color(0xFF0D1821),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D1821),
    surfaceVariant = Color(0xFFE1E9F0),
    onSurfaceVariant = Color(0xFF3E4E5E),
    outline = Color(0xFF6E7E8E)
)

val IndustrialShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) IndustrialDarkColorScheme else IndustrialLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = IndustrialShapes,
        content = content
    )
}
