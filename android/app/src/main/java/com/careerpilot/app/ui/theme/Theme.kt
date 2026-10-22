package com.careerpilot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── 品牌色（与 Web 端登录页渐变保持一致）────────────────────────

val BrandPrimary = Color(0xFF667EEA)
val BrandSecondary = Color(0xFF764BA2)
val BrandSuccess = Color(0xFF52C41A)
val BrandWarning = Color(0xFFFA8C16)
val BrandError = Color(0xFFE5484D)

private val LightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E8FE),
    onPrimaryContainer = Color(0xFF1B2559),
    secondary = BrandSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE3F5),
    onSecondaryContainer = Color(0xFF34124D),
    tertiary = Color(0xFF4FACFE),
    background = Color(0xFFF5F6FA),
    onBackground = Color(0xFF1F2330),
    surface = Color.White,
    onSurface = Color(0xFF1F2330),
    surfaceVariant = Color(0xFFEEF0F7),
    onSurfaceVariant = Color(0xFF5B6070),
    outline = Color(0xFFD6DAE8),
    outlineVariant = Color(0xFFE6E9F2),
    error = BrandError,
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = Color(0xFF7A1F1F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BA9FF),
    onPrimary = Color(0xFF14205C),
    primaryContainer = Color(0xFF32408A),
    onPrimaryContainer = Color(0xFFDDE1FF),
    secondary = Color(0xFFD3AEE8),
    onSecondary = Color(0xFF3B1A56),
    secondaryContainer = Color(0xFF52306E),
    onSecondaryContainer = Color(0xFFF0DBFF),
    tertiary = Color(0xFF8FD0FF),
    background = Color(0xFF101218),
    onBackground = Color(0xFFE6E8F0),
    surface = Color(0xFF181B24),
    onSurface = Color(0xFFE6E8F0),
    surfaceVariant = Color(0xFF242833),
    onSurfaceVariant = Color(0xFFB6BAC8),
    outline = Color(0xFF3B4050),
    outlineVariant = Color(0xFF2A2E3A),
    error = Color(0xFFFF8A8E),
    onError = Color(0xFF5A1416),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun CareerPilotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
