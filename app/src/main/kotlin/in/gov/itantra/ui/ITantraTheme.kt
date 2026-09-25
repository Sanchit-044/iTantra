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
    primary = ComposeColor(0xFF407BFF), // rgba(64, 123, 255, 1)
    onPrimary = ComposeColor(0xFFFFFFFF),
    primaryContainer = ComposeColor(0xFFDCE6FF),
    onPrimaryContainer = ComposeColor(0xFF001959),
    inversePrimary = ComposeColor(0xFF1A73E8),
    secondary = ComposeColor(0xFF006B5F),
    onSecondary = ComposeColor(0xFFFFFFFF),
    secondaryContainer = ComposeColor(0xFFA0F2E1),
    onSecondaryContainer = ComposeColor(0xFF00201C),
    tertiary = ComposeColor(0xFF9A4600),
    onTertiary = ComposeColor(0xFFFFFFFF),
    tertiaryContainer = ComposeColor(0xFFFFDBC8),
    onTertiaryContainer = ComposeColor(0xFF321200),
    error = ComposeColor(0xFFBA1A1A),
    onError = ComposeColor(0xFFFFFFFF),
    errorContainer = ComposeColor(0xFFFFDAD6),
    onErrorContainer = ComposeColor(0xFF410002),
    background = ComposeColor(0xFFFBF8FF),
    onBackground = ComposeColor(0xFF1B1B21),
    surface = ComposeColor(0xFFFBF8FF),
    onSurface = ComposeColor(0xFF1B1B21),
    surfaceVariant = ComposeColor(0xFFE3E1EC),
    onSurfaceVariant = ComposeColor(0xFF46464F),
    surfaceTint = ComposeColor(0xFF407BFF),
    inverseSurface = ComposeColor(0xFF303036),
    inverseOnSurface = ComposeColor(0xFFF2EFF7),
    outline = ComposeColor(0xFF767680),
    outlineVariant = ComposeColor(0xFFC7C5D0),
    scrim = ComposeColor(0xFF000000),
    surfaceBright = ComposeColor(0xFFFBF8FF),
    surfaceDim = ComposeColor(0xFFDBD9E0),
    surfaceContainerLowest = ComposeColor(0xFFFFFFFF),
    surfaceContainerLow = ComposeColor(0xFFF5F2FA),
    surfaceContainer = ComposeColor(0xFFEFEDF4),
    surfaceContainerHigh = ComposeColor(0xFFE9E7EF),
    surfaceContainerHighest = ComposeColor(0xFFE4E1E9),
)

private val DarkScheme = darkColorScheme(
    primary = ComposeColor(0xFF1A73E8), // rgba(26, 115, 232, 1)
    onPrimary = ComposeColor(0xFFFFFFFF),
    primaryContainer = ComposeColor(0xFF004494),
    onPrimaryContainer = ComposeColor(0xFFD6E3FF),
    inversePrimary = ComposeColor(0xFF407BFF),
    secondary = ComposeColor(0xFF84D6C5),
    onSecondary = ComposeColor(0xFF003731),
    secondaryContainer = ComposeColor(0xFF005047),
    onSecondaryContainer = ComposeColor(0xFFA0F2E1),
    tertiary = ComposeColor(0xFFFFB68A),
    onTertiary = ComposeColor(0xFF532200),
    tertiaryContainer = ComposeColor(0xFF763300),
    onTertiaryContainer = ComposeColor(0xFFFFDBC8),
    error = ComposeColor(0xFFFFB4AB),
    onError = ComposeColor(0xFF690005),
    errorContainer = ComposeColor(0xFF93000A),
    onErrorContainer = ComposeColor(0xFFFFDAD6),
    background = ComposeColor(0xFF121318),
    onBackground = ComposeColor(0xFFE4E1E9),
    surface = ComposeColor(0xFF121318),
    onSurface = ComposeColor(0xFFE4E1E9),
    surfaceVariant = ComposeColor(0xFF46464F),
    onSurfaceVariant = ComposeColor(0xFFC7C5D0),
    surfaceTint = ComposeColor(0xFF1A73E8),
    inverseSurface = ComposeColor(0xFFE4E1E9),
    inverseOnSurface = ComposeColor(0xFF303036),
    outline = ComposeColor(0xFF90909A),
    outlineVariant = ComposeColor(0xFF46464F),
    scrim = ComposeColor(0xFF000000),
    surfaceBright = ComposeColor(0xFF38393F),
    surfaceDim = ComposeColor(0xFF121318),
    surfaceContainerLowest = ComposeColor(0xFF0D0E13),
    surfaceContainerLow = ComposeColor(0xFF1B1B21),
    surfaceContainer = ComposeColor(0xFF1F1F25),
    surfaceContainerHigh = ComposeColor(0xFF29292F),
    surfaceContainerHighest = ComposeColor(0xFF34343A),
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
    success = ComposeColor(0xFF1B6D2F),
    onSuccess = ComposeColor(0xFFFFFFFF),
    successContainer = ComposeColor(0xFFA4F6A8),
    onSuccessContainer = ComposeColor(0xFF002107),
    warning = ComposeColor(0xFF8B5000),
    warningContainer = ComposeColor(0xFFFFDDB8),
    onWarningContainer = ComposeColor(0xFF2C1600),
)

private val DarkExtended = ExtendedColors(
    success = ComposeColor(0xFF89D98E),
    onSuccess = ComposeColor(0xFF003911),
    successContainer = ComposeColor(0xFF00531C),
    onSuccessContainer = ComposeColor(0xFFA4F6A8),
    warning = ComposeColor(0xFFFFB95F),
    warningContainer = ComposeColor(0xFF693C00),
    onWarningContainer = ComposeColor(0xFFFFDDB8),
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
