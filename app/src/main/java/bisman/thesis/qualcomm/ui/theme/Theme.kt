package bisman.thesis.qualcomm.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF4527A0),
    onPrimaryContainer = Color(0xFFE8DEF8),
    
    secondary = SecondaryLight,
    onSecondary = Color(0xFF003333),
    secondaryContainer = SecondaryDark,
    onSecondaryContainer = SecondaryContainer,
    
    tertiary = TertiaryLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = TertiaryDark,
    onTertiaryContainer = Color(0xFFFFE5E5),
    
    error = Error,
    errorContainer = Color(0xFF93000A),
    onError = OnError,
    onErrorContainer = ErrorContainer,
    
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    
    surface = SurfaceDark,
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFCAC4D0),
    
    outline = Color(0xFF938F99),
    inverseOnSurface = Color(0xFF313033),
    inverseSurface = Color(0xFFE6E1E5),
    inversePrimary = Primary,
    
    surfaceTint = PrimaryLight,
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    
    secondary = Secondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,
    
    tertiary = Tertiary,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE5E5),
    onTertiaryContainer = Color(0xFF410000),
    
    error = Error,
    errorContainer = ErrorContainer,
    onError = OnError,
    onErrorContainer = OnErrorContainer,
    
    background = Background,
    onBackground = OnBackground,
    
    surface = Surface,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = Color(0xFF49454F),
    
    outline = Color(0xFF79747E),
    inverseOnSurface = Color(0xFFF4EFF4),
    inverseSurface = Color(0xFF313033),
    inversePrimary = PrimaryLight,
    
    surfaceTint = Primary,
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
)

@Composable
fun ChatAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}