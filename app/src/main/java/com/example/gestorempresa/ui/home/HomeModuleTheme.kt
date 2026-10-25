package com.example.gestorempresa.ui.home

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun HomeModuleTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(
        primary = Color(0xFF60A5FA), onPrimary = Color(0xFF020617),
        secondary = Color(0xFF34D399), background = Color(0xFF020617),
        onBackground = Color.White, surface = Color(0xFF0F172A), onSurface = Color.White,
        surfaceVariant = Color(0xFF1E293B), onSurfaceVariant = Color(0xFFCBD5E1),
        outline = Color(0xFF475569), error = Color(0xFFFB7185)
    ), typography = MaterialTheme.typography, content = content)
}
