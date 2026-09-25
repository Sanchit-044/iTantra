package `in`.gov.itantra.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import `in`.gov.itantra.core.theme.ThemeMode
import androidx.compose.ui.graphics.Color as ComposeColor

// Brand palette: deep "mission" indigo as primary, a teal secondary for live /
// connected state, and a saffron tertiary. Every Material 3 role is filled in
// (including the surfaceContainer tiers) so cards, sheets and bars pick up
// consistent tonal elevation instead of falling back to baseline purple.

private val LightScheme = lightColorScheme(
    primary = ComposeColor(0xFF2563EB), // Clean royal blue
    onPrimary = ComposeColor(0xFFFFFFFF),
    primaryContainer = ComposeColor(0xFFEFF6FF), // Soft blue tint
    onPrimaryContainer = ComposeColor(0xFF1E40AF),
    inversePrimary = ComposeColor(0xFF60A5FA),
    secondary = ComposeColor(0xFF475569),
    onSecondary = ComposeColor(0xFFFFFFFF),
    secondaryContainer = ComposeColor(0xFFF1F5F9),
    onSecondaryContainer = ComposeColor(0xFF1E293B),
    tertiary = ComposeColor(0xFF64748B),
    onTertiary = ComposeColor(0xFFFFFFFF),
    tertiaryContainer = ComposeColor(0xFFF1F5F9),
    onTertiaryContainer = ComposeColor(0xFF0F172A),
    error = ComposeColor(0xFFDC2626),
    onError = ComposeColor(0xFFFFFFFF),
    errorContainer = ComposeColor(0xFFFEE2E2),
    onErrorContainer = ComposeColor(0xFF991B1B),
    background = ComposeColor(0xFFF8FAFC), // Crisp soft slate
    onBackground = ComposeColor(0xFF0F172A),
    surface = ComposeColor(0xFFF8FAFC),
    onSurface = ComposeColor(0xFF0F172A),
    surfaceVariant = ComposeColor(0xFFE2E8F0),
    onSurfaceVariant = ComposeColor(0xFF475569),
    surfaceTint = ComposeColor(0xFF2563EB),
    inverseSurface = ComposeColor(0xFF1E293B),
    inverseOnSurface = ComposeColor(0xFFF8FAFC),
    outline = ComposeColor(0xFF94A3B8),
    outlineVariant = ComposeColor(0xFFE2E8F0),
    scrim = ComposeColor(0xFF000000),
    surfaceBright = ComposeColor(0xFFFFFFFF),
    surfaceDim = ComposeColor(0xFFF1F5F9),
    surfaceContainerLowest = ComposeColor(0xFFFFFFFF),
    surfaceContainerLow = ComposeColor(0xFFFFFFFF), // Crisp pure white cards
    surfaceContainer = ComposeColor(0xFFF1F5F9),
    surfaceContainerHigh = ComposeColor(0xFFE2E8F0),
    surfaceContainerHighest = ComposeColor(0xFFCBD5E1),
)

private val DarkScheme = darkColorScheme(
    primary = ComposeColor(0xFF3B82F6), // Vibrant blue for dark mode
    onPrimary = ComposeColor(0xFFFFFFFF),
    primaryContainer = ComposeColor(0xFF1E293B),
    onPrimaryContainer = ComposeColor(0xFFDBEAFE),
    inversePrimary = ComposeColor(0xFF2563EB),
    secondary = ComposeColor(0xFF94A3B8),
    onSecondary = ComposeColor(0xFF0F172A),
    secondaryContainer = ComposeColor(0xFF1E293B),
    onSecondaryContainer = ComposeColor(0xFFE2E8F0),
    tertiary = ComposeColor(0xFFCBD5E1),
    onTertiary = ComposeColor(0xFF0F172A),
    tertiaryContainer = ComposeColor(0xFF1E293B),
    onTertiaryContainer = ComposeColor(0xFFF8FAFC),
    error = ComposeColor(0xFFEF4444),
    onError = ComposeColor(0xFFFFFFFF),
    errorContainer = ComposeColor(0xFF7F1D1D),
    onErrorContainer = ComposeColor(0xFFFEE2E2),
    background = ComposeColor(0xFF0F172A), // Deep slate
    onBackground = ComposeColor(0xFFF8FAFC),
    surface = ComposeColor(0xFF0F172A),
    onSurface = ComposeColor(0xFFF8FAFC),
    surfaceVariant = ComposeColor(0xFF1E293B),
    onSurfaceVariant = ComposeColor(0xFF94A3B8),
    surfaceTint = ComposeColor(0xFF3B82F6),
    inverseSurface = ComposeColor(0xFFF8FAFC),
    inverseOnSurface = ComposeColor(0xFF0F172A),
    outline = ComposeColor(0xFF64748B),
    outlineVariant = ComposeColor(0xFF334155),
    scrim = ComposeColor(0xFF000000),
    surfaceBright = ComposeColor(0xFF1E293B),
    surfaceDim = ComposeColor(0xFF0F172A),
    surfaceContainerLowest = ComposeColor(0xFF020617),
    surfaceContainerLow = ComposeColor(0xFF1E293B), // Elevated dark cards
    surfaceContainer = ComposeColor(0xFF1E293B),
    surfaceContainerHigh = ComposeColor(0xFF334155),
    surfaceContainerHighest = ComposeColor(0xFF475569),
)

/** Semantic colours Material 3 has no role for (success / online state). */
@Immutable
data class ExtendedColors(
    val success: ComposeColor,
    val onSuccess: ComposeColor,
    val successContainer: ComposeColor,
    val onSuccessContainer: ComposeColor,
    val warning: ComposeColor,
    val warningContainer: ComposeColor,
    val onWarningContainer: ComposeColor,
)

private val LightExtended = ExtendedColors(
    success = ComposeColor(0xFF166534),
    onSuccess = ComposeColor(0xFFFFFFFF),
    successContainer = ComposeColor(0xFFDCFCE7),
    onSuccessContainer = ComposeColor(0xFF14532D),
    warning = ComposeColor(0xFFD97706),
    warningContainer = ComposeColor(0xFFFEF3C7),
    onWarningContainer = ComposeColor(0xFF78350F),
)

private val DarkExtended = ExtendedColors(
    success = ComposeColor(0xFF4ADE80),
    onSuccess = ComposeColor(0xFF14532D),
    successContainer = ComposeColor(0xFF14532D),
    onSuccessContainer = ComposeColor(0xFFDCFCE7),
    warning = ComposeColor(0xFFFBBF24),
    warningContainer = ComposeColor(0xFF78350F),
    onWarningContainer = ComposeColor(0xFFFEF3C7),
)

private val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

/** Access via `ITantraTheme.extended.success` etc. */
object ITantraTheme {
    val extended: ExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalExtendedColors.current
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.25.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
fun ITantraTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = mode.resolveDark(isSystemInDarkTheme())
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    val extended = if (dark) DarkExtended else LightExtended
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
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
