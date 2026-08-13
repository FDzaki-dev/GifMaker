package com.gifmaker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

// Brand tokens dipakai lintas layar (Home/Editor) — top-level, bukan private lagi.
val BackgroundDark = Color(0xFF0A0A0D)
val SurfaceDark = Color(0xFF16161C)
val SurfaceRaised = Color(0xFF1E1E26)
val BrandPrimary = Color(0xFF7B5CFA)
val OnSurfaceDark = Color(0xFFF2F2F5)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            GifMakerTheme {
                GifMakerNavGraph()
            }
        }
    }
}

@Composable
fun GifMakerTheme(content: @Composable () -> Unit) {
    val colorScheme = darkColorScheme(
        primary = BrandPrimary,
        onPrimary = Color.White,
        background = BackgroundDark,
        surface = SurfaceDark,
        onSurface = OnSurfaceDark,
        onBackground = OnSurfaceDark
    )
    MaterialTheme(colorScheme = colorScheme, content = content)
}
