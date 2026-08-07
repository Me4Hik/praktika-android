// 04.08.2026 Reminder App cursor by Me4Hik START - тема Material3
package com.me4hik.praktika.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// 07.08.2026 Stage 24 Final Design cursor by Me4Hik START - dark-only product theme
private val PraktikaDarkColorScheme = darkColorScheme(
    primary = AccentViolet,
    onPrimary = TextPrimary,
    primaryContainer = AccentDeepBlue,
    onPrimaryContainer = TextPrimary,
    secondary = AccentCyan,
    onSecondary = NavyBackground,
    background = NavyBackground,
    onBackground = TextPrimary,
    surface = GraphiteSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSurface,
    onSurfaceVariant = TextSecondary,
    outline = TextMuted,
    error = Destructive,
    onError = TextPrimary,
)
// 07.08.2026 Stage 24 Final Design cursor by Me4Hik END

@Composable
fun PraktikaTheme(
    content: @Composable () -> Unit,
) {
    val colorScheme = PraktikaDarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = NavyBackground.toArgb()
            window.navigationBarColor = NavyBackground.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
// 04.08.2026 Reminder App cursor by Me4Hik END
