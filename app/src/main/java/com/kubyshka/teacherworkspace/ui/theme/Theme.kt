package com.kubyshka.teacherworkspace.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = SecondaryYellow,
    onSecondary = Color.Black,
    background = BackgroundLight,
    surface = Color.White,
    onSurface = Color(0xFF1F2937)
)

private val DarkColorScheme = darkColorScheme(
    primary = SecondaryYellow,
    onPrimary = Color.Black,
    secondary = PrimaryBlue,
    onSecondary = Color.White
)

@Composable
fun TeacherWorkspaceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
