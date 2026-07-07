package com.wifieap.connector.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val TvBackground = Color(0xFF1A1A2E)
val TvSurface = Color(0xFF16213E)
val TvPrimary = Color(0xFF0F3460)
val TvAccent = Color(0xFF53A8B6)
val TvOnSurface = Color(0xFFFFFFFF)
val TvOnSurfaceDim = Color(0xFFAAAAAA)
val TvSuccess = Color(0xFF4CAF50)
val TvError = Color(0xFFF44336)

@OptIn(ExperimentalTvMaterial3Api::class)
private val TvDarkColorScheme = darkColorScheme(
    primary = TvAccent,
    onPrimary = Color.White,
    surface = TvSurface,
    onSurface = TvOnSurface,
    background = TvBackground,
    onBackground = TvOnSurface,
    error = TvError,
    onError = Color.White
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun WifiEapTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvDarkColorScheme,
        content = content
    )
}
