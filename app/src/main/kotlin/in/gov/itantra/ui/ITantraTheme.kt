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

@Composable
fun ITantraTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = mode.resolveDark(isSystemInDarkTheme())
    val scheme = if (dark) darkColorScheme() else lightColorScheme()
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
    MaterialTheme(colorScheme = scheme, content = content)
}
