package app.justtalk.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BrandPurple = Color(0xFF6D28FF)
private val BrandCyan = Color(0xFF18C6FF)
private val BrandGreen = Color(0xFF00D26A)
private val BrandOrange = Color(0xFFFF8A00)

private val Light = lightColorScheme(
    primary = BrandPurple,
    secondary = BrandCyan,
    tertiary = BrandGreen,
    error = Color(0xFFE53935),
    background = Color(0xFFF7F8FF),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F2FF),
    onPrimary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF001018),
    onTertiary = Color(0xFF00150B),
    onBackground = Color(0xFF0E1020),
    onSurface = Color(0xFF0E1020),
    onSurfaceVariant = Color(0xFF30324A)
)

private val Dark = darkColorScheme(
    primary = BrandCyan,
    secondary = BrandPurple,
    tertiary = BrandGreen,
    error = Color(0xFFFF6B6B),
    background = Color(0xFF070A14),
    surface = Color(0xFF0B1020),
    surfaceVariant = Color(0xFF151A33),
    onPrimary = Color(0xFF001018),
    onSecondary = Color(0xFFFFFFFF),
    onTertiary = Color(0xFF00150B),
    onBackground = Color(0xFFECEEFF),
    onSurface = Color(0xFFECEEFF),
    onSurfaceVariant = Color(0xFFB9BCE0)
)

private val JustTalkTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = 0.2.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

@Composable
fun JustTalkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = JustTalkTypography,
        content = content
    )
}

@Composable
fun JustTalkBackground(content: @Composable BoxScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val overlay = Brush.linearGradient(
        colors = listOf(
            BrandPurple.copy(alpha = if (dark) 0.20f else 0.18f),
            BrandCyan.copy(alpha = if (dark) 0.14f else 0.14f),
            BrandGreen.copy(alpha = if (dark) 0.10f else 0.10f),
            BrandOrange.copy(alpha = if (dark) 0.10f else 0.10f)
        )
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .background(overlay),
        content = content
    )
}

