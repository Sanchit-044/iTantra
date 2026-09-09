package `in`.gov.itantra.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import `in`.gov.itantra.core.theme.ThemeMode

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color as ComposeColor

private val PremiumDarkColorScheme = darkColorScheme(
    primary = ComposeColor(0xFF60A5FA), // Soft Blue
    onPrimary = ComposeColor(0xFF000000),
    primaryContainer = ComposeColor(0xFF1E3A8A),
    onPrimaryContainer = ComposeColor(0xFFBFDBFE),
    secondary = ComposeColor(0xFF10B981), // Emerald Green
    onSecondary = ComposeColor(0xFF000000),
    background = ComposeColor(0xFF0F172A), // Slate 900
    surface = ComposeColor(0xFF1E293B), // Slate 800
    surfaceVariant = ComposeColor(0xFF334155), // Slate 700
    error = ComposeColor(0xFFEF4444),
    onError = ComposeColor(0xFFFFFFFF),
    onBackground = ComposeColor(0xFFF8FAFC),
    onSurface = ComposeColor(0xFFF8FAFC),
    onSurfaceVariant = ComposeColor(0xFFCBD5E1)
)

private val PremiumLightColorScheme = lightColorScheme(
    primary = ComposeColor(0xFF2563EB), // Blue 600
    onPrimary = ComposeColor(0xFFFFFFFF),
    primaryContainer = ComposeColor(0xFFDBEAFE),
    onPrimaryContainer = ComposeColor(0xFF1E3A8A),
    secondary = ComposeColor(0xFF059669), // Emerald 600
    onSecondary = ComposeColor(0xFFFFFFFF),
    background = ComposeColor(0xFFF8FAFC), // Slate 50
    surface = ComposeColor(0xFFFFFFFF),
    surfaceVariant = ComposeColor(0xFFF1F5F9), // Slate 100
    error = ComposeColor(0xFFDC2626),
    onError = ComposeColor(0xFFFFFFFF),
    onBackground = ComposeColor(0xFF0F172A),
    onSurface = ComposeColor(0xFF0F172A),
    onSurfaceVariant = ComposeColor(0xFF475569)
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    )
)

@Composable
fun ITantraTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = mode.resolveDark(isSystemInDarkTheme())
    val scheme = if (dark) PremiumDarkColorScheme else PremiumLightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            window.setBackgroundDrawable(ColorDrawable(scheme.background.toArgb()))
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
        }
    }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
